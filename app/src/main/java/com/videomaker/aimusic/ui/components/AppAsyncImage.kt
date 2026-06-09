package com.videomaker.aimusic.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.decode.BitmapFactoryDecoder
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
 * When [placeholderUrl] is supplied (e.g. a local `file:///android_asset` image), the component
 * switches to an onboarding-style flow: the local image renders first and stays fully visible,
 * and the remote image stays hidden until it has FINISHED loading, then fades in over the local
 * one (no progressive crossfade, no flash of the placeholder colour). On error the local image
 * simply remains.
 *
 * The [ImageLoader] (25% heap + 100 MB disk) is configured in [VideoMakerApplication.newImageLoader]
 * and automatically picked up by Coil as the singleton loader.
 */
@Composable
fun AppAsyncImage(
    imageUrl: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    placeholderUrl: String? = null,
) {
    val context = LocalContext.current

    val imageRequest = remember(imageUrl) {
        ImageRequest.Builder(context)
            .data(imageUrl.ifEmpty { null })
            .decoderFactory(BitmapFactoryDecoder.Factory())
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .memoryCacheKey(imageUrl.ifEmpty { null })
            .diskCacheKey(imageUrl.ifEmpty { null })
            .allowHardware(true)
            .crossfade(150)
            .build()
    }

    // Onboarding-style flow when a local stand-in is provided: local first, remote on done.
    if (!placeholderUrl.isNullOrEmpty()) {
        var isRemoteLoaded by remember(imageUrl) { mutableStateOf(false) }
        val remoteAlpha by animateFloatAsState(
            targetValue = if (isRemoteLoaded) 1f else 0f,
            animationSpec = tween(durationMillis = 300),
            label = "cover_remote_alpha"
        )

        Box(modifier = modifier) {
            // Layer 1: local global image — always visible (placeholder while loading, fallback on error).
            AsyncImage(
                model = placeholderUrl,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale
            )

            // Layer 2: remote image — only shown once fully loaded, fading in over the local one.
            if (imageUrl.isNotEmpty() && imageUrl != placeholderUrl) {
                SubcomposeAsyncImage(
                    model = imageRequest,
                    contentDescription = contentDescription,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = remoteAlpha },
                    contentScale = contentScale,
                    loading = { /* local layer visible beneath */ },
                    error = { /* local layer visible beneath */ },
                    success = {
                        isRemoteLoaded = true
                        SubcomposeAsyncImageContent()
                    }
                )
            }
        }
        return
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
