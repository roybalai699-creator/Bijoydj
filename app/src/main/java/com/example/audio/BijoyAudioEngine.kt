package com.example.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
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
 * Real low-latency 4-Channel + Dual-Deck DJ mixing engine built on Android AudioTrack.
 * - Channel 1: Deck A (PCM + 3/5/10/15-Band EQ + Vocal + Color Filter + 24 FX + Volume + Cue + Channel ON/OFF + Assign)
 * - Channel 2: Deck B (PCM + 3/5/10/15-Band EQ + Vocal + Color Filter + 24 FX + Volume + Cue + Channel ON/OFF + Assign)
 * - Channel 3: Sampler & 16-Pad SFX Bus
 * - Channel 4: Aux / FX Return Bus
 */
class BijoyAudioEngine(
    context: Context,
    val recordingEngine: RecordingEngine
) {
    private val sampleRate = AudioDecoder.TARGET_SAMPLE_RATE
    @Volatile private var bufferFrames = 512

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private var focusRequest: AudioFocusRequest? = null

    private val focusListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                rtA.isPlaying = false
                rtB.isPlaying = false
                _deckA.update { it.copy(isPlaying = false) }
                _deckB.update { it.copy(isPlaying = false) }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                masterVolumeDuck = 0.45f
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                masterVolumeDuck = 1.0f
            }
        }
    }

    private val _deckA = MutableStateFlow(DeckState(DeckId.A))
    val deckA: StateFlow<DeckState> = _deckA.asStateFlow()

    private val _deckB = MutableStateFlow(DeckState(DeckId.B))
    val deckB: StateFlow<DeckState> = _deckB.asStateFlow()

    private val _mixer = MutableStateFlow(MixerState())
    val mixer: StateFlow<MixerState> = _mixer.asStateFlow()

    val sfxEngine = SfxSamplerEngine(sampleRate)

    private class DeckRuntime {
        @Volatile var pcmStereo: ShortArray = ShortArray(0)
        @Volatile var totalFrames: Int = 0
        @Volatile var playheadFrame: Double = 0.0
        @Volatile var slipGhostFrame: Double = 0.0
        @Volatile var isPlaying: Boolean = false
        @Volatile var channelEnabled: Boolean = true
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
        @Volatile var assign: ChannelAssign = ChannelAssign.A
        val dsp = ChannelDspProcessor(44100f)
    }

    private val rtA = DeckRuntime().apply { assign = ChannelAssign.A }
    private val rtB = DeckRuntime().apply { assign = ChannelAssign.B }

    // Channels 3 & 4 real-time DSP buses
    private val ch3Dsp = ChannelDspProcessor(44100f)
    private val ch4Dsp = ChannelDspProcessor(44100f)
    @Volatile private var ch3Enabled: Boolean = true
    @Volatile private var ch4Enabled: Boolean = true
    @Volatile private var ch3Volume: Float = 0.85f
    @Volatile private var ch4Volume: Float = 0.85f
    @Volatile private var ch3Assign: ChannelAssign = ChannelAssign.THRU
    @Volatile private var ch4Assign: ChannelAssign = ChannelAssign.THRU

    private val masterDsp = ChannelDspProcessor(44100f)
    private val spectrumAnalyzer = BpmAndWaveformAnalyzer.RealtimeSpectrumAnalyzer(sampleRate)

    @Volatile private var masterVolume: Float = 0.85f
    @Volatile private var boothVolume: Float = 0.75f
    @Volatile private var masterVolumeDuck: Float = 1.0f
    @Volatile private var crossfader: Float = 0f
    @Volatile private var crossfaderCurve: CrossfaderCurve = CrossfaderCurve.CONSTANT_POWER
    @Volatile private var autoFadeActive: Boolean = false
    @Volatile private var autoFadeTarget: Float = 1f
    @Volatile private var autoFadeDurationSec: Float = 5.0f
    @Volatile private var jogSensitivity: Float = 1.0f

    private val isRunning = AtomicBoolean(false)
    private var audioThread: Thread? = null
    private var audioTrack: AudioTrack? = null

    fun startEngine() {
        if (!isRunning.compareAndSet(false, true)) return
        requestFocus()

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
        abandonFocus()
    }

    private fun requestFocus() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .setOnAudioFocusChangeListener(focusListener)
                    .build()
                focusRequest = req
                audioManager?.requestAudioFocus(req)
            } else {
                @Suppress("DEPRECATION")
                audioManager?.requestAudioFocus(focusListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
            }
        } catch (_: Exception) {
        }
    }

    private fun abandonFocus() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                focusRequest?.let { audioManager?.abandonAudioFocusRequest(it) }
            } else {
                @Suppress("DEPRECATION")
                audioManager?.abandonAudioFocus(focusListener)
            }
        } catch (_: Exception) {
        }
    }

    fun setBufferFrames(frames: Int) {
        bufferFrames = frames.coerceIn(256, 2048)
    }

    private fun createAudioTrack(framesPerBuf: Int): AudioTrack? {
        return try {
            val minBufBytes = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(framesPerBuf * 4 * 2)

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

    private fun gainForAssign(assign: ChannelAssign, cfGainA: Float, cfGainB: Float): Float {
        return when (assign) {
            ChannelAssign.A -> cfGainA
            ChannelAssign.B -> cfGainB
            ChannelAssign.THRU -> 1.0f
        }
    }

    private fun runAudioLoop() {
        val frames = bufferFrames
        val track = createAudioTrack(frames)
        audioTrack = track
        try {
            track?.play()
        } catch (_: Exception) {
        }

        val mixFloat = FloatArray(frames * 2)
        val ch3Float = FloatArray(frames * 2)
        val outShort = ShortArray(frames * 2)
        val recShort = ShortArray(frames * 2)
        val dspPair = FloatArray(2)
        val ch3Pair = FloatArray(2)
        val ch4Pair = FloatArray(2)
        val spectrumBands = FloatArray(16)
        var telemetryCounter = 0

        while (isRunning.get()) {
            if (autoFadeActive) {
                val stepPerBuf = (2f / (autoFadeDurationSec.coerceAtLeast(0.5f) * (sampleRate.toFloat() / frames)))
                val diff = autoFadeTarget - crossfader
                if (abs(diff) <= stepPerBuf) {
                    crossfader = autoFadeTarget
                    autoFadeActive = false
                } else {
                    crossfader += if (diff > 0f) stepPerBuf else -stepPerBuf
                }
            }

            val (cfGainA, cfGainB) = computeCrossfaderGains(crossfader, crossfaderCurve)
            val gainCh1 = if (rtA.channelEnabled) gainForAssign(rtA.assign, cfGainA, cfGainB) else 0f
            val gainCh2 = if (rtB.channelEnabled) gainForAssign(rtB.assign, cfGainA, cfGainB) else 0f
            val gainCh3 = if (ch3Enabled) gainForAssign(ch3Assign, cfGainA, cfGainB) else 0f
            val gainCh4 = if (ch4Enabled) gainForAssign(ch4Assign, cfGainA, cfGainB) else 0f

            sfxEngine.renderInto(ch3Float, frames)

            var peak1 = 0f; var peak2 = 0f; var peak3 = 0f; var peak4 = 0f
            var peakAL = 0f; var peakAR = 0f; var peakBL = 0f; var peakBR = 0f
            val recSrc = recordingEngine.activeSource

            for (f in 0 until frames) {
                // CH 1 (Deck A)
                renderDeckSample(rtA, dspPair)
                val aL = if (rtA.channelEnabled) dspPair[0] * rtA.volume else 0f
                val aR = if (rtA.channelEnabled) dspPair[1] * rtA.volume else 0f
                if (abs(aL) > peakAL) peakAL = abs(aL)
                if (abs(aR) > peakAR) peakAR = abs(aR)
                val p1 = maxOf(abs(aL), abs(aR)); if (p1 > peak1) peak1 = p1

                // CH 2 (Deck B)
                renderDeckSample(rtB, dspPair)
                val bL = if (rtB.channelEnabled) dspPair[0] * rtB.volume else 0f
                val bR = if (rtB.channelEnabled) dspPair[1] * rtB.volume else 0f
                if (abs(bL) > peakBL) peakBL = abs(bL)
                if (abs(bR) > peakBR) peakBR = abs(bR)
                val p2 = maxOf(abs(bL), abs(bR)); if (p2 > peak2) peak2 = p2

                // CH 3 (Sampler/SFX Bus DSP)
                ch3Dsp.processStereo(ch3Float[f * 2], ch3Float[f * 2 + 1], ch3Pair)
                val c3L = if (ch3Enabled) ch3Pair[0] * ch3Volume else 0f
                val c3R = if (ch3Enabled) ch3Pair[1] * ch3Volume else 0f
                val p3 = maxOf(abs(c3L), abs(c3R)); if (p3 > peak3) peak3 = p3

                // CH 4 (Aux / Master Wet Send Bus DSP)
                val auxInL = (aL + bL) * 0.15f
                val auxInR = (aR + bR) * 0.15f
                ch4Dsp.processStereo(auxInL, auxInR, ch4Pair)
                val hasCh4Fx = ch4Enabled && (ch4Dsp.activeEffect != DjEffectType.NONE || abs(ch4Dsp.filterCutoff) > 0.05f)
                val c4L = if (hasCh4Fx) ch4Pair[0] * ch4Volume else 0f
                val c4R = if (hasCh4Fx) ch4Pair[1] * ch4Volume else 0f
                val p4 = maxOf(abs(c4L), abs(c4R)); if (p4 > peak4) peak4 = p4

                mixFloat[f * 2] = aL * gainCh1 + bL * gainCh2 + c3L * gainCh3 + c4L * gainCh4
                mixFloat[f * 2 + 1] = aR * gainCh1 + bR * gainCh2 + c3R * gainCh3 + c4R * gainCh4

                if (recSrc == RecordSource.DECK_A) {
                    recShort[f * 2] = (aL.coerceIn(-1f, 1f) * 32767f).toInt().toShort()
                    recShort[f * 2 + 1] = (aR.coerceIn(-1f, 1f) * 32767f).toInt().toShort()
                } else if (recSrc == RecordSource.DECK_B) {
                    recShort[f * 2] = (bL.coerceIn(-1f, 1f) * 32767f).toInt().toShort()
                    recShort[f * 2 + 1] = (bR.coerceIn(-1f, 1f) * 32767f).toInt().toShort()
                }
            }

            var peakML = 0f; var peakMR = 0f
            val mVol = masterVolume * masterVolumeDuck
            for (f in 0 until frames) {
                masterDsp.processStereo(mixFloat[f * 2], mixFloat[f * 2 + 1], dspPair)
                val rawML = dspPair[0] * mVol
                val rawMR = dspPair[1] * mVol
                val mL = if (masterDsp.limiterEnabled) ChannelDspProcessor.softClip(rawML) else rawML.coerceIn(-1f, 1f)
                val mR = if (masterDsp.limiterEnabled) ChannelDspProcessor.softClip(rawMR) else rawMR.coerceIn(-1f, 1f)
                mixFloat[f * 2] = mL
                mixFloat[f * 2 + 1] = mR
                if (abs(mL) > peakML) peakML = abs(mL)
                if (abs(mR) > peakMR) peakMR = abs(mR)

                outShort[f * 2] = (mL * 32767f).toInt().coerceIn(-32768, 32767).toShort()
                outShort[f * 2 + 1] = (mR * 32767f).toInt().coerceIn(-32768, 32767).toShort()
            }

            val toWrite = if (recSrc == RecordSource.DECK_A || recSrc == RecordSource.DECK_B) recShort else outShort
            recordingEngine.writeMasterPcm(toWrite, frames * 2)

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

            telemetryCounter++
            if (telemetryCounter >= 3) {
                telemetryCounter = 0
                spectrumAnalyzer.analyze(mixFloat, frames, spectrumBands)
                publishTelemetry(peakAL, peakAR, peakBL, peakBR, peak1, peak2, peak3, peak4, peakML, peakMR, spectrumBands)
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

        val step: Double = when {
            rt.isScratching -> {
                val vel = rt.scratchVelocity
                rt.scratchVelocity *= 0.996
                vel
            }
            rt.brakeActive -> {
                rt.brakeSpeed = (rt.brakeSpeed - 0.000045).coerceAtLeast(0.0)
                rt.pitchRatio * rt.brakeSpeed
            }
            else -> {
                rt.brakeSpeed = 1.0
                val dir = if (rt.reverseActive) -1.0 else 1.0
                dir * (rt.pitchRatio + rt.pitchBendOffset).coerceIn(0.05, 2.2)
            }
        }

        rt.playheadFrame += step
        if (rt.slipMode && rt.isPlaying) {
            rt.slipGhostFrame += rt.pitchRatio
            if (rt.slipGhostFrame >= total) rt.slipGhostFrame -= total
        } else {
            rt.slipGhostFrame = rt.playheadFrame
        }

        if (rt.loopActive && rt.loopEndFrame > rt.loopStartFrame + 100.0) {
            if (rt.playheadFrame >= rt.loopEndFrame) {
                rt.playheadFrame = rt.loopStartFrame
            } else if (rt.playheadFrame < rt.loopStartFrame) {
                rt.playheadFrame = rt.loopEndFrame - 1.0
            }
        } else {
            if (rt.playheadFrame >= total - 1) {
                rt.playheadFrame = 0.0
            } else if (rt.playheadFrame < 0.0) {
                rt.playheadFrame = (total - 2).toDouble()
            }
        }

        rt.jogAngleDeg = (rt.jogAngleDeg + (step * 200.0 / sampleRate).toFloat()) % 360f

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
        p1: Float, p2: Float, p3: Float, p4: Float,
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

        val peaks = listOf(p1, p2, p3, p4)
        _mixer.update { m ->
            m.copy(
                crossfader = crossfader,
                autoFadeActive = autoFadeActive,
                masterLevelLeft = mL.coerceIn(0f, 1f),
                masterLevelRight = mR.coerceIn(0f, 1f),
                spectrumBands = bands.toList(),
                channels = m.channels.mapIndexed { idx, ch ->
                    ch.copy(meterLevel = peaks.getOrElse(idx) { 0f }.coerceIn(0f, 1f))
                }
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

    fun getLoadedDeckPcm(deckId: DeckId): DecodedAudio? {
        val r = rt(deckId)
        val st = flow(deckId).value
        if (r.pcmStereo.isEmpty()) return null
        return DecodedAudio(
            pcmStereo = r.pcmStereo,
            sampleRate = sampleRate,
            durationMs = st.durationMs,
            detectedBpm = st.originalBpm,
            waveform = st.waveform
        )
    }

    fun setActiveDeck(deckId: DeckId) {
        _mixer.update { it.copy(activeDeck = deckId) }
    }

    fun loadDecodedTrack(
        deckId: DeckId,
        songId: Long,
        title: String,
        artist: String,
        musicalKey: String = "Am",
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
                musicalKey = musicalKey,
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
        setActiveDeck(deckId)
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
        setActiveDeck(deckId)
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

    /**
     * Resets a single deck's playback back to 00:00 independently of the other deck.
     * Clears active loops, brake/reverse FX, and temporary pitch bend, ready to play from start.
     */
    fun resetDeckPlayback(deckId: DeckId) {
        setActiveDeck(deckId)
        val r = rt(deckId)
        r.isPlaying = false
        r.playheadFrame = 0.0
        r.slipGhostFrame = 0.0
        r.cuePointFrame = 0.0
        r.loopActive = false
        r.brakeActive = false
        r.reverseActive = false
        r.brakeSpeed = 1.0
        r.pitchBendOffset = 0.0
        r.jogAngleDeg = 0f
        r.dsp.reset()
        flow(deckId).update {
            it.copy(
                isPlaying = false,
                positionMs = 0L,
                jogAngleDeg = 0f,
                loopActive = false,
                repeatEnabled = false,
                activeRollBeats = null,
                beatCount = 1,
                levelLeft = 0f,
                levelRight = 0f
            )
        }
    }

    fun seekToRatio(deckId: DeckId, ratio: Float) {
        setActiveDeck(deckId)
        val r = rt(deckId)
        if (r.totalFrames <= 0) return
        val clamped = ratio.coerceIn(0f, 0.999f)
        r.playheadFrame = clamped.toDouble() * r.totalFrames
        r.slipGhostFrame = r.playheadFrame
    }

    fun setPitchPercent(deckId: DeckId, percent: Float) {
        val range = flow(deckId).value.pitchRangePercent
        val clamped = percent.coerceIn(-range, range)
        val r = rt(deckId)
        r.pitchRatio = (1.0 + (clamped / 100.0)).coerceIn(0.05, 2.0)
        flow(deckId).update {
            it.copy(
                pitchPercent = clamped,
                effectiveBpm = (it.originalBpm * r.pitchRatio.toFloat()).coerceAtLeast(10f)
            )
        }
    }

    fun cyclePitchRange(deckId: DeckId) {
        val ranges = listOf(6f, 10f, 16f, 50f, 100f)
        val cur = flow(deckId).value.pitchRangePercent
        val next = ranges[(ranges.indexOfFirst { abs(it - cur) < 0.5f } + 1) % ranges.size]
        flow(deckId).update { it.copy(pitchRangePercent = next) }
        setPitchPercent(deckId, flow(deckId).value.pitchPercent)
    }

    fun setPitchRange(deckId: DeckId, range: Float) {
        flow(deckId).update { it.copy(pitchRangePercent = range) }
        setPitchPercent(deckId, flow(deckId).value.pitchPercent)
    }

    fun resetTempo(deckId: DeckId) {
        val r = rt(deckId)
        r.pitchRatio = 1.0
        r.pitchBendOffset = 0.0
        flow(deckId).update {
            it.copy(
                pitchPercent = 0f,
                effectiveBpm = it.originalBpm,
                syncActive = false
            )
        }
    }

    fun toggleReloop(deckId: DeckId) {
        val r = rt(deckId)
        val cur = flow(deckId).value
        if (r.loopActive) {
            exitLoop(deckId)
        } else if (cur.loopInMs != null && cur.loopOutMs != null && r.loopEndFrame > r.loopStartFrame + 100.0) {
            r.loopActive = true
            r.playheadFrame = r.loopStartFrame
            flow(deckId).update { it.copy(loopActive = true) }
        } else {
            toggleAutoLoop(deckId, cur.autoLoopBeats)
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
        setActiveDeck(deckId)
        val targetDeck = flow(deckId).value
        val otherId = if (deckId == DeckId.A) DeckId.B else DeckId.A
        val refDeck = flow(otherId).value
        val nextSync = !targetDeck.syncActive

        if (nextSync && refDeck.effectiveBpm > 40f && targetDeck.originalBpm > 40f) {
            val range = targetDeck.pitchRangePercent.coerceAtLeast(16f)
            val requiredRatio = (refDeck.effectiveBpm / targetDeck.originalBpm).coerceIn(1f - range / 100f, 1f + range / 100f)
            val newPitchPct = (requiredRatio - 1f) * 100f
            setPitchPercent(deckId, newPitchPct)

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

    fun setJogMode(deckId: DeckId, mode: JogMode) {
        flow(deckId).update { it.copy(jogMode = mode, scratchMode = mode == JogMode.VINYL) }
    }

    fun cycleJogMode(deckId: DeckId) {
        val all = JogMode.entries
        val cur = flow(deckId).value.jogMode
        val next = all[(all.indexOf(cur) + 1) % all.size]
        setJogMode(deckId, next)
    }

    fun toggleScratchMode(deckId: DeckId) {
        val next = if (flow(deckId).value.jogMode == JogMode.VINYL) JogMode.CDJ else JogMode.VINYL
        setJogMode(deckId, next)
    }

    fun toggleSlipMode(deckId: DeckId) {
        val r = rt(deckId)
        val next = !r.slipMode
        r.slipMode = next
        if (!next) r.slipGhostFrame = r.playheadFrame
        flow(deckId).update { it.copy(slipMode = next) }
    }

    fun toggleRepeatMode(deckId: DeckId) {
        val next = !flow(deckId).value.repeatEnabled
        flow(deckId).update { it.copy(repeatEnabled = next) }
        if (next && !rt(deckId).loopActive) {
            toggleAutoLoop(deckId, flow(deckId).value.autoLoopBeats)
        } else if (!next && rt(deckId).loopActive) {
            exitLoop(deckId)
        }
    }

    fun setPadMode(deckId: DeckId, mode: PerformancePadMode) {
        setActiveDeck(deckId)
        flow(deckId).update { it.copy(padMode = mode) }
    }

    fun triggerSlicerPad(deckId: DeckId, sliceIndex: Int) {
        val r = rt(deckId)
        if (r.totalFrames <= 0) return
        val beatFrames = (60.0 / r.originalBpm.coerceAtLeast(60f)) * sampleRate
        val phraseStart = ((r.playheadFrame / (beatFrames * 8.0)).toInt() * (beatFrames * 8.0))
        val targetFrame = (phraseStart + sliceIndex * beatFrames).coerceIn(0.0, (r.totalFrames - 2).toDouble())
        r.playheadFrame = targetFrame
        if (!r.isPlaying) {
            r.isPlaying = true
            flow(deckId).update { it.copy(isPlaying = true, activeSlicerIndex = sliceIndex) }
        } else {
            flow(deckId).update { it.copy(activeSlicerIndex = sliceIndex) }
        }
    }

    fun triggerRollPad(deckId: DeckId, beats: Float) {
        val cur = flow(deckId).value
        if (cur.activeRollBeats == beats && rt(deckId).loopActive) {
            exitLoop(deckId)
            flow(deckId).update { it.copy(activeRollBeats = null) }
        } else {
            toggleAutoLoop(deckId, beats)
            flow(deckId).update { it.copy(activeRollBeats = beats) }
        }
    }

    fun toggleHeadphoneCue(deckId: DeckId) {
        val chIdx = if (deckId == DeckId.A) 1 else 2
        toggleChannelCue(chIdx)
    }

    fun onJogTouchStart(deckId: DeckId) {
        setActiveDeck(deckId)
        val r = rt(deckId)
        if (flow(deckId).value.jogMode == JogMode.VINYL) {
            r.isScratching = true
            r.scratchVelocity = 0.0
        }
    }

    fun onJogDrag(deckId: DeckId, deltaAngleDeg: Float) {
        val r = rt(deckId)
        val sens = jogSensitivity.coerceIn(0.4f, 2.5f)
        when (flow(deckId).value.jogMode) {
            JogMode.VINYL -> {
                r.isScratching = true
                r.scratchVelocity = (deltaAngleDeg * 0.22 * sens).coerceIn(-6.0, 6.0)
            }
            JogMode.CDJ -> {
                r.pitchBendOffset = (deltaAngleDeg * 0.006 * sens).coerceIn(-0.15, 0.15)
            }
            JogMode.SEARCH -> {
                if (r.totalFrames > 0) {
                    val jumpFrames = deltaAngleDeg * (sampleRate / 120.0) * sens
                    r.playheadFrame = (r.playheadFrame + jumpFrames).coerceIn(0.0, (r.totalFrames - 2).toDouble())
                }
            }
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

    // ==================== LOOPS & 8 HOT CUES ====================

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
        if (r.loopActive && abs(flow(deckId).value.autoLoopBeats - beats) < 0.01f) {
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
        flow(deckId).update { it.copy(loopActive = false, activeRollBeats = null, repeatEnabled = false) }
    }

    fun triggerHotCue(deckId: DeckId, slotIndex: Int, loopAfterJump: Boolean = false): Long? {
        if (slotIndex !in 0..7) return null
        val r = rt(deckId)
        val currentDeck = flow(deckId).value
        val existing = currentDeck.hotCues.find { it.slotIndex == slotIndex }
        return if (existing?.positionMs != null) {
            r.playheadFrame = ((existing.positionMs / 1000.0) * sampleRate).coerceIn(0.0, (r.totalFrames - 2).coerceAtLeast(0).toDouble())
            if (!r.isPlaying && r.totalFrames > 0) {
                r.isPlaying = true
                flow(deckId).update { it.copy(isPlaying = true) }
            }
            if (loopAfterJump) {
                toggleAutoLoop(deckId, currentDeck.autoLoopBeats)
            }
            existing.positionMs
        } else {
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

    // ==================== 4-CHANNEL MIXER CONTROLS (CH 1..4) ====================

    fun toggleChannelEnabled(channelIndex: Int) {
        var next = true
        when (channelIndex) {
            1 -> { next = !rtA.channelEnabled; rtA.channelEnabled = next; _deckA.update { it.copy(channelEnabled = next) } }
            2 -> { next = !rtB.channelEnabled; rtB.channelEnabled = next; _deckB.update { it.copy(channelEnabled = next) } }
            3 -> { next = !ch3Enabled; ch3Enabled = next }
            4 -> { next = !ch4Enabled; ch4Enabled = next }
        }
        _mixer.update { m ->
            m.copy(channels = m.channels.map {
                if (it.channelIndex == channelIndex) it.copy(channelEnabled = next) else it
            })
        }
    }

    fun setChannelVolume(deckId: DeckId, volume: Float) {
        setChannelVolumeByIndex(if (deckId == DeckId.A) 1 else 2, volume)
    }

    fun setChannelVolumeByIndex(channelIndex: Int, volume: Float) {
        val v = volume.coerceIn(0f, 1f)
        when (channelIndex) {
            1 -> { rtA.volume = v; _deckA.update { it.copy(volume = v) } }
            2 -> { rtB.volume = v; _deckB.update { it.copy(volume = v) } }
            3 -> ch3Volume = v
            4 -> ch4Volume = v
        }
        _mixer.update { m ->
            m.copy(channels = m.channels.map {
                if (it.channelIndex == channelIndex) it.copy(volume = v) else it
            })
        }
    }

    fun toggleChannelCue(channelIndex: Int) {
        var newState = false
        _mixer.update { m ->
            m.copy(channels = m.channels.map {
                if (it.channelIndex == channelIndex) {
                    newState = !it.cueActive
                    it.copy(cueActive = newState)
                } else it
            })
        }
        if (channelIndex == 1) _deckA.update { it.copy(headphoneCue = newState) }
        if (channelIndex == 2) _deckB.update { it.copy(headphoneCue = newState) }
    }

    fun cycleChannelAssign(channelIndex: Int) {
        val all = ChannelAssign.entries
        _mixer.update { m ->
            m.copy(channels = m.channels.map { ch ->
                if (ch.channelIndex == channelIndex) {
                    val next = all[(all.indexOf(ch.assign) + 1) % all.size]
                    when (channelIndex) {
                        1 -> rtA.assign = next
                        2 -> rtB.assign = next
                        3 -> ch3Assign = next
                        4 -> ch4Assign = next
                    }
                    ch.copy(assign = next)
                } else ch
            })
        }
    }

    fun updateChannelEqByIndex(
        channelIndex: Int,
        gainDb: Float? = null,
        trebleDb: Float? = null,
        midDb: Float? = null,
        bassDb: Float? = null,
        filterCutoff: Float? = null,
        vocalDb: Float? = null
    ) {
        when (channelIndex) {
            1 -> updateDeckEq(DeckId.A, gainDb, trebleDb, midDb, bassDb, filterCutoff, vocalDb = vocalDb)
            2 -> updateDeckEq(DeckId.B, gainDb, trebleDb, midDb, bassDb, filterCutoff, vocalDb = vocalDb)
            3, 4 -> {
                val cur = _mixer.value.channels.find { it.channelIndex == channelIndex } ?: return
                val g = (gainDb ?: cur.gainDb).coerceIn(-18f, 18f)
                val t = (trebleDb ?: cur.trebleDb).coerceIn(-12f, 12f)
                val m = (midDb ?: cur.midDb).coerceIn(-12f, 12f)
                val b = (bassDb ?: cur.bassDb).coerceIn(-12f, 12f)
                val f = (filterCutoff ?: cur.filterCutoff).coerceIn(-1f, 1f)
                val v = (vocalDb ?: cur.vocalDb).coerceIn(-12f, 12f)
                val dsp = if (channelIndex == 3) ch3Dsp else ch4Dsp
                dsp.updateEq(g, b, m, t, f, v)
                _mixer.update { mix ->
                    mix.copy(channels = mix.channels.map {
                        if (it.channelIndex == channelIndex) {
                            it.copy(gainDb = g, trebleDb = t, midDb = m, bassDb = b, filterCutoff = f, vocalDb = v)
                        } else it
                    })
                }
            }
        }
    }

    fun updateDeckEq(
        deckId: DeckId,
        gainDb: Float? = null,
        trebleDb: Float? = null,
        midDb: Float? = null,
        bassDb: Float? = null,
        filterCutoff: Float? = null,
        preset: EqPreset? = null,
        vocalDb: Float? = null
    ) {
        val cur = flow(deckId).value
        val g = (gainDb ?: cur.gainDb).coerceIn(-18f, 18f)
        val t = (trebleDb ?: cur.trebleDb).coerceIn(-12f, 12f)
        val m = (midDb ?: cur.midDb).coerceIn(-12f, 12f)
        val b = (bassDb ?: cur.bassDb).coerceIn(-12f, 12f)
        val f = (filterCutoff ?: cur.filterCutoff).coerceIn(-1f, 1f)
        val v = (vocalDb ?: cur.vocalDb).coerceIn(-12f, 12f)
        val p = preset ?: if (trebleDb != null || midDb != null || bassDb != null) EqPreset.CUSTOM else cur.eqPreset

        rt(deckId).dsp.updateEq(g, b, m, t, f, v)
        flow(deckId).update {
            it.copy(gainDb = g, trebleDb = t, midDb = m, bassDb = b, vocalDb = v, filterCutoff = f, eqPreset = p)
        }
        val chIdx = if (deckId == DeckId.A) 1 else 2
        _mixer.update { mix ->
            mix.copy(channels = mix.channels.map {
                if (it.channelIndex == chIdx) {
                    it.copy(gainDb = g, trebleDb = t, midDb = m, bassDb = b, vocalDb = v, filterCutoff = f)
                } else it
            })
        }
    }

    fun setEqBandMode(deckId: DeckId, mode: EqBandMode) {
        flow(deckId).update { it.copy(eqBandMode = mode) }
    }

    fun updateTenBandEq(deckId: DeckId, bandIndex: Int, db: Float) {
        val cur = flow(deckId).value
        val updated = cur.tenBandEqDb.toMutableList().apply {
            if (bandIndex in indices) this[bandIndex] = db.coerceIn(-12f, 12f)
        }
        rt(deckId).dsp.updateTenBandEq(updated)
        val bassAvg = (updated[1] + updated[2]) * 0.5f
        val midAvg = (updated[4] + updated[5] + updated[6]) / 3f
        val highAvg = (updated[7] + updated[8]) * 0.5f
        flow(deckId).update {
            it.copy(tenBandEqDb = updated, bassDb = bassAvg, midDb = midAvg, trebleDb = highAvg, eqPreset = EqPreset.CUSTOM)
        }
    }

    fun updateFifteenBandEq(deckId: DeckId, bandIndex: Int, db: Float) {
        val cur = flow(deckId).value
        val updated = cur.fifteenBandEqDb.toMutableList().apply {
            if (bandIndex in indices) this[bandIndex] = db.coerceIn(-12f, 12f)
        }
        rt(deckId).dsp.updateFifteenBandEq(updated)
        val bassAvg = (updated[2] + updated[3] + updated[4]) / 3f
        val midAvg = (updated[6] + updated[7] + updated[8] + updated[9]) / 4f
        val highAvg = (updated[11] + updated[12] + updated[13]) / 3f
        flow(deckId).update {
            it.copy(fifteenBandEqDb = updated, bassDb = bassAvg, midDb = midAvg, trebleDb = highAvg, eqPreset = EqPreset.CUSTOM)
        }
    }

    fun applyEqPreset(deckId: DeckId, preset: EqPreset) {
        rt(deckId).dsp.updateTenBandEq(preset.tenBandDb)
        if (flow(deckId).value.eqBandMode == EqBandMode.BAND_15) {
            rt(deckId).dsp.updateFifteenBandEq(preset.fifteenBandDb)
        }
        flow(deckId).update { it.copy(tenBandEqDb = preset.tenBandDb, fifteenBandEqDb = preset.fifteenBandDb) }
        updateDeckEq(
            deckId = deckId,
            bassDb = preset.bassDb,
            midDb = preset.midDb,
            trebleDb = preset.trebleDb,
            preset = preset
        )
    }

    fun applyCustomEqValues(deckId: DeckId, bassDb: Float, midDb: Float, trebleDb: Float, vocalDb: Float = 0f) {
        updateDeckEq(deckId, bassDb = bassDb, midDb = midDb, trebleDb = trebleDb, vocalDb = vocalDb, preset = EqPreset.CUSTOM)
    }

    fun setDeckEffect(deckId: DeckId, effect: DjEffectType, wet: Float? = null, param: Float? = null) {
        val cur = flow(deckId).value
        val w = (wet ?: cur.effectWet).coerceIn(0f, 1f)
        val p = (param ?: cur.effectParam).coerceIn(0f, 1f)
        val r = rt(deckId)
        r.reverseActive = (effect == DjEffectType.BACKSPIN)
        r.brakeActive = (effect == DjEffectType.BRAKE || effect == DjEffectType.VINYL_STOP)
        if (!r.brakeActive) r.brakeSpeed = 1.0
        r.dsp.updateEffect(effect, w, p)
        flow(deckId).update { it.copy(activeEffect = effect, effectWet = w, effectParam = p) }
    }

    fun setChannelEffect(channelIndex: Int, effect: DjEffectType, wet: Float = 0.5f, param: Float = 0.5f) {
        when (channelIndex) {
            1 -> setDeckEffect(DeckId.A, effect, wet, param)
            2 -> setDeckEffect(DeckId.B, effect, wet, param)
            3 -> ch3Dsp.updateEffect(effect, wet, param)
            4 -> ch4Dsp.updateEffect(effect, wet, param)
        }
    }

    fun setMasterEffect(effect: DjEffectType, wet: Float? = null, param: Float? = null) {
        val cur = _mixer.value
        val w = (wet ?: cur.masterEffectWet).coerceIn(0f, 1f)
        val p = (param ?: cur.masterEffectParam).coerceIn(0f, 1f)
        masterDsp.updateEffect(effect, w, p)
        _mixer.update {
            it.copy(
                masterEffect = effect,
                beatFxOn = effect != DjEffectType.NONE,
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
        ch3Dsp.updateColorFx(type, param)
        ch4Dsp.updateColorFx(type, param)
        _mixer.update { it.copy(activeColorFx = type) }
    }

    fun setColorFxParam(param: Float) {
        val p = param.coerceIn(0f, 1f)
        val type = _mixer.value.activeColorFx
        rtA.dsp.updateColorFx(type, p)
        rtB.dsp.updateColorFx(type, p)
        _mixer.update { it.copy(colorFxParam = p) }
    }

    fun toggleBeatFxOn() {
        val next = !_mixer.value.beatFxOn
        _mixer.update { it.copy(beatFxOn = next) }
        val fx = if (next) _mixer.value.beatFxType else DjEffectType.NONE
        masterDsp.updateEffect(fx, _mixer.value.masterEffectWet, _mixer.value.masterEffectParam)
        _mixer.update { it.copy(masterEffect = fx) }
    }

    fun setMasterVolume(volume: Float) {
        val v = volume.coerceIn(0f, 1f)
        masterVolume = v
        _mixer.update { it.copy(masterVolume = v) }
    }

    fun setBoothVolume(volume: Float) {
        val v = volume.coerceIn(0f, 1f)
        boothVolume = v
        _mixer.update { it.copy(boothVolume = v) }
    }

    fun toggleMasterLimiter() {
        val next = !_mixer.value.masterLimiterEnabled
        masterDsp.limiterEnabled = next
        _mixer.update { it.copy(masterLimiterEnabled = next) }
    }

    fun setHeadphoneVolume(volume: Float) {
        _mixer.update { it.copy(headphoneVolume = volume.coerceIn(0f, 1f)) }
    }

    fun setHeadphoneMix(mix: Float) {
        _mixer.update { it.copy(headphoneMix = mix.coerceIn(0f, 1f)) }
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

    fun cycleFadeDuration() {
        val options = listOf(1f, 3f, 5f, 10f, 15f)
        val cur = _mixer.value.fadeDurationSec
        val next = options[(options.indexOfFirst { abs(it - cur) < 0.5f } + 1) % options.size]
        autoFadeDurationSec = next
        _mixer.update { it.copy(fadeDurationSec = next) }
    }

    fun setFadeDuration(sec: Float) {
        val clamped = sec.coerceIn(1f, 15f)
        autoFadeDurationSec = clamped
        _mixer.update { it.copy(fadeDurationSec = clamped) }
    }

    fun triggerAutoFade(durationSec: Float = _mixer.value.fadeDurationSec) {
        autoFadeDurationSec = durationSec.coerceIn(0.5f, 16f)
        autoFadeTarget = if (crossfader <= 0f) 1f else -1f
        autoFadeActive = !autoFadeActive
        _mixer.update { it.copy(autoFadeActive = autoFadeActive, autoFadeTarget = autoFadeTarget) }
    }

    fun toggleSamplerRoll(padIndex: Int, beats: Float = _mixer.value.samplerRollBeats) {
        val active = !_mixer.value.samplerRollActive
        if (active) {
            val bpm = if (_mixer.value.activeDeck == DeckId.A) _deckA.value.effectiveBpm else _deckB.value.effectiveBpm
            sfxEngine.startSamplerRoll(padIndex, beats, bpm)
        } else {
            sfxEngine.stopSamplerRoll()
        }
        _mixer.update { it.copy(samplerRollActive = active, samplerRollBeats = beats) }
    }

    fun setSamplerRollBeats(beats: Float) {
        _mixer.update { it.copy(samplerRollBeats = beats) }
        if (_mixer.value.samplerRollActive) {
            val bpm = if (_mixer.value.activeDeck == DeckId.A) _deckA.value.effectiveBpm else _deckB.value.effectiveBpm
            sfxEngine.startSamplerRoll(sfxEngine.rollPadIndex, beats, bpm)
        }
    }

    fun setSamplerBank(bankIdx: Int) {
        _mixer.update { it.copy(samplerBankIndex = bankIdx.coerceIn(0, 3)) }
    }

    fun setJogSensitivity(sensitivity: Float) {
        jogSensitivity = sensitivity.coerceIn(0.4f, 2.5f)
    }

    fun setWaveformZoom(deckId: DeckId, zoom: Float) {
        flow(deckId).update { it.copy(waveformZoom = zoom.coerceIn(1f, 4f)) }
    }

    fun swapDecks() {
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
        fun computeCrossfaderGains(position: Float, curve: CrossfaderCurve): Pair<Float, Float> {
            val x = ((position.coerceIn(-1f, 1f) + 1f) * 0.5f)
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
