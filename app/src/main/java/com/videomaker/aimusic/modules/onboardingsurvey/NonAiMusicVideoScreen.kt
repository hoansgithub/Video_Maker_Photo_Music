package com.videomaker.aimusic.modules.onboardingsurvey

import android.net.Uri
import android.view.LayoutInflater
import androidx.annotation.OptIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.videomaker.aimusic.R
import com.videomaker.aimusic.core.analytics.Analytics
import com.videomaker.aimusic.core.playback.OnboardingMusicPlayer
import com.videomaker.aimusic.ui.theme.OnboardingSurveyBackground
import com.videomaker.aimusic.ui.theme.VideoMakerTheme
import org.koin.compose.koinInject

@OptIn(UnstableApi::class)
@Composable
fun NonAiMusicVideoScreen(
    modifier: Modifier = Modifier,
    bottomContainerTopPx: Float = 0f
) {
    val isPreview = LocalInspectionMode.current

    LaunchedEffect(Unit) {
        if (!isPreview) {
            Analytics.track(OnboardingSurveyAnalytics.EVENT_NON_AI_MUSIC_VIDEO_RENDER)
        }
    }

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Silence the looping onboarding background song while this screen is visible so the
    // video's own audio is heard, then resume it when leaving. (Skipped in @Preview: no Koin.)
    if (!isPreview) {
        val onboardingMusicPlayer: OnboardingMusicPlayer = koinInject()
        DisposableEffect(onboardingMusicPlayer) {
            onboardingMusicPlayer.pauseForAd()
            onDispose { onboardingMusicPlayer.resumeAfterAd() }
        }
    }

    // Initialize ExoPlayer with the bundled music video (looping, with sound, auto-play)
    val exoPlayer = if (isPreview) null else remember {
        ExoPlayer.Builder(context).build().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                /* handleAudioFocus = */ true
            )
            val videoUri =
                Uri.parse("android.resource://${context.packageName}/${R.raw.video_non_ai_music}")
            setMediaItem(MediaItem.fromUri(videoUri))
            repeatMode = Player.REPEAT_MODE_ALL
            prepare()
            playWhenReady = true
        }
    }

    // Manage ExoPlayer lifecycle (pause on ON_PAUSE, resume on ON_RESUME, release on dispose)
    DisposableEffect(lifecycleOwner, exoPlayer) {
        if (exoPlayer == null) return@DisposableEffect onDispose {}
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> exoPlayer.pause()
                Lifecycle.Event.ON_RESUME -> exoPlayer.play()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            exoPlayer.release()
        }
    }

    var totalHeightPx by remember { mutableStateOf(0) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(OnboardingSurveyBackground)
    ) {
        // Rounded card container holding the looping video
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .onSizeChanged { totalHeightPx = it.height }
        ) {
            // 1. Looping video
            if (exoPlayer != null) {
                AndroidView(
                    factory = { ctx ->
                        val view = LayoutInflater.from(ctx)
                            .inflate(R.layout.player_view_texture, null) as PlayerView
                        view.player = exoPlayer
                        view
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                // In Compose preview, render a fallback background placeholder
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.DarkGray)
                )
            }

            // 2. Overlay icon above the bottom container dynamically
            if (totalHeightPx > 0) {
                val density = LocalDensity.current
                val bottomPaddingDp = remember(totalHeightPx, bottomContainerTopPx) {
                    if (bottomContainerTopPx > 0f) {
                        with(density) { (totalHeightPx - bottomContainerTopPx).toDp() }
                    } else {
                        80.dp
                    }
                }

                Image(
                    painter = painterResource(R.drawable.ic_non_ai_video),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = bottomPaddingDp + 16.dp)
                )
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF1E1E1E)
@Composable
private fun NonAiMusicVideoScreenPreview() {
    VideoMakerTheme {
        NonAiMusicVideoScreen()
    }
}