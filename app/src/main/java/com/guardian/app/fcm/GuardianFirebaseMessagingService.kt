package com.guardian.app.fcm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.guardian.app.BuildConfig
import com.guardian.app.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * F27 Family Alert via FCM & Backend Push
 */
class GuardianFirebaseMessagingService {

    companion object {
        private const val TAG = "FamilyAlertService"
        private const val CHANNEL_ID = "guardian_family_channel"

        /**
         * Register device FCM token with the Guardian backend server
         */
        fun registerTokenWithBackend(context: Context, token: String, deviceName: String = Build.MODEL) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val client = OkHttpClient()
                    val payload = JSONObject().apply {
                        put("token", token)
                        put("deviceName", deviceName)
                    }
                    val request = Request.Builder()
                        .url("${BuildConfig.BACKEND_URL}/alerts/family/register")
                        .post(payload.toString().toRequestBody("application/json".toMediaType()))
                        .build()

                    val resp = client.newCall(request).execute()
                    if (resp.isSuccessful) {
                        Log.d(TAG, "Device registered for family alerts with token: ${token.take(8)}...")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to register family FCM token: ${e.message}")
                }
            }
        }

        /**
         * Display high-priority family emergency alert notification
         */
        fun showFamilyAlertNotification(
            context: Context,
            contactName: String,
            callerNumber: String,
            riskScore: Int,
            signals: List<String>
        ) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Guardian Family Emergency Alerts",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Instant notifications when high-risk calls target family members"
                    enableVibration(true)
                }
                manager.createNotificationChannel(channel)
            }

            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                System.currentTimeMillis().toInt(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val title = "🚨 Guardian Family Alert: $contactName"
            val text = "Suspicious call from $callerNumber ($riskScore% Risk). Signals: ${signals.joinToString(", ")}"

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()

            manager.notify(System.currentTimeMillis().toInt(), notification)
        }
    }
}
