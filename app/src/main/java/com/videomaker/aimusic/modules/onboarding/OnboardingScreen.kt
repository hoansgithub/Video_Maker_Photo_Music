package com.videomaker.aimusic.modules.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.alcheclub.lib.acccore.ads.loader.AdsLoaderService
import com.videomaker.aimusic.R
import com.videomaker.aimusic.VideoMakerApplication
import com.videomaker.aimusic.core.analytics.Analytics
import com.videomaker.aimusic.core.constants.AdPlacement
import com.videomaker.aimusic.modules.onboarding.pages.DynamicCarousel
import com.videomaker.aimusic.modules.onboarding.pages.FullscreenAdStep
import com.videomaker.aimusic.modules.onboarding.pages.WelcomePageDynamic
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.distinctUntilChanged
import org.koin.compose.koinInject

@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel,
    contentViewModel: OnboardingContentViewModel,
    onExitRequested: () -> Unit,
    onComplete: () -> Unit,
    adsLoaderService: AdsLoaderService = koinInject()
) {
    val contentState by contentViewModel.contentState.collectAsStateWithLifecycle()

    val pageList = remember(adsLoaderService) {
        buildOnboardingPageList(adsLoaderService)
    }

    // Optimize: preload the onboarding interstitial the moment this screen opens, so it's
    // ready by the time the user swipes to the (later) interstitial ad page and shows instantly.
    LaunchedEffect(Unit) {
        if (pageList.any { it is OnboardingPage.InterstitialAd }) {
            VideoMakerApplication.preloadInterstitial(AdPlacement.INTERSTITIAL_ONBOARDING)
        }
    }

    val coroutineScope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { pageList.size })
    // Track analytics when page changes
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { page ->
                val pageData = pageList[page]
                if (pageData is OnboardingPage.Welcome) {
                    val onboardingStep = pageData.pageIndex + 1
                    Analytics.track(
                        name = "onboarding_$onboardingStep",
                        params = mapOf("onboarding_screen" to "ob$onboardingStep")
                    )
                }
            }
    }

    var hasPreloadedFeatureSelection by remember { mutableStateOf(false) }

    fun preloadFeatureSelectionIfNeeded(triggeredFromPage: Int, action: String) {
        if (!hasPreloadedFeatureSelection) {
            val isNearEnd = triggeredFromPage >= pageList.size - 2
            if (isNearEnd) {
                android.util.Log.d("OnboardingScreen", "User $action from page $triggeredFromPage/${pageList.lastIndex}, preloading Feature Selection ads (primary immediate, ALT delayed 1s)")
                // Primary: immediate
                VideoMakerApplication.preloadNativeAd(AdPlacement.NATIVE_ONBOARDING_FEATURE_SELECTION)
                // ALT: delayed 1s (A/B variant, lower priority)
                VideoMakerApplication.preloadNativeAdDelayed(
                    placement = AdPlacement.NATIVE_ONBOARDING_FEATURE_SELECTION_ALT,
                    delayMs = 1000L
                )
                // Preload interstitial for feature selection "Get started" button
                VideoMakerApplication.preloadInterstitial(AdPlacement.INTERSTITIAL_ONBOARDING_COMPLETE)
                hasPreloadedFeatureSelection = true
            }
        }
    }

    // Track the page the user was on right before the current one, to detect swipe
    // direction (used by the interstitial ad page: close → go back if arrived backwards).
    var lastPageBeforeCurrent by remember { mutableIntStateOf(pagerState.currentPage) }

    LaunchedEffect(pagerState) {
        var previousPage = pagerState.currentPage
        snapshotFlow { pagerState.currentPage }.collect { currentPage ->
            if (previousPage != currentPage && previousPage >= 0) {
                preloadFeatureSelectionIfNeeded(previousPage, "swiped")
                lastPageBeforeCurrent = previousPage
            }
            previousPage = currentPage
        }
    }

    BackHandler {
        if (pagerState.currentPage > 0) {
            coroutineScope.launch {
                pagerState.animateScrollToPage(pagerState.currentPage - 1)
            }
        } else {
            onExitRequested()
        }
    }

    HorizontalPager(
        state = pagerState,
        modifier = Modifier
            .fillMaxSize(),
        beyondViewportPageCount = 1,
        userScrollEnabled = false
    ) { page ->
        val pageData = pageList[page]
        val isLastPage = page == pageList.lastIndex

        when (pageData) {
            is OnboardingPage.FullscreenAd -> {
                FullscreenAdStep(
                    isCurrentPage = page == pagerState.currentPage,
                    onClose = {
                        preloadFeatureSelectionIfNeeded(page, "closed ad")
                        coroutineScope.launch {
                            if (page < pageList.lastIndex) {
                                pagerState.animateScrollToPage(page + 1)
                            } else {
                                onComplete()
                            }
                        }
                    }
                )
            }

            is OnboardingPage.InterstitialAd -> {
                com.videomaker.aimusic.modules.onboarding.pages.OnboardingInterstitialAdStep(
                    isCurrentPage = page == pagerState.currentPage,
                    onClose = {
                        // If the user arrived here by swiping BACK, continue backwards
                        // after closing the ad; otherwise advance forward as usual.
                        val arrivedBySwipingBack = lastPageBeforeCurrent > page
                        coroutineScope.launch {
                            if (arrivedBySwipingBack) {
                                if (page > 0) {
                                    pagerState.animateScrollToPage(page - 1)
                                }
                            } else {
                                preloadFeatureSelectionIfNeeded(page, "closed interstitial")
                                if (page < pageList.lastIndex) {
                                    pagerState.animateScrollToPage(page + 1)
                                } else {
                                    onComplete()
                                }
                            }
                        }
                    }
                )
            }

            is OnboardingPage.Welcome -> {
                val isPage3 = pageData.pageIndex == 2

                if (isPage3) {
                    // Page 3: Dynamic carousel for ALL geos
                    DynamicCarousel(
                        thumbnailUrls = contentState.page3Thumbnails,
                        localFallbackResIds = contentState.page3LocalFallbacks,
                        title = stringResource(R.string.onboarding_india_page3_title),
                        subtitle = stringResource(R.string.onboarding_india_page3_subtitle),
                        ctaText = stringResource(R.string.onboarding_next),
                        onCta = {
                            Analytics.track(
                                name = "onboarding_${pageData.pageIndex + 1}_next",
                                params = emptyMap()
                            )
                            preloadFeatureSelectionIfNeeded(page, "tapped Continue")
                            if (isLastPage) {
                                onComplete()
                            } else {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(page + 1)
                                }
                            }
                        },
                        pageIndex = pageData.pageIndex
                    )
                } else {
                    // Pages 1-2: Dynamic thumbnail or local fallback
                    val thumbnailUrl = if (pageData.pageIndex == 0) {
                        contentState.page1ThumbnailUrl
                    } else {
                        contentState.page2ThumbnailUrl
                    }
                    val videoUrl = if (pageData.pageIndex == 0) contentState.page1VideoUrl else null
                    val localFallback = if (pageData.pageIndex == 0) {
                        contentState.page1LocalFallback ?: R.drawable.ob_page1
                    } else {
                        contentState.page2LocalFallback ?: R.drawable.ob_page2
                    }
                    val titleRes = if (pageData.pageIndex == 0) {
                        R.string.onboarding_page1_title
                    } else {
                        R.string.onboarding_page2_title
                    }
                    val subtitleRes = if (pageData.pageIndex == 0) {
                        R.string.onboarding_india_page1_subtitle
                    } else {
                        R.string.onboarding_page2_subtitle
                    }

                    WelcomePageDynamic(
                        thumbnailUrl = thumbnailUrl,
                        nameSong = contentState.nameSong,
                        nameArtist = contentState.nameArtist,
                        videoUrl = videoUrl,
                        localFallbackResId = localFallback,
                        title = stringResource(titleRes),
                        subtitle = stringResource(subtitleRes),
                        ctaText = stringResource(R.string.onboarding_next),
                        onCta = {
                            Analytics.track(
                                name = "onboarding_${pageData.pageIndex + 1}_next",
                                params = emptyMap()
                            )
                            preloadFeatureSelectionIfNeeded(page, "tapped Continue")
                            if (isLastPage) {
                                onComplete()
                            } else {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(page + 1)
                                }
                            }
                        },
                        pageIndex = pageData.pageIndex
                    )
                }
            }
        }
    }
}

// ============================================
// ONBOARDING PAGE TYPES
// ============================================

sealed class OnboardingPage {
    data class Welcome(val step: StepOnboard, val pageIndex: Int) : OnboardingPage()
    data object FullscreenAd : OnboardingPage()
    data object InterstitialAd : OnboardingPage()
}

data class StepOnboard(val pageIndex: Int)

// ============================================
// BUILD ONBOARDING PAGE SEQUENCE
// Dynamically injects fullscreen ad based on remote config
// ============================================

private fun buildOnboardingPageList(
    adsLoaderService: AdsLoaderService
): List<OnboardingPage> {
    val config = adsLoaderService.getPlacementConfig(AdPlacement.NATIVE_ONBOARDING_FULLSCREEN)
    val fullscreenEnabled = config?.enabled == true
    val injectAfterValue = config?.extras?.get("inject_after")
    val injectAfter = when {
        injectAfterValue == null -> 2
        else -> {
            val stringValue = injectAfterValue.toString().trim('"')
            stringValue.toIntOrNull() ?: 2
        }
    }
    val injectPosition = injectAfter.coerceIn(1, 3)

    // Both onboarding ad pages are injected as their own page ONLY when their placement
    // is enabled on Firebase (ad_native_onboarding_fullscreen / interstitial_onboarding).
    val interstitialEnabled =
        adsLoaderService.getPlacementConfig(AdPlacement.INTERSTITIAL_ONBOARDING)?.enabled == true

    val pages = mutableListOf<OnboardingPage>()
    repeat(3) { index ->
        pages.add(OnboardingPage.Welcome(StepOnboard(index), index))
        if (index + 1 == injectPosition) {
            if (fullscreenEnabled) {
                pages.add(OnboardingPage.FullscreenAd)
            }
            if (interstitialEnabled) {
                pages.add(OnboardingPage.InterstitialAd)
            }
        }
    }
    return pages
}
