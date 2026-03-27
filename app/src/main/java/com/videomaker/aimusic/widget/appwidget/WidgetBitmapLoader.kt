package com.videomaker.aimusic.widget.appwidget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import androidx.core.graphics.createBitmap
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult

internal object WidgetBitmapLoader {

    private const val TEMPLATE_WIDTH = 300
    private const val TEMPLATE_HEIGHT = 400
    private const val SONG_COVER_SIZE = 200

    suspend fun loadTemplateBitmap(context: Context, url: String): Bitmap? =
        load(context, url, TEMPLATE_WIDTH, TEMPLATE_HEIGHT)

    suspend fun loadSongCoverBitmap(context: Context, url: String): Bitmap? =
        load(context, url, SONG_COVER_SIZE, SONG_COVER_SIZE)

    private suspend fun load(context: Context, url: String, width: Int, height: Int): Bitmap? {
        if (url.isBlank()) return null
        return try {
            val request = ImageRequest.Builder(context)
                .data(url)
                .allowHardware(false)
                .size(width, height)
                .build()
            (context.imageLoader.execute(request) as? SuccessResult)?.drawable?.toBitmap()
        } catch (_: Exception) {
            null
        }
    }

    private fun Drawable.toBitmap(): Bitmap {
        val bmp = createBitmap(intrinsicWidth, intrinsicHeight)
        val canvas = Canvas(bmp)
        setBounds(0, 0, canvas.width, canvas.height)
        draw(canvas)
        return bmp
    }
}
