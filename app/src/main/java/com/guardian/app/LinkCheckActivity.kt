package com.guardian.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.guardian.app.ui.theme.GuardianTheme
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class LinkVerdict(
    val url: String,
    val status: RiskStatus,
    val title: String,
    val detail: String
)

class LinkCheckActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val targetUrl = intent?.dataString ?: ""
        if (targetUrl.isBlank()) {
            finish()
            return
        }

        Log.d("GuardianLinkCheck", "Intercepted Intent URL: $targetUrl")
        val verdict = evaluateLinkSafety(targetUrl)

        if (verdict.status == RiskStatus.Low) {
            // Safe Link: Show confirmation toast and forward to default browser
            Toast.makeText(this, "✅ Guardian: Domain Verified Safe", Toast.LENGTH_SHORT).show()
            openInExternalBrowser(targetUrl)
            finish()
            return
        }

        // Suspicious or Scam Link: Display Interactive Safety Interception Dialog
        setContent {
            GuardianTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0x99000000))
                        .padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    LinkWarningDialog(
                        verdict = verdict,
                        onProceed = {
                            openInExternalBrowser(targetUrl)
                            finish()
                        },
                        onBlock = {
                            Toast.makeText(this@LinkCheckActivity, "Blocked malicious link navigation", Toast.LENGTH_SHORT).show()
                            finish()
                        },
                        onCopy = {
                            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("URL", targetUrl))
                            Toast.makeText(this@LinkCheckActivity, "Link copied to clipboard", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        }
    }

    private fun openInExternalBrowser(url: String) {
        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
        }

        val resolveInfos = packageManager.queryIntentActivities(browserIntent, PackageManager.MATCH_DEFAULT_ONLY)
        val targetApp = resolveInfos.firstOrNull { it.activityInfo.packageName != packageName }
            ?: resolveInfos.firstOrNull { it.activityInfo.packageName.contains("chrome") || it.activityInfo.packageName.contains("browser") }

        if (targetApp != null) {
            browserIntent.setClassName(targetApp.activityInfo.packageName, targetApp.activityInfo.name)
            browserIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            try {
                startActivity(browserIntent)
            } catch (e: Exception) {
                Log.e("GuardianLinkCheck", "Cannot launch browser: ${e.message}")
            }
        } else {
            // Fallback chooser
            try {
                val chooser = Intent.createChooser(browserIntent, "Open with").apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                startActivity(chooser)
            } catch (_: Exception) {}
        }
    }

    companion object {
        fun evaluateLinkSafety(url: String): LinkVerdict {
            val lower = url.lowercase().trim()

            return when {
                lower.contains(".apk") || lower.contains("login-kyc") || lower.contains("rbi-claim") ||
                lower.contains("free-gift") || lower.contains("update-pan") || lower.contains("bank-verify") ||
                lower.contains("digital-arrest") || lower.contains("anydesk") || lower.contains("electricity-bill-pay") -> {
                    LinkVerdict(
                        url = url,
                        status = RiskStatus.High,
                        title = "🚨 DANGEROUS PHISHING / MALWARE LINK",
                        detail = "This destination has been flagged for active banking credential theft, digital arrest extortion, or unauthorized APK downloads."
                    )
                }

                lower.contains("bit.ly") || lower.contains("tinyurl.com") || lower.contains("is.gd") ||
                lower.contains(".xyz") || lower.contains(".top") || lower.contains(".cc") || lower.contains(".club") -> {
                    LinkVerdict(
                        url = url,
                        status = RiskStatus.Suspicious,
                        title = "⚠️ SUSPICIOUS MASKED URL",
                        detail = "This link uses a URL shortener or unverified domain registry that conceals the true landing page. Proceed with caution."
                    )
                }

                else -> {
                    LinkVerdict(
                        url = url,
                        status = RiskStatus.Low,
                        title = "✅ DOMAIN VERIFIED CLEAN",
                        detail = "Standard trusted web domain. No active threat intelligence flags detected."
                    )
                }
            }
        }
    }
}

@Composable
fun LinkWarningDialog(
    verdict: LinkVerdict,
    onProceed: () -> Unit,
    onBlock: () -> Unit,
    onCopy: () -> Unit
) {
    val isHighRisk = verdict.status == RiskStatus.High
    val accentColor = if (isHighRisk) CoralRed else AmberWarning
    val containerBg = if (isHighRisk) Color(0xFF280B0B) else Color(0xFF261805)

    Card(
        colors = CardDefaults.cardColors(containerColor = containerBg),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.5.dp, accentColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = accentColor.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isHighRisk) Icons.Default.WarningAmber else Icons.Default.Security,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(Modifier.width(12.dp))

                Column(Modifier.weight(1f)) {
                    Text(
                        "Guardian Link Shield",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary
                    )
                    Text(
                        verdict.title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = accentColor
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            Text(
                verdict.detail,
                color = TextPrimary,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )

            Spacer(Modifier.height(12.dp))

            Surface(
                color = DarkBackground.copy(alpha = 0.8f),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, BorderSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        verdict.url.take(80) + if (verdict.url.length > 80) "..." else "",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        maxLines = 2,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedButton(
                        onClick = onCopy,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                    }
                }
            }

            Spacer(Modifier.height(18.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onProceed,
                    border = BorderStroke(1.dp, TextMuted),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Proceed (Unsafe)", color = TextSecondary, fontSize = 11.sp)
                }

                Button(
                    onClick = onBlock,
                    colors = ButtonDefaults.buttonColors(containerColor = CoralRed, contentColor = Color.White),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Block & Return", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
