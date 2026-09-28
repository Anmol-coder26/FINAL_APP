package com.guardian.app.voip

import android.util.Log
import com.guardian.app.RiskReport
import com.guardian.app.SemanticAnalyzer
import com.guardian.app.protect.RegionalScamKeywords
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class LiveState(
    val riskScore: Int = 0,
    val topSignals: List<String> = emptyList(),
    val victimRole: String = "Unknown",
    val scammerRole: String = "Unknown",
    val roleConfidence: Float = 0f,
    val roleReasoning: String = ""
)

class LiveRiskAnalyzer(
    private val analyzer: SemanticAnalyzer,
    private val language: String = "hi",
    private val onState: (LiveState) -> Unit
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val buffer = StringBuilder()
    private var lastCallTs = 0L
    private val intervalMs = 2000L
    private var currentScore = 0
    private var currentState = LiveState()

    private val criticalKeywords = mapOf(
        "otp" to 60, "cvv" to 60, "pin" to 55, "password" to 60,
        "kyc" to 55, "lottery" to 50, "prize" to 45, "winner" to 45,
        "digital arrest" to 75, "cbi" to 65, "police" to 55,
        "arrest" to 55, "transfer" to 45, "upi" to 40,
        "aadhaar" to 50, "pan card" to 50, "anydesk" to 70,
        "teamviewer" to 70, "refund" to 40, "block" to 40,
        "urgent" to 35, "immediately" to 35, "बताइए" to 55,
        "बंद" to 45, "गिरफ्तार" to 60, "लॉटरी" to 50
    )

    fun start(transcripts: SharedFlow<TranscriptLine>) {
        scope.launch {
            transcripts.collect { line ->
                buffer.append(
                    if (line.speaker == Speaker.LOCAL) "\nYou: ${line.text}"
                    else "\nCaller: ${line.text}"
                )
                if (buffer.length > 2000) buffer.delete(0, buffer.length - 2000)

                // LAYER 1 — INSTANT keyword scoring & instant role detection
                val lower = line.text.lowercase()
                var instantScore = currentScore
                var detectedKw = ""
                for ((kw, weight) in criticalKeywords) {
                    if (lower.contains(kw)) {
                        if (weight > instantScore) {
                            instantScore = weight
                            detectedKw = kw
                        }
                    }
                }

                val regionalMatches = RegionalScamKeywords.match(line.text, language)
                for (kw in regionalMatches) {
                    if (kw.weight > instantScore) {
                        instantScore = kw.weight
                        detectedKw = kw.phrase
                    }
                }

                if (instantScore > currentScore) {
                    currentScore = instantScore
                    // Infer instant roles if high risk keyword spoken
                    val (vRole, sRole) = if (line.speaker == Speaker.REMOTE) "You" to "Caller" else "Caller" to "You"
                    currentState = currentState.copy(
                        riskScore = currentScore,
                        victimRole = if (currentState.victimRole == "Unknown") vRole else currentState.victimRole,
                        scammerRole = if (currentState.scammerRole == "Unknown") sRole else currentState.scammerRole,
                        roleConfidence = maxOf(currentState.roleConfidence, 0.75f),
                        roleReasoning = if (currentState.roleReasoning.isBlank()) "$sRole is demanding sensitive keyword '$detectedKw' while $vRole is responding." else currentState.roleReasoning,
                        topSignals = (currentState.topSignals + "Critical keyword: $detectedKw").distinct()
                    )
                    try {
                        withContext(Dispatchers.Main) { onState(currentState) }
                    } catch (_: Throwable) {
                        onState(currentState)
                    }
                }

                // LAYER 2 — AI analysis + role detection every 2 seconds
                if (line.isFinal) {
                    val now = System.currentTimeMillis()
                    if (now - lastCallTs >= intervalMs) {
                        lastCallTs = now
                        try {
                            val response = analyzer.analyzeChunkWithRoles(buffer.toString(), language)
                            val blended = maxOf(response.riskScore, currentScore)
                            currentScore = blended
                            currentState = currentState.copy(
                                riskScore = blended,
                                topSignals = if (response.topSignals.isNotEmpty()) response.topSignals else currentState.topSignals,
                                victimRole = if (response.victimRole != "Unknown") response.victimRole else currentState.victimRole,
                                scammerRole = if (response.scammerRole != "Unknown") response.scammerRole else currentState.scammerRole,
                                roleConfidence = if (response.roleConfidence > 0f) response.roleConfidence else currentState.roleConfidence,
                                roleReasoning = if (response.roleReasoning.isNotBlank()) response.roleReasoning else currentState.roleReasoning
                            )
                            try {
                                withContext(Dispatchers.Main) { onState(currentState) }
                            } catch (_: Throwable) {
                                onState(currentState)
                            }
                        } catch (e: Exception) {
                            try {
                                Log.e("Guardian", "AI analysis & role detection failed", e)
                            } catch (_: Throwable) {}
                        }
                    }
                }
            }
        }
    }

    fun stop() {
        scope.cancel()
        buffer.clear()
    }

    fun reset() {
        currentScore = 0
        currentState = LiveState()
        buffer.clear()
    }
}
