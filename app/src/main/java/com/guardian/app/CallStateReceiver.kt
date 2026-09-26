package com.guardian.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.content.ContextCompat

class CallStateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED &&
            intent.action != "android.intent.action.PHONE_STATE") {
            return
        }

        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
        Log.d("GuardianCallState", "CallStateReceiver onReceive state: $state")

        when (state) {
            TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                // Call connected / active
                val svcIntent = Intent(context, CallProtectionService::class.java).apply {
                    action = CallProtectionService.ACTION_CALL_CONNECTED
                }
                try {
                    ContextCompat.startForegroundService(context, svcIntent)
                } catch (e: Exception) {
                    Log.e("GuardianCallState", "Failed to start CallProtectionService: ${e.message}")
                }
            }

            TelephonyManager.EXTRA_STATE_RINGING -> {
                // Incoming call ringing
                val incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER).orEmpty()
                val svcIntent = Intent(context, CallProtectionService::class.java).apply {
                    action = CallProtectionService.ACTION_CALL_RINGING
                    putExtra(CallProtectionService.EXTRA_NUMBER, incomingNumber)
                }
                try {
                    ContextCompat.startForegroundService(context, svcIntent)
                } catch (e: Exception) {
                    Log.e("GuardianCallState", "Failed to start CallProtectionService on ringing: ${e.message}")
                }
            }

            TelephonyManager.EXTRA_STATE_IDLE -> {
                // Call ended
                try {
                    context.stopService(Intent(context, CallProtectionService::class.java))
                } catch (e: Exception) {
                    Log.e("GuardianCallState", "Failed to stop CallProtectionService: ${e.message}")
                }
            }
        }
    }
}
