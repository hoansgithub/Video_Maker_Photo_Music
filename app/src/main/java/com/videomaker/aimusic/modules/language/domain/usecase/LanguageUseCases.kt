package com.videomaker.aimusic.modules.language.domain.usecase

import com.videomaker.aimusic.core.data.local.LanguageManager
import com.videomaker.aimusic.core.data.local.RegionProvider

/**
 * Returns true if user still needs to go through LanguageSelectionActivity.
 * This covers two cases:
 * - Language not yet selected (first visit)
 * - Language selected but genre survey not yet completed (interrupted mid-flow)
 */
class CheckLanguageSelectedUseCase(
    private val languageManager: LanguageManager
) {
    operator fun invoke(): Boolean =
        !languageManager.isLanguageSelectionComplete() || languageManager.isGenreSelectionPending()
}

/**
 * Mark language selection as complete (user pressed Continue).
 */
class CompleteLanguageSelectionUseCase(
    private val languageManager: LanguageManager
) {
    operator fun invoke() {
        languageManager.markLanguageSelectionComplete()
    }
}

/**
 * Get the currently selected language code.
 */
class GetSelectedLanguageUseCase(
    private val languageManager: LanguageManager
) {
    operator fun invoke(): String = languageManager.getSelectedLanguage()
}

/**
 * Save language preference without applying — no Activity recreation.
 * Also invalidates RegionProvider cache so region is re-derived from new language.
 */
class SaveLanguagePreferenceUseCase(
    private val languageManager: LanguageManager,
    private val regionProvider: RegionProvider
) {
    operator fun invoke(languageCode: String) {
        languageManager.saveLanguagePreference(languageCode)
        // Clear region cache - will be re-derived from new language on next access
        regionProvider.invalidate()
    }
}

/**
 * Apply the saved language preference via AppCompat.
 * Call right before navigating to the next Activity so it starts with the new locale.
 */
class ApplyLanguageUseCase(
    private val languageManager: LanguageManager
) {
    operator fun invoke() {
        languageManager.applyLanguage()
    }
}