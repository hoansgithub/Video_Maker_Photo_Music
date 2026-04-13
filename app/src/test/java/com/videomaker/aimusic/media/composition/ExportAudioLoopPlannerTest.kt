package com.videomaker.aimusic.media.composition

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportAudioLoopPlannerTest {

    @Test
    fun `segment shorter than video returns loop plan with full loops and remainder`() {
        val plan = ExportAudioLoopPlanner.plan(
            segmentDurationMs = 30_000L,
            totalVideoDurationMs = 95_000L
        )

        assertTrue(plan.shouldLoop)
        assertEquals(3, plan.fullLoops)
        assertEquals(5_000L, plan.remainingMs)
    }

    @Test
    fun `exact multiple still loops with zero remainder`() {
        val plan = ExportAudioLoopPlanner.plan(
            segmentDurationMs = 10_000L,
            totalVideoDurationMs = 30_000L
        )

        assertTrue(plan.shouldLoop)
        assertEquals(3, plan.fullLoops)
        assertEquals(0L, plan.remainingMs)
    }

    @Test
    fun `segment equal or longer than video returns no loop plan`() {
        val equalPlan = ExportAudioLoopPlanner.plan(
            segmentDurationMs = 60_000L,
            totalVideoDurationMs = 60_000L
        )
        val longerPlan = ExportAudioLoopPlanner.plan(
            segmentDurationMs = 90_000L,
            totalVideoDurationMs = 60_000L
        )

        assertFalse(equalPlan.shouldLoop)
        assertEquals(0, equalPlan.fullLoops)
        assertEquals(0L, equalPlan.remainingMs)

        assertFalse(longerPlan.shouldLoop)
        assertEquals(0, longerPlan.fullLoops)
        assertEquals(0L, longerPlan.remainingMs)
    }

    @Test
    fun `invalid segment returns no loop plan`() {
        val zeroSegmentPlan = ExportAudioLoopPlanner.plan(
            segmentDurationMs = 0L,
            totalVideoDurationMs = 60_000L
        )
        val negativeSegmentPlan = ExportAudioLoopPlanner.plan(
            segmentDurationMs = -1L,
            totalVideoDurationMs = 60_000L
        )
        val invalidVideoPlan = ExportAudioLoopPlanner.plan(
            segmentDurationMs = 30_000L,
            totalVideoDurationMs = 0L
        )

        assertFalse(zeroSegmentPlan.shouldLoop)
        assertEquals(0, zeroSegmentPlan.fullLoops)
        assertEquals(0L, zeroSegmentPlan.remainingMs)

        assertFalse(negativeSegmentPlan.shouldLoop)
        assertEquals(0, negativeSegmentPlan.fullLoops)
        assertEquals(0L, negativeSegmentPlan.remainingMs)

        assertFalse(invalidVideoPlan.shouldLoop)
        assertEquals(0, invalidVideoPlan.fullLoops)
        assertEquals(0L, invalidVideoPlan.remainingMs)
    }
}
