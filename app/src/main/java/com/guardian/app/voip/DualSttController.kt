package com.guardian.app.voip

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

class DualSttController(language: String = "hi") {
    private val _transcripts = MutableSharedFlow<TranscriptLine>(extraBufferCapacity = 256)
    val transcripts: SharedFlow<TranscriptLine> = _transcripts

    private val localStt = BhashiniSttClient(
        activeLanguage = language,
        mode = BhashiniSttClient.Mode.PUSH,
        onTranscript = { t, f ->
            _transcripts.tryEmit(TranscriptLine(Speaker.LOCAL, t, f))
        },
        onError = { err ->
            _transcripts.tryEmit(TranscriptLine(Speaker.LOCAL, "[Local STT error: $err]", true))
        }
    )

    private val remoteStt = BhashiniSttClient(
        activeLanguage = language,
        mode = BhashiniSttClient.Mode.PUSH,
        onTranscript = { t, f ->
            _transcripts.tryEmit(TranscriptLine(Speaker.REMOTE, t, f))
        },
        onError = { err ->
            _transcripts.tryEmit(TranscriptLine(Speaker.REMOTE, "[Remote STT error: $err]", true))
        }
    )

    fun start() {
        localStt.start()
        remoteStt.start()
    }

    fun pushLocal(samples: ShortArray, rate: Int) = localStt.pushPcm(samples, rate)

    fun pushRemote(samples: ShortArray, rate: Int) = remoteStt.pushPcm(samples, rate)

    fun stop() {
        localStt.stop()
        remoteStt.stop()
    }
}
