package com.videomaker.aimusic.modules.featureselection

import android.os.Bundle
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import co.alcheclub.lib.acccore.ads.compose.NativeAdView
import com.videomaker.aimusic.BuildConfig
import com.videomaker.aimusic.R
import com.videomaker.aimusic.ui.components.LocalAsyncImage
import com.videomaker.aimusic.core.ads.AdClickDetector
import com.videomaker.aimusic.core.analytics.Analytics
import com.videomaker.aimusic.core.data.local.PreferencesManager
import com.videomaker.aimusic.core.ui.BaseOnboardingActivity
import com.videomaker.aimusic.modules.language.OnboardingCtaButton
import com.videomaker.aimusic.modules.onboarding.OnboardingStep
import com.videomaker.aimusic.modules.onboarding.OnboardingViewModel
import com.videomaker.aimusic.modules.onboarding.pages.FeatureSurveyPage
import com.videomaker.aimusic.ui.components.ModifierExtension.clickableSingle
import com.videomaker.aimusic.ui.theme.Primary
import kotlinx.coroutines.delay
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.compose.koinInject

class FeatureSelectionActivity : BaseOnboardingActivity() {

    override val onboardingStep = OnboardingStep.FEATURE_SELECTION

    private val preferencesManager: PreferencesManager by inject()
    private val onboardingViewModel: OnboardingViewModel by viewModel()

    override fun onSetupComplete(savedInstanceState: Bundle?) {
        Analytics.track(name = EVENT_GENRE_SHOW)
    }

    @Composable
    override fun Content() {
        val adClickDetector: AdClickDetector = koinInject()
        val density = LocalDensity.current
        var isSaving by remember { mutableStateOf(false) }

        val adSwap = rememberAdSwapState()

        var bottomSectionHeight by remember { mutableStateOf(0) }
        val bottomPaddingDp = with(density) { bottomSectionHeight.toDp() }

        var delayedButtonEnabled by remember { mutableStateOf(false) }

        // Enable button 500ms after the screen swap completes
        LaunchedEffect(adSwap.hasSwapped) {
            if (adSwap.hasSwapped) {
                delay(500)
                delayedButtonEnabled = true
            }
        }

        // Reset to primary ad when all selections are cleared
        LaunchedEffect(onboardingViewModel.selectedFeatures.isEmpty()) {
            if (onboardingViewModel.selectedFeatures.isEmpty() && adSwap.hasSwapped) {
                adSwap.resetSwap()
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF1A1A1A))
        ) {
            Box(modifier = Modifier.weight(1f)) {
                FeatureSurveyPage(
                    selectedFeatures = onboardingViewModel.selectedFeatures,
                    onFeatureToggle = { selectedFeature ->
                        onboardingViewModel.toggleFeature(selectedFeature)
                        adSwap.triggerSwap()
                        onboardingViewModel.selectedFeatures.firstOrNull()?.let { genre ->
                            Analytics.track(
                                name = EVENT_GENRE_SELECT,
                                params = mapOf(PARAM_GENRE_SELECT to if (genre == "music_video_instant") "music" else "photo")
                            )
                        }
                    },
                    bottomPaddingDp = bottomPaddingDp
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomEnd)
                        .then(
                            if (bottomSectionHeight == 0) Modifier.navigationBarsPadding()
                            else Modifier
                        )
                        .clickableSingle{}
                ) {
                    LocalAsyncImage(
                        resId = R.drawable.img_bg_cta_onboard,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.matchParentSize()
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(top = 10.dp, bottom = 12.dp)
                    ) {
                        OnboardingCtaButton(
                            text = stringResource(R.string.onboarding_get_started),
                            onClick = {
                                if (isSaving) return@OnboardingCtaButton
                                isSaving = true
                                Analytics.track(
                                    name = EVENT_GENRE_NEXT,
                                    params = mapOf(
                                        PARAM_GENRE_SELECT to toGenreAnalyticsValue(
                                            onboardingViewModel.selectedFeatures
                                        )
                                    )
                                )
                                onboardingViewModel.saveFeatures { result ->
                                    runOnUiThread {
                                        result.onSuccess {
                                            val selectedFeature =
                                                onboardingViewModel.selectedFeatures.firstOrNull()
                                            val initialTab =
                                                mapFeatureToInitialTab(selectedFeature)
                                            preferencesManager.setHomeInitialTabFromOnboarding(
                                                initialTab
                                            )
                                            preferencesManager.setFeatureSelectionComplete(true)
                                            navigateToNextStep()
                                        }.onFailure {
                                            isSaving = false
                                            Toast.makeText(
                                                this@FeatureSelectionActivity,
                                                getString(R.string.root_try_again),
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                }
                            },
                            enabled = onboardingViewModel.selectedFeatures.isNotEmpty() && delayedButtonEnabled && !isSaving,
                            color = Primary,
                            icon = R.drawable.ic_checkmark
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .onSizeChanged { size ->
                        bottomSectionHeight = size.height
                    }
            ) {
                NativeAdView(
                    placement = adSwap.currentPlacement,
                    modifier = Modifier.fillMaxWidth(),
                    isDebug = BuildConfig.DEBUG,
                    onAdClicked = { adClickDetector.onAdClick(it) },
                )
            }
        }
    }
}
