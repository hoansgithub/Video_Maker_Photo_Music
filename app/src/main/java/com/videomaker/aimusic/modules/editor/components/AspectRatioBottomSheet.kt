package com.videomaker.aimusic.modules.editor.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.videomaker.aimusic.R
import com.videomaker.aimusic.domain.model.AspectRatio
import com.videomaker.aimusic.ui.theme.Gray500
import com.videomaker.aimusic.ui.theme.SplashBackground
import com.videomaker.aimusic.ui.theme.TextPrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SelectRatioBottomSheet(
    currentRatio: AspectRatio,
    onDismiss: () -> Unit,
    onConfirm: (AspectRatio) -> Unit
) {
    var selected by remember { mutableStateOf(currentRatio) }
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SplashBackground,
        dragHandle = null,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(top = 24.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header with title and apply button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.editor_select_video_ratio),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    modifier = Modifier.weight(1f)
                )

                // Apply button
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                        .clickable {
                            onConfirm(selected)
                            onDismiss()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = stringResource(R.string.confirm),
                        tint = SplashBackground,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // Ratio options grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                listOf(
                    AspectRatio.RATIO_16_9,
                    AspectRatio.RATIO_9_16,
                    AspectRatio.RATIO_4_5,
                    AspectRatio.RATIO_1_1
                ).forEach { ratio ->
                    RatioOptionCard(
                        ratio = ratio,
                        isSelected = ratio == selected,
                        onClick = { selected = ratio },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun RatioOptionCard(
    ratio: AspectRatio,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else Gray500
    val borderWidth = if (isSelected) 2.dp else 1.dp
    val backgroundColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        SplashBackground
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .border(borderWidth, borderColor, RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .clickable { onClick() }
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Aspect ratio icon
            AspectRatioIcon(ratio = ratio, isSelected = isSelected)

            // Label
            Text(
                text = ratio.shortLabel,
                color = if (isSelected) MaterialTheme.colorScheme.primary else TextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun AspectRatioIcon(
    ratio: AspectRatio,
    isSelected: Boolean
) {
    val color = if (isSelected) MaterialTheme.colorScheme.primary else Gray500
    val maxSize = 36.dp
    val (iconW, iconH) = when (ratio) {
        AspectRatio.RATIO_16_9 -> maxSize to (maxSize * (9f / 16f))
        AspectRatio.RATIO_9_16 -> (maxSize * (9f / 16f)) to maxSize
        AspectRatio.RATIO_4_5 -> (maxSize * (4f / 5f)) to maxSize
        AspectRatio.RATIO_1_1 -> maxSize to maxSize
    }

    Box(
        modifier = Modifier
            .size(width = iconW, height = iconH)
            .border(1.5.dp, color, RoundedCornerShape(4.dp))
    )
}

// ============================================
// ASPECT RATIO EXTENSION
// ============================================

private val AspectRatio.shortLabel: String
    get() = when (this) {
        AspectRatio.RATIO_16_9 -> "16:9"
        AspectRatio.RATIO_9_16 -> "9:16"
        AspectRatio.RATIO_4_5 -> "4:5"
        AspectRatio.RATIO_1_1 -> "1:1"
    }
