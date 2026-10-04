package com.guardian.app.bhashini

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/** One recognizer owns the microphone. Restart only after a terminal result/error. */
class AndroidSpeechRecognizerFallback(
    private val context: Context,
    private val language: String = "hi",
    private val onTranscript: (String, Boolean) -> Unit,
    private val onError: (String) -> Unit = {},
    private val onStatus: (String) -> Unit = {},
    private val onAudioLevel: (Float) -> Unit = {}
) {
    private val handler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var running = false
    @Volatile private var closed = false
    private var failures = 0
    private var quietSegments = 0
    private val restart = Runnable { listen() }

    fun start() {
        handler.post {
            if (closed || running) return@post
            if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                onError("No speech recognition service found. Install/enable a speech service, then retry.")
                return@post
            }
            running = true
            try {
                recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                    setRecognitionListener(object : RecognitionListener {
                        override fun onReadyForSpeech(params: Bundle?) {
                            if (running && quietSegments < 2) onStatus("Android speech recognition ready — speak near the microphone")
                        }
                        override fun onBeginningOfSpeech() {}
                        override fun onRmsChanged(rmsdB: Float) {
                            if (running) onAudioLevel(((rmsdB + 2f) / 12f).coerceIn(0f, 1f))
                        }
                        override fun onBufferReceived(buffer: ByteArray?) {}
                        // onResults/onError must finish this segment before another startListening.
                        override fun onEndOfSpeech() { if (running) onAudioLevel(0f) }
                        override fun onError(error: Int) {
                            if (!running) return
                            onAudioLevel(0f)
                            when (error) {
                                SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                                    quietSegments++
                                    if (quietSegments >= 2) onStatus(
                                        "No speech recognized. Check speaker volume. If this phone is in a SIM call, Android may block its microphone; use SuSagi on a second device."
                                    )
                                    schedule(500)
                                }
                                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS,
                                SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
                                SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> fail(
                                    "Speech recognition permission or language unavailable (code $error). Check microphone permission and language support, then retry."
                                )
                                else -> {
                                    failures++
                                    if (failures >= 4) fail(
                                        "Speech recognition failed (code $error). Check connectivity and microphone access, then retry."
                                    ) else {
                                        onStatus("Speech recognition reconnecting (code $error)")
                                        schedule(1000L * failures)
                                    }
                                }
                            }
                        }
                        override fun onResults(results: Bundle?) {
                            if (!running) return
                            val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                                ?.firstOrNull()?.trim().orEmpty()
                            if (text.isNotEmpty()) {
                                failures = 0
                                quietSegments = 0
                                onTranscript(text, true)
                            }
                            schedule(250)
                        }
                        override fun onPartialResults(partialResults: Bundle?) {
                            if (!running) return
                            val text = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                                ?.firstOrNull()?.trim().orEmpty()
                            if (text.isNotEmpty()) {
                                quietSegments = 0
                                onTranscript(text, false)
                            }
                        }
                        override fun onEvent(eventType: Int, params: Bundle?) {}
                    })
                }
                listen()
            } catch (_: Exception) {
                fail("Could not start speech recognition. Check the device's speech service and retry.")
            }
        }
    }

    private fun schedule(delay: Long) {
        handler.removeCallbacks(restart)
        if (running) handler.postDelayed(restart, delay)
    }

    private fun listen() {
        if (!running) return
        val tag = if (language.contains('-')) language else "$language-IN"
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, tag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        try {
            recognizer?.startListening(intent)
        } catch (_: Exception) {
            fail("Speech recognition could not access the microphone. Stop other recorders and retry.")
        }
    }

    private fun fail(message: String) {
        dispose()
        onError(message)
    }

    private fun dispose() {
        closed = true
        running = false
        handler.removeCallbacks(restart)
        recognizer?.cancel()
        recognizer?.destroy()
        recognizer = null
    }

    fun stop() {
        closed = true
        if (Looper.myLooper() == Looper.getMainLooper()) dispose() else handler.post { dispose() }
    }
}
