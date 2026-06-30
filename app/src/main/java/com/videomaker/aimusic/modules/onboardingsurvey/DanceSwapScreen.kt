package com.videomaker.aimusic.modules.onboardingsurvey

import android.net.Uri
import android.view.LayoutInflater
import androidx.annotation.OptIn
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseInOutCubic
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.compose.ui.res.stringResource
import com.videomaker.aimusic.R
import com.videomaker.aimusic.core.analytics.Analytics
import com.videomaker.aimusic.ui.theme.OnboardingSurveyBackground
import com.videomaker.aimusic.ui.theme.SliderChevronColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

@OptIn(UnstableApi::class)
@Composable
fun DanceSwapScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Initialize ExoPlayer
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            val videoUri =
                Uri.parse("android.resource://${context.packageName}/${R.raw.video_dance_swap_after}")
            setMediaItem(MediaItem.fromUri(videoUri))
            repeatMode = Player.REPEAT_MODE_ALL
            volume = 0f // Muted
            prepare()
            playWhenReady = true
        }
    }

    // Manage ExoPlayer Lifecycle (Pause on ON_PAUSE, Resume on ON_RESUME, Release on Dispose)
    DisposableEffect(lifecycleOwner, exoPlayer) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    exoPlayer.pause()
                }

                Lifecycle.Event.ON_RESUME -> {
                    exoPlayer.play()
                }

                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            exoPlayer.release()
        }
    }

    var isUserInteracting by remember { mutableStateOf(false) }
    val anim = remember { Animatable(0.5f) }
    val scope = rememberCoroutineScope()
    var widthPx by remember { mutableStateOf(1f) }
    var titleTopPx by remember { mutableStateOf(0f) }

    LaunchedEffect(Unit) {
        Analytics.track(OnboardingSurveyAnalytics.EVENT_AI_DANCE_RENDER)
    }

    // Auto-swipe guide animation (until user interacts)
    LaunchedEffect(isUserInteracting) {
        if (!isUserInteracting) {
            delay(500.milliseconds)
            while (true) {
                anim.animateTo(0.2f, animationSpec = tween(1500, easing = EaseInOutCubic))
                delay(200.milliseconds)
                anim.animateTo(0.8f, animationSpec = tween(1500, easing = EaseInOutCubic))
                delay(200.milliseconds)
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(OnboardingSurveyBackground)
            .padding(start = 16.dp, end = 16.dp, top = 16.dp)
    ) {
        // Rounded card container holding the interactive split media slider
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(24.dp))
                .background(Color.Black)
                .onSizeChanged { widthPx = it.width.toFloat() }
                .pointerInput(widthPx) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val startX = down.position.x
                        scope.launch {
                            anim.snapTo((startX / widthPx).coerceIn(0f, 1f))
                        }
                        isUserInteracting = true

                        while (true) {
                            val event = awaitPointerEvent()
                            val anyPressed = event.changes.any { it.pressed }
                            if (!anyPressed) break
                            val pos = event.changes.first().position
                            scope.launch {
                                anim.snapTo((pos.x / widthPx).coerceIn(0f, 1f))
                            }
                            event.changes.forEach { it.consume() }
                        }
                    }
                }
        ) {
            val sliderPosition = anim.value

            // 1. Before Image (Background)
            Image(
                painter = painterResource(R.drawable.ic_dance_swap_before),
                contentDescription = stringResource(R.string.survey_before),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // 2. After Video (Overlay, clipped dynamically from sliderPosition to 1f)
            AndroidView(
                factory = { ctx ->
                    val view = LayoutInflater.from(ctx)
                        .inflate(R.layout.player_view_texture, null) as PlayerView
                    view.player = exoPlayer
                    view
                },
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RightClipShape(sliderPosition))
            )

            val density = LocalDensity.current
            val lineLengthDp = remember(titleTopPx) {
                with(density) { titleTopPx.toDp() }
            }

            // 3. Vertical divider line (White color) - stops at the top of AI Dance text
            if (lineLengthDp > 0.dp) {
                Box(
                    modifier = Modifier
                        .height(lineLengthDp)
                        .width(2.dp)
                        .align(Alignment.TopStart)
                        .offset { IntOffset((sliderPosition * widthPx).roundToInt(), 0) }
                        .background(Color.White)
                )
            }

            // 4. Center Handle button on the divider line (White background, shadow, and vector chevrons)
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .align(Alignment.CenterStart)
                    .offset {
                        IntOffset(
                            (sliderPosition * widthPx - 24.dp.toPx()).roundToInt(),
                            0
                        )
                    }
                    .shadow(elevation = 6.dp, shape = CircleShape)
                    .clip(CircleShape)
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.size(16.dp)) {
                    val path = Path().apply {
                        // Left chevron: <
                        moveTo(size.width * 0.35f, size.height * 0.25f)
                        lineTo(size.width * 0.15f, size.height * 0.5f)
                        lineTo(size.width * 0.35f, size.height * 0.75f)

                        // Right chevron: >
                        moveTo(size.width * 0.65f, size.height * 0.25f)
                        lineTo(size.width * 0.85f, size.height * 0.5f)
                        lineTo(size.width * 0.65f, size.height * 0.75f)
                    }
                    drawPath(
                        path = path,
                        color = SliderChevronColor,
                        style = Stroke(
                            width = 2.5.dp.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                }
            }

            // 5. Swipe hand guide (visible if not interacted yet)
            if (!isUserInteracting) {
                Image(
                    painter = painterResource(R.drawable.ic_hand),
                    contentDescription = stringResource(R.string.survey_swipe_hint),
                    modifier = Modifier
                        .size(64.dp)
                        .align(Alignment.CenterStart)
                        .offset {
                            IntOffset(
                                (sliderPosition * widthPx - 32.dp.toPx()).roundToInt(),
                                60.dp.toPx()
                                    .roundToInt() // Positioned slightly below the center handle
                            )
                        }
                )
            }

            // 6. Black gradient overlay at the bottom for readability of title
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f))
                        )
                    )
            )

            // 7. Title in stacked arrangement
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = 80.dp)
                    .onGloballyPositioned { coordinates ->
                        titleTopPx = coordinates.positionInParent().y
                    },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Centered Title "AI Dance Video"
                Text(
                    text = stringResource(R.string.survey_dance_swap_title),
                    color = Color.White,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
