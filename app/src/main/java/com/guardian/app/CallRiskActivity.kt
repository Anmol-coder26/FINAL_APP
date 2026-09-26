package com.guardian.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.HeadsetMic
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.guardian.app.protect.advanced.IdentityConsistencyChecker
import com.guardian.app.protect.advanced.PlainReasoningEngine
import com.guardian.app.protect.advanced.TrustedContactAlertSender
import com.guardian.app.protect.advanced.VoiceSynthesisDetector
import com.guardian.app.ui.components.GxButton
import com.guardian.app.ui.components.GxCard
import com.guardian.app.ui.components.GxChip
import com.guardian.app.ui.components.GxChipVariant
import com.guardian.app.ui.components.GxLiveDot
import com.guardian.app.ui.theme.GuardianTheme
import com.guardian.app.ui.theme.GxBorder
import com.guardian.app.ui.theme.GxDanger
import com.guardian.app.ui.theme.GxDangerSoft
import com.guardian.app.ui.theme.GxPrimary
import com.guardian.app.ui.theme.GxSafe
import com.guardian.app.ui.theme.GxShapeLg
import com.guardian.app.ui.theme.GxShapeMd
import com.guardian.app.ui.theme.GxShapePill
import com.guardian.app.ui.theme.GxShapeSm
import com.guardian.app.ui.theme.GxSurface
import com.guardian.app.ui.theme.GxSurfaceAlt
import com.guardian.app.ui.theme.GxTextHi
import com.guardian.app.ui.theme.GxTextLo
import com.guardian.app.ui.theme.GxTextMid
import com.guardian.app.ui.theme.GxTheme
import com.guardian.app.ui.theme.GxType
import com.guardian.app.ui.theme.GxVoid
import com.guardian.app.ui.theme.GxWarning
import com.guardian.app.ui.theme.GxWarningSoft
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class CallRiskActivity : ComponentActivity() {
    private val microphonePermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startDetection() else {
                errorMessage = "Microphone permission is required to analyze call audio."
            }
        }

    private var isDetecting by mutableStateOf(false)
    private var isAnalyzing by mutableStateOf(false)
    private var isAgoraMode by mutableStateOf(false)
    private var transcript by mutableStateOf("")
    private var riskReport by mutableStateOf(RiskReport())
    private var errorMessage by mutableStateOf<String?>(null)
    private var selectedLanguage by mutableStateOf(LanguageMode.AUTO)
    private var audioLevel by mutableFloatStateOf(0f)
    private val conversationHistory = mutableStateListOf<String>()
    private val transcriptBuffer = StringBuilder()

    private var transcriber: StreamingTranscriber? = null
    private var agoraEngine: AgoraEngine? = null
    private lateinit var semanticAnalyzer: SemanticAnalyzer
    private var analysisJob: Job? = null
    private var isDemoModePlaying by mutableStateOf(false)
    private var demoJob: Job? = null
    private var warningPlayer: com.guardian.app.callprotect.CriticalWarningPlayer? = null
    private var currentCallerNumber by mutableStateOf("")
    private var bhashiniPipeline: com.guardian.app.bhashini.GuardianAnalysisPipeline? = null
    private var usingBhashini = false
    private var trustedAlertSentForThisCall = false
    private val pcmBuffer = mutableListOf<Short>()
    private var lastVoiceAnalysisTime = 0L

    @Suppress("DEPRECATION")
    private val callEndListener = object : PhoneStateListener() {
        override fun onCallStateChanged(state: Int, phoneNumber: String?) {
            if (state == TelephonyManager.CALL_STATE_IDLE && isDetecting) {
                resetDemo()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }

        semanticAnalyzer = SemanticAnalyzer(this)
        agoraEngine = AgoraEngine(this)
        warningPlayer = com.guardian.app.callprotect.CriticalWarningPlayer(this)

        currentCallerNumber = intent?.getStringExtra(CallProtectionService.EXTRA_NUMBER).orEmpty()
        if (currentCallerNumber.isNotBlank()) {
            lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                val repo = com.guardian.app.callprotect.NumberReputationRepository(applicationContext)
                val flagged = repo.lookup(currentCallerNumber)
                if (flagged != null && flagged.riskLevel in listOf("HIGH_RISK", "SCAM", "SPAM")) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        riskReport = RiskReport(
                            riskScore = 80,
                            explanationEn = "Caller number is flagged in the reputation database for: ${flagged.reason}",
                            explanationHi = "कॉलर नंबर धोखाधड़ी डेटाबेस में दर्ज है: ${flagged.reason}",
                            engines = EngineReports(
                                pretextLegitimacy = PretextReport(
                                    detected = true,
                                    type = "reputation_blacklist",
                                    confidence = 0.95f,
                                    summary = "Flagged ${flagged.riskLevel}: ${flagged.reason}"
                                )
                            )
                        )
                        checkCallWarning(riskReport)
                    }
                }
            }
        }

        @Suppress("DEPRECATION")
        if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
            val telephony = getSystemService(TelephonyManager::class.java)
            telephony.listen(callEndListener, PhoneStateListener.LISTEN_CALL_STATE)
        }

        // Auto-start speakerphone transcription pipeline
        requestAndStartDetection()

        setContent {
            val agoraCallState by agoraEngine?.callState?.collectAsState() ?: remember { mutableStateOf(AgoraCallState.DISCONNECTED) }

            GuardianTheme {
                CallRiskScreen(
                    isDetecting = isDetecting || isDemoModePlaying || agoraCallState == AgoraCallState.IN_CALL || agoraCallState == AgoraCallState.CONNECTING,
                    isAnalyzing = isAnalyzing,
                    isAgoraMode = isAgoraMode,
                    agoraCallState = agoraCallState,
                    transcript = transcript,
                    conversationHistory = conversationHistory,
                    riskReport = riskReport,
                    errorMessage = errorMessage,
                    selectedLanguage = selectedLanguage,
                    audioLevel = audioLevel,
                    isDemoModePlaying = isDemoModePlaying,
                    callerNumber = currentCallerNumber,
                    onLanguageSelect = { lang ->
                        selectedLanguage = lang
                        transcriber?.setLanguage(lang)
                    },
                    onStartSpeaker = ::requestAndStartDetection,
                    onStartAgoraVoip = ::startAgoraCall,
                    onStop = ::stopDetection,
                    onReset = ::resetDemo,
                    onEndCall = {
                        com.guardian.app.callprotect.CallActionHelper.endCall(this@CallRiskActivity)
                        stopDetection()
                    },
                    onBlockNumber = {
                        val num = currentCallerNumber.ifBlank { "Unknown" }
                        val ok = com.guardian.app.callprotect.CallActionHelper.blockNumber(this@CallRiskActivity, num)
                        if (ok) {
                            android.widget.Toast.makeText(this@CallRiskActivity, "Blocked $num", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    },
                    onSimulateScenario = ::simulateScenario,
                    onPlayFullDemo = ::startFullDemoSimulation
                )
            }
        }
    }

    private fun checkCallWarning(report: RiskReport) {
        if (report.riskScore >= 85) {
            val prefLang = getSelectedLanguageFromPrefs()
            val msg = if (prefLang == "hi" || selectedLanguage == LanguageMode.HINDI) {
                "उच्च जोखिम धोखाधड़ी कॉल! आपकी वित्तीय जानकारी खतरे में हो सकती है। अभी कॉल समाप्त करें।"
            } else {
                "High risk scam call! Your financial information may be in danger. End this call now."
            }
            warningPlayer?.playBhashiniTts(msg, prefLang)
        }
    }

    private suspend fun processAndEnhanceRiskReport(incomingReport: RiskReport, fullContext: String): RiskReport {
        var enhanced = incomingReport

        // 1. Identity Consistency Check if caller number present
        if (currentCallerNumber.isNotBlank()) {
            val db = com.guardian.app.callprotect.GuardianDatabase.getInstance(applicationContext)
            val profile = db.contactProfileDao().lookup(currentCallerNumber)
            if (profile != null) {
                val currentHour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
                val signals: List<String> = enhanced.topSignals.map { "${it.title}: ${it.detail}" } + listOf(fullContext)
                val mismatch = IdentityConsistencyChecker.check(
                    profile = profile,
                    currentHour = currentHour,
                    signalsInCall = signals
                )
                if (mismatch.detected) {
                    enhanced = enhanced.copy(
                        identityMismatch = mismatch,
                        riskScore = maxOf(enhanced.riskScore, 70)
                    )
                }
            }
        }

        // 2. Plain Reasoning Generation if missing
        if (enhanced.plainReasoning.isBlank()) {
            enhanced = enhanced.copy(
                plainReasoning = PlainReasoningEngine.explain(enhanced),
                plainReasoningHi = PlainReasoningEngine.explainInHindi(enhanced)
            )
        }

        // 3. Retain active voice synthesis results if available
        if (riskReport.syntheticConfidence > 0f && enhanced.syntheticConfidence == 0f) {
            enhanced = enhanced.copy(
                syntheticConfidence = riskReport.syntheticConfidence,
                syntheticReasons = riskReport.syntheticReasons
            )
        }

        // 4. Trusted Contact Auto-Alert if risk >= 75
        if (enhanced.riskScore >= 75 && !trustedAlertSentForThisCall) {
            trustedAlertSentForThisCall = true
            val caller = currentCallerNumber.ifBlank { "Unknown Caller" }
            val reasons: List<String> = enhanced.topSignals.map { it.title }.ifEmpty { listOf(enhanced.explanationEn) }
            val backendUrl = BuildConfig.BACKEND_URL
            TrustedContactAlertSender.sendAlert(
                context = applicationContext,
                callerNumber = caller,
                riskScore = enhanced.riskScore,
                topSignals = reasons,
                backendUrl = backendUrl
            )
        }

        return enhanced
    }

    private fun handlePcmChunk(chunk: ShortArray) {
        synchronized(pcmBuffer) {
            for (s in chunk) {
                pcmBuffer.add(s)
            }
            if (pcmBuffer.size > 48000) {
                val excess = pcmBuffer.size - 48000
                pcmBuffer.subList(0, excess).clear()
            }
        }

        val now = System.currentTimeMillis()
        if (now - lastVoiceAnalysisTime >= 2000) {
            lastVoiceAnalysisTime = now
            val snapshot = synchronized(pcmBuffer) { pcmBuffer.toShortArray() }
            if (snapshot.size >= 16000) {
                lifecycleScope.launch(kotlinx.coroutines.Dispatchers.Default) {
                    val result = VoiceSynthesisDetector.analyze(snapshot)
                    if (result.syntheticConfidence > 0.25f) {
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            riskReport = riskReport.copy(
                                syntheticConfidence = result.syntheticConfidence,
                                syntheticReasons = result.reasons
                            )
                        }
                    }
                }
            }
        }
    }

    private fun startAnalysisPipeline() {
        try {
            val prefLang = getSelectedLanguageFromPrefs()
            bhashiniPipeline = com.guardian.app.bhashini.GuardianAnalysisPipeline(
                analyzer = semanticAnalyzer,
                onRiskUpdate = { report ->
                    lifecycleScope.launch {
                        val enhanced = processAndEnhanceRiskReport(report, transcriptBuffer.toString())
                        riskReport = enhanced
                        checkCallWarning(enhanced)
                    }
                },
                onError = { err ->
                    Log.e("Guardian", "Bhashini error: $err")
                    if (isAgoraMode) {
                        runOnUiThread {
                            fallbackToAgora()
                        }
                    }
                },
                onPcmChunk = { pcmChunk ->
                    handlePcmChunk(pcmChunk)
                }
            )
            bhashiniPipeline?.start(language = prefLang)
            usingBhashini = true
            Log.d("Guardian", "Bhashini pipeline started for lang: $prefLang")
        } catch (e: Exception) {
            Log.e("Guardian", "Bhashini init failed: ${e.message}", e)
            if (isAgoraMode) {
                fallbackToAgora()
            }
        }
    }

    private fun fallbackToAgora() {
        if (usingBhashini) {
            bhashiniPipeline?.stop()
            bhashiniPipeline = null
            usingBhashini = false
        }
        startAgoraCall()
    }

    private fun getSelectedLanguageFromPrefs(): String {
        return getSharedPreferences("guardian_prefs", MODE_PRIVATE)
            .getString("preferred_language", "hi") ?: "hi"
    }

    private fun startFullDemoSimulation(scenarioIndex: Int) {
        demoJob?.cancel()
        resetDemo()
        isDemoModePlaying = true

        val scenarioTurns = when (scenarioIndex) {
            1 -> listOf(
                "Good afternoon, this is SBI Card Fraud Prevention unit calling.",
                "An unauthorized overseas debit of ₹45,000 was initiated from Dubai.",
                "To freeze your card and cancel the transfer, please verify the 6-digit OTP code sent to your phone."
            )
            2 -> listOf(
                "Urgent notification: State Electricity Board power management team.",
                "Your residential power supply meter is scheduled for disconnection in 20 minutes due to unpaid dues.",
                "Download the AnyDesk remote screen application and pay ₹2,500 security deposit immediately."
            )
            else -> listOf(
                "I am Inspector Rajesh Kumar from Delhi Cyber Crime Cell HQ.",
                "A seized DHL narcotics parcel with 12 fake passports was found registered under your Aadhaar number.",
                "You are under immediate digital arrest. Transfer ₹50,000 verification bail money immediately to avoid police custody."
            )
        }

        demoJob = lifecycleScope.launch {
            for (turn in scenarioTurns) {
                if (!isDemoModePlaying) break
                Log.i("Guardian", "Transcript received: '$turn' (isFinal=true)")
                transcript = turn
                conversationHistory.add(0, turn)
                transcriptBuffer.append(" ").append(turn)
                isAnalyzing = true
                val raw = semanticAnalyzer.analyzeChunk(transcriptBuffer.toString())
                val enhanced = processAndEnhanceRiskReport(raw, transcriptBuffer.toString())
                riskReport = enhanced
                Log.i("Guardian", "Risk score: ${enhanced.riskScore} (level=${enhanced.status.label}) signals=${enhanced.topSignals.map { it.title }}")
                isAnalyzing = false
                checkCallWarning(riskReport)
                kotlinx.coroutines.delay(2500)
            }
            isDemoModePlaying = false
        }
    }

    private fun requestAndStartDetection() {
        errorMessage = null
        isAgoraMode = false
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            microphonePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        } else {
            startDetection()
        }
    }

    private fun startAgoraCall() {
        errorMessage = null
        isAgoraMode = true
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            microphonePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        } else {
            startDetection()
            agoraEngine?.startCall(
                channelName = "guardian_secure_call",
                listener = object : AgoraTranscriptListener {
                    override fun onTranscriptReceived(text: String, isFinal: Boolean, speakerUid: Int) {
                        handleIncomingTranscript(text, isFinal)
                    }

                    override fun onCallStateChanged(state: AgoraCallState, message: String) {
                        if (state == AgoraCallState.ERROR) {
                            errorMessage = message
                        }
                    }

                    override fun onAudioVolumeChanged(volume: Int) {
                        audioLevel = (volume / 255f).coerceIn(0f, 1f)
                    }
                }
            )
        }
    }

    private fun startDetection() {
        stopDetection()
        transcript = ""
        transcriptBuffer.clear()
        conversationHistory.clear()
        riskReport = RiskReport()
        errorMessage = null
        isDetecting = true
        trustedAlertSentForThisCall = false
        synchronized(pcmBuffer) { pcmBuffer.clear() }

        transcriber = AndroidSpeechTranscriber(this).also { speech ->
            speech.start(selectedLanguage, object : TranscriptListener {
                override fun onTranscript(text: String, isFinal: Boolean) {
                    handleIncomingTranscript(text, isFinal)
                }

                override fun onRmsChanged(rmsdB: Float) {
                    if (!isAgoraMode) {
                        audioLevel = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
                    }
                }

                override fun onTranscriptionError(message: String) {
                    if (!isAgoraMode) {
                        errorMessage = message
                    }
                }
            })
        }
    }

    private fun handleIncomingTranscript(text: String, isFinal: Boolean) {
        Log.i("Guardian", "Transcript received: '$text' (isFinal=$isFinal)")
        transcript = text
        if (isFinal && text.isNotBlank()) {
            conversationHistory.add(0, text)
            transcriptBuffer.append(" ").append(text)
            if (transcriptBuffer.length > 800) {
                transcriptBuffer.delete(0, transcriptBuffer.length - 800)
            }
        }

        val fullContext = if (transcriptBuffer.isNotEmpty()) {
            "$transcriptBuffer $text".trim()
        } else {
            text
        }

        analysisJob?.cancel()
        analysisJob = lifecycleScope.launch {
            isAnalyzing = true
            val raw = semanticAnalyzer.analyzeChunk(fullContext)
            val enhanced = processAndEnhanceRiskReport(raw, fullContext)
            riskReport = enhanced
            Log.i("Guardian", "Risk score: ${enhanced.riskScore} (level=${enhanced.status.label}) signals=${enhanced.topSignals.map { it.title }}")
            isAnalyzing = false
            checkCallWarning(riskReport)
        }
    }

    private fun stopDetection() {
        if (isDetecting || conversationHistory.isNotEmpty()) {
            val finalScore = riskReport.riskScore
            val signals = riskReport.topSignals.joinToString(", ")
            val summary = transcriptBuffer.toString().trim().take(300)
            val num = currentCallerNumber.ifBlank { "Live Speaker Audio" }
            lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                val repo = com.guardian.app.callprotect.NumberReputationRepository(applicationContext)
                repo.saveCallHistory(
                    com.guardian.app.callprotect.CallHistoryEntry(
                        number = num,
                        timestamp = System.currentTimeMillis(),
                        riskScore = finalScore,
                        topSignals = signals,
                        transcriptSummary = summary,
                        actionTaken = if (finalScore >= 85) "flagged_critical" else "analyzed"
                    )
                )
            }
        }

        if (usingBhashini) {
            bhashiniPipeline?.stop()
            bhashiniPipeline = null
            usingBhashini = false
        }

        transcriber?.stop()
        transcriber = null
        agoraEngine?.leaveCall()
        analysisJob?.cancel()
        analysisJob = null
        isDetecting = false
        isAnalyzing = false
        isAgoraMode = false
        audioLevel = 0f
    }

    private fun resetDemo() {
        stopDetection()
        transcript = ""
        transcriptBuffer.clear()
        conversationHistory.clear()
        riskReport = RiskReport()
        errorMessage = null
        trustedAlertSentForThisCall = false
        synchronized(pcmBuffer) { pcmBuffer.clear() }
        semanticAnalyzer.reset()
    }

    private fun simulateScenario(sampleText: String) {
        Log.i("Guardian", "Simulating scenario speech: '$sampleText'")
        Log.i("Guardian", "Transcript received: '$sampleText' (isFinal=true)")
        transcript = sampleText
        conversationHistory.add(0, sampleText)
        transcriptBuffer.append(" ").append(sampleText)
        lifecycleScope.launch {
            isAnalyzing = true
            val raw = semanticAnalyzer.analyzeChunk(transcriptBuffer.toString())
            val enhanced = processAndEnhanceRiskReport(raw, transcriptBuffer.toString())
            riskReport = enhanced
            Log.i("Guardian", "Risk score: ${enhanced.riskScore} (level=${enhanced.status.label}) signals=${enhanced.topSignals.map { it.title }}")
            isAnalyzing = false
            checkCallWarning(riskReport)
        }
    }

    override fun onDestroy() {
        @Suppress("DEPRECATION")
        getSystemService(TelephonyManager::class.java)?.listen(callEndListener, PhoneStateListener.LISTEN_NONE)
        stopDetection()
        warningPlayer?.release()
        agoraEngine?.destroy()
        super.onDestroy()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CallRiskScreen(
    isDetecting: Boolean,
    isAnalyzing: Boolean,
    isAgoraMode: Boolean,
    agoraCallState: AgoraCallState,
    transcript: String,
    conversationHistory: List<String>,
    riskReport: RiskReport,
    errorMessage: String?,
    selectedLanguage: LanguageMode,
    audioLevel: Float,
    isDemoModePlaying: Boolean = false,
    callerNumber: String = "",
    onLanguageSelect: (LanguageMode) -> Unit,
    onStartSpeaker: () -> Unit,
    onStartAgoraVoip: () -> Unit,
    onStop: () -> Unit,
    onReset: () -> Unit,
    onEndCall: () -> Unit = {},
    onBlockNumber: () -> Unit = {},
    onSimulateScenario: (String) -> Unit,
    onPlayFullDemo: (Int) -> Unit = {}
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(GxVoid),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Overlay Header: Caller ID + Live Pulsing Monitor
        item {
            GxCard(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = GxSurface
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            GxLiveDot(pulsing = isDetecting, color = if (isDetecting) GxSafe else GxTextLo)
                            Text(
                                if (isDetecting) "MONITORING ACTIVE" else "MONITORING STANDBY",
                                style = GxType.caption,
                                color = if (isDetecting) GxSafe else GxTextLo,
                                letterSpacing = 1.sp
                            )
                        }

                        GxChip(
                            text = if (isAgoraMode) "⚡ AGORA RTC" else "🎙️ BHASHINI DUAL-AI",
                            variant = GxChipVariant.Brand
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = callerNumber.ifBlank { "Unknown Caller" },
                        style = GxType.headline,
                        color = GxTextHi
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = if (isDetecting) {
                            if (isAgoraMode) "Agora voice stream active • Acoustic audio: ${(audioLevel * 100).toInt()}%"
                            else "Acoustic listener active • Voice activity: ${(audioLevel * 100).toInt()}%"
                        } else "Hold phone on speaker or initiate secured VoIP channel",
                        style = GxType.body,
                        color = GxTextMid
                    )
                }
            }
        }

        // Language Mode Selector
        item {
            GxCard(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = GxSurface
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "ASR DIALECT RECOGNITION",
                        style = GxType.caption,
                        color = GxTextLo,
                        letterSpacing = 1.sp
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LanguageMode.values().forEach { lang ->
                            val isSelected = selectedLanguage == lang
                            GxChip(
                                text = lang.label,
                                variant = if (isSelected) GxChipVariant.Brand else GxChipVariant.Neutral,
                                onClick = { onLanguageSelect(lang) }
                            )
                        }
                    }
                }
            }
        }

        // AI REASONING CARD (Cognitive 2x2 Grid + Risk Ring + Action Block)
        item {
            AIReasoningCard(
                report = riskReport,
                callerNumber = callerNumber,
                onReset = onReset,
                onEndCall = onEndCall,
                onBlockNumber = onBlockNumber
            )
        }

        // High Risk Urgent Banner
        if (riskReport.status == RiskStatus.High) {
            item {
                GxCard(
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = GxDangerSoft,
                    borderColor = GxDanger
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.WarningAmber,
                                contentDescription = null,
                                tint = GxDanger,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                "CRITICAL FRAUD THREAT DETECTED",
                                style = GxType.title,
                                color = GxDanger
                            )
                        }
                        Text(
                            "• HANG UP THE CALL IMMEDIATELY.\n• Do NOT share OTP, PIN, or banking passwords.\n• Law Enforcement & Banks NEVER enforce digital arrest.\n• Do NOT install remote desktop tools (AnyDesk/TeamViewer).",
                            style = GxType.body,
                            color = GxDanger
                        )
                    }
                }
            }
        }

        // Action Buttons (VoIP & Speakerphone)
        item {
            if (isDetecting) {
                GxButton.Danger(
                    text = if (isAgoraMode) "End Agora VoIP Stream" else "Stop Live Listening",
                    icon = Icons.Default.CallEnd,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onStop
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    GxButton.Primary(
                        text = "Agora VoIP",
                        icon = Icons.Default.HeadsetMic,
                        modifier = Modifier.weight(1f),
                        onClick = onStartAgoraVoip
                    )

                    GxButton.Ghost(
                        text = "Speakerphone",
                        icon = Icons.Default.Mic,
                        modifier = Modifier.weight(1f),
                        onClick = onStartSpeaker
                    )
                }
            }
        }

        // Live Utterance Transcript Feed
        item {
            GxCard(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = GxSurface
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "LIVE UTTERANCE STREAM",
                        style = GxType.caption,
                        color = GxTextLo,
                        letterSpacing = 1.sp
                    )

                    Surface(
                        color = GxSurfaceAlt,
                        shape = GxShapeSm,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (transcript.isNotBlank()) "\"$transcript\"" else "Awaiting live acoustic speech...",
                            style = GxType.mono,
                            color = if (transcript.isNotBlank()) GxPrimary else GxTextLo,
                            modifier = Modifier.padding(12.dp)
                        )
                    }

                    if (conversationHistory.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "CONVERSATION LOG:",
                            style = GxType.caption,
                            color = GxTextLo
                        )
                        conversationHistory.take(4).forEach { phrase ->
                            Text(
                                "• $phrase",
                                style = GxType.caption,
                                color = GxTextMid
                            )
                        }
                    }
                }
            }
        }

        // Stage Demo Simulation Triggers
        item {
            GxCard(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = GxSurfaceAlt
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = GxPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            if (isDemoModePlaying) "Stage Demo Playing (Multi-turn)..." else "STAGE DEMO SCENARIOS",
                            style = GxType.title,
                            color = if (isDemoModePlaying) GxDanger else GxTextHi
                        )
                    }

                    Text(
                        "Simulate full conversational fraud dialogues to demonstrate real-time AI classification:",
                        style = GxType.caption,
                        color = GxTextMid
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        GxButton.Danger(
                            text = "🚨 Digital Arrest",
                            modifier = Modifier.weight(1f),
                            onClick = { onPlayFullDemo(0) }
                        )

                        GxButton.Ghost(
                            text = "🏦 SBI KYC",
                            modifier = Modifier.weight(1f),
                            onClick = { onPlayFullDemo(1) }
                        )

                        GxButton.Ghost(
                            text = "⚡ Power Cut",
                            modifier = Modifier.weight(1f),
                            onClick = { onPlayFullDemo(2) }
                        )
                    }
                }
            }
        }
    }
}