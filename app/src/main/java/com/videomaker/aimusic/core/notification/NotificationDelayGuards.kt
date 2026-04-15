package com.videomaker.aimusic.core.notification

fun hasReachedDelay(elapsedMs: Long, requiredDelayMs: Long): Boolean {
    return elapsedMs >= requiredDelayMs.coerceAtLeast(0L)
}
