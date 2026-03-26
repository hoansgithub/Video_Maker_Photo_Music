package com.videomaker.aimusic.widget

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.paint
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.videomaker.aimusic.R
import com.videomaker.aimusic.domain.model.MusicSong
import com.videomaker.aimusic.domain.model.VideoTemplate
import com.videomaker.aimusic.ui.components.PageIndicator
import com.videomaker.aimusic.ui.components.PageIndicatorCircle
import com.videomaker.aimusic.ui.components.PrimaryButtonNeon
import com.videomaker.aimusic.ui.theme.AppDimens
import com.videomaker.aimusic.ui.theme.CtaText
import com.videomaker.aimusic.ui.theme.FoundationBlack
import com.videomaker.aimusic.widget.components.SmartSearchWidget
import com.videomaker.aimusic.widget.components.TrendingSongWidget
import com.videomaker.aimusic.widget.components.TrendingWidget
import com.videomaker.aimusic.widget.model.WidgetType
import kotlin.collections.get

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetScreen(
    onNavigateBack: () -> Unit,
) {
    val context = LocalContext.current
    val isPreview = LocalInspectionMode.current
    val coroutineScope = rememberCoroutineScope()
    // GlanceAppWidgetManager requires Android 8.0+ (API 26) for pinning
    // Our minSdk is 28, so this is always supported
    val canPinWidgets = remember {
        if (isPreview) true
        else Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
    }

    // Pager state
    val widgetTypes = WidgetType.entries
    val pagerState = rememberPagerState(pageCount = { widgetTypes.size })


    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CtaText)
            .paint(
                painter = painterResource(id = R.drawable.bg_widget),
                contentScale = ContentScale.Crop
            )
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ){
            Text(
                text = stringResource(R.string.widget_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.Center)
            )
            IconButton(onClick = onNavigateBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                    modifier = Modifier
                        .size(24.dp)
                        .align(Alignment.CenterStart)
                )
            }
        }

        // Content area with title and widget
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            pageSpacing = 38.dp,
            contentPadding = PaddingValues(start = 38.dp, end = 38.dp)
        ) { page ->
            val widgetType = widgetTypes[page]
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = when (widgetType) {
                        WidgetType.SEARCH -> stringResource(R.string.widget_smart_search)
                        WidgetType.TEMPLATE -> stringResource(R.string.widget_trending_template)
                        WidgetType.SONG -> stringResource(R.string.widget_trending_song)
                    },
                    fontSize = AppDimens.current.font4Xl,
                    fontWeight = FontWeight.W600,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Subtitle - #999999, regular 16sp
                Text(
                    text = "4 x 3",
                    fontSize = AppDimens.current.fontXl,
                    fontWeight = FontWeight.W500,
                    color = Color.White,
                    modifier = Modifier
                        .background(Color.White.copy(0.24f), RoundedCornerShape(120.dp))
                        .padding(vertical = 4.dp, horizontal = 12.dp)
                )

                // Space between title/subtitle and widget
                Spacer(modifier = Modifier.height(38.dp))

                when (widgetType) {
                    WidgetType.SEARCH -> {
                        SmartSearchWidget(
                            list = listOf(
                                VideoTemplate(
                                    id = "23",
                                    name = "132esd",
                                    songId = 123L,
                                    effectSetId = "23"
                                ),
                                VideoTemplate(
                                    id = "123",
                                    name = "132esd",
                                    songId = 123L,
                                    effectSetId = "23"
                                ),
                                VideoTemplate(
                                    id = "223",
                                    name = "132esd",
                                    songId = 123L,
                                    effectSetId = "23"
                                ),
                            ),
                            onClickSearch = {

                            }
                        ) {

                        }
                    }

                    WidgetType.SONG -> {
                        TrendingSongWidget(
                            listSongs = listOf(
                                MusicSong(
                                    id = 12000L,
                                    name = "qqw",
                                    artist = "sdas"
                                ),
                                MusicSong(
                                    id = 12000L,
                                    name = "qqw",
                                    artist = "sdas"
                                ),
                                MusicSong(
                                    id = 12000L,
                                    name = "qqw",
                                    artist = "sdas"
                                ),
                                MusicSong(
                                    id = 12000L,
                                    name = "qqw",
                                    artist = "sdas"
                                ),
                            )
                        ) {

                        }
                    }

                    WidgetType.TEMPLATE -> {
                        TrendingWidget(
                            list = listOf(
                                VideoTemplate(
                                    id = "23",
                                    name = "132esd",
                                    songId = 123L,
                                    effectSetId = "23"
                                ),
                                VideoTemplate(
                                    id = "123",
                                    name = "132esd",
                                    songId = 123L,
                                    effectSetId = "23"
                                ),
                                VideoTemplate(
                                    id = "223",
                                    name = "132esd",
                                    songId = 123L,
                                    effectSetId = "23"
                                ),
                            ),
                            onClickAdd = {

                            },
                            onClick = {

                            }
                        )
                    }
                }
            }
        }

        // Bottom section with balanced spacing
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Space above indicator
            Spacer(modifier = Modifier.height(48.dp))

            // Page Indicator - centered between widget and add button
            PageIndicatorCircle(
                currentPage = pagerState.currentPage,
                pageCount = widgetTypes.size
            )

            // Space below indicator
            Spacer(modifier = Modifier.height(16.dp))

            // Add Widget Button
            PrimaryButtonNeon(
                text = stringResource(R.string.widget_bts)
            ){

            }

            Spacer(modifier = Modifier.height(16.dp))

            // Native ad: shimmer until ready, ad when loaded, nothing on failure
            /*val adPlacement = when (pagerState.currentPage) {
                0 -> AdPlacement.NATIVE_WIDGET_SEARCH
                1 -> AdPlacement.NATIVE_WIDGET_TRENDING
                else -> AdPlacement.NATIVE_WIDGET_DAILY
            }
            WidgetNativeAdSlot(
                placement = adPlacement,
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .aspectRatio(390f / 200f)
            )

            // Bottom safe area
            Spacer(modifier = Modifier.height(24.dp))*/
        }
    }
}