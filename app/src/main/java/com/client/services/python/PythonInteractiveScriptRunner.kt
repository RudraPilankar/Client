package com.client.services.python

import android.content.Context
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import kotlinx.coroutines.*
import java.io.File

/**
 * PythonInteractiveScriptRunner runs an external Python script in an interactive session.
 *
 * It sets up a Python environment that:
 * - Reads the script from a file and applies the provided arguments.
 * - Creates two queues in Python (input_queue and output_queue).
 * - Overrides the built-in input() with a custom function that reads from input_queue.
 * - Redirects stdout/stderr so that output is captured in output_queue.
 *
 * Additionally, the Python wrapper defines an init_context() function to store the Android context.
 *
 * This runner mimics VirtualShell timing behavior:
 * - If no output is received for 2 seconds, the current output is flushed.
 * - If output continuously accumulates for 20 seconds, it is flushed.
 * - If the output exceeds 5000 characters, it is flushed immediately.
 *
 * The output callback and error callback are provided in the constructor.
 *
 * Use startScript() to begin execution; sendInput() to provide commands;
 * isScriptRunning() to check status; and shutdown() to terminate.
 */
class PythonInteractiveScriptRunner(
    context: Context,
    applicationContext: Context,
    scriptPath: String,
    args: List<String>,
    private val outputCallback: (String) -> Unit,
    private val errorCallback: (String) -> Unit
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val py: Python = run {
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(context))
        }
        Python.getInstance()
    }
    private val mainModule = py.getModule("__main__")
    private val script: String
    private val pyArgs: Any
    private val outputListenerJob: Job

    init {
        // Read the Python script from file.
        val scriptFile = File(scriptPath)
        if (!scriptFile.exists() || !scriptFile.canRead()) {
            throw IllegalArgumentException("File at $scriptPath does not exist or cannot be read.")
        }
        script = scriptFile.readText()

        // Prepare sys.argv: first element is the absolute script path.
        val argvKotlin = listOf(scriptFile.absolutePath) + args
        // Convert the Kotlin List into a native Python list.
        pyArgs = py.getBuiltins().callAttr("list", argvKotlin.toTypedArray())

        // Updated wrapper code: define init_context and set globals.
        val wrapperCode = """
import sys, queue, os, time, threading

# Global variables to hold the Android context.
applicationContext = None
context = None

def init_context(app_c, c):
    global applicationContext
    applicationContext = app_c
    global context
    context = c

input_queue = queue.Queue()
output_queue = queue.Queue()
shutdown_flag = threading.Event()  # Used to signal shutdown

class QueueWriter:
    def __init__(self, q):
        self.q = q
    def write(self, s):
        if s:  # Only send non-empty strings.
            self.q.put(s)
    def flush(self):
        pass

def custom_input(prompt=""):
    if prompt:
        output_queue.put(prompt)
    return input_queue.get()

def run_script(script, argv):
    sys.argv = argv
    __builtins__.input = custom_input
    old_stdout = sys.stdout
    old_stderr = sys.stderr
    # Use our QueueWriter so that prints are sent immediately.
    sys.stdout = QueueWriter(output_queue)
    sys.stderr = QueueWriter(output_queue)

    def monitor_shutdown():
        while not shutdown_flag.is_set():
            time.sleep(0.5)
    
    monitor_thread = threading.Thread(target=monitor_shutdown, daemon=True)
    monitor_thread.start()

    try:
        # Use globals() so that app_context and other globals are available.
        exec(script, globals())
    except Exception as e:
        output_queue.put("Error: " + str(e))
    finally:
        sys.stdout = old_stdout
        sys.stderr = old_stderr

def stop_script():
    shutdown_flag.set()
    input_queue.put("EXIT")  # Unblock any waiting input.
""".trimIndent()

        // Execute the wrapper code in __main__'s globals.
        py.getBuiltins().callAttr("exec", wrapperCode, mainModule.get("__dict__"))
        // Pass the Android context to Python.
        mainModule.callAttr("init_context", applicationContext, context)

        // Start the output listener immediately (with timing behavior).
        outputListenerJob = scope.launch {
            val outputQueue = mainModule.get("output_queue")!!
            val outputBuffer = StringBuilder()
            var debounceJob: Job? = null
            var maxTimeoutJob: Job? = null

            suspend fun flushBuffer() {
                debounceJob?.cancel()
                maxTimeoutJob?.cancel()
                if (outputBuffer.isNotEmpty()) {
                    outputCallback(outputBuffer.toString())
                    outputBuffer.clear()
                }
            }

            while (isActive) {
                try {
                    // Poll output_queue with a timeout of 0.1 seconds.
                    val result = outputQueue.callAttr("get", 0.1)
                    outputBuffer.append(result.toString())

                    // Flush immediately if output exceeds 5000 characters.
                    if (outputBuffer.length >= 5000) {
                        flushBuffer()
                        continue
                    }

                    // Reset the debounce timer: flush if no new output for 2 seconds.
                    debounceJob?.cancel()
                    debounceJob = launch {
                        delay(2000L)
                        flushBuffer()
                    }

                    // Start the max timeout timer: flush after 10 seconds.
                    if (maxTimeoutJob == null || !maxTimeoutJob.isActive) {
                        maxTimeoutJob = launch {
                            delay(10000L)
                            flushBuffer()
                        }
                    }
                } catch (e: Exception) {
                    if (e.message?.contains("Empty", ignoreCase = true) == false) {
                        errorCallback("Output listener error: ${e.localizedMessage}")
                    }
                    delay(100)
                }
            }
            flushBuffer()
        }
    }

    /**
     * Starts the Python script in a background coroutine.
     * The script runs until it terminates.
     * Once the script finishes, the runner automatically shuts down.
     */
    fun startScript() {
        scope.launch {
            try {
                // This call blocks until the script terminates.
                mainModule.callAttr("run_script", script, pyArgs)
            } catch (e: Exception) {
                errorCallback("Error running script: ${e.localizedMessage}")
            } finally {
                // Shutdown after the script exits.
                shutdown()
            }
        }
    }

    /**
     * Sends input to the Python script by placing it into the input_queue.
     */
    fun sendInput(input: String) {
        try {
            mainModule.get("input_queue")!!.callAttr("put", input)
        } catch (e: Exception) {
            errorCallback("Error sending input: ${e.localizedMessage}")
        }
    }

    /**
     * Checks if the script session is still running.
     *
     * For this implementation, if the coroutine scope is active, we consider the script running.
     */
    fun isScriptRunning(): Boolean {
        return scope.isActive
    }

    /**
     * Shuts down the runner by calling stop_script() and cancelling all background tasks.
     */
    fun shutdown() {
        mainModule.callAttr("stop_script")
        scope.cancel()
    }
}
