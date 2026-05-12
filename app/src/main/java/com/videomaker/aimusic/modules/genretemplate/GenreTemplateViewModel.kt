package com.videomaker.aimusic.modules.genretemplate

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.videomaker.aimusic.domain.model.VideoTemplate
import com.videomaker.aimusic.domain.repository.SongRepository
import com.videomaker.aimusic.domain.repository.TemplateRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class OnboardingGenre(
    val id: String,
    val displayName: String,
    val imageRes: Int
)

private val GENRE_IMAGES = mapOf(
    "pop" to com.videomaker.aimusic.R.drawable.img_pop_song,
    "rock" to com.videomaker.aimusic.R.drawable.img_rock_song,
    "hip-hop" to com.videomaker.aimusic.R.drawable.img_hip_hop_song,
    "r-and-b" to com.videomaker.aimusic.R.drawable.img_r_b_song,
    "edm" to com.videomaker.aimusic.R.drawable.img_edm_song,
    "jazz" to com.videomaker.aimusic.R.drawable.img_jazz_song,
    "country" to com.videomaker.aimusic.R.drawable.img_country_song,
    "classical" to com.videomaker.aimusic.R.drawable.img_classical_song,
    "k-pop" to com.videomaker.aimusic.R.drawable.img_k_pop_song,
    "alternative" to com.videomaker.aimusic.R.drawable.img_indie_alternative_song,
    "acoustic" to com.videomaker.aimusic.R.drawable.img_acoustic_song,
    "ballad" to com.videomaker.aimusic.R.drawable.img_ballad_song,
    "children" to com.videomaker.aimusic.R.drawable.img_children_song,
    "dance" to com.videomaker.aimusic.R.drawable.img_dance_song,
    "folk-pop" to com.videomaker.aimusic.R.drawable.img_folk_pop_song,
    "holiday" to com.videomaker.aimusic.R.drawable.img_holliday_song,
    "j-pop" to com.videomaker.aimusic.R.drawable.img_j_pop_song,
    "latin" to com.videomaker.aimusic.R.drawable.img_latin_song,
    "meme" to com.videomaker.aimusic.R.drawable.img_meme_song,
    "other" to com.videomaker.aimusic.R.drawable.img_other_song,
    "soundtrack" to com.videomaker.aimusic.R.drawable.img_soundtrack_song,
    "tiktok" to com.videomaker.aimusic.R.drawable.img_tiktok_song,
)

private val FALLBACK_IMAGES = GENRE_IMAGES.values.toList()

private var randomImageIndex = 0

fun genreImageRes(id: String): Int =
    GENRE_IMAGES[id] ?: FALLBACK_IMAGES[randomImageIndex++ % FALLBACK_IMAGES.size]

val ONBOARDING_GENRES = listOf(
    OnboardingGenre("pop", "Pop", genreImageRes("pop")),
    OnboardingGenre("edm", "EDM", genreImageRes("edm")),
    OnboardingGenre("rock", "Rock", genreImageRes("rock")),
    OnboardingGenre("latin", "Latin", genreImageRes("latin")),
    OnboardingGenre("jazz", "Jazz", genreImageRes("jazz")),
    OnboardingGenre("country", "Country", genreImageRes("country")),
    OnboardingGenre("classical", "Classical", genreImageRes("classical")),
    OnboardingGenre("dance", "Dance", genreImageRes("dance")),
    OnboardingGenre("tiktok", "TikTok", genreImageRes("tiktok")),
    OnboardingGenre("ballad", "Ballad", genreImageRes("ballad")),
    OnboardingGenre("children", "Children", genreImageRes("children")),
    OnboardingGenre("k-pop", "K-Pop", genreImageRes("k-pop")),
    OnboardingGenre("j-pop", "J-Pop", genreImageRes("j-pop")),
    OnboardingGenre("hip-hop", "Hip-Hop", genreImageRes("hip-hop")),
    OnboardingGenre("r-and-b", "R&B", genreImageRes("r-and-b")),
    OnboardingGenre("holiday", "Holiday", genreImageRes("holiday")),
    OnboardingGenre("other", "Other", genreImageRes("other")),
    OnboardingGenre("folk-pop", "Folk-Pop", genreImageRes("folk-pop")),
    OnboardingGenre("acoustic", "Acoustic", genreImageRes("acoustic")),
    OnboardingGenre("alternative", "Alternative", genreImageRes("alternative")),
    OnboardingGenre("soundtrack", "Soundtrack", genreImageRes("soundtrack")),
    OnboardingGenre("meme", "Meme", genreImageRes("meme")),
)

class GenreTemplateViewModel(
    private val templateRepository: TemplateRepository,
    private val songRepository: SongRepository
) : ViewModel() {

    private val _currentStep = MutableStateFlow(GenreTemplateStep.GENRE_SELECTION)
    val currentStep: StateFlow<GenreTemplateStep> = _currentStep.asStateFlow()

    // Step 1: Genre list from server, fallback to hardcoded
    val genres = mutableStateListOf<OnboardingGenre>()
    val selectedGenre = mutableStateOf<OnboardingGenre?>(null)
    val isGenresLoading = mutableStateOf(false)

    init {
        loadGenres()
    }

    private fun loadGenres() {
        isGenresLoading.value = true
        viewModelScope.launch {
            songRepository.getGenres()
                .onSuccess { serverGenres ->
                    genres.clear()
                    genres.addAll(serverGenres.map { OnboardingGenre(it.id, it.displayName, genreImageRes(it.id)) })
                }.onFailure {
                    genres.clear()
                    genres.addAll(ONBOARDING_GENRES)
                }
            isGenresLoading.value = false
        }
    }
    val suggestedTemplates = mutableStateListOf<VideoTemplate>()
    val selectedTemplate = mutableStateOf<VideoTemplate?>(null)
    val isTemplatesLoading = mutableStateOf(false)
    val templatesError = mutableStateOf<String?>(null)

    fun selectGenre(genre: OnboardingGenre) {
        selectedGenre.value = if (selectedGenre.value?.id == genre.id) null else genre
    }

    fun isStep1Valid(): Boolean = selectedGenre.value != null

    fun goToStep2() {
        _currentStep.value = GenreTemplateStep.PERSONALIZING
        loadTemplates()
    }

    private fun loadTemplates() {
        isTemplatesLoading.value = true
        templatesError.value = null
        val genre = selectedGenre.value ?: return

        viewModelScope.launch {
            // Try to get templates by genre (used as vibe tag)
            val result = templateRepository.getTemplatesByVibeTag(genre.id, limit = 4)

            val templates = result.getOrNull().takeIf { !it.isNullOrEmpty() }
                ?: templateRepository.getFeaturedTemplates(limit = 4).getOrNull().orEmpty()

            suggestedTemplates.clear()
            suggestedTemplates.addAll(templates)

            if (templates.isNotEmpty()) {
                _currentStep.value = GenreTemplateStep.TEMPLATE_PICK
            } else {
                templatesError.value = "No templates available"
            }
            isTemplatesLoading.value = false
        }
    }

    fun selectTemplate(template: VideoTemplate) {
        selectedTemplate.value = template
    }

    fun goBackToStep1() {
        selectedGenre.value = null
        _currentStep.value = GenreTemplateStep.GENRE_SELECTION
    }
}
