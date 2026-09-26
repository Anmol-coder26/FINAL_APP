package com.guardian.app.protect.advanced

import kotlin.math.abs
import kotlin.math.sqrt

object VoiceSynthesisDetector {
    private const val SAMPLE_RATE = 16000
    private const val WINDOW_SAMPLES = SAMPLE_RATE * 3  // 3-second analysis window
    private const val FFT_SIZE = 1024

    data class Result(
        val syntheticConfidence: Float,
        val reasons: List<String>
    )

    fun analyze(recentSamples: ShortArray): Result {
        if (recentSamples.size < WINDOW_SAMPLES / 2) {
            return Result(0f, emptyList())
        }

        val samples = recentSamples.takeLast(WINDOW_SAMPLES).map { it / 32768f }.toFloatArray()

        // 1. Compute FFT magnitude spectrum
        val spectrum = fftMagnitude(samples.take(FFT_SIZE).toFloatArray())

        // 2. Tonal vs non-tonal energy ratio
        val tonalEnd = spectrum.size / 8
        val nonTonalStart = spectrum.size / 8
        val nonTonalEnd = spectrum.size / 3

        val tonalEnergy = (0 until tonalEnd).sumOf { (spectrum[it] * spectrum[it]).toDouble() }
        val nonTonalEnergy = (nonTonalStart until nonTonalEnd).sumOf {
            (spectrum[it] * spectrum[it]).toDouble()
        }
        val ratio = (tonalEnergy / (nonTonalEnergy + 1e-6)).toFloat()

        // 3. F0 variance (prosody stability)
        val f0Variance = computeF0Variance(samples)

        val reasons = mutableListOf<String>()
        var score = 0.3f

        if (ratio > 1.0f) {
            score += 0.2f
            reasons += "unusual tonal-to-noise ratio"
        }
        if (f0Variance < 0.15f) {
            score += 0.2f
            reasons += "flat prosody (low pitch variance)"
        }

        // 4. Zero-crossing rate (synthetic speech tends to have stable ZCR)
        val zcr = computeZeroCrossingRate(samples)
        if (zcr < 0.05f || zcr > 0.3f) {
            score += 0.1f
            reasons += "atypical zero-crossing pattern"
        }

        return Result(
            syntheticConfidence = score.coerceIn(0f, 1f),
            reasons = reasons
        )
    }

    private fun fftMagnitude(input: FloatArray): FloatArray {
        val n = FFT_SIZE
        val real = FloatArray(n)
        val imag = FloatArray(n)
        input.copyInto(real, 0, 0, minOf(input.size, n))

        // Iterative Cooley-Tukey FFT
        var i = 0
        var j = 0
        while (i < n) {
            if (j > i) {
                val t = real[i]; real[i] = real[j]; real[j] = t
                val u = imag[i]; imag[i] = imag[j]; imag[j] = u
            }
            var m = n shr 1
            while (m >= 1 && j >= m) { j -= m; m = m shr 1 }
            j += m
            i++
        }

        var len = 2
        while (len <= n) {
            val ang = -2.0 * Math.PI / len
            val wr = Math.cos(ang).toFloat()
            val wi = Math.sin(ang).toFloat()
            var k = 0
            while (k < n) {
                var curR = 1f
                var curI = 0f
                for (m in 0 until len / 2) {
                    val uR = real[k + m]
                    val uI = imag[k + m]
                    val vR = real[k + m + len / 2] * curR - imag[k + m + len / 2] * curI
                    val vI = real[k + m + len / 2] * curI + imag[k + m + len / 2] * curR
                    real[k + m] = uR + vR
                    imag[k + m] = uI + vI
                    real[k + m + len / 2] = uR - vR
                    imag[k + m + len / 2] = uI - vI
                    val nextR = curR * wr - curI * wi
                    curI = curR * wi + curI * wr
                    curR = nextR
                }
                k += len
            }
            len = len shl 1
        }

        return FloatArray(n) { sqrt(real[it] * real[it] + imag[it] * imag[it]) }
    }

    private fun computeF0Variance(samples: FloatArray): Float {
        // Simplified autocorrelation-based pitch estimation
        val frameSize = 400
        val pitches = mutableListOf<Float>()
        var i = 0
        while (i + frameSize < samples.size) {
            val frame = samples.copyOfRange(i, i + frameSize)
            val pitch = estimatePitch(frame)
            if (pitch > 0f) pitches += pitch
            i += frameSize
        }
        if (pitches.size < 2) return 0.5f
        val mean = pitches.average().toFloat()
        val variance = pitches.map { (it - mean) * (it - mean) }.average().toFloat()
        return variance / (mean * mean + 1e-6f)
    }

    private fun estimatePitch(frame: FloatArray): Float {
        val maxLag = frame.size / 2
        var bestLag = 0
        var bestCorr = 0f
        for (lag in 20 until maxLag) {
            var corr = 0f
            for (i in 0 until frame.size - lag) {
                corr += frame[i] * frame[i + lag]
            }
            if (abs(corr) > abs(bestCorr)) {
                bestCorr = corr
                bestLag = lag
            }
        }
        return if (bestLag > 0) SAMPLE_RATE.toFloat() / bestLag else 0f
    }

    private fun computeZeroCrossingRate(samples: FloatArray): Float {
        var crossings = 0
        for (i in 1 until samples.size) {
            if ((samples[i - 1] >= 0 && samples[i] < 0) || (samples[i - 1] < 0 && samples[i] >= 0)) {
                crossings++
            }
        }
        return crossings.toFloat() / samples.size
    }
}
