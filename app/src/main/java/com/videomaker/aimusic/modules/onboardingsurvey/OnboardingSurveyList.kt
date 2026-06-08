package com.videomaker.aimusic.modules.onboardingsurvey

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.videomaker.aimusic.R
import com.videomaker.aimusic.ui.components.ModifierExtension.clickableSingle
import com.videomaker.aimusic.ui.theme.Primary

@Composable
fun OnboardingSurveyList(
    config: OnboardingSurveyStepConfig,
    selectedIds: Set<String>,
    onToggle: (String) -> Unit,
    bottomPaddingDp: Dp,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    onItemPositioned: ((String, Offset) -> Unit)? = null,
) {
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = 16.dp,
            bottom = bottomPaddingDp + 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "survey_header") {
            Column(modifier = Modifier.padding(bottom = 4.dp)) {
                Text(
                    text = stringResource(config.titleRes),
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 30.sp,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(config.subtitleRes),
                    color = Color(0xFF9E9E9E),
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                )
            }
        }
        items(config.items, key = { it.id }) { item ->
            SurveyRow(
                item = item,
                selected = item.id in selectedIds,
                onClick = { onToggle(item.id) },
                modifier = if (onItemPositioned != null) {
                    Modifier.onGloballyPositioned { coords ->
                        val topLeft = coords.positionInRoot()
                        onItemPositioned(item.id, topLeft + Offset(coords.size.width / 2f, 0f))
                    }
                } else Modifier,
            )
        }
    }
}

@Composable
private fun SurveyRow(
    item: SurveyItem,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(120.dp))
            .background(if (selected) Primary.copy(0.12f) else Color.Black.copy(0.2f))
            .border(
                width = 1.dp,
                color = if (selected) Primary else Color(0xFF404040),
                shape = RoundedCornerShape(120.dp),
            )
            .clickableSingle { onClick() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(item.iconRes),
            contentDescription = null,
            modifier = Modifier.size(40.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(item.labelRes),
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
        SelectionIndicator(selected = selected)
    }
}

@Composable
private fun SelectionIndicator(selected: Boolean) {
    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) Primary else Color.Transparent)
            .border(
                width = if (selected) 0.dp else 2.dp,
                color = if (selected) Color.Transparent else Color(0xFF404040),
                shape = RoundedCornerShape(10.dp),
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(
                painter = painterResource(R.drawable.ic_checkmark),
                contentDescription = null,
                tint = Color.Black,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}
