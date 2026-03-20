package com.videomaker.aimusic.modules.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.net.Uri
import com.videomaker.aimusic.domain.model.Asset
import com.videomaker.aimusic.domain.model.AspectRatio
import com.videomaker.aimusic.domain.model.Project
import com.videomaker.aimusic.domain.model.ProjectSettings
import com.videomaker.aimusic.domain.model.VideoQuality
import com.videomaker.aimusic.domain.usecase.AddAssetsUseCase
import com.videomaker.aimusic.domain.usecase.GetProjectUseCase
import com.videomaker.aimusic.domain.usecase.RemoveAssetUseCase
import com.videomaker.aimusic.domain.usecase.ReorderAssetsUseCase
import com.videomaker.aimusic.domain.usecase.UpdateProjectSettingsUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// ============================================
// UI STATE
// ============================================

sealed class EditorUiState {
    data object Loading : EditorUiState()

    data class Success(
        val project: Project,
        val selectedAssetIndex: Int = 0,
        val isPlaying: Boolean = false,
        val showSettingsPanel: Boolean = false,
        val pendingSettings: ProjectSettings? = null,
        val currentPositionMs: Long = 0L,
        val durationMs: Long = 0L,
        val seekToPosition: Long? = null,
        val scrubToPosition: Long? = null,
        val wasPlayingBeforeSeek: Boolean = false,
        val selectedQuality: VideoQuality = VideoQuality.DEFAULT
    ) : EditorUiState() {
        val hasPendingChanges: Boolean get() = pendingSettings != null
        val displaySettings: ProjectSettings get() = pendingSettings ?: project.settings
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
    private val projectId: String,
    private val getProjectUseCase: GetProjectUseCase,
    private val updateSettingsUseCase: UpdateProjectSettingsUseCase,
    private val reorderAssetsUseCase: ReorderAssetsUseCase,
    private val addAssetsUseCase: AddAssetsUseCase,
    private val removeAssetUseCase: RemoveAssetUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<EditorUiState>(EditorUiState.Loading)
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    // Navigation events are separate from UI state — never embedded in high-frequency state
    private val _navigationEvent = MutableStateFlow<EditorNavigationEvent?>(null)
    val navigationEvent: StateFlow<EditorNavigationEvent?> = _navigationEvent.asStateFlow()

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
                        val prev = currentState as? EditorUiState.Success
                        val selectedIndex = prev?.selectedAssetIndex
                            ?.coerceIn(0, project.assets.lastIndex.coerceAtLeast(0)) ?: 0
                        _uiState.value = EditorUiState.Success(
                            project = project,
                            selectedAssetIndex = selectedIndex,
                            isPlaying = prev?.isPlaying ?: false,
                            showSettingsPanel = prev?.showSettingsPanel ?: false,
                            pendingSettings = prev?.pendingSettings,
                            currentPositionMs = prev?.currentPositionMs ?: 0L,
                            durationMs = prev?.durationMs ?: 0L,
                            seekToPosition = prev?.seekToPosition,
                            scrubToPosition = prev?.scrubToPosition,
                            wasPlayingBeforeSeek = prev?.wasPlayingBeforeSeek ?: false,
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

    fun moveAsset(fromIndex: Int, toIndex: Int) {
        val currentState = _uiState.value
        if (currentState is EditorUiState.Success) {
            viewModelScope.launch {
                val assets = currentState.project.assets.toMutableList()
                val asset = assets.removeAt(fromIndex)
                assets.add(toIndex, asset)
                reorderAssetsUseCase(projectId, assets)
            }
        }
    }

    fun addAssets(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            addAssetsUseCase(projectId, uris)
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
        viewModelScope.launch {
            removeAssetUseCase(projectId, assetId)
        }
        return true
    }

    fun canRemoveAssets(): Boolean {
        val currentState = _uiState.value
        if (currentState !is EditorUiState.Success) return false
        return currentState.project.assets.size > 2
    }

    fun toggleSettingsPanel() {
        val currentState = _uiState.value
        if (currentState is EditorUiState.Success) {
            _uiState.value = currentState.copy(showSettingsPanel = !currentState.showSettingsPanel)
        }
    }

    fun closeSettingsPanel() {
        val currentState = _uiState.value
        if (currentState is EditorUiState.Success) {
            _uiState.value = currentState.copy(showSettingsPanel = false)
        }
    }

    fun updateQuality(quality: VideoQuality) {
        val currentState = _uiState.value
        if (currentState is EditorUiState.Success) {
            _uiState.value = currentState.copy(selectedQuality = quality)
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

    fun updateMusicSong(songId: Long?, songUrl: String?) {
        updatePendingSettings { it.copy(musicSongId = songId, musicSongUrl = songUrl, customAudioUri = null) }
    }

    fun updateCustomAudio(uri: Uri?) {
        if (uri != null) {
            updatePendingSettings { it.copy(customAudioUri = uri, musicSongId = null, musicSongUrl = null) }
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
            val updatedProject = currentState.project.copy(settings = newSettings)
            _uiState.value = currentState.copy(project = updatedProject, pendingSettings = null)
            viewModelScope.launch {
                updateSettingsUseCase(projectId, newSettings)
            }
        }
    }

    fun discardPendingSettings() {
        val currentState = _uiState.value
        if (currentState is EditorUiState.Success) {
            _uiState.value = currentState.copy(pendingSettings = null)
        }
    }

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

    fun resumePlaybackAfterSeek() {
        val currentState = _uiState.value
        if (currentState is EditorUiState.Success && currentState.wasPlayingBeforeSeek) {
            _uiState.value = currentState.copy(isPlaying = true, wasPlayingBeforeSeek = false)
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

    fun navigateToPreview() {
        _navigationEvent.value = EditorNavigationEvent.NavigateToPreview(projectId)
    }

    fun navigateToExport() {
        _navigationEvent.value = EditorNavigationEvent.NavigateToExport(projectId)
    }

    /** Called by UI after navigation is handled — clears the event. */
    fun onNavigationHandled() {
        _navigationEvent.value = null
    }
}