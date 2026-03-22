package com.videomaker.aimusic.modules.suggestedsongs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.videomaker.aimusic.domain.model.MusicSong
import com.videomaker.aimusic.domain.repository.SongRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// ============================================
// UI STATE
// ============================================

/**
 * State for the paginated song list
 */
data class SongsPageState(
    val songs: List<MusicSong> = emptyList(),
    val offset: Int = 0,
    val hasMore: Boolean = true,
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null
)

sealed class SuggestedSongsListUiState {
    data object Loading : SuggestedSongsListUiState()
    data class Success(val pageState: SongsPageState) : SuggestedSongsListUiState()
    data class Error(val message: String) : SuggestedSongsListUiState()
}

// ============================================
// NAVIGATION EVENTS
// ============================================

sealed class SuggestedSongsNavigationEvent {
    data object NavigateBack : SuggestedSongsNavigationEvent()
    data class NavigateToAssetPicker(val songId: Long) : SuggestedSongsNavigationEvent()
}

// ============================================
// VIEW MODEL
// ============================================

class SuggestedSongsListViewModel(
    private val songRepository: SongRepository
) : ViewModel() {

    companion object {
        private const val PAGE_SIZE = 20
    }

    private val _uiState = MutableStateFlow<SuggestedSongsListUiState>(SuggestedSongsListUiState.Loading)
    val uiState: StateFlow<SuggestedSongsListUiState> = _uiState.asStateFlow()

    private val _navigationEvent = MutableStateFlow<SuggestedSongsNavigationEvent?>(null)
    val navigationEvent: StateFlow<SuggestedSongsNavigationEvent?> = _navigationEvent.asStateFlow()

    private var currentPageState = SongsPageState()

    init {
        loadFirstPage()
    }

    private fun loadFirstPage() {
        viewModelScope.launch {
            _uiState.value = SuggestedSongsListUiState.Loading

            val result = songRepository.getSongsPaged(offset = 0, limit = PAGE_SIZE)

            if (result.isSuccess) {
                val songs = result.getOrNull() ?: emptyList()
                currentPageState = SongsPageState(
                    songs = songs,
                    offset = songs.size,
                    hasMore = songs.size >= PAGE_SIZE,
                    isLoading = false
                )
                _uiState.value = SuggestedSongsListUiState.Success(currentPageState)
            } else {
                _uiState.value = SuggestedSongsListUiState.Error("Failed to load songs")
            }
        }
    }

    fun loadMore() {
        val state = currentPageState
        if (state.isLoadingMore || !state.hasMore) return

        viewModelScope.launch {
            currentPageState = state.copy(isLoadingMore = true)
            _uiState.value = SuggestedSongsListUiState.Success(currentPageState)

            val result = songRepository.getSongsPaged(offset = state.offset, limit = PAGE_SIZE)

            if (result.isSuccess) {
                val newSongs = result.getOrNull() ?: emptyList()
                val allSongs = (state.songs + newSongs).distinctBy { it.id }
                currentPageState = SongsPageState(
                    songs = allSongs,
                    offset = state.offset + newSongs.size,
                    hasMore = newSongs.size >= PAGE_SIZE,
                    isLoadingMore = false
                )
                _uiState.value = SuggestedSongsListUiState.Success(currentPageState)
            } else {
                currentPageState = state.copy(
                    isLoadingMore = false,
                    error = "Failed to load more songs"
                )
                _uiState.value = SuggestedSongsListUiState.Success(currentPageState)
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            currentPageState = currentPageState.copy(isRefreshing = true)
            _uiState.value = SuggestedSongsListUiState.Success(currentPageState)

            songRepository.clearCache()

            val result = songRepository.getSongsPaged(offset = 0, limit = PAGE_SIZE)

            if (result.isSuccess) {
                val songs = result.getOrNull() ?: emptyList()
                currentPageState = SongsPageState(
                    songs = songs,
                    offset = songs.size,
                    hasMore = songs.size >= PAGE_SIZE,
                    isRefreshing = false
                )
                _uiState.value = SuggestedSongsListUiState.Success(currentPageState)
            } else {
                currentPageState = currentPageState.copy(
                    isRefreshing = false,
                    error = "Failed to refresh"
                )
                _uiState.value = SuggestedSongsListUiState.Success(currentPageState)
            }
        }
    }

    fun onSongClick(song: MusicSong) {
        _navigationEvent.value = SuggestedSongsNavigationEvent.NavigateToAssetPicker(song.id)
    }

    fun onNavigateBack() {
        _navigationEvent.value = SuggestedSongsNavigationEvent.NavigateBack
    }

    fun onNavigationHandled() {
        _navigationEvent.value = null
    }
}
