package com.videomaker.aimusic.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.videomaker.aimusic.ui.theme.BackgroundLight
import com.videomaker.aimusic.ui.theme.TextBright
import com.videomaker.aimusic.ui.theme.TextInactive

/**
 * Animated page indicator dots for pagers/carousels
 *
 * Selected indicator is wider (pill shape), unselected are circles.
 * Animates width and color transitions smoothly.
 *
 * @param pageCount Total number of pages
 * @param currentPage Currently selected page index
 * @param selectedColor Color for the selected indicator
 * @param unselectedColor Color for unselected indicators
 * @param indicatorSize Size of the indicator dots
 * @param selectedWidth Width of the selected indicator
 * @param spacing Horizontal spacing between indicators
 * @param modifier Modifier for the row
 */
@Composable
fun PageIndicator(
    pageCount: Int,
    currentPage: Int,
    modifier: Modifier = Modifier,
    selectedColor: Color = TextBright,
    unselectedColor: Color = TextBright.copy(alpha = 0.4f),
    indicatorSize: Dp = 6.dp,
    selectedWidth: Dp = 24.dp,
    spacing: Dp = 3.dp
) {
    if (pageCount <= 0) return

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(pageCount) { index ->
            val isSelected = currentPage == index
            val width by animateDpAsState(
                targetValue = if (isSelected) selectedWidth else indicatorSize,
                label = "indicator_width"
            )
            val color by animateColorAsState(
                targetValue = if (isSelected) selectedColor else unselectedColor,
                label = "indicator_color"
            )
            Box(
                modifier = Modifier
                    .padding(horizontal = spacing)
                    .width(width)
                    .height(indicatorSize)
                    .clip(RoundedCornerShape(indicatorSize / 2))
                    .background(color)
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF1A1A1A)
@Composable
private fun PageIndicatorPreview() {
    PageIndicator(
        pageCount = 5,
        currentPage = 2
    )
}

@Composable
fun PageIndicatorCircle(
    pageCount: Int,
    currentPage: Int,
    modifier: Modifier = Modifier,
    selectedColor: Color = BackgroundLight,
    unselectedColor: Color = TextInactive,
    size: Dp = 8.dp,
    sizeBorder: Dp = 4.dp,
    spacing: Dp = 7.dp
) {
    if (pageCount <= 0) return

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ){
        Row(
            horizontalArrangement = Arrangement.spacedBy(spacing),
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(pageCount) { index ->
                val isSelected = currentPage == index
                val color by animateColorAsState(
                    targetValue = if (isSelected) selectedColor else unselectedColor,
                    label = "indicator_color"
                )
                Box(
                    contentAlignment = Alignment.Center
                ){
                    if (isSelected) {
                        Spacer(
                            modifier = Modifier
                                .size(size + sizeBorder)
                                .clip(CircleShape)
                                .background(color.copy(0.5f))
                        )
                    }
                    Spacer(
                        modifier = Modifier
                            .size(size)
                            .clip(CircleShape)
                            .background(color)
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF1A1A1A)
@Composable
private fun PageIndicatorCirclePreview() {
    PageIndicatorCircle(
        pageCount = 5,
        currentPage = 2
    )
}
