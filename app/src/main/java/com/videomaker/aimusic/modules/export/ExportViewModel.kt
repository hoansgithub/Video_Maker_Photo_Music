package com.videomaker.aimusic.modules.export

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.videomaker.aimusic.core.analytics.Analytics
import com.videomaker.aimusic.core.analytics.AnalyticsEvent
import com.videomaker.aimusic.domain.model.AspectRatio
import com.videomaker.aimusic.domain.model.Project
import com.videomaker.aimusic.domain.model.VideoQuality
import com.videomaker.aimusic.domain.model.VideoTemplate
import com.videomaker.aimusic.core.rating.RatingTriggerManager
import com.videomaker.aimusic.domain.repository.ExportProgress
import com.videomaker.aimusic.domain.repository.ExportRepository
import com.videomaker.aimusic.domain.repository.ProjectRepository
import com.videomaker.aimusic.domain.repository.TemplateRepository
import com.videomaker.aimusic.domain.usecase.SubmitFeedbackUseCase
import com.videomaker.aimusic.media.export.MediaStoreHelper
import com.videomaker.aimusic.modules.rate.RatingStep
import com.videomaker.aimusic.ui.components.ProcessToastState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.math.roundToInt

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
    private val initialQuality: VideoQuality = VideoQuality.DEFAULT,
    private val exportRepository: ExportRepository,
    private val projectRepository: ProjectRepository,
    private val templateRepository: TemplateRepository,
    private val ratingTriggerManager: RatingTriggerManager,
    private val submitFeedbackUseCase: SubmitFeedbackUseCase
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

    // Current export quality (initialized from editor selection)
    private val _currentQuality = MutableStateFlow(initialQuality)
    val currentQuality: StateFlow<VideoQuality> = _currentQuality.asStateFlow()

    // Featured templates state
    private val _featuredTemplatesState = MutableStateFlow<FeaturedTemplatesState>(FeaturedTemplatesState.Loading)
    val featuredTemplatesState: StateFlow<FeaturedTemplatesState> = _featuredTemplatesState.asStateFlow()

    // Save to gallery toast state
    private val _saveToastState = MutableStateFlow<ProcessToastState?>(null)
    val saveToastState: StateFlow<ProcessToastState?> = _saveToastState.asStateFlow()

    // ============================================
    // RATING STATE
    // ============================================

    private val _ratingStep = MutableStateFlow(RatingStep.None)
    val ratingStep: StateFlow<RatingStep> = _ratingStep.asStateFlow()

    // Private tracking for feedback submission
    private var satisfactionResponse: String? = null
    private var lastStarRating: Int = 0

    // Play Store launch event — one-time action requiring Activity context in composable
    private val _launchPlayStoreEvent = Channel<Unit>(Channel.BUFFERED)
    val launchPlayStoreEvent = _launchPlayStoreEvent.receiveAsFlow()

    // ============================================
    // NAVIGATION EVENTS (StateFlow-based - Google recommended)
    // ============================================

    private val _navigationEvent = MutableStateFlow<ExportNavigationEvent?>(null)
    val navigationEvent: StateFlow<ExportNavigationEvent?> = _navigationEvent.asStateFlow()

    // ============================================
    // INTERNAL STATE
    // ============================================

    private var workId: UUID? = null
    private var currentProjectSnapshot: Project? = null
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
        observeRatingTrigger()
    }

    private fun loadProjectData() {
        viewModelScope.launch {
            val project = projectRepository.getProject(projectId)
            currentProjectSnapshot = project
            _thumbnailUri.value = project?.thumbnailUri ?: project?.assets?.firstOrNull()?.uri
            _aspectRatio.value = project?.settings?.aspectRatio ?: AspectRatio.RATIO_9_16
        }
    }

    private fun startExport() {
        exportJob?.cancel()
        exportJob = viewModelScope.launch {
            val project = currentProjectSnapshot ?: projectRepository.getProject(projectId)
            currentProjectSnapshot = project
            Analytics.trackVideoGenerate(
                videoId = projectId,
                templateId = project?.settings?.effectSetId,
                songId = project?.settings?.musicSongId?.toString(),
                quality = _currentQuality.value.displayName,
                duration = project?.totalDurationMs,
                ratioSize = project?.settings?.aspectRatio?.toAnalyticsRatioSize(),
                volume = project?.settings?.audioVolume?.times(100f)?.roundToInt(),
                mediaQuantity = project?.assets?.size
            )
            // startExport and observeProgress run sequentially in one coroutine —
            // workId is guaranteed to be set before observeProgress reads it.
            val id = exportRepository.startExport(projectId)
            workId = id
            exportRepository.observeExportProgress(id).collect { progress ->
                _uiState.value = when (progress) {
                    is ExportProgress.Preparing -> ExportUiState.Preparing
                    is ExportProgress.Processing -> ExportUiState.Processing(progress.percent)
                    is ExportProgress.Success -> {
                        Analytics.trackVideoGenerateComplete(
                            videoId = projectId,
                            templateId = project?.settings?.effectSetId,
                            songId = project?.settings?.musicSongId?.toString(),
                            quality = _currentQuality.value.displayName,
                            duration = project?.totalDurationMs,
                            ratioSize = project?.settings?.aspectRatio?.toAnalyticsRatioSize(),
                            volume = project?.settings?.audioVolume?.times(100f)?.roundToInt(),
                            mediaQuantity = project?.assets?.size
                        )
                        Analytics.trackVideoExportComplete(
                            videoId = projectId,
                            templateId = project?.settings?.effectSetId,
                            songId = project?.settings?.musicSongId?.toString(),
                            quality = _currentQuality.value.displayName,
                            duration = project?.totalDurationMs,
                            ratioSize = project?.settings?.aspectRatio?.toAnalyticsRatioSize(),
                            volume = project?.settings?.audioVolume?.times(100f)?.roundToInt(),
                            mediaQuantity = project?.assets?.size
                        )
                        ratingTriggerManager.onVideoCreated()
                        ExportUiState.Success(progress.outputPath)
                    }
                    is ExportProgress.Error -> ExportUiState.Error(progress.message)
                    is ExportProgress.Cancelled -> ExportUiState.Cancelled
                }
            }
        }
    }

    // ============================================
    // RATING ACTIONS
    // ============================================

    /** User tapped "Not Really" on Satisfaction popup — dismiss without further action */
    fun onNotReally() {
        satisfactionResponse = "not_really"
        Analytics.trackRateClick(option = AnalyticsEvent.Value.Option.BAD)
        _ratingStep.value = RatingStep.None
    }

    /** User tapped "Good" on Satisfaction popup — proceed to star rating */
    fun onGood() {
        satisfactionResponse = "good"
        Analytics.trackRateClick(option = AnalyticsEvent.Value.Option.GOOD)
        _ratingStep.value = RatingStep.Stars
    }

    /** User tapped "Rate Us" with 1-3 stars — proceed to feedback popup */
    fun onLowRating(stars: Int) {
        lastStarRating = stars
        Analytics.trackRateStar(stars, option = AnalyticsEvent.Value.Option.BAD)
        Analytics.trackRateRateUsButtonClick(option = AnalyticsEvent.Value.Option.GOOD)
        Analytics.trackRateFlowContinue(option = AnalyticsEvent.Value.Option.BAD, star = stars)
        Analytics.trackRateReason(option = AnalyticsEvent.Value.Option.BAD, star = stars)
        _ratingStep.value = RatingStep.Feedback
    }

    /** User tapped "Rate Us" with 4-5 stars — open Play Store, mark completed */
    fun onHighRating(stars: Int) {
        lastStarRating = stars
        Analytics.trackRateStar(stars, option = AnalyticsEvent.Value.Option.GOOD)
        Analytics.trackRateRateUsButtonClick(option = AnalyticsEvent.Value.Option.GOOD)
        Analytics.trackRateFlowContinue(option = AnalyticsEvent.Value.Option.GOOD, star = stars)
        Analytics.trackRateDone(option = AnalyticsEvent.Value.Option.GOOD, star = stars)
        _ratingStep.value = RatingStep.None
        ratingTriggerManager.onRatingCompleted()
        viewModelScope.launch {
            _launchPlayStoreEvent.send(Unit)
        }
    }

    /** User submitted feedback text — send to Supabase, mark completed */
    fun onFeedbackSubmit(feedback: String) {
        val star = if (lastStarRating > 0) lastStarRating else 0
        Analytics.trackReasonClick(des = feedback, option = AnalyticsEvent.Value.Option.BAD)
        Analytics.trackRateSubmit(
            des = feedback,
            option = AnalyticsEvent.Value.Option.BAD,
            star = star
        )
        if (star > 0) {
            Analytics.trackRateDone(option = AnalyticsEvent.Value.Option.BAD, star = star)
        }
        _ratingStep.value = RatingStep.None
        ratingTriggerManager.onRatingCompleted()
        viewModelScope.launch(Dispatchers.Default) {
            submitFeedbackUseCase(
                feedbackText = feedback,
                satisfactionResponse = satisfactionResponse,
                starRating = if (lastStarRating > 0) lastStarRating else null
            )
        }
    }

    /** User dismissed any popup via X button */
    fun onRatingDismiss() {
        _ratingStep.value = RatingStep.None
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

    private fun observeRatingTrigger() {
        viewModelScope.launch {
            ratingTriggerManager.showRatingPopup.collect {
                if (_ratingStep.value == RatingStep.None) {
                    Analytics.trackRateView(
                        logicRender = "system",
                        location = AnalyticsEvent.Value.Location.RESULT
                    )
                    _ratingStep.value = RatingStep.Satisfaction
                    ratingTriggerManager.onRatingShown()
                }
            }
        }
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
        val templateName = (_featuredTemplatesState.value as? FeaturedTemplatesState.Success)
            ?.templates
            ?.firstOrNull { it.id == templateId }
            ?.name
            ?: "unknown"
        Analytics.trackTemplateClick(
            templateId = templateId,
            templateName = templateName,
            location = "result_recommendation"
        )
        _navigationEvent.value = ExportNavigationEvent.NavigateToTemplateDetail(templateId)
    }

    fun trackShareAction() {
        val project = currentProjectSnapshot
        Analytics.trackVideoShare(
            videoId = projectId,
            templateId = project?.settings?.effectSetId,
            songId = project?.settings?.musicSongId?.toString(),
            quality = _currentQuality.value.displayName,
            duration = project?.totalDurationMs,
            ratioSize = project?.settings?.aspectRatio?.toAnalyticsRatioSize(),
            volume = project?.settings?.audioVolume?.times(100f)?.roundToInt(),
            mediaQuantity = project?.assets?.size,
            location = AnalyticsEvent.Value.Location.RESULT
        )
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
            val project = currentProjectSnapshot
            Analytics.trackVideoDownload(
                videoId = projectId,
                templateId = project?.settings?.effectSetId,
                songId = project?.settings?.musicSongId?.toString(),
                quality = _currentQuality.value.displayName,
                duration = project?.totalDurationMs,
                ratioSize = project?.settings?.aspectRatio?.toAnalyticsRatioSize(),
                volume = project?.settings?.audioVolume?.times(100f)?.roundToInt(),
                mediaQuantity = project?.assets?.size,
                location = AnalyticsEvent.Value.Location.RESULT
            )
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

private fun AspectRatio.toAnalyticsRatioSize(): String = when (this) {
    AspectRatio.RATIO_16_9 -> "16:9"
    AspectRatio.RATIO_9_16 -> "9:16"
    AspectRatio.RATIO_4_5 -> "4:5"
    AspectRatio.RATIO_1_1 -> "1:1"
}
