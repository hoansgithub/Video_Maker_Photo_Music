package com.videomaker.aimusic.modules.onboardingsurvey

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class OnboardingSurveyViewModel : ViewModel() {

    private val _selectedFeatures = MutableStateFlow<Set<String>>(emptySet())
    val selectedFeatures: StateFlow<Set<String>> = _selectedFeatures.asStateFlow()

    private val _selectedPlatforms = MutableStateFlow<Set<String>>(emptySet())
    val selectedPlatforms: StateFlow<Set<String>> = _selectedPlatforms.asStateFlow()

    // AI_LEVEL is single-select with no pre-selection (analytics-only screen):
    // empty until the user taps an item.
    private val _selectedAiLevel = MutableStateFlow<Set<String>>(emptySet())
    val selectedAiLevel: StateFlow<Set<String>> = _selectedAiLevel.asStateFlow()

    private val _selectedFaceSwap = MutableStateFlow<Set<String>>(emptySet())
    val selectedFaceSwap: StateFlow<Set<String>> = _selectedFaceSwap.asStateFlow()

    private val _selectedDanceSwap = MutableStateFlow<Set<String>>(emptySet())
    val selectedDanceSwap: StateFlow<Set<String>> = _selectedDanceSwap.asStateFlow()

    // Non-AI screens have no user selection (analytics-only): always empty.
    private val _selectedNonAiLyric = MutableStateFlow<Set<String>>(emptySet())
    val selectedNonAiLyric: StateFlow<Set<String>> = _selectedNonAiLyric.asStateFlow()

    private val _selectedNonAiMusicVideo = MutableStateFlow<Set<String>>(emptySet())
    val selectedNonAiMusicVideo: StateFlow<Set<String>> = _selectedNonAiMusicVideo.asStateFlow()

    private val autoSelectedIds = mutableSetOf<String>()

    fun selectAiLevel(id: String) { _selectedAiLevel.value = setOf(id) }

    fun selectedFlow(step: OnboardingSurveyStep): StateFlow<Set<String>> = when (step) {
        OnboardingSurveyStep.FEATURE -> selectedFeatures
        OnboardingSurveyStep.PLATFORM -> selectedPlatforms
        OnboardingSurveyStep.AI_LEVEL -> selectedAiLevel
        OnboardingSurveyStep.AI_FACE_SWAP -> selectedFaceSwap
        OnboardingSurveyStep.AI_DANCE -> selectedDanceSwap
        OnboardingSurveyStep.NON_AI_LYRIC -> selectedNonAiLyric
        OnboardingSurveyStep.NON_AI_MUSIC_VIDEO -> selectedNonAiMusicVideo
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
            OnboardingSurveyStep.AI_FACE_SWAP -> _selectedFaceSwap
            OnboardingSurveyStep.AI_DANCE -> _selectedDanceSwap
            OnboardingSurveyStep.NON_AI_LYRIC -> _selectedNonAiLyric
            OnboardingSurveyStep.NON_AI_MUSIC_VIDEO -> _selectedNonAiMusicVideo
        }
        val current = flow.value
        var nextSelection = current
        val autoSelectedInCurrent = current.filter { it in autoSelectedIds }
        if (autoSelectedInCurrent.isNotEmpty()) {
            if (id in autoSelectedIds) {
                autoSelectedIds.remove(id)
            } else {
                nextSelection = current - autoSelectedInCurrent.toSet()
                autoSelectedIds.removeAll(autoSelectedInCurrent.toSet())
            }
        }
        val nowSelected = id !in nextSelection
        flow.value = if (nowSelected) nextSelection + id else nextSelection - id
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
        autoSelectedIds.add(firstId)
        return true
    }

}

