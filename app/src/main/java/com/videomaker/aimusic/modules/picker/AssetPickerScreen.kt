package com.videomaker.aimusic.modules.picker

import android.app.Activity
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File
import androidx.activity.compose.BackHandler
import co.alcheclub.lib.acccore.ads.loader.AdsLoaderService
import com.videomaker.aimusic.core.ads.InterstitialAdHelperExt
import com.videomaker.aimusic.core.constants.AdPlacement
import org.koin.compose.koinInject
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.paint
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.videomaker.aimusic.R
import com.videomaker.aimusic.core.analytics.Analytics
import com.videomaker.aimusic.core.analytics.AnalyticsEvent
import coil.compose.SubcomposeAsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Precision
import coil.size.Size
import com.videomaker.aimusic.ui.components.ModifierExtension.clickableSingle
import com.videomaker.aimusic.ui.theme.FoundationBlack_100
import com.videomaker.aimusic.ui.theme.PlayerCardBackground
import com.videomaker.aimusic.ui.theme.Primary
import com.videomaker.aimusic.ui.theme.TextPrimaryDark
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged

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

private fun readPermissionSnapshot(context: android.content.Context): PermissionSnapshot {
    val hasFullPermission = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_MEDIA_IMAGES
            ) == PackageManager.PERMISSION_GRANTED
        else ->
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
    }
    val hasLimitedPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
        ) == PackageManager.PERMISSION_GRANTED

    return PermissionSnapshot(
        fullGranted = hasFullPermission,
        limitedGranted = hasLimitedPermission
    )
}

/**
 * AssetPickerScreen - Bottom sheet image picker with permission handling
 *
 * Permission states:
 * - Full access (READ_MEDIA_IMAGES / READ_EXTERNAL_STORAGE) → AllPermission: loads entire gallery
 * - Limited access (READ_MEDIA_VISUAL_USER_SELECTED, Android 14+) → LimitPermission: loads selected photos only
 * - Denied → DeniedPermission: shows "Go to Settings" prompt inside the sheet
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
    onNavigateToEditorWithData: (com.videomaker.aimusic.domain.model.EditorInitialData) -> Unit = {},
    onNavigateBack: () -> Unit,
    onAssetsAdded: () -> Unit = {},
    onNavigateToTemplatePreviewer: (templateId: String, imageUris: List<String>, overrideSongId: Long) -> Unit = { _, _, _ -> }
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Get dependencies for ad showing
    val activity = context as? Activity
    val adsLoaderService = koinInject<AdsLoaderService>()

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val gridScrollState by viewModel.gridScrollState.collectAsStateWithLifecycle()
    var hasInitializedPermissionCheck by remember { mutableStateOf(false) }
    var showExitConfirmDialog by remember { mutableStateOf(false) }
    var hasTrackedMediaRender by remember { mutableStateOf(false) }
    var pendingPermissionCheckAfterSettings by remember { mutableStateOf(false) }

    // Show bottom sheet immediately for smooth transition
    var showBottomSheet by remember { mutableStateOf(true) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Permissions to request, based on Android version:
    // - API 34+: request both full and limited so the system dialog shows all 3 options
    // - API 33: request full images permission
    // - API < 33: request external storage
    val permissionsToRequest = remember {
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE ->
                arrayOf(
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
                )
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
                arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
            else ->
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    // Permission launcher - handles full, limited, and denied results
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val fullGranted = result[Manifest.permission.READ_MEDIA_IMAGES] == true ||
            result[Manifest.permission.READ_EXTERNAL_STORAGE] == true
        val limitedGranted = result[Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED] == true
        val allow = fullGranted || limitedGranted
        Analytics.trackPermissionClick(
            button = if (allow) {
                AnalyticsEvent.Value.Option.ALLOW
            } else {
                AnalyticsEvent.Value.Option.NO_ALLOW
            }
        )
        viewModel.onPermissionSnapshot(
            snapshot = PermissionSnapshot(
                fullGranted = fullGranted,
                limitedGranted = limitedGranted
            ),
            source = PermissionUpdateSource.REQUEST_RESULT
        )
    }

    LaunchedEffect(permissionLauncher) {
        viewModel.permissionRequestEvent.collect {
            Analytics.trackPermissionRender()
            permissionLauncher.launch(permissionsToRequest)
        }
    }

    // Handle navigation events - StateFlow-based (Google recommended pattern)
    val navigationEvent by viewModel.navigationEvent.collectAsStateWithLifecycle()
    LaunchedEffect(navigationEvent) {
        navigationEvent?.let { event ->
            when (event) {
                is AssetPickerNavigationEvent.NavigateBack -> onNavigateBack()

                is AssetPickerNavigationEvent.RequestExitWithAd -> {
                    // Show ad if ready, otherwise navigate immediately (non-blocking)
                    if (event.shouldShowAd && activity != null) {
                        android.util.Log.d("AssetPickerScreen", "📺 Showing exit ad...")

                        InterstitialAdHelperExt.showInterstitial(
                            adsLoaderService = adsLoaderService,
                            activity = activity,
                            placement = AdPlacement.INTERSTITIAL_ASSET_PICKER_EXIT,
                            action = {
                                // Ad closed - navigate
                                android.util.Log.d("AssetPickerScreen", "✅ Exit ad closed - navigating")
                            },
                            onShown = {
                                // Navigate immediately when ad shows (parallel)
                                android.util.Log.d("AssetPickerScreen", "🎬 Exit ad shown - navigating")
                                onNavigateBack()
                            },
                            bypassFrequencyCap = true,  // Exit ads always show
                            showLoadingOverlay = false  // Ad already preloaded
                        )
                    } else {
                        // Ad not ready or no activity - navigate immediately
                        if (!event.shouldShowAd) {
                            android.util.Log.d("AssetPickerScreen", "⚠️ Exit ad not ready - navigating immediately")
                        }
                        onNavigateBack()
                    }
                }

                is AssetPickerNavigationEvent.NavigateToEditor -> onNavigateToEditor(event.projectId)
                is AssetPickerNavigationEvent.NavigateToEditorWithData -> onNavigateToEditorWithData(event.initialData)
                is AssetPickerNavigationEvent.AssetsAdded -> onAssetsAdded()
                is AssetPickerNavigationEvent.NavigateToTemplatePreviewer ->
                    onNavigateToTemplatePreviewer(event.templateId, event.imageUris, event.overrideSongId)
            }
            viewModel.onNavigationHandled()
        }
    }

    LaunchedEffect(uiState) {
        if (!hasTrackedMediaRender && uiState is AssetPickerUiState.WithAssets) {
            Analytics.trackMediaRender()
            hasTrackedMediaRender = true
        }
    }

    // Wait for sheet animation to complete, then check permission and load images
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(SHEET_ANIMATION_DELAY_MS)
        hasInitializedPermissionCheck = true
        viewModel.onPermissionSnapshot(
            snapshot = readPermissionSnapshot(context),
            source = PermissionUpdateSource.INITIAL
        )
    }

    DisposableEffect(lifecycleOwner, hasInitializedPermissionCheck) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && hasInitializedPermissionCheck) {
                val snapshot = readPermissionSnapshot(context)
                if (pendingPermissionCheckAfterSettings) {
                    Analytics.trackPermissionCheck(
                        allow = snapshot.fullGranted || snapshot.limitedGranted
                    )
                    pendingPermissionCheckAfterSettings = false
                }
                viewModel.onPermissionSnapshot(
                    snapshot = snapshot,
                    source = PermissionUpdateSource.RESUME
                )
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Opens app's system settings page (used by DeniedPermission "Go to Settings" button)
    val goToAppSettings = {
        Analytics.trackPermissionGotoSetting()
        pendingPermissionCheckAfterSettings = true
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
        }
        context.startActivity(intent)
    }

    // Photo picker launcher for "Add more photos" in LimitPermission state.
    // Using PickMultipleVisualMedia instead of re-requesting READ_MEDIA_VISUAL_USER_SELECTED
    // because re-requesting an already-granted permission returns silently with no UI on most devices.
    // On Android 14+, photos selected via the system photo picker are automatically added to
    // the READ_MEDIA_VISUAL_USER_SELECTED grant, so the MediaStore query will include them after reload.
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(20)
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult

        uris.forEach { uri ->
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) {
                // Some providers return non-persistable grants; transient read access is still valid.
            }
        }
        viewModel.onPermissionGranted(isLimited = true, forceReload = true)
    }

    val onAddMorePhotos = {
        photoPickerLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    // Camera URI state — created before launching TakePicture so it survives recomposition
    var cameraUri by remember { mutableStateOf<Uri?>(null) }

    // Camera capture launcher — called after permission is confirmed
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            cameraUri?.let { uri -> viewModel.onCameraImageCaptured(uri) }
        }
    }

    // Camera permission launcher
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            cameraUri?.let { cameraLauncher.launch(it) }
        }
    }

    // Creates a temp file URI via FileProvider and launches the camera
    val onCameraClick: () -> Unit = {
        try {
            val photoFile = File(context.cacheDir, "images/camera_${System.currentTimeMillis()}.jpg")
            photoFile.parentFile?.mkdirs()
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                photoFile
            )
            cameraUri = uri
            val hasCameraPermission = ContextCompat.checkSelfPermission(
                context, Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
            if (hasCameraPermission) {
                cameraLauncher.launch(uri)
            } else {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        } catch (e: IllegalArgumentException) {
            // FileProvider configuration error - log and ignore
            android.util.Log.e("AssetPicker", "Failed to create camera URI: ${e.message}")
        }
    }

    val closePickerAndNavigateBack = {
        showBottomSheet = false
        viewModel.onPickerClosed()
        viewModel.navigateBack()
    }

    val requestExit = {
        Analytics.trackExitClick(AnalyticsEvent.Value.Location.MEDIA_SELECT)
        val selectedCount = (uiState as? AssetPickerUiState.WithAssets)
            ?.selectedAssets
            ?.size
            ?: 0
        if (shouldShowExitConfirm(selectedCount)) {
            Analytics.trackExitPopupShow(AnalyticsEvent.Value.Location.MEDIA_SELECT)
            showExitConfirmDialog = true
        } else {
            closePickerAndNavigateBack()
        }
    }

    // Bottom Sheet
    if (showBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = {
                requestExit()
            },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            contentWindowInsets = { WindowInsets(0, 0, 0, 0) }
        ) {
            AssetPickerContent(
                uiState = uiState,
                minSelection = viewModel.minSelection,
                initialGridScrollState = gridScrollState,
                onAlbumSelect = { albumId -> viewModel.selectAlbum(albumId) },
                onAssetClick = { asset -> viewModel.toggleAssetSelection(asset) },
                onGridScrollChanged = { index, offset ->
                    viewModel.onGridScrollChanged(index, offset)
                },
                onConfirmClick = { viewModel.confirmSelection() },
                onClearSelection = {viewModel.clearSelection()},
                onCloseClick = {
                    requestExit()
                },
                onGoToSettings = goToAppSettings,
                onAddMorePhotos = onAddMorePhotos,
                onCameraClick = onCameraClick
            )
        }
    }

    if (showExitConfirmDialog) {
        AlertDialog(
            onDismissRequest = {
                showExitConfirmDialog = false
                Analytics.trackExitContinue(AnalyticsEvent.Value.Location.MEDIA_SELECT)
            },
            title = {
                Text(text = stringResource(R.string.picker_exit_confirm_title))
            },
            text = {
                Text(text = stringResource(R.string.picker_exit_confirm_message))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showExitConfirmDialog = false
                        Analytics.trackExitDiscard(
                            videoId = null,
                            location = AnalyticsEvent.Value.Location.MEDIA_SELECT
                        )
                        closePickerAndNavigateBack()
                    }
                ) {
                    Text(text = stringResource(R.string.picker_exit_confirm))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showExitConfirmDialog = false
                        Analytics.trackExitContinue(AnalyticsEvent.Value.Location.MEDIA_SELECT)
                    }
                ) {
                    Text(text = stringResource(R.string.picker_exit_stay))
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AssetPickerContent(
    uiState: AssetPickerUiState,
    minSelection: Int,
    initialGridScrollState: AssetPickerGridScrollState,
    onAlbumSelect: (String) -> Unit,
    onAssetClick: (GalleryAsset) -> Unit,
    onGridScrollChanged: (Int, Int) -> Unit,
    onConfirmClick: () -> Unit,
    onClearSelection: () -> Unit,
    onCloseClick: () -> Unit,
    onGoToSettings: () -> Unit,
    onAddMorePhotos: () -> Unit,
    onCameraClick: () -> Unit
) {
    // Selection count and confirm button
    val selectedCount = if (uiState is AssetPickerUiState.WithAssets) uiState.selectedAssets.size else 0
    val canConfirm = selectedCount >= minSelection


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
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.close)
                )
            }

            Text(
                text = stringResource(R.string.picker_title),
                style = MaterialTheme.typography.titleLarge
            )

            IconButton(onClick = onCameraClick) {
                Icon(
                    painter = painterResource(R.drawable.ic_camera),
                    contentDescription = stringResource(R.string.close)
                )
            }

        }

        // Album filter chips — only shown when assets are loaded
        if (uiState is AssetPickerUiState.WithAssets.AllPermission && uiState.albums.isNotEmpty()) {
            AlbumFilterChips(
                albums = uiState.albums,
                selectedAlbumId = uiState.selectedAlbumId,
                onAlbumSelect = onAlbumSelect
            )
        }

        // Content based on state
        when (uiState) {
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

            is AssetPickerUiState.WithAssets -> {
                // Limited access banner — shown only for partial permission (Android 14+)
                if (uiState is AssetPickerUiState.WithAssets.LimitPermission) {
                    LimitedAccessBanner(onAddMoreClick = onAddMorePhotos)
                }

                if (uiState.filteredAssets.isEmpty()) {
                    EmptyGalleryContent(modifier = Modifier.weight(1f))
                } else {
                    ImageGrid(
                        assets = uiState.filteredAssets,
                        selectedAssets = uiState.selectedAssets,
                        onAssetClick = onAssetClick,
                        initialScrollState = initialGridScrollState,
                        onScrollChanged = onGridScrollChanged,
                        modifier = Modifier.weight(1f)
                    )
                }

                Column(
                    modifier = Modifier
                        .padding(horizontal = 12.dp, vertical = 14.dp)
                        .fillMaxWidth()
                        .background(Color.White.copy(0.1f), RoundedCornerShape(16.dp)),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {

                        if (uiState.selectedAssets.isEmpty()){
                            Text(
                                text = stringResource(R.string.picker_min_selection, minSelection),
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 14.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .weight(1f)
                            )
                        } else {

                            Icon(
                                painter = painterResource(R.drawable.ic_trash),
                                tint = Color.White,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(24.dp)
                                    .clickableSingle {
                                        onClearSelection.invoke()
                                    }
                            )

                            Text(
                                text = stringResource(R.string.picker_current_selection, uiState.selectedAssets.size),
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 14.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .weight(1f)
                            )
                        }

                        Button(
                            onClick = onConfirmClick,
                            enabled = canConfirm,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                            ),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text(
                                stringResource(
                                    R.string.picker_done,
                                    uiState.selectedAssets.size,
                                    AssetPickerViewModel.MAX_SELECTION
                                )
                            )
                        }
                    }

                    Spacer(
                        Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(PlayerCardBackground)
                    )

                    val rowState = rememberLazyListState()
                    LaunchedEffect(uiState.selectedAssets.size) {
                        if (uiState.selectedAssets.isNotEmpty()) {
                            rowState.animateScrollToItem(uiState.selectedAssets.size - 1)
                        }
                    }

                    LazyRow(
                        state = rowState,
                        modifier = Modifier
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 12.dp)
                    ) {

                        items(
                            items = uiState.selectedAssets,
                            key = { it.id }
                        ) { asset ->
                            ImageGridChooseItem(
                                asset
                            ){
                                onAssetClick.invoke(asset)
                            }
                        }

                        items(maxOf(0, minSelection - uiState.selectedAssets.size)){
                            Image(
                                painter = painterResource(R.drawable.img_out_line),
                                contentDescription = null,
                                modifier = Modifier
                                    .size(80.dp)
                            )
                        }
                    }
                }
            }


            is AssetPickerUiState.Initial -> {
                // Show loading while checking permissions (don't show denied state yet)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            AssetPickerUiState.DeniedPermission -> {
                PermissionDeniedContent(
                    onGoToSettings = onGoToSettings
                )
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
                        style = MaterialTheme.typography.labelMedium
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
private fun LimitedAccessBanner(
    onAddMoreClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .padding(8.dp)
            .fillMaxWidth()
            .background(Color.White.copy(0.1f), RoundedCornerShape(8.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = stringResource(R.string.picker_limited_access_message),
            style = MaterialTheme.typography.bodySmall.copy(
                fontWeight = FontWeight.W400,
                fontSize = 14.sp
            ),
            color = TextPrimaryDark,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = stringResource(R.string.picker_add_more_photos),
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.SemiBold,
                color = Primary,
                fontSize = 16.sp
            ),
            modifier = Modifier
                .background(TextPrimaryDark.copy(0.12f), RoundedCornerShape(12.dp))
                .clickableSingle {
                    onAddMoreClick.invoke()
                }
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun ImageGrid(
    assets: List<GalleryAsset>,
    selectedAssets: List<GalleryAsset>,
    onAssetClick: (GalleryAsset) -> Unit,
    initialScrollState: AssetPickerGridScrollState,
    onScrollChanged: (Int, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val gridState = rememberLazyGridState(
        initialFirstVisibleItemIndex = initialScrollState.firstVisibleItemIndex,
        initialFirstVisibleItemScrollOffset = initialScrollState.firstVisibleItemOffset
    )

    LaunchedEffect(gridState) {
        snapshotFlow {
            gridState.firstVisibleItemIndex to gridState.firstVisibleItemScrollOffset
        }
            .distinctUntilChanged()
            .collect { (index, offset) ->
                onScrollChanged(index, offset)
            }
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(GRID_COLUMNS),
        state = gridState,
        contentPadding = PaddingValues(8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier,
        reverseLayout = false
    ) {
        items(
            items = assets,
            key = { it.id }
        ) { asset ->
            val isSelected = selectedAssets.any { it.id == asset.id }

            ImageGridItem(
                asset = asset,
                isSelected = isSelected,
                onClick = { onAssetClick(asset) }
            )
        }
    }
}

@Composable
private fun AssetThumbnail(
    asset: GalleryAsset,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val thumbnailSizePx = with(density) { THUMBNAIL_SIZE_DP.dp.roundToPx() }

    SubcomposeAsyncImage(
        model = ImageRequest.Builder(context)
            .data(asset.uri)
            .size(Size(thumbnailSizePx, thumbnailSizePx))
            .bitmapConfig(Bitmap.Config.RGB_565)
            .precision(Precision.INEXACT)
            .allowHardware(true)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .memoryCacheKey("thumb_${asset.id}")
            .diskCacheKey("thumb_${asset.id}_${asset.dateAdded}")
            .crossfade(100)
            .build(),
        contentDescription = asset.displayName,
        contentScale = ContentScale.Crop,
        modifier = modifier.fillMaxSize(),
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
                    contentDescription = stringResource(R.string.error),
                    tint = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.5f)
                )
            }
        }
    )
}

@Composable
private fun ImageGridItem(
    asset: GalleryAsset,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .clickableSingle(onClick = onClick)
    ) {
        AssetThumbnail(asset = asset)

        if (isSelected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF2C2C2C).copy(alpha = 0.4f))
            )
            Image(
                painter = painterResource(R.drawable.ic_choose_img),
                contentDescription = stringResource(R.string.picker_selected),
                modifier = Modifier
                    .size(40.dp)
                    .align(Alignment.Center)
            )
        }
    }
}

@Composable
private fun ImageGridChooseItem(
    asset: GalleryAsset,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(80.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickableSingle(onClick = onClick)
    ) {
        AssetThumbnail(asset = asset)

        Image(
            painter = painterResource(R.drawable.ic_close_fill),
            contentDescription = stringResource(R.string.picker_selected),
            modifier = Modifier
                .padding(4.dp)
                .size(20.dp)
                .clickableSingle { onClick() }
                .align(Alignment.TopEnd)
        )
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
                text = stringResource(R.string.picker_no_photos),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = stringResource(R.string.picker_no_photos_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun PermissionDeniedContent(
    onGoToSettings: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
    ){
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            contentPadding = PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(3) {
                Box(
                    modifier = Modifier
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFFD9D9D9).copy(0.05f))
                )
            }
            items(3) {
                Box(
                    modifier = Modifier
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color(0xFFD9D9D9).copy(0.04f),
                                    Color(0xFFD9D9D9).copy(0.02f),
                                )
                            )
                        )
                )
            }
            items(3) {
                Box(
                    modifier = Modifier
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color(0xFFD9D9D9).copy(0.01f),
                                    Color.Transparent
                                )
                            )
                        )
                )
            }
        }

        Column(
            modifier = Modifier
                .matchParentSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {

            Text(
                text = stringResource(R.string.picker_permission_title),
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 24.sp
                ),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = stringResource(R.string.picker_permission_message),
                style = MaterialTheme.typography.bodyLarge,
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
                color = FoundationBlack_100
            )

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = onGoToSettings,
                shape = RoundedCornerShape(120.dp)
            ) {
                Text(stringResource(R.string.picker_go_to_settings), modifier = Modifier.padding(vertical = 8.dp))
            }

        }
    }

}
