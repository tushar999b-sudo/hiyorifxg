package com.example.agent.llm

import com.example.agent.tool.ToolSpec
import com.example.data.settings.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit

data class LocalTestResult(
    val connected: Boolean,
    val message: String,
    val installedModels: List<String> = emptyList()
)

open class OllamaProvider(
    private val settingsStore: SettingsStore
) : LlmProvider {

    override val providerType: LlmProviderType = LlmProviderType.OLLAMA

    private val _lastHttpErrorCode = MutableStateFlow<Int?>(null)
    val lastHttpErrorCode: StateFlow<Int?> = _lastHttpErrorCode.asStateFlow()

    private val reachabilityClient = OkHttpClient.Builder()
        .connectTimeout(2, TimeUnit.SECONDS)
        .readTimeout(2, TimeUnit.SECONDS)
        .writeTimeout(2, TimeUnit.SECONDS)
        .build()

    private val testClient = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .writeTimeout(3, TimeUnit.SECONDS)
        .build()

    private val chatClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    @Volatile
    private var lastReachableCheckAtMs: Long = 0L

    @Volatile
    private var lastReachableValue: Boolean = false

    @Volatile
    private var lastCheckedAddress: String = ""

    fun invalidateReachabilityCache() {
        lastReachableCheckAtMs = 0L
        lastReachableValue = false
    }

    suspend fun testLocalConnection(addressOverride: String? = null): LocalTestResult = withContext(Dispatchers.IO) {
        val baseUrl = normalizeBaseUrl(addressOverride ?: settingsStore.getLaptopAddress())
        if (baseUrl.isBlank()) {
            invalidateReachabilityCache()
            return@withContext LocalTestResult(
                connected = false,
                message = "Not reachable. Check Wi-Fi and that Ollama is running."
            )
        }

        val request = Request.Builder()
            .url("$baseUrl/api/tags")
            .header("Accept", "application/json")
            .get()
            .build()

        try {
            testClient.newCall(request).execute().use { response ->
                val code = response.code
                if (!response.isSuccessful) {
                    _lastHttpErrorCode.value = code
                    lastCheckedAddress = baseUrl
                    lastReachableValue = false
                    lastReachableCheckAtMs = System.currentTimeMillis()
                    return@withContext LocalTestResult(
                        connected = false,
                        message = "Not reachable. Check Wi-Fi and that Ollama is running. (HTTP $code)"
                    )
                }

                _lastHttpErrorCode.value = null
                val body = response.body?.string().orEmpty()
                val models = parseInstalledModels(body)
                lastCheckedAddress = baseUrl
                lastReachableValue = true
                lastReachableCheckAtMs = System.currentTimeMillis()

                val summary = if (models.isEmpty()) {
                    "Connected (no models installed)"
                } else {
                    "Connected: ${models.joinToString(", ")}"
                }
                LocalTestResult(
                    connected = true,
                    message = summary,
                    installedModels = models
                )
            }
        } catch (_: Exception) {
            lastCheckedAddress = baseUrl
            lastReachableValue = false
            lastReachableCheckAtMs = System.currentTimeMillis()
            LocalTestResult(
                connected = false,
                message = "Not reachable. Check Wi-Fi and that Ollama is running."
            )
        }
    }

    open suspend fun isReachable(): Boolean = withContext(Dispatchers.IO) {
        val baseUrl = normalizeBaseUrl(settingsStore.getLaptopAddress())
        if (baseUrl.isBlank()) return@withContext false

        val now = System.currentTimeMillis()
        if (baseUrl == lastCheckedAddress && (now - lastReachableCheckAtMs) < REACHABILITY_CACHE_MS) {
            return@withContext lastReachableValue
        }

        val request = Request.Builder()
            .url("$baseUrl/api/tags")
            .header("Accept", "application/json")
            .get()
            .build()

        val reachable = try {
            reachabilityClient.newCall(request).execute().use { it.isSuccessful }
        } catch (_: Exception) {
            false
        }

        lastCheckedAddress = baseUrl
        lastReachableValue = reachable
        lastReachableCheckAtMs = System.currentTimeMillis()
        reachable
    }

    override suspend fun streamChat(
        messages: List<LlmMessage>,
        tools: List<ToolSpec>,
        onTokenDelta: (String) -> Unit
    ): LlmResponse = withContext(Dispatchers.IO) {
        val baseUrl = normalizeBaseUrl(settingsStore.getLaptopAddress())
        if (baseUrl.isBlank()) {
            throw LlmProviderException(
                message = "Laptop model is not reachable. Check Wi-Fi and that Ollama is running.",
                isNetworkError = true
            )
        }

        val modelName = settingsStore.state.value.localModelName
            .ifBlank { com.example.data.settings.HiyoriSettings.DEFAULT_LOCAL_MODEL }

        val payload = JSONObject().apply {
            put("model", modelName)
            put("messages", buildOpenAiMessages(messages))
            if (tools.isNotEmpty()) {
                put("tools", ToolSpec.toOpenAiToolsJson(tools.take(ToolSpec.MAX_TOOLS_PER_REQUEST)))
            }
            put("stream", false)
            put("temperature", 0.1)
        }

        val request = Request.Builder()
            .url("$baseUrl/v1/chat/completions")
            .header("Content-Type", "application/json")
            .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        val responseBody = try {
            chatClient.newCall(request).execute().use { response ->
                val code = response.code
                val bodyStr = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    _lastHttpErrorCode.value = code
                    if (code == 404 || bodyStr.contains("not found", ignoreCase = true)) {
                        throw LlmProviderException(
                            message = "Model '$modelName' is not installed on the laptop. Run: ollama pull $modelName",
                            httpCode = 404
                        )
                    }
                    throw LlmProviderException(
                        message = "Laptop model returned HTTP $code.",
                        httpCode = code
                    )
                }
                _lastHttpErrorCode.value = null
                bodyStr
            }
        } catch (e: LlmProviderException) {
            throw e
        } catch (e: IOException) {
            invalidateReachabilityCache()
            throw LlmProviderException(
                message = "Laptop model is not reachable. Check Wi-Fi and that Ollama is running.",
                isNetworkError = true,
                cause = e
            )
        }

        val root = runCatching { JSONObject(responseBody) }.getOrNull()
            ?: throw LlmProviderException("Laptop model returned an invalid response.")

        val choices = root.optJSONArray("choices")
        val firstChoice = choices?.optJSONObject(0)
        val messageObj = firstChoice?.optJSONObject("message")

        val rawContent = messageObj?.optString("content")?.takeIf { it != "null" }.orEmpty()
        val toolCallsArr = messageObj?.optJSONArray("tool_calls")
        val nativeToolCalls = mutableListOf<LlmToolCall>()

        if (toolCallsArr != null) {
            for (i in 0 until toolCallsArr.length()) {
                val tcObj = toolCallsArr.optJSONObject(i) ?: continue
                val id = tcObj.optString("id").ifBlank { "call_${UUID.randomUUID().toString().take(8)}" }
                val fnObj = tcObj.optJSONObject("function") ?: continue
                val name = fnObj.optString("name").trim()
                val argsRaw = fnObj.opt("arguments")
                val argsJson = when (argsRaw) {
                    is JSONObject -> argsRaw.toString()
                    is String -> argsRaw.ifBlank { "{}" }
                    else -> "{}"
                }
                if (name.isNotBlank()) {
                    nativeToolCalls.add(
                        LlmToolCall(
                            id = id,
                            name = name,
                            argumentsJson = argsJson
                        )
                    )
                }
            }
        }

        val parsed = LlmResponseParser.parse(rawContent, nativeToolCalls)
        if (parsed.visibleText.isNotEmpty()) {
            onTokenDelta(parsed.visibleText)
        }

        LlmResponse(
            text = parsed.visibleText,
            toolCalls = parsed.toolCalls,
            mood = parsed.mood,
            providerName = "$modelName (laptop)"
        )
    }

    private fun buildOpenAiMessages(messages: List<LlmMessage>): JSONArray {
        return JSONArray().apply {
            messages.forEach { msg ->
                val obj = JSONObject().put("role", msg.role)
                if (msg.role == "assistant" && msg.toolCalls.isNotEmpty()) {
                    obj.put("content", msg.content)
                    val tcArr = JSONArray()
                    msg.toolCalls.forEach { tc ->
                        tcArr.put(
                            JSONObject().apply {
                                put("id", tc.id)
                                put("type", "function")
                                put(
                                    "function",
                                    JSONObject().apply {
                                        put("name", tc.name.replace('.', '_'))
                                        put("arguments", tc.argumentsJson)
                                    }
                                )
                            }
                        )
                    }
                    obj.put("tool_calls", tcArr)
                } else if (msg.role == "tool") {
                    obj.put("tool_call_id", msg.toolCallId ?: "call_0")
                    obj.put("content", msg.content)
                } else {
                    obj.put("content", msg.content)
                }
                put(obj)
            }
        }
    }

    companion object {
        private const val REACHABILITY_CACHE_MS = 30_000L

        fun normalizeBaseUrl(raw: String): String {
            val trimmed = raw.trim().trimEnd('/')
            if (trimmed.isBlank()) return ""
            return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                trimmed
            } else {
                "http://$trimmed"
            }
        }

        fun parseInstalledModels(tagsJson: String): List<String> {
            val root = runCatching { Json.parseToJsonElement(tagsJson).jsonObject }.getOrNull() ?: return emptyList()
            val modelsArr = root["models"]?.jsonArray ?: return emptyList()
            return modelsArr.mapNotNull { element ->
                element.jsonObject["name"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotBlank() }
            }
        }
    }
}
