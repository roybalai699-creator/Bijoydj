package com.example.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

data class DecodedAudio(
    val pcmStereo: ShortArray,
    val sampleRate: Int,
    val durationMs: Long,
    val detectedBpm: Float,
    val waveform: WaveformData
)

data class ImportedTrackMeta(
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val format: String
)

object AudioDecoder {

    const val TARGET_SAMPLE_RATE = 44100

    fun extractMetadata(context: Context, uri: Uri, fallbackName: String): ImportedTrackMeta {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                ?.takeIf { it.isNotBlank() }
                ?: fallbackName.substringBeforeLast('.')
            val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                ?.takeIf { it.isNotBlank() }
                ?: "Unknown Artist"
            val album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                ?.takeIf { it.isNotBlank() }
                ?: "Local Import"
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            val ext = fallbackName.substringAfterLast('.', "MP3").uppercase()
            ImportedTrackMeta(title, artist, album, durationMs, ext)
        } catch (_: Exception) {
            ImportedTrackMeta(
                title = fallbackName.substringBeforeLast('.'),
                artist = "Local Artist",
                album = "Imported",
                durationMs = 0L,
                format = fallbackName.substringAfterLast('.', "AUDIO").uppercase()
            )
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }
    }

    /**
     * Decodes a local audio URI (MP3, WAV, AAC, M4A, FLAC, OGG) into 44.1kHz stereo 16-bit PCM.
     * Capped at 6 minutes per track to ensure smooth memory usage on mobile devices.
     */
    suspend fun decodeUriToPcm(
        context: Context,
        uri: Uri,
        localCopyPath: String? = null
    ): Result<DecodedAudio> = withContext(Dispatchers.IO) {
        runCatching {
            val extractor = MediaExtractor()
            if (!localCopyPath.isNullOrBlank() && File(localCopyPath).exists()) {
                extractor.setDataSource(localCopyPath)
            } else {
                extractor.setDataSource(context, uri, null)
            }

            var audioTrackIdx = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                val mime = f.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrackIdx = i
                    format = f
                    break
                }
            }
            if (audioTrackIdx < 0 || format == null) {
                extractor.release()
                error("No supported audio track found in file")
            }

            extractor.selectTrack(audioTrackIdx)
            val mime = format.getString(MediaFormat.KEY_MIME)!!
            val srcSampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val srcChannels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)

            val codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            val pcmOut = ByteArrayOutputStream(1024 * 512)
            val bufferInfo = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            // Cap decoded raw bytes (~6 minutes stereo 44.1kHz = ~63MB max)
            val maxRawBytes = srcSampleRate * srcChannels * 2 * 360

            try {
                while (!outputDone && pcmOut.size() < maxRawBytes) {
                    if (!inputDone) {
                        val inIdx = codec.dequeueInputBuffer(5000)
                        if (inIdx >= 0) {
                            val inBuf = codec.getInputBuffer(inIdx)
                            if (inBuf != null) {
                                val sampleSize = extractor.readSampleData(inBuf, 0)
                                if (sampleSize < 0) {
                                    codec.queueInputBuffer(
                                        inIdx, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                    )
                                    inputDone = true
                                } else {
                                    codec.queueInputBuffer(
                                        inIdx, 0, sampleSize, extractor.sampleTime, 0
                                    )
                                    extractor.advance()
                                }
                            }
                        }
                    }

                    val outIdx = codec.dequeueOutputBuffer(bufferInfo, 5000)
                    if (outIdx >= 0) {
                        val outBuf = codec.getOutputBuffer(outIdx)
                        if (outBuf != null && bufferInfo.size > 0) {
                            val chunk = ByteArray(bufferInfo.size)
                            outBuf.position(bufferInfo.offset)
                            outBuf.get(chunk)
                            pcmOut.write(chunk)
                        }
                        codec.releaseOutputBuffer(outIdx, false)
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            outputDone = true
                        }
                    }
                }
            } finally {
                try { codec.stop() } catch (_: Exception) {}
                try { codec.release() } catch (_: Exception) {}
                try { extractor.release() } catch (_: Exception) {}
            }

            val rawBytes = pcmOut.toByteArray()
            val rawShorts = ShortArray(rawBytes.size / 2)
            ByteBuffer.wrap(rawBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(rawShorts)

            val stereo44k = convertToStereo44k(rawShorts, srcChannels, srcSampleRate)
            val durationMs = ((stereo44k.size / 2).toLong() * 1000L) / TARGET_SAMPLE_RATE
            val detectedBpm = BpmAndWaveformAnalyzer.detectBpm(stereo44k, TARGET_SAMPLE_RATE)
            val waveform = BpmAndWaveformAnalyzer.generateWaveform(stereo44k, TARGET_SAMPLE_RATE, detectedBpm)

            DecodedAudio(
                pcmStereo = stereo44k,
                sampleRate = TARGET_SAMPLE_RATE,
                durationMs = durationMs,
                detectedBpm = detectedBpm,
                waveform = waveform
            )
        }
    }

    private fun convertToStereo44k(src: ShortArray, srcChannels: Int, srcRate: Int): ShortArray {
        val srcFrames = src.size / srcChannels
        if (srcFrames <= 0) return ShortArray(0)
        if (srcChannels == 2 && srcRate == TARGET_SAMPLE_RATE) return src

        val ratio = srcRate.toDouble() / TARGET_SAMPLE_RATE.toDouble()
        val dstFrames = (srcFrames / ratio).toInt().coerceAtLeast(1)
        val dst = ShortArray(dstFrames * 2)

        for (f in 0 until dstFrames) {
            val srcPos = f * ratio
            val idx0 = min(srcFrames - 1, srcPos.toInt())
            val idx1 = min(srcFrames - 1, idx0 + 1)
            val frac = (srcPos - idx0).toFloat()

            val l0 = src[idx0 * srcChannels].toFloat()
            val l1 = src[idx1 * srcChannels].toFloat()
            val l = (l0 + (l1 - l0) * frac).toInt().coerceIn(-32768, 32767).toShort()

            val r = if (srcChannels >= 2) {
                val r0 = src[idx0 * srcChannels + 1].toFloat()
                val r1 = src[idx1 * srcChannels + 1].toFloat()
                (r0 + (r1 - r0) * frac).toInt().coerceIn(-32768, 32767).toShort()
            } else {
                l
            }

            dst[f * 2] = l
            dst[f * 2 + 1] = r
        }
        return dst
    }

    /**
     * Generates a studio-grade synthesized electronic dance DJ groove (32 seconds seamless loopable)
     * so users can mix, scratch, EQ, apply FX, beat-sync, and record immediately on launch.
     */
    suspend fun generateBuiltInDjTrack(presetId: Int, bpm: Float): DecodedAudio =
        withContext(Dispatchers.Default) {
            val sr = TARGET_SAMPLE_RATE
            val beatSec = 60.0 / bpm
            val totalBeats = 64 // 16 bars (~30 seconds)
            val totalFrames = (totalBeats * beatSec * sr).toInt()
            val pcm = ShortArray(totalFrames * 2)
            val rng = Random(presetId * 1337 + 42)

            // Chord progressions per built-in studio groove
            val bassNotes = when (presetId % 4) {
                0 -> doubleArrayOf(55.0, 55.0, 65.41, 49.0) // Am - C - G (House 128 BPM)
                1 -> doubleArrayOf(61.74, 55.0, 49.0, 55.0) // Bm - A - G (Bangla Club 135 BPM)
                2 -> doubleArrayOf(43.65, 51.91, 58.27, 49.0) // Fm - Ab - Bb (Electro Bass 124 BPM)
                else -> doubleArrayOf(65.41, 73.42, 49.0, 55.0) // C - D - G - A (Festival 130 BPM)
            }
            val leadScale = when (presetId % 4) {
                0 -> doubleArrayOf(440.0, 523.25, 587.33, 659.25, 783.99)
                1 -> doubleArrayOf(493.88, 587.33, 659.25, 739.99, 880.0)
                2 -> doubleArrayOf(349.23, 415.30, 466.16, 523.25, 622.25)
                else -> doubleArrayOf(523.25, 587.33, 659.25, 783.99, 880.0)
            }

            var bassPhase = 0.0
            var leadPhase = 0.0
            var padPhase1 = 0.0
            var padPhase2 = 0.0

            for (i in 0 until totalFrames) {
                val tSec = i.toDouble() / sr
                val beatPos = tSec / beatSec
                val beatIndex = beatPos.toInt()
                val beatFrac = beatPos - beatIndex
                val step16 = (beatPos * 4.0).toInt()
                val stepFrac = (beatPos * 4.0) - step16
                val barIndex = (beatIndex / 4) % 4

                // 1. Punchy 4-on-the-floor Club Kick
                val kickEnv = exp(-beatFrac * 9.5)
                val kickFreq = 135.0 * exp(-beatFrac * 24.0) + 46.0
                val kick = sin(2.0 * PI * kickFreq * (beatFrac * beatSec)) * kickEnv * 0.62

                // 2. Crisp Off-beat Hi-Hat + Clap on beats 2 & 4
                val hatGate = if (step16 % 4 == 2) 1.0 else if (step16 % 2 == 1) 0.45 else 0.0
                val hat = (rng.nextDouble() * 2.0 - 1.0) * exp(-stepFrac * 14.0) * hatGate * 0.22

                val clap = if (beatIndex % 2 == 1 && beatFrac < 0.28) {
                    (rng.nextDouble() * 2.0 - 1.0) * exp(-beatFrac * 15.0) * 0.34
                } else 0.0

                // 3. Sidechained Driving Bassline
                val sidechain = (1.0 - exp(-beatFrac * 7.0)).coerceIn(0.15, 1.0)
                val bassFreq = bassNotes[barIndex] * (if (step16 % 4 == 3) 2.0 else 1.0)
                bassPhase += bassFreq / sr
                val sawBass = ((bassPhase % 1.0) * 2.0 - 1.0) * 0.5 + sin(2.0 * PI * bassPhase) * 0.5
                val bass = sawBass * sidechain * 0.34

                // 4. Melodic Synth Arp Lead (enters after bar 1, varies by phrase)
                val phraseEnergy = if ((beatIndex / 16) % 2 == 1) 1.0 else 0.72
                val noteIdx = (step16 + barIndex * 2) % leadScale.size
                val leadGate = if (step16 % 2 == 0 || step16 % 5 == 0) 1.0 else 0.25
                val leadFreq = leadScale[noteIdx]
                leadPhase += leadFreq / sr
                val lead = (sin(2.0 * PI * leadPhase) * 0.65 + ((leadPhase % 1.0) * 2.0 - 1.0) * 0.35) *
                    exp(-stepFrac * 5.0) * leadGate * phraseEnergy * 0.22

                // 5. Stereo Shimmer Pad
                val padF1 = bassNotes[barIndex] * 4.0
                val padF2 = bassNotes[barIndex] * 6.0
                padPhase1 += padF1 / sr
                padPhase2 += padF2 / sr
                val padL = sin(2.0 * PI * padPhase1) * sidechain * 0.12
                val padR = sin(2.0 * PI * padPhase2) * sidechain * 0.12

                val mixL = (kick + bass + hat * 0.8 + clap + lead * 0.9 + padL).coerceIn(-0.98, 0.98)
                val mixR = (kick + bass + hat * 1.1 + clap + lead * 1.1 + padR).coerceIn(-0.98, 0.98)

                pcm[i * 2] = (mixL * 32767.0).toInt().toShort()
                pcm[i * 2 + 1] = (mixR * 32767.0).toInt().toShort()
            }

            val durationMs = (totalFrames.toLong() * 1000L) / sr
            val waveform = BpmAndWaveformAnalyzer.generateWaveform(pcm, sr, bpm)
            DecodedAudio(
                pcmStereo = pcm,
                sampleRate = sr,
                durationMs = durationMs,
                detectedBpm = bpm,
                waveform = waveform
            )
        }
}
