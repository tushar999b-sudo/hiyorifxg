package com.example.shizuku

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

data class ShizukuToolResult(
    val status: ShellStatus,
    val message: String,
    val exitCode: Int,
    val stdout: String = "",
    val stderr: String = ""
)

data class ShizukuPendingConfirmation(
    val id: String = UUID.randomUUID().toString(),
    val toolName: String,
    val phone: String,
    val text: String
)

object ShizukuConfirmationGate {
    private const val TIMEOUT_MS = 60_000L

    private val _pending = MutableStateFlow<ShizukuPendingConfirmation?>(null)
    val pending: StateFlow<ShizukuPendingConfirmation?> = _pending.asStateFlow()

    @Volatile
    private var activeDeferred: CompletableDeferred<Boolean>? = null

    suspend fun requestConfirmation(toolName: String, phone: String, text: String): Boolean {
        val deferred = CompletableDeferred<Boolean>()
        activeDeferred?.cancel()
        activeDeferred = deferred
        _pending.value = ShizukuPendingConfirmation(
            toolName = toolName,
            phone = phone,
            text = text
        )

        val approved = withTimeoutOrNull(TIMEOUT_MS) {
            deferred.await()
        } ?: false

        _pending.value = null
        activeDeferred = null
        return approved
    }

    fun approve() {
        activeDeferred?.complete(true)
    }

    fun deny() {
        activeDeferred?.complete(false)
    }
}

object ShizukuToolBridge {

    suspend fun executeTool(name: String, args: Map<String, String>): ShizukuToolResult {
        val normalized = name.trim()
        if (!ShizukuManager.isReady()) {
            return ShizukuToolResult(
                status = ShellStatus.NOT_READY,
                message = "shizuku_not_ready: ${nextStepForStatus(ShizukuManager.status.value)}",
                exitCode = -1
            )
        }

        val commandResult: Result<String> = when (normalized) {
            "dialNumber", "dial_number" -> {
                val phone = args["phone"].orEmpty()
                CommandBuilder.dialNumber(phone)
            }
            "callNumber", "call_number" -> {
                val phone = args["phone"].orEmpty()
                CommandBuilder.callNumber(phone).also { res ->
                    if (res.isSuccess) {
                        val approved = ShizukuConfirmationGate.requestConfirmation(
                            toolName = "callNumber",
                            phone = phone,
                            text = "Place direct phone call to $phone"
                        )
                        if (!approved) {
                            return ShizukuToolResult(
                                status = ShellStatus.FAILED,
                                message = "Call to $phone was denied or timed out.",
                                exitCode = -1
                            )
                        }
                    }
                }
            }
            "openUrl", "open_url" -> {
                CommandBuilder.openUrl(args["url"].orEmpty())
            }
            "openApp", "open_app" -> {
                CommandBuilder.openApp(args["packageName"] ?: args["package_name"].orEmpty())
            }
            "whatsappSend", "whatsapp_send" -> {
                val phone = args["phone"].orEmpty()
                val text = args["text"].orEmpty()
                CommandBuilder.whatsappSend(phone, text).also { res ->
                    if (res.isSuccess) {
                        val approved = ShizukuConfirmationGate.requestConfirmation(
                            toolName = "whatsappSend",
                            phone = phone,
                            text = text
                        )
                        if (!approved) {
                            return ShizukuToolResult(
                                status = ShellStatus.FAILED,
                                message = "WhatsApp message to $phone was denied or timed out.",
                                exitCode = -1
                            )
                        }
                    }
                }
            }
            "toggleWifi", "toggle_wifi" -> {
                val on = parseBooleanArg(args["on"])
                    ?: return ShizukuToolResult(ShellStatus.FAILED, "Invalid boolean 'on' parameter.", -1)
                CommandBuilder.toggleWifi(on)
            }
            "toggleBluetooth", "toggle_bluetooth" -> {
                val on = parseBooleanArg(args["on"])
                    ?: return ShizukuToolResult(ShellStatus.FAILED, "Invalid boolean 'on' parameter.", -1)
                CommandBuilder.toggleBluetooth(on)
            }
            "setBrightness", "set_brightness" -> {
                val level = args["level"]?.toIntOrNull()
                    ?: return ShizukuToolResult(ShellStatus.FAILED, "Invalid integer 'level' parameter (0..255).", -1)
                CommandBuilder.setBrightness(level)
            }
            "setVolume", "set_volume" -> {
                val stream = args["stream"].orEmpty()
                val level = args["level"]?.toIntOrNull()
                    ?: return ShizukuToolResult(ShellStatus.FAILED, "Invalid integer 'level' parameter (0..15).", -1)
                CommandBuilder.setVolume(stream, level)
            }
            "screenshot" -> {
                val path = args["path"].orEmpty()
                CommandBuilder.screenshot(path)
            }
            else -> {
                return ShizukuToolResult(
                    status = ShellStatus.FAILED,
                    message = "unknown_tool: '$normalized'",
                    exitCode = -1
                )
            }
        }

        val command = commandResult.getOrElse { err ->
            return ShizukuToolResult(
                status = ShellStatus.FAILED,
                message = err.message ?: "Validation failed",
                exitCode = -1
            )
        }

        val shellRes = ShizukuShell.execute(command = command, toolName = normalized)
        if (shellRes.status != ShellStatus.SUCCESS || shellRes.exitCode != 0) {
            val debugStderr = if (shellRes.stderr.isNotBlank()) {
                "\n[stderr: ${shellRes.stderr}]"
            } else {
                ""
            }
            return ShizukuToolResult(
                status = shellRes.status,
                message = "Command failed (exit=${shellRes.exitCode}, status=${shellRes.status})$debugStderr",
                exitCode = shellRes.exitCode,
                stdout = shellRes.stdout,
                stderr = shellRes.stderr
            )
        }

        val successMessage = when (normalized) {
            "dialNumber", "dial_number" -> "dialer_opened"
            "callNumber", "call_number", "openUrl", "open_url", "openApp", "open_app", "whatsappSend", "whatsapp_send" -> "launched"
            "toggleWifi", "toggle_wifi" -> "Wi-Fi ${if (parseBooleanArg(args["on"]) == true) "enabled" else "disabled"}"
            "toggleBluetooth", "toggle_bluetooth" -> "Bluetooth ${if (parseBooleanArg(args["on"]) == true) "enabled" else "disabled"}"
            "setBrightness", "set_brightness" -> "Brightness set to ${args["level"]}"
            "setVolume", "set_volume" -> "Volume set for ${args["stream"]} to ${args["level"]}"
            "screenshot" -> "Screenshot saved to ${args["path"]}"
            else -> "executed"
        }

        return ShizukuToolResult(
            status = ShellStatus.SUCCESS,
            message = successMessage,
            exitCode = shellRes.exitCode,
            stdout = shellRes.stdout,
            stderr = shellRes.stderr
        )
    }

    private fun parseBooleanArg(raw: String?): Boolean? {
        return when (raw?.trim()?.lowercase()) {
            "true", "1", "on", "yes" -> true
            "false", "0", "off", "no" -> false
            else -> null
        }
    }

    private fun nextStepForStatus(status: ShizukuStatus): String = when (status) {
        ShizukuStatus.NOT_INSTALLED -> "Install Shizuku app first."
        ShizukuStatus.NOT_RUNNING -> "Open Shizuku app and start the Shizuku service."
        ShizukuStatus.RUNNING_NO_PERMISSION -> "Tap 'Grant permission' on the Overview screen to allow Hiyori."
        ShizukuStatus.PERMISSION_DENIED_PERMANENTLY -> "Open Shizuku app and enable permission for Hiyori."
        ShizukuStatus.VERSION_TOO_OLD -> "Update Shizuku app to the latest version."
        ShizukuStatus.READY -> "Ready"
    }
}
