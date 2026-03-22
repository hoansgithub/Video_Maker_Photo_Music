package com.videomaker.aimusic.modules.editor

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.videomaker.aimusic.R
import com.videomaker.aimusic.di.MusicPickerViewModelFactory
import com.videomaker.aimusic.domain.model.Project
import com.videomaker.aimusic.domain.model.VideoQuality
import com.videomaker.aimusic.modules.editor.components.DurationBottomSheet
import com.videomaker.aimusic.modules.editor.components.EffectSetBottomSheet
import com.videomaker.aimusic.modules.editor.components.MusicSearchBottomSheet
import com.videomaker.aimusic.modules.editor.components.MusicSection
import com.videomaker.aimusic.modules.editor.components.SelectRatioBottomSheet
import com.videomaker.aimusic.modules.editor.components.SettingsPanel
import com.videomaker.aimusic.modules.editor.components.SettingsTabBar
import com.videomaker.aimusic.modules.editor.components.VideoPreviewPlayer
import com.videomaker.aimusic.modules.editor.components.VolumeBottomSheet
import com.videomaker.aimusic.modules.editor.EffectSetViewModel
import com.videomaker.aimusic.modules.musicpicker.MusicPickerScreen
import com.videomaker.aimusic.ui.theme.SplashBackground
import com.videomaker.aimusic.ui.theme.TextPrimary
import com.videomaker.aimusic.ui.theme.TextSecondary
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

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
    var showEffectSetSheet by remember { mutableStateOf(false) }
    var showMusicSearchSheet by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Music Picker ViewModel - created once and reused
    val musicPickerViewModel = remember {
        musicPickerViewModelFactory.create()
    }

    // Effect Set ViewModel - created once and reused
    val effectSetViewModel: EffectSetViewModel = koinViewModel()

    // Song Search ViewModel - created once and reused
    val songSearchViewModel: com.videomaker.aimusic.modules.songsearch.SongSearchViewModel = koinViewModel()

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
                        effectSetName = state.effectSetName,
                        onPlayPauseClick = viewModel::togglePlayback,
                        onPlaybackStateChange = viewModel::setPlaybackState,
                        onPositionUpdate = viewModel::updatePlaybackPosition,
                        onSeek = viewModel::seekTo,
                        onScrub = viewModel::scrubTo,
                        onSeekStart = viewModel::stopPlayback,
                        onSeekEnd = {}, // Resume happens in clearSeekRequest after seek completes
                        onSeekComplete = viewModel::clearSeekRequest,
                        onScrubComplete = viewModel::clearScrubRequest,
                        onEffectClick = { showEffectSetSheet = true },
                        onImageDurationClick = { showDurationSheet = true },
                        onRatioClick = { showRatioSheet = true },
                        onVolumeClick = { showVolumeSheet = true },
                        onMusicClick = { showMusicSearchSheet = true },
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
                    onVolumeChange = viewModel::updateAudioVolume, // Live updates
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

        // Effect Set Bottom Sheet
        if (showEffectSetSheet) {
            val selectedEffectSetId = (uiState as? EditorUiState.Success)?.displaySettings?.effectSetId
            EffectSetBottomSheet(
                viewModel = effectSetViewModel,
                selectedEffectSetId = selectedEffectSetId,
                onEffectSetSelected = { effectSet ->
                    viewModel.updateEffectSet(effectSet.id)
                    showEffectSetSheet = false
                },
                onDismiss = { showEffectSetSheet = false }
            )
        }

        // Music Search Bottom Sheet
        if (showMusicSearchSheet) {
            MusicSearchBottomSheet(
                viewModel = songSearchViewModel,
                onSongSelected = { song ->
                    viewModel.updateMusicTrack(
                        songId = song.id,
                        songName = song.name,
                        songUrl = song.mp3Url,
                        songCoverUrl = song.coverUrl
                    )
                    showMusicSearchSheet = false
                },
                onDismiss = { showMusicSearchSheet = false }
            )
        }

        // Fullscreen Processing Overlay - blurry background with loading indicator
        val isProcessing = (uiState as? EditorUiState.Success)?.isProcessing ?: false
        if (isProcessing) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .blur(radius = 20.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { /* Block interactions during processing */ }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(64.dp),
                        strokeWidth = 5.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = stringResource(R.string.editor_preparing_video),
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
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
            text = stringResource(R.string.editor_hd),
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
                        contentDescription = stringResource(R.string.editor_select_quality),
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
// MusicSection, SettingsTabBar, and SettingsTabButton moved to components/ package

@Composable
internal fun EditorMainContent(
    project: Project,
    isPlaying: Boolean,
    isProcessing: Boolean,
    currentPositionMs: Long,
    durationMs: Long,
    seekToPosition: Long?,
    scrubToPosition: Long?,
    effectSetName: String,
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
    onMusicClick: () -> Unit,
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
        }

        // Music Section - song info and player
        MusicSection(
            songName = project.settings.musicSongName ?: "No music selected",
            coverUrl = project.settings.musicSongCoverUrl ?: "",
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
            onMusicClick = onMusicClick
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
            currentEffectSetName = effectSetName,
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

// SelectRatioBottomSheet, RatioOptionCard, AspectRatioIcon, DurationBottomSheet,
// and MusicSearchBottomSheet moved to components/ package
