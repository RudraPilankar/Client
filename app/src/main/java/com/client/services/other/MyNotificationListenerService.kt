package com.client.services.other

import android.app.Notification
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.client.services.client.KILL_SELF_BROADCAST
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MyNotificationListenerService: NotificationListenerService() {
    override fun onCreate() {
        super.onCreate()
        val killSelfBroadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                android.os.Process.killProcess(android.os.Process.myPid())
            }
        }
        registerReceiver(killSelfBroadcastReceiver, IntentFilter(KILL_SELF_BROADCAST), RECEIVER_EXPORTED)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        sbn?.let {
            // Package name of the app that posted the notification
            val packageName = it.packageName.toString()

            // Get the notification extras bundle
            val extras = it.notification.extras

            // Extract the title and text from the extras
            val title = extras.getString(Notification.EXTRA_TITLE).toString()
            val text = extras.getCharSequence(Notification.EXTRA_TEXT).toString()

            // Get the post time as a timestamp (in milliseconds)
            val postTime = it.postTime

            // Format the time. Adjust the pattern and locale as needed.
            val formattedTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                .format(Date(postTime))

            Log.i("MyNotificationListenerService", "Package: $packageName")
            Log.i("MyNotificationListenerService", "Title: $title")
            Log.i("MyNotificationListenerService", "Text: $text")
            Log.i("MyNotificationListenerService", "Time: $formattedTime")

            notifications.add(arrayOf(packageName, title, text, formattedTime))
        }
    }
}