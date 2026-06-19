package com.videomaker.aimusic.modules.picker

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.alcheclub.lib.acccore.ads.compose.BannerAdView
import co.alcheclub.lib.acccore.ads.compose.NativeAdView
import co.alcheclub.lib.acccore.ads.loader.AdsLoaderService
import coil.compose.SubcomposeAsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Precision
import coil.size.Size
import com.videomaker.aimusic.BuildConfig
import com.videomaker.aimusic.R
import com.videomaker.aimusic.VideoMakerApplication
import com.videomaker.aimusic.ui.components.LocalAsyncImage
import com.videomaker.aimusic.core.ads.AdClickDetector
import com.videomaker.aimusic.core.ads.AdPlacementConfigService
import com.videomaker.aimusic.core.ads.InterstitialAdHelperExt
import com.videomaker.aimusic.core.analytics.Analytics
import com.videomaker.aimusic.core.analytics.AnalyticsEvent
import com.videomaker.aimusic.core.constants.AdPlacement
import com.videomaker.aimusic.core.permission.MediaPermissionCoordinator
import com.videomaker.aimusic.core.rating.RatingTriggerManager
import com.videomaker.aimusic.domain.model.EditorInitialData
import com.videomaker.aimusic.ui.components.ModifierExtension.clickableSingle
import com.videomaker.aimusic.ui.theme.FoundationBlack
import com.videomaker.aimusic.ui.theme.FoundationBlack_100
import com.videomaker.aimusic.ui.theme.FoundationBlack_200
import com.videomaker.aimusic.ui.theme.Neutral_Black
import com.videomaker.aimusic.ui.theme.Neutral_N100
import com.videomaker.aimusic.ui.theme.Neutral_N50
import com.videomaker.aimusic.ui.theme.Neutral_N500
import com.videomaker.aimusic.ui.theme.Neutral_N700
import com.videomaker.aimusic.ui.theme.Neutral_N900
import com.videomaker.aimusic.ui.theme.PickerDialogBackground
import com.videomaker.aimusic.ui.theme.PickerOverlayBackground
import com.videomaker.aimusic.ui.theme.Primary
import com.videomaker.aimusic.ui.theme.SkeletonPlaceholder
import com.videomaker.aimusic.ui.theme.TextPrimary
import com.videomaker.aimusic.ui.theme.TextPrimaryDark
import com.videomaker.aimusic.ui.theme.VideoMakerTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import org.koin.compose.koinInject
import java.io.File

/**
 * Thumbnail size in pixels for image grid
 * Using fixed size for consistent memory usage and faster loading
 */
private const val THUMBNAIL_SIZE_DP = 120
private const val GRID_COLUMNS = 3

/** Selection tray sizing */
private const val TRAY_ITEM_SIZE_DP = 68
private const val CHECK_BUTTON_SIZE_DP = 48

/** Minimum number of tray cells kept visible (selected + dashed placeholders) */
private const val TRAY_VISIBLE_SLOTS = 5

/**
 * Below this many selected photos the bar shows the "Add at least N photos" nudge instead of the
 * "Add N more photos for a ~15s video" hint. Matches the design's "<3 images" vs ">=3 images" states.
 */
private const val PICKER_IDEAL_MESSAGE_MIN_COUNT = 2

/**
 * Delay before loading images to allow initial composition to complete
 * This prevents janky animation when loading images on first render
 */
private const val INITIAL_LOAD_DELAY_MS = 100L

private fun readPermissionSnapshot(context: Context): PermissionSnapshot {
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
 * AssetPickerScreen - Full-screen image picker with permission handling
 *
 * Permission states:
 * - Full access (READ_MEDIA_IMAGES / READ_EXTERNAL_STORAGE) → AllPermission: loads entire gallery
 * - Limited access (READ_MEDIA_VISUAL_USER_SELECTED, Android 14+) → LimitPermission: loads selected photos only
 * - Denied → DeniedPermission: shows "Go to Settings" prompt
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
    onNavigateToEditorWithData: (EditorInitialData) -> Unit = {},
    onNavigateBack: () -> Unit,
    onAssetsAdded: () -> Unit = {},
    onNavigateToTemplatePreviewer: (templateId: String, imageUris: List<String>, overrideSongId: Long) -> Unit = { _, _, _ -> }
) {
    val adClickDetector: AdClickDetector = koinInject()
    val adPlacementConfigService: AdPlacementConfigService = koinInject()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Get dependencies for ad showing
    val activity = context as? Activity
    val adsLoaderService = koinInject<AdsLoaderService>()
    val mediaPermissionCoordinator = koinInject<MediaPermissionCoordinator>()
    val ratingTriggerManager = koinInject<RatingTriggerManager>()

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val durationInfo by viewModel.durationInfo.collectAsStateWithLifecycle()
    val gridScrollState by viewModel.gridScrollState.collectAsStateWithLifecycle()
    var hasInitializedPermissionCheck by remember { mutableStateOf(false) }
    var showExitConfirmDialog by remember { mutableStateOf(false) }
    var hasTrackedMediaRender by remember { mutableStateOf(false) }
    var pendingPermissionCheckAfterSettings by remember { mutableStateOf(false) }
    var showPermissionPromoDialog by remember { mutableStateOf(false) }
    var showPermissionSettingsDialog by remember { mutableStateOf(false) }
    var showPhotosUnavailableDialog by remember { mutableStateOf(false) }
    var hasHandledEntryDeniedPrompt by remember { mutableStateOf(false) }
    var hasHandledEntryLimitedPrompt by remember { mutableStateOf(false) }

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
        val button = when {
            fullGranted -> AnalyticsEvent.Value.Option.ALLOW
            limitedGranted -> AnalyticsEvent.Value.Option.LIMIT_ACCESS
            else -> AnalyticsEvent.Value.Option.NO_ALLOW
        }
        Analytics.trackPermissionClick(
            button = button,
            perType = AnalyticsEvent.Value.PerType.MEDIA,
            popType = AnalyticsEvent.Value.PopType.SYSTEM
        )
        mediaPermissionCoordinator.onSystemPermissionResult(fullGranted = fullGranted)
        val blockedAfterResult = !fullGranted &&
                mediaPermissionCoordinator.isBlockedAfterSecondNonFull()
        showPermissionPromoDialog = false
        hasHandledEntryLimitedPrompt = fullGranted || limitedGranted
        hasHandledEntryDeniedPrompt = true
        showPermissionSettingsDialog = false
        if (!fullGranted && limitedGranted) {
            if (!mediaPermissionCoordinator.hasShownLimitedUpsellThisSession()) {
                mediaPermissionCoordinator.markLimitedUpsellShownInCurrentSession()
                showPhotosUnavailableDialog = true
            }
        }
        viewModel.onPermissionSnapshot(
            snapshot = PermissionSnapshot(
                fullGranted = fullGranted,
                limitedGranted = limitedGranted
            ),
            source = PermissionUpdateSource.REQUEST_RESULT
        )
    }

    val showPermissionGateForMode: (PermissionMode) -> Unit = { mode ->
        val decision = resolveFullPermissionPromptDecision(
            permissionMode = mode,
            blockedAfterSecondAttempt = mediaPermissionCoordinator.isBlockedAfterSecondNonFull(),
            limitedUpsellShownThisSession = mediaPermissionCoordinator.hasShownLimitedUpsellThisSession()
        )
        when (decision) {
            FullPermissionPromptDecision.NONE -> Unit
            FullPermissionPromptDecision.SHOW_PROMO -> {
                showPermissionSettingsDialog = false
                if (mode == PermissionMode.LIMITED) {
                    mediaPermissionCoordinator.markLimitedUpsellShownInCurrentSession()
                    showPermissionPromoDialog = false
                    showPhotosUnavailableDialog = true
                } else {
                    showPhotosUnavailableDialog = false
                    showPermissionPromoDialog = true
                }
            }

            FullPermissionPromptDecision.SHOW_SETTINGS -> {
                showPermissionPromoDialog = false
                showPhotosUnavailableDialog = false
                showPermissionSettingsDialog = true
            }
        }
    }

    LaunchedEffect(showPermissionPromoDialog, showPhotosUnavailableDialog) {
        if (showPermissionPromoDialog || showPhotosUnavailableDialog) {
            Analytics.trackPermissionRender(
                perType = AnalyticsEvent.Value.PerType.MEDIA,
                popType = AnalyticsEvent.Value.PopType.CUSTOM
            )
        }
    }

    // Enforce one popup at a time: the media permission popup takes priority over the
    // global rating popup. While a permission dialog is visible, suppress the rating
    // overlay so it only appears after the permission flow resolves (Permission -> Rating).
    val isPermissionPopupVisible =
        showPermissionPromoDialog || showPermissionSettingsDialog || showPhotosUnavailableDialog
    LaunchedEffect(isPermissionPopupVisible) {
        ratingTriggerManager.setRatingSuppressed(isPermissionPopupVisible)
    }
    DisposableEffect(Unit) {
        onDispose { ratingTriggerManager.setRatingSuppressed(false) }
    }

    // Handle navigation events - Channel pattern (Google official) - one-time delivery, no replay
    LaunchedEffect(Unit) {
        viewModel.navigationEvent.collect { event ->
            when (event) {
                is AssetPickerNavigationEvent.NavigateBack -> onNavigateBack()

                is AssetPickerNavigationEvent.RequestExitWithAd -> {
                    // Show ad if ready, otherwise navigate immediately (non-blocking)
                    if (event.shouldShowAd && activity != null) {
                        Log.d("AssetPickerScreen", "📺 Showing exit ad...")

                        InterstitialAdHelperExt.showInterstitial(
                            adsLoaderService = adsLoaderService,
                            activity = activity,
                            placement = AdPlacement.INTERSTITIAL_ASSET_PICKER_EXIT,
                            action = {
                                // Ad closed OR failed to show - always navigate as fallback
                                // (idempotent if onShown already navigated)
                                Log.d(
                                    "AssetPickerScreen",
                                    "✅ Exit ad closed - navigating"
                                )
                                onNavigateBack()
                            },
                            onShown = {
                                // Navigate immediately when ad shows (parallel)
                                Log.d(
                                    "AssetPickerScreen",
                                    "🎬 Exit ad shown - navigating"
                                )
                                onNavigateBack()
                            },
                            bypassFrequencyCap = true,  // Exit ads always show
                            showLoadingOverlay = false  // Ad already preloaded
                        )
                    } else {
                        // Ad not ready or no activity - navigate immediately
                        if (!event.shouldShowAd) {
                            Log.d(
                                "AssetPickerScreen",
                                "⚠️ Exit ad not ready - navigating immediately"
                            )
                        }
                        onNavigateBack()
                    }
                }

                is AssetPickerNavigationEvent.NavigateToEditor -> onNavigateToEditor(event.projectId)
                is AssetPickerNavigationEvent.NavigateToEditorWithData -> onNavigateToEditorWithData(
                    event.initialData
                )

                is AssetPickerNavigationEvent.AssetsAdded -> onAssetsAdded()
                is AssetPickerNavigationEvent.SelectionConfirmed -> {
                    // Store selection in cache
                    AssetSelectionCache.setSelection(event.selectedUris)

                    // Show interstitial if preloaded (non-blocking), then navigate back
                    if (event.shouldShowAd && activity != null) {
                        InterstitialAdHelperExt.showInterstitial(
                            adsLoaderService = adsLoaderService,
                            activity = activity,
                            placement = AdPlacement.INTERSTITIAL_PICKER_DONE,
                            action = { onNavigateBack() },
                            bypassFrequencyCap = true,
                            showLoadingOverlay = false
                        )
                    } else {
                        onNavigateBack()
                    }
                }

                is AssetPickerNavigationEvent.NavigateToTemplatePreviewer ->
                    onNavigateToTemplatePreviewer(
                        event.templateId,
                        event.imageUris,
                        event.overrideSongId
                    )
            }
            // Event auto-consumed by Channel - no manual cleanup needed
        }
    }

    LaunchedEffect(uiState) {
        if (!hasTrackedMediaRender && uiState is AssetPickerUiState.WithAssets) {
            Analytics.trackMediaRender()
            hasTrackedMediaRender = true
        }
    }

    // Wait for initial composition to complete, then check permission and load images
    LaunchedEffect(Unit) {
        delay(INITIAL_LOAD_DELAY_MS)
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
                        allow = snapshot.fullGranted
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

    val openAppSettings = {
        Analytics.trackPermissionGotoSetting()
        pendingPermissionCheckAfterSettings = true
        VideoMakerApplication.suppressAoa.set(true)
        runCatching {
            context.startActivity(
                mediaPermissionCoordinator.buildOpenAppSettingsIntent(context)
            )
        }.onFailure {
            pendingPermissionCheckAfterSettings = false
        }
        showPermissionSettingsDialog = false
    }

    val onGiveAccess = {
        Analytics.trackPermissionClick(
            button = AnalyticsEvent.Value.Option.ALLOW,
            perType = AnalyticsEvent.Value.PerType.MEDIA,
            popType = AnalyticsEvent.Value.PopType.CUSTOM
        )
        if (mediaPermissionCoordinator.canRequestSystemPermission(context)) {
            showPermissionPromoDialog = false
            showPhotosUnavailableDialog = false
            Analytics.trackPermissionRender(
                perType = AnalyticsEvent.Value.PerType.MEDIA,
                popType = AnalyticsEvent.Value.PopType.SYSTEM
            )
            VideoMakerApplication.suppressAoa.set(true)
            permissionLauncher.launch(permissionsToRequest)
        } else {
            showPermissionPromoDialog = false
            showPhotosUnavailableDialog = false
            showPermissionSettingsDialog = true
        }
    }

    val onNotNow = {
        Analytics.trackPermissionClick(
            button = AnalyticsEvent.Value.Option.NO_ALLOW,
            perType = AnalyticsEvent.Value.PerType.MEDIA,
            popType = AnalyticsEvent.Value.PopType.CUSTOM
        )
        showPermissionPromoDialog = false
        showPhotosUnavailableDialog = false
    }

    LaunchedEffect(uiState, hasInitializedPermissionCheck) {
        if (!hasInitializedPermissionCheck) return@LaunchedEffect
        when (uiState) {
            AssetPickerUiState.DeniedPermission -> {
                if (!hasHandledEntryDeniedPrompt) {
                    hasHandledEntryDeniedPrompt = true
                    // Only auto-show on entry if no promo was shown yet this session.
                    // Prevents a duplicate prompt when: (a) the user already saw the promo
                    // in a previous picker in the same session, or (b) a stale DENIED cache
                    // entry briefly appears before the real LIMITED state is applied.
                    // The manual "Allow Access" button on the denied screen still works
                    // because it calls showPermissionGateForMode directly (bypasses this guard).
                    if (!mediaPermissionCoordinator.hasShownLimitedUpsellThisSession()) {
                        showPermissionGateForMode(PermissionMode.DENIED)
                    }
                }
            }

            is AssetPickerUiState.WithAssets.LimitPermission -> {
                if (!hasHandledEntryLimitedPrompt) {
                    hasHandledEntryLimitedPrompt = true
                    showPermissionGateForMode(PermissionMode.LIMITED)
                }
            }

            is AssetPickerUiState.WithAssets.AllPermission -> {
                mediaPermissionCoordinator.onFullPermissionGranted()
                showPermissionPromoDialog = false
                showPhotosUnavailableDialog = false
                showPermissionSettingsDialog = false
            }

            else -> Unit
        }
    }

    // Photo picker launcher for "Add more photos" in LimitPermission state.
    // Using PickMultipleVisualMedia instead of re-requesting READ_MEDIA_VISUAL_USER_SELECTED
    // because re-requesting an already-granted permission returns silently with no UI on most devices.
    // On Android 14+, photos selected via the system photo picker are automatically added to
    // the READ_MEDIA_VISUAL_USER_SELECTED grant, so the MediaStore query will include them after reload.
    // Guards against spamming the "Add more photos" CTA. The system photo picker can take
    // longer than the clickableSingle debounce (300ms) to appear, so a second tap could launch
    // a second picker. We disable the CTA the moment we launch and re-enable only when the
    // picker is closed (the result callback fires on close, with or without a selection).
    var isPhotoPickerActive by remember { mutableStateOf(false) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(20)
    ) { uris ->
        // Picker closed → re-enable the CTA regardless of whether the user picked anything.
        isPhotoPickerActive = false
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
        // Ignore further taps while a picker is already open/launching.
        if (!isPhotoPickerActive) {
            isPhotoPickerActive = true
            Analytics.trackPermissionAddImage()
            try {
                VideoMakerApplication.suppressAoa.set(true)
                photoPickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            } catch (e: Exception) {
                // Launch failed (e.g. no picker available) → restore the CTA so it stays usable.
                isPhotoPickerActive = false
                Log.e("AssetPicker", "Failed to launch photo picker: ${e.message}")
            }
        }
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
        Analytics.trackPermissionClick(
            button = if (granted) {
                AnalyticsEvent.Value.Option.ALLOW
            } else {
                AnalyticsEvent.Value.Option.NO_ALLOW
            },
            perType = AnalyticsEvent.Value.PerType.CAMERA,
            popType = AnalyticsEvent.Value.PopType.SYSTEM
        )
        if (granted) {
            cameraUri?.let { uri ->
                try {
                    VideoMakerApplication.suppressAoa.set(true)
                    cameraLauncher.launch(uri)
                } catch (e: ActivityNotFoundException) {
                    Log.e("AssetPicker", "No camera app available: ${e.message}")
                }
            }
        }
    }

    // Creates a temp file URI via FileProvider and launches the camera
    val onCameraClick: () -> Unit = {
        try {
            val photoFile =
                File(context.cacheDir, "images/camera_${System.currentTimeMillis()}.jpg")
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
                try {
                    VideoMakerApplication.suppressAoa.set(true)
                    cameraLauncher.launch(uri)
                } catch (e: ActivityNotFoundException) {
                    Log.e("AssetPicker", "No camera app available: ${e.message}")
                }
            } else {
                Analytics.trackPermissionRender(
                    perType = AnalyticsEvent.Value.PerType.CAMERA,
                    popType = AnalyticsEvent.Value.PopType.SYSTEM
                )
                VideoMakerApplication.suppressAoa.set(true)
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        } catch (e: IllegalArgumentException) {
            // FileProvider configuration error - log and ignore
            Log.e("AssetPicker", "Failed to create camera URI: ${e.message}")
        }
    }

    val closePickerAndNavigateBack = {
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

    // Handle system back button
    BackHandler(enabled = true) {
        requestExit()
    }

    // Full-screen content
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
    ) {
        Box(modifier = Modifier.weight(1f)) {
            AssetPickerContent(
                uiState = uiState,
                minSelection = viewModel.minSelection,
                maxSelection = AssetPickerViewModel.MAX_SELECTION,
                durationText = durationInfo.formatted,
                additionalForIdeal = durationInfo.additionalForIdeal,
                initialGridScrollState = gridScrollState,
                onAlbumSelect = { albumId -> viewModel.selectAlbum(albumId) },
                onAssetClick = { asset -> viewModel.addAssetSelection(asset) },
                onRemoveSelectedAt = { index -> viewModel.removeSelectedAt(index) },
                onGridScrollChanged = { index, offset ->
                    viewModel.onGridScrollChanged(index, offset)
                },
                onConfirmClick = { viewModel.confirmSelection() },
                onCloseClick = {
                    requestExit()
                },
                onRequestFullPermission = {
                    showPermissionGateForMode(PermissionMode.DENIED)
                },
                // Banner "Manage Access" behaves exactly like the limited-permission popup CTA:
                // it checks the system-prompt frequency cap and either launches the system
                // permission prompt (cap available) or shows the settings education screen
                // (cap exhausted) — never a silent no-op.
                onManageAccess = onGiveAccess,
                onAddMorePhotos = onAddMorePhotos,
                addPhotoEnabled = !isPhotoPickerActive,
                onCameraClick = onCameraClick
            )
        }
        // Remote Config toggle: native ad (default) or standard banner
        Box {
            Spacer(Modifier.navigationBarsPadding())
            if (adPlacementConfigService.bannerUseNative) {
                NativeAdView(
                    placement = AdPlacement.NATIVE_ASSET_PICKER_BANNER,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp),
                    isDebug = BuildConfig.DEBUG,
                    onAdClicked = { adClickDetector.onAdClick(it) }
                )
            } else {
                BannerAdView(
                    placement = AdPlacement.BANNER_ASSET_PICKER,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    onAdClicked = { adClickDetector.onAdClick(it) }
                )
            }
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

    if (showPermissionPromoDialog) {
        AssetPickerFullAccessPromoDialog(
            onGiveAccess = onGiveAccess,
            onNotNow = onNotNow
        )
    }

    if (showPhotosUnavailableDialog) {
        AssetPickerPhotosUnavailableDialog(
            onManageAccess = onGiveAccess,
            onKeepLimited = onNotNow
        )
    }

    if (showPermissionSettingsDialog) {
        AssetPickerPermissionSettingsDialog(
            onOpenSettings = openAppSettings,
            onDismiss = { showPermissionSettingsDialog = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AssetPickerContent(
    uiState: AssetPickerUiState,
    minSelection: Int,
    maxSelection: Int,
    durationText: String,
    additionalForIdeal: Int,
    initialGridScrollState: AssetPickerGridScrollState,
    onAlbumSelect: (String) -> Unit,
    onAssetClick: (GalleryAsset) -> Unit,
    onRemoveSelectedAt: (Int) -> Unit,
    onGridScrollChanged: (Int, Int) -> Unit,
    onConfirmClick: () -> Unit,
    onCloseClick: () -> Unit,
    onRequestFullPermission: () -> Unit,
    onManageAccess: () -> Unit,
    onAddMorePhotos: () -> Unit,
    addPhotoEnabled: Boolean = true,
    onCameraClick: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Header — center shows an album dropdown once assets are loaded, otherwise a title
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

            // The album dropdown only makes sense once there are real gallery images to filter.
            // With no permission, or limited permission whose accessible set is still empty,
            // the header keeps the plain "Select your image" title.
            val withAssets = uiState as? AssetPickerUiState.WithAssets
            if (withAssets != null && withAssets.assets.isNotEmpty()) {
                AlbumDropdown(
                    albums = withAssets.albums,
                    selectedAlbumId = withAssets.selectedAlbumId,
                    onAlbumSelect = onAlbumSelect
                )
            } else {
                Text(
                    text = stringResource(R.string.picker_title),
                    style = MaterialTheme.typography.titleLarge
                )
            }

            IconButton(onClick = onCameraClick) {
                Icon(
                    painter = painterResource(R.drawable.ic_camera),
                    contentDescription = stringResource(R.string.close)
                )
            }
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
                val selectedCount = uiState.selectedAssets.size
                val maxReached = selectedCount >= maxSelection
                val isLimited = uiState is AssetPickerUiState.WithAssets.LimitPermission

                // Count of how many times each asset is selected (for the "xN" badge)
                val selectionCountById = remember(uiState.selectedAssets) {
                    uiState.selectedAssets.groupingBy { it.id }.eachCount()
                }

                if (uiState.assets.isEmpty()) {
                    // No accessible photos yet. For limited access this means the user granted
                    // partial access but nothing is visible — reuse the no-permission screen, but
                    // wire its CTA to the system photo picker so they can choose which photos to
                    // expose. The new banner + [+] grid only appears once the list is non-empty.
                    if (isLimited) {
                        PermissionDeniedContent(
                            onRequestPermission = onAddMorePhotos
                        )
                    } else {
                        EmptyGalleryContent(modifier = Modifier.weight(1f))
                    }
                } else {
                    // Limited access banner — shown only for partial permission (Android 14+)
                    if (isLimited) {
                        LimitedAccessBanner(onManageClick = onManageAccess)
                    }

                    if (uiState.filteredAssets.isEmpty() && !isLimited) {
                        EmptyGalleryContent(modifier = Modifier.weight(1f))
                    } else {
                        ImageGrid(
                            assets = uiState.filteredAssets,
                            selectionCountById = selectionCountById,
                            maxReached = maxReached,
                            showAddTile = isLimited,
                            onAddTileClick = onAddMorePhotos,
                            addTileEnabled = addPhotoEnabled,
                            onAssetClick = onAssetClick,
                            initialScrollState = initialGridScrollState,
                            onScrollChanged = onGridScrollChanged,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    PickerSelectionBar(
                        isLimit = isLimited,
                        selectedAssets = uiState.selectedAssets,
                        minSelection = minSelection,
                        maxSelection = maxSelection,
                        durationText = durationText,
                        additionalForIdeal = additionalForIdeal,
                        onRemoveSelectedAt = onRemoveSelectedAt,
                        onConfirmClick = onConfirmClick
                    )
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
                    onRequestPermission = onRequestFullPermission
                )
            }
        }
    }
}

/**
 * Bottom selection bar: estimated duration + dynamic hint message on top,
 * a horizontally scrollable tray of selected photos (with remove buttons) and
 * the confirm (check) button below. The confirm button is disabled until
 * [minSelection] photos are selected.
 */
@Composable
private fun PickerSelectionBar(
    isLimit: Boolean,
    selectedAssets: List<GalleryAsset>,
    minSelection: Int,
    maxSelection: Int,
    durationText: String,
    additionalForIdeal: Int,
    onRemoveSelectedAt: (Int) -> Unit,
    onConfirmClick: () -> Unit
) {
    val selectedCount = selectedAssets.size
    val canConfirm = selectedCount >= minSelection
    val maxReached = selectedCount >= maxSelection

    val message: String = when {
        maxReached -> stringResource(R.string.picker_max_reached, maxSelection)
        selectedCount < PICKER_IDEAL_MESSAGE_MIN_COUNT ->
            stringResource(R.string.picker_min_selection, minSelection)
        additionalForIdeal > 0 -> stringResource(R.string.picker_add_more_ideal, additionalForIdeal)
        else -> stringResource(R.string.picker_more_photos_longer, maxSelection)
    }
    val messageColor = if (maxReached) Color(0xFFEAA235) else Neutral_N50

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Neutral_Black)
            .padding(top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (isLimit.not()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 14.sp),
                    color = messageColor,
                    modifier = Modifier.weight(1f)
                )
                DurationPill(text = durationText)
            }
        }

        Box(
            modifier = Modifier.fillMaxWidth(),
        ) {
            val rowState = rememberLazyListState()
            LaunchedEffect(selectedCount) {
                if (selectedCount > 0) {
                    rowState.animateScrollToItem(selectedCount - 1)
                }
            }

            LazyRow(
                state = rowState,
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 4.dp, horizontal = 12.dp)
            ) {
                // Selected photos — index-keyed because the same photo can appear multiple times
                itemsIndexed(
                    items = selectedAssets,
                    key = { index, asset -> "${asset.id}_$index" }
                ) { index, asset ->
                    SelectedTrayItem(
                        asset = asset,
                        onRemove = { onRemoveSelectedAt(index) }
                    )
                }

                // Empty dashed placeholders so remaining slots stay visible
                val emptySlots = maxOf(0, TRAY_VISIBLE_SLOTS - selectedCount)
                items(emptySlots) {
                    Image(
                        painter = painterResource(R.drawable.img_out_line),
                        contentDescription = null,
                        modifier = Modifier.size(TRAY_ITEM_SIZE_DP.dp)
                    )
                }

                if (selectedAssets.size > 4) {
                    item {
                        Spacer(Modifier.width(TRAY_ITEM_SIZE_DP.dp))
                    }
                }
            }

            Box(
                modifier = Modifier
                    .height(TRAY_ITEM_SIZE_DP.dp)
                    .align(Alignment.CenterEnd)
                    .background(
                        brush = Brush.horizontalGradient(
                            colorStops = arrayOf(
                                0.0f to Color.Transparent,
                                0.2f to Color(0xCC333333),
                                0.5f to Color(0xFF333333),
                                1.0f to Color(0xFF333333)
                            )
                        )
                    )
                    .padding(start = 12.dp, end = 20.dp),
                contentAlignment = Alignment.Center
            ){
                ConfirmCheckButton(
                    enabled = canConfirm,
                    onClick = onConfirmClick
                )
            }
        }

        if (isLimit) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 14.sp),
                    color = messageColor,
                    modifier = Modifier.weight(1f)
                )
                DurationPill(text = durationText)
            }
        }
    }
}

@Composable
private fun DurationPill(text: String) {
    Box(
        modifier = Modifier
            .background(Color.White.copy(0.08f), RoundedCornerShape(160.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = FontWeight.W500,
                fontSize = 16.sp
            ),
            color = Neutral_N50
        )
    }
}

@Composable
private fun ConfirmCheckButton(
    enabled: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(CHECK_BUTTON_SIZE_DP.dp)
            .clip(CircleShape)
            .background(if (enabled) Primary else Primary.copy(alpha = 0.35f))
            .clickableSingle(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Check,
            contentDescription = stringResource(R.string.picker_selected),
            tint = FoundationBlack,
            modifier = Modifier.size(24.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlbumDropdown(
    albums: List<AlbumFilter>,
    selectedAlbumId: String,
    onAlbumSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = albums.find { it.id == selectedAlbumId }?.displayName
        ?: albums.firstOrNull()?.displayName.orEmpty()

    // With a single album there is nothing to switch to — show plain text, no chevron, no menu.
    val hasMultipleAlbums = albums.size > 1

    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .then(if (hasMultipleAlbums) Modifier.clickableSingle { expanded = true } else Modifier)
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(
                text = selectedName,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            if (hasMultipleAlbums) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = null
                )
            }
        }

        DropdownMenu(
            expanded = expanded && hasMultipleAlbums,
            onDismissRequest = { expanded = false }
        ) {
            albums.forEach { album ->
                DropdownMenuItem(
                    text = { Text("${album.displayName} (${album.count})") },
                    onClick = {
                        onAlbumSelect(album.id)
                        expanded = false
                    },
                    trailingIcon = {
                        if (album.id == selectedAlbumId) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = Primary
                            )
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun LimitedAccessBanner(
    onManageClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .padding(8.dp)
            .fillMaxWidth()
            .background(Color.White.copy(0.1f), RoundedCornerShape(8.dp))
            .padding(vertical = 16.dp, horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "You're allowing limited access.\nGrant full access to see all memories",
            style = MaterialTheme.typography.bodySmall.copy(
                fontWeight = FontWeight.W400,
                fontSize = 13.sp
            ),
            color = TextPrimaryDark,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "Manage",
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.W500,
                color = Primary,
                fontSize = 16.sp
            ),
            modifier = Modifier
                .background(TextPrimaryDark.copy(0.12f), RoundedCornerShape(12.dp))
                .clickableSingle {
                    onManageClick.invoke()
                }
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun ImageGrid(
    assets: List<GalleryAsset>,
    selectionCountById: Map<Long, Int>,
    maxReached: Boolean,
    showAddTile: Boolean,
    onAddTileClick: () -> Unit,
    addTileEnabled: Boolean = true,
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
        // "Add more photos" tile — only in limited-access mode
        if (showAddTile) {
            item(key = "add_more_tile") {
                AddPhotoTile(
                    enabled = addTileEnabled,
                    onClick = onAddTileClick
                )
            }
        }

        items(
            items = assets,
            key = { it.id }
        ) { asset ->
            ImageGridItem(
                asset = asset,
                selectionCount = selectionCountById[asset.id] ?: 0,
                maxReached = maxReached,
                onClick = { onAssetClick(asset) }
            )
        }
    }
}

@Composable
private fun AddPhotoTile(
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(Neutral_N900)
            .clickableSingle(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_circle_plus_v2),
            contentDescription = stringResource(R.string.picker_add_photos),
            tint = TextPrimaryDark,
            modifier = Modifier.size(32.dp)
        )
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
    selectionCount: Int,
    maxReached: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            // Disable adding once the maximum is reached
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                enabled = !maxReached,
                onClick = onClick)
    ) {
        AssetThumbnail(asset = asset)

        // "xN" count badge — clearly shows the photo is selected and how many times
        if (selectionCount > 0) {
            CountBadge(
                count = selectionCount,
                modifier = Modifier
                    .padding(top = 4.dp, end = 6.dp)
                    .align(Alignment.TopEnd)
            )
        }

        // Dim the whole grid when the maximum has been reached
        if (maxReached) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(PickerOverlayBackground.copy(alpha = 0.6f))
            )
        }
    }
}

@Composable
private fun CountBadge(
    count: Int,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(Primary, RoundedCornerShape(6.dp))
            .border(1.dp, Color.Black.copy(0.12f), RoundedCornerShape(6.dp))
            .padding(horizontal = 4.dp, vertical = 3.dp)
    ) {
        Text(
            text = stringResource(R.string.picker_count_badge, count),
            color = Neutral_N100,
            fontWeight = FontWeight.W600,
            lineHeight = 14.sp,
            fontSize = 11.sp
        )
    }
}

@Composable
private fun SelectedTrayItem(
    asset: GalleryAsset,
    onRemove: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(TRAY_ITEM_SIZE_DP.dp)
            .clip(RoundedCornerShape(8.dp))
    ) {
        AssetThumbnail(asset = asset)

        Image(
            painter = painterResource(R.drawable.ic_close_fill),
            contentDescription = stringResource(R.string.picker_selected),
            modifier = Modifier
                .padding(4.dp)
                .size(20.dp)
                .clickableSingle { onRemove() }
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
private fun AssetPickerFullAccessPromoDialog(
    onGiveAccess: () -> Unit,
    onNotNow: () -> Unit
) {
    Dialog(
        onDismissRequest = {
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .clickableSingle {
                }
                .fillMaxSize()
                .padding(top = 106.dp, start = 16.dp, end = 16.dp)
        ) {

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(PickerDialogBackground, RoundedCornerShape(16.dp)),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(12.dp))
                LocalAsyncImage(
                    resId = R.drawable.img_popup_asset_permission,
                    contentDescription = null,
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier
                        .width(100.dp)
                        .height(100.dp)
                )
                Spacer(Modifier.height(24.dp))
                Text(
                    text = stringResource(R.string.picker_give_access_permission),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.W600,
                    fontSize = 24.sp,
                    color = TextPrimary,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.picker_full_access_custom_message),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.W500,
                    fontSize = 15.sp,
                    color = FoundationBlack_200,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth()
                )
                Spacer(Modifier.height(32.dp))

                Row(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth()
                        .background(Primary, RoundedCornerShape(160.dp))
                        .clickableSingle {
                            onGiveAccess.invoke()
                        }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = stringResource(R.string.picker_give_access),
                        fontWeight = FontWeight.W600,
                        fontSize = 16.sp,
                        color = FoundationBlack,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.picker_not_now),
                    fontWeight = FontWeight.W600,
                    fontSize = 16.sp,
                    color = Neutral_N500,
                    modifier = Modifier
                        .clickableSingle {
                            onNotNow.invoke()
                        }
                        .padding(16.dp)
                )
                Spacer(Modifier.height(20.dp))
            }

        }
    }
}

@Composable
private fun AssetPickerPhotosUnavailableDialog(
    onManageAccess: () -> Unit,
    onKeepLimited: () -> Unit
) {
    LaunchedEffect(Unit) {
        Analytics.track(AnalyticsEvent.PERMISSION_WARNING_LIMITED)
    }

    Dialog(
        onDismissRequest = {
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .clickableSingle {
                }
                .fillMaxSize()
                .padding(top = 106.dp, start = 16.dp, end = 16.dp)
        ) {

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(PickerDialogBackground, RoundedCornerShape(24.dp)),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(24.dp))
                LocalAsyncImage(
                    resId = R.drawable.img_photo_unavailable,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .aspectRatio(1.5f)
                )
                Spacer(Modifier.height(24.dp))
                Text(
                    text = stringResource(R.string.picker_photos_unavailable_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.W700,
                    fontSize = 24.sp,
                    color = TextPrimary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.picker_photos_unavailable_subtitle),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.W400,
                    fontSize = 15.sp,
                    color = FoundationBlack_200,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .padding(horizontal = 24.dp)
                        .fillMaxWidth()
                )
                Spacer(Modifier.height(32.dp))

                Row(
                    modifier = Modifier
                        .padding(horizontal = 24.dp)
                        .fillMaxWidth()
                        .background(Primary, RoundedCornerShape(160.dp))
                        .clickableSingle {
                            Analytics.track(AnalyticsEvent.PERMISSION_WARNING_ALLOWBTN)
                            onManageAccess.invoke()
                        }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = stringResource(R.string.picker_manage_access),
                        fontWeight = FontWeight.W600,
                        fontSize = 16.sp,
                        color = FoundationBlack,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.picker_keep_limited_access),
                    fontWeight = FontWeight.W600,
                    fontSize = 16.sp,
                    color = Neutral_N500,
                    modifier = Modifier
                        .clickableSingle {
                            Analytics.track(AnalyticsEvent.PERMISSION_WARNING_DENYBTN)
                            onKeepLimited.invoke()
                        }
                        .padding(16.dp)
                )
                Spacer(Modifier.height(20.dp))
            }

        }
    }
}

@Composable
private fun AssetPickerPermissionSettingsDialog(
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = {
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .clickableSingle {
                }
                .fillMaxSize()
                .padding(top = 106.dp, start = 16.dp, end = 16.dp)
        ) {

            Column(
                modifier = Modifier


                    .fillMaxWidth()
                    .background(PickerDialogBackground, RoundedCornerShape(16.dp)),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                Image(
                    painter = painterResource(R.drawable.img_popup_asset_denied),
                    contentDescription = null,
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier
                        .fillMaxWidth()
                )
                Spacer(Modifier.height(24.dp))
                Text(
                    text = stringResource(R.string.picker_give_access_settings),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.W600,
                    fontSize = 24.sp,
                    color = TextPrimary,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.picker_full_access_settings_message),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.W500,
                    fontSize = 15.sp,
                    color = FoundationBlack_200,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth()
                )
                Spacer(Modifier.height(32.dp))

                Row(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth()
                        .background(Primary, RoundedCornerShape(160.dp))
                        .clickableSingle {
                            onOpenSettings.invoke()
                        }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = stringResource(R.string.notification_permission_open_settings),
                        fontWeight = FontWeight.W600,
                        fontSize = 16.sp,
                        color = FoundationBlack,
                    )
                }
                Spacer(Modifier.height(20.dp))
            }

            Icon(
                painter = painterResource(R.drawable.ic_close_circle),
                contentDescription = null,
                tint = Neutral_N700,
                modifier = Modifier
                    .padding(12.dp)
                    .size(28.dp)
                    .background(Color.Black.copy(0.2f), CircleShape)
                    .clickableSingle {
                        onDismiss.invoke()
                    }
                    .padding(4.dp)
                    .align(Alignment.TopStart)
            )
        }
    }
}

@Composable
private fun PermissionDeniedContent(
    onRequestPermission: () -> Unit
) {
    LaunchedEffect(Unit) {
        Analytics.track(AnalyticsEvent.PERMISSION_NO_ALLOW)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
    ) {
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
                        .background(SkeletonPlaceholder.copy(0.05f))
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
                                    SkeletonPlaceholder.copy(0.04f),
                                    SkeletonPlaceholder.copy(0.02f),
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
                                    SkeletonPlaceholder.copy(0.01f),
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
                text = stringResource(R.string.picker_permission_title_1),
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 24.sp
                ),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = stringResource(R.string.picker_permission_message_1),
                style = MaterialTheme.typography.bodyLarge,
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
                color = FoundationBlack_100
            )

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = {
                    Analytics.track(AnalyticsEvent.PERMISSION_NOALLOW_CLICKBTN)
                    onRequestPermission()
                },
                shape = RoundedCornerShape(120.dp)
            ) {
                Text(
                    stringResource(R.string.picker_allow_access_1),
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

        }
    }
}

@Preview(showBackground = true)
@Composable
private fun AssetPickerContentLoadingPreview() {
    VideoMakerTheme {
        AssetPickerContent(
            uiState = AssetPickerUiState.Loading,
            minSelection = 1,
            initialGridScrollState = AssetPickerGridScrollState(),
            onAlbumSelect = {},
            onAssetClick = {},
            onGridScrollChanged = { _, _ -> },
            onConfirmClick = {},
            onCloseClick = {},
            onRequestFullPermission = {},
            onAddMorePhotos = {},
            maxSelection = 1,
            durationText = "",
            additionalForIdeal = 1,
            onRemoveSelectedAt = {},
            onManageAccess = {},
            onCameraClick = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun AssetPickerContentDeniedPreview() {
    VideoMakerTheme {
        AssetPickerContent(
            uiState = AssetPickerUiState.DeniedPermission,
            minSelection = 1,
            initialGridScrollState = AssetPickerGridScrollState(),
            onAlbumSelect = {},
            onAssetClick = {},
            onGridScrollChanged = { _, _ -> },
            onConfirmClick = {},
            onCloseClick = {},
            onRequestFullPermission = {},
            onAddMorePhotos = {},
            maxSelection = 1,
            durationText = "",
            additionalForIdeal = 1,
            onRemoveSelectedAt = {},
            onManageAccess = {},
            onCameraClick = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun AssetPickerContentAllPermissionPreview() {
    val mockAssets = remember {
        (1..12).map { id ->
            val bucket = if (id <= 8) "Camera" else "Screenshots"
            GalleryAsset(
                id = id.toLong(),
                uri = Uri.EMPTY,
                displayName = "IMG_$id.jpg",
                dateAdded = System.currentTimeMillis() / 1000 - id * 3600,
                width = 1080,
                height = 1080,
                bucketId = if (bucket == "Camera") 1L else 2L,
                bucketName = bucket
            )
        }
    }
    val mockAlbums = listOf(
        AlbumFilter(id = AlbumFilterType.ALL, displayName = "All", count = 12),
        AlbumFilter(id = "camera", displayName = "Camera", bucketId = 1L, count = 8),
        AlbumFilter(id = "screenshots", displayName = "Screenshots", bucketId = 2L, count = 4)
    )
    val uiState = AssetPickerUiState.WithAssets.AllPermission(
        assets = mockAssets,
        filteredAssets = mockAssets,
        selectedAssets = emptyList(),
        albums = mockAlbums,
        selectedAlbumId = AlbumFilterType.ALL
    )
    VideoMakerTheme {
        AssetPickerContent(
            uiState = uiState,
            minSelection = 1,
            initialGridScrollState = AssetPickerGridScrollState(),
            onAlbumSelect = {},
            onAssetClick = {},
            onGridScrollChanged = { _, _ -> },
            onConfirmClick = {},
            onCloseClick = {},
            onRequestFullPermission = {},
            onAddMorePhotos = {},
            maxSelection = 1,
            durationText = "",
            additionalForIdeal = 1,
            onRemoveSelectedAt = {},
            onManageAccess = {},
            onCameraClick = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun AssetPickerContentSelectedPreview() {
    val mockAssets = remember {
        (1..12).map { id ->
            val bucket = if (id <= 8) "Camera" else "Screenshots"
            GalleryAsset(
                id = id.toLong(),
                uri = Uri.EMPTY,
                displayName = "IMG_$id.jpg",
                dateAdded = System.currentTimeMillis() / 1000 - id * 3600,
                width = 1080,
                height = 1080,
                bucketId = if (bucket == "Camera") 1L else 2L,
                bucketName = bucket
            )
        }
    }
    val mockAlbums = listOf(
        AlbumFilter(id = AlbumFilterType.ALL, displayName = "All", count = 12),
        AlbumFilter(id = "camera", displayName = "Camera", bucketId = 1L, count = 8),
        AlbumFilter(id = "screenshots", displayName = "Screenshots", bucketId = 2L, count = 4)
    )
    val selectedAssets = listOf(mockAssets[0], mockAssets[1])
    val uiState = AssetPickerUiState.WithAssets.AllPermission(
        assets = mockAssets,
        filteredAssets = mockAssets,
        selectedAssets = selectedAssets,
        albums = mockAlbums,
        selectedAlbumId = AlbumFilterType.ALL
    )
    VideoMakerTheme {
        AssetPickerContent(
            uiState = uiState,
            minSelection = 3,
            initialGridScrollState = AssetPickerGridScrollState(),
            onAlbumSelect = {},
            onAssetClick = {},
            onGridScrollChanged = { _, _ -> },
            onConfirmClick = {},
            onCloseClick = {},
            onRequestFullPermission = {},
            onAddMorePhotos = {},
            maxSelection = 1,
            durationText = "",
            additionalForIdeal = 1,
            onRemoveSelectedAt = {},
            onManageAccess = {},
            onCameraClick = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun AssetPickerContentLimitPermissionPreview() {
    val mockAssets = remember {
        (1..4).map { id ->
            GalleryAsset(
                id = id.toLong(),
                uri = Uri.EMPTY,
                displayName = "IMG_$id.jpg",
                dateAdded = System.currentTimeMillis() / 1000 - id * 3600,
                width = 1080,
                height = 1080,
                bucketId = 1L,
                bucketName = "Camera"
            )
        }
    }
    val uiState = AssetPickerUiState.WithAssets.LimitPermission(
        assets = mockAssets,
        filteredAssets = mockAssets,
        selectedAssets = emptyList(),
        albums = emptyList(),
        selectedAlbumId = AlbumFilterType.ALL
    )
    VideoMakerTheme {
        AssetPickerContent(
            uiState = uiState,
            minSelection = 1,
            initialGridScrollState = AssetPickerGridScrollState(),
            onAlbumSelect = {},
            onAssetClick = {},
            onGridScrollChanged = { _, _ -> },
            onConfirmClick = {},
            onCloseClick = {},
            onRequestFullPermission = {},
            onAddMorePhotos = {},
            maxSelection = 1,
            durationText = "",
            additionalForIdeal = 1,
            onRemoveSelectedAt = {},
            onManageAccess = {},
            onCameraClick = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun AssetPickerPromoDialogPreview() {
    VideoMakerTheme {
        AssetPickerFullAccessPromoDialog(
            onGiveAccess = {},
            onNotNow = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun AssetPickerPhotosUnavailableDialogPreview() {
    VideoMakerTheme {
        AssetPickerPhotosUnavailableDialog(
            onManageAccess = {},
            onKeepLimited = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun AssetPickerSettingsDialogPreview() {
    VideoMakerTheme {
        AssetPickerPermissionSettingsDialog(
            onOpenSettings = {},
            onDismiss = {}
        )
    }
}

