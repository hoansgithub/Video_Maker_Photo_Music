package com.videomaker.aimusic.modules.projects

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.videomaker.aimusic.R
import com.videomaker.aimusic.core.analytics.AnalyticsEvent
import com.videomaker.aimusic.media.audio.AudioPreviewCache
import com.videomaker.aimusic.modules.home.components.ProjectsTabContent
import com.videomaker.aimusic.modules.songs.MusicPlayerBottomSheet
import com.videomaker.aimusic.ui.components.ModifierExtension.clickableSingle
import com.videomaker.aimusic.ui.theme.FoundationBlack
import org.koin.compose.koinInject

/**
 * MyVideosScreen - Dedicated screen for the user's library (Created Video / Liked Template /
 * Liked Song), reached from Settings → Library → My Videos.
 *
 * Hosts the same content as the former Home "Library" tab ([ProjectsTabContent]) but inside its
 * own Nav3 route with a top app bar. Background is solid #030303 ([FoundationBlack]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyVideosScreen(
    viewModel: ProjectsViewModel,
    onNavigateBack: () -> Unit,
    onProjectClick: (projectId: String, thumbnailUri: String?) -> Unit,
    onCreateClick: () -> Unit,
    highlightProjectId: String? = null,
    hintMode: String? = null,
    onNavigateToTemplateDetail: (String, String?) -> Unit = { _, _ -> },
    onNavigateToSongSearch: () -> Unit = {},
    onNavigateToAllSongs: () -> Unit = {},
    onNavigateToTemplateSearch: () -> Unit = {},
    onNavigateToAllTemplates: () -> Unit = {},
    onNavigateToAssetPicker: (songId: Long) -> Unit = {},
) {
    val audioPreviewCache: AudioPreviewCache = koinInject()
    val selectedSong by viewModel.selectedSong.collectAsStateWithLifecycle()

    // CTA "Try it" hides while the user scrolls the list during preview; reappears on player
    // interaction or when a new song is selected. Mirrors the former Home Library behaviour.
    var isCtaVisible by remember { mutableStateOf(true) }
    LaunchedEffect(selectedSong?.id) {
        if (selectedSong != null) isCtaVisible = true
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = stringResource(R.string.settings_my_videos),
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_down),
                            tint = MaterialTheme.colorScheme.onSurface,
                            contentDescription = null,
                            modifier = Modifier
                                .padding(start = 16.dp)
                                .size(48.dp)
                                .background(
                                    MaterialTheme.colorScheme.onSurface.copy(0.1f),
                                    CircleShape
                                )
                                .clickableSingle(onClick = onNavigateBack)
                                .padding(12.dp)
                                .rotate(90f)
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = FoundationBlack
                    )
                )
            },
            containerColor = FoundationBlack,
            contentWindowInsets = WindowInsets(0, 0, 0, 0)
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                ProjectsTabContent(
                    viewModel = viewModel,
                    highlightProjectId = highlightProjectId,
                    hintMode = hintMode,
                    isVisible = true,
                    onCreateClick = onCreateClick,
                    onProjectClick = onProjectClick,
                    onNavigateToTemplateDetail = onNavigateToTemplateDetail,
                    onNavigateToSongSearch = onNavigateToSongSearch,
                    onNavigateToAllSongs = onNavigateToAllSongs,
                    onNavigateToTemplateSearch = onNavigateToTemplateSearch,
                    onNavigateToAllTemplates = onNavigateToAllTemplates,
                    onNavigateToAssetPicker = onNavigateToAssetPicker,
                    topBarHeight = 0.dp,
                    onListScroll = { isCtaVisible = false }
                )
            }
        }

        // Music player bottom sheet — rendered at screen root so it covers the full screen
        // (including the top app bar), matching the former Home-level placement.
        selectedSong?.let { song ->
            val selectedPlaylist by viewModel.selectedPlaylist.collectAsStateWithLifecycle()
            MusicPlayerBottomSheet(
                song = song,
                playlist = selectedPlaylist,
                categoryLocation = AnalyticsEvent.Value.Location.SONG_FAVORITE,
                genreId = null,
                cacheDataSourceFactory = audioPreviewCache.cacheDataSourceFactory,
                isCtaVisible = isCtaVisible,
                onPlayerInteraction = { isCtaVisible = true },
                onDismiss = {
                    isCtaVisible = true
                    viewModel.onDismissPlayer()
                },
                onUseToCreate = { viewModel.onUseToCreateVideo(song) }
            )
        }
    }
}
