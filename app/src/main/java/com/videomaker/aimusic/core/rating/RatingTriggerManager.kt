package com.videomaker.aimusic.core.rating

import android.util.Log
import com.videomaker.aimusic.BuildConfig
import com.videomaker.aimusic.core.data.local.PreferencesManager
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * Pure function for testing trigger conditions in isolation.
 *
 * @param videoCreateCount total successful exports so far (after increment)
 * @param shownCount how many times the popup has been shown
 * @param completed true if user has fully engaged (submitted feedback or rated 4-5 stars)
 * @param firstShow show popup after this many videos created
 * @param cap maximum number of times to re-show
 */
fun shouldShowRating(
    videoCreateCount: Int,
    shownCount: Int,
    completed: Boolean,
    firstShow: Int,
    cap: Int
): Boolean {
    if (completed) return false
    if (videoCreateCount < firstShow) return false
    if (shownCount >= cap) return false
    return true
}

/**
 * Manages the rating popup trigger logic.
 *
 * Trigger conditions:
 * - User has NOT completed the rating flow (submitted feedback or rated 4-5 stars)
 * - Total successful video exports >= FIRST_SHOW (default 1)
 * - Popup has been shown < CAP times (default 3)
 *
 * Usage:
 * - Call onVideoCreated() when ExportUiState.Success is reached
 * - Collect showRatingPopup Flow to react to trigger
 * - Call onRatingShown() when Satisfaction popup appears
 * - Call onRatingCompleted() when user submits feedback or rates 4-5 stars
 */
class RatingTriggerManager(
    private val preferencesManager: PreferencesManager
) {
    companion object {
        private const val TAG = "RatingTriggerManager"
        const val FIRST_SHOW = 1
        const val CAP = 3
    }

    private val _showRatingPopup = Channel<Unit>(Channel.BUFFERED)
    val showRatingPopup = _showRatingPopup.receiveAsFlow()

    /**
     * Call when a video export completes successfully (ExportProgress.Success).
     * Emits to showRatingPopup if all trigger conditions are met.
     */
    fun onVideoCreated() {
        preferencesManager.ratingVideoCreateCount++

        val videoCount = preferencesManager.ratingVideoCreateCount
        val shownCount = preferencesManager.ratingShownCount
        val completed = preferencesManager.ratingCompleted

        if (BuildConfig.DEBUG) {
            Log.d(TAG, "onVideoCreated: count=$videoCount, shown=$shownCount, completed=$completed")
        }

        if (shouldShowRating(videoCount, shownCount, completed, FIRST_SHOW, CAP)) {
            if (BuildConfig.DEBUG) Log.d(TAG, "✅ Rating conditions met — triggering popup")
            _showRatingPopup.trySend(Unit)
        } else {
            if (BuildConfig.DEBUG) Log.d(TAG, "❌ Rating conditions not met")
        }
    }

    /**
     * Call when the Satisfaction popup first appears.
     * Increments the shown count to track re-show cap.
     */
    fun onRatingShown() {
        preferencesManager.ratingShownCount++
        if (BuildConfig.DEBUG) Log.d(TAG, "onRatingShown: shownCount=${preferencesManager.ratingShownCount}")
    }

    /**
     * Call when user submits feedback or rates 4-5 stars.
     * Marks the rating as permanently completed — popup never shows again.
     */
    fun onRatingCompleted() {
        preferencesManager.ratingCompleted = true
        if (BuildConfig.DEBUG) Log.d(TAG, "onRatingCompleted: popup permanently dismissed")
    }
}
