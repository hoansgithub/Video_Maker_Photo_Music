package com.videomaker.aimusic.modules.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.videomaker.aimusic.R
import com.videomaker.aimusic.modules.onboarding.pages.OnboardingPage1
import com.videomaker.aimusic.modules.onboarding.pages.OnboardingPage2
import com.videomaker.aimusic.modules.onboarding.pages.OnboardingPage3

/**
 * OnboardingScreen — routes each welcome step to its own page composable.
 *
 * Welcome pages (1-3): WELCOME_1 → WELCOME_2 → WELCOME_3 → [onComplete]
 * Back: WELCOME_3 → WELCOME_2 → WELCOME_1 → exits via [onExitRequested].
 *
 * Genre selection (GENRE_SELECTION step) has been moved to LanguageSelectionActivity,
 * shown after the user picks their language.
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
        WelcomePageStep(
            step = step,
            onNext = viewModel::onNext,
            onComplete = onComplete
        )
    }
}

@Composable
private fun WelcomePageStep(
    step: OnboardingStep,
    onNext: () -> Unit,
    onComplete: () -> Unit
) {
    val isLastPage = step == OnboardingStep.WELCOME_3
    val ctaText = if (isLastPage) stringResource(R.string.onboarding_get_started)
                  else stringResource(R.string.onboarding_next)
        val action = if (isLastPage) onComplete else onNext

    when (step) {
        OnboardingStep.WELCOME_1 -> OnboardingPage1(ctaText, action)
        OnboardingStep.WELCOME_2 -> OnboardingPage2(ctaText, action)
        OnboardingStep.WELCOME_3 -> OnboardingPage3(ctaText, action)
    }
}
