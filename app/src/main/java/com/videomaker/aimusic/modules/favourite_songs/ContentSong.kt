package com.videomaker.aimusic.modules.favourite_songs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import co.alcheclub.lib.acccore.ads.compose.NativeAdView
import com.videomaker.aimusic.BuildConfig
import com.videomaker.aimusic.core.analytics.Analytics
import com.videomaker.aimusic.core.analytics.AnalyticsEvent
import com.videomaker.aimusic.core.analytics.onFirstVisible
import com.videomaker.aimusic.core.analytics.trackSongImpressionAndMark
import com.videomaker.aimusic.core.playback.MusicPlaybackSessionManager
import org.koin.compose.koinInject
import com.videomaker.aimusic.core.constants.AdPlacement
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.videomaker.aimusic.domain.model.MusicSong
import com.videomaker.aimusic.ui.components.SongListItem
import com.videomaker.aimusic.ui.theme.AppDimens

@Stable
private sealed class SongListItemType {
    data class SongItem(val song: MusicSong) : SongListItemType()
    data object AdItem : SongListItemType()
}

@Composable
fun ContentSong(
    songs: List<MusicSong>,
    onSongClick: (MusicSong) -> Unit,
    onDeleteSongClick: (MusicSong) -> Unit,
) {
    val dimens = AppDimens.current
    val sessionManager: MusicPlaybackSessionManager = koinInject()

    val listItems = remember(songs) {
        buildList {
            songs.forEachIndexed { index, song ->
                add(SongListItemType.SongItem(song))
                if (index == 0) {
                    add(SongListItemType.AdItem)
                }
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)),
        contentPadding = PaddingValues(
            top = dimens.spaceLg,
            bottom = dimens.space3Xl + dimens.space2Xl
        ),
        verticalArrangement = Arrangement.spacedBy(dimens.spaceSm)
    ) {
        items(
            count = listItems.size,
            key = { index ->
                when (val item = listItems[index]) {
                    is SongListItemType.SongItem -> item.song.id
                    is SongListItemType.AdItem -> "ad_liked_songs"
                }
            }
        ) { index ->
            when (val item = listItems[index]) {
                is SongListItemType.SongItem -> {
                    SongListItem(
                        name = item.song.name,
                        artist = item.song.artist,
                        coverUrl = item.song.coverUrl,
                        isShowOption = true,
                        onClickDelete = {
                            onDeleteSongClick.invoke(item.song)
                        },
                        onSongClick = { onSongClick(item.song) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 22.dp)
                            .onFirstVisible(key = item.song.id) {
                                sessionManager.trackSongImpressionAndMark(
                                    songId = item.song.id.toString(),
                                    songName = item.song.name,
                                    location = AnalyticsEvent.Value.Location.SONG_FAVORITE
                                )
                            }
                    )
                }
                is SongListItemType.AdItem -> {
                    NativeAdView(
                        placement = AdPlacement.NATIVE_LIBRARY_CREATED_VIDEO,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 22.dp),
                        isDebug = BuildConfig.DEBUG
                    )
                }
            }
        }

        item {
            Spacer(Modifier.height(150.dp))
        }
    }
}
