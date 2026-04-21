package com.videomaker.aimusic.domain.model

data class DurationPlan(
    val totalDurationMs: Long,
    val imageDurationMs: Long,
    val transitionOverlapMs: Long,
    val transitionPercentage: Int,
    val transitionPointsMs: List<Long>
)

object DurationPlanner {
    private val supportedTransitionPercentages = listOf(10, 20, 30, 40, 50)

    fun suggestTotalDurationMs(imageCount: Int): Long {
        return when (imageCount) {
            1, 2 -> 12_000L
            3 -> 15_000L
            4 -> 18_000L
            else -> 20_000L
        }
    }

    fun plan(imageCount: Int, totalDurationMs: Long): DurationPlan {
        if (imageCount <= 0 || totalDurationMs <= 0L) {
            return DurationPlan(
                totalDurationMs = 0L,
                imageDurationMs = 0L,
                transitionOverlapMs = 0L,
                transitionPercentage = 10,
                transitionPointsMs = emptyList()
            )
        }

        val transitionPercentage = snapTransitionPercentage(imageCount)
        val contractWeight = 100L * imageCount + 2L * transitionPercentage * (imageCount - 1)
        val imageDurationMs = totalDurationMs * 100L / contractWeight
        val transitionOverlapMs = imageDurationMs * 2L * transitionPercentage / 100L
        val transitionPointsMs = List(imageCount - 1) { index ->
            (index + 1L) * (imageDurationMs + transitionOverlapMs)
        }

        return DurationPlan(
            totalDurationMs = totalDurationMs,
            imageDurationMs = imageDurationMs,
            transitionOverlapMs = transitionOverlapMs,
            transitionPercentage = transitionPercentage,
            transitionPointsMs = transitionPointsMs
        )
    }

    private fun snapTransitionPercentage(imageCount: Int): Int {
        val targetPercentage = when {
            imageCount <= 1 -> 10
            imageCount == 2 -> 50
            imageCount == 3 -> 40
            imageCount == 4 -> 30
            else -> 20
        }

        return supportedTransitionPercentages.minBy { percentage ->
            kotlin.math.abs(percentage - targetPercentage)
        }
    }
}
