package com.videomaker.aimusic.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Precision
import coil.size.Size
import com.videomaker.aimusic.ui.components.bottomGradientOverlay
import com.videomaker.aimusic.ui.theme.AppDimens
import com.videomaker.aimusic.ui.theme.Black60
import com.videomaker.aimusic.ui.theme.GoldAccent
import com.videomaker.aimusic.ui.theme.Gray200
import com.videomaker.aimusic.ui.theme.TemplateBadgeBackground
import com.videomaker.aimusic.ui.theme.TextOnPrimary
import com.videomaker.aimusic.ui.theme.TextPrimary
import com.videomaker.aimusic.ui.theme.White12

private val BadgeShape = RoundedCornerShape(999.dp)

/**
 * Shared template card used in both the gallery feed and search results.
 *
 * Layout (bottom-to-top):
 * 1. Thumbnail image (AsyncImage, shimmer while loading)
 * 2. Bottom gradient scrim (ensures name is readable on any image)
 * 3. Template name — bottom-start, max 2 lines
 * 4. PRO badge — top-end (only when isPremium)
 * 5. Use count badge — bottom-end (only when useCount > 0)
 */
@Composable
fun TemplateCard(
    name: String,
    thumbnailPath: String,
    aspectRatio: Float,
    isPremium: Boolean,
    useCount: Long,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dimens = AppDimens.current
    val context = LocalContext.current

    val imageRequest = remember(thumbnailPath) {
        ImageRequest.Builder(context)
            .data(thumbnailPath)
            .size(Size(400, 700))
            .precision(Precision.INEXACT)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .build()
    }

    Card(
        onClick = onClick,
        modifier = modifier.aspectRatio(aspectRatio.coerceIn(0.3f, 3f)),
        shape = RoundedCornerShape(dimens.radiusLg),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {

            // Thumbnail
            if (thumbnailPath.isNotEmpty()) {
                AsyncImage(
                    model = imageRequest,
                    contentDescription = name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                ShimmerPlaceholder(
                    modifier = Modifier.fillMaxSize(),
                    cornerRadius = 0.dp
                )
            }

            // Bottom gradient scrim — ensures name is readable over any image
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .bottomGradientOverlay(listOf(Color.Transparent, Color.Transparent, Black60))
            )

            // PRO badge — top-end
            if (isPremium) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(dimens.spaceSm)
                        .background(color = GoldAccent, shape = RoundedCornerShape(dimens.radiusMd))
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "PRO",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = TextOnPrimary
                    )
                }
            }

            // Template name — bottom-start
            Text(
                text = name,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(
                        start = 10.dp,
                        end = if (useCount > 0) 64.dp else 10.dp,
                        bottom = 10.dp
                    )
            )

            // Use count badge — bottom-end
            if (useCount > 0) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(dimens.spaceSm)
                        .background(color = TemplateBadgeBackground, shape = BadgeShape)
                        .border(width = 1.dp, color = White12, shape = BadgeShape)
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Repeat,
                        contentDescription = null,
                        tint = Gray200,
                        modifier = Modifier.size(10.dp)
                    )
                    Text(
                        text = formatUseCount(useCount),
                        fontSize = 10.sp,
                        color = Gray200,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

private fun formatUseCount(count: Long): String = when {
    count >= 1_000_000 -> {
        val v = count / 1_000_000.0
        if (v % 1.0 == 0.0) "${v.toLong()}M" else "%.1fM".format(v)
    }
    count >= 1_000 -> {
        val v = count / 1_000.0
        if (v % 1.0 == 0.0) "${v.toLong()}K" else "%.1fK".format(v)
    }
    else -> count.toString()
}