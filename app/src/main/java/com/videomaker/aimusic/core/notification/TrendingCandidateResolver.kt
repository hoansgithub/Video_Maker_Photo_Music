package com.videomaker.aimusic.core.notification

import com.videomaker.aimusic.domain.model.MusicSong

object TrendingCandidateResolver {
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
        return topSong
    }
}
