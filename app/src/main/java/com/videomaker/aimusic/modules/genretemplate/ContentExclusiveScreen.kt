package com.videomaker.aimusic.modules.genretemplate

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.videomaker.aimusic.R
import com.videomaker.aimusic.ui.components.ModifierExtension.clickableSingle
import com.videomaker.aimusic.ui.theme.Neutral_N600
import com.videomaker.aimusic.ui.theme.Primary

/** Shared Style-B layout: title + a row of 2 image/title/subtitle cards (single-select). */
@Composable
fun ChoiceScreen(
    titleRes: Int,
    items: List<ChoiceItem>,
    selectedId: String,
    onSelect: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(60.dp))
        Text(
            text = stringResource(titleRes),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
        )
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items.forEach { item ->
                ChoiceCard(
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
private fun ChoiceCard(
    item: ChoiceItem,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .height(280.dp)
            .background(Color.Black.copy(alpha = 0.2f))
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) Primary else Color(0xFF404040),
                shape = shape,
            )
            .clickableSingle { onClick() }
            .padding(vertical = 16.dp, horizontal = 12.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .heightIn(min = 144.dp)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                val imageModifier = if (item.imageFillWidth) {
                    Modifier.fillMaxWidth().height(item.imageSize)
                } else {
                    Modifier.size(item.imageSize)
                }
                Image(
                    painter = painterResource(item.imageRes),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = imageModifier,
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(item.titleRes),
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(item.subtitleRes),
                color = Neutral_N600,
                fontSize = 12.sp,
                fontWeight = FontWeight.Normal,
                textAlign = TextAlign.Center,
            )
        }
        if (selected) {
            Image(
                painter = painterResource(R.drawable.img_checkbox),
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(24.dp),
            )
        }
    }
}

@Composable
fun ContentExclusiveScreen(selectedId: String, onSelect: (String) -> Unit) {
    ChoiceScreen(
        titleRes = R.string.content_exclusive_title,
        items = CONTENT_EXCLUSIVE_ITEMS,
        selectedId = selectedId,
        onSelect = onSelect,
    )
}
