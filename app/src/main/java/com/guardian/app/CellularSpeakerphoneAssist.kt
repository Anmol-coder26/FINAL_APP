package com.guardian.app

import android.content.Context
import android.util.Log
import com.guardian.app.bhashini.BhashiniSttClient

/**
 * F26 Cellular "Speakerphone Assist" Mode
 *
 * Android OS restrictions do not permit third-party applications to intercept raw
 * cellular call audio streams without root or carrier privilege.
 * This helper provides transparent, honest single-stream microphone analysis
 * when the user places the call on speakerphone.
 */
class CellularSpeakerphoneAssist(
    private val context: Context,
    private val language: String = "hi",
    private val onTranscript: (String, Boolean) -> Unit,
    private val onError: (String) -> Unit = {}
) {
    companion object {
        const val BANNER_MESSAGE =
            "Speakerphone Assist — put the call on speakerphone for microphone analysis. Dual-speaker separation is unavailable on cellular calls due to Android OS security policies."
    }

    private var sttClient: BhashiniSttClient? = null
    var isActive: Boolean = false
        private set

    fun start() {
        if (isActive) return
        Log.d("SpeakerphoneAssist", "Starting Speakerphone Assist mode (MICROPHONE)")
        sttClient = BhashiniSttClient(
            activeLanguage = language,
            mode = BhashiniSttClient.Mode.MICROPHONE,
            onTranscript = { text, isFinal ->
                onTranscript(text, isFinal)
            },
            onError = { err ->
                onError(err)
            }
        ).also {
            it.start()
            isActive = true
        }
    }

    fun stop() {
        sttClient?.stop()
        sttClient = null
        isActive = false
        Log.d("SpeakerphoneAssist", "Stopped Speakerphone Assist mode")
    }
}
