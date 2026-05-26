package com.videomaker.aimusic.media.library

import android.content.Context
import com.videomaker.aimusic.data.mapper.toMusicSongs
import com.videomaker.aimusic.data.remote.dto.SongDto
import com.videomaker.aimusic.domain.model.MusicSong
import com.videomaker.aimusic.domain.model.VideoTemplate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.Json

/**
 * BundledContentLibrary — small curated "GL default" content shipped in the APK.
 *
 * Used by the geo-aware loaders as a *timeout fallback*: when live content does not
 * return within the configured window (slow / no network), the screen shows this
 * bundled set so it never appears empty.
 *
 * Asset files (in app/src/main/assets/):
 * - default_global_songs.json     → 5 songs, mp3 + cover bundled offline
 * - default_global_templates.json → 3 templates, mp4 + webp bundled offline
 *
 * JSON shape mirrors the Supabase tables (snake_case). Songs reuse [SongDto] directly;
 * templates use a local DTO because asset paths are full `file:///android_asset/...`
 * URIs (no base-URL prefixing like the remote repo does).
 */
object BundledContentLibrary {

    private const val ASSET_SONGS = "default_global_songs.json"
    private const val ASSET_TEMPLATES = "default_global_templates.json"
    private const val TAG = "BundledContentLibrary"

    private var context: Context? = null

    @Volatile
    private var cachedSongs: List<MusicSong>? = null

    @Volatile
    private var cachedTemplates: List<VideoTemplate>? = null

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun init(context: Context) {
        this.context = context.applicationContext
    }

    fun getDefaultSongs(): List<MusicSong> {
        cachedSongs?.let { return it }
        val ctx = context ?: return emptyList()
        return try {
            val jsonString = ctx.assets.open(ASSET_SONGS).bufferedReader().use { it.readText() }
            val songs = json.decodeFromString<List<SongDto>>(jsonString).toMusicSongs()
            cachedSongs = songs
            songs
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to load $ASSET_SONGS", e)
            emptyList()
        }
    }

    fun getDefaultTemplates(): List<VideoTemplate> {
        cachedTemplates?.let { return it }
        val ctx = context ?: return emptyList()
        return try {
            val jsonString = ctx.assets.open(ASSET_TEMPLATES).bufferedReader().use { it.readText() }
            val templates = json.decodeFromString<List<BundledTemplateJson>>(jsonString)
                .map { it.toDomain() }
                .filter { it.isActive }
            cachedTemplates = templates
            templates
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to load $ASSET_TEMPLATES", e)
            emptyList()
        }
    }
}

@Serializable
private data class BundledTemplateJson(
    val id: String,
    val name: String,
    @SerialName("name_i18n") val nameI18n: JsonObject? = null,
    @SerialName("thumbnail_path") val thumbnailPath: String,
    @SerialName("preview_path") val previewPath: String? = null,
    @SerialName("song_id") val songId: Long,
    @SerialName("effect_set_id") val effectSetId: String,
    @SerialName("aspect_ratio") val aspectRatio: String = "9:16",
    @SerialName("image_duration_ms") val imageDurationMs: Int = 3000,
    @SerialName("transition_pct") val transitionPct: Int = 30,
    @SerialName("is_premium") val isPremium: Boolean = false,
    @SerialName("is_featured") val isFeatured: Boolean = false,
    @SerialName("is_active") val isActive: Boolean = true,
    @SerialName("sort_order") val sortOrder: Int = 0,
    @SerialName("use_count") val useCount: Long = 0,
    @SerialName("view_count") val viewCount: Long = 0,
    @SerialName("vibe_tags") val vibeTags: List<String> = emptyList(),
    @SerialName("target_regions") val targetRegions: List<String>? = null
) {
    fun toDomain(): VideoTemplate {
        val isVideo = previewPath?.endsWith(".mp4", ignoreCase = true) == true
        return VideoTemplate(
            id = id,
            name = name,
            thumbnailPath = thumbnailPath,
            previewImagePath = if (!isVideo && !previewPath.isNullOrEmpty()) previewPath else "",
            videoUrl = if (isVideo) previewPath else null,
            songId = songId,
            effectSetId = effectSetId,
            aspectRatio = aspectRatio,
            imageDurationMs = imageDurationMs,
            transitionPct = transitionPct,
            vibeTags = vibeTags,
            isPremium = isPremium,
            isFeatured = isFeatured,
            isActive = isActive,
            useCount = useCount,
            viewCount = viewCount
        )
    }
}
