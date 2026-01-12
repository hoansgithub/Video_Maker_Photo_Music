package com.videomaker.aimusic.modules.songs

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// ============================================
// UI STATE
// ============================================

sealed class SongsUiState {
    data object Loading : SongsUiState()
    data object Empty : SongsUiState()
    data class Success(val songs: List<Song>) : SongsUiState()
    data class Error(val message: String) : SongsUiState()
}

// ============================================
// NAVIGATION EVENTS
// ============================================

sealed class SongsNavigationEvent {
    data class NavigateToSongDetail(val songId: Int) : SongsNavigationEvent()
}

// ============================================
// MODELS
// ============================================

data class Song(
    val id: Int,
    val name: String,
    val artist: String,
    val coverUrl: String,
    val duration: Long,
    val category: String
)

// ============================================
// VIEW MODEL
// ============================================

class SongsViewModel : ViewModel() {

    private val _uiState = MutableStateFlow<SongsUiState>(SongsUiState.Empty)
    val uiState: StateFlow<SongsUiState> = _uiState.asStateFlow()

    private val _navigationEvent = MutableStateFlow<SongsNavigationEvent?>(null)
    val navigationEvent: StateFlow<SongsNavigationEvent?> = _navigationEvent.asStateFlow()

    init {
        loadSongs()
    }

    private fun loadSongs() {
        // TODO: Load songs from repository
        _uiState.value = SongsUiState.Empty
    }

    fun onSongClick(song: Song) {
        _navigationEvent.value = SongsNavigationEvent.NavigateToSongDetail(song.id)
    }

    fun onNavigationHandled() {
        _navigationEvent.value = null
    }
}
