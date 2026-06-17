package com.videomaker.aimusic.core.analytics

/**
 * Analytics Event Constants - Video Maker Photo Music
 *
 * Canonical event names and parameter keys.
 * Includes normalized taxonomy for requirements 2..20.
 */
object AnalyticsEvent {

    // ============================================
    // APP LIFECYCLE
    // ============================================
    const val APP_INIT_TIME = "app_init_time"

    // ============================================
    // LANGUAGE SELECTION
    // ============================================
    const val LANGUAGE_SHOW = "language_show"
    const val LANGUAGE_SELECT = "language_select"
    const val LANGUAGE_NEXT = "language_next"

    // ============================================
    // 2. HOME / TAB
    // ============================================
    const val TAB_VIEW = "tab_view"
    const val TAB_SWITCH = "tab_switch"

    // Tab render: system displays a tab. Must fire BEFORE any rewarded popup
    // event on that tab, same timing as TAB_VIEW.
    const val TAB_GALLERY_RENDER = "tab_gallery_render"
    const val TAB_SONG_RENDER = "tab_song_render"
    const val TAB_LIBRARY_RENDER = "tab_library_render"

    const val IDEA_SONG_IMPRESSION = "idea_song_impression"
    const val IDEA_TEMPLATE_IMPRESSION = "idea_template_impression"
    const val IDEA_SONG_CLICK = "idea_song_click"
    const val IDEA_TEMPLATE_CLICK = "idea_template_click"
    const val REFRESH_START_SONG = "refresh_start_song"
    const val REFRESH_START_TEMPLATE = "refresh_start_template"
    const val REFRESH_ICON_CLICK = "refresh_icon_click"

    // ============================================
    // 3. GALLERY SCREEN
    // ============================================
    const val GALLERY_SWIPE = "gallery_swipe"
    const val TEMPLATE_GENRE_CLICK = "template_genre_click"

    // ============================================
    // HOME BANNER (remote-config banner list)
    // ============================================
    const val BANNER_CLICK_TEMPLATE = "banner_click_template"
    const val BANNER_CLICK_SONG = "banner_click_song"

    // ============================================
    // 4. TEMPLATE CONTEXT
    // ============================================
    const val TEMPLATE_IMPRESSION = "template_impress"
    const val TEMPLATE_CLICK = "template_click"
    const val TEMPLATE_PREVIEW = "template_preview"
    const val TEMPLATE_OPTION = "template_option"
    const val TEMPLATE_FAVORITE = "template_favorite"
    const val TEMPLATE_UNFAVORITE = "template_unfavorite"
    const val TEMPLATE_SHARE = "template_share"
    const val TEMPLATE_REPORT = "template_report"
    const val TEMPLATE_SELECT = "template_select"

    // ============================================
    // 5. SONG SCREEN
    // ============================================
    const val SONG_TAB_SWIPE = "song_tab_swipe"
    const val SONG_GENRE_CLICK = "song_genre_click"

    // ============================================
    // 6. SONG CONTEXT
    // ============================================
    const val SONG_IMPRESSION = "song_impression"
    const val SONG_OPTION = "song_option"
    const val SONG_CLICK = "song_click"
    const val SONG_PLAY = "song_play"
    const val SONG_PAUSE = "song_pause"
    const val SONG_NEXT = "song_next"
    const val SONG_BACK = "song_back"
    const val SONG_PREVIEW = "song_preview"
    const val SONG_FAVORITE = "song_favorite"
    const val SONG_UNFAVORITE = "song_unfavorite"
    const val SONG_SHARE = "song_share"
    const val SONG_SELECT = "song_select"
    const val SONG_STARTTIME_CHANGE = "song_starttime_change"

    // ============================================
    // 7. LIBRARY SCREEN
    // ============================================
    const val LIBRARY_CLICK = "library_click"

    // ============================================
    // 8. CREATION FLOW
    // ============================================
    const val CREATION_START = "creation_start"
    const val RATIO_CLICK = "ratio_click"
    const val RATIO_SELECT = "ratio_select"
    const val MEDIA_RENDER = "media_render"
    const val MEDIA_SELECT = "media_select"
    const val MEDIA_UNSELECT = "media_unselect"
    const val MEDIA_COMPLETE = "media_complete"
    const val MEDIA_NPHOTO_STATE = "media_nphoto_state"
    const val MEDIA_MOREPHOTO_STATE = "media_morephoto_state"
    const val MEDIA_LIMITPHOTO_STATE = "media_limitphoto_state"
    const val VIDEO_GENERATE = "video_generate"
    const val VIDEO_GENERATE_COMPLETE = "video_generate_complete"

    // ============================================
    // 9. VIDEO EDITOR SCREEN
    // ============================================
    const val VIDEO_EDITOR_RENDER = "video_editor_render"
    const val VIDEO_PREVIEW = "video_preview"
    const val VIDEO_PREVIEW_FAILED = "video_preview_failed"
    const val VIDEO_PREVIEW_COMPLETE = "video_preview_complete"
    const val VIDEO_PLAY = "video_play"
    const val VIDEO_PAUSE = "video_pause"

    const val EFFECT_EDIT = "effect_edit"
    const val EFFECT_CLICK = "effect_click"
    const val EFFECT_SELECT = "effect_select"
    const val EFFECT_CLOSE = "effect_close"

    const val DURATION_EDIT = "duration_edit"
    const val DURATION_CLICK = "duration_click"
    const val DURATION_SELECT = "duration_select"
    const val DURATION_CLOSE = "duration_close"

    const val RATIO_EDIT = "ratio_edit"
    const val RATIO_CLOSE = "ratio_close"

    const val VOLUME_EDIT = "volumn_edit"
    const val VOLUME_CLICK = "volumn_click"
    const val VOLUME_SELECT = "volumn_select"
    const val VOLUME_CLOSE = "volumn_close"

    const val SONG_EDIT = "song_edit"
    const val SONG_CLOSE = "song_close"

    const val QUALITY_EDIT = "quality_edit"
    const val QUALITY_CLICK = "quality_click"
    const val QUALITY_SELECT = "quality_select"
    const val QUALITY_CLOSE = "quality_close"

    const val PHOTO_EDIT = "photo_edit"
    const val PHOTO_CLICK = "photo_click"
    const val PHOTO_DELETE = "photo_delete"
    const val PHOTO_DRAG = "photo_drag"
    const val PHOTO_ADD = "photo_add"
    const val PHOTO_SELECT = "photo_select"
    const val PHOTO_CLOSE = "photo_close"


    // ============================================
    // 10. EXPORT / RESULT / VIDEO ACTIONS
    // ============================================
    const val VIDEO_EXPORT = "video_export"
    const val VIDEO_EXPORT_COMPLETE = "video_export_complete"
    const val VIDEO_EXPORT_ERROR = "video_export_error"
    const val VIDEO_SHARE = "video_share"
    const val VIDEO_DOWNLOAD = "video_download"

    // ============================================
    // 11. VIDEO LIBRARY
    // ============================================
    const val VIDEO_CLICK = "video_click"
    const val VIDEO_OPTION = "video_option"
    const val VIDEO_DELETE = "video_delete"

    // ============================================
    // 12. EXIT FLOW
    // ============================================
    const val EXIT_CLICK = "exit_click"
    const val EXIT_POPUP_SHOW = "exit_popup_show"
    const val EXIT_DISCARD = "exit_discard"
    const val EXIT_CONTINUE = "exit_continue"
    const val EXIT_SAVE = "exit_save"

    // ============================================
    // 13. WIDGET
    // ============================================
    const val WIDGET_VIEW = "widget_view"
    const val WIDGET_CLICK = "widget_click"
    const val WIDGET_SELECT = "widget_select"
    const val WIDGET_ADD = "widget_add"
    const val WIDGET_IMPRESSION = "widget_impression"
    const val WIDGET_OPEN = "widget_open"

    // ============================================
    // 14. SETTING SCREEN
    // ============================================
    const val SETTING_OPEN = "setting_open"
    const val SETTING_VIEW = "setting_view"
    const val SETTING_OPTION_CLICK = "setting_option_click"

    // ============================================
    // 15. RATE US FLOW
    // ============================================
    const val RATE_VIEW = "rate_view"
    const val RATE_CLICK = "rate_click"
    const val RATE_STAR = "rate_star"
    const val RATE_RATE_US_BUTTON_CLICK = "rate_rate_us_button_click"
    const val RATE_FLOW_CONTINUE = "rate_flow_continue"
    const val RATE_REASON = "rate_reason"
    const val REASON_CLICK = "reason_click"
    const val RATE_DONE = "rate_done"
    const val RATE_SUBMIT = "rate_submit"

    // ============================================
    // 16. PERMISSION / ACCESS REQUEST
    // ============================================
    const val PERMISSION_RENDER = "permission_render"
    const val PERMISSION_CLICK = "permission_click"
    const val PERMISSION_GOTO_SETTING = "permission_goto_setting"
    const val PERMISSION_CHECK = "permission_check"
    const val PERMISSION_ADD_IMAGE = "permission_add_image"
    const val PERMISSION_NO_ALLOW = "permission_no_allow"
    const val PERMISSION_NOALLOW_CLICKBTN = "permission_noallow_clickbtn"
    const val PERMISSION_WARNING_LIMITED = "permission_warning_limited"
    const val PERMISSION_WARNING_ALLOWBTN = "permission_warning_allowbtn"
    const val PERMISSION_WARNING_DENYBTN = "permission_warning_denybtn"

    // ============================================
    // 17. REPORT FLOW
    // ============================================
    const val REPORT_RENDER = "report_render"
    const val REPORT_SELECT_REASON = "report_select_reason"
    const val REPORT_SUBMIT = "report_submit"
    const val REPORT_DONE = "report_done"

    // ============================================
    // 18. SEARCH SCREEN
    // ============================================
    const val SEARCH_OPEN = "search_open"
    const val SEARCH_RECENT_VIEW = "search_recent_view"
    const val SEARCH_CLICK = "search_click"
    const val SEARCH_TYPE = "search_type"
    const val SEARCH_SUGGEST_VIEW = "search_suggest_view"
    const val SEARCH_SUGGEST_CLICK = "search_suggest_click"
    const val SEARCH_SUBMIT = "search_submit"
    const val SEARCH_RESULT_VIEW = "search_result_view"
    const val SEARCH_NO_RESULT = "search_no_result"
    const val SEARCH_SEE_MORE = "search_see_more"
    const val SEARCH_RECENT_DELETE = "search_recent_delete"
    const val SEARCH_CANCEL = "search_cancel"

    // ============================================
    // 19. SHORTCUT
    // ============================================
    const val SHORTCUT_MENU_IMPRESSION = "shortcut_menu_impress"
    const val SHORTCUT_CLICK = "shortcut_click"

    // ============================================
    // 20. UNINSTALL RETAIN SCREEN
    // ============================================
    const val UNINSTALL_VIEW = "uninstall_view"
    const val UNINSTALL_CONTENT_CLICK = "uninstall_content_click"
    const val UNINSTALL_SEE_MORE = "uninstall_see_more"
    const val UNINSTALL_CTA_CLICK = "uninstall_cta_click"

    // ============================================
    // 21. NOTIFICATION
    // ============================================
    const val NOTIFICATION_ELIGIBLE = "notification_eligible"
    const val NOTIFICATION_SCHEDULED = "notification_scheduled"
    const val NOTIFICATION_SHOWN = "notification_shown"
    const val NOTIFICATION_CLICK = "notification_click"
    const val NOTIFICATION_DISMISS = "notification_dismiss"
    const val NOTIFICATION_CANCELED = "notification_canceled"
    const val NOTIFICATION_CONVERSION = "notification_conversion"

    // ============================================
    // 22. REWARD POPUP
    // ============================================
    const val REWARD_POPUP_RENDER = "reward_popup_render"
    const val REWARD_POPUP_BTN = "reward_popup_btn"

    // ============================================
    // 23. TRENDING POPUP
    // ============================================
    const val TRENDING_POPUP_SHOW = "trending_popup_show"
    const val TRENDING_POPUP_CTA = "trending_popup_cta"
    const val TRENDING_POPUP_DISMISS = "trending_popup_dismiss"

    const val REWARD_SONG_RENDER = "reward_song_render"
    const val REWARD_SONG_CLICK = "reward_song_click"
    const val REWARD_SONG_EXIT = "reward_song_exit"
    const val REWARD_TEMPLATE_RENDER = "reward_template_render"
    const val REWARD_TEMPLATE_CLICK = "reward_template_click"
    const val REWARD_TEMPLATE_EXIT = "reward_template_exit"

    // ============================================
    // PARAMETER KEYS
    // ============================================
    object Param {
        const val VALUE = "value"
        const val TYPE = "type"
        const val SOURCE = "source"
        const val LOGIC = "logic"
        const val FLOW = "flow"

        const val TAB_NAME = "tab_name"
        const val FROM = "from"
        const val TO = "to"
        const val LOCATION = "location"
        const val SECTION = "section"
        const val POSITION = "position"

        const val GENRE_ID = "genre_id"
        const val GENRE_NAME = "genre_name"

        const val TEMPLATE_ID = "template_id"
        const val TEMPLATE_NAME = "template_name"
        const val TEMPLATE_COUNT = "template_count"

        const val SONG_ID = "song_id"
        const val SONG_NAME = "song_name"
        const val MUSIC_COUNT = "music_count"

        const val VIDEO_ID = "video_id"

        const val QUALITY = "quality"
        const val DURATION = "duration"
        const val RATIO_SIZE = "ratio"
        const val VOLUME = "volume"
        const val VIDEO_QUALITY = "video_quality"
        const val MEDIA_QUALITY = "media_quality"
        const val MEDIA_QUANTITY = "media_quantity"

        const val DURATION_NUMBER = "duration_number"
        const val VOLUME_NUMBER = "volumn_number"
        const val QUALITY_NUMBER = "quality_number"

        const val EFFECT_ID = "effect_id"
        const val EFFECT_NAME = "effect_name"

        const val WIDGET_TYPE = "widget_type"
        const val WIDGET_SIZE = "widget_size"
        const val ENTRY_POINT = "entry_point"
        const val DEEP_LINK_TARGET = "deep_link_target"

        const val OPTION = "option"
        const val STAR = "star"
        const val LOGIC_RENDER = "logic_render"
        const val REASON = "reason"
        const val DES = "des"
        const val OTHER_TEXT = "other_text"

        const val BUTTON = "button"
        const val ALLOW = "allow"
        const val PER_TYPE = "per_type"
        const val POP_TYPE = "pop_type"
        const val PREVIOUS_ACTION = "previous_action"

        const val KEYWORD = "keyword"
        const val KEYWORD_1 = "keyword_1"
        const val KEYWORD_2 = "keyword_2"
        const val KEYWORD_3 = "keyword_3"

        const val ID = "id"
        const val SHORTCUT_TYPE = "shortcut_type"

        const val CORRELATION_ID = "correlation_id"
        const val SCREEN_SESSION_ID = "screen_session_id"
        const val NOTIFICATION_TYPE = "notification_type"
        const val ITEM_ID = "item_id"
        const val ITEM_TYPE = "item_type"
        const val SOURCE_TRIGGER = "source_trigger"
        const val DEEP_LINK_DESTINATION = "deep_link_destination"
        const val COPY_VARIANT = "copy_variant"
        const val IMAGE_TYPE = "image_type"
        const val SESSION_TYPE = "session_type"
        const val DELAY_MINUTES = "delay_minutes"
        const val SHOWN_AT = "shown_at"
        const val TAPPED_AT = "tapped_at"
        const val CONVERSION_ACTION = "conversion_action"
        const val CONVERSION_TIME_MINUTES = "conversion_time_minutes"
        const val CTA = "cta"
        const val LANGUAGE = "language"
        const val TRIGGER = "trigger"
        const val ERROR_CODE = "error_code"
        const val ERROR_MESSAGE = "error_message"
    }

    // ============================================
    // PARAMETER VALUES (shared dictionary)
    // ============================================
    object Value {
        const val ALL = "all"

        /**
         * Content monetization type for template/song context events.
         * Derived from the item's `is_premium` flag:
         * premium item -> [ADS], free item -> [FREE].
         */
        object Type {
            const val ADS = "ads"
            const val FREE = "free"
        }

        object TabName {
            const val GALLERY = "gallery"
            const val SONG = "song"
            const val LIBRARY = "library"
        }

        object LibraryTab {
            const val VIDEO = "video"
            const val TEMPLATE_FAVORITE = "template_favorite"
            const val SONG_FAVORITE = "song_favorite"
        }

        object Location {
            const val UNKNOWN = "unknown"
            const val GALLERY = "gallery"
            const val SONG = "song"
            const val LIBRARY = "library"
            const val SEARCH = "search"
            const val EDIT = "edit"
            const val RESULT = "result"
            const val NEW = "new"

            const val GALLERY_BANNER = "gallery_banner"
            const val GALLERY_TEMPLATE = "gallery_template"

            const val SONG_FORYOU = "song_foryou"
            const val SONG_RANKING = "song_ranking"
            const val SONG_STATIONS = "song_stations"

            const val SONG_PREVIEW = "song_preview"
            const val SONG_PLAYER = "song_player"
            const val TEMPLATE_PREVIEW = "template_preview"
            const val VIDEO_PREVIEW = "video_preview"

            const val MEDIA_SELECT = "media_select"
            const val VIDEO_GENERATE = "video_generate"

            // Source locations for template/song context events
            const val HOME_BANNER = "home_banner"
            const val HOME_TEMPLATE = "home_template"
            const val PREVIEW_SWIPE = "preview_swipe"
            const val LIBRARY_RCM = "library_rcm"
            const val RESULT_RCM = "result_rcm"
            const val SEARCH_RESULT = "search_result"
            const val SHORTCUT_CREATE_VIDEO = "shortcut_create_video"
            const val SEARCH_RCM = "search_rcm"
            const val TEMPLATE_FAVORITE = "template_favorite"
            const val SONG_FAVORITE = "song_favorite"
            const val VIDEO_EDITOR = "video_editor"
            const val VIDEO_EDITOR_SEARCH = "video_editor_search"
            const val VIDEO_EDITOR_RCM = "video_editor_rcm"
            const val UNINSTALL = "uninstall"

            // Editor bottom player (change-song view)
            const val EDITOR_SONG = "editor_song"
            // song_starttime_change sources
            const val DURATION_BAR = "duration_bar"
            const val DRAG_BAR = "drag_bar"

            // Song list screens
            const val SUGGESTED_SONGS = "suggested_songs"
            const val WEEKLY_RANKING = "weekly_ranking"

            // Trending popup source locations
            const val POPUP_TRENDING_TEMPLATE = "popup_trending_template"
            const val POPUP_TRENDING_SONG = "popup_trending_song"

            // Trending popup template/song impression + click (promote-content popups)
            const val POPUP_PROMOTE_CONTENT = "popup_promote_content"
        }

        object Section {
            const val TEMPLATE = "template"
            const val MUSIC = "music"
        }

        object YesNo {
            const val Y = "y"
            const val N = "n"
        }

        object Option {
            const val GOOD = "good"
            const val BAD = "bad"
            const val ALLOW = "allow"
            const val NO_ALLOW = "no_allow"
            const val LIMIT_ACCESS = "limit_access"
        }

        object PerType {
            const val NOTI = "noti"
            const val MEDIA = "media"
            const val CAMERA = "camera"
            const val AUDIO = "audio"
        }

        object PopType {
            const val SYSTEM = "system"
            const val CUSTOM = "custom"
        }

        object RewardPopupType {
            const val YES = "yes"
            const val NO = "no"
            const val EXIT = "exit"
        }

        object PreviousAction {
            const val USE_TO_CREATE = "use_to_create"
            const val DOWNLOAD_CLICK = "download_click"
            const val REMOVE_WATERMARK_CLICK = "remove_watermark_click"
            const val UNLOCK_TEMPLATE_CLICK = "unlock_template_click"
            const val UNLOCK_EFFECT_CLICK = "unlock_effect_click"
            const val UNLOCK_QUALITY_CLICK = "done_edit_click"
            const val EDITOR_SONG_CONFIRM = "editor_song_confirm"
        }

        object Trigger {
            const val IDLE_AUTO_SELECT = "idle_auto_select"
            const val AD_RETURN_AUTO_SELECT = "ad_return_auto_select"
        }
    }
}
