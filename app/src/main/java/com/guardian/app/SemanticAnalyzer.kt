package com.guardian.app

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

// ==========================================
// 1. DATA MODELS (Matching Dual-Engine Spec)
// ==========================================

data class PretextReport(
    val detected: Boolean = false,
    val type: String = "none",
    val confidence: Float = 0f,
    val summary: String = ""
)

data class IntentReport(
    val detected: Boolean = false,
    val action: String = "none",
    val confidence: Float = 0f,
    val summary: String = ""
)

data class PressureReport(
    val detected: Boolean = false,
    val tactic: String = "none",
    val confidence: Float = 0f,
    val summary: String = ""
)

data class AsymmetryReport(
    val detected: Boolean = false,
    val evidence: String = ""
)

data class ZeroShotReport(
    val detected: Boolean = false,
    val variantName: String = "",
    val explanation: String = ""
)

data class EngineReports(
    val pretextLegitimacy: PretextReport = PretextReport(),
    val intentRisk: IntentReport = IntentReport(),
    val psychologicalPressure: PressureReport = PressureReport(),
    val informationAsymmetry: AsymmetryReport = AsymmetryReport(),
    val zeroShotVariant: ZeroShotReport = ZeroShotReport()
)

data class SignalBreakdown(
    val title: String,
    val icon: String,
    val detail: String
)

data class IdentityMismatch(
    val detected: Boolean = false,
    val reasons: List<String> = emptyList()
)

data class RiskReport(
    val riskScore: Int = 0,
    val engines: EngineReports = EngineReports(),
    val explanationEn: String = "Monitoring active conversation for deceptive intent.",
    val explanationHi: String = "धोखाधड़ी के इरादे के लिए सक्रिय बातचीत की निगरानी की जा रही है।",
    val highlightedPhrases: List<String> = emptyList(),
    val isOffline: Boolean = false,
    val latencyMs: Long = 0L,
    val source: String = "online",
    val plainReasoning: String = "",
    val plainReasoningHi: String = "",
    val identityMismatch: IdentityMismatch = IdentityMismatch(),
    val syntheticConfidence: Float = 0f,
    val syntheticReasons: List<String> = emptyList()
) {
    val status: RiskStatus
        get() = when {
            riskScore >= 60 -> RiskStatus.High
            riskScore >= 25 -> RiskStatus.Suspicious
            else -> RiskStatus.Low
        }

    val topSignals: List<SignalBreakdown>
        get() {
            val signals = mutableListOf<SignalBreakdown>()
            if (engines.pretextLegitimacy.detected) {
                signals.add(SignalBreakdown("Authority Impersonation", "🎭", engines.pretextLegitimacy.summary))
            }
            if (engines.intentRisk.detected) {
                signals.add(SignalBreakdown("Credential / Fund Extraction", "⚠️", engines.intentRisk.summary))
            }
            if (engines.psychologicalPressure.detected) {
                signals.add(SignalBreakdown("Coercive Urgency / Pressure", "⏰", engines.psychologicalPressure.summary))
            }
            if (engines.zeroShotVariant.detected && engines.zeroShotVariant.variantName.isNotBlank()) {
                signals.add(SignalBreakdown("Pattern: ${engines.zeroShotVariant.variantName}", "🚨", engines.zeroShotVariant.explanation))
            }
            return signals.take(3)
        }
}

// ==========================================
// 2. JSON PARSER & SCHEMA VALIDATOR
// ==========================================

object RiskReportJsonParser {
    fun parse(jsonStr: String, isOffline: Boolean = false, latencyMs: Long = 0L): RiskReport {
        return try {
            // Defensive JSON extraction
            var cleaned = jsonStr.trim()
            if (cleaned.contains("```json")) {
                cleaned = cleaned.substringAfter("```json").substringBefore("```").trim()
            } else if (cleaned.contains("```")) {
                cleaned = cleaned.substringAfter("```").substringBefore("```").trim()
            }

            val startIndex = cleaned.indexOf('{')
            val endIndex = cleaned.lastIndexOf('}')
            if (startIndex != -1 && endIndex != -1 && endIndex > startIndex) {
                cleaned = cleaned.substring(startIndex, endIndex + 1)
            }

            val json = JSONObject(cleaned)
            val rawScore = json.optInt("risk_score", 0)
            val cappedScore = rawScore.coerceIn(0, 98) // Guardrail: capped at 98%

            val enginesObj = json.optJSONObject("engines") ?: JSONObject()

            // 1. Pretext Legitimacy
            val pretextObj = enginesObj.optJSONObject("pretext_legitimacy") ?: JSONObject()
            val pretext = PretextReport(
                detected = pretextObj.optBoolean("detected", false),
                type = pretextObj.optString("type", "none"),
                confidence = pretextObj.optDouble("confidence", 0.0).toFloat(),
                summary = pretextObj.optString("summary", "")
            )

            // 2. Intent Risk
            val intentObj = enginesObj.optJSONObject("intent_risk") ?: JSONObject()
            val intent = IntentReport(
                detected = intentObj.optBoolean("detected", false),
                action = intentObj.optString("action", "none"),
                confidence = intentObj.optDouble("confidence", 0.0).toFloat(),
                summary = intentObj.optString("summary", "")
            )

            // 3. Psychological Pressure
            val pressureObj = enginesObj.optJSONObject("psychological_pressure") ?: JSONObject()
            val pressure = PressureReport(
                detected = pressureObj.optBoolean("detected", false),
                tactic = pressureObj.optString("tactic", "none"),
                confidence = pressureObj.optDouble("confidence", 0.0).toFloat(),
                summary = pressureObj.optString("summary", "")
            )

            // 4. Information Asymmetry
            val asymmetryObj = enginesObj.optJSONObject("information_asymmetry") ?: JSONObject()
            val asymmetry = AsymmetryReport(
                detected = asymmetryObj.optBoolean("detected", false),
                evidence = asymmetryObj.optString("evidence", "")
            )

            // 5. Zero-Shot Variant
            val zeroShotObj = enginesObj.optJSONObject("zero_shot_variant") ?: JSONObject()
            val zeroShot = ZeroShotReport(
                detected = zeroShotObj.optBoolean("detected", false),
                variantName = zeroShotObj.optString("variant_name", ""),
                explanation = zeroShotObj.optString("explanation", "")
            )

            val explanationEn = json.optString("explanation_en", "Analysis in progress.")
            val explanationHi = json.optString("explanation_hi", "विश्लेषण प्रगति पर है।")
            val plainReasoning = json.optString("plain_reasoning", "")
            val plainReasoningHi = json.optString("plain_reasoning_hi", "")

            val phrasesArray = json.optJSONArray("highlighted_phrases") ?: JSONArray()
            val phrases = mutableListOf<String>()
            for (i in 0 until phrasesArray.length()) {
                val phrase = phrasesArray.optString(i, "")
                if (phrase.isNotBlank()) phrases.add(phrase)
            }

            RiskReport(
                riskScore = cappedScore,
                engines = EngineReports(
                    pretextLegitimacy = pretext,
                    intentRisk = intent,
                    psychologicalPressure = pressure,
                    informationAsymmetry = asymmetry,
                    zeroShotVariant = zeroShot
                ),
                explanationEn = explanationEn,
                explanationHi = explanationHi,
                highlightedPhrases = phrases,
                isOffline = isOffline,
                latencyMs = latencyMs,
                source = if (isOffline) "offline" else "gemini",
                plainReasoning = plainReasoning,
                plainReasoningHi = plainReasoningHi
            )
        } catch (_: Exception) {
            KeywordScorer.score(jsonStr).copy(isOffline = isOffline, latencyMs = latencyMs)
        }
    }
}

// ==========================================
// 3. PURE KOTLIN OFFLINE KEYWORD SCORER
// ==========================================

object KeywordScorer {
    private val highRiskWeights = mapOf(
        // Credentials / OTP
        "otp" to 28, "one time password" to 28, "cvv" to 28, "pin" to 22, "password" to 25,
        "verification code" to 24, "ओटीपी" to 28, "पासवर्ड" to 25, "पिन" to 22,
        // Authority Impersonation / Digital Arrest
        "arrest" to 30, "digital arrest" to 32, "police" to 25, "cbi" to 30, "customs" to 26,
        "narcotics" to 28, "crime branch" to 26, "court order" to 24, "fir" to 22,
        "पुलिस" to 25, "सीबीआई" to 30, "डिजिटल अरेस्ट" to 32, "गिरफ्तार" to 30, "जेल" to 25,
        // Financial & Banking
        "transfer" to 25, "send money" to 25, "pay now" to 22, "upi" to 24, "account blocked" to 24,
        "kyc" to 22, "pan card" to 20, "aadhaar" to 20, "खाता बंद" to 24, "केवाईसी" to 22, "पैसे भेजो" to 25,
        // Urgency & Disconnection
        "urgent" to 20, "immediately" to 20, "right now" to 18, "within 10 minutes" to 22,
        "power cut" to 24, "electricity bill" to 24, "bijli cut" to 24, "बिजली कट" to 24, "तुरंत" to 20,
        // Remote Access & Coercion
        "anydesk" to 32, "teamviewer" to 32, "rustdesk" to 30, "screen share" to 28,
        "download apk" to 28, "do not tell" to 22, "stay on the line" to 20, "स्क्रीन शेयर" to 28,
        // Lotteries & Fake Tasks
        "lottery" to 20, "winner" to 18, "refund" to 18, "kbc" to 20, "लॉटरी" to 20
    )

    fun score(transcript: String): RiskReport {
        if (transcript.isBlank()) {
            return RiskReport(
                riskScore = 0,
                explanationEn = "Awaiting speech input to initiate offline analysis.",
                explanationHi = "भाषण इनपुट की प्रतीक्षा की जा रही है।",
                isOffline = true,
                source = "offline"
            )
        }

        val lower = transcript.lowercase(Locale.ROOT)
        var totalScore = 0
        val detectedHits = mutableListOf<String>()

        for ((kw, weight) in highRiskWeights) {
            if (lower.contains(kw)) {
                totalScore += weight
                detectedHits.add(kw)
            }
        }

        val cappedScore = totalScore.coerceIn(0, 98)

        // Identify Cognitive Vectors
        val isPolice = lower.contains("police") || lower.contains("cbi") || lower.contains("arrest") || lower.contains("digital arrest") || lower.contains("पुलिस")
        val isBank = lower.contains("bank") || lower.contains("sbi") || lower.contains("hdfc") || lower.contains("kyc") || lower.contains("खाता")
        val isUtility = lower.contains("electricity") || lower.contains("power cut") || lower.contains("bijli") || lower.contains("बिजली")

        val pretextType = when {
            isPolice -> "police"
            isBank -> "bank"
            isUtility -> "utility"
            else -> if (detectedHits.isNotEmpty()) "unverified_authority" else "none"
        }

        val wantsOtp = lower.contains("otp") || lower.contains("pin") || lower.contains("password") || lower.contains("cvv") || lower.contains("ओटीपी")
        val wantsMoney = lower.contains("transfer") || lower.contains("send money") || lower.contains("pay now") || lower.contains("upi") || lower.contains("पैसे")
        val wantsRemote = lower.contains("anydesk") || lower.contains("teamviewer") || lower.contains("screen share") || lower.contains("download apk")

        val intentAction = when {
            wantsOtp -> "otp"
            wantsMoney -> "transfer"
            wantsRemote -> "remote_access"
            else -> "none"
        }

        val hasFear = lower.contains("arrest") || lower.contains("jail") || lower.contains("fir") || lower.contains("गिरफ्तार")
        val hasUrgency = lower.contains("urgent") || lower.contains("immediately") || lower.contains("right now") || lower.contains("तुरंत")

        val pressureTactic = when {
            hasFear -> "fear"
            hasUrgency -> "urgency"
            else -> "none"
        }

        val zeroShotName = when {
            isPolice && (hasFear || wantsMoney) -> "Digital Arrest Law Enforcement Extortion"
            isBank && wantsOtp -> "Bank Impersonation & 2FA Credential Harvesting"
            isUtility && wantsMoney -> "Electricity Disconnection Panic Scheme"
            wantsRemote -> "Remote Access Trojan / Screen Takeover"
            else -> if (cappedScore > 20) "Social Engineering Pretext" else "None"
        }

        val explanationEn = if (detectedHits.isEmpty()) {
            "No known scam indicators detected in local offline scan."
        } else {
            "Detected high-risk signals: ${detectedHits.distinct().take(4).joinToString(", ")}. Caller is using a $zeroShotName strategy."
        }

        val explanationHi = if (detectedHits.isEmpty()) {
            "स्थानीय ऑफ़लाइन स्कैन में कोई धोखाधड़ी संकेत नहीं मिला।"
        } else {
            "पहचाने गए उच्च-जोखिम संकेत: ${detectedHits.distinct().take(4).joinToString(", ")}। कॉलर $zeroShotName रणनीति का उपयोग कर रहा है।"
        }

        return RiskReport(
            riskScore = cappedScore,
            engines = EngineReports(
                pretextLegitimacy = PretextReport(
                    detected = pretextType != "none",
                    type = pretextType,
                    confidence = if (pretextType != "none") 0.90f else 0f,
                    summary = if (pretextType != "none") "Fabricated $pretextType pretext detected" else "No official pretext claimed"
                ),
                intentRisk = IntentReport(
                    detected = intentAction != "none",
                    action = intentAction,
                    confidence = if (intentAction != "none") 0.92f else 0f,
                    summary = if (intentAction != "none") "High-risk demand for $intentAction" else "No credential or financial demand"
                ),
                psychologicalPressure = PressureReport(
                    detected = pressureTactic != "none",
                    tactic = pressureTactic,
                    confidence = if (pressureTactic != "none") 0.85f else 0f,
                    summary = if (pressureTactic != "none") "Coercive $pressureTactic pressure applied" else "Standard conversational pacing"
                ),
                informationAsymmetry = AsymmetryReport(
                    detected = wantsOtp || (pretextType != "none" && wantsMoney),
                    evidence = if (wantsOtp) "Demanding private secret credentials without judicial authorization" else "None"
                ),
                zeroShotVariant = ZeroShotReport(
                    detected = cappedScore >= 35,
                    variantName = zeroShotName,
                    explanation = "Flagged via offline keyword & cognitive vector scoring."
                )
            ),
            explanationEn = explanationEn,
            explanationHi = explanationHi,
            highlightedPhrases = detectedHits.distinct().take(6),
            isOffline = true,
            latencyMs = 8L,
            source = "offline"
        )
    }
}

// ==========================================
// 4. GEMINI REST API CLIENT (Timeouts & 429 Protected)
// ==========================================

class GeminiApiClient(private val apiKey: String = "") {
    suspend fun analyzeTranscript(systemPrompt: String, transcript: String): String = withContext(Dispatchers.IO) {
        val effectiveKey = if (apiKey.isNotBlank()) apiKey else BuildConfig.GEMINI_API_KEY
        val masked = if (effectiveKey.isNotBlank()) {
            val prefix = effectiveKey.take(4)
            val suffix = effectiveKey.takeLast(4)
            "$prefix...$suffix (len=${effectiveKey.length})"
        } else {
            "[MISSING/EMPTY]"
        }
        Log.d("GuardianAI", "Initiating Gemini API Request with Key: $masked")

        if (effectiveKey.isBlank()) {
            Log.e("GuardianAI", "Gemini API Key is missing! Set GEMINI_API_KEY in local.properties.")
            throw IllegalArgumentException("GEMINI_API_KEY is not configured")
        }

        val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$effectiveKey"
        val url = URL(endpoint)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.connectTimeout = 4000 // 4 seconds connect timeout
        conn.readTimeout = 5000    // 5 seconds read timeout
        conn.doOutput = true

        val requestPayload = JSONObject().apply {
            put("system_instruction", JSONObject().apply {
                put("parts", JSONArray().apply {
                    put(JSONObject().apply { put("text", systemPrompt) })
                })
            })
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply { put("text", "Conversation Transcript to Analyze:\n$transcript") })
                    })
                })
            })
            put("generationConfig", JSONObject().apply {
                put("response_mime_type", "application/json")
                put("temperature", 0.1)
                put("maxOutputTokens", 500)
            })
        }

        OutputStreamWriter(conn.outputStream).use { writer ->
            writer.write(requestPayload.toString())
            writer.flush()
        }

        val responseCode = conn.responseCode
        Log.d("GuardianAI", "Gemini API HTTP Response Code: $responseCode")

        if (responseCode == HttpURLConnection.HTTP_OK) {
            val responseText = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
            Log.d("GuardianAI", "Gemini Response payload received (${responseText.length} bytes)")
            val root = JSONObject(responseText)
            val candidates = root.optJSONArray("candidates")
            val firstCandidate = candidates?.optJSONObject(0)
            val content = firstCandidate?.optJSONObject("content")
            val parts = content?.optJSONArray("parts")
            val text = parts?.optJSONObject(0)?.optString("text").orEmpty()
            text
        } else if (responseCode == 429) {
            Log.w("GuardianAI", "Gemini 429 Rate Limit hit (Quota Exceeded). Falling back to local offline keyword engine.")
            throw RuntimeException("429_RATE_LIMIT")
        } else {
            val errorStream = conn.errorStream?.let { BufferedReader(InputStreamReader(it)).use { r -> r.readText() } }
            Log.e("GuardianAI", "Gemini HTTP $responseCode Error: $errorStream")
            throw RuntimeException("Gemini HTTP $responseCode: $errorStream")
        }
    }
}

// ==========================================
// 5. SEMANTIC ANALYZER (Debounced & Rolling Buffer)
// ==========================================

class SemanticAnalyzer(
    private val context: Context,
    apiKey: String = ""
) {
    private val geminiClient = GeminiApiClient(apiKey)
    private var systemPrompt: String = ""
    private var lastRequestTime = 0L
    private val MIN_INTERVAL_MS = 2000L // 2-second rate-limit guard
    private var lastValidReport = RiskReport()

    init {
        loadSystemPrompt()
        val key = BuildConfig.GEMINI_API_KEY
        val masked = if (key.isNotBlank()) "${key.take(4)}...${key.takeLast(4)}" else "[EMPTY]"
        Log.d("GuardianAI", "SemanticAnalyzer initialized. BuildConfig.GEMINI_API_KEY = $masked")
    }

    private fun loadSystemPrompt() {
        systemPrompt = try {
            context.assets.open("scam_analysis_prompt.txt").bufferedReader().use { it.readText() }
        } catch (_: Exception) {
            "Analyze the conversation transcript and return JSON risk assessment with engines and explanation."
        }
    }

    fun isNetworkAvailable(): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return true
            val activeNetwork = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(activeNetwork) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (_: Exception) {
            true
        }
    }

    fun reset() {
        lastRequestTime = 0L
        lastValidReport = RiskReport()
    }

    suspend fun analyzeChunk(transcript: String): RiskReport = withContext(Dispatchers.Default) {
        val startTime = System.currentTimeMillis()
        val now = System.currentTimeMillis()

        // Keep rolling dialogue window (last ~800 chars / ~3-4 sentences)
        val windowedTranscript = if (transcript.length > 800) {
            transcript.takeLast(800)
        } else {
            transcript
        }

        if (windowedTranscript.isBlank()) {
            val emptyReport = RiskReport(source = "offline")
            lastValidReport = emptyReport
            return@withContext emptyReport
        }

        val effectiveApiKey = BuildConfig.GEMINI_API_KEY
        val hasNetwork = isNetworkAvailable()
        val hasKey = effectiveApiKey.isNotBlank()

        if (!hasKey) {
            Log.w("GuardianAI", "GEMINI_API_KEY is not configured. Using local KeywordScorer.")
            val report = KeywordScorer.score(windowedTranscript)
            val elapsed = System.currentTimeMillis() - startTime
            val finalReport = report.copy(isOffline = true, latencyMs = elapsed, source = "offline_no_key")
            lastValidReport = finalReport
            return@withContext finalReport
        }

        if (!hasNetwork) {
            Log.w("GuardianAI", "No active internet connection. Using local KeywordScorer.")
            val report = KeywordScorer.score(windowedTranscript)
            val elapsed = System.currentTimeMillis() - startTime
            val finalReport = report.copy(isOffline = true, latencyMs = elapsed, source = "offline_no_net")
            lastValidReport = finalReport
            return@withContext finalReport
        }

        if (now - lastRequestTime < MIN_INTERVAL_MS) {
            // Within debounce window: evaluate locally to keep UI ultra-responsive
            val localPreview = KeywordScorer.score(windowedTranscript)
            val elapsed = System.currentTimeMillis() - startTime
            return@withContext localPreview.copy(isOffline = false, latencyMs = elapsed, source = "debounced_preview")
        }

        lastRequestTime = now

        try {
            // Online Gemini 1.5 Flash Analysis
            val rawJson = geminiClient.analyzeTranscript(systemPrompt, windowedTranscript)
            val elapsed = System.currentTimeMillis() - startTime
            val report = RiskReportJsonParser.parse(rawJson, isOffline = false, latencyMs = elapsed)
            val onlineReport = report.copy(isOffline = false, latencyMs = elapsed, source = "gemini")
            lastValidReport = onlineReport
            Log.d("GuardianAI", "Gemini 1.5 Flash Analysis Successful! Score: ${onlineReport.riskScore}% (${elapsed}ms)")
            onlineReport
        } catch (e: Exception) {
            // Fall back to pure Kotlin KeywordScorer immediately
            Log.e("GuardianAI", "Gemini call failed with exception: ${e.javaClass.simpleName} - ${e.message}. Falling back to offline engine.")
            val report = KeywordScorer.score(windowedTranscript)
            val elapsed = System.currentTimeMillis() - startTime
            val offlineReport = report.copy(isOffline = true, latencyMs = elapsed, source = "offline")
            lastValidReport = offlineReport
            offlineReport
        }
    }

    suspend fun analyzeMultilingual(
        transcript: String,
        language: String
    ): RiskReport {
        // 1. Regional keyword match
        val keywordScore = com.guardian.app.protect.RegionalScamKeywords.score(transcript, language)
        val keywordCategories = com.guardian.app.protect.RegionalScamKeywords.categories(transcript, language)

        // 2. Translate to English for Gemini if not English
        val englishText = if (language != "en" && language.isNotBlank()) {
            com.guardian.app.bhashini.BhashiniTranslateClient.translate(transcript, language, "en")
        } else transcript

        // 3. Call Gemini with English text
        val geminiReport = analyzeChunk(englishText)

        // 4. Blend scores: keyword score weights 30%, Gemini weights 70%
        val blendedScore = (keywordScore * 0.3 + geminiReport.riskScore * 0.7).toInt().coerceIn(0, 98)

        return geminiReport.copy(
            riskScore = maxOf(keywordScore, blendedScore)
        )
    }

    suspend fun analyzeNotificationMultilingual(
        source: String,
        title: String,
        body: String,
        userLang: String = "en"
    ): RiskReport {
        val rawMessage = "Notification from $source. Title: $title. Body: $body"
        if (userLang.isBlank() || userLang.equals("en", ignoreCase = true)) {
            return analyzeChunk(rawMessage)
        }
        val translatedTitle = com.guardian.app.bhashini.BhashiniTranslateClient.translate(title, userLang, "en")
        val translatedBody = com.guardian.app.bhashini.BhashiniTranslateClient.translate(body, userLang, "en")
        val englishMessage = "Notification from $source. Title: $translatedTitle. Body: $translatedBody"
        val report = analyzeChunk(englishMessage)
        val localizedExplanation = com.guardian.app.bhashini.BhashiniTranslateClient.translate(report.explanationEn, "en", userLang)
        return report.copy(explanationHi = localizedExplanation)
    }

    suspend fun analyzeUrlMultilingual(
        url: String,
        userLang: String = "en"
    ): RiskReport {
        val report = analyzeChunk("Inspecting URL link: $url")
        if (userLang.isBlank() || userLang.equals("en", ignoreCase = true)) {
            return report
        }
        val localizedExplanation = com.guardian.app.bhashini.BhashiniTranslateClient.translate(report.explanationEn, "en", userLang)
        return report.copy(explanationHi = localizedExplanation)
    }
}
