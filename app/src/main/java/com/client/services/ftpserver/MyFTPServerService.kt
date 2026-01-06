package com.client.services.ftpserver

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log

class MyFTPServerService : Service() {
    companion object {
        const val ACTION_START_FTP_SERVER = "com.client.MyFTPServerService.ACTION_START_FTP_SERVER"
        const val ACTION_STOP_FTP_SERVER = "com.client.MyFTPServerService.ACTION_STOP_FTP_SERVER"
    }

    var server: MyFTPServer? = null

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("MyFTPServerService", "onStartCommand")
        if (intent?.action == ACTION_START_FTP_SERVER) {
            Thread(Runnable {
                startServer()
            }).start()
        } else if (intent?.action == ACTION_STOP_FTP_SERVER) {
            server?.stop()
        } else {
            Log.d("MyFTPServerService", "Unknown action: ${intent?.action}")
        }
        return START_NOT_STICKY
    }

    fun startServer() {
        server = MyFTPServer(this)
        server?.run()
    }
}