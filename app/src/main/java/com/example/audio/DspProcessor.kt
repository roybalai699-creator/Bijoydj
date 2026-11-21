package com.example.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tanh

/**
 * Real-time per-deck DSP chain:
 * - 3-Band EQ (Low Shelf 180Hz, Mid Peaking 1kHz, High Shelf 6kHz)
 * - Dual-mode Resonant Filter (LPF when cutoff < 0, HPF when cutoff > 0)
 * - DJ Effects: Echo, Reverb, Flanger, Phaser, Delay, Filter Sweep, Roll, Beat Repeat, Distortion
 * Zero allocations on processSample() hot path.
 */
class ChannelDspProcessor(private val sampleRate: Float = 44100f) {

    // 1-pole & 2-pole filter states (Stereo L/R)
    private var lpLowL = 0f
    private var lpLowR = 0f
    private var lpMidL = 0f
    private var lpMidR = 0f

    // Resonant State-Variable Filter (SVF) states
    private var svfLowL = 0f
    private var svfBandL = 0f
    private var svfLowR = 0f
    private var svfBandR = 0f

    // Delay / Echo / Flanger / Roll ring buffer (1.5 seconds stereo)
    private val delaySize = 66150
    private val delayBufL = FloatArray(delaySize)
    private val delayBufR = FloatArray(delaySize)
    private var delayWritePos = 0

    // Reverb comb buffer (~70ms & ~53ms)
    private val rev1Size = 3087
    private val rev2Size = 2337
    private val rev1L = FloatArray(rev1Size)
    private val rev1R = FloatArray(rev1Size)
    private val rev2L = FloatArray(rev2Size)
    private val rev2R = FloatArray(rev2Size)
    private var rev1Pos = 0
    private var rev2Pos = 0

    // LFO phase for Flanger / Phaser / Filter sweep
    private var lfoPhase = 0f
    private var crushCounter = 0
    private var crushHoldL = 0f
    private var crushHoldR = 0f
    private var noiseState = 1234567

    // Cached EQ gains (linear)
    @Volatile var gainLin: Float = 1f
    @Volatile var bassGain: Float = 1f
    @Volatile var midGain: Float = 1f
    @Volatile var trebleGain: Float = 1f
    @Volatile var filterCutoff: Float = 0f // -1..+1 (Color knob)
    @Volatile var colorFxType: SoundColorFxType = SoundColorFxType.FILTER
    @Volatile var colorFxParam: Float = 0.5f
    @Volatile var activeEffect: DjEffectType = DjEffectType.NONE
    @Volatile var effectWet: Float = 0.45f
    @Volatile var effectParam: Float = 0.5f

    private val alphaLow = (2.0 * PI * 220.0 / sampleRate).toFloat().coerceIn(0.01f, 0.45f)
    private val alphaMid = (2.0 * PI * 2800.0 / sampleRate).toFloat().coerceIn(0.05f, 0.75f)

    fun updateEq(gainDb: Float, bassDb: Float, midDb: Float, trebleDb: Float, filter: Float) {
        gainLin = dbToLinear(gainDb)
        bassGain = dbToLinear(bassDb)
        midGain = dbToLinear(midDb)
        trebleGain = dbToLinear(trebleDb)
        filterCutoff = filter.coerceIn(-1f, 1f)
    }

    fun updateColorFx(type: SoundColorFxType, param: Float) {
        colorFxType = type
        colorFxParam = param.coerceIn(0f, 1f)
    }

    fun updateEffect(effect: DjEffectType, wet: Float, param: Float) {
        activeEffect = effect
        effectWet = wet.coerceIn(0f, 1f)
        effectParam = param.coerceIn(0f, 1f)
    }

    fun reset() {
        lpLowL = 0f; lpLowR = 0f; lpMidL = 0f; lpMidR = 0f
        svfLowL = 0f; svfBandL = 0f; svfLowR = 0f; svfBandR = 0f
        delayBufL.fill(0f); delayBufR.fill(0f)
        rev1L.fill(0f); rev1R.fill(0f); rev2L.fill(0f); rev2R.fill(0f)
    }

    /**
     * Processes a single stereo sample pair in-place into outPair[0]=L, outPair[1]=R.
     */
    fun processStereo(inL: Float, inR: Float, outPair: FloatArray) {
        var l = inL * gainLin
        var r = inR * gainLin

        // 1. 3-Band Isolator / EQ Split
        lpLowL += alphaLow * (l - lpLowL)
        lpLowR += alphaLow * (r - lpLowR)
        lpMidL += alphaMid * (l - lpMidL)
        lpMidR += alphaMid * (r - lpMidR)

        val lowL = lpLowL
        val lowR = lpLowR
        val midL = lpMidL - lpLowL
        val midR = lpMidR - lpLowR
        val highL = l - lpMidL
        val highR = r - lpMidR

        l = lowL * bassGain + midL * midGain + highL * trebleGain
        r = lowR * bassGain + midR * midGain + highR * trebleGain

        // 2. DJM-A9 Sound Color FX (driven by per-channel COLOR knob -1..+1)
        val fc = filterCutoff
        val absFc = kotlin.math.abs(fc)
        if (absFc > 0.025f) {
            val resQ = 0.72f - colorFxParam * 0.35f
            when (colorFxType) {
                SoundColorFxType.FILTER, SoundColorFxType.SWEEP -> {
                    val normFreq = if (fc < 0f) {
                        (1f + fc).coerceIn(0.02f, 0.99f).pow(2.2f) * 0.78f + 0.02f
                    } else {
                        fc.pow(2.0f) * 0.55f + 0.015f
                    }
                    svfLowL += normFreq * svfBandL
                    val hpL = l - svfLowL - resQ * svfBandL
                    svfBandL += normFreq * hpL
                    svfLowR += normFreq * svfBandR
                    val hpR = r - svfLowR - resQ * svfBandR
                    svfBandR += normFreq * hpR
                    if (colorFxType == SoundColorFxType.SWEEP) {
                        l = l * (1f - absFc * 0.6f) + svfBandL * absFc * 0.9f
                        r = r * (1f - absFc * 0.6f) + svfBandR * absFc * 0.9f
                    } else if (fc < 0f) {
                        l = svfLowL; r = svfLowR
                    } else {
                        l = hpL; r = hpR
                    }
                }
                SoundColorFxType.SPACE -> {
                    val c1L = rev1L[rev1Pos]; val c1R = rev1R[rev1Pos]
                    val fb = 0.45f + colorFxParam * 0.3f
                    rev1L[rev1Pos] = l + c1L * fb
                    rev1R[rev1Pos] = r + c1R * fb
                    rev1Pos = (rev1Pos + 1) % rev1Size
                    l = l * (1f - absFc * 0.4f) + c1L * absFc * 0.75f
                    r = r * (1f - absFc * 0.4f) + c1R * absFc * 0.75f
                }
                SoundColorFxType.DUB_ECHO -> {
                    val dSamples = ((0.12f + colorFxParam * 0.28f) * sampleRate).toInt().coerceIn(1000, delaySize - 1)
                    val readPos = (delayWritePos - dSamples + delaySize) % delaySize
                    val dL = delayBufL[readPos]; val dR = delayBufR[readPos]
                    delayBufL[delayWritePos] = l + dL * 0.52f
                    delayBufR[delayWritePos] = r + dR * 0.52f
                    l += dL * absFc * 0.7f
                    r += dR * absFc * 0.7f
                }
                SoundColorFxType.CRUSH -> {
                    val holdPeriod = (1 + (absFc * 18f * (0.5f + colorFxParam)).toInt()).coerceAtLeast(1)
                    if (crushCounter % holdPeriod == 0) {
                        val levels = (4f + (1f - absFc) * 28f)
                        crushHoldL = kotlin.math.round(l * levels) / levels
                        crushHoldR = kotlin.math.round(r * levels) / levels
                    }
                    crushCounter++
                    l = crushHoldL; r = crushHoldR
                }
                SoundColorFxType.NOISE -> {
                    noiseState = noiseState * 1664525 + 1013904223
                    val n = ((noiseState ushr 16) and 0xFFFF) / 32768f - 1f
                    val sweepFreq = (0.04f + (fc + 1f) * 0.30f).coerceIn(0.03f, 0.75f)
                    svfLowL += sweepFreq * svfBandL
                    val hpN = n - svfLowL - 0.35f * svfBandL
                    svfBandL += sweepFreq * hpN
                    l = l * (1f - absFc * 0.25f) + svfBandL * absFc * 0.35f
                    r = r * (1f - absFc * 0.25f) + svfBandL * absFc * 0.35f
                }
            }
        }

        // 3. DJ Effects Processor
        val fx = activeEffect
        val wet = effectWet
        if (fx != DjEffectType.NONE && wet > 0.01f) {
            val dry = 1f - wet * 0.6f
            var fxL = l
            var fxR = r

            when (fx) {
                DjEffectType.ECHO, DjEffectType.DELAY -> {
                    val delaySamples = ((0.18f + effectParam * 0.45f) * sampleRate).toInt().coerceIn(2000, delaySize - 1)
                    val readPos = (delayWritePos - delaySamples + delaySize) % delaySize
                    val dL = delayBufL[readPos]
                    val dR = delayBufR[readPos]
                    val feedback = if (fx == DjEffectType.ECHO) 0.58f else 0.42f
                    delayBufL[delayWritePos] = l + dL * feedback
                    delayBufR[delayWritePos] = r + dR * feedback
                    fxL = l * dry + dL * wet
                    fxR = r * dry + dR * wet
                }
                DjEffectType.REVERB -> {
                    val c1L = rev1L[rev1Pos]
                    val c1R = rev1R[rev1Pos]
                    val c2L = rev2L[rev2Pos]
                    val c2R = rev2R[rev2Pos]
                    val fb = 0.55f + effectParam * 0.25f
                    rev1L[rev1Pos] = l + c1L * fb
                    rev1R[rev1Pos] = r + c1R * fb
                    rev2L[rev2Pos] = l + c2L * fb
                    rev2R[rev2Pos] = r + c2R * fb
                    rev1Pos = (rev1Pos + 1) % rev1Size
                    rev2Pos = (rev2Pos + 1) % rev2Size
                    fxL = l * dry + (c1L + c2L) * 0.5f * wet
                    fxR = r * dry + (c1R + c2R) * 0.5f * wet
                }
                DjEffectType.FLANGER, DjEffectType.PHASER -> {
                    val rateHz = 0.3f + effectParam * 2.5f
                    lfoPhase += (2.0 * PI * rateHz / sampleRate).toFloat()
                    if (lfoPhase > 2f * PI.toFloat()) lfoPhase -= 2f * PI.toFloat()
                    val modDepth = if (fx == DjEffectType.FLANGER) 220f else 85f
                    val modDelay = (30f + (0.5f + 0.5f * sin(lfoPhase)) * modDepth).toInt()
                    val readPos = (delayWritePos - modDelay + delaySize) % delaySize
                    val dL = delayBufL[readPos]
                    val dR = delayBufR[readPos]
                    delayBufL[delayWritePos] = l + dL * 0.45f
                    delayBufR[delayWritePos] = r + dR * 0.45f
                    fxL = l * dry + dL * wet
                    fxR = r * dry + dR * wet
                }
                DjEffectType.ROLL, DjEffectType.BEAT_REPEAT -> {
                    val sliceLen = ((0.08f + (1f - effectParam) * 0.25f) * sampleRate).toInt().coerceIn(1500, delaySize - 1)
                    val readPos = (delayWritePos - sliceLen + delaySize) % delaySize
                    val dL = delayBufL[readPos]
                    val dR = delayBufR[readPos]
                    delayBufL[delayWritePos] = dL * 0.92f + l * 0.08f
                    delayBufR[delayWritePos] = dR * 0.92f + r * 0.08f
                    fxL = l * (1f - wet) + dL * wet
                    fxR = r * (1f - wet) + dR * wet
                }
                DjEffectType.DISTORTION -> {
                    val drive = 1.5f + effectParam * 9.0f
                    fxL = l * (1f - wet) + tanh((l * drive).toDouble()).toFloat() * wet
                    fxR = r * (1f - wet) + tanh((r * drive).toDouble()).toFloat() * wet
                }
                DjEffectType.FILTER -> {
                    lfoPhase += (2.0 * PI * (0.25f + effectParam * 1.8f) / sampleRate).toFloat()
                    if (lfoPhase > 2f * PI.toFloat()) lfoPhase -= 2f * PI.toFloat()
                    val sweep = 0.08f + (0.5f + 0.5f * cos(lfoPhase)) * 0.55f
                    svfLowL += sweep * svfBandL
                    svfBandL += sweep * (l - svfLowL - 0.5f * svfBandL)
                    svfLowR += sweep * svfBandR
                    svfBandR += sweep * (r - svfLowR - 0.5f * svfBandR)
                    fxL = l * (1f - wet) + svfLowL * wet
                    fxR = r * (1f - wet) + svfLowR * wet
                }
                DjEffectType.BRAKE, DjEffectType.REVERSE, DjEffectType.NONE -> {
                    // Handled at transport/resampler level in BijoyAudioEngine
                }
            }
            l = fxL
            r = fxR
        } else {
            // Keep delay buffer fresh for seamless FX engagement
            delayBufL[delayWritePos] = l
            delayBufR[delayWritePos] = r
        }

        delayWritePos = (delayWritePos + 1) % delaySize

        // Soft limiter to prevent harsh digital clipping
        outPair[0] = softClip(l)
        outPair[1] = softClip(r)
    }

    companion object {
        fun dbToLinear(db: Float): Float {
            if (db <= -11.9f) return 0f // Full EQ kill at -12dB
            return 10.0.pow(db / 20.0).toFloat()
        }

        fun softClip(x: Float): Float {
            return when {
                x > 1.25f -> 1f
                x < -1.25f -> -1f
                x > 0.85f || x < -0.85f -> tanh(x.toDouble()).toFloat()
                else -> x
            }
        }
    }
}
