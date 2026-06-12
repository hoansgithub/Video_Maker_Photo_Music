package com.videomaker.aimusic.modules.editor.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.alcheclub.lib.acccore.ads.loader.AdsLoaderService
import com.videomaker.aimusic.R
import com.videomaker.aimusic.core.ads.RewardedAdPresenter
import com.videomaker.aimusic.core.constants.AdPlacement
import com.videomaker.aimusic.domain.model.EffectSet
import com.videomaker.aimusic.domain.model.Transition
import com.videomaker.aimusic.domain.model.TransitionCategory
import com.videomaker.aimusic.modules.editor.DownloadState
import com.videomaker.aimusic.modules.editor.EffectSetUiState
import com.videomaker.aimusic.modules.editor.EffectSetViewModel
import com.videomaker.aimusic.ui.components.AdsLoadingOverlay
import com.videomaker.aimusic.ui.components.AppAsyncImage
import com.videomaker.aimusic.ui.theme.Gray600
import com.videomaker.aimusic.ui.theme.Primary
import com.videomaker.aimusic.ui.theme.SplashBackground
import com.videomaker.aimusic.ui.theme.TextOnPrimary
import com.videomaker.aimusic.ui.theme.TextPrimary
import com.videomaker.aimusic.ui.theme.TextSecondary
import com.videomaker.aimusic.ui.theme.VideoMakerTheme
import org.koin.compose.koinInject

// ============================================
// EFFECT SET PANEL (INLINE)
// ============================================

@Composable
fun EffectSetPanel(
    viewModel: EffectSetViewModel,
    selectedEffectSetId: String?,
    onDismiss: () -> Unit,
    onEffectSetSelected: (EffectSet) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val shouldPresentAd by viewModel.shouldPresentAd.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val unlockedIds by viewModel.unlockedEffectSetIds.collectAsStateWithLifecycle()
    val downloadStates by viewModel.downloadStates.collectAsStateWithLifecycle()
    val activeEffectSetId by viewModel.activeEffectSetId.collectAsStateWithLifecycle()

    val gridState = rememberLazyGridState()
    val snackbarHostState = remember { SnackbarHostState() }
    val adsLoaderService = koinInject<AdsLoaderService>()

    // Synchronize selected ID with view model
    LaunchedEffect(selectedEffectSetId) {
        viewModel.setSelectedEffectSetId(selectedEffectSetId)
    }

    // Handle rewarded ad presentation
    RewardedAdPresenter(
        shouldPresent = shouldPresentAd,
        placement = AdPlacement.REWARD_UNLOCK_EFFECT_SET,
        adsLoaderService = adsLoaderService,
        onRewardEarned = viewModel::onRewardEarned,
        onAdFailed = viewModel::onAdFailed
    )

    // Show error message
    LaunchedEffect(errorMessage) {
        errorMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.onErrorMessageShown()
        }
    }

    // Detect pagination trigger
    LaunchedEffect(gridState) {
        val threshold = 8
        snapshotFlow { gridState.layoutInfo }
            .collect { layoutInfo ->
                val lastVisibleIndex =
                    layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: return@collect
                val totalItems = layoutInfo.totalItemsCount
                if (totalItems > 0 && lastVisibleIndex >= totalItems - threshold) {
                    viewModel.loadNextPage()
                }
            }
    }

    EffectSetPanelContent(
        uiState = uiState,
        selectedId = activeEffectSetId ?: selectedEffectSetId,
        unlockedIds = unlockedIds,
        downloadStates = downloadStates,
        gridState = gridState,
        snackbarHostState = snackbarHostState,
        onDismiss = onDismiss,
        onEffectSetClick = { effectSet ->
            viewModel.onEffectSetClick(
                effectSet = effectSet,
                onUnlockedEffectSetSelected = {
                    onEffectSetSelected(effectSet)
                    viewModel.startDownload(effectSet.id)
                }
            )
        },
        onRetry = { viewModel.onRetry() },
        modifier = modifier
    )
}

// ============================================
// EFFECT SET PANEL CONTENT (STATELESS)
// ============================================

@Composable
fun EffectSetPanelContent(
    uiState: EffectSetUiState,
    selectedId: String?,
    unlockedIds: Set<String>,
    downloadStates: Map<String, DownloadState>,
    gridState: LazyGridState,
    snackbarHostState: SnackbarHostState,
    onDismiss: () -> Unit,
    onEffectSetClick: (EffectSet) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(SplashBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp, top = 8.dp)
        ) {
            // Drag handle top indicator
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(vertical = 8.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Gray600)
            )

            // Header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left Cancel button
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_close),
                        contentDescription = stringResource(R.string.close),
                        tint = TextPrimary,
                        modifier = Modifier.size(21.dp)
                    )
                }

                // Center Title
                Text(
                    text = stringResource(R.string.editor_effect_sets),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )

                // Right Confirm/Checkmark button
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Primary)
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = stringResource(R.string.dialog_ok),
                        tint = TextOnPrimary,
                        modifier = Modifier.size(21.dp)
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Content based on state
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                when (uiState) {
                    is EffectSetUiState.Loading -> {
                        LoadingContent()
                    }

                    is EffectSetUiState.Success -> {
                        EffectSetGrid(
                            effectSets = uiState.effectSets,
                            selectedId = selectedId,
                            unlockedIds = unlockedIds,
                            downloadStates = downloadStates,
                            isLoadingMore = uiState.isLoadingMore,
                            gridState = gridState,
                            onEffectSetClick = onEffectSetClick
                        )
                    }

                    is EffectSetUiState.Error -> {
                        ErrorContent(
                            message = uiState.message,
                            onRetry = onRetry
                        )
                    }
                }
            }

            // Snackbar for error messages
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.padding(8.dp)
            )
        }

        // Ads loading overlay
        AdsLoadingOverlay()
    }
}

// ============================================
// GRID CONTENT
// ============================================

@Composable
private fun EffectSetGrid(
    effectSets: List<EffectSet>,
    selectedId: String?,
    unlockedIds: Set<String>,
    downloadStates: Map<String, DownloadState>,
    isLoadingMore: Boolean,
    gridState: LazyGridState,
    onEffectSetClick: (EffectSet) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        state = gridState,
        contentPadding = PaddingValues(bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        items(
            items = effectSets,
            key = { it.id },
            contentType = { "effect_set" }
        ) { effectSet ->
            val isLocked = effectSet.isPremium && !unlockedIds.contains(effectSet.id)
            val downloadState = downloadStates[effectSet.id] ?: DownloadState.NotDownloaded
            EffectSetCard(
                effectSet = effectSet,
                isSelected = effectSet.id == selectedId,
                isLocked = isLocked,
                downloadState = downloadState,
                onClick = { onEffectSetClick(effectSet) }
            )
        }

        // Loading footer
        if (isLoadingMore) {
            item(
                span = { androidx.compose.foundation.lazy.grid.GridItemSpan(4) }
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(32.dp),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 3.dp
                    )
                }
            }
        }
    }
}

// ============================================
// EFFECT SET CARD WITH DOWNLOAD SUPPORT
// ============================================

@Composable
private fun EffectSetCard(
    effectSet: EffectSet,
    isSelected: Boolean,
    isLocked: Boolean,
    downloadState: DownloadState,
    onClick: () -> Unit
) {
    val isAutoplay = downloadState is DownloadState.Downloaded
    val scale = if (isAutoplay) {
        val infiniteTransition = rememberInfiniteTransition(label = "autoplay")
        infiniteTransition.animateFloat(
            initialValue = 1.0f,
            targetValue = 1.06f,
            animationSpec = infiniteRepeatable(
                animation = tween(1200, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "scale"
        ).value
    } else {
        1.0f
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Gray600.copy(alpha = 0.3f))
            .then(
                if (isSelected) {
                    Modifier.border(
                        width = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(12.dp)
                    )
                } else if (isAutoplay) {
                    Modifier.border(
                        width = 1.5.dp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(12.dp)
                    )
                } else {
                    Modifier
                }
            )
            .clickable(onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
        ) {
            AppAsyncImage(
                imageUrl = effectSet.thumbnailUrl,
                contentDescription = effectSet.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(8.dp))
            )

            // Right-bottom indicator badge
            val showNewItem = effectSet.transitions.any { it.isPremium } && downloadState is DownloadState.NotDownloaded
            val showAds = !showNewItem && effectSet.isPremium && isLocked
            val showDownload =
                !showNewItem && !showAds && downloadState is DownloadState.NotDownloaded && !isLocked

            if (showNewItem) {
                Image(
                    painter = painterResource(id = R.drawable.ic_new_item),
                    contentDescription = null,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .size(18.dp)
                )
            } else if (showAds) {
                Image(
                    painter = painterResource(id = R.drawable.ic_ads),
                    contentDescription = null,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .size(18.dp)
                )
            } else if (showDownload) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_download),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .size(18.dp)
                )
            }

            if (downloadState is DownloadState.Downloading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.4f))
                        .clip(RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        progress = { downloadState.progress },
                        modifier = Modifier.size(24.dp),
                        color = Primary,
                        strokeWidth = 2.5.dp
                    )
                }
            }
        }

        Spacer(Modifier.height(4.dp))

        Text(
            text = effectSet.name,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = TextPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

// ============================================
// AD CONFIRMATION UNLOCK DIALOG
// ============================================

// ============================================
// LOADING CONTENT
// ============================================

@Composable
private fun LoadingContent() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(48.dp),
            color = MaterialTheme.colorScheme.primary,
            strokeWidth = 4.dp
        )
    }
}

// ============================================
// ERROR CONTENT
// ============================================

@Composable
private fun ErrorContent(
    message: String,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp)
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = message,
            fontSize = 16.sp,
            color = TextSecondary,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = onRetry,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = stringResource(R.string.editor_retry),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

// ============================================
// COMPOSE PREVIEWS
// ============================================

@Preview(name = "Effect Set Card - States", showBackground = true)
@Composable
private fun EffectSetCardPreview() {
    VideoMakerTheme {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(SplashBackground)
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // State 1: Free & Not Downloaded
            Box(modifier = Modifier.weight(1f)) {
                EffectSetCard(
                    effectSet = EffectSet(
                        id = "1",
                        name = "Zoom In",
                        description = "",
                        isPremium = false
                    ),
                    isSelected = false,
                    isLocked = false,
                    downloadState = DownloadState.NotDownloaded,
                    onClick = {}
                )
            }

            // State 2: Free & Downloading
            Box(modifier = Modifier.weight(1f)) {
                EffectSetCard(
                    effectSet = EffectSet(
                        id = "2",
                        name = "Blur Out",
                        description = "",
                        isPremium = false
                    ),
                    isSelected = true,
                    isLocked = false,
                    downloadState = DownloadState.Downloading(0.5f),
                    onClick = {}
                )
            }

            // State 3: Premium & Locked
            Box(modifier = Modifier.weight(1f)) {
                EffectSetCard(
                    effectSet = EffectSet(
                        id = "3",
                        name = "Rotate 3D",
                        description = "",
                        isPremium = true
                    ),
                    isSelected = false,
                    isLocked = true,
                    downloadState = DownloadState.NotDownloaded,
                    onClick = {}
                )
            }

            // State 4: Premium & Downloaded (Selected)
            Box(modifier = Modifier.weight(1f)) {
                EffectSetCard(
                    effectSet = EffectSet(
                        id = "4",
                        name = "Geometric",
                        description = "",
                        isPremium = true
                    ),
                    isSelected = true,
                    isLocked = false,
                    downloadState = DownloadState.Downloaded,
                    onClick = {}
                )
            }

            // State 5: Free with Premium Transition (Show New Badge)
            Box(modifier = Modifier.weight(1f)) {
                val mockPremiumTransition = Transition(
                    id = "vhs",
                    name = "VHS",
                    category = TransitionCategory.CREATIVE,
                    shaderCode = "",
                    isPremium = true
                )
                EffectSetCard(
                    effectSet = EffectSet(
                        id = "5",
                        name = "Retro Tape",
                        description = "",
                        isPremium = false,
                        transitions = listOf(mockPremiumTransition)
                    ),
                    isSelected = false,
                    isLocked = false,
                    downloadState = DownloadState.NotDownloaded,
                    onClick = {}
                )
            }
        }
    }
}


@Preview(name = "Effect Set Panel - Success State", showBackground = true)
@Composable
private fun EffectSetPanelSuccessPreview() {
    val mockPremiumTransition = Transition(
        id = "vhs",
        name = "VHS",
        category = TransitionCategory.CREATIVE,
        shaderCode = "",
        isPremium = true
    )
    val mockEffectSets = listOf(
        EffectSet(id = "1", name = "Zoom In", description = "", isPremium = false),
        EffectSet(id = "2", name = "Blur Out", description = "", isPremium = false),
        EffectSet(id = "3", name = "Rotate 3D", description = "", isPremium = true),
        EffectSet(id = "4", name = "Geometric", description = "", isPremium = true),
        EffectSet(id = "5", name = "Retro Tape", description = "", isPremium = false, transitions = listOf(mockPremiumTransition)),
        EffectSet(id = "6", name = "Bounce", description = "", isPremium = false),
        EffectSet(id = "7", name = "Glitch", description = "", isPremium = true),
        EffectSet(id = "8", name = "Split Screen", description = "", isPremium = true)
    )

    VideoMakerTheme {
        EffectSetPanelContent(
            uiState = EffectSetUiState.Success(
                effectSets = mockEffectSets,
                hasMorePages = false,
                isLoadingMore = false
            ),
            selectedId = "2",
            unlockedIds = setOf("3"),
            downloadStates = mapOf(
                "1" to DownloadState.NotDownloaded,
                "2" to DownloadState.Downloading(0.7f),
                "3" to DownloadState.NotDownloaded,
                "4" to DownloadState.Downloaded
            ),
            gridState = rememberLazyGridState(),
            snackbarHostState = remember { SnackbarHostState() },
            onDismiss = {},
            onEffectSetClick = {},
            onRetry = {},
            modifier = Modifier
                .fillMaxWidth()
                .height(340.dp)
        )
    }
}

@Preview(name = "Effect Set Panel - Loading & Error States", showBackground = true)
@Composable
private fun EffectSetPanelStatesPreview() {
    VideoMakerTheme {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(SplashBackground),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Loading State:",
                color = TextPrimary,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp)
            )
            Box(modifier = Modifier.height(200.dp)) {
                LoadingContent()
            }

            Text(
                text = "Error State:",
                color = TextPrimary,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Box(modifier = Modifier.height(200.dp)) {
                ErrorContent(
                    message = "Network error. Please check your connection and try again.",
                    onRetry = {}
                )
            }
        }
    }
}
