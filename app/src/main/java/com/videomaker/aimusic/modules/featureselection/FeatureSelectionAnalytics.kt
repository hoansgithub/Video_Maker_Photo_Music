package com.videomaker.aimusic.modules.featureselection

const val EVENT_GENRE_SHOW = "genre_show"
const val EVENT_GENRE_SELECT = "genre_select"
const val EVENT_GENRE_NEXT = "genre_next"
const val PARAM_GENRE_SELECT = "genre_select"

// Personalizing screen events
// personalize_render: system renders the personalize (loading) step
// personalize_next: system redirects the user to the next step
const val EVENT_PERSONALIZE_RENDER = "personalize_render"
const val EVENT_PERSONALIZE_NEXT = "personalize_next"

fun toGenreAnalyticsValue(selectedGenres: List<String>): String = selectedGenres.joinToString(",")
