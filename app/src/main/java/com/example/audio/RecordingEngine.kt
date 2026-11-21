package com.example.audio

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.BufferedOutputStream
import java.io.File
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
 * Records the real mixed stereo master output from BijoyAudioEngine directly into a 16-bit 44.1kHz WAV file.
 * Supports Start, Pause, Resume, Stop, and exporting to a user-selected SAF URI.
 */
class RecordingEngine(private val context: Context) {

    private val _state = MutableStateFlow(RecordingState())
    val state: StateFlow<RecordingState> = _state.asStateFlow()

    @Volatile private var isRecordingFlag = false
    @Volatile private var isPausedFlag = false
    private var outputFile: File? = null
    private var outStream: BufferedOutputStream? = null
    private var totalPcmBytesWritten: Long = 0L
    private val writeLock = Any()

    private val sampleRate = 44100
    private val channels = 2
    private val bitsPerSample = 16

    fun startRecording(customTitle: String? = null, includeMic: Boolean = false): String {
        synchronized(writeLock) {
            if (isRecordingFlag) return _state.value.currentTitle
            val dir = File(context.filesDir, "recordings").apply { mkdirs() }
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val title = customTitle?.takeIf { it.isNotBlank() } ?: "BIJOY_Mix_$stamp"
            val safeName = title.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
            val file = File(dir, "$safeName.wav")

            outputFile = file
            totalPcmBytesWritten = 0L
            val fos = FileOutputStream(file)
            // Reserve 44-byte WAV header
            fos.write(ByteArray(44))
            outStream = BufferedOutputStream(fos, 32768)

            isRecordingFlag = true
            isPausedFlag = false
            _state.value = RecordingState(
                isRecording = true,
                isPaused = false,
                elapsedMs = 0L,
                currentTitle = title,
                includeMic = includeMic
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

    /**
     * Called from the real-time audio engine thread with mixed master PCM samples.
     */
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
                patchWavHeader(file, dataBytes)
            }

            _state.value = RecordingState()
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

    fun exportRecordingToUri(sourcePath: String, destUri: Uri): Result<Unit> = runCatching {
        val src = File(sourcePath)
        require(src.exists()) { "Recording file no longer exists" }
        context.contentResolver.openOutputStream(destUri)?.use { out ->
            src.inputStream().use { input -> input.copyTo(out) }
        } ?: error("Could not open destination URI")
    }

    private fun patchWavHeader(file: File, totalAudioLen: Long) {
        val totalDataLen = totalAudioLen + 36
        val byteRate = (sampleRate * channels * bitsPerSample / 8).toLong()
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray(Charsets.US_ASCII))
            putInt(totalDataLen.toInt())
            put("WAVE".toByteArray(Charsets.US_ASCII))
            put("fmt ".toByteArray(Charsets.US_ASCII))
            putInt(16) // PCM chunk size
            putShort(1) // PCM format
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
