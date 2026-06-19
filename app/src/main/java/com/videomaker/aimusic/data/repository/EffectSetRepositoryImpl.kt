package com.videomaker.aimusic.data.repository

import com.videomaker.aimusic.core.data.local.ApiCacheManager
import com.videomaker.aimusic.core.data.local.RegionProvider
import com.videomaker.aimusic.domain.model.EffectSet
import com.videomaker.aimusic.domain.repository.EffectSetRepository
import com.videomaker.aimusic.media.library.TransitionSetLibrary
import com.videomaker.aimusic.media.library.TransitionShaderLibrary
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Implementation of EffectSetRepository using Supabase Postgrest.
 *
 * Table: effect_sets
 * Caches first page only (offset = 0) for fast initial load.
 * Falls back to empty list on network errors (no local data available for now).
 */
class EffectSetRepositoryImpl(
    private val supabaseClient: SupabaseClient,
    private val apiCacheManager: ApiCacheManager,
    private val regionProvider: RegionProvider
) : EffectSetRepository {

    companion object {
        private const val TABLE_EFFECT_SETS = "effect_sets"
        private const val ERROR_LOAD_FAILED = "Failed to load effect sets"
        private const val ERROR_NOT_FOUND = "Effect set not found"

        /** Cache key for first page only (offset = 0). */
        private fun cacheKeyEffectSetsPaged(region: String, limit: Int, offset: Int): String =
            "effect_sets_paged_${region}_${limit}_${offset}"
    }

    override suspend fun getEffectSetsPaged(offset: Int, limit: Int): Result<List<EffectSet>> =
        withContext(Dispatchers.IO) {
            val region = regionProvider.getRegionCode()
            val cacheKey = cacheKeyEffectSetsPaged(region, limit, offset)

            // Only cache first page (offset = 0)
            if (offset == 0) {
                apiCacheManager.get<List<EffectSet>>(cacheKey)
                    ?.let {
                        TransitionSetLibrary.registerRemote(it)
                        return@withContext Result.success(it)
                    }
            }

            try {
                val dtos = supabaseClient.from(TABLE_EFFECT_SETS)
                    .select {
                        filter {
                            eq("is_active", true)
                        }
                        order("sort_order", Order.ASCENDING)
                        order("created_at", Order.ASCENDING)
                        limit(limit.toLong())
                        this.range(offset.toLong(), (offset + limit - 1).toLong())
                    }
                    .decodeList<EffectSetDto>()

                val effectSets = dtos.map { it.toEffectSet() }

                // Register in TransitionSetLibrary so CompositionFactory can resolve them
                TransitionSetLibrary.registerRemote(effectSets)

                // Cache first page only
                if (offset == 0) {
                    apiCacheManager.put(cacheKey, effectSets)
                }

                Result.success(effectSets)
            } catch (e: Exception) {
                // Fallback: stale cache for first page, empty list for subsequent pages
                if (offset == 0) {
                    apiCacheManager.getStale<List<EffectSet>>(cacheKey)
                        ?.let {
                            TransitionSetLibrary.registerRemote(it)
                            return@withContext Result.success(it)
                        }
                }
                Result.failure(Exception(ERROR_LOAD_FAILED, e))
            }
        }

    override suspend fun getEffectSetById(id: String): Result<EffectSet> =
        withContext(Dispatchers.IO) {
            try {
                val effectSet = supabaseClient.from(TABLE_EFFECT_SETS)
                    .select {
                        filter {
                            eq("id", id)
                            eq("is_active", true)
                        }
                        limit(1)
                    }
                    .decodeSingleOrNull<EffectSetDto>()

                if (effectSet != null) {
                    val mapped = effectSet.toEffectSet()
                    TransitionSetLibrary.registerRemote(listOf(mapped))
                    Result.success(mapped)
                } else {
                    Result.failure(Exception(ERROR_NOT_FOUND))
                }
            } catch (e: Exception) {
                Result.failure(Exception(ERROR_LOAD_FAILED, e))
            }
        }

    override suspend fun clearCache() {
        withContext(Dispatchers.IO) {
            apiCacheManager.clearAll()
        }
    }

    /**
     * DTO for effect_sets table row.
     *
     * Maps to EffectSet domain model.
     */
    @Serializable
    private data class EffectSetDto(
        val id: String,
        val name: String,
        val description: String = "",
        @SerialName("is_premium")
        val isPremium: Boolean = false,
        @SerialName("is_active")
        val isActive: Boolean = true,
        @SerialName("sort_order")
        val sortOrder: Int = 0,
        @SerialName("transition_ids")
        val transitionIds: List<String>? = null,
        @SerialName("is_new")
        val isNew: Boolean = false,
        @SerialName("thumbnail_url")
        val thumbnailUrl: String? = null
    ) {
        /**
         * Maps DTO to domain model.
         * Resolves transition IDs to Transition objects via TransitionShaderLibrary.
         */
        fun toEffectSet() = EffectSet(
            id = id,
            name = name,
            description = description,
            thumbnailUrl = thumbnailUrl ?: "",
            isPremium = isPremium,
            isActive = isActive,
            transitionIds = transitionIds ?: emptyList(),
            transitions = transitionIds
                ?.mapNotNull { TransitionShaderLibrary.getById(it) }
                ?: emptyList(),
            sortOrder = sortOrder,
            isNew = isNew
        )
    }
}
