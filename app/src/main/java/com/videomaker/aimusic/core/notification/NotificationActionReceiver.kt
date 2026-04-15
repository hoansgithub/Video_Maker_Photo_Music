package com.videomaker.aimusic.core.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.videomaker.aimusic.core.analytics.Analytics
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class NotificationActionReceiver : BroadcastReceiver(), KoinComponent {

    private val conversionTracker: NotificationConversionTracker by inject()

    override fun onReceive(context: Context, intent: Intent) {
        val type = intent.getStringExtra(EXTRA_NOTIFICATION_TYPE)
            ?.let { runCatching { NotificationType.valueOf(it) }.getOrNull() }
            ?: return
        val itemId = intent.getStringExtra(EXTRA_ITEM_ID)?.takeIf { it.isNotBlank() } ?: return
        val itemType = intent.getStringExtra(EXTRA_ITEM_TYPE)?.takeIf { it.isNotBlank() } ?: return

        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, DEFAULT_NOTIFICATION_ID)
        if (notificationId != DEFAULT_NOTIFICATION_ID) {
            NotificationInteractionTracker.markClicked(notificationId.toString())
        }
        NotificationManagerCompat.from(context).cancel(notificationId)

        val cta = intent.getStringExtra(EXTRA_CTA) ?: "open_notification"
        val deepLinkDestination = intent.getStringExtra(EXTRA_DEEP_LINK_DESTINATION) ?: "home"
        val tappedAt = System.currentTimeMillis()
        Analytics.trackNotificationClick(
            type = type.analyticsValue,
            itemId = itemId,
            itemType = itemType,
            cta = cta,
            deepLinkDestination = deepLinkDestination,
            tappedAt = tappedAt
        )
        conversionTracker.recordTap(type = type, itemId = itemId, tappedAtMs = tappedAt)

        val activityIntent = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_DEEP_LINK_INTENT, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_DEEP_LINK_INTENT)
        } ?: return
        context.startActivity(activityIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    companion object {
        private const val DEFAULT_NOTIFICATION_ID = -1
        private const val EXTRA_NOTIFICATION_TYPE = "extra_notification_type"
        private const val EXTRA_ITEM_ID = "extra_item_id"
        private const val EXTRA_ITEM_TYPE = "extra_item_type"
        private const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
        private const val EXTRA_CTA = "extra_cta"
        private const val EXTRA_DEEP_LINK_DESTINATION = "extra_deep_link_destination"
        private const val EXTRA_DEEP_LINK_INTENT = "extra_deep_link_intent"

        fun buildIntent(
            context: Context,
            type: NotificationType,
            itemId: String,
            itemType: String,
            notificationId: Int,
            cta: String,
            deepLinkDestination: String,
            deepLinkIntent: Intent
        ): Intent {
            return Intent(context, NotificationActionReceiver::class.java).apply {
                putExtra(EXTRA_NOTIFICATION_TYPE, type.name)
                putExtra(EXTRA_ITEM_ID, itemId)
                putExtra(EXTRA_ITEM_TYPE, itemType)
                putExtra(EXTRA_NOTIFICATION_ID, notificationId)
                putExtra(EXTRA_CTA, cta)
                putExtra(EXTRA_DEEP_LINK_DESTINATION, deepLinkDestination)
                putExtra(EXTRA_DEEP_LINK_INTENT, deepLinkIntent)
            }
        }
    }
}
