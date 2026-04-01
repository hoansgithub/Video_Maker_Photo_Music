package com.videomaker.aimusic.media.library

import android.content.Context
import com.videomaker.aimusic.domain.model.VideoTemplate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * VideoTemplateLibrary - Loads video templates from local JSON (fallback only)
 *
 * Primary source is Supabase. This JSON is used when network is unavailable.
 * Filter by vibeTags (e.g. "birthday", "wedding", "travel", "party", "love")
 */
object VideoTemplateLibrary {

    private var context: Context? = null
    private var cachedTemplates: List<VideoTemplate>? = null

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun init(context: Context) {
        this.context = context.applicationContext
    }

    private fun loadTemplates(): List<VideoTemplate> {
        cachedTemplates?.let { return it }

        val ctx = context ?: throw IllegalStateException(
            "VideoTemplateLibrary not initialized. Call init(context) first."
        )

        return try {
            val jsonString = ctx.assets.open("video_templates.json")
                .bufferedReader()
                .use { it.readText() }

            val templatesJson = json.decodeFromString<List<VideoTemplateJson>>(jsonString)
            val templates = templatesJson.map { it.toDomain() }.filter { it.isActive }

            cachedTemplates = templates
            templates
        } catch (e: Exception) {
            android.util.Log.e("VideoTemplateLibrary", "Failed to load templates", e)
            emptyList()
        }
    }

    fun getAll(): List<VideoTemplate> = loadTemplates()

    fun getById(id: String): VideoTemplate? = loadTemplates().find { it.id == id }

    fun getByVibeTag(tag: String): List<VideoTemplate> =
        loadTemplates().filter { it.vibeTags.contains(tag) }

    fun getFree(): List<VideoTemplate> = loadTemplates().filter { !it.isPremium }

    fun getPremium(): List<VideoTemplate> = loadTemplates().filter { it.isPremium }

    fun clearCache() {
        cachedTemplates = null
    }
}

// ============================================
// JSON DATA CLASSES
// ============================================

@Serializable
private data class VideoTemplateJson(
    val id: String,
    val name: String,
    @SerialName("thumbnailPath") val thumbnailPath: String = "",
    @SerialName("songId") val songId: Long,
    @SerialName("effectSetId") val effectSetId: String,
    @SerialName("aspectRatio") val aspectRatio: String = "9:16",
    @SerialName("imageDurationMs") val imageDurationMs: Int = 3000,
    @SerialName("transitionPct") val transitionPct: Int = 30,
    @SerialName("vibeTags") val vibeTags: List<String> = emptyList(),
    @SerialName("isPremium") val isPremium: Boolean = false,
    @SerialName("isActive") val isActive: Boolean = true
) {
    fun toDomain() = VideoTemplate(
        id = id,
        name = name,
        thumbnailPath = thumbnailPath,
        previewImagePath = thumbnailPath, // Fallback uses same image for both
        songId = songId,
        effectSetId = effectSetId,
        aspectRatio = aspectRatio,
        imageDurationMs = imageDurationMs,
        transitionPct = transitionPct,
        vibeTags = vibeTags,
        isPremium = isPremium,
        isActive = isActive
    )
}