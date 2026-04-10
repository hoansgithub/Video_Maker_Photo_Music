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
import com.videomaker.aimusic.core.analytics.Analytics
import com.videomaker.aimusic.core.analytics.AnalyticsEvent
// import com.videomaker.aimusic.di.MusicPickerViewModelFactory // Commented out - using Supabase only
import com.videomaker.aimusic.domain.model.AspectRatio
import com.videomaker.aimusic.domain.model.Project
import com.videomaker.aimusic.domain.model.VideoQuality
import com.videomaker.aimusic.modules.editor.components.DurationBottomSheet
import com.videomaker.aimusic.modules.editor.components.EffectSetBottomSheet
import com.videomaker.aimusic.modules.editor.components.MusicSearchBottomSheet
import com.videomaker.aimusic.modules.editor.components.MusicSection
import com.videomaker.aimusic.modules.editor.components.MusicSettingsBottomSheet
import com.videomaker.aimusic.modules.editor.components.SelectRatioBottomSheet
import com.videomaker.aimusic.modules.editor.components.VolumeBottomSheet
// import com.videomaker.aimusic.modules.editor.components.SettingsPanel // Removed - using individual bottom sheets
import com.videomaker.aimusic.modules.editor.components.SettingsTabBar
import com.videomaker.aimusic.modules.editor.components.VideoPreviewPlayer
import com.videomaker.aimusic.modules.editor.EffectSetViewModel
// import com.videomaker.aimusic.modules.musicpicker.MusicPickerScreen // Commented out - using Supabase only
import com.videomaker.aimusic.ui.components.ErrorOverlay
import com.videomaker.aimusic.ui.components.ErrorType
import com.videomaker.aimusic.ui.components.ModifierExtension.clickableSingle
import com.videomaker.aimusic.ui.components.QualityPicker
import com.videomaker.aimusic.ui.theme.SplashBackground
import com.videomaker.aimusic.ui.theme.TextPrimary
import com.videomaker.aimusic.ui.theme.TextSecondary
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import kotlin.math.roundToInt
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import co.alcheclub.lib.acccore.ads.loader.AdsLoaderService
import co.alcheclub.lib.acccore.ads.loader.AdsLoaderException
import co.alcheclub.lib.acccore.ads.state.AdsLoadingState
import com.videomaker.aimusic.core.constants.AdPlacement
import androidx.compose.material3.SnackbarHostState

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
    // musicPickerViewModelFactory: MusicPickerViewModelFactory, // Commented out - using Supabase only
    onNavigateBack: () -> Unit,
    onNavigateToPreview: (String) -> Unit,
    onNavigateToExport: (String, com.videomaker.aimusic.domain.model.VideoQuality) -> Unit,
    onNavigateToAddAssets: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val showQualityAdDialog by viewModel.showQualityAdDialog.collectAsStateWithLifecycle()
    val pendingQualityUnlock by viewModel.pendingQualityUnlock.collectAsStateWithLifecycle()
    val qualityAdError by viewModel.qualityAdError.collectAsStateWithLifecycle()
    val isQualityUnlocked by viewModel.isQualityUnlocked.collectAsStateWithLifecycle()

    var showExitConfirmation by remember { mutableStateOf(false) }
    // var showMusicPicker by remember { mutableStateOf(false) } // Commented out - using Supabase only
    var showRatioSheet by remember { mutableStateOf(false) }
    var showDurationSheet by remember { mutableStateOf(false) }
    var showEffectSetSheet by remember { mutableStateOf(false) }
    var showMusicSearchSheet by remember { mutableStateOf(false) }
    var showVolumeSheet by remember { mutableStateOf(false) }
    var showMusicTrimSheet by remember { mutableStateOf(false) }
    var wasPlayingBeforeMusicSheet by remember { mutableStateOf(false) }
    var musicLoadError by remember { mutableStateOf<String?>(null) }
    var hasTrackedVideoPreview by remember { mutableStateOf(false) }
    var hasTrackedVideoPreviewComplete by remember { mutableStateOf(false) }
    var hasTrackedExitPopupShow by remember { mutableStateOf(false) }
    var ratioConfirmed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    val activity = context as? android.app.Activity
    val adsLoaderService = koinInject<AdsLoaderService>()
    val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }

    // Music Picker ViewModel - created once and reused
    // Commented out - using Supabase only
    // val musicPickerViewModel = remember {
    //     musicPickerViewModelFactory.create()
    // }

    // Effect Set ViewModel - created once and reused
    val effectSetViewModel: EffectSetViewModel = koinViewModel()

    // Song Search ViewModel - created once and reused
    val songSearchViewModel: com.videomaker.aimusic.modules.songsearch.SongSearchViewModel = koinViewModel()

    fun currentState(): EditorUiState.Success? = uiState as? EditorUiState.Success

    fun currentVideoId(): String? = currentState()?.project?.id

    fun currentTemplateId(): String? = currentState()?.displaySettings?.effectSetId

    fun currentSongId(): String =
        currentState()?.displaySettings?.musicSongId?.toString() ?: "unknown"

    fun currentSongName(): String =
        currentState()?.displaySettings?.musicSongName ?: "unknown"

    fun currentDurationMs(): Long = currentState()?.displayProject?.totalDurationMs ?: 0L

    fun currentRatioLabel(): String =
        currentState()?.displaySettings?.aspectRatio?.toAnalyticsRatioSize()
            ?: AspectRatio.RATIO_9_16.toAnalyticsRatioSize()

    fun currentVolumePercent(): Int =
        ((currentState()?.displaySettings?.audioVolume ?: 1f) * 100f).roundToInt()

    fun requestExitFromEditor() {
        Analytics.trackExitClick(AnalyticsEvent.Value.Location.VIDEO_PREVIEW)
        hasTrackedExitPopupShow = false
        showExitConfirmation = true
    }

    // Handle back button press - show confirmation dialog
    BackHandler {
        requestExitFromEditor()
    }

    val successStateForTracking = currentState()
    LaunchedEffect(successStateForTracking?.project?.id) {
        if (successStateForTracking != null) {
            hasTrackedVideoPreview = false
            hasTrackedVideoPreviewComplete = false
        }
    }

    LaunchedEffect(successStateForTracking?.project?.id, successStateForTracking?.currentPositionMs) {
        val state = successStateForTracking ?: return@LaunchedEffect
        val videoId = state.project.id
        if (!hasTrackedVideoPreview) {
            Analytics.trackVideoPreview(
                videoId = videoId,
                location = if (state.isUnsavedProject) {
                    AnalyticsEvent.Value.Location.NEW
                } else {
                    AnalyticsEvent.Value.Location.LIBRARY
                }
            )
            hasTrackedVideoPreview = true
        }
        if (!hasTrackedVideoPreviewComplete && state.currentPositionMs >= 3000L) {
            Analytics.trackVideoPreviewComplete(
                videoId = videoId,
                templateId = state.displaySettings.effectSetId
            )
            hasTrackedVideoPreviewComplete = true
        }
    }

    // Handle quality ad presentation when user confirms watching ad
    LaunchedEffect(pendingQualityUnlock) {
        if (!pendingQualityUnlock) return@LaunchedEffect

        val state = currentState() ?: return@LaunchedEffect

        // Check if Activity is available
        if (activity == null) {
            android.util.Log.w("EditorScreen", "Activity unavailable for quality ad presentation")
            snackbarHostState.showSnackbar(context.getString(com.videomaker.aimusic.R.string.quality_ad_not_available))
            viewModel.onQualityAdFailed()
            return@LaunchedEffect
        }

        try {
            // 1. Check if ad is ready
            if (!adsLoaderService.isRewardedAdReady(com.videomaker.aimusic.core.constants.AdPlacement.REWARD_UNLOCK_QUALITY)) {
                // 2. Show loading overlay
                co.alcheclub.lib.acccore.ads.state.AdsLoadingState.show("Loading ad...")

                // 3. Load ad with 60s timeout
                kotlinx.coroutines.withTimeout(60_000) {
                    adsLoaderService.loadRewarded(com.videomaker.aimusic.core.constants.AdPlacement.REWARD_UNLOCK_QUALITY)
                }

                // 4. Hide loading overlay
                co.alcheclub.lib.acccore.ads.state.AdsLoadingState.hide()
            }

            // 5. Present ad and wait for result (blocking)
            val result = adsLoaderService.presentRewarded(
                placement = com.videomaker.aimusic.core.constants.AdPlacement.REWARD_UNLOCK_QUALITY,
                activity = activity
            )

            // 6. Check if user earned reward
            if (result.earnedReward) {
                viewModel.onQualityRewardEarned()
            } else {
                android.util.Log.d("EditorScreen", "User did not earn reward (closed ad early)")
                viewModel.onQualityAdFailed()
            }

        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            android.util.Log.w("EditorScreen", "Quality ad load timeout")
            co.alcheclub.lib.acccore.ads.state.AdsLoadingState.hide()
            viewModel.showQualityAdError(context.getString(com.videomaker.aimusic.R.string.quality_ad_not_available))
            viewModel.onQualityAdFailed()

        } catch (e: co.alcheclub.lib.acccore.ads.loader.AdsLoaderException.NoAdToShow) {
            android.util.Log.w("EditorScreen", "No quality ad available")
            co.alcheclub.lib.acccore.ads.state.AdsLoadingState.hide()
            viewModel.showQualityAdError(context.getString(com.videomaker.aimusic.R.string.quality_ad_not_available))
            viewModel.onQualityAdFailed()

        } catch (e: Exception) {
            android.util.Log.e("EditorScreen", "Failed to show quality rewarded ad: ${e.message}")
            co.alcheclub.lib.acccore.ads.state.AdsLoadingState.hide()
            viewModel.showQualityAdError(context.getString(com.videomaker.aimusic.R.string.quality_ad_not_available))
            viewModel.onQualityAdFailed()

        } finally {
            // Always hide loading overlay
            co.alcheclub.lib.acccore.ads.state.AdsLoadingState.hide()
        }
    }

    // Show error message for quality ad failures
    LaunchedEffect(qualityAdError) {
        qualityAdError?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.onQualityAdErrorShown()
        }
    }

    // Navigation events live in their own StateFlow — decoupled from high-frequency UI state
    val navigationEvent by viewModel.navigationEvent.collectAsStateWithLifecycle()
    LaunchedEffect(navigationEvent) {
        navigationEvent?.let { event ->
            when (event) {
                is EditorNavigationEvent.NavigateBack -> onNavigateBack()
                is EditorNavigationEvent.NavigateToPreview -> onNavigateToPreview(event.projectId)
                is EditorNavigationEvent.NavigateToExport -> onNavigateToExport(event.projectId, event.quality)
            }
            viewModel.onNavigationHandled()
        }
    }

    // Track preview state to show fullscreen blur overlay
    var previewState by remember { mutableStateOf<com.videomaker.aimusic.modules.editor.components.PreviewState>(com.videomaker.aimusic.modules.editor.components.PreviewState.Building) }
    val isPreviewBuilding = previewState is com.videomaker.aimusic.modules.editor.components.PreviewState.Building

    Box(modifier = Modifier.fillMaxSize()) {
        // Main editor UI with Scaffold - blur when preview is building
        val editorTitle = stringResource(R.string.editor_title)
        Scaffold(
            topBar = {
                val successState = uiState as? EditorUiState.Success
                val selectedQuality = successState?.selectedQuality ?: VideoQuality.DEFAULT
                EditorTopBar(
                    selectedQuality = selectedQuality,
                    canExport = !isPreviewBuilding &&
                                (successState?.isMusicCached ?: true) &&
                                !(successState?.isCachingMusic ?: false),
                    isQualityLocked = viewModel.isQualityLocked(selectedQuality),
                    onBackClick = { requestExitFromEditor() },
                    onQualityMenuOpen = {
                        val state = currentState() ?: return@EditorTopBar
                        Analytics.trackQualityEdit(
                            videoId = state.project.id,
                            qualityNumber = state.selectedQuality.displayName
                        )
                    },
                    onQualityChange = { quality ->
                        val state = currentState()
                        if (state != null && state.selectedQuality != quality) {
                            val videoId = state.project.id
                            val qualityLabel = quality.displayName
                            Analytics.trackQualityClick(videoId, qualityLabel)
                            Analytics.trackQualitySelect(videoId, qualityLabel)
                        }
                        viewModel.updateQuality(quality)
                    },
                    onDoneClick = {
                        val state = currentState()
                        if (state != null) {
                            Analytics.trackVideoExport(
                                videoId = state.project.id,
                                templateId = state.displaySettings.effectSetId,
                                songId = state.displaySettings.musicSongId?.toString(),
                                quality = state.selectedQuality.displayName,
                                duration = state.displayProject.totalDurationMs,
                                ratioSize = state.displaySettings.aspectRatio.toAnalyticsRatioSize(),
                                volume = (state.displaySettings.audioVolume * 100f).roundToInt(),
                                mediaQuantity = state.project.assets.size
                            )
                        }
                        viewModel.onDoneClick()
                    }
                )
            },
            containerColor = SplashBackground, // #101010 (closest to #101313)
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            modifier = if (isPreviewBuilding) Modifier.blur(16.dp) else Modifier
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
                        currentPositionMs = state.currentPositionMs,
                        durationMs = state.durationMs,
                        seekToPosition = state.seekToPosition,
                        scrubToPosition = state.scrubToPosition,
                        effectSetName = state.effectSetName,
                        onPlayPauseClick = {
                            if (state.isPlaying) {
                                Analytics.trackVideoPause(state.project.id)
                            } else {
                                Analytics.trackVideoPlay(state.project.id)
                            }
                            viewModel.togglePlayback()
                        },
                        onPlaybackStateChange = viewModel::setPlaybackState,
                        onPositionUpdate = viewModel::updatePlaybackPosition,
                        onSeek = viewModel::seekTo,
                        onScrub = viewModel::scrubTo,
                        onSeekStart = viewModel::stopPlayback,
                        onSeekEnd = {}, // Resume happens in clearSeekRequest after seek completes
                        onSeekComplete = viewModel::clearSeekRequest,
                        onScrubComplete = viewModel::clearScrubRequest,
                        onPreviewStateChange = { previewState = it },
                        onEffectClick = {
                            Analytics.trackEffectEdit(
                                videoId = state.project.id,
                                templateId = state.displaySettings.effectSetId
                            )
                            showEffectSetSheet = true
                        },
                        onImageDurationClick = {
                            Analytics.trackDurationEdit(
                                videoId = state.project.id,
                                durationNumber = state.displaySettings.imageDurationMs
                            )
                            showDurationSheet = true
                        },
                        onRatioClick = {
                            Analytics.trackRatioEdit(
                                videoId = state.project.id,
                                ratioSize = state.displaySettings.aspectRatio.toAnalyticsRatioSize()
                            )
                            ratioConfirmed = false
                            showRatioSheet = true
                        },
                        onMusicClick = {
                            Analytics.trackSongEdit(
                                videoId = state.project.id,
                                songId = state.displaySettings.musicSongId?.toString() ?: "unknown",
                                songName = state.displaySettings.musicSongName ?: "unknown"
                            )
                            Analytics.trackSearchOpen(AnalyticsEvent.Value.Location.EDIT)
                            showMusicSearchSheet = true
                        },
                        onVolumeClick = {
                            Analytics.trackVolumeEdit(
                                videoId = state.project.id,
                                volumeNumber = (state.displaySettings.audioVolume * 100f).roundToInt()
                            )
                            showVolumeSheet = true
                        },
                        onTrimClick = { showMusicTrimSheet = true },
                        modifier = Modifier.padding(paddingValues)
                    )
                }
            }
        }

        // Fullscreen Settings Panel - Removed (using individual bottom sheets)
        // val successState = uiState as? EditorUiState.Success
        // AnimatedVisibility(
        //     visible = successState?.showSettingsPanel == true,
        //     enter = slideInVertically(initialOffsetY = { it }), // Slide up from bottom
        //     exit = slideOutVertically(targetOffsetY = { it })   // Slide down to bottom
        // ) {
        //     if (successState != null) {
        //         SettingsPanel(
        //             settings = successState.displaySettings,
        //             hasPendingChanges = successState.hasPendingChanges,
        //             onEffectSetChange = viewModel::updateEffectSet,
        //             onImageDurationChange = viewModel::updateImageDuration,
        //             onTransitionPercentageChange = viewModel::updateTransitionPercentage,
        //             onOverlayFrameChange = viewModel::updateOverlayFrame,
        //             onMusicSongChange = { songId -> viewModel.updateMusicSong(songId, null) },
        //             onCustomAudioChange = viewModel::updateCustomAudio,
        //             onAudioVolumeChange = viewModel::updateAudioVolume,
        //             onAspectRatioChange = viewModel::updateAspectRatio,
        //             onApplySettings = viewModel::applySettings,
        //             onDiscardSettings = viewModel::discardPendingSettings,
        //             onClose = viewModel::closeSettingsPanel,
        //             onOpenMusicPicker = { /* showMusicPicker = true */ }, // Commented out - using Supabase only
        //             modifier = Modifier.fillMaxSize()
        //         )
        //     }
        // }

        // Exit confirmation dialog - rendered last to overlay everything
        if (showExitConfirmation) {
            val successState = uiState as? EditorUiState.Success
            val hasPendingChanges = successState?.hasPendingChanges == true
            val isUnsavedProject = successState?.isUnsavedProject == true
            val videoId = successState?.project?.id

            // Only show dialog if there are pending changes
            if (hasPendingChanges) {
                if (!hasTrackedExitPopupShow) {
                    Analytics.trackExitPopupShow(AnalyticsEvent.Value.Location.VIDEO_PREVIEW)
                    hasTrackedExitPopupShow = true
                }
                ExitConfirmationDialog(
                    isUnsavedProject = isUnsavedProject,
                    onSaveAndExit = {
                        Analytics.trackExitSave(
                            videoId = videoId,
                            location = AnalyticsEvent.Value.Location.VIDEO_PREVIEW
                        )
                        showExitConfirmation = false
                        hasTrackedExitPopupShow = false
                        scope.launch {
                            // Save project (applies pending settings + saves new project)
                            if (viewModel.saveProject()) {
                                viewModel.navigateBack()
                            }
                            // If save fails, stay in editor and show error
                        }
                    },
                    onDiscardAndExit = {
                        Analytics.trackExitDiscard(
                            videoId = videoId,
                            location = AnalyticsEvent.Value.Location.VIDEO_PREVIEW
                        )
                        showExitConfirmation = false
                        hasTrackedExitPopupShow = false
                        // Discard pending changes and navigate back
                        viewModel.navigateBack()
                    },
                    onCancel = {
                        Analytics.trackExitContinue(AnalyticsEvent.Value.Location.VIDEO_PREVIEW)
                        showExitConfirmation = false
                        hasTrackedExitPopupShow = false
                    }
                )
            } else {
                // No pending changes, just navigate back
                showExitConfirmation = false
                hasTrackedExitPopupShow = false
                viewModel.navigateBack()
            }
        }

        // Music Picker Bottom Sheet - Commented out (using Supabase only)
        // if (showMusicPicker) {
        //     MusicPickerScreen(
        //         viewModel = musicPickerViewModel,
        //         onTrackSelected = { uri ->
        //             viewModel.updateCustomAudio(uri)
        //             showMusicPicker = false
        //         },
        //         onDismiss = {
        //             showMusicPicker = false
        //         }
        //     )
        // }

        // Ratio Bottom Sheet
        if (showRatioSheet) {
            val successState = uiState as? EditorUiState.Success
            if (successState != null) {
                SelectRatioBottomSheet(
                    currentRatio = successState.displaySettings.aspectRatio,
                    onDismiss = {
                        if (!ratioConfirmed) {
                            Analytics.trackRatioClose(
                                videoId = successState.project.id,
                                ratioSize = successState.displaySettings.aspectRatio.toAnalyticsRatioSize()
                            )
                        }
                        showRatioSheet = false
                    },
                    onRatioClick = { selectedRatio ->
                        Analytics.trackRatioClick(
                            videoId = successState.project.id,
                            ratioSize = selectedRatio.toAnalyticsRatioSize()
                        )
                    },
                    onConfirm = { selectedRatio ->
                        val ratioSize = selectedRatio.toAnalyticsRatioSize()
                        Analytics.trackRatioSelect(
                            videoId = successState.project.id,
                            ratioSize = ratioSize
                        )
                        ratioConfirmed = true
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
                    onDismiss = {
                        Analytics.trackDurationClose(
                            videoId = successState.project.id,
                            durationNumber = successState.displaySettings.imageDurationMs
                        )
                        showDurationSheet = false
                    },
                    onDurationClick = { selectedDurationMs ->
                        Analytics.trackDurationClick(
                            videoId = successState.project.id,
                            durationNumber = selectedDurationMs
                        )
                    },
                    onConfirm = { selectedDurationMs ->
                        Analytics.trackDurationSelect(
                            videoId = successState.project.id,
                            durationNumber = selectedDurationMs
                        )
                        viewModel.updateImageDuration(selectedDurationMs)
                        showDurationSheet = false
                    }
                )
            }
        }

        // Effect Set Bottom Sheet
        if (showEffectSetSheet) {
            val selectedEffectSetId = (uiState as? EditorUiState.Success)?.displaySettings?.effectSetId
            val currentVideoId = (uiState as? EditorUiState.Success)?.project?.id
            val currentEffectName = (uiState as? EditorUiState.Success)?.effectSetName
            EffectSetBottomSheet(
                viewModel = effectSetViewModel,
                selectedEffectSetId = selectedEffectSetId,
                onEffectSetSelected = { effectSet ->
                    val videoId = currentVideoId
                    if (videoId != null) {
                        Analytics.trackEffectClick(
                            videoId = videoId,
                            effectId = effectSet.id,
                            effectName = effectSet.name
                        )
                        Analytics.trackEffectSelect(
                            videoId = videoId,
                            effectId = effectSet.id,
                            effectName = effectSet.name
                        )
                    }
                    viewModel.updateEffectSet(effectSet.id)
                    showEffectSetSheet = false
                },
                onDismiss = {
                    val videoId = currentVideoId
                    if (videoId != null) {
                        Analytics.trackEffectClose(
                            videoId = videoId,
                            effectId = selectedEffectSetId,
                            effectName = currentEffectName
                        )
                    }
                    showEffectSetSheet = false
                }
            )
        }

        // Music Search Bottom Sheet
        if (showMusicSearchSheet) {
            // Pause video preview when music sheet is open
            LaunchedEffect(Unit) {
                val currentState = uiState
                if (currentState is EditorUiState.Success) {
                    wasPlayingBeforeMusicSheet = currentState.isPlaying
                    viewModel.stopPlayback()
                }
            }

            MusicSearchBottomSheet(
                viewModel = songSearchViewModel,
                onSongClick = { song ->
                    val videoId = currentVideoId()
                    if (videoId != null) {
                        Analytics.trackEditorSongClick(
                            videoId = videoId,
                            songId = song.id.toString(),
                            songName = song.name
                        )
                    }
                },
                onSongSelected = { song ->
                    val videoId = currentVideoId()
                    if (videoId != null) {
                        Analytics.trackEditorSongSelect(
                            videoId = videoId,
                            songId = song.id.toString(),
                            songName = song.name
                        )
                    }
                    viewModel.updateMusicTrack(
                        songId = song.id,
                        songName = song.name,
                        songUrl = song.mp3Url,
                        songCoverUrl = song.coverUrl
                    )
                    showMusicSearchSheet = false
                    // Resume playback if it was playing before
                    if (wasPlayingBeforeMusicSheet) {
                        viewModel.setPlaybackState(true)
                    }
                },
                onDismiss = {
                    val videoId = currentVideoId()
                    if (videoId != null) {
                        Analytics.trackSongClose(
                            videoId = videoId,
                            songId = currentSongId(),
                            songName = currentSongName()
                        )
                    }
                    showMusicSearchSheet = false
                    // Resume playback if it was playing before
                    if (wasPlayingBeforeMusicSheet) {
                        viewModel.setPlaybackState(true)
                    }
                }
            )
        }

        // Volume Bottom Sheet
        if (showVolumeSheet) {
            val successState = uiState as? EditorUiState.Success
            VolumeBottomSheet(
                currentVolume = successState?.displaySettings?.audioVolume ?: 1f,
                onVolumeChange = { volume ->
                    viewModel.updateAudioVolume(volume)
                },
                onVolumeClick = { volume ->
                    val videoId = currentVideoId()
                    if (videoId != null) {
                        Analytics.trackVolumeClick(
                            videoId = videoId,
                            volumeNumber = (volume * 100f).roundToInt()
                        )
                    }
                },
                onDismiss = {
                    val videoId = currentVideoId()
                    if (videoId != null) {
                        val volumeNumber = currentVolumePercent()
                        Analytics.trackVolumeSelect(videoId, volumeNumber)
                    }
                    showVolumeSheet = false
                }
            )
        }

        // Music Trimmer Bottom Sheet
        if (showMusicTrimSheet) {
            val successState = uiState as? EditorUiState.Success
            val musicTrimState by viewModel.musicTrimmerState.collectAsStateWithLifecycle()

            // Open trimmer when sheet is shown
            LaunchedEffect(Unit) {
                viewModel.openMusicTrimmer()
            }

            // Show bottom sheet when music settings is open
            if (musicTrimState is MusicTrimmerState.Open) {
                val trimState = musicTrimState as MusicTrimmerState.Open

                MusicSettingsBottomSheet(
                    songName = trimState.songName,
                    songUrl = successState?.displaySettings?.musicSongUrl ?: "",
                    songDurationMs = trimState.songDurationMs,
                    trimStartMs = trimState.trimStartMs,
                    trimEndMs = trimState.trimEndMs,
                    currentVolume = successState?.displaySettings?.audioVolume ?: 1f,
                    onTrimChange = { startMs, endMs ->
                        viewModel.updateMusicTrimPreview(startMs, endMs)
                    },
                    onVolumeChange = { volume ->
                        viewModel.updateAudioVolume(volume)
                    },
                    onDurationReady = { durationMs ->
                        viewModel.updateMusicTrimDuration(durationMs)
                    },
                    onError = { errorMessage ->
                        musicLoadError = errorMessage
                    },
                    onApply = {
                        viewModel.applyMusicTrim()
                        showMusicTrimSheet = false
                    },
                    onDismiss = {
                        viewModel.closeMusicTrimmer(applyChanges = false)
                        showMusicTrimSheet = false
                    }
                )
            }
        }

        // Fullscreen Processing Overlay - blocks all interactions, content is blurred
        if (isPreviewBuilding) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f)),
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

        // Error overlay - shows both preview errors and music trimmer errors
        // Preview errors (PreviewState.Error)
        val previewErrorMessage = (previewState as? com.videomaker.aimusic.modules.editor.components.PreviewState.Error)?.message

        // Music trimmer errors
        val trimmerErrorMessage = musicLoadError

        // Show error overlay if any error exists
        val errorMessage = previewErrorMessage ?: trimmerErrorMessage
        if (errorMessage != null) {
            ErrorOverlay(
                errorType = ErrorType.MusicLoading,
                title = stringResource(R.string.error_preview_title),
                message = errorMessage,
                onRetry = {
                    // Clear error and trigger rebuild
                    if (previewErrorMessage != null) {
                        previewState = com.videomaker.aimusic.modules.editor.components.PreviewState.Building
                    } else {
                        musicLoadError = null
                    }
                },
                onDismiss = {
                    // Clear error state
                    if (previewErrorMessage != null) {
                        previewState = com.videomaker.aimusic.modules.editor.components.PreviewState.Building
                    } else {
                        musicLoadError = null
                    }
                }
            )
        }

        // Quality unlock watch ad dialog
        if (showQualityAdDialog) {
            com.videomaker.aimusic.modules.export.WatchAdDialog(
                title = stringResource(com.videomaker.aimusic.R.string.quality_watch_ad_title),
                subtitle = stringResource(com.videomaker.aimusic.R.string.quality_watch_ad_subtitle),
                onDismiss = viewModel::onQualityAdDialogDismiss,
                onWatchAd = {
                    // Set pending flag - LaunchedEffect will handle ad presentation
                    viewModel.onQualityAdConfirmed()
                }
            )
        }

        // Ads loading overlay
        com.videomaker.aimusic.ui.components.AdsLoadingOverlay()

        // Snackbar for error messages
        androidx.compose.material3.SnackbarHost(
            hostState = snackbarHostState,
            modifier = androidx.compose.ui.Modifier.padding(16.dp)
        )

    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EditorTopBar(
    selectedQuality: VideoQuality,
    canExport: Boolean,
    isQualityLocked: Boolean,
    onBackClick: () -> Unit,
    onQualityMenuOpen: () -> Unit,
    onQualityChange: (VideoQuality) -> Unit,
    onDoneClick: () -> Unit
) {
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
            // Quality picker (reusable component) - shows [AD] badge for locked qualities
            QualityPicker(
                selectedQuality = selectedQuality,
                onQualityChange = onQualityChange,
                isQualityUnlocked = !isQualityLocked,
                onMenuOpen = onQualityMenuOpen,
                modifier = Modifier.height(40.dp)
            )

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
                Text(
                    text = stringResource(R.string.done),
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

private fun AspectRatio.toAnalyticsRatioSize(): String = when (this) {
    AspectRatio.RATIO_16_9 -> "16:9"
    AspectRatio.RATIO_9_16 -> "9:16"
    AspectRatio.RATIO_4_5 -> "4:5"
    AspectRatio.RATIO_1_1 -> "1:1"
}

@OptIn(ExperimentalMaterial3Api::class)
// MusicSection, SettingsTabBar, and SettingsTabButton moved to components/ package

@Composable
internal fun EditorMainContent(
    project: Project,
    isPlaying: Boolean,
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
    onPreviewStateChange: (com.videomaker.aimusic.modules.editor.components.PreviewState) -> Unit,
    onEffectClick: () -> Unit,
    onImageDurationClick: () -> Unit,
    onRatioClick: () -> Unit,
    onMusicClick: () -> Unit,
    onVolumeClick: () -> Unit = {},
    onTrimClick: () -> Unit = {},
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
                onPreviewStateChange = onPreviewStateChange,
                autoPlay = true,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Music Section - song info and player
        MusicSection(
            songName = project.settings.musicSongName ?: stringResource(R.string.editor_no_music_selected),
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

        // Settings Tab Bar - Effect, Music Clip, Duration, Ratio, Volume (horizontally scrollable)
        val hasMusic = project.settings.musicSongId != null || project.settings.customAudioUri != null
        SettingsTabBar(
            currentEffectSetName = effectSetName,
            currentRatio = project.settings.aspectRatio,
            currentDurationMs = project.settings.imageDurationMs,
            showMusicControls = hasMusic,
            currentVolume = project.settings.audioVolume,
            onEffectClick = onEffectClick,
            onImageDurationClick = onImageDurationClick,
            onRatioClick = onRatioClick,
            onVolumeClick = onVolumeClick,
            onClipClick = onTrimClick,
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
            .clickableSingle(
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
