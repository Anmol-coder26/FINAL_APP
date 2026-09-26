package com.guardian.app.voip

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.guardian.app.MainActivity

class VoipCallService : Service() {

    companion object {
        const val CHANNEL_ID = "guardian_voip_service"
        const val NOTIFICATION_ID = 2002
        const val ACTION_START = "com.guardian.app.voip.ACTION_START"
        const val ACTION_STOP = "com.guardian.app.voip.ACTION_STOP"
        const val EXTRA_CHANNEL = "extra_channel"

        fun startService(context: Context, channelName: String) {
            val intent = Intent(context, VoipCallService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_CHANNEL, channelName)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, VoipCallService::class.java).apply {
                action = ACTION_STOP
            }
            context.stopService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        val channelName = intent?.getStringExtra(EXTRA_CHANNEL) ?: "Secure VoIP Call"
        val notification = buildNotification(channelName)

        val foregroundTypes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL
        } else {
            0
        }

        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, foregroundTypes)
        return START_STICKY
    }

    private fun buildNotification(channelName: String): Notification {
        val tapIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Guardian Secure VoIP Call")
            .setContentText("Active monitoring for scam tactics in channel: $channelName")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Guardian VoIP Call Protection",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows status of ongoing encrypted secure VoIP calls"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
