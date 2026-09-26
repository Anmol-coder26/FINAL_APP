package com.guardian.app.protect.advanced

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

object TrustedContactAlertSender {
    // Rate limit: max 1 per call, max 5 per hour
    private var lastSentAt: Long = 0L
    private var sentThisHour = 0
    private var hourStart: Long = 0L

    suspend fun sendAlert(
        context: Context,
        callerNumber: String,
        riskScore: Int,
        topSignals: List<String>,
        backendUrl: String
    ): Boolean = withContext(Dispatchers.IO) {
        val contact = TrustedContactManager.load(context) ?: return@withContext false
        if (!contact.enabled) return@withContext false

        // Rate limit
        val now = System.currentTimeMillis()
        if (now - lastSentAt < 60_000L) return@withContext false
        if (now - hourStart > 3_600_000L) {
            hourStart = now
            sentThisHour = 0
        }
        if (sentThisHour >= 5) return@withContext false

        val payload = JSONObject().apply {
            put("to", contact.phone)
            put("contactName", contact.name)
            put("callerNumber", callerNumber)
            put("riskScore", riskScore)
            put("signals", JSONArray(topSignals))
            put("timestamp", now)
        }

        try {
            val client = OkHttpClient()
            val request = Request.Builder()
                .url("$backendUrl/alerts/trusted-contact")
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()
            val response = client.newCall(request).execute()
            val success = response.isSuccessful

            if (success) {
                lastSentAt = now
                sentThisHour += 1
            }
            success
        } catch (e: Exception) {
            false
        }
    }
}
