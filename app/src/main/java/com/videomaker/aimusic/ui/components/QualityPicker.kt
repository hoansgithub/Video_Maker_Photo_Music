package com.videomaker.aimusic.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.videomaker.aimusic.R
import com.videomaker.aimusic.domain.model.VideoQuality
import com.videomaker.aimusic.ui.components.ModifierExtension.clickableSingle

/**
 * Reusable Quality Picker Component
 *
 * Displays a dropdown button showing the current video quality with an HD badge for 1080p.
 * Used in both Editor and Export screens for consistent UI.
 *
 * @param selectedQuality Currently selected video quality
 * @param onQualityChange Callback when user selects a different quality
 * @param modifier Optional modifier for the component
 * @param buttonColor Background color for the quality button
 * @param textColor Text color for the quality button
 */
@Composable
fun QualityPicker(
    selectedQuality: VideoQuality,
    onQualityChange: (VideoQuality) -> Unit,
    modifier: Modifier = Modifier,
    buttonColor: Color = Color(0x1FFFFFFF),
    textColor: Color = Color.White
) {
    var showQualityMenu by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        // Quality button
        Row(
            modifier = Modifier
                .background(
                    color = buttonColor,
                    shape = RoundedCornerShape(16.dp)
                )
                .clickableSingle { showQualityMenu = true }
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // HD badge for 1080p (on the left)
            if (selectedQuality == VideoQuality.FHD_1080) {
                HdBadge()
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                text = selectedQuality.displayName,
                color = textColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = stringResource(R.string.editor_select_quality),
                tint = textColor,
                modifier = Modifier.size(18.dp)
            )
        }

        // Quality dropdown menu
        DropdownMenu(
            expanded = showQualityMenu,
            onDismissRequest = { showQualityMenu = false }
        ) {
            VideoQuality.entries.forEach { quality ->
                DropdownMenuItem(
                    text = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // HD badge for 1080p
                            if (quality == VideoQuality.FHD_1080) {
                                HdBadge()
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text(
                                text = quality.displayName,
                                fontWeight = if (quality == selectedQuality) {
                                    FontWeight.Bold
                                } else {
                                    FontWeight.Normal
                                },
                                textAlign = TextAlign.End
                            )
                        }
                    },
                    onClick = {
                        onQualityChange(quality)
                        showQualityMenu = false
                    },
                    leadingIcon = if (quality == selectedQuality) {
                        {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    } else null
                )
            }
        }
    }
}

/**
 * HD Badge for 1080p quality indicator
 */
@Composable
private fun HdBadge(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(
                color = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(4.dp)
            )
            .padding(horizontal = 4.dp, vertical = 2.dp)
    ) {
        Text(
            text = stringResource(R.string.editor_hd),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimary
        )
    }
}
