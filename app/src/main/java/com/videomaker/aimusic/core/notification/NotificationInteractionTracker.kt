package com.videomaker.aimusic.core.notification

object NotificationInteractionTracker {
    private val clickedNotificationIds = mutableSetOf<String>()

    fun markClicked(notificationId: String) {
        if (notificationId.isBlank()) return
        synchronized(clickedNotificationIds) {
            clickedNotificationIds.add(notificationId)
        }
    }

    fun consumeIfClicked(notificationId: String): Boolean {
        if (notificationId.isBlank()) return false
        synchronized(clickedNotificationIds) {
            return clickedNotificationIds.remove(notificationId)
        }
    }
}
