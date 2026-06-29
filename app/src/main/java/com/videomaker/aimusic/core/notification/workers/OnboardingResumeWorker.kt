package com.videomaker.aimusic.core.notification.workers

import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.videomaker.aimusic.core.analytics.Analytics
import com.videomaker.aimusic.core.data.local.PreferencesManager
import com.videomaker.aimusic.core.notification.NotificationRenderer
import com.videomaker.aimusic.core.notification.NotificationScheduler
import com.videomaker.aimusic.core.notification.NotificationType
import com.videomaker.aimusic.core.notification.OnboardingResumeNotifications
import com.videomaker.aimusic.core.notification.TrendingCandidateResolver
import co.alcheclub.lib.acccore.remoteconfig.RemoteConfig
import com.videomaker.aimusic.domain.repository.SongRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.time.LocalDate
import java.time.ZoneId

class OnboardingResumeWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params), KoinComponent {

    private val preferencesManager: PreferencesManager by inject()
    private val notificationRenderer: NotificationRenderer by inject()
    private val notificationScheduler: NotificationScheduler by inject()
    private val songRepository: SongRepository by inject()
    private val remoteConfig: RemoteConfig by inject()

    override suspend fun doWork(): Result {
        // Permission revoked or notifications disabled → no-op.
        if (!isNotificationAllowed(applicationContext)) {
            Analytics.trackNotificationCanceled(
                type = NotificationType.ONBOARDING_RESUME.analyticsValue,
                reason = "permission_denied"
            )
            return Result.success()
        }
        // Onboarding finished while we were waiting → no-op.
        if (preferencesManager.isOnboardingComplete()) {
            Analytics.trackNotificationCanceled(
                type = NotificationType.ONBOARDING_RESUME.analyticsValue,
                reason = "onboarding_complete"
            )
            return Result.success()
        }

        val firedCount = preferencesManager.obResumeFiredCount
        val attempt = OnboardingResumeNotifications.nextAttempt(firedCount)
            ?: return Result.success()
        // Guard against a stale scheduled attempt that no longer matches progress.
        // Default -1 (invalid) so a malformed/missing key fails CLOSED rather than open.
        val requestedAttempt = inputData.getInt(NotificationScheduler.KEY_OB_RESUME_ATTEMPT, -1)
        if (requestedAttempt != attempt) {
            return Result.success()
        }
        val delayMs = inputData.getLong(
            NotificationScheduler.KEY_OB_RESUME_DELAY_MS,
            OnboardingResumeNotifications.DEFAULT_DELAY_MINUTES * 60_000L
        )

        // Don't nag while the user is actively in the app. The worker runs in the app's main
        // process, so ProcessLifecycleOwner reflects the real foreground state (and is at
        // INITIALIZED in a fresh worker-only process after a swipe-kill → treated as background).
        val isForeground = withContext(Dispatchers.Main.immediate) {
            ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        }
        if (isForeground) {
            // Re-arm and try again later, once the user has left.
            notificationScheduler.scheduleOnboardingResume(attempt = attempt, delayMs = delayMs)
            return Result.success()
        }

        // Only attempt 2 uses the dynamic Top-1-song-by-GEO cover; 1 & 3 use bundled artwork.
        val songCoverUrl = if (attempt == 2) resolveTopSongCover() else null

        val shown = notificationRenderer.show(
            OnboardingResumeNotifications.buildPayload(attempt = attempt, songCoverUrl = songCoverUrl)
        )
        if (!shown) return Result.success()

        // Advance the counter ONLY after a successful post.
        preferencesManager.obResumeFiredCount = firedCount + 1
        preferencesManager.recordNotificationShown(
            notificationType = NotificationType.ONBOARDING_RESUME.name,
            itemId = "ob_resume_$attempt",
            shownAtMs = System.currentTimeMillis()
        )
        Analytics.trackNotificationShown(
            type = NotificationType.ONBOARDING_RESUME.analyticsValue,
            itemId = "ob_resume_$attempt",
            itemType = "onboarding",
            sourceTrigger = "ob_exit",
            deepLinkDestination = "onboarding_resume",
            copyVariant = "ob_resume_v1",
            imageType = if (!songCoverUrl.isNullOrBlank()) "song_artwork" else "fallback_artwork",
            shownAt = System.currentTimeMillis()
        )

        // Chain: schedule the next attempt so it fires without the user re-opening the app.
        val nextResolution = OnboardingResumeNotifications.resolveScheduleRequest(
            onboardingComplete = preferencesManager.isOnboardingComplete(),
            enabled = true,
            firedCount = preferencesManager.obResumeFiredCount,
            delayMinutesFor = { a ->
                remoteConfig.getLong(
                    OnboardingResumeNotifications.delayMinutesKey(a),
                    OnboardingResumeNotifications.DEFAULT_DELAY_MINUTES
                )
            }
        )
        if (nextResolution.firedCount > preferencesManager.obResumeFiredCount) {
            preferencesManager.obResumeFiredCount = nextResolution.firedCount
        }
        nextResolution.request?.let { next ->
            notificationScheduler.scheduleOnboardingResume(attempt = next.attempt, delayMs = next.delayMs)
        }

        return Result.success()
    }

    private suspend fun resolveTopSongCover(): String? {
        val localDate = LocalDate.now(ZoneId.systemDefault()).toString()
        val featuredSongs = songRepository.getFeaturedSongs(limit = 20).getOrNull() ?: return null
        val snapshot = preferencesManager.getTrendingSongSnapshotForDate(localDate)
        val candidate = TrendingCandidateResolver.resolve(
            featuredSongs = featuredSongs,
            snapshot = snapshot?.let {
                TrendingCandidateResolver.DailySnapshot(
                    localDate = it.localDate,
                    songId = it.songId,
                    usageCount = it.usageCount
                )
            },
            currentLocalDate = localDate
        ) ?: return null
        return candidate.coverUrl.takeIf { it.isNotBlank() }
    }
}
