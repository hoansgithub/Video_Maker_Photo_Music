package co.alcheclub.video.maker.photo.music.modules.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.net.Uri
import co.alcheclub.video.maker.photo.music.domain.model.Asset
import co.alcheclub.video.maker.photo.music.domain.model.AspectRatio
import co.alcheclub.video.maker.photo.music.domain.model.Project
import co.alcheclub.video.maker.photo.music.domain.model.ProjectSettings
import co.alcheclub.video.maker.photo.music.domain.usecase.AddAssetsUseCase
import co.alcheclub.video.maker.photo.music.domain.usecase.GetProjectUseCase
import co.alcheclub.video.maker.photo.music.domain.usecase.RemoveAssetUseCase
import co.alcheclub.video.maker.photo.music.domain.usecase.ReorderAssetsUseCase
import co.alcheclub.video.maker.photo.music.domain.usecase.UpdateProjectSettingsUseCase
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
 * EditorUiState - Sealed class state machine for editor screen
 */
sealed class EditorUiState {
    data object Loading : EditorUiState()

    data class Success(
        val project: Project,
        val selectedAssetIndex: Int = 0,
        val isPlaying: Boolean = false,
        val showSettingsPanel: Boolean = false
    ) : EditorUiState()

    data class Error(val message: String) : EditorUiState()
}

// ============================================
// NAVIGATION EVENTS
// ============================================

/**
 * EditorNavigationEvent - Channel-based navigation events
 */
sealed class EditorNavigationEvent {
    data object NavigateBack : EditorNavigationEvent()
    data class NavigateToPreview(val projectId: String) : EditorNavigationEvent()
    data class NavigateToExport(val projectId: String) : EditorNavigationEvent()
}

// ============================================
// VIEW MODEL
// ============================================

/**
 * EditorViewModel - Manages video editor state and actions
 *
 * Follows CLAUDE.md patterns:
 * - Sealed class state machine
 * - Channel for navigation events
 * - viewModelScope for coroutines
 */
class EditorViewModel(
    private val projectId: String,
    private val getProjectUseCase: GetProjectUseCase,
    private val updateSettingsUseCase: UpdateProjectSettingsUseCase,
    private val reorderAssetsUseCase: ReorderAssetsUseCase,
    private val addAssetsUseCase: AddAssetsUseCase,
    private val removeAssetUseCase: RemoveAssetUseCase
) : ViewModel() {

    // ============================================
    // STATE
    // ============================================

    private val _uiState = MutableStateFlow<EditorUiState>(EditorUiState.Loading)
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    // ============================================
    // NAVIGATION EVENTS
    // ============================================

    private val _navigationEvent = Channel<EditorNavigationEvent>(Channel.BUFFERED)
    val navigationEvent = _navigationEvent.receiveAsFlow()

    // ============================================
    // INITIALIZATION
    // ============================================

    init {
        loadProject()
    }

    private fun loadProject() {
        viewModelScope.launch {
            _uiState.value = EditorUiState.Loading

            try {
                getProjectUseCase.observe(projectId).collect { project ->
                    if (project != null) {
                        val currentState = _uiState.value
                        val selectedIndex = if (currentState is EditorUiState.Success) {
                            currentState.selectedAssetIndex.coerceIn(0, project.assets.lastIndex.coerceAtLeast(0))
                        } else {
                            0
                        }
                        _uiState.value = EditorUiState.Success(
                            project = project,
                            selectedAssetIndex = selectedIndex
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

    // ============================================
    // ASSET SELECTION
    // ============================================

    fun selectAsset(index: Int) {
        val currentState = _uiState.value
        if (currentState is EditorUiState.Success) {
            if (index in currentState.project.assets.indices) {
                _uiState.value = currentState.copy(selectedAssetIndex = index)
            }
        }
    }

    fun selectNextAsset() {
        val currentState = _uiState.value
        if (currentState is EditorUiState.Success) {
            val nextIndex = (currentState.selectedAssetIndex + 1) % currentState.project.assets.size
            _uiState.value = currentState.copy(selectedAssetIndex = nextIndex)
        }
    }

    fun selectPreviousAsset() {
        val currentState = _uiState.value
        if (currentState is EditorUiState.Success) {
            val prevIndex = if (currentState.selectedAssetIndex == 0) {
                currentState.project.assets.lastIndex
            } else {
                currentState.selectedAssetIndex - 1
            }
            _uiState.value = currentState.copy(selectedAssetIndex = prevIndex)
        }
    }

    // ============================================
    // ASSET REORDERING
    // ============================================

    fun moveAsset(fromIndex: Int, toIndex: Int) {
        val currentState = _uiState.value
        if (currentState is EditorUiState.Success) {
            viewModelScope.launch {
                val newAssets = reorderAssetsUseCase.move(
                    projectId = projectId,
                    assets = currentState.project.assets,
                    fromIndex = fromIndex,
                    toIndex = toIndex
                )

                // Update selected index if needed
                val newSelectedIndex = when {
                    currentState.selectedAssetIndex == fromIndex -> toIndex
                    fromIndex < currentState.selectedAssetIndex && toIndex >= currentState.selectedAssetIndex ->
                        currentState.selectedAssetIndex - 1
                    fromIndex > currentState.selectedAssetIndex && toIndex <= currentState.selectedAssetIndex ->
                        currentState.selectedAssetIndex + 1
                    else -> currentState.selectedAssetIndex
                }

                _uiState.value = currentState.copy(
                    project = currentState.project.copy(assets = newAssets),
                    selectedAssetIndex = newSelectedIndex
                )
            }
        }
    }

    // ============================================
    // ASSET ADD/REMOVE
    // ============================================

    /**
     * Add new assets to the project
     */
    fun addAssets(uris: List<Uri>) {
        if (uris.isEmpty()) return

        viewModelScope.launch {
            addAssetsUseCase(projectId, uris)
            // Project will be updated via Flow observation
        }
    }

    /**
     * Remove an asset from the project
     * Enforces minimum 2 images constraint
     * @return true if removed, false if blocked by constraint
     */
    fun removeAsset(assetId: String): Boolean {
        val currentState = _uiState.value
        if (currentState !is EditorUiState.Success) return false

        val assetCount = currentState.project.assets.size
        if (!removeAssetUseCase.canRemove(assetCount)) {
            return false
        }

        viewModelScope.launch {
            removeAssetUseCase(projectId, assetId, assetCount)
            // Project will be updated via Flow observation
        }
        return true
    }

    /**
     * Check if assets can be removed (more than minimum)
     */
    fun canRemoveAssets(): Boolean {
        val currentState = _uiState.value
        if (currentState !is EditorUiState.Success) return false
        return removeAssetUseCase.canRemove(currentState.project.assets.size)
    }

    // ============================================
    // SETTINGS
    // ============================================

    fun toggleSettingsPanel() {
        val currentState = _uiState.value
        if (currentState is EditorUiState.Success) {
            _uiState.value = currentState.copy(
                showSettingsPanel = !currentState.showSettingsPanel
            )
        }
    }

    fun updateTransitionSet(setId: String) {
        updateSettings { it.copy(transitionSetId = setId) }
    }

    fun updateTransitionDuration(durationMs: Long) {
        updateSettings { it.copy(transitionDurationMs = durationMs) }
    }

    fun updateOverlayFrame(frameId: String?) {
        updateSettings { it.copy(overlayFrameId = frameId) }
    }

    fun updateAudioTrack(trackId: String?) {
        android.util.Log.d("EditorViewModel", "updateAudioTrack called with: $trackId")
        updateSettings { it.copy(audioTrackId = trackId, customAudioUri = null) }
    }

    fun updateCustomAudio(uri: Uri?) {
        // Only clear audioTrackId when setting a custom audio (uri is not null)
        // When clearing custom audio (uri is null), keep the audioTrackId unchanged
        if (uri != null) {
            updateSettings { it.copy(customAudioUri = uri, audioTrackId = null) }
        } else {
            updateSettings { it.copy(customAudioUri = null) }
        }
    }

    fun updateAudioVolume(volume: Float) {
        updateSettings { it.copy(audioVolume = volume) }
    }

    fun updateAspectRatio(ratio: AspectRatio) {
        updateSettings { it.copy(aspectRatio = ratio) }
    }

    private fun updateSettings(update: (ProjectSettings) -> ProjectSettings) {
        val currentState = _uiState.value
        if (currentState is EditorUiState.Success) {
            val newSettings = update(currentState.project.settings)
            android.util.Log.d("EditorViewModel", "updateSettings: old audioTrackId=${currentState.project.settings.audioTrackId}, new audioTrackId=${newSettings.audioTrackId}")

            // Optimistically update UI immediately for responsive feel
            val updatedProject = currentState.project.copy(settings = newSettings)
            _uiState.value = currentState.copy(project = updatedProject)
            android.util.Log.d("EditorViewModel", "UI state updated optimistically")

            // Then persist to database (Room Flow will confirm the update)
            viewModelScope.launch {
                updateSettingsUseCase(projectId, newSettings)
                android.util.Log.d("EditorViewModel", "Settings saved to database")
            }
        } else {
            android.util.Log.w("EditorViewModel", "updateSettings called but state is not Success: $currentState")
        }
    }

    // ============================================
    // PLAYBACK (Real-time preview with CompositionPlayer)
    // ============================================

    /**
     * Toggle playback state - triggers play/pause on CompositionPlayer
     */
    fun togglePlayback() {
        val currentState = _uiState.value
        if (currentState is EditorUiState.Success) {
            _uiState.value = currentState.copy(isPlaying = !currentState.isPlaying)
        }
    }

    /**
     * Set playback state from player callback
     * Called when CompositionPlayer's playback state changes
     */
    fun setPlaybackState(isPlaying: Boolean) {
        val currentState = _uiState.value
        if (currentState is EditorUiState.Success) {
            _uiState.value = currentState.copy(isPlaying = isPlaying)
        }
    }

    /**
     * Stop playback
     */
    fun stopPlayback() {
        val currentState = _uiState.value
        if (currentState is EditorUiState.Success && currentState.isPlaying) {
            _uiState.value = currentState.copy(isPlaying = false)
        }
    }

    // ============================================
    // NAVIGATION
    // ============================================

    fun navigateBack() {
        viewModelScope.launch {
            _navigationEvent.send(EditorNavigationEvent.NavigateBack)
        }
    }

    fun navigateToPreview() {
        viewModelScope.launch {
            _navigationEvent.send(EditorNavigationEvent.NavigateToPreview(projectId))
        }
    }

    fun navigateToExport() {
        viewModelScope.launch {
            _navigationEvent.send(EditorNavigationEvent.NavigateToExport(projectId))
        }
    }
}
