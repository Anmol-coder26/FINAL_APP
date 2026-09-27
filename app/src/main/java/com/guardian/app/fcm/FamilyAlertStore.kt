package com.guardian.app.fcm

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class FamilyAlertEntry(
    val alertId: String,
    val protectedUserName: String,
    val riskScore: Int,
    val scamType: String,
    val callerNumber: String,
    val transcriptSummary: String,
    val timestamp: Long
)

object FamilyAlertStore {
    private const val PREFS_NAME = "guardian_family_alerts_store"
    private const val KEY_ALERTS = "saved_family_alerts"

    fun save(
        context: Context,
        alertId: String,
        protectedUserName: String,
        riskScore: Int,
        scamType: String,
        callerNumber: String,
        transcriptSummary: String,
        timestamp: Long
    ) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val rawJson = prefs.getString(KEY_ALERTS, "[]") ?: "[]"
        val array = try { JSONArray(rawJson) } catch (_: Exception) { JSONArray() }

        val newObj = JSONObject().apply {
            put("alertId", alertId)
            put("protectedUserName", protectedUserName)
            put("riskScore", riskScore)
            put("scamType", scamType)
            put("callerNumber", callerNumber)
            put("transcriptSummary", transcriptSummary)
            put("timestamp", timestamp)
        }
        array.put(newObj)

        prefs.edit().putString(KEY_ALERTS, array.toString()).apply()
    }

    fun getAll(context: Context): List<FamilyAlertEntry> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val rawJson = prefs.getString(KEY_ALERTS, "[]") ?: "[]"
        val list = mutableListOf<FamilyAlertEntry>()
        try {
            val array = JSONArray(rawJson)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    FamilyAlertEntry(
                        alertId = obj.optString("alertId", ""),
                        protectedUserName = obj.optString("protectedUserName", "Family Member"),
                        riskScore = obj.optInt("riskScore", 0),
                        scamType = obj.optString("scamType", "Unknown"),
                        callerNumber = obj.optString("callerNumber", "Unknown"),
                        transcriptSummary = obj.optString("transcriptSummary", ""),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                    )
                )
            }
        } catch (_: Exception) {}
        return list.reversed()
    }
}
