package com.videomaker.aimusic.modules.gallerysearch

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.videomaker.aimusic.core.data.local.PreferencesManager
import com.videomaker.aimusic.domain.model.VibeTag
import com.videomaker.aimusic.domain.model.VideoTemplate
import com.videomaker.aimusic.domain.repository.TemplateRepository
import com.videomaker.aimusic.domain.usecase.SearchTemplatesUseCase
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
        val templates: List<GallerySearchTemplateItem>
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
    val isPremium: Boolean,
    val useCount: Long = 0
)

// ============================================
// NAVIGATION EVENTS
// ============================================

sealed class GallerySearchNavigationEvent {
    data object NavigateBack : GallerySearchNavigationEvent()
    data class NavigateToTemplateDetail(val templateId: String) : GallerySearchNavigationEvent()
}

// ============================================
// VIEW MODEL
// ============================================

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class GallerySearchViewModel(
    private val preferencesManager: PreferencesManager,
    private val templateRepository: TemplateRepository,
    private val searchTemplatesUseCase: SearchTemplatesUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<GallerySearchUiState>(GallerySearchUiState.Idle)
    val uiState: StateFlow<GallerySearchUiState> = _uiState.asStateFlow()

    // Text shown in the search bar — updated on every character typed, tag tap, or recent tap.
    // Separate from _query so that tag/recent taps do NOT trigger the debounce text search.
    private val _displayText = MutableStateFlow("")
    val displayText: StateFlow<String> = _displayText.asStateFlow()

    // Drives the 300ms debounce search — only mutated when the user is actively typing.
    private val _query = MutableStateFlow("")

    private val _recentSearches = MutableStateFlow<List<String>>(emptyList())
    val recentSearches: StateFlow<List<String>> = _recentSearches.asStateFlow()

    private val _suggestionVibeTags = MutableStateFlow<List<VibeTag>>(emptyList())
    val suggestionVibeTags: StateFlow<List<VibeTag>> = _suggestionVibeTags.asStateFlow()

    private val _featuredTemplates = MutableStateFlow<List<GallerySearchTemplateItem>>(emptyList())
    val featuredTemplates: StateFlow<List<GallerySearchTemplateItem>> = _featuredTemplates.asStateFlow()

    private val _navigationEvent = MutableStateFlow<GallerySearchNavigationEvent?>(null)
    val navigationEvent: StateFlow<GallerySearchNavigationEvent?> = _navigationEvent.asStateFlow()

    // Tracks the explicit search job (keyboard Search / recent tap / vibe tag tap).
    private var explicitSearchJob: Job? = null

    // When true, the debounce flow is not allowed to write to _uiState.
    // Set when an explicit search takes control; cleared when the user resumes typing.
    private var debounceLockedOut = false

    init {
        _recentSearches.value = preferencesManager.getRecentSearches()

        viewModelScope.launch {
            templateRepository.getVibeTags()
                .onSuccess { _suggestionVibeTags.value = it }
        }

        viewModelScope.launch {
            templateRepository.getFeaturedTemplates(limit = 6)
                .onSuccess { templates ->
                    _featuredTemplates.value = templates.map { it.toSearchItem() }
                }
        }

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
                .collect { state ->
                    // Suppress debounce output while an explicit search has taken control.
                    if (!debounceLockedOut) {
                        _uiState.value = state
                    }
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
            preferencesManager.addRecentSearch(q)
            _recentSearches.value = preferencesManager.getRecentSearches()
            launchExplicitSearch(q)
        }
    }

    fun onClearQuery() {
        explicitSearchJob?.cancel()
        debounceLockedOut = false
        _displayText.value = ""
        _query.value = ""
        _uiState.value = GallerySearchUiState.Idle
    }

    fun onRecentSearchClick(query: String) {
        acquireExplicitSearchControl()
        _displayText.value = query
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
     * Sets _displayText only (not _query) so the debounce flow is not re-triggered.
     */
    fun onVibeTagClick(tag: VibeTag) {
        acquireExplicitSearchControl()
        _displayText.value = tag.displayName
        _uiState.value = GallerySearchUiState.Loading
        explicitSearchJob = viewModelScope.launch {
            _uiState.value = runSearchByVibeTag(tag.id, tag.displayName)
        }
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
        _uiState.value = GallerySearchUiState.Loading
        explicitSearchJob = viewModelScope.launch {
            _uiState.value = runSearch(query)
        }
    }

    private suspend fun runSearch(query: String): GallerySearchUiState {
        val result = searchTemplatesUseCase(query)
        return toUiState(result, label = query)
    }

    private suspend fun runSearchByVibeTag(tagId: String, displayName: String): GallerySearchUiState {
        val result = templateRepository.getTemplatesByVibeTag(tag = tagId, limit = 20, offset = 0)
        return toUiState(result, label = displayName)
    }

    private fun toUiState(result: Result<List<VideoTemplate>>, label: String): GallerySearchUiState {
        if (result.isFailure) return GallerySearchUiState.Error("Search failed. Please try again.")
        val templates = result.getOrElse { emptyList() }.map { it.toSearchItem() }
        return if (templates.isEmpty()) GallerySearchUiState.Empty(label)
        else GallerySearchUiState.Results(query = label, templates = templates)
    }

    private fun VideoTemplate.toSearchItem() = GallerySearchTemplateItem(
        id = id,
        name = name,
        thumbnailPath = thumbnailPath,
        tags = vibeTags,
        aspectRatio = aspectRatio,
        isPremium = isPremium,
        useCount = useCount
    )
}