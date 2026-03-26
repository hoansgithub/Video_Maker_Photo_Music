package com.videomaker.aimusic.widget.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.paint
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
import com.videomaker.aimusic.ui.components.AppAsyncImage
import com.videomaker.aimusic.ui.components.ShimmerPlaceholder
import com.videomaker.aimusic.ui.components.shadowCustom
import com.videomaker.aimusic.ui.theme.BackgroundLight
import com.videomaker.aimusic.ui.theme.Neutral_Black
import com.videomaker.aimusic.ui.theme.Primary
import com.videomaker.aimusic.ui.theme.TextOnSecondary
import kotlin.text.ifEmpty

@Composable
fun TrendingWidget(
    list: List<VideoTemplate>,
    onClickAdd: () -> Unit,
    onClick: () -> Unit,
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(24.dp))
            .fillMaxWidth()
            .background(Color(0xFF1A1A1A))
            .paint(
                painter = painterResource(R.drawable.bg_item_widget),
            )
            .padding(top = 16.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Image(
                painter = painterResource(R.drawable.img_logo_ipsum),
                contentDescription = "",
                modifier = Modifier.width(44.dp),
                contentScale = ContentScale.FillWidth
            )
            Text(
                text = stringResource(R.string.widget_recently),
                color = TextOnSecondary,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = FontWeight.W600,
                    fontSize = 10.sp
                ),
            )
        }

        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp),
            userScrollEnabled = false,
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            item {
                Box(
                    modifier = Modifier
                        .width(120.dp)
                        .height(164.dp)
                        .background(Neutral_Black,RoundedCornerShape(18.dp))
                        .clickable(
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            indication = null,
                            onClick = onClickAdd
                        )
                        .padding(5.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Spacer(
                        Modifier
                            .matchParentSize()
                            .paint(
                                painterResource(R.drawable.bg_button_in_shadown),
                                contentScale = ContentScale.Crop
                            )
                    )

                    Icon(
                        painter = painterResource(R.drawable.ic_circle_plus_v2),
                        contentDescription = "",
                        tint = Primary,
                        modifier = Modifier
                            .size(25.dp)
                    )
                }
            }

            items(list, key = {it.id}) { template ->

                // ✅ Only create image request if within visible range
                val imageRequest = remember(template.id) {
                    ImageRequest.Builder(context)
                        .data(template.thumbnailPath.ifEmpty { null })
                        .size(Size(720, 405))  // Optimize for 16:9 carousel
                        .precision(Precision.INEXACT)
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .memoryCacheKey("featured_${template.id}_${if (false) "anim" else "static"}")
                        .diskCacheKey("featured_${template.id}")
                        .crossfade(true)
                        .crossfade(200)
                        .apply {
                            if (true) {
                                // Static first frame only — bypasses animated WebP decoder
                                decoderFactory(BitmapFactoryDecoder.Factory())
                            }
                        }
                        .build()
                }

                if (imageRequest != null) {
                    AsyncImage(
                        model = imageRequest,
                        contentDescription = "",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .width(120.dp)
                            .height(164.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .clickable(
                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                indication = null,
                                onClick = onClick
                            )
                    )
                } else {
                    ShimmerPlaceholder(
                        modifier = Modifier
                            .width(120.dp)
                            .height(164.dp),
                        cornerRadius = 18.dp
                    )
                }
            }
        }
    }
}


@Preview(
    showBackground = true,
    backgroundColor = 0xFF0A0A0A,
)
@Composable
private fun TrendingWidgetPreview() {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0A))
    ) {
        TrendingWidget(
            listOf(
                VideoTemplate(
                    id = "23",
                    name = "132esd",
                    songId = 123L,
                    effectSetId = "23"
                ),
                VideoTemplate(
                    id = "123",
                    name = "132esd",
                    songId = 123L,
                    effectSetId = "23"
                ),
                VideoTemplate(
                    id = "223",
                    name = "132esd",
                    songId = 123L,
                    effectSetId = "23"
                ),
            ),
            onClickAdd = {},
            onClick = {}
        )
    }
}