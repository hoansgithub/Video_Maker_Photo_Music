package com.videomaker.aimusic.modules.onboarding

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import co.alcheclub.lib.acccore.remoteconfig.RemoteConfig
import com.videomaker.aimusic.R
import com.videomaker.aimusic.core.constants.RemoteConfigKeys
import com.videomaker.aimusic.core.data.local.RegionProvider
import com.videomaker.aimusic.domain.repository.SongRepository
import com.videomaker.aimusic.domain.repository.TemplateRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class OnboardingContentViewModel(
    application: Application,
    private val templateRepository: TemplateRepository,
    private val songRepository: SongRepository,
    private val regionProvider: RegionProvider,
    private val remoteConfig: RemoteConfig
) : AndroidViewModel(application) {

    private val _contentState = MutableStateFlow(OnboardingContentState())
    val contentState: StateFlow<OnboardingContentState> = _contentState.asStateFlow()

    private var preloadJob: Job? = null

    private val isInGeo: Boolean
        get() = try {
            regionProvider.getRegionCode() == "in"
        } catch (e: Exception) {
            false
        }

    fun preloadContent() {
        // VM is a singleton — guard against duplicate fetch if both
        // LanguageSelectionActivity and OnboardingActivity (or re-entries) call this.
        if (_contentState.value.isReady || preloadJob?.isActive == true) return
        preloadJob = viewModelScope.launch {
            try {
                // 1. Fetch page 3 templates first (priority for dedup)
                val page3Templates = fetchPage3Templates()

                // 2. Resolve page 1 content (exclude page 3 template IDs)
                val page3Ids = page3Templates.map { it.id }.toSet()
                val (page1Video, page1Url, page1Local) = resolvePage1Content(page3Ids)

                // 3. Resolve page 2 thumbnail (songs, no overlap with templates)
                val (page2Url, page2Local) = resolvePage2Thumbnail()

                // 4. Build page 3 thumbnail URLs and local fallbacks
                val page3Urls = page3Templates.map { it.thumbnailPath }
                val page3Locals = buildPage3LocalFallbacks(page3Templates.size)

                _contentState.value = OnboardingContentState(
                    page1VideoUrl = page1Video,
                    page1ThumbnailUrl = page1Url,
                    page1LocalFallback = page1Local,
                    page2ThumbnailUrl = page2Url,
                    page2LocalFallback = page2Local,
                    page3Thumbnails = page3Urls,
                    page3LocalFallbacks = page3Locals,
                    isReady = true
                )

                Log.d(TAG, "Preload complete: p1video=${page1Video != null}, p1thumb=${page1Url != null}, p2=${page2Url != null}, p3=${page3Urls.size} thumbnails")
            } catch (e: Exception) {
                Log.e(TAG, "Preload failed, using local fallbacks", e)
                _contentState.value = OnboardingContentState(
                    page1LocalFallback = localPage1(),
                    page2LocalFallback = localPage2(),
                    page3LocalFallbacks = buildPage3LocalFallbacks(0),
                    isReady = true
                )
            }
        }
    }

    private suspend fun fetchPage3Templates() =
        templateRepository.getFeaturedTemplates(limit = 3)
            .getOrElse { emptyList() }

    // Returns (videoUrl, thumbnailUrl, localFallback)
    private suspend fun resolvePage1Content(excludeIds: Set<String>): Triple<String?, String?, Int> {
        val localFallback = localPage1()

        val configTemplateId = try {
            val id = remoteConfig.getString(RemoteConfigKeys.ONBOARDING_PAGE1_TEMPLATE_ID)
            id.ifBlank { null }
        } catch (e: Exception) {
            null
        }

        if (configTemplateId != null) {
            val template = templateRepository.getTemplateById(configTemplateId).getOrNull()
            if (template != null && template.thumbnailPath.isNotBlank() && template.id !in excludeIds) {
                return Triple(template.videoUrl, template.thumbnailPath, localFallback)
            }
        }

        // Fallback: top 1 featured template (excluding page 3 templates)
        val featured = templateRepository.getFeaturedTemplates(limit = 4)
            .getOrElse { emptyList() }
            .firstOrNull { it.id !in excludeIds && it.thumbnailPath.isNotBlank() }

        if (featured != null) {
            return Triple(featured.videoUrl, featured.thumbnailPath, localFallback)
        }

        return Triple(null, null, localFallback)
    }

    private suspend fun resolvePage2Thumbnail(): Pair<String?, Int> {
        val localFallback = localPage2()

        // Try remote config song ID
        val configSongId = try {
            val id = remoteConfig.getLong(RemoteConfigKeys.ONBOARDING_PAGE2_SONG_ID, 0L)
            if (id > 0) id.toInt() else null
        } catch (e: Exception) {
            null
        }

        if (configSongId != null) {
            val song = songRepository.getSongById(configSongId.toLong()).getOrNull()
            if (song != null && song.coverUrl.isNotBlank()) {
                return Pair(song.coverUrl, localFallback)
            }
        }

        // Fallback: top 1 featured song
        val featured = songRepository.getFeaturedSongs(limit = 1)
            .getOrElse { emptyList() }
            .firstOrNull { it.coverUrl.isNotBlank() }

        if (featured != null) {
            return Pair(featured.coverUrl, localFallback)
        }

        return Pair(null, localFallback)
    }

    private fun buildPage3LocalFallbacks(templateCount: Int): List<Int> {
        val locals = if (isInGeo) {
            listOf(R.drawable.img_onb31_in, R.drawable.img_onb32_in, R.drawable.img_onb33_in)
        } else {
            listOf(R.drawable.ob_page3)
        }
        val needed = 3 - templateCount
        if (needed <= 0) return emptyList()
        return locals.take(needed)
    }

    private fun localPage1(): Int =
        if (isInGeo) R.drawable.img_fall_back_onb1 else R.drawable.ob_page1

    private fun localPage2(): Int =
        if (isInGeo) R.drawable.img_fall_back_onb2 else R.drawable.img_song1

    companion object {
        private const val TAG = "OnboardingContentVM"
    }
}
