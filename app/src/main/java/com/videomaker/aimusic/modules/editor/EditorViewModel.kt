package com.videomaker.aimusic.modules.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.net.Uri
import com.videomaker.aimusic.core.ads.AdPlacementConfigService
import com.videomaker.aimusic.core.ads.InterstitialAdHelperExt
import com.videomaker.aimusic.core.ads.RewardedAdController
import com.videomaker.aimusic.core.analytics.Analytics
import com.videomaker.aimusic.core.constants.AdPlacement
import com.videomaker.aimusic.domain.model.Asset
import com.videomaker.aimusic.domain.model.AspectRatio
// DurationPlanner removed - beat-sync only mode
import com.videomaker.aimusic.domain.model.EditorInitialData
import com.videomaker.aimusic.domain.model.MusicSong
import com.videomaker.aimusic.domain.model.Project
import com.videomaker.aimusic.domain.model.ProjectSettings
import com.videomaker.aimusic.domain.model.VideoQuality
import com.videomaker.aimusic.domain.repository.EffectSetRepository
import com.videomaker.aimusic.domain.repository.SongRepository
import com.videomaker.aimusic.domain.usecase.AddAssetsUseCase
import com.videomaker.aimusic.domain.usecase.CreateProjectUseCase
import com.videomaker.aimusic.domain.usecase.GetProjectUseCase
import com.videomaker.aimusic.domain.usecase.RemoveAssetUseCase
import com.videomaker.aimusic.domain.usecase.UpdateProjectSettingsUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
import java.util.UUID

// ============================================
// UI STATE
// ============================================

sealed class EditorUiState {
    data object Loading : EditorUiState()

    data class Success(
        val project: Project,
        val isUnsavedProject: Boolean = false,
        val selectedAssetIndex: Int = 0,
        val isPlaying: Boolean = false,
        val showSettingsPanel: Boolean = false,
        val pendingSettings: ProjectSettings? = null,
        val pendingAssets: List<Asset>? = null, // Temporary assets for ImagesBottomSheet - not saved/rebuilt until confirmed
        val currentPositionMs: Long = 0L,
        val durationMs: Long = 0L,
        val seekToPosition: Long? = null,
        val scrubToPosition: Long? = null,
        val wasPlayingBeforeSeek: Boolean = false,
        val selectedQuality: VideoQuality = VideoQuality.DEFAULT,
        val effectSetName: String = "Effect",
        val isMusicCached: Boolean = true, // Music fully downloaded and ready for export
        val isCachingMusic: Boolean = false, // Currently downloading music to cache
        val isProcessingAudio: Boolean = false // Currently preprocessing audio with fadeout
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

    private val _uiState = MutableStateFlow<EditorUiState>(EditorUiState.Loading)
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    // Navigation events are separate from UI state — never embedded in high-frequency state
    // Using Channel for one-time events (Google pattern) - prevents replay on config change
    private val _navigationEvent = Channel<EditorNavigationEvent>()
    val navigationEvent = _navigationEvent.receiveAsFlow()

    // Music Trimmer State — modal bottom sheet with isolated music player
    private val _musicTrimmerState = MutableStateFlow<MusicTrimmerState>(MusicTrimmerState.Closed)
    val musicTrimmerState: StateFlow<MusicTrimmerState> = _musicTrimmerState.asStateFlow()

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

    // Mutex for thread-safe trimmer operations (prevents race conditions)
    private val trimStateMutex = Mutex()

    // Track the actual project ID (might be generated for new projects)
    private var currentProjectId: String? = projectId
    private var hasTrackedReadyToEditGenerateComplete = false

    // Observer job for existing projects
    private var projectObserverJob: Job? = null

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
    private suspend fun getAudioUriSync(settings: ProjectSettings): Uri? {
        // Priority 1: Custom audio from device
        settings.customAudioUri?.let { return it }

        // Priority 2: Music song URL (cached)
        settings.musicSongUrl?.let { url ->
            if (url.isNotBlank()) {
                return Uri.parse(url)
            }
        }

        // Priority 3: Look up song from repository
        settings.musicSongId?.let { songId ->
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

    private fun loadExistingProject(id: String) {
        projectObserverJob?.cancel()
        projectObserverJob = viewModelScope.launch {
            _uiState.value = EditorUiState.Loading
            try {
                getProjectUseCase.observe(id).collect { loadedProject ->
                    if (loadedProject != null) {
                        val currentState = _uiState.value
                        val prev = currentState as? EditorUiState.Success

                        val selectedIndex = prev?.selectedAssetIndex
                            ?.coerceIn(0, loadedProject.assets.lastIndex.coerceAtLeast(0)) ?: 0

                        // Backfill missing cover URL for projects saved before cover URL was persisted
                        var project = loadedProject
                        if (project.settings.musicSongId != null && project.settings.musicSongCoverUrl == null) {
                            val song = try { fetchSongWithRetry(project.settings.musicSongId) } catch (_: Exception) { null }
                            if (song?.coverUrl?.isNotBlank() == true) {
                                project = project.copy(
                                    settings = project.settings.copy(musicSongCoverUrl = song.coverUrl)
                                )
                            }
                        }

                        // Load beat-sync data if music is selected but beatSyncData is null
                        // (beatSyncData is not persisted in database, must be loaded from Supabase)
                        if (project.settings.musicSongId != null && project.settings.beatSyncData == null) {
                            android.util.Log.d("EditorViewModel", "Loading beat-sync data for saved project: ${project.settings.musicSongId}")

                            val beatSyncData = try {
                                loadBeatSyncWithRetry(project.settings.musicSongId)
                            } catch (e: Exception) {
                                android.util.Log.e("EditorViewModel", "Failed to load beat-sync data", e)
                                null
                            }

                            if (beatSyncData != null) {
                                // Calculate total duration with beat-sync data
                                val totalDurationMs = Project.calculateBeatSyncDuration(
                                    beatData = beatSyncData,
                                    assetCount = project.assets.size,
                                    trimStartMs = 0L
                                ) ?: 0L

                                // Update project with beat-sync data
                                project = project.copy(
                                    settings = project.settings.copy(
                                        beatSyncData = beatSyncData,
                                        totalDurationMs = totalDurationMs
                                    )
                                )

                                android.util.Log.d("EditorViewModel", "Beat-sync data loaded for saved project: BPM=${beatSyncData.bpm}")
                            } else {
                                // Beat-sync data load failed - error dialog already shown
                                android.util.Log.e("EditorViewModel", "Beat-sync data required but failed to load")
                                return@collect
                            }
                        }

                        // Refresh processedAudioUri so it points to the volume-agnostic cache file.
                        // Stored URIs from older builds may target stale per-volume cache files
                        // (e.g. *_vol0.m4a) or files that were evicted by the LRU cleanup.
                        val onlyOnFirstLoad = prev == null
                        if (onlyOnFirstLoad &&
                            project.settings.musicSongId != null &&
                            project.settings.musicSongUrl != null &&
                            project.settings.beatSyncData != null &&
                            project.settings.totalDurationMs > 0
                        ) {
                            val refreshedUri = try {
                                withContext(Dispatchers.IO) {
                                    preprocessBeatSyncAudio(
                                        songId = project.settings.musicSongId,
                                        songUrl = project.settings.musicSongUrl,
                                        beatSyncData = project.settings.beatSyncData,
                                        totalDurationMs = project.settings.totalDurationMs,
                                        trimStartMs = project.settings.hookStartTimeMs
                                    )
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("EditorViewModel", "Failed to refresh preprocessed audio on load", e)
                                project.settings.processedAudioUri
                            }
                            if (refreshedUri != project.settings.processedAudioUri) {
                                project = project.copy(
                                    settings = project.settings.copy(processedAudioUri = refreshedUri)
                                )
                            }
                        }

                        // Load effect set name
                        val effectSetName = getEffectSetName(project.settings.effectSetId)

                        _uiState.value = EditorUiState.Success(
                            project = project,
                            isUnsavedProject = false,
                            selectedAssetIndex = selectedIndex,
                            isPlaying = prev?.isPlaying ?: false,
                            showSettingsPanel = prev?.showSettingsPanel ?: false,
                            pendingSettings = prev?.pendingSettings,
                            currentPositionMs = prev?.currentPositionMs ?: 0L,
                            durationMs = prev?.durationMs ?: 0L,
                            seekToPosition = prev?.seekToPosition,
                            scrubToPosition = prev?.scrubToPosition,
                            wasPlayingBeforeSeek = prev?.wasPlayingBeforeSeek ?: false,
                            effectSetName = effectSetName
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

    private fun initializeNewProject(data: EditorInitialData) {
        viewModelScope.launch {
            _uiState.value = EditorUiState.Loading
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

                // Fetch song data with retry (name + URL) to ensure consistency across app
                val song = data.musicSongId?.let { songId ->
                    fetchSongWithRetry(songId)
                }

                // Load beat-sync data if music is selected
                val beatSyncData = data.musicSongId?.let { songId ->
                    loadBeatSyncWithRetry(songId)
                }

                // CRITICAL: If music is selected but beat-sync data failed to load, return early
                // Error dialog is already shown by loadBeatSyncWithRetry, user will be navigated back
                if (data.musicSongId != null && beatSyncData == null) {
                    android.util.Log.e("EditorViewModel", "Beat-sync data required but failed to load - aborting initialization")
                    return@launch
                }

                // CRITICAL: If music is selected but song data couldn't be fetched (deleted/network error),
                // abort to avoid silent video (beat-sync works but no audio URL to play)
                if (data.musicSongId != null && (song == null || song.mp3Url.isBlank())) {
                    android.util.Log.e("EditorViewModel", "Song data required but unavailable after retries (songId=${data.musicSongId}, song=${song?.name}, mp3Url=${song?.mp3Url}) - aborting initialization")
                    _showBeatSyncErrorDialog.value = true
                    return@launch
                }

                // Fetch first effect set from Supabase if not provided
                val effectSetId = if (data.effectSetId == null) {
                    android.util.Log.d("EditorViewModel", "Effect set not provided, fetching first from Supabase...")
                    val result = effectSetRepository.getEffectSetsPaged(offset = 0, limit = 1)

                    result.fold(
                        onSuccess = { effectSets ->
                            if (effectSets.isNotEmpty()) {
                                val firstEffectSet = effectSets.first()
                                android.util.Log.d("EditorViewModel", "✅ Fetched first effect set: ${firstEffectSet.id} (${firstEffectSet.name})")
                                firstEffectSet.id
                            } else {
                                android.util.Log.e("EditorViewModel", "No effect sets found in Supabase")
                                _showBeatSyncErrorDialog.value = true
                                return@launch
                            }
                        },
                        onFailure = { error ->
                            android.util.Log.e("EditorViewModel", "Failed to fetch first effect set: ${error.message}", error)
                            _showBeatSyncErrorDialog.value = true
                            return@launch
                        }
                    )
                } else {
                    data.effectSetId
                }

                // Calculate hook start time (use song's default or 0)
                val hookStartTimeMs = if (data.applyHookStartDefaults) {
                    song?.hookStartTimeMs ?: 0L
                } else {
                    0L
                }

                // Calculate total duration from beat-sync data
                val totalDurationMs = if (beatSyncData != null) {
                    Project.calculateBeatSyncDuration(
                        beatData = beatSyncData,
                        assetCount = imageUris.size,
                        trimStartMs = 0L
                    ) ?: 0L
                } else {
                    0L
                }

                // Preprocess audio BEFORE setting UI state (wait for all assets ready)
                // This prevents double "Preparing video" by ensuring everything is ready before first preview
                val preprocessedUri = if (beatSyncData != null && data.musicSongId != null && song?.mp3Url != null && totalDurationMs > 0) {
                    try {
                        withContext(Dispatchers.IO) {
                            preprocessBeatSyncAudio(
                                songId = data.musicSongId,
                                songUrl = song.mp3Url,
                                beatSyncData = beatSyncData,
                                totalDurationMs = totalDurationMs,
                                trimStartMs = hookStartTimeMs
                            )
                        }.also {
                            android.util.Log.d("EditorViewModel", "✅ Template audio preprocessed with fadeout")
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("EditorViewModel", "Failed to preprocess template audio", e)
                        null // Non-fatal: user can still preview without preprocessed audio
                    }
                } else {
                    null
                }

                // Beat-sync only: settings with loaded beat-sync data AND preprocessed audio
                val settings = ProjectSettings(
                    beatSyncData = beatSyncData,
                    hookStartTimeMs = hookStartTimeMs,
                    totalDurationMs = totalDurationMs,
                    effectSetId = effectSetId,  // Use fetched effect set (or provided one)
                    templateId = data.templateId,
                    musicSongId = data.musicSongId,
                    musicSongName = song?.name, // For display in UI
                    musicSongUrl = song?.mp3Url, // For playback (same URL as previewer)
                    musicSongCoverUrl = song?.coverUrl, // For display in music section
                    processedAudioUri = preprocessedUri, // Already preprocessed - ready for preview
                    aspectRatio = data.aspectRatio
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

                _uiState.value = EditorUiState.Success(
                    project = project,
                    isUnsavedProject = true,
                    effectSetName = effectSetName
                )
                trackVideoGenerateCompleteReadyToEdit(
                    data = data,
                    totalDurationMs = settings.totalDurationMs,
                    mediaQuantity = assets.size
                )
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
        }
    }

    fun updateMusicSong(songId: Long?, songUrl: String? = null) {
        // Fetch song data to get both name and URL
        viewModelScope.launch {
            try {
                val song = songId?.let { fetchSongWithRetry(it) }

                // Load beat-sync data with retry if song is selected
                val beatSyncData = songId?.let { loadBeatSyncWithRetry(it) }

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
                val totalDurationMs = if (beatSyncData != null && currentState != null) {
                    Project.calculateBeatSyncDuration(
                        beatData = beatSyncData,
                        assetCount = currentState.project.assets.size,
                        trimStartMs = 0L
                    ) ?: 0L
                } else {
                    0L
                }

                // Preprocess audio BEFORE updating settings (wait for all assets ready)
                // This prevents double "Preparing video" by ensuring everything is ready before update
                val preprocessedUri = if (beatSyncData != null && songId != null && song?.mp3Url != null && totalDurationMs > 0) {
                    val trimStartMs = song.hookStartTimeMs ?: 0L
                    withContext(Dispatchers.IO) {
                        preprocessBeatSyncAudio(
                            songId = songId,
                            songUrl = song.mp3Url,
                            beatSyncData = beatSyncData,
                            totalDurationMs = totalDurationMs,
                            trimStartMs = trimStartMs
                        )
                    }.also {
                        android.util.Log.d("EditorViewModel", "✅ Preprocessed audio ready")
                    }
                } else {
                    null
                }

                // Update settings once with all assets ready (including preprocessed audio)
                updatePendingSettings {
                    it.copy(
                        musicSongId = songId,
                        musicSongName = song?.name,
                        musicSongUrl = song?.mp3Url,
                        musicSongCoverUrl = song?.coverUrl,
                        customAudioUri = null,
                        processedAudioUri = preprocessedUri, // Already preprocessed - ready for preview
                        // Beat-sync integration
                        beatSyncData = beatSyncData,
                        hookStartTimeMs = song?.hookStartTimeMs ?: 0L,
                        totalDurationMs = totalDurationMs // Pre-calculated (prevents ANR)
                    )
                }
            } catch (e: CancellationException) {
                throw e // Don't catch cancellation
            } catch (e: Exception) {
                // ANY failure (beat-sync load, preprocessing, etc.) → show error dialog
                android.util.Log.e("EditorViewModel", "Music update failed: ${e.message}", e)
                _showBeatSyncErrorDialog.value = true
            }
        }
    }

    fun updateMusicTrack(songId: Long, songName: String, songUrl: String, songCoverUrl: String) {
        viewModelScope.launch {
            try {
                // Fetch song to get hookStartTimeMs
                val song = songRepository.getSongById(songId).getOrNull()

                // Load beat-sync data with retry logic
                val beatSyncData = loadBeatSyncWithRetry(songId)

                // Beat-sync load failure already shows error dialog and returns null
                if (beatSyncData == null) {
                    // Error dialog already shown by loadBeatSyncWithRetry
                    return@launch
                }

                android.util.Log.i("EditorViewModel", "Beat-sync loaded: BPM=${beatSyncData.bpm}, beats=${beatSyncData.beats.size}")

                // Pre-calculate beat-sync duration (avoid ANR in getter)
                val currentState = _uiState.value as? EditorUiState.Success
                val totalDurationMs = if (currentState != null) {
                    Project.calculateBeatSyncDuration(
                        beatData = beatSyncData,
                        assetCount = currentState.project.assets.size,
                        trimStartMs = 0L
                    ) ?: 0L
                } else {
                    0L
                }

                if (totalDurationMs <= 0) {
                    throw Exception("Invalid duration for beat-sync preprocessing")
                }

                // Preprocess audio BEFORE updating settings (wait for all assets ready)
                // This prevents double "Preparing video" by ensuring everything is ready before update
                val trimStartMs = song?.hookStartTimeMs ?: 0L
                val preprocessedUri = withContext(Dispatchers.IO) {
                    preprocessBeatSyncAudio(
                        songId = songId,
                        songUrl = songUrl,
                        beatSyncData = beatSyncData,
                        totalDurationMs = totalDurationMs,
                        trimStartMs = trimStartMs
                    )
                }.also {
                    android.util.Log.d("EditorViewModel", "✅ Preprocessed audio ready")
                }

                // Update settings once with all assets ready (including preprocessed audio)
                updatePendingSettings {
                    it.copy(
                        musicSongId = songId,
                        musicSongName = songName,
                        musicSongUrl = songUrl,
                        musicSongCoverUrl = songCoverUrl,
                        customAudioUri = null,
                        processedAudioUri = preprocessedUri, // Already preprocessed - ready for preview
                        // Beat-sync integration
                        beatSyncData = beatSyncData,
                        hookStartTimeMs = song?.hookStartTimeMs ?: 0L,
                        totalDurationMs = totalDurationMs // Pre-calculated (prevents ANR)
                    )
                }

                // Auto-processing disabled temporarily due to player freeze issues
                // User must manually trim music to match video duration
                // autoProcessAudioForVideoDuration(songUrl)
            } catch (e: CancellationException) {
                throw e // Don't catch cancellation
            } catch (e: Exception) {
                // ANY failure (beat-sync load, preprocessing, etc.) → show error dialog
                android.util.Log.e("EditorViewModel", "Music update failed: ${e.message}", e)
                _showBeatSyncErrorDialog.value = true
            }
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
                val result = songRepository.getSongById(songId)
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
    private suspend fun loadBeatSyncWithRetry(songId: Long): com.videomaker.aimusic.domain.model.BeatSyncData? {
        val maxRetries = 3
        repeat(maxRetries) { attempt ->
            try {
                android.util.Log.i("EditorViewModel", "Loading beat-sync data (attempt ${attempt + 1}/$maxRetries)")
                val result = beatSyncRepository.getBeatData(songId)

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
     * Preprocess audio with beat-sync fadeout.
     *
     * Creates a volume-agnostic preprocessed audio file with fadeout over last 6 beats.
     * Volume is applied separately at preview/export time, NOT baked in here.
     *
     * @param songId Song ID for cache key
     * @param songUrl Original song URL
     * @param beatSyncData Beat-sync data (for calculating fadeout duration)
     * @param totalDurationMs Total video duration
     * @return Preprocessed audio URI
     * @throws Exception if preprocessing fails
     */
    private suspend fun preprocessBeatSyncAudio(
        songId: Long,
        songUrl: String,
        beatSyncData: com.videomaker.aimusic.domain.model.BeatSyncData,
        totalDurationMs: Long,
        trimStartMs: Long = 0L
    ): Uri {
        android.util.Log.d("EditorViewModel", "Preprocessing audio with fadeout: songId=$songId, trimStart=${trimStartMs}ms, duration=${totalDurationMs}ms")

        val sourceUri = Uri.parse(songUrl)
        val beatMs = 60000.0 / beatSyncData.bpm
        val fadeoutDurationMs = (beatMs * com.videomaker.aimusic.media.composition.BeatSyncTimingCalculator.FADEOUT_BEATS).toLong()

        val preprocessedUri = audioPreprocessingService.preprocessAudioWithFadeout(
            sourceUri = sourceUri,
            songId = songId,
            trimStartMs = trimStartMs,
            totalDurationMs = totalDurationMs,
            fadeoutDurationMs = fadeoutDurationMs
        ) ?: throw Exception("Audio preprocessing failed: service returned null")

        android.util.Log.d("EditorViewModel", "✅ Audio preprocessing successful: $preprocessedUri")
        return preprocessedUri
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
                    // Update settings with processed audio
                    updatePendingSettings {
                        it.copy(processedAudioUri = Uri.fromFile(outputFile))
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
        // Store in pending settings - NO database write until user confirms
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
     * Apply pending assets to the project - this will trigger video rebuild
     * @param assets The reordered/updated assets list from ImagesBottomSheet
     */
    suspend fun applyPendingAssets(assets: List<Asset>) {
        val currentState = _uiState.value
        if (currentState is EditorUiState.Success) {
            android.util.Log.d("EditorViewModel", "applyPendingAssets: Applying ${assets.size} assets")

            val updatedProject = currentState.project.copy(
                assets = assets,
                updatedAt = System.currentTimeMillis()
            )
            _uiState.value = currentState.copy(project = updatedProject, pendingAssets = null)

            // Note: Assets are already saved to DB by asset picker
            // This just updates the project reference and triggers video rebuild
            android.util.Log.d("EditorViewModel", "applyPendingAssets: Assets applied, video will rebuild")
        } else {
            android.util.Log.d("EditorViewModel", "applyPendingAssets: Not in Success state")
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
     * Replace project assets with selected URIs from AssetPicker (editing mode)
     * Handles both existing assets (reordered) and new assets (added from gallery)
     * Triggers only ONE video rebuild
     */
    suspend fun replaceAssetsFromUris(selectedUris: List<String>) {
        val currentState = _uiState.value
        if (currentState !is EditorUiState.Success) return

        android.util.Log.d("EditorViewModel", "Replace assets: ${currentState.project.assets.size} → ${selectedUris.size} images")

        // Map existing assets by URI for quick lookup
        val existingAssetsByUri = currentState.project.assets.associateBy { it.uri.toString() }

        // Build final asset list maintaining selection order
        val finalAssets = selectedUris.mapIndexedNotNull { index, uriString ->
            // Try to use existing asset first (preserves ID and metadata)
            existingAssetsByUri[uriString]?.copy(orderIndex = index)
                ?: android.net.Uri.parse(uriString)?.let { uri ->
                    // New asset - create with new ID
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

        // Check if assets actually changed to avoid unnecessary rebuild
        val currentAssetUris = currentState.project.assets.map { it.uri.toString() }
        if (currentAssetUris == selectedUris) {
            android.util.Log.d("EditorViewModel", "replaceAssetsFromUris: Assets unchanged, skipping update")
            // Clear pending assets but don't update project
            _uiState.value = currentState.copy(pendingAssets = null)
            return
        }

        // Recalculate duration and reprocess audio if beat-sync music is present
        val beatSyncData = currentState.project.settings.beatSyncData
        val updatedSettings = if (beatSyncData != null && currentState.project.settings.musicSongId != null) {
            val newDuration = Project.calculateBeatSyncDuration(
                beatData = beatSyncData,
                assetCount = finalAssets.size,
                trimStartMs = currentState.project.settings.hookStartTimeMs
            ) ?: currentState.project.settings.totalDurationMs

            val durationChanged = newDuration != currentState.project.settings.totalDurationMs

            android.util.Log.d("EditorViewModel", "Duration recalculated: ${currentState.project.settings.totalDurationMs}ms → ${newDuration}ms (${finalAssets.size} images)")

            val songId = currentState.project.settings.musicSongId
            val songUrl = currentState.project.settings.musicSongUrl

            if (durationChanged && songId != null && songUrl != null) {
                // Show loading overlay during preprocessing
                _uiState.value = currentState.copy(isProcessingAudio = true)

                // Reprocess audio with new duration and fadeout
                android.util.Log.d("EditorViewModel", "🎵 replaceAssetsFromUris: Duration changed from ${currentState.project.settings.totalDurationMs}ms to ${newDuration}ms")
                android.util.Log.d("EditorViewModel", "🎵 Old processedAudioUri: ${currentState.project.settings.processedAudioUri}")
                android.util.Log.d("EditorViewModel", "🎵 Starting audio preprocessing with new duration...")

                val newProcessedUri = try {
                    preprocessBeatSyncAudio(
                        songId = songId,
                        songUrl = songUrl,
                        beatSyncData = beatSyncData,
                        totalDurationMs = newDuration,
                        trimStartMs = currentState.project.settings.hookStartTimeMs
                    )
                } catch (e: Exception) {
                    android.util.Log.e("EditorViewModel", "🎵 Audio preprocessing FAILED: ${e.message}", e)
                    null
                }

                android.util.Log.d("EditorViewModel", "🎵 New processedAudioUri: $newProcessedUri")
                android.util.Log.d("EditorViewModel", "🎵 Audio reprocessing complete!")

                currentState.project.settings.copy(
                    totalDurationMs = newDuration,
                    processedAudioUri = newProcessedUri  // Updated with new fadeout
                )
            } else {
                android.util.Log.d("EditorViewModel", "⚠️ Duration not changed or no music URL - durationChanged=$durationChanged, musicUrl=${currentState.project.settings.musicSongUrl != null}")
                currentState.project.settings.copy(totalDurationMs = newDuration)
            }
        } else {
            currentState.project.settings
        }

        // Apply directly to project - triggers ONE rebuild with updated duration
        val updatedProject = currentState.project.copy(
            assets = finalAssets,
            settings = updatedSettings,
            updatedAt = System.currentTimeMillis()
        )

        android.util.Log.d("EditorViewModel", "Applying changes: ${finalAssets.size} images, ${updatedSettings.totalDurationMs}ms - video will rebuild")

        // Update state immediately with new project (don't wait for database)
        _uiState.value = currentState.copy(
            project = updatedProject,
            pendingAssets = null,
            isProcessingAudio = false
        )

        // Save to database in background (saved projects only)
        if (!currentState.isUnsavedProject) {
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val existingAssetIds = currentState.project.assets.map { it.id }.toSet()
                    val newAssetIds = finalAssets.map { it.id }.toSet()

                    // 1. Delete assets that are no longer in the list
                    val assetsToRemove = currentState.project.assets.filter { it.id !in newAssetIds }
                    assetsToRemove.forEach { asset ->
                        projectRepository.removeAsset(updatedProject.id, asset.id)
                    }

                    // 2. Find new assets that need to be inserted
                    val newAssets = finalAssets.filter { it.id !in existingAssetIds }
                    if (newAssets.isNotEmpty()) {
                        projectRepository.addAssets(updatedProject.id, newAssets.map { it.uri })
                    }

                    // 3. Update/reorder all assets (including newly inserted ones)
                    projectRepository.reorderAssets(updatedProject.id, finalAssets)

                    // Save updated settings if duration changed
                    if (updatedSettings != currentState.project.settings) {
                        updateSettingsUseCase(updatedProject.id, updatedSettings)
                    }

                    android.util.Log.d("EditorViewModel", "Saved ${finalAssets.size} assets to database (removed: ${assetsToRemove.size}, added: ${newAssets.size})")
                } catch (e: Exception) {
                    android.util.Log.e("EditorViewModel", "❌ Failed to save - ${e.message}", e)
                }
            }
        }
    }

    // ============================================
    // MUSIC TRIMMER CONTROLS
    // ============================================

    /**
     * Opens the music trimmer bottom sheet.
     * Pauses main video player and creates isolated music-only preview player.
     * Thread-safe with mutex protection.
     */
    fun openMusicTrimmer() {
        val currentState = _uiState.value
        if (currentState !is EditorUiState.Success) return

        val settings = currentState.displaySettings
        val songName = settings.musicSongName
        val songUrl = settings.musicSongUrl

        // Can only trim if there's a music track selected
        if (songName == null || songUrl == null) return

        viewModelScope.launch {
            trimStateMutex.withLock {
                try {
                    // Pause main video player if playing
                    val wasPlaying = currentState.isPlaying
                    if (wasPlaying) {
                        setPlaybackState(false)
                    }

                    // TODO: Get actual song duration from music player/metadata
                    // For now using placeholder - will be set when music player loads
                    val songDurationMs = 180_000L // 3 minutes placeholder

                    // Open trimmer with a window aligned to current video duration.
                    // This keeps music editing tied to the current timeline length.
                    val currentVideoDurationMs = currentState.displayProject.totalDurationMs
                        .coerceAtLeast(MusicTrimmerState.Open.MIN_TRIM_DURATION_MS)
                    val preferredStartMs = 0L  // Beat-sync mode: always start from beginning
                    val preferredEndMs = preferredStartMs + currentVideoDurationMs
                    val (trimStartMs, trimEndMs) = normalizeTrimWindow(
                        preferredStartMs = preferredStartMs,
                        preferredEndMs = preferredEndMs,
                        songDurationMs = songDurationMs
                    )

                    _musicTrimmerState.value = MusicTrimmerState.Open(
                        songName = songName,
                        songDurationMs = songDurationMs,
                        trimStartMs = trimStartMs,
                        trimEndMs = trimEndMs,
                        currentMusicPositionMs = trimStartMs, // Start at trim start
                        isMusicPlaying = false,
                        wasMainPlayerPlaying = wasPlaying
                    )
                } catch (e: Exception) {
                    // Log error but don't crash
                    android.util.Log.e("EditorViewModel", "Failed to open music trimmer", e)
                }
            }
        }
    }

    /**
     * Updates music trim preview positions during drag.
     * Real-time update - no database write.
     * Thread-safe with mutex protection.
     */
    fun updateMusicTrimPreview(startMs: Long, endMs: Long) {
        val currentTrimState = _musicTrimmerState.value
        if (currentTrimState !is MusicTrimmerState.Open) return

        viewModelScope.launch {
            trimStateMutex.withLock {
                try {
                    // Validate minimum duration (5 seconds)
                    val duration = endMs - startMs
                    if (duration < MusicTrimmerState.Open.MIN_TRIM_DURATION_MS) {
                        return@withLock
                    }

                    // Update trimmer state (NOT pending settings yet - preview only)
                    _musicTrimmerState.value = currentTrimState.copy(
                        trimStartMs = startMs,
                        trimEndMs = endMs
                    )
                } catch (e: Exception) {
                    android.util.Log.e("EditorViewModel", "Failed to update trim preview", e)
                }
            }
        }
    }

    /**
     * Updates current music playback position (for playhead indicator).
     * High-frequency update - no mutex needed (single atomic write).
     */
    fun updateMusicTrimPosition(currentMs: Long) {
        val currentTrimState = _musicTrimmerState.value
        if (currentTrimState is MusicTrimmerState.Open) {
            _musicTrimmerState.value = currentTrimState.copy(
                currentMusicPositionMs = currentMs
            )
        }
    }

    /**
     * Toggles music playback in trimmer.
     * Single atomic write - no mutex needed.
     */
    fun toggleMusicTrimPlayback() {
        val currentTrimState = _musicTrimmerState.value
        if (currentTrimState is MusicTrimmerState.Open) {
            _musicTrimmerState.value = currentTrimState.copy(
                isMusicPlaying = !currentTrimState.isMusicPlaying
            )
        }
    }

    /**
     * Sets music playback state in trimmer.
     * Single atomic write - no mutex needed.
     */
    fun setMusicTrimPlaybackState(isPlaying: Boolean) {
        val currentTrimState = _musicTrimmerState.value
        if (currentTrimState is MusicTrimmerState.Open) {
            _musicTrimmerState.value = currentTrimState.copy(
                isMusicPlaying = isPlaying
            )
        }
    }

    /**
     * Updates song duration when music player finishes loading.
     * Called by UI when actual duration is known.
     */
    fun updateMusicTrimDuration(durationMs: Long) {
        val currentTrimState = _musicTrimmerState.value
        if (currentTrimState is MusicTrimmerState.Open) {
            viewModelScope.launch {
                trimStateMutex.withLock {
                    val (trimStartMs, trimEndMs) = normalizeTrimWindow(
                        preferredStartMs = currentTrimState.trimStartMs,
                        preferredEndMs = currentTrimState.trimEndMs,
                        songDurationMs = durationMs
                    )

                    _musicTrimmerState.value = currentTrimState.copy(
                        songDurationMs = durationMs,
                        trimStartMs = trimStartMs,
                        trimEndMs = trimEndMs
                    )
                }
            }
        }
    }

    /**
     * Applies music trim to project settings and saves to database.
     * Thread-safe with mutex protection.
     * Closes trimmer after applying.
     */
    fun applyMusicTrim() {
        val currentTrimState = _musicTrimmerState.value
        if (currentTrimState !is MusicTrimmerState.Open) {
            android.util.Log.w("EditorViewModel", "applyMusicTrim: Not in Open state")
            return
        }
        if (!currentTrimState.isValid) {
            android.util.Log.w("EditorViewModel", "applyMusicTrim: Invalid trim state")
            return // Don't save invalid trim
        }

        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            trimStateMutex.withLock {
                try {
                    val startMs = currentTrimState.trimStartMs
                    val endMs = currentTrimState.trimEndMs
                    val currentState = _uiState.value

                    if (currentState !is EditorUiState.Success) {
                        android.util.Log.w("EditorViewModel", "applyMusicTrim: Invalid UI state")
                        return@withLock
                    }

                    // Get audio URI (handles both custom audio and music songs)
                    val audioUri = getAudioUriSync(currentState.displaySettings)
                    if (audioUri == null) {
                        android.util.Log.w("EditorViewModel", "applyMusicTrim: No audio selected")
                        return@withLock
                    }

                    android.util.Log.d("EditorViewModel", "applyMusicTrim: Saving trim positions start=$startMs, end=$endMs (instant)")

                    // Beat-sync mode: Music trimming is handled via hookStartTimeMs in beat-sync data
                    // No need to update totalDurationMs here - it's calculated from beats
                    updatePendingSettings {
                        it.copy(
                            processedAudioUri = null // Clear preprocessed audio
                        )
                    }

                    // Auto-apply settings to save to database
                    applySettings()

                    android.util.Log.d("EditorViewModel", "applyMusicTrim: Trim positions saved (Media3 will handle looping)")

                    // Close trimmer and restore main player state
                    closeMusicTrimmerInternal(currentTrimState.wasMainPlayerPlaying)
                } catch (e: Exception) {
                    android.util.Log.e("EditorViewModel", "Failed to apply music trim", e)
                }
            }
        }
    }

    /**
     * Closes music trimmer bottom sheet.
     * Discards changes if applyChanges = false.
     * Restores main player state.
     * Thread-safe with mutex protection.
     */
    fun closeMusicTrimmer(applyChanges: Boolean = false) {
        val currentTrimState = _musicTrimmerState.value
        if (currentTrimState !is MusicTrimmerState.Open) return

        viewModelScope.launch {
            trimStateMutex.withLock {
                try {
                    if (applyChanges && currentTrimState.isValid) {
                        // Apply trim positions before closing
                        val startMs = currentTrimState.trimStartMs
                        val endMs = currentTrimState.trimEndMs

                        updatePendingSettings {
                            it.copy(
                            )
                        }

                        applySettings()
                    }

                    // Close trimmer and restore main player state
                    closeMusicTrimmerInternal(currentTrimState.wasMainPlayerPlaying)
                } catch (e: Exception) {
                    android.util.Log.e("EditorViewModel", "Failed to close music trimmer", e)
                }
            }
        }
    }

    /**
     * Internal helper to close trimmer and restore main player.
     * Must be called within mutex lock.
     */
    private fun closeMusicTrimmerInternal(restorePlaybackState: Boolean) {
        android.util.Log.d("EditorViewModel", "closeMusicTrimmerInternal: restorePlaybackState=$restorePlaybackState")

        // Close trimmer state
        _musicTrimmerState.value = MusicTrimmerState.Closed

        // Restore main player playback state if it was playing before
        if (restorePlaybackState) {
            android.util.Log.d("EditorViewModel", "closeMusicTrimmerInternal: Restoring playback (play)")
            setPlaybackState(true)
        } else {
            android.util.Log.d("EditorViewModel", "closeMusicTrimmerInternal: Not restoring playback (was paused)")
        }
    }

    /**
     * Clears music trim settings (reset to full song).
     * Updates pending settings and closes trimmer.
     */
    fun clearMusicTrim() {
        viewModelScope.launch {
            trimStateMutex.withLock {
                try {
                    // Reset trim positions to default (full song: 0 → null)
                    // null means "use entire song, no trimming"
                    updatePendingSettings {
                        it.copy(
                            processedAudioUri = null // Clear processed audio
                        )
                    }

                    applySettings()

                    // Close trimmer if open
                    val currentTrimState = _musicTrimmerState.value
                    if (currentTrimState is MusicTrimmerState.Open) {
                        closeMusicTrimmerInternal(currentTrimState.wasMainPlayerPlaying)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("EditorViewModel", "Failed to clear music trim", e)
                }
            }
        }
    }

    // ============================================
    // PLAYBACK CONTROLS
    // ============================================

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
     * Check if selected quality requires ad unlock.
     * 720p and 1080p are locked until user watches rewarded ad.
     */
    fun isQualityLocked(quality: VideoQuality): Boolean {
        return (quality == VideoQuality.HD_720 || quality == VideoQuality.FHD_1080) && !_isQualityUnlocked.value
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
        _showBeatSyncErrorDialog.value = false
        // Navigate back to home (no ad on error case)
        viewModelScope.launch {
            _navigationEvent.send(EditorNavigationEvent.RequestBackWithAd(shouldShowAd = false))
        }
    }

    override fun onCleared() {
        super.onCleared()

        // Cancel project observer
        projectObserverJob?.cancel()
        projectObserverJob = null

        // Close music trimmer and release resources (prevents memory leak)
        val currentTrimState = _musicTrimmerState.value
        if (currentTrimState is MusicTrimmerState.Open) {
            _musicTrimmerState.value = MusicTrimmerState.Closed
        }
    }

    private fun normalizeTrimWindow(
        preferredStartMs: Long,
        preferredEndMs: Long,
        songDurationMs: Long
    ): Pair<Long, Long> {
        val minDurationMs = MusicTrimmerState.Open.MIN_TRIM_DURATION_MS
        val safeSongDurationMs = songDurationMs.coerceAtLeast(minDurationMs)
        val maxStartMs = (safeSongDurationMs - minDurationMs).coerceAtLeast(0L)

        val startMs = preferredStartMs.coerceIn(0L, maxStartMs)
        val minEndMs = startMs + minDurationMs
        val endMs = preferredEndMs
            .coerceAtLeast(minEndMs)
            .coerceAtMost(safeSongDurationMs)

        return if (endMs - startMs >= minDurationMs) {
            startMs to endMs
        } else {
            val fallbackStartMs = (safeSongDurationMs - minDurationMs).coerceAtLeast(0L)
            fallbackStartMs to safeSongDurationMs
        }
    }
}

private fun AspectRatio.toAnalyticsRatioSize(): String = when (this) {
    AspectRatio.RATIO_16_9 -> "16:9"
    AspectRatio.RATIO_9_16 -> "9:16"
    AspectRatio.RATIO_4_5 -> "4:5"
    AspectRatio.RATIO_1_1 -> "1:1"
}
