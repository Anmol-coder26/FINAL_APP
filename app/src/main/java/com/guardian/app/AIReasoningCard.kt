package com.guardian.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.guardian.app.ui.components.GxButton
import com.guardian.app.ui.components.GxCard
import com.guardian.app.ui.components.GxChip
import com.guardian.app.ui.components.GxChipVariant
import com.guardian.app.ui.components.GxLiveDot
import com.guardian.app.ui.components.GxRiskRing
import com.guardian.app.ui.theme.GxBorder
import com.guardian.app.ui.theme.GxDanger
import com.guardian.app.ui.theme.GxDangerSoft
import com.guardian.app.ui.theme.GxPrimary
import com.guardian.app.ui.theme.GxPrimaryGlow
import com.guardian.app.ui.theme.GxPrimarySoft
import com.guardian.app.ui.theme.GxSafe
import com.guardian.app.ui.theme.GxShapeLg
import com.guardian.app.ui.theme.GxShapeMd
import com.guardian.app.ui.theme.GxShapePill
import com.guardian.app.ui.theme.GxSurface
import com.guardian.app.ui.theme.GxSurfaceAlt
import com.guardian.app.ui.theme.GxTextHi
import com.guardian.app.ui.theme.GxTextLo
import com.guardian.app.ui.theme.GxTextMid
import com.guardian.app.ui.theme.GxType
import com.guardian.app.ui.theme.GxVoid
import com.guardian.app.ui.theme.GxWarning
import com.guardian.app.ui.theme.GxWarningSoft
import com.guardian.app.ui.theme.gxDangerGlow
import com.guardian.app.ui.theme.gxFancyGlow

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AIReasoningCard(
    report: RiskReport,
    modifier: Modifier = Modifier,
    callerNumber: String = "",
    canEndCall: Boolean = true,
    onEndCall: (() -> Unit)? = null,
    onBlockNumber: (() -> Unit)? = null,
    onReset: (() -> Unit)? = null
) {
    var showHindiExplanation by remember { mutableStateOf(false) }
    var isExpanded by remember { mutableStateOf(false) }
    val isCritical = report.riskScore >= 85

    // Shake animation on danger threshold crossing
    var shakeTrigger by remember { mutableFloatStateOf(0f) }
    val shakeOffset by animateFloatAsState(
        targetValue = shakeTrigger,
        animationSpec = keyframes {
            durationMillis = 350
            0f at 0
            -6f at 50
            6f at 100
            -4f at 150
            4f at 200
            -2f at 250
            2f at 300
            0f at 350
        },
        label = "shake-offset"
    )

    LaunchedEffect(isCritical) {
        if (isCritical) {
            shakeTrigger = 1f
        }
    }

    val glowModifier = if (isCritical) {
        Modifier.gxDangerGlow()
    } else {
        Modifier.gxFancyGlow()
    }

    GxCard(
        modifier = modifier
            .offset { IntOffset(shakeOffset.toInt(), 0) }
            .then(glowModifier),
        backgroundColor = if (isCritical) Color(0xFF14070A) else GxSurface,
        borderColor = if (isCritical) GxDanger.copy(alpha = 0.6f) else GxBorder,
        contentPadding = 18.dp
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ----------------------------------------------------
            // 1. Header Row (64dp)
            // ----------------------------------------------------
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GxLiveDot(
                    color = when {
                        isCritical -> GxDanger
                        report.riskScore >= 40 -> GxWarning
                        else -> GxSafe
                    },
                    size = 9.dp
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Dual-Engine AI",
                            color = GxTextHi,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.width(8.dp))
                        GxChip(
                            text = if (report.isOffline) "OFFLINE SCORER" else "GEMINI 1.5 FLASH",
                            variant = if (report.isOffline) GxChipVariant.Warning else GxChipVariant.Brand,
                            height = 22.dp
                        )
                    }
                    if (callerNumber.isNotBlank()) {
                        Text(
                            "Target: $callerNumber",
                            color = GxTextLo,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Bilingual Toggle Chip
                GxChip(
                    text = if (showHindiExplanation) "🇮🇳 हिंदी" else "🇬🇧 English",
                    variant = GxChipVariant.Neutral,
                    onClick = { showHindiExplanation = !showHindiExplanation },
                    height = 26.dp
                )
            }

            // ----------------------------------------------------
            // 2. Centered Risk Score Block (140dp)
            // ----------------------------------------------------
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(136.dp),
                contentAlignment = Alignment.Center
            ) {
                // Radial Glow Backdrop
                Box(
                    modifier = Modifier
                        .size(130.dp)
                        .clip(RoundedCornerShape(100.dp))
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    when {
                                        isCritical -> GxDanger.copy(alpha = 0.25f)
                                        report.riskScore >= 40 -> GxWarning.copy(alpha = 0.2f)
                                        else -> GxSafe.copy(alpha = 0.15f)
                                    },
                                    Color.Transparent
                                )
                            )
                        )
                )

                GxRiskRing(
                    riskScore = report.riskScore,
                    size = 120.dp,
                    strokeWidth = 9.dp
                )
            }

            // ----------------------------------------------------
            // 3. Cognitive Engine Grid (2x2)
            // ----------------------------------------------------
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    EngineCardItem(
                        icon = Icons.Default.AccountBalance,
                        title = "Pretext Legitimacy",
                        detected = report.engines.pretextLegitimacy.detected,
                        detail = if (report.engines.pretextLegitimacy.detected) report.engines.pretextLegitimacy.summary else "Clean authority",
                        modifier = Modifier.weight(1f)
                    )
                    EngineCardItem(
                        icon = Icons.Default.Lock,
                        title = "Intent Risk",
                        detected = report.engines.intentRisk.detected,
                        detail = if (report.engines.intentRisk.detected) report.engines.intentRisk.summary else "No credential theft",
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    EngineCardItem(
                        icon = Icons.Default.Timer,
                        title = "Psychological Pressure",
                        detected = report.engines.psychologicalPressure.detected,
                        detail = if (report.engines.psychologicalPressure.detected) report.engines.psychologicalPressure.summary else "Normal tone",
                        modifier = Modifier.weight(1f)
                    )
                    EngineCardItem(
                        icon = Icons.Default.Bolt,
                        title = "Pattern Vector",
                        detected = report.engines.zeroShotVariant.detected || report.engines.informationAsymmetry.detected,
                        detail = if (report.engines.zeroShotVariant.detected) report.engines.zeroShotVariant.variantName else "Standard call profile",
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // ----------------------------------------------------
            // 3.5 Identity Consistency Mismatch Alert (if detected)
            // ----------------------------------------------------
            if (report.identityMismatch.detected) {
                Surface(
                    color = GxDangerSoft,
                    shape = GxShapeMd,
                    border = androidx.compose.foundation.BorderStroke(1.dp, GxDanger.copy(alpha = 0.6f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = GxDanger,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Identity Mismatch Detected",
                                color = GxDanger,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = report.identityMismatch.reasons.joinToString(", ").ifBlank { "Behavior deviates from contact baseline" },
                                color = GxTextMid,
                                fontSize = 11.sp
                            )
                        }
                        GxChip(text = "ANOMALY", variant = GxChipVariant.Danger, height = 22.dp)
                    }
                }
            }

            // ----------------------------------------------------
            // 4. Tactical Reasoning Block
            // ----------------------------------------------------
            Surface(
                color = GxSurfaceAlt,
                shape = GxShapeMd,
                border = androidx.compose.foundation.BorderStroke(1.dp, GxBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded }
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Psychology,
                            contentDescription = null,
                            tint = GxPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "AI Tactical Reasoning",
                            color = GxTextHi,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            if (isExpanded) "Less" else "More",
                            color = GxPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    val explanation = if (showHindiExplanation) report.explanationHi else report.explanationEn
                    Text(
                        text = explanation.ifBlank { "Monitoring live speech utterances for deception tactics..." },
                        color = GxTextMid,
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                        maxLines = if (isExpanded) Int.MAX_VALUE else 3
                    )
                }
            }

            // ----------------------------------------------------
            // 4.5 Plain-Language Explanation ("Why this score?")
            // ----------------------------------------------------
            Surface(
                color = GxSurfaceAlt,
                shape = GxShapeMd,
                border = androidx.compose.foundation.BorderStroke(1.dp, GxBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Why this score?",
                        style = GxType.label,
                        color = GxTextMid
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = if (showHindiExplanation) com.guardian.app.protect.advanced.PlainReasoningEngine.explainInHindi(report)
                        else com.guardian.app.protect.advanced.PlainReasoningEngine.explain(report),
                        style = GxType.body,
                        color = GxTextHi,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // ----------------------------------------------------
            // 4.8 Voice Naturalness / Synthesis Detector
            // ----------------------------------------------------
            if (report.syntheticConfidence > 0f) {
                Surface(
                    color = GxSurfaceAlt,
                    shape = GxShapeMd,
                    border = androidx.compose.foundation.BorderStroke(1.dp, GxBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "Voice Naturalness Analysis",
                                style = GxType.label,
                                color = GxTextMid
                            )
                            val (synthLabel, synthVariant) = when {
                                report.syntheticConfidence >= 0.70f -> "Synthetic AI Voice" to GxChipVariant.Danger
                                report.syntheticConfidence >= 0.40f -> "Uncertain" to GxChipVariant.Warning
                                else -> "Human Organic" to GxChipVariant.Safe
                            }
                            GxChip(text = synthLabel, variant = synthVariant, height = 20.dp)
                        }

                        // Progress track
                        androidx.compose.material3.LinearProgressIndicator(
                            progress = { report.syntheticConfidence.coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(GxShapePill),
                            color = when {
                                report.syntheticConfidence >= 0.70f -> GxDanger
                                report.syntheticConfidence >= 0.40f -> GxWarning
                                else -> GxSafe
                            },
                            trackColor = GxSurface
                        )

                        Text(
                            text = "Heuristic signal — not a guarantee • Confidence: ${(report.syntheticConfidence * 100).toInt()}%",
                            style = GxType.caption,
                            color = GxTextLo
                        )
                    }
                }
            }

            // ----------------------------------------------------
            // 5. Highlighted Risky Phrases
            // ----------------------------------------------------
            if (report.highlightedPhrases.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "Flagged Trigger Keywords",
                        color = GxTextLo,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.5.sp
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        report.highlightedPhrases.forEach { phrase ->
                            GxChip(
                                text = phrase,
                                variant = GxChipVariant.Danger,
                                icon = Icons.Default.WarningAmber,
                                height = 26.dp
                            )
                        }
                    }
                }
            }

            // ----------------------------------------------------
            // 6. Action Block (Critical Warning Controls)
            // ----------------------------------------------------
            if (isCritical || onEndCall != null || onBlockNumber != null) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (onEndCall != null) {
                        GxButton.Danger(
                            text = "END CALL IMMEDIATELY",
                            onClick = onEndCall,
                            pulsing = isCritical,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (onBlockNumber != null) {
                        GxButton.Ghost(
                            text = "Block & Add to Reputation DB",
                            onClick = onBlockNumber,
                            icon = Icons.Default.Lock,
                            modifier = Modifier.fillMaxWidth(),
                            height = 46.dp
                        )
                    }
                    if (report.riskScore >= 60) {
                        val context = androidx.compose.ui.platform.LocalContext.current
                        GxButton.Primary(
                            text = "Capture Evidence",
                            onClick = {
                                val intent = android.content.Intent(context, com.guardian.app.evidence.EvidenceCaptureActivity::class.java).apply {
                                    putExtra("riskScore", report.riskScore)
                                    putExtra("callerId", callerNumber.ifBlank { "Unknown Caller" })
                                    putExtra("packageName", "Guardian VoIP")
                                    putExtra("transcript", report.explanationEn)
                                }
                                context.startActivity(intent)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            height = 46.dp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EngineCardItem(
    icon: ImageVector,
    title: String,
    detected: Boolean,
    detail: String,
    modifier: Modifier = Modifier
) {
    Surface(
        color = if (detected) GxDangerSoft else GxSurfaceAlt,
        shape = GxShapeMd,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (detected) GxDanger.copy(alpha = 0.5f) else GxBorder
        ),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (detected) GxDanger else GxTextMid,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.weight(1f))
                GxChip(
                    text = if (detected) "FLAGGED" else "SAFE",
                    variant = if (detected) GxChipVariant.Danger else GxChipVariant.Safe,
                    height = 20.dp
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = title,
                color = GxTextHi,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
            Text(
                text = detail,
                color = if (detected) GxDanger else GxTextLo,
                fontSize = 10.sp,
                lineHeight = 14.sp,
                maxLines = 2
            )
        }
    }
}
