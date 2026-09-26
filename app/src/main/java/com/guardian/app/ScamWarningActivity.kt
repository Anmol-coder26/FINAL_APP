package com.guardian.app

import android.app.PendingIntent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.guardian.app.ui.theme.GuardianTheme
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.OpenInNew
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

private val GxDanger = CoralRed
private val GxWarning = AmberWarning
private val GxSurface = DarkSurface
private val GxSurfaceAlt = DarkSurfaceElevated
private val GxTextHi = TextPrimary
private val GxTextMid = TextSecondary
private val GxTextLo = TextMuted

class ScamWarningActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val source = intent.getStringExtra("source").orEmpty()
        val title = intent.getStringExtra("title") ?: "Suspicious Message"
        val body = intent.getStringExtra("body").orEmpty()
        val score = intent.getIntExtra("score", 75)
        val explanation = intent.getStringExtra("explanation")
            ?: "Guardian detected dangerous scam patterns or credential extraction requests in this notification."

        val originalPendingIntent: PendingIntent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra("original_pending_intent", PendingIntent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra("original_pending_intent")
        }

        setContent {
            GuardianTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xCC000000))
                        .padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    ScamWarningCard(
                        source = source,
                        title = title,
                        body = body,
                        score = score,
                        explanation = explanation,
                        onOpenAnyway = {
                            try {
                                originalPendingIntent?.send()
                            } catch (_: Exception) {
                                Toast.makeText(this@ScamWarningActivity, "Cannot open message", Toast.LENGTH_SHORT).show()
                            }
                            finish()
                        },
                        onDismiss = {
                            Toast.makeText(this@ScamWarningActivity, "Blocked scam message interaction", Toast.LENGTH_SHORT).show()
                            finish()
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun ScamWarningCard(
    source: String,
    title: String,
    body: String,
    score: Int,
    explanation: String,
    onOpenAnyway: () -> Unit,
    onDismiss: () -> Unit
) {
    val isCritical = score >= 75
    val accentColor = if (isCritical) GxDanger else GxWarning

    Card(
        colors = CardDefaults.cardColors(containerColor = GxSurface),
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
                            imageVector = Icons.Default.WarningAmber,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(Modifier.width(12.dp))

                Column(Modifier.weight(1f)) {
                    Text(
                        "Guardian Shield ΓÇö $score% Risk",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = accentColor
                    )
                    Text(
                        title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = GxTextHi
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            Text(
                text = explanation,
                color = GxTextMid,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )

            if (body.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Surface(
                    color = GxSurfaceAlt,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Color(0x33FFFFFF)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = body.take(160) + if (body.length > 160) "..." else "",
                        color = GxTextLo,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onOpenAnyway,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Open anyway", fontSize = 12.sp, color = GxTextMid)
                }

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = GxDanger, contentColor = Color.White),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Delete message", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
