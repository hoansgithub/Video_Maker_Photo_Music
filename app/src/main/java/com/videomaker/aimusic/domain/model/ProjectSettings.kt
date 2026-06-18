package com.videomaker.aimusic.domain.model

/**
 * ProjectSettings - Domain model for project editing settings
 *
 * BEAT-SYNC MODE ONLY:
 * - beatSyncData: Beat positions from song's beats_url (REQUIRED)
 * - hookStartTimeMs: Where to start music playback (best part of song)
 * - Transitions land on every 4th beat, duration = min(60000/BPM, 1000)ms
 * - Last image holds for 6 beats with audio fadeout
 *
 * AUDIO:
 * - audioNodes: Multi-track audio timeline. Single source of truth for all audio.
 * - primaryAudioNode: Convenience accessor for the first (main) audio node.
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
 * @param aspectRatio Output video aspect ratio
 * @param audioNodes Multi-track audio nodes (single source of truth for all audio)
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
    val aspectRatio: AspectRatio = AspectRatio.RATIO_9_16,

    // ============================================
    // MULTI-TRACK AUDIO (single source of truth)
    // ============================================
    val audioNodes: List<AudioNode> = emptyList()
) {

    /** The first (primary) audio node, or null if no audio is configured. */
    val primaryAudioNode: AudioNode? get() = audioNodes.firstOrNull()

    companion object {
        val DEFAULT = ProjectSettings()
    }
}
