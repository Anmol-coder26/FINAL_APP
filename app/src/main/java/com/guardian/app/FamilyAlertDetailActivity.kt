package com.guardian.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.guardian.app.evidence.EvidenceSharer
import java.util.Locale

class FamilyAlertDetailActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val protectedUser = intent.getStringExtra("protected_user") ?: "Family member"
        val riskScore = intent.getIntExtra("risk_score", 0)
        val scamType = intent.getStringExtra("scam_type") ?: "Unknown"
        val callerNumber = intent.getStringExtra("caller_number") ?: "Unknown"
        val transcriptSummary = intent.getStringExtra("transcript_summary") ?: ""
        val alertType = intent.getStringExtra("alert_type") ?: "scam_detected"

        setContent {
            MaterialTheme {
                FamilyAlertReportScreen(
                    protectedUser = protectedUser,
                    riskScore = riskScore,
                    scamType = scamType,
                    callerNumber = callerNumber,
                    transcriptSummary = transcriptSummary,
                    alertType = alertType,
                    onDismiss = { finish() }
                )
            }
        }
    }
}

@Composable
fun FamilyAlertReportScreen(
    protectedUser: String,
    riskScore: Int,
    scamType: String,
    callerNumber: String,
    transcriptSummary: String,
    alertType: String,
    onDismiss: () -> Unit
) {
    val scrollState = rememberScrollState()
    val context = androidx.compose.ui.platform.LocalContext.current

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .verticalScroll(scrollState)
        ) {
            // Header
            Text(
                text = "⚠️ Scam Alert",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFD32F2F)
            )
            Spacer(Modifier.height(8.dp))

            // Risk score card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = when {
                        riskScore >= 85 -> Color(0xFFFFEBEE)
                        riskScore >= 60 -> Color(0xFFFFF3E0)
                        else -> Color(0xFFE8F5E9)
                    }
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Risk Score", fontSize = 14.sp, color = Color.Gray)
                    Text(
                        "$riskScore%",
                        fontSize = 48.sp,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            riskScore >= 85 -> Color(0xFFD32F2F)
                            riskScore >= 60 -> Color(0xFFF57C00)
                            else -> Color(0xFF388E3C)
                        }
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Details
            ReportRow("Protected user", protectedUser)
            ReportRow("Alert type", alertType.replace("_", " ").replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() })
            ReportRow("Scam type", scamType)
            ReportRow("Caller number", callerNumber)
            ReportRow("Detected at", java.text.SimpleDateFormat(
                "dd MMM yyyy, HH:mm:ss",
                java.util.Locale.getDefault()
            ).format(java.util.Date()))

            Spacer(Modifier.height(16.dp))

            // Transcript
            if (transcriptSummary.isNotBlank()) {
                Text(
                    "What happened",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = transcriptSummary,
                        modifier = Modifier.padding(16.dp),
                        fontSize = 14.sp
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // AI Analysis
            Text(
                "How Guardian detected this",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("• Authority impersonation pattern", fontSize = 13.sp)
                    Text("• Urgency pressure tactics", fontSize = 13.sp)
                    Text("• OTP extraction attempt", fontSize = 13.sp)
                    Text("• Financial data request", fontSize = 13.sp)
                }
            }

            Spacer(Modifier.height(24.dp))

            // Actions
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Close")
            }

            Spacer(Modifier.height(8.dp))

            OutlinedButton(
                onClick = {
                    EvidenceSharer.openCybercrimePortal(context)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Report to cybercrime.gov.in")
            }

            Spacer(Modifier.height(16.dp))

            Text(
                text = "Guardian detected this automatically. No personal data was shared.",
                fontSize = 11.sp,
                color = Color.Gray
            )
        }
    }
}

@Composable
fun ReportRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 13.sp, color = Color.Gray)
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}
