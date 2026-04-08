package com.videomaker.aimusic.modules.root

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.alcheclub.lib.acccore.remoteconfig.RemoteConfig
import com.videomaker.aimusic.core.analytics.Analytics
import com.videomaker.aimusic.core.analytics.AnalyticsEvent
import com.videomaker.aimusic.core.data.local.PreferencesManager
import com.videomaker.aimusic.modules.language.domain.usecase.CheckLanguageSelectedUseCase
import com.videomaker.aimusic.modules.onboarding.domain.usecase.CheckOnboardingStatusUseCase
import com.videomaker.aimusic.navigation.AppRoute
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
 * - Feature selection: one-time intent picker that sets first Home tab
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
    private val application: Application,
    private val checkOnboardingStatusUseCase: CheckOnboardingStatusUseCase,
    private val checkLanguageSelectedUseCase: CheckLanguageSelectedUseCase,
    private val preferencesManager: PreferencesManager,
    private val remoteConfig: RemoteConfig
) : ViewModel() {

    // ============================================
    // STATE
    // ============================================

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _loadingStep = MutableStateFlow(LoadingStep.INITIALIZING)
    val loadingStep: StateFlow<LoadingStep> = _loadingStep.asStateFlow()

    // ============================================
    // NAVIGATION EVENTS (StateFlow-based - Google recommended)
    // ============================================

    private val _navigationEvent = MutableStateFlow<RootNavigationEvent?>(null)
    val navigationEvent: StateFlow<RootNavigationEvent?> = _navigationEvent.asStateFlow()

    private val _showNoInternetDialog = MutableStateFlow(false)
    val showNoInternetDialog: StateFlow<Boolean> = _showNoInternetDialog.asStateFlow()

    // ============================================
    // INTERNAL STATE
    // ============================================

    @Volatile
    private var needsLanguageSelection = false

    @Volatile
    private var needsOnboarding = false

    @Volatile
    private var needsFeatureSelection = false

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
     */
    fun initializeApp() {
        if (isInitialized) {
            // Already initialized, just navigate
            proceedToNextScreen()
            return
        }

        isInitialized = true
        loadInitialData()
    }

    /**
     * Retry app initialization after a no-internet failure.
     * Resets state so the full initialization runs again.
     */
    fun retryInitialization() {
        _showNoInternetDialog.value = false
        isInitialized = false
        initializeApp()
    }

    /**
     * Dismiss the no-internet dialog without retrying.
     */
    fun dismissNoInternetDialog() {
        _showNoInternetDialog.value = false
    }

    /**
     * Clear navigation event after it has been handled
     * MUST be called after processing navigation event
     */
    fun onNavigationHandled() {
        _navigationEvent.value = null
    }

    // ============================================
    // PRIVATE: INITIALIZATION
    // ============================================

    private fun loadInitialData() {
        viewModelScope.launch {
            _isLoading.value = true
            val startTimeMillis = System.currentTimeMillis()

            // Step 1: Check internet connection
            if (!isInternetAvailable()) {
                _isLoading.value = false
                isInitialized = false
                _showNoInternetDialog.value = true
                return@launch
            }

            try {
                // Step 2: Initialize AdMob (placeholder for future)
                _loadingStep.value = LoadingStep.INITIALIZING
                initializeAds()

                // Step 3: Load Firebase Remote Config (10s timeout)
                _loadingStep.value = LoadingStep.FETCHING_CONFIG
                loadRemoteConfig()

                // Step 4: Present App Open Ad (placeholder for future)
                _loadingStep.value = LoadingStep.LOADING_AD
                presentAppOpenAd()

                // Step 5: Check startup gate status
                _loadingStep.value = LoadingStep.CHECKING_STATUS
                val onboardingResult = checkOnboardingStatusUseCase()
                needsOnboarding = onboardingResult.getOrNull() ?: false
                needsLanguageSelection = checkLanguageSelectedUseCase()
                needsFeatureSelection = !preferencesManager.isFeatureSelectionComplete()

                // Step 6: Track initialization time
                val durationMs = System.currentTimeMillis() - startTimeMillis
                Analytics.track(
                    name = AnalyticsEvent.APP_INIT_TIME,
                    params = mapOf(
                        AnalyticsEvent.Param.VALUE to durationMs
                    )
                )

                // Step 7: Navigate to appropriate screen
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

            // Fetch with 10 second timeout to prevent blocking on slow networks
            val result = withTimeoutOrNull(10_000L) {
                remoteConfig.fetchAndActivate()
            }

            // fetchAndActivate returns Result<Boolean>
            val success = result?.getOrNull() ?: false
            if (success) {
            } else {
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

        val route = resolveStartupRoute(
            SetupProgress(
                needsLanguageSelection = needsLanguageSelection,
                needsOnboarding = needsOnboarding,
                needsFeatureSelection = needsFeatureSelection
            )
        )
        _navigationEvent.value = RootNavigationEvent.NavigateTo(route)
    }

    // ============================================
    // PRIVATE: CONNECTIVITY
    // ============================================

    private fun isInternetAvailable(): Boolean {
        val cm = application.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
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

// ============================================
// LOADING STEP ENUM
// ============================================

/**
 * Loading steps during app initialization
 * Used for UI feedback on splash/loading screen
 */
enum class LoadingStep {
    /** Initializing app services (AdMob, etc.) */
    INITIALIZING,

    /** Fetching Remote Config from Firebase */
    FETCHING_CONFIG,

    /** Loading and presenting App Open ad */
    LOADING_AD,

    /** Checking onboarding/language/feature selection status */
    CHECKING_STATUS,

    /** Preparing to navigate to first screen */
    PREPARING
}
