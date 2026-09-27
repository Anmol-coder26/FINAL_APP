package com.guardian.app.core

import android.content.Context
import android.util.Log
import io.agora.rtc2.RtcEngine
import io.agora.rtc2.RtcEngineConfig
import com.guardian.app.BuildConfig

object AgoraEngineManager {
    private var engine: RtcEngine? = null

    @Synchronized
    fun get(context: Context): RtcEngine {
        if (engine == null) {
            val appId = BuildConfig.AGORA_APP_ID.ifBlank { "d575bd8b35004ad896366419f3a8a8f1" }
            Log.d("AgoraEngineManager", "Creating single RtcEngine instance for App ID: $appId")
            val config = RtcEngineConfig().apply {
                mContext = context.applicationContext
                mAppId = appId
            }
            engine = RtcEngine.create(config)
        }
        return engine!!
    }

    @Synchronized
    fun destroy() {
        try {
            RtcEngine.destroy()
        } catch (e: Exception) {
            Log.e("AgoraEngineManager", "Error destroying RtcEngine: ${e.message}")
        }
        engine = null
    }
}
