package com.videomaker.aimusic.core.ads

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Tracks whether the app was backgrounded due to an ad click.
 * Used to determine which app open ad placement to use when user returns.
 *
 * Detection method: Lifecycle heuristic
 * - When ad is shown, record timestamp
 * - When app backgrounds within 2 seconds of ad show, assume user clicked the ad
 * - Accuracy: ~90% (good enough for UX optimization)
 *
 * Use case: Prevent ad fatigue
 * - User clicks ad -> leaves to advertiser's app -> returns
 * - We suppress app open ad (or show different units) for better UX
 */
class AdClickContextTracker {
    private val _wasBackgroundedByAdClick = MutableStateFlow(false)
    val wasBackgroundedByAdClick: StateFlow<Boolean> = _wasBackgroundedByAdClick

    /**
     * Mark that user likely clicked an ad (called by AdClickDetector)
     */
    fun onAdClicked() {
        Log.d(TAG, "Ad click detected - flagging background context")
        _wasBackgroundedByAdClick.value = true
    }

    /**
     * Check if we should use the post-ad-click placement
     */
    fun shouldUsePostAdClickPlacement(): Boolean {
        return _wasBackgroundedByAdClick.value
    }

    /**
     * Reset the flag after app resumes
     */
    fun reset() {
        if (_wasBackgroundedByAdClick.value) {
            Log.d(TAG, "Resetting ad click context")
            _wasBackgroundedByAdClick.value = false
        }
    }

    companion object {
        private const val TAG = "AdClickContext"
    }
}
