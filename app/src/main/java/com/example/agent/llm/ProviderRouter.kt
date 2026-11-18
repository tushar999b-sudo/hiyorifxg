package com.example.agent.llm

import com.example.agent.tool.ToolSpec
import com.example.data.settings.SettingsStore

data class RoutedLlmResult(
    val response: LlmResponse,
    val providerLabel: String,
    val usedFallback: Boolean = false,
    val fallbackReason: String? = null
)

class ProviderRouter(
    private val geminiProvider: LlmProvider,
    private val ollamaProvider: OllamaProvider? = null,
    private val settingsStore: SettingsStore? = null
) {
    suspend fun chat(
        messages: List<LlmMessage>,
        tools: List<ToolSpec>,
        onTokenDelta: (String) -> Unit = {}
    ): RoutedLlmResult {
        val settings = settingsStore?.state?.value
        if (settings == null) {
            val res = geminiProvider.streamChat(messages, tools, onTokenDelta)
            return RoutedLlmResult(
                response = res,
                providerLabel = "via ${res.providerName}"
            )
        }

        val geminiOn = settings.useGeminiApi
        val localOn = settings.useLocalModel

        if (!geminiOn && !localOn) {
            throw LlmProviderException("Please turn on Gemini API or Local model in Settings.")
        }

        // Local-only mode
        if (localOn && !geminiOn) {
            val ollama = ollamaProvider
                ?: throw LlmProviderException("Laptop model is not reachable. Check Wi-Fi and that Ollama is running.")
            if (!ollama.isReachable()) {
                throw LlmProviderException("Laptop model is not reachable. Check Wi-Fi and that Ollama is running.")
            }
            val res = ollama.streamChat(messages, tools, onTokenDelta)
            return RoutedLlmResult(
                response = res,
                providerLabel = "via ${settings.localModelName} (laptop)"
            )
        }

        // Gemini-only mode
        if (!localOn && geminiOn) {
            val res = geminiProvider.streamChat(messages, tools, onTokenDelta)
            return RoutedLlmResult(
                response = res,
                providerLabel = "via Gemini"
            )
        }

        // Both ON:
        // Use Local if it responded to /api/tags within the last 30s (2s timeout, cached 30s), otherwise Gemini.
        // If Local returns an error and Gemini is ON, fall back to Gemini.
        // If Gemini returns 429 and Local is ON, fall back to Local.
        val ollama = ollamaProvider
        val localReachable = ollama?.isReachable() == true

        if (localReachable && ollama != null) {
            try {
                val res = ollama.streamChat(messages, tools, onTokenDelta)
                return RoutedLlmResult(
                    response = res,
                    providerLabel = "via ${settings.localModelName} (laptop)"
                )
            } catch (localErr: LlmProviderException) {
                val geminiRes = geminiProvider.streamChat(messages, tools, onTokenDelta)
                return RoutedLlmResult(
                    response = geminiRes,
                    providerLabel = "via Gemini",
                    usedFallback = true,
                    fallbackReason = localErr.message
                )
            }
        }

        return try {
            val geminiRes = geminiProvider.streamChat(messages, tools, onTokenDelta)
            RoutedLlmResult(
                response = geminiRes,
                providerLabel = "via Gemini"
            )
        } catch (geminiErr: LlmProviderException) {
            if (geminiErr.httpCode == 429 && ollama != null && ollama.isReachable()) {
                val localRes = ollama.streamChat(messages, tools, onTokenDelta)
                RoutedLlmResult(
                    response = localRes,
                    providerLabel = "via ${settings.localModelName} (laptop)",
                    usedFallback = true,
                    fallbackReason = geminiErr.message
                )
            } else {
                throw geminiErr
            }
        }
    }
}
