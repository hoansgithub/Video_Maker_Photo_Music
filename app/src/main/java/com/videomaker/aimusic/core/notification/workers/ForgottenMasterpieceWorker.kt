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
import com.videomaker.aimusic.domain.repository.ProjectRepository
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class ForgottenMasterpieceWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params), KoinComponent {

    private val preferencesManager: PreferencesManager by inject()
    private val notificationCapPolicy: NotificationCapPolicy by inject()
    private val notificationRenderer: NotificationRenderer by inject()
    private val projectRepository: ProjectRepository by inject()

    override suspend fun doWork(): Result {
        if (!isNotificationAllowed(applicationContext)) return Result.success()
        val projectId = inputData.getString(NotificationScheduler.KEY_PROJECT_ID)?.takeIf { it.isNotBlank() }
            ?: return Result.success()
        val state = preferencesManager.getVideoReminderState(projectId) ?: return Result.success()
        val project = projectRepository.getProject(projectId) ?: return Result.success()
        val nowMs = System.currentTimeMillis()

        if (state.savedAtMs != null || state.sharedAtMs != null) return Result.success()
        if ((nowMs - state.generatedAtMs) < NotificationScheduler.FORGOTTEN_MASTERPIECE_DELAY_MS) return Result.success()

        val gateDecision = notificationCapPolicy.evaluate(
            NotificationCapPolicy.Input(
                nowMs = nowMs,
                dailyShownCount = preferencesManager.getNotificationDailyShownCount(nowMs),
                typeDailyShownCount = preferencesManager.getNotificationTypeDailyShownCount(
                    NotificationType.FORGOTTEN_MASTERPIECE.name,
                    nowMs
                ),
                lastShownAtMs = preferencesManager.getNotificationLastShownAtMs(),
                sameItemLastShownAtMs = preferencesManager.getNotificationItemLastShownAtMs(
                    notificationType = NotificationType.FORGOTTEN_MASTERPIECE.name,
                    itemId = projectId
                )
            )
        )
        if (!gateDecision.allowed) {
            Analytics.trackNotificationCanceled(
                type = NotificationType.FORGOTTEN_MASTERPIECE.analyticsValue,
                reason = gateDecision.reason?.analyticsReason ?: "blocked",
                itemId = projectId,
                itemType = "video"
            )
            return Result.success()
        }

        Analytics.trackNotificationEligible(
            type = NotificationType.FORGOTTEN_MASTERPIECE.analyticsValue,
            itemId = projectId,
            itemType = "video",
            sourceTrigger = "generated_no_share_no_save_24h",
            deepLinkDestination = "my_video",
            copyVariant = "masterpiece_waiting_v1",
            imageType = if (!state.thumbnailUri.isNullOrBlank()) "video_cover" else "fallback_artwork",
            sessionType = "retention",
            delayMinutes = 24L * 60L
        )

        val shown = notificationRenderer.show(
            NotificationPayload(
                type = NotificationType.FORGOTTEN_MASTERPIECE,
                itemId = projectId,
                itemType = "video",
                channelId = NotificationChannels.CHANNEL_MY_VIDEO_RETENTION,
                title = "Your masterpiece is waiting!",
                body = "You did the hard part, now let it shine. Your video is ready to be shared with the world!",
                ctaText = "Continue Editing",
                deepLink = NotificationDeepLinkFactory.myVideo(projectId, hintMode = "hint_share"),
                imageCandidates = listOfNotNull(
                    state.thumbnailUri,
                    project.thumbnailUri?.toString()
                ),
                fallbackImageRes = R.drawable.img_template2,
                ivCtaIcon = R.drawable.ic_video_generator_fill
            )
        )
        if (!shown) return Result.success()

        preferencesManager.recordNotificationShown(
            notificationType = NotificationType.FORGOTTEN_MASTERPIECE.name,
            itemId = projectId,
            shownAtMs = nowMs
        )
        Analytics.trackNotificationShown(
            type = NotificationType.FORGOTTEN_MASTERPIECE.analyticsValue,
            itemId = projectId,
            itemType = "video",
            sourceTrigger = "generated_no_share_no_save_24h",
            deepLinkDestination = "my_video",
            copyVariant = "masterpiece_waiting_v1",
            imageType = if (!state.thumbnailUri.isNullOrBlank()) "video_cover" else "fallback_artwork",
            shownAt = nowMs
        )
        return Result.success()
    }
}

