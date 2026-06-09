package com.videomaker.aimusic.modules.onboardingsurvey

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.alcheclub.lib.acccore.remoteconfig.RemoteConfig
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class OnboardingSurveyViewModel(
    remoteConfig: RemoteConfig,
) : ViewModel() {

    val enabledSteps: List<OnboardingSurveyStep> = OnboardingSurveyGate.enabledSteps(remoteConfig)

    private val _currentStep = MutableStateFlow(enabledSteps.firstOrNull())
    val currentStep: StateFlow<OnboardingSurveyStep?> = _currentStep.asStateFlow()

    private val _selectedFeatures = MutableStateFlow<Set<String>>(emptySet())
    val selectedFeatures: StateFlow<Set<String>> = _selectedFeatures.asStateFlow()

    private val _selectedPlatforms = MutableStateFlow<Set<String>>(emptySet())
    val selectedPlatforms: StateFlow<Set<String>> = _selectedPlatforms.asStateFlow()

    // AI_LEVEL is single-select with no pre-selection (analytics-only screen):
    // empty until the user taps an item.
    private val _selectedAiLevel = MutableStateFlow<Set<String>>(emptySet())
    val selectedAiLevel: StateFlow<Set<String>> = _selectedAiLevel.asStateFlow()

    fun selectAiLevel(id: String) { _selectedAiLevel.value = setOf(id) }

    // One-time forward navigation event (collected once in the Activity).
    private val _navToNext = Channel<Unit>(Channel.BUFFERED)
    val navToNext = _navToNext.receiveAsFlow()

    init {
        // Defensive: the Activity is only launched when at least one step is enabled,
        // but if it is launched empty, leave immediately.
        if (enabledSteps.isEmpty()) {
            viewModelScope.launch { _navToNext.send(Unit) }
        }
    }

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

    fun onNext() {
        val next = OnboardingSurveyGate.nextStep(enabledSteps, _currentStep.value)
        if (next == null) {
            viewModelScope.launch { _navToNext.send(Unit) }
        } else {
            _currentStep.value = next
        }
    }
}
