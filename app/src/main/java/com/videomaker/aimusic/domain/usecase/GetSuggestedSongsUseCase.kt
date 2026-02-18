package com.videomaker.aimusic.domain.usecase

import com.videomaker.aimusic.domain.model.MusicSong
import com.videomaker.aimusic.domain.repository.SongRepository
import com.videomaker.aimusic.modules.onboarding.repository.OnboardingRepository

class GetSuggestedSongsUseCase(
    private val songRepository: SongRepository,
    private val onboardingRepository: OnboardingRepository
) {
    suspend operator fun invoke(limit: Int = 10): Result<List<MusicSong>> {
        val preferredGenres = onboardingRepository.getPreferredGenres()
        return songRepository.getSuggestedSongs(preferredGenres, limit)
    }
}
