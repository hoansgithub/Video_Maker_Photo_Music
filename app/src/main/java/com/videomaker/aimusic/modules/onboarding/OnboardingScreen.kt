package com.videomaker.aimusic.modules.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.videomaker.aimusic.R
import com.videomaker.aimusic.modules.onboarding.pages.OnboardingPage1
import com.videomaker.aimusic.modules.onboarding.pages.OnboardingPage2
import com.videomaker.aimusic.modules.onboarding.pages.OnboardingPage3
import com.videomaker.aimusic.modules.onboarding.pages.OnboardingPage4

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

            OnboardingStep.GENRE_SELECTION -> GenreSelectionStep(
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
    val pageIndex = welcomeSteps.indexOf(step)
    val isLastPage = step == OnboardingStep.WELCOME_3

    Box(modifier = Modifier.fillMaxSize()) {
        // Page content
        when (step) {
            OnboardingStep.WELCOME_1 -> OnboardingPage1()
            OnboardingStep.WELCOME_2 -> OnboardingPage2()
            OnboardingStep.WELCOME_3 -> OnboardingPage3()
            else -> Unit
        }

        // Bottom overlay: capsule indicator + button
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 32.dp, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Capsule/dot indicator
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 32.dp)
            ) {
                welcomeSteps.forEachIndexed { index, _ ->
                    val isActive = index == pageIndex
                    Box(
                        modifier = Modifier
                            .height(8.dp)
                            .width(if (isActive) 32.dp else 8.dp)
                            .clip(RoundedCornerShape(50))
                            .background(
                                if (isActive) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                            )
                    )
                }
            }

            Button(
                onClick = onNext,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text(
                    text = if (isLastPage) stringResource(R.string.onboarding_get_started)
                    else stringResource(R.string.onboarding_next),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun GenreSelectionStep(
    viewModel: OnboardingViewModel,
    onComplete: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        OnboardingPage4(
            selectedGenres = viewModel.selectedGenres,
            onGenreToggle = viewModel::toggleGenre
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 32.dp, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Button(
                onClick = {
                    viewModel.saveGenres()
                    onComplete()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text(
                    text = stringResource(R.string.onboarding_get_started),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}