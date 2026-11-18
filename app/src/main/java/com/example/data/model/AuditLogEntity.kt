package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class ActionStatus {
    PENDING_APPROVAL,
    APPROVED,
    REJECTED,
    SUCCESS,
    FAILED,
    NOT_IMPLEMENTED
}

@Entity(tableName = "audit_logs")
data class AuditLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val stepNumber: Int = 0,
    val actionName: String,
    val target: String,
    val parametersJson: String,
    val permissionLevel: Int, // 0 to 4
    val status: String,
    val resultDetails: String? = null,
    val durationMs: Long = 0L
)
