package com.aimusic.videoeditor.core.data.local

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import androidx.core.os.LocaleListCompat
import java.util.Locale

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
 * - Arabic (ar)
 * - Hindi (hi)
 * - Indonesian (in)
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
        private const val KEY_PENDING_LOCALE_RECREATION = "pending_locale_recreation"

        // Supported language codes
        const val LANGUAGE_ENGLISH = "en"
        const val LANGUAGE_ARABIC = "ar"
        const val LANGUAGE_HINDI = "hi"
        const val LANGUAGE_INDONESIAN = "in"

        val SUPPORTED_LANGUAGES = listOf(
            LANGUAGE_ENGLISH,
            LANGUAGE_ARABIC,
            LANGUAGE_HINDI,
            LANGUAGE_INDONESIAN
        )
    }

    /**
     * Check if user has completed language selection (pressed Continue)
     * This is separate from whether a language is saved - user can change language
     * multiple times before confirming.
     */
    fun isLanguageSelectionComplete(): Boolean {
        return prefs.getBoolean(KEY_LANGUAGE_SELECTION_COMPLETE, false)
    }

    /**
     * Mark language selection as complete (user pressed Continue)
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
        // AppCompat stores locale when autoStoreLocales=true
        // This is the source of truth after first language selection
        val appLocales = AppCompatDelegate.getApplicationLocales()
        if (!appLocales.isEmpty) {
            val localeTag = appLocales[0]?.toLanguageTag() ?: LANGUAGE_ENGLISH
            // Normalize locale tag (e.g., "en-US" -> "en")
            val languageCode = localeTag.split("-", "_").firstOrNull() ?: LANGUAGE_ENGLISH
            if (languageCode in SUPPORTED_LANGUAGES) {
                return languageCode
            }
        }
        // Fallback to SharedPreferences for first-run or if AppCompat locale not set
        return prefs.getString(KEY_SELECTED_LANGUAGE, LANGUAGE_ENGLISH) ?: LANGUAGE_ENGLISH
    }

    /**
     * Set and apply a new language (triggers Activity recreation)
     * Does NOT mark selection as complete - call markLanguageSelectionComplete() for that
     * @param languageCode The language code (en, ar, hi, in)
     */
    fun setLanguage(languageCode: String) {
        if (languageCode !in SUPPORTED_LANGUAGES) {
            android.util.Log.w("LanguageManager", "Unsupported language: $languageCode")
            return
        }

        prefs.edit {
            putString(KEY_SELECTED_LANGUAGE, languageCode)
        }

        // Apply the locale using AppCompat per-app language API
        applyLocale(languageCode)
    }

    /**
     * Save language preference without applying (for preview purposes).
     * Use this when you want to preview language changes without Activity recreation.
     * Call applyLanguage() later to actually apply the change.
     */
    fun saveLanguagePreference(languageCode: String) {
        if (languageCode !in SUPPORTED_LANGUAGES) {
            return
        }
        prefs.edit {
            putString(KEY_SELECTED_LANGUAGE, languageCode)
        }
    }

    /**
     * Apply the currently saved language preference.
     * This triggers Activity recreation on Android 12 and below.
     */
    fun applyLanguage() {
        val savedLanguage = prefs.getString(KEY_SELECTED_LANGUAGE, LANGUAGE_ENGLISH) ?: LANGUAGE_ENGLISH
        applyLocale(savedLanguage)
    }

    /**
     * Get a localized string for a specific locale (for preview without Activity recreation).
     * Creates a temporary context with the specified locale to load the string resource.
     */
    fun getLocalizedString(@StringRes resId: Int, languageCode: String): String {
        val locale = Locale.forLanguageTag(languageCode)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        val localizedContext = context.createConfigurationContext(config)
        return localizedContext.getString(resId)
    }

    /**
     * Apply locale using AndroidX AppCompat per-app language API
     * This is the recommended approach for Android 13+ and backwards compatible
     */
    private fun applyLocale(languageCode: String) {
        val localeList = LocaleListCompat.forLanguageTags(languageCode)
        AppCompatDelegate.setApplicationLocales(localeList)
    }

    /**
     * Initialize language on app startup.
     *
     * NOTE: With autoStoreLocales=true in AndroidManifest.xml, AppCompat automatically
     * restores the saved locale during Activity creation. This method is kept for
     * compatibility but is a no-op when autoStoreLocales is enabled.
     *
     * Only call this if you've set autoStoreLocales=false and handle storage manually.
     */
    fun initializeLanguage() {
        // With autoStoreLocales=true, AppCompat handles locale restoration automatically.
        // No manual initialization needed - the locale is applied before Activity.onCreate()
        // completes on Android 12 and below, or via system API on Android 13+.
    }

    /**
     * Set flag indicating Activity will recreate due to locale change.
     * Call this BEFORE applying locale change.
     */
    fun setPendingLocaleRecreation() {
        prefs.edit { putBoolean(KEY_PENDING_LOCALE_RECREATION, true) }
    }

    /**
     * Check if Activity was recreated due to locale change.
     */
    fun isPendingLocaleRecreation(): Boolean {
        return prefs.getBoolean(KEY_PENDING_LOCALE_RECREATION, false)
    }

    /**
     * Clear the pending locale recreation flag.
     * Call this after handling the recreation.
     */
    fun clearPendingLocaleRecreation() {
        prefs.edit { putBoolean(KEY_PENDING_LOCALE_RECREATION, false) }
    }

    /**
     * Get display name for a language code
     */
    fun getLanguageDisplayName(languageCode: String): String {
        return when (languageCode) {
            LANGUAGE_ENGLISH -> "English"
            LANGUAGE_ARABIC -> "العربية"
            LANGUAGE_HINDI -> "हिन्दी"
            LANGUAGE_INDONESIAN -> "Bahasa Indonesia"
            else -> languageCode
        }
    }

    /**
     * Get flag emoji for a language code
     */
    fun getLanguageFlag(languageCode: String): String {
        return when (languageCode) {
            LANGUAGE_ENGLISH -> "🇺🇸"
            LANGUAGE_ARABIC -> "🇸🇦"
            LANGUAGE_HINDI -> "🇮🇳"
            LANGUAGE_INDONESIAN -> "🇮🇩"
            else -> "🌐"
        }
    }

    /**
     * Check if current language is RTL (Right-to-Left)
     */
    fun isRtl(): Boolean {
        return getSelectedLanguage() == LANGUAGE_ARABIC
    }
}

/**
 * Data class representing a supported language
 */
data class SupportedLanguage(
    val code: String,
    val displayName: String,
    val flag: String,
    val isRtl: Boolean = false
)

/**
 * Get all supported languages as a list
 */
fun LanguageManager.Companion.getAllLanguages(): List<SupportedLanguage> {
    return listOf(
        SupportedLanguage(LANGUAGE_ENGLISH, "English", "🇺🇸"),
        SupportedLanguage(LANGUAGE_ARABIC, "العربية", "🇸🇦", isRtl = true),
        SupportedLanguage(LANGUAGE_HINDI, "हिन्दी", "🇮🇳"),
        SupportedLanguage(LANGUAGE_INDONESIAN, "Bahasa Indonesia", "🇮🇩")
    )
}
