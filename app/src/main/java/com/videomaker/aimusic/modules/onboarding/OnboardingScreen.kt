package com.videomaker.aimusic.modules.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import com.videomaker.aimusic.core.constants.AdPlacement
import com.videomaker.aimusic.modules.onboarding.pages.DynamicCarousel
import com.videomaker.aimusic.modules.onboarding.pages.FullscreenAdStep
import com.videomaker.aimusic.modules.onboarding.pages.WelcomePageDynamic
import kotlinx.coroutines.delay
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

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.isScrollInProgress }.collect { isScrolling ->
            if (isScrolling) showSwipeHint = false
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
                VideoMakerApplication.preloadInterstitialAd(AdPlacement.INTERSTITIAL_ONBOARDING_COMPLETE)
                hasPreloadedFeatureSelection = true
            }
        }
    }

    LaunchedEffect(pagerState) {
        var previousPage = pagerState.currentPage
        snapshotFlow { pagerState.currentPage }.collect { currentPage ->
            if (previousPage != currentPage && previousPage >= 0) {
                preloadFeatureSelectionIfNeeded(previousPage, "swiped")
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
        userScrollEnabled = pagerState.currentPage != pagerState.pageCount - 1
    ) { page ->
        val pageData = pageList[page]
        val isLastPage = page == pageList.lastIndex

        when (pageData) {
            is OnboardingPage.FullscreenAd -> {
                FullscreenAdStep(
                    isCurrentPage = page == pagerState.currentPage,
                    onClose = {
                        preloadFeatureSelectionIfNeeded(page, "closed ad")
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
                            showSwipeHint = false
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

                    val pageModifier = if (isLastPage) {
                        Modifier.pointerInput(Unit) {
                            detectHorizontalDragGestures(
                                onDragStart = { totalDrag = 0f },
                                onHorizontalDrag = { change, dragAmount ->
                                    change.consume()
                                    totalDrag += dragAmount
                                },
                                onDragEnd = {
                                    val shouldComplete = totalDrag <= -80f
                                    totalDrag = 0f
                                    if (shouldComplete) onComplete()
                                },
                                onDragCancel = { totalDrag = 0f }
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
                                showSwipeHint = false
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
                                    modifier = Modifier.size(90.dp)
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
}

// ============================================
// ONBOARDING PAGE TYPES
// ============================================

sealed class OnboardingPage {
    data class Welcome(val step: StepOnboard, val pageIndex: Int) : OnboardingPage()
    data object FullscreenAd : OnboardingPage()
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
    val injectAfterValue = config?.extras?.get("inject_after")
    val injectAfter = when {
        injectAfterValue == null -> 2
        else -> {
            val stringValue = injectAfterValue.toString().trim('"')
            stringValue.toIntOrNull() ?: 2
        }
    }
    val injectPosition = injectAfter.coerceIn(1, 3)

    val pages = mutableListOf<OnboardingPage>()
    repeat(3) { index ->
        pages.add(OnboardingPage.Welcome(StepOnboard(index), index))
        if (index + 1 == injectPosition) {
            pages.add(OnboardingPage.FullscreenAd)
        }
    }
    return pages
}
