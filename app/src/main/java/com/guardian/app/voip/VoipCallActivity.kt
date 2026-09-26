package com.guardian.app.voip

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.guardian.app.ui.theme.GuardianTheme

class VoipCallActivity : ComponentActivity() {

    private val viewModel: VoipCallViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val channel = intent.getStringExtra("channel_name") ?: "guardian_secure_call"
        viewModel.startCall(channel)

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
