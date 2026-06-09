package com.videomaker.aimusic.modules.onboardingsurvey

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.videomaker.aimusic.R
import com.videomaker.aimusic.ui.components.ModifierExtension.clickableSingle
import com.videomaker.aimusic.ui.theme.Primary

@Composable
fun AiLevelScreen(
    items: List<AiLevelItem>,
    selectedId: String,
    onSelect: (String) -> Unit,
    bottomPaddingDp: Dp,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 16.dp)
            .padding(bottom = bottomPaddingDp + 16.dp),
    ) {
        Text(
            text = stringResource(R.string.ai_level_title),
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 30.sp,
        )
        Spacer(Modifier.height(24.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items.forEach { item ->
                AiLevelCard(
                    item = item,
                    selected = item.id == selectedId,
                    onClick = { onSelect(item.id) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun AiLevelCard(
    item: AiLevelItem,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(Color.Black.copy(alpha = 0.2f))
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) Primary else Color(0xFF404040),
                shape = shape,
            )
            .clickableSingle { onClick() },
    ) {
        Column {
            Image(
                painter = painterResource(item.imageRes),
                contentDescription = null,
                contentScale = ContentScale.FillWidth,
                modifier = Modifier
                    .fillMaxWidth(),
            )
            Text(
                text = stringResource(item.titleRes),
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 12.dp),
            )
            // Banner pill
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
                    .padding(bottom = 18.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(item.bannerBgColor).copy(alpha = item.bannerBgAlpha)),
            ) {
                Text(
                    text = stringResource(item.bannerRes),
                    color = Color(item.bannerTextColor),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Normal,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp, horizontal = 4.dp),
                )
            }
        }

        if (selected) {
            Image(
                painter = painterResource(R.drawable.img_checkbox),
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(24.dp),
            )
        }
    }
}
