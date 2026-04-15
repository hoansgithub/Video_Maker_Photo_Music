package com.videomaker.aimusic.core.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.videomaker.aimusic.core.analytics.Analytics

class NotificationDismissReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val type = intent.getStringExtra(EXTRA_NOTIFICATION_TYPE)
            ?.let { runCatching { NotificationType.valueOf(it) }.getOrNull() }
            ?: return
        val itemId = intent.getStringExtra(EXTRA_ITEM_ID)?.takeIf { it.isNotBlank() } ?: return
        val itemType = intent.getStringExtra(EXTRA_ITEM_TYPE)?.takeIf { it.isNotBlank() } ?: return
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, DEFAULT_NOTIFICATION_ID)
        if (notificationId != DEFAULT_NOTIFICATION_ID) {
            val wasClicked = NotificationInteractionTracker.consumeIfClicked(notificationId.toString())
            if (wasClicked) return
        }

        Analytics.trackNotificationDismiss(
            type = type.analyticsValue,
            itemId = itemId,
            itemType = itemType
        )
    }

    companion object {
        private const val EXTRA_NOTIFICATION_TYPE = "extra_notification_type"
        private const val EXTRA_ITEM_ID = "extra_item_id"
        private const val EXTRA_ITEM_TYPE = "extra_item_type"
        private const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
        private const val DEFAULT_NOTIFICATION_ID = -1

        fun buildIntent(
            context: Context,
            type: NotificationType,
            itemId: String,
            itemType: String,
            notificationId: Int
        ): Intent {
            return Intent(context, NotificationDismissReceiver::class.java).apply {
                putExtra(EXTRA_NOTIFICATION_TYPE, type.name)
                putExtra(EXTRA_ITEM_ID, itemId)
                putExtra(EXTRA_ITEM_TYPE, itemType)
                putExtra(EXTRA_NOTIFICATION_ID, notificationId)
            }
        }
    }
}
