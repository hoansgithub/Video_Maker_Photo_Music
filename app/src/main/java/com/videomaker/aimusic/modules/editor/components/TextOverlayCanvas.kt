package com.videomaker.aimusic.modules.editor.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.videomaker.aimusic.R
import com.videomaker.aimusic.domain.model.TextOverlay
import com.videomaker.aimusic.domain.model.mockFontPresets
import com.videomaker.aimusic.modules.editor.EditorViewModel
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * TextOverlayCanvas - Displays and manages interactive text overlays over the player preview.
 */
@Composable
fun TextOverlayCanvas(
    viewModel: EditorViewModel,
    onDoubleTapText: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val textOverlays by viewModel.textOverlays.collectAsStateWithLifecycle()
    val selectedId by viewModel.selectedTextOverlayId.collectAsStateWithLifecycle()

    TextOverlayCanvasContent(
        textOverlays = textOverlays,
        selectedId = selectedId,
        onSelectText = viewModel::setSelectedTextOverlayId,
        onUpdateText = { id, x, y, rot, sc ->
            viewModel.updateTextOverlay(
                id = id,
                xPercentage = x,
                yPercentage = y,
                rotation = rot,
                scale = sc
            )
        },
        onRemoveText = viewModel::removeTextOverlay,
        onDoubleTapText = onDoubleTapText,
        modifier = modifier
    )
}

/**
 * TextOverlayCanvasContent - Stateless layout for text items inside the preview viewport.
 *
 * Key UX decisions:
 * - clipToBounds() clips any text that overflows the video bounds
 * - Control buttons (delete, scale handle) use inverse scale so they stay a fixed size
 * - Single tap on any overlay selects it; double tap opens the edit sheet
 * - Drag has low threshold (0.5x touchSlop) for responsive movement
 * - Scale range is 0.3f to 5.0f — user can freely resize
 * - Drag position is NOT clamped — text can be dragged partially off-screen (clipped)
 */
@Composable
fun TextOverlayCanvasContent(
    textOverlays: List<TextOverlay>,
    selectedId: String?,
    onSelectText: (String?) -> Unit,
    onUpdateText: (id: String, x: Float?, y: Float?, rot: Float?, sc: Float?) -> Unit,
    onRemoveText: (String) -> Unit,
    onDoubleTapText: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var canvasWidth by remember { mutableStateOf(0) }
    var canvasHeight by remember { mutableStateOf(0) }
    val haptic = LocalHapticFeedback.current

    Box(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds() // Clip text that overflows video bounds
            .onGloballyPositioned { coordinates ->
                canvasWidth = coordinates.size.width
                canvasHeight = coordinates.size.height
            }
    ) {
        if (canvasWidth > 0 && canvasHeight > 0) {
            textOverlays.forEach { overlay ->
                val isSelected = overlay.id == selectedId
                val fontPreset = mockFontPresets.firstOrNull { it.id == overlay.fontId }
                    ?: mockFontPresets.first()

                val currentOverlay by rememberUpdatedState(overlay)
                val currentCanvasWidth by rememberUpdatedState(canvasWidth)
                val currentCanvasHeight by rememberUpdatedState(canvasHeight)
                val currentOnUpdateText by rememberUpdatedState(onUpdateText)
                val currentOnSelectText by rememberUpdatedState(onSelectText)
                val currentOnDoubleTapText by rememberUpdatedState(onDoubleTapText)

                // Local drag offset for smooth drag performance (avoids StateFlow update per frame)
                var localDragDeltaX by remember { mutableFloatStateOf(0f) }
                var localDragDeltaY by remember { mutableFloatStateOf(0f) }
                var isDragging by remember { mutableStateOf(false) }

                // Calculate center screen coordinates based on percentages
                val centerX = overlay.xPercentage * canvasWidth
                val centerY = overlay.yPercentage * canvasHeight

                // Drag/Offset properties
                var startX by remember { mutableStateOf(0f) }
                var startY by remember { mutableStateOf(0f) }

                // Rotation and scale properties relative to center
                var startAngle by remember { mutableStateOf(0f) }
                var startDistance by remember { mutableStateOf(0f) }
                var startRotation by remember { mutableStateOf(0f) }
                var startScale by remember { mutableStateOf(1f) }

                var centerCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
                var handleCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }

                // Inverse scale factor for control buttons — keeps them constant size
                val inverseScale = if (overlay.scale > 0.01f) 1f / overlay.scale else 1f

                Box(
                    modifier = Modifier
                        .layout { measurable, constraints ->
                            // layout{} gives access to measured size — use it to center the box
                            // on (centerX, centerY) while keeping hit area aligned with visual
                            val placeable = measurable.measure(constraints)
                            layout(placeable.width, placeable.height) {
                                placeable.placeRelative(
                                    x = (centerX + localDragDeltaX - placeable.width / 2f).roundToInt(),
                                    y = (centerY + localDragDeltaY - placeable.height / 2f).roundToInt()
                                )
                            }
                        }
                        .onGloballyPositioned { coords ->
                            centerCoordinates = coords
                        }
                        .pointerInput(overlay.id) {
                            // NOTE: graphicsLayer is placed AFTER this pointerInput in the chain.
                            // This ensures touch coordinates are always in the unrotated/unscaled
                            // layout space (canvas space), so dragDelta is correct after any
                            // rotation or scale applied by graphicsLayer.
                            var lastTapTime = 0L

                            awaitPointerEventScope {
                                while (true) {
                                    // requireUnconsumed = true so child handlers (delete, scale handle)
                                    // get priority — parent only handles taps/drags on the text body
                                    val down = awaitFirstDown(requireUnconsumed = true)
                                    down.consume()
                                    val currentTime = System.currentTimeMillis()
                                    val isDoubleTap = (currentTime - lastTapTime < 500L)

                                    // Single tap always selects this overlay (enables cross-overlay selection)
                                    currentOnSelectText(currentOverlay.id)

                                    if (isDoubleTap) {
                                        currentOnDoubleTapText?.invoke(currentOverlay.id)
                                        lastTapTime = 0L // Reset
                                    } else {
                                        lastTapTime = currentTime
                                    }

                                    startX = currentOverlay.xPercentage * currentCanvasWidth
                                    startY = currentOverlay.yPercentage * currentCanvasHeight

                                    var hasDragged = false
                                    val pointerId = down.id

                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val change =
                                            event.changes.firstOrNull { it.id == pointerId }
                                        if (change == null || !change.pressed) {
                                            change?.consume()
                                            break
                                        }

                                        val diff = change.position - down.position

                                        // Lower threshold (0.5x touchSlop) for responsive drag
                                        if (!hasDragged && diff.getDistance() > viewConfiguration.touchSlop * 0.5f) {
                                            hasDragged = true
                                            isDragging = true
                                            lastTapTime = 0L // Reset so drag is not counted towards double tap
                                            // Light haptic feedback when drag begins
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        }

                                        if (hasDragged) {
                                            change.consume()
                                            val dragDelta =
                                                change.position - change.previousPosition

                                            // Update local drag offset for smooth rendering
                                            // No coerceIn — let user drag freely, clipToBounds handles overflow
                                            startX += dragDelta.x
                                            startY += dragDelta.y
                                            localDragDeltaX =
                                                startX - currentOverlay.xPercentage * currentCanvasWidth
                                            localDragDeltaY =
                                                startY - currentOverlay.yPercentage * currentCanvasHeight
                                        }
                                    }

                                    if (hasDragged) {
                                        // Commit final position to ViewModel on pointer UP
                                        localDragDeltaX = 0f
                                        localDragDeltaY = 0f
                                        isDragging = false
                                        currentOnUpdateText(
                                            currentOverlay.id,
                                            startX / currentCanvasWidth,
                                            startY / currentCanvasHeight,
                                            currentOverlay.rotation,
                                            currentOverlay.scale
                                        )
                                        lastTapTime = 0L
                                    }
                                }
                            }
                        }
                        .graphicsLayer {
                            // graphicsLayer AFTER pointerInput — visual transform only.
                            // Touch coordinates in pointerInput above are unaffected by rotation/scale.
                            rotationZ = overlay.rotation
                            scaleX = overlay.scale
                            scaleY = overlay.scale
                        }
                        .padding(24.dp) // Larger padding for control badges + bigger touch target
                ) {
                    // Text Box with dashed border when selected
                    Box(
                        modifier = Modifier
                            .drawBehind {
                                if (isSelected) {
                                    val dashEffect = PathEffect.dashPathEffect(
                                        intervals = floatArrayOf(12f, 12f),
                                        phase = 0f
                                    )
                                    drawRoundRect(
                                        color = Color.White,
                                        style = Stroke(
                                            width = 1.5.dp.toPx(),
                                            pathEffect = dashEffect
                                        ),
                                        cornerRadius = CornerRadius(6.dp.toPx())
                                    )
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = overlay.text,
                            color = Color(overlay.color),
                            fontFamily = fontPreset.fontFamily,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }

                    if (isSelected) {
                        // Top-Left: Close/Delete Button
                        // Uses inverse scale so button stays constant size regardless of text scale
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .offset(x = (-18).dp, y = (-18).dp)
                                .graphicsLayer {
                                    scaleX = inverseScale
                                    scaleY = inverseScale
                                }
                                .size(40.dp) // 40dp touch target (Material minimum)
                                .clip(CircleShape)
                                .clickable {
                                    onRemoveText(overlay.id)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            // Visual button inside touch target
                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .clip(CircleShape)
                                    .background(Color.White),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.ic_close_red),
                                    contentDescription = stringResource(R.string.text_overlay_desc_delete),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        // Bottom-Right: Scale and Rotation Anchor Handle
                        // Uses inverse scale so handle stays constant size
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .offset(x = 12.dp, y = 12.dp)
                                .graphicsLayer {
                                    scaleX = inverseScale
                                    scaleY = inverseScale
                                }
                                .size(40.dp) // 40dp touch target
                                .clip(CircleShape)
                                .onGloballyPositioned { coords ->
                                    handleCoordinates = coords
                                }
                                .pointerInput(overlay.id) {
                                    awaitPointerEventScope {
                                        while (true) {
                                            val down = awaitFirstDown(requireUnconsumed = false)
                                            down.consume()
                                            val currentHandleCoords = handleCoordinates ?: continue
                                            val currentCenterCoords = centerCoordinates ?: continue

                                            val touchInWindow =
                                                currentHandleCoords.localToWindow(down.position)
                                            val centerInWindow = currentCenterCoords.localToWindow(
                                                Offset(
                                                    currentCenterCoords.size.width / 2f,
                                                    currentCenterCoords.size.height / 2f
                                                )
                                            )

                                            val dx = touchInWindow.x - centerInWindow.x
                                            val dy = touchInWindow.y - centerInWindow.y
                                            startAngle =
                                                Math.toDegrees(atan2(dy.toDouble(), dx.toDouble()))
                                                    .toFloat()
                                            startDistance =
                                                hypot(dx.toDouble(), dy.toDouble()).toFloat()
                                            startRotation = currentOverlay.rotation
                                            startScale = currentOverlay.scale

                                            val pointerId = down.id
                                            while (true) {
                                                val event = awaitPointerEvent()
                                                val change =
                                                    event.changes.firstOrNull { it.id == pointerId }
                                                if (change == null || !change.pressed) {
                                                    change?.consume()
                                                    break
                                                }

                                                change.consume()
                                                val currentHandleCoords2 =
                                                    handleCoordinates ?: continue
                                                val currentCenterCoords2 =
                                                    centerCoordinates ?: continue

                                                val touchInWindow2 =
                                                    currentHandleCoords2.localToWindow(change.position)
                                                val centerInWindow2 =
                                                    currentCenterCoords2.localToWindow(
                                                        Offset(
                                                            currentCenterCoords2.size.width / 2f,
                                                            currentCenterCoords2.size.height / 2f
                                                        )
                                                    )

                                                val dx2 = touchInWindow2.x - centerInWindow2.x
                                                val dy2 = touchInWindow2.y - centerInWindow2.y

                                                val currentAngle =
                                                    Math.toDegrees(
                                                        atan2(
                                                            dy2.toDouble(),
                                                            dx2.toDouble()
                                                        )
                                                    )
                                                        .toFloat()
                                                val currentDistance =
                                                    hypot(dx2.toDouble(), dy2.toDouble()).toFloat()

                                                val deltaAngle = currentAngle - startAngle
                                                val scaleFactor =
                                                    if (startDistance > 0f) currentDistance / startDistance else 1f

                                                // Free scale: 0.3f min, 5.0f max — no edge clamping
                                                currentOnUpdateText(
                                                    currentOverlay.id,
                                                    currentOverlay.xPercentage,
                                                    currentOverlay.yPercentage,
                                                    (startRotation + deltaAngle) % 360f,
                                                    (startScale * scaleFactor).coerceIn(0.3f, 5.0f)
                                                )
                                            }
                                        }
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            // Visual handle inside touch target
                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .clip(CircleShape)
                                    .background(Color.White),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.ic_zoom),
                                    contentDescription = stringResource(R.string.text_overlay_desc_rotate_scale),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Preview(
    name = "Text Canvas - Selected State",
    showBackground = true,
    backgroundColor = 0xFF2A2A2A,
    widthDp = 360,
    heightDp = 640
)
@Composable
private fun TextOverlayCanvasPreview() {
    val mockOverlays = listOf(
        TextOverlay(
            id = "1",
            text = "Video Editor Text",
            color = 0xFFFFD600L,
            fontId = "1",
            xPercentage = 0.5f,
            yPercentage = 0.4f,
            scale = 1.2f,
            rotation = 15f
        ),
        TextOverlay(
            id = "2",
            text = "Tap to edit style",
            color = 0xFFFFFFFFL,
            fontId = "2",
            xPercentage = 0.5f,
            yPercentage = 0.6f,
            scale = 0.9f,
            rotation = -5f
        )
    )

    TextOverlayCanvasContent(
        textOverlays = mockOverlays,
        selectedId = "1",
        onSelectText = {},
        onUpdateText = { _, _, _, _, _ -> },
        onRemoveText = {}
    )
}