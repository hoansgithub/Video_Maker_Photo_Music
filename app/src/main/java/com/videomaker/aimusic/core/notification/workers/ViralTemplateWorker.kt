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
import com.videomaker.aimusic.core.notification.NotificationText
import com.videomaker.aimusic.core.notification.NotificationType
import com.videomaker.aimusic.core.notification.ViralTemplateResolver
import com.videomaker.aimusic.domain.repository.TemplateRepository
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.time.LocalDate
import java.time.ZoneId

class ViralTemplateWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params), KoinComponent {

    private val templateRepository: TemplateRepository by inject()
    private val preferencesManager: PreferencesManager by inject()
    private val notificationCapPolicy: NotificationCapPolicy by inject()
    private val notificationRenderer: NotificationRenderer by inject()
    private val notificationScheduler: NotificationScheduler by inject()

    override suspend fun doWork(): Result {
        val localDate = LocalDate.now(ZoneId.systemDefault()).toString()
        return try {
            if (!isNotificationAllowed(applicationContext)) {
                Analytics.trackNotificationCanceled(
                    type = NotificationType.VIRAL_TEMPLATE.analyticsValue,
                    reason = "permission_denied"
                )
                return Result.success()
            }

            val featured = templateRepository.getFeaturedTemplates(limit = 20).getOrElse {
                Analytics.trackNotificationCanceled(
                    type = NotificationType.VIRAL_TEMPLATE.analyticsValue,
                    reason = "remote_unavailable"
                )
                return Result.success()
            }
            val snapshot = preferencesManager.getViralTemplateSnapshotForDate(localDate)
            val candidate = ViralTemplateResolver.resolve(
                featuredTemplates = featured,
                snapshot = snapshot?.let {
                    ViralTemplateResolver.DailySnapshot(
                        localDate = it.localDate,
                        templateId = it.templateId,
                        usageCount = it.usageCount
                    )
                },
                currentLocalDate = localDate
            ) ?: return Result.success()

            val nowMs = System.currentTimeMillis()
            val gateDecision = notificationCapPolicy.evaluate(
                NotificationCapPolicy.Input(
                    nowMs = nowMs,
                    dailyShownCount = preferencesManager.getNotificationDailyShownCount(nowMs),
                    typeDailyShownCount = preferencesManager.getNotificationTypeDailyShownCount(
                        notificationType = NotificationType.VIRAL_TEMPLATE.name,
                        nowMs = nowMs
                    ),
                    lastShownAtMs = preferencesManager.getNotificationLastShownAtMs(),
                    sameItemLastShownAtMs = preferencesManager.getNotificationItemLastShownAtMs(
                        notificationType = NotificationType.VIRAL_TEMPLATE.name,
                        itemId = candidate.template.id
                    )
                )
            )
            if (!gateDecision.allowed) {
                Analytics.trackNotificationCanceled(
                    type = NotificationType.VIRAL_TEMPLATE.analyticsValue,
                    reason = gateDecision.reason?.analyticsReason ?: "blocked",
                    itemId = candidate.template.id,
                    itemType = "template"
                )
                return Result.success()
            }

            Analytics.trackNotificationEligible(
                type = NotificationType.VIRAL_TEMPLATE.analyticsValue,
                itemId = candidate.template.id,
                itemType = "template",
                sourceTrigger = "daily_cutoff_20_local",
                deepLinkDestination = "template_preview",
                copyVariant = "social_proof_1m_v1",
                imageType = if (candidate.collageSources.isNotEmpty()) "template_collage" else "fallback_artwork",
                delayMinutes = 0
            )

            val shown = notificationRenderer.show(
                NotificationPayload(
                    type = NotificationType.VIRAL_TEMPLATE,
                    itemId = candidate.template.id,
                    itemType = "template",
                    channelId = NotificationChannels.CHANNEL_TREND_ALERTS,
                    title = NotificationText(R.string.notif_viral_template_title),
                    body = NotificationText(
                        R.string.notif_viral_template_body,
                        listOf(candidate.template.name)
                    ),
                    ctaText = NotificationText(R.string.notif_cta_discover_templates),
                    deepLink = NotificationDeepLinkFactory.viralTemplate(candidate.template.id),
                    imageCandidates = candidate.collageSources,
                    fallbackImageRes = R.drawable.img_template1
                )
            )
            if (!shown) return Result.success()

            preferencesManager.recordNotificationShown(
                notificationType = NotificationType.VIRAL_TEMPLATE.name,
                itemId = candidate.template.id,
                shownAtMs = nowMs
            )
            preferencesManager.setViralTemplateSnapshot(
                localDate = localDate,
                templateId = candidate.template.id,
                usageCount = candidate.template.useCount
            )
            Analytics.trackNotificationShown(
                type = NotificationType.VIRAL_TEMPLATE.analyticsValue,
                itemId = candidate.template.id,
                itemType = "template",
                sourceTrigger = "daily_cutoff_20_local",
                deepLinkDestination = "template_preview",
                copyVariant = "social_proof_1m_v1",
                imageType = if (candidate.collageSources.isNotEmpty()) "template_collage" else "fallback_artwork",
                shownAt = nowMs
            )
            Result.success()
        } finally {
            runCatching { notificationScheduler.rescheduleViralTemplateDaily() }
        }
    }
}
