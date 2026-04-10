package com.videomaker.aimusic.core.ads

import android.app.Activity
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import co.alcheclub.lib.acccore.ads.loader.AdsLoaderException
import co.alcheclub.lib.acccore.ads.loader.AdsLoaderService
import co.alcheclub.lib.acccore.ads.state.AdsLoadingState
import com.videomaker.aimusic.R
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

/**
 * Reusable Rewarded Ad Presenter Composable
 *
 * Handles the common ad loading and presentation logic for all rewarded ad placements.
 * Eliminates duplicated LaunchedEffect code across screens.
 *
 * Features:
 * - Automatic ad loading with timeout
 * - Loading overlay display
 * - Error handling
 * - Reward callback execution
 *
 * Usage:
 * ```kotlin
 * @Composable
 * fun MyScreen(viewModel: MyViewModel) {
 *     val shouldPresentAd by viewModel.shouldPresentAd.collectAsStateWithLifecycle()
 *
 *     RewardedAdPresenter(
 *         shouldPresent = shouldPresentAd,
 *         placement = AdPlacement.REWARD_UNLOCK_ITEM,
 *         adsLoaderService = adsLoaderService,
 *         onRewardEarned = viewModel::onRewardEarned,
 *         onAdFailed = viewModel::onAdFailed
 *     )
 * }
 * ```
 */
@Composable
fun RewardedAdPresenter(
    shouldPresent: Boolean,
    placement: String,
    adsLoaderService: AdsLoaderService,
    onRewardEarned: () -> Unit,
    onAdFailed: () -> Unit,
    loadTimeoutMs: Long = 60_000L
) {
    val context = LocalContext.current
    val activity = context as? Activity

    LaunchedEffect(shouldPresent) {
        if (!shouldPresent) return@LaunchedEffect

        // Check if Activity is available
        if (activity == null) {
            android.util.Log.w("RewardedAdPresenter", "Activity unavailable for ad presentation")
            onAdFailed()
            return@LaunchedEffect
        }

        try {
            // 1. Check if ad is ready
            if (!adsLoaderService.isRewardedAdReady(placement)) {
                // 2. Show loading overlay
                AdsLoadingState.show(context.getString(R.string.ad_loading))

                // 3. Load ad with timeout
                withTimeout(loadTimeoutMs) {
                    adsLoaderService.loadRewarded(placement)
                }

                // 4. Hide loading overlay
                AdsLoadingState.hide()
            }

            // 5. Present ad and wait for result (blocking)
            val result = adsLoaderService.presentRewarded(
                placement = placement,
                activity = activity
            )

            // 6. Check if user earned reward
            if (result.earnedReward) {
                onRewardEarned()
            } else {
                android.util.Log.d("RewardedAdPresenter", "User did not earn reward (closed ad early)")
                onAdFailed()
            }

        } catch (e: TimeoutCancellationException) {
            android.util.Log.w("RewardedAdPresenter", "Ad load timeout for $placement")
            AdsLoadingState.hide()
            onAdFailed()
        } catch (e: AdsLoaderException) {
            android.util.Log.w("RewardedAdPresenter", "Ad load error for $placement: ${e.message}")
            AdsLoadingState.hide()
            onAdFailed()
        } catch (e: Exception) {
            android.util.Log.e("RewardedAdPresenter", "Unexpected error for $placement: ${e.message}", e)
            AdsLoadingState.hide()
            onAdFailed()
        }
    }
}
