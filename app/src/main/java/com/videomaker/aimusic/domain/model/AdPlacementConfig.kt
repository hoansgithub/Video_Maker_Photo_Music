package com.videomaker.aimusic.domain.model

import kotlinx.serialization.Serializable

/**
 * Domain model for ad placement configuration from Remote Config.
 *
 * Represents a single ad placement (e.g., "interstitial_editor_enter") with its configuration:
 * - enabled: Whether this placement is active
 * - type: Ad format ("interstitial", "native", "banner", "rewarded", "app_open")
 * - units: Waterfall list of ad units to try in order
 * - extras: Additional key-value settings for this placement
 *
 * Example Remote Config JSON:
 * ```json
 * {
 *   "interstitial_editor_enter": {
 *     "enabled": true,
 *     "type": "interstitial",
 *     "units": [
 *       {"network": "admob", "unitId": "ca-app-pub-xxx/yyy"},
 *       {"network": "pangle", "unitId": "123456"}
 *     ],
 *     "extras": {
 *       "minInterval": "60"
 *     }
 *   }
 * }
 * ```
 */
@Serializable
data class AdPlacementConfig(
    /**
     * Whether this ad placement is enabled.
     * If false, ads for this placement will not load or show.
     */
    val enabled: Boolean = true,

    /**
     * Ad format type: "interstitial", "native", "banner", "rewarded", "app_open"
     */
    val type: String,

    /**
     * Waterfall list of ad units to try in order (primary → fallback).
     * Each unit specifies network and unit ID.
     */
    val units: List<AdUnitConfig> = emptyList(),

    /**
     * Additional settings for this placement (e.g., refresh rate, timeout).
     * Example: {"minInterval": "60", "timeout": "10"}
     */
    val extras: Map<String, String> = emptyMap()
)

/**
 * Individual ad unit configuration within a waterfall.
 *
 * Example:
 * ```json
 * {
 *   "network": "admob",
 *   "unitId": "ca-app-pub-xxx/yyy",
 *   "extras": {"timeout": "5"}
 * }
 * ```
 */
@Serializable
data class AdUnitConfig(
    /**
     * Ad network identifier: "admob", "pangle", "meta", "applovin", etc.
     */
    val network: String,

    /**
     * Network-specific ad unit ID.
     * - AdMob: "ca-app-pub-xxx/yyy"
     * - Pangle: "123456"
     */
    val unitId: String,

    /**
     * Additional unit-specific settings (e.g., timeout, floor price).
     */
    val extras: Map<String, String> = emptyMap()
)
