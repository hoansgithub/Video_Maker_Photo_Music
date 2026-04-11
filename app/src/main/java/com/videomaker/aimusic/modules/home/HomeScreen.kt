package com.videomaker.aimusic.modules.home

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.videomaker.aimusic.R
import com.videomaker.aimusic.core.analytics.Analytics
import com.videomaker.aimusic.core.analytics.AnalyticsEvent
import com.videomaker.aimusic.modules.gallery.GalleryScreen
import com.videomaker.aimusic.modules.gallery.GalleryViewModel
import com.videomaker.aimusic.modules.home.components.ProjectsTabContent
import com.videomaker.aimusic.modules.projects.ProjectsViewModel
import com.videomaker.aimusic.modules.songs.SongsScreen
import com.videomaker.aimusic.modules.songs.SongsViewModel
import com.videomaker.aimusic.ui.components.ModifierExtension.clickableSingle
import com.videomaker.aimusic.ui.theme.AppDimens
import com.videomaker.aimusic.ui.theme.PrimaryDark
import com.videomaker.aimusic.ui.theme.TextInactive
import com.videomaker.aimusic.ui.theme.VideoMakerTheme
import kotlinx.coroutines.launch
import co.alcheclub.lib.acccore.ads.compose.BannerAdView
import com.videomaker.aimusic.core.constants.AdPlacement
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.runtime.snapshotFlow

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
@Composable
fun HomeScreen(
    galleryViewModel: GalleryViewModel,
    songsViewModel: SongsViewModel,
    projectsViewModel: ProjectsViewModel,
    initialTab: Int = 0,
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
    onNavigateToAllSongs: () -> Unit = {}
) {
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

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { settledPage ->
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
            }
    }

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
                modifier = Modifier.fillMaxSize()
            ) { page ->
                when (page) {
                    0 -> GalleryTabContent(
                        viewModel = galleryViewModel,
                        isVisible = pagerState.settledPage == 0,
                        onCreateClick = onCreateClick,
                        onNavigateToSearch = onNavigateToSearch,
                        onNavigateToTemplateDetail = onNavigateToTemplateDetail,
                        onNavigateToAllTemplates = onNavigateToAllTemplates,
                        topBarHeight = topBarHeight
                    )
                    1 -> SongsTabContent(
                        viewModel = songsViewModel,
                        topBarHeight = topBarHeight,
                        onNavigateToSearch = onNavigateToSongSearch,
                        onNavigateToSuggestedSongsList = onNavigateToSuggestedSongsList,
                        onNavigateToWeeklyRankingList = onNavigateToWeeklyRankingList,
                        onNavigateToAssetPicker = onNavigateToAssetPicker
                    )
                    2 -> ProjectsTabContent(
                        viewModel = projectsViewModel,
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
                        topBarHeight = topBarHeight
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
        }

        // Banner ad below tab content (at bottom of screen)
        // Fixed height prevents measurement issues after multiple navigations
        BannerAdView(
            placement = AdPlacement.BANNER_HOME,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
        )
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
    topBarHeight: Dp = 0.dp,
    onNavigateToSearch: () -> Unit = {},
    onNavigateToSuggestedSongsList: () -> Unit = {},
    onNavigateToWeeklyRankingList: () -> Unit = {},
    onNavigateToAssetPicker: (Long) -> Unit = {}
) {
    SongsScreen(
        viewModel = viewModel,
        topBarHeight = topBarHeight,
        onNavigateToAssetPicker = onNavigateToAssetPicker,
        onNavigateToSuggestedAll = onNavigateToSuggestedSongsList,
        onNavigateToWeeklyRankingList = onNavigateToWeeklyRankingList,
        onNavigateToSearch = onNavigateToSearch
    )
}

// ============================================
// PREVIEW
// ============================================

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    VideoMakerTheme {
        // Use preview-safe version that doesn't require ACCDI
        HomeScreenPreviewContent()
    }
}

/**
 * Preview-safe HomeScreen content that doesn't depend on ACCDI/ViewModels
 */
@Composable
private fun HomeScreenPreviewContent() {
    val tabs = listOf("Gallery", "Songs", "My Videos")

    val pagerState = rememberPagerState(
        initialPage = 0,
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

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            // Preview placeholders - no ACCDI dependency
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(AppDimens.current.spaceLg),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${tabs[page]} Content",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
        }
    }
}
