package com.videomaker.aimusic.modules.editor

import android.app.Activity
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.alcheclub.lib.acccore.ads.loader.AdsLoaderException
import co.alcheclub.lib.acccore.ads.loader.AdsLoaderService
import co.alcheclub.lib.acccore.ads.state.AdsLoadingState
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

    // Watch Ad Dialog state
    private val _showWatchAdDialog = MutableStateFlow(false)
    val showWatchAdDialog: StateFlow<Boolean> = _showWatchAdDialog.asStateFlow()

    // Effect set waiting to be unlocked (UI will handle ad presentation)
    private val _pendingUnlockEffectSet = MutableStateFlow<EffectSet?>(null)
    val pendingUnlockEffectSet: StateFlow<EffectSet?> = _pendingUnlockEffectSet.asStateFlow()

    // Unlocked effect set IDs (from manager)
    val unlockedEffectSetIds = unlockedEffectSetsManager.unlockedEffectSetIds

    // Error message for ad failures
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

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
        if (!effectSet.isPremium) return true

        // Otherwise, check if unlocked locally
        return unlockedEffectSetsManager.isUnlocked(effectSet.id)
    }

    /**
     * Handle effect set click.
     * If locked, show watch ad dialog. If unlocked, pass to callback.
     *
     * @param effectSet The clicked effect set
     * @param onUnlockedEffectSetSelected Callback when effect set is unlocked
     */
    fun onEffectSetClick(
        effectSet: EffectSet,
        onUnlockedEffectSetSelected: (EffectSet) -> Unit
    ) {
        if (isEffectSetUnlocked(effectSet)) {
            // Unlocked - proceed with selection
            onUnlockedEffectSetSelected(effectSet)
        } else {
            // Locked - show watch ad dialog
            _pendingUnlockEffectSet.value = effectSet
            _showWatchAdDialog.value = true
        }
    }

    /**
     * User dismissed watch ad dialog (clicked "Close")
     */
    fun onWatchAdDialogDismiss() {
        _showWatchAdDialog.value = false
        _pendingUnlockEffectSet.value = null
    }

    /**
     * User confirmed watching ad (clicked "Watch Ad")
     * UI layer will handle ad presentation after this
     */
    fun onWatchAdConfirmed() {
        _showWatchAdDialog.value = false
        // pendingUnlockEffectSet remains set - UI will use it to show ad
    }

    /**
     * Called by UI after user earns reward from watching ad.
     * Unlocks the pending effect set and notifies success.
     *
     * @param onUnlockSuccess Callback when effect set is unlocked
     */
    suspend fun onRewardEarned(onUnlockSuccess: (EffectSet) -> Unit) {
        val effectSetToUnlock = _pendingUnlockEffectSet.value
        if (effectSetToUnlock == null) {
            android.util.Log.w("EffectSetViewModel", "No effect set to unlock")
            return
        }

        android.util.Log.d("EffectSetViewModel", "✅ User earned reward - unlocking effect set: ${effectSetToUnlock.id}")

        // Unlock effect set locally
        unlockedEffectSetsManager.unlockEffectSet(effectSetToUnlock.id)

        // Notify success callback
        onUnlockSuccess(effectSetToUnlock)

        // Clear pending
        _pendingUnlockEffectSet.value = null
    }

    /**
     * Called by UI when ad fails to load or user closes ad without watching.
     * Clears pending state.
     */
    fun onAdFailed() {
        android.util.Log.d("EffectSetViewModel", "❌ Ad failed or user cancelled")
        _pendingUnlockEffectSet.value = null
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
