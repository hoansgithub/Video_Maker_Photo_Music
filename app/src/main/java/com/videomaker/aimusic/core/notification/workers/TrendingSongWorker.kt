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
import com.videomaker.aimusic.core.notification.TrendingCandidateResolver
import com.videomaker.aimusic.domain.repository.SongRepository
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.time.LocalDate
import java.time.ZoneId

class TrendingSongWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params), KoinComponent {

    private val songRepository: SongRepository by inject()
    private val preferencesManager: PreferencesManager by inject()
    private val notificationCapPolicy: NotificationCapPolicy by inject()
    private val notificationRenderer: NotificationRenderer by inject()
    private val notificationScheduler: NotificationScheduler by inject()

    override suspend fun doWork(): Result {
        val localDate = LocalDate.now(ZoneId.systemDefault()).toString()
        return try {
            if (!isNotificationAllowed(applicationContext)) {
                Analytics.trackNotificationCanceled(
                    type = NotificationType.TRENDING_SONG.analyticsValue,
                    reason = "permission_denied"
                )
                return Result.success()
            }

            val featuredSongs = songRepository.getFeaturedSongs(limit = 20).getOrElse {
                Analytics.trackNotificationCanceled(
                    type = NotificationType.TRENDING_SONG.analyticsValue,
                    reason = "remote_unavailable"
                )
                return Result.success()
            }

            val todaySnapshot = preferencesManager.getTrendingSongSnapshotForDate(localDate)
            val candidate = TrendingCandidateResolver.resolve(
                featuredSongs = featuredSongs,
                snapshot = todaySnapshot?.let {
                    TrendingCandidateResolver.DailySnapshot(
                        localDate = it.localDate,
                        songId = it.songId,
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
                        notificationType = NotificationType.TRENDING_SONG.name,
                        nowMs = nowMs
                    ),
                    lastShownAtMs = preferencesManager.getNotificationLastShownAtMs(),
                    sameItemLastShownAtMs = preferencesManager.getNotificationItemLastShownAtMs(
                        notificationType = NotificationType.TRENDING_SONG.name,
                        itemId = candidate.id
                    )
                )
            )
            if (!gateDecision.allowed) {
                Analytics.trackNotificationCanceled(
                    type = NotificationType.TRENDING_SONG.analyticsValue,
                    reason = gateDecision.reason?.analyticsReason ?: "blocked",
                    itemId = candidate.id.toString(),
                    itemType = "song"
                )
                return Result.success()
            }

            Analytics.trackNotificationEligible(
                type = NotificationType.TRENDING_SONG.analyticsValue,
                itemId = candidate.id.toString(),
                itemType = "song",
                sourceTrigger = "daily_cutoff_19_local",
                deepLinkDestination = "song_preview",
                copyVariant = "kendrick_viral_v1",
                imageType = if (candidate.coverUrl.isNotBlank()) "song_artwork" else "fallback_artwork",
                delayMinutes = 0
            )

            val shown = notificationRenderer.show(
                NotificationPayload(
                    type = NotificationType.TRENDING_SONG,
                    itemId = candidate.id.toString(),
                    itemType = "song",
                    channelId = NotificationChannels.CHANNEL_TREND_ALERTS,
                    title = NotificationText(R.string.notif_trending_song_title),
                    body = NotificationText(
                        R.string.notif_trending_song_body,
                        listOf(candidate.artist, candidate.name)
                    ),
                    ctaText = NotificationText(R.string.notif_cta_play_song),
                    deepLink = NotificationDeepLinkFactory.trendingSong(candidate.id),
                    imageCandidates = listOf(candidate.coverUrl),
                    fallbackImageRes = R.drawable.img_song1
                )
            )
            if (!shown) return Result.success()

            preferencesManager.recordNotificationShown(
                notificationType = NotificationType.TRENDING_SONG.name,
                itemId = candidate.id.toString(),
                shownAtMs = nowMs
            )
            preferencesManager.setTrendingSongSnapshot(
                localDate = localDate,
                songId = candidate.id,
                usageCount = candidate.usageCount
            )
            Analytics.trackNotificationShown(
                type = NotificationType.TRENDING_SONG.analyticsValue,
                itemId = candidate.id.toString(),
                itemType = "song",
                sourceTrigger = "daily_cutoff_19_local",
                deepLinkDestination = "song_preview",
                copyVariant = "kendrick_viral_v1",
                imageType = if (candidate.coverUrl.isNotBlank()) "song_artwork" else "fallback_artwork",
                shownAt = nowMs
            )
            Result.success()
        } finally {
            runCatching { notificationScheduler.rescheduleTrendingSongDaily() }
        }
    }
}
