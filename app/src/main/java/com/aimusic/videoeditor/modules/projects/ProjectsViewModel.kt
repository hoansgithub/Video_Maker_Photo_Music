package com.aimusic.videoeditor.modules.projects

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aimusic.videoeditor.domain.model.Project
import com.aimusic.videoeditor.domain.usecase.DeleteProjectUseCase
import com.aimusic.videoeditor.domain.usecase.GetAllProjectsUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

// ============================================
// UI STATE
// ============================================

sealed class ProjectsUiState {
    data object Loading : ProjectsUiState()
    data object Empty : ProjectsUiState()
    data class Success(val projects: List<Project>) : ProjectsUiState()
    data class Error(val message: String) : ProjectsUiState()
}

// ============================================
// NAVIGATION EVENTS
// ============================================

sealed class ProjectsNavigationEvent {
    data object NavigateBack : ProjectsNavigationEvent()
    data class NavigateToEditor(val projectId: String) : ProjectsNavigationEvent()
}

// ============================================
// VIEW MODEL
// ============================================

class ProjectsViewModel(
    private val getAllProjectsUseCase: GetAllProjectsUseCase,
    private val deleteProjectUseCase: DeleteProjectUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<ProjectsUiState>(ProjectsUiState.Loading)
    val uiState: StateFlow<ProjectsUiState> = _uiState.asStateFlow()

    private val _navigationEvent = MutableStateFlow<ProjectsNavigationEvent?>(null)
    val navigationEvent: StateFlow<ProjectsNavigationEvent?> = _navigationEvent.asStateFlow()

    init {
        observeProjects()
    }

    private fun observeProjects() {
        viewModelScope.launch {
            getAllProjectsUseCase.observe()
                .catch { e ->
                    _uiState.value = ProjectsUiState.Error(e.message ?: "Failed to load projects")
                }
                .collect { projects ->
                    _uiState.value = if (projects.isEmpty()) {
                        ProjectsUiState.Empty
                    } else {
                        ProjectsUiState.Success(projects)
                    }
                }
        }
    }

    fun onProjectClick(project: Project) {
        _navigationEvent.value = ProjectsNavigationEvent.NavigateToEditor(project.id)
    }

    fun onDeleteProject(project: Project) {
        viewModelScope.launch {
            deleteProjectUseCase(project.id)
        }
    }

    fun onNavigateBack() {
        _navigationEvent.value = ProjectsNavigationEvent.NavigateBack
    }

    fun onNavigationHandled() {
        _navigationEvent.value = null
    }
}
