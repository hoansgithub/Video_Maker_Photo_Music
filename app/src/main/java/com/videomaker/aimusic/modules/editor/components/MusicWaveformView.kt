package com.videomaker.aimusic.modules.editor.components

import android.graphics.Paint as FrameworkPaint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.videomaker.aimusic.ui.theme.Gray500
import com.videomaker.aimusic.ui.theme.TextPrimary
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sin

/**
 * MusicWaveformView - Waveform visualization with two draggable trim handles
 *
 * Features:
 * - Waveform amplitude visualization (generated from audio samples or placeholder)
 * - Two draggable handles (start and end) to select middle portion
 * - Minimum 5-second gap constraint between handles
 * - Playhead indicator showing current playback position
 * - Real-time preview during drag
 *
 * @param songDurationMs Total duration of the song in milliseconds
 * @param trimStartMs Current trim start position in milliseconds
 * @param trimEndMs Current trim end position in milliseconds
 * @param currentPositionMs Current playback position in milliseconds (for playhead)
 * @param waveformData List of amplitude values (0.0 to 1.0), or null for placeholder
 * @param onTrimChange Callback when trim positions change (during drag)
 * @param onDragStateChange Callback when drag state changes (true = dragging, false = released)
 * @param minTrimDurationMs Minimum allowed gap between handles (default 5 seconds)
 */
@Composable
fun MusicWaveformView(
    songDurationMs: Long,
    trimStartMs: Long,
    trimEndMs: Long,
    currentPositionMs: Long,
    waveformData: List<Float>? = null,
    onTrimChange: (startMs: Long, endMs: Long) -> Unit,
    onDragStateChange: (Boolean) -> Unit = {},
    minTrimDurationMs: Long = 5000L,
    modifier: Modifier = Modifier
) {
    // Track gesture state to avoid handle jumping when both handles are close.
    var activeDragHandle by remember { mutableStateOf<DragHandle?>(null) }
    var pendingDirectionSelection by remember { mutableStateOf(false) }
    var accumulatedDragX by remember { mutableFloatStateOf(0f) }
    var localTrimStart by remember(trimStartMs) { mutableFloatStateOf(trimStartMs.toFloat()) }
    var localTrimEnd by remember(trimEndMs) { mutableFloatStateOf(trimEndMs.toFloat()) }

    // Use provided waveform data or empty list (will show loading indicator)
    val waveform = waveformData ?: emptyList()
    val density = LocalDensity.current
    val labelTextSizePx = with(density) { 11.sp.toPx() }
    val labelBaselinePx = with(density) { 16.dp.toPx() }
    val labelHorizontalPaddingPx = with(density) { 8.dp.toPx() }
    val touchTargetPx = with(density) { 80.dp.toPx() }
    val ambiguityThresholdPx = with(density) { 12.dp.toPx() }
    val directionCommitThresholdPx = with(density) { 4.dp.toPx() }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(120.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = 0.3f))
            .pointerInput(songDurationMs, minTrimDurationMs, touchTargetPx, ambiguityThresholdPx, directionCommitThresholdPx) {
                detectDragGestures(
                    onDragStart = { offset ->
                        val width = size.width.toFloat()
                        if (width <= 0f || songDurationMs <= 0L) {
                            activeDragHandle = null
                            pendingDirectionSelection = false
                            accumulatedDragX = 0f
                            return@detectDragGestures
                        }
                        val tapX = offset.x
                        val startHandleX = (localTrimStart / songDurationMs) * width
                        val endHandleX = (localTrimEnd / songDurationMs) * width

                        accumulatedDragX = 0f
                        when (
                            resolveDragStartSelection(
                                tapX = tapX,
                                startHandleX = startHandleX,
                                endHandleX = endHandleX,
                                touchTargetPx = touchTargetPx,
                                ambiguityThresholdPx = ambiguityThresholdPx
                            )
                        ) {
                            is DragStartSelection.Start -> {
                                activeDragHandle = DragHandle.START
                                pendingDirectionSelection = false
                                onDragStateChange(true)
                            }
                            is DragStartSelection.End -> {
                                activeDragHandle = DragHandle.END
                                pendingDirectionSelection = false
                                onDragStateChange(true)
                            }
                            DragStartSelection.Ambiguous -> {
                                activeDragHandle = null
                                pendingDirectionSelection = true
                            }
                            DragStartSelection.None -> {
                                activeDragHandle = null
                                pendingDirectionSelection = false
                            }
                        }
                    },
                    onDrag = { change, dragAmount ->
                        val width = size.width.toFloat()
                        if (width <= 0f || songDurationMs <= 0L) {
                            change.consume()
                            return@detectDragGestures
                        }

                        if (pendingDirectionSelection && activeDragHandle == null) {
                            accumulatedDragX += dragAmount.x
                            resolveHandleByDragDirection(
                                accumulatedDx = accumulatedDragX,
                                directionCommitThresholdPx = directionCommitThresholdPx
                            )?.let { committedHandle ->
                                activeDragHandle = committedHandle
                                pendingDirectionSelection = false
                                onDragStateChange(true)
                            }
                        }

                        if (activeDragHandle == null) {
                            change.consume()
                            return@detectDragGestures
                        }

                        val deltaMs = (dragAmount.x / width) * songDurationMs

                        when (activeDragHandle) {
                            DragHandle.START -> {
                                val newStart = (localTrimStart + deltaMs)
                                    .coerceIn(0f, localTrimEnd - minTrimDurationMs)
                                localTrimStart = newStart
                                onTrimChange(newStart.toLong(), localTrimEnd.toLong())
                            }
                            DragHandle.END -> {
                                val newEnd = (localTrimEnd + deltaMs)
                                    .coerceIn(localTrimStart + minTrimDurationMs, songDurationMs.toFloat())
                                localTrimEnd = newEnd
                                onTrimChange(localTrimStart.toLong(), newEnd.toLong())
                            }
                            null -> {} // Not dragging a handle
                        }
                        change.consume()
                    },
                    onDragEnd = {
                        if (activeDragHandle != null) {
                            onDragStateChange(false) // Triggers auto-play
                        }
                        activeDragHandle = null
                        pendingDirectionSelection = false
                        accumulatedDragX = 0f
                    },
                    onDragCancel = {
                        if (activeDragHandle != null) {
                            onDragStateChange(false)
                        }
                        activeDragHandle = null
                        pendingDirectionSelection = false
                        accumulatedDragX = 0f
                    }
                )
            }
            .pointerInput(songDurationMs) {
                // Allow tapping on waveform to seek (future enhancement)
                detectTapGestures { offset ->
                    // Could add seek-to-position feature here
                }
            }
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val width = size.width
            val height = size.height
            val centerY = height / 2f

            // Calculate positions
            val startX = (localTrimStart / songDurationMs) * width
            val endX = (localTrimEnd / songDurationMs) * width
            val playheadX = (currentPositionMs / songDurationMs.toFloat()) * width
            val startLabel = "${formatMusicTrimTime(localTrimStart.toLong())}"
            val endLabel = "${formatMusicTrimTime(localTrimEnd.toLong())}"

            if (waveform.isEmpty()) {
                // Show simple loading indicator - just a horizontal line
                drawLine(
                    color = Gray500.copy(alpha = 0.5f),
                    start = Offset(0f, centerY),
                    end = Offset(width, centerY),
                    strokeWidth = 2f,
                    cap = StrokeCap.Round
                )
            } else {
                // Draw waveform
                val barWidth = width / waveform.size.toFloat()
                waveform.forEachIndexed { index, amplitude ->
                    val x = index * barWidth
                    val barHeight = amplitude * (height * 0.7f) // 70% of container height

                    // Determine color based on whether this bar is in the selected region
                    val color = if (x >= startX && x <= endX) {
                        TextPrimary.copy(alpha = 0.9f)
                    } else {
                        Gray500.copy(alpha = 0.3f)
                    }

                    // Draw waveform bar (vertical line centered)
                    drawLine(
                        color = color,
                        start = Offset(x, centerY - barHeight / 2),
                        end = Offset(x, centerY + barHeight / 2),
                        strokeWidth = max(barWidth * 0.8f, 2f),
                        cap = StrokeCap.Round
                    )
                }
            }

            // Draw selection overlay (semi-transparent area)
            drawRect(
                color = TextPrimary.copy(alpha = 0.1f),
                topLeft = Offset(startX, 0f),
                size = Size(endX - startX, height)
            )

            // Draw start handle
            drawHandle(
                x = startX,
                height = height,
                color = TextPrimary,
                isLeft = true
            )

            // Draw end handle
            drawHandle(
                x = endX,
                height = height,
                color = TextPrimary,
                isLeft = false
            )

            val labelPaint = FrameworkPaint().apply {
                isAntiAlias = true
                color = Color.White.toArgb()
                textSize = labelTextSizePx
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }

            drawHandleLabel(
                text = startLabel,
                anchorX = startX,
                maxWidth = width,
                baselineY = labelBaselinePx,
                horizontalPadding = labelHorizontalPaddingPx,
                side = LabelSide.LEFT_OF_HANDLE,
                paint = labelPaint
            )
            drawHandleLabel(
                text = endLabel,
                anchorX = endX,
                maxWidth = width,
                baselineY = labelBaselinePx,
                horizontalPadding = labelHorizontalPaddingPx,
                side = LabelSide.RIGHT_OF_HANDLE,
                paint = labelPaint
            )

            // Draw playhead indicator (thin vertical line)
            if (playheadX in startX..endX) {
                drawLine(
                    color = Color.White,
                    start = Offset(playheadX, 0f),
                    end = Offset(playheadX, height),
                    strokeWidth = 3f,
                    cap = StrokeCap.Round
                )

                // Draw playhead circle at top
                drawCircle(
                    color = Color.White,
                    radius = 6f,
                    center = Offset(playheadX, 8f)
                )
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawHandleLabel(
    text: String,
    anchorX: Float,
    maxWidth: Float,
    baselineY: Float,
    horizontalPadding: Float,
    side: LabelSide,
    paint: FrameworkPaint
) {
    val textWidth = paint.measureText(text)
    val maxX = (maxWidth - textWidth - horizontalPadding).coerceAtLeast(horizontalPadding)
    val rawTextX = when (side) {
        LabelSide.LEFT_OF_HANDLE -> anchorX - textWidth - horizontalPadding
        LabelSide.RIGHT_OF_HANDLE -> anchorX + horizontalPadding
    }
    val textX = rawTextX.coerceIn(horizontalPadding, maxX)
    drawContext.canvas.nativeCanvas.drawText(text, textX, baselineY, paint)
}

private enum class LabelSide {
    LEFT_OF_HANDLE,
    RIGHT_OF_HANDLE
}

/**
 * Helper function to draw a trim handle (more visible version)
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawHandle(
    x: Float,
    height: Float,
    color: Color,
    isLeft: Boolean
) {
    val handleWidth = 8f
    val gripWidth = 32f
    val gripHeight = 80f

    // Draw white outline for better visibility
    drawLine(
        color = Color.White,
        start = Offset(x, 0f),
        end = Offset(x, height),
        strokeWidth = handleWidth + 4f,
        cap = StrokeCap.Square
    )

    // Draw vertical line (full height)
    drawLine(
        color = color,
        start = Offset(x, 0f),
        end = Offset(x, height),
        strokeWidth = handleWidth,
        cap = StrokeCap.Square
    )

    // Draw grip indicator (rounded rect at center)
    val gripY = (height - gripHeight) / 2f
    val gripPath = Path().apply {
        val gripX = if (isLeft) x + 2f else x - gripWidth - 2f
        addRoundRect(
            androidx.compose.ui.geometry.RoundRect(
                left = gripX,
                top = gripY,
                right = gripX + gripWidth,
                bottom = gripY + gripHeight,
                radiusX = 8f,
                radiusY = 8f
            )
        )
    }

    // Draw white outline for grip
    drawPath(
        path = gripPath,
        color = Color.White,
        style = Stroke(width = 3f)
    )

    // Draw grip background
    drawPath(
        path = gripPath,
        color = color,
        style = Fill
    )

    // Draw grip lines (visual indicator for dragging)
    val lineSpacing = 8f
    for (i in 0..2) {
        val lineY = gripY + gripHeight / 2f + (i - 1) * lineSpacing
        val lineStartX = if (isLeft) x + 6f else x - gripWidth + 2f
        val lineEndX = lineStartX + gripWidth - 8f

        drawLine(
            color = Color.White,
            start = Offset(lineStartX, lineY),
            end = Offset(lineEndX, lineY),
            strokeWidth = 3f,
            cap = StrokeCap.Round
        )
    }
}

/**
 * Generates placeholder waveform data (sine wave pattern)
 * Used when actual audio sample data is not available
 */
private fun generatePlaceholderWaveform(sampleCount: Int): List<Float> {
    return List(sampleCount) { index ->
        // Create varying amplitude using sine waves at different frequencies
        val baseWave = sin(index * 0.1).toFloat() * 0.5f + 0.5f // Slow wave
        val detailWave = sin(index * 0.5).toFloat() * 0.3f // Fast wave for detail
        val variation = (kotlin.random.Random.nextFloat() - 0.5f) * 0.2f // Random variation

        ((baseWave + detailWave + variation + 0.3f) * 0.8f)
            .coerceIn(0.1f, 1.0f)
    }
}

/**
 * Enum for tracking which handle is being dragged
 */
internal fun resolveDragStartSelection(
    tapX: Float,
    startHandleX: Float,
    endHandleX: Float,
    touchTargetPx: Float,
    ambiguityThresholdPx: Float
): DragStartSelection {
    val distanceToStart = abs(tapX - startHandleX)
    val distanceToEnd = abs(tapX - endHandleX)
    val canDragStart = distanceToStart <= touchTargetPx
    val canDragEnd = distanceToEnd <= touchTargetPx
    if (!canDragStart && !canDragEnd) {
        return DragStartSelection.None
    }
    if (canDragStart && canDragEnd) {
        val isAmbiguous = abs(distanceToStart - distanceToEnd) <= ambiguityThresholdPx
        if (isAmbiguous) {
            return DragStartSelection.Ambiguous
        }
        return if (distanceToStart <= distanceToEnd) {
            DragStartSelection.Start
        } else {
            DragStartSelection.End
        }
    }
    return if (canDragStart) DragStartSelection.Start else DragStartSelection.End
}

internal fun resolveHandleByDragDirection(
    accumulatedDx: Float,
    directionCommitThresholdPx: Float
): DragHandle? {
    if (abs(accumulatedDx) < directionCommitThresholdPx) {
        return null
    }
    return if (accumulatedDx < 0f) DragHandle.START else DragHandle.END
}

internal sealed interface DragStartSelection {
    data object Start : DragStartSelection
    data object End : DragStartSelection
    data object Ambiguous : DragStartSelection
    data object None : DragStartSelection
}

internal enum class DragHandle {
    START, END
}
