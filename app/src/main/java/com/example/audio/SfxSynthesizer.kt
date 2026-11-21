package com.example.audio

import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * Polyphonic real-time SFX & Sampler engine.
 * Synthesizes all 12 classic DJ SFX in 44.1kHz stereo PCM and also supports custom loaded PCM samples.
 */
class SfxSamplerEngine(private val sampleRate: Int = 44100) {

    data class ActiveVoice(
        val padIndex: Int,
        val pcmStereo: ShortArray,
        var framePos: Int = 0,
        val volume: Float = 0.9f
    )

    private val padBuffers = arrayOfNulls<ShortArray>(12)
    private val activeVoices = CopyOnWriteArrayList<ActiveVoice>()

    init {
        val defaultTypes = listOf(
            "AIRHORN", "GUNSHOT", "LASER", "POLICE_SIREN",
            "PUNCH", "SIREN", "SCRATCH", "CROWD",
            "DRUM", "BASS_DROP", "CLAP", "DJ_VOICE"
        )
        defaultTypes.forEachIndexed { idx, type ->
            padBuffers[idx] = synthesizeSfx(type, sampleRate)
        }
    }

    fun setCustomSample(padIndex: Int, pcmStereo: ShortArray) {
        if (padIndex in 0..11) {
            padBuffers[padIndex] = pcmStereo
        }
    }

    fun triggerPad(padIndex: Int, volume: Float = 0.9f) {
        if (padIndex !in 0..11) return
        val buf = padBuffers[padIndex] ?: return
        // Allow multiple simultaneous effects (polyphony up to 8 voices)
        if (activeVoices.size >= 8) {
            activeVoices.removeAt(0)
        }
        activeVoices.add(ActiveVoice(padIndex, buf, 0, volume.coerceIn(0f, 1.5f)))
    }

    fun stopPad(padIndex: Int) {
        activeVoices.removeAll { it.padIndex == padIndex }
    }

    fun stopAll() {
        activeVoices.clear()
    }

    fun activePadIndices(): Set<Int> = activeVoices.map { it.padIndex }.toSet()

    /**
     * Mixes all active SFX voices into the stereo master mix buffer (zero allocations).
     */
    fun mixInto(outStereo: FloatArray, frameCount: Int) {
        if (activeVoices.isEmpty()) return
        val iter = activeVoices.iterator()
        while (iter.hasNext()) {
            val voice = iter.next()
            val buf = voice.pcmStereo
            val totalFrames = buf.size / 2
            var pos = voice.framePos
            val vol = voice.volume / 32768f

            var f = 0
            while (f < frameCount && pos < totalFrames) {
                val idx = pos * 2
                outStereo[f * 2] += buf[idx] * vol
                outStereo[f * 2 + 1] += buf[idx + 1] * vol
                pos++
                f++
            }
            voice.framePos = pos
            if (pos >= totalFrames) {
                activeVoices.remove(voice)
            }
        }
    }

    companion object {
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
                        // Classic Jamaican/Club brass detuned saw stack
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
                        val env = (1f - norm)
                        sin(phase1).toFloat() * env * 0.85f
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
                        // Vinyl chirp scratch
                        val mod = sin(2.0 * PI * 7.5 * t)
                        val freq = 280.0 + 520.0 * mod * mod
                        phase1 += 2.0 * PI * freq / sr
                        val env = sin(PI * norm).toFloat()
                        ((phase1 / (2.0 * PI) % 1.0) * 2.0 - 1.0).toFloat() * env * 0.75f
                    }
                    "CROWD" -> {
                        // Filtered stadium cheer swell
                        val env = sin(PI * norm).toFloat()
                        val raw = (rng.nextFloat() * 2f - 1f)
                        phase1 = phase1 * 0.82 + raw * 0.18
                        (phase1.toFloat() * 1.8f + sin(2.0 * PI * 520.0 * t).toFloat() * 0.05f) * env * 0.85f
                    }
                    "DRUM" -> {
                        // Punchy 808 Club Kick
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
                    else -> {
                        // DJ Voice synth chord stab ("B-I-J-O-Y!")
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
