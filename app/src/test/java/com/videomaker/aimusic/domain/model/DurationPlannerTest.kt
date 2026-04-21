package com.videomaker.aimusic.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DurationPlannerTest {

    @Test
    fun `suggestTotalDurationMs uses configured policy buckets`() {
        assertEquals(12_000L, DurationPlanner.suggestTotalDurationMs(2))
        assertEquals(15_000L, DurationPlanner.suggestTotalDurationMs(3))
        assertEquals(18_000L, DurationPlanner.suggestTotalDurationMs(4))
        assertEquals(20_000L, DurationPlanner.suggestTotalDurationMs(5))
        assertEquals(20_000L, DurationPlanner.suggestTotalDurationMs(8))
    }

    @Test
    fun `plan recomposes requested total exactly for supported sample`() {
        val plan = DurationPlanner.plan(
            imageCount = 5,
            totalDurationMs = 33_000L
        )

        assertEquals(33_000L, plan.totalDurationMs)
        assertEquals(5_000L, plan.imageDurationMs)
        assertEquals(20, plan.transitionPercentage)
        assertEquals(2_000L, plan.transitionOverlapMs)
        assertEquals(4, plan.transitionPointsMs.size)
        assertEquals(listOf(7_000L, 14_000L, 21_000L, 28_000L), plan.transitionPointsMs)
        assertTrue(plan.transitionPointsMs.zipWithNext().all { (left, right) -> left < right })
        assertEquals(
            plan.totalDurationMs,
            5 * plan.imageDurationMs + 4 * plan.transitionOverlapMs
        )
        assertEquals(
            plan.transitionOverlapMs,
            plan.imageDurationMs * 2 * plan.transitionPercentage / 100
        )
    }

    @Test
    fun `plan returns empty values for invalid inputs`() {
        val zeroImages = DurationPlanner.plan(
            imageCount = 0,
            totalDurationMs = 33_000L
        )
        val zeroDuration = DurationPlanner.plan(
            imageCount = 5,
            totalDurationMs = 0L
        )

        assertEquals(0L, zeroImages.totalDurationMs)
        assertEquals(0L, zeroImages.imageDurationMs)
        assertEquals(0L, zeroImages.transitionOverlapMs)
        assertEquals(emptyList<Long>(), zeroImages.transitionPointsMs)

        assertEquals(0L, zeroDuration.totalDurationMs)
        assertEquals(0L, zeroDuration.imageDurationMs)
        assertEquals(0L, zeroDuration.transitionOverlapMs)
        assertEquals(emptyList<Long>(), zeroDuration.transitionPointsMs)
    }
}
