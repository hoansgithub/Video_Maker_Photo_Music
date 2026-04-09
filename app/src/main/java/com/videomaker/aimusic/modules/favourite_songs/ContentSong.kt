package com.videomaker.aimusic.modules.favourite_songs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.videomaker.aimusic.core.analytics.Analytics
import com.videomaker.aimusic.core.analytics.AnalyticsEvent
import com.videomaker.aimusic.domain.model.MusicSong
import com.videomaker.aimusic.ui.components.SongListItem
import com.videomaker.aimusic.ui.theme.AppDimens

@Composable
fun ContentSong(
    songs: List<MusicSong>,
    onSongClick: (MusicSong) -> Unit,
    onDeleteSongClick: (MusicSong) -> Unit,
) {
    val dimens = AppDimens.current
    val screenSessionId = remember { Analytics.newScreenSessionId() }

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

        items(songs, key = { song -> song.id }) { song ->
            LaunchedEffect(song.id, screenSessionId) {
                Analytics.trackSongImpression(
                    songId = song.id.toString(),
                    songName = song.name,
                    location = AnalyticsEvent.Value.Location.SONG_FAVORITE,
                    screenSessionId = screenSessionId
                )
            }
            SongListItem(
                name = song.name,
                artist = song.artist,
                coverUrl = song.coverUrl,
                isShowOption = true,
                onClickDelete = {
                    onDeleteSongClick.invoke(song)
                },
                onSongClick = { onSongClick(song) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp)
            )
        }
    }
}
