package com.guardian.app.bhashini

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.guardian.app.BuildConfig
import java.io.File
import java.net.URI

data class SpeechConnectionConfig(
    val serverUrl: String = "https://dhruva-api.bhashini.gov.in",
    val apiKey: String = "",
    val serviceId: String = "",
    val backendUrl: String = BuildConfig.BACKEND_URL
) {
    fun speechError(): String? = when {
        apiKey.isBlank() || serviceId.isBlank() -> "BHASHINI transcription is not configured. Add your API key and ASR service ID in Call setup."
        !validUrl(serverUrl) -> "Enter a valid HTTPS speech service address."
        else -> null
    }

    companion object {
        fun validUrl(value: String): Boolean = runCatching {
            val uri = URI(value.trim())
            uri.scheme in listOf("https", "http", "wss", "ws") && !uri.host.isNullOrBlank() && uri.userInfo == null
        }.getOrDefault(false)
    }
}

/** Runtime configuration lets the hackathon build use real services without embedding keys. */
object SpeechConnectionStore {
    private const val NAME = "guardian_speech_connection"

    private fun prefs(context: Context) = EncryptedSharedPreferences.create(
        context.applicationContext, NAME,
        MasterKey.Builder(context.applicationContext).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun load(context: Context): SpeechConnectionConfig {
        val defaults = SpeechConnectionConfig(
            serverUrl = BuildConfig.BHASHINI_STT_ENDPOINT.ifBlank { "https://dhruva-api.bhashini.gov.in" },
            apiKey = BuildConfig.BHASHINI_INFERENCE_API_KEY,
            serviceId = BuildConfig.BHASHINI_ASR_SERVICE_ID
        )
        if (!File(context.applicationInfo.dataDir, "shared_prefs/$NAME.xml").exists()) return defaults
        return runCatching {
            val p = prefs(context)
            defaults.copy(
                serverUrl = p.getString("speech_url", defaults.serverUrl) ?: defaults.serverUrl,
                apiKey = p.getString("speech_key", defaults.apiKey) ?: defaults.apiKey,
                serviceId = p.getString("service_id", defaults.serviceId) ?: defaults.serviceId,
                backendUrl = p.getString("backend_url", defaults.backendUrl) ?: defaults.backendUrl
            )
        }.getOrDefault(defaults)
    }

    fun save(context: Context, config: SpeechConnectionConfig) {
        require(SpeechConnectionConfig.validUrl(config.serverUrl)) { "Invalid speech service address" }
        require(config.backendUrl.isBlank() || SpeechConnectionConfig.validUrl(config.backendUrl)) { "Invalid call server address" }
        check(prefs(context).edit()
            .putString("speech_url", config.serverUrl.trim())
            .putString("speech_key", config.apiKey.trim())
            .putString("service_id", config.serviceId.trim())
            .putString("backend_url", config.backendUrl.trim().trimEnd('/'))
            .commit()) { "Could not save call setup securely" }
    }
}
