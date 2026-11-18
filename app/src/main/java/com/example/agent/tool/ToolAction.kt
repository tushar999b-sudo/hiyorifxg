package com.example.agent.tool

enum class PermissionLevel(val level: Int, val title: String, val requiresUserConfirmation: Boolean) {
    LEVEL_0(0, "Read-Only (Safe)", false),
    LEVEL_1(1, "Safe Local Actions", false),
    LEVEL_2(2, "Device Interaction", false),
    LEVEL_3(3, "External Communication", false),
    LEVEL_4(4, "Destructive Deletion", true);

    companion object {
        fun fromInt(level: Int): PermissionLevel = entries.find { it.level == level } ?: LEVEL_2
    }
}

enum class ToolResultStatus(val code: String) {
    SUCCESS("success"),
    NEEDS_SETUP("needs_setup"),
    NOT_FOUND("not_found"),
    PERMISSION_DENIED("permission_denied"),
    FAILED("failed")
}

data class ToolDefinition(
    val name: String,
    val description: String,
    val permissionLevel: PermissionLevel,
    val parameterSchemaDescription: String
)

data class ToolCallRequest(
    val callId: String,
    val toolName: String,
    val parameters: Map<String, Any>,
    val permissionLevel: PermissionLevel
)

data class ToolExecutionResult(
    val callId: String,
    val toolName: String,
    val success: Boolean,
    val resultMessage: String,
    val status: ToolResultStatus = if (success) ToolResultStatus.SUCCESS else ToolResultStatus.FAILED,
    val requiredSetting: String? = null,
    val data: Map<String, Any> = emptyMap(),
    val error: String? = null
) {
    fun toDisplayStatusLine(): String {
        return when (status) {
            ToolResultStatus.SUCCESS -> "success"
            ToolResultStatus.NEEDS_SETUP -> {
                if (!requiredSetting.isNullOrBlank()) "needs_setup ($requiredSetting)" else "needs_setup"
            }
            ToolResultStatus.NOT_FOUND -> "not_found"
            ToolResultStatus.PERMISSION_DENIED -> "failed (permission denied)"
            ToolResultStatus.FAILED -> {
                val reason = resultMessage
                    .removePrefix("FAILED:")
                    .trim()
                    .take(80)
                    .ifBlank { "error" }
                "failed ($reason)"
            }
        }
    }
}
