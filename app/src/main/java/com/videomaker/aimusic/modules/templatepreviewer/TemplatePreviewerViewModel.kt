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
import com.videomaker.aimusic.domain.usecase.LikeTemplateUseCase
import com.videomaker.aimusic.domain.usecase.ObserveLikedTemplatesUseCase
import com.videomaker.aimusic.domain.usecase.UnlikeTemplateUseCase
import com.videomaker.aimusic.domain.usecase.UpdateProjectSettingsUseCase
import co.alcheclub.lib.acccore.ads.loader.AdsLoaderService
import com.videomaker.aimusic.core.ads.InterstitialAdHelperExt
import com.videomaker.aimusic.core.ads.RewardedAdController
import com.videomaker.aimusic.core.constants.AdPlacement
import com.videomaker.aimusic.core.storage.UnlockedTemplatesManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
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
    /**
     * Error state when song fails to load (network error, API failure, etc.)
     */
    data class Error(val message: String) : SongLoadState()
}

// ============================================
// NAVIGATION EVENTS
// ============================================

sealed class TemplatePreviewerNavigationEvent {
    /**
     * Legacy back navigation (no ad check)
     * Use RequestBackWithAd instead for ad support
     */
    data object NavigateBack : TemplatePreviewerNavigationEvent()

    /**
     * Request back navigation with optional ad
     * @param shouldShowAd true if ad is ready and should be shown
     */
    data class RequestBackWithAd(val shouldShowAd: Boolean) : TemplatePreviewerNavigationEvent()

    data class NavigateToAssetPicker(
        val template: VideoTemplate,
        val overrideSongId: Long,
        val aspectRatio: AspectRatio
    ) : TemplatePreviewerNavigationEvent()
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
    private val updateProjectSettingsUseCase: UpdateProjectSettingsUseCase,
    private val likeTemplateUseCase: LikeTemplateUseCase,
    private val unlikeTemplateUseCase: UnlikeTemplateUseCase,
    private val observeLikedTemplatesUseCase: ObserveLikedTemplatesUseCase,
    private val adsLoaderService: AdsLoaderService,
    private val unlockedTemplatesManager: UnlockedTemplatesManager
) : ViewModel() {

    private val imageUris: List<Uri> = imageUrisStr.mapNotNull { uriStr ->
        uriStr.takeIf { it.isNotBlank() }?.let { Uri.parse(it) }
    }

    /** Browse mode: no user images selected yet (show sample images, navigate to AssetPicker) */
    val isBrowseMode: Boolean get() = imageUris.isEmpty()

    // UI State
    private val _uiState = MutableStateFlow<TemplatePreviewerUiState>(TemplatePreviewerUiState.Loading)
    val uiState: StateFlow<TemplatePreviewerUiState> = _uiState.asStateFlow()

    // Navigation Events — StateFlow-based (Gold standard per CLAUDE.md)
    private val _navigationEvent = MutableStateFlow<TemplatePreviewerNavigationEvent?>(null)
    val navigationEvent: StateFlow<TemplatePreviewerNavigationEvent?> = _navigationEvent.asStateFlow()

    // Current song for the visible page
    private val _currentSong = MutableStateFlow<SongLoadState>(SongLoadState.None)
    val currentSong: StateFlow<SongLoadState> = _currentSong.asStateFlow()

    // Liked template IDs — observed from Room
    val likedTemplateIds: StateFlow<Set<String>> = observeLikedTemplatesUseCase.ids()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptySet()
        )

    // Unlocked template IDs — observed from UnlockedTemplatesManager
    val unlockedTemplateIds: StateFlow<Set<String>> = unlockedTemplatesManager.unlockedTemplateIds

    // Rewarded ad controller for template unlock
    private val rewardedAdController = RewardedAdController(
        placement = AdPlacement.REWARD_UNLOCK_TEMPLATE,
        adsLoaderService = adsLoaderService,
        viewModelScope = viewModelScope
    )

    // Expose rewarded ad states
    val showWatchAdDialog: StateFlow<Boolean> = rewardedAdController.showWatchAdDialog
    val shouldPresentAd: StateFlow<Boolean> = rewardedAdController.shouldPresentAd

    // Pending template to unlock (set when dialog shows)
    private val _pendingUnlockTemplate = MutableStateFlow<VideoTemplate?>(null)
    val pendingUnlockTemplate: StateFlow<VideoTemplate?> = _pendingUnlockTemplate.asStateFlow()

    // Pending selected ratio (set when user selects ratio for locked template)
    private val _pendingSelectedRatio = MutableStateFlow<AspectRatio?>(null)
    val pendingSelectedRatio: StateFlow<AspectRatio?> = _pendingSelectedRatio.asStateFlow()

    // Error message for snackbar
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

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

        // Preload back button interstitial ad
        // Ad loads in background with no timeout - may be used later if back is pressed
        // Non-blocking: back button works normally if ad not ready yet
        viewModelScope.launch {
            android.util.Log.d("TemplatePreviewerVM", "🎬 Preloading back button ad...")
            runCatching {
                InterstitialAdHelperExt.preloadInterstitial(
                    adsLoaderService = adsLoaderService,
                    placement = AdPlacement.INTERSTITIAL_TEMPLATE_PREVIEWER_BACK,
                    loadTimeoutMillis = null,  // No timeout - load as long as needed
                    showLoadingOverlay = false  // Background preload, no overlay
                )
            }.onSuccess { success ->
                if (success) {
                    android.util.Log.d("TemplatePreviewerVM", "✅ Back button ad preload SUCCESS")
                } else {
                    android.util.Log.w("TemplatePreviewerVM", "⚠️ Back button ad preload FAILED")
                }
            }.onFailure { e ->
                android.util.Log.e("TemplatePreviewerVM", "❌ Back button ad preload exception: ${e.message}", e)
            }
        }
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

        // Navigate to AssetPicker - user needs to select images
        _navigationEvent.value = TemplatePreviewerNavigationEvent.NavigateToAssetPicker(
            template = template,
            overrideSongId = overrideSongId,
            aspectRatio = aspectRatio
        )
    }

    fun onNavigateBack() {
        // Check if back button ad is ready (non-blocking)
        val isAdReady = adsLoaderService.isInterstitialReady(AdPlacement.INTERSTITIAL_TEMPLATE_PREVIEWER_BACK)

        android.util.Log.d("TemplatePreviewerVM", "🔙 onNavigateBack - Ad ready: $isAdReady")

        // Send navigation event with ad status
        // Screen will show ad if ready, otherwise navigate immediately
        _navigationEvent.value = TemplatePreviewerNavigationEvent.RequestBackWithAd(isAdReady)
    }

    /** Called by UI after navigation is handled — clears the event */
    fun onNavigationHandled() {
        _navigationEvent.value = null
    }

    fun onLikeTemplate(template: VideoTemplate) {
        viewModelScope.launch {
            if (template.id in likedTemplateIds.value) {
                unlikeTemplateUseCase(template.id)
            } else {
                likeTemplateUseCase(template)
            }
        }
    }

    /**
     * Called when user selects a ratio for a locked template
     * - If ad is enabled: show WatchAdDialog
     * - If ad is disabled: unlock directly and navigate
     */
    fun onRatioSelected(template: VideoTemplate, selectedRatio: AspectRatio) {
        // Store template and ratio for later use
        _pendingUnlockTemplate.value = template
        _pendingSelectedRatio.value = selectedRatio

        // Request ad (with auto-unlock if disabled)
        rewardedAdController.requestAd(
            onReward = {
                // Unlock template and navigate
                viewModelScope.launch {
                    unlockedTemplatesManager.unlockTemplate(template.id)
                    android.util.Log.d("TemplatePreviewerVM", "✅ Template unlocked: ${template.id}")

                    _navigationEvent.value = TemplatePreviewerNavigationEvent.NavigateToAssetPicker(
                        template = template,
                        overrideSongId = overrideSongId,
                        aspectRatio = selectedRatio
                    )

                    // Clear pending state
                    _pendingUnlockTemplate.value = null
                    _pendingSelectedRatio.value = null
                }
            },
            checkEnabled = { adsLoaderService.canLoadAd(AdPlacement.REWARD_UNLOCK_TEMPLATE) }
        )
    }

    /** User dismissed watch ad dialog without watching */
    fun onWatchAdDialogDismiss() {
        rewardedAdController.onDialogDismiss()
        _pendingUnlockTemplate.value = null
        _pendingSelectedRatio.value = null
    }

    /** User confirmed they want to watch ad - triggers ad presentation */
    fun onWatchAdConfirmed() {
        rewardedAdController.onDialogConfirm()
    }

    /** Rewarded ad completed successfully - unlock template and navigate */
    fun onRewardEarned() {
        rewardedAdController.onRewardEarned()
    }

    /** Rewarded ad failed or user canceled */
    fun onAdFailed() {
        rewardedAdController.onAdFailed()
        _pendingUnlockTemplate.value = null
        _pendingSelectedRatio.value = null
        _errorMessage.value = "AD_NOT_AVAILABLE"
    }

    // ============================================
    // PRIVATE METHODS
    // ============================================

    private fun loadInitialTemplates() {
        viewModelScope.launch {
            _uiState.value = TemplatePreviewerUiState.Loading
            currentOffset = 0
            hasMorePages = true

            // First, get the specific template by ID to ensure we show the correct one
            val initialTemplateResult = templateRepository.getTemplateById(initialTemplateId)
            val initialTemplate = initialTemplateResult.getOrNull()

            templateRepository.getTemplates(limit = PAGE_SIZE, offset = 0)
                .onSuccess { templates ->
                    currentOffset = templates.size
                    hasMorePages = templates.size >= PAGE_SIZE

                    // Find the initial template in the list
                    var initialPage = templates.indexOfFirst { it.id == initialTemplateId }
                    var finalTemplates = templates

                    // If the clicked template is not in the first page and we found it by ID,
                    // insert it at the beginning of the list
                    if (initialPage < 0 && initialTemplate != null) {
                        // Remove it from templates first if it exists (shouldn't happen, but be safe)
                        val templatesWithoutInitial = templates.filterNot { it.id == initialTemplateId }
                        finalTemplates = listOf(initialTemplate) + templatesWithoutInitial
                        initialPage = 0
                    } else if (initialPage < 0) {
                        // Template not found anywhere, default to first template
                        initialPage = 0
                    }

                    _uiState.value = TemplatePreviewerUiState.Ready(
                        templates = finalTemplates,
                        initialPage = initialPage
                    )
                    // Kick off song load for the initial page
                    loadSongForTemplate(finalTemplates[initialPage])
                }
                .onFailure { error ->
                    // Even if templates list fails, try to show the initial template
                    if (initialTemplate != null) {
                        _uiState.value = TemplatePreviewerUiState.Ready(
                            templates = listOf(initialTemplate),
                            initialPage = 0
                        )
                        loadSongForTemplate(initialTemplate)
                    } else {
                        _uiState.value = TemplatePreviewerUiState.Error(
                            error.message ?: "Failed to load templates"
                        )
                    }
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
                            // Filter out duplicates - keep track of existing IDs
                            val existingIds = currentState.templates.map { it.id }.toSet()
                            val uniqueNewTemplates = newTemplates.filterNot { it.id in existingIds }

                            _uiState.value = currentState.copy(
                                templates = currentState.templates + uniqueNewTemplates
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
                .onFailure { error ->
                    android.util.Log.e("TemplatePreviewerVM", "Failed to load song $songId", error)
                    // Use generic error message - will be localized in UI layer
                    _currentSong.value = SongLoadState.Error("SONG_LOAD_FAILED")
                }
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