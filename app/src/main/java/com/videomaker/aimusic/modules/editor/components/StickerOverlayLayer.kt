package com.videomaker.aimusic.modules.editor.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.request.ImageRequest
import com.videomaker.aimusic.R
import com.videomaker.aimusic.domain.model.StickerPlacement
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * StickerOverlayLayer - Draws + edits stickers on top of the video preview.
 *
 * Gestures are handled on the FIXED-SIZE root layer (in stable video-frame coordinates),
 * NOT on the per-sticker views — so pinch zooms around the focal point and never drifts,
 * and the selection handles stay glued to the corners. Everything is clipped to the video
 * rect, so anything dragged/zoomed outside the video is hidden (CapCut-style).
 *
 * Interactions:
 * - One-finger drag a sticker = move (a sliver must stay inside, can't be lost)
 * - Two-finger pinch = zoom + rotate around the pinch focal point (aspect preserved)
 * - Bottom-right handle = drag to resize + rotate; top-left = delete
 * - Single tap empty = deselect; double tap empty = select the top-most sticker
 */
@Composable
fun StickerOverlayLayer(
    stickers: List<StickerPlacement>,
    selectedInstanceId: String?,
    onSelect: (String?) -> Unit,
    onTransform: (StickerPlacement) -> Unit,
    onDelete: (String) -> Unit,
    onDoubleTapTopMost: () -> Unit,
    // Tapping a sticker while committed (picker closed) requests re-opening the picker for it,
    // like tapping a text overlay re-enters text editing. Receives the tapped instanceId.
    onRequestEdit: (String) -> Unit,
    // When false the layer is "committed": stickers still render (and still clip to the video)
    // and a tap re-opens the picker via [onRequestEdit], but there is no selection box and no
    // drag/zoom. Driven by the sticker picker being open.
    interactive: Boolean = true,
    modifier: Modifier = Modifier
) {
    var rectSize by remember { mutableStateOf(Size.Zero) }
    // sticker aspect ratio (width / height), captured when each image loads; default square.
    val aspectRatios = remember { mutableStateMapOf<String, Float>() }
    // Live transform of the sticker currently being manipulated. Updated every pointer event
    // (cheap, local — only the overlay recomposes) and committed to the ViewModel once on
    // release, so we don't recompose the whole editor 60x/second (was the source of jank).
    var live by remember { mutableStateOf<StickerPlacement?>(null) }
    // Stable callback so sticker images that aren't being manipulated can skip recomposition.
    val onAspect = remember { { url: String, ratio: Float -> aspectRatios[url] = ratio } }

    // Latest values read inside the long-lived gesture coroutine (so it isn't re-keyed on
    // every transform, which would cancel the in-progress gesture).
    val latestStickers by rememberUpdatedState(stickers)
    val latestSelected by rememberUpdatedState(selectedInstanceId)
    val onSelectState by rememberUpdatedState(onSelect)
    val onTransformState by rememberUpdatedState(onTransform)
    val onDeleteState by rememberUpdatedState(onDelete)
    val onDoubleTapState by rememberUpdatedState(onDoubleTapTopMost)
    val onRequestEditState by rememberUpdatedState(onRequestEdit)

    val density = LocalDensity.current

    Box(
        modifier = modifier
            // Clip EVERYTHING (image + dashed box + icons) to the video rect: zoom can exceed
            // the video, but only the part inside the video is shown — anything outside is hidden.
            .clipToBounds()
            .onSizeChanged { rectSize = Size(it.width.toFloat(), it.height.toFloat()) }
            // Re-keyed on `interactive` so toggling it cancels any in-flight gesture and
            // detaches the handler entirely when the picker is closed.
            .pointerInput(rectSize, interactive) {
                if (rectSize == Size.Zero) return@pointerInput
                val rectW = rectSize.width
                val rectH = rectSize.height
                // Sticker SIZE is anchored to the frame's SHORT side (which maps to the 1080
                // video dimension in every aspect ratio), so a sticker keeps the same absolute
                // size across ratios — like text's fixed font size — instead of growing/shrinking
                // with the frame width. POSITION still uses rectW/rectH (normalized centers).
                val sizeRefPx = minOf(rectW, rectH)
                val handleRadiusPx = with(density) { 24.dp.toPx() }
                val minVisiblePx = with(density) { 2.dp.toPx() }
                val touchSlop = viewConfiguration.touchSlop
                val doubleTapMs = viewConfiguration.doubleTapTimeoutMillis
                var lastTapUptime = 0L

                fun aspectOf(p: StickerPlacement) = aspectRatios[p.assetUrl] ?: 1f
                fun centerOf(p: StickerPlacement) = Offset(p.centerXNorm * rectW, p.centerYNorm * rectH)
                fun cornerOf(p: StickerPlacement, sx: Float, sy: Float): Offset {
                    val halfW = boxWidthPx(p, sizeRefPx) / 2f
                    val halfH = boxHeightPx(p, sizeRefPx, aspectOf(p)) / 2f
                    val rad = Math.toRadians(p.rotationDeg.toDouble())
                    val c = cos(rad).toFloat()
                    val s = sin(rad).toFloat()
                    val lx = sx * halfW
                    val ly = sy * halfH
                    val ctr = centerOf(p)
                    return Offset(ctr.x + (lx * c - ly * s), ctr.y + (lx * s + ly * c))
                }
                fun hitTest(pos: Offset): StickerPlacement? {
                    // Top-most first.
                    for (p in latestStickers.sortedByDescending { it.zIndex }) {
                        val halfW = boxWidthPx(p, sizeRefPx) / 2f
                        val halfH = boxHeightPx(p, sizeRefPx, aspectOf(p)) / 2f
                        val ctr = centerOf(p)
                        val rad = Math.toRadians(-p.rotationDeg.toDouble())
                        val c = cos(rad).toFloat()
                        val s = sin(rad).toFloat()
                        val d = pos - ctr
                        val lx = d.x * c - d.y * s
                        val ly = d.x * s + d.y * c
                        if (abs(lx) <= halfW && abs(ly) <= halfH) return p
                    }
                    return null
                }

                // COMMITTED state (picker closed): the only interaction is a tap on a sticker,
                // which re-opens the picker for that sticker (like re-entering text editing).
                // No drag/zoom/selection box here.
                if (!interactive) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val start = down.position
                        var moved = false
                        do {
                            val e = awaitPointerEvent()
                            e.changes.forEach { ch ->
                                if ((ch.position - start).getDistance() > touchSlop) moved = true
                            }
                        } while (e.changes.any { it.pressed })
                        if (!moved) hitTest(start)?.let { onRequestEditState(it.instanceId) }
                    }
                    return@pointerInput
                }

                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val start = down.position
                    val selected = latestStickers.firstOrNull { it.instanceId == latestSelected }

                    // 1) Resize/rotate handle of the selected sticker (bottom-right corner).
                    if (selected != null &&
                        (start - cornerOf(selected, 1f, 1f)).getDistance() <= handleRadiusPx
                    ) {
                        down.consume()
                        val center = centerOf(selected)
                        val v0 = start - center
                        val startDist = hypot(v0.x, v0.y).coerceAtLeast(1f)
                        val startAngle = atan2(v0.y, v0.x)
                        val startW = selected.widthFractionOfVideo
                        val startRot = selected.rotationDeg
                        var result: StickerPlacement? = null
                        do {
                            val e = awaitPointerEvent()
                            val ch = e.changes.firstOrNull()
                            if (ch != null) {
                                val v = ch.position - center
                                val scale = hypot(v.x, v.y) / startDist
                                val deltaDeg =
                                    Math.toDegrees((atan2(v.y, v.x) - startAngle).toDouble()).toFloat()
                                val clamped = clampPlacement(
                                    selected, selected.centerXNorm, selected.centerYNorm,
                                    startW * scale, startRot + deltaDeg,
                                    rectW, rectH, aspectOf(selected), minVisiblePx
                                )
                                result = clamped
                                live = clamped         // local preview, no editor recompose
                                ch.consume()
                            }
                        } while (e.changes.any { it.pressed })
                        result?.let { onTransformState(it) }   // commit once on release
                        live = null   // clear the live preview so later recompositions (e.g. an
                                      // aspect-ratio remap) render the committed placement, not a stale one
                        return@awaitEachGesture
                    }

                    // 2) Delete button of the selected sticker (top-left corner).
                    if (selected != null &&
                        (start - cornerOf(selected, -1f, -1f)).getDistance() <= handleRadiusPx
                    ) {
                        down.consume()
                        var moved = false
                        do {
                            val e = awaitPointerEvent()
                            e.changes.forEach { ch ->
                                if ((ch.position - start).getDistance() > touchSlop) moved = true
                                ch.consume()
                            }
                        } while (e.changes.any { it.pressed })
                        if (!moved) onDeleteState(selected.instanceId)
                        return@awaitEachGesture
                    }

                    // 3) Manipulate ONLY the sticker whose box is under the finger. Touching
                    //    outside every sticker's box never drags the selected one — you must
                    //    grab the sticker inside its box (selection panel) to move/zoom it.
                    val active = hitTest(start)
                    if (active != null) {
                        onSelectState(active.instanceId)
                        var cx = active.centerXNorm
                        var cy = active.centerYNorm
                        var w = active.widthFractionOfVideo
                        var rot = active.rotationDeg
                        val aspect = aspectOf(active)
                        var result: StickerPlacement? = null
                        do {
                            val e = awaitPointerEvent()
                            val zoom = e.calculateZoom()
                            val rotation = e.calculateRotation()
                            val pan = e.calculatePan()
                            if (zoom != 1f || rotation != 0f || pan != Offset.Zero) {
                                val centroid = e.calculateCentroid(useCurrent = true)
                                // Clamp the width FIRST and use the ACTUAL applied zoom for the
                                // focal-center scaling. Otherwise, when width is at the cap, the
                                // center would still get scaled by the requested zoom → the
                                // sticker drifts while size doesn't change (hard to move/pinch).
                                val newW = (w * zoom).coerceIn(MIN_WIDTH_FRACTION, maxWidthFraction(sizeRefPx, aspect))
                                val effZoom = if (w > 0f) newW / w else 1f
                                var p = Offset(cx * rectW, cy * rectH)
                                p += pan
                                if (centroid != Offset.Unspecified) {
                                    val rad = Math.toRadians(rotation.toDouble())
                                    val c = cos(rad).toFloat()
                                    val s = sin(rad).toFloat()
                                    val d = p - centroid
                                    val rotated = Offset(d.x * c - d.y * s, d.x * s + d.y * c)
                                    p = centroid + rotated * effZoom
                                }
                                w = newW
                                rot += rotation
                                val clamped = clampPlacement(
                                    active, p.x / rectW, p.y / rectH, w, rot,
                                    rectW, rectH, aspect, minVisiblePx
                                )
                                cx = clamped.centerXNorm
                                cy = clamped.centerYNorm
                                w = clamped.widthFractionOfVideo
                                rot = clamped.rotationDeg
                                result = clamped
                                live = clamped         // local preview, no editor recompose
                                e.changes.forEach { if (it.positionChanged()) it.consume() }
                            }
                        } while (e.changes.any { it.pressed })
                        if (result != null) {
                            onTransformState(result)   // moved → commit once on release
                        } else {
                            // No movement → it was a tap: re-select under the point / deselect.
                            val now = down.uptimeMillis
                            val isDouble = now - lastTapUptime <= doubleTapMs
                            lastTapUptime = if (isDouble) 0L else now
                            val tapped = hitTest(start)
                            when {
                                isDouble -> onDoubleTapState()
                                tapped != null -> onSelectState(tapped.instanceId)
                                else -> onSelectState(null)
                            }
                        }
                        live = null   // clear the live preview (see note in the resize branch)
                        return@awaitEachGesture
                    }

                    // 4) Nothing selected and nothing hit → tap = no-op, double-tap = top-most.
                    var moved = false
                    do {
                        val e = awaitPointerEvent()
                        e.changes.forEach { ch ->
                            if ((ch.position - start).getDistance() > touchSlop) moved = true
                        }
                    } while (e.changes.any { it.pressed })
                    if (!moved) {
                        val now = down.uptimeMillis
                        val isDouble = now - lastTapUptime <= doubleTapMs
                        lastTapUptime = if (isDouble) 0L else now
                        if (isDouble) onDoubleTapState() else onSelectState(null)
                    }
                }
            }
    ) {
        if (rectSize == Size.Zero) return@Box
        val rectW = rectSize.width
        val rectH = rectSize.height
        // Render the live (in-progress) placement instead of the committed one for the sticker
        // being manipulated, so the gesture is reflected instantly without an editor recompose.
        val live0 = live
        val ordered = stickers
            .map { if (live0 != null && it.instanceId == live0.instanceId) live0 else it }
            .sortedBy { it.zIndex }

        // Layer 1: sticker images (clipped to the video by the root).
        Box(modifier = Modifier.matchParentSize()) {
            ordered.forEach { placement ->
                StickerImage(
                    placement = placement,
                    rectW = rectW,
                    rectH = rectH,
                    aspect = aspectRatios[placement.assetUrl] ?: 1f,
                    onAspect = onAspect
                )
            }
        }

        // Layer 2: selection chrome (dashed box, delete, resize handle) — purely visual,
        // positioned from the placement so it always tracks the sticker's corners. Only shown
        // while the layer is interactive (picker open); hidden once stickers are committed.
        ordered.forEach { placement ->
            if (interactive && placement.instanceId == selectedInstanceId) {
                StickerSelectionChrome(
                    placement = placement,
                    rectW = rectW,
                    rectH = rectH,
                    aspect = aspectRatios[placement.assetUrl] ?: 1f
                )
            }
        }
    }
}

// ============================================
// GEOMETRY
// ============================================

private const val MIN_WIDTH_FRACTION = 0.05f

/**
 * Absolute pixel safety cap (well under Compose's Constraints packing limit ~32766). The real
 * cap is frame-fit (see [clampPlacement]); this only guards against bad persisted data.
 */
private const val MAX_BOX_PX = 12000f

/** Cap Coil's decoded bitmap size regardless of how large the sticker is zoomed. */
private const val STICKER_DECODE_PX = 1024

/**
 * Fixed pixel size of each sticker's render layer. Zoom is applied as a GPU [graphicsLayer]
 * scale ON TOP of this constant-size layer, so a huge zoom never re-measures the node or
 * allocates a giant offscreen buffer (the cause of the jank/freeze when zooming very large).
 * Kept >= [STICKER_DECODE_PX] so the un-zoomed sticker stays crisp.
 */
private const val STICKER_BASE_PX = 1280f

/**
 * Max sticker width (fraction of the short side) so neither rendered dimension exceeds
 * [MAX_BOX_PX]. [refPx] is the frame's short side — the ratio-invariant size reference.
 */
private fun maxWidthFraction(refPx: Float, aspect: Float): Float {
    if (refPx <= 0f) return MIN_WIDTH_FRACTION
    val a = if (aspect > 0f) aspect else 1f
    return minOf(MAX_BOX_PX / refPx, MAX_BOX_PX * a / refPx).coerceAtLeast(MIN_WIDTH_FRACTION)
}

/**
 * Box pixel dimensions for a placement (width drives, height follows the sticker aspect).
 * [refPx] is the frame's SHORT side, so size stays constant across aspect ratios.
 */
private fun boxWidthPx(placement: StickerPlacement, refPx: Float): Float =
    (placement.widthFractionOfVideo * refPx).coerceAtLeast(1f)

private fun boxHeightPx(placement: StickerPlacement, refPx: Float, aspect: Float): Float =
    boxWidthPx(placement, refPx) / (if (aspect > 0f) aspect else 1f)

/**
 * Clamp a candidate transform: enforce only a minimum size (zoom in is unlimited), and allow
 * the center to leave [0,1] but keep at least [minVisiblePx] of the box inside each edge.
 */
private fun clampPlacement(
    base: StickerPlacement,
    cx: Float,
    cy: Float,
    widthFrac: Float,
    rot: Float,
    rectW: Float,
    rectH: Float,
    aspect: Float,
    minVisiblePx: Float
): StickerPlacement {
    // Allow zoom bigger than the video (CapCut-style); only cap at MAX_BOX_PX so neither
    // rendered dimension can overflow Compose's layout Constraints and crash.
    // Size is measured against the short side (ratio-invariant); position against rectW/rectH.
    val refPx = minOf(rectW, rectH)
    val clampedWidth = widthFrac.coerceIn(MIN_WIDTH_FRACTION, maxWidthFraction(refPx, aspect))
    val halfWpx = clampedWidth * refPx / 2f
    val halfHpx = halfWpx / (if (aspect > 0f) aspect else 1f)
    val cxLow = if (rectW > 0f) (minVisiblePx - halfWpx) / rectW else 0f
    val cyLow = if (rectH > 0f) (minVisiblePx - halfHpx) / rectH else 0f
    return base.copy(
        centerXNorm = cx.coerceIn(cxLow, 1f - cxLow),
        centerYNorm = cy.coerceIn(cyLow, 1f - cyLow),
        widthFractionOfVideo = clampedWidth,
        rotationDeg = rot
    )
}

// ============================================
// IMAGE
// ============================================

/**
 * The sticker image — clipped to the video rect by the root. Reports its aspect on load.
 *
 * Rendered into a CONSTANT-size base layer ([STICKER_BASE_PX] wide). Position, zoom and
 * rotation are applied as a GPU [graphicsLayer] transform (translate + scale + rotate) on top
 * of that fixed layer — so dragging/pinching to any size only re-composites on the GPU and
 * never re-measures the node or allocates a buffer bigger than the base. This is what keeps
 * zoom smooth and crash-free at extreme sizes (the previous layout-resize approach allocated
 * an offscreen buffer up to MAX_BOX_PX² and re-laid-out every frame).
 */
@Composable
private fun StickerImage(
    placement: StickerPlacement,
    rectW: Float,
    rectH: Float,
    aspect: Float,
    onAspect: (String, Float) -> Unit
) {
    val density = LocalDensity.current
    val context = LocalContext.current
    val a = if (aspect > 0f) aspect else 1f
    // Size is anchored to the frame's SHORT side (ratio-invariant), not the width.
    val sizeRefPx = minOf(rectW, rectH)
    // Fixed base layer: width = STICKER_BASE_PX (capped to the short side so we never allocate a
    // buffer wider than needed on huge surfaces), height follows the sticker aspect.
    val baseWpx = sizeRefPx.coerceAtMost(STICKER_BASE_PX).coerceAtLeast(1f)
    val baseHpx = (baseWpx / a).coerceAtLeast(1f)
    val baseWdp = with(density) { baseWpx.toDp() }
    val baseHdp = with(density) { baseHpx.toDp() }

    // Cap Coil's decode size so the layer never makes it allocate a giant bitmap.
    val request = remember(placement.assetUrl) {
        ImageRequest.Builder(context)
            .data(placement.assetUrl)
            .size(STICKER_DECODE_PX)
            .build()
    }

    Box(
        modifier = Modifier
            // requiredWidth/Height: fixed base size, ignoring the parent (video-rect) max
            // constraints; the visible size comes from the graphicsLayer scale below.
            .requiredWidth(baseWdp)
            .requiredHeight(baseHdp)
            // All transforms in the draw phase (GPU) — read live placement values here so a
            // gesture re-composites without re-measuring. Scale maps the base layer to the
            // target box width; translation moves the base-box center onto the placement
            // center (scale/rotate pivot is the layer center, so translating the center is
            // zoom-invariant and never drifts).
            .graphicsLayer {
                val boxWpx = (placement.widthFractionOfVideo * sizeRefPx).coerceAtLeast(1f)
                val s = (boxWpx / baseWpx).coerceAtLeast(0.0001f)
                scaleX = s
                scaleY = s
                rotationZ = placement.rotationDeg
                alpha = placement.opacity.coerceIn(0f, 1f)
                translationX = placement.centerXNorm * rectW - baseWpx / 2f
                translationY = placement.centerYNorm * rectH - baseHpx / 2f
            }
    ) {
        AsyncImage(
            model = request,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.matchParentSize(),
            onState = { state ->
                if (state is AsyncImagePainter.State.Success) {
                    val s = state.painter.intrinsicSize
                    if (s.isSpecified && s.width > 0f && s.height > 0f) {
                        onAspect(placement.assetUrl, s.width / s.height)
                    }
                }
            }
        )
    }
}

// ============================================
// SELECTION CHROME (visual only — gestures handled by the root)
// ============================================

@Composable
private fun StickerSelectionChrome(
    placement: StickerPlacement,
    rectW: Float,
    rectH: Float,
    aspect: Float
) {
    val density = LocalDensity.current
    val sizeRefPx = minOf(rectW, rectH)
    val boxW = boxWidthPx(placement, sizeRefPx)
    val boxH = boxHeightPx(placement, sizeRefPx, aspect)
    val centerXpx = placement.centerXNorm * rectW
    val centerYpx = placement.centerYNorm * rectH
    val thetaRad = Math.toRadians(placement.rotationDeg.toDouble())
    val cosT = cos(thetaRad).toFloat()
    val sinT = sin(thetaRad).toFloat()

    fun corner(sx: Float, sy: Float): Offset {
        val lx = sx * boxW / 2f
        val ly = sy * boxH / 2f
        return Offset(centerXpx + (lx * cosT - ly * sinT), centerYpx + (lx * sinT + ly * cosT))
    }

    val deleteAt = corner(-1f, -1f)
    val handleAt = corner(1f, 1f)

    // Dashed bounding box — matches the text overlay's selection style (white, thin 1.5dp
    // stroke, 12/12 dash, 6dp rounded corners). Drawn in video-rect pixel space via
    // rotate + drawRoundRect so the stroke stays thin at any zoom and never allocates a
    // giant scaled layer.
    val strokePx = with(density) { 1.5.dp.toPx() }
    val cornerPx = with(density) { 6.dp.toPx() }
    Canvas(modifier = Modifier.fillMaxSize()) {
        rotate(degrees = placement.rotationDeg, pivot = Offset(centerXpx, centerYpx)) {
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(centerXpx - boxW / 2f, centerYpx - boxH / 2f),
                size = Size(boxW, boxH),
                cornerRadius = CornerRadius(cornerPx, cornerPx),
                style = Stroke(
                    width = strokePx,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 12f), 0f)
                )
            )
        }
    }

    // Control badges — same size + icons as the text overlay (18dp white circle).
    val btn = 18.dp
    val btnPx = with(density) { btn.toPx() }

    // Delete (top-left corner)
    Box(
        modifier = Modifier
            .offset {
                IntOffset(
                    (deleteAt.x - btnPx / 2f).roundToInt(),
                    (deleteAt.y - btnPx / 2f).roundToInt()
                )
            }
            .size(btn)
            .clip(CircleShape)
            .background(Color.White)
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_close_red),
            contentDescription = null,
            modifier = Modifier
                .size(btn)
                .align(Alignment.Center)
        )
    }

    // Resize / rotate handle (bottom-right corner)
    Box(
        modifier = Modifier
            .offset {
                IntOffset(
                    (handleAt.x - btnPx / 2f).roundToInt(),
                    (handleAt.y - btnPx / 2f).roundToInt()
                )
            }
            .size(btn)
            .clip(CircleShape)
            .background(Color.White)
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_zoom),
            contentDescription = null,
            modifier = Modifier
                .size(btn)
                .align(Alignment.Center)
        )
    }
}
