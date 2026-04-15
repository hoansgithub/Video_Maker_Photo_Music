package com.videomaker.aimusic.core.notification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
}
