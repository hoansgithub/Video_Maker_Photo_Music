package co.alcheclub.video.maker.photo.music.modules.editor

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.alcheclub.video.maker.photo.music.R
import co.alcheclub.video.maker.photo.music.domain.model.Project
import co.alcheclub.video.maker.photo.music.modules.editor.components.AssetStrip
import co.alcheclub.video.maker.photo.music.modules.editor.components.PlaybackSeekbar
import co.alcheclub.video.maker.photo.music.modules.editor.components.SettingsPanel
import co.alcheclub.video.maker.photo.music.modules.editor.components.VideoPreviewPlayer

/**
 * EditorScreen - Main video editor screen
 *
 * Layout:
 * - TopBar with back, title, and preview buttons
 * - Preview area showing current asset
 * - Timeline with horizontal scrollable asset thumbnails
 * - Settings panel (expandable)
 * - Export button
 *
 * Follows CLAUDE.md patterns:
 * - collectAsStateWithLifecycle for StateFlow
 * - LaunchedEffect(Unit) for navigation events
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    viewModel: EditorViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToPreview: (String) -> Unit,
    onNavigateToExport: (String) -> Unit,
    onNavigateToAddAssets: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showExitConfirmation by remember { mutableStateOf(false) }

    // Handle back button press - show confirmation dialog
    BackHandler {
        showExitConfirmation = true
    }

    // Handle navigation events - StateFlow-based (Google recommended pattern)
    // Observe navigationEvent from uiState and call onNavigationHandled() after navigating
    val navigationEvent = (uiState as? EditorUiState.Success)?.navigationEvent
    LaunchedEffect(navigationEvent) {
        navigationEvent?.let { event ->
            when (event) {
                is EditorNavigationEvent.NavigateBack -> onNavigateBack()
                is EditorNavigationEvent.NavigateToPreview -> onNavigateToPreview(event.projectId)
                is EditorNavigationEvent.NavigateToExport -> onNavigateToExport(event.projectId)
            }
            viewModel.onNavigationHandled()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Main editor UI with Scaffold
        val editorTitle = stringResource(R.string.editor_title)
        Scaffold(
            topBar = {
                EditorTopBar(
                    title = (uiState as? EditorUiState.Success)?.project?.name ?: editorTitle,
                    onBackClick = { showExitConfirmation = true },
                    onPreviewClick = viewModel::navigateToPreview,
                    onSettingsClick = viewModel::toggleSettingsPanel,
                    showSettingsSelected = (uiState as? EditorUiState.Success)?.showSettingsPanel == true
                )
            },
            containerColor = MaterialTheme.colorScheme.surface,
            contentWindowInsets = WindowInsets(0, 0, 0, 0)
        ) { paddingValues ->
            when (val state = uiState) {
                is EditorUiState.Loading -> {
                    LoadingContent(modifier = Modifier.padding(paddingValues))
                }
                is EditorUiState.Error -> {
                    ErrorContent(
                        message = state.message,
                        modifier = Modifier.padding(paddingValues)
                    )
                }
                is EditorUiState.Success -> {
                    EditorMainContent(
                        project = state.project,
                        isPlaying = state.isPlaying,
                        currentPositionMs = state.currentPositionMs,
                        durationMs = state.durationMs,
                        seekToPosition = state.seekToPosition,
                        scrubToPosition = state.scrubToPosition,
                        canRemoveAssets = viewModel.canRemoveAssets(),
                        onPlayPauseClick = viewModel::togglePlayback,
                        onPlaybackStateChange = viewModel::setPlaybackState,
                        onPositionUpdate = viewModel::updatePlaybackPosition,
                        onSeek = viewModel::seekTo,
                        onScrub = viewModel::scrubTo,
                        onSeekStart = viewModel::stopPlayback,
                        onSeekEnd = {}, // Resume happens in clearSeekRequest after seek completes
                        onSeekComplete = viewModel::clearSeekRequest,
                        onScrubComplete = viewModel::clearScrubRequest,
                        onAddAssetsClick = { onNavigateToAddAssets(state.project.id) },
                        onRemoveAsset = { viewModel.removeAsset(it) },
                        onExportClick = viewModel::navigateToExport,
                        modifier = Modifier.padding(paddingValues)
                    )
                }
            }
        }

        // Fullscreen Settings Panel - slides up from bottom
        val successState = uiState as? EditorUiState.Success
        AnimatedVisibility(
            visible = successState?.showSettingsPanel == true,
            enter = slideInVertically(initialOffsetY = { it }), // Slide up from bottom
            exit = slideOutVertically(targetOffsetY = { it })   // Slide down to bottom
        ) {
            if (successState != null) {
                SettingsPanel(
                    settings = successState.displaySettings,
                    hasPendingChanges = successState.hasPendingChanges,
                    onTransitionChange = viewModel::updateTransition,
                    onImageDurationChange = viewModel::updateImageDuration,
                    onTransitionPercentageChange = viewModel::updateTransitionPercentage,
                    onOverlayFrameChange = viewModel::updateOverlayFrame,
                    onAudioTrackChange = viewModel::updateAudioTrack,
                    onCustomAudioChange = viewModel::updateCustomAudio,
                    onAudioVolumeChange = viewModel::updateAudioVolume,
                    onAspectRatioChange = viewModel::updateAspectRatio,
                    onApplySettings = viewModel::applySettings,
                    onDiscardSettings = viewModel::discardPendingSettings,
                    onClose = viewModel::closeSettingsPanel,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // Exit confirmation dialog - rendered last to overlay everything
        if (showExitConfirmation) {
            ExitConfirmationDialog(
                onSaveAndExit = {
                    showExitConfirmation = false
                    // Project is already auto-saved via Room, just navigate back
                    viewModel.navigateBack()
                },
                onDiscardAndExit = {
                    showExitConfirmation = false
                    // Navigate back without additional save (project remains as last auto-saved state)
                    viewModel.navigateBack()
                },
                onCancel = {
                    showExitConfirmation = false
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditorTopBar(
    title: String,
    onBackClick: () -> Unit,
    onPreviewClick: () -> Unit,
    onSettingsClick: () -> Unit,
    showSettingsSelected: Boolean
) {
    TopAppBar(
        title = {
            Text(
                text = title,
                fontWeight = FontWeight.Bold
            )
        },
        navigationIcon = {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.back)
                )
            }
        },
        actions = {
            IconButton(onClick = onSettingsClick) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = stringResource(R.string.settings),
                    tint = if (showSettingsSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    )
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

@Composable
private fun EditorMainContent(
    project: Project,
    isPlaying: Boolean,
    currentPositionMs: Long,
    durationMs: Long,
    seekToPosition: Long?,
    scrubToPosition: Long?,
    canRemoveAssets: Boolean,
    onPlayPauseClick: () -> Unit,
    onPlaybackStateChange: (Boolean) -> Unit,
    onPositionUpdate: (Long, Long) -> Unit,
    onSeek: (Long) -> Unit,
    onScrub: (Long) -> Unit,
    onSeekStart: () -> Unit,
    onSeekEnd: () -> Unit,
    onSeekComplete: () -> Unit,
    onScrubComplete: () -> Unit,
    onAddAssetsClick: () -> Unit,
    onRemoveAsset: (String) -> Unit,
    onExportClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        // Real-time Video Preview using CompositionPlayer
        VideoPreviewPlayer(
            project = project,
            isPlaying = isPlaying,
            onPlayPauseClick = onPlayPauseClick,
            onPlaybackStateChange = onPlaybackStateChange,
            onPositionUpdate = onPositionUpdate,
            seekToPosition = seekToPosition,
            scrubToPosition = scrubToPosition,
            onSeekComplete = onSeekComplete,
            onScrubComplete = onScrubComplete,
            autoPlay = true,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )

        // Playback Seekbar - slider with time labels
        PlaybackSeekbar(
            currentPositionMs = currentPositionMs,
            durationMs = durationMs,
            isEnabled = durationMs > 0,
            onSeek = onSeek,
            onScrub = onScrub,
            onSeekStart = onSeekStart,
            onSeekEnd = onSeekEnd,
            modifier = Modifier.fillMaxWidth()
        )

        // Asset Strip - add/remove photos
        AssetStrip(
            assets = project.assets,
            canRemove = canRemoveAssets,
            onAddClick = onAddAssetsClick,
            onRemoveAsset = onRemoveAsset,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        )

        // Duration and Export Button
        BottomBar(
            duration = project.formattedDuration,
            assetCount = project.assets.size,
            onExportClick = onExportClick,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun BottomBar(
    duration: String,
    assetCount: Int,
    onExportClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Duration info
        Column {
            Text(
                text = stringResource(R.string.editor_duration_format, duration),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = stringResource(R.string.editor_photos_count, assetCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Export button
        Button(
            onClick = onExportClick,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            ),
            modifier = Modifier.height(48.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Upload,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.size(8.dp))
            Text(
                text = stringResource(R.string.editor_export),
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun ExitConfirmationDialog(
    onSaveAndExit: () -> Unit,
    onDiscardAndExit: () -> Unit,
    onCancel: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .padding(32.dp)
                .background(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.editor_save_dialog_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = stringResource(R.string.editor_save_dialog_message),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Save & Exit button
            Button(
                onClick = onSaveAndExit,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.editor_save_and_exit),
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Discard button
            OutlinedButton(
                onClick = onDiscardAndExit,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(text = stringResource(R.string.editor_discard_changes))
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Cancel button
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                Text(text = stringResource(R.string.cancel))
            }
        }
    }
}
