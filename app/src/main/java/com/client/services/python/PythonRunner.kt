package com.client.services.python

import android.content.Context
import com.chaquo.python.Python
import java.io.File

class PythonRunner private constructor() {
    companion object {
        /**
         * Executes an external Python file with the specified arguments and returns its output.
         *
         * @param filePath The absolute path to the .py file.
         * @param args A list of arguments to pass to the script (excluding the script name).
         * @return The output captured from the script's stdout.
         */
        fun runPythonScriptWithArgs(filePath: String, args: List<String>, context: Context, applicationContext: Context): String {
            val scriptFile = File(filePath)
            if (!scriptFile.exists() || !scriptFile.canRead()) {
                throw IllegalArgumentException("File at $filePath does not exist or cannot be read.")
            }
            val script = scriptFile.readText()
            val py = Python.getInstance()

            // Get the __main__ module and its globals dictionary.
            val mainModule = py.getModule("__main__")
            val globals = mainModule.get("__dict__")

            // This wrapper code sets sys.argv, adds the script's directory to sys.path,
            // and captures stdout while executing the script.
            val wrapperCode = """
                import sys, io, os
                
                context = None
                applicationContext = None
                global context
                global applicationContext
                def __run_script(script, argv, c, appC):
                    # Insert the script directory into sys.path.
                    script_dir = os.path.dirname(argv[0])
                    if script_dir and script_dir not in sys.path:
                        sys.path.insert(0, script_dir)
                        pass
                    sys.argv = argv
                    old_stdout = sys.stdout
                    sys.stdout = io.StringIO()
                    try:
                        exec(script, {})
                        result = sys.stdout.getvalue()
                    finally:
                        sys.stdout = old_stdout
                    return result
            """.trimIndent()

            // Execute the wrapper code in the __main__ module's globals.
            py.getBuiltins().callAttr("exec", wrapperCode, globals)

            // Prepare sys.argv. We use the absolute path so that the wrapper code can extract the directory.
            val argv = listOf(scriptFile.absolutePath) + args

            // Execute the helper function to run the script with the given arguments.
            val result = mainModule.callAttr("__run_script", script, argv.toTypedArray(), context, applicationContext)
            return result.toString()
        }
    }
}
