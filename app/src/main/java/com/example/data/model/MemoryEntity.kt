package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class MemoryCategory {
    PREFERENCE,
    PROJECT,
    FACT,
    SYSTEM_SETTING
}

@Entity(tableName = "memories")
data class MemoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val category: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val importance: Int = 3,
    val tags: List<String> = emptyList()
)
