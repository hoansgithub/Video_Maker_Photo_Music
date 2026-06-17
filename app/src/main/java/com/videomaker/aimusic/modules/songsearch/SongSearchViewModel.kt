package com.videomaker.aimusic.modules.songsearch

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.videomaker.aimusic.core.data.local.PreferencesManager
import com.videomaker.aimusic.domain.model.MusicSong
import com.videomaker.aimusic.domain.model.SongGenre
import com.videomaker.aimusic.domain.usecase.GetGenresUseCase
import com.videomaker.aimusic.domain.usecase.GetSongsByGenreUseCase
import com.videomaker.aimusic.domain.usecase.GetSuggestedSongsUseCase
import com.videomaker.aimusic.domain.usecase.SearchSongsUseCase
import kotlinx.coroutines.Dispatchers
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

sealed class SongSearchUiState {
    data object Idle : SongSearchUiState()
    /** Initial full-screen skeleton shown while suggested songs load. */
    data object Loading : SongSearchUiState()
    /** Text search in progress — shows the "Searching…" skeleton. */
    data object Searching : SongSearchUiState()
    @Immutable data class Results(
        val query: String,
        val songs: List<MusicSong>
    ) : SongSearchUiState()
    data class Empty(val query: String) : SongSearchUiState()
    data class Error(val message: String) : SongSearchUiState()
}

// ============================================
// NAVIGATION EVENTS
// ============================================

sealed class SongSearchNavigationEvent {
    data object NavigateBack : SongSearchNavigationEvent()
    data class NavigateToSongDetail(val songId: Long) : SongSearchNavigationEvent()
}

// ============================================
// VIEW MODEL
// ============================================

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class SongSearchViewModel(
    private val preferencesManager: PreferencesManager,
    private val searchSongsUseCase: SearchSongsUseCase,
    private val getGenresUseCase: GetGenresUseCase,
    private val getSuggestedSongsUseCase: GetSuggestedSongsUseCase,
    private val getSongsByGenreUseCase: GetSongsByGenreUseCase
) : ViewModel() {

    // Starts in Loading so the initial open shows the full-screen skeleton until
    // suggested songs arrive, then transitions to Idle.
    private val _uiState = MutableStateFlow<SongSearchUiState>(SongSearchUiState.Loading)
    val uiState: StateFlow<SongSearchUiState> = _uiState.asStateFlow()

    // Text shown in the search bar — updated on every keystroke, genre tap, or recent tap.
    // Separate from _query so that genre/recent taps do NOT trigger the debounce text search.
    private val _displayText = MutableStateFlow("")
    val displayText: StateFlow<String> = _displayText.asStateFlow()

    // Drives the 300ms debounce search — only mutated when the user is actively typing.
    private val _query = MutableStateFlow("")

    private val _recentSearches = MutableStateFlow<List<String>>(emptyList())
    val recentSearches: StateFlow<List<String>> = _recentSearches.asStateFlow()

    private val _genres = MutableStateFlow<List<SongGenre>>(emptyList())
    val genres: StateFlow<List<SongGenre>> = _genres.asStateFlow()

    private val _suggestedSongs = MutableStateFlow<List<MusicSong>>(emptyList())
    val suggestedSongs: StateFlow<List<MusicSong>> = _suggestedSongs.asStateFlow()

    private val _selectedGenre = MutableStateFlow<String?>(null) // null = "All"
    val selectedGenre: StateFlow<String?> = _selectedGenre.asStateFlow()

    // Starts true so the initial open renders the loading skeleton.
    private val _suggestedSongsLoading = MutableStateFlow(true)
    val suggestedSongsLoading: StateFlow<Boolean> = _suggestedSongsLoading.asStateFlow()

    // Suggested/genre pagination
    @Volatile private var suggestedOffset = 0
    @Volatile private var suggestedHasMore = true
    private val _suggestedLoadingMore = MutableStateFlow(false)
    val suggestedLoadingMore: StateFlow<Boolean> = _suggestedLoadingMore.asStateFlow()

    private val _navigationEvent = MutableStateFlow<SongSearchNavigationEvent?>(null)
    val navigationEvent: StateFlow<SongSearchNavigationEvent?> = _navigationEvent.asStateFlow()

    // Song currently shown in the player bottom sheet (null = sheet closed)
    private val _selectedSong = MutableStateFlow<MusicSong?>(null)
    val selectedSong: StateFlow<MusicSong?> = _selectedSong.asStateFlow()

    // ============================================
    // PREVIEW STATE (for music selector in editor)
    // ============================================
    private val _previewingSongId = MutableStateFlow<Long?>(null)
    val previewingSongId: StateFlow<Long?> = _previewingSongId.asStateFlow()

    private val _selectedForConfirmId = MutableStateFlow<Long?>(null)
    val selectedForConfirmId: StateFlow<Long?> = _selectedForConfirmId.asStateFlow()

    private val _isLoadingPreview = MutableStateFlow(false)
    val isLoadingPreview: StateFlow<Boolean> = _isLoadingPreview.asStateFlow()

    // When opened from the editor with a current song, stay in Loading until that song's
    // preview is prepared (can auto-play), then switch to Idle.
    @Volatile private var awaitingInitialSong = false

    // Tracks the explicit search job (keyboard Search / recent tap / genre tap).
    private var explicitSearchJob: Job? = null

    // When true, the debounce flow is not allowed to write to _uiState.
    // Set when an explicit search takes control; cleared when the user resumes typing.
    private var debounceLockedOut = false

    init {
        viewModelScope.launch {
            _recentSearches.value = preferencesManager.getRecentSearches()
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                getSuggestedSongsUseCase(limit = PAGE_SIZE)
                    .onSuccess {
                        _suggestedSongs.value = it
                        suggestedOffset = it.size
                        suggestedHasMore = it.size >= PAGE_SIZE && suggestedOffset < SUGGESTED_MAX_ITEMS
                    }
            } finally {
                _suggestedSongsLoading.value = false
                // Leave the initial skeleton only if the user hasn't started searching yet and
                // we're not still waiting for the editor's current song to become playable.
                if (_uiState.value is SongSearchUiState.Loading && !awaitingInitialSong) {
                    _uiState.value = SongSearchUiState.Idle
                }
            }
        }

        viewModelScope.launch(Dispatchers.IO) {
            getGenresUseCase()
                .onSuccess { _genres.value = it }
                // Genre failure is non-critical — fall back to empty (hides the chip row)
        }

        // flatMapLatest: cancels the previous search coroutine when a new query arrives
        viewModelScope.launch {
            _query
                .debounce(300L)
                .flatMapLatest { query ->
                    flow {
                        if (query.isBlank()) {
                            emit(SongSearchUiState.Idle)
                        } else {
                            emit(SongSearchUiState.Searching)
                            emit(runSearch(query))
                        }
                    }
                }
                .collect { state ->
                    // Suppress debounce output while an explicit search has taken control.
                    if (debounceLockedOut) return@collect
                    // Hold the initial skeleton until the editor's current song can auto-play
                    // (don't let the blank-query debounce flip us to Idle early).
                    if (awaitingInitialSong && state is SongSearchUiState.Idle) return@collect
                    _uiState.value = state
                }
        }
    }

    /** Called on every keystroke in the search field. */
    fun onQueryChange(newQuery: String) {
        debounceLockedOut = false  // user is typing — let debounce control state
        _displayText.value = newQuery
        _query.value = newQuery
    }

    /** Called when the user taps the keyboard Search / Done button. */
    fun onSearch() {
        val q = _displayText.value.trim()
        if (q.isNotBlank()) {
            acquireExplicitSearchControl()
            viewModelScope.launch {
                preferencesManager.addRecentSearch(q)
                _recentSearches.value = preferencesManager.getRecentSearches()
            }
            launchExplicitSearch(q)
        }
    }

    fun onClearQuery() {
        explicitSearchJob?.cancel()
        debounceLockedOut = false
        _displayText.value = ""
        _query.value = ""
        _uiState.value = SongSearchUiState.Idle
    }

    fun onRecentSearchClick(query: String) {
        acquireExplicitSearchControl()
        _displayText.value = query
        viewModelScope.launch {
            preferencesManager.addRecentSearch(query)
            _recentSearches.value = preferencesManager.getRecentSearches()
        }
        launchExplicitSearch(query)
    }

    fun onRemoveRecentSearch(query: String) {
        viewModelScope.launch {
            preferencesManager.removeRecentSearch(query)
            _recentSearches.value = preferencesManager.getRecentSearches()
        }
    }

    fun onClearAllRecents() {
        viewModelScope.launch {
            preferencesManager.clearRecentSearches()
            _recentSearches.value = emptyList()
        }
    }

    /**
     * Called when a genre chip is tapped.
     * Uses getSongsByGenreUseCase with the genre id (DB key), not displayName.
     * displayName is shown in the search bar for UX only.
     */
    fun onGenreClick(genre: SongGenre) {
        acquireExplicitSearchControl()
        _displayText.value = genre.displayName
        launchGenreSearch(genre)
    }

    /**
     * Called when a genre filter chip is tapped in the bottom sheet.
     * null = "All" (reload suggested songs), non-null = filter by genre.
     */
    fun onGenreSelected(genreId: String?) {
        if (_selectedGenre.value == genreId) return
        _selectedGenre.value = genreId
        suggestedOffset = 0
        suggestedHasMore = true
        _suggestedSongsLoading.value = true
        viewModelScope.launch(Dispatchers.IO) {
            val result = if (genreId == null) {
                getSuggestedSongsUseCase(limit = PAGE_SIZE)
            } else {
                getSongsByGenreUseCase(genreId, limit = PAGE_SIZE)
            }
            result.onSuccess {
                _suggestedSongs.value = it
                suggestedOffset = it.size
                suggestedHasMore = it.size >= PAGE_SIZE && suggestedOffset < SUGGESTED_MAX_ITEMS
            }
            _suggestedSongsLoading.value = false
        }
    }

    /** Opens the music player bottom sheet for the given song. */
    fun onSongClick(song: MusicSong) {
        _selectedSong.value = song
    }

    /** Dismisses the player bottom sheet without any further action. */
    fun onDismissPlayer() {
        _selectedSong.value = null
    }

    /** Called when user taps "Use to Create Video" — closes sheet and navigates. */
    fun onUseToCreateVideo() {
        val song = _selectedSong.value ?: return
        _selectedSong.value = null
        _navigationEvent.value = SongSearchNavigationEvent.NavigateToSongDetail(song.id)
    }

    fun onNavigateBack() {
        _navigationEvent.value = SongSearchNavigationEvent.NavigateBack
    }

    fun onNavigationHandled() {
        _navigationEvent.value = null
    }

    /**
     * Locks out the debounce flow and cancels any pending explicit job.
     * Resets _query to "" so any pending debounce timer is cancelled.
     */
    private fun acquireExplicitSearchControl() {
        explicitSearchJob?.cancel()
        debounceLockedOut = true
        _query.value = ""
    }

    private fun launchExplicitSearch(query: String) {
        _uiState.value = SongSearchUiState.Searching
        explicitSearchJob = viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = runSearch(query)
        }
    }

    private fun launchGenreSearch(genre: SongGenre) {
        _uiState.value = SongSearchUiState.Searching
        explicitSearchJob = viewModelScope.launch(Dispatchers.IO) {
            val result = getSongsByGenreUseCase(genre.id)
            _uiState.value = toUiState(result, label = genre.displayName)
        }
    }

    private suspend fun runSearch(query: String): SongSearchUiState {
        val result = searchSongsUseCase(query)
        return toUiState(result, label = query)
    }

    private fun toUiState(result: Result<List<MusicSong>>, label: String): SongSearchUiState {
        if (result.isFailure) return SongSearchUiState.Error("Search failed. Please try again.")
        val songs = result.getOrElse { emptyList() }
        return if (songs.isEmpty()) SongSearchUiState.Empty(label)
        else SongSearchUiState.Results(query = label, songs = songs)
    }

    /** Called by UI when user scrolls near the end of the suggested songs list. */
    fun loadMoreSuggested() {
        if (!_suggestedLoadingMore.compareAndSet(false, true)) return
        if (!suggestedHasMore) {
            _suggestedLoadingMore.value = false
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val genreId = _selectedGenre.value
                val result = if (genreId == null) {
                    getSuggestedSongsUseCase(offset = suggestedOffset, limit = PAGE_SIZE)
                } else {
                    getSongsByGenreUseCase(genreId, limit = PAGE_SIZE, offset = suggestedOffset)
                }
                result.onSuccess { newSongs ->
                    suggestedOffset += newSongs.size
                    suggestedHasMore = newSongs.size >= PAGE_SIZE && suggestedOffset < SUGGESTED_MAX_ITEMS
                    val current = _suggestedSongs.value
                    val existingIds = current.map { it.id }.toSet()
                    val unique = newSongs.filterNot { it.id in existingIds }
                    _suggestedSongs.value = current + unique
                }
            } finally {
                _suggestedLoadingMore.value = false
            }
        }
    }

    // ============================================
    // PREVIEW METHODS (for music selector in editor)
    // ============================================

    /**
     * Toggle preview for a song. Marks it as selected for confirmation.
     */
    fun togglePreview(songId: Long) {
        if (_previewingSongId.value == songId) {
            // Stop preview but keep selection
            _previewingSongId.value = null
            _isLoadingPreview.value = false
        } else {
            // Start new preview
            _isLoadingPreview.value = true
            _previewingSongId.value = songId
            _selectedForConfirmId.value = songId
        }
    }

    /**
     * Called when ExoPlayer is prepared and starts playing
     */
    fun onPreviewPrepared() {
        _isLoadingPreview.value = false
    }

    /**
     * Stop preview
     */
    fun stopPreview() {
        _previewingSongId.value = null
        _isLoadingPreview.value = false
    }

    /**
     * Confirm selection - used by music selector confirm button
     * Returns the selected song for the callback
     */
    fun confirmSelection(): MusicSong? {
        val songId = _selectedForConfirmId.value ?: return null
        val state = _uiState.value

        return when (state) {
            is SongSearchUiState.Results -> state.songs.find { it.id == songId }
            else -> {
                // Check suggested songs if not in results
                _suggestedSongs.value.find { it.id == songId }
            }
        }
    }

    /**
     * Clear preview state (called when sheet is dismissed)
     */
    fun clearPreviewState() {
        _previewingSongId.value = null
        _selectedForConfirmId.value = null
        _isLoadingPreview.value = false
    }

    /**
     * Called when the sheet opens from the editor with a song already in use.
     * Keeps the UI in [SongSearchUiState.Loading] until [onInitialSongReady].
     */
    fun beginWithInitialSong() {
        awaitingInitialSong = true
        _uiState.value = SongSearchUiState.Loading
    }

    /**
     * Called once the editor's current song is prepared / actually playing.
     * Releases the initial loading gate and switches to Idle.
     */
    fun onInitialSongReady() {
        if (!awaitingInitialSong) return
        awaitingInitialSong = false
        if (_uiState.value is SongSearchUiState.Loading) {
            _uiState.value = SongSearchUiState.Idle
        }
    }

    companion object {
        private const val PAGE_SIZE = 15
        private const val SUGGESTED_MAX_ITEMS = 30
    }
}
