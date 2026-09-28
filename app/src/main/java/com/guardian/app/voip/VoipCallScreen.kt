package com.guardian.app.voip

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.guardian.app.callprotect.CallActionHelper
import com.guardian.app.ui.components.GxButton
import com.guardian.app.ui.components.GxCard
import com.guardian.app.ui.components.GxLiveDot
import com.guardian.app.ui.components.GxRiskRing
import com.guardian.app.ui.theme.GxBase
import com.guardian.app.ui.theme.GxDanger
import com.guardian.app.ui.theme.GxSafe
import com.guardian.app.ui.theme.GxWarning

@Composable
fun VoipCallScreen(
    viewModel: VoipCallViewModel,
    onNavigateBack: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val listState = rememberLazyListState()

    LaunchedEffect(state.transcripts.size) {
        if (state.transcripts.isNotEmpty()) {
            listState.animateScrollToItem(state.transcripts.size - 1)
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = GxBase
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // 1. Top Bar: Status + Duration
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        GxLiveDot()
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = state.status,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }

                    val mins = state.durationSeconds / 60
                    val secs = state.durationSeconds % 60
                    Text(
                        text = String.format("%02d:%02d", mins, secs),
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                state.banner?.let { bannerText ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(GxWarning.copy(alpha = 0.2f))
                            .border(1.dp, GxWarning, RoundedCornerShape(8.dp))
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = bannerText,
                            color = GxWarning,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // 2. Risk Header: Animated Risk Ring + Score
            GxCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "LIVE CALL RISK",
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.6f),
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        val riskColor = when {
                            state.report.riskScore >= 60 -> GxDanger
                            state.report.riskScore >= 25 -> GxWarning
                            else -> GxSafe
                        }
                        Text(
                            text = when {
                                state.report.riskScore >= 85 -> "CRITICAL THREAT"
                                state.report.riskScore >= 60 -> "HIGH RISK SCAM"
                                state.report.riskScore >= 25 -> "SUSPICIOUS ACTIVITY"
                                else -> "SAFE CALL"
                            },
                            color = riskColor,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = if (state.report.riskScore >= 85) Modifier.alpha(pulseAlpha) else Modifier
                        )
                        if (state.report.topSignals.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = state.report.topSignals.first().title,
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 12.sp
                            )
                        }
                    }

                    GxRiskRing(
                        riskScore = state.report.riskScore,
                        size = 80.dp,
                        strokeWidth = 8.dp
                    )
                }
            }

            // Role Confidence Banner
            if (state.roleConfidence > 0f && (state.scammerRole != "Unknown" || state.victimRole != "Unknown")) {
                val scammerText = if (state.scammerRole != "Unknown") state.scammerRole else "Caller"
                val confidencePct = (state.roleConfidence * 100).toInt()
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1B4B)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "Detected: $scammerText is likely the SCAMMER ($confidencePct% confidence)",
                            color = Color(0xFFFF4D5E),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (state.roleReasoning.isNotBlank()) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Reasoning: ${state.roleReasoning}",
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }

            // 3. Dual Transcript: LazyColumn
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                if (state.transcripts.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Waiting for conversation speech...",
                            color = Color.White.copy(alpha = 0.4f),
                            fontSize = 14.sp
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(state.transcripts) { line ->
                            val isLocal = line.speaker == Speaker.LOCAL
                            val roleLabel = when {
                                isLocal && state.victimRole == "You" -> "Victim"
                                isLocal && state.scammerRole == "You" -> "Scammer"
                                !isLocal && state.victimRole == "Caller" -> "Victim"
                                !isLocal && state.scammerRole == "Caller" -> "Scammer"
                                else -> if (isLocal) "You" else "Caller"
                            }

                            val roleColor = when (roleLabel) {
                                "Scammer" -> Color(0xFFFF4D5E)
                                "Victim" -> Color(0xFF00E5A0)
                                else -> if (isLocal) Color(0xFF38BDF8) else Color(0xFFF97316)
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = if (isLocal) Arrangement.Start else Arrangement.End
                            ) {
                                Card(
                                    shape = RoundedCornerShape(
                                        topStart = 16.dp,
                                        topEnd = 16.dp,
                                        bottomStart = if (isLocal) 4.dp else 16.dp,
                                        bottomEnd = if (isLocal) 16.dp else 4.dp
                                    ),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isLocal) Color(0xFF1E293B) else Color(0xFF334155)
                                    ),
                                    modifier = Modifier.fillMaxWidth(0.85f)
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text(
                                            text = "$roleLabel:",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = roleColor
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = line.text,
                                            fontSize = 14.sp,
                                            color = Color.White
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 4. Reasoning Card: Plain English/Hindi rationale
            GxCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "AI REASONING",
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.6f),
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = state.report.explanationEn.ifBlank { "Monitoring active conversation for deceptive intent." },
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 5. Action Bar: Mute, Speaker, END CALL, Block
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Mute
                IconButton(
                    onClick = { viewModel.toggleMute() },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(if (state.isMuted) GxDanger.copy(alpha = 0.2f) else Color(0xFF334155))
                ) {
                    Icon(
                        imageVector = if (state.isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                        contentDescription = "Mute",
                        tint = if (state.isMuted) GxDanger else Color.White
                    )
                }

                // Speaker
                IconButton(
                    onClick = { viewModel.toggleSpeaker() },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(if (state.isSpeakerOn) Color(0xFF2563EB) else Color(0xFF334155))
                ) {
                    Icon(
                        imageVector = if (state.isSpeakerOn) Icons.Default.VolumeUp else Icons.Default.VolumeDown,
                        contentDescription = "Speaker",
                        tint = Color.White
                    )
                }

                // Block
                IconButton(
                    onClick = {
                        val num = state.channelName.ifBlank { "VoIP Caller" }
                        val success = CallActionHelper.blockNumber(context, num)
                        Toast.makeText(context, if (success) "Blocked $num" else "Block requested for $num", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF334155))
                ) {
                    Icon(
                        imageVector = Icons.Default.Block,
                        contentDescription = "Block",
                        tint = GxWarning
                    )
                }

                // END CALL
                GxButton.Danger(
                    text = "END CALL",
                    icon = Icons.Default.CallEnd,
                    onClick = {
                        viewModel.endCall()
                        CallActionHelper.endCall(context)
                        onNavigateBack()
                    },
                    modifier = Modifier.height(48.dp)
                )
            }
        }
    }
}
