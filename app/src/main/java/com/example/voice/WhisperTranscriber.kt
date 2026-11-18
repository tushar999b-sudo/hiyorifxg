package com.example.voice

import android.content.Context
import dev.ffmpegkit.whisper.Whisper
import dev.ffmpegkit.whisper.WhisperConfig
import dev.ffmpegkit.whisper.WhisperModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.util.concurrent.TimeUnit

class WhisperTranscriber(private val context: Context) {

    private val modelMutex = Mutex()
    private var loadedModel: WhisperModel? = null

    private val downloadClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    fun getModelFile(): File {
        val dir = File(context.filesDir, "whisper")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, MODEL_FILE_NAME)
    }

    fun isModelDownloaded(): Boolean {
        val file = getModelFile()
        return file.exists() && file.length() > MIN_MODEL_BYTES
    }

    suspend fun downloadModel(onProgress: (Float) -> Unit): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val targetFile = getModelFile()
            val tempFile = File(targetFile.parentFile, "$MODEL_FILE_NAME.tmp")
            if (tempFile.exists()) tempFile.delete()

            val request = Request.Builder()
                .url(WHISPER_TINY_URL)
                .get()
                .build()

            downloadClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("Download failed (HTTP ${response.code})")
                }
                val body = response.body ?: throw IllegalStateException("Empty response body")
                val totalBytes = body.contentLength()
                var downloadedBytes = 0L

                body.byteStream().use { input ->
                    FileOutputStream(tempFile).use { output ->
                        val buffer = ByteArray(16 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            downloadedBytes += read
                            if (totalBytes > 0L) {
                                onProgress((downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f))
                            }
                        }
                        output.flush()
                    }
                }
            }

            if (targetFile.exists()) targetFile.delete()
            if (!tempFile.renameTo(targetFile)) {
                throw IllegalStateException("Failed to save downloaded model")
            }
            onProgress(1f)
        }
    }

    suspend fun transcribePcm16kMono(samples: ShortArray): String? = withContext(Dispatchers.IO) {
        if (samples.isEmpty() || !isModelDownloaded()) return@withContext null

        val wavFile = File(context.cacheDir, "hiyori_cmd_${System.currentTimeMillis()}.wav")
        try {
            writeWav16kMono(wavFile, samples)
            val model = modelMutex.withLock {
                loadedModel?.takeIf { it.isValid } ?: Whisper.loadModel(
                    context.applicationContext,
                    getModelFile().absolutePath
                ).also { loadedModel = it }
            }

            val config = WhisperConfig(
                language = "auto",
                translate = false,
                threads = 4,
                maxSegmentLength = 0,
                printTimestamps = false
            )
            val result = Whisper.transcribe(model, wavFile.absolutePath, config)
            result.text.trim().takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        } finally {
            runCatching { wavFile.delete() }
        }
    }

    fun release() {
        loadedModel?.let { model ->
            runCatching { Whisper.releaseModel(model) }
        }
        loadedModel = null
    }

    private fun writeWav16kMono(file: File, samples: ShortArray) {
        val byteData = ByteArray(samples.size * 2)
        for (i in samples.indices) {
            val s = samples[i].toInt()
            byteData[i * 2] = (s and 0xFF).toByte()
            byteData[i * 2 + 1] = ((s shr 8) and 0xFF).toByte()
        }

        val totalDataLen = byteData.size + 36
        val sampleRate = 16000
        val channels = 1
        val byteRate = sampleRate * channels * 2

        RandomAccessFile(file, "rw").use { raf ->
            raf.setLength(0)
            raf.writeBytes("RIFF")
            raf.writeInt(Integer.reverseBytes(totalDataLen))
            raf.writeBytes("WAVE")
            raf.writeBytes("fmt ")
            raf.writeInt(Integer.reverseBytes(16)) // Subchunk1Size (16 for PCM)
            raf.writeShort(java.lang.Short.reverseBytes(1.toShort()).toInt()) // AudioFormat = 1 (PCM)
            raf.writeShort(java.lang.Short.reverseBytes(channels.toShort()).toInt())
            raf.writeInt(Integer.reverseBytes(sampleRate))
            raf.writeInt(Integer.reverseBytes(byteRate))
            raf.writeShort(java.lang.Short.reverseBytes((channels * 2).toShort()).toInt()) // BlockAlign
            raf.writeShort(java.lang.Short.reverseBytes(16.toShort()).toInt()) // BitsPerSample
            raf.writeBytes("data")
            raf.writeInt(Integer.reverseBytes(byteData.size))
            raf.write(byteData)
        }
    }

    companion object {
        const val MODEL_FILE_NAME = "ggml-tiny.bin"
        private const val MIN_MODEL_BYTES = 1_000_000L
        const val WHISPER_TINY_URL =
            "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.bin"
    }
}
