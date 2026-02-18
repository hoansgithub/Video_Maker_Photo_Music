package com.videomaker.aimusic.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.videomaker.aimusic.ui.theme.PlaceholderBackground

/**
 * Image component with explicit memory + disk caching via Coil.
 *
 * - Stable [ImageRequest] via [remember(imageUrl)] — no unnecessary reloads on recomposition
 * - Explicit [memoryCacheKey] / [diskCacheKey] so Coil's 100 MB disk cache is properly keyed
 * - Hardware bitmaps enabled for GPU rendering
 * - 150 ms crossfade for smooth transitions
 * - Loading: plain [PlaceholderBackground] fill
 * - Error / empty URL: [PlaceholderBackground] fill + centered 🎵 emoji
 *
 * The [ImageLoader] (25% heap + 100 MB disk) is configured in [VideoMakerApplication.newImageLoader]
 * and automatically picked up by Coil as the singleton loader.
 */
@Composable
fun AppAsyncImage(
    imageUrl: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    val context = LocalContext.current

    val imageRequest = remember(imageUrl) {
        ImageRequest.Builder(context)
            .data(imageUrl.ifEmpty { null })
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .memoryCacheKey(imageUrl.ifEmpty { null })
            .diskCacheKey(imageUrl.ifEmpty { null })
            .allowHardware(true)
            .crossfade(150)
            .build()
    }

    SubcomposeAsyncImage(
        model = imageRequest,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
        loading = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(PlaceholderBackground)
            )
        },
        error = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(PlaceholderBackground),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "🎵", fontSize = 24.sp)
            }
        },
        success = {
            SubcomposeAsyncImageContent()
        }
    )
}
