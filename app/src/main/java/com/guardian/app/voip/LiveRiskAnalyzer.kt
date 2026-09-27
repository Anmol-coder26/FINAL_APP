package com.guardian.app.voip

import android.util.Log
import com.guardian.app.RiskReport
import com.guardian.app.SemanticAnalyzer
import com.guardian.app.protect.RegionalScamKeywords
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class LiveRiskAnalyzer(
    private val analyzer: SemanticAnalyzer,
    private val language: String = "hi",
    private val onReport: (RiskReport) -> Unit
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val buffer = StringBuilder()
    private var lastCallTs = 0L
    private val intervalMs = 2000L
    private var currentScore = 0

    // INSTANT keyword layer — triggers immediately
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

                // LAYER 1 — INSTANT keyword check
                val lower = line.text.lowercase()
                var instantScore = currentScore
                var detectedKeyword = ""
                for ((keyword, weight) in criticalKeywords) {
                    if (lower.contains(keyword)) {
                        if (weight > instantScore) {
                            instantScore = weight
                            detectedKeyword = keyword
                        }
                    }
                }

                val regionalMatches = RegionalScamKeywords.match(line.text, language)
                for (kw in regionalMatches) {
                    if (kw.weight > instantScore) {
                        instantScore = kw.weight
                        detectedKeyword = kw.phrase
                    }
                }

                if (instantScore > currentScore) {
                    currentScore = instantScore
                    val instantReport = RiskReport(
                        riskScore = currentScore,
                        explanationEn = "High-risk keyword '${detectedKeyword.ifBlank { "Scam indicator" }}' detected in conversation.",
                        explanationHi = "बातचीत में उच्च जोखिम शब्द '${detectedKeyword.ifBlank { "धोखाधड़ी संकेत" }}' मिला।"
                    )
                    withContext(Dispatchers.Main) { onReport(instantReport) }
                }

                // LAYER 2 — AI analysis (rate-limited)
                if (line.isFinal) {
                    val now = System.currentTimeMillis()
                    if (now - lastCallTs >= intervalMs) {
                        lastCallTs = now
                        try {
                            val r = analyzer.analyzeMultilingual(buffer.toString(), language)
                            val blended = maxOf(r.riskScore, currentScore)
                            currentScore = blended
                            withContext(Dispatchers.Main) {
                                onReport(r.copy(riskScore = blended))
                            }
                        } catch (e: Exception) {
                            Log.e("Guardian", "AI analysis failed", e)
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
        buffer.clear()
    }
}
