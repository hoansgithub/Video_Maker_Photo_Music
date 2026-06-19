package com.videomaker.aimusic.modules.editor.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.videomaker.aimusic.ui.components.AppAsyncImage
import com.videomaker.aimusic.ui.components.ModifierExtension.clickableSingle
import com.videomaker.aimusic.ui.components.PlayingAnimationBars
import com.videomaker.aimusic.ui.components.ShimmerPlaceholder
import com.videomaker.aimusic.ui.theme.Neutral_N700
import com.videomaker.aimusic.ui.theme.Primary
import com.videomaker.aimusic.ui.theme.TextTertiary
import kotlin.random.Random

/**
 * A hook segment of a song (a highlighted region on the progress bar / waveform).
 * The app will supply these from real beat-sync data; until then a single segment is
 * derived from [MusicSong.hookStartTimeMs].
 */
@Immutable
data class MusicHookSegment(val startMs: Long, val endMs: Long)

/**
 * Which UI element changed the song start time — drives the song_starttime_change location.
 * [DURATION_BAR]: the slim hook progress bar (tap or drag). [DRAG_BAR]: the waveform scrubber.
 */
enum class StartTimeChangeSource { DURATION_BAR, DRAG_BAR }

/**
 * The floating "now selecting" music player shown at the bottom of the music search sheet.
 *
 * Layout (Figma #7):
 *  - Header: cover + name (+ equalizer bars while playing) + artist, and a round confirm (✓) button
 *  - Divider
 *  - Progress row: elapsed time + [HookProgressBar] + play/pause button
 *  - [MusicScrubber]: a fixed center frame over a draggable, video-duration-scaled waveform track
 *
 * @param songDurationMs full song length (ms); drives the progress bar + scrubber track width
 * @param videoDurationMs current video length (ms); the scrubber frame spans exactly this much song
 * @param selectionStartMs start (ms) of the song portion currently under the frame
 */
@Composable
fun MusicSelectionPlayer(
    coverUrl: String,
    name: String,
    artist: String,
    isPlaying: Boolean,
    positionMs: Long,
    songDurationMs: Long,
    videoDurationMs: Long,
    selectionStartMs: Long,
    hookSegments: List<MusicHookSegment>,
    waveform: List<Float>,
    onPlayPauseClick: () -> Unit,
    onConfirmClick: () -> Unit,
    onSelectionChange: (Long) -> Unit,
    modifier: Modifier = Modifier,
    // Fired once per gesture (drag end / tap) so the caller can emit song_starttime_change.
    onStartTimeCommit: (StartTimeChangeSource) -> Unit = {}
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
    ) {

        Spacer(
            modifier = Modifier
                .matchParentSize()
                .shadow(
                    elevation = 12.dp,
                    shape = RoundedCornerShape(20.dp),
                    ambientColor = Color.Black.copy(alpha = 0.24f),
                    spotColor = Color.Black.copy(alpha = 0.24f)
                )
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF575757))
                .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(20.dp)
                )
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .padding(horizontal = 10.dp, vertical = 12.dp)
        ) {
            // Header: cover + title/artist + confirm
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppAsyncImage(
                    imageUrl = coverUrl,
                    contentDescription = name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
                Spacer(modifier = Modifier.width(8.dp))
                // Song name + artist
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Song title row with playing animation bars
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = name,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (isPlaying) {
                            Spacer(modifier = Modifier.width(8.dp))
                            PlayingAnimationBars(
                                barColor = Primary,
                                barWidth = 3.dp,
                                maxBarHeight = 16.dp,
                                containerSize = 18.dp
                            )
                        }
                    }
                    if (artist.isNotBlank()) {
                        Text(
                            text = artist,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.W400,
                            color = TextTertiary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Primary)
                        .clickableSingle { onConfirmClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(21.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
            Spacer(modifier = Modifier.height(10.dp))

            // Progress row: time + hook progress bar + play/pause
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Shows the start time of the currently-selected frame (does not animate
                // with playback — only changes when the frame selection changes).
                Text(
                    text = formatPlayerTime(videoDurationMs),
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.W500
                )
                Spacer(modifier = Modifier.width(12.dp))
                HookProgressBar(
                    selectionStartMs = selectionStartMs,
                    videoDurationMs = videoDurationMs,
                    songDurationMs = songDurationMs,
                    hookSegments = hookSegments,
                    onSeek = { newStart -> onSelectionChange(newStart) },
                    onSeekCommit = { onStartTimeCommit(StartTimeChangeSource.DURATION_BAR) },
                    modifier = Modifier
                        .weight(1f)
                        .height(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Neutral_N700)
                        .clickableSingle { onPlayPauseClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            MusicScrubber(
                waveform = waveform,
                songDurationMs = songDurationMs,
                videoDurationMs = videoDurationMs,
                selectionStartMs = selectionStartMs,
                positionMs = positionMs,
                onSelectionChange = onSelectionChange,
                onDragCommit = { onStartTimeCommit(StartTimeChangeSource.DRAG_BAR) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
            )
        }
    }
}

/**
 * Shimmer skeleton matching [MusicSelectionPlayer]'s layout, shown while a newly-selected
 * song's preview is still loading. Requires a [ProvideShimmerEffect] ancestor (the sheet
 * already provides one).
 */
@Composable
fun MusicSelectionPlayerSkeleton(
    modifier: Modifier = Modifier
) {
    // Mirrors MusicSelectionPlayer 1:1 (same wrapper, paddings and element sizes) so the
    // card does not change size / position when the swap happens — only the content fades.
    Box(
        modifier = modifier.fillMaxWidth()
    ) {
        Spacer(
            modifier = Modifier
                .matchParentSize()
                .shadow(
                    elevation = 12.dp,
                    shape = RoundedCornerShape(20.dp),
                    ambientColor = Color.Black.copy(alpha = 0.24f),
                    spotColor = Color.Black.copy(alpha = 0.24f)
                )
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF575757))
                .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(20.dp)
                )
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .padding(horizontal = 10.dp, vertical = 12.dp)
        ) {
            // Header: cover + title/artist + confirm (matches player sizes)
            Row(verticalAlignment = Alignment.CenterVertically) {
                ShimmerPlaceholder(
                    modifier = Modifier.size(36.dp),
                    cornerRadius = 8.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ShimmerPlaceholder(
                        modifier = Modifier
                            .fillMaxWidth(0.55f)
                            .height(15.dp),
                        cornerRadius = 4.dp
                    )
                    ShimmerPlaceholder(
                        modifier = Modifier
                            .fillMaxWidth(0.3f)
                            .height(12.dp),
                        cornerRadius = 4.dp
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                ShimmerPlaceholder(
                    modifier = Modifier.size(36.dp),
                    cornerRadius = 18.dp
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
            Spacer(modifier = Modifier.height(10.dp))

            // Progress row: time + bar + play/pause (matches player sizes)
            Row(verticalAlignment = Alignment.CenterVertically) {
                ShimmerPlaceholder(
                    modifier = Modifier
                        .width(28.dp)
                        .height(12.dp),
                    cornerRadius = 4.dp
                )
                Spacer(modifier = Modifier.width(12.dp))
                ShimmerPlaceholder(
                    modifier = Modifier
                        .weight(1f)
                        .height(6.dp),
                    cornerRadius = 4.dp
                )
                Spacer(modifier = Modifier.width(12.dp))
                ShimmerPlaceholder(
                    modifier = Modifier.size(36.dp),
                    cornerRadius = 18.dp
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Waveform scrubber (matches player's 40.dp height)
            ShimmerPlaceholder(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp),
                cornerRadius = 10.dp
            )
        }
    }
}

/**
 * Slim progress bar (Figma #8). Reflects the currently-selected frame:
 *  - gray full track
 *  - Primary (green) pill spanning each hook segment [startMs, endMs] (start → +15s)
 *  - white window = the currently-selected video portion [selectionStart, selectionStart+videoDuration]
 *
 * Interaction: tap jumps to a point (white window glides there smoothly), and the bar can be
 * dragged horizontally — the white window follows the finger and stays where released.
 * [onSeek] fires continuously with the new start; [onSeekCommit] fires once per gesture.
 */
@Composable
private fun HookProgressBar(
    selectionStartMs: Long,
    videoDurationMs: Long,
    songDurationMs: Long,
    hookSegments: List<MusicHookSegment>,
    modifier: Modifier = Modifier,
    onSeek: (Long) -> Unit = {},
    onSeekCommit: () -> Unit = {}
) {
    val duration = songDurationMs.coerceAtLeast(1L).toFloat()
    // Largest start so the [start, start+videoDuration] window still fits in the song.
    val maxStartMs = (songDurationMs - videoDurationMs).coerceAtLeast(0L)
    val canSeek = songDurationMs > 0L && videoDurationMs > 0L && maxStartMs > 0L

    val scope = rememberCoroutineScope()
    // Drawn position (ms) of the white window's start — animated for smooth gliding on tap /
    // external changes, snapped 1:1 to the finger while dragging.
    val animatedStartMs = remember { Animatable(selectionStartMs.toFloat()) }
    var isDragging by remember { mutableStateOf(false) }

    // Glide to the selection whenever it changes outside of an active drag (tap, song change,
    // or the waveform scrubber moving it).
    LaunchedEffect(selectionStartMs, isDragging) {
        if (!isDragging && animatedStartMs.value != selectionStartMs.toFloat()) {
            animatedStartMs.animateTo(
                targetValue = selectionStartMs.toFloat(),
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
        }
    }

    fun xToStart(x: Float, w: Float): Long =
        (x / w * duration).toLong().coerceIn(0L, maxStartMs)

    Canvas(
        modifier = modifier
            .pointerInput(songDurationMs, videoDurationMs, maxStartMs) {
                if (!canSeek) return@pointerInput
                detectTapGestures { offset ->
                    val w = size.width.toFloat()
                    if (w <= 0f) return@detectTapGestures
                    onSeek(xToStart(offset.x, w))
                    onSeekCommit()
                }
            }
            .pointerInput(songDurationMs, videoDurationMs, maxStartMs) {
                if (!canSeek) return@pointerInput
                var changed = false
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        isDragging = true
                        changed = false
                        val w = size.width.toFloat()
                        if (w > 0f) {
                            val s = xToStart(offset.x, w)
                            scope.launch { animatedStartMs.snapTo(s.toFloat()) }
                            onSeek(s)
                            changed = true
                        }
                    },
                    onDragEnd = {
                        isDragging = false
                        if (changed) onSeekCommit()
                    },
                    onDragCancel = { isDragging = false }
                ) { change, _ ->
                    val w = size.width.toFloat()
                    if (w <= 0f) return@detectHorizontalDragGestures
                    change.consume()
                    val s = xToStart(change.position.x, w)
                    scope.launch { animatedStartMs.snapTo(s.toFloat()) }
                    onSeek(s)
                    changed = true
                }
            }
    ) {
        val w = size.width
        val centerY = size.height / 2f
        val trackHeight = 4.dp.toPx()

        fun msToX(ms: Float): Float = (ms / duration).coerceIn(0f, 1f) * w

        // Full track
        drawLine(
            color = Color.White.copy(alpha = 0.25f),
            start = Offset(0f, centerY),
            end = Offset(w, centerY),
            strokeWidth = trackHeight,
            cap = StrokeCap.Round
        )
        // Selected video window (white) — uses the animated start for smooth movement
        if (videoDurationMs > 0L) {
            val drawnStart = animatedStartMs.value
            val startX = msToX(drawnStart)
            val endX = msToX(drawnStart + videoDurationMs)
            drawLine(
                color = Color.White,
                start = Offset(startX, centerY),
                end = Offset(endX.coerceAtLeast(startX + trackHeight), centerY),
                strokeWidth = trackHeight + 2.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
        // Hook points (green capsule dots) — drawn last so they render on top of everything
        val capsuleWidth = 8.dp.toPx()
        hookSegments.forEach { hook ->
            val cx = msToX(hook.startMs.toFloat())
            drawLine(
                color = Primary,
                start = Offset((cx - capsuleWidth / 2f).coerceAtLeast(0f), centerY),
                end = Offset((cx + capsuleWidth / 2f).coerceAtMost(w), centerY),
                strokeWidth = trackHeight,
                cap = StrokeCap.Round
            )
        }
    }
}

/**
 * Instagram-style scrubber: a fixed [frameWidth] window centered horizontally; the waveform
 * track underneath is scaled so that exactly [videoDurationMs] of song spans the frame, and is
 * dragged left/right to choose which song portion sits inside the frame (the selection).
 *
 * The frame never moves — only the track does. The portion under the frame is the selection.
 */
@Composable
fun MusicScrubber(
    waveform: List<Float>,
    songDurationMs: Long,
    videoDurationMs: Long,
    selectionStartMs: Long,
    positionMs: Long,
    onSelectionChange: (Long) -> Unit,
    modifier: Modifier = Modifier,
    frameWidth: androidx.compose.ui.unit.Dp = 100.dp,
    // Fired once when a drag gesture ends and the start actually moved.
    onDragCommit: () -> Unit = {}
) {
    val density = LocalDensity.current
    val frameWidthPx = with(density) { frameWidth.toPx() }

    // ms per px so the frame spans exactly the video duration.
    val pxPerMs = if (videoDurationMs > 0L) frameWidthPx / videoDurationMs else 0f
    val maxStartMs = (songDurationMs - videoDurationMs).coerceAtLeast(0L)
    val canScrub = pxPerMs > 0f && songDurationMs > 0L && maxStartMs > 0L

    // Tracks whether the in-progress drag actually changed the start, so we only emit a
    // commit (analytics) once per meaningful gesture.
    var draggedThisGesture by remember { mutableStateOf(false) }
    val draggable = rememberDraggableState { delta ->
        if (canScrub) {
            // Dragging the track right (+delta) reveals earlier song → start decreases.
            val deltaMs = (delta / pxPerMs).toLong()
            val newStart = (selectionStartMs - deltaMs).coerceIn(0L, maxStartMs)
            if (newStart != selectionStartMs) {
                draggedThisGesture = true
                onSelectionChange(newStart)
            }
        }
    }

    Box(
        modifier = modifier.draggable(
            state = draggable,
            orientation = Orientation.Horizontal,
            enabled = canScrub,
            onDragStarted = { draggedThisGesture = false },
            onDragStopped = {
                if (draggedThisGesture) {
                    onDragCommit()
                    draggedThisGesture = false
                }
            }
        )
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val w = size.width
            val h = size.height
            val centerY = h / 2f
            if (waveform.isEmpty()) return@Canvas

            val frameLeft = (w - frameWidthPx) / 2f
            val frameRight = frameLeft + frameWidthPx
            // trackOffset so that selectionStartMs sits at the left edge of the frame.
            val trackOffset = frameLeft - selectionStartMs * pxPerMs
            val trackWidth = if (pxPerMs > 0f) songDurationMs * pxPerMs else w

            // Fixed bar geometry so the waveform looks identical no matter how short or long
            // the video is: every bar is the same width with a constant 4.dp gap between them.
            val barW = 3.dp.toPx()
            val barGap = 4.dp.toPx()
            val pitch = barW + barGap
            // How many evenly-spaced bars fit across the full track at the fixed pitch.
            val barCount = (trackWidth / pitch).toInt().coerceAtLeast(1)

            // Green fills left→right with playback progress inside the frame (Figma #11).
            for (b in 0 until barCount) {
                // Center of this bar slot, mapped to its position along the track.
                val barFrac = (b + 0.5f) / barCount
                val x = trackOffset + b * pitch + barW / 2f
                if (x < -pitch || x > w + pitch) continue
                // Sample the source waveform proportionally so its shape is preserved
                // regardless of how many bars we end up drawing.
                val sampleIndex = (barFrac * waveform.size).toInt().coerceIn(0, waveform.size - 1)
                val amp = waveform[sampleIndex]
                val timeMs = barFrac * songDurationMs
                val inFrame = x in frameLeft..frameRight
                val played = timeMs in selectionStartMs.toFloat()..positionMs.toFloat()
                val color = when {
                    inFrame && played -> Primary
                    inFrame -> Color.White
                    else -> Color.White.copy(alpha = 0.22f)
                }
                val barHeight = (amp.coerceIn(0.05f, 1f)) * h * 0.8f
                drawLine(
                    color = color,
                    start = Offset(x, centerY - barHeight / 2f),
                    end = Offset(x, centerY + barHeight / 2f),
                    strokeWidth = barW,
                    cap = StrokeCap.Round
                )
            }

            // Fixed selection frame (white rounded border)
            val stroke = 3.dp.toPx()
            val radius = 10.dp.toPx()
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(frameLeft, 0f + stroke / 2f),
                size = Size(frameWidthPx, h - stroke),
                cornerRadius = CornerRadius(radius, radius),
                style = Stroke(width = stroke)
            )
        }
    }
}

/** Stable placeholder waveform (used until real amplitude data is supplied). */
fun placeholderWaveform(sampleCount: Int = 64, seed: Long = 0L): List<Float> {
    val rng = Random(seed)
    return List(sampleCount) { (0.25f + rng.nextFloat() * 0.75f).coerceIn(0.1f, 1f) }
}

private fun formatPlayerTime(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0L)
    val m = totalSec / 60
    val s = totalSec % 60
    return "%d:%02d".format(m, s)
}
