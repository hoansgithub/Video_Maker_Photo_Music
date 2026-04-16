package com.videomaker.aimusic.media.composition

data class ExportAudioLoopPlan(
    val shouldLoop: Boolean,
    val fullLoops: Int,
    val remainingMs: Long
)

object ExportAudioLoopPlanner {

    /**
     * Non-positive segment or video durations are treated as invalid and return a no-loop plan.
     */
    fun plan(segmentDurationMs: Long, totalVideoDurationMs: Long): ExportAudioLoopPlan {
        if (segmentDurationMs <= 0L || totalVideoDurationMs <= 0L) {
            return ExportAudioLoopPlan(
                shouldLoop = false,
                fullLoops = 0,
                remainingMs = 0L
            )
        }

        if (segmentDurationMs >= totalVideoDurationMs) {
            return ExportAudioLoopPlan(
                shouldLoop = false,
                fullLoops = 0,
                remainingMs = 0L
            )
        }

        return ExportAudioLoopPlan(
            shouldLoop = true,
            fullLoops = (totalVideoDurationMs / segmentDurationMs).toInt(),
            remainingMs = totalVideoDurationMs % segmentDurationMs
        )
    }
}
