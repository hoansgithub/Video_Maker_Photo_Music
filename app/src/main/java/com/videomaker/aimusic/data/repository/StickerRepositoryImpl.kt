package com.videomaker.aimusic.data.repository

import com.videomaker.aimusic.core.data.local.ApiCacheManager
import com.videomaker.aimusic.core.data.local.RegionProvider
import com.videomaker.aimusic.domain.model.Sticker
import com.videomaker.aimusic.domain.model.StickerCategory
import com.videomaker.aimusic.domain.repository.StickerRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * StickerRepository backed by Supabase Postgrest.
 *
 * Tables:
 * - sticker_categories
 * - category_sticker (embeds `stickers`)
 *
 * Caches first page only (offset = 0) for fast initial load; falls back to stale
 * cache on network error. Mirrors EffectSetRepositoryImpl.
 */
class StickerRepositoryImpl(
    private val supabaseClient: SupabaseClient,
    private val apiCacheManager: ApiCacheManager,
    private val regionProvider: RegionProvider
) : StickerRepository {

    companion object {
        private const val TABLE_CATEGORIES = "sticker_categories"
        private const val TABLE_CATEGORY_STICKER = "category_sticker"
        private const val ERROR_LOAD_FAILED = "Failed to load stickers"

        private const val CATEGORY_COLUMNS =
            "id,name,icon_url,thumbnail_url,is_premium,is_new,sort_order"
        private const val CATEGORY_STICKER_COLUMNS =
            "sort_order,stickers(id,name,icon_url,thumbnail_url,geo,is_premium,is_new)"

        private fun cacheKeyCategories(region: String, limit: Int, offset: Int): String =
            "sticker_categories_${region}_${limit}_${offset}"

        private fun cacheKeyStickers(region: String, categoryId: String, limit: Int, offset: Int): String =
            "category_stickers_${region}_${categoryId}_${limit}_${offset}"
    }

    override suspend fun getCategories(offset: Int, limit: Int): Result<List<StickerCategory>> =
        withContext(Dispatchers.IO) {
            val region = regionProvider.getRegionCode()
            val cacheKey = cacheKeyCategories(region, limit, offset)

            if (offset == 0) {
                apiCacheManager.get<List<StickerCategory>>(cacheKey)
                    ?.let { return@withContext Result.success(it) }
            }

            try {
                val dtos = supabaseClient.from(TABLE_CATEGORIES)
                    .select(Columns.raw(CATEGORY_COLUMNS)) {
                        filter { eq("is_active", true) }
                        order("sort_order", Order.ASCENDING)
                        limit(limit.toLong())
                        range(offset.toLong(), (offset + limit - 1).toLong())
                    }
                    .decodeList<StickerCategoryDto>()

                val categories = dtos.map { it.toCategory() }
                if (offset == 0) apiCacheManager.put(cacheKey, categories)
                Result.success(categories)
            } catch (e: Exception) {
                if (offset == 0) {
                    apiCacheManager.getStale<List<StickerCategory>>(cacheKey)
                        ?.let { return@withContext Result.success(it) }
                }
                Result.failure(Exception(ERROR_LOAD_FAILED, e))
            }
        }

    override suspend fun getStickersByCategory(
        categoryId: String,
        offset: Int,
        limit: Int
    ): Result<List<Sticker>> = withContext(Dispatchers.IO) {
        val region = regionProvider.getRegionCode()
        val cacheKey = cacheKeyStickers(region, categoryId, limit, offset)

        if (offset == 0) {
            apiCacheManager.get<List<Sticker>>(cacheKey)
                ?.let { return@withContext Result.success(it) }
        }

        try {
            val rows = supabaseClient.from(TABLE_CATEGORY_STICKER)
                .select(Columns.raw(CATEGORY_STICKER_COLUMNS)) {
                    filter { eq("category_id", categoryId) }
                    order("sort_order", Order.ASCENDING)
                    limit(limit.toLong())
                    range(offset.toLong(), (offset + limit - 1).toLong())
                }
                .decodeList<CategoryStickerDto>()

            val stickers = rows.mapNotNull { row -> row.stickers?.toSticker(row.sortOrder) }
            if (offset == 0) apiCacheManager.put(cacheKey, stickers)
            Result.success(stickers)
        } catch (e: Exception) {
            if (offset == 0) {
                apiCacheManager.getStale<List<Sticker>>(cacheKey)
                    ?.let { return@withContext Result.success(it) }
            }
            Result.failure(Exception(ERROR_LOAD_FAILED, e))
        }
    }

    override suspend fun clearCache() {
        withContext(Dispatchers.IO) { apiCacheManager.clearAll() }
    }

    @Serializable
    private data class StickerCategoryDto(
        // API returns numeric ids — decode as Int, expose as String to the domain.
        val id: Int,
        val name: String = "",
        @SerialName("icon_url") val iconUrl: String? = null,
        @SerialName("thumbnail_url") val thumbnailUrl: String? = null,
        @SerialName("is_premium") val isPremium: Boolean = false,
        @SerialName("is_new") val isNew: Boolean = false,
        @SerialName("sort_order") val sortOrder: Int = 0
    ) {
        fun toCategory() = StickerCategory(
            id = id.toString(),
            name = name,
            iconUrl = iconUrl ?: "",
            thumbnailUrl = thumbnailUrl ?: "",
            isPremium = isPremium,
            isNew = isNew,
            sortOrder = sortOrder
        )
    }

    @Serializable
    private data class CategoryStickerDto(
        @SerialName("sort_order") val sortOrder: Int = 0,
        val stickers: StickerDto? = null
    )

    @Serializable
    private data class StickerDto(
        val id: Int,
        val name: String = "",
        @SerialName("icon_url") val iconUrl: String? = null,
        @SerialName("thumbnail_url") val thumbnailUrl: String? = null,
        // geo may be a plain string or a jsonb object — decode loosely to avoid crashes.
        val geo: JsonElement? = null,
        @SerialName("is_premium") val isPremium: Boolean = false,
        @SerialName("is_new") val isNew: Boolean = false
    ) {
        fun toSticker(sortOrder: Int) = Sticker(
            id = id.toString(),
            name = name,
            iconUrl = iconUrl ?: "",
            thumbnailUrl = thumbnailUrl ?: "",
            geo = geo?.toString(),
            isPremium = isPremium,
            isNew = isNew,
            sortOrder = sortOrder
        )
    }
}
