package com.videomaker.aimusic.modules.songs

import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Slideshow
import co.alcheclub.lib.acccore.ads.loader.AdsLoaderService
import com.videomaker.aimusic.core.ads.RewardedAdPresenter
import com.videomaker.aimusic.core.constants.AdPlacement
import com.videomaker.aimusic.ui.components.AdBadge
import com.videomaker.aimusic.ui.components.AdBadgeStyle
import com.videomaker.aimusic.ui.components.AdsLoadingOverlay
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.koin.compose.koinInject
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.videomaker.aimusic.media.audio.HookStartTimePolicy
import com.videomaker.aimusic.R
import com.videomaker.aimusic.core.analytics.Analytics
import com.videomaker.aimusic.core.analytics.AnalyticsEvent
import com.videomaker.aimusic.di.MusicPlayerViewModelFactory
import com.videomaker.aimusic.domain.model.MusicSong
import com.videomaker.aimusic.ui.components.AppAsyncImage
import com.videomaker.aimusic.ui.components.ModifierExtension.clickableSingle
import com.videomaker.aimusic.ui.components.ProvideShimmerEffect
import com.videomaker.aimusic.ui.components.ShimmerBox
import com.videomaker.aimusic.ui.theme.Black60
import com.videomaker.aimusic.ui.theme.Gray500
import com.videomaker.aimusic.ui.theme.Gray600
import com.videomaker.aimusic.ui.theme.PlayerCardBackground
import com.videomaker.aimusic.ui.theme.Primary
import com.videomaker.aimusic.ui.theme.SurfaceDark
import com.videomaker.aimusic.ui.theme.TextOnPrimary
import com.videomaker.aimusic.ui.theme.TextPrimary
import com.videomaker.aimusic.ui.theme.TextSecondary
import com.videomaker.aimusic.ui.theme.White16
import kotlinx.coroutines.delay
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.videomaker.aimusic.core.popup.TrendingPopupCoordinator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// ============================================
// HELPER - Release player async to avoid ANR
// ============================================

/**
 * Release ExoPlayer asynchronously on background thread to avoid ANR.
 * ExoPlayer.release() can block for several seconds when releasing audio resources.
 */
private fun ExoPlayer.releaseAsync() {
    val playerToRelease = this
    ProcessLifecycleOwner.get().lifecycleScope.launch(Dispatchers.IO) {
        runCatching {
            Log.d("MusicPlayerBottomSheet", "Releasing player on background thread...")
            playerToRelease.release()
            Log.d("MusicPlayerBottomSheet", "Player released successfully")
        }.onFailure { e ->
            Log.e("MusicPlayerBottomSheet", "Failed to release player", e)
        }
    }
}

internal fun shouldApplyMusicPlayerHookSeek(
    playbackState: Int,
    hasAppliedHookSeek: Boolean
): Boolean = playbackState == Player.STATE_READY && !hasAppliedHookSeek

internal fun resolveMusicPlayerHookStartPositionMs(
    hookStartTimeMs: Long,
    playerDurationMs: Long?,
    songDurationMs: Int?
): Long {
    val resolvedDurationMs = playerDurationMs?.takeIf { it > 0L } ?: songDurationMs?.toLong()
    return HookStartTimePolicy.resolve(
        hookStartTimeMs = hookStartTimeMs,
        durationMs = resolvedDurationMs
    )
}

// ============================================
// MUSIC PLAYER BOTTOM SHEET
// ============================================

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicPlayerBottomSheet(
    song: MusicSong,
    cacheDataSourceFactory: CacheDataSource.Factory,
    location: String = AnalyticsEvent.Value.Location.SONG_PREVIEW,
    isCtaVisible: Boolean = true,
    onPlayerInteraction: () -> Unit = {},
    onDismiss: () -> Unit,
    onUseToCreate: () -> Unit,
) {
    val playerFactory = koinInject<MusicPlayerViewModelFactory>()
    val viewModel: MusicPlayerViewModel = viewModel(
        key = "player_${song.id}",
        factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val viewModel = playerFactory.create(song.id, song)
                if (modelClass.isAssignableFrom(viewModel::class.java)) {
                    @Suppress("UNCHECKED_CAST")
                    return viewModel as T
                } else {
                    throw IllegalArgumentException(
                        "Unknown ViewModel: ${modelClass.name}, expected: ${viewModel::class.java.name}"
                    )
                }
            }
        }
    )
    val isLiked by viewModel.isLiked.collectAsStateWithLifecycle()
    val isSongUnlocked by viewModel.isSongUnlocked.collectAsStateWithLifecycle()
    val shouldPresentAd by viewModel.shouldPresentAd.collectAsStateWithLifecycle()
    val adsLoaderService = koinInject<AdsLoaderService>()
    val trendingPopupCoordinator = koinInject<TrendingPopupCoordinator>()
    val isPopupShowing by trendingPopupCoordinator.isAnyPopupShowing.collectAsStateWithLifecycle()
    var wasPlayingBeforePopup by remember { mutableStateOf(false) }
    var isPlaying  by remember { mutableStateOf(false) }
    var isPrepared by remember { mutableStateOf(false) }
    var currentMs  by remember { mutableIntStateOf(0) }
    var durationMs by remember { mutableIntStateOf((song.durationMs ?: 0).coerceAtLeast(1)) }
    var isSeeking  by remember { mutableStateOf(false) }
    var seekValue  by remember { mutableFloatStateOf(0f) }
    var hasTrackedAutoPreview by remember(song.id) { mutableStateOf(false) }
    var hasAppliedHookSeek by remember(song.id) { mutableStateOf(false) }
    var wasPlayingBeforeAd by remember(song.id) { mutableStateOf(false) }

    val context = LocalContext.current
    // Player is created once per sheet open and released on close.
    // CacheDataSource.Factory routes playback through the 50 MB disk cache
    // so the same mp3 URL is only downloaded once across sessions.
    // LoadControl optimized for faster streaming: reduced buffer, prioritize playback start
    val player = remember {
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 2500,      // Balanced: faster than default, stable on slow networks
                /* maxBufferMs = */ 15000,     // Max buffer 15s (reduced from default 50s)
                /* bufferForPlaybackMs = */ 1500,   // Start after 1.5s buffered (safer than 500ms)
                /* bufferForPlaybackAfterRebufferMs = */ 2500  // ExoPlayer default for stability
            )
            .setPrioritizeTimeOverSizeThresholds(true)  // Favor low latency over buffer size
            .build()

        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(cacheDataSourceFactory))
            .setLoadControl(loadControl)
            .build()
    }

    // ── ExoPlayer lifecycle ────────────────────────────────────────────────
    DisposableEffect(song.id) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                when (state) {
                    Player.STATE_READY -> {
                        val resolvedDurationMs = player.duration.takeIf { it > 0L } ?: song.durationMs?.toLong()
                        durationMs = resolvedDurationMs?.coerceAtLeast(1L)?.toInt() ?: 1
                        if (shouldApplyMusicPlayerHookSeek(state, hasAppliedHookSeek)) {
                            val hookStartPositionMs = resolveMusicPlayerHookStartPositionMs(
                                hookStartTimeMs = song.hookStartTimeMs,
                                playerDurationMs = resolvedDurationMs,
                                songDurationMs = song.durationMs
                            )
                            if (hookStartPositionMs > 0L) {
                                player.seekTo(hookStartPositionMs)
                                currentMs = hookStartPositionMs.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                            }
                            hasAppliedHookSeek = true
                        }
                        player.play()
                        isPlaying = true
                        isPrepared = true
                        if (!hasTrackedAutoPreview) {
                            Analytics.trackSongPreview(
                                songId = song.id.toString(),
                                songName = song.name,
                                location = location
                            )
                            hasTrackedAutoPreview = true
                        }
                        // Note: song_impression with location=song_player should only fire
                        // on next/back clicks per spec — NOT on 1st display. Bottom sheet
                        // currently has no next/back controls, so no impression fires here.
                    }
                    Player.STATE_ENDED -> {
                        isPlaying = false
                        currentMs = durationMs  // Stay at end — keep controls visible
                    }
                }
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
        }

        val url = song.mp3Url.ifEmpty { song.previewUrl }
        if (url.isNotEmpty()) {
            player.addListener(listener)
            runCatching {
                player.setMediaItem(MediaItem.fromUri(url))
                player.prepare()
            }
        }
        onDispose {
            player.removeListener(listener)
            player.stop()  // ✅ Stop immediately (synchronous)
            player.releaseAsync()  // ✅ Release async to avoid ANR
        }
    }

    // ── Progress polling ───────────────────────────────────────────────────
    LaunchedEffect(isPrepared) {
        while (isPrepared) {
            if (!isSeeking) {
                runCatching { currentMs = player.currentPosition.toInt() }
            }
            delay(200)
        }
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Box(
        modifier = Modifier
            .fillMaxSize(),
        contentAlignment = Alignment.BottomCenter
    ) {
        // Player content at bottom with solid background and rounded top corners
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = 12.dp,
                    shape = RoundedCornerShape(
                        topStart = 20.dp,
                        topEnd = 20.dp
                    ),
                    ambientColor = Color(0x29FFFFFF),
                    spotColor = Color(0x29FFFFFF)
                )
                .clip(
                    RoundedCornerShape(
                        topStart = 20.dp,
                        topEnd = 20.dp
                    )
                )
                .clickableSingle{
                    onPlayerInteraction()
                }
                .background(SurfaceDark)
        ) {
            ProvideShimmerEffect {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(
                            topStart = 20.dp,
                            topEnd = 20.dp
                        ))
                        .background(PlayerCardBackground)
                        .padding(16.dp)
                ) {
                    // Song info row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AppAsyncImage(
                            imageUrl = song.coverUrl,
                            contentDescription = song.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )

                        Spacer(Modifier.width(12.dp))


                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                modifier = Modifier.fillMaxWidth().height(18.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ){
                                Text(
                                    text = song.name,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )

                                EqualizerBars(isPlaying = isPlaying)
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = song.artist,
                                fontSize = 13.sp,
                                color = TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Spacer(Modifier.width(12.dp))

                        Icon(
                            painter = painterResource(
                                if (isLiked) R.drawable.ic_heart_liked else R.drawable.ic_heart
                            ),
                            tint = if (isLiked) Primary else TextSecondary,
                            contentDescription = null,
                            modifier = Modifier
                                .size(24.dp)
                                .clickableSingle{
                                    onPlayerInteraction()
                                    if (isLiked) {
                                        Analytics.trackSongUnfavorite(
                                            songId = song.id.toString(),
                                            songName = song.name,
                                            location = location
                                        )
                                    } else {
                                        Analytics.trackSongFavorite(
                                            songId = song.id.toString(),
                                            songName = song.name,
                                            location = location
                                        )
                                    }
                                    viewModel.toggleLike(song)
                                }
                        )

                        Spacer(Modifier.width(12.dp))

                        Spacer(Modifier
                            .width(1.dp)
                            .height(22.dp)
                            .background(Color.White)
                        )
                        Spacer(Modifier.width(8.dp))

                        Icon(
                            imageVector = Icons.Default.SkipPrevious,
                            contentDescription = null,
                            tint = TextPrimary,
                            modifier = Modifier
                                .size(32.dp)
                                .clickableSingle{
                                    onPlayerInteraction()
                                }
                                .padding(3.dp)
                        )

                        Spacer(Modifier.width(8.dp))
                        // Play/pause button
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause
                            else Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = TextPrimary,
                            modifier = Modifier
                                .size(32.dp)
                                .background(White16, CircleShape)
                                .clickableSingle{
                                    onPlayerInteraction()
                                    when {
                                        player.isPlaying -> {
                                            player.pause()
                                            Analytics.trackSongPause(
                                                songId = song.id.toString(),
                                                songName = song.name,
                                                location = location
                                            )
                                        }
                                        player.playbackState == Player.STATE_ENDED -> {
                                            player.seekTo(0)
                                            currentMs = 0
                                            player.play()
                                            Analytics.trackSongPlay(
                                                songId = song.id.toString(),
                                                songName = song.name,
                                                location = location
                                            )
                                        }
                                        else -> {
                                            player.play()
                                            Analytics.trackSongPlay(
                                                songId = song.id.toString(),
                                                songName = song.name,
                                                location = location
                                            )
                                        }
                                    }
                                }
                                .padding(5.dp)
                        )

                        Spacer(Modifier.width(8.dp))

                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = null,
                            tint = TextPrimary,
                            modifier = Modifier
                                .size(32.dp)
                                .clickableSingle{
                                    onPlayerInteraction()
                                }
                                .padding(3.dp)
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    // Seeker row
                    if (!isPrepared) {
                        ShimmerBox(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                        )
                    } else {
                        val progress = if (isSeeking) seekValue
                        else currentMs.toFloat() / durationMs.toFloat()

                        Slider(
                            value = progress.coerceIn(0f, 1f),
                            onValueChange = { v ->
                                isSeeking = true
                                seekValue = v
                            },
                            onValueChangeFinished = {
                                onPlayerInteraction()
                                runCatching {
                                    player.seekTo((seekValue * durationMs).toLong())
                                    currentMs = (seekValue * durationMs).toInt()
                                }
                                isSeeking = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = SliderDefaults.colors(
                                thumbColor = TextPrimary,
                                activeTrackColor = TextPrimary,
                                inactiveTrackColor = Gray500,
                                activeTickColor = Color.Transparent,
                                inactiveTickColor = Color.Transparent
                            ),
                            thumb = {
                                SliderDefaults.Thumb(
                                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                    thumbSize = androidx.compose.ui.unit.DpSize(18.dp, 18.dp),
                                    colors = SliderDefaults.colors(thumbColor = TextPrimary)
                                )
                            },
                            track = { sliderState ->
                                SliderDefaults.Track(
                                    sliderState = sliderState,
                                    modifier = Modifier.height(4.dp),
                                    colors = SliderDefaults.colors(
                                        activeTrackColor = TextPrimary,
                                        inactiveTrackColor = Gray500,
                                        activeTickColor = Color.Transparent,
                                        inactiveTickColor = Color.Transparent
                                    ),
                                    drawStopIndicator = null
                                )
                            }
                        )

//                            // Realtime countdown — remaining time, updates every 200ms
//                            val remainingMs = if (isSeeking)
//                                ((1f - seekValue) * durationMs).toLong().coerceAtLeast(0L)
//                            else
//                                (durationMs - currentMs).toLong().coerceAtLeast(0L)
//                            Text(
//                                text = formatDurationMmSs(remainingMs),
//                                fontSize = 13.sp,
//                                color = TextSecondary,
//                                modifier = Modifier.width(36.dp)
//                            )
                    }


                    // ── CTA button ────────────────────────────────────
                    // Hidden (shrink + fade) while user scrolls the songs list to
                    // discover other tracks; revealed again on any player interaction.
                    AnimatedVisibility(
                        visible = isCtaVisible,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(50))
                            .background(Primary)
                            .clickableSingle(onClick = {
                                Analytics.trackSongSelect(
                                    songId = song.id.toString(),
                                    songName = song.name,
                                    location = location
                                )
                                Analytics.trackCreationStart(AnalyticsEvent.Value.Location.SONG)
                                viewModel.onUseToCreateClick(onProceed = onUseToCreate)
                            })
                            .padding(14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            if (!isSongUnlocked) {
                                // Show ad badge instead of slideshow icon
                                AdBadge(
                                    style = AdBadgeStyle.Small(
                                        textColor = Primary,
                                        backgroundColor = TextOnPrimary
                                    )
                                )
                            } else {
                                Icon(
                                    painter = painterResource(R.drawable.ic_lead_search),
                                    contentDescription = null,
                                    tint = TextOnPrimary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = stringResource(
                                    if (isSongUnlocked) R.string.music_player_try_it
                                    else R.string.music_player_free_unlock
                                ),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextOnPrimary
                            )
                        }
                    }
                    }
                }
            }
            // Standard ad loading overlay - covers entire fullscreen sheet
            AdsLoadingOverlay()
        }  // End Column

    }  // End Box

    // Pause/resume when Activity loses focus (AOA, interstitial ads)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        var wasPlayingBeforeActivityPause = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    wasPlayingBeforeActivityPause = player.isPlaying
                    if (player.isPlaying) {
                        player.pause()
                        isPlaying = false
                    }
                }
                Lifecycle.Event.ON_RESUME -> {
                    if (wasPlayingBeforeActivityPause) {
                        player.play()
                        isPlaying = true
                    }
                    wasPlayingBeforeActivityPause = false
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Pause when trending popup shows; auto-resume only when user dismisses it (X).
    // On CTA, popupUserDismissEvent does NOT fire — music stays paused while
    // user navigates to TemplatePreviewer.
    LaunchedEffect(isPopupShowing) {
        if (isPopupShowing) {
            wasPlayingBeforePopup = player.isPlaying
            if (player.isPlaying) {
                player.pause()
                isPlaying = false
            }
        }
    }
    LaunchedEffect(Unit) {
        trendingPopupCoordinator.popupUserDismissEvent.collect {
            if (wasPlayingBeforePopup) {
                player.play()
                isPlaying = true
            }
            wasPlayingBeforePopup = false
        }
    }

    // Handle rewarded ad presentation
    RewardedAdPresenter(
        shouldPresent = shouldPresentAd,
        placement = AdPlacement.REWARD_UNLOCK_SONG,
        adsLoaderService = adsLoaderService,
        onRewardEarned = viewModel::onRewardEarned,
        onAdFailed = viewModel::onAdFailed,
        onAdShown = {
            wasPlayingBeforeAd = player.isPlaying
            if (player.isPlaying) {
                player.pause()
                isPlaying = false
                Log.d("MusicPlayerBottomSheet", "Paused music for ad presentation")
            }
        },
        onAdClosed = {
            if (wasPlayingBeforeAd) {
                player.play()
            }
            wasPlayingBeforeAd = false
        }
    )
}

// ============================================
// HELPERS
// ============================================

private fun formatDurationMmSs(durationMs: Long): String {
    val totalSec = durationMs / 1000
    val minutes = totalSec / 60
    val seconds = totalSec % 60
    return "%02d:%02d".format(minutes, seconds)
}

// ============================================
// EQUALIZER BARS — animated when playing
// ============================================

@Composable
private fun EqualizerBars(isPlaying: Boolean) {
    val transition = rememberInfiniteTransition(label = "eq")

    val bar1 by transition.animateFloat(
        initialValue = 0.3f, targetValue = 1.0f,
        animationSpec = InfiniteRepeatableSpec(tween(400), repeatMode = RepeatMode.Reverse),
        label = "b1"
    )
    val bar2 by transition.animateFloat(
        initialValue = 0.8f, targetValue = 0.2f,
        animationSpec = InfiniteRepeatableSpec(tween(350), repeatMode = RepeatMode.Reverse),
        label = "b2"
    )
    val bar3 by transition.animateFloat(
        initialValue = 0.5f, targetValue = 0.9f,
        animationSpec = InfiniteRepeatableSpec(tween(500), repeatMode = RepeatMode.Reverse),
        label = "b3"
    )

    val heights = if (isPlaying) listOf(bar1, bar2, bar3) else listOf(0.3f, 0.5f, 0.4f)

    Row(
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        heights.forEach { fraction ->
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(18.dp * fraction)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Primary)
            )
        }
    }
}
