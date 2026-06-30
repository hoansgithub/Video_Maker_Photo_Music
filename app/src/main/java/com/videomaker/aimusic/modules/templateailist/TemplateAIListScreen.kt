package com.videomaker.aimusic.modules.templateailist

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.alcheclub.lib.acccore.ads.compose.NativeAdView
import com.videomaker.aimusic.BuildConfig
import com.videomaker.aimusic.R
import com.videomaker.aimusic.core.ads.AdClickDetector
import com.videomaker.aimusic.core.analytics.Analytics
import com.videomaker.aimusic.core.analytics.AnalyticsEvent
import com.videomaker.aimusic.core.analytics.onFirstVisible
import com.videomaker.aimusic.core.constants.AdPlacement
import com.videomaker.aimusic.domain.model.VibeTag
import com.videomaker.aimusic.domain.model.VideoTemplate
import com.videomaker.aimusic.modules.home.AiTabViewModel
import com.videomaker.aimusic.modules.templatelist.PageState
import com.videomaker.aimusic.ui.components.TagChipRow
import com.videomaker.aimusic.ui.components.TemplateCard
import com.videomaker.aimusic.ui.theme.AppDimens
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * Dedicated AI template list with FIXED tabs (All / AI Video Generator / AI Dance).
 *
 * - Entry from the AI tab focuses the matching tab (via [TemplateAIListViewModel] init).
 * - "All" loads both AI vibe tags merged; each other tab loads its single tag.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplateAIListScreen(
    viewModel: TemplateAIListViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToTemplatePreviewer: (templateId: String, vibeTagId: String?) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val navigationEvent by viewModel.navigationEvent.collectAsStateWithLifecycle()
    val currentTabIndex by viewModel.currentTabIndex.collectAsStateWithLifecycle()

    // System rendered the AI all-template screen.
    LaunchedEffect(Unit) { Analytics.trackAiAllTemplateRender() }

    // Refresh on locale change (template names are localized).
    val locale = LocalConfiguration.current.locales[0]?.toLanguageTag()
    var previousLocale by rememberSaveable { mutableStateOf(locale) }
    LaunchedEffect(locale) {
        if (locale != null && locale != previousLocale && previousLocale != null) {
            viewModel.refreshCurrentTab()
        }
        previousLocale = locale
    }

    LaunchedEffect(navigationEvent) {
        navigationEvent?.let { event ->
            when (event) {
                is TemplateAIListNavigationEvent.NavigateBack -> onNavigateBack()
                is TemplateAIListNavigationEvent.NavigateToTemplatePreviewer -> {
                    val tag = when (currentTabIndex) {
                        TemplateAIListViewModel.TAB_VIDEO_GENERATOR -> AiTabViewModel.TAG_VIDEO_GENERATOR
                        TemplateAIListViewModel.TAB_DANCE -> AiTabViewModel.TAG_DANCE
                        else -> null
                    }
                    onNavigateToTemplatePreviewer(event.templateId, tag)
                }
            }
            viewModel.onNavigationHandled()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = { viewModel.onNavigateBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = Modifier.background(MaterialTheme.colorScheme.background)
    ) { paddingValues ->
        AiPagedContent(
            viewModel = viewModel,
            currentTabIndex = currentTabIndex,
            currentPageState = (uiState as? TemplateAIListUiState.Success)?.pageState,
            onTemplateClick = { viewModel.onTemplateClick(it) },
            modifier = Modifier.padding(paddingValues)
        )
    }
}

@Composable
private fun AiPagedContent(
    viewModel: TemplateAIListViewModel,
    currentTabIndex: Int,
    currentPageState: PageState?,
    onTemplateClick: (VideoTemplate) -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()

    // Fixed AI tabs powering the chip row (index 1 = video generator, index 2 = dance).
    val aiTabs = listOf(
        VibeTag(id = AiTabViewModel.TAG_VIDEO_GENERATOR, displayName = stringResource(R.string.ai_section_video_generator)),
        VibeTag(id = AiTabViewModel.TAG_DANCE, displayName = stringResource(R.string.ai_section_dance))
    )
    val pageCount = 1 + aiTabs.size

    val pagerState = rememberPagerState(
        initialPage = currentTabIndex,
        pageCount = { pageCount }
    )

    // Pager swipe → ViewModel
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { page -> viewModel.onTabSelected(page) }
    }

    // Chip tap → pager
    LaunchedEffect(currentTabIndex) {
        if (pagerState.currentPage != currentTabIndex) {
            pagerState.animateScrollToPage(currentTabIndex)
        }
    }

    val selectedTagId = remember(currentTabIndex) {
        when (currentTabIndex) {
            TemplateAIListViewModel.TAB_VIDEO_GENERATOR -> AiTabViewModel.TAG_VIDEO_GENERATOR
            TemplateAIListViewModel.TAB_DANCE -> AiTabViewModel.TAG_DANCE
            else -> null
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        TagChipRow(
            vibeTags = aiTabs,
            selectedTagId = selectedTagId,
            onTagSelected = { tagId ->
                Analytics.trackAiTemplateTabClick(tagId ?: AnalyticsEvent.Value.AiTemplateTab.ALL)
                val newIndex = when (tagId) {
                    AiTabViewModel.TAG_VIDEO_GENERATOR -> TemplateAIListViewModel.TAB_VIDEO_GENERATOR
                    AiTabViewModel.TAG_DANCE -> TemplateAIListViewModel.TAB_DANCE
                    else -> TemplateAIListViewModel.TAB_ALL
                }
                coroutineScope.launch { pagerState.animateScrollToPage(newIndex) }
            },
            showAllLabel = stringResource(R.string.settings_all),
            modifier = Modifier.padding(vertical = AppDimens.current.spaceSm)
        )

        HorizontalPager(
            state = pagerState,
            beyondViewportPageCount = 1,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val state = remember(page, currentTabIndex, currentPageState) {
                if (page == currentTabIndex) {
                    currentPageState ?: PageState(isLoading = true)
                } else {
                    viewModel.getPageState(page) ?: PageState(isLoading = true)
                }
            }

            AiPageGrid(
                pageState = state,
                onTemplateClick = onTemplateClick,
                onLoadMore = { if (page == currentTabIndex) viewModel.loadMoreForCurrentTab() },
                onRefresh = { if (page == currentTabIndex) viewModel.refreshCurrentTab() }
            )
        }
    }
}

// ============================================
// GRID ITEMS (Template + Ad)
// ============================================

private const val AD_INSERTION_INDEX = 3

private sealed class AiGridItem {
    data class TemplateItem(val template: VideoTemplate) : AiGridItem()
    data object AdItem : AiGridItem()
}

private fun parseAspectRatio(aspectRatio: String): Float {
    return try {
        val parts = aspectRatio.split(":")
        if (parts.size == 2) {
            val width = parts[0].toFloatOrNull() ?: 9f
            val height = parts[1].toFloatOrNull() ?: 16f
            width / height
        } else {
            9f / 16f
        }
    } catch (e: Exception) {
        9f / 16f
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AiPageGrid(
    pageState: PageState,
    onTemplateClick: (VideoTemplate) -> Unit,
    onLoadMore: () -> Unit,
    onRefresh: () -> Unit
) {
    val dimens = AppDimens.current
    val gridState = rememberLazyGridState()
    val pullRefreshState = rememberPullToRefreshState()
    val adClickDetector: AdClickDetector = koinInject()

    val distinctTemplates = remember(pageState.items) {
        pageState.items.distinctBy { it.id }
    }
    val gridItems = remember(distinctTemplates) {
        buildList {
            if (distinctTemplates.isEmpty()) return@buildList
            if (distinctTemplates.size < AD_INSERTION_INDEX) {
                distinctTemplates.forEach { add(AiGridItem.TemplateItem(it)) }
                add(AiGridItem.AdItem)
            } else {
                distinctTemplates.forEachIndexed { index, template ->
                    add(AiGridItem.TemplateItem(template))
                    if (index == AD_INSERTION_INDEX - 1) add(AiGridItem.AdItem)
                }
            }
        }
    }

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
            pageState.items.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.ai_templates_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                        items = gridItems,
                        key = { item ->
                            when (item) {
                                is AiGridItem.TemplateItem -> "template_${item.template.id}"
                                is AiGridItem.AdItem -> "ad_native_ai_grid"
                            }
                        },
                        contentType = { item ->
                            when (item) {
                                is AiGridItem.TemplateItem -> "template"
                                is AiGridItem.AdItem -> "ad"
                            }
                        }
                    ) { item ->
                        when (item) {
                            is AiGridItem.TemplateItem -> {
                                val template = item.template
                                TemplateCard(
                                    name = template.name,
                                    thumbnailPath = template.thumbnailPath,
                                    aspectRatio = parseAspectRatio(template.aspectRatio),
                                    isPremium = template.isPremium,
                                    useCount = template.useCount,
                                    viewCount = template.viewCount,
                                    modifier = Modifier.onFirstVisible(key = template.id) {
                                        Analytics.trackTemplateImpression(
                                            templateId = template.id,
                                            templateName = template.name,
                                            location = AnalyticsEvent.Value.Location.AI,
                                            screenSessionId = "",
                                            isPremium = template.isPremium,
                                            style = AnalyticsEvent.Value.Style.AI
                                        )
                                    },
                                    onClick = {
                                        Analytics.trackTemplateClick(
                                            templateId = template.id,
                                            templateName = template.name,
                                            location = AnalyticsEvent.Value.Location.AI,
                                            isPremium = template.isPremium,
                                            style = AnalyticsEvent.Value.Style.AI
                                        )
                                        onTemplateClick(template)
                                    }
                                )
                            }
                            is AiGridItem.AdItem -> {
                                NativeAdView(
                                    placement = AdPlacement.NATIVE_GALLERY_GRID,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    isDebug = BuildConfig.DEBUG,
                                    onAdClicked = { adClickDetector.onAdClick(it) }
                                )
                            }
                        }
                    }

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
