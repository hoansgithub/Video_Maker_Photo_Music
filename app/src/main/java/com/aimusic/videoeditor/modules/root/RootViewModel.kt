package com.aimusic.videoeditor.modules.root

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.lang.ref.WeakReference
import co.alcheclub.lib.acccore.remoteconfig.RemoteConfig
import com.aimusic.videoeditor.modules.language.domain.usecase.CheckLanguageSelectedUseCase
import com.aimusic.videoeditor.modules.onboarding.domain.usecase.CheckOnboardingStatusUseCase
import com.aimusic.videoeditor.navigation.AppRoute
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * RootViewModel - Root state machine for app initialization
 *
 * Manages the entire app initialization flow:
 * - Loading: Initialize services, load config, check status
 * - Language Selection: First-time language picker (separate Activity)
 * - Onboarding: First-time user tutorial
 * - Home: Main app experience
 *
 * Firebase Integration:
 * - Remote Config: Fetched and activated during initialization
 *
 * Placeholder for future features:
 * - AdMob initialization
 * - App Open ad presentation
 *
 * Usage in RootViewActivity:
 * ```kotlin
 * private val rootViewModel: RootViewModel by viewModel()
 * rootViewModel.initializeApp(this)
 * ```
 */
class RootViewModel(
    private val checkOnboardingStatusUseCase: CheckOnboardingStatusUseCase,
    private val checkLanguageSelectedUseCase: CheckLanguageSelectedUseCase,
    private val remoteConfig: RemoteConfig
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

    @Volatile
    private var activityRef: WeakReference<Activity>? = null

    @Volatile
    private var shouldShowLanguageSelection = false

    @Volatile
    private var shouldShowOnboarding = false

    @Volatile
    private var isInitialized = false

    // ============================================
    // PUBLIC API
    // ============================================

    /**
     * Initialize app - MUST be called from RootViewActivity onCreate
     *
     * This handles:
     * 1. AdMob initialization (placeholder for future)
     * 2. Firebase Remote Config fetch and activate
     * 3. App Open ad presentation (placeholder for future)
     * 4. Language selection status check
     * 5. Onboarding status check
     * 6. Navigation to appropriate screen
     *
     * @param activity Activity context required for ads
     */
    fun initializeApp(activity: Activity) {
        activityRef = WeakReference(activity)

        if (isInitialized) {
            // Already initialized, just navigate
            proceedToNextScreen()
            return
        }

        isInitialized = true
        loadInitialData()
    }

    /**
     * Navigate to home screen
     */
    fun navigateToHome() {
        _navigationEvent.value = RootNavigationEvent.NavigateTo(AppRoute.Home)
    }

    /**
     * Clear navigation event after it has been handled
     * MUST be called after processing navigation event
     */
    fun onNavigationHandled() {
        _navigationEvent.value = null
    }

    /**
     * Get Activity reference
     */
    fun getActivityRef(): Activity? = activityRef?.get()

    /**
     * Update Activity reference
     */
    fun updateActivityRef(activity: Activity) {
        activityRef = WeakReference(activity)
    }

    /**
     * Clear Activity reference
     */
    fun clearActivityRef() {
        activityRef?.clear()
        activityRef = null
    }

    // ============================================
    // PRIVATE: INITIALIZATION
    // ============================================

    private fun loadInitialData() {
        viewModelScope.launch {
            _isLoading.value = true

            try {
                // Step 1: Initialize AdMob (placeholder for future)
                _loadingMessage.value = "Initializing..."
                initializeAds()

                // Step 2: Load Firebase Remote Config (10s timeout)
                _loadingMessage.value = "Loading configuration..."
                loadRemoteConfig()

                // Step 3: Present App Open Ad (placeholder for future)
                _loadingMessage.value = "Loading..."
                presentAppOpenAd()

                // Step 4: Check language selection status
                val languageResult = checkLanguageSelectedUseCase()
                shouldShowLanguageSelection = languageResult.getOrNull() ?: false

                // Step 5: Check onboarding status
                val onboardingResult = checkOnboardingStatusUseCase()
                shouldShowOnboarding = onboardingResult.getOrNull() ?: false

                // Step 6: Navigate to appropriate screen
                proceedToNextScreen()

            } catch (e: Exception) {
                android.util.Log.e("RootViewModel", "Initialization error: ${e.message}")
                proceedToNextScreen()
            }
        }
    }

    // ============================================
    // PRIVATE: FIREBASE REMOTE CONFIG
    // ============================================

    /**
     * Load Firebase Remote Config
     * Fetches and activates remote config values with a 10-second timeout.
     * On failure or timeout, continues with cached/default values.
     */
    private suspend fun loadRemoteConfig() {
        try {
            android.util.Log.d("RootViewModel", "Fetching Remote Config...")

            // Fetch with 10 second timeout to prevent blocking on slow networks
            val result = withTimeoutOrNull(10_000L) {
                remoteConfig.fetchAndActivate()
            }

            // fetchAndActivate returns Result<Boolean>
            val success = result?.getOrNull() ?: false
            if (success) {
                android.util.Log.d("RootViewModel", "Remote Config fetched and activated successfully")
            } else {
                android.util.Log.w("RootViewModel", "Remote Config fetch timed out or failed - using cached/default values")
            }
        } catch (e: Exception) {
            android.util.Log.e("RootViewModel", "Remote Config error: ${e.message}")
            // Continue with cached/default values - don't block app startup
        }
    }

    // ============================================
    // PRIVATE: PLACEHOLDERS (for future implementation)
    // ============================================

    /**
     * Initialize AdMob SDK
     * TODO: Implement UMP consent + AdMob initialization
     */
    private suspend fun initializeAds() {
        // Placeholder: Add AdMob initialization here
        delay(100) // Simulate initialization
    }

    /**
     * Present App Open Ad
     * TODO: Implement App Open ad loading and presentation
     */
    private suspend fun presentAppOpenAd() {
        // Placeholder: Add App Open ad presentation here
        delay(100) // Simulate ad presentation
    }

    // ============================================
    // PRIVATE: NAVIGATION
    // ============================================

    private fun proceedToNextScreen() {
        _isLoading.value = false

        when {
            // First priority: Language selection (if not yet selected)
            shouldShowLanguageSelection -> {
                _navigationEvent.value = RootNavigationEvent.NavigateTo(AppRoute.LanguageSelection)
            }
            // Second priority: Onboarding (first-time user after language)
            shouldShowOnboarding -> {
                _navigationEvent.value = RootNavigationEvent.NavigateTo(AppRoute.Onboarding)
            }
            // Default: Go to Home
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
