package com.guardian.app.voip

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Copy SDK-owned PCM before its callback returns; downmix interleaved stereo. */
object Pcm16Frames {
    fun mono(buffer: ByteBuffer?, samplesPerChannel: Int, channels: Int, bytesPerSample: Int): ShortArray {
        if (buffer == null || samplesPerChannel <= 0 || channels !in 1..2 || bytesPerSample != 2) return shortArrayOf()
        val input = buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        val frames = minOf(samplesPerChannel, input.remaining() / channels)
        return ShortArray(frames) {
            var sum = 0
            repeat(channels) { sum += input.get().toInt() }
            (sum / channels).toShort()
        }
    }
}
