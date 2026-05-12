package com.videomaker.aimusic.modules.genretemplate

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Precision
import coil.size.Size
import com.videomaker.aimusic.R
import com.videomaker.aimusic.domain.model.VideoTemplate
import com.videomaker.aimusic.ui.components.ContentTag
import com.videomaker.aimusic.ui.components.ContentTags
import com.videomaker.aimusic.ui.components.ShimmerPlaceholder
import com.videomaker.aimusic.ui.theme.AppDimens
import com.videomaker.aimusic.ui.theme.Gray200
import com.videomaker.aimusic.ui.theme.Gray600
import com.videomaker.aimusic.ui.theme.Primary
import com.videomaker.aimusic.ui.theme.SurfaceDark
import com.videomaker.aimusic.ui.theme.TemplateBadgeBackground
import com.videomaker.aimusic.ui.theme.White12
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun TemplatePickScreen(
    templates: List<VideoTemplate>,
    selectedTemplate: VideoTemplate?,
    onTemplateSelect: (VideoTemplate) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(76.dp))

        Text(
            text = stringResource(R.string.template_pick_title),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.template_pick_subtitle),
            fontSize = 14.sp,
            color = Color.White.copy(alpha = 0.7f)
        )

        Spacer(modifier = Modifier.height(24.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(templates, key = { it.id }) { template ->
                TemplateItem(
                    template = template,
                    isSelected = selectedTemplate?.id == template.id,
                    onClick = { onTemplateSelect(template) }
                )
            }
        }
    }
}

@Composable
private fun TemplateItem(
    template: VideoTemplate,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val dimens = AppDimens.current
    val shape = RoundedCornerShape(12.dp)
    val borderColor = if (isSelected) Primary else Color.White.copy(alpha = 0.1f)
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var expanded by remember { mutableStateOf(false) }

    // Simple retry mechanism (3 attempts max) - keyed to thumbnailPath for lazy list safety
    var retryCount by remember(template.thumbnailPath) { mutableIntStateOf(0) }
    var retryTrigger by remember(template.thumbnailPath) { mutableIntStateOf(0) }

    val imageRequest = remember(template.thumbnailPath, retryTrigger) {
        ImageRequest.Builder(context)
            .data(template.thumbnailPath)
            .size(Size(200, 350))  // Reduced from 400x700 to 200x350 (4x less data!)
            .precision(Precision.INEXACT)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .crossfade(true)  // Smooth fade-in animation
            .crossfade(200)  // 200ms crossfade duration
            .listener(
                onError = { request, result ->
                    android.util.Log.e("TemplateCard", "Failed to load thumbnail (attempt ${retryCount + 1}/3): ${template.thumbnailPath}, error: ${result.throwable.message}")

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

    Box(
        modifier = Modifier
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .padding(top = 10.dp)
                .fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .border(2.dp, borderColor, shape)
            ) {
                // Thumbnail with loading/error states
                if (template.thumbnailPath.isNotEmpty()) {
                    SubcomposeAsyncImage(
                        model = imageRequest,
                        contentDescription = template.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                        loading = {
                            // Show shimmer while loading
                            ShimmerPlaceholder(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(350.dp),
                                cornerRadius = 0.dp
                            )
                        },
                        error = { errorState ->
                            // Show friendly placeholder on error (auto-retrying up to 3 times)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(350.dp)
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
                        },
                        success = {
                            // Show the loaded image
                            SubcomposeAsyncImageContent()
                        }
                    )
                } else {
                    ShimmerPlaceholder(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(350.dp),
                        cornerRadius = 0.dp
                    )
                }

                ContentTags(
                    tags = listOf(ContentTag.HOT),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(dimens.spaceSm)
                )

                // Use count badge — bottom-end
                if (template.useCount > 0) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(dimens.spaceSm)
                            .background(color = TemplateBadgeBackground, shape = RoundedCornerShape(999.dp))
                            .border(width = 1.dp, color = White12, shape = RoundedCornerShape(999.dp))
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
                            text = formatUseCount(template.useCount),
                            fontSize = 10.sp,
                            color = Gray200,
                            maxLines = 1
                        )
                    }
                }
            }

            // Template name at bottom
            Text(
                text = template.name,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White,
                modifier = Modifier.padding(8.dp)
            )
        }

        if (isSelected) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 8.dp)
            ) {
                Image(
                    painter = painterResource(R.drawable.img_checkbox),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
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
