package com.guardian.app.callprotect

import android.content.Context
import android.content.Intent
import android.net.Uri
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ReportToCybercrime {
    fun report(context: Context, entry: CallHistoryEntry) {
        val dateStr = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(entry.timestamp))
        val text = """
            Scam call reported by Guardian AI.
            Caller Number: ${entry.number}
            Incident Timestamp: $dateStr
            Risk Score: ${entry.riskScore}%
            Threat Signals: ${entry.topSignals}
            Transcript / Context: ${entry.transcriptSummary}
            Action Taken: ${entry.actionTaken}
        """.trimIndent()

        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("https://cybercrime.gov.in")
            putExtra(Intent.EXTRA_TEXT, text)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            context.startActivity(intent)
        } catch (_: Exception) {}
    }
}
