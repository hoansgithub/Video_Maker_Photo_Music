package com.videomaker.aimusic.modules.editor

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.videomaker.aimusic.R
import com.videomaker.aimusic.di.MusicPickerViewModelFactory
import com.videomaker.aimusic.domain.model.AspectRatio
import com.videomaker.aimusic.domain.model.Project
import com.videomaker.aimusic.domain.model.VideoQuality
import com.videomaker.aimusic.ui.theme.Gray500
import com.videomaker.aimusic.ui.theme.PlayerCardBackground
import com.videomaker.aimusic.ui.theme.SplashBackground
import com.videomaker.aimusic.ui.theme.TextPrimary
import com.videomaker.aimusic.ui.theme.TextSecondary
import com.videomaker.aimusic.modules.editor.components.SettingsPanel
import com.videomaker.aimusic.modules.editor.components.VideoPreviewPlayer
import com.videomaker.aimusic.modules.editor.components.VolumeBottomSheet
import com.videomaker.aimusic.modules.musicpicker.MusicPickerScreen

/**
 * EditorScreen - Main video editor screen
 *
 * Layout:
 * - TopBar with back, title, and preview buttons
 * - Preview area showing current asset
 * - Music section with playback controls
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
    musicPickerViewModelFactory: MusicPickerViewModelFactory,
    onNavigateBack: () -> Unit,
    onNavigateToPreview: (String) -> Unit,
    onNavigateToExport: (String) -> Unit,
    onNavigateToAddAssets: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showExitConfirmation by remember { mutableStateOf(false) }
    var showMusicPicker by remember { mutableStateOf(false) }
    var showVolumeSheet by remember { mutableStateOf(false) }
    var showRatioSheet by remember { mutableStateOf(false) }
    var showDurationSheet by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Music Picker ViewModel - created once and reused
    val musicPickerViewModel = remember {
        musicPickerViewModelFactory.create()
    }

    // Handle back button press - show confirmation dialog
    BackHandler {
        showExitConfirmation = true
    }

    // Navigation events live in their own StateFlow — decoupled from high-frequency UI state
    val navigationEvent by viewModel.navigationEvent.collectAsStateWithLifecycle()
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
                val successState = uiState as? EditorUiState.Success
                EditorTopBar(
                    selectedQuality = successState?.selectedQuality ?: VideoQuality.DEFAULT,
                    isProcessing = successState?.isProcessing ?: false,
                    canExport = successState?.canExport ?: false,
                    onBackClick = { showExitConfirmation = true },
                    onQualityChange = viewModel::updateQuality,
                    onDoneClick = viewModel::navigateToExport
                )
            },
            containerColor = SplashBackground, // #101010 (closest to #101313)
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
                        project = state.displayProject, // Use displayProject to show pending changes in preview
                        isPlaying = state.isPlaying,
                        isProcessing = state.isProcessing,
                        currentPositionMs = state.currentPositionMs,
                        durationMs = state.durationMs,
                        seekToPosition = state.seekToPosition,
                        scrubToPosition = state.scrubToPosition,
                        onPlayPauseClick = viewModel::togglePlayback,
                        onPlaybackStateChange = viewModel::setPlaybackState,
                        onPositionUpdate = viewModel::updatePlaybackPosition,
                        onSeek = viewModel::seekTo,
                        onScrub = viewModel::scrubTo,
                        onSeekStart = viewModel::stopPlayback,
                        onSeekEnd = {}, // Resume happens in clearSeekRequest after seek completes
                        onSeekComplete = viewModel::clearSeekRequest,
                        onScrubComplete = viewModel::clearScrubRequest,
                        onEffectClick = { /* TODO: Open effect picker */ },
                        onImageDurationClick = { showDurationSheet = true },
                        onRatioClick = { showRatioSheet = true },
                        onVolumeClick = { showVolumeSheet = true },
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
                    onEffectSetChange = viewModel::updateEffectSet,
                    onImageDurationChange = viewModel::updateImageDuration,
                    onTransitionPercentageChange = viewModel::updateTransitionPercentage,
                    onOverlayFrameChange = viewModel::updateOverlayFrame,
                    onMusicSongChange = { songId -> viewModel.updateMusicSong(songId, null) },
                    onCustomAudioChange = viewModel::updateCustomAudio,
                    onAudioVolumeChange = viewModel::updateAudioVolume,
                    onAspectRatioChange = viewModel::updateAspectRatio,
                    onApplySettings = viewModel::applySettings,
                    onDiscardSettings = viewModel::discardPendingSettings,
                    onClose = viewModel::closeSettingsPanel,
                    onOpenMusicPicker = { showMusicPicker = true },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // Exit confirmation dialog - rendered last to overlay everything
        if (showExitConfirmation) {
            val isUnsaved = (uiState as? EditorUiState.Success)?.isUnsavedProject == true
            ExitConfirmationDialog(
                isUnsavedProject = isUnsaved,
                onSaveAndExit = {
                    showExitConfirmation = false
                    scope.launch {
                        if (isUnsaved) {
                            // Save project to DB first, then navigate
                            if (viewModel.saveProject()) {
                                viewModel.navigateBack()
                            }
                        } else {
                            // Project already saved, just navigate
                            viewModel.navigateBack()
                        }
                    }
                },
                onDiscardAndExit = {
                    showExitConfirmation = false
                    // Navigate back without saving (for unsaved projects, this discards the work)
                    viewModel.navigateBack()
                },
                onCancel = {
                    showExitConfirmation = false
                }
            )
        }

        // Music Picker Bottom Sheet
        if (showMusicPicker) {
            MusicPickerScreen(
                viewModel = musicPickerViewModel,
                onTrackSelected = { uri ->
                    viewModel.updateCustomAudio(uri)
                    showMusicPicker = false
                },
                onDismiss = {
                    showMusicPicker = false
                }
            )
        }

        // Volume Bottom Sheet
        if (showVolumeSheet) {
            val successState = uiState as? EditorUiState.Success
            if (successState != null) {
                VolumeBottomSheet(
                    currentVolume = successState.displaySettings.audioVolume,
                    onConfirm = { volume ->
                        viewModel.updateAudioVolume(volume)
                        showVolumeSheet = false
                    },
                    onDismiss = {
                        showVolumeSheet = false
                    }
                )
            }
        }

        // Ratio Bottom Sheet
        if (showRatioSheet) {
            val successState = uiState as? EditorUiState.Success
            if (successState != null) {
                SelectRatioBottomSheet(
                    currentRatio = successState.displaySettings.aspectRatio,
                    onDismiss = { showRatioSheet = false },
                    onConfirm = { selectedRatio ->
                        viewModel.updateAspectRatio(selectedRatio)
                        showRatioSheet = false
                    }
                )
            }
        }

        // Duration Bottom Sheet
        if (showDurationSheet) {
            val successState = uiState as? EditorUiState.Success
            if (successState != null) {
                DurationBottomSheet(
                    currentDurationMs = successState.displaySettings.imageDurationMs,
                    onDismiss = { showDurationSheet = false },
                    onConfirm = { selectedDurationMs ->
                        viewModel.updateImageDuration(selectedDurationMs)
                        showDurationSheet = false
                    }
                )
            }
        }
    }
}

@Composable
private fun HdBadge(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(
                color = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(4.dp)
            )
            .padding(horizontal = 4.dp, vertical = 2.dp)
    ) {
        Text(
            text = "HD",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimary
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EditorTopBar(
    selectedQuality: VideoQuality,
    isProcessing: Boolean,
    canExport: Boolean,
    onBackClick: () -> Unit,
    onQualityChange: (VideoQuality) -> Unit,
    onDoneClick: () -> Unit
) {
    var showQualityMenu by remember { mutableStateOf(false) }

    TopAppBar(
        title = {
            // Empty title - quality button moved to actions
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
            // Quality dropdown button (aligned right)
            Box {
                Row(
                    modifier = Modifier
                        .height(40.dp)
                        .border(
                            width = 1.dp,
                            color = Color(0x1FFFFFFF), // #FFFFFF1F
                            shape = RoundedCornerShape(16.dp)
                        )
                        .clickable { showQualityMenu = true }
                        .padding(horizontal = 16.dp, vertical = 0.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // HD badge for 1080p (on the left)
                    if (selectedQuality == VideoQuality.FHD_1080) {
                        HdBadge()
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(
                        text = selectedQuality.displayName,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = "Select quality",
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Quality dropdown menu
                DropdownMenu(
                    expanded = showQualityMenu,
                    onDismissRequest = { showQualityMenu = false }
                ) {
                    VideoQuality.entries.forEach { quality ->
                        DropdownMenuItem(
                            text = {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // HD badge for 1080p
                                    if (quality == VideoQuality.FHD_1080) {
                                        HdBadge()
                                        Spacer(modifier = Modifier.width(8.dp))
                                    }
                                    Text(
                                        text = quality.displayName,
                                        fontWeight = if (quality == selectedQuality) {
                                            FontWeight.Bold
                                        } else {
                                            FontWeight.Normal
                                        }
                                    )
                                }
                            },
                            onClick = {
                                onQualityChange(quality)
                                showQualityMenu = false
                            },
                            leadingIcon = if (quality == selectedQuality) {
                                {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            } else null
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Done button - disabled during processing
            Button(
                onClick = onDoneClick,
                enabled = canExport,
                modifier = Modifier.height(40.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(16.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 16.dp,
                    vertical = 0.dp
                )
            ) {
                if (isProcessing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = if (isProcessing) stringResource(R.string.editor_processing)
                           else stringResource(R.string.done),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = SplashBackground // #101010 (closest to #101313)
        )
    )
}

@Composable
internal fun LoadingContent(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
internal fun ErrorContent(
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MusicSection(
    songName: String,
    duration: String,
    currentPosition: Float,
    isPlaying: Boolean,
    onSeek: (Float) -> Unit,
    onScrub: (Float) -> Unit = {},
    onSeekStart: () -> Unit = {},
    onSeekEnd: () -> Unit = {},
    onPlayPauseClick: () -> Unit,
    onExpandClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Hoist interaction source to prevent recreation on every recomposition
    val sliderInteractionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }

    // Local state for smooth slider dragging (prevents jumps during drag)
    var isDragging by remember { mutableStateOf(false) }
    var localPosition by remember { mutableStateOf(currentPosition) }
    // Track last scrub time for throttling (150ms)
    var lastScrubTime by remember { mutableLongStateOf(0L) }

    // Update local position when not dragging (allows external position updates)
    if (!isDragging) {
        localPosition = currentPosition
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(PlayerCardBackground)
            .padding(12.dp)
    ) {
        // Seeker row - TOP
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Play/pause button
            IconButton(
                onClick = onPlayPauseClick,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = TextPrimary,
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Slider with smooth dragging and real-time scrubbing
            Slider(
                value = localPosition.coerceIn(0f, 1f),
                onValueChange = { newValue ->
                    if (!isDragging) {
                        isDragging = true
                        onSeekStart() // Pause playback when starting to drag
                    }
                    localPosition = newValue

                    // Throttled scrubbing - show preview while dragging (150ms intervals)
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastScrubTime >= 150L) {
                        lastScrubTime = currentTime
                        onScrub(newValue) // Real-time preview during drag
                    }
                },
                onValueChangeFinished = {
                    isDragging = false
                    onSeek(localPosition) // Seek when user releases
                    onSeekEnd() // Resume playback
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
                        interactionSource = sliderInteractionSource,
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

            Spacer(modifier = Modifier.width(8.dp))

            // Duration
            Text(
                text = duration,
                fontSize = 13.sp,
                color = TextSecondary,
                modifier = Modifier.width(36.dp)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Separator line
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Color.White.copy(alpha = 0.1f))
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Song info row - BOTTOM
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Music icon
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Song name
            Text(
                text = songName,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.width(8.dp))

            // Expand button - matches quality selector icon
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = "Expand music options",
                tint = TextSecondary,
                modifier = Modifier
                    .size(24.dp)
                    .clickable(onClick = onExpandClick)
            )
        }
    }
}

// ============================================
// SETTINGS TAB BAR
// Effect, Duration, Ratio, Volume tabs
// ============================================
@Composable
private fun SettingsTabBar(
    currentVolume: Float,
    currentRatio: AspectRatio,
    currentDurationMs: Long,
    onEffectClick: () -> Unit,
    onImageDurationClick: () -> Unit,
    onRatioClick: () -> Unit,
    onVolumeClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(SplashBackground)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        // Effect button
        SettingsTabButton(
            icon = Icons.Default.AutoAwesome,
            label = "Effect",
            onClick = onEffectClick,
            modifier = Modifier.weight(1f)
        )

        // Image Duration button - shows current duration
        SettingsTabButton(
            icon = Icons.Default.Schedule,
            label = "${currentDurationMs / 1000f}s",
            onClick = onImageDurationClick,
            modifier = Modifier.weight(1f)
        )

        // Ratio button - shows current ratio
        SettingsTabButton(
            icon = Icons.Default.AspectRatio,
            label = currentRatio.shortLabel,
            onClick = onRatioClick,
            modifier = Modifier.weight(1f)
        )

        // Volume button - shows current percentage
        SettingsTabButton(
            icon = Icons.AutoMirrored.Filled.VolumeUp,
            label = "${(currentVolume * 100).toInt()}%",
            onClick = onVolumeClick,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun SettingsTabButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = TextPrimary,
            modifier = Modifier.size(28.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = TextPrimary
        )
    }
}

@Composable
internal fun EditorMainContent(
    project: Project,
    isPlaying: Boolean,
    isProcessing: Boolean,
    currentPositionMs: Long,
    durationMs: Long,
    seekToPosition: Long?,
    scrubToPosition: Long?,
    onPlayPauseClick: () -> Unit,
    onPlaybackStateChange: (Boolean) -> Unit,
    onPositionUpdate: (Long, Long) -> Unit,
    onSeek: (Long) -> Unit,
    onScrub: (Long) -> Unit,
    onSeekStart: () -> Unit,
    onSeekEnd: () -> Unit,
    onSeekComplete: () -> Unit,
    onScrubComplete: () -> Unit,
    onEffectClick: () -> Unit,
    onImageDurationClick: () -> Unit,
    onRatioClick: () -> Unit,
    onVolumeClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        // Real-time Video Preview using CompositionPlayer
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
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
                modifier = Modifier.fillMaxSize()
            )

            // Processing indicator - centered overlay
            if (isProcessing) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(48.dp),
                            strokeWidth = 4.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.editor_preparing_video),
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        // Music Section - song info and player
        MusicSection(
            songName = project.settings.musicSongName ?: "No music selected",
            duration = project.formattedDuration,
            currentPosition = if (durationMs > 0) currentPositionMs / durationMs.toFloat() else 0f,
            isPlaying = isPlaying,
            onSeek = { position ->
                if (durationMs > 0) {
                    onSeek((position * durationMs).toLong())
                }
            },
            onScrub = { position ->
                if (durationMs > 0) {
                    onScrub((position * durationMs).toLong())
                }
            },
            onSeekStart = onSeekStart,
            onSeekEnd = onSeekEnd,
            onPlayPauseClick = onPlayPauseClick,
            onExpandClick = { /* TODO: Open music picker */ }
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Separator
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Color.White.copy(alpha = 0.1f))
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Settings Tab Bar - Effect, Duration, Ratio, Volume
        SettingsTabBar(
            currentVolume = project.settings.audioVolume,
            currentRatio = project.settings.aspectRatio,
            currentDurationMs = project.settings.imageDurationMs,
            onEffectClick = onEffectClick,
            onImageDurationClick = onImageDurationClick,
            onRatioClick = onRatioClick,
            onVolumeClick = onVolumeClick,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun ExitConfirmationDialog(
    isUnsavedProject: Boolean,
    onSaveAndExit: () -> Unit,
    onDiscardAndExit: () -> Unit,
    onCancel: () -> Unit
) {
    val title = if (isUnsavedProject) {
        stringResource(R.string.editor_unsaved_project_title)
    } else {
        stringResource(R.string.editor_unsaved_changes_title)
    }
    val message = if (isUnsavedProject) {
        stringResource(R.string.editor_unsaved_project_message)
    } else {
        stringResource(R.string.editor_unsaved_changes_message)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null,
                onClick = { /* Prevent background clicks */ }
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 32.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(SplashBackground)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Title
            Text(
                text = title,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Message
            Text(
                text = message,
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
                color = TextSecondary,
                lineHeight = 22.sp
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Save & Exit button (Primary)
            Button(
                onClick = onSaveAndExit,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text(
                    text = stringResource(R.string.editor_save_and_exit),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Keep Editing button (Secondary)
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.2f)
                )
            ) {
                Text(
                    text = stringResource(R.string.editor_keep_editing),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Discard button (Tertiary)
            TextButton(
                onClick = onDiscardAndExit,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Text(
                    text = stringResource(R.string.editor_discard),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

// ============================================
// ASPECT RATIO EXTENSION
// ============================================

private val AspectRatio.shortLabel: String
    get() = when (this) {
        AspectRatio.RATIO_16_9 -> "16:9"
        AspectRatio.RATIO_9_16 -> "9:16"
        AspectRatio.RATIO_4_5 -> "4:5"
        AspectRatio.RATIO_1_1 -> "1:1"
    }

// ============================================
// SELECT RATIO BOTTOM SHEET
// ============================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectRatioBottomSheet(
    currentRatio: AspectRatio,
    onDismiss: () -> Unit,
    onConfirm: (AspectRatio) -> Unit
) {
    var selected by remember { mutableStateOf(currentRatio) }
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Select Video Ratio",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )

            // Ratio options grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
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

            // Confirm button
            Button(
                onClick = {
                    onConfirm(selected)
                    onDismiss()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "Apply",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
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
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else Gray500
    val borderWidth = if (isSelected) 2.dp else 1.dp
    val backgroundColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        SplashBackground
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .border(borderWidth, borderColor, RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .clickable { onClick() }
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Aspect ratio icon
            AspectRatioIcon(ratio = ratio, isSelected = isSelected)

            // Label
            Text(
                text = ratio.shortLabel,
                color = if (isSelected) MaterialTheme.colorScheme.primary else TextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun AspectRatioIcon(
    ratio: AspectRatio,
    isSelected: Boolean
) {
    val color = if (isSelected) MaterialTheme.colorScheme.primary else Gray500
    val maxSize = 36.dp
    val (iconW, iconH) = when (ratio) {
        AspectRatio.RATIO_16_9 -> maxSize to (maxSize * (9f / 16f))
        AspectRatio.RATIO_9_16 -> (maxSize * (9f / 16f)) to maxSize
        AspectRatio.RATIO_4_5 -> (maxSize * (4f / 5f)) to maxSize
        AspectRatio.RATIO_1_1 -> maxSize to maxSize
    }

    Box(
        modifier = Modifier
            .size(width = iconW, height = iconH)
            .border(1.5.dp, color, RoundedCornerShape(4.dp))
    )
}

// ============================================
// DURATION BOTTOM SHEET
// ============================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DurationBottomSheet(
    currentDurationMs: Long,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit
) {
    // Convert ms to seconds for slider (default 3s if current is 0 or out of range)
    val currentSeconds = (currentDurationMs / 1000f).coerceIn(1f, 20f)
    var sliderValue by remember { mutableFloatStateOf(currentSeconds) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SplashBackground,
        dragHandle = null,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(top = 24.dp, bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text(
                text = "Image Duration",
                color = TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )

            // Selected duration display
            Text(
                text = "${sliderValue.toInt()}s",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold
            )

            // Slider
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
            ) {
                Slider(
                    value = sliderValue,
                    onValueChange = { sliderValue = it },
                    valueRange = 1f..20f,
                    steps = 18, // 19 discrete values (1-20 inclusive)
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = Gray500
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // Min/Max labels
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "1s",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                    Text(
                        text = "20s",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }
            }

            // Apply button
            Button(
                onClick = { onConfirm((sliderValue.toInt() * 1000).toLong()) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "Apply",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
