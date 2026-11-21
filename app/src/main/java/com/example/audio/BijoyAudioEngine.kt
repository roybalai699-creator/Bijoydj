package com.example.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.os.Process
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Real low-latency dual-deck DJ mixing engine built on Android AudioTrack (PERFORMANCE_MODE_LOW_LATENCY).
 * Simultaneously mixes Deck A, Deck B, and Polyphonic SFX Sampler with real-time per-deck EQ, filter,
 * DJ effects, crossfader curves, pitch/tempo resampling, scratch scrubbing, loops, hot cues, and WAV recording.
 */
class BijoyAudioEngine(
    context: Context,
    val recordingEngine: RecordingEngine
) {
    private val sampleRate = AudioDecoder.TARGET_SAMPLE_RATE
    private val bufferFrames = 512

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    // Reactive UI state flows
    private val _deckA = MutableStateFlow(DeckState(DeckId.A))
    val deckA: StateFlow<DeckState> = _deckA.asStateFlow()

    private val _deckB = MutableStateFlow(DeckState(DeckId.B))
    val deckB: StateFlow<DeckState> = _deckB.asStateFlow()

    private val _mixer = MutableStateFlow(MixerState())
    val mixer: StateFlow<MixerState> = _mixer.asStateFlow()

    val sfxEngine = SfxSamplerEngine(sampleRate)

    // Real-time internal deck playback state (accessed by urgent audio thread)
    private class DeckRuntime {
        @Volatile var pcmStereo: ShortArray = ShortArray(0)
        @Volatile var totalFrames: Int = 0
        @Volatile var playheadFrame: Double = 0.0
        @Volatile var slipGhostFrame: Double = 0.0
        @Volatile var isPlaying: Boolean = false
        @Volatile var pitchRatio: Double = 1.0
        @Volatile var pitchBendOffset: Double = 0.0
        @Volatile var scratchVelocity: Double = 0.0
        @Volatile var isScratching: Boolean = false
        @Volatile var slipMode: Boolean = false
        @Volatile var loopActive: Boolean = false
        @Volatile var loopStartFrame: Double = 0.0
        @Volatile var loopEndFrame: Double = 0.0
        @Volatile var cuePointFrame: Double = 0.0
        @Volatile var volume: Float = 0.85f
        @Volatile var reverseActive: Boolean = false
        @Volatile var brakeActive: Boolean = false
        @Volatile var brakeSpeed: Double = 1.0
        @Volatile var jogAngleDeg: Float = 0f
        @Volatile var originalBpm: Float = 128f
        val dsp = ChannelDspProcessor(44100f)
    }

    private val rtA = DeckRuntime()
    private val rtB = DeckRuntime()
    private val masterDsp = ChannelDspProcessor(44100f)
    private val spectrumAnalyzer = BpmAndWaveformAnalyzer.RealtimeSpectrumAnalyzer(sampleRate)

    // Mixer runtime params
    @Volatile private var masterVolume: Float = 0.85f
    @Volatile private var crossfader: Float = 0f // -1..+1
    @Volatile private var crossfaderCurve: CrossfaderCurve = CrossfaderCurve.CONSTANT_POWER
    @Volatile private var autoFadeActive: Boolean = false
    @Volatile private var autoFadeTarget: Float = 1f
    @Volatile private var autoFadeDurationSec: Float = 4.0f
    @Volatile private var jogSensitivity: Float = 1.0f

    private val isRunning = AtomicBoolean(false)
    private var audioThread: Thread? = null
    private var audioTrack: AudioTrack? = null

    fun startEngine() {
        if (!isRunning.compareAndSet(false, true)) return

        audioThread = Thread({
            try {
                Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
            } catch (_: Exception) {
            }
            runAudioLoop()
        }, "BijoyDjAudioEngine").apply {
            isDaemon = true
            start()
        }
    }

    fun stopEngine() {
        isRunning.set(false)
        try {
            audioThread?.join(500)
        } catch (_: Exception) {
        }
        audioThread = null
        releaseAudioTrack()
    }

    private fun createAudioTrack(): AudioTrack? {
        return try {
            val minBufBytes = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(bufferFrames * 4 * 2)

            val builder = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                        .build()
                )
                .setBufferSizeInBytes(minBufBytes)
                .setTransferMode(AudioTrack.MODE_STREAM)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                builder.setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            }
            builder.build()
        } catch (_: Exception) {
            null
        }
    }

    private fun releaseAudioTrack() {
        try {
            audioTrack?.pause()
            audioTrack?.flush()
            audioTrack?.release()
        } catch (_: Exception) {
        }
        audioTrack = null
    }

    private fun runAudioLoop() {
        val track = createAudioTrack()
        audioTrack = track
        try {
            track?.play()
        } catch (_: Exception) {
        }

        // Pre-allocated audio buffers (zero allocation in audio loop)
        val mixFloat = FloatArray(bufferFrames * 2)
        val outShort = ShortArray(bufferFrames * 2)
        val dspPair = FloatArray(2)
        val spectrumBands = FloatArray(16)
        var telemetryCounter = 0

        while (isRunning.get()) {
            // 1. Update Auto-Fade if active
            if (autoFadeActive) {
                val stepPerBuf = (2f / (autoFadeDurationSec.coerceAtLeast(0.5f) * (sampleRate.toFloat() / bufferFrames)))
                val diff = autoFadeTarget - crossfader
                if (abs(diff) <= stepPerBuf) {
                    crossfader = autoFadeTarget
                    autoFadeActive = false
                } else {
                    crossfader += if (diff > 0f) stepPerBuf else -stepPerBuf
                }
            }

            // 2. Compute Crossfader Gains for Deck A & Deck B
            val (cfGainA, cfGainB) = computeCrossfaderGains(crossfader, crossfaderCurve)

            // 3. Render Deck A & Deck B + Mix
            var peakAL = 0f; var peakAR = 0f
            var peakBL = 0f; var peakBR = 0f

            for (f in 0 until bufferFrames) {
                // Render Deck A sample
                renderDeckSample(rtA, dspPair)
                val aL = dspPair[0] * rtA.volume
                val aR = dspPair[1] * rtA.volume
                if (abs(aL) > peakAL) peakAL = abs(aL)
                if (abs(aR) > peakAR) peakAR = abs(aR)

                // Render Deck B sample
                renderDeckSample(rtB, dspPair)
                val bL = dspPair[0] * rtB.volume
                val bR = dspPair[1] * rtB.volume
                if (abs(bL) > peakBL) peakBL = abs(bL)
                if (abs(bR) > peakBR) peakBR = abs(bR)

                mixFloat[f * 2] = aL * cfGainA + bL * cfGainB
                mixFloat[f * 2 + 1] = aR * cfGainA + bR * cfGainB
            }

            // 4. Mix Polyphonic SFX Sampler voices
            sfxEngine.mixInto(mixFloat, bufferFrames)

            // 5. Master DSP + Master Volume + Soft Clip -> ShortArray
            var peakML = 0f; var peakMR = 0f
            val mVol = masterVolume
            for (f in 0 until bufferFrames) {
                masterDsp.processStereo(mixFloat[f * 2], mixFloat[f * 2 + 1], dspPair)
                val mL = ChannelDspProcessor.softClip(dspPair[0] * mVol)
                val mR = ChannelDspProcessor.softClip(dspPair[1] * mVol)
                mixFloat[f * 2] = mL
                mixFloat[f * 2 + 1] = mR
                if (abs(mL) > peakML) peakML = abs(mL)
                if (abs(mR) > peakMR) peakMR = abs(mR)

                outShort[f * 2] = (mL * 32767f).toInt().coerceIn(-32768, 32767).toShort()
                outShort[f * 2 + 1] = (mR * 32767f).toInt().coerceIn(-32768, 32767).toShort()
            }

            // 6. Tap Master Output into Recording Engine
            recordingEngine.writeMasterPcm(outShort, bufferFrames * 2)

            // 7. Write to Low-Latency AudioTrack
            if (track != null) {
                try {
                    track.write(outShort, 0, outShort.size)
                } catch (_: Exception) {
                }
            } else {
                try {
                    Thread.sleep(11)
                } catch (_: InterruptedException) {
                    break
                }
            }

            // 8. Publish Telemetry (~30fps = every 3 buffers)
            telemetryCounter++
            if (telemetryCounter >= 3) {
                telemetryCounter = 0
                spectrumAnalyzer.analyze(mixFloat, bufferFrames, spectrumBands)
                publishTelemetry(peakAL, peakAR, peakBL, peakBR, peakML, peakMR, spectrumBands)
            }
        }
        releaseAudioTrack()
    }

    private fun renderDeckSample(rt: DeckRuntime, outPair: FloatArray) {
        val pcm = rt.pcmStereo
        val total = rt.totalFrames
        if (total <= 1 || (!rt.isPlaying && !rt.isScratching)) {
            outPair[0] = 0f
            outPair[1] = 0f
            return
        }

        // Compute instantaneous playback step
        val step: Double = when {
            rt.isScratching -> {
                val vel = rt.scratchVelocity
                rt.scratchVelocity *= 0.996 // smooth inertia decay
                vel
            }
            rt.brakeActive -> {
                rt.brakeSpeed = (rt.brakeSpeed - 0.000045).coerceAtLeast(0.0)
                rt.pitchRatio * rt.brakeSpeed
            }
            else -> {
                rt.brakeSpeed = 1.0
                val dir = if (rt.reverseActive) -1.0 else 1.0
                dir * (rt.pitchRatio + rt.pitchBendOffset).coerceIn(0.4, 2.0)
            }
        }

        // Advance playhead + slip ghost
        rt.playheadFrame += step
        if (rt.slipMode && rt.isPlaying) {
            rt.slipGhostFrame += rt.pitchRatio
            if (rt.slipGhostFrame >= total) rt.slipGhostFrame -= total
        } else {
            rt.slipGhostFrame = rt.playheadFrame
        }

        // Loop boundary enforcement
        if (rt.loopActive && rt.loopEndFrame > rt.loopStartFrame + 100.0) {
            if (rt.playheadFrame >= rt.loopEndFrame) {
                rt.playheadFrame = rt.loopStartFrame
            } else if (rt.playheadFrame < rt.loopStartFrame) {
                rt.playheadFrame = rt.loopEndFrame - 1.0
            }
        } else {
            if (rt.playheadFrame >= total - 1) {
                // Seamless wrap for continuous DJ sets
                rt.playheadFrame = 0.0
            } else if (rt.playheadFrame < 0.0) {
                rt.playheadFrame = (total - 2).toDouble()
            }
        }

        // Advance Jog Platter visual rotation (~33.3 RPM = 200 deg/sec)
        rt.jogAngleDeg = (rt.jogAngleDeg + (step * 200.0 / sampleRate).toFloat()) % 360f

        // Linear interpolation resampling
        val idx0 = rt.playheadFrame.toInt().coerceIn(0, total - 2)
        val idx1 = idx0 + 1
        val frac = (rt.playheadFrame - idx0).toFloat().coerceIn(0f, 1f)

        val l0 = pcm[idx0 * 2] / 32768f
        val r0 = pcm[idx0 * 2 + 1] / 32768f
        val l1 = pcm[idx1 * 2] / 32768f
        val r1 = pcm[idx1 * 2 + 1] / 32768f

        val rawL = l0 + (l1 - l0) * frac
        val rawR = r0 + (r1 - r0) * frac

        rt.dsp.processStereo(rawL, rawR, outPair)
    }

    private fun publishTelemetry(
        aL: Float, aR: Float,
        bL: Float, bR: Float,
        mL: Float, mR: Float,
        bands: FloatArray
    ) {
        val posAMs = ((rtA.playheadFrame / sampleRate) * 1000.0).toLong().coerceAtLeast(0L)
        val beatA = computeBeatCounter(posAMs, rtA.originalBpm)
        _deckA.update {
            it.copy(
                positionMs = posAMs,
                jogAngleDeg = rtA.jogAngleDeg,
                levelLeft = aL.coerceIn(0f, 1f),
                levelRight = aR.coerceIn(0f, 1f),
                beatCount = beatA
            )
        }

        val posBMs = ((rtB.playheadFrame / sampleRate) * 1000.0).toLong().coerceAtLeast(0L)
        val beatB = computeBeatCounter(posBMs, rtB.originalBpm)
        _deckB.update {
            it.copy(
                positionMs = posBMs,
                jogAngleDeg = rtB.jogAngleDeg,
                levelLeft = bL.coerceIn(0f, 1f),
                levelRight = bR.coerceIn(0f, 1f),
                beatCount = beatB
            )
        }

        _mixer.update {
            it.copy(
                crossfader = crossfader,
                autoFadeActive = autoFadeActive,
                masterLevelLeft = mL.coerceIn(0f, 1f),
                masterLevelRight = mR.coerceIn(0f, 1f),
                spectrumBands = bands.toList()
            )
        }
    }

    private fun computeBeatCounter(posMs: Long, bpm: Float): Int {
        if (bpm <= 1f) return 1
        val beatDurMs = 60000f / bpm
        return ((posMs / beatDurMs).toInt() % 4) + 1
    }

    // ==================== PUBLIC DECK CONTROLS ====================

    private fun rt(id: DeckId) = if (id == DeckId.A) rtA else rtB
    private fun flow(id: DeckId) = if (id == DeckId.A) _deckA else _deckB

    fun loadDecodedTrack(
        deckId: DeckId,
        songId: Long,
        title: String,
        artist: String,
        artworkResName: String?,
        decoded: DecodedAudio,
        savedCues: List<HotCuePoint>
    ) {
        val r = rt(deckId)
        r.isPlaying = false
        r.pcmStereo = decoded.pcmStereo
        r.totalFrames = decoded.pcmStereo.size / 2
        r.playheadFrame = 0.0
        r.slipGhostFrame = 0.0
        r.cuePointFrame = 0.0
        r.loopActive = false
        r.originalBpm = decoded.detectedBpm
        r.pitchRatio = 1.0
        r.dsp.reset()

        flow(deckId).update {
            it.copy(
                songId = songId,
                title = title,
                artist = artist,
                artworkResName = artworkResName,
                isLoaded = true,
                isPlaying = false,
                positionMs = 0L,
                durationMs = decoded.durationMs,
                originalBpm = decoded.detectedBpm,
                effectiveBpm = decoded.detectedBpm,
                pitchPercent = 0f,
                loopActive = false,
                loopInMs = null,
                loopOutMs = null,
                hotCues = savedCues,
                waveform = decoded.waveform
            )
        }
    }

    fun togglePlayPause(deckId: DeckId) {
        val r = rt(deckId)
        if (r.totalFrames <= 0) return
        val next = !r.isPlaying
        r.isPlaying = next
        flow(deckId).update { it.copy(isPlaying = next) }
    }

    fun setPlaying(deckId: DeckId, playing: Boolean) {
        val r = rt(deckId)
        if (r.totalFrames <= 0) return
        r.isPlaying = playing
        flow(deckId).update { it.copy(isPlaying = playing) }
    }

    fun triggerCue(deckId: DeckId) {
        val r = rt(deckId)
        if (r.totalFrames <= 0) return
        if (!r.isPlaying) {
            r.cuePointFrame = r.playheadFrame
        } else {
            r.isPlaying = false
            r.playheadFrame = r.cuePointFrame
            flow(deckId).update { it.copy(isPlaying = false) }
        }
    }

    fun seekToRatio(deckId: DeckId, ratio: Float) {
        val r = rt(deckId)
        if (r.totalFrames <= 0) return
        val clamped = ratio.coerceIn(0f, 0.999f)
        r.playheadFrame = clamped.toDouble() * r.totalFrames
        r.slipGhostFrame = r.playheadFrame
    }

    fun setPitchPercent(deckId: DeckId, percent: Float) {
        val clamped = percent.coerceIn(-16f, 16f)
        val r = rt(deckId)
        r.pitchRatio = 1.0 + (clamped / 100.0)
        flow(deckId).update {
            it.copy(
                pitchPercent = clamped,
                effectiveBpm = it.originalBpm * r.pitchRatio.toFloat()
            )
        }
    }

    fun pitchBend(deckId: DeckId, delta: Double) {
        rt(deckId).pitchBendOffset = delta.coerceIn(-0.15, 0.15)
    }

    fun setManualBpm(deckId: DeckId, newBpm: Float) {
        val clamped = newBpm.coerceIn(60f, 200f)
        val r = rt(deckId)
        r.originalBpm = clamped
        flow(deckId).update {
            it.copy(
                originalBpm = clamped,
                effectiveBpm = clamped * r.pitchRatio.toFloat()
            )
        }
    }

    fun toggleSync(deckId: DeckId) {
        val targetDeck = flow(deckId).value
        val otherId = if (deckId == DeckId.A) DeckId.B else DeckId.A
        val refDeck = flow(otherId).value
        val nextSync = !targetDeck.syncActive

        if (nextSync && refDeck.effectiveBpm > 40f && targetDeck.originalBpm > 40f) {
            val requiredRatio = (refDeck.effectiveBpm / targetDeck.originalBpm).coerceIn(0.84f, 1.16f)
            val newPitchPct = (requiredRatio - 1f) * 100f
            setPitchPercent(deckId, newPitchPct)

            // Phase-align beat position with reference deck
            val refBeatMs = 60000.0 / refDeck.effectiveBpm
            val refPhase = (refDeck.positionMs % refBeatMs) / refBeatMs
            val targetBeatFrames = (60.0 / targetDeck.originalBpm) * sampleRate
            val r = rt(deckId)
            val currentBeatNum = (r.playheadFrame / targetBeatFrames).toInt()
            val alignedFrame = (currentBeatNum + refPhase) * targetBeatFrames
            if (r.totalFrames > 0) {
                r.playheadFrame = alignedFrame.coerceIn(0.0, (r.totalFrames - 2).toDouble())
            }
        }
        flow(deckId).update { it.copy(syncActive = nextSync) }
    }

    fun toggleKeyLock(deckId: DeckId) {
        flow(deckId).update { it.copy(keyLock = !it.keyLock) }
    }

    fun toggleScratchMode(deckId: DeckId) {
        flow(deckId).update { it.copy(scratchMode = !it.scratchMode) }
    }

    fun toggleSlipMode(deckId: DeckId) {
        val r = rt(deckId)
        val next = !r.slipMode
        r.slipMode = next
        if (!next) {
            r.slipGhostFrame = r.playheadFrame
        }
        flow(deckId).update { it.copy(slipMode = next) }
    }

    fun toggleHeadphoneCue(deckId: DeckId) {
        flow(deckId).update { it.copy(headphoneCue = !it.headphoneCue) }
    }

    fun onJogTouchStart(deckId: DeckId) {
        val r = rt(deckId)
        if (flow(deckId).value.scratchMode) {
            r.isScratching = true
            r.scratchVelocity = 0.0
        }
    }

    fun onJogDrag(deckId: DeckId, deltaAngleDeg: Float) {
        val r = rt(deckId)
        val sens = jogSensitivity.coerceIn(0.4f, 2.5f)
        if (flow(deckId).value.scratchMode) {
            r.isScratching = true
            // Translate angular gesture to real PCM scratch speed
            r.scratchVelocity = (deltaAngleDeg * 0.22 * sens).coerceIn(-6.0, 6.0)
        } else {
            // Pitch nudge mode
            r.pitchBendOffset = (deltaAngleDeg * 0.006 * sens).coerceIn(-0.12, 0.12)
        }
    }

    fun onJogTouchEnd(deckId: DeckId) {
        val r = rt(deckId)
        r.isScratching = false
        r.scratchVelocity = 0.0
        r.pitchBendOffset = 0.0
        if (r.slipMode && r.isPlaying) {
            r.playheadFrame = r.slipGhostFrame
        }
    }

    // ==================== LOOPS & HOT CUES ====================

    fun setLoopIn(deckId: DeckId) {
        val r = rt(deckId)
        r.loopStartFrame = r.playheadFrame
        val ms = ((r.loopStartFrame / sampleRate) * 1000.0).toLong()
        flow(deckId).update { it.copy(loopInMs = ms) }
    }

    fun setLoopOut(deckId: DeckId) {
        val r = rt(deckId)
        if (r.playheadFrame > r.loopStartFrame + 2000.0) {
            r.loopEndFrame = r.playheadFrame
            r.loopActive = true
            val outMs = ((r.loopEndFrame / sampleRate) * 1000.0).toLong()
            flow(deckId).update { it.copy(loopOutMs = outMs, loopActive = true) }
        }
    }

    fun toggleAutoLoop(deckId: DeckId, beats: Float = 4f) {
        val r = rt(deckId)
        if (r.totalFrames <= 0) return
        if (r.loopActive && flow(deckId).value.autoLoopBeats == beats) {
            exitLoop(deckId)
            return
        }
        val beatFrames = (60.0 / r.originalBpm.coerceAtLeast(60f)) * sampleRate
        val start = r.playheadFrame
        val end = min((r.totalFrames - 2).toDouble(), start + beatFrames * beats)
        r.loopStartFrame = start
        r.loopEndFrame = end
        r.loopActive = true
        flow(deckId).update {
            it.copy(
                loopActive = true,
                autoLoopBeats = beats,
                loopInMs = ((start / sampleRate) * 1000.0).toLong(),
                loopOutMs = ((end / sampleRate) * 1000.0).toLong()
            )
        }
    }

    fun exitLoop(deckId: DeckId) {
        val r = rt(deckId)
        r.loopActive = false
        if (r.slipMode && r.isPlaying) {
            r.playheadFrame = r.slipGhostFrame
        }
        flow(deckId).update { it.copy(loopActive = false) }
    }

    fun triggerHotCue(deckId: DeckId, slotIndex: Int): Long? {
        if (slotIndex !in 0..3) return null
        val r = rt(deckId)
        val currentDeck = flow(deckId).value
        val existing = currentDeck.hotCues.find { it.slotIndex == slotIndex }
        return if (existing?.positionMs != null) {
            // Jump to saved Hot Cue & play
            r.playheadFrame = ((existing.positionMs / 1000.0) * sampleRate).coerceIn(0.0, (r.totalFrames - 2).coerceAtLeast(0).toDouble())
            if (!r.isPlaying && r.totalFrames > 0) {
                r.isPlaying = true
                flow(deckId).update { it.copy(isPlaying = true) }
            }
            existing.positionMs
        } else {
            // Set new Hot Cue at current playhead
            val posMs = ((r.playheadFrame / sampleRate) * 1000.0).toLong()
            val updated = currentDeck.hotCues.map {
                if (it.slotIndex == slotIndex) it.copy(positionMs = posMs) else it
            }
            flow(deckId).update { it.copy(hotCues = updated) }
            posMs
        }
    }

    fun clearHotCue(deckId: DeckId, slotIndex: Int) {
        flow(deckId).update { d ->
            d.copy(hotCues = d.hotCues.map {
                if (it.slotIndex == slotIndex) it.copy(positionMs = null) else it
            })
        }
    }

    // ==================== EQ, FILTER, FX & MIXER ====================

    fun setChannelVolume(deckId: DeckId, volume: Float) {
        val v = volume.coerceIn(0f, 1f)
        rt(deckId).volume = v
        flow(deckId).update { it.copy(volume = v) }
    }

    fun updateDeckEq(
        deckId: DeckId,
        gainDb: Float? = null,
        trebleDb: Float? = null,
        midDb: Float? = null,
        bassDb: Float? = null,
        filterCutoff: Float? = null,
        preset: EqPreset? = null
    ) {
        val cur = flow(deckId).value
        val g = (gainDb ?: cur.gainDb).coerceIn(-12f, 12f)
        val t = (trebleDb ?: cur.trebleDb).coerceIn(-12f, 12f)
        val m = (midDb ?: cur.midDb).coerceIn(-12f, 12f)
        val b = (bassDb ?: cur.bassDb).coerceIn(-12f, 12f)
        val f = (filterCutoff ?: cur.filterCutoff).coerceIn(-1f, 1f)
        val p = preset ?: if (trebleDb != null || midDb != null || bassDb != null) EqPreset.CUSTOM else cur.eqPreset

        rt(deckId).dsp.updateEq(g, b, m, t, f)
        flow(deckId).update {
            it.copy(
                gainDb = g,
                trebleDb = t,
                midDb = m,
                bassDb = b,
                filterCutoff = f,
                eqPreset = p
            )
        }
    }

    fun applyEqPreset(deckId: DeckId, preset: EqPreset) {
        updateDeckEq(
            deckId = deckId,
            bassDb = preset.bassDb,
            midDb = preset.midDb,
            trebleDb = preset.trebleDb,
            preset = preset
        )
    }

    fun setDeckEffect(deckId: DeckId, effect: DjEffectType, wet: Float? = null, param: Float? = null) {
        val cur = flow(deckId).value
        val w = (wet ?: cur.effectWet).coerceIn(0f, 1f)
        val p = (param ?: cur.effectParam).coerceIn(0f, 1f)
        val r = rt(deckId)
        r.reverseActive = (effect == DjEffectType.REVERSE)
        r.brakeActive = (effect == DjEffectType.BRAKE)
        if (!r.brakeActive) r.brakeSpeed = 1.0
        r.dsp.updateEffect(effect, w, p)
        flow(deckId).update { it.copy(activeEffect = effect, effectWet = w, effectParam = p) }
    }

    fun setMasterEffect(effect: DjEffectType, wet: Float? = null, param: Float? = null) {
        val cur = _mixer.value
        val w = (wet ?: cur.masterEffectWet).coerceIn(0f, 1f)
        val p = (param ?: cur.masterEffectParam).coerceIn(0f, 1f)
        val activeFx = if (cur.beatFxOn) effect else DjEffectType.NONE
        masterDsp.updateEffect(activeFx, w, p)
        _mixer.update {
            it.copy(
                masterEffect = activeFx,
                beatFxType = if (effect != DjEffectType.NONE) effect else it.beatFxType,
                masterEffectWet = w,
                masterEffectParam = p
            )
        }
    }

    fun setSoundColorFx(type: SoundColorFxType) {
        val param = _mixer.value.colorFxParam
        rtA.dsp.updateColorFx(type, param)
        rtB.dsp.updateColorFx(type, param)
        _mixer.update { it.copy(activeColorFx = type) }
    }

    fun setColorFxParam(param: Float) {
        val p = param.coerceIn(0f, 1f)
        val type = _mixer.value.activeColorFx
        rtA.dsp.updateColorFx(type, p)
        rtB.dsp.updateColorFx(type, p)
        _mixer.update { it.copy(colorFxParam = p) }
    }

    fun toggleBeatFxAssign(deckId: DeckId) {
        flow(deckId).update { it.copy(beatFxAssigned = !it.beatFxAssigned) }
        syncBeatFxToEngine()
    }

    fun cycleBeatFxType() {
        val fxOptions = listOf(
            DjEffectType.ECHO,
            DjEffectType.REVERB,
            DjEffectType.FLANGER,
            DjEffectType.PHASER,
            DjEffectType.DELAY,
            DjEffectType.ROLL,
            DjEffectType.BEAT_REPEAT,
            DjEffectType.DISTORTION
        )
        val current = _mixer.value.beatFxType
        val next = fxOptions[(fxOptions.indexOf(current) + 1).coerceAtLeast(0) % fxOptions.size]
        _mixer.update { it.copy(beatFxType = next) }
        syncBeatFxToEngine()
    }

    fun setBeatFxRatio(ratio: String) {
        val param = when (ratio) {
            "1/2" -> 0.28f
            "1" -> 0.52f
            else -> 0.80f
        }
        _mixer.update { it.copy(beatFxRatio = ratio, masterEffectParam = param) }
        syncBeatFxToEngine()
    }

    fun toggleBeatFxOn() {
        _mixer.update { it.copy(beatFxOn = !it.beatFxOn) }
        syncBeatFxToEngine()
    }

    fun setBeatFxLevel(level: Float) {
        _mixer.update { it.copy(masterEffectWet = level.coerceIn(0f, 1f)) }
        syncBeatFxToEngine()
    }

    private fun syncBeatFxToEngine() {
        val m = _mixer.value
        val fx = if (m.beatFxOn) m.beatFxType else DjEffectType.NONE
        val wet = m.masterEffectWet
        val param = m.masterEffectParam
        masterDsp.updateEffect(fx, wet, param)
        _mixer.update { it.copy(masterEffect = fx) }
    }

    fun setMasterVolume(volume: Float) {
        val v = volume.coerceIn(0f, 1f)
        masterVolume = v
        _mixer.update { it.copy(masterVolume = v) }
    }

    fun setCrossfader(value: Float) {
        val v = value.coerceIn(-1f, 1f)
        crossfader = v
        autoFadeActive = false
        _mixer.update { it.copy(crossfader = v, autoFadeActive = false) }
    }

    fun setCrossfaderCurve(curve: CrossfaderCurve) {
        crossfaderCurve = curve
        _mixer.update { it.copy(crossfaderCurve = curve) }
    }

    fun triggerAutoFade(durationSec: Float = 4.0f) {
        autoFadeDurationSec = durationSec.coerceIn(0.5f, 16f)
        autoFadeTarget = if (crossfader <= 0f) 1f else -1f
        autoFadeActive = !autoFadeActive
        _mixer.update { it.copy(autoFadeActive = autoFadeActive, autoFadeTarget = autoFadeTarget) }
    }

    fun setJogSensitivity(sensitivity: Float) {
        jogSensitivity = sensitivity.coerceIn(0.4f, 2.5f)
    }

    fun setWaveformZoom(deckId: DeckId, zoom: Float) {
        flow(deckId).update { it.copy(waveformZoom = zoom.coerceIn(1f, 4f)) }
    }

    fun swapDecks() {
        // Swap runtime buffers & states cleanly
        val tmpPcm = rtA.pcmStereo; val tmpTotal = rtA.totalFrames; val tmpPos = rtA.playheadFrame
        val tmpPlay = rtA.isPlaying; val tmpPitch = rtA.pitchRatio; val tmpBpm = rtA.originalBpm
        val tmpLoop = rtA.loopActive; val tmpLoopS = rtA.loopStartFrame; val tmpLoopE = rtA.loopEndFrame

        rtA.pcmStereo = rtB.pcmStereo; rtA.totalFrames = rtB.totalFrames; rtA.playheadFrame = rtB.playheadFrame
        rtA.isPlaying = rtB.isPlaying; rtA.pitchRatio = rtB.pitchRatio; rtA.originalBpm = rtB.originalBpm
        rtA.loopActive = rtB.loopActive; rtA.loopStartFrame = rtB.loopStartFrame; rtA.loopEndFrame = rtB.loopEndFrame

        rtB.pcmStereo = tmpPcm; rtB.totalFrames = tmpTotal; rtB.playheadFrame = tmpPos
        rtB.isPlaying = tmpPlay; rtB.pitchRatio = tmpPitch; rtB.originalBpm = tmpBpm
        rtB.loopActive = tmpLoop; rtB.loopStartFrame = tmpLoopS; rtB.loopEndFrame = tmpLoopE

        val stateA = _deckA.value
        val stateB = _deckB.value
        _deckA.value = stateB.copy(deckId = DeckId.A)
        _deckB.value = stateA.copy(deckId = DeckId.B)
    }

    companion object {
        /**
         * Calculates left (Deck A) and right (Deck B) linear amplitude gains from crossfader (-1..+1).
         */
        fun computeCrossfaderGains(position: Float, curve: CrossfaderCurve): Pair<Float, Float> {
            val x = ((position.coerceIn(-1f, 1f) + 1f) * 0.5f) // 0 = full A, 0.5 = center, 1 = full B
            return when (curve) {
                CrossfaderCurve.LINEAR -> (1f - x) to x
                CrossfaderCurve.CONSTANT_POWER -> {
                    val gainA = cos(x * PI * 0.5).toFloat().coerceIn(0f, 1f)
                    val gainB = sin(x * PI * 0.5).toFloat().coerceIn(0f, 1f)
                    gainA to gainB
                }
                CrossfaderCurve.SHARP_CUT -> {
                    val gainA = if (x > 0.90f) ((1f - x) / 0.10f).coerceIn(0f, 1f) else 1f
                    val gainB = if (x < 0.10f) (x / 0.10f).coerceIn(0f, 1f) else 1f
                    gainA to gainB
                }
            }
        }
    }
}
