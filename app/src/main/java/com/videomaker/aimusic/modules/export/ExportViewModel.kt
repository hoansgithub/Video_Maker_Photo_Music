package com.videomaker.aimusic.modules.export

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.alcheclub.lib.acccore.ads.loader.AdsLoaderService
import com.videomaker.aimusic.core.ads.InterstitialAdHelperExt
import com.videomaker.aimusic.core.ads.RewardedAdController
import com.videomaker.aimusic.core.analytics.Analytics
import com.videomaker.aimusic.core.analytics.AnalyticsEvent
import com.videomaker.aimusic.core.constants.AdPlacement
import com.videomaker.aimusic.core.data.local.PreferencesManager
import com.videomaker.aimusic.core.notification.NotificationConversionTracker
import com.videomaker.aimusic.core.notification.NotificationScheduler
import com.videomaker.aimusic.core.notification.NotificationType
import com.videomaker.aimusic.core.rating.RatingTriggerManager
import com.videomaker.aimusic.domain.model.AspectRatio
import com.videomaker.aimusic.domain.model.Project
import com.videomaker.aimusic.domain.model.VideoQuality
import com.videomaker.aimusic.domain.model.VideoTemplate
import com.videomaker.aimusic.domain.repository.ExportProgress
import com.videomaker.aimusic.domain.repository.ExportRepository
import com.videomaker.aimusic.domain.repository.ProjectRepository
import com.videomaker.aimusic.domain.repository.TemplateRepository
import com.videomaker.aimusic.domain.usecase.SubmitFeedbackUseCase
import com.videomaker.aimusic.media.export.MediaStoreHelper
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
    /**
     * Legacy back navigation (no ad check)
     * Use RequestExitWithAd instead for ad support
     */
    data object NavigateBack : ExportNavigationEvent()

    /**
     * Navigate to Home My Videos tab (without ad)
     * Use RequestExitWithAd instead for ad support
     */
    data object NavigateToHomeMyVideos : ExportNavigationEvent()

    /**
     * Request exit with optional ad
     * @param shouldShowAd true if ad is ready and should be shown
     */
    data class RequestExitWithAd(val shouldShowAd: Boolean) : ExportNavigationEvent()

    data class NavigateToTemplateDetail(
        val templateId: String,
        val shouldShowAd: Boolean = false
    ) : ExportNavigationEvent()
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
    private val submitFeedbackUseCase: SubmitFeedbackUseCase,
    private val adsLoaderService: AdsLoaderService,
    private val notificationScheduler: NotificationScheduler,
    private val preferencesManager: PreferencesManager,
    private val conversionTracker: NotificationConversionTracker
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

    // Rewarded ad controller for video download
    private val downloadAdController = RewardedAdController(
        placement = AdPlacement.REWARD_INTER_DOWNLOAD_VIDEO,
        viewModelScope = viewModelScope
    )

    // Expose download ad state
    val shouldPresentDownloadAd: StateFlow<Boolean> = downloadAdController.shouldPresentAd

    // Pending download request - stored when user initiates download
    private var pendingDownloadRequest: (() -> Unit)? = null

    // Watermark state - shown by default, removed after watching ad
    private val _showWatermark = MutableStateFlow(true)
    val showWatermark: StateFlow<Boolean> = _showWatermark.asStateFlow()

    // Rewarded ad controller for watermark removal
    private val watermarkAdController = RewardedAdController(
        placement = AdPlacement.REWARD_REMOVE_WATERMARK,
        viewModelScope = viewModelScope
    )

    // Expose watermark ad state
    val shouldPresentWatermarkAd: StateFlow<Boolean> = watermarkAdController.shouldPresentAd

    // ============================================
    // NAVIGATION EVENTS (StateFlow-based - Google recommended)
    // ============================================
    // Using Channel for one-time events (Google pattern) - prevents replay on config change

    private val _navigationEvent = Channel<ExportNavigationEvent>()
    val navigationEvent = _navigationEvent.receiveAsFlow()

    // ============================================
    // INTERNAL STATE
    // ============================================

    private var workId: UUID? = null
    private var currentProjectSnapshot: Project? = null
    private var currentOutputWatermarkFree: Boolean = false
    private var watermarkStatusUpdateJob: kotlinx.coroutines.Job? = null
    // Single job for export + progress observation — cancelled on retry to prevent
    // parallel observers watching different work items simultaneously.
    private var exportJob: kotlinx.coroutines.Job? = null

    // Parallel watermark-free export — pre-generates the clean version in background.
    // When user earns the remove-watermark reward, we switch to this path instantly
    // instead of triggering a re-export at download time.
    private var cleanWorkId: UUID? = null
    private var cleanExportJob: kotlinx.coroutines.Job? = null
    private var cleanOutputPath: String? = null
    private var pendingCleanSwitch: Boolean = false

    // ============================================
    // INITIALIZATION
    // ============================================

    init {
        loadProjectData()
        startExport()
        loadFeaturedTemplates()
        observeSuccessForAdPreload()
    }

    private fun loadProjectData() {
        viewModelScope.launch {
            val project = projectRepository.getProject(projectId)
            currentProjectSnapshot = project
            _thumbnailUri.value = project?.thumbnailUri ?: project?.assets?.firstOrNull()?.uri
            _aspectRatio.value = project?.settings?.aspectRatio ?: AspectRatio.RATIO_9_16

            // Watermark is always shown on ExportScreen entry regardless of past sessions.
            // _showWatermark defaults to true; it only changes within this session after
            // the user earns the remove-watermark reward.
        }
    }

    private fun startExport() {
        exportJob?.cancel()
        cleanExportJob?.cancel()
        cleanOutputPath = null
        pendingCleanSwitch = false

        exportJob = viewModelScope.launch {
            val project = currentProjectSnapshot ?: projectRepository.getProject(projectId)
            currentProjectSnapshot = project
            // startExport and observeProgress run sequentially in one coroutine —
            // workId is guaranteed to be set before observeProgress reads it.
            val id = exportRepository.startExport(projectId, quality = _currentQuality.value)
            workId = id

            // Pre-generate the watermark-free version in parallel so switching is instant
            // after the user earns the remove-watermark reward.
            startCleanExport()
            exportRepository.observeExportProgress(id).collect { progress ->
                _uiState.value = when (progress) {
                    is ExportProgress.Preparing -> ExportUiState.Preparing
                    is ExportProgress.Processing -> ExportUiState.Processing(progress.percent)
                    is ExportProgress.Success -> {
                        currentOutputWatermarkFree = false // main export always has watermark
                        Analytics.trackVideoExportComplete(
                            videoId = projectId,
                            templateId = project?.settings?.templateId,
                            songId = project?.settings?.primaryAudioNode?.songId?.toString(),
                            quality = _currentQuality.value.displayName,
                            duration = project?.totalDurationMs,
                            ratioSize = project?.settings?.aspectRatio?.toAnalyticsRatioSize(),
                            volume = (project?.settings?.primaryAudioNode?.volume ?: 1f).times(100f).roundToInt(),
                            mediaQuantity = project?.assets?.size
                        )
                        val generatedAt = System.currentTimeMillis()
                        preferencesManager.upsertVideoReminderState(
                            projectId = projectId,
                            generatedAtMs = generatedAt,
                            templateId = project?.settings?.templateId,
                            songId = project?.settings?.primaryAudioNode?.songId,
                            thumbnailUri = _thumbnailUri.value?.toString()
                        )
                        notificationScheduler.scheduleQuickSaveReminder(
                            projectId = projectId,
                            generatedAtMs = generatedAt
                        )
                        notificationScheduler.scheduleShareEncouragement(
                            projectId = projectId,
                            generatedAtMs = generatedAt
                        )
                        notificationScheduler.scheduleForgottenMasterpiece(
                            projectId = projectId,
                            generatedAtMs = generatedAt
                        )
                        Analytics.trackNotificationScheduled(
                            type = NotificationType.QUICK_SAVE_REMINDER.analyticsValue,
                            itemId = projectId,
                            itemType = "video",
                            sourceTrigger = "export_success",
                            deepLinkDestination = "my_video",
                            delayMinutes = notificationScheduler.currentScheduleConfig().quickSaveDelayMs / 60_000L,
                            copyVariant = "quick_save_v1",
                            imageType = "video_cover",
                            sessionType = "retention"
                        )
                        Analytics.trackNotificationScheduled(
                            type = NotificationType.SHARE_ENCOURAGEMENT.analyticsValue,
                            itemId = projectId,
                            itemType = "video",
                            sourceTrigger = "export_success",
                            deepLinkDestination = "my_video",
                            delayMinutes = notificationScheduler.currentScheduleConfig().shareEncouragementDelayMs / 60_000L,
                            copyVariant = "likes_push_v1",
                            imageType = "video_cover",
                            sessionType = "retention"
                        )
                        Analytics.trackNotificationScheduled(
                            type = NotificationType.FORGOTTEN_MASTERPIECE.analyticsValue,
                            itemId = projectId,
                            itemType = "video",
                            sourceTrigger = "export_success",
                            deepLinkDestination = "my_video",
                            delayMinutes = notificationScheduler.currentScheduleConfig().forgottenMasterpieceDelayMs / 60_000L,
                            copyVariant = "masterpiece_waiting_v1",
                            imageType = "video_cover",
                            sessionType = "retention"
                        )
                        ratingTriggerManager.onVideoCreated()
                        ExportUiState.Success(progress.outputPath)
                    }
                    is ExportProgress.Error -> {
                        Analytics.trackVideoExportError(
                            videoId = projectId,
                            errorMessage = progress.message,
                            errorCode = progress.errorCode,
                            templateId = project?.settings?.templateId,
                            songId = project?.settings?.primaryAudioNode?.songId?.toString(),
                            quality = _currentQuality.value.displayName,
                            duration = project?.totalDurationMs,
                            ratioSize = project?.settings?.aspectRatio?.toAnalyticsRatioSize(),
                            mediaQuantity = project?.assets?.size
                        )
                        ExportUiState.Error(progress.message)
                    }
                    is ExportProgress.Cancelled -> ExportUiState.Cancelled
                }
            }
        }
    }

    private fun startCleanExport() {
        cleanExportJob?.cancel()
        cleanExportJob = viewModelScope.launch {
            val cleanId = exportRepository.startExport(projectId, forceWatermarkFree = true, quality = _currentQuality.value)
            cleanWorkId = cleanId
            android.util.Log.d("ExportViewModel", "🎬 Clean export started: $cleanId")
            exportRepository.observeExportProgress(cleanId).collect { progress ->
                if (progress is ExportProgress.Success) {
                    cleanOutputPath = progress.outputPath
                    android.util.Log.d("ExportViewModel", "✅ Clean export ready: ${progress.outputPath}")
                    // Auto-switch if user already earned the reward in this session
                    // (pendingCleanSwitch) or previously earned then changed quality
                    // (!_showWatermark.value means reward was already granted).
                    if (pendingCleanSwitch || !_showWatermark.value) {
                        pendingCleanSwitch = false
                        switchToCleanOutput(progress.outputPath)
                    }
                }
            }
        }
    }

    private fun switchToCleanOutput(cleanPath: String) {
        val currentState = _uiState.value as? ExportUiState.Success ?: return
        _uiState.value = currentState.copy(outputPath = cleanPath, savedToGallery = false)
        currentOutputWatermarkFree = true
        android.util.Log.d("ExportViewModel", "🔄 Switched preview to watermark-free output")
    }

    // ============================================
    // USER ACTIONS
    // ============================================

    /**
     * Cancel the ongoing export
     */
    fun cancelExport() {
        workId?.let { exportRepository.cancelExport(it) }
        cleanWorkId?.let { exportRepository.cancelExport(it) }
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
        viewModelScope.launch {
            _navigationEvent.send(ExportNavigationEvent.NavigateBack)
        }
    }

    /**
     * User explicitly exits result screen (Done/Back gesture on success state).
     * Keep analytics trigger at UI action boundary to avoid passive/programmatic firing.
     */
    fun onResultExitClick() {
        Analytics.trackExitClick(AnalyticsEvent.Value.Location.RESULT)
        navigateToHomeMyVideos()
    }

    /**
     * Navigate to Home screen with My Videos tab (tab index 2)
     * Resets savedToGallery state so button is enabled on next export
     * Checks if exit ad is ready and shows it before navigation
     */
    fun navigateToHomeMyVideos() {

        // Reset saved state so button is enabled next time
        val currentState = _uiState.value
        if (currentState is ExportUiState.Success && currentState.savedToGallery) {
            _uiState.value = currentState.copy(savedToGallery = false)
        }

        // Clear any active toast
        _saveToastState.value = null

        // Check if exit ad is ready (non-blocking)
        val isAdReady = adsLoaderService.isInterstitialReady(AdPlacement.INTERSTITIAL_EXPORT_RESULT_EXIT)

        android.util.Log.d("ExportViewModel", "🔙 navigateToHomeMyVideos - Ad ready: $isAdReady")

        // Send navigation event with ad status (Channel - one-time event, no replay)
        // Screen will show ad if ready, otherwise navigate immediately
        viewModelScope.launch {
            _navigationEvent.send(ExportNavigationEvent.RequestExitWithAd(isAdReady))
        }
    }

    /**
     * Called by UI after toast is dismissed - clears the toast state
     */
    fun onSaveToastDismissed() {
        _saveToastState.value = null
    }

    /**
     * Observe Success state to preload exit ad
     * Preloads ad when export completes successfully (non-blocking)
     */
    private fun observeSuccessForAdPreload() {
        viewModelScope.launch {
            _uiState.collect { state ->
                if (state is ExportUiState.Success) {
                    // Preload exit ad when export completes (no timeout, non-blocking)
                    android.util.Log.d("ExportViewModel", "🎬 Preloading exit ad...")
                    runCatching {
                        InterstitialAdHelperExt.preloadInterstitial(
                            adsLoaderService = adsLoaderService,
                            placement = AdPlacement.INTERSTITIAL_EXPORT_RESULT_EXIT,
                            loadTimeoutMillis = null,  // No timeout - loads in background
                            showLoadingOverlay = false
                        )
                    }.onSuccess { success ->
                        if (success) {
                            android.util.Log.d("ExportViewModel", "✅ Exit ad preload SUCCESS")
                        } else {
                            android.util.Log.w("ExportViewModel", "⚠️ Exit ad preload FAILED")
                        }
                    }.onFailure { e ->
                        android.util.Log.e("ExportViewModel", "❌ Exit ad preload exception: ${e.message}", e)
                    }
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
        val template = (_featuredTemplatesState.value as? FeaturedTemplatesState.Success)
            ?.templates
            ?.firstOrNull { it.id == templateId }
        val templateName = template?.name ?: "unknown"
        val templateIsPremium = template?.isPremium ?: false
        Analytics.trackTemplateClick(
            templateId = templateId,
            templateName = templateName,
            location = AnalyticsEvent.Value.Location.RESULT_RCM,
            isPremium = templateIsPremium
        )

        // Check if template grid tap ad is ready
        val isAdReady = adsLoaderService.isInterstitialReady(AdPlacement.INTERSTITIAL_TEMPLATE_GRID_TAP)

        viewModelScope.launch {
            _navigationEvent.send(
                ExportNavigationEvent.NavigateToTemplateDetail(
                    templateId = templateId,
                    shouldShowAd = isAdReady
                )
            )
        }
    }

    fun trackShareAction() {
        val project = currentProjectSnapshot
        val now = System.currentTimeMillis()
        preferencesManager.markVideoShared(projectId, now)
        notificationScheduler.cancelProjectReminders(projectId)
        conversionTracker.trackConversionIfEligible(
            type = NotificationType.SHARE_ENCOURAGEMENT,
            itemId = projectId,
            itemType = "video",
            conversionAction = "share",
            nowMs = now
        )
        conversionTracker.trackConversionIfEligible(
            type = NotificationType.FORGOTTEN_MASTERPIECE,
            itemId = projectId,
            itemType = "video",
            conversionAction = "share",
            nowMs = now
        )
        Analytics.trackVideoShare(
            videoId = projectId,
            templateId = project?.settings?.templateId,
            songId = project?.settings?.primaryAudioNode?.songId?.toString(),
            quality = _currentQuality.value.displayName,
            duration = project?.totalDurationMs,
            ratioSize = project?.settings?.aspectRatio?.toAnalyticsRatioSize(),
            volume = (project?.settings?.primaryAudioNode?.volume ?: 1f).times(100f).roundToInt(),
            mediaQuantity = project?.assets?.size,
            location = AnalyticsEvent.Value.Location.RESULT
        )
    }

    /**
     * Called when user initiates download (clicks download button)
     * Presents rewarded ad or proceeds directly if ad disabled
     *
     * @param applicationContext Application context for download
     * @param loadingMessage Localized message for loading state
     * @param successMessage Localized message for success state
     * @param errorMessage Localized message for error state
     */
    fun onDownloadClick(
        applicationContext: Context,
        loadingMessage: String,
        successMessage: String,
        errorMessage: String
    ) {
        // Store pending download request
        pendingDownloadRequest = {
            saveToGallery(
                applicationContext = applicationContext,
                loadingMessage = loadingMessage,
                successMessage = successMessage,
                errorMessage = errorMessage
            )
        }

        // Request ad via controller
        downloadAdController.requestAd(
            onReward = {
                // Download after ad is watched
                pendingDownloadRequest?.invoke()
                pendingDownloadRequest = null
            },
            onSkip = {
                // Ad disabled or fallback - proceed with download
                android.util.Log.d("ExportViewModel", "⏭️ Ad disabled/unavailable - proceeding with download")
                pendingDownloadRequest?.invoke()
                pendingDownloadRequest = null
            },
            checkEnabled = { adsLoaderService.canLoadAd(AdPlacement.REWARD_INTER_DOWNLOAD_VIDEO) }
        )
    }

    /**
     * Called by UI after user earns reward from watching download ad
     */
    fun onDownloadRewardEarned() {
        downloadAdController.onRewardEarned()
    }

    /**
     * Called by UI when download ad fails to load or user closes ad without watching
     * Allows download anyway with error toast (per requirement: fallback on timeout/error)
     */
    fun onDownloadAdFailed() {
        downloadAdController.onAdFailed()
        _saveToastState.value = ProcessToastState.Error("Ad not available right now")
        // Allow download anyway on failure (per requirement)
        pendingDownloadRequest?.invoke()
        pendingDownloadRequest = null
    }

    /**
     * Called when user clicks watermark overlay
     * Presents rewarded ad or removes watermark immediately if ad disabled
     */
    fun onWatermarkClick() {
        watermarkAdController.requestAd(
            onReward = {
                _showWatermark.value = false
                android.util.Log.d("ExportViewModel", "✅ Watermark removed after ad")
                val cleanPath = cleanOutputPath
                if (cleanPath != null) {
                    switchToCleanOutput(cleanPath)
                } else {
                    // Clean export still in progress — switch preview when it finishes
                    pendingCleanSwitch = true
                }
            },
            onSkip = {
                // Ad disabled - remove watermark immediately
                android.util.Log.d("ExportViewModel", "⏭️ Ad disabled - removing watermark")
                _showWatermark.value = false
                val cleanPath = cleanOutputPath
                if (cleanPath != null) {
                    switchToCleanOutput(cleanPath)
                } else {
                    pendingCleanSwitch = true
                }
            },
            checkEnabled = { adsLoaderService.canLoadAd(AdPlacement.REWARD_REMOVE_WATERMARK) }
        )
    }

    /**
     * Called when user dismisses watermark via X button
     * Keeps watermark visible but closes it (no dialog shown)
     */
    fun onWatermarkDismiss() {
        android.util.Log.d("ExportViewModel", "❌ User dismissed watermark without watching ad")
        // Watermark stays visible, just closes the overlay
        // User can click it again later to watch ad
    }

    /**
     * Called by UI after user earns reward from watching watermark ad
     */
    fun onWatermarkRewardEarned() {
        watermarkAdController.onRewardEarned()
    }

    /**
     * Called by UI when watermark ad fails to load or user closes ad without watching
     * Shows error toast - watermark stays visible
     */
    fun onWatermarkAdFailed() {
        watermarkAdController.onAdFailed()
        _saveToastState.value = ProcessToastState.Error("Ad not available right now")
    }

    /**
     * Mark project as watermark-free in database
     * Called when user successfully watches ad to remove watermark
     */
    private fun saveWatermarkFreeStatus() {
        watermarkStatusUpdateJob?.cancel()
        watermarkStatusUpdateJob = viewModelScope.launch {
            try {
                projectRepository.updateWatermarkFreeStatus(projectId, isWatermarkFree = true)
                android.util.Log.d("ExportViewModel", "✅ Project marked as watermark-free in database")
            } catch (e: Exception) {
                android.util.Log.e("ExportViewModel", "Failed to save watermark-free status", e)
            }
        }
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
    private fun saveToGallery(
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
                templateId = project?.settings?.templateId,
                songId = project?.settings?.primaryAudioNode?.songId?.toString(),
                quality = _currentQuality.value.displayName,
                duration = project?.totalDurationMs,
                ratioSize = project?.settings?.aspectRatio?.toAnalyticsRatioSize(),
                volume = (project?.settings?.primaryAudioNode?.volume ?: 1f).times(100f).roundToInt(),
                mediaQuantity = project?.assets?.size,
                location = AnalyticsEvent.Value.Location.RESULT
            )
            // Show loading toast
            _saveToastState.value = ProcessToastState.Loading(loadingMessage)

            val outputPath = resolveOutputPathForDownload(currentState).getOrElse { e ->
                android.util.Log.e("ExportViewModel", "Failed to prepare output for download", e)
                _saveToastState.value = ProcessToastState.Error(errorMessage)
                return@launch
            }

            val result = withContext(Dispatchers.IO) {
                MediaStoreHelper.saveVideoToGallery(
                    context = applicationContext,
                    videoFile = File(outputPath),
                    displayName = MediaStoreHelper.generateDisplayName(projectId)
                )
            }

            when (result) {
                is MediaStoreHelper.SaveResult.Success -> {
                    _uiState.value = mergeDownloadResultState(
                        snapshotState = currentState,
                        latestState = _uiState.value as? ExportUiState.Success,
                        savedToGallery = true,
                        saveError = null
                    )
                    _saveToastState.value = ProcessToastState.Success(successMessage)
                    val now = System.currentTimeMillis()
                    preferencesManager.markVideoSaved(projectId, now)
                    notificationScheduler.cancelProjectReminders(projectId)
                    conversionTracker.trackConversionIfEligible(
                        type = NotificationType.QUICK_SAVE_REMINDER,
                        itemId = projectId,
                        itemType = "video",
                        conversionAction = "download",
                        nowMs = now
                    )
                    conversionTracker.trackConversionIfEligible(
                        type = NotificationType.FORGOTTEN_MASTERPIECE,
                        itemId = projectId,
                        itemType = "video",
                        conversionAction = "download",
                        nowMs = now
                    )
                }
                is MediaStoreHelper.SaveResult.Error -> {
                    _uiState.value = mergeDownloadResultState(
                        snapshotState = currentState,
                        latestState = _uiState.value as? ExportUiState.Success,
                        savedToGallery = false,
                        saveError = result.message
                    )
                    _saveToastState.value = ProcessToastState.Error(errorMessage)
                }
            }
        }
    }

    private suspend fun resolveOutputPathForDownload(currentState: ExportUiState.Success): Result<String> {
        // outputPath in state is always the correct version to download:
        // - Watermarked by default after initial export
        // - Switched to the pre-generated clean version via switchToCleanOutput() when
        //   the user earns the remove-watermark reward (no re-export required at download time)
        return Result.success(currentState.outputPath)
    }

    /**
     * Preload template grid tap interstitial ad.
     * Called after ad is shown to prepare the next one (Drama app pattern).
     * ACCCore handles duplicate prevention automatically.
     */
    fun preloadTemplateGridAd() {
        viewModelScope.launch {
            runCatching {
                InterstitialAdHelperExt.preloadInterstitial(
                    adsLoaderService = adsLoaderService,
                    placement = AdPlacement.INTERSTITIAL_TEMPLATE_GRID_TAP,
                    loadTimeoutMillis = null,
                    showLoadingOverlay = false
                )
            }
        }
    }

    companion object {
        internal fun shouldPrepareWatermarkFreeOutputForDownload(
            projectIsWatermarkFree: Boolean,
            outputIsWatermarkFree: Boolean
        ): Boolean {
            return projectIsWatermarkFree && !outputIsWatermarkFree
        }

        internal fun mergeDownloadResultState(
            snapshotState: ExportUiState.Success,
            latestState: ExportUiState.Success?,
            savedToGallery: Boolean,
            saveError: String?
        ): ExportUiState.Success {
            val baseState = latestState ?: snapshotState
            return baseState.copy(
                savedToGallery = savedToGallery,
                saveError = saveError
            )
        }
    }

}

private fun AspectRatio.toAnalyticsRatioSize(): String = when (this) {
    AspectRatio.RATIO_16_9 -> "16:9"
    AspectRatio.RATIO_9_16 -> "9:16"
    AspectRatio.RATIO_3_4 -> "3:4"
    AspectRatio.RATIO_4_5 -> "4:5"
    AspectRatio.RATIO_1_1 -> "1:1"
}
