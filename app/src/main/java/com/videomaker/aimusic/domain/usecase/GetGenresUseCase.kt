package com.videomaker.aimusic.domain.usecase

import com.videomaker.aimusic.domain.repository.SongRepository

/**
 * GetGenresUseCase - Retrieves all available music genres
 *
 * Returns a list of genre names that can be used for filtering songs.
 */
class GetGenresUseCase(
    private val repository: SongRepository
) {
    /**
     * Get all available music genres
     * @return Result containing list of genre names
     */
    suspend operator fun invoke(): Result<List<String>> = repository.getGenres()
}
