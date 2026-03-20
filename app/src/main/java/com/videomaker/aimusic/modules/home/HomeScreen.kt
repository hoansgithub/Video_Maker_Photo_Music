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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.videomaker.aimusic.R
import com.videomaker.aimusic.modules.gallery.GalleryScreen
import com.videomaker.aimusic.modules.gallery.GalleryViewModel
import com.videomaker.aimusic.modules.songs.SongsScreen
import com.videomaker.aimusic.modules.songs.SongsViewModel
import com.videomaker.aimusic.ui.theme.AppDimens
import com.videomaker.aimusic.ui.theme.PrimaryDark
import com.videomaker.aimusic.ui.theme.TextInactive
import com.videomaker.aimusic.ui.theme.VideoMakerTheme
import kotlinx.coroutines.launch

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
    onSettingsClick: () -> Unit = {},
    onCreateClick: () -> Unit = {},
    onMyProjectsClick: () -> Unit = {},
    onProjectClick: (String) -> Unit = {},
    onNavigateToSearch: () -> Unit = {},
    onNavigateToSongSearch: () -> Unit = {},
    onNavigateToTemplateDetail: (String) -> Unit = {}
) {
    val tabs = listOf(
        stringResource(R.string.home_tab_gallery),
        stringResource(R.string.home_tab_songs),
        stringResource(R.string.home_tab_projects)
    )

    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { tabs.size }
    )
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    var topBarHeightPx by remember { mutableIntStateOf(0) }
    val topBarHeight = with(density) { topBarHeightPx.toDp() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        // Pager Content — full-bleed so each page can draw its own background
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            when (page) {
                0 -> GalleryTabContent(
                    viewModel = galleryViewModel,
                    onCreateClick = onCreateClick,
                    onNavigateToSearch = onNavigateToSearch,
                    onNavigateToTemplateDetail = onNavigateToTemplateDetail,
                    topBarHeight = topBarHeight
                )
                1 -> SongsTabContent(
                    viewModel = songsViewModel,
                    topBarHeight = topBarHeight,
                    onNavigateToSearch = onNavigateToSongSearch
                )
                2 -> ProjectsTabContent(
                    onCreateClick = onCreateClick,
                    onProjectClick = onProjectClick,
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
}

/**
 * Custom Top Bar with tab titles on the left and settings on the right
 */
@Composable
private fun HomeTopBar(
    tabs: List<String>,
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dimens = AppDimens.current

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
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.Top
            ) {
                tabs.forEachIndexed { index, title ->
                    val isSelected = index == selectedTabIndex

                    // Each tab: title + indicator stacked vertically
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
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
            IconButton(onClick = onSettingsClick) {
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
    onCreateClick: () -> Unit,
    onNavigateToSearch: () -> Unit = {},
    onNavigateToTemplateDetail: (String) -> Unit = {},
    topBarHeight: Dp = 0.dp
) {
    GalleryScreen(
        viewModel = viewModel,
        topBarHeight = topBarHeight,
        onNavigateToCreate = onCreateClick,
        onNavigateToSongDetail = {
            // TODO: Navigate to song detail
        },
        onNavigateToTemplateDetail = onNavigateToTemplateDetail,
        onNavigateToAllTopSongs = {
            // TODO: Navigate to all top songs
        },
        onNavigateToAllTemplates = {
            // TODO: Navigate to all templates
        },
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
    onNavigateToSearch: () -> Unit = {}
) {
    SongsScreen(
        viewModel = viewModel,
        topBarHeight = topBarHeight,
        onNavigateToSongDetail = { songId ->
            // TODO: Navigate to song detail
        },
        onNavigateToSearch = onNavigateToSearch
    )
}

/**
 * Projects Tab - User's saved projects with create button
 */
@Composable
private fun ProjectsTabContent(
    onCreateClick: () -> Unit,
    onProjectClick: (String) -> Unit,
    topBarHeight: Dp = 0.dp
) {
    val dimens = AppDimens.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = topBarHeight)
            .padding(dimens.spaceLg)
    ) {
        // Create New Project Card
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(dimens.radiusLg)
                )
                .clickable(onClick = onCreateClick)
                .padding(dimens.spaceLg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(28.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "+",
                    fontSize = dimens.font3Xl,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }

            Spacer(modifier = Modifier.width(dimens.spaceLg))

            Column {
                Text(
                    text = stringResource(R.string.home_create),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = stringResource(R.string.projects_create_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
            }
        }

        Spacer(modifier = Modifier.height(dimens.spaceXxl))

        // Empty state message
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = stringResource(R.string.projects_empty),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(dimens.spaceSm))
                Text(
                    text = stringResource(R.string.projects_empty_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
    }
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
