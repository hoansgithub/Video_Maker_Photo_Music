package com.videomaker.aimusic.modules.weeklyranking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.videomaker.aimusic.domain.model.MusicSong
import com.videomaker.aimusic.domain.usecase.GetWeeklyRankingSongsUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// ============================================
// UI STATE
// ============================================

/**
 * State for the paginated weekly ranking list
 */
data class WeeklyRankingPageState(
    val songs: List<MusicSong> = emptyList(),
    val offset: Int = 0,
    val hasMore: Boolean = true,
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null
)

sealed class WeeklyRankingListUiState {
    data object Loading : WeeklyRankingListUiState()
    data class Success(val pageState: WeeklyRankingPageState) : WeeklyRankingListUiState()
    data class Error(val message: String) : WeeklyRankingListUiState()
}

// ============================================
// NAVIGATION EVENTS
// ============================================

sealed class WeeklyRankingNavigationEvent {
    data object NavigateBack : WeeklyRankingNavigationEvent()
    data class NavigateToAssetPicker(val songId: Long) : WeeklyRankingNavigationEvent()
}

// ============================================
// VIEW MODEL
// ============================================

class WeeklyRankingListViewModel(
    private val getWeeklyRankingSongsUseCase: GetWeeklyRankingSongsUseCase
) : ViewModel() {

    companion object {
        private const val PAGE_SIZE = 20
        private const val MAX_ITEMS = 100  // Maximum 100 items as requested
    }

    private val _uiState = MutableStateFlow<WeeklyRankingListUiState>(WeeklyRankingListUiState.Loading)
    val uiState: StateFlow<WeeklyRankingListUiState> = _uiState.asStateFlow()

    private val _navigationEvent = MutableStateFlow<WeeklyRankingNavigationEvent?>(null)
    val navigationEvent: StateFlow<WeeklyRankingNavigationEvent?> = _navigationEvent.asStateFlow()

    // Selected song for music player bottom sheet
    private val _selectedSong = MutableStateFlow<MusicSong?>(null)
    val selectedSong: StateFlow<MusicSong?> = _selectedSong.asStateFlow()

    private var currentPageState = WeeklyRankingPageState()

    init {
        loadFirstPage()
    }

    private fun loadFirstPage() {
        viewModelScope.launch {
            _uiState.value = WeeklyRankingListUiState.Loading

            val result = getWeeklyRankingSongsUseCase(offset = 0, limit = PAGE_SIZE)

            if (result.isSuccess) {
                val songs = result.getOrNull() ?: emptyList()
                currentPageState = WeeklyRankingPageState(
                    songs = songs,
                    offset = songs.size,
                    hasMore = songs.size >= PAGE_SIZE && songs.size < MAX_ITEMS,
                    isLoading = false
                )
                _uiState.value = WeeklyRankingListUiState.Success(currentPageState)
            } else {
                android.util.Log.e("WeeklyRanking", "loadFirstPage() failed: ${result.exceptionOrNull()?.message}")
                _uiState.value = WeeklyRankingListUiState.Error("Failed to load weekly ranking")
            }
        }
    }

    fun loadMore() {
        val state = currentPageState
        // Stop loading if already loading, no more items, or reached max limit
        if (state.isLoadingMore || !state.hasMore || state.songs.size >= MAX_ITEMS) {
            return
        }

        viewModelScope.launch {
            currentPageState = state.copy(isLoadingMore = true)
            _uiState.value = WeeklyRankingListUiState.Success(currentPageState)

            val result = getWeeklyRankingSongsUseCase(offset = state.offset, limit = PAGE_SIZE)

            if (result.isSuccess) {
                val newSongs = result.getOrNull() ?: emptyList()
                val allSongs = (state.songs + newSongs).distinctBy { it.id }.take(MAX_ITEMS)
                currentPageState = WeeklyRankingPageState(
                    songs = allSongs,
                    offset = state.offset + newSongs.size,
                    hasMore = newSongs.size >= PAGE_SIZE && allSongs.size < MAX_ITEMS,
                    isLoadingMore = false
                )
                _uiState.value = WeeklyRankingListUiState.Success(currentPageState)
            } else {
                android.util.Log.e("WeeklyRanking", "loadMore() failed: ${result.exceptionOrNull()?.message}")
                currentPageState = state.copy(
                    isLoadingMore = false,
                    error = "Failed to load more songs"
                )
                _uiState.value = WeeklyRankingListUiState.Success(currentPageState)
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            currentPageState = currentPageState.copy(isRefreshing = true)
            _uiState.value = WeeklyRankingListUiState.Success(currentPageState)

            // First page (offset=0) is cached by the repository
            val result = getWeeklyRankingSongsUseCase(offset = 0, limit = PAGE_SIZE)

            if (result.isSuccess) {
                val songs = result.getOrNull() ?: emptyList()
                currentPageState = WeeklyRankingPageState(
                    songs = songs,
                    offset = songs.size,
                    hasMore = songs.size >= PAGE_SIZE && songs.size < MAX_ITEMS,
                    isRefreshing = false
                )
                _uiState.value = WeeklyRankingListUiState.Success(currentPageState)
            } else {
                currentPageState = currentPageState.copy(
                    isRefreshing = false,
                    error = "Failed to refresh"
                )
                _uiState.value = WeeklyRankingListUiState.Success(currentPageState)
            }
        }
    }

    fun onSongClick(song: MusicSong) {
        _selectedSong.value = song
    }

    fun onDismissPlayer() {
        _selectedSong.value = null
    }

    fun onUseToCreateVideo(song: MusicSong) {
        _selectedSong.value = null
        _navigationEvent.value = WeeklyRankingNavigationEvent.NavigateToAssetPicker(song.id)
    }

    fun onNavigateBack() {
        _navigationEvent.value = WeeklyRankingNavigationEvent.NavigateBack
    }

    fun onNavigationHandled() {
        _navigationEvent.value = null
    }
}
