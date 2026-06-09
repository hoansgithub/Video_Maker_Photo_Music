package com.videomaker.aimusic.modules.genretemplate

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.alcheclub.lib.acccore.ads.compose.NativeAdView
import com.videomaker.aimusic.BuildConfig
import com.videomaker.aimusic.R
import com.videomaker.aimusic.ui.components.LocalAsyncImage
import com.videomaker.aimusic.core.analytics.Analytics
import com.videomaker.aimusic.core.constants.AdPlacement
import com.videomaker.aimusic.modules.language.OnboardingCtaButton
import com.videomaker.aimusic.ui.components.ModifierExtension.clickableSingle
import com.videomaker.aimusic.ui.theme.Primary
import com.videomaker.aimusic.ui.theme.VideoMakerTheme
import kotlinx.coroutines.delay
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
            var bottomSectionHeight by remember { mutableStateOf(0) }
            val currentStep by viewModel.currentStep.collectAsStateWithLifecycle()

            // MEDIA_PRIVACY dual-ad swap: show the primary native until the user taps a privacy
            // option, then (after a 0.5s IAB viewability delay) swap to the ALT placement.
            var mediaPrivacyTapped by remember { mutableStateOf(false) }
            var mediaPrivacyAltActive by remember { mutableStateOf(false) }
            LaunchedEffect(mediaPrivacyTapped) {
                if (mediaPrivacyTapped && !mediaPrivacyAltActive) {
                    delay(500)
                    mediaPrivacyAltActive = true
                }
            }

            // CONTENT_EXCLUSIVE dual-ad swap: same behavior as MEDIA_PRIVACY.
            var contentExclusiveTapped by remember { mutableStateOf(false) }
            var contentExclusiveAltActive by remember { mutableStateOf(false) }
            LaunchedEffect(contentExclusiveTapped) {
                if (contentExclusiveTapped && !contentExclusiveAltActive) {
                    delay(500)
                    contentExclusiveAltActive = true
                }
            }

            LaunchedEffect(Unit) {
                viewModel.navToNext.collect { navigateToFeatureSelection() }
            }

            LaunchedEffect(currentStep) {
                when (currentStep) {
                    GenreTemplateStep.TEMPLATE_PICK -> Analytics.track(name = "vibe_template_render")
                    GenreTemplateStep.CONTENT_EXCLUSIVE -> Analytics.track(name = "content_feed_render")
                    GenreTemplateStep.MEDIA_PRIVACY -> Analytics.track(name = "privacy_render")
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

                                GenreTemplateStep.CONTENT_EXCLUSIVE -> {
                                    ContentExclusiveScreen(
                                        selectedId = viewModel.selectedContentFilter.value.orEmpty(),
                                        onSelect = { id ->
                                            viewModel.selectContentFilter(id)
                                            contentExclusiveTapped = true
                                            Analytics.track(
                                                name = "content_feed_select",
                                                params = mapOf("option" to id),
                                            )
                                        },
                                    )
                                }

                                GenreTemplateStep.MEDIA_PRIVACY -> {
                                    MediaPrivacyScreen(
                                        selectedId = viewModel.selectedPrivacy.value.orEmpty(),
                                        onSelect = { id ->
                                            viewModel.selectPrivacy(id)
                                            mediaPrivacyTapped = true
                                            Analytics.track(
                                                name = "privacy_select",
                                                params = mapOf("option" to id),
                                            )
                                        },
                                    )
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
                                                        viewModel.onGenreNext()
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
                                                    Analytics.track(
                                                        name = "vibe_template_next",
                                                        params = mapOf("template_id" to template.id)
                                                    )
                                                    viewModel.onTemplateNext()
                                                },
                                                enabled = viewModel.selectedTemplate.value != null,
                                                color = Primary,
                                                icon = R.drawable.ic_right_arrow
                                            )
                                        }

                                        GenreTemplateStep.CONTENT_EXCLUSIVE -> {
                                            OnboardingCtaButton(
                                                text = stringResource(R.string.onboarding_next),
                                                onClick = {
                                                    val selected = viewModel.selectedContentFilter.value
                                                        ?: return@OnboardingCtaButton
                                                    Analytics.track(
                                                        name = "content_feed_next",
                                                        params = mapOf("option" to selected),
                                                    )
                                                    viewModel.onContentExclusiveNext()
                                                },
                                                enabled = viewModel.selectedContentFilter.value != null,
                                                color = Primary,
                                                icon = R.drawable.ic_right_arrow
                                            )
                                        }

                                        GenreTemplateStep.MEDIA_PRIVACY -> {
                                            OnboardingCtaButton(
                                                text = stringResource(R.string.onboarding_next),
                                                onClick = {
                                                    val selected = viewModel.selectedPrivacy.value
                                                        ?: return@OnboardingCtaButton
                                                    Analytics.track(
                                                        name = "privacy_next",
                                                        params = mapOf("option" to selected),
                                                    )
                                                    viewModel.onMediaPrivacyNext()
                                                },
                                                enabled = viewModel.selectedPrivacy.value != null,
                                                color = Primary,
                                                icon = R.drawable.ic_right_arrow
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        val adPlacement = when (currentStep) {
                            GenreTemplateStep.GENRE_SELECTION -> AdPlacement.NATIVE_ONBOARDING_SELECT_MUSIC
                            GenreTemplateStep.TEMPLATE_PICK -> AdPlacement.NATIVE_ONBOARDING_SELECT_TPT
                            GenreTemplateStep.CONTENT_EXCLUSIVE ->
                                if (contentExclusiveAltActive) AdPlacement.NATIVE_ONBOARDING_CONTENT_EXCLUSIVE_ALT
                                else AdPlacement.NATIVE_ONBOARDING_CONTENT_EXCLUSIVE
                            GenreTemplateStep.MEDIA_PRIVACY ->
                                if (mediaPrivacyAltActive) AdPlacement.NATIVE_ONBOARDING_MEDIA_PRIVACY_ALT
                                else AdPlacement.NATIVE_ONBOARDING_MEDIA_PRIVACY
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
