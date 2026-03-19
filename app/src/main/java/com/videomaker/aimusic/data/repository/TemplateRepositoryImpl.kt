package com.videomaker.aimusic.data.repository

import com.videomaker.aimusic.core.data.local.ApiCacheManager
import com.videomaker.aimusic.core.data.local.RegionProvider
import com.videomaker.aimusic.domain.model.VibeTag
import com.videomaker.aimusic.domain.model.VideoTemplate
import com.videomaker.aimusic.domain.repository.TemplateRepository
import com.videomaker.aimusic.media.library.VideoTemplateLibrary
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class TemplateRepositoryImpl(
    private val supabaseClient: SupabaseClient,
    private val apiCacheManager: ApiCacheManager,
    private val templateLibrary: VideoTemplateLibrary = VideoTemplateLibrary,
    private val regionProvider: RegionProvider
) : TemplateRepository {

    companion object {
        private const val TABLE_TEMPLATES = "templates"
        private const val FN_TEMPLATES_SORTED = "get_templates_sorted"
        private const val FN_TEMPLATES_BY_TAG_SORTED = "get_templates_by_tag_sorted"
        private val COLUMNS_TEMPLATE = Columns.raw(
            "id,name,thumbnail_path,song_id,effect_set_id,aspect_ratio," +
            "image_duration_ms,transition_pct,is_premium,is_active,sort_order,use_count," +
            "template_vibe_tags(vibe_tag_id,sort_order)"
        )
    }

    override suspend fun getTemplates(limit: Int, offset: Int): Result<List<VideoTemplate>> =
        withContext(Dispatchers.IO) {
            val region = regionProvider.getRegionCode()
            val cacheKey = ApiCacheManager.keyTemplates(region, limit, offset)
            apiCacheManager.get<List<VideoTemplate>>(cacheKey)
                ?.let { return@withContext Result.success(it) }

            try {
                val templates = supabaseClient.postgrest
                    .rpc(FN_TEMPLATES_SORTED, buildJsonObject {
                        put("p_region", region)
                        put("p_limit", limit)
                        put("p_offset", offset)
                    })
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
        val region = regionProvider.getRegionCode()
        val cacheKey = ApiCacheManager.keyTemplatesByTag(region, tag, limit, offset)
        apiCacheManager.get<List<VideoTemplate>>(cacheKey)
            ?.let { return@withContext Result.success(it) }

        try {
            val templates = supabaseClient.postgrest
                .rpc(FN_TEMPLATES_BY_TAG_SORTED, buildJsonObject {
                    put("p_region", region)
                    put("p_tag", tag)
                    put("p_limit", limit)
                    put("p_offset", offset)
                })
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
            // !inner joins through template_vibe_tags → templates, ensuring only tags that have
            // at least one *active* linked template are returned. This prevents chips that would
            // always return empty results when tapped.
            val tags = supabaseClient.from("vibe_tags")
                .select(Columns.raw("id,display_name,emoji,template_vibe_tags!inner(vibe_tag_id,templates!inner(id,is_active))")) {
                    filter {
                        eq("tag_type", "theme")
                        eq("is_active", true)
                        eq("template_vibe_tags.templates.is_active", true)
                    }
                    order("sort_order", Order.ASCENDING)
                    limit(100)
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

    override suspend fun getFeaturedTemplates(limit: Int): Result<List<VideoTemplate>> =
        withContext(Dispatchers.IO) {
            val cacheKey = "featured_templates_$limit"
            apiCacheManager.get<List<VideoTemplate>>(cacheKey)
                ?.let { return@withContext Result.success(it) }

            try {
                val templates = supabaseClient.from(TABLE_TEMPLATES)
                    .select(COLUMNS_TEMPLATE) {
                        filter { eq("is_active", true) }
                        order("use_count", Order.DESCENDING)
                        limit(limit.toLong())
                    }
                    .decodeList<TemplateDto>()
                    .map { it.toDomain() }

                apiCacheManager.put(cacheKey, templates)
                Result.success(templates)
            } catch (e: Exception) {
                apiCacheManager.getStale<List<VideoTemplate>>(cacheKey)
                    ?.let { return@withContext Result.success(it) }
                val fallback = templateLibrary.getAll()
                    .sortedByDescending { it.useCount }
                    .take(limit)
                Result.success(fallback)
            }
        }

    override suspend fun searchTemplates(query: String): Result<List<VideoTemplate>> =
        withContext(Dispatchers.IO) {
            val q = query.trim()
            if (q.isEmpty()) return@withContext Result.success(emptyList())

            try {
                val templates = supabaseClient.from(TABLE_TEMPLATES)
                    .select(COLUMNS_TEMPLATE) {
                        filter {
                            eq("is_active", true)
                            ilike("name", "%$q%")
                        }
                        order("use_count", Order.DESCENDING)
                        limit(15)
                    }
                    .decodeList<TemplateDto>()
                    .map { it.toDomain() }

                Result.success(templates)
            } catch (e: Exception) {
                Result.failure(Exception("Failed to search templates", e))
            }
        }

    override suspend fun clearCache() {
        apiCacheManager.clearTemplateCache()
    }
}

// ============================================
// DTO
// ============================================

private const val THUMBNAIL_BASE_URL =
    "https://zdydtiwglotssklnkwjh.supabase.co/storage/v1/object/public/template-thumbnails/"

@Serializable
private data class TemplateDto(
    val id: String,
    val name: String,
    @SerialName("thumbnail_path") val thumbnailPath: String? = null,
    @SerialName("song_id") val songId: Long,
    @SerialName("effect_set_id") val effectSetId: String,
    @SerialName("aspect_ratio") val aspectRatio: String = "9:16",
    @SerialName("image_duration_ms") val imageDurationMs: Int = 3000,
    @SerialName("transition_pct") val transitionPct: Int = 30,
    @SerialName("is_premium") val isPremium: Boolean = false,
    @SerialName("is_active") val isActive: Boolean = true,
    @SerialName("sort_order") val sortOrder: Int = 0,
    @SerialName("use_count") val useCount: Long = 0,
    @SerialName("template_vibe_tags") val vibeTags: List<VibeTagRef> = emptyList(),
    @SerialName("target_regions") val targetRegions: List<String> = emptyList()
) {
    fun toDomain() = VideoTemplate(
        id = id,
        name = name,
        thumbnailPath = if (!thumbnailPath.isNullOrEmpty()) THUMBNAIL_BASE_URL + thumbnailPath else "",
        songId = songId,
        effectSetId = effectSetId,
        aspectRatio = aspectRatio,
        imageDurationMs = imageDurationMs,
        transitionPct = transitionPct,
        // Sort server-returned tags by sort_order (small list per template, ~1-3 items)
        vibeTags = vibeTags.sortedBy { it.sortOrder }.map { it.vibeTagId },
        isPremium = isPremium,
        isActive = isActive,
        useCount = useCount
    )
}

@Serializable
private data class VibeTagRef(
    @SerialName("vibe_tag_id") val vibeTagId: String,
    @SerialName("sort_order") val sortOrder: Int = 0
)

/**
 * Junction row for the getVibeTags !inner join — discarded after mapping.
 * template_vibe_tags → templates is a many-to-one FK, so PostgREST returns
 * `templates` as a single nested object, not an array.
 */
@Serializable
private data class VibeTagJoinDto(
    @SerialName("vibe_tag_id") val vibeTagId: String = "",
    val templates: VibeTagTemplateJoinDto? = null
)

@Serializable
private data class VibeTagTemplateJoinDto(
    val id: String = "",
    @SerialName("is_active") val isActive: Boolean = true
)

@Serializable
private data class VibeTagDto(
    val id: String,
    @SerialName("display_name") val displayName: String,
    val emoji: String = "",
    // Included only to satisfy the !inner join chain — not used after mapping
    @SerialName("template_vibe_tags") val templateVibeTags: List<VibeTagJoinDto> = emptyList()
)