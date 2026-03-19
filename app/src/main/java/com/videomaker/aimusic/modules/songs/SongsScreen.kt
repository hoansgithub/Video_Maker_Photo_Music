package com.videomaker.aimusic.modules.songs

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import co.alcheclub.lib.acccore.di.ACCDI
import com.videomaker.aimusic.R
import com.videomaker.aimusic.media.audio.AudioPreviewCache
import com.videomaker.aimusic.domain.model.MusicSong
import com.videomaker.aimusic.domain.model.SongGenre
import com.videomaker.aimusic.ui.components.AppAsyncImage
import com.videomaker.aimusic.ui.components.AppFilterChip
import com.videomaker.aimusic.ui.components.ProvideShimmerEffect
import com.videomaker.aimusic.ui.components.SectionHeader
import com.videomaker.aimusic.ui.components.ShimmerBox
import com.videomaker.aimusic.ui.components.SongListItem
import com.videomaker.aimusic.ui.components.SongListItemPlaceholder
import com.videomaker.aimusic.ui.theme.AppDimens
import com.videomaker.aimusic.ui.theme.Black24
import com.videomaker.aimusic.ui.theme.Black40
import com.videomaker.aimusic.ui.theme.GoldAccent
import com.videomaker.aimusic.ui.theme.PlaceholderBackground
import com.videomaker.aimusic.ui.theme.Primary
import com.videomaker.aimusic.ui.theme.SearchFieldBackground
import com.videomaker.aimusic.ui.theme.SearchFieldBorder
import com.videomaker.aimusic.ui.theme.TextBright
import com.videomaker.aimusic.ui.theme.TextPrimary
import com.videomaker.aimusic.ui.theme.TextSecondary
import com.videomaker.aimusic.ui.theme.TextTertiary
import com.videomaker.aimusic.ui.theme.VideoMakerTheme

// ============================================
// SONGS SCREEN
// ============================================

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun SongsScreen(
    viewModel: SongsViewModel,
    topBarHeight: Dp = 0.dp,
    onNavigateToSongDetail: (Long) -> Unit = {},
    onNavigateToSuggestedAll: () -> Unit = {},
    onNavigateToSearch: () -> Unit = {}
) {

    val suggestedState by viewModel.suggestedState.collectAsStateWithLifecycle()
    val rankingState by viewModel.rankingState.collectAsStateWithLifecycle()
    val stationState by viewModel.stationState.collectAsStateWithLifecycle()
    val genresState by viewModel.genresState.collectAsStateWithLifecycle()
    val selectedGenre by viewModel.selectedGenre.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val navigationEvent by viewModel.navigationEvent.collectAsStateWithLifecycle()
    val selectedSong by viewModel.selectedSong.collectAsStateWithLifecycle()
    val audioPreviewCache = remember { ACCDI.get<AudioPreviewCache>() }

    LaunchedEffect(navigationEvent) {
        navigationEvent?.let { event ->
            when (event) {
                is SongsNavigationEvent.NavigateToSongDetail -> onNavigateToSongDetail(event.songId)
                is SongsNavigationEvent.NavigateToSuggestedAll -> onNavigateToSuggestedAll()
            }
            viewModel.onNavigationHandled()
        }
    }

    // Single shimmer animation instance shared by all placeholders in this screen
    ProvideShimmerEffect {
        Box(modifier = Modifier.fillMaxSize()) {
            // Background image
            Image(
                painter = painterResource(id = R.drawable.bg_home),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            SongsContent(
                topBarHeight = topBarHeight,
                suggestedState = suggestedState,
                rankingState = rankingState,
                stationState = stationState,
                genresState = genresState,
                selectedGenre = selectedGenre,
                onGenreSelected = viewModel::onGenreSelected,
                isRefreshing = isRefreshing,
                onRefresh = viewModel::refresh,
                onSongClick = viewModel::onSongClick,
                onSeeMoreSuggested = viewModel::onSeeMoreSuggestedClick,
                onSearchClick = onNavigateToSearch
            )
        }
    }

    // Music player bottom sheet — shown when a song is tapped
    selectedSong?.let { song ->
        MusicPlayerBottomSheet(
            song = song,
            cacheDataSourceFactory = audioPreviewCache.cacheDataSourceFactory,
            onDismiss = viewModel::onDismissPlayer,
            onUseToCreate = { viewModel.onUseToCreateVideo(song) }
        )
    }
}

// ============================================
// SONGS CONTENT
// ============================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SongsContent(
    topBarHeight: Dp = 0.dp,
    suggestedState: SectionState<List<MusicSong>>,
    rankingState: SectionState<List<MusicSong>>,
    stationState: SectionState<List<MusicSong>>,
    genresState: SectionState<List<SongGenre>>,
    selectedGenre: String?,
    onGenreSelected: (String?) -> Unit,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onSongClick: (MusicSong) -> Unit,
    onSeeMoreSuggested: () -> Unit,
    onSearchClick: () -> Unit
) {
    val dimens = AppDimens.current

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = topBarHeight + dimens.spaceLg,
                bottom = dimens.space3Xl + dimens.space2Xl
            )
        ) {
            // Section 1: Search Field
            item(key = "search", contentType = "search") {
                SongsSearchField(
                    onClick = onSearchClick,
                    hint = stringResource(R.string.songs_search_hint),
                    modifier = Modifier.padding(
                        horizontal = dimens.spaceLg,
                        vertical = dimens.spaceMd
                    )
                )
            }

            // Section 2: Suggested For You
            item(key = "suggest_header", contentType = "section_header") {
                SectionHeader(
                    title = stringResource(R.string.songs_suggest_for_you),
                    icon = Icons.Default.Favorite,
                    iconTint = Primary,
                    onSeeAllClick = onSeeMoreSuggested
                )
                Spacer(modifier = Modifier.height(dimens.spaceSm))
            }

            item(key = "suggest_list", contentType = "horizontal_list") {
                SuggestSongsList(
                    state = suggestedState,
                    onSongClick = onSongClick
                )
                Spacer(modifier = Modifier.height(dimens.spaceMd))
            }

            // Section 3: Weekly Ranking
            item(key = "ranking_header", contentType = "section_header") {
                SectionHeader(
                    title = stringResource(R.string.songs_weekly_ranking),
                    icon = Icons.Default.LocalFireDepartment,
                    iconTint = GoldAccent,
                    onSeeAllClick = { /* TODO: Navigate to all rankings */ }
                )
                Spacer(modifier = Modifier.height(dimens.spaceSm))
            }

            item(key = "ranking_pager", contentType = "pager") {
                WeeklyRankingSection(
                    state = rankingState,
                    onSongClick = onSongClick
                )
                Spacer(modifier = Modifier.height(dimens.spaceMd))
            }

            // Section 4: Stations with tag filters
            item(key = "station_header", contentType = "section_header") {
                SectionHeader(
                    title = stringResource(R.string.songs_station),
                    icon = Icons.Default.Radio,
                    iconTint = MaterialTheme.colorScheme.surface
                )
                Spacer(modifier = Modifier.height(dimens.spaceSm))
            }

            item(key = "station_tags", contentType = "tags") {
                GenreTagChipRow(
                    state = genresState,
                    selectedGenre = selectedGenre,
                    onGenreSelected = onGenreSelected,
                    modifier = Modifier.padding(bottom = dimens.spaceSm)
                )
            }

            item(key = "station_songs", contentType = "station_songs") {
                StationSongsSection(
                    state = stationState,
                    onSongClick = onSongClick
                )
            }
        }
    }
}

// ============================================
// SEARCH FIELD
// ============================================

@Composable
private fun SongsSearchField(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    hint: String = "Search songs"
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
            painter = painterResource(id = R.drawable.ic_music_note),
            contentDescription = null,
            tint = Color.Unspecified,
            modifier = Modifier.size(24.dp)
        )

        Spacer(modifier = Modifier.width(dimens.spaceSm))

        Text(
            text = hint,
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
// SUGGEST SONGS LIST (horizontal, ratio 162:207)
// Shows shimmer placeholders while data loads.
// ============================================

private const val SUGGEST_PLACEHOLDER_COUNT = 5

@Composable
private fun SuggestSongsList(
    state: SectionState<List<MusicSong>>,
    onSongClick: (MusicSong) -> Unit,
    modifier: Modifier = Modifier
) {
    val dimens = AppDimens.current

    Row(
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = dimens.spaceLg),
        horizontalArrangement = Arrangement.spacedBy(dimens.spaceMd)
    ) {
        when (state) {
            is SectionState.Loading -> {
                repeat(SUGGEST_PLACEHOLDER_COUNT) {
                    SuggestSongCardPlaceholder()
                }
            }
            is SectionState.Success -> {
                state.data.forEach { song ->
                    SuggestSongCard(
                        song = song,
                        onClick = { onSongClick(song) }
                    )
                }
            }
            is SectionState.Error -> {
                // Inline error — keep the row visible but show a message
                Box(
                    modifier = Modifier
                        .width(200.dp)
                        .height(60.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.error_load_failed),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

/** Shimmer skeleton that matches the real [SuggestSongCard] layout (162dp wide, 1:1 thumb + info). */
@Composable
private fun SuggestSongCardPlaceholder() {
    val dimens = AppDimens.current

    Column(
        modifier = Modifier
            .width(162.dp)
            .clip(RoundedCornerShape(dimens.radiusLg))
            .background(PlaceholderBackground)
    ) {
        // 1:1 thumbnail shimmer
        ShimmerBox(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(16.dp))
        )
        // Title row + artist shimmer
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = dimens.spaceSm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(dimens.spaceXxs)
            ) {
                ShimmerBox(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(15.dp)
                        .clip(RoundedCornerShape(4.dp))
                )
                ShimmerBox(
                    modifier = Modifier
                        .fillMaxWidth(0.6f)
                        .height(13.dp)
                        .clip(RoundedCornerShape(4.dp))
                )
            }
            Spacer(modifier = Modifier.width(dimens.spaceXs))
            ShimmerBox(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(4.dp))
            )
        }
    }
}

@Composable
private fun SuggestSongCard(
    song: MusicSong,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dimens = AppDimens.current

    Card(
        onClick = onClick,
        modifier = modifier
            .width(162.dp),
        shape = RoundedCornerShape(dimens.radiusLg),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Column {
            // Cover image — 1:1, 16dp corner radius
            AppAsyncImage(
                imageUrl = song.coverUrl,
                contentDescription = song.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(16.dp))
            )

            // Song info: [name + artist column] | [start-project button]
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = dimens.spaceSm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = song.name,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Medium,
                            fontSize = 15.sp
                        ),
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(dimens.spaceXxs))
                    Text(
                        text = song.artist,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.Normal,
                            fontSize = 13.sp
                        ),
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.width(dimens.spaceXs))
                Icon(
                    painter = painterResource(R.drawable.ic_start_project),
                    contentDescription = stringResource(R.string.start_project),
                    tint = Color.Unspecified,
                    modifier = Modifier
                        .size(36.dp)
                        .clickable(onClick = onClick)
                )
            }
        }
    }
}

// ============================================
// WEEKLY RANKING SECTION (pager or shimmer)
// ============================================

private const val RANKING_PLACEHOLDER_COUNT = 3

@Composable
private fun WeeklyRankingSection(
    state: SectionState<List<MusicSong>>,
    onSongClick: (MusicSong) -> Unit,
    modifier: Modifier = Modifier
) {
    val dimens = AppDimens.current

    when (state) {
        is SectionState.Loading -> {
            Column(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(horizontal = dimens.spaceLg),
                verticalArrangement = Arrangement.spacedBy(dimens.spaceSm)
            ) {
                repeat(RANKING_PLACEHOLDER_COUNT) {
                    RankingSongItemPlaceholder()
                }
            }
        }
        is SectionState.Success -> {
            WeeklyRankingPager(
                songs = state.data,
                onSongClick = onSongClick,
                modifier = modifier
            )
        }
        is SectionState.Error -> {
            Box(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(horizontal = dimens.spaceLg)
                    .height(60.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.error_load_failed),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

/** Shimmer skeleton matching [RankingSongItem] dimensions. */
@Composable
private fun RankingSongItemPlaceholder() {
    val dimens = AppDimens.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(dimens.spaceSm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Thumbnail
        ShimmerBox(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(dimens.radiusMd))
        )
        Spacer(modifier = Modifier.width(dimens.spaceSm))
        // Rank number box
        ShimmerBox(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(dimens.radiusMd))
        )
        Spacer(modifier = Modifier.width(dimens.spaceSm))
        // Name + usage count lines
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(dimens.spaceXxs)
        ) {
            ShimmerBox(
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .height(15.dp)
                    .clip(RoundedCornerShape(4.dp))
            )
            ShimmerBox(
                modifier = Modifier
                    .fillMaxWidth(0.4f)
                    .height(13.dp)
                    .clip(RoundedCornerShape(4.dp))
            )
        }
        Spacer(modifier = Modifier.width(dimens.spaceXs))
        // Start project button
        ShimmerBox(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(4.dp))
        )
    }
}

@Composable
private fun WeeklyRankingPager(
    songs: List<MusicSong>,
    onSongClick: (MusicSong) -> Unit,
    modifier: Modifier = Modifier
) {
    val dimens = AppDimens.current
    val pages = remember(songs) { songs.take(9).chunked(3) }
    val pagerState = rememberPagerState(pageCount = { pages.size })

    // contentPadding peek: shows ~half of the next page on the right
    HorizontalPager(
        state = pagerState,
        contentPadding = PaddingValues(start = dimens.spaceLg, end = 48.dp),
        pageSpacing = dimens.spaceMd,
        modifier = modifier.fillMaxWidth()
    ) { pageIndex ->
        Column(
            verticalArrangement = Arrangement.spacedBy(dimens.spaceSm)
        ) {
            pages.getOrNull(pageIndex)?.forEachIndexed { index, song ->
                RankingSongItem(
                    song = song,
                    ranking = pageIndex * 3 + index + 1,
                    onClick = { onSongClick(song) }
                )
            }
        }
    }
}

@Composable
private fun RankingSongItem(
    song: MusicSong,
    ranking: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dimens = AppDimens.current

    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(dimens.radiusLg),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(dimens.spaceSm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail
            AppAsyncImage(
                imageUrl = song.coverUrl,
                contentDescription = song.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(dimens.radiusMd))
            )

            Spacer(modifier = Modifier.width(dimens.spaceSm))

            // Ranking number
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(
                        color = if (ranking <= 3) GoldAccent else Black24,
                        shape = RoundedCornerShape(dimens.radiusMd)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = ranking.toString(),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = if (ranking <= 3) Color.Black else TextBright
                )
            }

            Spacer(modifier = Modifier.width(dimens.spaceSm))

            // Song name + usage count
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.name,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Medium,
                        fontSize = 15.sp
                    ),
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(dimens.spaceXxs))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(R.drawable.ic_repeat),
                        contentDescription = null,
                        tint = Color.Unspecified,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(dimens.spaceXxs))
                    Text(
                        text = song.formattedUsageCount,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                        color = TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.width(dimens.spaceXs))

            Icon(
                painter = painterResource(R.drawable.ic_start_project),
                contentDescription = stringResource(R.string.start_project),
                tint = Color.Unspecified,
                modifier = Modifier
                    .size(36.dp)
                    .clickable(onClick = onClick)
            )
        }
    }
}

// ============================================
// STATION SONGS SECTION (vertical list or shimmer)
// ============================================

private const val STATION_PLACEHOLDER_COUNT = 5

@Composable
private fun StationSongsSection(
    state: SectionState<List<MusicSong>>,
    onSongClick: (MusicSong) -> Unit,
    modifier: Modifier = Modifier
) {
    val dimens = AppDimens.current

    when (state) {
        is SectionState.Loading -> {
            Column(modifier = modifier) {
                repeat(STATION_PLACEHOLDER_COUNT) {
                    StationSongItemPlaceholder(
                        modifier = Modifier.padding(
                            horizontal = dimens.spaceLg,
                            vertical = dimens.spaceXs
                        )
                    )
                }
            }
        }
        is SectionState.Success -> {
            Column(modifier = modifier) {
                state.data.forEach { song ->
                    StationSongItem(
                        song = song,
                        onSongClick = { onSongClick(song) },
                        modifier = Modifier.padding(
                            horizontal = dimens.spaceLg,
                            vertical = dimens.spaceXs
                        )
                    )
                }
            }
        }
        is SectionState.Error -> {
            Box(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(horizontal = dimens.spaceLg)
                    .height(60.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.error_load_failed),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

/** Shimmer skeleton matching [StationSongItem] dimensions. */
@Composable
private fun StationSongItemPlaceholder(modifier: Modifier = Modifier) {
    SongListItemPlaceholder(modifier = modifier)
}

@Composable
private fun StationSongItem(
    song: MusicSong,
    onSongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    SongListItem(
        name = song.name,
        artist = song.artist,
        coverUrl = song.coverUrl,
        onSongClick = onSongClick,
        modifier = modifier
    )
}

// ============================================
// GENRE TAG CHIP ROW — dynamic, driven by VM state
// ============================================

@Composable
private fun GenreTagChipRow(
    state: SectionState<List<SongGenre>>,
    selectedGenre: String?,
    onGenreSelected: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val dimens = AppDimens.current

    when (state) {
        is SectionState.Loading -> {
            // Shimmer placeholder chips
            Row(
                modifier = modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = dimens.spaceLg),
                horizontalArrangement = Arrangement.spacedBy(dimens.spaceSm)
            ) {
                repeat(5) {
                    ShimmerBox(
                        modifier = Modifier
                            .width(64.dp)
                            .height(32.dp)
                            .clip(RoundedCornerShape(999.dp))
                    )
                }
            }
        }

        is SectionState.Success -> {
            if (state.data.isEmpty()) return

            Row(
                modifier = modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = dimens.spaceLg),
                horizontalArrangement = Arrangement.spacedBy(dimens.spaceSm)
            ) {
                // "All" chip — maps to null selection
                AppFilterChip(
                    text = stringResource(R.string.settings_all),
                    isSelected = selectedGenre == null,
                    onClick = { onGenreSelected(null) }
                )
                state.data.forEach { genre ->
                    AppFilterChip(
                        text = genre.displayName,
                        isSelected = selectedGenre == genre.id,
                        onClick = { onGenreSelected(genre.id) }
                    )
                }
            }
        }

        is SectionState.Error -> {
            // Genre load failure is non-critical — show nothing, station songs still visible
        }
    }
}


// ============================================
// SAMPLE DATA
// ============================================

private val previewGenres = listOf(
    SongGenre("pop", "Pop"),
    SongGenre("rock", "Rock"),
    SongGenre("jazz", "Jazz"),
    SongGenre("classical", "Classical"),
    SongGenre("hip-hop", "Hip Hop"),
    SongGenre("electronic", "Electronic")
)

private val previewSongs = listOf(
    MusicSong(1L,  "Blinding Lights",  "The Weeknd",     durationMs = 200000, usageCount = 1850000),
    MusicSong(2L,  "Levitating",       "Dua Lipa",       durationMs = 203000, usageCount = 1240000),
    MusicSong(3L,  "Save Your Tears",  "The Weeknd",     durationMs = 215000, usageCount =  980000),
    MusicSong(4L,  "Peaches",          "Justin Bieber",  durationMs = 198000, usageCount =  630000),
    MusicSong(5L,  "Good 4 U",         "Olivia Rodrigo", durationMs = 178000, usageCount =  412000),
    MusicSong(6L,  "Montero",          "Lil Nas X",      durationMs = 137000, usageCount =  287500),
    MusicSong(7L,  "Stay",             "The Kid LAROI",  durationMs = 141000, usageCount =  175000),
    MusicSong(8L,  "Heat Waves",       "Glass Animals",  durationMs = 239000, usageCount =   98300),
    MusicSong(9L,  "Shivers",          "Ed Sheeran",     durationMs = 207000, usageCount =   54200),
    MusicSong(10L, "Industry Baby",    "Lil Nas X",      durationMs = 212000, usageCount =   12500)
)

// ============================================
// PREVIEW
// ============================================

@Preview(name = "Songs Screen – All Loaded", widthDp = 375, heightDp = 812)
@Composable
private fun SongsContentLoadedPreview() {
    VideoMakerTheme {
        ProvideShimmerEffect {
            Box(modifier = Modifier.fillMaxSize()) {
                Image(
                    painter = painterResource(id = R.drawable.bg_home),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                SongsContent(
                    topBarHeight = 56.dp,
                    suggestedState = SectionState.Success(previewSongs),
                    rankingState = SectionState.Success(previewSongs.take(9)),
                    stationState = SectionState.Success(previewSongs),
                    genresState = SectionState.Success(previewGenres),
                    selectedGenre = null,
                    onGenreSelected = {},
                    isRefreshing = false,
                    onRefresh = {},
                    onSongClick = {},
                    onSeeMoreSuggested = {},
                    onSearchClick = {}
                )
            }
        }
    }
}

@Preview(name = "Songs Screen – All Loading", widthDp = 375, heightDp = 812)
@Composable
private fun SongsContentLoadingPreview() {
    VideoMakerTheme {
        ProvideShimmerEffect {
            Box(modifier = Modifier.fillMaxSize()) {
                Image(
                    painter = painterResource(id = R.drawable.bg_home),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                SongsContent(
                    topBarHeight = 56.dp,
                    suggestedState = SectionState.Loading,
                    rankingState = SectionState.Loading,
                    stationState = SectionState.Loading,
                    genresState = SectionState.Loading,
                    selectedGenre = null,
                    onGenreSelected = {},
                    isRefreshing = false,
                    onRefresh = {},
                    onSongClick = {},
                    onSeeMoreSuggested = {},
                    onSearchClick = {}
                )
            }
        }
    }
}

@Preview(name = "Suggest Song Card", widthDp = 162)
@Composable
private fun SuggestSongCardPreview() {
    VideoMakerTheme {
        SuggestSongCard(song = previewSongs[0], onClick = {})
    }
}

@Preview(name = "Suggest Song Card – Placeholder", widthDp = 162)
@Composable
private fun SuggestSongCardPlaceholderPreview() {
    VideoMakerTheme {
        ProvideShimmerEffect {
            SuggestSongCardPlaceholder()
        }
    }
}

@Preview(name = "Ranking Pager – Page 1", widthDp = 375)
@Composable
private fun RankingPagerPage1Preview() {
    VideoMakerTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            WeeklyRankingSection(
                state = SectionState.Success(previewSongs.take(9)),
                onSongClick = {}
            )
        }
    }
}

@Preview(name = "Ranking Pager – Loading", widthDp = 375)
@Composable
private fun RankingPagerLoadingPreview() {
    VideoMakerTheme {
        ProvideShimmerEffect {
            Surface(color = MaterialTheme.colorScheme.background) {
                WeeklyRankingSection(
                    state = SectionState.Loading,
                    onSongClick = {}
                )
            }
        }
    }
}

@Preview(name = "Station Song Item", widthDp = 375)
@Composable
private fun StationSongItemPreview() {
    VideoMakerTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StationSongItem(song = previewSongs[0], onSongClick = {})
                StationSongItem(song = previewSongs[1], onSongClick = {})
            }
        }
    }
}

@Preview(name = "Songs Search Field", widthDp = 375)
@Composable
private fun SongsSearchFieldPreview() {
    VideoMakerTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            SongsSearchField(onClick = {}, modifier = Modifier.padding(16.dp))
        }
    }
}
