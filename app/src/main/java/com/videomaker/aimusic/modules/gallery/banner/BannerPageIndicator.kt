package com.videomaker.aimusic.modules.gallery.banner

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Swipe indicator overlaid on the top-start of each home banner.
 *
 * Current page: 24×6dp pill, color #F3F3F3. Other pages: 6×6dp dots, color #222222.
 * 12dp padding around the row, radius 180 (fully rounded), animated width + color on page change.
 */
@Composable
fun BannerPageIndicator(
    pageCount: Int,
    currentPage: Int,
    modifier: Modifier = Modifier,
) {
    if (pageCount <= 1) return
    Row(
        modifier = modifier.padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(pageCount) { index ->
            val selected = index == currentPage
            val width by animateDpAsState(
                targetValue = if (selected) 24.dp else 6.dp,
                label = "banner_indicator_width"
            )
            val color by animateColorAsState(
                targetValue = if (selected) Color(0xFFF3F3F3) else Color(0xFF222222),
                label = "banner_indicator_color"
            )
            Box(
                modifier = Modifier
                    .height(6.dp)
                    .width(width)
                    .clip(RoundedCornerShape(180.dp))
                    .background(color)
            )
        }
    }
}
