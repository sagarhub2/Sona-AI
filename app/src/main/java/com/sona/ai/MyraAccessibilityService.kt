package com.sona.ai

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

/**
 * User-enabled accessibility bridge for explicit, local navigation commands.
 * This service intentionally does not inspect screen text, click arbitrary controls,
 * enter text, or perform sensitive actions.
 */
class MyraAccessibilityService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Screen contents are not read or retained.
    }

    override fun onInterrupt() = Unit

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_BACK -> performGlobalAction(GLOBAL_ACTION_BACK)
            ACTION_HOME -> performGlobalAction(GLOBAL_ACTION_HOME)
            ACTION_NOTIFICATIONS -> performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
            ACTION_QUICK_SETTINGS -> performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)
            ACTION_RECENTS -> performGlobalAction(GLOBAL_ACTION_RECENTS)
        }
        stopSelf(startId)
        return START_NOT_STICKY
    }

    companion object {
        const val ACTION_BACK = "com.sona.ai.ACCESSIBILITY_BACK"
        const val ACTION_HOME = "com.sona.ai.ACCESSIBILITY_HOME"
        const val ACTION_NOTIFICATIONS = "com.sona.ai.ACCESSIBILITY_NOTIFICATIONS"
        const val ACTION_QUICK_SETTINGS = "com.sona.ai.ACCESSIBILITY_QUICK_SETTINGS"
        const val ACTION_RECENTS = "com.sona.ai.ACCESSIBILITY_RECENTS"
    }
}
