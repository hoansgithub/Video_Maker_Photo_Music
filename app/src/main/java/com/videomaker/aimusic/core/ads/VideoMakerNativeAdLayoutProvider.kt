package com.videomaker.aimusic.core.ads

import co.alcheclub.lib.acccore.ads.layout.NativeAdLayoutProvider
import co.alcheclub.lib.acccore.ads.layout.NativeAdViewIds
import com.videomaker.aimusic.R

/**
 * Video Maker app's native ad layout provider.
 *
 * Maps layout names from Remote Config to app's R.layout resources.
 * Provides view IDs for native ad components (media, headline, CTA, etc.).
 *
 * Layout naming convention:
 * - `native_small_clean`: Small horizontal layout (200dp height, clean CTA)
 * - `native_big_clean`: Large vertical layout (304dp height, clean CTA)
 *
 * Remote Config example:
 * ```json
 * {
 *   "native_home_1": {
 *     "enabled": true,
 *     "type": "native",
 *     "units": [...],
 *     "extras": {
 *       "layout": "native_small_clean"
 *     }
 *   }
 * }
 * ```
 *
 * Usage in Application:
 * ```kotlin
 * val adMobMediator = ACCDI.get<AdMobMediator>()
 * val layoutProvider = ACCDI.get<VideoMakerNativeAdLayoutProvider>()
 * adMobMediator.setNativeAdLayoutProvider(layoutProvider)
 * ```
 */
class VideoMakerNativeAdLayoutProvider : NativeAdLayoutProvider {

    /**
     * Map layout name to R.layout resource.
     *
     * @param layoutName Layout name from Remote Config (e.g., "native_small_clean")
     * @return Layout resource ID, or null if layout not found
     */
    override fun getLayoutResource(layoutName: String): Int? {
        return when (layoutName) {
            "native_small_clean" -> R.layout.native_small_clean
            "native_small_bait" -> R.layout.native_small_bait
            "native_small_bait_reversed" -> R.layout.native_small_bait_reversed
            "native_small_row" -> R.layout.native_small_row
            "native_big_clean" -> R.layout.native_big_clean
            "native_big_bait" -> R.layout.native_big_bait
            "native_big_bait_reversed" -> R.layout.native_big_bait_reversed
            "native_big_row" -> R.layout.native_big_row
            "native_full_screen_clean" -> R.layout.native_full_screen_clean
            "native_full_screen_bait" -> R.layout.native_full_screen_bait
            "native_showcase_item" -> R.layout.native_showcase_item
            "native_project_card" -> R.layout.native_project_card
            else -> {
                android.util.Log.w("NativeAdLayoutProvider", "⚠️ Unknown layout: $layoutName")
                null
            }
        }
    }

    /**
     * Provide view IDs for native ad components.
     *
     * ALL layouts MUST use these exact view IDs.
     * Optional views (advertiser, store, choicesContainer) can be null.
     *
     * @return View IDs for native ad components
     */
    override fun getViewIds(): NativeAdViewIds {
        return NativeAdViewIds(
            media = R.id.ad_media,              // Required: MediaView
            headline = R.id.ad_headline,        // Required: Headline text
            body = R.id.ad_body,                // Required: Body text
            callToAction = R.id.ad_call_to_action, // Required: CTA button
            icon = R.id.ad_icon,                // Required: App icon
            badge = 0,                          // Not used: Using AdChoices instead
            advertiser = R.id.ad_advertiser,    // Optional: Advertiser name
            store = R.id.ad_store,              // Optional: App store name
            choicesContainer = R.id.ad_choices_container // Required: Ad choices icon (replaces custom badge)
        )
    }
}
