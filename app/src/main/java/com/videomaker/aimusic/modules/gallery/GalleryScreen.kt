package com.videomaker.aimusic.modules.gallery

import java.util.Locale
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons

import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.videomaker.aimusic.R
import com.videomaker.aimusic.core.analytics.Analytics
import com.videomaker.aimusic.core.analytics.AnalyticsEvent
import com.videomaker.aimusic.ui.theme.AppDimens
import com.videomaker.aimusic.ui.theme.GoldAccent
import com.videomaker.aimusic.ui.theme.Gray200
import com.videomaker.aimusic.ui.theme.Primary
import com.videomaker.aimusic.ui.theme.SearchFieldBackground
import com.videomaker.aimusic.ui.theme.SearchFieldBorder
import com.videomaker.aimusic.ui.components.AppFilterChip
import com.videomaker.aimusic.ui.components.TagChipRow
import com.videomaker.aimusic.ui.components.PrimaryButton
import com.videomaker.aimusic.ui.theme.TextTertiary
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.decode.BitmapFactoryDecoder
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Precision
import coil.size.Size
import com.videomaker.aimusic.domain.model.VibeTag
import com.videomaker.aimusic.domain.model.VideoTemplate
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.videomaker.aimusic.ui.components.PageIndicator
import com.videomaker.aimusic.ui.components.SectionHeader
import com.videomaker.aimusic.ui.components.ContentTag
import com.videomaker.aimusic.ui.components.ContentTags
import com.videomaker.aimusic.ui.components.ShimmerPlaceholder
import com.videomaker.aimusic.ui.components.StaggeredGrid
import com.videomaker.aimusic.ui.components.TemplateCard
import com.videomaker.aimusic.ui.components.bottomGradientOverlay
import com.videomaker.aimusic.ui.theme.VideoMakerTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.Stable
import androidx.compose.runtime.Immutable
import co.alcheclub.lib.acccore.ads.compose.NativeAdView
import com.videomaker.aimusic.core.constants.AdPlacement

// ============================================
// GALLERY GRID ITEM (Template + Ad)
// ============================================

@Immutable
private sealed class GalleryGridItem {
    data class TemplateItem(val template: VideoTemplate) : GalleryGridItem()
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
    onNavigateToSongDetail: (Long) -> Unit = {},
    onNavigateToTemplateDetail: (String) -> Unit = {},
    onNavigateToAllTopSongs: () -> Unit = {},
    onNavigateToAllTemplates: (String?) -> Unit = {},
    onNavigateToCreate: () -> Unit = {},
    onNavigateToSearch: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val navigationEvent by viewModel.navigationEvent.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val screenSessionId = remember { Analytics.newScreenSessionId() }

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

    // Handle navigation events
    LaunchedEffect(navigationEvent) {
        navigationEvent?.let { event ->
            when (event) {
                is GalleryNavigationEvent.NavigateToSongDetail -> onNavigateToSongDetail(event.songId)
                is GalleryNavigationEvent.NavigateToTemplateDetail -> onNavigateToTemplateDetail(event.templateId)
                is GalleryNavigationEvent.NavigateToAllTopSongs -> onNavigateToAllTopSongs()
                is GalleryNavigationEvent.NavigateToAllTemplates -> onNavigateToAllTemplates(event.selectedVibeTagId)
                is GalleryNavigationEvent.NavigateToCreate -> onNavigateToCreate()
            }
            viewModel.onNavigationHandled()
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
                screenSessionId = screenSessionId,
                featuredTemplates = state.featuredTemplates,
                vibeTags = state.vibeTags,
                selectedVibeTagId = state.selectedVibeTagId,
                templateListState = state.templateListState,
                isRefreshing = isRefreshing,
                onRefresh = viewModel::refresh,
                onVibeTagSelected = { selectedTagId ->
                    val selectedTag = state.vibeTags.firstOrNull { it.id == selectedTagId }
                    Analytics.trackTemplateGenreClick(
                        genreId = selectedTagId ?: AnalyticsEvent.Value.ALL,
                        genreName = selectedTag?.displayName ?: AnalyticsEvent.Value.ALL,
                        location = AnalyticsEvent.Value.Location.GALLERY
                    )
                    viewModel.onVibeTagSelected(selectedTagId)
                },
                onTemplateClick = viewModel::onTemplateClick,
                onSeeAllTemplates = viewModel::onSeeAllTemplatesClick,
                onCreateClick = {
                    Analytics.trackCreationStart(AnalyticsEvent.Value.Location.GALLERY)
                    viewModel.onCreateClick()
                },
                onSearchClick = onNavigateToSearch,
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
    screenSessionId: String,
    featuredTemplates: List<VideoTemplate>,
    vibeTags: List<VibeTag>,
    selectedVibeTagId: String?,
    templateListState: TemplateListState,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onVibeTagSelected: (String?) -> Unit,
    onTemplateClick: (VideoTemplate) -> Unit,
    onSeeAllTemplates: () -> Unit,
    onCreateClick: () -> Unit,
    onSearchClick: () -> Unit,
    onLoadMore: () -> Unit = {}
) {
    val dimens = AppDimens.current
    val listState = rememberLazyListState()
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

    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .drop(1)
            .collect { (index, offset) ->
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
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = topBarHeight + dimens.spaceLg,
                    bottom = dimens.space3Xl + dimens.space2Xl
                )
            ) {
            // Section 1: Search Field
            item(key = "search", contentType = "search") {
                GallerySearchField(
                    onClick = onSearchClick,
                    modifier = Modifier.padding(
                        horizontal = dimens.spaceLg,
                        vertical = dimens.spaceMd
                    )
                )
            }

            // Section 2: Featured Templates Carousel
            if (featuredTemplates.isNotEmpty()) {
                item(key = "featured_templates", contentType = "featured_carousel") {
                    FeaturedTemplatesCarousel(
                        templates = featuredTemplates,
                        screenSessionId = screenSessionId,
                        onTemplateClick = onTemplateClick
                    )
                }
            }

            item(key = "spacer1", contentType = "spacer") {
                Spacer(modifier = Modifier.height(dimens.spaceMd))
            }

            // Section 3: Templates header + tag chips
            item(key = "templates_header", contentType = "templates_header") {
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

            // Templates grid — shows loading shimmer or results
            item(key = "template_grid", contentType = "template_grid") {
                when (templateListState) {
                    is TemplateListState.Loading -> TemplateGridSkeleton(
                        modifier = Modifier.padding(horizontal = dimens.spaceLg)
                    )
                    is TemplateListState.Success -> {
                        StaggeredTemplateGrid(
                            templates = templateListState.templates,
                            screenSessionId = screenSessionId,
                            onTemplateClick = onTemplateClick,
                            spacing = dimens.spaceSm,
                            modifier = Modifier.padding(horizontal = dimens.spaceLg)
                        )
                    }
                }
            }

            // Loading more indicator
            if (templateListState is TemplateListState.Success && templateListState.isLoadingMore) {
                item(key = "loading_more", contentType = "loading_more") {
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

        // Bottom gradient fade — dark to transparent, behind the button
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(120.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            MaterialTheme.colorScheme.background
                        )
                    )
                )
        )

        // Floating Create button — only shown once featured templates are loaded
        if (featuredTemplates.isNotEmpty()) {
            CreateNewVideoButton(
                onClick = onCreateClick,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(bottom = dimens.spaceLg)
            )
        }
    }
}

// ============================================
// CREATE NEW VIDEO BUTTON
// ============================================

@Composable
private fun CreateNewVideoButton(
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
    screenSessionId: String,
    onTemplateClick: (VideoTemplate) -> Unit,
    autoSlideIntervalMs: Long = 4000L,
    modifier: Modifier = Modifier
) {
    val infinitePageCount = Int.MAX_VALUE
    val initialPage = infinitePageCount / 2

    val pagerState = rememberPagerState(
        initialPage = initialPage,
        pageCount = { infinitePageCount }
    )

    val isDragged by pagerState.interactionSource.collectIsDraggedAsState()
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(Unit) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                delay(autoSlideIntervalMs)
                if (!isDragged && !pagerState.isScrollInProgress) {
                    pagerState.animateScrollToPage(pagerState.currentPage + 1)
                }
            }
        }
    }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { settledPage ->
                Analytics.trackGallerySwipe(AnalyticsEvent.Value.Location.GALLERY_BANNER)
                val template = templates[settledPage.mod(templates.size)]
                Analytics.trackTemplateImpression(
                    templateId = template.id,
                    templateName = template.name,
                    location = AnalyticsEvent.Value.Location.GALLERY_BANNER,
                    screenSessionId = screenSessionId
                )
            }
    }

    val dimens = AppDimens.current

    Column(modifier = modifier) {
        HorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(horizontal = dimens.spaceLg),
            pageSpacing = dimens.spaceMd,
            modifier = Modifier.fillMaxWidth()
        ) { page ->
            // ✅ FIX: Calculate index once to avoid repeated modulo operations
            val templateIndex = page.mod(templates.size)
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
                        location = AnalyticsEvent.Value.Location.GALLERY_BANNER
                    )
                    onTemplateClick(template)
                }
            )
        }

        Spacer(modifier = Modifier.height(dimens.spaceMd))

        // ✅ FIX: Memoize current page calculation
        val currentPageIndex = remember(pagerState.currentPage, templates.size) {
            pagerState.currentPage.mod(templates.size)
        }

        PageIndicator(
            pageCount = templates.size,
            currentPage = currentPageIndex
        )
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

            // Content tags — top-left
            ContentTags(
                tags = listOf(ContentTag.HOT, ContentTag.TRENDING),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(dimens.spaceMd)
            )

            // Use count badge — top-right
            if (template.useCount > 0) {
                // ✅ FIX: Pre-calculate formatted use count once
                val formattedCount = remember(template.useCount) {
                    formatUseCount(template.useCount)
                }

                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(dimens.spaceMd)
                        .background(
                            color = Color.Black.copy(alpha = 0.6f),
                            shape = RoundedCornerShape(999.dp)
                        )
                        .border(
                            width = 1.dp,
                            color = Color.White.copy(alpha = 0.12f),
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

/**
 * Format use count for display (1000 -> 1K, 1500000 -> 1.5M)
 */
private fun formatUseCount(count: Long): String = when {
    count >= 1_000_000 -> {
        val v = count / 1_000_000.0
        if (v % 1.0 == 0.0) "${v.toLong()}M" else "%.1fM".format(v)
    }
    count >= 1_000 -> {
        val v = count / 1_000.0
        if (v % 1.0 == 0.0) "${v.toLong()}K" else "%.1fK".format(v)
    }
    else -> count.toString()
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

@Composable
private fun StaggeredTemplateGrid(
    templates: List<VideoTemplate>,
    screenSessionId: String,
    onTemplateClick: (VideoTemplate) -> Unit,
    spacing: Dp,
    modifier: Modifier = Modifier,
    columns: Int = 2
) {
    if (templates.isEmpty()) return

    // ✅ FIX: Build grid items list (templates + ad)
    // Position: AD_INSERTION_INDEX (4th position), or last if < 3 items
    val gridItems = remember(templates) {
        buildList {
            if (templates.size < AD_INSERTION_INDEX) {
                // Show ad at last position if < 3 templates
                templates.forEach { add(GalleryGridItem.TemplateItem(it)) }
                add(GalleryGridItem.AdItem)
            } else {
                // Insert ad at AD_INSERTION_INDEX (after 3rd template at index 2)
                templates.forEachIndexed { index, template ->
                    add(GalleryGridItem.TemplateItem(template))
                    if (index == AD_INSERTION_INDEX - 1) {  // After (AD_INSERTION_INDEX - 1)th template
                        add(GalleryGridItem.AdItem)
                    }
                }
            }
        }
    }

    // ✅ OPTIMIZED: Pre-calculate aspect ratios once when grid items change
    val aspectRatios = remember(gridItems.size, gridItems.firstOrNull()) {
        gridItems.map { item ->
            when (item) {
                is GalleryGridItem.TemplateItem -> parseAspectRatio(item.template.aspectRatio)
                is GalleryGridItem.AdItem -> 9f / 16f  // 9:16 portrait (matches native_project_card)
            }
        }
    }

    StaggeredGrid(
        items = gridItems,
        aspectRatios = aspectRatios,
        columns = columns,
        spacing = spacing,
        modifier = modifier,
        key = ::galleryItemKey
    ) { item ->
        when (item) {
            is GalleryGridItem.TemplateItem -> {
                val template = item.template

                // ✅ OPTIMIZED: Pre-calculate aspect ratio for this specific template
                val aspectRatio = remember(template.aspectRatio) {
                    parseAspectRatio(template.aspectRatio)
                }

                // ✅ Track impression only once when template enters composition
                LaunchedEffect(Unit) {
                    Analytics.trackTemplateImpression(
                        templateId = template.id,
                        templateName = template.name,
                        location = AnalyticsEvent.Value.Location.GALLERY_TEMPLATE,
                        screenSessionId = screenSessionId
                    )
                }

                TemplateCard(
                    name = template.name,
                    thumbnailPath = template.thumbnailPath,
                    aspectRatio = aspectRatio,
                    isPremium = template.isPremium,
                    showHotTag = true,  // Show Hot tag in Gallery tab only
                    useCount = template.useCount,
                    onClick = {
                        Analytics.trackTemplateClick(
                            templateId = template.id,
                            templateName = template.name,
                            location = AnalyticsEvent.Value.Location.GALLERY_TEMPLATE
                        )
                        onTemplateClick(template)
                    }
                )
            }
            is GalleryGridItem.AdItem -> {
                // Native ad card (9:16 portrait, matches template cards)
                NativeAdView(
                    placement = AdPlacement.NATIVE_GALLERY_GRID,
                    modifier = Modifier.fillMaxWidth()
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
                screenSessionId = "preview",
                featuredTemplates = previewTemplates.take(5),
                vibeTags = listOf(
                    VibeTag("birthday", "Birthday", "🎂"),
                    VibeTag("travel", "Travel", "✈️"),
                    VibeTag("wedding", "Wedding", "💒")
                ),
                selectedVibeTagId = null,
                templateListState = TemplateListState.Success(previewTemplates),
                isRefreshing = false,
                onRefresh = {},
                onVibeTagSelected = {},
                onTemplateClick = {},
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
