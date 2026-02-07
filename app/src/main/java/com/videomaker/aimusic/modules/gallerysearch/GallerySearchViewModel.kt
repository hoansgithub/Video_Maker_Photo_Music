package com.videomaker.aimusic.modules.gallerysearch

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.videomaker.aimusic.core.data.local.PreferencesManager
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

// ============================================
// UI STATE
// ============================================

sealed class GallerySearchUiState {
    data object Idle : GallerySearchUiState()
    data class Results(
        val query: String,
        val templates: List<GallerySearchTemplateItem>,
        val songs: List<GallerySearchSongItem>
    ) : GallerySearchUiState()
    data class Empty(val query: String) : GallerySearchUiState()
}

@Immutable
data class GallerySearchTemplateItem(
    val id: String,
    val name: String,
    val tags: List<String>,
    val aspectRatio: String,
    val isPremium: Boolean
)

@Immutable
data class GallerySearchSongItem(
    val id: Long,
    val name: String,
    val artist: String,
    val genres: List<String>
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

@OptIn(FlowPreview::class)
class GallerySearchViewModel(
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    private val _uiState = MutableStateFlow<GallerySearchUiState>(GallerySearchUiState.Idle)
    val uiState: StateFlow<GallerySearchUiState> = _uiState.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _recentSearches = MutableStateFlow<List<String>>(emptyList())
    val recentSearches: StateFlow<List<String>> = _recentSearches.asStateFlow()

    private val _navigationEvent = MutableStateFlow<GallerySearchNavigationEvent?>(null)
    val navigationEvent: StateFlow<GallerySearchNavigationEvent?> = _navigationEvent.asStateFlow()

    init {
        _recentSearches.value = preferencesManager.getRecentSearches()

        viewModelScope.launch {
            _query
                .debounce(300L)
                .distinctUntilChanged()
                .collect { query ->
                    if (query.isBlank()) {
                        _uiState.value = GallerySearchUiState.Idle
                    } else {
                        performSearch(query)
                    }
                }
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
            performSearch(q)
        }
    }

    fun onClearQuery() {
        _query.value = ""
        _uiState.value = GallerySearchUiState.Idle
    }

    fun onRecentSearchClick(query: String) {
        _query.value = query
        preferencesManager.addRecentSearch(query)
        _recentSearches.value = preferencesManager.getRecentSearches()
        performSearch(query)
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

    private fun performSearch(query: String) {
        // Mock search — will be replaced with real data later
        val q = query.lowercase()

        val templateResults = mockTemplates.filter { t ->
            t.name.lowercase().contains(q) ||
                t.tags.any { it.lowercase().contains(q) }
        }

        val songResults = mockSongs.filter { s ->
            s.name.lowercase().contains(q) ||
                s.artist.lowercase().contains(q) ||
                s.genres.any { it.lowercase().contains(q) }
        }

        if (templateResults.isEmpty() && songResults.isEmpty()) {
            _uiState.value = GallerySearchUiState.Empty(query)
        } else {
            _uiState.value = GallerySearchUiState.Results(
                query = query,
                templates = templateResults,
                songs = songResults
            )
        }
    }
}

// ============================================
// MOCK DATA (preview only — no real data yet)
// ============================================

private val mockTemplates = listOf(
    GallerySearchTemplateItem("1", "Summer Vibes", listOf("travel", "aesthetic"), "9:16", true),
    GallerySearchTemplateItem("2", "Chill Lofi", listOf("lofi", "aesthetic"), "1:1", false),
    GallerySearchTemplateItem("3", "Retro Wave", listOf("retro", "vintage"), "9:16", true),
    GallerySearchTemplateItem("4", "Birthday Bash", listOf("birthday", "party"), "4:5", false),
    GallerySearchTemplateItem("5", "Travel Diary", listOf("travel", "vlog"), "9:16", false),
    GallerySearchTemplateItem("6", "Neon Nights", listOf("neon", "cyberpunk"), "1:1", false),
    GallerySearchTemplateItem("7", "Aesthetic Mood", listOf("aesthetic", "pastel"), "9:16", false),
    GallerySearchTemplateItem("8", "Cinematic", listOf("cinematic", "film"), "16:9", true),
    GallerySearchTemplateItem("9", "Golden Hour", listOf("golden", "sunset"), "9:16", false),
    GallerySearchTemplateItem("10", "Vintage Love", listOf("vintage", "lovely"), "1:1", false),
)

private val mockSongs = listOf(
    GallerySearchSongItem(1, "Sunset Drive", "Chill Beats", listOf("lofi", "chill")),
    GallerySearchSongItem(2, "Neon Dreams", "Synthwave", listOf("electronic", "synthwave")),
    GallerySearchSongItem(3, "Ocean Breeze", "Nature Sounds", listOf("ambient", "nature")),
    GallerySearchSongItem(4, "Birthday Song", "Happy Tunes", listOf("party", "birthday")),
    GallerySearchSongItem(5, "Travel Along", "Adventure", listOf("travel", "acoustic")),
    GallerySearchSongItem(6, "Retro Funk", "Old School", listOf("retro", "funk")),
    GallerySearchSongItem(7, "Aesthetic Vibes", "Lo-Fi Girl", listOf("lofi", "aesthetic")),
    GallerySearchSongItem(8, "Cinematic Score", "Epic Music", listOf("cinematic", "epic")),
)
