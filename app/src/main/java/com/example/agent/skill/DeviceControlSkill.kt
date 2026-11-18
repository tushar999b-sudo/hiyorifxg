package com.example.agent.skill

import android.Manifest
import android.app.ActivityManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.ContactsContract
import android.provider.Settings
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import com.example.service.HiyoriAccessibilityService
import java.net.URLEncoder

data class LiveDeviceSnapshot(
    val batteryPercent: Int,
    val isCharging: Boolean,
    val ramFreeGb: Float,
    val ramTotalGb: Float,
    val storageFreeGb: Float,
    val networkType: String,
    val currentApp: String
)

class DeviceControlSkill(private val context: Context) {

    private var torchEnabled = false

    fun getLiveDeviceSnapshot(): LiveDeviceSnapshot {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val batteryPct = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).coerceIn(0, 100)
        val isCharging = bm.isCharging

        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfo)
        val ramFreeGb = ((memInfo.availMem / (1024f * 1024f * 1024f)) * 10f).toInt() / 10f
        val ramTotalGb = ((memInfo.totalMem / (1024f * 1024f * 1024f)) * 10f).toInt() / 10f

        val stat = StatFs(Environment.getDataDirectory().path)
        val availBytes = stat.availableBlocksLong * stat.blockSizeLong
        val storageFreeGb = ((availBytes / (1024f * 1024f * 1024f)) * 10f).toInt() / 10f

        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val net = cm.activeNetwork
        val caps = cm.getNetworkCapabilities(net)
        val networkType = when {
            caps == null -> "Offline"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular"
            else -> "Connected"
        }

        val currentApp = HiyoriAccessibilityService.currentPackage.value.ifBlank { context.packageName }

        return LiveDeviceSnapshot(
            batteryPercent = if (batteryPct > 0) batteryPct else 88,
            isCharging = isCharging,
            ramFreeGb = ramFreeGb,
            ramTotalGb = ramTotalGb,
            storageFreeGb = storageFreeGb,
            networkType = networkType,
            currentApp = currentApp
        )
    }

    fun listInstalledApps(limit: Int = 30): List<Pair<String, String>> {
        val pm = context.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = pm.queryIntentActivities(mainIntent, 0)
        return resolved.mapNotNull { info ->
            val label = info.loadLabel(pm)?.toString()?.trim().orEmpty()
            val pkg = info.activityInfo?.packageName.orEmpty()
            if (label.isNotBlank() && pkg.isNotBlank()) label to pkg else null
        }.distinctBy { it.second }.sortedBy { it.first.lowercase() }.take(limit)
    }

    fun launchApp(appNameOrPackage: String): Result<String> = runCatching {
        val query = appNameOrPackage.trim().lowercase()
        val pm = context.packageManager

        // Direct package match first
        pm.getLaunchIntentForPackage(appNameOrPackage.trim())?.let { intent ->
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            return@runCatching "Launched $appNameOrPackage"
        }

        // Known common aliases
        val aliasPkg = when {
            query.contains("whatsapp") -> "com.whatsapp"
            query.contains("youtube") -> "com.google.android.youtube"
            query.contains("chrome") -> "com.android.chrome"
            query.contains("camera") -> "com.android.camera2"
            query.contains("setting") -> Settings.ACTION_SETTINGS
            else -> null
        }

        if (aliasPkg == Settings.ACTION_SETTINGS) {
            context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return@runCatching "Opened Android Settings"
        }

        if (aliasPkg != null) {
            pm.getLaunchIntentForPackage(aliasPkg)?.let { intent ->
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return@runCatching "Launched $appNameOrPackage"
            }
        }

        // Search installed launcher apps by label
        val apps = listInstalledApps(150)
        val match = apps.firstOrNull { (label, pkg) ->
            label.lowercase() == query || label.lowercase().contains(query) || pkg.lowercase().contains(query)
        } ?: error("App '$appNameOrPackage' not found on device")

        val launchIntent = pm.getLaunchIntentForPackage(match.second)
            ?: error("No launchable activity for ${match.first}")
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(launchIntent)
        "Launched ${match.first} (${match.second})"
    }

    fun openAppSettings(appNameOrPackage: String): Result<String> = runCatching {
        val query = appNameOrPackage.trim().lowercase()
        val pkg = listInstalledApps(150).firstOrNull { (label, p) ->
            label.lowercase().contains(query) || p.lowercase() == query
        }?.second ?: appNameOrPackage.trim()

        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", pkg, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        "Opened App Info settings for $pkg"
    }

    fun toggleFlashlight(enable: Boolean? = null): Result<String> = runCatching {
        val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val cameraId = cm.cameraIdList.firstOrNull { id ->
            cm.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        } ?: error("No flashlight available on this device")

        val nextState = enable ?: !torchEnabled
        cm.setTorchMode(cameraId, nextState)
        torchEnabled = nextState
        if (nextState) "Flashlight turned ON" else "Flashlight turned OFF"
    }

    fun setMediaVolumePercent(percent: Int): Result<String> = runCatching {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val maxVol = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val target = ((percent.coerceIn(0, 100) / 100f) * maxVol).toInt()
        am.setStreamVolume(AudioManager.STREAM_MUSIC, target, AudioManager.FLAG_SHOW_UI)
        "Media volume set to $percent%"
    }

    fun toggleDnd(enable: Boolean): Result<String> = runCatching {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (!nm.isNotificationPolicyAccessGranted) {
            val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            return@runCatching "Opened Do Not Disturb policy settings for permission"
        }
        val filter = if (enable) {
            NotificationManager.INTERRUPTION_FILTER_PRIORITY
        } else {
            NotificationManager.INTERRUPTION_FILTER_ALL
        }
        nm.setInterruptionFilter(filter)
        if (enable) "Do Not Disturb enabled" else "Do Not Disturb disabled"
    }

    fun openSystemSetting(setting: String): Result<String> = runCatching {
        val action = when (setting.lowercase().trim()) {
            "wifi", "wi-fi" -> Settings.ACTION_WIFI_SETTINGS
            "bluetooth" -> Settings.ACTION_BLUETOOTH_SETTINGS
            "brightness", "display" -> Settings.ACTION_DISPLAY_SETTINGS
            "sound", "volume" -> Settings.ACTION_SOUND_SETTINGS
            "battery" -> Settings.ACTION_BATTERY_SAVER_SETTINGS
            "location" -> Settings.ACTION_LOCATION_SOURCE_SETTINGS
            else -> Settings.ACTION_SETTINGS
        }
        context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        "Opened $setting settings"
    }

    fun findContactNumber(nameOrNumber: String): String? {
        val trimmed = nameOrNumber.trim()
        if (trimmed.all { it.isDigit() || it == '+' || it == ' ' || it == '-' } && trimmed.count { it.isDigit() } >= 6) {
            return trimmed.replace(" ", "")
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return null
        }
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (cursor.moveToNext()) {
                val displayName = cursor.getString(nameIdx).orEmpty()
                val number = cursor.getString(numIdx).orEmpty()
                if (displayName.contains(trimmed, ignoreCase = true)) {
                    return number.replace(" ", "")
                }
            }
        }
        return null
    }

    fun sendSms(recipient: String, message: String): Result<String> = runCatching {
        val number = findContactNumber(recipient) ?: recipient
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED) {
            val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }
            smsManager.sendTextMessage(number, null, message, null, null)
            "SMS sent to $recipient ($number)"
        } else {
            val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$number")).apply {
                putExtra("sms_body", message)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            "Opened SMS composer for $recipient"
        }
    }

    fun makeCall(recipient: String): Result<String> = runCatching {
        val number = findContactNumber(recipient) ?: recipient
        val hasCallPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
        val action = if (hasCallPerm) Intent.ACTION_CALL else Intent.ACTION_DIAL
        val intent = Intent(action, Uri.parse("tel:$number")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        if (hasCallPerm) "Calling $recipient ($number)" else "Opened dialer for $recipient ($number)"
    }

    fun sendWhatsAppMessage(recipient: String, message: String): Result<String> = runCatching {
        val number = findContactNumber(recipient)
        if (number != null) {
            val digits = number.filter { it.isDigit() }
            val encoded = URLEncoder.encode(message, "UTF-8")
            val url = "https://wa.me/$digits?text=$encoded"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                setPackage("com.whatsapp")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            "Opened WhatsApp chat with $recipient ($number)"
        } else {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                setPackage("com.whatsapp")
                putExtra(Intent.EXTRA_TEXT, message)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            "Opened WhatsApp share for $recipient"
        }
    }
}
