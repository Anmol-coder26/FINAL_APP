package com.guardian.app.evidence

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

class EvidenceCaptureActivity : ComponentActivity() {

    private var intentRiskScore: Int = 0
    private var intentCallerId: String = "Unknown"
    private var intentSourcePackage: String = "Unknown"
    private var intentTranscript: String = ""

    private val projectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val intent = Intent(this, EvidenceCaptureService::class.java).apply {
                putExtra("resultCode", result.resultCode)
                putExtra("data", result.data)
                putExtra("riskScore", intentRiskScore)
                putExtra("callerId", intentCallerId)
                putExtra("packageName", intentSourcePackage)
                putExtra("transcript", intentTranscript)
            }
            ContextCompat.startForegroundService(this, intent)
            finish()
        } else {
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        intentRiskScore = intent.getIntExtra("riskScore", 0)
        intentCallerId = intent.getStringExtra("callerId") ?: "Unknown"
        intentSourcePackage = intent.getStringExtra("packageName") ?: "Unknown"
        intentTranscript = intent.getStringExtra("transcript") ?: ""

        setContent {
            MaterialTheme {
                CaptureConsentScreen(
                    riskScore = intentRiskScore,
                    callerId = intentCallerId,
                    onCapture = { requestCapture() },
                    onCancel = { finish() }
                )
            }
        }
    }

    private fun requestCapture() {
        val mpm = getSystemService(MediaProjectionManager::class.java)
        projectionLauncher.launch(mpm.createScreenCaptureIntent())
    }
}

@Composable
fun CaptureConsentScreen(
    riskScore: Int,
    callerId: String,
    onCapture: () -> Unit,
    onCancel: () -> Unit
) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Capture Evidence",
                style = MaterialTheme.typography.headlineMedium
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Risk Score: $riskScore%",
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = "Caller: $callerId",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(32.dp))
            Text(
                text = "Guardian will capture the current screen and generate a PDF report for cybercrime.gov.in filing.",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(32.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedButton(onClick = onCancel) { Text("Cancel") }
                Button(onClick = onCapture) { Text("Capture") }
            }
        }
    }
}
