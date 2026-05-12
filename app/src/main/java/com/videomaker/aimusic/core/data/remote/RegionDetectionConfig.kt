package com.videomaker.aimusic.core.data.remote

import co.alcheclub.lib.acccore.remoteconfig.ConfigContainer
import co.alcheclub.lib.acccore.remoteconfig.ConfigurableObject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Remote Config for region detection strategy.
 *
 * Controls whether to use IP-based geolocation (VPN-aware) for region detection.
 * When enabled, IP geolocation becomes the top priority, falling back to
 * SIM → Network → Locale if IP detection fails or times out.
 *
 * Firebase Remote Config format:
 * ```json
 * {
 *   "region_detection_config": {
 *     "use_ip_geolocation": true,
 *     "ip_timeout_ms": 5000
 *   }
 * }
 * ```
 *
 * Default values (when remote config not loaded):
 * - use_ip_geolocation: false (disabled in production)
 * - ip_timeout_ms: 5000 (5 seconds)
 */
class RegionDetectionConfig : ConfigurableObject {

    @Volatile
    var useIpGeolocation: Boolean = false
        private set

    @Volatile
    var ipTimeoutMs: Long = 5000L
        private set

    override suspend fun update(config: ConfigContainer) {
        val json = config.getString("region_detection_config")
        if (json != null) {
            try {
                val jsonObject = Json.parseToJsonElement(json).jsonObject

                useIpGeolocation = jsonObject["use_ip_geolocation"]
                    ?.jsonPrimitive?.content?.toBoolean() ?: false

                ipTimeoutMs = jsonObject["ip_timeout_ms"]
                    ?.jsonPrimitive?.content?.toLongOrNull() ?: 5000L

                // Clamp timeout to reasonable range (1-30 seconds)
                ipTimeoutMs = ipTimeoutMs.coerceIn(1000L, 30_000L)

                android.util.Log.d(
                    TAG,
                    "📡 Region detection config updated: IP=${useIpGeolocation}, timeout=${ipTimeoutMs}ms"
                )
            } catch (e: Exception) {
                android.util.Log.w(TAG, "Failed to parse region_detection_config: ${e.message}")
            }
        }
    }

    companion object {
        private const val TAG = "RegionDetectionConfig"
    }
}
