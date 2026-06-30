package com.videomaker.aimusic.modules.onboardingsurvey

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.videomaker.aimusic.R
import com.videomaker.aimusic.ui.components.ModifierExtension.clickableSingle
import com.videomaker.aimusic.ui.theme.Primary
import com.videomaker.aimusic.ui.theme.VideoMakerTheme

@Composable
fun AiLevelScreen(
    items: List<AiLevelItem>,
    selectedId: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 16.dp)
            .padding(bottom = 96.dp),
    ) {
        Text(
            text = stringResource(R.string.ai_level_title),
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 30.sp,
        )
        Spacer(Modifier.height(24.dp))

        Row(
            modifier = Modifier.height(intrinsicSize = IntrinsicSize.Max),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items.forEach { item ->
                AiLevelCard(
                    item = item,
                    selected = item.id == selectedId,
                    onClick = { onSelect(item.id) },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        val isExploreLaterSelected = selectedId == "explore_later"
        val exploreLaterShape = RoundedCornerShape(16.dp)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(exploreLaterShape)
                .background(
                    if (isExploreLaterSelected) Primary.copy(alpha = 0.1f) else Color.Black.copy(
                        alpha = 0.2f
                    )
                )
                .border(
                    width = if (isExploreLaterSelected) 2.dp else 1.dp,
                    color = if (isExploreLaterSelected) Primary else Color(0xFF404040),
                    shape = exploreLaterShape
                )
                .clickableSingle { onSelect("explore_later") }
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(R.drawable.ic_clock),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                colorFilter = ColorFilter.tint(
                    if (isExploreLaterSelected) Primary else Color(0xFF9E9E9E)
                )
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = stringResource(R.string.survey_feature_explore_later),
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            if (isExploreLaterSelected) {
                Image(
                    painter = painterResource(R.drawable.img_checkbox),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
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
            .fillMaxHeight()
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
                    .padding(top = 8.dp, bottom = 24.dp),
            )
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

@Preview(showBackground = true, backgroundColor = 0xFF1A1A1A)
@Composable
private fun AiLevelScreenPreview() {
    VideoMakerTheme {
        AiLevelScreen(
            items = AI_LEVEL_ITEMS,
            selectedId = "light_touch",
            onSelect = {},
        )
    }
}

