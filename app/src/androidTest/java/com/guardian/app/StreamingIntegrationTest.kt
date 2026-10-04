package com.guardian.app

import android.Manifest
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.guardian.app.bhashini.SpeechConnectionConfig
import com.guardian.app.bhashini.SpeechConnectionStore
import com.guardian.app.bhashini.SpeechStreamPhase
import com.guardian.app.voip.DualSttController
import com.guardian.app.voip.Speaker
import com.guardian.app.voip.TranscriptLine
import com.guardian.app.voip.VoipTranscriptBuffer
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

/** Exercises the production Socket.IO clients with explicitly synthetic test responses. */
@RunWith(AndroidJUnit4::class)
class StreamingIntegrationTest {
    @get:Rule val micPermission: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.RECORD_AUDIO)
    private fun config(service: String) = SpeechConnectionConfig(
        "http://10.0.2.2:3901", "fixture-only", service, "http://10.0.2.2:3901")

    @Test fun twoPushSessionsDeliverSeparatePartialAndFinalTranscripts() = runBlocking {
        val ready = ConcurrentHashMap.newKeySet<Speaker>()
        val errors = ConcurrentLinkedQueue<String>()
        val lines = Channel<TranscriptLine>(Channel.UNLIMITED)
        val controller = DualSttController(configuration = config("fixture_push"),
            onStreamState = { speaker, phase, message ->
                if (phase == SpeechStreamPhase.READY) ready.add(speaker)
                if (phase == SpeechStreamPhase.ERROR) errors.add(message ?: "speech error")
            })
        val collector = launch(start = CoroutineStart.UNDISPATCHED) { controller.transcripts.collect { lines.send(it) } }
        try {
            controller.start()
            withTimeout(15_000) { while (ready.size != 2 && errors.isEmpty()) delay(25) }
            assertTrue("Speech sessions failed: $errors", errors.isEmpty())
            controller.pushLocal(ShortArray(3200) { 1111 }, 16000)
            controller.pushRemote(ShortArray(3200) { 2222 }, 16000)
            val received = withTimeout(10_000) { List(4) { lines.receive() } }
            assertEquals(2, received.count { it.isFinal })
            assertTrue(received.filter { it.speaker == Speaker.LOCAL }.all { it.text.startsWith("LOCAL TEST FIXTURE") })
            assertTrue(received.filter { it.speaker == Speaker.REMOTE }.all { it.text.startsWith("REMOTE TEST FIXTURE") })
            val buffer = VoipTranscriptBuffer()
            var displayed = emptyList<TranscriptLine>()
            received.forEach { displayed = buffer.accept(it) }
            assertEquals(2, displayed.size)
            assertTrue(displayed.all { it.isFinal && it.text.endsWith("completed") })
            controller.stop()
            controller.pushLocal(ShortArray(3200) { 1111 }, 16000)
            delay(200)
            assertTrue("A stopped session emitted another transcript", lines.tryReceive().isFailure)
        } finally { controller.stop(); collector.cancel(); lines.close() }
    }

    @Test fun microphonePcmTravelsThroughProductionPipelineAndUpdatesTheScreen() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val original = SpeechConnectionStore.load(context)
        val device = UiDevice.getInstance(instrumentation)
        try {
            SpeechConnectionStore.save(context, config("fixture_microphone"))
            ActivityScenario.launch(CallRiskActivity::class.java).use {
                assertTrue(device.wait(Until.hasObject(By.text("Start live transcription")), 10_000))
                device.findObject(By.text("Start live transcription")).click()
                assertTrue("No synthetic fixture transcript reached the UI from recorded PCM",
                    device.wait(Until.hasObject(By.textContains("MICROPHONE TEST FIXTURE completed")), 15_000))
                assertTrue(device.wait(Until.hasObject(By.text("Stop live listening")), 5_000))
                device.findObject(By.text("Stop live listening")).click()
                assertTrue(device.wait(Until.hasObject(By.text("Start live transcription")), 5_000))
            }
        } finally { SpeechConnectionStore.save(context, original) }
    }
}
