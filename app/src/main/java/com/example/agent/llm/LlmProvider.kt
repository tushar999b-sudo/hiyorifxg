package com.example.agent.llm

import com.example.agent.tool.ToolSpec
import com.example.avatar.HiyoriExpression

enum class LlmProviderType(val displayName: String) {
    GEMINI("Gemini"),
    OLLAMA("Ollama");

    companion object {
        fun fromStoredName(raw: String?): LlmProviderType {
            return when (raw?.trim()?.uppercase()) {
                "OLLAMA" -> OLLAMA
                else -> GEMINI
            }
        }
    }
}

data class LlmToolCall(
    val id: String,
    val name: String,
    val argumentsJson: String
)

data class LlmMessage(
    val role: String,
    val content: String,
    val toolCalls: List<LlmToolCall> = emptyList(),
    val toolCallId: String? = null,
    val toolName: String? = null,
    val imageBase64: String? = null
)

data class LlmResponse(
    val text: String,
    val toolCalls: List<LlmToolCall> = emptyList(),
    val mood: HiyoriExpression = HiyoriExpression.NEUTRAL,
    val providerName: String,
    val goalAchieved: Boolean? = null
)

class LlmProviderException(
    message: String,
    val httpCode: Int? = null,
    val isNetworkError: Boolean = false,
    cause: Throwable? = null
) : Exception(message, cause)

interface LlmProvider {
    val providerType: LlmProviderType

    suspend fun streamChat(
        messages: List<LlmMessage>,
        tools: List<ToolSpec>,
        onTokenDelta: (String) -> Unit = {}
    ): LlmResponse
}
