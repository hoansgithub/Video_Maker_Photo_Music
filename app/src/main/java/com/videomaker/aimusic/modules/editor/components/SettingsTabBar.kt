package com.videomaker.aimusic.modules.editor.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Schedule
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
 * Settings Tab Bar - Effect, Duration, Ratio, Music (trim + volume)
 */
@Composable
internal fun SettingsTabBar(
    currentEffectSetName: String,
    currentRatio: AspectRatio,
    currentDurationMs: Long,
    showMusicButton: Boolean,
    musicLabel: String?,
    onEffectClick: () -> Unit,
    onImageDurationClick: () -> Unit,
    onRatioClick: () -> Unit,
    onMusicClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(SplashBackground)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        // Effect button - displays effect set name (max 2 lines)
        SettingsTabButton(
            icon = Icons.Default.AutoAwesome,
            label = currentEffectSetName,
            onClick = onEffectClick,
            modifier = Modifier.weight(1f)
        )

        // Image Duration button - shows current duration
        SettingsTabButton(
            icon = Icons.Default.Schedule,
            label = "${currentDurationMs / 1000f}s",
            onClick = onImageDurationClick,
            modifier = Modifier.weight(1f)
        )

        // Ratio button - shows current ratio
        SettingsTabButton(
            icon = Icons.Default.AspectRatio,
            label = currentRatio.shortLabel,
            onClick = onRatioClick,
            modifier = Modifier.weight(1f)
        )

        // Music button - trim + volume (only show if music is selected)
        if (showMusicButton) {
            SettingsTabButton(
                icon = Icons.Default.MusicNote,
                label = musicLabel ?: stringResource(R.string.editor_music),
                onClick = onMusicClick,
                modifier = Modifier.weight(1f)
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
            modifier = Modifier.size(28.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = TextPrimary,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 16.sp
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
