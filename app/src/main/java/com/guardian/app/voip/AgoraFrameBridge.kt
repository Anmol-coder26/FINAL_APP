package com.guardian.app.voip

import io.agora.rtc2.Constants
import io.agora.rtc2.IAudioFrameObserver
import io.agora.rtc2.RtcEngine
import io.agora.rtc2.audio.AudioParams
import java.nio.ByteBuffer

class AgoraFrameBridge(
    private val rtcEngine: RtcEngine,
    private val onLocalPcm: (ShortArray, Int) -> Unit,
    private val onRemotePcm: (uid: Int, ShortArray, Int) -> Unit
) : IAudioFrameObserver {

    fun register() {
        check(rtcEngine.setRecordingAudioFrameParameters(16000, 1, Constants.RAW_AUDIO_FRAME_OP_MODE_READ_ONLY, 1600) == 0) {
            "Agora could not configure local PCM capture"
        }
        // The mixed-playback setter does not configure the per-user callback.
        check(rtcEngine.setPlaybackAudioFrameBeforeMixingParameters(16000, 1) == 0) {
            "Agora could not configure caller PCM capture"
        }
        check(rtcEngine.registerAudioFrameObserver(this) == 0) { "Agora could not register PCM capture" }
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
            val shorts = Pcm16Frames.mono(it, samplesPerChannel, channels, bytesPerSample)
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
            val shorts = Pcm16Frames.mono(it, samplesPerChannel, channels, bytesPerSample)
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

    // Agora 4.4.1: RECORD=0x0002; BEFORE_MIXING=0x0008. 0x0001 is mixed playback.
    override fun getObservedAudioFramePosition(): Int = 0x0008 or 0x0002

    override fun getRecordAudioParams(): AudioParams = AudioParams(16000, 1, Constants.RAW_AUDIO_FRAME_OP_MODE_READ_ONLY, 1600)
    override fun getPlaybackAudioParams(): AudioParams? = null
    override fun getMixedAudioParams(): AudioParams? = null
    override fun getEarMonitoringAudioParams(): AudioParams? = null

}
