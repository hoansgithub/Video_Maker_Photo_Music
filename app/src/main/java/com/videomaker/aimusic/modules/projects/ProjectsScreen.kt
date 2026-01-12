package com.videomaker.aimusic.modules.projects

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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.videomaker.aimusic.R
import com.videomaker.aimusic.domain.model.Project

// ============================================
// PROJECTS SCREEN
// ============================================

@Composable
fun ProjectsScreen(
    viewModel: ProjectsViewModel,
    onNavigateBack: () -> Unit = {},
    onNavigateToEditor: (String) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val navigationEvent by viewModel.navigationEvent.collectAsStateWithLifecycle()

    // Handle navigation events
    LaunchedEffect(navigationEvent) {
        navigationEvent?.let { event ->
            when (event) {
                is ProjectsNavigationEvent.NavigateBack -> onNavigateBack()
                is ProjectsNavigationEvent.NavigateToEditor -> onNavigateToEditor(event.projectId)
            }
            viewModel.onNavigationHandled()
        }
    }

    // UI based on state
    when (val state = uiState) {
        is ProjectsUiState.Loading -> ProjectsLoadingContent()
        is ProjectsUiState.Empty -> ProjectsEmptyContent()
        is ProjectsUiState.Success -> ProjectsListContent(
            projects = state.projects,
            onProjectClick = viewModel::onProjectClick
        )
        is ProjectsUiState.Error -> ProjectsErrorContent(message = state.message)
    }
}

@Composable
private fun ProjectsLoadingContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ProjectsEmptyContent() {
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

@Composable
private fun ProjectsListContent(
    projects: List<Project>,
    onProjectClick: (Project) -> Unit
) {
    // TODO: Implement projects list UI
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(text = "${projects.size} projects")
    }
}

@Composable
private fun ProjectsErrorContent(message: String) {
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
