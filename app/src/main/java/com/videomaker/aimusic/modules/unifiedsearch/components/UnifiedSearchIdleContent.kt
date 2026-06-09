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
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import co.alcheclub.lib.acccore.ads.compose.NativeAdView
import com.videomaker.aimusic.BuildConfig
import com.videomaker.aimusic.R
import com.videomaker.aimusic.core.analytics.Analytics
import com.videomaker.aimusic.core.analytics.AnalyticsEvent
import com.videomaker.aimusic.core.analytics.onFirstVisible
import com.videomaker.aimusic.core.analytics.trackSongImpressionAndMark
import com.videomaker.aimusic.core.playback.MusicPlaybackSessionManager
import org.koin.compose.koinInject
import com.videomaker.aimusic.core.constants.AdPlacement
import com.videomaker.aimusic.domain.model.MusicSong
import com.videomaker.aimusic.domain.model.SongGenre
import com.videomaker.aimusic.domain.model.VibeTag
import com.videomaker.aimusic.domain.model.VideoTemplate
import com.videomaker.aimusic.navigation.SearchSection
import com.videomaker.aimusic.ui.components.AppFilterChip
import com.videomaker.aimusic.ui.components.ModifierExtension.clickableSingle
import co.alcheclub.lib.acccore.ads.loader.AdsLoaderService
import com.videomaker.aimusic.ui.components.DEFAULT_INFEED_INTERVAL
import com.videomaker.aimusic.ui.components.SongFeedItem
import com.videomaker.aimusic.ui.components.SongListItem
import com.videomaker.aimusic.ui.components.buildSongFeedWithAds
import com.videomaker.aimusic.ui.components.songFeedItemKey
import com.videomaker.aimusic.ui.theme.AppDimens
import com.videomaker.aimusic.ui.theme.TextSecondary
import com.videomaker.aimusic.ui.theme.TextTertiary
import com.videomaker.aimusic.core.ads.AdClickDetector

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
    onSongClick: (MusicSong, String) -> Unit,
    onSeeMoreTemplates: () -> Unit,
    onSeeMoreSongs: () -> Unit
) {
    val adClickDetector: AdClickDetector = koinInject()
    val dimens = AppDimens.current
    val sessionManager: MusicPlaybackSessionManager = koinInject()
    val adsLoaderService: AdsLoaderService = koinInject()
    val searchInfeedInterval = remember {
        adsLoaderService.getPlacementConfig(AdPlacement.NATIVE_SEARCH_MUSIC_INFEED)
            ?.extras?.get("infeed_interval")
            ?.toString()?.toIntOrNull()
            ?: DEFAULT_INFEED_INTERVAL
    }

    // Build feed items at composable scope (not inside LazyListScope) to avoid recomputing on every recomposition
    val idleFeedItems = remember(suggestedSongs, searchInfeedInterval) {
        buildSongFeedWithAds(suggestedSongs, searchInfeedInterval)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth(),
        contentPadding = PaddingValues(vertical = dimens.spaceMd)
    ) {
        // Space for overlaying search bar
        item {
            Spacer(Modifier.height(100.dp))
        }

        // Native ad - right after search bar space
        item {
            key(AdPlacement.NATIVE_SEARCH_INFEED) {
                android.util.Log.d("UnifiedSearch", "🔵 Composing NativeAdView (Idle)")
                NativeAdView(
                    placement = AdPlacement.NATIVE_SEARCH_INFEED,
                    modifier = Modifier.fillMaxWidth().height(100.dp),
                    isDebug = BuildConfig.DEBUG,
                    onAdClicked = { adClickDetector.onAdClick(it) }
                )
            }
        }

        // Spacing after ad
        item {
            Spacer(Modifier.height(dimens.spaceMd))
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
                                            onClick = {
                                                Analytics.trackTemplateGenreClick(
                                                    genreId = tag.id,
                                                    genreName = tag.displayName,
                                                    location = AnalyticsEvent.Value.Location.SEARCH
                                                )
                                                onVibeTagClick(tag)
                                            }
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
                                onTemplateClick = { template ->
                                    Analytics.trackTemplateClick(
                                        templateId = template.id,
                                        templateName = template.name,
                                        location = AnalyticsEvent.Value.Location.SEARCH_RCM,
                                        isPremium = template.isPremium
                                    )
                                    onTemplateClick.invoke(template.id)
                                },
                                modifier = Modifier.padding(horizontal = dimens.spaceLg),
                                impressionLocation = AnalyticsEvent.Value.Location.SEARCH_RCM
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
                                            onClick = {
                                                Analytics.trackSongGenreClick(
                                                    genreId = genre.id,
                                                    genreName = genre.displayName,
                                                    location = AnalyticsEvent.Value.Location.SEARCH
                                                )
                                                onGenreClick(genre)
                                            }
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(dimens.spaceSm))
                            }
                        }

                        item(key = "suggested_header") {
                            UnifiedSectionHeader(text = stringResource(R.string.search_music_suggestion))
                        }
                        items(
                            items = idleFeedItems,
                            key = { item -> songFeedItemKey(item, "idle_") },
                            contentType = { item ->
                                when (item) {
                                    is SongFeedItem.Song -> "song"
                                    is SongFeedItem.Ad -> "ad"
                                }
                            }
                        ) { item ->
                            when (item) {
                                is SongFeedItem.Song -> {
                                    val song = item.song
                                    SongListItem(
                                        name = song.name,
                                        artist = song.artist,
                                        coverUrl = song.coverUrl,
                                        onSongClick = {
                                            Analytics.trackSongClick(
                                                songId = song.id.toString(),
                                                songName = song.name,
                                                location = AnalyticsEvent.Value.Location.SEARCH_RCM,
                                                isPremium = song.isPremium
                                            )
                                            onSongClick(song, AnalyticsEvent.Value.Location.SEARCH_RCM)
                                        },
                                        modifier = Modifier.onFirstVisible(key = song.id) {
                                            sessionManager.trackSongImpressionAndMark(
                                                songId = song.id.toString(),
                                                songName = song.name,
                                                location = AnalyticsEvent.Value.Location.SEARCH_RCM,
                                                isPremium = song.isPremium
                                            )
                                        }
                                    )
                                }
                                is SongFeedItem.Ad -> {
                                    NativeAdView(
                                        placement = AdPlacement.NATIVE_SEARCH_MUSIC_INFEED,
                                        modifier = Modifier.fillMaxWidth().height(100.dp),
                                        autoLoad = true,
                                        isDebug = BuildConfig.DEBUG,
                                        onAdClicked = { adClickDetector.onAdClick(it) }
                                    )
                                }
                            }
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

        item {
            Spacer(Modifier.height(150.dp))
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
            .clickableSingle(onClick = onClick)
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
                .clickableSingle { onRemove() }
        )
    }
}
