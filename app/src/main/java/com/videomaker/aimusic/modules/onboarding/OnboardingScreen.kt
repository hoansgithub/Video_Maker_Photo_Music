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
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.rememberLottieComposition
import com.videomaker.aimusic.R
import com.videomaker.aimusic.modules.onboarding.pages.WelcomePage
import kotlinx.coroutines.delay

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
    onComplete: () -> Unit
) {
    val currentStep by viewModel.currentStep.collectAsStateWithLifecycle()
    val pagerState = rememberPagerState(pageCount = { listOnboardingStep.size })
    var totalDrag by remember { mutableFloatStateOf(0f) }
    var showSwipeHint by remember { mutableStateOf(true) }
    val swipeHintComposition by rememberLottieComposition(
        LottieCompositionSpec.RawRes(R.raw.onboarding_leftswipe)
    )

    LaunchedEffect(Unit) {
        delay(3_000)
        showSwipeHint = false
    }


    // ── ViewModel step → pager (CTA button drives navigation) ────────────
    LaunchedEffect(currentStep) {
        val targetPage = currentStep.ordinal
        if (pagerState.currentPage != targetPage) {
            pagerState.animateScrollToPage(targetPage)
        }
    }

    // ── Pager swipe → ViewModel (user swiped manually) ───────────────────
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            viewModel.goToStep(OnboardingStep.entries[page])
            if (page > 0) showSwipeHint = false
        }
    }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.isScrollInProgress }.collect { isScrolling ->
            if (isScrolling) showSwipeHint = false
        }
    }

    BackHandler {
        if (!viewModel.onBack()) {
            onExitRequested()
        }
    }

    HorizontalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize(),
        beyondViewportPageCount = 1,
        userScrollEnabled = pagerState.currentPage != pagerState.pageCount - 1
    ) { page ->
        val step = listOnboardingStep[page]
        val isLastPage = page == listOnboardingStep.lastIndex

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
                imageResId = step.img,
                title = stringResource(step.title),
                subtitle = stringResource(step.description),
                ctaText = stringResource(R.string.onboarding_next),
                onCta = {
                    showSwipeHint = false
                    if (isLastPage) onComplete() else viewModel.onNext()
                }
            )

            if (showSwipeHint && pagerState.settledPage == 0) {
                Column(
                    modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color.White.copy(0.5f), RoundedCornerShape(24.dp))
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
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
        }
    }
}

val listOnboardingStep = listOf(
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

data class StepOnboard(
    val img: Int,
    val title: Int,
    val description: Int
)
