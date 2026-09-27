package com.guardian.app.fcm

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.guardian.app.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class GuardianFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("GuardianFCM", "New FCM token: ${token.take(20)}...")
        sendTokenToBackend(token)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d("GuardianFCM", "Message received from: ${remoteMessage.from}")

        // Handle data payload
        val data = remoteMessage.data
        if (data.isNotEmpty()) {
            Log.d("GuardianFCM", "Data payload: $data")
            handleFamilyAlert(data)
        }

        // Handle notification payload (if present)
        remoteMessage.notification?.let {
            Log.d("GuardianFCM", "Notification: ${it.title} - ${it.body}")
        }
    }

    private fun handleFamilyAlert(data: Map<String, String>) {
        val alertType = data["alert_type"] ?: "scam_detected"
        val protectedUserName = data["protected_user"] ?: "Family member"
        val riskScore = data["risk_score"]?.toIntOrNull() ?: 0
        val scamType = data["scam_type"] ?: "Unknown"
        val callerNumber = data["caller_number"] ?: "Unknown"
        val transcriptSummary = data["transcript_summary"] ?: ""

        // Show a local notification with detailed info
        GuardianNotificationHelper.showFamilyAlert(
            context = this,
            alertType = alertType,
            protectedUserName = protectedUserName,
            riskScore = riskScore,
            scamType = scamType,
            callerNumber = callerNumber,
            transcriptSummary = transcriptSummary
        )

        // Save to local database for the in-app report
        CoroutineScope(Dispatchers.IO).launch {
            FamilyAlertStore.save(
                context = applicationContext,
                alertId = data["alert_id"] ?: System.currentTimeMillis().toString(),
                protectedUserName = protectedUserName,
                riskScore = riskScore,
                scamType = scamType,
                callerNumber = callerNumber,
                transcriptSummary = transcriptSummary,
                timestamp = System.currentTimeMillis()
            )
        }
    }

    fun sendTokenToBackend(token: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val payload = JSONObject().apply {
                    put("fcmToken", token)
                    put("userId", getUserId())
                    put("timestamp", System.currentTimeMillis())
                }

                val request = Request.Builder()
                    .url("${BuildConfig.BACKEND_URL}/family/register")
                    .post(payload.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                OkHttpClient().newCall(request).execute()
                Log.d("GuardianFCM", "Token registered with backend")
            } catch (e: Exception) {
                Log.e("GuardianFCM", "Token registration failed", e)
            }
        }
    }

    private fun getUserId(): String {
        return getSharedPreferences("guardian_secure_prefs", MODE_PRIVATE)
            .getString("user_id", "anonymous") ?: "anonymous"
    }
}
