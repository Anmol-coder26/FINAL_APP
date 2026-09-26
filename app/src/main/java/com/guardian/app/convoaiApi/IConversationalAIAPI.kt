package com.guardian.app.convoaiApi

import io.agora.rtc2.RtcEngine
import io.agora.rtm.RtmClient

enum class TranscriptType {
    AGENT,
    USER,
    UNKNOWN
}

enum class TranscriptStatus {
    IN_PROGRESS,
    END,
    INTERRUPTED,
    UNKNOWN
}

enum class TranscriptRenderMode {
    Word,
    Text
}

data class Transcript(
    val turnId: Long = 0L,
    val text: String = "",
    val isFinal: Boolean = false,
    val type: TranscriptType = TranscriptType.USER,
    val status: TranscriptStatus = TranscriptStatus.IN_PROGRESS,
    val timestamp: Long = System.currentTimeMillis()
)

data class ConversationalAIAPIConfig(
    val rtcEngine: RtcEngine,
    val rtmClient: RtmClient,
    val renderMode: TranscriptRenderMode = TranscriptRenderMode.Word,
    val enableLog: Boolean = true,
    val enableRenderModeFallback: Boolean = true
)

interface IConversationalAIAPIEventHandler {
    fun onTranscriptUpdated(agentUserId: String, transcript: Transcript)
    fun onAgentStateChanged(agentUserId: String, state: String) {}
    fun onError(errorCode: Int, message: String) {}
}

interface IConversationalAIAPI {
    fun addHandler(handler: IConversationalAIAPIEventHandler)
    fun removeHandler(handler: IConversationalAIAPIEventHandler)
    fun subscribeMessage(channelName: String, callback: (Exception?) -> Unit)
    fun unsubscribeMessage(channelName: String, callback: (Exception?) -> Unit)
    fun destroy()
}
