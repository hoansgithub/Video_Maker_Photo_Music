package com.videomaker.aimusic.modules.gallery

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import co.alcheclub.lib.acccore.ads.compose.NativeAdView
import coil.compose.AsyncImage
import coil.decode.BitmapFactoryDecoder
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Precision
import coil.size.Size
import com.videomaker.aimusic.BuildConfig
import com.videomaker.aimusic.R
import com.videomaker.aimusic.core.ads.AdClickDetector
import com.videomaker.aimusic.core.analytics.Analytics
import com.videomaker.aimusic.core.analytics.AnalyticsEvent
import com.videomaker.aimusic.core.analytics.onFirstVisible
import com.videomaker.aimusic.core.constants.AdPlacement
import com.videomaker.aimusic.core.util.NumberFormatter
import com.videomaker.aimusic.domain.model.MusicSong
import com.videomaker.aimusic.domain.model.VibeTag
import com.videomaker.aimusic.domain.model.VideoTemplate
import com.videomaker.aimusic.ui.components.ContentTag
import com.videomaker.aimusic.ui.components.ContentTags
import com.videomaker.aimusic.ui.components.PageIndicator
import com.videomaker.aimusic.ui.components.PrimaryButton
import com.videomaker.aimusic.ui.components.SectionHeader
import com.videomaker.aimusic.ui.components.ShimmerPlaceholder
import com.videomaker.aimusic.ui.components.TagChipRow
import com.videomaker.aimusic.ui.components.TemplateCard
import com.videomaker.aimusic.ui.theme.AppDimens
import com.videomaker.aimusic.ui.theme.GoldAccent
import com.videomaker.aimusic.ui.theme.Gray200
import com.videomaker.aimusic.ui.theme.Primary
import com.videomaker.aimusic.ui.theme.SearchFieldBackground
import com.videomaker.aimusic.ui.theme.SearchFieldBorder
import com.videomaker.aimusic.ui.theme.TemplateBadgeBackground
import com.videomaker.aimusic.ui.theme.TextTertiary
import com.videomaker.aimusic.ui.theme.VideoMakerTheme
import com.videomaker.aimusic.ui.theme.White12
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import org.koin.compose.koinInject

// ============================================
// GALLERY GRID ITEM (Template + Ad)
// ============================================

@Immutable
private sealed class GalleryGridItem {
    data class TemplateItem(val template: VideoTemplate, val index: Int) : GalleryGridItem()
    data object AdItem : GalleryGridItem()
}

/**
 * Stable key function for gallery grid items
 */
private fun galleryItemKey(item: GalleryGridItem): String = when (item) {
    is GalleryGridItem.TemplateItem -> "template_${item.template.id}"
    is GalleryGridItem.AdItem -> "ad_native_gallery"
}

// Ad insertion position - 4th position (after 3rd template at index 2)
private const val AD_INSERTION_INDEX = 3

// ============================================
// GALLERY SCREEN
// ============================================

@Composable
fun GalleryScreen(
    viewModel: GalleryViewModel,
    topBarHeight: Dp = 0.dp,
    isVisible: Boolean = true,
    listState: LazyStaggeredGridState = rememberLazyStaggeredGridState(),
    onUserInteraction: () -> Unit = {},
    onNavigateToSongDetail: (Long) -> Unit = {},
    onNavigateToSongPreview: (MusicSong) -> Unit = {},
    onNavigateToTemplateDetail: (String, String?) -> Unit = { _, _ -> },
    onNavigateToAllTopSongs: () -> Unit = {},
    onNavigateToAllTemplates: (String?) -> Unit = {},
    onNavigateToCreate: () -> Unit = {},
    onNavigateToSearch: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val navigationEvent by viewModel.navigationEvent.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val adsLoaderService = org.koin.compose.koinInject<co.alcheclub.lib.acccore.ads.loader.AdsLoaderService>()
    val unlockedTemplatesManager = org.koin.compose.koinInject<com.videomaker.aimusic.core.storage.UnlockedTemplatesManager>()
    val unlockedTemplateIds by unlockedTemplatesManager.unlockedTemplateIds.collectAsStateWithLifecycle()

    // ✅ FIX: Refresh data when locale changes (after language change in settings)
    // Use rememberSaveable to persist previousLocale across Activity recreation
    val locale = LocalConfiguration.current.locales[0]?.toLanguageTag()
    var previousLocale by rememberSaveable { mutableStateOf(locale) }
    LaunchedEffect(locale) {
        // Only refresh on locale CHANGE, not initial composition
        if (locale != null && locale != previousLocale && previousLocale != null) {
            viewModel.refresh()
        }
        previousLocale = locale
    }

    // Preload template grid tap ad when view appears
    LaunchedEffect(Unit) {
        com.videomaker.aimusic.core.ads.InterstitialAdHelperExt.preloadInterstitial(
            adsLoaderService = adsLoaderService,
            placement = com.videomaker.aimusic.core.constants.AdPlacement.INTERSTITIAL_TEMPLATE_GRID_TAP,
            loadTimeoutMillis = null,
            showLoadingOverlay = false
        )
    }

    // Extract template navigation data
    val templateNavigation = remember(navigationEvent) {
        (navigationEvent as? GalleryNavigationEvent.NavigateToTemplateDetail)?.let {
            Triple(it.templateId, it.sourceLocation, it.shouldShowAd)
        }
    }

    // Handle template navigation with reusable helper
    templateNavigation?.let { (templateId, sourceLocation, shouldShowAd) ->
        com.videomaker.aimusic.core.ads.HandleTemplateNavigation(
            templateId = templateId,
            shouldShowAd = shouldShowAd,
            onPreloadNext = { viewModel.preloadTemplateGridAd() },
            onNavigate = {
                onNavigateToTemplateDetail(it, sourceLocation)
                viewModel.onNavigationHandled()
            }
        )
    }

    // Handle other navigation events
    LaunchedEffect(navigationEvent) {
        navigationEvent?.let { event ->
            when (event) {
                is GalleryNavigationEvent.NavigateToSongDetail -> {
                    onNavigateToSongDetail(event.songId)
                    viewModel.onNavigationHandled()
                }
                is GalleryNavigationEvent.NavigateToAllTopSongs -> {
                    onNavigateToAllTopSongs()
                    viewModel.onNavigationHandled()
                }
                is GalleryNavigationEvent.NavigateToAllTemplates -> {
                    onNavigateToAllTemplates(event.selectedVibeTagId)
                    viewModel.onNavigationHandled()
                }
                is GalleryNavigationEvent.NavigateToCreate -> {
                    onNavigateToCreate()
                    viewModel.onNavigationHandled()
                }
                is GalleryNavigationEvent.NavigateToSongPreview -> {
                    onNavigateToSongPreview(event.song)
                    viewModel.onNavigationHandled()
                }
                is GalleryNavigationEvent.NavigateToTemplateDetail -> {
                    // Handled by HandleTemplateNavigation above
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Background image — edge-to-edge, behind everything
        Image(
            painter = painterResource(id = R.drawable.bg_home),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // UI based on state
        when (val state = uiState) {
            is GalleryUiState.Loading -> GalleryLoadingContent(topBarHeight = topBarHeight)
            is GalleryUiState.Success -> GalleryContent(
                topBarHeight = topBarHeight,
                listState = listState,
                onUserInteraction = onUserInteraction,
                featuredTemplates = state.featuredTemplates,
                homeBanners = state.homeBanners,
                vibeTags = state.vibeTags,
                selectedVibeTagId = state.selectedVibeTagId,
                templateListState = state.templateListState,
                unlockedTemplateIds = unlockedTemplateIds,
                isRefreshing = isRefreshing,
                isVisible = isVisible,
                onRefresh = viewModel::refresh,
                onTemplateBannerClick = { template, position ->
                    onUserInteraction()
                    viewModel.onTemplateBannerClick(template, position)
                },
                onSongBannerClick = { song, position ->
                    onUserInteraction()
                    viewModel.onSongBannerClick(song, position)
                },
                onVibeTagSelected = { selectedTagId ->
                    onUserInteraction()
                    val selectedTag = state.vibeTags.firstOrNull { it.id == selectedTagId }
                    Analytics.trackTemplateGenreClick(
                        genreId = selectedTagId ?: AnalyticsEvent.Value.ALL,
                        genreName = selectedTag?.displayName ?: AnalyticsEvent.Value.ALL,
                        location = AnalyticsEvent.Value.Location.HOME_TEMPLATE
                    )
                    viewModel.onVibeTagSelected(selectedTagId)
                },
                onTemplateClick = { template, location ->
                    onUserInteraction()
                    viewModel.onTemplateClick(template, location)
                },
                onSeeAllTemplates = {
                    onUserInteraction()
                    viewModel.onSeeAllTemplatesClick()
                },
                onCreateClick = {
                    onUserInteraction()
                    Analytics.trackCreationStart(AnalyticsEvent.Value.Location.GALLERY)
                    viewModel.onCreateClick()
                },
                onSearchClick = {
                    onUserInteraction()
                    onNavigateToSearch()
                },
                onLoadMore = viewModel::loadMore
            )
            is GalleryUiState.Error -> GalleryErrorContent(message = state.message)
        }
    }
}

/**
 * Shimmer skeleton loading for gallery content.
 */
@Composable
private fun GalleryLoadingContent(topBarHeight: Dp = 0.dp) {
    val dimens = AppDimens.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = topBarHeight + dimens.spaceLg)
            .padding(horizontal = dimens.spaceLg),
        verticalArrangement = Arrangement.spacedBy(dimens.spaceMd)
    ) {
        // Search field shimmer
        ShimmerPlaceholder(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            cornerRadius = 999.dp
        )

        // Featured effects shimmer
        ShimmerPlaceholder(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
            cornerRadius = dimens.radiusLg
        )

        // Templates shimmer
        ShimmerPlaceholder(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            cornerRadius = dimens.radiusLg
        )
    }
}

@Composable
private fun GalleryErrorContent(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            color = MaterialTheme.colorScheme.error
        )
    }
}

// ============================================
// GALLERY CONTENT
// ============================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GalleryContent(
    topBarHeight: Dp = 0.dp,
    listState: LazyStaggeredGridState,
    onUserInteraction: () -> Unit,
    featuredTemplates: List<VideoTemplate>,
    homeBanners: List<com.videomaker.aimusic.modules.gallery.banner.HomeBannerUi>,
    vibeTags: List<VibeTag>,
    selectedVibeTagId: String?,
    templateListState: TemplateListState,
    unlockedTemplateIds: Set<String>,
    isRefreshing: Boolean,
    isVisible: Boolean,
    onRefresh: () -> Unit,
    onTemplateBannerClick: (VideoTemplate, Int) -> Unit,
    onSongBannerClick: (MusicSong, Int) -> Unit,
    onVibeTagSelected: (String?) -> Unit,
    onTemplateClick: (VideoTemplate, String) -> Unit,
    onSeeAllTemplates: () -> Unit,
    onCreateClick: () -> Unit,
    onSearchClick: () -> Unit,
    onLoadMore: () -> Unit = {}
) {
    val dimens = AppDimens.current
    var lastTrackedTemplateScroll by remember { mutableStateOf(false) }

    // ✅ FIX: Scroll-based pagination detection
    LaunchedEffect(listState.canScrollForward) {
        if (!listState.canScrollForward &&
            templateListState is TemplateListState.Success &&
            templateListState.hasMore &&
            !templateListState.isLoadingMore) {
            onLoadMore()
        }
    }

    LaunchedEffect(listState, isVisible) {
        snapshotFlow {
            Triple(
                listState.firstVisibleItemIndex,
                listState.firstVisibleItemScrollOffset,
                listState.isScrollInProgress
            )
        }
            .drop(1)
            .collect { (index, offset, isScrolling) ->
                if (!isVisible || !isScrolling) return@collect
                val hasTemplateScroll = index >= 3 || (index == 2 && offset > 0)
                if (hasTemplateScroll && !lastTrackedTemplateScroll) {
                    Analytics.trackGallerySwipe(AnalyticsEvent.Value.Location.GALLERY_TEMPLATE)
                    lastTrackedTemplateScroll = true
                } else if (!hasTemplateScroll && lastTrackedTemplateScroll) {
                    lastTrackedTemplateScroll = false
                }
            }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize()
        ) {
            LazyVerticalStaggeredGrid(
                columns = StaggeredGridCells.Fixed(2),
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = topBarHeight + dimens.spaceSm,
                    bottom = dimens.space3Xl + dimens.space2Xl
                ),
                horizontalArrangement = Arrangement.spacedBy(dimens.spaceSm),
                verticalItemSpacing = dimens.spaceSm
            ) {
                // Section 1: Search Field
                item(key = "search", contentType = "search", span = StaggeredGridItemSpan.FullLine) {
                    GallerySearchField(
                        onClick = onSearchClick,
                        modifier = Modifier.padding(
                            horizontal = dimens.spaceLg,
                            vertical = dimens.spaceXs
                        )
                    )
                }

                // Section 2: Home banner carousel.
                if (homeBanners.isNotEmpty()) {
                    item(key = "spacer0", contentType = "spacer", span = StaggeredGridItemSpan.FullLine) {
                        Spacer(modifier = Modifier.height(dimens.spaceMd))
                    }

                    item(key = "home_banners", contentType = "home_banners", span = StaggeredGridItemSpan.FullLine) {
                        com.videomaker.aimusic.modules.gallery.banner.HomeBannerCarousel(
                            banners = homeBanners,
                            isVisible = isVisible,
                            onTemplateBannerClick = onTemplateBannerClick,
                            onSongBannerClick = onSongBannerClick
                        )
                    }
                } else if (featuredTemplates.isNotEmpty()) {
                    item(key = "spacer0", contentType = "spacer", span = StaggeredGridItemSpan.FullLine) {
                        Spacer(modifier = Modifier.height(dimens.spaceMd))
                    }

                    item(key = "featured_templates", contentType = "featured_carousel", span = StaggeredGridItemSpan.FullLine) {
                        FeaturedTemplatesCarousel(
                            templates = featuredTemplates,
                            isVisible = isVisible,
                            onTemplateClick = onTemplateClick
                        )
                    }
                }

                item(key = "spacer1", contentType = "spacer", span = StaggeredGridItemSpan.FullLine) {
                    Spacer(modifier = Modifier.height(dimens.spaceMd))
                }

                // Section 3: Templates header + tag chips
                item(key = "templates_header", contentType = "templates_header", span = StaggeredGridItemSpan.FullLine) {
                    SectionHeader(
                        title = stringResource(R.string.gallery_templates),
                        icon = Icons.Default.Star,
                        iconTint = GoldAccent,
                        onSeeAllClick = onSeeAllTemplates
                    )
                    Spacer(modifier = Modifier.height(dimens.spaceSm))
                    TagChipRow(
                        vibeTags = vibeTags,
                        selectedTagId = selectedVibeTagId,
                        onTagSelected = onVibeTagSelected,
                        modifier = Modifier.padding(bottom = dimens.spaceSm)
                    )
                }

                // Section 4: Templates grid items directly inside LazyVerticalStaggeredGrid
                when (templateListState) {
                    is TemplateListState.Loading -> {
                        item(key = "loading_skeleton", contentType = "loading_skeleton", span = StaggeredGridItemSpan.FullLine) {
                            TemplateGridSkeleton(
                                modifier = Modifier.padding(horizontal = dimens.spaceLg)
                            )
                        }
                    }
                    is TemplateListState.Success -> {
                        val templates = templateListState.templates
                        val gridItems = buildList {
                            if (templates.size < AD_INSERTION_INDEX) {
                                templates.forEachIndexed { index, template ->
                                    add(GalleryGridItem.TemplateItem(template, index))
                                }
                                add(GalleryGridItem.AdItem)
                            } else {
                                templates.forEachIndexed { index, template ->
                                    add(GalleryGridItem.TemplateItem(template, index))
                                    if (index == AD_INSERTION_INDEX - 1) {
                                        add(GalleryGridItem.AdItem)
                                    }
                                }
                            }
                        }

                        items(
                            items = gridItems,
                            key = ::galleryItemKey,
                            contentType = { item ->
                                when (item) {
                                    is GalleryGridItem.TemplateItem -> "template"
                                    is GalleryGridItem.AdItem -> "ad"
                                }
                            }
                        ) { item ->
                            Box(modifier = Modifier.padding(horizontal = dimens.spaceLg)) {
                                when (item) {
                                    is GalleryGridItem.TemplateItem -> {
                                        val template = item.template
                                        val templateIndex = item.index
                                        val aspectRatio = remember(template.aspectRatio) {
                                            parseAspectRatio(template.aspectRatio)
                                        }

                                        TemplateCard(
                                            name = template.name,
                                            thumbnailPath = template.thumbnailPath,
                                            aspectRatio = aspectRatio,
                                            isPremium = template.isPremium,
                                            isUnlocked = unlockedTemplateIds.contains(template.id),
                                            showHotTag = templateIndex < 10,
                                            useCount = template.useCount,
                                            viewCount = template.viewCount,
                                            modifier = Modifier.onFirstVisible(key = template.id) {
                                                Analytics.trackTemplateImpression(
                                                    templateId = template.id,
                                                    templateName = template.name,
                                                    location = AnalyticsEvent.Value.Location.HOME_TEMPLATE,
                                                    screenSessionId = "",
                                                    isPremium = template.isPremium
                                                )
                                            },
                                            onClick = {
                                                Analytics.trackTemplateClick(
                                                    templateId = template.id,
                                                    templateName = template.name,
                                                    location = AnalyticsEvent.Value.Location.GALLERY_TEMPLATE,
                                                    isPremium = template.isPremium
                                                )
                                                onTemplateClick(template, AnalyticsEvent.Value.Location.GALLERY_TEMPLATE)
                                            }
                                        )
                                    }
                                    is GalleryGridItem.AdItem -> {
                                        val adClickDetector: AdClickDetector = koinInject()
                                        NativeAdView(
                                            placement = AdPlacement.NATIVE_GALLERY_GRID,
                                            modifier = Modifier.fillMaxWidth(),
                                            isDebug = BuildConfig.DEBUG,
                                            onAdClicked = { adClickDetector.onAdClick(it) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Loading more indicator
                if (templateListState is TemplateListState.Success && templateListState.isLoadingMore) {
                    item(key = "loading_more", contentType = "loading_more", span = StaggeredGridItemSpan.FullLine) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(dimens.spaceLg),
                            contentAlignment = Alignment.Center
                        ) {
                            androidx.compose.material3.CircularProgressIndicator(
                                modifier = Modifier.size(32.dp),
                                color = Primary
                            )
                        }
                    }
                }
            }
        }

    }
}

// ============================================
// CREATE NEW VIDEO BUTTON
// ============================================

@Composable
fun CreateNewVideoButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    PrimaryButton(
        text = stringResource(R.string.gallery_create_new_video),
        onClick = onClick,
        leadingIcon = {
            Icon(
                painter = painterResource(id = R.drawable.ic_circle_plus),
                contentDescription = null,
                tint = Color.Unspecified,
                modifier = Modifier.size(24.dp)
            )
        },
        modifier = modifier.height(52.dp)
    )
}

// ============================================
// SEARCH FIELD
// ============================================

@Composable
private fun GallerySearchField(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dimens = AppDimens.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = SearchFieldBackground,
                shape = RoundedCornerShape(dimens.radiusXl)
            )
            .border(
                width = 1.dp,
                color = SearchFieldBorder,
                shape = RoundedCornerShape(dimens.radiusXl)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = dimens.spaceMd, vertical = dimens.spaceMd),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_lead_search),
            contentDescription = null,
            tint = Color.Unspecified,
            modifier = Modifier.size(24.dp)
        )

        Spacer(modifier = Modifier.width(dimens.spaceSm))

        Text(
            text = stringResource(R.string.gallery_search_hint),
            style = MaterialTheme.typography.titleSmall,
            color = TextTertiary,
            modifier = Modifier.weight(1f)
        )

        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = "Search",
            tint = TextTertiary
        )
    }
}

// ============================================
// FEATURED TEMPLATES CAROUSEL
// ============================================

/**
 * Optimized carousel - only loads images for current and adjacent pages.
 * Uses beyondBoundsPageCount to limit prefetch to 1 page in each direction.
 */
@Composable
private fun FeaturedTemplatesCarousel(
    templates: List<VideoTemplate>,
    isVisible: Boolean,
    onTemplateClick: (VideoTemplate, String) -> Unit,
    autoSlideIntervalMs: Long = 4000L,
    modifier: Modifier = Modifier
) {
    val adClickDetector: AdClickDetector = koinInject()
    val infinitePageCount = Int.MAX_VALUE
    val initialPage = infinitePageCount / 2

    val pagerState = rememberPagerState(
        initialPage = initialPage,
        pageCount = { infinitePageCount }
    )

    val isDragged by pagerState.interactionSource.collectIsDraggedAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasPendingUserSwipe by remember { mutableStateOf(false) }

    LaunchedEffect(isDragged, isVisible) {
        if (isVisible && isDragged) {
            hasPendingUserSwipe = true
        }
    }

    LaunchedEffect(isVisible) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                delay(autoSlideIntervalMs)
                if (isVisible && !isDragged && !pagerState.isScrollInProgress) {
                    pagerState.animateScrollToPage(pagerState.currentPage + 1)
                }
            }
        }
    }

    LaunchedEffect(pagerState, isVisible, templates) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { settledPage ->
                if (!isVisible) {
                    hasPendingUserSwipe = false
                    return@collect
                }
                if (hasPendingUserSwipe) {
                    Analytics.trackGallerySwipe(AnalyticsEvent.Value.Location.GALLERY_BANNER)
                    hasPendingUserSwipe = false
                }
                if (templates.isEmpty()) return@collect
                val carouselSize = templates.size + 1
                val itemIndex = settledPage.mod(carouselSize)
                if (itemIndex == 1) return@collect // ad slot — skip
                val templateIndex = if (itemIndex == 0) 0 else itemIndex - 1
                val template = templates.getOrNull(templateIndex) ?: return@collect
                Analytics.trackTemplateImpression(
                    templateId = template.id,
                    templateName = template.name,
                    location = AnalyticsEvent.Value.Location.HOME_BANNER,
                    screenSessionId = "",
                    isPremium = template.isPremium
                )
            }
    }

    val dimens = AppDimens.current

    Column(modifier = modifier) {
        val carouselSize = if (templates.isNotEmpty()) templates.size + 1 else 0
        
        if (carouselSize > 0) {
            HorizontalPager(
                state = pagerState,
                contentPadding = PaddingValues(horizontal = dimens.spaceLg),
                pageSpacing = dimens.spaceMd,
                modifier = Modifier.fillMaxWidth()
            ) { page ->
                val itemIndex = page.mod(carouselSize)
                
                if (itemIndex == 1) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .clip(RoundedCornerShape(dimens.radiusXl))
                            .background(Color.Black)
                    ) {
                        NativeAdView(
                            placement = AdPlacement.NATIVE_GALLERY_HOT_TPT,
                            autoLoad = true,
                            isDebug = BuildConfig.DEBUG,
                            onAdClicked = { adClickDetector.onAdClick(it) }
                        )
                    }
                } else {
                    val templateIndex = if (itemIndex == 0) 0 else itemIndex - 1
                    val template = templates[templateIndex]
                    val isCurrentPage = page == pagerState.settledPage && !pagerState.isScrollInProgress

                    // ✅ Only load image if within visible range (current ± 1 page)
                    val isNearCurrentPage = kotlin.math.abs(page - pagerState.currentPage) <= 1

                    FeaturedTemplateCard(
                        template = template,
                        isCurrentPage = isCurrentPage,
                        shouldLoadImage = isNearCurrentPage,
                        onClick = {
                            Analytics.trackTemplateClick(
                                templateId = template.id,
                                templateName = template.name,
                                location = AnalyticsEvent.Value.Location.GALLERY_BANNER,
                                isPremium = template.isPremium
                            )
                            onTemplateClick(template, AnalyticsEvent.Value.Location.GALLERY_BANNER)
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(dimens.spaceMd))

            // ✅ FIX: Memoize current page calculation
            val currentPageIndex = remember(pagerState.currentPage, carouselSize) {
                pagerState.currentPage.mod(carouselSize)
            }

            PageIndicator(
                pageCount = carouselSize,
                currentPage = currentPageIndex
            )
        }
    }
}

/**
 * Optimized featured card - only loads image when shouldLoadImage is true.
 * This prevents loading images for pages far from the current page.
 */
@Composable
private fun FeaturedTemplateCard(
    template: VideoTemplate,
    isCurrentPage: Boolean,
    shouldLoadImage: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dimens = AppDimens.current
    val context = LocalContext.current

    // ✅ Only create image request if within visible range
    val imageRequest = if (shouldLoadImage) {
        remember(template.id, isCurrentPage) {
            ImageRequest.Builder(context)
                .data(template.thumbnailPath.ifEmpty { null })
                .size(Size(720, 405))  // Optimize for 16:9 carousel
                .precision(Precision.INEXACT)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .diskCachePolicy(CachePolicy.ENABLED)
                .memoryCacheKey("featured_${template.id}_${if (isCurrentPage) "anim" else "static"}")
                .diskCacheKey("featured_${template.id}")
                .crossfade(true)
                .crossfade(200)
                .apply {
                    if (!isCurrentPage) {
                        // Static first frame only — bypasses animated WebP decoder
                        decoderFactory(BitmapFactoryDecoder.Factory())
                    }
                }
                .build()
        }
    } else {
        null
    }

    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f),
        shape = RoundedCornerShape(dimens.radiusXl),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Black)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // ✅ Only show image if request exists (within visible range)
            if (imageRequest != null) {
                AsyncImage(
                    model = imageRequest,
                    contentDescription = template.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                // Show placeholder for pages far from current
                ShimmerPlaceholder(
                    modifier = Modifier.fillMaxSize(),
                    cornerRadius = 0.dp
                )
            }

            // Bottom gradient scrim
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.45f)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                        )
                    )
                    .align(Alignment.BottomCenter)
            )

            // Content tags — top-left (only for premium templates)
            if (template.isPremium) {
                ContentTags(
                    tags = listOf(ContentTag.HOT, ContentTag.TRENDING),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(dimens.spaceMd)
                )
            }

            // Use count badge — top-right
            if (template.useCount > 0) {
                // ✅ FIX: Pre-calculate formatted use count once
                val formattedCount = remember(template.useCount) {
                    NumberFormatter.formatCount(template.useCount)
                }

                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(dimens.spaceMd)
                        .background(
                            color = TemplateBadgeBackground,
                            shape = RoundedCornerShape(999.dp)
                        )
                        .border(
                            width = 1.dp,
                            color = White12,
                            shape = RoundedCornerShape(999.dp)
                        )
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_heart),
                        contentDescription = null,
                        tint = Gray200,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = formattedCount,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = Gray200,
                        maxLines = 1
                    )
                }
            }

            // Template name — bottom-left
            Text(
                text = template.name,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(dimens.spaceMd)
            )
        }
    }
}

// ============================================
// TEMPLATE GRID (kept as-is)
// ============================================

/**
 * Template grid using reusable StaggeredGrid component
 */
@Composable
private fun TemplateGridSkeleton(modifier: Modifier = Modifier) {
    val dimens = AppDimens.current
    // Mimic staggered grid: alternating tall/short cards per column
    // Left col: tall, short, tall  |  Right col: short, tall, short
    val leftHeights  = listOf(220.dp, 140.dp, 200.dp)
    val rightHeights = listOf(150.dp, 210.dp, 130.dp)

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(dimens.spaceSm)
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(dimens.spaceSm)
        ) {
            leftHeights.forEach { h ->
                ShimmerPlaceholder(
                    modifier = Modifier.fillMaxWidth().height(h),
                    cornerRadius = dimens.radiusLg
                )
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(dimens.spaceSm)
        ) {
            rightHeights.forEach { h ->
                ShimmerPlaceholder(
                    modifier = Modifier.fillMaxWidth().height(h),
                    cornerRadius = dimens.radiusLg
                )
            }
        }
    }
}



// ============================================
// VIDEO TEMPLATE HELPERS (kept as-is)
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


// ============================================
// PREVIEW
// ============================================

private val previewTemplates = listOf(
    VideoTemplate(id = "1", name = "Summer Vibes", songId = 1, effectSetId = "e1", aspectRatio = "9:16", isPremium = true),
    VideoTemplate(id = "2", name = "Chill Lofi", songId = 2, effectSetId = "e2", aspectRatio = "1:1"),
    VideoTemplate(id = "3", name = "Retro Wave", songId = 3, effectSetId = "e3", aspectRatio = "9:16", isPremium = true),
    VideoTemplate(id = "4", name = "Birthday Bash", songId = 4, effectSetId = "e4", aspectRatio = "4:5"),
    VideoTemplate(id = "5", name = "Travel Diary", songId = 5, effectSetId = "e5", aspectRatio = "9:16"),
    VideoTemplate(id = "6", name = "Neon Nights", songId = 6, effectSetId = "e6", aspectRatio = "1:1"),
    VideoTemplate(id = "7", name = "Aesthetic Mood", songId = 7, effectSetId = "e7", aspectRatio = "9:16"),
    VideoTemplate(id = "8", name = "Cinematic", songId = 8, effectSetId = "e8", aspectRatio = "4:5", isPremium = true),
    VideoTemplate(id = "9", name = "Golden Hour", songId = 9, effectSetId = "e9", aspectRatio = "9:16"),
    VideoTemplate(id = "10", name = "Vintage Love", songId = 10, effectSetId = "e10", aspectRatio = "1:1"),
)

@Preview(widthDp = 375, heightDp = 812)
@Composable
private fun GalleryContentPreview() {
    VideoMakerTheme {
        Box(modifier = Modifier.fillMaxSize()) {
            Image(
                painter = painterResource(id = R.drawable.bg_home),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            GalleryContent(
                topBarHeight = 56.dp,
                listState = rememberLazyStaggeredGridState(),
                onUserInteraction = {},
                featuredTemplates = previewTemplates.take(5),
                homeBanners = emptyList(),
                vibeTags = listOf(
                    VibeTag("birthday", "Birthday", "🎂"),
                    VibeTag("travel", "Travel", "✈️"),
                    VibeTag("wedding", "Wedding", "💒")
                ),
                selectedVibeTagId = null,
                templateListState = TemplateListState.Success(previewTemplates),
                unlockedTemplateIds = emptySet(),  // Empty for preview
                isRefreshing = false,
                isVisible = true,
                onRefresh = {},
                onTemplateBannerClick = { _, _ -> },
                onSongBannerClick = { _, _ -> },
                onVibeTagSelected = {},
                onTemplateClick = { _, _ -> },
                onSeeAllTemplates = {},
                onCreateClick = {},
                onSearchClick = {}
            )
        }
    }
}

@Preview(widthDp = 375, heightDp = 812)
@Composable
private fun GalleryLoadingPreview() {
    VideoMakerTheme {
        Box(modifier = Modifier.fillMaxSize()) {
            Image(
                painter = painterResource(id = R.drawable.bg_home),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            GalleryLoadingContent(topBarHeight = 56.dp)
        }
    }
}
