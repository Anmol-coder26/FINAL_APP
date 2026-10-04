package com.guardian.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.IBinder
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import androidx.core.app.NotificationCompat

internal object GuardianNotifications {
    private const val channelId = "guardian_protection"

    fun warn(context: Context, title: String, message: String) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                channelId,
                "Guardian protection",
                NotificationManager.IMPORTANCE_HIGH
            )
        )
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        manager.notify((title + message).hashCode(), notification)
    }

    fun callConnected(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                channelId,
                "Guardian protection",
                NotificationManager.IMPORTANCE_HIGH
            )
        )
        val pendingIntent = PendingIntent.getActivity(
            context,
            420,
            Intent(context, CallRiskActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Call connected")
            .setContentText("Put the call on speaker, open SuSagi, and tap Start.")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "Turn on speakerphone, open SuSagi and tap Start live transcription. " +
                        "Speech is sent to the selected recognition provider. Same-phone capture may be blocked; a nearby listening device can be used."
                )
            )
            .setContentIntent(pendingIntent)
            .addAction(
                android.R.drawable.ic_media_play,
                "Analyze call",
                pendingIntent
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        manager.notify("call-connected".hashCode(), notification)
    }
    fun callEnded(context: Context) {
        context.getSystemService(NotificationManager::class.java).cancel("call-connected".hashCode())
    }
}

class CallMonitorService : Service() {
    private val callListener = object : PhoneStateListener() {
        override fun onCallStateChanged(state: Int, phoneNumber: String?) {
            when (state) {
                TelephonyManager.CALL_STATE_RINGING -> {
                    GuardianNotifications.warn(
                        this@CallMonitorService,
                        "Incoming call to review",
                        "Guardian noticed an incoming call. Verify the caller before sharing information."
                    )
                }
                TelephonyManager.CALL_STATE_OFFHOOK -> {
                    GuardianNotifications.callConnected(this@CallMonitorService)
                }
                TelephonyManager.CALL_STATE_IDLE -> GuardianNotifications.callEnded(this@CallMonitorService)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        val telephony = getSystemService(TelephonyManager::class.java)
        if (checkSelfPermission(android.Manifest.permission.READ_PHONE_STATE) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            telephony.listen(callListener, PhoneStateListener.LISTEN_CALL_STATE)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int =
        START_STICKY

    override fun onDestroy() {
        getSystemService(TelephonyManager::class.java)
            .listen(callListener, PhoneStateListener.LISTEN_NONE)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

class MessageListenerService : NotificationListenerService() {
    override fun onNotificationPosted(statusBarNotification: StatusBarNotification) {
        if (statusBarNotification.packageName == packageName) return
        val extras = statusBarNotification.notification.extras
        val title = extras.getCharSequence("android.title")?.toString().orEmpty()
        val text = extras.getCharSequence("android.text")?.toString().orEmpty()
        val message = "$title $text".trim()
        if (SuspiciousMessageDetector.isSuspicious(message)) {
            GuardianNotifications.warn(
                this,
                "Message needs a second look",
                "This notification contains language commonly used in urgent scams. Do not share an OTP, PIN, or payment."
            )
        }
    }
}

internal object SuspiciousMessageDetector {
    private val warningTerms = listOf(
        // Credentials / OTP
        "otp", "one time password", "verification code", "cvv", "security pin", "password", "ओटीपी",
        // Banking & KYC
        "verify your account", "kyc", "kyc update", "kyc expire", "pan card", "blocked account",
        "account suspended", "debit card block", "khata block", "rbi", "sbi", "hdfc", "icici",
        // Electricity & Utility
        "electricity", "power cut", "bijli bill", "bill unpaid", "disconnection", "bijli cut", "bijli vibhag",
        // Urgency & Law Enforcement
        "urgent", "arrest", "police", "cbi", "fir registered", "digital arrest", "legal action", "immediately",
        // Payments & Rewards
        "upi", "send money", "transfer money", "claim reward", "lottery", "cashback", "you have won",
        "telegram task", "youtube like", "part time job", "kbc",
        // Remote access & malicious downloads
        "click this link", "remote access", "anydesk", "teamviewer", "rustdesk", "quicksupport", "download apk"
    )

    fun isSuspicious(message: String): Boolean {
        val normalized = message.lowercase()
        return warningTerms.any(normalized::contains) ||
            Regex("""https?://\S+""").containsMatchIn(normalized)
    }
}