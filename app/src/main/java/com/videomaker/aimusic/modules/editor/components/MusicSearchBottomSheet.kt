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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
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
import co.alcheclub.lib.acccore.ads.compose.NativeAdView
import co.alcheclub.lib.acccore.ads.loader.AdsLoaderService
import com.videomaker.aimusic.BuildConfig
import com.videomaker.aimusic.core.ads.AdClickDetector
import com.videomaker.aimusic.core.ads.RewardedAdPresenter
import com.videomaker.aimusic.core.analytics.Analytics
import com.videomaker.aimusic.core.analytics.AnalyticsEvent
import com.videomaker.aimusic.core.analytics.onFirstVisible
import com.videomaker.aimusic.core.analytics.trackSongImpressionAndMark
import com.videomaker.aimusic.core.playback.MusicPlaybackSessionManager
import com.videomaker.aimusic.core.constants.AdPlacement
import com.videomaker.aimusic.core.storage.UnlockedSongsManager
import com.videomaker.aimusic.R
import com.videomaker.aimusic.domain.model.MusicSong
import com.videomaker.aimusic.domain.model.SongGenre
import com.videomaker.aimusic.modules.songsearch.SongSearchUiState
import com.videomaker.aimusic.modules.songsearch.SongSearchViewModel
import com.videomaker.aimusic.ui.components.AdBadge
import com.videomaker.aimusic.ui.components.AdBadgeStyle
import com.videomaker.aimusic.ui.components.AdsLoadingOverlay
import com.videomaker.aimusic.ui.components.AppFilterChip
import com.videomaker.aimusic.ui.components.SongFeedItem
import com.videomaker.aimusic.ui.components.SongListItem
import com.videomaker.aimusic.ui.components.buildSongFeedWithAds
import com.videomaker.aimusic.ui.components.DEFAULT_INFEED_INTERVAL
import com.videomaker.aimusic.ui.components.songFeedItemKey
import com.videomaker.aimusic.media.audio.AudioPreviewCache
import com.videomaker.aimusic.media.audio.MusicPreviewManager
import com.videomaker.aimusic.ui.components.ModifierExtension.clickableSingle
import com.videomaker.aimusic.ui.theme.AppDimens
import org.koin.compose.koinInject
import com.videomaker.aimusic.ui.theme.SearchFieldBackground
import com.videomaker.aimusic.ui.theme.SearchFieldBorder
import com.videomaker.aimusic.ui.theme.SplashBackground
import com.videomaker.aimusic.ui.theme.TextMuted
import com.videomaker.aimusic.ui.theme.TextPrimary
import com.videomaker.aimusic.ui.theme.TextSecondary
import com.videomaker.aimusic.ui.theme.TextTertiary
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
    val selectedGenre by viewModel.selectedGenre.collectAsStateWithLifecycle()
    val suggestedSongsLoading by viewModel.suggestedSongsLoading.collectAsStateWithLifecycle()
    val suggestedLoadingMore by viewModel.suggestedLoadingMore.collectAsStateWithLifecycle()

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
    val adClickDetector: AdClickDetector = koinInject()
    val sessionManager: MusicPlaybackSessionManager = koinInject()
    val screenSessionId = remember { Analytics.newScreenSessionId() }

    // Read infeed ad interval from placement config
    val infeedInterval = remember {
        val config = adsLoaderService.getPlacementConfig(AdPlacement.NATIVE_EDITOR_MUSIC_INFEED)
        val value = config?.extras?.get("infeed_interval")
        value?.toString()?.trim('"')?.toIntOrNull() ?: DEFAULT_INFEED_INTERVAL
    }

    // State for rewarded ad flow
    var shouldPresentAd by remember { mutableStateOf(false) }
    var pendingSongToUnlock by remember { mutableStateOf<MusicSong?>(null) }
    var selectedSongLocation by rememberSaveable {
        mutableStateOf(AnalyticsEvent.Value.Location.VIDEO_EDITOR_RCM)
    }

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
            // Song is locked - present rewarded ad directly
            pendingSongToUnlock = song
            shouldPresentAd = true
        } else {
            // Song is unlocked or free - directly select
            MusicPreviewManager.clearPreviewState()
            onSongSelected(song)
            Analytics.trackSongSelect(
                songId = song.id.toString(),
                songName = song.name,
                location = selectedSongLocation,
                isPremium = song.isPremium
            )
        }
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

    // Pause/resume when Activity loses focus (AOA, interstitial ads)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        var wasPlayingBeforeActivityPause = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    wasPlayingBeforeActivityPause = exoPlayer.isPlaying
                    if (exoPlayer.isPlaying) exoPlayer.pause()
                }
                Lifecycle.Event.ON_RESUME -> {
                    if (wasPlayingBeforeActivityPause) exoPlayer.play()
                    wasPlayingBeforeActivityPause = false
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
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

    val sheetHeight = (LocalConfiguration.current.screenHeightDp * 2 / 3).dp

    ModalBottomSheet(
        onDismissRequest = {
            MusicPreviewManager.clearPreviewState()
            onDismiss()
        },
        sheetState = sheetState,
        containerColor = SplashBackground,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        // Box wrapper to allow overlay to cover entire bottom sheet area
        Box(modifier = Modifier.height(sheetHeight)) {
            Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
        ) {
            // Title row with centered title and confirm button on right
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                // Centered title
                Text(
                    text = stringResource(R.string.song_search_change_music),
                    color = TextMuted,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 18.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )

                // Confirm button - right aligned, only visible when song is selected
                if (selectedForConfirmId != null) {
                    val selectedSong = remember(selectedForConfirmId, uiState, suggestedSongs) {
                        val selectedId = selectedForConfirmId
                        when (val state = uiState) {
                            is SongSearchUiState.Results -> state.songs.find { it.id == selectedId }
                            else -> suggestedSongs.find { it.id == selectedId }
                        }
                    }
                    val isLocked = selectedSong?.let { isSongLocked(it) } ?: false

                    Box(modifier = Modifier.align(Alignment.CenterEnd)) {
                        if (isLocked) {
                            // Locked song - show "Done" button with ad badge
                            Box(
                                modifier = Modifier
                                    .height(36.dp)
                                    .clip(RoundedCornerShape(18.dp))
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
                                        modifier = Modifier.size(18.dp)
                                    )
                                    AdBadge(style = AdBadgeStyle.Small(textColor = SplashBackground))
                                }
                            }
                        } else {
                            // Unlocked song - show checkmark button
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                                    .clickableSingle { onConfirmClick() },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = stringResource(R.string.confirm),
                                    tint = SplashBackground,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
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
                            SongListItem(
                                name = song.name,
                                artist = song.artist,
                                coverUrl = song.coverUrl,
                                isPlaying = song.id == previewingSongId,
                                isSelected = song.id == selectedForConfirmId,
                                isLoading = song.id == selectedForConfirmId && isLoadingPreview,
                                onSongClick = {
                                    onSongClick(song)
                                    selectedSongLocation = AnalyticsEvent.Value.Location.VIDEO_EDITOR_SEARCH
                                    Analytics.trackSongClick(
                                        songId = song.id.toString(),
                                        songName = song.name,
                                        location = AnalyticsEvent.Value.Location.VIDEO_EDITOR_SEARCH,
                                        isPremium = song.isPremium
                                    )
                                    Analytics.trackSongPreview(
                                        songId = song.id.toString(),
                                        songName = song.name,
                                        location = AnalyticsEvent.Value.Location.VIDEO_EDITOR_SEARCH,
                                        isPremium = song.isPremium
                                    )
                                    focusManager.clearFocus()
                                    keyboardController?.hide()
                                    MusicPreviewManager.togglePreview(song.id)
                                },
                                modifier = Modifier.onFirstVisible(key = song.id) {
                                    sessionManager.trackSongImpressionAndMark(
                                        songId = song.id.toString(),
                                        songName = song.name,
                                        location = AnalyticsEvent.Value.Location.VIDEO_EDITOR_SEARCH,
                                        isPremium = song.isPremium
                                    )
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

                        // Genre filter chips
                        if (genres.isNotEmpty()) {
                            item(key = "empty_genre_chips") {
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(dimens.spaceSm)
                                ) {
                                    item(key = "all") {
                                        AppFilterChip(
                                            text = stringResource(R.string.settings_all),
                                            isSelected = selectedGenre == null,
                                            onClick = { viewModel.onGenreSelected(null) }
                                        )
                                    }
                                    items(genres, key = { it.id }) { genre ->
                                        AppFilterChip(
                                            text = genre.displayName,
                                            isSelected = selectedGenre == genre.id,
                                            onClick = { viewModel.onGenreSelected(genre.id) }
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(dimens.spaceMd))
                            }
                        }

                        // Suggested songs section
                        if (suggestedSongs.isNotEmpty()) {
                            items(suggestedSongs, key = { "empty_song_${it.id}" }) { song ->
                                SongListItem(
                                    name = song.name,
                                    artist = song.artist,
                                    coverUrl = song.coverUrl,
                                    isPlaying = song.id == previewingSongId,
                                    isSelected = song.id == selectedForConfirmId,
                                    isLoading = song.id == selectedForConfirmId && isLoadingPreview,
                                    onSongClick = {
                                        onSongClick(song)
                                        selectedSongLocation = AnalyticsEvent.Value.Location.VIDEO_EDITOR_RCM
                                        Analytics.trackSongClick(
                                            songId = song.id.toString(),
                                            songName = song.name,
                                            location = AnalyticsEvent.Value.Location.VIDEO_EDITOR_RCM,
                                            isPremium = song.isPremium
                                        )
                                        Analytics.trackSongPreview(
                                            songId = song.id.toString(),
                                            songName = song.name,
                                            location = AnalyticsEvent.Value.Location.VIDEO_EDITOR_RCM,
                                            isPremium = song.isPremium
                                        )
                                        focusManager.clearFocus()
                                        keyboardController?.hide()
                                        MusicPreviewManager.togglePreview(song.id)
                                    },
                                    modifier = Modifier.onFirstVisible(key = song.id) {
                                        sessionManager.trackSongImpressionAndMark(
                                            songId = song.id.toString(),
                                            songName = song.name,
                                            location = AnalyticsEvent.Value.Location.VIDEO_EDITOR_RCM,
                                            isPremium = song.isPremium
                                        )
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

                    // Scroll-position-based pagination: load more when near bottom
                    val shouldLoadMore by remember(listState, suggestedLoadingMore) {
                        derivedStateOf {
                            val layoutInfo = listState.layoutInfo
                            val totalItems = layoutInfo.totalItemsCount
                            val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                            !suggestedLoadingMore && lastVisibleIndex >= totalItems - 4
                        }
                    }

                    LaunchedEffect(shouldLoadMore) {
                        if (shouldLoadMore) viewModel.loadMoreSuggested()
                    }

                    // Build feed items at composable scope (not inside LazyListScope)
                    val suggestedFeedItems = remember(suggestedSongs, infeedInterval) {
                        buildSongFeedWithAds(suggestedSongs, infeedInterval)
                    }

                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = dimens.spaceMd)
                    ) {
                        // Genre filter chips
                        if (genres.isNotEmpty()) {
                            item(key = "genre_chips") {
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(dimens.spaceSm)
                                ) {
                                    item(key = "all") {
                                        AppFilterChip(
                                            text = stringResource(R.string.settings_all),
                                            isSelected = selectedGenre == null,
                                            onClick = { viewModel.onGenreSelected(null) }
                                        )
                                    }
                                    items(genres, key = { it.id }) { genre ->
                                        AppFilterChip(
                                            text = genre.displayName,
                                            isSelected = selectedGenre == genre.id,
                                            onClick = { viewModel.onGenreSelected(genre.id) }
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(dimens.spaceMd))
                            }
                        }

                        // Loading indicator when switching genres
                        if (suggestedSongsLoading) {
                            item(key = "genre_loading") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = dimens.spaceXl),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                            }
                        } else {
                            // Song list with interleaved native ads
                            items(
                                items = suggestedFeedItems,
                                key = { item -> songFeedItemKey(item, "suggested_") },
                                contentType = { item ->
                                    when (item) {
                                        is SongFeedItem.Song -> "song"
                                        is SongFeedItem.Ad -> "ad"
                                    }
                                }
                            ) { item ->
                                when (item) {
                                    is SongFeedItem.Song -> {
                                        val song = item.song
                                        SongListItem(
                                            name = song.name,
                                            artist = song.artist,
                                            coverUrl = song.coverUrl,
                                            isPlaying = song.id == previewingSongId,
                                            isSelected = song.id == selectedForConfirmId,
                                            isLoading = song.id == selectedForConfirmId && isLoadingPreview,
                                            onSongClick = {
                                                onSongClick(song)
                                                selectedSongLocation = AnalyticsEvent.Value.Location.VIDEO_EDITOR_RCM
                                                Analytics.trackSongClick(
                                                    songId = song.id.toString(),
                                                    songName = song.name,
                                                    location = AnalyticsEvent.Value.Location.VIDEO_EDITOR_RCM,
                                                    isPremium = song.isPremium
                                                )
                                                Analytics.trackSongPreview(
                                                    songId = song.id.toString(),
                                                    songName = song.name,
                                                    location = AnalyticsEvent.Value.Location.VIDEO_EDITOR_RCM,
                                                    isPremium = song.isPremium
                                                )
                                                focusManager.clearFocus()
                                                keyboardController?.hide()
                                                MusicPreviewManager.togglePreview(song.id)
                                            },
                                            modifier = Modifier.onFirstVisible(key = song.id) {
                                                sessionManager.trackSongImpressionAndMark(
                                                    songId = song.id.toString(),
                                                    songName = song.name,
                                                    location = AnalyticsEvent.Value.Location.VIDEO_EDITOR_RCM,
                                                    isPremium = song.isPremium
                                                )
                                            }
                                        )
                                    }
                                    is SongFeedItem.Ad -> {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(dimens.radiusXl))
                                                .background(MaterialTheme.colorScheme.surface)
                                        ) {
                                            NativeAdView(
                                                placement = AdPlacement.NATIVE_EDITOR_MUSIC_INFEED,
                                                modifier = Modifier.fillMaxWidth().height(100.dp),
                                                autoLoad = true,
                                                isDebug = BuildConfig.DEBUG,
                                                onAdClicked = { adClickDetector.onAdClick(it) }
                                            )
                                        }
                                    }
                                }
                            }

                            // Loading indicator at bottom when loading more
                            if (suggestedLoadingMore) {
                                item(key = "suggested_load_more") {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = dimens.spaceMd),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(24.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                        )
                                    }
                                }
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

    // Handle rewarded ad presentation
    RewardedAdPresenter(
        shouldPresent = shouldPresentAd,
        placement = AdPlacement.REWARD_UNLOCK_SONG,
        adsLoaderService = adsLoaderService,
        onRewardEarned = ::onRewardEarned,
        onAdFailed = ::onAdFailed
    )

    LaunchedEffect(Unit) {
        try {
            delay(100)
            focusRequester.requestFocus()
        } catch (_: Exception) { }
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, backgroundColor = 0xFF101010)
@Composable
private fun MusicSearchContentPreview() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SplashBackground)
            .padding(horizontal = 20.dp)
            .padding(top = 16.dp, bottom = 32.dp)
    ) {
        // Centered title
        Text(
            text = "Change music",
            color = TextMuted,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            lineHeight = 18.sp,
            modifier = Modifier.fillMaxWidth(),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Search field
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = SearchFieldBackground,
                    shape = RoundedCornerShape(16.dp)
                )
                .border(
                    width = 1.dp,
                    color = SearchFieldBorder,
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(horizontal = 12.dp, vertical = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = TextTertiary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Search songs...",
                    color = TextTertiary,
                    fontSize = 14.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Sample song items - selected + playing
        SongListItem(
            name = "Sunflower",
            artist = "Post Malone, Swae Lee",
            coverUrl = "",
            isPlaying = true,
            isSelected = true,
            onSongClick = {}
        )
        // Normal item
        SongListItem(
            name = "Blinding Lights",
            artist = "The Weeknd",
            coverUrl = "",
            onSongClick = {}
        )
        // Selected but not playing
        SongListItem(
            name = "Shape of You",
            artist = "Ed Sheeran",
            coverUrl = "",
            isSelected = true,
            onSongClick = {}
        )
        // Normal item
        SongListItem(
            name = "Stay With Me",
            artist = "Sam Smith",
            coverUrl = "",
            onSongClick = {}
        )
    }
}
