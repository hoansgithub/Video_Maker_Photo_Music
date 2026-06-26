package com.videomaker.aimusic.modules.genretemplate

import android.os.Bundle
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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

class ContentExclusiveActivity : BaseOnboardingActivity() {

    override val onboardingStep = OnboardingStep.CONTENT_EXCLUSIVE

    private val viewModel: GenreTemplateViewModel by viewModel()
    private var sharedBottomHeight by mutableStateOf(0)

    override fun onSetupComplete(savedInstanceState: Bundle?) {
        Analytics.track(name = "content_feed_render")
    }

    @Composable
    override fun Content() {
        val placements = coordinator.adPlacements(onboardingStep!!)

        val stepContent: @Composable (
            onUserInteraction: () -> Unit,
            bottomPadding: Dp,
            buttonEnabled: Boolean,
        ) -> Unit = { onUserInteraction, bottomPadding, buttonEnabled ->
            Box(
                modifier = Modifier.fillMaxSize().statusBarsPadding(),
                contentAlignment = Alignment.TopEnd,
            ) {
                ContentExclusiveScreen(
                    selectedId = viewModel.selectedContentFilter.value.orEmpty(),
                    onSelect = { id ->
                        viewModel.selectContentFilter(id)
                        onUserInteraction()
                        Analytics.track(
                            name = "content_feed_select",
                            params = mapOf("option" to id),
                        )
                    },
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
                            text = stringResource(R.string.onboarding_next),
                            onClick = {
                                val selected = viewModel.selectedContentFilter.value
                                    ?: return@OnboardingCtaButton
                                Analytics.track(
                                    name = "content_feed_next",
                                    params = mapOf("option" to selected),
                                )
                                navigateToNextStep()
                            },
                            enabled = viewModel.selectedContentFilter.value != null && buttonEnabled,
                            color = Primary,
                            icon = R.drawable.ic_right_arrow
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
