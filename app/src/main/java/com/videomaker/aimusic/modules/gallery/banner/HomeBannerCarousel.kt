package com.videomaker.aimusic.modules.gallery.banner

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import co.alcheclub.lib.acccore.ads.compose.NativeAdView
import com.videomaker.aimusic.BuildConfig
import com.videomaker.aimusic.core.analytics.Analytics
import com.videomaker.aimusic.core.analytics.AnalyticsEvent
import com.videomaker.aimusic.core.constants.AdPlacement
import com.videomaker.aimusic.core.playback.BannerSongPlayer
import com.videomaker.aimusic.domain.model.VideoTemplate
import com.videomaker.aimusic.ui.components.ShimmerPlaceholder
import com.videomaker.aimusic.ui.theme.AppDimens
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import org.koin.compose.koinInject
import kotlin.math.abs
import com.videomaker.aimusic.core.ads.AdClickDetector

/**
 * Remote-config driven home banner carousel.
 *
 * Renders the resolved [banners] (template/song styles 1–2) plus a native AD at the 2nd slot
 * (parity with the legacy carousel). A top-start [BannerPageIndicator] is overlaid on each banner.
 * Song banners play inline through [BannerSongPlayer]; auto-swipe pauses while a song is playing.
 */
@Composable
fun HomeBannerCarousel(
    banners: List<HomeBannerUi>,
    isVisible: Boolean,
    onTemplateBannerClick: (VideoTemplate, Int) -> Unit,
    onSongBannerClick: (Long, Int) -> Unit,
    modifier: Modifier = Modifier,
    autoSlideIntervalMs: Long = 4000L,
) {
    val adClickDetector: AdClickDetector = koinInject()
    if (banners.isEmpty()) return

    val dimens = AppDimens.current
    val player = koinInject<BannerSongPlayer>()
    val activeSongId by player.activeSongId.collectAsStateWithLifecycle()

    val carouselSize = banners.size + 1 // +1 for the AD slot at logical index 1

    val infinitePageCount = Int.MAX_VALUE
    val pagerState = rememberPagerState(
        initialPage = infinitePageCount / 2,
        pageCount = { infinitePageCount }
    )

    val isDragged by pagerState.interactionSource.collectIsDraggedAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasPendingUserSwipe by remember { mutableStateOf(false) }

    // Resolve the banner (if any) sitting at a given pager page.
    fun bannerAt(page: Int): HomeBannerUi? {
        val itemIndex = page.mod(carouselSize)
        if (itemIndex == 1) return null // AD slot
        val bannerIndex = if (itemIndex == 0) 0 else itemIndex - 1
        return banners.getOrNull(bannerIndex)
    }

    // Pause inline music when the gallery is no longer visible (tab switch — always foreground).
    LaunchedEffect(isVisible) {
        if (!isVisible) player.onScreenHidden()
    }

    // Pause inline music when navigating to another screen (lifecycle stop while in foreground).
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) player.onScreenStopped()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(isDragged, isVisible) {
        if (isVisible && isDragged) hasPendingUserSwipe = true
    }

    // Auto-swipe — suspended while a song plays inline, while dragging, or while hidden.
    LaunchedEffect(isVisible) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                delay(autoSlideIntervalMs)
                if (isVisible && !isDragged && activeSongId == null && !pagerState.isScrollInProgress) {
                    pagerState.animateScrollToPage(pagerState.currentPage + 1)
                }
            }
        }
    }

    // On settle: report the current banner so the player can stop a song swiped off-screen.
    LaunchedEffect(pagerState, isVisible, banners) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { settledPage ->
                val songId = (bannerAt(settledPage) as? HomeBannerUi.SongBanner)?.song?.id
                player.onBannerSettled(songId)
                if (isVisible && hasPendingUserSwipe) {
                    Analytics.trackGallerySwipe(AnalyticsEvent.Value.Location.GALLERY_BANNER)
                    hasPendingUserSwipe = false
                }
            }
    }

    HorizontalPager(
        state = pagerState,
        contentPadding = PaddingValues(horizontal = dimens.spaceLg),
        pageSpacing = dimens.spaceMd,
        modifier = modifier.fillMaxWidth()
    ) { page ->
        val itemIndex = page.mod(carouselSize)
        val currentPageIndex = pagerState.currentPage.mod(carouselSize)

        if (itemIndex == 1) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(388 / 200f)
                    .clip(RoundedCornerShape(dimens.radiusXl))
                    .background(Color.Black)
            ) {
                NativeAdView(
                    placement = AdPlacement.NATIVE_GALLERY_HOT_TPT,
                    autoLoad = true,
                    isDebug = BuildConfig.DEBUG,
                    onAdClicked = { adClickDetector.onAdClick(it) }
                )
            }
            return@HorizontalPager
        }

        val banner = bannerAt(page) ?: return@HorizontalPager
        val isNearCurrentPage = abs(page - pagerState.currentPage) <= 1
        val isCurrentPage = page == pagerState.settledPage && !pagerState.isScrollInProgress

        Box(modifier = Modifier.fillMaxWidth()) {
            when (banner) {
                is HomeBannerUi.TemplateBanner -> {
                    val template = banner.template
                    if (template == null) {
                        BannerShimmer()
                    } else {
                        BannerTemplateStyle(
                            template = template,
                            style = banner.style,
                            isCurrentPage = isCurrentPage,
                            shouldLoadImage = isNearCurrentPage,
                            onClick = { onTemplateBannerClick(template, banner.position) }
                        )
                    }
                }

                is HomeBannerUi.SongBanner -> {
                    val song = banner.song
                    if (song == null) {
                        BannerShimmer()
                    } else {
                        BannerSongStyle(
                            song = song,
                            style = banner.style,
                            isPlaying = activeSongId == song.id,
                            onPlay = { player.toggle(song) },
                            onClick = { onSongBannerClick(song.id, banner.position) }
                        )
                    }
                }
            }

            BannerPageIndicator(
                pageCount = carouselSize,
                currentPage = currentPageIndex,
                modifier = Modifier.align(Alignment.TopStart)
            )
        }
    }
}

@Composable
private fun BannerShimmer() {
    ShimmerPlaceholder(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(388 / 200f)
            .clip(RoundedCornerShape(16.dp)),
        cornerRadius = 16.dp
    )
}
