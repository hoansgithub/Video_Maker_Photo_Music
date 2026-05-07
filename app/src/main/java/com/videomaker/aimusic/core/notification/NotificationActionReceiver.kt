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
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, DEFAULT_NOTIFICATION_ID)
        if (notificationId != DEFAULT_NOTIFICATION_ID) {
            NotificationInteractionTracker.markClicked(notificationId.toString())
        }
        NotificationManagerCompat.from(context).cancel(notificationId)

        val deepLink = buildDeepLink(intent)
        val activityIntent = deepLink?.let {
            NotificationDeepLinkFactory.toMainActivityIntent(context, it)
        } ?: context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

        runCatching {
            activityIntent?.let { context.startActivity(it) }
        }

        val type = intent.getStringExtra(EXTRA_NOTIFICATION_TYPE)
            ?.let { runCatching { NotificationType.valueOf(it) }.getOrNull() }
        val itemId = intent.getStringExtra(EXTRA_ITEM_ID)?.takeIf { it.isNotBlank() }
        val itemType = intent.getStringExtra(EXTRA_ITEM_TYPE)?.takeIf { it.isNotBlank() }
        if (type == null || itemId == null || itemType == null) return

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
        runCatching {
            conversionTracker.recordTap(type = type, itemId = itemId, tappedAtMs = tappedAt)
        }
    }

    companion object {
        private const val DEFAULT_NOTIFICATION_ID = -1
        private const val EXTRA_NOTIFICATION_TYPE = "extra_notification_type"
        private const val EXTRA_ITEM_ID = "extra_item_id"
        private const val EXTRA_ITEM_TYPE = "extra_item_type"
        private const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
        private const val EXTRA_CTA = "extra_cta"
        private const val EXTRA_DEEP_LINK_DESTINATION = "extra_deep_link_destination"
        private const val EXTRA_DEEP_LINK_ACTION = "extra_deep_link_action"
        private const val EXTRA_SONG_ID = "extra_song_id"
        private const val EXTRA_TEMPLATE_ID = "extra_template_id"
        private const val EXTRA_PROJECT_ID = "extra_project_id"
        private const val EXTRA_DRAFT_ID = "extra_draft_id"
        private const val EXTRA_HINT_MODE = "extra_hint_mode"

        fun buildIntent(
            context: Context,
            type: NotificationType,
            itemId: String,
            itemType: String,
            notificationId: Int,
            cta: String,
            deepLinkDestination: String,
            deepLink: NotificationDeepLink
        ): Intent {
            return Intent(context, NotificationActionReceiver::class.java).apply {
                putExtra(EXTRA_NOTIFICATION_TYPE, type.name)
                putExtra(EXTRA_ITEM_ID, itemId)
                putExtra(EXTRA_ITEM_TYPE, itemType)
                putExtra(EXTRA_NOTIFICATION_ID, notificationId)
                putExtra(EXTRA_CTA, cta)
                putExtra(EXTRA_DEEP_LINK_DESTINATION, deepLinkDestination)
                putExtra(EXTRA_DEEP_LINK_ACTION, deepLink.action)
                putExtra(EXTRA_SONG_ID, deepLink.songId)
                putExtra(EXTRA_TEMPLATE_ID, deepLink.templateId)
                putExtra(EXTRA_PROJECT_ID, deepLink.projectId)
                putExtra(EXTRA_DRAFT_ID, deepLink.draftId)
                putExtra(EXTRA_HINT_MODE, deepLink.hintMode)
            }
        }

        private fun buildDeepLink(intent: Intent): NotificationDeepLink? {
            val action = intent.getStringExtra(EXTRA_DEEP_LINK_ACTION)?.takeIf { it.isNotBlank() } ?: return null
            val destination = intent.getStringExtra(EXTRA_DEEP_LINK_DESTINATION) ?: "home"
            return NotificationDeepLink(
                action = action,
                deepLinkDestination = destination,
                songId = intent.getLongExtra(EXTRA_SONG_ID, -1L),
                templateId = intent.getStringExtra(EXTRA_TEMPLATE_ID),
                projectId = intent.getStringExtra(EXTRA_PROJECT_ID),
                draftId = intent.getStringExtra(EXTRA_DRAFT_ID),
                hintMode = intent.getStringExtra(EXTRA_HINT_MODE)
            )
        }
    }
}
