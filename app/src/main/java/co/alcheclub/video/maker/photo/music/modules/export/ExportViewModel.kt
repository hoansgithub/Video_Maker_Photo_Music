package co.alcheclub.video.maker.photo.music.modules.export

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.alcheclub.video.maker.photo.music.domain.repository.ExportProgress
import co.alcheclub.video.maker.photo.music.domain.repository.ExportRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
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
     */
    data class Success(val outputPath: String) : ExportUiState()

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
 * ExportNavigationEvent - Channel-based navigation events
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
 * - Channel for navigation events
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
    // NAVIGATION EVENTS
    // ============================================

    private val _navigationEvent = Channel<ExportNavigationEvent>(Channel.BUFFERED)
    val navigationEvent = _navigationEvent.receiveAsFlow()

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
        viewModelScope.launch {
            _navigationEvent.send(ExportNavigationEvent.NavigateBack)
        }
    }
}
