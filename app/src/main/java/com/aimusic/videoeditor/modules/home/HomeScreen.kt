package com.aimusic.videoeditor.modules.home

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aimusic.videoeditor.R
import com.aimusic.videoeditor.modules.gallery.GalleryScreen
import com.aimusic.videoeditor.modules.songs.SongsScreen
import com.aimusic.videoeditor.ui.theme.VideoMakerTheme
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
    onSettingsClick: () -> Unit = {},
    onCreateClick: () -> Unit = {},
    onMyProjectsClick: () -> Unit = {},
    onProjectClick: (String) -> Unit = {}
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        // Top Bar with Tabs and Settings
        HomeTopBar(
            tabs = tabs,
            selectedTabIndex = pagerState.currentPage,
            onTabSelected = { index ->
                coroutineScope.launch {
                    pagerState.animateScrollToPage(index)
                }
            },
            onSettingsClick = onSettingsClick
        )

        // Pager Content
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            when (page) {
                0 -> GalleryTabContent(onCreateClick = onCreateClick)
                1 -> SongsTabContent()
                2 -> ProjectsTabContent(
                    onCreateClick = onCreateClick,
                    onProjectClick = onProjectClick
                )
            }
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
    onSettingsClick: () -> Unit
) {
    val density = LocalDensity.current

    // Track tab positions and widths for indicator animation
    val tabPositions = remember { mutableMapOf<Int, Pair<Dp, Dp>>() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Tab Titles on the left
            Row(
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                tabs.forEachIndexed { index, title ->
                    val isSelected = index == selectedTabIndex

                    Box(
                        modifier = Modifier
                            .onGloballyPositioned { coordinates ->
                                val position = with(density) {
                                    coordinates.positionInParent().x.toDp()
                                }
                                val width = with(density) {
                                    coordinates.size.width.toDp()
                                }
                                tabPositions[index] = Pair(position, width)
                            }
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                onTabSelected(index)
                            }
                            .padding(vertical = 12.dp)
                    ) {
                        Text(
                            text = title,
                            fontSize = if (isSelected) 20.sp else 16.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            }
                        )
                    }

                    if (index < tabs.lastIndex) {
                        Spacer(modifier = Modifier.width(24.dp))
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

        // White Indicator Bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            // Animate indicator position and width
            val indicatorOffset by animateDpAsState(
                targetValue = tabPositions[selectedTabIndex]?.first ?: 0.dp,
                animationSpec = tween(durationMillis = 250),
                label = "indicatorOffset"
            )
            val indicatorWidth by animateDpAsState(
                targetValue = tabPositions[selectedTabIndex]?.second ?: 0.dp,
                animationSpec = tween(durationMillis = 250),
                label = "indicatorWidth"
            )

            Box(
                modifier = Modifier
                    .offset(x = indicatorOffset)
                    .width(indicatorWidth)
                    .height(3.dp)
                    .background(
                        color = Color.White,
                        shape = RoundedCornerShape(1.5.dp)
                    )
            )
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
    onCreateClick: () -> Unit
) {
    GalleryScreen(
        onNavigateToCreate = onCreateClick,
        onNavigateToSongDetail = { songId ->
            // TODO: Navigate to song detail
        },
        onNavigateToTemplateDetail = { templateId ->
            // TODO: Navigate to template detail
        },
        onNavigateToAllTopSongs = {
            // TODO: Navigate to all top songs
        },
        onNavigateToAllTrendingTemplates = {
            // TODO: Navigate to all trending templates
        },
        onNavigateToAllPopularTemplates = {
            // TODO: Navigate to all popular templates
        }
    )
}

/**
 * Songs Tab - Browse all songs
 */
@Composable
private fun SongsTabContent() {
    SongsScreen(
        onNavigateToSongDetail = { songId ->
            // TODO: Navigate to song detail
        }
    )
}

/**
 * Projects Tab - User's saved projects
 * Note: Full ProjectsScreen with DI is accessed via AppRoute.Projects
 * This is a simplified tab placeholder that shows empty state
 */
@Composable
private fun ProjectsTabContent(
    onCreateClick: () -> Unit,
    onProjectClick: (String) -> Unit
) {
    // Simple placeholder for the tab - full screen accessed via navigation
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = stringResource(R.string.home_tab_projects),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.gallery_coming_soon),
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
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
        HomeScreen()
    }
}
