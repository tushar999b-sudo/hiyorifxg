package com.example.shizuku

import java.net.URLEncoder

object CommandBuilder {

    private val E164_PHONE_REGEX = Regex("^\\+[1-9][0-9]{7,14}$")
    private val PACKAGE_NAME_REGEX = Regex("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+$")
    private val SAFE_SCREENSHOT_PATH_REGEX = Regex("^/sdcard/Pictures/Hiyori/[a-zA-Z0-9_./-]+\\.png$")

    private val STREAM_MAP = mapOf(
        "music" to 3,
        "media" to 3,
        "ring" to 2,
        "alarm" to 4,
        "notification" to 5,
        "call" to 0,
        "voice" to 0
    )

    fun dialNumber(phone: String): Result<String> {
        val trimmed = phone.trim()
        if (!E164_PHONE_REGEX.matches(trimmed)) {
            return Result.failure(IllegalArgumentException("Invalid E.164 phone number (expected +<country><digits>, e.g. +919876543210)."))
        }
        return Result.success("am start -a android.intent.action.DIAL -d \"tel:$trimmed\"")
    }

    fun callNumber(phone: String): Result<String> {
        val trimmed = phone.trim()
        if (!E164_PHONE_REGEX.matches(trimmed)) {
            return Result.failure(IllegalArgumentException("Invalid E.164 phone number (expected +<country><digits>, e.g. +919876543210)."))
        }
        return Result.success("am start -a android.intent.action.CALL -d \"tel:$trimmed\"")
    }

    fun openUrl(url: String): Result<String> {
        val trimmed = url.trim()
        if (!trimmed.startsWith("https://") || trimmed.length <= 8) {
            return Result.failure(IllegalArgumentException("URL must start with https://"))
        }
        if (trimmed.any { it.isWhitespace() || it.isISOControl() }) {
            return Result.failure(IllegalArgumentException("URL must not contain whitespace or control characters."))
        }
        val escaped = trimmed
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("$", "\\$")
            .replace("`", "\\`")
        return Result.success("am start -a android.intent.action.VIEW -d \"$escaped\"")
    }

    fun openApp(packageName: String): Result<String> {
        val trimmed = packageName.trim()
        if (!PACKAGE_NAME_REGEX.matches(trimmed)) {
            return Result.failure(IllegalArgumentException("Invalid Android package name '$trimmed'."))
        }
        return Result.success("monkey -p $trimmed -c android.intent.category.LAUNCHER 1")
    }

    fun whatsappSend(phone: String, text: String): Result<String> {
        val trimmedPhone = phone.trim()
        if (!E164_PHONE_REGEX.matches(trimmedPhone)) {
            return Result.failure(IllegalArgumentException("Invalid E.164 phone number (expected +<country><digits>)."))
        }
        if (text.isBlank()) {
            return Result.failure(IllegalArgumentException("WhatsApp message text cannot be empty."))
        }
        val digits = trimmedPhone.removePrefix("+")
        val encoded = URLEncoder.encode(text, "UTF-8")
        return Result.success("am start -a android.intent.action.VIEW -d \"https://wa.me/$digits?text=$encoded\"")
    }

    fun toggleWifi(on: Boolean): Result<String> {
        return Result.success(if (on) "svc wifi enable" else "svc wifi disable")
    }

    fun toggleBluetooth(on: Boolean): Result<String> {
        return Result.success(if (on) "svc bluetooth enable" else "svc bluetooth disable")
    }

    fun setBrightness(level: Int): Result<String> {
        if (level !in 0..255) {
            return Result.failure(IllegalArgumentException("Brightness level must be between 0 and 255 (got $level)."))
        }
        return Result.success("settings put system screen_brightness $level")
    }

    fun setVolume(stream: String, level: Int): Result<String> {
        val key = stream.trim().lowercase()
        val streamId = STREAM_MAP[key]
            ?: return Result.failure(IllegalArgumentException("Unsupported audio stream '$stream'. Allowed: ${STREAM_MAP.keys.joinToString()}."))
        if (level !in 0..15) {
            return Result.failure(IllegalArgumentException("Volume level must be between 0 and 15 (got $level)."))
        }
        return Result.success("cmd media_session volume --show --stream $streamId --set $level")
    }

    fun screenshot(path: String): Result<String> {
        val trimmed = path.trim()
        if (trimmed.contains("..") || !SAFE_SCREENSHOT_PATH_REGEX.matches(trimmed)) {
            return Result.failure(IllegalArgumentException("Screenshot path must be inside /sdcard/Pictures/Hiyori/ and end with .png without '..' or special characters."))
        }
        return Result.success("screencap -p $trimmed")
    }
}
