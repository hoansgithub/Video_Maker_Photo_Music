package com.videomaker.aimusic.navigation

import androidx.compose.runtime.Immutable
import androidx.navigation3.runtime.NavKey
import com.videomaker.aimusic.domain.model.AspectRatio
import kotlinx.serialization.Serializable

@Serializable
enum class SearchSection {
    TEMPLATES,
    MUSIC
}

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

    /** Routing signal: launch FeatureSelectionActivity */
    @Serializable
    data object FeatureSelection : AppRoute

    // ============================================
    // HOME LEVEL ROUTES
    // ============================================

    /**
     * Home screen with optional initial tab
     * @param initialTab Tab index: 0=Gallery, 1=Songs, 2=My Videos
     */
    @Serializable
    data class Home(val initialTab: Int = 0, val initialSongId: Long = -1L) : AppRoute

    @Serializable
    data class UnifiedSearch(
        val initialSection: SearchSection = SearchSection.TEMPLATES
    ) : AppRoute

    /**
     * Suggested songs list: paginated browsing of all suggested songs
     */
    @Serializable
    data object SuggestedSongsList : AppRoute

    /**
     * Weekly ranking list: paginated browsing of top 100 weekly ranking songs
     */
    @Serializable
    data object WeeklyRankingList : AppRoute

    // ============================================
    // CREATE FLOW ROUTES
    // ============================================

    /**
     * @param projectId null = create new project; non-null = add to existing
     * @param overrideSongId When >= 0, TemplatePreviewer will play this song instead of the
     *   template's embedded song. Used by the song → image picker → previewer flow.
     * @param aspectRatio User's selected aspect ratio from template previewer.
     *   null = use template's default or fallback to RATIO_9_16.
     */
    @Serializable
    data class AssetPicker(
        val projectId: String? = null,
        val templateId: String? = null,
        val overrideSongId: Long = -1L,
        val aspectRatio: AspectRatio? = null,
        val sourceLocation: String? = null
    ) : AppRoute

    /**
     * Editor screen route.
     *
     * For existing projects: pass projectId only
     * For new projects from template: pass initialData only
     * One of projectId or initialData must be non-null.
     */
    @Serializable
    data class Editor(
        val projectId: String? = null,
        val initialData: com.videomaker.aimusic.domain.model.EditorInitialData? = null
    ) : AppRoute

    @Serializable
    data class Preview(val projectId: String) : AppRoute

    @Serializable
    data class Export(
        val projectId: String,
        val quality: com.videomaker.aimusic.domain.model.VideoQuality = com.videomaker.aimusic.domain.model.VideoQuality.DEFAULT
    ) : AppRoute

    // ============================================
    // TEMPLATE FLOW ROUTES
    // ============================================

    /**
     * Template list: paginated browsing with vibe tag filtering.
     *
     * @param selectedVibeTagId null = "All" templates, non-null = filter by specific tag
     */
    @Serializable
    data class TemplateList(val selectedVibeTagId: String? = null) : AppRoute

    /**
     * Template preview: apply a template to user-selected images OR sample images.
     *
     * @param templateId ID of the template to open first. Empty string = open at top-ranked template.
     * @param imageUris User-selected images. Empty list = show sample/placeholder images for browsing.
     * @param overrideSongId When >= 0, plays this song across all templates instead of each
     *   template's own song. -1 = use template's embedded song (default behaviour).
     */
    @Serializable
    data class TemplatePreviewer(
        val templateId: String,
        val imageUris: List<String> = emptyList(), // Empty = browse mode with sample images
        val overrideSongId: Long = -1L,
        val sourceLocation: String? = null
    ) : AppRoute

    // ============================================
    // SETTINGS ROUTES
    // ============================================

    @Serializable
    data object Settings : AppRoute

    @Serializable
    data object LanguageSettings : AppRoute


    @Serializable
    data object WidgetScreen : AppRoute

    @Serializable
    data object ConfirmUninstall : AppRoute
}
