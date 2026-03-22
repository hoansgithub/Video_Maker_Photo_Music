package com.videomaker.aimusic.modules.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.net.Uri
import com.videomaker.aimusic.domain.model.Asset
import com.videomaker.aimusic.domain.model.AspectRatio
import com.videomaker.aimusic.domain.model.EditorInitialData
import com.videomaker.aimusic.domain.model.Project
import com.videomaker.aimusic.domain.model.ProjectSettings
import com.videomaker.aimusic.domain.model.VideoQuality
import com.videomaker.aimusic.domain.repository.EffectSetRepository
import com.videomaker.aimusic.domain.repository.SongRepository
import com.videomaker.aimusic.domain.usecase.AddAssetsUseCase
import com.videomaker.aimusic.domain.usecase.CreateProjectUseCase
import com.videomaker.aimusic.domain.usecase.GetProjectUseCase
import com.videomaker.aimusic.domain.usecase.RemoveAssetUseCase
import com.videomaker.aimusic.domain.usecase.UpdateProjectSettingsUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

// ============================================
// UI STATE
// ============================================

sealed class EditorUiState {
    data object Loading : EditorUiState()

    data class Success(
        val project: Project,
        val isUnsavedProject: Boolean = false,
        val selectedAssetIndex: Int = 0,
        val isPlaying: Boolean = false,
        val showSettingsPanel: Boolean = false,
        val pendingSettings: ProjectSettings? = null,
        val currentPositionMs: Long = 0L,
        val durationMs: Long = 0L,
        val seekToPosition: Long? = null,
        val scrubToPosition: Long? = null,
        val wasPlayingBeforeSeek: Boolean = false,
        val selectedQuality: VideoQuality = VideoQuality.DEFAULT,
        val effectSetName: String = "Effect"
    ) : EditorUiState() {
        val hasPendingChanges: Boolean get() = pendingSettings != null || isUnsavedProject
        val displaySettings: ProjectSettings get() = pendingSettings ?: project.settings

        /**
         * Project with pending settings applied - use this for preview/display
         * This allows real-time preview of unsaved changes (e.g., volume slider)
         */
        val displayProject: Project get() = if (pendingSettings != null) {
            project.copy(settings = pendingSettings)
        } else {
            project
        }
    }

    data class Error(val message: String) : EditorUiState()
}

// ============================================
// NAVIGATION EVENTS
// ============================================

sealed class EditorNavigationEvent {
    data object NavigateBack : EditorNavigationEvent()
    data class NavigateToPreview(val projectId: String) : EditorNavigationEvent()
    data class NavigateToExport(val projectId: String) : EditorNavigationEvent()
}

// ============================================
// VIEW MODEL
// ============================================

class EditorViewModel(
    private val projectId: String?,
    private val initialData: EditorInitialData?,
    private val getProjectUseCase: GetProjectUseCase,
    private val createProjectUseCase: CreateProjectUseCase,
    private val updateSettingsUseCase: UpdateProjectSettingsUseCase,
    private val addAssetsUseCase: AddAssetsUseCase,
    private val removeAssetUseCase: RemoveAssetUseCase,
    private val songRepository: SongRepository,
    private val effectSetRepository: EffectSetRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<EditorUiState>(EditorUiState.Loading)
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    // Navigation events are separate from UI state — never embedded in high-frequency state
    private val _navigationEvent = MutableStateFlow<EditorNavigationEvent?>(null)
    val navigationEvent: StateFlow<EditorNavigationEvent?> = _navigationEvent.asStateFlow()

    // Track the actual project ID (might be generated for new projects)
    private var currentProjectId: String? = projectId

    // Observer job for existing projects
    private var projectObserverJob: Job? = null

    init {
        require(projectId != null || initialData != null) {
            "Either projectId or initialData must be provided"
        }
        loadOrInitializeProject()
    }

    /**
     * Fetches the effect set name by ID.
     * Returns "Effect" as default if ID is null or not found.
     */
    private suspend fun getEffectSetName(effectSetId: String?): String {
        if (effectSetId == null) return "Effect"
        return effectSetRepository.getEffectSetById(effectSetId)
            .getOrNull()
            ?.name
            ?: "Effect"
    }

    private fun loadOrInitializeProject() {
        if (projectId != null) {
            // Mode 1: Load existing project from DB
            loadExistingProject(projectId)
        } else if (initialData != null) {
            // Mode 2: Initialize new project in memory
            initializeNewProject(initialData)
        }
    }

    private fun loadExistingProject(id: String) {
        projectObserverJob?.cancel()
        projectObserverJob = viewModelScope.launch {
            _uiState.value = EditorUiState.Loading
            try {
                getProjectUseCase.observe(id).collect { project ->
                    if (project != null) {
                        val currentState = _uiState.value
                        val prev = currentState as? EditorUiState.Success
                        val selectedIndex = prev?.selectedAssetIndex
                            ?.coerceIn(0, project.assets.lastIndex.coerceAtLeast(0)) ?: 0

                        // Load effect set name
                        val effectSetName = getEffectSetName(project.settings.effectSetId)

                        _uiState.value = EditorUiState.Success(
                            project = project,
                            isUnsavedProject = false,
                            selectedAssetIndex = selectedIndex,
                            isPlaying = prev?.isPlaying ?: false,
                            showSettingsPanel = prev?.showSettingsPanel ?: false,
                            pendingSettings = prev?.pendingSettings,
                            currentPositionMs = prev?.currentPositionMs ?: 0L,
                            durationMs = prev?.durationMs ?: 0L,
                            seekToPosition = prev?.seekToPosition,
                            scrubToPosition = prev?.scrubToPosition,
                            wasPlayingBeforeSeek = prev?.wasPlayingBeforeSeek ?: false,
                            effectSetName = effectSetName
                        )
                    } else {
                        _uiState.value = EditorUiState.Error("Project not found")
                    }
                }
            } catch (e: Exception) {
                _uiState.value = EditorUiState.Error(e.message ?: "Failed to load project")
            }
        }
    }

    private fun initializeNewProject(data: EditorInitialData) {
        viewModelScope.launch {
            _uiState.value = EditorUiState.Loading
            try {
                // Generate temporary project ID
                val tempId = UUID.randomUUID().toString()
                currentProjectId = tempId

                // Create in-memory project
                val imageUris = data.imageUris.mapNotNull { uriStr ->
                    uriStr.takeIf { it.isNotBlank() }?.let { Uri.parse(it) }
                }

                val assets = imageUris.mapIndexed { index, uri ->
                    Asset(
                        id = UUID.randomUUID().toString(),
                        uri = uri,
                        orderIndex = index
                    )
                }

                // Fetch song data (name + URL) to ensure consistency across app
                val song = data.musicSongId?.let { songId ->
                    songRepository.getSongById(songId).getOrNull()
                }

                val settings = ProjectSettings(
                    imageDurationMs = data.imageDurationMs,
                    transitionPercentage = data.transitionPercentage,
                    effectSetId = data.effectSetId,
                    musicSongId = data.musicSongId,
                    musicSongName = song?.name, // For display in UI
                    musicSongUrl = song?.mp3Url, // For playback (same URL as previewer)
                    aspectRatio = data.aspectRatio
                )

                val project = Project(
                    id = tempId,
                    name = "New Project",
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                    thumbnailUri = imageUris.firstOrNull(),
                    assets = assets,
                    settings = settings
                )

                // Load effect set name
                val effectSetName = getEffectSetName(settings.effectSetId)

                _uiState.value = EditorUiState.Success(
                    project = project,
                    isUnsavedProject = true,
                    effectSetName = effectSetName
                )
            } catch (e: Exception) {
                _uiState.value = EditorUiState.Error(e.message ?: "Failed to initialize project")
            }
        }
    }

    /**
     * Comprehensive save function that handles both pending settings and new projects.
     *
     * Save logic:
     * 1. If there are pending settings, apply them to the project
     * 2. If project is unsaved (new), create it in the database
     * 3. If project exists, settings are auto-saved via applySettings()
     *
     * Returns true if save was successful or no save was needed.
     */
    suspend fun saveProject(): Boolean {
        val currentState = _uiState.value
        if (currentState !is EditorUiState.Success) return false

        // Step 1: Apply pending settings if any
        if (currentState.pendingSettings != null) {
            applySettings()
            // Wait a bit for settings to be applied
            kotlinx.coroutines.delay(100)
        }

        // Step 2: Save new project if unsaved
        if (currentState.isUnsavedProject) {
            return try {
                val project = currentState.project
                val assetUris = project.assets.map { it.uri }
                val settings = currentState.displaySettings

                // Create project in DB with settings
                val result = createProjectUseCase(assetUris, settings)
                result.onSuccess { createdProject ->
                    // Update current project ID
                    currentProjectId = createdProject.id
                    // Start observing the DB project - it will update the state
                    // Don't manually update state here to avoid race condition
                    loadExistingProject(createdProject.id)
                }
                result.onFailure { error ->
                    // Show error to user
                    _uiState.value = EditorUiState.Error(
                        error.message ?: "Failed to save project"
                    )
                }
                result.isSuccess
            } catch (e: Exception) {
                // Show error to user
                _uiState.value = EditorUiState.Error(
                    e.message ?: "Failed to save project"
                )
                false
            }
        }

        // Already saved and no pending changes
        return true
    }

    fun addAssets(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val currentState = _uiState.value
        if (currentState !is EditorUiState.Success) return

        if (currentState.isUnsavedProject) {
            // In-memory update
            val existingCount = currentState.project.assets.size
            val newAssets = uris.mapIndexed { index, uri ->
                Asset(
                    id = UUID.randomUUID().toString(),
                    uri = uri,
                    orderIndex = existingCount + index
                )
            }
            _uiState.value = currentState.copy(
                project = currentState.project.copy(
                    assets = currentState.project.assets + newAssets,
                    updatedAt = System.currentTimeMillis()
                )
            )
        } else {
            // DB update
            viewModelScope.launch {
                currentProjectId?.let { id ->
                    addAssetsUseCase(id, uris)
                }
            }
        }
    }

    /**
     * Remove an asset from the project.
     * Enforces minimum 2-image constraint. Returns false if blocked.
     * Note: the actual DB removal is async; Room Flow will push the updated project.
     */
    fun removeAsset(assetId: String): Boolean {
        val currentState = _uiState.value
        if (currentState !is EditorUiState.Success) return false
        if (currentState.project.assets.size <= 2) return false

        if (currentState.isUnsavedProject) {
            // In-memory update - remove and reindex
            val remainingAssets = currentState.project.assets
                .filter { it.id != assetId }
                .mapIndexed { index, asset ->
                    asset.copy(orderIndex = index)
                }
            _uiState.value = currentState.copy(
                project = currentState.project.copy(
                    assets = remainingAssets,
                    thumbnailUri = remainingAssets.firstOrNull()?.uri,
                    updatedAt = System.currentTimeMillis()
                )
            )
        } else {
            // DB update
            viewModelScope.launch {
                currentProjectId?.let { id ->
                    removeAssetUseCase(id, assetId)
                }
            }
        }

        return true
    }

    fun updateQuality(quality: VideoQuality) {
        val currentState = _uiState.value
        if (currentState is EditorUiState.Success) {
            _uiState.value = currentState.copy(selectedQuality = quality)
        }
    }

    fun updateEffectSet(effectSetId: String?) {
        viewModelScope.launch {
            // Load effect set name
            val effectSetName = if (effectSetId != null) {
                getEffectSetName(effectSetId)
            } else {
                "Effect"
            }

            // Update both pending settings and effect set name
            updatePendingSettings { it.copy(effectSetId = effectSetId) }

            // Update effect set name in state
            _uiState.update { state ->
                if (state is EditorUiState.Success) {
                    state.copy(effectSetName = effectSetName)
                } else {
                    state
                }
            }
        }
    }

    fun updateImageDuration(durationMs: Long) {
        updatePendingSettings { it.copy(imageDurationMs = durationMs) }
    }

    fun updateMusicSong(songId: Long?, songUrl: String? = null) {
        // Fetch song data to get both name and URL
        viewModelScope.launch {
            val song = songId?.let { songRepository.getSongById(it).getOrNull() }
            updatePendingSettings {
                it.copy(
                    musicSongId = songId,
                    musicSongName = song?.name,
                    musicSongUrl = song?.mp3Url,
                    musicSongCoverUrl = song?.coverUrl,
                    customAudioUri = null
                )
            }
        }
    }

    fun updateMusicTrack(songId: Long, songName: String, songUrl: String, songCoverUrl: String) {
        // Direct update with all song details (no fetch needed)
        updatePendingSettings {
            it.copy(
                musicSongId = songId,
                musicSongName = songName,
                musicSongUrl = songUrl,
                musicSongCoverUrl = songCoverUrl,
                customAudioUri = null
            )
        }
    }

    fun updateAudioVolume(volume: Float) {
        // Store in pending settings - NO database write until user confirms
        updatePendingSettings { it.copy(audioVolume = volume) }
    }

    fun updateAspectRatio(ratio: AspectRatio) {
        updatePendingSettings { it.copy(aspectRatio = ratio) }
    }

    private fun updatePendingSettings(update: (ProjectSettings) -> ProjectSettings) {
        val currentState = _uiState.value
        if (currentState is EditorUiState.Success) {
            val baseSettings = currentState.pendingSettings ?: currentState.project.settings
            _uiState.value = currentState.copy(pendingSettings = update(baseSettings))
        }
    }

    fun applySettings() {
        val currentState = _uiState.value
        if (currentState is EditorUiState.Success && currentState.pendingSettings != null) {
            val newSettings = currentState.pendingSettings
            val updatedProject = currentState.project.copy(
                settings = newSettings,
                updatedAt = System.currentTimeMillis()
            )
            _uiState.value = currentState.copy(project = updatedProject, pendingSettings = null)

            if (!currentState.isUnsavedProject) {
                // DB update
                viewModelScope.launch {
                    currentProjectId?.let { id ->
                        updateSettingsUseCase(id, newSettings)
                    }
                }
            }
            // For unsaved projects, settings are already in memory
        }
    }

    fun discardPendingSettings() {
        val currentState = _uiState.value
        if (currentState is EditorUiState.Success) {
            _uiState.value = currentState.copy(pendingSettings = null)
        }
    }

    // ============================================
    // PLAYBACK CONTROLS
    // ============================================

    fun togglePlayback() {
        val currentState = _uiState.value
        if (currentState is EditorUiState.Success) {
            _uiState.value = currentState.copy(isPlaying = !currentState.isPlaying)
        }
    }

    fun setPlaybackState(isPlaying: Boolean) {
        val currentState = _uiState.value
        if (currentState is EditorUiState.Success) {
            _uiState.value = currentState.copy(isPlaying = isPlaying)
        }
    }

    fun stopPlayback() {
        val currentState = _uiState.value
        if (currentState is EditorUiState.Success) {
            _uiState.value = currentState.copy(
                wasPlayingBeforeSeek = currentState.isPlaying,
                isPlaying = false
            )
        }
    }

    fun updatePlaybackPosition(currentMs: Long, durationMs: Long) {
        val currentState = _uiState.value
        if (currentState is EditorUiState.Success) {
            if (currentState.currentPositionMs != currentMs || currentState.durationMs != durationMs) {
                _uiState.value = currentState.copy(currentPositionMs = currentMs, durationMs = durationMs)
            }
        }
    }

    fun seekTo(positionMs: Long) {
        val currentState = _uiState.value
        if (currentState is EditorUiState.Success) {
            _uiState.value = currentState.copy(seekToPosition = positionMs)
        }
    }

    fun scrubTo(positionMs: Long) {
        val currentState = _uiState.value
        if (currentState is EditorUiState.Success) {
            _uiState.value = currentState.copy(scrubToPosition = positionMs)
        }
    }

    fun clearScrubRequest() {
        val currentState = _uiState.value
        if (currentState is EditorUiState.Success) {
            _uiState.value = currentState.copy(scrubToPosition = null)
        }
    }

    fun clearSeekRequest() {
        val currentState = _uiState.value
        if (currentState is EditorUiState.Success) {
            val shouldResume = currentState.wasPlayingBeforeSeek
            _uiState.value = currentState.copy(
                seekToPosition = null,
                isPlaying = if (shouldResume) true else currentState.isPlaying,
                wasPlayingBeforeSeek = false
            )
        }
    }

    // ============================================
    // NAVIGATION — separate StateFlow, never in UI state
    // ============================================

    fun navigateBack() {
        _navigationEvent.value = EditorNavigationEvent.NavigateBack
    }

    fun navigateToExport() {
        val currentState = _uiState.value
        if (currentState !is EditorUiState.Success) return

        // Always save before export (applies pending settings and saves new projects)
        viewModelScope.launch {
            if (saveProject()) {
                currentProjectId?.let { id ->
                    _navigationEvent.value = EditorNavigationEvent.NavigateToExport(id)
                }
            }
            // If save failed, user sees error message and stays in editor
        }
    }

    /** Called by UI after navigation is handled — clears the event. */
    fun onNavigationHandled() {
        _navigationEvent.value = null
    }

    override fun onCleared() {
        super.onCleared()
        projectObserverJob?.cancel()
        projectObserverJob = null
    }
}