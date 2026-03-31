package com.videomaker.aimusic.modules.home.components

import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.videomaker.aimusic.R
import com.videomaker.aimusic.domain.model.Project
import com.videomaker.aimusic.media.audio.AudioPreviewCache
import com.videomaker.aimusic.modules.favourite_songs.ContentSong
import com.videomaker.aimusic.modules.favourite_songs.LikeSongEmpty
import com.videomaker.aimusic.modules.favourite_templates.ContentTemplate
import com.videomaker.aimusic.modules.favourite_templates.LikeTemplateEmpty
import com.videomaker.aimusic.modules.projects.ProjectsNavigationEvent
import com.videomaker.aimusic.modules.projects.ProjectsUiState
import com.videomaker.aimusic.modules.projects.ProjectsViewModel
import com.videomaker.aimusic.modules.projects.SongTabState
import com.videomaker.aimusic.modules.projects.TemplateTabState
import com.videomaker.aimusic.modules.songs.MusicPlayerBottomSheet
import com.videomaker.aimusic.ui.components.SongListItem
import com.videomaker.aimusic.ui.components.StaggeredGrid
import com.videomaker.aimusic.ui.components.TemplateCard
import com.videomaker.aimusic.ui.theme.AppDimens
import com.videomaker.aimusic.ui.theme.Neutral_Black
import com.videomaker.aimusic.ui.theme.Primary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(UnstableApi::class)
@Composable
fun ProjectsTabContent(
    viewModel: ProjectsViewModel,
    onCreateClick: () -> Unit,
    onProjectClick: (String) -> Unit,
    onNavigateToTemplateDetail: (String) -> Unit = {},
    onNavigateToSongSearch: () -> Unit = {},
    onNavigateToAllSongs: () -> Unit = {},
    onNavigateToTemplateSearch: () -> Unit = {},
    onNavigateToAllTemplates: () -> Unit = {},
    onNavigateToAssetPicker: (songId: Long) -> Unit = {},
    topBarHeight: Dp = 0.dp
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val templateState by viewModel.templateState.collectAsStateWithLifecycle()
    val templateStateLocal by viewModel.templateStateLocal.collectAsStateWithLifecycle()
    val songState by viewModel.songState.collectAsStateWithLifecycle()
    val songStateLocal by viewModel.songStateLocal.collectAsStateWithLifecycle()
    val navigationEvent by viewModel.navigationEvent.collectAsStateWithLifecycle()
    val selectedSong by viewModel.selectedSong.collectAsStateWithLifecycle()
    val audioPreviewCache: AudioPreviewCache = koinInject()
    var showRemovedMessage by remember { mutableStateOf(false) }

    LaunchedEffect(showRemovedMessage) {
        if (showRemovedMessage) {
            delay(2000)
            showRemovedMessage = false
        }
    }

    LaunchedEffect(navigationEvent) {
        navigationEvent?.let { event ->
            when (event) {
                is ProjectsNavigationEvent.NavigateToSongSearch -> onNavigateToSongSearch()
                is ProjectsNavigationEvent.NavigateToAllSongs -> onNavigateToAllSongs()
                is ProjectsNavigationEvent.NavigateToTemplateSearch -> onNavigateToTemplateSearch()
                is ProjectsNavigationEvent.NavigateToAllTemplates -> onNavigateToAllTemplates()
                is ProjectsNavigationEvent.NavigateToAssetPickerForSong -> onNavigateToAssetPicker(event.songId)
                is ProjectsNavigationEvent.NavigateToEditor -> { /* handled by caller */ }
                is ProjectsNavigationEvent.NavigateToTemplateDetail -> { /* handled by caller */ }
                is ProjectsNavigationEvent.NavigateBack -> { /* handled by caller */ }
            }
            viewModel.onNavigationHandled()
        }
    }

    val tabs = listOf(
        stringResource(R.string.projects_tab_created_video),
        stringResource(R.string.projects_tab_liked_template),
        stringResource(R.string.projects_tab_liked_song)
    )

    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { tabs.size }
    )
    val coroutineScope = rememberCoroutineScope()
    val lazyListState = rememberLazyListState()

    // Start loading projects only when this tab appears
    LaunchedEffect(Unit) {
        viewModel.startObservingProjects()
    }

    // Animate LazyRow to selected tab when pager settles, and notify VM for swipe case
    LaunchedEffect(pagerState.settledPage) {
        lazyListState.animateScrollToItem(pagerState.settledPage)
        viewModel.onTabSelected(pagerState.settledPage)
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
        Column(
            modifier = Modifier
                .padding(top = topBarHeight + 16.dp)
                .fillMaxSize()
        ) {
            ProjectTabRow(
                tabs = tabs,
                state = lazyListState,
                currentPage = pagerState.currentPage,
                onClick = { index ->
                    coroutineScope.launch {
                        pagerState.scrollToPage(index)
                    }
                    viewModel.onTabSelected(index)
                }
            )
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                ) {
                    when (page) {
                        0 -> when (val state = uiState) {
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

                        1 -> {
                            if (templateStateLocal.isNotEmpty()){
                                ContentTemplate(
                                    state = templateStateLocal,
                                    onTemplateClick = onNavigateToTemplateDetail,
                                    onDeleteTemplateClick = {
                                        viewModel.onUnlikeTemplate(it)
                                        showRemovedMessage = true
                                    }
                                )
                            } else {
                                LikeTemplateEmpty(
                                    state = templateState,
                                    onTemplateClick = onNavigateToTemplateDetail,
                                    onSeeAllClick = viewModel::onSeeAllTemplates,
                                    onSearch = viewModel::onTemplateSearch
                                )
                            }
                        }

                        2 -> {
                            if (songStateLocal.isNotEmpty()){
                                ContentSong(
                                    songs = songStateLocal,
                                    onSongClick = viewModel::onSongClick,
                                    onDeleteSongClick = {
                                        viewModel.onUnlikeSong(it)
                                        showRemovedMessage = true
                                    }
                                )
                            } else {
                                LikeSongEmpty(
                                    state = songState,
                                    onSeeAllClick = viewModel::onSeeAllSongs,
                                    onSearch = viewModel::onSongSearch,
                                    onSongClick = viewModel::onSongClick
                                )
                            }
                        }

                        else -> Unit
                    }
                }
            }
        }

        // Removed-from-list feedback overlay
        AnimatedVisibility(
            visible = showRemovedMessage,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = 24.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .background(Primary)
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_circle_checkmark),
                        contentDescription = null,
                        tint = Neutral_Black,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = stringResource(R.string.projects_removed_from_list),
                        color = Neutral_Black,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }

    // Music player bottom sheet — shown when a song is tapped
    selectedSong?.let { song ->
        MusicPlayerBottomSheet(
            song = song,
            cacheDataSourceFactory = audioPreviewCache.cacheDataSourceFactory,
            onDismiss = viewModel::onDismissPlayer,
            onUseToCreate = { viewModel.onUseToCreateVideo(song) }
        )
    }
}

@Composable
fun ProjectTabRow(
    modifier: Modifier = Modifier,
    tabs: List<String>,
    state: LazyListState,
    currentPage: Int,
    onClick: (Int) -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
    ) {
        Spacer(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Color.White.copy(0.08f),
                )
                .blur(12.dp)
        )

        LazyRow(
            state = state,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            contentPadding = PaddingValues(start = 10.dp, end = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(tabs) { index, tab ->
                val isSelected = currentPage == index
                Box(
                    modifier = Modifier
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            onClick.invoke(index)
                        }
                ) {
                    if (isSelected) {
                        Spacer(
                            modifier = Modifier
                                .matchParentSize()
                                .background(
                                    Color.White.copy(0.1f),
                                    RoundedCornerShape(16.dp)
                                )
                                .border(
                                    1.dp,
                                    Color.White.copy(
                                        0.12f
                                    ),
                                    RoundedCornerShape(16.dp)
                                )
                                .blur(4.dp)
                        )
                    }

                    Text(
                        text = tab,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.W400,
                        fontSize = 16.sp,
                        color = if (isSelected) Primary else Color.White,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    )
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
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
            .padding(top = 40.dp),
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
            painter = painterResource(id = R.drawable.ic_circle_plus_v2),
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