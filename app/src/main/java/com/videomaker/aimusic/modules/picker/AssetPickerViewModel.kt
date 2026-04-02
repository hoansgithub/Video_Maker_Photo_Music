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
import com.videomaker.aimusic.domain.model.AspectRatio
import com.videomaker.aimusic.domain.model.EditorInitialData
import com.videomaker.aimusic.domain.repository.SongRepository
import com.videomaker.aimusic.domain.repository.TemplateRepository
import com.videomaker.aimusic.domain.usecase.AddAssetsUseCase
import com.videomaker.aimusic.domain.usecase.CreateProjectUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    data object NavigateBack : AssetPickerNavigationEvent()
    data class NavigateToEditor(val projectId: String) : AssetPickerNavigationEvent()
    /** NEW: Navigate to Editor with initial data (from template flow) */
    data class NavigateToEditorWithData(val initialData: EditorInitialData) : AssetPickerNavigationEvent()
    /** Assets added to existing project - just go back */
    data object AssetsAdded : AssetPickerNavigationEvent()
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
    private val projectId: String? = null, // null = create new project, non-null = add to existing
    private val templateId: String? = null,  // non-null = template mode, bypasses project creation
    private val overrideSongId: Long = -1L,   // >= 0 = song-to-video mode, overrides template song
    private val aspectRatio: AspectRatio? = null  // User's selected aspect ratio from template previewer
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
    val minSelection: Int get() = if (isTemplateMode) 3 else 1

    // ============================================
    // STATE
    // ============================================

    private val _uiState = MutableStateFlow<AssetPickerUiState>(AssetPickerUiState.Initial)
    val uiState: StateFlow<AssetPickerUiState> = _uiState.asStateFlow()

    // Tracks whether the current permission is limited (Android 14+ partial access)
    private var isLimitedPermission = false

    // ============================================
    // NAVIGATION EVENTS (StateFlow-based - Google recommended)
    // ============================================

    private val _navigationEvent = MutableStateFlow<AssetPickerNavigationEvent?>(null)
    val navigationEvent: StateFlow<AssetPickerNavigationEvent?> = _navigationEvent.asStateFlow()

    // USER MESSAGE EVENTS (for snackbars/toasts)
    // ============================================

    private val _messageEvent = MutableStateFlow<String?>(null)
    val messageEvent: StateFlow<String?> = _messageEvent.asStateFlow()

    init {
        // NOTE: With the new flow (Template Browse → Select Template → Pick Images),
        // it's valid to have both overrideSongId and templateId:
        // - User picks a song → browses templates with that song → selects a template → picks images
        // The confirmSelection() method handles priority: overrideSongId takes priority over template's song
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
    fun onPermissionGranted(isLimited: Boolean = false) {
        isLimitedPermission = isLimited
        loadImages()
    }

    /**
     * Called when permission is denied
     */
    fun onPermissionDenied() {
        _uiState.value = AssetPickerUiState.DeniedPermission
    }

    /**
     * Load images from device gallery.
     * Produces AllPermission state for full access, LimitPermission for partial access.
     */
    fun loadImages() {
        viewModelScope.launch {
            _uiState.value = AssetPickerUiState.Loading

            try {
                val mediaStoreImages = queryGalleryImages()

                val persistedUris = appContext.contentResolver.persistedUriPermissions
                    .map { it.uri }

                // Chuyển đổi Uri thành Asset model của bạn
                val persistedAssets = withContext(Dispatchers.IO) {
                    persistedUris.map { uri -> createAssetFromUri(uri) }
                }

                val images = mediaStoreImages + persistedAssets

                val albums = buildAlbumFilters(images)

                _uiState.value = if (isLimitedPermission) {
                    AssetPickerUiState.WithAssets.LimitPermission(
                        assets = images,
                        filteredAssets = images,
                        selectedAssets = emptyList(),
                        albums = albums,
                        selectedAlbumId = AlbumFilterType.ALL
                    )
                } else {
                    AssetPickerUiState.WithAssets.AllPermission(
                        assets = images,
                        filteredAssets = images,
                        selectedAssets = emptyList(),
                        albums = albums,
                        selectedAlbumId = AlbumFilterType.ALL
                    )
                }
            } catch (e: Exception) {
                _messageEvent.value = e.message ?: "Failed to load images"
            }
        }
    }

    /**
     * Select an album filter
     */
    fun selectAlbum(albumId: String) {
        val currentState = _uiState.value as? AssetPickerUiState.WithAssets ?: return
        val filteredAssets = filterAssetsByAlbum(currentState.assets, albumId, currentState.albums)
        _uiState.value = currentState.copyAssets(
            filteredAssets = filteredAssets,
            selectedAlbumId = albumId
        )
    }

    /**
     * Toggle selection of an asset
     */
    fun toggleAssetSelection(asset: GalleryAsset) {
        val currentState = _uiState.value as? AssetPickerUiState.WithAssets ?: return
        val currentSelection = currentState.selectedAssets.toMutableList()

        if (currentSelection.contains(asset)) {
            currentSelection.remove(asset)
        } else {
            if (currentSelection.size >= MAX_SELECTION) {
                _messageEvent.value = "Maximum $MAX_SELECTION images can be selected"
                return
            }
            currentSelection.add(asset)
        }

        _uiState.value = currentState.copyAssets(selectedAssets = currentSelection)
    }

    /**
     * Called when a photo is captured via the system camera.
     * Prepends the captured image to the asset list and auto-selects it.
     * If gallery is not yet loaded, creates a minimal WithAssets state from the captured image.
     */
    fun onCameraImageCaptured(uri: Uri) {
        viewModelScope.launch {
            val newAsset = withContext(Dispatchers.IO) { createAssetFromUri(uri) }

            val currentState = _uiState.value
            if (currentState is AssetPickerUiState.WithAssets) {
                val updatedAssets = listOf(newAsset) + currentState.assets
                val updatedFiltered = listOf(newAsset) + currentState.filteredAssets
                val updatedSelected = if (currentState.selectedAssets.size < MAX_SELECTION) {
                    currentState.selectedAssets + newAsset
                } else {
                    currentState.selectedAssets
                }
                _uiState.value = currentState.copyAssets(
                    assets = updatedAssets,
                    filteredAssets = updatedFiltered,
                    selectedAssets = updatedSelected
                )
            } else {
                _uiState.value = AssetPickerUiState.WithAssets.AllPermission(
                    assets = listOf(newAsset),
                    filteredAssets = listOf(newAsset),
                    selectedAssets = listOf(newAsset)
                )
            }
        }
    }

    /**
     * Clear all selections
     */
    fun clearSelection() {
        val currentState = _uiState.value as? AssetPickerUiState.WithAssets ?: return
        _uiState.value = currentState.copyAssets(selectedAssets = emptyList())
    }

    /**
     * Confirm selection and proceed
     * - Create mode: Create new project and navigate to editor
     * - Add mode: Add assets to existing project and go back
     */
    fun confirmSelection() {
        val currentState = _uiState.value as? AssetPickerUiState.WithAssets ?: return
        if (currentState.selectedAssets.size < minSelection) return

        viewModelScope.launch {
            val uris = currentState.selectedAssets.map { it.uri }

            when {
                // PRIORITY 1: Template already selected - go directly to Editor
                // This handles both:
                // - Gallery → Template → Image → Editor
                // - Music → Template → Image → Editor (templateId + overrideSongId both set)
                templateId != null -> {
                    // Fetch templates and find by ID
                    templateRepository.getTemplates(limit = 100, offset = 0)
                        .onSuccess { templates ->
                            val template = templates.find { it.id == templateId }
                            if (template != null) {
                                // Determine song ID (override or template's song)
                                val songId = if (overrideSongId >= 0L) overrideSongId
                                            else template.songId.takeIf { it > 0L }

                                // Fetch song name (avoid extra network request in Editor)
                                val songName = songId?.let { id ->
                                    songRepository.getSongById(id).getOrNull()?.name
                                }

                                val initialData = EditorInitialData(
                                    imageUris = uris.map { it.toString() },
                                    effectSetId = template.effectSetId,
                                    imageDurationMs = template.imageDurationMs.toLong(),
                                    transitionPercentage = template.transitionPct,
                                    musicSongId = songId,
                                    musicSongName = songName,
                                    aspectRatio = aspectRatio ?: AspectRatio.fromString(template.aspectRatio)
                                )

                                _navigationEvent.value = AssetPickerNavigationEvent.NavigateToEditorWithData(initialData)
                            } else {
                                _messageEvent.value = "Template not found"
                            }
                        }
                        .onFailure { error ->
                            _messageEvent.value = error.message ?: "Failed to load template"
                        }
                }
                // PRIORITY 2: Song-to-video mode WITHOUT template selected
                // Navigate to template selector with the chosen song
                isSongToVideoMode -> {
                    _navigationEvent.value = AssetPickerNavigationEvent.NavigateToTemplatePreviewer(
                        templateId = "",
                        imageUris = uris.map { it.toString() },
                        overrideSongId = overrideSongId
                    )
                }
                projectId != null -> {
                    // Add mode - add to existing project
                    addAssetsUseCase(projectId, uris)
                        .onSuccess {
                            _navigationEvent.value = AssetPickerNavigationEvent.AssetsAdded
                        }
                        .onFailure { error ->
                            _messageEvent.value = error.message ?: "Failed to add assets"
                        }
                }
                else -> {
                    // Create mode - create new project
                    createProjectUseCase(uris)
                        .onSuccess { project ->
                            _navigationEvent.value = AssetPickerNavigationEvent.NavigateToEditor(project.id)
                        }
                        .onFailure { error ->
                            _messageEvent.value = error.message ?: "Failed to create project"
                        }
                }
            }
        }
    }

    /**
     * Navigate back
     */
    fun navigateBack() {
        _navigationEvent.value = AssetPickerNavigationEvent.NavigateBack
    }

    /**
     * Called by UI after navigation is handled - clears the event
     */
    fun onNavigationHandled() {
        _navigationEvent.value = null
    }

    fun onMessageHandled() {
        _messageEvent.value = null
    }

    // ============================================
    // PRIVATE: Album Filtering
    // ============================================

    private fun buildAlbumFilters(assets: List<GalleryAsset>): List<AlbumFilter> {
        val filters = mutableListOf<AlbumFilter>()

        // All Media filter
        filters.add(
            AlbumFilter(
                id = AlbumFilterType.ALL,
                displayName = "All",
                bucketId = null,
                count = assets.size
            )
        )

        // Group by bucket and find Camera/Screenshots
        val bucketGroups = assets.groupBy { it.bucketId }

        // Find Camera folder (common names: Camera, DCIM)
        val cameraAssets = assets.filter { asset ->
            val name = asset.bucketName.lowercase()
            name == "camera" || name == "dcim"
        }
        cameraAssets.firstOrNull()?.let { firstCameraAsset ->
            filters.add(
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
            filters.add(
                AlbumFilter(
                    id = AlbumFilterType.SCREENSHOTS,
                    displayName = "Screenshots",
                    bucketId = firstScreenshotAsset.bucketId,
                    count = screenshotAssets.size
                )
            )
        }

        // Add other albums (sorted by count, top 10)
        val existingBucketIds = filters.mapNotNull { it.bucketId }.toSet()
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

        filters.addAll(otherAlbums)

        return filters
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