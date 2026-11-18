package com.example.permissions

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.AlarmManager
import android.app.AppOpsManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.service.HiyoriAccessibilityService
import com.example.service.HiyoriNotificationService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

enum class PermissionStatusLabel(val text: String) {
    GRANTED("Granted"),
    NOT_GRANTED("Not granted"),
    NEEDS_SETTINGS("Needs Settings")
}

data class RuntimePermissionItem(
    val id: String,
    val permission: String,
    val title: String,
    val subtitle: String,
    val minSdk: Int = 1,
    val maxSdk: Int = Int.MAX_VALUE
) {
    fun isApplicableOnCurrentSdk(): Boolean =
        Build.VERSION.SDK_INT in minSdk..maxSdk
}

enum class SpecialPermissionType(
    val title: String,
    val subtitle: String,
    val settingsPageName: String
) {
    ACCESSIBILITY(
        title = "Accessibility Service",
        subtitle = "Read screen node tree, tap, type, swipe & global actions",
        settingsPageName = "Accessibility Settings (Hiyori Mobile Agent)"
    ),
    NOTIFICATION_LISTENER(
        title = "Notification Listener",
        subtitle = "Read incoming notifications & send direct replies",
        settingsPageName = "Notification Access Settings (Hiyori Notification Reader)"
    ),
    ALL_FILES(
        title = "All Files Access",
        subtitle = "Create, read, write, move & delete shared storage files",
        settingsPageName = "All Files Access Settings"
    ),
    WRITE_SETTINGS(
        title = "Modify System Settings",
        subtitle = "Change screen brightness & system preferences",
        settingsPageName = "Modify System Settings"
    ),
    DND_ACCESS(
        title = "Do Not Disturb Access",
        subtitle = "Toggle Do Not Disturb / interruption filter",
        settingsPageName = "Do Not Disturb Access Settings"
    ),
    OVERLAY(
        title = "Display Over Other Apps",
        subtitle = "Allow floating agent overlays over other apps",
        settingsPageName = "Display Over Other Apps Settings"
    ),
    USAGE_ACCESS(
        title = "Usage Access",
        subtitle = "Detect active foreground app & usage statistics",
        settingsPageName = "Usage Access Settings"
    ),
    BATTERY_OPTIMIZATION(
        title = "Battery Optimisation Exemption",
        subtitle = "Keep Hiyori's autonomous loop alive in background",
        settingsPageName = "Battery Optimisation Exemption Settings"
    ),
    EXACT_ALARMS(
        title = "Exact Alarms",
        subtitle = "Schedule exact-time reminders & triggers",
        settingsPageName = "Alarms & Reminders Settings"
    ),
    INSTALL_UNKNOWN_APPS(
        title = "Install Unknown Apps",
        subtitle = "Allow installing APK packages when requested",
        settingsPageName = "Install Unknown Apps Settings"
    )
}

object HiyoriPermissionManager {

    val allRuntimePermissions: List<RuntimePermissionItem> = listOf(
        RuntimePermissionItem(
            id = "call_phone",
            permission = Manifest.permission.CALL_PHONE,
            title = "Make Calls (CALL_PHONE)",
            subtitle = "Place phone calls directly"
        ),
        RuntimePermissionItem(
            id = "read_contacts",
            permission = Manifest.permission.READ_CONTACTS,
            title = "Read Contacts (READ_CONTACTS)",
            subtitle = "Find contacts by name for calls & messages"
        ),
        RuntimePermissionItem(
            id = "send_sms",
            permission = Manifest.permission.SEND_SMS,
            title = "Send SMS (SEND_SMS)",
            subtitle = "Send text messages via SIM"
        ),
        RuntimePermissionItem(
            id = "read_sms",
            permission = Manifest.permission.READ_SMS,
            title = "Read SMS (READ_SMS)",
            subtitle = "Read inbox SMS messages"
        ),
        RuntimePermissionItem(
            id = "read_phone_state",
            permission = Manifest.permission.READ_PHONE_STATE,
            title = "Phone State (READ_PHONE_STATE)",
            subtitle = "Read cellular & SIM call status"
        ),
        RuntimePermissionItem(
            id = "camera",
            permission = Manifest.permission.CAMERA,
            title = "Camera & Torch (CAMERA)",
            subtitle = "Control flashlight torch & camera vision"
        ),
        RuntimePermissionItem(
            id = "record_audio",
            permission = Manifest.permission.RECORD_AUDIO,
            title = "Microphone (RECORD_AUDIO)",
            subtitle = "Voice input & speech recognition"
        ),
        RuntimePermissionItem(
            id = "post_notifications",
            permission = Manifest.permission.POST_NOTIFICATIONS,
            title = "Notifications (POST_NOTIFICATIONS)",
            subtitle = "Agent foreground service notification (Android 13+)",
            minSdk = Build.VERSION_CODES.TIRAMISU
        ),
        RuntimePermissionItem(
            id = "read_media_images",
            permission = Manifest.permission.READ_MEDIA_IMAGES,
            title = "Media Images (READ_MEDIA_IMAGES)",
            subtitle = "Access photos on device (Android 13+)",
            minSdk = Build.VERSION_CODES.TIRAMISU
        ),
        RuntimePermissionItem(
            id = "read_media_video",
            permission = Manifest.permission.READ_MEDIA_VIDEO,
            title = "Media Video (READ_MEDIA_VIDEO)",
            subtitle = "Access videos on device (Android 13+)",
            minSdk = Build.VERSION_CODES.TIRAMISU
        ),
        RuntimePermissionItem(
            id = "read_media_audio",
            permission = Manifest.permission.READ_MEDIA_AUDIO,
            title = "Media Audio (READ_MEDIA_AUDIO)",
            subtitle = "Access audio files on device (Android 13+)",
            minSdk = Build.VERSION_CODES.TIRAMISU
        ),
        RuntimePermissionItem(
            id = "read_external_storage",
            permission = Manifest.permission.READ_EXTERNAL_STORAGE,
            title = "External Storage (READ_EXTERNAL_STORAGE)",
            subtitle = "Read media & files (Android 12 and below)",
            maxSdk = Build.VERSION_CODES.S_V2
        ),
        RuntimePermissionItem(
            id = "access_fine_location",
            permission = Manifest.permission.ACCESS_FINE_LOCATION,
            title = "Fine Location (ACCESS_FINE_LOCATION)",
            subtitle = "Precise GPS coordinates for location queries"
        ),
        RuntimePermissionItem(
            id = "access_coarse_location",
            permission = Manifest.permission.ACCESS_COARSE_LOCATION,
            title = "Coarse Location (ACCESS_COARSE_LOCATION)",
            subtitle = "Approximate network-based location"
        ),
        RuntimePermissionItem(
            id = "read_calendar",
            permission = Manifest.permission.READ_CALENDAR,
            title = "Read Calendar (READ_CALENDAR)",
            subtitle = "View upcoming calendar events"
        ),
        RuntimePermissionItem(
            id = "write_calendar",
            permission = Manifest.permission.WRITE_CALENDAR,
            title = "Write Calendar (WRITE_CALENDAR)",
            subtitle = "Create & update calendar events"
        ),
        RuntimePermissionItem(
            id = "bluetooth_connect",
            permission = Manifest.permission.BLUETOOTH_CONNECT,
            title = "Bluetooth Connect (BLUETOOTH_CONNECT)",
            subtitle = "Connect & inspect paired Bluetooth devices (Android 12+)",
            minSdk = Build.VERSION_CODES.S
        ),
        RuntimePermissionItem(
            id = "bluetooth_scan",
            permission = Manifest.permission.BLUETOOTH_SCAN,
            title = "Bluetooth Scan (BLUETOOTH_SCAN)",
            subtitle = "Scan nearby Bluetooth devices (Android 12+)",
            minSdk = Build.VERSION_CODES.S
        ),
        RuntimePermissionItem(
            id = "nearby_wifi_devices",
            permission = Manifest.permission.NEARBY_WIFI_DEVICES,
            title = "Nearby Wi-Fi (NEARBY_WIFI_DEVICES)",
            subtitle = "Discover nearby Wi-Fi devices (Android 13+)",
            minSdk = Build.VERSION_CODES.TIRAMISU
        )
    )

    fun isRuntimePermissionGranted(context: Context, item: RuntimePermissionItem): Boolean {
        if (!item.isApplicableOnCurrentSdk()) return true
        return ContextCompat.checkSelfPermission(context, item.permission) == PackageManager.PERMISSION_GRANTED
    }

    fun isRuntimePermissionGranted(context: Context, permission: String): Boolean {
        val item = allRuntimePermissions.find { it.permission == permission }
        if (item != null && !item.isApplicableOnCurrentSdk()) return true
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    fun getRuntimeStatus(context: Context, item: RuntimePermissionItem): PermissionStatusLabel {
        return if (isRuntimePermissionGranted(context, item)) {
            PermissionStatusLabel.GRANTED
        } else {
            PermissionStatusLabel.NOT_GRANTED
        }
    }

    fun isSpecialPermissionGranted(context: Context, type: SpecialPermissionType): Boolean {
        return when (type) {
            SpecialPermissionType.ACCESSIBILITY -> isAccessibilityEnabled(context)
            SpecialPermissionType.NOTIFICATION_LISTENER -> isNotificationListenerEnabled(context)
            SpecialPermissionType.ALL_FILES -> isAllFilesAccessGranted()
            SpecialPermissionType.WRITE_SETTINGS -> Settings.System.canWrite(context)
            SpecialPermissionType.DND_ACCESS -> isDndAccessGranted(context)
            SpecialPermissionType.OVERLAY -> Settings.canDrawOverlays(context)
            SpecialPermissionType.USAGE_ACCESS -> isUsageAccessGranted(context)
            SpecialPermissionType.BATTERY_OPTIMIZATION -> isBatteryOptimizationIgnored(context)
            SpecialPermissionType.EXACT_ALARMS -> isExactAlarmGranted(context)
            SpecialPermissionType.INSTALL_UNKNOWN_APPS -> isInstallUnknownAppsGranted(context)
        }
    }

    fun getSpecialStatus(context: Context, type: SpecialPermissionType): PermissionStatusLabel {
        return if (isSpecialPermissionGranted(context, type)) {
            PermissionStatusLabel.GRANTED
        } else {
            PermissionStatusLabel.NEEDS_SETTINGS
        }
    }

    fun isAccessibilityEnabled(context: Context): Boolean {
        if (HiyoriAccessibilityService.isConnected.value) return true
        return runCatching {
            val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
            val enabled = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            enabled.any { info ->
                val svc = info.resolveInfo?.serviceInfo
                svc?.packageName == context.packageName &&
                    svc.name == HiyoriAccessibilityService::class.java.name
            }
        }.getOrDefault(false)
    }

    fun isNotificationListenerEnabled(context: Context): Boolean {
        if (HiyoriNotificationService.isConnected.value) return true
        return runCatching {
            NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
        }.getOrDefault(false)
    }

    fun isAllFilesAccessGranted(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true
        }
    }

    fun isDndAccessGranted(context: Context): Boolean {
        return runCatching {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.isNotificationPolicyAccessGranted
        }.getOrDefault(false)
    }

    fun isUsageAccessGranted(context: Context): Boolean {
        return runCatching {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val mode = appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
            mode == AppOpsManager.MODE_ALLOWED
        }.getOrDefault(false)
    }

    fun isBatteryOptimizationIgnored(context: Context): Boolean {
        return runCatching {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            pm.isIgnoringBatteryOptimizations(context.packageName)
        }.getOrDefault(false)
    }

    fun isExactAlarmGranted(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching {
                val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                am.canScheduleExactAlarms()
            }.getOrDefault(false)
        } else {
            true
        }
    }

    fun isInstallUnknownAppsGranted(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            runCatching {
                context.packageManager.canRequestPackageInstalls()
            }.getOrDefault(false)
        } else {
            true
        }
    }

    fun buildSpecialSettingsIntent(context: Context, type: SpecialPermissionType): Intent {
        val pkgUri = Uri.parse("package:${context.packageName}")
        val primary = when (type) {
            SpecialPermissionType.ACCESSIBILITY ->
                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            SpecialPermissionType.NOTIFICATION_LISTENER ->
                Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            SpecialPermissionType.ALL_FILES ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, pkgUri)
                } else {
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkgUri)
                }
            SpecialPermissionType.WRITE_SETTINGS ->
                Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, pkgUri)
            SpecialPermissionType.DND_ACCESS ->
                Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
            SpecialPermissionType.OVERLAY ->
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, pkgUri)
            SpecialPermissionType.USAGE_ACCESS ->
                Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            SpecialPermissionType.BATTERY_OPTIMIZATION ->
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkgUri)
            SpecialPermissionType.EXACT_ALARMS ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, pkgUri)
                } else {
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkgUri)
                }
            SpecialPermissionType.INSTALL_UNKNOWN_APPS ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, pkgUri)
                } else {
                    Intent(Settings.ACTION_SECURITY_SETTINGS)
                }
        }
        primary.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return primary
    }

    fun openSpecialSettings(context: Context, type: SpecialPermissionType): Boolean {
        val pkgUri = Uri.parse("package:${context.packageName}")
        val primary = buildSpecialSettingsIntent(context, type)
        if (tryStartActivity(context, primary)) return true

        val fallback = when (type) {
            SpecialPermissionType.BATTERY_OPTIMIZATION ->
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            SpecialPermissionType.ALL_FILES ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                } else {
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkgUri)
                }
            else -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkgUri)
        }.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }

        return tryStartActivity(context, fallback)
    }

    private fun tryStartActivity(context: Context, intent: Intent): Boolean {
        return runCatching {
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }
}

object RuntimePermissionCoordinator {
    @Volatile
    private var launcher: ((Array<String>) -> Unit)? = null

    @Volatile
    private var pendingDeferred: CompletableDeferred<Map<String, Boolean>>? = null

    fun registerLauncher(launchFn: (Array<String>) -> Unit) {
        launcher = launchFn
    }

    fun unregisterLauncher() {
        launcher = null
    }

    fun onPermissionsResult(results: Map<String, Boolean>) {
        pendingDeferred?.complete(results)
        pendingDeferred = null
    }

    suspend fun ensurePermissions(context: Context, permissions: Array<String>): Boolean {
        val missing = permissions.filter { !HiyoriPermissionManager.isRuntimePermissionGranted(context, it) }
        if (missing.isEmpty()) return true

        val launchFn = launcher ?: return false
        val deferred = CompletableDeferred<Map<String, Boolean>>()
        pendingDeferred?.cancel()
        pendingDeferred = deferred

        withContext(Dispatchers.Main) {
            launchFn(missing.toTypedArray())
        }

        withTimeoutOrNull(60_000L) {
            deferred.await()
        }
        pendingDeferred = null
        return permissions.all { HiyoriPermissionManager.isRuntimePermissionGranted(context, it) }
    }
}
