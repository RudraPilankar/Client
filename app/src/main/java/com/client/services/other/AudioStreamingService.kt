package com.client.services.other

import android.app.Service
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.BufferedInputStream
import java.io.IOException
import java.net.Socket

class AudioStreamingService : Service() {

    private val TAG = "AudioStreamingService"
    private var SERVER_IP = "YOUR_SERVER_IP" // Replace with your server's IP address
    private var SERVER_PORT = 12345 // Replace with your server's port
    private val SAMPLE_RATE = 44100
    private val CHANNEL_CONFIG = AudioFormat.CHANNEL_OUT_MONO
    private val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    private val BUFFER_SIZE = AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
    private var audioTrack: AudioTrack? = null
    private var socket: Socket? = null
    private var isStreaming = false
    private var streamingJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        SERVER_IP = intent!!.getStringExtra("ServerIP").toString()
        SERVER_PORT = intent.getIntExtra("ServerPort", -1)
        if (SERVER_PORT == -1)
            stopSelf()
        else
            startStreaming()
        return START_REDELIVER_INTENT
    }

    private fun startStreaming() {
        if (isStreaming) return

        isStreaming = true
        streamingJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                socket = Socket(SERVER_IP, SERVER_PORT)
                Log.d(TAG, "Connected to server: ${socket?.inetAddress?.hostAddress}:${socket?.port}")

                val inputStream = BufferedInputStream(socket!!.getInputStream())
                audioTrack = AudioTrack(
                    AudioManager.STREAM_MUSIC,
                    SAMPLE_RATE,
                    CHANNEL_CONFIG,
                    AUDIO_FORMAT,
                    BUFFER_SIZE,
                    AudioTrack.MODE_STREAM
                )
                audioTrack?.play()

                val buffer = ByteArray(BUFFER_SIZE)
                var bytesRead: Int = 0
                while (isStreaming && inputStream.read(buffer).also { bytesRead = it } != -1) {
                    if (bytesRead > 0) {
                        audioTrack?.write(buffer, 0, bytesRead)
                    }
                }
            } catch (e: IOException) {
                Log.e(TAG, "Error during audio streaming: ${e.message}")
            } finally {
                stopStreaming()
            }
        }
    }

    private fun stopStreaming() {
        isStreaming = false
        streamingJob?.cancel()
        streamingJob = null

        audioTrack?.stop()
        audioTrack?.release()
        audioTrack = null

        try {
            socket?.close()
        } catch (e: IOException) {
            Log.e(TAG, "Error closing socket: ${e.message}")
        }
        socket = null
        Log.d(TAG, "Streaming stopped.")
    }

    override fun onDestroy() {
        super.onDestroy()
        stopStreaming()
    }
}
