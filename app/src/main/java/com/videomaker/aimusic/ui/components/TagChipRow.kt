package com.videomaker.aimusic.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.videomaker.aimusic.R
import com.videomaker.aimusic.domain.model.VibeTag
import com.videomaker.aimusic.ui.theme.AppDimens

/**
 * Reusable tag chip row used in Gallery and Template List screens.
 * Auto-scrolls to center the selected chip.
 *
 * @param vibeTags List of vibe tags to display
 * @param selectedTagId Currently selected tag ID (null = "All"/"For you")
 * @param onTagSelected Callback when a tag is selected (null = "All")
 * @param showAllLabel Label for "All" chip (default: "For you")
 * @param modifier Modifier for the row
 */
@Composable
fun TagChipRow(
    vibeTags: List<VibeTag>,
    selectedTagId: String?,
    onTagSelected: (String?) -> Unit,
    modifier: Modifier = Modifier,
    showAllLabel: String = stringResource(R.string.gallery_filter_for_you)
) {
    val dimens = AppDimens.current
    val listState = rememberLazyListState()

    // Find selected chip index (0 = "All", 1+ = vibe tags)
    val selectedIndex = if (selectedTagId == null) {
        0
    } else {
        vibeTags.indexOfFirst { it.id == selectedTagId }.let {
            if (it >= 0) it + 1 else 0
        }
    }

    // Auto-scroll to center selected chip
    LaunchedEffect(selectedIndex) {
        if (selectedIndex > 0 && listState.layoutInfo.totalItemsCount > 0) {
            // Calculate scroll position to center the selected chip
            val itemWidth = 120  // Approximate chip width + spacing
            val viewportWidth = listState.layoutInfo.viewportSize.width
            val centerOffset = (viewportWidth / 2) - (itemWidth / 2)

            listState.animateScrollToItem(
                index = selectedIndex,
                scrollOffset = -centerOffset
            )
        }
    }

    LazyRow(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = dimens.spaceLg),
        horizontalArrangement = Arrangement.spacedBy(dimens.spaceSm)
    ) {
        // "All"/"For you" chip — clears filter
        item(key = "all_chip") {
            AppFilterChip(
                text = showAllLabel,
                isSelected = selectedTagId == null,
                onClick = { onTagSelected(null) }
            )
        }

        itemsIndexed(
            items = vibeTags,
            key = { _, tag -> tag.id }
        ) { _, tag ->
            AppFilterChip(
                text = if (tag.emoji.isNotEmpty()) "${tag.emoji} ${tag.displayName}" else tag.displayName,
                isSelected = tag.id == selectedTagId,
                onClick = { onTagSelected(tag.id) }
            )
        }
    }
}
