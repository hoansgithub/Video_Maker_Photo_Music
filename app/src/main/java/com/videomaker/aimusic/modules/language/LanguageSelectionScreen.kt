package com.videomaker.aimusic.modules.language

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseInOutCubic
import androidx.compose.animation.core.EaseInCubic
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.paint
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import com.videomaker.aimusic.R
import com.videomaker.aimusic.core.analytics.Analytics
import com.videomaker.aimusic.core.data.local.LanguageManager
import com.videomaker.aimusic.core.data.local.SupportedLanguage
import com.videomaker.aimusic.core.data.local.getAllLanguages
import com.videomaker.aimusic.core.language.LanguageConfigService
import com.videomaker.aimusic.ui.components.ModifierExtension.clickableSingle
import org.koin.compose.koinInject
import com.videomaker.aimusic.ui.theme.VideoMakerTheme
import com.videomaker.aimusic.ui.theme.Black12
import com.videomaker.aimusic.ui.theme.Black20
import com.videomaker.aimusic.ui.theme.Gray700
import com.videomaker.aimusic.ui.theme.Primary
import com.videomaker.aimusic.ui.theme.White20
import com.videomaker.aimusic.ui.theme.White40
import co.alcheclub.lib.acccore.ads.compose.NativeAdView
import co.alcheclub.lib.acccore.ads.loader.AdsLoaderService
import com.videomaker.aimusic.BuildConfig
import com.videomaker.aimusic.core.constants.AdPlacement
import com.videomaker.aimusic.modules.featureselection.EVENT_GENRE_SHOW
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * LanguageSelectionScreen - Language picker for onboarding and settings.
 *
 * Styled to match standard onboarding elements:
 * - Background: colorScheme.background
 * - CTA: glass capsule (primaryContainer bg + White40 border)
 * - Language cards: flat dark style matching feature cards
 */
@Composable
fun LanguageSelectionScreen(
    onLanguageSelected: (String) -> Unit,
    onContinue: () -> Unit,
    showBackButton: Boolean = false,
    onBackClick: () -> Unit = {},
    languageConfigService: LanguageConfigService = koinInject()
) {
    LaunchedEffect(Unit) {
        Analytics.track(name = "language_show")
    }
    val density = LocalDensity.current
    var selectedLanguage by remember { mutableStateOf<String?>(null) }

    // Get sorted languages (country-based priority)
    val languages = remember { languageConfigService.getSortedLanguages() }

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

    // Cursor hint: idle timer key and visibility
    var interactionKey by remember { mutableStateOf(0L) }
    var showCursor by remember { mutableStateOf(false) }
    val languageCardOffsets = remember { mutableStateMapOf<String, Offset>() }
    var ctaButtonOffset by remember { mutableStateOf(Offset.Zero) }

    // Sequential delays ensuring EACH ad gets at least 0.5 second of display time
    // Timer starts on FIRST selection and does NOT reset on subsequent selections
    LaunchedEffect(hasStartedDelay) {
        if (hasStartedDelay) {
            // Step 1: Wait 0.5s from FIRST interaction before switching to ALT ad
            // NATIVE_ONBOARDING_LANGUAGE (PRIMARY) gets guaranteed 0.5s visibility
            delay(500)
            delayedHasSelection = true

            // Step 2: Wait another 0.5s before enabling button
            // NATIVE_ONBOARDING_LANGUAGE_ALT gets guaranteed 0.5s visibility
            delay(500)
            delayedButtonEnabled = true
        }
    }

    // Watch for first interaction and reset when deselected
    LaunchedEffect(selectedLanguage) {
        if (selectedLanguage != null && !hasStartedDelay) {
            // First interaction - start the timer (only happens once)
            hasStartedDelay = true
            android.util.Log.d("LanguageSelection", "🎬 Started IAB viewability timer")
        } else if (selectedLanguage == null && hasStartedDelay) {
            // User deselected - reset everything
            hasStartedDelay = false
            delayedHasSelection = false
            delayedButtonEnabled = false
            android.util.Log.d("LanguageSelection", "🔄 Reset IAB viewability timer")
        }
    }

    // Show cursor after 2s of no user interaction. Resets on every touch.
    LaunchedEffect(interactionKey) {
        delay(2_000)
        showCursor = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1A1A1A))
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(
                    WindowInsetsSides.Horizontal + WindowInsetsSides.Top
                )
            )
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
        // Scrollable content with dynamic bottom padding
        Column(
            modifier = Modifier
                .fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp)
                        .padding(
                            top = 16.dp,
                            bottom = bottomPaddingDp + 24.dp  // Dynamic padding based on measured bottom section height
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (showBackButton) {
                        // Top bar with back button (left) and done button (right)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = onBackClick) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = stringResource(R.string.back),
                                    tint = MaterialTheme.colorScheme.onBackground
                                )
                            }

                            // Done button in top right
                            Box(
                                modifier = Modifier.onGloballyPositioned { coords ->
                                    val topLeft = coords.positionInRoot()
                                    ctaButtonOffset = topLeft + Offset(coords.size.width / 2f, 0f)
                                }
                            ) {
                                OnboardingCtaButtonV1(
                                    text = stringResource(R.string.done),
                                    onClick = {
                                        onContinue.invoke()
                                        selectedLanguage?.let {
                                            Analytics.track(
                                                name = "language_next",
                                                params = mapOf(
                                                    "language" to it
                                                )
                                            )
                                        }
                                    },
                                    color = Primary,
                                    enabled = selectedLanguage != null && delayedButtonEnabled
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    } else {
                        Spacer(modifier = Modifier.height(25.dp))
                    }

                    Text(
                        text = stringResource(R.string.language_select_title),
                        fontSize = 32.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = stringResource(R.string.language_select_subtitle),
                        fontSize = 17.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(36.dp))

                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        languages.forEach { language ->
                            LanguageCard(
                                language = language,
                                isSelected = selectedLanguage == language.code,
                                modifier = Modifier.onGloballyPositioned { coords ->
                                    val topLeft = coords.positionInRoot()
                                    languageCardOffsets[language.code] =
                                        topLeft + Offset(coords.size.width / 2f, 0f)
                                },
                                onClick = {
                                    selectedLanguage = language.code
                                    onLanguageSelected(language.code)
                                    Analytics.track(
                                        name = "language_select",
                                        params = mapOf(
                                            "language" to language.code
                                        )
                                    )
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                }
                // Top-right button for settings flow (outside bottom section)
                if (!showBackButton) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomEnd)
                            .paint(
                                painter = painterResource(R.drawable.img_bg_cta_onboard),
                                contentScale = ContentScale.Crop
                            )
                            .then(
                                if (bottomSectionHeight == 0) Modifier.navigationBarsPadding()
                                else Modifier
                            )
                            .clickableSingle{}
                    ) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(18.dp)
                                .onGloballyPositioned { coords ->
                                    val topLeft = coords.positionInRoot()
                                    ctaButtonOffset = topLeft + Offset(coords.size.width / 2f, 0f)
                                }
                        ) {
                            OnboardingCtaButton(
                                text = stringResource(R.string.onboarding_next),
                                icon = R.drawable.ic_right_arrow,
                                color = Primary,
                                onClick = {
                                    onContinue.invoke()
                                    selectedLanguage?.let {
                                        Analytics.track(
                                            name = "language_next",
                                            params = mapOf(
                                                "language" to it
                                            )
                                        )
                                    }
                                },
                                enabled = selectedLanguage != null && delayedButtonEnabled
                            )
                        }
                    }
                }
            }

            // Bottom section: Native ad only (button moved to top right for showBackButton=true)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .onSizeChanged { size ->
                        bottomSectionHeight = size.height  // Measure actual height dynamically!
                    }
            ) {
                // ALT ad - bottom layer, always at full opacity
                NativeAdView(
                    placement = AdPlacement.NATIVE_ONBOARDING_LANGUAGE_ALT,
                    modifier = Modifier.fillMaxWidth(),
                    isDebug = BuildConfig.DEBUG
                )

                // PRIMARY ad - top layer, fades out when user selects
                NativeAdView(
                    placement = AdPlacement.NATIVE_ONBOARDING_LANGUAGE,
                    modifier = Modifier
                        .fillMaxWidth()
                        .alpha(if (delayedHasSelection) 0f else 1f),
                    isDebug = BuildConfig.DEBUG
                )
            }
        }

        CursorOverlay(
            visible = showCursor,
            selectedLanguage = selectedLanguage,
            languageCardOffsets = languageCardOffsets,
            ctaButtonOffset = ctaButtonOffset,
            onSelectLanguage = { code ->
                selectedLanguage = code
                onLanguageSelected(code)
            }
        )
    }
}

// ============================================
// LANGUAGE CARD — flat dark style
// ============================================

@Composable
private fun LanguageCard(
    language: SupportedLanguage,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cardShape = RoundedCornerShape(50)
    val accentColor = MaterialTheme.colorScheme.primary

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(if (isSelected) accentColor.copy(alpha = 0.15f) else Black20)
            .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = if (isSelected) accentColor else Gray700,
                shape = cardShape
            )
            .clickableSingle(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 18.dp)
    ) {
        Text(
            text = language.flag,
            fontSize = 20.sp,
            modifier = Modifier.padding(end = 14.dp)
        )

        Text(
            text = language.displayName,
            fontSize = 16.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f)
        )

        if (isSelected) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(accentColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(14.dp)
                )
            }
        } else {
            Spacer(modifier = Modifier.width(22.dp))
        }
    }
}

// ============================================
// SHARED CTA BUTTON — iOS glassmorphism style
//
// Layers (inside capsule clip):
//   1. Base fill      — primaryContainer (~10% white)
//   2. Top highlight  — White40 → transparent over top 45% (specular reflection)
//   3. Bottom shadow  — transparent → Black12 over bottom 35% (depth)
//   4. Outer stroke   — White20 border (surface edge)
// ============================================

@Composable
internal fun OnboardingCtaButton(
    text: String,
    onClick: () -> Unit,
    icon: Int? = null,
    color: Color = MaterialTheme.colorScheme.onBackground,
    enabled: Boolean = true
) {

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .clickableSingle(enabled = enabled, onClick = onClick)
            .alpha(if (enabled) 1f else 0.35f)
            .padding(horizontal = 20.dp)
    ) {
        Text(
            text = text,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = color
        )

        icon?.let {
            Icon(
                painter = painterResource(it),
                contentDescription = null,
                tint = color,
                modifier = Modifier
                    .size(20.dp)
            )
        }
    }
}

@Composable
internal fun OnboardingCtaButtonV1(
    text: String,
    onClick: () -> Unit,
    icon: Int? = null,
    color: Color = MaterialTheme.colorScheme.onBackground,
    enabled: Boolean = true
) {
    val shape = RoundedCornerShape(50)
    val baseColor = MaterialTheme.colorScheme.primaryContainer

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .alpha(if (enabled) 1f else 0.35f)
            .clip(shape)
            .drawBehind {
                // 1. Base glass fill
                drawRect(baseColor)
                // 2. Top inner highlight — light catching the top of the glass
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(White40, Color.Transparent),
                        startY = 0f,
                        endY = size.height * 0.45f
                    )
                )
                // 3. Bottom inner shadow — subtle depth below the glass
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Black12),
                        startY = size.height * 0.65f,
                        endY = size.height
                    )
                )
            }
            .border(1.dp, White20, shape)
            .clickableSingle(enabled = enabled, onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 20.dp)
    ) {
        Text(
            text = text,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = color
        )

        icon?.let {
            Icon(
                painter = painterResource(it),
                contentDescription = null,
                tint = color,
                modifier = Modifier
                    .size(20.dp)
            )
        }
    }
}

@Composable
internal fun OnboardingCtaMaxWidthButton(
    text: String,
    onClick: () -> Unit,
    icon: Int? = null,
    color: Color = MaterialTheme.colorScheme.onBackground,
    enabled: Boolean = true
) {
    val shape = RoundedCornerShape(50)
    val baseColor = MaterialTheme.colorScheme.primaryContainer

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .alpha(if (enabled) 1f else 0.35f)
            .clip(shape)
            .drawBehind {
                // 1. Base glass fill
                drawRect(baseColor)
                // 2. Top inner highlight — light catching the top of the glass
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(White40, Color.Transparent),
                        startY = 0f,
                        endY = size.height * 0.45f
                    )
                )
                // 3. Bottom inner shadow — subtle depth below the glass
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Black12),
                        startY = size.height * 0.65f,
                        endY = size.height
                    )
                )
            }
            .border(1.dp, White20, shape)
            .clickableSingle(enabled = enabled, onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 20.dp)
    ) {
        Text(
            text = text,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = color
        )

        icon?.let {
            Icon(
                painter = painterResource(it),
                contentDescription = null,
                tint = color,
                modifier = Modifier
                    .padding(start = 12.dp)
                    .size(20.dp)
            )
        }
    }
}

@Composable
private fun CursorOverlay(
    visible: Boolean,
    selectedLanguage: String?,
    languageCardOffsets: Map<String, Offset>,
    ctaButtonOffset: Offset,
    onSelectLanguage: (String) -> Unit
) {
    val density = LocalDensity.current
    val cursorAnim = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    var atCta by remember { mutableStateOf(false) }

    LaunchedEffect(visible) {
        if (!visible) return@LaunchedEffect
        if (languageCardOffsets.isEmpty() || ctaButtonOffset == Offset.Zero) return@LaunchedEffect

        val bouncePx = with(density) { 12.dp.toPx() }
        val hotspot = with(density) { Offset(8.dp.toPx(), 4.dp.toPx()) }
        // Mirrored hotspot for flipped cursor at CTA (image is 48dp wide, mirror x around center)
        val ctaHotspot = with(density) { Offset((65.dp).toPx(), 70.dp.toPx()) }

        atCta = false

        if (selectedLanguage == null) {
            val firstVisibleCode = languageCardOffsets
                .filter { (_, offset) -> offset.y >= 0f }
                .minByOrNull { it.value.y }
                ?.key

            if (firstVisibleCode != null) {
                val rawTarget = languageCardOffsets[firstVisibleCode] ?: return@LaunchedEffect
                val target = rawTarget - hotspot

                // Appear above target then slide in
                cursorAnim.snapTo(Offset(target.x, target.y - with(density) { 48.dp.toPx() }))
                cursorAnim.animateTo(target, animationSpec = tween(450, easing = EaseInOutCubic))

                // Single tap bounce
                cursorAnim.animateTo(target + Offset(0f, bouncePx), animationSpec = tween(150))
                cursorAnim.animateTo(target, animationSpec = tween(150))
                delay(300)

                onSelectLanguage(firstVisibleCode)
                delay(400)
            }
        }

        atCta = true
        val ctaTarget = ctaButtonOffset - ctaHotspot

        cursorAnim.animateTo(ctaTarget, animationSpec = tween(450, easing = EaseInOutCubic))

        // Loop bounce at CTA until user interacts (coroutine cancelled on showCursor=false)
        while (true) {
            cursorAnim.animateTo(ctaTarget + Offset(0f, bouncePx), animationSpec = tween(200))
            cursorAnim.animateTo(ctaTarget, animationSpec = tween(200))
            delay(600)
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(300)),
        exit = fadeOut(tween(200))
    ) {
        Image(
            painter = painterResource(if (atCta) R.drawable.img_hand_point_1 else R.drawable.img_hand_point),
            contentDescription = null,
            modifier = Modifier
                .size(48.dp)
                .offset {
                    IntOffset(
                        cursorAnim.value.x.roundToInt(),
                        cursorAnim.value.y.roundToInt()
                    )
                }
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun LanguageSelectionScreenPreview() {
    VideoMakerTheme {
        LanguageSelectionScreen(
            onLanguageSelected = {},
            onContinue = {}
        )
    }
}
