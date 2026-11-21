package com.example.audio

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

object BpmAndWaveformAnalyzer {

    /**
     * Generates a real, multi-band intensity waveform from decoded stereo 16-bit PCM audio.
     * Computes amplitude, low-frequency (bass) energy via 1-pole LPF, and high-frequency energy.
     */
    fun generateWaveform(
        pcmStereo: ShortArray,
        sampleRate: Int,
        bpm: Float,
        bucketCount: Int = 180
    ): WaveformData {
        if (pcmStereo.isEmpty()) return WaveformData()

        val totalFrames = pcmStereo.size / 2
        val framesPerBucket = max(1, totalFrames / bucketCount)
        val peaks = ArrayList<WaveformPeak>(bucketCount)

        // Simple 1-pole LPF state for bass extraction (~220Hz)
        val alphaLow = (2.0 * Math.PI * 220.0 / sampleRate).toFloat().coerceIn(0.005f, 0.2f)
        var lpState = 0f
        var prevSample = 0f

        var maxAmp = 0.001f

        val rawAmps = FloatArray(bucketCount)
        val rawLows = FloatArray(bucketCount)
        val rawHighs = FloatArray(bucketCount)

        for (b in 0 until bucketCount) {
            val startFrame = b * framesPerBucket
            val endFrame = min(totalFrames, startFrame + framesPerBucket)
            if (startFrame >= totalFrames) break

            var sumSq = 0.0
            var lowSq = 0.0
            var highSq = 0.0
            var count = 0
            // Step by 4 frames for ultra-fast analysis on long tracks
            val step = max(1, (endFrame - startFrame) / 256)

            var f = startFrame
            while (f < endFrame) {
                val idx = f * 2
                val mono = ((pcmStereo[idx].toInt() + pcmStereo[idx + 1].toInt()) * 0.5f) / 32768f
                lpState += alphaLow * (mono - lpState)
                val high = mono - prevSample
                prevSample = mono

                sumSq += mono * mono
                lowSq += lpState * lpState
                highSq += high * high
                count++
                f += step
            }

            if (count > 0) {
                val rms = sqrt(sumSq / count).toFloat()
                val lowRms = sqrt(lowSq / count).toFloat()
                val highRms = sqrt(highSq / count).toFloat()
                rawAmps[b] = rms
                rawLows[b] = lowRms
                rawHighs[b] = highRms
                if (rms > maxAmp) maxAmp = rms
            }
        }

        val invMax = 1f / maxAmp
        for (b in 0 until bucketCount) {
            val amp = (rawAmps[b] * invMax).coerceIn(0.06f, 1f)
            val lowRatio = if (rawAmps[b] > 0.0001f) (rawLows[b] / rawAmps[b]).coerceIn(0f, 1f) else 0.5f
            val highRatio = if (rawAmps[b] > 0.0001f) (rawHighs[b] / rawAmps[b]).coerceIn(0f, 1f) else 0.5f
            peaks.add(WaveformPeak(amplitude = amp, lowEnergy = lowRatio, highEnergy = highRatio))
        }

        // Generate beat grid marker ratios from BPM
        val durationSec = totalFrames.toFloat() / sampleRate.toFloat()
        val beats = mutableListOf<Float>()
        if (bpm > 40f && durationSec > 0.5f) {
            val beatIntervalSec = 60f / bpm
            var t = 0f
            while (t < durationSec && beats.size < 400) {
                beats.add((t / durationSec).coerceIn(0f, 1f))
                t += beatIntervalSec
            }
        }

        return WaveformData(peaks = peaks, beatPositionsRatio = beats)
    }

    /**
     * Real energy-onset autocorrelation BPM detector on decoded PCM stereo samples.
     * Analyzes up to the first 30 seconds of audio in the 70..175 BPM range.
     */
    fun detectBpm(pcmStereo: ShortArray, sampleRate: Int): Float {
        val totalFrames = pcmStereo.size / 2
        if (totalFrames < sampleRate * 2) return 128f

        val analyzeFrames = min(totalFrames, sampleRate * 30)
        val hop = 512
        val envSize = analyzeFrames / hop
        if (envSize < 64) return 128f

        val energy = FloatArray(envSize)
        for (i in 0 until envSize) {
            val base = i * hop * 2
            var sum = 0f
            var k = 0
            while (k < hop && (base + k * 2 + 1) < pcmStereo.size) {
                val mono = (pcmStereo[base + k * 2].toInt() + pcmStereo[base + k * 2 + 1].toInt()) * 0.5f / 32768f
                sum += mono * mono
                k += 2
            }
            energy[i] = sum
        }

        // Half-wave rectified onset difference
        val onset = FloatArray(envSize)
        for (i in 1 until envSize) {
            val diff = energy[i] - energy[i - 1]
            onset[i] = if (diff > 0f) diff else 0f
        }

        val envRate = sampleRate.toFloat() / hop.toFloat()
        val minLag = ((60f / 175f) * envRate).toInt().coerceAtLeast(10)
        val maxLag = ((60f / 75f) * envRate).toInt().coerceAtMost(envSize / 2)

        var bestLag = minLag
        var bestScore = -1f

        for (lag in minLag..maxLag) {
            var score = 0f
            val limit = envSize - lag
            for (i in 0 until limit) {
                score += onset[i] * onset[i + lag]
            }
            // Slight preference toward 110..145 BPM dance tempo range
            val candidateBpm = 60f * envRate / lag
            val weight = if (candidateBpm in 105f..148f) 1.15f else 1.0f
            val weighted = score * weight
            if (weighted > bestScore) {
                bestScore = weighted
                bestLag = lag
            }
        }

        if (bestScore <= 0.00001f) return 128f
        var detected = 60f * envRate / bestLag.toFloat()
        while (detected < 80f) detected *= 2f
        while (detected > 168f) detected /= 2f
        return (kotlin.math.round(detected * 2f) / 2f).coerceIn(70f, 175f)
    }

    /**
     * Computes instantaneous 16-band spectrum magnitudes from a stereo float buffer
     * using lightweight resonant bandpass energy banks (zero heap allocation).
     */
    class RealtimeSpectrumAnalyzer(private val sampleRate: Int = 44100) {
        private val bandCount = 16
        private val freqs = floatArrayOf(
            45f, 75f, 115f, 175f, 260f, 380f, 550f, 800f,
            1150f, 1650f, 2400f, 3500f, 5000f, 7200f, 10500f, 14500f
        )
        private val coeff = FloatArray(bandCount)
        private val smoothed = FloatArray(bandCount)

        init {
            for (i in 0 until bandCount) {
                val omega = (2.0 * Math.PI * freqs[i] / sampleRate).toFloat()
                coeff[i] = 2f * kotlin.math.cos(omega)
            }
        }

        fun analyze(stereoBuffer: FloatArray, frameCount: Int, outBands: FloatArray) {
            val n = min(frameCount, 256)
            if (n <= 0) return
            for (b in 0 until bandCount) {
                val c = coeff[b]
                var s1 = 0f
                var s2 = 0f
                var i = 0
                while (i < n) {
                    val sample = (stereoBuffer[i * 2] + stereoBuffer[i * 2 + 1]) * 0.5f
                    val s0 = sample + c * s1 - s2
                    s2 = s1
                    s1 = s0
                    i++
                }
                val power = (s1 * s1 + s2 * s2 - c * s1 * s2).coerceAtLeast(0f)
                val mag = (sqrt(power) / (n * 0.14f)).coerceIn(0f, 1f)
                // Smooth decay for realistic LED bar ballistics
                val prev = smoothed[b]
                val next = if (mag > prev) prev * 0.35f + mag * 0.65f else prev * 0.80f + mag * 0.20f
                smoothed[b] = next
                outBands[b] = abs(next).coerceIn(0f, 1f)
            }
        }
    }
}
