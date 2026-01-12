package com.videomaker.aimusic.media.export

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileInputStream

/**
 * MediaStoreHelper - Utility for saving media to device gallery
 *
 * Uses MediaStore API to make videos visible in the device's Photos/Gallery app.
 * Supports Android 10+ (API 29+) scoped storage.
 */
object MediaStoreHelper {

    private const val TAG = "MediaStoreHelper"

    /**
     * Result of saving to gallery
     */
    sealed class SaveResult {
        data class Success(val uri: Uri) : SaveResult()
        data class Error(val message: String) : SaveResult()
    }

    /**
     * Save a video file to the device's gallery (Movies folder)
     *
     * @param context Application context
     * @param videoFile The video file to save
     * @param displayName Optional display name (defaults to file name)
     * @return SaveResult indicating success or failure
     */
    fun saveVideoToGallery(
        context: Context,
        videoFile: File,
        displayName: String? = null
    ): SaveResult {
        if (!videoFile.exists()) {
            return SaveResult.Error("Video file does not exist")
        }

        val fileName = displayName ?: videoFile.nameWithoutExtension
        val mimeType = "video/mp4"

        return try {
            val contentValues = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, "$fileName.mp4")
                put(MediaStore.Video.Media.MIME_TYPE, mimeType)
                put(MediaStore.Video.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
                put(MediaStore.Video.Media.DATE_MODIFIED, System.currentTimeMillis() / 1000)

                // For Android 10+ (API 29+), use relative path
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/VideoMaker")
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
            }

            val resolver = context.contentResolver
            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            }

            // Insert the metadata and get a URI
            val uri = resolver.insert(collection, contentValues)
                ?: return SaveResult.Error("Failed to create MediaStore entry")

            android.util.Log.d(TAG, "Created MediaStore entry: $uri")

            // Copy the file content
            resolver.openOutputStream(uri)?.use { outputStream ->
                FileInputStream(videoFile).use { inputStream ->
                    val bytes = inputStream.copyTo(outputStream)
                    android.util.Log.d(TAG, "Copied $bytes bytes to gallery")
                }
            } ?: return SaveResult.Error("Failed to open output stream")

            // Mark as complete (Android 10+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.Video.Media.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)
            }

            android.util.Log.d(TAG, "Video saved to gallery successfully: $uri")
            SaveResult.Success(uri)

        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to save video to gallery", e)
            SaveResult.Error(e.message ?: "Unknown error")
        }
    }

    /**
     * Generate a unique display name for the video
     */
    fun generateDisplayName(projectId: String): String {
        val timestamp = java.text.SimpleDateFormat(
            "yyyyMMdd_HHmmss",
            java.util.Locale.getDefault()
        ).format(java.util.Date())
        return "VideoMaker_$timestamp"
    }
}
