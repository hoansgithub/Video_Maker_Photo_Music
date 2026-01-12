package com.aimusic.videoeditor.modules.songs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aimusic.videoeditor.R
import com.aimusic.videoeditor.ui.theme.VideoMakerTheme

// ============================================
// SONGS SCREEN
// ============================================

@Composable
fun SongsScreen(
    viewModel: SongsViewModel = viewModel(),
    onNavigateToSongDetail: (Int) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val navigationEvent by viewModel.navigationEvent.collectAsStateWithLifecycle()

    // Handle navigation events
    LaunchedEffect(navigationEvent) {
        navigationEvent?.let { event ->
            when (event) {
                is SongsNavigationEvent.NavigateToSongDetail -> onNavigateToSongDetail(event.songId)
            }
            viewModel.onNavigationHandled()
        }
    }

    // UI based on state
    when (val state = uiState) {
        is SongsUiState.Loading -> SongsLoadingContent()
        is SongsUiState.Empty -> SongsEmptyContent()
        is SongsUiState.Success -> SongsListContent(
            songs = state.songs,
            onSongClick = viewModel::onSongClick
        )
        is SongsUiState.Error -> SongsErrorContent(message = state.message)
    }
}

@Composable
private fun SongsLoadingContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun SongsEmptyContent() {
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
                text = stringResource(R.string.home_tab_songs),
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

@Composable
private fun SongsListContent(
    songs: List<Song>,
    onSongClick: (Song) -> Unit
) {
    // TODO: Implement songs list UI
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(text = "${songs.size} songs")
    }
}

@Composable
private fun SongsErrorContent(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            color = MaterialTheme.colorScheme.error
        )
    }
}

// ============================================
// PREVIEW
// ============================================

@Preview(showBackground = true, backgroundColor = 0xFF1A1A1A)
@Composable
private fun SongsScreenPreview() {
    VideoMakerTheme {
        SongsScreen()
    }
}
