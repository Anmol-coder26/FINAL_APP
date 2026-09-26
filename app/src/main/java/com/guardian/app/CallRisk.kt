package com.guardian.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import java.util.Locale

enum class RiskStatus(val label: String) {
    Low("LOW RISK"),
    Suspicious("SUSPICIOUS"),
    High("HIGH RISK")
}

data class RiskAssessment(
    val score: Int,
    val status: RiskStatus,
    val reasons: List<String>,
    val detectedTokens: List<String> = emptyList()
)

interface ScamAnalyzer {
    fun analyze(transcript: String): RiskAssessment
}

enum class LanguageMode(val tag: String, val label: String) {
    AUTO("", "Auto / Default"),
    ENGLISH("en-IN", "English (India/Global)"),
    HINDI("hi-IN", "Hindi (हिंदी)")
}

/**
 * Comprehensive multi-lingual scam pattern analyzer for Hindi, Hinglish, and English.
 */
class KeywordScamAnalyzer : ScamAnalyzer {
    private data class Indicator(
        val terms: List<String>,
        val points: Int,
        val reason: String
    )

    private val indicators = listOf(
        // 1. Digital Arrest / Law Enforcement / Police / Customs
        Indicator(
            terms = listOf(
                "digital arrest", "arrest warrant", "cbi", "police", "customs", "narcotics",
                "crime branch", "ed officer", "enforcement directorate", "court order",
                "police station", "fir registered", "illegal parcel", "drugs found",
                "police officer", "thaana", "giraftar", "jail", "digital giraftari",
                "पुलिस", "सीबीआई", "डिजिटल अरेस्ट", "गिरफ्तार", "जेल", "वारंट", "कस्टम्स", "ड्रग्स", "थाना"
            ),
            points = 32,
            reason = "Law enforcement impersonation or fake digital arrest threat"
        ),

        // 2. Secret Codes / OTP / PIN / Credentials
        Indicator(
            terms = listOf(
                "otp", "one time password", "pin", "password", "cvv", "verification code",
                "security code", "secret code", "otp batao", "otp share", "code bhejo",
                "six digit code", "4 digit pin", "ओटीपी", "पासवर्ड", "पिन", "सीवीवी", "कोड बताओ"
            ),
            points = 30,
            reason = "Caller is demanding secret OTP, PIN, or verification credentials"
        ),

        // 3. Bank & KYC Suspension Threat
        Indicator(
            terms = listOf(
                "rbi", "reserve bank", "sbi", "hdfc", "icici", "pnb", "bank manager",
                "kyc update", "kyc expire", "account blocked", "account freeze", "pan link",
                "debit card block", "khata block", "bank se bol raha", "bank verification",
                "खाता बंद", "केवाईसी", "बैंक मैनेजर", "खाता ब्लॉक", "पैन कार्ड"
            ),
            points = 26,
            reason = "Bank or financial institution impersonation / KYC urgency"
        ),

        // 4. Urgent Money / UPI Transfer Demands
        Indicator(
            terms = listOf(
                "send money", "transfer money", "pay now", "upi pin", "wire transfer",
                "paytm karo", "phonepe", "gpay", "scan qr to receive", "refund fees",
                "paise transfer karo", "rupaye bhejo", "turant paise", "paise dalwao",
                "पैसे भेजो", "ट्रांसफर करो", "रुपये भेजो", "यूपीआई"
            ),
            points = 28,
            reason = "Demanding immediate money transfer, payment, or UPI actions"
        ),

        // 5. Urgency, Coercion, and Utility Disconnection
        Indicator(
            terms = listOf(
                "urgent", "immediately", "right now", "within 10 minutes", "within one hour",
                "power cut", "electricity bill", "bijli cut", "connection disconnect",
                "sim block", "legal action", "line cut jayegi", "turant karo",
                "बिजली कट", "तुरंत", "अभी करो", "लाइन कट", "कानूनी कार्रवाई"
            ),
            points = 22,
            reason = "High-pressure urgency or utility disconnection threats"
        ),

        // 6. Remote Screen Sharing & Malware App Installation
        Indicator(
            terms = listOf(
                "anydesk", "teamviewer", "rustdesk", "quicksupport", "screen share",
                "install app", "download apk", "remote access", "share screen",
                "app download karo", "screen share karo", "apk bheja hai",
                "ऐप डाउनलोड", "स्क्रीन शेयर", "रिमोट एक्सेस"
            ),
            points = 28,
            reason = "Requesting remote screen control or suspicious app installation"
        ),

        // 7. Fake Lottery, Job Offers, and Task Rewards
        Indicator(
            terms = listOf(
                "lottery", "winner", "prize", "reward", "cashback", "lucky draw",
                "you have won", "kbc", "crorepati", "youtube like task", "telegram earning",
                "part time job", "daily 5000", "लॉटरी", "इनाम", "टास्क", "करोड़पति"
            ),
            points = 20,
            reason = "Fake lottery, part-time job offer, or reward scheme"
        ),

        // 8. Sensitive Identity Documents
        Indicator(
            terms = listOf(
                "aadhaar", "aadhar", "pan card", "date of birth", "mother name",
                "bank account number", "card number", "आधार कार्ड", "पैन कार्ड नंबर"
            ),
            points = 18,
            reason = "Requesting sensitive identity or document numbers"
        ),

        // 9. Family Emergency / Accident / Police Bail Extortion
        Indicator(
            terms = listOf(
                "accident", "hospital", "icu", "police custody", "bail money", "save your child",
                "son in trouble", "daughter arrested", "beta arrest", "hospital admit", "police pakad",
                "kidnap", "emergency bail", "अस्पताल", "एक्सीडेंट", "जमानत", "बच्चा गिरफ्तार"
            ),
            points = 30,
            reason = "Fake family emergency, road accident, or hostage extortion threat"
        ),

        // 10. Telecom / SIM Disconnection & Illegal Courier Seizure
        Indicator(
            terms = listOf(
                "trai", "telecom department", "sim block", "sim deactivate", "fedex parcel",
                "dhl courier", "customs clearance", "illegal passport", "taiwan parcel",
                "mumbai customs", "delhi customs", "पार्सल", "कस्टम क्लीयरेंस"
            ),
            points = 26,
            reason = "Fake courier parcel seizure or telecom authority impersonation"
        )
    )

    override fun analyze(transcript: String): RiskAssessment {
        if (transcript.isBlank()) {
            return RiskAssessment(
                score = 0,
                status = RiskStatus.Low,
                reasons = emptyList(),
                detectedTokens = emptyList()
            )
        }

        val normalized = transcript.lowercase(Locale.ROOT)
        val matchedIndicators = mutableListOf<Indicator>()
        val detectedWords = mutableListOf<String>()

        for (indicator in indicators) {
            val matchingTerms = indicator.terms.filter { term ->
                normalized.contains(term)
            }
            if (matchingTerms.isNotEmpty()) {
                matchedIndicators.add(indicator)
                detectedWords.addAll(matchingTerms)
            }
        }

        val score = matchedIndicators.sumOf { it.points }.coerceIn(0, 99)
        val status = when {
            score >= 50 -> RiskStatus.High
            score >= 20 -> RiskStatus.Suspicious
            else -> RiskStatus.Low
        }

        return RiskAssessment(
            score = score,
            status = status,
            reasons = matchedIndicators.map { it.reason }.distinct().take(5),
            detectedTokens = detectedWords.distinct().take(10)
        )
    }
}

interface TranscriptListener {
    fun onTranscript(text: String, isFinal: Boolean)
    fun onRmsChanged(rmsdB: Float) {}
    fun onTranscriptionError(message: String)
}

interface StreamingTranscriber {
    fun start(languageMode: LanguageMode, listener: TranscriptListener)
    fun setLanguage(languageMode: LanguageMode)
    fun stop()
}

/**
 * Continuous, self-recovering Speech Recognizer adapter.
 * Handles speakerphone audio streaming without stalling on timeouts or pauses.
 */
class AndroidSpeechTranscriber(private val context: Context) : StreamingTranscriber {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var listener: TranscriptListener? = null
    private var isRunning = false
    private var currentLanguage = LanguageMode.AUTO
    private var restartScheduled = false

    override fun start(languageMode: LanguageMode, listener: TranscriptListener) {
        this.listener = listener
        this.currentLanguage = languageMode
        this.isRunning = true
        this.restartScheduled = false
        initAndListen()
    }

    override fun setLanguage(languageMode: LanguageMode) {
        if (currentLanguage == languageMode) return
        currentLanguage = languageMode
        if (isRunning) {
            startListeningIntent()
        }
    }

    override fun stop() {
        isRunning = false
        restartScheduled = false
        mainHandler.removeCallbacksAndMessages(null)
        try {
            recognizer?.stopListening()
            recognizer?.cancel()
            recognizer?.destroy()
        } catch (_: Exception) {}
        recognizer = null
        listener = null
    }

    private fun initAndListen() {
        mainHandler.post {
            if (!isRunning) return@post
            if (recognizer == null) {
                recognizer = SpeechRecognizer.createSpeechRecognizer(context.applicationContext)
                recognizer?.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        Log.d("GuardianSpeech", "SpeechRecognizer ready for speech (lang=$currentLanguage)")
                    }

                    override fun onBeginningOfSpeech() {
                        Log.d("GuardianSpeech", "SpeechRecognizer detected beginning of speech")
                    }

                    override fun onRmsChanged(rmsdB: Float) {
                        listener?.onRmsChanged(rmsdB)
                    }

                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {
                        Log.d("GuardianSpeech", "SpeechRecognizer end of speech segment")
                    }

                    override fun onError(error: Int) {
                        if (!isRunning) return
                        Log.w("GuardianSpeech", "SpeechRecognizer onError code: $error")
                        scheduleRestart(300L)
                    }

                    override fun onResults(results: Bundle?) {
                        if (!isRunning) return
                        val textList = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val text = textList?.firstOrNull()?.trim().orEmpty()
                        Log.i("GuardianSpeech", "SpeechRecognizer onResults: '$text'")
                        if (text.isNotBlank()) {
                            listener?.onTranscript(text, true)
                        }
                        scheduleRestart(100L)
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        if (!isRunning) return
                        val textList = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val text = textList?.firstOrNull()?.trim().orEmpty()
                        if (text.isNotBlank()) {
                            Log.d("GuardianSpeech", "SpeechRecognizer onPartialResults: '$text'")
                            listener?.onTranscript(text, false)
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }

            startListeningIntent()
        }
    }

    private fun startListeningIntent() {
        mainHandler.post {
            if (!isRunning) return@post
            val activeRecognizer = recognizer ?: return@post
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)

                val langCode = when (currentLanguage) {
                    LanguageMode.HINDI -> "hi-IN"
                    LanguageMode.ENGLISH -> "en-IN"
                    LanguageMode.AUTO -> "en-IN"
                }
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, langCode)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, langCode)
                putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", arrayOf("hi-IN", "en-IN", "en-US"))
            }

            try {
                activeRecognizer.startListening(intent)
            } catch (_: Exception) {
                scheduleRestart(500L)
            }
        }
    }

    private fun scheduleRestart(delayMs: Long) {
        if (isRunning && !restartScheduled) {
            restartScheduled = true
            mainHandler.postDelayed({
                restartScheduled = false
                if (isRunning) {
                    startListeningIntent()
                }
            }, delayMs)
        }
    }
}