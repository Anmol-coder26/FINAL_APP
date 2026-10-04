package com.guardian.app.bhashini

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import com.guardian.app.BuildConfig
import io.socket.client.IO
import io.socket.client.Socket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.sqrt

enum class SpeechStreamPhase { IDLE, CONNECTING, CONFIGURING, READY, ERROR, STOPPED }

/** BHASHINI Socket.IO session. PUSH mode never opens an Android microphone. */
class BhashiniSttClient(
    var sourceLanguage: String = "hi",
    val mode: Mode = Mode.MICROPHONE,
    private val onTranscript: (String, Boolean) -> Unit,
    private val onError: (String) -> Unit = {},
    private val onPcmChunk: ((ShortArray) -> Unit)? = null,
    private val onStatus: (String) -> Unit = {},
    private val onAudioLevel: (Float) -> Unit = {},
    private val onPhase: (SpeechStreamPhase) -> Unit = {},
    private val configuration: SpeechConnectionConfig = SpeechConnectionConfig(
        BuildConfig.BHASHINI_STT_ENDPOINT.ifBlank { "https://dhruva-api.bhashini.gov.in" },
        BuildConfig.BHASHINI_INFERENCE_API_KEY, BuildConfig.BHASHINI_ASR_SERVICE_ID
    )
) {
    enum class Mode { MICROPHONE, PUSH }
    var activeLanguage: String
        get() = sourceLanguage
        set(value) { sourceLanguage = value }

    constructor(onTranscript: (String, Boolean) -> Unit, onError: (String) -> Unit = {}, onPcmChunk: ((ShortArray) -> Unit)? = null)
        : this("hi", Mode.MICROPHONE, onTranscript, onError, onPcmChunk)

    private val running = AtomicBoolean(false)
    private val ready = AtomicBoolean(false)
    private val closed = AtomicBoolean(false)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var readyTimeout: Job? = null
    private var socket: Socket? = null
    private var recorder: AudioRecord? = null
    private var recordingThread: Thread? = null
    private val pcmBuffer = ByteArrayOutputStream()

    @Synchronized fun start(language: String = sourceLanguage) {
        if (closed.get() || running.get()) return
        configuration.speechError()?.let {
            onPhase(SpeechStreamPhase.ERROR)
            onError(it)
            return
        }
        sourceLanguage = language
        running.set(true)
        onPhase(SpeechStreamPhase.CONNECTING)
        onStatus("Connecting to BHASHINI speech service…")
        try {
            val options = IO.Options().apply {
                auth = mapOf("Authorization" to configuration.apiKey)
                transports = arrayOf("websocket")
                reconnection = false
                timeout = 10_000
            }
            val address = configuration.serverUrl.replaceFirst("wss://", "https://").replaceFirst("ws://", "http://")
            val connection = IO.socket(address, options)
            socket = connection
            connection.on(Socket.EVENT_CONNECT) {
                if (!running.get()) return@on
                onPhase(SpeechStreamPhase.CONFIGURING)
                onStatus("BHASHINI connected — waiting for speech session readiness")
                Log.i("GuardianSTT", "socket_connected mode=$mode")
                connection.emit("start", BhashiniSocketProtocol.task(sourceLanguage, configuration.serviceId), BhashiniSocketProtocol.streamingConfig())
                readyTimeout = scope.launch {
                    delay(10_000)
                    if (!ready.get()) fail("BHASHINI did not acknowledge the speech session. Check the ASR service ID and language.")
                }
            }
            connection.on("ready") {
                if (!running.get() || ready.getAndSet(true)) return@on
                readyTimeout?.cancel()
                onPhase(SpeechStreamPhase.READY)
                onStatus("BHASHINI speech session ready — listening")
                Log.i("GuardianSTT", "session_ready mode=$mode")
                if (mode == Mode.MICROPHONE) startMicrophone()
            }
            connection.on("response") { args ->
                if (!running.get()) return@on
                val payload = args.firstOrNull()
                val body = when (payload) {
                    is JSONObject -> payload
                    is String -> runCatching { JSONObject(payload) }.getOrNull()
                    else -> null
                }
                if (body?.has("error") == true && !body.isNull("error")) {
                    fail("BHASHINI rejected the speech task. Check the service ID, key, and language.")
                    return@on
                }
                val finalFlag = args.drop(1).firstOrNull { it is Boolean } as? Boolean
                BhashiniSocketProtocol.transcripts(payload, finalFlag).forEach { (text, final) ->
                    if (running.get()) onTranscript(text, final)
                }
            }
            connection.on(Socket.EVENT_CONNECT_ERROR) { fail("Could not connect to BHASHINI. Check the speech service address, key, and internet connection.") }
            connection.on(Socket.EVENT_DISCONNECT) { if (running.get()) fail("BHASHINI disconnected. Stop and retry when the connection returns.") }
            connection.on("abort") { fail("BHASHINI aborted the speech task. Check the service ID and selected language.") }
            connection.on("terminate") { fail("BHASHINI ended the speech session. Stop and retry.") }
            connection.connect()
        } catch (_: Exception) {
            fail("Could not initialize the BHASHINI speech connection.")
        }
    }

    fun pushPcm(samples: ShortArray, sampleRate: Int) {
        if (!running.get() || !ready.get() || sampleRate <= 0 || samples.isEmpty()) return
        val targetRate = BhashiniSocketProtocol.SAMPLE_RATE
        val converted = if (sampleRate == targetRate) samples else {
            val ratio = sampleRate.toDouble() / targetRate
            ShortArray((samples.size / ratio).toInt()) { outIndex ->
                val position = outIndex * ratio
                val left = position.toInt().coerceAtMost(samples.lastIndex)
                val right = (left + 1).coerceAtMost(samples.lastIndex)
                (samples[left] + (samples[right] - samples[left]) * (position - left)).toInt().toShort()
            }
        }
        val bytes = ByteBuffer.allocate(converted.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        converted.forEach { bytes.putShort(it) }
        synchronized(pcmBuffer) {
            pcmBuffer.write(bytes.array())
            while (pcmBuffer.size() >= BhashiniSocketProtocol.CHUNK_BYTES && running.get() && ready.get()) {
                val pending = pcmBuffer.toByteArray()
                val chunk = pending.copyOfRange(0, BhashiniSocketProtocol.CHUNK_BYTES)
                socket?.emit("data", BhashiniSocketProtocol.audio(chunk), JSONObject(), false, false)
                pcmBuffer.reset()
                if (pending.size > chunk.size) pcmBuffer.write(pending, chunk.size, pending.size - chunk.size)
            }
        }
    }

    @SuppressLint("MissingPermission")
    @Synchronized private fun startMicrophone() {
        if (!running.get() || closed.get()) return
        val rate = 16000
        val minSize = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (minSize <= 0) { fail("16 kHz microphone recording is unavailable"); return }
        try {
            recorder = AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, rate,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(minSize, 12800))
            check(recorder?.state == AudioRecord.STATE_INITIALIZED)
            recorder?.startRecording()
            check(recorder?.recordingState == AudioRecord.RECORDSTATE_RECORDING)
        } catch (_: Exception) {
            fail("Could not access the microphone. Check permission and stop other recorders.")
            return
        }
        val input = recorder ?: return
        recordingThread = Thread({
            val bytes = ByteArray(3200)
            var quiet = 0
            var previousStatus = ""
            try {
                while (running.get()) {
                    val read = input.read(bytes, 0, bytes.size)
                    if (read <= 0) { if (running.get()) fail("Microphone capture stopped. Stop and retry."); break }
                    if (!running.get()) break
                    val pcm = ShortArray(read / 2)
                    ByteBuffer.wrap(bytes, 0, read).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(pcm)
                    if (pcm.isEmpty()) continue
                    val rms = sqrt(pcm.sumOf { it.toDouble() * it } / pcm.size)
                    quiet = if (rms < 30) quiet + 1 else 0
                    val silenced = Build.VERSION.SDK_INT >= 29 && input.activeRecordingConfiguration?.isClientSilenced == true
                    val status = when {
                        silenced -> "Android is silencing this microphone during the call. Use a second listening device beside the speakerphone."
                        quiet >= 80 -> "No audible speech. Check speaker volume and microphone access."
                        else -> "BHASHINI speech session ready — microphone audio available"
                    }
                    if (status != previousStatus) { onStatus(status); previousStatus = status }
                    onAudioLevel((rms / 5000).toFloat().coerceIn(0f, 1f))
                    if (!silenced) {
                        onPcmChunk?.invoke(pcm)
                        pushPcm(pcm, rate)
                    }
                }
            } catch (_: Exception) { if (running.get()) fail("Microphone capture failed. Stop and retry.") }
        }, "BhashiniMicrophone").apply { start() }
    }

    private fun fail(message: String) {
        if (!running.getAndSet(false)) return
        ready.set(false)
        stop()
        onPhase(SpeechStreamPhase.ERROR)
        onError(message)
        Log.w("GuardianSTT", "session_failed mode=$mode")
    }

    @Synchronized fun stop() {
        if (closed.getAndSet(true)) return
        running.set(false)
        ready.set(false)
        scope.cancel()
        val connection = socket
        socket = null
        connection?.off()
        if (connection?.connected() == true) connection.emit("stop", null, null, true, true)
        connection?.disconnect()
        try { recorder?.stop() } catch (_: Exception) {}
        recordingThread?.interrupt()
        if (Thread.currentThread() != recordingThread) {
            try { recordingThread?.join(300) } catch (_: InterruptedException) {}
        }
        try { recorder?.release() } catch (_: Exception) {}
        recorder = null
        recordingThread = null
        synchronized(pcmBuffer) { pcmBuffer.reset() }
        onPhase(SpeechStreamPhase.STOPPED)
    }
}
