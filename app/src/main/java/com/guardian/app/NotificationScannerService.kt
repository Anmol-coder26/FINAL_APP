package com.guardian.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat

class NotificationScannerService : NotificationListenerService() {

    companion object {
        private const val CHANNEL_ID = "guardian_sms_interception"
        private const val CHANNEL_NAME = "Guardian Scam Message Shield"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null || sbn.packageName == packageName) return

        val extras = sbn.notification.extras ?: return
        val title = extras.getCharSequence("android.title")?.toString().orEmpty()
        val text = extras.getCharSequence("android.text")?.toString().orEmpty()
        val bigText = extras.getCharSequence("android.bigText")?.toString().orEmpty()
        val fullContent = "$title $text $bigText".trim()

        if (fullContent.isBlank()) return

        if (SuspiciousMessageDetector.isSuspicious(fullContent)) {
            val originalPendingIntent: PendingIntent? = sbn.notification.contentIntent

            // 1. Cancel malicious original notification to prevent direct user tap
            try {
                cancelNotification(sbn.key)
            } catch (_: Exception) {}

            // 2. Post safe Guardian interception notification
            postGuardianInterceptNotification(
                packageName = sbn.packageName,
                title = title.ifBlank { "Suspicious Alert" },
                body = fullContent,
                originalPendingIntent = originalPendingIntent
            )
        }
    }

    private fun postGuardianInterceptNotification(
        packageName: String,
        title: String,
        body: String,
        originalPendingIntent: PendingIntent?
    ) {
        val warningIntent = Intent(this, ScamWarningActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("source", packageName)
            putExtra("title", title)
            putExtra("body", body)
            putExtra("score", 85)
            putExtra("explanation", "Guardian detected high-risk scam patterns (credential/OTP theft, impersonation, or malicious link) and blocked direct opening.")
            if (originalPendingIntent != null) {
                putExtra("original_pending_intent", originalPendingIntent)
            }
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            (title + body).hashCode(),
            warningIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("⚠️ Scam Alert Intercepted: $title")
            .setContentText("Guardian intercepted a suspicious notification. Tap to inspect.")
            .setStyle(NotificationCompat.BigTextStyle().bigText("Guardian intercepted a high-risk message targeting sensitive credentials. Tap to inspect safely."))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify((title + body).hashCode(), notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifies when malicious messages or phishing SMS are intercepted"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }
}
