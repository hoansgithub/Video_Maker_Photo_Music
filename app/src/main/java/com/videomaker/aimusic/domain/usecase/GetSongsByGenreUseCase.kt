package com.videomaker.aimusic.domain.usecase

import com.videomaker.aimusic.domain.model.MusicSong
import com.videomaker.aimusic.domain.repository.SongRepository

/**
 * GetSongsByGenreUseCase - Retrieves songs filtered by genre
 *
 * Returns a paginated list of songs matching the specified genre.
 */
class GetSongsByGenreUseCase(
    private val repository: SongRepository
) {
    /**
     * Get songs by genre
     * @param genre Genre name to filter by
     * @param limit Maximum number of songs to return (default: 20)
     * @return Result containing list of songs in the specified genre
     */
    suspend operator fun invoke(genre: String, limit: Int = 20): Result<List<MusicSong>> =
        repository.getSongsByGenre(genre, limit)
}
