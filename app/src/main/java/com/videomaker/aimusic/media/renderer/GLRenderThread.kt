package com.videomaker.aimusic.media.renderer

import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLSurfaceView
import android.util.Log
import android.view.Surface

/**
 * GLRenderThread — EGL14 context + render loop that replaces GLSurfaceView's built-in GL thread.
 *
 * Used with AndroidEmbeddedExternalSurface (TextureView-based) which provides a raw Surface
 * but no GL context. This thread sets up EGL14, creates a GLES2 context, and drives the
 * VideoRenderer's standard GLSurfaceView.Renderer callbacks.
 *
 * Two render modes (matching GLSurfaceView):
 * - RENDERMODE_CONTINUOUSLY: render every frame, vsync-limited by eglSwapBuffers
 * - RENDERMODE_WHEN_DIRTY: wait() on lock, render only on requestRender()
 *
 * Thread safety: synchronized(lock) + wait()/notifyAll() for pause, render-on-demand, size changes.
 * All GL calls on this thread only.
 */
class GLRenderThread(
    private val surface: Surface,
    private val renderer: VideoRenderer,
    initialWidth: Int,
    initialHeight: Int
) : Thread("GLRenderThread") {

    companion object {
        private const val TAG = "GLRenderThread"
    }

    // Use java.lang.Object for wait()/notifyAll() support
    @Suppress("Platform_class_mapped_to_kotlin")
    private val lock = java.lang.Object()

    @Volatile
    var renderMode: Int = GLSurfaceView.RENDERMODE_WHEN_DIRTY
        set(value) {
            field = value
            synchronized(lock) { lock.notifyAll() }
        }

    @Volatile
    private var running = true

    @Volatile
    private var paused = false

    private var renderRequested = false

    // Pending size change (queued from main thread, applied on GL thread)
    private var pendingWidth: Int = initialWidth
    private var pendingHeight: Int = initialHeight
    private var sizeChanged = true // Force initial onSurfaceChanged

    // EGL handles
    private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
    private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE

    fun requestRender() {
        synchronized(lock) {
            renderRequested = true
            lock.notifyAll()
        }
    }

    fun onSurfaceSizeChanged(width: Int, height: Int) {
        synchronized(lock) {
            pendingWidth = width
            pendingHeight = height
            sizeChanged = true
            lock.notifyAll()
        }
    }

    fun onPause() {
        synchronized(lock) {
            paused = true
            lock.notifyAll()
        }
    }

    fun onResume() {
        synchronized(lock) {
            paused = false
            lock.notifyAll()
        }
    }

    /**
     * Stop the render loop, release GL resources and EGL context.
     * Blocks the caller until the thread exits (up to 3 seconds).
     * Falls back to interrupt if the thread doesn't exit in time.
     */
    fun stopAndRelease() {
        synchronized(lock) {
            running = false
            lock.notifyAll()
        }
        try {
            join(3000)
            if (isAlive) {
                Log.w(TAG, "GL thread did not stop in 3s, interrupting")
                interrupt()
                join(1000)
            }
        } catch (_: InterruptedException) {
            // Best-effort wait
        }
    }

    override fun run() {
        try {
            initEGL()
            renderer.onSurfaceCreated(null, null)

            while (running) {
                // Snapshot frame parameters under lock
                val frameParams = awaitFrame() ?: break

                if (frameParams.sizeChanged) {
                    renderer.onSurfaceChanged(null, frameParams.width, frameParams.height)
                }

                renderer.onDrawFrame(null)

                if (!EGL14.eglSwapBuffers(eglDisplay, eglSurface)) {
                    val error = EGL14.eglGetError()
                    if (error == EGL14.EGL_BAD_SURFACE || error == EGL14.EGL_BAD_NATIVE_WINDOW) {
                        Log.w(TAG, "Surface lost (EGL error $error), stopping render thread")
                        synchronized(lock) { running = false }
                        break
                    }
                    Log.w(TAG, "eglSwapBuffers failed: $error")
                }
            }
        } catch (e: InterruptedException) {
            Log.d(TAG, "Render thread interrupted")
        } catch (e: Exception) {
            Log.e(TAG, "Render thread error", e)
        } finally {
            try {
                renderer.release()
            } catch (e: Exception) {
                Log.e(TAG, "Error releasing renderer", e)
            }
            releaseEGL()
        }
    }

    private class FrameParams(val width: Int, val height: Int, val sizeChanged: Boolean)

    /**
     * Wait until a frame should be rendered, then return the current parameters.
     * Returns null if the thread should stop.
     */
    private fun awaitFrame(): FrameParams? {
        synchronized(lock) {
            // Wait while paused
            while (running && paused) {
                lock.wait()
            }
            if (!running) return null

            // In WHEN_DIRTY mode, wait for a render request or size change
            while (running && !paused &&
                renderMode == GLSurfaceView.RENDERMODE_WHEN_DIRTY &&
                !renderRequested && !sizeChanged
            ) {
                lock.wait()
            }
            if (!running) return null

            renderRequested = false
            val params = FrameParams(pendingWidth, pendingHeight, sizeChanged)
            sizeChanged = false
            return params
        }
    }

    private fun initEGL() {
        eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        check(eglDisplay != EGL14.EGL_NO_DISPLAY) { "eglGetDisplay failed" }

        val version = IntArray(2)
        check(EGL14.eglInitialize(eglDisplay, version, 0, version, 1)) { "eglInitialize failed" }

        // 8,8,8,0 RGB — TextureView's SurfaceTexture requires 8-bit channels
        val configAttribs = intArrayOf(
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 0,
            EGL14.EGL_DEPTH_SIZE, 0,
            EGL14.EGL_STENCIL_SIZE, 0,
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_NONE
        )

        val configs = arrayOfNulls<EGLConfig>(1)
        val numConfigs = IntArray(1)
        check(
            EGL14.eglChooseConfig(eglDisplay, configAttribs, 0, configs, 0, 1, numConfigs, 0)
                    && numConfigs[0] > 0
        ) { "eglChooseConfig failed" }

        val config = configs[0]
            ?: throw IllegalStateException("eglChooseConfig returned null config")

        val contextAttribs = intArrayOf(
            EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
            EGL14.EGL_NONE
        )
        eglContext = EGL14.eglCreateContext(
            eglDisplay, config, EGL14.EGL_NO_CONTEXT, contextAttribs, 0
        )
        check(eglContext != EGL14.EGL_NO_CONTEXT) { "eglCreateContext failed" }

        val surfaceAttribs = intArrayOf(EGL14.EGL_NONE)
        eglSurface = EGL14.eglCreateWindowSurface(
            eglDisplay, config, surface, surfaceAttribs, 0
        )
        check(eglSurface != EGL14.EGL_NO_SURFACE) { "eglCreateWindowSurface failed" }

        check(EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
            "eglMakeCurrent failed"
        }

        Log.d(TAG, "EGL initialized: display=$eglDisplay, context=$eglContext")
    }

    private fun releaseEGL() {
        if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
            EGL14.eglMakeCurrent(
                eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT
            )
            if (eglSurface != EGL14.EGL_NO_SURFACE) {
                EGL14.eglDestroySurface(eglDisplay, eglSurface)
                eglSurface = EGL14.EGL_NO_SURFACE
            }
            if (eglContext != EGL14.EGL_NO_CONTEXT) {
                EGL14.eglDestroyContext(eglDisplay, eglContext)
                eglContext = EGL14.EGL_NO_CONTEXT
            }
            EGL14.eglTerminate(eglDisplay)
            eglDisplay = EGL14.EGL_NO_DISPLAY
        }
        Log.d(TAG, "EGL released")
    }
}
