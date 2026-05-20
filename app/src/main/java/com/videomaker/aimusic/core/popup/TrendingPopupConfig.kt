package com.videomaker.aimusic.core.popup

import co.alcheclub.lib.acccore.remoteconfig.RemoteConfig
import com.videomaker.aimusic.core.constants.RemoteConfigKeys
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Snapshot of the remote-config values that gate the trending popup.
 *
 * `intervalMinutes <= 0` disables the feature entirely (kill switch).
 */
data class TrendingPopupConfigValues(
    val intervalMinutes: Long,
    val dailyCap: Long
)

class TrendingPopupConfig(
    private val remoteConfig: RemoteConfig
) {
    fun read(): TrendingPopupConfigValues {
        val intervalMinutes = parseIntervalMinutes(
            remoteConfig.getString(RemoteConfigKeys.TRENDING_POPUP_INTERVAL)
        )
        val cap = remoteConfig.getLong(RemoteConfigKeys.TRENDING_POPUP_DAILY_CAP, 3L)
            .let { if (it < 0) 0L else it }
        return TrendingPopupConfigValues(intervalMinutes = intervalMinutes, dailyCap = cap)
    }

    /**
     * Parse `{"hour":H,"minute":M}`. Returns total minutes (`H*60 + M`).
     * Negative components are clamped to 0. Malformed JSON returns 0 (kill switch).
     */
    private fun parseIntervalMinutes(raw: String): Long {
        if (raw.isBlank()) return 0L
        return runCatching {
            val obj = Json.parseToJsonElement(raw).jsonObject
            val hour = (obj["hour"] as? JsonPrimitive)?.jsonPrimitive?.content?.toLongOrNull() ?: 0L
            val minute = (obj["minute"] as? JsonPrimitive)?.jsonPrimitive?.content?.toLongOrNull() ?: 0L
            (hour.coerceAtLeast(0L) * 60L) + minute.coerceAtLeast(0L)
        }.getOrDefault(0L)
    }
}
