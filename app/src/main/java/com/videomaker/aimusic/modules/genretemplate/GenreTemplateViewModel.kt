package com.videomaker.aimusic.modules.genretemplate

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.alcheclub.lib.acccore.remoteconfig.RemoteConfig
import com.videomaker.aimusic.core.constants.RemoteConfigKeys
import com.videomaker.aimusic.domain.model.VideoTemplate
import com.videomaker.aimusic.domain.repository.SongRepository
import com.videomaker.aimusic.domain.repository.TemplateRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
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

// Firebase RC getBoolean(key) returns false silently for missing keys (defaultValue is only used
// in the exception path). This helper returns true when the key hasn't been published yet.
fun RemoteConfig.getStepEnabled(key: String): Boolean =
    if (key in getAllKeys()) getBoolean(key, true) else true

fun RemoteConfig.isGenreTemplateFlowAllOff(): Boolean =
    !getStepEnabled(RemoteConfigKeys.ONBOARDING_GENRE_SELECTION_ENABLED) &&
    !getStepEnabled(RemoteConfigKeys.ONBOARDING_TEMPLATE_PICK_ENABLED) &&
    !getStepEnabled(RemoteConfigKeys.ONBOARDING_CONTENT_EXCLUSIVE_ENABLED) &&
    !getStepEnabled(RemoteConfigKeys.ONBOARDING_MEDIA_PRIVACY_ENABLED)

class GenreTemplateViewModel(
    private val templateRepository: TemplateRepository,
    private val songRepository: SongRepository,
    remoteConfig: RemoteConfig
) : ViewModel() {

    // Feature flags - default true so existing behaviour is preserved when keys not set in RC
    val isGenreSelectionEnabled = remoteConfig.getStepEnabled(RemoteConfigKeys.ONBOARDING_GENRE_SELECTION_ENABLED)
    val isTemplatePickEnabled = remoteConfig.getStepEnabled(RemoteConfigKeys.ONBOARDING_TEMPLATE_PICK_ENABLED)
    val isContentExclusiveEnabled = remoteConfig.getStepEnabled(RemoteConfigKeys.ONBOARDING_CONTENT_EXCLUSIVE_ENABLED)
    val isMediaPrivacyEnabled = remoteConfig.getStepEnabled(RemoteConfigKeys.ONBOARDING_MEDIA_PRIVACY_ENABLED)

    private val enabledSteps: List<GenreTemplateStep> = GenreTemplateGate.enabledSteps(remoteConfig)

    // Fired when the flow should proceed to FeatureSelectionActivity (all remaining steps done/skipped)
    private val _navToNext = Channel<Unit>(Channel.BUFFERED)
    val navToNext = _navToNext.receiveAsFlow()

    // Single-select choices for the two new analytics-only screens (item 1 pre-selected).
    val selectedContentFilter = mutableStateOf(CONTENT_EXCLUSIVE_ITEMS.first().id)
    val selectedPrivacy = mutableStateOf(MEDIA_PRIVACY_ITEMS.first().id)

    // Initial visible step: first enabled screen in the fixed order
    private val _currentStep = MutableStateFlow(
        enabledSteps.firstOrNull() ?: GenreTemplateStep.TEMPLATE_PICK
    )
    val currentStep: StateFlow<GenreTemplateStep> = _currentStep.asStateFlow()

    val genres = mutableStateListOf<OnboardingGenre>()
    val selectedGenre = mutableStateOf<OnboardingGenre?>(null)
    val isGenresLoading = mutableStateOf(false)

    // Must be declared before init{} — loadTemplates() is called from init when GENRE_SELECTION is off
    val suggestedTemplates = mutableStateListOf<VideoTemplate>()
    val selectedTemplate = mutableStateOf<VideoTemplate?>(null)
    val isTemplatesLoading = mutableStateOf(false)
    val templatesError = mutableStateOf<String?>(null)

    init {
        loadGenres()
        when {
            enabledSteps.isEmpty() -> {
                // All steps off → skip the whole activity immediately
                viewModelScope.launch { _navToNext.send(Unit) }
            }
            !isGenreSelectionEnabled && isTemplatePickEnabled -> {
                // No genre screen but template pick is first → preload templates (no genre).
                loadTemplates()
            }
        }
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

    fun selectGenre(genre: OnboardingGenre) {
        selectedGenre.value = if (selectedGenre.value?.id == genre.id) null else genre
    }

    fun isStep1Valid(): Boolean = selectedGenre.value != null

    fun onGenreNext() {
        if (isTemplatesLoading.value) return
        if (isTemplatePickEnabled) {
            // Stay on GENRE_SELECTION while templates load (CTA disabled via isTemplatesLoading),
            // then transition to TEMPLATE_PICK inside loadTemplates().
            loadTemplates()
        } else {
            advanceFrom(GenreTemplateStep.GENRE_SELECTION)
        }
    }

    private fun loadTemplates() {
        isTemplatesLoading.value = true
        templatesError.value = null
        val genre = selectedGenre.value // null when GENRE_SELECTION is off

        viewModelScope.launch {
            val templates = if (genre != null) {
                // Genre was selected: prefer genre-tagged templates, fall back to featured
                val result = templateRepository.getTemplatesByVibeTag(genre.id, limit = 4)
                result.getOrNull().takeIf { !it.isNullOrEmpty() }
                    ?: templateRepository.getFeaturedTemplates(limit = 4).getOrNull().orEmpty()
            } else {
                // No genre (GENRE_SELECTION was off): use geo hot/featured templates
                templateRepository.getFeaturedTemplates(limit = 4).getOrNull().orEmpty()
            }

            suggestedTemplates.clear()
            suggestedTemplates.addAll(templates)
            isTemplatesLoading.value = false

            when {
                templates.isNotEmpty() -> {
                    _currentStep.value = GenreTemplateStep.TEMPLATE_PICK
                }
                else -> {
                    // Templates unavailable. If we can't return to pick a genre, skip the template
                    // step and continue to the next enabled step (or finish).
                    if (!isGenreSelectionEnabled) {
                        advanceFrom(GenreTemplateStep.TEMPLATE_PICK)
                    } else {
                        templatesError.value = "No templates available"
                    }
                }
            }
        }
    }

    fun selectTemplate(template: VideoTemplate) {
        selectedTemplate.value = template
    }

    fun goBackToStep1() {
        if (!isGenreSelectionEnabled) return
        selectedGenre.value = null
        _currentStep.value = GenreTemplateStep.GENRE_SELECTION
    }

    fun onTemplateNext() = advanceFrom(GenreTemplateStep.TEMPLATE_PICK)

    fun selectContentFilter(id: String) { selectedContentFilter.value = id }
    fun onContentExclusiveNext() = advanceFrom(GenreTemplateStep.CONTENT_EXCLUSIVE)

    fun selectPrivacy(id: String) { selectedPrivacy.value = id }
    fun onMediaPrivacyNext() = advanceFrom(GenreTemplateStep.MEDIA_PRIVACY)

    private fun advanceFrom(step: GenreTemplateStep) {
        val next = GenreTemplateGate.nextStep(enabledSteps, step)
        if (next == null) {
            viewModelScope.launch { _navToNext.send(Unit) }
        } else if (next == GenreTemplateStep.TEMPLATE_PICK) {
            // Reaching TEMPLATE_PICK requires templates; load then transition.
            loadTemplates()
        } else {
            _currentStep.value = next
        }
    }
}
