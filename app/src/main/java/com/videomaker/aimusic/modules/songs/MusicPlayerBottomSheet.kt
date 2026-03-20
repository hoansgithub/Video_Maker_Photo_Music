package com.videomaker.aimusic.modules.songs

import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Slideshow
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.videomaker.aimusic.R
import com.videomaker.aimusic.domain.model.MusicSong
import com.videomaker.aimusic.ui.components.AppAsyncImage
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

// ============================================
// MUSIC PLAYER BOTTOM SHEET
// ============================================

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicPlayerBottomSheet(
    song: MusicSong,
    cacheDataSourceFactory: CacheDataSource.Factory,
    onDismiss: () -> Unit,
    onUseToCreate: () -> Unit,
) {
    var isPlaying  by remember { mutableStateOf(false) }
    var isPrepared by remember { mutableStateOf(false) }
    var currentMs  by remember { mutableIntStateOf(0) }
    var durationMs by remember { mutableIntStateOf((song.durationMs ?: 0).coerceAtLeast(1)) }
    var isSeeking  by remember { mutableStateOf(false) }
    var seekValue  by remember { mutableFloatStateOf(0f) }

    val context = LocalContext.current
    // Player is created once per sheet open and released on close.
    // CacheDataSource.Factory routes playback through the 50 MB disk cache
    // so the same preview URL is only downloaded once across sessions.
    val player = remember {
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(cacheDataSourceFactory))
            .build()
    }

    // ── ExoPlayer lifecycle ────────────────────────────────────────────────
    DisposableEffect(song.id) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                when (state) {
                    Player.STATE_READY -> {
                        durationMs = player.duration.coerceAtLeast(1).toInt()
                        player.play()
                        isPlaying = true
                        isPrepared = true
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

        val url = song.previewUrl.ifEmpty { song.mp3Url }
        if (url.isNotEmpty()) {
            player.addListener(listener)
            runCatching {
                player.setMediaItem(MediaItem.fromUri(url))
                player.prepare()
            }
        }
        onDispose {
            player.removeListener(listener)
            player.release()
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

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SurfaceDark,
        scrimColor = Black60,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Gray600)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 40.dp)
        ) {
            // ── Title row ─────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.music_player_title),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.close),
                        tint = TextPrimary
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── Song info + seeker card ────────────────────────
            // ModalBottomSheet runs in its own window so shimmer needs its
            // own ProvideShimmerEffect — no ancestor provides it here.
            ProvideShimmerEffect {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(PlayerCardBackground)
                        .padding(12.dp)
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
                            Text(
                                text = song.name,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = song.artist,
                                fontSize = 13.sp,
                                color = TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Spacer(Modifier.width(12.dp))

                        EqualizerBars(isPlaying = isPlaying)
                    }

                    Spacer(Modifier.height(12.dp))

                    // Seeker row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (!isPrepared) {
                            // Loading state — shimmer placeholders
                            ShimmerBox(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                            )
                            Spacer(Modifier.width(8.dp))
                            ShimmerBox(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp))
                            )
                            Spacer(Modifier.width(8.dp))
                            ShimmerBox(
                                modifier = Modifier
                                    .width(36.dp)
                                    .height(13.dp)
                                    .clip(RoundedCornerShape(4.dp))
                            )
                        } else {
                            // Play/pause button
                            IconButton(
                                onClick = {
                                    when {
                                        player.isPlaying -> player.pause()
                                        player.playbackState == Player.STATE_ENDED -> {
                                            player.seekTo(0)
                                            currentMs = 0
                                            player.play()
                                        }
                                        else -> player.play()
                                    }
                                },
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(White16, CircleShape)
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause
                                                  else Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = TextPrimary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            Spacer(Modifier.width(8.dp))

                            val progress = if (isSeeking) seekValue
                                           else currentMs.toFloat() / durationMs.toFloat()

                            Slider(
                                value = progress.coerceIn(0f, 1f),
                                onValueChange = { v ->
                                    isSeeking = true
                                    seekValue = v
                                },
                                onValueChangeFinished = {
                                    runCatching {
                                        player.seekTo((seekValue * durationMs).toLong())
                                        currentMs = (seekValue * durationMs).toInt()
                                    }
                                    isSeeking = false
                                },
                                modifier = Modifier.weight(1f),
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

                            Spacer(Modifier.width(8.dp))

                            // Realtime countdown — remaining time, updates every 200ms
                            val remainingMs = if (isSeeking)
                                ((1f - seekValue) * durationMs).toLong().coerceAtLeast(0L)
                            else
                                (durationMs - currentMs).toLong().coerceAtLeast(0L)
                            Text(
                                text = formatDurationMmSs(remainingMs),
                                fontSize = 13.sp,
                                color = TextSecondary,
                                modifier = Modifier.width(36.dp)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // ── CTA button ────────────────────────────────────
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Primary)
                    .clickable(onClick = onUseToCreate)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Slideshow,
                        contentDescription = null,
                        tint = TextOnPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.music_player_use_to_create),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextOnPrimary
                    )
                }
            }
        }
    }
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
                    .height(20.dp * fraction)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Primary)
            )
        }
    }
}