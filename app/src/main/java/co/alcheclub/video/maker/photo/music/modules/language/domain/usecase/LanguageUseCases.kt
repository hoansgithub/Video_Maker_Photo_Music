package co.alcheclub.video.maker.photo.music.modules.language.domain.usecase

import co.alcheclub.video.maker.photo.music.core.data.local.LanguageManager

/**
 * Use case to check if language selection should be shown
 * Returns true if user hasn't completed language selection yet
 */
class CheckLanguageSelectedUseCase(
    private val languageManager: LanguageManager
) {
    operator fun invoke(): Result<Boolean> {
        return try {
            // Show language selection if user hasn't completed it
            Result.success(!languageManager.isLanguageSelectionComplete())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

/**
 * Use case to mark language selection as complete
 */
class CompleteLanguageSelectionUseCase(
    private val languageManager: LanguageManager
) {
    operator fun invoke() {
        languageManager.markLanguageSelectionComplete()
    }
}

/**
 * Use case to get the current selected language
 */
class GetSelectedLanguageUseCase(
    private val languageManager: LanguageManager
) {
    operator fun invoke(): String {
        return languageManager.getSelectedLanguage()
    }
}

/**
 * Use case to set and apply the app language (triggers Activity recreation)
 */
class SetLanguageUseCase(
    private val languageManager: LanguageManager
) {
    operator fun invoke(languageCode: String) {
        languageManager.setLanguage(languageCode)
    }
}

/**
 * Use case to save language preference without applying (for preview)
 */
class SaveLanguagePreferenceUseCase(
    private val languageManager: LanguageManager
) {
    operator fun invoke(languageCode: String) {
        languageManager.saveLanguagePreference(languageCode)
    }
}

/**
 * Use case to apply the saved language preference
 */
class ApplyLanguageUseCase(
    private val languageManager: LanguageManager
) {
    operator fun invoke() {
        languageManager.applyLanguage()
    }
}

/**
 * Use case to initialize language on app startup
 */
class InitializeLanguageUseCase(
    private val languageManager: LanguageManager
) {
    operator fun invoke() {
        languageManager.initializeLanguage()
    }
}
