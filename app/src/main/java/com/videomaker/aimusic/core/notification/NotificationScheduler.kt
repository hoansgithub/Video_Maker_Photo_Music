package com.videomaker.aimusic.core.notification

import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.WorkManager
import com.videomaker.aimusic.core.notification.workers.AbandonedSelectPhotosWorker
import com.videomaker.aimusic.core.notification.workers.DraftCompletionNudgeWorker
import com.videomaker.aimusic.core.notification.workers.ForgottenMasterpieceWorker
import com.videomaker.aimusic.core.notification.workers.QuickSaveReminderWorker
import com.videomaker.aimusic.core.notification.workers.ShareEncouragementWorker
import com.videomaker.aimusic.core.notification.workers.TrendingSongWorker
import com.videomaker.aimusic.core.notification.workers.ViralTemplateWorker
import java.time.LocalDateTime
import java.time.Duration
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit

class NotificationScheduler(
    private val workManager: WorkManager
) {

    fun scheduleDailyBootstrap(now: ZonedDateTime = ZonedDateTime.now()) {
        scheduleTrendingSongDailyBootstrap(now)
        scheduleViralTemplateDailyBootstrap(now)
    }

    fun scheduleTrendingSongDailyBootstrap(now: ZonedDateTime = ZonedDateTime.now()) {
        enqueueDailyTrendingSong(
            now = now,
            policy = ExistingWorkPolicy.KEEP
        )
    }

    fun rescheduleTrendingSongDaily(now: ZonedDateTime = ZonedDateTime.now()) {
        enqueueDailyTrendingSong(
            now = now,
            policy = ExistingWorkPolicy.REPLACE
        )
    }

    fun scheduleViralTemplateDailyBootstrap(now: ZonedDateTime = ZonedDateTime.now()) {
        enqueueDailyViralTemplate(
            now = now,
            policy = ExistingWorkPolicy.KEEP
        )
    }

    fun rescheduleViralTemplateDaily(now: ZonedDateTime = ZonedDateTime.now()) {
        enqueueDailyViralTemplate(
            now = now,
            policy = ExistingWorkPolicy.REPLACE
        )
    }

    fun scheduleForgottenMasterpiece(projectId: String, generatedAtMs: Long) {
        val delayMs = (generatedAtMs + FORGOTTEN_MASTERPIECE_DELAY_MS) - System.currentTimeMillis()
        val request = OneTimeWorkRequestBuilder<ForgottenMasterpieceWorker>()
            .setInputData(
                androidx.work.Data.Builder()
                    .putString(KEY_PROJECT_ID, projectId)
                    .build()
            )
            .setInitialDelay(delayMs.coerceAtLeast(0L), TimeUnit.MILLISECONDS)
            .addTag(TAG_MY_VIDEO)
            .build()
        workManager.enqueueUniqueWork(uniqueForgotten(projectId), ExistingWorkPolicy.REPLACE, request)
    }

    fun scheduleQuickSaveReminder(projectId: String, generatedAtMs: Long) {
        val delayMs = (generatedAtMs + QUICK_SAVE_DELAY_MS) - System.currentTimeMillis()
        val request = OneTimeWorkRequestBuilder<QuickSaveReminderWorker>()
            .setInputData(
                androidx.work.Data.Builder()
                    .putString(KEY_PROJECT_ID, projectId)
                    .build()
            )
            .setInitialDelay(delayMs.coerceAtLeast(0L), TimeUnit.MILLISECONDS)
            .addTag(TAG_MY_VIDEO)
            .build()
        workManager.enqueueUniqueWork(uniqueQuickSave(projectId), ExistingWorkPolicy.REPLACE, request)
    }

    fun scheduleShareEncouragement(projectId: String, generatedAtMs: Long) {
        val delayMs = (generatedAtMs + SHARE_ENCOURAGEMENT_DELAY_MS) - System.currentTimeMillis()
        val request = OneTimeWorkRequestBuilder<ShareEncouragementWorker>()
            .setInputData(
                androidx.work.Data.Builder()
                    .putString(KEY_PROJECT_ID, projectId)
                    .build()
            )
            .setInitialDelay(delayMs.coerceAtLeast(0L), TimeUnit.MILLISECONDS)
            .addTag(TAG_MY_VIDEO)
            .build()
        workManager.enqueueUniqueWork(uniqueShare(projectId), ExistingWorkPolicy.REPLACE, request)
    }

    fun cancelProjectReminders(projectId: String) {
        workManager.cancelUniqueWork(uniqueQuickSave(projectId))
        workManager.cancelUniqueWork(uniqueShare(projectId))
        workManager.cancelUniqueWork(uniqueForgotten(projectId))
    }

    fun scheduleAbandonedSelectPhotos(draftId: String, sessionId: Long, exitedAtMs: Long) {
        val sanitizedDraftId = sanitizeKey(draftId)
        val dataBuilder = androidx.work.Data.Builder()
            .putString(KEY_DRAFT_ID, draftId)
            .putLong(KEY_EXIT_SESSION_ID, sessionId)
            .putLong(KEY_EXITED_AT_MS, exitedAtMs)

        val sameSessionRequest = OneTimeWorkRequestBuilder<AbandonedSelectPhotosWorker>()
            .setInputData(
                dataBuilder
                    .putString(KEY_ABANDON_MODE, ABANDON_MODE_SAME)
                    .build()
            )
            .setInitialDelay(ABANDONED_SAME_SESSION_DELAY_MS, TimeUnit.MILLISECONDS)
            .addTag(TAG_CREATION)
            .build()
        workManager.enqueueUniqueWork(
            uniqueAbandonedSame(sanitizedDraftId),
            ExistingWorkPolicy.REPLACE,
            sameSessionRequest
        )

        val coldSessionRequest = OneTimeWorkRequestBuilder<AbandonedSelectPhotosWorker>()
            .setInputData(
                dataBuilder
                    .putString(KEY_ABANDON_MODE, ABANDON_MODE_COLD)
                    .build()
            )
            .setInitialDelay(ABANDONED_COLD_SESSION_DELAY_MS, TimeUnit.MILLISECONDS)
            .addTag(TAG_CREATION)
            .build()
        workManager.enqueueUniqueWork(
            uniqueAbandonedCold(sanitizedDraftId),
            ExistingWorkPolicy.REPLACE,
            coldSessionRequest
        )
    }

    fun scheduleDraftCompletionNudge(draftId: String, exitedAtMs: Long) {
        val delayMs = (exitedAtMs + DRAFT_COMPLETION_DELAY_MS) - System.currentTimeMillis()
        val request = OneTimeWorkRequestBuilder<DraftCompletionNudgeWorker>()
            .setInputData(
                androidx.work.Data.Builder()
                    .putString(KEY_DRAFT_ID, draftId)
                    .putLong(KEY_EXITED_AT_MS, exitedAtMs)
                    .build()
            )
            .setInitialDelay(delayMs.coerceAtLeast(0L), TimeUnit.MILLISECONDS)
            .addTag(TAG_CREATION)
            .build()
        workManager.enqueueUniqueWork(
            uniqueDraftNudge(sanitizeKey(draftId)),
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun cancelDraftReminders(draftId: String) {
        val sanitized = sanitizeKey(draftId)
        workManager.cancelUniqueWork(uniqueAbandonedSame(sanitized))
        workManager.cancelUniqueWork(uniqueAbandonedCold(sanitized))
        workManager.cancelUniqueWork(uniqueDraftNudge(sanitized))
    }

    private fun enqueueDailyTrendingSong(
        now: ZonedDateTime,
        policy: ExistingWorkPolicy
    ) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = OneTimeWorkRequestBuilder<TrendingSongWorker>()
            .setInitialDelay(nextDelayMillis(now), TimeUnit.MILLISECONDS)
            .setConstraints(constraints)
            .addTag(TAG_TRENDING_DAILY)
            .build()

        workManager.enqueueUniqueWork(
            UNIQUE_WORK_TRENDING_DAILY,
            policy,
            request
        )
    }

    private fun enqueueDailyViralTemplate(
        now: ZonedDateTime,
        policy: ExistingWorkPolicy
    ) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = OneTimeWorkRequestBuilder<ViralTemplateWorker>()
            .setInitialDelay(nextDelayMillis(now, TARGET_VIRAL_HOUR, TARGET_VIRAL_MINUTE), TimeUnit.MILLISECONDS)
            .setConstraints(constraints)
            .addTag(TAG_VIRAL_DAILY)
            .build()

        workManager.enqueueUniqueWork(
            UNIQUE_WORK_VIRAL_DAILY,
            policy,
            request
        )
    }

    private fun nextDelayMillis(now: ZonedDateTime): Long {
        return nextDelayMillis(now, TARGET_TRENDING_HOUR, TARGET_TRENDING_MINUTE)
    }

    private fun nextDelayMillis(now: ZonedDateTime, hour: Int, minute: Int): Long {
        var target = now.withHour(hour).withMinute(minute).withSecond(0).withNano(0)
        if (!target.isAfter(now)) {
            target = target.plusDays(1)
        }
        return Duration.between(now, target).toMillis().coerceAtLeast(0L)
    }

    private fun uniqueQuickSave(projectId: String): String = "notif_quick_save_${sanitizeKey(projectId)}"
    private fun uniqueShare(projectId: String): String = "notif_share_${sanitizeKey(projectId)}"
    private fun uniqueForgotten(projectId: String): String = "notif_forgotten_${sanitizeKey(projectId)}"
    private fun uniqueAbandonedSame(draftId: String): String = "notif_abandon_same_$draftId"
    private fun uniqueAbandonedCold(draftId: String): String = "notif_abandon_cold_$draftId"
    private fun uniqueDraftNudge(draftId: String): String = "notif_draft_nudge_$draftId"

    private fun sanitizeKey(raw: String): String {
        return raw.lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9_]"), "_")
            .take(64)
            .ifBlank { "unknown_${DateTimeFormatter.ofPattern("HHmmss").format(LocalDateTime.now())}" }
    }

    companion object {
        const val UNIQUE_WORK_TRENDING_DAILY = "notif_trending_song_daily"
        const val UNIQUE_WORK_VIRAL_DAILY = "notif_viral_template_daily"
        const val KEY_PROJECT_ID = "key_project_id"
        const val KEY_DRAFT_ID = "key_draft_id"
        const val KEY_EXIT_SESSION_ID = "key_exit_session_id"
        const val KEY_EXITED_AT_MS = "key_exited_at_ms"
        const val KEY_ABANDON_MODE = "key_abandon_mode"
        const val ABANDON_MODE_SAME = "same"
        const val ABANDON_MODE_COLD = "cold"
        private const val TAG_MY_VIDEO = "notif_my_video"
        private const val TAG_CREATION = "notif_creation"
        private const val TAG_TRENDING_DAILY = "notif_trending_song_daily_tag"
        private const val TAG_VIRAL_DAILY = "notif_viral_template_daily_tag"
        private const val TARGET_TRENDING_HOUR = 19
        private const val TARGET_TRENDING_MINUTE = 2
        private const val TARGET_VIRAL_HOUR = 20
        private const val TARGET_VIRAL_MINUTE = 2
        const val QUICK_SAVE_DELAY_MS = 30L * 60_000L
        const val SHARE_ENCOURAGEMENT_DELAY_MS = 12L * 60L * 60_000L
        const val FORGOTTEN_MASTERPIECE_DELAY_MS = 24L * 60L * 60_000L
        const val ABANDONED_SAME_SESSION_DELAY_MS = 2L * 60_000L
        const val ABANDONED_COLD_SESSION_DELAY_MS = 15L * 60_000L
        const val DRAFT_COMPLETION_DELAY_MS = 15L * 60_000L
    }
}
