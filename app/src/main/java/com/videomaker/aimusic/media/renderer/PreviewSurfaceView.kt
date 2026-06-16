package com.videomaker.aimusic.media.renderer

import android.opengl.GLSurfaceView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * PreviewSurfaceView - Compose wrapper for the GL preview renderer.
 *
 * Wraps a GLSurfaceView with the VideoRenderer for real-time video preview.
 * Handles lifecycle (pause/resume GL thread) and state updates.
 *
 * @param renderState Current render state — updates take effect on next frame
 * @param playbackClock Shared time source for playback
 * @param modifier Compose modifier
 */
@Composable
fun PreviewSurfaceView(
    renderState: RenderState,
    playbackClock: PlaybackClock,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val renderer = remember { VideoRenderer(context) }

    // Update renderer state reactively — @Volatile ensures atomic read on GL thread
    LaunchedEffect(renderState) {
        renderer.renderState = renderState
    }

    LaunchedEffect(playbackClock) {
        renderer.playbackClock = playbackClock
    }

    // GLSurfaceView instance (created once, reused across recompositions)
    val glSurfaceView = remember {
        GLSurfaceView(context).apply {
            setEGLContextClientVersion(2)
            setRenderer(renderer)
            // RENDERMODE_CONTINUOUSLY for 60fps render loop
            renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
        }
    }

    // Lifecycle management — pause/resume GL thread with app lifecycle
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> glSurfaceView.onResume()
                Lifecycle.Event.ON_PAUSE -> glSurfaceView.onPause()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            // Release GL resources on GL thread
            glSurfaceView.queueEvent { renderer.release() }
        }
    }

    AndroidView(
        factory = { glSurfaceView },
        modifier = modifier
    )
}
