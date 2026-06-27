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
import com.videomaker.aimusic.core.ads.AdPlacementConfigService
import com.videomaker.aimusic.core.ads.InterstitialAdHelperExt
import com.videomaker.aimusic.core.analytics.Analytics
import com.videomaker.aimusic.core.analytics.AnalyticsEvent
import com.videomaker.aimusic.core.constants.AdPlacement
import com.videomaker.aimusic.core.constants.RemoteConfigKeys
import com.videomaker.aimusic.core.data.local.PreferencesManager
import com.videomaker.aimusic.core.permission.NotificationPermissionCoordinator
import com.videomaker.aimusic.modules.language.domain.usecase.CheckLanguageSelectedUseCase
import com.videomaker.aimusic.modules.onboarding.domain.usecase.CheckOnboardingStatusUseCase
import com.videomaker.aimusic.navigation.AppRoute
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.lang.ref.WeakReference

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
 * ## HIGH/LOW splash interstitial strategy
 *
 * This ViewModel only handles HIGH priority ad loading.
 * If HIGH fails, it sets [showLowPriorityLoading] to `true`, which triggers
 * [LoadingScreenLow] in the Activity — a separate composable with its own
 * [LoadingScreenLowViewModel] that independently handles LOW priority ad loading.
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
    private val adsLoaderService: AdsLoaderService,
    private val adPlacementConfigService: AdPlacementConfigService,
    private val notificationPermissionCoordinator: NotificationPermissionCoordinator
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
    // LOW-PRIORITY LOADING SCREEN STATE
    // ============================================

    /** When `true`, RootViewActivity switches from LoadingScreen to LoadingScreenLow. */
    private val _showLowPriorityLoading = MutableStateFlow(false)
    val showLowPriorityLoading: StateFlow<Boolean> = _showLowPriorityLoading.asStateFlow()

    /** Navigation destination resolved during init, exposed for LoadingScreenLow. */
    private val _destination = MutableStateFlow<AppRoute?>(null)
    val destination: StateFlow<AppRoute?> = _destination.asStateFlow()

    /** Whether this is first open, exposed for LoadingScreenLow placement selection. */
    private val _isFirstOpen = MutableStateFlow(false)
    val isFirstOpen: StateFlow<Boolean> = _isFirstOpen.asStateFlow()

    // ============================================
    // NOTIFICATION PERMISSION EVENTS (Channel-based one-shot)
    // ============================================

    private val _permissionRequestChannel = Channel<String>(Channel.BUFFERED)

    /** Collect in Activity to launch the system permission dialog. */
    val permissionRequestEvent = _permissionRequestChannel.receiveAsFlow()

    @Volatile
    private var permissionDeferred: CompletableDeferred<Boolean>? = null

    @Volatile
    private var isPermissionRequestInProgress = false

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
    // true = first ever launch → INTERSTITIAL_SPLASH_HIGH; false = second+ → INTERSTITIAL_OPEN_APP_HIGH
    @Volatile
    private var firstOpen = false

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
     * 5. HIGH priority splash interstitial loading
     * 6. Navigation to appropriate screen (or switch to LoadingScreenLow)
     *
     * @param activity Activity context required for UMP consent form and ads
     */
    fun initializeApp(activity: Activity) {
        // Store Activity reference (WeakReference to avoid leaks)
        activityRef = WeakReference(activity)

        if (isInitialized) {
            // Already initialized — if LoadingScreenLow is active, let it handle things.
            // If the notification permission dialog is currently showing (Activity recreation
            // while OS dialog is up), do NOT navigate — the suspended loadInitialData()
            // coroutine will resume when the user responds and handle navigation itself.
            if (!_showLowPriorityLoading.value && !isPermissionRequestInProgress) {
                proceedWithHighAd()
            }
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

    /**
     * Called from Activity when the system permission dialog returns a result.
     * Completes the deferred so [requestNotificationPermission] can resume.
     */
    fun onPermissionResult(granted: Boolean) {
        permissionDeferred?.complete(granted)
        permissionDeferred = null
        isPermissionRequestInProgress = false
    }

    // ============================================
    // PRIVATE: NOTIFICATION PERMISSION
    // ============================================

    /**
     * Request notification permission during splash loading.
     * Suspends until the user responds to the system dialog (or skips if not needed).
     *
     * Uses Channel to send the permission string to the Activity, then awaits
     * the result via CompletableDeferred. The Activity collects [permissionRequestEvent]
     * and calls [onPermissionResult] when the dialog returns.
     */
    private suspend fun requestNotificationPermission() {
        if (isPermissionRequestInProgress) return

        val context = activityRef?.get() ?: return
        if (!notificationPermissionCoordinator.shouldRequestOnboardingPermission(context)) return

        isPermissionRequestInProgress = true
        notificationPermissionCoordinator.markOnboardingPermissionDialogShown()

        // Small delay so the splash screen is visible before the dialog appears
        kotlinx.coroutines.delay(300)

        val deferred = CompletableDeferred<Boolean>()
        permissionDeferred = deferred
        _permissionRequestChannel.send(android.Manifest.permission.POST_NOTIFICATIONS)

        // Suspend until Activity calls onPermissionResult()
        deferred.await()
    }

    // ============================================
    // PRIVATE: INITIALIZATION
    // ============================================

    /**
     * Load initial data and attempt HIGH priority splash ad.
     *
     * This function only handles HIGH priority ad loading.
     * If HIGH fails, it switches to [LoadingScreenLow] which has its own
     * ViewModel for LOW priority ads.
     */
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
            firstOpen = preferencesManager.isFirstLaunch()
            android.util.Log.d("RootViewModel", "📊 First open detection: isFirstOpen=$firstOpen")

            // Step 2: Get initialization timeout from Remote Config (already pre-fetched in Application.onCreate())
            initTimeoutMs = try {
                val configValue = remoteConfig.getLong(RemoteConfigKeys.APP_INIT_TIMEOUT_MS, 45_000L)
                if (configValue > 0) configValue else 45_000L
            } catch (e: Exception) {
                45_000L
            }
            android.util.Log.d("RootViewModel", "📊 Init timeout: ${initTimeoutMs}ms (from cached Remote Config)")

            val highPlacement = if (firstOpen) AdPlacement.INTERSTITIAL_SPLASH_HIGH else AdPlacement.INTERSTITIAL_OPEN_APP_HIGH

            // ============================================
            // CRITICAL: Timeout wrapper prevents infinite loading
            // ============================================
            try {
                withTimeout(initTimeoutMs) {
                    try {
                        // Step 3: Check startup gate status
                        _loadingStep.value = LoadingStep.CHECKING_STATUS
                        val onboardingResult = checkOnboardingStatusUseCase()
                        val shouldShowOnboarding = onboardingResult.getOrNull() ?: false
                        this@RootViewModel.onboardingComplete = !shouldShowOnboarding

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

                        // Step 4: Preload native ads (Application scope, survives navigation)
                        _loadingStep.value = LoadingStep.LOADING_AD
                        android.util.Log.d("RootViewModel", "🎬 Step 4: Loading HIGH priority ad ($highPlacement)...")

                        preloadNativeAds()

                        // Step 5: Load HIGH interstitial + request notification permission in parallel
                        val adLoadJob = async {
                            val highEnabled = adPlacementConfigService.isPlacementEnabled(highPlacement)
                            if (highEnabled) {
                                android.util.Log.d("RootViewModel", "📺 Loading splash interstitial HIGH ($highPlacement)")
                                runCatching {
                                    InterstitialAdHelperExt.preloadInterstitial(
                                        adsLoaderService = adsLoaderService,
                                        placement = highPlacement,
                                        loadTimeoutMillis = initTimeoutMs,
                                        showLoadingOverlay = false
                                    )
                                }
                            } else {
                                android.util.Log.d("RootViewModel", "⏭️ HIGH placement disabled, skipping ($highPlacement)")
                            }
                        }

                        requestNotificationPermission()
                        adLoadJob.await()

                    } catch (e: Exception) {
                        android.util.Log.e("RootViewModel", "Initialization error: ${e.message}")
                    }
                }
            } catch (e: TimeoutCancellationException) {
                initTimedOut = true
                android.util.Log.w("RootViewModel", "⏱️ Initialization timed out after ${initTimeoutMs}ms")
            }

            // Step 6: Track initialization time
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

            Analytics.track(
                name = AnalyticsEvent.APP_INIT_TIME,
                params = mapOf(
                    AnalyticsEvent.Param.VALUE to timeBucket
                )
            )

            // Step 7: Check if HIGH ad is ready
            val isHighReady = adsLoaderService.isInterstitialReady(highPlacement)

            if (!isHighReady) {
                // HIGH failed → switch to LoadingScreenLow (separate ViewModel handles LOW)
                // Native ads already preloading from step 4 above
                android.util.Log.w("RootViewModel", "⚠️ HIGH priority ad not ready — switching to LoadingScreenLow")

                val route = resolveStartupRoute(
                    SetupProgress(
                        onboardingComplete = this@RootViewModel.onboardingComplete,
                        resumeStep = this@RootViewModel.onboardingResumeStep
                    )
                )

                _destination.value = route
                _isFirstOpen.value = firstOpen
                _showLowPriorityLoading.value = true
                return@launch // LoadingScreenLowViewModel takes over
            }

            android.util.Log.d("RootViewModel", "✅ HIGH priority ad loaded ($highPlacement)")

            // HIGH is ready → show it and navigate
            proceedWithHighAd()
        }
    }

    // ============================================
    // PRIVATE: NATIVE AD PRELOADING
    // ============================================

    /**
     * Preload native ads using Application-scoped coroutines.
     * Called once during initialization — survives ViewModel destruction.
     */
    private fun preloadNativeAds() {
        if (!this@RootViewModel.onboardingComplete) {
            if (this@RootViewModel.onboardingResumeStep != null) {
                // Partial progress → Preload welcome-back ad
                android.util.Log.d("RootViewModel", "🔄 Preloading ONBOARDING_WELCOME_BACK ad (resume flow)")
                com.videomaker.aimusic.VideoMakerApplication.preloadNativeAd(
                    placement = AdPlacement.NATIVE_ONBOARDING_WELCOME_BACK
                )
            } else {
                // Fresh start → Preload Language Selection ads (Step 0)
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
            android.util.Log.d("RootViewModel", "⏭️ Onboarding complete, skipping native ad preload")
        }
    }

    // ============================================
    // PRIVATE: NAVIGATION
    // ============================================

    /**
     * Show HIGH priority interstitial and navigate.
     * Only called when HIGH ad is confirmed ready via [isInterstitialReady].
     */
    private fun proceedWithHighAd() {
        val route = resolveStartupRoute(
            SetupProgress(
                onboardingComplete = this@RootViewModel.onboardingComplete,
                resumeStep = this@RootViewModel.onboardingResumeStep
            )
        )

        val highPlacement = if (firstOpen) AdPlacement.INTERSTITIAL_SPLASH_HIGH else AdPlacement.INTERSTITIAL_OPEN_APP_HIGH
        val isAdReady = adsLoaderService.isInterstitialReady(highPlacement)

        android.util.Log.d("RootViewModel", "📍 proceedWithHighAd() - Destination: $route, isAdReady: $isAdReady")

        if (!isAdReady) {
            android.util.Log.w("RootViewModel", "⚠️ HIGH ad no longer ready — navigating directly")
            _navigationEvent.value = RootNavigationEvent.NavigateTo(route)
            return
        }

        val activity = activityRef?.get()
        if (activity == null) {
            android.util.Log.w("RootViewModel", "⚠️ No activity reference, skipping splash ad")
            _navigationEvent.value = RootNavigationEvent.NavigateTo(route)
            return
        }

        android.util.Log.d("RootViewModel", "📺 Showing HIGH priority ad ($highPlacement)")

        InterstitialAdHelperExt.showInterstitial(
            adsLoaderService = adsLoaderService,
            activity = activity,
            placement = highPlacement,
            action = {
                android.util.Log.d("RootViewModel", "✅ Ad closed — navigating to $route")
                _navigationEvent.value = RootNavigationEvent.NavigateTo(route)
            },
            onShown = {
                android.util.Log.d("RootViewModel", "🎬 Splash ad is now showing on screen")
            },
            bypassFrequencyCap = true,
            showLoadingOverlay = false
        )
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
     * Also checks NOT_SUSPENDED to ensure the network isn't temporarily paused.
     */
    private fun isInternetAvailable(): Boolean {
        val cm = application.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false

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
