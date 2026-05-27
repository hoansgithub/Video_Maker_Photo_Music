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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import coil.decode.BitmapFactoryDecoder
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Precision
import coil.size.Size
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView
import com.videomaker.aimusic.R
import com.videomaker.aimusic.core.util.NumberFormatter
import com.videomaker.aimusic.ui.theme.AppDimens
import com.videomaker.aimusic.ui.theme.Black60
import com.videomaker.aimusic.ui.theme.GoldAccent
import com.videomaker.aimusic.ui.theme.Gray200
import com.videomaker.aimusic.ui.theme.Gray600
import com.videomaker.aimusic.ui.theme.SurfaceDark
import com.videomaker.aimusic.ui.theme.TemplateBadgeBackground
import com.videomaker.aimusic.ui.theme.TextOnPrimary
import com.videomaker.aimusic.ui.theme.TextPrimary
import com.videomaker.aimusic.ui.theme.White12
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val BadgeShape = RoundedCornerShape(999.dp)

/**
 * Shared template card used in both the gallery feed and search results.
 *
 * Layout (bottom-to-top):
 * 1. Thumbnail image (AsyncImage, shimmer while loading)
 * 2. Bottom gradient scrim (ensures name is readable on any image)
 * 3. Template name — bottom-start, max 2 lines
 * 4. ADS/PRO badge — top-end (ADS if premium & locked, nothing if unlocked)
 * 5. Use count badge — bottom-end (only when useCount > 0)
 */
@Composable
fun TemplateCard(
    name: String,
    thumbnailPath: String,
    aspectRatio: Float,
    isPremium: Boolean,
    isUnlocked: Boolean = true,  // Default true for backward compatibility
    isShowOption: Boolean = false,
    showHotTag: Boolean = false,  // Show for top 10 templates
    useCount: Long,  // Kept for backward compatibility, prefer viewCount
    viewCount: Long = useCount,  // Display count (defaults to useCount if not provided)
    onClickDelete: () -> Unit = {},
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dimens = AppDimens.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val view = LocalView.current

    var expanded by remember { mutableStateOf(false) }

    // Visibility detection: animate WebP only when on screen, static first frame when off screen.
    // This saves CPU (no frame decoding) and memory (single bitmap vs animation frames).
    var isOnScreen by remember { mutableStateOf(true) }

    // Simple retry mechanism (3 attempts max) - keyed to thumbnailPath for lazy list safety
    var retryCount by remember(thumbnailPath) { mutableIntStateOf(0) }
    var retryTrigger by remember(thumbnailPath) { mutableIntStateOf(0) }

    val imageRequest = remember(thumbnailPath, retryTrigger, isOnScreen) {
        ImageRequest.Builder(context)
            .data(thumbnailPath)
            .size(Size(200, 350))  // Reduced from 400x700 to 200x350 (4x less data!)
            .precision(Precision.INEXACT)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .memoryCacheKey("grid_${thumbnailPath}_${if (isOnScreen) "anim" else "static"}")
            .diskCacheKey("grid_$thumbnailPath")
            .crossfade(true)  // Smooth fade-in animation
            .crossfade(200)  // 200ms crossfade duration
            .apply {
                if (!isOnScreen) {
                    // Static first frame only — bypasses animated WebP decoder
                    decoderFactory(BitmapFactoryDecoder.Factory())
                }
            }
            .listener(
                onError = { request, result ->
                    android.util.Log.e("TemplateCard", "Failed to load thumbnail (attempt ${retryCount + 1}/3): ${thumbnailPath}, error: ${result.throwable.message}")

                    // Auto-retry silently (no user message)
                    if (retryCount < 2) {  // 0, 1 = retry; 2 = give up
                        retryCount++
                        coroutineScope.launch {
                            delay(1000L * retryCount)  // 1s, 2s delay
                            retryTrigger++  // Trigger reload
                        }
                    }
                },
                onSuccess = { _, _ ->
                    retryCount = 0  // Reset on success
                }
            )
            .build()
    }

    Card(
        onClick = onClick,
        modifier = modifier
            .aspectRatio(aspectRatio.coerceIn(0.3f, 3f))
            .onGloballyPositioned { coordinates ->
                val bounds = coordinates.boundsInWindow()
                val screenHeight = view.height.toFloat()
                isOnScreen = bounds.bottom > 0f && bounds.top < screenHeight
            },
        shape = RoundedCornerShape(dimens.radiusLg),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {

            // Thumbnail with loading/error states
            if (thumbnailPath.isNotEmpty()) {
                SubcomposeAsyncImage(
                    model = imageRequest,
                    contentDescription = name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    loading = {
                        ShimmerPlaceholder(
                            modifier = Modifier.fillMaxSize(),
                            cornerRadius = 0.dp
                        )
                    },
                    error = {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(SurfaceDark),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_choose_img),
                                contentDescription = "Failed to load",
                                modifier = Modifier.size(48.dp),
                                tint = Gray600
                            )
                        }
                    }
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

            // Content tag — top-start (only in Gallery tab)
            if (showHotTag) {
                ContentTags(
                    tags = listOf(ContentTag.HOT),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(dimens.spaceSm)
                )
            }

            // ADS badge — top-end (only show for premium templates that are NOT unlocked)
            if (isPremium && !isUnlocked) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(dimens.spaceSm)
                        .background(color = GoldAccent, shape = RoundedCornerShape(dimens.radiusMd))
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_play),
                            contentDescription = "Watch ad to unlock",
                            tint = TextOnPrimary,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = "ADS",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = TextOnPrimary
                        )
                    }
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
                        end = if (viewCount > 0) 64.dp else 10.dp,
                        bottom = 10.dp
                    )
            )

            // View count badge — bottom-end (shows viewCount, not useCount)
            if (viewCount > 0) {
                val formattedViewCount = remember(viewCount) { NumberFormatter.formatCount(viewCount) }
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
                        painter = painterResource(R.drawable.ic_heart),
                        contentDescription = null,
                        tint = Gray200,
                        modifier = Modifier.size(10.dp)
                    )
                    Text(
                        text = formattedViewCount,
                        fontSize = 10.sp,
                        color = Gray200,
                        maxLines = 1
                    )
                }
            }

            if (isShowOption) {
                TemplateMore(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                ){
                    onClickDelete.invoke()
                }
            }
        }
    }
}

@Composable
fun TemplateMore(
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box (
        modifier = modifier
    ){
        IconButton(
            onClick = { expanded = true }
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_more_menu),
                contentDescription = "More",
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(Color(0xff282828).copy(0.7f))
                    .border(1.dp, Color.White.copy(0.12f))
                    .padding(4.dp),
            )
        }

        CustomDropdownMenuWithPainter(
            expanded = expanded,
            offset = DpOffset(-110.dp, 0.dp),
            onDismissRequest = { expanded = false }
        ) {
            CustomDropdownItemWithPainter(
                painter = painterResource(id = R.drawable.ic_unheart),
                title = "Unfavorite",
                onClick = {
                    onClick.invoke()
                    expanded = false
                },
                showDivider = false
            )
        }
    }
}