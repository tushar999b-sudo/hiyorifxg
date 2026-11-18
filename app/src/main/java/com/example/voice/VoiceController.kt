package com.example.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.core.content.ContextCompat
import com.example.data.settings.SettingsStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.math.abs

enum class VoiceStatus(val label: String) {
    OFF("Off"),
    LISTENING_FOR_WAKE_WORD("Listening for Hiyori"),
    LISTENING("Listening..."),
    THINKING("Thinking..."),
    SPEAKING("Speaking...")
}

class VoiceController(
    private val context: Context,
    private val settingsStore: SettingsStore
) : TextToSpeech.OnInitListener {

    val whisperTranscriber = WhisperTranscriber(context.applicationContext)
    val wakeWordManager = WakeWordManager(context.applicationContext, settingsStore)

    private var tts: TextToSpeech? = null
    private var isTtsInitialized = false

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _voiceStatus = MutableStateFlow(VoiceStatus.OFF)
    val voiceStatus: StateFlow<VoiceStatus> = _voiceStatus.asStateFlow()

    private val _voiceMuted = MutableStateFlow(false)
    val voiceMuted: StateFlow<Boolean> = _voiceMuted.asStateFlow()

    private var speechRecognizer: SpeechRecognizer? = null

    @Volatile
    private var activeSpeakDeferred: CompletableDeferred<Unit>? = null

    init {
        runCatching {
            tts = TextToSpeech(context.applicationContext, this)
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.let { engine ->
                val enIn = Locale("en", "IN")
                val enInRes = engine.setLanguage(enIn)
                val langOk = if (enInRes == TextToSpeech.LANG_MISSING_DATA || enInRes == TextToSpeech.LANG_NOT_SUPPORTED) {
                    val usRes = engine.setLanguage(Locale.US)
                    usRes != TextToSpeech.LANG_MISSING_DATA && usRes != TextToSpeech.LANG_NOT_SUPPORTED
                } else {
                    true
                }

                if (langOk) {
                    engine.setPitch(1.12f)
                    engine.setSpeechRate(1.04f)
                    isTtsInitialized = true

                    engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {
                            _isSpeaking.value = true
                            _voiceStatus.value = VoiceStatus.SPEAKING
                        }

                        override fun onDone(utteranceId: String?) {
                            _isSpeaking.value = false
                            activeSpeakDeferred?.complete(Unit)
                            restoreIdleStatus()
                        }

                        @Deprecated("Deprecated in Java")
                        override fun onError(utteranceId: String?) {
                            _isSpeaking.value = false
                            activeSpeakDeferred?.complete(Unit)
                            restoreIdleStatus()
                        }

                        override fun onError(utteranceId: String?, errorCode: Int) {
                            _isSpeaking.value = false
                            activeSpeakDeferred?.complete(Unit)
                            restoreIdleStatus()
                        }
                    })
                }
            }
        }
    }

    fun setVoiceStatus(status: VoiceStatus) {
        _voiceStatus.value = status
    }

    fun restoreIdleStatus() {
        val liveOn = settingsStore.state.value.isLiveVoiceEnabled
        _voiceStatus.value = if (liveOn && wakeWordManager.wakeWordAvailable.value) {
            VoiceStatus.LISTENING_FOR_WAKE_WORD
        } else {
            VoiceStatus.OFF
        }
    }

    fun speak(text: String) {
        if (_voiceMuted.value || !isTtsInitialized || text.isBlank()) {
            _isSpeaking.value = false
            restoreIdleStatus()
            return
        }
        val cleanText = cleanForTts(text)
        if (cleanText.isNotBlank()) {
            tts?.speak(cleanText, TextToSpeech.QUEUE_FLUSH, null, "UTTERANCE_${System.currentTimeMillis()}")
        } else {
            _isSpeaking.value = false
            restoreIdleStatus()
        }
    }

    suspend fun speakAndWait(text: String) {
        if (_voiceMuted.value || !isTtsInitialized || text.isBlank()) {
            _isSpeaking.value = false
            restoreIdleStatus()
            return
        }
        val cleanText = cleanForTts(text)
        if (cleanText.isBlank()) {
            _isSpeaking.value = false
            restoreIdleStatus()
            return
        }
        val deferred = CompletableDeferred<Unit>()
        activeSpeakDeferred = deferred
        _isSpeaking.value = true
        _voiceStatus.value = VoiceStatus.SPEAKING
        tts?.speak(cleanText, TextToSpeech.QUEUE_FLUSH, null, "UTTERANCE_${System.currentTimeMillis()}")
        withTimeoutOrNull(30_000L) {
            deferred.await()
        }
        _isSpeaking.value = false
        activeSpeakDeferred = null
    }

    private fun cleanForTts(text: String): String {
        return text
            .replace(Regex("\\[.*?\\]"), "")
            .replace(Regex("[*#_`~]"), "")
            .trim()
    }

    fun stopSpeaking() {
        tts?.stop()
        _isSpeaking.value = false
        activeSpeakDeferred?.complete(Unit)
        activeSpeakDeferred = null
        restoreIdleStatus()
    }

    fun toggleMute() {
        _voiceMuted.value = !_voiceMuted.value
        if (_voiceMuted.value) {
            stopSpeaking()
        }
    }

    /**
     * Records one command on the phone (no beep) and transcribes it:
     * - Preferred: 16 kHz mono PCM AudioRecord (stop on 1.2s silence or 15s max) -> on-device Whisper (language = "auto")
     * - Fallback when Whisper model is not downloaded: Android SpeechRecognizer (hi-IN, EXTRA_PREFER_OFFLINE = true)
     */
    suspend fun captureAndTranscribeCommand(): String? {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            return null
        }

        _isListening.value = true
        _voiceStatus.value = VoiceStatus.LISTENING

        return try {
            if (whisperTranscriber.isModelDownloaded()) {
                val pcmSamples = recordPcm16kMonoWithSilenceDetection()
                _isListening.value = false
                if (pcmSamples.isEmpty()) {
                    restoreIdleStatus()
                    null
                } else {
                    _voiceStatus.value = VoiceStatus.THINKING
                    whisperTranscriber.transcribePcm16kMono(pcmSamples)
                }
            } else {
                val transcript = recognizeWithAndroidSpeechRecognizer()
                _isListening.value = false
                if (transcript.isNullOrBlank()) {
                    restoreIdleStatus()
                    null
                } else {
                    _voiceStatus.value = VoiceStatus.THINKING
                    transcript
                }
            }
        } catch (_: Exception) {
            _isListening.value = false
            restoreIdleStatus()
            null
        }
    }

    private suspend fun recordPcm16kMonoWithSilenceDetection(): ShortArray = withContext(Dispatchers.IO) {
        val sampleRate = 16000
        val minBuf = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val bufferSize = (minBuf.coerceAtLeast(3200)) * 2
        val recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            return@withContext ShortArray(0)
        }

        val maxSamples = sampleRate * 15 // 15 seconds maximum
        val silenceSamplesLimit = (sampleRate * 1.2f).toInt() // 1.2 seconds of silence
        val collected = ArrayList<Short>(sampleRate * 5)
        val chunk = ShortArray(1600) // 100ms chunks
        var silentSamples = 0
        var heardSpeech = false

        try {
            recorder.startRecording()
            while (collected.size < maxSamples && _isListening.value) {
                val read = recorder.read(chunk, 0, chunk.size)
                if (read <= 0) break

                var peak = 0
                for (i in 0 until read) {
                    val s = chunk[i]
                    collected.add(s)
                    val a = abs(s.toInt())
                    if (a > peak) peak = a
                }

                if (peak >= SILENCE_AMPLITUDE_THRESHOLD) {
                    heardSpeech = true
                    silentSamples = 0
                } else {
                    silentSamples += read
                    if (heardSpeech && silentSamples >= silenceSamplesLimit) {
                        break
                    }
                    if (!heardSpeech && silentSamples >= sampleRate * 5) {
                        // 5 seconds of pure initial silence -> stop early
                        break
                    }
                }
            }
        } finally {
            runCatching { recorder.stop() }
            runCatching { recorder.release() }
        }

        if (!heardSpeech) ShortArray(0) else collected.toShortArray()
    }

    private suspend fun recognizeWithAndroidSpeechRecognizer(): String? = withContext(Dispatchers.Main) {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            return@withContext null
        }

        val deferred = CompletableDeferred<String?>()
        try {
            speechRecognizer?.destroy()
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context.applicationContext).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        _isListening.value = true
                    }
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {
                        _isListening.value = false
                    }
                    override fun onError(error: Int) {
                        _isListening.value = false
                        if (!deferred.isCompleted) deferred.complete(null)
                    }
                    override fun onResults(results: Bundle?) {
                        _isListening.value = false
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val text = matches?.firstOrNull()?.trim()
                        if (!deferred.isCompleted) deferred.complete(text)
                    }
                    override fun onPartialResults(partialResults: Bundle?) {}
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }
            speechRecognizer?.startListening(intent)
        } catch (_: Exception) {
            _isListening.value = false
            if (!deferred.isCompleted) deferred.complete(null)
        }

        withTimeoutOrNull(16_000L) {
            deferred.await()
        }
    }

    fun stopListening() {
        _isListening.value = false
        runCatching { speechRecognizer?.stopListening() }
        restoreIdleStatus()
    }

    fun destroy() {
        wakeWordManager.stop()
        whisperTranscriber.release()
        tts?.stop()
        tts?.shutdown()
        runCatching { speechRecognizer?.destroy() }
    }

    companion object {
        private const val SILENCE_AMPLITUDE_THRESHOLD = 650
    }
}
