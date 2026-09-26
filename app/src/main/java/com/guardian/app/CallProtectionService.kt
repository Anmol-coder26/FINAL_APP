package com.guardian.app

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.core.app.NotificationCompat

class CallProtectionService : Service() {

    companion object {
        const val CHANNEL_ID = "guardian_call_protection_fg"
        const val NOTIFICATION_ID = 9110
        const val ACTION_CALL_CONNECTED = "com.guardian.app.CALL_CONNECTED"
        const val ACTION_CALL_RINGING = "com.guardian.app.CALL_RINGING"
        const val EXTRA_NUMBER = "extra_caller_number"
    }

    private var windowManager: WindowManager? = null
    private var floatingView: View? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_CALL_CONNECTED
        val number = intent?.getStringExtra(EXTRA_NUMBER).orEmpty()

        val notification = buildForegroundNotification(action, number)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    startForeground(
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                    )
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
            } catch (e: Exception) {
                Log.w("GuardianCallService", "Fallback startForeground: ${e.message}")
                try {
                    startForeground(NOTIFICATION_ID, notification)
                } catch (e2: Exception) {
                    Log.e("GuardianCallService", "Failed to startForeground: ${e2.message}")
                }
            }
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        // Show floating overlay if permitted
        showFloatingCallCard(action)

        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Guardian Call Scam Defense",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Active foreground call protection and live speech scam analysis."
                setShowBadge(true)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildForegroundNotification(action: String, number: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            1001,
            Intent(this, CallRiskActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (action == ACTION_CALL_RINGING) {
            "Incoming Call Security Screening"
        } else {
            "Guardian Active Call Protection"
        }

        val text = if (number.isNotBlank()) {
            "Call from $number. Tap to activate Dual-Engine Scam Defense."
        } else {
            "Put call on speakerphone to analyze conversation in real-time."
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$text\nGuardian inspects speech locally for extortion, digital arrest, and OTP fraud."))
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_media_play, "Open AI Scanner", pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            .build()
    }

    @SuppressLint("InflateParams")
    private fun showFloatingCallCard(action: String) {
        if (!Settings.canDrawOverlays(this)) return

        if (floatingView != null) return

        try {
            windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutFlag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.END
                x = 30
                y = 180
            }

            val card = android.widget.LinearLayout(this).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                setPadding(28, 20, 28, 20)
                val bgDrawable = android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = 24f
                    setColor(0xFF111726.toInt())
                    setStroke(2, 0xFF10B981.toInt())
                }
                background = bgDrawable

                val title = TextView(this@CallProtectionService).apply {
                    text = "🛡️ Guardian Shield"
                    textSize = 13f
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    setTextColor(0xFF10B981.toInt())
                }
                addView(title)

                val subtext = TextView(this@CallProtectionService).apply {
                    text = "Call Active • Tap to scan"
                    textSize = 10f
                    setTextColor(0xFF94A3B8.toInt())
                    setPadding(0, 4, 0, 8)
                }
                addView(subtext)

                val btn = Button(this@CallProtectionService).apply {
                    text = "Analyze"
                    textSize = 11f
                    setOnClickListener {
                        val intent = Intent(this@CallProtectionService, CallRiskActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        startActivity(intent)
                    }
                }
                addView(btn)
            }

            floatingView = card
            windowManager?.addView(card, params)
        } catch (e: Exception) {
            Log.e("GuardianCallService", "Failed to show floating card: ${e.message}")
        }
    }

    override fun onDestroy() {
        floatingView?.let {
            try {
                windowManager?.removeView(it)
            } catch (_: Exception) {}
            floatingView = null
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
