package com.videomaker.aimusic.modules.picker

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.material3.Text
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.videomaker.aimusic.R
import com.videomaker.aimusic.core.analytics.Analytics
import com.videomaker.aimusic.ui.components.ModifierExtension.clickableSingle
import com.videomaker.aimusic.ui.components.PrimaryButton

private val SheetBackground = Color(0xFF1C1C1E)
private val CardBackground = Color(0xFF2A2A2C)
private val TitleColor = Color(0xFFF8FAFC)
private val DescColor = Color(0xFF8C8C8C)
private val ItemTextColor = Color(0xFFA5A5A5)

/**
 * "Use the Right Photos" guide popup shown when entering the picker from the AI flow (after
 * media permission is granted). Reuses the user-provided check/cross icons + sample images.
 */
@Composable
fun AiPhotoGuideDialog(
    onSelectPhoto: () -> Unit,
    onDismiss: () -> Unit
) {
    // System displays popup.
    LaunchedEffect(Unit) { Analytics.trackAiGuidePhotoRender() }

    val handleSelect = {
        Analytics.trackAiGuidePhotoSelect()
        onSelectPhoto()
    }
    val handleClose = {
        Analytics.trackAiGuidePhotoClose()
        onDismiss()
    }

    Dialog(
        onDismissRequest = handleClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .background(SheetBackground)
                    .padding(24.dp)
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.close),
                        tint = Color.White,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(24.dp)
                            .clickableSingle { handleClose() }
                    )
                }

                Text(
                    text = stringResource(R.string.ai_guide_title),
                    color = TitleColor,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.W700,
                    lineHeight = 31.2.sp,
                    letterSpacing = 1.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = stringResource(R.string.ai_guide_desc),
                    color = DescColor,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.W500,
                    lineHeight = 21.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(24.dp))

                GuideCard(
                    iconRes = R.drawable.ic_select_circle,
                    labels = listOf(
                        stringResource(R.string.ai_guide_good_front_facing),
                        stringResource(R.string.ai_guide_good_one_person),
                        stringResource(R.string.ai_guide_good_face_visible)
                    ),
                    sampleImage = R.drawable.img_guidle_right,
                )

                Spacer(modifier = Modifier.height(16.dp))

                GuideCard(
                    iconRes = R.drawable.ic_close_circle,
                    labels = listOf(
                        stringResource(R.string.ai_guide_bad_side_profile),
                        stringResource(R.string.ai_guide_bad_blurry),
                        stringResource(R.string.ai_guide_bad_group)
                    ),
                    sampleImage = R.drawable.img_guidle_error,
                )

                Spacer(modifier = Modifier.height(24.dp))

                PrimaryButton(
                    text = stringResource(R.string.ai_guide_cta),
                    onClick = handleSelect,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                )
            }
        }
    }
}

@Composable
private fun GuideCard(
    @DrawableRes iconRes: Int,
    labels: List<String>,
    @DrawableRes sampleImage: Int,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CardBackground)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            labels.forEach { label ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        painter = painterResource(iconRes),
                        contentDescription = null,
                        tint = Color.Unspecified,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = label,
                        color = ItemTextColor,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.W500,
                        lineHeight = 22.4.sp
                    )
                }
            }
        }

        Box(modifier = Modifier.size(106.dp)) {
            Image(
                painter = painterResource(sampleImage),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp))
            )
        }
    }
}
