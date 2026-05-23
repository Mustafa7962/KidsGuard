package com.kidsguard.app

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Accessibility service that watches YouTube Kids UI.
 *
 * The primary pause/resume method is the media key dispatch in FaceWatchService.
 * This service acts as a reliable fallback — it finds and clicks the
 * pause/play button directly in the YouTube Kids UI when needed.
 *
 * It also taps the center of the screen to reveal the player controls
 * before clicking play/pause, since YouTube Kids hides controls after a few seconds.
 */
class YTKidsAccessibilityService : AccessibilityService() {

    companion object {
        var instance: YTKidsAccessibilityService? = null
        private const val YT_KIDS_PACKAGE = "com.google.android.apps.youtube.kids"
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // We don't need to react to events — FaceWatchService calls us directly
    }

    override fun onInterrupt() {}

    // ──────────────────────────────────────────
    // Public API called by FaceWatchService
    // ──────────────────────────────────────────

    /**
     * Tap center of screen to show controls, then find and click pause.
     */
    fun tapPause() {
        tapScreenCenter()
        Thread.sleep(400)
        clickButtonWithDescription("Pause")
            ?: clickButtonWithDescription("pause")
            ?: tapScreenCenter() // second tap to toggle if button not found
    }

    fun tapPlay() {
        tapScreenCenter()
        Thread.sleep(400)
        clickButtonWithDescription("Play")
            ?: clickButtonWithDescription("play")
            ?: tapScreenCenter()
    }

    // ──────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────

    private fun clickButtonWithDescription(desc: String): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        val nodes = root.findAccessibilityNodeInfosByText(desc)
        for (node in nodes) {
            if (node.isClickable) {
                node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                return node
            }
            // Try parent
            val parent = node.parent
            if (parent != null && parent.isClickable) {
                parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                return parent
            }
        }
        return null
    }

    private fun tapScreenCenter() {
        val display = resources.displayMetrics
        val cx = display.widthPixels  / 2f
        val cy = display.heightPixels / 2f

        val path = Path().apply { moveTo(cx, cy) }
        val stroke = GestureDescription.StrokeDescription(path, 0, 100)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, null, null)
    }
}
