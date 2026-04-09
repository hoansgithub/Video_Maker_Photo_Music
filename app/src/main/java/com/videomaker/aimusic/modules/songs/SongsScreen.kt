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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import org.koin.compose.koinInject
import com.videomaker.aimusic.R
import com.videomaker.aimusic.core.analytics.Analytics
import com.videomaker.aimusic.core.analytics.AnalyticsEvent
import com.videomaker.aimusic.media.audio.AudioPreviewCache
import com.videomaker.aimusic.domain.model.MusicSong
import com.videomaker.aimusic.domain.model.SongGenre
import com.videomaker.aimusic.ui.components.AppAsyncImage
import com.videomaker.aimusic.ui.components.AppFilterChip
import com.videomaker.aimusic.ui.components.ProvideShimmerEffect
import com.videomaker.aimusic.ui.components.RankingSongCard
import com.videomaker.aimusic.ui.components.RankingSongCardPlaceholder
import com.videomaker.aimusic.ui.components.SectionHeader
import com.videomaker.aimusic.ui.components.ShimmerBox
import com.videomaker.aimusic.ui.components.SongListItem
import com.videomaker.aimusic.ui.components.SongListItemPlaceholder
import com.videomaker.aimusic.ui.components.SongsSearchField
import com.videomaker.aimusic.ui.components.SuggestSongCard
import com.videomaker.aimusic.ui.components.SuggestSongCardPlaceholder
import com.videomaker.aimusic.ui.theme.AppDimens
import com.videomaker.aimusic.ui.theme.Black40
import com.videomaker.aimusic.ui.theme.GoldAccent
import com.videomaker.aimusic.ui.theme.Gray400
import com.videomaker.aimusic.ui.theme.PlaceholderBackground
import com.videomaker.aimusic.ui.theme.Primary
import com.videomaker.aimusic.ui.theme.TextBright
import com.videomaker.aimusic.ui.theme.TextPrimary
import com.videomaker.aimusic.ui.theme.TextSecondary
import com.videomaker.aimusic.ui.theme.TextTertiary
import com.videomaker.aimusic.ui.theme.VideoMakerTheme
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.distinctUntilChanged

// ============================================
// SONGS SCREEN
// ============================================

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun SongsScreen(
    viewModel: SongsViewModel,
    topBarHeight: Dp = 0.dp,
    onNavigateToAssetPicker: (songId: Long) -> Unit = {},
    onNavigateToSuggestedAll: () -> Unit = {},
    onNavigateToWeeklyRankingList: () -> Unit = {},
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
    val audioPreviewCache: AudioPreviewCache = koinInject()
    val screenSessionId = remember { Analytics.newScreenSessionId() }
    var selectedSongLocation by rememberSaveable {
        mutableStateOf(AnalyticsEvent.Value.Location.SONG_PREVIEW)
    }

    // ✅ FIX: Refresh data when locale changes (genres will be localized in future)
    // Use rememberSaveable to persist previousLocale across Activity recreation
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]?.toLanguageTag()
    var previousLocale by rememberSaveable { mutableStateOf(locale) }
    LaunchedEffect(locale) {
        // Only refresh on locale CHANGE, not initial composition
        if (locale != null && locale != previousLocale && previousLocale != null) {
            viewModel.refresh()
        }
        previousLocale = locale
    }

    LaunchedEffect(navigationEvent) {
        navigationEvent?.let { event ->
            when (event) {
                is SongsNavigationEvent.NavigateToAssetPickerForSong -> onNavigateToAssetPicker(event.songId)
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
                screenSessionId = screenSessionId,
                suggestedState = suggestedState,
                rankingState = rankingState,
                stationState = stationState,
                genresState = genresState,
                selectedGenre = selectedGenre,
                onGenreSelected = { genreId ->
                    val genreName = when (val currentGenres = genresState) {
                        is SectionState.Success -> {
                            currentGenres.data.firstOrNull { it.id == genreId }?.displayName
                                ?: AnalyticsEvent.Value.ALL
                        }
                        else -> AnalyticsEvent.Value.ALL
                    }
                    Analytics.trackSongGenreClick(
                        genreId = genreId ?: AnalyticsEvent.Value.ALL,
                        genreName = genreName,
                        location = AnalyticsEvent.Value.Location.SONG
                    )
                    viewModel.onGenreSelected(genreId)
                },
                isRefreshing = isRefreshing,
                onRefresh = viewModel::refresh,
                onSongClick = { song, location ->
                    selectedSongLocation = location
                    viewModel.onSongClick(song)
                },
                onSeeMoreSuggested = viewModel::onSeeMoreSuggestedClick,
                onNavigateToWeeklyRankingList = onNavigateToWeeklyRankingList,
                onSearchClick = onNavigateToSearch
            )
        }
    }

    // Music player bottom sheet — shown when a song is tapped
    selectedSong?.let { song ->
        MusicPlayerBottomSheet(
            song = song,
            cacheDataSourceFactory = audioPreviewCache.cacheDataSourceFactory,
            location = selectedSongLocation,
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
    screenSessionId: String,
    suggestedState: SectionState<List<MusicSong>>,
    rankingState: SectionState<List<MusicSong>>,
    stationState: SectionState<List<MusicSong>>,
    genresState: SectionState<List<SongGenre>>,
    selectedGenre: String?,
    onGenreSelected: (String?) -> Unit,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onSongClick: (MusicSong, String) -> Unit,
    onSeeMoreSuggested: () -> Unit,
    onNavigateToWeeklyRankingList: () -> Unit,
    onSearchClick: () -> Unit
) {
    val dimens = AppDimens.current
    val listState = rememberLazyListState()
    var lastTrackedLocation by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .drop(1)
            .collect { (firstVisibleIndex, _) ->
                val location = when {
                    firstVisibleIndex <= 2 -> AnalyticsEvent.Value.Location.SONG_FORYOU
                    firstVisibleIndex <= 4 -> AnalyticsEvent.Value.Location.SONG_RANKING
                    else -> AnalyticsEvent.Value.Location.SONG_STATIONS
                }
                if (lastTrackedLocation != location) {
                    Analytics.trackSongTabSwipe(location)
                    lastTrackedLocation = location
                }
            }
    }

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
                    onSongClick = onSongClick,
                    screenSessionId = screenSessionId
                )
                Spacer(modifier = Modifier.height(dimens.spaceMd))
            }

            // Section 3: Weekly Ranking
            item(key = "ranking_header", contentType = "section_header") {
                SectionHeader(
                    title = stringResource(R.string.songs_weekly_ranking),
                    icon = Icons.Default.LocalFireDepartment,
                    iconTint = GoldAccent,
                    onSeeAllClick = onNavigateToWeeklyRankingList
                )
                Spacer(modifier = Modifier.height(dimens.spaceSm))
            }

            item(key = "ranking_pager", contentType = "pager") {
                WeeklyRankingSection(
                    state = rankingState,
                    onSongClick = onSongClick,
                    screenSessionId = screenSessionId
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
                    onSongClick = onSongClick,
                    screenSessionId = screenSessionId
                )
            }
        }
    }
}

// ============================================
// SEARCH FIELD
// ============================================


// ============================================
// SUGGEST SONGS LIST (horizontal, ratio 162:207)
// Shows shimmer placeholders while data loads.
// ============================================

private const val SUGGEST_PLACEHOLDER_COUNT = 5

@Composable
private fun SuggestSongsList(
    state: SectionState<List<MusicSong>>,
    onSongClick: (MusicSong, String) -> Unit,
    screenSessionId: String,
    modifier: Modifier = Modifier
) {
    val dimens = AppDimens.current
    val horizontalScrollState = rememberScrollState()
    var hasTrackedSwipe by remember { mutableStateOf(false) }

    LaunchedEffect(horizontalScrollState) {
        snapshotFlow { horizontalScrollState.value }
            .distinctUntilChanged()
            .drop(1)
            .collect { value ->
                if (value > 0 && !hasTrackedSwipe) {
                    Analytics.trackSongTabSwipe(AnalyticsEvent.Value.Location.SONG_FORYOU)
                    hasTrackedSwipe = true
                } else if (value == 0) {
                    hasTrackedSwipe = false
                }
            }
    }

    Row(
        modifier = modifier
            .horizontalScroll(horizontalScrollState)
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
                    LaunchedEffect(song.id, screenSessionId) {
                        Analytics.trackSongImpression(
                            songId = song.id.toString(),
                            songName = song.name,
                            location = AnalyticsEvent.Value.Location.SONG_FORYOU,
                            screenSessionId = screenSessionId
                        )
                    }
                    SuggestSongCard(
                        song = song,
                        onClick = {
                            Analytics.trackSongClick(
                                songId = song.id.toString(),
                                songName = song.name,
                                location = AnalyticsEvent.Value.Location.SONG_FORYOU
                            )
                            onSongClick(song, AnalyticsEvent.Value.Location.SONG_FORYOU)
                        }
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

// ============================================
// WEEKLY RANKING SECTION (pager or shimmer)
// ============================================

private const val RANKING_PLACEHOLDER_COUNT = 3

@Composable
private fun WeeklyRankingSection(
    state: SectionState<List<MusicSong>>,
    onSongClick: (MusicSong, String) -> Unit,
    screenSessionId: String,
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
                    RankingSongCardPlaceholder()
                }
            }
        }
        is SectionState.Success -> {
            WeeklyRankingPager(
                songs = state.data,
                onSongClick = onSongClick,
                screenSessionId = screenSessionId,
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

@Composable
private fun WeeklyRankingPager(
    songs: List<MusicSong>,
    onSongClick: (MusicSong, String) -> Unit,
    screenSessionId: String,
    modifier: Modifier = Modifier
) {
    val dimens = AppDimens.current
    val pages = remember(songs) { songs.take(9).chunked(3) }
    val pagerState = rememberPagerState(pageCount = { pages.size })

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .drop(1)
            .collect {
                Analytics.trackSongTabSwipe(AnalyticsEvent.Value.Location.SONG_RANKING)
            }
    }

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
                LaunchedEffect(song.id, screenSessionId) {
                    Analytics.trackSongImpression(
                        songId = song.id.toString(),
                        songName = song.name,
                        location = AnalyticsEvent.Value.Location.SONG_RANKING,
                        screenSessionId = screenSessionId
                    )
                }
                RankingSongCard(
                    song = song,
                    ranking = pageIndex * 3 + index + 1,
                    onClick = {
                        Analytics.trackSongClick(
                            songId = song.id.toString(),
                            songName = song.name,
                            location = AnalyticsEvent.Value.Location.SONG_RANKING
                        )
                        onSongClick(song, AnalyticsEvent.Value.Location.SONG_RANKING)
                    }
                )
            }
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
    onSongClick: (MusicSong, String) -> Unit,
    screenSessionId: String,
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
                        screenSessionId = screenSessionId,
                        onSongClick = {
                            Analytics.trackSongClick(
                                songId = song.id.toString(),
                                songName = song.name,
                                location = AnalyticsEvent.Value.Location.SONG_STATIONS
                            )
                            onSongClick(song, AnalyticsEvent.Value.Location.SONG_STATIONS)
                        },
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
    screenSessionId: String,
    onSongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    LaunchedEffect(song.id, screenSessionId) {
        Analytics.trackSongImpression(
            songId = song.id.toString(),
            songName = song.name,
            location = AnalyticsEvent.Value.Location.SONG_STATIONS,
            screenSessionId = screenSessionId
        )
    }
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
                    screenSessionId = "preview",
                    suggestedState = SectionState.Success(previewSongs),
                    rankingState = SectionState.Success(previewSongs.take(9)),
                    stationState = SectionState.Success(previewSongs),
                    genresState = SectionState.Success(previewGenres),
                    selectedGenre = null,
                    onGenreSelected = {},
                    isRefreshing = false,
                    onRefresh = {},
                    onSongClick = { _, _ -> },
                    onSeeMoreSuggested = {},
                    onNavigateToWeeklyRankingList = {},
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
                    screenSessionId = "preview",
                    suggestedState = SectionState.Loading,
                    rankingState = SectionState.Loading,
                    stationState = SectionState.Loading,
                    genresState = SectionState.Loading,
                    selectedGenre = null,
                    onGenreSelected = {},
                    isRefreshing = false,
                    onRefresh = {},
                    onSongClick = { _, _ -> },
                    onSeeMoreSuggested = {},
                    onNavigateToWeeklyRankingList = {},
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
                onSongClick = { _, _ -> },
                screenSessionId = "preview"
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
                    onSongClick = { _, _ -> },
                    screenSessionId = "preview"
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
                StationSongItem(song = previewSongs[0], screenSessionId = "preview", onSongClick = {})
                StationSongItem(song = previewSongs[1], screenSessionId = "preview", onSongClick = {})
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
