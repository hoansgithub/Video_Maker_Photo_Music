package com.videomaker.aimusic.domain.usecase

import com.videomaker.aimusic.domain.model.MusicSong
import com.videomaker.aimusic.domain.repository.SongRepository

/**
 * GetStationSongsUseCase - Retrieves random songs for radio station mode
 *
 * Returns a selection of random songs for discovery and variety.
 */
class GetStationSongsUseCase(
    private val repository: SongRepository
) {
    /**
     * Get random songs for station playback
     * @param limit Maximum number of songs to return (default: 10)
     * @return Result containing list of random songs
     */
    suspend operator fun invoke(limit: Int = 10): Result<List<MusicSong>> =
        repository.getRandomSongs(limit)
}
