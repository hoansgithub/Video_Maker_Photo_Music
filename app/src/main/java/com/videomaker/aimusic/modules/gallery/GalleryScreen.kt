package com.videomaker.aimusic.modules.gallery

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
import co.alcheclub.lib.acccore.di.ACCDI
import co.alcheclub.lib.acccore.di.get
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import com.videomaker.aimusic.di.GalleryViewModelFactory
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Precision
import coil.size.Size
import com.videomaker.aimusic.domain.model.VideoTemplate
import com.videomaker.aimusic.ui.components.PageIndicator
import com.videomaker.aimusic.ui.components.RankingTag
import com.videomaker.aimusic.ui.components.SectionHeader
import com.videomaker.aimusic.ui.components.ShimmerBox
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
    viewModel: GalleryViewModel = remember { ACCDI.get<GalleryViewModelFactory>().create() },
    onNavigateToSongDetail: (Int) -> Unit = {},
    onNavigateToTemplateDetail: (String) -> Unit = {},
    onNavigateToAllTopSongs: () -> Unit = {},
    onNavigateToAllTrendingTemplates: () -> Unit = {},
    onNavigateToAllPopularTemplates: () -> Unit = {},
    onNavigateToCreate: () -> Unit = {}
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
                is GalleryNavigationEvent.NavigateToAllTrendingTemplates -> onNavigateToAllTrendingTemplates()
                is GalleryNavigationEvent.NavigateToAllPopularTemplates -> onNavigateToAllPopularTemplates()
                is GalleryNavigationEvent.NavigateToCreate -> onNavigateToCreate()
            }
            viewModel.onNavigationHandled()
        }
    }

    // UI based on state
    when (val state = uiState) {
        is GalleryUiState.Loading -> GalleryLoadingContent()
        is GalleryUiState.Success -> GalleryContent(
            trendingSongs = state.trendingSongs,
            topSongs = state.topSongs,
            trendingTemplates = state.trendingTemplates,
            popularTemplates = state.popularTemplates,
            onTrendingSongClick = viewModel::onTrendingSongClick,
            onTopSongClick = viewModel::onTopSongClick,
            onTemplateClick = viewModel::onTemplateClick,
            onSeeAllTopSongs = viewModel::onSeeAllTopSongsClick,
            onSeeAllTrendingTemplates = viewModel::onSeeAllTrendingTemplatesClick,
            onSeeAllPopularTemplates = viewModel::onSeeAllPopularTemplatesClick
        )
        is GalleryUiState.Error -> GalleryErrorContent(message = state.message)
    }
}

/**
 * Shimmer skeleton loading for gallery content
 * Mimics the actual layout structure for smooth transition
 */
@Composable
private fun GalleryLoadingContent() {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp),
        userScrollEnabled = false
    ) {
        // Shimmer Banner
        item {
            ShimmerPlaceholder(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .aspectRatio(16f / 9f),
                cornerRadius = 16.dp
            )
        }

        item { Spacer(modifier = Modifier.height(12.dp)) }

        // Shimmer Top Songs Section
        item {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                // Section header shimmer
                ShimmerBox(
                    modifier = Modifier
                        .width(100.dp)
                        .height(18.dp),
                    cornerRadius = 4.dp
                )
                Spacer(modifier = Modifier.height(8.dp))
                // Song items shimmer
                repeat(4) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ShimmerBox(
                            modifier = Modifier.size(44.dp),
                            cornerRadius = 6.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            ShimmerBox(
                                modifier = Modifier
                                    .fillMaxWidth(0.6f)
                                    .height(14.dp),
                                cornerRadius = 4.dp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            ShimmerBox(
                                modifier = Modifier
                                    .width(50.dp)
                                    .height(10.dp),
                                cornerRadius = 4.dp
                            )
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(12.dp)) }

        // Shimmer Templates Section
        item {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                // Section header shimmer
                ShimmerBox(
                    modifier = Modifier
                        .width(140.dp)
                        .height(18.dp),
                    cornerRadius = 4.dp
                )
                Spacer(modifier = Modifier.height(8.dp))
                // Template grid shimmer (2 columns)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ShimmerPlaceholder(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(9f / 16f),
                        cornerRadius = 12.dp
                    )
                    ShimmerPlaceholder(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(9f / 16f),
                        cornerRadius = 12.dp
                    )
                }
            }
        }
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
    trendingSongs: List<TrendingSong>,
    topSongs: List<TopSong>,
    trendingTemplates: List<VideoTemplate>,
    popularTemplates: List<VideoTemplate>,
    onTrendingSongClick: (TrendingSong) -> Unit,
    onTopSongClick: (TopSong) -> Unit,
    onTemplateClick: (VideoTemplate) -> Unit,
    onSeeAllTopSongs: () -> Unit,
    onSeeAllTrendingTemplates: () -> Unit,
    onSeeAllPopularTemplates: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        // Section 1: Trending Banner - contentType helps recycling
        item(key = "banner", contentType = "banner") {
            TrendingBanner(
                songs = trendingSongs,
                onSongClick = onTrendingSongClick
            )
        }

        item(key = "spacer1", contentType = "spacer") {
            Spacer(modifier = Modifier.height(12.dp))
        }

        // Section 2: Top Songs
        item(key = "top_songs", contentType = "songs_section") {
            TopSongsSection(
                songs = topSongs,
                onSongClick = onTopSongClick,
                onSeeAllClick = onSeeAllTopSongs
            )
        }

        item(key = "spacer2", contentType = "spacer") {
            Spacer(modifier = Modifier.height(12.dp))
        }

        // Section 3: Trending Templates
        item(key = "trending_section", contentType = "template_grid") {
            SectionHeader(
                title = "Trending Templates",
                icon = Icons.Default.Whatshot,
                iconTint = Color(0xFFFF6B6B),
                onSeeAllClick = onSeeAllTrendingTemplates
            )
            Spacer(modifier = Modifier.height(8.dp))
            StaggeredTemplateGrid(
                templates = trendingTemplates,
                onTemplateClick = onTemplateClick,
                spacing = 8.dp,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        item(key = "spacer3", contentType = "spacer") {
            Spacer(modifier = Modifier.height(12.dp))
        }

        // Section 4: Popular Templates
        item(key = "popular_section", contentType = "template_grid") {
            SectionHeader(
                title = "Popular Templates",
                icon = Icons.Default.Star,
                iconTint = Color(0xFFFFD700),
                onSeeAllClick = onSeeAllPopularTemplates
            )
            Spacer(modifier = Modifier.height(8.dp))
            StaggeredTemplateGrid(
                templates = popularTemplates,
                onTemplateClick = onTemplateClick,
                spacing = 8.dp,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
    }
}

/**
 * Template grid using reusable StaggeredGrid component
 */
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
// TRENDING BANNER
// ============================================

@Composable
private fun TrendingBanner(
    songs: List<TrendingSong>,
    onSongClick: (TrendingSong) -> Unit,
    autoSlideIntervalMs: Long = 5000L,
    modifier: Modifier = Modifier
) {
    if (songs.isEmpty()) return

    val infinitePageCount = Int.MAX_VALUE
    val initialPage = infinitePageCount / 2

    val pagerState = rememberPagerState(
        initialPage = initialPage,
        pageCount = { infinitePageCount }
    )

    val isDragged by pagerState.interactionSource.collectIsDraggedAsState()
    val lifecycleOwner = LocalLifecycleOwner.current

    // Lifecycle-aware auto-slide
    LaunchedEffect(isDragged) {
        if (!isDragged) {
            lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                while (true) {
                    delay(autoSlideIntervalMs)
                    if (!pagerState.isScrollInProgress) {
                        pagerState.animateScrollToPage(pagerState.currentPage + 1)
                    }
                }
            }
        }
    }

    Column(modifier = modifier) {
        HorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(horizontal = 16.dp),
            pageSpacing = 12.dp,
            modifier = Modifier.fillMaxWidth()
        ) { page ->
            val actualIndex = page.mod(songs.size)
            TrendingBannerItem(
                song = songs[actualIndex],
                onClick = { onSongClick(songs[actualIndex]) }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Page Indicator
        PageIndicator(
            pageCount = songs.size,
            currentPage = pagerState.currentPage.mod(songs.size)
        )
    }
}

@Composable
private fun TrendingBannerItem(
    song: TrendingSong,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Remember ImageRequest to avoid re-creation on every recomposition
    val imageRequest = remember(song.coverUrl, song.id) {
        ImageRequest.Builder(context)
            .data(song.coverUrl)
            .size(Size(720, 405)) // 16:9 at reasonable resolution
            .bitmapConfig(Bitmap.Config.RGB_565)
            .precision(Precision.INEXACT)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .memoryCacheKey("banner_${song.id}")
            .diskCacheKey("banner_${song.id}")
            .crossfade(150)
            .allowHardware(true)
            .build()
    }

    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Banner uses SubcomposeAsyncImage for shimmer during load (important for carousel)
            if (song.coverUrl.isNotEmpty()) {
                SubcomposeAsyncImage(
                    model = imageRequest,
                    contentDescription = song.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    loading = {
                        ShimmerPlaceholder(
                            modifier = Modifier.fillMaxSize(),
                            cornerRadius = 0.dp
                        )
                    },
                    error = {
                        ShimmerPlaceholder(
                            modifier = Modifier.fillMaxSize(),
                            cornerRadius = 0.dp
                        )
                    }
                )
            } else {
                ShimmerPlaceholder(
                    modifier = Modifier.fillMaxSize(),
                    cornerRadius = 0.dp
                )
            }

            // Gradient overlay for text readability
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .bottomGradientOverlay()
            )

            // #trending tag
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp)
                    .background(
                        color = Color(0xFFFF4757),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "#trending",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }

            // Song info
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp)
            ) {
                Text(
                    text = song.name,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = song.artist,
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// ============================================
// TOP SONGS SECTION
// ============================================

@Composable
private fun TopSongsSection(
    songs: List<TopSong>,
    onSongClick: (TopSong) -> Unit,
    onSeeAllClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (songs.isEmpty()) return

    val itemsPerPage = 4
    val pageCount = (songs.size + itemsPerPage - 1) / itemsPerPage

    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { pageCount }
    )

    Column(modifier = modifier) {
        SectionHeader(
            title = "Top Songs",
            icon = Icons.Default.Whatshot,
            iconTint = Color(0xFFFF6B6B),
            onSeeAllClick = onSeeAllClick
        )

        Spacer(modifier = Modifier.height(8.dp))

        HorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(start = 16.dp, end = 48.dp),
            pageSpacing = 12.dp,
            modifier = Modifier.fillMaxWidth()
        ) { page ->
            val startIndex = page * itemsPerPage
            val endIndex = minOf(startIndex + itemsPerPage, songs.size)
            val pageSongs = songs.subList(startIndex, endIndex)

            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                pageSongs.forEach { song ->
                    TopSongItem(
                        song = song,
                        onClick = { onSongClick(song) }
                    )
                }
            }
        }
    }
}

@Composable
private fun TopSongItem(
    song: TopSong,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val thumbnailSizePx = remember(density) { with(density) { 44.dp.roundToPx() } }

    // Remember ImageRequest to avoid re-creation on every recomposition
    val imageRequest = remember(song.coverUrl, song.id, thumbnailSizePx) {
        ImageRequest.Builder(context)
            .data(song.coverUrl)
            .size(Size(thumbnailSizePx, thumbnailSizePx))
            .bitmapConfig(Bitmap.Config.RGB_565)
            .precision(Precision.INEXACT)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .memoryCacheKey("song_thumb_${song.id}")
            .crossfade(100)
            .allowHardware(true)
            .build()
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .clickable(onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Optimized thumbnail with AsyncImage (lighter than SubcomposeAsyncImage)
        if (song.coverUrl.isNotEmpty()) {
            AsyncImage(
                model = imageRequest,
                contentDescription = song.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(6.dp))
            )
        } else {
            ShimmerBox(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(6.dp)),
                cornerRadius = 6.dp
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.name,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = null,
                    tint = Color(0xFFFF6B6B),
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = formatLikes(song.likes),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        RankingTag(ranking = song.ranking)
    }
}

// ============================================
// VIDEO TEMPLATE HELPERS
// ============================================

/**
 * Parse aspect ratio string (e.g., "9:16", "16:9", "1:1") to float
 * Memoized via remember {} at call site to avoid recalculation
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
 * Optimized VideoTemplateItem with:
 * - AsyncImage (lighter than SubcomposeAsyncImage, fewer recompositions)
 * - RGB_565 bitmap config (50% memory savings)
 * - drawWithCache for gradient overlay (cached between recompositions)
 * - Remembered ImageRequest to avoid re-allocation
 * - Aspect ratio support for staggered grid
 */
@Composable
private fun VideoTemplateItem(
    template: VideoTemplate,
    aspectRatio: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Remember ImageRequest to avoid re-creation on every recomposition
    val imageRequest = remember(template.thumbnailUrl, template.id) {
        ImageRequest.Builder(context)
            .data(template.thumbnailUrl)
            .size(Size(400, 700))
            .bitmapConfig(Bitmap.Config.RGB_565)
            .precision(Precision.INEXACT)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .memoryCacheKey("template_${template.id}")
            .diskCacheKey("template_${template.id}")
            .crossfade(150)
            .allowHardware(true)
            .build()
    }

    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(aspectRatio),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Thumbnail with AsyncImage (lighter weight than SubcomposeAsyncImage)
            if (template.thumbnailUrl.isNotEmpty()) {
                AsyncImage(
                    model = imageRequest,
                    contentDescription = template.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                // Animated shimmer for items without thumbnail
                ShimmerPlaceholder(
                    modifier = Modifier.fillMaxSize(),
                    cornerRadius = 0.dp
                )
            }

            // Gradient overlay for text readability
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .bottomGradientOverlay()
            )

            // Premium badge
            if (template.isPremium) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .background(
                            color = Color(0xFFFFD700),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "PRO",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                }
            }

            // Template name
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(10.dp)
            ) {
                Text(
                    text = template.name,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun formatLikes(likes: Int): String {
    return when {
        likes >= 1_000_000 -> String.format("%.1fM", likes / 1_000_000.0)
        likes >= 1_000 -> String.format("%.1fK", likes / 1_000.0)
        else -> likes.toString()
    }
}

// ============================================
// PREVIEW
// ============================================

@Preview(showBackground = true, backgroundColor = 0xFF1A1A1A)
@Composable
private fun GalleryScreenPreview() {
    VideoMakerTheme {
        GalleryScreen()
    }
}
