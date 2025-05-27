package com.client.services.virtualshell

import android.content.Context
import android.os.Environment
import android.util.Log
import kotlinx.coroutines.*
import java.io.File
import java.io.InputStreamReader

/**
 * VirtualShell now combines stdout and stderr into one stream so that output is delivered in order.
 *
 * @param callback Called with the merged output (both stdout and stderr) in the order the shell produced it.
 * @param onShellExit Optional callback invoked when the shell process terminates.
 */
class VirtualShell(
    private val callback: (String) -> Unit,        // Called for combined output.
    private val onShellExit: (() -> Unit)? = null    // Called when the shell exits.
) {
    private lateinit var process: Process
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var streamJob: Job? = null
    private var debounceJob: Job? = null
    private var maxTimeoutJob: Job? = null

    private val mergedBuffer = StringBuilder()
    private val bufferLock = Any()

    /**
     * Starts the virtual shell. This version merges stderr into stdout.
     * When the process terminates any pending output is flushed and onShellExit is fired.
     */
    fun createVirtualShell(shellPath: String, context: Context, enableInteractiveMode: Boolean = false) {
        process = if (enableInteractiveMode) {
            ProcessBuilder(shellPath, "-i")
                .redirectErrorStream(true)
                .directory(Environment.getExternalStorageDirectory())
                .start()
        } else {
            ProcessBuilder(shellPath)
                .redirectErrorStream(true)
                .directory(Environment.getExternalStorageDirectory())
                .start()
        }

        // Launch a coroutine to wait for process termination.
        scope.launch {
            withContext(Dispatchers.IO) { process.waitFor() }
            flushMergedBuffer() // Flush any pending output on termination.
            onShellExit?.invoke() // Fire the shell exit callback.
        }

        // Launch a coroutine to read the combined stream character by character.
        streamJob = scope.launch {
            val reader = InputStreamReader(process.inputStream)
            try {
                while (isActive) {
                    val ch = reader.read()
                    if (ch == -1) break

                    var flushNow = false
                    synchronized(bufferLock) {
                        mergedBuffer.append(ch.toChar())
                        if (mergedBuffer.length >= 5000) {
                            flushNow = true
                        }
                    }
                    if (flushNow) {
                        flushMergedBuffer()
                        continue
                    }
                    // Reset the debounce timer.
                    debounceJob?.cancel()
                    debounceJob = launch {
                        delay(1000L)
                        flushMergedBuffer()
                    }
                    // Start the max timeout timer if not already running.
                    if (maxTimeoutJob == null || !maxTimeoutJob!!.isActive) {
                        Log.d("VirtualShell", "Starting maxTimeoutJob")
                        maxTimeoutJob = launch {
                            delay(10000L)
                            flushMergedBuffer()
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                reader.close()
            }
        }
    }

    /**
     * Returns true if the shell process is running.
     */
    fun isShellRunning(): Boolean {
        return this::process.isInitialized && process.isAlive
    }

    /**
     * Flushes the merged buffer and calls callback if there's any output.
     * Cancels debounce and max timeout timers.
     */
    private suspend fun flushMergedBuffer() {
        val output: String
        synchronized(bufferLock) {
            output = mergedBuffer.toString()
            mergedBuffer.clear()
        }
        debounceJob?.cancel()
        maxTimeoutJob?.cancel()
        if (output.isNotBlank()) {
            callback(output)
        }
    }

    /**
     * Sends input to the shell's stdin.
     */
    fun sendInput(input: String) {
        scope.launch {
            process.outputStream.apply {
                write(input.toByteArray())
                flush()
            }
        }
    }

    /**
     * Sends keyboard strokes to the shell simulating keystrokes.
     */
    fun sendKeystrokes(input: String, delayMillis: Long = 50L) {
        scope.launch {
            val tokenRegex = Regex("<(.*?)>")
            var lastIndex = 0
            tokenRegex.findAll(input).forEach { matchResult ->
                val literal = input.substring(lastIndex, matchResult.range.first)
                for (ch in literal) {
                    process.outputStream.write(ch.code)
                    process.outputStream.flush()
                    delay(delayMillis)
                }
                val token = matchResult.groupValues[1].trim().lowercase()
                if (token.startsWith("ctrl+")) {
                    val letter = token.substringAfter("ctrl+")
                    if (letter.isNotEmpty()) {
                        val controlCode = letter[0].uppercaseChar().code - 'A'.code + 1
                        process.outputStream.write(controlCode)
                        process.outputStream.flush()
                        delay(delayMillis)
                    }
                } else {
                    val specialBytes = when (token) {
                        "enter" -> "\n".toByteArray()
                        "tab" -> "\t".toByteArray()
                        "backspace" -> byteArrayOf(8)
                        "esc", "escape" -> byteArrayOf(27)
                        "up" -> "\u001B[A".toByteArray()
                        "down" -> "\u001B[B".toByteArray()
                        "right" -> "\u001B[C".toByteArray()
                        "left" -> "\u001B[D".toByteArray()
                        "delete" -> byteArrayOf(127)
                        else -> null
                    }
                    if (specialBytes != null) {
                        process.outputStream.write(specialBytes)
                        process.outputStream.flush()
                        delay(delayMillis)
                    }
                }
                lastIndex = matchResult.range.last + 1
            }
            if (lastIndex < input.length) {
                val literal = input.substring(lastIndex)
                for (ch in literal) {
                    process.outputStream.write(ch.code)
                    process.outputStream.flush()
                    delay(delayMillis)
                }
            }
        }
    }

    /**
     * Flushes any pending output then forcefully closes the virtual shell.
     * Blocks until the buffers are flushed.
     */
    fun closeVirtualShell() {
        runBlocking {
            flushMergedBuffer()
        }
        streamJob?.cancel()
        debounceJob?.cancel()
        maxTimeoutJob?.cancel()
        scope.cancel()
        process.destroyForcibly()
    }
}
