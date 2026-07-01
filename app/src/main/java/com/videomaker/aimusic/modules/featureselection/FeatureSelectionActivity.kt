package com.videomaker.aimusic.modules.featureselection

import android.os.Bundle
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.videomaker.aimusic.R
import com.videomaker.aimusic.core.analytics.Analytics
import com.videomaker.aimusic.core.data.local.PreferencesManager
import com.videomaker.aimusic.core.ui.BaseOnboardingActivity
import com.videomaker.aimusic.modules.language.OnboardingCtaButton
import com.videomaker.aimusic.modules.onboarding.OnboardingAltScreen
import com.videomaker.aimusic.modules.onboarding.OnboardingNormalScreen
import com.videomaker.aimusic.modules.onboarding.OnboardingStep
import com.videomaker.aimusic.modules.onboarding.OnboardingViewModel
import com.videomaker.aimusic.ui.components.LocalAsyncImage
import com.videomaker.aimusic.ui.components.ModifierExtension.clickableSingle
import com.videomaker.aimusic.ui.theme.Primary
import com.videomaker.aimusic.ui.theme.VideoMakerTheme
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel

class FeatureSelectionActivity : BaseOnboardingActivity() {

    override val onboardingStep = OnboardingStep.FEATURE_SELECTION

    private val preferencesManager: PreferencesManager by inject()
    private val onboardingViewModel: OnboardingViewModel by viewModel()
    private var sharedBottomHeight by mutableStateOf(0)

    override fun onSetupComplete(savedInstanceState: Bundle?) {
        Analytics.track(name = EVENT_GENRE_SHOW)
    }

    @Composable
    override fun Content() {
        var isSaving by remember { mutableStateOf(false) }
        val placements = coordinator.adPlacements(onboardingStep!!)

        // Reset to primary ad when all selections are cleared
        LaunchedEffect(onboardingViewModel.selectedFeatures.isEmpty()) {
            if (onboardingViewModel.selectedFeatures.isEmpty() && showAlt) {
                resetToNormal()
            }
        }

        val stepContent: @Composable (
            onUserInteraction: () -> Unit,
            bottomPadding: Dp,
            buttonEnabled: Boolean,
        ) -> Unit = { onUserInteraction, bottomPadding, buttonEnabled ->
            Box(modifier = Modifier.fillMaxSize()) {
                FeatureSurveyPage(
                    selectedFeatures = onboardingViewModel.selectedFeatures,
                    onFeatureToggle = { selectedFeature ->
                        onboardingViewModel.toggleFeature(selectedFeature)
                        onUserInteraction()
                        onboardingViewModel.selectedFeatures.firstOrNull()?.let { genre ->
                            Analytics.track(
                                name = EVENT_GENRE_SELECT,
                                params = mapOf(PARAM_GENRE_SELECT to if (genre == "music_video_instant") "music" else "photo")
                            )
                        }
                    },
                    bottomPaddingDp = bottomPadding
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomEnd)
                        .then(
                            if (bottomPadding == 0.dp) Modifier.navigationBarsPadding()
                            else Modifier
                        )
                        .clickableSingle {}
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
                            enabled = onboardingViewModel.selectedFeatures.isNotEmpty() && buttonEnabled && !isSaving,
                            color = Primary,
                            icon = R.drawable.ic_checkmark
                        )
                    }
                }
            }
        }

        Box(Modifier.fillMaxSize().background(Color(0xFF1A1A1A))) {
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
}

// ============================================
// PREVIEW
// ============================================

@Preview(name = "Template Selected", showBackground = true, widthDp = 375, heightDp = 812, backgroundColor = 0xFF1A1A1A)
@Composable
private fun FeatureSelectionPreviewTemplate() {
    VideoMakerTheme {
        FeatureSurveyPage(selectedFeatures = listOf("music_video_instant"), onFeatureToggle = {})
    }
}

@Preview(name = "Song Selected", showBackground = true, widthDp = 375, heightDp = 812, backgroundColor = 0xFF1A1A1A)
@Composable
private fun FeatureSelectionPreviewSong() {
    VideoMakerTheme {
        FeatureSurveyPage(selectedFeatures = listOf("photos_to_video"), onFeatureToggle = {})
    }
}

@Preview(name = "AI Selected", showBackground = true, widthDp = 375, heightDp = 812, backgroundColor = 0xFF1A1A1A)
@Composable
private fun FeatureSelectionPreviewAI() {
    VideoMakerTheme {
        FeatureSurveyPage(selectedFeatures = listOf("create_with_ai"), onFeatureToggle = {})
    }
}
