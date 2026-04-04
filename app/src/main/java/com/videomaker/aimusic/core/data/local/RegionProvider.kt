package com.videomaker.aimusic.core.data.local

import java.util.Locale

/**
 * Single source of truth for the user's region code (ISO 3166-1 alpha-2, lowercase).
 *
 * Resolution order:
 * 1. In-memory cache (set once per process)
 * 2. Persisted value in PreferencesManager (set on first run)
 * 3. Derived from selected language (unambiguous mappings)
 * 4. Device locale country (for ambiguous languages: en, es, ar)
 * 5. Hardcoded fallback "us"
 *
 * Used by SongRepositoryImpl and TemplateRepositoryImpl to filter and prioritize content:
 * - Priority 1: Content targeting user's region (target_regions = ["us"])
 * - Priority 2: Explicit global content (target_regions = ["all"])
 * - Priority 3: Implicit global content (target_regions = [] empty)
 */
class RegionProvider(
    private val languageManager: LanguageManager,
    private val preferencesManager: PreferencesManager
) {
    @Volatile
    private var cached: String? = null

    /**
     * Returns the resolved region code. Safe to call from any thread.
     * Result is cached in memory after first call.
     */
    fun getRegionCode(): String {
        cached?.let { return it }
        val region = preferencesManager.getUserRegion()
            ?.takeIf { it.isNotBlank() }  // Treat empty string as null
            ?: deriveFromLanguage().also { preferencesManager.setUserRegion(it) }
        cached = region
        return region
    }

    /**
     * Force-clears cached + persisted region (e.g. user changes language in Settings).
     * Next call to getRegionCode() will re-derive.
     */
    fun invalidate() {
        cached = null
        preferencesManager.setUserRegion("")
    }

    private fun deriveFromLanguage(): String =
        when (languageManager.getSelectedLanguage()) {
            "hi"  -> "in"   // Hindi        → India
            "id"  -> "id"   // Indonesian   → Indonesia
            "tr"  -> "tr"   // Turkish      → Turkey
            "fil" -> "ph"   // Filipino     → Philippines
            "pt"  -> "br"   // Portuguese   → Brazil (largest market)
            else  ->        // en, es, ar   → use device locale country
                Locale.getDefault().country.lowercase().ifBlank { "us" }
        }
}