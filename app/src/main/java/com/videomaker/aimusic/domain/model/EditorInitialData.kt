package com.videomaker.aimusic.domain.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

/**
 * Initial data for Editor screen - holds temporary project data
 * before project is created in database.
 *
 * This allows users to edit without committing to database until
 * they explicitly save via "Done" or "Save & Exit".
 *
 * Beat-sync mode only - no legacy fixed duration or transition percentage
 */
@Parcelize
@Serializable
data class EditorInitialData(
    val imageUris: List<String>,
    val effectSetId: String?,
    val templateId: String? = null,
    val musicSongId: Long?,
    val musicSongName: String? = null, // Pass song name to avoid extra network request
    val aspectRatio: AspectRatio,
    val analyticsVideoId: String? = null
) : Parcelable
