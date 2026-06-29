package com.videomaker.aimusic.modules.language

import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseInOutCubic
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.videomaker.aimusic.R
import com.videomaker.aimusic.ui.theme.Primary
import com.videomaker.aimusic.ui.theme.PickerDialogBackground
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

enum class DialogState {
    Loading,
    Success
}

@Composable
fun LanguageSetupDialog(
    onDismiss: () -> Unit
) {
    var dialogState by remember { mutableStateOf(DialogState.Loading) }

    LaunchedEffect(Unit) {
        delay(2000.milliseconds)
        dialogState = DialogState.Success
        delay(1000.milliseconds)
        onDismiss()
    }

    Dialog(
        onDismissRequest = {}, // Cannot be dismissed by user
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    ) {
        LanguageSetupDialogContent(dialogState = dialogState)
    }
}

@Composable
fun LanguageSetupDialogContent(
    dialogState: DialogState,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .width(220.dp)
            .wrapContentHeight()
            .clip(RoundedCornerShape(16.dp))
            .background(PickerDialogBackground)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Icon/Progress area
            Box(
                modifier = Modifier.size(48.dp),
                contentAlignment = Alignment.Center
            ) {
                if (dialogState == DialogState.Loading) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(36.dp)
                      )
                } else {
                    AnimatedCheckmark()
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Text area with crossfade animation
            AnimatedContent(
                targetState = dialogState,
                transitionSpec = {
                    fadeIn(animationSpec = tween(220, delayMillis = 90)) togetherWith
                            fadeOut(animationSpec = tween(90))
                },
                label = "TextTransition"
            ) { state ->
                Text(
                    text = if (state == DialogState.Loading) {
                        stringResource(R.string.language_setting_up)
                    } else {
                        stringResource(R.string.language_setting_up_done)
                    },
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Preview(name = "Loading State", showBackground = true, backgroundColor = 0xFF121212)
@Composable
private fun LanguageSetupDialogLoadingPreview() {
    Box(
        modifier = Modifier.padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        LanguageSetupDialogContent(dialogState = DialogState.Loading)
    }
}

@Preview(name = "Success State", showBackground = true, backgroundColor = 0xFF121212)
@Composable
private fun LanguageSetupDialogSuccessPreview() {
    Box(
        modifier = Modifier.padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        LanguageSetupDialogContent(dialogState = DialogState.Success)
    }
}

@Composable
private fun AnimatedCheckmark() {
    val pathPortion = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        pathPortion.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 400, easing = EaseInOutCubic)
        )
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val start = Offset(size.width * 0.25f, size.height * 0.5f)
        val bend = Offset(size.width * 0.45f, size.height * 0.7f)
        val end = Offset(size.width * 0.75f, size.height * 0.35f)

        val strokeWidth = 5.dp.toPx()
        val portion = pathPortion.value

        if (portion > 0f) {
            val path = Path()
            path.moveTo(start.x, start.y)

            if (portion <= 0.3f) {
                val progress = portion / 0.3f
                val currentX = start.x + (bend.x - start.x) * progress
                val currentY = start.y + (bend.y - start.y) * progress
                path.lineTo(currentX, currentY)
            } else {
                path.lineTo(bend.x, bend.y)
                val progress = (portion - 0.3f) / 0.7f
                val currentX = bend.x + (end.x - bend.x) * progress
                val currentY = bend.y + (end.y - bend.y) * progress
                path.lineTo(currentX, currentY)
            }

            drawPath(
                path = path,
                color = Primary,
                style = Stroke(
                    width = strokeWidth,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        }
    }
}
