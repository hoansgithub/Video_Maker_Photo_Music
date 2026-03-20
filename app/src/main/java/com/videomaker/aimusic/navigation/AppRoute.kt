package com.videomaker.aimusic.navigation

import androidx.compose.runtime.Immutable
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Navigation Routes for MainActivity
 *
 * All routes for the main app content using Navigation 3 (1.0.0 stable).
 *
 * Sealed interface (not class) — Navigation 3 uses kotlinx.serialization for
 * back stack persistence, so @Parcelize / Parcelable are not required.
 *
 * Routes are also used by RootViewModel as routing signals for Activity-level
 * navigation (e.g. Onboarding → OnboardingActivity, Home → MainActivity).
 *
 * NavKey: Required interface for Navigation 3 back stack management.
 */
@Immutable
sealed interface AppRoute : NavKey {

    // ============================================
    // ROOT LEVEL ROUTES (RootViewModel routing signals)
    // Not rendered inside NavDisplay — used to launch the right Activity
    // ============================================

    /** Routing signal: launch LanguageSelectionActivity */
    @Serializable
    data object LanguageSelection : AppRoute

    /** Routing signal: launch OnboardingActivity */
    @Serializable
    data object Onboarding : AppRoute

    // ============================================
    // HOME LEVEL ROUTES
    // ============================================

    @Serializable
    data object Home : AppRoute

    @Serializable
    data object Search : AppRoute

    @Serializable
    data object SongSearch : AppRoute

    // ============================================
    // CREATE FLOW ROUTES
    // ============================================

    /**
     * @param projectId null = create new project; non-null = add to existing
     * @param overrideSongId When >= 0, TemplatePreviewer will play this song instead of the
     *   template's embedded song. Used by the song → image picker → previewer flow.
     */
    @Serializable
    data class AssetPicker(
        val projectId: String? = null,
        val templateId: String? = null,
        val overrideSongId: Long = -1L
    ) : AppRoute

    @Serializable
    data class Editor(val projectId: String) : AppRoute

    @Serializable
    data class Preview(val projectId: String) : AppRoute

    @Serializable
    data class Export(val projectId: String) : AppRoute

    // ============================================
    // TEMPLATE FLOW ROUTES
    // ============================================

    /**
     * Template preview: apply a template to user-selected images.
     *
     * @param templateId ID of the template to open first. Empty string = open at top-ranked template.
     * @param overrideSongId When >= 0, plays this song across all templates instead of each
     *   template's own song. -1 = use template's embedded song (default behaviour).
     */
    @Serializable
    data class TemplatePreviewer(
        val templateId: String,
        val imageUris: List<String>,
        val overrideSongId: Long = -1L
    ) : AppRoute

    // ============================================
    // PROJECTS ROUTES
    // ============================================

    @Serializable
    data object Projects : AppRoute

    // ============================================
    // SETTINGS ROUTES
    // ============================================

    @Serializable
    data object Settings : AppRoute
}
