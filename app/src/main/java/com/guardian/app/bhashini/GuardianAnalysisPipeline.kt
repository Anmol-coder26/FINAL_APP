package com.guardian.app.bhashini

import android.util.Log
import com.guardian.app.RiskReport
import com.guardian.app.SemanticAnalyzer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class GuardianAnalysisPipeline(
    private val analyzer: SemanticAnalyzer,
    private val onRiskUpdate: (RiskReport) -> Unit,
    private val onError: (String) -> Unit = {},
    private val onPcmChunk: ((ShortArray) -> Unit)? = null
) {
    companion object {
        private const val TAG = "GuardianAnalysisPipe"
        private const val MAX_BUFFER_CHARS = 1000
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var sttClient: BhashiniSttClient? = null
    private var sourceLanguage: String = "hi"
    private val transcriptBuffer = StringBuilder()
    private var analysisJob: Job? = null

    fun start(language: String = "hi") {
        sourceLanguage = language
        transcriptBuffer.clear()

        sttClient = BhashiniSttClient(
            onTranscript = { text, isFinal ->
                handleTranscript(text, isFinal)
            },
            onError = { error ->
                Log.e(TAG, "STT Pipeline error: $error")
                onError(error)
            },
            onPcmChunk = onPcmChunk
        )

        try {
            sttClient?.start(language)
            Log.d(TAG, "Guardian Bhashini pipeline started with language: $language")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start Bhashini pipeline: ${e.message}")
            onError(e.message ?: "Pipeline start failed")
        }
    }

    private fun handleTranscript(text: String, isFinal: Boolean) {
        if (text.isBlank()) return

        if (isFinal) {
            synchronized(transcriptBuffer) {
                if (transcriptBuffer.isNotEmpty()) {
                    transcriptBuffer.append(" ")
                }
                transcriptBuffer.append(text)
                if (transcriptBuffer.length > MAX_BUFFER_CHARS) {
                    val excess = transcriptBuffer.length - MAX_BUFFER_CHARS
                    transcriptBuffer.delete(0, excess)
                }
            }

            val currentFullText = synchronized(transcriptBuffer) { transcriptBuffer.toString() }

            analysisJob?.cancel()
            analysisJob = scope.launch {
                try {
                    // 1. Translate from source language to English for AI reasoning
                    val englishChunk = if (sourceLanguage.equals("en", ignoreCase = true)) {
                        currentFullText
                    } else {
                        BhashiniTranslateClient.translate(currentFullText, sourceLanguage, "en")
                    }

                    // 2. Perform deep multi-engine semantic analysis
                    val report = analyzer.analyzeChunk(englishChunk)

                    // 3. Translate the explanation back to the user's selected language
                    val localizedExplanation = if (sourceLanguage.equals("en", ignoreCase = true)) {
                        report.explanationEn
                    } else {
                        BhashiniTranslateClient.translate(report.explanationEn, "en", sourceLanguage)
                    }

                    val finalReport = report.copy(
                        explanationHi = localizedExplanation
                    )

                    onRiskUpdate(finalReport)
                } catch (e: Exception) {
                    Log.e(TAG, "Analysis pipeline step failed: ${e.message}")
                    onError(e.message ?: "Analysis execution error")
                }
            }
        }
    }

    fun stop() {
        try {
            sttClient?.stop()
        } catch (_: Exception) {}
        sttClient = null
        analysisJob?.cancel()
        synchronized(transcriptBuffer) {
            transcriptBuffer.clear()
        }
        scope.cancel()
        Log.d(TAG, "Guardian Bhashini pipeline stopped")
    }
}
