package com.videomaker.aimusic.ui.utils

import android.graphics.BlurMaskFilter
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

fun Modifier.innerShadowCustom(
    color: Color = Color.Black,
    borderRadius: Dp = 0.dp,
    blurRadius: Dp = 0.dp,
    offsetY: Dp = 0.dp,
    offsetX: Dp = 0.dp,
    spread: Dp = 0.dp,
    blurBackground: Dp = 0.dp,
    modifier: Modifier = Modifier
) = this
    // Background blur (giống Figma)
    .then(
        if (blurBackground > 0.dp) {
            Modifier.graphicsLayer {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    renderEffect = RenderEffect.createBlurEffect(
                        blurBackground.toPx(),
                        blurBackground.toPx(),
                        Shader.TileMode.CLAMP
                    ).asComposeRenderEffect()
                }
            }
        } else Modifier
    )
    // Inner shadow
    .then(
        modifier.drawBehind {
            drawIntoCanvas { canvas ->
                val paint = Paint()
                val frameworkPaint = paint.asFrameworkPaint()

                val spreadPx = spread.toPx()
                val left = (0f + spreadPx)
                val top = (0f + spreadPx)
                val right = size.width - spreadPx
                val bottom = size.height - spreadPx

                if (blurRadius != 0.dp) {
                    frameworkPaint.maskFilter =
                        BlurMaskFilter(blurRadius.toPx(), BlurMaskFilter.Blur.NORMAL)
                }

                frameworkPaint.color = color.toArgb()

                val rect = Rect(0f, 0f, size.width, size.height)

                // 👉 Trick để tạo inner shadow
                canvas.saveLayer(rect, Paint())

                // vẽ shape gốc
                canvas.drawRoundRect(
                    0f,
                    0f,
                    size.width,
                    size.height,
                    borderRadius.toPx(),
                    borderRadius.toPx(),
                    Paint().apply { this.color = Color.Transparent }
                )

                // vẽ shadow bị lệch vào trong
                canvas.drawRoundRect(
                    left + offsetX.toPx(),
                    top + offsetY.toPx(),
                    right + offsetX.toPx(),
                    bottom + offsetY.toPx(),
                    borderRadius.toPx(),
                    borderRadius.toPx(),
                    paint
                )

                canvas.restore()
            }
        }
    )