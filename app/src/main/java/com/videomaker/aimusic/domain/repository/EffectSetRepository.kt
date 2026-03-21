package com.videomaker.aimusic.domain.repository

import com.videomaker.aimusic.domain.model.EffectSet

/**
 * Repository for Effect Sets (Transition collections).
 *
 * Provides paginated access to transition sets from Supabase,
 * with caching for the first page.
 */
interface EffectSetRepository {
    /**
     * Fetches a page of effect sets from Supabase.
     *
     * @param offset Pagination offset (0-based)
     * @param limit Number of items per page
     * @return Result containing list of EffectSet, or error
     */
    suspend fun getEffectSetsPaged(offset: Int, limit: Int): Result<List<EffectSet>>

    /**
     * Fetches a single effect set by ID.
     *
     * @param id Effect set ID
     * @return Result containing EffectSet, or error
     */
    suspend fun getEffectSetById(id: String): Result<EffectSet>

    /**
     * Clears all cached effect set data.
     */
    suspend fun clearCache()
}
