package com.videomaker.aimusic.modules.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.draw.shadow
import com.videomaker.aimusic.ui.components.StaggeredGrid
import coil.request.ImageRequest
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.videomaker.aimusic.ui.theme.Primary
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.videomaker.aimusic.R
import com.videomaker.aimusic.domain.model.Project
import com.videomaker.aimusic.modules.gallery.GalleryScreen
import com.videomaker.aimusic.modules.gallery.GalleryViewModel
import com.videomaker.aimusic.modules.projects.ProjectsUiState
import com.videomaker.aimusic.modules.projects.ProjectsViewModel
import com.videomaker.aimusic.modules.songs.SongsScreen
import com.videomaker.aimusic.modules.songs.SongsViewModel
import com.videomaker.aimusic.ui.theme.AppDimens
import com.videomaker.aimusic.ui.theme.PrimaryDark
import com.videomaker.aimusic.ui.theme.TextBright
import com.videomaker.aimusic.ui.theme.TextInactive
import com.videomaker.aimusic.ui.theme.TextSecondary
import com.videomaker.aimusic.ui.theme.VideoMakerTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    onSettingsClick: () -> Unit = {},
    onCreateClick: () -> Unit = {},
    onProjectClick: (String) -> Unit = {},
    onNavigateToSearch: () -> Unit = {},
    onNavigateToSongSearch: () -> Unit = {},
    onNavigateToTemplateDetail: (String) -> Unit = {},
    onNavigateToAssetPicker: (songId: Long) -> Unit = {}
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
                    onNavigateToSearch = onNavigateToSongSearch,
                    onNavigateToAssetPicker = onNavigateToAssetPicker
                )
                2 -> ProjectsTabContent(
                    viewModel = projectsViewModel,
                    onCreateClick = { onNavigateToTemplateDetail("") }, // Open template previewer with first template
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
    onNavigateToSearch: () -> Unit = {},
    onNavigateToAssetPicker: (Long) -> Unit = {}
) {
    SongsScreen(
        viewModel = viewModel,
        topBarHeight = topBarHeight,
        onNavigateToAssetPicker = onNavigateToAssetPicker,
        onNavigateToSearch = onNavigateToSearch
    )
}

/**
 * Projects Tab - User's saved projects with create button
 */
@Composable
private fun ProjectsTabContent(
    viewModel: ProjectsViewModel,
    onCreateClick: () -> Unit,
    onProjectClick: (String) -> Unit,
    topBarHeight: Dp = 0.dp
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val dimens = AppDimens.current

    // Start loading projects only when this tab appears
    LaunchedEffect(Unit) {
        viewModel.startObservingProjects()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Background image — edge-to-edge, behind everything
        Image(
            painter = painterResource(id = R.drawable.bg_projects),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // Content with top padding
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = topBarHeight)
        ) {
            when (val state = uiState) {
                is ProjectsUiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                is ProjectsUiState.Empty -> {
                    ProjectsEmptyState(onCreateClick = onCreateClick)
                }
                is ProjectsUiState.Success -> {
                    ProjectsListContent(
                        projects = state.projects,
                        onProjectClick = { project ->
                            onProjectClick(project.id)
                        },
                        onDeleteProject = { project ->
                            viewModel.onDeleteProject(project)
                        },
                        onCreateClick = onCreateClick
                    )
                }
                is ProjectsUiState.Error -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = state.message,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

/**
 * Empty state with icon, title, subtitle, and button
 */
@Composable
private fun ProjectsEmptyState(
    onCreateClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Empty box icon
            Image(
                painter = painterResource(id = R.drawable.ic_empty_box),
                contentDescription = null,
                modifier = Modifier.size(120.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Title
            Text(
                text = stringResource(R.string.projects_empty_title),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Subtitle
            Text(
                text = stringResource(R.string.projects_empty_subtitle),
                fontSize = 15.sp,
                fontWeight = FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
                lineHeight = 22.sp
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Create New Video Button
            Button(
                onClick = onCreateClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text(
                    text = stringResource(R.string.projects_create_new_video),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }
}

/**
 * Projects list with staggered grid layout and floating create button
 */
@Composable
private fun ProjectsListContent(
    projects: List<Project>,
    onProjectClick: (Project) -> Unit,
    onDeleteProject: (Project) -> Unit,
    onCreateClick: () -> Unit
) {
    val dimens = AppDimens.current

    Box(modifier = Modifier.fillMaxSize()) {
        // Scrollable staggered grid
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = dimens.spaceLg,
                end = dimens.spaceLg,
                top = dimens.spaceLg,
                bottom = dimens.space3Xl + dimens.space2Xl // Extra padding for FAB
            )
        ) {
            item(key = "project_grid", contentType = "grid") {
                ProjectsStaggeredGrid(
                    projects = projects,
                    onProjectClick = onProjectClick,
                    onDeleteProject = onDeleteProject,
                    spacing = dimens.spaceSm
                )
            }
        }

        // Bottom gradient fade — dark to transparent, behind the FAB
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(120.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            MaterialTheme.colorScheme.background
                        )
                    )
                )
        )

        // Circular Floating Action Button — bottom right with shadow
        CreateProjectFloatingButton(
            onClick = onCreateClick,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = dimens.spaceLg, end = dimens.spaceLg)
        )
    }
}

/**
 * Staggered grid for projects based on aspect ratio
 */
@Composable
private fun ProjectsStaggeredGrid(
    projects: List<Project>,
    onProjectClick: (Project) -> Unit,
    onDeleteProject: (Project) -> Unit,
    spacing: Dp
) {
    if (projects.isEmpty()) return

    // Calculate aspect ratios from project settings
    val aspectRatios = projects.map { project ->
        project.settings.aspectRatio.ratio
    }

    StaggeredGrid(
        items = projects,
        aspectRatios = aspectRatios,
        columns = 2,
        spacing = spacing,
        key = { project -> project.id }
    ) { project ->
        ProjectGridCard(
            project = project,
            onClick = { onProjectClick(project) },
            onDelete = { onDeleteProject(project) }
        )
    }
}

/**
 * Circular Floating Action Button for creating new projects
 */
@Composable
private fun CreateProjectFloatingButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(64.dp)
            .shadow(
                elevation = 12.dp,
                shape = CircleShape,
                clip = false
            )
            .clip(CircleShape)
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Primary,
                        Primary.copy(alpha = 0.9f)
                    )
                )
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_circle_plus),
            contentDescription = "Create New Project",
            modifier = Modifier.size(32.dp),
            tint = Color.Unspecified
        )
    }
}

/**
 * Project card for grid layout (staggered)
 */
@Composable
private fun ProjectGridCard(
    project: Project,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val dimens = AppDimens.current
    val coroutineScope = rememberCoroutineScope()
    var showMenu by remember { mutableStateOf(false) }
    var isVisible by remember { mutableStateOf(true) }

    AnimatedVisibility(
        visible = isVisible,
        exit = shrinkVertically() + fadeOut()
    ) {
        Box {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(dimens.radiusMd))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    .clickable(onClick = onClick)
            ) {
        // Thumbnail with aspect ratio
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(project.settings.aspectRatio.ratio)
                .clip(RoundedCornerShape(topStart = dimens.radiusMd, topEnd = dimens.radiusMd))
                .background(Color.Black.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            if (project.thumbnailUri != null) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(project.thumbnailUri)
                        .crossfade(true)
                        .build(),
                    contentDescription = project.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else if (project.assets.isNotEmpty()) {
                // Use first asset as thumbnail
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(project.assets.first().uri)
                        .crossfade(true)
                        .build(),
                    contentDescription = project.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    imageVector = Icons.Outlined.Folder,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                )
            }

            // Duration badge at bottom-right
            if (project.assets.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(dimens.spaceXs)
                        .background(
                            color = Color.Black.copy(alpha = 0.7f),
                            shape = RoundedCornerShape(dimens.radiusSm)
                        )
                        .padding(horizontal = dimens.spaceXs, vertical = dimens.spaceXxs)
                ) {
                    Text(
                        text = project.formattedDuration,
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        color = Color.White
                    )
                }
            }

            // Creation time badge at bottom-left
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(dimens.spaceXs)
                    .background(
                        color = Color.Black.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(dimens.radiusSm)
                    )
                    .padding(horizontal = dimens.spaceXs, vertical = dimens.spaceXxs)
            ) {
                Text(
                    text = formatProjectDate(project.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    color = Color.White
                )
            }

            // Play icon at center
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = null,
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        color = Color.Black.copy(alpha = 0.5f),
                        shape = CircleShape
                    )
                    .padding(8.dp),
                tint = Color.White
            )
        }

                // Project Info
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(dimens.spaceSm)
                ) {
                    Text(
                        text = project.name,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(dimens.spaceXxs))
                    Text(
                        text = stringResource(
                            R.string.projects_item_info,
                            project.assets.size,
                            project.formattedDuration
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }

            // Menu button (top right) - positioned absolutely with circular dark background
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(dimens.spaceXs)
                    .size(28.dp)
                    .background(
                        color = Color.Black.copy(alpha = 0.6f),
                        shape = CircleShape
                    )
                    .clickable { showMenu = true },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = stringResource(R.string.projects_menu),
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )

                // Dropdown menu
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.projects_delete)) },
                        onClick = {
                            showMenu = false
                            isVisible = false
                            // Delay actual deletion to allow animation to complete
                            coroutineScope.launch {
                                delay(300) // Animation duration
                                onDelete()
                            }
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    )
                }
            }
        }
    }
}

private val projectDateFormatter = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())

private fun formatProjectDate(timestamp: Long): String = projectDateFormatter.format(Date(timestamp))

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
