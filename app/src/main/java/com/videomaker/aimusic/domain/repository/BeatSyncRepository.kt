package com.videomaker.aimusic.domain.repository

import com.videomaker.aimusic.domain.model.BeatSyncData

/**
 * Repository for loading beat-sync data from Supabase storage.
 *
 * Beat JSON files are stored in Supabase storage bucket: beats-cache
 * Path format: beats-cache/<song_id>.json
 *
 * Graceful degradation: If beat data is unavailable (network error, 404, parsing error),
 * returns null instead of throwing. This allows the app to fall back to legacy fixed-duration mode.
 */
interface BeatSyncRepository {
    /**
     * Get beat data for a song.
     *
     * Checks local cache first, downloads from Supabase if not cached.
     *
     * @param songId The song ID (matches songs table primary key)
     * @return Result with BeatSyncData if successful, null if unavailable (triggers legacy fallback)
     */
    suspend fun getBeatData(songId: Long): Result<BeatSyncData?>
}
