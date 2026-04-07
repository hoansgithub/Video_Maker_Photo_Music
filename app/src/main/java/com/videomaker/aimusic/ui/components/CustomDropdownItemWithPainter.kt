package com.videomaker.aimusic.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.videomaker.aimusic.ui.components.ModifierExtension.clickableSingle

@Composable
fun CustomDropdownItemWithPainter(
    painter: Painter,
    title: String,
    onClick: () -> Unit,
    showDivider: Boolean = true
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickableSingle(
                onClick = onClick
            )
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painter,
            contentDescription = title,
            tint = Color.White,
            modifier = Modifier.size(24.dp)
        )

        Spacer(modifier = Modifier.width(10.dp))

        Text(
            text = title,
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold
        )
    }

    if (showDivider) {
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 16.dp),
            thickness = 0.5.dp,
            color = Color.White.copy(alpha = 0.12f)
        )
    }
}