package com.guardian.app.voip

import io.agora.rtc2.Constants
import io.agora.rtc2.IAudioFrameObserver
import io.agora.rtc2.RtcEngine
import io.agora.rtc2.audio.AudioParams
import java.nio.ByteBuffer
import java.nio.ByteOrder

class AgoraFrameBridge(
    private val rtcEngine: RtcEngine,
    private val onLocalPcm: (ShortArray, Int) -> Unit,
    private val onRemotePcm: (uid: Int, ShortArray, Int) -> Unit
) : IAudioFrameObserver {

    fun register() {
        rtcEngine.registerAudioFrameObserver(this)
        rtcEngine.setPlaybackAudioFrameParameters(16000, 1, Constants.RAW_AUDIO_FRAME_OP_MODE_READ_ONLY, 1600)
        rtcEngine.setRecordingAudioFrameParameters(16000, 1, Constants.RAW_AUDIO_FRAME_OP_MODE_READ_ONLY, 1600)
    }

    fun unregister() {
        rtcEngine.registerAudioFrameObserver(null)
    }

    override fun onRecordAudioFrame(
        channelId: String?,
        type: Int,
        samplesPerChannel: Int,
        bytesPerSample: Int,
        channels: Int,
        samplesPerSec: Int,
        buffer: ByteBuffer?,
        renderTimeMs: Long,
        avSyncType: Int
    ): Boolean {
        buffer?.let {
            val shorts = byteBufferToShorts(it, samplesPerChannel, channels)
            if (shorts.isNotEmpty()) {
                onLocalPcm(shorts, samplesPerSec)
            }
        }
        return true
    }

    override fun onPlaybackAudioFrameBeforeMixing(
        channelId: String?,
        uid: Int,
        type: Int,
        samplesPerChannel: Int,
        bytesPerSample: Int,
        channels: Int,
        samplesPerSec: Int,
        buffer: ByteBuffer?,
        renderTimeMs: Long,
        avSyncType: Int,
        is_mute: Int
    ): Boolean {
        buffer?.let {
            val shorts = byteBufferToShorts(it, samplesPerChannel, channels)
            if (shorts.isNotEmpty()) {
                onRemotePcm(uid, shorts, samplesPerSec)
            }
        }
        return true
    }

    override fun onPlaybackAudioFrame(
        channelId: String?,
        type: Int,
        samplesPerChannel: Int,
        bytesPerSample: Int,
        channels: Int,
        samplesPerSec: Int,
        buffer: ByteBuffer?,
        renderTimeMs: Long,
        avSyncType: Int
    ): Boolean = true

    override fun onMixedAudioFrame(
        channelId: String?,
        type: Int,
        samplesPerChannel: Int,
        bytesPerSample: Int,
        channels: Int,
        samplesPerSec: Int,
        buffer: ByteBuffer?,
        renderTimeMs: Long,
        avSyncType: Int
    ): Boolean = true

    override fun onEarMonitoringAudioFrame(
        type: Int,
        samplesPerChannel: Int,
        bytesPerSample: Int,
        channels: Int,
        samplesPerSec: Int,
        buffer: ByteBuffer?,
        renderTimeMs: Long,
        avSyncType: Int
    ): Boolean = true

    override fun getObservedAudioFramePosition(): Int = 1 or 2 // POSITION_RECORD (1) | POSITION_PLAYBACK_BEFORE_MIXING (2)

    override fun getRecordAudioParams(): AudioParams? = null
    override fun getPlaybackAudioParams(): AudioParams? = null
    override fun getMixedAudioParams(): AudioParams? = null
    override fun getEarMonitoringAudioParams(): AudioParams? = null

    private fun byteBufferToShorts(byteBuffer: ByteBuffer, samplesPerChannel: Int, channels: Int): ShortArray {
        val totalSamples = samplesPerChannel * channels
        val shortArray = ShortArray(totalSamples)
        val duplicate = byteBuffer.duplicate()
        duplicate.order(ByteOrder.LITTLE_ENDIAN)
        val shortBuffer = duplicate.asShortBuffer()
        val count = minOf(shortBuffer.remaining(), totalSamples)
        if (count > 0) {
            shortBuffer.get(shortArray, 0, count)
        }
        return shortArray
    }
}
