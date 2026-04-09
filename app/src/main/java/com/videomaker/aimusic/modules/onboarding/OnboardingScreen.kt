package com.videomaker.aimusic.modules.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.alcheclub.lib.acccore.ads.loader.AdsLoaderService
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.rememberLottieComposition
import com.videomaker.aimusic.R
import com.videomaker.aimusic.VideoMakerApplication
import com.videomaker.aimusic.core.analytics.Analytics
import com.videomaker.aimusic.core.analytics.AnalyticsEvent
import com.videomaker.aimusic.core.constants.AdPlacement
import com.videomaker.aimusic.modules.onboarding.pages.FullscreenAdStep
import com.videomaker.aimusic.modules.onboarding.pages.WelcomePage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.distinctUntilChanged
import org.koin.compose.koinInject

/**
 * OnboardingScreen — swipe-driven welcome flow backed by HorizontalPager.
 *
 * Pages (0-based index maps to OnboardingStep ordinal):
 *   0 → WELCOME_1, 1 → WELCOME_2, 2 → WELCOME_3
 *
 * Bidirectional sync:
 *   • CTA "Next" tapped  → viewModel.onNext() → currentStep ↑ → pager animates forward
 *   • User swipes pager  → settledPage changes → viewModel.goToStep() syncs state
 *
 * Back: WELCOME_3 → WELCOME_2 → WELCOME_1 → exits via [onExitRequested].
 */
@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel,
    onExitRequested: () -> Unit,
    onComplete: () -> Unit,
    adsLoaderService: AdsLoaderService = koinInject()
) {
    // Build page list dynamically based on remote config
    val pageList = remember {
        buildOnboardingPageList(adsLoaderService)
    }

    val coroutineScope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { pageList.size })
    var totalDrag by remember { mutableFloatStateOf(0f) }
    var showSwipeHint by remember { mutableStateOf(true) }
    val swipeHintComposition by rememberLottieComposition(
        LottieCompositionSpec.RawRes(R.raw.onboarding_leftswipe)
    )

    LaunchedEffect(Unit) {
        delay(3_000)
        showSwipeHint = false
    }

    // Track analytics when page changes
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { page ->
                if (page > 0) showSwipeHint = false
                // Track page view (1-indexed for analytics)
                val onboardingStep = page + 1
                Analytics.track(
                    name = "onboarding_$onboardingStep",
                    params = mapOf("onboarding_screen" to "ob$onboardingStep")
                )
            }
    }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.isScrollInProgress }.collect { isScrolling ->
            if (isScrolling) showSwipeHint = false
        }
    }

    // Track Feature Selection ads preloading status
    // Preload triggered when user LEAVES one of the last 2 pages (via Continue or Swipe)
    var hasPreloadedFeatureSelection by remember { mutableStateOf(false) }

    // Helper function to preload Feature Selection ads
    fun preloadFeatureSelectionIfNeeded(triggeredFromPage: Int, action: String) {
        if (!hasPreloadedFeatureSelection) {
            // Preload when user LEAVES one of the last 2 pages
            val isNearEnd = triggeredFromPage >= pageList.size - 2

            if (isNearEnd) {
                android.util.Log.d("OnboardingScreen", "🔄 User ${action} from page ${triggeredFromPage}/${pageList.lastIndex}, preloading Feature Selection ads")
                VideoMakerApplication.preloadNativeAd(AdPlacement.NATIVE_ONBOARDING_FEATURE_SELECTION)
                VideoMakerApplication.preloadNativeAd(AdPlacement.NATIVE_ONBOARDING_FEATURE_SELECTION_ALT)
                hasPreloadedFeatureSelection = true
            }
        }
    }

    // Monitor page changes to catch SWIPE gestures (backup to Continue button)
    LaunchedEffect(pagerState) {
        var previousPage = pagerState.currentPage

        snapshotFlow { pagerState.currentPage }.collect { currentPage ->
            // User navigated away from previous page (either via swipe or button)
            // Check if they LEFT one of the last 2 pages
            if (previousPage != currentPage && previousPage >= 0) {
                preloadFeatureSelectionIfNeeded(previousPage, "swiped")
            }

            previousPage = currentPage
        }
    }

    BackHandler {
        if (pagerState.currentPage > 0) {
            // Go back one page
            coroutineScope.launch {
                pagerState.animateScrollToPage(pagerState.currentPage - 1)
            }
        } else {
            onExitRequested()
        }
    }

    HorizontalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize(),
        beyondViewportPageCount = 1,
        userScrollEnabled = pagerState.currentPage != pagerState.pageCount - 1
    ) { page ->
        val pageData = pageList[page]
        val isLastPage = page == pageList.lastIndex

        when (pageData) {
            // Fullscreen native ad page
            is OnboardingPage.FullscreenAd -> {
                FullscreenAdStep(
                    isCurrentPage = page == pagerState.currentPage,
                    onClose = {
                        // Preload Feature Selection ads if this is one of the last 2 pages
                        preloadFeatureSelectionIfNeeded(page, "closed ad")

                        // Navigate to next page when user closes the ad
                        showSwipeHint = false
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

            // Regular welcome page
            is OnboardingPage.Welcome -> {
                val pageModifier = if (isLastPage) {
                    Modifier.pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onDragStart = {
                                totalDrag = 0f
                            },
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                totalDrag += dragAmount
                            },
                            onDragEnd = {
                                val shouldComplete = totalDrag <= -80f
                                totalDrag = 0f
                                if (shouldComplete) onComplete()
                            },
                            onDragCancel = {
                                totalDrag = 0f
                            }
                        )
                    }
                } else {
                    Modifier
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(pageModifier)
                ) {
                    WelcomePage(
                        imageResId = pageData.step.img,
                        title = stringResource(pageData.step.title),
                        subtitle = stringResource(pageData.step.description),
                        ctaText = stringResource(R.string.onboarding_next),
                        onCta = {
                            // Preload Feature Selection ads if this is one of the last 2 pages
                            preloadFeatureSelectionIfNeeded(page, "tapped Continue")

                            showSwipeHint = false
                            if (isLastPage) {
                                onComplete()
                            } else {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(page + 1)
                                }
                            }
                        },
                        pageIndex = pageData.pageIndex  // Pass page index for ad placement
                    )

                    if (showSwipeHint && pagerState.settledPage == 0) {
                        Column(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .background(Color.Black.copy(0.7f), RoundedCornerShape(24.dp))
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            LottieAnimation(
                                composition = swipeHintComposition,
                                iterations = LottieConstants.IterateForever,
                                modifier = Modifier
                                    .size(90.dp)
                            )
                            Text(
                                text = stringResource(R.string.onboarding_swipe_next),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.W500,
                                color = Color.White
                            )
                        }
                    }
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
}

data class StepOnboard(
    val img: Int,
    val title: Int,
    val description: Int
)

// ============================================
// BASE WELCOME PAGES (without fullscreen ad)
// ============================================

private val baseWelcomePages = listOf(
    StepOnboard(
        img = R.drawable.ob_page1,
        title = R.string.onboarding_page1_title,
        description = R.string.onboarding_page1_subtitle
    ),
    StepOnboard(
        img = R.drawable.ob_page2,
        title = R.string.onboarding_page2_title,
        description = R.string.onboarding_page2_subtitle
    ),
    StepOnboard(
        img = R.drawable.ob_page3,
        title = R.string.onboarding_page3_title,
        description = R.string.onboarding_page3_subtitle
    ),
)

// ============================================
// BUILD ONBOARDING PAGE SEQUENCE
// Dynamically injects fullscreen ad based on remote config
// ============================================

/**
 * Builds the onboarding page list with fullscreen ad injected at configured position.
 *
 * Remote Config (ad_native_onboarding_fullscreen.extras):
 * - inject_after: Which page to show after (1, 2, or 3, default: 2)
 *
 * Example configurations:
 * - inject_after = 1: Shows ad after page 1 (between pages 1 and 2)
 * - inject_after = 2: Shows ad after page 2 (between pages 2 and 3) [DEFAULT]
 * - inject_after = 3: Shows ad after page 3 (before feature selection)
 */
private fun buildOnboardingPageList(adsLoaderService: AdsLoaderService): List<OnboardingPage> {
    // Read injection position from remote config
    val config = adsLoaderService.getPlacementConfig(AdPlacement.NATIVE_ONBOARDING_FULLSCREEN)
    val injectAfterValue = config?.extras?.get("inject_after")

    // Support both string "2" and number 2 formats
    val injectAfter = when {
        injectAfterValue == null -> 2
        else -> {
            // Try parsing: handles both "2" (string) and 2 (number)
            val stringValue = injectAfterValue.toString().trim('"')
            stringValue.toIntOrNull() ?: 2
        }
    }

    // Clamp to valid range (1-3)
    val injectPosition = injectAfter.coerceIn(1, 3)

    android.util.Log.d("OnboardingScreen", "🎯 Fullscreen ad will inject after page $injectPosition (raw value: $injectAfterValue)")

    // Build page list with fullscreen ad injected at configured position
    val pages = mutableListOf<OnboardingPage>()

    baseWelcomePages.forEachIndexed { index, step ->
        // Add welcome page
        pages.add(OnboardingPage.Welcome(step, index))

        // Insert fullscreen ad after the configured page
        if (index + 1 == injectPosition) {
            pages.add(OnboardingPage.FullscreenAd)
        }
    }

    return pages
}
