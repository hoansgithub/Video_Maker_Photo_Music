package com.videomaker.aimusic.modules.templatepreviewer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.SubcomposeAsyncImage
import coil.decode.BitmapFactoryDecoder
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Precision
import com.videomaker.aimusic.domain.model.VideoTemplate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop

// ============================================
// SCREEN
// ============================================

@Composable
fun TemplatePreviewerScreen(
    viewModel: TemplatePreviewerViewModel,
    onNavigateToEditor: (String) -> Unit,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val navigationEvent by viewModel.navigationEvent.collectAsStateWithLifecycle()

    LaunchedEffect(navigationEvent) {
        navigationEvent?.let { event ->
            when (event) {
                is TemplatePreviewerNavigationEvent.NavigateToEditor -> onNavigateToEditor(event.projectId)
                is TemplatePreviewerNavigationEvent.NavigateBack -> onNavigateBack()
            }
            viewModel.onNavigationHandled()
        }
    }

    when (val state = uiState) {
        is TemplatePreviewerUiState.Loading -> {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color.White)
            }
        }
        is TemplatePreviewerUiState.Ready -> {
            TemplatePreviewerReadyContent(
                state = state,
                onPageChanged = viewModel::onPageChanged,
                onUseThisTemplate = viewModel::onUseThisTemplate,
                onNavigateBack = viewModel::onNavigateBack
            )
        }
        is TemplatePreviewerUiState.Error -> {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = state.message,
                        color = Color.White,
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                    Button(onClick = viewModel::onNavigateBack) { Text("Go Back") }
                }
            }
        }
    }
}
// Virtual page count for infinite-scroll illusion.
// User starts near the middle so both up and down scrolling work immediately.
// Real template = virtualIndex % templates.size, so the list loops seamlessly.
private const val VIRTUAL_PAGE_COUNT = 10_000

private fun initialVirtualPage(initialPage: Int, templateCount: Int): Int {
    if (templateCount == 0) return VIRTUAL_PAGE_COUNT / 2
    val mid = VIRTUAL_PAGE_COUNT / 2
    // Round mid down to nearest multiple of templateCount, then offset to initialPage
    return (mid / templateCount) * templateCount + initialPage
}

// ============================================
// READY CONTENT — virtual infinite vertical pager
// ============================================

@Composable
private fun TemplatePreviewerReadyContent(
    state: TemplatePreviewerUiState.Ready,
    onPageChanged: (Int) -> Unit,
    onUseThisTemplate: (VideoTemplate) -> Unit,
    onNavigateBack: () -> Unit
) {
    val templates = state.templates
    val pagerState = rememberPagerState(
        initialPage = initialVirtualPage(state.initialPage, templates.size),
        pageCount = { VIRTUAL_PAGE_COUNT }
    )

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .drop(1)
            .collect { onPageChanged(it) }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        VerticalPager(
            state = pagerState,
            beyondViewportPageCount = 1,
            modifier = Modifier.fillMaxSize(),
            key = { pageIndex -> templates[pageIndex % templates.size].id }
        ) { pageIndex ->
            // Animate only the settled page and only when not mid-swipe.
            // Off-screen pages (prev/next buffer) load a static first frame to save
            // CPU and memory — animated WebP frames are not decoded for hidden pages.
            val isCurrentPage = pageIndex == pagerState.settledPage && !pagerState.isScrollInProgress
            TemplateThumbnailPage(
                template = templates[pageIndex % templates.size],
                isCurrentPage = isCurrentPage
            )
        }

        // Top gradient scrim
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent)
                    )
                )
                .align(Alignment.TopCenter)
        )

        // Bottom gradient scrim
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))
                    )
                )
                .align(Alignment.BottomCenter)
        )

        // Back button
        IconButton(
            onClick = onNavigateBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(start = 8.dp, top = 8.dp)
                .size(48.dp)
                .background(color = Color.Black.copy(alpha = 0.4f), shape = CircleShape)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = Color.White
            )
        }

        // Bottom bar — template name + CTA
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val currentTemplate = templates.getOrNull(pagerState.settledPage % templates.size)
            if (currentTemplate != null) {
                Text(
                    text = currentTemplate.name,
                    color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Button(
                onClick = {
                    val template = templates.getOrNull(pagerState.settledPage % templates.size) ?: return@Button
                    onUseThisTemplate(template)
                },
                enabled = !state.isCreatingProject,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color.Black
                )
            ) {
                if (state.isCreatingProject) {
                    CircularProgressIndicator(
                        color = Color.Black,
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = "Use This Template",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

// ============================================
// SINGLE PAGE — thumbnail image
// ============================================

@Composable
private fun TemplateThumbnailPage(template: VideoTemplate, isCurrentPage: Boolean) {
    val context = LocalContext.current
    val thumbnailUrl = template.thumbnailPath.ifEmpty { null }

    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        SubcomposeAsyncImage(
            model = ImageRequest.Builder(context)
                .data(thumbnailUrl)
                .diskCachePolicy(CachePolicy.ENABLED)
                .memoryCachePolicy(CachePolicy.ENABLED)
                // Separate memory cache entries for animated vs static so they
                // don't overwrite each other when a page transitions to/from current.
                .memoryCacheKey("${template.id}_${if (isCurrentPage) "anim" else "static"}")
                .precision(Precision.INEXACT)
                .apply {
                    if (!isCurrentPage) {
                        // BitmapFactoryDecoder decodes only the first frame — no animation
                        // decoder is invoked, so ImageDecoderDecoder (animated WebP) is bypassed.
                        decoderFactory(BitmapFactoryDecoder.Factory())
                    }
                    // Current page: use default decoder chain — ImageDecoderDecoder handles
                    // animated WebP automatically via the coil-gif dependency.
                }
                .crossfade(true)
                .build(),
            contentDescription = template.name,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            loading = {
                Box(
                    modifier = Modifier.fillMaxSize().background(Color.DarkGray),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(32.dp))
                }
            },
            error = {
                Box(modifier = Modifier.fillMaxSize().background(Color.DarkGray))
            }
        )
    }
}