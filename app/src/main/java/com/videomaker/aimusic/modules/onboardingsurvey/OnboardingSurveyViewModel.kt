package com.videomaker.aimusic.modules.onboardingsurvey

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class OnboardingSurveyViewModel : ViewModel() {

    private val _selectedFeatures = MutableStateFlow<Set<String>>(emptySet())
    val selectedFeatures: StateFlow<Set<String>> = _selectedFeatures.asStateFlow()

    private val _selectedPlatforms = MutableStateFlow<Set<String>>(emptySet())
    val selectedPlatforms: StateFlow<Set<String>> = _selectedPlatforms.asStateFlow()

    // AI_LEVEL is single-select with no pre-selection (analytics-only screen):
    // empty until the user taps an item.
    private val _selectedAiLevel = MutableStateFlow<Set<String>>(emptySet())
    val selectedAiLevel: StateFlow<Set<String>> = _selectedAiLevel.asStateFlow()

    fun selectAiLevel(id: String) { _selectedAiLevel.value = setOf(id) }

    fun selectedFlow(step: OnboardingSurveyStep): StateFlow<Set<String>> = when (step) {
        OnboardingSurveyStep.FEATURE -> selectedFeatures
        OnboardingSurveyStep.PLATFORM -> selectedPlatforms
        OnboardingSurveyStep.AI_LEVEL -> selectedAiLevel
    }

    /** Toggles selection. Returns true if [id] is now selected (used to decide select-tracking / ad reload). */
    fun toggle(step: OnboardingSurveyStep, id: String): Boolean {
        if (step == OnboardingSurveyStep.AI_LEVEL) {
            selectAiLevel(id)
            return true
        }
        val flow = when (step) {
            OnboardingSurveyStep.FEATURE -> _selectedFeatures
            OnboardingSurveyStep.PLATFORM -> _selectedPlatforms
            OnboardingSurveyStep.AI_LEVEL -> _selectedFeatures // unreachable (handled above)
        }
        val current = flow.value
        val nowSelected = id !in current
        flow.value = if (nowSelected) current + id else current - id
        return nowSelected
    }

    /**
     * Auto-selects [firstId] for [step] if nothing is currently selected.
     * Returns true if a selection was actually made.
     */
    fun autoSelectFirst(step: OnboardingSurveyStep, firstId: String): Boolean {
        val flow = when (step) {
            OnboardingSurveyStep.FEATURE -> _selectedFeatures
            OnboardingSurveyStep.PLATFORM -> _selectedPlatforms
            else -> return false
        }
        if (flow.value.isNotEmpty()) return false
        flow.value = setOf(firstId)
        return true
    }

}
