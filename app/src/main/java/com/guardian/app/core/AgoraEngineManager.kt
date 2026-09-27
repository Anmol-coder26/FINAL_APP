package com.guardian.app.core

import android.content.Context
import io.agora.rtc2.RtcEngine
import io.agora.rtc2.RtcEngineConfig
import com.guardian.app.BuildConfig

object AgoraEngineManager {
    private var engine: RtcEngine? = null

    fun get(context: Context): RtcEngine {
        if (engine == null) {
            val config = RtcEngineConfig().apply {
                mContext = context.applicationContext
                mAppId = BuildConfig.AGORA_APP_ID.ifBlank { "d575bd8b35004ad896366419f3a8a8f1" }
                mEventHandler = null // Event handler is registered separately
            }
            // Ensure only one instance is ever created.
            engine = RtcEngine.create(config) ?: throw IllegalStateException("Agora RtcEngine creation failed")
        }
        return engine!!
    }

    fun destroy() {
        RtcEngine.destroy()
        engine = null
    }
}
