package com.videomaker.aimusic.core.ads

import android.util.Log
import co.alcheclub.lib.acccore.ads.loader.AdsLoaderService
import com.videomaker.aimusic.BuildConfig
import com.videomaker.aimusic.core.constants.AdPlacement
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Manager for post-reward fullscreen native ad functionality.
 *
 * Responsibilities:
 * - Preload native ad when a rewarded ad is shown
 * - Track loaded state and reward-ad active state
 * - Show fullscreen native ad immediately when loaded (while reward ad potentially still active)
 * - Prevent display if reward ad closes before native ad loads
 * - Clear ad on user close
 *
 * Flow:
 * 1. When rewarded ad shows → mark reward as active, start preloading native ad
 * 2. Native ad loads → check if reward still active
 *    ├─ YES → show fullscreen native ad immediately (while reward may still be on screen)
 *    └─ NO  → skip display (reward already closed)
 * 3. When rewarded ad closes → mark reward as inactive
 * 4. User closes native ad → clear state and destroy ad
 *
 * Centralized: hooked once inside [RewardedAdPresenter], so every rewarded placement
 * (8 locations) triggers it automatically.
 *
 * @param adsLoaderService ACCCore-Ads service for loading/showing ads
 * @param coroutineScope MUST be the application scope (app-level lifecycle)
 */
class PostRewardNativeAdManager(
    private val adsLoaderService: AdsLoaderService,
    private val coroutineScope: CoroutineScope
) {
    companion object {
        private const val TAG = "PostRewardNativeAdMgr"

        // Minimum interval between preloads to avoid excessive loading
        private const val PRELOAD_COOLDOWN_MS = 30_000L
    }

    // State: whether to show fullscreen native ad
    private val _showNativeAd = MutableStateFlow(false)
    val showNativeAd: StateFlow<Boolean> = _showNativeAd.asStateFlow()

    // State: whether native ad is loaded and ready
    private val _isLoaded = MutableStateFlow(false)
    val isLoaded: StateFlow<Boolean> = _isLoaded.asStateFlow()

    // Track if reward ad is still active (showing)
    @Volatile
    private var isRewardAdActive: Boolean = false

    // Track last preload time to avoid excessive preloading
    @Volatile
    private var lastPreloadTimestamp: Long = 0

    /**
     * Called when a rewarded ad is shown.
     * Starts preloading native ad and shows it immediately when loaded (if reward still active).
     */
    fun onRewardedAdShown() {
        // Mark reward ad as active
        isRewardAdActive = true

        // Check if already loaded
        if (adsLoaderService.isNativeAdReady(AdPlacement.NATIVE_POST_REWARD)) {
            _isLoaded.value = true
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "✅ Post-reward native ad already loaded")
            }
            // Show immediately since it's already ready
            if (isRewardAdActive) {
                _showNativeAd.value = true
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "📺 Showing post-reward native ad (already loaded)")
                }
            }
            return
        }

        // Check cooldown to avoid excessive preloading (minimum 30 seconds between preloads)
        val now = System.currentTimeMillis()
        if (now - lastPreloadTimestamp < PRELOAD_COOLDOWN_MS) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "⏭️ Skipping preload (cooldown not elapsed)")
            }
            return
        }

        // Start preloading
        lastPreloadTimestamp = now
        coroutineScope.launch {
            try {
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "🔄 Preloading post-reward native ad...")
                }

                adsLoaderService.loadNative(AdPlacement.NATIVE_POST_REWARD)

                // Check if loaded successfully
                if (adsLoaderService.isNativeAdReady(AdPlacement.NATIVE_POST_REWARD)) {
                    _isLoaded.value = true
                    if (BuildConfig.DEBUG) {
                        Log.d(TAG, "✅ Post-reward native ad preloaded successfully")
                    }

                    // Show immediately if reward ad is still active
                    if (isRewardAdActive) {
                        _showNativeAd.value = true
                        if (BuildConfig.DEBUG) {
                            Log.d(TAG, "📺 Showing post-reward native ad (loaded while reward active)")
                        }
                    } else {
                        if (BuildConfig.DEBUG) {
                            Log.d(TAG, "⏭️ Reward ad already closed, skipping display")
                        }
                    }
                } else {
                    _isLoaded.value = false
                    if (BuildConfig.DEBUG) {
                        Log.d(TAG, "⚠️ Post-reward native ad preload failed")
                    }
                }
            } catch (e: Exception) {
                _isLoaded.value = false
                Log.e(TAG, "❌ Failed to preload post-reward native ad", e)
            }
        }
    }

    /**
     * Called when the rewarded ad closes (user finished watching or dismissed).
     * Marks reward as inactive to prevent late display.
     */
    fun onRewardedAdClosed() {
        // Mark reward ad as inactive
        isRewardAdActive = false

        if (BuildConfig.DEBUG) {
            Log.d(TAG, "🔚 Reward ad closed (native ad will not show if loaded after this)")
        }
    }

    /**
     * Called when user closes the fullscreen native ad.
     * Clears state and destroys the ad.
     */
    fun onNativeAdClosed() {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "👋 User closed post-reward native ad")
        }

        _showNativeAd.value = false
        _isLoaded.value = false

        // Destroy the ad (cleanup)
        try {
            adsLoaderService.destroyNative(AdPlacement.NATIVE_POST_REWARD)
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "🗑️ Post-reward native ad destroyed")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to destroy post-reward native ad", e)
        }
    }

    /**
     * Reset state (for testing or manual reset).
     */
    fun reset() {
        _showNativeAd.value = false
        _isLoaded.value = false
        lastPreloadTimestamp = 0
    }
}
