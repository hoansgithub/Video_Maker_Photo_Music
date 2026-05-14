package com.videomaker.aimusic.modules.onboarding.pages

import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PaintingStyle.Companion.Stroke
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import co.alcheclub.lib.acccore.ads.compose.NativeAdView
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Precision
import coil.size.Size
import com.videomaker.aimusic.BuildConfig
import com.videomaker.aimusic.R
import com.videomaker.aimusic.core.constants.AdPlacement
import com.videomaker.aimusic.modules.language.OnboardingCtaButton
import com.videomaker.aimusic.ui.theme.FoundationBlack
import com.videomaker.aimusic.ui.theme.Primary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ============================================
// WELCOME PAGE TEMPLATE
// Dynamic layout with full-screen image and overlay:
// - Banner image fills entire area (scale aspect fill)
// - Dark gradient overlay at bottom for text readability
// - Title/Subtitle + CTA Button Row overlaid at bottom
// - Text limited to 2 lines max with ellipsis
// - Native ad at bottom (measured dynamically, pushes content up)
// ============================================

@Composable
internal fun WelcomePage(
    imageResId: Int,
    title: String,
    subtitle: String,
    ctaText: String,
    onCta: () -> Unit,
    pageIndex: Int = 0  // 0-based index for ad placement
) {
    // Map page index to ad placement
    val adPlacement = when (pageIndex) {
        0 -> AdPlacement.NATIVE_ONBOARDING_PAGE1
        1 -> AdPlacement.NATIVE_ONBOARDING_PAGE2
        2 -> AdPlacement.NATIVE_ONBOARDING_PAGE3
        else -> null
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Banner image — fills entire area with Crop
        Image(
            painter = painterResource(imageResId),
            contentDescription = null,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.5f),
            contentScale = ContentScale.Crop
        )
        // Title/Subtitle + CTA Button Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.0f),
                            Color.Black.copy(alpha = 0.5f),
                            Color.Black.copy(alpha = 0.75f),
                            Color.Black.copy(alpha = 1.0f)
                        )
                    )
                )
                .padding(horizontal = 24.dp)
                .padding(top = 40.dp, bottom = 32.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Title + Subtitle Column (left side)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 16.dp)
            ) {
                Text(
                    text = title,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = subtitle,
                    fontSize = 16.sp,
                    color = Color.White.copy(alpha = 0.9f),
                    lineHeight = 22.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // CTA Button (right side, vertically centered with title area)
            OnboardingCtaButton(
                text = ctaText,
                onClick = onCta,
                color = Primary,
                icon = R.drawable.ic_right_arrow
            )
        }

        // ── Native Ad at bottom (takes only needed height) ──────────────
        if (adPlacement != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
            ) {
                NativeAdView(
                    placement = adPlacement,
                    modifier = Modifier.fillMaxWidth(),
                    isDebug = BuildConfig.DEBUG
                )
            }
        }
    }
}

// ============================================
// DYNAMIC WELCOME PAGE (URL-based image)
// Same layout as WelcomePage but loads from URL with shimmer
// ============================================

@Composable
internal fun WelcomePageDynamic(
    thumbnailUrl: String?,
    localFallbackResId: Int,
    title: String,
    subtitle: String,
    ctaText: String,
    onCta: () -> Unit,
    pageIndex: Int = 0
) {
    val adPlacement = when (pageIndex) {
        0 -> AdPlacement.NATIVE_ONBOARDING_PAGE1
        1 -> AdPlacement.NATIVE_ONBOARDING_PAGE2
        2 -> AdPlacement.NATIVE_ONBOARDING_PAGE3
        else -> null
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.5f),
        ) {
            // Banner image — URL-based or local fallback
            if (thumbnailUrl != null) {
                if (pageIndex == 0) {
                    TemplateItem(
                        thumbnailPath = thumbnailUrl,
                        errorLocal = localFallbackResId,
                        onSuccess = {
                        }
                    )
                    Image(
                        painter = painterResource(R.drawable.img_bg_onboard_page1),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        alignment = Alignment.BottomCenter,
                        contentScale = ContentScale.Crop
                    )
                }

                if (pageIndex == 1){
                    Image(
                        painter = painterResource(R.drawable.img_bg_ob2),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        alignment = Alignment.TopCenter,
                        contentScale = ContentScale.Crop
                    )

                    val gradientBrush = Brush.linearGradient(
                        colors = listOf(
                            Color(0x29CCFF00),
                            Color(0x29F751C8)
                        ),
                        start = Offset(0f, 0f),
                        end = Offset(1000f, 100f) // nghiêng nhẹ ~95deg
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth(0.72f)
                            .aspectRatio(283.66f/148.1f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(gradientBrush)
                            .drawBehind {
                                drawRect(
                                    Color(0x52171717)
                                )
                            }
                            .drawBehind {
                                drawRect(Color(0x52171717))

                                drawRoundRect(
                                    brush = Brush.horizontalGradient(
                                        listOf(
                                            Color(0xFFCCFF00),
                                            Color(0xFFF751C8)
                                        )
                                    ),
                                    style = Stroke(1.57.dp.toPx()),
                                    cornerRadius = CornerRadius(16.dp.toPx())
                                )
                            }
                            .align(Alignment.Center),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(78f/283.66f)
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(10.dp))
                            ){
                                TemplateItem(
                                    thumbnailPath = thumbnailUrl,
                                    errorLocal = localFallbackResId,
                                    onSuccess = {

                                    }
                                )
                            }

                            Image(
                                painter = painterResource(R.drawable.img_text_ob2),
                                contentDescription = null,
                                modifier = Modifier
                                    .weight(1f),
                                contentScale = ContentScale.FillWidth
                            )
                        }

                        Spacer(Modifier.padding(horizontal = 12.dp).fillMaxWidth().height(2.dp).background(Color(0xff373737)))
                        Image(
                            painter = painterResource(R.drawable.img_slide_ob2),
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxWidth(),
                            contentScale = ContentScale.FillWidth
                        )
                    }
                }

            } else {
                Image(
                    painter = painterResource(localFallbackResId),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
        }

        // Title/Subtitle + CTA Button Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.0f),
                            Color.Black.copy(alpha = 0.5f),
                            Color.Black.copy(alpha = 0.75f),
                            Color.Black.copy(alpha = 1.0f)
                        )
                    )
                )
                .padding(horizontal = 24.dp)
                .padding(top = 40.dp, bottom = 32.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 16.dp)
            ) {
                Text(
                    text = title,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = subtitle,
                    fontSize = 16.sp,
                    color = Color.White.copy(alpha = 0.9f),
                    lineHeight = 22.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            OnboardingCtaButton(
                text = ctaText,
                onClick = onCta,
                color = Primary,
                icon = R.drawable.ic_right_arrow
            )
        }

        // Native Ad at bottom
        if (adPlacement != null) {
            Box(modifier = Modifier.fillMaxWidth()) {
                NativeAdView(
                    placement = adPlacement,
                    modifier = Modifier.fillMaxWidth(),
                    isDebug = BuildConfig.DEBUG
                )
            }
        }
    }
}

// ============================================
// DYNAMIC CAROUSEL (all geos)
// Auto-swipe carousel with URL-based thumbnails
// ============================================

@Composable
internal fun DynamicCarousel(
    thumbnailUrls: List<String>,
    localFallbackResIds: List<Int>,
    title: String,
    subtitle: String,
    ctaText: String,
    onCta: () -> Unit,
    pageIndex: Int = 0
) {
    val totalSlides = maxOf(1, thumbnailUrls.size + localFallbackResIds.size)
    val pagerState = rememberPagerState(pageCount = { totalSlides })

    val adPlacement = when (pageIndex) {
        0 -> AdPlacement.NATIVE_ONBOARDING_PAGE1
        1 -> AdPlacement.NATIVE_ONBOARDING_PAGE2
        2 -> AdPlacement.NATIVE_ONBOARDING_PAGE3
        else -> null
    }

    // Auto-swipe every 3 seconds (matches IndiaPage3Carousel pattern).
    // Key on totalSlides so the loop re-launches when content state arrives,
    // and guard against tight-loop ANR when there is only one slide.
    LaunchedEffect(totalSlides) {
        if (totalSlides <= 1) return@LaunchedEffect
        while (true) {
            delay(3000)
            val nextPage = (pagerState.currentPage + 1) % totalSlides
            pagerState.animateScrollToPage(nextPage)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.5f),
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                userScrollEnabled = false
            ) { page ->
                val url = thumbnailUrls.getOrNull(page)
                val localResId = if (url == null) {
                    val localIndex = page - thumbnailUrls.size
                    localFallbackResIds.getOrNull(localIndex)
                } else null

                if (url != null) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        val errorLocal = localFallbackResIds.firstOrNull()
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.65f)
                                .aspectRatio(258/379f)
                                .align(Alignment.Center)
                                .clip(RoundedCornerShape(14.dp))
                        ) {
                            TemplateItem(
                                thumbnailPath = url,
                                errorLocal = when(page){
                                    0 -> R.drawable.img_fall_back_onb3_sl1
                                    1 -> R.drawable.img_fall_back_onb3_sl2
                                    else -> R.drawable.img_fall_back_onb3_sl3
                                },
                                onSuccess = {
                                }
                            )
                        }

                        Image(
                            painter = painterResource(
                                when(page){
                                    0 -> R.drawable.img_bg_ob_sl1
                                    1 -> R.drawable.img_bg_ob_sl2
                                    else -> R.drawable.img_bg_ob_sl3
                                }
                            ),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            alignment = Alignment.Center,
                            contentScale = ContentScale.Crop
                        )
                    }
                } else if (localResId != null) {
                    Image(
                        painter = painterResource(localResId),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            }
        }

        // Title/Subtitle + CTA overlay
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.0f),
                            Color.Black.copy(alpha = 0.5f),
                            Color.Black.copy(alpha = 0.75f),
                            Color.Black.copy(alpha = 1.0f)
                        )
                    )
                )
                .padding(horizontal = 24.dp)
                .padding(top = 40.dp, bottom = 32.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 16.dp)
                ) {
                    Text(
                        text = title,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = subtitle,
                        fontSize = 16.sp,
                        color = Color.White.copy(alpha = 0.9f),
                        lineHeight = 22.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                OnboardingCtaButton(
                    text = ctaText,
                    onClick = onCta,
                    color = Primary,
                    icon = R.drawable.ic_right_arrow
                )
            }
        }

        // Native Ad at bottom
        if (adPlacement != null) {
            Box(modifier = Modifier.fillMaxWidth()) {
                NativeAdView(
                    placement = adPlacement,
                    modifier = Modifier.fillMaxWidth(),
                    isDebug = BuildConfig.DEBUG
                )
            }
        }
    }
}

// ============================================
// INDIA PAGE 3 CAROUSEL
// Auto-scrolling carousel for India region page 3
// ============================================

@Composable
internal fun IndiaPage3Carousel(
    title: String,
    subtitle: String,
    ctaText: String,
    onCta: () -> Unit,
    pageIndex: Int = 0
) {
    val pagerState = rememberPagerState(pageCount = { 3 })

    val images = listOf(
        R.drawable.img_onb31_in,
        R.drawable.img_onb32_in,
        R.drawable.img_onb33_in
    )

    val adPlacement = when (pageIndex) {
        0 -> AdPlacement.NATIVE_ONBOARDING_PAGE1
        1 -> AdPlacement.NATIVE_ONBOARDING_PAGE2
        2 -> AdPlacement.NATIVE_ONBOARDING_PAGE3
        else -> null
    }

    // Auto-scroll every 3 seconds
    LaunchedEffect(pagerState) {
        while (true) {
            delay(3000)
            val nextPage = (pagerState.currentPage + 1) % 3
            pagerState.animateScrollToPage(nextPage)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Main content area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            // Only images swipe — pager contains just images
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                userScrollEnabled = true
            ) { page ->
                Image(
                    painter = painterResource(images[page]),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }

            // Static content overlay — stays fixed, doesn't swipe
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.0f),
                                Color.Black.copy(alpha = 0.5f),
                                Color.Black.copy(alpha = 0.75f),
                                Color.Black.copy(alpha = 1.0f)
                            )
                        )
                    )
                    .padding(horizontal = 24.dp)
                    .padding(top = 40.dp, bottom = 32.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 16.dp)
                    ) {
                        Text(
                            text = title,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = subtitle,
                            fontSize = 16.sp,
                            color = Color.White.copy(alpha = 0.9f),
                            lineHeight = 22.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    OnboardingCtaButton(
                        text = ctaText,
                        onClick = onCta,
                        color = Primary,
                        icon = R.drawable.ic_right_arrow
                    )
                }
            }
        }

        // Native Ad at bottom
        if (adPlacement != null) {
            Box(
                modifier = Modifier.fillMaxWidth()
            ) {
                NativeAdView(
                    placement = adPlacement,
                    modifier = Modifier.fillMaxWidth(),
                    isDebug = BuildConfig.DEBUG
                )
            }
        }
    }
}

@Composable
private fun TemplateItem(
    thumbnailPath: String,
    errorLocal: Int?,
    onSuccess: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var retryCount by remember(thumbnailPath) { mutableIntStateOf(0) }
    var retryTrigger by remember(thumbnailPath) { mutableIntStateOf(0) }
    var isRemoteLoaded by remember(thumbnailPath) { mutableStateOf(false) }

    val imageRequest = remember(thumbnailPath, retryTrigger) {
        ImageRequest.Builder(context)
            .data(thumbnailPath)
            .size(Size(200, 350))
            .precision(Precision.INEXACT)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .listener(
                onError = { _, result ->
                    Log.e("TemplateCard", "Failed to load (attempt ${retryCount + 1}/3): $thumbnailPath, error: ${result.throwable.message}")
                    if (retryCount < 2) {
                        retryCount++
                        coroutineScope.launch {
                            delay(1000L * retryCount)
                            retryTrigger++
                        }
                    }
                },
                onSuccess = { _, _ -> retryCount = 0 }
            )
            .build()
    }

    val remoteAlpha by animateFloatAsState(
        targetValue = if (isRemoteLoaded) 1f else 0f,
        animationSpec = tween(durationMillis = 300),
        label = "remote_alpha"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        // Layer 1: local image always visible — instant placeholder while loading, fallback on error
        if (errorLocal != null) {
            Image(
                painter = painterResource(errorLocal),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }

        // Layer 2: remote image fades in on success
        if (thumbnailPath.isNotEmpty()) {
            SubcomposeAsyncImage(
                model = imageRequest,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = remoteAlpha },
                loading = { /* local layer visible beneath */ },
                error = { /* local layer visible beneath, retrying silently */ },
                success = {
                    isRemoteLoaded = true
                    onSuccess()
                    SubcomposeAsyncImageContent()
                }
            )
        }
    }
}