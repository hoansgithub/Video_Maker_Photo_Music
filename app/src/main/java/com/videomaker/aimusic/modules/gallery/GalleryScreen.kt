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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.videomaker.aimusic.ui.theme.AppDimens
import com.videomaker.aimusic.ui.theme.GoldAccent
import com.videomaker.aimusic.ui.theme.Gray200
import com.videomaker.aimusic.ui.theme.Primary
import com.videomaker.aimusic.ui.theme.SearchFieldBackground
import com.videomaker.aimusic.ui.theme.SearchFieldBorder
import com.videomaker.aimusic.ui.components.AppFilterChip
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
import com.videomaker.aimusic.ui.components.ShimmerPlaceholder
import com.videomaker.aimusic.ui.components.StaggeredGrid
import com.videomaker.aimusic.ui.components.TemplateCard
import com.videomaker.aimusic.ui.components.bottomGradientOverlay
import com.videomaker.aimusic.ui.theme.VideoMakerTheme
import kotlinx.coroutines.delay

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
    onNavigateToAllTemplates: () -> Unit = {},
    onNavigateToCreate: () -> Unit = {},
    onNavigateToSearch: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val navigationEvent by viewModel.navigationEvent.collectAsStateWithLifecycle()

    // Handle navigation events
    LaunchedEffect(navigationEvent) {
        navigationEvent?.let { event ->
            when (event) {
                is GalleryNavigationEvent.NavigateToSongDetail -> onNavigateToSongDetail(event.songId)
                is GalleryNavigationEvent.NavigateToTemplateDetail -> onNavigateToTemplateDetail(event.templateId)
                is GalleryNavigationEvent.NavigateToAllTopSongs -> onNavigateToAllTopSongs()
                is GalleryNavigationEvent.NavigateToAllTemplates -> onNavigateToAllTemplates()
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
                featuredTemplates = state.featuredTemplates,
                vibeTags = state.vibeTags,
                selectedVibeTagId = state.selectedVibeTagId,
                templateListState = state.templateListState,
                onVibeTagSelected = viewModel::onVibeTagSelected,
                onTemplateClick = viewModel::onTemplateClick,
                onSeeAllTemplates = viewModel::onSeeAllTemplatesClick,
                onCreateClick = viewModel::onCreateClick,
                onSearchClick = onNavigateToSearch
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

@Composable
private fun GalleryContent(
    topBarHeight: Dp = 0.dp,
    featuredTemplates: List<VideoTemplate>,
    vibeTags: List<VibeTag>,
    selectedVibeTagId: String?,
    templateListState: TemplateListState,
    onVibeTagSelected: (String?) -> Unit,
    onTemplateClick: (VideoTemplate) -> Unit,
    onSeeAllTemplates: () -> Unit,
    onCreateClick: () -> Unit,
    onSearchClick: () -> Unit
) {
    val dimens = AppDimens.current

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
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
                    is TemplateListState.Success -> StaggeredTemplateGrid(
                        templates = templateListState.templates,
                        onTemplateClick = onTemplateClick,
                        spacing = dimens.spaceSm,
                        modifier = Modifier.padding(horizontal = dimens.spaceLg)
                    )
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

@Composable
private fun FeaturedTemplatesCarousel(
    templates: List<VideoTemplate>,
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

    val dimens = AppDimens.current

    Column(modifier = modifier) {
        HorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(horizontal = dimens.spaceLg),
            pageSpacing = dimens.spaceMd,
            modifier = Modifier.fillMaxWidth()
        ) { page ->
            val template = templates[page.mod(templates.size)]
            val isCurrentPage = page == pagerState.settledPage && !pagerState.isScrollInProgress
            FeaturedTemplateCard(
                template = template,
                isCurrentPage = isCurrentPage,
                onClick = { onTemplateClick(template) }
            )
        }

        Spacer(modifier = Modifier.height(dimens.spaceMd))

        PageIndicator(
            pageCount = templates.size,
            currentPage = pagerState.currentPage.mod(templates.size)
        )
    }
}

@Composable
private fun FeaturedTemplateCard(
    template: VideoTemplate,
    isCurrentPage: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dimens = AppDimens.current
    val context = LocalContext.current

    val imageRequest = remember(template.id, isCurrentPage) {
        ImageRequest.Builder(context)
            .data(template.thumbnailPath.ifEmpty { null })
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .memoryCacheKey("featured_${template.id}_${if (isCurrentPage) "anim" else "static"}")
            .diskCacheKey("featured_${template.id}")
            .crossfade(true)
            .apply {
                if (!isCurrentPage) {
                    // Static first frame only — bypasses animated WebP decoder
                    decoderFactory(BitmapFactoryDecoder.Factory())
                }
            }
            .build()
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
            AsyncImage(
                model = imageRequest,
                contentDescription = template.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

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
// TAG CHIP ROW
// ============================================

@Composable
private fun TagChipRow(
    vibeTags: List<VibeTag>,
    selectedTagId: String?,
    onTagSelected: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val dimens = AppDimens.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = dimens.spaceLg),
        horizontalArrangement = Arrangement.spacedBy(dimens.spaceSm)
    ) {
        // "For you" chip — clears filter
        AppFilterChip(
            text = stringResource(R.string.gallery_filter_for_you),
            isSelected = selectedTagId == null,
            onClick = { onTagSelected(null) }
        )
        vibeTags.forEach { tag ->
            AppFilterChip(
                text = if (tag.emoji.isNotEmpty()) "${tag.emoji} ${tag.displayName}" else tag.displayName,
                isSelected = tag.id == selectedTagId,
                onClick = { onTagSelected(tag.id) }
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

@Composable
private fun StaggeredTemplateGrid(
    templates: List<VideoTemplate>,
    onTemplateClick: (VideoTemplate) -> Unit,
    spacing: Dp,
    modifier: Modifier = Modifier,
    columns: Int = 2
) {
    if (templates.isEmpty()) return

    // Pre-calculate aspect ratios once
    val aspectRatios = remember(templates) {
        templates.map { parseAspectRatio(it.aspectRatio) }
    }

    StaggeredGrid(
        itemCount = templates.size,
        aspectRatios = aspectRatios,
        columns = columns,
        spacing = spacing,
        modifier = modifier
    ) { index ->
        key(templates[index].id) {
            TemplateCard(
                name = templates[index].name,
                thumbnailPath = templates[index].thumbnailPath,
                aspectRatio = aspectRatios[index],
                isPremium = templates[index].isPremium,
                useCount = templates[index].useCount,
                onClick = { onTemplateClick(templates[index]) }
            )
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
                featuredTemplates = previewTemplates.take(5),
                vibeTags = listOf(
                    VibeTag("birthday", "Birthday", "🎂"),
                    VibeTag("travel", "Travel", "✈️"),
                    VibeTag("wedding", "Wedding", "💒")
                ),
                selectedVibeTagId = null,
                templateListState = TemplateListState.Success(previewTemplates),
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
