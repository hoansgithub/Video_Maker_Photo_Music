package com.videomaker.aimusic.modules.editor

// DurationPlanner removed - beat-sync only mode
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.videomaker.aimusic.core.ads.AdPlacementConfigService
import com.videomaker.aimusic.core.ads.InterstitialAdHelperExt
import com.videomaker.aimusic.core.ads.RewardedAdController
import com.videomaker.aimusic.core.analytics.Analytics
import com.videomaker.aimusic.core.constants.AdPlacement
import com.videomaker.aimusic.domain.model.AspectRatio
import com.videomaker.aimusic.domain.model.Asset
import com.videomaker.aimusic.domain.model.AudioNode
import com.videomaker.aimusic.domain.model.EditorInitialData
import com.videomaker.aimusic.domain.model.MusicSong
import com.videomaker.aimusic.domain.model.Project
import com.videomaker.aimusic.domain.model.ProjectSettings
import com.videomaker.aimusic.domain.model.TextOverlay
import com.videomaker.aimusic.domain.model.VideoQuality
import com.videomaker.aimusic.domain.repository.EffectSetRepository
import com.videomaker.aimusic.domain.repository.SongRepository
import com.videomaker.aimusic.domain.usecase.AddAssetsUseCase
import com.videomaker.aimusic.domain.usecase.CreateProjectUseCase
import com.videomaker.aimusic.domain.usecase.GetProjectUseCase
import com.videomaker.aimusic.domain.usecase.RemoveAssetUseCase
import com.videomaker.aimusic.domain.usecase.UpdateProjectSettingsUseCase
import com.videomaker.aimusic.media.library.TransitionSetLibrary
import com.videomaker.aimusic.media.renderer.PlaybackClock
import com.videomaker.aimusic.media.renderer.RenderState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

// ============================================
// UI STATE
// ============================================

sealed class EditorUiState {
    // firstImageUri lets the editor show the first picked image (or saved-project thumbnail)
    // as a placeholder while data loads. Null -> ShimmerPlaceholder.
    // aspectRatio frames the placeholder exactly like the upcoming video preview.
    data class Loading(
        val firstImageUri: Uri? = null,
        val aspectRatio: AspectRatio = AspectRatio.RATIO_9_16
    ) : EditorUiState()

    data class Success(
        val project: Project,
        val isUnsavedProject: Boolean = false,
        val selectedAssetIndex: Int = 0,
        val isPlaying: Boolean = false,
        val showSettingsPanel: Boolean = false,
        val pendingSettings: ProjectSettings? = null,
        val pendingAssets: List<Asset>? = null, // Temporary assets for ImagesBottomSheet - not saved/rebuilt until confirmed
        val seekToPosition: Long? = null,
        val scrubToPosition: Long? = null,
        val wasPlayingBeforeSeek: Boolean = false,
        val selectedQuality: VideoQuality = VideoQuality.DEFAULT,
        val effectSetName: String = "Effect",
        val isMusicCached: Boolean = true, // Music fully downloaded and ready for export
        val isCachingMusic: Boolean = false, // Currently downloading music to cache
        val isProcessingAudio: Boolean = false, // Currently preprocessing audio with fadeout
        val selectedAudioNodeId: String? = null // Currently selected audio node for editing
    ) : EditorUiState() {
        val hasPendingChanges: Boolean get() = pendingSettings != null || pendingAssets != null || isUnsavedProject
        val displaySettings: ProjectSettings get() = pendingSettings ?: project.settings
        val displayAssets: List<Asset> get() = pendingAssets ?: project.assets

        /**
         * Project for video preview composition
         * Uses pendingSettings for real-time preview (e.g., volume)
         * Uses actual project.assets (NOT pendingAssets) to prevent rebuild during image editing
         * Only rebuilds when assets are confirmed via applyPendingAssets()
         */
        val previewProject: Project get() = project.copy(
            settings = pendingSettings ?: project.settings
            // assets intentionally uses project.assets, not pendingAssets
        )

        /**
         * Project with all pending changes for display (used by ImagesBottomSheet)
         */
        val displayProject: Project get() = project.copy(
            settings = pendingSettings ?: project.settings,
            assets = pendingAssets ?: project.assets
        )
    }

    data class Error(val message: String) : EditorUiState()
}

/**
 * High-level editor content state for driving the UI (placeholder vs player, skeleton vs real
 * controls, error popup). Derived from [EditorUiState] — a flat enum that's convenient to branch on.
 */
enum class EditorContentState { LOADING, SUCCESS, ERROR }

val EditorUiState.contentState: EditorContentState
    get() = when (this) {
        is EditorUiState.Loading -> EditorContentState.LOADING
        is EditorUiState.Success -> EditorContentState.SUCCESS
        is EditorUiState.Error -> EditorContentState.ERROR
    }

/**
 * Combined editor screen state covering BOTH the data load and the video preview build:
 * - LOADING: data loading, or data ready but the video composition is still building/processing.
 * - READY:   data ready AND the video has reached Ready AND nothing is reprocessing → video plays.
 * - ERROR:   data load failed or the preview failed to build.
 *
 * Unlike [EditorContentState] (data only), this reflects what the user actually sees, so it must
 * be derived in EditorScreen where the preview state lives (not in the ViewModel).
 */
enum class EditorScreenState { LOADING, READY, ERROR }

// ============================================
// NAVIGATION EVENTS
// ============================================

sealed class EditorNavigationEvent {
    /**
     * Request back navigation with optional ad
     * @param shouldShowAd true if ad is ready and should be shown
     */
    data class RequestBackWithAd(val shouldShowAd: Boolean) : EditorNavigationEvent()

    data class NavigateToPreview(val projectId: String) : EditorNavigationEvent()
    data class NavigateToExport(val projectId: String, val quality: VideoQuality) : EditorNavigationEvent()
    data object RequestQualityInterstitial : EditorNavigationEvent()
}

// ============================================
// VIEW MODEL
// ============================================

class EditorViewModel(
    private val context: android.content.Context,
    private val projectId: String?,
    private val initialData: EditorInitialData?,
    // Thumbnail of a saved project, passed via navigation so the Loading state can show
    // the first image immediately (before the full project is loaded from DB).
    private val initialThumbnailUri: String? = null,
    private val getProjectUseCase: GetProjectUseCase,
    private val createProjectUseCase: CreateProjectUseCase,
    private val updateSettingsUseCase: UpdateProjectSettingsUseCase,
    private val addAssetsUseCase: AddAssetsUseCase,
    private val removeAssetUseCase: RemoveAssetUseCase,
    private val songRepository: SongRepository,
    private val effectSetRepository: EffectSetRepository,
    private val beatSyncRepository: com.videomaker.aimusic.domain.repository.BeatSyncRepository,
    private val projectRepository: com.videomaker.aimusic.domain.repository.ProjectRepository,
    private val adsLoaderService: co.alcheclub.lib.acccore.ads.loader.AdsLoaderService,
    private val audioPreprocessingService: com.videomaker.aimusic.media.audio.AudioPreprocessingService,
    private val adPlacementConfigService: AdPlacementConfigService
) : ViewModel() {

    private val _uiState = MutableStateFlow<EditorUiState>(EditorUiState.Loading())
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    // Navigation events are separate from UI state — never embedded in high-frequency state
    // Using Channel for one-time events (Google pattern) - prevents replay on config change
    private val _navigationEvent = Channel<EditorNavigationEvent>()
    val navigationEvent = _navigationEvent.receiveAsFlow()

    // Whether the video preview has reached Ready at least once for the current load. Lives in the
    // ViewModel (retained across the asset-picker round trip and config changes) so the screen can
    // stay READY across replace/effect without flickering back to LOADING. Reset on a fresh load.
    private val _hasPreviewBeenReady = MutableStateFlow(false)
    val hasPreviewBeenReady: StateFlow<Boolean> = _hasPreviewBeenReady.asStateFlow()

    fun markPreviewReady() {
        _hasPreviewBeenReady.value = true
    }

    // Quality unlock state — session-based (reset when ViewModel cleared)
    // 720p and 1080p require watching rewarded ad to unlock
    private val _isQualityUnlocked = MutableStateFlow(false)
    val isQualityUnlocked: StateFlow<Boolean> = _isQualityUnlocked.asStateFlow()

    // Rewarded ad controller for quality unlock
    private val qualityAdController = RewardedAdController(
        placement = AdPlacement.REWARD_UNLOCK_QUALITY,
        viewModelScope = viewModelScope
    )

    // Expose quality ad state
    val shouldPresentQualityAd: StateFlow<Boolean> = qualityAdController.shouldPresentAd

    // Error message for ad failures
    private val _qualityAdError = MutableStateFlow<String?>(null)
    val qualityAdError: StateFlow<String?> = _qualityAdError.asStateFlow()

    // Beat-sync error dialog state
    private val _showBeatSyncErrorDialog = MutableStateFlow(false)
    val showBeatSyncErrorDialog: StateFlow<Boolean> = _showBeatSyncErrorDialog.asStateFlow()

    // GL renderer state — updated instantly on property changes, read by VideoRenderer each frame
    private val _renderState = MutableStateFlow(RenderState.EMPTY)
    val renderState: StateFlow<RenderState> = _renderState.asStateFlow()

    // ============================================
    // TEXT OVERLAYS COUPLING
    // ============================================
    fun updateTextOverlays(overlays: List<TextOverlay>) {
        updatePendingSettingsAudioOnly { it.copy(textOverlays = overlays) }
    }


    // Playback position — separate from uiState to avoid 240 state copies/sec.
    // Only the slider and time label observe these, not the entire EditorScreen tree.
    private val _currentPositionMs = MutableStateFlow(0L)
    val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()
    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    // Shared playback clock between GL renderer and ExoPlayer audio
    val playbackClock = PlaybackClock()

    // Mutex for thread-safe asset replacement (prevents interleaving from rapid changes)
    private val assetMutex = Mutex()

    // Track the actual project ID (might be generated for new projects)
    private var currentProjectId: String? = projectId
    private var hasTrackedReadyToEditGenerateComplete = false

    // Observer job for existing projects
    private var projectObserverJob: Job? = null
    private var musicUpdateJob: Job? = null
    private var positionTickerJob: Job? = null

    // Distinguishes music-change errors (stay in editor) from initial-load errors (navigate back)
    private var isMusicChangeError = false

    // Auto-play: start playback once on first project load
    private var hasAutoPlayed = false

    // Screen lifecycle: remember play state across pause/resume
    private var wasPlayingBeforeScreenPause = false

    init {
        require(projectId != null || initialData != null) {
            "Either projectId or initialData must be provided"
        }
        loadOrInitializeProject()

        // Preload back button interstitial ad
        // Ad loads in background with no timeout - may be used later if back is pressed
        // Non-blocking: back button works normally if ad not ready yet
        viewModelScope.launch {
            android.util.Log.d("EditorViewModel", "🎬 Preloading back button ad...")
            runCatching {
                com.videomaker.aimusic.core.ads.InterstitialAdHelperExt.preloadInterstitial(
                    adsLoaderService = adsLoaderService,
                    placement = com.videomaker.aimusic.core.constants.AdPlacement.INTERSTITIAL_EDITOR_BACK,
                    loadTimeoutMillis = null,  // No timeout - load as long as needed
                    showLoadingOverlay = false  // Background preload, no overlay
                )
            }.onSuccess { success ->
                if (success) {
                    android.util.Log.d("EditorViewModel", "✅ Back button ad preload SUCCESS")
                } else {
                    android.util.Log.w("EditorViewModel", "⚠️ Back button ad preload FAILED")
                }
            }.onFailure { e ->
                android.util.Log.e("EditorViewModel", "❌ Back button ad preload exception: ${e.message}", e)
            }
        }

        // Preload "after prepare" interstitial (shown 1s after editor finishes preparing)
        // Loads in background while the preparing screen is visible; non-blocking.
        viewModelScope.launch {
            android.util.Log.d("EditorViewModel", "🎬 Preloading after-prepare interstitial...")
            runCatching {
                com.videomaker.aimusic.core.ads.InterstitialAdHelperExt.preloadInterstitial(
                    adsLoaderService = adsLoaderService,
                    placement = com.videomaker.aimusic.core.constants.AdPlacement.INTERSTITIAL_EDITOR_AFTER_PREPARE,
                    loadTimeoutMillis = null,  // No timeout - load as long as needed
                    showLoadingOverlay = false  // Background preload, no overlay
                )
            }.onFailure { e ->
                android.util.Log.e("EditorViewModel", "❌ After-prepare ad preload exception: ${e.message}", e)
            }
        }

        // Preload quality-unlock interstitial if remote config routes to interstitial type.
        // Non-blocking background load — if not ready when Done is tapped, we skip the ad.
        if (adPlacementConfigService.getAdTypeForQuality(com.videomaker.aimusic.domain.model.VideoQuality.HD_720) == "interstitial" ||
            adPlacementConfigService.getAdTypeForQuality(com.videomaker.aimusic.domain.model.VideoQuality.FHD_1080) == "interstitial") {
            viewModelScope.launch {
                android.util.Log.d("EditorViewModel", "🎬 Preloading quality-unlock interstitial...")
                runCatching {
                    com.videomaker.aimusic.core.ads.InterstitialAdHelperExt.preloadInterstitial(
                        adsLoaderService = adsLoaderService,
                        placement = com.videomaker.aimusic.core.constants.AdPlacement.INTERSTITIAL_UNLOCK_QUALITY,
                        loadTimeoutMillis = null,
                        showLoadingOverlay = false
                    )
                }.onSuccess { success ->
                    if (success) {
                        android.util.Log.d("EditorViewModel", "✅ Quality-unlock interstitial preload SUCCESS")
                    } else {
                        android.util.Log.w("EditorViewModel", "⚠️ Quality-unlock interstitial preload FAILED")
                    }
                }.onFailure { e ->
                    android.util.Log.e("EditorViewModel", "❌ Quality-unlock interstitial preload exception: ${e.message}", e)
                }
            }
        }
    }

    /**
     * Fetches the effect set name by ID.
     * Returns "Effect" as default if ID is null or not found.
     */
    private suspend fun getEffectSetName(effectSetId: String?): String {
        if (effectSetId == null) return "Effect"
        return effectSetRepository.getEffectSetById(effectSetId)
            .getOrNull()
            ?.name
            ?: "Effect"
    }

    /**
     * Get audio URI from settings (sync version for use in IO coroutine)
     * Returns custom audio URI or looks up music song URL
     */
    private suspend fun getAudioUri(settings: ProjectSettings): Uri? {
        val node = settings.primaryAudioNode ?: return null

        // Priority 1: Custom audio from device
        node.customAudioUri?.let { return Uri.parse(it) }

        // Priority 2: Cached song URL
        node.songUrl?.let { url ->
            if (url.isNotBlank()) {
                return Uri.parse(url)
            }
        }

        // Priority 3: Look up song from repository
        node.songId?.let { songId ->
            val result = songRepository.getSongById(songId)
            result.onSuccess { song ->
                if (song.mp3Url.isNotBlank()) {
                    return Uri.parse(song.mp3Url)
                }
            }
        }

        return null
    }

    private fun trackVideoGenerateCompleteReadyToEdit(
        data: EditorInitialData,
        totalDurationMs: Long,
        mediaQuantity: Int
    ) {
        if (hasTrackedReadyToEditGenerateComplete) return

        val videoId = data.analyticsVideoId ?: currentProjectId ?: "editor_ready_${System.currentTimeMillis()}"
        Analytics.trackVideoGenerateComplete(
            videoId = videoId,
            songId = data.musicSongId?.toString(),
            duration = totalDurationMs,
            ratioSize = data.aspectRatio.toAnalyticsRatioSize(),
            mediaQuantity = mediaQuantity
        )
        hasTrackedReadyToEditGenerateComplete = true
    }

    private fun loadOrInitializeProject() {
        if (projectId != null) {
            // Mode 1: Load existing project from DB
            loadExistingProject(projectId)
        } else if (initialData != null) {
            // Mode 2: Initialize new project in memory
            initializeNewProject(initialData)
        }
    }

    /** Re-run the initial load/init — used by the Error popup's Retry action. */
    fun retry() {
        loadOrInitializeProject()
    }

    private fun loadExistingProject(id: String) {
        projectObserverJob?.cancel()
        _hasPreviewBeenReady.value = false // fresh load → preview not ready yet
        projectObserverJob = viewModelScope.launch {
            _uiState.value = EditorUiState.Loading(
                firstImageUri = initialThumbnailUri?.takeIf { it.isNotBlank() }?.let { Uri.parse(it) }
            )
            try {
                getProjectUseCase.observe(id).collect { loadedProject ->
                    if (loadedProject != null) {
                        val currentState = _uiState.value
                        val prev = currentState as? EditorUiState.Success

                        val selectedIndex = prev?.selectedAssetIndex
                            ?.coerceIn(0, loadedProject.assets.lastIndex.coerceAtLeast(0)) ?: 0

                        var project = loadedProject
                        val primaryNode = project.settings.primaryAudioNode
                        val songId = primaryNode?.songId

                        // Parallelize backfill + beat-sync load (independent operations)
                        val needsBackfill = primaryNode != null && primaryNode.songId != null &&
                            (primaryNode.songUrl == null || primaryNode.coverUrl == null || primaryNode.songArtist == null)
                        val needsBeatSync = songId != null && project.settings.beatSyncData == null

                        val backfillDeferred = if (needsBackfill && primaryNode != null) {
                            val backfillSongId = primaryNode.songId
                            async {
                                try {
                                    fetchSongWithRetry(backfillSongId)
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (_: Exception) { null }
                            }
                        } else null

                        val beatSyncDeferred = if (needsBeatSync && songId != null) {
                            val beatSyncSongId = songId
                            async {
                                android.util.Log.d("EditorViewModel", "Loading beat-sync data for saved project: $beatSyncSongId")
                                try {
                                    loadBeatSyncWithRetry(beatSyncSongId)
                                } catch (e: Exception) {
                                    android.util.Log.e("EditorViewModel", "Failed to load beat-sync data", e)
                                    null
                                }
                            }
                        } else null

                        // Await backfill result
                        if (needsBackfill && primaryNode != null) {
                            val song = backfillDeferred?.await()
                            if (song != null) {
                                val updatedNode = primaryNode.copy(
                                    songUrl = primaryNode.songUrl ?: song.mp3Url,
                                    songName = primaryNode.songName ?: song.name,
                                    coverUrl = primaryNode.coverUrl ?: song.coverUrl,
                                    songArtist = primaryNode.songArtist ?: song.artist
                                )
                                project = project.copy(
                                    settings = project.settings.copy(
                                        audioNodes = listOf(updatedNode) + project.settings.audioNodes.drop(1)
                                    )
                                )
                            }
                        }

                        // Await beat-sync result
                        if (needsBeatSync) {
                            val beatSyncData = beatSyncDeferred?.await()
                            if (beatSyncData != null) {
                                val savedTrimStart = project.settings.primaryAudioNode?.trimStartMs ?: project.settings.hookStartTimeMs
                                val totalDurationMs = Project.calculateBeatSyncDuration(
                                    beatData = beatSyncData,
                                    assetCount = project.assets.size,
                                    trimStartMs = savedTrimStart
                                ) ?: 0L

                                project = project.copy(
                                    settings = project.settings.copy(
                                        beatSyncData = beatSyncData,
                                        totalDurationMs = totalDurationMs
                                    )
                                )
                                android.util.Log.d("EditorViewModel", "Beat-sync data loaded for saved project: BPM=${beatSyncData.bpm}")
                            } else {
                                android.util.Log.e("EditorViewModel", "Beat-sync data required but failed to load")
                                return@collect
                            }
                        }

                        // Load effect set name
                        val effectSetName = getEffectSetName(project.settings.effectSetId)

                        // Show editor IMMEDIATELY — defer preprocessing to background
                        val shouldAutoPlay = prev == null && !hasAutoPlayed
                        _uiState.value = EditorUiState.Success(
                            project = project,
                            isUnsavedProject = false,
                            selectedAssetIndex = selectedIndex,
                            isPlaying = prev?.isPlaying ?: shouldAutoPlay,
                            showSettingsPanel = prev?.showSettingsPanel ?: false,
                            pendingSettings = prev?.pendingSettings,
                            seekToPosition = prev?.seekToPosition,
                            scrubToPosition = prev?.scrubToPosition,
                            wasPlayingBeforeSeek = prev?.wasPlayingBeforeSeek ?: false,
                            effectSetName = effectSetName
                        )
                        refreshRenderState()
                        startPositionTicker()
                        if (shouldAutoPlay) {
                            hasAutoPlayed = true
                            playbackClock.play()
                        }

                        // No background preprocessing needed — preview uses realtime fadeout
                        // via volume ramp in AudioTimelinePlayer. Export re-preprocesses independently.
                    } else {
                        _uiState.value = EditorUiState.Error("Project not found")
                    }
                }
            } catch (e: Exception) {
                _uiState.value = EditorUiState.Error(e.message ?: "Failed to load project")
            }
        }
    }

    private fun initializeNewProject(data: EditorInitialData) {
        _hasPreviewBeenReady.value = false // fresh load → preview not ready yet
        viewModelScope.launch {
            _uiState.value = EditorUiState.Loading(
                firstImageUri = data.imageUris.firstOrNull()?.takeIf { it.isNotBlank() }?.let { Uri.parse(it) },
                aspectRatio = data.aspectRatio
            )
            try {
                // Generate temporary project ID
                val tempId = UUID.randomUUID().toString()
                currentProjectId = tempId

                // Create in-memory project
                val imageUris = data.imageUris.mapNotNull { uriStr ->
                    uriStr.takeIf { it.isNotBlank() }?.let { Uri.parse(it) }
                }

                val assets = imageUris.mapIndexed { index, uri ->
                    Asset(
                        id = UUID.randomUUID().toString(),
                        uri = uri,
                        orderIndex = index
                    )
                }

                val songIdStr = data.musicSongId?.toString()

                // Use pre-fetched song data from picker if available, otherwise fetch with retry
                val song = if (data.musicSongUrl != null && data.musicSongId != null) {
                    MusicSong(
                        id = data.musicSongId,
                        name = data.musicSongName ?: "",
                        artist = data.musicSongArtist ?: "",
                        mp3Url = data.musicSongUrl,
                        coverUrl = data.musicSongCoverUrl ?: "",
                        beatsUrl = data.musicSongBeatsUrl ?: "",
                        durationMs = data.musicSongDurationMs?.toInt(),
                        hookStartTimes = data.musicSongHookStartTimes
                    )
                } else {
                    data.musicSongId?.let { songId -> fetchSongWithRetry(songId) }
                }
                Analytics.trackEditorPrepareStep("song_fetched", songIdStr)

                // Parallelize beat-sync load + effect set fetch (independent operations)
                val beatSyncDeferred = data.musicSongId?.let { songId ->
                    async { loadBeatSyncWithRetry(songId, song?.beatsUrl?.ifBlank { null }) }
                }
                val effectSetDeferred = if (data.effectSetId == null) {
                    async { fetchFirstEffectSetId() }
                } else null

                val rawBeatSyncData = beatSyncDeferred?.await()
                Analytics.trackEditorPrepareStep("beat_sync_loaded", songIdStr)
                val beatSyncData = rawBeatSyncData?.takeIf { beatData ->
                    // Validate beat-sync data: BPM must be positive and beats must not be empty
                    (beatData.bpm > 0 && beatData.beats.isNotEmpty()).also { valid ->
                        if (!valid) {
                            android.util.Log.e("EditorViewModel", "Invalid beat-sync data: bpm=${beatData.bpm}, beats=${beatData.beats.size}")
                        }
                    }
                }

                // CRITICAL: Song check FIRST so the correct error reason is identified
                // (song fetch failure vs beat-sync failure are different root causes)
                if (data.musicSongId != null && song == null) {
                    android.util.Log.e("EditorViewModel", "Song data required but unavailable after retries (songId=${data.musicSongId}) - aborting initialization")
                    Analytics.trackEditorPrepareFailed("song_fetch_failed", data.musicSongId.toString())
                    _showBeatSyncErrorDialog.value = true
                    return@launch
                }

                if (data.musicSongId != null && song != null && song.mp3Url.isBlank()) {
                    android.util.Log.e("EditorViewModel", "Song fetched but mp3Url is blank (songId=${data.musicSongId}, name=${song.name}) - aborting initialization")
                    Analytics.trackEditorPrepareFailed("song_url_blank", data.musicSongId.toString())
                    _showBeatSyncErrorDialog.value = true
                    return@launch
                }

                Analytics.trackEditorPrepareStep("song_validated", songIdStr)

                // Beat-sync data failed to load or is invalid
                // loadBeatSyncWithRetry already shows error dialog on load failure;
                // we set it here too for validation failures (idempotent on StateFlow)
                if (data.musicSongId != null && beatSyncData == null) {
                    val errorCode = if (rawBeatSyncData != null) "beat_sync_invalid" else "beat_sync_load_failed"
                    android.util.Log.e("EditorViewModel", "Beat-sync data required but unavailable ($errorCode) - aborting initialization")
                    Analytics.trackEditorPrepareFailed(errorCode, data.musicSongId.toString())
                    if (!_showBeatSyncErrorDialog.value) {
                        _showBeatSyncErrorDialog.value = true
                    }
                    return@launch
                }

                Analytics.trackEditorPrepareStep("beat_sync_validated", songIdStr)

                // Await effect set result (already running in parallel with beat-sync)
                val effectSetId = effectSetDeferred?.await()
                    ?: data.effectSetId
                    ?: TransitionSetLibrary.getDefault().id
                Analytics.trackEditorPrepareStep("effect_set_fetched", songIdStr)

                // Always use the song's hook start time when song metadata is available
                val hookStartTimeMs = song?.hookStartTimes?.firstOrNull() ?: 0L

                // Calculate total duration from beat-sync data
                val totalDurationMs = if (beatSyncData != null) {
                    Project.calculateBeatSyncDuration(
                        beatData = beatSyncData,
                        assetCount = imageUris.size,
                        trimStartMs = hookStartTimeMs
                    ) ?: 0L
                } else {
                    0L
                }

                Analytics.trackEditorPrepareStep("duration_calculated", songIdStr)

                // Build audio node WITHOUT preprocessed URI — show editor immediately.
                // AudioTimelinePlayer streams from raw songUrl when processedAudioUri is null.
                val audioNodes = if (data.musicSongId != null && song != null) {
                    listOf(
                        AudioNode(
                            songId = data.musicSongId,
                            songName = song.name,
                            songArtist = song.artist,
                            songUrl = song.mp3Url,
                            coverUrl = song.coverUrl,
                            startTimeMs = 0L,
                            trimStartMs = hookStartTimeMs,
                            volume = 1.0f,
                            processedAudioUri = null, // Deferred — will hot-swap when ready
                            hookStartTimes = song.hookStartTimes,
                            songDurationMs = song.durationMs?.toLong()
                        )
                    )
                } else {
                    emptyList()
                }

                // Beat-sync only: settings with loaded beat-sync data (preprocessed audio deferred)
                val settings = ProjectSettings(
                    beatSyncData = beatSyncData,
                    hookStartTimeMs = hookStartTimeMs,
                    totalDurationMs = totalDurationMs,
                    effectSetId = effectSetId,
                    templateId = data.templateId,
                    aspectRatio = data.aspectRatio,
                    audioNodes = audioNodes
                )

                val project = Project(
                    id = tempId,
                    name = "New Project",
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                    thumbnailUri = imageUris.firstOrNull(),
                    assets = assets,
                    settings = settings
                )

                // Load effect set name
                val effectSetName = getEffectSetName(settings.effectSetId)

                // Show editor IMMEDIATELY — music streams from raw URL via ExoPlayer
                _uiState.value = EditorUiState.Success(
                    project = project,
                    isUnsavedProject = true,
                    isPlaying = true,
                    effectSetName = effectSetName
                )
                refreshRenderState()
                startPositionTicker()
                hasAutoPlayed = true
                playbackClock.play()
                Analytics.trackEditorPrepareStep("project_built", songIdStr)
                trackVideoGenerateCompleteReadyToEdit(
                    data = data,
                    totalDurationMs = settings.totalDurationMs,
                    mediaQuantity = assets.size
                )

                // No background preprocessing needed — preview uses realtime fadeout
                // via volume ramp in AudioTimelinePlayer. Export re-preprocesses independently.
            } catch (e: Exception) {
                _uiState.value = EditorUiState.Error(e.message ?: "Failed to initialize project")
            }
        }
    }

    /**
     * Comprehensive save function that handles both pending settings and new projects.
     *
     * Save logic:
     * 1. If there are pending settings, apply them to the project
     * 2. If project is unsaved (new), create it in the database
     * 3. If project exists, settings are auto-saved via applySettings()
     *
     * Returns true if save was successful or no save was needed.
     */
    suspend fun saveProject(): Boolean {
        val currentState = _uiState.value
        if (currentState !is EditorUiState.Success) return false

        // Step 1: Apply pending settings if any
        if (currentState.pendingSettings != null) {
            applySettings()
            // Wait a bit for settings to be applied
            kotlinx.coroutines.delay(100)
        }

        // CRITICAL: Re-read state after applySettings() to get updated settings
        val updatedState = _uiState.value
        if (updatedState !is EditorUiState.Success) return false

        android.util.Log.d("EditorViewModel", "saveProject: About to save project")

        // Step 2: Save new project if unsaved
        if (updatedState.isUnsavedProject) {
            return try {
                val project = updatedState.project
                val assetUris = project.assets.map { it.uri }
                val settings = updatedState.displaySettings  // Use UPDATED state!

                android.util.Log.d("EditorViewModel", "saveProject: Creating new project")

                // Create project in DB with settings
                val result = createProjectUseCase(assetUris, settings)
                result.onSuccess { createdProject ->
                    android.util.Log.d("EditorViewModel", "saveProject: Project created with ID=${createdProject.id}")
                    // Update current project ID
                    currentProjectId = createdProject.id
                    // Wait a bit for DB write to complete before export reads it
                    kotlinx.coroutines.delay(200)
                    // Start observing the DB project - it will update the state
                    // Don't manually update state here to avoid race condition
                    loadExistingProject(createdProject.id)
                }
                result.onFailure { error ->
                    // Show error to user
                    _uiState.value = EditorUiState.Error(
                        error.message ?: "Failed to save project"
                    )
                }
                result.isSuccess
            } catch (e: Exception) {
                // Show error to user
                _uiState.value = EditorUiState.Error(
                    e.message ?: "Failed to save project"
                )
                false
            }
        }

        // Already saved and no pending changes
        return true
    }

    fun addAssets(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val currentState = _uiState.value
        if (currentState !is EditorUiState.Success) return

        if (currentState.isUnsavedProject) {
            // In-memory update
            val existingCount = currentState.project.assets.size
            val newAssets = uris.mapIndexed { index, uri ->
                Asset(
                    id = UUID.randomUUID().toString(),
                    uri = uri,
                    orderIndex = existingCount + index
                )
            }
            _uiState.value = currentState.copy(
                project = currentState.project.copy(
                    assets = currentState.project.assets + newAssets,
                    updatedAt = System.currentTimeMillis()
                )
            )
        } else {
            // DB update
            viewModelScope.launch {
                currentProjectId?.let { id ->
                    addAssetsUseCase(id, uris)
                }
            }
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

        if (currentState.isUnsavedProject) {
            // In-memory update - remove and reindex
            val remainingAssets = currentState.project.assets
                .filter { it.id != assetId }
                .mapIndexed { index, asset ->
                    asset.copy(orderIndex = index)
                }
            _uiState.value = currentState.copy(
                project = currentState.project.copy(
                    assets = remainingAssets,
                    thumbnailUri = remainingAssets.firstOrNull()?.uri,
                    updatedAt = System.currentTimeMillis()
                )
            )
        } else {
            // DB update
            viewModelScope.launch {
                currentProjectId?.let { id ->
                    removeAssetUseCase(id, assetId)
                }
            }
        }

        return true
    }

    fun updateQuality(quality: VideoQuality) {
        val currentState = _uiState.value
        if (currentState is EditorUiState.Success) {
            _uiState.value = currentState.copy(selectedQuality = quality)
            // Preload inter if quality is locked and config = interstitial
            if (isQualityLocked(quality) &&
                adPlacementConfigService.getAdTypeForQuality(quality) == "interstitial") {
                viewModelScope.launch {
                    InterstitialAdHelperExt.preloadInterstitial(
                        adsLoaderService = adsLoaderService,
                        placement = AdPlacement.INTERSTITIAL_UNLOCK_QUALITY,
                        showLoadingOverlay = false
                    )
                }
            }
        }
    }

    fun updateEffectSet(effectSetId: String?) {
        viewModelScope.launch {
            // Load effect set name
            val effectSetName = if (effectSetId != null) {
                getEffectSetName(effectSetId)
            } else {
                "Effect"
            }

            // Update both pending settings and effect set name
            updatePendingSettings { it.copy(effectSetId = effectSetId) }

            // Update effect set name in state
            _uiState.update { state ->
                if (state is EditorUiState.Success) {
                    state.copy(effectSetName = effectSetName)
                } else {
                    state
                }
            }

            // Restart from beginning so user sees the new transitions from the start
            restartPlaybackFromStart()
        }
    }

    /**
     * Fire-and-forget prefetch: warms the beat-sync 3-layer cache while the user previews a song.
     * When updateMusicTrack later calls loadBeatSyncWithRetry, the data is already in memory cache.
     */
    fun prefetchBeatSync(songId: Long, beatsUrl: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            beatSyncRepository.getBeatData(songId, beatsUrl)
        }
    }

    fun updateMusicSong(songId: Long?, songUrl: String? = null) {
        // Cancel any in-flight music update/preprocessing
        musicUpdateJob?.cancel()
        // Fetch song data to get both name and URL
        musicUpdateJob = viewModelScope.launch {
            try {
                // Parallelize song fetch + beat-sync load (independent network calls)
                val (song, beatSyncData) = if (songId != null) {
                    val songDeferred = async { fetchSongWithRetry(songId) }
                    val beatSyncDeferred = async { loadBeatSyncWithRetry(songId) }
                    songDeferred.await() to beatSyncDeferred.await()
                } else {
                    null to null
                }

                // Beat-sync load failure already shows error dialog and returns null
                if (beatSyncData == null && songId != null) {
                    // Error dialog already shown by loadBeatSyncWithRetry
                    return@launch
                }

                // Song data unavailable (deleted/network error) - abort to avoid silent video
                if (songId != null && (song == null || song.mp3Url.isBlank())) {
                    android.util.Log.e("EditorViewModel", "Song data unavailable after retries (songId=$songId) - cannot update music")
                    _showBeatSyncErrorDialog.value = true
                    return@launch
                }

                if (beatSyncData != null) {
                    android.util.Log.i("EditorViewModel", "Beat-sync loaded: BPM=${beatSyncData.bpm}, beats=${beatSyncData.beats.size}")
                }

                // Pre-calculate beat-sync duration (avoid ANR in getter)
                val currentState = _uiState.value as? EditorUiState.Success
                val songTrimStart = song?.hookStartTimes?.firstOrNull() ?: 0L
                val totalDurationMs = if (beatSyncData != null && currentState != null) {
                    Project.calculateBeatSyncDuration(
                        beatData = beatSyncData,
                        assetCount = currentState.project.assets.size,
                        trimStartMs = songTrimStart
                    ) ?: 0L
                } else {
                    0L
                }

                // No preprocessing needed — preview uses realtime fadeout via volume ramp.
                // Export re-preprocesses independently in VideoExportWorker.

                // Build audio node with all data
                val node = AudioNode(
                    songId = songId,
                    songName = song?.name,
                    songArtist = song?.artist,
                    songUrl = song?.mp3Url,
                    coverUrl = song?.coverUrl,
                    startTimeMs = 0L,
                    trimStartMs = song?.hookStartTimes?.firstOrNull() ?: 0L,
                    volume = 1.0f,
                    processedAudioUri = null,
                    hookStartTimes = song?.hookStartTimes ?: emptyList(),
                    songDurationMs = song?.durationMs?.toLong()
                )

                // Update settings once with all assets ready (including preprocessed audio)
                updatePendingSettings {
                    it.copy(
                        audioNodes = listOf(node),
                        // Beat-sync integration
                        beatSyncData = beatSyncData,
                        hookStartTimeMs = song?.hookStartTimes?.firstOrNull() ?: 0L,
                        totalDurationMs = totalDurationMs // Pre-calculated (prevents ANR)
                    )
                }

                // Seek to start and auto-play with new song
                playbackClock.setDuration(totalDurationMs)
                restartPlaybackFromStart()
            } catch (e: CancellationException) {
                throw e // Don't catch cancellation
            } catch (e: Exception) {
                // ANY failure (beat-sync load, song fetch, etc.) → show error dialog
                android.util.Log.e("EditorViewModel", "Music update failed: ${e.message}", e)
                _showBeatSyncErrorDialog.value = true
            }
        }
    }

    fun updateMusicTrack(songId: Long, songName: String, songArtist: String, songUrl: String, songCoverUrl: String, trimStartMs: Long? = null, hookStartTimes: List<Long> = emptyList(), songDurationMs: Long? = null, beatsUrl: String? = null) {
        // Reset error flag from any previous failed music change
        isMusicChangeError = false

        // Save original settings for revert on failure
        val originalState = _uiState.value as? EditorUiState.Success
        val originalSettings = originalState?.pendingSettings ?: originalState?.project?.settings

        // Immediately update display-only fields (MusicSection UI) via primary audio node
        // without touching player-facing fields to avoid triggering error/loading state
        updatePendingSettingsAudioOnly { settings ->
            val existingNode = settings.primaryAudioNode
            val displayNode = (existingNode ?: AudioNode()).copy(
                songName = songName,
                coverUrl = songCoverUrl
            )
            settings.copy(
                audioNodes = listOf(displayNode) + settings.audioNodes.drop(1)
            )
        }

        // Show composing overlay for the entire music change process
        val currentState = _uiState.value as? EditorUiState.Success
        if (currentState != null) {
            _uiState.value = currentState.copy(isProcessingAudio = true)
        }

        // Cancel any in-flight music update/preprocessing
        musicUpdateJob?.cancel()
        musicUpdateJob = viewModelScope.launch {
            try {
                // Beat-sync data has 3-layer cache (memory → file → network)
                // Song data is already passed from the music sheet — no need to re-fetch
                val beatSyncData = loadBeatSyncWithRetry(songId, beatsUrl)

                // Beat-sync load failure already shows error dialog and returns null
                if (beatSyncData == null) {
                    // Revert display changes and mark as music-change error (stay in editor)
                    revertMusicDisplayChanges(originalSettings)
                    isMusicChangeError = true
                    // Error dialog already shown by loadBeatSyncWithRetry
                    return@launch
                }

                android.util.Log.i("EditorViewModel", "Beat-sync loaded: BPM=${beatSyncData.bpm}, beats=${beatSyncData.beats.size}")

                // Resolve the effective trim start: user's scrubber selection, or 0
                val effectiveTrimStart = trimStartMs ?: 0L

                // Pre-calculate beat-sync duration (avoid ANR in getter)
                val currentState = _uiState.value as? EditorUiState.Success
                val totalDurationMs = if (currentState != null) {
                    Project.calculateBeatSyncDuration(
                        beatData = beatSyncData,
                        assetCount = currentState.project.assets.size,
                        trimStartMs = effectiveTrimStart
                    ) ?: 0L
                } else {
                    0L
                }

                if (totalDurationMs <= 0) {
                    throw Exception("Invalid duration for beat-sync calculation")
                }

                // No preprocessing needed — preview uses realtime fadeout via volume ramp.
                // Export re-preprocesses independently in VideoExportWorker.

                // Build audio node with all data
                val node = AudioNode(
                    songId = songId,
                    songName = songName,
                    songArtist = songArtist,
                    songUrl = songUrl,
                    coverUrl = songCoverUrl,
                    startTimeMs = 0L,
                    trimStartMs = effectiveTrimStart,
                    volume = 1.0f,
                    processedAudioUri = null,
                    hookStartTimes = hookStartTimes,
                    songDurationMs = songDurationMs
                )

                // Seek to start — beat-sync timing is completely different with new song
                playbackClock.pause()
                playbackClock.setDuration(totalDurationMs)
                playbackClock.seekTo(0L)
                _currentPositionMs.value = 0L
                _durationMs.value = totalDurationMs

                // Update settings with new audio + auto-play from position 0
                _uiState.update { current ->
                    val latest = current as? EditorUiState.Success ?: return@update current
                    val baseSettings = latest.pendingSettings ?: latest.project.settings
                    latest.copy(
                        pendingSettings = baseSettings.copy(
                            audioNodes = listOf(node),
                            beatSyncData = beatSyncData,
                            hookStartTimeMs = effectiveTrimStart,
                            totalDurationMs = totalDurationMs
                        ),
                        isPlaying = true
                    )
                }
                refreshRenderState()
                playbackClock.play()
            } catch (e: CancellationException) {
                throw e // Don't catch cancellation
            } catch (e: Exception) {
                // Music change failed — revert display, show error, stay in editor
                android.util.Log.e("EditorViewModel", "Music update failed: ${e.message}", e)
                revertMusicDisplayChanges(originalSettings)
                isMusicChangeError = true
                _showBeatSyncErrorDialog.value = true
            } finally {
                // Hide composing overlay regardless of success or failure
                val finalState = _uiState.value as? EditorUiState.Success
                if (finalState != null) {
                    _uiState.value = finalState.copy(isProcessingAudio = false)
                }
            }
        }
    }

    /**
     * Revert display-only audio node changes on music change failure.
     * Restores the original pending settings so the UI shows the previous song info.
     */
    private fun revertMusicDisplayChanges(originalSettings: ProjectSettings?) {
        if (originalSettings == null) return
        _uiState.update { current ->
            val latest = current as? EditorUiState.Success ?: return@update current
            latest.copy(pendingSettings = originalSettings)
        }
    }

    /**
     * Fetch song data with 3 retry attempts.
     * Returns null if song doesn't exist or all retries fail.
     */
    private suspend fun fetchSongWithRetry(songId: Long): MusicSong? {
        val maxRetries = 3
        repeat(maxRetries) { attempt ->
            try {
                val result = withTimeoutOrNull(15_000L) {
                    songRepository.getSongById(songId)
                }
                if (result == null) {
                    android.util.Log.w("EditorViewModel", "Song fetch timed out (attempt ${attempt + 1}/$maxRetries)")
                    if (attempt < maxRetries - 1) {
                        delay(1000L * (attempt + 1))
                    }
                    return@repeat
                }
                if (result.isSuccess) {
                    val song = result.getOrNull()
                    android.util.Log.d("EditorViewModel", "Song fetched: id=${song?.id}, name=${song?.name}, hasUrl=${song?.mp3Url?.isNotBlank()}")
                    return song
                } else {
                    android.util.Log.w("EditorViewModel", "Song fetch failed (attempt ${attempt + 1}/$maxRetries): ${result.exceptionOrNull()?.message}")
                    if (attempt < maxRetries - 1) {
                        delay(1000L * (attempt + 1))
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("EditorViewModel", "Song fetch exception (attempt ${attempt + 1}/$maxRetries)", e)
                if (attempt < maxRetries - 1) {
                    delay(1000L * (attempt + 1))
                }
            }
        }
        android.util.Log.e("EditorViewModel", "Song fetch failed after $maxRetries attempts for songId=$songId")
        return null
    }

    /**
     * Load beat-sync data with 3 retry attempts
     * If all retries fail, show error dialog and navigate back to home
     */
    private suspend fun loadBeatSyncWithRetry(songId: Long, beatsUrl: String? = null): com.videomaker.aimusic.domain.model.BeatSyncData? {
        val maxRetries = 3
        repeat(maxRetries) { attempt ->
            try {
                android.util.Log.i("EditorViewModel", "Loading beat-sync data (attempt ${attempt + 1}/$maxRetries)")

                // Safety-net timeout in case getBeatData hangs despite its own timeouts
                val result = withTimeoutOrNull(25_000L) {
                    beatSyncRepository.getBeatData(songId, beatsUrl)
                }

                if (result == null) {
                    // Timed out at ViewModel level
                    android.util.Log.w("EditorViewModel", "Beat-sync load timed out (attempt ${attempt + 1}/$maxRetries)")
                    if (attempt < maxRetries - 1) {
                        delay(1000L * (attempt + 1))
                    }
                    return@repeat
                }

                if (result.isSuccess) {
                    val data = result.getOrNull()
                    if (data != null) {
                        return data // Success - return the data
                    } else {
                        // Data is null (file doesn't exist) - this is OK, use legacy mode
                        return null
                    }
                } else {
                    // Network or parsing error - retry
                    android.util.Log.w("EditorViewModel", "Beat-sync load failed (attempt ${attempt + 1}): ${result.exceptionOrNull()?.message}")
                    if (attempt < maxRetries - 1) {
                        delay(1000L * (attempt + 1)) // Exponential backoff
                    }
                }
            } catch (e: CancellationException) {
                throw e // Don't catch cancellation
            } catch (e: Exception) {
                android.util.Log.e("EditorViewModel", "Beat-sync load exception (attempt ${attempt + 1})", e)
                if (attempt < maxRetries - 1) {
                    delay(1000L * (attempt + 1)) // Exponential backoff
                }
            }
        }

        // All retries failed - show error dialog and navigate back
        android.util.Log.e("EditorViewModel", "Beat-sync load failed after $maxRetries attempts")
        _showBeatSyncErrorDialog.value = true
        return null
    }

    /**
     * Fetch first effect set ID from Supabase.
     * Falls back to local default if fetch fails or returns empty.
     */
    private suspend fun fetchFirstEffectSetId(): String {
        return effectSetRepository.getEffectSetsPaged(offset = 0, limit = 1)
            .fold(
                onSuccess = { effectSets ->
                    if (effectSets.isNotEmpty()) {
                        val first = effectSets.first()
                        android.util.Log.d("EditorViewModel", "Fetched first effect set: ${first.id} (${first.name})")
                        first.id
                    } else {
                        android.util.Log.w("EditorViewModel", "No effect sets from Supabase, falling back to local default")
                        TransitionSetLibrary.getDefault().id
                    }
                },
                onFailure = { error ->
                    android.util.Log.w("EditorViewModel", "Effect set fetch failed, falling back to local default: ${error.message}")
                    TransitionSetLibrary.getDefault().id
                }
            )
    }

    /**
     * Automatically process audio to match video duration
     * Called when music is selected to ensure audio doesn't extend beyond video
     */
    private fun autoProcessAudioForVideoDuration(songUrl: String) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val currentState = _uiState.value
                if (currentState !is EditorUiState.Success) return@launch

                val sourceUri = Uri.parse(songUrl)
                val videoDurationMs = currentState.project.totalDurationMs

                android.util.Log.d("EditorViewModel", "Auto-processing audio: video duration = ${videoDurationMs}ms")

                // Generate output filename
                val projectId = currentState.project.id
                val filename = com.videomaker.aimusic.media.audio.AudioTranscoder.generateOutputFilename(
                    projectId,
                    0L, // Start from beginning
                    videoDurationMs // End at video duration
                )
                val outputFile = java.io.File(context.cacheDir, filename)

                // Transcode: clip audio from 0 to video duration (no looping, just clip)
                val success = com.videomaker.aimusic.media.audio.AudioTranscoder.transcodeAndLoop(
                    context = context,
                    sourceUri = sourceUri,
                    trimStartMs = 0L,
                    trimEndMs = videoDurationMs,
                    targetDurationMs = videoDurationMs,
                    outputFile = outputFile
                )

                if (success) {
                    android.util.Log.d("EditorViewModel", "Auto-processing successful: ${outputFile.absolutePath}")
                    // Update primary audio node with processed audio — no GL render needed
                    updatePendingSettingsAudioOnly { settings ->
                        settings.copy(
                            audioNodes = settings.audioNodes.mapIndexed { i, node ->
                                if (i == 0) node.copy(processedAudioUri = Uri.fromFile(outputFile).toString()) else node
                            }
                        )
                    }
                } else {
                    android.util.Log.e("EditorViewModel", "Auto-processing failed")
                }
            } catch (e: Exception) {
                android.util.Log.e("EditorViewModel", "Auto-processing exception", e)
            }
        }
    }

    fun updateAudioVolume(volume: Float) {
        // Update the primary audio node's volume — audio only, no GL render needed
        updatePendingSettingsAudioOnly { settings ->
            settings.copy(
                audioNodes = settings.audioNodes.mapIndexed { i, node ->
                    if (i == 0) node.copy(volume = volume) else node
                }
            )
        }
    }

    fun updateAspectRatio(ratio: AspectRatio) {
        // Only the ratio changes. Sticker placements are left untouched: both the preview
        // (frac * rectW) and the export (frac * frameWidth) interpret widthFractionOfVideo
        // relative to the frame, and centers are normalized — so each sticker keeps the same
        // position and frame-relative size across ratios and stays inside the frame, exactly
        // like text overlays (which are also not remapped). Rescaling the fraction here would
        // grow edge stickers when switching to a narrower frame and push them outside.
        updatePendingSettings { it.copy(aspectRatio = ratio) }
        // Restart from beginning so user sees the new aspect ratio from the start
        restartPlaybackFromStart()
    }

    // ============================================
    // STICKER CRUD
    // ============================================
    // Stickers are drawn as a Compose overlay (not via the GL renderer), so these
    // mutate pending settings WITHOUT a GL refresh. They are persisted with the
    // project and read by the export pipeline.

    /**
     * Add a sticker centered on the video, sized to 1/3 of the frame's SHORT side. Because the
     * short side is the same (1080) in every aspect ratio, [StickerPlacement.widthFractionOfVideo]
     * is interpreted against the short side (see [StickerImagesLayer]/[StickerOverlay]), so 1/3
     * stays a sensible, ratio-invariant size — no per-ratio adjustment needed. Each call adds a
     * NEW instance (stacking on top).
     */
    fun addSticker(sticker: com.videomaker.aimusic.domain.model.Sticker): String {
        val instanceId = java.util.UUID.randomUUID().toString()
        updatePendingSettingsAudioOnly { settings ->
            val nextZ = com.videomaker.aimusic.modules.editor.overlay
                .combinedMaxZIndex(settings.textOverlays, settings.stickers) + 1
            val placement = com.videomaker.aimusic.domain.model.StickerPlacement(
                instanceId = instanceId,
                stickerId = sticker.id,
                // 512px original on the video (preview + export); grid uses the 128px thumbnail.
                assetUrl = sticker.fullUrl,
                centerXNorm = 0.5f,
                centerYNorm = 0.5f,
                widthFractionOfVideo = 1f / 3f,
                rotationDeg = 0f,
                opacity = 1f,
                zIndex = nextZ
            )
            settings.copy(stickers = settings.stickers + placement)
        }
        return instanceId
    }

    /** Replace a sticker placement (drag / zoom / rotate result). */
    fun updateStickerPlacement(placement: com.videomaker.aimusic.domain.model.StickerPlacement) {
        updatePendingSettingsAudioOnly { settings ->
            settings.copy(
                stickers = settings.stickers.map {
                    if (it.instanceId == placement.instanceId) placement else it
                }
            )
        }
    }

    /** Remove a sticker instance. */
    fun removeSticker(instanceId: String) {
        updatePendingSettingsAudioOnly { settings ->
            settings.copy(stickers = settings.stickers.filter { it.instanceId != instanceId })
        }
    }

    /**
     * Replace the entire sticker list. Used to revert to the pre-panel snapshot when the
     * sticker picker is cancelled (dismiss), mirroring the effect-set cancel behavior.
     */
    fun setStickers(stickers: List<com.videomaker.aimusic.domain.model.StickerPlacement>) {
        updatePendingSettingsAudioOnly { settings ->
            settings.copy(stickers = stickers)
        }
    }

    /** Raise a sticker above all others (used when selecting it for editing). */
    fun bringStickerToFront(instanceId: String) {
        updatePendingSettingsAudioOnly { settings ->
            val maxZ = settings.stickers.maxOfOrNull { it.zIndex } ?: 0
            settings.copy(
                stickers = settings.stickers.map {
                    if (it.instanceId == instanceId && it.zIndex != maxZ) it.copy(zIndex = maxZ + 1) else it
                }
            )
        }
    }

    // ============================================
    // AUDIO NODE CRUD
    // ============================================

    fun addAudioNode(node: AudioNode) {
        updatePendingSettingsAudioOnly { settings ->
            settings.copy(audioNodes = settings.audioNodes + node)
        }
    }

    fun removeAudioNode(nodeId: String) {
        updatePendingSettingsAudioOnly { settings ->
            settings.copy(audioNodes = settings.audioNodes.filter { it.id != nodeId })
        }
        // Deselect if the removed node was selected
        val currentState = _uiState.value
        if (currentState is EditorUiState.Success && currentState.selectedAudioNodeId == nodeId) {
            _uiState.value = currentState.copy(selectedAudioNodeId = null)
        }
    }

    fun updateAudioNode(nodeId: String, update: (AudioNode) -> AudioNode) {
        updatePendingSettingsAudioOnly { settings ->
            settings.copy(
                audioNodes = settings.audioNodes.map { node ->
                    if (node.id == nodeId) update(node) else node
                }
            )
        }
    }

    fun selectAudioNode(nodeId: String?) {
        val currentState = _uiState.value
        if (currentState is EditorUiState.Success) {
            _uiState.value = currentState.copy(selectedAudioNodeId = nodeId)
        }
    }

    /**
     * Update pending settings AND refresh GL render state.
     * Use for visual changes: effectSet, aspectRatio, overlay, images.
     */
    private fun updatePendingSettings(update: (ProjectSettings) -> ProjectSettings) {
        val currentState = _uiState.value
        if (currentState is EditorUiState.Success) {
            val baseSettings = currentState.pendingSettings ?: currentState.project.settings
            _uiState.value = currentState.copy(pendingSettings = update(baseSettings))
            refreshRenderState()
        }
    }

    /**
     * Update pending settings WITHOUT refreshing GL render state.
     * Use for audio-only changes: volume, audioNodes — no GL render needed.
     */
    private fun updatePendingSettingsAudioOnly(update: (ProjectSettings) -> ProjectSettings) {
        val currentState = _uiState.value
        if (currentState is EditorUiState.Success) {
            val baseSettings = currentState.pendingSettings ?: currentState.project.settings
            _uiState.value = currentState.copy(pendingSettings = update(baseSettings))
        }
    }

    /**
     * Refresh the GL renderer state from current project + pending settings.
     * Called on visual property changes for instant preview updates.
     * Caches transitions and imageUris to avoid re-allocating identical lists.
     */
    private var cachedEffectSetId: String? = null
    private var cachedTransitions: List<com.videomaker.aimusic.domain.model.Transition> = emptyList()
    private var cachedImageUris: List<android.net.Uri> = emptyList()
    private var cachedAssets: List<Asset>? = null

    private fun refreshRenderState() {
        val state = _uiState.value as? EditorUiState.Success ?: return
        val settings = state.displaySettings
        val assets = state.project.assets

        // Cache transitions by effectSetId — only re-resolve when effect set changes
        val effectSetId = settings.effectSetId
        val transitions = if (effectSetId == cachedEffectSetId && cachedTransitions.isNotEmpty()) {
            cachedTransitions
        } else {
            val effectSet = effectSetId?.let { TransitionSetLibrary.getById(it) }
            val resolved = if (effectSet != null) {
                val r = effectSet.transitionIds.mapNotNull {
                    com.videomaker.aimusic.media.library.TransitionShaderLibrary.getById(it)
                }
                r.ifEmpty {
                    listOfNotNull(com.videomaker.aimusic.media.library.TransitionShaderLibrary.getDefault())
                }
            } else {
                listOfNotNull(com.videomaker.aimusic.media.library.TransitionShaderLibrary.getDefault())
            }
            cachedEffectSetId = effectSetId
            cachedTransitions = resolved
            resolved
        }

        // Cache imageUris — only rebuild when assets list changes (reference equality)
        val imageUris = if (assets === cachedAssets) {
            cachedImageUris
        } else {
            val uris = assets.map { it.uri }
            cachedImageUris = uris
            cachedAssets = assets
            uris
        }

        _renderState.value = RenderState(
            imageUris = imageUris,
            transitions = transitions,
            beatSyncData = settings.beatSyncData,
            aspectRatio = settings.aspectRatio,
            overlayFrameId = settings.overlayFrameId,
            hookStartTimeMs = settings.hookStartTimeMs,
            totalDurationMs = settings.totalDurationMs
        )

        playbackClock.setDuration(settings.totalDurationMs)
        _durationMs.value = settings.totalDurationMs
    }

    suspend fun applySettings() {
        val currentState = _uiState.value
        if (currentState is EditorUiState.Success && currentState.pendingSettings != null) {
            val newSettings = currentState.pendingSettings
            android.util.Log.d("EditorViewModel", "applySettings: Applying settings")

            val updatedProject = currentState.project.copy(
                settings = newSettings,
                updatedAt = System.currentTimeMillis()
            )
            _uiState.value = currentState.copy(project = updatedProject, pendingSettings = null)

            if (!currentState.isUnsavedProject) {
                // DB update - WAIT for it to complete before returning
                currentProjectId?.let { id ->
                    android.util.Log.d("EditorViewModel", "applySettings: Saving to DB for project $id")
                    updateSettingsUseCase(id, newSettings)
                    android.util.Log.d("EditorViewModel", "applySettings: DB save completed")
                }
            } else {
                android.util.Log.d("EditorViewModel", "applySettings: Unsaved project - settings only in memory")
            }
        } else {
            android.util.Log.d("EditorViewModel", "applySettings: No pending settings to apply")
        }
    }

    fun discardPendingSettings() {
        val currentState = _uiState.value
        if (currentState is EditorUiState.Success) {
            _uiState.value = currentState.copy(pendingSettings = null)
        }
    }

    // ============================================
    // PENDING ASSETS (for ImagesBottomSheet)
    // ============================================

    /**
     * Set pending assets for temporary editing in ImagesBottomSheet
     * This creates a temporary copy that won't trigger video rebuild until confirmed
     */
    fun setPendingAssets(assets: List<Asset>) {
        val currentState = _uiState.value
        if (currentState is EditorUiState.Success) {
            _uiState.value = currentState.copy(pendingAssets = assets)
        }
    }

    /**
     * Recalculate project settings when asset count changes.
     * If beat-sync music is present and image count changed, recalculates duration
     * and reprocesses audio with new fadeout timing.
     * Shows processing overlay during audio reprocessing.
     */
    private suspend fun recalculateSettingsForAssets(
        assets: List<Asset>,
        currentState: EditorUiState.Success
    ): ProjectSettings {
        val beatSyncData = currentState.project.settings.beatSyncData
        val primaryNode = currentState.project.settings.primaryAudioNode

        if (beatSyncData == null || primaryNode?.songId == null) {
            // No beat-sync — settings unchanged
            return currentState.project.settings
        }

        val newDuration = Project.calculateBeatSyncDuration(
            beatData = beatSyncData,
            assetCount = assets.size,
            trimStartMs = currentState.project.settings.hookStartTimeMs
        ) ?: currentState.project.settings.totalDurationMs

        val durationChanged = newDuration != currentState.project.settings.totalDurationMs
        if (!durationChanged) {
            // Same duration — just update totalDurationMs (may differ slightly)
            return currentState.project.settings.copy(totalDurationMs = newDuration)
        }

        // Duration changed — update duration. No preprocessing needed for preview
        // (realtime fadeout via volume ramp). Export re-preprocesses independently.
        android.util.Log.d("EditorViewModel", "recalculateSettingsForAssets: Duration ${currentState.project.settings.totalDurationMs}ms → ${newDuration}ms (${assets.size} images)")

        return currentState.project.settings.copy(totalDurationMs = newDuration)
    }

    /**
     * Apply pending assets to the project.
     * GL renderer updates instantly (new images visible immediately).
     * Audio reprocessing (if image count changed) runs in background — no overlay.
     */
    fun applyPendingAssets(assets: List<Asset>) {
        val currentState = _uiState.value
        if (currentState !is EditorUiState.Success) {
            android.util.Log.d("EditorViewModel", "applyPendingAssets: Not in Success state")
            return
        }

        android.util.Log.d("EditorViewModel", "applyPendingAssets: Applying ${assets.size} assets (was ${currentState.project.assets.size})")

        // 1. Update project with new assets IMMEDIATELY — GL renderer sees them right away.
        //    Use current settings (audio may be stale if duration changed — fixed in step 2).
        val immediateSettings = currentState.project.settings
        val updatedProject = currentState.project.copy(
            assets = assets,
            settings = immediateSettings,
            updatedAt = System.currentTimeMillis()
        )

        _uiState.update { current ->
            val latest = current as? EditorUiState.Success ?: return@update current
            latest.copy(
                project = updatedProject,
                pendingAssets = null,
                isPlaying = true
            )
        }

        // GL renderer picks up new images instantly
        refreshRenderState()
        restartPlaybackFromStart()

        android.util.Log.d("EditorViewModel", "applyPendingAssets: ${assets.size} assets visible, checking audio...")

        // 2. Recalculate duration + reprocess audio in background if image count changed.
        //    No overlay — GL preview already shows new images.
        viewModelScope.launch {
            assetMutex.withLock {
                val stateAfterVisualUpdate = _uiState.value as? EditorUiState.Success ?: return@withLock
                val updatedSettings = recalculateSettingsForAssets(
                    assets = assets,
                    currentState = stateAfterVisualUpdate
                )

                // If settings actually changed (new duration/audio), apply them
                if (updatedSettings != stateAfterVisualUpdate.project.settings) {
                    playbackClock.setDuration(updatedSettings.totalDurationMs)

                    _uiState.update { current ->
                        val latest = current as? EditorUiState.Success ?: return@update current
                        latest.copy(
                            project = latest.project.copy(settings = updatedSettings),
                            isProcessingAudio = false
                        )
                    }

                    _durationMs.value = updatedSettings.totalDurationMs
                    android.util.Log.d("EditorViewModel", "applyPendingAssets: audio updated, ${updatedSettings.totalDurationMs}ms")
                }
            }
        }
    }

    /**
     * Discard pending assets without applying
     */
    fun discardPendingAssets() {
        val currentState = _uiState.value
        if (currentState is EditorUiState.Success) {
            _uiState.value = currentState.copy(pendingAssets = null)
        }
    }

    /**
     * Replace project assets with selected URIs from AssetPicker (editing mode).
     * GL renderer updates instantly (new images visible immediately).
     * Audio reprocessing (if image count changed) runs in background — no overlay.
     */
    fun replaceAssetsFromUris(selectedUris: List<String>) {
        val currentState = _uiState.value
        if (currentState !is EditorUiState.Success) return

        android.util.Log.d("EditorViewModel", "Replace assets: ${currentState.project.assets.size} → ${selectedUris.size} images")

        // Map existing assets by URI for quick lookup
        val existingAssetsByUri = currentState.project.assets.associateBy { it.uri.toString() }

        // Build final asset list maintaining selection order
        val finalAssets = selectedUris.mapIndexedNotNull { index, uriString ->
            existingAssetsByUri[uriString]?.copy(orderIndex = index)
                ?: android.net.Uri.parse(uriString)?.let { uri ->
                    Asset(
                        id = UUID.randomUUID().toString(),
                        uri = uri,
                        orderIndex = index
                    )
                }
        }

        if (finalAssets.isEmpty()) {
            android.util.Log.w("EditorViewModel", "replaceAssetsFromUris: No valid assets to apply")
            return
        }

        // Check if assets actually changed to avoid unnecessary work
        val currentAssetUris = currentState.project.assets.map { it.uri.toString() }
        if (currentAssetUris == selectedUris) {
            android.util.Log.d("EditorViewModel", "replaceAssetsFromUris: Assets unchanged, skipping update")
            _uiState.value = currentState.copy(pendingAssets = null)
            return
        }

        // 1. Update project with new assets IMMEDIATELY — GL renderer sees them right away.
        val updatedProject = currentState.project.copy(
            assets = finalAssets,
            updatedAt = System.currentTimeMillis()
        )

        _uiState.update { current ->
            val latest = current as? EditorUiState.Success ?: return@update current
            latest.copy(
                project = updatedProject,
                pendingAssets = null,
                isPlaying = true
            )
        }

        // GL renderer picks up new images instantly
        refreshRenderState()
        restartPlaybackFromStart()

        android.util.Log.d("EditorViewModel", "replaceAssetsFromUris: ${finalAssets.size} assets visible, checking audio...")

        // 2. Recalculate duration + reprocess audio in background if image count changed.
        //    Also save to DB in background. No overlay — GL preview already shows new images.
        viewModelScope.launch {
            assetMutex.withLock {
                val stateAfterVisualUpdate = _uiState.value as? EditorUiState.Success ?: return@withLock
                val updatedSettings = recalculateSettingsForAssets(
                    assets = finalAssets,
                    currentState = stateAfterVisualUpdate
                )

                if (updatedSettings != stateAfterVisualUpdate.project.settings) {
                    playbackClock.setDuration(updatedSettings.totalDurationMs)

                    _uiState.update { current ->
                        val latest = current as? EditorUiState.Success ?: return@update current
                        latest.copy(
                            project = latest.project.copy(settings = updatedSettings),
                            isProcessingAudio = false
                        )
                    }

                    _durationMs.value = updatedSettings.totalDurationMs
                    android.util.Log.d("EditorViewModel", "replaceAssetsFromUris: audio updated, ${updatedSettings.totalDurationMs}ms")
                }
            }

            // Save to database in background (saved projects only)
            if (!currentState.isUnsavedProject) {
                withContext(Dispatchers.IO) {
                    try {
                        val existingAssetIds = currentState.project.assets.map { it.id }.toSet()
                        val newAssetIds = finalAssets.map { it.id }.toSet()

                        val assetsToRemove = currentState.project.assets.filter { it.id !in newAssetIds }
                        assetsToRemove.forEach { asset ->
                            projectRepository.removeAsset(updatedProject.id, asset.id)
                        }

                        val newAssets = finalAssets.filter { it.id !in existingAssetIds }
                        if (newAssets.isNotEmpty()) {
                            projectRepository.addAssets(updatedProject.id, newAssets.map { it.uri })
                        }

                        projectRepository.reorderAssets(updatedProject.id, finalAssets)

                        val latestState = _uiState.value as? EditorUiState.Success
                        val latestSettings = latestState?.project?.settings
                        if (latestSettings != null && latestSettings != currentState.project.settings) {
                            updateSettingsUseCase(updatedProject.id, latestSettings)
                        }

                        android.util.Log.d("EditorViewModel", "Saved ${finalAssets.size} assets to database (removed: ${assetsToRemove.size}, added: ${newAssets.size})")
                    } catch (e: Exception) {
                        android.util.Log.e("EditorViewModel", "❌ Failed to save - ${e.message}", e)
                    }
                }
            }
        }
    }

    // ============================================
    // PLAYBACK CONTROLS
    // ============================================

    /**
     * Start a coroutine-based position ticker that reads from PlaybackClock
     * at 4Hz to update the UI slider. Replaces the Handler-based position
     * tracking that was previously in VideoPreviewPlayer.
     */
    private fun startPositionTicker() {
        positionTickerJob?.cancel()
        positionTickerJob = viewModelScope.launch {
            while (true) {
                val timeMs = playbackClock.currentTimeMs()
                val duration = playbackClock.totalDurationMs
                // Write to dedicated StateFlows — no full EditorUiState copy needed.
                // Only slider/time-label composables observe these, not the entire tree.
                _currentPositionMs.value = timeMs
                _durationMs.value = duration
                delay(250) // 4Hz update for slider
            }
        }
    }

    fun togglePlayback() {
        val currentState = _uiState.value
        if (currentState is EditorUiState.Success) {
            val newIsPlaying = !currentState.isPlaying
            _uiState.value = currentState.copy(isPlaying = newIsPlaying)
            if (newIsPlaying) playbackClock.play() else playbackClock.pause()
        }
    }

    fun setPlaybackState(isPlaying: Boolean) {
        val currentState = _uiState.value
        if (currentState is EditorUiState.Success) {
            _uiState.value = currentState.copy(isPlaying = isPlaying)
            if (isPlaying) playbackClock.play() else playbackClock.pause()
        }
    }

    /**
     * Called when editor screen pauses (app backgrounded, screen off, etc.).
     * Remembers play state so it can resume when screen returns.
     */
    fun onScreenPause() {
        val currentState = _uiState.value as? EditorUiState.Success ?: return
        wasPlayingBeforeScreenPause = currentState.isPlaying
        if (currentState.isPlaying) {
            _uiState.value = currentState.copy(isPlaying = false)
            playbackClock.pause()
        }
    }

    /**
     * Called when editor screen resumes (app foregrounded).
     * Resumes playback if it was playing before the pause.
     */
    fun onScreenResume() {
        if (!wasPlayingBeforeScreenPause) return
        wasPlayingBeforeScreenPause = false
        val currentState = _uiState.value as? EditorUiState.Success ?: return
        _uiState.value = currentState.copy(isPlaying = true)
        playbackClock.play()
    }

    /**
     * Seek to position 0 and start playing.
     * Called after visual property changes (effect set, aspect ratio) to match old
     * CompositionPlayer behavior where every rebuild restarted from the beginning.
     */
    private fun restartPlaybackFromStart() {
        playbackClock.seekTo(0L)
        _currentPositionMs.value = 0L
        _uiState.update { state ->
            if (state is EditorUiState.Success) state.copy(isPlaying = true) else state
        }
        playbackClock.play()
    }

    fun stopPlayback() {
        val currentState = _uiState.value
        if (currentState is EditorUiState.Success) {
            _uiState.value = currentState.copy(
                wasPlayingBeforeSeek = currentState.isPlaying,
                isPlaying = false
            )
            playbackClock.pause()
        }
    }

    fun seekTo(positionMs: Long) {
        val currentState = _uiState.value
        if (currentState is EditorUiState.Success) {
            _uiState.value = currentState.copy(seekToPosition = positionMs)
            playbackClock.seekTo(positionMs)
        }
    }

    fun scrubTo(positionMs: Long) {
        val currentState = _uiState.value
        if (currentState is EditorUiState.Success) {
            _uiState.value = currentState.copy(scrubToPosition = positionMs)
            playbackClock.seekTo(positionMs)
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
            if (shouldResume) playbackClock.play()
        }
    }

    // ============================================
    // NAVIGATION — separate StateFlow, never in UI state
    // ============================================

    fun navigateBack() {
        // Check if back button ad is ready (non-blocking)
        val isAdReady = adsLoaderService.isInterstitialReady(com.videomaker.aimusic.core.constants.AdPlacement.INTERSTITIAL_EDITOR_BACK)

        android.util.Log.d("EditorViewModel", "🔙 navigateBack - Ad ready: $isAdReady")

        // Send navigation event with ad status (Channel - one-time event, no replay)
        // Screen will show ad if ready, otherwise navigate immediately
        viewModelScope.launch {
            _navigationEvent.send(EditorNavigationEvent.RequestBackWithAd(isAdReady))
        }
    }

    /**
     * Check if export requires ad unlock.
     * All qualities are locked until user watches a rewarded ad.
     */
    fun isQualityLocked(quality: VideoQuality): Boolean {
        return !_isQualityUnlocked.value
    }

    /**
     * Whether the unlock ad type for this quality is "interstitial".
     * Used to suppress the [AD] badge for interstitial qualities (the interstitial
     * is shown on export anyway; the badge only makes sense for rewarded unlocks).
     */
    fun isQualityInterstitialAd(quality: VideoQuality): Boolean {
        return adPlacementConfigService.getAdTypeForQuality(quality) == "interstitial"
    }

    /**
     * Handle Done button click.
     * If quality is locked, present rewarded ad.
     * If unlocked, proceed to export.
     */
    fun onDoneClick() {
        val currentState = _uiState.value
        if (currentState !is EditorUiState.Success) return

        if (isQualityLocked(currentState.selectedQuality)) {
            val adType = adPlacementConfigService.getAdTypeForQuality(currentState.selectedQuality)
            if (adType == "interstitial") {
                android.util.Log.d("EditorViewModel", "📺 Quality locked → showing interstitial")
                viewModelScope.launch {
                    _navigationEvent.send(EditorNavigationEvent.RequestQualityInterstitial)
                }
            } else {
                // Rewarded flow (default)
                qualityAdController.requestAd(
                    onReward = {
                        android.util.Log.d("EditorViewModel", "✅ Quality unlocked for session")
                        _isQualityUnlocked.value = true
                        navigateToExport()
                    },
                    onSkip = {
                        android.util.Log.d("EditorViewModel", "⏭️ Ad disabled - unlocking quality for free")
                        _isQualityUnlocked.value = true
                        navigateToExport()
                    },
                    checkEnabled = { adsLoaderService.canLoadAd(AdPlacement.REWARD_UNLOCK_QUALITY) }
                )
            }
        } else {
            // Quality unlocked or free - proceed to export
            navigateToExport()
        }
    }

    /**
     * Called by UI after user earns reward from watching quality unlock ad
     */
    fun onQualityRewardEarned() {
        qualityAdController.onRewardEarned()
    }

    /**
     * Called by UI when quality ad fails to load or user closes ad without watching
     */
    fun onQualityAdFailed() {
        qualityAdController.onAdFailed()
    }

    /**
     * Show error message for quality ad (e.g., ad not available).
     */
    fun showQualityAdError(message: String) {
        _qualityAdError.value = message
    }

    /**
     * Clear quality ad error message after shown.
     */
    fun onQualityAdErrorShown() {
        _qualityAdError.value = null
    }

    /**
     * Called by EditorScreen when quality unlock interstitial closes.
     * Unlocks quality for this session and proceeds to export.
     */
    fun onQualityInterstitialClosed() {
        android.util.Log.d("EditorViewModel", "✅ Quality interstitial closed — unlocking for session")
        _isQualityUnlocked.value = true
        navigateToExport()
    }

    fun navigateToExport() {
        val currentState = _uiState.value
        if (currentState !is EditorUiState.Success) return

        // Always save before export (applies pending settings and saves new projects)
        viewModelScope.launch {
            if (saveProject()) {
                currentProjectId?.let { id ->
                    // Pass selected quality to export screen (not saved to DB)
                    _navigationEvent.send(EditorNavigationEvent.NavigateToExport(id, currentState.selectedQuality))
                }
            }
            // If save failed, user sees error message and stays in editor
        }
    }

    fun onBeatSyncErrorDismissed() {
        Analytics.trackEditorErrorDialog("dismiss_back_home")
        _showBeatSyncErrorDialog.value = false
        if (isMusicChangeError) {
            // Music change failed — stay in editor with previous song, resume playback
            isMusicChangeError = false
            val currentState = _uiState.value as? EditorUiState.Success
            if (currentState != null && !currentState.isPlaying) {
                _uiState.value = currentState.copy(isPlaying = true)
                playbackClock.play()
            }
        } else {
            // Initial load failed — navigate back to home (no ad on error case)
            viewModelScope.launch {
                _navigationEvent.send(EditorNavigationEvent.RequestBackWithAd(shouldShowAd = false))
            }
        }
    }

    /** Dismiss the network/beat-sync error dialog and re-run the load. */
    fun onBeatSyncErrorRetry() {
        Analytics.trackEditorErrorDialog("retry")
        _showBeatSyncErrorDialog.value = false
        // Clear cached null so re-fetch hits the network instead of returning cached failure
        val songId = initialData?.musicSongId
            ?: (_uiState.value as? EditorUiState.Success)?.project?.settings?.primaryAudioNode?.songId
        if (songId != null) {
            beatSyncRepository.clearErrorCache(songId)
        }
        retry()
    }

    override fun onCleared() {
        super.onCleared()

        // Cancel project observer and in-flight music updates
        projectObserverJob?.cancel()
        projectObserverJob = null
        musicUpdateJob?.cancel()
        musicUpdateJob = null
        positionTickerJob?.cancel()
        positionTickerJob = null

    }
}

private fun AspectRatio.toAnalyticsRatioSize(): String = when (this) {
    AspectRatio.RATIO_16_9 -> "16:9"
    AspectRatio.RATIO_9_16 -> "9:16"
    AspectRatio.RATIO_4_5 -> "4:5"
    AspectRatio.RATIO_1_1 -> "1:1"
}
