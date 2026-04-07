package com.videomaker.aimusic.modules.language.domain.usecase

import com.videomaker.aimusic.core.data.local.LanguageManager
import com.videomaker.aimusic.core.data.local.RegionProvider

/**
 * Returns true if user still needs to go through LanguageSelectionActivity.
 * Returns true only when language was not confirmed yet.
 */
class CheckLanguageSelectedUseCase(
    private val languageManager: LanguageManager
) {
    operator fun invoke(): Boolean = !languageManager.isLanguageSelectionComplete()
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
 * Clears template cache so fresh i18n data is fetched with new language.
 */
class SaveLanguagePreferenceUseCase(
    private val languageManager: LanguageManager,
    private val regionProvider: RegionProvider,
    private val templateRepository: com.videomaker.aimusic.domain.repository.TemplateRepository
) {
    suspend operator fun invoke(languageCode: String) {
        languageManager.saveLanguagePreference(languageCode)
        // Clear region cache - will be re-derived from new language on next access
        regionProvider.invalidate()
        // Clear template cache - force re-fetch with new language
        templateRepository.clearCache()
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
