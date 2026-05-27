package com.videomaker.aimusic.modules.home

import android.Manifest
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.foundation.border
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.ui.res.painterResource
import com.videomaker.aimusic.ui.theme.Primary
import com.videomaker.aimusic.ui.components.PrimaryButton
import com.videomaker.aimusic.modules.gallery.CreateNewVideoButton
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import com.videomaker.aimusic.R
import com.videomaker.aimusic.core.analytics.Analytics
import com.videomaker.aimusic.core.analytics.AnalyticsEvent
import com.videomaker.aimusic.core.permission.NotificationPermissionCoordinator
import com.videomaker.aimusic.media.audio.AudioPreviewCache
import com.videomaker.aimusic.modules.gallery.GalleryScreen
import com.videomaker.aimusic.modules.gallery.GalleryViewModel
import com.videomaker.aimusic.modules.gallery.GalleryUiState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.videomaker.aimusic.modules.home.components.ProjectsTabContent
import com.videomaker.aimusic.modules.projects.ProjectsViewModel
import com.videomaker.aimusic.modules.songs.MusicPlayerBottomSheet
import com.videomaker.aimusic.modules.songs.SongsScreen
import com.videomaker.aimusic.modules.songs.SongsViewModel
import com.videomaker.aimusic.ui.components.ModifierExtension.clickableSingle
import com.videomaker.aimusic.ui.theme.AppDimens
import com.videomaker.aimusic.ui.theme.PrimaryDark
import com.videomaker.aimusic.ui.theme.TextInactive
import com.videomaker.aimusic.ui.theme.NewIdeasBackground
import com.videomaker.aimusic.ui.theme.PreviewCardBackground
import com.videomaker.aimusic.ui.theme.PreviewButtonBackground
import com.videomaker.aimusic.ui.theme.WelcomeBackBackground
import com.videomaker.aimusic.ui.theme.VideoMakerTheme
import kotlinx.coroutines.launch
import co.alcheclub.lib.acccore.ads.compose.NativeAdView
import co.alcheclub.lib.acccore.ads.loader.AdsLoaderException
import co.alcheclub.lib.acccore.ads.loader.AdsLoaderService
import com.videomaker.aimusic.core.constants.AdPlacement
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.runtime.snapshotFlow
import com.videomaker.aimusic.BuildConfig
import org.koin.compose.koinInject
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow

object HomeFabStateManager {
    var hasCompletedFirstAccess = false
}

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
    onProjectClick: (String) -> Unit = {},
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
    val context = LocalContext.current
    val notificationPermissionCoordinator = koinInject<NotificationPermissionCoordinator>()
    val adsLoaderService = koinInject<AdsLoaderService>()

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

    val galleryListState = rememberLazyListState()
    val songsListState = rememberLazyListState()

    val galleryUiState by galleryViewModel.uiState.collectAsStateWithLifecycle()
    val galleryTemplatesHeaderIndex = remember(galleryUiState) {
        val successState = galleryUiState as? GalleryUiState.Success
        val featuredTemplates = successState?.featuredTemplates ?: emptyList()
        if (featuredTemplates.isNotEmpty()) 4 else 2
    }

    var hasCompletedFirstAccess by rememberSaveable { mutableStateOf(HomeFabStateManager.hasCompletedFirstAccess) }

    var lastInteractionScrollPositionGallery by rememberSaveable { mutableIntStateOf(0) }
    var lastInteractionScrollPositionSongs by rememberSaveable { mutableIntStateOf(0) }

    val currentGalleryScroll by remember(galleryTemplatesHeaderIndex) {
        derivedStateOf {
            val targetIndex = galleryTemplatesHeaderIndex + 1
            val visibleItems = galleryListState.layoutInfo.visibleItemsInfo
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

    val currentSongsScroll by remember {
        derivedStateOf {
            val targetIndex = 8
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

    // Collapse FAB on scroll down
    LaunchedEffect(currentGalleryScroll) {
        if (currentGalleryScroll > 0 && !hasCompletedFirstAccess) {
            hasCompletedFirstAccess = true
            HomeFabStateManager.hasCompletedFirstAccess = true
        }
    }

    LaunchedEffect(currentSongsScroll) {
        if (currentSongsScroll > 0 && !hasCompletedFirstAccess) {
            hasCompletedFirstAccess = true
            HomeFabStateManager.hasCompletedFirstAccess = true
        }
    }

    var isNewIdeasClickedGallery by rememberSaveable { mutableStateOf(false) }
    var isNewIdeasClickedSongs by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(galleryListState.firstVisibleItemIndex, galleryListState.firstVisibleItemScrollOffset) {
        if (galleryListState.firstVisibleItemIndex <= galleryTemplatesHeaderIndex) {
            isNewIdeasClickedGallery = false
            lastInteractionScrollPositionGallery = 0
        }
    }

    LaunchedEffect(songsListState.firstVisibleItemIndex, songsListState.firstVisibleItemScrollOffset) {
        if (songsListState.firstVisibleItemIndex <= 7) {
            isNewIdeasClickedSongs = false
            lastInteractionScrollPositionSongs = 0
        }
    }

    val isNewIdeasVisible by remember {
        derivedStateOf {
            when (pagerState.currentPage) {
                0 -> !isNewIdeasClickedGallery && currentGalleryScroll > 0 && (currentGalleryScroll - lastInteractionScrollPositionGallery >= 3 * galleryHeight.value)
                1 -> !isNewIdeasClickedSongs && currentSongsScroll > 0 && (currentSongsScroll - lastInteractionScrollPositionSongs >= 3 * songsHeight.value)
                else -> false
            }
        }
    }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { settledPage ->
                songsViewModel.onDismissPlayer()
                projectsViewModel.onDismissPlayer()
                val currentTab = tabNameByIndex(settledPage)
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
                    0 -> galleryViewModel.onTabFocused()
                    1 -> songsViewModel.onTabFocused()
                    else -> Unit
                }
            }
    }

    // Player overlay state lifted here so MusicPlayerBottomSheet can render full-screen
    // above both the bottom NativeAdView and the tab content.
    val audioPreviewCache: AudioPreviewCache = koinInject()
    val songsSelectedSong by songsViewModel.selectedSong.collectAsStateWithLifecycle()
    val projectsSelectedSong by projectsViewModel.selectedSong.collectAsStateWithLifecycle()
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
                .windowInsetsPadding(WindowInsets.navigationBars)
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
                            onUserInteraction = {
                                lastInteractionScrollPositionGallery = currentGalleryScroll
                            },
                            onCreateClick = onCreateClick,
                            onNavigateToSearch = onNavigateToSearch,
                            onNavigateToTemplateDetail = onNavigateToTemplateDetail,
                            onNavigateToAllTemplates = onNavigateToAllTemplates,
                            topBarHeight = topBarHeight
                        )
                        1 -> SongsTabContent(
                            viewModel = songsViewModel,
                            listState = songsListState,
                            onUserInteraction = {
                                lastInteractionScrollPositionSongs = currentSongsScroll
                            },
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
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 16.dp, start = 16.dp, end = 16.dp),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    if (pagerState.currentPage == 0) {
                        // Gallery Tab: S1 pill, S2 collapsed FAB, S3 FAB + New Ideas pill
                        if (!hasCompletedFirstAccess) {
                            // S1: Centered bottom expanded pill
                            CreateNewVideoButton(
                                onClick = {
                                    hasCompletedFirstAccess = true
                                    HomeFabStateManager.hasCompletedFirstAccess = true
                                    onCreateClick()
                                },
                                modifier = Modifier.align(Alignment.BottomCenter)
                            )
                        } else {
                            // S2 & S3: Right edge icon only (+) FAB + "New Ideas ✨" pill
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .align(Alignment.BottomEnd),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AnimatedVisibility(
                                    visible = isNewIdeasVisible,
                                    enter = fadeIn() + expandHorizontally(),
                                    exit = fadeOut() + shrinkHorizontally(),
                                    modifier = Modifier.then(
                                        if (isNewIdeasVisible) Modifier.weight(1f) else Modifier
                                    )
                                ) {
                                    // Custom "New Ideas ✨" pill button matching the mockup UI
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(end = 12.dp)
                                            .height(52.dp)
                                            .background(
                                                color = NewIdeasBackground,
                                                shape = RoundedCornerShape(26.dp)
                                            )
                                            .border(
                                                width = 1.5.dp,
                                                color = Primary,
                                                shape = RoundedCornerShape(26.dp)
                                            )
                                            .clickable {
                                                isNewIdeasClickedGallery = true
                                                coroutineScope.launch {
                                                    galleryListState.animateScrollToItem(
                                                        galleryTemplatesHeaderIndex
                                                    )
                                                }
                                                galleryViewModel.shuffle()
                                            }
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(start = 20.dp, end = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = "New Ideas✨",
                                                color = Color.White,
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.Bold
                                            )

                                            // Circular white refresh button inside the pill
                                            Icon(
                                                painter = painterResource(id = R.drawable.ic_refresh),
                                                contentDescription = "Refresh",
                                                tint = Color.Unspecified,
                                                modifier = Modifier.size(38.dp)
                                            )
                                        }
                                    }
                                }

                                // Circular (+) FAB
                                androidx.compose.material3.FloatingActionButton(
                                    onClick = {
                                        onCreateClick()
                                    },
                                    shape = CircleShape,
                                    containerColor = Primary,
                                    contentColor = Color.Black,
                                    modifier = Modifier.size(52.dp)
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_circle_plus),
                                        contentDescription = "Create",
                                        tint = Color.Unspecified,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }
                    } else {
                        // Songs Tab: Only show "New Ideas ✨" pill button when visible, aligned to bottom center. No add/FAB button at all.
                        androidx.compose.animation.AnimatedVisibility(
                            visible = isNewIdeasVisible,
                            enter = fadeIn() + expandHorizontally(),
                            exit = fadeOut() + shrinkHorizontally(),
                            modifier = Modifier.align(Alignment.BottomCenter)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.7f)
                                    .height(52.dp)
                                    .background(
                                        color = NewIdeasBackground,
                                        shape = RoundedCornerShape(26.dp)
                                    )
                                    .border(
                                        width = 1.5.dp,
                                        color = Primary,
                                        shape = RoundedCornerShape(26.dp)
                                    )
                                    .clickable {
                                        isNewIdeasClickedSongs = true
                                        coroutineScope.launch {
                                            songsListState.animateScrollToItem(6)
                                        }
                                        songsViewModel.shuffle()
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(start = 20.dp, end = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "New Ideas✨",
                                        color = Color.White,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold
                                    )

                                    // Circular white refresh button inside the pill
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_refresh),
                                        contentDescription = "Refresh",
                                        tint = Color.Unspecified,
                                        modifier = Modifier.size(38.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

            // Native ad below tab content (at bottom of screen)
            // Fixed height prevents measurement issues after multiple navigations
            // Replaced standard banner with native ad as requested
            NativeAdView(
                placement = AdPlacement.NATIVE_HOME_BANNER,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                isDebug = BuildConfig.DEBUG
            )
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
    listState: LazyListState,
    onUserInteraction: () -> Unit,
    onCreateClick: () -> Unit,
    onNavigateToSearch: () -> Unit = {},
    onNavigateToTemplateDetail: (String, String?) -> Unit = { _, _ -> },
    onNavigateToAllTemplates: (String?) -> Unit = {},
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
            hasCompletedFirstAccess = false,
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
            hasCompletedFirstAccess = true,
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
            hasCompletedFirstAccess = true,
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
            hasCompletedFirstAccess = true,
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
    hasCompletedFirstAccess: Boolean = false,
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

            // Unified FAB Overlay - copied exactly from real implementation to ensure visual fidelity
            if (pagerState.currentPage == 0 || pagerState.currentPage == 1) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 16.dp, start = 16.dp, end = 16.dp),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    if (pagerState.currentPage == 0) {
                        if (!hasCompletedFirstAccess) {
                            // S1: Centered bottom expanded pill
                            CreateNewVideoButton(
                                onClick = {},
                                modifier = Modifier.align(Alignment.BottomCenter)
                            )
                        } else {
                            // S2 & S3: Right edge icon only (+) FAB + "New Ideas ✨" pill
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .align(Alignment.BottomEnd),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AnimatedVisibility(
                                    visible = isNewIdeasVisible,
                                    enter = fadeIn() + expandHorizontally(),
                                    exit = fadeOut() + shrinkHorizontally(),
                                    modifier = Modifier.then(
                                        if (isNewIdeasVisible) Modifier.weight(1f) else Modifier
                                    )
                                ) {
                                    // Custom "New Ideas ✨" pill button matching the mockup UI
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(end = 12.dp)
                                            .height(52.dp)
                                            .background(
                                                color = NewIdeasBackground,
                                                shape = RoundedCornerShape(26.dp)
                                            )
                                            .border(
                                                width = 1.5.dp,
                                                color = Primary,
                                                shape = RoundedCornerShape(26.dp)
                                            )
                                            .clickable { }
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(start = 20.dp, end = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = "New Ideas✨",
                                                color = Color.White,
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.Bold
                                            )

                                            // Circular white refresh button inside the pill
                                            Icon(
                                                painter = painterResource(id = R.drawable.ic_refresh),
                                                contentDescription = "Refresh",
                                                tint = Color.Unspecified,
                                                modifier = Modifier.size(38.dp)
                                            )
                                        }
                                    }
                                }

                                // Circular (+) FAB
                                androidx.compose.material3.FloatingActionButton(
                                    onClick = {},
                                    shape = CircleShape,
                                    containerColor = Primary,
                                    contentColor = Color.Black,
                                    modifier = Modifier.size(52.dp)
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_circle_plus),
                                        contentDescription = "Create",
                                        tint = Color.Unspecified,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }
                    } else {
                        // Songs Tab: Only show "New Ideas ✨" pill button when visible, aligned to bottom center. No FAB.
                        androidx.compose.animation.AnimatedVisibility(
                            visible = isNewIdeasVisible,
                            enter = fadeIn() + expandHorizontally(),
                            exit = fadeOut() + shrinkHorizontally(),
                            modifier = Modifier.align(Alignment.BottomCenter)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.7f)
                                    .height(52.dp)
                                    .background(
                                        color = NewIdeasBackground,
                                        shape = RoundedCornerShape(26.dp)
                                    )
                                    .border(
                                        width = 1.5.dp,
                                        color = Primary,
                                        shape = RoundedCornerShape(26.dp)
                                    )
                                    .clickable { }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(start = 20.dp, end = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "New Ideas✨",
                                        color = Color.White,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold
                                    )

                                    // Circular white refresh button inside the pill
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_refresh),
                                        contentDescription = "Refresh",
                                        tint = Color.Unspecified,
                                        modifier = Modifier.size(38.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
