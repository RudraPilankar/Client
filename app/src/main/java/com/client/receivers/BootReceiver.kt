package com.client.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.client.services.client.ClientService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        Log.d("BootReceiver", "Starting client service")
        Intent(context, ClientService::class.java).also {
            it.action = ClientService.Actions.START.toString()
            context?.startForegroundService(it)
        }
    }
}