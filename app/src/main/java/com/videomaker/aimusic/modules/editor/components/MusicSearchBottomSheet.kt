package com.videomaker.aimusic.modules.editor.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.alcheclub.lib.acccore.ads.loader.AdsLoaderService
import com.videomaker.aimusic.core.ads.RewardedAdPresenter
import com.videomaker.aimusic.core.analytics.Analytics
import com.videomaker.aimusic.core.analytics.AnalyticsEvent
import com.videomaker.aimusic.core.constants.AdPlacement
import com.videomaker.aimusic.core.storage.UnlockedSongsManager
import com.videomaker.aimusic.modules.export.WatchAdDialog
import com.videomaker.aimusic.R
import com.videomaker.aimusic.domain.model.MusicSong
import com.videomaker.aimusic.domain.model.SongGenre
import com.videomaker.aimusic.modules.songsearch.SongSearchUiState
import com.videomaker.aimusic.modules.songsearch.SongSearchViewModel
import com.videomaker.aimusic.ui.components.AdBadge
import com.videomaker.aimusic.ui.components.AdBadgeStyle
import com.videomaker.aimusic.ui.components.AdsLoadingOverlay
import com.videomaker.aimusic.ui.components.AppFilterChip
import com.videomaker.aimusic.ui.components.SongListItem
import com.videomaker.aimusic.media.audio.AudioPreviewCache
import com.videomaker.aimusic.media.audio.MusicPreviewManager
import com.videomaker.aimusic.ui.components.ModifierExtension.clickableSingle
import com.videomaker.aimusic.ui.theme.AppDimens
import org.koin.compose.koinInject
import com.videomaker.aimusic.ui.theme.SearchFieldBackground
import com.videomaker.aimusic.ui.theme.SearchFieldBorder
import com.videomaker.aimusic.ui.theme.SplashBackground
import com.videomaker.aimusic.ui.theme.TextPrimary
import com.videomaker.aimusic.ui.theme.TextSecondary
import com.videomaker.aimusic.ui.theme.TextTertiary
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// ============================================
// HELPER - Release player async to avoid ANR
// ============================================

/**
 * Release ExoPlayer asynchronously on background thread to avoid ANR.
 * ExoPlayer.release() can sometimes block for several seconds.
 */
private fun ExoPlayer.releaseAsync() {
    val playerToRelease = this
    ProcessLifecycleOwner.get().lifecycleScope.launch(Dispatchers.IO) {
        runCatching {
            android.util.Log.d("MusicSearch", "Releasing player on background thread...")
            playerToRelease.release()
            android.util.Log.d("MusicSearch", "Player released successfully")
        }.onFailure { e ->
            android.util.Log.e("MusicSearch", "Failed to release player", e)
        }
    }
}

/**
 * Music Search Bottom Sheet (Full Screen)
 * Allows searching and selecting music tracks
 */
@OptIn(ExperimentalMaterial3Api::class)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
internal fun MusicSearchBottomSheet(
    viewModel: SongSearchViewModel,
    onSongClick: (MusicSong) -> Unit,
    onSongSelected: (MusicSong) -> Unit,
    onDismiss: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val displayText by viewModel.displayText.collectAsStateWithLifecycle()
    val genres by viewModel.genres.collectAsStateWithLifecycle()
    val suggestedSongs by viewModel.suggestedSongs.collectAsStateWithLifecycle()

    // Global music preview state - shared across the whole app
    val previewingSongId by MusicPreviewManager.previewingSongId.collectAsStateWithLifecycle()
    val selectedForConfirmId by MusicPreviewManager.selectedForConfirmId.collectAsStateWithLifecycle()
    val isLoadingPreview by MusicPreviewManager.isLoadingPreview.collectAsStateWithLifecycle()

    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    val dimens = AppDimens.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val audioCache: AudioPreviewCache = koinInject()
    val unlockedSongsManager: UnlockedSongsManager = koinInject()
    val adsLoaderService: AdsLoaderService = koinInject()
    val screenSessionId = remember { Analytics.newScreenSessionId() }

    // State for watch ad flow
    var showWatchAdDialog by remember { mutableStateOf(false) }
    var shouldPresentAd by remember { mutableStateOf(false) }
    var pendingSongToUnlock by remember { mutableStateOf<MusicSong?>(null) }

    // Helper function to check if song is locked
    fun isSongLocked(song: MusicSong): Boolean {
        return song.isPremium && !unlockedSongsManager.isUnlocked(song.id)
    }

    // Handle confirm button click
    fun onConfirmClick() {
        val selectedId = MusicPreviewManager.getSelectedId() ?: return
        val song = when (val state = uiState) {
            is SongSearchUiState.Results -> state.songs.find { it.id == selectedId }
            else -> suggestedSongs.find { it.id == selectedId }
        } ?: return

        if (isSongLocked(song)) {
            // Song is locked - show watch ad dialog
            pendingSongToUnlock = song
            showWatchAdDialog = true
        } else {
            // Song is unlocked or free - directly select
            MusicPreviewManager.clearPreviewState()
            onSongSelected(song)
        }
    }

    // Watch ad dialog handlers
    fun onWatchAdDialogDismiss() {
        showWatchAdDialog = false
        pendingSongToUnlock = null
    }

    fun onWatchAdConfirmed() {
        showWatchAdDialog = false
        shouldPresentAd = true
    }

    fun onRewardEarned() {
        val song = pendingSongToUnlock ?: return
        coroutineScope.launch {
            unlockedSongsManager.unlockSong(song.id)
            pendingSongToUnlock = null
            shouldPresentAd = false
            MusicPreviewManager.clearPreviewState()
            onSongSelected(song)
        }
    }

    fun onAdFailed() {
        pendingSongToUnlock = null
        shouldPresentAd = false
    }

    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true
    )

    // ExoPlayer for preview with cache
    val exoPlayer = remember {
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(
                androidx.media3.exoplayer.source.DefaultMediaSourceFactory(
                    audioCache.cacheDataSourceFactory
                )
            )
            .build()
            .apply {
                repeatMode = Player.REPEAT_MODE_OFF
            }
    }

    // Cleanup ExoPlayer
    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.stop()  // ✅ Already stopping immediately (good!)
            exoPlayer.releaseAsync() // Release async to avoid ANR
        }
    }

    // Pause music preview when ad is about to show
    LaunchedEffect(shouldPresentAd) {
        if (shouldPresentAd && exoPlayer.isPlaying) {
            exoPlayer.pause()
            android.util.Log.d("MusicSearchBottomSheet", "Paused music preview for ad presentation")
        }
    }

    // Handle preview playback
    LaunchedEffect(previewingSongId) {
        var currentListener: Player.Listener? = null

        try {
            if (previewingSongId != null) {
                val state = uiState
                val song = when (state) {
                    is SongSearchUiState.Results -> state.songs.find { it.id == previewingSongId }
                    else -> suggestedSongs.find { it.id == previewingSongId }
                }

                song?.let {
                    try {
                        exoPlayer.stop()
                        exoPlayer.setMediaItem(MediaItem.fromUri(it.mp3Url))
                        exoPlayer.prepare()
                        exoPlayer.playWhenReady = true

                        // Listen for when player is ready
                        val listener = object : Player.Listener {
                            override fun onPlaybackStateChanged(playbackState: Int) {
                                if (playbackState == Player.STATE_READY) {
                                    MusicPreviewManager.onPreviewPrepared()
                                }
                            }

                            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                                if (!playWhenReady) {
                                    MusicPreviewManager.stopPreview()
                                }
                            }

                            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                                MusicPreviewManager.stopPreview()
                            }
                        }
                        currentListener = listener
                        exoPlayer.addListener(listener)
                    } catch (e: Exception) {
                        MusicPreviewManager.stopPreview()
                    }
                }
            } else {
                exoPlayer.stop()
            }
        } finally {
            // Remove listener when effect is cancelled or re-triggered
            currentListener?.let { exoPlayer.removeListener(it) }
        }
    }

    // Dismiss keyboard when sheet is being dragged
    LaunchedEffect(sheetState.currentValue) {
        focusManager.clearFocus()
    }

    ModalBottomSheet(
        onDismissRequest = {
            MusicPreviewManager.clearPreviewState()
            onDismiss()
        },
        sheetState = sheetState,
        containerColor = SplashBackground,
        dragHandle = null,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        modifier = Modifier.fillMaxHeight() // Full screen height, covers all content including tabs
    ) {
        // Box wrapper to allow overlay to cover entire bottom sheet area
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
                .padding(top = 24.dp, bottom = 32.dp)
        ) {
            // Title with close and confirm buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.song_search_change_music),
                    color = TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )

                // Close button (left)
                IconButton(onClick = {
                    MusicPreviewManager.clearPreviewState()
                    onDismiss()
                }) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.close),
                        tint = TextSecondary
                    )
                }

                // Confirm button (right) - only visible when song is selected
                if (selectedForConfirmId != null) {
                    Spacer(modifier = Modifier.width(8.dp))

                    // Check if selected song is locked
                    val selectedSong = remember(selectedForConfirmId, uiState, suggestedSongs) {
                        val selectedId = selectedForConfirmId
                        when (val state = uiState) {
                            is SongSearchUiState.Results -> state.songs.find { it.id == selectedId }
                            else -> suggestedSongs.find { it.id == selectedId }
                        }
                    }
                    val isLocked = selectedSong?.let { isSongLocked(it) } ?: false

                    if (isLocked) {
                        // Locked song - show "Done" button with ad badge
                        Box(
                            modifier = Modifier
                                .height(40.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(MaterialTheme.colorScheme.primary)
                                .clickableSingle { onConfirmClick() }
                                .padding(horizontal = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Done,
                                    contentDescription = stringResource(R.string.confirm),
                                    tint = SplashBackground,
                                    modifier = Modifier.size(20.dp)
                                )
                                AdBadge(style = AdBadgeStyle.Small(textColor = SplashBackground))
                            }
                        }
                    } else {
                        // Unlocked song - show checkmark button
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                                .clickableSingle { onConfirmClick() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = stringResource(R.string.confirm),
                                tint = SplashBackground,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Search field - matching SongSearchTopBar style
            Box(
                modifier = Modifier
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
                        if (displayText.isEmpty()) {
                            Text(
                                text = stringResource(R.string.song_search_hint),
                                style = MaterialTheme.typography.titleSmall,
                                color = TextTertiary
                            )
                        }
                        BasicTextField(
                            value = displayText,
                            onValueChange = viewModel::onQueryChange,
                            singleLine = true,
                            textStyle = MaterialTheme.typography.titleSmall.copy(
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(
                                onSearch = {
                                    viewModel.onSearch()
                                    keyboardController?.hide()
                                }
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester)
                        )
                    }
                    if (displayText.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(dimens.spaceXs))
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.close),
                            tint = TextTertiary,
                            modifier = Modifier
                                .size(20.dp)
                                .clickableSingle { viewModel.onClearQuery() }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Results
            when (val state = uiState) {
                is SongSearchUiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }

                is SongSearchUiState.Results -> {
                    val listState = rememberLazyListState()

                    // Aggressive keyboard dismissal on ANY scroll movement
                    LaunchedEffect(listState) {
                        var previousIndex = listState.firstVisibleItemIndex
                        var previousOffset = listState.firstVisibleItemScrollOffset

                        snapshotFlow {
                            listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
                        }.collect { (index, offset) ->
                            if (index != previousIndex || kotlin.math.abs(offset - previousOffset) > 0) {
                                focusManager.clearFocus()
                            }
                            previousIndex = index
                            previousOffset = offset
                        }
                    }

                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(state.songs, key = { "song_${it.id}" }) { song ->
                            LaunchedEffect(song.id, screenSessionId) {
                                Analytics.trackSongImpression(
                                    songId = song.id.toString(),
                                    songName = song.name,
                                    location = AnalyticsEvent.Value.Location.VIDEO_EDITOR_SEARCH,
                                    screenSessionId = screenSessionId
                                )
                            }
                            SongListItem(
                                name = song.name,
                                artist = song.artist,
                                coverUrl = song.coverUrl,
                                isPlaying = song.id == previewingSongId,
                                isSelected = song.id == selectedForConfirmId,
                                isLoading = song.id == selectedForConfirmId && isLoadingPreview,
                                onSongClick = {
                                    onSongClick(song)
                                    Analytics.trackSongClick(
                                        songId = song.id.toString(),
                                        songName = song.name,
                                        location = AnalyticsEvent.Value.Location.VIDEO_EDITOR_SEARCH
                                    )
                                    focusManager.clearFocus()
                                    keyboardController?.hide()
                                    MusicPreviewManager.togglePreview(song.id)
                                }
                            )
                        }
                    }
                }

                is SongSearchUiState.Empty -> {
                    val listState = rememberLazyListState()

                    // Aggressive keyboard dismissal on ANY scroll movement
                    LaunchedEffect(listState) {
                        var previousIndex = listState.firstVisibleItemIndex
                        var previousOffset = listState.firstVisibleItemScrollOffset

                        snapshotFlow {
                            listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
                        }.collect { (index, offset) ->
                            if (index != previousIndex || kotlin.math.abs(offset - previousOffset) > 0) {
                                focusManager.clearFocus()
                            }
                            previousIndex = index
                            previousOffset = offset
                        }
                    }

                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = dimens.spaceMd)
                    ) {
                        // Empty message
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

                        // Genres section
                        if (genres.isNotEmpty()) {
                            item(key = "empty_genre_header") {
                                Text(
                                    text = stringResource(R.string.song_search_explore_by_genre),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(horizontal = dimens.spaceMd)
                                )
                                Spacer(modifier = Modifier.height(dimens.spaceSm))
                            }
                            item(key = "empty_genre_chips") {
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = dimens.spaceMd),
                                    horizontalArrangement = Arrangement.spacedBy(dimens.spaceSm)
                                ) {
                                    items(genres, key = { it.id }) { genre ->
                                        AppFilterChip(
                                            text = genre.displayName,
                                            onClick = { viewModel.onGenreClick(genre) }
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(dimens.spaceXl))
                            }
                        }

                        // Suggested songs section
                        if (suggestedSongs.isNotEmpty()) {
                            item(key = "empty_suggested_header") {
                                Text(
                                    text = stringResource(R.string.song_search_suggested),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(horizontal = dimens.spaceMd)
                                )
                                Spacer(modifier = Modifier.height(dimens.spaceSm))
                            }
                            items(suggestedSongs, key = { "empty_song_${it.id}" }) { song ->
                                LaunchedEffect(song.id, screenSessionId) {
                                    Analytics.trackSongImpression(
                                        songId = song.id.toString(),
                                        songName = song.name,
                                        location = AnalyticsEvent.Value.Location.VIDEO_EDITOR_RCM,
                                        screenSessionId = screenSessionId
                                    )
                                }
                                SongListItem(
                                    name = song.name,
                                    artist = song.artist,
                                    coverUrl = song.coverUrl,
                                    isPlaying = song.id == previewingSongId,
                                    isSelected = song.id == selectedForConfirmId,
                                    isLoading = song.id == selectedForConfirmId && isLoadingPreview,
                                    onSongClick = {
                                        onSongClick(song)
                                        Analytics.trackSongClick(
                                            songId = song.id.toString(),
                                            songName = song.name,
                                            location = AnalyticsEvent.Value.Location.VIDEO_EDITOR_RCM
                                        )
                                        focusManager.clearFocus()
                                        keyboardController?.hide()
                                        MusicPreviewManager.togglePreview(song.id)
                                    }
                                )
                            }
                        }
                    }
                }

                is SongSearchUiState.Error -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = state.message,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 16.sp
                        )
                    }
                }

                is SongSearchUiState.Idle -> {
                    val listState = rememberLazyListState()

                    // Aggressive keyboard dismissal on ANY scroll movement
                    LaunchedEffect(listState) {
                        var previousIndex = listState.firstVisibleItemIndex
                        var previousOffset = listState.firstVisibleItemScrollOffset

                        snapshotFlow {
                            listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
                        }.collect { (index, offset) ->
                            if (index != previousIndex || kotlin.math.abs(offset - previousOffset) > 0) {
                                focusManager.clearFocus()
                            }
                            previousIndex = index
                            previousOffset = offset
                        }
                    }

                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = dimens.spaceMd)
                    ) {
                        // Genres section
                        if (genres.isNotEmpty()) {
                            item(key = "genre_header") {
                                Text(
                                    text = stringResource(R.string.song_search_explore_by_genre),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(horizontal = dimens.spaceMd)
                                )
                                Spacer(modifier = Modifier.height(dimens.spaceSm))
                            }
                            item(key = "genre_chips") {
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = dimens.spaceMd),
                                    horizontalArrangement = Arrangement.spacedBy(dimens.spaceSm)
                                ) {
                                    items(genres, key = { it.id }) { genre ->
                                        AppFilterChip(
                                            text = genre.displayName,
                                            onClick = { viewModel.onGenreClick(genre) }
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(dimens.spaceXl))
                            }
                        }

                        // Suggested songs section
                        if (suggestedSongs.isNotEmpty()) {
                            item(key = "suggested_header") {
                                Text(
                                    text = stringResource(R.string.song_search_suggested),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(horizontal = dimens.spaceMd)
                                )
                                Spacer(modifier = Modifier.height(dimens.spaceSm))
                            }
                            items(suggestedSongs, key = { "suggested_${it.id}" }) { song ->
                                LaunchedEffect(song.id, screenSessionId) {
                                    Analytics.trackSongImpression(
                                        songId = song.id.toString(),
                                        songName = song.name,
                                        location = AnalyticsEvent.Value.Location.VIDEO_EDITOR_RCM,
                                        screenSessionId = screenSessionId
                                    )
                                }
                                SongListItem(
                                    name = song.name,
                                    artist = song.artist,
                                    coverUrl = song.coverUrl,
                                    isPlaying = song.id == previewingSongId,
                                    isSelected = song.id == selectedForConfirmId,
                                    isLoading = song.id == selectedForConfirmId && isLoadingPreview,
                                    onSongClick = {
                                        onSongClick(song)
                                        Analytics.trackSongClick(
                                            songId = song.id.toString(),
                                            songName = song.name,
                                            location = AnalyticsEvent.Value.Location.VIDEO_EDITOR_RCM
                                        )
                                        focusManager.clearFocus()
                                        keyboardController?.hide()
                                        MusicPreviewManager.togglePreview(song.id)
                                    }
                                )
                            }
                        }
                    }
                }
            }
            }  // End Column

            // Ads loading overlay - covers entire bottom sheet area (inside same window)
            AdsLoadingOverlay()
        }  // End Box
    }  // End ModalBottomSheet

    // Watch ad dialog for song unlock
    if (showWatchAdDialog) {
        WatchAdDialog(
            title = stringResource(R.string.song_watch_ad_title),
            subtitle = stringResource(R.string.song_watch_ad_subtitle),
            onDismiss = ::onWatchAdDialogDismiss,
            onWatchAd = ::onWatchAdConfirmed
        )
    }

    // Handle rewarded ad presentation
    RewardedAdPresenter(
        shouldPresent = shouldPresentAd,
        placement = AdPlacement.REWARD_UNLOCK_SONG,
        adsLoaderService = adsLoaderService,
        onRewardEarned = ::onRewardEarned,
        onAdFailed = ::onAdFailed
    )

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}
