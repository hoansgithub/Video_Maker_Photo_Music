package com.videomaker.aimusic.core.popup

import com.videomaker.aimusic.domain.model.MusicSong
import com.videomaker.aimusic.domain.model.VideoTemplate
import com.videomaker.aimusic.domain.repository.SongRepository
import com.videomaker.aimusic.domain.repository.TemplateRepository
import kotlin.random.Random

/**
 * Picks one trending item (template or song) for the popup.
 *
 * Algorithm: fetch top [TOP_K] from a GEO-sorted source, filter out [excludeIds],
 * return a uniform-random pick from the remainder. Returns `null` if no eligible
 * item exists (empty repo, network failure, or all top-K already shown today).
 */
class TrendingPopupSelector(
    private val templateRepository: TemplateRepository,
    private val songRepository: SongRepository,
    private val random: Random = Random.Default
) {

    suspend fun pickTemplate(excludeIds: Set<String>): VideoTemplate? {
        val candidates = templateRepository.getTemplates(limit = TOP_K, offset = 0)
            .getOrNull()
            .orEmpty()
        val eligible = candidates.filter { it.id !in excludeIds }
        return eligible.randomOrNull(random)
    }

    suspend fun pickSong(excludeIds: Set<String>): MusicSong? {
        val candidates = songRepository.getFeaturedSongs(limit = TOP_K, offset = 0)
            .getOrNull()
            .orEmpty()
        val eligible = candidates.filter { it.id.toString() !in excludeIds }
        return eligible.randomOrNull(random)
    }

    companion object {
        const val TOP_K = 10
    }
}
