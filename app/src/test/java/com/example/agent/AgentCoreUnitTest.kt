package com.example.agent

import com.example.agent.audit.AuditLogger
import com.example.agent.llm.GeminiProvider
import com.example.agent.llm.LlmMessage
import com.example.agent.llm.LlmProvider
import com.example.agent.llm.LlmProviderException
import com.example.agent.llm.LlmProviderType
import com.example.agent.llm.LlmResponse
import com.example.agent.llm.LlmResponseParser
import com.example.agent.llm.LlmToolCall
import com.example.agent.llm.OllamaProvider
import com.example.agent.llm.ProviderRouter
import com.example.agent.loop.AgentLoop
import com.example.agent.loop.StopController
import com.example.agent.memory.MemoryManager
import com.example.agent.safety.SafetyGate
import com.example.agent.tool.ToolRegistry
import com.example.agent.tool.ToolSpec
import com.example.agent.tool.ToolValidationResult
import com.example.agent.tool.ToolValidator
import com.example.avatar.HiyoriExpression
import com.example.data.dao.AuditLogDao
import com.example.data.dao.MemoryDao
import com.example.data.model.AuditLogEntity
import com.example.data.model.MemoryEntity
import com.example.data.settings.InMemorySettingsBackend
import com.example.data.settings.SettingsStore
import com.example.data.settings.maskApiKey
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentCoreUnitTest {

    private fun createTestSettingsStore(): SettingsStore = SettingsStore(InMemorySettingsBackend())

    private class InMemoryAuditLogDao : AuditLogDao {
        val logs = mutableListOf<AuditLogEntity>()
        private val flow = MutableStateFlow<List<AuditLogEntity>>(emptyList())

        override fun getRecentLogsFlow(): Flow<List<AuditLogEntity>> = flow
        override suspend fun insertLog(log: AuditLogEntity): Long {
            val item = log.copy(id = (logs.size + 1).toLong())
            logs.add(item)
            flow.value = logs.toList()
            return item.id
        }
        override suspend fun updateLog(log: AuditLogEntity) {
            val idx = logs.indexOfFirst { it.id == log.id }
            if (idx >= 0) logs[idx] = log
            flow.value = logs.toList()
        }
        override suspend fun updateLogStatus(id: Long, status: String, result: String?) {
            val idx = logs.indexOfFirst { it.id == id }
            if (idx >= 0) logs[idx] = logs[idx].copy(status = status, resultDetails = result)
            flow.value = logs.toList()
        }
        override suspend fun clearLogs() {
            logs.clear()
            flow.value = emptyList()
        }
    }

    private class InMemoryMemoryDao : MemoryDao {
        val items = mutableListOf<MemoryEntity>()
        private val flow = MutableStateFlow<List<MemoryEntity>>(emptyList())

        override fun getAllMemoriesFlow(): Flow<List<MemoryEntity>> = flow
        override suspend fun getAllMemories(): List<MemoryEntity> = items.toList()
        override suspend fun searchMemories(query: String): List<MemoryEntity> =
            items.filter { it.content.contains(query, ignoreCase = true) }
        override fun getMemoriesByCategory(category: String): Flow<List<MemoryEntity>> = flow
        override suspend fun insertMemory(memory: MemoryEntity): Long {
            val saved = memory.copy(id = (items.size + 1).toLong())
            items.add(saved)
            flow.value = items.toList()
            return saved.id
        }
        override suspend fun updateMemory(memory: MemoryEntity) {
            val idx = items.indexOfFirst { it.id == memory.id }
            if (idx >= 0) items[idx] = memory
            flow.value = items.toList()
        }
        override suspend fun deleteMemory(memory: MemoryEntity) {
            items.removeAll { it.id == memory.id }
            flow.value = items.toList()
        }
        override suspend fun deleteMemoryById(id: Long) {
            items.removeAll { it.id == id }
            flow.value = items.toList()
        }
        override suspend fun clearAllMemories() {
            items.clear()
            flow.value = emptyList()
        }
    }

    @Test
    fun geminiModelSelection_picksFirstFlashWithGenerateContent() {
        val json = """
            {
              "models": [
                {"name": "models/embedding-001", "supportedGenerationMethods": ["embedContent"]},
                {"name": "models/gemini-2.0-flash-lite-preview", "supportedGenerationMethods": ["countTokens"]},
                {"name": "models/gemini-2.5-flash", "supportedGenerationMethods": ["generateContent", "countTokens"]},
                {"name": "models/gemini-2.5-pro", "supportedGenerationMethods": ["generateContent"]}
              ]
            }
        """.trimIndent()

        assertEquals("gemini-2.5-flash", GeminiProvider.selectFirstFlashModel(json))
    }

    @Test
    fun geminiModelSelection_returnsNullWhenNoFlashGenerateContentExists() {
        val json = """
            {
              "models": [
                {"name": "models/gemini-2.5-pro", "supportedGenerationMethods": ["generateContent"]}
              ]
            }
        """.trimIndent()

        assertNull(GeminiProvider.selectFirstFlashModel(json))
    }

    @Test
    fun maskApiKey_formatsAsPrefixEllipsisLastFour() {
        assertEquals("AIza...wxyz", maskApiKey("AIzaSy1234567890wxyz"))
        assertEquals("", maskApiKey("   "))
    }

    @Test
    fun safetyGate_approveRejectAndTimeoutWorkCorrectly() = runBlocking {
        val settingsStore = createTestSettingsStore()
        val deleteSpec = ToolSpec.findByName("file.delete")!!

        val gateApprove = SafetyGate(settingsStore, timeoutMs = 2000L)
        val approveJob = launch {
            delay(30L)
            gateApprove.approve()
        }
        val approved = gateApprove.requestApproval("c1", deleteSpec, """{"path":"a.txt"}""", """{"path":"a.txt"}""")
        approveJob.join()
        assertTrue(approved)

        val gateReject = SafetyGate(settingsStore, timeoutMs = 2000L)
        val rejectJob = launch {
            delay(30L)
            gateReject.reject()
        }
        val rejected = gateReject.requestApproval("c2", deleteSpec, """{"path":"a.txt"}""", """{"path":"a.txt"}""")
        rejectJob.join()
        assertFalse(rejected)

        val gateTimeout = SafetyGate(settingsStore, timeoutMs = 80L)
        val timedOut = gateTimeout.requestApproval("c3", deleteSpec, """{"path":"a.txt"}""", """{"path":"a.txt"}""")
        assertFalse(timedOut)
    }

    @Test
    fun stopController_cancelsRunningAgentLoopImmediately() = runBlocking {
        val settingsStore = createTestSettingsStore()
        val stopController = StopController()
        val safetyGate = SafetyGate(settingsStore)
        val auditLogger = AuditLogger(InMemoryAuditLogDao())
        val memoryManager = MemoryManager(InMemoryMemoryDao())
        val toolRegistry = ToolRegistry(memoryManager, stopController)

        val slowProvider = object : LlmProvider {
            override val providerType = LlmProviderType.GEMINI
            override suspend fun streamChat(
                messages: List<LlmMessage>,
                tools: List<ToolSpec>,
                onTokenDelta: (String) -> Unit
            ): LlmResponse {
                delay(5_000L)
                return LlmResponse("Done", emptyList(), HiyoriExpression.HAPPY, "Gemini")
            }
        }

        val router = ProviderRouter(slowProvider)
        val loop = AgentLoop(router, toolRegistry, safetyGate, stopController, auditLogger, memoryManager, settingsStore)

        val completed = CompletableDeferred<String>()
        loop.runGoal(
            scope = this,
            systemPrompt = "System",
            userGoal = "Run slow task",
            shortHistory = emptyList(),
            onComplete = { text, _ -> completed.complete(text) }
        )

        delay(50L)
        assertTrue(loop.state.value.running)
        stopController.requestStop()

        val finalMsg = withTimeout(2_000L) { completed.await() }
        assertTrue(stopController.isStopped.value)
        assertFalse(loop.state.value.running)
        assertTrue(finalMsg.contains("ruk gaya", ignoreCase = true))
    }

    @Test
    fun agentLoop_enforcesMax3LoopsAndAllowsOneValidationRetry() = runBlocking {
        val settingsStore = createTestSettingsStore()
        val stopController = StopController()
        val safetyGate = SafetyGate(settingsStore)
        val auditDao = InMemoryAuditLogDao()
        val auditLogger = AuditLogger(auditDao)
        val memoryManager = MemoryManager(InMemoryMemoryDao())
        val toolRegistry = ToolRegistry(memoryManager, stopController)

        var callCount = 0
        val provider = object : LlmProvider {
            override val providerType = LlmProviderType.GEMINI
            override suspend fun streamChat(
                messages: List<LlmMessage>,
                tools: List<ToolSpec>,
                onTokenDelta: (String) -> Unit
            ): LlmResponse {
                callCount++
                return when (callCount) {
                    1 -> LlmResponse(
                        text = "",
                        toolCalls = listOf(LlmToolCall("c1", "torch", """{"state":"invalid_enum"}""")),
                        mood = HiyoriExpression.THINKING,
                        providerName = "Gemini"
                    )
                    2 -> LlmResponse(
                        text = "",
                        toolCalls = listOf(LlmToolCall("c2", "memory_search", """{"query":"test"}""")),
                        mood = HiyoriExpression.THINKING,
                        providerName = "Gemini"
                    )
                    else -> LlmResponse(
                        text = "Maine check kar liya Master!",
                        toolCalls = emptyList(),
                        mood = HiyoriExpression.HAPPY,
                        providerName = "Gemini"
                    )
                }
            }
        }

        val router = ProviderRouter(provider)
        val loop = AgentLoop(router, toolRegistry, safetyGate, stopController, auditLogger, memoryManager, settingsStore)

        val completed = CompletableDeferred<String>()
        loop.runGoal(
            scope = this,
            systemPrompt = "System",
            userGoal = "Test validation retry",
            shortHistory = emptyList(),
            onComplete = { text, _ -> completed.complete(text) }
        )

        val finalMsg = withTimeout(3_000L) { completed.await() }
        assertFalse(loop.state.value.running)
        assertEquals("Maine check kar liya Master!", finalMsg)
        assertEquals(2, auditDao.logs.size)
    }

    @Test
    fun toolSpec_capsAt12ToolsExcludesGlobalBackAndMapsOptimizeToClearCache() {
        val defaultTools = ToolSpec.selectToolsForRequest("Hi Hiyori, kaise ho?")
        assertEquals(12, defaultTools.size)
        assertNull(ToolSpec.findByName("global_back"))
        assertEquals("clear_cache", ToolSpec.findByName("optimize")?.name)
        assertEquals("clear_cache", ToolSpec.findByName("mobile.optimize_device")?.name)
    }

    @Test
    fun toolValidator_validatesRequiredFieldsTypesEnumsAndUnknownKeys() {
        // Valid enum (case-insensitive normalized)
        val validTorch = ToolValidator.validate("torch", """{"state":"ON"}""")
        assertTrue(validTorch is ToolValidationResult.Valid)
        assertEquals("on", (validTorch as ToolValidationResult.Valid).normalizedParameters["state"])

        // Unknown key rejected
        val unknownKey = ToolValidator.validate("torch", """{"state":"on","extra":123}""")
        assertTrue(unknownKey is ToolValidationResult.Invalid)

        // Wrong type rejected (string instead of integer)
        val wrongType = ToolValidator.validate("brightness", """{"level":"200"}""")
        assertTrue(wrongType is ToolValidationResult.Invalid)

        // Missing required field rejected
        val missingReq = ToolValidator.validate("open_app", """{}""")
        assertTrue(missingReq is ToolValidationResult.Invalid)
    }

    @Test
    fun llmResponseParser_extractsFencedToolJsonAndMoodLine() {
        val raw = """
            ```json
            {"name": "torch", "arguments": {"state": "on"}}
            ```
            mood: EXCITED
        """.trimIndent()

        val parsed = LlmResponseParser.parse(raw, emptyList())
        assertEquals(1, parsed.toolCalls.size)
        assertEquals("torch", parsed.toolCalls[0].name)
        assertEquals("", parsed.visibleText)
        assertEquals(HiyoriExpression.EXCITED, parsed.mood)
    }

    @Test
    fun providerRouter_whenBothTogglesOff_throwsExactError() = runBlocking {
        val settingsStore = createTestSettingsStore()
        val dummyGemini = object : LlmProvider {
            override val providerType = LlmProviderType.GEMINI
            override suspend fun streamChat(
                messages: List<LlmMessage>,
                tools: List<ToolSpec>,
                onTokenDelta: (String) -> Unit
            ): LlmResponse = LlmResponse("hi", emptyList(), HiyoriExpression.NEUTRAL, "Gemini")
        }
        val router = ProviderRouter(
            geminiProvider = dummyGemini,
            settingsStore = settingsStore
        )

        var caughtMessage: String? = null
        try {
            router.chat(listOf(LlmMessage("user", "hello")), emptyList())
        } catch (e: LlmProviderException) {
            caughtMessage = e.message
        }
        assertEquals("Please turn on Gemini API or Local model in Settings.", caughtMessage)
    }

    @Test
    fun providerRouter_whenBothOnAndLocalReachable_usesLocalFirstAndFallsBackToGemini() = runBlocking {
        val settingsStore = createTestSettingsStore()
        settingsStore.setUseGeminiApi(true)
        settingsStore.setUseLocalModel(true)
        settingsStore.setLocalModelName("qwen2.5:7b")

        var localShouldFail = false
        val fakeOllama = object : OllamaProvider(settingsStore) {
            override suspend fun isReachable(): Boolean = true
            override suspend fun streamChat(
                messages: List<LlmMessage>,
                tools: List<ToolSpec>,
                onTokenDelta: (String) -> Unit
            ): LlmResponse {
                if (localShouldFail) {
                    throw LlmProviderException("Laptop model is not reachable. Check Wi-Fi and that Ollama is running.", isNetworkError = true)
                }
                return LlmResponse("Local reply", emptyList(), HiyoriExpression.HAPPY, "qwen2.5:7b (laptop)")
            }
        }
        val fakeGemini = object : LlmProvider {
            override val providerType = LlmProviderType.GEMINI
            override suspend fun streamChat(
                messages: List<LlmMessage>,
                tools: List<ToolSpec>,
                onTokenDelta: (String) -> Unit
            ): LlmResponse = LlmResponse("Gemini reply", emptyList(), HiyoriExpression.HAPPY, "Gemini")
        }

        val router = ProviderRouter(
            geminiProvider = fakeGemini,
            ollamaProvider = fakeOllama,
            settingsStore = settingsStore
        )

        val first = router.chat(listOf(LlmMessage("user", "hi")), emptyList())
        assertEquals("Local reply", first.response.text)
        assertEquals("via qwen2.5:7b (laptop)", first.providerLabel)
        assertFalse(first.usedFallback)

        localShouldFail = true
        val second = router.chat(listOf(LlmMessage("user", "hi")), emptyList())
        assertEquals("Gemini reply", second.response.text)
        assertEquals("via Gemini", second.providerLabel)
        assertTrue(second.usedFallback)
    }

    @Test
    fun providerRouter_whenGeminiReturns429AndLocalReachable_retriesOnLocal() = runBlocking {
        val settingsStore = createTestSettingsStore()
        settingsStore.setUseGeminiApi(true)
        settingsStore.setUseLocalModel(true)
        settingsStore.setLocalModelName("dolphin3:8b")

        var reachabilityCalls = 0
        val fakeOllama = object : OllamaProvider(settingsStore) {
            override suspend fun isReachable(): Boolean {
                reachabilityCalls++
                return reachabilityCalls >= 2
            }
            override suspend fun streamChat(
                messages: List<LlmMessage>,
                tools: List<ToolSpec>,
                onTokenDelta: (String) -> Unit
            ): LlmResponse = LlmResponse("Local 429 fallback", emptyList(), HiyoriExpression.HAPPY, "dolphin3:8b (laptop)")
        }
        val rateLimitedGemini = object : LlmProvider {
            override val providerType = LlmProviderType.GEMINI
            override suspend fun streamChat(
                messages: List<LlmMessage>,
                tools: List<ToolSpec>,
                onTokenDelta: (String) -> Unit
            ): LlmResponse {
                throw LlmProviderException("Rate limit reached. Please wait a moment and try again.", httpCode = 429)
            }
        }

        val router = ProviderRouter(
            geminiProvider = rateLimitedGemini,
            ollamaProvider = fakeOllama,
            settingsStore = settingsStore
        )

        val result = router.chat(listOf(LlmMessage("user", "hi")), emptyList())
        assertEquals("Local 429 fallback", result.response.text)
        assertEquals("via dolphin3:8b (laptop)", result.providerLabel)
        assertTrue(result.usedFallback)
    }

    @Test
    fun ollamaProvider_parseInstalledModels_extractsModelNames() {
        val json = """
            {
              "models": [
                {"name": "qwen2.5:7b", "size": 4683075271},
                {"name": "qwen2.5-coder:7b", "size": 4683075271},
                {"name": "dolphin3:8b", "size": 4920739200}
              ]
            }
        """.trimIndent()
        val models = OllamaProvider.parseInstalledModels(json)
        assertEquals(listOf("qwen2.5:7b", "qwen2.5-coder:7b", "dolphin3:8b"), models)
    }
}
