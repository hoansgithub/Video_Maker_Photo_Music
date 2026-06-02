package com.videomaker.aimusic.modules.gallery.banner

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.decode.BitmapFactoryDecoder
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Precision
import coil.size.Size
import com.videomaker.aimusic.R
import com.videomaker.aimusic.domain.model.VideoTemplate
import com.videomaker.aimusic.ui.components.ModifierExtension.clickableSingle
import com.videomaker.aimusic.ui.components.ShimmerPlaceholder
import com.videomaker.aimusic.ui.theme.Neutral_N100
import com.videomaker.aimusic.ui.theme.Primary
import com.videomaker.aimusic.ui.theme.VideoMakerTheme

@Composable
fun BannerTemplateStyle(
    template: VideoTemplate,
    style: BannerTemplate,
    isCurrentPage: Boolean,
    shouldLoadImage: Boolean,
    onClick: () -> Unit,
) {
    val context = LocalContext.current

    // ✅ Only create image request if within visible range
    val imageRequest = if (shouldLoadImage) {
        remember(template.id, isCurrentPage) {
            ImageRequest.Builder(context)
                .data(template.thumbnailPath.ifEmpty { null })
                .size(Size(720, 405))  // Optimize for 16:9 carousel
                .precision(Precision.INEXACT)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .diskCachePolicy(CachePolicy.ENABLED)
                .memoryCacheKey("featured_${template.id}_${if (isCurrentPage) "anim" else "static"}")
                .diskCacheKey("featured_${template.id}")
                .crossfade(true)
                .crossfade(200)
                .apply {
                    if (!isCurrentPage) {
                        // Static first frame only — bypasses animated WebP decoder
                        decoderFactory(BitmapFactoryDecoder.Factory())
                    }
                }
                .build()
        }
    } else {
        null
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .fillMaxWidth()
            .aspectRatio(388 / 200f)
            .border(2.dp, Color.White.copy(0.12f), RoundedCornerShape(16.dp))
            .clickableSingle { onClick.invoke() }
    ) {
        // ✅ Only show image if request exists (within visible range)
        if (imageRequest != null) {
            AsyncImage(
                model = imageRequest,
                contentDescription = template.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            Image(
                painter = painterResource(if (style.style == 1) R.drawable.img_bg_banner_template1 else R.drawable.img_bg_banner_template2),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
            )
        } else {
            // Show placeholder for pages far from current
            ShimmerPlaceholder(
                modifier = Modifier.fillMaxSize(),
                cornerRadius = 0.dp
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(16.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {

                Row(
                    modifier = Modifier
                        .background(
                            if (style.style == 1) Color(0xFFFC19CF) else Color(0xFF19FCBF),
                            RoundedCornerShape(24.dp)
                        )
                        .border(1.dp, Color.White.copy(0.12f), RoundedCornerShape(24.dp))
                        .padding(horizontal = 6.dp, vertical = 3.5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        painter = painterResource(
                            if (style.style == 1) {
                                R.drawable.ic_mingcute_trending
                            } else {
                                R.drawable.ic_new_moon_symbol
                            }
                        ),
                        contentDescription = null,
                        tint = if (style.style == 1) Color(0xFFFDB9F0) else Color(0xFF028367)
                    )

                    Text(
                        text = if (style.style == 1) "TRENDING NOW" else "NEW ARRIVALS",
                        color = if (style.style == 1) Color(0xFFFDB9F0) else Color(0xFF028367),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.W700,
                        fontStyle = FontStyle.Italic
                    )
                }
                Text(
                    text = style.name,
                    color = Color(0xFFF6F6F6),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.W800,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                )
            }

            Row(
                modifier = Modifier
                    .background(Neutral_N100, RoundedCornerShape(160.dp))
                    .clickableSingle {
                        onClick.invoke()
                    }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "Try it",
                    color = Primary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.W600,
                )
                Icon(
                    painter = painterResource(R.drawable.ic_right_arrow),
                    contentDescription = null,
                    tint = Primary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Preview(widthDp = 375, heightDp = 812)
@Composable
private fun GalleryLoadingPreview() {
    VideoMakerTheme {
        Box(modifier = Modifier.fillMaxSize()) {
            BannerTemplateStyle(
                template = VideoTemplate(
                    id = "1",
                    name = "Summer Vibes",
                    songId = 1,
                    effectSetId = "e1",
                    aspectRatio = "9:16",
                    isPremium = true
                ),
                style = BannerTemplate(name = "tsstttttt", id = "", style = 1),
                isCurrentPage = true,
                shouldLoadImage = false,
                onClick = {}
            )
        }
    }
}

data class BannerTemplate(
    val name: String,
    val id: String,
    val style: Int,
)