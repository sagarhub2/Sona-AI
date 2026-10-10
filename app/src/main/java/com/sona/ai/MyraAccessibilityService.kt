package com.sona.ai

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.view.accessibility.AccessibilityEvent

/**
 * User-enabled bridge for explicit navigation commands only.
 * It does not inspect screen text, click arbitrary controls, or enter text.
 */
class MyraAccessibilityService : AccessibilityService() {
    private val actionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_BACK -> performGlobalAction(GLOBAL_ACTION_BACK)
                ACTION_HOME -> performGlobalAction(GLOBAL_ACTION_HOME)
                ACTION_NOTIFICATIONS -> performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
                ACTION_QUICK_SETTINGS -> performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)
                ACTION_RECENTS -> performGlobalAction(GLOBAL_ACTION_RECENTS)
            }
        }
    }
    private var receiverRegistered = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        val filter = IntentFilter().apply {
            addAction(ACTION_BACK)
            addAction(ACTION_HOME)
            addAction(ACTION_NOTIFICATIONS)
            addAction(ACTION_QUICK_SETTINGS)
            addAction(ACTION_RECENTS)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(actionReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(actionReceiver, filter)
        }
        receiverRegistered = true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Screen contents are deliberately not read or retained.
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (receiverRegistered) {
            try { unregisterReceiver(actionReceiver) } catch (_: Exception) { }
            receiverRegistered = false
        }
        super.onDestroy()
    }

    companion object {
        const val ACTION_BACK = "com.sona.ai.ACCESSIBILITY_BACK"
        const val ACTION_HOME = "com.sona.ai.ACCESSIBILITY_HOME"
        const val ACTION_NOTIFICATIONS = "com.sona.ai.ACCESSIBILITY_NOTIFICATIONS"
        const val ACTION_QUICK_SETTINGS = "com.sona.ai.ACCESSIBILITY_QUICK_SETTINGS"
        const val ACTION_RECENTS = "com.sona.ai.ACCESSIBILITY_RECENTS"
    }
}
