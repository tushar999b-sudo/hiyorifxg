package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.os.StatFs
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.voice.VoiceController
import com.example.voice.VoiceStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class HiyoriForegroundService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var batteryMonitorJob: Job? = null
    private var storageMonitorJob: Job? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    @Volatile
    private var lastAnnouncementAtMs: Long = 0L
    private val activeAlerts = mutableSetOf<String>()

    @Volatile
    private var lastKnownNetworkConnected: Boolean? = null

    @Volatile
    private var isHandlingCommand: Boolean = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        _isRunning.value = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_AGENT) {
            onStopRequestedCallback?.invoke()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        val statusText = intent?.getStringExtra(EXTRA_STATUS_TEXT)
            ?: NOTIFICATION_LISTENING_TEXT

        val notification = buildNotification(statusText)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        startWakeWordLoop()
        startStatusMonitors()
        return START_STICKY
    }

    private fun startWakeWordLoop() {
        val vc = voiceControllerRef ?: return
        if (!vc.wakeWordManager.refreshAvailability()) {
            vc.setVoiceStatus(VoiceStatus.OFF)
            return
        }
        vc.setVoiceStatus(VoiceStatus.LISTENING_FOR_WAKE_WORD)
        updateNotification(NOTIFICATION_LISTENING_TEXT)

        vc.wakeWordManager.startListening {
            if (isHandlingCommand) return@startListening
            isHandlingCommand = true
            serviceScope.launch {
                try {
                    vc.wakeWordManager.pause()
                    updateNotification("Listening...")
                    val transcript = vc.captureAndTranscribeCommand()
                    if (!transcript.isNullOrBlank()) {
                        updateNotification("Thinking...")
                        val handler = onVoiceCommandSuspendCallback
                        if (handler != null) {
                            handler.invoke(transcript)
                        }
                    }
                } finally {
                    isHandlingCommand = false
                    if (_isRunning.value && vc.wakeWordManager.wakeWordAvailable.value) {
                        vc.setVoiceStatus(VoiceStatus.LISTENING_FOR_WAKE_WORD)
                        updateNotification(NOTIFICATION_LISTENING_TEXT)
                        startWakeWordLoop()
                    } else {
                        vc.setVoiceStatus(VoiceStatus.OFF)
                    }
                }
            }
        }
    }

    private fun startStatusMonitors() {
        // 1. Battery & charging check every 60 seconds
        batteryMonitorJob?.cancel()
        batteryMonitorJob = serviceScope.launch(Dispatchers.IO) {
            while (isActive) {
                checkBatteryStatus()
                delay(60_000L)
            }
        }

        // 2. Storage check every 10 minutes
        storageMonitorJob?.cancel()
        storageMonitorJob = serviceScope.launch(Dispatchers.IO) {
            while (isActive) {
                checkStorageStatus()
                delay(10 * 60_000L)
            }
        }

        // 3. Network connect / disconnect callback
        registerNetworkMonitor()
    }

    private fun checkBatteryStatus() {
        val intent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
        val pct = (level * 100) / scale

        if (pct in 0..19 && !isCharging) {
            announceIfAllowed(
                alertKey = ALERT_LOW_BATTERY,
                message = "Master, battery $pct percent bachi hai aur charging nahi ho rahi."
            )
        } else if (pct >= 20 || isCharging) {
            synchronized(activeAlerts) {
                activeAlerts.remove(ALERT_LOW_BATTERY)
            }
        }
    }

    private fun checkStorageStatus() {
        runCatching {
            val stat = StatFs(Environment.getDataDirectory().path)
            val total = stat.totalBytes.coerceAtLeast(1L)
            val avail = stat.availableBytes
            val freePct = ((avail * 100L) / total).toInt()
            if (freePct < 10) {
                announceIfAllowed(
                    alertKey = ALERT_LOW_STORAGE,
                    message = "Master, phone ki storage 10 percent se kam bachi hai."
                )
            } else {
                synchronized(activeAlerts) {
                    activeAlerts.remove(ALERT_LOW_STORAGE)
                }
            }
        }
    }

    private fun registerNetworkMonitor() {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        val activeNet = cm.activeNetwork
        val caps = activeNet?.let { cm.getNetworkCapabilities(it) }
        lastKnownNetworkConnected = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true

        networkCallback?.let { runCatching { cm.unregisterNetworkCallback(it) } }
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                val prev = lastKnownNetworkConnected
                lastKnownNetworkConnected = true
                if (prev == false) {
                    synchronized(activeAlerts) {
                        activeAlerts.remove(ALERT_NETWORK_LOST)
                    }
                    announceIfAllowed(
                        alertKey = ALERT_NETWORK_RESTORED,
                        message = "Master, internet connection wapas aa gaya hai."
                    )
                }
            }

            override fun onLost(network: Network) {
                val capsNow = cm.activeNetwork?.let { cm.getNetworkCapabilities(it) }
                val stillConnected = capsNow?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
                if (!stillConnected) {
                    val prev = lastKnownNetworkConnected
                    lastKnownNetworkConnected = false
                    if (prev == true) {
                        synchronized(activeAlerts) {
                            activeAlerts.remove(ALERT_NETWORK_RESTORED)
                        }
                        announceIfAllowed(
                            alertKey = ALERT_NETWORK_LOST,
                            message = "Master, internet connection chala gaya hai."
                        )
                    }
                }
            }
        }
        networkCallback = cb
        runCatching { cm.registerDefaultNetworkCallback(cb) }
    }

    private fun announceIfAllowed(alertKey: String, message: String) {
        if (isHandlingCommand) return
        val now = System.currentTimeMillis()
        synchronized(activeAlerts) {
            if (alertKey in activeAlerts) return
            if (now - lastAnnouncementAtMs < ANNOUNCEMENT_COOLDOWN_MS) return
            activeAlerts.add(alertKey)
            lastAnnouncementAtMs = now
        }

        val vc = voiceControllerRef ?: return
        serviceScope.launch {
            if (!isHandlingCommand) {
                vc.wakeWordManager.pause()
                vc.speakAndWait(message)
                if (_isRunning.value && !isHandlingCommand) {
                    startWakeWordLoop()
                }
            }
        }
    }

    private fun updateNotification(statusText: String) {
        val mgr = getSystemService(NotificationManager::class.java)
        mgr?.notify(NOTIFICATION_ID, buildNotification(statusText))
    }

    override fun onDestroy() {
        super.onDestroy()
        _isRunning.value = false
        batteryMonitorJob?.cancel()
        storageMonitorJob?.cancel()
        networkCallback?.let { cb ->
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            runCatching { cm?.unregisterNetworkCallback(cb) }
        }
        voiceControllerRef?.wakeWordManager?.stop()
        voiceControllerRef?.setVoiceStatus(VoiceStatus.OFF)
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(statusText: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, HiyoriForegroundService::class.java).apply { action = ACTION_STOP_AGENT },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Hiyori Mobile Agent")
            .setContentText(statusText)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "STOP", stopIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Hiyori Autonomous Agent",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps Hiyori running in the background for wake-word listening"
            }
            val mgr = getSystemService(NotificationManager::class.java)
            mgr.createNotificationChannel(channel)
        }
    }

    companion object {
        private const val CHANNEL_ID = "hiyori_agent_channel"
        private const val NOTIFICATION_ID = 1042
        private const val NOTIFICATION_LISTENING_TEXT = "Hiyori is listening for 'Hiyori'"
        private const val ANNOUNCEMENT_COOLDOWN_MS = 5 * 60_000L

        private const val ALERT_LOW_BATTERY = "low_battery"
        private const val ALERT_LOW_STORAGE = "low_storage"
        private const val ALERT_NETWORK_LOST = "network_lost"
        private const val ALERT_NETWORK_RESTORED = "network_restored"

        const val ACTION_STOP_AGENT = "com.example.action.STOP_AGENT"
        const val EXTRA_STATUS_TEXT = "extra_status_text"

        var onStopRequestedCallback: (() -> Unit)? = null
        var onVoiceCommandSuspendCallback: (suspend (String) -> Unit)? = null
        var voiceControllerRef: VoiceController? = null

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

        fun start(context: Context, statusText: String = NOTIFICATION_LISTENING_TEXT) {
            val intent = Intent(context, HiyoriForegroundService::class.java).apply {
                putExtra(EXTRA_STATUS_TEXT, statusText)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, HiyoriForegroundService::class.java))
        }
    }
}
