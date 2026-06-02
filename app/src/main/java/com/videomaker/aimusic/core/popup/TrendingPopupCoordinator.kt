package com.videomaker.aimusic.core.popup

import com.videomaker.aimusic.core.analytics.Analytics
import com.videomaker.aimusic.core.analytics.AnalyticsEvent
import com.videomaker.aimusic.domain.model.MusicSong
import com.videomaker.aimusic.domain.model.VideoTemplate
import com.videomaker.aimusic.domain.repository.SongRepository
import com.videomaker.aimusic.domain.repository.TemplateRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.random.Random

/** Indirection so tests can supply an in-memory store. */
interface PopupSnapshotStore {
    fun get(tab: TrendingPopupTab): TrendingPopupDailySnapshot?
    fun set(tab: TrendingPopupTab, snapshot: TrendingPopupDailySnapshot)
}

/** Indirection so tests can supply a fake config without Firebase. */
interface PopupConfigSource {
    fun read(): TrendingPopupConfigValues
}

/** Indirection so tests can advance a fake clock. */
interface PopupClock {
    fun nowMs(): Long
    fun todayEpochDay(): Long
}

class SystemPopupClock : PopupClock {
    override fun nowMs(): Long = System.currentTimeMillis()
    override fun todayEpochDay(): Long = LocalDate.now().toEpochDay()
}

class TrendingPopupCoordinator(
    private val templateRepository: TemplateRepository,
    private val songRepository: SongRepository,
    private val snapshotStore: PopupSnapshotStore,
    private val config: PopupConfigSource,
    private val clock: PopupClock,
    private val gate: TrendingPopupGate,
    selectorRandom: Random = Random.Default,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
) {
    private val selector = TrendingPopupSelector(
        templateRepository = templateRepository,
        songRepository = songRepository,
        random = selectorRandom
    )

    private val _templatePopup =
        MutableStateFlow<TrendingPopupState<VideoTemplate>>(TrendingPopupState.Hidden)
    val templatePopup: StateFlow<TrendingPopupState<VideoTemplate>> = _templatePopup.asStateFlow()

    private val _songPopup =
        MutableStateFlow<TrendingPopupState<MusicSong>>(TrendingPopupState.Hidden)
    val songPopup: StateFlow<TrendingPopupState<MusicSong>> = _songPopup.asStateFlow()

    val isAnyPopupShowing: StateFlow<Boolean> = combine(_templatePopup, _songPopup) { t, s ->
        t is TrendingPopupState.Showing || s is TrendingPopupState.Showing
    }.stateIn(scope, SharingStarted.Eagerly, false)

    private val _navigationEvent = Channel<TrendingPopupNavEvent>(Channel.BUFFERED)
    val navigationEvent = _navigationEvent.receiveAsFlow()

    /**
     * Emitted ONLY when user explicitly dismisses a popup (taps X).
     * NOT emitted on CTA path (since user is navigating away).
     * Subscribers (e.g. MusicPlayerBottomSheet) use this to auto-resume audio.
     */
    private val _popupUserDismissEvent = Channel<Unit>(Channel.BUFFERED)
    val popupUserDismissEvent = _popupUserDismissEvent.receiveAsFlow()

    fun isPopupEligible(tab: TrendingPopupTab): Boolean {
        val snapshot = snapshotStore.get(tab)
        val cfg = config.read()
        val now = clock.nowMs()
        val today = clock.todayEpochDay()
        val otherShowing = when (tab) {
            TrendingPopupTab.GALLERY -> _songPopup.value is TrendingPopupState.Showing
            TrendingPopupTab.SONGS -> _templatePopup.value is TrendingPopupState.Showing
        }

        return gate.evaluate(
            snapshot = snapshot,
            config = cfg,
            nowMs = now,
            todayEpochDay = today,
            otherPopupShowing = otherShowing
        ) == TrendingPopupGate.Decision.Eligible
    }

    fun onTabFocused(tab: TrendingPopupTab) {
        scope.launch { evaluateAndMaybeShow(tab) }
    }

    private suspend fun evaluateAndMaybeShow(tab: TrendingPopupTab) {
        val alreadyShowing = when (tab) {
            TrendingPopupTab.GALLERY -> _templatePopup.value is TrendingPopupState.Showing
            TrendingPopupTab.SONGS -> _songPopup.value is TrendingPopupState.Showing
        }
        if (alreadyShowing) return

        val snapshot = snapshotStore.get(tab)
        val cfg = config.read()
        val now = clock.nowMs()
        val today = clock.todayEpochDay()
        val otherShowing = when (tab) {
            TrendingPopupTab.GALLERY -> _songPopup.value is TrendingPopupState.Showing
            TrendingPopupTab.SONGS -> _templatePopup.value is TrendingPopupState.Showing
        }

        val decision = gate.evaluate(
            snapshot = snapshot,
            config = cfg,
            nowMs = now,
            todayEpochDay = today,
            otherPopupShowing = otherShowing
        )
        if (decision != TrendingPopupGate.Decision.Eligible) return

        val effective = snapshot
            ?.takeIf { it.epochDay == today }
            ?: TrendingPopupDailySnapshot.empty(today)
        val excludeIds = effective.shownIds.toSet()

        when (tab) {
            TrendingPopupTab.GALLERY -> {
                // Repository methods already wrap in withContext(Dispatchers.IO) internally.
                val pick = selector.pickTemplate(excludeIds) ?: return
                persistShow(tab, effective, pick.id, now, today)
                _templatePopup.value = TrendingPopupState.Showing(pick)
                trackShow(contentType = "template", contentId = pick.id,
                    location = AnalyticsEvent.Value.Location.POPUP_TRENDING_TEMPLATE)
            }
            TrendingPopupTab.SONGS -> {
                val pick = selector.pickSong(excludeIds) ?: return
                persistShow(tab, effective, pick.id.toString(), now, today)
                _songPopup.value = TrendingPopupState.Showing(pick)
                trackShow(contentType = "song", contentId = pick.id.toString(),
                    location = AnalyticsEvent.Value.Location.POPUP_TRENDING_SONG)
            }
        }
    }

    private fun trackShow(contentType: String, contentId: String, location: String) {
        runCatching {
            Analytics.trackTrendingPopupShow(contentType, contentId, location)
        }
    }

    private fun trackCta(contentType: String, contentId: String, location: String) {
        runCatching {
            Analytics.trackTrendingPopupCta(contentType, contentId, location)
        }
    }

    private fun trackDismiss(contentType: String, contentId: String, location: String) {
        runCatching {
            Analytics.trackTrendingPopupDismiss(contentType, contentId, location)
        }
    }

    private fun persistShow(
        tab: TrendingPopupTab,
        effective: TrendingPopupDailySnapshot,
        newId: String,
        nowMs: Long,
        todayEpochDay: Long
    ) {
        val updated = effective.copy(
            epochDay = todayEpochDay,
            shownCount = effective.shownCount + 1,
            shownIds = effective.shownIds + newId,
            lastShownAtMs = nowMs
        )
        snapshotStore.set(tab, updated)
    }

    fun onTemplatePopupCta(template: VideoTemplate) {
        trackCta(contentType = "template", contentId = template.id,
            location = AnalyticsEvent.Value.Location.POPUP_TRENDING_TEMPLATE)
        _templatePopup.value = TrendingPopupState.Hidden
        _navigationEvent.trySend(
            TrendingPopupNavEvent.OpenTemplatePreviewer(
                templateId = template.id,
                overrideSongId = -1L,
                sourceLocation = AnalyticsEvent.Value.Location.POPUP_TRENDING_TEMPLATE
            )
        )
    }

    fun onSongPopupCta(song: MusicSong) {
        trackCta(contentType = "song", contentId = song.id.toString(),
            location = AnalyticsEvent.Value.Location.POPUP_TRENDING_SONG)
        _songPopup.value = TrendingPopupState.Hidden
        _navigationEvent.trySend(
            TrendingPopupNavEvent.OpenTemplatePreviewer(
                templateId = "",
                overrideSongId = song.id,
                sourceLocation = AnalyticsEvent.Value.Location.POPUP_TRENDING_SONG
            )
        )
    }

    fun onTemplatePopupDismissed() {
        val showing = _templatePopup.value as? TrendingPopupState.Showing ?: return
        trackDismiss(contentType = "template", contentId = showing.content.id,
            location = AnalyticsEvent.Value.Location.POPUP_TRENDING_TEMPLATE)
        _templatePopup.value = TrendingPopupState.Hidden
        _popupUserDismissEvent.trySend(Unit)
    }

    fun onSongPopupDismissed() {
        val showing = _songPopup.value as? TrendingPopupState.Showing ?: return
        trackDismiss(contentType = "song", contentId = showing.content.id.toString(),
            location = AnalyticsEvent.Value.Location.POPUP_TRENDING_SONG)
        _songPopup.value = TrendingPopupState.Hidden
        _popupUserDismissEvent.trySend(Unit)
    }
}
