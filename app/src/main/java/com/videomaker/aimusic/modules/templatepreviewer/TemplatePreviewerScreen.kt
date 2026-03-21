package com.videomaker.aimusic.modules.templatepreviewer

import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.videomaker.aimusic.R
import com.videomaker.aimusic.domain.model.AspectRatio
import com.videomaker.aimusic.ui.components.PrimaryButton
import com.videomaker.aimusic.ui.theme.Black60
import com.videomaker.aimusic.ui.theme.Primary
import com.videomaker.aimusic.ui.theme.SurfaceDark
import com.videomaker.aimusic.ui.theme.SurfaceDarkVariant
import com.videomaker.aimusic.ui.theme.White16
import com.videomaker.aimusic.ui.theme.White40
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
    onNavigateToAssetPicker: (template: com.videomaker.aimusic.domain.model.VideoTemplate, overrideSongId: Long, aspectRatio: AspectRatio) -> Unit,
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
                is TemplatePreviewerNavigationEvent.NavigateToAssetPicker ->
                    onNavigateToAssetPicker(event.template, event.overrideSongId, event.aspectRatio)
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

    // Real duration read from ExoPlayer after playback is ready — not from DB
    var playerDurationMs by remember { mutableStateOf<Long?>(null) }

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
                playerDurationMs = null
                fadeVolume(player, to = 0f)
                player.stop()
            }

            is SongLoadState.Ready -> {
                playerDurationMs = null  // clear stale duration until new track is ready
                // Use full track (mp3Url) for consistency with Editor, not short preview clip
                val url = state.song.mp3Url.ifEmpty { state.song.previewUrl }
                if (url.isEmpty()) { player.stop(); return@LaunchedEffect }

                fadeVolume(player, to = 0f)
                player.setMediaItem(MediaItem.fromUri(url))
                player.repeatMode = Player.REPEAT_MODE_ONE
                player.prepare()
                player.play()
                if (awaitPlayerReady(player)) {
                    val dur = player.duration
                    playerDurationMs = if (dur != C.TIME_UNSET && dur > 0) dur else null
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
                playerDurationMs = playerDurationMs,
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TemplatePreviewerReadyContent(
    state: TemplatePreviewerUiState.Ready,
    currentSong: SongLoadState,
    playerDurationMs: Long?,
    onPageChanged: (Int) -> Unit,
    onUseThisTemplate: (VideoTemplate, AspectRatio) -> Unit,
    onNavigateBack: () -> Unit
) {
    val templates = state.templates
    val pagerState = rememberPagerState(
        initialPage = initialVirtualPage(state.initialPage, templates.size),
        pageCount = { VIRTUAL_PAGE_COUNT }
    )

    // Bottom sheet state — non-null while the sheet is visible
    var pendingTemplate by remember { mutableStateOf<VideoTemplate?>(null) }

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

        // Top bar — back button (left) + template name (right)
        val currentTemplateName = templates.getOrNull(pagerState.settledPage % templates.size)?.name
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 8.dp, end = 16.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onNavigateBack,
                modifier = Modifier
                    .size(48.dp)
                    .background(color = Color.Black.copy(alpha = 0.4f), shape = CircleShape)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }
            if (currentTemplateName != null) {
                Text(
                    text = currentTemplateName,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1f)
                )
            }
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
                MusicInfoCapsule(currentSong = currentSong, playerDurationMs = playerDurationMs)

                // CTA button — spinner while music loads or project is being created.
                // Enabled once music is ready (or template has no music) and not yet creating.
                val ctaLoading = state.isCreatingProject || currentSong is SongLoadState.Loading
                val ctaEnabled = currentSong !is SongLoadState.Loading && !state.isCreatingProject
                PrimaryButton(
                    text = "Use This Template",
                    onClick = {
                        val template = templates.getOrNull(pagerState.settledPage % templates.size) ?: return@PrimaryButton
                        pendingTemplate = template
                    },
                    enabled = ctaEnabled,
                    isLoading = ctaLoading,
                    leadingIcon = {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_circle_plus),
                            contentDescription = null,
                            tint = Color.Unspecified,
                            modifier = Modifier.size(24.dp)
                        )
                    },
                    modifier = Modifier
                        .wrapContentWidth()
                        .height(52.dp)
                )
            }
        }

        // Ratio selection bottom sheet
        val template = pendingTemplate
        if (template != null) {
            SelectRatioBottomSheet(
                defaultRatio = aspectRatioFromString(template.aspectRatio),
                onDismiss = { pendingTemplate = null },
                onConfirm = { selectedRatio ->
                    pendingTemplate = null
                    onUseThisTemplate(template, selectedRatio)
                }
            )
        }
    }
}

// ============================================
// RATIO SELECTION BOTTOM SHEET
// ============================================

private fun aspectRatioFromString(value: String): AspectRatio = when (value) {
    "16:9" -> AspectRatio.RATIO_16_9
    "9:16" -> AspectRatio.RATIO_9_16
    "4:5" -> AspectRatio.RATIO_4_5
    "1:1" -> AspectRatio.RATIO_1_1
    else -> AspectRatio.RATIO_9_16
}

private val AspectRatio.shortLabel: String
    get() = when (this) {
        AspectRatio.RATIO_16_9 -> "16:9"
        AspectRatio.RATIO_9_16 -> "9:16"
        AspectRatio.RATIO_4_5 -> "4:5"
        AspectRatio.RATIO_1_1 -> "1:1"
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectRatioBottomSheet(
    defaultRatio: AspectRatio,
    onDismiss: () -> Unit,
    onConfirm: (AspectRatio) -> Unit
) {
    var selected by remember { mutableStateOf(defaultRatio) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SurfaceDark,
        dragHandle = null,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(top = 24.dp, bottom = 16.dp)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Text(
                text = "Select Video Ratio",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                listOf(
                    AspectRatio.RATIO_16_9,
                    AspectRatio.RATIO_9_16,
                    AspectRatio.RATIO_4_5,
                    AspectRatio.RATIO_1_1
                ).forEach { ratio ->
                    RatioOptionCard(
                        ratio = ratio,
                        isSelected = ratio == selected,
                        onClick = { selected = ratio },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            PrimaryButton(
                text = "Create now",
                onClick = { onConfirm(selected) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            )
        }
    }
}

@Composable
private fun RatioOptionCard(
    ratio: AspectRatio,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = if (isSelected) Primary else White16
    val borderWidth = if (isSelected) 1.5.dp else 1.dp

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .border(borderWidth, borderColor, RoundedCornerShape(12.dp))
            .background(SurfaceDarkVariant)
            .clickable { onClick() }
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            AspectRatioIcon(ratio = ratio, isSelected = isSelected)
            Text(
                text = ratio.shortLabel,
                color = if (isSelected) Primary else Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun AspectRatioIcon(ratio: AspectRatio, isSelected: Boolean) {
    val color = if (isSelected) Primary else White40
    val maxSize = 40.dp
    val (iconW, iconH) = when (ratio) {
        AspectRatio.RATIO_16_9 -> maxSize to (maxSize * (9f / 16f))
        AspectRatio.RATIO_9_16 -> (maxSize * (9f / 16f)) to maxSize
        AspectRatio.RATIO_4_5 -> (maxSize * (4f / 5f)) to maxSize
        AspectRatio.RATIO_1_1 -> maxSize to maxSize
    }
    Box(
        modifier = Modifier
            .size(width = iconW, height = iconH)
            .border(1.5.dp, color, RoundedCornerShape(3.dp))
    )
}

// ============================================
// MUSIC INFO CAPSULE — vertical layout, shimmer while loading
// ============================================

/** Formats duration millis as "00:12" (zero-padded minutes and seconds). */
private fun formatDurationMmSs(durationMs: Long): String {
    val totalSec = durationMs / 1000
    val minutes = totalSec / 60
    val seconds = totalSec % 60
    return "%02d:%02d".format(minutes, seconds)
}

@Composable
private fun MusicInfoCapsule(currentSong: SongLoadState, playerDurationMs: Long?) {
    if (currentSong is SongLoadState.None) return

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(color = Black60, shape = RoundedCornerShape(999.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_double_notes),
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.8f),
            modifier = Modifier.size(16.dp)
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
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    modifier = Modifier
                        .weight(1f)
                        .basicMarquee()
                )
                Text(
                    text = if (playerDurationMs != null && playerDurationMs > 0)
                        formatDurationMmSs(playerDurationMs)
                    else "--:--",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal
                )
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

// ============================================
// PREVIEWS
// ============================================

private val previewTemplates = listOf(
    VideoTemplate(id = "t1", name = "Golden Hour Vibes", songId = 1L, effectSetId = "classic"),
    VideoTemplate(id = "t2", name = "Summer Memories", songId = 2L, effectSetId = "cinematic"),
    VideoTemplate(id = "t3", name = "City Lights", songId = 0L, effectSetId = "minimal"),
)

private val previewSongReady = SongLoadState.Ready(
    song = com.videomaker.aimusic.domain.model.MusicSong(
        id = 1L,
        name = "Golden Hour",
        artist = "Loving Caliber",
        durationMs = 182000
    ),
    nonce = 0
)

@androidx.compose.ui.tooling.preview.Preview(
    name = "Ready — music loaded",
    showBackground = true,
    backgroundColor = 0xFF000000,
    device = "spec:width=390dp,height=844dp,dpi=460"
)
@Composable
private fun PreviewTemplatePreviewerReady() {
    com.videomaker.aimusic.ui.theme.VideoMakerTheme {
        ProvideShimmerEffect {
            TemplatePreviewerReadyContent(
                state = TemplatePreviewerUiState.Ready(
                    templates = previewTemplates,
                    initialPage = 0
                ),
                currentSong = previewSongReady,
                playerDurationMs = 182000L,
                onPageChanged = {},
                onUseThisTemplate = { _, _ -> },
                onNavigateBack = {}
            )
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(
    name = "Ready — music loading (shimmer)",
    showBackground = true,
    backgroundColor = 0xFF000000,
    device = "spec:width=390dp,height=844dp,dpi=460"
)
@Composable
private fun PreviewTemplatePreviewerMusicLoading() {
    com.videomaker.aimusic.ui.theme.VideoMakerTheme {
        ProvideShimmerEffect {
            TemplatePreviewerReadyContent(
                state = TemplatePreviewerUiState.Ready(
                    templates = previewTemplates,
                    initialPage = 0
                ),
                currentSong = SongLoadState.Loading,
                playerDurationMs = null,
                onPageChanged = {},
                onUseThisTemplate = { _, _ -> },
                onNavigateBack = {}
            )
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(
    name = "Ready — no music",
    showBackground = true,
    backgroundColor = 0xFF000000,
    device = "spec:width=390dp,height=844dp,dpi=460"
)
@Composable
private fun PreviewTemplatePreviewerNoMusic() {
    com.videomaker.aimusic.ui.theme.VideoMakerTheme {
        ProvideShimmerEffect {
            TemplatePreviewerReadyContent(
                state = TemplatePreviewerUiState.Ready(
                    templates = previewTemplates,
                    initialPage = 2  // "City Lights" has songId=0
                ),
                currentSong = SongLoadState.None,
                playerDurationMs = null,
                onPageChanged = {},
                onUseThisTemplate = { _, _ -> },
                onNavigateBack = {}
            )
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(
    name = "Ready — creating project",
    showBackground = true,
    backgroundColor = 0xFF000000,
    device = "spec:width=390dp,height=844dp,dpi=460"
)
@Composable
private fun PreviewTemplatePreviewerCreating() {
    com.videomaker.aimusic.ui.theme.VideoMakerTheme {
        ProvideShimmerEffect {
            TemplatePreviewerReadyContent(
                state = TemplatePreviewerUiState.Ready(
                    templates = previewTemplates,
                    initialPage = 0,
                    isCreatingProject = true
                ),
                currentSong = previewSongReady,
                playerDurationMs = 182000L,
                onPageChanged = {},
                onUseThisTemplate = { _, _ -> },
                onNavigateBack = {}
            )
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(
    name = "Loading state",
    showBackground = true,
    backgroundColor = 0xFF000000
)
@Composable
private fun PreviewTemplatePreviewerLoading() {
    com.videomaker.aimusic.ui.theme.VideoMakerTheme {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = Color.White)
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(
    name = "Error state",
    showBackground = true,
    backgroundColor = 0xFF000000
)
@Composable
private fun PreviewTemplatePreviewerError() {
    com.videomaker.aimusic.ui.theme.VideoMakerTheme {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Failed to load templates. Please try again.",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp)
                )
                Button(onClick = {}) { Text("Go Back") }
            }
        }
    }
}