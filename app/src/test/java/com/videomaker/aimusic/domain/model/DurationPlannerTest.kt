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
    fun `plan handles one image without transitions`() {
        val plan = DurationPlanner.plan(
            imageCount = 1,
            totalDurationMs = 12_000L
        )

        assertEquals(12_000L, plan.totalDurationMs)
        assertEquals(12_000L, plan.imageDurationMs)
        assertEquals(0L, plan.transitionOverlapMs)
        assertEquals(0, plan.transitionPointsMs.size)
    }

    @Test
    fun `plan recomposes suggested totals deterministically`() {
        assertPlan(
            imageCount = 3,
            requestedTotalDurationMs = 15_000L,
            expectedRecomposedTotalDurationMs = 14_999L,
            expectedTransitionPointsMs = listOf(5_869L, 11_738L)
        )
        assertPlan(
            imageCount = 4,
            requestedTotalDurationMs = 18_000L,
            expectedRecomposedTotalDurationMs = 18_002L,
            expectedTransitionPointsMs = listOf(4_966L, 9_932L, 14_898L)
        )
        assertPlan(
            imageCount = 5,
            requestedTotalDurationMs = 20_000L,
            expectedRecomposedTotalDurationMs = 19_998L,
            expectedTransitionPointsMs = listOf(5_454L, 10_908L, 16_362L, 21_816L)
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

    private fun assertPlan(
        imageCount: Int,
        requestedTotalDurationMs: Long,
        expectedRecomposedTotalDurationMs: Long,
        expectedTransitionPointsMs: List<Long>
    ) {
        val plan = DurationPlanner.plan(
            imageCount = imageCount,
            totalDurationMs = requestedTotalDurationMs
        )

        assertEquals(expectedRecomposedTotalDurationMs, plan.totalDurationMs)
        assertEquals(expectedTransitionPointsMs.size, plan.transitionPointsMs.size)
        assertEquals(expectedTransitionPointsMs, plan.transitionPointsMs)
        assertTrue(plan.transitionPointsMs.zipWithNext().all { (left, right) -> left < right })
        assertEquals(
            plan.totalDurationMs,
            imageCount * plan.imageDurationMs + (imageCount - 1) * plan.transitionOverlapMs
        )
        assertEquals(
            plan.transitionOverlapMs,
            plan.imageDurationMs * 2 * plan.transitionPercentage / 100
        )
    }
}
