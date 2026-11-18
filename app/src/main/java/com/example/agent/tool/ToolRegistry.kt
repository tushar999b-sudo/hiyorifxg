package com.example.agent.tool

import android.Manifest
import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.NotificationManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.location.Location
import android.location.LocationManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.CancellationSignal
import android.os.Environment
import android.os.StatFs
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.provider.Settings
import android.provider.Telephony
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import com.example.agent.loop.StopController
import com.example.agent.memory.MemoryManager
import com.example.data.model.MemoryCategory
import com.example.permissions.HiyoriPermissionManager
import com.example.permissions.RuntimePermissionCoordinator
import com.example.service.HiyoriAccessibilityService
import com.example.service.HiyoriNotificationService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class ToolRegistry(
    private val memoryManager: MemoryManager,
    private val stopController: StopController,
    private val context: Context? = null
) {

    fun findSpec(name: String): ToolSpec? = ToolSpec.findByName(name)

    private fun successResult(
        request: ToolCallRequest,
        message: String,
        data: Map<String, Any> = emptyMap()
    ) = ToolExecutionResult(
        callId = request.callId,
        toolName = request.toolName,
        success = true,
        resultMessage = message,
        status = ToolResultStatus.SUCCESS,
        data = data
    )

    private fun needsSetupResult(
        request: ToolCallRequest,
        settingName: String,
        message: String
    ) = ToolExecutionResult(
        callId = request.callId,
        toolName = request.toolName,
        success = false,
        resultMessage = "NEEDS_SETUP ($settingName): $message",
        status = ToolResultStatus.NEEDS_SETUP,
        requiredSetting = settingName,
        error = "NEEDS_SETUP"
    )

    private fun permissionDeniedResult(
        request: ToolCallRequest,
        message: String
    ) = ToolExecutionResult(
        callId = request.callId,
        toolName = request.toolName,
        success = false,
        resultMessage = "PERMISSION_DENIED: $message",
        status = ToolResultStatus.PERMISSION_DENIED,
        error = "PERMISSION_DENIED"
    )

    private fun failedResult(
        request: ToolCallRequest,
        message: String
    ) = ToolExecutionResult(
        callId = request.callId,
        toolName = request.toolName,
        success = false,
        resultMessage = "FAILED: $message",
        status = ToolResultStatus.FAILED,
        error = "FAILED"
    )

    private fun notFoundResult(
        request: ToolCallRequest,
        message: String
    ) = ToolExecutionResult(
        callId = request.callId,
        toolName = request.toolName,
        success = false,
        resultMessage = "NOT_FOUND: $message",
        status = ToolResultStatus.NOT_FOUND,
        error = "NOT_FOUND"
    )

    suspend fun execute(request: ToolCallRequest): ToolExecutionResult {
        stopController.throwIfStopped()
        val canonical = ToolSpec.findByName(request.toolName)?.name ?: request.toolName

        return when (canonical) {
            "clear_cache" -> {
                val ctx = context ?: return failedResult(request, "App context unavailable.")
                var freedBytes = 0L
                fun deleteDirContents(dir: File?): Long {
                    if (dir == null || !dir.exists()) return 0L
                    var bytes = 0L
                    dir.listFiles()?.forEach { child ->
                        bytes += if (child.isDirectory) {
                            val sub = deleteDirContents(child)
                            child.delete()
                            sub
                        } else {
                            val len = child.length()
                            if (child.delete()) len else 0L
                        }
                    }
                    return bytes
                }
                freedBytes += deleteDirContents(ctx.cacheDir)
                freedBytes += deleteDirContents(ctx.codeCacheDir)
                System.gc()
                val freedKb = (freedBytes / 1024L).coerceAtLeast(1L)
                val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                val memInfo = ActivityManager.MemoryInfo()
                am?.getMemoryInfo(memInfo)
                val availMb = memInfo.availMem / (1024L * 1024L)
                successResult(
                    request,
                    "Cache cleared (${freedKb} KB freed). Available RAM: ${availMb} MB."
                )
            }

            "memory_search" -> {
                val query = request.parameters["query"]?.toString().orEmpty()
                val matches = memoryManager.search(query)
                if (matches.isEmpty()) {
                    successResult(request, "No memories found matching '$query'.")
                } else {
                    val summary = matches.take(8).joinToString("\n") { "- [${it.category}] ${it.content}" }
                    successResult(request, "Found ${matches.size} memory item(s):\n$summary", mapOf("count" to matches.size))
                }
            }

            "memory_save" -> {
                val content = request.parameters["content"]?.toString().orEmpty()
                if (content.isBlank()) {
                    failedResult(request, "Memory content cannot be empty.")
                } else {
                    val catStr = request.parameters["category"]?.toString()?.uppercase() ?: "FACT"
                    val category = runCatching { MemoryCategory.valueOf(catStr) }.getOrDefault(MemoryCategory.FACT)
                    val importance = (request.parameters["importance"] as? Number)?.toInt() ?: 4
                    val savedEntity = memoryManager.saveWithDeduplication(content, category, importance)
                    successResult(
                        request,
                        "Saved to ${savedEntity.category} memory (id=${savedEntity.id}): \"${savedEntity.content}\""
                    )
                }
            }

            "memory_forget" -> {
                val memoryId = (request.parameters["id"] as? Number)?.toLong()
                val query = request.parameters["query"]?.toString()
                    ?: request.parameters["text"]?.toString()
                    ?: request.parameters["content"]?.toString()
                if ((memoryId == null || memoryId <= 0) && query.isNullOrBlank()) {
                    failedResult(request, "Please specify which memory to forget.")
                } else {
                    val removed = memoryManager.forget(memoryId, query)
                    if (removed > 0) {
                        successResult(request, "Forgot $removed matching memory item(s).")
                    } else {
                        failedResult(request, "No matching memory was found.")
                    }
                }
            }

            else -> {
                if (canonical.startsWith("shizuku_")) {
                    val bridgeName = canonical.removePrefix("shizuku_")
                    val stringArgs = request.parameters.mapValues { it.value.toString() }
                    val res = com.example.shizuku.ShizukuToolBridge.executeTool(bridgeName, stringArgs)
                    when (res.status) {
                        com.example.shizuku.ShellStatus.SUCCESS -> successResult(
                            request,
                            res.message,
                            mapOf("exit_code" to res.exitCode, "stdout" to res.stdout)
                        )
                        com.example.shizuku.ShellStatus.NOT_READY -> needsSetupResult(
                            request,
                            "Shizuku ADB Shell (Overview screen)",
                            res.message
                        )
                        com.example.shizuku.ShellStatus.FAILED,
                        com.example.shizuku.ShellStatus.TIMEOUT -> failedResult(request, res.message)
                    }
                } else {
                    executeDeviceTool(canonical, request)
                }
            }
        }
    }

    private suspend fun executeDeviceTool(canonical: String, request: ToolCallRequest): ToolExecutionResult {
        val ctx = context ?: return failedResult(request, "Android Context unavailable.")

        return when (canonical) {
            "open_app" -> handleOpenApp(ctx, request)
            "open_url" -> handleOpenUrl(ctx, request)
            "web_search" -> handleWebSearch(ctx, request)
            "youtube_search" -> handleYoutubeSearch(ctx, request)
            "make_call" -> handleMakeCall(ctx, request)
            "send_sms" -> handleSendSms(ctx, request)
            "send_whatsapp" -> handleSendWhatsApp(ctx, request)
            "read_sms" -> handleReadSms(ctx, request)
            "read_notifications" -> handleReadNotifications(ctx, request)
            "reply_notification" -> handleReplyNotification(ctx, request)
            "screen_read" -> handleScreenRead(ctx, request)
            "tap_text" -> handleTapText(ctx, request)
            "type_text" -> handleTypeText(ctx, request)
            "swipe" -> handleSwipe(ctx, request)
            "global_back" -> handleGlobalAction(ctx, request, "back")
            "global_home" -> handleGlobalAction(ctx, request, "home")
            "global_recents" -> handleGlobalAction(ctx, request, "recents")
            "torch" -> handleTorch(ctx, request)
            "volume" -> handleVolume(ctx, request)
            "brightness" -> handleBrightness(ctx, request)
            "do_not_disturb" -> handleDnd(ctx, request)
            "wifi_panel" -> handleWifiPanel(ctx, request)
            "bluetooth_panel" -> handleBluetoothPanel(ctx, request)
            "device_info" -> handleDeviceInfo(ctx, request)
            "file_create" -> handleFileCreate(request)
            "file_read" -> handleFileRead(request)
            "file_write" -> handleFileWrite(request)
            "file_list" -> handleFileList(request)
            "file_move" -> handleFileMove(request)
            "file_rename" -> handleFileRename(request)
            "file_search" -> handleFileSearch(request)
            "file_delete" -> handleFileDelete(request)
            "calendar_events" -> handleCalendarEvents(ctx, request)
            "location" -> handleLocation(ctx, request)
            else -> failedResult(request, "Unknown tool '${request.toolName}'.")
        }
    }

    private fun handleOpenApp(ctx: Context, request: ToolCallRequest): ToolExecutionResult {
        val appName = request.parameters["app_name"]?.toString()?.trim().orEmpty()
        if (appName.isBlank()) return failedResult(request, "Missing 'app_name' parameter.")

        val pm = ctx.packageManager
        val q = appName.lowercase()

        if (q == "settings" || q == "android settings") {
            return runCatching {
                ctx.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                successResult(request, "Opened Android Settings.")
            }.getOrElse { failedResult(request, it.message ?: "Could not open Settings.") }
        }

        // 1. Direct package match
        pm.getLaunchIntentForPackage(appName)?.let { intent ->
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(intent)
            return successResult(request, "Opened $appName.")
        }

        // 2. Query launchable apps by label
        val mainIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = pm.queryIntentActivities(mainIntent, 0)
        val exactMatch = resolved.firstOrNull {
            it.loadLabel(pm)?.toString()?.trim()?.equals(appName, ignoreCase = true) == true
        }
        val partialMatch = exactMatch ?: resolved.firstOrNull {
            it.loadLabel(pm)?.toString()?.trim()?.lowercase()?.contains(q) == true
        }

        val pkg = partialMatch?.activityInfo?.packageName
        if (pkg != null) {
            val launchIntent = pm.getLaunchIntentForPackage(pkg)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                ctx.startActivity(launchIntent)
                val label = partialMatch.loadLabel(pm)?.toString() ?: appName
                return successResult(request, "Opened $label ($pkg).")
            }
        }

        return failedResult(request, "App '$appName' is not installed or has no launchable activity.")
    }

    private fun handleOpenUrl(ctx: Context, request: ToolCallRequest): ToolExecutionResult {
        val rawUrl = request.parameters["url"]?.toString()?.trim().orEmpty()
        if (rawUrl.isBlank()) return failedResult(request, "Missing 'url' parameter.")
        val normalized = if (rawUrl.startsWith("http://") || rawUrl.startsWith("https://")) {
            rawUrl
        } else {
            "https://$rawUrl"
        }
        val useChrome = request.parameters["chrome"] as? Boolean ?: false
        return launchBrowserUrl(ctx, request, normalized, useChrome, "Opened $normalized")
    }

    private fun handleWebSearch(ctx: Context, request: ToolCallRequest): ToolExecutionResult {
        val query = request.parameters["query"]?.toString()?.trim().orEmpty()
        if (query.isBlank()) return failedResult(request, "Missing 'query' parameter.")
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = "https://www.google.com/search?q=$encoded"
        val useChrome = request.parameters["chrome"] as? Boolean ?: false
        return launchBrowserUrl(ctx, request, url, useChrome, "Opened Google search for '$query'")
    }

    private fun handleYoutubeSearch(ctx: Context, request: ToolCallRequest): ToolExecutionResult {
        val query = request.parameters["query"]?.toString()?.trim().orEmpty()
        if (query.isBlank()) return failedResult(request, "Missing 'query' parameter.")
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = "https://www.youtube.com/results?search_query=$encoded"
        return launchBrowserUrl(ctx, request, url, false, "Opened YouTube search for '$query'")
    }

    private fun launchBrowserUrl(
        ctx: Context,
        request: ToolCallRequest,
        url: String,
        preferChrome: Boolean,
        successMsg: String
    ): ToolExecutionResult {
        return runCatching {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (preferChrome && isPackageInstalled(ctx, "com.android.chrome")) {
                    setPackage("com.android.chrome")
                }
            }
            ctx.startActivity(intent)
            successResult(request, successMsg, mapOf("url" to url))
        }.getOrElse {
            failedResult(request, it.message ?: "Failed to open URL '$url'.")
        }
    }

    private fun isPackageInstalled(ctx: Context, pkg: String): Boolean {
        return runCatching {
            ctx.packageManager.getPackageInfo(pkg, 0)
            true
        }.getOrDefault(false)
    }

    private suspend fun handleMakeCall(ctx: Context, request: ToolCallRequest): ToolExecutionResult {
        val target = request.parameters["name_or_number"]?.toString()
            ?: request.parameters["recipient"]?.toString()
            ?: ""
        if (target.isBlank()) return failedResult(request, "Missing 'name_or_number' parameter.")

        val resolvedNumber = resolvePhoneNumber(ctx, target)
            ?: return permissionDeniedResult(request, "READ_CONTACTS permission is required to look up '$target'.")

        if (resolvedNumber.isEmpty()) {
            return failedResult(request, "No phone number found for contact '$target'.")
        }

        val hasCallPerm = RuntimePermissionCoordinator.ensurePermissions(
            ctx,
            arrayOf(Manifest.permission.CALL_PHONE)
        )
        if (!hasCallPerm) {
            return permissionDeniedResult(request, "CALL_PHONE permission was denied.")
        }

        return runCatching {
            val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:${Uri.encode(resolvedNumber)}")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            ctx.startActivity(intent)
            successResult(request, "Placed call to $target ($resolvedNumber).")
        }.getOrElse {
            failedResult(request, it.message ?: "Failed to place call.")
        }
    }

    private suspend fun handleSendSms(ctx: Context, request: ToolCallRequest): ToolExecutionResult {
        val target = request.parameters["name_or_number"]?.toString()
            ?: request.parameters["recipient"]?.toString()
            ?: ""
        val text = request.parameters["text"]?.toString()
            ?: request.parameters["message"]?.toString()
            ?: ""
        if (target.isBlank() || text.isBlank()) {
            return failedResult(request, "Both 'name_or_number' and 'text' are required.")
        }

        val resolvedNumber = resolvePhoneNumber(ctx, target)
            ?: return permissionDeniedResult(request, "READ_CONTACTS permission is required to look up '$target'.")

        if (resolvedNumber.isEmpty()) {
            return failedResult(request, "No phone number found for contact '$target'.")
        }

        val hasSmsPerm = RuntimePermissionCoordinator.ensurePermissions(
            ctx,
            arrayOf(Manifest.permission.SEND_SMS)
        )
        if (!hasSmsPerm) {
            return permissionDeniedResult(request, "SEND_SMS permission was denied.")
        }

        return runCatching {
            val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                ctx.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }
            val parts = smsManager.divideMessage(text)
            if (parts.size > 1) {
                smsManager.sendMultipartTextMessage(resolvedNumber, null, parts, null, null)
            } else {
                smsManager.sendTextMessage(resolvedNumber, null, text, null, null)
            }
            successResult(request, "SMS sent to $target ($resolvedNumber).")
        }.getOrElse {
            failedResult(request, it.message ?: "Failed to send SMS.")
        }
    }

    private suspend fun handleSendWhatsApp(ctx: Context, request: ToolCallRequest): ToolExecutionResult {
        val target = request.parameters["phone_number"]?.toString()
            ?: request.parameters["name_or_number"]?.toString()
            ?: request.parameters["recipient"]?.toString()
            ?: ""
        val text = request.parameters["text"]?.toString()
            ?: request.parameters["message"]?.toString()
            ?: ""
        if (target.isBlank() || text.isBlank()) {
            return failedResult(request, "Both 'phone_number' and 'text' are required.")
        }

        val resolvedNumber = resolvePhoneNumber(ctx, target)
            ?: return permissionDeniedResult(request, "READ_CONTACTS permission is required to look up '$target'.")

        if (resolvedNumber.isEmpty()) {
            return failedResult(request, "No phone number found for '$target'.")
        }

        val digitsOnly = resolvedNumber.filter { it.isDigit() }
        if (digitsOnly.length < 7) {
            return failedResult(request, "Invalid phone number '$resolvedNumber' for WhatsApp.")
        }

        val encoded = URLEncoder.encode(text, "UTF-8")
        val waUrl = "https://wa.me/$digitsOnly?text=$encoded"
        runCatching {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(waUrl)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            ctx.startActivity(intent)
        }.getOrElse {
            return failedResult(request, it.message ?: "Could not open WhatsApp.")
        }

        val acc = HiyoriAccessibilityService.instance
        if (acc != null) {
            delay(1600L)
            val clicked = acc.clickElement("com.whatsapp:id/send") || acc.clickElement("Send")
            if (clicked) {
                return successResult(
                    request,
                    "sent: WhatsApp message sent to $target ($resolvedNumber).",
                    mapOf("delivery_status" to "sent")
                )
            }
        }

        return successResult(
            request,
            "opened_user_must_press_send: WhatsApp opened for $target ($resolvedNumber) with message pre-filled; please tap Send.",
            mapOf("delivery_status" to "opened_user_must_press_send")
        )
    }

    /**
     * Returns null if READ_CONTACTS permission was denied; empty string if contact not found;
     * or the phone number string if found or if [target] is already a phone number.
     */
    private suspend fun resolvePhoneNumber(ctx: Context, target: String): String? {
        val trimmed = target.trim()
        val hasLetters = trimmed.any { it.isLetter() }
        if (!hasLetters) {
            return trimmed
        }

        val hasContacts = RuntimePermissionCoordinator.ensurePermissions(
            ctx,
            arrayOf(Manifest.permission.READ_CONTACTS)
        )
        if (!hasContacts) return null

        return withContext(Dispatchers.IO) {
            var found = ""
            val cursor = ctx.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER
                ),
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
                arrayOf("%$trimmed%"),
                null
            )
            cursor?.use {
                if (it.moveToFirst()) {
                    found = it.getString(1)?.trim().orEmpty()
                }
            }
            found
        }
    }

    private suspend fun handleReadSms(ctx: Context, request: ToolCallRequest): ToolExecutionResult {
        val hasReadSms = RuntimePermissionCoordinator.ensurePermissions(
            ctx,
            arrayOf(Manifest.permission.READ_SMS)
        )
        if (!hasReadSms) {
            return permissionDeniedResult(request, "READ_SMS permission was denied.")
        }

        val count = ((request.parameters["count"] as? Number)?.toInt() ?: 10).coerceIn(1, 30)
        return withContext(Dispatchers.IO) {
            runCatching {
                val messages = mutableListOf<String>()
                val sdf = SimpleDateFormat("dd MMM HH:mm", Locale.getDefault())
                val cursor = ctx.contentResolver.query(
                    Telephony.Sms.Inbox.CONTENT_URI,
                    arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
                    null,
                    null,
                    "${Telephony.Sms.DATE} DESC"
                )
                cursor?.use {
                    var idx = 0
                    while (it.moveToNext() && idx < count) {
                        val addr = it.getString(0).orEmpty()
                        val body = it.getString(1).orEmpty().replace("\n", " ")
                        val date = sdf.format(Date(it.getLong(2)))
                        messages.add("[$date] $addr: ${body.take(140)}")
                        idx++
                    }
                }
                if (messages.isEmpty()) {
                    successResult(request, "SMS inbox is empty.")
                } else {
                    successResult(request, "Recent SMS (${messages.size}):\n" + messages.joinToString("\n"))
                }
            }.getOrElse {
                failedResult(request, it.message ?: "Failed to read SMS inbox.")
            }
        }
    }

    private fun handleReadNotifications(ctx: Context, request: ToolCallRequest): ToolExecutionResult {
        if (!HiyoriPermissionManager.isNotificationListenerEnabled(ctx)) {
            return needsSetupResult(
                request,
                "Notification Access Settings (Hiyori Notification Reader)",
                "Enable Notification Access for Hiyori Notification Reader in Settings."
            )
        }
        val svc = HiyoriNotificationService.instance
        svc?.refreshActiveNotifications()
        val count = ((request.parameters["count"] as? Number)?.toInt() ?: 10).coerceIn(1, 30)
        val list = HiyoriNotificationService.notifications.value.take(count)
        return if (list.isEmpty()) {
            successResult(request, "No active notifications right now.")
        } else {
            val formatted = list.joinToString("\n") { n ->
                val replyTag = if (n.canReply) " [canReply]" else ""
                "- [${n.packageName}] ${n.title}: ${n.text}$replyTag (key=${n.key})"
            }
            successResult(request, "Active notifications (${list.size}):\n$formatted")
        }
    }

    private fun handleReplyNotification(ctx: Context, request: ToolCallRequest): ToolExecutionResult {
        if (!HiyoriPermissionManager.isNotificationListenerEnabled(ctx)) {
            return needsSetupResult(
                request,
                "Notification Access Settings (Hiyori Notification Reader)",
                "Enable Notification Access for Hiyori Notification Reader in Settings."
            )
        }
        val key = request.parameters["key"]?.toString().orEmpty()
        val replyText = request.parameters["reply_text"]?.toString()
            ?: request.parameters["text"]?.toString()
            ?: ""
        if (key.isBlank() || replyText.isBlank()) {
            return failedResult(request, "Both 'key' and 'reply_text' are required.")
        }
        val svc = HiyoriNotificationService.instance
            ?: return failedResult(request, "Notification listener service is not bound yet.")
        return if (svc.replyToNotification(key, replyText)) {
            successResult(request, "Replied to notification '$key'.")
        } else {
            failedResult(request, "Could not reply to '$key' (notification not found or no inline reply action).")
        }
    }

    private fun requireAccessibility(ctx: Context, request: ToolCallRequest): HiyoriAccessibilityService? {
        if (!HiyoriPermissionManager.isAccessibilityEnabled(ctx)) return null
        return HiyoriAccessibilityService.instance
    }

    private fun handleScreenRead(ctx: Context, request: ToolCallRequest): ToolExecutionResult {
        val acc = requireAccessibility(ctx, request)
            ?: return needsSetupResult(
                request,
                "Accessibility Settings (Hiyori Mobile Agent)",
                "Enable Hiyori Mobile Agent in Accessibility Settings to read the screen."
            )
        val summary = acc.getScreenTreeSummary()
        return successResult(request, summary)
    }

    private fun handleTapText(ctx: Context, request: ToolCallRequest): ToolExecutionResult {
        val acc = requireAccessibility(ctx, request)
            ?: return needsSetupResult(
                request,
                "Accessibility Settings (Hiyori Mobile Agent)",
                "Enable Hiyori Mobile Agent in Accessibility Settings to tap elements."
            )
        val text = request.parameters["text"]?.toString()
            ?: request.parameters["query"]?.toString()
            ?: ""
        if (text.isBlank()) return failedResult(request, "Missing 'text' parameter.")
        return if (acc.clickElement(text)) {
            successResult(request, "Tapped element matching '$text'.")
        } else {
            failedResult(request, "No visible element matching '$text' found on screen.")
        }
    }

    private fun handleTypeText(ctx: Context, request: ToolCallRequest): ToolExecutionResult {
        val acc = requireAccessibility(ctx, request)
            ?: return needsSetupResult(
                request,
                "Accessibility Settings (Hiyori Mobile Agent)",
                "Enable Hiyori Mobile Agent in Accessibility Settings to type text."
            )
        val text = request.parameters["text"]?.toString().orEmpty()
        val target = request.parameters["target"]?.toString()
        if (text.isBlank()) return failedResult(request, "Missing 'text' parameter.")
        return if (acc.typeText(target, text)) {
            successResult(request, "Typed text into ${target ?: "focused field"}.")
        } else {
            failedResult(request, "No editable input field found on screen.")
        }
    }

    private fun handleSwipe(ctx: Context, request: ToolCallRequest): ToolExecutionResult {
        val acc = requireAccessibility(ctx, request)
            ?: return needsSetupResult(
                request,
                "Accessibility Settings (Hiyori Mobile Agent)",
                "Enable Hiyori Mobile Agent in Accessibility Settings to swipe."
            )
        val dir = request.parameters["direction"]?.toString()?.lowercase() ?: "down"
        return if (acc.scrollScreen(dir)) {
            successResult(request, "Swiped $dir on screen.")
        } else {
            failedResult(request, "Swipe gesture failed.")
        }
    }

    private fun handleGlobalAction(ctx: Context, request: ToolCallRequest, action: String): ToolExecutionResult {
        val acc = requireAccessibility(ctx, request)
            ?: return needsSetupResult(
                request,
                "Accessibility Settings (Hiyori Mobile Agent)",
                "Enable Hiyori Mobile Agent in Accessibility Settings for global navigation."
            )
        return if (acc.performGlobal(action)) {
            successResult(request, "Performed global action: $action.")
        } else {
            failedResult(request, "Global action '$action' failed.")
        }
    }

    private suspend fun handleTorch(ctx: Context, request: ToolCallRequest): ToolExecutionResult {
        val hasCam = RuntimePermissionCoordinator.ensurePermissions(
            ctx,
            arrayOf(Manifest.permission.CAMERA)
        )
        if (!hasCam) {
            return permissionDeniedResult(request, "CAMERA permission was denied.")
        }

        val stateStr = request.parameters["state"]?.toString()?.lowercase()
        val turnOn = when {
            stateStr != null -> stateStr == "on" || stateStr == "true" || stateStr == "1"
            request.parameters["on"] is Boolean -> request.parameters["on"] as Boolean
            else -> true
        }

        return runCatching {
            val cm = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = cm.cameraIdList.firstOrNull { id ->
                cm.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: return failedResult(request, "No camera flash unit found on this device.")
            cm.setTorchMode(cameraId, turnOn)
            successResult(request, "Flashlight torch turned ${if (turnOn) "ON" else "OFF"}.")
        }.getOrElse { e ->
            if (e is SecurityException) {
                permissionDeniedResult(request, "CAMERA permission denied for torch.")
            } else {
                failedResult(request, e.message ?: "Failed to toggle torch.")
            }
        }
    }

    private fun handleVolume(ctx: Context, request: ToolCallRequest): ToolExecutionResult {
        val streamName = request.parameters["stream"]?.toString()?.lowercase() ?: "music"
        val rawLevel = (request.parameters["level"] as? Number)?.toInt()
            ?: return failedResult(request, "Missing 'level' integer parameter.")

        val streamType = when (streamName) {
            "music", "media" -> AudioManager.STREAM_MUSIC
            "ring", "ringtone" -> AudioManager.STREAM_RING
            "alarm" -> AudioManager.STREAM_ALARM
            "notification", "notif" -> AudioManager.STREAM_NOTIFICATION
            "call", "voice" -> AudioManager.STREAM_VOICE_CALL
            else -> AudioManager.STREAM_MUSIC
        }

        return runCatching {
            val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val maxVol = am.getStreamMaxVolume(streamType)
            val targetVol = if (rawLevel > maxVol && rawLevel <= 100) {
                ((rawLevel / 100f) * maxVol).toInt().coerceIn(0, maxVol)
            } else {
                rawLevel.coerceIn(0, maxVol)
            }
            am.setStreamVolume(streamType, targetVol, 0)
            successResult(request, "Set $streamName volume to $targetVol/$maxVol.")
        }.getOrElse {
            failedResult(request, it.message ?: "Failed to set volume.")
        }
    }

    private fun handleBrightness(ctx: Context, request: ToolCallRequest): ToolExecutionResult {
        if (!Settings.System.canWrite(ctx)) {
            return needsSetupResult(
                request,
                "Modify System Settings",
                "Allow 'Modify system settings' for Hiyori to change screen brightness."
            )
        }
        val level = (request.parameters["level"] as? Number)?.toInt()
            ?: return failedResult(request, "Missing 'level' integer parameter (0-255).")
        val clamped = level.coerceIn(0, 255)
        return runCatching {
            Settings.System.putInt(
                ctx.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
            )
            Settings.System.putInt(
                ctx.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS,
                clamped
            )
            successResult(request, "Screen brightness set to $clamped/255.")
        }.getOrElse {
            failedResult(request, it.message ?: "Failed to set brightness.")
        }
    }

    private fun handleDnd(ctx: Context, request: ToolCallRequest): ToolExecutionResult {
        if (!HiyoriPermissionManager.isDndAccessGranted(ctx)) {
            return needsSetupResult(
                request,
                "Do Not Disturb Access Settings",
                "Grant Do Not Disturb access to Hiyori in Settings."
            )
        }
        val stateStr = request.parameters["state"]?.toString()?.lowercase()
        val turnOn = when {
            stateStr != null -> stateStr == "on" || stateStr == "true" || stateStr == "1"
            request.parameters["on"] is Boolean -> request.parameters["on"] as Boolean
            else -> true
        }
        return runCatching {
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val filter = if (turnOn) {
                NotificationManager.INTERRUPTION_FILTER_PRIORITY
            } else {
                NotificationManager.INTERRUPTION_FILTER_ALL
            }
            nm.setInterruptionFilter(filter)
            successResult(request, "Do Not Disturb turned ${if (turnOn) "ON" else "OFF"}.")
        }.getOrElse {
            failedResult(request, it.message ?: "Failed to change Do Not Disturb.")
        }
    }

    private fun handleWifiPanel(ctx: Context, request: ToolCallRequest): ToolExecutionResult {
        return runCatching {
            val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                Intent(Settings.Panel.ACTION_WIFI)
            } else {
                Intent(Settings.ACTION_WIFI_SETTINGS)
            }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(intent)
            successResult(request, "Opened Wi-Fi settings panel. Please toggle Wi-Fi there.")
        }.getOrElse {
            failedResult(request, it.message ?: "Could not open Wi-Fi panel.")
        }
    }

    private fun handleBluetoothPanel(ctx: Context, request: ToolCallRequest): ToolExecutionResult {
        return runCatching {
            val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(intent)
            successResult(request, "Opened Bluetooth settings page. Please toggle Bluetooth there.")
        }.getOrElse {
            failedResult(request, it.message ?: "Could not open Bluetooth settings.")
        }
    }

    private fun handleDeviceInfo(ctx: Context, request: ToolCallRequest): ToolExecutionResult {
        return runCatching {
            val bm = ctx.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            val batteryPct = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).coerceIn(0, 100)
            val isCharging = bm.isCharging

            val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val memInfo = ActivityManager.MemoryInfo()
            am.getMemoryInfo(memInfo)
            val ramFreeGb = String.format(Locale.US, "%.1f", memInfo.availMem / (1024f * 1024f * 1024f))
            val ramTotalGb = String.format(Locale.US, "%.1f", memInfo.totalMem / (1024f * 1024f * 1024f))

            val stat = StatFs(Environment.getDataDirectory().path)
            val availGb = String.format(Locale.US, "%.1f", (stat.availableBlocksLong * stat.blockSizeLong) / (1024f * 1024f * 1024f))
            val totalGb = String.format(Locale.US, "%.1f", (stat.blockCountLong * stat.blockSizeLong) / (1024f * 1024f * 1024f))

            val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val caps = cm.getNetworkCapabilities(cm.activeNetwork)
            val net = when {
                caps == null -> "Offline"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
                else -> "Connected"
            }

            val msg = "Battery: $batteryPct% (${if (isCharging) "Charging" else "Discharging"}) | RAM: $ramFreeGb/$ramTotalGb GB free | Storage: $availGb/$totalGb GB free | Network: $net"
            successResult(
                request,
                msg,
                mapOf(
                    "battery_percent" to batteryPct,
                    "is_charging" to isCharging,
                    "ram_free_gb" to ramFreeGb,
                    "storage_free_gb" to availGb,
                    "network" to net
                )
            )
        }.getOrElse {
            failedResult(request, it.message ?: "Failed to read device info.")
        }
    }

    // --- Shared Storage File Tools ---

    private fun requireAllFiles(request: ToolCallRequest): ToolExecutionResult? {
        if (!HiyoriPermissionManager.isAllFilesAccessGranted()) {
            return needsSetupResult(
                request,
                "All Files Access Settings",
                "Grant 'All files access' to Hiyori in Settings to manage shared storage files."
            )
        }
        return null
    }

    private fun resolveStorageFile(rawPath: String): File {
        val trimmed = rawPath.trim()
        val root = Environment.getExternalStorageDirectory()
        return if (trimmed.startsWith("/")) {
            File(trimmed)
        } else {
            val base = File(root, "Hiyori").apply { if (!exists()) mkdirs() }
            File(base, trimmed)
        }
    }

    private suspend fun handleFileCreate(request: ToolCallRequest): ToolExecutionResult {
        requireAllFiles(request)?.let { return it }
        val path = request.parameters["path"]?.toString().orEmpty()
        val content = request.parameters["content"]?.toString().orEmpty()
        if (path.isBlank()) return failedResult(request, "Missing 'path' parameter.")
        return withContext(Dispatchers.IO) {
            runCatching {
                val file = resolveStorageFile(path)
                file.parentFile?.let { if (!it.exists()) it.mkdirs() }
                file.writeText(content)
                successResult(request, "Created ${file.absolutePath} (${file.length()} bytes).")
            }.getOrElse { failedResult(request, it.message ?: "Failed to create file.") }
        }
    }

    private suspend fun handleFileRead(request: ToolCallRequest): ToolExecutionResult {
        requireAllFiles(request)?.let { return it }
        val path = request.parameters["path"]?.toString().orEmpty()
        if (path.isBlank()) return failedResult(request, "Missing 'path' parameter.")
        return withContext(Dispatchers.IO) {
            runCatching {
                val file = resolveStorageFile(path)
                if (!file.exists()) return@runCatching failedResult(request, "File not found: ${file.absolutePath}")
                val text = file.readText()
                successResult(request, "File ${file.absolutePath} (${file.length()} bytes):\n${text.take(4000)}")
            }.getOrElse { failedResult(request, it.message ?: "Failed to read file.") }
        }
    }

    private suspend fun handleFileWrite(request: ToolCallRequest): ToolExecutionResult {
        requireAllFiles(request)?.let { return it }
        val path = request.parameters["path"]?.toString().orEmpty()
        val content = request.parameters["content"]?.toString().orEmpty()
        val append = request.parameters["append"] as? Boolean ?: false
        if (path.isBlank()) return failedResult(request, "Missing 'path' parameter.")
        return withContext(Dispatchers.IO) {
            runCatching {
                val file = resolveStorageFile(path)
                file.parentFile?.let { if (!it.exists()) it.mkdirs() }
                if (append && file.exists()) file.appendText(content) else file.writeText(content)
                successResult(request, "Wrote ${file.length()} bytes to ${file.absolutePath}.")
            }.getOrElse { failedResult(request, it.message ?: "Failed to write file.") }
        }
    }

    private suspend fun handleFileList(request: ToolCallRequest): ToolExecutionResult {
        requireAllFiles(request)?.let { return it }
        val rawPath = request.parameters["path"]?.toString().orEmpty()
        return withContext(Dispatchers.IO) {
            runCatching {
                val dir = if (rawPath.isBlank()) {
                    Environment.getExternalStorageDirectory()
                } else {
                    resolveStorageFile(rawPath)
                }
                if (!dir.exists() || !dir.isDirectory) {
                    return@runCatching failedResult(request, "Directory not found: ${dir.absolutePath}")
                }
                val items = dir.listFiles()?.sortedBy { it.name.lowercase() }?.take(40).orEmpty()
                if (items.isEmpty()) {
                    successResult(request, "Directory ${dir.absolutePath} is empty.")
                } else {
                    val listing = items.joinToString("\n") { f ->
                        if (f.isDirectory) "[DIR] ${f.name}/" else "${f.name} (${f.length()} B)"
                    }
                    successResult(request, "Contents of ${dir.absolutePath}:\n$listing")
                }
            }.getOrElse { failedResult(request, it.message ?: "Failed to list directory.") }
        }
    }

    private suspend fun handleFileMove(request: ToolCallRequest): ToolExecutionResult {
        requireAllFiles(request)?.let { return it }
        val srcPath = request.parameters["source_path"]?.toString().orEmpty()
        val dstPath = request.parameters["dest_path"]?.toString().orEmpty()
        if (srcPath.isBlank() || dstPath.isBlank()) {
            return failedResult(request, "Both 'source_path' and 'dest_path' are required.")
        }
        return withContext(Dispatchers.IO) {
            runCatching {
                val src = resolveStorageFile(srcPath)
                if (!src.exists()) return@runCatching failedResult(request, "Source not found: ${src.absolutePath}")
                val dst = resolveStorageFile(dstPath)
                dst.parentFile?.let { if (!it.exists()) it.mkdirs() }
                if (!src.renameTo(dst)) {
                    src.copyTo(dst, overwrite = true)
                    src.delete()
                }
                successResult(request, "Moved to ${dst.absolutePath}.")
            }.getOrElse { failedResult(request, it.message ?: "Failed to move file.") }
        }
    }

    private suspend fun handleFileRename(request: ToolCallRequest): ToolExecutionResult {
        requireAllFiles(request)?.let { return it }
        val srcPath = request.parameters["source_path"]?.toString().orEmpty()
        val newName = request.parameters["new_name"]?.toString().orEmpty()
        if (srcPath.isBlank() || newName.isBlank()) {
            return failedResult(request, "Both 'source_path' and 'new_name' are required.")
        }
        return withContext(Dispatchers.IO) {
            runCatching {
                val src = resolveStorageFile(srcPath)
                if (!src.exists()) return@runCatching failedResult(request, "Source not found: ${src.absolutePath}")
                val dst = if (newName.startsWith("/")) File(newName) else File(src.parentFile, newName)
                if (!src.renameTo(dst)) {
                    return@runCatching failedResult(request, "Could not rename ${src.name} to ${dst.name}.")
                }
                successResult(request, "Renamed to ${dst.absolutePath}.")
            }.getOrElse { failedResult(request, it.message ?: "Failed to rename file.") }
        }
    }

    private suspend fun handleFileSearch(request: ToolCallRequest): ToolExecutionResult {
        requireAllFiles(request)?.let { return it }
        val query = request.parameters["query"]?.toString()?.trim().orEmpty()
        if (query.isBlank()) return failedResult(request, "Missing 'query' parameter.")
        return withContext(Dispatchers.IO) {
            runCatching {
                val root = Environment.getExternalStorageDirectory()
                val q = query.lowercase()
                val matches = mutableListOf<String>()
                val skip = setOf("Android", ".thumbnails", ".cache")
                fun walk(dir: File, depth: Int) {
                    if (depth > 4 || matches.size >= 25) return
                    val children = dir.listFiles() ?: return
                    for (c in children) {
                        if (matches.size >= 25) break
                        if (c.isDirectory) {
                            if (c.name !in skip && !c.name.startsWith(".")) walk(c, depth + 1)
                        } else if (c.name.lowercase().contains(q)) {
                            matches.add("${c.absolutePath} (${c.length()} B)")
                        }
                    }
                }
                walk(root, 0)
                if (matches.isEmpty()) {
                    successResult(request, "No files matching '$query' found.")
                } else {
                    successResult(request, "Found ${matches.size} file(s):\n" + matches.joinToString("\n"))
                }
            }.getOrElse { failedResult(request, it.message ?: "File search failed.") }
        }
    }

    private suspend fun handleFileDelete(request: ToolCallRequest): ToolExecutionResult {
        requireAllFiles(request)?.let { return it }
        val path = request.parameters["path"]?.toString().orEmpty()
        if (path.isBlank()) return failedResult(request, "Missing 'path' parameter.")
        return withContext(Dispatchers.IO) {
            runCatching {
                val file = resolveStorageFile(path)
                if (!file.exists()) return@runCatching failedResult(request, "File not found: ${file.absolutePath}")
                val ok = if (file.isDirectory) file.deleteRecursively() else file.delete()
                if (!ok) return@runCatching failedResult(request, "Could not delete ${file.absolutePath}.")
                successResult(request, "Deleted ${file.absolutePath}.")
            }.getOrElse { failedResult(request, it.message ?: "Failed to delete file.") }
        }
    }

    // --- Calendar & Location ---

    private suspend fun handleCalendarEvents(ctx: Context, request: ToolCallRequest): ToolExecutionResult {
        val createTitle = request.parameters["title"]?.toString()?.trim()
        if (!createTitle.isNullOrBlank()) {
            val hasCal = RuntimePermissionCoordinator.ensurePermissions(
                ctx,
                arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
            )
            if (!hasCal) {
                return permissionDeniedResult(request, "READ_CALENDAR / WRITE_CALENDAR permission was denied.")
            }
            return withContext(Dispatchers.IO) {
                runCatching {
                    var calId = 1L
                    ctx.contentResolver.query(
                        CalendarContract.Calendars.CONTENT_URI,
                        arrayOf(CalendarContract.Calendars._ID),
                        "${CalendarContract.Calendars.VISIBLE} = 1",
                        null,
                        null
                    )?.use { c ->
                        if (c.moveToFirst()) calId = c.getLong(0)
                    }
                    val startMs = (request.parameters["start_time_millis"] as? Number)?.toLong()
                        ?: (System.currentTimeMillis() + 3600_000L)
                    val endMs = (request.parameters["end_time_millis"] as? Number)?.toLong()
                        ?: (startMs + 3600_000L)
                    val values = ContentValues().apply {
                        put(CalendarContract.Events.CALENDAR_ID, calId)
                        put(CalendarContract.Events.TITLE, createTitle)
                        put(CalendarContract.Events.DTSTART, startMs)
                        put(CalendarContract.Events.DTEND, endMs)
                        put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
                    }
                    val uri = ctx.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
                    if (uri != null) {
                        successResult(request, "Created calendar event '$createTitle'.")
                    } else {
                        failedResult(request, "Calendar provider returned null when inserting event.")
                    }
                }.getOrElse { failedResult(request, it.message ?: "Failed to create calendar event.") }
            }
        }

        val hasReadCal = RuntimePermissionCoordinator.ensurePermissions(
            ctx,
            arrayOf(Manifest.permission.READ_CALENDAR)
        )
        if (!hasReadCal) {
            return permissionDeniedResult(request, "READ_CALENDAR permission was denied.")
        }

        val daysAhead = ((request.parameters["days_ahead"] as? Number)?.toInt() ?: 7).coerceIn(1, 60)
        return withContext(Dispatchers.IO) {
            runCatching {
                val now = System.currentTimeMillis()
                val end = now + daysAhead * 86_400_000L
                val sdf = SimpleDateFormat("dd MMM HH:mm", Locale.getDefault())
                val events = mutableListOf<String>()
                val cursor = ctx.contentResolver.query(
                    CalendarContract.Events.CONTENT_URI,
                    arrayOf(
                        CalendarContract.Events.TITLE,
                        CalendarContract.Events.DTSTART,
                        CalendarContract.Events.EVENT_LOCATION
                    ),
                    "${CalendarContract.Events.DTSTART} >= ? AND ${CalendarContract.Events.DTSTART} <= ?",
                    arrayOf(now.toString(), end.toString()),
                    "${CalendarContract.Events.DTSTART} ASC"
                )
                cursor?.use {
                    while (it.moveToNext() && events.size < 20) {
                        val title = it.getString(0).orEmpty().ifBlank { "(No title)" }
                        val start = sdf.format(Date(it.getLong(1)))
                        val loc = it.getString(2).orEmpty()
                        val locPart = if (loc.isNotBlank()) " @ $loc" else ""
                        events.add("- [$start] $title$locPart")
                    }
                }
                if (events.isEmpty()) {
                    successResult(request, "No calendar events in the next $daysAhead day(s).")
                } else {
                    successResult(request, "Upcoming calendar events (${events.size}):\n" + events.joinToString("\n"))
                }
            }.getOrElse { failedResult(request, it.message ?: "Failed to query calendar events.") }
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun handleLocation(ctx: Context, request: ToolCallRequest): ToolExecutionResult {
        val fineGranted = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fineGranted && !coarseGranted) {
            RuntimePermissionCoordinator.ensurePermissions(
                ctx,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
            val fineAfter = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            val coarseAfter = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
            if (!fineAfter && !coarseAfter) {
                return permissionDeniedResult(request, "Location permission (ACCESS_FINE_LOCATION / ACCESS_COARSE_LOCATION) was denied.")
            }
        }

        return runCatching {
            val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val providers = listOf(
                LocationManager.GPS_PROVIDER,
                LocationManager.NETWORK_PROVIDER,
                LocationManager.PASSIVE_PROVIDER
            )
            var best: Location? = null
            for (p in providers) {
                if (runCatching { lm.isProviderEnabled(p) }.getOrDefault(false)) {
                    val loc = runCatching { lm.getLastKnownLocation(p) }.getOrNull()
                    if (loc != null && (best == null || loc.time > best.time)) {
                        best = loc
                    }
                }
            }

            if (best == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val activeProvider = providers.firstOrNull {
                    runCatching { lm.isProviderEnabled(it) }.getOrDefault(false)
                }
                if (activeProvider != null) {
                    val deferred = CompletableDeferred<Location?>()
                    val signal = CancellationSignal()
                    lm.getCurrentLocation(
                        activeProvider,
                        signal,
                        ctx.mainExecutor
                    ) { loc -> deferred.complete(loc) }
                    best = withTimeoutOrNull(5000L) { deferred.await() }
                    if (best == null) signal.cancel()
                }
            }

            if (best == null) {
                failedResult(request, "Location unavailable (GPS/Network location may be turned off).")
            } else {
                val lat = String.format(Locale.US, "%.6f", best.latitude)
                val lon = String.format(Locale.US, "%.6f", best.longitude)
                val acc = best.accuracy.toInt()
                successResult(
                    request,
                    "Current location: $lat, $lon (accuracy ~${acc}m via ${best.provider})",
                    mapOf("latitude" to best.latitude, "longitude" to best.longitude, "accuracy_m" to acc)
                )
            }
        }.getOrElse {
            failedResult(request, it.message ?: "Failed to obtain location.")
        }
    }
}
