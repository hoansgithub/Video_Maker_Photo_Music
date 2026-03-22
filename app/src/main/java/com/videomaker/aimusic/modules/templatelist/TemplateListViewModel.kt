package com.videomaker.aimusic.modules.templatelist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.videomaker.aimusic.domain.model.VibeTag
import com.videomaker.aimusic.domain.model.VideoTemplate
import com.videomaker.aimusic.domain.repository.TemplateRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// ============================================
// UI STATE
// ============================================

/**
 * State for a single page (tag filter)
 * Following android-short-drama-app PageState pattern
 */
data class PageState(
    val items: List<VideoTemplate> = emptyList(),
    val offset: Int = 0,
    val hasMore: Boolean = true,
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null
)

sealed class TemplateListUiState {
    data object Loading : TemplateListUiState()
    data class Success(
        val vibeTags: List<VibeTag>,
        val currentPageIndex: Int,
        val pageState: PageState
    ) : TemplateListUiState()
    data class Error(val message: String) : TemplateListUiState()
}

// ============================================
// NAVIGATION EVENTS
// ============================================

sealed class TemplateListNavigationEvent {
    data object NavigateBack : TemplateListNavigationEvent()
    data class NavigateToTemplatePreviewer(val templateId: String) : TemplateListNavigationEvent()
}

// ============================================
// VIEW MODEL
// ============================================

class TemplateListViewModel(
    private val templateRepository: TemplateRepository,
    initialVibeTagId: String? = null
) : ViewModel() {

    companion object {
        private const val PAGE_SIZE = 20  // As requested
    }

    private val _uiState = MutableStateFlow<TemplateListUiState>(TemplateListUiState.Loading)
    val uiState: StateFlow<TemplateListUiState> = _uiState.asStateFlow()

    private val _navigationEvent = MutableStateFlow<TemplateListNavigationEvent?>(null)
    val navigationEvent: StateFlow<TemplateListNavigationEvent?> = _navigationEvent.asStateFlow()

    // Following android-short-drama-app multi-page caching pattern
    private val _currentPageIndex = MutableStateFlow(0)
    val currentPageIndex: StateFlow<Int> = _currentPageIndex.asStateFlow()

    // Cache states by tag ID (null = "All")
    private val pageStates = mutableMapOf<String?, PageState>()
    private var vibeTags = emptyList<VibeTag>()

    init {
        loadInitialData(initialVibeTagId)
    }

    private fun loadInitialData(initialVibeTagId: String?) {
        viewModelScope.launch {
            _uiState.value = TemplateListUiState.Loading

            // Load vibe tags first
            val tagsResult = templateRepository.getVibeTags()
            if (tagsResult.isSuccess) {
                vibeTags = tagsResult.getOrNull() ?: emptyList()

                // Find initial page index
                val initialIndex = if (initialVibeTagId == null) {
                    0  // "All" tab
                } else {
                    vibeTags.indexOfFirst { it.id == initialVibeTagId }.let {
                        if (it >= 0) it + 1 else 0  // +1 because index 0 = "All"
                    }
                }

                _currentPageIndex.value = initialIndex

                // Load first page data
                loadPageData(initialIndex)
            } else {
                _uiState.value = TemplateListUiState.Error("Failed to load vibe tags")
            }
        }
    }

    /**
     * Load data for a specific page index.
     * Page 0 = "All", page 1+ = specific vibe tags.
     *
     * Following android-short-drama-app pattern:
     * - Return cached if already loaded
     * - Otherwise fetch from repository with query-level filtering
     */
    fun loadPageData(pageIndex: Int) {
        val tagId = getTagIdForIndex(pageIndex)
        val existingState = pageStates[tagId]

        // Return cached if already loaded and not stale
        if (existingState != null && existingState.items.isNotEmpty() && !existingState.isLoading) {
            updateUiState(pageIndex, existingState)
            return
        }

        // Fetch first page (offset 0)
        viewModelScope.launch {
            val loadingState = PageState(isLoading = true)
            pageStates[tagId] = loadingState
            updateUiState(pageIndex, loadingState)

            val result = if (tagId == null) {
                templateRepository.getTemplates(limit = PAGE_SIZE, offset = 0)
            } else {
                templateRepository.getTemplatesByVibeTag(tag = tagId, limit = PAGE_SIZE, offset = 0)
            }

            if (result.isSuccess) {
                val templates = result.getOrNull() ?: emptyList()
                val newState = PageState(
                    items = templates,
                    offset = templates.size,
                    hasMore = templates.size >= PAGE_SIZE,
                    isLoading = false
                )
                pageStates[tagId] = newState
                updateUiState(pageIndex, newState)
            } else {
                val errorState = PageState(
                    isLoading = false,
                    error = "Failed to load templates"
                )
                pageStates[tagId] = errorState
                updateUiState(pageIndex, errorState)
            }
        }
    }

    /**
     * Load more items for the current page.
     * Called when user scrolls near the bottom.
     */
    fun loadMoreForCurrentPage() {
        val pageIndex = _currentPageIndex.value
        val tagId = getTagIdForIndex(pageIndex)
        val currentState = pageStates[tagId] ?: return

        // Don't load if already loading or no more items
        if (currentState.isLoadingMore || !currentState.hasMore) return

        viewModelScope.launch {
            val loadingMoreState = currentState.copy(isLoadingMore = true)
            pageStates[tagId] = loadingMoreState
            updateUiState(pageIndex, loadingMoreState)

            val result = if (tagId == null) {
                templateRepository.getTemplates(limit = PAGE_SIZE, offset = currentState.offset)
            } else {
                templateRepository.getTemplatesByVibeTag(
                    tag = tagId,
                    limit = PAGE_SIZE,
                    offset = currentState.offset
                )
            }

            if (result.isSuccess) {
                val newTemplates = result.getOrNull() ?: emptyList()
                // Deduplicate by ID (in case of overlaps)
                val allTemplates = (currentState.items + newTemplates).distinctBy { it.id }
                val newState = currentState.copy(
                    items = allTemplates,
                    offset = currentState.offset + newTemplates.size,
                    hasMore = newTemplates.size >= PAGE_SIZE,
                    isLoadingMore = false
                )
                pageStates[tagId] = newState
                updateUiState(pageIndex, newState)
            } else {
                val errorState = currentState.copy(
                    isLoadingMore = false,
                    error = "Failed to load more templates"
                )
                pageStates[tagId] = errorState
                updateUiState(pageIndex, errorState)
            }
        }
    }

    /**
     * Refresh current page from network (clear cache).
     */
    fun refreshCurrentPage() {
        val pageIndex = _currentPageIndex.value
        val tagId = getTagIdForIndex(pageIndex)

        viewModelScope.launch {
            val refreshingState = pageStates[tagId]?.copy(isRefreshing = true)
                ?: PageState(isRefreshing = true)
            pageStates[tagId] = refreshingState
            updateUiState(pageIndex, refreshingState)

            // Clear cache for this tag
            templateRepository.clearCache()

            // Reload from offset 0
            val result = if (tagId == null) {
                templateRepository.getTemplates(limit = PAGE_SIZE, offset = 0)
            } else {
                templateRepository.getTemplatesByVibeTag(tag = tagId, limit = PAGE_SIZE, offset = 0)
            }

            if (result.isSuccess) {
                val templates = result.getOrNull() ?: emptyList()
                val newState = PageState(
                    items = templates,
                    offset = templates.size,
                    hasMore = templates.size >= PAGE_SIZE,
                    isRefreshing = false
                )
                pageStates[tagId] = newState
                updateUiState(pageIndex, newState)
            } else {
                val errorState = refreshingState.copy(
                    isRefreshing = false,
                    error = "Failed to refresh"
                )
                pageStates[tagId] = errorState
                updateUiState(pageIndex, errorState)
            }
        }
    }

    /**
     * Called when user selects a page via pager swipe or chip tap.
     */
    fun onPageSelected(pageIndex: Int) {
        _currentPageIndex.value = pageIndex
        loadPageData(pageIndex)
    }

    /**
     * Get cached state for a specific page (for rendering non-current pages).
     */
    fun getPageState(pageIndex: Int): PageState? {
        val tagId = getTagIdForIndex(pageIndex)
        return pageStates[tagId]
    }

    fun onTemplateClick(template: VideoTemplate) {
        _navigationEvent.value = TemplateListNavigationEvent.NavigateToTemplatePreviewer(template.id)
    }

    fun onNavigateBack() {
        _navigationEvent.value = TemplateListNavigationEvent.NavigateBack
    }

    fun onNavigationHandled() {
        _navigationEvent.value = null
    }

    // ============================================
    // PRIVATE HELPERS
    // ============================================

    /**
     * Map page index to tag ID.
     * Index 0 = null ("All"), index 1+ = vibeTags[index - 1].id
     */
    private fun getTagIdForIndex(index: Int): String? {
        return if (index == 0) null else vibeTags.getOrNull(index - 1)?.id
    }

    /**
     * Update UI state with current page data.
     */
    private fun updateUiState(pageIndex: Int, pageState: PageState) {
        _uiState.value = TemplateListUiState.Success(
            vibeTags = vibeTags,
            currentPageIndex = pageIndex,
            pageState = pageState
        )
    }
}
