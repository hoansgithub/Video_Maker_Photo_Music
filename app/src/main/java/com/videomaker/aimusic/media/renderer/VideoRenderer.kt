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

/**
 * VideoRenderer - GLSurfaceView.Renderer that renders transitions at 60fps.
 *
 * Reads RenderState atomically each frame. Property changes (effect set,
 * aspect ratio, images) take effect on the next frame — no composition rebuild.
 *
 * Uses BeatSyncTimingCalculator to determine which image and transition
 * to render at a given timestamp, identical to the export path.
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

    // GL resources (created on GL thread)
    private lateinit var textureManager: TextureManager
    private lateinit var shaderCache: ShaderProgramCache
    private lateinit var quadBuffer: FloatBuffer

    // Beat-sync timing calculator (reused)
    private val timingCalculator = BeatSyncTimingCalculator()

    // Viewport dimensions
    private var viewportWidth = 0
    private var viewportHeight = 0

    // Pre-computed clips cache (invalidated when state changes)
    private var cachedClips: List<BeatSyncClip>? = null
    private var cachedClipsKey: String = ""

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
        Log.d(TAG, "Surface changed: ${width}x$height")
    }

    override fun onDrawFrame(gl: GL10?) {
        val state = renderState  // Atomic snapshot
        val clock = playbackClock

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        if (state.imageUris.isEmpty()) return

        // Ensure textures are loaded
        textureManager.ensureTextures(state.imageUris)

        // Get current time
        val timeMs = clock?.currentTimeMs() ?: state.currentTimeMs

        // Calculate which frame to render
        val frameInfo = calculateFrameAt(timeMs, state)
        if (frameInfo == null) {
            // Just show first image
            drawHoldFrame(textureManager.getTexture(0))
            return
        }

        if (frameInfo.isTransition && frameInfo.toIndex >= 0) {
            // Render transition between two images
            val fromTex = textureManager.getTexture(frameInfo.fromIndex)
            val toTex = textureManager.getTexture(frameInfo.toIndex)
            val transition = state.transitions.getOrNull(frameInfo.shaderIndex)

            if (transition != null && fromTex != 0 && toTex != 0) {
                drawTransition(fromTex, toTex, transition, frameInfo.progress, state.aspectRatio.ratio)
            } else {
                // Fallback: show from image
                drawHoldFrame(fromTex)
            }
        } else {
            // Hold frame — show single image
            val tex = textureManager.getTexture(frameInfo.fromIndex)
            drawHoldFrame(tex)
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
    // FRAME CALCULATION
    // ============================================

    private data class FrameInfo(
        val fromIndex: Int,
        val toIndex: Int = -1,
        val progress: Float = 0f,
        val shaderIndex: Int = 0,
        val isTransition: Boolean = false
    )

    private fun calculateFrameAt(timeMs: Long, state: RenderState): FrameInfo? {
        val beatSyncData = state.beatSyncData ?: return null
        val imageCount = state.imageUris.size
        if (imageCount == 0) return null

        // Get or compute clips (uses hookStartTimeMs to match export path)
        val clips = getClips(beatSyncData, imageCount, state.transitions.size, state.hookStartTimeMs)
        if (clips.isEmpty()) return null

        // Walk through clips to find which one contains timeMs
        var accumulatedMs = 0L

        for (clip in clips) {
            val clipEndMs = accumulatedMs + clip.totalDurationMs

            if (timeMs < clipEndMs) {
                val timeInClip = timeMs - accumulatedMs

                if (clip.hasTransition && clip.holdDurationMs > 0 && timeInClip >= clip.holdDurationMs) {
                    // In transition phase
                    val transitionTimeMs = timeInClip - clip.holdDurationMs
                    val progress = (transitionTimeMs.toFloat() / clip.transitionDurationMs.toFloat())
                        .coerceIn(0f, 1f)

                    val fromIndex = clip.imageIndex
                    val toIndex = (clip.imageIndex + 1).coerceAtMost(imageCount - 1)

                    return FrameInfo(
                        fromIndex = fromIndex,
                        toIndex = toIndex,
                        progress = progress,
                        shaderIndex = clip.transitionShaderIndex,
                        isTransition = true
                    )
                } else if (clip.hasTransition && clip.holdDurationMs == 0L) {
                    // Transition only (no hold)
                    val progress = (timeInClip.toFloat() / clip.transitionDurationMs.toFloat())
                        .coerceIn(0f, 1f)

                    val fromIndex = clip.imageIndex
                    val toIndex = (clip.imageIndex + 1).coerceAtMost(imageCount - 1)

                    return FrameInfo(
                        fromIndex = fromIndex,
                        toIndex = toIndex,
                        progress = progress,
                        shaderIndex = clip.transitionShaderIndex,
                        isTransition = true
                    )
                } else {
                    // Hold phase
                    return FrameInfo(fromIndex = clip.imageIndex)
                }
            }

            accumulatedMs = clipEndMs
        }

        // Past all clips — show last image
        return FrameInfo(fromIndex = (imageCount - 1).coerceAtLeast(0))
    }

    private fun getClips(
        beatSyncData: BeatSyncData,
        imageCount: Int,
        numShaders: Int,
        hookStartTimeMs: Long
    ): List<BeatSyncClip> {
        val key = "${beatSyncData.bpm}_${beatSyncData.beats.size}_${imageCount}_${numShaders}_$hookStartTimeMs"
        if (key == cachedClipsKey && cachedClips != null) {
            return cachedClips!!
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
        cachedClipsKey = key
        return clips
    }

    // ============================================
    // DRAWING
    // ============================================

    private fun drawHoldFrame(textureId: Int) {
        if (textureId == 0) return

        val program = shaderCache.getPassthroughProgram()
        if (program == 0) return

        GLES20.glUseProgram(program)

        // Bind texture
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        val texLoc = GLES20.glGetUniformLocation(program, "uTexFrom")
        GLES20.glUniform1i(texLoc, 0)

        drawQuad(program)
    }

    private fun drawTransition(
        fromTex: Int,
        toTex: Int,
        transition: Transition,
        progress: Float,
        aspectRatio: Float
    ) {
        val program = shaderCache.getProgram(transition)
        if (program == 0) return

        GLES20.glUseProgram(program)

        // Bind from texture to unit 0
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, fromTex)
        val fromLoc = GLES20.glGetUniformLocation(program, "uTexFrom")
        GLES20.glUniform1i(fromLoc, 0)

        // Bind to texture to unit 1
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, toTex)
        val toLoc = GLES20.glGetUniformLocation(program, "uTexTo")
        GLES20.glUniform1i(toLoc, 1)

        // Set uniforms
        val progressLoc = GLES20.glGetUniformLocation(program, "progress")
        GLES20.glUniform1f(progressLoc, progress)

        val ratioLoc = GLES20.glGetUniformLocation(program, "ratio")
        GLES20.glUniform1f(ratioLoc, aspectRatio)

        drawQuad(program)
    }

    private fun drawQuad(program: Int) {
        val positionLoc = GLES20.glGetAttribLocation(program, "aPosition")
        val texCoordLoc = GLES20.glGetAttribLocation(program, "aTexCoord")

        // Position attribute (x, y) — stride 16 bytes (4 floats * 4 bytes), offset 0
        GLES20.glEnableVertexAttribArray(positionLoc)
        quadBuffer.position(0)
        GLES20.glVertexAttribPointer(positionLoc, 2, GLES20.GL_FLOAT, false, 16, quadBuffer)

        // TexCoord attribute (u, v) — stride 16 bytes, offset 8 bytes (2 floats)
        GLES20.glEnableVertexAttribArray(texCoordLoc)
        quadBuffer.position(2)
        GLES20.glVertexAttribPointer(texCoordLoc, 2, GLES20.GL_FLOAT, false, 16, quadBuffer)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        GLES20.glDisableVertexAttribArray(positionLoc)
        GLES20.glDisableVertexAttribArray(texCoordLoc)
    }
}
