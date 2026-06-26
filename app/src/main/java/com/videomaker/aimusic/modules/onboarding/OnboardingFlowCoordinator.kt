package com.videomaker.aimusic.modules.onboarding

import co.alcheclub.lib.acccore.ads.loader.AdsLoaderService
import co.alcheclub.lib.acccore.remoteconfig.RemoteConfig
import com.videomaker.aimusic.MainActivity
import com.videomaker.aimusic.VideoMakerApplication
import com.videomaker.aimusic.core.ads.InterstitialAdHelperExt
import com.videomaker.aimusic.core.constants.AdPlacement
import com.videomaker.aimusic.core.constants.RemoteConfigKeys
import com.videomaker.aimusic.core.data.local.PreferencesManager
import com.videomaker.aimusic.core.ui.BaseOnboardingActivity
import com.videomaker.aimusic.modules.featureselection.FeatureSelectionActivity
import com.videomaker.aimusic.modules.featureselection.PersonalizingActivity
import com.videomaker.aimusic.modules.genretemplate.ContentExclusiveActivity
import com.videomaker.aimusic.modules.genretemplate.GenreSelectionActivity
import com.videomaker.aimusic.modules.genretemplate.MediaPrivacyActivity
import com.videomaker.aimusic.modules.genretemplate.TemplatePickActivity
import com.videomaker.aimusic.modules.language.LanguageSelectionActivity
import com.videomaker.aimusic.modules.onboardingsurvey.SurveyAiLevelActivity
import com.videomaker.aimusic.modules.onboardingsurvey.SurveyFeatureActivity
import com.videomaker.aimusic.modules.onboardingsurvey.SurveyPlatformActivity

/**
 * Central coordinator for the flattened onboarding flow.
 *
 * Given a step, returns the next **enabled** step and its Activity class.
 * Each step maps 1:1 to a single Activity — no internal step-switching or pagers.
 *
 * Most steps are gated via Remote Config (missing keys default to enabled).
 * [FULLSCREEN_AD] is gated by its ad placement `enabled` flag on Firebase and
 * dynamically positioned after the welcome page specified by `inject_after`
 * (defaults to page 2).
 * [INTERSTITIAL_ONBOARDING] is not a step — it fires as a transition ad when
 * navigating from the ad injection point (FULLSCREEN_AD, or the injection-point
 * welcome page when FULLSCREEN_AD is disabled).
 */
class OnboardingFlowCoordinator(
    private val remoteConfig: RemoteConfig,
    private val preferencesManager: PreferencesManager,
    private val adsLoaderService: AdsLoaderService,
) {

    /**
     * Maps each [OnboardingStep] to its Remote Config gate key.
     * Steps not in this map use alternative gating (e.g. ad placement config).
     */
    private val stepGateKeys = mapOf(
        OnboardingStep.LANGUAGE_SELECTION to RemoteConfigKeys.ONBOARDING_LANGUAGE_SELECTION_ENABLED,
        OnboardingStep.SURVEY_FEATURE to RemoteConfigKeys.ONBOARDING_FEATURE_SELECTION_ENABLED,
        OnboardingStep.SURVEY_PLATFORM to RemoteConfigKeys.ONBOARDING_PLATFORM_SELECTION_ENABLED,
        OnboardingStep.SURVEY_AI_LEVEL to RemoteConfigKeys.ONBOARDING_AI_LEVEL_ENABLED,
        OnboardingStep.WELCOME_PAGE_1 to RemoteConfigKeys.ONBOARDING_WELCOME_PAGE_1_ENABLED,
        OnboardingStep.WELCOME_PAGE_2 to RemoteConfigKeys.ONBOARDING_WELCOME_PAGE_2_ENABLED,
        OnboardingStep.WELCOME_PAGE_3 to RemoteConfigKeys.ONBOARDING_WELCOME_PAGE_3_ENABLED,
        OnboardingStep.GENRE_SELECTION to RemoteConfigKeys.ONBOARDING_GENRE_SELECTION_ENABLED,
        OnboardingStep.TEMPLATE_PICK to RemoteConfigKeys.ONBOARDING_TEMPLATE_PICK_ENABLED,
        OnboardingStep.CONTENT_EXCLUSIVE to RemoteConfigKeys.ONBOARDING_CONTENT_EXCLUSIVE_ENABLED,
        OnboardingStep.MEDIA_PRIVACY to RemoteConfigKeys.ONBOARDING_MEDIA_PRIVACY_ENABLED,
        OnboardingStep.FEATURE_SELECTION to RemoteConfigKeys.ONBOARDING_FEATURE_SELECT_ENABLED,
        OnboardingStep.PERSONALIZING to RemoteConfigKeys.ONBOARDING_PERSONALIZING_ENABLED,
    )

    /**
     * Full ordered list filtered by Remote Config gates and ad placement config.
     *
     * [FULLSCREEN_AD] is excluded from the base enum order and dynamically inserted
     * after the welcome page specified by the placement's `inject_after` extra
     * (1 = after page 1, 2 = after page 2, 3 = after page 3; default 2).
     */
    fun enabledSteps(): List<OnboardingStep> {
        val steps = OnboardingStep.entries.filter { step ->
            when (step) {
                // Handled separately — inserted at the dynamic injection point below
                OnboardingStep.FULLSCREEN_AD -> false
                else -> {
                    val key = stepGateKeys[step] ?: return@filter true
                    stepEnabled(key)
                }
            }
        }.toMutableList()

        // Insert FULLSCREEN_AD after the injection-point welcome page if enabled
        if (isAdPlacementEnabled(AdPlacement.NATIVE_ONBOARDING_FULLSCREEN)) {
            val insertIndex = steps.indexOf(adInjectionPoint())
            if (insertIndex >= 0) {
                steps.add(insertIndex + 1, OnboardingStep.FULLSCREEN_AD)
            }
        }

        return steps
    }

    /** Next enabled step after [current], or null if [current] is last. */
    fun nextStep(current: OnboardingStep): OnboardingStep? {
        val steps = enabledSteps()
        val index = steps.indexOf(current)
        if (index < 0) return null
        return steps.getOrNull(index + 1)
    }

    /** Activity class for a given step. */
    fun activityClass(step: OnboardingStep): Class<out BaseOnboardingActivity> = when (step) {
        OnboardingStep.LANGUAGE_SELECTION -> LanguageSelectionActivity::class.java
        OnboardingStep.SURVEY_FEATURE -> SurveyFeatureActivity::class.java
        OnboardingStep.SURVEY_PLATFORM -> SurveyPlatformActivity::class.java
        OnboardingStep.SURVEY_AI_LEVEL -> SurveyAiLevelActivity::class.java
        OnboardingStep.WELCOME_PAGE_1 -> WelcomePage1Activity::class.java
        OnboardingStep.WELCOME_PAGE_2 -> WelcomePage2Activity::class.java
        OnboardingStep.FULLSCREEN_AD -> FullscreenAdActivity::class.java
        OnboardingStep.WELCOME_PAGE_3 -> WelcomePage3Activity::class.java
        OnboardingStep.GENRE_SELECTION -> GenreSelectionActivity::class.java
        OnboardingStep.TEMPLATE_PICK -> TemplatePickActivity::class.java
        OnboardingStep.CONTENT_EXCLUSIVE -> ContentExclusiveActivity::class.java
        OnboardingStep.MEDIA_PRIVACY -> MediaPrivacyActivity::class.java
        OnboardingStep.FEATURE_SELECTION -> FeatureSelectionActivity::class.java
        OnboardingStep.PERSONALIZING -> PersonalizingActivity::class.java
    }

    /** Native ad placements for a step (for 1-step-ahead preloading). */
    fun adPlacements(step: OnboardingStep): List<String> = when (step) {
        OnboardingStep.LANGUAGE_SELECTION -> listOf(
            AdPlacement.NATIVE_ONBOARDING_LANGUAGE,
            AdPlacement.NATIVE_ONBOARDING_LANGUAGE_ALT,
        )
        OnboardingStep.SURVEY_FEATURE -> listOf(
            AdPlacement.NATIVE_ONBOARDING_SELECT,
            AdPlacement.NATIVE_ONBOARDING_SELECT_ALT,
        )
        OnboardingStep.SURVEY_PLATFORM -> listOf(
            AdPlacement.NATIVE_ONBOARDING_SOCIAL,
        )
        OnboardingStep.SURVEY_AI_LEVEL -> listOf(
            AdPlacement.NATIVE_ONBOARDING_AI_LEVEL,
            AdPlacement.NATIVE_ONBOARDING_AI_LEVEL_ALT,
        )
        OnboardingStep.WELCOME_PAGE_1 -> listOf(
            AdPlacement.NATIVE_ONBOARDING_PAGE1,
        )
        OnboardingStep.WELCOME_PAGE_2 -> listOf(
            AdPlacement.NATIVE_ONBOARDING_PAGE2,
        )
        OnboardingStep.FULLSCREEN_AD -> listOf(
            AdPlacement.NATIVE_ONBOARDING_FULLSCREEN,
        )
        OnboardingStep.WELCOME_PAGE_3 -> listOf(
            AdPlacement.NATIVE_ONBOARDING_PAGE3,
        )
        OnboardingStep.GENRE_SELECTION -> listOf(
            AdPlacement.NATIVE_ONBOARDING_SELECT_MUSIC,
        )
        OnboardingStep.TEMPLATE_PICK -> listOf(
            AdPlacement.NATIVE_ONBOARDING_SELECT_TPT,
        )
        OnboardingStep.CONTENT_EXCLUSIVE -> listOf(
            AdPlacement.NATIVE_ONBOARDING_CONTENT_EXCLUSIVE,
            AdPlacement.NATIVE_ONBOARDING_CONTENT_EXCLUSIVE_ALT,
        )
        OnboardingStep.MEDIA_PRIVACY -> listOf(
            AdPlacement.NATIVE_ONBOARDING_MEDIA_PRIVACY,
            AdPlacement.NATIVE_ONBOARDING_MEDIA_PRIVACY_ALT,
        )
        OnboardingStep.FEATURE_SELECTION -> listOf(
            AdPlacement.NATIVE_ONBOARDING_FEATURE_SELECTION,
            AdPlacement.NATIVE_ONBOARDING_FEATURE_SELECTION_ALT,
        )
        OnboardingStep.PERSONALIZING -> listOf(
            AdPlacement.NATIVE_ONBOARDING_PERSONALIZING,
        )
    }

    /**
     * Interstitial placements to preload alongside a step's native ads.
     * Preloaded early so the interstitial is ready when the trigger fires.
     *
     * Preloads INTERSTITIAL_ONBOARDING when visiting the injection-point welcome page
     * or FULLSCREEN_AD, so it's ready regardless of whether FULLSCREEN_AD is enabled.
     */
    private fun interstitialPlacements(step: OnboardingStep): List<String> = when {
        step == OnboardingStep.FULLSCREEN_AD || step == adInjectionPoint() ->
            listOf(AdPlacement.INTERSTITIAL_ONBOARDING)
        step == OnboardingStep.PERSONALIZING ->
            listOf(AdPlacement.INTERSTITIAL_ONBOARDING_COMPLETE)
        else -> emptyList()
    }

    /** Resume step for welcome-back screen. */
    fun resumeStep(): OnboardingStep? {
        if (preferencesManager.isOnboardingComplete()) return null

        val welcomeDone = preferencesManager.isOnboardingWelcomeComplete()
        val featureDone = preferencesManager.isFeatureSelectionComplete()

        val steps = enabledSteps()
        return when {
            !welcomeDone -> {
                val firstSurvey = steps.firstOrNull {
                    it == OnboardingStep.SURVEY_FEATURE ||
                    it == OnboardingStep.SURVEY_PLATFORM ||
                    it == OnboardingStep.SURVEY_AI_LEVEL
                }
                firstSurvey ?: steps.firstOrNull { it == OnboardingStep.WELCOME_PAGE_1 }
            }
            !featureDone -> {
                val firstGenre = steps.firstOrNull {
                    it == OnboardingStep.GENRE_SELECTION ||
                    it == OnboardingStep.TEMPLATE_PICK ||
                    it == OnboardingStep.CONTENT_EXCLUSIVE ||
                    it == OnboardingStep.MEDIA_PRIVACY
                }
                firstGenre ?: steps.firstOrNull { it == OnboardingStep.FEATURE_SELECTION }
            }
            else -> null
        }
    }

    /**
     * Preload all ad placements (native + interstitial) for a given [step].
     * Primary native loads immediately; ALT natives delayed 1 s to stagger requests.
     */
    fun preloadAdsForStep(step: OnboardingStep) {
        adPlacements(step).forEachIndexed { index, placement ->
            if (index == 0) {
                VideoMakerApplication.preloadNativeAd(placement)
            } else {
                VideoMakerApplication.preloadNativeAdDelayed(placement, 1000L)
            }
        }
        interstitialPlacements(step).forEach {
            VideoMakerApplication.preloadInterstitial(it)
        }
    }

    /**
     * Preload ads for the next enabled step after [current].
     * No-op if [current] is the last enabled step.
     */
    fun preloadAdsForNextStep(current: OnboardingStep) {
        nextStep(current)?.let { preloadAdsForStep(it) }
    }

    /**
     * Navigate from [current] to the next enabled step. If there are no more steps,
     * marks onboarding complete and navigates to [MainActivity].
     *
     * Also preloads ads for the step AFTER the next step (2-ahead).
     *
     * When leaving the ad injection point (FULLSCREEN_AD, or the injection-point
     * welcome page when FULLSCREEN_AD is disabled), shows [INTERSTITIAL_ONBOARDING]
     * as a transition ad before proceeding — if the placement is enabled on Firebase.
     */
    fun navigateToNext(activity: BaseOnboardingActivity, current: OnboardingStep) {
        val next = nextStep(current)
        if (next != null) {
            // Preload next+1 step's ads (1-step-ahead of the destination)
            nextStep(next)?.let { preloadAdsForStep(it) }

            // Show onboarding interstitial as a transition ad when leaving
            // the ad injection point, then navigate on close.
            if (shouldShowOnboardingInterstitial(current)) {
                InterstitialAdHelperExt.showInterstitial(
                    adsLoaderService = adsLoaderService,
                    activity = activity,
                    placement = AdPlacement.INTERSTITIAL_ONBOARDING,
                    action = { activity.navigateForward(activityClass(next)) },
                    onShown = {
                        // Interstitials are single-use: preload fresh for potential re-entry
                        VideoMakerApplication.preloadInterstitial(AdPlacement.INTERSTITIAL_ONBOARDING)
                    },
                    bypassFrequencyCap = true,
                    showLoadingOverlay = false,
                )
            } else {
                activity.navigateForward(activityClass(next))
            }
        } else {
            // Flow complete → save & go home
            preferencesManager.markOnboardingCompleteSync()
            activity.navigateForward(MainActivity::class.java) {
                putExtra(MainActivity.EXTRA_FROM_ONBOARDING, true)
            }
        }
    }

    // ── Private helpers ──────────────────────────────────────────────────

    /**
     * Whether to show INTERSTITIAL_ONBOARDING when navigating away from [current].
     *
     * Fires when leaving FULLSCREEN_AD, or when leaving the injection-point
     * welcome page if FULLSCREEN_AD is not in the enabled steps (so the
     * interstitial still shows at the injection point even when the fullscreen
     * native is off).
     */
    private fun shouldShowOnboardingInterstitial(current: OnboardingStep): Boolean {
        if (!isAdPlacementEnabled(AdPlacement.INTERSTITIAL_ONBOARDING)) return false
        return when (current) {
            OnboardingStep.FULLSCREEN_AD -> true
            else -> OnboardingStep.FULLSCREEN_AD !in enabledSteps()
                    && current == adInjectionPoint()
        }
    }

    /**
     * The welcome page after which FULLSCREEN_AD and the onboarding interstitial
     * are injected. Read from the fullscreen placement's `inject_after` extra
     * on Firebase (1 = after page 1, 2 = after page 2, 3 = after page 3).
     * Defaults to 2 (after WELCOME_PAGE_2).
     */
    private fun adInjectionPoint(): OnboardingStep {
        val config = try {
            adsLoaderService.getPlacementConfig(AdPlacement.NATIVE_ONBOARDING_FULLSCREEN)
        } catch (_: Exception) { null }

        val injectAfterValue = config?.extras?.get("inject_after")
        val injectAfter = when {
            injectAfterValue == null -> 2
            else -> injectAfterValue.toString().trim('"').toIntOrNull() ?: 2
        }.coerceIn(1, 3)

        return when (injectAfter) {
            1 -> OnboardingStep.WELCOME_PAGE_1
            3 -> OnboardingStep.WELCOME_PAGE_3
            else -> OnboardingStep.WELCOME_PAGE_2
        }
    }

    /** Check if an ad placement is enabled via its Firebase placement config. */
    private fun isAdPlacementEnabled(placement: String): Boolean = try {
        adsLoaderService.getPlacementConfig(placement)?.enabled == true
    } catch (_: Exception) {
        false
    }

    /**
     * Firebase RC getBoolean(key) returns false silently for missing keys.
     * Treats an unpublished key as enabled (true).
     */
    private fun stepEnabled(key: String): Boolean =
        if (key in remoteConfig.getAllKeys()) remoteConfig.getBoolean(key, true) else true
}
