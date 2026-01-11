package com.aimusic.videoeditor.media.library

import android.content.Context
import com.aimusic.videoeditor.domain.model.OverlayFrame
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * OverlayFrameLibrary - Loads overlay frames from JSON
 *
 * Frames are decorative borders rendered on top of videos.
 */
object OverlayFrameLibrary {

    private var context: Context? = null
    private var cachedFrames: List<OverlayFrame>? = null

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * Initialize the library with application context.
     * Must be called before using other methods.
     */
    fun init(context: Context) {
        this.context = context.applicationContext
    }

    /**
     * Load frames from JSON file
     */
    private fun loadFrames(): List<OverlayFrame> {
        cachedFrames?.let { return it }

        val ctx = context ?: throw IllegalStateException(
            "OverlayFrameLibrary not initialized. Call init(context) first."
        )

        return try {
            val jsonString = ctx.assets.open("overlay_frames.json")
                .bufferedReader()
                .use { it.readText() }

            val framesJson = json.decodeFromString<List<OverlayFrameJson>>(jsonString)
            val frames = framesJson.map { jsonFrame ->
                OverlayFrame(
                    id = jsonFrame.id,
                    name = jsonFrame.name,
                    description = jsonFrame.description,
                    thumbnailUrl = jsonFrame.thumbnailUrl,
                    frameUrl = jsonFrame.frameUrl,
                    isPremium = jsonFrame.isPremium,
                    isActive = jsonFrame.isActive
                )
            }.filter { it.isActive }

            cachedFrames = frames
            frames
        } catch (e: Exception) {
            android.util.Log.e("OverlayFrameLibrary", "Failed to load frames", e)
            emptyList()
        }
    }

    fun getAll(): List<OverlayFrame> = loadFrames()

    fun getById(id: String): OverlayFrame? = loadFrames().find { it.id == id }

    fun getFree(): List<OverlayFrame> = loadFrames().filter { !it.isPremium }

    fun getPremium(): List<OverlayFrame> = loadFrames().filter { it.isPremium }

    /**
     * Clear cached frames (useful for development hot reload)
     */
    fun clearCache() {
        cachedFrames = null
    }
}

// ============================================
// JSON DATA CLASSES
// ============================================

@Serializable
private data class OverlayFrameJson(
    val id: String,
    val name: String,
    val description: String = "",
    val thumbnailUrl: String = "",
    val frameUrl: String = "",
    val isPremium: Boolean = false,
    val isActive: Boolean = true
)
