package com.videomaker.aimusic.widget.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
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
import com.videomaker.aimusic.ui.components.ShimmerPlaceholder
import com.videomaker.aimusic.ui.theme.Neutral_N500
import com.videomaker.aimusic.ui.theme.TextOnSecondary


@Composable
fun SmartSearchWidget(
    list: List<VideoTemplate>,
    onClickSearch: () -> Unit,
    onClick: (VideoTemplate) -> Unit,
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
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 12.dp)
                .fillMaxWidth()
                .background(Color.White.copy(0.1f),RoundedCornerShape(16.dp))
                .border(1.dp,Color.White.copy(0.16f),RoundedCornerShape(16.dp))
                .clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null,
                    onClick = onClickSearch
                )
                .padding(vertical = 12.dp, horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Image(
                painter = painterResource(R.drawable.app_icon_loading),
                contentDescription = "",
                modifier = Modifier.width(24.dp),
                contentScale = ContentScale.FillWidth
            )
            Text(
                text = stringResource(R.string.widget_smart_search_description),
                color = Neutral_N500,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = FontWeight.W500,
                    fontSize = 15.sp
                ),
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = Neutral_N500,
                modifier = Modifier.size(24.dp)
            )
        }


        Text(
            text = stringResource(R.string.widget_smart_search_trending),
            color = TextOnSecondary,
            style = MaterialTheme.typography.bodySmall.copy(
                fontWeight = FontWeight.W600,
                fontSize = 10.sp
            ),
            modifier = Modifier
                .padding(start = 12.dp),
        )

        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp),
            userScrollEnabled = false,
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {

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
                            .width(96.dp)
                            .height(129.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .clickable(
                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                indication = null,
                                onClick = { onClick(template) }
                            )
                    )
                } else {
                    ShimmerPlaceholder(
                        modifier = Modifier
                            .width(96.dp)
                            .height(129.dp),
                        cornerRadius = 18.dp
                    )
                }
            }
        }
    }
}