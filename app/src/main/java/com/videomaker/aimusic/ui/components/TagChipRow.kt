package com.videomaker.aimusic.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.videomaker.aimusic.R
import com.videomaker.aimusic.domain.model.VibeTag
import com.videomaker.aimusic.ui.theme.AppDimens

/**
 * Reusable tag chip row used in Gallery and Template List screens.
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

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = dimens.spaceLg),
        horizontalArrangement = Arrangement.spacedBy(dimens.spaceSm)
    ) {
        // "All"/"For you" chip — clears filter
        AppFilterChip(
            text = showAllLabel,
            isSelected = selectedTagId == null,
            onClick = { onTagSelected(null) }
        )
        vibeTags.forEach { tag ->
            AppFilterChip(
                text = if (tag.emoji.isNotEmpty()) "${tag.emoji} ${tag.displayName}" else tag.displayName,
                isSelected = tag.id == selectedTagId,
                onClick = { onTagSelected(tag.id) }
            )
        }
    }
}
