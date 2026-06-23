package com.videomaker.aimusic.modules.editor.components

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.alcheclub.lib.acccore.ads.loader.AdsLoaderService
import coil.compose.SubcomposeAsyncImage
import com.videomaker.aimusic.R
import com.videomaker.aimusic.core.analytics.Analytics
import com.videomaker.aimusic.core.ads.RewardedAdPresenter
import com.videomaker.aimusic.core.constants.AdPlacement
import com.videomaker.aimusic.domain.model.Sticker
import com.videomaker.aimusic.domain.model.StickerCategory
import com.videomaker.aimusic.modules.editor.DownloadState
import com.videomaker.aimusic.modules.editor.StickerCategoriesState
import com.videomaker.aimusic.modules.editor.StickerListState
import com.videomaker.aimusic.modules.editor.StickerViewModel
import com.videomaker.aimusic.ui.components.AppAsyncImage
import com.videomaker.aimusic.ui.components.ShimmerPlaceholder
import com.videomaker.aimusic.ui.theme.EffectUnselectedBg
import com.videomaker.aimusic.ui.theme.Neutral_Black
import com.videomaker.aimusic.ui.theme.Primary
import com.videomaker.aimusic.ui.theme.SplashBackground
import com.videomaker.aimusic.ui.theme.TextOnPrimary
import com.videomaker.aimusic.ui.theme.TextPrimary
import com.videomaker.aimusic.ui.theme.TextSecondary
import org.koin.compose.koinInject

/**
 * StickerPanel - Inline sticker picker (mirrors EffectSetPanel).
 *
 * Top: horizontally-scrolling category row. Below: 4-column grid of stickers for the
 * selected category. Tapping a sticker downloads it (if needed) and adds it to the
 * video via [onAddSticker]; the panel stays open so multiple stickers can be added.
 */
@Composable
fun StickerPanel(
    viewModel: StickerViewModel,
    onAddSticker: (Sticker) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier
) {
    val categoriesState by viewModel.categoriesState.collectAsStateWithLifecycle()
    val stickerState by viewModel.stickerState.collectAsStateWithLifecycle()
    val selectedCategoryId by viewModel.selectedCategoryId.collectAsStateWithLifecycle()
    val downloadStates by viewModel.downloadStates.collectAsStateWithLifecycle()
    val unlockedIds by viewModel.unlockedStickerIds.collectAsStateWithLifecycle()
    val shouldPresentAd by viewModel.shouldPresentAd.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()

    val gridState = rememberLazyGridState()
    val snackbarHostState = remember { SnackbarHostState() }
    val adsLoaderService = koinInject<AdsLoaderService>()

    RewardedAdPresenter(
        shouldPresent = shouldPresentAd,
        placement = AdPlacement.REWARD_UNLOCK_EFFECT_SET,
        adsLoaderService = adsLoaderService,
        onRewardEarned = viewModel::onRewardEarned,
        onAdFailed = viewModel::onAdFailed
    )

    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.onErrorMessageShown()
        }
    }

    // Pagination for the sticker grid
    LaunchedEffect(gridState, selectedCategoryId) {
        snapshotFlow { gridState.layoutInfo }
            .collect { layoutInfo ->
                val last = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: return@collect
                val total = layoutInfo.totalItemsCount
                if (total > 0 && last >= total - 8) viewModel.loadNextStickers()
            }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(SplashBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
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
                Text(
                    text = stringResource(R.string.editor_sticker),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Primary)
                        .clickable(onClick = onConfirm),
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

            // Category row
            Box(modifier = Modifier.fillMaxWidth()) {
                Spacer(
                    Modifier
                        .height(1.dp)
                        .fillMaxWidth()
                        .background(Neutral_Black)
                        .align(Alignment.BottomCenter)
                )
                CategoryRow(
                    state = categoriesState,
                    selectedId = selectedCategoryId,
                    onCategoryClick = { viewModel.selectCategory(it) }
                )
            }


            // Sticker grid
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                when (val state = stickerState) {
                    is StickerListState.Loading -> StickerGridSkeleton()
                    is StickerListState.Error -> StickerError(
                        message = state.message,
                        onRetry = viewModel::onRetryStickers
                    )
                    is StickerListState.Success -> StickerGrid(
                        stickers = state.stickers,
                        downloadStates = downloadStates,
                        unlockedIds = unlockedIds,
                        isLoadingMore = state.isLoadingMore,
                        gridState = gridState,
                        onStickerClick = { sticker ->
                            Analytics.trackStickerClick(
                                stickerName = sticker.name,
                                isPremium = sticker.isPremium
                            )
                            viewModel.onStickerClick(sticker, onReady = onAddSticker)
                        }
                    )
                }
            }

            SnackbarHost(hostState = snackbarHostState, modifier = Modifier.padding(8.dp))
        }
    }
}

// ============================================
// CATEGORY ROW
// ============================================

@Composable
private fun CategoryRow(
    state: StickerCategoriesState,
    selectedId: String?,
    onCategoryClick: (String) -> Unit
) {
    when (state) {
        is StickerCategoriesState.Loading -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                repeat(5) {
                    ShimmerPlaceholder(
                        modifier = Modifier.size(46.dp),
                        cornerRadius = 12.dp
                    )
                }
            }
        }

        is StickerCategoriesState.Error -> {
            // Categories failed — keep the row area empty; grid shows the retry.
            Spacer(Modifier.height(46.dp))
        }

        is StickerCategoriesState.Success -> {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(items = state.categories, key = { it.id }) { category ->
                    CategoryItem(
                        category = category,
                        isSelected = category.id == selectedId,
                        onClick = { onCategoryClick(category.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryItem(
    category: StickerCategory,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable {
                    Analytics.trackStickerSetClick(category.name)
                    onClick()
                },
            contentAlignment = Alignment.Center
        ) {
            AppAsyncImage(
                // Thumbnail first for fast category-row loading.
                imageUrl = category.thumbnailUrl.ifEmpty { category.iconUrl },
                contentDescription = category.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(30.dp)
            )
        }
        Spacer(Modifier.height(4.dp))
        // Selected underline: width 20, height 2, radius 12
        Box(
            modifier = Modifier
                .width(20.dp)
                .height(2.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (isSelected) Primary else Color.Transparent)
        )
    }
}

// ============================================
// STICKER GRID
// ============================================

@Composable
private fun StickerGrid(
    stickers: List<Sticker>,
    downloadStates: Map<String, DownloadState>,
    unlockedIds: Set<String>,
    isLoadingMore: Boolean,
    gridState: LazyGridState,
    onStickerClick: (Sticker) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        state = gridState,
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        items(items = stickers, key = { it.id }, contentType = { "sticker" }) { sticker ->
            val isLocked = sticker.isPremium && !unlockedIds.contains(sticker.id)
            val downloadState = downloadStates[sticker.id] ?: DownloadState.NotDownloaded
            StickerCard(
                sticker = sticker,
                isLocked = isLocked,
                downloadState = downloadState,
                onClick = { onStickerClick(sticker) }
            )
        }
        if (isLoadingMore) {
            item(span = { GridItemSpan(4) }) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(28.dp),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 3.dp
                    )
                }
            }
        }
    }
}

@Composable
private fun StickerCard(
    sticker: Sticker,
    isLocked: Boolean,
    downloadState: DownloadState,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(88.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(EffectUnselectedBg)
            .clickable(onClick = onClick)
    ) {
        SubcomposeAsyncImage(
            // 128px thumbnail in the grid for fast loading.
            model = sticker.displayUrl,
            contentDescription = sticker.name,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            loading = { ShimmerPlaceholder(modifier = Modifier.fillMaxSize(), cornerRadius = 12.dp) },
            error = { ShimmerPlaceholder(modifier = Modifier.fillMaxSize(), cornerRadius = 12.dp) }
        )

        // NEW badge (top-start)
        if (sticker.isNew && downloadState is DownloadState.NotDownloaded) {
            Image(
                painter = painterResource(id = R.drawable.ic_new_item),
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .size(20.dp)
            )
        }

        when {
            downloadState is DownloadState.Downloading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                }
            }
            // AD badge for locked premium stickers
            isLocked -> {
                Image(
                    painter = painterResource(id = R.drawable.ic_ads),
                    contentDescription = null,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .size(18.dp)
                )
            }
            // Download icon for not-yet-downloaded free stickers
            downloadState is DownloadState.NotDownloaded -> {
                Icon(
                    painter = painterResource(id = R.drawable.ic_download),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .size(18.dp)
                )
            }
        }
    }
}

// ============================================
// SKELETON / ERROR
// ============================================

@Composable
private fun StickerGridSkeleton() {
    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth(),
        userScrollEnabled = false
    ) {
        items(count = 12) {
            ShimmerPlaceholder(
                modifier = Modifier.size(88.dp),
                cornerRadius = 12.dp
            )
        }
    }
}

@Composable
private fun     StickerError(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp)
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = message, fontSize = 16.sp, color = TextSecondary, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onRetry,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = stringResource(R.string.editor_retry),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextOnPrimary
            )
        }
    }
}
