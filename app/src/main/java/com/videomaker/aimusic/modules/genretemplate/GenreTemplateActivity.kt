package com.videomaker.aimusic.modules.genretemplate

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.paint
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.alcheclub.lib.acccore.ads.compose.NativeAdView
import com.videomaker.aimusic.BuildConfig
import com.videomaker.aimusic.R
import com.videomaker.aimusic.core.analytics.Analytics
import com.videomaker.aimusic.core.constants.AdPlacement
import com.videomaker.aimusic.modules.language.OnboardingCtaButton
import com.videomaker.aimusic.ui.components.ModifierExtension.clickableSingle
import com.videomaker.aimusic.ui.theme.Primary
import com.videomaker.aimusic.ui.theme.VideoMakerTheme
import org.koin.androidx.viewmodel.ext.android.viewModel
import com.videomaker.aimusic.core.ads.AdClickDetector
import org.koin.compose.koinInject

class GenreTemplateActivity : AppCompatActivity() {

    private val viewModel: GenreTemplateViewModel by viewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Analytics.track(name = "music_genre_render")

        setContent {
            val adClickDetector: AdClickDetector = koinInject()
            var isSaving by remember { mutableStateOf(false) }
            var bottomSectionHeight by remember { mutableStateOf(0) }
            val currentStep by viewModel.currentStep.collectAsStateWithLifecycle()

            LaunchedEffect(Unit) {
                viewModel.navToNext.collect { navigateToFeatureSelection() }
            }

            LaunchedEffect(currentStep) {
                when (currentStep) {
                    GenreTemplateStep.TEMPLATE_PICK -> Analytics.track(name = "vibe_template_render")
                    else -> {}
                }
            }

            VideoMakerTheme {
                var showExitDialog by remember { mutableStateOf(false) }

                BackHandler { showExitDialog = true }

                if (showExitDialog) {
                    com.videomaker.aimusic.modules.onboarding.OnboardingExitDialog(
                        onExit = { finish() },
                        onDismiss = { showExitDialog = false }
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF1A1A1A))
                ) {
                    Column(
                        modifier = Modifier
                            .statusBarsPadding()
                            .fillMaxSize()
                    ) {
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.TopEnd
                        ) {
                            when (currentStep) {
                                GenreTemplateStep.GENRE_SELECTION -> {
                                    GenreSelectionScreen(
                                        genres = viewModel.genres,
                                        selectedGenre = viewModel.selectedGenre.value,
                                        onGenreSelect = { viewModel.selectGenre(it) }
                                    )
                                }

                                GenreTemplateStep.PERSONALIZING -> {
                                    PersonalizingScreen()
                                }

                                GenreTemplateStep.TEMPLATE_PICK -> {
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
                                }
                            }
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

                                Image(
                                    painter = painterResource(R.drawable.img_bg_cta_onboard),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.matchParentSize()
                                )

                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(top = 10.dp, bottom = 12.dp)
                                ) {
                                    when (currentStep) {
                                        GenreTemplateStep.GENRE_SELECTION -> {
                                            OnboardingCtaButton(
                                                text = stringResource(R.string.onboarding_next),
                                                onClick = {
                                                    if (viewModel.isStep1Valid()) {
                                                        Analytics.track(
                                                            name = "music_genre_next",
                                                            params = mapOf(
                                                                "genre" to (viewModel.selectedGenre.value?.displayName
                                                                    ?: "")
                                                            )
                                                        )
                                                        viewModel.goToStep2()
                                                    }
                                                },
                                                enabled = viewModel.isStep1Valid() && !viewModel.isTemplatesLoading.value,
                                                color = Primary,
                                                icon = R.drawable.ic_right_arrow
                                            )
                                        }

                                        GenreTemplateStep.TEMPLATE_PICK -> {
                                            OnboardingCtaButton(
                                                text = stringResource(R.string.onboarding_next),
                                                onClick = {
                                                    val template = viewModel.selectedTemplate.value
                                                        ?: return@OnboardingCtaButton
                                                    if (isSaving) return@OnboardingCtaButton
                                                    isSaving = true
                                                    Analytics.track(
                                                        name = "vibe_template_next",
                                                        params = mapOf("template_id" to template.id)
                                                    )
                                                    navigateToFeatureSelection()
                                                },
                                                enabled = viewModel.selectedTemplate.value != null && !isSaving,
                                                color = Primary,
                                                icon = R.drawable.ic_right_arrow
                                            )
                                        }

                                        else -> { /* No button during personalizing */
                                        }
                                    }
                                }
                            }
                        }

                        val adPlacement = when (currentStep) {
                            GenreTemplateStep.GENRE_SELECTION -> AdPlacement.NATIVE_ONBOARDING_SELECT_MUSIC
                            GenreTemplateStep.PERSONALIZING -> AdPlacement.NATIVE_ONBOARDING_PERSONALIZING
                            GenreTemplateStep.TEMPLATE_PICK -> AdPlacement.NATIVE_ONBOARDING_SELECT_TPT
                        }
                        // key(adPlacement) forces NativeAdView to remount when placement changes,
                        // resetting its internal isAdLoaded/adRevision state. Without this, stale
                        // state from the previous step keeps the slot empty until the new ad loads.
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .onSizeChanged { size ->
                                    bottomSectionHeight = size.height
                                }
                        ) {
                            key(adPlacement) {
                                NativeAdView(
                                    placement = adPlacement,
                                    modifier = Modifier.fillMaxWidth(),
                                    isDebug = BuildConfig.DEBUG,
                                    onAdClicked = { adClickDetector.onAdClick(it) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun navigateToFeatureSelection() {
        startActivity(Intent(this, com.videomaker.aimusic.modules.featureselection.FeatureSelectionActivity::class.java))
        finish()
    }
}
