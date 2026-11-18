package com.example.agent.safety

import com.example.agent.tool.ToolSpec
import com.example.data.settings.SettingsStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

data class PendingConfirmation(
    val callId: String,
    val toolName: String,
    val permissionLevel: Int,
    val summary: String,
    val argumentsRedacted: String
)

class SafetyGate(
    @Suppress("unused") private val settingsStore: SettingsStore,
    private val timeoutMs: Long = 60_000L
) {
    private val _pendingConfirmation = MutableStateFlow<PendingConfirmation?>(null)
    val pendingConfirmation: StateFlow<PendingConfirmation?> = _pendingConfirmation.asStateFlow()

    @Volatile
    private var activeDeferred: CompletableDeferred<Boolean>? = null

    /**
     * Confirmation is required ONLY for deletion (file_delete / file.delete and memory_forget / memory.forget).
     */
    fun requiresConfirmation(tool: ToolSpec, @Suppress("unused") argumentsJson: String): Boolean {
        return tool.name in DELETION_TOOLS || tool.apiFunctionName in DELETION_TOOLS
    }

    suspend fun requestApproval(
        callId: String,
        tool: ToolSpec,
        argumentsJson: String,
        redactedArgs: String
    ): Boolean {
        if (!requiresConfirmation(tool, argumentsJson)) {
            return true
        }

        val targetDetail = extractDeletionTarget(tool.name, argumentsJson)
        val deferred = CompletableDeferred<Boolean>()
        activeDeferred = deferred
        _pendingConfirmation.value = PendingConfirmation(
            callId = callId,
            toolName = tool.name,
            permissionLevel = tool.permissionLevel,
            summary = targetDetail,
            argumentsRedacted = redactedArgs
        )

        val approved = withTimeoutOrNull(timeoutMs) {
            deferred.await()
        } ?: false

        _pendingConfirmation.value = null
        activeDeferred = null
        return approved
    }

    fun approve() {
        activeDeferred?.complete(true)
    }

    fun reject() {
        activeDeferred?.complete(false)
    }

    private fun extractDeletionTarget(toolName: String, argumentsJson: String): String {
        val obj = runCatching { JSONObject(argumentsJson) }.getOrNull()
        return if (toolName.contains("file", ignoreCase = true)) {
            val path = obj?.optString("path").orEmpty().ifBlank { "unknown path" }
            "Delete file: $path"
        } else {
            val query = obj?.optString("query")
                ?.ifBlank { obj.optString("text") }
                ?.ifBlank { obj.optString("content") }
                .orEmpty()
                .ifBlank { "matching memory" }
            "Forget memory: $query"
        }
    }

    companion object {
        private val DELETION_TOOLS = setOf("file_delete", "file.delete", "memory_forget", "memory.forget")
    }
}
