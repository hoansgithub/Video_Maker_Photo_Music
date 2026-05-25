package com.videomaker.aimusic.modules.songs

import com.videomaker.aimusic.domain.model.MusicSong
import com.videomaker.aimusic.domain.model.SongGenre
import kotlin.random.Random

/**
 * Returns the first song in [queue] that is neither the current song nor in [impressed].
 * Returns null if no such song exists — caller should then fall back to a new-genre fetch.
 */
internal fun findNextUnimpressedSong(
    queue: List<MusicSong>,
    currentId: Long,
    impressed: Set<Long>
): MusicSong? = queue.firstOrNull { it.id != currentId && it.id !in impressed }

/**
 * Picks a random genre from [allGenres] excluding [usedGenres] and the [initialGenre]
 * the user opened the sheet with. Returns null when no candidates remain — caller should
 * then loop replay from the initial playlist.
 */
internal fun pickRandomAvailableGenre(
    allGenres: List<SongGenre>,
    usedGenres: Set<String>,
    initialGenre: String?,
    random: Random
): SongGenre? {
    val excluded = usedGenres + listOfNotNull(initialGenre)
    val available = allGenres.filter { it.id !in excluded }
    if (available.isEmpty()) return null
    return available.random(random)
}

/**
 * Appends [toAppend] to [existing], evicting from the front so the result is at most [cap]
 * songs long. Used to bound the playback queue.
 */
internal fun appendCapped(
    existing: List<MusicSong>,
    toAppend: List<MusicSong>,
    cap: Int
): List<MusicSong> {
    val combined = existing + toAppend
    return if (combined.size <= cap) combined else combined.takeLast(cap)
}

/**
 * Selects the song to replay when all genres are exhausted. Prefers the first song in the
 * initial playlist that isn't currently playing; falls back to the current song; returns
 * null only if the initial playlist is empty.
 */
internal fun pickReplaySong(
    initialPlaylist: List<MusicSong>,
    currentId: Long
): MusicSong? {
    if (initialPlaylist.isEmpty()) return null
    return initialPlaylist.firstOrNull { it.id != currentId } ?: initialPlaylist.first()
}
