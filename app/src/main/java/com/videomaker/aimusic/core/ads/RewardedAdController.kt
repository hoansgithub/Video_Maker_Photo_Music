package com.videomaker.aimusic.core.ads

import co.alcheclub.lib.acccore.ads.loader.AdsLoaderService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Reusable Rewarded Ad Controller
 *
 * Eliminates duplicated code across all rewarded ad placements by providing
 * a common state management and flow control for:
 * - Watch ad dialog state
 * - Ad presentation triggers
 * - Reward callback handling
 * - Error states
 *
 * Usage in ViewModel:
 * ```kotlin
 * class MyViewModel(
 *     private val adsLoaderService: AdsLoaderService,
 *     private val unlockedManager: UnlockedItemsManager
 * ) : ViewModel() {
 *     private val rewardedAdController = RewardedAdController(
 *         placement = AdPlacement.REWARD_UNLOCK_ITEM,
 *         adsLoaderService = adsLoaderService,
 *         viewModelScope = viewModelScope
 *     )
 *
 *     val showWatchAdDialog = rewardedAdController.showWatchAdDialog
 *     val shouldPresentAd = rewardedAdController.shouldPresentAd
 *
 *     fun onItemClick(item: Item) {
 *         rewardedAdController.requestAd(
 *             onReward = { unlockItem(item) },
 *             checkEnabled = { adsLoaderService.canLoadAd(placement) }
 *         )
 *     }
 * }
 * ```
 */
class RewardedAdController(
    private val placement: String,
    private val adsLoaderService: AdsLoaderService,
    private val viewModelScope: CoroutineScope
) {
    // Watch ad dialog state
    private val _showWatchAdDialog = MutableStateFlow(false)
    val showWatchAdDialog: StateFlow<Boolean> = _showWatchAdDialog.asStateFlow()

    // Trigger to present ad (set when user clicks "Watch Ad" in dialog)
    private val _shouldPresentAd = MutableStateFlow(false)
    val shouldPresentAd: StateFlow<Boolean> = _shouldPresentAd.asStateFlow()

    // Pending reward action (stored until ad completes)
    private var pendingRewardAction: (() -> Unit)? = null

    /**
     * Request to show rewarded ad
     *
     * @param onReward Callback executed when user earns reward
     * @param onSkip Optional callback when ad is disabled (executes onReward by default)
     * @param checkEnabled Optional check if ad is enabled (defaults to true)
     */
    fun requestAd(
        onReward: () -> Unit,
        onSkip: (() -> Unit)? = null,
        checkEnabled: () -> Boolean = { true }
    ) {
        // Check if ad is enabled
        if (!checkEnabled()) {
            // Ad disabled - execute skip action or reward action
            android.util.Log.d("RewardedAdController", "⏭️ Ad disabled for $placement - proceeding without ad")
            viewModelScope.launch {
                (onSkip ?: onReward)()
            }
            return
        }

        // Ad enabled - store reward action and show dialog
        pendingRewardAction = onReward
        _showWatchAdDialog.value = true
    }

    /**
     * User dismissed watch ad dialog without watching
     */
    fun onDialogDismiss() {
        _showWatchAdDialog.value = false
        _shouldPresentAd.value = false
        pendingRewardAction = null
    }

    /**
     * User confirmed they want to watch ad - triggers ad presentation
     */
    fun onDialogConfirm() {
        _showWatchAdDialog.value = false
        _shouldPresentAd.value = true
    }

    /**
     * Rewarded ad completed successfully - execute reward action
     */
    fun onRewardEarned() {
        viewModelScope.launch {
            pendingRewardAction?.invoke()
            clearState()
        }
    }

    /**
     * Rewarded ad failed or user canceled
     */
    fun onAdFailed() {
        clearState()
    }

    private fun clearState() {
        _shouldPresentAd.value = false
        pendingRewardAction = null
    }
}
