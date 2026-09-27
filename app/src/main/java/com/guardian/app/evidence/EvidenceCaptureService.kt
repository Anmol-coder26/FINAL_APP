package com.guardian.app.evidence

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider

class EvidenceCaptureService : Service() {

    private var mediaProjection: MediaProjection? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildForegroundNotification())

        val resultCode = intent?.getIntExtra("resultCode", 0) ?: 0
        @Suppress("DEPRECATION")
        val data = intent?.getParcelableExtra<Intent>("data")
        val riskScore = intent?.getIntExtra("riskScore", 0) ?: 0
        val callerId = intent?.getStringExtra("callerId") ?: "Unknown"
        val sourcePackage = intent?.getStringExtra("packageName") ?: "Unknown"
        val transcript = intent?.getStringExtra("transcript") ?: ""

        if (data == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        val mpm = getSystemService(MediaProjectionManager::class.java)
        mediaProjection = mpm.getMediaProjection(resultCode, data)

        captureFrame { bitmap ->
            val pdfFile = EvidencePdfGenerator.generate(
                context = this,
                screenshot = bitmap,
                riskScore = riskScore,
                callerId = callerId,
                sourcePackage = sourcePackage,
                transcript = transcript
            )
            notifyEvidenceReady(pdfFile)
            stopSelf()
        }

        return START_NOT_STICKY
    }

    private fun captureFrame(onCaptured: (Bitmap) -> Unit) {
        val metrics = resources.displayMetrics
        val width = metrics.widthPixels
        val height = metrics.heightPixels

        val imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)

        val virtualDisplay = mediaProjection?.createVirtualDisplay(
            "GuardianEvidence",
            width, height, metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader.surface, null, null
        )

        Handler(Looper.getMainLooper()).postDelayed({
            val image = imageReader.acquireLatestImage()
            if (image != null) {
                val plane = image.planes[0]
                val buffer = plane.buffer
                val pixelStride = plane.pixelStride
                val rowStride = plane.rowStride
                val rowPadding = rowStride - pixelStride * width

                val bmp = Bitmap.createBitmap(
                    width + rowPadding / pixelStride,
                    height,
                    Bitmap.Config.ARGB_8888
                )
                bmp.copyPixelsFromBuffer(buffer)
                val cropped = Bitmap.createBitmap(bmp, 0, 0, width, height)

                image.close()
                virtualDisplay?.release()
                mediaProjection?.stop()

                onCaptured(cropped)
            } else {
                virtualDisplay?.release()
                mediaProjection?.stop()
                stopSelf()
            }
        }, 800)
    }

    private fun buildForegroundNotification(): Notification {
        createChannel()
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Guardian")
            .setContentText("Capturing evidence...")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun notifyEvidenceReady(file: java.io.File) {
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        val openIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val pending = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Evidence report ready")
            .setContentText("Tap to view your Guardian report")
            .setSmallIcon(android.R.drawable.ic_menu_save)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                    .notify(EVIDENCE_NOTIFICATION_ID, notification)
            }
        } else {
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                .notify(EVIDENCE_NOTIFICATION_ID, notification)
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Guardian Evidence",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_ID = "guardian_evidence"
        const val NOTIFICATION_ID = 2
        const val EVIDENCE_NOTIFICATION_ID = 3
    }
}
