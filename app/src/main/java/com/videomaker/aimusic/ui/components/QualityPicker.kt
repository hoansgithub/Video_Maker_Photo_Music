package com.videomaker.aimusic.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.videomaker.aimusic.R
import com.videomaker.aimusic.domain.model.VideoQuality
import com.videomaker.aimusic.ui.components.ModifierExtension.clickableSingle
import com.videomaker.aimusic.ui.theme.Neutral_N600
import com.videomaker.aimusic.ui.theme.SplashBackground

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
            if (!isQualityUnlocked) {
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
    isAdTypeInterstitial: Boolean = false,
    onMenuOpen: () -> Unit = {},
) {
    var showQualityMenu by remember { mutableStateOf(false) }

    // Measure anchor (button) and menu widths so the dropdown can be right-aligned
    // under the button instead of the default start-alignment (which pushes it right).
    val density = LocalDensity.current
    var buttonWidthPx by remember { mutableIntStateOf(0) }
    var menuWidthPx by remember { mutableIntStateOf(0) }

    Box {
        // Quality button
        Row(
            modifier = Modifier
                .onSizeChanged { buttonWidthPx = it.width }
                .clickableSingle {
                    onMenuOpen()
                    showQualityMenu = true
                }
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(2.5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // [AD] badge for locked qualities — only when the unlock ad type is NOT
            // interstitial (interstitial shows on export anyway; badge is for rewarded unlocks)
            if (!isQualityUnlocked && !isAdTypeInterstitial) {
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
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.width(1.5.dp))
            Icon(
                painter = painterResource(R.drawable.ic_arrow_down),
                contentDescription = stringResource(R.string.editor_select_quality),
                tint = Neutral_N600,
                modifier = Modifier.size(12.dp)
            )
        }

        // Quality dropdown menu — right-aligned under the button with an 8.dp gap.
        // offset.x is negative (menu is wider than the button) so its right edge lines
        // up with the button's right edge; offset.y adds the vertical spacing.
        val menuOffsetX = with(density) { (buttonWidthPx - menuWidthPx).toDp() }
        DropdownMenu(
            expanded = showQualityMenu,
            onDismissRequest = { showQualityMenu = false },
            offset = DpOffset(x = menuOffsetX, y = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .width(IntrinsicSize.Max)
                    .onSizeChanged { menuWidthPx = it.width }
            ) {
                VideoQuality.entries.forEach { quality ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickableSingle{
                                onQualityChange(quality)
                                showQualityMenu = false
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (quality == selectedQuality) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(8.dp))
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

                }
            }
        }
    }
}

