package com.videomaker.aimusic.domain.usecase

import com.videomaker.aimusic.domain.model.MusicSong
import com.videomaker.aimusic.domain.repository.SongRepository
import com.videomaker.aimusic.modules.onboarding.repository.OnboardingRepository

/**
 * GetSuggestedSongsUseCase - Retrieves personalized song suggestions
 *
 * Returns songs based on user's preferred genres selected during onboarding.
 * Orchestrates data from both song and onboarding repositories.
 */
class GetSuggestedSongsUseCase(
    private val songRepository: SongRepository,
    private val onboardingRepository: OnboardingRepository
) {
    /**
     * Get personalized song suggestions based on user preferences
     * @param limit Maximum number of songs to return (default: 10)
     * @return Result containing list of suggested songs
     */
    suspend operator fun invoke(limit: Int = 10): Result<List<MusicSong>> {
        val preferredGenres = onboardingRepository.getPreferredGenres()
        return songRepository.getSuggestedSongs(preferredGenres, limit)
    }
}
