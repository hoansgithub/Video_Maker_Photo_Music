package com.videomaker.aimusic.modules.onboardingsurvey

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.alcheclub.lib.acccore.ads.compose.NativeAdView
import com.videomaker.aimusic.BuildConfig
import com.videomaker.aimusic.R
import com.videomaker.aimusic.VideoMakerApplication
import com.videomaker.aimusic.ui.components.LocalAsyncImage
import com.videomaker.aimusic.core.ads.AdClickDetector
import com.videomaker.aimusic.core.ads.AdPlacementConfigService
import com.videomaker.aimusic.core.analytics.Analytics
import com.videomaker.aimusic.core.ui.BaseOnboardingActivity
import com.videomaker.aimusic.modules.language.OnboardingCtaButton
import com.videomaker.aimusic.modules.onboarding.OnboardingStep
import com.videomaker.aimusic.ui.components.ModifierExtension.clickableSingle
import com.videomaker.aimusic.ui.theme.Primary
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.compose.koinInject

class SurveyPlatformActivity : BaseOnboardingActivity() {

    override val onboardingStep = OnboardingStep.SURVEY_PLATFORM

    private val viewModel: OnboardingSurveyViewModel by viewModel()

    @Composable
    override fun Content() {
        val adClickDetector: AdClickDetector = koinInject()
        val adPlacementConfigService: AdPlacementConfigService = koinInject()
        val config = remember { PLATFORM_CONFIG }
        val density = LocalDensity.current
        val scope = rememberCoroutineScope()
        val selectedIds by viewModel.selectedFlow(OnboardingSurveyStep.PLATFORM).collectAsStateWithLifecycle()

        var bottomSectionHeight by remember { mutableStateOf(0) }
        val bottomPaddingDp = with(density) { bottomSectionHeight.toDp() }

        // IAB viewability delay (single 0.5s)
        var hasStartedDelay by remember { mutableStateOf(false) }
        var delayedButtonEnabled by remember { mutableStateOf(false) }

        // Reload-on-selection state
        var adReloadKey by remember { mutableIntStateOf(0) }
        var reloadJob by remember { mutableStateOf<Job?>(null) }

        val listState = rememberLazyListState()

        LaunchedEffect(Unit) {
            Analytics.track(name = config.eventRender)
        }

        LaunchedEffect(hasStartedDelay) {
            if (hasStartedDelay) {
                delay(500)
                delayedButtonEnabled = true
            }
        }

        LaunchedEffect(selectedIds.isEmpty()) {
            if (selectedIds.isNotEmpty() && !hasStartedDelay) {
                hasStartedDelay = true
            } else if (selectedIds.isEmpty() && hasStartedDelay) {
                hasStartedDelay = false
                delayedButtonEnabled = false
            }
        }

        Column(
            modifier = Modifier
                .statusBarsPadding()
                .fillMaxSize()
                .background(Color(0xFF1A1A1A))
        ) {
            Box(modifier = Modifier.weight(1f)) {
                OnboardingSurveyList(
                    config = config,
                    selectedIds = selectedIds,
                    listState = listState,
                    onToggle = { id ->
                        val wasEmpty = selectedIds.isEmpty()
                        val nowSelected = viewModel.toggle(OnboardingSurveyStep.PLATFORM, id)
                        Analytics.track(
                            name = config.eventSelect,
                            params = mapOf(config.paramKey to id),
                        )
                        // Reload ad from 2nd selection onward
                        if (nowSelected && !wasEmpty) {
                            reloadJob?.cancel()
                            reloadJob = scope.launch {
                                if (VideoMakerApplication.preloadNativeAdSuspend(config.placement)) {
                                    adReloadKey++
                                }
                            }
                        }
                    },
                    bottomPaddingDp = bottomPaddingDp,
                )

                // CTA button
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
                                    name = config.eventNext,
                                    params = buildMap {
                                        putAll(OnboardingSurveyAnalytics.expandSelection(config.paramKey, selectedIds))
                                        put(config.countKey, selectedIds.size.toString())
                                    },
                                )
                                navigateToNextStep()
                            },
                            enabled = selectedIds.isNotEmpty() && delayedButtonEnabled,
                            color = Primary,
                            icon = R.drawable.ic_right_arrow,
                        )
                    }
                }
            }

            // Bottom ad section
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = bottomPaddingDp)
                    .onSizeChanged { size ->
                        if (size.height > bottomSectionHeight) bottomSectionHeight = size.height
                    }
                    .then(if (adPlacementConfigService.adBottomNavPaddingEnabled) Modifier.navigationBarsPadding() else Modifier)
            ) {
                key(adReloadKey) {
                    NativeAdView(
                        placement = config.placement,
                        modifier = Modifier.fillMaxWidth(),
                        isDebug = BuildConfig.DEBUG,
                        onAdClicked = { adClickDetector.onAdClick(it) },
                    )
                }
            }
        }
    }
}
