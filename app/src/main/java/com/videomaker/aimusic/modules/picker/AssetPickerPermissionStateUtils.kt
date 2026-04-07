package com.videomaker.aimusic.modules.picker

internal data class PermissionSnapshot(
    val fullGranted: Boolean,
    val limitedGranted: Boolean
)

internal enum class PermissionMode {
    DENIED,
    LIMITED,
    FULL
}

internal enum class PermissionUpdateSource {
    INITIAL,
    RESUME,
    REQUEST_RESULT,
    MANUAL_REFRESH
}

internal fun resolvePermissionMode(snapshot: PermissionSnapshot): PermissionMode {
    return when {
        snapshot.fullGranted -> PermissionMode.FULL
        snapshot.limitedGranted -> PermissionMode.LIMITED
        else -> PermissionMode.DENIED
    }
}

internal fun shouldRequestPermissionDialog(
    previousMode: PermissionMode?,
    newMode: PermissionMode
): Boolean {
    return newMode == PermissionMode.DENIED && previousMode != PermissionMode.DENIED
}

internal fun retainSelectedUrisAfterReload(
    selectedUris: Set<String>,
    availableUris: Set<String>
): Set<String> {
    return selectedUris.intersect(availableUris)
}
