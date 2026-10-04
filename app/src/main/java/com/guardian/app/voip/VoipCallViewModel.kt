package com.guardian.app.voip

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.guardian.app.AgoraCallState
import com.guardian.app.AgoraEngine
import com.guardian.app.AgoraTranscriptListener
import com.guardian.app.RiskReport
import com.guardian.app.SemanticAnalyzer
import com.guardian.app.bhashini.SpeechConnectionConfig
import com.guardian.app.bhashini.SpeechConnectionStore
import com.guardian.app.bhashini.SpeechStreamPhase
import com.guardian.app.callprotect.CallHistoryEntry
import com.guardian.app.callprotect.CriticalWarningPlayer
import com.guardian.app.callprotect.GuardianDatabase
import com.guardian.app.protect.advanced.TrustedContactAlertSender
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class VoipState(
    val status: String = "Ready to call",
    val isConnected: Boolean = false,
    val isJoining: Boolean = false,
    val peerConnected: Boolean = false,
    val channelName: String = "",
    val durationSeconds: Int = 0,
    val transcripts: List<TranscriptLine> = emptyList(),
    val report: RiskReport = RiskReport(),
    val banner: String? = null,
    val error: String? = null,
    val isMuted: Boolean = false,
    val isSpeakerOn: Boolean = true,
    val isWarningActive: Boolean = false,
    val localPcm: Boolean = false,
    val remotePcm: Boolean = false,
    val localSpeech: SpeechStreamPhase = SpeechStreamPhase.IDLE,
    val remoteSpeech: SpeechStreamPhase = SpeechStreamPhase.IDLE,
    val configuration: SpeechConnectionConfig = SpeechConnectionConfig(),
    val setupLoaded: Boolean = false
)

class VoipCallViewModel(application: Application) : AndroidViewModel(application) {
    private val _state = MutableStateFlow(VoipState())
    val state = _state.asStateFlow()
    private var agoraEngine: AgoraEngine? = null
    private val analyzer = SemanticAnalyzer(application)
    private var warningPlayer: CriticalWarningPlayer? = null
    private val transcriptBuffer = VoipTranscriptBuffer()
    private var dualSttController: DualSttController? = null
    private var frameBridge: AgoraFrameBridge? = null
    private var liveRiskAnalyzer: LiveRiskAnalyzer? = null
    private var transcriptJob: Job? = null
    private var timerJob: Job? = null
    private var joinTimeout: Job? = null
    @Volatile private var generation = 0
    private var warningTriggered = false
    private var trustedAlertTriggered = false
    private var everConnected = false
    private var language = "hi"

    init {
        viewModelScope.launch {
            val config = withContext(Dispatchers.IO) { SpeechConnectionStore.load(application) }
            _state.update { it.copy(configuration = config, setupLoaded = true) }
        }
    }

    fun showError(message: String) { _state.update { it.copy(error = message) } }
    fun saveSetup(config: SpeechConnectionConfig, onSaved: (Boolean) -> Unit) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { SpeechConnectionStore.save(getApplication(), config) } }
            if (result.isSuccess) _state.update { it.copy(configuration = config, error = null, status = "Call setup saved") }
            else showError(result.exceptionOrNull()?.message ?: "Could not save call setup")
            onSaved(result.isSuccess)
        }
    }

    fun startCall(channelName: String = "guardian_secure_call") = joinCall(channelName)
    fun createRoom() = joinCall("gx_${(100000..999999).random()}")
    fun joinRoom(code: String) {
        if (!Regex("[0-9]{6}").matches(code)) { showError("Enter the 6-digit room code shared by the other person."); return }
        joinCall("gx_$code")
    }

    fun joinCall(channelName: String) {
        if (_state.value.isConnected || _state.value.isJoining || !_state.value.setupLoaded) return
        val config = _state.value.configuration
        if (!SpeechConnectionConfig.validUrl(config.backendUrl)) { showError("Add your call server address in Call setup."); return }
        val session = ++generation
        warningTriggered = false
        trustedAlertTriggered = false
        everConnected = false
        transcriptBuffer.clear()
        analyzer.reset()
        language = getApplication<Application>().getSharedPreferences("guardian_prefs", 0).getString("preferred_language", "hi") ?: "hi"
        _state.update { VoipState(status = "Connecting…", isJoining = true, channelName = channelName,
            configuration = config, setupLoaded = true, banner = config.speechError()) }
        try {
            VoipCallService.startService(getApplication(), channelName)
            val engine = AgoraEngine(getApplication(), config.backendUrl)
            agoraEngine = engine
            val rtc = engine.getRtcEngine() ?: error("Voice calling is unavailable on this device.")
            val controller = DualSttController(language = language, configuration = config,
                onStreamState = { speaker, phase, message ->
                    if (session == generation) _state.update {
                        if (speaker == Speaker.LOCAL) it.copy(localSpeech = phase, banner = message ?: it.banner)
                        else it.copy(remoteSpeech = phase, banner = message ?: it.banner)
                    }
                },
                onPcmReceived = { speaker -> if (session == generation) _state.update {
                    if (speaker == Speaker.LOCAL) it.copy(localPcm = true) else it.copy(remotePcm = true)
                } }
            )
            dualSttController = controller
            transcriptJob = viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
                controller.transcripts.collect { line ->
                    if (session == generation) _state.update { it.copy(transcripts = transcriptBuffer.accept(line)) }
                }
            }
            liveRiskAnalyzer = LiveRiskAnalyzer(analyzer, language, onReport = ::onRiskUpdated).also { it.start(controller.transcripts) }
            frameBridge = AgoraFrameBridge(rtc, controller::pushLocal, { _, pcm, rate -> controller.pushRemote(pcm, rate) }).also { it.register() }
            controller.start()
            engine.startCall(channelName = channelName, listener = object : AgoraTranscriptListener {
                override fun onTranscriptReceived(text: String, isFinal: Boolean, speakerUid: Int) {}
                override fun onAudioVolumeChanged(volume: Int) {}
                override fun onPeerConnectionChanged(connected: Boolean) {
                    if (!connected) controller.remoteDisconnected()
                    if (session == generation) _state.update { it.copy(peerConnected = connected,
                        status = if (connected) "In call" else "Waiting for the other person", remotePcm = if (connected) it.remotePcm else false) }
                }
                override fun onCallStateChanged(state: AgoraCallState, message: String) {
                    if (session != generation) return
                    when (state) {
                        AgoraCallState.IN_CALL -> {
                            everConnected = true
                            joinTimeout?.cancel()
                            _state.update { it.copy(status = message, isConnected = true, isJoining = false) }
                            startTimer()
                        }
                        AgoraCallState.CONNECTING -> _state.update { it.copy(status = message, isJoining = true) }
                        AgoraCallState.ERROR -> finishCall(message)
                        AgoraCallState.DISCONNECTED -> finishCall()
                    }
                }
            })
            joinTimeout = viewModelScope.launch { delay(20_000); if (session == generation && !everConnected) finishCall("The room did not connect. Check internet access and Call setup, then retry.") }
        } catch (e: Exception) { finishCall(e.message ?: "Could not start the call. Check microphone permission and retry.") }
        catch (_: LinkageError) { finishCall("The voice library is unavailable on this device.") }
    }

    private fun startTimer() {
        if (timerJob?.isActive == true) return
        timerJob = viewModelScope.launch { while (true) { delay(1000); _state.update { it.copy(durationSeconds = it.durationSeconds + 1) } } }
    }

    private fun onRiskUpdated(report: RiskReport) {
        if (!_state.value.isConnected && !_state.value.isJoining) return
        _state.update { it.copy(report = report) }
        if (!_state.value.isConnected) return
        if (report.riskScore >= 85 && !warningTriggered) {
            warningTriggered = true
            _state.update { it.copy(isWarningActive = true) }
            val player = warningPlayer ?: CriticalWarningPlayer(getApplication()).also { warningPlayer = it }
            player.playBhashiniTts(if (language == "hi") "संभावित धोखाधड़ी का उच्च जोखिम। कॉल समाप्त करें और पहचान की पुष्टि करें।" else "High risk of a possible scam. End the call and verify the person's identity.", language)
        }
        if (report.riskScore >= 75 && !trustedAlertTriggered) {
            trustedAlertTriggered = true
            val session = generation
            val current = _state.value
            viewModelScope.launch {
                val accepted = TrustedContactAlertSender.sendAlert(getApplication(), current.channelName,
                    report.riskScore, report.topSignals.map { it.title }, current.configuration.backendUrl)
                if (session == generation) _state.update { it.copy(banner = if (accepted)
                    "Trusted-contact alert request accepted by the server." else
                    "Trusted-contact alert was not accepted. Check your trusted contact and server configuration.") }
            }
        }
    }

    fun endCall() = finishCall()
    private fun finishCall(message: String? = null) {
        val previous = _state.value
        if (!previous.isConnected && !previous.isJoining) { if (message != null) showError(message); return }
        generation++
        joinTimeout?.cancel(); joinTimeout = null
        timerJob?.cancel(); timerJob = null
        transcriptJob?.cancel(); transcriptJob = null
        try { frameBridge?.unregister() } catch (_: Exception) {}
        frameBridge = null
        dualSttController?.stop(); dualSttController = null
        liveRiskAnalyzer?.stop(); liveRiskAnalyzer = null
        agoraEngine?.destroy(); agoraEngine = null
        VoipCallService.stopService(getApplication())
        warningPlayer?.release(); warningPlayer = null
        _state.update { it.copy(status = if (message == null || everConnected) "Call ended" else "Call did not connect", isConnected = false,
            isJoining = false, peerConnected = false, error = message, isWarningActive = false,
            localSpeech = SpeechStreamPhase.STOPPED, remoteSpeech = SpeechStreamPhase.STOPPED) }
        if (everConnected) viewModelScope.launch(Dispatchers.IO) {
            try {
                GuardianDatabase.getInstance(getApplication()).callHistoryDao().insert(CallHistoryEntry(
                    number = previous.channelName, timestamp = System.currentTimeMillis(), riskScore = previous.report.riskScore,
                    topSignals = previous.report.topSignals.joinToString { it.title },
                    transcriptSummary = previous.transcripts.joinToString("\n") { "${it.speaker}: ${it.text}" },
                    actionTaken = if (previous.report.riskScore >= 85) "CRITICAL_WARN" else "NORMAL"
                ))
            } catch (e: Exception) { Log.w("GuardianCall", "Could not save call history: ${e.javaClass.simpleName}") }
        }
        everConnected = false
    }

    fun toggleMute() {
        if (!_state.value.isConnected) return
        val next = !_state.value.isMuted
        if (agoraEngine?.getRtcEngine()?.muteLocalAudioStream(next) == 0) _state.update { it.copy(isMuted = next) }
    }
    fun toggleSpeaker() {
        if (!_state.value.isConnected) return
        val next = !_state.value.isSpeakerOn
        if (agoraEngine?.getRtcEngine()?.setEnableSpeakerphone(next) == 0) _state.update { it.copy(isSpeakerOn = next) }
    }
    override fun onCleared() {
        finishCall()
        warningPlayer?.release(); warningPlayer = null
        super.onCleared()
    }
}
