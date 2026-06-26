package com.videomaker.aimusic.modules.onboardingsurvey

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.alcheclub.lib.acccore.ads.compose.NativeAdView
import com.videomaker.aimusic.BuildConfig
import com.videomaker.aimusic.R
import com.videomaker.aimusic.ui.components.LocalAsyncImage
import com.videomaker.aimusic.core.ads.AdClickDetector
import com.videomaker.aimusic.core.ads.AdPlacementConfigService
import com.videomaker.aimusic.core.analytics.Analytics
import com.videomaker.aimusic.core.ui.BaseOnboardingActivity
import com.videomaker.aimusic.modules.language.OnboardingCtaButton
import com.videomaker.aimusic.modules.onboarding.OnboardingStep
import com.videomaker.aimusic.ui.components.ModifierExtension.clickableSingle
import com.videomaker.aimusic.ui.theme.Primary
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.compose.koinInject

class SurveyAiLevelActivity : BaseOnboardingActivity() {

    override val onboardingStep = OnboardingStep.SURVEY_AI_LEVEL

    private val viewModel: OnboardingSurveyViewModel by viewModel()

    @Composable
    override fun Content() {
        val adClickDetector: AdClickDetector = koinInject()
        val adPlacementConfigService: AdPlacementConfigService = koinInject()
        val density = LocalDensity.current
        val selectedIds by viewModel.selectedAiLevel.collectAsStateWithLifecycle()
        val selectedId = selectedIds.firstOrNull().orEmpty()

        val adSwap = rememberAdSwapState()

        var bottomSectionHeight by remember { mutableStateOf(0) }
        val bottomPaddingDp = with(density) { bottomSectionHeight.toDp() }

        LaunchedEffect(Unit) {
            Analytics.track(name = OnboardingSurveyAnalytics.EVENT_AI_LEVEL_RENDER)
        }

        Column(
            modifier = Modifier
                .statusBarsPadding()
                .fillMaxSize()
        ) {
            Box(modifier = Modifier.weight(1f)) {
                AiLevelScreen(
                    items = AI_LEVEL_ITEMS,
                    selectedId = selectedId,
                    onSelect = { id ->
                        viewModel.selectAiLevel(id)
                        adSwap.triggerSwap()
                        Analytics.track(
                            name = OnboardingSurveyAnalytics.EVENT_AI_LEVEL_SELECT,
                            params = mapOf(OnboardingSurveyAnalytics.PARAM_AI_LEVEL to id),
                        )
                    },
                    bottomPaddingDp = bottomPaddingDp,
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomEnd)
                        .then(
                            if (bottomSectionHeight == 0) Modifier.navigationBarsPadding()
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
                            enabled = selectedIds.isNotEmpty(),
                            color = Primary,
                            icon = R.drawable.ic_right_arrow,
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = bottomPaddingDp)
                    .onSizeChanged { size ->
                        if (size.height > bottomSectionHeight) bottomSectionHeight = size.height
                    }
                    .then(if (adPlacementConfigService.adBottomNavPaddingEnabled) Modifier.navigationBarsPadding() else Modifier)
            ) {
                NativeAdView(
                    placement = adSwap.currentPlacement,
                    modifier = Modifier.fillMaxWidth(),
                    isDebug = BuildConfig.DEBUG,
                    onAdClicked = { adClickDetector.onAdClick(it) }
                )
            }
        }
    }
}
