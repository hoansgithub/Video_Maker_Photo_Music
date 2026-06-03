package com.videomaker.aimusic.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Dp

/**
 * Generic staggered grid layout (waterfall/Pinterest style)
 *
 * Items are placed in the shortest column, creating a masonry effect.
 * Each item can have a different height based on its aspect ratio.
 *
 * @param itemCount Number of items to display
 * @param columns Number of columns (default 2)
 * @param spacing Spacing between items (both horizontal and vertical)
 * @param aspectRatioProvider Function that returns aspect ratio (width/height) for each index
 * @param modifier Modifier for the layout
 * @param itemContent Content for each item, receives index
 */
@Composable
fun StaggeredGrid(
    itemCount: Int,
    columns: Int = 2,
    spacing: Dp,
    aspectRatioProvider: (index: Int) -> Float,
    modifier: Modifier = Modifier,
    itemContent: @Composable (index: Int) -> Unit
) {
    if (itemCount == 0) return

    Layout(
        content = {
            repeat(itemCount) { index ->
                itemContent(index)
            }
        },
        modifier = modifier
    ) { measurables, constraints ->
        val spacingPx = spacing.roundToPx()
        val columnWidth = ((constraints.maxWidth - spacingPx * (columns - 1)) / columns)
            .coerceAtLeast(0)

        // Track height of each column
        val columnHeights = IntArray(columns) { 0 }

        val placeables = measurables.mapIndexed { index, measurable ->
            // Find shortest column
            val column = columnHeights.indices.minByOrNull { columnHeights[it] } ?: 0

            // Calculate height based on aspect ratio; guard against zero/negative aspect ratio
            val aspectRatio = aspectRatioProvider(index).coerceAtLeast(0.01f)
            val itemHeight = (columnWidth / aspectRatio).toInt().coerceAtLeast(0)

            val placeable = measurable.measure(
                constraints.copy(
                    minWidth = columnWidth,
                    maxWidth = columnWidth,
                    minHeight = itemHeight,
                    maxHeight = itemHeight
                )
            )

            val x = column * (columnWidth + spacingPx)
            val y = columnHeights[column]

            columnHeights[column] += itemHeight + spacingPx

            Triple(placeable, x, y)
        }

        val totalHeight = (columnHeights.maxOrNull() ?: 0) - spacingPx

        layout(constraints.maxWidth, totalHeight.coerceAtLeast(0)) {
            placeables.forEach { (placeable, x, y) ->
                // Guard against detached LayoutNode during placement.
                // Known Compose issue: Android View interop children (e.g. NativeAdView)
                // can become detached when their external Activity closes and the draw
                // cycle runs before Compose reconciles the tree.
                try {
                    placeable.placeRelative(x, y)
                } catch (_: IllegalStateException) {
                    // LayoutNode detached from owner — skip this frame;
                    // Compose will reconcile on the next layout pass.
                }
            }
        }
    }
}

/**
 * Staggered grid with pre-calculated aspect ratios list
 *
 * @param itemCount Number of items to display
 * @param aspectRatios Pre-calculated list of aspect ratios
 * @param columns Number of columns (default 2)
 * @param spacing Spacing between items
 * @param modifier Modifier for the layout
 * @param key Stable key function for each item
 * @param itemContent Content for each item
 */
@Composable
fun <T> StaggeredGrid(
    items: List<T>,
    aspectRatios: List<Float>,
    columns: Int = 2,
    spacing: Dp,
    modifier: Modifier = Modifier,
    key: ((item: T) -> Any)? = null,
    itemContent: @Composable (item: T) -> Unit
) {
    if (items.isEmpty()) return

    Layout(
        content = {
            items.forEach { item ->
                if (key != null) {
                    androidx.compose.runtime.key(key(item)) {
                        itemContent(item)
                    }
                } else {
                    itemContent(item)
                }
            }
        },
        modifier = modifier
    ) { measurables, constraints ->
        val spacingPx = spacing.roundToPx()
        val columnWidth = ((constraints.maxWidth - spacingPx * (columns - 1)) / columns)
            .coerceAtLeast(0)

        // Track height of each column
        val columnHeights = IntArray(columns) { 0 }

        val placeables = measurables.mapIndexed { index, measurable ->
            // Find shortest column
            val column = columnHeights.indices.minByOrNull { columnHeights[it] } ?: 0

            // Calculate height based on aspect ratio; guard against zero/negative aspect ratio
            val aspectRatio = aspectRatios.getOrElse(index) { 1f }.coerceAtLeast(0.01f)
            val itemHeight = (columnWidth / aspectRatio).toInt().coerceAtLeast(0)

            val placeable = measurable.measure(
                constraints.copy(
                    minWidth = columnWidth,
                    maxWidth = columnWidth,
                    minHeight = itemHeight,
                    maxHeight = itemHeight
                )
            )

            val x = column * (columnWidth + spacingPx)
            val y = columnHeights[column]

            columnHeights[column] += itemHeight + spacingPx

            Triple(placeable, x, y)
        }

        val totalHeight = (columnHeights.maxOrNull() ?: 0) - spacingPx

        layout(constraints.maxWidth, totalHeight.coerceAtLeast(0)) {
            placeables.forEach { (placeable, x, y) ->
                try {
                    placeable.placeRelative(x, y)
                } catch (_: IllegalStateException) {
                    // LayoutNode detached from owner — skip this frame
                }
            }
        }
    }
}

/**
 * Staggered grid with pre-calculated aspect ratios list (index-based - deprecated)
 *
 * @param itemCount Number of items to display
 * @param aspectRatios Pre-calculated list of aspect ratios
 * @param columns Number of columns (default 2)
 * @param spacing Spacing between items
 * @param modifier Modifier for the layout
 * @param itemContent Content for each item
 */
@Composable
fun StaggeredGrid(
    itemCount: Int,
    aspectRatios: List<Float>,
    columns: Int = 2,
    spacing: Dp,
    modifier: Modifier = Modifier,
    itemContent: @Composable (index: Int) -> Unit
) {
    StaggeredGrid(
        itemCount = itemCount,
        columns = columns,
        spacing = spacing,
        aspectRatioProvider = { index -> aspectRatios.getOrElse(index) { 1f } },
        modifier = modifier,
        itemContent = itemContent
    )
}
