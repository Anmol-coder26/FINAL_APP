package com.guardian.app.bhashini

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
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
    private var activeLanguage: String = "hi",
    val mode: Mode = Mode.MICROPHONE,
    private val onTranscript: (text: String, isFinal: Boolean) -> Unit,
    private val onError: (error: String) -> Unit = {},
    private val onPcmChunk: ((ShortArray) -> Unit)? = null
) {
    enum class Mode { MICROPHONE, PUSH }

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
            Log.d(TAG, "WebSocket connected to Bhashini STT (Mode: $mode, Lang: $activeLanguage)")
            sendStartEvent(webSocket, activeLanguage)
            if (mode == Mode.MICROPHONE) {
                startAudioStream(webSocket)
            }
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            parseServerMessage(text)
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            Log.d(TAG, "WebSocket closing: $code / $reason")
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            Log.d(TAG, "WebSocket closed")
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Log.e(TAG, "WebSocket failure: ${t.message}")
            stop()
            onError(t.message ?: "STT WebSocket failure")
        }
    }

    fun start(language: String = activeLanguage) {
        if (isRecording.get()) {
            Log.w(TAG, "STT client already running")
            return
        }

        val endpoint = BuildConfig.BHASHINI_STT_ENDPOINT.ifBlank {
            "wss://api.bhashini.gov.in/v1/stt/stream"
        }

        if (BuildConfig.BHASHINI_INFERENCE_API_KEY.isBlank()) {
            Log.w(TAG, "Bhashini inference key is blank, notifying fallback")
            onError("Bhashini inference API key is missing")
            return
        }

        activeLanguage = language
        isRecording.set(true)

        val request = Request.Builder()
            .url(endpoint)
            .addHeader("Authorization", BuildConfig.BHASHINI_INFERENCE_API_KEY)
            .addHeader("x-pipeline-id", BuildConfig.BHASHINI_PIPELINE_ID)
            .build()

        webSocket = client.newWebSocket(request, wsListener)
    }

    /**
     * Push external PCM samples (e.g. from AgoraFrameBridge in PUSH mode).
     * Automatically handles sample rate resampling to 16,000 Hz and sends 100ms chunks.
     */
    fun pushPcm(samples: ShortArray, sampleRate: Int) {
        if (!isRecording.get() || webSocket == null || samples.isEmpty()) return

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
    private fun startAudioStream(ws: WebSocket) {
        val minBufferSize = AudioRecord.getMinBufferSize(TARGET_SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
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
                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.DEFAULT,
                    TARGET_SAMPLE_RATE,
                    CHANNEL_CONFIG,
                    AUDIO_FORMAT,
                    bufferSize
                )
            }

            audioRecord?.startRecording()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize AudioRecord: ${e.message}")
            onError("Microphone recording initialization failed")
            return
        }

        recordingThread = Thread({
            val audioBuffer = ByteArray(CHUNK_SIZE_BYTES)
            while (isRecording.get()) {
                val readBytes = audioRecord?.read(audioBuffer, 0, audioBuffer.size) ?: -1
                if (readBytes > 0) {
                    val byteString = audioBuffer.toByteString(0, readBytes)
                    ws.send(byteString)

                    if (onPcmChunk != null && readBytes >= 2) {
                        val shortCount = readBytes / 2
                        val shortArr = ShortArray(shortCount)
                        ByteBuffer.wrap(audioBuffer, 0, readBytes)
                            .order(ByteOrder.LITTLE_ENDIAN)
                            .asShortBuffer()
                            .get(shortArr)
                        onPcmChunk.invoke(shortArr)
                    }
                }
            }
        }, "BhashiniAudioStream").apply {
            priority = Thread.MAX_PRIORITY
            start()
        }
    }

    private fun parseServerMessage(jsonStr: String) {
        try {
            val json = JSONObject(jsonStr)
            val event = json.optString("event", "")

            when (event) {
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

    fun stop() {
        if (!isRecording.getAndSet(false)) return

        try {
            webSocket?.send(JSONObject().apply { put("event", "finalize") }.toString())
            webSocket?.send(JSONObject().apply { put("event", "stop") }.toString())
            webSocket?.close(1000, "Normal closure")
        } catch (_: Exception) {}

        webSocket = null

        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (_: Exception) {}

        audioRecord = null
        recordingThread?.interrupt()
        recordingThread = null
        synchronized(pushBuffer) {
            pushBuffer.reset()
        }
        Log.d(TAG, "Bhashini STT client stopped cleanly (Mode: $mode)")
    }
}
