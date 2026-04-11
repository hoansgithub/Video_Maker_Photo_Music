package com.videomaker.aimusic.modules.editor.components

import android.app.Activity
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.rememberCoroutineScope
import co.alcheclub.lib.acccore.ads.loader.AdsLoaderException
import co.alcheclub.lib.acccore.ads.loader.AdsLoaderService
import co.alcheclub.lib.acccore.ads.state.AdsLoadingState
import com.videomaker.aimusic.R
import com.videomaker.aimusic.core.ads.RewardedAdPresenter
import org.koin.compose.koinInject
import com.videomaker.aimusic.core.constants.AdPlacement
import com.videomaker.aimusic.domain.model.EffectSet
import com.videomaker.aimusic.modules.editor.EffectSetUiState
import com.videomaker.aimusic.modules.editor.EffectSetViewModel
import com.videomaker.aimusic.modules.export.WatchAdDialog
import com.videomaker.aimusic.ui.components.AdsLoadingOverlay
import com.videomaker.aimusic.ui.components.AppAsyncImage
import com.videomaker.aimusic.ui.theme.Gray500
import com.videomaker.aimusic.ui.theme.Gray600
import com.videomaker.aimusic.ui.theme.SplashBackground
import com.videomaker.aimusic.ui.theme.TextPrimary
import com.videomaker.aimusic.ui.theme.TextSecondary
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

// ============================================
// EFFECT SET BOTTOM SHEET
// ============================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EffectSetBottomSheet(
    viewModel: EffectSetViewModel,
    selectedEffectSetId: String?,
    onDismiss: () -> Unit,
    onEffectSetSelected: (EffectSet) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val showWatchAdDialog by viewModel.showWatchAdDialog.collectAsStateWithLifecycle()
    val shouldPresentAd by viewModel.shouldPresentAd.collectAsStateWithLifecycle()
    val pendingUnlockEffectSet by viewModel.pendingUnlockEffectSet.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val unlockedIds by viewModel.unlockedEffectSetIds.collectAsStateWithLifecycle()

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val gridState = rememberLazyGridState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val activity = context as? Activity
    val adsLoaderService = koinInject<AdsLoaderService>()
    val coroutineScope = rememberCoroutineScope()

    // Handle rewarded ad presentation using reusable presenter
    RewardedAdPresenter(
        shouldPresent = shouldPresentAd,
        placement = AdPlacement.REWARD_UNLOCK_EFFECT_SET,
        adsLoaderService = adsLoaderService,
        onRewardEarned = viewModel::onRewardEarned,
        onAdFailed = viewModel::onAdFailed
    )

    // Show error message if ad not available
    LaunchedEffect(errorMessage) {
        errorMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.onErrorMessageShown()
        }
    }

    // Detect when user scrolls near the end to trigger pagination
    LaunchedEffect(gridState) {
        val threshold = 8 // Trigger load when 8 items from end (2 rows with 4 columns)
        snapshotFlow { gridState.layoutInfo }
            .collect { layoutInfo ->
                val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: return@collect
                val totalItems = layoutInfo.totalItemsCount
                if (totalItems > 0 && lastVisibleIndex >= totalItems - threshold) {
                    viewModel.loadNextPage()
                }
            }
    }

    Box {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = sheetState,
            containerColor = SplashBackground,
            dragHandle = {
                Box(
                    modifier = Modifier
                        .padding(vertical = 12.dp)
                        .size(width = 36.dp, height = 4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Gray600)
                )
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 32.dp)
            ) {
                // Title row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.editor_effect_sets),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.close),
                            tint = TextPrimary
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Content based on state
                when (val state = uiState) {
                    is EffectSetUiState.Loading -> {
                        LoadingContent()
                    }

                    is EffectSetUiState.Success -> {
                        EffectSetGrid(
                            effectSets = state.effectSets,
                            selectedId = selectedEffectSetId,
                            unlockedIds = unlockedIds,
                            isLoadingMore = state.isLoadingMore,
                            gridState = gridState,
                            onEffectSetClick = { effectSet ->
                                viewModel.onEffectSetClick(
                                    effectSet = effectSet,
                                    onUnlockedEffectSetSelected = onEffectSetSelected
                                )
                            }
                        )
                    }

                    is EffectSetUiState.Error -> {
                        ErrorContent(
                            message = state.message,
                            onRetry = viewModel::onRetry
                        )
                    }
                }

                // Snackbar for error messages
                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }

        // Watch ad dialog
        if (showWatchAdDialog) {
            WatchAdDialog(
                title = stringResource(R.string.effect_set_watch_ad_title),
                subtitle = stringResource(R.string.effect_set_watch_ad_subtitle),
                onDismiss = viewModel::onWatchAdDialogDismiss,
                onWatchAd = {
                    // Set pending effect set - LaunchedEffect will handle ad presentation
                    viewModel.onWatchAdConfirmed()
                }
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
            EffectSetCard(
                effectSet = effectSet,
                isSelected = effectSet.id == selectedId,
                isLocked = isLocked,
                onClick = { onEffectSetClick(effectSet) }
            )
        }

        // Loading footer
        if (isLoadingMore) {
            item(
                span = { androidx.compose.foundation.lazy.grid.GridItemSpan(2) }
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
// EFFECT SET CARD
// ============================================

@Composable
private fun EffectSetCard(
    effectSet: EffectSet,
    isSelected: Boolean,
    isLocked: Boolean,
    onClick: () -> Unit
) {
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
                } else {
                    Modifier
                }
            )
            .clickable(onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Thumbnail with lock overlay
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
        ) {
            AppAsyncImage(
                imageUrl = effectSet.thumbnailUrl,
                contentDescription = effectSet.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(8.dp))
            )

            // Lock icon overlay for locked effect sets
            if (isLocked) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            color = Color.Black.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(8.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = stringResource(R.string.effect_set_locked),
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(4.dp))

        // Name - 2 lines max
        Text(
            text = effectSet.name,
            fontSize = 12.sp,
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
// LOADING CONTENT
// ============================================

@Composable
private fun LoadingContent() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(400.dp),
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
            .height(400.dp)
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

