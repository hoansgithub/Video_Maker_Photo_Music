package com.videomaker.aimusic.core.ads

import android.util.Log
import co.alcheclub.lib.acccore.ads.loader.AdsLoaderService
import com.videomaker.aimusic.core.constants.AdPlacement
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manager for post-interstitial fullscreen native ad (Drama app pattern).
 *
 * Currently only triggered by splash/open-app interstitials:
 * - INTERSTITIAL_SPLASH_HIGH / SPLASH_LOW (first install)
 * - INTERSTITIAL_OPEN_APP_HIGH / OPEN_APP_LOW (returning users)
 *
 * Flow:
 * 1. Native ad preloaded early during splash loading (RootViewModel.preloadNativeAds)
 * 2. Interstitial closes → if native loaded, show fullscreen overlay; if not, skip
 * 3. User closes native ad → cleanup and proceed
 *
 * Non-blocking: features proceed normally if native ad is unavailable.
 *
 * @param adsLoaderService ACCCore-Ads service for loading/showing ads
 * @param adPlacementConfigService Placement config for enabled check
 */
class PostInterNativeAdManager(
    private val adsLoaderService: AdsLoaderService,
    private val adPlacementConfigService: AdPlacementConfigService
) {
    companion object {
        private const val TAG = "PostInterNativeAdMgr"

        /** Interstitial placements that trigger post-interstitial native ad */
        private val ELIGIBLE_PLACEMENTS = setOf(
            AdPlacement.INTERSTITIAL_SPLASH_HIGH,
            AdPlacement.INTERSTITIAL_SPLASH_LOW,
            AdPlacement.INTERSTITIAL_OPEN_APP_HIGH,
            AdPlacement.INTERSTITIAL_OPEN_APP_LOW
        )

        /** Check if a placement should trigger post-interstitial native ad */
        fun isSplashPlacement(placement: String): Boolean = placement in ELIGIBLE_PLACEMENTS
    }

    private val _showNativeAd = MutableStateFlow(false)
    val showNativeAd: StateFlow<Boolean> = _showNativeAd.asStateFlow()

    /**
     * Called when eligible interstitial ad closes.
     * Shows native ad if preloaded and ready, otherwise skips silently.
     */
    fun onInterstitialClosed() {
        val isEnabled = adPlacementConfigService.isPlacementEnabled(AdPlacement.NATIVE_AFTER_SPLASH)
        Log.d(TAG, "onInterstitialClosed: placement enabled=$isEnabled")

        if (!isEnabled) {
            return
        }

        val isReady = adsLoaderService.isNativeAdReady(AdPlacement.NATIVE_AFTER_SPLASH)
        Log.d(TAG, "onInterstitialClosed: native ad ready=$isReady")

        if (isReady) {
            _showNativeAd.value = true
            Log.d(TAG, "Showing native after-splash ad")
        } else {
            Log.d(TAG, "Native not ready, skipping")
        }
    }

    /**
     * Called when user closes the fullscreen native ad.
     */
    fun onNativeAdClosed() {
        Log.d(TAG, "User closed native after-splash ad")

        _showNativeAd.value = false

        try {
            adsLoaderService.destroyNative(AdPlacement.NATIVE_AFTER_SPLASH)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to destroy native after-splash ad", e)
        }
    }
}
