package com.videomaker.aimusic.modules.export

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.videomaker.aimusic.domain.model.AspectRatio
import com.videomaker.aimusic.domain.model.VideoQuality
import com.videomaker.aimusic.domain.model.VideoTemplate
import com.videomaker.aimusic.domain.repository.ExportProgress
import com.videomaker.aimusic.domain.repository.ExportRepository
import com.videomaker.aimusic.domain.repository.ProjectRepository
import com.videomaker.aimusic.domain.repository.TemplateRepository
import com.videomaker.aimusic.media.export.MediaStoreHelper
import com.videomaker.aimusic.ui.components.ProcessToastState
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
    data object NavigateToHomeMyVideos : ExportNavigationEvent()
    data class NavigateToTemplateDetail(val templateId: String) : ExportNavigationEvent()
}

/**
 * FeaturedTemplatesState - State for the "Try Another Templates" section
 */
sealed class FeaturedTemplatesState {
    data object Loading : FeaturedTemplatesState()
    data class Success(val templates: List<VideoTemplate>) : FeaturedTemplatesState()
    data object Error : FeaturedTemplatesState()
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
    private val exportRepository: ExportRepository,
    private val projectRepository: ProjectRepository,
    private val templateRepository: TemplateRepository
) : ViewModel() {

    // ============================================
    // STATE
    // ============================================

    private val _uiState = MutableStateFlow<ExportUiState>(ExportUiState.Preparing)
    val uiState: StateFlow<ExportUiState> = _uiState.asStateFlow()

    // Project data for thumbnail
    private val _thumbnailUri = MutableStateFlow<Uri?>(null)
    val thumbnailUri: StateFlow<Uri?> = _thumbnailUri.asStateFlow()

    // Project aspect ratio for correct video display
    private val _aspectRatio = MutableStateFlow(AspectRatio.RATIO_9_16)
    val aspectRatio: StateFlow<AspectRatio> = _aspectRatio.asStateFlow()

    // Current export quality
    private val _currentQuality = MutableStateFlow(VideoQuality.DEFAULT)
    val currentQuality: StateFlow<VideoQuality> = _currentQuality.asStateFlow()

    // Featured templates state
    private val _featuredTemplatesState = MutableStateFlow<FeaturedTemplatesState>(FeaturedTemplatesState.Loading)
    val featuredTemplatesState: StateFlow<FeaturedTemplatesState> = _featuredTemplatesState.asStateFlow()

    // Save to gallery toast state
    private val _saveToastState = MutableStateFlow<ProcessToastState?>(null)
    val saveToastState: StateFlow<ProcessToastState?> = _saveToastState.asStateFlow()

    // ============================================
    // NAVIGATION EVENTS (StateFlow-based - Google recommended)
    // ============================================

    private val _navigationEvent = MutableStateFlow<ExportNavigationEvent?>(null)
    val navigationEvent: StateFlow<ExportNavigationEvent?> = _navigationEvent.asStateFlow()

    // ============================================
    // INTERNAL STATE
    // ============================================

    private var workId: UUID? = null
    // Single job for export + progress observation — cancelled on retry to prevent
    // parallel observers watching different work items simultaneously.
    private var exportJob: kotlinx.coroutines.Job? = null

    // ============================================
    // INITIALIZATION
    // ============================================

    init {
        loadProjectData()
        startExport()
        loadFeaturedTemplates()
    }

    private fun loadProjectData() {
        viewModelScope.launch {
            val project = projectRepository.getProject(projectId)
            _thumbnailUri.value = project?.thumbnailUri ?: project?.assets?.firstOrNull()?.uri
            _aspectRatio.value = project?.settings?.aspectRatio ?: AspectRatio.RATIO_9_16
        }
    }

    private fun startExport() {
        exportJob?.cancel()
        exportJob = viewModelScope.launch {
            // startExport and observeProgress run sequentially in one coroutine —
            // workId is guaranteed to be set before observeProgress reads it.
            val id = exportRepository.startExport(projectId)
            workId = id
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
     * Change export quality and re-export
     */
    fun changeQuality(quality: VideoQuality) {
        _currentQuality.value = quality
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
     * Navigate to Home screen with My Videos tab (tab index 2)
     * Resets savedToGallery state so button is enabled on next export
     */
    fun navigateToHomeMyVideos() {
        // Reset saved state so button is enabled next time
        val currentState = _uiState.value
        if (currentState is ExportUiState.Success && currentState.savedToGallery) {
            _uiState.value = currentState.copy(savedToGallery = false)
        }

        // Clear any active toast
        _saveToastState.value = null

        _navigationEvent.value = ExportNavigationEvent.NavigateToHomeMyVideos
    }

    /**
     * Called by UI after navigation is handled - clears the event
     */
    fun onNavigationHandled() {
        _navigationEvent.value = null
    }

    /**
     * Called by UI after toast is dismissed - clears the toast state
     */
    fun onSaveToastDismissed() {
        _saveToastState.value = null
    }

    /**
     * Load featured templates for "Try Another Templates" section
     * Gets 6 random templates, excluding the current project's template if available
     */
    private fun loadFeaturedTemplates() {
        viewModelScope.launch {
            _featuredTemplatesState.value = FeaturedTemplatesState.Loading

            templateRepository.getFeaturedTemplates(limit = 6)
                .onSuccess { templates ->
                    // TODO: Filter out current template ID if we have it
                    _featuredTemplatesState.value = FeaturedTemplatesState.Success(templates)
                }
                .onFailure {
                    _featuredTemplatesState.value = FeaturedTemplatesState.Error
                }
        }
    }

    /**
     * Handle template click - navigate to template detail
     */
    fun onTemplateClick(templateId: String) {
        _navigationEvent.value = ExportNavigationEvent.NavigateToTemplateDetail(templateId)
    }

    /**
     * Save the exported video to device gallery
     *
     * @param applicationContext Application context (NOT Activity context) - passed explicitly
     *   to prevent accidental memory leaks from capturing Activity context
     * @param loadingMessage Localized message for loading state
     * @param successMessage Localized message for success state
     * @param errorMessage Localized message for error state
     */
    fun saveToGallery(
        applicationContext: Context,
        loadingMessage: String,
        successMessage: String,
        errorMessage: String
    ) {
        val currentState = _uiState.value
        if (currentState !is ExportUiState.Success) return
        // Allow re-downloading - user may want multiple copies or re-download if deleted

        viewModelScope.launch {
            // Show loading toast
            _saveToastState.value = ProcessToastState.Loading(loadingMessage)

            val result = withContext(Dispatchers.IO) {
                MediaStoreHelper.saveVideoToGallery(
                    context = applicationContext,
                    videoFile = File(currentState.outputPath),
                    displayName = MediaStoreHelper.generateDisplayName(projectId)
                )
            }

            when (result) {
                is MediaStoreHelper.SaveResult.Success -> {
                    _uiState.value = currentState.copy(savedToGallery = true, saveError = null)
                    _saveToastState.value = ProcessToastState.Success(successMessage)
                }
                is MediaStoreHelper.SaveResult.Error -> {
                    _uiState.value = currentState.copy(savedToGallery = false, saveError = result.message)
                    _saveToastState.value = ProcessToastState.Error(errorMessage)
                }
            }
        }
    }
}
