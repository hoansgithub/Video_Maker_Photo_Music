package com.videomaker.aimusic.media.renderer

import android.net.Uri
import com.videomaker.aimusic.domain.model.AspectRatio
import com.videomaker.aimusic.domain.model.BeatSyncData
import com.videomaker.aimusic.domain.model.Transition

/**
 * RenderState - Reactive state object for the GL preview renderer.
 *
 * The VideoRenderer reads this atomically each frame. Property changes
 * (effect set, aspect ratio, images) update fields here directly —
 * no composition rebuild required.
 *
 * @param imageUris Preprocessed image URIs (with blur background applied)
 * @param transitions Current effect set's transition shaders
 * @param beatSyncData Beat timing data for syncing transitions to music
 * @param aspectRatio Current output aspect ratio
 * @param overlayFrameId ID of selected overlay frame (null = none)
 * @param currentTimeMs Current playback position (updated by PlaybackClock)
 * @param isPlaying Whether playback is active
 * @param hookStartTimeMs Where music playback starts (ms from song start)
 * @param totalDurationMs Total project duration
 */
data class RenderState(
    val imageUris: List<Uri> = emptyList(),
    val transitions: List<Transition> = emptyList(),
    val beatSyncData: BeatSyncData? = null,
    val aspectRatio: AspectRatio = AspectRatio.RATIO_9_16,
    val overlayFrameId: String? = null,
    val currentTimeMs: Long = 0L,
    val isPlaying: Boolean = false,
    val hookStartTimeMs: Long = 0L,
    val totalDurationMs: Long = 0L
) {
    companion object {
        val EMPTY = RenderState()
    }
}
