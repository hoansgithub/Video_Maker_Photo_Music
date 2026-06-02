package com.videomaker.aimusic.modules.onboardingsurvey

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.alcheclub.lib.acccore.ads.compose.NativeAdView
import com.videomaker.aimusic.BuildConfig
import com.videomaker.aimusic.R
import com.videomaker.aimusic.VideoMakerApplication
import com.videomaker.aimusic.core.analytics.Analytics
import com.videomaker.aimusic.core.constants.AdPlacement
import com.videomaker.aimusic.modules.language.OnboardingCtaButton
import com.videomaker.aimusic.modules.onboarding.OnboardingActivity
import com.videomaker.aimusic.ui.components.ModifierExtension.clickableSingle
import com.videomaker.aimusic.ui.theme.Primary
import com.videomaker.aimusic.ui.theme.VideoMakerTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import com.videomaker.aimusic.core.ads.AdClickDetector
import org.koin.compose.koinInject

class OnboardingSurveyActivity : AppCompatActivity() {

    private val viewModel: OnboardingSurveyViewModel by viewModel()
    private val onboardingMusicPlayer: com.videomaker.aimusic.core.playback.OnboardingMusicPlayer by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Idempotent safety net in case the flow is entered here directly.
        onboardingMusicPlayer.start()

        setContent {
            val currentStep by viewModel.currentStep.collectAsStateWithLifecycle()

            LaunchedEffect(Unit) {
                viewModel.navToNext.collect { navigateToOnboarding() }
            }

            VideoMakerTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF1A1A1A))
                ) {
                    val step = currentStep
                    if (step != null) {
                        // key(step) resets per-screen UI/ad state when switching FEATURE <-> PLATFORM.
                        key(step) {
                            SurveyStep(step = step)
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun SurveyStep(step: OnboardingSurveyStep) {
        val adClickDetector: AdClickDetector = koinInject()
        val config = remember(step) { configFor(step) }
        val density = LocalDensity.current
        val scope = rememberCoroutineScope()
        val selectedIds by viewModel.selectedFlow(step).collectAsStateWithLifecycle()

        var bottomSectionHeight by remember { mutableStateOf(0) }
        val bottomPaddingDp = with(density) { bottomSectionHeight.toDp() }

        // IAB viewability: the native gets >= 0.5s before the Next button enables (after first selection).
        var hasStartedDelay by remember { mutableStateOf(false) }
        var delayedButtonEnabled by remember { mutableStateOf(false) }
        // Bumped after a fresh ad is ready to remount the native and surface the reloaded ad.
        var adReloadKey by remember { mutableIntStateOf(0) }
        // Tracks the in-flight reload so a rapid next tap cancels the previous one (latest wins).
        var reloadJob by remember { mutableStateOf<Job?>(null) }

        // Render event once per screen.
        LaunchedEffect(step) {
            Analytics.track(name = config.eventRender)
        }

        LaunchedEffect(hasStartedDelay) {
            if (hasStartedDelay) {
                delay(500)
                delayedButtonEnabled = true
            }
        }

        // Start/reset the viewability timer with the first/last selection.
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
        ) {
            Box(modifier = Modifier.weight(1f)) {
                OnboardingSurveyList(
                    config = config,
                    selectedIds = selectedIds,
                    onToggle = { id ->
                        val wasEmpty = selectedIds.isEmpty()
                        val nowSelected = viewModel.toggle(step, id)
                        // Select event fires on every tap (select or deselect) with the tapped id.
                        Analytics.track(
                            name = config.eventSelect,
                            params = mapOf(config.paramKey to id),
                        )
                        // Reload the native only from the 2nd selection onward — the first selection
                        // stays smooth (no remount). Await the fresh ad BEFORE remounting so the old
                        // ad stays visible until the new one is ready; cancel any in-flight reload so
                        // a rapid next tap supersedes it.
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

                // CTA button bottom-right over the gradient background image.
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
                    Image(
                        painter = painterResource(R.drawable.img_bg_cta_onboard),
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
                                viewModel.onNext()
                            },
                            enabled = selectedIds.isNotEmpty() && delayedButtonEnabled,
                            color = Primary,
                            icon = R.drawable.ic_right_arrow,
                        )
                    }
                }
            }

            // Bottom ad section: single native (high → all waterfall).
            // Reserve the last-known height (latch the max, never shrink) so a reload remount on the
            // 2nd+ selection can't collapse the slot and jolt the list/CTA — only the ad content
            // briefly refreshes inside the reserved space.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = bottomPaddingDp)
                    .onSizeChanged { size ->
                        if (size.height > bottomSectionHeight) bottomSectionHeight = size.height
                    }
            ) {
                key(adReloadKey) {
                    NativeAdView(
                        placement = config.placement,
                        modifier = Modifier.fillMaxWidth(),
                        isDebug = BuildConfig.DEBUG,
                        onAdClicked = { adClickDetector.onAdClick(it) }
                    )
                }
            }
        }

        // Preload the next screen's ads (1-step-ahead).
        LaunchedEffect(step) {
            when (step) {
                OnboardingSurveyStep.FEATURE -> {
                    if (OnboardingSurveyStep.PLATFORM in viewModel.enabledSteps) {
                        VideoMakerApplication.preloadNativeAd(AdPlacement.NATIVE_ONBOARDING_SOCIAL)
                    } else {
                        VideoMakerApplication.preloadNativeAd(AdPlacement.NATIVE_ONBOARDING_PAGE1)
                        VideoMakerApplication.preloadNativeAd(AdPlacement.NATIVE_ONBOARDING_PAGE2)
                    }
                }
                OnboardingSurveyStep.PLATFORM -> {
                    VideoMakerApplication.preloadNativeAd(AdPlacement.NATIVE_ONBOARDING_PAGE1)
                    VideoMakerApplication.preloadNativeAd(AdPlacement.NATIVE_ONBOARDING_PAGE2)
                }
            }
        }
    }

    private fun navigateToOnboarding() {
        startActivity(Intent(this, OnboardingActivity::class.java))
        finish()
    }
}
