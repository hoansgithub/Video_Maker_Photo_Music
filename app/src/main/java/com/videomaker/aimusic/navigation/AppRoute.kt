package com.videomaker.aimusic.navigation

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

/**
 * Navigation Routes for MainActivity
 *
 * All routes for the main app content using Jetpack Navigation Compose.
 * Routes are organized by app flow:
 * - Root: Loading, Onboarding
 * - Home: Main screen with Create / My Projects
 * - Create Flow: Asset picker, Editor, Preview, Export
 * - Projects: Project list
 * - Settings: App settings (About)
 *
 * Note: Language selection is handled by LanguageSelectionActivity (separate Activity)
 *       before the user reaches MainActivity.
 *
 * @Serializable: For Navigation Compose type-safe routing
 * @Parcelize: For Android Parcelable (saved state, process death recovery)
 */
sealed class AppRoute : Parcelable {

    // ============================================
    // ROOT LEVEL ROUTES (App State Machine)
    // ============================================

    /**
     * Loading screen - Initial app loading
     * Shows while initializing app (ads, config, status checks)
     */
    @Parcelize
    @Serializable
    data object Loading : AppRoute()

    /**
     * Language Selection - Route indicator for RootViewActivity
     * Note: This routes to LanguageSelectionActivity (separate Activity)
     */
    @Parcelize
    @Serializable
    data object LanguageSelection : AppRoute()

    /**
     * Onboarding screen - First-time user tutorial
     * Shown after language is selected
     */
    @Parcelize
    @Serializable
    data object Onboarding : AppRoute()

    // ============================================
    // HOME LEVEL ROUTES
    // ============================================

    /**
     * Home screen - Main screen with Create / My Projects buttons
     */
    @Parcelize
    @Serializable
    data object Home : AppRoute()

    // ============================================
    // CREATE FLOW ROUTES
    // ============================================

    /**
     * Asset Picker screen - Select photos/videos
     * @param projectId Optional project ID for editing existing project
     */
    @Parcelize
    @Serializable
    data class AssetPicker(
        val projectId: String? = null
    ) : AppRoute()

    /**
     * Editor screen - Timeline and settings
     * @param projectId The project ID being edited
     */
    @Parcelize
    @Serializable
    data class Editor(
        val projectId: String
    ) : AppRoute()

    /**
     * Preview screen - Preview the video
     * @param projectId The project ID to preview
     */
    @Parcelize
    @Serializable
    data class Preview(
        val projectId: String
    ) : AppRoute()

    /**
     * Export screen - Export progress and share
     * @param projectId The project ID to export
     */
    @Parcelize
    @Serializable
    data class Export(
        val projectId: String
    ) : AppRoute()

    // ============================================
    // PROJECTS ROUTES
    // ============================================

    /**
     * Projects list screen - My Projects
     */
    @Parcelize
    @Serializable
    data object Projects : AppRoute()

    // ============================================
    // SETTINGS ROUTES
    // ============================================

    /**
     * Settings screen - App settings (About only)
     * Note: Language selection removed - handled by LanguageSelectionActivity on first launch
     */
    @Parcelize
    @Serializable
    data object Settings : AppRoute()
}
