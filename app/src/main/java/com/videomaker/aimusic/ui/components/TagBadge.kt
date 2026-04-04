package com.videomaker.aimusic.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.videomaker.aimusic.R
import com.videomaker.aimusic.ui.theme.HotGradient
import com.videomaker.aimusic.ui.theme.TrendingGradient
import com.videomaker.aimusic.ui.theme.VideoMakerTheme

/**
 * Content tag types for templates and songs
 */
enum class ContentTag {
    HOT,
    TRENDING,
    NEW,
    FEATURED
}

/**
 * Tag badge with vertical gradient background
 *
 * Design specs:
 * - Capsule shape with rounded corners on top-right, bottom-right, bottom-left
 * - Top-left corner is sharp (0dp) so badge appears attached to item
 * - Vertical linear gradient background
 * - Text color: White
 *
 * @param tag The content tag type
 * @param modifier Optional modifier
 */
@Composable
fun TagBadge(
    tag: ContentTag,
    modifier: Modifier = Modifier
) {
    val (text, gradient) = when (tag) {
        ContentTag.HOT -> stringResource(R.string.tag_hot) to HotGradient
        ContentTag.TRENDING -> stringResource(R.string.tag_trending) to TrendingGradient
        ContentTag.NEW -> stringResource(R.string.tag_new) to HotGradient  // Use hot gradient for "New"
        ContentTag.FEATURED -> stringResource(R.string.tag_featured) to TrendingGradient  // Use trending gradient for "Featured"
    }

    Box(
        modifier = modifier
            .background(
                brush = Brush.verticalGradient(
                    colors = gradient.map { Color(it.value) }
                ),
                shape = RoundedCornerShape(
                    topStart = 0.dp,      // Sharp corner (attached to top-left)
                    topEnd = 12.dp,       // Rounded
                    bottomEnd = 12.dp,    // Rounded
                    bottomStart = 12.dp   // Rounded
                )
            )
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            lineHeight = 12.sp
        )
    }
}

/**
 * Preview for Hot tag
 */
@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun TagBadgeHotPreview() {
    VideoMakerTheme {
        TagBadge(tag = ContentTag.HOT)
    }
}

/**
 * Preview for Trending tag
 */
@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun TagBadgeTrendingPreview() {
    VideoMakerTheme {
        TagBadge(tag = ContentTag.TRENDING)
    }
}
