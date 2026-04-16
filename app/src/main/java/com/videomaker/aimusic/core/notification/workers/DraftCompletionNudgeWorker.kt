package com.videomaker.aimusic.core.notification.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.videomaker.aimusic.R
import com.videomaker.aimusic.core.analytics.Analytics
import com.videomaker.aimusic.core.data.local.PreferencesManager
import com.videomaker.aimusic.core.notification.NotificationCapPolicy
import com.videomaker.aimusic.core.notification.NotificationChannels
import com.videomaker.aimusic.core.notification.NotificationScheduleConfigService
import com.videomaker.aimusic.core.notification.NotificationDeepLinkFactory
import com.videomaker.aimusic.core.notification.hasReachedDelay
import com.videomaker.aimusic.core.notification.shouldSkipDraftNudgeDueToRecentAbandoned
import com.videomaker.aimusic.core.notification.NotificationPayload
import com.videomaker.aimusic.core.notification.NotificationRenderer
import com.videomaker.aimusic.core.notification.NotificationScheduler
import com.videomaker.aimusic.core.notification.NotificationText
import com.videomaker.aimusic.core.notification.NotificationType
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class DraftCompletionNudgeWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params), KoinComponent {

    private val preferencesManager: PreferencesManager by inject()
    private val notificationCapPolicy: NotificationCapPolicy by inject()
    private val notificationRenderer: NotificationRenderer by inject()
    private val notificationScheduleConfigService: NotificationScheduleConfigService by inject()

    override suspend fun doWork(): Result {
        if (!isNotificationAllowed(applicationContext)) return Result.success()
        val draftId = inputData.getString(NotificationScheduler.KEY_DRAFT_ID)?.takeIf { it.isNotBlank() }
            ?: return Result.success()
        val state = preferencesManager.getDraftReminderState(draftId) ?: return Result.success()
        val nowMs = System.currentTimeMillis()
        val scheduleConfig = notificationScheduleConfigService.current()
        val delayMs = scheduleConfig.draftCompletionDelayMs

        if (state.selectedPhotoCount > 0) return Result.success()
        if (state.templateId.isNullOrBlank() && state.songId == null) return Result.success()
        if (!hasReachedDelay(nowMs - state.exitedAtMs, delayMs)) return Result.success()
        if (shouldSkipDraftNudgeDueToRecentAbandoned(
                nowMs = nowMs,
                lastAbandonedShownAtMs = state.lastAbandonedShownAtMs,
                dedupeWindowMs = DEDUPE_WITH_ABANDONED_WINDOW_MS,
                fastScheduleMode = scheduleConfig.isFastScheduleMode()
            )
        ) {
            return Result.success()
        }

        val gateDecision = notificationCapPolicy.evaluate(
            NotificationCapPolicy.Input(
                nowMs = nowMs,
                dailyShownCount = preferencesManager.getNotificationDailyShownCount(nowMs),
                typeDailyShownCount = preferencesManager.getNotificationTypeDailyShownCount(
                    NotificationType.DRAFT_COMPLETION_NUDGE.name,
                    nowMs
                ),
                lastShownAtMs = preferencesManager.getNotificationLastShownAtMs(),
                sameItemLastShownAtMs = preferencesManager.getNotificationItemLastShownAtMs(
                    notificationType = NotificationType.DRAFT_COMPLETION_NUDGE.name,
                    itemId = draftId
                )
            )
        )
        if (!gateDecision.allowed) return Result.success()

        Analytics.trackNotificationEligible(
            type = NotificationType.DRAFT_COMPLETION_NUDGE.analyticsValue,
            itemId = draftId,
            itemType = "draft",
            sourceTrigger = "draft_zero_photo_15m",
            deepLinkDestination = "select_photos",
            copyVariant = "finish_what_started_v1",
            imageType = "template_key_art",
            sessionType = "exit_intent",
            delayMinutes = delayMs / 60_000L
        )

        val shown = notificationRenderer.show(
            NotificationPayload(
                type = NotificationType.DRAFT_COMPLETION_NUDGE,
                itemId = draftId,
                itemType = "draft",
                channelId = NotificationChannels.CHANNEL_CREATION_REMINDERS,
                title = NotificationText(R.string.notif_draft_completion_nudge_title),
                body = NotificationText(R.string.notif_draft_completion_nudge_body),
                ctaText = NotificationText(R.string.notif_cta_view_template),
                deepLink = NotificationDeepLinkFactory.resumeTemplate(
                    templateId = state.templateId,
                    songId = state.songId ?: -1L,
                    draftId = draftId
                ),
                imageCandidates = emptyList(),
                fallbackImageRes = R.drawable.img_template2
            )
        )
        if (!shown) return Result.success()

        preferencesManager.recordNotificationShown(
            notificationType = NotificationType.DRAFT_COMPLETION_NUDGE.name,
            itemId = draftId,
            shownAtMs = nowMs
        )
        preferencesManager.markDraftNudgeShown(draftId, nowMs)
        Analytics.trackNotificationShown(
            type = NotificationType.DRAFT_COMPLETION_NUDGE.analyticsValue,
            itemId = draftId,
            itemType = "draft",
            sourceTrigger = "draft_zero_photo_15m",
            deepLinkDestination = "select_photos",
            copyVariant = "finish_what_started_v1",
            imageType = "template_key_art",
            shownAt = nowMs
        )
        return Result.success()
    }

    companion object {
        private const val DEDUPE_WITH_ABANDONED_WINDOW_MS = 6L * 60L * 60_000L
    }
}
