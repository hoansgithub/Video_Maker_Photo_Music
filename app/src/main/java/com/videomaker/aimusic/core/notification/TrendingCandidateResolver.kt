package com.videomaker.aimusic.core.notification

import com.videomaker.aimusic.domain.model.MusicSong

object TrendingCandidateResolver {
    const val VIRAL_USAGE_MIN = 100_000

    data class DailySnapshot(
        val localDate: String,
        val songId: Long,
        val usageCount: Int
    )

    fun resolve(
        featuredSongs: List<MusicSong>,
        snapshot: DailySnapshot?,
        currentLocalDate: String
    ): MusicSong? {
        val topSong = featuredSongs.maxByOrNull { it.usageCount } ?: return null
        if (topSong.usageCount < VIRAL_USAGE_MIN) return null

        val shouldBlockBecauseAlreadyShownToday =
            snapshot != null &&
                snapshot.localDate == currentLocalDate &&
                snapshot.songId > 0L &&
                snapshot.songId == topSong.id
        if (shouldBlockBecauseAlreadyShownToday) return null

        return topSong
    }
}
