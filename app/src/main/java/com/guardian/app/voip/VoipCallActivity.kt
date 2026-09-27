package com.guardian.app.voip

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import com.guardian.app.ui.theme.GuardianTheme

class VoipCallActivity : ComponentActivity() {

    private val viewModel: VoipCallViewModel by viewModels()
    private var channelName: String = "guardian_secure_call"

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val micGranted = permissions[Manifest.permission.RECORD_AUDIO] == true
            if (micGranted) {
                viewModel.startCall(channelName)
            } else {
                Toast.makeText(this, "Microphone permission is required for Secure VoIP calls", Toast.LENGTH_LONG).show()
                finish()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        channelName = intent.getStringExtra("channel_name") ?: "guardian_secure_call"

        val hasMic = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (hasMic) {
            viewModel.startCall(channelName)
        } else {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.READ_PHONE_STATE
                )
            )
        }

        setContent {
            GuardianTheme {
                VoipCallScreen(
                    viewModel = viewModel,
                    onNavigateBack = { finish() }
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        viewModel.endCall()
    }
}
