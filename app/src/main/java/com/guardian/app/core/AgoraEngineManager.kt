package com.guardian.app.core

import android.content.Context
import android.util.Log
import io.agora.rtc2.Constants
import io.agora.rtc2.IRtcEngineEventHandler
import io.agora.rtc2.RtcEngine
import io.agora.rtc2.RtcEngineConfig
import com.guardian.app.BuildConfig

object AgoraEngineManager {
    private var engine: RtcEngine? = null

    @Synchronized
    fun get(context: Context, eventHandler: IRtcEngineEventHandler? = null): RtcEngine {
        if (engine == null) {
            val appId = BuildConfig.AGORA_APP_ID.ifBlank { "d575bd8b35004ad896366419f3a8a8f1" }
            Log.d("AgoraEngineManager", "Initializing RtcEngine singleton with App ID: $appId")
            
            val dummyHandler = object : IRtcEngineEventHandler() {}
            val handlerToUse = eventHandler ?: dummyHandler

            engine = try {
                RtcEngine.create(context.applicationContext, appId, handlerToUse)
            } catch (e: Exception) {
                Log.w("AgoraEngineManager", "Classic RtcEngine.create failed, trying RtcEngineConfig: ${e.message}")
                val config = RtcEngineConfig().apply {
                    mContext = context.applicationContext
                    mAppId = appId
                    mEventHandler = handlerToUse
                    mChannelProfile = Constants.CHANNEL_PROFILE_COMMUNICATION
                }
                RtcEngine.create(config)
            } ?: throw IllegalStateException("Agora RtcEngine creation returned null")
        } else if (eventHandler != null) {
            engine?.addHandler(eventHandler)
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
