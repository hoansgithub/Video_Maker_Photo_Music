package com.videomaker.aimusic.modules.featureselection

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import co.alcheclub.lib.acccore.ads.compose.NativeAdView
import com.videomaker.aimusic.MainActivity
import com.videomaker.aimusic.R
import com.videomaker.aimusic.core.analytics.Analytics
import com.videomaker.aimusic.core.constants.AdPlacement
import com.videomaker.aimusic.core.data.local.PreferencesManager
import com.videomaker.aimusic.modules.language.OnboardingCtaButton
import com.videomaker.aimusic.modules.onboarding.OnboardingViewModel
import com.videomaker.aimusic.modules.onboarding.pages.FeatureSurveyPage
import com.videomaker.aimusic.ui.theme.Primary
import com.videomaker.aimusic.ui.theme.VideoMakerTheme
import kotlinx.coroutines.delay
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel

class FeatureSelectionActivity : AppCompatActivity() {

    private val preferencesManager: PreferencesManager by inject()
    private val onboardingViewModel: OnboardingViewModel by viewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Analytics.trackScreenView(
            screenName = EVENT_GENRE_SHOW,
            screenClass = FeatureSelectionActivity::class.java.simpleName
        )

        setContent {
            val density = LocalDensity.current
            var isSaving by remember { mutableStateOf(false) }

            // Track bottom section height dynamically (button + ad)
            var bottomSectionHeight by remember { mutableStateOf(0) }
            val bottomPaddingDp = with(density) { bottomSectionHeight.toDp() }

            // Delayed states for ad viewability compliance (0.5-second per ad)
            // Sequential delays ensuring EACH ad gets at least 0.5 second of display time
            // Pipeline: FIRST user interaction → PRIMARY shows 0.5s → ALT shows 0.5s → Button enables
            // Total 1s delay for faster UX while maintaining ad viewability
            var delayedHasSelection by remember { mutableStateOf(false) }
            var delayedButtonEnabled by remember { mutableStateOf(false) }
            var hasStartedDelay by remember { mutableStateOf(false) }

            // Sequential delays ensuring EACH ad gets at least 0.5 second of display time
            // Timer starts on FIRST selection and does NOT reset on subsequent selections
            LaunchedEffect(hasStartedDelay) {
                if (hasStartedDelay) {
                    // Step 1: Wait 0.5s from FIRST interaction before switching to ALT ad
                    // NATIVE_ONBOARDING_FEATURE_SELECTION (PRIMARY) gets guaranteed 0.5s visibility
                    delay(500)
                    delayedHasSelection = true

                    // Step 2: Wait another 0.5s before enabling button
                    // NATIVE_ONBOARDING_FEATURE_SELECTION_ALT gets guaranteed 0.5s visibility
                    delay(500)
                    delayedButtonEnabled = true
                }
            }

            // Watch for first interaction and reset when all deselected
            LaunchedEffect(onboardingViewModel.selectedFeatures.size) {
                if (onboardingViewModel.selectedFeatures.isNotEmpty() && !hasStartedDelay) {
                    // First interaction - start the timer (only happens once)
                    hasStartedDelay = true
                    android.util.Log.d("FeatureSelection", "🎬 Started IAB viewability timer")
                } else if (onboardingViewModel.selectedFeatures.isEmpty() && hasStartedDelay) {
                    // User deselected all - reset everything
                    hasStartedDelay = false
                    delayedHasSelection = false
                    delayedButtonEnabled = false
                    android.util.Log.d("FeatureSelection", "🔄 Reset IAB viewability timer")
                }
            }

            VideoMakerTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    // Scrollable content with dynamic bottom padding
                    FeatureSurveyPage(
                        selectedFeatures = onboardingViewModel.selectedFeatures,
                        onFeatureToggle = { selectedFeature ->
                            onboardingViewModel.toggleFeature(selectedFeature)
                            onboardingViewModel.selectedFeatures.firstOrNull()?.let { genre ->
                                Analytics.track(
                                    name = EVENT_GENRE_SELECT,
                                    params = mapOf(PARAM_GENRE_SELECT to genre)
                                )
                            }
                        },
                        bottomPaddingDp = bottomPaddingDp  // Pass dynamic padding
                    )

                    // Bottom section: Native ad only (measures its own height)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = 24.dp, vertical = 16.dp)
                            .onSizeChanged { size ->
                                bottomSectionHeight = size.height  // Measure actual height dynamically!
                            }
                    ) {
                        // ALT ad - bottom layer, always at full opacity
                        NativeAdView(
                            placement = AdPlacement.NATIVE_ONBOARDING_FEATURE_SELECTION_ALT,
                            autoLoad = false,
                            modifier = Modifier.fillMaxWidth()
                        )

                        // PRIMARY ad - top layer, fades out when user selects
                        NativeAdView(
                            placement = AdPlacement.NATIVE_ONBOARDING_FEATURE_SELECTION,
                            autoLoad = false,
                            modifier = Modifier
                                .fillMaxWidth()
                                .alpha(if (delayedHasSelection) 0f else 1f)
                        )
                    }

                    // Button at top right (outside measured section)
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(horizontal = 24.dp, vertical = 48.dp)
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
                                            val selectedFeature = onboardingViewModel.selectedFeatures.firstOrNull()
                                            val initialTab = mapFeatureToInitialTab(selectedFeature)
                                            preferencesManager.setHomeInitialTabFromOnboarding(initialTab)

                                            // Mark onboarding as COMPLETE (simplified flow)
                                            // This is the END of the full flow: Language → Onboarding → Feature Selection
                                            android.util.Log.d("FeatureSelection", "🎯 Marking onboarding as COMPLETE")
                                            preferencesManager.setOnboardingComplete(true)

                                            // Verify it was saved
                                            val isComplete = preferencesManager.isOnboardingComplete()
                                            android.util.Log.d("FeatureSelection", "🎯 Verified onboarding complete: $isComplete")

                                            navigateToMain(initialTab)
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
        }
    }

    private fun navigateToMain(initialTab: Int) {
        val intent = Intent(this, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_INITIAL_TAB, initialTab)
        startActivity(intent)
        finish()
    }
}
