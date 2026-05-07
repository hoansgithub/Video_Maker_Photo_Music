package com.videomaker.aimusic.core.notification

data class NotificationScheduleConfig(
    val trendingHour: Int = DEFAULT_TRENDING_HOUR,
    val trendingMinute: Int = DEFAULT_TRENDING_MINUTE,
    val viralHour: Int = DEFAULT_VIRAL_HOUR,
    val viralMinute: Int = DEFAULT_VIRAL_MINUTE,
    val quickSaveDelayMs: Long = DEFAULT_QUICK_SAVE_DELAY_MS,
    val shareEncouragementDelayMs: Long = DEFAULT_SHARE_ENCOURAGEMENT_DELAY_MS,
    val forgottenMasterpieceDelayMs: Long = DEFAULT_FORGOTTEN_MASTERPIECE_DELAY_MS,
    val abandonedSameDelayMs: Long = DEFAULT_ABANDONED_SAME_DELAY_MS,
    val abandonedColdDelayMs: Long = DEFAULT_ABANDONED_COLD_DELAY_MS,
    val draftCompletionDelayMs: Long = DEFAULT_DRAFT_COMPLETION_DELAY_MS
) {
    fun isFastScheduleMode(): Boolean {
        val maxDelayMs = maxOf(
            quickSaveDelayMs,
            shareEncouragementDelayMs,
            forgottenMasterpieceDelayMs,
            abandonedSameDelayMs,
            abandonedColdDelayMs,
            draftCompletionDelayMs
        )
        return maxDelayMs in 0L..FAST_SCHEDULE_MAX_DELAY_MS
    }

    fun fingerprint(): String {
        return buildString {
            append(trendingHour)
            append(':')
            append(trendingMinute)
            append('|')
            append(viralHour)
            append(':')
            append(viralMinute)
            append('|')
            append(quickSaveDelayMs)
            append('|')
            append(shareEncouragementDelayMs)
            append('|')
            append(forgottenMasterpieceDelayMs)
            append('|')
            append(abandonedSameDelayMs)
            append('|')
            append(abandonedColdDelayMs)
            append('|')
            append(draftCompletionDelayMs)
        }
    }

    companion object {
        private const val FAST_SCHEDULE_MAX_DELAY_MS = 10L * 60_000L
        const val DEFAULT_TRENDING_HOUR = 19
        const val DEFAULT_TRENDING_MINUTE = 2
        const val DEFAULT_VIRAL_HOUR = 20
        const val DEFAULT_VIRAL_MINUTE = 2
        const val DEFAULT_QUICK_SAVE_DELAY_MS = 30L * 60_000L
        const val DEFAULT_SHARE_ENCOURAGEMENT_DELAY_MS = 12L * 60L * 60_000L
        const val DEFAULT_FORGOTTEN_MASTERPIECE_DELAY_MS = 24L * 60L * 60_000L
        const val DEFAULT_ABANDONED_SAME_DELAY_MS = 2L * 60_000L
        const val DEFAULT_ABANDONED_COLD_DELAY_MS = 15L * 60_000L
        const val DEFAULT_DRAFT_COMPLETION_DELAY_MS = 15L * 60_000L
    }
}
