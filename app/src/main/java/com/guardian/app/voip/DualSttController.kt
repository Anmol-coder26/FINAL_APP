package com.guardian.app.voip

import android.content.Context
import android.util.Log
import com.guardian.app.bhashini.BhashiniSttClient
import com.guardian.app.bhashini.SpeechConnectionConfig
import com.guardian.app.bhashini.SpeechConnectionStore
import com.guardian.app.bhashini.SpeechStreamPhase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

enum class Speaker { LOCAL, REMOTE }
data class TranscriptLine(val speaker: Speaker, val text: String, val isFinal: Boolean, val timestamp: Long = System.currentTimeMillis())

/** Agora owns the microphone. These two sessions consume only copied Agora PCM. */
class DualSttController(
    context: Context? = null,
    private val language: String = "hi",
    private val configuration: SpeechConnectionConfig = context?.let { SpeechConnectionStore.load(it) } ?: SpeechConnectionConfig(),
    private val onStreamState: (Speaker, SpeechStreamPhase, String?) -> Unit = { _, _, _ -> },
    private val onPcmReceived: (Speaker) -> Unit = {}
) {
    private val _transcripts = MutableSharedFlow<TranscriptLine>(extraBufferCapacity = 256, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val transcripts: SharedFlow<TranscriptLine> = _transcripts
    private val active = AtomicBoolean(false)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val localFrames = Channel<Pair<ShortArray, Int>>(8, BufferOverflow.DROP_OLDEST)
    private val remoteFrames = Channel<Pair<ShortArray, Int>>(8, BufferOverflow.DROP_OLDEST)
    private val localSeen = AtomicBoolean(false)
    private val remoteSeen = AtomicBoolean(false)
    private fun client(speaker: Speaker) = BhashiniSttClient(
        sourceLanguage = language, mode = BhashiniSttClient.Mode.PUSH, configuration = configuration,
        onTranscript = { text, final -> if (active.get()) _transcripts.tryEmit(TranscriptLine(speaker, text, final)) },
        onPhase = { phase -> if (active.get()) onStreamState(speaker, phase, null) },
        onError = { message -> if (active.get()) onStreamState(speaker, SpeechStreamPhase.ERROR, message) }
    )
    private val localStt = client(Speaker.LOCAL)
    private val remoteStt = client(Speaker.REMOTE)

    fun start() {
        if (!active.compareAndSet(false, true)) return
        scope.launch { for ((pcm, rate) in localFrames) localStt.pushPcm(pcm, rate) }
        scope.launch { for ((pcm, rate) in remoteFrames) remoteStt.pushPcm(pcm, rate) }
        localStt.start()
        remoteStt.start()
    }

    fun pushLocal(samples: ShortArray, rate: Int) {
        if (!active.get() || samples.isEmpty()) return
        if (!localSeen.getAndSet(true)) {
            onPcmReceived(Speaker.LOCAL)
            Log.i("GuardianPCM", "local_frames_received rate=$rate samples=${samples.size}")
        }
        localFrames.trySend(samples to rate)
    }

    fun pushRemote(samples: ShortArray, rate: Int) {
        if (!active.get() || samples.isEmpty()) return
        if (!remoteSeen.getAndSet(true)) {
            onPcmReceived(Speaker.REMOTE)
            Log.i("GuardianPCM", "remote_frames_received rate=$rate samples=${samples.size}")
        }
        remoteFrames.trySend(samples to rate)
    }

    fun stop() {
        active.set(false)
        localFrames.close()
        remoteFrames.close()
        scope.cancel()
        localStt.stop()
        remoteStt.stop()
    }

    fun remoteDisconnected() { remoteSeen.set(false) }
}
