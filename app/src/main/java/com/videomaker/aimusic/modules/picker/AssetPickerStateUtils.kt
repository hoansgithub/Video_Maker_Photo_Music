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
