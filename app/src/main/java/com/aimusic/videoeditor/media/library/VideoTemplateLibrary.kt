package com.aimusic.videoeditor.media.library

import android.content.Context
import com.aimusic.videoeditor.domain.model.VideoTemplate
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * VideoTemplateLibrary - Loads video templates from JSON
 *
 * Templates combine a song + effect set for quick video creation.
 * Use tags to filter: trending, new, seasonal
 */
object VideoTemplateLibrary {

    private var context: Context? = null
    private var cachedTemplates: List<VideoTemplate>? = null

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
     * Load templates from JSON file
     */
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
            val templates = templatesJson.map { jsonTemplate ->
                VideoTemplate(
                    id = jsonTemplate.id,
                    name = jsonTemplate.name,
                    description = jsonTemplate.description,
                    thumbnailUrl = jsonTemplate.thumbnailUrl,
                    songId = jsonTemplate.songId,
                    effectSetId = jsonTemplate.effectSetId,
                    aspectRatio = jsonTemplate.aspectRatio,
                    tags = jsonTemplate.tags,
                    isPremium = jsonTemplate.isPremium,
                    isActive = jsonTemplate.isActive
                )
            }.filter { it.isActive }

            cachedTemplates = templates
            templates
        } catch (e: Exception) {
            android.util.Log.e("VideoTemplateLibrary", "Failed to load templates", e)
            emptyList()
        }
    }

    fun getAll(): List<VideoTemplate> = loadTemplates()

    fun getById(id: String): VideoTemplate? = loadTemplates().find { it.id == id }

    fun getByTag(tag: String): List<VideoTemplate> =
        loadTemplates().filter { it.tags.contains(tag) }

    fun getTrending(): List<VideoTemplate> = getByTag("trending")

    fun getNew(): List<VideoTemplate> = getByTag("new")

    fun getSeasonal(): List<VideoTemplate> = getByTag("seasonal")

    fun getFree(): List<VideoTemplate> = loadTemplates().filter { !it.isPremium }

    fun getPremium(): List<VideoTemplate> = loadTemplates().filter { it.isPremium }

    fun getByAspectRatio(aspectRatio: String): List<VideoTemplate> =
        loadTemplates().filter { it.aspectRatio == aspectRatio }

    /**
     * Clear cached templates (useful for development hot reload)
     */
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
    val description: String,
    val thumbnailUrl: String = "",
    val songId: Int,
    val effectSetId: String,
    val aspectRatio: String = "9:16",
    val tags: List<String> = emptyList(),
    val isPremium: Boolean = false,
    val isActive: Boolean = true
)
