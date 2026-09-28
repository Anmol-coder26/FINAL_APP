package com.guardian.app.voip

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.guardian.app.AgoraCallState
import com.guardian.app.AgoraEngine
import com.guardian.app.AgoraTranscriptListener
import com.guardian.app.BuildConfig
import com.guardian.app.RiskReport
import com.guardian.app.SemanticAnalyzer
import com.guardian.app.callprotect.CallHistoryEntry
import com.guardian.app.callprotect.CriticalWarningPlayer
import com.guardian.app.callprotect.GuardianDatabase
import com.guardian.app.protect.advanced.TrustedContactAlertSender
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

data class VoipState(
    val status: String = "Idle",
    val isConnected: Boolean = false,
    val channelName: String = "",
    val durationSeconds: Int = 0,
    val transcripts: List<TranscriptLine> = emptyList(),
    val report: RiskReport = RiskReport(),
    val banner: String? = null,
    val isMuted: Boolean = false,
    val isSpeakerOn: Boolean = true,
    val isWarningActive: Boolean = false,
    val victimRole: String = "Unknown",
    val scammerRole: String = "Unknown",
    val roleConfidence: Float = 0f,
    val roleReasoning: String = ""
)

class VoipCallViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(VoipState())
    val state: StateFlow<VoipState> = _state.asStateFlow()

    private val agoraEngine = AgoraEngine(application)
    private val analyzer = SemanticAnalyzer(application)
    private val warningPlayer = CriticalWarningPlayer(application)
    private var dualSttController: DualSttController? = null
    private var frameBridge: AgoraFrameBridge? = null
    private var liveRiskAnalyzer: LiveRiskAnalyzer? = null

    private var timerJob: Job? = null
    private var warningTriggered = false
    private var trustedContactAlertTriggered = false
    private var familyAlertSent = false
    private var language: String = "hi"

    private fun getSelectedLanguageFromPrefs(): String {
        val prefs = getApplication<Application>().getSharedPreferences("guardian_prefs", android.content.Context.MODE_PRIVATE)
        return prefs.getString("preferred_language", "hi") ?: "hi"
    }

    fun startCall(channelName: String = "guardian_secure_call") {
        joinCall(channelName)
    }

    fun joinCall(channelName: String) {
        _state.update {
            it.copy(
                status = "Connecting to $channelName...",
                channelName = channelName,
                isConnected = false,
                transcripts = emptyList(),
                report = RiskReport(),
                durationSeconds = 0,
                banner = null
            )
        }
        warningTriggered = false
        trustedContactAlertTriggered = false
        familyAlertSent = false

        startDurationTimer()
        startBhashiniPipeline()

        // 1. Join Agora RTC Call
        agoraEngine.startCall(
            channelName = channelName,
            listener = object : AgoraTranscriptListener {
                override fun onTranscriptReceived(text: String, isFinal: Boolean, speakerUid: Int) {
                    if (_state.value.banner == "Agora fallback active") {
                        val speaker = if (speakerUid == 0 || speakerUid == 998) Speaker.LOCAL else Speaker.REMOTE
                        handleNewTranscript(TranscriptLine(speaker, text, isFinal))
                    }
                }

                override fun onCallStateChanged(state: AgoraCallState, message: String) {
                    when (state) {
                        AgoraCallState.IN_CALL -> {
                            _state.update { it.copy(status = "In Secure Call", isConnected = true) }
                        }
                        AgoraCallState.CONNECTING -> {
                            _state.update { it.copy(status = "Connecting...", isConnected = false) }
                        }
                        AgoraCallState.DISCONNECTED -> {
                            _state.update { it.copy(status = "Call Ended", isConnected = false) }
                        }
                        AgoraCallState.ERROR -> {
                            _state.update { it.copy(status = "Connection Error: $message", isConnected = false) }
                        }
                    }
                }

                override fun onAudioVolumeChanged(volume: Int) {}
            }
        )
    }

    private fun startBhashiniPipeline() {
        language = getSelectedLanguageFromPrefs()
        try {
            dualSttController = DualSttController(context = getApplication(), language = language).also { controller ->
                controller.start()

                // Collect transcripts
                viewModelScope.launch {
                    controller.transcripts.collect { line ->
                        handleNewTranscript(line)
                    }
                }
            }

            val rtc = agoraEngine.getRtcEngine()
            if (rtc != null) {
                frameBridge = AgoraFrameBridge(
                    rtcEngine = rtc,
                    onLocalPcm = { samples, rate ->
                        dualSttController?.pushLocal(samples, rate)
                    },
                    onRemotePcm = { _, samples, rate ->
                        dualSttController?.pushRemote(samples, rate)
                    }
                ).also { it.register() }
            }

            liveRiskAnalyzer = LiveRiskAnalyzer(analyzer, language) { liveState ->
                _state.update { current ->
                    current.copy(
                        report = current.report.copy(
                            riskScore = maxOf(current.report.riskScore, liveState.riskScore),
                            explanationEn = if (liveState.roleReasoning.isNotBlank()) liveState.roleReasoning else current.report.explanationEn
                        ),
                        victimRole = liveState.victimRole,
                        scammerRole = liveState.scammerRole,
                        roleConfidence = liveState.roleConfidence,
                        roleReasoning = liveState.roleReasoning
                    )
                }
                onRiskReportUpdated(RiskReport(riskScore = liveState.riskScore, explanationEn = liveState.roleReasoning))
            }.also {
                dualSttController?.let { c -> it.start(c.transcripts) }
            }

        } catch (e: Exception) {
            Log.e("GuardianVoip", "Bhashini failed, fallback to Agora", e)
            _state.update { it.copy(banner = "Agora fallback active") }
        }
    }

    private fun handleNewTranscript(line: TranscriptLine) {
        _state.update { current ->
            val updated = current.transcripts.toMutableList()
            if (line.isFinal) {
                updated.add(line)
            } else {
                val lastIdx = updated.indexOfLast { it.speaker == line.speaker && !it.isFinal }
                if (lastIdx != -1) {
                    updated[lastIdx] = line
                } else {
                    updated.add(line)
                }
            }
            current.copy(transcripts = updated)
        }
    }

    private fun onRiskReportUpdated(report: RiskReport) {
        _state.update { it.copy(report = report) }

        // Bhashini TTS Warning at >= 85%
        if (report.riskScore >= 85 && !warningTriggered) {
            warningTriggered = true
            _state.update { it.copy(isWarningActive = true) }
            val warningText = if (language == "hi") {
                "उच्च जोखिम धोखाधड़ी कॉल! आपकी वित्तीय जानकारी खतरे में हो सकती है। अभी कॉल समाप्त करें।"
            } else {
                "High risk scam call! Your financial information may be in danger. End this call now."
            }
            warningPlayer.playBhashiniTts(warningText, language)
        }

        // Trusted contact alert at >= 75%
        if (report.riskScore >= 75 && !trustedContactAlertTriggered) {
            trustedContactAlertTriggered = true
            viewModelScope.launch {
                val topSignals = report.topSignals.map { it.title }
                TrustedContactAlertSender.sendAlert(
                    context = getApplication(),
                    callerNumber = _state.value.channelName.ifBlank { "VoIP Caller" },
                    riskScore = report.riskScore,
                    topSignals = topSignals,
                    backendUrl = BuildConfig.BACKEND_URL
                )
            }
        }

        // Family FCM alert trigger at >= 75%
        if (report.riskScore >= 75 && !familyAlertSent) {
            familyAlertSent = true
            viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val payload = org.json.JSONObject().apply {
                        put("protectedUserId", "protected_user_1")
                        put("familyUserIds", org.json.JSONArray().apply { put("family_member_1") })
                        put("protectedUserName", "Protected Member")
                        put("riskScore", report.riskScore)
                        put("scamType", report.topSignals.firstOrNull()?.title ?: "Impersonation Risk")
                        put("callerNumber", _state.value.channelName.ifBlank { "VoIP Caller" })
                        put("transcriptSummary", report.explanationEn)
                        put("alertType", "scam_call")
                    }

                    val body = payload.toString().toRequestBody("application/json".toMediaType())
                    val request = okhttp3.Request.Builder()
                        .url("${BuildConfig.BACKEND_URL}/alerts/family")
                        .post(body)
                        .build()

                    okhttp3.OkHttpClient().newCall(request).execute()
                    android.util.Log.d("GuardianFCM", "Family FCM alert sent successfully")
                } catch (e: Exception) {
                    android.util.Log.e("GuardianFCM", "Family FCM alert failed", e)
                }
            }
        }
    }

    fun endCall() {
        stopDurationTimer()
        frameBridge?.unregister()
        frameBridge = null

        dualSttController?.stop()
        dualSttController = null

        liveRiskAnalyzer?.stop()
        liveRiskAnalyzer = null

        agoraEngine.leaveCall()

        // Persist call history
        val currentState = _state.value
        viewModelScope.launch {
            try {
                val summaryText = currentState.transcripts.joinToString("\n") {
                    "${if (it.speaker == Speaker.LOCAL) "You" else "Caller"}: ${it.text}"
                }
                val topSignals = currentState.report.topSignals.joinToString(", ") { it.title }
                val entry = CallHistoryEntry(
                    number = currentState.channelName.ifBlank { "Secure VoIP Call" },
                    timestamp = System.currentTimeMillis(),
                    riskScore = currentState.report.riskScore,
                    topSignals = topSignals,
                    transcriptSummary = summaryText,
                    actionTaken = if (currentState.report.riskScore >= 85) "CRITICAL_WARN" else "NORMAL"
                )
                GuardianDatabase.getInstance(getApplication()).callHistoryDao().insert(entry)
            } catch (e: Exception) {
                Log.e("GuardianVoip", "Failed to save call history: ${e.message}")
            }
        }

        _state.update { it.copy(status = "Call Ended", isConnected = false) }
    }

    fun toggleMute() {
        val nextMute = !_state.value.isMuted
        agoraEngine.getRtcEngine()?.muteLocalAudioStream(nextMute)
        _state.update { it.copy(isMuted = nextMute) }
    }

    fun toggleSpeaker() {
        val nextSpeaker = !_state.value.isSpeakerOn
        agoraEngine.getRtcEngine()?.setEnableSpeakerphone(nextSpeaker)
        _state.update { it.copy(isSpeakerOn = nextSpeaker) }
    }

    private fun startDurationTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                _state.update { it.copy(durationSeconds = it.durationSeconds + 1) }
            }
        }
    }

    private fun stopDurationTimer() {
        timerJob?.cancel()
        timerJob = null
    }

    override fun onCleared() {
        super.onCleared()
        endCall()
        warningPlayer.release()
    }
}
