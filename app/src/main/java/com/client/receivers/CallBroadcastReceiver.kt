package com.client.receivers

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.telecom.TelecomManager
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.app.ActivityCompat

val blockedPhoneNumbers = mutableListOf<String>()

class CallBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
        val incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
        Log.d("CallBroadcastReceiver", "State: $state, Incoming Number: $incomingNumber")

        // Check if the call is incoming and if the number matches a blocked number
        if (state == TelephonyManager.EXTRA_STATE_RINGING) {
            if (incomingNumber != null && isBlockedNumber(incomingNumber)) {
                rejectCall(context)
            }
        } else {
            if (incomingNumber != null && isBlockedNumber(incomingNumber)) {
                rejectCall(context)
            }
        }
    }

    // Function to check if the number is in your block list
    private fun isBlockedNumber(phoneNumber: String): Boolean {
        // Compare phoneNumber with your blocked numbers list
        return blockedPhoneNumbers.contains(phoneNumber)
    }

    // Function to reject the incoming call
    private fun rejectCall(context: Context) {
        try {
            val tm = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ANSWER_PHONE_CALLS
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                tm.endCall()
                Log.d("CallBroadcastReceiver", "Call blocked successfully")
            } else {
                Log.e("CallBroadcastReceiver", "Permission not granted to end call")
            }
        } catch (e: Exception) {
            Log.e("CallBroadcastReceiver", "Error blocking the call", e)
        }
    }
}
