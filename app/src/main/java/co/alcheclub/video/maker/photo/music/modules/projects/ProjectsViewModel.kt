package co.alcheclub.video.maker.photo.music.modules.projects

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.alcheclub.video.maker.photo.music.domain.model.Project
import co.alcheclub.video.maker.photo.music.domain.usecase.DeleteProjectUseCase
import co.alcheclub.video.maker.photo.music.domain.usecase.GetAllProjectsUseCase
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

// ============================================
// UI STATE
// ============================================

/**
 * ProjectsUiState - Sealed class state machine for projects screen
 */
sealed class ProjectsUiState {
    data object Loading : ProjectsUiState()
    data class Success(val projects: List<Project>) : ProjectsUiState()
    data class Error(val message: String) : ProjectsUiState()
}

// ============================================
// NAVIGATION EVENTS
// ============================================

/**
 * ProjectsNavigationEvent - Channel-based navigation events
 */
sealed class ProjectsNavigationEvent {
    data object NavigateBack : ProjectsNavigationEvent()
    data class NavigateToEditor(val projectId: String) : ProjectsNavigationEvent()
}

// ============================================
// VIEW MODEL
// ============================================

/**
 * ProjectsViewModel - Manages projects list state
 *
 * Follows CLAUDE.md patterns:
 * - Sealed class state machine
 * - Channel for navigation events
 * - viewModelScope for coroutines
 */
class ProjectsViewModel(
    private val getAllProjectsUseCase: GetAllProjectsUseCase,
    private val deleteProjectUseCase: DeleteProjectUseCase
) : ViewModel() {

    // ============================================
    // STATE
    // ============================================

    private val _uiState = MutableStateFlow<ProjectsUiState>(ProjectsUiState.Loading)
    val uiState: StateFlow<ProjectsUiState> = _uiState.asStateFlow()

    // ============================================
    // NAVIGATION EVENTS
    // ============================================

    private val _navigationEvent = Channel<ProjectsNavigationEvent>(Channel.BUFFERED)
    val navigationEvent = _navigationEvent.receiveAsFlow()

    // ============================================
    // INITIALIZATION
    // ============================================

    init {
        loadProjects()
    }

    private fun loadProjects() {
        viewModelScope.launch {
            _uiState.value = ProjectsUiState.Loading

            try {
                getAllProjectsUseCase.observe().collect { projects ->
                    _uiState.value = ProjectsUiState.Success(projects)
                }
            } catch (e: Exception) {
                _uiState.value = ProjectsUiState.Error(e.message ?: "Failed to load projects")
            }
        }
    }

    // ============================================
    // USER ACTIONS
    // ============================================

    /**
     * Open a project in the editor
     */
    fun openProject(projectId: String) {
        viewModelScope.launch {
            _navigationEvent.send(ProjectsNavigationEvent.NavigateToEditor(projectId))
        }
    }

    /**
     * Delete a project
     */
    fun deleteProject(projectId: String) {
        viewModelScope.launch {
            try {
                deleteProjectUseCase(projectId)
                // List will update automatically via Flow observation
            } catch (e: Exception) {
                // Handle error silently for now
                android.util.Log.e("ProjectsViewModel", "Failed to delete project", e)
            }
        }
    }

    /**
     * Navigate back to home
     */
    fun navigateBack() {
        viewModelScope.launch {
            _navigationEvent.send(ProjectsNavigationEvent.NavigateBack)
        }
    }
}
