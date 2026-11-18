package com.example.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ScreenElementNode(
    val index: Int,
    val text: String,
    val contentDescription: String,
    val viewId: String,
    val className: String,
    val clickable: Boolean,
    val editable: Boolean,
    val scrollable: Boolean,
    val bounds: Rect
)

class HiyoriAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        _isConnected.value = true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event?.packageName?.toString()?.let { pkg ->
            if (pkg.isNotBlank()) {
                _currentPackage.value = pkg
            }
        }
    }

    override fun onInterrupt() {
        // Service interrupted by system
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance === this) {
            instance = null
        }
        _isConnected.value = false
    }

    fun captureScreenElements(): List<ScreenElementNode> {
        val root = rootInActiveWindow ?: return emptyList()
        val results = mutableListOf<ScreenElementNode>()
        traverseNode(root, results)
        return results
    }

    fun getScreenTreeSummary(maxItems: Int = 45): String {
        val elements = captureScreenElements()
        if (elements.isEmpty()) {
            return "Accessibility node tree is empty or unavailable."
        }
        val pkg = _currentPackage.value.ifBlank { "unknown" }
        val sb = StringBuilder("Active App: $pkg | Visible Elements (${elements.size}):\n")
        elements.take(maxItems).forEach { el ->
            val label = when {
                el.text.isNotBlank() -> "\"${el.text}\""
                el.contentDescription.isNotBlank() -> "[desc: \"${el.contentDescription}\"]"
                el.viewId.isNotBlank() -> "[id: ${el.viewId.substringAfterLast('/')}]"
                else -> "[${el.className.substringAfterLast('.')}]"
            }
            val flags = buildList {
                if (el.clickable) add("click")
                if (el.editable) add("edit")
                if (el.scrollable) add("scroll")
            }.joinToString(",")
            sb.append("#${el.index} $label ($flags) @(${el.bounds.centerX()},${el.bounds.centerY()})\n")
        }
        return sb.toString().trim()
    }

    fun clickElement(query: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val target = findNodeByQuery(root, query) ?: return false
        var current: AccessibilityNodeInfo? = target
        while (current != null) {
            if (current.isClickable) {
                return current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
            current = current.parent
        }
        // Fallback: tap center coordinates via gesture
        val rect = Rect()
        target.getBoundsInScreen(rect)
        return tapAt(rect.centerX().toFloat(), rect.centerY().toFloat())
    }

    fun longPressElement(query: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val target = findNodeByQuery(root, query) ?: return false
        var current: AccessibilityNodeInfo? = target
        while (current != null) {
            if (current.isLongClickable) {
                return current.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)
            }
            current = current.parent
        }
        return false
    }

    fun typeText(query: String?, text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val target = if (!query.isNullOrBlank()) {
            findEditableNodeByQuery(root, query) ?: findNodeByQuery(root, query)
        } else {
            root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: findFirstEditableNode(root)
        } ?: return false

        target.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    fun scrollScreen(direction: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val scrollable = findFirstScrollableNode(root)
        val dir = direction.lowercase()
        if (scrollable != null) {
            val action = if (dir == "up" || dir == "left") {
                AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
            } else {
                AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
            }
            if (scrollable.performAction(action)) return true
        }
        // Gesture fallback
        return when (dir) {
            "up" -> swipe(540f, 700f, 540f, 1600f, 320L)
            "left" -> swipe(200f, 1200f, 900f, 1200f, 300L)
            "right" -> swipe(900f, 1200f, 200f, 1200f, 300L)
            else -> swipe(540f, 1650f, 540f, 700f, 320L)
        }
    }

    fun tapAt(x: Float, y: Float): Boolean {
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, 60L))
            .build()
        return dispatchGesture(gesture, null, null)
    }

    fun swipe(startX: Float, startY: Float, endX: Float, endY: Float, durationMs: Long = 320L): Boolean {
        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, durationMs.coerceAtLeast(50L)))
            .build()
        return dispatchGesture(gesture, null, null)
    }

    fun performGlobal(actionName: String): Boolean {
        val action = when (actionName.lowercase().trim()) {
            "back" -> GLOBAL_ACTION_BACK
            "home" -> GLOBAL_ACTION_HOME
            "recents", "recent", "overview" -> GLOBAL_ACTION_RECENTS
            "notifications", "notification" -> GLOBAL_ACTION_NOTIFICATIONS
            "quick_settings", "quicksettings" -> GLOBAL_ACTION_QUICK_SETTINGS
            "screenshot" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) GLOBAL_ACTION_TAKE_SCREENSHOT else return false
            else -> return false
        }
        return performGlobalAction(action)
    }

    private fun traverseNode(node: AccessibilityNodeInfo, out: MutableList<ScreenElementNode>) {
        if (!node.isVisibleToUser) return
        val text = node.text?.toString()?.trim().orEmpty()
        val desc = node.contentDescription?.toString()?.trim().orEmpty()
        val viewId = node.viewIdResourceName?.trim().orEmpty()
        val cls = node.className?.toString().orEmpty()
        val rect = Rect()
        node.getBoundsInScreen(rect)

        if (text.isNotEmpty() || desc.isNotEmpty() || node.isClickable || node.isEditable) {
            out.add(
                ScreenElementNode(
                    index = out.size,
                    text = text,
                    contentDescription = desc,
                    viewId = viewId,
                    className = cls,
                    clickable = node.isClickable,
                    editable = node.isEditable,
                    scrollable = node.isScrollable,
                    bounds = rect
                )
            )
        }

        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                traverseNode(child, out)
            }
        }
    }

    private fun findNodeByQuery(node: AccessibilityNodeInfo, query: String): AccessibilityNodeInfo? {
        if (!node.isVisibleToUser) return null
        val q = query.trim().lowercase()
        val text = node.text?.toString()?.lowercase().orEmpty()
        val desc = node.contentDescription?.toString()?.lowercase().orEmpty()
        val id = node.viewIdResourceName?.lowercase().orEmpty()

        if (text.contains(q) || desc.contains(q) || id.endsWith(q)) {
            return node
        }
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                findNodeByQuery(child, query)?.let { return it }
            }
        }
        return null
    }

    private fun findEditableNodeByQuery(node: AccessibilityNodeInfo, query: String): AccessibilityNodeInfo? {
        if (!node.isVisibleToUser) return null
        val q = query.trim().lowercase()
        val text = node.text?.toString()?.lowercase().orEmpty()
        val desc = node.contentDescription?.toString()?.lowercase().orEmpty()
        val id = node.viewIdResourceName?.lowercase().orEmpty()
        val hint = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            node.hintText?.toString()?.lowercase().orEmpty()
        } else ""

        if (node.isEditable && (text.contains(q) || desc.contains(q) || hint.contains(q) || id.contains(q))) {
            return node
        }
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                findEditableNodeByQuery(child, query)?.let { return it }
            }
        }
        return null
    }

    private fun findFirstEditableNode(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (!node.isVisibleToUser) return null
        if (node.isEditable) return node
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                findFirstEditableNode(child)?.let { return it }
            }
        }
        return null
    }

    private fun findFirstScrollableNode(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (!node.isVisibleToUser) return null
        if (node.isScrollable) return node
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                findFirstScrollableNode(child)?.let { return it }
            }
        }
        return null
    }

    companion object {
        @Volatile
        var instance: HiyoriAccessibilityService? = null
            private set

        private val _isConnected = MutableStateFlow(false)
        val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

        private val _currentPackage = MutableStateFlow("")
        val currentPackage: StateFlow<String> = _currentPackage.asStateFlow()
    }
}
