package com.guardian.app.callprotect

import android.content.Context
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.ToneGenerator
import android.speech.tts.TextToSpeech
import android.util.Base64
import android.util.Log
import com.guardian.app.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.concurrent.TimeUnit

class CriticalWarningPlayer(private val context: Context) {
    companion object {
        private const val TAG = "CriticalWarningPlayer"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }

    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private var mediaPlayer: MediaPlayer? = null
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isInitialized = true
                tts?.language = Locale.ENGLISH
            }
        }
    }

    fun playWarning(message: String, languageTag: String = "en-IN") {
        try {
            val tone = ToneGenerator(AudioManager.STREAM_ALARM, 100)
            tone.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 1000)
        } catch (_: Exception) {}

        try {
            if (isInitialized && tts != null) {
                tts?.language = Locale.forLanguageTag(languageTag)
                tts?.speak(message, TextToSpeech.QUEUE_ADD, null, "guardian_warning")
            }
        } catch (_: Exception) {}
    }

    fun playBhashiniTts(text: String, language: String = "hi") {
        // 1. Play siren alarm tone
        try {
            val tone = ToneGenerator(AudioManager.STREAM_ALARM, 100)
            tone.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 1000)
        } catch (_: Exception) {}

        val endpoint = BuildConfig.BHASHINI_REST_ENDPOINT.ifBlank {
            "https://dhruva-api.bhashini.gov.in/services/inference/pipeline"
        }
        val inferenceKey = BuildConfig.BHASHINI_INFERENCE_API_KEY

        if (inferenceKey.isBlank()) {
            playWarning(text, if (language == "hi") "hi-IN" else "en-IN")
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val payload = JSONObject().apply {
                    put("pipelineTasks", JSONArray().apply {
                        put(JSONObject().apply {
                            put("taskType", "tts")
                            put("config", JSONObject().apply {
                                put("language", JSONObject().apply {
                                    put("sourceLanguage", language)
                                })
                                put("gender", "female")
                            })
                        })
                    })
                    put("inputData", JSONObject().apply {
                        put("input", JSONArray().apply {
                            put(JSONObject().apply {
                                put("source", text)
                            })
                        })
                    })
                }

                val request = Request.Builder()
                    .url(endpoint)
                    .addHeader("Authorization", inferenceKey)
                    .addHeader("x-pipeline-id", BuildConfig.BHASHINI_PIPELINE_ID)
                    .addHeader("Content-Type", "application/json")
                    .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.w(TAG, "Bhashini TTS returned ${response.code}, falling back to system TTS")
                        playWarning(text, if (language == "hi") "hi-IN" else "en-IN")
                        return@use
                    }

                    val respBody = response.body?.string().orEmpty()
                    val json = JSONObject(respBody)
                    val pipelineResp = json.optJSONArray("pipelineResponse") ?: JSONArray()
                    if (pipelineResp.length() > 0) {
                        val audioArr = pipelineResp.getJSONObject(0).optJSONArray("audio") ?: JSONArray()
                        if (audioArr.length() > 0) {
                            val base64Audio = audioArr.getJSONObject(0).optString("audioContent", "")
                            if (base64Audio.isNotBlank()) {
                                playBase64Audio(base64Audio)
                                return@use
                            }
                        }
                    }

                    // Fallback if audio content empty
                    playWarning(text, if (language == "hi") "hi-IN" else "en-IN")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Bhashini TTS error: ${e.message}, falling back to system TTS")
                playWarning(text, if (language == "hi") "hi-IN" else "en-IN")
            }
        }
    }

    private fun playBase64Audio(base64Audio: String) {
        try {
            val audioBytes = Base64.decode(base64Audio, Base64.DEFAULT)
            val tempFile = File.createTempFile("bhashini_tts_", ".wav", context.cacheDir)
            FileOutputStream(tempFile).use { fos ->
                fos.write(audioBytes)
                fos.flush()
            }

            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setDataSource(tempFile.absolutePath)
                prepare()
                start()
                setOnCompletionListener {
                    try {
                        tempFile.delete()
                    } catch (_: Exception) {}
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decode/play base64 TTS audio: ${e.message}")
        }
    }

    fun release() {
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (_: Exception) {}
        tts = null
        try {
            mediaPlayer?.release()
        } catch (_: Exception) {}
        mediaPlayer = null
        isInitialized = false
    }
}
