package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class MessageSender {
    USER,
    HIYORI,
    SYSTEM
}

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val sender: String,
    val content: String,
    val expression: String? = null,
    val toolActionName: String? = null,
    val toolStatus: String? = null,
    val providerLabel: String? = null
)
