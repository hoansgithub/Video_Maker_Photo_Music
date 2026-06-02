package com.videomaker.aimusic.core.ads

import android.app.Application
import android.util.Log
import co.alcheclub.lib.acccore.ads.loader.AdsLoaderService
import com.videomaker.aimusic.core.constants.AdPlacement
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Handles ad click detection and triggers post-click app open ad loading.
 *
 * **IMPORTANT: For BANNER and NATIVE ads ONLY**
 * - Banner ads: Inline ads that stay visible while user browses
 * - Native ads: Content-style ads embedded in UI
 * - NOT for interstitial/rewarded (they use their own click callbacks)
 *
 * How it works:
 * 1. When user clicks banner/native ad -> onAdClick() is called
 * 2. Immediately loads ad_appopen_aoa_after_click placement
 * 3. Sets flag so app knows to use post-click placement on resume
 * 4. User backgrounds to advertiser app
 * 5. User returns -> Post-click ad is already loaded and ready
 *
 * Usage:
 * ```kotlin
 * NativeAdView(
 *     placement = AdPlacement.NATIVE_HOME,
 *     onAdClicked = { placement ->
 *         adClickDetector.onAdClick(placement)
 *     }
 * )
 * ```
 */
class AdClickDetector(
    private val application: Application,
    private val adClickContextTracker: AdClickContextTracker,
    private val adsLoaderService: AdsLoaderService,
    private val applicationScope: CoroutineScope
) {
    init {
        Log.d(TAG, "AdClickDetector initialized")
    }

    /**
     * Called when user clicks a banner or native ad.
     * Immediately loads the post-click app open ad placement and sets flag.
     *
     * @param placement The placement ID of the clicked ad (for logging)
     */
    fun onAdClick(placement: String) {
        Log.d(TAG, "Ad clicked: $placement")

        // Flag the click context
        adClickContextTracker.onAdClicked()

        // Load post-click app open ad placement immediately
        // By the time user returns from advertiser app, the ad will be ready
        // Uses application scope to survive Activity destruction
        // IMPORTANT: Must use Dispatchers.Main because AdMob's AppOpenAd.load() requires main thread
        applicationScope.launch(Dispatchers.Main) {
            Log.d(TAG, "Loading post-click app open ad: ${AdPlacement.APP_OPEN_AFTER_AD_CLICK}")
            try {
                adsLoaderService.loadAppOpen(AdPlacement.APP_OPEN_AFTER_AD_CLICK)
                Log.d(TAG, "Post-click app open ad loaded successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load post-click app open ad: ${e.message}", e)
                // Don't fail - placement switching will still work, just no ad will show
            }
        }
    }

    companion object {
        private const val TAG = "AdClickDetector"
    }
}
