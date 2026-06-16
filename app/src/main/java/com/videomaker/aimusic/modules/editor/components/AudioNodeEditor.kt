package com.videomaker.aimusic.modules.editor.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.videomaker.aimusic.domain.model.AudioNode
import kotlin.math.roundToInt

/**
 * AudioNodeEditor - Per-node editing controls for trim, volume, and fade.
 *
 * Displayed when a node is selected in the AudioTimelineBar.
 * Provides sliders for volume, fade in/out, and a delete button.
 *
 * @param audioNode The audio node being edited
 * @param onVolumeChange Callback when volume changes
 * @param onFadeInChange Callback when fade-in duration changes
 * @param onFadeOutChange Callback when fade-out duration changes
 * @param onDelete Callback to delete this node
 */
@Composable
fun AudioNodeEditor(
    audioNode: AudioNode,
    onVolumeChange: (Float) -> Unit,
    onFadeInChange: (Long) -> Unit,
    onFadeOutChange: (Long) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    maxFadeDurationMs: Long = 5000L
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
            .background(Color(0xFF2A2A2A))
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = audioNode.songName ?: "Audio",
                    color = Color.White,
                    style = MaterialTheme.typography.titleSmall
                )
                if (audioNode.songArtist != null) {
                    Text(
                        text = audioNode.songArtist,
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 12.sp
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Remove audio",
                    tint = Color(0xFFFF5252)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Volume slider
        var volumeState by remember(audioNode.id) { mutableFloatStateOf(audioNode.volume) }
        LabeledSlider(
            label = "Volume",
            value = volumeState,
            valueText = "${(volumeState * 100).roundToInt()}%",
            onValueChange = { volumeState = it },
            onValueChangeFinished = { onVolumeChange(volumeState) },
            valueRange = 0f..1f
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Fade in slider
        var fadeInState by remember(audioNode.id) {
            mutableFloatStateOf(audioNode.fadeInMs.toFloat())
        }
        LabeledSlider(
            label = "Fade In",
            value = fadeInState,
            valueText = "${(fadeInState / 1000f).let { "%.1f".format(it) }}s",
            onValueChange = { fadeInState = it },
            onValueChangeFinished = { onFadeInChange(fadeInState.toLong()) },
            valueRange = 0f..maxFadeDurationMs.toFloat()
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Fade out slider
        var fadeOutState by remember(audioNode.id) {
            mutableFloatStateOf(audioNode.fadeOutMs.toFloat())
        }
        LabeledSlider(
            label = "Fade Out",
            value = fadeOutState,
            valueText = "${(fadeOutState / 1000f).let { "%.1f".format(it) }}s",
            onValueChange = { fadeOutState = it },
            onValueChangeFinished = { onFadeOutChange(fadeOutState.toLong()) },
            valueRange = 0f..maxFadeDurationMs.toFloat()
        )
    }
}

@Composable
private fun LabeledSlider(
    label: String,
    value: Float,
    valueText: String,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 13.sp
            )
            Text(
                text = valueText,
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 13.sp
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color(0xFF4CAF50),
                inactiveTrackColor = Color.White.copy(alpha = 0.2f)
            )
        )
    }
}
