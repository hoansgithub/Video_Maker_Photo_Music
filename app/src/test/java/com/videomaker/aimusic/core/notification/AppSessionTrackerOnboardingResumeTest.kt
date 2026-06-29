package com.videomaker.aimusic.core.notification

import com.videomaker.aimusic.core.notification.OnboardingResumeNotifications.ScheduleRequest
import com.videomaker.aimusic.core.notification.OnboardingResumeNotifications.ScheduleResolution
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Tests the pure scheduling decision that AppSessionTracker.onStop() delegates to.
 * (AppSessionTracker itself only wires PreferencesManager / RemoteConfig into this and
 * persists the returned firedCount.)
 */
class AppSessionTrackerOnboardingResumeTest {

    private fun resolve(
        onboardingComplete: Boolean = false,
        enabled: Boolean = true,
        firedCount: Int = 0,
        delays: (Int) -> Long = { 5L }
    ): ScheduleResolution = OnboardingResumeNotifications.resolveScheduleRequest(
        onboardingComplete = onboardingComplete,
        enabled = enabled,
        firedCount = firedCount,
        delayMinutesFor = delays
    )

    @Test
    fun schedules_attempt1_when_ob_incomplete_and_enabled() {
        val r = resolve(firedCount = 0)
        assertEquals(ScheduleRequest(attempt = 1, delayMs = 5L * 60_000L), r.request)
        assertEquals(0, r.firedCount)
    }

    @Test
    fun schedules_attempt3_when_two_already_fired() {
        val r = resolve(firedCount = 2)
        assertEquals(ScheduleRequest(attempt = 3, delayMs = 5L * 60_000L), r.request)
        assertEquals(2, r.firedCount)
    }

    @Test
    fun nothing_when_ob_complete() {
        val r = resolve(onboardingComplete = true)
        assertNull(r.request)
    }

    @Test
    fun nothing_when_disabled() {
        val r = resolve(enabled = false)
        assertNull(r.request)
    }

    @Test
    fun nothing_when_all_three_fired() {
        val r = resolve(firedCount = 3)
        assertNull(r.request)
    }

    @Test
    fun zero_delay_on_attempt1_skips_to_attempt2() {
        val r = resolve(firedCount = 0, delays = { if (it == 1) 0L else 5L })
        assertEquals(ScheduleRequest(attempt = 2, delayMs = 5L * 60_000L), r.request)
        // attempt 1 skipped (disabled) → counter advanced past it.
        assertEquals(1, r.firedCount)
    }

    @Test
    fun zero_delay_on_middle_attempt_still_fires_attempt3() {
        val r = resolve(firedCount = 1, delays = { if (it == 2) 0L else 5L })
        assertEquals(ScheduleRequest(attempt = 3, delayMs = 5L * 60_000L), r.request)
        assertEquals(2, r.firedCount)
    }

    @Test
    fun all_zero_disables_whole_sequence() {
        val r = resolve(firedCount = 0, delays = { 0L })
        assertNull(r.request)
        assertEquals(3, r.firedCount)
    }

    @Test
    fun negative_delay_is_treated_as_disabled() {
        val r = resolve(firedCount = 0, delays = { if (it == 1) -10L else 5L })
        assertEquals(ScheduleRequest(attempt = 2, delayMs = 5L * 60_000L), r.request)
        assertEquals(1, r.firedCount)
    }
}
