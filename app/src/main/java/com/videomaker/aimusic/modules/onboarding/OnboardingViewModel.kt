package com.videomaker.aimusic.modules.onboarding

import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import com.videomaker.aimusic.modules.onboarding.repository.OnboardingRepository

/**
 * OnboardingViewModel — thin ViewModel for genre selection state on the onboarding screen.
 *
 * Owns the selectedGenres list and delegates persistence to OnboardingRepository.
 */
class OnboardingViewModel(
    private val onboardingRepository: OnboardingRepository
) : ViewModel() {

    val selectedGenres = mutableStateListOf<String>()

    fun toggleGenre(genre: String) {
        if (selectedGenres.contains(genre)) selectedGenres.remove(genre)
        else selectedGenres.add(genre)
    }

    fun saveGenres() {
        onboardingRepository.savePreferredGenres(selectedGenres.toList())
    }
}