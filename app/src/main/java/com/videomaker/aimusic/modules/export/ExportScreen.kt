package com.videomaker.aimusic.modules.export

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.shadow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.videomaker.aimusic.R
import com.videomaker.aimusic.domain.model.AspectRatio
import com.videomaker.aimusic.domain.model.VideoQuality
import com.videomaker.aimusic.domain.model.VideoTemplate
import com.videomaker.aimusic.modules.rate.RatingFeedbackPopup
import com.videomaker.aimusic.modules.rate.RatingSatisfactionPopup
import com.videomaker.aimusic.modules.rate.RatingStarsPopup
import com.videomaker.aimusic.modules.rate.RatingStep
import com.videomaker.aimusic.modules.rate.RatingStep.*
import com.videomaker.aimusic.ui.components.ProcessToast
import com.videomaker.aimusic.ui.components.ProcessToastState
import com.videomaker.aimusic.ui.components.QualityPicker
import com.videomaker.aimusic.ui.components.ShimmerPlaceholder
import com.videomaker.aimusic.ui.components.TemplateCard
import com.videomaker.aimusic.ui.theme.BackgroundLight
import com.videomaker.aimusic.ui.theme.Neutral_N800
import com.videomaker.aimusic.ui.theme.SplashBackground
import com.videomaker.aimusic.ui.theme.SurfaceDark
import com.videomaker.aimusic.ui.theme.VideoMakerTheme
import kotlinx.coroutines.delay
import java.io.File

/**
 * Responsive sizing configuration for export buttons
 */
private data class Responsive(
    val iconSize: Dp,
    val fontSize: TextUnit,
    val buttonHeight: Dp,
    val horizontalPadding: Dp,
    val buttonSpacing: Dp,
    val iconSpacing: Dp,
    val contentPadding: PaddingValues
)

/**
 * ExportScreen - Full-screen blocking UI for video export
 *
 * Shows progress indicator during export and blocks all interactions.
 * Follows CLAUDE.md patterns:
 * - collectAsStateWithLifecycle for StateFlow
 * - LaunchedEffect(Unit) for navigation events
 */
@Composable
fun ExportScreen(
    viewModel: ExportViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToHomeMyVideos: () -> Unit,
    onNavigateToTemplateDetail: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val navigationEvent by viewModel.navigationEvent.collectAsStateWithLifecycle()
    val thumbnailUri by viewModel.thumbnailUri.collectAsStateWithLifecycle()
    val aspectRatio by viewModel.aspectRatio.collectAsStateWithLifecycle()
    val currentQuality by viewModel.currentQuality.collectAsStateWithLifecycle()
    val featuredTemplatesState by viewModel.featuredTemplatesState.collectAsStateWithLifecycle()
    val saveToastState by viewModel.saveToastState.collectAsStateWithLifecycle()
    val ratingStep by viewModel.ratingStep.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var shareErrorMessage by remember { mutableStateOf<String?>(null) }

    // Cancel export when app goes to background
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    // Cancel export to free resources when app is not visible
                    if (uiState is ExportUiState.Processing || uiState is ExportUiState.Preparing) {
                        viewModel.cancelExport()
                    }
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Handle navigation events - StateFlow-based (Google recommended pattern)
    // Observe navigationEvent StateFlow and call onNavigationHandled() after navigating
    LaunchedEffect(navigationEvent) {
        navigationEvent?.let { event ->
            when (event) {
                is ExportNavigationEvent.NavigateBack -> onNavigateBack()
                is ExportNavigationEvent.NavigateToHomeMyVideos -> onNavigateToHomeMyVideos()
                is ExportNavigationEvent.NavigateToTemplateDetail -> {
                    onNavigateToTemplateDetail(event.templateId)
                }
            }
            viewModel.onNavigationHandled()
        }
    }

    // Launch Play Store for high-star rating — one-time event requiring Activity context
    LaunchedEffect(Unit) {
        viewModel.launchPlayStoreEvent.collect {
            val packageName = context.packageName
            val marketIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(marketIntent)
            } catch (e: ActivityNotFoundException) {
                // Fallback: open Play Store in browser if Play Store app not installed
                context.startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://play.google.com/store/apps/details?id=$packageName")
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }
    }

    // Block back navigation during processing - no cancel option
    BackHandler(enabled = uiState is ExportUiState.Processing || uiState is ExportUiState.Preparing) {
        // Do nothing - prevent back navigation during export
    }

    // Full-screen blocking container with dark background (#101313 closest = SplashBackground)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SplashBackground),
        contentAlignment = Alignment.Center
    ) {
        when (val state = uiState) {
            is ExportUiState.Preparing -> {
                PreparingContent()
            }

            is ExportUiState.Processing -> {
                ProcessingContent(
                    progress = state.progress,
                    thumbnailUri = thumbnailUri,
                    aspectRatio = aspectRatio
                )
            }

            is ExportUiState.Success -> {
                SuccessContent(
                    outputPath = state.outputPath,
                    savedToGallery = state.savedToGallery,
                    saveError = state.saveError,
                    shareError = shareErrorMessage,
                    aspectRatio = aspectRatio,
                    currentQuality = currentQuality,
                    featuredTemplatesState = featuredTemplatesState,
                    saveToastState = saveToastState,
                    onSaveToGalleryClick = {
                        viewModel.saveToGallery(
                            applicationContext = context.applicationContext,
                            loadingMessage = context.getString(R.string.export_saving_to_gallery),
                            successMessage = context.getString(R.string.export_saved_to_gallery),
                            errorMessage = context.getString(R.string.export_error_save_failed)
                        )
                    },
                    onShareClick = {
                        shareVideo(
                            context = context,
                            outputPath = state.outputPath,
                            onError = { error -> shareErrorMessage = error }
                        )
                    },
                    onQualityChange = { quality ->
                        viewModel.changeQuality(quality)
                    },
                    onDoneClick = viewModel::navigateToHomeMyVideos,
                    onTemplateClick = viewModel::onTemplateClick,
                    onSaveToastDismissed = viewModel::onSaveToastDismissed
                )
            }

            is ExportUiState.Error -> {
                ErrorContent(
                    message = state.message,
                    onRetryClick = viewModel::retryExport,
                    onBackClick = viewModel::navigateBack
                )
            }

            is ExportUiState.Cancelled -> {
                CancelledContent(
                    onBackClick = viewModel::navigateBack
                )
            }
        }

        // Rating popup overlay — renders on top of export content
        AnimatedContent(
            targetState = ratingStep,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "rating_popup_transition"
        ) { step ->
            when (step) {
                Satisfaction -> RatingSatisfactionPopup(
                    onNotReally = viewModel::onNotReally,
                    onGood = viewModel::onGood,
                    onDismiss = viewModel::onRatingDismiss
                )
                Stars -> RatingStarsPopup(
                    onLowRating = viewModel::onLowRating,
                    onHighRating = viewModel::onHighRating,
                    onDismiss = viewModel::onRatingDismiss
                )
                Feedback -> RatingFeedbackPopup(
                    onSubmit = viewModel::onFeedbackSubmit,
                    onDismiss = viewModel::onRatingDismiss
                )
                None -> { /* No popup shown */ }
            }
        }
    }
}

@Composable
private fun PreparingContent() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(80.dp),
            strokeWidth = 6.dp
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = stringResource(R.string.export_preparing),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun ProcessingContent(
    progress: Int,
    thumbnailUri: Uri? = null,
    aspectRatio: AspectRatio = AspectRatio.RATIO_9_16
) {
    // Rotating tips - cycle through 10 messages every 4 seconds
    val tipMessages = listOf(
        stringResource(R.string.export_tip_1),
        stringResource(R.string.export_tip_2),
        stringResource(R.string.export_tip_3),
        stringResource(R.string.export_tip_4),
        stringResource(R.string.export_tip_5),
        stringResource(R.string.export_tip_6),
        stringResource(R.string.export_tip_7),
        stringResource(R.string.export_tip_8),
        stringResource(R.string.export_tip_9),
        stringResource(R.string.export_tip_10)
    )

    var currentTipIndex by remember { mutableStateOf(0) }

    // Rotate tips every 4 seconds
    LaunchedEffect(Unit) {
        while (true) {
            delay(4000)
            currentTipIndex = (currentTipIndex + 1) % tipMessages.size
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(32.dp)
    ) {
        // Top: "Generating" text
        Text(
            text = stringResource(R.string.export_generating),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 40.dp)
        )

        // Center: Thumbnail with progress overlay
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            // Thumbnail with progress overlay (matches video aspect ratio)
            // Use wider width for landscape and square ratios
            val thumbnailWidthFraction = when (aspectRatio) {
                AspectRatio.RATIO_16_9 -> 0.95f // Landscape - almost full width
                AspectRatio.RATIO_1_1 -> 0.8f   // Square - wider
                else -> 0.7f                     // Portrait - default
            }

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth(thumbnailWidthFraction)
                    .aspectRatio(aspectRatio.ratio)
            ) {
                // Real thumbnail with white outer shadow
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .shadow(
                            elevation = 24.dp,
                            shape = RoundedCornerShape(16.dp),
                            ambientColor = Color.White,
                            spotColor = Color.White
                        )
                ) {
                    if (thumbnailUri != null) {
                        AsyncImage(
                            model = thumbnailUri,
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(16.dp)
                                ),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        // Fallback placeholder
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(16.dp)
                                )
                        )
                    }
                }

                // Progress percentage in dark transparent capsule
                Column(
                    modifier = Modifier
                        .background(
                            color = Color.Black.copy(alpha = 0.6f),
                            shape = RoundedCornerShape(24.dp)
                        )
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(36.dp),
                        color = Color.White,
                        strokeWidth = 4.dp,
                        trackColor = Color(0xFF4A4A4A)
                    )
                    Text(
                        text = "$progress%",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Rotating tip message below thumbnail
            Text(
                text = tipMessages[currentTipIndex],
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                lineHeight = 24.sp
            )
        }

        // Warning capsule at bottom
        Row(
            modifier = Modifier
                .background(
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(24.dp)
                )
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = stringResource(R.string.export_dont_close),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun SuccessContent(
    outputPath: String,
    savedToGallery: Boolean,
    saveError: String?,
    shareError: String? = null,
    aspectRatio: AspectRatio = AspectRatio.RATIO_9_16,
    currentQuality: VideoQuality,
    featuredTemplatesState: FeaturedTemplatesState = FeaturedTemplatesState.Loading,
    saveToastState: ProcessToastState? = null,
    onSaveToGalleryClick: () -> Unit,
    onShareClick: () -> Unit,
    onQualityChange: (VideoQuality) -> Unit = {},
    onDoneClick: () -> Unit,
    onTemplateClick: (String) -> Unit = {},
    onSaveToastDismissed: () -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val isPreview = LocalInspectionMode.current

    // Create ExoPlayer without preparing (to avoid blocking main thread)
    // Skip player creation in preview mode to avoid NoClassDefFoundError
    val exoPlayer = if (!isPreview) {
        remember(outputPath) {
            ExoPlayer.Builder(context).build().apply {
                val videoUri = Uri.fromFile(File(outputPath))
                setMediaItem(MediaItem.fromUri(videoUri))
                repeatMode = Player.REPEAT_MODE_ONE
                // Don't call prepare() here - it can block on ConditionVariable
            }
        }
    } else {
        null
    }

    // Prepare player asynchronously to avoid ANR
    // Skip in preview mode
    LaunchedEffect(exoPlayer) {
        exoPlayer?.let { player ->
            // prepare() is non-blocking but can wait on ConditionVariable internally
            // Running in LaunchedEffect ensures it doesn't block composition
            player.prepare()
        }
    }

    // Track playing state from ExoPlayer
    var isPlaying by remember { mutableStateOf(false) }

    // Update isPlaying state and handle auto-play when player is ready
    // Skip in preview mode
    DisposableEffect(exoPlayer) {
        exoPlayer?.let { player ->
            val listener = object : Player.Listener {
                override fun onIsPlayingChanged(playing: Boolean) {
                    isPlaying = playing
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    // Auto-play when player is ready
                    if (playbackState == Player.STATE_READY && !player.isPlaying) {
                        player.play()
                    }
                }
            }
            player.addListener(listener)
            onDispose {
                player.removeListener(listener)
            }
        } ?: onDispose { }
    }

    // Handle lifecycle events - pause/resume playback
    // Skip in preview mode
    DisposableEffect(lifecycleOwner, exoPlayer) {
        exoPlayer?.let { player ->
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_PAUSE -> player.pause()
                    Lifecycle.Event.ON_RESUME -> {
                        // Always auto-play when screen becomes visible and player is ready
                        if (player.playbackState == Player.STATE_READY && !player.isPlaying) {
                            player.play()
                        }
                    }
                    else -> {}
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
                player.release()
            }
        } ?: onDispose { }
    }

    // Get screen dimensions for video size calculation
    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp
    val screenWidth = configuration.screenWidthDp.dp
    val maxVideoHeight = screenHeight * 0.5f

    // Calculate video dimensions respecting max height, max width, and aspect ratio
    val maxVideoWidth = screenWidth - 64.dp // Account for horizontal padding (32dp each side)
    val videoHeightFromWidth = maxVideoWidth / aspectRatio.ratio
    val actualVideoHeight = minOf(videoHeightFromWidth, maxVideoHeight)
    val calculatedWidth = actualVideoHeight * aspectRatio.ratio
    val actualVideoWidth = minOf(calculatedWidth, maxVideoWidth) // Ensure width never exceeds screen

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
        // Top bar with close button (left) and quality selector (center)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Close button (left)
            IconButton(onClick = onDoneClick) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.export_go_back),
                    tint = Color.White
                )
            }

            // Quality picker (center)
            QualityPicker(
                selectedQuality = currentQuality,
                onQualityChange = onQualityChange
            )

            // Empty spacer (right) for balanced layout
            Spacer(modifier = Modifier.width(48.dp))
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Video preview with play/pause button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            contentAlignment = Alignment.Center
        ) {
            // Video player with explicit size to maintain ratio and max height
            // Show placeholder in preview mode
            if (exoPlayer != null) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            player = exoPlayer
                            useController = false // Hide default controls
                            setBackgroundColor(android.graphics.Color.BLACK)
                        }
                    },
                    modifier = Modifier
                        .width(actualVideoWidth)
                        .height(actualVideoHeight)
                        .background(
                            color = Color.Black,
                            shape = RoundedCornerShape(16.dp)
                        )
                        .clip(RoundedCornerShape(18.dp))
                        .border(1.dp, Color.White.copy(0.5f), RoundedCornerShape(18.dp))
                )
            } else {
                // Preview mode - show placeholder
                Box(
                    modifier = Modifier
                        .width(actualVideoWidth)
                        .height(actualVideoHeight)
                        .background(
                            color = Color.Black,
                            shape = RoundedCornerShape(16.dp)
                        )
                )
            }

                // Play/Pause button overlay
                IconButton(
                    onClick = {
                        exoPlayer?.let { player ->
                            if (player.isPlaying) {
                                player.pause()
                            } else {
                                player.play()
                            }
                        }
                    },
                    modifier = Modifier.size(80.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                color = Color.Black.copy(alpha = 0.6f),
                                shape = RoundedCornerShape(40.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = stringResource(if (isPlaying) R.string.editor_pause else R.string.editor_play),
                            tint = Color.White,
                            modifier = Modifier.size(48.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Action buttons row - Share and Save to Gallery (responsive sizing)
            val configuration = LocalConfiguration.current
            val screenWidthDp = configuration.screenWidthDp

            // Responsive sizing based on screen width buckets
            val (iconSize, fontSize, buttonHeight, horizontalPadding, buttonSpacing, iconSpacing, contentPadding) = when {
                screenWidthDp < 340 -> {
                    // Very small screens (e.g., 320dp) - aggressive compression
                    Responsive(
                        iconSize = 16.dp,
                        fontSize = 11.sp,
                        buttonHeight = 44.dp,
                        horizontalPadding = 8.dp,
                        buttonSpacing = 6.dp,
                        iconSpacing = 4.dp,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                    )
                }
                screenWidthDp < 380 -> {
                    // Small screens (e.g., 360dp) - moderate compression
                    Responsive(
                        iconSize = 18.dp,
                        fontSize = 13.sp,
                        buttonHeight = 50.dp,
                        horizontalPadding = 12.dp,
                        buttonSpacing = 8.dp,
                        iconSpacing = 6.dp,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
                    )
                }
                else -> {
                    // Normal and large screens (380dp+) - standard sizing
                    Responsive(
                        iconSize = 20.dp,
                        fontSize = 15.sp,
                        buttonHeight = 56.dp,
                        horizontalPadding = 24.dp,
                        buttonSpacing = 12.dp,
                        iconSpacing = 8.dp,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = horizontalPadding, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(buttonSpacing)
            ) {
                // Share button - left side, double width with text
                Button(
                    onClick = onShareClick,
                    modifier = Modifier
                        .weight(2f)
                        .height(buttonHeight),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Neutral_N800,
                        contentColor = Color.White
                    ),
                    contentPadding = contentPadding
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = null,
                        modifier = Modifier.size(iconSize)
                    )
                    Spacer(modifier = Modifier.width(iconSpacing))
                    Text(
                        text = stringResource(R.string.export_share),
                        fontSize = fontSize,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Save to Gallery button - right side (always enabled, no state change)
                Button(
                    onClick = onSaveToGalleryClick,
                    modifier = Modifier
                        .weight(3f)
                        .height(buttonHeight),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BackgroundLight,
                        contentColor = SurfaceDark
                    ),
                    contentPadding = contentPadding
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = null,
                        modifier = Modifier.size(iconSize)
                    )
                    Spacer(modifier = Modifier.width(iconSpacing))
                    Text(
                        text = stringResource(R.string.export_save),
                        fontSize = fontSize,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Show share error if any (save errors are shown via ProcessToast)
            if (shareError != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = shareError,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp)
                )
            }

            // Try Another Templates section
            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = stringResource(R.string.export_try_another_templates),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Templates grid based on state
            when (featuredTemplatesState) {
                is FeaturedTemplatesState.Loading -> {
                    // Show shimmer loading
                    FeaturedTemplatesSkeleton(
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                }
                is FeaturedTemplatesState.Success -> {
                    // Show template grid
                    FeaturedTemplatesGrid(
                        templates = featuredTemplatesState.templates,
                        onTemplateClick = { template -> onTemplateClick(template.id) },
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                }
                is FeaturedTemplatesState.Error -> {
                    // Show error state (optional - could just hide the section)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                            .padding(horizontal = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.export_templates_load_failed),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }

        // ProcessToast overlay for save to gallery
        ProcessToast(
            state = saveToastState,
            onDismiss = onSaveToastDismissed
        )
    }
}

@Composable
private fun ErrorContent(
    message: String,
    onRetryClick: () -> Unit,
    onBackClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.padding(32.dp)
    ) {
        Box(
            modifier = Modifier
                .size(100.dp)
                .background(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(50.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.error
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = stringResource(R.string.export_failed),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(48.dp))

        Button(
            onClick = onRetryClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.export_try_again),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = onBackClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = stringResource(R.string.export_go_back),
                fontSize = 16.sp
            )
        }
    }
}

@Composable
private fun CancelledContent(
    onBackClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.padding(32.dp)
    ) {
        Text(
            text = stringResource(R.string.export_cancelled),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.export_cancelled_message),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(48.dp))

        Button(
            onClick = onBackClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = stringResource(R.string.export_back_to_editor),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}


/**
 * Share video file using system share sheet
 *
 * @param context Android context
 * @param outputPath Path to the video file
 * @param onError Callback for error handling (shows error message to user)
 */
private fun shareVideo(
    context: Context,
    outputPath: String,
    onError: (String) -> Unit
) {
    try {
        val file = File(outputPath)
        if (!file.exists()) {
            Log.e("ExportScreen", "Share failed: Video file not found at $outputPath")
            onError(context.getString(R.string.export_error_file_not_found))
            return
        }

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "video/mp4"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.export_share_video)))
    } catch (e: Exception) {
        Log.e("ExportScreen", "Share failed", e)
        onError(e.message ?: context.getString(R.string.export_error_share_failed))
    }
}

// ============================================
// FEATURED TEMPLATES SECTION
// ============================================

@Composable
private fun FeaturedTemplatesGrid(
    templates: List<VideoTemplate>,
    onTemplateClick: (VideoTemplate) -> Unit,
    modifier: Modifier = Modifier
) {
    if (templates.isEmpty()) return

    // Display templates in a 2-column grid (max 6 items)
    val displayTemplates = templates.take(6)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Split into rows of 2
        displayTemplates.chunked(2).forEach { rowTemplates ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                rowTemplates.forEach { template ->
                    Box(modifier = Modifier.weight(1f)) {
                        TemplateCard(
                            name = template.name,
                            thumbnailPath = template.thumbnailPath,
                            aspectRatio = parseAspectRatio(template.aspectRatio),
                            isPremium = template.isPremium,
                            useCount = template.useCount,
                            onClick = { onTemplateClick(template) }
                        )
                    }
                }
                // Add empty space if odd number in last row
                if (rowTemplates.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun FeaturedTemplatesSkeleton(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Show 3 rows of 2 shimmer items (total 6)
        repeat(3) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                repeat(2) {
                    ShimmerPlaceholder(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(9f / 16f)
                    )
                }
            }
        }
    }
}

/**
 * Parse aspect ratio string (e.g., "9:16", "16:9", "1:1") to float
 */
private fun parseAspectRatio(aspectRatio: String): Float {
    return try {
        val parts = aspectRatio.split(":")
        if (parts.size == 2) {
            val width = parts[0].toFloatOrNull() ?: 9f
            val height = parts[1].toFloatOrNull() ?: 16f
            width / height
        } else {
            9f / 16f // Default to portrait
        }
    } catch (e: Exception) {
        9f / 16f // Default to portrait
    }
}

// ============================================
// PREVIEWS
// ============================================

@Preview(showBackground = true)
@Composable
private fun PreparingContentPreview() {
    VideoMakerTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(SplashBackground),
            contentAlignment = Alignment.Center
        ) {
            PreparingContent()
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ProcessingContentPreview() {
    VideoMakerTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(SplashBackground),
            contentAlignment = Alignment.Center
        ) {
            ProcessingContent(
                progress = 45,
                thumbnailUri = null,
                aspectRatio = AspectRatio.RATIO_9_16
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SuccessContentPreview() {
    VideoMakerTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(SplashBackground)
        ) {
            SuccessContent(
                outputPath = "/storage/emulated/0/Movies/video_123.mp4",
                savedToGallery = false,
                saveError = null,
                shareError = null,
                aspectRatio = AspectRatio.RATIO_9_16,
                currentQuality = VideoQuality.DEFAULT,
                onSaveToGalleryClick = {},
                onShareClick = {},
                onQualityChange = {},
                onDoneClick = {}
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SuccessContentSavedPreview() {
    VideoMakerTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(SplashBackground)
        ) {
            SuccessContent(
                outputPath = "/storage/emulated/0/Movies/video_123.mp4",
                savedToGallery = true,
                saveError = null,
                shareError = null,
                aspectRatio = AspectRatio.RATIO_9_16,
                currentQuality = VideoQuality.DEFAULT,
                onSaveToGalleryClick = {},
                onShareClick = {},
                onQualityChange = {},
                onDoneClick = {}
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ErrorContentPreview() {
    VideoMakerTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(SplashBackground),
            contentAlignment = Alignment.Center
        ) {
            ErrorContent(
                message = "Failed to export video. Please check available storage space and try again.",
                onRetryClick = {},
                onBackClick = {}
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun CancelledContentPreview() {
    VideoMakerTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(SplashBackground),
            contentAlignment = Alignment.Center
        ) {
            CancelledContent(
                onBackClick = {}
            )
        }
    }
}

