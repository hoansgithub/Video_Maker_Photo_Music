package com.videomaker.aimusic.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.videomaker.aimusic.ui.theme.AppDimens
import com.videomaker.aimusic.ui.theme.TextPrimary
import com.videomaker.aimusic.ui.theme.VideoMakerTheme

/**
 * Reusable section header with icon, title, and optional "See All" action
 *
 * @param title Section title text
 * @param icon Leading icon
 * @param iconTint Icon color
 * @param onSeeAllClick Optional callback for "See All" arrow button (hidden if null)
 * @param modifier Modifier for the row
 */
@Composable
fun SectionHeader(
    title: String,
    icon: ImageVector,
    iconTint: Color,
    onSeeAllClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val dimens = AppDimens.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = dimens.spaceLg),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.width(dimens.spaceSm))
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(24.dp)
            )
        }

        if (onSeeAllClick != null) {
            IconButton(
                onClick = onSeeAllClick,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "See all",
                    tint = TextPrimary,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}

// ============================================
// PREVIEW
// ============================================

@Preview(name = "Section Header with Arrow", widthDp = 375)
@Composable
private fun SectionHeaderWithArrowPreview() {
    VideoMakerTheme {
        Surface(
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier.padding(vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // With arrow button
                SectionHeader(
                    title = "Trending Templates",
                    icon = Icons.Default.Star,
                    iconTint = MaterialTheme.colorScheme.primary,
                    onSeeAllClick = { /* Arrow click */ }
                )

                // With arrow button - different icon
                SectionHeader(
                    title = "Featured Effects",
                    icon = Icons.Default.AutoAwesome,
                    iconTint = MaterialTheme.colorScheme.secondary,
                    onSeeAllClick = { /* Arrow click */ }
                )

                // Without arrow button
                SectionHeader(
                    title = "Recent Projects",
                    icon = Icons.Default.Star,
                    iconTint = MaterialTheme.colorScheme.tertiary,
                    onSeeAllClick = null
                )
            }
        }
    }
}
