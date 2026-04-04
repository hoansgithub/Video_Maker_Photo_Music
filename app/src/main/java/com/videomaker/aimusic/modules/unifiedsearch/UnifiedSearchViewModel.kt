package com.videomaker.aimusic.modules.unifiedsearch

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.videomaker.aimusic.core.data.local.PreferencesManager
import com.videomaker.aimusic.domain.model.MusicSong
import com.videomaker.aimusic.domain.model.SongGenre
import com.videomaker.aimusic.domain.model.VibeTag
import com.videomaker.aimusic.domain.model.VideoTemplate
import com.videomaker.aimusic.domain.repository.TemplateRepository
import com.videomaker.aimusic.domain.usecase.GetGenresUseCase
import com.videomaker.aimusic.domain.usecase.GetSuggestedSongsUseCase
import com.videomaker.aimusic.domain.usecase.SearchSongsPagedUseCase
import com.videomaker.aimusic.domain.usecase.SearchTemplatesPagedUseCase
import com.videomaker.aimusic.navigation.SearchSection
import kotlinx.coroutines.Dispatchers
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

@Immutable
data class TemplateSectionState(
    val items: List<VideoTemplate> = emptyList(),
    val hasMore: Boolean = false,
    val isLoadingMore: Boolean = false,
    val totalLoaded: Int = 0,
    val totalCount: Int = 0
)

@Immutable
data class MusicSectionState(
    val songs: List<MusicSong> = emptyList(),
    val hasMore: Boolean = false,
    val isLoadingMore: Boolean = false,
    val totalLoaded: Int = 0,
    val totalCount: Int = 0
)

sealed class UnifiedSearchUiState {
    data class Idle (
        val initialSection: SearchSection
    ): UnifiedSearchUiState()

    data class Typing(
        val currentText: String,
        val suggestions: List<String>
    ) : UnifiedSearchUiState()

    data object Loading : UnifiedSearchUiState()

    @Immutable
    data class Results(
        val query: String,
        val templates: TemplateSectionState,
        val music: MusicSectionState,
        val initialSection: SearchSection,
        val templateEmpty: List<VideoTemplate> = emptyList(),
        val songEmpty: List<MusicSong> = emptyList(),
    ) : UnifiedSearchUiState()

    data class Empty(
        val query: String,
        val exploreSuggestions: List<VideoTemplate> = emptyList(),
        val isLoadingExplore: Boolean = false
    ) : UnifiedSearchUiState()

    data class Error(val message: String) : UnifiedSearchUiState()
}

sealed class UnifiedSearchNavigationEvent {
    data object NavigateBack : UnifiedSearchNavigationEvent()
    data class NavigateToTemplateDetail(val templateId: String) : UnifiedSearchNavigationEvent()
    data class NavigateToSongDetail(val songId: Long) : UnifiedSearchNavigationEvent()
}

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class UnifiedSearchViewModel(
    private val initialSection: SearchSection,
    private val preferencesManager: PreferencesManager,
    private val searchTemplatesPagedUseCase: SearchTemplatesPagedUseCase,
    private val searchSongsPagedUseCase: SearchSongsPagedUseCase,
    private val templateRepository: TemplateRepository,
    private val getGenresUseCase: GetGenresUseCase,
    private val getSuggestedSongsUseCase: GetSuggestedSongsUseCase
) : ViewModel() {

    companion object {
        private const val PAGE_SIZE = 6
        private const val SEARCH_DEBOUNCE_MS = 400L
        private const val MIN_TYPING_LENGTH = 2
    }

    private val _uiState = MutableStateFlow<UnifiedSearchUiState>(UnifiedSearchUiState.Idle(initialSection))
    val uiState: StateFlow<UnifiedSearchUiState> = _uiState.asStateFlow()

    private val _displayText = MutableStateFlow("")
    val displayText: StateFlow<String> = _displayText.asStateFlow()

    private val _query = MutableStateFlow("")

    private val _recentSearches = MutableStateFlow<List<String>>(emptyList())
    val recentSearches: StateFlow<List<String>> = _recentSearches.asStateFlow()

    private val _suggestionVibeTags = MutableStateFlow<List<VibeTag>>(emptyList())
    val suggestionVibeTags: StateFlow<List<VibeTag>> = _suggestionVibeTags.asStateFlow()

    private val _genres = MutableStateFlow<List<SongGenre>>(emptyList())
    val genres: StateFlow<List<SongGenre>> = _genres.asStateFlow()

    private val _featuredTemplates = MutableStateFlow<List<VideoTemplate>>(emptyList())
    val featuredTemplates: StateFlow<List<VideoTemplate>> = _featuredTemplates.asStateFlow()
    private val _hasMoreFeaturedTemplates = MutableStateFlow(false)
    val hasMoreFeaturedTemplates: StateFlow<Boolean> = _hasMoreFeaturedTemplates.asStateFlow()
    private val _isLoadingMoreFeaturedTemplates = MutableStateFlow(false)
    val isLoadingMoreFeaturedTemplates: StateFlow<Boolean> =
        _isLoadingMoreFeaturedTemplates.asStateFlow()

    private val _suggestedSongs = MutableStateFlow<List<MusicSong>>(emptyList())
    val suggestedSongs: StateFlow<List<MusicSong>> = _suggestedSongs.asStateFlow()
    private val _hasMoreSuggestedSongs = MutableStateFlow(false)
    val hasMoreSuggestedSongs: StateFlow<Boolean> = _hasMoreSuggestedSongs.asStateFlow()
    private val _isLoadingMoreSuggestedSongs = MutableStateFlow(false)
    val isLoadingMoreSuggestedSongs: StateFlow<Boolean> = _isLoadingMoreSuggestedSongs.asStateFlow()

    private val _selectedSong = MutableStateFlow<MusicSong?>(null)
    val selectedSong: StateFlow<MusicSong?> = _selectedSong.asStateFlow()

    private val _navigationEvent = MutableStateFlow<UnifiedSearchNavigationEvent?>(null)
    val navigationEvent: StateFlow<UnifiedSearchNavigationEvent?> = _navigationEvent.asStateFlow()

    private var explicitSearchJob: Job? = null
    private var debounceLockedOut = false

    // Full server results cached for client-side "See More" (no re-fetch needed)
    private var cachedSearchTemplates: List<VideoTemplate> = emptyList()
    private var cachedSearchSongs: List<MusicSong> = emptyList()

    init {
        _recentSearches.value = preferencesManager.getRecentSearches()

        viewModelScope.launch(Dispatchers.IO) {
            templateRepository.getVibeTags()
                .onSuccess { _suggestionVibeTags.value = it }
        }

        viewModelScope.launch(Dispatchers.IO) {
            templateRepository.getFeaturedTemplates(limit = PAGE_SIZE)
                .onSuccess {
                    _featuredTemplates.value = it
                    _hasMoreFeaturedTemplates.value = it.size == PAGE_SIZE
                }
                .onFailure {
                    _hasMoreFeaturedTemplates.value = false
                }
        }

        viewModelScope.launch(Dispatchers.IO) {
            getGenresUseCase()
                .onSuccess { _genres.value = it }
        }

        viewModelScope.launch(Dispatchers.IO) {
            getSuggestedSongsUseCase(limit = PAGE_SIZE)
                .onSuccess {
                    _suggestedSongs.value = it
                    _hasMoreSuggestedSongs.value = it.size == PAGE_SIZE
                }
                .onFailure {
                    _hasMoreSuggestedSongs.value = false
                }
        }

        viewModelScope.launch {
            _query
                .debounce(SEARCH_DEBOUNCE_MS)
                .flatMapLatest { query ->
                    flow {
                        emit(runDebouncedSearch(query))
                    }
                }
                .collect { state ->
                    if (!debounceLockedOut) {
                        _uiState.value = state
                    }
                }
        }
    }

    fun onQueryChange(newQuery: String) {
        debounceLockedOut = false
        _displayText.value = newQuery

        val normalized = newQuery.trim()
        _query.value = normalized

        _uiState.value = when {
            normalized.isBlank() -> UnifiedSearchUiState.Idle(initialSection)
            normalized.length >= MIN_TYPING_LENGTH ->
                UnifiedSearchUiState.Typing(newQuery, suggestionsFor(normalized))
            else -> UnifiedSearchUiState.Idle(initialSection)
        }
    }

    fun onSearch() {
        val q = _displayText.value.trim()
        if (q.isBlank()) return

        acquireExplicitSearchControl()
        rememberQuery(q)
        launchExplicitSearch(q)
    }

    fun onSuggestionClick(query: String) {
        val q = query.trim()
        if (q.isBlank()) return

        acquireExplicitSearchControl()
        _displayText.value = q
        rememberQuery(q)
        launchExplicitSearch(q)
    }

    fun onClearQuery() {
        explicitSearchJob?.cancel()
        debounceLockedOut = false
        _displayText.value = ""
        _query.value = ""
    }

    fun onRecentSearchClick(query: String) {
        val q = query.trim()
        if (q.isBlank()) return

        acquireExplicitSearchControl()
        _displayText.value = q
        rememberQuery(q)
        launchExplicitSearch(q)
    }

    fun onRemoveRecentSearch(query: String) {
        preferencesManager.removeRecentSearch(query)
        _recentSearches.value = preferencesManager.getRecentSearches()
    }

    fun onClearAllRecents() {
        preferencesManager.clearRecentSearches()
        _recentSearches.value = emptyList()
    }

    fun onVibeTagClick(tag: VibeTag) {
        onSuggestionClick(tag.displayName)
    }

    fun onGenreClick(genre: SongGenre) {
        onSuggestionClick(genre.displayName)
    }

    fun onTemplateClick(templateId: String) {
        _navigationEvent.value = UnifiedSearchNavigationEvent.NavigateToTemplateDetail(templateId)
    }

    fun onSongClick(song: MusicSong) {
        _selectedSong.value = song
    }

    fun onDismissPlayer() {
        _selectedSong.value = null
    }

    fun onUseToCreateVideo() {
        val song = _selectedSong.value ?: return
        _selectedSong.value = null
        _navigationEvent.value = UnifiedSearchNavigationEvent.NavigateToSongDetail(song.id)
    }

    fun onSeeMoreFeaturedTemplates() {
        if (_isLoadingMoreFeaturedTemplates.value || !_hasMoreFeaturedTemplates.value) return

        _isLoadingMoreFeaturedTemplates.value = true

        viewModelScope.launch(Dispatchers.IO) {
            val currentCount = _featuredTemplates.value.size
            val result = templateRepository.getFeaturedTemplates(limit = currentCount + PAGE_SIZE)
            val latest = _featuredTemplates.value

            result.onSuccess { fetched ->
                val existingIds = latest.asSequence().map { it.id }.toHashSet()
                val next = fetched.filterNot { existingIds.contains(it.id) }
                if (next.isNotEmpty()) {
                    _featuredTemplates.value = latest + next
                }
                _hasMoreFeaturedTemplates.value = next.size == PAGE_SIZE
            }

            _isLoadingMoreFeaturedTemplates.value = false
        }
    }

    fun onSeeMoreSuggestedSongs() {
        if (_isLoadingMoreSuggestedSongs.value || !_hasMoreSuggestedSongs.value) return

        _isLoadingMoreSuggestedSongs.value = true

        viewModelScope.launch(Dispatchers.IO) {
            val currentCount = _suggestedSongs.value.size
            val result = getSuggestedSongsUseCase(offset = currentCount, limit = PAGE_SIZE)
            val latest = _suggestedSongs.value

            result.onSuccess { next ->
                val merged = (latest + next).distinctBy { it.id }
                val appendedCount = merged.size - latest.size
                _suggestedSongs.value = merged
                _hasMoreSuggestedSongs.value = next.size == PAGE_SIZE && appendedCount > 0
            }

            _isLoadingMoreSuggestedSongs.value = false
        }
    }

    fun onNavigateBack() {
        _navigationEvent.value = UnifiedSearchNavigationEvent.NavigateBack
    }

    fun onNavigationHandled() {
        _navigationEvent.value = null
    }

    fun onSeeMoreTemplates() {
        val current = _uiState.value as? UnifiedSearchUiState.Results ?: return
        if (!current.templates.hasMore) return

        val nextItems = cachedSearchTemplates.take(current.templates.totalLoaded + PAGE_SIZE)
        _uiState.value = current.copy(
            templates = current.templates.copy(
                items = nextItems,
                hasMore = nextItems.size < cachedSearchTemplates.size,
                isLoadingMore = false,
                totalLoaded = nextItems.size
            )
        )
    }

    fun onSeeMoreMusic() {
        val current = _uiState.value as? UnifiedSearchUiState.Results ?: return
        if (!current.music.hasMore) return

        val nextSongs = cachedSearchSongs.take(current.music.totalLoaded + PAGE_SIZE)
        _uiState.value = current.copy(
            music = current.music.copy(
                songs = nextSongs,
                hasMore = nextSongs.size < cachedSearchSongs.size,
                isLoadingMore = false,
                totalLoaded = nextSongs.size
            )
        )
    }

    fun onExploreMore() {
        _uiState.value = UnifiedSearchUiState.Idle(initialSection)
    }

    private fun rememberQuery(query: String) {
        preferencesManager.addRecentSearch(query)
        _recentSearches.value = preferencesManager.getRecentSearches()
    }

    private fun acquireExplicitSearchControl() {
        explicitSearchJob?.cancel()
        debounceLockedOut = true
        _query.value = ""
    }

    private fun launchExplicitSearch(query: String) {
        cachedSearchTemplates = emptyList()
        cachedSearchSongs = emptyList()
        _uiState.value = UnifiedSearchUiState.Loading
        explicitSearchJob = viewModelScope.launch {
            _uiState.value = runParallelSearch(query)
        }
    }

    private suspend fun runDebouncedSearch(query: String): UnifiedSearchUiState {
        val q = query.trim()
        return when {
            q.isBlank() -> UnifiedSearchUiState.Idle(initialSection)
            else -> UnifiedSearchUiState.Typing(query, suggestionsFor(q))
        }
    }

    private suspend fun runParallelSearch(query: String): UnifiedSearchUiState = coroutineScope {
        // Fetch all results from server in one shot — client-side paging handles "See More"
        val templateDeferred = async(Dispatchers.IO) {
            searchTemplatesPagedUseCase(query = query, limit = Int.MAX_VALUE, offset = 0)
        }
        val musicDeferred = async(Dispatchers.IO) {
            searchSongsPagedUseCase(query = query, limit = Int.MAX_VALUE, offset = 0)
        }

        val templatesResult = templateDeferred.await()
        val musicResult = musicDeferred.await()

        if (templatesResult.isFailure && musicResult.isFailure) {
            return@coroutineScope UnifiedSearchUiState.Error("Search failed. Please try again.")
        }

        val allTemplates = templatesResult.getOrElse { emptyList() }
        val allSongs = musicResult.getOrElse { emptyList() }

        // Cache full lists for instant client-side "See More"
        cachedSearchTemplates = allTemplates
        cachedSearchSongs = allSongs

        if (allTemplates.isEmpty() && allSongs.isEmpty()) {
            return@coroutineScope UnifiedSearchUiState.Empty(query = query)
        }

        val visibleTemplates = allTemplates.take(PAGE_SIZE)
        val visibleSongs = allSongs.take(PAGE_SIZE)

        UnifiedSearchUiState.Results(
            query = query,
            templates = TemplateSectionState(
                items = visibleTemplates,
                hasMore = allTemplates.size > PAGE_SIZE,
                totalLoaded = visibleTemplates.size,
                totalCount = allTemplates.size
            ),
            music = MusicSectionState(
                songs = visibleSongs,
                hasMore = allSongs.size > PAGE_SIZE,
                totalLoaded = visibleSongs.size,
                totalCount = allSongs.size
            ),
            initialSection = initialSection
        )
    }

    private fun suggestionsFor(query: String): List<String> {
        val needle = query.trim().lowercase()
        if (needle.isBlank()) return emptyList()

        val source = buildList {
            addAll(_recentSearches.value)
            addAll(_suggestionVibeTags.value.map { it.displayName })
            addAll(_genres.value.map { it.displayName })
        }

        return source
            .distinctBy { it.lowercase() }
            .filter { it.lowercase().startsWith(needle) }
            .take(10)
    }
}

class UnifiedSearchViewModelFactory(
    private val preferencesManager: PreferencesManager,
    private val searchTemplatesPagedUseCase: SearchTemplatesPagedUseCase,
    private val searchSongsPagedUseCase: SearchSongsPagedUseCase,
    private val templateRepository: TemplateRepository,
    private val getGenresUseCase: GetGenresUseCase,
    private val getSuggestedSongsUseCase: GetSuggestedSongsUseCase
) {
    fun create(initialSection: SearchSection): UnifiedSearchViewModel {
        return UnifiedSearchViewModel(
            initialSection = initialSection,
            preferencesManager = preferencesManager,
            searchTemplatesPagedUseCase = searchTemplatesPagedUseCase,
            searchSongsPagedUseCase = searchSongsPagedUseCase,
            templateRepository = templateRepository,
            getGenresUseCase = getGenresUseCase,
            getSuggestedSongsUseCase = getSuggestedSongsUseCase
        )
    }
}
