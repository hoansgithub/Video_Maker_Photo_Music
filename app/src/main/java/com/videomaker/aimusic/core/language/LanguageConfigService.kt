package com.videomaker.aimusic.core.language

import android.content.Context
import android.telephony.TelephonyManager
import androidx.core.os.ConfigurationCompat
import co.alcheclub.lib.acccore.remoteconfig.ConfigContainer
import co.alcheclub.lib.acccore.remoteconfig.ConfigurableObject
import com.videomaker.aimusic.core.data.local.LanguageManager
import com.videomaker.aimusic.core.data.local.SupportedLanguage
import com.videomaker.aimusic.core.data.local.getAllLanguages
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Language Configuration Service
 *
 * Handles intelligent language sorting based on:
 * 1. Device language (ALWAYS FIRST - respects user's explicit choice)
 * 2. User's region detection (uses RegionProvider with region_detection_config):
 *    - IP-based geolocation (if enabled via remote config, VPN-aware)
 *    - SIM card country (physical location, permanent)
 *    - Network operator country (physical location, ignores VPN)
 *    - System locale country (last fallback)
 * 3. Remote Config priorities (updatable without app release)
 * 4. Hardcoded fallback priorities
 *
 * Region detection respects region_detection_config:
 * - When use_ip_geolocation = true: Uses IP detection first, falls back to SIM/Network/Locale
 * - When use_ip_geolocation = false: Uses SIM → Network → Locale only
 *
 * Remote Config Format:
 * ```json
 * {
 *   "onboarding_language_priority": {
 *     "priorities": {
 *       "US": ["en", "es", "fil", "ar", "hi"],
 *       "BR": ["pt", "en", "es"],
 *       "ID": ["id", "en"]
 *     }
 *   }
 * }
 * ```
 *
 * Implements ConfigurableObject for automatic Remote Config updates.
 * Registration happens centrally in VideoMakerApplication.kt.
 *
 * @param context Application context for device language detection
 * @param regionProvider Provides region code with IP detection support
 */
class LanguageConfigService(
    private val context: Context,
    private val regionProvider: com.videomaker.aimusic.core.data.local.RegionProvider
) : ConfigurableObject {

    // Cache for remote languages (code → SupportedLanguage)
    private val remoteLanguages = mutableMapOf<String, SupportedLanguage>()

    // Cache for remote config priorities (country code → language code list)
    private val remotePriorities = mutableMapOf<String, List<String>>()

    /**
     * Sort languages based on device language and user's region.
     *
     * Language source priority:
     * 1. Remote Config languages (if available)
     * 2. Hardcoded fallback languages
     *
     * Sorting priority:
     * 1. Device language (ALWAYS FIRST - respects user's explicit choice)
     * 2. Region-based priorities (uses RegionProvider with IP detection if enabled)
     * 3. Rest of languages in default order
     *
     * Region detection (via RegionProvider):
     * - If region_detection_config.use_ip_geolocation = true:
     *   IP → Persisted → SIM → Network → Locale → "us"
     * - If region_detection_config.use_ip_geolocation = false:
     *   Persisted → SIM → Network → Locale → "us"
     *
     * @return Sorted list with device language first, then region priorities, rest after
     */
    /**
     * Sort languages using full region detection (may block for IP detection).
     * Call from a background thread / IO dispatcher.
     */
    fun getSortedLanguages(): List<SupportedLanguage> {
        return sortByRegion(regionProvider.getRegionCode().uppercase())
    }

    /**
     * Sort languages using system region only (SIM/locale). Never blocks.
     * Safe to call during composition or from the main thread.
     */
    fun getSortedLanguagesImmediate(): List<SupportedLanguage> {
        return sortByRegion(regionProvider.getRegionCodeImmediate().uppercase())
    }

    private fun sortByRegion(region: String): List<SupportedLanguage> {
        val availableLanguages = if (remoteLanguages.isNotEmpty()) {
            remoteLanguages.values.toList()
        } else {
            LanguageManager.getAllLanguages()
        }

        val deviceLanguage = getDeviceLanguage()
        val deviceLanguageAvailable = deviceLanguage?.let { lang ->
            availableLanguages.any { it.code == lang }
        } ?: false

        val regionPriorities = getLanguagePriorityForCountry(region)

        val priorityCodes = listOfNotNull(deviceLanguage?.takeIf { deviceLanguageAvailable }) +
                           regionPriorities.filter { it != deviceLanguage }

        val prioritized = priorityCodes.mapNotNull { code ->
            availableLanguages.find { it.code == code }
        }

        val rest = availableLanguages.filter { it.code !in priorityCodes }

        return prioritized + rest
    }

    /**
     * Get device's current language setting.
     *
     * @return Language code (e.g., "en", "es", "fil") or null if unsupported
     */
    private fun getDeviceLanguage(): String? {
        val config = context.resources.configuration
        val locale = ConfigurationCompat.getLocales(config).get(0)
        val rawCode = locale?.language

        android.util.Log.d(TAG, "🌐 Device locale: $locale")
        android.util.Log.d(TAG, "🌐 Raw language code: $rawCode")

        if (rawCode == null) {
            android.util.Log.w(TAG, "⚠️ No device language detected")
            return null
        }

        // Handle legacy language codes
        val deviceCode = when (rawCode) {
            "tl" -> "fil"  // Android/Java reports Tagalog for Filipino
            "in" -> "id"   // Java legacy ISO 639-1 code for Indonesian
            else -> rawCode
        }

        android.util.Log.d(TAG, "✅ Device language: $deviceCode")
        return deviceCode
    }

    /**
     * Get language priority list for a specific country.
     *
     * Priority:
     * 1. Remote Config (if available)
     * 2. Hardcoded fallback
     * 3. Device language + English (last fallback)
     *
     * @param country Country code (e.g., "US", "BR")
     * @return List of language codes in priority order
     */
    fun getLanguagePriorityForCountry(country: String): List<String> {
        // Try remote config first
        remotePriorities[country]?.let { return it }

        // Fallback to hardcoded priorities
        return getHardcodedPriority(country)
    }

    /**
     * Hardcoded language priorities per country (fallback).
     *
     * Supported: en, ar, es, fil, hi, id, pt, tr, bn, kn, de, jv, su, ha, yo, ig
     *
     * Based on:
     * - Official languages of the country
     * - English as international fallback
     * - Regional language preferences
     */
    private fun getHardcodedPriority(country: String): List<String> {
        return when (country) {
            // North America
            "US" -> listOf("en", "es", "fil", "ar", "hi")

            // Latin America - Portuguese
            "BR" -> listOf("pt", "es", "en", "de")
            "PT" -> listOf("pt", "en", "es")

            // Latin America - Spanish
            "MX" -> listOf("es")
            "CO" -> listOf("es", "en")
            "ES", "AR", "PE", "CL", "VE" -> listOf("es", "en", "pt")

            // Asia - Southeast
            "ID" -> listOf("id", "jv", "su")
            "PH" -> listOf("fil", "en")
            "VN" -> listOf("vi", "en")

            // Asia - South
            "IN" -> listOf("hi", "bn", "en", "kn")
            "PK" -> listOf("en", "hi", "ar", "bn")
            "BD" -> listOf("bn", "en", "ar", "hi")

            // Middle East & North Africa
            "SA", "AE", "EG", "IQ", "JO", "LB", "SY", "YE", "KW", "OM", "QA", "BH" ->
                listOf("ar", "en")

            // Europe - Turkish
            "TR" -> listOf("tr", "en")

            // Europe - German
            "DE" -> listOf("de")
            "AT", "CH" -> listOf("de", "en")

            // Africa - Nigeria
            "NG" -> listOf("ha", "yo", "ig", "en")

            // Unknown country - use device language
            else -> {
                val config = context.resources.configuration
                val rawCode = ConfigurationCompat.getLocales(config).get(0)?.language ?: "en"

                // Handle legacy language codes
                val deviceCode = when (rawCode) {
                    "tl" -> "fil"  // Android/Java reports Tagalog for Filipino
                    "in" -> "id"   // Java legacy ISO 639-1 code for Indonesian
                    else -> rawCode
                }

                // Device language first, English second (unless device is English)
                if (deviceCode == "en") listOf("en") else listOf(deviceCode, "en")
            }
        }
    }

    /**
     * Update configuration from Remote Config.
     *
     * Called automatically by RemoteConfigCoordinator when config changes.
     *
     * Expected format:
     * ```json
     * {
     *   "onboarding_languages": {
     *     "languages": [
     *       {"code": "en", "name": "English", "country_code": "US"},
     *       {"code": "es", "name": "Español", "country_code": "ES"}
     *     ]
     *   },
     *   "onboarding_language_priority": {
     *     "priorities": {
     *       "US": ["en", "es", "fil"],
     *       "BR": ["pt", "en", "es"]
     *     }
     *   }
     * }
     * ```
     */
    override suspend fun update(config: ConfigContainer) {
        remoteLanguages.clear()
        remotePriorities.clear()

        // Parse onboarding_languages
        try {
            val languagesJson = config.getString("onboarding_languages")
            if (languagesJson != null) {
                val languagesConfig = Json.parseToJsonElement(languagesJson).jsonObject
                val languagesArray = languagesConfig["languages"]?.jsonArray

                languagesArray?.forEach { languageElement ->
                    try {
                        val languageObj = languageElement.jsonObject
                        val code = languageObj["code"]?.jsonPrimitive?.content ?: return@forEach
                        val name = languageObj["name"]?.jsonPrimitive?.content ?: return@forEach
                        val countryCode = languageObj["country_code"]?.jsonPrimitive?.content ?: ""

                        val flag = countryCodeToFlag(countryCode)
                        val isRtl = code == "ar" // Arabic is RTL

                        remoteLanguages[code] = SupportedLanguage(
                            code = code,
                            displayName = name,
                            flag = flag,
                            isRtl = isRtl
                        )
                    } catch (e: Exception) {
                        android.util.Log.w(TAG, "Failed to parse language: ${e.message}")
                    }
                }

                android.util.Log.d(TAG, "📚 Loaded ${remoteLanguages.size} languages from Remote Config")
            }
        } catch (e: Exception) {
            android.util.Log.w(TAG, "Failed to parse onboarding_languages: ${e.message}")
        }

        // Parse onboarding_language_priority
        try {
            val priorityJson = config.getString("onboarding_language_priority")
            if (priorityJson != null) {
                val priorityConfig = Json.parseToJsonElement(priorityJson).jsonObject
                val priorities = priorityConfig["priorities"]?.jsonObject

                priorities?.forEach { (country, jsonArray) ->
                    try {
                        val codes = jsonArray.jsonArray.mapNotNull { it.jsonPrimitive.content }
                        if (codes.isNotEmpty()) {
                            remotePriorities[country] = codes
                            android.util.Log.d(TAG, "Remote priority for $country: $codes")
                        }
                    } catch (e: Exception) {
                        android.util.Log.w(TAG, "Failed to parse priority for $country: ${e.message}")
                    }
                }

                android.util.Log.d(TAG, "📊 Loaded ${remotePriorities.size} country priorities from Remote Config")
            }
        } catch (e: Exception) {
            android.util.Log.w(TAG, "Failed to parse onboarding_language_priority: ${e.message}")
        }
    }

    /**
     * Convert country code to flag emoji.
     * E.g., "US" → "🇺🇸", "BR" → "🇧🇷"
     */
    private fun countryCodeToFlag(countryCode: String): String {
        if (countryCode.length != 2) return "🌐" // Generic globe for invalid codes

        return countryCode.uppercase().map { char ->
            // Convert A-Z to regional indicator symbols (🇦-🇿)
            // A = 0x1F1E6, B = 0x1F1E7, etc.
            Character.toChars(0x1F1E6 + (char - 'A')).concatToString()
        }.joinToString("")
    }

    companion object {
        private const val TAG = "LanguageConfigService"
    }
}
