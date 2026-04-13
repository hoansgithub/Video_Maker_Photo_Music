package com.videomaker.aimusic.modules.editor.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PreviewLoopPolicyTest {

    @Test
    fun `trim preferred over detected`() {
        val duration = PreviewLoopPolicy.resolveSegmentDurationMs(
            trimStartMs = 10_000L,
            trimEndMs = 16_500L,
            detectedDurationMs = 2_000L,
            videoDurationMs = 60_000L
        )

        assertEquals(6_500L, duration)
    }

    @Test
    fun `reversed trim input falls back to detected duration`() {
        val duration = PreviewLoopPolicy.resolveSegmentDurationMs(
            trimStartMs = 10_000L,
            trimEndMs = 9_000L,
            detectedDurationMs = 2_250L,
            videoDurationMs = 60_000L
        )

        assertEquals(2_250L, duration)
    }

    @Test
    fun `fallback to video duration when trim is invalid and detected is absent`() {
        val duration = PreviewLoopPolicy.resolveSegmentDurationMs(
            trimStartMs = 10_000L,
            trimEndMs = 9_000L,
            detectedDurationMs = 0L,
            videoDurationMs = 60_000L
        )

        assertEquals(60_000L, duration)
    }

    @Test
    fun `shouldLoopAudio returns true when segment is shorter than video`() {
        assertTrue(PreviewLoopPolicy.shouldLoopAudio(segmentDurationMs = 30_000L, videoDurationMs = 60_000L))
    }

    @Test
    fun `shouldLoopAudio returns false when segment equals video`() {
        assertFalse(PreviewLoopPolicy.shouldLoopAudio(segmentDurationMs = 60_000L, videoDurationMs = 60_000L))
    }

    @Test
    fun `shouldLoopAudio returns false when segment duration is invalid`() {
        assertFalse(PreviewLoopPolicy.shouldLoopAudio(segmentDurationMs = 0L, videoDurationMs = 60_000L))
    }

    @Test
    fun `shouldLoopAudio returns false when video duration is invalid`() {
        assertFalse(PreviewLoopPolicy.shouldLoopAudio(segmentDurationMs = 30_000L, videoDurationMs = 0L))
    }

    @Test
    fun `modulo mapping while looping`() {
        val mappedPosition = PreviewLoopPolicy.mapVideoToAudioPosition(
            videoPositionMs = 125_000L,
            segmentDurationMs = 30_000L,
            videoDurationMs = 60_000L
        )

        assertEquals(5_000L, mappedPosition)
    }

    @Test
    fun `direct mapping while no loop`() {
        val mappedPosition = PreviewLoopPolicy.mapVideoToAudioPosition(
            videoPositionMs = 12_500L,
            segmentDurationMs = 60_000L,
            videoDurationMs = 60_000L
        )

        assertEquals(12_500L, mappedPosition)
    }

    @Test
    fun `negative video position clamps to zero`() {
        val mappedPosition = PreviewLoopPolicy.mapVideoToAudioPosition(
            videoPositionMs = -12_500L,
            segmentDurationMs = 60_000L,
            videoDurationMs = 60_000L
        )

        assertEquals(0L, mappedPosition)
    }

    @Test
    fun `shouldLoopPreviewAtEnd boundary cases`() {
        assertFalse(
            PreviewLoopPolicy.shouldLoopPreviewAtEnd(
                currentVideoPositionMs = 59_999L,
                videoDurationMs = 60_000L,
                isPlaying = true
            )
        )
        assertTrue(
            PreviewLoopPolicy.shouldLoopPreviewAtEnd(
                currentVideoPositionMs = 60_000L,
                videoDurationMs = 60_000L,
                isPlaying = true
            )
        )
        assertFalse(
            PreviewLoopPolicy.shouldLoopPreviewAtEnd(
                currentVideoPositionMs = 60_000L,
                videoDurationMs = 60_000L,
                isPlaying = false
            )
        )
    }

    @Test
    fun `large position mapping stability`() {
        val mappedPosition = PreviewLoopPolicy.mapVideoToAudioPosition(
            videoPositionMs = 1_000_000_125L,
            segmentDurationMs = 30_000L,
            videoDurationMs = 60_000L
        )

        assertEquals(125L, mappedPosition)
    }

    @Test
    fun `invalid video duration returns false for shouldLoopPreviewAtEnd`() {
        assertFalse(
            PreviewLoopPolicy.shouldLoopPreviewAtEnd(
                currentVideoPositionMs = 10L,
                videoDurationMs = 0L,
                isPlaying = true
            )
        )
    }
}
