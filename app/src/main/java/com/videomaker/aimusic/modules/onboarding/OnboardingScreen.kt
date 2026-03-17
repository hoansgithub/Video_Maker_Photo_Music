package com.videomaker.aimusic.modules.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.videomaker.aimusic.R
import com.videomaker.aimusic.modules.language.OnboardingCtaButton
import com.videomaker.aimusic.modules.onboarding.pages.FeatureSurveyPage
import com.videomaker.aimusic.modules.onboarding.pages.OnboardingPage1
import com.videomaker.aimusic.modules.onboarding.pages.OnboardingPage2
import com.videomaker.aimusic.modules.onboarding.pages.OnboardingPage3

private val welcomeSteps = listOf(
    OnboardingStep.WELCOME_1,
    OnboardingStep.WELCOME_2,
    OnboardingStep.WELCOME_3
)

/**
 * OnboardingScreen — routes each step to its own page composable.
 *
 * Welcome pages (1-3) share the same bottom control bar with a capsule indicator.
 * Back: GENRE_SELECTION → WELCOME_3 → WELCOME_2 → WELCOME_1 → exits via [onExitRequested].
 */
@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel,
    onExitRequested: () -> Unit,
    onComplete: () -> Unit
) {
    val currentStep by viewModel.currentStep.collectAsStateWithLifecycle()

    BackHandler {
        if (!viewModel.onBack()) {
            onExitRequested()
        }
    }

    AnimatedContent(
        targetState = currentStep,
        transitionSpec = {
            val forward = targetState.ordinal > initialState.ordinal
            (slideInHorizontally(tween(300)) { if (forward) it else -it } + fadeIn(tween(300))) togetherWith
                    (slideOutHorizontally(tween(300)) { if (forward) -it else it } + fadeOut(tween(300)))
        },
        label = "onboarding_step"
    ) { step ->
        when (step) {
            OnboardingStep.WELCOME_1,
            OnboardingStep.WELCOME_2,
            OnboardingStep.WELCOME_3 -> WelcomePageStep(
                step = step,
                onNext = viewModel::onNext
            )

            OnboardingStep.GENRE_SELECTION -> SurveyStep(
                viewModel = viewModel,
                onComplete = onComplete
            )
        }
    }
}

@Composable
private fun WelcomePageStep(
    step: OnboardingStep,
    onNext: () -> Unit
) {
    val isLastPage = step == OnboardingStep.WELCOME_3
    val ctaText = if (isLastPage) stringResource(R.string.onboarding_get_started)
                  else stringResource(R.string.onboarding_next)

    when (step) {
        OnboardingStep.WELCOME_1 -> OnboardingPage1(ctaText, onNext)
        OnboardingStep.WELCOME_2 -> OnboardingPage2(ctaText, onNext)
        OnboardingStep.WELCOME_3 -> OnboardingPage3(ctaText, onNext)
        OnboardingStep.GENRE_SELECTION -> Unit // handled by SurveyStep above
    }
}

@Composable
private fun SurveyStep(
    viewModel: OnboardingViewModel,
    onComplete: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        FeatureSurveyPage(
            selectedFeatures = viewModel.selectedFeatures,
            onFeatureToggle = viewModel::toggleFeature
        )

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 48.dp)
        ) {
            OnboardingCtaButton(
                text = stringResource(R.string.onboarding_get_started),
                onClick = { viewModel.saveFeatures(onSaved = onComplete) },
                enabled = viewModel.selectedFeatures.isNotEmpty()
            )
        }
    }
}
