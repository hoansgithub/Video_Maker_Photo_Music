package com.videomaker.aimusic.modules.unifiedsearch

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import com.videomaker.aimusic.R
import com.videomaker.aimusic.media.audio.AudioPreviewCache
import com.videomaker.aimusic.modules.songs.MusicPlayerBottomSheet
import com.videomaker.aimusic.modules.unifiedsearch.components.UnifiedSearchEmptyContent
import com.videomaker.aimusic.modules.unifiedsearch.components.UnifiedSearchIdleContent
import com.videomaker.aimusic.modules.unifiedsearch.components.UnifiedSearchLoadingContent
import com.videomaker.aimusic.modules.unifiedsearch.components.UnifiedSearchResultsContent
import com.videomaker.aimusic.modules.unifiedsearch.components.UnifiedSearchTopBar
import com.videomaker.aimusic.modules.unifiedsearch.components.UnifiedSearchTypingOverlay
import com.videomaker.aimusic.ui.theme.TextSecondary
import org.koin.compose.koinInject

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun UnifiedSearchScreen(
    viewModel: UnifiedSearchViewModel,
    onNavigateToTemplateDetail: (String) -> Unit = {},
    onNavigateToSongDetail: (Long) -> Unit = {},
    onNavigateBack: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val displayText by viewModel.displayText.collectAsStateWithLifecycle()
    val recentSearches by viewModel.recentSearches.collectAsStateWithLifecycle()
    val persistentRelatedSearches by viewModel.persistentRelatedSearches.collectAsStateWithLifecycle()
    val suggestionVibeTags by viewModel.suggestionVibeTags.collectAsStateWithLifecycle()
    val genres by viewModel.genres.collectAsStateWithLifecycle()
    val featuredTemplates by viewModel.featuredTemplates.collectAsStateWithLifecycle()
    val hasMoreFeaturedTemplates by viewModel.hasMoreFeaturedTemplates.collectAsStateWithLifecycle()
    val isLoadingMoreFeaturedTemplates by viewModel.isLoadingMoreFeaturedTemplates.collectAsStateWithLifecycle()
    val suggestedSongs by viewModel.suggestedSongs.collectAsStateWithLifecycle()
    val hasMoreSuggestedSongs by viewModel.hasMoreSuggestedSongs.collectAsStateWithLifecycle()
    val isLoadingMoreSuggestedSongs by viewModel.isLoadingMoreSuggestedSongs.collectAsStateWithLifecycle()
    val selectedSong by viewModel.selectedSong.collectAsStateWithLifecycle()
    val navigationEvent by viewModel.navigationEvent.collectAsStateWithLifecycle()
    val keyboardController = LocalSoftwareKeyboardController.current
    val audioPreviewCache: AudioPreviewCache = koinInject()

    LaunchedEffect(navigationEvent) {
        navigationEvent?.let { event ->
            when (event) {
                is UnifiedSearchNavigationEvent.NavigateBack -> onNavigateBack()
                is UnifiedSearchNavigationEvent.NavigateToTemplateDetail ->
                    onNavigateToTemplateDetail(event.templateId)
                is UnifiedSearchNavigationEvent.NavigateToSongDetail ->
                    onNavigateToSongDetail(event.songId)
            }
            viewModel.onNavigationHandled()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .imePadding()
    ) {
        when (val state = uiState) {
            is UnifiedSearchUiState.Idle -> UnifiedSearchIdleContent(
                initialSection = state.initialSection,
                recentSearches = recentSearches,
                suggestionVibeTags = suggestionVibeTags,
                genres = genres,
                featuredTemplates = featuredTemplates,
                suggestedSongs = suggestedSongs,
                hasMoreFeaturedTemplates = hasMoreFeaturedTemplates,
                isLoadingMoreFeaturedTemplates = isLoadingMoreFeaturedTemplates,
                hasMoreSuggestedSongs = hasMoreSuggestedSongs,
                isLoadingMoreSuggestedSongs = isLoadingMoreSuggestedSongs,
                onRecentClick = viewModel::onRecentSearchClick,
                onRemoveRecent = viewModel::onRemoveRecentSearch,
                onClearAllRecents = viewModel::onClearAllRecents,
                onVibeTagClick = viewModel::onVibeTagClick,
                onGenreClick = viewModel::onGenreClick,
                onTemplateClick = viewModel::onTemplateClick,
                onSeeMoreTemplates = viewModel::onSeeMoreFeaturedTemplates,
                onSongClick = { song ->
                    keyboardController?.hide()
                    viewModel.onSongClick(song)
                },
                onSeeMoreSongs = viewModel::onSeeMoreSuggestedSongs
            )

            is UnifiedSearchUiState.Typing -> UnifiedSearchTypingOverlay(
                currentText = state.currentText,
                suggestions = state.suggestions,
                persistentRelatedSearches = persistentRelatedSearches,
                onSuggestionClick = viewModel::onSuggestionClick
            )

            is UnifiedSearchUiState.Loading -> UnifiedSearchLoadingContent(
                query = displayText,
                relatedSearches = persistentRelatedSearches,
                onRelatedSearchClick = viewModel::onSuggestionClick
            )

            is UnifiedSearchUiState.Results -> UnifiedSearchResultsContent(
                state = state,
                query = displayText,
                relatedSearches = persistentRelatedSearches,
                onTemplateClick = viewModel::onTemplateClick,
                onSongClick = { song ->
                    keyboardController?.hide()
                    viewModel.onSongClick(song)
                },
                onExplore = viewModel::onExplore,
                onScrollStarted = { keyboardController?.hide() },
                onRelatedSearchClick = viewModel::onSuggestionClick
            )

            else -> UnifiedSearchEmptyContent(
                onExploreMore = viewModel::onExploreMore,
            )
        }


        UnifiedSearchTopBar(
            query = displayText,
            onQueryChange = viewModel::onQueryChange,
            onSearch = viewModel::onSearch,
            onClearQuery = viewModel::onClearQuery,
            onBack = viewModel::onNavigateBack
        )
    }

    selectedSong?.let { song ->
        MusicPlayerBottomSheet(
            song = song,
            cacheDataSourceFactory = audioPreviewCache.cacheDataSourceFactory,
            onDismiss = viewModel::onDismissPlayer,
            onUseToCreate = viewModel::onUseToCreateVideo
        )
    }
}

