package com.videomaker.aimusic.core.notification

fun hasReachedDelay(elapsedMs: Long, requiredDelayMs: Long): Boolean {
    return elapsedMs >= requiredDelayMs.coerceAtLeast(0L)
}

fun shouldSkipDraftNudgeDueToRecentAbandoned(
    nowMs: Long,
    lastAbandonedShownAtMs: Long?,
    dedupeWindowMs: Long,
    fastScheduleMode: Boolean
): Boolean {
    if (fastScheduleMode) return false
    val shownAt = lastAbandonedShownAtMs ?: return false
    return (nowMs - shownAt) < dedupeWindowMs.coerceAtLeast(0L)
}

fun shouldRetryQuickSaveWhileForeground(
    appBackgroundAtMs: Long?,
    generatedAtMs: Long,
    runAttemptCount: Int,
    maxRetryCount: Int
): Boolean {
    val cappedMaxRetries = maxRetryCount.coerceAtLeast(0)
    if (runAttemptCount >= cappedMaxRetries) return false
    val backgroundAt = appBackgroundAtMs ?: return true
    return backgroundAt <= generatedAtMs
}
