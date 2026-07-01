package com.videomaker.aimusic.modules.onboardingsurvey

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.videomaker.aimusic.R
import com.videomaker.aimusic.core.analytics.Analytics
import com.videomaker.aimusic.core.ui.BaseOnboardingActivity
import com.videomaker.aimusic.modules.language.OnboardingCtaButton
import com.videomaker.aimusic.modules.onboarding.OnboardingAltScreen
import com.videomaker.aimusic.modules.onboarding.OnboardingNormalScreen
import com.videomaker.aimusic.modules.onboarding.OnboardingStep
import com.videomaker.aimusic.ui.components.LocalAsyncImage
import com.videomaker.aimusic.ui.components.ModifierExtension.clickableSingle
import com.videomaker.aimusic.ui.theme.Primary
import kotlin.time.Duration.Companion.milliseconds

class SurveyAiPromoteActivity : BaseOnboardingActivity() {

    override val onboardingStep = OnboardingStep.SURVEY_AI_PROMOTE

    private var sharedBottomHeight by mutableStateOf(0)

    @Composable
    override fun Content() {
        val placements = coordinator.adPlacements(onboardingStep)

        LaunchedEffect(Unit) {
            Analytics.track(name = OnboardingSurveyAnalytics.EVENT_AI_PROMOTE_RENDER)
        }

        var bottomContainerTopPx by remember { mutableStateOf(0f) }

        val stepContent: @Composable (
            onUserInteraction: () -> Unit,
            bottomPadding: Dp,
            buttonEnabled: Boolean,
        ) -> Unit = { _, bottomPadding, buttonEnabled ->
            // Delay button enable for ad viewability (screens with no user selection)
            var delayedEnabled by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                kotlinx.coroutines.delay(2000L.milliseconds)
                delayedEnabled = true
            }

            Box(Modifier.fillMaxSize().statusBarsPadding()) {
                AiPromoteScreen(bottomContainerTopPx = bottomContainerTopPx)

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .onGloballyPositioned { coordinates ->
                            bottomContainerTopPx = coordinates.positionInParent().y
                        }
                        .clickableSingle { }
                ) {
                    LocalAsyncImage(
                        resId = R.drawable.img_bg_cta_onboard,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.matchParentSize(),
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (bottomPadding == 0.dp) Modifier.navigationBarsPadding()
                                else Modifier
                            )
                            .padding(top = 16.dp, bottom = 12.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.survey_ai_promote_title),
                            color = Color.White,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            lineHeight = 30.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Box(
                            modifier = Modifier
                                .align(Alignment.End)
                                .padding(end = 16.dp)
                        ) {
                            OnboardingCtaButton(
                                text = stringResource(R.string.onboarding_next),
                                onClick = {
                                    Analytics.track(
                                        name = OnboardingSurveyAnalytics.EVENT_AI_PROMOTE_NEXT,
                                    )
                                    navigateToNextStep()
                                },
                                enabled = buttonEnabled && delayedEnabled,
                                color = Primary,
                                icon = R.drawable.ic_right_arrow,
                            )
                        }
                    }
                }
            }
        }

        if (showAlt && placements.size > 1) {
            OnboardingAltScreen(
                altPlacement = placements[1],
                initialBottomHeight = sharedBottomHeight,
                onBottomHeightChanged = { sharedBottomHeight = it },
                content = stepContent,
            )
        } else {
            OnboardingNormalScreen(
                placement = placements.firstOrNull().orEmpty(),
                onTriggerSwap = ::triggerAltSwap,
                initialBottomHeight = sharedBottomHeight,
                onBottomHeightChanged = { sharedBottomHeight = it },
                content = stepContent,
            )
        }
    }
}