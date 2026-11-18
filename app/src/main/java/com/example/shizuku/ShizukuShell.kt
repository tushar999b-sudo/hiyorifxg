package com.example.shizuku

import android.os.ParcelFileDescriptor
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import moe.shizuku.server.IShizukuService
import rikka.shizuku.Shizuku

enum class ShellStatus {
    SUCCESS,
    FAILED,
    TIMEOUT,
    NOT_READY
}

data class ShellResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    val status: ShellStatus
)

object ShizukuShell {

    private const val TAG = "ShizukuShell"

    suspend fun execute(
        command: String,
        timeoutMs: Long = 15_000L,
        toolName: String = "shizuku_shell"
    ): ShellResult {
        if (!ShizukuManager.isReady()) {
            return ShellResult(
                exitCode = -1,
                stdout = "",
                stderr = "Shizuku is not ready (status=${ShizukuManager.status.value})",
                status = ShellStatus.NOT_READY
            )
        }

        // Log only the tool name, never the full command text (which may contain phone numbers or message text)
        Log.d(TAG, "Executing tool: $toolName")

        return withContext(Dispatchers.IO) {
            var destroyAction: (() -> Unit)? = null
            try {
                val binder = Shizuku.getBinder()
                    ?: return@withContext ShellResult(
                        exitCode = -1,
                        stdout = "",
                        stderr = "Shizuku binder is null",
                        status = ShellStatus.NOT_READY
                    )

                val service = IShizukuService.Stub.asInterface(binder)
                val proc = service.newProcess(arrayOf("sh", "-c", command), null, null)
                destroyAction = { runCatching { proc.destroy() } }

                val completed = withTimeoutOrNull(timeoutMs) {
                    coroutineScope {
                        val stdoutDeferred = async(Dispatchers.IO) {
                            ParcelFileDescriptor.AutoCloseInputStream(proc.inputStream)
                                .bufferedReader()
                                .use { it.readText() }
                        }
                        val stderrDeferred = async(Dispatchers.IO) {
                            ParcelFileDescriptor.AutoCloseInputStream(proc.errorStream)
                                .bufferedReader()
                                .use { it.readText() }
                        }
                        val exitCode = proc.waitFor()
                        val out = stdoutDeferred.await().trim()
                        val err = stderrDeferred.await().trim()
                        ShellResult(
                            exitCode = exitCode,
                            stdout = out,
                            stderr = err,
                            status = if (exitCode == 0) ShellStatus.SUCCESS else ShellStatus.FAILED
                        )
                    }
                }

                if (completed != null) {
                    completed
                } else {
                    destroyAction.invoke()
                    ShellResult(
                        exitCode = -1,
                        stdout = "",
                        stderr = "Command timed out after ${timeoutMs}ms",
                        status = ShellStatus.TIMEOUT
                    )
                }
            } catch (e: Exception) {
                destroyAction?.invoke()
                ShellResult(
                    exitCode = -1,
                    stdout = "",
                    stderr = e.message ?: "Shizuku process execution failed",
                    status = ShellStatus.FAILED
                )
            }
        }
    }
}
