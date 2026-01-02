package co.alcheclub.video.maker.photo.music.modules.picker

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.SubcomposeAsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Precision
import coil.size.Size

/**
 * Thumbnail size in pixels for image grid
 * Using fixed size for consistent memory usage and faster loading
 */
private const val THUMBNAIL_SIZE_DP = 120
private const val GRID_COLUMNS = 3

/**
 * Delay before loading images to allow bottom sheet animation to complete
 * This prevents janky animation when loading images while sheet is expanding
 */
private const val SHEET_ANIMATION_DELAY_MS = 350L

/**
 * AssetPickerScreen - Bottom sheet image picker with permission handling
 *
 * Features:
 * - Requests photo library permission on first load
 * - Album filter chips (All, Camera, Screenshots, etc.)
 * - Displays images in a grid from bottom sheet
 * - Multi-select with visual indicators
 * - Confirm selection to proceed to editor
 *
 * Performance optimizations:
 * - LazyVerticalGrid for virtualized rendering
 * - Coil with fixed thumbnail size (120dp) for memory efficiency
 * - Disk + Memory caching with time-based keys
 * - Crossfade animation for smooth loading
 * - Stable keys for efficient recomposition
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssetPickerScreen(
    viewModel: AssetPickerViewModel,
    onNavigateToEditor: (String) -> Unit,
    onNavigateBack: () -> Unit,
    onAssetsAdded: () -> Unit = {}
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val permissionGranted by viewModel.permissionGranted.collectAsStateWithLifecycle()

    // Show bottom sheet immediately for smooth transition
    var showBottomSheet by remember { mutableStateOf(true) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Determine the correct permission based on Android version
    val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_IMAGES
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.onPermissionGranted()
        } else {
            viewModel.onPermissionDenied()
        }
    }

    // Handle navigation events
    LaunchedEffect(Unit) {
        viewModel.navigationEvent.collect { event ->
            when (event) {
                is AssetPickerNavigationEvent.NavigateBack -> onNavigateBack()
                is AssetPickerNavigationEvent.NavigateToEditor -> onNavigateToEditor(event.projectId)
                is AssetPickerNavigationEvent.AssetsAdded -> onAssetsAdded()
            }
        }
    }

    // Wait for sheet animation to complete, then check permission and load images
    // Using LaunchedEffect(Unit) for one-time initialization
    LaunchedEffect(Unit) {
        // Wait for sheet animation to complete before requesting permission
        kotlinx.coroutines.delay(SHEET_ANIMATION_DELAY_MS)

        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            permission
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            viewModel.onPermissionGranted()
        } else {
            permissionLauncher.launch(permission)
        }
    }

    // Main content - semi-transparent background
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable { viewModel.navigateBack() }
    )

    // Bottom Sheet
    if (showBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = {
                showBottomSheet = false
                viewModel.navigateBack()
            },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            contentWindowInsets = { WindowInsets(0, 0, 0, 0) }
        ) {
            AssetPickerContent(
                uiState = uiState,
                onAlbumSelect = { albumId -> viewModel.selectAlbum(albumId) },
                onAssetClick = { asset -> viewModel.toggleAssetSelection(asset) },
                onConfirmClick = { viewModel.confirmSelection() },
                onCloseClick = {
                    showBottomSheet = false
                    viewModel.navigateBack()
                }
            )
        }
    }

    // Permission denied state
    if (!permissionGranted && uiState is AssetPickerUiState.Error) {
        PermissionDeniedContent(
            onRetryClick = { permissionLauncher.launch(permission) },
            onBackClick = { viewModel.navigateBack() }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AssetPickerContent(
    uiState: AssetPickerUiState,
    onAlbumSelect: (String) -> Unit,
    onAssetClick: (GalleryAsset) -> Unit,
    onConfirmClick: () -> Unit,
    onCloseClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onCloseClick) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close"
                )
            }

            Text(
                text = "Select Photos",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            // Selection count and confirm button
            if (uiState is AssetPickerUiState.Success && uiState.selectedAssets.isNotEmpty()) {
                Button(
                    onClick = onConfirmClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text("Done (${uiState.selectedAssets.size})")
                }
            } else {
                Spacer(modifier = Modifier.width(48.dp))
            }
        }

        // Album filter chips
        if (uiState is AssetPickerUiState.Success && uiState.albums.isNotEmpty()) {
            AlbumFilterChips(
                albums = uiState.albums,
                selectedAlbumId = uiState.selectedAlbumId,
                onAlbumSelect = onAlbumSelect
            )
        }

        // Content based on state
        when (uiState) {
            is AssetPickerUiState.Initial,
            is AssetPickerUiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            is AssetPickerUiState.Success -> {
                if (uiState.filteredAssets.isEmpty()) {
                    EmptyGalleryContent(
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    ImageGrid(
                        assets = uiState.filteredAssets,
                        selectedAssets = uiState.selectedAssets,
                        onAssetClick = onAssetClick,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            is AssetPickerUiState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = uiState.message,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlbumFilterChips(
    albums: List<AlbumFilter>,
    selectedAlbumId: String,
    onAlbumSelect: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        albums.forEach { album ->
            val isSelected = album.id == selectedAlbumId

            FilterChip(
                selected = isSelected,
                onClick = { onAlbumSelect(album.id) },
                label = {
                    Text(
                        text = "${album.displayName} (${album.count})",
                        fontSize = 13.sp
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    }
}

@Composable
private fun ImageGrid(
    assets: List<GalleryAsset>,
    selectedAssets: List<GalleryAsset>,
    onAssetClick: (GalleryAsset) -> Unit,
    modifier: Modifier = Modifier
) {
    val gridState = rememberLazyGridState()

    LazyVerticalGrid(
        columns = GridCells.Fixed(GRID_COLUMNS),
        state = gridState,
        contentPadding = PaddingValues(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
    ) {
        items(
            items = assets,
            key = { it.id } // Stable key for efficient recomposition
        ) { asset ->
            val isSelected = selectedAssets.any { it.id == asset.id }
            val selectionIndex = selectedAssets.indexOfFirst { it.id == asset.id }

            ImageGridItem(
                asset = asset,
                isSelected = isSelected,
                selectionIndex = if (isSelected) selectionIndex + 1 else null,
                onClick = { onAssetClick(asset) }
            )
        }
    }
}

@Composable
private fun ImageGridItem(
    asset: GalleryAsset,
    isSelected: Boolean,
    selectionIndex: Int?,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current

    // Calculate thumbnail size in pixels for Coil
    val thumbnailSizePx = with(density) { THUMBNAIL_SIZE_DP.dp.roundToPx() }

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .then(
                if (isSelected) {
                    Modifier.border(
                        width = 3.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(8.dp)
                    )
                } else {
                    Modifier
                }
            )
    ) {
        // Optimized image loading with Coil
        // Memory optimizations:
        // - RGB_565: 2 bytes/pixel vs ARGB_8888's 4 bytes = 50% memory reduction
        // - INEXACT precision: allows slightly smaller images, saving memory
        // - allowHardware(false): ensures software bitmaps that can be recycled
        SubcomposeAsyncImage(
            model = ImageRequest.Builder(context)
                .data(asset.uri)
                .size(Size(thumbnailSizePx, thumbnailSizePx)) // Fixed thumbnail size
                .bitmapConfig(Bitmap.Config.RGB_565) // Half memory vs ARGB_8888
                .precision(Precision.INEXACT) // Allow slightly smaller size for memory savings
                .allowHardware(false) // Software bitmaps can be recycled
                .memoryCachePolicy(CachePolicy.ENABLED)
                .diskCachePolicy(CachePolicy.ENABLED)
                .memoryCacheKey("thumb_${asset.id}")
                .diskCacheKey("thumb_${asset.id}_${asset.dateAdded}") // Time-based cache key
                .crossfade(150) // Fast crossfade
                .build(),
            contentDescription = asset.displayName,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
            loading = {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    )
                }
            },
            error = {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.errorContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Photo,
                        contentDescription = "Error",
                        tint = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.5f)
                    )
                }
            }
        )

        // Selection overlay
        if (isSelected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.3f))
            )

            // Selection number badge
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(24.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primary,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (selectionIndex != null) {
                    Text(
                        text = selectionIndex.toString(),
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyGalleryContent(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Photo,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "No photos found",
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Take some photos to get started",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun PermissionDeniedContent(
    onRetryClick: () -> Unit,
    onBackClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Photo,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Photo Access Required",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "To create videos from your photos, we need permission to access your photo library.",
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = onRetryClick,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Allow Access", modifier = Modifier.padding(vertical = 8.dp))
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onBackClick,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Go Back", modifier = Modifier.padding(vertical = 8.dp))
            }
        }
    }
}
