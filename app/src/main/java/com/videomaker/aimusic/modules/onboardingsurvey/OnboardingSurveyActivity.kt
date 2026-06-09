package com.videomaker.aimusic.modules.onboardingsurvey

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseInOutCubic
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.alcheclub.lib.acccore.ads.compose.NativeAdView
import co.alcheclub.lib.acccore.remoteconfig.RemoteConfig
import com.videomaker.aimusic.BuildConfig
import com.videomaker.aimusic.R
import com.videomaker.aimusic.ui.components.LocalAsyncImage
import com.videomaker.aimusic.VideoMakerApplication
import com.videomaker.aimusic.core.ads.AdClickDetector
import com.videomaker.aimusic.core.analytics.Analytics
import com.videomaker.aimusic.core.analytics.AnalyticsEvent
import com.videomaker.aimusic.core.constants.AdPlacement
import com.videomaker.aimusic.core.constants.RemoteConfigKeys
import com.videomaker.aimusic.modules.language.OnboardingCtaButton
import com.videomaker.aimusic.modules.onboarding.OnboardingActivity
import com.videomaker.aimusic.ui.components.ModifierExtension.clickableSingle
import com.videomaker.aimusic.ui.theme.Primary
import com.videomaker.aimusic.ui.theme.VideoMakerTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.compose.koinInject
import kotlin.math.roundToInt

class OnboardingSurveyActivity : AppCompatActivity() {

    private val viewModel: OnboardingSurveyViewModel by viewModel()
    private val onboardingMusicPlayer: com.videomaker.aimusic.core.playback.OnboardingMusicPlayer by inject()
    private val remoteConfig: RemoteConfig by inject()

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
                var showExitDialog by remember { mutableStateOf(false) }

                BackHandler { showExitDialog = true }

                if (showExitDialog) {
                    com.videomaker.aimusic.ui.components.RetentionDialog(
                        onClose = { finish() },
                        onStay = { showExitDialog = false }
                    )
                }

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
        if (step == OnboardingSurveyStep.AI_LEVEL) {
            AiLevelStep(adClickDetector = adClickDetector)
            return
        }
        val config = remember(step) { configFor(step) }
        val density = LocalDensity.current
        val scope = rememberCoroutineScope()
        val selectedIds by viewModel.selectedFlow(step).collectAsStateWithLifecycle()

        val isFeatureStep = step == OnboardingSurveyStep.FEATURE

        // --- RC sort (FEATURE only) ---
        val sortedItems = remember(step) {
            if (!isFeatureStep) return@remember config.items
            val raw = remoteConfig.getString(RemoteConfigKeys.ONBOARDING_FEATURE_SURVEY_ORDER)
            sortItemsByRcOrder(config.items, raw)
        }
        // Use a config copy with sorted items for display.
        val displayConfig = remember(sortedItems) {
            if (sortedItems === config.items) config
            else config.copy(items = sortedItems)
        }

        var bottomSectionHeight by remember { mutableStateOf(0) }
        val bottomPaddingDp = with(density) { bottomSectionHeight.toDp() }

        // --- IAB viewability delay ---
        var hasStartedDelay by remember { mutableStateOf(false) }
        var delayedHasSelection by remember { mutableStateOf(false) }
        var delayedButtonEnabled by remember { mutableStateOf(false) }

        // PLATFORM step: reload-on-selection state (existing behavior).
        var adReloadKey by remember { mutableIntStateOf(0) }
        var reloadJob by remember { mutableStateOf<Job?>(null) }

        // --- Cursor overlay state (FEATURE only) ---
        var interactionKey by remember { mutableStateOf(0L) }
        var showCursor by remember { mutableStateOf(false) }
        val itemOffsets = remember { mutableStateMapOf<String, Offset>() }
        var ctaButtonOffset by remember { mutableStateOf(Offset.Zero) }

        val listState = rememberLazyListState()

        // Render event once per screen.
        LaunchedEffect(step) {
            Analytics.track(name = config.eventRender)
        }

        // --- IAB viewability timer ---
        if (isFeatureStep) {
            // FEATURE: dual-ad pipeline (PRIMARY 0.5s → ALT 0.5s → button enables)
            LaunchedEffect(hasStartedDelay) {
                if (hasStartedDelay) {
                    delay(500)
                    delayedHasSelection = true
                    delay(500)
                    delayedButtonEnabled = true
                }
            }
        } else {
            // PLATFORM: single 0.5s delay then button enables (existing behavior).
            LaunchedEffect(hasStartedDelay) {
                if (hasStartedDelay) {
                    delay(500)
                    delayedButtonEnabled = true
                }
            }
        }

        // Start/reset the viewability timer with the first/last selection.
        LaunchedEffect(selectedIds.isEmpty()) {
            if (selectedIds.isNotEmpty() && !hasStartedDelay) {
                hasStartedDelay = true
            } else if (selectedIds.isEmpty() && hasStartedDelay) {
                hasStartedDelay = false
                delayedHasSelection = false
                delayedButtonEnabled = false
            }
        }

        // --- Cursor: show after 2s idle (FEATURE only) ---
        if (isFeatureStep) {
            LaunchedEffect(interactionKey) {
                delay(2_000)
                showCursor = true
            }
        }

        // --- 7-second auto-select (FEATURE only) ---
        if (isFeatureStep) {
            LaunchedEffect(interactionKey, selectedIds.isEmpty(), listState.isScrollInProgress) {
                if (selectedIds.isEmpty() && !listState.isScrollInProgress) {
                    delay(7_000)
                    val firstId = sortedItems.firstOrNull()?.id ?: return@LaunchedEffect
                    if (viewModel.autoSelectFirst(step, firstId)) {
                        Analytics.track(
                            name = config.eventSelect,
                            params = mapOf(
                                config.paramKey to firstId,
                                AnalyticsEvent.Param.TRIGGER to AnalyticsEvent.Value.Trigger.IDLE_AUTO_SELECT,
                            ),
                        )
                    }
                }
            }
        }

        // --- Ad misclick recovery (FEATURE only): auto-select on resume after pause ---
        if (isFeatureStep) {
            var hasBeenPaused by remember { mutableStateOf(false) }
            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_PAUSE -> {
                            hasBeenPaused = true
                        }
                        Lifecycle.Event.ON_RESUME -> {
                            if (hasBeenPaused && selectedIds.isEmpty()) {
                                val firstId = sortedItems.firstOrNull()?.id ?: return@LifecycleEventObserver
                                if (viewModel.autoSelectFirst(step, firstId)) {
                                    Analytics.track(
                                        name = config.eventSelect,
                                        params = mapOf(
                                            config.paramKey to firstId,
                                            AnalyticsEvent.Param.TRIGGER to AnalyticsEvent.Value.Trigger.AD_RETURN_AUTO_SELECT,
                                        ),
                                    )
                                }
                            }
                        }
                        else -> {}
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }
        }

        // Root Box: idle detector resets cursor on touch (FEATURE only).
        val rootModifier = if (isFeatureStep) {
            Modifier
                .statusBarsPadding()
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            awaitPointerEvent(pass = PointerEventPass.Initial)
                            interactionKey = System.currentTimeMillis()
                            showCursor = false
                        }
                    }
                }
        } else {
            Modifier
                .statusBarsPadding()
                .fillMaxSize()
        }

        Box(modifier = rootModifier) {
            Column(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.weight(1f)) {
                    OnboardingSurveyList(
                        config = displayConfig,
                        selectedIds = selectedIds,
                        listState = listState,
                        onToggle = { id ->
                            // Reset idle timer on selection tap (FEATURE).
                            if (isFeatureStep) {
                                interactionKey = System.currentTimeMillis()
                                showCursor = false
                            }
                            val wasEmpty = selectedIds.isEmpty()
                            val nowSelected = viewModel.toggle(step, id)
                            Analytics.track(
                                name = config.eventSelect,
                                params = mapOf(config.paramKey to id),
                            )
                            // PLATFORM: reload ad from 2nd selection onward (existing behavior).
                            if (!isFeatureStep && nowSelected && !wasEmpty) {
                                reloadJob?.cancel()
                                reloadJob = scope.launch {
                                    if (VideoMakerApplication.preloadNativeAdSuspend(config.placement)) {
                                        adReloadKey++
                                    }
                                }
                            }
                        },
                        bottomPaddingDp = bottomPaddingDp,
                        onItemPositioned = if (isFeatureStep) { id, offset ->
                            itemOffsets[id] = offset
                        } else null,
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
                                .then(
                                    if (isFeatureStep) {
                                        Modifier.onGloballyPositioned { coords ->
                                            val topLeft = coords.positionInRoot()
                                            ctaButtonOffset = topLeft + Offset(coords.size.width / 2f, 0f)
                                        }
                                    } else Modifier
                                )
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

                // Bottom ad section.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = bottomPaddingDp)
                        .onSizeChanged { size ->
                            if (size.height > bottomSectionHeight) bottomSectionHeight = size.height
                        }
                ) {
                    if (isFeatureStep) {
                        // FEATURE: dual-ad swap (matches Language/Feature Selection pattern).
                        // if/else ensures only one NativeAdView is in composition at a time.
                        if (delayedHasSelection) {
                            NativeAdView(
                                placement = AdPlacement.NATIVE_ONBOARDING_SELECT_ALT,
                                modifier = Modifier.fillMaxWidth(),
                                isDebug = BuildConfig.DEBUG,
                                onAdClicked = { adClickDetector.onAdClick(it) },
                            )
                        } else {
                            NativeAdView(
                                placement = AdPlacement.NATIVE_ONBOARDING_SELECT,
                                modifier = Modifier.fillMaxWidth(),
                                isDebug = BuildConfig.DEBUG,
                                onAdClicked = { adClickDetector.onAdClick(it) },
                            )
                        }
                    } else {
                        // PLATFORM: single native with reload-on-selection (existing behavior).
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

            // --- Cursor overlay (FEATURE only) ---
            if (isFeatureStep) {
                val firstItemOffset = sortedItems.firstOrNull()?.id?.let { itemOffsets[it] }
                CursorOverlay(
                    visible = showCursor && firstItemOffset != null && ctaButtonOffset != Offset.Zero,
                    hasSelection = selectedIds.isNotEmpty(),
                    firstItemOffset = firstItemOffset ?: Offset.Zero,
                    ctaButtonOffset = ctaButtonOffset,
                )
            }
        }

        // 1-step-ahead: preload the next step's ads when arriving at the current step.
        LaunchedEffect(step) {
            val enabled = viewModel.enabledSteps
            val isLastStep = enabled.lastOrNull() == step

            when (step) {
                OnboardingSurveyStep.FEATURE -> {
                    when {
                        OnboardingSurveyStep.PLATFORM in enabled ->
                            VideoMakerApplication.preloadNativeAd(AdPlacement.NATIVE_ONBOARDING_SOCIAL)
                        OnboardingSurveyStep.AI_LEVEL in enabled -> {
                            VideoMakerApplication.preloadNativeAd(AdPlacement.NATIVE_ONBOARDING_AI_LEVEL)
                            VideoMakerApplication.preloadNativeAdDelayed(AdPlacement.NATIVE_ONBOARDING_AI_LEVEL_ALT, 1000L)
                        }
                    }
                }
                OnboardingSurveyStep.PLATFORM -> {
                    if (OnboardingSurveyStep.AI_LEVEL in enabled) {
                        VideoMakerApplication.preloadNativeAd(AdPlacement.NATIVE_ONBOARDING_AI_LEVEL)
                        VideoMakerApplication.preloadNativeAdDelayed(AdPlacement.NATIVE_ONBOARDING_AI_LEVEL_ALT, 1000L)
                    }
                }
                OnboardingSurveyStep.AI_LEVEL -> { /* PAGE1/PAGE2 handled below */ }
            }

            // Last step → preload welcome pager (next screen)
            if (isLastStep) {
                VideoMakerApplication.preloadNativeAd(AdPlacement.NATIVE_ONBOARDING_PAGE1)
                VideoMakerApplication.preloadNativeAd(AdPlacement.NATIVE_ONBOARDING_PAGE2)
            }
        }
    }

    @Composable
    private fun AiLevelStep(adClickDetector: AdClickDetector) {
        val density = LocalDensity.current
        val selectedIds by viewModel.selectedAiLevel.collectAsStateWithLifecycle()
        // No pre-selection: empty string means nothing is selected (no item highlighted).
        val selectedId = selectedIds.firstOrNull().orEmpty()

        var bottomSectionHeight by remember { mutableStateOf(0) }
        val bottomPaddingDp = with(density) { bottomSectionHeight.toDp() }

        // Dual-ad swap: show the primary native until the user taps a selection, then
        // (after a 0.5s IAB viewability delay) swap to the ALT placement.
        var aiLevelTapped by remember { mutableStateOf(false) }
        var aiLevelAltActive by remember { mutableStateOf(false) }
        LaunchedEffect(aiLevelTapped) {
            if (aiLevelTapped && !aiLevelAltActive) {
                delay(500)
                aiLevelAltActive = true
            }
        }

        LaunchedEffect(Unit) {
            Analytics.track(name = OnboardingSurveyAnalytics.EVENT_AI_LEVEL_RENDER)
        }

        Column(
            modifier = Modifier
                .statusBarsPadding()
                .fillMaxSize()
        ) {
            Box(modifier = Modifier.weight(1f)) {
                AiLevelScreen(
                    items = AI_LEVEL_ITEMS,
                    selectedId = selectedId,
                    onSelect = { id ->
                        viewModel.selectAiLevel(id)
                        aiLevelTapped = true
                        Analytics.track(
                            name = OnboardingSurveyAnalytics.EVENT_AI_LEVEL_SELECT,
                            params = mapOf(OnboardingSurveyAnalytics.PARAM_AI_LEVEL to id),
                        )
                    },
                    bottomPaddingDp = bottomPaddingDp,
                )

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
                                    name = OnboardingSurveyAnalytics.EVENT_AI_LEVEL_NEXT,
                                    params = mapOf(OnboardingSurveyAnalytics.PARAM_AI_LEVEL to selectedId),
                                )
                                viewModel.onNext()
                            },
                            enabled = selectedIds.isNotEmpty(),
                            color = Primary,
                            icon = R.drawable.ic_right_arrow,
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = bottomPaddingDp)
                    .onSizeChanged { size ->
                        if (size.height > bottomSectionHeight) bottomSectionHeight = size.height
                    }
            ) {
                val aiLevelPlacement =
                    if (aiLevelAltActive) AdPlacement.NATIVE_ONBOARDING_AI_LEVEL_ALT
                    else AdPlacement.NATIVE_ONBOARDING_AI_LEVEL
                // key(placement) remounts NativeAdView on swap so it resets its load state.
                key(aiLevelPlacement) {
                    NativeAdView(
                        placement = aiLevelPlacement,
                        modifier = Modifier.fillMaxWidth(),
                        isDebug = BuildConfig.DEBUG,
                        onAdClicked = { adClickDetector.onAdClick(it) }
                    )
                }
            }
        }
    }

    // ============================================================
    // CursorOverlay — idle hand-cursor pointing at first item / CTA
    // ============================================================

    @Composable
    private fun CursorOverlay(
        visible: Boolean,
        hasSelection: Boolean,
        firstItemOffset: Offset,
        ctaButtonOffset: Offset,
    ) {
        val density = LocalDensity.current
        val cursorAnim = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
        var atCta by remember { mutableStateOf(false) }

        LaunchedEffect(visible) {
            if (!visible) return@LaunchedEffect

            val bouncePx = with(density) { 12.dp.toPx() }
            val hotspot = with(density) { Offset(8.dp.toPx(), 4.dp.toPx()) }
            val ctaHotspot = with(density) { Offset(65.dp.toPx(), 70.dp.toPx()) }

            atCta = false

            if (!hasSelection) {
                val target = firstItemOffset - hotspot

                // Appear above target then slide in.
                cursorAnim.snapTo(Offset(target.x, target.y - with(density) { 48.dp.toPx() }))
                cursorAnim.animateTo(target, animationSpec = tween(450, easing = EaseInOutCubic))

                // Bounce loop until cancelled (user interacts or selection made).
                while (true) {
                    cursorAnim.animateTo(target + Offset(0f, bouncePx), animationSpec = tween(150))
                    cursorAnim.animateTo(target, animationSpec = tween(150))
                    delay(300)
                }
            }

            // Has selection → point at CTA.
            atCta = true
            val ctaTarget = ctaButtonOffset - ctaHotspot
            cursorAnim.animateTo(ctaTarget, animationSpec = tween(450, easing = EaseInOutCubic))

            while (true) {
                cursorAnim.animateTo(ctaTarget + Offset(0f, bouncePx), animationSpec = tween(200))
                cursorAnim.animateTo(ctaTarget, animationSpec = tween(200))
                delay(600)
            }
        }

        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(300)),
            exit = fadeOut(tween(200)),
        ) {
            Image(
                painter = painterResource(if (atCta) R.drawable.img_hand_point_1 else R.drawable.img_hand_point),
                contentDescription = null,
                modifier = Modifier
                    .size(48.dp)
                    .offset {
                        IntOffset(
                            cursorAnim.value.x.roundToInt(),
                            cursorAnim.value.y.roundToInt(),
                        )
                    },
            )
        }
    }

    // ============================================================
    // RC sort helper
    // ============================================================

    private fun sortItemsByRcOrder(items: List<SurveyItem>, rcJson: String): List<SurveyItem> {
        if (rcJson.isBlank()) return items
        val order: List<String> = try {
            val arr = JSONArray(rcJson)
            (0 until arr.length()).map { arr.getString(it) }
        } catch (_: Exception) {
            return items
        }
        if (order.isEmpty()) return items

        val byId = items.associateBy { it.id }
        val sorted = mutableListOf<SurveyItem>()
        for (id in order) {
            byId[id]?.let { sorted.add(it) }
        }
        // Append remaining items not in the RC list, preserving hardcoded order.
        val rcSet = order.toSet()
        for (item in items) {
            if (item.id !in rcSet) sorted.add(item)
        }
        return sorted
    }

    private fun navigateToOnboarding() {
        startActivity(Intent(this, OnboardingActivity::class.java))
        finish()
    }
}
