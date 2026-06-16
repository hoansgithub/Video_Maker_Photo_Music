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
 * Uses RENDERMODE_CONTINUOUSLY during playback for 60fps rendering,
 * switches to RENDERMODE_WHEN_DIRTY when paused to save GPU/battery.
 *
 * @param renderState Current render state — updates take effect on next frame
 * @param playbackClock Shared time source for playback
 * @param isPlaying Whether video is currently playing (controls render mode)
 * @param modifier Compose modifier
 */
@Composable
fun PreviewSurfaceView(
    renderState: RenderState,
    playbackClock: PlaybackClock,
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val renderer = remember { VideoRenderer(context) }

    // GLSurfaceView instance (created once, reused across recompositions)
    val glSurfaceView = remember {
        GLSurfaceView(context).apply {
            setEGLContextClientVersion(2)
            setRenderer(renderer)
            // Start with WHEN_DIRTY; LaunchedEffect(isPlaying) switches to CONTINUOUSLY when playing
            renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
        }
    }

    // Update renderer state reactively — @Volatile ensures atomic read on GL thread
    LaunchedEffect(renderState) {
        renderer.renderState = renderState
        glSurfaceView.requestRender() // Ensure state changes are visible even when paused
    }

    LaunchedEffect(playbackClock) {
        renderer.playbackClock = playbackClock
    }

    // Switch render mode: CONTINUOUSLY for 60fps during playback, WHEN_DIRTY when paused
    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            glSurfaceView.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
        } else {
            glSurfaceView.renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
            glSurfaceView.requestRender() // Render one final frame at paused position
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
