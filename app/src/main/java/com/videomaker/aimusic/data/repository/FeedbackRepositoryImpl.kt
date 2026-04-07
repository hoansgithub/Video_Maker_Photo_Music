package com.videomaker.aimusic.data.repository

import android.util.Log
import com.videomaker.aimusic.core.device.DeviceInfoProvider
import com.videomaker.aimusic.data.remote.dto.UserFeedbackDto
import com.videomaker.aimusic.domain.repository.FeedbackRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Submits user feedback to the shared Supabase `user_feedback` table.
 */
class FeedbackRepositoryImpl(
    private val supabaseClient: SupabaseClient,
    private val deviceInfoProvider: DeviceInfoProvider
) : FeedbackRepository {

    companion object {
        private const val TAG = "FeedbackRepository"
        private const val TABLE_USER_FEEDBACK = "user_feedback"
    }

    override suspend fun submitFeedback(
        feedbackText: String,
        satisfactionResponse: String?,
        starRating: Int?
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val dto = UserFeedbackDto(
                deviceId = deviceInfoProvider.deviceId,
                feedbackText = feedbackText,
                satisfactionResponse = satisfactionResponse,
                starRating = starRating,
                appVersion = deviceInfoProvider.appVersion,
                appVersionCode = deviceInfoProvider.appVersionCode,
                deviceModel = deviceInfoProvider.deviceModel,
                osVersion = deviceInfoProvider.osVersion,
                locale = deviceInfoProvider.locale
            )

            supabaseClient.from(TABLE_USER_FEEDBACK).insert(dto)

            Log.d(TAG, "Feedback submitted successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to submit feedback: ${e.message}", e)
            Result.failure(e)
        }
    }
}
