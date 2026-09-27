package com.guardian.app

import android.content.Context
import android.util.Log
import com.guardian.app.convoaiApi.ConversationalAIAPIConfig
import com.guardian.app.convoaiApi.ConversationalAIAPIImpl
import com.guardian.app.convoaiApi.IConversationalAIAPI
import com.guardian.app.convoaiApi.IConversationalAIAPIEventHandler
import com.guardian.app.convoaiApi.Transcript
import com.guardian.app.convoaiApi.TranscriptRenderMode
import io.agora.rtc2.Constants
import io.agora.rtc2.IRtcEngineEventHandler
import io.agora.rtc2.RtcEngine
import io.agora.rtc2.RtcEngineConfig
import io.agora.rtm.ErrorInfo
import io.agora.rtm.ResultCallback
import io.agora.rtm.RtmClient
import io.agora.rtm.RtmConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

enum class AgoraCallState {
    DISCONNECTED,
    CONNECTING,
    IN_CALL,
    ERROR
}

interface AgoraTranscriptListener {
    fun onTranscriptReceived(text: String, isFinal: Boolean, speakerUid: Int)
    fun onCallStateChanged(state: AgoraCallState, message: String = "")
    fun onAudioVolumeChanged(volume: Int)
}

class AgoraEngine(
    private val context: Context,
    private val tokenServerBaseUrl: String = "http://10.252.26.51:3001"
) {
    private var rtcEngine: RtcEngine? = null
    private var rtmClient: RtmClient? = null
    private var convoAiApi: IConversationalAIAPI? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    private val _callState = MutableStateFlow(AgoraCallState.DISCONNECTED)
    val callState: StateFlow<AgoraCallState> = _callState.asStateFlow()

    private var activeChannel: String? = null
    private var localUid: Int = 0
    private var activeRtcToken: String = ""
    private var activeRtmToken: String = ""
    private var listener: AgoraTranscriptListener? = null
    private var agentId: String? = null

    private val rtcEventHandler = object : IRtcEngineEventHandler() {
        override fun onJoinChannelSuccess(channel: String?, uid: Int, elapsed: Int) {
            Log.d("GuardianAgora", "Joined Agora RTC Channel: $channel with UID: $uid")
            _callState.value = AgoraCallState.IN_CALL
            listener?.onCallStateChanged(AgoraCallState.IN_CALL, "Connected to voice channel: $channel")
        }

        override fun onUserJoined(uid: Int, elapsed: Int) {
            Log.d("GuardianAgora", "Remote User Joined Voice: $uid")
        }

        override fun onUserOffline(uid: Int, reason: Int) {
            Log.d("GuardianAgora", "Remote User Offline: $uid (reason: $reason)")
        }

        override fun onAudioVolumeIndication(speakers: Array<out AudioVolumeInfo>?, totalVolume: Int) {
            listener?.onAudioVolumeChanged(totalVolume)
        }

        override fun onError(err: Int) {
            Log.e("GuardianAgora", "Agora RTC Error Code: $err")
            if (_callState.value == AgoraCallState.CONNECTING) {
                _callState.value = AgoraCallState.ERROR
                val message = if (err == 110) {
                    "Agora Error 110: Join channel rejected (Check mobile data/Wi-Fi connection)"
                } else {
                    "Agora Error: $err"
                }
                listener?.onCallStateChanged(AgoraCallState.ERROR, message)
            }
        }
    }

    init {
        try {
            initRtcEngine()
        } catch (_: Exception) {}
    }

    private fun initRtcEngine(): RtcEngine? {
        if (rtcEngine != null) return rtcEngine
        try {
            val engine = com.guardian.app.core.AgoraEngineManager.get(context)
            engine.addHandler(rtcEventHandler)
            engine.setChannelProfile(Constants.CHANNEL_PROFILE_COMMUNICATION)
            engine.enableAudio()
            engine.enableAudioVolumeIndication(200, 3, true)
            engine.setAudioProfile(Constants.AUDIO_PROFILE_SPEECH_STANDARD)
            rtcEngine = engine
            Log.d("GuardianAgora", "Agora RtcEngine retrieved from AgoraEngineManager singleton successfully")
        } catch (e: Exception) {
            Log.e("GuardianAgora", "Failed to get Agora RtcEngine from AgoraEngineManager: ${e.message}")
        }
        return rtcEngine
    }

    fun getRtcEngine(): RtcEngine? = rtcEngine ?: initRtcEngine()

    fun startCall(
        channelName: String = "guardian_secure_call",
        uid: Int = (1000..9999).random(),
        listener: AgoraTranscriptListener
    ) {
        this.listener = listener
        this.activeChannel = channelName
        this.localUid = uid

        _callState.value = AgoraCallState.CONNECTING
        listener.onCallStateChanged(AgoraCallState.CONNECTING, "Connecting to secure channel...")

        scope.launch {
            val engine = rtcEngine ?: initRtcEngine()
            if (engine == null) {
                _callState.value = AgoraCallState.ERROR
                listener.onCallStateChanged(AgoraCallState.ERROR, "RTC Engine unavailable")
                return@launch
            }

            try {
                val tokens = fetchRteTokens(channelName, uid)
                activeRtcToken = tokens.optString("rtcToken", "")
                activeRtmToken = tokens.optString("rtmToken", "")

                Log.d("AgoraDebug", "Token has RTM privileges: $activeRtmToken")

                // 1. Join RTC channel for voice audio transmission
                engine.setClientRole(Constants.CLIENT_ROLE_BROADCASTER)
                val tokenToPass = if (activeRtcToken.isBlank()) "" else activeRtcToken
                var joinResult = engine.joinChannel(tokenToPass, channelName, "", uid)
                if (joinResult != Constants.ERR_OK) {
                    Log.w("GuardianAgora", "joinChannel with token failed (code: $joinResult), retrying tokenless join...")
                    joinResult = engine.joinChannel("", channelName, "", uid)
                }
                if (joinResult != Constants.ERR_OK) {
                    Log.e("GuardianAgora", "joinChannel primary and fallback failed with code: $joinResult")
                    _callState.value = AgoraCallState.ERROR
                    listener.onCallStateChanged(AgoraCallState.ERROR, "Join failed (code: $joinResult)")
                    return@launch
                }

                // 2. Initialize RTM & Official ConversationalAIAPIImpl Toolkit
                initializeConvoAi(
                    userId = uid.toString(),
                    rtmToken = activeRtmToken,
                    channelName = channelName,
                    engine = engine
                )

            } catch (e: Exception) {
                Log.e("GuardianAgora", "Start call exception, attempting fallback join: ${e.message}")
                try {
                    engine.setClientRole(Constants.CLIENT_ROLE_BROADCASTER)
                    val joinResult = engine.joinChannel("", channelName, "", uid)
                    if (joinResult != Constants.ERR_OK) {
                        _callState.value = AgoraCallState.ERROR
                        listener.onCallStateChanged(AgoraCallState.ERROR, "Join fallback failed (code: $joinResult)")
                    }
                } catch (fallbackErr: Exception) {
                    _callState.value = AgoraCallState.ERROR
                    listener.onCallStateChanged(AgoraCallState.ERROR, "RTC Engine unavailable: ${fallbackErr.message}")
                }
            }
        }
    }

    private fun initializeConvoAi(
        userId: String,
        rtmToken: String,
        channelName: String,
        engine: RtcEngine
    ) {
        val appId = BuildConfig.AGORA_APP_ID.ifBlank { "d575bd8b35004ad896366419f3a8a8f1" }
        if (appId.isBlank()) {
            Log.e("Guardian", "Cannot init ConvoAI: AGORA_APP_ID is blank")
            return
        }

        try {
            // 1. Initialize RTM Client
            val rtmConfig = RtmConfig.Builder(appId, userId).build()
            val client = RtmClient.create(rtmConfig)
            rtmClient = client

            // 2. Login with RTM token (must succeed before subscribing)
            client.login(rtmToken, object : ResultCallback<Void> {
                override fun onSuccess(response: Void?) {
                    Log.d("Guardian", "RTM login success")

                    // 3. Initialize Official ConversationalAIAPIImpl Toolkit
                    val convoConfig = ConversationalAIAPIConfig(
                        rtcEngine = engine,
                        rtmClient = client,
                        renderMode = TranscriptRenderMode.Word,
                        enableLog = true,
                        enableRenderModeFallback = true
                    )
                    val api = ConversationalAIAPIImpl(convoConfig)
                    convoAiApi = api

                    // 4. Register Transcript Handler
                    api.addHandler(object : IConversationalAIAPIEventHandler {
                        override fun onTranscriptUpdated(agentUserId: String, transcript: Transcript) {
                            Log.d("Guardian", "Transcript: ${transcript.text}")
                            listener?.onTranscriptReceived(
                                transcript.text,
                                transcript.isFinal,
                                agentUserId.toIntOrNull() ?: 0
                            )
                        }
                    })

                    // 5. Subscribe to Channel (MUST be before starting the Agent)
                    api.subscribeMessage(channelName) { error ->
                        if (error != null) {
                            Log.e("Guardian", "RTM subscription failed: ${error.message}")
                        } else {
                            Log.d("Guardian", "RTM subscription confirmed")
                            // 6. Only after subscription succeeds, start the STT Agent
                            scope.launch {
                                startSttTask(channelName, localUid)
                            }
                        }
                    }
                }

                override fun onFailure(errorInfo: ErrorInfo?) {
                    Log.e("Guardian", "RTM login failed: ${errorInfo?.errorCode} ${errorInfo?.errorReason}")
                }
            })
        } catch (e: Exception) {
            Log.e("Guardian", "Failed to initialize Conversational AI Toolkit: ${e.message}", e)
        }
    }

    fun leaveCall() {
        try {
            agentId?.let { id ->
                scope.launch { stopSttTask(id) }
            }
            activeChannel?.let { ch ->
                convoAiApi?.unsubscribeMessage(ch) {}
            }
            convoAiApi?.destroy()
            convoAiApi = null

            rtmClient?.logout(object : ResultCallback<Void> {
                override fun onSuccess(responseInfo: Void?) {}
                override fun onFailure(errorInfo: ErrorInfo?) {}
            })
            rtcEngine?.leaveChannel()
        } catch (_: Exception) {}

        _callState.value = AgoraCallState.DISCONNECTED
        listener?.onCallStateChanged(AgoraCallState.DISCONNECTED, "Call ended")
        activeChannel = null
        agentId = null
    }

    fun destroy() {
        leaveCall()
        try {
            RtmClient.release()
            rtmClient = null
            rtcEngine?.removeHandler(rtcEventHandler)
        } catch (_: Exception) {}
        rtcEngine = null
    }

    private suspend fun fetchRteTokens(channelName: String, uid: Int): JSONObject = withContext(Dispatchers.IO) {
        val urlStr = "$tokenServerBaseUrl/rte/$channelName/$uid"
        val url = URL(urlStr)
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 3000
        conn.readTimeout = 3000
        conn.requestMethod = "GET"

        if (conn.responseCode == HttpURLConnection.HTTP_OK) {
            val responseText = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
            JSONObject(responseText)
        } else {
            throw RuntimeException("Token server returned HTTP ${conn.responseCode}")
        }
    }

    private suspend fun startSttTask(channelName: String, userUid: Int) = withContext(Dispatchers.IO) {
        try {
            val url = URL("$tokenServerBaseUrl/stt/start")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 3000
            conn.readTimeout = 4000
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true

            val body = JSONObject().apply {
                put("channelName", channelName)
                put("userUid", userUid)
            }

            OutputStreamWriter(conn.outputStream).use { it.write(body.toString()); it.flush() }
            if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                val responseText = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
                val json = JSONObject(responseText)
                agentId = json.optString("agent_id", "")
                Log.d("Guardian", "STT Agent started successfully: $agentId")
            }
        } catch (e: Exception) {
            Log.w("Guardian", "STT Task start request skipped: ${e.message}")
        }
    }

    private suspend fun stopSttTask(agentId: String) = withContext(Dispatchers.IO) {
        try {
            val url = URL("$tokenServerBaseUrl/stt/stop")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 2000
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true

            val body = JSONObject().apply { put("agent_id", agentId) }
            OutputStreamWriter(conn.outputStream).use { it.write(body.toString()); it.flush() }
        } catch (_: Exception) {}
    }
}
