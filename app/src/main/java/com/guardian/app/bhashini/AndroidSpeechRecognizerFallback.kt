package com.guardian.app.bhashini

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log

class AndroidSpeechRecognizerFallback(
    private val context: Context,
    private val language: String = "hi",
    private val onTranscript: (text: String, isFinal: Boolean) -> Unit,
    private val onError: (String) -> Unit = {}
) {
    private var recognizer: SpeechRecognizer? = null
    private var isListening = false

    fun start() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Log.e("AndroidSTT", "Native SpeechRecognizer is not available on this device")
            onError("Android SpeechRecognizer unavailable")
            return
        }

        try {
            recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        Log.d("AndroidSTT", "Native Android SpeechRecognizer ready (Lang: $language)")
                    }

                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}

                    override fun onEndOfSpeech() {
                        if (isListening) restartListening()
                    }

                    override fun onError(error: Int) {
                        Log.w("AndroidSTT", "Native SpeechRecognizer error code: $error")
                        // Error codes 7 (NO_MATCH) and 6 (SPEECH_TIMEOUT) are normal when quiet
                        if (isListening) {
                            restartListening()
                        }
                    }

                    override fun onResults(results: Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val text = matches?.firstOrNull()
                        if (!text.isNullOrBlank()) {
                            Log.d("AndroidSTT", "Native STT Final: $text")
                            onTranscript(text, true)
                        }
                        if (isListening) restartListening()
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val text = matches?.firstOrNull()
                        if (!text.isNullOrBlank()) {
                            Log.d("AndroidSTT", "Native STT Partial: $text")
                            onTranscript(text, false)
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }

            isListening = true
            restartListening()
        } catch (e: Exception) {
            Log.e("AndroidSTT", "Failed to start native SpeechRecognizer: ${e.message}")
            onError(e.message ?: "SpeechRecognizer start error")
        }
    }

    private fun restartListening() {
        if (!isListening) return
        try {
            val langTag = when (language.lowercase()) {
                "hi" -> "hi-IN"
                "ta" -> "ta-IN"
                "te" -> "te-IN"
                "bn" -> "bn-IN"
                "mr" -> "mr-IN"
                "kn" -> "kn-IN"
                "ml" -> "ml-IN"
                "pa" -> "pa-IN"
                "gu" -> "gu-IN"
                else -> "en-IN"
            }
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, langTag)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }
            recognizer?.startListening(intent)
        } catch (e: Exception) {
            Log.e("AndroidSTT", "Restart listening failed: ${e.message}")
        }
    }

    fun stop() {
        isListening = false
        try {
            recognizer?.stopListening()
            recognizer?.destroy()
        } catch (_: Exception) {}
        recognizer = null
    }
}
