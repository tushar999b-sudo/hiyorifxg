package com.example.agent.tool

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

sealed class ToolValidationResult {
    data class Valid(
        val spec: ToolSpec,
        val normalizedParameters: Map<String, Any>
    ) : ToolValidationResult()

    data class Invalid(
        val errorMessage: String
    ) : ToolValidationResult()
}

object ToolValidator {

    fun validate(rawToolName: String, rawArgumentsJson: String): ToolValidationResult {
        val spec = ToolSpec.findByName(rawToolName)
            ?: return ToolValidationResult.Invalid(
                "Validation error: Unknown tool '$rawToolName'. Use only a registered tool name."
            )

        val trimmedJson = rawArgumentsJson.trim().ifEmpty { "{}" }
        val parsedElement: JsonElement = try {
            Json.parseToJsonElement(trimmedJson)
        } catch (e: Exception) {
            return ToolValidationResult.Invalid(
                "Validation error for '${spec.name}': Arguments must be a valid JSON object."
            )
        }

        val jsonObj = parsedElement as? JsonObject
            ?: return ToolValidationResult.Invalid(
                "Validation error for '${spec.name}': Arguments must be a JSON object."
            )

        val allowedKeys = spec.parameters.map { it.name }.toSet()
        val unknownKeys = jsonObj.keys.filter { it !in allowedKeys }
        if (unknownKeys.isNotEmpty()) {
            return ToolValidationResult.Invalid(
                "Validation error for '${spec.name}': Unknown parameter(s): ${unknownKeys.joinToString(", ")}. Allowed parameters: ${if (allowedKeys.isEmpty()) "none" else allowedKeys.joinToString(", ")}."
            )
        }

        val normalized = linkedMapOf<String, Any>()

        for (param in spec.parameters) {
            val rawVal = jsonObj[param.name]
            if (rawVal == null || rawVal is JsonNull) {
                if (param.required) {
                    return ToolValidationResult.Invalid(
                        "Validation error for '${spec.name}': Missing required parameter '${param.name}' (${param.description})."
                    )
                }
                continue
            }

            val primitive = rawVal as? JsonPrimitive
                ?: return ToolValidationResult.Invalid(
                    "Validation error for '${spec.name}': Parameter '${param.name}' must be a ${param.type.name.lowercase()}."
                )

            when (param.type) {
                ParamType.STRING -> {
                    if (!primitive.isString) {
                        return ToolValidationResult.Invalid(
                            "Validation error for '${spec.name}': Parameter '${param.name}' must be a string."
                        )
                    }
                    val strVal = primitive.content
                    if (param.required && strVal.isBlank()) {
                        return ToolValidationResult.Invalid(
                            "Validation error for '${spec.name}': Required parameter '${param.name}' cannot be blank."
                        )
                    }
                    if (!param.enumValues.isNullOrEmpty()) {
                        val matchedEnum = param.enumValues.find { it.equals(strVal.trim(), ignoreCase = true) }
                            ?: return ToolValidationResult.Invalid(
                                "Validation error for '${spec.name}': Parameter '${param.name}' must be one of [${param.enumValues.joinToString(", ")}], got '$strVal'."
                            )
                        normalized[param.name] = matchedEnum
                    } else {
                        normalized[param.name] = strVal
                    }
                }

                ParamType.INTEGER -> {
                    if (primitive.isString) {
                        return ToolValidationResult.Invalid(
                            "Validation error for '${spec.name}': Parameter '${param.name}' must be an integer, not a string."
                        )
                    }
                    val longVal = primitive.longOrNull
                        ?: return ToolValidationResult.Invalid(
                            "Validation error for '${spec.name}': Parameter '${param.name}' must be an integer."
                        )
                    normalized[param.name] = if (longVal in Int.MIN_VALUE..Int.MAX_VALUE) longVal.toInt() else longVal
                }

                ParamType.NUMBER -> {
                    if (primitive.isString) {
                        return ToolValidationResult.Invalid(
                            "Validation error for '${spec.name}': Parameter '${param.name}' must be a number, not a string."
                        )
                    }
                    val doubleVal = primitive.doubleOrNull
                        ?: return ToolValidationResult.Invalid(
                            "Validation error for '${spec.name}': Parameter '${param.name}' must be a number."
                        )
                    normalized[param.name] = doubleVal
                }

                ParamType.BOOLEAN -> {
                    if (primitive.isString) {
                        return ToolValidationResult.Invalid(
                            "Validation error for '${spec.name}': Parameter '${param.name}' must be a boolean (true or false), not a string."
                        )
                    }
                    val boolVal = primitive.booleanOrNull
                        ?: return ToolValidationResult.Invalid(
                            "Validation error for '${spec.name}': Parameter '${param.name}' must be a boolean."
                        )
                    normalized[param.name] = boolVal
                }
            }
        }

        return ToolValidationResult.Valid(spec, normalized)
    }
}
