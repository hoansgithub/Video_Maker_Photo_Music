package com.videomaker.aimusic.core.ads

import android.util.Log
import co.alcheclub.lib.acccore.ads.loader.AdsLoaderService
import com.videomaker.aimusic.core.constants.AdPlacement
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manager for post-interstitial fullscreen native ad (Drama app pattern).
 *
 * Triggered by specific interstitial placements:
 * - INTERSTITIAL_SPLASH_HIGH / SPLASH_LOW (first install) → NATIVE_AFTER_SPLASH
 * - INTERSTITIAL_OPEN_APP_HIGH / OPEN_APP_LOW (returning users) → NATIVE_AFTER_SPLASH
 * - INTERSTITIAL_ONBOARDING_COMPLETE (end of onboarding) → NATIVE_AFTER_ONBOARDING
 *
 * Flow:
 * 1. Interstitial shown on screen → start loading native ad in background
 * 2. Interstitial closes → if native loaded, show fullscreen overlay; if not, skip
 * 3. User closes native ad → cleanup and proceed
 *
 * Non-blocking: features proceed normally if native ad is unavailable.
 *
 * @param adsLoaderService ACCCore-Ads service for loading/showing ads
 * @param adPlacementConfigService Placement config for enabled check
 * @param coroutineScope Application-scoped coroutine scope for preloading
 */
class PostInterNativeAdManager(
    private val adsLoaderService: AdsLoaderService,
    private val adPlacementConfigService: AdPlacementConfigService,
    private val coroutineScope: CoroutineScope
) {
    companion object {
        private const val TAG = "PostInterNativeAdMgr"

        /** Mapping from interstitial placement → native ad placement */
        private val INTERSTITIAL_TO_NATIVE = mapOf(
            AdPlacement.INTERSTITIAL_SPLASH_HIGH to AdPlacement.NATIVE_AFTER_SPLASH,
            AdPlacement.INTERSTITIAL_SPLASH_LOW to AdPlacement.NATIVE_AFTER_SPLASH,
            AdPlacement.INTERSTITIAL_OPEN_APP_HIGH to AdPlacement.NATIVE_AFTER_SPLASH,
            AdPlacement.INTERSTITIAL_OPEN_APP_LOW to AdPlacement.NATIVE_AFTER_SPLASH,
            AdPlacement.INTERSTITIAL_ONBOARDING_COMPLETE to AdPlacement.NATIVE_AFTER_ONBOARDING
        )

        /** Check if an interstitial placement should trigger post-interstitial native ad */
        fun isEligiblePlacement(placement: String): Boolean = placement in INTERSTITIAL_TO_NATIVE

        /** Get the native ad placement for an interstitial placement */
        fun nativePlacementFor(placement: String): String? = INTERSTITIAL_TO_NATIVE[placement]
    }

    private val _showNativeAd = MutableStateFlow(false)
    val showNativeAd: StateFlow<Boolean> = _showNativeAd.asStateFlow()

    /** Tracks which native placement is currently active (for cleanup) */
    @Volatile
    private var activeNativePlacement: String? = null

    /**
     * Called when eligible interstitial ad is shown on screen.
     * Starts loading the native ad in background while user watches the interstitial.
     *
     * @param interstitialPlacement The interstitial placement now showing
     */
    fun onInterstitialShown(interstitialPlacement: String) {
        val nativePlacement = nativePlacementFor(interstitialPlacement) ?: return
        val isEnabled = adPlacementConfigService.isPlacementEnabled(nativePlacement)
        if (!isEnabled) {
            Log.d(TAG, "onInterstitialShown: $nativePlacement disabled, skipping preload")
            return
        }

        Log.d(TAG, "onInterstitialShown: loading $nativePlacement")
        coroutineScope.launch {
            try {
                adsLoaderService.loadNative(nativePlacement)
                Log.d(TAG, "Native ad loaded ($nativePlacement)")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load native ad ($nativePlacement)", e)
            }
        }
    }

    /**
     * Called when eligible interstitial ad closes.
     * Shows native ad if loaded and ready, otherwise skips silently.
     *
     * @param interstitialPlacement The interstitial placement that just closed
     */
    fun onInterstitialClosed(interstitialPlacement: String) {
        val nativePlacement = nativePlacementFor(interstitialPlacement)
        if (nativePlacement == null) {
            Log.d(TAG, "onInterstitialClosed: no native mapping for $interstitialPlacement")
            return
        }

        val isEnabled = adPlacementConfigService.isPlacementEnabled(nativePlacement)
        Log.d(TAG, "onInterstitialClosed: placement=$nativePlacement enabled=$isEnabled")

        if (!isEnabled) {
            return
        }

        val isReady = adsLoaderService.isNativeAdReady(nativePlacement)
        Log.d(TAG, "onInterstitialClosed: native ad ready=$isReady")

        if (isReady) {
            activeNativePlacement = nativePlacement
            _showNativeAd.value = true
            Log.d(TAG, "Showing native ad ($nativePlacement)")
        } else {
            Log.d(TAG, "Native not ready, skipping")
        }
    }

    /**
     * Get the currently active native ad placement (for rendering the correct ad).
     */
    fun getActiveNativePlacement(): String? = activeNativePlacement

    /**
     * Called when user closes the fullscreen native ad.
     */
    fun onNativeAdClosed() {
        val placement = activeNativePlacement
        Log.d(TAG, "User closed native ad ($placement)")

        _showNativeAd.value = false
        activeNativePlacement = null

        if (placement != null) {
            try {
                adsLoaderService.destroyNative(placement)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to destroy native ad ($placement)", e)
            }
        }
    }
}
