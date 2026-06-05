package com.videomaker.aimusic.core.rating

import android.util.Log
import com.videomaker.aimusic.BuildConfig
import com.videomaker.aimusic.core.analytics.Analytics
import com.videomaker.aimusic.core.analytics.AnalyticsEvent
import com.videomaker.aimusic.core.constants.RemoteConfigKeys
import com.videomaker.aimusic.core.data.local.PreferencesManager
import com.videomaker.aimusic.domain.usecase.SubmitFeedbackUseCase
import com.videomaker.aimusic.modules.rate.RatingStep
import co.alcheclub.lib.acccore.remoteconfig.RemoteConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import com.videomaker.aimusic.core.popup.TrendingPopupCoordinator
import com.videomaker.aimusic.core.popup.TrendingPopupTab

/**
 * Pure function for testing trigger conditions in isolation.
 */
fun shouldShowRating(
    videoCreateCount: Int,
    shownCount: Int,
    completed: Boolean,
    firstShow: Int,
    cap: Int
): Boolean {
    if (completed) return false
    if (videoCreateCount < firstShow) return false
    if (shownCount >= cap) return false
    return true
}

/**
 * Manages the rating popup trigger logic.
 */
class RatingTriggerManager(
    private val preferencesManager: PreferencesManager,
    private val remoteConfig: RemoteConfig,
    private val submitFeedbackUseCase: SubmitFeedbackUseCase,
    private val trendingPopupCoordinator: TrendingPopupCoordinator
) {
    companion object {
        private const val TAG = "RatingTriggerManager"
        const val FIRST_SHOW = 1
        const val CAP = 3
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    init {
        trendingPopupCoordinator.isRatingShowing = {
            _ratingStep.value != RatingStep.None && !_suppressed.value
        }
        scope.launch {
            trendingPopupCoordinator.popupUserDismissEvent.collect {
                if (isRatingDeferred) {
                    isRatingDeferred = false
                    if (BuildConfig.DEBUG) {
                        Log.d(TAG, "popupUserDismissEvent collected, triggering deferred rating popup.")
                    }
                    triggerRatingPopupIfEligible(location = "home")
                }
            }
        }
    }

    private val _ratingStep = MutableStateFlow(RatingStep.None)
    val ratingStep: StateFlow<RatingStep> = _ratingStep.asStateFlow()

    /**
     * When true, the global rating overlay must stay hidden even if [ratingStep] is set.
     * Used to enforce "one popup at a time" — e.g. the media permission popup takes
     * priority over the rating popup, so the rating is deferred until permission resolves.
     */
    private val _suppressed = MutableStateFlow(false)
    val isSuppressed: StateFlow<Boolean> = _suppressed.asStateFlow()

    /**
     * Suppress (true) or release (false) the rating overlay. The pending [ratingStep] is
     * preserved while suppressed, so the popup reappears once suppression is released.
     */
    fun setRatingSuppressed(suppressed: Boolean) {
        _suppressed.value = suppressed
    }

    private val _showRatingPopup = Channel<Unit>(Channel.BUFFERED)
    val showRatingPopup = _showRatingPopup.receiveAsFlow()

    private val _launchPlayStoreEvent = Channel<Unit>(Channel.BUFFERED)
    val launchPlayStoreEvent = _launchPlayStoreEvent.receiveAsFlow()

    private var satisfactionResponse = ""
    private var lastStarRating = 0
    private var isRatingDeferred = false

    private fun triggerRatingPopupIfEligible(location: String) {
        if (preferencesManager.ratingCompleted) {
            if (BuildConfig.DEBUG) Log.d(TAG, "Rating already completed, skipping trigger.")
            return
        }

        val dailyCap = remoteConfig.getLong(RemoteConfigKeys.RATING_POPUP_DAILY_CAP, 3L)
        val dailyShownCount = preferencesManager.getRatingDailyShownCount()
        if (dailyShownCount >= dailyCap) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Cannot trigger rating popup: dailyShownCount ($dailyShownCount) >= dailyCap ($dailyCap)")
            }
            return
        }

        // If eligible, show it!
        preferencesManager.incrementRatingDailyShownCount()
        preferencesManager.ratingShownCount++

        Analytics.trackRateView(
            logicRender = "system",
            location = location
        )

        _ratingStep.value = RatingStep.Satisfaction
        _showRatingPopup.trySend(Unit)
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Rating popup triggered successfully. Step set to Satisfaction. Location: $location")
        }
    }

    /**
     * Call when a video export completes successfully (ExportProgress.Success).
     */
    fun onVideoCreated() {
        preferencesManager.ratingVideoCreateCount++
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "onVideoCreated: count=${preferencesManager.ratingVideoCreateCount}")
        }
        triggerRatingPopupIfEligible(location = AnalyticsEvent.Value.Location.RESULT)
    }

    /**
     * Call when template is selected.
     */
    fun onTemplateSelected() {
        if (BuildConfig.DEBUG) Log.d(TAG, "onTemplateSelected")
        if (!preferencesManager.ratingHasTriggeredOnSelect) {
            preferencesManager.ratingHasTriggeredOnSelect = true
            triggerRatingPopupIfEligible(location = "template_select")
        }
    }

    /**
     * Call when song is selected.
     */
    fun onSongSelected() {
        if (BuildConfig.DEBUG) Log.d(TAG, "onSongSelected")
        if (!preferencesManager.ratingHasTriggeredOnSelect) {
            preferencesManager.ratingHasTriggeredOnSelect = true
            triggerRatingPopupIfEligible(location = "song_select")
        }
    }

    /**
     * Call when template is swiped.
     */
    fun onTemplateSwiped() {
        preferencesManager.ratingSwipeTemplateCount++
        val threshold = remoteConfig.getLong(RemoteConfigKeys.RATING_POPUP_TRIGGER_COUNT, 5L)
        val targetCount = (threshold - 1).coerceAtLeast(1)
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "onTemplateSwiped: count=${preferencesManager.ratingSwipeTemplateCount}, targetCount=$targetCount, threshold=$threshold")
        }
        if (preferencesManager.ratingSwipeTemplateCount >= targetCount) {
            preferencesManager.ratingSwipeTemplateCount = 0
            triggerRatingPopupIfEligible(location = "swipe_template")
        }
    }

    /**
     * Call when song is skipped/nexted.
     */
    fun onSongNexted() {
        preferencesManager.ratingNextSongCount++
        val threshold = remoteConfig.getLong(RemoteConfigKeys.RATING_POPUP_TRIGGER_COUNT, 5L)
        val targetCount = (threshold - 1).coerceAtLeast(1)
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "onSongNexted: count=${preferencesManager.ratingNextSongCount}, targetCount=$targetCount, threshold=$threshold")
        }
        if (preferencesManager.ratingNextSongCount >= targetCount) {
            preferencesManager.ratingNextSongCount = 0
            triggerRatingPopupIfEligible(location = "next_song")
        }
    }

    /**
     * Call when user focuses the home screen.
     */
    fun onHomeScreenFocused(currentTab: Int) {
        val sessionId = preferencesManager.getAppSessionId()
        val lastTriggeredSessionId = preferencesManager.ratingLastTriggeredHomeSessionId
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "onHomeScreenFocused: sessionId=$sessionId, lastTriggeredSessionId=$lastTriggeredSessionId, currentTab=$currentTab")
        }
        if (sessionId >= 2 && lastTriggeredSessionId != sessionId) {
            val isPromotePopupEligible = when (currentTab) {
                0 -> trendingPopupCoordinator.isPopupEligible(TrendingPopupTab.GALLERY)
                1 -> trendingPopupCoordinator.isPopupEligible(TrendingPopupTab.SONGS)
                else -> false
            }

            if (isPromotePopupEligible) {
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "Promote popup is eligible on tab $currentTab. Deferring rating popup.")
                }
                preferencesManager.ratingLastTriggeredHomeSessionId = sessionId
                isRatingDeferred = true
                return
            }

            isRatingDeferred = false
            preferencesManager.ratingLastTriggeredHomeSessionId = sessionId
            triggerRatingPopupIfEligible(location = "home")
        } else {
            isRatingDeferred = false
        }
    }

    fun onNotReally() {
        satisfactionResponse = "not_really"
        Analytics.trackRateClick(option = AnalyticsEvent.Value.Option.BAD)
        _ratingStep.value = RatingStep.None
    }

    fun onGood() {
        satisfactionResponse = "good"
        Analytics.trackRateClick(option = AnalyticsEvent.Value.Option.GOOD)
        _ratingStep.value = RatingStep.Stars
    }

    fun onLowRating(stars: Int) {
        lastStarRating = stars
        Analytics.trackRateStar(stars, option = AnalyticsEvent.Value.Option.BAD)
        Analytics.trackRateRateUsButtonClick(option = AnalyticsEvent.Value.Option.GOOD)
        Analytics.trackRateFlowContinue(option = AnalyticsEvent.Value.Option.BAD, star = stars)
        Analytics.trackRateReason(option = AnalyticsEvent.Value.Option.BAD, star = stars)
        _ratingStep.value = RatingStep.Feedback
    }

    fun onHighRating(stars: Int) {
        lastStarRating = stars
        Analytics.trackRateStar(stars, option = AnalyticsEvent.Value.Option.GOOD)
        Analytics.trackRateRateUsButtonClick(option = AnalyticsEvent.Value.Option.GOOD)
        Analytics.trackRateFlowContinue(option = AnalyticsEvent.Value.Option.GOOD, star = stars)
        Analytics.trackRateDone(option = AnalyticsEvent.Value.Option.GOOD, star = stars)
        _ratingStep.value = RatingStep.None
        onRatingCompleted()
        _launchPlayStoreEvent.trySend(Unit)
    }

    fun onFeedbackSubmit(feedback: String) {
        val star = if (lastStarRating > 0) lastStarRating else 0
        Analytics.trackReasonClick(des = feedback, option = AnalyticsEvent.Value.Option.BAD)
        Analytics.trackRateSubmit(
            des = feedback,
            option = AnalyticsEvent.Value.Option.BAD,
            star = star
        )
        if (star > 0) {
            Analytics.trackRateDone(option = AnalyticsEvent.Value.Option.BAD, star = star)
        }
        _ratingStep.value = RatingStep.None
        onRatingCompleted()
        scope.launch(Dispatchers.IO) {
            runCatching {
                submitFeedbackUseCase(
                    feedbackText = feedback,
                    satisfactionResponse = satisfactionResponse,
                    starRating = if (lastStarRating > 0) lastStarRating else null
                )
            }
        }
    }

    fun onRatingDismiss() {
        _ratingStep.value = RatingStep.None
    }

    /**
     * Call when the Satisfaction popup first appears.
     */
    fun onRatingShown() {
        // Handled directly inside triggerRatingPopupIfEligible to be consistent
    }

    /**
     * Call when user submits feedback or rates 4-5 stars.
     */
    fun onRatingCompleted() {
        preferencesManager.ratingCompleted = true
        if (BuildConfig.DEBUG) Log.d(TAG, "onRatingCompleted: popup permanently dismissed")
    }
}
