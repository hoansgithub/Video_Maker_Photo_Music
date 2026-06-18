package com.videomaker.aimusic.domain.repository

import com.videomaker.aimusic.domain.model.BeatSyncData

/**
 * Repository for loading beat-sync data.
 *
 * Beat JSON files are downloaded from URLs stored in the `beats_url` column of the songs table.
 *
 * Graceful degradation: If beat data is unavailable (network error, 404, parsing error),
 * returns null instead of throwing. This allows the app to fall back to legacy fixed-duration mode.
 */
interface BeatSyncRepository {
    /**
     * Get beat data for a song.
     *
     * Checks local cache first, downloads from beats_url if not cached.
     *
     * @param songId The song ID (matches songs table primary key)
     * @param beatsUrl Optional direct URL to the beats JSON file. If null, looked up from the songs table.
     * @return Result with BeatSyncData if successful, null if unavailable (triggers legacy fallback)
     */
    suspend fun getBeatData(songId: Long, beatsUrl: String? = null): Result<BeatSyncData?>
}
