package com.videomaker.aimusic.modules.onboardingsurvey

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseInOutCubic
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.alcheclub.lib.acccore.remoteconfig.RemoteConfig
import com.videomaker.aimusic.R
import com.videomaker.aimusic.core.data.local.PreferencesManager
import com.videomaker.aimusic.core.analytics.Analytics
import com.videomaker.aimusic.core.analytics.AnalyticsEvent
import com.videomaker.aimusic.core.constants.RemoteConfigKeys
import com.videomaker.aimusic.core.ui.BaseOnboardingActivity
import com.videomaker.aimusic.modules.language.OnboardingCtaButton
import com.videomaker.aimusic.modules.onboarding.OnboardingAltScreen
import com.videomaker.aimusic.modules.onboarding.OnboardingNormalScreen
import com.videomaker.aimusic.modules.onboarding.OnboardingStep
import com.videomaker.aimusic.ui.components.LocalAsyncImage
import com.videomaker.aimusic.ui.components.ModifierExtension.clickableSingle
import com.videomaker.aimusic.ui.theme.Primary
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import kotlin.math.roundToInt

class SurveyFeatureActivity : BaseOnboardingActivity() {

    override val onboardingStep = OnboardingStep.SURVEY_FEATURE

    private val viewModel: OnboardingSurveyViewModel by viewModel()
    private val remoteConfig: RemoteConfig by inject()
    private val preferencesManager: PreferencesManager by inject()
    private var sharedBottomHeight by mutableStateOf(0)

    @Composable
    override fun Content() {
        val config = remember { FEATURE_CONFIG }
        val selectedIds by viewModel.selectedFlow(OnboardingSurveyStep.FEATURE).collectAsStateWithLifecycle()
        val placements = coordinator.adPlacements(onboardingStep!!)

        // --- RC sort ---
        val sortedItems = remember {
            val raw = remoteConfig.getString(RemoteConfigKeys.ONBOARDING_FEATURE_SURVEY_ORDER)
            sortItemsByRcOrder(config.items, raw)
        }
        val displayConfig = remember(sortedItems) {
            if (sortedItems === config.items) config
            else config.copy(items = sortedItems)
        }

        // --- Cursor overlay state ---
        var interactionKey by remember { mutableStateOf(0L) }
        var showCursor by remember { mutableStateOf(false) }
        val itemOffsets = remember { mutableStateMapOf<String, Offset>() }
        var ctaButtonOffset by remember { mutableStateOf(Offset.Zero) }
        val listState = rememberLazyListState()

        // Render event
        LaunchedEffect(Unit) {
            Analytics.track(name = config.eventRender)
        }

        // Reset to primary ad when all selections are cleared
        LaunchedEffect(selectedIds.isEmpty()) {
            if (selectedIds.isEmpty() && showAlt) {
                resetToNormal()
            }
        }

        // Cursor: show after 2s idle
        LaunchedEffect(interactionKey) {
            delay(2_000)
            showCursor = true
        }

        // 7-second auto-select
        LaunchedEffect(interactionKey, selectedIds.isEmpty(), listState.isScrollInProgress) {
            if (selectedIds.isEmpty() && !listState.isScrollInProgress) {
                delay(7_000)
                val firstId = sortedItems.firstOrNull()?.id ?: return@LaunchedEffect
                if (viewModel.autoSelectFirst(OnboardingSurveyStep.FEATURE, firstId)) {
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

        // Ad misclick recovery: auto-select on resume after pause
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
                            if (viewModel.autoSelectFirst(OnboardingSurveyStep.FEATURE, firstId)) {
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

        val stepContent: @Composable (
            onUserInteraction: () -> Unit,
            bottomPadding: Dp,
            buttonEnabled: Boolean,
        ) -> Unit = { onUserInteraction, bottomPadding, buttonEnabled ->
            Box(Modifier.fillMaxSize().statusBarsPadding()) {
                OnboardingSurveyList(
                    config = displayConfig,
                    selectedIds = selectedIds,
                    listState = listState,
                    onToggle = { id ->
                        interactionKey = System.currentTimeMillis()
                        showCursor = false
                        viewModel.toggle(OnboardingSurveyStep.FEATURE, id)
                        onUserInteraction()
                        Analytics.track(
                            name = config.eventSelect,
                            params = mapOf(config.paramKey to id),
                        )
                    },
                    onItemPositioned = { id, offset ->
                        itemOffsets[id] = offset
                    },
                )

                // CTA button
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomEnd)
                        .then(
                            if (bottomPadding == 0.dp) Modifier.navigationBarsPadding()
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
                            .onGloballyPositioned { coords ->
                                val topLeft = coords.positionInRoot()
                                ctaButtonOffset = topLeft + Offset(coords.size.width / 2f, 0f)
                            }
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
                                preferencesManager.setPreferredFeatures(selectedIds.toList())
                                navigateToNextStep()
                            },
                            enabled = selectedIds.isNotEmpty() && buttonEnabled,
                            color = Primary,
                            icon = R.drawable.ic_right_arrow,
                        )
                    }
                }
            }
        }

        // Root Box: idle detector resets cursor on touch
        Box(
            modifier = Modifier
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
        ) {
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

            // Cursor overlay
            val firstItemOffset = sortedItems.firstOrNull()?.id?.let { itemOffsets[it] }
            CursorOverlay(
                visible = showCursor && firstItemOffset != null && ctaButtonOffset != Offset.Zero,
                hasSelection = selectedIds.isNotEmpty(),
                firstItemOffset = firstItemOffset ?: Offset.Zero,
                ctaButtonOffset = ctaButtonOffset,
            )
        }
    }

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
                cursorAnim.snapTo(Offset(target.x, target.y - with(density) { 48.dp.toPx() }))
                cursorAnim.animateTo(target, animationSpec = tween(450, easing = EaseInOutCubic))
                while (true) {
                    cursorAnim.animateTo(target + Offset(0f, bouncePx), animationSpec = tween(150))
                    cursorAnim.animateTo(target, animationSpec = tween(150))
                    delay(300)
                }
            }

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
        val rcSet = order.toSet()
        for (item in items) {
            if (item.id !in rcSet) sorted.add(item)
        }
        return sorted
    }
}
