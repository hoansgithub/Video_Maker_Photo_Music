package com.videomaker.aimusic.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * DTO for submitting user feedback to the shared Supabase `user_feedback` table.
 * Schema matches the table used by android-short-drama-app.
 */
@Serializable
data class UserFeedbackDto(
    @SerialName("device_id")
    val deviceId: String,

    @SerialName("user_id")
    val userId: String? = null,

    @SerialName("feedback_text")
    val feedbackText: String,

    @SerialName("satisfaction_response")
    val satisfactionResponse: String? = null,

    @SerialName("star_rating")
    val starRating: Int? = null,

    @SerialName("app_version")
    val appVersion: String,

    @SerialName("app_version_code")
    val appVersionCode: Int? = null,

    @SerialName("platform")
    val platform: String = "android",

    @SerialName("device_model")
    val deviceModel: String? = null,

    @SerialName("os_version")
    val osVersion: String? = null,

    @SerialName("locale")
    val locale: String? = null
)
