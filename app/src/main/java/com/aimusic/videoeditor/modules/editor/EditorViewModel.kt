package com.aimusic.videoeditor.modules.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.net.Uri
import com.aimusic.videoeditor.domain.model.Asset
import com.aimusic.videoeditor.domain.model.AspectRatio
import com.aimusic.videoeditor.domain.model.Project
import com.aimusic.videoeditor.domain.model.ProjectSettings
import com.aimusic.videoeditor.domain.usecase.AddAssetsUseCase
import com.aimusic.videoeditor.domain.usecase.GetProjectUseCase
import com.aimusic.videoeditor.domain.usecase.RemoveAssetUseCase
import com.aimusic.videoeditor.domain.usecase.ReorderAssetsUseCase
import com.aimusic.videoeditor.domain.usecase.UpdateProjectSettingsUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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
        val showSettingsPanel: Boolean = false,
        // Pending settings - changes made but not yet applied
        // When null, no pending changes exist
        val pendingSettings: ProjectSettings? = null,
        // Navigation event - StateFlow-based (Google recommended pattern)
        // UI observes this and calls onNavigationHandled() after navigating
        val navigationEvent: EditorNavigationEvent? = null,
        // Playback position tracking for seekbar
        val currentPositionMs: Long = 0L,
        val durationMs: Long = 0L,
        // Pending seek request (null = no pending seek)
        val seekToPosition: Long? = null,
        // Scrub position for frame preview while dragging (no resume after)
        val scrubToPosition: Long? = null,
        // Track if video was playing before seek (to restore after)
        val wasPlayingBeforeSeek: Boolean = false
    ) : EditorUiState() {
        /** True if there are uncommitted setting changes */
        val hasPendingChanges: Boolean get() = pendingSettings != null

        /** Settings to display in UI (pending if exists, otherwise current) */
        val displaySettings: ProjectSettings get() = pendingSettings ?: project.settings
    }

    data class Error(val message: String) : EditorUiState()
}

// ============================================
// NAVIGATION EVENTS
// ============================================

/**
 * EditorNavigationEvent - StateFlow-based navigation events (Google recommended)
 * UI observes navigationEvent in UiState and calls onNavigationHandled() after navigating
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
 * - StateFlow-based navigation events (Google recommended)
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
    // SETTINGS (Pending pattern - changes are staged until Apply)
    // ============================================

    fun toggleSettingsPanel() {
        val currentState = _uiState.value
        if (currentState is EditorUiState.Success) {
            _uiState.value = currentState.copy(
                showSettingsPanel = !currentState.showSettingsPanel
            )
        }
    }

    /**
     * Close settings panel (used when settings handles its own discard/apply)
     */
    fun closeSettingsPanel() {
        val currentState = _uiState.value
        if (currentState is EditorUiState.Success) {
            _uiState.value = currentState.copy(showSettingsPanel = false)
        }
    }

    fun updateEffectSet(effectSetId: String?) {
        updatePendingSettings { it.copy(effectSetId = effectSetId) }
    }

    fun updateImageDuration(durationMs: Long) {
        updatePendingSettings { it.copy(imageDurationMs = durationMs) }
    }

    fun updateTransitionPercentage(percentage: Int) {
        updatePendingSettings { it.copy(transitionPercentage = percentage) }
    }

    fun updateOverlayFrame(frameId: String?) {
        updatePendingSettings { it.copy(overlayFrameId = frameId) }
    }

    fun updateAudioTrack(trackId: String?) {
        android.util.Log.d("EditorViewModel", "updateAudioTrack called with: $trackId")
        updatePendingSettings { it.copy(audioTrackId = trackId, customAudioUri = null) }
    }

    fun updateCustomAudio(uri: Uri?) {
        if (uri != null) {
            updatePendingSettings { it.copy(customAudioUri = uri, audioTrackId = null) }
        } else {
            updatePendingSettings { it.copy(customAudioUri = null) }
        }
    }

    fun updateAudioVolume(volume: Float) {
        updatePendingSettings { it.copy(audioVolume = volume) }
    }

    fun updateAspectRatio(ratio: AspectRatio) {
        updatePendingSettings { it.copy(aspectRatio = ratio) }
    }

    /**
     * Stage a setting change (does NOT trigger reprocessing)
     * Changes are only applied when applySettings() is called
     */
    private fun updatePendingSettings(update: (ProjectSettings) -> ProjectSettings) {
        val currentState = _uiState.value
        if (currentState is EditorUiState.Success) {
            // Start from current pending settings, or current project settings if no pending
            val baseSettings = currentState.pendingSettings ?: currentState.project.settings
            val newPendingSettings = update(baseSettings)

            android.util.Log.d("EditorViewModel", "Staging setting change (pending)")
            _uiState.value = currentState.copy(pendingSettings = newPendingSettings)
        }
    }

    /**
     * Apply all pending settings - triggers video reprocessing
     * Called when user taps "Apply" button
     */
    fun applySettings() {
        val currentState = _uiState.value
        if (currentState is EditorUiState.Success && currentState.pendingSettings != null) {
            val newSettings = currentState.pendingSettings
            android.util.Log.d("EditorViewModel", "Applying settings: $newSettings")

            // Update project with new settings and clear pending
            val updatedProject = currentState.project.copy(settings = newSettings)
            _uiState.value = currentState.copy(
                project = updatedProject,
                pendingSettings = null  // Clear pending after apply
            )

            // Persist to database
            viewModelScope.launch {
                updateSettingsUseCase(projectId, newSettings)
                android.util.Log.d("EditorViewModel", "Settings applied and saved to database")
            }
        }
    }

    /**
     * Discard pending settings and revert to current project settings
     */
    fun discardPendingSettings() {
        val currentState = _uiState.value
        if (currentState is EditorUiState.Success) {
            android.util.Log.d("EditorViewModel", "Discarding pending settings")
            _uiState.value = currentState.copy(pendingSettings = null)
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
     * Stop playback (used during seeking)
     * Saves current playing state to restore after seek
     */
    fun stopPlayback() {
        val currentState = _uiState.value
        if (currentState is EditorUiState.Success) {
            _uiState.value = currentState.copy(
                wasPlayingBeforeSeek = currentState.isPlaying,
                isPlaying = false
            )
        }
    }

    /**
     * Resume playback after seeking if video was playing before
     */
    fun resumePlaybackAfterSeek() {
        val currentState = _uiState.value
        if (currentState is EditorUiState.Success && currentState.wasPlayingBeforeSeek) {
            _uiState.value = currentState.copy(
                isPlaying = true,
                wasPlayingBeforeSeek = false
            )
        }
    }

    /**
     * Update playback position from player callback
     * Called periodically by VideoPreviewPlayer's position tracker
     */
    fun updatePlaybackPosition(currentMs: Long, durationMs: Long) {
        val currentState = _uiState.value
        if (currentState is EditorUiState.Success) {
            // Only update if values actually changed (avoid unnecessary recomposition)
            if (currentState.currentPositionMs != currentMs || currentState.durationMs != durationMs) {
                _uiState.value = currentState.copy(
                    currentPositionMs = currentMs,
                    durationMs = durationMs
                )
            }
        }
    }

    /**
     * Request seek to a specific position
     * VideoPreviewPlayer will handle the actual seek and call clearSeekRequest
     */
    fun seekTo(positionMs: Long) {
        val currentState = _uiState.value
        if (currentState is EditorUiState.Success) {
            _uiState.value = currentState.copy(seekToPosition = positionMs)
        }
    }

    /**
     * Scrub to a position for frame preview while dragging
     * Unlike seekTo, this doesn't trigger resume after completion
     */
    fun scrubTo(positionMs: Long) {
        val currentState = _uiState.value
        if (currentState is EditorUiState.Success) {
            _uiState.value = currentState.copy(scrubToPosition = positionMs)
        }
    }

    /**
     * Clear scrub request after it's been handled by the player
     * No resume logic - just clears the position
     */
    fun clearScrubRequest() {
        val currentState = _uiState.value
        if (currentState is EditorUiState.Success) {
            _uiState.value = currentState.copy(scrubToPosition = null)
        }
    }

    /**
     * Clear pending seek request after it's been handled by the player
     * Also resumes playback if the video was playing before the seek
     */
    fun clearSeekRequest() {
        val currentState = _uiState.value
        if (currentState is EditorUiState.Success) {
            // Resume playback if video was playing before seek
            val shouldResume = currentState.wasPlayingBeforeSeek
            _uiState.value = currentState.copy(
                seekToPosition = null,
                isPlaying = if (shouldResume) true else currentState.isPlaying,
                wasPlayingBeforeSeek = false
            )
            if (shouldResume) {
                android.util.Log.d("EditorViewModel", "Resuming playback after seek complete")
            }
        }
    }

    // ============================================
    // NAVIGATION (StateFlow-based - Google recommended)
    // ============================================

    /**
     * Trigger navigation back - UI will observe and navigate
     */
    fun navigateBack() {
        _uiState.update { state ->
            if (state is EditorUiState.Success) {
                state.copy(navigationEvent = EditorNavigationEvent.NavigateBack)
            } else state
        }
    }

    /**
     * Trigger navigation to preview - UI will observe and navigate
     */
    fun navigateToPreview() {
        _uiState.update { state ->
            if (state is EditorUiState.Success) {
                state.copy(navigationEvent = EditorNavigationEvent.NavigateToPreview(projectId))
            } else state
        }
    }

    /**
     * Trigger navigation to export - UI will observe and navigate
     */
    fun navigateToExport() {
        _uiState.update { state ->
            if (state is EditorUiState.Success) {
                state.copy(navigationEvent = EditorNavigationEvent.NavigateToExport(projectId))
            } else state
        }
    }

    /**
     * Called by UI after navigation is handled - clears the event
     * This prevents re-navigation on configuration changes
     */
    fun onNavigationHandled() {
        _uiState.update { state ->
            if (state is EditorUiState.Success) {
                state.copy(navigationEvent = null)
            } else state
        }
    }
}
