package com.videomaker.aimusic.modules.templateailist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.videomaker.aimusic.domain.model.VideoTemplate
import com.videomaker.aimusic.domain.repository.TemplateRepository
import com.videomaker.aimusic.modules.home.AiTabViewModel
import com.videomaker.aimusic.modules.templatelist.PageState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// ============================================
// UI STATE
// ============================================

sealed class TemplateAIListUiState {
    data object Loading : TemplateAIListUiState()
    data class Success(
        val currentTabIndex: Int,
        val pageState: PageState
    ) : TemplateAIListUiState()
}

// ============================================
// NAVIGATION EVENTS
// ============================================

sealed class TemplateAIListNavigationEvent {
    data object NavigateBack : TemplateAIListNavigationEvent()
    data class NavigateToTemplatePreviewer(val templateId: String) : TemplateAIListNavigationEvent()
}

// ============================================
// VIEW MODEL
// ============================================

/**
 * Drives the dedicated AI template list with three FIXED tabs:
 *  - [TAB_ALL] ("All")            → loads BOTH AI vibe tags ([AiTabViewModel.TAG_VIDEO_GENERATOR]
 *                                   and [AiTabViewModel.TAG_DANCE]) and merges them.
 *  - [TAB_VIDEO_GENERATOR]        → loads only [AiTabViewModel.TAG_VIDEO_GENERATOR].
 *  - [TAB_DANCE]                  → loads only [AiTabViewModel.TAG_DANCE].
 *
 * Reuses the existing per-vibe-tag RPC ([TemplateRepository.getTemplatesByVibeTag]). Each tab's
 * page is cached so swiping between tabs doesn't refetch.
 */
class TemplateAIListViewModel(
    private val templateRepository: TemplateRepository,
    initialVibeTagId: String? = null
) : ViewModel() {

    companion object {
        private const val PAGE_SIZE = 20

        const val TAB_ALL = 0
        const val TAB_VIDEO_GENERATOR = 1
        const val TAB_DANCE = 2

        /** Map an incoming vibe tag id to the tab that should be focused on entry. */
        fun tabForVibeTagId(vibeTagId: String?): Int = when (vibeTagId) {
            AiTabViewModel.TAG_VIDEO_GENERATOR -> TAB_VIDEO_GENERATOR
            AiTabViewModel.TAG_DANCE -> TAB_DANCE
            else -> TAB_ALL
        }
    }

    private val _uiState = MutableStateFlow<TemplateAIListUiState>(TemplateAIListUiState.Loading)
    val uiState: StateFlow<TemplateAIListUiState> = _uiState.asStateFlow()

    private val _navigationEvent = MutableStateFlow<TemplateAIListNavigationEvent?>(null)
    val navigationEvent: StateFlow<TemplateAIListNavigationEvent?> = _navigationEvent.asStateFlow()

    private val _currentTabIndex = MutableStateFlow(tabForVibeTagId(initialVibeTagId))
    val currentTabIndex: StateFlow<Int> = _currentTabIndex.asStateFlow()

    // Cached page state per tab index.
    private val pageStates = mutableMapOf<Int, PageState>()

    // The "All" tab merges two independently-paginated sources, so it tracks each one's cursor.
    private var allVideoOffset = 0
    private var allDanceOffset = 0
    private var allVideoHasMore = true
    private var allDanceHasMore = true

    init {
        loadTab(_currentTabIndex.value)
    }

    fun onTabSelected(tabIndex: Int) {
        _currentTabIndex.value = tabIndex
        loadTab(tabIndex)
    }

    fun getPageState(tabIndex: Int): PageState? = pageStates[tabIndex]

    fun loadTab(tabIndex: Int) {
        val existing = pageStates[tabIndex]
        if (existing != null && existing.items.isNotEmpty() && !existing.isLoading) {
            updateUiState(tabIndex, existing)
            return
        }

        viewModelScope.launch {
            val loadingState = PageState(isLoading = true)
            pageStates[tabIndex] = loadingState
            updateUiState(tabIndex, loadingState)

            val newState = fetchFirstPage(tabIndex)
            pageStates[tabIndex] = newState
            updateUiState(tabIndex, newState)
        }
    }

    fun loadMoreForCurrentTab() {
        val tabIndex = _currentTabIndex.value
        val current = pageStates[tabIndex] ?: return
        if (current.isLoadingMore || !current.hasMore) return

        viewModelScope.launch {
            val loadingMore = current.copy(isLoadingMore = true)
            pageStates[tabIndex] = loadingMore
            updateUiState(tabIndex, loadingMore)

            val newState = fetchNextPage(tabIndex, current)
            pageStates[tabIndex] = newState
            updateUiState(tabIndex, newState)
        }
    }

    fun refreshCurrentTab() {
        val tabIndex = _currentTabIndex.value
        viewModelScope.launch {
            val refreshing = (pageStates[tabIndex] ?: PageState()).copy(isRefreshing = true)
            pageStates[tabIndex] = refreshing
            updateUiState(tabIndex, refreshing)

            templateRepository.clearCache()
            val newState = fetchFirstPage(tabIndex)
            pageStates[tabIndex] = newState
            updateUiState(tabIndex, newState)
        }
    }

    fun onTemplateClick(template: VideoTemplate) {
        _navigationEvent.value =
            TemplateAIListNavigationEvent.NavigateToTemplatePreviewer(template.id)
    }

    fun onNavigateBack() {
        _navigationEvent.value = TemplateAIListNavigationEvent.NavigateBack
    }

    fun onNavigationHandled() {
        _navigationEvent.value = null
    }

    // ============================================
    // PRIVATE HELPERS
    // ============================================

    private suspend fun fetchFirstPage(tabIndex: Int): PageState {
        if (tabIndex == TAB_ALL) {
            val video = fetch(AiTabViewModel.TAG_VIDEO_GENERATOR, 0)
            val dance = fetch(AiTabViewModel.TAG_DANCE, 0)
            allVideoOffset = video.size
            allDanceOffset = dance.size
            allVideoHasMore = video.size >= PAGE_SIZE
            allDanceHasMore = dance.size >= PAGE_SIZE
            val merged = (video + dance).distinctBy { it.id }
            return PageState(
                items = merged,
                offset = merged.size,
                hasMore = allVideoHasMore || allDanceHasMore,
                isLoading = false
            )
        }

        val items = fetch(tagForTab(tabIndex), 0)
        return PageState(
            items = items,
            offset = items.size,
            hasMore = items.size >= PAGE_SIZE,
            isLoading = false
        )
    }

    private suspend fun fetchNextPage(tabIndex: Int, current: PageState): PageState {
        if (tabIndex == TAB_ALL) {
            val video = if (allVideoHasMore) fetch(AiTabViewModel.TAG_VIDEO_GENERATOR, allVideoOffset) else emptyList()
            val dance = if (allDanceHasMore) fetch(AiTabViewModel.TAG_DANCE, allDanceOffset) else emptyList()
            allVideoOffset += video.size
            allDanceOffset += dance.size
            allVideoHasMore = video.size >= PAGE_SIZE
            allDanceHasMore = dance.size >= PAGE_SIZE
            val merged = (current.items + video + dance).distinctBy { it.id }
            return current.copy(
                items = merged,
                offset = merged.size,
                hasMore = allVideoHasMore || allDanceHasMore,
                isLoadingMore = false
            )
        }

        val newItems = fetch(tagForTab(tabIndex), current.offset)
        val merged = (current.items + newItems).distinctBy { it.id }
        return current.copy(
            items = merged,
            offset = current.offset + newItems.size,
            hasMore = newItems.size >= PAGE_SIZE,
            isLoadingMore = false
        )
    }

    /** Concurrent-friendly single fetch with empty fallback (repo has its own local fallback). */
    private suspend fun fetch(tag: String, offset: Int): List<VideoTemplate> =
        templateRepository.getTemplatesByVibeTag(tag, PAGE_SIZE, offset).getOrNull().orEmpty()

    private fun tagForTab(tabIndex: Int): String = when (tabIndex) {
        TAB_VIDEO_GENERATOR -> AiTabViewModel.TAG_VIDEO_GENERATOR
        TAB_DANCE -> AiTabViewModel.TAG_DANCE
        else -> AiTabViewModel.TAG_VIDEO_GENERATOR
    }

    private fun updateUiState(tabIndex: Int, pageState: PageState) {
        // Only publish the visible tab to avoid flashing a background tab's load into the UI.
        if (tabIndex != _currentTabIndex.value) return
        _uiState.value = TemplateAIListUiState.Success(
            currentTabIndex = tabIndex,
            pageState = pageState
        )
    }
}
