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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import com.videomaker.aimusic.R
import com.videomaker.aimusic.ui.components.AppAsyncImage
import com.videomaker.aimusic.ui.components.PlayingAnimationBars
import com.videomaker.aimusic.ui.theme.Gray500
import com.videomaker.aimusic.ui.theme.Primary
import com.videomaker.aimusic.ui.theme.PlayerCardBorder
import com.videomaker.aimusic.ui.theme.PlayerCardGlass
import com.videomaker.aimusic.ui.theme.PlayerCardInnerGlow
import com.videomaker.aimusic.ui.theme.PlayerCardTopHighlight
import com.videomaker.aimusic.ui.theme.TextPrimary
import com.videomaker.aimusic.ui.theme.TextSecondary
import com.videomaker.aimusic.ui.theme.TextTertiary
import com.videomaker.aimusic.ui.theme.White10

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MusicSection(
    songName: String,
    artistName: String,
    coverUrl: String,
    duration: String,
    currentPosition: Float,
    isPlaying: Boolean,
    onSeek: (Float) -> Unit,
    onScrub: (Float) -> Unit = {},
    onSeekStart: () -> Unit = {},
    onSeekEnd: () -> Unit = {},
    onPlayPauseClick: () -> Unit,
    onMusicSelectorClick: () -> Unit = {},
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

    val glassShape = RoundedCornerShape(16.dp)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(glassShape)
            .background(PlayerCardGlass)
            .border(1.dp, PlayerCardBorder, glassShape)
            .drawWithContent {
                drawContent()
                val cornerPx = 16.dp.toPx()
                val blurPx = 12.dp.toPx()
                // Inset white glow — top (offset 2dp down per box-shadow y-offset)
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(PlayerCardInnerGlow, Color.Transparent),
                        startY = 2.dp.toPx(),
                        endY = blurPx
                    ),
                    size = Size(size.width, blurPx)
                )
                // Inset white glow — bottom
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color.Transparent, PlayerCardInnerGlow),
                        startY = size.height - blurPx,
                        endY = size.height
                    ),
                    topLeft = Offset(0f, size.height - blurPx),
                    size = Size(size.width, blurPx)
                )
                // Inset white glow — left
                drawRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(PlayerCardInnerGlow, Color.Transparent),
                        startX = 0f,
                        endX = blurPx
                    ),
                    size = Size(blurPx, size.height)
                )
                // Inset white glow — right
                drawRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(Color.Transparent, PlayerCardInnerGlow),
                        startX = size.width - blurPx,
                        endX = size.width
                    ),
                    topLeft = Offset(size.width - blurPx, 0f),
                    size = Size(blurPx, size.height)
                )
                // Top highlight border (brighter than side borders)
                drawRoundRect(
                    color = PlayerCardTopHighlight,
                    cornerRadius = CornerRadius(cornerPx),
                    style = Stroke(width = 1.dp.toPx()),
                    size = Size(size.width, 1.dp.toPx())
                )
            }
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        // Song info row - TOP (clickable to open music selector)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onMusicSelectorClick)
                .padding(start = 8.dp, top = 2.dp, bottom = 2.dp),
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

            Spacer(modifier = Modifier.width(10.dp))

            // Song name + artist
            Column(modifier = Modifier.weight(1f)) {
                // Song title row with playing animation bars
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = songName,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (isPlaying) {
                        Spacer(modifier = Modifier.width(6.dp))
                        PlayingAnimationBars(
                            barColor = Primary,
                            barWidth = 3.dp,
                            maxBarHeight = 14.dp,
                            containerSize = 16.dp
                        )
                    }
                }
                if (artistName.isNotBlank()) {
                    Text(
                        text = artistName,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Normal,
                        color = TextTertiary,
                        lineHeight = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(4.dp))

            // Chevron arrow
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = TextPrimary,
                modifier = Modifier.size(32.dp)
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Separator line
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(White10)
        )

        Spacer(modifier = Modifier.height(6.dp))

        // Duration label - top left, padded to align with slider track
        Text(
            text = duration,
            fontSize = 12.sp,
            color = TextPrimary,
            modifier = Modifier.padding(start = 8.dp)
        )

        Spacer(modifier = Modifier.height(2.dp))

        // Seeker row - slider + play/pause aligned horizontally
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
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

            // Play/pause button - circle shape, aligned with slider
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(White10)
                    .clickable(onClick = onPlayPauseClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) stringResource(R.string.editor_pause) else stringResource(
                        R.string.editor_play
                    ),
                    tint = TextPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayMusicSlider(
    currentPositionMs: Long,
    durationMs: Long,
    currentPosition: Float,
    isPlaying: Boolean,
    onSeek: (Float) -> Unit,
    onScrub: (Float) -> Unit = {},
    onSeekStart: () -> Unit = {},
    onSeekEnd: () -> Unit = {},
    onPlayPauseClick: () -> Unit
) {
    // Hoist interaction source to prevent recreation on every recomposition
    val sliderInteractionSource = remember { MutableInteractionSource() }

    // Local state for smooth slider dragging (prevents jumps during drag)
    var isDragging by remember { mutableStateOf(false) }
    var localPosition by remember { mutableFloatStateOf(currentPosition) }
    // Track last scrub time for throttling
    var lastScrubTime by remember { mutableLongStateOf(0L) }
    // After releasing the thumb we keep showing the dragged value until the
    // player's reported position catches up. Without this the thumb snaps back
    // to the stale position and then jumps forward once the seek lands (giật).
    var pendingSeek by remember { mutableStateOf(false) }

    // Sync external position into the slider only when the user is not dragging.
    if (!isDragging) {
        if (pendingSeek) {
            // Resume external sync once the reported position converges on the seek target.
            if (kotlin.math.abs(currentPosition - localPosition) < SLIDER_SEEK_SETTLE_THRESHOLD) {
                pendingSeek = false
                localPosition = currentPosition
            }
        } else {
            localPosition = currentPosition
        }
    }

    // Smooth running clock: the player only reports its position every ~500ms,
    // which makes a hundredths counter (and the thumb) tick in coarse jumps.
    // Interpolate locally at frame rate between those coarse updates, re-anchoring
    // whenever a fresh position arrives so it never drifts.
    var smoothMs by remember { mutableLongStateOf(currentPositionMs) }
    LaunchedEffect(isPlaying, currentPositionMs, isDragging, pendingSeek) {
        if (isDragging || pendingSeek) return@LaunchedEffect
        smoothMs = currentPositionMs
        if (!isPlaying) return@LaunchedEffect
        val anchorRealtime = android.os.SystemClock.elapsedRealtime()
        val anchorPos = currentPositionMs
        while (true) {
            withFrameMillis { }
            val elapsed = android.os.SystemClock.elapsedRealtime() - anchorRealtime
            smoothMs = if (durationMs > 0) {
                (anchorPos + elapsed).coerceIn(0L, durationMs)
            } else {
                anchorPos + elapsed
            }
        }
    }

    // Running time shown next to the slider. While the user interacts (drag or
    // settling seek) it follows the thumb; during playback it uses the smooth
    // interpolated clock; otherwise it tracks the reported playback position.
    val displayedMs = when {
        isDragging || pendingSeek -> (localPosition.coerceIn(0f, 1f) * durationMs).toLong()
        isPlaying -> smoothMs
        else -> currentPositionMs
    }

    // Thumb position: follow the finger while seeking, otherwise the smooth clock.
    val sliderValue = when {
        isDragging || pendingSeek -> localPosition
        isPlaying && durationMs > 0 -> smoothMs / durationMs.toFloat()
        else -> localPosition
    }.coerceIn(0f, 1f)

    // Seeker row - slider + play/pause aligned horizontally
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Play/pause button - circle shape, aligned with slider
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(White10)
                .clickable(onClick = onPlayPauseClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) stringResource(R.string.editor_pause) else stringResource(
                    R.string.editor_play
                ),
                tint = TextPrimary,
                modifier = Modifier.size(20.dp)
            )
        }

        // Slider with smooth dragging and real-time scrubbing
        Slider(
            value = sliderValue,
            onValueChange = { newValue ->
                if (!isDragging) {
                    isDragging = true
                    pendingSeek = false
                    onSeekStart() // Pause playback when starting to drag
                }
                localPosition = newValue

                // Throttled scrubbing - show preview frame while dragging.
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastScrubTime >= SLIDER_SCRUB_THROTTLE_MS) {
                    lastScrubTime = currentTime
                    onScrub(newValue) // Real-time preview during drag
                }
            },
            onValueChangeFinished = {
                isDragging = false
                pendingSeek = true // Hold the dragged value until the seek settles
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

        // Running time label (format m:ss.SS) - follows the thumb while seeking
        Text(
            text = formatPlaybackTime(displayedMs),
            fontSize = 10.sp,
            color = TextPrimary,
            textAlign = TextAlign.End,
            modifier = Modifier.width(40.dp)
        )
    }
}

/** Throttle interval for scrub previews while dragging the seek bar. */
private const val SLIDER_SCRUB_THROTTLE_MS = 120L

/** Slider keeps the dragged value until the reported position is within this fraction of the target. */
private const val SLIDER_SEEK_SETTLE_THRESHOLD = 0.02f

/** Formats a playback position in milliseconds as m:ss.SS (e.g. 0:07.42). */
private fun formatPlaybackTime(ms: Long): String {
    val safeMs = ms.coerceAtLeast(0L)
    val totalSeconds = safeMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val hundredths = (safeMs % 1000) / 10
    return String.format(java.util.Locale.US, "%d:%02d.%02d", minutes, seconds, hundredths)
}

@Preview(showBackground = true, backgroundColor = 0xFF1A1A1A)
@Composable
private fun MusicSectionPreview() {
    MusicSection(
        songName = "Sunflower",
        artistName = "Post Malone, Swae Lee",
        coverUrl = "",
        duration = "3:24",
        currentPosition = 0.35f,
        isPlaying = true,
        onSeek = {},
        onPlayPauseClick = {}
    )
}
