package com.example.agent.loop

import com.example.agent.audit.AuditLogger
import com.example.agent.llm.LlmMessage
import com.example.agent.llm.LlmProviderException
import com.example.agent.llm.ProviderRouter
import com.example.agent.memory.MemoryManager
import com.example.agent.safety.SafetyGate
import com.example.agent.tool.PermissionLevel
import com.example.agent.tool.ToolCallRequest
import com.example.agent.tool.ToolRegistry
import com.example.agent.tool.ToolResultStatus
import com.example.agent.tool.ToolSpec
import com.example.agent.tool.ToolValidationResult
import com.example.agent.tool.ToolValidator
import com.example.avatar.HiyoriExpression
import com.example.data.model.ActionStatus
import com.example.data.settings.SettingsStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

data class StepLogEntry(
    val id: String = UUID.randomUUID().toString(),
    val stepNumber: Int,
    val toolName: String,
    val argumentsRedacted: String,
    val resultSummary: String,
    val status: ActionStatus,
    val durationMs: Long,
    val timestamp: Long = System.currentTimeMillis()
)

data class AgentLoopState(
    val running: Boolean = false,
    val currentStep: Int = 0,
    val maxSteps: Int = 3,
    val plan: String = "Idle",
    val lastAction: String = "",
    val lastResult: String = "",
    val lastProviderLabel: String = "",
    val lastToolName: String? = null,
    val lastToolStatusLine: String? = null,
    val mood: HiyoriExpression = HiyoriExpression.NEUTRAL,
    val stepLogs: List<StepLogEntry> = emptyList()
)

class AgentLoop(
    private val providerRouter: ProviderRouter,
    private val toolRegistry: ToolRegistry,
    private val safetyGate: SafetyGate,
    private val stopController: StopController,
    private val auditLogger: AuditLogger,
    private val memoryManager: MemoryManager,
    private val settingsStore: SettingsStore
) {
    private val _state = MutableStateFlow(AgentLoopState())
    val state: StateFlow<AgentLoopState> = _state.asStateFlow()

    fun runGoal(
        scope: CoroutineScope,
        systemPrompt: String,
        userGoal: String,
        shortHistory: List<Pair<String, String>>,
        onTokenDelta: (String) -> Unit = {},
        onComplete: suspend (finalText: String, finalMood: HiyoriExpression) -> Unit
    ) {
        stopController.requestStop()
        stopController.resetForNewRun()

        val maxLoops = settingsStore.state.value.maxAgentSteps.coerceIn(1, MAX_TOOL_LOOPS)
        _state.value = AgentLoopState(
            running = true,
            currentStep = 0,
            maxSteps = maxLoops,
            plan = "Observing context & memories...",
            mood = HiyoriExpression.THINKING,
            stepLogs = emptyList()
        )

        val job = scope.launch {
            try {
                memoryManager.autoExtractFromUserMessage(userGoal)
                val relevantMemories = memoryManager.search(userGoal).take(6)
                val memoryContext = if (relevantMemories.isNotEmpty()) {
                    "\nRelevant Memories:\n" + relevantMemories.joinToString("\n") { "- [${it.category}] ${it.content}" }
                } else {
                    ""
                }

                val conversation = mutableListOf<LlmMessage>()
                conversation.add(LlmMessage(role = "system", content = systemPrompt + memoryContext))
                shortHistory.takeLast(6).forEach { (sender, msg) ->
                    val role = if (sender.equals("USER", ignoreCase = true)) "user" else "assistant"
                    conversation.add(LlmMessage(role = role, content = msg))
                }
                conversation.add(LlmMessage(role = "user", content = userGoal))

                val requestTools = ToolSpec.selectToolsForRequest(userGoal, maxTools = ToolSpec.MAX_TOOLS_PER_REQUEST)
                var lastToolOutcome = ""
                var validationRetriesUsed = 0

                for (step in 1..maxLoops) {
                    stopController.throwIfStopped()
                    _state.value = _state.value.copy(
                        currentStep = step,
                        plan = "Step $step/$maxLoops: Thinking & planning...",
                        mood = HiyoriExpression.THINKING
                    )

                    val routed = providerRouter.chat(
                        messages = conversation,
                        tools = requestTools,
                        onTokenDelta = { token ->
                            if (!stopController.isStopped.value) {
                                onTokenDelta(token)
                            }
                        }
                    )
                    stopController.throwIfStopped()

                    val llmResp = routed.response
                    _state.value = _state.value.copy(
                        mood = llmResp.mood,
                        lastProviderLabel = routed.providerLabel
                    )

                    // No tool calls -> final answer
                    if (llmResp.toolCalls.isEmpty()) {
                        val finalAnswer = llmResp.text.ifBlank {
                            if (lastToolOutcome.isNotBlank()) lastToolOutcome else "Haanji Master, ho gaya!"
                        }
                        _state.value = _state.value.copy(
                            running = false,
                            plan = "Goal achieved in $step step(s).",
                            mood = llmResp.mood,
                            lastProviderLabel = routed.providerLabel
                        )
                        onComplete(finalAnswer, llmResp.mood)
                        return@launch
                    }

                    // Record assistant tool-call turn
                    conversation.add(
                        LlmMessage(
                            role = "assistant",
                            content = llmResp.text,
                            toolCalls = llmResp.toolCalls
                        )
                    )

                    var abortDueToRepeatedValidationError = false

                    for (tc in llmResp.toolCalls) {
                        stopController.throwIfStopped()
                        val startTime = System.currentTimeMillis()
                        val redactedArgs = AuditLogger.redactSensitiveData(tc.argumentsJson)

                        _state.value = _state.value.copy(
                            plan = "Validating ${tc.name}...",
                            lastAction = "${tc.name}($redactedArgs)"
                        )

                        // Validate tool existence, required fields, types, enums, and reject unknown keys
                        val validation = ToolValidator.validate(tc.name, tc.argumentsJson)
                        if (validation is ToolValidationResult.Invalid) {
                            val dur = System.currentTimeMillis() - startTime
                            val errMsg = validation.errorMessage
                            recordStepAndAudit(step, tc.name, redactedArgs, 0, ActionStatus.FAILED, errMsg, dur)
                            lastToolOutcome = errMsg
                            _state.value = _state.value.copy(
                                lastResult = errMsg,
                                lastToolName = tc.name,
                                lastToolStatusLine = "failed (invalid arguments)"
                            )
                            conversation.add(
                                LlmMessage(
                                    role = "tool",
                                    content = errMsg,
                                    toolCallId = tc.id,
                                    toolName = tc.name
                                )
                            )
                            if (validationRetriesUsed >= 1) {
                                abortDueToRepeatedValidationError = true
                                break
                            }
                            validationRetriesUsed++
                            continue
                        }

                        val validCall = validation as ToolValidationResult.Valid
                        val spec = validCall.spec
                        val paramsMap = validCall.normalizedParameters

                        // SafetyGate check
                        val approved = safetyGate.requestApproval(
                            callId = tc.id,
                            tool = spec,
                            argumentsJson = tc.argumentsJson,
                            redactedArgs = redactedArgs
                        )
                        stopController.throwIfStopped()

                        if (!approved) {
                            val dur = System.currentTimeMillis() - startTime
                            val deniedMsg = "Action '${spec.name}' was rejected or timed out by Master."
                            recordStepAndAudit(
                                step, spec.name, redactedArgs, spec.permissionLevel,
                                ActionStatus.REJECTED, deniedMsg, dur
                            )
                            conversation.add(
                                LlmMessage(
                                    role = "tool",
                                    content = deniedMsg,
                                    toolCallId = tc.id,
                                    toolName = spec.name
                                )
                            )
                            lastToolOutcome = deniedMsg
                            _state.value = _state.value.copy(
                                lastResult = deniedMsg,
                                lastToolName = spec.name,
                                lastToolStatusLine = "failed (rejected by user)"
                            )
                            continue
                        }

                        // Execute tool
                        _state.value = _state.value.copy(plan = "Executing ${spec.name}...")
                        stopController.enqueueAction(spec.name)
                        val execResult = toolRegistry.execute(
                            ToolCallRequest(
                                callId = tc.id,
                                toolName = spec.name,
                                parameters = paramsMap,
                                permissionLevel = PermissionLevel.fromInt(spec.permissionLevel)
                            )
                        )
                        stopController.dequeueAction(spec.name)

                        val dur = System.currentTimeMillis() - startTime
                        val actionStatus = when {
                            execResult.success -> ActionStatus.SUCCESS
                            execResult.status == ToolResultStatus.NOT_FOUND -> ActionStatus.FAILED
                            execResult.error == "NOT_IMPLEMENTED" -> ActionStatus.NOT_IMPLEMENTED
                            else -> ActionStatus.FAILED
                        }

                        recordStepAndAudit(
                            step, spec.name, redactedArgs, spec.permissionLevel,
                            actionStatus, execResult.resultMessage, dur
                        )

                        lastToolOutcome = execResult.resultMessage
                        _state.value = _state.value.copy(
                            lastResult = execResult.resultMessage,
                            lastToolName = spec.name,
                            lastToolStatusLine = execResult.toDisplayStatusLine(),
                            plan = "Verifying ${spec.name} result..."
                        )

                        conversation.add(
                            LlmMessage(
                                role = "tool",
                                content = execResult.resultMessage,
                                toolCallId = tc.id,
                                toolName = spec.name
                            )
                        )
                    }

                    if (abortDueToRepeatedValidationError) {
                        val errReply = "Master, tool parameters valid nahi the: $lastToolOutcome"
                        _state.value = _state.value.copy(
                            running = false,
                            plan = "Stopped: Tool validation failed after retry.",
                            mood = HiyoriExpression.SAD
                        )
                        onComplete(errReply, HiyoriExpression.SAD)
                        return@launch
                    }
                }

                // After maxLoops (3), ask for one short final reply without tools
                stopController.throwIfStopped()
                val finalRouted = runCatching {
                    providerRouter.chat(
                        messages = conversation,
                        tools = emptyList(),
                        onTokenDelta = { token ->
                            if (!stopController.isStopped.value) {
                                onTokenDelta(token)
                            }
                        }
                    )
                }.getOrNull()

                val finalReply = finalRouted?.response?.text?.takeIf { it.isNotBlank() }
                    ?: lastToolOutcome.ifBlank { "Maine $maxLoops steps tak try kiya Master." }
                val finalMood = finalRouted?.response?.mood ?: HiyoriExpression.NEUTRAL
                val finalProvider = finalRouted?.providerLabel ?: _state.value.lastProviderLabel

                _state.value = _state.value.copy(
                    running = false,
                    plan = "Completed in $maxLoops step(s).",
                    mood = finalMood,
                    lastProviderLabel = finalProvider
                )
                onComplete(finalReply, finalMood)
            } catch (e: CancellationException) {
                _state.value = _state.value.copy(
                    running = false,
                    plan = "Stopped immediately by Master.",
                    mood = HiyoriExpression.SHY
                )
                onComplete("Theek hai Master, maine turant ruk gaya!", HiyoriExpression.SHY)
            } catch (e: LlmProviderException) {
                val errText = e.message ?: "Request failed. Please check your settings."
                _state.value = _state.value.copy(
                    running = false,
                    plan = errText,
                    lastResult = errText,
                    lastProviderLabel = "",
                    mood = HiyoriExpression.SAD
                )
                onComplete(errText, HiyoriExpression.SAD)
            } catch (_: Exception) {
                val errText = "Request failed. Please check your connection and settings."
                _state.value = _state.value.copy(
                    running = false,
                    plan = errText,
                    lastResult = errText,
                    lastProviderLabel = "",
                    mood = HiyoriExpression.SAD
                )
                onComplete(errText, HiyoriExpression.SAD)
            }
        }

        stopController.attachJob(job)
    }

    private suspend fun recordStepAndAudit(
        stepNumber: Int,
        toolName: String,
        redactedArgs: String,
        permissionLevel: Int,
        status: ActionStatus,
        resultSummary: String,
        durationMs: Long
    ) {
        val entry = StepLogEntry(
            stepNumber = stepNumber,
            toolName = toolName,
            argumentsRedacted = redactedArgs,
            resultSummary = AuditLogger.redactSensitiveData(resultSummary),
            status = status,
            durationMs = durationMs
        )
        _state.value = _state.value.copy(stepLogs = _state.value.stepLogs + entry)
        auditLogger.logAction(
            stepNumber = stepNumber,
            toolName = toolName,
            target = toolName,
            rawArgumentsJson = redactedArgs,
            permissionLevel = permissionLevel,
            status = status,
            resultSummary = resultSummary,
            durationMs = durationMs
        )
    }

    companion object {
        const val MAX_TOOL_LOOPS = 3
    }
}
