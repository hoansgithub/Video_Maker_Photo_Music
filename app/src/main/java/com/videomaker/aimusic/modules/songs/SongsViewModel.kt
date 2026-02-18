package com.videomaker.aimusic.modules.songs

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.videomaker.aimusic.domain.model.MusicSong
import com.videomaker.aimusic.domain.usecase.GetGenresUseCase
import com.videomaker.aimusic.domain.usecase.GetSongsByGenreUseCase
import com.videomaker.aimusic.domain.usecase.GetStationSongsUseCase
import com.videomaker.aimusic.domain.usecase.GetSuggestedSongsUseCase
import com.videomaker.aimusic.domain.usecase.GetWeeklyRankingSongsUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// ============================================
// SECTION STATE — per-section loading indicator
// ============================================

@Immutable
sealed class SectionState<out T> {
    @Immutable data object Loading : SectionState<Nothing>()
    @Immutable data class Success<out T>(val data: T) : SectionState<T>()
    @Immutable data class Error(val message: String) : SectionState<Nothing>()
}

// ============================================
// NAVIGATION EVENTS
// ============================================

sealed class SongsNavigationEvent {
    data class NavigateToSongDetail(val songId: Long) : SongsNavigationEvent()
}

// ============================================
// VIEW MODEL
// ============================================

class SongsViewModel(
    private val getSuggestedSongsUseCase: GetSuggestedSongsUseCase,
    private val getWeeklyRankingSongsUseCase: GetWeeklyRankingSongsUseCase,
    private val getStationSongsUseCase: GetStationSongsUseCase,
    private val getGenresUseCase: GetGenresUseCase,
    private val getSongsByGenreUseCase: GetSongsByGenreUseCase
) : ViewModel() {

    private val _suggestedState =
        MutableStateFlow<SectionState<List<MusicSong>>>(SectionState.Loading)
    val suggestedState: StateFlow<SectionState<List<MusicSong>>> = _suggestedState.asStateFlow()

    private val _rankingState =
        MutableStateFlow<SectionState<List<MusicSong>>>(SectionState.Loading)
    val rankingState: StateFlow<SectionState<List<MusicSong>>> = _rankingState.asStateFlow()

    private val _stationState =
        MutableStateFlow<SectionState<List<MusicSong>>>(SectionState.Loading)
    val stationState: StateFlow<SectionState<List<MusicSong>>> = _stationState.asStateFlow()

    // Genres for the station tag chips
    private val _genresState =
        MutableStateFlow<SectionState<List<String>>>(SectionState.Loading)
    val genresState: StateFlow<SectionState<List<String>>> = _genresState.asStateFlow()

    // Currently active genre filter (null = show all / unfiltered)
    private val _selectedGenre = MutableStateFlow<String?>(null)
    val selectedGenre: StateFlow<String?> = _selectedGenre.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _navigationEvent = MutableStateFlow<SongsNavigationEvent?>(null)
    val navigationEvent: StateFlow<SongsNavigationEvent?> = _navigationEvent.asStateFlow()

    init {
        loadAll()
    }

    // Each section loads independently — UI shows shimmer per section
    private fun loadAll() {
        viewModelScope.launch(Dispatchers.Default) { loadSuggested() }
        viewModelScope.launch(Dispatchers.Default) { loadRanking() }
        viewModelScope.launch(Dispatchers.Default) { loadStations() }
        viewModelScope.launch(Dispatchers.Default) { loadGenres() }
    }

    fun refresh() {
        if (_isRefreshing.value) return
        _isRefreshing.value = true
        viewModelScope.launch {
            coroutineScope {
                launch(Dispatchers.Default) { loadSuggested() }
                launch(Dispatchers.Default) { loadRanking() }
                launch(Dispatchers.Default) { loadStations() }
                launch(Dispatchers.Default) { loadGenres() }
            }
            _isRefreshing.value = false
        }
    }

    /** Called when user taps a genre chip. null = "All" (no filter). */
    fun onGenreSelected(genre: String?) {
        if (_selectedGenre.value == genre) return
        _selectedGenre.value = genre
        viewModelScope.launch(Dispatchers.Default) { loadStations() }
    }

    private suspend fun loadSuggested() {
        _suggestedState.value = SectionState.Loading
        getSuggestedSongsUseCase(limit = 10)
            .onSuccess { _suggestedState.value = SectionState.Success(it) }
            .onFailure { _suggestedState.value = SectionState.Error(it.message ?: "Failed to load") }
    }

    private suspend fun loadRanking() {
        _rankingState.value = SectionState.Loading
        getWeeklyRankingSongsUseCase(limit = 9)
            .onSuccess { _rankingState.value = SectionState.Success(it) }
            .onFailure { _rankingState.value = SectionState.Error(it.message ?: "Failed to load") }
    }

    private suspend fun loadStations() {
        _stationState.value = SectionState.Loading
        val genre = _selectedGenre.value
        val result = if (genre == null) {
            getStationSongsUseCase(limit = 10)
        } else {
            getSongsByGenreUseCase(genre, limit = 20)
        }
        result
            .onSuccess { _stationState.value = SectionState.Success(it) }
            .onFailure { _stationState.value = SectionState.Error(it.message ?: "Failed to load") }
    }

    private suspend fun loadGenres() {
        _genresState.value = SectionState.Loading
        getGenresUseCase()
            .onSuccess { _genresState.value = SectionState.Success(it) }
            // Genre failure is non-critical — fall back to empty (hides the chip row)
            .onFailure { _genresState.value = SectionState.Success(emptyList()) }
    }

    fun onSongClick(song: MusicSong) {
        _navigationEvent.value = SongsNavigationEvent.NavigateToSongDetail(song.id)
    }

    fun onNavigationHandled() {
        _navigationEvent.value = null
    }
}
