package com.videomaker.aimusic.modules.onboarding

import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.videomaker.aimusic.modules.onboarding.repository.OnboardingRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * OnboardingViewModel — manages step navigation and genre selection state.
 *
 * Steps: WELCOME_1 → WELCOME_2 → WELCOME_3 → GENRE_SELECTION
 */
class OnboardingViewModel(
    private val onboardingRepository: OnboardingRepository
) : ViewModel() {

    private val _currentStep = MutableStateFlow(OnboardingStep.WELCOME_1)
    val currentStep: StateFlow<OnboardingStep> = _currentStep.asStateFlow()

    val selectedFeatures = mutableStateListOf<String>()

    fun goToStep(step: OnboardingStep) {
        _currentStep.value = step
    }

    fun onNext() {
        _currentStep.value = when (_currentStep.value) {
            OnboardingStep.WELCOME_1 -> OnboardingStep.WELCOME_2
            OnboardingStep.WELCOME_2 -> OnboardingStep.WELCOME_3
            OnboardingStep.WELCOME_3 -> OnboardingStep.WELCOME_3
        }
    }

    /**
     * Returns true if stepped back, false if already at first step
     * (caller should show exit dialog).
     */
    fun onBack(): Boolean {
        val previous = when (_currentStep.value) {
            OnboardingStep.WELCOME_1 -> return false
            OnboardingStep.WELCOME_2 -> OnboardingStep.WELCOME_1
            OnboardingStep.WELCOME_3 -> OnboardingStep.WELCOME_2
        }
        _currentStep.value = previous
        return true
    }

    fun toggleFeature(feature: String) {
        val alreadySelected = selectedFeatures.contains(feature)
        selectedFeatures.clear()
        if (!alreadySelected) selectedFeatures.add(feature)
    }

    fun saveFeatures(onSaved: () -> Unit) {
        viewModelScope.launch {
            onboardingRepository.savePreferredFeatures(selectedFeatures.toList())
            onSaved()
        }
    }
}