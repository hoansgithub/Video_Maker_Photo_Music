package com.videomaker.aimusic.media.audio

object HookStartTimePolicy {

    fun resolve(hookStartTimeMs: Long, durationMs: Long?): Long {
        val normalizedHookStartTimeMs = hookStartTimeMs.coerceAtLeast(0L)
        val resolvedDurationMs = durationMs ?: return normalizedHookStartTimeMs

        if (resolvedDurationMs <= 0L) {
            return normalizedHookStartTimeMs
        }

        val maxHookStartTimeMs = maxOf(0L, resolvedDurationMs - 1L)
        return normalizedHookStartTimeMs.coerceAtMost(maxHookStartTimeMs)
    }
}
