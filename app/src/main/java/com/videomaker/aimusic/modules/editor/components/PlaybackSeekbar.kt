package com.videomaker.aimusic.modules.editor.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * PlaybackSeekbar - A simple seekbar with time labels for video playback control
 *
 * Displays current position and total duration in MM:SS format with a draggable slider.
 * Supports scrubbing preview with throttling to show frames while dragging.
 *
 * @param currentPositionMs Current playback position in milliseconds
 * @param durationMs Total duration in milliseconds
 * @param isEnabled Whether the seekbar is interactive (disabled when no composition)
 * @param onSeek Called when user finishes seeking with the target position in milliseconds
 * @param onScrub Called during drag to preview frames (throttled to prevent overload)
 * @param onSeekStart Called when user starts dragging the slider
 * @param onSeekEnd Called when user finishes dragging (after onSeek)
 * @param scrubThrottleMs Minimum time between scrub calls in milliseconds (default 150ms)
 * @param modifier Modifier for the container
 */
@Composable
fun PlaybackSeekbar(
    currentPositionMs: Long,
    durationMs: Long,
    isEnabled: Boolean = true,
    onSeek: (positionMs: Long) -> Unit,
    onScrub: (positionMs: Long) -> Unit = {},
    onSeekStart: () -> Unit = {},
    onSeekEnd: () -> Unit = {},
    scrubThrottleMs: Long = 150L,
    modifier: Modifier = Modifier
) {
    // Track whether user is currently dragging
    var isDragging by remember { mutableStateOf(false) }
    // Local slider value during drag (to show immediate feedback)
    var dragValue by remember { mutableFloatStateOf(0f) }
    // Track the last seek target to avoid jump-back during seek completion
    var pendingSeekValue by remember { mutableFloatStateOf(-1f) }
    // Track last scrub time for throttling
    var lastScrubTime by remember { mutableLongStateOf(0L) }

    // Calculate slider progress (0f to 1f)
    // Priority: dragging > pending seek > current position
    val progress = if (durationMs > 0) {
        when {
            isDragging -> dragValue
            pendingSeekValue >= 0f -> pendingSeekValue
            else -> (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
        }
    } else {
        0f
    }

    // Clear pending seek when player position catches up (within 500ms tolerance)
    if (pendingSeekValue >= 0f && durationMs > 0) {
        val pendingPositionMs = (pendingSeekValue * durationMs).toLong()
        if (kotlin.math.abs(currentPositionMs - pendingPositionMs) < 500) {
            pendingSeekValue = -1f
        }
    }

    // Calculate display position (either current or drag position)
    val displayPositionMs = when {
        isDragging && durationMs > 0 -> (dragValue * durationMs).toLong()
        pendingSeekValue >= 0f && durationMs > 0 -> (pendingSeekValue * durationMs).toLong()
        else -> currentPositionMs
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Current time label
        Text(
            text = formatDuration(displayPositionMs),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )

        // Seekbar slider
        Slider(
            value = progress,
            onValueChange = { newValue ->
                if (!isDragging) {
                    isDragging = true
                    onSeekStart()
                }
                dragValue = newValue

                // Throttled scrubbing - seek to show frame preview while dragging
                if (durationMs > 0) {
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastScrubTime >= scrubThrottleMs) {
                        lastScrubTime = currentTime
                        val scrubPositionMs = (newValue * durationMs).toLong()
                        onScrub(scrubPositionMs)
                    }
                }
            },
            onValueChangeFinished = {
                if (isDragging && durationMs > 0) {
                    val seekPositionMs = (dragValue * durationMs).toLong()
                    // Keep showing the drag position until player catches up
                    pendingSeekValue = dragValue
                    onSeek(seekPositionMs)
                }
                isDragging = false
                onSeekEnd()
            },
            enabled = isEnabled && durationMs > 0,
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                disabledThumbColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                disabledActiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                disabledInactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
            )
        )

        // Total duration label
        Text(
            text = formatDuration(durationMs),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Format milliseconds to MM:SS string
 */
private fun formatDuration(durationMs: Long): String {
    val totalSeconds = (durationMs / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}
