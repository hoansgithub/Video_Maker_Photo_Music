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
import com.videomaker.aimusic.core.constants.RemoteConfigKeys
import com.videomaker.aimusic.core.data.local.PreferencesManager
import com.videomaker.aimusic.modules.language.domain.usecase.CheckLanguageSelectedUseCase
import com.videomaker.aimusic.modules.onboarding.domain.usecase.CheckOnboardingStatusUseCase
import com.videomaker.aimusic.navigation.AppRoute
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.TimeoutCancellationException

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
     * 1. UMP consent and AdMob SDK initialization (CRITICAL: MUST happen first!)
     * 2. Firebase Remote Config fetch and activate
     * 3. Language selection status check
     * 4. Onboarding status check
     * 5. Navigation to appropriate screen
     *
     * @param activity Activity context required for UMP consent form
     */
    fun initializeApp(activity: android.app.Activity) {
        if (isInitialized) {
            // Already initialized, just navigate
            proceedToNextScreen()
            return
        }

        isInitialized = true

        // Check if ads already initialized (global state)
        if (com.videomaker.aimusic.VideoMakerApplication.isAdsInitialized()) {
            loadInitialData()
            return
        }

        _loadingStep.value = LoadingStep.INITIALIZING

        // ⚠️ CRITICAL: Initialize UMP consent + AdMob SDK BEFORE any ad loading!
        // This uses Application-scoped coroutine (survives Activity destruction)
        com.videomaker.aimusic.VideoMakerApplication.initializeAdsIfNeeded(activity) {
            // This callback is called when UMP consent + AdMob initialization is complete
            // NOW it's safe to load Remote Config and ads
            loadInitialData()
        }
    }

    /**
     * Retry app initialization after a no-internet failure.
     * Directly restarts the loadInitialData() flow without requiring Activity reference.
     */
    fun retryInitialization() {
        _showNoInternetDialog.value = false
        isInitialized = false
        loadInitialData()
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
            var initTimedOut = false

            // Step 1: Brief delay to allow network subsystem to fully initialize on cold start
            // This prevents race conditions where network is transitioning during app launch
            delay(300)

            // Step 2: Check internet connection
            if (!isInternetAvailable()) {
                _isLoading.value = false
                isInitialized = false
                _showNoInternetDialog.value = true
                return@launch
            }

            // Step 3: Get initialization timeout from Remote Config
            // Default: 45 seconds (can be adjusted remotely without app update)
            // Longer timeout allows for slower networks while still preventing infinite loading
            val initTimeoutMs = try {
                remoteConfig.getLong(RemoteConfigKeys.APP_INIT_TIMEOUT_MS, 45_000L)
            } catch (e: Exception) {
                45_000L  // Fallback to 45 seconds if Remote Config fails
            }

            // ============================================
            // CRITICAL: Timeout wrapper prevents infinite loading
            // ============================================
            // Wraps entire initialization in configurable timeout (default 45s)
            // If any operation hangs, app proceeds to navigation anyway
            // This prevents users from getting stuck on loading screen forever
            try {
                withTimeout(initTimeoutMs) {  // Remote Config controlled timeout
                    try {
                        // Step 3: Load Firebase Remote Config (10s timeout)
                        // UMP consent is already complete, safe to fetch config
                        _loadingStep.value = LoadingStep.FETCHING_CONFIG
                        loadRemoteConfig()

                        // Step 4: Present App Open Ad (placeholder for future)
                        // UMP consent is already complete, safe to load/show ads
                        _loadingStep.value = LoadingStep.LOADING_AD
                        presentAppOpenAd()

                        // Step 5: Check startup gate status
                        _loadingStep.value = LoadingStep.CHECKING_STATUS
                        val onboardingResult = checkOnboardingStatusUseCase()
                        needsOnboarding = onboardingResult.getOrNull() ?: false
                        needsLanguageSelection = checkLanguageSelectedUseCase()
                        needsFeatureSelection = !preferencesManager.isFeatureSelectionComplete()

                    } catch (e: Exception) {
                        android.util.Log.e("RootViewModel", "Initialization error: ${e.message}")
                        // Continue to navigation even if initialization fails
                    }
                }
            } catch (e: TimeoutCancellationException) {
                initTimedOut = true
                android.util.Log.w("RootViewModel", "⏱️ Initialization timed out after ${initTimeoutMs}ms — proceeding to navigation")
            }

            // Step 7: Track initialization time with time buckets
            val durationMillis = System.currentTimeMillis() - startTimeMillis
            val durationSeconds = durationMillis / 1000.0
            val timeBucket = when {
                initTimedOut -> "timeout"
                durationSeconds < 3.0 -> "under_3s"
                durationSeconds < 5.0 -> "3_to_5s"
                durationSeconds < 8.0 -> "5_to_8s"
                durationSeconds < 10.0 -> "8_to_10s"
                durationSeconds < 15.0 -> "10_to_15s"
                else -> "over_15s"
            }

            // Track initialization performance
            Analytics.track(
                name = AnalyticsEvent.APP_INIT_TIME,
                params = mapOf(
                    AnalyticsEvent.Param.VALUE to timeBucket
                )
            )

            // Step 8: Navigate to appropriate screen (ALWAYS happens, even if timeout/error)
            // This ensures users never get stuck on loading screen
            proceedToNextScreen()
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

    /**
     * Check if internet is actually available and validated.
     *
     * Uses NET_CAPABILITY_VALIDATED which means Android has verified the network
     * can reach the internet (by pinging Google's captive portal servers).
     *
     * This is more reliable than NET_CAPABILITY_INTERNET which only checks if
     * the network declares it can provide internet (not if it actually works).
     *
     * Also checks NOT_SUSPENDED to ensure the network isn't temporarily paused.
     */
    private fun isInternetAvailable(): Boolean {
        val cm = application.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false

        // Check if network is validated (Android verified it can reach the internet)
        // AND not suspended (network isn't temporarily paused)
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) &&
               capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_SUSPENDED)
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
