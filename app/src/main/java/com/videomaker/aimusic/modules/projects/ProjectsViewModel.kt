package com.videomaker.aimusic.modules.projects

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.videomaker.aimusic.domain.model.MusicSong
import com.videomaker.aimusic.domain.model.Project
import com.videomaker.aimusic.domain.model.VideoTemplate
import com.videomaker.aimusic.domain.repository.TemplateRepository
import com.videomaker.aimusic.domain.usecase.DeleteProjectUseCase
import com.videomaker.aimusic.domain.usecase.GetAllProjectsUseCase
import com.videomaker.aimusic.domain.usecase.GetSuggestedSongsUseCase
import com.videomaker.aimusic.domain.usecase.LikeSongUseCase
import com.videomaker.aimusic.domain.usecase.ObserveLikedSongsUseCase
import com.videomaker.aimusic.domain.usecase.ObserveLikedTemplatesUseCase
import com.videomaker.aimusic.domain.usecase.UnlikeSongUseCase
import com.videomaker.aimusic.domain.usecase.UnlikeTemplateUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// ============================================
// UI STATE
// ============================================

sealed class TemplateTabState {
    data object Idle : TemplateTabState()
    data object Loading : TemplateTabState()
    data class Success(val templates: List<VideoTemplate>) : TemplateTabState()
    data class Error(val message: String) : TemplateTabState()
}

sealed class SongTabState {
    data object Idle : SongTabState()
    data object Loading : SongTabState()
    data class Success(val songs: List<MusicSong>) : SongTabState()
    data class Error(val message: String) : SongTabState()
}

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
    data class NavigateToTemplateDetail(val templateId: String) : ProjectsNavigationEvent()
    data object NavigateToSongSearch : ProjectsNavigationEvent()
    data object NavigateToAllSongs : ProjectsNavigationEvent()
    data object NavigateToTemplateSearch : ProjectsNavigationEvent()
    data object NavigateToAllTemplates : ProjectsNavigationEvent()
    data class NavigateToAssetPickerForSong(val songId: Long) : ProjectsNavigationEvent()
}

// ============================================
// VIEW MODEL
// ============================================

class ProjectsViewModel(
    private val getAllProjectsUseCase: GetAllProjectsUseCase,
    private val deleteProjectUseCase: DeleteProjectUseCase,
    private val templateRepository: TemplateRepository,
    private val getSuggestedSongsUseCase: GetSuggestedSongsUseCase,
    private val observeLikedSongsUseCase: ObserveLikedSongsUseCase,
    private val observeLikedTemplatesUseCase: ObserveLikedTemplatesUseCase,
    private val likeSongUseCase: LikeSongUseCase,
    private val unlikeSongUseCase: UnlikeSongUseCase,
    private val unlikeTemplateUseCase: UnlikeTemplateUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<ProjectsUiState>(ProjectsUiState.Loading)
    val uiState: StateFlow<ProjectsUiState> = _uiState.asStateFlow()

    private val _navigationEvent = MutableStateFlow<ProjectsNavigationEvent?>(null)
    val navigationEvent: StateFlow<ProjectsNavigationEvent?> = _navigationEvent.asStateFlow()

    private val _templateState = MutableStateFlow<TemplateTabState>(TemplateTabState.Idle)
    val templateState: StateFlow<TemplateTabState> = _templateState.asStateFlow()

    private val _songState = MutableStateFlow<SongTabState>(SongTabState.Idle)
    val songState: StateFlow<SongTabState> = _songState.asStateFlow()

    private val _selectedSong = MutableStateFlow<MusicSong?>(null)
    val selectedSong: StateFlow<MusicSong?> = _selectedSong.asStateFlow()

    val templateStateLocal: StateFlow<List<VideoTemplate>> = observeLikedTemplatesUseCase()
        .catch { e -> emit(emptyList()) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    val songStateLocal: StateFlow<List<MusicSong>> = observeLikedSongsUseCase()
        .catch { e -> emit(emptyList()) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    // Liked song IDs for heart icon state in the player bottom sheet
    val likedSongIds: StateFlow<Set<Long>> = observeLikedSongsUseCase.ids()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptySet()
        )
    private var isObserving = false

    /**
     * Start observing projects - call this when the My Videos tab becomes visible
     */
    fun startObservingProjects() {
        if (isObserving) return
        isObserving = true
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
                        // Sort by creation time - latest first (descending order)
                        val sortedProjects = projects.sortedByDescending { it.createdAt }
                        ProjectsUiState.Success(sortedProjects)
                    }
                }
        }
    }

    fun onTabSelected(index: Int) {
        when (index) {
            1 -> loadTemplates()
            2 -> loadSongs()
        }
    }

    private fun loadTemplates() {
        if (_templateState.value is TemplateTabState.Success) return
        viewModelScope.launch {
            _templateState.value = TemplateTabState.Loading
            templateRepository.getTemplates(limit = 20, offset = 0)
                .onSuccess { _templateState.value = TemplateTabState.Success(it) }
                .onFailure { _templateState.value = TemplateTabState.Error(it.message ?: "Failed to load templates") }
        }
    }

    private fun loadSongs() {
        if (_songState.value is SongTabState.Success) return
        viewModelScope.launch {
            _songState.value = SongTabState.Loading
            getSuggestedSongsUseCase(limit = 20)
                .onSuccess { _songState.value = SongTabState.Success(it) }
                .onFailure { _songState.value = SongTabState.Error(it.message ?: "Failed to load songs") }
        }
    }

    fun onProjectClick(project: Project) {
        _navigationEvent.value = ProjectsNavigationEvent.NavigateToEditor(project.id)
    }

    fun onTemplateClick(template: VideoTemplate) {
        _navigationEvent.value = ProjectsNavigationEvent.NavigateToTemplateDetail(template.id)
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

    fun onLikeSong(song: MusicSong) {
        viewModelScope.launch {
            likeSongUseCase(song)
        }
    }

    fun onUnlikeSong(song: MusicSong) {
        viewModelScope.launch {
            unlikeSongUseCase(song.id)
        }
    }

    fun onUnlikeTemplate(templateId: String) {
        viewModelScope.launch {
            unlikeTemplateUseCase(templateId)
        }
    }

    fun onSongClick(song: MusicSong) {
        _selectedSong.value = song
    }

    fun onDismissPlayer() {
        _selectedSong.value = null
    }

    fun onUseToCreateVideo(song: MusicSong) {
        _selectedSong.value = null
        _navigationEvent.value = ProjectsNavigationEvent.NavigateToAssetPickerForSong(song.id)
    }

    fun onSongSearch() {
        _navigationEvent.value = ProjectsNavigationEvent.NavigateToSongSearch
    }

    fun onSeeAllSongs() {
        _navigationEvent.value = ProjectsNavigationEvent.NavigateToAllSongs
    }

    fun onTemplateSearch() {
        _navigationEvent.value = ProjectsNavigationEvent.NavigateToTemplateSearch
    }

    fun onSeeAllTemplates() {
        _navigationEvent.value = ProjectsNavigationEvent.NavigateToAllTemplates
    }
}