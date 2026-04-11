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
 * 1. User's detected country (SIM → Network → Locale)
 * 2. Remote Config priorities (updatable without app release)
 * 3. Hardcoded fallback priorities
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
 */
class LanguageConfigService(
    private val context: Context
) : ConfigurableObject {

    // Cache for remote languages (code → SupportedLanguage)
    private val remoteLanguages = mutableMapOf<String, SupportedLanguage>()

    // Cache for remote config priorities (country code → language code list)
    private val remotePriorities = mutableMapOf<String, List<String>>()

    /**
     * Sort languages based on user's country and configured priorities.
     *
     * Language source priority:
     * 1. Remote Config languages (if available)
     * 2. Hardcoded fallback languages
     *
     * Sorting priority:
     * 1. SIM card country (most reliable)
     * 2. Network country (fallback)
     * 3. System locale country (second fallback)
     * 4. Device language (last fallback)
     *
     * @return Sorted list with prioritized languages first, rest alphabetically
     */
    fun getSortedLanguages(): List<SupportedLanguage> {
        // Use remote languages if available, otherwise fallback to hardcoded
        val availableLanguages = if (remoteLanguages.isNotEmpty()) {
            remoteLanguages.values.toList()
        } else {
            LanguageManager.getAllLanguages()
        }

        val country = detectUserCountry()
        val priorityCodes = getLanguagePriorityForCountry(country)

        // Prioritized languages (maintain priority order)
        val prioritized = priorityCodes.mapNotNull { code ->
            availableLanguages.find { it.code == code }
        }

        // Rest of languages (not in priority list)
        val rest = availableLanguages.filter { it.code !in priorityCodes }

        return prioritized + rest
    }

    /**
     * Detect user's country code using multiple fallback strategies.
     *
     * @return Country code (e.g., "US", "BR", "ID") or empty string if unknown
     */
    private fun detectUserCountry(): String {
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager

        // Try SIM card country (most reliable)
        tm?.simCountryIso?.uppercase()?.takeIf { it.isNotEmpty() }?.let { return it }

        // Try network country (fallback)
        tm?.networkCountryIso?.uppercase()?.takeIf { it.isNotEmpty() }?.let { return it }

        // Try system locale country (second fallback)
        val config = context.resources.configuration
        ConfigurationCompat.getLocales(config).get(0)?.country?.uppercase()?.takeIf { it.isNotEmpty() }?.let { return it }

        // Unknown country
        return ""
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
     * Matches drama app pattern with supported languages only.
     * Supported: en, ar, es, fil, hi, id, pt, tr
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
            "BR", "PT" -> listOf("pt", "en", "es")

            // Latin America - Spanish (short list like drama app)
            "MX", "CO" -> listOf("es", "en")
            "ES", "AR", "PE", "CL", "VE" -> listOf("es", "en", "pt")

            // Asia - Southeast
            "ID" -> listOf("id", "en")
            "PH" -> listOf("fil", "en")

            // Asia - South
            "IN" -> listOf("hi", "en")
            "PK" -> listOf("en")  // Urdu not supported, fallback to English
            "BD" -> listOf("en")  // Bengali not supported, fallback to English

            // Middle East & North Africa
            "SA", "AE", "EG", "IQ", "JO", "LB", "SY", "YE", "KW", "OM", "QA", "BH" ->
                listOf("ar", "en")

            // Europe - Turkish
            "TR" -> listOf("tr", "en")

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
