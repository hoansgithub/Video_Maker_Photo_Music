package com.videomaker.aimusic.widget

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.paint
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.videomaker.aimusic.R
import com.videomaker.aimusic.ui.components.PageIndicatorCircle
import com.videomaker.aimusic.ui.components.PrimaryButtonNeon
import com.videomaker.aimusic.ui.theme.CtaText
import com.videomaker.aimusic.widget.appwidget.SmartSearchAppWidget
import com.videomaker.aimusic.widget.appwidget.SmartSearchWidgetReceiver
import com.videomaker.aimusic.widget.appwidget.TrendingSongAppWidget
import com.videomaker.aimusic.widget.appwidget.TrendingSongWidgetReceiver
import com.videomaker.aimusic.widget.appwidget.TrendingTemplateAppWidget
import com.videomaker.aimusic.widget.appwidget.TrendingTemplateWidgetReceiver
import com.videomaker.aimusic.widget.components.SmartSearchWidget
import com.videomaker.aimusic.widget.components.TrendingSongWidget
import com.videomaker.aimusic.widget.components.TrendingWidget
import com.videomaker.aimusic.widget.model.WidgetType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private const val TAG = "WidgetScreen"

// Widget preview sizes (4x3 cells)
private val WIDGET_SIZE = DpSize(250.dp, 180.dp)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetScreen(
    viewModel: WidgetViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToTemplatePreviewer: (templateId: String) -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateToTemplatePreviewerWithSong: (songId: Long) -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val navigationEvent by viewModel.navigationEvent.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Handle navigation events - StateFlow-based (Google recommended pattern)
    LaunchedEffect(navigationEvent) {
        navigationEvent?.let { event ->
            when (event) {
                is WidgetNavigationEvent.NavigateBack -> onNavigateBack()
                is WidgetNavigationEvent.NavigateToTemplatePreviewer -> onNavigateToTemplatePreviewer(event.templateId)
                is WidgetNavigationEvent.NavigateToSearch -> onNavigateToSearch()
                is WidgetNavigationEvent.NavigateToTemplatePreviewerWithSong -> onNavigateToTemplatePreviewerWithSong(event.songId)
            }
            viewModel.onNavigationHandled()
        }
    }

    // Collect pin widget event (requires Context, so stays in composable)
    LaunchedEffect(Unit) {
        viewModel.pinWidgetEvent.collect { widgetType ->
            requestPinWidget(
                scope = coroutineScope,
                context = context,
                widgetType = widgetType
            )
        }
    }

    // Derive widget data from state
    val widgetData = when (val state = uiState) {
        is WidgetUiState.Success -> state.data
        else -> WidgetData()
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
                    style = MaterialTheme.typography.displayLarge,
                    fontWeight = FontWeight.W600,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Subtitle - #999999, regular 16sp
                Text(
                    text = "4 x 3",
                    style = MaterialTheme.typography.titleLarge,
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
                            list = widgetData.newReleaseTemplates,
                            onClickSearch = viewModel::onSearchClick,
                            onClick = viewModel::onTemplateClick
                        )
                    }

                    WidgetType.SONG -> {
                        TrendingSongWidget(
                            listSongs = widgetData.trendingSongs,
                            onClick = viewModel::onSongPlayClick
                        )
                    }

                    WidgetType.TEMPLATE -> {
                        TrendingWidget(
                            list = widgetData.trendingTemplates,
                            onClickAdd = viewModel::onAddTemplateClick,
                            onClick = viewModel::onTemplateClick
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

            // Add Widget Button - pins the current widget to home screen
            PrimaryButtonNeon(
                text = stringResource(R.string.widget_bts)
            ) {
                val widgetType = widgetTypes[pagerState.currentPage]
                viewModel.onAddWidgetClick(widgetType)
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * Request to pin a widget to the home screen using Glance AppWidgetManager.
 */
private fun requestPinWidget(
    scope: CoroutineScope,
    context: Context,
    widgetType: WidgetType
) {
    Log.d(TAG, "Requesting pin widget: $widgetType")

    scope.launch {
        try {
            val glanceManager = GlanceAppWidgetManager(context)
            val success = when (widgetType) {
                WidgetType.SEARCH -> glanceManager.requestPinGlanceAppWidget(
                    receiver = SmartSearchWidgetReceiver::class.java,
                    preview = SmartSearchAppWidget(),
                    previewState = WIDGET_SIZE
                )
                WidgetType.SONG -> glanceManager.requestPinGlanceAppWidget(
                    receiver = TrendingSongWidgetReceiver::class.java,
                    preview = TrendingSongAppWidget(),
                    previewState = WIDGET_SIZE
                )
                WidgetType.TEMPLATE -> glanceManager.requestPinGlanceAppWidget(
                    receiver = TrendingTemplateWidgetReceiver::class.java,
                    preview = TrendingTemplateAppWidget(),
                    previewState = WIDGET_SIZE
                )
            }
            Log.d(TAG, "requestPinGlanceAppWidget result: $success")

            if (success) {
                Toast.makeText(context, R.string.widget_pin_request_sent, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, R.string.widget_pin_not_supported, Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to pin widget", e)
            Toast.makeText(
                context,
                context.getString(R.string.widget_add_failed, e.message),
                Toast.LENGTH_LONG
            ).show()
        }
    }
}