package com.videomaker.aimusic.core.ads

import android.app.Activity
import co.alcheclub.lib.acccore.ads.helpers.InterstitialAdHelper
import co.alcheclub.lib.acccore.ads.loader.AdsLoaderService
import co.alcheclub.lib.acccore.ads.mediators.admob.AdMobMediator
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * App-level extension for InterstitialAdHelper
 *
 * Provides app-specific dependencies and simplified API for showing interstitial ads.
 * This object wraps ACCCore's InterstitialAdHelper with Video Maker-specific configuration.
 *
 * ## Features:
 * - Automatic dependency injection (AdMobMediator, config)
 * - Preload ads with timeout protection
 * - Show ads with frequency cap management
 * - Action callbacks for navigation after ad closes
 *
 * ## Usage in RootViewModel:
 * ```kotlin
 * // Preload during initialization
 * InterstitialAdHelperExt.preloadInterstitial(
 *     adsLoaderService = adsLoaderService,
 *     placement = AdPlacement.INTERSTITIAL_SPLASH,
 *     loadTimeoutMillis = 30000L,
 *     showLoadingOverlay = false
 * )
 *
 * // Show after loading completes
 * InterstitialAdHelperExt.showInterstitial(
 *     adsLoaderService = adsLoaderService,
 *     activity = activity,
 *     placement = AdPlacement.INTERSTITIAL_SPLASH,
 *     action = { proceedToNextScreen() },
 *     bypassFrequencyCap = true
 * )
 * ```
 */
object InterstitialAdHelperExt : KoinComponent {

    private val adMobMediator: AdMobMediator by inject()
    private val adPlacementConfigService: AdPlacementConfigService by inject()

    /**
     * Preload interstitial ad
     *
     * Loads the ad in advance so it's ready to show instantly.
     * Use during app initialization or screen transitions.
     *
     * @param adsLoaderService Ad loading service from ACCCore
     * @param placement Ad placement ID (e.g., AdPlacement.INTERSTITIAL_SPLASH)
     * @param loadTimeoutMillis Max time to wait for ad load (null = no timeout)
     * @param showLoadingOverlay Whether to show loading overlay during load
     * @return true if ad loaded successfully, false if failed or timed out
     */
    suspend fun preloadInterstitial(
        adsLoaderService: AdsLoaderService,
        placement: String,
        loadTimeoutMillis: Long? = null,
        showLoadingOverlay: Boolean = true
    ): Boolean {
        return InterstitialAdHelper.preloadInterstitial(
            adsLoaderService = adsLoaderService,
            placement = placement,
            loadTimeoutMillis = loadTimeoutMillis,
            showLoadingOverlay = showLoadingOverlay
        )
    }

    /**
     * Show interstitial ad with action callback
     *
     * Shows a preloaded (or loads on-demand) interstitial ad.
     * Executes the action callback when ad closes or fails to show.
     *
     * @param adsLoaderService Ad loading service from ACCCore
     * @param activity Activity context for showing the ad
     * @param placement Ad placement ID (e.g., AdPlacement.INTERSTITIAL_SPLASH)
     * @param action Callback executed when ad closes (use for navigation)
     * @param onShown Optional callback when ad is shown (before user closes it)
     * @param bypassFrequencyCap If true, ignore frequency cap (use for splash ads)
     * @param loadTimeoutMillis Max time to wait if ad not preloaded (null = no timeout)
     * @param showLoadingOverlay Whether to show loading overlay if loading on-demand
     */
    fun showInterstitial(
        adsLoaderService: AdsLoaderService,
        activity: Activity,
        placement: String,
        action: suspend () -> Unit,
        onShown: (() -> Unit)? = null,
        bypassFrequencyCap: Boolean = false,
        loadTimeoutMillis: Long? = null,
        showLoadingOverlay: Boolean = true
    ) {
        InterstitialAdHelper.showInterstitial(
            adsLoaderService = adsLoaderService,
            adMobMediator = adMobMediator,
            activity = activity,
            placement = placement,
            action = action,
            onShown = onShown,
            bypassFrequencyCap = bypassFrequencyCap,
            loadTimeoutMillis = loadTimeoutMillis,
            showLoadingOverlay = showLoadingOverlay,
            interstitialIntervalSeconds = adPlacementConfigService.interstitialIntervalSeconds
        )
    }

    /**
     * Check if interstitial is currently showing
     *
     * @return true if an interstitial ad is visible
     */
    fun isInterstitialShowing(): Boolean {
        return InterstitialAdHelper.isInterstitialShowing()
    }

    /**
     * Check if safe to show resume interstitial
     *
     * Used when app returns from background to avoid showing ad immediately.
     *
     * @param delayMillis Minimum time app must be in background before showing ad
     * @return true if safe to show, false if too soon after resume
     */
    suspend fun canShowResumeInterstitial(delayMillis: Long = 300): Boolean {
        return InterstitialAdHelper.canShowResumeInterstitial(
            adMobMediator = adMobMediator,
            delayMillis = delayMillis
        )
    }
}
