package com.example.agent.memory

import com.example.data.dao.MemoryDao
import com.example.data.model.MemoryCategory
import com.example.data.model.MemoryEntity

class MemoryManager(
    private val memoryDao: MemoryDao
) {
    suspend fun saveWithDeduplication(
        content: String,
        category: MemoryCategory,
        importance: Int = 3
    ): MemoryEntity {
        val cleaned = content.trim()
        val normalizedNew = normalizeForComparison(cleaned)
        val existing = memoryDao.getAllMemories()

        val duplicate = existing.find { item ->
            val normExisting = normalizeForComparison(item.content)
            normExisting == normalizedNew ||
                (normExisting.length > 12 && normalizedNew.length > 12 &&
                    (normExisting.contains(normalizedNew) || normalizedNew.contains(normExisting)))
        }

        if (duplicate != null) {
            val updated = duplicate.copy(
                importance = maxOf(duplicate.importance, importance.coerceIn(1, 5)),
                timestamp = System.currentTimeMillis()
            )
            memoryDao.updateMemory(updated)
            return updated
        }

        val entity = MemoryEntity(
            category = category.name,
            content = cleaned,
            importance = importance.coerceIn(1, 5)
        )
        val id = memoryDao.insertMemory(entity)
        return entity.copy(id = id)
    }

    suspend fun search(query: String): List<MemoryEntity> {
        if (query.isBlank()) return memoryDao.getAllMemories().take(10)
        return memoryDao.searchMemories(query.trim())
    }

    suspend fun forget(memoryId: Long?, query: String?): Int {
        if (memoryId != null && memoryId > 0) {
            val existing = memoryDao.getAllMemories().find { it.id == memoryId } ?: return 0
            memoryDao.deleteMemory(existing)
            return 1
        }
        if (!query.isNullOrBlank()) {
            val matches = memoryDao.searchMemories(query.trim())
            matches.forEach { memoryDao.deleteMemory(it) }
            return matches.size
        }
        return 0
    }

    suspend fun autoExtractFromUserMessage(message: String) {
        val lower = message.lowercase().trim()
        if (lower.length !in 10..220) return
        val category = when {
            lower.contains("prefer") || lower.contains("favorite") || lower.contains("i like") || lower.contains("mujhe pasand") ->
                MemoryCategory.PREFERENCE
            lower.contains("project") || lower.contains("app bana") ->
                MemoryCategory.PROJECT
            lower.contains("remember") || lower.contains("yaad rakhna") || lower.contains("my name") || lower.contains("mera naam") ->
                MemoryCategory.FACT
            else -> null
        }
        if (category != null) {
            saveWithDeduplication(content = message, category = category, importance = 4)
        }
    }

    private fun normalizeForComparison(text: String): String {
        return text.lowercase()
            .replace(Regex("""[^\p{L}\p{N}\s]"""), "")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }
}
