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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
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
import com.videomaker.aimusic.ui.theme.Neutral_N900
import com.videomaker.aimusic.ui.theme.Primary

@Composable
fun MediaPrivacyScreen(selectedId: String, onSelect: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.media_privacy_title),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
        )
        Spacer(Modifier.height(24.dp))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            MEDIA_PRIVACY_ITEMS.forEach { item ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(32.dp))
                        .background(Color.Black.copy(alpha = 0.2f))
                        .border(
                            width = 0.83.dp,
                            color = if (item.id == selectedId) item.colorContent else Color(0xFF404040),
                            shape = RoundedCornerShape(32.dp),
                        )
                        .clickableSingle { onSelect(item.id) },
                ) {
                    Image(
                        painter = painterResource(if (item.id == selectedId) item.imageRes else item.imageRes2),
                        contentDescription = null,
                        contentScale = ContentScale.FillHeight,
                        modifier = Modifier
                            .height(120.dp)
                            .clip(RoundedCornerShape(topStart = 32.dp, bottomStart = 32.dp))
                            .align(Alignment.CenterStart),
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.CenterStart)
                            .padding(start = 110.dp, end = 56.dp),
                    ) {
                        Text(
                            text = stringResource(item.titleRes),
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(item.subtitleRes),
                            color = Neutral_N600,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Normal,
                        )
                    }
                    if (item.id == selectedId) {
                        Icon(
                            painter = painterResource(R.drawable.ic_checkmark),
                            contentDescription = null,
                            tint = Color(0xFF151515),
                            modifier = Modifier
                                .padding(end = 16.dp)
                                .align(Alignment.CenterEnd)
                                .size(32.dp)
                                .background(item.colorContent, CircleShape)
                                .padding(6.dp),
                        )
                    }

                    if (item.id == "private_mode") {
                        Text(
                            text = "RECOMMENDED",
                            color = if (item.id == selectedId) Neutral_N900 else Neutral_N600,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.W800,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .background(if (item.id == selectedId) Primary else Color(0xFF404040), RoundedCornerShape(bottomStart = 22.dp, topEnd = 22.dp))
                                .padding(end = 24.dp, start = 16.dp, top = 4.dp, bottom = 2.dp),
                        )
                    }
                }
            }
        }
    }
}
