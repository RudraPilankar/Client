package com.client.services.socks5proxy

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.client.R
import com.client.services.client.KILL_SELF_BROADCAST
import java.io.File
import java.io.FileOutputStream
import java.io.IOException


class Socks5Service : Service() {
    inner class Socks5Binder : Binder() {
        val service: Socks5Service
            get() = this@Socks5Service
    }

    private val mBinder: IBinder = Socks5Binder()
    private var started = false

    override fun onBind(intent: Intent): IBinder? {
        return mBinder
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        if (intent != null && ACTION_STOP == intent.action) {
            stopService()
            return START_NOT_STICKY
        }

        val killSelfBroadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                Log.d("Socks5Service", "Received kill self broadcast")
                android.os.Process.killProcess(android.os.Process.myPid())
            }
        }
        registerReceiver(killSelfBroadcastReceiver, IntentFilter(KILL_SELF_BROADCAST), RECEIVER_EXPORTED)

        startService()
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    fun startService() {
        if (started) return

        val prefs: Preferences = Preferences(this)

        val file = File(cacheDir, "socks5.conf")
        try {
            file.createNewFile()
            val fos = FileOutputStream(file, false)

            var conf = ((((((((("""main:
  workers: ${prefs.workers}""").toString() + "\n" +
                    "  port: " + prefs.listenPort).toString() + "\n" +
                    "  listen-address: '" + prefs.listenAddress).toString() + "'\n" +
                    "  udp-port: " + prefs.uDPListenPort).toString() + "\n" +
                    "  udp-listen-address: '" + prefs.uDPListenAddress).toString() + "'\n" +
                    "  listen-ipv6-only: " + prefs.listenIPv6Only).toString() + "\n" +
                    "  bind-address-v4: '" + prefs.bindIPv4Address).toString() + "'\n" +
                    "  bind-address-v6: '" + prefs.bindIPv6Address).toString() + "'\n" +
                    "  bind-interface: '" + prefs.bindInterface).toString() + "'\n"

            conf += ("""misc:
  task-stack-size: ${prefs.taskStackSize}""").toString() + "\n"

            if (!prefs.authUsername?.isEmpty()!! &&
                !prefs.authPassword?.isEmpty()!!
            ) {
                conf += (("""auth:
  username: '${prefs.authUsername}""").toString() + "'\n" +
                        "  password: '" + prefs.authPassword).toString() + "'\n"
            }

            fos.write(conf.toByteArray())
            fos.close()
        } catch (e: IOException) {
            return
        }
        Socks5StartService(file.absolutePath)
        prefs.enable = true
        started = true

        val channelName = "socks5"
        initNotificationChannel(channelName)
        createNotification(channelName)
    }

    fun stopService() {
        if (!started) return

        stopForeground(true)
        Socks5StopService()
        System.exit(0)
    }

    private fun createNotification(channelName: String) {
        val i = Intent(this, Socks5Service::class.java)
        val pi = PendingIntent.getService(this, 0, i, PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(this, channelName)
        val notify = notification
            .setContentTitle(getString(R.string.app_name))
            .setSmallIcon(android.R.drawable.sym_def_app_icon)
            .setContentIntent(pi)
            .build()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(1, notify)
        } else {
            startForeground(1, notify, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        }
    }

    private fun initNotificationChannel(channelName: String) {
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name: CharSequence = getString(R.string.app_name)
            val channel =
                NotificationChannel(channelName, name, NotificationManager.IMPORTANCE_DEFAULT)
            notificationManager.createNotificationChannel(channel)
        }
    }

    companion object {
        @JvmStatic
        private external fun Socks5StartService(config_path: String)
        @JvmStatic
        private external fun Socks5StopService()

        const val ACTION_START: String = "hev.socks5.START"
        const val ACTION_STOP: String = "hev.socks5.STOP"

        init {
            System.loadLibrary("hev-socks5-server")
        }
    }
}
