package com.example.audio

import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * Polyphonic real-time 16-Pad SFX & Sampler engine.
 * Synthesizes 16 studio pads across DJ categories (Drums, Kicks, Snares, Claps, Bass, Risers,
 * Impacts, Vocal FX, Crowd, Siren, Scratch, DJ Drops), supports custom imported PCM samples,
 * per-pad looping, per-pad pitch tuning, and BPM-synced rhythmic Sampler Roll.
 */
class SfxSamplerEngine(private val sampleRate: Int = 44100) {

    data class ActiveVoice(
        val padIndex: Int,
        val pcmStereo: ShortArray,
        var framePos: Double = 0.0,
        val volume: Float = 0.9f,
        val pitchRatio: Float = 1.0f,
        var loop: Boolean = false
    )

    private val padBuffers = arrayOfNulls<ShortArray>(16)
    private val padPitches = FloatArray(16) { 1.0f }
    private val activeVoices = CopyOnWriteArrayList<ActiveVoice>()
    @Volatile var masterSampleVolume: Float = 1.0f
    @Volatile var loopingPads: Set<Int> = emptySet()

    // BPM-synced Sampler Roll
    @Volatile var rollActive: Boolean = false
    @Volatile var rollPadIndex: Int = 0
    @Volatile var rollBeats: Float = 1f
    @Volatile var rollBpm: Float = 128f
    private var rollSampleCounter: Int = 0

    init {
        val defaultTypes = DEFAULT_TYPES_16
        defaultTypes.forEachIndexed { idx, type ->
            padBuffers[idx] = synthesizeSfx(type, sampleRate)
        }
    }

    fun setCustomSample(padIndex: Int, pcmStereo: ShortArray) {
        if (padIndex in 0..15) {
            padBuffers[padIndex] = pcmStereo
        }
    }

    fun clearCustomSample(padIndex: Int) {
        if (padIndex !in 0..15) return
        padBuffers[padIndex] = synthesizeSfx(DEFAULT_TYPES_16[padIndex], sampleRate)
    }

    fun setPadPitch(padIndex: Int, pitchRatio: Float) {
        if (padIndex in 0..15) {
            padPitches[padIndex] = pitchRatio.coerceIn(0.5f, 2.0f)
        }
    }

    fun getPadPitch(padIndex: Int): Float = if (padIndex in 0..15) padPitches[padIndex] else 1.0f

    fun triggerPad(
        padIndex: Int,
        volume: Float = 0.9f,
        loop: Boolean = padIndex in loopingPads,
        pitchRatio: Float = padPitches.getOrElse(padIndex) { 1.0f }
    ) {
        if (padIndex !in 0..15) return
        val buf = padBuffers[padIndex] ?: return
        if (activeVoices.size >= 10) {
            activeVoices.removeAt(0)
        }
        activeVoices.add(
            ActiveVoice(
                padIndex = padIndex,
                pcmStereo = buf,
                framePos = 0.0,
                volume = volume.coerceIn(0f, 1.5f),
                pitchRatio = pitchRatio.coerceIn(0.5f, 2.0f),
                loop = loop
            )
        )
    }

    fun togglePadLoop(padIndex: Int): Boolean {
        val next = if (padIndex in loopingPads) loopingPads - padIndex else loopingPads + padIndex
        loopingPads = next
        activeVoices.filter { it.padIndex == padIndex }.forEach { it.loop = padIndex in next }
        return padIndex in next
    }

    fun startSamplerRoll(padIndex: Int, beats: Float, bpm: Float) {
        rollPadIndex = padIndex.coerceIn(0, 15)
        rollBeats = beats.coerceIn(0.25f, 16f)
        rollBpm = bpm.coerceIn(60f, 200f)
        rollSampleCounter = 0
        rollActive = true
        triggerPad(rollPadIndex, 0.95f, loop = false)
    }

    fun stopSamplerRoll() {
        rollActive = false
    }

    fun stopPad(padIndex: Int) {
        activeVoices.removeAll { it.padIndex == padIndex }
    }

    fun stopAll() {
        rollActive = false
        activeVoices.clear()
    }

    fun activePadIndices(): Set<Int> = activeVoices.map { it.padIndex }.toSet()

    /**
     * Renders active SFX / Sampler voices into an isolated stereo float buffer for Channel 3 DSP processing.
     */
    fun renderInto(outStereo: FloatArray, frameCount: Int) {
        outStereo.fill(0f, 0, frameCount * 2)

        if (rollActive) {
            val intervalFrames = ((60.0 / rollBpm.coerceAtLeast(60f)) * rollBeats * sampleRate).toInt().coerceAtLeast(2205)
            rollSampleCounter += frameCount
            if (rollSampleCounter >= intervalFrames) {
                rollSampleCounter -= intervalFrames
                triggerPad(rollPadIndex, 0.95f, loop = false)
            }
        }

        if (activeVoices.isEmpty()) return
        val mVol = masterSampleVolume.coerceIn(0f, 1.5f)
        val iter = activeVoices.iterator()
        while (iter.hasNext()) {
            val voice = iter.next()
            val buf = voice.pcmStereo
            val totalFrames = buf.size / 2
            if (totalFrames <= 0) {
                activeVoices.remove(voice)
                continue
            }
            var pos = voice.framePos
            val step = voice.pitchRatio.toDouble()
            val vol = (voice.volume * mVol) / 32768f

            var f = 0
            while (f < frameCount) {
                if (pos >= totalFrames) {
                    if (voice.loop) {
                        pos = 0.0
                    } else {
                        break
                    }
                }
                val idx0 = pos.toInt().coerceIn(0, totalFrames - 1)
                val idx = idx0 * 2
                outStereo[f * 2] += buf[idx] * vol
                outStereo[f * 2 + 1] += buf[idx + 1] * vol
                pos += step
                f++
            }
            voice.framePos = pos
            if (pos >= totalFrames && !voice.loop) {
                activeVoices.remove(voice)
            }
        }
    }

    fun mixInto(outStereo: FloatArray, frameCount: Int) {
        val temp = FloatArray(frameCount * 2)
        renderInto(temp, frameCount)
        for (i in 0 until frameCount * 2) {
            outStereo[i] += temp[i]
        }
    }

    companion object {
        val DEFAULT_TYPES_16 = listOf(
            "AIRHORN", "GUNSHOT", "LASER", "POLICE_SIREN",
            "PUNCH", "SIREN", "SCRATCH", "CROWD",
            "DRUM", "BASS_DROP", "CLAP", "DJ_VOICE",
            "SNARE_SNAP", "RISER_SWEEP", "IMPACT_BOOM", "DJ_DROP_TAG"
        )

        val PAD_CATEGORIES = listOf(
            "All", "Drums", "Kicks", "Snares", "Claps", "Bass",
            "Risers", "Impacts", "Vocal FX", "Crowd", "Siren", "Scratch", "DJ Drops"
        )

        fun categoryForType(type: String): String = when (type) {
            "DRUM" -> "Kicks"
            "SNARE_SNAP" -> "Snares"
            "CLAP" -> "Claps"
            "BASS_DROP" -> "Bass"
            "RISER_SWEEP", "LASER" -> "Risers"
            "PUNCH", "GUNSHOT", "IMPACT_BOOM" -> "Impacts"
            "DJ_VOICE" -> "Vocal FX"
            "CROWD" -> "Crowd"
            "AIRHORN", "POLICE_SIREN", "SIREN" -> "Siren"
            "SCRATCH" -> "Scratch"
            "DJ_DROP_TAG" -> "DJ Drops"
            else -> "Drums"
        }

        fun synthesizeSfx(type: String, sr: Int): ShortArray {
            val durSec = when (type) {
                "AIRHORN" -> 1.15f
                "GUNSHOT" -> 0.45f
                "LASER" -> 0.55f
                "POLICE_SIREN" -> 1.4f
                "PUNCH" -> 0.30f
                "SIREN" -> 1.3f
                "SCRATCH" -> 0.55f
                "CROWD" -> 1.6f
                "DRUM" -> 0.35f
                "BASS_DROP" -> 1.4f
                "CLAP" -> 0.28f
                "SNARE_SNAP" -> 0.26f
                "RISER_SWEEP" -> 1.5f
                "IMPACT_BOOM" -> 0.9f
                "DJ_DROP_TAG" -> 0.95f
                else -> 0.85f
            }
            val frames = (sr * durSec).toInt()
            val out = ShortArray(frames * 2)
            val rng = Random(type.hashCode())
            var phase1 = 0.0
            var phase2 = 0.0
            var phase3 = 0.0

            for (i in 0 until frames) {
                val t = i.toFloat() / sr
                val norm = i.toFloat() / frames
                val sample: Float = when (type) {
                    "AIRHORN" -> {
                        val env = if (t < 0.02f) t / 0.02f else if (norm > 0.82f) (1f - norm) / 0.18f else 1f
                        val bend = if (t < 0.08f) 0.94f + 0.06f * (t / 0.08f) else 1f
                        val f1 = 466.16 * bend
                        val f2 = 466.16 * 1.255 * bend
                        val f3 = 466.16 * 1.498 * bend
                        phase1 += f1 / sr; phase2 += f2 / sr; phase3 += f3 / sr
                        val s1 = ((phase1 % 1.0) * 2.0 - 1.0).toFloat()
                        val s2 = ((phase2 % 1.0) * 2.0 - 1.0).toFloat()
                        val s3 = ((phase3 % 1.0) * 2.0 - 1.0).toFloat()
                        (s1 * 0.45f + s2 * 0.35f + s3 * 0.3f) * env * 0.75f
                    }
                    "GUNSHOT" -> {
                        val env = exp(-t * 11.0).toFloat()
                        val noise = (rng.nextFloat() * 2f - 1f)
                        val thump = sin(2.0 * PI * (150.0 * exp(-t * 18.0) + 42.0) * t).toFloat()
                        (noise * 0.7f + thump * 0.6f) * env * 0.95f
                    }
                    "LASER" -> {
                        val freq = 1800.0 * exp(-t * 6.5) + 120.0
                        phase1 += 2.0 * PI * freq / sr
                        sin(phase1).toFloat() * (1f - norm) * 0.85f
                    }
                    "POLICE_SIREN" -> {
                        val freq = 660.0 + 380.0 * sin(2.0 * PI * 2.8 * t)
                        phase1 += 2.0 * PI * freq / sr
                        val env = if (norm > 0.88f) (1f - norm) / 0.12f else 1f
                        (sin(phase1).toFloat() * 0.7f + sin(phase1 * 2.0).toFloat() * 0.2f) * env * 0.7f
                    }
                    "PUNCH" -> {
                        val env = exp(-t * 14.0).toFloat()
                        val freq = 180.0 * exp(-t * 22.0) + 48.0
                        phase1 += 2.0 * PI * freq / sr
                        (sin(phase1).toFloat() * 0.85f + (rng.nextFloat() * 2f - 1f) * 0.25f * exp(-t * 30.0).toFloat()) * env
                    }
                    "SIREN" -> {
                        val freq = 420.0 + 780.0 * norm
                        phase1 += 2.0 * PI * freq / sr
                        val env = if (norm > 0.9f) (1f - norm) / 0.1f else 1f
                        sin(phase1).toFloat() * env * 0.72f
                    }
                    "SCRATCH" -> {
                        val mod = sin(2.0 * PI * 7.5 * t)
                        val freq = 280.0 + 520.0 * mod * mod
                        phase1 += 2.0 * PI * freq / sr
                        val env = sin(PI * norm).toFloat()
                        ((phase1 / (2.0 * PI) % 1.0) * 2.0 - 1.0).toFloat() * env * 0.75f
                    }
                    "CROWD" -> {
                        val env = sin(PI * norm).toFloat()
                        val raw = (rng.nextFloat() * 2f - 1f)
                        phase1 = phase1 * 0.82 + raw * 0.18
                        (phase1.toFloat() * 1.8f + sin(2.0 * PI * 520.0 * t).toFloat() * 0.05f) * env * 0.85f
                    }
                    "DRUM" -> {
                        val env = exp(-t * 9.5).toFloat()
                        val freq = 140.0 * exp(-t * 25.0) + 46.0
                        phase1 += 2.0 * PI * freq / sr
                        sin(phase1).toFloat() * env * 0.95f
                    }
                    "BASS_DROP" -> {
                        val env = exp(-t * 1.9).toFloat()
                        val freq = 115.0 * exp(-t * 2.4) + 32.0
                        phase1 += 2.0 * PI * freq / sr
                        (sin(phase1).toFloat() * 0.9f + sin(phase1 * 2.0).toFloat() * 0.18f) * env * 0.95f
                    }
                    "CLAP" -> {
                        val burst = if (t < 0.035f && ((t * 1000f).toInt() % 11) < 5) 1f else exp(-t * 18.0).toFloat()
                        (rng.nextFloat() * 2f - 1f) * burst * 0.85f
                    }
                    "SNARE_SNAP" -> {
                        val tone = sin(2.0 * PI * (195.0 * exp(-t * 18.0) + 120.0) * t).toFloat() * exp(-t * 22.0).toFloat()
                        val noise = (rng.nextFloat() * 2f - 1f) * exp(-t * 14.0).toFloat()
                        (tone * 0.55f + noise * 0.65f) * 0.9f
                    }
                    "RISER_SWEEP" -> {
                        val freq = 220.0 + 1800.0 * norm * norm
                        phase1 += 2.0 * PI * freq / sr
                        val n = (rng.nextFloat() * 2f - 1f) * 0.35f
                        (sin(phase1).toFloat() * 0.55f + n) * norm * 0.85f
                    }
                    "IMPACT_BOOM" -> {
                        val sub = sin(2.0 * PI * (95.0 * exp(-t * 5.0) + 36.0) * t).toFloat()
                        val crash = (rng.nextFloat() * 2f - 1f) * exp(-t * 4.5).toFloat() * 0.45f
                        (sub * exp(-t * 2.5).toFloat() * 0.85f + crash) * 0.95f
                    }
                    "DJ_DROP_TAG" -> {
                        val subEnv = sin(PI * norm).toFloat()
                        phase1 += 2.0 * PI * (261.63 * (1.0 + 0.01 * sin(2.0 * PI * 5.0 * t))) / sr
                        phase2 += 2.0 * PI * 329.63 / sr
                        phase3 += 2.0 * PI * 392.00 / sr
                        val gate = if (((t * 10f).toInt() % 2) == 0) 1.0f else 0.55f
                        (sin(phase1) * 0.5 + sin(phase2) * 0.4 + sin(phase3) * 0.4).toFloat() * subEnv * gate * 0.72f
                    }
                    else -> {
                        val env = sin(PI * norm).toFloat()
                        val vibrato = 1.0 + 0.015 * sin(2.0 * PI * 6.0 * t)
                        phase1 += 2.0 * PI * 330.0 * vibrato / sr
                        phase2 += 2.0 * PI * 415.3 * vibrato / sr
                        phase3 += 2.0 * PI * 493.88 * vibrato / sr
                        (sin(phase1) + sin(phase2) * 0.8 + sin(phase3) * 0.7).toFloat() * 0.32f * env
                    }
                }
                val s = (sample.coerceIn(-1f, 1f) * 32767f).toInt().toShort()
                out[i * 2] = s
                out[i * 2 + 1] = s
            }
            return out
        }
    }
}
