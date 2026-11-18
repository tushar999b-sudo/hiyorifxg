package com.example.service

import android.app.Notification
import android.app.RemoteInput
import android.content.Intent
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class CapturedNotification(
    val key: String,
    val packageName: String,
    val title: String,
    val text: String,
    val timestamp: Long,
    val canReply: Boolean
)

class HiyoriNotificationService : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
        _isConnected.value = true
        refreshActiveNotifications()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        if (instance === this) instance = null
        _isConnected.value = false
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        refreshActiveNotifications()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        refreshActiveNotifications()
    }

    fun refreshActiveNotifications() {
        runCatching {
            val list = activeNotifications?.mapNotNull { sbn ->
                val extras = sbn.notification?.extras ?: return@mapNotNull null
                val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
                val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
                if (title.isBlank() && text.isBlank()) return@mapNotNull null
                val hasReply = findReplyAction(sbn.notification) != null
                CapturedNotification(
                    key = sbn.key,
                    packageName = sbn.packageName,
                    title = title,
                    text = text,
                    timestamp = sbn.postTime,
                    canReply = hasReply
                )
            }?.sortedByDescending { it.timestamp } ?: emptyList()
            _notifications.value = list
        }
    }

    fun replyToNotification(keyOrQuery: String, replyText: String): Boolean {
        val active = activeNotifications ?: return false
        val sbn = active.find { it.key == keyOrQuery }
            ?: active.find { item ->
                val extras = item.notification?.extras
                val title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
                title.contains(keyOrQuery, ignoreCase = true) ||
                    item.packageName.contains(keyOrQuery, ignoreCase = true)
            }
            ?: active.firstOrNull { findReplyAction(it.notification) != null }
            ?: return false

        val replyAction = findReplyAction(sbn.notification) ?: return false
        val remoteInputs = replyAction.remoteInputs ?: return false
        val intent = Intent()
        val bundle = Bundle()
        for (input in remoteInputs) {
            bundle.putCharSequence(input.resultKey, replyText)
        }
        RemoteInput.addResultsToIntent(remoteInputs, intent, bundle)
        return runCatching {
            replyAction.actionIntent.send(this, 0, intent)
            true
        }.getOrDefault(false)
    }

    private fun findReplyAction(notification: Notification?): Notification.Action? {
        val actions = notification?.actions ?: return null
        return actions.firstOrNull { action ->
            action.remoteInputs != null && action.remoteInputs.isNotEmpty()
        }
    }

    companion object {
        @Volatile
        var instance: HiyoriNotificationService? = null
            private set

        private val _isConnected = MutableStateFlow(false)
        val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

        private val _notifications = MutableStateFlow<List<CapturedNotification>>(emptyList())
        val notifications: StateFlow<List<CapturedNotification>> = _notifications.asStateFlow()
    }
}
