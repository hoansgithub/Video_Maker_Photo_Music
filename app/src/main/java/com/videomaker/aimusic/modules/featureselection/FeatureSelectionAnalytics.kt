package com.videomaker.aimusic.modules.featureselection

const val EVENT_GENRE_SHOW = "genre_show"
const val EVENT_GENRE_SELECT = "genre_select"
const val EVENT_GENRE_NEXT = "genre_next"
const val PARAM_GENRE_SELECT = "genre_select"

fun toGenreAnalyticsValue(selectedGenres: List<String>): String = selectedGenres.joinToString(",")
