package com.videomaker.aimusic.modules.gallerysearch

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.videomaker.aimusic.core.data.local.PreferencesManager
import com.videomaker.aimusic.domain.model.MusicSong
import com.videomaker.aimusic.domain.model.SongGenre
import com.videomaker.aimusic.domain.usecase.GetGenresUseCase
import com.videomaker.aimusic.domain.usecase.SearchSongsUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch

// ============================================
// UI STATE
// ============================================

sealed class GallerySearchUiState {
    data object Idle : GallerySearchUiState()
    data object Loading : GallerySearchUiState()
    @Immutable data class Results(
        val query: String,
        val templates: List<GallerySearchTemplateItem>,
        val songs: List<MusicSong>
    ) : GallerySearchUiState()
    data class Empty(val query: String) : GallerySearchUiState()
    data class Error(val message: String) : GallerySearchUiState()
}

@Immutable
data class GallerySearchTemplateItem(
    val id: String,
    val name: String,
    val tags: List<String>,
    val aspectRatio: String,
    val isPremium: Boolean
)

// ============================================
// NAVIGATION EVENTS
// ============================================

sealed class GallerySearchNavigationEvent {
    data object NavigateBack : GallerySearchNavigationEvent()
    data class NavigateToTemplateDetail(val templateId: String) : GallerySearchNavigationEvent()
    data class NavigateToSongDetail(val songId: Long) : GallerySearchNavigationEvent()
}

// ============================================
// VIEW MODEL
// ============================================

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class GallerySearchViewModel(
    private val preferencesManager: PreferencesManager,
    private val searchSongsUseCase: SearchSongsUseCase,
    private val getGenresUseCase: GetGenresUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<GallerySearchUiState>(GallerySearchUiState.Idle)
    val uiState: StateFlow<GallerySearchUiState> = _uiState.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _recentSearches = MutableStateFlow<List<String>>(emptyList())
    val recentSearches: StateFlow<List<String>> = _recentSearches.asStateFlow()

    private val _suggestionGenres = MutableStateFlow<List<SongGenre>>(emptyList())
    val suggestionGenres: StateFlow<List<SongGenre>> = _suggestionGenres.asStateFlow()

    private val _navigationEvent = MutableStateFlow<GallerySearchNavigationEvent?>(null)
    val navigationEvent: StateFlow<GallerySearchNavigationEvent?> = _navigationEvent.asStateFlow()

    // Song currently shown in the player bottom sheet (null = sheet closed)
    private val _selectedSong = MutableStateFlow<MusicSong?>(null)
    val selectedSong: StateFlow<MusicSong?> = _selectedSong.asStateFlow()

    // Tracks the explicit search job (keyboard Search button / recent tap) so it can be cancelled
    // when the debounce flow fires a newer query.
    private var explicitSearchJob: Job? = null

    init {
        _recentSearches.value = preferencesManager.getRecentSearches()

        viewModelScope.launch {
            getGenresUseCase().onSuccess { _suggestionGenres.value = it }
        }

        // distinctUntilChanged BEFORE debounce: suppresses duplicate emissions before the timer
        // flatMapLatest: cancels the previous search coroutine when a new query arrives
        viewModelScope.launch {
            _query
                .debounce(300L)
                .flatMapLatest { query ->
                    flow {
                        if (query.isBlank()) {
                            emit(GallerySearchUiState.Idle)
                        } else {
                            emit(GallerySearchUiState.Loading)
                            val result = searchSongsUseCase(query)
                            emit(buildResultState(query, result))
                        }
                    }
                }
                .collect { state -> _uiState.value = state }
        }
    }

    fun onQueryChange(newQuery: String) {
        _query.value = newQuery
    }

    fun onSearch() {
        val q = _query.value.trim()
        if (q.isNotBlank()) {
            preferencesManager.addRecentSearch(q)
            _recentSearches.value = preferencesManager.getRecentSearches()
            launchExplicitSearch(q)
        }
    }

    fun onClearQuery() {
        explicitSearchJob?.cancel()
        _query.value = ""
        _uiState.value = GallerySearchUiState.Idle
    }

    fun onRecentSearchClick(query: String) {
        _query.value = query
        preferencesManager.addRecentSearch(query)
        _recentSearches.value = preferencesManager.getRecentSearches()
        launchExplicitSearch(query)
    }

    fun onRemoveRecentSearch(query: String) {
        preferencesManager.removeRecentSearch(query)
        _recentSearches.value = preferencesManager.getRecentSearches()
    }

    fun onClearAllRecents() {
        preferencesManager.clearRecentSearches()
        _recentSearches.value = emptyList()
    }

    fun onSongClick(song: MusicSong) {
        _selectedSong.value = song
    }

    fun onDismissPlayer() {
        _selectedSong.value = null
    }

    fun onUseToCreateVideo(song: MusicSong) {
        _selectedSong.value = null
        _navigationEvent.value = GallerySearchNavigationEvent.NavigateToSongDetail(song.id)
    }

    fun onTemplateClick(templateId: String) {
        _navigationEvent.value = GallerySearchNavigationEvent.NavigateToTemplateDetail(templateId)
    }

    fun onNavigateBack() {
        _navigationEvent.value = GallerySearchNavigationEvent.NavigateBack
    }

    fun onNavigationHandled() {
        _navigationEvent.value = null
    }

    /** Explicit search (keyboard Search / recent tap) — cancels any previous explicit job. */
    private fun launchExplicitSearch(query: String) {
        explicitSearchJob?.cancel()
        _uiState.value = GallerySearchUiState.Loading
        explicitSearchJob = viewModelScope.launch {
            _uiState.value = buildResultState(query, searchSongsUseCase(query))
        }
    }

    private fun buildResultState(
        query: String,
        result: Result<List<MusicSong>>
    ): GallerySearchUiState {
        return result.fold(
            onSuccess = { songs ->
                if (songs.isEmpty()) GallerySearchUiState.Empty(query)
                else GallerySearchUiState.Results(query = query, templates = emptyList(), songs = songs)
            },
            onFailure = { GallerySearchUiState.Error("Search failed. Please try again.") }
        )
    }
}
