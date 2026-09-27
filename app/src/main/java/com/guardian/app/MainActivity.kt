package com.guardian.app

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.ui.graphics.Color
import com.guardian.app.ui.theme.GuardianTheme
import com.guardian.app.ui.theme.GxBase
import com.guardian.app.ui.theme.GxBorder
import com.guardian.app.ui.theme.GxDanger
import com.guardian.app.ui.theme.GxDangerSoft
import com.guardian.app.ui.theme.GxPrimary
import com.guardian.app.ui.theme.GxPrimaryGlow
import com.guardian.app.ui.theme.GxSafe
import com.guardian.app.ui.theme.GxSurface
import com.guardian.app.ui.theme.GxSurfaceAlt
import com.guardian.app.ui.theme.GxTextHi
import com.guardian.app.ui.theme.GxTextLo
import com.guardian.app.ui.theme.GxTextMid
import com.guardian.app.ui.theme.GxVoid
import com.guardian.app.ui.theme.GxWarning

class MainActivity : ComponentActivity() {
    private val protectionPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            if (permissions[Manifest.permission.READ_PHONE_STATE] == true) {
                try {
                    startService(Intent(this, CallMonitorService::class.java))
                } catch (_: Exception) {}
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            com.google.firebase.messaging.FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                if (!task.isSuccessful) {
                    android.util.Log.w("GuardianFCM", "Fetching FCM token failed", task.exception)
                    return@addOnCompleteListener
                }
                val token = task.result
                android.util.Log.d("GuardianFCM", "Current FCM token: ${token.take(20)}...")
                com.guardian.app.fcm.GuardianFirebaseMessagingService().sendTokenToBackend(token)
            }
        } catch (e: Exception) {
            android.util.Log.e("GuardianFCM", "Firebase messaging init fallback", e)
        }
        setContent {
            GuardianTheme {
                GuardianApp(
                    onOpenCallRisk = {
                        startActivity(Intent(this, CallRiskActivity::class.java))
                    },
                    onOpenQrScanner = {
                        startActivity(Intent(this, QrScannerActivity::class.java))
                    },
                    onPhoneProtectionToggle = { enabled ->
                        if (enabled) {
                            protectionPermissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.READ_PHONE_STATE,
                                    Manifest.permission.POST_NOTIFICATIONS
                                )
                            )
                        } else {
                            try {
                                stopService(Intent(this, CallMonitorService::class.java))
                            } catch (_: Exception) {}
                        }
                    },
                    onMessageProtectionToggle = { enabled ->
                        if (enabled) {
                            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                        } else {
                            try {
                                stopService(Intent(this, MessageListenerService::class.java))
                            } catch (_: Exception) {}
                        }
                    }
                )
            }
        }
    }
}

// Design Token Aliases for backward-compatibility
val DarkBackground = GxBase
val DarkSurface = GxSurface
val DarkSurfaceElevated = GxSurfaceAlt
val DarkSurfaceVariant = GxSurfaceAlt
val CyberEmerald = GxSafe
val CyberEmeraldGlow = GxPrimaryGlow
val ElectricIndigo = GxPrimary
val CoralRed = GxDanger
val AmberWarning = GxWarning
val TextPrimary = GxTextHi
val TextSecondary = GxTextMid
val TextMuted = GxTextLo
val BorderSubtle = GxBorder