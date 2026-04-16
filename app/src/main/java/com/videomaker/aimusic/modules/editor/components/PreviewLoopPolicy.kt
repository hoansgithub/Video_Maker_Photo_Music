package com.videomaker.aimusic.modules.editor.components

object PreviewLoopPolicy {

    // Ignore tiny duration deltas caused by timing/decoder jitter to avoid accidental loop toggles.
    private const val LOOP_COMPARISON_TOLERANCE_MS = 50L

    fun resolveSegmentDurationMs(
        trimStartMs: Long,
        trimEndMs: Long,
        detectedDurationMs: Long,
        videoDurationMs: Long
    ): Long {
        if (trimEndMs > trimStartMs) {
            return trimEndMs - trimStartMs
        }

        if (detectedDurationMs > 0L) {
            return detectedDurationMs
        }

        return maxOf(videoDurationMs, 1L)
    }

    fun shouldLoopAudio(segmentDurationMs: Long, videoDurationMs: Long): Boolean {
        return segmentDurationMs > 0L &&
            videoDurationMs > 0L &&
            segmentDurationMs + LOOP_COMPARISON_TOLERANCE_MS < videoDurationMs
    }

    fun mapVideoToAudioPosition(
        videoPositionMs: Long,
        segmentDurationMs: Long,
        videoDurationMs: Long
    ): Long {
        val normalizedVideoPositionMs = videoPositionMs.coerceAtLeast(0L)

        return if (shouldLoopAudio(segmentDurationMs, videoDurationMs)) {
            normalizedVideoPositionMs % segmentDurationMs
        } else {
            normalizedVideoPositionMs
        }
    }

    fun shouldLoopPreviewAtEnd(
        currentVideoPositionMs: Long,
        videoDurationMs: Long,
        isPlaying: Boolean
    ): Boolean {
        // Loop only after crossing the end boundary.
        // Exact-boundary handling is owned by Player.STATE_ENDED callback to avoid double-restart races.
        return isPlaying && videoDurationMs > 0L && currentVideoPositionMs > videoDurationMs
    }
}
