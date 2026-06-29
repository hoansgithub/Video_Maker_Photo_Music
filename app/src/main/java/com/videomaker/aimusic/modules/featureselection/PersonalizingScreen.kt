package com.videomaker.aimusic.modules.featureselection

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import com.videomaker.aimusic.R
import com.videomaker.aimusic.ui.theme.Primary
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun PersonalizingScreen(
    onLoadingComplete: () -> Unit = {}
) {
    var currentStep by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        // Step-by-step progress simulation
        delay(1500.milliseconds)
        currentStep = 1
        delay(1500.milliseconds)
        currentStep = 2
        delay(1500.milliseconds)
        currentStep = 3
        delay(1500.milliseconds)
        currentStep = 4
        delay(1500.milliseconds)
        currentStep = 5 // All steps completed
        onLoadingComplete()
    }

    val steps = listOf(
        R.string.personalizing_step_style,
        R.string.personalizing_step_templates,
        R.string.personalizing_step_songs,
        R.string.personalizing_step_effects,
        R.string.personalizing_step_ready
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF161616))
    ) {
        // Glowing background blobs (Top-Left & Top-Right)
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFCCFF00).copy(alpha = 0.15f), Color.Transparent),
                    center = Offset(0f, 0f),
                    radius = size.width * 0.8f
                ),
                radius = size.width * 0.8f,
                center = Offset(0f, 0f)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF00FFFF).copy(alpha = 0.1f), Color.Transparent),
                    center = Offset(size.width, 0f),
                    radius = size.width * 0.8f
                ),
                radius = size.width * 0.8f,
                center = Offset(size.width, 0f)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Icon Capsule with sparkles
            Box(
                modifier = Modifier.padding(bottom = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                // Glow behind the capsule
                Box(
                    modifier = Modifier
                        .size(width = 84.dp, height = 56.dp)
                        .background(Primary.copy(alpha = 0.15f), RoundedCornerShape(28.dp))
                )

                // Capsule box
                Box(
                    modifier = Modifier
                        .size(width = 84.dp, height = 56.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .background(Color(0xFF222415))
                        .border(
                            width = 1.5.dp,
                            color = Primary.copy(alpha = 0.8f),
                            shape = RoundedCornerShape(28.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_personalize),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        colorFilter = ColorFilter.tint(Color.White)
                    )
                }

                // Sparkle at Top-Left
                Image(
                    painter = painterResource(id = R.drawable.ic_sparkle_filled),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(Color.White),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset(x = (-8).dp, y = (-4).dp)
                        .size(12.dp)
                )

                // Sparkle at Bottom-Right
                Image(
                    painter = painterResource(id = R.drawable.ic_sparkle_filled),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(Color.White),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = 8.dp, y = 4.dp)
                        .size(12.dp)
                )
            }

            // Title
            val titleText = buildAnnotatedString {
                append("We’re ")
                withStyle(style = SpanStyle(color = Primary)) {
                    append("personalizing")
                }
                append("\nyour ")
                withStyle(style = SpanStyle(color = Primary)) {
                    append("experience...")
                }
            }

            Text(
                text = titleText,
                fontSize = 28.sp,
                lineHeight = 36.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Checklist Steps Column
            Column(
                modifier = Modifier.fillMaxWidth(0.9f),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                steps.forEachIndexed { i, stepResId ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top
                    ) {
                        // Left status circle + connector line
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.width(36.dp)
                        ) {
                            Box(
                                modifier = Modifier.height(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (i < currentStep) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_circle_checkmark),
                                        contentDescription = null,
                                        tint = Primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                } else if (i == currentStep) {
                                    CircularProgressIndicator(
                                        color = Color.White,
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(18.dp)
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(18.dp)
                                            .border(
                                                width = 1.5.dp,
                                                color = Color.White.copy(alpha = 0.25f),
                                                shape = CircleShape
                                            )
                                    )
                                }
                            }

                            if (i < steps.size - 1) {
                                if (i < currentStep) {
                                    Spacer(
                                        modifier = Modifier
                                            .width(2.dp)
                                            .height(24.dp)
                                            .background(Primary)
                                    )
                                } else {
                                    DashedLine(
                                        color = Color.White.copy(alpha = 0.2f),
                                        modifier = Modifier
                                            .width(2.dp)
                                            .height(24.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        // Right step text description
                        Box(
                            modifier = Modifier.height(24.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Text(
                                text = stringResource(id = stepResId),
                                fontSize = 16.sp,
                                fontWeight = if (i <= currentStep) FontWeight.Bold else FontWeight.Medium,
                                color = if (i <= currentStep) Color.White else Color.White.copy(
                                    alpha = 0.25f
                                ),
                                textAlign = TextAlign.Start
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DashedLine(
    color: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        drawLine(
            color = color,
            start = Offset(size.width / 2, 0f),
            end = Offset(size.width / 2, size.height),
            strokeWidth = 2.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
        )
    }
}

@Preview(showSystemUi = true)
@Composable
fun PersonalizingScreenPreview() {
    PersonalizingScreen()
}
