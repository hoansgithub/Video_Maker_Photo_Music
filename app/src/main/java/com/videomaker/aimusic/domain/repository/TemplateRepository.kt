package com.videomaker.aimusic.domain.repository

import com.videomaker.aimusic.domain.model.VibeTag
import com.videomaker.aimusic.domain.model.VideoTemplate

interface TemplateRepository {
    /**
     * Get a page of active templates ordered by sort_order.
     * Priority: cache → Supabase → local JSON fallback.
     */
    suspend fun getTemplates(limit: Int, offset: Int = 0): Result<List<VideoTemplate>>

    /**
     * Get templates for a specific vibe tag, server-side filtered and paginated.
     */
    suspend fun getTemplatesByVibeTag(tag: String, limit: Int, offset: Int = 0): Result<List<VideoTemplate>>

    /**
     * Get all active vibe tags with tag_type = 'theme' for filter chips.
     */
    suspend fun getVibeTags(): Result<List<VibeTag>>

    /**
     * Get top templates by use_count for the home screen featured carousel.
     * Not region-filtered — shows globally popular templates.
     */
    suspend fun getFeaturedTemplates(limit: Int): Result<List<VideoTemplate>>

    /**
     * Search templates by name (ILIKE). Returns up to 15 results ordered by use_count.
     * Not cached — search results are query-specific.
     */
    suspend fun searchTemplates(query: String): Result<List<VideoTemplate>>

    /** Clears cached template data. Call before pull-to-refresh. */
    suspend fun clearCache()
}