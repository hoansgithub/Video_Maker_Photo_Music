package com.videomaker.aimusic.modules.editor.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.rotate
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import coil.compose.AsyncImage
import com.videomaker.aimusic.R
import com.videomaker.aimusic.domain.model.Asset
import com.videomaker.aimusic.ui.theme.Gray500
import com.videomaker.aimusic.ui.theme.SplashBackground
import com.videomaker.aimusic.ui.theme.TextPrimary

/**
 * Images Bottom Sheet - Manage image order and add new images
 *
 * Features:
 * - Display current images in horizontal scrollable list
 * - Add new images button
 * - Drag and drop reordering (to be implemented)
 * - Confirm button to apply changes
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ImagesBottomSheet(
    currentAssets: List<Asset>,
    onDismiss: () -> Unit,
    onAddImages: () -> Unit,
    onConfirm: (List<Asset>) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true
    )

    // Track which asset is selected to show remove button
    var selectedAssetId by remember { mutableStateOf<String?>(null) }

    // Mutable list for removing assets
    var assets by remember(currentAssets) { mutableStateOf(currentAssets) }

    // Drag and drop state
    var draggedAssetId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var dropIndex by remember { mutableStateOf<Int?>(null) }
    var lazyRowStartX by remember { mutableStateOf(0f) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SplashBackground,
        dragHandle = null,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(top = 24.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header with title and checkmark button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.editor_images_title),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    maxLines = 2,
                    modifier = Modifier.weight(1f)
                )

                // Checkmark confirm button
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                        .clickable {
                            onConfirm(assets)
                            onDismiss()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = stringResource(R.string.confirm),
                        tint = SplashBackground,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Images horizontal scroll list with drag and drop
            Box(modifier = Modifier.fillMaxWidth()) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onGloballyPositioned { coordinates ->
                            lazyRowStartX = coordinates.positionInParent().x
                        },
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Current images
                    items(
                        items = assets,
                        key = { asset -> asset.id }
                    ) { asset ->
                        val index = assets.indexOf(asset)

                        // Show drop indicator line before this item
                        if (dropIndex == index && draggedAssetId != null) {
                            Box(
                                modifier = Modifier
                                    .width(2.dp)
                                    .height(102.dp)
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                        }

                        ImageItem(
                            asset = asset,
                            isSelected = selectedAssetId == asset.id,
                            isDragging = draggedAssetId == asset.id,
                            canDelete = assets.size > 3,
                            onClick = {
                                if (draggedAssetId == null && assets.size > 3) {
                                    // Only allow selection for delete if more than 3 images
                                    selectedAssetId = if (selectedAssetId == asset.id) null else asset.id
                                }
                            },
                            onRemove = {
                                // Only allow deletion if more than 3 images
                                if (assets.size > 3) {
                                    assets = assets.filter { it.id != asset.id }
                                    selectedAssetId = null
                                }
                            },
                            onDragStart = { offset ->
                                draggedAssetId = asset.id
                                dragOffset = offset
                                selectedAssetId = null
                            },
                            onDrag = { offset ->
                                dragOffset = offset
                                // Calculate drop index based on finger position
                                val itemWidth = 78f // Item width in dp
                                val spacing = 8f // Spacing between items
                                val totalItemWidth = itemWidth + spacing

                                // Get finger position relative to the LazyRow start
                                val fingerX = offset.x - lazyRowStartX

                                // Calculate which boundary the finger is closest to
                                // Each boundary is at: n * (itemWidth + spacing) + itemWidth/2
                                // Simplified: find which "slot" the finger is in
                                val rawIndex = (fingerX + spacing / 2f) / totalItemWidth
                                dropIndex = rawIndex.toInt().coerceIn(0, assets.size)
                            },
                            onDragEnd = {
                                val finalDropIndex = dropIndex
                                if (draggedAssetId != null && finalDropIndex != null) {
                                    val fromIndex = assets.indexOfFirst { it.id == draggedAssetId }
                                    if (fromIndex != -1 && finalDropIndex != fromIndex) {
                                        val newList = assets.toMutableList()
                                        val item = newList.removeAt(fromIndex)
                                        val targetIndex = if (finalDropIndex > fromIndex) finalDropIndex - 1 else finalDropIndex
                                        newList.add(targetIndex.coerceIn(0, newList.size), item)
                                        assets = newList
                                    }
                                }
                                draggedAssetId = null
                                dragOffset = Offset.Zero
                                dropIndex = null
                            }
                        )
                    }

                    // Show drop indicator at the end
                    if (dropIndex == assets.size && draggedAssetId != null) {
                        item {
                            Box(
                                modifier = Modifier
                                    .width(2.dp)
                                    .height(102.dp)
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                        }
                    }

                    // Add images button (last item)
                    item {
                        AddImageButton(
                            onClick = onAddImages
                        )
                    }
                }

                // Dragged item following finger
                if (draggedAssetId != null) {
                    val draggedAsset = assets.find { it.id == draggedAssetId }
                    if (draggedAsset != null) {
                        Box(
                            modifier = Modifier
                                .offset { IntOffset(dragOffset.x.roundToInt(), dragOffset.y.roundToInt()) }
                                .zIndex(1f)
                                .graphicsLayer {
                                    alpha = 0.8f
                                    scaleX = 1.1f
                                    scaleY = 1.1f
                                }
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(78.dp)
                                    .height(102.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .shadow(8.dp, RoundedCornerShape(12.dp))
                            ) {
                                AsyncImage(
                                    model = draggedAsset.uri,
                                    contentDescription = "Dragging",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AddImageButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .width(78.dp)
            .height(102.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF2C2C2C))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = stringResource(R.string.editor_add_images),
            tint = Color.White,
            modifier = Modifier.size(32.dp)
        )
    }
}

@Composable
private fun ImageItem(
    asset: Asset,
    isSelected: Boolean,
    isDragging: Boolean,
    canDelete: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    onDragStart: (Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    modifier: Modifier = Modifier
) {
    var itemPosition by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = modifier
            .width(78.dp)
            .height(102.dp)
            .graphicsLayer {
                alpha = if (isDragging) 0.3f else 1f
            }
            .onGloballyPositioned { coordinates ->
                itemPosition = coordinates.positionInParent()
            }
            .clip(RoundedCornerShape(12.dp))
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        if (!isDragging) {
                            onClick()
                        }
                    }
                )
            }
            .pointerInput(Unit) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { offset ->
                        onDragStart(itemPosition + offset)
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        onDrag(itemPosition + change.position)
                    },
                    onDragEnd = {
                        onDragEnd()
                    },
                    onDragCancel = {
                        onDragEnd()
                    }
                )
            }
    ) {
        // Image
        AsyncImage(
            model = asset.uri,
            contentDescription = "Image ${asset.id}",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // 4-way arrow icon at bottom left for drag & drop
        if (!isSelected) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(4.dp)
                    .size(20.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.OpenWith,
                    contentDescription = "Drag to reorder",
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
            }
        }

        // Delete menu inside the item when selected (only shown if canDelete)
        if (isSelected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF2C2C2C).copy(alpha = 0.9f))
                    .clickable(onClick = onRemove),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = Color.Red,
                        modifier = Modifier.size(28.dp)
                    )
                    Text(
                        text = stringResource(R.string.editor_delete_image),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }
            }
        }
    }
}
