package com.videomaker.aimusic.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.videomaker.aimusic.R
import com.videomaker.aimusic.domain.model.VideoQuality
import com.videomaker.aimusic.ui.components.ModifierExtension.clickableSingle
import com.videomaker.aimusic.ui.theme.Neutral_N600

/**
 * Reusable Quality Picker Component
 *
 * Displays a dropdown button showing the current video quality with badges:
 * - HD badge for 1080p
 * - [AD] badge for locked qualities (720p/1080p when not unlocked)
 * Used in both Editor and Export screens for consistent UI.
 *
 * @param selectedQuality Currently selected video quality
 * @param onQualityChange Callback when user selects a different quality
 * @param isQualityUnlocked Whether high quality (720p/1080p) is unlocked for this session
 * @param modifier Optional modifier for the component
 * @param buttonColor Background color for the quality button
 * @param textColor Text color for the quality button
 */
@Composable
fun QualityPicker(
    selectedQuality: VideoQuality,
    onQualityChange: (VideoQuality) -> Unit,
    isQualityUnlocked: Boolean = true,
    onMenuOpen: () -> Unit = {},
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
                .clickableSingle {
                    onMenuOpen()
                    showQualityMenu = true
                }
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // [AD] badge for locked qualities
            val isLocked = !isQualityUnlocked
            if (isLocked) {
                AdBadge(
                    style = AdBadgeStyle.Small(
                        textColor = textColor,
                        backgroundColor = textColor.copy(alpha = 0.2f)
                    )
                )
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
                val isQualityLocked = !isQualityUnlocked
                DropdownMenuItem(
                    text = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = quality.displayName,
                                fontWeight = if (quality == selectedQuality) {
                                    FontWeight.Bold
                                } else {
                                    FontWeight.Normal
                                },
                                textAlign = TextAlign.End
                            )
                            // [AD] badge for locked qualities
                            if (isQualityLocked) {
                                Spacer(modifier = Modifier.width(6.dp))
                                AdBadge(
                                    style = AdBadgeStyle.Small(
                                        textColor = MaterialTheme.colorScheme.onSurface
                                    )
                                )
                            }
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

@Composable
fun QualityPickerV2(
    selectedQuality: VideoQuality,
    onQualityChange: (VideoQuality) -> Unit,
    isQualityUnlocked: Boolean = true,
    onMenuOpen: () -> Unit = {},
) {
    var showQualityMenu by remember { mutableStateOf(false) }

    Box {
        // Quality button
        Row(
            modifier = Modifier
                .clickableSingle {
                    onMenuOpen()
                    showQualityMenu = true
                }
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(2.5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // [AD] badge for locked qualities
            if (!isQualityUnlocked) {
                AdBadge(
                    style = AdBadgeStyle.Small(
                        textColor = Neutral_N600,
                        backgroundColor = Neutral_N600.copy(alpha = 0.2f)
                    )
                )
            }
            Text(
                text = selectedQuality.displayName,
                color = Neutral_N600,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.width(1.5.dp))
            Icon(
                painter = painterResource(R.drawable.ic_arrow_down),
                contentDescription = stringResource(R.string.editor_select_quality),
                tint = Neutral_N600,
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
                            Text(
                                text = quality.displayName,
                                fontWeight = if (quality == selectedQuality) {
                                    FontWeight.Bold
                                } else {
                                    FontWeight.Normal
                                },
                                textAlign = TextAlign.End
                            )
                            if (!isQualityUnlocked) {
                                Spacer(modifier = Modifier.width(6.dp))
                                AdBadge(
                                    style = AdBadgeStyle.Small(
                                        textColor = MaterialTheme.colorScheme.onSurface
                                    )
                                )
                            }
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

