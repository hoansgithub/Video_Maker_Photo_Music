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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.core.content.FileProvider
import android.content.Intent
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import co.alcheclub.lib.acccore.ads.compose.NativeAdView
import com.videomaker.aimusic.R
import com.videomaker.aimusic.core.analytics.Analytics
import com.videomaker.aimusic.core.constants.AdPlacement
import com.videomaker.aimusic.core.analytics.AnalyticsEvent
import com.videomaker.aimusic.domain.model.AspectRatio
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
import com.videomaker.aimusic.ui.components.ModifierExtension.clickableSingle
import com.videomaker.aimusic.ui.components.ProcessToast
import com.videomaker.aimusic.ui.components.ProjectCard
import com.videomaker.aimusic.ui.components.SongListItem
import com.videomaker.aimusic.ui.components.StaggeredGrid
import com.videomaker.aimusic.ui.components.TemplateCard
import com.videomaker.aimusic.ui.theme.AppDimens
import com.videomaker.aimusic.ui.theme.Neutral_Black
import com.videomaker.aimusic.ui.theme.Primary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

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
    val toastState by viewModel.toastState.collectAsStateWithLifecycle()
    val audioPreviewCache: AudioPreviewCache = koinInject()
    var showRemovedMessage by remember { mutableStateOf(false) }
    val context = LocalContext.current

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
    val libraryTabByIndex: (Int) -> String = { index ->
        when (index) {
            0 -> AnalyticsEvent.Value.LibraryTab.VIDEO
            1 -> AnalyticsEvent.Value.LibraryTab.TEMPLATE_FAVORITE
            else -> AnalyticsEvent.Value.LibraryTab.SONG_FAVORITE
        }
    }
    var lastSettledPage by remember { mutableStateOf(pagerState.settledPage) }
    val coroutineScope = rememberCoroutineScope()
    val lazyListState = rememberLazyListState()

    // Start loading projects only when this tab appears
    LaunchedEffect(Unit) {
        viewModel.startObservingProjects()
    }

    // Animate LazyRow to selected tab when pager settles, and notify VM for swipe case
    LaunchedEffect(pagerState.settledPage) {
        val currentPage = pagerState.settledPage
        if (currentPage != lastSettledPage) {
            Analytics.trackLibraryClick(
                from = libraryTabByIndex(lastSettledPage),
                to = libraryTabByIndex(currentPage)
            )
            lastSettledPage = currentPage
        }
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
                .padding(top = topBarHeight)
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
                                        Analytics.trackVideoClick(
                                            videoId = project.id,
                                            templateId = project.settings.effectSetId,
                                            songId = project.settings.musicSongId?.toString(),
                                            location = AnalyticsEvent.Value.Location.LIBRARY
                                        )
                                        onProjectClick(project.id)
                                    },
                                    onProjectOption = { project ->
                                        Analytics.trackVideoOption(project.id)
                                    },
                                    onDeleteProject = { project ->
                                        Analytics.trackVideoDelete(
                                            videoId = project.id,
                                            templateId = project.settings.effectSetId,
                                            songId = project.settings.musicSongId?.toString(),
                                            duration = project.totalDurationMs,
                                            ratioSize = project.settings.aspectRatio.toAnalyticsRatioSize(),
                                            volume = (project.settings.audioVolume * 100f).toInt(),
                                            mediaQuality = null
                                        )
                                        viewModel.onDeleteProject(project)
                                    },
                                    onDownloadProject = { project ->
                                        Analytics.trackVideoDownload(
                                            videoId = project.id,
                                            templateId = project.settings.effectSetId,
                                            songId = project.settings.musicSongId?.toString(),
                                            duration = project.totalDurationMs,
                                            ratioSize = project.settings.aspectRatio.toAnalyticsRatioSize(),
                                            volume = (project.settings.audioVolume * 100f).toInt(),
                                            mediaQuantity = project.assets.size,
                                            location = AnalyticsEvent.Value.Location.LIBRARY
                                        )
                                        viewModel.onDownloadProject(project, context)
                                    },
                                    onShareProject = { project ->
                                        Analytics.trackVideoShare(
                                            videoId = project.id,
                                            templateId = project.settings.effectSetId,
                                            songId = project.settings.musicSongId?.toString(),
                                            duration = project.totalDurationMs,
                                            ratioSize = project.settings.aspectRatio.toAnalyticsRatioSize(),
                                            volume = (project.settings.audioVolume * 100f).toInt(),
                                            mediaQuantity = project.assets.size,
                                            location = AnalyticsEvent.Value.Location.LIBRARY
                                        )
                                        shareProjectVideo(context, project, viewModel)
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
                                    onTemplateClick = { templateId ->
                                        val templateName = templateStateLocal
                                            .firstOrNull { it.id == templateId }
                                            ?.name
                                            ?: "unknown"
                                        Analytics.trackTemplateClick(
                                            templateId = templateId,
                                            templateName = templateName,
                                            location = AnalyticsEvent.Value.Location.LIBRARY
                                        )
                                        onNavigateToTemplateDetail(templateId)
                                    },
                                    onDeleteTemplateClick = {
                                        val templateName = templateStateLocal
                                            .firstOrNull { template -> template.id == it }
                                            ?.name
                                            ?: "unknown"
                                        Analytics.trackTemplateOption(
                                            templateId = it,
                                            templateName = templateName,
                                            location = AnalyticsEvent.Value.Location.LIBRARY
                                        )
                                        Analytics.trackTemplateUnfavorite(
                                            templateId = it,
                                            templateName = templateName,
                                            location = AnalyticsEvent.Value.Location.LIBRARY
                                        )
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
                                    onSongClick = { song ->
                                        Analytics.trackSongClick(
                                            songId = song.id.toString(),
                                            songName = song.name,
                                            location = AnalyticsEvent.Value.Location.LIBRARY
                                        )
                                        viewModel.onSongClick(song)
                                    },
                                    onDeleteSongClick = {
                                        Analytics.trackSongOption(
                                            songId = it.id.toString(),
                                            songName = it.name,
                                            location = AnalyticsEvent.Value.Location.LIBRARY
                                        )
                                        Analytics.trackSongUnfavorite(
                                            songId = it.id.toString(),
                                            songName = it.name,
                                            location = AnalyticsEvent.Value.Location.LIBRARY
                                        )
                                        viewModel.onUnlikeSong(it)
                                        showRemovedMessage = true
                                    }
                                )
                            } else {
                                LikeSongEmpty(
                                    state = songState,
                                    onSeeAllClick = viewModel::onSeeAllSongs,
                                    onSearch = viewModel::onSongSearch,
                                    onSongClick = { song ->
                                        Analytics.trackSongClick(
                                            songId = song.id.toString(),
                                            songName = song.name,
                                            location = AnalyticsEvent.Value.Location.LIBRARY
                                        )
                                        viewModel.onSongClick(song)
                                    }
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

        // Process toast (download/share)
        ProcessToast(
            state = toastState,
            onDismiss = viewModel::onToastDismissed
        )
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
        LazyRow(
            state = state,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            contentPadding = PaddingValues(start = 10.dp, end = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(tabs) { index, tab ->
                val isSelected = currentPage == index
                Box(
                    modifier = Modifier
                        .clickableSingle {
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
    onProjectOption: (Project) -> Unit,
    onDeleteProject: (Project) -> Unit,
    onDownloadProject: (Project) -> Unit,
    onShareProject: (Project) -> Unit,
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
                    onProjectOption = onProjectOption,
                    onDeleteProject = onDeleteProject,
                    onDownloadProject = onDownloadProject,
                    onShareProject = onShareProject,
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
 * Item type for projects grid - can be either a Project or an Ad
 */
private sealed class ProjectGridItem {
    data class ProjectItem(val project: Project) : ProjectGridItem()
    data object AdItem : ProjectGridItem()
}

/**
 * Staggered grid for projects based on aspect ratio
 * Inserts native ad at position 2 when projects list is not empty
 */
@Composable
private fun ProjectsStaggeredGrid(
    projects: List<Project>,
    onProjectClick: (Project) -> Unit,
    onProjectOption: (Project) -> Unit,
    onDeleteProject: (Project) -> Unit,
    onDownloadProject: (Project) -> Unit,
    onShareProject: (Project) -> Unit,
    spacing: Dp
) {
    if (projects.isEmpty()) return

    // ✅ Create mixed list with ad inserted at position 2 (after first project)
    val gridItems = remember(projects) {
        buildList {
            projects.forEachIndexed { index, project ->
                add(ProjectGridItem.ProjectItem(project))
                // Insert ad after 1st project (index 0) - shows when at least 1 project exists
                if (index == 0) {
                    add(ProjectGridItem.AdItem)
                }
            }
        }
    }

    // ✅ OPTIMIZED: Pre-calculate adjusted aspect ratios once when projects list changes
    // Info section needs ~40dp for: date+menu row (20dp) + padding (20dp)
    // Ad has 9:16 aspect ratio (280dp media + ~148dp info = ~428dp total)
    // For 180dp card width → 320dp height (9:16 ratio = 0.5625)
    val infoSectionHeightDp = 40f
    val adAspectRatio = 9f / 16f // 9:16 portrait ratio = 0.5625

    val aspectRatios = remember(gridItems) {
        gridItems.map { item ->
            when (item) {
                is ProjectGridItem.ProjectItem -> {
                    val project = item.project
                    val thumbnailRatio = project.settings.aspectRatio.ratio
                    // Adjust ratio: assume card width of 180dp as baseline
                    val cardWidth = 180f
                    val thumbnailHeight = cardWidth / thumbnailRatio
                    val totalCardHeight = thumbnailHeight + infoSectionHeightDp
                    cardWidth / totalCardHeight // Adjusted ratio for full card including info section
                }
                is ProjectGridItem.AdItem -> adAspectRatio
            }
        }
    }

    StaggeredGrid(
        items = gridItems,
        aspectRatios = aspectRatios,
        columns = 2,
        spacing = spacing,
        key = { item ->
            when (item) {
                is ProjectGridItem.ProjectItem -> "project_${item.project.id}"
                is ProjectGridItem.AdItem -> "ad_projects_grid"
            }
        }
    ) { item ->
        when (item) {
            is ProjectGridItem.ProjectItem -> {
                ProjectCard(
                    project = item.project,
                    onClick = { onProjectClick(item.project) },
                    onOptionClick = { onProjectOption(item.project) },
                    onDelete = { onDeleteProject(item.project) },
                    onDownload = { onDownloadProject(item.project) },
                    onShare = { onShareProject(item.project) }
                )
            }
            is ProjectGridItem.AdItem -> {
                // Native ad matching project card layout
                android.util.Log.d("ProjectsTab", "🔵 Composing NativeAdView (Projects Grid)")
                NativeAdView(
                    placement = AdPlacement.NATIVE_PROJECTS_GRID,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
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
            .clickableSingle(onClick = onClick),
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
 * Share project video using system share sheet
 */
private fun shareProjectVideo(
    context: android.content.Context,
    project: Project,
    viewModel: ProjectsViewModel
) {
    viewModel.onShareStarted()

    try {
        val videoFile = viewModel.getShareVideoFile(context, project.id)
        if (videoFile == null || !videoFile.exists()) {
            viewModel.onShareError(context.getString(R.string.export_error_file_not_found))
            return
        }

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            videoFile
        )

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "video/mp4"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.export_share_video)))
        viewModel.onShareCompleted()
    } catch (e: Exception) {
        android.util.Log.e("ProjectsTab", "Share failed", e)
        viewModel.onShareError(e.message ?: context.getString(R.string.export_error_share_failed))
    }
}

private fun AspectRatio.toAnalyticsRatioSize(): String = when (this) {
    AspectRatio.RATIO_16_9 -> "16:9"
    AspectRatio.RATIO_9_16 -> "9:16"
    AspectRatio.RATIO_4_5 -> "4:5"
    AspectRatio.RATIO_1_1 -> "1:1"
}
