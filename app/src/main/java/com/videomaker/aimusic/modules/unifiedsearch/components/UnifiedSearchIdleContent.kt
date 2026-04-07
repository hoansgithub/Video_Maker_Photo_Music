package com.videomaker.aimusic.modules.unifiedsearch.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.videomaker.aimusic.R
import com.videomaker.aimusic.domain.model.MusicSong
import com.videomaker.aimusic.domain.model.SongGenre
import com.videomaker.aimusic.domain.model.VibeTag
import com.videomaker.aimusic.domain.model.VideoTemplate
import com.videomaker.aimusic.navigation.SearchSection
import com.videomaker.aimusic.ui.components.AppFilterChip
import com.videomaker.aimusic.ui.components.SongListItem
import com.videomaker.aimusic.ui.theme.AppDimens
import com.videomaker.aimusic.ui.theme.TextSecondary
import com.videomaker.aimusic.ui.theme.TextTertiary

@Composable
fun UnifiedSearchIdleContent(
    initialSection: SearchSection,
    recentSearches: List<String>,
    suggestionVibeTags: List<VibeTag>,
    genres: List<SongGenre>,
    featuredTemplates: List<VideoTemplate>,
    suggestedSongs: List<MusicSong>,
    hasMoreFeaturedTemplates: Boolean,
    isLoadingMoreFeaturedTemplates: Boolean,
    hasMoreSuggestedSongs: Boolean,
    isLoadingMoreSuggestedSongs: Boolean,
    onRecentClick: (String) -> Unit,
    onRemoveRecent: (String) -> Unit,
    onClearAllRecents: () -> Unit,
    onVibeTagClick: (VibeTag) -> Unit,
    onGenreClick: (SongGenre) -> Unit,
    onTemplateClick: (String) -> Unit,
    onSongClick: (MusicSong) -> Unit,
    onSeeMoreTemplates: () -> Unit,
    onSeeMoreSongs: () -> Unit
) {
    val dimens = AppDimens.current

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth(),
        contentPadding = PaddingValues(vertical = dimens.spaceMd)
    ) {
        item {
            Spacer(Modifier.height(100.dp))
        }
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
                }
                Spacer(modifier = Modifier.height(dimens.spaceSm))
            }

            items(recentSearches.take(3), key = { "recent_$it" }) { search ->
                UnifiedRecentSearchItem(
                    query = search,
                    onClick = { onRecentClick(search) },
                    onRemove = { onRemoveRecent(search) }
                )
            }

            item(key = "recent_spacer") {
                Spacer(modifier = Modifier.height(dimens.spaceXl))
            }
        }

        val renderOrder = if (initialSection == SearchSection.TEMPLATES) {
            listOf(SearchSection.TEMPLATES, SearchSection.MUSIC)
        } else {
            listOf(SearchSection.MUSIC, SearchSection.TEMPLATES)
        }
        renderOrder.forEach { section ->
            when (section) {
                SearchSection.TEMPLATES -> {
                    if (featuredTemplates.isNotEmpty()) {

                        if (suggestionVibeTags.isNotEmpty()) {
                            item(key = "theme_header") {
                                Text(
                                    text = stringResource(R.string.search_browse_by_theme),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(horizontal = dimens.spaceLg)
                                )
                                Spacer(modifier = Modifier.height(dimens.spaceSm))
                            }

                            item(key = "theme_chips") {
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = dimens.spaceLg),
                                    horizontalArrangement = Arrangement.spacedBy(dimens.spaceSm)
                                ) {
                                    items(suggestionVibeTags, key = { it.id }) { tag ->
                                        AppFilterChip(
                                            text = if (tag.emoji.isNotEmpty()) "${tag.emoji} ${tag.displayName}" else tag.displayName,
                                            onClick = { onVibeTagClick(tag) }
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(dimens.spaceSm))
                            }
                        }

                        item(key = "featured_header") {
                            UnifiedSectionHeader(text = stringResource(R.string.search_templates_suggestions))
                        }
                        item(key = "featured_grid") {
                            Spacer(modifier = Modifier.height(dimens.spaceSm))
                            UnifiedTemplateGrid(
                                templates = featuredTemplates,
                                onTemplateClick = onTemplateClick,
                                modifier = Modifier.padding(horizontal = dimens.spaceLg)
                            )
                            Spacer(modifier = Modifier.height(dimens.spaceXl))
                        }
                        item(key = "featured_see_more") {
                            UnifiedSeeMore(
                                visible = hasMoreFeaturedTemplates,
                                isLoading = isLoadingMoreFeaturedTemplates,
                                onClick = onSeeMoreTemplates
                            )
                            Spacer(modifier = Modifier.height(dimens.spaceXl))
                        }
                    }
                }

                SearchSection.MUSIC -> {
                    if (suggestedSongs.isNotEmpty()) {
                        if (genres.isNotEmpty()) {
                            item(key = "genre_header") {
                                Text(
                                    text = stringResource(R.string.song_search_explore_by_genre),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(horizontal = dimens.spaceLg)
                                )
                                Spacer(modifier = Modifier.height(dimens.spaceSm))
                            }
                            item(key = "genre_chips") {
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = dimens.spaceLg),
                                    horizontalArrangement = Arrangement.spacedBy(dimens.spaceSm)
                                ) {
                                    items(genres, key = { it.id }) { genre ->
                                        AppFilterChip(
                                            text = genre.displayName,
                                            onClick = { onGenreClick(genre) }
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(dimens.spaceSm))
                            }
                        }

                        item(key = "suggested_header") {
                            UnifiedSectionHeader(text = stringResource(R.string.search_music_suggestion))
                        }
                        items(suggestedSongs, key = { "suggested_${it.id}" }) { song ->
                            SongListItem(
                                name = song.name,
                                artist = song.artist,
                                coverUrl = song.coverUrl,
                                onSongClick = { onSongClick(song) }
                            )
                        }

                        item(key = "suggested_see_more") {
                            Spacer(modifier = Modifier.height(dimens.spaceXl))
                            UnifiedSeeMore(
                                visible = hasMoreSuggestedSongs,
                                isLoading = isLoadingMoreSuggestedSongs,
                                onClick = onSeeMoreSongs
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UnifiedRecentSearchItem(
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
            painter = painterResource(R.drawable.ic_search_history),
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
            contentDescription = stringResource(R.string.remove),
            tint = TextTertiary,
            modifier = Modifier
                .size(18.dp)
                .clickable { onRemove() }
        )
    }
}
