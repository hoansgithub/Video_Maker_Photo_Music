package com.videomaker.aimusic.modules.templatepreviewer

import android.Manifest
import android.app.Activity
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.EaseInOutCubic
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
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
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.alcheclub.lib.acccore.ads.compose.BannerAdView
import co.alcheclub.lib.acccore.ads.compose.NativeAdView
import co.alcheclub.lib.acccore.ads.loader.AdsLoaderService
import com.videomaker.aimusic.BuildConfig
import com.videomaker.aimusic.core.ads.RewardedAdPresenter
import com.videomaker.aimusic.R
import kotlinx.coroutines.delay
import com.videomaker.aimusic.core.ads.InterstitialAdHelperExt
import com.videomaker.aimusic.core.analytics.Analytics
import com.videomaker.aimusic.core.analytics.AnalyticsEvent
import com.videomaker.aimusic.core.constants.AdPlacement
import com.videomaker.aimusic.core.permission.NotificationPermissionCoordinator
import com.videomaker.aimusic.ui.components.AdBadge
import com.videomaker.aimusic.ui.components.AdBadgeStyle
import com.videomaker.aimusic.ui.components.AdsLoadingOverlay
import com.videomaker.aimusic.ui.components.NotificationPermissionPromoDialog
import com.videomaker.aimusic.ui.components.NotificationPermissionSettingsGuideDialog
import com.videomaker.aimusic.domain.model.AspectRatio
import com.videomaker.aimusic.modules.templatepreviewer.components.TemplateVideoPlayer
import org.koin.compose.koinInject
import com.videomaker.aimusic.ui.components.PrimaryButton
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
import coil.size.Scale
import com.videomaker.aimusic.domain.model.VideoTemplate
import com.videomaker.aimusic.ui.theme.FoundationBlack
import com.videomaker.aimusic.ui.theme.SurfaceLight
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import androidx.core.content.edit
import com.videomaker.aimusic.ui.components.ModifierExtension.clickableSingle

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
    sourceLocation: String? = null,
    onNavigateToAssetPicker: (template: com.videomaker.aimusic.domain.model.VideoTemplate, overrideSongId: Long, aspectRatio: AspectRatio) -> Unit,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val likedTemplateIds by viewModel.likedTemplateIds.collectAsStateWithLifecycle()
    val unlockedTemplateIds by viewModel.unlockedTemplateIds.collectAsStateWithLifecycle()
    val shouldPresentAd by viewModel.shouldPresentAd.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val eventLocation = remember(sourceLocation) {
        sourceLocation?.takeIf { it.isNotBlank() } ?: AnalyticsEvent.Value.Location.PREVIEW_SWIPE
    }

    // Get dependencies for ad showing
    val activity = context as? Activity
    val adsLoaderService = koinInject<AdsLoaderService>()
    val notificationPermissionCoordinator = koinInject<NotificationPermissionCoordinator>()
    var showNotificationPromoDialog by remember { mutableStateOf(false) }
    var showNotificationSettingsGuideDialog by remember { mutableStateOf(false) }
    var pendingPermissionCheckAfterSettings by remember { mutableStateOf(false) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        Analytics.trackPermissionClick(
            button = if (granted) {
                AnalyticsEvent.Value.Option.ALLOW
            } else {
                AnalyticsEvent.Value.Option.NO_ALLOW
            },
            perType = AnalyticsEvent.Value.PerType.NOTI,
            popType = AnalyticsEvent.Value.PopType.SYSTEM
        )
        notificationPermissionCoordinator.onSystemPermissionResult(granted)
        Analytics.trackPermissionCheck(allow = granted)
        showNotificationPromoDialog = false
    }

    // Intercept system back gesture (swipe) - same ad logic as back button
    BackHandler {
        viewModel.onNavigateBack()
    }

    // Handle navigation events - Channel pattern (Google official) - one-time delivery, no replay
    LaunchedEffect(Unit) {
        viewModel.navigationEvent.collect { event ->
            when (event) {
                is TemplatePreviewerNavigationEvent.NavigateToAssetPicker -> {
                    onNavigateToAssetPicker(event.template, event.overrideSongId, event.aspectRatio)
                }

                is TemplatePreviewerNavigationEvent.RequestBackWithAd -> {
                    // Show ad if ready, otherwise navigate immediately (non-blocking)
                    if (event.shouldShowAd && activity != null) {
                        android.util.Log.d("TemplatePreviewerScreen", "📺 Showing back button ad...")

                        InterstitialAdHelperExt.showInterstitial(
                            adsLoaderService = adsLoaderService,
                            activity = activity,
                            placement = AdPlacement.INTERSTITIAL_TEMPLATE_PREVIEWER_BACK,
                            action = {
                                // Ad closed - navigate back
                                android.util.Log.d("TemplatePreviewerScreen", "✅ Back ad closed - navigating")
                            },
                            onShown = {
                                // Navigate immediately when ad shows (parallel)
                                android.util.Log.d("TemplatePreviewerScreen", "🎬 Back ad shown - navigating")
                                onNavigateBack()
                            },
                            bypassFrequencyCap = true,  // Back button ads always show
                            showLoadingOverlay = false  // Ad already preloaded
                        )
                    } else {
                        // Ad not ready or no activity - navigate immediately
                        if (!event.shouldShowAd) {
                            android.util.Log.d("TemplatePreviewerScreen", "⚠️ Back ad not ready - navigating immediately")
                        }
                        onNavigateBack()
                    }
                }

                is TemplatePreviewerNavigationEvent.ShowScrollInterstitial -> {
                    // Show scroll interstitial if activity available
                    // ACCCore handles frequency cap automatically (ad_interstitial_interval_seconds)
                    // If interval not passed, ad is skipped silently
                    if (activity != null) {
                        android.util.Log.d("TemplatePreviewerScreen", "📺 Attempting to show scroll interstitial...")

                        InterstitialAdHelperExt.showInterstitial(
                            adsLoaderService = adsLoaderService,
                            activity = activity,
                            placement = AdPlacement.INTERSTITIAL_TEMPLATE_PREVIEWER_SCROLL,
                            action = {
                                // Ad closed or skipped - no action needed, user continues browsing
                                android.util.Log.d("TemplatePreviewerScreen", "✅ Scroll ad action callback")
                            },
                            onShown = {
                                // Ad actually shown (not skipped by frequency cap)
                                android.util.Log.d("TemplatePreviewerScreen", "🎬 Scroll ad shown to user")
                            },
                            bypassFrequencyCap = false,  // ✅ Let ACCCore enforce interval
                            showLoadingOverlay = false  // Background preloaded, no overlay
                        )
                    } else {
                        android.util.Log.w("TemplatePreviewerScreen", "⚠️ No activity - cannot show scroll ad")
                    }
                }

                is TemplatePreviewerNavigationEvent.NavigateBack -> onNavigateBack()
            }
            // Event auto-consumed by Channel - no manual cleanup needed
        }
    }

    // Handle rewarded ad presentation using reusable presenter
    RewardedAdPresenter(
        shouldPresent = shouldPresentAd,
        placement = AdPlacement.REWARD_UNLOCK_TEMPLATE,
        adsLoaderService = adsLoaderService,
        onRewardEarned = viewModel::onRewardEarned,
        onAdFailed = viewModel::onAdFailed
    )

    // Track loading state
    var showLoadingOverlay by remember { mutableStateOf(true) }
    var firstVideoReady by remember { mutableStateOf(false) }

    // Loading overlay timing:
    // - Video starts buffering immediately (in TemplatePreviewerReadyContent below)
    // - Wait for ad to be ready (up to 10s timeout)
    // - Show ad for 2 seconds (impression + user sees it)
    // - Video will be ready by then (loads in ~1s in background)
    LaunchedEffect(Unit) {
        val startTime = System.currentTimeMillis()
        var adReady = false

        // Check if ad is already loaded (cached from preload)
        adReady = adsLoaderService.isNativeAdReady(AdPlacement.NATIVE_TEMPLATE_PREVIEWER_LOADING)

        if (adReady) {
            android.util.Log.d("TemplatePreviewerLoading", "✅ Ad already cached - showing for 2s")
        } else {
            android.util.Log.d("TemplatePreviewerLoading", "⏳ Ad not ready, polling...")

            // Poll for ad ready state (or timeout after 10s)
            while (!adReady && (System.currentTimeMillis() - startTime) < 10_000) {
                delay(500) // Check every 500ms

                // Check if native ad has loaded
                if (adsLoaderService.isNativeAdReady(AdPlacement.NATIVE_TEMPLATE_PREVIEWER_LOADING)) {
                    val elapsedSeconds = (System.currentTimeMillis() - startTime) / 1000.0
                    android.util.Log.d("TemplatePreviewerLoading", "✅ Ad loaded after ${elapsedSeconds}s")
                    adReady = true
                }
            }
        }

        // Show ad for 2 seconds when ready (impression + visibility)
        if (adReady) {
            android.util.Log.d("TemplatePreviewerLoading", "📊 Ad ready - showing for 2s (impression + display)")
            delay(2_000) // Show for 2 seconds
        } else {
            android.util.Log.d("TemplatePreviewerLoading", "⏱️ Ad timeout (10s) - proceeding immediately")
        }

        val totalTime = System.currentTimeMillis() - startTime
        android.util.Log.d("TemplatePreviewerLoading", "✅ LOADING COMPLETE (${totalTime}ms)")

        // Log video status
        if (firstVideoReady) {
            android.util.Log.d("TemplatePreviewerLoading", "✅ First video is ready")
        } else {
            android.util.Log.d("TemplatePreviewerLoading", "⏳ First video still loading (will continue in background)")
        }

        showLoadingOverlay = false
    }

    // Show notification permission dialog only after loading complete
    LaunchedEffect(uiState, showLoadingOverlay) {
        if (uiState is TemplatePreviewerUiState.Ready &&
            !showLoadingOverlay &&
            notificationPermissionCoordinator.shouldShowTemplatePreviewerContextualPopup(context)
        ) {
            if (notificationPermissionCoordinator.shouldShowSettingsGuide(context)) {
                showNotificationSettingsGuideDialog = true
                showNotificationPromoDialog = false
            } else {
                showNotificationPromoDialog = true
                showNotificationSettingsGuideDialog = false
            }
        }
    }

    LaunchedEffect(showNotificationPromoDialog) {
        if (showNotificationPromoDialog) {
            Analytics.trackPermissionRender(
                perType = AnalyticsEvent.Value.PerType.NOTI,
                popType = AnalyticsEvent.Value.PopType.CUSTOM
            )
        }
    }

    // Pause/resume on app background
    DisposableEffect(lifecycleOwner, showLoadingOverlay, pendingPermissionCheckAfterSettings) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    if (pendingPermissionCheckAfterSettings) {
                        val allow = notificationPermissionCoordinator.isNotificationGranted(context)
                        if (allow) {
                            notificationPermissionCoordinator.onSystemPermissionGranted()
                        }
                        Analytics.trackPermissionCheck(allow = allow)
                        pendingPermissionCheckAfterSettings = false
                    }
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    when (val state = uiState) {
        is TemplatePreviewerUiState.Loading -> {
            LoadingStateWithAd()
        }
        is TemplatePreviewerUiState.Ready -> {
            // Show content immediately so video starts buffering
            // Loading overlay will be shown on top until ad display completes
            Box(modifier = Modifier.fillMaxSize()) {
                TemplatePreviewerReadyContent(
                    state = state,
                    likedTemplateIds = likedTemplateIds,
                    unlockedTemplateIds = unlockedTemplateIds,
                    onPageChanged = viewModel::onPageChanged,
                    onUseThisTemplate = viewModel::onUseThisTemplate,
                    onRatioSelected = viewModel::onRatioSelected,
                    onLikeTemplate = viewModel::onLikeTemplate,
                    eventLocation = eventLocation,
                    onNavigateBack = viewModel::onNavigateBack,
                    onFirstVideoReady = { firstVideoReady = true }
                )

                // Show loading overlay on top until ad display completes
                if (showLoadingOverlay) {
                    LoadingStateWithAd()
                }
            }
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

    if (showNotificationPromoDialog) {
        NotificationPermissionPromoDialog(
            onNotifyMe = {
                Analytics.trackPermissionClick(
                    button = AnalyticsEvent.Value.Option.ALLOW,
                    perType = AnalyticsEvent.Value.PerType.NOTI,
                    popType = AnalyticsEvent.Value.PopType.CUSTOM
                )
                if (notificationPermissionCoordinator.canRequestSystemPermission(context)) {
                    Analytics.trackPermissionRender(
                        perType = AnalyticsEvent.Value.PerType.NOTI,
                        popType = AnalyticsEvent.Value.PopType.SYSTEM
                    )
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    showNotificationPromoDialog = false
                } else {
                    showNotificationPromoDialog = false
                    showNotificationSettingsGuideDialog = true
                }
            },
            onMaybeLater = {
                Analytics.trackPermissionClick(
                    button = AnalyticsEvent.Value.Option.NO_ALLOW,
                    perType = AnalyticsEvent.Value.PerType.NOTI,
                    popType = AnalyticsEvent.Value.PopType.CUSTOM
                )
                showNotificationPromoDialog = false
            }
        )
    }

    if (showNotificationSettingsGuideDialog) {
        NotificationPermissionSettingsGuideDialog(
            onOpenSettings = {
                Analytics.trackPermissionGotoSetting()
                pendingPermissionCheckAfterSettings = true
                showNotificationSettingsGuideDialog = false
                runCatching {
                    context.startActivity(
                        notificationPermissionCoordinator.buildOpenAppSettingsIntent(context)
                    )
                }.onFailure {
                    pendingPermissionCheckAfterSettings = false
                }
            },
            onDismiss = {
                showNotificationSettingsGuideDialog = false
            }
        )
    }

    // Ads loading overlay
    AdsLoadingOverlay()
}

// ============================================
// LOADING STATE WITH AD — Waits for both ad and video
// ============================================

/**
 * Loading state with native ad at bottom
 *
 * Behavior:
 * - Shows CircularProgressIndicator at center
 * - Shows native ad at bottom
 * - Waits for BOTH:
 *   1. Ad loading: 10s timeout + 2s display (12s total)
 *   2. First video loading: 10s timeout
 * - Transitions to Ready state only when BOTH complete (or timeout)
 * - Maximum wait time: ~12s (both timeouts run in parallel)
 *
 * Note: This ensures users see a fully-loaded experience with both
 * ad impression and first video ready to play.
 */
@Composable
private fun LoadingStateWithAd() {
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Loading indicator with label at center
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircularProgressIndicator(color = Color.White)

            Text(
                text = stringResource(R.string.loading_building_feed),
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.W500,
                textAlign = TextAlign.Center
            )
        }

        // Native ad at bottom
        // Loads during 10s + displays for 1s more = 11s maximum
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
        ) {
            NativeAdView(
                placement = AdPlacement.NATIVE_TEMPLATE_PREVIEWER_LOADING,
                modifier = Modifier.fillMaxWidth(),
                isDebug = BuildConfig.DEBUG
            )
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
    likedTemplateIds: Set<String>,
    unlockedTemplateIds: Set<String>,
    onPageChanged: (Int) -> Unit,
    onUseThisTemplate: (VideoTemplate, AspectRatio) -> Unit,
    onRatioSelected: (VideoTemplate, AspectRatio) -> Unit,
    onLikeTemplate: (VideoTemplate) -> Unit,
    eventLocation: String = AnalyticsEvent.Value.Location.PREVIEW_SWIPE,
    onNavigateBack: () -> Unit,
    onFirstVideoReady: () -> Unit
) {
    val templates = state.templates
    val screenSessionId = remember { Analytics.newScreenSessionId() }
    var hasSwipedTemplate by remember { mutableStateOf(false) }
    val pagerState = rememberPagerState(
        initialPage = initialVirtualPage(state.initialPage, templates.size),
        pageCount = { VIRTUAL_PAGE_COUNT }
    )

    // Bottom sheet state — non-null while the sheet is visible
    var pendingTemplate by remember { mutableStateOf<VideoTemplate?>(null) }

    // First-user swipe hint — shown only on first launch, hidden after 3s or on swipe
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE) }
    val isFirstUser = remember { prefs.getBoolean("is_first_user_template_previewer", true) }
    var showSwipeHint by remember { mutableStateOf(isFirstUser) }

    LaunchedEffect(showSwipeHint) {
        if (showSwipeHint) {
            delay(3000L)
            showSwipeHint = false
            prefs.edit { putBoolean("is_first_user_template_previewer", false) }
        }
    }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .drop(1)
            .collect {
                hasSwipedTemplate = true
                onPageChanged(it)
            }
    }

    LaunchedEffect(pagerState, templates, screenSessionId) {
        var isFirstSettledEmission = true
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { settledPage ->
                val template = templates[settledPage % templates.size]
                val trackingLocation =
                    if (isFirstSettledEmission) {
                        eventLocation
                    } else {
                        AnalyticsEvent.Value.Location.PREVIEW_SWIPE
                    }
                Analytics.trackTemplatePreview(
                    templateId = template.id,
                    templateName = template.name,
                    location = trackingLocation
                )
                Analytics.trackTemplateImpression(
                    templateId = template.id,
                    templateName = template.name,
                    location = trackingLocation,
                    screenSessionId = screenSessionId
                )
                isFirstSettledEmission = false
            }
    }

    val templateEventLocation =
        if (hasSwipedTemplate) AnalyticsEvent.Value.Location.PREVIEW_SWIPE else eventLocation

    // Priority-based image preloading: current page first, then adjacent pages
    // Use singleton ImageLoader to avoid memory leaks from creating new instances
    val imageLoader = remember { coil.Coil.imageLoader(context) }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { currentPage ->

                // PRIORITY 1: Load current page immediately (loads first by order)
                val currentTemplate = templates[currentPage % templates.size]
                val currentImageUrl = currentTemplate.previewImagePath.ifEmpty { currentTemplate.thumbnailPath }
                if (currentImageUrl.isNotEmpty()) {
                    val currentRequest = coil.request.ImageRequest.Builder(context)
                        .data(currentImageUrl)
                        .size(378, 672)  // Match original size - avoid upscaling
                        .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                        .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                        .build()
                    imageLoader.enqueue(currentRequest)
                }

                // PRIORITY 2: Delay 150ms to ensure current page loads first
                delay(150)

                // Preload next 3 pages and previous 1 page (low priority background loading)
                for (offset in -1..3) {
                    if (offset == 0) continue  // Skip current page (already loaded)

                    val pageIndex = (currentPage + offset)
                    if (pageIndex >= 0 && pageIndex < VIRTUAL_PAGE_COUNT) {
                        val template = templates[pageIndex % templates.size]
                        val imageUrl = template.previewImagePath.ifEmpty { template.thumbnailPath }
                        if (imageUrl.isNotEmpty()) {
                            val request = coil.request.ImageRequest.Builder(context)
                                .data(imageUrl)
                                .size(378, 672)  // Match original size - avoid upscaling
                                .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                                .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                                .build()
                            imageLoader.enqueue(request)
                        }
                    }
                }
            }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        VerticalPager(
            state = pagerState,
            beyondViewportPageCount = 2,  // Preload 2 pages ahead/behind for smoother scrolling
            modifier = Modifier.fillMaxSize(),
            key = { pageIndex -> templates[pageIndex % templates.size].id }
        ) { pageIndex ->
            // Videos now have built-in music, so always animate immediately when page is current
            val isCurrentPage = pageIndex == pagerState.settledPage
                && !pagerState.isScrollInProgress

            // Only pass onFirstVideoReady callback to the initial page
            val isInitialPage = pageIndex == initialVirtualPage(state.initialPage, templates.size)

            TemplateThumbnailPage(
                template = templates[pageIndex % templates.size],
                isCurrentPage = isCurrentPage,
                isPriorityPage = pageIndex == pagerState.settledPage,  // High priority for visible page
                onVideoReady = if (isInitialPage) onFirstVideoReady else null  // Only track first video
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

        // Bottom bar — like button + CTA
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .padding(bottom = 60.dp),  // Space for banner ad (50dp + 10dp spacing)
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val currentTemplate = templates.getOrNull(pagerState.settledPage % templates.size)
            val isLiked = currentTemplate?.id in likedTemplateIds
            Row(
                modifier = Modifier
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (isLiked) {
                        Icon(
                            painter = painterResource(R.drawable.ic_heart_liked),
                            tint = Primary,
                            contentDescription = null,
                            modifier = Modifier.size(32.dp)
                                .clickableSingle {
                                    currentTemplate?.let { template ->
                                        Analytics.trackTemplateUnfavorite(
                                            templateId = template.id,
                                            templateName = template.name,
                                            location = templateEventLocation
                                        )
                                        onLikeTemplate(template)
                                    }
                                }
                        )
                    } else {
                        Icon(
                            painter = painterResource(R.drawable.ic_heart),
                            tint = SurfaceLight,
                            contentDescription = null,
                            modifier = Modifier
                                .size(32.dp)
                                .clickableSingle {
                                    currentTemplate?.let { template ->
                                        Analytics.trackTemplateFavorite(
                                            templateId = template.id,
                                            templateName = template.name,
                                            location = templateEventLocation
                                        )
                                        onLikeTemplate(template)
                                    }
                                }
                        )
                    }

                    Text(
                        text = stringResource(R.string.template_add_to_favorites),
                        color = SurfaceLight,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp
                    )
                }
            }

            // CTA button — spinner while project is being created
            val ctaLoading = state.isCreatingProject
            val ctaEnabled = !state.isCreatingProject
            PrimaryButton(
                text = stringResource(R.string.template_use_button),
                onClick = {
                    val template = templates.getOrNull(pagerState.settledPage % templates.size) ?: return@PrimaryButton
                    Analytics.trackTemplateClick(
                        templateId = template.id,
                        templateName = template.name,
                        location = templateEventLocation
                    )
                    // Always show ratio selection bottom sheet
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

        // Banner ad - positioned at bottom, above safe area (like HomeScreen)
        BannerAdView(
            placement = AdPlacement.BANNER_TEMPLATE_PREVIEWER,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()  // Respect safe area
                .height(50.dp)
        )

        // Ratio selection bottom sheet
        val template = pendingTemplate
        if (template != null) {
            val isLocked = template.isPremium && !unlockedTemplateIds.contains(template.id)
            SelectRatioBottomSheet(
                defaultRatio = aspectRatioFromString(template.aspectRatio),
                isLocked = isLocked,
                onDismiss = { pendingTemplate = null },
                onConfirm = { selectedRatio ->
                    Analytics.trackRatioSelect(selectedRatio.shortLabel)
                    Analytics.trackTemplateSelect(
                        templateId = template.id,
                        templateName = template.name,
                        location = templateEventLocation
                    )

                    if (isLocked) {
                        // Template is locked - show watch ad dialog
                        // Store the selected ratio for after ad completes
                        pendingTemplate = null  // Dismiss ratio sheet
                        onRatioSelected(template, selectedRatio)
                    } else {
                        // Template is unlocked - navigate immediately
                        pendingTemplate = null
                        onUseThisTemplate(template, selectedRatio)
                    }
                }
            )
        }

        AnimatedVisibility(
            visible = showSwipeHint,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(0.65f))
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onVerticalDrag = { _, dragAmount ->
                            },
                            onDragEnd = {
                                if (showSwipeHint) {
                                    showSwipeHint = false
                                    prefs.edit { putBoolean("is_first_user_template_previewer", false) }
                                }
                            }
                        )
                    }
                    .clickableSingle {},
                contentAlignment = Alignment.Center
            ) {
                CenterSwipeContent()
            }
        }
    }
}

@Composable
private fun CenterSwipeContent(modifier: Modifier = Modifier) {
    // Bounce animation for the swipe icon
    val infiniteTransition = rememberInfiniteTransition(label = "swipe_bounce")
    val offsetY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -16f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bounce_y"
    )

    Column(
        modifier = modifier.offset(y = 30.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box {

            Box(
                modifier = Modifier
                    .size(width = 81.dp, height = 122.dp)
                    .clip(RoundedCornerShape(topStart = 317.95.dp, topEnd = 317.95.dp))
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFFCCFF00), // 0%
                                Color(0xFFCCFF00), // 39.61%
                                Color(0x00CCFF00)  // transparent at 69.18%
                            ),
                            startY = 0f,
                            endY = Float.POSITIVE_INFINITY
                        )
                    )
            )

            Icon(
                painter = painterResource(R.drawable.ic_hand_swipe),
                contentDescription = null,
                tint = FoundationBlack,
                modifier = Modifier
                    .offset(y = offsetY.dp)
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .padding(15.dp)
                    .align(Alignment.TopCenter)
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // "Swipe up" text
        Text(
            text = stringResource(R.string.template_swipe_hint),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Subtitle
        Text(
            text = stringResource(R.string.template_swipe_subtitle),
            fontSize = 14.sp,
            color = Color.White,
            textAlign = TextAlign.Center
        )
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
    isLocked: Boolean,
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
                text = stringResource(R.string.template_select_ratio_title),
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
                        onClick = {
                            Analytics.trackRatioClick(ratio.shortLabel)
                            selected = ratio
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // "Create Now" button with [AD] badge if locked
            PrimaryButton(
                text = stringResource(
                    if (isLocked) R.string.template_free_unlock
                    else R.string.template_create_now
                ),
                onClick = { onConfirm(selected) },
                leadingIcon = if (isLocked) {
                    {
                        AdBadge(
                            style = AdBadgeStyle.Small(
                                textColor = Color.Black,
                                backgroundColor = Color.White
                            ),
                            modifier = Modifier
                        )
                    }
                } else null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            )

            NativeAdView(
                placement = AdPlacement.NATIVE_TEMPLATE_RATIO_SHEET,
                modifier = Modifier.fillMaxWidth(),
                isDebug = BuildConfig.DEBUG
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
            .clickableSingle { onClick() }
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
// SINGLE PAGE — thumbnail image
// ============================================

@Composable
private fun TemplateThumbnailPage(
    template: VideoTemplate,
    isCurrentPage: Boolean,
    isPriorityPage: Boolean = false,  // True for visible/settled page (kept for future use)
    onVideoReady: (() -> Unit)? = null  // Callback when video is ready (for first video only)
) {
    val context = LocalContext.current

    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        // Use video player if videoUrl is available, otherwise fall back to image
        if (!template.videoUrl.isNullOrEmpty()) {
            android.util.Log.d("TemplatePreviewer", "Showing VIDEO for ${template.name}: ${template.videoUrl} (isCurrentPage=$isCurrentPage)")
            // Video preview (720p quality with lazy loading and disk caching)
            // Only auto-play when this page is visible to save performance
            TemplateVideoPlayer(
                videoUrl = template.videoUrl,
                modifier = Modifier.fillMaxSize(),
                autoPlay = isCurrentPage,  // Only play when visible
                loop = true,
                showControls = false,
                skipDebounce = onVideoReady != null,  // Skip 150ms delay for first video only
                onVideoReady = onVideoReady  // Notify when video is ready (for first video tracking)
            )
        } else {
            // Fallback to image preview
            android.util.Log.d("TemplatePreviewer", "Showing IMAGE for ${template.name}: videoUrl=${template.videoUrl}")
            val imageUrl = template.previewImagePath.ifEmpty { template.thumbnailPath.ifEmpty { null } }

            SubcomposeAsyncImage(
                model = ImageRequest.Builder(context)
                    .data(imageUrl)
                    .size(378, 672)  // Match original size - avoid upscaling
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .networkCachePolicy(CachePolicy.ENABLED)  // Enable network cache
                    .diskCacheKey("template_preview_${template.id}")  // Consistent disk cache key
                    .precision(Precision.INEXACT)  // Allow downsampling
                    .scale(Scale.FILL)  // Faster than FIT - fills viewport with crop
                    .allowHardware(!isCurrentPage)  // Hardware bitmap only for static pages (animation needs software bitmap)
                    .apply {
                        if (!isCurrentPage) {
                            // Static first frame only — bypasses ImageDecoderDecoder (animated WebP)
                            decoderFactory(BitmapFactoryDecoder.Factory())
                        }
                    }
                    .crossfade(false)  // Instant display, no animation delay
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
}

// ============================================
// PREVIEWS
// ============================================

private val previewTemplates = listOf(
    VideoTemplate(id = "t1", name = "Golden Hour Vibes", songId = 1L, effectSetId = "classic"),
    VideoTemplate(id = "t2", name = "Summer Memories", songId = 2L, effectSetId = "cinematic"),
    VideoTemplate(id = "t3", name = "City Lights", songId = 0L, effectSetId = "minimal"),
)

@androidx.compose.ui.tooling.preview.Preview(
    name = "Ready",
    showBackground = true,
    backgroundColor = 0xFF000000,
    device = "spec:width=390dp,height=844dp,dpi=460"
)
@Composable
private fun PreviewTemplatePreviewerReady() {
    com.videomaker.aimusic.ui.theme.VideoMakerTheme {
        TemplatePreviewerReadyContent(
            state = TemplatePreviewerUiState.Ready(
                templates = previewTemplates,
                initialPage = 0
            ),
            likedTemplateIds = emptySet(),
            unlockedTemplateIds = emptySet(),
            onPageChanged = {},
            onUseThisTemplate = { _, _ -> },
            onRatioSelected = { _, _ -> },
            onLikeTemplate = {},
            onNavigateBack = {},
            onFirstVideoReady = {}
        )
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
        TemplatePreviewerReadyContent(
            state = TemplatePreviewerUiState.Ready(
                templates = previewTemplates,
                initialPage = 0,
                isCreatingProject = true
            ),
            likedTemplateIds = setOf("t1"),
            unlockedTemplateIds = emptySet(),
            onPageChanged = {},
            onUseThisTemplate = { _, _ -> },
            onRatioSelected = { _, _ -> },
            onLikeTemplate = {},
            onNavigateBack = {},
            onFirstVideoReady = {}
        )
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
                    text = stringResource(R.string.template_error_load_failed),
                    color = Color.White,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp)
                )
                Button(onClick = {}) { Text(stringResource(R.string.back)) }
            }
        }
    }
}
