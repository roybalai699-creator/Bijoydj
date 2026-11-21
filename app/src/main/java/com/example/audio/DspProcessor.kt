package com.example.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tanh

/**
 * Real-time per-channel DSP chain:
 * - 3 / 5 / 10 / 15-Band EQ Split + Dedicated Mid-Side Vocal Clarity / Suppression (vocalDb)
 * - Dual-mode Resonant Filter + 6 Sound Color FX
 * - 24 DJ Effects: Echo, Reverb, Delay, Flanger, Phaser, Chorus, Distortion, Filter, LPF, HPF,
 *   Beat Repeat, Roll, Backspin, Brake, Vinyl Stop, Noise, Gate, Compressor, Limiter,
 *   Tremolo, Stutter, Bit Crusher, Trans.
 * Zero heap allocations on processStereo() audio thread hot path.
 */
class ChannelDspProcessor(private val sampleRate: Float = 44100f) {

    // Multi-band crossover LPF states (Stereo L/R)
    private var subLpL = 0f; private var subLpR = 0f
    private var lpLowL = 0f; private var lpLowR = 0f
    private var lpMidL = 0f; private var lpMidR = 0f
    private var airLpL = 0f; private var airLpR = 0f

    // Center vocal band-pass filter states (300Hz..4500Hz on center (L+R)*0.5)
    private var vocLowLp = 0f
    private var vocHighLp = 0f

    // Resonant State-Variable Filter (SVF) states
    private var svfLowL = 0f; private var svfBandL = 0f
    private var svfLowR = 0f; private var svfBandR = 0f

    // Delay / Echo / Flanger / Chorus / Roll / Stutter ring buffer (1.5 seconds stereo)
    private val delaySize = 66150
    private val delayBufL = FloatArray(delaySize)
    private val delayBufR = FloatArray(delaySize)
    private var delayWritePos = 0

    // Reverb comb buffers (~70ms & ~53ms)
    private val rev1Size = 3087
    private val rev2Size = 2337
    private val rev1L = FloatArray(rev1Size); private val rev1R = FloatArray(rev1Size)
    private val rev2L = FloatArray(rev2Size); private val rev2R = FloatArray(rev2Size)
    private var rev1Pos = 0
    private var rev2Pos = 0

    // Modulation & dynamics states
    private var lfoPhase = 0f
    private var transPhase = 0f
    private var crushCounter = 0
    private var crushHoldL = 0f
    private var crushHoldR = 0f
    private var noiseState = 1234567
    private var envFollower = 0f

    // Cached gains
    @Volatile var gainLin: Float = 1f
    @Volatile var bassGain: Float = 1f
    @Volatile var midGain: Float = 1f
    @Volatile var trebleGain: Float = 1f
    @Volatile var subGain: Float = 1f
    @Volatile var airGain: Float = 1f
    @Volatile var vocalGainLin: Float = 1f // < 1 cuts center vocals, > 1 boosts center vocal band
    @Volatile var vocalDb: Float = 0f
    @Volatile var filterCutoff: Float = 0f // -1..+1
    @Volatile var colorFxType: SoundColorFxType = SoundColorFxType.FILTER
    @Volatile var colorFxParam: Float = 0.5f
    @Volatile var activeEffect: DjEffectType = DjEffectType.NONE
    @Volatile var effectWet: Float = 0.45f
    @Volatile var effectParam: Float = 0.5f
    @Volatile var limiterEnabled: Boolean = true

    private val alphaSub = (2.0 * PI * 75.0 / sampleRate).toFloat().coerceIn(0.005f, 0.25f)
    private val alphaLow = (2.0 * PI * 240.0 / sampleRate).toFloat().coerceIn(0.01f, 0.45f)
    private val alphaMid = (2.0 * PI * 2600.0 / sampleRate).toFloat().coerceIn(0.05f, 0.75f)
    private val alphaAir = (2.0 * PI * 9000.0 / sampleRate).toFloat().coerceIn(0.1f, 0.9f)
    private val alphaVocLo = (2.0 * PI * 300.0 / sampleRate).toFloat().coerceIn(0.01f, 0.3f)
    private val alphaVocHi = (2.0 * PI * 4500.0 / sampleRate).toFloat().coerceIn(0.1f, 0.85f)

    fun updateEq(
        gainDb: Float,
        bassDb: Float,
        midDb: Float,
        trebleDb: Float,
        filter: Float,
        vocalDb: Float? = null
    ) {
        gainLin = dbToLinear(gainDb)
        bassGain = dbToLinear(bassDb)
        midGain = dbToLinear(midDb)
        trebleGain = dbToLinear(trebleDb)
        filterCutoff = filter.coerceIn(-1f, 1f)
        if (vocalDb != null) {
            this.vocalDb = vocalDb.coerceIn(-12f, 12f)
            vocalGainLin = dbToLinear(this.vocalDb)
        }
    }

    fun updateVocalDb(db: Float) {
        vocalDb = db.coerceIn(-12f, 12f)
        vocalGainLin = dbToLinear(vocalDb)
    }

    fun updateTenBandEq(bandsDb: List<Float>) {
        if (bandsDb.size < 10) return
        val subDb = (bandsDb[0] + bandsDb[1]) * 0.5f
        val lowDb = (bandsDb[2] + bandsDb[3]) * 0.5f
        val midDb = (bandsDb[4] + bandsDb[5] + bandsDb[6]) / 3f
        val highDb = (bandsDb[7] + bandsDb[8]) * 0.5f
        val airDb = bandsDb[9]
        subGain = dbToLinear(subDb * 0.5f)
        bassGain = dbToLinear(lowDb)
        midGain = dbToLinear(midDb)
        trebleGain = dbToLinear(highDb)
        airGain = dbToLinear(airDb * 0.5f)
    }

    fun updateFifteenBandEq(bandsDb: List<Float>) {
        if (bandsDb.size < 15) return
        val subDb = (bandsDb[0] + bandsDb[1] + bandsDb[2]) / 3f
        val lowDb = (bandsDb[3] + bandsDb[4] + bandsDb[5]) / 3f
        val midDb = (bandsDb[6] + bandsDb[7] + bandsDb[8] + bandsDb[9]) / 4f
        val highDb = (bandsDb[10] + bandsDb[11] + bandsDb[12]) / 3f
        val airDb = (bandsDb[13] + bandsDb[14]) * 0.5f
        subGain = dbToLinear(subDb * 0.5f)
        bassGain = dbToLinear(lowDb)
        midGain = dbToLinear(midDb)
        trebleGain = dbToLinear(highDb)
        airGain = dbToLinear(airDb * 0.5f)
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
        subLpL = 0f; subLpR = 0f; airLpL = 0f; airLpR = 0f
        vocLowLp = 0f; vocHighLp = 0f
        svfLowL = 0f; svfBandL = 0f; svfLowR = 0f; svfBandR = 0f
        envFollower = 0f
        delayBufL.fill(0f); delayBufR.fill(0f)
        rev1L.fill(0f); rev1R.fill(0f); rev2L.fill(0f); rev2R.fill(0f)
    }

    fun processStereo(inL: Float, inR: Float, outPair: FloatArray) {
        var l = inL * gainLin
        var r = inR * gainLin

        // 0. Center Vocal Presence / Suppression (-12dB cuts center vocals; +12dB isolates/boosts vocals)
        val vGain = vocalGainLin
        if (abs(vGain - 1f) > 0.02f) {
            val mid = (l + r) * 0.5f
            vocLowLp += alphaVocLo * (mid - vocLowLp)
            vocHighLp += alphaVocHi * (mid - vocHighLp)
            val vocalBand = vocHighLp - vocLowLp
            val delta = vocalBand * (vGain - 1f)
            l += delta
            r += delta
        }

        // 1. Multi-band EQ Split (Sub / Low / Mid / High / Air)
        subLpL += alphaSub * (l - subLpL); subLpR += alphaSub * (r - subLpR)
        lpLowL += alphaLow * (l - lpLowL); lpLowR += alphaLow * (r - lpLowR)
        lpMidL += alphaMid * (l - lpMidL); lpMidR += alphaMid * (r - lpMidR)
        airLpL += alphaAir * (l - airLpL); airLpR += alphaAir * (r - airLpR)

        val subL = subLpL; val subR = subLpR
        val lowL = lpLowL - subLpL; val lowR = lpLowR - subLpR
        val midL = lpMidL - lpLowL; val midR = lpMidR - lpLowR
        val highL = airLpL - lpMidL; val highR = airLpR - lpMidR
        val airL = l - airLpL; val airR = r - airLpR

        l = subL * subGain * bassGain + lowL * bassGain + midL * midGain + highL * trebleGain + airL * airGain * trebleGain
        r = subR * subGain * bassGain + lowR * bassGain + midR * midGain + highR * trebleGain + airR * airGain * trebleGain

        // 2. Per-Channel Color / Filter Knob (-1..+1)
        val fc = filterCutoff
        val absFc = abs(fc)
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

        // 3. 24 Professional DJ Effects Processor
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
                    val dL = delayBufL[readPos]; val dR = delayBufR[readPos]
                    val feedback = if (fx == DjEffectType.ECHO) 0.58f else 0.42f
                    delayBufL[delayWritePos] = l + dL * feedback
                    delayBufR[delayWritePos] = r + dR * feedback
                    fxL = l * dry + dL * wet
                    fxR = r * dry + dR * wet
                }
                DjEffectType.REVERB -> {
                    val c1L = rev1L[rev1Pos]; val c1R = rev1R[rev1Pos]
                    val c2L = rev2L[rev2Pos]; val c2R = rev2R[rev2Pos]
                    val fb = 0.55f + effectParam * 0.25f
                    rev1L[rev1Pos] = l + c1L * fb; rev1R[rev1Pos] = r + c1R * fb
                    rev2L[rev2Pos] = l + c2L * fb; rev2R[rev2Pos] = r + c2R * fb
                    rev1Pos = (rev1Pos + 1) % rev1Size
                    rev2Pos = (rev2Pos + 1) % rev2Size
                    fxL = l * dry + (c1L + c2L) * 0.5f * wet
                    fxR = r * dry + (c1R + c2R) * 0.5f * wet
                }
                DjEffectType.FLANGER, DjEffectType.PHASER, DjEffectType.CHORUS -> {
                    val rateHz = when (fx) {
                        DjEffectType.CHORUS -> 0.8f + effectParam * 2.2f
                        DjEffectType.FLANGER -> 0.3f + effectParam * 2.5f
                        else -> 0.25f + effectParam * 2.0f
                    }
                    lfoPhase += (2.0 * PI * rateHz / sampleRate).toFloat()
                    if (lfoPhase > 2f * PI.toFloat()) lfoPhase -= 2f * PI.toFloat()
                    val baseDelay = if (fx == DjEffectType.CHORUS) 480f else 30f
                    val modDepth = when (fx) {
                        DjEffectType.CHORUS -> 320f
                        DjEffectType.FLANGER -> 220f
                        else -> 85f
                    }
                    val modDelay = (baseDelay + (0.5f + 0.5f * sin(lfoPhase)) * modDepth).toInt().coerceIn(8, delaySize - 1)
                    val readPosL = (delayWritePos - modDelay + delaySize) % delaySize
                    val modDelayR = (baseDelay + (0.5f + 0.5f * cos(lfoPhase)) * modDepth).toInt().coerceIn(8, delaySize - 1)
                    val readPosR = (delayWritePos - modDelayR + delaySize) % delaySize
                    val dL = delayBufL[readPosL]; val dR = delayBufR[readPosR]
                    val fb = if (fx == DjEffectType.CHORUS) 0.15f else 0.45f
                    delayBufL[delayWritePos] = l + dL * fb
                    delayBufR[delayWritePos] = r + dR * fb
                    fxL = l * dry + dL * wet
                    fxR = r * dry + dR * wet
                }
                DjEffectType.ROLL, DjEffectType.BEAT_REPEAT, DjEffectType.STUTTER -> {
                    val baseSec = if (fx == DjEffectType.STUTTER) 0.03f else 0.06f
                    val spanSec = if (fx == DjEffectType.STUTTER) 0.11f else 0.25f
                    val sliceLen = ((baseSec + (1f - effectParam) * spanSec) * sampleRate).toInt().coerceIn(800, delaySize - 1)
                    val readPos = (delayWritePos - sliceLen + delaySize) % delaySize
                    val dL = delayBufL[readPos]; val dR = delayBufR[readPos]
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
                DjEffectType.BIT_CRUSHER -> {
                    val holdPeriod = (1 + (effectParam * 20f).toInt()).coerceAtLeast(1)
                    if (crushCounter % holdPeriod == 0) {
                        val levels = (3f + (1f - wet) * 24f)
                        crushHoldL = kotlin.math.round(l * levels) / levels
                        crushHoldR = kotlin.math.round(r * levels) / levels
                    }
                    crushCounter++
                    fxL = l * (1f - wet) + crushHoldL * wet
                    fxR = r * (1f - wet) + crushHoldR * wet
                }
                DjEffectType.FILTER, DjEffectType.LOW_PASS_FILTER, DjEffectType.HIGH_PASS_FILTER -> {
                    val sweep = when (fx) {
                        DjEffectType.LOW_PASS_FILTER -> (0.04f + (1f - effectParam) * 0.45f).coerceIn(0.02f, 0.75f)
                        DjEffectType.HIGH_PASS_FILTER -> (0.04f + effectParam * 0.45f).coerceIn(0.02f, 0.75f)
                        else -> {
                            lfoPhase += (2.0 * PI * (0.25f + effectParam * 1.8f) / sampleRate).toFloat()
                            if (lfoPhase > 2f * PI.toFloat()) lfoPhase -= 2f * PI.toFloat()
                            0.08f + (0.5f + 0.5f * cos(lfoPhase)) * 0.55f
                        }
                    }
                    svfLowL += sweep * svfBandL
                    val hpL = l - svfLowL - 0.5f * svfBandL
                    svfBandL += sweep * hpL
                    svfLowR += sweep * svfBandR
                    val hpR = r - svfLowR - 0.5f * svfBandR
                    svfBandR += sweep * hpR
                    val filtL = if (fx == DjEffectType.HIGH_PASS_FILTER) hpL else svfLowL
                    val filtR = if (fx == DjEffectType.HIGH_PASS_FILTER) hpR else svfLowR
                    fxL = l * (1f - wet) + filtL * wet
                    fxR = r * (1f - wet) + filtR * wet
                }
                DjEffectType.NOISE -> {
                    noiseState = noiseState * 1664525 + 1013904223
                    val n = ((noiseState ushr 16) and 0xFFFF) / 32768f - 1f
                    val sweepFreq = (0.04f + effectParam * 0.50f).coerceIn(0.03f, 0.75f)
                    svfLowL += sweepFreq * svfBandL
                    svfBandL += sweepFreq * (n - svfLowL - 0.35f * svfBandL)
                    fxL = l + svfBandL * wet * 0.45f
                    fxR = r + svfBandL * wet * 0.45f
                }
                DjEffectType.TRANS, DjEffectType.TREMOLO -> {
                    val rateHz = 2.0f + effectParam * 10.0f
                    transPhase += (2.0 * PI * rateHz / sampleRate).toFloat()
                    if (transPhase > 2f * PI.toFloat()) transPhase -= 2f * PI.toFloat()
                    val gate = if (fx == DjEffectType.TRANS) {
                        if (sin(transPhase) >= 0f) 1f else (1f - wet)
                    } else {
                        1f - wet * (0.5f + 0.5f * sin(transPhase))
                    }
                    fxL = l * gate
                    fxR = r * gate
                }
                DjEffectType.GATE -> {
                    val peak = maxOf(abs(l), abs(r))
                    envFollower = envFollower * 0.992f + peak * 0.008f
                    val threshold = 0.02f + effectParam * 0.25f
                    val gateGain = if (envFollower >= threshold) 1f else (1f - wet * 0.92f)
                    fxL = l * gateGain
                    fxR = r * gateGain
                }
                DjEffectType.COMPRESSOR -> {
                    val peak = maxOf(abs(l), abs(r))
                    envFollower = envFollower * 0.995f + peak * 0.005f
                    val threshold = 0.25f - effectParam * 0.12f
                    val compGain = if (envFollower > threshold) {
                        val over = envFollower - threshold
                        (threshold + over * 0.28f) / envFollower.coerceAtLeast(0.001f)
                    } else 1.25f
                    val gain = (1f - wet) + compGain * wet
                    fxL = l * gain
                    fxR = r * gain
                }
                DjEffectType.LIMITER -> {
                    val boost = 1f + effectParam * 0.6f
                    val ceiling = 0.88f - wet * 0.15f
                    fxL = (l * boost).coerceIn(-ceiling, ceiling)
                    fxR = (r * boost).coerceIn(-ceiling, ceiling)
                }
                DjEffectType.BRAKE, DjEffectType.VINYL_STOP, DjEffectType.BACKSPIN, DjEffectType.NONE -> {
                    // Handled at transport/resampler level in BijoyAudioEngine
                }
            }
            l = fxL
            r = fxR
        } else {
            delayBufL[delayWritePos] = l
            delayBufR[delayWritePos] = r
        }

        delayWritePos = (delayWritePos + 1) % delaySize

        outPair[0] = if (limiterEnabled) softClip(l) else l.coerceIn(-1f, 1f)
        outPair[1] = if (limiterEnabled) softClip(r) else r.coerceIn(-1f, 1f)
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
