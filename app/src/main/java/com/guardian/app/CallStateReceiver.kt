package com.guardian.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import android.util.Log

/** A call notification invites a visible user action; it never starts microphone capture. */
class CallStateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return
        try {
            when (intent.getStringExtra(TelephonyManager.EXTRA_STATE)) {
                TelephonyManager.EXTRA_STATE_OFFHOOK -> GuardianNotifications.callConnected(context)
                TelephonyManager.EXTRA_STATE_RINGING -> GuardianNotifications.warn(
                    context, "Incoming call to review",
                    "Answer the call and enable speakerphone. Open SuSagi to start live transcription."
                )
                TelephonyManager.EXTRA_STATE_IDLE -> {
                    GuardianNotifications.callEnded(context)
                    context.stopService(Intent(context, CallProtectionService::class.java))
                }
            }
        } catch (_: SecurityException) {
            Log.w("GuardianCallState", "Call notification permission unavailable; open Live Defense manually")
        }
    }
}
