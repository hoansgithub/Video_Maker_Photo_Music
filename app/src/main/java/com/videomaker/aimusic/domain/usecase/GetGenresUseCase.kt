package com.videomaker.aimusic.domain.usecase

import com.videomaker.aimusic.domain.model.SongGenre
import com.videomaker.aimusic.domain.repository.SongRepository

/**
 * GetGenresUseCase - Retrieves all available music genres
 *
 * Returns a list of SongGenre objects that can be used for filtering songs.
 */
class GetGenresUseCase(
    private val repository: SongRepository
) {
    /**
     * Get all available music genres
     * @return Result containing list of SongGenre objects with id and displayName
     */
    suspend operator fun invoke(): Result<List<SongGenre>> = repository.getGenres()
}
