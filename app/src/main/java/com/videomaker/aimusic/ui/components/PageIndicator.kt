package com.videomaker.aimusic.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Animated page indicator dots for pagers/carousels
 *
 * Selected indicator is wider (pill shape), unselected are circles.
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
    selectedColor: Color = Color.White,
    unselectedColor: Color = Color.White.copy(alpha = 0.4f),
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
            Box(
                modifier = Modifier
                    .padding(horizontal = spacing)
                    .then(
                        if (isSelected) {
                            Modifier
                                .width(selectedWidth)
                                .height(indicatorSize)
                        } else {
                            Modifier.size(indicatorSize)
                        }
                    )
                    .clip(RoundedCornerShape(indicatorSize / 2))
                    .background(if (isSelected) selectedColor else unselectedColor)
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
