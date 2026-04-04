package com.videomaker.aimusic.modules.unifiedsearch.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.videomaker.aimusic.R
import com.videomaker.aimusic.domain.model.MusicSong
import com.videomaker.aimusic.domain.model.VideoTemplate
import com.videomaker.aimusic.modules.unifiedsearch.MusicSectionState
import com.videomaker.aimusic.modules.unifiedsearch.TemplateSectionState
import com.videomaker.aimusic.modules.unifiedsearch.UnifiedSearchUiState
import com.videomaker.aimusic.navigation.SearchSection
import com.videomaker.aimusic.ui.components.SongListItem
import com.videomaker.aimusic.ui.components.SuggestSongCard
import com.videomaker.aimusic.ui.components.TemplateCard
import com.videomaker.aimusic.ui.theme.AppDimens
import com.videomaker.aimusic.ui.theme.FoundationBlack_Gray_100
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun UnifiedSearchResultsContent(
    state: UnifiedSearchUiState.Results,
    onTemplateClick: (String) -> Unit,
    onSongClick: (MusicSong) -> Unit,
    onSeeMoreTemplates: () -> Unit,
    onSeeMoreMusic: () -> Unit,
    onExplore: (SearchSection) -> Unit,
    onScrollStarted: () -> Unit
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

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = dimens.spaceMd)
    ) {

        item {
            Spacer(Modifier.height(100.dp))
        }

        val renderOrder = if (state.initialSection == SearchSection.TEMPLATES) {
            listOf(SearchSection.TEMPLATES, SearchSection.MUSIC)
        } else {
            listOf(SearchSection.MUSIC, SearchSection.TEMPLATES)
        }

        renderOrder.forEach { section ->
            when (section) {
                SearchSection.TEMPLATES -> {
                    if (state.templateEmpty.isNotEmpty()){
                        item(key = "templates_suggestions_header") {
                            UnifiedSectionHeader(
                                text = "Template Suggestions"
                            )
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
                                        onClick = { onTemplateClick(template.id) },
                                        modifier = Modifier.width(190.dp)
                                    )
                                }
                            }
                        }

                    } else {
                        item(key = "templates_header") {
                            UnifiedSectionHeader(
                                text = stringResource(R.string.unified_search_templates),
                                count = state.templates.totalCount
                            )
                        }

                        if (state.templates.totalCount == 0) {
                            item(key = "empty_templates") {
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
                                        text = "Let’s try something else or explore\ntrending vibes\u2028\u2028",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = FoundationBlack_Gray_100,
                                        fontWeight = FontWeight.W400,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier
                                            .fillMaxWidth(),
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
                                            .clickable(onClick = { onExplore.invoke(SearchSection.TEMPLATES) })
                                            .padding(horizontal = 16.dp, vertical = 12.dp)
                                    )
                                }
                            }
                        } else {
                            item(key = "templates_grid") {
                                UnifiedTemplateGrid(
                                    templates = state.templates.items,
                                    onTemplateClick = onTemplateClick,
                                    modifier = Modifier.padding(horizontal = dimens.spaceLg)
                                )
                            }
                            item(key = "templates_see_more") {
                                UnifiedSeeMore(
                                    visible = state.templates.hasMore,
                                    isLoading = state.templates.isLoadingMore,
                                    onClick = onSeeMoreTemplates
                                )
                            }
                        }
                    }
                }

                SearchSection.MUSIC -> {

                    if (state.songEmpty.isNotEmpty()){
                        item(key = "music_suggestions_header") {
                            UnifiedSectionHeader(
                                text = "Music Suggestions"
                            )
                            Spacer(modifier = Modifier.height(dimens.spaceSm))
                        }

                        item {
                            Row(
                                modifier = Modifier
                                    .horizontalScroll(rememberScrollState())
                                    .padding(horizontal = dimens.spaceLg),
                                horizontalArrangement = Arrangement.spacedBy(dimens.spaceMd)
                            ) {
                                state.songEmpty.forEach { song ->
                                    SuggestSongCard(
                                        song = song,
                                        onClick = { onSongClick(song) }
                                    )
                                }
                            }
                        }

                    }
                    else {

                        item(key = "music_header") {
                            UnifiedSectionHeader(
                                text = stringResource(R.string.unified_search_music),
                                count = state.music.totalCount
                            )
                        }

                        if (state.music.totalCount == 0) {
                            item(key = "empty_music") {
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
                                        text = "Let’s try something else or explore\ntrending vibes\u2028\u2028",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = FoundationBlack_Gray_100,
                                        fontWeight = FontWeight.W400,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier
                                            .fillMaxWidth(),
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
                                            .clickable(onClick = { onExplore.invoke(SearchSection.MUSIC) })
                                            .padding(horizontal = 16.dp, vertical = 12.dp)
                                    )
                                }
                            }
                        } else {
                            items(
                                items = state.music.songs,
                                key = { "song_${it.id}" }
                            ) { song ->
                                SongListItem(
                                    name = song.name,
                                    artist = song.artist,
                                    coverUrl = song.coverUrl,
                                    onSongClick = { onSongClick(song) }
                                )
                            }
                            item(key = "music_see_more") {
                                UnifiedSeeMore(
                                    visible = state.music.hasMore,
                                    isLoading = state.music.isLoadingMore,
                                    onClick = onSeeMoreMusic
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun UnifiedTemplateSection(
    title: String,
    state: TemplateSectionState,
    onTemplateClick: (String) -> Unit,
    onSeeMore: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dimens = AppDimens.current
    if (state.items.isEmpty()) return

    Column(modifier = modifier.fillMaxWidth()) {
        UnifiedSectionHeader(text = title)
        UnifiedTemplateGrid(
            templates = state.items,
            onTemplateClick = onTemplateClick,
            modifier = Modifier.padding(horizontal = dimens.spaceLg)
        )
        UnifiedSeeMore(
            visible = state.hasMore,
            isLoading = state.isLoadingMore,
            onClick = onSeeMore
        )
    }
}

@Composable
internal fun UnifiedMusicSection(
    title: String,
    state: MusicSectionState,
    onSongClick: (MusicSong) -> Unit,
    onSeeMore: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (state.songs.isEmpty()) return

    Column(modifier = modifier.fillMaxWidth()) {
        UnifiedSectionHeader(text = title)
        state.songs.forEach { song ->
            SongListItem(
                name = song.name,
                artist = song.artist,
                coverUrl = song.coverUrl,
                onSongClick = { onSongClick(song) }
            )
        }
        UnifiedSeeMore(
            visible = state.hasMore,
            isLoading = state.isLoadingMore,
            onClick = onSeeMore
        )
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
                .clickable(enabled = !isLoading, onClick = onClick)
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
    val rows = templates.chunked(3)

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(dimens.spaceSm)
    ) {
        rows.forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(dimens.spaceSm)
            ) {
                rowItems.forEach { template ->
                    TemplateCard(
                        name = template.name,
                        thumbnailPath = template.thumbnailPath,
                        aspectRatio = parseAspectRatio(template.aspectRatio),
                        isPremium = template.isPremium,
                        useCount = template.useCount,
                        onClick = { onTemplateClick(template.id) },
                        modifier = Modifier.weight(1f)
                    )
                }
                if (rowItems.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
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
