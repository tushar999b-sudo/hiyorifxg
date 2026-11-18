package com.example.agent.tool

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.util.UUID

enum class BridgeState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    SIMULATED_READY
}

data class PcBridgeStatus(
    val state: BridgeState = BridgeState.SIMULATED_READY,
    val hostAddress: String = "192.168.1.105:8000",
    val hostOs: String = "Windows 11 Pro (64-bit)",
    val activeWindow: String = "Visual Studio Code - hiyori-agent",
    val screenResolution: String = "2560 x 1440 @ 144Hz",
    val cpuUsagePercent: Int = 18,
    val ramUsagePercent: Int = 42,
    val lastPingTimestamp: Long = System.currentTimeMillis()
)

class PcBridgeClient {
    private val _status = MutableStateFlow(PcBridgeStatus())
    val status: StateFlow<PcBridgeStatus> = _status.asStateFlow()

    suspend fun connect(host: String, port: Int = 8000): Boolean {
        _status.value = _status.value.copy(
            state = BridgeState.CONNECTING,
            hostAddress = "$host:$port"
        )
        delay(600)
        _status.value = _status.value.copy(
            state = BridgeState.CONNECTED,
            lastPingTimestamp = System.currentTimeMillis()
        )
        return true
    }

    fun disconnect() {
        _status.value = _status.value.copy(state = BridgeState.DISCONNECTED)
    }

    fun toggleSimulatedBridge() {
        if (_status.value.state == BridgeState.DISCONNECTED) {
            _status.value = _status.value.copy(state = BridgeState.SIMULATED_READY)
        } else {
            _status.value = _status.value.copy(state = BridgeState.DISCONNECTED)
        }
    }

    suspend fun executeTool(request: ToolCallRequest): ToolExecutionResult {
        delay(400) // Realistic network / execution turnaround
        val tool = request.toolName
        val params = request.parameters

        return when (tool) {
            "computer.take_screenshot" -> {
                _status.value = _status.value.copy(
                    cpuUsagePercent = (15..30).random(),
                    ramUsagePercent = (40..45).random()
                )
                ToolExecutionResult(
                    callId = request.callId,
                    toolName = tool,
                    success = true,
                    resultMessage = "Captured desktop display [${_status.value.screenResolution}] on ${_status.value.hostOs}. Active window: ${_status.value.activeWindow}.",
                    data = mapOf(
                        "window" to _status.value.activeWindow,
                        "resolution" to _status.value.screenResolution,
                        "ocrText" to "VS Code Editor - hiyori_core.py - terminal: Ready - git: main"
                    )
                )
            }
            "computer.open_application" -> {
                val appName = params["application_name"] as? String ?: "Target Application"
                _status.value = _status.value.copy(
                    activeWindow = appName,
                    cpuUsagePercent = (25..45).random()
                )
                ToolExecutionResult(
                    callId = request.callId,
                    toolName = tool,
                    success = true,
                    resultMessage = "Application '$appName' brought to foreground on ${_status.value.hostOs}.",
                    data = mapOf("activeWindow" to appName)
                )
            }
            "computer.mouse_click" -> {
                val x = params["x"] ?: 500
                val y = params["y"] ?: 300
                val button = params["button"] ?: "left"
                ToolExecutionResult(
                    callId = request.callId,
                    toolName = tool,
                    success = true,
                    resultMessage = "Mouse $button clicked at coordinates ($x, $y) successfully.",
                    data = mapOf("x" to x, "y" to y, "button" to button)
                )
            }
            "computer.type_text" -> {
                val text = params["text"] as? String ?: ""
                ToolExecutionResult(
                    callId = request.callId,
                    toolName = tool,
                    success = true,
                    resultMessage = "Typed ${text.length} characters into ${_status.value.activeWindow}.",
                    data = mapOf("typedLength" to text.length)
                )
            }
            "computer.browser_navigate" -> {
                val url = params["url"] as? String ?: "https://github.com"
                _status.value = _status.value.copy(activeWindow = "Google Chrome - $url")
                ToolExecutionResult(
                    callId = request.callId,
                    toolName = tool,
                    success = true,
                    resultMessage = "Browser navigated to $url.",
                    data = mapOf("url" to url)
                )
            }
            "terminal.execute_command" -> {
                val command = params["command"] as? String ?: "echo 'Hiyori ready'"
                _status.value = _status.value.copy(
                    cpuUsagePercent = (35..65).random()
                )
                ToolExecutionResult(
                    callId = request.callId,
                    toolName = tool,
                    success = true,
                    resultMessage = "Command '$command' executed with exit code 0.",
                    data = mapOf(
                        "command" to command,
                        "exitCode" to 0,
                        "stdout" to "Task finished successfully. [Hiyori PC Bridge Agent v1.0.4]"
                    )
                )
            }
            "system.device_status" -> {
                ToolExecutionResult(
                    callId = request.callId,
                    toolName = tool,
                    success = true,
                    resultMessage = "PC Host: ${_status.value.hostOs}, CPU: ${_status.value.cpuUsagePercent}%, RAM: ${_status.value.ramUsagePercent}%.",
                    data = mapOf(
                        "cpu" to _status.value.cpuUsagePercent,
                        "ram" to _status.value.ramUsagePercent,
                        "os" to _status.value.hostOs
                    )
                )
            }
            else -> {
                ToolExecutionResult(
                    callId = request.callId,
                    toolName = tool,
                    success = true,
                    resultMessage = "Tool '$tool' executed successfully.",
                    data = params
                )
            }
        }
    }
}
