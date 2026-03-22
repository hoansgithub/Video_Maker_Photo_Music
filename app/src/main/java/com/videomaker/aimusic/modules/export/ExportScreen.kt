package com.videomaker.aimusic.modules.export

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import com.videomaker.aimusic.ui.components.QualityPicker
import com.videomaker.aimusic.ui.theme.SplashBackground
import com.videomaker.aimusic.ui.theme.VideoMakerTheme
import java.io.File

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
    onNavigateToHomeMyVideos: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val navigationEvent by viewModel.navigationEvent.collectAsStateWithLifecycle()
    val thumbnailUri by viewModel.thumbnailUri.collectAsStateWithLifecycle()
    val aspectRatio by viewModel.aspectRatio.collectAsStateWithLifecycle()
    val currentQuality by viewModel.currentQuality.collectAsStateWithLifecycle()
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
            }
            viewModel.onNavigationHandled()
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
                    onSaveToGalleryClick = {
                        viewModel.saveToGallery(context.applicationContext)
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
                    onDoneClick = viewModel::navigateToHomeMyVideos
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
                Box(
                    modifier = Modifier
                        .background(
                            color = Color.Black.copy(alpha = 0.6f),
                            shape = RoundedCornerShape(24.dp)
                        )
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = "$progress%",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Message below thumbnail
            Text(
                text = stringResource(R.string.export_please_wait),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                lineHeight = 24.sp
            )
        }

        // Bottom spacer for layout balance
        Spacer(modifier = Modifier.height(40.dp))
    }
}

@Composable
private fun SuccessContent(
    outputPath: String,
    savedToGallery: Boolean,
    saveError: String?,
    shareError: String? = null,
    aspectRatio: AspectRatio = AspectRatio.RATIO_9_16,
    currentQuality: VideoQuality = VideoQuality.DEFAULT,
    onSaveToGalleryClick: () -> Unit,
    onShareClick: () -> Unit,
    onQualityChange: (VideoQuality) -> Unit = {},
    onDoneClick: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Create ExoPlayer without preparing (to avoid blocking main thread)
    val exoPlayer = remember(outputPath) {
        ExoPlayer.Builder(context).build().apply {
            val videoUri = Uri.fromFile(File(outputPath))
            setMediaItem(MediaItem.fromUri(videoUri))
            repeatMode = Player.REPEAT_MODE_ONE
            // Don't call prepare() here - it can block on ConditionVariable
        }
    }

    // Prepare player asynchronously to avoid ANR
    LaunchedEffect(exoPlayer) {
        // prepare() is non-blocking but can wait on ConditionVariable internally
        // Running in LaunchedEffect ensures it doesn't block composition
        exoPlayer.prepare()
    }

    // Track playing state from ExoPlayer
    var isPlaying by remember { mutableStateOf(false) }

    // Update isPlaying state and handle auto-play when player is ready
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                // Auto-play when player is ready
                if (playbackState == Player.STATE_READY && !exoPlayer.isPlaying) {
                    exoPlayer.play()
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
        }
    }

    // Handle lifecycle events - pause/resume playback
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> exoPlayer.pause()
                Lifecycle.Event.ON_RESUME -> {
                    // Always auto-play when screen becomes visible and player is ready
                    if (exoPlayer.playbackState == Player.STATE_READY && !exoPlayer.isPlaying) {
                        exoPlayer.play()
                    }
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            exoPlayer.release()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Top bar with controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(16.dp)
                .align(Alignment.TopCenter),
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

            // Right side controls
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Quality picker (reusable component)
                QualityPicker(
                    selectedQuality = currentQuality,
                    onQualityChange = onQualityChange
                )

                // Share button
                IconButton(onClick = onShareClick) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = stringResource(R.string.export_share_video),
                        tint = Color.White
                    )
                }
            }
        }

        // Center: Video preview with play/pause button
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 80.dp, start = 32.dp, end = 32.dp, bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Video preview container with ExoPlayer
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                // Video player with correct aspect ratio
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            player = exoPlayer
                            useController = false // Hide default controls
                            setBackgroundColor(android.graphics.Color.BLACK)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(aspectRatio.ratio)
                        .background(
                            color = Color.Black,
                            shape = RoundedCornerShape(16.dp)
                        )
                )

                // Play/Pause button overlay
                IconButton(
                    onClick = {
                        if (exoPlayer.isPlaying) {
                            exoPlayer.pause()
                        } else {
                            exoPlayer.play()
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

            Spacer(modifier = Modifier.height(32.dp))

            // Save to Gallery button - disabled after saving, resets next time
            Button(
                onClick = onSaveToGalleryClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(12.dp),
                // Disable after saving, re-enable on next entry (new ViewModel instance)
                enabled = !savedToGallery,
                // Show success color when saved, default color when not saved
                colors = if (savedToGallery) {
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                } else {
                    ButtonDefaults.buttonColors()
                }
            ) {
                Icon(
                    imageVector = if (savedToGallery) Icons.Default.Check else Icons.Default.Download,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (savedToGallery) {
                        stringResource(R.string.export_saved_to_gallery)
                    } else {
                        stringResource(R.string.export_save)
                    },
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Show errors if any
            if (saveError != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = saveError,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
            }

            if (shareError != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = shareError,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
            }
        }
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
    context: android.content.Context,
    outputPath: String,
    onError: (String) -> Unit
) {
    try {
        val file = File(outputPath)
        if (!file.exists()) {
            android.util.Log.e("ExportScreen", "Share failed: Video file not found at $outputPath")
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
        android.util.Log.e("ExportScreen", "Share failed", e)
        onError(e.message ?: context.getString(R.string.export_error_share_failed))
    }
}

// ============================================
// PREVIEWS
// ============================================

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
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

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
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

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
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

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
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

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
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

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
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

