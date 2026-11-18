package com.example.agent.llm

import com.example.agent.tool.ToolSpec
import com.example.avatar.HiyoriExpression
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID

data class ParsedLlmOutput(
    val visibleText: String,
    val toolCalls: List<LlmToolCall>,
    val mood: HiyoriExpression
)

object LlmResponseParser {

    private val MOOD_LINE_REGEX = Regex("(?im)^\\s*mood\\s*:\\s*([A-Za-z_]+)\\s*$")
    private val SET_YOUR_MOOD_REGEX = Regex("(?im)^\\s*set\\s+your\\s+mood\\s*:.*$")
    private val FENCED_JSON_REGEX = Regex("```(?:json)?\\s*([\\s\\S]*?)```", RegexOption.IGNORE_CASE)

    fun parse(rawText: String, nativeToolCalls: List<LlmToolCall>): ParsedLlmOutput {
        var workingText = rawText
        val resolvedToolCalls = nativeToolCalls.toMutableList()

        // Text fallback: if no native tool calls, check if reply contains a JSON object or fenced JSON block
        // whose "name" matches a registered tool, and remove it from visible text.
        if (resolvedToolCalls.isEmpty() && workingText.isNotBlank()) {
            val extracted = extractTextFallbackToolCalls(workingText)
            if (extracted.first.isNotEmpty()) {
                resolvedToolCalls.addAll(extracted.first)
                workingText = extracted.second
            }
        }

        // Parse trailing "mood: X" line and remove it from visible text
        var detectedMood = HiyoriExpression.NEUTRAL
        val moodMatches = MOOD_LINE_REGEX.findAll(workingText).toList()
        if (moodMatches.isNotEmpty()) {
            val lastMatch = moodMatches.last()
            val rawMood = lastMatch.groupValues.getOrNull(1)?.trim().orEmpty()
            detectedMood = HiyoriExpression.fromString(rawMood)
            workingText = workingText.replace(MOOD_LINE_REGEX, "")
        }

        // Remove any "Set your mood:" text if present
        workingText = workingText.replace(SET_YOUR_MOOD_REGEX, "")

        val cleanedText = workingText
            .lines()
            .dropWhile { it.isBlank() }
            .dropLastWhile { it.isBlank() }
            .joinToString("\n")
            .trim()

        return ParsedLlmOutput(
            visibleText = cleanedText,
            toolCalls = resolvedToolCalls,
            mood = detectedMood
        )
    }

    private fun extractTextFallbackToolCalls(text: String): Pair<List<LlmToolCall>, String> {
        val calls = mutableListOf<LlmToolCall>()
        var remaining = text

        // 1. Check fenced JSON blocks first
        val fencedMatches = FENCED_JSON_REGEX.findAll(remaining).toList()
        for (match in fencedMatches) {
            val inner = match.groupValues[1].trim()
            val parsedCall = tryParseSingleToolJson(inner)
            if (parsedCall != null) {
                calls.add(parsedCall)
                remaining = remaining.replace(match.value, "")
            }
        }

        if (calls.isNotEmpty()) {
            return calls to remaining
        }

        // 2. Check if the whole text (or an inline JSON object) is a tool call JSON
        val trimmed = remaining.trim()
        val directCall = tryParseSingleToolJson(trimmed)
        if (directCall != null) {
            calls.add(directCall)
            return calls to ""
        }

        // 3. Check for an embedded JSON object {...} in the text
        val firstBrace = trimmed.indexOf('{')
        val lastBrace = trimmed.lastIndexOf('}')
        if (firstBrace >= 0 && lastBrace > firstBrace) {
            val candidate = trimmed.substring(firstBrace, lastBrace + 1)
            val embeddedCall = tryParseSingleToolJson(candidate)
            if (embeddedCall != null) {
                calls.add(embeddedCall)
                val withoutJson = (trimmed.substring(0, firstBrace) + trimmed.substring(lastBrace + 1)).trim()
                return calls to withoutJson
            }
        }

        return emptyList<LlmToolCall>() to text
    }

    private fun tryParseSingleToolJson(candidate: String): LlmToolCall? {
        if (!candidate.startsWith("{") || !candidate.endsWith("}")) return null
        val obj: JsonObject = runCatching { Json.parseToJsonElement(candidate).jsonObject }.getOrNull() ?: return null
        val name = obj["name"]?.jsonPrimitive?.contentOrNull?.trim() ?: return null
        val spec = ToolSpec.findByName(name) ?: return null

        val argsElement = obj["arguments"] ?: obj["parameters"] ?: obj["args"]
        val argsJson = when {
            argsElement == null -> "{}"
            argsElement is JsonObject -> argsElement.toString()
            argsElement.jsonPrimitive.isString -> argsElement.jsonPrimitive.content.ifBlank { "{}" }
            else -> "{}"
        }

        return LlmToolCall(
            id = "call_${UUID.randomUUID().toString().take(8)}",
            name = spec.name,
            argumentsJson = argsJson
        )
    }
}
