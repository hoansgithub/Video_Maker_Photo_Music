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
    /**
     * @param nonce Increments on every page change so StateFlow always emits a new value,
     *   even when the same song plays across consecutive templates. This guarantees the
     *   player restarts from the beginning on each swipe.
     */
    data class Ready(val song: MusicSong, val nonce: Int) : SongLoadState()
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
    /** When >= 0, this song is played on every page and applied on project creation,
     *  overriding each template's embedded song. -1 = use template's own song. */
    private val overrideSongId: Long = -1L,
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

    // Incremented on every page change so SongLoadState.Ready always differs between pages,
    // even when consecutive templates share the same song — guaranteeing player restart.
    private var songNonce = 0

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

    fun onUseThisTemplate(template: VideoTemplate, aspectRatio: AspectRatio) {
        val currentState = _uiState.value as? TemplatePreviewerUiState.Ready ?: return
        if (currentState.isCreatingProject) return

        viewModelScope.launch {
            _uiState.value = currentState.copy(isCreatingProject = true)

            createProjectUseCase(imageUris)
                .onSuccess { project ->
                    val settings = buildSettingsFromTemplate(template, aspectRatio)
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
        // If the caller supplied an override song, use it on every page.
        val songId = if (overrideSongId >= 0L) overrideSongId else template.songId

        // Always increment nonce — even for the same song — so Ready(song, nonce) differs
        // from the previous emission and LaunchedEffect(currentSong) re-runs, restarting playback.
        val nonce = ++songNonce

        if (songId <= 0L) {
            songLoadJob?.cancel()
            _currentSong.value = SongLoadState.None
            return
        }

        songLoadJob?.cancel()
        _currentSong.value = SongLoadState.Loading
        songLoadJob = viewModelScope.launch {
            songRepository.getSongById(songId)
                .onSuccess { song -> _currentSong.value = SongLoadState.Ready(song, nonce) }
                .onFailure { _currentSong.value = SongLoadState.None }
        }
    }

    private fun buildSettingsFromTemplate(template: VideoTemplate, aspectRatio: AspectRatio): ProjectSettings {
        return ProjectSettings(
            imageDurationMs = template.imageDurationMs.toLong(),
            transitionPercentage = template.transitionPct,
            effectSetId = template.effectSetId,
            musicSongId = if (overrideSongId >= 0L) overrideSongId
                          else template.songId.takeIf { it > 0L },
            aspectRatio = aspectRatio
        )
    }

    companion object {
        private const val PAGE_SIZE = 15
        private const val LOAD_MORE_THRESHOLD = 3
    }
}