package com.videomaker.aimusic.modules.home

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import co.alcheclub.lib.acccore.ads.compose.BannerAdView
import co.alcheclub.lib.acccore.ads.compose.NativeAdView
import co.alcheclub.lib.acccore.ads.loader.AdsLoaderException
import co.alcheclub.lib.acccore.ads.loader.AdsLoaderService
import com.videomaker.aimusic.BuildConfig
import com.videomaker.aimusic.R
import com.videomaker.aimusic.core.analytics.Analytics
import com.videomaker.aimusic.core.analytics.AnalyticsEvent
import com.videomaker.aimusic.domain.model.MusicSong
import com.videomaker.aimusic.core.constants.AdPlacement
import com.videomaker.aimusic.core.permission.NotificationPermissionCoordinator
import com.videomaker.aimusic.core.popup.TrendingPopupCoordinator
import com.videomaker.aimusic.core.rating.RatingTriggerManager
import com.videomaker.aimusic.media.audio.AudioPreviewCache
import com.videomaker.aimusic.modules.gallery.GalleryScreen
import com.videomaker.aimusic.modules.gallery.GalleryUiState
import com.videomaker.aimusic.modules.gallery.GalleryViewModel
import com.videomaker.aimusic.modules.home.components.HomeFabOverlay
import com.videomaker.aimusic.modules.home.components.ProjectsTabContent
import com.videomaker.aimusic.modules.projects.ProjectsViewModel
import com.videomaker.aimusic.modules.songs.MusicPlayerBottomSheet
import com.videomaker.aimusic.modules.songs.SongsScreen
import com.videomaker.aimusic.modules.songs.SongsViewModel
import com.videomaker.aimusic.ui.components.ModifierExtension.clickableSingle
import com.videomaker.aimusic.ui.theme.AppDimens
import com.videomaker.aimusic.ui.theme.NewIdeasBackground
import com.videomaker.aimusic.ui.theme.PreviewButtonBackground
import com.videomaker.aimusic.ui.theme.PreviewCardBackground
import com.videomaker.aimusic.ui.theme.Primary
import com.videomaker.aimusic.ui.theme.PrimaryDark
import com.videomaker.aimusic.ui.theme.TextInactive
import com.videomaker.aimusic.ui.theme.VideoMakerTheme
import com.videomaker.aimusic.ui.theme.WelcomeBackBackground
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import com.videomaker.aimusic.core.ads.AdClickDetector
import com.videomaker.aimusic.core.ads.AdPlacementConfigService

/** Two scroll sessions ending within this window count as a single swipe (dedupes split flings). */
private const val GESTURE_COOLDOWN_MS = 250L

/** On refresh, jump instantly to this item if further down, then smooth-animate the rest to top. */
private const val NEAR_TOP_JUMP_INDEX = 8

/** Stable no-op progress provider for tabs without a collapsing create pill (e.g. Songs). */
private val ZeroProgress: () -> Float = { 0f }

/**
 * HomeScreen - Main screen with tabbed navigation
 *
 * Features:
 * - 3 tabs: Gallery, Songs, My Projects
 * - Tab titles at top left with white indicator bar
 * - Settings button at top right
 * - Swipeable content with HorizontalPager
 *
 * @param onSettingsClick Callback when Settings button is clicked
 * @param onCreateClick Callback when Create action is triggered
 * @param onProjectClick Callback when a project is clicked
 */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun HomeScreen(
    galleryViewModel: GalleryViewModel,
    songsViewModel: SongsViewModel,
    projectsViewModel: ProjectsViewModel,
    initialTab: Int = 0,
    highlightProjectId: String? = null,
    projectHintMode: String? = null,
    onSettingsClick: (String) -> Unit = {},
    onCreateClick: () -> Unit = {},
    onProjectClick: (projectId: String, thumbnailUri: String?) -> Unit = { _, _ -> },
    onNavigateToSearch: () -> Unit = {},
    onNavigateToSongSearch: () -> Unit = {},
    onNavigateToSuggestedSongsList: () -> Unit = {},
    onNavigateToWeeklyRankingList: () -> Unit = {},
    onNavigateToTemplateDetail: (String, String?) -> Unit = { _, _ -> },
    onNavigateToAllTemplates: (String?) -> Unit = {},
    onNavigateToAssetPicker: (songId: Long) -> Unit = {},
    onNavigateToAllSongs: () -> Unit = {},
    onNavigateToTemplatePreviewerWithSong: (songId: Long) -> Unit = {}
) {
    val adClickDetector: AdClickDetector = koinInject()
    val adPlacementConfigService: AdPlacementConfigService = koinInject()
    val context = LocalContext.current
    val notificationPermissionCoordinator = koinInject<NotificationPermissionCoordinator>()
    val adsLoaderService = koinInject<AdsLoaderService>()
    val ratingTriggerManager = koinInject<RatingTriggerManager>()
    val trendingPopupCoordinator = koinInject<TrendingPopupCoordinator>()

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
    }

    LaunchedEffect("notification_permission") {
        if (notificationPermissionCoordinator.shouldRequestHomeFirstTimePermission(context)) {
            Analytics.trackPermissionRender(
                perType = AnalyticsEvent.Value.PerType.NOTI,
                popType = AnalyticsEvent.Value.PopType.SYSTEM
            )
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Preload template previewer ad while user is on home screen
    LaunchedEffect("ad_preload") {
        try {
            adsLoaderService.loadNative(AdPlacement.NATIVE_TEMPLATE_PREVIEWER_LOADING)
        } catch (e: AdsLoaderException) {
            // Ad failed to load - not critical, user can still use the app
            android.util.Log.w("HomeScreen", "Failed to preload template previewer ad: ${e.message}")
        } catch (e: Exception) {
            // Unexpected error - log but don't crash
            android.util.Log.e("HomeScreen", "Unexpected error preloading ad: ${e.message}", e)
        }
    }

    LaunchedEffect(Unit) {
        ratingTriggerManager.onHomeScreenFocused(initialTab)
    }

    val tabs = listOf(
        stringResource(R.string.home_tab_gallery),
        stringResource(R.string.home_tab_songs),
        stringResource(R.string.home_tab_projects)
    )

    val pagerState = rememberPagerState(
        initialPage = initialTab.coerceIn(0, tabs.size - 1),
        pageCount = { tabs.size }
    )
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    var topBarHeightPx by remember { mutableIntStateOf(0) }
    val topBarHeight = with(density) { topBarHeightPx.toDp() }
    val tabNameByIndex: (Int) -> String = { index ->
        when (index) {
            0 -> AnalyticsEvent.Value.TabName.GALLERY
            1 -> AnalyticsEvent.Value.TabName.SONG
            else -> AnalyticsEvent.Value.TabName.LIBRARY
        }
    }
    var lastSettledPage by remember { mutableIntStateOf(pagerState.currentPage) }
    var hasSentInitialTabView by remember { mutableStateOf(false) }

    val galleryListState = rememberLazyStaggeredGridState()
    val songsListState = rememberLazyListState()

    val galleryUiState by galleryViewModel.uiState.collectAsStateWithLifecycle()
    val galleryTemplatesHeaderIndex = remember(galleryUiState) {
        val successState = galleryUiState as? GalleryUiState.Success
        val featuredTemplates = successState?.featuredTemplates ?: emptyList()
        if (featuredTemplates.isNotEmpty()) 4 else 2
    }

    // Swipe-gesture counters drive the bottom FAB state machine per tab.
    // On the 3rd counted downward swipe: the create pill collapses into the (+) AND
    // "See What's New" reveals. Scrolling back near the top resets the count → both fold back.
    var galleryGestureCount by remember { mutableIntStateOf(0) }
    var songsGestureCount by remember { mutableIntStateOf(0) }
    // Scroll magnitude at which the last swipe was counted — counting net distance since this
    // (not per-session) makes a split fling count as one swipe. Reset with the count at top.
    var galleryLastCountedMag by remember { mutableIntStateOf(0) }
    var songsLastCountedMag by remember { mutableIntStateOf(0) }

    val galleryScrollMagnitude = remember(galleryTemplatesHeaderIndex) {
        derivedStateOf {
            val targetIndex = galleryTemplatesHeaderIndex + 1
            val visibleItems = galleryListState.layoutInfo.visibleItemsInfo
            if (visibleItems.isEmpty()) return@derivedStateOf 0

            val targetVisibleItem = visibleItems.firstOrNull { it.index == targetIndex }
            if (targetVisibleItem != null) {
                (-targetVisibleItem.offset.y).coerceAtLeast(0)
            } else {
                val firstVisible = visibleItems.first()
                if (firstVisible.index > targetIndex) {
                    val avgSize = visibleItems.sumOf { it.size.height } / visibleItems.size
                    val estimatedPassed = (firstVisible.index - targetIndex) * avgSize
                    (estimatedPassed - firstVisible.offset.y).coerceAtLeast(0)
                } else {
                    0
                }
            }
        }
    }

    val songsScrollMagnitude = remember {
        derivedStateOf {
            // Track scroll from near the very top so the FIRST swipe already counts. (Was 8 —
            // the tall songs header spans ~8 items, so the first swipe through it produced
            // magnitude 0 and didn't count, which is why it took 4 swipes instead of 3.)
            val targetIndex = 1
            val visibleItems = songsListState.layoutInfo.visibleItemsInfo
            if (visibleItems.isEmpty()) return@derivedStateOf 0

            val targetVisibleItem = visibleItems.firstOrNull { it.index == targetIndex }
            if (targetVisibleItem != null) {
                (-targetVisibleItem.offset).coerceAtLeast(0)
            } else {
                val firstVisible = visibleItems.first()
                if (firstVisible.index > targetIndex) {
                    val avgSize = visibleItems.sumOf { it.size } / visibleItems.size
                    val estimatedPassed = (firstVisible.index - targetIndex) * avgSize
                    (estimatedPassed - firstVisible.offset).coerceAtLeast(0)
                } else {
                    0
                }
            }
        }
    }

    val galleryHeight = remember {
        derivedStateOf {
            val viewportHeight = galleryListState.layoutInfo.viewportEndOffset - galleryListState.layoutInfo.viewportStartOffset
            if (viewportHeight > 0) viewportHeight else 2000
        }
    }

    val songsHeight = remember {
        derivedStateOf {
            val viewportHeight = songsListState.layoutInfo.viewportEndOffset - songsListState.layoutInfo.viewportStartOffset
            if (viewportHeight > 0) viewportHeight else 2000
        }
    }

    // Count a downward swipe at each scroll-session end, but measured as NET distance scrolled
    // since the previous counted swipe (≥ ~35% of the viewport — a real swipe, not a flick).
    // Because it's cumulative, a fling that splits into two sessions only adds one count (the
    // small second half doesn't clear the threshold), and a single long fling is always just
    // one count — so it genuinely takes 3 separate swipes, never 2.
    LaunchedEffect(galleryListState) {
        var lastCountAt = 0L
        snapshotFlow { galleryListState.isScrollInProgress }
            .collect { scrolling ->
                if (!scrolling) {
                    val threshold = (galleryHeight.value * 0.35f).toInt().coerceAtLeast(300)
                    val now = System.currentTimeMillis()
                    val mag = galleryScrollMagnitude.value
                    if (mag - galleryLastCountedMag >= threshold && now - lastCountAt >= GESTURE_COOLDOWN_MS) {
                        galleryGestureCount++
                        galleryLastCountedMag = mag
                        lastCountAt = now
                    }
                }
            }
    }

    LaunchedEffect(songsListState) {
        var lastCountAt = 0L
        snapshotFlow { songsListState.isScrollInProgress }
            .collect { scrolling ->
                if (!scrolling) {
                    val threshold = (songsHeight.value * 0.35f).toInt().coerceAtLeast(300)
                    val now = System.currentTimeMillis()
                    val mag = songsScrollMagnitude.value
                    if (mag - songsLastCountedMag >= threshold && now - lastCountAt >= GESTURE_COOLDOWN_MS) {
                        songsGestureCount++
                        songsLastCountedMag = mag
                        lastCountAt = now
                    }
                }
            }
    }

    // Reset the sequence (fold back) once the list returns near the top. No deep-scroll
    // shortcut: CTA only appears after the user has genuinely completed 3 downward swipes,
    // never from a single long fling. snapshotFlow on the item index keeps this cheap.
    LaunchedEffect(galleryListState) {
        snapshotFlow { galleryListState.firstVisibleItemIndex }
            .collect { index ->
                if (index <= galleryTemplatesHeaderIndex) {
                    galleryGestureCount = 0
                    galleryLastCountedMag = 0
                }
            }
    }

    LaunchedEffect(songsListState) {
        snapshotFlow { songsListState.firstVisibleItemIndex }
            .collect { index ->
                if (index <= 7) {
                    songsGestureCount = 0
                    songsLastCountedMag = 0
                }
            }
    }

    // After the 3rd completed downward swipe the create pill collapses; reset fold-back at top.
    val galleryCollapsed by remember {
        derivedStateOf { galleryGestureCount >= 3 }
    }

    // Collapse progress (0 = expanded pill, 1 = collapsed into the +).
    //  - Collapsing is TIME-animated (a clean morph fired by the swipe trigger).
    //  - Re-expanding is SCROLL-LINKED: as you scroll up into the last ~0.3 viewport before the
    //    top, progress follows the scroll position directly (no separate time clock), so the
    //    fold-back STARTS during the scroll-up and tracks it smoothly to completion at the top.
    // (firstVisibleItemIndex can't drive this — the whole grid is one LazyColumn item — but the
    // grid item's scroll offset, captured by galleryScrollMagnitude, can.)
    val galleryExpandFraction = remember(galleryTemplatesHeaderIndex) {
        derivedStateOf {
            val ramp = (galleryHeight.value * 0.3f).coerceAtLeast(1f)
            (galleryScrollMagnitude.value / ramp).coerceIn(0f, 1f)
        }
    }
    val galleryCollapse = remember { Animatable(0f) }
    val galleryCollapseProgressProvider = remember { { galleryCollapse.value } }

    LaunchedEffect(galleryCollapsed) {
        galleryCollapse.animateTo(
            targetValue = if (galleryCollapsed) 1f else 0f,
            animationSpec = tween(520, easing = FastOutSlowInEasing)
        )
    }
    LaunchedEffect(galleryListState) {
        snapshotFlow { galleryExpandFraction.value }
            .collect { fraction ->
                // Only drive progress DOWN to follow an upward scroll; the collapse animation
                // owns the upward direction (and frac == 1 while deep, so it never caps it).
                if (galleryCollapsed && fraction < galleryCollapse.value) {
                    galleryCollapse.snapTo(fraction)
                }
            }
    }

    val isNewIdeasVisible by remember {
        derivedStateOf {
            when (pagerState.currentPage) {
                0 -> galleryGestureCount >= 3
                1 -> songsGestureCount >= 3
                else -> false
            }
        }
    }

    LaunchedEffect(isNewIdeasVisible, pagerState.currentPage) {
        if (isNewIdeasVisible) {
            when (pagerState.currentPage) {
                0 -> Analytics.trackIdeaTemplateImpression()
                1 -> Analytics.trackIdeaSongImpression()
            }
        }
    }

    // Hide a tab's music player the moment the pager COMMITS to leaving that tab, rather than
    // waiting for the swipe to fully settle. targetPage flips at the gesture/fling commit (well
    // before settledPage updates), so the sheet disappears together with the swipe motion instead
    // of lingering through the whole animation and then popping out late.
    //  - We dismiss a player only when the target is a DIFFERENT tab than the one that owns it,
    //    so a small drag that snaps back to the Song tab (target stays 1) keeps the player, and
    //    the banner "Try It" preview (animateScrollToPage(1) → target 1) is never wiped.
    //  - Songs player → tab 1, Projects player → tab 2.
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.targetPage }
            .distinctUntilChanged()
            .collect { targetPage ->
                if (targetPage != 1) songsViewModel.onDismissPlayer()
                if (targetPage != 2) projectsViewModel.onDismissPlayer()
            }
    }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { settledPage ->
                val currentTab = tabNameByIndex(settledPage)
                // Tab render must precede any rewarded popup on this tab, same timing as tab_view.
                Analytics.trackTabRender(currentTab)
                Analytics.trackTabView(currentTab)

                if (hasSentInitialTabView && lastSettledPage != settledPage) {
                    Analytics.trackTabSwitch(
                        from = tabNameByIndex(lastSettledPage),
                        to = currentTab
                    )
                } else {
                    hasSentInitialTabView = true
                }
                lastSettledPage = settledPage

                when (settledPage) {
                    0 -> {
                        galleryViewModel.onTabFocused()
                        Analytics.trackRefreshStartTemplate()
                    }
                    1 -> {
                        songsViewModel.onTabFocused()
                        Analytics.trackRefreshStartSong()
                    }
                    // My Videos tab: no trending popup belongs here. Clear the active surface
                    // so any Showing popup is hidden (and restored when swiping back).
                    else -> trendingPopupCoordinator.onPopupSurfaceInactive()
                }
            }
    }

    // Player overlay state lifted here so MusicPlayerBottomSheet can render full-screen
    // above both the bottom NativeAdView and the tab content.
    val audioPreviewCache: AudioPreviewCache = koinInject()
    val songsSelectedSong by songsViewModel.selectedSong.collectAsStateWithLifecycle()
    val projectsSelectedSong by projectsViewModel.selectedSong.collectAsStateWithLifecycle()
    val isMusicPlayerActive = songsSelectedSong != null || projectsSelectedSong != null
    LaunchedEffect(isMusicPlayerActive) {
        trendingPopupCoordinator.setMusicPlayerActive(isMusicPlayerActive)
    }
    // [Experiment] CTA "Try it" hides while the user scrolls the list to discover other songs
    // during preview; reappears on player interaction or new song select.
    var isSongsCtaVisible by remember { mutableStateOf(true) }
    var isProjectsCtaVisible by remember { mutableStateOf(true) }
    // New song selected → reveal CTA again. Keyed by song id so re-selecting same song is a no-op.
    LaunchedEffect(songsSelectedSong?.id) {
        if (songsSelectedSong != null) isSongsCtaVisible = true
    }
    LaunchedEffect(projectsSelectedSong?.id) {
        if (projectsSelectedSong != null) isProjectsCtaVisible = true
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
        ) {
            // Main content area with tabs
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                // Pager Content — full-bleed so each page can draw its own background
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    beyondViewportPageCount = 2  // Keep all 3 tabs composed to avoid reloading on tab switch
                ) { page ->
                    when (page) {
                        0 -> GalleryTabContent(
                            viewModel = galleryViewModel,
                            isVisible = pagerState.settledPage == 0,
                            listState = galleryListState,
                            onUserInteraction = {},
                            onCreateClick = onCreateClick,
                            onNavigateToSearch = onNavigateToSearch,
                            onNavigateToTemplateDetail = onNavigateToTemplateDetail,
                            onNavigateToAllTemplates = onNavigateToAllTemplates,
                            onNavigateToSongPreview = { song ->
                                // Switch to the Song tab and open the preview player for this song.
                                // The banner hands us the already-resolved MusicSong, so open the
                                // player SYNCHRONOUSLY (no re-fetch) — the popup appears immediately
                                // instead of after a network round-trip. animateScrollToPage(1) sets
                                // targetPage = 1, and the tab-leave handler only dismisses the Song
                                // player when the target is NOT the Song tab, so this just-opened
                                // popup survives the page change.
                                coroutineScope.launch { pagerState.animateScrollToPage(1) }
                                songsViewModel.onSongClick(
                                    song = song,
                                    playlist = emptyList(),
                                    categoryLocation = AnalyticsEvent.Value.Location.SONG_PREVIEW,
                                )
                            },
                            topBarHeight = topBarHeight
                        )
                        1 -> SongsTabContent(
                            viewModel = songsViewModel,
                            listState = songsListState,
                            onUserInteraction = {},
                            topBarHeight = topBarHeight,
                            isVisible = pagerState.settledPage == 1,
                            onNavigateToSearch = {
                                songsViewModel.onDismissPlayer()
                                onNavigateToSongSearch.invoke()
                            },
                            onNavigateToSuggestedSongsList = {
                                songsViewModel.onDismissPlayer()
                                onNavigateToSuggestedSongsList.invoke()
                                                             },
                            onNavigateToWeeklyRankingList = {
                                songsViewModel.onDismissPlayer()
                                onNavigateToWeeklyRankingList.invoke()
                                                            },
                            onNavigateToAssetPicker = {
                                songsViewModel.onDismissPlayer()
                                onNavigateToAssetPicker.invoke(it) },
                            onNavigateToTemplatePreviewerWithSong = {
                                songsViewModel.onDismissPlayer()
                                onNavigateToTemplatePreviewerWithSong.invoke(it) },
                            onListScroll = { isSongsCtaVisible = false }
                        )
                        2 -> ProjectsTabContent(
                            viewModel = projectsViewModel,
                            highlightProjectId = highlightProjectId,
                            hintMode = projectHintMode,
                            isVisible = pagerState.settledPage == 2,
                            onCreateClick = {
                                Analytics.trackCreationStart(AnalyticsEvent.Value.Location.LIBRARY)
                                onNavigateToTemplateDetail("", AnalyticsEvent.Value.Location.LIBRARY_RCM)
                            }, // Open template previewer with first template + analytics
                            onProjectClick = onProjectClick,
                            onNavigateToTemplateDetail = onNavigateToTemplateDetail,
                            onNavigateToSongSearch = onNavigateToSongSearch,
                            onNavigateToAllSongs = onNavigateToAllSongs,
                            onNavigateToTemplateSearch = onNavigateToSearch,
                            onNavigateToAllTemplates = { onNavigateToAllTemplates(null) },
                            onNavigateToAssetPicker = onNavigateToAssetPicker,
                            topBarHeight = topBarHeight,
                            onListScroll = { isProjectsCtaVisible = false }
                        )
                    }
                }

            // Top Bar with Tabs and Settings — on top, respecting system bars
            // onGloballyPositioned BEFORE windowInsetsPadding to measure full height including status bar
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { coordinates ->
                        topBarHeightPx = coordinates.size.height
                    }
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.background,
                                MaterialTheme.colorScheme.background,
                                Color.Transparent
                            ),
                            startY = 0f,
                            endY = Float.POSITIVE_INFINITY
                        )
                    )
            ) {
                HomeTopBar(
                    tabs = tabs,
                    selectedTabIndex = pagerState.currentPage,
                    onTabSelected = { index ->
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(index)
                        }
                    },
                    onSettingsClick = onSettingsClick,
                    modifier = Modifier.windowInsetsPadding(
                        WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
                    )
                )
            }

            // Unified FAB Overlay for Gallery and Song tabs
            if (pagerState.currentPage == 0 || pagerState.currentPage == 1) {
                val isGalleryTab = pagerState.currentPage == 0
                HomeFabOverlay(
                    isGalleryTab = isGalleryTab,
                    collapseProgress = if (isGalleryTab) galleryCollapseProgressProvider else ZeroProgress,
                    refreshVisible = isNewIdeasVisible,
                    onCreateClick = onCreateClick,
                    onRefreshClick = {
                        // Jump instantly to near the top, THEN smooth-animate the last short
                        // stretch. animateScrollToItem over a long distance composes/draws every
                        // item it passes (the whole tall grid / many song rows) → that's the
                        // stutter. The instant jump skips that; you still see a smooth pull-up for
                        // the final part. Shuffle after, at the top.
                        if (isGalleryTab) {
                            Analytics.trackIdeaTemplateClick()
                            coroutineScope.launch {
                                val gridIndex = galleryTemplatesHeaderIndex + 1
                                if (galleryListState.firstVisibleItemIndex >= gridIndex) {
                                    // Jump to just INSIDE the grid (offset kept above the
                                    // re-expand ramp so the CTA stays collapsed and doesn't snap),
                                    // then animate the short rest — the scroll-linked fold-back
                                    // still plays over the last stretch.
                                    galleryListState.scrollToItem(
                                        gridIndex,
                                        (galleryHeight.value * 0.4f).toInt()
                                    )
                                }
                                galleryListState.animateScrollToItem(0)
                                galleryViewModel.shuffle()
                            }
                        } else {
                            Analytics.trackIdeaSongClick()
                            coroutineScope.launch {
                                if (songsListState.firstVisibleItemIndex > NEAR_TOP_JUMP_INDEX) {
                                    songsListState.scrollToItem(NEAR_TOP_JUMP_INDEX)
                                }
                                songsListState.animateScrollToItem(0)
                                songsViewModel.shuffle()
                            }
                        }
                    }
                )
            }
        }

            // Ad below tab content (at bottom of screen)
            // Remote Config toggle: native ad (default) or standard banner
            if (adPlacementConfigService.bannerUseNative) {
                NativeAdView(
                    placement = AdPlacement.NATIVE_HOME_BANNER,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp),
                    isDebug = BuildConfig.DEBUG,
                    onAdClicked = { adClickDetector.onAdClick(it) }
                )
            } else {
                BannerAdView(
                    placement = AdPlacement.BANNER_HOME,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    onAdClicked = { adClickDetector.onAdClick(it) }
                )
            }
        }

        // Music player bottom sheets — rendered at HomeScreen level so they cover the
        // full screen (including the bottom NativeAdView), not just the tab content area.
        songsSelectedSong?.let { song ->
            val selectedPlaylist by songsViewModel.selectedPlaylist.collectAsStateWithLifecycle()
            val selectedCategoryLocation by songsViewModel.selectedCategoryLocation.collectAsStateWithLifecycle()
            val selectedGenreId by songsViewModel.selectedGenreId.collectAsStateWithLifecycle()
            MusicPlayerBottomSheet(
                song = song,
                playlist = selectedPlaylist,
                categoryLocation = selectedCategoryLocation,
                genreId = selectedGenreId,
                cacheDataSourceFactory = audioPreviewCache.cacheDataSourceFactory,
                isCtaVisible = isSongsCtaVisible,
                onPlayerInteraction = { isSongsCtaVisible = true },
                onDismiss = {
                    isSongsCtaVisible = true
                    songsViewModel.onDismissPlayer()
                },
                onUseToCreate = { songsViewModel.onUseToCreateVideo(song) }
            )
        }

        projectsSelectedSong?.let { song ->
            val selectedPlaylist by projectsViewModel.selectedPlaylist.collectAsStateWithLifecycle()
            MusicPlayerBottomSheet(
                song = song,
                playlist = selectedPlaylist,
                categoryLocation = AnalyticsEvent.Value.Location.SONG_FAVORITE,
                genreId = null,
                cacheDataSourceFactory = audioPreviewCache.cacheDataSourceFactory,
                isCtaVisible = isProjectsCtaVisible,
                onPlayerInteraction = { isProjectsCtaVisible = true },
                onDismiss = {
                    isProjectsCtaVisible = true
                    projectsViewModel.onDismissPlayer()
                },
                onUseToCreate = { projectsViewModel.onUseToCreateVideo(song) }
            )
        }
    }
}

/**
 * Custom Top Bar with tab titles on the left and settings on the right
 */
@Composable
private fun HomeTopBar(
    tabs: List<String>,
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    onSettingsClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val dimens = AppDimens.current
    val currentLocation = when (selectedTabIndex) {
        0 -> AnalyticsEvent.Value.Location.GALLERY
        1 -> "songs"
        else -> AnalyticsEvent.Value.Location.LIBRARY
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = dimens.spaceSm)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = dimens.spaceLg),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            // Tab Titles on the left
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.Top
            ) {
                tabs.forEachIndexed { index, title ->
                    val isSelected = index == selectedTabIndex

                    // Each tab: title + indicator stacked vertically
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clickableSingle {
                                onTabSelected(index)
                            }
                            .padding(vertical = dimens.spaceMd)
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.headlineSmall,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                TextInactive
                            }
                        )

                        Spacer(modifier = Modifier.height(dimens.spaceSm))

                        // Indicator — always present, invisible when not selected
                        Box(
                            modifier = Modifier
                                .width(32.dp)
                                .height(4.dp)
                                .background(
                                    color = if (isSelected) PrimaryDark else Color.Transparent,
                                    shape = RoundedCornerShape(2.dp)
                                )
                        )
                    }

                    if (index < tabs.lastIndex) {
                        Spacer(modifier = Modifier.width(dimens.spaceXxl))
                    }
                }
            }

            // Settings Button on the right
            IconButton(onClick = {
                Analytics.trackSettingOpen(currentLocation)
                onSettingsClick(currentLocation)
            }) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = stringResource(R.string.settings),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

// ============================================
// TAB CONTENT - Using modular screens
// ============================================

/**
 * Gallery Tab - Hot songs and suggested video templates
 */
@Composable
private fun GalleryTabContent(
    viewModel: GalleryViewModel,
    isVisible: Boolean,
    listState: LazyStaggeredGridState,
    onUserInteraction: () -> Unit,
    onCreateClick: () -> Unit,
    onNavigateToSearch: () -> Unit = {},
    onNavigateToTemplateDetail: (String, String?) -> Unit = { _, _ -> },
    onNavigateToAllTemplates: (String?) -> Unit = {},
    onNavigateToSongPreview: (MusicSong) -> Unit = {},
    topBarHeight: Dp = 0.dp
) {
    GalleryScreen(
        viewModel = viewModel,
        topBarHeight = topBarHeight,
        isVisible = isVisible,
        listState = listState,
        onUserInteraction = onUserInteraction,
        onNavigateToCreate = onCreateClick,
        onNavigateToSongDetail = {
            // TODO: Navigate to song detail
        },
        onNavigateToSongPreview = onNavigateToSongPreview,
        onNavigateToTemplateDetail = onNavigateToTemplateDetail,
        onNavigateToAllTopSongs = {
            // TODO: Navigate to all top songs
        },
        onNavigateToAllTemplates = onNavigateToAllTemplates,
        onNavigateToSearch = onNavigateToSearch
    )
}

/**
 * Songs Tab - Browse all songs
 */
@Composable
private fun SongsTabContent(
    viewModel: SongsViewModel,
    listState: LazyListState,
    onUserInteraction: () -> Unit,
    topBarHeight: Dp = 0.dp,
    isVisible: Boolean = true,
    onNavigateToSearch: () -> Unit = {},
    onNavigateToSuggestedSongsList: () -> Unit = {},
    onNavigateToWeeklyRankingList: () -> Unit = {},
    onNavigateToAssetPicker: (Long) -> Unit = {},
    onNavigateToTemplatePreviewerWithSong: (Long) -> Unit = {},
    onListScroll: () -> Unit = {}
) {
    SongsScreen(
        viewModel = viewModel,
        topBarHeight = topBarHeight,
        isVisible = isVisible,
        listState = listState,
        onUserInteraction = onUserInteraction,
        onNavigateToAssetPicker = onNavigateToAssetPicker,
        onNavigateToTemplatePreviewer = onNavigateToTemplatePreviewerWithSong,
        onNavigateToSuggestedAll = onNavigateToSuggestedSongsList,
        onNavigateToWeeklyRankingList = onNavigateToWeeklyRankingList,
        onNavigateToSearch = onNavigateToSearch,
        onListScroll = onListScroll
    )
}

// ============================================
// PREVIEW
// ============================================

@Preview(showBackground = true, name = "S1: First Access (Centered Pill)")
@Composable
private fun HomeScreenFirstAccessPreview() {
    VideoMakerTheme {
        HomeScreenPreviewContent(
            collapsed = false,
            isNewIdeasVisible = false,
            currentPage = 0
        )
    }
}

@Preview(showBackground = true, name = "S2: Collapsed FAB (+) on Right")
@Composable
private fun HomeScreenCollapsedFabPreview() {
    VideoMakerTheme {
        HomeScreenPreviewContent(
            collapsed = true,
            isNewIdeasVisible = false,
            currentPage = 0
        )
    }
}

@Preview(showBackground = true, name = "S3: New Ideas Pill (+) Expanded")
@Composable
private fun HomeScreenNewIdeasExpandedPreview() {
    VideoMakerTheme {
        HomeScreenPreviewContent(
            collapsed = true,
            isNewIdeasVisible = true,
            currentPage = 0
        )
    }
}

@Preview(showBackground = true, name = "S4: Song Tab (New Ideas Pill Only)")
@Composable
private fun HomeScreenSongTabNewIdeasPreview() {
    VideoMakerTheme {
        HomeScreenPreviewContent(
            collapsed = true,
            isNewIdeasVisible = true,
            currentPage = 1
        )
    }
}

/**
 * Preview-safe HomeScreen content that doesn't depend on ACCDI/ViewModels
 */
@Composable
private fun HomeScreenPreviewContent(
    collapsed: Boolean = false,
    isNewIdeasVisible: Boolean = false,
    currentPage: Int = 0
) {
    val tabs = listOf("Gallery", "Songs", "My Videos")

    val pagerState = rememberPagerState(
        initialPage = currentPage,
        pageCount = { tabs.size }
    )
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        HomeTopBar(
            tabs = tabs,
            selectedTabIndex = pagerState.currentPage,
            onTabSelected = { index ->
                coroutineScope.launch {
                    pagerState.animateScrollToPage(index)
                }
            },
            onSettingsClick = {}
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            // Simulated screen content
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(WelcomeBackBackground)
            ) {
                // Mock layout content to make it look like a real app screen
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Popular Templates",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        repeat(3) { index ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(180.dp)
                                    .background(
                                        PreviewCardBackground,
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .border(
                                        1.dp,
                                        Color.White.copy(alpha = 0.1f),
                                        RoundedCornerShape(12.dp)
                                    ),
                                contentAlignment = Alignment.BottomStart
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            brush = Brush.verticalGradient(
                                                colors = listOf(
                                                    Color.Transparent,
                                                    Color.Black.copy(alpha = 0.7f)
                                                )
                                            )
                                        )
                                )
                                Text(
                                    text = "Template ${index + 1}",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(8.dp)
                                )
                            }
                        }
                    }

                    Text(
                        text = "Weekly Top Hits",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )

                    repeat(2) { index ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(NewIdeasBackground, shape = RoundedCornerShape(8.dp))
                                .padding(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(
                                        PreviewButtonBackground,
                                        shape = RoundedCornerShape(8.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MusicNote,
                                    contentDescription = null,
                                    tint = Primary
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Song Title ${index + 1}", color = Color.White, fontWeight = FontWeight.SemiBold)
                                Text("Artist Name", color = Color.Gray, fontSize = 12.sp)
                            }
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = Color.White
                            )
                        }
                    }
                }
            }

            // Unified FAB Overlay — shared with the real screen for visual fidelity
            if (pagerState.currentPage == 0 || pagerState.currentPage == 1) {
                val isGalleryTab = pagerState.currentPage == 0
                HomeFabOverlay(
                    isGalleryTab = isGalleryTab,
                    collapseProgress = { if (isGalleryTab && collapsed) 1f else 0f },
                    refreshVisible = isNewIdeasVisible,
                    onCreateClick = {},
                    onRefreshClick = {}
                )
            }
        }
    }
}
