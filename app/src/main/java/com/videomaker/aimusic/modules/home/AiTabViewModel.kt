package com.videomaker.aimusic.modules.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.videomaker.aimusic.domain.model.VideoTemplate
import com.videomaker.aimusic.domain.repository.TemplateRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * State for the AI tab's two preview rows. Each row shows a short, non-scrollable slice of
 * templates fetched by vibe tag; the "See all" arrow opens the full list filtered to that tag.
 */
data class AiTabUiState(
    val videoGenerator: List<VideoTemplate> = emptyList(),
    val dance: List<VideoTemplate> = emptyList(),
    val isLoading: Boolean = true,
)

/**
 * Loads the AI tab preview rows by reusing the existing per-vibe-tag template RPC
 * ([TemplateRepository.getTemplatesByVibeTag], backed by `get_templates_by_tag_sorted`):
 *  - "AI Video Generator" → vibe tag [TAG_VIDEO_GENERATOR]
 *  - "AI Dance"           → vibe tag [TAG_DANCE]
 */
class AiTabViewModel(
    private val templateRepository: TemplateRepository,
) : ViewModel() {

    companion object {
        //"ai_video_generator"
        const val TAG_VIDEO_GENERATOR = "party"
        //"ai_dance"
        const val TAG_DANCE = "love"

        /** The row is non-scrollable and only ever shows a handful of items. */
        private const val ROW_LIMIT = 4
    }

    private val _uiState = MutableStateFlow(AiTabUiState())
    val uiState: StateFlow<AiTabUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            // Fetch both rows concurrently; repository methods are query-level limited and
            // run on Dispatchers.IO internally.
            val videoGeneratorDeferred = async {
                templateRepository.getTemplatesByVibeTag(TAG_VIDEO_GENERATOR, ROW_LIMIT, 0)
                    .getOrNull().orEmpty()
            }
            val danceDeferred = async {
                templateRepository.getTemplatesByVibeTag(TAG_DANCE, ROW_LIMIT, 0)
                    .getOrNull().orEmpty()
            }

            _uiState.value = AiTabUiState(
                videoGenerator = videoGeneratorDeferred.await(),
                dance = danceDeferred.await(),
                isLoading = false,
            )
        }
    }
}
