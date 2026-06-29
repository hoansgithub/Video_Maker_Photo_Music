package com.videomaker.aimusic.modules.onboardingsurvey

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import org.koin.androidx.viewmodel.ext.android.viewModel

class SurveyAiLevelActivity : BaseOnboardingActivity() {

    override val onboardingStep = OnboardingStep.SURVEY_AI_LEVEL

    private val viewModel: OnboardingSurveyViewModel by viewModel()
    private var sharedBottomHeight by mutableStateOf(0)

    @Composable
    override fun Content() {
        val selectedIds by viewModel.selectedAiLevel.collectAsStateWithLifecycle()
        val selectedId = selectedIds.firstOrNull().orEmpty()
        val placements = coordinator.adPlacements(onboardingStep!!)

        LaunchedEffect(Unit) {
            Analytics.track(name = OnboardingSurveyAnalytics.EVENT_AI_LEVEL_RENDER)
        }

        val stepContent: @Composable (
            onUserInteraction: () -> Unit,
            bottomPadding: Dp,
            buttonEnabled: Boolean,
        ) -> Unit = { onUserInteraction, bottomPadding, buttonEnabled ->
            Box(Modifier.fillMaxSize().statusBarsPadding()) {
                AiLevelScreen(
                    items = AI_LEVEL_ITEMS,
                    selectedId = selectedId,
                    onSelect = { id ->
                        viewModel.selectAiLevel(id)
                        onUserInteraction()
                        Analytics.track(
                            name = OnboardingSurveyAnalytics.EVENT_AI_LEVEL_SELECT,
                            params = mapOf(OnboardingSurveyAnalytics.PARAM_AI_LEVEL to id),
                        )
                    },
                    bottomPaddingDp = bottomPadding,
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomEnd)
                        .then(
                            if (bottomPadding == 0.dp) Modifier.navigationBarsPadding()
                            else Modifier
                        )
                        .clickableSingle { }
                ) {
                    LocalAsyncImage(
                        resId = R.drawable.img_bg_cta_onboard,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.matchParentSize(),
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(top = 10.dp, bottom = 12.dp)
                    ) {
                        OnboardingCtaButton(
                            text = stringResource(R.string.onboarding_next),
                            onClick = {
                                Analytics.track(
                                    name = OnboardingSurveyAnalytics.EVENT_AI_LEVEL_NEXT,
                                    params = mapOf(OnboardingSurveyAnalytics.PARAM_AI_LEVEL to selectedId),
                                )
                                navigateToNextStep()
                            },
                            enabled = selectedIds.isNotEmpty() && buttonEnabled,
                            color = Primary,
                            icon = R.drawable.ic_right_arrow,
                        )
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
