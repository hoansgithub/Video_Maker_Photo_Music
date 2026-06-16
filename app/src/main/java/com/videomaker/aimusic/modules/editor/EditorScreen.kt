package com.videomaker.aimusic.modules.editor

// import com.videomaker.aimusic.di.MusicPickerViewModelFactory // Commented out - using Supabase only
// import com.videomaker.aimusic.modules.editor.components.SettingsPanel // Removed - using individual bottom sheets
// import com.videomaker.aimusic.modules.musicpicker.MusicPickerScreen // Commented out - using Supabase only
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.alcheclub.lib.acccore.ads.compose.BannerAdView
import co.alcheclub.lib.acccore.ads.compose.NativeAdView
import co.alcheclub.lib.acccore.ads.loader.AdsLoaderService
import com.videomaker.aimusic.BuildConfig
import com.videomaker.aimusic.R
import com.videomaker.aimusic.core.ads.AdClickDetector
import com.videomaker.aimusic.core.ads.AdPlacementConfigService
import com.videomaker.aimusic.core.ads.InterstitialAdHelperExt
import com.videomaker.aimusic.core.ads.RewardedAdPresenter
import com.videomaker.aimusic.core.analytics.Analytics
import com.videomaker.aimusic.core.analytics.AnalyticsEvent
import com.videomaker.aimusic.core.constants.AdPlacement
import com.videomaker.aimusic.domain.model.AspectRatio
import com.videomaker.aimusic.domain.model.EffectSet
import com.videomaker.aimusic.domain.model.Project
import com.videomaker.aimusic.domain.model.VideoQuality
import com.videomaker.aimusic.modules.editor.components.ImagesBottomSheet
import com.videomaker.aimusic.modules.editor.components.MusicSearchBottomSheet
import com.videomaker.aimusic.modules.editor.components.MusicSection
import com.videomaker.aimusic.modules.editor.components.SelectRatioBottomSheet
import com.videomaker.aimusic.modules.editor.components.SettingsTabBar
import com.videomaker.aimusic.modules.editor.components.AudioPreviewPlayer
import com.videomaker.aimusic.modules.editor.components.VolumeBottomSheet
import com.videomaker.aimusic.media.renderer.PreviewSurfaceView
import com.videomaker.aimusic.media.renderer.PlaybackClock
import com.videomaker.aimusic.media.renderer.RenderState
import com.videomaker.aimusic.ui.components.ModifierExtension.clickableSingle
import com.videomaker.aimusic.ui.components.QualityPicker
import com.videomaker.aimusic.ui.theme.SplashBackground
import com.videomaker.aimusic.ui.theme.TextPrimary
import com.videomaker.aimusic.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import kotlin.math.roundToInt

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
    onNavigateToAddAssets: (projectId: String, assetUris: List<String>, songId: Long, hookStartMs: Long) -> Unit
) {
    val adClickDetector: AdClickDetector = koinInject()
    val adPlacementConfigService: AdPlacementConfigService = koinInject()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val shouldPresentQualityAd by viewModel.shouldPresentQualityAd.collectAsStateWithLifecycle()
    val qualityAdError by viewModel.qualityAdError.collectAsStateWithLifecycle()
    val isQualityUnlocked by viewModel.isQualityUnlocked.collectAsStateWithLifecycle()
    val showBeatSyncErrorDialog by viewModel.showBeatSyncErrorDialog.collectAsStateWithLifecycle()
    val renderState by viewModel.renderState.collectAsStateWithLifecycle()
    // Position/duration are separate StateFlows — only slider/time-label recompose on tick
    val currentPositionMs by viewModel.currentPositionMs.collectAsStateWithLifecycle()
    val durationMs by viewModel.durationMs.collectAsStateWithLifecycle()

    // Pause playback when screen pauses, resume when screen resumes
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> viewModel.onScreenPause()
                Lifecycle.Event.ON_RESUME -> viewModel.onScreenResume()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    var showExitConfirmation by remember { mutableStateOf(false) }
    // var showMusicPicker by remember { mutableStateOf(false) } // Commented out - using Supabase only
    var showRatioSheet by remember { mutableStateOf(false) }
    var showEffectSetSheet by remember { mutableStateOf(false) }
    var showMusicSearchSheet by remember { mutableStateOf(false) }
    var showVolumeSheet by remember { mutableStateOf(false) }
    var showImagesSheet by remember { mutableStateOf(false) }
    var isEditingImages by remember { mutableStateOf(false) } // Track when user is editing images to prevent video rebuild
    var wasPlayingBeforeMusicSheet by remember { mutableStateOf(false) }
    var wasPlayingBeforeQualityAd by remember { mutableStateOf(false) }
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
    val songSearchViewModel: com.videomaker.aimusic.modules.songsearch.SongSearchViewModel =
        koinViewModel()

    fun currentState(): EditorUiState.Success? = uiState as? EditorUiState.Success

    fun currentVideoId(): String? = currentState()?.project?.id

    fun currentTemplateId(): String? = currentState()?.displaySettings?.templateId

    fun currentSongId(): String =
        currentState()?.displaySettings?.primaryAudioNode?.songId?.toString() ?: "unknown"

    fun currentSongName(): String =
        currentState()?.displaySettings?.primaryAudioNode?.songName ?: "unknown"

    fun currentRatioLabel(): String =
        currentState()?.displaySettings?.aspectRatio?.toAnalyticsRatioSize()
            ?: AspectRatio.RATIO_9_16.toAnalyticsRatioSize()

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

    LaunchedEffect(
        successStateForTracking?.project?.id,
        currentPositionMs
    ) {
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
        if (!hasTrackedVideoPreviewComplete && currentPositionMs >= 3000L) {
            Analytics.trackVideoPreviewComplete(
                videoId = videoId,
                templateId = state.displaySettings.templateId
            )
            hasTrackedVideoPreviewComplete = true
        }
    }

    // Handle quality ad presentation using reusable presenter
    RewardedAdPresenter(
        shouldPresent = shouldPresentQualityAd,
        placement = AdPlacement.REWARD_UNLOCK_QUALITY,
        adsLoaderService = adsLoaderService,
        onRewardEarned = viewModel::onQualityRewardEarned,
        onAdFailed = viewModel::onQualityAdFailed,
        onAdShown = {
            val state = currentState()
            if (state != null) {
                wasPlayingBeforeQualityAd = state.isPlaying
                if (state.isPlaying) {
                    viewModel.setPlaybackState(false)
                }
            }
        },
        onAdClosed = {
            if (wasPlayingBeforeQualityAd) {
                viewModel.setPlaybackState(true)
            }
            wasPlayingBeforeQualityAd = false
        }
    )

    // Show error message for quality ad failures
    LaunchedEffect(qualityAdError) {
        qualityAdError?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.onQualityAdErrorShown()
        }
    }

    // Navigation events use Channel pattern (Google official) — one-time delivery, no replay
    LaunchedEffect(Unit) {
        viewModel.navigationEvent.collect { event ->
            when (event) {
                is EditorNavigationEvent.RequestBackWithAd -> {
                    if (event.shouldShowAd && activity != null) {
                        // Show back button interstitial ad
                        com.videomaker.aimusic.core.ads.InterstitialAdHelperExt.showInterstitial(
                            adsLoaderService = adsLoaderService,
                            activity = activity,
                            placement = com.videomaker.aimusic.core.constants.AdPlacement.INTERSTITIAL_EDITOR_BACK,
                            action = { onNavigateBack() },  // Navigate after ad closes
                            onShown = {
                                // Pause preview playback when ad shows
                                viewModel.stopPlayback()
                            },
                            bypassFrequencyCap = false,  // Respect frequency cap
                            showLoadingOverlay = false  // Background preloaded, no overlay
                        )
                    } else {
                        // Ad not ready or no activity - navigate immediately
                        onNavigateBack()
                    }
                }

                is EditorNavigationEvent.NavigateToPreview -> onNavigateToPreview(event.projectId)
                is EditorNavigationEvent.NavigateToExport -> onNavigateToExport(
                    event.projectId,
                    event.quality
                )

                is EditorNavigationEvent.RequestQualityInterstitial -> {
                    if (activity != null) {
                        InterstitialAdHelperExt.showInterstitial(
                            adsLoaderService = adsLoaderService,
                            activity = activity,
                            placement = AdPlacement.INTERSTITIAL_UNLOCK_QUALITY,
                            action = {
                                // Called after user closes the ad or ad fails to show
                                android.util.Log.d("EditorScreen", "✅ Quality interstitial closed - proceeding to export")
                                viewModel.onQualityInterstitialClosed()
                            },
                            bypassFrequencyCap = true,
                            loadTimeoutMillis = 30_000L, // 30 second timeout (matches rewarded ad)
                            showLoadingOverlay = true // Show loading indicator while ad loads
                        )
                    } else {
                        // activity null (rare) — unlock for free and proceed
                        viewModel.onQualityInterstitialClosed()
                    }
                }
            }
            // Event auto-consumed by Channel - no manual cleanup needed
        }
    }

    // Show the "after prepare" interstitial once: editor appears (Loading -> Success),
    // stays for 1s, then the (preloaded) fullscreen-image interstitial is shown.
    var afterPrepareInterShown by remember { mutableStateOf(false) }
    val isEditorReady = uiState is EditorUiState.Success
    LaunchedEffect(isEditorReady) {
        if (isEditorReady && !afterPrepareInterShown && activity != null) {
            afterPrepareInterShown = true
            delay(1000)
            InterstitialAdHelperExt.showInterstitial(
                adsLoaderService = adsLoaderService,
                activity = activity,
                placement = AdPlacement.INTERSTITIAL_EDITOR_AFTER_PREPARE,
                action = { /* no-op: keep editing after the ad closes */ },
                onShown = { viewModel.stopPlayback() },
                bypassFrequencyCap = true,   // always show right after prepare
                showLoadingOverlay = false   // background preloaded, don't block editing
            )
        }
    }

    // Track preview readiness — GL renderer is instant, so preview is ready
    // once renderState has images. No CompositionPlayer build step.
    var hasBeenReady by remember { mutableStateOf(false) }
    LaunchedEffect(renderState) {
        if (renderState.imageUris.isNotEmpty()) {
            hasBeenReady = true
        }
    }
    val isProcessingAudio = (uiState as? EditorUiState.Success)?.isProcessingAudio == true
    // Show overlay before initial render OR during music change processing
    val showComposingOverlay = !hasBeenReady || isProcessingAudio

    Column(
        modifier = Modifier
            .fillMaxSize()
    ) {
        Box(modifier = Modifier.weight(1f)) {
            // Main editor UI with Scaffold - blur when preview is building
            val editorTitle = stringResource(R.string.editor_title)
            Scaffold(
                topBar = {
                    val successState = uiState as? EditorUiState.Success
                    val selectedQuality = successState?.selectedQuality ?: VideoQuality.DEFAULT
                    Box {
                        androidx.compose.animation.AnimatedVisibility(
                            visible = !showEffectSetSheet,
                            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut()
                        ) {
                            EditorTopBar(
                                selectedQuality = selectedQuality,
                                canExport = !showComposingOverlay &&
                                        !isProcessingAudio &&
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
                                            templateId = state.displaySettings.templateId,
                                            songId = state.displaySettings.primaryAudioNode?.songId?.toString(),
                                            quality = state.selectedQuality.displayName,
                                            duration = state.displayProject.totalDurationMs,
                                            ratioSize = state.displaySettings.aspectRatio.toAnalyticsRatioSize(),
                                            volume = ((state.displaySettings.primaryAudioNode?.volume ?: 1f) * 100f).roundToInt(),
                                            mediaQuantity = state.project.assets.size
                                        )
                                    }
                                    viewModel.onDoneClick()
                                }
                            )
                        }
                    }
                },
                containerColor = SplashBackground, // #101010 (closest to #101313)
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                modifier = if (showComposingOverlay) Modifier.blur(16.dp) else Modifier
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
                            project = state.previewProject, // Use previewProject: pendingSettings but actual assets
                            isPlaying = state.isPlaying,
                            currentPositionMs = currentPositionMs,
                            durationMs = durationMs,
                            seekToPosition = state.seekToPosition,
                            scrubToPosition = state.scrubToPosition,
                            effectSetName = state.effectSetName,
                            renderState = renderState,
                            playbackClock = viewModel.playbackClock,
                            onPlayPauseClick = {
                                if (state.isPlaying) {
                                    Analytics.trackVideoPause(state.project.id)
                                } else {
                                    Analytics.trackVideoPlay(state.project.id)
                                }
                                viewModel.togglePlayback()
                            },
                            onSeek = viewModel::seekTo,
                            onScrub = viewModel::scrubTo,
                            onSeekStart = viewModel::stopPlayback,
                            onSeekEnd = {}, // Resume happens in clearSeekRequest after seek completes
                            onSeekComplete = viewModel::clearSeekRequest,
                            onScrubComplete = viewModel::clearScrubRequest,
                            onImagesClick = {
                                Analytics.trackPhotoEdit()
                                showImagesSheet = true
                            },
                            onEffectClick = {
                                Analytics.trackEffectEdit(
                                    videoId = state.project.id,
                                    templateId = state.displaySettings.templateId
                                )
                                showEffectSetSheet = true
                            },
                            onRatioClick = {
                                Analytics.trackRatioEdit(
                                    videoId = state.project.id,
                                    ratioSize = state.displaySettings.aspectRatio.toAnalyticsRatioSize()
                                )
                                ratioConfirmed = false
                                showRatioSheet = true
                            },
                            onVolumeClick = {
                                Analytics.trackVolumeEdit(
                                    videoId = state.project.id,
                                    volumeNumber = ((state.displaySettings.primaryAudioNode?.volume ?: 1f) * 100f).roundToInt()
                                )
                                showVolumeSheet = true
                            },
                            onMusicSelectorClick = {
                                val videoId = currentVideoId()
                                if (videoId != null) {
                                    Analytics.trackSongEdit(
                                        videoId = videoId,
                                        songId = currentSongId(),
                                        songName = currentSongName(),
                                        location = AnalyticsEvent.Value.Location.VIDEO_EDITOR
                                    )
                                }
                                wasPlayingBeforeMusicSheet = state.isPlaying
                                if (state.isPlaying) viewModel.stopPlayback()
                                showMusicSearchSheet = true
                            },
                            showEffectSetPanel = showEffectSetSheet,
                            effectSetViewModel = effectSetViewModel,
                            onEffectPanelDismiss = {
                                val videoId = currentVideoId()
                                if (videoId != null) {
                                    Analytics.trackEffectClose(
                                        videoId = videoId,
                                        effectId = state.displaySettings.effectSetId,
                                        effectName = state.effectSetName
                                    )
                                }
                                showEffectSetSheet = false
                            },
                            onEffectSetSelected = { effectSet ->
                                val videoId = currentVideoId()
                                if (videoId != null) {
                                    Analytics.trackEffectClick(
                                        videoId = videoId,
                                        effectId = effectSet.id,
                                        effectName = effectSet.name,
                                        isPremium = effectSet.isPremium
                                    )
                                    Analytics.trackEffectSelect(
                                        videoId = videoId,
                                        effectId = effectSet.id,
                                        effectName = effectSet.name,
                                        isPremium = effectSet.isPremium
                                    )
                                }
                                viewModel.updateEffectSet(effectSet.id)
                            },
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

            // Inline panel replaces bottom sheets, so we only handle non-effect sheets here

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
                                songName = song.name,
                                isPremium = song.isPremium,
                                location = AnalyticsEvent.Value.Location.VIDEO_EDITOR
                            )
                        }
                    },
                    onSongSelected = { song ->
                        val videoId = currentVideoId()
                        if (videoId != null) {
                            Analytics.trackEditorSongSelect(
                                videoId = videoId,
                                songId = song.id.toString(),
                                songName = song.name,
                                isPremium = song.isPremium,
                                location = AnalyticsEvent.Value.Location.VIDEO_EDITOR
                            )
                        }
                        // ViewModel handles seek-to-0 + auto-play after preprocessing
                        viewModel.updateMusicTrack(
                            songId = song.id,
                            songName = song.name,
                            songUrl = song.mp3Url,
                            songCoverUrl = song.coverUrl
                        )
                        showMusicSearchSheet = false
                    },
                    onDismiss = {
                        val videoId = currentVideoId()
                        if (videoId != null) {
                            Analytics.trackSongClose(
                                videoId = videoId,
                                songId = currentSongId(),
                                songName = currentSongName(),
                                location = AnalyticsEvent.Value.Location.VIDEO_EDITOR
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
                    currentVolume = successState?.displaySettings?.primaryAudioNode?.volume ?: 1f,
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
                    onDismiss = { selectedVolume, didSelect ->
                        val videoId = currentVideoId()
                        if (videoId != null) {
                            val volumeNumber = (selectedVolume * 100f).roundToInt()
                            if (didSelect) {
                                Analytics.trackVolumeSelect(videoId, volumeNumber)
                            } else {
                                Analytics.trackVolumeClose(videoId, volumeNumber)
                            }
                        }
                        showVolumeSheet = false
                    }
                )
            }

            // Collect selections from AssetPicker via Flow
            // Flow-based approach: reactive, no polling, no cancellation issues
            LaunchedEffect(Unit) {
                com.videomaker.aimusic.modules.picker.AssetSelectionCache.selectionFlow.collect { selectedUris ->
                    android.util.Log.d("EditorScreen", "Received ${selectedUris.size} selected assets from AssetPicker")
                    // Save project and rebuild video with new assets
                    viewModel.replaceAssetsFromUris(selectedUris)
                    // Clear pending assets and close sheet
                    viewModel.discardPendingAssets()
                    isEditingImages = false
                    showImagesSheet = false
                }
            }

            // Images Bottom Sheet
            if (showImagesSheet) {
                val successState = uiState as? EditorUiState.Success
                if (successState != null) {
                    // Set pending assets when sheet opens (first time only)
                    LaunchedEffect(Unit) {
                        if (successState.pendingAssets == null) {
                            viewModel.setPendingAssets(successState.project.assets)
                            isEditingImages = true
                        }
                    }

                    ImagesBottomSheet(
                        currentAssets = successState.displayAssets,
                        onDismiss = {
                            Analytics.trackPhotoClose()
                            viewModel.discardPendingAssets()
                            isEditingImages = false
                            showImagesSheet = false
                        },
                        onAddImages = {
                            // Navigate to asset picker in editing mode.
                            // Pass the project's song + hook start so the picker's duration estimate
                            // matches what the editor will re-render after assets change.
                            val currentAssetUris = successState.displayAssets.map { it.uri.toString() }
                            onNavigateToAddAssets(
                                successState.project.id,
                                currentAssetUris,
                                successState.project.settings.primaryAudioNode?.songId ?: -1L,
                                successState.project.settings.hookStartTimeMs
                            )
                        },
                        onConfirm = { updatedAssets ->
                            // Close sheet immediately — GL renderer updates instantly
                            isEditingImages = false
                            showImagesSheet = false
                            scope.launch {
                                viewModel.applyPendingAssets(updatedAssets)
                            }
                        }
                    )
                }
            }

            // Fullscreen Processing Overlay - blocks all interactions, content is blurred
            if (showComposingOverlay) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.6f))
                        .clickable(
                            enabled = true,
                            onClick = { /* Block all clicks */ },
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
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

            // Preview error overlay removed — no CompositionPlayer to produce errors.
            // Audio errors are handled by ExoPlayer internally (retry/fallback).

            // Network error dialog (beat-sync or effect set loading failure)
            if (showBeatSyncErrorDialog) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = viewModel::onBeatSyncErrorDismissed,
                    title = { androidx.compose.material3.Text(stringResource(R.string.error_network_title)) },
                    text = { androidx.compose.material3.Text(stringResource(R.string.error_data_load_failed)) },
                    confirmButton = {
                        androidx.compose.material3.TextButton(onClick = viewModel::onBeatSyncErrorDismissed) {
                            androidx.compose.material3.Text(stringResource(R.string.dialog_ok))
                        }
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

        // Remote Config toggle: native ad (default) or standard banner
        if (adPlacementConfigService.bannerUseNative) {
            NativeAdView(
                placement = AdPlacement.NATIVE_EDITOR_BANNER,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp),
                isDebug = BuildConfig.DEBUG,
                onAdClicked = { adClickDetector.onAdClick(it) }
            )
        } else {
            BannerAdView(
                placement = AdPlacement.BANNER_EDITOR,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                onAdClicked = { adClickDetector.onAdClick(it) }
            )
        }
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
    renderState: RenderState,
    playbackClock: PlaybackClock,
    onPlayPauseClick: () -> Unit,
    onSeek: (Long) -> Unit,
    onScrub: (Long) -> Unit,
    onSeekStart: () -> Unit,
    onSeekEnd: () -> Unit,
    onSeekComplete: () -> Unit,
    onScrubComplete: () -> Unit,
    onImagesClick: () -> Unit,
    onEffectClick: () -> Unit,
    onRatioClick: () -> Unit,
    onVolumeClick: () -> Unit = {},
    onMusicSelectorClick: () -> Unit = {},
    showEffectSetPanel: Boolean,
    effectSetViewModel: EffectSetViewModel,
    onEffectPanelDismiss: () -> Unit,
    onEffectSetSelected: (EffectSet) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
    ) {
        // Real-time Video Preview using GL renderer (instant property changes)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            PreviewSurfaceView(
                renderState = renderState,
                playbackClock = playbackClock,
                isPlaying = isPlaying,
                modifier = Modifier
                    .fillMaxSize()
                    .aspectRatio(renderState.aspectRatio.ratio)
            )

            // Audio-only player: syncs ExoPlayer audio to PlaybackClock.
            // No CompositionPlayer — no GPU composition rebuilds on effect/ratio changes.
            AudioPreviewPlayer(
                audioNodes = project.settings.audioNodes,
                hookStartTimeMs = project.settings.hookStartTimeMs,
                isPlaying = isPlaying,
                playbackClock = playbackClock,
                seekToPosition = seekToPosition,
                scrubToPosition = scrubToPosition,
                onSeekComplete = onSeekComplete,
                onScrubComplete = onScrubComplete
            )
        }

        AnimatedContent(
            targetState = showEffectSetPanel,
            transitionSpec = {
                if (targetState) {
                    (slideInVertically(initialOffsetY = { it }) + fadeIn()) togetherWith
                            (slideOutVertically(targetOffsetY = { it }) + fadeOut())
                } else {
                    (slideInVertically(initialOffsetY = { -it }) + fadeIn()) togetherWith
                            (slideOutVertically(targetOffsetY = { it }) + fadeOut())
                }
            },
            label = "bottom_controls_transition"
        ) { isEffectOpen ->
            if (isEffectOpen) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    EditorPlayerControls(
                        currentPositionMs = currentPositionMs,
                        durationMs = durationMs,
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
                        modifier = Modifier.fillMaxWidth()
                    )
                    com.videomaker.aimusic.modules.editor.components.EffectSetPanel(
                        viewModel = effectSetViewModel,
                        selectedEffectSetId = project.settings.effectSetId,
                        onDismiss = onEffectPanelDismiss,
                        onEffectSetSelected = onEffectSetSelected,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(340.dp)
                    )
                }
            } else {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Music Section - song info and player (reads from primary audio node)
                    val primaryNode = project.settings.primaryAudioNode
                    MusicSection(
                        songName = primaryNode?.songName
                            ?: stringResource(R.string.editor_no_music_selected),
                        artistName = primaryNode?.songArtist ?: "",
                        coverUrl = primaryNode?.coverUrl ?: "",
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
                        onMusicSelectorClick = onMusicSelectorClick
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Separator
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(Color.White.copy(alpha = 0.1f))
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Settings Tab Bar - Images, Effect, Ratio, Volume (horizontally scrollable)
                    val hasMusic = project.settings.audioNodes.isNotEmpty()
                    SettingsTabBar(
                        currentImageCount = project.assets.size,
                        currentEffectSetName = effectSetName,
                        currentRatio = project.settings.aspectRatio,
                        showMusicControls = hasMusic,
                        currentVolume = primaryNode?.volume ?: 1f,
                        onImagesClick = onImagesClick,
                        onEffectClick = onEffectClick,
                        onRatioClick = onRatioClick,
                        onVolumeClick = onVolumeClick,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditorPlayerControls(
    currentPositionMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    onSeek: (Float) -> Unit,
    onScrub: (Float) -> Unit = {},
    onSeekStart: () -> Unit = {},
    onSeekEnd: () -> Unit = {},
    onPlayPauseClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sliderInteractionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    var isDragging by remember { androidx.compose.runtime.mutableStateOf(false) }
    var localPosition by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    var lastScrubTime by remember { androidx.compose.runtime.mutableLongStateOf(0L) }

    val currentPosition = if (durationMs > 0) currentPositionMs / durationMs.toFloat() else 0f

    if (!isDragging) {
        localPosition = currentPosition
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Play/Pause button
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.1f))
                .clickable(onClick = onPlayPauseClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Slider
        androidx.compose.material3.Slider(
            value = localPosition.coerceIn(0f, 1f),
            onValueChange = { newValue ->
                if (!isDragging) {
                    isDragging = true
                    onSeekStart()
                }
                localPosition = newValue

                val currentTime = System.currentTimeMillis()
                if (currentTime - lastScrubTime >= 150L) {
                    lastScrubTime = currentTime
                    onScrub(newValue)
                }
            },
            onValueChangeFinished = {
                isDragging = false
                onSeek(localPosition)
                onSeekEnd()
            },
            modifier = Modifier.weight(1f),
            colors = androidx.compose.material3.SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color.White,
                inactiveTrackColor = Color.White.copy(alpha = 0.3f),
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent
            ),
            thumb = {
                androidx.compose.material3.SliderDefaults.Thumb(
                    interactionSource = sliderInteractionSource,
                    thumbSize = androidx.compose.ui.unit.DpSize(14.dp, 14.dp),
                    colors = androidx.compose.material3.SliderDefaults.colors(thumbColor = Color.White)
                )
            },
            track = { sliderState ->
                androidx.compose.material3.SliderDefaults.Track(
                    sliderState = sliderState,
                    modifier = Modifier.height(3.dp),
                    colors = androidx.compose.material3.SliderDefaults.colors(
                        activeTrackColor = Color.White,
                        inactiveTrackColor = Color.White.copy(alpha = 0.3f),
                        activeTickColor = Color.Transparent,
                        inactiveTickColor = Color.Transparent
                    ),
                    drawStopIndicator = null
                )
            }
        )

        Spacer(modifier = Modifier.width(12.dp))

        // Time stamp text
        Text(
            text = formatTime(currentPositionMs),
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val hundredths = (ms % 1000) / 10
    return String.format(java.util.Locale.US, "%d:%02d.%02d", minutes, seconds, hundredths)
}

// SelectRatioBottomSheet, RatioOptionCard, AspectRatioIcon, DurationBottomSheet,
// and MusicSearchBottomSheet moved to components/ package
