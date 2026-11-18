package com.example.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import ai.picovoice.porcupine.Porcupine
import com.example.data.settings.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class WakeWordManager(
    private val context: Context,
    private val settingsStore: SettingsStore
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var listenJob: Job? = null
    private var porcupine: Porcupine? = null
    private var audioRecord: AudioRecord? = null

    private val _wakeWordAvailable = MutableStateFlow(false)
    val wakeWordAvailable: StateFlow<Boolean> = _wakeWordAvailable.asStateFlow()

    init {
        refreshAvailability()
    }

    fun findPpnAssetName(): String? {
        val assets = runCatching { context.assets.list("")?.toList().orEmpty() }.getOrDefault(emptyList())
        if ("hiyori.ppn" in assets) return "hiyori.ppn"
        return assets.firstOrNull { it.endsWith(".ppn", ignoreCase = true) }
    }

    fun refreshAvailability(): Boolean {
        val hasKey = settingsStore.getPorcupineAccessKey().isNotBlank()
        val hasPpn = findPpnAssetName() != null
        val available = hasKey && hasPpn
        _wakeWordAvailable.value = available
        return available
    }

    fun startListening(onWakeWordDetected: () -> Unit): Boolean {
        if (!refreshAvailability()) return false
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            return false
        }

        stop()

        val accessKey = settingsStore.getPorcupineAccessKey().trim()
        val ppnAsset = findPpnAssetName() ?: return false

        return try {
            val engine = Porcupine.Builder()
                .setAccessKey(accessKey)
                .setKeywordPath(ppnAsset)
                .setSensitivity(0.6f)
                .build(context.applicationContext)
            porcupine = engine

            val sampleRate = engine.sampleRate
            val frameLength = engine.frameLength
            val minBuf = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val bufferSize = (minBuf.coerceAtLeast(frameLength * 2)) * 2

            val recorder = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )
            if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                recorder.release()
                engine.delete()
                porcupine = null
                return false
            }

            audioRecord = recorder
            recorder.startRecording()

            listenJob = scope.launch {
                val frame = ShortArray(frameLength)
                while (isActive) {
                    val read = recorder.read(frame, 0, frameLength)
                    if (read == frameLength) {
                        val keywordIndex = runCatching { engine.process(frame) }.getOrDefault(-1)
                        if (keywordIndex >= 0) {
                            stopInternal()
                            onWakeWordDetected()
                            break
                        }
                    } else if (read < 0) {
                        break
                    }
                }
            }
            true
        } catch (_: Exception) {
            stop()
            false
        }
    }

    fun pause() {
        stop()
    }

    fun stop() {
        listenJob?.cancel()
        listenJob = null
        stopInternal()
    }

    @Synchronized
    private fun stopInternal() {
        runCatching {
            audioRecord?.stop()
        }
        runCatching {
            audioRecord?.release()
        }
        audioRecord = null

        runCatching {
            porcupine?.delete()
        }
        porcupine = null
    }
}
