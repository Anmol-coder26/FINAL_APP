package com.guardian.app.convoaiApi

import android.util.Log
import io.agora.rtm.ErrorInfo
import io.agora.rtm.LinkStateEvent
import io.agora.rtm.LockEvent
import io.agora.rtm.MessageEvent
import io.agora.rtm.PresenceEvent
import io.agora.rtm.ResultCallback
import io.agora.rtm.RtmEventListener
import io.agora.rtm.StorageEvent
import io.agora.rtm.SubscribeOptions
import io.agora.rtm.TopicEvent
import org.json.JSONObject
import java.util.concurrent.CopyOnWriteArrayList

class ConversationalAIAPIImpl(
    private val config: ConversationalAIAPIConfig
) : IConversationalAIAPI {

    private val TAG = "ConversationalAIAPI"
    private val handlers = CopyOnWriteArrayList<IConversationalAIAPIEventHandler>()

    private val rtmEventListener = object : RtmEventListener {
        override fun onMessageEvent(event: MessageEvent?) {
            event ?: return
            val publisherId = event.publisherId.orEmpty()
            val rawData = event.message?.data
            val textContent = when (rawData) {
                is ByteArray -> String(rawData, Charsets.UTF_8)
                is String -> rawData
                else -> rawData?.toString().orEmpty()
            }

            if (config.enableLog) {
                Log.d(TAG, "onMessageEvent from $publisherId: $textContent")
            }

            if (textContent.isNotBlank()) {
                val transcript = parseTranscriptPayload(textContent)
                notifyTranscriptUpdated(publisherId, transcript)
            }
        }

        override fun onPresenceEvent(event: PresenceEvent?) {}
        override fun onTopicEvent(event: TopicEvent?) {}
        override fun onLockEvent(event: LockEvent?) {}
        override fun onStorageEvent(event: StorageEvent?) {}
        override fun onLinkStateEvent(event: LinkStateEvent?) {}
    }

    init {
        try {
            config.rtmClient.addEventListener(rtmEventListener)
            if (config.enableLog) {
                Log.d(TAG, "ConversationalAIAPIImpl initialized with renderMode=${config.renderMode}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register RTM event listener", e)
        }
    }

    override fun addHandler(handler: IConversationalAIAPIEventHandler) {
        if (!handlers.contains(handler)) {
            handlers.add(handler)
        }
    }

    override fun removeHandler(handler: IConversationalAIAPIEventHandler) {
        handlers.remove(handler)
    }

    override fun subscribeMessage(channelName: String, callback: (Exception?) -> Unit) {
        val options = SubscribeOptions().apply {
            withMessage = true
            withPresence = true
        }

        if (config.enableLog) {
            Log.d(TAG, "Subscribing to RTM channel messages for: $channelName")
        }

        config.rtmClient.subscribe(channelName, options, object : ResultCallback<Void> {
            override fun onSuccess(responseInfo: Void?) {
                if (config.enableLog) {
                    Log.d(TAG, "RTM channel subscription confirmed for $channelName")
                }
                callback(null)
            }

            override fun onFailure(errorInfo: ErrorInfo?) {
                val errorMsg = "RTM subscription failed: ${errorInfo?.errorCode} - ${errorInfo?.errorReason}"
                Log.e(TAG, errorMsg)
                callback(RuntimeException(errorMsg))
            }
        })
    }

    override fun unsubscribeMessage(channelName: String, callback: (Exception?) -> Unit) {
        config.rtmClient.unsubscribe(channelName, object : ResultCallback<Void> {
            override fun onSuccess(responseInfo: Void?) {
                if (config.enableLog) {
                    Log.d(TAG, "Unsubscribed from RTM channel $channelName")
                }
                callback(null)
            }

            override fun onFailure(errorInfo: ErrorInfo?) {
                callback(RuntimeException("Unsubscribe error: ${errorInfo?.errorCode}"))
            }
        })
    }

    override fun destroy() {
        try {
            config.rtmClient.removeEventListener(rtmEventListener)
        } catch (_: Exception) {}
        handlers.clear()
        if (config.enableLog) {
            Log.d(TAG, "ConversationalAIAPIImpl destroyed")
        }
    }

    private fun notifyTranscriptUpdated(agentUserId: String, transcript: Transcript) {
        for (handler in handlers) {
            try {
                handler.onTranscriptUpdated(agentUserId, transcript)
            } catch (e: Exception) {
                Log.e(TAG, "Error invoking handler onTranscriptUpdated", e)
            }
        }
    }

    private fun parseTranscriptPayload(rawJson: String): Transcript {
        return try {
            val json = JSONObject(rawJson)
            val text = json.optString("text", json.optString("words", rawJson))
            val isFinal = json.optBoolean("is_final", true)
            val turnId = json.optLong("turn_id", 0L)
            
            val typeStr = json.optString("type", "user").lowercase()
            val type = when {
                typeStr.contains("agent") || typeStr.contains("bot") -> TranscriptType.AGENT
                typeStr.contains("user") -> TranscriptType.USER
                else -> TranscriptType.UNKNOWN
            }

            val statusStr = json.optString("status", "").lowercase()
            val status = when {
                statusStr.contains("end") || isFinal -> TranscriptStatus.END
                statusStr.contains("interrupt") -> TranscriptStatus.INTERRUPTED
                else -> TranscriptStatus.IN_PROGRESS
            }

            Transcript(
                turnId = turnId,
                text = text,
                isFinal = isFinal,
                type = type,
                status = status
            )
        } catch (_: Exception) {
            // Raw text fallback
            Transcript(
                text = rawJson,
                isFinal = true,
                type = TranscriptType.USER,
                status = TranscriptStatus.END
            )
        }
    }
}
