package com.example.shizuku

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import rikka.shizuku.Shizuku

enum class ShizukuStatus {
    NOT_INSTALLED,
    NOT_RUNNING,
    RUNNING_NO_PERMISSION,
    READY,
    PERMISSION_DENIED_PERMANENTLY,
    VERSION_TOO_OLD
}

object ShizukuManager {

    private const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
    private const val MIN_SHIZUKU_VERSION = 11
    private const val DEFAULT_REQUEST_CODE = 1001

    private val _status = MutableStateFlow(ShizukuStatus.NOT_INSTALLED)
    val status: StateFlow<ShizukuStatus> = _status.asStateFlow()

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var listenersRegistered = false

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        refresh()
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        refresh()
    }

    private val permissionResultListener =
        Shizuku.OnRequestPermissionResultListener { _, grantResult ->
            if (grantResult == PackageManager.PERMISSION_GRANTED) {
                _status.value = ShizukuStatus.READY
            } else {
                refresh()
            }
        }

    fun init(context: Context) {
        appContext = context.applicationContext
        if (!listenersRegistered) {
            Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
            Shizuku.addBinderDeadListener(binderDeadListener)
            Shizuku.addRequestPermissionResultListener(permissionResultListener)
            listenersRegistered = true
        }
        refresh()
    }

    fun destroy() {
        if (listenersRegistered) {
            Shizuku.removeBinderReceivedListener(binderReceivedListener)
            Shizuku.removeBinderDeadListener(binderDeadListener)
            Shizuku.removeRequestPermissionResultListener(permissionResultListener)
            listenersRegistered = false
        }
    }

    fun refresh() {
        val ctx = appContext ?: return
        if (!isShizukuInstalled(ctx)) {
            _status.value = ShizukuStatus.NOT_INSTALLED
            return
        }

        if (!Shizuku.pingBinder()) {
            _status.value = ShizukuStatus.NOT_RUNNING
            return
        }

        val versionOk = runCatching {
            !Shizuku.isPreV11() && Shizuku.getVersion() >= MIN_SHIZUKU_VERSION
        }.getOrDefault(false)

        if (!versionOk) {
            _status.value = ShizukuStatus.VERSION_TOO_OLD
            return
        }

        val granted = runCatching {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)

        if (granted) {
            _status.value = ShizukuStatus.READY
            return
        }

        val deniedPermanently = runCatching {
            Shizuku.shouldShowRequestPermissionRationale()
        }.getOrDefault(false)

        _status.value = if (deniedPermanently) {
            ShizukuStatus.PERMISSION_DENIED_PERMANENTLY
        } else {
            ShizukuStatus.RUNNING_NO_PERMISSION
        }
    }

    fun requestPermission(requestCode: Int = DEFAULT_REQUEST_CODE) {
        refresh()
        if (!Shizuku.pingBinder()) return
        if (Shizuku.isPreV11()) {
            _status.value = ShizukuStatus.VERSION_TOO_OLD
            return
        }
        if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            _status.value = ShizukuStatus.READY
            return
        }
        Shizuku.requestPermission(requestCode)
    }

    fun isReady(): Boolean = _status.value == ShizukuStatus.READY

    fun openShizukuApp(context: Context): Boolean {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(SHIZUKU_PACKAGE)
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return runCatching {
                context.startActivity(launchIntent)
                true
            }.getOrDefault(false)
        }
        return openInstallShizuku(context)
    }

    fun openInstallShizuku(context: Context): Boolean {
        val marketIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("market://details?id=$SHIZUKU_PACKAGE")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        if (runCatching { context.startActivity(marketIntent); true }.getOrDefault(false)) {
            return true
        }

        val githubIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://github.com/RikkaApps/Shizuku/releases")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        return runCatching {
            context.startActivity(githubIntent)
            true
        }.getOrDefault(false)
    }

    private fun isShizukuInstalled(context: Context): Boolean {
        return runCatching {
            context.packageManager.getPackageInfo(SHIZUKU_PACKAGE, 0)
            true
        }.getOrDefault(false)
    }
}
