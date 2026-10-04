package com.guardian.app

import com.guardian.app.voip.DualSttController
import com.guardian.app.voip.LiveRiskAnalyzer
import com.guardian.app.voip.Speaker
import com.guardian.app.voip.TranscriptLine
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoipPipelineTest {

    @Test
    fun testInstantKeywordScoring_triggersImmediateHighRiskScore() = runTest {
        val flow = MutableSharedFlow<TranscriptLine>(extraBufferCapacity = 16)
        var latestReport: RiskReport? = null

        val analyzer = SemanticAnalyzer(org.mockito.Mockito.mock(android.content.Context::class.java))
        val liveRiskAnalyzer = LiveRiskAnalyzer(analyzer, "en") { report ->
            latestReport = report
        }

        liveRiskAnalyzer.start(flow)

        // Emit instant high risk keyword "otp"
        flow.emit(TranscriptLine(Speaker.REMOTE, "Please share your OTP immediately", isFinal = false))

        // Assert score jumped to >= 60% instantly
        assertTrue(latestReport != null)
        assertEquals(60, latestReport?.riskScore)
        assertTrue(latestReport?.explanationEn?.contains("otp") == true)

        liveRiskAnalyzer.stop()
    }

    @Test
    fun testInstantKeywordScoring_hindiKeyword_triggersImmediateRiskScore() = runTest {
        val flow = MutableSharedFlow<TranscriptLine>(extraBufferCapacity = 16)
        var latestReport: RiskReport? = null

        val analyzer = SemanticAnalyzer(org.mockito.Mockito.mock(android.content.Context::class.java))
        val liveRiskAnalyzer = LiveRiskAnalyzer(analyzer, "hi") { report ->
            latestReport = report
        }

        liveRiskAnalyzer.start(flow)

        // Emit instant Hindi high risk keyword "गिरफ्तार" (arrest)
        flow.emit(TranscriptLine(Speaker.REMOTE, "आपको पुलिस द्वारा गिरफ्तार किया जाएगा", isFinal = false))

        assertTrue(latestReport != null)
        assertEquals(60, latestReport?.riskScore)

        liveRiskAnalyzer.stop()
    }
}
