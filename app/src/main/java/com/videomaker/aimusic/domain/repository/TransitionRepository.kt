package com.videomaker.aimusic.domain.repository

import com.videomaker.aimusic.domain.model.Transition

/**
 * Repository for fetching individual transition shaders from Supabase.
 *
 * Enables OTA shader updates: new transitions can be added to the Supabase
 * `transitions` table and downloaded at runtime without an app release.
 *
 * Downloaded shaders are registered in TransitionShaderLibrary for use
 * by CompositionFactory and the GL renderer.
 */
interface TransitionRepository {
    /**
     * Fetches transitions by their IDs from Supabase.
     * Only fetches IDs not already cached locally or in the remote cache.
     * Registers fetched transitions in TransitionShaderLibrary.
     *
     * @param ids List of transition IDs to fetch
     * @return Result containing list of fetched Transition objects
     */
    suspend fun fetchRemoteTransitions(ids: List<String>): Result<List<Transition>>

    /**
     * Fetches all active transitions from Supabase.
     * Registers fetched transitions in TransitionShaderLibrary.
     *
     * @return Result containing list of all active Transition objects
     */
    suspend fun fetchAllRemoteTransitions(): Result<List<Transition>>
}
