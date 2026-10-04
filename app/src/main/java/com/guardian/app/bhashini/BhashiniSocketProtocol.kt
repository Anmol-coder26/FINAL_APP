package com.guardian.app.bhashini

import org.json.JSONArray
import org.json.JSONObject

/** Wire fields follow bhashini-dibd/bhashini-websockets' Java client example. */
object BhashiniSocketProtocol {
    const val SAMPLE_RATE = 8000
    const val CHUNK_BYTES = 1600 // 100 ms, mono PCM16

    fun task(language: String, serviceId: String): JSONArray = JSONArray().put(JSONObject().apply {
        put("taskType", "asr")
        put("config", JSONObject().apply {
            put("serviceId", serviceId)
            put("language", JSONObject().put("sourceLanguage", language.substringBefore('-')))
            put("samplingRate", SAMPLE_RATE)
            put("audioFormat", "wav")
            put("encoding", JSONObject.NULL)
        })
    })

    fun streamingConfig(): JSONObject = JSONObject()
        .put("responseFrequencyInSecs", 1.0)
        .put("responseTaskSequenceDepth", 1)

    fun audio(bytes: ByteArray): JSONObject = JSONObject().put("audio", JSONArray()
        .put(JSONObject().put("audioContent", bytes)))

    fun transcripts(payload: Any?, explicitFinal: Boolean? = null): List<Pair<String, Boolean>> {
        val root = when (payload) {
            is JSONObject -> payload
            is String -> runCatching { JSONObject(payload) }.getOrNull()
            else -> null
        } ?: return emptyList()
        val final = explicitFinal ?: root.optBoolean("isFinal", root.optBoolean("is_final", false))
        val results = mutableListOf<Pair<String, Boolean>>()
        val pipeline = root.optJSONArray("pipelineResponse")
        if (pipeline != null) {
            for (i in 0 until pipeline.length()) {
                val task = pipeline.optJSONObject(i) ?: continue
                if (task.optString("taskType", "asr") != "asr") continue
                val outputs = task.optJSONArray("output") ?: continue
                for (j in 0 until outputs.length()) {
                    val item = outputs.optJSONObject(j) ?: continue
                    val text = item.optString("source").trim()
                    if (text.isNotEmpty()) results += text to item.optBoolean("isFinal", item.optBoolean("is_final", final))
                }
            }
        } else {
            val text = root.optString("text", root.optString("transcript")).trim()
            if (text.isNotEmpty()) results += text to final
        }
        return results
    }
}
