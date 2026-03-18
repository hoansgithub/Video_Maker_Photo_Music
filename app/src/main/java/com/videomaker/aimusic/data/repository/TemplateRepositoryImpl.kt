package com.videomaker.aimusic.data.repository

import com.videomaker.aimusic.core.data.local.ApiCacheManager
import com.videomaker.aimusic.domain.model.VibeTag
import com.videomaker.aimusic.domain.model.VideoTemplate
import com.videomaker.aimusic.domain.repository.TemplateRepository
import com.videomaker.aimusic.media.library.VideoTemplateLibrary
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class TemplateRepositoryImpl(
    private val supabaseClient: SupabaseClient,
    private val apiCacheManager: ApiCacheManager,
    private val templateLibrary: VideoTemplateLibrary = VideoTemplateLibrary
) : TemplateRepository {

    companion object {
        private const val TABLE_TEMPLATES = "templates"
        private val COLUMNS = Columns.raw(
            "id,name,thumbnail_url,song_id,effect_set_id,aspect_ratio," +
            "image_duration_ms,transition_pct,is_premium,is_active,sort_order," +
            "template_vibe_tags(vibe_tag_id,sort_order)"
        )
        // !inner = only return templates that have at least one matching tag row
        private val COLUMNS_BY_TAG = Columns.raw(
            "id,name,thumbnail_url,song_id,effect_set_id,aspect_ratio," +
            "image_duration_ms,transition_pct,is_premium,is_active,sort_order," +
            "template_vibe_tags!inner(vibe_tag_id,sort_order)"
        )
    }

    override suspend fun getTemplates(limit: Int, offset: Int): Result<List<VideoTemplate>> =
        withContext(Dispatchers.IO) {
            val cacheKey = ApiCacheManager.keyTemplates(limit, offset)
            apiCacheManager.get<List<VideoTemplate>>(cacheKey)
                ?.let { return@withContext Result.success(it) }

            try {
                val templates = supabaseClient.from(TABLE_TEMPLATES)
                    .select(COLUMNS) {
                        filter { eq("is_active", true) }
                        order("sort_order", Order.ASCENDING)
                        range(offset.toLong(), (offset + limit - 1).toLong())
                    }
                    .decodeList<TemplateDto>()
                    .map { it.toDomain() }

                apiCacheManager.put(cacheKey, templates)
                Result.success(templates)
            } catch (e: Exception) {
                apiCacheManager.getStale<List<VideoTemplate>>(cacheKey)
                    ?.let { return@withContext Result.success(it) }
                // Final fallback: local JSON asset (bounded, curated content)
                val fallback = templateLibrary.getAll()
                    .drop(offset)
                    .take(limit)
                Result.success(fallback)
            }
        }

    override suspend fun getTemplatesByVibeTag(
        tag: String,
        limit: Int,
        offset: Int
    ): Result<List<VideoTemplate>> = withContext(Dispatchers.IO) {
        val cacheKey = ApiCacheManager.keyTemplatesByTag(tag, limit, offset)
        apiCacheManager.get<List<VideoTemplate>>(cacheKey)
            ?.let { return@withContext Result.success(it) }

        try {
            val templates = supabaseClient.from(TABLE_TEMPLATES)
                .select(COLUMNS_BY_TAG) {
                    filter {
                        eq("is_active", true)
                        // Filters both the embedded rows AND the parent (due to !inner join)
                        eq("template_vibe_tags.vibe_tag_id", tag)
                    }
                    order("sort_order", Order.ASCENDING)
                    range(offset.toLong(), (offset + limit - 1).toLong())
                }
                .decodeList<TemplateDto>()
                .map { it.toDomain() }

            apiCacheManager.put(cacheKey, templates)
            Result.success(templates)
        } catch (e: Exception) {
            apiCacheManager.getStale<List<VideoTemplate>>(cacheKey)
                ?.let { return@withContext Result.success(it) }
            val fallback = templateLibrary.getByVibeTag(tag)
                .drop(offset)
                .take(limit)
            Result.success(fallback)
        }
    }

    override suspend fun getVibeTags(): Result<List<VibeTag>> = withContext(Dispatchers.IO) {
        apiCacheManager.get<List<VibeTag>>(ApiCacheManager.KEY_VIBE_TAGS)
            ?.let { return@withContext Result.success(it) }

        try {
            val tags = supabaseClient.from("vibe_tags")
                .select(Columns.raw("id,display_name,emoji")) {
                    filter {
                        eq("tag_type", "theme")
                        eq("is_active", true)
                    }
                    order("sort_order", Order.ASCENDING)
                }
                .decodeList<VibeTagDto>()
                .map { VibeTag(id = it.id, displayName = it.displayName, emoji = it.emoji) }

            apiCacheManager.put(ApiCacheManager.KEY_VIBE_TAGS, tags)
            Result.success(tags)
        } catch (e: Exception) {
            apiCacheManager.getStale<List<VibeTag>>(ApiCacheManager.KEY_VIBE_TAGS)
                ?.let { return@withContext Result.success(it) }
            // Fallback: derive tags from local JSON vibe tags
            val fallback = templateLibrary.getAll()
                .flatMap { it.vibeTags }
                .distinct()
                .map { VibeTag(id = it, displayName = it.replace('_', ' ').replaceFirstChar { c -> c.uppercase() }) }
            Result.success(fallback)
        }
    }

    override suspend fun clearCache() {
        apiCacheManager.clearTemplateCache()
    }
}

// ============================================
// DTO
// ============================================

@Serializable
private data class TemplateDto(
    val id: String,
    val name: String,
    @SerialName("thumbnail_url") val thumbnailUrl: String? = null,
    @SerialName("song_id") val songId: Long,
    @SerialName("effect_set_id") val effectSetId: String,
    @SerialName("aspect_ratio") val aspectRatio: String = "9:16",
    @SerialName("image_duration_ms") val imageDurationMs: Int = 3000,
    @SerialName("transition_pct") val transitionPct: Int = 30,
    @SerialName("is_premium") val isPremium: Boolean = false,
    @SerialName("is_active") val isActive: Boolean = true,
    @SerialName("sort_order") val sortOrder: Int = 0,
    @SerialName("template_vibe_tags") val vibeTags: List<VibeTagRef> = emptyList()
) {
    fun toDomain() = VideoTemplate(
        id = id,
        name = name,
        thumbnailUrl = thumbnailUrl ?: "",
        songId = songId,
        effectSetId = effectSetId,
        aspectRatio = aspectRatio,
        imageDurationMs = imageDurationMs,
        transitionPct = transitionPct,
        // Sort server-returned tags by sort_order (small list per template, ~1-3 items)
        vibeTags = vibeTags.sortedBy { it.sortOrder }.map { it.vibeTagId },
        isPremium = isPremium,
        isActive = isActive
    )
}

@Serializable
private data class VibeTagRef(
    @SerialName("vibe_tag_id") val vibeTagId: String,
    @SerialName("sort_order") val sortOrder: Int = 0
)

@Serializable
private data class VibeTagDto(
    val id: String,
    @SerialName("display_name") val displayName: String,
    val emoji: String = ""
)