package com.videomaker.aimusic.modules.picker

/**
 * Returns true when current state already matches the requested permission mode
 * and another reload would only reset UI state unnecessarily.
 */
internal fun shouldSkipPermissionReload(
    currentState: AssetPickerUiState,
    isLimited: Boolean
): Boolean {
    return when (currentState) {
        is AssetPickerUiState.WithAssets.AllPermission -> !isLimited
        is AssetPickerUiState.WithAssets.LimitPermission -> isLimited
        else -> false
    }
}

/**
 * Merges lists while keeping first occurrence for each key.
 * Order of lists matters: earlier lists have higher priority.
 */
internal fun <T> mergeDistinctByKey(
    vararg lists: List<T>,
    keySelector: (T) -> String
): List<T> {
    val merged = mutableListOf<T>()
    val seen = HashSet<String>()

    lists.forEach { list ->
        list.forEach { item ->
            val key = keySelector(item)
            if (seen.add(key)) {
                merged.add(item)
            }
        }
    }

    return merged
}

internal fun shouldShowExitConfirm(selectedCount: Int): Boolean = selectedCount > 0

// ============================================
// Estimated video duration
// ============================================

/** Each selected photo contributes this many seconds to the estimated video. */
const val PICKER_DURATION_PER_PHOTO_SEC = 2.8

/** Recommended "social-ready" video length used to nudge the user toward more photos. */
const val PICKER_RECOMMENDED_DURATION_SEC = 15.0

/** Recommended length in milliseconds (used when comparing against beat-synced durations). */
const val PICKER_RECOMMENDED_DURATION_MS = 15_000L

/**
 * Formats a duration in milliseconds as MM:SS, matching [com.videomaker.aimusic.domain.model.Project.formattedDuration]
 * so the picker's estimate reads identically to the editor's real duration.
 */
internal fun formatPickerDurationMs(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}

/** estimatedDuration = selectedPhotos × durationPerPhoto */
internal fun estimatedPickerDurationSec(selectedCount: Int): Double =
    selectedCount * PICKER_DURATION_PER_PHOTO_SEC

/**
 * Number of extra photos needed to reach the recommended ~15s length.
 * additionalPhotosNeeded = floor((15 - estimatedDuration) / durationPerPhoto), floored at 0.
 */
internal fun additionalPhotosForIdealDuration(selectedCount: Int): Int {
    val remaining = PICKER_RECOMMENDED_DURATION_SEC - estimatedPickerDurationSec(selectedCount)
    if (remaining <= 0.0) return 0
    return kotlin.math.floor(remaining / PICKER_DURATION_PER_PHOTO_SEC).toInt()
}

/** Formats the estimated duration as M:SS (e.g. 0:08). */
internal fun formatPickerDuration(selectedCount: Int): String {
    val totalSec = estimatedPickerDurationSec(selectedCount).toInt()
    val minutes = totalSec / 60
    val seconds = totalSec % 60
    return "%d:%02d".format(minutes, seconds)
}
