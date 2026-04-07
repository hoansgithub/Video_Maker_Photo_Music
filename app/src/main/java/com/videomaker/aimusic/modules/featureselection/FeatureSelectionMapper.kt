package com.videomaker.aimusic.modules.featureselection

const val FEATURE_ID_SONG = "photos_to_video"
const val FEATURE_ID_TEMPLATE = "music_video_instant"

fun mapFeatureToInitialTab(featureId: String?): Int = when (featureId) {
    FEATURE_ID_SONG -> 1
    FEATURE_ID_TEMPLATE -> 0
    else -> 0
}
