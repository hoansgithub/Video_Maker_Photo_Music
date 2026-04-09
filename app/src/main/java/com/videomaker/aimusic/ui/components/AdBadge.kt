package com.videomaker.aimusic.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.videomaker.aimusic.ui.theme.BackgroundLight
import com.videomaker.aimusic.ui.theme.SurfaceDark
import com.videomaker.aimusic.ui.theme.VideoMakerTheme

/**
 * Reusable AD Badge Component
 *
 * Displays an "AD" badge with different styles for different contexts.
 * Used to indicate features that require watching rewarded ads.
 *
 * @param style Badge style (Large for dialogs, Small for inline usage)
 * @param textColor Text color (defaults to style-specific colors)
 * @param backgroundColor Background color (defaults to style-specific colors)
 * @param modifier Optional modifier
 */
@Composable
fun AdBadge(
    style: AdBadgeStyle = AdBadgeStyle.Small(),
    modifier: Modifier = Modifier
) {
    when (style) {
        is AdBadgeStyle.Large -> LargeAdBadge(
            textColor = style.textColor,
            backgroundColor = style.backgroundColor,
            modifier = modifier
        )
        is AdBadgeStyle.Small -> SmallAdBadge(
            textColor = style.textColor,
            backgroundColor = style.backgroundColor,
            modifier = modifier
        )
    }
}

/**
 * Badge styles for different contexts
 */
sealed class AdBadgeStyle {
    /**
     * Large badge for dialog headers
     * - 28.sp font size
     * - Border
     * - Large padding
     * - Letter spacing
     */
    data class Large(
        val textColor: Color = SurfaceDark,
        val backgroundColor: Color = BackgroundLight
    ) : AdBadgeStyle()

    /**
     * Small badge for inline usage (buttons, dropdowns, quality picker)
     * - 10.sp font size
     * - No border
     * - Small padding
     * - Minimal letter spacing
     */
    data class Small(
        val textColor: Color? = null,  // If null, adapts to context
        val backgroundColor: Color? = null  // If null, uses semi-transparent overlay
    ) : AdBadgeStyle()
}

/**
 * Large AD badge for dialog headers
 */
@Composable
private fun LargeAdBadge(
    textColor: Color,
    backgroundColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(
                color = backgroundColor,
                shape = RoundedCornerShape(12.dp)
            )
            .border(
                width = 2.dp,
                color = backgroundColor.copy(alpha = 0.5f),
                shape = RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 24.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "AD",
            fontSize = 28.sp,
            fontWeight = FontWeight.ExtraBold,
            color = textColor,
            letterSpacing = 4.sp
        )
    }
}

/**
 * Small AD badge for inline usage
 */
@Composable
private fun SmallAdBadge(
    textColor: Color?,
    backgroundColor: Color?,
    modifier: Modifier = Modifier
) {
    val resolvedTextColor = textColor ?: MaterialTheme.colorScheme.onSurface
    val resolvedBackgroundColor = backgroundColor ?: resolvedTextColor.copy(alpha = 0.15f)

    Text(
        text = "AD",
        fontSize = 10.sp,
        fontWeight = FontWeight.ExtraBold,
        color = resolvedTextColor,
        letterSpacing = 1.sp,
        modifier = modifier
            .background(
                color = resolvedBackgroundColor,
                shape = RoundedCornerShape(4.dp)
            )
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

// ============================================
// PREVIEWS
// ============================================

@Preview(showBackground = true)
@Composable
private fun LargeAdBadgePreview() {
    VideoMakerTheme {
        AdBadge(style = AdBadgeStyle.Large())
    }
}

@Preview(showBackground = true)
@Composable
private fun SmallAdBadgePreview() {
    VideoMakerTheme {
        AdBadge(style = AdBadgeStyle.Small())
    }
}

@Preview(showBackground = true)
@Composable
private fun SmallAdBadgeWithColorPreview() {
    VideoMakerTheme {
        AdBadge(
            style = AdBadgeStyle.Small(
                textColor = Color.White,
                backgroundColor = Color.White.copy(alpha = 0.2f)
            )
        )
    }
}
