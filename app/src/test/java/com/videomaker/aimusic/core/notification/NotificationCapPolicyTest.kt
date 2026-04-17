package com.videomaker.aimusic.core.notification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationCapPolicyTest {

    private val policy = NotificationCapPolicy()

    @Test
    fun `global cap blocks after 3 per day`() {
        val decision = policy.evaluate(
            NotificationCapPolicy.Input(
                nowMs = 1_000_000L,
                dailyShownCount = 3,
                lastShownAtMs = null,
                sameItemLastShownAtMs = null
            )
        )

        assertFalse(decision.allowed)
        assertEquals(NotificationCapPolicy.BlockReason.GLOBAL_DAILY_CAP_REACHED, decision.reason)
    }

    @Test
    fun `cooldown blocks when last shown under 120 minutes`() {
        val now = 2_000_000L
        val decision = policy.evaluate(
            NotificationCapPolicy.Input(
                nowMs = now,
                dailyShownCount = 1,
                lastShownAtMs = now - (119 * 60_000L),
                sameItemLastShownAtMs = null
            )
        )

        assertFalse(decision.allowed)
        assertEquals(NotificationCapPolicy.BlockReason.GLOBAL_COOLDOWN_ACTIVE, decision.reason)
    }

    @Test
    fun `per-item cap blocks same trending song within 24 hours`() {
        val now = 3_000_000L
        val decision = policy.evaluate(
            NotificationCapPolicy.Input(
                nowMs = now,
                dailyShownCount = 0,
                lastShownAtMs = now - (121 * 60_000L),
                sameItemLastShownAtMs = now - (23 * 60 * 60_000L)
            )
        )

        assertFalse(decision.allowed)
        assertEquals(NotificationCapPolicy.BlockReason.PER_ITEM_COOLDOWN_ACTIVE, decision.reason)
    }

    @Test
    fun `per-type cap blocks second notification of same type in one day`() {
        val now = 4_000_000L
        val decision = policy.evaluate(
            NotificationCapPolicy.Input(
                nowMs = now,
                dailyShownCount = 1,
                typeDailyShownCount = 1,
                lastShownAtMs = null,
                sameItemLastShownAtMs = null
            )
        )

        assertFalse(decision.allowed)
        assertEquals(NotificationCapPolicy.BlockReason.PER_TYPE_DAILY_CAP_REACHED, decision.reason)
    }

    @Test
    fun `fast schedule mode bypasses cap and cooldown guards`() {
        val configService = NotificationScheduleConfigService()
        configService.applyRawJsonForTesting(
            """
            {
              "trending_song_daily": {"hour": 0, "minute": 1},
              "viral_template_daily": {"hour": 0, "minute": 1},
              "quick_save_delay_minutes": 1,
              "share_encouragement_delay_minutes": 2,
              "forgotten_masterpiece_delay_minutes": 3,
              "abandoned_same_session_delay_minutes": 2,
              "abandoned_cold_session_delay_minutes": 5,
              "draft_completion_nudge_delay_minutes": 6
            }
            """.trimIndent()
        )
        val fastModePolicy = NotificationCapPolicy(configService)
        val now = 5_000_000L

        val decision = fastModePolicy.evaluate(
            NotificationCapPolicy.Input(
                nowMs = now,
                dailyShownCount = 99,
                typeDailyShownCount = 99,
                lastShownAtMs = now - 1_000L,
                sameItemLastShownAtMs = now - 1_000L
            )
        )

        assertTrue(decision.allowed)
        assertNull(decision.reason)
    }
}
