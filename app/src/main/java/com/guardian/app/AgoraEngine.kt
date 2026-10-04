package com.guardian.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.guardian.app.core.AgoraEngineManager
import io.agora.rtc2.Constants
import io.agora.rtc2.IRtcEngineEventHandler
import io.agora.rtc2.RtcEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

enum class AgoraCallState { DISCONNECTED, CONNECTING, IN_CALL, ERROR }
interface AgoraTranscriptListener {
    fun onTranscriptReceived(text: String, isFinal: Boolean, speakerUid: Int)
    fun onCallStateChanged(state: AgoraCallState, message: String = "")
    fun onAudioVolumeChanged(volume: Int)
    fun onPeerConnectionChanged(connected: Boolean) {}
}

/** RTC carries audio; BHASHINI alone transcribes that audio in the room-call flow. */
class AgoraEngine(private val context: Context, private val tokenServerBaseUrl: String = BuildConfig.BACKEND_URL) {
    private var rtcEngine: RtcEngine? = null
    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private val handler = Handler(Looper.getMainLooper())
    private val _callState = MutableStateFlow(AgoraCallState.DISCONNECTED)
    val callState = _callState.asStateFlow()
    private var activeChannel: String? = null
    private var localUid = 0
    private var listener: AgoraTranscriptListener? = null
    private var joinJob: Job? = null
    private var revision = 0

    private fun notify(state: AgoraCallState, message: String) {
        _callState.value = state
        listener?.onCallStateChanged(state, message)
    }
    private val events = object : IRtcEngineEventHandler() {
        override fun onJoinChannelSuccess(channel: String?, uid: Int, elapsed: Int) {
            handler.post {
                if (activeChannel != channel) return@post
                Log.i("GuardianRTC", "channel_joined uid=$uid elapsed_ms=$elapsed")
                notify(AgoraCallState.IN_CALL, "Connected — waiting for the other person")
            }
        }
        override fun onUserJoined(uid: Int, elapsed: Int) {
            handler.post { if (activeChannel != null) listener?.onPeerConnectionChanged(true) }
            Log.i("GuardianRTC", "peer_joined uid=$uid")
        }
        override fun onUserOffline(uid: Int, reason: Int) {
            handler.post { if (activeChannel != null) listener?.onPeerConnectionChanged(false) }
        }
        override fun onAudioVolumeIndication(speakers: Array<out AudioVolumeInfo>?, totalVolume: Int) {
            handler.post { if (activeChannel != null) listener?.onAudioVolumeChanged(totalVolume) }
        }
        override fun onError(err: Int) {
            handler.post { if (activeChannel != null) notify(AgoraCallState.ERROR, "Call connection failed (Agora code $err). Check Call setup and retry.") }
        }
        override fun onConnectionLost() {
            handler.post { if (activeChannel != null) notify(AgoraCallState.CONNECTING, "Audio connection lost — reconnecting") }
        }
        override fun onTokenPrivilegeWillExpire(token: String?) {
            val channel = activeChannel ?: return
            val expected = revision
            scope.launch {
                try {
                    val data = fetchTokens(channel, localUid)
                    if (expected == revision && activeChannel == channel) rtcEngine?.renewToken(data.optString("rtcToken"))
                } catch (_: Exception) {
                    if (expected == revision) notify(AgoraCallState.ERROR, "The call token could not be renewed. Check the call server and retry.")
                }
            }
        }
    }

    fun getRtcEngine(): RtcEngine? {
        if (rtcEngine != null) return rtcEngine
        try {
            rtcEngine = AgoraEngineManager.get(context).also { engine ->
                engine.addHandler(events)
                engine.setChannelProfile(Constants.CHANNEL_PROFILE_COMMUNICATION)
                engine.enableAudio()
                engine.setAudioProfile(Constants.AUDIO_PROFILE_SPEECH_STANDARD)
                engine.enableAudioVolumeIndication(200, 3, true)
                engine.setEnableSpeakerphone(true)
            }
        } catch (e: Exception) { Log.e("GuardianRTC", "RTC initialization failed: ${e.javaClass.simpleName}") }
        catch (e: LinkageError) { Log.e("GuardianRTC", "RTC native library unavailable: ${e.javaClass.simpleName}") }
        return rtcEngine
    }

    fun startCall(channelName: String = "guardian_secure_call", uid: Int = (10000..Int.MAX_VALUE).random(), listener: AgoraTranscriptListener) {
        this.listener = listener
        activeChannel = channelName
        localUid = uid
        val expected = ++revision
        notify(AgoraCallState.CONNECTING, "Connecting to the call server…")
        joinJob?.cancel()
        joinJob = scope.launch {
            val engine = getRtcEngine()
            if (engine == null) { notify(AgoraCallState.ERROR, "Voice calling is unavailable on this device."); return@launch }
            try {
                val tokens = fetchTokens(channelName, uid)
                if (revision != expected || activeChannel != channelName) return@launch
                val serverAppId = tokens.optString("appId")
                require(serverAppId.isBlank() || serverAppId == BuildConfig.AGORA_APP_ID) { "The call server uses a different Agora app ID" }
                val result = engine.joinChannel(tokens.optString("rtcToken"), channelName, "", uid)
                if (result != 0) notify(AgoraCallState.ERROR, "Could not join the room (Agora code $result). Check Call setup.")
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) {
                if (revision == expected) notify(AgoraCallState.ERROR, "Could not reach the configured call server. Check its address in Call setup and retry.")
            }
        }
    }

    private suspend fun fetchTokens(channel: String, uid: Int): JSONObject = withContext(Dispatchers.IO) {
        val connection = URL("${tokenServerBaseUrl.trimEnd('/')}/rte/$channel/$uid").openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            require(connection.responseCode == 200) { "Call server returned HTTP ${connection.responseCode}" }
            JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
        } finally { connection.disconnect() }
    }

    fun leaveCall() {
        revision++
        activeChannel = null
        joinJob?.cancel()
        joinJob = null
        rtcEngine?.leaveChannel()
        notify(AgoraCallState.DISCONNECTED, "Call ended")
    }
    fun destroy() {
        leaveCall()
        rtcEngine?.removeHandler(events)
        listener = null
        scope.cancel()
        handler.removeCallbacksAndMessages(null)
        rtcEngine = null
    }
}
