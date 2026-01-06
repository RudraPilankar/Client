package com.client.services.ftpserver

import android.content.Context
import android.util.Log
import java.io.IOException
import java.net.ServerSocket


class MyFTPServer(private val context: Context) {
    private val controlPort = 1025
    private var welcomeSocket: ServerSocket? = null
    var serverRunning: Boolean = true

    fun run() {
        welcomeSocket = ServerSocket(controlPort)

        try {
            var noOfThreads = 0;
            Log.d("MyFTPServer", "FTP server started on port $controlPort")

            while (serverRunning) {
                try {
                    val client = welcomeSocket!!.accept()

                    // Port for incoming dataConnection (for passive mode) is the controlPort +
                    // number of created threads + 1
                    val dataPort: Int = controlPort + noOfThreads + 1

                    // Create new worker thread for new connection
                    val w = MyFTPWorker(context, client, dataPort)
                    Log.d("MyFTPServer", "New connection received. Worker was created.")
                    noOfThreads++
                    w.start()
                } catch (e: IOException) {
                    Log.e("MyFTPServer", "Problem with client connection")
                    e.printStackTrace()
                }
            }
        } catch (ex: Exception) {
            try {
                welcomeSocket!!.close()
                Log.d("MyFTPServer", "FTP server stopped")
            } catch (e: IOException) {
                Log.e("MyFTPServer", "Problem with closing server socket")
                e.printStackTrace()
            }
        }
    }

    fun stop() {
        serverRunning = false
        welcomeSocket!!.close()
    }
}