package com.videomaker.aimusic.modules.featureselection

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.videomaker.aimusic.MainActivity
import com.videomaker.aimusic.R
import com.videomaker.aimusic.core.analytics.Analytics
import com.videomaker.aimusic.core.data.local.PreferencesManager
import com.videomaker.aimusic.modules.language.OnboardingCtaButton
import com.videomaker.aimusic.modules.onboarding.OnboardingViewModel
import com.videomaker.aimusic.modules.onboarding.pages.FeatureSurveyPage
import com.videomaker.aimusic.ui.theme.Primary
import com.videomaker.aimusic.ui.theme.VideoMakerTheme
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
            var isSaving by remember { mutableStateOf(false) }

            VideoMakerTheme {
                Box(modifier = Modifier.fillMaxSize()) {
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
                        }
                    )

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
                                            preferencesManager.setFeatureSelectionComplete(true)
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
                            enabled = onboardingViewModel.selectedFeatures.isNotEmpty() && !isSaving,
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
