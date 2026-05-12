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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

            // Wrapper Box with padding for floating menu - prevents clipping
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 60.dp) // Space for floating menu above images
            ) {
                // Images horizontal scroll list
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Current images
                    items(
                        items = assets,
                        key = { asset -> asset.id }
                    ) { asset ->
                        ImageItem(
                            asset = asset,
                            isSelected = selectedAssetId == asset.id,
                            onClick = {
                                selectedAssetId = if (selectedAssetId == asset.id) null else asset.id
                            },
                            onRemove = {
                                assets = assets.filter { it.id != asset.id }
                                selectedAssetId = null
                            }
                        )
                    }

                    // Add images button (last item)
                    item {
                        AddImageButton(
                            onClick = onAddImages
                        )
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
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(MaterialTheme.colorScheme.primary, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = stringResource(R.string.editor_add_images),
                tint = SplashBackground,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

@Composable
private fun ImageItem(
    asset: Asset,
    isSelected: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
    ) {
        // Image with drag icon
        Box(
            modifier = Modifier
                .width(78.dp)
                .height(102.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onClick)
        ) {
            // Image
            AsyncImage(
                model = asset.uri,
                contentDescription = "Image ${asset.id}",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // 4-way arrow icon at bottom left for drag & drop
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

            // Selection overlay
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                )
            }
        }

        // Floating delete menu with arrow - appears above this item when selected
        if (isSelected) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = (-58).dp) // Closer to item top
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Menu card with dark background
                    Column(
                        modifier = Modifier
                            .shadow(8.dp, RoundedCornerShape(8.dp))
                            .background(Color(0xFF2C2C2C), RoundedCornerShape(8.dp))
                            .clickable(onClick = onRemove)
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = Color.Red,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = stringResource(R.string.editor_delete_image),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White
                        )
                    }

                    // Arrow pointing down to the item
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .offset(y = (-5).dp)
                            .rotate(45f)
                            .background(Color(0xFF2C2C2C))
                    )
                }
            }
        }
    }
}
