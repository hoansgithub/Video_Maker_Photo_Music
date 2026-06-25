package com.videomaker.aimusic.media.renderer

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.util.Log
import com.videomaker.aimusic.domain.model.BeatSyncData
import com.videomaker.aimusic.domain.model.Transition
import com.videomaker.aimusic.media.composition.BeatSyncClip
import com.videomaker.aimusic.media.composition.BeatSyncTimingCalculator
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.cos
import kotlin.math.PI

/**
 * VideoRenderer - GLSurfaceView.Renderer that renders transitions at 60fps.
 *
 * Reads RenderState atomically each frame. Property changes (effect set,
 * aspect ratio, images) take effect on the next frame — no composition rebuild.
 *
 * Uses BeatSyncTimingCalculator to determine which image and transition
 * to render at a given timestamp, identical to the export path.
 *
 * Performance optimizations for preview:
 * - Zero per-frame allocations (mutable FrameInfo fields)
 * - Clip cache with field-level comparison (no string allocation)
 * - Textures at 1x viewport + RGB_565 (via TextureManager)
 */
class VideoRenderer(private val context: Context) : GLSurfaceView.Renderer {

    companion object {
        private const val TAG = "VideoRenderer"

        // Fullscreen quad vertices (position + texcoord)
        private val QUAD_VERTICES = floatArrayOf(
            // x, y, u, v
            -1f, -1f, 0f, 1f,  // bottom-left (v flipped for Android)
             1f, -1f, 1f, 1f,  // bottom-right
            -1f,  1f, 0f, 0f,  // top-left
             1f,  1f, 1f, 0f   // top-right
        )
    }

    // Current render state — set atomically from the main thread
    @Volatile var renderState: RenderState = RenderState.EMPTY

    // Clock for time-based rendering
    @Volatile var playbackClock: PlaybackClock? = null

    // Fires once on the GL thread after the first frame with actual content is rendered.
    // Cleared after invocation to avoid leaks.
    @Volatile var onFirstFrameRendered: (() -> Unit)? = null
    private var hasNotifiedFirstFrame = false

    // GL resources (created on GL thread)
    private lateinit var textureManager: TextureManager
    private lateinit var shaderCache: ShaderProgramCache
    private lateinit var quadBuffer: FloatBuffer

    // Beat-sync timing calculator (reused)
    private val timingCalculator = BeatSyncTimingCalculator()

    // Viewport dimensions
    private var viewportWidth = 0
    private var viewportHeight = 0

    // Pre-computed clips cache — individual fields compared to avoid per-frame string allocation
    private var cachedClips: List<BeatSyncClip>? = null
    private var cachedClipsBpm: Double = 0.0
    private var cachedClipsBeatsSize: Int = 0
    private var cachedClipsImageCount: Int = 0
    private var cachedClipsNumShaders: Int = 0
    private var cachedClipsHookStartMs: Long = 0L

    // Mutable frame info — reused every frame to avoid allocation at 60fps.
    // Written by calculateFrameAt(), read by onDrawFrame().
    private var frameFromIndex: Int = 0
    private var frameToIndex: Int = -1
    private var frameProgress: Float = 0f
    private var frameShaderIndex: Int = 0
    private var frameIsTransition: Boolean = false
    private var frameValid: Boolean = false

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)

        textureManager = TextureManager(context)
        shaderCache = ShaderProgramCache()
        shaderCache.init()

        // Create quad vertex buffer
        quadBuffer = ByteBuffer.allocateDirect(QUAD_VERTICES.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(QUAD_VERTICES)
                position(0)
            }

        Log.d(TAG, "Surface created, GL resources initialized")
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        viewportWidth = width
        viewportHeight = height
        GLES20.glViewport(0, 0, width, height)
        textureManager.setViewportSize(width, height)
        Log.d(TAG, "Surface changed: ${width}x$height")
    }

    override fun onDrawFrame(gl: GL10?) {
        val state = renderState  // Atomic snapshot
        val clock = playbackClock

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        if (state.imageUris.isEmpty()) return

        // Ensure textures are loaded
        textureManager.ensureTextures(state.imageUris)

        // Notify first frame rendered — textures are loaded and content is about to draw.
        // Fired after ensureTextures so the preparing overlay stays until real content is visible.
        if (!hasNotifiedFirstFrame) {
            hasNotifiedFirstFrame = true
            onFirstFrameRendered?.invoke()
            onFirstFrameRendered = null
        }

        // Get current time from PlaybackClock
        val timeMs = clock?.currentTimeMs() ?: 0L

        // Calculate which frame to render (writes mutable fields, zero allocation)
        calculateFrameAt(timeMs, state)
        if (!frameValid) {
            // Just show first image
            drawHoldFrame(textureManager.getTexture(0), textureManager.getAspectRatio(0), state.aspectRatio.ratio)
            return
        }

        val targetAspect = state.aspectRatio.ratio

        if (frameIsTransition && frameToIndex >= 0) {
            // Render transition between two images
            val fromTex = textureManager.getTexture(frameFromIndex)
            val toTex = textureManager.getTexture(frameToIndex)
            val fromAspect = textureManager.getAspectRatio(frameFromIndex)
            val toAspect = textureManager.getAspectRatio(frameToIndex)
            val transition = state.transitions.getOrNull(frameShaderIndex)

            if (transition != null && fromTex != 0 && toTex != 0) {
                drawTransition(fromTex, toTex, transition, frameProgress, targetAspect, fromAspect, toAspect)
            } else {
                // Fallback: show from image
                drawHoldFrame(fromTex, fromAspect, targetAspect)
            }
        } else {
            // Hold frame — show single image
            val tex = textureManager.getTexture(frameFromIndex)
            val inputAspect = textureManager.getAspectRatio(frameFromIndex)
            drawHoldFrame(tex, inputAspect, targetAspect)
        }
    }

    /**
     * Release GL resources. Must be called on GL thread.
     */
    fun release() {
        if (::textureManager.isInitialized) textureManager.releaseTextures()
        if (::shaderCache.isInitialized) shaderCache.release()
    }

    /**
     * Handle GL context loss.
     */
    fun onContextLost() {
        if (::textureManager.isInitialized) textureManager.onContextLost()
        if (::shaderCache.isInitialized) shaderCache.onContextLost()
        cachedClips = null
    }

    // ============================================
    // FRAME CALCULATION (zero-allocation)
    // ============================================

    /**
     * Calculate which image/transition to render at [timeMs].
     * Writes results to mutable fields (frameFromIndex, frameToIndex, etc.)
     * instead of allocating a data class — called 60 times per second.
     */
    private fun calculateFrameAt(timeMs: Long, state: RenderState) {
        val beatSyncData = state.beatSyncData
        val imageCount = state.imageUris.size

        if (beatSyncData == null || imageCount == 0) {
            frameValid = false
            return
        }

        // Get or compute clips (uses hookStartTimeMs to match export path)
        val clips = getClips(beatSyncData, imageCount, state.transitions.size, state.hookStartTimeMs)
        if (clips.isEmpty()) {
            frameValid = false
            return
        }

        // Walk through clips to find which one contains timeMs
        var accumulatedMs = 0L

        for (clip in clips) {
            val clipEndMs = accumulatedMs + clip.totalDurationMs

            if (timeMs < clipEndMs) {
                val timeInClip = timeMs - accumulatedMs

                if (clip.hasTransition && clip.holdDurationMs > 0 && timeInClip >= clip.holdDurationMs) {
                    // In transition phase
                    val transitionTimeMs = timeInClip - clip.holdDurationMs
                    val linearProgress = (transitionTimeMs.toFloat() / clip.transitionDurationMs.toFloat())
                        .coerceIn(0f, 1f)
                    // Cosine ease-in-out to match export path (TransitionEffect)
                    frameFromIndex = clip.imageIndex
                    frameToIndex = (clip.imageIndex + 1).coerceAtMost(imageCount - 1)
                    frameProgress = 0.5f - 0.5f * cos(linearProgress * PI.toFloat())
                    frameShaderIndex = clip.transitionShaderIndex
                    frameIsTransition = true
                    frameValid = true
                    return
                } else if (clip.hasTransition && clip.holdDurationMs == 0L) {
                    // Transition only (no hold)
                    val linearProgress = (timeInClip.toFloat() / clip.transitionDurationMs.toFloat())
                        .coerceIn(0f, 1f)
                    frameFromIndex = clip.imageIndex
                    frameToIndex = (clip.imageIndex + 1).coerceAtMost(imageCount - 1)
                    frameProgress = 0.5f - 0.5f * cos(linearProgress * PI.toFloat())
                    frameShaderIndex = clip.transitionShaderIndex
                    frameIsTransition = true
                    frameValid = true
                    return
                } else {
                    // Hold phase
                    frameFromIndex = clip.imageIndex
                    frameToIndex = -1
                    frameProgress = 0f
                    frameShaderIndex = 0
                    frameIsTransition = false
                    frameValid = true
                    return
                }
            }

            accumulatedMs = clipEndMs
        }

        // Past all clips — show last image
        frameFromIndex = (imageCount - 1).coerceAtLeast(0)
        frameToIndex = -1
        frameProgress = 0f
        frameShaderIndex = 0
        frameIsTransition = false
        frameValid = true
    }

    private fun getClips(
        beatSyncData: BeatSyncData,
        imageCount: Int,
        numShaders: Int,
        hookStartTimeMs: Long
    ): List<BeatSyncClip> {
        // Compare individual fields — no string allocation per frame
        cachedClips?.let { cached ->
            if (beatSyncData.bpm == cachedClipsBpm &&
                beatSyncData.beats.size == cachedClipsBeatsSize &&
                imageCount == cachedClipsImageCount &&
                numShaders == cachedClipsNumShaders &&
                hookStartTimeMs == cachedClipsHookStartMs
            ) {
                return cached
            }
        }

        val imageSequence = (0 until imageCount).toList()
        val clips = timingCalculator.calculateClips(
            beatData = beatSyncData,
            imageSequence = imageSequence,
            trimStartMs = hookStartTimeMs,
            trimEndMs = null,
            numShaders = numShaders.coerceAtLeast(1)
        )

        cachedClips = clips
        cachedClipsBpm = beatSyncData.bpm
        cachedClipsBeatsSize = beatSyncData.beats.size
        cachedClipsImageCount = imageCount
        cachedClipsNumShaders = numShaders
        cachedClipsHookStartMs = hookStartTimeMs
        return clips
    }

    // ============================================
    // DRAWING
    // ============================================

    private fun drawHoldFrame(textureId: Int, inputAspect: Float, targetAspect: Float) {
        if (textureId == 0) return

        val program = shaderCache.getPassthroughProgram()
        if (program == 0) return

        GLES20.glUseProgram(program)
        val locs = shaderCache.getLocations(program)

        // Bind texture
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        GLES20.glUniform1i(locs.uTexFrom, 0)

        // Set aspect ratio uniforms for blur-background rendering
        GLES20.glUniform1f(locs.uInputAspect, inputAspect)
        GLES20.glUniform1f(locs.uTargetAspect, targetAspect)

        drawQuad(locs)
    }

    private fun drawTransition(
        fromTex: Int,
        toTex: Int,
        transition: Transition,
        progress: Float,
        targetAspect: Float,
        fromInputAspect: Float,
        toInputAspect: Float
    ) {
        val program = shaderCache.getProgram(transition)
        if (program == 0) return

        GLES20.glUseProgram(program)
        val locs = shaderCache.getLocations(program)

        // Bind from texture to unit 0
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, fromTex)
        GLES20.glUniform1i(locs.uTexFrom, 0)

        // Bind to texture to unit 1
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, toTex)
        GLES20.glUniform1i(locs.uTexTo, 1)

        // Set transition uniforms
        GLES20.glUniform1f(locs.progress, progress)
        GLES20.glUniform1f(locs.ratio, targetAspect)

        // Set per-image aspect ratio uniforms for blur-background rendering
        GLES20.glUniform1f(locs.uInputAspectFrom, fromInputAspect)
        GLES20.glUniform1f(locs.uInputAspectTo, toInputAspect)
        GLES20.glUniform1f(locs.uTargetAspect, targetAspect)

        drawQuad(locs)
    }

    private fun drawQuad(locs: ProgramLocations) {
        // Position attribute (x, y) — stride 16 bytes (4 floats * 4 bytes), offset 0
        GLES20.glEnableVertexAttribArray(locs.aPosition)
        quadBuffer.position(0)
        GLES20.glVertexAttribPointer(locs.aPosition, 2, GLES20.GL_FLOAT, false, 16, quadBuffer)

        // TexCoord attribute (u, v) — stride 16 bytes, offset 8 bytes (2 floats)
        GLES20.glEnableVertexAttribArray(locs.aTexCoord)
        quadBuffer.position(2)
        GLES20.glVertexAttribPointer(locs.aTexCoord, 2, GLES20.GL_FLOAT, false, 16, quadBuffer)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        GLES20.glDisableVertexAttribArray(locs.aPosition)
        GLES20.glDisableVertexAttribArray(locs.aTexCoord)
    }
}
