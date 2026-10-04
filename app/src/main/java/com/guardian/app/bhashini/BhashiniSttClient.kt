package com.guardian.app.bhashini

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import kotlin.math.sqrt
import android.util.Log
import com.guardian.app.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class BhashiniSttClient(
    var sourceLanguage: String = "hi",
    val mode: Mode = Mode.MICROPHONE,
    private val onTranscript: (text: String, isFinal: Boolean) -> Unit,
    private val onError: (error: String) -> Unit = {},
    private val onPcmChunk: ((ShortArray) -> Unit)? = null,
    private val onStatus: (String) -> Unit = {},
    private val onAudioLevel: (Float) -> Unit = {}
) {
    enum class Mode { MICROPHONE, PUSH }

    var activeLanguage: String
        get() = sourceLanguage
        set(value) { sourceLanguage = value }

    // Secondary constructor for backward compatibility with existing microphone call sites
    constructor(
        onTranscript: (text: String, isFinal: Boolean) -> Unit,
        onError: (error: String) -> Unit = {},
        onPcmChunk: ((ShortArray) -> Unit)? = null
    ) : this("hi", Mode.MICROPHONE, onTranscript, onError, onPcmChunk)

    companion object {
        private const val TAG = "BhashiniSttClient"
        private const val TARGET_SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val CHUNK_SIZE_BYTES = 3200 // 100ms at 16kHz 16-bit mono (1600 samples)
    }

    private val client: OkHttpClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private var audioRecord: AudioRecord? = null
    private var recordingThread: Thread? = null
    private val isRecording = AtomicBoolean(false)
    private val pushBuffer = ByteArrayOutputStream()

    private val wsListener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            if (!isRecording.get()) {
                webSocket.close(1000, "Stopped before connection")
                return
            }
            onStatus("BHASHINI connected — listening to microphone")
            Log.d(TAG, "WebSocket connected to Bhashini STT (Mode: $mode, Lang: $activeLanguage)")
            sendStartEvent(webSocket, activeLanguage)
            if (mode == Mode.MICROPHONE) {
                startAudioStream(webSocket)
            }
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            if (isRecording.get()) parseServerMessage(text)
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(code, reason)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            if (isRecording.get()) {
                stop()
                onError("Speech service disconnected")
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Log.e(TAG, "WebSocket failure: ${t.message}")
            if (isRecording.get()) {
                stop()
                onError("STT connection failed")
            }
        }
    }

    fun start(language: String = activeLanguage) {
        if (isRecording.get()) {
            Log.w(TAG, "STT client already running")
            return
        }

        val endpoint = BuildConfig.BHASHINI_STT_ENDPOINT.trim()
        if (endpoint.isBlank()) {
            onError("BHASHINI streaming endpoint is not configured")
            return
        }
        if (!endpoint.startsWith("wss://") && !endpoint.startsWith("ws://")) {
            onError("BHASHINI streaming endpoint must use ws:// or wss://")
            return
        }

        if (BuildConfig.BHASHINI_INFERENCE_API_KEY.isBlank()) {
            Log.w(TAG, "Bhashini inference key is blank, notifying fallback")
            onError("Bhashini inference API key is missing")
            return
        }

        activeLanguage = language
        isRecording.set(true)

        onStatus("Connecting to BHASHINI speech service…")
        try {
            val request = Request.Builder()
                .url(endpoint)
                .addHeader("Authorization", BuildConfig.BHASHINI_INFERENCE_API_KEY)
                .addHeader("x-pipeline-id", BuildConfig.BHASHINI_PIPELINE_ID)
                .build()
            webSocket = client.newWebSocket(request, wsListener)
        } catch (_: Exception) {
            isRecording.set(false)
            onError("Invalid BHASHINI streaming configuration")
        }
    }

    /**
     * Push external PCM samples (e.g. from AgoraFrameBridge in PUSH mode).
     * Automatically handles sample rate resampling to 16,000 Hz and sends 100ms chunks.
     */
    fun pushPcm(samples: ShortArray, sampleRate: Int) {
        if (mode != Mode.PUSH || !isRecording.get() || webSocket == null || samples.isEmpty()) return

        val resampled = if (sampleRate == TARGET_SAMPLE_RATE) {
            samples
        } else {
            resampleLinear(samples, sampleRate, TARGET_SAMPLE_RATE)
        }

        val pcmBytes = ByteArray(resampled.size * 2)
        val byteBuf = ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN)
        for (s in resampled) {
            byteBuf.putShort(s)
        }

        synchronized(pushBuffer) {
            pushBuffer.write(pcmBytes)
            while (pushBuffer.size() >= CHUNK_SIZE_BYTES) {
                val fullBytes = pushBuffer.toByteArray()
                val chunk = fullBytes.copyOfRange(0, CHUNK_SIZE_BYTES)
                if (logChunkCount < 5) {
                    logChunkCount++
                    Log.d("Guardian", "Chunk size: ${chunk.size} bytes, sampleRate: $sampleRate")
                }
                webSocket?.send(chunk.toByteString())

                pushBuffer.reset()
                if (fullBytes.size > CHUNK_SIZE_BYTES) {
                    pushBuffer.write(fullBytes, CHUNK_SIZE_BYTES, fullBytes.size - CHUNK_SIZE_BYTES)
                }
            }
        }
    }

    private fun resampleLinear(input: ShortArray, fromRate: Int, toRate: Int): ShortArray {
        if (fromRate <= 0 || toRate <= 0) return input
        val ratio = fromRate.toDouble() / toRate.toDouble()
        val outLength = (input.size / ratio).toInt()
        val out = ShortArray(outLength)
        for (i in 0 until outLength) {
            val srcIndex = i * ratio
            val srcIndexFloor = srcIndex.toInt()
            val fraction = srcIndex - srcIndexFloor
            if (srcIndexFloor + 1 < input.size) {
                val sample1 = input[srcIndexFloor].toDouble()
                val sample2 = input[srcIndexFloor + 1].toDouble()
                out[i] = (sample1 + fraction * (sample2 - sample1)).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            } else if (srcIndexFloor < input.size) {
                out[i] = input[srcIndexFloor]
            }
        }
        return out
    }

    private var logChunkCount = 0

    private fun sendStartEvent(ws: WebSocket, lang: String) {
        try {
            val startPayload = JSONObject().apply {
                put("event", "start")
                put("language", lang)
                put("useVad", true)
                put("inputEncoding", JSONObject().apply {
                    put("encoding", "linear16")
                    put("samplingRate", TARGET_SAMPLE_RATE)
                    put("bitsPerSample", 16)
                    put("numChannels", 1)
                })
                put("vadConfig", JSONObject().apply {
                    put("pStart", 0.4)
                    put("pauseMs", 800)
                })
                put("interimIntervalMs", 200)
                put("preProcessors", org.json.JSONArray().apply { put("vad") })
            }
            ws.send(startPayload.toString())
            Log.d(TAG, "Sent STT start event for lang: $lang (Mode: $mode)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send start event: ${e.message}")
        }
    }

    @SuppressLint("MissingPermission")
    @Synchronized
    private fun startAudioStream(ws: WebSocket) {
        if (!isRecording.get()) return
        val minBufferSize = AudioRecord.getMinBufferSize(TARGET_SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        if (minBufferSize <= 0) {
            onError("16 kHz microphone recording is unavailable")
            return
        }
        val bufferSize = maxOf(minBufferSize, CHUNK_SIZE_BYTES * 4)

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                TARGET_SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                audioRecord?.release()
                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    TARGET_SAMPLE_RATE,
                    CHANNEL_CONFIG,
                    AUDIO_FORMAT,
                    bufferSize
                )
            }

            check(audioRecord?.state == AudioRecord.STATE_INITIALIZED)
            audioRecord?.startRecording()
            check(audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize AudioRecord: ${e.message}")
            onError("Microphone recording initialization failed")
            return
        }

        val recorder = audioRecord ?: return
        recordingThread = Thread({
            val audioBuffer = ByteArray(CHUNK_SIZE_BYTES)
            var quietChunks = 0
            var lastStatus = ""
            try {
                while (isRecording.get()) {
                    val readBytes = recorder.read(audioBuffer, 0, audioBuffer.size)
                    if (readBytes <= 0) {
                        if (isRecording.get()) onError("Microphone capture stopped. Check microphone access.")
                        break
                    }
                    if (!isRecording.get()) break
                    val samples = ShortArray(readBytes / 2)
                    ByteBuffer.wrap(audioBuffer, 0, readBytes).order(ByteOrder.LITTLE_ENDIAN)
                        .asShortBuffer().get(samples)
                    val rms = sqrt(samples.sumOf { it.toDouble() * it } / samples.size)
                    quietChunks = if (rms < 30) quietChunks + 1 else 0
                    // Poll capture policy too: the OS can silently give an ordinary app zero PCM.
                    val silenced = Build.VERSION.SDK_INT >= 29 &&
                        recorder.activeRecordingConfiguration?.isClientSilenced == true
                    val status = when {
                        silenced -> "Android is silencing this microphone during the call. Use SuSagi on a second device beside the speakerphone."
                        quietChunks >= 80 -> "No audible speech. Check speaker volume and microphone access; try a second listening device if this phone is in a SIM call."
                        else -> "BHASHINI connected — microphone audio available"
                    }
                    if (status != lastStatus) { onStatus(status); lastStatus = status }
                    onAudioLevel((rms / 5000).toFloat().coerceIn(0f, 1f))
                    if (!silenced) {
                        if (ws.queueSize() > 320000 || !ws.send(audioBuffer.toByteString(0, readBytes))) {
                            onError("Speech connection cannot keep up with live audio")
                            break
                        }
                        onPcmChunk?.invoke(samples)
                    }
                }
            } catch (_: Exception) {
                if (isRecording.get()) onError("Microphone capture failed")
            }
        }, "BhashiniAudioStream").apply { start() }
    }

    private fun parseServerMessage(jsonStr: String) {
        try {
            val json = JSONObject(jsonStr)
            val event = json.optString("event", "")

            when (event) {
                "error" -> onError("BHASHINI rejected the speech session. Check endpoint and credentials.")
                "speech_start" -> Log.v(TAG, "Bhashini speech_start")
                "speech_pause" -> Log.v(TAG, "Bhashini speech_pause")
                "speech_resume" -> Log.v(TAG, "Bhashini speech_resume")
                "speech_end", "transcript" -> {
                    val text = json.optString("text", json.optString("transcript", ""))
                    val isFinal = json.optBoolean("isFinal", event == "speech_end")
                    if (text.isNotBlank()) {
                        onTranscript(text.trim(), isFinal)
                    }
                }
                else -> {
                    // Check standard ULCA streaming pipeline response structure
                    val pipelineResponse = json.optJSONArray("pipelineResponse")
                    if (pipelineResponse != null && pipelineResponse.length() > 0) {
                        val firstResp = pipelineResponse.getJSONObject(0)
                        val outputArr = firstResp.optJSONArray("output")
                        if (outputArr != null && outputArr.length() > 0) {
                            val outputObj = outputArr.getJSONObject(0)
                            val text = outputObj.optString("source", outputObj.optString("target", ""))
                            val isFinal = outputObj.optBoolean("isFinal", true)
                            if (text.isNotBlank()) {
                                onTranscript(text.trim(), isFinal)
                            }
                        }
                    } else if (json.has("text") || json.has("transcript")) {
                        val text = json.optString("text", json.optString("transcript", ""))
                        val isFinal = json.optBoolean("isFinal", false)
                        if (text.isNotBlank()) {
                            onTranscript(text.trim(), isFinal)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing server STT message: ${e.message}")
        }
    }

    @Synchronized
    fun stop() {
        if (!isRecording.getAndSet(false)) return

        try {
            webSocket?.send(JSONObject().apply { put("event", "finalize") }.toString())
            webSocket?.send(JSONObject().apply { put("event", "stop") }.toString())
            webSocket?.close(1000, "Normal closure")
        } catch (_: Exception) {}

        webSocket = null

        try { audioRecord?.stop() } catch (_: Exception) {}
        if (Thread.currentThread() != recordingThread) {
            try { recordingThread?.join(500) } catch (_: InterruptedException) {}
        }
        try { audioRecord?.release() } catch (_: Exception) {}

        audioRecord = null
        recordingThread?.interrupt()
        recordingThread = null
        synchronized(pushBuffer) {
            pushBuffer.reset()
        }
        Log.d(TAG, "Bhashini STT client stopped cleanly (Mode: $mode)")
    }
}
