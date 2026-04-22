package com.videomaker.aimusic.modules.editor.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.videomaker.aimusic.R
import com.videomaker.aimusic.domain.model.AspectRatio
import com.videomaker.aimusic.ui.theme.SplashBackground
import com.videomaker.aimusic.ui.theme.TextPrimary

/**
 * Settings Tab Bar - Effect, Ratio, Volume
 * Each tab opens its own bottom sheet
 * Horizontally scrollable with equal-width tabs
 */
@Composable
internal fun SettingsTabBar(
    currentEffectSetName: String,
    currentRatio: AspectRatio,
    showMusicControls: Boolean, // Show volume tab if music is selected
    currentVolume: Float, // 0.0 to 1.0
    onEffectClick: () -> Unit,
    onRatioClick: () -> Unit,
    onVolumeClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(SplashBackground)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Effect button - displays effect set name
        SettingsTabButton(
            icon = Icons.Default.AutoAwesome,
            label = currentEffectSetName,
            onClick = onEffectClick,
            modifier = Modifier.width(70.dp)
        )

        // Ratio button - shows current ratio
        SettingsTabButton(
            icon = Icons.Default.AspectRatio,
            label = currentRatio.shortLabel,
            onClick = onRatioClick,
            modifier = Modifier.width(70.dp)
        )

        // Volume control (only show if music is selected)
        if (showMusicControls) {
            // Volume button - shows current volume percentage
            SettingsTabButton(
                icon = Icons.Default.VolumeUp,
                label = "${(currentVolume * 100).toInt()}%",
                onClick = onVolumeClick,
                modifier = Modifier.width(70.dp)
            )
        }

    }
}

@Composable
private fun SettingsTabButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = TextPrimary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = TextPrimary,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 14.sp
        )
    }
}

// ============================================
// ASPECT RATIO EXTENSION
// ============================================

private val AspectRatio.shortLabel: String
    get() = when (this) {
        AspectRatio.RATIO_16_9 -> "16:9"
        AspectRatio.RATIO_9_16 -> "9:16"
        AspectRatio.RATIO_4_5 -> "4:5"
        AspectRatio.RATIO_1_1 -> "1:1"
    }
