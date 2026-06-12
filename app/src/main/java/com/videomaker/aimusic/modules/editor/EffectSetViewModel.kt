package com.videomaker.aimusic.modules.editor

import android.app.Activity
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.alcheclub.lib.acccore.ads.loader.AdsLoaderException
import co.alcheclub.lib.acccore.ads.loader.AdsLoaderService
import co.alcheclub.lib.acccore.ads.state.AdsLoadingState
import com.videomaker.aimusic.core.ads.RewardedAdController
import com.videomaker.aimusic.core.constants.AdPlacement
import com.videomaker.aimusic.core.storage.UnlockedEffectSetsManager
import com.videomaker.aimusic.domain.model.EffectSet
import com.videomaker.aimusic.domain.usecase.GetEffectSetsPagedUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

// ============================================
// UI STATE
// ============================================

sealed class EffectSetUiState {
    data object Loading : EffectSetUiState()

    @Immutable
    data class Success(
        val effectSets: List<EffectSet>,
        val hasMorePages: Boolean,
        val isLoadingMore: Boolean
    ) : EffectSetUiState()

    data class Error(val message: String) : EffectSetUiState()
}

sealed class DownloadState {
    data object NotDownloaded : DownloadState()
    data class Downloading(val progress: Float) : DownloadState()
    data object Downloaded : DownloadState()
}

// ============================================
// VIEW MODEL
// ============================================

class EffectSetViewModel(
    private val getEffectSetsPagedUseCase: GetEffectSetsPagedUseCase,
    private val unlockedEffectSetsManager: UnlockedEffectSetsManager,
    private val adsLoaderService: AdsLoaderService
) : ViewModel() {

    companion object {
        private const val PAGE_SIZE = 30
    }

    // UI State
    private val _uiState = MutableStateFlow<EffectSetUiState>(EffectSetUiState.Loading)
    val uiState: StateFlow<EffectSetUiState> = _uiState.asStateFlow()

    // Rewarded ad controller for effect set unlock
    private val rewardedAdController = RewardedAdController(
        placement = AdPlacement.REWARD_UNLOCK_EFFECT_SET,
        viewModelScope = viewModelScope
    )

    // Expose rewarded ad state
    val shouldPresentAd: StateFlow<Boolean> = rewardedAdController.shouldPresentAd

    // Effect set waiting to be unlocked (stored for callback after ad)
    private val _pendingUnlockEffectSet = MutableStateFlow<EffectSet?>(null)
    val pendingUnlockEffectSet: StateFlow<EffectSet?> = _pendingUnlockEffectSet.asStateFlow()

    // Unlocked effect set IDs (from manager)
    // Active/clicked effect set ID (specifically clicked by user, even if still downloading)
    private val _activeEffectSetId = MutableStateFlow<String?>(null)
    val activeEffectSetId: StateFlow<String?> = _activeEffectSetId.asStateFlow()

    // Unlocked effect set IDs (from manager)
    val unlockedEffectSetIds = unlockedEffectSetsManager.unlockedEffectSetIds

    // Callback for when effect set is unlocked (stored until reward earned)
    private var onUnlockSuccessCallback: ((EffectSet) -> Unit)? = null

    // Error message for ad failures
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // Download states
    private val _downloadStates = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    val downloadStates: StateFlow<Map<String, DownloadState>> = _downloadStates.asStateFlow()

    // Pagination state
    private var currentOffset = 0
    private var loadMoreJob: Job? = null

    init {
        loadFirstPage()
    }

    /**
     * Check if an effect set is unlocked (free or unlocked locally).
     *
     * @param effectSet The effect set to check
     * @return true if free or unlocked, false if locked
     */
    fun isEffectSetUnlocked(effectSet: EffectSet): Boolean {
        // If it's not premium in database, it's always free
        val requiresUnlock = effectSet.isPremium
        if (!requiresUnlock) return true

        // Otherwise, check if unlocked locally
        return unlockedEffectSetsManager.isUnlocked(effectSet.id)
    }

    /**
     * Handle effect set click.
     * If locked, present rewarded ad. If unlocked, pass to callback.
     *
     * @param effectSet The clicked effect set
     * @param onUnlockedEffectSetSelected Callback when effect set is unlocked
     */
    fun onEffectSetClick(
        effectSet: EffectSet,
        onUnlockedEffectSetSelected: (EffectSet) -> Unit
    ) {
        _activeEffectSetId.value = effectSet.id
        if (isEffectSetUnlocked(effectSet)) {
            // Unlocked - proceed with selection
            onUnlockedEffectSetSelected(effectSet)
        } else {
            // Locked - request ad directly
            _pendingUnlockEffectSet.value = effectSet
            onUnlockSuccessCallback = onUnlockedEffectSetSelected
            requestPendingUnlockAd()
        }
    }

    /**
     * Requests rewarded ad for the pending effect set.
     */
    fun requestPendingUnlockAd() {
        val effectSet = _pendingUnlockEffectSet.value ?: return
        rewardedAdController.requestAd(
            onReward = {
                // Unlock effect set and call success callback
                viewModelScope.launch {
                    unlockedEffectSetsManager.unlockEffectSet(effectSet.id)
                    android.util.Log.d("EffectSetViewModel", "✅ Effect set unlocked: ${effectSet.id}")

                    onUnlockSuccessCallback?.invoke(effectSet)

                    // Clear state
                    _pendingUnlockEffectSet.value = null
                    onUnlockSuccessCallback = null
                }
            },
            checkEnabled = { adsLoaderService.canLoadAd(AdPlacement.REWARD_UNLOCK_EFFECT_SET) }
        )
    }

    /**
     * Cancels the pending unlock ad.
     */
    fun cancelPendingUnlock() {
        _pendingUnlockEffectSet.value = null
        onUnlockSuccessCallback = null
    }

    /**
     * Instantly marks an effect set as downloaded.
     */
    fun setSelectedEffectSetId(id: String?) {
        _activeEffectSetId.value = id
        if (id == null) return
        if (_downloadStates.value[id] is DownloadState.Downloaded) return
        _downloadStates.value = _downloadStates.value.toMutableMap().apply {
            put(id, DownloadState.Downloaded)
        }
    }

    /**
     * Triggers background download for the top 2 sorted effect sets.
     */
    private fun triggerAutoDownloads(effectSets: List<EffectSet>) {
        val targetSets = effectSets.filter { it.sortOrder == 1 || it.sortOrder == 2 }
            .takeIf { it.size >= 2 }
            ?: effectSets.take(2)

        targetSets.forEach { effectSet ->
            val currentState = _downloadStates.value[effectSet.id]
            if (currentState == null || currentState is DownloadState.NotDownloaded) {
                startDownload(effectSet.id)
            }
        }
    }

    /**
     * Start downloading an effect set.
     */
    fun startDownload(effectSetId: String, onFinished: () -> Unit = {}) {
        if (_downloadStates.value[effectSetId] is DownloadState.Downloaded) {
            onFinished()
            return
        }
        if (_downloadStates.value[effectSetId] is DownloadState.Downloading) {
            viewModelScope.launch {
                while (_downloadStates.value[effectSetId] is DownloadState.Downloading) {
                    delay(100)
                }
                if (_downloadStates.value[effectSetId] is DownloadState.Downloaded) {
                    onFinished()
                }
            }
            return
        }

        viewModelScope.launch {
            _downloadStates.value = _downloadStates.value.toMutableMap().apply {
                put(effectSetId, DownloadState.Downloading(0f))
            }
            for (progress in 1..10) {
                delay(200)
                _downloadStates.value = _downloadStates.value.toMutableMap().apply {
                    put(effectSetId, DownloadState.Downloading(progress / 10f))
                }
            }
            _downloadStates.value = _downloadStates.value.toMutableMap().apply {
                put(effectSetId, DownloadState.Downloaded)
            }
            onFinished()
        }
    }

    /**
     * Called by UI after user earns reward from watching ad
     */
    fun onRewardEarned() {
        rewardedAdController.onRewardEarned()
    }

    /**
     * Called by UI when ad fails to load or user closes ad without watching
     */
    fun onAdFailed() {
        rewardedAdController.onAdFailed()
        _pendingUnlockEffectSet.value = null
        onUnlockSuccessCallback = null
    }

    /**
     * Show error message (e.g., ad not available).
     * UI will display this in a Snackbar.
     *
     * @param message Error message to display
     */
    fun showError(message: String) {
        _errorMessage.value = message
    }

    /**
     * Clear error message after it's been shown
     */
    fun onErrorMessageShown() {
        _errorMessage.value = null
    }

    /**
     * Loads the first page of effect sets.
     */
    private fun loadFirstPage() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = EffectSetUiState.Loading
            currentOffset = 0

            val result = getEffectSetsPagedUseCase(offset = 0, limit = PAGE_SIZE)
            if (result.isSuccess) {
                val effectSets = result.getOrDefault(emptyList())
                _uiState.value = EffectSetUiState.Success(
                    effectSets = effectSets,
                    hasMorePages = effectSets.size >= PAGE_SIZE,
                    isLoadingMore = false
                )
                triggerAutoDownloads(effectSets)
            } else {
                _uiState.value = EffectSetUiState.Error("Failed to load effect sets. Please try again.")
            }
        }
    }

    /**
     * Loads the next page of effect sets.
     * Debounced to prevent multiple rapid calls.
     */
    fun loadNextPage() {
        val currentState = _uiState.value
        if (currentState !is EffectSetUiState.Success) return
        if (!currentState.hasMorePages || currentState.isLoadingMore) return

        // Cancel any pending load job (debounce)
        loadMoreJob?.cancel()

        loadMoreJob = viewModelScope.launch(Dispatchers.IO) {
            // Debounce delay to prevent rapid scrolling from triggering multiple loads
            delay(300)

            // Set loading more state
            _uiState.value = currentState.copy(isLoadingMore = true)

            // Calculate next offset
            currentOffset += PAGE_SIZE

            val result = getEffectSetsPagedUseCase(offset = currentOffset, limit = PAGE_SIZE)
            if (result.isSuccess) {
                val newItems = result.getOrDefault(emptyList())
                val allItems = currentState.effectSets + newItems

                _uiState.value = EffectSetUiState.Success(
                    effectSets = allItems,
                    hasMorePages = newItems.size >= PAGE_SIZE,
                    isLoadingMore = false
                )
            } else {
                // Keep existing items, just stop loading more
                _uiState.value = currentState.copy(
                    isLoadingMore = false,
                    hasMorePages = false
                )
            }
        }
    }

    /**
     * Retries loading the first page after an error.
     */
    fun onRetry() {
        loadFirstPage()
    }
}
