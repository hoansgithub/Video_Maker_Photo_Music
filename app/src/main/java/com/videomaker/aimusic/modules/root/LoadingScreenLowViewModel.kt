package com.videomaker.aimusic.modules.root

import android.app.Activity
import androidx.lifecycle.ViewModel
import co.alcheclub.lib.acccore.ads.loader.AdsLoaderService
import com.videomaker.aimusic.core.ads.AdPlacementConfigService
import com.videomaker.aimusic.core.ads.InterstitialAdHelperExt
import com.videomaker.aimusic.core.constants.AdPlacement
import com.videomaker.aimusic.navigation.AppRoute
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import java.lang.ref.WeakReference

/**
 * ViewModel for LoadingScreenLow — handles LOW priority ad loading.
 *
 * Created when HIGH priority splash ad fails to load in [RootViewModel].
 * Manages its own ad loading lifecycle independently:
 * - Loads LOW priority ad (INTERSTITIAL_SPLASH_LOW or INTERSTITIAL_OPEN_APP_LOW)
 * - Shows ad if loaded, navigates without ad if it fails
 * - Uses [Channel] for one-time navigation events
 *
 * Native ads are already preloaded by RootViewModel when HIGH fails,
 * so this ViewModel only handles the interstitial.
 */
class LoadingScreenLowViewModel(
    private val adsLoaderService: AdsLoaderService,
    private val adPlacementConfigService: AdPlacementConfigService
) : ViewModel() {

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _loadingStep = MutableStateFlow(LoadingStep.LOADING_AD)
    val loadingStep: StateFlow<LoadingStep> = _loadingStep.asStateFlow()

    // Channel for one-time navigation events (prevents re-trigger on recomposition)
    private val _navigationEvent = Channel<RootNavigationEvent>(Channel.BUFFERED)
    val navigationEvent = _navigationEvent.receiveAsFlow()

    @Volatile
    private var activityRef: WeakReference<Activity>? = null

    /**
     * Load and show LOW priority interstitial ad.
     *
     * @param activity Activity context required for showing ads
     * @param destination Final navigation destination (resolved by RootViewModel)
     * @param isFirstOpen Whether this is the first app launch (determines placement)
     */
    suspend fun initializeLowPriorityAd(
        activity: Activity,
        destination: AppRoute,
        isFirstOpen: Boolean
    ) {
        android.util.Log.d(TAG, "🚀 initializeLowPriorityAd (isFirstOpen=$isFirstOpen, destination=$destination)")
        activityRef = WeakReference(activity)

        _isLoading.value = true
        _loadingStep.value = LoadingStep.LOADING_AD

        val lowPlacement = if (isFirstOpen) {
            AdPlacement.INTERSTITIAL_SPLASH_LOW
        } else {
            AdPlacement.INTERSTITIAL_OPEN_APP_LOW
        }

        // Skip if placement disabled via Remote Config
        val lowEnabled = adPlacementConfigService.isPlacementEnabled(lowPlacement)
        if (!lowEnabled) {
            android.util.Log.d(TAG, "⏭️ LOW placement disabled ($lowPlacement), navigating without ad")
            navigateTo(destination)
            return
        }

        android.util.Log.d(TAG, "🔄 Loading LOW priority ad: $lowPlacement")

        // Load LOW ad (preloadInterstitial handles its own timeout)
        try {
            InterstitialAdHelperExt.preloadInterstitial(
                adsLoaderService = adsLoaderService,
                placement = lowPlacement,
                showLoadingOverlay = false
            )
        } catch (e: Exception) {
            android.util.Log.e(TAG, "❌ LOW priority ad exception: ${e.message}")
        }

        // Verify ad actually loaded
        val isLowReady = adsLoaderService.isInterstitialReady(lowPlacement)

        if (!isLowReady) {
            android.util.Log.w(TAG, "❌ LOW priority ad failed to load — navigating without ad")
            navigateTo(destination)
            return
        }

        android.util.Log.d(TAG, "✅ LOW priority ad loaded successfully")

        val act = activityRef?.get()
        if (act == null) {
            android.util.Log.w(TAG, "⚠️ No activity reference, navigating without ad")
            navigateTo(destination)
            return
        }

        android.util.Log.d(TAG, "📺 Showing LOW priority ad: $lowPlacement")

        InterstitialAdHelperExt.showInterstitial(
            adsLoaderService = adsLoaderService,
            activity = act,
            placement = lowPlacement,
            action = {
                android.util.Log.d(TAG, "✅ LOW ad closed — navigating to $destination")
                navigateTo(destination)
            },
            bypassFrequencyCap = true,
            showLoadingOverlay = false
        )
    }

    private fun navigateTo(destination: AppRoute) {
        _isLoading.value = false
        val result = _navigationEvent.trySend(RootNavigationEvent.NavigateTo(destination))
        if (!result.isSuccess) {
            android.util.Log.w(TAG, "⚠️ Failed to send navigation event: ${result.exceptionOrNull()}")
        }
    }

    fun updateActivityRef(activity: Activity) {
        activityRef = WeakReference(activity)
    }

    fun clearActivityRef() {
        activityRef?.clear()
        activityRef = null
    }

    override fun onCleared() {
        super.onCleared()
        clearActivityRef()
        _navigationEvent.close()
    }

    companion object {
        private const val TAG = "LoadingScreenLowVM"
    }
}
