package com.videomaker.aimusic.core.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import androidx.core.os.LocaleListCompat

/**
 * LanguageManager - Handles app locale/language settings
 *
 * Manages:
 * - Persisting selected language to SharedPreferences
 * - Applying locale changes using AppCompat per-app language API
 * - Checking if language has been selected (for first-time users)
 *
 * Supported Languages:
 * - English (en) - Default
 * - Portuguese/Brazilian (pt)
 * - Spanish (es)
 * - Arabic (ar) - RTL
 * - Hindi (hi)
 * - Indonesian (id)
 * - Filipino (fil)
 * - Turkish (tr)
 */
class LanguageManager(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    companion object {
        private const val PREFS_NAME = "language_prefs"
        private const val KEY_SELECTED_LANGUAGE = "selected_language"
        private const val KEY_LANGUAGE_SELECTION_COMPLETE = "language_selection_complete"
        private const val KEY_GENRE_SELECTION_PENDING = "genre_selection_pending"

        // Supported language codes
        const val LANGUAGE_ENGLISH = "en"
        const val LANGUAGE_PORTUGUESE = "pt"
        const val LANGUAGE_SPANISH = "es"
        const val LANGUAGE_ARABIC = "ar"
        const val LANGUAGE_HINDI = "hi"
        const val LANGUAGE_INDONESIAN = "id"
        const val LANGUAGE_FILIPINO = "fil"
        const val LANGUAGE_TURKISH = "tr"

        val SUPPORTED_LANGUAGES = listOf(
            LANGUAGE_ENGLISH,
            LANGUAGE_PORTUGUESE,
            LANGUAGE_SPANISH,
            LANGUAGE_ARABIC,
            LANGUAGE_HINDI,
            LANGUAGE_INDONESIAN,
            LANGUAGE_FILIPINO,
            LANGUAGE_TURKISH
        )
    }

    /**
     * Check if user has completed language selection (pressed Continue).
     * Separate from whether a language is saved — user can browse languages
     * multiple times before confirming.
     */
    fun isLanguageSelectionComplete(): Boolean {
        return prefs.getBoolean(KEY_LANGUAGE_SELECTION_COMPLETE, false)
    }

    /**
     * Mark language selection as complete (user pressed Continue).
     */
    fun markLanguageSelectionComplete() {
        prefs.edit { putBoolean(KEY_LANGUAGE_SELECTION_COMPLETE, true) }
    }

    /**
     * Get the currently selected language code.
     * Prefers AppCompat's stored locale (single source of truth with autoStoreLocales=true),
     * falls back to SharedPreferences for first-run scenarios.
     */
    fun getSelectedLanguage(): String {
        val appLocales = AppCompatDelegate.getApplicationLocales()
        if (!appLocales.isEmpty) {
            val localeTag = appLocales[0]?.toLanguageTag() ?: LANGUAGE_ENGLISH
            // Normalize locale tag (e.g. "en-US" → "en", "fil" → "fil")
            val languageCode = localeTag.split("-", "_").firstOrNull() ?: LANGUAGE_ENGLISH
            if (languageCode in SUPPORTED_LANGUAGES) {
                return languageCode
            }
        }
        return prefs.getString(KEY_SELECTED_LANGUAGE, LANGUAGE_ENGLISH) ?: LANGUAGE_ENGLISH
    }

    /**
     * Save language preference without applying the locale.
     * Use during language browsing — no Activity recreation.
     * Call [applyLanguage] to commit the change.
     */
    fun saveLanguagePreference(languageCode: String) {
        if (languageCode !in SUPPORTED_LANGUAGES) return
        prefs.edit { putString(KEY_SELECTED_LANGUAGE, languageCode) }
    }

    /**
     * Apply the saved language preference via AppCompat per-app language API.
     * On Android 13+ handled by the system; on older versions AppCompat triggers recreation.
     * Call this right before navigating away so the next Activity starts with the new locale.
     */
    fun applyLanguage() {
        val savedLanguage = prefs.getString(KEY_SELECTED_LANGUAGE, LANGUAGE_ENGLISH) ?: LANGUAGE_ENGLISH
        val localeList = LocaleListCompat.forLanguageTags(savedLanguage)
        AppCompatDelegate.setApplicationLocales(localeList)
    }

    /**
     * Returns true if the user confirmed language selection but has not yet completed
     * the genre survey step. Used to re-route back to LanguageSelectionActivity on relaunch.
     */
    fun isGenreSelectionPending(): Boolean =
        prefs.getBoolean(KEY_GENRE_SELECTION_PENDING, false)

    /** Called when the user presses Continue on the language step. */
    fun markGenreSelectionPending() =
        prefs.edit { putBoolean(KEY_GENRE_SELECTION_PENDING, true) }

    /** Called after the genre survey is saved — clears the pending flag. */
    fun clearGenreSelectionPending() =
        prefs.edit { putBoolean(KEY_GENRE_SELECTION_PENDING, false) }

    /**
     * Check if current language is RTL (Right-to-Left).
     */
    fun isRtl(): Boolean = getSelectedLanguage() == LANGUAGE_ARABIC
}

/**
 * Data class representing a supported language.
 */
data class SupportedLanguage(
    val code: String,
    val displayName: String,
    val flag: String,
    val isRtl: Boolean = false
)

/**
 * All supported languages as an ordered list.
 */
fun LanguageManager.Companion.getAllLanguages(): List<SupportedLanguage> {
    return listOf(
        SupportedLanguage(LANGUAGE_ENGLISH, "English", "🇺🇸"),
        SupportedLanguage(LANGUAGE_PORTUGUESE, "Português", "🇧🇷"),
        SupportedLanguage(LANGUAGE_SPANISH, "Español", "🇪🇸"),
        SupportedLanguage(LANGUAGE_ARABIC, "العربية", "🇸🇦", isRtl = true),
        SupportedLanguage(LANGUAGE_HINDI, "हिन्दी", "🇮🇳"),
        SupportedLanguage(LANGUAGE_INDONESIAN, "Bahasa Indonesia", "🇮🇩"),
        SupportedLanguage(LANGUAGE_FILIPINO, "Filipino", "🇵🇭"),
        SupportedLanguage(LANGUAGE_TURKISH, "Türkçe", "🇹🇷")
    )
}