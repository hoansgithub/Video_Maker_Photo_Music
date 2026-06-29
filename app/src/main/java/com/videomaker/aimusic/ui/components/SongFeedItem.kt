package com.videomaker.aimusic.ui.components

import androidx.compose.runtime.Immutable
import com.videomaker.aimusic.domain.model.MusicSong

/**
 * Sealed class representing items in a song feed that can contain interleaved native ads.
 * Used across multiple screens: station songs, weekly ranking, suggested songs, search results.
 */
@Immutable
sealed class SongFeedItem {
    data class Song(val song: MusicSong) : SongFeedItem()
    data class Ad(val index: Int) : SongFeedItem()
}

/** Default interval for in-feed ads if config is unavailable */
const val DEFAULT_INFEED_INTERVAL = 10

/**
 * Build a song feed list with native ads inserted every [interval] songs.
 * If total songs < [interval] but >= 1, a single ad is placed after the last song.
 *
 * @param songs The original song list
 * @param interval Insert an ad after every N songs (e.g. 10 = ad after 10th, 20th, 30th…)
 * @return List of [SongFeedItem] with ads interleaved
 */
fun buildSongFeedWithAds(
    songs: List<MusicSong>,
    interval: Int
): List<SongFeedItem> {
    if (songs.isEmpty() || interval <= 0) return songs.map { SongFeedItem.Song(it) }

    val result = mutableListOf<SongFeedItem>()
    var adIndex = 0

    for (i in songs.indices) {
        result.add(SongFeedItem.Song(songs[i]))
        if ((i + 1) % interval == 0) {
            result.add(SongFeedItem.Ad(adIndex++))
        }
    }

    // If fewer songs than interval but at least 1, show ad after last song
    if (songs.size in 1 until interval) {
        result.add(SongFeedItem.Ad(0))
    }

    return result
}

/**
 * Stable key for LazyColumn items. Prefix avoids collisions with other items in the same list.
 */
fun songFeedItemKey(item: SongFeedItem, prefix: String = ""): String = when (item) {
    is SongFeedItem.Song -> "${prefix}song_${item.song.id}"
    is SongFeedItem.Ad -> "${prefix}ad_infeed_${item.index}"
}
