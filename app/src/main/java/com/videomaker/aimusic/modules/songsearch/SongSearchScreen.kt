package com.videomaker.aimusic.modules.songsearch

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import org.koin.compose.koinInject
import com.videomaker.aimusic.R
import com.videomaker.aimusic.domain.model.MusicSong
import com.videomaker.aimusic.domain.model.SongGenre
import com.videomaker.aimusic.media.audio.AudioPreviewCache
import com.videomaker.aimusic.modules.songs.MusicPlayerBottomSheet
import com.videomaker.aimusic.ui.components.AppFilterChip
import com.videomaker.aimusic.ui.components.ModifierExtension.clickableSingle
import com.videomaker.aimusic.ui.components.ProvideShimmerEffect
import com.videomaker.aimusic.ui.components.SongListItem
import com.videomaker.aimusic.ui.components.SongListItemPlaceholder
import com.videomaker.aimusic.ui.theme.AppDimens
import com.videomaker.aimusic.ui.theme.Primary
import com.videomaker.aimusic.ui.theme.SearchFieldBackground
import com.videomaker.aimusic.ui.theme.SearchFieldBorder
import com.videomaker.aimusic.ui.theme.TextSecondary
import com.videomaker.aimusic.ui.theme.TextTertiary

// ============================================
// SONG SEARCH SCREEN
// ============================================

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun SongSearchScreen(
    viewModel: SongSearchViewModel,
    onNavigateToSongDetail: (Long) -> Unit = {},
    onNavigateBack: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val displayText by viewModel.displayText.collectAsStateWithLifecycle()
    val recentSearches by viewModel.recentSearches.collectAsStateWithLifecycle()
    val genres by viewModel.genres.collectAsStateWithLifecycle()
    val suggestedSongs by viewModel.suggestedSongs.collectAsStateWithLifecycle()
    val navigationEvent by viewModel.navigationEvent.collectAsStateWithLifecycle()
    val selectedSong by viewModel.selectedSong.collectAsStateWithLifecycle()
    val keyboardController = LocalSoftwareKeyboardController.current
    val audioPreviewCache: AudioPreviewCache = koinInject()

    LaunchedEffect(navigationEvent) {
        navigationEvent?.let { event ->
            when (event) {
                is SongSearchNavigationEvent.NavigateBack -> onNavigateBack()
                is SongSearchNavigationEvent.NavigateToSongDetail ->
                    onNavigateToSongDetail(event.songId)
            }
            viewModel.onNavigationHandled()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .imePadding()
    ) {
        SongSearchTopBar(
            query = displayText,
            onQueryChange = viewModel::onQueryChange,
            onSearch = viewModel::onSearch,
            onClearQuery = viewModel::onClearQuery,
            onBack = viewModel::onNavigateBack
        )

        when (val state = uiState) {
            is SongSearchUiState.Idle -> SongSearchIdleContent(
                recentSearches = recentSearches,
                genres = genres,
                suggestedSongs = suggestedSongs,
                onRecentClick = viewModel::onRecentSearchClick,
                onRemoveRecent = viewModel::onRemoveRecentSearch,
                onClearAllRecents = viewModel::onClearAllRecents,
                onGenreClick = viewModel::onGenreClick,
                onSongClick = { song ->
                    keyboardController?.hide()
                    viewModel.onSongClick(song)
                }
            )

            is SongSearchUiState.Loading -> SongSearchLoadingContent()

            is SongSearchUiState.Results -> SongSearchResultsContent(
                songs = state.songs,
                onSongClick = { song ->
                    keyboardController?.hide()
                    viewModel.onSongClick(song)
                },
                onScrollStarted = { keyboardController?.hide() }
            )

            is SongSearchUiState.Empty -> SongSearchEmptyContent(
                genres = genres,
                suggestedSongs = suggestedSongs,
                onGenreClick = viewModel::onGenreClick,
                onSongClick = { song ->
                    keyboardController?.hide()
                    viewModel.onSongClick(song)
                }
            )

            is SongSearchUiState.Error -> SongSearchErrorContent(message = state.message)
        }
    }

    selectedSong?.let { song ->
        MusicPlayerBottomSheet(
            song = song,
            cacheDataSourceFactory = audioPreviewCache.cacheDataSourceFactory,
            onDismiss = viewModel::onDismissPlayer,
            onUseToCreate = { viewModel.onUseToCreateVideo() }
        )
    }
}

// ============================================
// SEARCH TOP BAR
// ============================================

@Composable
private fun SongSearchTopBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onClearQuery: () -> Unit,
    onBack: () -> Unit
) {
    val dimens = AppDimens.current
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .padding(
                start = dimens.spaceXs,
                end = dimens.spaceLg,
                top = dimens.spaceSm,
                bottom = dimens.spaceMd
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.back),
                tint = MaterialTheme.colorScheme.onSurface
            )
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .background(color = SearchFieldBackground, shape = RoundedCornerShape(dimens.radiusXl))
                .border(width = 1.dp, color = SearchFieldBorder, shape = RoundedCornerShape(dimens.radiusXl))
                .padding(horizontal = dimens.spaceMd, vertical = dimens.spaceMd)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = TextTertiary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(dimens.spaceSm))
                Box(modifier = Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        Text(
                            text = stringResource(R.string.song_search_hint),
                            style = MaterialTheme.typography.titleSmall,
                            color = TextTertiary
                        )
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        singleLine = true,
                        textStyle = MaterialTheme.typography.titleSmall.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        cursorBrush = SolidColor(Primary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(
                            onSearch = {
                                onSearch()
                                keyboardController?.hide()
                            }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                    )
                }
                if (query.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(dimens.spaceXs))
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.close),
                        tint = TextTertiary,
                        modifier = Modifier
                            .size(20.dp)
                            .clickableSingle { onClearQuery() }
                    )
                }
            }
        }
    }
}

// ============================================
// IDLE CONTENT
// ============================================

@Composable
private fun SongSearchIdleContent(
    recentSearches: List<String>,
    genres: List<SongGenre>,
    suggestedSongs: List<MusicSong>,
    onRecentClick: (String) -> Unit,
    onRemoveRecent: (String) -> Unit,
    onClearAllRecents: () -> Unit,
    onGenreClick: (SongGenre) -> Unit,
    onSongClick: (MusicSong) -> Unit
) {
    val dimens = AppDimens.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = dimens.spaceMd)
    ) {
        if (recentSearches.isNotEmpty()) {
            item(key = "recent_header") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = dimens.spaceLg),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.search_recent),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.search_clear_all),
                        style = MaterialTheme.typography.bodySmall,
                        color = Primary,
                        modifier = Modifier.clickableSingle { onClearAllRecents() }
                    )
                }
                Spacer(modifier = Modifier.height(dimens.spaceSm))
            }
            items(recentSearches, key = { "recent_$it" }) { search ->
                SongSearchRecentItem(
                    query = search,
                    onClick = { onRecentClick(search) },
                    onRemove = { onRemoveRecent(search) }
                )
            }
            item(key = "recent_spacer") { Spacer(modifier = Modifier.height(dimens.spaceXl)) }
        }

        if (genres.isNotEmpty()) {
            item(key = "genre_header") {
                Text(
                    text = stringResource(R.string.song_search_explore_by_genre),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = dimens.spaceLg)
                )
                Spacer(modifier = Modifier.height(dimens.spaceSm))
            }
            item(key = "genre_chips") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = dimens.spaceLg),
                    horizontalArrangement = Arrangement.spacedBy(dimens.spaceSm)
                ) {
                    items(genres, key = { it.id }) { genre ->
                        AppFilterChip(
                            text = genre.displayName,
                            onClick = { onGenreClick(genre) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(dimens.spaceXl))
            }
        }

        if (suggestedSongs.isNotEmpty()) {
            item(key = "suggested_header") {
                Text(
                    text = stringResource(R.string.song_search_suggested),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = dimens.spaceLg)
                )
                Spacer(modifier = Modifier.height(dimens.spaceSm))
            }
            items(suggestedSongs, key = { "suggested_${it.id}" }) { song ->
                SongListItem(
                    name = song.name,
                    artist = song.artist,
                    coverUrl = song.coverUrl,
                    onSongClick = { onSongClick(song) }
                )
            }
        }
    }
}

@Composable
private fun SongSearchRecentItem(
    query: String,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    val dimens = AppDimens.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickableSingle(onClick = onClick)
            .padding(horizontal = dimens.spaceLg, vertical = dimens.spaceMd),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.AccessTime,
            contentDescription = null,
            tint = TextSecondary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(dimens.spaceMd))
        Text(
            text = query,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Icon(
            imageVector = Icons.Default.Close,
            contentDescription = stringResource(R.string.remove),
            tint = TextTertiary,
            modifier = Modifier
                .size(18.dp)
                .clickableSingle { onRemove() }
        )
    }
}

// ============================================
// RESULTS CONTENT
// ============================================

@Composable
private fun SongSearchResultsContent(
    songs: List<MusicSong>,
    onSongClick: (MusicSong) -> Unit,
    onScrollStarted: () -> Unit
) {
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        snapshotFlow { listState.isScrollInProgress }
            .collect { scrolling -> if (scrolling) onScrollStarted() }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize()
    ) {
        items(songs, key = { "song_${it.id}" }, contentType = { "song" }) { song ->
            SongListItem(
                name = song.name,
                artist = song.artist,
                coverUrl = song.coverUrl,
                onSongClick = { onSongClick(song) }
            )
        }
    }
}

// ============================================
// LOADING CONTENT
// ============================================

@Composable
private fun SongSearchLoadingContent() {
    ProvideShimmerEffect {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = false
        ) {
            items(8, key = { it }, contentType = { "song_placeholder" }) {
                SongListItemPlaceholder()
            }
        }
    }
}

// ============================================
// EMPTY CONTENT
// ============================================

@Composable
private fun SongSearchEmptyContent(
    genres: List<SongGenre>,
    suggestedSongs: List<MusicSong>,
    onGenreClick: (SongGenre) -> Unit,
    onSongClick: (MusicSong) -> Unit
) {
    val dimens = AppDimens.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = dimens.spaceMd)
    ) {
        item(key = "empty_message") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = dimens.spaceXxl),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(dimens.spaceMd))
                Text(
                    text = stringResource(R.string.song_search_no_results),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(dimens.spaceSm))
                Text(
                    text = stringResource(R.string.search_no_results_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }
        }

        if (genres.isNotEmpty()) {
            item(key = "empty_genre_header") {
                Text(
                    text = stringResource(R.string.song_search_explore_by_genre),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = dimens.spaceLg)
                )
                Spacer(modifier = Modifier.height(dimens.spaceSm))
            }
            item(key = "empty_genre_chips") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = dimens.spaceLg),
                    horizontalArrangement = Arrangement.spacedBy(dimens.spaceSm)
                ) {
                    items(genres, key = { it.id }) { genre ->
                        AppFilterChip(
                            text = genre.displayName,
                            onClick = { onGenreClick(genre) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(dimens.spaceXl))
            }
        }

        if (suggestedSongs.isNotEmpty()) {
            item(key = "empty_suggested_header") {
                Text(
                    text = stringResource(R.string.song_search_suggested),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = dimens.spaceLg)
                )
                Spacer(modifier = Modifier.height(dimens.spaceSm))
            }
            items(suggestedSongs, key = { "empty_song_${it.id}" }) { song ->
                SongListItem(
                    name = song.name,
                    artist = song.artist,
                    coverUrl = song.coverUrl,
                    onSongClick = { onSongClick(song) }
                )
            }
        }
    }
}

// ============================================
// ERROR CONTENT
// ============================================

@Composable
private fun SongSearchErrorContent(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )
    }
}