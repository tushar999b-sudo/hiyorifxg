package com.example.agent.audit

import com.example.data.dao.AuditLogDao
import com.example.data.model.ActionStatus
import com.example.data.model.AuditLogEntity
import org.json.JSONObject

class AuditLogger(
    private val auditLogDao: AuditLogDao
) {
    suspend fun logAction(
        stepNumber: Int,
        toolName: String,
        target: String,
        rawArgumentsJson: String,
        permissionLevel: Int,
        status: ActionStatus,
        resultSummary: String,
        durationMs: Long
    ) {
        val redactedArgs = redactSensitiveData(rawArgumentsJson)
        val redactedResult = redactSensitiveData(resultSummary)
        auditLogDao.insertLog(
            AuditLogEntity(
                stepNumber = stepNumber,
                actionName = toolName,
                target = target,
                parametersJson = redactedArgs,
                permissionLevel = permissionLevel,
                status = status.name,
                resultDetails = redactedResult,
                durationMs = durationMs
            )
        )
    }

    companion object {
        private val API_KEY_REGEX = Regex("""(gsk_[A-Za-z0-9_\-]{8,}|AIza[A-Za-z0-9_\-]{12,}|sk-[A-Za-z0-9_\-]{12,}|Bearer\s+[A-Za-z0-9._\-]{10,})""")
        private val PHONE_REGEX = Regex("""(\+?\d[\d\s\-()]{7,}\d)""")
        private val SENSITIVE_KEYS = setOf("api_key", "apikey", "token", "secret", "password", "authorization")

        fun redactSensitiveData(input: String): String {
            if (input.isBlank()) return input
            var sanitized = input

            // Redact JSON fields with sensitive keys
            runCatching {
                val obj = JSONObject(sanitized)
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    if (k.lowercase() in SENSITIVE_KEYS) {
                        obj.put(k, "[REDACTED]")
                    }
                }
                sanitized = obj.toString()
            }

            // Redact API keys / Bearer tokens
            sanitized = API_KEY_REGEX.replace(sanitized) { "[REDACTED_KEY]" }

            // Mask phone numbers (keep last 2 digits)
            sanitized = PHONE_REGEX.replace(sanitized) { match ->
                val digits = match.value.filter { it.isDigit() }
                if (digits.length >= 7) "••••${digits.takeLast(2)}" else match.value
            }

            return sanitized
        }
    }
}
