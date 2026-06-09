package com.videomaker.aimusic.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.rememberAsyncImagePainter
import coil.decode.BitmapFactoryDecoder
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Precision

/**
 * Drop-in replacement for `Image(painterResource(R.drawable.large_image), ...)`
 * that decodes the drawable **off the main thread** via Coil.
 *
 * Use this for any drawable >=15 KB where synchronous `painterResource()` would
 * block the UI thread for 10-50 ms during decode.
 *
 * - Decodes on Coil's background dispatcher (no main-thread jank)
 * - Uses [BitmapFactoryDecoder] (pure-Kotlin, no heavy JNI overhead)
 * - Memory + disk cache enabled so repeated compositions are instant
 * - [Precision.INEXACT] allows Coil to downsample when the target is smaller
 */
@Composable
fun LocalAsyncImage(
    @DrawableRes resId: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    alignment: Alignment = Alignment.Center
) {
    val context = LocalContext.current
    val painter = rememberAsyncImagePainter(
        model = ImageRequest.Builder(context)
            .data(resId)
            .decoderFactory(BitmapFactoryDecoder.Factory())
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .precision(Precision.INEXACT)
            .allowHardware(true)
            .build()
    )
    Image(
        painter = painter,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
        alignment = alignment
    )
}
