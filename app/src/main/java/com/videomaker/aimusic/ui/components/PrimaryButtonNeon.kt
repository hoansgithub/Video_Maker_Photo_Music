package com.videomaker.aimusic.ui.components

import android.graphics.BlurMaskFilter
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin


private val NeonLime = Color(0xFFD4FF00)
private val NeonLimeLight = Color(0xFFE8FF5E)
private val NeonLimeDark = Color(0xFFB8E600)
private val DarkBg = Color(0xFF0A0A0A)

@Composable
fun PrimaryButtonNeon(
    text: String,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val pillShape = RoundedCornerShape(40.dp)
    val innerShape = RoundedCornerShape(32.dp)

    // Glow pulse animation
    val infiniteTransition = rememberInfiniteTransition(label = "glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    // Press scale
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "scale"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .scale(scale)
            .shadowCustom(
                color = Color(0xFFF8F8F8).copy(0.5f),
                blurRadius = 8.dp,
                borderRadius = 40.dp,
            )
    ) {
        // ── Layer 1: Outer glow ─────────────────────────────
        Box(
            modifier = Modifier
                .matchParentSize()
                .padding(4.dp)
                .clip(pillShape)
                .background(NeonLime.copy(alpha = glowAlpha))
        )

        // ── Layer 2: Main pill body ─────────────────────────
        //   • Gradient fill (lime)
        //   • border: 1.5px gradient @ 158.39deg
        //   • box-shadow: 0 0 8px 0 #F8F8F840 inset
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(pillShape)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(NeonLimeLight, NeonLime, NeonLimeDark)
                    )
                )
                .glassBorderWithInsetShadow(
                    cornerRadius = 40f,
                    borderWidth = 1.5f,
                    insetBlurRadius = 8f
                )
        )

        // ── Layer 3: Inner glassmorphism pill ───────────────
        //   • backdrop-filter: blur(22dp)
        //   • Same gradient border
        //   • Subtle white overlay
        Box(
            modifier = Modifier
                .matchParentSize()
                .padding(horizontal = 34.dp, vertical = 4.dp)
        ) {
            // Backdrop blur layer
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(innerShape)
                    .blur(22.dp) // backdrop-filter: blur(~22dp)
                    .background(Color.White.copy(alpha = 0.06f))
            )

            // Glass highlight + border
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(innerShape)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.18f),
                                Color.White.copy(alpha = 0.04f)
                            )
                        )
                    )
                    .glassBorderWithInsetShadow(
                        cornerRadius = 32f,
                        borderWidth = 1.5f,
                        insetBlurRadius = 8f
                    )
            )
        }

        // ── Layer 4: Content ────────────────────────────────
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier
                .clip(pillShape)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick
                )
                .padding(vertical = 12.dp, horizontal = 50.dp)
        ) {
            Text(
                text = text,
                style = TextStyle(
                    color = Color.Black.copy(alpha = 0.85f),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = (-0.3).sp
                )
            )

            Spacer(modifier = Modifier.width(16.dp))

            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "Arrow",
                tint = Color.Black.copy(alpha = 0.85f),
                modifier = Modifier.size(26.dp)
            )
        }
    }
}

// box-shadow: 0px 0px 8px 0px #F8F8F840 inset
private val InsetShadowColor = Color(0x40F8F8F8)

// border-image-source gradient stops (158.39deg)
//   rgba(255,255,255, 0.4)    at 14.19%
//   rgba(255,255,255, 0.0001) at 50.59%
//   rgba(255,255,255, 0.0001) at 68.79%
//   rgba(255,255,255, 0.1)    at 105.18% → clamped to 100%
private val BorderGradientStops = arrayOf(
    0.1419f to Color(0x66FFFFFF),   // white 40%
    0.5059f to Color(0x00FFFFFF),   // white ~0%
    0.6879f to Color(0x00FFFFFF),   // white ~0%
    1.0000f to Color(0x1AFFFFFF)    // white 10%
)

// ── Helper: CSS-angle linear gradient ────────────────────────
// CSS 158.39deg → starts top-left bright, fades toward bottom-right
private fun cssAngleGradient(
    angleDeg: Float,
    colorStops: Array<Pair<Float, Color>>,
    size: Size
): Brush {
    // CSS angle: 0deg = bottom-to-top, clockwise
    // Convert to math: radians from positive-x axis
    val angleRad = ((angleDeg + 90f) % 360f) * (PI.toFloat() / 180f)
    val halfW = size.width / 2f
    val halfH = size.height / 2f
    val cosA = cos(angleRad)
    val sinA = sin(angleRad)
    val length = abs(halfW * cosA) + abs(halfH * sinA)
    val start = Offset(halfW - cosA * length, halfH + sinA * length)
    val end = Offset(halfW + cosA * length, halfH - sinA * length)
    return Brush.linearGradient(
        colorStops = colorStops,
        start = start,
        end = end
    )
}

// ── Custom Modifier: gradient border + inset shadow ─────────
private fun Modifier.glassBorderWithInsetShadow(
    cornerRadius: Float,  // in dp, will be converted
    borderWidth: Float,   // in dp
    insetBlurRadius: Float // in dp (box-shadow blur)
) = this.drawWithContent {
    drawContent()

    val cornerPx = cornerRadius * density
    val strokePx = borderWidth * density
    val blurPx = insetBlurRadius * density
    val cr = CornerRadius(cornerPx, cornerPx)

    // 1) Gradient border: 1.5px solid, 158.39deg
    val borderBrush = cssAngleGradient(
        angleDeg = 158.39f,
        colorStops = BorderGradientStops,
        size = size
    )
    drawRoundRect(
        brush = borderBrush,
        cornerRadius = cr,
        style = Stroke(width = strokePx)
    )

    // 2) Inset box-shadow: 0 0 8px 0 #F8F8F840 inset
    // Approximate by drawing a stroked rect inset by half the blur
    val insetAmount = blurPx * 0.5f
    inset(insetAmount) {
        drawRoundRect(
            color = InsetShadowColor,
            cornerRadius = CornerRadius(
                cornerPx - insetAmount,
                cornerPx - insetAmount
            ),
            style = Stroke(width = blurPx)
        )
    }
}

fun Modifier.shadowCustom(
    color: Color = Color.Black,
    borderRadius: Dp = 0.dp,
    blurRadius: Dp = 0.dp,
    offsetY: Dp = 0.dp,
    offsetX: Dp = 0.dp,
    spread: Dp = 0f.dp,
    modifier: Modifier = Modifier
) = this.then(modifier.drawBehind {
    this.drawIntoCanvas {
        val paint = Paint()
        val frameworkPaint = paint.asFrameworkPaint()
        val spreadPixel = spread.toPx()
        val leftPixel = (0f - spreadPixel) + offsetX.toPx()
        val topPixel = (0f - spreadPixel) + offsetY.toPx()
        val rightPixel = (this.size.width + spreadPixel)
        val bottomPixel = (this.size.height + spreadPixel)

        if (blurRadius != 0.dp) {
            frameworkPaint.maskFilter =
                (BlurMaskFilter(blurRadius.toPx(), BlurMaskFilter.Blur.NORMAL))
        }

        frameworkPaint.color = color.toArgb()
        it.drawRoundRect(
            left = leftPixel,
            top = topPixel,
            right = rightPixel,
            bottom = bottomPixel,
            radiusX = borderRadius.toPx(),
            radiusY = borderRadius.toPx(),
            paint
        )
    }
})

// ═════════════════════════════════════════════════════════════
// Preview
// ═════════════════════════════════════════════════════════════
@Preview(
    showBackground = true,
    backgroundColor = 0xFF0A0A0A,
    widthDp = 360,
    heightDp = 160
)
@Composable
private fun GlowingAddWidgetButtonPreview() {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
            .padding(horizontal = 32.dp)
    ) {
        PrimaryButtonNeon(onClick = {}, text = "testttt")
    }
}