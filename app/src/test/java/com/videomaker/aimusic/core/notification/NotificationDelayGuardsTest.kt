package com.videomaker.aimusic.core.notification

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationDelayGuardsTest {

    @Test
    fun `hasReachedDelay is false below required delay`() {
        assertFalse(hasReachedDelay(elapsedMs = 119_999L, requiredDelayMs = 120_000L))
    }

    @Test
    fun `hasReachedDelay is true at required delay`() {
        assertTrue(hasReachedDelay(elapsedMs = 120_000L, requiredDelayMs = 120_000L))
    }

    @Test
    fun `hasReachedDelay is true when zero required delay meets zero elapsed`() {
        assertTrue(hasReachedDelay(elapsedMs = 0L, requiredDelayMs = 0L))
    }

    @Test
    fun `hasReachedDelay is false for negative elapsed with positive required delay`() {
        assertFalse(hasReachedDelay(elapsedMs = -1L, requiredDelayMs = 1L))
    }

    @Test
    fun `hasReachedDelay treats negative required delay as zero bound`() {
        assertTrue(hasReachedDelay(elapsedMs = 0L, requiredDelayMs = -1L))
        assertFalse(hasReachedDelay(elapsedMs = -1L, requiredDelayMs = -1L))
    }

    @Test
    fun `draft nudge dedupe is skipped in fast schedule mode`() {
        val now = 1_000_000L
        val shouldSkip = shouldSkipDraftNudgeDueToRecentAbandoned(
            nowMs = now,
            lastAbandonedShownAtMs = now - 60_000L,
            dedupeWindowMs = 6L * 60L * 60_000L,
            fastScheduleMode = true
        )

        assertFalse(shouldSkip)
    }

    @Test
    fun `quick save retries while waiting for first app background`() {
        assertTrue(
            shouldRetryQuickSaveWhileForeground(
                appBackgroundAtMs = null,
                generatedAtMs = 1_000L,
                runAttemptCount = 0,
                maxRetryCount = 5
            )
        )

        assertFalse(
            shouldRetryQuickSaveWhileForeground(
                appBackgroundAtMs = null,
                generatedAtMs = 1_000L,
                runAttemptCount = 5,
                maxRetryCount = 5
            )
        )

        assertFalse(
            shouldRetryQuickSaveWhileForeground(
                appBackgroundAtMs = 2_000L,
                generatedAtMs = 1_000L,
                runAttemptCount = 0,
                maxRetryCount = 5
            )
        )
    }
}
