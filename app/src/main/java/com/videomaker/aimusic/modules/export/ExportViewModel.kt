package com.videomaker.aimusic.modules.export

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.videomaker.aimusic.domain.repository.ExportProgress
import com.videomaker.aimusic.domain.repository.ExportRepository
import com.videomaker.aimusic.media.export.MediaStoreHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

// ============================================
// UI STATE
// ============================================

/**
 * ExportUiState - Sealed class state machine for export screen
 */
sealed class ExportUiState {
    /**
     * Export is being prepared (enqueued)
     */
    data object Preparing : ExportUiState()

    /**
     * Export is in progress
     *
     * @param progress Progress percentage (0-100)
     */
    data class Processing(val progress: Int) : ExportUiState()

    /**
     * Export completed successfully
     *
     * @param outputPath Path to the exported video file
     * @param savedToGallery Whether the video has been saved to gallery
     * @param saveError Error message if save to gallery failed
     */
    data class Success(
        val outputPath: String,
        val savedToGallery: Boolean = false,
        val saveError: String? = null
    ) : ExportUiState()

    /**
     * Export failed
     *
     * @param message Error message
     */
    data class Error(val message: String) : ExportUiState()

    /**
     * Export was cancelled by user
     */
    data object Cancelled : ExportUiState()
}

// ============================================
// NAVIGATION EVENTS
// ============================================

/**
 * ExportNavigationEvent - StateFlow-based navigation events (Google recommended)
 * UI observes navigationEvent StateFlow and calls onNavigationHandled() after navigating
 */
sealed class ExportNavigationEvent {
    data object NavigateBack : ExportNavigationEvent()
}

// ============================================
// VIEW MODEL
// ============================================

/**
 * ExportViewModel - Manages video export state
 *
 * Follows CLAUDE.md patterns:
 * - Sealed class state machine
 * - StateFlow-based navigation events (Google recommended)
 * - viewModelScope for coroutines
 */
class ExportViewModel(
    private val projectId: String,
    private val exportRepository: ExportRepository
) : ViewModel() {

    // ============================================
    // STATE
    // ============================================

    private val _uiState = MutableStateFlow<ExportUiState>(ExportUiState.Preparing)
    val uiState: StateFlow<ExportUiState> = _uiState.asStateFlow()

    // ============================================
    // NAVIGATION EVENTS (StateFlow-based - Google recommended)
    // ============================================

    private val _navigationEvent = MutableStateFlow<ExportNavigationEvent?>(null)
    val navigationEvent: StateFlow<ExportNavigationEvent?> = _navigationEvent.asStateFlow()

    // ============================================
    // INTERNAL STATE
    // ============================================

    private var workId: UUID? = null

    // ============================================
    // INITIALIZATION
    // ============================================

    init {
        startExport()
    }

    private fun startExport() {
        viewModelScope.launch {
            workId = exportRepository.startExport(projectId)
            observeProgress()
        }
    }

    private fun observeProgress() {
        viewModelScope.launch {
            val id = workId ?: return@launch

            exportRepository.observeExportProgress(id).collect { progress ->
                _uiState.value = when (progress) {
                    is ExportProgress.Preparing -> ExportUiState.Preparing
                    is ExportProgress.Processing -> ExportUiState.Processing(progress.percent)
                    is ExportProgress.Success -> ExportUiState.Success(progress.outputPath)
                    is ExportProgress.Error -> ExportUiState.Error(progress.message)
                    is ExportProgress.Cancelled -> ExportUiState.Cancelled
                }
            }
        }
    }

    // ============================================
    // USER ACTIONS
    // ============================================

    /**
     * Cancel the ongoing export
     */
    fun cancelExport() {
        workId?.let { exportRepository.cancelExport(it) }
    }

    /**
     * Retry a failed export
     */
    fun retryExport() {
        _uiState.value = ExportUiState.Preparing
        startExport()
    }

    /**
     * Navigate back to editor
     */
    fun navigateBack() {
        _navigationEvent.value = ExportNavigationEvent.NavigateBack
    }

    /**
     * Called by UI after navigation is handled - clears the event
     */
    fun onNavigationHandled() {
        _navigationEvent.value = null
    }

    /**
     * Save the exported video to device gallery
     */
    fun saveToGallery(context: Context) {
        val currentState = _uiState.value
        if (currentState !is ExportUiState.Success) return
        if (currentState.savedToGallery) return // Already saved

        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                MediaStoreHelper.saveVideoToGallery(
                    context = context,
                    videoFile = File(currentState.outputPath),
                    displayName = MediaStoreHelper.generateDisplayName(projectId)
                )
            }

            _uiState.value = when (result) {
                is MediaStoreHelper.SaveResult.Success -> {
                    currentState.copy(savedToGallery = true, saveError = null)
                }
                is MediaStoreHelper.SaveResult.Error -> {
                    currentState.copy(savedToGallery = false, saveError = result.message)
                }
            }
        }
    }
}
