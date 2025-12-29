package co.alcheclub.video.maker.photo.music.modules.picker

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.alcheclub.video.maker.photo.music.domain.usecase.AddAssetsUseCase
import co.alcheclub.video.maker.photo.music.domain.usecase.CreateProjectUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
 */
sealed class AssetPickerUiState {
    data object Initial : AssetPickerUiState()
    data object Loading : AssetPickerUiState()
    data class Success(
        val assets: List<GalleryAsset>,
        val filteredAssets: List<GalleryAsset>,
        val selectedAssets: List<GalleryAsset> = emptyList(),
        val albums: List<AlbumFilter> = emptyList(),
        val selectedAlbumId: String = AlbumFilterType.ALL
    ) : AssetPickerUiState()
    data class Error(val message: String) : AssetPickerUiState()
}

/**
 * Navigation events for Asset Picker
 */
sealed class AssetPickerNavigationEvent {
    data object NavigateBack : AssetPickerNavigationEvent()
    data class NavigateToEditor(val projectId: String) : AssetPickerNavigationEvent()
    /** Assets added to existing project - just go back */
    data object AssetsAdded : AssetPickerNavigationEvent()
}

/**
 * AssetPickerViewModel - Manages gallery image loading, filtering, and selection
 *
 * Performance optimizations:
 * - Lazy loading via StateFlow (UI only renders visible items)
 * - Single MediaStore query with bucket grouping
 * - Filtering done in-memory after initial load
 * - Image thumbnails handled by Coil with caching
 */
class AssetPickerViewModel(
    context: Context,  // Convert to Application context to prevent memory leak
    private val createProjectUseCase: CreateProjectUseCase,
    private val addAssetsUseCase: AddAssetsUseCase,
    private val projectId: String? = null // null = create new project, non-null = add to existing
) : ViewModel() {

    // Use applicationContext to prevent Activity memory leak
    private val appContext: Context = context.applicationContext

    /** True if adding to existing project, false if creating new */
    val isAddMode: Boolean get() = projectId != null

    // ============================================
    // STATE
    // ============================================

    private val _uiState = MutableStateFlow<AssetPickerUiState>(AssetPickerUiState.Initial)
    val uiState: StateFlow<AssetPickerUiState> = _uiState.asStateFlow()

    private val _permissionGranted = MutableStateFlow(false)
    val permissionGranted: StateFlow<Boolean> = _permissionGranted.asStateFlow()

    // ============================================
    // NAVIGATION EVENTS
    // ============================================

    private val _navigationEvent = Channel<AssetPickerNavigationEvent>(Channel.BUFFERED)
    val navigationEvent = _navigationEvent.receiveAsFlow()

    // ============================================
    // PUBLIC API
    // ============================================

    /**
     * Called when permission is granted
     */
    fun onPermissionGranted() {
        _permissionGranted.value = true
        loadImages()
    }

    /**
     * Called when permission is denied
     */
    fun onPermissionDenied() {
        _permissionGranted.value = false
        _uiState.value = AssetPickerUiState.Error("Photo library permission is required to select images")
    }

    /**
     * Load images from device gallery
     */
    fun loadImages() {
        viewModelScope.launch {
            _uiState.value = AssetPickerUiState.Loading

            try {
                val images = queryGalleryImages()
                val albums = buildAlbumFilters(images)

                _uiState.value = AssetPickerUiState.Success(
                    assets = images,
                    filteredAssets = images,
                    selectedAssets = emptyList(),
                    albums = albums,
                    selectedAlbumId = AlbumFilterType.ALL
                )
            } catch (e: Exception) {
                _uiState.value = AssetPickerUiState.Error(
                    e.message ?: "Failed to load images"
                )
            }
        }
    }

    /**
     * Select an album filter
     */
    fun selectAlbum(albumId: String) {
        val currentState = _uiState.value
        if (currentState is AssetPickerUiState.Success) {
            val filteredAssets = filterAssetsByAlbum(currentState.assets, albumId, currentState.albums)
            _uiState.value = currentState.copy(
                filteredAssets = filteredAssets,
                selectedAlbumId = albumId
            )
        }
    }

    /**
     * Toggle selection of an asset
     */
    fun toggleAssetSelection(asset: GalleryAsset) {
        val currentState = _uiState.value
        if (currentState is AssetPickerUiState.Success) {
            val currentSelection = currentState.selectedAssets.toMutableList()

            if (currentSelection.contains(asset)) {
                currentSelection.remove(asset)
            } else {
                currentSelection.add(asset)
            }

            _uiState.value = currentState.copy(selectedAssets = currentSelection)
        }
    }

    /**
     * Clear all selections
     */
    fun clearSelection() {
        val currentState = _uiState.value
        if (currentState is AssetPickerUiState.Success) {
            _uiState.value = currentState.copy(selectedAssets = emptyList())
        }
    }

    /**
     * Confirm selection and proceed
     * - Create mode: Create new project and navigate to editor
     * - Add mode: Add assets to existing project and go back
     */
    fun confirmSelection() {
        val currentState = _uiState.value
        if (currentState is AssetPickerUiState.Success && currentState.selectedAssets.isNotEmpty()) {
            viewModelScope.launch {
                try {
                    val uris = currentState.selectedAssets.map { it.uri }

                    if (projectId != null) {
                        // Add mode - add to existing project
                        addAssetsUseCase(projectId, uris)
                        _navigationEvent.send(AssetPickerNavigationEvent.AssetsAdded)
                    } else {
                        // Create mode - create new project
                        val project = createProjectUseCase(uris)
                        _navigationEvent.send(AssetPickerNavigationEvent.NavigateToEditor(project.id))
                    }
                } catch (e: Exception) {
                    _uiState.value = AssetPickerUiState.Error(
                        e.message ?: if (projectId != null) "Failed to add assets" else "Failed to create project"
                    )
                }
            }
        }
    }

    /**
     * Navigate back
     */
    fun navigateBack() {
        viewModelScope.launch {
            _navigationEvent.send(AssetPickerNavigationEvent.NavigateBack)
        }
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

        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        appContext.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            sortOrder
        )?.use { cursor ->
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
