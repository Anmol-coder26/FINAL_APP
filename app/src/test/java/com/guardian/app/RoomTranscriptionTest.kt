package com.guardian.app

import com.guardian.app.bhashini.BhashiniSocketProtocol
import com.guardian.app.voip.AgoraFrameBridge
import com.guardian.app.voip.Pcm16Frames
import com.guardian.app.voip.Speaker
import com.guardian.app.voip.TranscriptLine
import com.guardian.app.voip.VoipTranscriptBuffer
import io.agora.rtc2.Constants
import io.agora.rtc2.RtcEngine
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.*
import java.nio.ByteBuffer
import java.nio.ByteOrder

class RoomTranscriptionTest {
    private fun pcm(vararg values: Int): ByteBuffer = ByteBuffer.allocate(values.size * 2)
        .order(ByteOrder.LITTLE_ENDIAN).apply { values.forEach { putShort(it.toShort()) }; flip() }

    @Test fun pcmTruncatesToAvailableSamplesWithoutZeroPadding() {
        assertArrayEquals(shortArrayOf(20, -40), Pcm16Frames.mono(pcm(20, -40), 1600, 1, 2))
    }
    @Test fun stereoDownmixDoesNotInterleaveSpeakersOrOverflow() {
        assertArrayEquals(shortArrayOf(30000, 0, -30000),
            Pcm16Frames.mono(pcm(30000, 30000, -1000, 1000, -30000, -30000), 3, 2, 2))
    }
    @Test fun pcmCopyPreservesSdkBufferPositionAndLifetime() {
        val buffer = pcm(999, 12, 24).apply { position(2) }
        val copied = Pcm16Frames.mono(buffer, 2, 1, 2)
        assertEquals(2, buffer.position())
        buffer.putShort(2, 0)
        assertArrayEquals(shortArrayOf(12, 24), copied)
    }
    @Test fun incompleteStereoFrameIsDiscarded() {
        assertArrayEquals(shortArrayOf(20), Pcm16Frames.mono(pcm(10, 30, 90), 2, 2, 2))
    }
    @Test fun unsupportedAudioFormatIsRejected() {
        assertTrue(Pcm16Frames.mono(pcm(1), 1, 1, 1).isEmpty())
        assertTrue(Pcm16Frames.mono(null, 1, 1, 2).isEmpty())
    }
    @Test fun bridgeRegistersRecordAndBeforeMixingPositions() {
        val engine = mock(RtcEngine::class.java)
        val bridge = AgoraFrameBridge(engine, { _, _ -> }, { _, _, _ -> })
        bridge.register()
        assertEquals(0x000a, bridge.getObservedAudioFramePosition())
        verify(engine).setRecordingAudioFrameParameters(16000, 1, Constants.RAW_AUDIO_FRAME_OP_MODE_READ_ONLY, 1600)
        verify(engine).setPlaybackAudioFrameBeforeMixingParameters(16000, 1)
        verify(engine).registerAudioFrameObserver(bridge)
    }
    @Test fun bridgeDeliversDistinctLocalAndRemoteCopiedFrames() {
        var local = shortArrayOf()
        var remote = shortArrayOf()
        var remoteUid = 0
        val bridge = AgoraFrameBridge(mock(RtcEngine::class.java), { samples, rate ->
            local = samples; assertEquals(16000, rate)
        }, { uid, samples, rate -> remoteUid = uid; remote = samples; assertEquals(16000, rate) })
        bridge.onRecordAudioFrame("gx_123456", 0, 2, 2, 1, 16000, pcm(10, 20), 0, 0)
        bridge.onPlaybackAudioFrameBeforeMixing("gx_123456", 77, 0, 2, 2, 1, 16000, pcm(30, 40), 0, 0, 0)
        assertArrayEquals(shortArrayOf(10, 20), local)
        assertArrayEquals(shortArrayOf(30, 40), remote)
        assertEquals(77, remoteUid)
    }
    @Test fun finalReplacesPartialEvenWhenOtherSpeakerInterleaves() {
        val buffer = VoipTranscriptBuffer()
        buffer.accept(TranscriptLine(Speaker.LOCAL, "Please", false, 100))
        buffer.accept(TranscriptLine(Speaker.REMOTE, "Hello", false, 200))
        buffer.accept(TranscriptLine(Speaker.LOCAL, "Please wait", false, 300))
        val lines = buffer.accept(TranscriptLine(Speaker.LOCAL, "Please wait here", true, 400))
        assertEquals(2, lines.size)
        assertEquals("Please wait here", lines[0].text)
        assertEquals(100L, lines[0].timestamp)
        assertTrue(lines[0].isFinal)
        assertEquals("Hello", lines[1].text)
    }
    @Test fun nextUtteranceKeepsFinalHistoryAndBoundedMemory() {
        val buffer = VoipTranscriptBuffer(3)
        repeat(6) { buffer.accept(TranscriptLine(Speaker.REMOTE, "Sentence $it", true)) }
        val lines = buffer.accept(TranscriptLine(Speaker.LOCAL, "Next", false))
        assertEquals(3, lines.size)
        assertEquals("Sentence 4", lines.first().text)
        assertTrue(buffer.context().contains("You: Next"))
        assertTrue(buffer.context(10).length <= 10)
        buffer.clear()
        assertEquals("", buffer.context())
    }
    @Test fun documentedTaskUsesServiceIdAndMonoPcmSampleRate() {
        val task = BhashiniSocketProtocol.task("hi-IN", "provisioned_service").getJSONObject(0)
        assertEquals("asr", task.getString("taskType"))
        val config = task.getJSONObject("config")
        assertEquals("provisioned_service", config.getString("serviceId"))
        assertEquals(8000, config.getInt("samplingRate"))
        assertEquals("hi", config.getJSONObject("language").getString("sourceLanguage"))
        assertEquals(1.0, BhashiniSocketProtocol.streamingConfig().getDouble("responseFrequencyInSecs"), 0.0)
    }
    @Test fun audioKeepsBinaryPayloadForSocketIoAttachment() {
        val bytes = byteArrayOf(1, 2, 3, 4)
        val actual = BhashiniSocketProtocol.audio(bytes).getJSONArray("audio").getJSONObject(0).get("audioContent")
        assertSame(bytes, actual)
    }
    @Test fun providerPartialAndFinalFlagsArePreserved() {
        val payload = JSONObject("""{"pipelineResponse":[{"taskType":"asr","output":[{"source":"  live words  "}]}]}""")
        assertEquals(listOf("live words" to false), BhashiniSocketProtocol.transcripts(payload))
        assertEquals(listOf("live words" to true), BhashiniSocketProtocol.transcripts(payload, true))
        assertEquals(listOf("finished" to true), BhashiniSocketProtocol.transcripts("""{"text":"finished","is_final":true}"""))
    }
    @Test fun nonSpeechAndMalformedPacketsDoNotBecomeTranscripts() {
        assertTrue(BhashiniSocketProtocol.transcripts("not json").isEmpty())
        assertTrue(BhashiniSocketProtocol.transcripts(JSONObject("""{"pipelineResponse":[{"taskType":"translation","output":[{"source":"ignore"}]}]}""")).isEmpty())
    }
}
