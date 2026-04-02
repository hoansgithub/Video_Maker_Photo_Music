package com.videomaker.aimusic.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.videomaker.aimusic.R
import com.videomaker.aimusic.ui.theme.AppDimens
import com.videomaker.aimusic.ui.theme.SearchFieldBackground
import com.videomaker.aimusic.ui.theme.SearchFieldBorder
import com.videomaker.aimusic.ui.theme.TextTertiary

/**
 * Search field component for the Songs screen.
 * Displays a clickable search bar with music note and search icons.
 */
@Composable
fun SongsSearchField(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    hint: String = "Search songs"
) {
    val dimens = AppDimens.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = SearchFieldBackground,
                shape = RoundedCornerShape(dimens.radiusXl)
            )
            .border(
                width = 1.dp,
                color = SearchFieldBorder,
                shape = RoundedCornerShape(dimens.radiusXl)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = dimens.spaceMd, vertical = dimens.spaceMd),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_music_note),
            contentDescription = null,
            tint = Color.Unspecified,
            modifier = Modifier.size(24.dp)
        )

        Spacer(modifier = Modifier.width(dimens.spaceSm))

        Text(
            text = hint,
            style = MaterialTheme.typography.titleSmall,
            color = TextTertiary,
            modifier = Modifier.weight(1f)
        )

        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = "Search",
            tint = TextTertiary
        )
    }
}
