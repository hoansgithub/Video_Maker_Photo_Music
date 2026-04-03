package com.videomaker.aimusic.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.videomaker.aimusic.ui.theme.VideoMakerTheme

/**
 * Container for displaying multiple content tags
 *
 * Position this at the top-left of carousel items using:
 * ```
 * Box {
 *     // Your carousel item content
 *     ContentTags(
 *         tags = listOf(ContentTag.HOT, ContentTag.TRENDING),
 *         modifier = Modifier.align(Alignment.TopStart)
 *     )
 * }
 * ```
 *
 * @param tags List of content tags to display
 * @param modifier Optional modifier
 */
@Composable
fun ContentTags(
    tags: List<ContentTag>,
    modifier: Modifier = Modifier
) {
    if (tags.isEmpty()) return

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        tags.forEach { tag ->
            TagBadge(tag = tag)
        }
    }
}

/**
 * Helper to build content tags list from boolean flags
 *
 * Usage in ViewModel/Mapper:
 * ```kotlin
 * val tags = buildContentTags(
 *     isHot = template.isHot,
 *     isTrending = template.isTrending,
 *     isNew = template.isNew,
 *     isFeatured = template.isFeatured
 * )
 * ```
 *
 * @param isHot Whether content has "Hot" tag
 * @param isTrending Whether content has "Trending" tag
 * @param isNew Whether content has "New" tag (auto-calculated or from DB)
 * @param isFeatured Whether content has "Featured" tag
 * @return List of active content tags
 */
fun buildContentTags(
    isHot: Boolean = false,
    isTrending: Boolean = false,
    isNew: Boolean = false,
    isFeatured: Boolean = false
): List<ContentTag> = buildList {
    // Note: If you want priority order (show max 2 tags), uncomment the following:
    // val maxTags = 2
    // var count = 0

    if (isFeatured) add(ContentTag.FEATURED)
    if (isHot) add(ContentTag.HOT)
    if (isTrending) add(ContentTag.TRENDING)
    if (isNew) add(ContentTag.NEW)

    // To limit to max 2 tags, replace above with:
    // if (isFeatured && count < maxTags) { add(ContentTag.FEATURED); count++ }
    // if (isHot && count < maxTags) { add(ContentTag.HOT); count++ }
    // if (isTrending && count < maxTags) { add(ContentTag.TRENDING); count++ }
    // if (isNew && count < maxTags) { add(ContentTag.NEW); count++ }
}

// ============================================
// PREVIEWS
// ============================================

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun ContentTagsSinglePreview() {
    VideoMakerTheme {
        ContentTags(tags = listOf(ContentTag.HOT))
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun ContentTagsDoublePreview() {
    VideoMakerTheme {
        ContentTags(tags = listOf(ContentTag.HOT, ContentTag.TRENDING))
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun ContentTagsAllPreview() {
    VideoMakerTheme {
        ContentTags(
            tags = listOf(
                ContentTag.FEATURED,
                ContentTag.HOT,
                ContentTag.TRENDING,
                ContentTag.NEW
            )
        )
    }
}
