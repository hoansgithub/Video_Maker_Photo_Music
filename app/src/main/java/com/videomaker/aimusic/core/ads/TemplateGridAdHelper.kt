package com.videomaker.aimusic.core.ads

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import co.alcheclub.lib.acccore.ads.loader.AdsLoaderService
import com.videomaker.aimusic.core.constants.AdPlacement
import org.koin.compose.koinInject

/**
 * Template Grid Tap Ad Helper
 *
 * Reusable logic for showing interstitial ads when user taps template cards.
 * Used across Gallery, Search, Export, and Projects screens.
 *
 * ## Usage in Screen:
 * ```kotlin
 * HandleTemplateNavigation(
 *     templateId = event.templateId,
 *     shouldShowAd = event.shouldShowAd,
 *     onPreloadNext = { viewModel.preloadTemplateGridAd() },
 *     onNavigate = { templateId -> navigateToTemplate(templateId) }
 * )
 * ```
 */

/**
 * Handles template navigation with optional interstitial ad.
 * Shows ad if ready and frequency cap allows, otherwise navigates immediately.
 *
 * @param templateId The template ID to navigate to
 * @param shouldShowAd Whether ad is ready and should be shown
 * @param placement The ad placement to show (default: INTERSTITIAL_TEMPLATE_GRID_TAP)
 * @param onPreloadNext Callback to preload next ad after current one shows
 * @param onNavigate Navigation callback with template ID
 */
@Composable
fun HandleTemplateNavigation(
    templateId: String,
    shouldShowAd: Boolean,
    placement: String = AdPlacement.INTERSTITIAL_TEMPLATE_GRID_TAP,
    onPreloadNext: () -> Unit,
    onNavigate: (String) -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val adsLoaderService = koinInject<AdsLoaderService>()

    LaunchedEffect(templateId, shouldShowAd) {
        if (shouldShowAd && activity != null) {
            // Show interstitial ad
            InterstitialAdHelperExt.showInterstitial(
                adsLoaderService = adsLoaderService,
                activity = activity,
                placement = placement,
                action = {
                    // Navigate after ad closes
                    onNavigate(templateId)
                },
                onShown = {
                    // Preload next ad after current one shows (Drama app pattern)
                    onPreloadNext()
                },
                bypassFrequencyCap = true,   // Show every time, ignore frequency cap
                showLoadingOverlay = false   // Background preloaded, no overlay
            )
        } else {
            // Ad not ready or frequency cap active - navigate immediately
            onNavigate(templateId)
        }
    }
}

/**
 * Interface for ViewModels that support template grid tap ads.
 * Implement this in any ViewModel that navigates to template previewer.
 */
interface TemplateGridAdViewModel {
    /**
     * Preload template grid tap interstitial ad.
     * Called after ad is shown to prepare the next one.
     */
    fun preloadTemplateGridAd()
}
