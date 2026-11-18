package com.example.ui

import android.Manifest
import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.agent.audit.AuditLogger
import com.example.agent.brain.HiyoriBrain
import com.example.agent.llm.GeminiProvider
import com.example.agent.llm.LlmProviderException
import com.example.agent.llm.OllamaProvider
import com.example.agent.llm.ProviderRouter
import com.example.agent.loop.AgentLoop
import com.example.agent.loop.AgentLoopState
import com.example.agent.loop.StopController
import com.example.agent.memory.MemoryManager
import com.example.agent.safety.PendingConfirmation
import com.example.agent.safety.SafetyGate
import com.example.agent.tool.PcBridgeClient
import com.example.agent.tool.PcBridgeStatus
import com.example.agent.tool.PermissionLevel
import com.example.agent.tool.ToolCallRequest
import com.example.agent.tool.ToolExecutionResult
import com.example.agent.tool.ToolRegistry
import com.example.avatar.HiyoriExpression
import com.example.data.database.HiyoriDatabase
import com.example.data.model.ActionStatus
import com.example.data.model.AuditLogEntity
import com.example.data.model.ChatMessageEntity
import com.example.data.model.MemoryCategory
import com.example.data.model.MemoryEntity
import com.example.data.model.MessageSender
import com.example.data.settings.HiyoriSettings
import com.example.data.settings.SettingsStore
import com.example.service.HiyoriForegroundService
import com.example.voice.VoiceController
import com.example.voice.VoiceStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.UUID

enum class CompanionTab(val title: String) {
    OVERVIEW("Overview"),
    AGENT_HUB("Agent Hub"),
    CONTROLS("Controls"),
    CHAT("AI Chat"),
    MEMORY("Memory")
}

data class MobileDeviceStats(
    val energyScore: Int = 94,
    val batteryPercent: Int = 88,
    val ramFreeGb: Float = 3.6f,
    val storageFreeGb: Float = 78.4f,
    val securityStatus: String = "Shielded",
    val activeTask: String? = null
)

data class HiyoriUiState(
    val activeTab: CompanionTab = CompanionTab.AGENT_HUB,
    val currentExpression: HiyoriExpression = HiyoriExpression.NEUTRAL,
    val isThinking: Boolean = false,
    val isSpeaking: Boolean = false,
    val isListening: Boolean = false,
    val voiceStatus: VoiceStatus = VoiceStatus.OFF,
    val wakeWordAvailable: Boolean = false,
    val voiceMuted: Boolean = false,
    val pendingPermissionRequest: ToolCallRequest? = null,
    val lastExecutedToolResult: ToolExecutionResult? = null,
    val screenVisionPreview: Map<String, Any>? = null,
    val speechWaveformIntensity: Float = 0f,
    val notificationMessage: String? = null,
    val deviceStats: MobileDeviceStats = MobileDeviceStats(),
    val isDarkTheme: Boolean = true,
    val remainingActions: Int = 50,
    val streamingDraft: String = "",
    val geminiTestStatus: String? = null,
    val isTestingGemini: Boolean = false,
    val lastGeminiHttpCode: Int? = null,
    val localTestStatus: String? = null,
    val isTestingLocal: Boolean = false,
    val lastLocalHttpCode: Int? = null,
    val whisperDownloadProgress: Float? = null,
    val whisperStatusText: String = "Not downloaded (using hi-IN offline fallback)"
)

class HiyoriViewModel(application: Application) : AndroidViewModel(application) {

    private val database = HiyoriDatabase.getInstance(application)
    private val memoryDao = database.memoryDao()
    private val auditLogDao = database.auditLogDao()
    private val chatMessageDao = database.chatMessageDao()

    val settingsStore = SettingsStore(application)
    val stopController = StopController()
    val safetyGate = SafetyGate(settingsStore)
    val auditLogger = AuditLogger(auditLogDao)
    val memoryManager = MemoryManager(memoryDao)

    private val geminiProvider = GeminiProvider(settingsStore)
    private val ollamaProvider = OllamaProvider(settingsStore)
    val providerRouter = ProviderRouter(
        geminiProvider = geminiProvider,
        ollamaProvider = ollamaProvider,
        settingsStore = settingsStore
    )

    val toolRegistry = ToolRegistry(memoryManager, stopController, application)
    val agentLoop = AgentLoop(
        providerRouter = providerRouter,
        toolRegistry = toolRegistry,
        safetyGate = safetyGate,
        stopController = stopController,
        auditLogger = auditLogger,
        memoryManager = memoryManager,
        settingsStore = settingsStore
    )
    val brain = HiyoriBrain(agentLoop, stopController)
    val pcBridgeClient = PcBridgeClient()
    val voiceController = VoiceController(application, settingsStore)

    private val _uiState = MutableStateFlow(HiyoriUiState())
    val uiState: StateFlow<HiyoriUiState> = _uiState.asStateFlow()

    val loopState: StateFlow<AgentLoopState> = agentLoop.state
    val pendingConfirmation: StateFlow<PendingConfirmation?> = safetyGate.pendingConfirmation
    val settingsState: StateFlow<HiyoriSettings> = settingsStore.state
    val pcStatus: StateFlow<PcBridgeStatus> = pcBridgeClient.status

    val chatMessages: StateFlow<List<ChatMessageEntity>> = chatMessageDao.getAllMessagesFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val memories: StateFlow<List<MemoryEntity>> = memoryDao.getAllMemoriesFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val auditLogs: StateFlow<List<AuditLogEntity>> = auditLogDao.getRecentLogsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        HiyoriForegroundService.voiceControllerRef = voiceController
        HiyoriForegroundService.onStopRequestedCallback = {
            stop()
            viewModelScope.launch {
                settingsStore.setLiveVoiceEnabled(false)
            }
        }
        HiyoriForegroundService.onVoiceCommandSuspendCallback = { transcript ->
            sendAndAwaitTts(transcript)
        }

        refreshWhisperAndWakeWordState()

        // Observe AgentLoop state
        viewModelScope.launch {
            agentLoop.state.collect { loop ->
                _uiState.value = _uiState.value.copy(
                    isThinking = loop.running,
                    currentExpression = loop.mood,
                    deviceStats = _uiState.value.deviceStats.copy(
                        activeTask = if (loop.running) loop.plan else null
                    )
                )
            }
        }

        // Observe HTTP error codes
        viewModelScope.launch {
            geminiProvider.lastHttpErrorCode.collect { code ->
                _uiState.value = _uiState.value.copy(lastGeminiHttpCode = code)
            }
        }
        viewModelScope.launch {
            ollamaProvider.lastHttpErrorCode.collect { code ->
                _uiState.value = _uiState.value.copy(lastLocalHttpCode = code)
            }
        }

        // Collect TTS / STT / WakeWord state
        viewModelScope.launch {
            voiceController.isSpeaking.collect { speaking ->
                _uiState.value = _uiState.value.copy(
                    isSpeaking = speaking,
                    speechWaveformIntensity = if (speaking) 0.8f else 0f
                )
            }
        }
        viewModelScope.launch {
            voiceController.isListening.collect { listening ->
                _uiState.value = _uiState.value.copy(isListening = listening)
            }
        }
        viewModelScope.launch {
            voiceController.voiceStatus.collect { status ->
                _uiState.value = _uiState.value.copy(voiceStatus = status)
            }
        }
        viewModelScope.launch {
            voiceController.wakeWordManager.wakeWordAvailable.collect { available ->
                _uiState.value = _uiState.value.copy(wakeWordAvailable = available)
            }
        }
        viewModelScope.launch {
            voiceController.voiceMuted.collect { muted ->
                _uiState.value = _uiState.value.copy(voiceMuted = muted)
            }
        }

        // Initialize starter memory & welcome message
        viewModelScope.launch {
            if (memoryDao.getAllMemories().isEmpty()) {
                memoryManager.saveWithDeduplication(
                    content = "Master prefers Roman Hinglish companion replies and honest tool reporting.",
                    category = MemoryCategory.PREFERENCE,
                    importance = 5
                )
            }

            if (chatMessageDao.getRecentMessages(1).isEmpty()) {
                val greeting = "Hi Master! Main Hiyori hoon, aapki AI companion. Boliye aaj kya karein?"
                chatMessageDao.insertMessage(
                    ChatMessageEntity(
                        sender = MessageSender.HIYORI.name,
                        content = greeting,
                        expression = HiyoriExpression.HAPPY.name
                    )
                )
                voiceController.speak(greeting)
            }
        }
    }

    private fun refreshWhisperAndWakeWordState() {
        val downloaded = voiceController.whisperTranscriber.isModelDownloaded()
        viewModelScope.launch {
            settingsStore.setWhisperModelDownloaded(downloaded)
        }
        val wakeAvailable = voiceController.wakeWordManager.refreshAvailability()
        _uiState.value = _uiState.value.copy(
            wakeWordAvailable = wakeAvailable,
            whisperStatusText = if (downloaded) {
                "Downloaded (ggml-tiny multilingual ready)"
            } else {
                "Not downloaded (using hi-IN offline fallback)"
            }
        )
    }

    fun send(message: String) {
        if (message.isBlank()) return
        val trimmed = message.trim()
        viewModelScope.launch {
            voiceController.wakeWordManager.pause()
            voiceController.setVoiceStatus(VoiceStatus.THINKING)
            chatMessageDao.insertMessage(
                ChatMessageEntity(
                    sender = MessageSender.USER.name,
                    content = trimmed
                )
            )
            _uiState.value = _uiState.value.copy(
                remainingActions = (_uiState.value.remainingActions - 1).coerceAtLeast(0),
                streamingDraft = ""
            )

            val history = chatMessageDao.getRecentMessages(6).map { it.sender to it.content }.reversed()
            brain.send(
                scope = viewModelScope,
                userMessage = trimmed,
                shortHistory = history,
                onTokenDelta = { delta ->
                    _uiState.value = _uiState.value.copy(
                        streamingDraft = _uiState.value.streamingDraft + delta
                    )
                },
                onComplete = { finalText, finalMood ->
                    val loopSnapshot = agentLoop.state.value
                    val providerLabel = loopSnapshot.lastProviderLabel.ifBlank { null }
                    _uiState.value = _uiState.value.copy(
                        streamingDraft = "",
                        currentExpression = finalMood
                    )
                    chatMessageDao.insertMessage(
                        ChatMessageEntity(
                            sender = MessageSender.HIYORI.name,
                            content = finalText,
                            expression = finalMood.name,
                            providerLabel = providerLabel,
                            toolActionName = loopSnapshot.lastToolName,
                            toolStatus = loopSnapshot.lastToolStatusLine
                        )
                    )
                    voiceController.speakAndWait(finalText)
                    resumeWakeWordIfLiveEnabled()
                }
            )
        }
    }

    private suspend fun sendAndAwaitTts(message: String) {
        if (message.isBlank()) return
        val trimmed = message.trim()
        voiceController.setVoiceStatus(VoiceStatus.THINKING)
        chatMessageDao.insertMessage(
            ChatMessageEntity(
                sender = MessageSender.USER.name,
                content = trimmed
            )
        )
        _uiState.value = _uiState.value.copy(
            remainingActions = (_uiState.value.remainingActions - 1).coerceAtLeast(0),
            streamingDraft = ""
        )

        val history = chatMessageDao.getRecentMessages(6).map { it.sender to it.content }.reversed()
        val done = CompletableDeferred<String>()
        brain.send(
            scope = viewModelScope,
            userMessage = trimmed,
            shortHistory = history,
            onTokenDelta = { delta ->
                _uiState.value = _uiState.value.copy(
                    streamingDraft = _uiState.value.streamingDraft + delta
                )
            },
            onComplete = { finalText, finalMood ->
                val loopSnapshot = agentLoop.state.value
                val providerLabel = loopSnapshot.lastProviderLabel.ifBlank { null }
                _uiState.value = _uiState.value.copy(
                    streamingDraft = "",
                    currentExpression = finalMood
                )
                chatMessageDao.insertMessage(
                    ChatMessageEntity(
                        sender = MessageSender.HIYORI.name,
                        content = finalText,
                        expression = finalMood.name,
                        providerLabel = providerLabel,
                        toolActionName = loopSnapshot.lastToolName,
                        toolStatus = loopSnapshot.lastToolStatusLine
                    )
                )
                done.complete(finalText)
            }
        )
        val replyText = done.await()
        voiceController.speakAndWait(replyText)
    }

    private fun resumeWakeWordIfLiveEnabled() {
        if (settingsState.value.isLiveVoiceEnabled && HiyoriForegroundService.isRunning.value) {
            HiyoriForegroundService.start(getApplication())
        } else {
            voiceController.restoreIdleStatus()
        }
    }

    fun sendMessage(content: String) = send(content)

    fun stop() {
        brain.stop()
        voiceController.stopSpeaking()
        _uiState.value = _uiState.value.copy(
            isThinking = false,
            streamingDraft = "",
            currentExpression = HiyoriExpression.SHY,
            notificationMessage = "Agent stopped immediately!"
        )
    }

    fun approveConfirmation() {
        safetyGate.approve()
    }

    fun rejectConfirmation() {
        safetyGate.reject()
    }

    fun selectTab(tab: CompanionTab) {
        _uiState.value = _uiState.value.copy(activeTab = tab)
    }

    fun toggleTheme(currentIsDark: Boolean) {
        val next = !currentIsDark
        _uiState.value = _uiState.value.copy(isDarkTheme = next)
        viewModelScope.launch {
            settingsStore.setDarkTheme(next)
        }
    }

    fun setExpression(expression: HiyoriExpression) {
        _uiState.value = _uiState.value.copy(currentExpression = expression)
    }

    fun pokeAvatar() {
        val expressions = listOf(
            HiyoriExpression.SHY,
            HiyoriExpression.HAPPY,
            HiyoriExpression.SURPRISED,
            HiyoriExpression.SMUG,
            HiyoriExpression.LOVE
        )
        val next = expressions.random()
        setExpression(next)
        val phrase = when (next) {
            HiyoriExpression.SHY -> "Ehehe... Master, aise mat chhedo! Batao kya kaam hai?"
            HiyoriExpression.HAPPY -> "Yay! Aapke saath kaam karne mein maza aata hai Master!"
            HiyoriExpression.SURPRISED -> "W-whoa! Master aapne dara diya!"
            HiyoriExpression.SMUG -> "Aapki favorite mobile agent ready hai Master!"
            HiyoriExpression.LOVE -> "Main hamesha aapke phone ka dhyan rakhti hoon!"
            else -> "Haanji Master, main yahin hoon!"
        }
        voiceController.speak(phrase)
    }

    fun toggleVoiceMute() = voiceController.toggleMute()

    fun startVoiceListening() {
        if (_uiState.value.isListening) return
        viewModelScope.launch {
            voiceController.wakeWordManager.pause()
            val transcript = voiceController.captureAndTranscribeCommand()
            if (!transcript.isNullOrBlank()) {
                sendAndAwaitTts(transcript)
            }
            resumeWakeWordIfLiveEnabled()
        }
    }

    fun stopVoiceListening() {
        voiceController.stopListening()
        resumeWakeWordIfLiveEnabled()
    }

    fun setLiveVoiceEnabled(enabled: Boolean) {
        viewModelScope.launch {
            if (enabled) {
                val hasMic = ContextCompat.checkSelfPermission(
                    getApplication(),
                    Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
                if (!hasMic) {
                    _uiState.value = _uiState.value.copy(
                        notificationMessage = "Microphone permission is required for Live listening."
                    )
                    return@launch
                }
                settingsStore.setLiveVoiceEnabled(true)
                HiyoriForegroundService.start(getApplication())
            } else {
                settingsStore.setLiveVoiceEnabled(false)
                HiyoriForegroundService.stop(getApplication())
                voiceController.wakeWordManager.stop()
                voiceController.setVoiceStatus(VoiceStatus.OFF)
            }
        }
    }

    fun savePorcupineAccessKey(key: String) {
        val trimmed = key.trim()
        if (trimmed.isBlank()) return
        settingsStore.savePorcupineAccessKey(trimmed)
        val available = voiceController.wakeWordManager.refreshAvailability()
        _uiState.value = _uiState.value.copy(
            wakeWordAvailable = available,
            notificationMessage = if (available) {
                "Porcupine AccessKey saved. Wake word 'Hiyori' is ready."
            } else {
                "Porcupine AccessKey saved. Add hiyori.ppn to assets to enable wake word."
            }
        )
    }

    fun clearPorcupineAccessKey() {
        settingsStore.clearPorcupineAccessKey()
        voiceController.wakeWordManager.stop()
        voiceController.wakeWordManager.refreshAvailability()
        _uiState.value = _uiState.value.copy(
            wakeWordAvailable = false,
            notificationMessage = "Porcupine AccessKey cleared."
        )
    }

    fun downloadWhisperModel() {
        if (_uiState.value.whisperDownloadProgress != null) return
        _uiState.value = _uiState.value.copy(
            whisperDownloadProgress = 0f,
            whisperStatusText = "Downloading ggml-tiny.bin..."
        )
        viewModelScope.launch {
            val result = voiceController.whisperTranscriber.downloadModel { progress ->
                _uiState.value = _uiState.value.copy(
                    whisperDownloadProgress = progress,
                    whisperStatusText = "Downloading ${(progress * 100).toInt()}%"
                )
            }
            if (result.isSuccess) {
                settingsStore.setWhisperModelDownloaded(true)
                _uiState.value = _uiState.value.copy(
                    whisperDownloadProgress = null,
                    whisperStatusText = "Downloaded (ggml-tiny multilingual ready)",
                    notificationMessage = "Whisper model downloaded."
                )
            } else {
                settingsStore.setWhisperModelDownloaded(false)
                _uiState.value = _uiState.value.copy(
                    whisperDownloadProgress = null,
                    whisperStatusText = "Download failed. Check internet connection.",
                    notificationMessage = "Whisper model download failed."
                )
            }
        }
    }

    fun updateMoodFromDraft(draft: String) {
        if (draft.isBlank()) return
        val detected = HiyoriExpression.detectFromText(draft)
        if (detected != HiyoriExpression.NEUTRAL && detected != _uiState.value.currentExpression) {
            _uiState.value = _uiState.value.copy(currentExpression = detected)
        }
    }

    fun triggerQuickAction(actionName: String, params: Map<String, Any>) {
        viewModelScope.launch {
            val spec = toolRegistry.findSpec(actionName)
            val perm = PermissionLevel.fromInt(spec?.permissionLevel ?: 2)
            val request = ToolCallRequest(
                callId = UUID.randomUUID().toString(),
                toolName = actionName,
                parameters = params,
                permissionLevel = perm
            )
            val argsJson = JSONObject(params).toString()
            if (spec != null && safetyGate.requiresConfirmation(spec, argsJson)) {
                _uiState.value = _uiState.value.copy(pendingPermissionRequest = request)
            } else {
                executeToolDirectly(request)
            }
        }
    }

    fun approvePendingTool() {
        val request = _uiState.value.pendingPermissionRequest ?: return
        _uiState.value = _uiState.value.copy(pendingPermissionRequest = null)
        executeToolDirectly(request)
    }

    fun denyPendingTool() {
        val request = _uiState.value.pendingPermissionRequest ?: return
        viewModelScope.launch {
            auditLogger.logAction(
                stepNumber = 0,
                toolName = request.toolName,
                target = "Mobile Action Denied",
                rawArgumentsJson = JSONObject(request.parameters).toString(),
                permissionLevel = request.permissionLevel.level,
                status = ActionStatus.REJECTED,
                resultSummary = "Denied by user",
                durationMs = 0L
            )
            _uiState.value = _uiState.value.copy(
                pendingPermissionRequest = null,
                currentExpression = HiyoriExpression.ANGRY,
                notificationMessage = "Action ${request.toolName} was denied."
            )
        }
    }

    private fun executeToolDirectly(request: ToolCallRequest) {
        viewModelScope.launch {
            val start = System.currentTimeMillis()
            val result = toolRegistry.execute(request)
            val dur = System.currentTimeMillis() - start
            val status = when {
                result.success -> ActionStatus.SUCCESS
                result.error == "NOT_IMPLEMENTED" -> ActionStatus.NOT_IMPLEMENTED
                else -> ActionStatus.FAILED
            }
            auditLogger.logAction(
                stepNumber = 0,
                toolName = request.toolName,
                target = result.toolName,
                rawArgumentsJson = JSONObject(request.parameters).toString(),
                permissionLevel = request.permissionLevel.level,
                status = status,
                resultSummary = result.resultMessage,
                durationMs = dur
            )
            _uiState.value = _uiState.value.copy(
                lastExecutedToolResult = result,
                currentExpression = if (result.success) HiyoriExpression.SMUG else HiyoriExpression.SAD,
                notificationMessage = result.resultMessage
            )
        }
    }

    fun optimizeDevice() = triggerQuickAction("clear_cache", emptyMap())
    fun inspectScreen() = triggerQuickAction("screen_read", emptyMap())

    fun connectPc(host: String) {
        viewModelScope.launch { pcBridgeClient.connect(host) }
    }

    fun disconnectPc() = pcBridgeClient.disconnect()
    fun toggleSimulatedBridge() = pcBridgeClient.toggleSimulatedBridge()

    fun saveMemory(content: String, category: String) {
        if (content.isBlank()) return
        viewModelScope.launch {
            val cat = runCatching { MemoryCategory.valueOf(category.uppercase()) }.getOrDefault(MemoryCategory.FACT)
            memoryManager.saveWithDeduplication(content, cat, 4)
            _uiState.value = _uiState.value.copy(
                currentExpression = HiyoriExpression.LOVE,
                notificationMessage = "Saved to ${cat.name} memory!"
            )
        }
    }

    fun editMemory(memory: MemoryEntity) {
        if (memory.content.isBlank()) return
        viewModelScope.launch {
            memoryDao.updateMemory(memory)
            _uiState.value = _uiState.value.copy(notificationMessage = "Memory updated!")
        }
    }

    fun deleteMemory(id: Long) {
        viewModelScope.launch {
            memoryDao.deleteMemoryById(id)
            _uiState.value = _uiState.value.copy(notificationMessage = "Memory deleted.")
        }
    }

    fun clearChat() {
        viewModelScope.launch { chatMessageDao.clearChat() }
    }

    fun clearAuditLogs() {
        viewModelScope.launch { auditLogDao.clearLogs() }
    }

    fun dismissNotification() {
        _uiState.value = _uiState.value.copy(notificationMessage = null)
    }

    // Settings actions
    fun setUseGeminiApi(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setUseGeminiApi(enabled) }
    }

    fun saveGeminiApiKey(key: String) {
        val trimmed = key.trim()
        if (trimmed.isBlank()) return
        settingsStore.saveGeminiApiKey(trimmed)
        _uiState.value = _uiState.value.copy(
            geminiTestStatus = null,
            notificationMessage = "Gemini API key saved."
        )
        viewModelScope.launch {
            try {
                geminiProvider.discoverAndCacheFlashModel(trimmed)
            } catch (e: LlmProviderException) {
                _uiState.value = _uiState.value.copy(
                    notificationMessage = e.message ?: "Could not verify Gemini key."
                )
            }
        }
    }

    fun clearGeminiApiKey() {
        settingsStore.clearGeminiApiKey()
        _uiState.value = _uiState.value.copy(
            geminiTestStatus = null,
            notificationMessage = "Gemini API key cleared."
        )
    }

    fun testGeminiConnection() {
        if (_uiState.value.isTestingGemini) return
        _uiState.value = _uiState.value.copy(isTestingGemini = true, geminiTestStatus = "Testing...")
        viewModelScope.launch {
            val (_, msg) = geminiProvider.testConnection()
            _uiState.value = _uiState.value.copy(
                isTestingGemini = false,
                geminiTestStatus = msg
            )
        }
    }

    fun setUseLocalModel(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setUseLocalModel(enabled) }
    }

    fun saveLaptopAddress(address: String) {
        val trimmed = address.trim()
        settingsStore.saveLaptopAddress(trimmed)
        ollamaProvider.invalidateReachabilityCache()
        _uiState.value = _uiState.value.copy(
            localTestStatus = null,
            notificationMessage = if (trimmed.isBlank()) "Laptop address cleared." else "Laptop address saved."
        )
    }

    fun setLocalModelName(modelName: String) {
        viewModelScope.launch { settingsStore.setLocalModelName(modelName) }
    }

    fun testLocalConnection(addressOverride: String? = null) {
        if (_uiState.value.isTestingLocal) return
        val addrToTest = addressOverride?.trim()
        if (addrToTest != null) {
            settingsStore.saveLaptopAddress(addrToTest)
        }
        _uiState.value = _uiState.value.copy(isTestingLocal = true, localTestStatus = "Testing...")
        viewModelScope.launch {
            val result = ollamaProvider.testLocalConnection(addrToTest)
            _uiState.value = _uiState.value.copy(
                isTestingLocal = false,
                localTestStatus = result.message
            )
        }
    }

    fun setConfirmDeleteFiles(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setConfirmDeleteFiles(enabled) }
    }

    fun setConfirmCriticalSettings(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setConfirmCriticalSettings(enabled) }
    }

    fun setConfirmNewContacts(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setConfirmNewContacts(enabled) }
    }

    fun setMaxAgentSteps(steps: Int) {
        viewModelScope.launch { settingsStore.setMaxAgentSteps(steps) }
    }

    override fun onCleared() {
        super.onCleared()
        stopController.requestStop()
        voiceController.destroy()
    }
}
