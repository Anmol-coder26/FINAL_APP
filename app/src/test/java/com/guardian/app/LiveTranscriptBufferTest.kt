package com.guardian.app

import org.junit.Assert.*
import org.junit.Test

class LiveTranscriptBufferTest {
    @Test fun partialsReplaceWithoutDuplicatingFinalContext() {
        val buffer = LiveTranscriptBuffer()
        buffer.accept("please", false)
        buffer.accept("please share", false)
        assertEquals("please share", buffer.interim)
        assertTrue(buffer.history.isEmpty())
        buffer.accept("please share your OTP", true)
        assertEquals("please share your OTP", buffer.context)
        assertEquals("", buffer.interim)
        assertEquals(1, buffer.history.size)
        buffer.accept("  ", true)
        assertEquals(1, buffer.history.size)
    }
    @Test fun historyAndContextAreBoundedAndRepeatedSpeechIsRetained() {
        val buffer = LiveTranscriptBuffer(maxChars = 10, maxSegments = 2)
        buffer.accept("first", true)
        buffer.accept("again", true)
        buffer.accept("again", true)
        assertEquals(listOf("again", "again"), buffer.history)
        assertTrue(buffer.context.length <= 10)
        buffer.clear()
        assertEquals("", buffer.context)
        assertTrue(buffer.history.isEmpty())
    }
}
