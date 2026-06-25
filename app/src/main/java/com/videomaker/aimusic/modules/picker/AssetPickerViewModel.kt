package com.videomaker.aimusic.modules.picker

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.alcheclub.lib.acccore.ads.loader.AdsLoaderService
import com.videomaker.aimusic.core.ads.InterstitialAdHelperExt
import com.videomaker.aimusic.core.analytics.Analytics
import com.videomaker.aimusic.core.constants.AdPlacement
import com.videomaker.aimusic.core.data.local.PreferencesManager
import com.videomaker.aimusic.core.notification.NotificationScheduler
import com.videomaker.aimusic.core.notification.NotificationType
import com.videomaker.aimusic.domain.model.AspectRatio
import com.videomaker.aimusic.domain.model.BeatSyncData
import com.videomaker.aimusic.domain.model.EditorInitialData
import com.videomaker.aimusic.domain.model.Project
import com.videomaker.aimusic.domain.model.ProjectSettings
import com.videomaker.aimusic.domain.repository.BeatSyncRepository
import com.videomaker.aimusic.domain.repository.SongRepository
import com.videomaker.aimusic.domain.repository.TemplateRepository
import com.videomaker.aimusic.domain.usecase.AddAssetsUseCase
import com.videomaker.aimusic.domain.usecase.CreateProjectUseCase
import com.videomaker.aimusic.modules.picker.AssetPickerViewModel.Companion.MAX_SELECTION
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Asset data class representing a media item from the gallery
 */
data class GalleryAsset(
    val id: Long,
    val uri: Uri,
    val displayName: String,
    val dateAdded: Long,
    val width: Int,
    val height: Int,
    val bucketId: Long,
    val bucketName: String
)

/**
 * Album/Bucket filter
 */
data class AlbumFilter(
    val id: String,
    val displayName: String,
    val bucketId: Long? = null, // null means "All"
    val count: Int = 0
)

/**
 * Predefined album filter types
 */
object AlbumFilterType {
    const val ALL = "all"
    const val CAMERA = "camera"
    const val SCREENSHOTS = "screenshots"
}

/**
 * UI State for Asset Picker
 *
 * - Initial/Loading: transitional states
 * - WithAssets.AllPermission: full photo access granted — all gallery images loaded
 * - WithAssets.LimitPermission: limited/partial access (Android 14+) — only user-selected images loaded
 * - DeniedPermission: permission denied — show "Go to Settings" prompt
 */
sealed class AssetPickerUiState {
    data object Initial : AssetPickerUiState()
    data object Loading : AssetPickerUiState()
    data object DeniedPermission : AssetPickerUiState()

    sealed class WithAssets : AssetPickerUiState() {
        abstract val assets: List<GalleryAsset>
        abstract val filteredAssets: List<GalleryAsset>
        abstract val selectedAssets: List<GalleryAsset>
        abstract val albums: List<AlbumFilter>
        abstract val selectedAlbumId: String

        abstract fun copyAssets(
            assets: List<GalleryAsset> = this.assets,
            filteredAssets: List<GalleryAsset> = this.filteredAssets,
            selectedAssets: List<GalleryAsset> = this.selectedAssets,
            albums: List<AlbumFilter> = this.albums,
            selectedAlbumId: String = this.selectedAlbumId
        ): WithAssets

        /** Full permission — entire gallery is accessible */
        data class AllPermission(
            override val assets: List<GalleryAsset>,
            override val filteredAssets: List<GalleryAsset>,
            override val selectedAssets: List<GalleryAsset> = emptyList(),
            override val albums: List<AlbumFilter> = emptyList(),
            override val selectedAlbumId: String = AlbumFilterType.ALL
        ) : WithAssets() {
            override fun copyAssets(
                assets: List<GalleryAsset>,
                filteredAssets: List<GalleryAsset>,
                selectedAssets: List<GalleryAsset>,
                albums: List<AlbumFilter>,
                selectedAlbumId: String
            ): WithAssets = copy(
                assets = assets,
                filteredAssets = filteredAssets,
                selectedAssets = selectedAssets,
                albums = albums,
                selectedAlbumId = selectedAlbumId
            )
        }

        /** Limited permission (Android 14+ READ_MEDIA_VISUAL_USER_SELECTED) — only chosen images */
        data class LimitPermission(
            override val assets: List<GalleryAsset>,
            override val filteredAssets: List<GalleryAsset>,
            override val selectedAssets: List<GalleryAsset> = emptyList(),
            override val albums: List<AlbumFilter> = emptyList(),
            override val selectedAlbumId: String = AlbumFilterType.ALL
        ) : WithAssets() {
            override fun copyAssets(
                assets: List<GalleryAsset>,
                filteredAssets: List<GalleryAsset>,
                selectedAssets: List<GalleryAsset>,
                albums: List<AlbumFilter>,
                selectedAlbumId: String
            ): WithAssets = copy(
                assets = assets,
                filteredAssets = filteredAssets,
                selectedAssets = selectedAssets,
                albums = albums,
                selectedAlbumId = selectedAlbumId
            )
        }
    }
}

/**
 * Navigation events for Asset Picker
 */
sealed class AssetPickerNavigationEvent {
    /**
     * Legacy back navigation (no ad check)
     * Use RequestExitWithAd instead for ad support
     */
    data object NavigateBack : AssetPickerNavigationEvent()

    /**
     * Request exit with optional ad
     * @param shouldShowAd true if ad is ready and should be shown
     */
    data class RequestExitWithAd(val shouldShowAd: Boolean) : AssetPickerNavigationEvent()

    data class NavigateToEditor(val projectId: String) : AssetPickerNavigationEvent()
    /** NEW: Navigate to Editor with initial data (from template flow) */
    data class NavigateToEditorWithData(val initialData: EditorInitialData) : AssetPickerNavigationEvent()
    /** Assets added to existing project - just go back */
    data object AssetsAdded : AssetPickerNavigationEvent()
    /** Editing mode: return selected URIs without saving to DB */
    data class SelectionConfirmed(val selectedUris: List<String>, val shouldShowAd: Boolean = false) : AssetPickerNavigationEvent()
    /** Template mode / song-to-video mode: confirm selection with URIs directly */
    data class NavigateToTemplatePreviewer(
        val templateId: String,
        val imageUris: List<String>,
        val overrideSongId: Long = -1L
    ) : AssetPickerNavigationEvent()
}

/**
 * AssetPickerViewModel - Manages gallery image loading, filtering, and selection
 *
 * Performance optimizations:
 * - Lazy loading via StateFlow (UI only renders visible items)
 * - Single MediaStore query with bucket grouping
 * - Query limit to prevent loading too many images at once
 * - Filtering done in-memory after initial load
 * - Image thumbnails handled by Coil with caching
 */
class AssetPickerViewModel(
    context: Context,  // Convert to Application context to prevent memory leak
    private val createProjectUseCase: CreateProjectUseCase,
    private val addAssetsUseCase: AddAssetsUseCase,
    private val templateRepository: TemplateRepository,
    private val songRepository: SongRepository,
    private val adsLoaderService: AdsLoaderService,
    private val notificationScheduler: NotificationScheduler,
    private val preferencesManager: PreferencesManager,
    private val beatSyncRepository: BeatSyncRepository,
    private val projectId: String? = null, // null = create new project, non-null = add to existing
    private val templateId: String? = null,  // non-null = template mode, bypasses project creation
    private val overrideSongId: Long = -1L,   // >= 0 = song-to-video mode, overrides template song
    private val aspectRatio: AspectRatio? = null,  // User's selected aspect ratio from template previewer
    private val resumeDraftId: String? = null,
    private val selectedAssetUris: List<String> = emptyList(),  // URIs to auto-select (for editing mode)
    private val isEditingMode: Boolean = false,  // true = return URIs without saving to DB
    // Duration-estimate only (does NOT affect selection/confirm logic). Used by the "add images to
    // existing project" flow so the picker's estimate matches the editor's beat-synced duration.
    private val durationSongId: Long = -1L,        // >= 0 = song to load beat-sync data for the estimate
    private val durationTrimStartMs: Long = 0L     // matches the editor's trimStart for that flow
) : ViewModel() {

    // Use applicationContext to prevent Activity memory leak
    private val appContext: Context = context.applicationContext

    companion object {
        // Cap metadata load — enough for any real user gallery
        private const val MAX_IMAGES = 3000

        // Maximum number of images that can be selected at once
        const val MAX_SELECTION = 20
    }

    /** True if adding to existing project, false if creating new */
    val isAddMode: Boolean get() = projectId != null

    /** True if launched from template flow */
    val isTemplateMode: Boolean get() = templateId != null

    /** True if launched from the song player — images will go straight to TemplatePreviewer
     *  with the selected song overriding each template's own track. */
    val isSongToVideoMode: Boolean get() = overrideSongId >= 0L

    /** Minimum number of images required before confirming */
    val minSelection: Int get() = 2

    // ============================================
    // STATE
    // ============================================

    private val _uiState = MutableStateFlow<AssetPickerUiState>(AssetPickerUiState.Initial)
    val uiState: StateFlow<AssetPickerUiState> = _uiState.asStateFlow()

    private var currentPermissionMode: PermissionMode? = null
    private var isRefreshing = false
    private val _gridScrollState = MutableStateFlow(AssetPickerGridScrollState())
    internal val gridScrollState: StateFlow<AssetPickerGridScrollState> = _gridScrollState.asStateFlow()

    private val _permissionRequestEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    internal val permissionRequestEvent: SharedFlow<Unit> = _permissionRequestEvent.asSharedFlow()

    // ============================================
    // NAVIGATION EVENTS (StateFlow-based - Google recommended)
    // ============================================
    // Using Channel for one-time events (Google pattern) - prevents replay on config change

    private val _navigationEvent = Channel<AssetPickerNavigationEvent>()
    val navigationEvent = _navigationEvent.receiveAsFlow()

    // USER MESSAGE EVENTS (for snackbars/toasts)
    // ============================================

    private val _isConfirming = MutableStateFlow(false)
    val isConfirming: StateFlow<Boolean> = _isConfirming.asStateFlow()

    private val _messageEvent = MutableStateFlow<String?>(null)
    val messageEvent: StateFlow<String?> = _messageEvent.asStateFlow()

    // Camera captures are app-private files, so keep them in-memory and merge on reloads.
    private val transientCameraAssets = mutableListOf<GalleryAsset>()

    // ============================================
    // ESTIMATED DURATION (beat-sync aware)
    // ============================================
    // Mirrors the editor: when a song is known we load its beat-sync data and compute the exact
    // duration the editor will render (Project.calculateBeatSyncDuration). Until/unless beat data
    // is available, we fall back to the flat 2.8s/photo estimate.

    /**
     * Picker duration shown in the bottom bar.
     * @param formatted MM:SS string (matches the editor's Project.formattedDuration)
     * @param additionalForIdeal extra photos needed to reach the recommended ~15s length
     */
    data class PickerDurationInfo(
        val formatted: String = formatPickerDurationMs(0L),
        val additionalForIdeal: Int = 0
    )

    private val _durationInfo = MutableStateFlow(PickerDurationInfo())
    val durationInfo: StateFlow<PickerDurationInfo> = _durationInfo.asStateFlow()

    // Pre-computed duration per selected-photo count (index = count). Starts as the flat estimate;
    // rebuilt with real beat-sync timing once beat data loads.
    @Volatile
    private var durationByCount: LongArray = LongArray(MAX_SELECTION + 1) { estimateFlatDurationMs(it) }
    private var beatSyncData: BeatSyncData? = null

    init {
        AssetPickerSessionCache.snapshot?.let { cached ->
            currentPermissionMode = cached.permissionMode
            _gridScrollState.value = cached.gridScrollState
            when {
                cached.permissionMode == PermissionMode.DENIED -> {
                    _uiState.value = AssetPickerUiState.DeniedPermission
                }
                cached.assets.isNotEmpty() -> {
                    // Prioritize selectedAssetUris parameter (editing mode) over cached selection
                    val initialSelection = if (selectedAssetUris.isNotEmpty()) {
                        android.util.Log.d("AssetPickerViewModel", "Init with ${selectedAssetUris.size} pre-selected URIs from parameter")
                        selectedAssetUris
                    } else {
                        android.util.Log.d("AssetPickerViewModel", "Init with ${cached.selectedUris.size} cached selected URIs")
                        cached.selectedUris
                    }
                    _uiState.value = createWithAssetsState(
                        permissionMode = cached.permissionMode,
                        assets = cached.assets,
                        selectedUris = initialSelection,
                        preferredAlbumId = cached.selectedAlbumId
                    )
                }
            }
        }

        resumeDraftId?.takeIf { it.isNotBlank() }?.let { resumed ->
            notificationScheduler.cancelDraftReminders(resumed)
        }

        // NOTE: With the new flow (Template Browse → Select Template → Pick Images),
        // it's valid to have both overrideSongId and templateId:
        // - User picks a song → browses templates with that song → selects a template → picks images
        // The confirmSelection() method handles priority: overrideSongId takes priority over template's song

        // Preload exit ad when screen launches (no timeout, non-blocking)
        viewModelScope.launch {
            android.util.Log.d("AssetPickerVM", "🎬 Preloading exit ad...")
            runCatching {
                InterstitialAdHelperExt.preloadInterstitial(
                    adsLoaderService = adsLoaderService,
                    placement = AdPlacement.INTERSTITIAL_ASSET_PICKER_EXIT,
                    loadTimeoutMillis = null,  // No timeout - loads in background
                    showLoadingOverlay = false
                )
            }.onSuccess { success ->
                if (success) {
                    android.util.Log.d("AssetPickerVM", "✅ Exit ad preload SUCCESS")
                } else {
                    android.util.Log.w("AssetPickerVM", "⚠️ Exit ad preload FAILED")
                }
            }.onFailure { e ->
                android.util.Log.e("AssetPickerVM", "❌ Exit ad preload exception: ${e.message}", e)
            }
        }

        // Preload "Done" interstitial for edit mode only
        if (isEditingMode) {
            viewModelScope.launch {
                runCatching {
                    InterstitialAdHelperExt.preloadInterstitial(
                        adsLoaderService = adsLoaderService,
                        placement = AdPlacement.INTERSTITIAL_PICKER_DONE,
                        loadTimeoutMillis = null,
                        showLoadingOverlay = false
                    )
                }
            }
        }

        // Recompute the displayed duration whenever the selection count changes (cheap array lookup).
        viewModelScope.launch {
            _uiState.collect { state ->
                publishDurationInfo(currentSelectedCount(state))
            }
        }

        // Resolve the song (template/song mode) and load its beat-sync data so the picker's
        // estimate matches the editor's real, beat-synced duration.
        loadBeatSyncForDuration()
    }

    private fun currentSelectedCount(state: AssetPickerUiState): Int =
        (state as? AssetPickerUiState.WithAssets)?.selectedAssets?.size ?: 0

    /** Flat 2.8s/photo fallback when no beat-sync data is available. */
    private fun estimateFlatDurationMs(count: Int): Long =
        if (count <= 0) 0L else (count * PICKER_DURATION_PER_PHOTO_SEC * 1000).toLong()

    private fun publishDurationInfo(count: Int) {
        val safeCount = count.coerceIn(0, MAX_SELECTION)
        val durationMs = durationByCount[safeCount]
        _durationInfo.value = PickerDurationInfo(
            formatted = formatPickerDurationMs(durationMs),
            additionalForIdeal = additionalPhotosForIdeal(safeCount)
        )
    }

    // Fires media_*_state events whenever a selection changes (add/remove/camera).
    // Matches the promote message rendered by PickerSelectionBar:
    //   - count >= MAX_SELECTION              → media_limitphoto_state
    //   - count >= 2 AND duration >= 15s      → media_morephoto_state
    //   - count >= 2 (still under 15s)        → media_nphoto_state
    //   - count < 2                           → no promote state (min-selection message)
    private fun trackPromoteState() {
        val count = currentSelectedCount(_uiState.value).coerceIn(0, MAX_SELECTION)
        when {
            count >= MAX_SELECTION -> Analytics.trackMediaLimitPhotoState()
            count >= 2 && durationByCount[count] >= PICKER_RECOMMENDED_DURATION_MS ->
                Analytics.trackMediaMorePhotoState()
            count >= 2 -> Analytics.trackMediaNPhotoState()
            else -> {}
        }
    }

    /**
     * Extra photos to suggest until the estimated duration first reaches the recommended ~15s.
     * Picks the smallest total count whose duration ≥ recommended length, so the user is nudged
     * past the threshold even when the last step slightly overshoots. durationByCount is
     * monotonically increasing.
     *
     * e.g. flat 2.8s/photo: 5 photos = 14s (< 15) → still suggest reaching 6 (≈16.8s).
     * Beat-sync example: 4 = 13.5s, 5 = 17s → suggest reaching 5 (first to cross 15s).
     */
    private fun additionalPhotosForIdeal(count: Int): Int {
        if (count >= MAX_SELECTION) return 0
        if (durationByCount[count] >= PICKER_RECOMMENDED_DURATION_MS) return 0
        for (n in (count + 1)..MAX_SELECTION) {
            if (durationByCount[n] >= PICKER_RECOMMENDED_DURATION_MS) {
                return n - count
            }
        }
        return MAX_SELECTION - count
    }

    private fun loadBeatSyncForDuration() {
        viewModelScope.launch {
            // Resolve the song whose beat-sync timing drives the estimate. The editor uses
            // trimStartMs = 0 for new-project flows (song-to-video / template) and the project's
            // hookStartTimeMs when re-rendering an existing project (add-images flow); the caller
            // passes the matching durationTrimStartMs.
            val songId = when {
                overrideSongId >= 0L -> overrideSongId
                !templateId.isNullOrBlank() -> resolveTemplateSongId(templateId)
                durationSongId >= 0L -> durationSongId
                else -> null
            } ?: return@launch  // No song context — keep the flat estimate.

            val data = beatSyncRepository.getBeatData(songId).getOrNull()
                ?: return@launch  // Beat data unavailable — graceful fallback to the flat estimate.

            beatSyncData = data
            durationByCount = buildDurationTable(data, durationTrimStartMs)
            publishDurationInfo(currentSelectedCount(_uiState.value))
        }
    }

    private suspend fun resolveTemplateSongId(templateId: String): Long? =
        templateRepository.getTemplates(limit = 100, offset = 0).getOrNull()
            ?.find { it.id == templateId }
            ?.songId
            ?.takeIf { it > 0L }

    private suspend fun buildDurationTable(
        data: BeatSyncData,
        trimStartMs: Long
    ): LongArray = withContext(Dispatchers.Default) {
        LongArray(MAX_SELECTION + 1) { count ->
            if (count <= 0) {
                0L
            } else {
                Project.calculateBeatSyncDuration(
                    beatData = data,
                    assetCount = count,
                    trimStartMs = trimStartMs
                ) ?: estimateFlatDurationMs(count)
            }
        }
    }

    // ============================================
    // PUBLIC API
    // ============================================

    /**
     * Called when full or limited permission is granted.
     * @param isLimited true when only READ_MEDIA_VISUAL_USER_SELECTED is granted (Android 14+)
     *
     * When full access is granted the saved picked_assets are no longer needed — MediaStore
     * now covers the entire gallery — so the table is cleared before loading.
     */
    internal fun onPermissionSnapshot(
        snapshot: PermissionSnapshot,
        source: PermissionUpdateSource
    ) {
        val mode = resolvePermissionMode(snapshot)
        applyPermissionMode(mode, source = source, forceReload = false)
    }

    fun onPermissionGranted(
        isLimited: Boolean = false,
        forceReload: Boolean = false
    ) {
        val mode = if (isLimited) PermissionMode.LIMITED else PermissionMode.FULL
        applyPermissionMode(
            newMode = mode,
            source = PermissionUpdateSource.REQUEST_RESULT,
            forceReload = forceReload
        )
    }

    /**
     * Called when permission is denied
     */
    fun onPermissionDenied() {
        applyPermissionMode(
            newMode = PermissionMode.DENIED,
            source = PermissionUpdateSource.REQUEST_RESULT,
            forceReload = false
        )
    }

    internal fun onGridScrollChanged(index: Int, offset: Int) {
        val newState = AssetPickerGridScrollState(index, offset)
        if (_gridScrollState.value == newState) return
        _gridScrollState.value = newState
        AssetPickerSessionCache.snapshot = AssetPickerSessionCache.snapshot?.copy(
            gridScrollState = newState
        )
    }

    /**
     * Keep gallery cache for fast reopen, but clear selected assets when picker closes.
     */
    fun onPickerClosed() {
        _isConfirming.value = false
        val currentState = _uiState.value as? AssetPickerUiState.WithAssets
        val selectedCount = currentState?.selectedAssets?.size ?: 0
        val draftId = buildDraftId()
        if (!draftId.isNullOrBlank()) {
            if (selectedCount <= 0) {
                val now = System.currentTimeMillis()
                val sessionId = preferencesManager.getAppSessionId()
                preferencesManager.upsertDraftReminderState(
                    draftId = draftId,
                    templateId = templateId,
                    songId = overrideSongId.takeIf { it >= 0L },
                    exitedAtMs = now,
                    exitSessionId = sessionId,
                    selectedPhotoCount = selectedCount
                )
                notificationScheduler.scheduleAbandonedSelectPhotos(
                    draftId = draftId,
                    sessionId = sessionId,
                    exitedAtMs = now
                )
                notificationScheduler.scheduleDraftCompletionNudge(
                    draftId = draftId,
                    exitedAtMs = now
                )
                Analytics.trackNotificationScheduled(
                    type = NotificationType.ABANDONED_SELECT_PHOTOS.analyticsValue,
                    itemId = draftId,
                    itemType = "draft",
                    sourceTrigger = "asset_picker_exit",
                    deepLinkDestination = "select_photos",
                    delayMinutes = 2,
                    copyVariant = "beat_hanging_v1",
                    imageType = "template_key_art",
                    sessionType = "same_and_cold"
                )
                Analytics.trackNotificationScheduled(
                    type = NotificationType.DRAFT_COMPLETION_NUDGE.analyticsValue,
                    itemId = draftId,
                    itemType = "draft",
                    sourceTrigger = "asset_picker_exit",
                    deepLinkDestination = "select_photos",
                    delayMinutes = 15,
                    copyVariant = "finish_what_started_v1",
                    imageType = "template_key_art",
                    sessionType = "exit_intent"
                )
            } else {
                notificationScheduler.cancelDraftReminders(draftId)
                preferencesManager.clearDraftReminderState(draftId)
            }
        }
        if (currentState != null) {
            val allAlbumId = AlbumFilterType.ALL
            val filteredAssets = filterAssetsByAlbum(
                assets = currentState.assets,
                albumId = allAlbumId,
                albums = currentState.albums
            )
            val updatedState = currentState.copyAssets(
                filteredAssets = filteredAssets,
                selectedAssets = emptyList(),
                selectedAlbumId = allAlbumId
            )
            _uiState.value = updatedState
            persistSessionSnapshot(updatedState, modeForState(updatedState))
            return
        }
        AssetPickerSessionCache.snapshot = AssetPickerSessionCache.snapshot?.copy(
            selectedUris = emptyList(),
            selectedAlbumId = AlbumFilterType.ALL
        )
    }

    private fun applyPermissionMode(
        newMode: PermissionMode,
        source: PermissionUpdateSource,
        forceReload: Boolean
    ) {
        val previousMode = currentPermissionMode
        currentPermissionMode = newMode

        if (newMode == PermissionMode.DENIED) {
            _uiState.value = AssetPickerUiState.DeniedPermission
            persistDeniedSnapshot()
            val shouldRequest = source != PermissionUpdateSource.REQUEST_RESULT &&
                shouldRequestPermissionDialog(previousMode, newMode)
            if (shouldRequest) {
                _permissionRequestEvent.tryEmit(Unit)
            }
            return
        }

        val currentWithAssets = _uiState.value as? AssetPickerUiState.WithAssets
        if (!forceReload && currentWithAssets != null && modeForState(currentWithAssets) == newMode) {
            persistSessionSnapshot(currentWithAssets, newMode)
            // todo need to test again
            if (source == PermissionUpdateSource.RESUME) {
                val preferredSelectedUris = currentWithAssets.selectedAssets.map { it.uri.toString() }
                loadImages(
                    permissionMode = newMode,
                    preferredSelectedUris = preferredSelectedUris,
                    preferredAlbumId = currentWithAssets.selectedAlbumId,
                    forceShowLoading = false
                )
            }
            return
        }

        val cached = AssetPickerSessionCache.snapshot
        if (!forceReload &&
            currentWithAssets == null &&
            cached != null &&
            cached.permissionMode == newMode &&
            cached.assets.isNotEmpty()
        ) {
            _gridScrollState.value = cached.gridScrollState
            // Prioritize selectedAssetUris parameter (editing mode) over cached selection.
            // When the user deletes images in ImagesBottomSheet and then opens the picker,
            // selectedAssetUris contains the updated list without deleted images, but
            // cached.selectedUris still has the old (pre-deletion) selection.
            val resolvedSelection = if (selectedAssetUris.isNotEmpty()) {
                selectedAssetUris
            } else {
                cached.selectedUris
            }
            _uiState.value = createWithAssetsState(
                permissionMode = newMode,
                assets = cached.assets,
                selectedUris = resolvedSelection,
                preferredAlbumId = cached.selectedAlbumId
            )
            return
        }

        val preferredSelectedUris = currentWithAssets
            ?.selectedAssets
            ?.map { it.uri.toString() }
            ?: selectedAssetUris  // Use initial selectedAssetUris if no current state (preserves order)
        val preferredAlbumId = currentWithAssets?.selectedAlbumId
        loadImages(
            permissionMode = newMode,
            preferredSelectedUris = preferredSelectedUris,
            preferredAlbumId = preferredAlbumId,
            forceShowLoading = forceReload || currentWithAssets == null
        )
    }

    /**
     * Load images from device gallery.
     * Produces AllPermission state for full access, LimitPermission for partial access.
     */
    private fun loadImages(
        permissionMode: PermissionMode,
        preferredSelectedUris: List<String>,
        preferredAlbumId: String?,
        forceShowLoading: Boolean
    ) {
        if (isRefreshing) return
        isRefreshing = true

        viewModelScope.launch {
            val previousState = _uiState.value as? AssetPickerUiState.WithAssets
            val fallbackState = previousState
            if (forceShowLoading || previousState == null) {
                _uiState.value = AssetPickerUiState.Loading
            }

            try {
                val mediaStoreImages = queryGalleryImages()

                val persistedUris = appContext.contentResolver.persistedUriPermissions
                    .map { it.uri }

                val persistedAssets = withContext(Dispatchers.IO) {
                    persistedUris.map { uri -> createAssetFromUri(uri) }
                }

                val images = mergeDistinctByKey(
                    transientCameraAssets,
                    mediaStoreImages,
                    persistedAssets,
                    keySelector = { it.uri.toString() }
                ).sortedByDescending { it.dateAdded }

                val selectedSeed = if (preferredSelectedUris.isNotEmpty()) {
                    preferredSelectedUris
                } else {
                    previousState
                        ?.selectedAssets
                        ?.map { it.uri.toString() }
                        ?: emptyList()
                }
                val availableUris = images.map { it.uri.toString() }.toSet()
                val retainedSelectedUris = retainSelectedUrisAfterReload(
                    selectedUris = selectedSeed,
                    availableUris = availableUris
                )

                _uiState.value = createWithAssetsState(
                    permissionMode = permissionMode,
                    assets = images,
                    selectedUris = retainedSelectedUris,
                    preferredAlbumId = preferredAlbumId ?: previousState?.selectedAlbumId
                )
                val latestWithAssets = _uiState.value as? AssetPickerUiState.WithAssets
                if (latestWithAssets != null) {
                    persistSessionSnapshot(latestWithAssets, permissionMode)
                }
            } catch (e: Exception) {
                if (fallbackState != null) {
                    _uiState.value = fallbackState
                }
                _messageEvent.value = e.message ?: "Failed to load images"
            } finally {
                isRefreshing = false
            }
        }
    }

    private fun createWithAssetsState(
        permissionMode: PermissionMode,
        assets: List<GalleryAsset>,
        selectedUris: List<String>,
        preferredAlbumId: String?
    ): AssetPickerUiState.WithAssets {
        val albums = buildAlbumFilters(assets)
        val selectedAlbumId = preferredAlbumId
            ?.takeIf { selected ->
                selected == AlbumFilterType.ALL || albums.any { it.id == selected }
            }
            ?: AlbumFilterType.ALL
        val filteredAssets = filterAssetsByAlbum(assets, selectedAlbumId, albums)

        // Create a map for quick lookup
        val assetMap = assets.associateBy { it.uri.toString() }

        // Preserve order of selectedUris
        val selectedAssets = selectedUris
            .mapNotNull { uri -> assetMap[uri] }
            .take(MAX_SELECTION)

        return if (permissionMode == PermissionMode.LIMITED) {
            AssetPickerUiState.WithAssets.LimitPermission(
                assets = assets,
                filteredAssets = filteredAssets,
                selectedAssets = selectedAssets,
                albums = albums,
                selectedAlbumId = selectedAlbumId
            )
        } else {
            AssetPickerUiState.WithAssets.AllPermission(
                assets = assets,
                filteredAssets = filteredAssets,
                selectedAssets = selectedAssets,
                albums = albums,
                selectedAlbumId = selectedAlbumId
            )
        }
    }

    private fun modeForState(state: AssetPickerUiState.WithAssets): PermissionMode {
        return when (state) {
            is AssetPickerUiState.WithAssets.AllPermission -> PermissionMode.FULL
            is AssetPickerUiState.WithAssets.LimitPermission -> PermissionMode.LIMITED
        }
    }

    private fun persistSessionSnapshot(
        state: AssetPickerUiState.WithAssets,
        mode: PermissionMode
    ) {
        AssetPickerSessionCache.snapshot = AssetPickerSessionSnapshot(
            permissionMode = mode,
            assets = state.assets,
            // Do not keep selected items between picker openings.
            selectedUris = emptyList(),
            selectedAlbumId = state.selectedAlbumId,
            gridScrollState = _gridScrollState.value
        )
    }

    private fun persistDeniedSnapshot() {
        AssetPickerSessionCache.snapshot = AssetPickerSessionSnapshot(
            permissionMode = PermissionMode.DENIED,
            assets = emptyList(),
            selectedUris = emptyList(),
            selectedAlbumId = AlbumFilterType.ALL,
            gridScrollState = _gridScrollState.value
        )
    }

    /**
     * Select an album filter
     */
    fun selectAlbum(albumId: String) {
        val currentState = _uiState.value as? AssetPickerUiState.WithAssets ?: return
        val filteredAssets = filterAssetsByAlbum(currentState.assets, albumId, currentState.albums)
        val updatedState = currentState.copyAssets(
            filteredAssets = filteredAssets,
            selectedAlbumId = albumId
        )
        _uiState.value = updatedState
        persistSessionSnapshot(updatedState, modeForState(updatedState))
    }

    /**
     * Add one copy of an asset to the selection.
     *
     * The same photo can be selected multiple times (each tap appends another instance),
     * so [AssetPickerUiState.WithAssets.selectedAssets] may contain duplicates. Removal is
     * index-based via [removeSelectedAt]. Adding is blocked once [MAX_SELECTION] is reached.
     */
    fun addAssetSelection(asset: GalleryAsset) {
        val currentState = _uiState.value as? AssetPickerUiState.WithAssets ?: return
        if (currentState.selectedAssets.size >= MAX_SELECTION) {
            _messageEvent.value = "Maximum $MAX_SELECTION images can be selected"
            return
        }
        val updatedSelection = currentState.selectedAssets + asset
        Analytics.trackMediaSelect()
        val updatedState = currentState.copyAssets(selectedAssets = updatedSelection)
        _uiState.value = updatedState
        persistSessionSnapshot(updatedState, modeForState(updatedState))
        trackPromoteState()
    }

    /**
     * Remove the selected instance at [index] from the selection tray.
     * Index-based so a single duplicate can be removed without affecting other copies;
     * removing immediately frees up a slot.
     */
    fun removeSelectedAt(index: Int) {
        val currentState = _uiState.value as? AssetPickerUiState.WithAssets ?: return
        if (index !in currentState.selectedAssets.indices) return
        val updatedSelection = currentState.selectedAssets.toMutableList().apply { removeAt(index) }
        Analytics.trackMediaUnselect()
        val updatedState = currentState.copyAssets(selectedAssets = updatedSelection)
        _uiState.value = updatedState
        persistSessionSnapshot(updatedState, modeForState(updatedState))
        trackPromoteState()
    }

    /**
     * Called when a photo is captured via the system camera.
     * Prepends the captured image to the asset list and auto-selects it.
     * If gallery is not yet loaded, creates a minimal WithAssets state from the captured image.
     */
    fun onCameraImageCaptured(uri: Uri) {
        viewModelScope.launch {
            val newAsset = withContext(Dispatchers.IO) { createAssetFromUri(uri) }
            transientCameraAssets.removeAll { it.uri.toString() == newAsset.uri.toString() }
            transientCameraAssets.add(0, newAsset)

            val currentState = _uiState.value
            if (currentState is AssetPickerUiState.WithAssets) {
                val updatedAssets = mergeDistinctByKey(
                    listOf(newAsset),
                    currentState.assets,
                    keySelector = { it.uri.toString() }
                )
                val updatedAlbums = buildAlbumFilters(updatedAssets)
                val selectedAlbumId = currentState.selectedAlbumId
                    .takeIf { selected ->
                        selected == AlbumFilterType.ALL || updatedAlbums.any { it.id == selected }
                    }
                    ?: AlbumFilterType.ALL
                val updatedFiltered = filterAssetsByAlbum(updatedAssets, selectedAlbumId, updatedAlbums)
                val isAlreadySelected = currentState.selectedAssets.any {
                    it.uri.toString() == newAsset.uri.toString()
                }
                val updatedSelected = if (isAlreadySelected) {
                    currentState.selectedAssets
                } else if (currentState.selectedAssets.size < MAX_SELECTION) {
                    currentState.selectedAssets + newAsset
                } else {
                    currentState.selectedAssets
                }
                val updatedState = currentState.copyAssets(
                    assets = updatedAssets,
                    filteredAssets = updatedFiltered,
                    selectedAssets = updatedSelected,
                    albums = updatedAlbums,
                    selectedAlbumId = selectedAlbumId
                )
                _uiState.value = updatedState
                persistSessionSnapshot(updatedState, modeForState(updatedState))
            } else {
                val initialAssets = listOf(newAsset)
                val initialAlbums = buildAlbumFilters(initialAssets)
                val initialState = if (currentPermissionMode == PermissionMode.LIMITED) {
                    AssetPickerUiState.WithAssets.LimitPermission(
                        assets = initialAssets,
                        filteredAssets = initialAssets,
                        selectedAssets = initialAssets,
                        albums = initialAlbums,
                        selectedAlbumId = AlbumFilterType.ALL
                    )
                } else {
                    AssetPickerUiState.WithAssets.AllPermission(
                        assets = initialAssets,
                        filteredAssets = initialAssets,
                        selectedAssets = initialAssets,
                        albums = initialAlbums,
                        selectedAlbumId = AlbumFilterType.ALL
                    )
                }
                _uiState.value = initialState
                persistSessionSnapshot(initialState, modeForState(initialState))
            }
            trackPromoteState()
        }
    }

    /**
     * Clear all selections
     */
    fun clearSelection() {
        val currentState = _uiState.value as? AssetPickerUiState.WithAssets ?: return
        val updatedState = currentState.copyAssets(selectedAssets = emptyList())
        _uiState.value = updatedState
        persistSessionSnapshot(updatedState, modeForState(updatedState))
    }

    /**
     * Confirm selection and proceed
     * - Create mode: Create new project and navigate to editor
     * - Add mode: Add assets to existing project and go back
     */
    fun confirmSelection() {
        val currentState = _uiState.value as? AssetPickerUiState.WithAssets ?: return
        if (currentState.selectedAssets.size < minSelection) return
        if (!_isConfirming.compareAndSet(expect = false, update = true)) return
        buildDraftId()?.let { draftId ->
            notificationScheduler.cancelDraftReminders(draftId)
            preferencesManager.clearDraftReminderState(draftId)
        }
        Analytics.trackMediaComplete(currentState.selectedAssets.size)

        viewModelScope.launch {
            val uris = currentState.selectedAssets.map { it.uri }
            // Beat-sync mode only - duration calculated from beat-sync data later
            val analyticsVideoId = buildDraftId() ?: "picker_generate_${System.currentTimeMillis()}"

            if (!templateId.isNullOrBlank()) {
                Analytics.trackVideoGenerate(
                    videoId = analyticsVideoId,
                    templateId = templateId,
                    songId = overrideSongId.takeIf { it >= 0L }?.toString(),
                    duration = null, // Duration will be calculated from beat-sync data
                    mediaQuantity = uris.size
                )
            }

            when {
                // PRIORITY 1: Template already selected - go directly to Editor
                // This handles both:
                // - Gallery → Template → Image → Editor
                // - Music → Template → Image → Editor (templateId + overrideSongId both set)
                templateId != null -> {
                    // Fetch the specific template by ID with retry (handles search-result templates
                    // that may not appear in the default top-N sorted list).
                    val maxRetries = 3
                    var template: com.videomaker.aimusic.domain.model.VideoTemplate? = null
                    var lastError: Throwable? = null

                    for (attempt in 1..maxRetries) {
                        val result = templateRepository.getTemplateById(templateId)
                        if (result.isSuccess) {
                            template = result.getOrNull()
                            lastError = null
                            break
                        } else {
                            lastError = result.exceptionOrNull()
                            android.util.Log.w("AssetPickerVM", "Template fetch failed (attempt $attempt/$maxRetries): ${lastError?.message}")
                            if (attempt < maxRetries) {
                                kotlinx.coroutines.delay(1000L * attempt)
                            }
                        }
                    }

                    if (lastError != null) {
                        Analytics.trackEditorPrepareFailed(
                            "template_fetch_failed",
                            overrideSongId.takeIf { it >= 0L }?.toString()
                        )
                        _messageEvent.value = lastError.message ?: "Failed to load template"
                        _isConfirming.value = false
                    } else if (template != null) {
                        // Determine song ID (override or template's song)
                        val songId = if (overrideSongId >= 0L) overrideSongId
                                    else template.songId.takeIf { it > 0L }

                        // Fetch full song data (avoids redundant network fetch in Editor)
                        val song = songId?.let { id ->
                            songRepository.getSongById(id).getOrNull()
                        }

                        val initialData = EditorInitialData(
                            imageUris = uris.map { it.toString() },
                            effectSetId = template.effectSetId,
                            templateId = template.id,
                            musicSongId = songId,
                            musicSongName = song?.name,
                            aspectRatio = aspectRatio ?: AspectRatio.fromString(template.aspectRatio),
                            analyticsVideoId = analyticsVideoId,
                            musicSongUrl = song?.mp3Url,
                            musicSongCoverUrl = song?.coverUrl,
                            musicSongArtist = song?.artist,
                            musicSongDurationMs = song?.durationMs?.toLong(),
                            musicSongBeatsUrl = song?.beatsUrl,
                            musicSongHookStartTimes = song?.hookStartTimes ?: emptyList(),
                        )

                        _navigationEvent.send(AssetPickerNavigationEvent.NavigateToEditorWithData(initialData))
                    } else {
                        Analytics.trackEditorPrepareFailed(
                            "template_not_found",
                            overrideSongId.takeIf { it >= 0L }?.toString()
                        )
                        _messageEvent.value = "Template not found"
                        _isConfirming.value = false
                    }
                }
                // PRIORITY 2: Song-to-video mode WITHOUT template selected
                // Navigate directly to Editor - effect set will be fetched from Supabase
                isSongToVideoMode -> {
                    // Pre-fetch song data to avoid redundant fetch in Editor
                    val song = songRepository.getSongById(overrideSongId).getOrNull()
                    _navigationEvent.send(
                        AssetPickerNavigationEvent.NavigateToEditorWithData(
                            initialData = EditorInitialData(
                                imageUris = uris.map { it.toString() },
                                effectSetId = null,  // Editor will fetch first effect set from Supabase
                                templateId = null,
                                musicSongId = overrideSongId,
                                musicSongName = song?.name,
                                aspectRatio = AspectRatio.RATIO_9_16,
                                analyticsVideoId = analyticsVideoId,
                                musicSongUrl = song?.mp3Url,
                                musicSongCoverUrl = song?.coverUrl,
                                musicSongArtist = song?.artist,
                                musicSongDurationMs = song?.durationMs?.toLong(),
                                musicSongBeatsUrl = song?.beatsUrl,
                                musicSongHookStartTimes = song?.hookStartTimes ?: emptyList(),
                            )
                        )
                    )
                }
                isEditingMode -> {
                    // Editing mode - return selected URIs without saving to DB
                    // Used by ImagesBottomSheet to replace current assets list
                    val isAdReady = adsLoaderService.isInterstitialReady(
                        AdPlacement.INTERSTITIAL_PICKER_DONE
                    )
                    _navigationEvent.send(
                        AssetPickerNavigationEvent.SelectionConfirmed(
                            selectedUris = uris.map { it.toString() },
                            shouldShowAd = isAdReady
                        )
                    )
                }
                projectId != null -> {
                    // Add mode - add to existing project
                    addAssetsUseCase(projectId, uris)
                        .onSuccess {
                            _navigationEvent.send(AssetPickerNavigationEvent.AssetsAdded)
                        }
                        .onFailure { error ->
                            _messageEvent.value = error.message ?: "Failed to add assets"
                            _isConfirming.value = false
                        }
                }
                else -> {
                    // Create mode - create new project (beat-sync only)
                    // Duration will be calculated from beat-sync data in Editor
                    val settings = ProjectSettings(
                        totalDurationMs = 0L,
                        templateId = null
                    )

                    createProjectUseCase(uris, settings)
                        .onSuccess { project ->
                            _navigationEvent.send(AssetPickerNavigationEvent.NavigateToEditor(project.id))
                        }
                        .onFailure { error ->
                            _messageEvent.value = error.message ?: "Failed to create project"
                            _isConfirming.value = false
                        }
                }
            }
        }
    }

    /**
     * Navigate back
     */
    fun navigateBack() {
        // In editing mode, skip exit ad — just pop back to the editor.
        // The exit ad's onShown + action both call onNavigateBack(), which would
        // double-pop the backstack (picker + editor) and land on Home.
        val isAdReady = if (isEditingMode) {
            false
        } else {
            adsLoaderService.isInterstitialReady(AdPlacement.INTERSTITIAL_ASSET_PICKER_EXIT)
        }

        android.util.Log.d("AssetPickerVM", "🔙 navigateBack - Ad ready: $isAdReady, editMode: $isEditingMode")

        // Send navigation event with ad status (Channel - one-time event, no replay)
        // Screen will show ad if ready, otherwise navigate immediately
        viewModelScope.launch {
            _navigationEvent.send(AssetPickerNavigationEvent.RequestExitWithAd(isAdReady))
        }
    }

    fun onMessageHandled() {
        _messageEvent.value = null
    }

    private fun buildDraftId(): String? {
        val hasDraftContext = !templateId.isNullOrBlank() || overrideSongId >= 0L
        if (!hasDraftContext) return null
        val templatePart = templateId?.ifBlank { "none" } ?: "none"
        val songPart = if (overrideSongId >= 0L) overrideSongId.toString() else "none"
        val projectPart = projectId?.ifBlank { "new" } ?: "new"
        return "draft_tpl_${templatePart}_song_${songPart}_project_${projectPart}"
    }

    // ============================================
    // PRIVATE: Album Filtering
    // ============================================

    private fun buildAlbumFilters(assets: List<GalleryAsset>): List<AlbumFilter> {
        // Build the real (bucket-backed) albums first. The synthetic "All" album is only useful
        // when there is more than one real album to switch between — with a single album, "All"
        // would just duplicate it, so we omit it and show that one album without a dropdown.
        val realAlbums = mutableListOf<AlbumFilter>()

        // Group by bucket and find Camera/Screenshots
        val bucketGroups = assets.groupBy { it.bucketId }

        // Find Camera folder (common names: Camera, DCIM)
        val cameraAssets = assets.filter { asset ->
            val name = asset.bucketName.lowercase()
            name == "camera" || name == "dcim"
        }
        cameraAssets.firstOrNull()?.let { firstCameraAsset ->
            realAlbums.add(
                AlbumFilter(
                    id = AlbumFilterType.CAMERA,
                    displayName = "Camera",
                    bucketId = firstCameraAsset.bucketId,
                    count = cameraAssets.size
                )
            )
        }

        // Find Screenshots folder
        val screenshotAssets = assets.filter { asset ->
            val name = asset.bucketName.lowercase()
            name.contains("screenshot") || name.contains("screen shot")
        }
        screenshotAssets.firstOrNull()?.let { firstScreenshotAsset ->
            realAlbums.add(
                AlbumFilter(
                    id = AlbumFilterType.SCREENSHOTS,
                    displayName = "Screenshots",
                    bucketId = firstScreenshotAsset.bucketId,
                    count = screenshotAssets.size
                )
            )
        }

        // Add other albums (sorted by count, top 10)
        val existingBucketIds = realAlbums.mapNotNull { it.bucketId }.toSet()
        val otherAlbums = bucketGroups
            .filter { (bucketId, _) -> bucketId !in existingBucketIds }
            .mapNotNull { (bucketId, albumAssets) ->
                albumAssets.firstOrNull()?.let { firstAsset ->
                    AlbumFilter(
                        id = "bucket_$bucketId",
                        displayName = firstAsset.bucketName,
                        bucketId = bucketId,
                        count = albumAssets.size
                    )
                }
            }
            .sortedByDescending { it.count }
            .take(10)

        realAlbums.addAll(otherAlbums)

        // Only prepend "All" when there are genuinely 2+ distinct album types to choose between.
        return if (realAlbums.size >= 2) {
            buildList {
                add(
                    AlbumFilter(
                        id = AlbumFilterType.ALL,
                        displayName = "All",
                        bucketId = null,
                        count = assets.size
                    )
                )
                addAll(realAlbums)
            }
        } else {
            realAlbums
        }
    }

    /**
     * Builds a GalleryAsset from a Photo Picker URI.
     * Queries MediaStore for available metadata; falls back to minimal data on failure.
     * Photo Picker URIs support a subset of MediaStore columns, so each column index
     * is checked before use.
     */
    private fun createAssetFromUri(uri: Uri): GalleryAsset {
        val projection = arrayOf(
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
            MediaStore.Images.Media.BUCKET_ID,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME
        )
        return try {
            appContext.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME)
                    val dateIdx = cursor.getColumnIndex(MediaStore.Images.Media.DATE_ADDED)
                    val widthIdx = cursor.getColumnIndex(MediaStore.Images.Media.WIDTH)
                    val heightIdx = cursor.getColumnIndex(MediaStore.Images.Media.HEIGHT)
                    val bucketIdIdx = cursor.getColumnIndex(MediaStore.Images.Media.BUCKET_ID)
                    val bucketNameIdx = cursor.getColumnIndex(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
                    GalleryAsset(
                        id = uri.toString().hashCode().toLong(),
                        uri = uri,
                        displayName = if (nameIdx >= 0) cursor.getString(nameIdx) ?: "Photo" else "Photo",
                        dateAdded = if (dateIdx >= 0) cursor.getLong(dateIdx) else System.currentTimeMillis() / 1000,
                        width = if (widthIdx >= 0) cursor.getInt(widthIdx) else 0,
                        height = if (heightIdx >= 0) cursor.getInt(heightIdx) else 0,
                        bucketId = if (bucketIdIdx >= 0) cursor.getLong(bucketIdIdx) else -1L,
                        bucketName = if (bucketNameIdx >= 0) cursor.getString(bucketNameIdx) ?: "Photos" else "Photos",
                    )
                } else createMinimalAsset(uri)
            } ?: createMinimalAsset(uri)
        } catch (e: Exception) {
            createMinimalAsset(uri)
        }
    }

    private fun createMinimalAsset(uri: Uri): GalleryAsset = GalleryAsset(
        id = uri.toString().hashCode().toLong(),
        uri = uri,
        displayName = uri.lastPathSegment ?: "Photo",
        dateAdded = System.currentTimeMillis() / 1000,
        width = 0,
        height = 0,
        bucketId = -1L,
        bucketName = "Photos",
    )

    private fun filterAssetsByAlbum(
        assets: List<GalleryAsset>,
        albumId: String,
        albums: List<AlbumFilter>
    ): List<GalleryAsset> {
        if (albumId == AlbumFilterType.ALL) {
            return assets
        }

        val album = albums.find { it.id == albumId } ?: return assets
        val bucketId = album.bucketId ?: return assets

        // For Camera filter, include both "Camera" and "DCIM" folders
        if (albumId == AlbumFilterType.CAMERA) {
            return assets.filter { asset ->
                val name = asset.bucketName.lowercase()
                name == "camera" || name == "dcim"
            }
        }

        // For Screenshots filter
        if (albumId == AlbumFilterType.SCREENSHOTS) {
            return assets.filter { asset ->
                val name = asset.bucketName.lowercase()
                name.contains("screenshot") || name.contains("screen shot")
            }
        }

        return assets.filter { it.bucketId == bucketId }
    }

    // ============================================
    // PRIVATE: MediaStore Query
    // ============================================

    private suspend fun queryGalleryImages(): List<GalleryAsset> = withContext(Dispatchers.IO) {
        val images = mutableListOf<GalleryAsset>()

        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
            MediaStore.Images.Media.BUCKET_ID,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME
        )

        // QUERY_ARG_LIMIT added in API 30. On API 28-29 we append "LIMIT N" to the sortOrder
        // string, which MediaStore passes directly to SQLite and is accepted on those versions.
        val cursor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val queryArgs = Bundle().apply {
                putStringArray(ContentResolver.QUERY_ARG_SORT_COLUMNS,
                    arrayOf(MediaStore.Images.Media.DATE_ADDED))
                putInt(ContentResolver.QUERY_ARG_SORT_DIRECTION,
                    ContentResolver.QUERY_SORT_DIRECTION_DESCENDING)
                putInt(ContentResolver.QUERY_ARG_LIMIT, MAX_IMAGES)
            }
            appContext.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                queryArgs,
                null
            )
        } else {
            appContext.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                "${MediaStore.Images.Media.DATE_ADDED} DESC LIMIT $MAX_IMAGES"
            )
        }

        cursor?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            val widthColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
            val heightColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
            val bucketIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_ID)
            val bucketNameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val name = cursor.getString(nameColumn) ?: "Unknown"
                val date = cursor.getLong(dateColumn)
                val width = cursor.getInt(widthColumn)
                val height = cursor.getInt(heightColumn)
                val bucketId = cursor.getLong(bucketIdColumn)
                val bucketName = cursor.getString(bucketNameColumn) ?: "Unknown"

                val contentUri = ContentUris.withAppendedId(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    id
                )

                images.add(
                    GalleryAsset(
                        id = id,
                        uri = contentUri,
                        displayName = name,
                        dateAdded = date,
                        width = width,
                        height = height,
                        bucketId = bucketId,
                        bucketName = bucketName
                    )
                )
            }
        }

        images
    }
}
