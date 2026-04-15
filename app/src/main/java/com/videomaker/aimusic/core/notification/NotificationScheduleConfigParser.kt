package com.videomaker.aimusic.core.notification

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

object NotificationScheduleConfigParser {

    private const val KEY_TRENDING_SONG_DAILY = "trending_song_daily"
    private const val KEY_VIRAL_TEMPLATE_DAILY = "viral_template_daily"
    private const val KEY_HOUR = "hour"
    private const val KEY_MINUTE = "minute"
    private const val KEY_QUICK_SAVE_DELAY_MINUTES = "quick_save_delay_minutes"
    private const val KEY_SHARE_ENCOURAGEMENT_DELAY_MINUTES = "share_encouragement_delay_minutes"
    private const val KEY_FORGOTTEN_MASTERPIECE_DELAY_MINUTES = "forgotten_masterpiece_delay_minutes"
    private const val KEY_ABANDONED_SAME_SESSION_DELAY_MINUTES = "abandoned_same_session_delay_minutes"
    private const val KEY_ABANDONED_COLD_SESSION_DELAY_MINUTES = "abandoned_cold_session_delay_minutes"
    private const val KEY_DRAFT_COMPLETION_NUDGE_DELAY_MINUTES = "draft_completion_nudge_delay_minutes"

    private const val MIN_HOUR = 0
    private const val MAX_HOUR = 23
    private const val MIN_MINUTE = 0
    private const val MAX_MINUTE = 59
    /** Delay minutes must stay within 0..10080 (7 days). Invalid values fall back to the field default. */
    private const val MIN_DELAY_MINUTES = 0L
    private const val MAX_DELAY_MINUTES = 10_080L
    private const val MINUTES_TO_MS = 60_000L

    fun parse(json: String?): NotificationScheduleConfig {
        if (json.isNullOrBlank()) {
            return NotificationScheduleConfig()
        }

        return try {
            val root = Json.parseToJsonElement(json).jsonObject
            NotificationScheduleConfig(
                trendingHour = readScheduleHour(
                    root = root,
                    key = KEY_TRENDING_SONG_DAILY,
                    defaultValue = NotificationScheduleConfig.DEFAULT_TRENDING_HOUR
                ),
                trendingMinute = readScheduleMinute(
                    root = root,
                    key = KEY_TRENDING_SONG_DAILY,
                    defaultValue = NotificationScheduleConfig.DEFAULT_TRENDING_MINUTE
                ),
                viralHour = readScheduleHour(
                    root = root,
                    key = KEY_VIRAL_TEMPLATE_DAILY,
                    defaultValue = NotificationScheduleConfig.DEFAULT_VIRAL_HOUR
                ),
                viralMinute = readScheduleMinute(
                    root = root,
                    key = KEY_VIRAL_TEMPLATE_DAILY,
                    defaultValue = NotificationScheduleConfig.DEFAULT_VIRAL_MINUTE
                ),
                quickSaveDelayMs = readDelayMsField(
                    root = root,
                    key = KEY_QUICK_SAVE_DELAY_MINUTES,
                    defaultValue = NotificationScheduleConfig.DEFAULT_QUICK_SAVE_DELAY_MS
                ),
                shareEncouragementDelayMs = readDelayMsField(
                    root = root,
                    key = KEY_SHARE_ENCOURAGEMENT_DELAY_MINUTES,
                    defaultValue = NotificationScheduleConfig.DEFAULT_SHARE_ENCOURAGEMENT_DELAY_MS
                ),
                forgottenMasterpieceDelayMs = readDelayMsField(
                    root = root,
                    key = KEY_FORGOTTEN_MASTERPIECE_DELAY_MINUTES,
                    defaultValue = NotificationScheduleConfig.DEFAULT_FORGOTTEN_MASTERPIECE_DELAY_MS
                ),
                abandonedSameDelayMs = readDelayMsField(
                    root = root,
                    key = KEY_ABANDONED_SAME_SESSION_DELAY_MINUTES,
                    defaultValue = NotificationScheduleConfig.DEFAULT_ABANDONED_SAME_DELAY_MS
                ),
                abandonedColdDelayMs = readDelayMsField(
                    root = root,
                    key = KEY_ABANDONED_COLD_SESSION_DELAY_MINUTES,
                    defaultValue = NotificationScheduleConfig.DEFAULT_ABANDONED_COLD_DELAY_MS
                ),
                draftCompletionDelayMs = readDelayMsField(
                    root = root,
                    key = KEY_DRAFT_COMPLETION_NUDGE_DELAY_MINUTES,
                    defaultValue = NotificationScheduleConfig.DEFAULT_DRAFT_COMPLETION_DELAY_MS
                )
            )
        } catch (_: Exception) {
            NotificationScheduleConfig()
        }
    }

    private fun readScheduleHour(
        root: Map<String, JsonElement>,
        key: String,
        defaultValue: Int
    ): Int {
        return readNestedIntField(
            root = root,
            key = key,
            childKey = "hour",
            defaultValue = defaultValue,
            minValue = MIN_HOUR,
            maxValue = MAX_HOUR
        )
    }

    private fun readScheduleMinute(
        root: Map<String, JsonElement>,
        key: String,
        defaultValue: Int
    ): Int {
        return readNestedIntField(
            root = root,
            key = key,
            childKey = "minute",
            defaultValue = defaultValue,
            minValue = MIN_MINUTE,
            maxValue = MAX_MINUTE
        )
    }

    private fun readNestedIntField(
        root: Map<String, JsonElement>,
        key: String,
        childKey: String,
        defaultValue: Int,
        minValue: Int,
        maxValue: Int
    ): Int {
        val raw = root[key] ?: return defaultValue
        val nested = runCatching { raw.jsonObject[childKey] }.getOrNull() ?: return defaultValue
        val value = runCatching { nested.jsonPrimitive.content.toInt() }.getOrNull() ?: return defaultValue
        return if (value in minValue..maxValue) value else defaultValue
    }

    private fun readDelayMsField(
        root: Map<String, JsonElement>,
        key: String,
        defaultValue: Long
    ): Long {
        val raw = root[key] ?: return defaultValue
        val valueMinutes = runCatching { raw.jsonPrimitive.content.toLong() }.getOrNull() ?: return defaultValue
        if (valueMinutes !in MIN_DELAY_MINUTES..MAX_DELAY_MINUTES) {
            return defaultValue
        }
        return valueMinutes * MINUTES_TO_MS
    }
}
