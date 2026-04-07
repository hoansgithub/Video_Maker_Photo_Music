package com.videomaker.aimusic.modules.editor.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.videomaker.aimusic.R
import com.videomaker.aimusic.ui.components.AppAsyncImage
import com.videomaker.aimusic.ui.components.ModifierExtension.clickableSingle
import com.videomaker.aimusic.ui.theme.Gray500
import com.videomaker.aimusic.ui.theme.PlayerCardBackground
import com.videomaker.aimusic.ui.theme.TextPrimary
import com.videomaker.aimusic.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MusicSection(
    songName: String,
    coverUrl: String,
    duration: String,
    currentPosition: Float,
    isPlaying: Boolean,
    onSeek: (Float) -> Unit,
    onScrub: (Float) -> Unit = {},
    onSeekStart: () -> Unit = {},
    onSeekEnd: () -> Unit = {},
    onPlayPauseClick: () -> Unit,
    onMusicClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Hoist interaction source to prevent recreation on every recomposition
    val sliderInteractionSource = remember { MutableInteractionSource() }

    // Local state for smooth slider dragging (prevents jumps during drag)
    var isDragging by remember { mutableStateOf(false) }
    var localPosition by remember { mutableFloatStateOf(currentPosition) }
    // Track last scrub time for throttling (150ms)
    var lastScrubTime by remember { mutableLongStateOf(0L) }

    // Update local position when not dragging (allows external position updates)
    if (!isDragging) {
        localPosition = currentPosition
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(PlayerCardBackground)
            .padding(12.dp)
    ) {
        // Seeker row - TOP
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Play/pause button
            IconButton(
                onClick = onPlayPauseClick,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) stringResource(R.string.editor_pause) else stringResource(R.string.editor_play),
                    tint = TextPrimary,
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Slider with smooth dragging and real-time scrubbing
            Slider(
                value = localPosition.coerceIn(0f, 1f),
                onValueChange = { newValue ->
                    if (!isDragging) {
                        isDragging = true
                        onSeekStart() // Pause playback when starting to drag
                    }
                    localPosition = newValue

                    // Throttled scrubbing - show preview while dragging (150ms intervals)
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastScrubTime >= 150L) {
                        lastScrubTime = currentTime
                        onScrub(newValue) // Real-time preview during drag
                    }
                },
                onValueChangeFinished = {
                    isDragging = false
                    onSeek(localPosition) // Seek when user releases
                    onSeekEnd() // Resume playback
                },
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(
                    thumbColor = TextPrimary,
                    activeTrackColor = TextPrimary,
                    inactiveTrackColor = Gray500,
                    activeTickColor = Color.Transparent,
                    inactiveTickColor = Color.Transparent
                ),
                thumb = {
                    SliderDefaults.Thumb(
                        interactionSource = sliderInteractionSource,
                        thumbSize = androidx.compose.ui.unit.DpSize(18.dp, 18.dp),
                        colors = SliderDefaults.colors(thumbColor = TextPrimary)
                    )
                },
                track = { sliderState ->
                    SliderDefaults.Track(
                        sliderState = sliderState,
                        modifier = Modifier.height(4.dp),
                        colors = SliderDefaults.colors(
                            activeTrackColor = TextPrimary,
                            inactiveTrackColor = Gray500,
                            activeTickColor = Color.Transparent,
                            inactiveTickColor = Color.Transparent
                        ),
                        drawStopIndicator = null
                    )
                }
            )

            Spacer(modifier = Modifier.width(8.dp))

            // Duration
            Text(
                text = duration,
                fontSize = 13.sp,
                color = TextSecondary,
                modifier = Modifier.width(36.dp)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Separator line
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Color.White.copy(alpha = 0.1f))
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Song info row - BOTTOM (clickable)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickableSingle(onClick = onMusicClick)
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Album cover thumbnail
            AppAsyncImage(
                imageUrl = coverUrl,
                contentDescription = songName,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
            )

            Spacer(modifier = Modifier.width(12.dp))

            // Song name
            Text(
                text = songName,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.width(8.dp))

            // Expand icon - indicates clickable
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = stringResource(R.string.editor_change_music),
                tint = TextSecondary,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
