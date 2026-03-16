package com.videomaker.aimusic.modules.gallerysearch

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.videomaker.aimusic.R
import com.videomaker.aimusic.domain.model.SongGenre
import com.videomaker.aimusic.ui.components.ProvideShimmerEffect
import com.videomaker.aimusic.ui.components.SongListItem
import com.videomaker.aimusic.ui.components.SongListItemPlaceholder
import com.videomaker.aimusic.ui.theme.AppDimens
import com.videomaker.aimusic.ui.theme.Black24
import com.videomaker.aimusic.ui.theme.ChipBorderInactive
import com.videomaker.aimusic.ui.theme.Gray450
import com.videomaker.aimusic.ui.theme.Primary
import com.videomaker.aimusic.ui.theme.SearchFieldBackground
import com.videomaker.aimusic.ui.theme.SearchFieldBorder
import com.videomaker.aimusic.ui.theme.TextBright
import com.videomaker.aimusic.ui.theme.TextSecondary
import com.videomaker.aimusic.ui.theme.TextTertiary
import com.videomaker.aimusic.ui.theme.VideoMakerTheme

// ============================================
// GALLERY SEARCH SCREEN
// ============================================

@Composable
fun GallerySearchScreen(
    viewModel: GallerySearchViewModel,
    onNavigateToTemplateDetail: (String) -> Unit = {},
    onNavigateBack: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val recentSearches by viewModel.recentSearches.collectAsStateWithLifecycle()
    val suggestionGenres by viewModel.suggestionGenres.collectAsStateWithLifecycle()
    val navigationEvent by viewModel.navigationEvent.collectAsStateWithLifecycle()

    // Handle navigation events
    LaunchedEffect(navigationEvent) {
        navigationEvent?.let { event ->
            when (event) {
                is GallerySearchNavigationEvent.NavigateBack -> onNavigateBack()
                is GallerySearchNavigationEvent.NavigateToTemplateDetail ->
                    onNavigateToTemplateDetail(event.templateId)
            }
            viewModel.onNavigationHandled()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .imePadding()
    ) {
        // Top bar with back + search field
        GallerySearchTopBar(
            query = query,
            onQueryChange = viewModel::onQueryChange,
            onSearch = viewModel::onSearch,
            onClearQuery = viewModel::onClearQuery,
            onBack = viewModel::onNavigateBack
        )

        // Content based on state
        when (val state = uiState) {
            is GallerySearchUiState.Idle -> GallerySearchIdleContent(
                recentSearches = recentSearches,
                suggestionGenres = suggestionGenres,
                onRecentClick = viewModel::onRecentSearchClick,
                onRemoveRecent = viewModel::onRemoveRecentSearch,
                onClearAllRecents = viewModel::onClearAllRecents,
                onSuggestionClick = { suggestion ->
                    viewModel.onQueryChange(suggestion)
                    viewModel.onSearch()
                }
            )

            is GallerySearchUiState.Loading -> GallerySearchLoadingContent()

            is GallerySearchUiState.Results -> GallerySearchResultsContent(
                templates = state.templates,
                songs = state.songs,
                onTemplateClick = viewModel::onTemplateClick
            )

            is GallerySearchUiState.Empty -> GallerySearchEmptyContent(query = state.query)

            is GallerySearchUiState.Error -> GallerySearchErrorContent(message = state.message)
        }
    }
}

// ============================================
// SEARCH TOP BAR
// ============================================

@Composable
private fun GallerySearchTopBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onClearQuery: () -> Unit,
    onBack: () -> Unit
) {
    val dimens = AppDimens.current
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    // Auto-focus the text field on enter
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .padding(
                start = dimens.spaceXs,
                end = dimens.spaceLg,
                top = dimens.spaceSm,
                bottom = dimens.spaceMd
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Back button
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.back),
                tint = MaterialTheme.colorScheme.onSurface
            )
        }

        // Search field
        Box(
            modifier = Modifier
                .weight(1f)
                .background(
                    color = SearchFieldBackground,
                    shape = RoundedCornerShape(dimens.radiusXl)
                )
                .border(
                    width = 1.dp,
                    color = SearchFieldBorder,
                    shape = RoundedCornerShape(dimens.radiusXl)
                )
                .padding(horizontal = dimens.spaceMd, vertical = dimens.spaceMd)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_lead_search),
                    contentDescription = null,
                    tint = Color.Unspecified,
                    modifier = Modifier.size(24.dp)
                )

                Spacer(modifier = Modifier.width(dimens.spaceSm))

                Box(modifier = Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        Text(
                            text = stringResource(R.string.search_hint),
                            style = MaterialTheme.typography.titleSmall,
                            color = TextTertiary
                        )
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        singleLine = true,
                        textStyle = MaterialTheme.typography.titleSmall.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        cursorBrush = SolidColor(Primary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(
                            onSearch = {
                                onSearch()
                                keyboardController?.hide()
                            }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                    )
                }

                if (query.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(dimens.spaceXs))
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear",
                        tint = TextTertiary,
                        modifier = Modifier
                            .size(20.dp)
                            .clickable { onClearQuery() }
                    )
                }
            }
        }
    }
}

// ============================================
// IDLE CONTENT (recents + suggestions)
// ============================================

@Composable
private fun GallerySearchIdleContent(
    recentSearches: List<String>,
    suggestionGenres: List<SongGenre>,
    onRecentClick: (String) -> Unit,
    onRemoveRecent: (String) -> Unit,
    onClearAllRecents: () -> Unit,
    onSuggestionClick: (String) -> Unit
) {
    val dimens = AppDimens.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = dimens.spaceMd)
    ) {
        // Recent searches section
        if (recentSearches.isNotEmpty()) {
            item(key = "recent_header") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = dimens.spaceLg),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.search_recent),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.search_clear_all),
                        style = MaterialTheme.typography.bodySmall,
                        color = Primary,
                        modifier = Modifier.clickable { onClearAllRecents() }
                    )
                }
                Spacer(modifier = Modifier.height(dimens.spaceSm))
            }

            items(
                items = recentSearches,
                key = { "recent_$it" }
            ) { search ->
                RecentSearchItem(
                    query = search,
                    onClick = { onRecentClick(search) },
                    onRemove = { onRemoveRecent(search) }
                )
            }

            item(key = "recent_spacer") {
                Spacer(modifier = Modifier.height(dimens.spaceXl))
            }
        }

        // Suggestions section
        item(key = "suggestions_header") {
            Text(
                text = stringResource(R.string.search_suggestions),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = dimens.spaceLg)
            )
            Spacer(modifier = Modifier.height(dimens.spaceSm))
        }

        item(key = "suggestion_chips") {
            LazyRow(
                contentPadding = PaddingValues(horizontal = dimens.spaceLg),
                horizontalArrangement = Arrangement.spacedBy(dimens.spaceSm)
            ) {
                items(suggestionGenres, key = { it.id }) { genre ->
                    SuggestionChip(
                        // TODO: Use label_i18n[locale] when the column is populated in DB
                        label = genre.displayName,
                        onClick = { onSuggestionClick(genre.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun RecentSearchItem(
    query: String,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    val dimens = AppDimens.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = dimens.spaceLg, vertical = dimens.spaceMd),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.AccessTime,
            contentDescription = null,
            tint = TextSecondary,
            modifier = Modifier.size(20.dp)
        )

        Spacer(modifier = Modifier.width(dimens.spaceMd))

        Text(
            text = query,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Icon(
            imageVector = Icons.Default.Close,
            contentDescription = "Remove",
            tint = TextTertiary,
            modifier = Modifier
                .size(18.dp)
                .clickable { onRemove() }
        )
    }
}

@Composable
private fun SuggestionChip(
    label: String,
    onClick: () -> Unit
) {
    val dimens = AppDimens.current

    Box(
        modifier = Modifier
            .background(Black24, RoundedCornerShape(dimens.radiusFull))
            .border(1.dp, ChipBorderInactive, RoundedCornerShape(dimens.radiusFull))
            .clickable(onClick = onClick)
            .padding(horizontal = dimens.spaceMd, vertical = dimens.spaceSm),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
            color = Gray450
        )
    }
}

// ============================================
// RESULTS CONTENT
// ============================================

@Composable
private fun GallerySearchResultsContent(
    templates: List<GallerySearchTemplateItem>,
    songs: List<GallerySearchSongItem>,
    onTemplateClick: (String) -> Unit
) {
    val dimens = AppDimens.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = dimens.spaceMd)
    ) {
        // Templates section
        if (templates.isNotEmpty()) {
            item(key = "templates_header") {
                Text(
                    text = stringResource(R.string.gallery_templates),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = dimens.spaceLg, vertical = dimens.spaceSm)
                )
            }

            item(key = "templates_grid") {
                GallerySearchTemplateGrid(
                    templates = templates,
                    onTemplateClick = onTemplateClick,
                    modifier = Modifier.padding(horizontal = dimens.spaceLg)
                )
            }

            item(key = "templates_spacer") {
                Spacer(modifier = Modifier.height(dimens.spaceXl))
            }
        }

        // Songs section
        if (songs.isNotEmpty()) {
            item(key = "songs_header") {
                Text(
                    text = stringResource(R.string.songs_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = dimens.spaceLg, vertical = dimens.spaceSm)
                )
            }

            items(
                items = songs,
                key = { "song_${it.id}" },
                contentType = { "song" }
            ) { song ->
                SongListItem(
                    name = song.name,
                    artist = song.artist,
                    coverUrl = song.coverUrl,
                    onSongClick = {}
                )
            }
        }
    }
}

@Composable
private fun GallerySearchTemplateGrid(
    templates: List<GallerySearchTemplateItem>,
    onTemplateClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val dimens = AppDimens.current

    // Simple 2-column grid layout
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(dimens.spaceSm)
    ) {
        templates.chunked(2).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(dimens.spaceSm)
            ) {
                rowItems.forEach { template ->
                    GallerySearchTemplateCard(
                        template = template,
                        onClick = { onTemplateClick(template.id) },
                        modifier = Modifier.weight(1f)
                    )
                }
                // Fill empty space if odd number
                if (rowItems.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun GallerySearchTemplateCard(
    template: GallerySearchTemplateItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dimens = AppDimens.current
    val aspectRatio = parseAspectRatio(template.aspectRatio)

    Card(
        onClick = onClick,
        modifier = modifier.aspectRatio(aspectRatio.coerceIn(0.5f, 2f)),
        shape = RoundedCornerShape(dimens.radiusLg),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Primary.copy(alpha = 0.15f),
                            MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                )
        ) {
            // Premium badge
            if (template.isPremium) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(dimens.spaceSm)
                        .background(
                            color = Color(0xFFFFD700),
                            shape = RoundedCornerShape(dimens.radiusMd)
                        )
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "PRO",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = Color.Black
                    )
                }
            }

            // Template name
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(10.dp)
            ) {
                Text(
                    text = template.name,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                    color = TextBright,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// ============================================
// LOADING CONTENT
// ============================================

@Composable
private fun GallerySearchLoadingContent() {
    val dimens = AppDimens.current

    ProvideShimmerEffect {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = dimens.spaceMd),
            userScrollEnabled = false
        ) {
            items(6, contentType = { "song_placeholder" }) {
                SongListItemPlaceholder()
            }
        }
    }
}

// ============================================
// EMPTY CONTENT
// ============================================

@Composable
private fun GallerySearchEmptyContent(query: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.search_no_results),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(AppDimens.current.spaceSm))
            Text(
                text = stringResource(R.string.search_no_results_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
        }
    }
}

// ============================================
// ERROR CONTENT
// ============================================

@Composable
private fun GallerySearchErrorContent(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
        }
    }
}

// ============================================
// HELPERS
// ============================================

private fun parseAspectRatio(aspectRatio: String): Float {
    return try {
        val parts = aspectRatio.split(":")
        if (parts.size == 2) {
            val w = parts[0].toFloatOrNull() ?: 9f
            val h = parts[1].toFloatOrNull() ?: 16f
            w / h
        } else {
            9f / 16f
        }
    } catch (_: Exception) {
        9f / 16f
    }
}

// ============================================
// PREVIEW
// ============================================

@Preview(widthDp = 375, heightDp = 812)
@Composable
private fun GallerySearchTopBarPreview() {
    VideoMakerTheme {
        Column(modifier = Modifier.background(MaterialTheme.colorScheme.surface)) {
            GallerySearchTopBar(
                query = "",
                onQueryChange = {},
                onSearch = {},
                onClearQuery = {},
                onBack = {}
            )
        }
    }
}

@Preview(widthDp = 375, heightDp = 812)
@Composable
private fun GallerySearchIdlePreview() {
    VideoMakerTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
        ) {
            GallerySearchTopBar(
                query = "",
                onQueryChange = {},
                onSearch = {},
                onClearQuery = {},
                onBack = {}
            )
            GallerySearchIdleContent(
                recentSearches = listOf("birthday", "aesthetic", "travel vlog"),
                suggestionGenres = listOf(
                    SongGenre("aesthetic", "Aesthetic"),
                    SongGenre("birthday", "Birthday"),
                    SongGenre("lofi", "Lofi"),
                    SongGenre("cinematic", "Cinematic"),
                ),
                onRecentClick = {},
                onRemoveRecent = {},
                onClearAllRecents = {},
                onSuggestionClick = {}
            )
        }
    }
}

@Preview(widthDp = 375, heightDp = 812)
@Composable
private fun GallerySearchResultsPreview() {
    VideoMakerTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
        ) {
            GallerySearchTopBar(
                query = "aesthetic",
                onQueryChange = {},
                onSearch = {},
                onClearQuery = {},
                onBack = {}
            )
            GallerySearchResultsContent(
                templates = listOf(
                    GallerySearchTemplateItem("1", "Aesthetic Mood", listOf("aesthetic"), "9:16", false),
                    GallerySearchTemplateItem("2", "Chill Lofi", listOf("lofi", "aesthetic"), "1:1", true),
                ),
                songs = listOf(
                    GallerySearchSongItem(1, "Aesthetic Vibes", "Lo-Fi Girl", listOf("lofi")),
                ),
                onTemplateClick = {}
            )
        }
    }
}

@Preview(widthDp = 375, heightDp = 812)
@Composable
private fun GallerySearchEmptyPreview() {
    VideoMakerTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
        ) {
            GallerySearchTopBar(
                query = "xyznonexistent",
                onQueryChange = {},
                onSearch = {},
                onClearQuery = {},
                onBack = {}
            )
            GallerySearchEmptyContent(query = "xyznonexistent")
        }
    }
}
