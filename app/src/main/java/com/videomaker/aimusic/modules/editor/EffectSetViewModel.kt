package com.videomaker.aimusic.modules.editor

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.videomaker.aimusic.domain.model.EffectSet
import com.videomaker.aimusic.domain.usecase.GetEffectSetsPagedUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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
    private val getEffectSetsPagedUseCase: GetEffectSetsPagedUseCase
) : ViewModel() {

    companion object {
        private const val PAGE_SIZE = 30
    }

    // UI State
    private val _uiState = MutableStateFlow<EffectSetUiState>(EffectSetUiState.Loading)
    val uiState: StateFlow<EffectSetUiState> = _uiState.asStateFlow()

    // Pagination state
    private var currentOffset = 0
    private var loadMoreJob: Job? = null

    init {
        loadFirstPage()
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
