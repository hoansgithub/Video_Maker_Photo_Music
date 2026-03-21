package com.videomaker.aimusic.modules.editor.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.videomaker.aimusic.R
import com.videomaker.aimusic.domain.model.MusicSong
import com.videomaker.aimusic.domain.model.SongGenre
import com.videomaker.aimusic.modules.songsearch.SongSearchUiState
import com.videomaker.aimusic.modules.songsearch.SongSearchViewModel
import com.videomaker.aimusic.ui.components.AppFilterChip
import com.videomaker.aimusic.ui.components.SongListItem
import com.videomaker.aimusic.ui.theme.AppDimens
import com.videomaker.aimusic.ui.theme.SearchFieldBackground
import com.videomaker.aimusic.ui.theme.SearchFieldBorder
import com.videomaker.aimusic.ui.theme.SplashBackground
import com.videomaker.aimusic.ui.theme.TextPrimary
import com.videomaker.aimusic.ui.theme.TextSecondary
import com.videomaker.aimusic.ui.theme.TextTertiary

/**
 * Music Search Bottom Sheet (Full Screen)
 * Allows searching and selecting music tracks
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MusicSearchBottomSheet(
    viewModel: SongSearchViewModel,
    onSongSelected: (MusicSong) -> Unit,
    onDismiss: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val displayText by viewModel.displayText.collectAsStateWithLifecycle()
    val genres by viewModel.genres.collectAsStateWithLifecycle()
    val suggestedSongs by viewModel.suggestedSongs.collectAsStateWithLifecycle()
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    val dimens = AppDimens.current

    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true
    )

    // Dismiss keyboard when sheet is being dragged
    LaunchedEffect(sheetState.currentValue) {
        focusManager.clearFocus()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SplashBackground,
        dragHandle = null,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        modifier = Modifier.fillMaxHeight() // Full screen height, covers all content including tabs
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
                .padding(top = 24.dp, bottom = 32.dp)
        ) {
            // Title
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.song_search_change_music),
                    color = TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )

                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.close),
                        tint = TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Search field - matching SongSearchTopBar style
            Box(
                modifier = Modifier
                    .fillMaxWidth()
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
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = TextTertiary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(dimens.spaceSm))
                    Box(modifier = Modifier.weight(1f)) {
                        if (displayText.isEmpty()) {
                            Text(
                                text = stringResource(R.string.song_search_hint),
                                style = MaterialTheme.typography.titleSmall,
                                color = TextTertiary
                            )
                        }
                        BasicTextField(
                            value = displayText,
                            onValueChange = viewModel::onQueryChange,
                            singleLine = true,
                            textStyle = MaterialTheme.typography.titleSmall.copy(
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(
                                onSearch = {
                                    viewModel.onSearch()
                                    keyboardController?.hide()
                                }
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester)
                        )
                    }
                    if (displayText.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(dimens.spaceXs))
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.close),
                            tint = TextTertiary,
                            modifier = Modifier
                                .size(20.dp)
                                .clickable { viewModel.onClearQuery() }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Results
            when (val state = uiState) {
                is SongSearchUiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }

                is SongSearchUiState.Results -> {
                    val listState = rememberLazyListState()

                    // Aggressive keyboard dismissal on ANY scroll movement
                    LaunchedEffect(listState) {
                        var previousIndex = listState.firstVisibleItemIndex
                        var previousOffset = listState.firstVisibleItemScrollOffset

                        snapshotFlow {
                            listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
                        }.collect { (index, offset) ->
                            if (index != previousIndex || kotlin.math.abs(offset - previousOffset) > 0) {
                                focusManager.clearFocus()
                            }
                            previousIndex = index
                            previousOffset = offset
                        }
                    }

                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(state.songs, key = { "song_${it.id}" }) { song ->
                            SongListItem(
                                name = song.name,
                                artist = song.artist,
                                coverUrl = song.coverUrl,
                                onSongClick = {
                                    focusManager.clearFocus()
                                    keyboardController?.hide()
                                    onSongSelected(song)
                                }
                            )
                        }
                    }
                }

                is SongSearchUiState.Empty -> {
                    val listState = rememberLazyListState()

                    // Aggressive keyboard dismissal on ANY scroll movement
                    LaunchedEffect(listState) {
                        var previousIndex = listState.firstVisibleItemIndex
                        var previousOffset = listState.firstVisibleItemScrollOffset

                        snapshotFlow {
                            listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
                        }.collect { (index, offset) ->
                            if (index != previousIndex || kotlin.math.abs(offset - previousOffset) > 0) {
                                focusManager.clearFocus()
                            }
                            previousIndex = index
                            previousOffset = offset
                        }
                    }

                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = dimens.spaceMd)
                    ) {
                        // Empty message
                        item(key = "empty_message") {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = dimens.spaceXxl),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MusicNote,
                                    contentDescription = null,
                                    tint = TextSecondary,
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(dimens.spaceMd))
                                Text(
                                    text = stringResource(R.string.song_search_no_results),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(dimens.spaceSm))
                                Text(
                                    text = stringResource(R.string.search_no_results_hint),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary
                                )
                            }
                        }

                        // Genres section
                        if (genres.isNotEmpty()) {
                            item(key = "empty_genre_header") {
                                Text(
                                    text = stringResource(R.string.song_search_explore_by_genre),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(horizontal = dimens.spaceMd)
                                )
                                Spacer(modifier = Modifier.height(dimens.spaceSm))
                            }
                            item(key = "empty_genre_chips") {
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = dimens.spaceMd),
                                    horizontalArrangement = Arrangement.spacedBy(dimens.spaceSm)
                                ) {
                                    items(genres, key = { it.id }) { genre ->
                                        AppFilterChip(
                                            text = genre.displayName,
                                            onClick = { viewModel.onGenreClick(genre) }
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(dimens.spaceXl))
                            }
                        }

                        // Suggested songs section
                        if (suggestedSongs.isNotEmpty()) {
                            item(key = "empty_suggested_header") {
                                Text(
                                    text = stringResource(R.string.song_search_suggested),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(horizontal = dimens.spaceMd)
                                )
                                Spacer(modifier = Modifier.height(dimens.spaceSm))
                            }
                            items(suggestedSongs, key = { "empty_song_${it.id}" }) { song ->
                                SongListItem(
                                    name = song.name,
                                    artist = song.artist,
                                    coverUrl = song.coverUrl,
                                    onSongClick = {
                                        focusManager.clearFocus()
                                        keyboardController?.hide()
                                        onSongSelected(song)
                                    }
                                )
                            }
                        }
                    }
                }

                is SongSearchUiState.Error -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = state.message,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 16.sp
                        )
                    }
                }

                is SongSearchUiState.Idle -> {
                    val listState = rememberLazyListState()

                    // Aggressive keyboard dismissal on ANY scroll movement
                    LaunchedEffect(listState) {
                        var previousIndex = listState.firstVisibleItemIndex
                        var previousOffset = listState.firstVisibleItemScrollOffset

                        snapshotFlow {
                            listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
                        }.collect { (index, offset) ->
                            if (index != previousIndex || kotlin.math.abs(offset - previousOffset) > 0) {
                                focusManager.clearFocus()
                            }
                            previousIndex = index
                            previousOffset = offset
                        }
                    }

                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = dimens.spaceMd)
                    ) {
                        // Genres section
                        if (genres.isNotEmpty()) {
                            item(key = "genre_header") {
                                Text(
                                    text = stringResource(R.string.song_search_explore_by_genre),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(horizontal = dimens.spaceMd)
                                )
                                Spacer(modifier = Modifier.height(dimens.spaceSm))
                            }
                            item(key = "genre_chips") {
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = dimens.spaceMd),
                                    horizontalArrangement = Arrangement.spacedBy(dimens.spaceSm)
                                ) {
                                    items(genres, key = { it.id }) { genre ->
                                        AppFilterChip(
                                            text = genre.displayName,
                                            onClick = { viewModel.onGenreClick(genre) }
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(dimens.spaceXl))
                            }
                        }

                        // Suggested songs section
                        if (suggestedSongs.isNotEmpty()) {
                            item(key = "suggested_header") {
                                Text(
                                    text = stringResource(R.string.song_search_suggested),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(horizontal = dimens.spaceMd)
                                )
                                Spacer(modifier = Modifier.height(dimens.spaceSm))
                            }
                            items(suggestedSongs, key = { "suggested_${it.id}" }) { song ->
                                SongListItem(
                                    name = song.name,
                                    artist = song.artist,
                                    coverUrl = song.coverUrl,
                                    onSongClick = {
                                        focusManager.clearFocus()
                                        keyboardController?.hide()
                                        onSongSelected(song)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}
