package com.client.services.virtualshell

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import android.os.Process
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class VirtualShellService : Service() {

    companion object {
        const val EXTRA_SHELL_PATH = "ScriptPath"
        const val EXTRA_INTERACTIVE_MODE = "EnableInteractiveMode"
        const val ACTION_OUTPUT = "com.client.services.virtualshell.VirtualShellService.OUTPUT"
        const val ACTION_ERROR = "com.client.services.virtualshell.VirtualShellService.ERROR"
        const val ACTION_INPUT = "com.client.services.virtualshell.VirtualShellService.INPUT"
        const val ACTION_KEY_STROKES = "com.client.services.virtualshell.VirtualShellService.INPUT_KEY_STROKES"
        const val ACTION_SHUTDOWN = "com.client.services.virtualshell.VirtualShellService.SHUTDOWN"
        const val EXTRA_OUTPUT = "Output"
        const val EXTRA_ERROR = "Error"
        const val EXTRA_INPUT = "Input"
        const val EXTRA_KEY_STROKES = "KeyStrokes"
        const val EXTRA_DELAY = "Delay"
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var shell: VirtualShell? = null
    private var inputReceiver: BroadcastReceiver? = null
    private var keyStrokesReceiver: BroadcastReceiver? = null
    private var shutdownReceiver: BroadcastReceiver? = null
    private var isShutdownBroadcastReceived = false

    override fun onCreate() {
        super.onCreate()
        // Mark the service as running.
        registerInputReceiver()
        registerKeyStrokesReceiver()
        registerShutdownReceiver()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val shellPath = intent?.getStringExtra(EXTRA_SHELL_PATH)
        val enableInteractiveMode = intent?.getBooleanExtra(EXTRA_INTERACTIVE_MODE, false)

        Log.i("VirtualShellService", "onStartCommand: shellPath = $shellPath")
        if (shellPath.isNullOrEmpty() || enableInteractiveMode == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        serviceScope.launch {
            try {
                shell = VirtualShell({ output ->
                    broadcastOutput(output)
                }, {
                    Log.i("VirtualShellService", "Shell exited")
                    serviceScope.cancel()
                    stopSelf()
                })
                shell?.createVirtualShell(shellPath, enableInteractiveMode)
                while (shell?.isShellRunning() == true) {
                    delay(500)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                broadcastError("Exception in virtual shell service: ${e.localizedMessage}")
            } finally {
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun broadcastError(error: String) {
        val intent = Intent(ACTION_ERROR).apply {
            putExtra(EXTRA_ERROR, error)
        }
        sendBroadcast(intent)
        Log.e("VirtualShellService", error)
    }

    override fun onDestroy() {
        Log.i("VirtualShellService", "On destroy called")
        unregisterInputReceiver()
        unregisterKeyStrokesReceiver()
        unregisterShutdownReceiver()
        serviceScope.cancel()
        isShutdownBroadcastReceived = true
        // Forcefully terminate the process running this service (ensure this service is in its own process).
        Process.killProcess(Process.myPid())
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun broadcastOutput(output: String) {
        Log.i("VirtualShellService", "Output: $output")
        val intent = Intent(ACTION_OUTPUT).apply {
            putExtra(EXTRA_OUTPUT, output)
        }
        sendBroadcast(intent)
    }

    /**
     * Registers a broadcast receiver to listen for input to send to the Virtual Shell script.
     */
    private fun registerInputReceiver() {
        val filter = IntentFilter(ACTION_INPUT)
        inputReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val input = intent?.getStringExtra(EXTRA_INPUT)
                if (!input.isNullOrEmpty()) {
                    try {
                        shell?.sendInput(input)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        broadcastError("Exception in virtual shell service: ${e.localizedMessage}")
                    }
                }
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(inputReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(inputReceiver, filter)
        }
    }

    private fun unregisterInputReceiver() {
        inputReceiver?.let { unregisterReceiver(it) }
    }
    
    /**
     * Registers a broadcast receiver to listen for key strokes to send to the Virtual Shell script.
     */
    private fun registerKeyStrokesReceiver() {
        val filter = IntentFilter(ACTION_KEY_STROKES)
        keyStrokesReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val input = intent?.getStringExtra(EXTRA_KEY_STROKES)
                var delay = intent?.getLongExtra(EXTRA_DELAY, 50)
                if (delay == null)
                    delay = 50
                if (!input.isNullOrEmpty()) {
                    try {
                        shell?.sendKeystrokes(input, delay)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        broadcastError("Exception in virtual shell service: ${e.localizedMessage}")
                    }
                }
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(keyStrokesReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(keyStrokesReceiver, filter)
        }
    }

    private fun unregisterKeyStrokesReceiver() {
        keyStrokesReceiver?.let { unregisterReceiver(it) }
    }

    /**
     * Registers a broadcast receiver to listen for a shutdown signal.
     * When an ACTION_SHUTDOWN broadcast is received, the service stops itself.
     */
    private fun registerShutdownReceiver() {
        val filter = IntentFilter(ACTION_SHUTDOWN)
        shutdownReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                Log.i("VirtualShellService", "Received shutdown signal")
                serviceScope.cancel()
                stopSelf()
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(shutdownReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(shutdownReceiver, filter)
        }
    }

    private fun unregisterShutdownReceiver() {
        shutdownReceiver?.let { unregisterReceiver(it) }
    }
}
