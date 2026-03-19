package com.videomaker.aimusic.modules.gallerysearch

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.videomaker.aimusic.core.data.local.PreferencesManager
import com.videomaker.aimusic.domain.model.MusicSong
import com.videomaker.aimusic.domain.model.VibeTag
import com.videomaker.aimusic.domain.model.VideoTemplate
import com.videomaker.aimusic.domain.repository.TemplateRepository
import com.videomaker.aimusic.domain.usecase.SearchSongsUseCase
import com.videomaker.aimusic.domain.usecase.SearchTemplatesUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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
    val thumbnailPath: String,
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
    private val templateRepository: TemplateRepository,
    private val searchSongsUseCase: SearchSongsUseCase,
    private val searchTemplatesUseCase: SearchTemplatesUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<GallerySearchUiState>(GallerySearchUiState.Idle)
    val uiState: StateFlow<GallerySearchUiState> = _uiState.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _recentSearches = MutableStateFlow<List<String>>(emptyList())
    val recentSearches: StateFlow<List<String>> = _recentSearches.asStateFlow()

    private val _suggestionVibeTags = MutableStateFlow<List<VibeTag>>(emptyList())
    val suggestionVibeTags: StateFlow<List<VibeTag>> = _suggestionVibeTags.asStateFlow()

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
            templateRepository.getVibeTags()
                .onSuccess { _suggestionVibeTags.value = it }
                .onFailure { android.util.Log.w("GallerySearchVM", "Failed to load vibe tags", it) }
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
                            emit(runSearch(query))
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

    /**
     * Called when a suggestion vibe tag chip is tapped.
     * Searches templates by vibe tag (exact match) and songs by display name.
     */
    fun onVibeTagClick(tag: VibeTag) {
        explicitSearchJob?.cancel()
        _query.value = tag.displayName
        _uiState.value = GallerySearchUiState.Loading
        explicitSearchJob = viewModelScope.launch {
            _uiState.value = runSearchByVibeTag(tag.id, tag.displayName)
        }
    }

    /** Explicit search (keyboard Search / recent tap) — cancels any previous explicit job. */
    private fun launchExplicitSearch(query: String) {
        explicitSearchJob?.cancel()
        _uiState.value = GallerySearchUiState.Loading
        explicitSearchJob = viewModelScope.launch {
            _uiState.value = runSearch(query)
        }
    }

    /** Runs template + song searches in parallel and merges into a single UI state. */
    private suspend fun runSearch(query: String): GallerySearchUiState = coroutineScope {
        val templatesDeferred = async { searchTemplatesUseCase(query) }
        val songsDeferred = async { searchSongsUseCase(query) }
        mergeResults(templatesDeferred.await(), songsDeferred.await(), label = query)
    }

    /**
     * Searches templates by vibe tag (exact tag match).
     * Songs are not included — vibe tags are a template-only concept.
     */
    private suspend fun runSearchByVibeTag(
        tagId: String,
        displayName: String
    ): GallerySearchUiState {
        val templateResult = templateRepository.getTemplatesByVibeTag(tag = tagId, limit = 20, offset = 0)
        return mergeResults(templateResult, Result.success(emptyList()), label = displayName)
    }

    /** Maps raw results into a [GallerySearchUiState]. Shared by text search and tag search. */
    private fun mergeResults(
        templateResult: Result<List<VideoTemplate>>,
        songResult: Result<List<MusicSong>>,
        label: String
    ): GallerySearchUiState {
        if (templateResult.isFailure && songResult.isFailure) {
            return GallerySearchUiState.Error("Search failed. Please try again.")
        }
        val templates = templateResult.getOrElse { emptyList() }.map { t ->
            GallerySearchTemplateItem(
                id = t.id,
                name = t.name,
                thumbnailPath = t.thumbnailPath,
                tags = t.vibeTags,
                aspectRatio = t.aspectRatio,
                isPremium = t.isPremium
            )
        }
        val songs = songResult.getOrElse { emptyList() }
        return if (templates.isEmpty() && songs.isEmpty()) {
            GallerySearchUiState.Empty(label)
        } else {
            GallerySearchUiState.Results(query = label, templates = templates, songs = songs)
        }
    }
}
