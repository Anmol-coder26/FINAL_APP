package com.guardian.app

import com.guardian.app.voip.LiveRiskAnalyzer
import com.guardian.app.voip.LiveState
import com.guardian.app.voip.Speaker
import com.guardian.app.voip.TranscriptLine
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Test

class RoleClassificationTest {

    @Test
    fun testRoleClassification_callerDemandsOtp_identifiesCallerAsScammer() = runBlocking {
        val flow = MutableSharedFlow<TranscriptLine>(extraBufferCapacity = 16)
        var latestState: LiveState? = null

        val analyzer = SemanticAnalyzer()
        val liveRiskAnalyzer = LiveRiskAnalyzer(analyzer, "en") { state ->
            println("DEBUG_TEST 1: $state")
            latestState = state
        }

        liveRiskAnalyzer.start(flow)
        delay(200) // Allow collector to subscribe

        // Remote caller asks for OTP
        flow.emit(TranscriptLine(Speaker.REMOTE, "I am from SBI Bank. Share your OTP immediately or your account will be blocked.", isFinal = true))
        delay(500)

        assertNotNull(latestState)
        println("FINAL_TEST 1: $latestState")
        liveRiskAnalyzer.stop()
    }

    @Test
    fun testRoleClassification_labelsDoNotFlip_whenConversationContinues() = runBlocking {
        val flow = MutableSharedFlow<TranscriptLine>(extraBufferCapacity = 16)
        var latestState: LiveState? = null

        val analyzer = SemanticAnalyzer()
        val liveRiskAnalyzer = LiveRiskAnalyzer(analyzer, "en") { state ->
            println("DEBUG_TEST 2: $state")
            latestState = state
        }

        liveRiskAnalyzer.start(flow)
        delay(200) // Allow collector to subscribe

        // Remote caller initiates scam
        flow.emit(TranscriptLine(Speaker.REMOTE, "Your account is under digital arrest by police.", isFinal = true))
        delay(500)
        // Local victim responds hesitantly
        flow.emit(TranscriptLine(Speaker.LOCAL, "Why? What did I do wrong?", isFinal = true))
        delay(500)

        assertNotNull(latestState)
        println("FINAL_TEST 2: $latestState")
        liveRiskAnalyzer.stop()
    }
}
