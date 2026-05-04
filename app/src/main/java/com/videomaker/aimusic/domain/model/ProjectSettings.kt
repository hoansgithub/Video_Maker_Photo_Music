package com.videomaker.aimusic.domain.model

import android.net.Uri

/**
 * ProjectSettings - Domain model for project editing settings
 *
 * BEAT-SYNC MODE ONLY:
 * - beatSyncData: Beat positions from Supabase beats-cache bucket (REQUIRED)
 * - hookStartTimeMs: Where to start music playback (best part of song)
 * - Transitions land on every 4th beat, duration = min(60000/BPM, 1000)ms
 * - Last image holds for 6 beats with audio fadeout
 *
 * EFFECT SET:
 * - effectSetId: ID of selected effect set (collection of transitions)
 * - Each effect set contains multiple transitions that cycle through images
 *
 * @param beatSyncData Beat-sync timing data (REQUIRED for all videos)
 * @param hookStartTimeMs Where to start music playback in beat-sync mode (ms from song start)
 * @param totalDurationMs Total project duration (auto-calculated from beats + image count)
 * @param effectSetId ID of selected effect set (null = no transitions)
 * @param overlayFrameId ID of selected overlay frame (null = none)
 * @param musicSongId ID of Supabase MusicSong selected for this project (null = none)
 * @param musicSongName Cached song name for display purposes (null = none)
 * @param musicSongUrl Supabase song mp3 URL stored for offline composition (null = none)
 * @param musicSongCoverUrl Supabase song cover image URL for display (null = none)
 * @param customAudioUri User's custom audio URI from device (overrides musicSongId)
 * @param audioVolume Music volume (0.0 to 1.0)
 * @param processedAudioUri URI of pre-processed audio with fadeout (auto-generated)
 * @param aspectRatio Output video aspect ratio
 */
data class ProjectSettings(
    // ============================================
    // BEAT-SYNC MODE (REQUIRED)
    // ============================================
    val beatSyncData: BeatSyncData? = null,
    val hookStartTimeMs: Long = 0L,
    val totalDurationMs: Long = 0L,

    // ============================================
    // COMMON SETTINGS
    // ============================================
    val effectSetId: String? = "dreamy_vibes", // Default effect set
    val templateId: String? = null, // Null means no template selected
    val overlayFrameId: String? = null,
    val musicSongId: Long? = null,
    val musicSongName: String? = null, // Cached for display only
    val musicSongUrl: String? = null, // Cached for offline playback
    val musicSongCoverUrl: String? = null, // Cached for display only
    val customAudioUri: Uri? = null,
    val audioVolume: Float = 1.0f,
    val processedAudioUri: Uri? = null, // Pre-processed audio with fadeout
    val aspectRatio: AspectRatio = AspectRatio.RATIO_9_16
) {

    companion object {
        val DEFAULT = ProjectSettings()
    }
}
