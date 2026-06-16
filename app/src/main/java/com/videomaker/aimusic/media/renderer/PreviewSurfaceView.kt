package com.videomaker.aimusic.media.renderer

import android.opengl.GLSurfaceView
import androidx.compose.foundation.AndroidEmbeddedExternalSurface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * PreviewSurfaceView - Compose wrapper for the GL preview renderer.
 *
 * Uses AndroidEmbeddedExternalSurface (TextureView-based) which renders within
 * the Compose layer hierarchy, eliminating z-ordering issues and black flash
 * on resize that GLSurfaceView's separate hardware surface caused.
 *
 * EGL context and render loop are managed by GLRenderThread.
 *
 * Performance optimizations:
 * - 8,8,8,0 EGL config (TextureView requires 8-bit channels)
 * - RENDERMODE_CONTINUOUSLY during playback for 60fps rendering
 * - RENDERMODE_WHEN_DIRTY when paused to save GPU/battery
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

    var glThread by remember { mutableStateOf<GLRenderThread?>(null) }

    // Update renderer state reactively — @Volatile ensures atomic read on GL thread
    LaunchedEffect(renderState) {
        renderer.renderState = renderState
        glThread?.requestRender() // Ensure state changes are visible even when paused
    }

    LaunchedEffect(playbackClock) {
        renderer.playbackClock = playbackClock
    }

    // Switch render mode: CONTINUOUSLY for 60fps during playback, WHEN_DIRTY when paused
    LaunchedEffect(isPlaying) {
        val thread = glThread ?: return@LaunchedEffect
        if (isPlaying) {
            thread.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
        } else {
            thread.renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
            thread.requestRender() // Render one final frame at paused position
        }
    }

    // Lifecycle management — pause/resume render thread with app lifecycle
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> glThread?.onResume()
                Lifecycle.Event.ON_PAUSE -> glThread?.onPause()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    AndroidEmbeddedExternalSurface(
        modifier = modifier,
        isOpaque = true
    ) {
        onSurface { surface, width, height ->
            val thread = GLRenderThread(surface, renderer, width, height).apply {
                renderMode = if (isPlaying) {
                    GLSurfaceView.RENDERMODE_CONTINUOUSLY
                } else {
                    GLSurfaceView.RENDERMODE_WHEN_DIRTY
                }
                start()
            }
            glThread = thread

            surface.onChanged { newWidth, newHeight ->
                thread.onSurfaceSizeChanged(newWidth, newHeight)
            }

            surface.onDestroyed {
                thread.stopAndRelease()
                glThread = null
            }
        }
    }
}
