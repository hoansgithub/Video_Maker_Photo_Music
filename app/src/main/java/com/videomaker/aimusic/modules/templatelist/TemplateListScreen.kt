package com.videomaker.aimusic.modules.templatelist

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.videomaker.aimusic.R
import com.videomaker.aimusic.domain.model.VibeTag
import com.videomaker.aimusic.domain.model.VideoTemplate
import com.videomaker.aimusic.ui.components.TagChipRow
import com.videomaker.aimusic.ui.components.TemplateCard
import com.videomaker.aimusic.ui.theme.AppDimens
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Template List Screen - Paginated template browsing with vibe tag filtering
 *
 * Features:
 * - Tag filter chips at top (with "All" option)
 * - HorizontalPager synced with tag selection
 * - 2-column grid layout
 * - Lazy loading: max 20 items per page
 * - Swipe or tap chip to change filter
 * - Query-level filtering and pagination (no client-side filtering)
 *
 * Following patterns from android-short-drama-app
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplateListScreen(
    viewModel: TemplateListViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToTemplatePreviewer: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val navigationEvent by viewModel.navigationEvent.collectAsStateWithLifecycle()

    // ✅ FIX: Refresh data when locale changes (template names + vibe tags)
    // Use rememberSaveable to persist previousLocale across Activity recreation
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]?.toLanguageTag()
    var previousLocale by rememberSaveable { mutableStateOf(locale) }
    LaunchedEffect(locale) {
        // Only refresh on locale CHANGE, not initial composition
        if (locale != null && locale != previousLocale && previousLocale != null) {
            viewModel.refreshCurrentPage()
        }
        previousLocale = locale
    }

    // Handle navigation events
    LaunchedEffect(navigationEvent) {
        navigationEvent?.let { event ->
            when (event) {
                is TemplateListNavigationEvent.NavigateBack -> onNavigateBack()
                is TemplateListNavigationEvent.NavigateToTemplatePreviewer -> {
                    onNavigateToTemplatePreviewer(event.templateId)
                }
            }
            viewModel.onNavigationHandled()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Templates") },
                navigationIcon = {
                    IconButton(onClick = { viewModel.onNavigateBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = Modifier.background(MaterialTheme.colorScheme.background)
    ) { paddingValues ->
        when (val state = uiState) {
            is TemplateListUiState.Loading -> {
                LoadingContent(modifier = Modifier.padding(paddingValues))
            }
            is TemplateListUiState.Success -> {
                PagedContent(
                    viewModel = viewModel,
                    vibeTags = state.vibeTags,
                    currentPageIndex = state.currentPageIndex,
                    pageState = state.pageState,
                    onTemplateClick = { viewModel.onTemplateClick(it) },
                    modifier = Modifier.padding(paddingValues)
                )
            }
            is TemplateListUiState.Error -> {
                ErrorContent(
                    message = state.message,
                    modifier = Modifier.padding(paddingValues)
                )
            }
        }
    }
}

@Composable
private fun LoadingContent(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ErrorContent(
    message: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Paged content with tag chips + HorizontalPager + grids
 * Following android-short-drama-app PagedContent pattern
 */
@Composable
private fun PagedContent(
    viewModel: TemplateListViewModel,
    vibeTags: List<VibeTag>,
    currentPageIndex: Int,
    pageState: PageState,
    onTemplateClick: (VideoTemplate) -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val pageCount = 1 + vibeTags.size  // "All" + vibe tags

    val pagerState = rememberPagerState(
        initialPage = currentPageIndex,
        pageCount = { pageCount }
    )

    // Sync pager with ViewModel on settle (swipe gesture)
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { page ->
                viewModel.onPageSelected(page)
            }
    }

    // Sync pager when ViewModel changes (chip tap)
    LaunchedEffect(currentPageIndex) {
        if (pagerState.currentPage != currentPageIndex) {
            pagerState.animateScrollToPage(currentPageIndex)
        }
    }

    // Memoize selectedTagId calculation to avoid recalculation on every recomposition
    val selectedTagId = remember(currentPageIndex, vibeTags) {
        if (currentPageIndex == 0) null else vibeTags.getOrNull(currentPageIndex - 1)?.id
    }

    Column(
        modifier = modifier.fillMaxSize()
    ) {
        // Tag filter chips - reusable component from GalleryScreen
        TagChipRow(
            vibeTags = vibeTags,
            selectedTagId = selectedTagId,
            onTagSelected = { tagId ->
                val newIndex = if (tagId == null) {
                    0
                } else {
                    vibeTags.indexOfFirst { it.id == tagId }.let { if (it >= 0) it + 1 else 0 }
                }
                coroutineScope.launch {
                    pagerState.animateScrollToPage(newIndex)
                }
            },
            showAllLabel = stringResource(R.string.settings_all),
            modifier = Modifier.padding(vertical = AppDimens.current.spaceSm)
        )

        // HorizontalPager with 2-column grid per page
        HorizontalPager(
            state = pagerState,
            beyondViewportPageCount = 1,  // Preload adjacent pages
            modifier = Modifier.fillMaxSize()
        ) { page ->
            // Memoize page state retrieval to avoid unnecessary recalculations
            val state = remember(page, currentPageIndex, pageState) {
                if (page == currentPageIndex) {
                    pageState
                } else {
                    viewModel.getPageState(page) ?: PageState(isLoading = true)
                }
            }

            PageGrid(
                pageState = state,
                onTemplateClick = onTemplateClick,
                onLoadMore = {
                    if (page == currentPageIndex) {
                        viewModel.loadMoreForCurrentPage()
                    }
                },
                onRefresh = {
                    if (page == currentPageIndex) {
                        viewModel.refreshCurrentPage()
                    }
                }
            )
        }
    }
}

// ============================================
// HELPERS
// ============================================

/**
 * Parse aspect ratio string (e.g., "9:16", "16:9", "1:1") to float
 */
private fun parseAspectRatio(aspectRatio: String): Float {
    return try {
        val parts = aspectRatio.split(":")
        if (parts.size == 2) {
            val width = parts[0].toFloatOrNull() ?: 9f
            val height = parts[1].toFloatOrNull() ?: 16f
            width / height
        } else {
            9f / 16f // Default to portrait
        }
    } catch (e: Exception) {
        9f / 16f // Default to portrait
    }
}

/**
 * 2-column grid with pull-to-refresh and auto-load more
 * Following android-short-drama-app PageGrid pattern
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PageGrid(
    pageState: PageState,
    onTemplateClick: (VideoTemplate) -> Unit,
    onLoadMore: () -> Unit,
    onRefresh: () -> Unit
) {
    val dimens = AppDimens.current
    val gridState = rememberLazyGridState()
    val pullRefreshState = rememberPullToRefreshState()

    // Detect scroll near bottom (6 items threshold like short-drama-app)
    // Add gridState to remember dependencies for proper updates
    val shouldLoadMore by remember(gridState, pageState.hasMore, pageState.isLoadingMore) {
        derivedStateOf {
            val layoutInfo = gridState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            pageState.hasMore && !pageState.isLoadingMore && lastVisibleIndex >= totalItems - 6
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) onLoadMore()
    }

    PullToRefreshBox(
        isRefreshing = pageState.isRefreshing,
        onRefresh = onRefresh,
        state = pullRefreshState,
        modifier = Modifier.fillMaxSize()
    ) {
        when {
            pageState.isLoading && pageState.items.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            pageState.error != null && pageState.items.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = pageState.error,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                }
            }
            else -> {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    state = gridState,
                    contentPadding = PaddingValues(
                        start = dimens.spaceMd,
                        end = dimens.spaceMd,
                        top = dimens.spaceXs,
                        bottom = dimens.spaceLg
                    ),
                    horizontalArrangement = Arrangement.spacedBy(dimens.spaceSm),
                    verticalArrangement = Arrangement.spacedBy(dimens.spaceSm),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(
                        items = pageState.items,
                        key = { it.id },
                        contentType = { "template" }  // Better item recycling
                    ) { template ->
                        TemplateCard(
                            name = template.name,
                            thumbnailPath = template.thumbnailPath,
                            aspectRatio = parseAspectRatio(template.aspectRatio),
                            isPremium = template.isPremium,
                            useCount = template.useCount,
                            onClick = { onTemplateClick(template) }
                        )
                    }

                    // Loading more indicator
                    if (pageState.isLoadingMore) {
                        item(span = { GridItemSpan(2) }) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = dimens.spaceMd),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(32.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

