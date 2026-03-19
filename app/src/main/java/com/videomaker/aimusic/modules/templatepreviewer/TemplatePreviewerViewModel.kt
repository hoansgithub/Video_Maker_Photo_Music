package com.videomaker.aimusic.modules.templatepreviewer

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.videomaker.aimusic.domain.model.AspectRatio
import com.videomaker.aimusic.domain.model.MusicSong
import com.videomaker.aimusic.domain.model.ProjectSettings
import com.videomaker.aimusic.domain.model.VideoTemplate
import com.videomaker.aimusic.domain.repository.SongRepository
import com.videomaker.aimusic.domain.repository.TemplateRepository
import com.videomaker.aimusic.domain.usecase.CreateProjectUseCase
import com.videomaker.aimusic.domain.usecase.UpdateProjectSettingsUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// ============================================
// UI STATE
// ============================================

sealed class TemplatePreviewerUiState {
    data object Loading : TemplatePreviewerUiState()
    data class Ready(
        val templates: List<VideoTemplate>,
        val initialPage: Int,
        val isCreatingProject: Boolean = false
    ) : TemplatePreviewerUiState()
    data class Error(val message: String) : TemplatePreviewerUiState()
}

// ============================================
// SONG LOAD STATE
// ============================================

sealed class SongLoadState {
    data object None : SongLoadState()
    data object Loading : SongLoadState()
    data class Ready(val song: MusicSong) : SongLoadState()
}

// ============================================
// NAVIGATION EVENTS
// ============================================

sealed class TemplatePreviewerNavigationEvent {
    data object NavigateBack : TemplatePreviewerNavigationEvent()
    data class NavigateToEditor(val projectId: String) : TemplatePreviewerNavigationEvent()
}

// ============================================
// VIEW MODEL
// ============================================

class TemplatePreviewerViewModel(
    private val initialTemplateId: String,
    imageUrisStr: List<String>,
    private val templateRepository: TemplateRepository,
    private val songRepository: SongRepository,
    private val createProjectUseCase: CreateProjectUseCase,
    private val updateProjectSettingsUseCase: UpdateProjectSettingsUseCase
) : ViewModel() {

    private val imageUris: List<Uri> = imageUrisStr.mapNotNull { uriStr ->
        uriStr.takeIf { it.isNotBlank() }?.let { Uri.parse(it) }
    }

    // UI State
    private val _uiState = MutableStateFlow<TemplatePreviewerUiState>(TemplatePreviewerUiState.Loading)
    val uiState: StateFlow<TemplatePreviewerUiState> = _uiState.asStateFlow()

    // Navigation Events — StateFlow-based (Gold standard per CLAUDE.md)
    private val _navigationEvent = MutableStateFlow<TemplatePreviewerNavigationEvent?>(null)
    val navigationEvent: StateFlow<TemplatePreviewerNavigationEvent?> = _navigationEvent.asStateFlow()

    // Current song for the visible page
    private val _currentSong = MutableStateFlow<SongLoadState>(SongLoadState.None)
    val currentSong: StateFlow<SongLoadState> = _currentSong.asStateFlow()

    // Pagination tracking
    private var currentOffset = 0
    private var isLoadingMore = false
    private var hasMorePages = true

    // Cancels the in-flight song fetch when the page changes before it resolves
    private var songLoadJob: Job? = null

    init {
        loadInitialTemplates()
    }

    // ============================================
    // PUBLIC METHODS
    // ============================================

    fun onPageChanged(virtualIndex: Int) {
        val state = _uiState.value as? TemplatePreviewerUiState.Ready ?: return
        if (state.templates.isEmpty()) return
        val realIndex = virtualIndex % state.templates.size
        val remaining = state.templates.size - 1 - realIndex
        if (remaining <= LOAD_MORE_THRESHOLD && hasMorePages && !isLoadingMore) {
            loadMoreTemplates()
        }
        loadSongForTemplate(state.templates[realIndex])
    }

    fun onUseThisTemplate(template: VideoTemplate) {
        val currentState = _uiState.value as? TemplatePreviewerUiState.Ready ?: return
        if (currentState.isCreatingProject) return

        viewModelScope.launch {
            _uiState.value = currentState.copy(isCreatingProject = true)

            createProjectUseCase(imageUris)
                .onSuccess { project ->
                    val settings = buildSettingsFromTemplate(template)
                    updateProjectSettingsUseCase(project.id, settings)
                        .onSuccess {
                            _navigationEvent.value = TemplatePreviewerNavigationEvent.NavigateToEditor(project.id)
                        }
                        .onFailure {
                            // Still navigate — settings update failure is non-fatal
                            _navigationEvent.value = TemplatePreviewerNavigationEvent.NavigateToEditor(project.id)
                        }
                }
                .onFailure { error ->
                    _uiState.value = TemplatePreviewerUiState.Error(
                        error.message ?: "Failed to create project"
                    )
                }
        }
    }

    fun onNavigateBack() {
        _navigationEvent.value = TemplatePreviewerNavigationEvent.NavigateBack
    }

    /** Called by UI after navigation is handled — clears the event */
    fun onNavigationHandled() {
        _navigationEvent.value = null
    }

    // ============================================
    // PRIVATE METHODS
    // ============================================

    private fun loadInitialTemplates() {
        viewModelScope.launch {
            _uiState.value = TemplatePreviewerUiState.Loading
            currentOffset = 0
            hasMorePages = true

            templateRepository.getTemplates(limit = PAGE_SIZE, offset = 0)
                .onSuccess { templates ->
                    currentOffset = templates.size
                    hasMorePages = templates.size >= PAGE_SIZE

                    val initialPage = templates.indexOfFirst { it.id == initialTemplateId }
                        .takeIf { it >= 0 } ?: 0

                    _uiState.value = TemplatePreviewerUiState.Ready(
                        templates = templates,
                        initialPage = initialPage
                    )
                    // Kick off song load for the initial page
                    loadSongForTemplate(templates[initialPage])
                }
                .onFailure { error ->
                    _uiState.value = TemplatePreviewerUiState.Error(
                        error.message ?: "Failed to load templates"
                    )
                }
        }
    }

    private fun loadMoreTemplates() {
        if (isLoadingMore || !hasMorePages) return

        isLoadingMore = true
        viewModelScope.launch {
            try {
                templateRepository.getTemplates(limit = PAGE_SIZE, offset = currentOffset)
                    .onSuccess { newTemplates ->
                        currentOffset += newTemplates.size
                        hasMorePages = newTemplates.size >= PAGE_SIZE

                        val currentState = _uiState.value as? TemplatePreviewerUiState.Ready
                        if (currentState != null && newTemplates.isNotEmpty()) {
                            _uiState.value = currentState.copy(
                                templates = currentState.templates + newTemplates
                            )
                        }
                    }
                    // Silently fail on pagination — user can scroll back up
            } finally {
                isLoadingMore = false
            }
        }
    }

    private fun loadSongForTemplate(template: VideoTemplate) {
        val songId = template.songId
        if (songId <= 0L) {
            songLoadJob?.cancel()
            _currentSong.value = SongLoadState.None
            return
        }
        // Already showing this song — no need to reload
        val current = _currentSong.value
        if (current is SongLoadState.Ready && current.song.id == songId) return

        songLoadJob?.cancel()
        _currentSong.value = SongLoadState.Loading
        songLoadJob = viewModelScope.launch {
            songRepository.getSongById(songId)
                .onSuccess { song -> _currentSong.value = SongLoadState.Ready(song) }
                .onFailure { _currentSong.value = SongLoadState.None }
        }
    }

    private fun buildSettingsFromTemplate(template: VideoTemplate): ProjectSettings {
        val aspectRatio = when (template.aspectRatio) {
            "16:9" -> AspectRatio.RATIO_16_9
            "1:1" -> AspectRatio.RATIO_1_1
            "4:5" -> AspectRatio.RATIO_4_5
            else -> AspectRatio.RATIO_9_16
        }

        return ProjectSettings(
            imageDurationMs = template.imageDurationMs.toLong(),
            transitionPercentage = template.transitionPct,
            effectSetId = template.effectSetId,
            musicSongId = template.songId.takeIf { it > 0L },
            aspectRatio = aspectRatio
        )
    }

    companion object {
        private const val PAGE_SIZE = 15
        private const val LOAD_MORE_THRESHOLD = 3
    }
}