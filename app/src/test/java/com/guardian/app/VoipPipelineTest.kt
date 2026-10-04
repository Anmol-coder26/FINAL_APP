package com.guardian.app

import com.guardian.app.voip.LiveRiskAnalyzer
import com.guardian.app.voip.Speaker
import com.guardian.app.voip.TranscriptLine
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock

@OptIn(ExperimentalCoroutinesApi::class)
class VoipPipelineTest {
    private fun instantReport(text: String, language: String): RiskReport = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val flow = MutableSharedFlow<TranscriptLine>(extraBufferCapacity = 16)
        val result = CompletableDeferred<RiskReport>()
        // Interim keyword scoring must not invoke the network-backed semantic analyzer.
        val analyzer = mock(SemanticAnalyzer::class.java)
        val liveRiskAnalyzer = LiveRiskAnalyzer(analyzer, language) { result.complete(it) }
        try {
            liveRiskAnalyzer.start(flow)
            withTimeout(5000) {
                // SharedFlow drops values with no subscriber; wait for the actual collector.
                flow.subscriptionCount.first { it > 0 }
                flow.emit(TranscriptLine(Speaker.REMOTE, text, isFinal = false))
                result.await()
            }
        } finally {
            liveRiskAnalyzer.stop()
            Dispatchers.resetMain()
        }
    }

    @Test fun testInstantKeywordScoring_triggersImmediateHighRiskScore() {
        val report = instantReport("Please share your OTP immediately", "en")
        assertEquals(60, report.riskScore)
        assertTrue(report.explanationEn.contains("otp"))
    }

    @Test fun testInstantKeywordScoring_hindiKeyword_triggersImmediateRiskScore() {
        val report = instantReport("आपको पुलिस द्वारा गिरफ्तार किया जाएगा", "hi")
        assertEquals(60, report.riskScore)
    }
}
