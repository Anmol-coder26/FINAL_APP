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
    private val semanticAnalysis: suspend (String, String) -> RiskReport = { text, lang -> analyzer.analyzeMultilingual(text, lang) },
    private val onReport: (RiskReport) -> Unit
) {
    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private val buffer = VoipTranscriptBuffer()
    private var lastCallTs = 0L
    private val intervalMs = 2000L
    private var currentScore = 0
    private var dirty = false
    private var analysisJob: Job? = null
    private var collectorJob: Job? = null
    private var timerJob: Job? = null

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
        if (collectorJob != null) return
        collectorJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            transcripts.collect { line ->
                if (line.text.isBlank()) return@collect
                buffer.accept(line)
                dirty = true
                val lower = line.text.lowercase()
                var instantScore = currentScore
                var keyword = ""
                for ((phrase, weight) in criticalKeywords) {
                    if (lower.contains(phrase) && weight > instantScore) { instantScore = weight; keyword = phrase }
                }
                for (match in RegionalScamKeywords.match(line.text, language)) {
                    if (match.weight > instantScore) { instantScore = match.weight; keyword = match.phrase }
                }
                if (instantScore > currentScore) {
                    currentScore = instantScore
                    onReport(RiskReport(
                        riskScore = currentScore,
                        explanationEn = "Potential risk signal '$keyword' detected in the conversation.",
                        explanationHi = "बातचीत में संभावित जोखिम संकेत '$keyword' मिला।",
                        isOffline = true, source = "local_keywords"
                    ))
                }
                // Semantic requests run independently of collection and UI text delivery.
                scheduleAnalysis()
            }
        }
        timerJob = scope.launch {
            while (isActive) { delay(250); scheduleAnalysis() }
        }
    }

    private fun scheduleAnalysis() {
        val now = System.currentTimeMillis()
        if (!dirty || analysisJob?.isActive == true || now - lastCallTs < intervalMs) return
        val snapshot = buffer.context()
        if (snapshot.isBlank()) return
        dirty = false
        lastCallTs = now
        analysisJob = scope.launch {
            try {
                val report = withContext(Dispatchers.IO) { semanticAnalysis(snapshot, language) }
                ensureActive()
                currentScore = maxOf(report.riskScore, currentScore)
                onReport(report.copy(riskScore = currentScore))
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { Log.w("GuardianRisk", "Semantic analysis unavailable: ${e.javaClass.simpleName}") }
        }
    }

    fun stop() {
        scope.cancel()
        buffer.clear()
    }

    fun reset() {
        analysisJob?.cancel()
        currentScore = 0
        dirty = false
        buffer.clear()
    }
}
