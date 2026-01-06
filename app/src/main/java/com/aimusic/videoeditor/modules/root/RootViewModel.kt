package com.aimusic.videoeditor.modules.root

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.lang.ref.WeakReference
import com.aimusic.videoeditor.core.data.local.LanguageManager
import com.aimusic.videoeditor.modules.language.domain.usecase.CheckLanguageSelectedUseCase
import com.aimusic.videoeditor.modules.language.domain.usecase.InitializeLanguageUseCase
import com.aimusic.videoeditor.modules.onboarding.domain.usecase.CheckOnboardingStatusUseCase
import com.aimusic.videoeditor.modules.onboarding.domain.usecase.CompleteOnboardingUseCase
import com.aimusic.videoeditor.navigation.AppRoute
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * RootViewModel - Root state machine for Single-Activity Architecture
 *
 * Manages the entire app state machine:
 * - Loading: Check onboarding status
 * - Onboarding: First-time user flow
 * - Home: Main app experience
 *
 * Usage in MainActivity:
 * ```kotlin
 * private val rootViewModel: RootViewModel by viewModel()
 * rootViewModel.initializeApp(this)
 * ```
 */
class RootViewModel(
    private val checkOnboardingStatusUseCase: CheckOnboardingStatusUseCase,
    private val completeOnboardingUseCase: CompleteOnboardingUseCase,
    private val checkLanguageSelectedUseCase: CheckLanguageSelectedUseCase,
    private val initializeLanguageUseCase: InitializeLanguageUseCase,
    private val languageManager: LanguageManager
) : ViewModel() {

    // ============================================
    // STATE
    // ============================================

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _loadingMessage = MutableStateFlow("Initializing...")
    val loadingMessage: StateFlow<String> = _loadingMessage.asStateFlow()

    // ============================================
    // NAVIGATION EVENTS (StateFlow-based - Google recommended)
    // ============================================

    private val _navigationEvent = MutableStateFlow<RootNavigationEvent?>(null)
    val navigationEvent: StateFlow<RootNavigationEvent?> = _navigationEvent.asStateFlow()

    // ============================================
    // INTERNAL STATE
    // ============================================

    private var isInitialized = false
    private var activityRef: WeakReference<Activity>? = null
    private var shouldShowLanguageSelection = false
    private var shouldShowOnboarding = false

    // ============================================
    // PUBLIC API
    // ============================================

    /**
     * Initialize app - MUST be called from Activity onCreate
     *
     * This handles:
     * 1. Check onboarding status
     * 2. Navigation to appropriate screen
     *
     * @param activity Activity context
     */
    fun initializeApp(activity: Activity) {
        activityRef = WeakReference(activity)

        // Check if this is a recreation due to locale change
        if (languageManager.isPendingLocaleRecreation()) {
            languageManager.clearPendingLocaleRecreation()

            // Re-check status and navigate (skip loading delay)
            viewModelScope.launch {
                reloadAndNavigate()
            }
            return
        }

        if (isInitialized) return

        isInitialized = true

        viewModelScope.launch {
            try {
                loadInitialData()
            } catch (e: Exception) {
                proceedToNextScreen()
            }
        }
    }

    /**
     * Reload status and navigate after locale change (skip delay)
     */
    private suspend fun reloadAndNavigate() {
        // Check if language has been selected
        val languageResult = checkLanguageSelectedUseCase()
        shouldShowLanguageSelection = languageResult.getOrNull() ?: false

        // Check onboarding status
        val onboardingResult = checkOnboardingStatusUseCase()
        shouldShowOnboarding = onboardingResult.getOrNull() ?: false

        proceedToNextScreen()
    }

    /**
     * Called when user completes language selection (presses Continue)
     * Language is already applied when user tapped an option
     */
    fun onLanguageSelectionComplete() {
        shouldShowLanguageSelection = false

        // After language is selected, check if onboarding is needed
        if (shouldShowOnboarding) {
            _navigationEvent.value = RootNavigationEvent.NavigateTo(AppRoute.Onboarding)
        } else {
            navigateToHome()
        }
    }

    /**
     * Complete onboarding and navigate to home
     * Called when user finishes onboarding
     */
    fun completeOnboarding() {
        viewModelScope.launch {
            completeOnboardingUseCase()
            navigateToHome()
        }
    }

    /**
     * Navigate to home screen
     */
    fun navigateToHome() {
        _navigationEvent.value = RootNavigationEvent.NavigateTo(AppRoute.Home)
    }

    /**
     * Called by UI after navigation is handled - clears the event
     */
    fun onNavigationHandled() {
        _navigationEvent.value = null
    }

    /**
     * Get Activity reference
     * Returns null if Activity is not available
     */
    fun getActivityRef(): Activity? = activityRef?.get()

    /**
     * Update Activity reference
     * Called when Activity is recreated
     */
    fun updateActivityRef(activity: Activity) {
        activityRef = WeakReference(activity)
    }

    /**
     * Clear Activity reference
     * Called when Activity is destroyed
     */
    fun clearActivityRef() {
        activityRef?.clear()
        activityRef = null
    }

    // ============================================
    // PRIVATE: INITIALIZATION
    // ============================================

    private suspend fun loadInitialData() {
        _isLoading.value = true
        _loadingMessage.value = "Loading..."

        try {
            // Short delay for splash screen display
            delay(500)

            // Initialize language settings (restore previously selected language)
            initializeLanguageUseCase()

            // Check if language has been selected (first-time user check)
            val languageResult = checkLanguageSelectedUseCase()
            shouldShowLanguageSelection = languageResult.getOrNull() ?: false

            // Check onboarding status
            val onboardingResult = checkOnboardingStatusUseCase()
            shouldShowOnboarding = onboardingResult.getOrNull() ?: false

            proceedToNextScreen()
        } catch (_: Exception) {
            proceedToNextScreen()
        }
    }

    private fun proceedToNextScreen() {
        _isLoading.value = false

        when {
            shouldShowLanguageSelection -> {
                _navigationEvent.value = RootNavigationEvent.NavigateTo(AppRoute.LanguageSelection)
            }
            shouldShowOnboarding -> {
                _navigationEvent.value = RootNavigationEvent.NavigateTo(AppRoute.Onboarding)
            }
            else -> {
                _navigationEvent.value = RootNavigationEvent.NavigateTo(AppRoute.Home)
            }
        }
    }

    // ============================================
    // CLEANUP
    // ============================================

    override fun onCleared() {
        super.onCleared()
        activityRef?.clear()
        activityRef = null
    }
}

// ============================================
// NAVIGATION EVENTS
// ============================================

/**
 * Root navigation events - one-time events for navigation
 */
sealed class RootNavigationEvent {
    /**
     * Navigate to a specific route
     */
    data class NavigateTo(val route: AppRoute) : RootNavigationEvent()

    /**
     * Navigate back (pop back stack)
     */
    data object NavigateBack : RootNavigationEvent()
}
