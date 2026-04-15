package com.videomaker.aimusic.core.notification.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.videomaker.aimusic.R
import com.videomaker.aimusic.core.analytics.Analytics
import com.videomaker.aimusic.core.data.local.PreferencesManager
import com.videomaker.aimusic.core.notification.NotificationCapPolicy
import com.videomaker.aimusic.core.notification.NotificationChannels
import com.videomaker.aimusic.core.notification.NotificationDeepLinkFactory
import com.videomaker.aimusic.core.notification.NotificationPayload
import com.videomaker.aimusic.core.notification.NotificationRenderer
import com.videomaker.aimusic.core.notification.NotificationScheduler
import com.videomaker.aimusic.core.notification.NotificationType
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class AbandonedSelectPhotosWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params), KoinComponent {

    private val preferencesManager: PreferencesManager by inject()
    private val notificationCapPolicy: NotificationCapPolicy by inject()
    private val notificationRenderer: NotificationRenderer by inject()

    override suspend fun doWork(): Result {
        if (!isNotificationAllowed(applicationContext)) return Result.success()
        val draftId = inputData.getString(NotificationScheduler.KEY_DRAFT_ID)?.takeIf { it.isNotBlank() }
            ?: return Result.success()
        val mode = inputData.getString(NotificationScheduler.KEY_ABANDON_MODE) ?: NotificationScheduler.ABANDON_MODE_SAME
        val exitSessionId = inputData.getLong(NotificationScheduler.KEY_EXIT_SESSION_ID, -1L).coerceAtLeast(0L)
        val state = preferencesManager.getDraftReminderState(draftId) ?: return Result.success()
        val nowMs = System.currentTimeMillis()

        if (state.selectedPhotoCount > 0) return Result.success()
        if (state.templateId.isNullOrBlank() && state.songId == null) return Result.success()

        val currentSessionId = preferencesManager.getAppSessionId()
        val sessionValid = when (mode) {
            NotificationScheduler.ABANDON_MODE_SAME -> currentSessionId == exitSessionId
            NotificationScheduler.ABANDON_MODE_COLD -> currentSessionId != exitSessionId
            else -> false
        }
        if (!sessionValid) return Result.success()

        val expectedDelayMs = if (mode == NotificationScheduler.ABANDON_MODE_SAME) {
            NotificationScheduler.ABANDONED_SAME_SESSION_DELAY_MS
        } else {
            NotificationScheduler.ABANDONED_COLD_SESSION_DELAY_MS
        }
        if ((nowMs - state.exitedAtMs) < expectedDelayMs) return Result.success()

        val gateDecision = notificationCapPolicy.evaluate(
            NotificationCapPolicy.Input(
                nowMs = nowMs,
                dailyShownCount = preferencesManager.getNotificationDailyShownCount(nowMs),
                typeDailyShownCount = preferencesManager.getNotificationTypeDailyShownCount(
                    NotificationType.ABANDONED_SELECT_PHOTOS.name,
                    nowMs
                ),
                lastShownAtMs = preferencesManager.getNotificationLastShownAtMs(),
                sameItemLastShownAtMs = preferencesManager.getNotificationItemLastShownAtMs(
                    notificationType = NotificationType.ABANDONED_SELECT_PHOTOS.name,
                    itemId = draftId
                )
            )
        )
        if (!gateDecision.allowed) return Result.success()

        Analytics.trackNotificationEligible(
            type = NotificationType.ABANDONED_SELECT_PHOTOS.analyticsValue,
            itemId = draftId,
            itemType = "draft",
            sourceTrigger = if (mode == NotificationScheduler.ABANDON_MODE_SAME) {
                "select_photos_exit_same_session_2m"
            } else {
                "select_photos_exit_cold_15m"
            },
            deepLinkDestination = "select_photos",
            copyVariant = "beat_hanging_v1",
            imageType = "template_key_art",
            sessionType = mode,
            delayMinutes = if (mode == NotificationScheduler.ABANDON_MODE_SAME) 2 else 15
        )

        val shown = notificationRenderer.show(
            NotificationPayload(
                type = NotificationType.ABANDONED_SELECT_PHOTOS,
                itemId = draftId,
                itemType = "draft",
                channelId = NotificationChannels.CHANNEL_CREATION_REMINDERS,
                title = "Don't leave the beat hanging!",
                body = "Your 'Not Like Us' edit is almost ready. Just pick a few photos to see the magic happen!",
                ctaText = "View Template",
                deepLink = NotificationDeepLinkFactory.resumeTemplate(
                    templateId = state.templateId,
                    songId = state.songId ?: -1L,
                    draftId = draftId
                ),
                imageCandidates = emptyList(),
                fallbackImageRes = R.drawable.img_template1
            )
        )
        if (!shown) return Result.success()

        preferencesManager.recordNotificationShown(
            notificationType = NotificationType.ABANDONED_SELECT_PHOTOS.name,
            itemId = draftId,
            shownAtMs = nowMs
        )
        preferencesManager.markDraftAbandonedShown(draftId, nowMs)
        Analytics.trackNotificationShown(
            type = NotificationType.ABANDONED_SELECT_PHOTOS.analyticsValue,
            itemId = draftId,
            itemType = "draft",
            sourceTrigger = if (mode == NotificationScheduler.ABANDON_MODE_SAME) {
                "select_photos_exit_same_session_2m"
            } else {
                "select_photos_exit_cold_15m"
            },
            deepLinkDestination = "select_photos",
            copyVariant = "beat_hanging_v1",
            imageType = "template_key_art",
            shownAt = nowMs
        )
        return Result.success()
    }
}

