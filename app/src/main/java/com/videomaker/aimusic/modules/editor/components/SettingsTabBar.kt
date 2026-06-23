package com.videomaker.aimusic.modules.editor.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.videomaker.aimusic.R
import com.videomaker.aimusic.ui.components.ModifierExtension.clickableSingle
import com.videomaker.aimusic.ui.theme.FoundationBlack_100
import com.videomaker.aimusic.ui.theme.SplashBackground

/**
 * Settings Tab Bar - Images, Effect, Ratio, Volume
 * Each tab opens its own bottom sheet
 * Horizontally scrollable with equal-width tabs
 */
@Composable
internal fun SettingsTabBar(
    showMusicControls: Boolean, // Show volume tab if music is selected
    onImagesClick: () -> Unit,
    onEffectClick: () -> Unit,
    onTextClick: () -> Unit,
    onStickerClick: () -> Unit,
    onRatioClick: () -> Unit,
    onVolumeClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(SplashBackground)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.SpaceAround
    ) {
        // Effect button - displays effect set name
        SettingsTabButton(
            icon = R.drawable.img_effect_video,
            label = stringResource(R.string.editor_effect),
            onClick = onEffectClick,
        )

        // Images button - shows "Photos" label
        SettingsTabButton(
            icon = R.drawable.img_replace_gallery,
            label = stringResource(R.string.editor_photos_label),
            onClick = onImagesClick,
        )
        // Sticker button - opens the sticker picker panel
        SettingsTabButton(
            icon = R.drawable.ic_edit_sticker,
            label = stringResource(R.string.editor_sticker),
            onClick = onStickerClick,
        )

        // Ratio button - shows current ratio
        SettingsTabButton(
            icon = R.drawable.img_ratio_frame,
            label = stringResource(R.string.editor_ratio_label),
            onClick = onRatioClick,
        )

        // Volume control (only show if music is selected)
        if (showMusicControls) {
            // Volume button - shows current volume percentage
            SettingsTabButton(
                icon = R.drawable.img_edit_sound,
                label = stringResource(R.string.editor_volume),
                onClick = onVolumeClick,
            )
        }

        // Text button
        SettingsTabButton(
            icon = R.drawable.ic_add_text,
            label = stringResource(R.string.editor_text),
            onClick = onTextClick,
        )
    }
}

@Composable
private fun SettingsTabButton(
    icon: Int,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clickableSingle(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(icon),
            contentDescription = label,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.W500,
            color = FoundationBlack_100,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 14.sp
        )
    }
}

