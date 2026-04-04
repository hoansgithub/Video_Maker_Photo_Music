package com.videomaker.aimusic.modules.editor.components

import androidx.compose.foundation.background
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
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.videomaker.aimusic.R
import com.videomaker.aimusic.domain.model.ProjectSettings
import com.videomaker.aimusic.ui.theme.Gray500
import com.videomaker.aimusic.ui.theme.SplashBackground
import com.videomaker.aimusic.ui.theme.TextPrimary
import com.videomaker.aimusic.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DurationBottomSheet(
    currentDurationMs: Long,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit
) {
    // Duration range: 1.0s to 10.0s with 0.1s steps
    val minSeconds = ProjectSettings.MIN_IMAGE_DURATION_SECONDS
    val maxSeconds = ProjectSettings.MAX_IMAGE_DURATION_SECONDS
    val step = ProjectSettings.IMAGE_DURATION_STEP

    // Convert current duration to seconds (with 1 decimal precision)
    val currentSeconds = (currentDurationMs / 100f).let { (it / 10f) }
        .coerceIn(minSeconds, maxSeconds)

    var selectedSeconds by remember { mutableFloatStateOf(currentSeconds) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

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
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Header with title and apply button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.editor_image_duration),
                    color = TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )

                // Apply button
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                        .clickable {
                            // Convert seconds to milliseconds
                            val selectedDuration = (selectedSeconds * 1000).toLong()
                            onConfirm(selectedDuration)
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

            // Selected duration display (centered)
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = String.format("%.1fs", selectedSeconds),
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Slider with smooth continuous control, rounded to 0.1s precision
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
            ) {
                Slider(
                    value = selectedSeconds,
                    onValueChange = { value ->
                        // Round to nearest 0.1s step for clean values
                        selectedSeconds = (kotlin.math.round(value / step) * step)
                            .coerceIn(minSeconds, maxSeconds)
                    },
                    valueRange = minSeconds..maxSeconds,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = Gray500
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // Min/Max labels (1.0s - 10.0s)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = String.format("%.1fs", minSeconds),
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                    Text(
                        text = String.format("%.1fs", maxSeconds),
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}
