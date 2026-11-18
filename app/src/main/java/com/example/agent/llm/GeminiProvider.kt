package com.example.agent.llm

import com.example.agent.tool.ParamType
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

class GeminiProvider(
    private val settingsStore: SettingsStore,
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(25, TimeUnit.SECONDS)
        .build()
) : LlmProvider {

    override val providerType: LlmProviderType = LlmProviderType.GEMINI

    private val _lastHttpErrorCode = MutableStateFlow<Int?>(null)
    val lastHttpErrorCode: StateFlow<Int?> = _lastHttpErrorCode.asStateFlow()

    suspend fun discoverAndCacheFlashModel(apiKeyOverride: String? = null): String = withContext(Dispatchers.IO) {
        val apiKey = (apiKeyOverride ?: settingsStore.getGeminiApiKey()).trim()
        if (apiKey.isBlank()) {
            throw LlmProviderException("Please add your Gemini API key in Settings.")
        }

        val request = Request.Builder()
            .url("$GEMINI_BASE_URL/models")
            .header("x-goog-api-key", apiKey)
            .header("Accept", "application/json")
            .get()
            .build()

        val bodyString = try {
            httpClient.newCall(request).execute().use { response ->
                val code = response.code
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    _lastHttpErrorCode.value = code
                    throw mapHttpError(code, body)
                }
                _lastHttpErrorCode.value = null
                body
            }
        } catch (e: LlmProviderException) {
            throw e
        } catch (e: IOException) {
            throw LlmProviderException(
                message = "Could not reach Gemini. Check your internet connection.",
                isNetworkError = true,
                cause = e
            )
        }

        val selected = selectFirstFlashModel(bodyString)
            ?: throw LlmProviderException("No Gemini Flash model with generateContent found for this API key.")

        settingsStore.setCachedGeminiModel(selected)
        selected
    }

    suspend fun testConnection(): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val apiKey = settingsStore.getGeminiApiKey().trim()
        if (apiKey.isBlank()) {
            return@withContext false to "Please add your Gemini API key in Settings."
        }

        try {
            val model = discoverAndCacheFlashModel(apiKey)
            val url = "$GEMINI_BASE_URL/models/$model:generateContent"
            val payload = JSONObject().apply {
                put(
                    "contents",
                    JSONArray().put(
                        JSONObject().apply {
                            put("role", "user")
                            put("parts", JSONArray().put(JSONObject().put("text", "hi")))
                        }
                    )
                )
                put("generationConfig", JSONObject().put("maxOutputTokens", 1))
            }

            val request = Request.Builder()
                .url(url)
                .header("Content-Type", "application/json")
                .header("x-goog-api-key", apiKey)
                .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()

            httpClient.newCall(request).execute().use { response ->
                val code = response.code
                val body = response.body?.string().orEmpty()
                if (response.isSuccessful) {
                    _lastHttpErrorCode.value = null
                    true to "Connected ($model, HTTP $code)"
                } else {
                    _lastHttpErrorCode.value = code
                    val mapped = mapHttpError(code, body)
                    false to "${mapped.message} (HTTP $code)"
                }
            }
        } catch (e: LlmProviderException) {
            false to (e.message ?: "Could not connect to Gemini.")
        } catch (_: IOException) {
            false to "Network error: Could not reach Gemini."
        }
    }

    override suspend fun streamChat(
        messages: List<LlmMessage>,
        tools: List<ToolSpec>,
        onTokenDelta: (String) -> Unit
    ): LlmResponse = withContext(Dispatchers.IO) {
        val apiKey = settingsStore.getGeminiApiKey().trim()
        if (apiKey.isBlank()) {
            throw LlmProviderException("Please add your Gemini API key in Settings.")
        }

        var model = settingsStore.state.value.cachedGeminiModel.trim()
        if (model.isBlank()) {
            model = discoverAndCacheFlashModel(apiKey)
        }

        val payload = buildGeminiPayload(messages, tools)

        var responseBody = executeGenerateContent(apiKey, model, payload, allow404Retry = true)
        val parsedResponse = parseGeminiResponse(responseBody)
        if (parsedResponse.text.isNotEmpty()) {
            onTokenDelta(parsedResponse.text)
        }
        parsedResponse
    }

    private suspend fun executeGenerateContent(
        apiKey: String,
        model: String,
        payload: JSONObject,
        allow404Retry: Boolean
    ): String {
        val url = "$GEMINI_BASE_URL/models/$model:generateContent"
        val request = Request.Builder()
            .url(url)
            .header("Content-Type", "application/json")
            .header("x-goog-api-key", apiKey)
            .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        return try {
            httpClient.newCall(request).execute().use { response ->
                val code = response.code
                val bodyStr = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    if (code == 404 && allow404Retry) {
                        settingsStore.clearCachedGeminiModel()
                        val freshModel = discoverAndCacheFlashModel(apiKey)
                        return executeGenerateContent(apiKey, freshModel, payload, allow404Retry = false)
                    }
                    _lastHttpErrorCode.value = code
                    throw mapHttpError(code, bodyStr)
                }
                _lastHttpErrorCode.value = null
                bodyStr
            }
        } catch (e: LlmProviderException) {
            throw e
        } catch (e: IOException) {
            throw LlmProviderException(
                message = "Could not reach Gemini. Check your internet connection.",
                isNetworkError = true,
                cause = e
            )
        }
    }

    private fun mapHttpError(code: Int, bodyStr: String): LlmProviderException {
        return when {
            code == 401 || code == 403 || (code == 400 && bodyStr.contains("API_KEY_INVALID", ignoreCase = true)) ->
                LlmProviderException(
                    message = "The API key is invalid. Please paste it again in Settings.",
                    httpCode = 401
                )
            code == 429 ->
                LlmProviderException(
                    message = "Rate limit reached. Please wait a moment and try again.",
                    httpCode = 429
                )
            else ->
                LlmProviderException(
                    message = "Gemini request failed. (HTTP $code)",
                    httpCode = code
                )
        }
    }

    private fun buildGeminiPayload(messages: List<LlmMessage>, tools: List<ToolSpec>): JSONObject {
        val systemTexts = mutableListOf<String>()
        val contents = JSONArray()

        for (msg in messages) {
            when (msg.role) {
                "system" -> {
                    if (msg.content.isNotBlank()) systemTexts.add(msg.content)
                }
                "user" -> {
                    contents.put(
                        JSONObject().apply {
                            put("role", "user")
                            put("parts", JSONArray().put(JSONObject().put("text", msg.content)))
                        }
                    )
                }
                "assistant" -> {
                    val parts = JSONArray()
                    if (msg.content.isNotBlank()) {
                        parts.put(JSONObject().put("text", msg.content))
                    }
                    msg.toolCalls.forEach { tc ->
                        val argsObj = runCatching { JSONObject(tc.argumentsJson) }.getOrDefault(JSONObject())
                        parts.put(
                            JSONObject().put(
                                "functionCall",
                                JSONObject().apply {
                                    put("name", tc.name.replace('.', '_'))
                                    put("args", argsObj)
                                }
                            )
                        )
                    }
                    if (parts.length() > 0) {
                        contents.put(
                            JSONObject().apply {
                                put("role", "model")
                                put("parts", parts)
                            }
                        )
                    }
                }
                "tool" -> {
                    val fnName = (msg.toolName ?: "tool").replace('.', '_')
                    val respObj = JSONObject().put("result", msg.content)
                    val fnResp = JSONObject().apply {
                        put("name", fnName)
                        put("response", respObj)
                    }
                    contents.put(
                        JSONObject().apply {
                            put("role", "user")
                            put("parts", JSONArray().put(JSONObject().put("functionResponse", fnResp)))
                        }
                    )
                }
            }
        }

        return JSONObject().apply {
            if (systemTexts.isNotEmpty()) {
                put(
                    "systemInstruction",
                    JSONObject().put(
                        "parts",
                        JSONArray().put(JSONObject().put("text", systemTexts.joinToString("\n\n")))
                    )
                )
            }
            put("contents", contents)

            if (tools.isNotEmpty()) {
                val declarations = JSONArray()
                tools.take(ToolSpec.MAX_TOOLS_PER_REQUEST).forEach { spec ->
                    val props = JSONObject()
                    val req = JSONArray()
                    spec.parameters.forEach { param ->
                        val pObj = JSONObject().apply {
                            put(
                                "type",
                                when (param.type) {
                                    ParamType.STRING -> "STRING"
                                    ParamType.INTEGER -> "INTEGER"
                                    ParamType.NUMBER -> "NUMBER"
                                    ParamType.BOOLEAN -> "BOOLEAN"
                                }
                            )
                            put("description", param.description)
                            if (!param.enumValues.isNullOrEmpty()) {
                                put("enum", JSONArray(param.enumValues))
                            }
                        }
                        props.put(param.name, pObj)
                        if (param.required) req.put(param.name)
                    }
                    val schema = JSONObject().apply {
                        put("type", "OBJECT")
                        put("properties", props)
                        if (req.length() > 0) put("required", req)
                    }
                    declarations.put(
                        JSONObject().apply {
                            put("name", spec.apiFunctionName)
                            put("description", spec.description)
                            put("parameters", schema)
                        }
                    )
                }
                put("tools", JSONArray().put(JSONObject().put("functionDeclarations", declarations)))
            }
        }
    }

    private fun parseGeminiResponse(bodyStr: String): LlmResponse {
        val root = JSONObject(bodyStr)
        val candidates = root.optJSONArray("candidates")
        val firstCandidate = candidates?.optJSONObject(0)
        val content = firstCandidate?.optJSONObject("content")
        val parts = content?.optJSONArray("parts")

        val textBuilder = StringBuilder()
        val nativeToolCalls = mutableListOf<LlmToolCall>()

        if (parts != null) {
            for (i in 0 until parts.length()) {
                val part = parts.optJSONObject(i) ?: continue
                if (part.has("text")) {
                    textBuilder.append(part.optString("text"))
                }
                val fnCall = part.optJSONObject("functionCall")
                if (fnCall != null) {
                    val fnName = fnCall.optString("name").trim()
                    val argsObj = fnCall.optJSONObject("args") ?: JSONObject()
                    if (fnName.isNotBlank()) {
                        nativeToolCalls.add(
                            LlmToolCall(
                                id = "call_${UUID.randomUUID().toString().take(8)}",
                                name = fnName,
                                argumentsJson = argsObj.toString()
                            )
                        )
                    }
                }
            }
        }

        val parsed = LlmResponseParser.parse(textBuilder.toString(), nativeToolCalls)
        return LlmResponse(
            text = parsed.visibleText,
            toolCalls = parsed.toolCalls,
            mood = parsed.mood,
            providerName = "Gemini"
        )
    }

    companion object {
        private const val GEMINI_BASE_URL = "https://generativelanguage.googleapis.com/v1beta"

        fun selectFirstFlashModel(modelsJson: String): String? {
            val root = runCatching { Json.parseToJsonElement(modelsJson).jsonObject }.getOrNull() ?: return null
            val modelsArr = root["models"]?.jsonArray ?: return null
            for (element in modelsArr) {
                val obj = element.jsonObject
                val rawName = obj["name"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                if (!rawName.contains("flash", ignoreCase = true)) continue
                val methods = obj["supportedGenerationMethods"]?.jsonArray
                    ?.mapNotNull { it.jsonPrimitive.contentOrNull }
                    ?: emptyList()
                if ("generateContent" in methods) {
                    return rawName.removePrefix("models/")
                }
            }
            return null
        }
    }
}
