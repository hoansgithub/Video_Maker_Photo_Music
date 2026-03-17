package com.videomaker.aimusic.modules.onboarding

import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import com.videomaker.aimusic.modules.onboarding.repository.OnboardingRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * OnboardingViewModel — manages step navigation and genre selection state.
 *
 * Steps: WELCOME (pages 1-3 via HorizontalPager) → GENRE_SELECTION
 */
class OnboardingViewModel(
    private val onboardingRepository: OnboardingRepository
) : ViewModel() {

    private val _currentStep = MutableStateFlow(OnboardingStep.WELCOME)
    val currentStep: StateFlow<OnboardingStep> = _currentStep.asStateFlow()

    val selectedGenres = mutableStateListOf<String>()

    fun onWelcomeComplete() {
        _currentStep.value = OnboardingStep.GENRE_SELECTION
    }

    /**
     * Returns true if stepped back to WELCOME, false if already at first step
     * (caller should handle exit in that case).
     */
    fun onBack(): Boolean {
        return if (_currentStep.value != OnboardingStep.WELCOME) {
            _currentStep.value = OnboardingStep.WELCOME
            true
        } else false
    }

    fun toggleGenre(genre: String) {
        if (selectedGenres.contains(genre)) selectedGenres.remove(genre)
        else selectedGenres.add(genre)
    }

    fun saveGenres() {
        onboardingRepository.savePreferredGenres(selectedGenres.toList())
    }
}