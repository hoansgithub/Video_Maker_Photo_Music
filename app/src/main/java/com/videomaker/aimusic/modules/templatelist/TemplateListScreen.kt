package com.videomaker.aimusic.modules.templatelist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.videomaker.aimusic.R
import com.videomaker.aimusic.domain.model.VibeTag
import com.videomaker.aimusic.domain.model.VideoTemplate
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
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
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

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Tag filter chips with auto-center
        TagFilterChips(
            vibeTags = vibeTags,
            selectedIndex = currentPageIndex,
            onIndexSelected = { index ->
                coroutineScope.launch {
                    pagerState.animateScrollToPage(index)
                }
            },
            modifier = Modifier.fillMaxWidth()
        )

        // HorizontalPager with 2-column grid per page
        HorizontalPager(
            state = pagerState,
            beyondViewportPageCount = 1,  // Preload adjacent pages
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val state = if (page == currentPageIndex) {
                pageState
            } else {
                viewModel.getPageState(page) ?: PageState(isLoading = true)
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

/**
 * Tag filter chips row (following android-short-drama-app TagFilterChipsPaged pattern)
 */
@Composable
private fun TagFilterChips(
    vibeTags: List<VibeTag>,
    selectedIndex: Int,
    onIndexSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val dimens = AppDimens.current
    val listState = rememberLazyListState()

    // Auto-center selected chip
    LaunchedEffect(selectedIndex) {
        // Calculate scroll position to center the selected chip
        val itemWidth = 120  // Approximate chip width + spacing
        val viewportWidth = listState.layoutInfo.viewportSize.width
        val centerOffset = (viewportWidth / 2) - (itemWidth / 2)

        listState.animateScrollToItem(
            index = selectedIndex,
            scrollOffset = -centerOffset
        )
    }

    LazyRow(
        state = listState,
        contentPadding = PaddingValues(horizontal = dimens.spaceMd),
        horizontalArrangement = Arrangement.spacedBy(dimens.spaceXs),
        modifier = modifier.padding(vertical = dimens.spaceSm)
    ) {
        // "All" chip (index 0)
        item {
            FilterChip(
                selected = selectedIndex == 0,
                onClick = { onIndexSelected(0) },
                label = {
                    Text(
                        text = stringResource(R.string.settings_all),
                        fontSize = 14.sp,
                        fontWeight = if (selectedIndex == 0) FontWeight.SemiBold else FontWeight.Normal
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }

        // Vibe tag chips (index 1+)
        itemsIndexed(vibeTags) { index, tag ->
            val chipIndex = index + 1  // +1 because index 0 = "All"
            FilterChip(
                selected = selectedIndex == chipIndex,
                onClick = { onIndexSelected(chipIndex) },
                label = {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (tag.emoji.isNotEmpty()) {
                            Text(text = tag.emoji, fontSize = 14.sp)
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        Text(
                            text = tag.displayName,
                            fontSize = 14.sp,
                            fontWeight = if (selectedIndex == chipIndex) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
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
    val shouldLoadMore by remember(pageState.hasMore, pageState.isLoadingMore) {
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
                    items(pageState.items, key = { it.id }) { template ->
                        TemplateGridItem(
                            template = template,
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

/**
 * Template grid item (reused from GalleryScreen pattern)
 */
@Composable
private fun TemplateGridItem(
    template: VideoTemplate,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dimens = AppDimens.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(dimens.radiusMd))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .clickable(onClick = onClick)
    ) {
        // Thumbnail
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(9f / 16f)
                .clip(RoundedCornerShape(topStart = dimens.radiusMd, topEnd = dimens.radiusMd))
                .background(Color.Black.copy(alpha = 0.1f))
        ) {
            if (template.thumbnailPath.isNotEmpty()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(template.thumbnailPath)
                        .crossfade(true)
                        .build(),
                    contentDescription = template.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }

            // Premium badge
            if (template.isPremium) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(dimens.spaceXs)
                        .background(
                            color = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(dimens.radiusSm)
                        )
                        .padding(horizontal = dimens.spaceXs, vertical = 2.dp)
                ) {
                    Text(
                        text = stringResource(R.string.badge_pro),
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }

            // Play button overlay
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(40.dp)
                    .background(
                        color = Color.Black.copy(alpha = 0.5f),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        // Template name
        Text(
            text = template.name,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(dimens.spaceSm)
        )
    }
}
