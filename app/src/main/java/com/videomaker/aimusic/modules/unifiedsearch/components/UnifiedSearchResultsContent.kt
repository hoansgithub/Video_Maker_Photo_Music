package com.videomaker.aimusic.modules.unifiedsearch.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Precision
import coil.size.Size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.videomaker.aimusic.R
import com.videomaker.aimusic.core.analytics.Analytics
import com.videomaker.aimusic.core.analytics.AnalyticsEvent
import com.videomaker.aimusic.domain.model.MusicSong
import com.videomaker.aimusic.domain.model.VideoTemplate
import com.videomaker.aimusic.modules.unifiedsearch.MusicSectionState
import com.videomaker.aimusic.modules.unifiedsearch.TemplateSectionState
import com.videomaker.aimusic.modules.unifiedsearch.UnifiedSearchUiState
import com.videomaker.aimusic.navigation.SearchSection
import com.videomaker.aimusic.ui.components.ModifierExtension.clickableSingle
import com.videomaker.aimusic.ui.components.ShimmerPlaceholder
import com.videomaker.aimusic.ui.components.SongListItem
import com.videomaker.aimusic.ui.components.StaggeredGrid
import com.videomaker.aimusic.ui.components.SuggestSongCard
import com.videomaker.aimusic.ui.components.TemplateCard
import com.videomaker.aimusic.ui.components.bottomGradientOverlay
import com.videomaker.aimusic.ui.theme.AppDimens
import com.videomaker.aimusic.ui.theme.Black60
import com.videomaker.aimusic.ui.theme.FoundationBlack_Gray_100
import com.videomaker.aimusic.ui.theme.GoldAccent
import com.videomaker.aimusic.ui.theme.Gray200
import com.videomaker.aimusic.ui.theme.TemplateBadgeBackground
import com.videomaker.aimusic.ui.theme.TextOnPrimary
import com.videomaker.aimusic.ui.theme.TextPrimary
import com.videomaker.aimusic.ui.theme.White12
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun UnifiedSearchResultsContent(
    screenSessionId: String,
    state: UnifiedSearchUiState.Results,
    query: String,
    relatedSearches: List<String>,
    onTemplateClick: (String) -> Unit,
    onSongClick: (MusicSong, String) -> Unit,
    onExplore: (SearchSection) -> Unit,
    onScrollStarted: () -> Unit,
    onRelatedSearchClick: (String) -> Unit
) {
    val dimens = AppDimens.current
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        snapshotFlow { listState.isScrollInProgress }
            .distinctUntilChanged()
            .collect { scrolling ->
                if (scrolling) onScrollStarted()
            }
    }

    // Check actual search results (not suggestions)
    val hasTemplateResults = state.templates.totalCount > 0
    val hasMusicResults = state.music.totalCount > 0

    // Both empty: show empty state or suggestions
    val isTotallyEmpty = !hasTemplateResults && !hasMusicResults

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = dimens.spaceMd)
    ) {

        item {
            Spacer(Modifier.height(100.dp))
        }

        // Related searches - inside scrollable content
        if (relatedSearches.isNotEmpty()) {
            items(
                items = relatedSearches,
                key = { "related_$it" }
            ) { suggestion ->
                RelatedSearchRow(
                    query = query,
                    suggestion = suggestion,
                    onClick = { onRelatedSearchClick(suggestion) }
                )
            }
        }

        // Rule: "Partial empty: show other category if one is empty"
        // - If both empty → show empty state or suggestions
        // - If one empty → show ONLY the category with results (hide empty category completely)
        // - If both have results → show both

        if (isTotallyEmpty) {
            // Both categories are empty - show suggestions or empty state
            val hasAnySuggestions = state.templateEmpty.isNotEmpty() || state.songEmpty.isNotEmpty()

            if (hasAnySuggestions) {
                // Show suggestions for empty categories
                val renderOrder = if (state.initialSection == SearchSection.TEMPLATES) {
                    listOf(SearchSection.TEMPLATES, SearchSection.MUSIC)
                } else {
                    listOf(SearchSection.MUSIC, SearchSection.TEMPLATES)
                }

                renderOrder.forEach { section ->
                    when (section) {
                        SearchSection.TEMPLATES -> {
                            if (state.templateEmpty.isNotEmpty()) {
                                item(key = "templates_suggestions_header") {
                                    UnifiedSectionHeader(text = stringResource(R.string.search_templates_suggestions))
                                    Spacer(modifier = Modifier.height(dimens.spaceSm))
                                }

                                item {
                                    Row(
                                        modifier = Modifier
                                            .horizontalScroll(rememberScrollState())
                                            .padding(horizontal = dimens.spaceLg),
                                        horizontalArrangement = Arrangement.spacedBy(dimens.spaceMd)
                                    ) {
                                        state.templateEmpty.forEach { template ->
                                            TemplateCard(
                                                name = template.name,
                                                thumbnailPath = template.thumbnailPath,
                                                aspectRatio = (9f / 16f),
                                                isPremium = template.isPremium,
                                                useCount = template.useCount,
                                                onClick = {

                                                    Analytics.trackTemplateClick(
                                                        templateId = template.id,
                                                        templateName = template.name,
                                                        location = AnalyticsEvent.Value.Location.SEARCH_RCM
                                                    )
                                                    onTemplateClick(template.id)
                                                          },
                                                modifier = Modifier.width(190.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        SearchSection.MUSIC -> {
                            if (state.songEmpty.isNotEmpty()) {
                                item(key = "music_suggestions_header") {
                                    UnifiedSectionHeader(text = stringResource(R.string.search_music_suggestion))
                                    Spacer(modifier = Modifier.height(dimens.spaceSm))
                                }

                                item {
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = dimens.spaceLg),
                                        horizontalArrangement = Arrangement.spacedBy(dimens.spaceMd)
                                    ) {
                                        items(state.songEmpty, key = { "empty_song_${it.id}" }) { song ->
                                            LaunchedEffect(song.id, screenSessionId) {
                                                Analytics.trackSongImpression(
                                                    songId = song.id.toString(),
                                                    songName = song.name,
                                                    location = AnalyticsEvent.Value.Location.SEARCH_RCM,
                                                    screenSessionId = screenSessionId
                                                )
                                            }
                                            SuggestSongCard(
                                                song = song,
                                                onClick = {
                                                    Analytics.trackSongClick(
                                                        songId = song.id.toString(),
                                                        songName = song.name,
                                                        location = AnalyticsEvent.Value.Location.SEARCH_RCM
                                                    )
                                                    onSongClick(song, AnalyticsEvent.Value.Location.SEARCH_RCM)
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // No suggestions either - show empty state
                item(key = "empty_state") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = dimens.spaceXxl),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Image(
                            painter = painterResource(R.drawable.img_empty_search),
                            contentDescription = null,
                            modifier = Modifier.size(80.dp)
                        )
                        Spacer(modifier = Modifier.height(dimens.spaceMd))
                        Text(
                            text = stringResource(R.string.unified_search_no_results_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.W600,
                            fontSize = 18.sp
                        )
                        Spacer(modifier = Modifier.height(dimens.spaceSm))
                        Text(
                            text = stringResource(R.string.search_empty_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = FoundationBlack_Gray_100,
                            fontWeight = FontWeight.W400,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                            fontSize = 16.sp,
                        )
                        Spacer(modifier = Modifier.height(dimens.spaceLg))
                        Text(
                            text = stringResource(R.string.unified_search_explore_more),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .background(
                                    Color.White.copy(0.08f),
                                    RoundedCornerShape(160.dp)
                                )
                                .clickableSingle(onClick = {
                                    onExplore.invoke(state.initialSection)
                                })
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        )
                    }
                }
            }
        } else {
            // At least one category has results
            // Partial empty rule: show ONLY categories with results
            val renderOrder = if (state.initialSection == SearchSection.TEMPLATES) {
                listOf(SearchSection.TEMPLATES, SearchSection.MUSIC)
            } else {
                listOf(SearchSection.MUSIC, SearchSection.TEMPLATES)
            }

            renderOrder.forEach { section ->
                when (section) {
                    SearchSection.TEMPLATES -> {
                        // Only show templates if they have results
                        if (hasTemplateResults) {
                            item(key = "templates_header") {
                                UnifiedSectionHeader(
                                    text = stringResource(R.string.unified_search_templates),
                                    count = state.templates.totalCount
                                )
                            }

                            item(key = "templates_grid") {
                                UnifiedTemplateGrid(
                                    templates = state.templates.items,
                                    onTemplateClick = onTemplateClick,
                                    modifier = Modifier.padding(horizontal = dimens.spaceLg)
                                )
                            }
                        }
                        // If templates empty and music has results → hide templates completely
                    }

                    SearchSection.MUSIC -> {
                        // Only show music if they have results
                        if (hasMusicResults) {
                            item(key = "music_header") {
                                UnifiedSectionHeader(
                                    text = stringResource(R.string.unified_search_music),
                                    count = state.music.totalCount
                                )
                            }

                            items(
                                items = state.music.songs,
                                key = { "song_${it.id}" }
                            ) { song ->
                                LaunchedEffect(song.id, screenSessionId) {
                                    Analytics.trackSongImpression(
                                        songId = song.id.toString(),
                                        songName = song.name,
                                        location = AnalyticsEvent.Value.Location.SEARCH_RESULT,
                                        screenSessionId = screenSessionId
                                    )
                                }
                                SongListItem(
                                    name = song.name,
                                    artist = song.artist,
                                    coverUrl = song.coverUrl,
                                    onSongClick = {
                                        Analytics.trackSongClick(
                                            songId = song.id.toString(),
                                            songName = song.name,
                                            location = AnalyticsEvent.Value.Location.SEARCH_RESULT
                                        )
                                        onSongClick(song, AnalyticsEvent.Value.Location.SEARCH_RESULT)
                                    }
                                )
                            }
                        }
                        // If music empty and templates have results → hide music completely
                    }
                }
            }
        }
    }
}

@Composable
internal fun UnifiedSectionHeader(text: String, count: Int? = null) {
    val dimens = AppDimens.current
    val displayText = count?.let{"$text ($count)"} ?: text
    Text(
        text = displayText,
        style = MaterialTheme.typography.titleSmall,
        fontSize = 22.sp,
        fontWeight = FontWeight.W600,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(horizontal = dimens.spaceLg, vertical = dimens.spaceSm)
    )
}

@Composable
internal fun UnifiedSeeMore(
    visible: Boolean,
    isLoading: Boolean,
    onClick: () -> Unit
) {
    val dimens = AppDimens.current
    if (!visible) return

    val label = if (isLoading) {
        stringResource(R.string.unified_search_loading_more)
    } else {
        stringResource(R.string.unified_search_see_more)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth(),
        contentAlignment = Alignment.Center
    ){
        Row(
            modifier = Modifier
                .background(Color.White.copy(0.12f), RoundedCornerShape(160.dp))
                .clickableSingle(enabled = !isLoading, onClick = onClick)
                .padding(vertical = 12.dp, horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                color = Color.White,
                fontWeight = FontWeight.W600,
                fontSize = 16.sp
            )
            Icon(
                painter = painterResource(R.drawable.ic_arrow_down),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier
                    .size(20.dp)
            )
        }
    }
}

@Composable
internal fun UnifiedTemplateGrid(
    templates: List<VideoTemplate>,
    onTemplateClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val dimens = AppDimens.current

    // ✅ OPTIMIZED: Pre-calculate aspect ratios once when templates list changes
    val aspectRatios = remember(templates) {
        templates.map { parseAspectRatio(it.aspectRatio) }
    }

    StaggeredGrid(
        items = templates,
        aspectRatios = aspectRatios,
        columns = 3,
        spacing = dimens.spaceSm,
        modifier = modifier,
        key = { it.id }
    ) { template ->
        ScaledTemplateCard(
            template = template,
            onClick = {
                Analytics.trackTemplateClick(
                    templateId = template.id,
                    templateName = template.name,
                    location = AnalyticsEvent.Value.Location.SEARCH_RESULT
                )
                onTemplateClick(template.id)
            }
        )
    }
}

/**
 * Template card with responsive scaling for search results.
 * All elements (text, icons, padding) scale proportionally based on card width.
 * Base reference: 120.dp width (1/3 of ~360dp screen with spacing)
 */
@Composable
private fun ScaledTemplateCard(
    template: VideoTemplate,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val aspectRatio = parseAspectRatio(template.aspectRatio)

    BoxWithConstraints(modifier = modifier) {
        // Calculate scale factor based on actual width
        // Reference width: 120.dp (typical width for 3-column grid on ~360dp screen)
        val referenceWidth = 120.dp
        val scaleFactor = (maxWidth / referenceWidth).coerceIn(0.6f, 1.2f)

        val imageRequest = remember(template.thumbnailPath) {
            ImageRequest.Builder(context)
                .data(template.thumbnailPath)
                .size(Size(200, 350))
                .precision(Precision.INEXACT)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .diskCachePolicy(CachePolicy.ENABLED)
                .crossfade(true)
                .crossfade(200)
                .build()
        }

        Card(
            onClick = onClick,
            modifier = Modifier.aspectRatio(aspectRatio.coerceIn(0.3f, 3f)),
            shape = RoundedCornerShape(8.dp * scaleFactor),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Thumbnail
                if (template.thumbnailPath.isNotEmpty()) {
                    SubcomposeAsyncImage(
                        model = imageRequest,
                        contentDescription = template.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                        loading = {
                            ShimmerPlaceholder(
                                modifier = Modifier.fillMaxSize(),
                                cornerRadius = 0.dp
                            )
                        },
                        error = {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "⚠️",
                                    fontSize = (32 * scaleFactor).sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                                )
                            }
                        },
                        success = { SubcomposeAsyncImageContent() }
                    )
                } else {
                    ShimmerPlaceholder(
                        modifier = Modifier.fillMaxSize(),
                        cornerRadius = 0.dp
                    )
                }

                // Bottom gradient scrim
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .bottomGradientOverlay(listOf(Color.Transparent, Color.Transparent, Black60))
                )

                // PRO badge - top-end
                if (template.isPremium) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding((6 * scaleFactor).dp)
                            .background(color = GoldAccent, shape = RoundedCornerShape((6 * scaleFactor).dp))
                            .padding(horizontal = (6 * scaleFactor).dp, vertical = (3 * scaleFactor).dp)
                    ) {
                        Text(
                            text = stringResource(R.string.search_pro_badge),
                            fontSize = (10 * scaleFactor).sp,
                            fontWeight = FontWeight.Bold,
                            color = TextOnPrimary
                        )
                    }
                }

                // Template name - bottom-start
                Text(
                    text = template.name,
                    fontSize = (12 * scaleFactor).sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(
                            start = (10 * scaleFactor).dp,
                            end = if (template.useCount > 0) (64 * scaleFactor).dp else (10 * scaleFactor).dp,
                            bottom = (10 * scaleFactor).dp
                        )
                )

                // Use count badge - bottom-end
                if (template.useCount > 0) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding((6 * scaleFactor).dp)
                            .background(color = TemplateBadgeBackground, shape = RoundedCornerShape(999.dp))
                            .border(width = 1.dp, color = White12, shape = RoundedCornerShape(999.dp))
                            .padding(horizontal = (8 * scaleFactor).dp, vertical = (5 * scaleFactor).dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy((3 * scaleFactor).dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_heart),
                            contentDescription = null,
                            tint = Gray200,
                            modifier = Modifier.size((10 * scaleFactor).dp)
                        )
                        Text(
                            text = formatUseCount(template.useCount),
                            fontSize = (10 * scaleFactor).sp,
                            color = Gray200,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

private fun parseAspectRatio(aspectRatio: String): Float {
    return try {
        val parts = aspectRatio.split(":")
        if (parts.size == 2) {
            val w = parts[0].toFloatOrNull() ?: 9f
            val h = parts[1].toFloatOrNull() ?: 16f
            w / h
        } else {
            9f / 16f
        }
    } catch (_: Exception) {
        9f / 16f
    }
}

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

/**
 * Shared related search row component (used in both Loading and Results)
 */
@Composable
internal fun RelatedSearchRow(
    query: String,
    suggestion: String,
    onClick: () -> Unit
) {
    val annotatedText = remember(suggestion, query) {
        buildAnnotatedString {
            val startIndex = suggestion.indexOf(query, ignoreCase = true)

            if (suggestion.isNotEmpty() && startIndex >= 0) {
                val endIndex = startIndex + query.length

                append(suggestion)

                // Highlight the matching part
                addStyle(
                    style = SpanStyle(
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    ),
                    start = startIndex,
                    end = endIndex
                )

                // Gray out the rest
                addStyle(
                    style = SpanStyle(
                        color = Color.Gray
                    ),
                    start = endIndex,
                    end = suggestion.length
                )
            } else {
                append(suggestion)
            }
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickableSingle(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 20.dp)
    ) {
        Text(
            text = annotatedText,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Icon(
            painter = painterResource(R.drawable.ic_recommend_search),
            tint = Color.Gray,
            contentDescription = null,
            modifier = Modifier.size(24.dp)
        )
    }
}
