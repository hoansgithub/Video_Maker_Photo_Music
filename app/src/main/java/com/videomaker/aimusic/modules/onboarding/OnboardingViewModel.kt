package com.videomaker.aimusic.modules.onboarding

import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.videomaker.aimusic.modules.onboarding.repository.OnboardingRepository
import kotlinx.coroutines.launch

/**
 * OnboardingViewModel — manages feature selection state for FeatureSelectionActivity.
 */
class OnboardingViewModel(
    private val onboardingRepository: OnboardingRepository
) : ViewModel() {

    val selectedFeatures = mutableStateListOf<String>()

    fun toggleFeature(feature: String) {
        val alreadySelected = selectedFeatures.contains(feature)
        selectedFeatures.clear()
        if (!alreadySelected) selectedFeatures.add(feature)
    }

    fun saveFeatures(onResult: (Result<Unit>) -> Unit) {
        viewModelScope.launch {
            runCatching {
                onboardingRepository.savePreferredFeatures(selectedFeatures.toList())
            }.onSuccess {
                onResult(Result.success(Unit))
            }.onFailure { throwable ->
                onResult(Result.failure(throwable))
            }
        }
    }
}
