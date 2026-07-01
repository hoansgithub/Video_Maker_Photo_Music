package com.videomaker.aimusic.modules.home.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.request.CachePolicy
import coil.request.ImageRequest
import co.alcheclub.lib.acccore.ads.compose.NativeAdView
import coil.size.Precision
import coil.size.Size
import com.videomaker.aimusic.BuildConfig
import com.videomaker.aimusic.R
import com.videomaker.aimusic.core.ads.AdClickDetector
import com.videomaker.aimusic.core.analytics.Analytics
import com.videomaker.aimusic.core.analytics.AnalyticsEvent
import com.videomaker.aimusic.core.analytics.onFirstVisible
import com.videomaker.aimusic.core.util.NumberFormatter
import com.videomaker.aimusic.domain.model.VideoTemplate
import com.videomaker.aimusic.modules.gallery.CreateNewVideoButton
import com.videomaker.aimusic.modules.home.AiTabViewModel
import com.videomaker.aimusic.ui.components.ModifierExtension.clickableSingle
import com.videomaker.aimusic.ui.components.ShimmerPlaceholder
import com.videomaker.aimusic.ui.theme.Black60
import com.videomaker.aimusic.ui.theme.Gray200
import com.videomaker.aimusic.ui.theme.Gray600
import com.videomaker.aimusic.ui.theme.Neutral_N100
import com.videomaker.aimusic.ui.theme.Neutral_N900
import com.videomaker.aimusic.ui.theme.Primary
import com.videomaker.aimusic.ui.theme.SurfaceDark
import com.videomaker.aimusic.ui.theme.TemplateBadgeBackground
import com.videomaker.aimusic.ui.theme.TextPrimary
import com.videomaker.aimusic.ui.theme.White12
import com.videomaker.aimusic.core.constants.AdPlacement
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/** Card geometry shared by every AI template row item (W:H = 120:180, 12dp radius). */
private val AiCardWidth = 110.dp
private val AiCardHeight = 180.dp
private val AiCardShape = RoundedCornerShape(12.dp)

/** Horizontal inset that keeps the banner, headers, and card rows visually aligned. */
private val AiContentHorizontalPadding = 12.dp

private val BadgeShape = RoundedCornerShape(999.dp)

/** Placeholder count shown while the rows are still loading. */
private const val PLACEHOLDER_COUNT = 4

/**
 * AI Tab — a hardcoded showcase of upcoming AI features:
 *  - A "Trending AI Video" banner (Coming Soon) styled like the gallery template banner.
 *  - Two non-scrollable template rows (AI Video Generator, AI Dance).
 *  - A static "Create New Video" CTA pinned to the bottom (no collapse-on-scroll animation).
 *
 * Everything is presentational for now; wire callbacks to real data/navigation later.
 */
@Composable
fun AiTabContent(
    viewModel: AiTabViewModel,
    isShowPaddingBottom: Boolean,
    topBarHeight: Dp = 0.dp,
    onCreateClick: () -> Unit = {},
    onRemindMeClick: () -> Unit = {},
    onSeeAllVideoGenerator: () -> Unit = {},
    onSeeAllDance: () -> Unit = {},
    onTemplateClick: (VideoTemplate, String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val adClickDetector: AdClickDetector = koinInject()

    // Transient "We'll remind you later" toast shown when the banner's Remind Me is tapped.
    var showRemindToast by remember { mutableStateOf(false) }
    LaunchedEffect(showRemindToast) {
        if (showRemindToast) {
            delay(1500L)
            showRemindToast = false
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        // Background image — edge-to-edge, behind everything (same as the Gallery tab).
        Image(
            painter = painterResource(R.drawable.bg_home),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = topBarHeight + 8.dp)
                // Leaves room for the pinned Create CTA + bottom ad.
                .padding(bottom = 96.dp)
        ) {
            AiBanner(
                onRemindMeClick = {
                    showRemindToast = true
                    onRemindMeClick()
                },
                modifier = Modifier.padding(horizontal = AiContentHorizontalPadding)
            )

            Spacer(modifier = Modifier.height(24.dp))

            AiTemplateSection(
                title = stringResource(R.string.ai_section_video_generator),
                templates = uiState.videoGenerator,
                isLoading = uiState.isLoading,
                onSeeAllClick = onSeeAllVideoGenerator,
                onTemplateClick = { onTemplateClick(it, AiTabViewModel.TAG_VIDEO_GENERATOR) }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // "Big bait" native ad between the two AI sections. Self-sizes and
            // self-hides when no ad is available (or ads are disabled/premium).
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AiContentHorizontalPadding)
            ) {
                NativeAdView(
                    placement = AdPlacement.NATIVE_AI_TAB,
                    autoLoad = true,
                    isDebug = BuildConfig.DEBUG,
                    onAdClicked = { adClickDetector.onAdClick(it) }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            AiTemplateSection(
                title = stringResource(R.string.ai_section_dance),
                templates = uiState.dance,
                isLoading = uiState.isLoading,
                onSeeAllClick = onSeeAllDance,
                onTemplateClick = { onTemplateClick(it, AiTabViewModel.TAG_DANCE) }
            )
        }

        // Static Create CTA — same button as Gallery, but with no scroll-driven animation.
        CreateNewVideoButton(
            onClick = onCreateClick,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .then(
                    if (isShowPaddingBottom) Modifier.navigationBarsPadding()
                    else Modifier
                )
                .padding(bottom = 16.dp)
        )

        AnimatedVisibility(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .then(
                    if (isShowPaddingBottom) Modifier.navigationBarsPadding()
                    else Modifier
                )
                .padding(bottom = 16.dp),
            visible = showRemindToast,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            RemindMeToast()
        }
    }
}

/** Dark pill toast confirming the user will be reminded when the AI feature launches. */
@Composable
private fun RemindMeToast() {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(120.dp))
            .border(1.dp, Color.White.copy(0.12f),RoundedCornerShape(120.dp))
            .background(Neutral_N900)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_select_circle),
            contentDescription = null,
            tint = Color.Unspecified,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = stringResource(R.string.ai_remind_toast),
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.W500
        )
    }
}

/**
 * "Trending AI Video" banner — mirrors the gallery template banner frame (388:200, 16dp radius,
 * subtle white border) but with hardcoded AI content and the [R.drawable.img_banner_ai] artwork.
 */
@Composable
private fun AiBanner(
    onRemindMeClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .fillMaxWidth()
            .aspectRatio(388 / 200f)
            .border(2.dp, Color.White.copy(0.12f), RoundedCornerShape(16.dp))
    ) {
        Image(
            painter = painterResource(R.drawable.img_banner_ai),
            contentDescription = stringResource(R.string.ai_banner_title),
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // Same overlay treatment as the style-2 template banner.
        Image(
            painter = painterResource(R.drawable.img_bg_banner_template1),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(16.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // "COMING SOON" pill.
                Row(
                    modifier = Modifier
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color(0xFFB026FF), Color(0xFFFC19CF))
                            ),
                            RoundedCornerShape(24.dp)
                        )
                        .border(1.dp, Color.White.copy(0.12f), RoundedCornerShape(24.dp))
                        .padding(horizontal = 6.dp, vertical = 3.5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_mingcute_trending),
                        contentDescription = null,
                        tint = Color.White
                    )
                    Text(
                        text = stringResource(R.string.ai_banner_coming_soon),
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.W700,
                        fontStyle = FontStyle.Italic
                    )
                }

                Text(
                    text = stringResource(R.string.ai_banner_title),
                    color = Color(0xFFF6F6F6),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.W800,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // "Remind Me" pill.
            Row(
                modifier = Modifier
                    .background(Neutral_N100, RoundedCornerShape(160.dp))
                    .clickableSingle { onRemindMeClick() }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_bell),
                    contentDescription = null,
                    tint = Primary,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = stringResource(R.string.ai_banner_remind_me),
                    color = Primary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.W600
                )
            }
        }
    }
}

/**
 * A section header ("AI Video Generator" / "AI Dance") over a non-scrollable row of AI cards.
 * The whole section is hidden once loaded if the vibe tag returned no templates.
 */
@Composable
private fun AiTemplateSection(
    title: String,
    templates: List<VideoTemplate>,
    isLoading: Boolean,
    onSeeAllClick: () -> Unit,
    onTemplateClick: (VideoTemplate) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!isLoading && templates.isEmpty()) return

    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = AiContentHorizontalPadding),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            IconButton(
                onClick = {
                    Analytics.trackAiAllTemplateClick()
                    onSeeAllClick()
                },
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = stringResource(R.string.ai_see_all),
                    tint = TextPrimary,
                    modifier = Modifier.size(32.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            userScrollEnabled = false,
            contentPadding = PaddingValues(horizontal = AiContentHorizontalPadding),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (isLoading) {
                items(PLACEHOLDER_COUNT) {
                    ShimmerPlaceholder(
                        modifier = Modifier.size(width = AiCardWidth, height = AiCardHeight),
                        cornerRadius = 12.dp
                    )
                }
            } else {
                items(templates, key = { it.id }) { template ->
                    AiTemplateCard(
                        template = template,
                        onClick = {
                            Analytics.trackTemplateClick(
                                templateId = template.id,
                                templateName = template.name,
                                location = AnalyticsEvent.Value.Location.AI,
                                isPremium = template.isPremium,
                                style = AnalyticsEvent.Value.Style.AI
                            )
                            onTemplateClick(template)
                        },
                        modifier = Modifier.onFirstVisible(key = template.id) {
                            Analytics.trackTemplateImpression(
                                templateId = template.id,
                                templateName = template.name,
                                location = AnalyticsEvent.Value.Location.AI,
                                screenSessionId = "",
                                isPremium = template.isPremium,
                                style = AnalyticsEvent.Value.Style.AI
                            )
                        }
                    )
                }
            }
        }
    }
}

/** Single AI template card — remote thumbnail with only the view-count badge, like other templates. */
@Composable
private fun AiTemplateCard(
    template: VideoTemplate,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val thumbnailPath = template.thumbnailPath
    val coroutineScope = rememberCoroutineScope()

    // Silent retry (3 attempts max) — same behavior as the shared TemplateCard.
    var retryCount by remember(thumbnailPath) { mutableIntStateOf(0) }
    var retryTrigger by remember(thumbnailPath) { mutableIntStateOf(0) }

    // Stable request keyed with the shared "grid_" cache key so the AI row and the
    // template grid reuse the same cached bitmap (no reload / no duplicate cache entry).
    val imageRequest = remember(thumbnailPath, retryTrigger) {
        ImageRequest.Builder(context)
            .data(thumbnailPath)
            .size(Size(200, 350))
            .precision(Precision.INEXACT)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .memoryCacheKey("grid_$thumbnailPath")
            .diskCacheKey("grid_$thumbnailPath")
            .crossfade(true)
            .crossfade(200)
            .listener(
                onError = { _, result ->
                    android.util.Log.e(
                        "AiTemplateCard",
                        "Failed to load thumbnail (attempt ${retryCount + 1}/3): $thumbnailPath, error: ${result.throwable.message}"
                    )
                    // Auto-retry silently (no user message)
                    if (retryCount < 2) {  // 0, 1 = retry; 2 = give up
                        retryCount++
                        coroutineScope.launch {
                            delay(1000L * retryCount)  // 1s, 2s delay
                            retryTrigger++  // Trigger reload
                        }
                    }
                },
                onSuccess = { _, _ ->
                    retryCount = 0  // Reset on success
                }
            )
            .build()
    }

    Box(
        modifier = modifier
            .size(width = AiCardWidth, height = AiCardHeight)
            .clip(AiCardShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickableSingle { onClick() }
    ) {
        if (thumbnailPath.isNotEmpty()) {
            val painter = rememberAsyncImagePainter(model = imageRequest)

            // Shimmer / error behind the image — mirrors TemplateCard.
            when (painter.state) {
                is AsyncImagePainter.State.Loading,
                is AsyncImagePainter.State.Empty -> {
                    ShimmerPlaceholder(
                        modifier = Modifier.fillMaxSize(),
                        cornerRadius = 0.dp
                    )
                }
                is AsyncImagePainter.State.Error -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(SurfaceDark),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_choose_img),
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = Gray600
                        )
                    }
                }
                is AsyncImagePainter.State.Success -> { /* image renders below */ }
            }

            Image(
                painter = painter,
                contentDescription = template.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            ShimmerPlaceholder(
                modifier = Modifier.fillMaxSize(),
                cornerRadius = 0.dp
            )
        }

        // Bottom gradient scrim — keeps the badge legible over any artwork.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent, Black60))
                )
        )

        // View-count badge — bottom-end.
        if (template.viewCount > 0) {
            val formattedViewCount = remember(template.viewCount) {
                NumberFormatter.formatCount(template.viewCount)
            }
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .background(color = TemplateBadgeBackground, shape = BadgeShape)
                    .border(width = 1.dp, color = White12, shape = BadgeShape)
                    .padding(horizontal = 8.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_heart),
                    contentDescription = null,
                    tint = Gray200,
                    modifier = Modifier.size(10.dp)
                )
                Text(
                    text = formattedViewCount,
                    fontSize = 10.sp,
                    color = Gray200,
                    maxLines = 1
                )
            }
        }
    }
}
