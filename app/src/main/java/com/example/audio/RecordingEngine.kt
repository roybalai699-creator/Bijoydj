package com.example.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class CompletedRecording(
    val title: String,
    val filePath: String,
    val format: String,
    val durationMs: Long,
    val fileSizeBytes: Long
)

/**
 * Records Master Output, Deck A, Deck B, or Live Mix directly into 16-bit 44.1kHz Stereo WAV.
 * Supports Start, Pause, Resume, Stop, Rename, M4A/AAC transcoding, and SAF URI export.
 */
class RecordingEngine(private val context: Context) {

    private val _state = MutableStateFlow(RecordingState())
    val state: StateFlow<RecordingState> = _state.asStateFlow()

    @Volatile private var isRecordingFlag = false
    @Volatile private var isPausedFlag = false
    @Volatile var activeSource: RecordSource = RecordSource.MASTER

    private var outputFile: File? = null
    private var outStream: BufferedOutputStream? = null
    private var totalPcmBytesWritten: Long = 0L
    private val writeLock = Any()

    private val sampleRate = 44100
    private val channels = 2
    private val bitsPerSample = 16

    fun setRecordSource(source: RecordSource) {
        activeSource = source
        _state.value = _state.value.copy(source = source)
    }

    fun startRecording(
        customTitle: String? = null,
        includeMic: Boolean = false,
        source: RecordSource = activeSource
    ): String {
        synchronized(writeLock) {
            if (isRecordingFlag) return _state.value.currentTitle
            activeSource = source
            val dir = File(context.filesDir, "recordings").apply { mkdirs() }
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val title = customTitle?.takeIf { it.isNotBlank() } ?: "BIJOY_Mix_$stamp"
            val safeName = title.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
            val file = File(dir, "$safeName.wav")

            outputFile = file
            totalPcmBytesWritten = 0L
            val fos = FileOutputStream(file)
            fos.write(ByteArray(44))
            outStream = BufferedOutputStream(fos, 32768)

            isRecordingFlag = true
            isPausedFlag = false
            _state.value = RecordingState(
                isRecording = true,
                isPaused = false,
                elapsedMs = 0L,
                currentTitle = title,
                includeMic = includeMic,
                source = source
            )
            return title
        }
    }

    fun pauseRecording() {
        synchronized(writeLock) {
            if (!isRecordingFlag) return
            isPausedFlag = true
            _state.value = _state.value.copy(isPaused = true)
        }
    }

    fun resumeRecording() {
        synchronized(writeLock) {
            if (!isRecordingFlag) return
            isPausedFlag = false
            _state.value = _state.value.copy(isPaused = false)
        }
    }

    fun writeMasterPcm(pcmShorts: ShortArray, shortCount: Int) {
        if (!isRecordingFlag || isPausedFlag) return
        synchronized(writeLock) {
            val stream = outStream ?: return
            val byteCount = shortCount * 2
            val bytes = ByteArray(byteCount)
            var b = 0
            for (i in 0 until shortCount) {
                val s = pcmShorts[i].toInt()
                bytes[b++] = (s and 0xFF).toByte()
                bytes[b++] = ((s shr 8) and 0xFF).toByte()
            }
            try {
                stream.write(bytes)
                totalPcmBytesWritten += byteCount
                val frames = totalPcmBytesWritten / (channels * 2)
                val elapsedMs = (frames * 1000L) / sampleRate
                if (elapsedMs - _state.value.elapsedMs >= 150L) {
                    _state.value = _state.value.copy(elapsedMs = elapsedMs)
                }
            } catch (_: Exception) {
            }
        }
    }

    fun stopRecording(): CompletedRecording? {
        synchronized(writeLock) {
            if (!isRecordingFlag) return null
            isRecordingFlag = false
            isPausedFlag = false

            val file = outputFile
            val title = _state.value.currentTitle
            val dataBytes = totalPcmBytesWritten

            try {
                outStream?.flush()
                outStream?.close()
            } catch (_: Exception) {
            }
            outStream = null

            val frames = dataBytes / (channels * 2)
            val durationMs = (frames * 1000L) / sampleRate

            if (file != null && file.exists()) {
                patchWavHeader(file, dataBytes, sampleRate, channels, bitsPerSample)
            }

            val savedSource = activeSource
            _state.value = RecordingState(source = savedSource)
            return if (file != null && file.exists()) {
                CompletedRecording(
                    title = title,
                    filePath = file.absolutePath,
                    format = "WAV",
                    durationMs = durationMs,
                    fileSizeBytes = file.length()
                )
            } else null
        }
    }

    fun renameRecordingFile(currentPath: String, newTitle: String): Result<File> = runCatching {
        val src = File(currentPath)
        require(src.exists()) { "Source recording file missing" }
        val ext = src.extension.ifBlank { "wav" }
        val safeName = newTitle.trim().replace(Regex("[^a-zA-Z0-9_\\-]"), "_").ifBlank { "BIJOY_Mix" }
        val dst = File(src.parentFile, "$safeName.$ext")
        if (src.absolutePath != dst.absolutePath) {
            check(src.renameTo(dst)) { "Could not rename file" }
        }
        dst
    }

    /**
     * Converts a recorded or remixed WAV file into compressed AAC/M4A via Android MediaCodec + MediaMuxer.
     */
    suspend fun exportWavAsM4a(wavPath: String, bitrateBps: Int = 192_000): Result<File> =
        withContext(Dispatchers.IO) {
            runCatching {
                val srcFile = File(wavPath)
                require(srcFile.exists() && srcFile.length() > 44L) { "Invalid WAV file" }
                val outFile = File(srcFile.parentFile, "${srcFile.nameWithoutExtension}.m4a")

                val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, channels).apply {
                    setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                    setInteger(MediaFormat.KEY_BIT_RATE, bitrateBps)
                    setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
                }
                val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
                codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                codec.start()

                val muxer = MediaMuxer(outFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
                var trackIndex = -1
                var muxerStarted = false

                FileInputStream(srcFile).use { fis ->
                    fis.skip(44L)
                    val bufferInfo = MediaCodec.BufferInfo()
                    val tempBuf = ByteArray(4096)
                    var inputDone = false
                    var outputDone = false
                    var ptsUs = 0L

                    try {
                        while (!outputDone) {
                            if (!inputDone) {
                                val inIdx = codec.dequeueInputBuffer(5000)
                                if (inIdx >= 0) {
                                    val inBuf = codec.getInputBuffer(inIdx)
                                    val read = fis.read(tempBuf)
                                    if (read <= 0 || inBuf == null) {
                                        codec.queueInputBuffer(inIdx, 0, 0, ptsUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                        inputDone = true
                                    } else {
                                        inBuf.clear()
                                        inBuf.put(tempBuf, 0, read)
                                        codec.queueInputBuffer(inIdx, 0, read, ptsUs, 0)
                                        val framesWritten = read / (channels * 2)
                                        ptsUs += (framesWritten * 1_000_000L) / sampleRate
                                    }
                                }
                            }

                            val outIdx = codec.dequeueOutputBuffer(bufferInfo, 5000)
                            when {
                                outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                                    if (!muxerStarted) {
                                        trackIndex = muxer.addTrack(codec.outputFormat)
                                        muxer.start()
                                        muxerStarted = true
                                    }
                                }
                                outIdx >= 0 -> {
                                    val outBuf = codec.getOutputBuffer(outIdx)
                                    if (outBuf != null && bufferInfo.size > 0 && muxerStarted) {
                                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                                            outBuf.position(bufferInfo.offset)
                                            outBuf.limit(bufferInfo.offset + bufferInfo.size)
                                            muxer.writeSampleData(trackIndex, outBuf, bufferInfo)
                                        }
                                    }
                                    codec.releaseOutputBuffer(outIdx, false)
                                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                        outputDone = true
                                    }
                                }
                            }
                        }
                    } finally {
                        runCatching { codec.stop() }
                        runCatching { codec.release() }
                        runCatching { if (muxerStarted) muxer.stop() }
                        runCatching { muxer.release() }
                    }
                }
                outFile
            }
        }

    fun exportRecordingToUri(sourcePath: String, destUri: Uri): Result<Unit> = runCatching {
        val src = File(sourcePath)
        require(src.exists()) { "Recording file no longer exists" }
        context.contentResolver.openOutputStream(destUri)?.use { out ->
            src.inputStream().use { input -> input.copyTo(out) }
        } ?: error("Could not open destination URI")
    }

    companion object {
        fun writeStereoWavFile(file: File, pcmStereo: ShortArray, sampleRate: Int = 44100) {
            file.parentFile?.mkdirs()
            val totalAudioBytes = pcmStereo.size.toLong() * 2L
            BufferedOutputStream(FileOutputStream(file), 32768).use { bos ->
                bos.write(ByteArray(44))
                val chunk = ByteArray(pcmStereo.size * 2)
                var b = 0
                for (sample in pcmStereo) {
                    val v = sample.toInt()
                    chunk[b++] = (v and 0xFF).toByte()
                    chunk[b++] = ((v shr 8) and 0xFF).toByte()
                }
                bos.write(chunk)
            }
            patchWavHeader(file, totalAudioBytes, sampleRate, 2, 16)
        }

        fun patchWavHeader(
            file: File,
            totalAudioLen: Long,
            sampleRate: Int = 44100,
            channels: Int = 2,
            bitsPerSample: Int = 16
        ) {
            val totalDataLen = totalAudioLen + 36
            val byteRate = (sampleRate * channels * bitsPerSample / 8).toLong()
            val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
                put("RIFF".toByteArray(Charsets.US_ASCII))
                putInt(totalDataLen.toInt())
                put("WAVE".toByteArray(Charsets.US_ASCII))
                put("fmt ".toByteArray(Charsets.US_ASCII))
                putInt(16)
                putShort(1)
                putShort(channels.toShort())
                putInt(sampleRate)
                putInt(byteRate.toInt())
                putShort((channels * bitsPerSample / 8).toShort())
                putShort(bitsPerSample.toShort())
                put("data".toByteArray(Charsets.US_ASCII))
                putInt(totalAudioLen.toInt())
            }.array()

            RandomAccessFile(file, "rw").use { raf ->
                raf.seek(0)
                raf.write(header)
            }
        }
    }
}
