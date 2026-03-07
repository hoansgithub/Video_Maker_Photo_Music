package com.videomaker.aimusic.domain.usecase

import com.videomaker.aimusic.domain.model.MusicSong
import com.videomaker.aimusic.domain.repository.SongRepository

/**
 * GetWeeklyRankingSongsUseCase - Retrieves weekly featured/trending songs
 *
 * Returns curated or trending songs for the current week, typically
 * displayed in a featured or "top charts" section.
 */
class GetWeeklyRankingSongsUseCase(
    private val repository: SongRepository
) {
    /**
     * Get weekly ranking/featured songs
     * @param limit Maximum number of songs to return (default: 9)
     * @return Result containing list of featured songs
     */
    suspend operator fun invoke(limit: Int = 9): Result<List<MusicSong>> =
        repository.getFeaturedSongs(limit)
}
