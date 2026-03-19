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
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.AutoAwesome
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.videomaker.aimusic.R
import com.videomaker.aimusic.ui.theme.AmberAccent
import com.videomaker.aimusic.ui.theme.AppDimens
import com.videomaker.aimusic.ui.theme.Black24
import com.videomaker.aimusic.ui.theme.ChipBorderInactive
import com.videomaker.aimusic.ui.theme.CyanAccent
import com.videomaker.aimusic.ui.theme.GoldAccent
import com.videomaker.aimusic.ui.theme.Gray200
import com.videomaker.aimusic.ui.theme.Gray450
import com.videomaker.aimusic.ui.theme.GreenAccent
import com.videomaker.aimusic.ui.theme.OrangeAccent
import com.videomaker.aimusic.ui.theme.PinkAccent
import com.videomaker.aimusic.ui.theme.Primary
import com.videomaker.aimusic.ui.theme.RoseAccent
import com.videomaker.aimusic.ui.theme.SearchFieldBackground
import com.videomaker.aimusic.ui.theme.SearchFieldBorder
import com.videomaker.aimusic.ui.theme.SlateAccent
import com.videomaker.aimusic.ui.theme.SurfaceDark
import com.videomaker.aimusic.ui.theme.TealAccent
import com.videomaker.aimusic.ui.components.PrimaryButton
import com.videomaker.aimusic.ui.theme.TextBright
import com.videomaker.aimusic.ui.theme.TextOnPrimary
import com.videomaker.aimusic.ui.theme.TextSecondary
import com.videomaker.aimusic.ui.theme.TextTertiary
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
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
            is GalleryUiState.Loading -> GalleryLoadingContent()
            is GalleryUiState.Success -> GalleryContent(
                topBarHeight = topBarHeight,
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
private fun GalleryLoadingContent() {
    val dimens = AppDimens.current

    Column(
        modifier = Modifier
            .fillMaxSize()
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

            // Section 2: Featured Effects Showcase
            item(key = "featured_effects", contentType = "effects_showcase") {
                FeaturedEffectsShowcase()
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

        // Floating Create button
        CreateNewVideoButton(
            onClick = onCreateClick,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = dimens.spaceLg)
        )
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
// FEATURED EFFECTS SHOWCASE
// ============================================

private data class FeaturedEffect(
    val name: String,
    val description: String,
    val accentColor: Color
)

private fun buildSampleEffects(context: android.content.Context) = listOf(
    FeaturedEffect(context.getString(R.string.effect_dreamy_vibes_title), context.getString(R.string.effect_dreamy_vibes_desc), RoseAccent),
    FeaturedEffect(context.getString(R.string.effect_cyberpunk_title), context.getString(R.string.effect_cyberpunk_desc), CyanAccent),
    FeaturedEffect(context.getString(R.string.effect_vintage_film_title), context.getString(R.string.effect_vintage_film_desc), AmberAccent),
    FeaturedEffect(context.getString(R.string.effect_ocean_breeze_title), context.getString(R.string.effect_ocean_breeze_desc), TealAccent),
    FeaturedEffect(context.getString(R.string.effect_golden_hour_title), context.getString(R.string.effect_golden_hour_desc), OrangeAccent),
    FeaturedEffect(context.getString(R.string.effect_noir_title), context.getString(R.string.effect_noir_desc), SlateAccent),
    FeaturedEffect(context.getString(R.string.effect_sakura_title), context.getString(R.string.effect_sakura_desc), PinkAccent),
    FeaturedEffect(context.getString(R.string.effect_aurora_title), context.getString(R.string.effect_aurora_desc), GreenAccent),
)

@Composable
private fun FeaturedEffectsShowcase(
    autoSlideIntervalMs: Long = 4000L,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val sampleEffects = remember(context) { buildSampleEffects(context) }
    val infinitePageCount = Int.MAX_VALUE
    val initialPage = infinitePageCount / 2

    val pagerState = rememberPagerState(
        initialPage = initialPage,
        pageCount = { infinitePageCount }
    )

    val isDragged by pagerState.interactionSource.collectIsDraggedAsState()
    val lifecycleOwner = LocalLifecycleOwner.current

    // Lifecycle-aware auto-slide — single coroutine, checks isDragged inside loop to
    // avoid multiple competing coroutines from repeated drag/release interactions
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
            val actualIndex = page.mod(sampleEffects.size)
            FeaturedEffectCard(effect = sampleEffects[actualIndex])
        }

        Spacer(modifier = Modifier.height(dimens.spaceMd))

        PageIndicator(
            pageCount = sampleEffects.size,
            currentPage = pagerState.currentPage.mod(sampleEffects.size)
        )
    }
}

@Composable
private fun FeaturedEffectCard(
    effect: FeaturedEffect,
    modifier: Modifier = Modifier
) {
    val dimens = AppDimens.current

    Card(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f),
        shape = RoundedCornerShape(dimens.radiusXl),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            effect.accentColor.copy(alpha = 0.6f),
                            effect.accentColor.copy(alpha = 0.15f),
                            SurfaceDark
                        )
                    )
                )
        ) {
            // Decorative icon
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = effect.accentColor.copy(alpha = 0.2f),
                modifier = Modifier
                    .size(120.dp)
                    .align(Alignment.TopEnd)
                    .padding(dimens.spaceLg)
            )

            // Effect info
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(dimens.spaceLg)
            ) {
                Text(
                    text = effect.name,
                    style = MaterialTheme.typography.titleLarge,
                    color = TextBright,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(dimens.spaceXs))
                Text(
                    text = effect.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextBright.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
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
        // "All" chip — clears filter
        TagChip(
            label = "All",
            isSelected = selectedTagId == null,
            onClick = { onTagSelected(null) }
        )
        vibeTags.forEach { tag ->
            TagChip(
                label = if (tag.emoji.isNotEmpty()) "${tag.emoji} ${tag.displayName}" else tag.displayName,
                isSelected = tag.id == selectedTagId,
                onClick = { onTagSelected(tag.id) }
            )
        }
    }
}

@Composable
private fun TagChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dimens = AppDimens.current
    val borderColor = if (isSelected) Primary else ChipBorderInactive
    val textColor = if (isSelected) Primary else Gray450

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(dimens.radiusFull))
            .background(Black24)
            .border(
                width = 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(dimens.radiusFull)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = dimens.spaceMd, vertical = dimens.spaceSm),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
            color = textColor
        )
    }
}

// ============================================
// TEMPLATE GRID (kept as-is)
// ============================================

/**
 * Template grid using reusable StaggeredGrid component
 */
@Composable
private fun TemplateGridSkeleton(
    modifier: Modifier = Modifier,
    columns: Int = 2
) {
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
            VideoTemplateItem(
                template = templates[index],
                aspectRatio = aspectRatios[index],
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

private fun formatUseCount(count: Long): String = when {
    count >= 1_000_000 -> {
        val v = count / 1_000_000.0
        if (v % 1.0 == 0.0) "${v.toLong()}M"
        else String.format(Locale.US, "%.1fM", v)
    }
    count >= 1_000 -> {
        val v = count / 1_000.0
        if (v % 1.0 == 0.0) "${v.toLong()}k"
        else String.format(Locale.US, "%.1fk", v)
    }
    else -> count.toString()
}

@Composable
private fun VideoTemplateItem(
    template: VideoTemplate,
    aspectRatio: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val imageRequest = remember(template.thumbnailPath, template.id) {
        ImageRequest.Builder(context)
            .data(template.thumbnailPath)
            .size(Size(400, 700))
            .precision(Precision.INEXACT)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .memoryCacheKey("template_${template.id}")
            .diskCacheKey("template_${template.id}")
            .build()
    }

    val dimens = AppDimens.current

    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(aspectRatio),
        shape = RoundedCornerShape(dimens.radiusLg),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (template.thumbnailPath.isNotEmpty()) {
                AsyncImage(
                    model = imageRequest,
                    contentDescription = template.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                ShimmerPlaceholder(
                    modifier = Modifier.fillMaxSize(),
                    cornerRadius = 0.dp
                )
            }

            // Premium badge
            if (template.isPremium) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(dimens.spaceSm)
                        .background(
                            color = GoldAccent,
                            shape = RoundedCornerShape(dimens.radiusMd)
                        )
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "PRO",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = Color.Black
                    )
                }
            }

            // Use count badge
            if (template.useCount > 0) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(dimens.spaceSm)
                        .background(
                            color = Color(0xE5282828),
                            shape = RoundedCornerShape(999.dp)
                        )
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Repeat,
                        contentDescription = null,
                        tint = Gray200,
                        modifier = Modifier.size(10.dp)
                    )
                    Text(
                        text = formatUseCount(template.useCount),
                        style = MaterialTheme.typography.labelSmall,
                        color = Gray200,
                        maxLines = 1
                    )
                }
            }
        }
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
            GalleryLoadingContent()
        }
    }
}
