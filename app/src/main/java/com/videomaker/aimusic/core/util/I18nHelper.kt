package com.videomaker.aimusic.core.util

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Helper for extracting localized values from Supabase JSONB i18n columns.
 *
 * Pattern: i18n data is stored as JSON objects with locale keys:
 * ```json
 * {
 *   "en": "Birthday Party",
 *   "pt": "Festa de Aniversário",
 *   "es": "Fiesta de Cumpleaños",
 *   "ar": "حفلة عيد ميلاد",
 *   "hi": "जन्मदिन की पार्टी",
 *   "id": "Pesta Ulang Tahun",
 *   "fil": "Handaan sa Kaarawan",
 *   "tr": "Doğum Günü Partisi"
 * }
 * ```
 *
 * Usage:
 * ```kotlin
 * val localizedName = getLocalizedValue(
 *     i18nData = template.nameI18n,
 *     locale = "pt",
 *     fallback = template.name
 * )
 * ```
 */
object I18nHelper {

    /**
     * Extracts localized value from JSONB i18n column with fallback chain:
     * 1. Try requested locale (e.g. "pt")
     * 2. Try English ("en") from i18n data
     * 3. Return fallback value (ALWAYS English from display_name column)
     *
     * Note: We do NOT use "first available key" because it could be any language.
     * The fallback parameter is guaranteed to be English.
     *
     * @param i18nData JSONB object from Supabase (nullable)
     * @param locale Requested locale code (en, pt, es, ar, hi, id, fil, tr)
     * @param fallback Fallback value in English (from display_name column in Supabase)
     * @return Localized string or English fallback
     */
    fun getLocalizedValue(
        i18nData: JsonObject?,
        locale: String,
        fallback: String
    ): String {
        if (i18nData == null) {
            return fallback
        }

        // Try requested locale
        i18nData[locale]?.jsonPrimitive?.content
            ?.takeIf { it.isNotBlank() }
            ?.let {
                return it
            }

        // Try English fallback from i18n data
        i18nData["en"]?.jsonPrimitive?.content
            ?.takeIf { it.isNotBlank() }
            ?.let {
                return it
            }

        // ✅ FIX: Use fallback parameter (English) instead of first available key (random language)
        // The fallback parameter is ALWAYS English (from display_name column)
        return fallback
    }

    /**
     * Returns all available locales in the i18n data.
     * Useful for debugging or showing language coverage.
     */
    fun getAvailableLocales(i18nData: JsonObject?): List<String> {
        return i18nData?.keys?.toList() ?: emptyList()
    }

    /**
     * Checks if i18n data contains a specific locale.
     */
    fun hasLocale(i18nData: JsonObject?, locale: String): Boolean {
        return i18nData?.containsKey(locale) == true &&
               i18nData[locale]?.jsonPrimitive?.content?.isNotBlank() == true
    }
}
