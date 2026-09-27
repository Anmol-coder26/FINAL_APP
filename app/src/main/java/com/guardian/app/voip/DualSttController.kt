package com.guardian.app.voip

import android.content.Context
import android.util.Log
import com.guardian.app.bhashini.AndroidSpeechRecognizerFallback
import com.guardian.app.bhashini.BhashiniSttClient
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

enum class Speaker { LOCAL, REMOTE }

data class TranscriptLine(
    val speaker: Speaker,
    val text: String,
    val isFinal: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

class DualSttController(
    private val context: Context? = null,
    private val language: String = "hi"
) {
    private val _transcripts = MutableSharedFlow<TranscriptLine>(extraBufferCapacity = 256)
    val transcripts: SharedFlow<TranscriptLine> = _transcripts

    private var nativeFallback: AndroidSpeechRecognizerFallback? = null

    private val localStt = BhashiniSttClient(
        sourceLanguage = language,
        mode = BhashiniSttClient.Mode.PUSH,
        onTranscript = { t, f ->
            _transcripts.tryEmit(TranscriptLine(Speaker.LOCAL, t, f))
        },
        onError = { err ->
            Log.w("DualSttController", "Local Bhashini STT notice: $err. Triggering fallback.")
            startNativeFallback()
        }
    )

    private val remoteStt = BhashiniSttClient(
        sourceLanguage = language,
        mode = BhashiniSttClient.Mode.PUSH,
        onTranscript = { t, f ->
            _transcripts.tryEmit(TranscriptLine(Speaker.REMOTE, t, f))
        },
        onError = { err ->
            Log.w("DualSttController", "Remote Bhashini STT notice: $err")
        }
    )

    private fun startNativeFallback() {
        if (context != null && nativeFallback == null) {
            Log.d("DualSttController", "Starting Android native SpeechRecognizer fallback for language: $language")
            nativeFallback = AndroidSpeechRecognizerFallback(
                context = context,
                language = language,
                onTranscript = { t, f ->
                    _transcripts.tryEmit(TranscriptLine(Speaker.LOCAL, t, f))
                },
                onError = { err ->
                    Log.e("DualSttController", "Native SpeechRecognizer error: $err")
                }
            ).also { it.start() }
        }
    }

    fun start() {
        localStt.start()
        remoteStt.start()
    }

    fun pushLocal(samples: ShortArray, rate: Int) = localStt.pushPcm(samples, rate)

    fun pushRemote(samples: ShortArray, rate: Int) = remoteStt.pushPcm(samples, rate)

    fun stop() {
        localStt.stop()
        remoteStt.stop()
        nativeFallback?.stop()
        nativeFallback = null
    }
}
