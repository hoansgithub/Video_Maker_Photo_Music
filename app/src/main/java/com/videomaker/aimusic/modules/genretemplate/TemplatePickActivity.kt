package com.videomaker.aimusic.modules.genretemplate

import android.os.Bundle
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import co.alcheclub.lib.acccore.ads.compose.NativeAdView
import com.videomaker.aimusic.BuildConfig
import com.videomaker.aimusic.R
import com.videomaker.aimusic.ui.components.LocalAsyncImage
import com.videomaker.aimusic.core.ads.AdClickDetector
import com.videomaker.aimusic.core.ads.AdPlacementConfigService
import com.videomaker.aimusic.core.analytics.Analytics
import com.videomaker.aimusic.core.constants.AdPlacement
import com.videomaker.aimusic.core.data.local.PreferencesManager
import com.videomaker.aimusic.core.ui.BaseOnboardingActivity
import com.videomaker.aimusic.modules.language.OnboardingCtaButton
import com.videomaker.aimusic.modules.onboarding.OnboardingStep
import com.videomaker.aimusic.ui.components.ModifierExtension.clickableSingle
import com.videomaker.aimusic.ui.theme.Primary
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.compose.koinInject

class TemplatePickActivity : BaseOnboardingActivity() {

    override val onboardingStep = OnboardingStep.TEMPLATE_PICK

    private val viewModel: GenreTemplateViewModel by viewModel()
    private val preferencesManager: PreferencesManager by inject()

    override fun onSetupComplete(savedInstanceState: Bundle?) {
        Analytics.track(name = "vibe_template_render")
        // Load templates for the genre selected in GenreSelectionActivity
        val genreId = preferencesManager.getOnboardingSelectedGenre()
        viewModel.loadTemplatesForGenreId(genreId)
    }

    @Composable
    override fun Content() {
        val adClickDetector: AdClickDetector = koinInject()
        val adPlacementConfigService: AdPlacementConfigService = koinInject()
        var bottomSectionHeight by remember { mutableStateOf(0) }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF1A1A1A))
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .statusBarsPadding(),
                contentAlignment = Alignment.TopEnd
            ) {
                TemplatePickScreen(
                    templates = viewModel.suggestedTemplates,
                    selectedTemplate = viewModel.selectedTemplate.value,
                    onTemplateSelect = { template ->
                        viewModel.selectTemplate(template)
                        Analytics.track(
                            name = "vibe_template_select",
                            params = mapOf("template_id" to template.id)
                        )
                    }
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomEnd)
                        .then(
                            if (bottomSectionHeight == 0) Modifier.navigationBarsPadding()
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
                            text = stringResource(R.string.onboarding_next),
                            onClick = {
                                val template = viewModel.selectedTemplate.value
                                    ?: return@OnboardingCtaButton
                                Analytics.track(
                                    name = "vibe_template_next",
                                    params = mapOf("template_id" to template.id)
                                )
                                navigateToNextStep()
                            },
                            enabled = viewModel.selectedTemplate.value != null,
                            color = Primary,
                            icon = R.drawable.ic_right_arrow
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
                    .then(if (adPlacementConfigService.adBottomNavPaddingEnabled) Modifier.navigationBarsPadding() else Modifier)
            ) {
                NativeAdView(
                    placement = AdPlacement.NATIVE_ONBOARDING_SELECT_TPT,
                    modifier = Modifier.fillMaxWidth(),
                    isDebug = BuildConfig.DEBUG,
                    onAdClicked = { adClickDetector.onAdClick(it) }
                )
            }
        }
    }
}
