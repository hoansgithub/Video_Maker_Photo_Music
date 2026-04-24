package com.videomaker.aimusic.media.composition

import com.videomaker.aimusic.domain.model.BeatSyncData
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for BeatSyncTimingCalculator
 *
 * Verifies:
 * 1. Correct transition point selection (every 4th beat)
 * 2. Transition duration calculation (min(60000/BPM, 1000))
 * 3. Clip hold duration calculation
 * 4. Fadeout hold duration (6 beats)
 * 5. Edge cases (empty beats, single image, etc.)
 */
class BeatSyncTimingCalculatorTest {

    private val calculator = BeatSyncTimingCalculator()

    @Test
    fun `test transition points at every 4th beat`() {
        // Song with 16 beats at 120 BPM
        val beatData = BeatSyncData(
            beats = (0 until 16).map { it * 0.5 }, // Every 500ms
            bpm = 120.0,
            numBeats = 16
        )

        val clips = calculator.calculateClips(
            beatData = beatData,
            imageSequence = listOf(0, 1, 2, 3), // 4 images
            trimStartMs = 0L,
            trimEndMs = null,
            numShaders = 1
        )

        // Should have 4 clips (3 with transitions + 1 fadeout)
        assertEquals(4, clips.size)

        // First 3 clips should have transitions
        assertTrue(clips[0].hasTransition)
        assertTrue(clips[1].hasTransition)
        assertTrue(clips[2].hasTransition)

        // Last clip should NOT have transition (fadeout)
        assertFalse(clips[3].hasTransition)
    }

    @Test
    fun `test transition duration capping at 1000ms`() {
        // Slow song (60 BPM) - beat interval = 1000ms
        val slowBeatData = BeatSyncData(
            beats = (0 until 20).map { it * 1.0 }, // Every 1000ms
            bpm = 60.0,
            numBeats = 20
        )

        val slowClips = calculator.calculateClips(
            beatData = slowBeatData,
            imageSequence = listOf(0, 1, 2),
            trimStartMs = 0L,
            trimEndMs = null,
            numShaders = 1
        )

        // Transition duration should be capped at 1000ms
        assertEquals(1000L, slowClips[0].transitionDurationMs)

        // Fast song (150 BPM) - beat interval = 400ms
        val fastBeatData = BeatSyncData(
            beats = (0 until 20).map { it * 0.4 }, // Every 400ms
            bpm = 150.0,
            numBeats = 20
        )

        val fastClips = calculator.calculateClips(
            beatData = fastBeatData,
            imageSequence = listOf(0, 1, 2),
            trimStartMs = 0L,
            trimEndMs = null,
            numShaders = 1
        )

        // Transition duration should be 400ms (60000/150 = 400)
        assertEquals(400L, fastClips[0].transitionDurationMs)
    }

    @Test
    fun `test fadeout hold duration is 6 beats`() {
        val beatData = BeatSyncData(
            beats = (0 until 20).map { it * 0.5 }, // Every 500ms at 120 BPM
            bpm = 120.0,
            numBeats = 20
        )

        val clips = calculator.calculateClips(
            beatData = beatData,
            imageSequence = listOf(0, 1, 2),
            trimStartMs = 0L,
            trimEndMs = null,
            numShaders = 1
        )

        // Last clip should have 6 beats of hold duration
        val lastClip = clips.last()
        val expectedFadeoutMs = (60000.0 / beatData.bpm * 6).toLong() // 3000ms for 120 BPM

        assertEquals(expectedFadeoutMs, lastClip.holdDurationMs)
        assertEquals(0L, lastClip.transitionDurationMs) // No transition for fadeout
    }

    @Test
    fun `test real song data - 153 BPM`() {
        // Real data from song 16030 (Seu Brilho Sumiu)
        val beatData = BeatSyncData(
            beats = listOf(0.11, 0.51, 0.91, 1.31, 1.72, 2.12, 2.52, 2.91, 3.31, 3.71),
            bpm = 153.3,
            numBeats = 10
        )

        val clips = calculator.calculateClips(
            beatData = beatData,
            imageSequence = listOf(0, 1),
            trimStartMs = 0L,
            trimEndMs = null,
            numShaders = 1
        )

        // Expected transition duration: min(60000/153.3, 1000) = 391ms
        val expectedTransitionMs = 391L

        assertEquals(2, clips.size)
        assertEquals(expectedTransitionMs, clips[0].transitionDurationMs)

        // Fadeout should be 6 beats from precise beat interval
        // beatMs = 60000/153.3 = 391.386ms -> fadeout = 391.386 * 6 = 2348ms
        assertEquals(2348L, clips[1].holdDurationMs)
    }

    @Test
    fun `test trim start offset`() {
        val beatData = BeatSyncData(
            beats = (0 until 20).map { it * 0.5 }, // Every 500ms
            bpm = 120.0,
            numBeats = 20
        )

        // Start from 2 seconds into the song
        val clips = calculator.calculateClips(
            beatData = beatData,
            imageSequence = listOf(0, 1, 2),
            trimStartMs = 2000L,
            trimEndMs = null,
            numShaders = 1
        )

        // All beat positions should be offset by -2000ms
        // Beat at 2000ms becomes beat at 0ms
        assertTrue(clips.isNotEmpty())
    }

    @Test
    fun `test shader index cycling`() {
        val beatData = BeatSyncData(
            beats = (0 until 20).map { it * 0.5 },
            bpm = 120.0,
            numBeats = 20
        )

        // 5 images with 3 shaders available
        val clips = calculator.calculateClips(
            beatData = beatData,
            imageSequence = listOf(0, 1, 2, 3, 4),
            trimStartMs = 0L,
            trimEndMs = null,
            numShaders = 3
        )

        // Shader indices should cycle: 0, 1, 2, 0, (fadeout)
        assertEquals(0, clips[0].transitionShaderIndex)
        assertEquals(1, clips[1].transitionShaderIndex)
        assertEquals(2, clips[2].transitionShaderIndex)
        assertEquals(0, clips[3].transitionShaderIndex) // Cycles back
    }

    @Test
    fun `test total duration calculation`() {
        val beatData = BeatSyncData(
            beats = (0 until 16).map { it * 0.5 }, // Every 500ms at 120 BPM
            bpm = 120.0,
            numBeats = 16
        )

        val clips = calculator.calculateClips(
            beatData = beatData,
            imageSequence = listOf(0, 1, 2),
            trimStartMs = 0L,
            trimEndMs = null,
            numShaders = 1
        )

        // Each clip's total duration should be hold + transition
        clips.dropLast(1).forEach { clip ->
            assertEquals(
                clip.holdDurationMs + clip.transitionDurationMs,
                clip.totalDurationMs
            )
        }

        // Last clip (fadeout) should have only hold duration
        val lastClip = clips.last()
        assertEquals(lastClip.holdDurationMs, lastClip.totalDurationMs)
    }

    @Test
    fun `test edge case - single image`() {
        val beatData = BeatSyncData(
            beats = (0 until 20).map { it * 0.5 },
            bpm = 120.0,
            numBeats = 20
        )

        val clips = calculator.calculateClips(
            beatData = beatData,
            imageSequence = listOf(0), // Only 1 image
            trimStartMs = 0L,
            trimEndMs = null,
            numShaders = 1
        )

        // Should have 1 clip with fadeout (no transition)
        assertEquals(1, clips.size)
        assertFalse(clips[0].hasTransition)
        assertEquals(3000L, clips[0].holdDurationMs) // 6 beats at 120 BPM
    }

    @Test
    fun `test edge case - insufficient beats`() {
        // Only 2 beats available
        val beatData = BeatSyncData(
            beats = listOf(0.0, 0.5),
            bpm = 120.0,
            numBeats = 2
        )

        val clips = calculator.calculateClips(
            beatData = beatData,
            imageSequence = listOf(0, 1, 2),
            trimStartMs = 0L,
            trimEndMs = null,
            numShaders = 1
        )

        // Should still generate clips, but might have fewer transitions
        assertTrue(clips.isNotEmpty())
    }
}
