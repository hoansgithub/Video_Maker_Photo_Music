package com.videomaker.aimusic.modules.editor.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.videomaker.aimusic.ui.components.ProvideShimmerEffect
import com.videomaker.aimusic.ui.components.ShimmerPlaceholder
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
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
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
import co.alcheclub.lib.acccore.ads.compose.BannerAdView
import com.videomaker.aimusic.core.ads.AdPlacementConfigService
import com.videomaker.aimusic.modules.editor.EditorScreenState
import com.videomaker.aimusic.modules.home.components.innerShadow
import com.videomaker.aimusic.ui.components.AppAsyncImage
import com.videomaker.aimusic.ui.components.PlayingAnimationBars
import com.videomaker.aimusic.ui.components.SongItemMore
import com.videomaker.aimusic.ui.theme.Neutral_N900
import com.videomaker.aimusic.ui.theme.TextPrimaryDark
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
    onSongSelected: (MusicSong, Long) -> Unit,
    onDismiss: () -> Unit,
    currentVideoDurationMs: Long = 0L,
    initialSong: MusicSong? = null
) {
    val adPlacementConfigService: AdPlacementConfigService = koinInject()
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

    // Resolve a song by id across results / suggested / the editor's current (initial) song.
    fun resolveSong(id: Long?): MusicSong? {
        if (id == null) return null
        return (uiState as? SongSearchUiState.Results)?.songs?.find { it.id == id }
            ?: suggestedSongs.find { it.id == id }
            ?: initialSong?.takeIf { it.id == id }
    }

    // The song shown in the bottom player: the one selected/previewing, else the editor's song.
    val displaySong = resolveSong(selectedForConfirmId ?: previewingSongId) ?: initialSong

    var selectionStartMs by remember(displaySong?.id) {
        // When displaying the saved project song, start from its saved trim position.
        // When previewing a new song from the picker, use first hook point.
        val savedPosition = if (displaySong == initialSong && initialSong != null) {
            initialSong.hookStartTimeMs.takeIf { it > 0L }
        } else null
        mutableLongStateOf(savedPosition ?: displaySong?.hookStartTimes?.firstOrNull() ?: 0L)
    }

    // Handle confirm button click
    fun onConfirmClick() {
        val selectedId = MusicPreviewManager.getSelectedId() ?: return
        val song = resolveSong(selectedId) ?: return

        if (isSongLocked(song)) {
            // Song is locked - present rewarded ad directly
            pendingSongToUnlock = song
            shouldPresentAd = true
        } else {
            // Song is unlocked or free - directly select
            MusicPreviewManager.clearPreviewState()
            onSongSelected(song, selectionStartMs)
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
            onSongSelected(song, selectionStartMs)
        }
    }

    fun onAdFailed() {
        pendingSongToUnlock = null
        shouldPresentAd = false
    }

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

    // While a fullscreen ad (AOA / interstitial) is up we PAUSE the preview but must keep it
    // resumable. exoPlayer.pause() fires onPlayWhenReadyChanged(false) which normally tears the
    // preview down (stopPreview → previewingSongId=null → exoPlayer.stop()), so a later play()
    // would do nothing. This flag tells that listener to skip the teardown so ON_RESUME can
    // simply resume playback when the ad closes.
    var suppressStopForAdResume by remember { mutableStateOf(false) }

    // Pause/resume when Activity loses focus (AOA, interstitial ads)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        var wasPlayingBeforeActivityPause = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    wasPlayingBeforeActivityPause = exoPlayer.isPlaying
                    if (exoPlayer.isPlaying) {
                        suppressStopForAdResume = true
                        exoPlayer.pause()
                    }
                }

                Lifecycle.Event.ON_RESUME -> {
                    if (wasPlayingBeforeActivityPause) exoPlayer.play()
                    wasPlayingBeforeActivityPause = false
                    suppressStopForAdResume = false
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
                val song = resolveSong(previewingSongId)

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
                                    // Release the initial loading gate once the editor's
                                    // current song is ready to auto-play.
                                    viewModel.onInitialSongReady()
                                }
                            }

                            override fun onPlayWhenReadyChanged(
                                playWhenReady: Boolean,
                                reason: Int
                            ) {
                                // Skip teardown when we paused only to show a fullscreen ad —
                                // the lifecycle observer resumes playback when the ad closes.
                                if (!playWhenReady && !suppressStopForAdResume) {
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

    // ============================================
    // BOTTOM PLAYER CARD STATE
    // ============================================
    var positionMs by remember { mutableLongStateOf(0L) }
    var playerDurationMs by remember { mutableLongStateOf(0L) }
    var isPreviewPlaying by remember { mutableStateOf(false) }

    val waveform = remember(displaySong?.id) { placeholderWaveform(seed = displaySong?.id ?: 0L) }
    val hookSegments = remember(displaySong) {
        val hookTimes = displaySong?.hookStartTimes ?: emptyList()
        hookTimes.filter { it > 0L }.map { start ->
            MusicHookSegment(startMs = start, endMs = start)
        }
    }
    val songDurationMs = displaySong?.durationMs?.toLong()?.takeIf { it > 0L } ?: playerDurationMs

    // Seed the editor's current song on open and stay in Loading until it can auto-play.
    LaunchedEffect(initialSong?.id) {
        val seed = initialSong ?: return@LaunchedEffect
        if (MusicPreviewManager.getSelectedId() == null) {
            viewModel.beginWithInitialSong()
            MusicPreviewManager.togglePreview(seed.id)
        }
    }

    // Poll playback position / duration / playing state for the player UI, and loop
    // playback only within the selected frame [selectionStart, selectionStart + videoDuration].
    LaunchedEffect(previewingSongId) {
        while (true) {
            if (currentVideoDurationMs > 0L && previewingSongId != null && exoPlayer.isPlaying) {
                val winStart = selectionStartMs
                val winEnd = selectionStartMs + currentVideoDurationMs
                val pos = exoPlayer.currentPosition
                if (pos < winStart || pos >= winEnd) {
                    exoPlayer.seekTo(winStart)
                }
            }
            positionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
            playerDurationMs = exoPlayer.duration.takeIf { it > 0L } ?: 0L
            isPreviewPlaying = exoPlayer.isPlaying
            // Authoritatively clear the loading flag once the player is actually ready —
            // the Player.Listener can miss STATE_READY for cached audio (prepare resolves
            // before/around addListener), which would otherwise keep the shimmer forever.
            if (isLoadingPreview &&
                (exoPlayer.isPlaying || exoPlayer.playbackState == Player.STATE_READY)
            ) {
                MusicPreviewManager.onPreviewPrepared()
            }
            delay(60)
        }
    }

    // Pause preview while searching / on empty results. Resume only on explicit play
    // or when the user selects another song.
    LaunchedEffect(uiState) {
        if (uiState is SongSearchUiState.Searching || uiState is SongSearchUiState.Empty) {
            if (exoPlayer.isPlaying) exoPlayer.pause()
        }
    }

    // Robustly release the initial loading gate: the moment the seeded song is ready /
    // actually playing, leave the Loading skeleton for Idle. This is decoupled from the
    // ExoPlayer listener timing so it can't get stuck.
    LaunchedEffect(isPreviewPlaying, isLoadingPreview) {
        if (isPreviewPlaying || (previewingSongId != null && !isLoadingPreview)) {
            viewModel.onInitialSongReady()
        }
    }

    Dialog(
        onDismissRequest = {
            MusicPreviewManager.clearPreviewState()
            onDismiss()
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        // IME/focus controllers MUST be obtained INSIDE the Dialog: the Dialog hosts its own
        // window with a separate FocusOwner and IME. Controllers read in the outer composition
        // target the host window and have no effect on the search field shown here.
        val focusManager = LocalFocusManager.current
        val keyboardController = LocalSoftwareKeyboardController.current

        // Shared song tap handler — used by the Results and Idle lists.
        val onSongPreview: (MusicSong, String) -> Unit = { song, location ->
            onSongClick(song)
            selectedSongLocation = location
            Analytics.trackSongClick(
                songId = song.id.toString(),
                songName = song.name,
                location = location,
                isPremium = song.isPremium
            )
            Analytics.trackSongPreview(
                songId = song.id.toString(),
                songName = song.name,
                location = location,
                isPremium = song.isPremium
            )
            focusManager.clearFocus()
            keyboardController?.hide()
            MusicPreviewManager.togglePreview(song.id)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(SplashBackground)
                .statusBarsPadding()
                // Tap anywhere on empty space dismisses the keyboard. Taps consumed by
                // child clickables (song items, buttons, text field) are not affected.
                .pointerInput(Unit) {
                    detectTapGestures(onTap = {
                        focusManager.clearFocus()
                        keyboardController?.hide()
                    })
                }
        ) {
            // Title row with close button (left), centered title and confirm button (right)
            Box(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                // Close button - left aligned
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.close),
                    tint = TextPrimary,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .size(28.dp)
                        .clickableSingle {
                            MusicPreviewManager.clearPreviewState()
                            onDismiss()
                        }
                )

                // Centered title
                Text(
                    text = stringResource(R.string.song_search_title),
                    color = TextMuted,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 18.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }

            val listState = rememberLazyListState()

            // Aggressive keyboard dismissal on ANY scroll movement
            LaunchedEffect(listState) {
                var previousIndex = listState.firstVisibleItemIndex
                var previousOffset = listState.firstVisibleItemScrollOffset
                snapshotFlow {
                    listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
                }.collect { (index, offset) ->
                    if (index != previousIndex || offset != previousOffset) {
                        focusManager.clearFocus()
                        keyboardController?.hide()
                    }
                    previousIndex = index
                    previousOffset = offset
                }
            }

            // Scroll-position-based pagination — only while browsing suggested songs (Idle)
            val shouldLoadMore by remember(listState) {
                derivedStateOf {
                    if (uiState !is SongSearchUiState.Idle) return@derivedStateOf false
                    val layoutInfo = listState.layoutInfo
                    val totalItems = layoutInfo.totalItemsCount
                    val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                    !suggestedLoadingMore && lastVisibleIndex >= totalItems - 4
                }
            }
            LaunchedEffect(shouldLoadMore) {
                if (shouldLoadMore) viewModel.loadMoreSuggested()
            }

            // Feed items built at composable scope (not inside LazyListScope)
            val resultsFeedItems = remember(uiState, infeedInterval) {
                (uiState as? SongSearchUiState.Results)
                    ?.let { buildSongFeedWithAds(it.songs, infeedInterval) }
                    ?: emptyList()
            }
            val suggestedFeedItems = remember(suggestedSongs, infeedInterval) {
                buildSongFeedWithAds(suggestedSongs, infeedInterval)
            }

            // Single scroll container: only the title row above stays fixed; the search
            // field, genre tabs and lists all scroll together.
            ProvideShimmerEffect {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxWidth(),
                        contentPadding = PaddingValues(bottom = dimens.spaceMd)
                    ) {
                        // Search field (scrolls with the content)
                        item(key = "search_field") {
                            Spacer(modifier = Modifier.height(16.dp))

                            // Search field - matching SongSearchTopBar style
                            Row(
                                modifier = Modifier
                                    .padding(horizontal = 16.dp)
                                    .fillMaxWidth()
                                    .background(
                                        color = Color.White.copy(0.1f),
                                        shape = RoundedCornerShape(16.dp)
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = Color.White.copy(0.16f),
                                        shape = RoundedCornerShape(16.dp)
                                    )
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_music_note),
                                    contentDescription = null,
                                    tint = TextPrimaryDark,
                                    modifier = Modifier
                                        .padding(end = 8.dp)
                                        .size(20.dp)
                                )
                                Box(modifier = Modifier.weight(1f)) {
                                    if (displayText.isEmpty()) {
                                        Text(
                                            text = stringResource(R.string.song_search_sheet_hint),
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
                                                focusManager.clearFocus()
                                                keyboardController?.hide()
                                            }),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                    )
                                }
                                if (displayText.isNotEmpty()) {
                                    Spacer(modifier = Modifier.width(dimens.spaceXs))
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = stringResource(R.string.close),
                                        tint = SplashBackground,
                                        modifier = Modifier
                                            .background(Color.White, CircleShape)
                                            .size(20.dp)
                                            .clickableSingle { viewModel.onClearQuery() }
                                            .padding(4.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))
                        }

                        // Results
                        when (val state = uiState) {
                            is SongSearchUiState.Loading -> {
                                // Initial skeleton: genre tabs + shimmer rows
                                item(key = "loading_tabs") {
                                    GenreTabRow(
                                        genres = genres,
                                        selectedGenre = selectedGenre,
                                        onGenreSelected = viewModel::onGenreSelected
                                    )
                                    Spacer(modifier = Modifier.height(dimens.spaceMd))
                                }
                                items(6, key = { "loading_skeleton_$it" }) {
                                    SongSkeletonItem(
                                        modifier = Modifier.padding(horizontal = 12.dp)
                                    )
                                }
                            }

                            is SongSearchUiState.Searching -> {
                                item(key = "searching") {
                                    Text(
                                        text = stringResource(R.string.song_search_searching),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TextSecondary,
                                        modifier = Modifier
                                            .padding(vertical = dimens.spaceSm, horizontal = 12.dp)
                                    )
                                    SongSkeletonItem(modifier = Modifier.padding(horizontal = 12.dp))
                                }
                            }

                            is SongSearchUiState.Results -> {
                                // Results count header
                                item(key = "results_header") {
                                    Text(
                                        text = stringResource(
                                            R.string.song_search_results_found,
                                            state.songs.size
                                        ),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TextSecondary,
                                        modifier = Modifier
                                            .padding(horizontal = 12.dp)
                                            .padding(bottom = dimens.spaceSm)
                                    )
                                }
                                items(
                                    items = resultsFeedItems,
                                    key = { item -> songFeedItemKey(item, "results_") },
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
                                            SongEditItem(
                                                name = song.name,
                                                artist = song.artist,
                                                coverUrl = song.coverUrl,
                                                isPlaying = song.id == previewingSongId,
                                                isSelected = song.id == selectedForConfirmId,
                                                isShowAD = song.isPremium,
                                                onSongClick = {
                                                    onSongPreview(
                                                        song,
                                                        AnalyticsEvent.Value.Location.VIDEO_EDITOR_SEARCH
                                                    )
                                                },
                                                modifier = Modifier
                                                    .padding(horizontal = 12.dp)
                                                    .onFirstVisible(key = song.id) {
                                                    sessionManager.trackSongImpressionAndMark(
                                                        songId = song.id.toString(),
                                                        songName = song.name,
                                                        location = AnalyticsEvent.Value.Location.VIDEO_EDITOR_SEARCH,
                                                        isPremium = song.isPremium
                                                    )
                                                }
                                            )
                                        }

                                        is SongFeedItem.Ad -> {
                                            Box(
                                                modifier = Modifier
                                                    .padding(horizontal = 12.dp)
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(dimens.radiusXl))
                                                    .background(MaterialTheme.colorScheme.surface)
                                            ) {
                                                NativeAdView(
                                                    placement = AdPlacement.NATIVE_EDITOR_MUSIC_INFEED,
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(100.dp),
                                                    autoLoad = true,
                                                    isDebug = BuildConfig.DEBUG,
                                                    onAdClicked = { adClickDetector.onAdClick(it) }
                                                )
                                            }
                                        }
                                    }
                                }

                                item{
                                    Spacer(Modifier.height(167.dp))
                                }
                            }

                            is SongSearchUiState.Empty -> {
                                item(key = "empty") {
                                    Column(
                                        modifier = Modifier
                                            .padding(horizontal = 12.dp)
                                            .padding(top = 36.dp)
                                            .fillMaxWidth(),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Image(
                                            painter = painterResource(R.drawable.img_empty_search),
                                            contentDescription = null,
                                            modifier = Modifier.size(80.dp)
                                        )
                                        Spacer(modifier = Modifier.height(dimens.spaceMd))
                                        Text(
                                            text = stringResource(R.string.song_search_no_results),
                                            style = MaterialTheme.typography.titleMedium,
                                            color = TextPrimary,
                                            fontWeight = FontWeight.W600,
                                            fontSize = 18.sp
                                        )
                                        Spacer(modifier = Modifier.height(dimens.spaceSm))
                                        Text(
                                            text = stringResource(R.string.search_no_results_hint),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = TextSecondary
                                        )
                                    }
                                }
                            }

                            is SongSearchUiState.Error -> {
                                item(key = "error") {
                                    Box(
                                        modifier = Modifier.fillParentMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = state.message,
                                            color = MaterialTheme.colorScheme.error,
                                            fontSize = 16.sp
                                        )
                                    }
                                }
                            }

                            is SongSearchUiState.Idle -> {
                                // Genre tabs above the scrolling suggested feed
                                item(key = "idle_tabs") {
                                    GenreTabRow(
                                        genres = genres,
                                        selectedGenre = selectedGenre,
                                        onGenreSelected = viewModel::onGenreSelected
                                    )
                                    Spacer(modifier = Modifier.height(dimens.spaceMd))
                                }

                                if (suggestedSongsLoading) {
                                    // Genre switch / initial load — show skeleton rows
                                    items(6, key = { "idle_skeleton_$it" }) {
                                        SongSkeletonItem(modifier = Modifier.padding(horizontal = 12.dp))
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
                                                SongEditItem(
                                                    name = song.name,
                                                    artist = song.artist,
                                                    coverUrl = song.coverUrl,
                                                    isPlaying = song.id == previewingSongId,
                                                    isSelected = song.id == selectedForConfirmId,
                                                    isShowAD = song.isPremium,
                                                    onSongClick = {
                                                        onSongPreview(
                                                            song,
                                                            AnalyticsEvent.Value.Location.VIDEO_EDITOR_RCM
                                                        )
                                                    },
                                                    modifier = Modifier
                                                        .padding(horizontal = 12.dp)
                                                        .onFirstVisible(key = song.id) {
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
                                                        .padding(horizontal = 12.dp)
                                                        .fillMaxWidth()
                                                        .clip(RoundedCornerShape(dimens.radiusXl))
                                                        .background(MaterialTheme.colorScheme.surface)
                                                ) {
                                                    NativeAdView(
                                                        placement = AdPlacement.NATIVE_EDITOR_MUSIC_INFEED,
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .height(100.dp),
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
                                                    color = MaterialTheme.colorScheme.onSurface.copy(
                                                        alpha = 0.5f
                                                    )
                                                )
                                            }
                                        }
                                    }
                                }

                                item{
                                    Spacer(Modifier.height(167.dp))
                                }
                            }
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                            // Smoothly animate any residual height delta between skeleton/player.
                            .animateContentSize()
                            // Consume all touches so they don't pass through to the song list
                            // or the keyboard-dismiss handler behind this overlay.
                            .pointerInput(Unit) {
                                detectTapGestures { /* consumed */ }
                            }
                    ) {
                        val playerSong = displaySong
                        // Newly-selected song still preparing — show shimmer until ready.
                        val showSkeleton = playerSong != null && isLoadingPreview && !isPreviewPlaying
                        // Crossfade the skeleton<->player swap so changing songs fades instead of
                        // hard-cutting (no flicker). Same outer modifier keeps size/position fixed.
                        Crossfade(
                            targetState = showSkeleton,
                            animationSpec = tween(durationMillis = 200),
                            label = "music_player_swap"
                        ) { skeleton ->
                            when {
                                playerSong == null -> {}
                                skeleton -> MusicSelectionPlayerSkeleton()
                                else -> MusicSelectionPlayer(
                                    coverUrl = playerSong.coverUrl,
                                    name = playerSong.name,
                                    artist = playerSong.artist,
                                    isPlaying = isPreviewPlaying,
                                    positionMs = positionMs,
                                    songDurationMs = songDurationMs,
                                    videoDurationMs = currentVideoDurationMs,
                                    selectionStartMs = selectionStartMs,
                                    hookSegments = hookSegments,
                                    waveform = waveform,
                                    onPlayPauseClick = {
                                        if (exoPlayer.isPlaying) {
                                            exoPlayer.pause()
                                            Analytics.trackSongPause(
                                                songId = playerSong.id.toString(),
                                                songName = playerSong.name,
                                                location = AnalyticsEvent.Value.Location.EDITOR_SONG,
                                                isPremium = playerSong.isPremium
                                            )
                                        } else {
                                            if (previewingSongId == playerSong.id) {
                                                exoPlayer.play()
                                            } else {
                                                MusicPreviewManager.togglePreview(playerSong.id)
                                            }
                                            Analytics.trackSongPlay(
                                                songId = playerSong.id.toString(),
                                                songName = playerSong.name,
                                                location = AnalyticsEvent.Value.Location.EDITOR_SONG,
                                                isPremium = playerSong.isPremium
                                            )
                                        }
                                    },
                                    onConfirmClick = { onConfirmClick() },
                                    onSelectionChange = { newStart ->
                                        selectionStartMs = newStart
                                        runCatching { exoPlayer.seekTo(newStart) }
                                    },
                                    onStartTimeCommit = { source ->
                                        val loc = when (source) {
                                            StartTimeChangeSource.DURATION_BAR ->
                                                AnalyticsEvent.Value.Location.DURATION_BAR
                                            StartTimeChangeSource.DRAG_BAR ->
                                                AnalyticsEvent.Value.Location.DRAG_BAR
                                        }
                                        Analytics.trackSongStartTimeChange(
                                            songId = playerSong.id.toString(),
                                            location = loc
                                        )
                                    },
                                )
                            }
                        }
                    }
                }
            }  // End ProvideShimmerEffect

            Box {
                Spacer(Modifier.navigationBarsPadding())
                if (adPlacementConfigService.bannerUseNative) {
                    NativeAdView(
                        placement = AdPlacement.NATIVE_EDITOR_BANNER,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp),
                        isDebug = BuildConfig.DEBUG,
                        onAdClicked = { adClickDetector.onAdClick(it) }
                    )
                } else {
                    BannerAdView(
                        placement = AdPlacement.BANNER_EDITOR,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        onAdClicked = { adClickDetector.onAdClick(it) }
                    )
                }
            }
        }  // End Column
    }  // End Dialog

    // Handle rewarded ad presentation
    RewardedAdPresenter(
        shouldPresent = shouldPresentAd,
        placement = AdPlacement.REWARD_UNLOCK_SONG,
        adsLoaderService = adsLoaderService,
        onRewardEarned = ::onRewardEarned,
        onAdFailed = ::onAdFailed
    )
}

// ============================================
// GENRE TABS (underline style)
// ============================================

@Composable
private fun GenreTabRow(
    genres: List<SongGenre>,
    selectedGenre: String?,
    onGenreSelected: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    if (genres.isEmpty()) return
    val dimens = AppDimens.current
    Box(
        modifier = modifier.fillMaxWidth(),
    ) {
        Spacer(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Color.White.copy(0.08f))
                .align(Alignment.BottomCenter)
        )
        LazyRow(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(dimens.spaceLg),
            contentPadding = PaddingValues(horizontal = 12.dp)
        ) {
            item(key = "all") {
                GenreTab(
                    text = stringResource(R.string.settings_all),
                    isSelected = selectedGenre == null,
                    onClick = { onGenreSelected(null) }
                )
            }
            items(genres, key = { it.id }) { genre ->
                GenreTab(
                    text = genre.displayName,
                    isSelected = selectedGenre == genre.id,
                    onClick = { onGenreSelected(genre.id) }
                )
            }
        }
    }
}

@Composable
private fun GenreTab(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickableSingle(onClick = onClick)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (isSelected) MaterialTheme.colorScheme.primary else TextTertiary,
            modifier = Modifier.padding(vertical = 6.dp)
        )
        Box(
            modifier = Modifier
                .width(20.dp)
                .height(2.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(
                    if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
                )
        )
    }
}

// ============================================
// SKELETON ROW (shimmer placeholder for a song item)
// ============================================

@Composable
private fun SongSkeletonItem(
    modifier: Modifier = Modifier
) {
    val dimens = AppDimens.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(dimens.spaceSm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ShimmerPlaceholder(
            modifier = Modifier.size(40.dp),
            cornerRadius = dimens.radiusMd
        )
        Spacer(modifier = Modifier.width(dimens.spaceMd))
        Column(modifier = Modifier.weight(1f)) {
            ShimmerPlaceholder(
                modifier = Modifier
                    .fillMaxWidth(0.55f)
                    .height(14.dp),
                cornerRadius = 4.dp
            )
            Spacer(modifier = Modifier.height(dimens.spaceXs))
            ShimmerPlaceholder(
                modifier = Modifier
                    .fillMaxWidth(0.3f)
                    .height(12.dp),
                cornerRadius = 4.dp
            )
        }
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
                    color = SearchFieldBackground, shape = RoundedCornerShape(16.dp)
                )
                .border(
                    width = 1.dp, color = SearchFieldBorder, shape = RoundedCornerShape(16.dp)
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
            isLoading = true,
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

@Composable
fun SongEditItem(
    name: String,
    artist: String,
    coverUrl: String,
    isShowOption: Boolean = false,
    onClickDelete: () -> Unit = {},
    onSongClick: () -> Unit,
    modifier: Modifier = Modifier,
    isPlaying: Boolean = false,
    isSelected: Boolean = false,
    isShowAD: Boolean = false
) {
    val dimens = AppDimens.current

    Card(
        onClick = onSongClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(dimens.radiusLg),
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(dimens.spaceSm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail
            AppAsyncImage(
                imageUrl = coverUrl,
                contentDescription = name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(dimens.radiusMd))
            )

            Spacer(modifier = Modifier.width(dimens.spaceMd))

            // Song name + artist
            Column(modifier = Modifier.weight(1f)) {
                // Title row with animated bars when playing
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Medium,
                        fontSize = 15.sp
                    ),
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(dimens.spaceXxs))
                Text(
                    text = artist,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.Normal,
                        fontSize = 13.sp
                    ),
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }


            if (isSelected && isPlaying) {
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .background(Neutral_N900,CircleShape),
                    contentAlignment = Alignment.Center
                ){
                    PlayingAnimationBars(
                        barColor = MaterialTheme.colorScheme.primary,
                        barWidth = 3.dp,
                        maxBarHeight = 14.dp,
                        containerSize = 18.dp
                    )
                }
            }

            if (isShowAD) {
                Spacer(modifier = Modifier.width(dimens.spaceMd))
                AdBadge(
                    style = AdBadgeStyle.Small(
                        textColor = Color.White,
                        backgroundColor = Color.White.copy(alpha = 0.2f)
                    )
                )
            }
        }
    }
}
