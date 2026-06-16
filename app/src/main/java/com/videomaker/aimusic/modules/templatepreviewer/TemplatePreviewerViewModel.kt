package com.videomaker.aimusic.modules.templatepreviewer

import android.net.Uri
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
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
// SONG LOAD STATE - REMOVED
// Videos now have built-in music (template-preview-videos-v2 bucket)
// No separate music player needed
// ============================================

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

    /**
     * Show scroll interstitial ad while browsing templates
     * Frequency controlled by ad_interstitial_interval_seconds (ACCCore handles timing)
     */
    data object ShowScrollInterstitial : TemplatePreviewerNavigationEvent()

    data class NavigateToAssetPicker(
        val template: VideoTemplate,
        val overrideSongId: Long,
        val aspectRatio: AspectRatio
    ) : TemplatePreviewerNavigationEvent()

    /**
     * Request "use template" navigation with optional interstitial ad.
     * Non-blocking: if ad not ready, navigates immediately.
     */
    data class RequestUseTemplateWithAd(
        val shouldShowAd: Boolean,
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
    overrideSongId: Long = -1L,
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

    // Navigation Events — Channel pattern (Google official) - prevents replay on config change
    private val _navigationEvent = Channel<TemplatePreviewerNavigationEvent>()
    val navigationEvent = _navigationEvent.receiveAsFlow()

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
        viewModelScope = viewModelScope
    )

    // Expose rewarded ad state
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

    // Override song ID (exposed for UI to check if user song should be played)
    val overrideSongId: StateFlow<Long> = MutableStateFlow(overrideSongId).asStateFlow()

    // User's selected song (for background playback in TemplatePreviewer)
    private val _userSong = MutableStateFlow<MusicSong?>(null)
    val userSong: StateFlow<MusicSong?> = _userSong.asStateFlow()

    // Pagination tracking
    private var currentOffset = 0
    private var isLoadingMore = false
    private var hasMorePages = true

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

        // Preload scroll interstitial ad (Drama app pattern: preload on entry)
        // Frequency controlled by ACCCore's ad_interstitial_interval_seconds
        preloadScrollInterstitial()

        // Load user's selected song if overrideSongId is provided
        if (overrideSongId >= 0L) {
            loadUserSong(overrideSongId)
        }
    }

    // ============================================
    // PUBLIC METHODS
    // ============================================

    /** Retry loading templates after an error. */
    fun retry() {
        loadInitialTemplates()
    }

    fun refresh(excludeIds: Set<String> = emptySet()) {
        viewModelScope.launch {
            _uiState.value = TemplatePreviewerUiState.Loading
            currentOffset = 0
            hasMorePages = true

            templateRepository.getTemplates(limit = PAGE_SIZE, offset = 0)
                .onSuccess { templates ->
                    currentOffset = templates.size
                    hasMorePages = templates.size >= PAGE_SIZE

                    if (excludeIds.isNotEmpty()) {
                        // Filter out already-viewed templates
                        val freshTemplates = templates.filterNot { it.id in excludeIds }
                        if (freshTemplates.isNotEmpty()) {
                            _uiState.value = TemplatePreviewerUiState.Ready(
                                templates = freshTemplates,
                                initialPage = 0
                            )
                        } else {
                            // All templates in first page were already viewed — show full list anyway
                            _uiState.value = TemplatePreviewerUiState.Ready(
                                templates = templates,
                                initialPage = 0
                            )
                        }
                    } else {
                        _uiState.value = TemplatePreviewerUiState.Ready(
                            templates = templates,
                            initialPage = 0
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.value = TemplatePreviewerUiState.Error(
                        error.message ?: "Failed to load templates"
                    )
                }

            val currentSongId = overrideSongId.value
            if (currentSongId >= 0L) {
                loadUserSong(currentSongId)
            }
        }
    }

    fun onPageChanged(virtualIndex: Int) {
        val state = _uiState.value as? TemplatePreviewerUiState.Ready ?: return
        if (state.templates.isEmpty()) return
        val realIndex = virtualIndex % state.templates.size
        val remaining = state.templates.size - 1 - realIndex
        if (remaining <= LOAD_MORE_THRESHOLD && hasMorePages && !isLoadingMore) {
            loadMoreTemplates()
        }
        // No music loading needed - videos have built-in music

        // Preload scroll interstitial on every page change (Drama app pattern)
        // Library handles duplicate checks - safe to call repeatedly
        preloadScrollInterstitial()

        // Trigger scroll ad check (ACCCore enforces interval automatically)
        tryShowScrollInterstitial()
    }

    fun onUseThisTemplate(template: VideoTemplate, aspectRatio: AspectRatio) {
        val currentState = _uiState.value as? TemplatePreviewerUiState.Ready ?: return
        if (currentState.isCreatingProject) return

        // Increment use_count for analytics/ranking
        viewModelScope.launch(Dispatchers.IO) {
            templateRepository.incrementUseCount(template.id)
        }

        // Check if "use template" interstitial is ready (non-blocking)
        val isAdReady = adsLoaderService.isInterstitialReady(
            AdPlacement.INTERSTITIAL_TEMPLATE_PREVIEWER_USE
        )
        val currentOverrideSongId = overrideSongId.value

        viewModelScope.launch {
            _navigationEvent.send(
                TemplatePreviewerNavigationEvent.RequestUseTemplateWithAd(
                    shouldShowAd = isAdReady,
                    template = template,
                    overrideSongId = currentOverrideSongId,
                    aspectRatio = aspectRatio
                )
            )
        }
    }

    fun onNavigateBack() {
        // Check if back button ad is ready (non-blocking)
        val isAdReady = adsLoaderService.isInterstitialReady(AdPlacement.INTERSTITIAL_TEMPLATE_PREVIEWER_BACK)

        android.util.Log.d("TemplatePreviewerVM", "🔙 onNavigateBack - Ad ready: $isAdReady")

        // Send navigation event with ad status (Channel - one-time event, no replay)
        // Screen will show ad if ready, otherwise navigate immediately
        viewModelScope.launch {
            _navigationEvent.send(TemplatePreviewerNavigationEvent.RequestBackWithAd(isAdReady))
        }
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
     * - If ad is enabled: present rewarded ad
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

                    val currentOverrideSongId = overrideSongId.value  // Get current value from StateFlow
                    _navigationEvent.send(
                        TemplatePreviewerNavigationEvent.NavigateToAssetPicker(
                            template = template,
                            overrideSongId = currentOverrideSongId,
                            aspectRatio = selectedRatio
                        )
                    )

                    // Clear pending state
                    _pendingUnlockTemplate.value = null
                    _pendingSelectedRatio.value = null
                }
            },
            checkEnabled = { adsLoaderService.canLoadAd(AdPlacement.REWARD_UNLOCK_TEMPLATE) }
        )
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
    // SCROLL INTERSTITIAL AD LOGIC
    // ============================================

    /**
     * Preload scroll interstitial ad in background (Drama app pattern).
     *
     * Aggressive preload strategy:
     * - Called on screen entry (init)
     * - Called on every page change
     * - ACCCore handles duplicate request prevention automatically
     * - Persistent: Uses ProcessLifecycleOwner scope to survive ViewModel destruction
     * - No timeout: Loads in background while user browses
     */
    private fun preloadScrollInterstitial() {
        // Use ProcessLifecycleOwner scope - persists beyond ViewModel lifecycle
        // IMPORTANT: Must use Main dispatcher - ad SDK requires main thread
        // No job tracking needed - ACCCore prevents duplicate requests
        ProcessLifecycleOwner.get().lifecycleScope.launch(Dispatchers.Main) {
            android.util.Log.d("TemplatePreviewerVM", "🔄 Preloading scroll interstitial...")
            runCatching {
                InterstitialAdHelperExt.preloadInterstitial(
                    adsLoaderService = adsLoaderService,
                    placement = AdPlacement.INTERSTITIAL_TEMPLATE_PREVIEWER_SCROLL,
                    loadTimeoutMillis = null,  // No timeout - load as long as needed
                    showLoadingOverlay = false  // Background load, no UI
                )
            }.onSuccess { success ->
                if (success) {
                    android.util.Log.d("TemplatePreviewerVM", "✅ Scroll ad preload SUCCESS")
                } else {
                    android.util.Log.d("TemplatePreviewerVM", "⚠️ Scroll ad preload FAILED")
                }
            }.onFailure { e ->
                android.util.Log.e("TemplatePreviewerVM", "❌ Scroll ad preload exception: ${e.message}", e)
            }
        }
    }

    /**
     * Trigger scroll interstitial ad if ready and interval passed.
     *
     * ACCCore automatically handles:
     * - Frequency cap (ad_interstitial_interval_seconds from Remote Config)
     * - Ad ready check
     * - Global timing coordination
     *
     * If interval hasn't passed, ACCCore skips silently (no ad shown).
     */
    private fun tryShowScrollInterstitial() {
        // Send navigation event - ACCCore will enforce interval in showInterstitial()
        viewModelScope.launch {
            _navigationEvent.send(TemplatePreviewerNavigationEvent.ShowScrollInterstitial)
        }
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
                }
                .onFailure { error ->
                    // Even if templates list fails, try to show the initial template
                    if (initialTemplate != null) {
                        _uiState.value = TemplatePreviewerUiState.Ready(
                            templates = listOf(initialTemplate),
                            initialPage = 0
                        )
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

    private fun buildSettingsFromTemplate(template: VideoTemplate, aspectRatio: AspectRatio): ProjectSettings {
        val currentOverrideSongId = overrideSongId.value  // Get current value from StateFlow
        val songId = if (currentOverrideSongId >= 0L) currentOverrideSongId
                     else template.songId.takeIf { it > 0L }
        val audioNodes = songId?.let {
            listOf(com.videomaker.aimusic.domain.model.AudioNode(songId = it))
        } ?: emptyList()
        return ProjectSettings(
            effectSetId = template.effectSetId,
            aspectRatio = aspectRatio,
            audioNodes = audioNodes
        )
    }

    private fun loadUserSong(songId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            songRepository.getSongById(songId)
                .onSuccess { song ->
                    _userSong.value = song
                    android.util.Log.d("TemplatePreviewerVM", "✅ Loaded user song: ${song.name}")
                }
                .onFailure { e ->
                    android.util.Log.e("TemplatePreviewerVM", "❌ Failed to load user song: $songId", e)
                }
        }
    }

    companion object {
        private const val PAGE_SIZE = 15
        private const val LOAD_MORE_THRESHOLD = 3
    }
}
