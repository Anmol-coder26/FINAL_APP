package com.guardian.app.callprotect

import android.content.Context

object AutoBlockPrefs {
    private const val PREFS_NAME = "guardian_call_screening_prefs"
    private const val KEY_AUTO_BLOCK = "auto_block_enabled"

    fun isEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_AUTO_BLOCK, true)
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_AUTO_BLOCK, enabled).apply()
    }
}

object CallLogStore {
    suspend fun record(context: Context, number: String, actionTaken: String) {
        val repo = NumberReputationRepository(context)
        repo.saveCallHistory(
            CallHistoryEntry(
                number = number,
                timestamp = System.currentTimeMillis(),
                riskScore = 95,
                topSignals = "Known scam/spam database match",
                transcriptSummary = "Call intercepted and screened by Guardian CallScreeningService before ringing.",
                actionTaken = actionTaken,
                wasUserReported = false
            )
        )
    }
}
