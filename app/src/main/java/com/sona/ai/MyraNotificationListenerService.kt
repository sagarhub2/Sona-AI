package com.sona.ai

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class MyraNotificationListenerService : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty()
        if (title.isBlank() && text.isBlank()) return
        val prefs = getSharedPreferences("myra_notifications", MODE_PRIVATE)
        val item = "${sbn.packageName}: $title. $text".take(500)
        val old = prefs.getString("recent", "").orEmpty()
        prefs.edit().putString("recent", (item + "\n" + old).lines().distinct().take(10).joinToString("\n")).apply()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) = Unit
}
