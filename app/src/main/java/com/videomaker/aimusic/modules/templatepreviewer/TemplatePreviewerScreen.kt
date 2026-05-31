package com.videomaker.aimusic.modules.templatepreviewer

import android.Manifest
import android.app.Activity
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.EaseInOutCubic
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.layout.width
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
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import co.alcheclub.lib.acccore.ads.compose.BannerAdView
import co.alcheclub.lib.acccore.ads.compose.NativeAdView
import co.alcheclub.lib.acccore.ads.loader.AdsLoaderService
import coil.compose.SubcomposeAsyncImage
import coil.decode.BitmapFactoryDecoder
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Precision
import coil.size.Scale
import com.videomaker.aimusic.BuildConfig
import com.videomaker.aimusic.R
import com.videomaker.aimusic.core.ads.InterstitialAdHelperExt
import com.videomaker.aimusic.core.ads.RewardedAdPresenter
import com.videomaker.aimusic.core.analytics.Analytics
import com.videomaker.aimusic.core.analytics.AnalyticsEvent
import com.videomaker.aimusic.core.constants.AdPlacement
import com.videomaker.aimusic.core.permission.NotificationPermissionCoordinator
import com.videomaker.aimusic.core.util.NumberFormatter
import com.videomaker.aimusic.domain.model.AspectRatio
import com.videomaker.aimusic.domain.model.MusicSong
import com.videomaker.aimusic.domain.model.VideoTemplate
import com.videomaker.aimusic.media.audio.AudioPreviewCache
import com.videomaker.aimusic.modules.templatepreviewer.components.TemplateVideoPlayer
import com.videomaker.aimusic.modules.templatepreviewer.components.UserSongBackgroundPlayer
import com.videomaker.aimusic.ui.components.AdBadge
import com.videomaker.aimusic.ui.components.AdBadgeStyle
import com.videomaker.aimusic.ui.components.AdsLoadingOverlay
import com.videomaker.aimusic.ui.components.ModifierExtension.clickableSingle
import com.videomaker.aimusic.ui.components.NotificationPermissionPromoDialog
import com.videomaker.aimusic.ui.components.NotificationPermissionSettingsGuideDialog
import com.videomaker.aimusic.ui.components.PrimaryButton
import com.videomaker.aimusic.ui.theme.FoundationBlack
import com.videomaker.aimusic.ui.theme.Primary
import com.videomaker.aimusic.ui.theme.SurfaceDark
import com.videomaker.aimusic.ui.theme.SurfaceDarkVariant
import com.videomaker.aimusic.ui.theme.SurfaceLight
import com.videomaker.aimusic.ui.theme.White16
import com.videomaker.aimusic.ui.theme.White40
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import org.koin.compose.koinInject

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
    val overrideSongId by viewModel.overrideSongId.collectAsStateWithLifecycle()
    val userSong by viewModel.userSong.collectAsStateWithLifecycle()
    val showUserSongPlayer = remember(overrideSongId) { overrideSongId >= 0L }
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val eventLocation = remember(sourceLocation) {
        sourceLocation?.takeIf { it.isNotBlank() } ?: AnalyticsEvent.Value.Location.PREVIEW_SWIPE
    }

    // Get dependencies for ad showing
    val activity = context as? Activity
    val adsLoaderService = koinInject<AdsLoaderService>()
    val notificationPermissionCoordinator = koinInject<NotificationPermissionCoordinator>()
    val ratingTriggerManager = koinInject<com.videomaker.aimusic.core.rating.RatingTriggerManager>()
    var showNotificationPromoDialog by remember { mutableStateOf(false) }
    var showNotificationSettingsGuideDialog by remember { mutableStateOf(false) }
    var pendingPermissionCheckAfterSettings by remember { mutableStateOf(false) }

    // True while an interstitial or rewarded ad is on screen. Set explicitly via the
    // ad SDK's onShown / close callbacks — lifecycle events alone are not reliable
    // for in-process interstitials (ProcessLifecycleOwner doesn't fire ON_STOP).
    //
    // Combined with `showLoadingOverlay` at the TemplatePreviewerReadyContent call site
    // (search "isAdShowing = isAdShowing || showLoadingOverlay") so the template video
    // is held paused + muted under either the loading-native overlay or an interstitial,
    // and plays from frame 0 the moment the overlay hides.
    var isAdShowing by remember { mutableStateOf(false) }

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

    // Preload "use template" interstitial ad on view appear
    // Non-blocking: if ad not ready when user taps, navigation proceeds immediately
    LaunchedEffect(Unit) {
        android.util.Log.d("TemplatePreviewerScreen", "🎬 Preloading use-template ad...")
        runCatching {
            InterstitialAdHelperExt.preloadInterstitial(
                adsLoaderService = adsLoaderService,
                placement = AdPlacement.INTERSTITIAL_TEMPLATE_PREVIEWER_USE,
                loadTimeoutMillis = null,
                showLoadingOverlay = false
            )
        }.onSuccess { success ->
            android.util.Log.d("TemplatePreviewerScreen", if (success) "✅ Use-template ad preload SUCCESS" else "⚠️ Use-template ad preload FAILED")
        }.onFailure { e ->
            android.util.Log.e("TemplatePreviewerScreen", "❌ Use-template ad preload exception: ${e.message}", e)
        }
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
                                // Ad closed OR failed to show - always navigate as fallback
                                // (idempotent if onShown already navigated)
                                isAdShowing = false
                                android.util.Log.d("TemplatePreviewerScreen", "✅ Back ad closed - navigating")
                                onNavigateBack()
                            },
                            onShown = {
                                // Mute players while ad is on screen, then navigate.
                                isAdShowing = true
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
                                // Ad closed or skipped - restore audio. Always runs (even if
                                // ad was skipped by frequency cap and onShown never fired), so
                                // this is safe to set to false unconditionally.
                                isAdShowing = false
                                android.util.Log.d("TemplatePreviewerScreen", "✅ Scroll ad action callback")
                            },
                            onShown = {
                                // Ad actually shown (not skipped by frequency cap) - mute audio.
                                isAdShowing = true
                                android.util.Log.d("TemplatePreviewerScreen", "🎬 Scroll ad shown to user")
                            },
                            bypassFrequencyCap = false,  // ✅ Let ACCCore enforce interval
                            showLoadingOverlay = false  // Background preloaded, no overlay
                        )
                    } else {
                        android.util.Log.w("TemplatePreviewerScreen", "⚠️ No activity - cannot show scroll ad")
                    }
                }

                is TemplatePreviewerNavigationEvent.RequestUseTemplateWithAd -> {
                    if (event.shouldShowAd && activity != null) {
                        InterstitialAdHelperExt.showInterstitial(
                            adsLoaderService = adsLoaderService,
                            activity = activity,
                            placement = AdPlacement.INTERSTITIAL_TEMPLATE_PREVIEWER_USE,
                            action = {
                                isAdShowing = false
                                onNavigateToAssetPicker(event.template, event.overrideSongId, event.aspectRatio)
                            },
                            onShown = {
                                isAdShowing = true
                                onNavigateToAssetPicker(event.template, event.overrideSongId, event.aspectRatio)
                            },
                            bypassFrequencyCap = true,
                            showLoadingOverlay = false
                        )
                    } else {
                        onNavigateToAssetPicker(event.template, event.overrideSongId, event.aspectRatio)
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
        onAdFailed = viewModel::onAdFailed,
        onAdShown = { isAdShowing = true },
        onAdClosed = { isAdShowing = false }
    )

    // Track loading state
    var showLoadingOverlay by remember { mutableStateOf(true) }
    var firstVideoReady by remember { mutableStateOf(false) }

    // Phase 2 late-ad: shows ONLY the native ad strip at bottom (no full overlay/spinner),
    // while the video underneath is paused + muted via the isAdShowing combined flag.
    var showPhase2NativeAd by remember { mutableStateOf(false) }

    // Hoisted from TemplatePreviewerReadyContent so the loading LaunchedEffect (below)
    // can read it from the background poll loop and skip the late-ad overlay if the
    // user has already engaged with the pager.
    var hasSwipedTemplate by remember { mutableStateOf(false) }

    // Loading overlay timing — two-phase race(ad-ready, 2s budget) with late-ad followup.
    //
    // Phase 1 (initial overlay, 0..2s):
    //   - Video starts buffering under the overlay (TemplatePreviewerReadyContent is already
    //     mounted; player.prepare() runs immediately for the first page).
    //   - If ad cached on entry from Home preload: hold 1s for impression → reveal video.
    //   - Otherwise poll every 200ms up to 2s. If ad becomes ready in-window, hold 1s for
    //     impression. If 2s elapses, hide overlay so the user starts watching the video.
    //
    // Phase 2 (background poll, only if Phase 1 timed out, 2..15s):
    //   - Keep polling for the ad in the background (200ms interval) up to 15s total.
    //   - If the ad becomes ready AND the user hasn't swiped to another template
    //     (hasSwipedTemplate == false): mount the native ad strip at the bottom for 1s
    //     impression, then unmount. No fullscreen overlay/spinner — only the ad shows.
    //     The video underneath is paused + muted via the
    //     `isAdShowing = isAdShowing || showLoadingOverlay || showPhase2NativeAd` plumbing,
    //     and resumes from its current position when the ad strip hides.
    //   - If the user swipes before the ad loads: skip the late ad (don't interrupt
    //     active engagement). The native ad stays in the SDK cache for next placement.
    //
    // Compose cancels this coroutine when the composable leaves, so navigating away
    // automatically stops the background poll — no leaks.
    LaunchedEffect(Unit) {
        val startTime = System.currentTimeMillis()
        val adCachedOnEntry = adsLoaderService.isNativeAdReady(AdPlacement.NATIVE_TEMPLATE_PREVIEWER_LOADING)
        var adReady = adCachedOnEntry

        if (adCachedOnEntry) {
            android.util.Log.d("TemplatePreviewerLoading", "✅ Ad cached on entry - 1s impression hold")
        } else {
            android.util.Log.d("TemplatePreviewerLoading", "⏳ Ad not cached, racing 2s budget...")
            while (!adReady && (System.currentTimeMillis() - startTime) < 2_000) {
                delay(200)
                if (adsLoaderService.isNativeAdReady(AdPlacement.NATIVE_TEMPLATE_PREVIEWER_LOADING)) {
                    val elapsedMs = System.currentTimeMillis() - startTime
                    android.util.Log.d("TemplatePreviewerLoading", "✅ Ad loaded after ${elapsedMs}ms")
                    adReady = true
                }
            }
        }

        val initialTimeoutHit = !adReady
        if (adReady) {
            delay(1_000) // Impression hold — networks generally count at ≥1s visible.
        } else {
            android.util.Log.d("TemplatePreviewerLoading", "⏱️ Ad timeout (2s) - showing video, will retry in background")
        }

        val initialOverlayMs = System.currentTimeMillis() - startTime
        android.util.Log.d("TemplatePreviewerLoading", "✅ INITIAL OVERLAY DONE (${initialOverlayMs}ms, firstVideoReady=$firstVideoReady)")

        showLoadingOverlay = false

        // Phase 2: late-ad followup only if the initial 2s budget expired without an ad.
        if (!initialTimeoutHit) return@LaunchedEffect

        android.util.Log.d("TemplatePreviewerLoading", "🔁 Phase 2: polling for late ad up to 15s total...")
        while ((System.currentTimeMillis() - startTime) < 15_000) {
            delay(200)
            if (hasSwipedTemplate) {
                val elapsedMs = System.currentTimeMillis() - startTime
                android.util.Log.d("TemplatePreviewerLoading", "↩️ Late ad skipped (user swiped) at ${elapsedMs}ms")
                Analytics.track(
                    name = "template_previewer_late_ad",
                    params = mapOf("outcome" to "skipped_swiped", "elapsed_ms" to elapsedMs)
                )
                return@LaunchedEffect
            }
            if (adsLoaderService.isNativeAdReady(AdPlacement.NATIVE_TEMPLATE_PREVIEWER_LOADING)) {
                val elapsedMs = System.currentTimeMillis() - startTime
                android.util.Log.d("TemplatePreviewerLoading", "✅ Late ad ready at ${elapsedMs}ms - showing native ad strip")
                showPhase2NativeAd = true
                delay(1_000) // Impression hold (video pauses+mutes via isAdShowing combined flag).
                showPhase2NativeAd = false
                Analytics.track(
                    name = "template_previewer_late_ad",
                    params = mapOf("outcome" to "shown", "elapsed_ms" to elapsedMs)
                )
                return@LaunchedEffect
            }
        }
        android.util.Log.d("TemplatePreviewerLoading", "⏱️ Late ad gave up at 15s - no impression this session")
        Analytics.track(
            name = "template_previewer_late_ad",
            params = mapOf("outcome" to "skipped_timeout", "elapsed_ms" to 15_000L)
        )
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
                    showUserSongPlayer = showUserSongPlayer,
                    userSong = userSong,
                    // Treat the loading-native overlay AND the Phase 2 ad strip as
                    // "ad showing" so the video pauses+mutes underneath either one,
                    // then auto-resumes once both hide.
                    isAdShowing = isAdShowing || showLoadingOverlay || showPhase2NativeAd,
                    hasSwipedTemplate = hasSwipedTemplate,
                    onPageChanged = viewModel::onPageChanged,
                    onUseThisTemplate = viewModel::onUseThisTemplate,
                    onRatioSelected = viewModel::onRatioSelected,
                    onLikeTemplate = viewModel::onLikeTemplate,
                    onSwipeTemplate = {
                        hasSwipedTemplate = true
                        ratingTriggerManager.onTemplateSwiped()
                    },
                    eventLocation = eventLocation,
                    onNavigateBack = viewModel::onNavigateBack,
                    onFirstVideoReady = { firstVideoReady = true },
                    onRefresh = { excludeIds -> viewModel.refresh(excludeIds) }
                )

                // Show loading overlay on top until ad display completes
                if (showLoadingOverlay) {
                    LoadingStateWithAd()
                }

                // Phase 2 late-ad strip: only the native ad mounts at the bottom.
                // Video underneath pauses+mutes via the combined isAdShowing flag above.
                if (showPhase2NativeAd) {
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
    showUserSongPlayer: Boolean,
    userSong: MusicSong?,
    isAdShowing: Boolean = false,
    hasSwipedTemplate: Boolean = false,
    onPageChanged: (Int) -> Unit,
    onUseThisTemplate: (VideoTemplate, AspectRatio) -> Unit,
    onRatioSelected: (VideoTemplate, AspectRatio) -> Unit,
    onLikeTemplate: (VideoTemplate) -> Unit,
    onSwipeTemplate: () -> Unit = {},
    eventLocation: String = AnalyticsEvent.Value.Location.PREVIEW_SWIPE,
    onNavigateBack: () -> Unit,
    onFirstVideoReady: () -> Unit,
    onRefresh: (excludeIds: Set<String>) -> Unit = {}
) {
    // Track viewed template IDs for refresh exclusion logic
    var viewedTemplateIds by remember { mutableStateOf(setOf<String>()) }
    val showRefreshIcon = viewedTemplateIds.size >= 5

    var showRefreshTooltip by remember { mutableStateOf(false) }
    // Show tooltip only after the refresh icon becomes visible
    LaunchedEffect(showRefreshIcon) {
        if (showRefreshIcon) {
            delay(800)
            showRefreshTooltip = true
            delay(6000)
            showRefreshTooltip = false
        }
    }
    val templates = state.templates
    val screenSessionId = remember { Analytics.newScreenSessionId() }
    val pagerState = rememberPagerState(
        initialPage = initialVirtualPage(state.initialPage, templates.size),
        pageCount = { VIRTUAL_PAGE_COUNT }
    )

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
            .collect { settledPage ->
                // Track viewed template IDs for refresh exclusion
                val template = templates.getOrNull(settledPage % templates.size)
                if (template != null) {
                    viewedTemplateIds = viewedTemplateIds + template.id
                }
                onSwipeTemplate()
                onPageChanged(settledPage)
            }
    }

    // Track initial template as viewed
    LaunchedEffect(Unit) {
        val initialTemplate = templates.getOrNull(pagerState.settledPage % templates.size)
        if (initialTemplate != null) {
            viewedTemplateIds = viewedTemplateIds + initialTemplate.id
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
                // Impression on first entry is already fired by the source list
                // (home_template / home_banner / search_result / etc.). Only fire
                // here on subsequent swipes with location=preview_swipe.
                if (!isFirstSettledEmission) {
                    Analytics.trackTemplateImpression(
                        templateId = template.id,
                        templateName = template.name,
                        location = AnalyticsEvent.Value.Location.PREVIEW_SWIPE,
                        screenSessionId = ""
                    )
                }
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
            key = { pageIndex -> pageIndex }
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
                showUserSongPlayer = showUserSongPlayer,
                userSong = userSong,
                isAdShowing = isAdShowing,
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

        // Top bar — back button (left) + template name (center) + refresh button (right)
        val currentTemplateName = templates.getOrNull(pagerState.settledPage % templates.size)?.name
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 8.dp, end = 8.dp, top = 8.dp),
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
            
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                if (currentTemplateName != null) {
                    Text(
                        text = currentTemplateName,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Only show refresh icon after viewing 5+ templates
            AnimatedVisibility(
                visible = showRefreshIcon,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                IconButton(
                    onClick = {
                        onRefresh(viewedTemplateIds)
                        showRefreshTooltip = false
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .background(color = Color.Black.copy(alpha = 0.4f), shape = CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh",
                        tint = Color.White
                    )
                }
            }

            // Invisible spacer to keep the back button / title centered when refresh icon is hidden
            if (!showRefreshIcon) {
                Spacer(modifier = Modifier.size(48.dp))
            }
        }

        // Custom Tooltip Popup pointing directly UP to the Refresh Icon
        AnimatedVisibility(
            visible = showRefreshTooltip && showRefreshIcon,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 64.dp, end = 8.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.End,
                modifier = Modifier.width(280.dp)
            ) {
                // Triangle pointing UP
                Box(
                    modifier = Modifier
                        .padding(end = 16.dp)
                        .size(width = 16.dp, height = 8.dp)
                        .drawBehind {
                            val path = Path().apply {
                                moveTo(size.width / 2f, 0f)
                                lineTo(size.width, size.height)
                                lineTo(0f, size.height)
                                close()
                            }
                            drawPath(path, color = Color(0xFF1E1E1E))
                        }
                )
                // Main popup container
                Box(
                    modifier = Modifier
                        .background(color = Color(0xFF1E1E1E), shape = RoundedCornerShape(12.dp))
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "✨",
                            fontSize = 14.sp
                        )
                        val annotatedText = buildAnnotatedString {
                            append("We've just updated a lot of new content for you! ")
                            pushStringAnnotation(tag = "refresh", annotation = "refresh")
                            withStyle(
                                style = SpanStyle(
                                    color = Primary,
                                    fontWeight = FontWeight.Bold,
                                    textDecoration = TextDecoration.Underline
                                )
                            ) {
                                append("Refresh now")
                            }
                            pop()
                            append(" to check it out.")
                        }
                        ClickableText(
                            text = annotatedText,
                            style = TextStyle(
                                color = Color.White,
                                fontSize = 13.sp,
                                lineHeight = 18.sp
                            ),
                            onClick = { offset ->
                                annotatedText.getStringAnnotations(tag = "refresh", start = offset, end = offset)
                                    .firstOrNull()?.let {
                                        onRefresh(viewedTemplateIds)
                                        showRefreshTooltip = false
                                    }
                            }
                        )
                    }
                }
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
                        text = NumberFormatter.formatCount(currentTemplate?.viewCount ?: 0L),
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
                    val defaultRatio = aspectRatioFromString(template.aspectRatio)
                    Analytics.trackRatioSelect(defaultRatio.shortLabel)
                    Analytics.trackTemplateSelect(
                        templateId = template.id,
                        templateName = template.name,
                        location = templateEventLocation
                    )
                    val isLocked = template.isPremium && !unlockedTemplateIds.contains(template.id)
                    if (isLocked) {
                        onRatioSelected(template, defaultRatio)
                    } else {
                        onUseThisTemplate(template, defaultRatio)
                    }
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
    showUserSongPlayer: Boolean = false,
    userSong: com.videomaker.aimusic.domain.model.MusicSong? = null,
    isAdShowing: Boolean = false,  // When true, mute both video and user-song audio (volume = 0f)
    onVideoReady: (() -> Unit)? = null  // Callback when video is ready (for first video only)
) {
    val context = LocalContext.current
    val audioPreviewCache: AudioPreviewCache = koinInject()

    // Track when template video has loaded successfully before starting user song
    var isTemplateVideoReady by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        // Use video player if videoUrl is available, otherwise fall back to image
        if (!template.videoUrl.isNullOrEmpty()) {
            android.util.Log.d("TemplatePreviewer", "Showing VIDEO for ${template.name}: ${template.videoUrl} (isCurrentPage=$isCurrentPage)")
            // Video preview (720p quality with lazy loading and disk caching).
            // While an ad/loading overlay is on top: prepare() still runs (mounted under
            // overlay) so buffering proceeds, but autoPlay is gated so the player stays
            // paused at frame 0 — no wasted decode under the overlay, and when the
            // overlay hides autoPlay flips true → LaunchedEffect(autoPlay, isPrepared)
            // calls play() for a clean frame-0 start.
            TemplateVideoPlayer(
                videoUrl = template.videoUrl,
                modifier = Modifier.fillMaxSize(),
                autoPlay = isCurrentPage && !isAdShowing,
                loop = true,
                showControls = false,
                skipDebounce = onVideoReady != null,  // Skip 150ms delay for first video only
                onVideoReady = {
                    isTemplateVideoReady = true  // Mark template as ready
                    onVideoReady?.invoke()  // Notify parent for first video tracking
                },
                // Mute template video when an ad is on screen or when user song is playing.
                volume = if (isAdShowing || showUserSongPlayer) 0f else 1.0f
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

        // User song background player - plays when user selected a song AND template video is ready
        if (showUserSongPlayer && userSong != null && isCurrentPage && isTemplateVideoReady) {
            UserSongBackgroundPlayer(
                song = userSong,
                cacheDataSourceFactory = audioPreviewCache.cacheDataSourceFactory,
                autoPlay = true,
                loop = true,
                startFromHook = true,
                // Mute (but keep playing) while an ad is on screen, so playback resumes
                // seamlessly when the ad closes — per product spec.
                volume = if (isAdShowing) 0f else 1.0f
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
            showUserSongPlayer = false,
            userSong = null,
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
            showUserSongPlayer = false,
            userSong = null,
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
