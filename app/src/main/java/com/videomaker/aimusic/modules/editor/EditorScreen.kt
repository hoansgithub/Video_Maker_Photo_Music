package com.videomaker.aimusic.modules.editor

// import com.videomaker.aimusic.di.MusicPickerViewModelFactory // Commented out - using Supabase only
// import com.videomaker.aimusic.modules.editor.components.SettingsPanel // Removed - using individual bottom sheets
// import com.videomaker.aimusic.modules.musicpicker.MusicPickerScreen // Commented out - using Supabase only

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.alcheclub.lib.acccore.ads.compose.BannerAdView
import co.alcheclub.lib.acccore.ads.compose.NativeAdView
import co.alcheclub.lib.acccore.ads.loader.AdsLoaderService
import coil.compose.SubcomposeAsyncImage
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
import com.videomaker.aimusic.media.renderer.PlaybackClock
import com.videomaker.aimusic.media.renderer.PreviewSurfaceView
import com.videomaker.aimusic.media.renderer.RenderState
import com.videomaker.aimusic.modules.editor.components.AudioPreviewPlayer
import com.videomaker.aimusic.modules.editor.components.EffectSetPanel
import com.videomaker.aimusic.modules.editor.components.ImagesBottomSheet
import com.videomaker.aimusic.modules.editor.components.MusicSearchBottomSheet
import com.videomaker.aimusic.modules.editor.components.PlayMusicSlider
import com.videomaker.aimusic.modules.editor.components.RatioPanel
import com.videomaker.aimusic.modules.editor.components.SettingsTabBar
import com.videomaker.aimusic.modules.editor.components.TextBottomSheet
import com.videomaker.aimusic.modules.editor.components.TextOverlayCanvas
import com.videomaker.aimusic.modules.editor.components.VolumeBottomSheet
import com.videomaker.aimusic.ui.components.AppAsyncImage
import com.videomaker.aimusic.ui.components.EditorErrorDialog
import com.videomaker.aimusic.ui.components.ModifierExtension.clickableSingle
import com.videomaker.aimusic.ui.components.QualityPickerV2
import com.videomaker.aimusic.ui.components.ShimmerPlaceholder
import com.videomaker.aimusic.ui.theme.Neutral_N100
import com.videomaker.aimusic.ui.theme.Neutral_N600
import com.videomaker.aimusic.ui.theme.Primary
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
    onNavigateToHome: () -> Unit,
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
    var showTextSheet by remember { mutableStateOf(false) }
    var textFocusTrigger by remember { mutableStateOf(0L) }
    var effectSetIdBeforePanel by remember { mutableStateOf<String?>(null) }
    var showMusicSearchSheet by remember { mutableStateOf(false) }
    var showVolumeSheet by remember { mutableStateOf(false) }
    var showImagesSheet by remember { mutableStateOf(false) }
    var isEditingImages by remember { mutableStateOf(false) } // Track when user is editing images to prevent video rebuild
    var wasPlayingBeforeMusicSheet by remember { mutableStateOf(false) }
    var wasPlayingBeforeQualityAd by remember { mutableStateOf(false) }
    var hasTrackedVideoPreview by remember { mutableStateOf(false) }
    var hasTrackedVideoPreviewComplete by remember { mutableStateOf(false) }
    var hasTrackedExitPopupShow by remember { mutableStateOf(false) }
    var ratioBeforePanel by remember { mutableStateOf<AspectRatio?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(viewModel) {
        viewModel.selectedTextOverlayId.collect { id ->
            if (id != null) {
                showTextSheet = true
            } else {
                showTextSheet = false
            }
        }
    }
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

    // Track editor screen render / failure once at launch
    var hasTrackedEditorLaunch by remember { mutableStateOf(false) }
    LaunchedEffect(uiState) {
        if (hasTrackedEditorLaunch) return@LaunchedEffect
        when (uiState) {
            is EditorUiState.Success -> {
                hasTrackedEditorLaunch = true
                Analytics.trackVideoEditorRender()
            }
            is EditorUiState.Error -> {
                hasTrackedEditorLaunch = true
                Analytics.trackVideoPreviewFailed(videoId = "")
            }
            else -> {}
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
                    val currentActivity = activity
                    if (currentActivity != null &&
                        adsLoaderService.isInterstitialReady(AdPlacement.INTERSTITIAL_UNLOCK_QUALITY)
                    ) {
                        // Ad was preloaded on editor launch and is ready — show it
                        InterstitialAdHelperExt.showInterstitial(
                            adsLoaderService = adsLoaderService,
                            activity = currentActivity,
                            placement = AdPlacement.INTERSTITIAL_UNLOCK_QUALITY,
                            action = {
                                android.util.Log.d(
                                    "EditorScreen",
                                    "✅ Quality interstitial closed - proceeding to export"
                                )
                                viewModel.onQualityInterstitialClosed()
                            },
                            bypassFrequencyCap = true,
                            loadTimeoutMillis = 0L,
                            showLoadingOverlay = false
                        )
                    } else {
                        // Ad not ready (still loading or failed) — skip and proceed to export
                        android.util.Log.d("EditorScreen", "⏭️ Quality interstitial not ready - skipping")
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
            // Remember whether the preview was playing so we can resume it after the ad closes.
            // This pairs with onScreenPause()/onScreenResume(): whichever callback captures the
            // play state first wins, the other sees isPlaying=false — so we resume exactly once
            // regardless of the onShown vs ON_PAUSE ordering.
            var wasPlayingBeforeAfterPrepareAd = false
            InterstitialAdHelperExt.showInterstitial(
                adsLoaderService = adsLoaderService,
                activity = activity,
                placement = AdPlacement.INTERSTITIAL_EDITOR_AFTER_PREPARE,
                action = {
                    // Called after the ad closes (or fails to show) — resume only if we paused
                    // for it. If the ad never showed, onShown didn't run and this is a no-op.
                    if (wasPlayingBeforeAfterPrepareAd) viewModel.setPlaybackState(true)
                },
                onShown = {
                    wasPlayingBeforeAfterPrepareAd = currentState()?.isPlaying == true
                    viewModel.setPlaybackState(false)
                },
                bypassFrequencyCap = true,   // always show right after prepare
                showLoadingOverlay = false   // background preloaded, don't block editing
            )
        }
    }

    // True once the GL renderer has images loaded (replaces CompositionPlayer Ready state).
    // NOTE: plain remember → resets when the editor is recomposed fresh (e.g. returning from the
    // asset picker), which is what we want for the placeholder cover during a replace rebuild.
    var hasBeenReady by remember { mutableStateOf(false) }
    // Same signal but kept in the ViewModel (survives the asset-picker round trip and config
    // changes), so editorScreenState stays READY across replace/effect and only reports LOADING
    // for the very first load. Reset by the VM on a fresh load (initial/retry).
    val hasPreviewBeenReady by viewModel.hasPreviewBeenReady.collectAsStateWithLifecycle()
    // Drive hasPreviewBeenReady from renderState instead of CompositionPlayer
    LaunchedEffect(renderState) {
        if (renderState.imageUris.isNotEmpty()) {
            hasBeenReady = true
            viewModel.markPreviewReady()
        }
    }
    val isProcessingAudio = (uiState as? EditorUiState.Success)?.isProcessingAudio == true

    // Combined screen state:
    // - LOADING until data + preview ready, and also during audio reprocessing (music change).
    // - READY when everything is loaded and no processing is in progress.
    // - ERROR on data-load failure or preview build failure.
    val editorScreenState = when {
        uiState.contentState == EditorContentState.ERROR ->
            EditorScreenState.ERROR

        uiState.contentState == EditorContentState.SUCCESS && hasPreviewBeenReady && !isProcessingAudio ->
            EditorScreenState.READY

        else -> EditorScreenState.LOADING
    }

    // Remember the last known first-image URI + aspect ratio from the Loading state so the
    // Error state keeps showing the same placeholder view (Error doesn't carry them itself).
    var lastFirstImageUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var lastAspectRatio by remember { mutableStateOf(AspectRatio.RATIO_9_16) }
    LaunchedEffect(uiState) {
        (uiState as? EditorUiState.Loading)?.let { loading ->
            loading.firstImageUri?.let { lastFirstImageUri = it }
            lastAspectRatio = loading.aspectRatio
        }
    }
    // Aspect ratio used to frame the placeholder exactly like the video preview.
    val previewAspectRatio =
        (uiState as? EditorUiState.Success)?.displaySettings?.aspectRatio?.ratio
            ?: lastAspectRatio.ratio

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SplashBackground)
            .statusBarsPadding()
    ) {
        val successState = uiState as? EditorUiState.Success
        val selectedQuality = successState?.selectedQuality ?: VideoQuality.DEFAULT

        Box(modifier = Modifier.weight(1f)) {
            // All three states render EditorMainContent. Loading/Error show the first-image
            // placeholder (controls become a shimmer skeleton); Success shows the real editor.
            EditorMainContent(
                currentState = editorScreenState,
                contentState = uiState.contentState,
                project = successState?.previewProject, // Use previewProject: pendingSettings but actual assets
                firstImageUri = lastFirstImageUri,
                previewAspectRatio = previewAspectRatio,
                isPreviewReady = hasBeenReady,
                renderState = renderState,
                playbackClock = viewModel.playbackClock,
                isPlaying = successState?.isPlaying ?: false,
                currentPositionMs = currentPositionMs,
                durationMs = durationMs,
                seekToPosition = successState?.seekToPosition,
                scrubToPosition = successState?.scrubToPosition,
                effectSetName = successState?.effectSetName ?: "",
                onPlayPauseClick = {
                    val state = currentState() ?: return@EditorMainContent
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
                    val state = currentState() ?: return@EditorMainContent
                    Analytics.trackEffectEdit(
                        videoId = state.project.id,
                        templateId = state.displaySettings.templateId
                    )
                    effectSetIdBeforePanel = state.displaySettings.effectSetId
                    showEffectSetSheet = true
                },
                onTextClick = {
                    val state = currentState() ?: return@EditorMainContent
                    val defaultText = context.getString(R.string.text_overlay_placeholder)
                    viewModel.addTextOverlay(defaultText)
                    showTextSheet = true
                },
                onRatioClick = {
                    val state = currentState() ?: return@EditorMainContent
                    Analytics.trackRatioEdit(
                        videoId = state.project.id,
                        ratioSize = state.displaySettings.aspectRatio.toAnalyticsRatioSize()
                    )
                    ratioBeforePanel = state.displaySettings.aspectRatio
                    showRatioSheet = true
                },
                onVolumeClick = {
                    val state = currentState() ?: return@EditorMainContent
                    Analytics.trackVolumeEdit(
                        videoId = state.project.id,
                        volumeNumber = ((state.displaySettings.primaryAudioNode?.volume ?: 1f) * 100f).roundToInt()
                    )
                    showVolumeSheet = true
                },
                onMusicSelectorClick = {
                    val state = currentState() ?: return@EditorMainContent
                    Analytics.trackSongEdit(
                        videoId = state.project.id,
                        songId = currentSongId(),
                        songName = currentSongName(),
                        location = AnalyticsEvent.Value.Location.VIDEO_EDITOR
                    )
                    wasPlayingBeforeMusicSheet = state.isPlaying
                    if (state.isPlaying) viewModel.stopPlayback()
                    showMusicSearchSheet = true
                },
                showEffectSetPanel = showEffectSetSheet,
                effectSetViewModel = effectSetViewModel,
                onEffectPanelDismiss = {
                    val state = currentState()
                    if (state != null) {
                        Analytics.trackEffectClose(
                            videoId = state.project.id,
                            effectId = state.displaySettings.effectSetId,
                            effectName = state.effectSetName
                        )
                    }
                    // Only revert if the effect actually changed during preview
                    if (state?.displaySettings?.effectSetId != effectSetIdBeforePanel) {
                        viewModel.updateEffectSet(effectSetIdBeforePanel)
                        effectSetViewModel.setSelectedEffectSetId(effectSetIdBeforePanel)
                    }
                    showEffectSetSheet = false
                },
                onEffectPanelConfirm = {
                    // Effect is already applied via preview — just track and close
                    val selectedId = effectSetViewModel.activeEffectSetId.value
                    val state = currentState()
                    if (state != null) {
                        Analytics.trackEffectClose(
                            videoId = state.project.id,
                            effectId = state.displaySettings.effectSetId,
                            effectName = state.effectSetName
                        )
                    }
                    if (selectedId != null && effectSetIdBeforePanel != selectedId) {
                        val effectSet = (effectSetViewModel.uiState.value as? EffectSetUiState.Success)
                            ?.effectSets?.find { it.id == selectedId }
                        if (effectSet != null) {
                            Analytics.trackEffectSelect(
                                videoId = state?.project?.id ?: "",
                                effectId = effectSet.id,
                                effectName = effectSet.name,
                                isPremium = effectSet.isPremium
                            )
                        }
                    }
                    showEffectSetSheet = false
                },
                onEffectSetSelected = { effectSet ->
                    viewModel.updateEffectSet(effectSet.id)
                    val videoId = currentVideoId()
                    if (videoId != null) {
                        Analytics.trackEffectClick(
                            videoId = videoId,
                            effectId = effectSet.id,
                            effectName = effectSet.name,
                            isPremium = effectSet.isPremium
                        )
                    }
                },
                showRatioPanel = showRatioSheet,
                onRatioPanelDismiss = {
                    val state = currentState()
                    if (state != null) {
                        Analytics.trackRatioClose(
                            videoId = state.project.id,
                            ratioSize = state.displaySettings.aspectRatio.toAnalyticsRatioSize()
                        )
                    }
                    // Revert if ratio changed during preview
                    val before = ratioBeforePanel
                    if (before != null && state?.displaySettings?.aspectRatio != before) {
                        viewModel.updateAspectRatio(before)
                    }
                    showRatioSheet = false
                },
                onRatioPanelConfirm = {
                    val state = currentState()
                    if (state != null) {
                        val ratioSize = state.displaySettings.aspectRatio.toAnalyticsRatioSize()
                        Analytics.trackRatioSelect(
                            videoId = state.project.id,
                            ratioSize = ratioSize
                        )
                    }
                    showRatioSheet = false
                },
                onRatioSelected = { selectedRatio ->
                    viewModel.updateAspectRatio(selectedRatio)
                    val videoId = currentVideoId()
                    if (videoId != null) {
                        Analytics.trackRatioClick(
                            videoId = videoId,
                            ratioSize = selectedRatio.toAnalyticsRatioSize()
                        )
                    }
                },
                showTextPanel = showTextSheet,
                textFocusTrigger = textFocusTrigger,
                editorViewModel = viewModel,
                onDoubleTapText = { id ->
                    viewModel.setSelectedTextOverlayId(id)
                    showTextSheet = true
                    textFocusTrigger = System.currentTimeMillis()
                },
                onTextPanelDismiss = {
                    viewModel.setSelectedTextOverlayId(null)
                    showTextSheet = false
                },
                onTextPanelConfirm = {
                    viewModel.setSelectedTextOverlayId(null)
                    showTextSheet = false
                },
                topBar = {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = !showEffectSetSheet && !showRatioSheet && !showTextSheet,
                        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut()
                    ) {
                        EditorTopBar(
                            selectedQuality = selectedQuality,
                            isLoading = editorScreenState == EditorScreenState.LOADING,
                            canExport = successState != null &&
                                    !isProcessingAudio &&
                                    successState.isMusicCached &&
                                    !successState.isCachingMusic,
                            isQualityLocked = viewModel.isQualityLocked(selectedQuality),
                            isAdTypeInterstitial = viewModel.isQualityInterstitialAd(selectedQuality),
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
            )

            // Error popup over the (loading-like) placeholder view
            (uiState as? EditorUiState.Error)?.let { errorState ->
                EditorErrorDialog(
                    title = stringResource(R.string.error_network_title),
                    message = errorState.message,
                    primaryText = stringResource(R.string.error_dialog_try_again),
                    onPrimary = { viewModel.retry() },
                    secondaryText = stringResource(R.string.error_dialog_back_home),
                    onSecondary = { onNavigateToHome() }
                )
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

            // Inline panels replace bottom sheets for ratio and effect — other sheets below

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

                val editorState = currentState()
                val editorSettings = editorState?.displaySettings
                val audioNode = editorSettings?.primaryAudioNode
                val editorInitialSong = audioNode?.songId?.let { sid ->
                    com.videomaker.aimusic.domain.model.MusicSong(
                        id = sid,
                        name = audioNode.songName ?: "",
                        artist = audioNode.songArtist ?: "",
                        mp3Url = audioNode.songUrl ?: "",
                        coverUrl = audioNode.coverUrl ?: "",
                        hookStartTimeMs = audioNode.trimStartMs,
                        hookStartTimes = audioNode.hookStartTimes
                    )
                }

                MusicSearchBottomSheet(
                    viewModel = songSearchViewModel,
                    currentVideoDurationMs = durationMs,
                    initialSong = editorInitialSong,
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
                    onSongSelected = { song, selectionStartMs ->
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
                        viewModel.updateMusicTrack(
                            songId = song.id,
                            songName = song.name,
                            songArtist = song.artist,
                            songUrl = song.mp3Url,
                            songCoverUrl = song.coverUrl,
                            trimStartMs = selectionStartMs,
                            hookStartTimes = song.hookStartTimes
                        )
                        showMusicSearchSheet = false
                        // ViewModel handles auto-play after music change completes
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
                    android.util.Log.d(
                        "EditorScreen",
                        "Received ${selectedUris.size} selected assets from AssetPicker"
                    )
                    // GL renderer updates instantly via renderState — no overlay needed.
                    // Audio reprocessing runs in background inside the VM.
                    isEditingImages = false
                    showImagesSheet = false
                    viewModel.replaceAssetsFromUris(selectedUris)
                    viewModel.discardPendingAssets()
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
                            val currentAssetUris =
                                successState.displayAssets.map { it.uri.toString() }
                            onNavigateToAddAssets(
                                successState.project.id,
                                currentAssetUris,
                                successState.project.settings.primaryAudioNode?.songId ?: -1L,
                                successState.project.settings.hookStartTimeMs
                            )
                        },
                        onConfirm = { updatedAssets ->
                            // Close sheet immediately — GL renderer updates instantly via renderState.
                            // Audio reprocessing (if needed) runs in background inside the VM.
                            isEditingImages = false
                            showImagesSheet = false
                            viewModel.applyPendingAssets(updatedAssets)
                        }
                    )
                }
            }

            // Network error dialog (beat-sync or effect set loading failure)
            if (showBeatSyncErrorDialog) {
                EditorErrorDialog(
                    title = stringResource(R.string.error_network_title),
                    message = stringResource(R.string.error_data_load_failed),
                    primaryText = stringResource(R.string.error_dialog_try_again),
                    onPrimary = viewModel::onBeatSyncErrorRetry,
                    secondaryText = stringResource(R.string.error_dialog_back_home),
                    onSecondary = viewModel::onBeatSyncErrorDismissed
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
        Box {
            Spacer(Modifier.navigationBarsPadding())
            if (editorScreenState == EditorScreenState.LOADING) {
                // Native ad at bottom (edge-to-edge, no horizontal padding)
                NativeAdView(
                    placement = AdPlacement.NATIVE_EDITOR_LOADING,
                    modifier = Modifier
                        .fillMaxWidth(),
                    isDebug = BuildConfig.DEBUG,
                    onAdClicked = { adClickDetector.onAdClick(it) }
                )
            } else {
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
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EditorTopBar(
    selectedQuality: VideoQuality,
    isLoading: Boolean,
    canExport: Boolean,
    isQualityLocked: Boolean,
    isAdTypeInterstitial: Boolean,
    onBackClick: () -> Unit,
    onQualityMenuOpen: () -> Unit,
    onQualityChange: (VideoQuality) -> Unit,
    onDoneClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SplashBackground)
            .padding(top = 10.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = stringResource(R.string.back),
            modifier = Modifier
                .size(32.dp)
                .clickableSingle {
                    onBackClick.invoke()
                }
                .padding(4.dp)
        )

        Spacer(Modifier.weight(1f))
        if (isLoading.not()) {
            // Quality picker (reusable component) - shows [AD] badge for locked qualities
            QualityPickerV2(
                selectedQuality = selectedQuality,
                onQualityChange = onQualityChange,
                isQualityUnlocked = !isQualityLocked,
                isAdTypeInterstitial = isAdTypeInterstitial,
                onMenuOpen = onQualityMenuOpen,
            )

            // Done button - disabled during processing
            Text(
                text = stringResource(R.string.done),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = Neutral_N100,
                modifier = Modifier
                    .padding(start = 12.dp)
                    .background(
                        if (canExport) Primary else Primary.copy(alpha = 0.5f),
                        RoundedCornerShape(16.dp)
                    )
                    .clickableSingle(canExport) {
                        onDoneClick.invoke()
                    }
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            )
        }
    }
}

/**
 * Preview-area placeholder shown while the video composition isn't ready yet.
 *
 * Frames the first image exactly like the video preview (same padding, aspect ratio, rounded
 * corners) and pulses the image scale 1f -> 1.2f on a continuous loop. The image is clipped to
 * the fixed frame, so the frame size/ratio stays matched to the video while the content zooms.
 * Falls back to a shimmer when the URI is null or fails to load.
 */
@Composable
private fun PreviewPlaceholder(
    imageUri: android.net.Uri?,
    aspectRatio: Float,
    modifier: Modifier = Modifier
) {
    val pulse =
        androidx.compose.animation.core.rememberInfiniteTransition(label = "placeholder_pulse")
    val scale by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 1.2f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(
                durationMillis = 1200,
                easing = androidx.compose.animation.core.FastOutSlowInEasing
            ),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "placeholder_scale"
    )

    // Outer box mirrors the video preview container: padding + centered, aspect-ratio frame.
    // SplashBackground fills the entire area so there's no gap when GLSurfaceView is hidden.
    Box(
        modifier = modifier
            .background(SplashBackground)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .aspectRatio(aspectRatio)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            if (imageUri == null) {
                ShimmerPlaceholder(modifier = Modifier.fillMaxSize(), cornerRadius = 0.dp)
            } else {
                SubcomposeAsyncImage(
                    model = imageUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .scale(scale),
                    loading = {
                        ShimmerPlaceholder(
                            modifier = Modifier.fillMaxSize(),
                            cornerRadius = 0.dp
                        )
                    },
                    error = {
                        ShimmerPlaceholder(
                            modifier = Modifier.fillMaxSize(),
                            cornerRadius = 0.dp
                        )
                    }
                )
            }
        }
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
    currentState: EditorScreenState,
    contentState: EditorContentState,
    project: Project?,
    firstImageUri: android.net.Uri?,
    previewAspectRatio: Float,
    isPreviewReady: Boolean,
    renderState: RenderState,
    playbackClock: PlaybackClock,
    isPlaying: Boolean,
    currentPositionMs: Long,
    durationMs: Long,
    seekToPosition: Long?,
    scrubToPosition: Long?,
    effectSetName: String,
    onPlayPauseClick: () -> Unit,
    onSeek: (Long) -> Unit,
    onScrub: (Long) -> Unit,
    onSeekStart: () -> Unit,
    onSeekEnd: () -> Unit,
    onSeekComplete: () -> Unit,
    onScrubComplete: () -> Unit,
    onImagesClick: () -> Unit,
    onEffectClick: () -> Unit,
    onTextClick: () -> Unit,
    onRatioClick: () -> Unit,
    onVolumeClick: () -> Unit = {},
    onMusicSelectorClick: () -> Unit = {},
    showEffectSetPanel: Boolean,
    effectSetViewModel: EffectSetViewModel,
    onEffectPanelDismiss: () -> Unit,
    onEffectPanelConfirm: () -> Unit,
    onEffectSetSelected: (EffectSet) -> Unit,
    showRatioPanel: Boolean = false,
    onRatioPanelDismiss: () -> Unit = {},
    onRatioPanelConfirm: () -> Unit = {},
    onRatioSelected: (AspectRatio) -> Unit = {},
    showTextPanel: Boolean = false,
    textFocusTrigger: Long = 0L,
    editorViewModel: EditorViewModel? = null,
    onDoubleTapText: (String) -> Unit = {},
    onTextPanelDismiss: () -> Unit = {},
    onTextPanelConfirm: () -> Unit = {},
    topBar: @Composable () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val density = LocalDensity.current
    val minHeight = remember(context) {
        val displayMetrics = context.resources.displayMetrics
        val xdpi = if (displayMetrics.xdpi > 100f) displayMetrics.xdpi.toDouble() else displayMetrics.densityDpi.toDouble()
        val ydpi = if (displayMetrics.ydpi > 100f) displayMetrics.ydpi.toDouble() else displayMetrics.densityDpi.toDouble()
        val wi = displayMetrics.widthPixels.toDouble() / xdpi
        val hi = displayMetrics.heightPixels.toDouble() / ydpi
        val screenInches = kotlin.math.sqrt(wi * wi + hi * hi)
        if (screenInches < 5.5) 250.dp else 350.dp
    }
    val maxHeight = 620.dp
    var currentPanelHeight by remember(minHeight) { mutableStateOf(minHeight) }

    LaunchedEffect(showEffectSetPanel, showTextPanel, showRatioPanel, minHeight) {
        if (showEffectSetPanel || showTextPanel || showRatioPanel) {
            currentPanelHeight = minHeight
        }
    }

    val nestedScrollConnection = remember(minHeight) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val deltaY = available.y
                if (deltaY < 0 && currentPanelHeight < maxHeight) {
                    val dragDp = with(density) { -deltaY.toDp() }
                    val newHeight = (currentPanelHeight + dragDp).coerceAtMost(maxHeight)
                    val consumedDp = newHeight - currentPanelHeight
                    currentPanelHeight = newHeight
                    val consumedY = with(density) { -consumedDp.toPx() }
                    return Offset(0f, consumedY)
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                val deltaY = available.y
                if (deltaY > 0 && currentPanelHeight > minHeight) {
                    val dragDp = with(density) { -deltaY.toDp() }
                    val newHeight = (currentPanelHeight + dragDp).coerceAtLeast(minHeight)
                    val consumedDp = currentPanelHeight - newHeight
                    currentPanelHeight = newHeight
                    val consumedY = with(density) { consumedDp.toPx() }
                    return Offset(0f, consumedY)
                }
                return Offset.Zero
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SplashBackground)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
        // Top bar rendered inside the same Column as the GL preview so it participates
        // in the normal layout order. If placed in a parent Column, GLSurfaceView's
        // separate hardware surface layer can overlap it.
        topBar()

        // Real-time Video Preview using GL renderer
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(top = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            // GL renderer — always mounted. AndroidEmbeddedExternalSurface (TextureView)
            // renders within the Compose layer, so overlays draw on top naturally.
            // NOTE: Do NOT use fillMaxSize() before aspectRatio() — fillMaxSize() creates
            // tight constraints (min==max) which prevents aspectRatio() from adjusting.
            // Without fillMaxSize(), aspectRatio() receives loose constraints and can freely
            // choose the largest size that fits within the Box while maintaining the ratio.
            Box(
                modifier = Modifier
                    .aspectRatio(previewAspectRatio),
                contentAlignment = Alignment.Center
            ) {
                PreviewSurfaceView(
                    renderState = renderState,
                    playbackClock = playbackClock,
                    isPlaying = isPlaying,
                    modifier = Modifier.fillMaxSize()
                )

                if (editorViewModel != null) {
                    TextOverlayCanvas(
                        viewModel = editorViewModel,
                        onDoubleTapText = onDoubleTapText,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // Audio-only player — synced to PlaybackClock
            AudioPreviewPlayer(
                audioNodes = project?.settings?.audioNodes ?: emptyList(),
                hookStartTimeMs = project?.settings?.hookStartTimeMs ?: 0L,
                isPlaying = isPlaying,
                playbackClock = playbackClock,
                seekToPosition = seekToPosition,
                scrubToPosition = scrubToPosition,
                onSeekComplete = onSeekComplete,
                onScrubComplete = onScrubComplete
            )

            // Show the first image until ready (initial load or audio processing).
            // PreviewPlaceholder has an opaque Black background that fully covers the GL SurfaceView.
            if (currentState != EditorScreenState.READY) {
                PreviewPlaceholder(
                    imageUri = project?.assets?.firstOrNull()?.uri ?: firstImageUri,
                    aspectRatio = previewAspectRatio,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }


        if (showEffectSetPanel || showTextPanel) {
            Spacer(modifier = Modifier.height(minHeight))
        } else if (showRatioPanel) {
            Spacer(modifier = Modifier.height(210.dp))
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (currentState == EditorScreenState.LOADING) {
                        val tipMessages = listOf(
                            stringResource(R.string.editor_loading_tip_1),
                            stringResource(R.string.editor_loading_tip_2),
                            stringResource(R.string.editor_loading_tip_3),
                            stringResource(R.string.editor_loading_tip_4),
                            stringResource(R.string.editor_loading_tip_5),
                        )

                        var currentTipIndex by remember { mutableStateOf(0) }

                        // Rotate tips every 4 seconds
                        LaunchedEffect(Unit) {
                            while (true) {
                                delay(4000)
                                currentTipIndex = (currentTipIndex + 1) % tipMessages.size
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Image(
                            painter = painterResource(R.drawable.img_content_edit_loading),
                            contentDescription = null,
                            contentScale = ContentScale.FillHeight,
                            modifier = Modifier
                                .height(36.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = tipMessages[currentTipIndex],
                            fontSize = 16.sp,
                            fontWeight = FontWeight.W400,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 32.dp)
                        )
                        Spacer(modifier = Modifier.height(24.dp))

                        Row(
                            modifier = Modifier
                                .border(1.dp, Color.White.copy(0.08f), RoundedCornerShape(120.dp))
                                .padding(vertical = 6.dp, horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_outline_info),
                                contentDescription = null,
                                tint = Neutral_N600,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = stringResource(R.string.editor_loading_dont_close),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.W400,
                                color = Neutral_N600,
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))

                    } else {
                        PlayMusicSlider(
                            currentPositionMs = currentPositionMs,
                            durationMs = durationMs,
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
                        )

                        // Music Section - song info and player
                        Row(
                            modifier = Modifier
                                .padding(horizontal = 12.dp)
                                .fillMaxWidth()
                                .shadow(
                                    elevation = 16.dp,            // approximates the 32px blur radius
                                    shape = RoundedCornerShape(20.dp),
                                    ambientColor = Color(0x3D000000),
                                    spotColor = Color(0x3D000000),
                                    clip = false
                                )
                                .clip(RoundedCornerShape(20.dp))
                                .background(Color(0xFF575757).copy(0.4f))
                                .border(1.dp, Color.White.copy(0.4f), RoundedCornerShape(20.dp))
                                .clickable(onClick = onMusicSelectorClick)
                                .padding(vertical = 8.dp, horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Album cover thumbnail
                            AppAsyncImage(
                                imageUrl = project?.settings?.primaryAudioNode?.coverUrl ?: "",
                                contentDescription = project?.settings?.primaryAudioNode?.songName
                                    ?: stringResource(R.string.editor_no_music_selected),
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(RoundedCornerShape(8.dp))
                            )

                            Spacer(modifier = Modifier.width(10.dp))

                            // Song name + artist
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = project?.settings?.primaryAudioNode?.songName
                                        ?: stringResource(R.string.editor_no_music_selected),
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.W500,
                                    color = TextPrimary,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Spacer(Modifier.height(2.dp))
                                val songArtist = project?.settings?.primaryAudioNode?.songArtist
                                if (!songArtist.isNullOrBlank()) {
                                    Text(
                                        text = songArtist,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.W400,
                                        color = TextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            // Chevron arrow
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = TextPrimary,
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Separator
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(Color.White.copy(alpha = 0.1f))
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        // Settings Tab Bar - Images, Effect, Ratio, Volume (horizontally scrollable)
                        val hasMusic = project?.settings?.primaryAudioNode != null
                        SettingsTabBar(
                            showMusicControls = hasMusic,
                            onImagesClick = onImagesClick,
                            onEffectClick = onEffectClick,
                            onTextClick = onTextClick,
                            onRatioClick = onRatioClick,
                            onVolumeClick = onVolumeClick,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }

    if (showEffectSetPanel) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(currentPanelHeight)
                .background(SplashBackground)
                .nestedScroll(nestedScrollConnection)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { /* consume clicks */ }
        ) {
            // Drag handle area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            val dragDp = with(density) { -dragAmount.y.toDp() }
                            currentPanelHeight = (currentPanelHeight + dragDp).coerceIn(minHeight, maxHeight)
                        }
                    }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 36.dp, height = 4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.Gray)
                )
            }

            // Player Controls — hidden when user swipes the panel up
            androidx.compose.animation.AnimatedVisibility(
                visible = currentPanelHeight <= minHeight + 30.dp,
                enter = fadeIn() + androidx.compose.animation.expandVertically(),
                exit = fadeOut() + androidx.compose.animation.shrinkVertically()
            ) {
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
            }

            // Inline EffectSetPanel
            EffectSetPanel(
                viewModel = effectSetViewModel,
                selectedEffectSetId = project?.settings?.effectSetId,
                onDismiss = onEffectPanelDismiss,
                onConfirm = onEffectPanelConfirm,
                onEffectSetSelected = onEffectSetSelected,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
        }
    }

    if (showRatioPanel) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(SplashBackground)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { /* consume clicks */ }
        ) {
            // Inline RatioPanel
            RatioPanel(
                selectedRatio = project?.settings?.aspectRatio ?: AspectRatio.RATIO_9_16,
                onDismiss = onRatioPanelDismiss,
                onConfirm = onRatioPanelConfirm,
                onRatioSelected = onRatioSelected,
                modifier = Modifier.fillMaxWidth()
            )

            // Player Controls
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
        }
    }

    if (showTextPanel && editorViewModel != null) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(currentPanelHeight)
                .background(SplashBackground)
                .nestedScroll(nestedScrollConnection)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { /* consume clicks */ }
        ) {
            // Drag handle area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            val dragDp = with(density) { -dragAmount.y.toDp() }
                            currentPanelHeight = (currentPanelHeight + dragDp).coerceIn(minHeight, maxHeight)
                        }
                    }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 36.dp, height = 4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.Gray)
                )
            }

            val isKeyboardOpen = WindowInsets.ime.asPaddingValues().calculateBottomPadding() > 0.dp

            // Player Controls — hidden when user swipes the panel up or keyboard is open
            AnimatedVisibility(
                visible = currentPanelHeight <= minHeight + 30.dp && !isKeyboardOpen,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
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
            }

            // Inline TextBottomSheet
            TextBottomSheet(
                viewModel = editorViewModel,
                onDismiss = onTextPanelDismiss,
                onConfirm = onTextPanelConfirm,
                focusTrigger = textFocusTrigger,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
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
    val sliderInteractionSource =
        remember { MutableInteractionSource() }
    var isDragging by remember { mutableStateOf(false) }
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
    return String.format(java.util.Locale.US, "%d:%02d", minutes, seconds)
}

// SelectRatioBottomSheet, RatioOptionCard, AspectRatioIcon, DurationBottomSheet,
// and MusicSearchBottomSheet moved to components/ package
