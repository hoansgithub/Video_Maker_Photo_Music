package com.videomaker.aimusic.core.notification

import com.videomaker.aimusic.R
import com.videomaker.aimusic.core.constants.RemoteConfigKeys

/**
 * Pure logic for the onboarding-resume notification sequence (attempts 1..3).
 * No Android/Context dependencies so it is unit-testable.
 */
object OnboardingResumeNotifications {

    const val MAX_ATTEMPTS = 3
    const val DEFAULT_DELAY_MINUTES = 5L

    /** A resolved request to schedule the next OB-resume notification. */
    data class ScheduleRequest(val attempt: Int, val delayMs: Long)

    /**
     * Outcome of the background scheduling decision.
     *
     * @param firedCount the fired/skipped counter to persist. An attempt whose configured delay
     *   is 0 (or negative) is treated as DISABLED: it is skipped over — advancing this counter
     *   without firing — so disabling one notification never blocks the later ones.
     * @param request what to schedule next, or null when nothing should fire.
     */
    data class ScheduleResolution(
        val firedCount: Int,
        val request: ScheduleRequest?
    )

    /** Next attempt to fire given how many have already fired/skipped, or null if all done. */
    fun nextAttempt(firedCount: Int): Int? {
        if (firedCount < 0 || firedCount >= MAX_ATTEMPTS) return null
        return firedCount + 1
    }

    /**
     * Pure decision for whether/what to schedule on app background.
     *
     * Walks the attempts after [firedCount]; any attempt whose delay is <= 0 is disabled and
     * skipped (counter advances, nothing fires). Returns the first enabled attempt to schedule,
     * or no request when OB is complete, the feature is disabled, or all remaining attempts are
     * disabled / already fired.
     *
     * @param delayMinutesFor resolves the configured delay (minutes) for a given attempt;
     *   0 means "this notification is turned off".
     */
    fun resolveScheduleRequest(
        onboardingComplete: Boolean,
        enabled: Boolean,
        firedCount: Int,
        delayMinutesFor: (attempt: Int) -> Long
    ): ScheduleResolution {
        if (onboardingComplete || !enabled) return ScheduleResolution(firedCount, null)
        var resolvedFired = firedCount.coerceIn(0, MAX_ATTEMPTS)
        var attempt = resolvedFired + 1
        while (attempt <= MAX_ATTEMPTS) {
            val minutes = delayMinutesFor(attempt)
            if (minutes <= 0L) {
                // 0 (or negative) => this notification is turned off: skip it without firing.
                resolvedFired = attempt
                attempt++
                continue
            }
            return ScheduleResolution(
                firedCount = resolvedFired,
                request = ScheduleRequest(attempt = attempt, delayMs = minutes * 60_000L)
            )
        }
        return ScheduleResolution(resolvedFired, null)
    }

    fun delayMinutesKey(attempt: Int): String = when (attempt) {
        1 -> RemoteConfigKeys.OB_RESUME_NOTI_1_DELAY_MINUTES
        2 -> RemoteConfigKeys.OB_RESUME_NOTI_2_DELAY_MINUTES
        else -> RemoteConfigKeys.OB_RESUME_NOTI_3_DELAY_MINUTES
    }

    /**
     * Builds the notification payload for the given attempt.
     * - Attempt 1: bundled artwork (collapsed [R.drawable.img_noti_trigger_1] +
     *   expanded [R.drawable.img_noti_trigger_expand_1]).
     * - Attempt 2: Top-1-song-by-GEO cover (dynamic) when available.
     * - Attempt 3: bundled artwork [R.drawable.img_noti_trigger_3].
     */
    fun buildPayload(attempt: Int, songCoverUrl: String?): NotificationPayload {
        val titleRes: Int
        val bodyRes: Int
        val ctaRes: Int
        var fallbackRes: Int
        var candidates: List<String> = emptyList()
        var collapsedRes: Int? = null
        var expandedRes: Int? = null

        when (attempt) {
            1 -> {
                titleRes = R.string.notif_ob_resume_1_title
                bodyRes = R.string.notif_ob_resume_1_body
                ctaRes = R.string.notif_cta_discover_now
                collapsedRes = R.drawable.img_noti_trigger_1
                expandedRes = R.drawable.img_noti_trigger_expand_1
                fallbackRes = R.drawable.img_noti_trigger_expand_1
            }
            2 -> {
                titleRes = R.string.notif_ob_resume_2_title
                bodyRes = R.string.notif_ob_resume_2_body
                ctaRes = R.string.notif_cta_discover_now
                fallbackRes = R.drawable.img_song1
                candidates = songCoverUrl?.takeIf { it.isNotBlank() }?.let { listOf(it) } ?: emptyList()
            }
            else -> {
                titleRes = R.string.notif_ob_resume_3_title
                bodyRes = R.string.notif_ob_resume_3_body
                ctaRes = R.string.notif_cta_see_now
                collapsedRes = R.drawable.img_noti_trigger_3
                expandedRes = R.drawable.img_noti_trigger_3
                fallbackRes = R.drawable.img_noti_trigger_3
            }
        }

        return NotificationPayload(
            type = NotificationType.ONBOARDING_RESUME,
            itemId = "ob_resume_$attempt",
            itemType = "onboarding",
            channelId = NotificationChannels.CHANNEL_ONBOARDING_RESUME,
            title = NotificationText(titleRes),
            body = NotificationText(bodyRes),
            ctaText = NotificationText(ctaRes),
            deepLink = NotificationDeepLinkFactory.onboardingResume(),
            ivCtaIcon = 0,
            imageCandidates = candidates,
            fallbackImageRes = fallbackRes,
            collapsedImageRes = collapsedRes,
            expandedImageRes = expandedRes
        )
    }
}
