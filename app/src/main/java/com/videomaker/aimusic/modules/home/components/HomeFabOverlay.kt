package com.videomaker.aimusic.modules.home.components

import android.graphics.BlurMaskFilter
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.videomaker.aimusic.R
import com.videomaker.aimusic.ui.components.ModifierExtension.clickableSingle
import com.videomaker.aimusic.ui.theme.NewIdeasBackground
import com.videomaker.aimusic.ui.theme.Primary

private const val ANIM_MS = 700           // Songs "See What's New" reveal duration
private const val FAB_SIZE = 52
private const val SW_START = 0.4f          // CTA2 begins REVEALING (clip) once progress passes this
// CTA2 is added/removed from composition only at this near-zero progress (i.e. at the
// extremes, when the list is settled) — NOT at SW_START. Toggling composition mid-animation
// caused a one-frame recompose+relayout stutter ("giật 1 cái") right in the middle.
private const val SW_MOUNT = 0.02f
private val PillShape = RoundedCornerShape(26.dp) // half of 52dp → a perfect circle at 52dp wide

// Songs: no create pill to coordinate with, so a simple centered reveal.
private val SongsRefreshEnter: EnterTransition =
    expandHorizontally(tween(ANIM_MS, easing = FastOutSlowInEasing), expandFrom = Alignment.Start) +
        fadeIn(tween(ANIM_MS, easing = FastOutSlowInEasing))
private val SongsRefreshExit: ExitTransition =
    shrinkHorizontally(tween(ANIM_MS, easing = FastOutSlowInEasing), shrinkTowards = Alignment.Start) +
        fadeOut(tween(ANIM_MS, easing = FastOutSlowInEasing))

/**
 * Bottom floating-action overlay shared by the Gallery and Songs tabs.
 *
 * Gallery: everything is driven by a single [collapseProgress] (0 = expanded pill,
 * 1 = collapsed into the (+)) owned by the caller — time-animated when collapsing, but
 * scroll-linked when re-expanding (so scrolling up just follows the finger, no separate
 * animation clock to stutter against the fling). The create pill morphs its width into the
 * (+); "See What's New" clip-wipes in once progress passes [SW_START] (a stagger), and wipes
 * back out as progress falls — both in lockstep with the same value.
 *
 * Songs tab has no create button — only the centered "See What's New" pill ([refreshVisible]).
 */
@Composable
fun HomeFabOverlay(
    isGalleryTab: Boolean,
    collapseProgress: () -> Float,
    refreshVisible: Boolean,
    onCreateClick: () -> Unit,
    onRefreshClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(bottom = 16.dp, start = 16.dp, end = 16.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        if (isGalleryTab) {
            val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val fullWidthPx = with(LocalDensity.current) { maxWidth.toPx() }

                CreateMorphButton(
                    progress = collapseProgress,
                    fullWidthPx = fullWidthPx,
                    isRtl = isRtl,
                    onClick = onCreateClick,
                    modifier = Modifier.align(Alignment.BottomEnd)
                )

                // "See What's New" — clip wipe driven by the SAME progress (staggered after the
                // pill): reveals start→end as progress rises past SW_START, wipes end→start as it
                // falls. Reading progress in draw only → no recomposition during the animation.
                val swVisible by remember(collapseProgress) {
                    derivedStateOf { collapseProgress() > SW_MOUNT }
                }
                if (swVisible) {
                    SeeWhatsNewPill(
                        onClick = onRefreshClick,
                        modifier = Modifier
                            .drawWithContent {
                                val reveal = ((collapseProgress() - SW_START) / (1f - SW_START))
                                    .coerceIn(0f, 1f)
                                if (isRtl) {
                                    clipRect(left = size.width * (1f - reveal)) { this@drawWithContent.drawContent() }
                                } else {
                                    clipRect(right = size.width * reveal) { this@drawWithContent.drawContent() }
                                }
                            }
                            .align(Alignment.Center)
                    )
                }
            }
        } else {
            // Songs tab — only the "See What's New" pill, centered.
            AnimatedVisibility(
                visible = refreshVisible,
                enter = SongsRefreshEnter,
                exit = SongsRefreshExit,
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                SeeWhatsNewPill(onClick = onRefreshClick)
            }
        }
    }
}

/**
 * The "Create New Video" CTA as one morphing element.
 *
 * Expanded: a centered, content-width pill (measured once via [onGloballyPositioned]).
 * Collapsing: its width animates down to 52dp (becoming the (+) circle) while [progress]
 * glides it from center into the end corner and fades the label out. Single element ⇒ no
 * crossfade / no scale distortion.
 *
 * All draw-phase translations are RTL-aware via [isRtl]: the pill collapses toward the
 * layout-end edge (right in LTR, left in RTL).
 */
@Composable
private fun CreateMorphButton(
    progress: () -> Float,
    fullWidthPx: Float,
    isRtl: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var contentWidthPx by remember { mutableIntStateOf(0) }
    val measured = contentWidthPx > 0
    val density = LocalDensity.current
    val fabPx = with(density) { FAB_SIZE.dp.toPx() }
    val contentWidthDp = with(density) { contentWidthPx.toDp() }
    // +1 in LTR (slides left-to-center when expanded), −1 in RTL (slides right-to-center).
    val dirSign = if (isRtl) 1f else -1f

    Box(
        modifier = modifier
            // Width is FIXED (set once after measuring). The morph runs entirely in the DRAW
            // phase (drawWithContent + graphicsLayer reading progress), so the animation only
            // invalidates drawing — never layout or recomposition — and stays smooth even while
            // the LazyColumn is busy composing items during a scroll.
            .then(if (measured) Modifier.width(contentWidthDp) else Modifier)
            .height(FAB_SIZE.dp)
            .graphicsLayer {
                // Centered while expanded, slides into the end corner as it collapses.
                translationX = dirSign * ((fullWidthPx - contentWidthPx) / 2f) * (1f - progress())
            }
            .drawWithContent {
                val w = lerp(size.width, fabPx, progress())
                // LTR: pill anchored to right edge; RTL: anchored to left edge.
                val left = if (isRtl) 0f else size.width - w
                val radius = CornerRadius(size.height / 2f)
                drawRoundRect(
                    color = Color.Black.copy(alpha = 0.18f),
                    topLeft = Offset(left, 4.dp.toPx()),
                    size = Size(w, size.height),
                    cornerRadius = radius
                )
                drawRoundRect(
                    color = Primary,
                    topLeft = Offset(left, 0f),
                    size = Size(w, size.height),
                    cornerRadius = radius
                )
                // Clip the icon + label to the shrinking background.
                clipRect(left = left, right = left + w) { this@drawWithContent.drawContent() }
            }
            .pointerInput(isRtl) {
                // Only the visible (drawn) part is tappable — avoids a phantom hit target over
                // the transparent area once collapsed.
                detectTapGestures { offset ->
                    val w = lerp(size.width.toFloat(), fabPx, progress())
                    val hit = if (isRtl) offset.x <= w else offset.x >= size.width - w
                    if (hit) onClick()
                }
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxHeight()
                .onGloballyPositioned { if (contentWidthPx == 0) contentWidthPx = it.size.width }
                // start padding 14dp keeps the 24dp icon centered when width == 52dp.
                .padding(start = 14.dp, end = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_circle_plus),
                contentDescription = "Create",
                tint = Color.Unspecified,
                modifier = Modifier
                    .size(24.dp)
                    // Glides the + to the circle's center as the pill collapses.
                    // LTR: icon moves right; RTL: icon moves left (Row is mirrored).
                    .graphicsLayer {
                        translationX = -dirSign * (contentWidthPx - fabPx) * progress()
                    }
            )
            Text(
                text = stringResource(R.string.gallery_create_new_video),
                color = Color.Black,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                softWrap = false,
                // Fades out over the first ~40% so it's gone before the clip reaches it.
                modifier = Modifier.graphicsLayer {
                    alpha = (1f - progress() / 0.4f).coerceIn(0f, 1f)
                }
            )
        }
    }
}

/**
 * Black pill with a Primary border, "See What's New" label and a Primary refresh glyph.
 * Wraps its content to match the mockup.
 */
@Composable
private fun SeeWhatsNewPill(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .background(color = NewIdeasBackground, shape = PillShape)
            .innerShadow(
                shape = PillShape,
                color = Color(0x66FFFFFF), // #FFFFFF3D
                blur = 12.dp,
                offsetY = (-3).dp
            )
            .border(width = 1.dp, color = Primary.copy(0.7f), shape = PillShape)
            .clickableSingle(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = "See What's New",
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.W400,
            maxLines = 1,
            softWrap = false
        )
        Icon(
            imageVector = Icons.Default.Autorenew,
            contentDescription = "Refresh",
            tint = Primary,
            modifier = Modifier.size(24.dp)
        )
    }
}

fun Modifier.innerShadow(
    shape: Shape,
    color: Color,
    blur: Dp,
    offsetX: Dp = 0.dp,
    offsetY: Dp = 0.dp,
    spread: Dp = 0.dp,
) = this.drawWithContent {
    drawContent()
    drawIntoCanvas { canvas ->
        val outline = shape.createOutline(size, layoutDirection, this)

        // paint vẽ vùng glow (giữ nguyên alpha của color)
        val shadowPaint = Paint().apply { this.color = color }

        // layer composite ở alpha = 1 (KHÔNG truyền shadowPaint vào saveLayer)
        canvas.saveLayer(size.toRect(), Paint())

        // 1. tô đầy shape bằng màu glow
        canvas.drawOutline(outline, shadowPaint)

        // 2. đục lỗ phần giữa bằng shape blur + offset
        shadowPaint.asFrameworkPaint().apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
            if (blur.toPx() > 0f) {
                maskFilter = BlurMaskFilter(blur.toPx(), BlurMaskFilter.Blur.NORMAL)
            }
        }
        shadowPaint.color = Color.Black // DST_OUT chỉ dùng alpha, màu gì cũng được

        val inflate = spread.toPx()
        canvas.translate(offsetX.toPx(), offsetY.toPx())
        canvas.drawOutline(
            if (inflate != 0f)
                shape.createOutline(
                    Size(size.width + inflate, size.height + inflate),
                    layoutDirection, this
                )
            else outline,
            shadowPaint
        )
        canvas.restore()
    }
}