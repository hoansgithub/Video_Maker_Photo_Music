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
    fun `plan returns requested total duration and supported transition bucket`() {
        val plan = DurationPlanner.plan(
            imageCount = 5,
            totalDurationMs = 20_000L
        )

        assertEquals(20_000L, plan.totalDurationMs)
        assertTrue(plan.transitionPercentage in setOf(10, 20, 30, 40, 50))
        assertEquals(4, plan.transitionPointsMs.size)
    }
}
