package com.videomaker.aimusic.modules.root

import android.app.Activity
import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.alcheclub.lib.acccore.ads.loader.AdsLoaderService
import co.alcheclub.lib.acccore.remoteconfig.RemoteConfig
import com.videomaker.aimusic.core.ads.InterstitialAdHelperExt
import com.videomaker.aimusic.core.analytics.Analytics
import com.videomaker.aimusic.core.analytics.AnalyticsEvent
import com.videomaker.aimusic.core.constants.AdPlacement
import com.videomaker.aimusic.core.constants.RemoteConfigKeys
import com.videomaker.aimusic.core.data.local.PreferencesManager
import com.videomaker.aimusic.modules.language.domain.usecase.CheckLanguageSelectedUseCase
import com.videomaker.aimusic.modules.onboarding.domain.usecase.CheckOnboardingStatusUseCase
import com.videomaker.aimusic.navigation.AppRoute
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

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
    private val remoteConfig: RemoteConfig,
    private val adsLoaderService: AdsLoaderService
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

    // Simplified onboarding tracking - only care if complete or not
    @Volatile
    private var onboardingComplete = false

    // Non-null when user has partial onboarding progress and should see welcome-back screen
    @Volatile
    private var onboardingResumeStep: OnboardingResumeStep? = null

    // Captured once in loadInitialData() before any ad logic runs.
    // true = first ever launch → INTERSTITIAL_SPLASH; false = second+ → INTERSTITIAL_OPEN_APP
    @Volatile
    private var isFirstOpen = false

    @Volatile
    private var isInitialized = false

    // Use WeakReference to avoid leaking Activity during configuration changes
    @Volatile
    private var activityRef: WeakReference<Activity>? = null

    // Initialization timeout from Remote Config (default 45s)
    @Volatile
    private var initTimeoutMs: Long = 45_000L

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
     * 5. Splash interstitial ad loading and showing
     * 6. Navigation to appropriate screen
     *
     * @param activity Activity context required for UMP consent form and ads
     */
    fun initializeApp(activity: Activity) {
        // Store Activity reference (WeakReference to avoid leaks)
        activityRef = WeakReference(activity)

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

            // Step 1: Check internet connection
            // Note: Modern Android (API 28+) network stack is stable on cold start, no delay needed
            if (!isInternetAvailable()) {
                // Keep loading indicator visible while showing no-internet dialog
                isInitialized = false
                _showNoInternetDialog.value = true
                return@launch
            }

            // Capture once — isFirstLaunch() auto-marks false on first call
            isFirstOpen = preferencesManager.isFirstLaunch()
            android.util.Log.d("RootViewModel", "📊 First open detection: isFirstOpen=$isFirstOpen")

            // Step 2: Get initialization timeout from Remote Config (already pre-fetched in Application.onCreate())
            // Default: 45 seconds (can be adjusted remotely without app update)
            // Longer timeout allows for slower networks while still preventing infinite loading
            // Note: Using cached Remote Config values (pre-fetched during app startup)
            initTimeoutMs = try {
                val configValue = remoteConfig.getLong(RemoteConfigKeys.APP_INIT_TIMEOUT_MS, 45_000L)
                // Validate: Remote Config might return 0 or invalid value
                if (configValue > 0) configValue else 45_000L
            } catch (e: Exception) {
                45_000L  // Fallback to 45 seconds if Remote Config fails
            }
            android.util.Log.d("RootViewModel", "📊 Init timeout: ${initTimeoutMs}ms (from cached Remote Config)")

            // ============================================
            // CRITICAL: Timeout wrapper prevents infinite loading
            // ============================================
            // Wraps entire initialization in configurable timeout (default 45s)
            // If any operation hangs, app proceeds to navigation anyway
            // This prevents users from getting stuck on loading screen forever
            try {
                withTimeout(initTimeoutMs) {  // Remote Config controlled timeout
                    try {
                        // Step 3: Check startup gate status
                        // Simplified: Only check if onboarding is complete
                        // If not complete, always start from Language Selection (beginning of flow)
                        _loadingStep.value = LoadingStep.CHECKING_STATUS
                        val onboardingResult = checkOnboardingStatusUseCase()
                        val shouldShowOnboarding = onboardingResult.getOrNull() ?: false
                        this@RootViewModel.onboardingComplete = !shouldShowOnboarding

                        // Also check PreferencesManager directly to verify
                        val directCheck = preferencesManager.isOnboardingComplete()

                        android.util.Log.d("RootViewModel", "📊 Onboarding Status:")
                        android.util.Log.d("RootViewModel", "   - PreferencesManager.isOnboardingComplete(): $directCheck")
                        android.util.Log.d("RootViewModel", "   - shouldShowOnboarding (from UseCase): $shouldShowOnboarding")
                        android.util.Log.d("RootViewModel", "   - onboardingComplete (saved to class property): ${this@RootViewModel.onboardingComplete}")

                        // Detect partial onboarding progress for welcome-back flow
                        if (!this@RootViewModel.onboardingComplete) {
                            val languageComplete = !checkLanguageSelectedUseCase()
                            this@RootViewModel.onboardingResumeStep =
                                preferencesManager.getOnboardingResumeStep(languageComplete)
                            android.util.Log.d("RootViewModel", "📊 Onboarding resume step: ${this@RootViewModel.onboardingResumeStep}")
                        }

                        // Step 4: Preload ads (optimized strategy)
                        // Native ads: Load in Application scope (background, survives ViewModel lifecycle)
                        // Splash interstitial: Wait for it (we show it immediately before navigation)
                        _loadingStep.value = LoadingStep.LOADING_AD
                        val splashPlacement = if (isFirstOpen) AdPlacement.INTERSTITIAL_SPLASH else AdPlacement.INTERSTITIAL_OPEN_APP
                        android.util.Log.d("RootViewModel", "🎬 Step 4: Preloading ads (optimized 1-step-ahead strategy)...")
                        android.util.Log.d("RootViewModel", "   - onboardingComplete: ${this@RootViewModel.onboardingComplete}")
                        android.util.Log.d("RootViewModel", "   - Splash interstitial: $splashPlacement (isFirstOpen=$isFirstOpen)")
                        android.util.Log.d("RootViewModel", "   - Splash timeout: ${initTimeoutMs}ms")

                        /*
                         * OPTIMIZED AD LOADING STRATEGY (Delayed Parallel)
                         *
                         * 1. Splash interstitial (CRITICAL PATH):
                         *    - Starts immediately (blocking) - we show it right away
                         *    - Gets initial network bandwidth priority
                         *    - Uses main thread (AdMob requirement)
                         *
                         * 2. Native ads (BACKGROUND):
                         *    - Starts after 1.5s delay (gives splash a head start)
                         *    - Launch in Application scope (survives ViewModel lifecycle)
                         *    - Won't compete with splash on slow networks
                         *    - On fast networks: splash finishes quickly, native ads still load early
                         *
                         * Why 1.5s delay?
                         * - Typical splash ad load: 1.5-4s
                         * - On slow networks: 1.5s gives splash exclusive bandwidth
                         * - On fast networks: splash finishes before native ads start anyway
                         * - Native ads still have plenty of time (load during splash display + navigation)
                         *
                         * Full onboarding flow (each step = separate Activity):
                         * Language → Survey → Welcome Pages → Genre/Template → Feature Selection → Personalizing → Home
                         */

                        // Launch native ads with delay (Application scope, survives navigation)
                        if (!this@RootViewModel.onboardingComplete) {
                            if (this@RootViewModel.onboardingResumeStep != null) {
                                // Partial progress → Preload welcome-back ad
                                android.util.Log.d("RootViewModel", "🔄 Preloading ONBOARDING_WELCOME_BACK ad (resume flow)")
                                com.videomaker.aimusic.VideoMakerApplication.preloadNativeAd(
                                    placement = AdPlacement.NATIVE_ONBOARDING_WELCOME_BACK
                                )
                            } else {
                                // Fresh start → Preload Language Selection ads (Step 0)
                                // Primary: immediate (prioritize bandwidth for the ad users see first)
                                // ALT: delayed 1s (A/B variant, lower priority)
                                android.util.Log.d("RootViewModel", "🔄 Preloading LANGUAGE ad immediately, LANGUAGE_ALT delayed 1s")
                                com.videomaker.aimusic.VideoMakerApplication.preloadNativeAd(
                                    placement = AdPlacement.NATIVE_ONBOARDING_LANGUAGE
                                )
                                com.videomaker.aimusic.VideoMakerApplication.preloadNativeAdDelayed(
                                    placement = AdPlacement.NATIVE_ONBOARDING_LANGUAGE_ALT,
                                    delayMs = 1000L
                                )
                            }
                        } else {
                            // Onboarding complete → Go to Home (no native ads needed)
                            android.util.Log.d("RootViewModel", "⏭️ Onboarding complete, skipping native ad preload")
                        }

                        // Load splash interstitial (starts immediately, blocking)
                        android.util.Log.d("RootViewModel", "📺 Loading splash interstitial (priority)")
                        withContext(Dispatchers.Main) {
                            runCatching {
                                val success = InterstitialAdHelperExt.preloadInterstitial(
                                    adsLoaderService = adsLoaderService,
                                    placement = splashPlacement,
                                    loadTimeoutMillis = initTimeoutMs,
                                    showLoadingOverlay = false
                                )
                                if (success) {
                                    android.util.Log.d("RootViewModel", "✅ Splash interstitial loaded ($splashPlacement)")
                                } else {
                                    android.util.Log.w("RootViewModel", "⚠️ Splash interstitial load failed ($splashPlacement)")
                                }
                            }.onFailure {
                                android.util.Log.e("RootViewModel", "❌ Splash interstitial exception: ${it.message}", it)
                            }
                        }

                    } catch (e: Exception) {
                        android.util.Log.e("RootViewModel", "Initialization error: ${e.message}")
                        // Continue to navigation even if initialization fails
                    }
                }
            } catch (e: TimeoutCancellationException) {
                initTimedOut = true
                android.util.Log.w("RootViewModel", "⏱️ Initialization timed out after ${initTimeoutMs}ms — proceeding to navigation")
            }

            // Step 5: Track initialization time with time buckets
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

            // Step 6: Navigate to appropriate screen (ALWAYS happens, even if timeout/error)
            // This ensures users never get stuck on loading screen
            proceedToNextScreen()
        }
    }

    // ============================================
    // PRIVATE: NAVIGATION
    // ============================================

    private fun proceedToNextScreen() {
        // Determine destination route
        val route = resolveStartupRoute(
            SetupProgress(
                onboardingComplete = this@RootViewModel.onboardingComplete,
                resumeStep = this@RootViewModel.onboardingResumeStep
            )
        )

        android.util.Log.d("RootViewModel", "📍 proceedToNextScreen() - Destination: $route")

        // Show splash interstitial ad (if loaded) before navigating
        // Ad shows on top of loading screen, then navigates when closed
        val splashPlacement = if (isFirstOpen) AdPlacement.INTERSTITIAL_SPLASH else AdPlacement.INTERSTITIAL_OPEN_APP
        activityRef?.get()?.let { activity ->
            android.util.Log.d("RootViewModel", "📺 Attempting to show splash interstitial ad...")
            android.util.Log.d("RootViewModel", "   - Placement: $splashPlacement (isFirstOpen=$isFirstOpen)")
            android.util.Log.d("RootViewModel", "   - Bypass frequency cap: true")
            android.util.Log.d("RootViewModel", "   - Load timeout: ${initTimeoutMs}ms")

            // Track if callback was called
            val callbackCalled = AtomicBoolean(false)

            InterstitialAdHelperExt.showInterstitial(
                adsLoaderService = adsLoaderService,
                activity = activity,
                placement = splashPlacement,
                action = {
                    if (callbackCalled.compareAndSet(false, true)) {
                        // Navigate when ad closes (or if ad fails to show)
                        android.util.Log.d("RootViewModel", "✅ Ad action callback called - navigating to $route")
                        _navigationEvent.value = RootNavigationEvent.NavigateTo(route)
                    }
                },
                onShown = {
                    android.util.Log.d("RootViewModel", "🎬 Splash ad is now showing on screen")
                },
                bypassFrequencyCap = true,  // Splash ad always shows (once per session)
                loadTimeoutMillis = initTimeoutMs,  // Use Remote Config timeout (default 45s)
                showLoadingOverlay = false  // Loading screen already visible
            )

            // ⚠️ WORKAROUND for ACCCore bug: InterstitialAdHelper doesn't call action callback on timeout
            // Force navigation after timeout + 5s buffer if callback wasn't called
            viewModelScope.launch {
                delay(initTimeoutMs + 5000L)  // Wait for ad timeout + 5s buffer
                if (callbackCalled.compareAndSet(false, true)) {
                    android.util.Log.w("RootViewModel", "⚠️ Ad callback not called within timeout - forcing navigation")
                    _navigationEvent.value = RootNavigationEvent.NavigateTo(route)
                }
            }
        } ?: run {
            // No activity reference - navigate without ad
            android.util.Log.w("RootViewModel", "⚠️ No activity reference, skipping splash ad")
            _navigationEvent.value = RootNavigationEvent.NavigateTo(route)
        }
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
