package com.videomaker.aimusic.domain.repository

import com.videomaker.aimusic.domain.model.Sticker
import com.videomaker.aimusic.domain.model.StickerCategory

/**
 * Repository for stickers loaded from Supabase.
 *
 * - Categories: table `sticker_categories` (is_active = true, ordered by sort_order)
 * - Stickers per category: table `category_sticker` with embedded `stickers`
 *
 * First page of each request is cached for fast load (mirrors EffectSetRepository).
 */
interface StickerRepository {
    /** Fetch active sticker categories ordered by sort_order. */
    suspend fun getCategories(offset: Int, limit: Int): Result<List<StickerCategory>>

    /** Fetch stickers for a category (embedded), ordered by sort_order. */
    suspend fun getStickersByCategory(
        categoryId: String,
        offset: Int,
        limit: Int
    ): Result<List<Sticker>>

    /** Clears all cached sticker data. */
    suspend fun clearCache()
}
