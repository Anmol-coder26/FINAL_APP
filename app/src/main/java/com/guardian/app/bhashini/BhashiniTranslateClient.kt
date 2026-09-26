package com.guardian.app.bhashini

import android.util.Log
import android.util.LruCache
import com.guardian.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object BhashiniTranslateClient {
    private const val TAG = "BhashiniTranslate"
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    // LRU Cache for translation results: Key = "sourceLang:targetLang:text", Value = translated text
    private val translationCache = LruCache<String, String>(50)

    val supportedLanguages = mapOf(
        "hi" to "Hindi",
        "en" to "English",
        "ta" to "Tamil",
        "te" to "Telugu",
        "bn" to "Bengali",
        "mr" to "Marathi",
        "kn" to "Kannada",
        "ml" to "Malayalam",
        "gu" to "Gujarati",
        "pa" to "Punjabi"
    )

    suspend fun translate(
        text: String,
        sourceLang: String,
        targetLang: String
    ): String = withContext(Dispatchers.IO) {
        if (text.isBlank() || sourceLang.equals(targetLang, ignoreCase = true)) {
            return@withContext text
        }

        val cacheKey = "${sourceLang.lowercase()}:${targetLang.lowercase()}:$text"
        translationCache.get(cacheKey)?.let { cached ->
            return@withContext cached
        }

        val endpoint = BuildConfig.BHASHINI_REST_ENDPOINT.ifBlank {
            "https://dhruva-api.bhashini.gov.in/services/inference/pipeline"
        }

        val inferenceKey = BuildConfig.BHASHINI_INFERENCE_API_KEY
        if (inferenceKey.isBlank()) {
            return@withContext text
        }

        try {
            val payload = JSONObject().apply {
                put("pipelineTasks", JSONArray().apply {
                    put(JSONObject().apply {
                        put("taskType", "translation")
                        put("config", JSONObject().apply {
                            put("language", JSONObject().apply {
                                put("sourceLanguage", sourceLang)
                                put("targetLanguage", targetLang)
                            })
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
                    Log.w(TAG, "Translation API returned code ${response.code}")
                    return@withContext text
                }

                val respBody = response.body?.string().orEmpty()
                val jsonResp = JSONObject(respBody)
                val pipelineResp = jsonResp.optJSONArray("pipelineResponse") ?: JSONArray()
                if (pipelineResp.length() > 0) {
                    val taskOutput = pipelineResp.getJSONObject(0).optJSONArray("output") ?: JSONArray()
                    if (taskOutput.length() > 0) {
                        val translated = taskOutput.getJSONObject(0).optString("target", text)
                        if (translated.isNotBlank()) {
                            translationCache.put(cacheKey, translated)
                            return@withContext translated
                        }
                    }
                }
            }
        } catch (e: Exception) {
            val maskedKey = inferenceKey.take(6) + "..."
            Log.e(TAG, "Translation error (auth: $maskedKey): ${e.message}")
        }

        return@withContext text
    }
}
