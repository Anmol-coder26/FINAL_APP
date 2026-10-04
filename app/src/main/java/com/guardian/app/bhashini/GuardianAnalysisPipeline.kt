package com.guardian.app.bhashini

import android.content.Context
import android.util.Log
import com.guardian.app.RiskReport
import com.guardian.app.SemanticAnalyzer
import android.os.Handler
import android.os.Looper
import com.guardian.app.LiveTranscriptBuffer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class GuardianAnalysisPipeline(
    private val analyzer: SemanticAnalyzer,
    private val context: Context? = null,
    private val onRiskUpdate: (RiskReport) -> Unit,
    private val onError: (String) -> Unit = {},
    private val onPcmChunk: ((ShortArray) -> Unit)? = null,
    private val onTranscript: (String, Boolean) -> Unit = { _, _ -> },
    private val onStatus: (String) -> Unit = {},
    private val onAudioLevel: (Float) -> Unit = {},
    private val onAnalysisState: (Boolean) -> Unit = {}
) {
    companion object {
        private const val TAG = "GuardianAnalysisPipe"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var sttClient: BhashiniSttClient? = null
    private var nativeFallback: AndroidSpeechRecognizerFallback? = null
    private var sourceLanguage: String = "hi"
    private val transcriptBuffer = LiveTranscriptBuffer()
    private val mainHandler = Handler(Looper.getMainLooper())
    @Volatile private var stopped = false
    private var analysisJob: Job? = null
    private var analysisRevision = 0

    fun start(language: String = "hi") {
        if (stopped || sttClient != null || nativeFallback != null) return
        sourceLanguage = language
        transcriptBuffer.clear()

        sttClient = BhashiniSttClient(
            sourceLanguage = language,
            mode = BhashiniSttClient.Mode.MICROPHONE,
            onTranscript = { text, isFinal ->
                handleTranscript(text, isFinal)
            },
            onError = { _ ->
                mainHandler.post {
                    if (!stopped) {
                        // Release AudioRecord before SpeechRecognizer takes the microphone.
                        sttClient?.stop()
                        sttClient = null
                        onStatus("BHASHINI unavailable — switching to Android speech recognition")
                        startNativeFallback(language)
                    }
                }
            },
            onPcmChunk = onPcmChunk,
            onStatus = { status -> mainHandler.post { if (!stopped) onStatus(status) } },
            onAudioLevel = { level -> mainHandler.post { if (!stopped) onAudioLevel(level) } }
        )

        try {
            sttClient?.start(language)
            Log.d(TAG, "Guardian Bhashini pipeline started with language: $language")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start Bhashini pipeline: ${e.message}. Triggering fallback.")
            sttClient?.stop()
            sttClient = null
            startNativeFallback(language)
        }
    }

    private fun startNativeFallback(language: String) {
        if (!stopped && context != null && nativeFallback == null) {
            Log.d(TAG, "Starting native Android SpeechRecognizer fallback for language: $language")
            nativeFallback = AndroidSpeechRecognizerFallback(
                context = context,
                language = language,
                onTranscript = { text, isFinal ->
                    handleTranscript(text, isFinal)
                },
                onError = { err ->
                    if (!stopped) onError(err)
                },
                onStatus = { if (!stopped) onStatus(it) },
                onAudioLevel = { if (!stopped) onAudioLevel(it) }
            ).also { it.start() }
        } else if (!stopped && context == null) {
            onError("Speech recognition requires an Android context")
        }
    }

    private fun handleTranscript(text: String, isFinal: Boolean) {
        mainHandler.post {
            if (stopped || text.isBlank()) return@post
            transcriptBuffer.accept(text, isFinal)
            onTranscript(text, isFinal)
            if (!isFinal) return@post
            val currentFullText = transcriptBuffer.context
            onAnalysisState(true)

            val revision = ++analysisRevision
            analysisJob?.cancel()
            analysisJob = scope.launch {
                try {
                    // 1. Translate from source language to English for AI reasoning
                    val englishChunk = if (sourceLanguage.equals("en", ignoreCase = true)) {
                        currentFullText
                    } else {
                        try {
                            BhashiniTranslateClient.translate(currentFullText, sourceLanguage, "en")
                        } catch (_: Exception) {
                            currentFullText
                        }
                    }

                    // 2. Perform deep multi-engine semantic analysis
                    val report = analyzer.analyzeChunk(englishChunk)

                    // 3. Translate the explanation back to the user's selected language
                    val localizedExplanation = if (sourceLanguage.equals("en", ignoreCase = true)) {
                        report.explanationEn
                    } else {
                        try {
                            BhashiniTranslateClient.translate(report.explanationEn, "en", sourceLanguage)
                        } catch (_: Exception) {
                            report.explanationEn
                        }
                    }

                    val finalReport = report.copy(
                        explanationHi = localizedExplanation
                    )

                    mainHandler.post {
                        if (!stopped && revision == analysisRevision) {
                            onRiskUpdate(finalReport)
                            onAnalysisState(false)
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    mainHandler.post {
                        if (!stopped && revision == analysisRevision) {
                            onError("Risk analysis unavailable; transcription can continue.")
                            onAnalysisState(false)
                        }
                    }
                }
            }
        }
    }

    fun stop() {
        stopped = true
        mainHandler.removeCallbacksAndMessages(null)
        try {
            sttClient?.stop()
        } catch (_: Exception) {}
        sttClient = null

        try {
            nativeFallback?.stop()
        } catch (_: Exception) {}
        nativeFallback = null

        analysisJob?.cancel()
        transcriptBuffer.clear()
        scope.cancel()
        Log.d(TAG, "Guardian analysis pipeline stopped")
    }
}
