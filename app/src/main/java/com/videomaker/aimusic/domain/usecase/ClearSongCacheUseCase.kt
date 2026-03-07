package com.videomaker.aimusic.domain.usecase

import com.videomaker.aimusic.domain.repository.SongRepository

/**
 * ClearSongCacheUseCase - Clears the song cache
 *
 * Removes all cached song data from local storage. Useful for
 * freeing up space or refreshing song data.
 */
class ClearSongCacheUseCase(
    private val repository: SongRepository
) {
    /**
     * Clear all cached songs
     * @return Result indicating success or failure
     */
    suspend operator fun invoke(): Result<Unit> {
        return try {
            repository.clearCache()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
