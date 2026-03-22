package com.videomaker.aimusic.modules.weeklyranking

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import org.koin.compose.koinInject
import com.videomaker.aimusic.R
import com.videomaker.aimusic.domain.model.MusicSong
import com.videomaker.aimusic.media.audio.AudioPreviewCache
import com.videomaker.aimusic.modules.songs.MusicPlayerBottomSheet
import com.videomaker.aimusic.ui.components.SongListItem
import com.videomaker.aimusic.ui.theme.AppDimens

/**
 * Weekly Ranking List Screen - Paginated ranking browsing
 *
 * Features:
 * - Vertical list layout
 * - Lazy loading: max 20 items per page, 100 items total
 * - Pull-to-refresh
 * - Query-level pagination (no client-side filtering)
 * - Music player bottom sheet on song click
 */
@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeeklyRankingListScreen(
    viewModel: WeeklyRankingListViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToAssetPicker: (Long) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val navigationEvent by viewModel.navigationEvent.collectAsStateWithLifecycle()
    val selectedSong by viewModel.selectedSong.collectAsStateWithLifecycle()
    val audioPreviewCache: AudioPreviewCache = koinInject()

    // Handle navigation events
    LaunchedEffect(navigationEvent) {
        navigationEvent?.let { event ->
            when (event) {
                is WeeklyRankingNavigationEvent.NavigateBack -> onNavigateBack()
                is WeeklyRankingNavigationEvent.NavigateToAssetPicker -> {
                    onNavigateToAssetPicker(event.songId)
                }
            }
            viewModel.onNavigationHandled()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.songs_weekly_ranking)) },
                navigationIcon = {
                    IconButton(onClick = { viewModel.onNavigateBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = Modifier.background(MaterialTheme.colorScheme.background)
    ) { paddingValues ->
        when (val state = uiState) {
            is WeeklyRankingListUiState.Loading -> {
                LoadingContent(modifier = Modifier.padding(paddingValues))
            }
            is WeeklyRankingListUiState.Success -> {
                WeeklyRankingListContent(
                    pageState = state.pageState,
                    onSongClick = { viewModel.onSongClick(it) },
                    onLoadMore = { viewModel.loadMore() },
                    onRefresh = { viewModel.refresh() },
                    modifier = Modifier.padding(paddingValues)
                )
            }
            is WeeklyRankingListUiState.Error -> {
                ErrorContent(
                    message = state.message,
                    modifier = Modifier.padding(paddingValues)
                )
            }
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

@Composable
private fun LoadingContent(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ErrorContent(
    message: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Vertical list with pull-to-refresh and auto-load more
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WeeklyRankingListContent(
    pageState: WeeklyRankingPageState,
    onSongClick: (MusicSong) -> Unit,
    onLoadMore: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dimens = AppDimens.current
    val listState = rememberLazyListState()
    val pullRefreshState = rememberPullToRefreshState()

    // Detect scroll near bottom (6 items threshold)
    val shouldLoadMore by remember(listState, pageState.hasMore, pageState.isLoadingMore) {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            pageState.hasMore && !pageState.isLoadingMore && lastVisibleIndex >= totalItems - 6
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) onLoadMore()
    }

    PullToRefreshBox(
        isRefreshing = pageState.isRefreshing,
        onRefresh = onRefresh,
        state = pullRefreshState,
        modifier = modifier.fillMaxSize()
    ) {
        when {
            pageState.isLoading && pageState.songs.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            pageState.error != null && pageState.songs.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = pageState.error,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                }
            }
            else -> {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(
                        start = dimens.spaceLg,
                        end = dimens.spaceLg,
                        top = dimens.spaceXs,
                        bottom = dimens.spaceLg
                    ),
                    verticalArrangement = Arrangement.spacedBy(dimens.spaceXs),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(
                        items = pageState.songs,
                        key = { it.id },
                        contentType = { "song" }
                    ) { song ->
                        SongListItem(
                            name = song.name,
                            artist = song.artist,
                            coverUrl = song.coverUrl,
                            onSongClick = { onSongClick(song) }
                        )
                    }

                    // Loading more indicator
                    if (pageState.isLoadingMore) {
                        item(key = "loading_more") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = dimens.spaceMd),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(32.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
