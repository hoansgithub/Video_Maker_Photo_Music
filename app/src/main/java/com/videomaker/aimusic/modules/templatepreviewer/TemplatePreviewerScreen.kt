package com.videomaker.aimusic.modules.templatepreviewer

import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.videomaker.aimusic.R
import com.videomaker.aimusic.ui.theme.Primary
import com.videomaker.aimusic.ui.theme.TextOnPrimary
import com.videomaker.aimusic.ui.theme.White16
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import coil.compose.SubcomposeAsyncImage
import coil.decode.BitmapFactoryDecoder
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Precision
import com.videomaker.aimusic.domain.model.VideoTemplate
import com.videomaker.aimusic.ui.components.ProvideShimmerEffect
import com.videomaker.aimusic.ui.components.ShimmerPlaceholder
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

// Virtual page count for infinite-scroll illusion.
private const val VIRTUAL_PAGE_COUNT = 10_000

private fun initialVirtualPage(initialPage: Int, templateCount: Int): Int {
    if (templateCount == 0) return VIRTUAL_PAGE_COUNT / 2
    val mid = VIRTUAL_PAGE_COUNT / 2
    return (mid / templateCount) * templateCount + initialPage
}

// ============================================
// SCREEN
// ============================================

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun TemplatePreviewerScreen(
    viewModel: TemplatePreviewerViewModel,
    audioDataSourceFactory: CacheDataSource.Factory,
    onNavigateToEditor: (String) -> Unit,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val navigationEvent by viewModel.navigationEvent.collectAsStateWithLifecycle()
    val currentSong by viewModel.currentSong.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current

    LaunchedEffect(navigationEvent) {
        navigationEvent?.let { event ->
            when (event) {
                is TemplatePreviewerNavigationEvent.NavigateToEditor -> onNavigateToEditor(event.projectId)
                is TemplatePreviewerNavigationEvent.NavigateBack -> onNavigateBack()
            }
            viewModel.onNavigationHandled()
        }
    }

    // Single ExoPlayer for the entire screen — crossfaded on page change
    val player = remember {
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(audioDataSourceFactory))
            .build()
    }

    DisposableEffect(Unit) {
        onDispose { player.release() }
    }

    // Pause/resume on app background
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> player.pause()
                Lifecycle.Event.ON_START -> if (player.playbackState == Player.STATE_READY) player.play()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Crossfade audio on song change.
    // Loading state: keep current track playing while the next song is being fetched.
    // Ready: fade out → swap track → fade in.
    // None: fade out and stop.
    LaunchedEffect(currentSong) {
        when (val state = currentSong) {
            is SongLoadState.Loading -> Unit // keep playing previous track

            is SongLoadState.None -> {
                fadeVolume(player, to = 0f)
                player.stop()
            }

            is SongLoadState.Ready -> {
                val url = state.song.previewUrl.ifEmpty { state.song.mp3Url }
                if (url.isEmpty()) { player.stop(); return@LaunchedEffect }

                fadeVolume(player, to = 0f)
                player.setMediaItem(MediaItem.fromUri(url))
                player.repeatMode = Player.REPEAT_MODE_ONE
                player.prepare()
                player.play()
                if (awaitPlayerReady(player)) {
                    fadeVolume(player, to = 1f)
                } else {
                    player.volume = 1f
                }
            }
        }
    }

    when (val state = uiState) {
        is TemplatePreviewerUiState.Loading -> {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color.White)
            }
        }
        is TemplatePreviewerUiState.Ready -> {
            TemplatePreviewerReadyContent(
                state = state,
                currentSong = currentSong,
                onPageChanged = viewModel::onPageChanged,
                onUseThisTemplate = viewModel::onUseThisTemplate,
                onNavigateBack = viewModel::onNavigateBack
            )
        }
        is TemplatePreviewerUiState.Error -> {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = state.message,
                        color = Color.White,
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                    Button(onClick = viewModel::onNavigateBack) { Text("Go Back") }
                }
            }
        }
    }
}

// ============================================
// READY CONTENT — virtual infinite vertical pager
// ============================================

@Composable
private fun TemplatePreviewerReadyContent(
    state: TemplatePreviewerUiState.Ready,
    currentSong: SongLoadState,
    onPageChanged: (Int) -> Unit,
    onUseThisTemplate: (VideoTemplate) -> Unit,
    onNavigateBack: () -> Unit
) {
    val templates = state.templates
    val pagerState = rememberPagerState(
        initialPage = initialVirtualPage(state.initialPage, templates.size),
        pageCount = { VIRTUAL_PAGE_COUNT }
    )

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .drop(1)
            .collect { onPageChanged(it) }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        VerticalPager(
            state = pagerState,
            beyondViewportPageCount = 1,
            modifier = Modifier.fillMaxSize(),
            key = { pageIndex -> templates[pageIndex % templates.size].id }
        ) { pageIndex ->
            // Only animate thumbnail once music is loaded — thumbnail + audio start together.
            // While SongLoadState.Loading: static first frame shown (shimmer in music row).
            // SongLoadState.None means this template has no song — animate immediately.
            val musicReady = currentSong !is SongLoadState.Loading
            val isCurrentPage = pageIndex == pagerState.settledPage
                && !pagerState.isScrollInProgress
                && musicReady
            TemplateThumbnailPage(
                template = templates[pageIndex % templates.size],
                isCurrentPage = isCurrentPage
            )
        }

        // Top gradient scrim
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent)
                    )
                )
                .align(Alignment.TopCenter)
        )

        // Bottom gradient scrim
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))
                    )
                )
                .align(Alignment.BottomCenter)
        )

        // Back button
        IconButton(
            onClick = onNavigateBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(start = 8.dp, top = 8.dp)
                .size(48.dp)
                .background(color = Color.Black.copy(alpha = 0.4f), shape = CircleShape)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = Color.White
            )
        }

        // Bottom bar — music info + template name + CTA
        ProvideShimmerEffect {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Music info capsule
                MusicInfoCapsule(currentSong = currentSong)

                // Template name
                val currentTemplate = templates.getOrNull(pagerState.settledPage % templates.size)
                if (currentTemplate != null) {
                    Text(
                        text = currentTemplate.name,
                        color = Color.White.copy(alpha = 0.85f),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // CTA button
                val ctaShape = RoundedCornerShape(999.dp)
                Button(
                    onClick = {
                        val template = templates.getOrNull(pagerState.settledPage % templates.size) ?: return@Button
                        onUseThisTemplate(template)
                    },
                    enabled = !state.isCreatingProject,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .shadow(
                            elevation = 12.dp,
                            shape = ctaShape,
                            ambientColor = Primary,
                            spotColor = Primary
                        ),
                    shape = ctaShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Primary,
                        contentColor = TextOnPrimary,
                        disabledContainerColor = Primary.copy(alpha = 0.7f),
                        disabledContentColor = TextOnPrimary
                    ),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 24.dp, vertical = 0.dp
                    )
                ) {
                    if (state.isCreatingProject) {
                        CircularProgressIndicator(
                            color = TextOnPrimary,
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_circle_plus),
                            contentDescription = null,
                            tint = Color.Unspecified,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Use This Template",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = TextOnPrimary
                        )
                    }
                }
            }
        }
    }
}

// ============================================
// MUSIC INFO CAPSULE — vertical layout, shimmer while loading
// ============================================

/** Formats duration millis as "00:12" (zero-padded minutes and seconds). */
private fun formatDurationMmSs(durationMs: Int): String {
    val totalSec = durationMs / 1000
    val minutes = totalSec / 60
    val seconds = totalSec % 60
    return "%02d:%02d".format(minutes, seconds)
}

@Composable
private fun MusicInfoCapsule(currentSong: SongLoadState) {
    if (currentSong is SongLoadState.None) return

    Column(
        modifier = Modifier
            .wrapContentWidth()
            .background(color = White16, shape = RoundedCornerShape(999.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.MusicNote,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.8f),
            modifier = Modifier.size(14.dp)
        )

        when (currentSong) {
            is SongLoadState.Loading -> {
                ShimmerPlaceholder(
                    modifier = Modifier.width(100.dp).height(10.dp),
                    cornerRadius = 4.dp
                )
                ShimmerPlaceholder(
                    modifier = Modifier.width(36.dp).height(10.dp),
                    cornerRadius = 4.dp
                )
            }
            is SongLoadState.Ready -> {
                Text(
                    text = currentSong.song.name,
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    modifier = Modifier
                        .widthIn(max = 160.dp)
                        .basicMarquee()
                )
                val duration = currentSong.song.durationMs
                if (duration != null && duration > 0) {
                    Text(
                        text = formatDurationMmSs(duration),
                        color = Color.White.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
            is SongLoadState.None -> Unit
        }
    }
}

// ============================================
// SINGLE PAGE — thumbnail image
// ============================================

@Composable
private fun TemplateThumbnailPage(template: VideoTemplate, isCurrentPage: Boolean) {
    val context = LocalContext.current
    val thumbnailUrl = template.thumbnailPath.ifEmpty { null }

    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        SubcomposeAsyncImage(
            model = ImageRequest.Builder(context)
                .data(thumbnailUrl)
                .diskCachePolicy(CachePolicy.ENABLED)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .memoryCacheKey("${template.id}_${if (isCurrentPage) "anim" else "static"}")
                .precision(Precision.INEXACT)
                .apply {
                    if (!isCurrentPage) {
                        // Static first frame only — bypasses ImageDecoderDecoder (animated WebP)
                        decoderFactory(BitmapFactoryDecoder.Factory())
                    }
                }
                .crossfade(true)
                .build(),
            contentDescription = template.name,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            loading = {
                Box(
                    modifier = Modifier.fillMaxSize().background(Color.DarkGray),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(32.dp))
                }
            },
            error = {
                Box(modifier = Modifier.fillMaxSize().background(Color.DarkGray))
            }
        )
    }
}

// ============================================
// AUDIO HELPERS
// ============================================

/** Smoothly animate player volume to [to] over [durationMs]. */
private suspend fun fadeVolume(player: ExoPlayer, to: Float, durationMs: Long = 250) {
    val from = player.volume
    if (from == to) return
    val steps = 10
    val stepMs = durationMs / steps
    repeat(steps) { i ->
        player.volume = from + (to - from) * ((i + 1).toFloat() / steps)
        delay(stepMs)
    }
    player.volume = to
}

/** Suspend until the player reaches STATE_READY or fails. Returns true on ready. */
@androidx.annotation.OptIn(UnstableApi::class)
private suspend fun awaitPlayerReady(player: ExoPlayer): Boolean {
    if (player.playbackState == Player.STATE_READY) return true
    return suspendCancellableCoroutine { cont ->
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                // STATE_IDLE excluded: it fires spuriously right after prepare() before
                // buffering begins. Errors are handled exclusively by onPlayerError.
                if (state == Player.STATE_READY || state == Player.STATE_ENDED) {
                    player.removeListener(this)
                    if (cont.isActive) cont.resume(state == Player.STATE_READY)
                }
            }
            override fun onPlayerError(error: PlaybackException) {
                player.removeListener(this)
                if (cont.isActive) cont.resume(false)
            }
        }
        player.addListener(listener)
        cont.invokeOnCancellation { player.removeListener(listener) }
    }
}