# Android Compose & Image Loading Performance Optimization Guide

This document explains the root causes of common UI lag/jank issues in image-heavy Compose list screens and provides concrete code samples for implementing the solutions.

---

## 1. Coil Thread Pool Optimization (Preventing Lock Contention)

### Problem
By default, Coil decodes images on `Dispatchers.IO`. In a rapidly scrolling grid, Coil spawns many decoder threads (up to 64). CPU-intensive decoding on many parallel threads leads to JVM lock contention on thread management hooks, blocking the Main (UI) thread.

### Solution
Limit image decoding tasks to `Dispatchers.Default` (which matches the number of CPU cores).

### Code Sample
```kotlin
// VideoMakerApplication.kt or wherever you configure the global ImageLoader
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import kotlinx.coroutines.Dispatchers

class VideoMakerApplication : Application(), ImageLoaderFactory {
    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.20) // Use 20% of available RAM
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(1024 * 1024 * 100) // 100 MB
                    .build()
            }
            // Optimize thread management by replacing Dispatchers.IO (unbounded)
            // with Dispatchers.Default (bounded to CPU cores count)
            .decoderDispatcher(Dispatchers.Default)
            .crossfade(true)
            .build()
    }
}
```

---

## 2. Fast Static Thumbnail Decoding

### Problem
Using default JNI decoders designed for general-purpose image formats (such as animations or high-end formats) allocates heavy decoder structures, causing memory pressure and delay for simple static grid thumbnails.

### Solution
Enforce `BitmapFactoryDecoder.Factory()` for static resources.

### Code Sample
```kotlin
// TemplateCard.kt or GridItem.kt
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.decode.BitmapFactoryDecoder
import coil.request.CachePolicy
import coil.request.ImageRequest

@Composable
fun TemplateCard(
    thumbnailPath: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    val imageRequest = remember(thumbnailPath) {
        ImageRequest.Builder(context)
            .data(thumbnailPath)
            // Force the lightweight BitmapFactory decoder for static thumbnails
            .decoderFactory(BitmapFactoryDecoder.Factory())
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .crossfade(true)
            .build()
    }

    AsyncImage(
        model = imageRequest,
        contentDescription = "Thumbnail",
        modifier = modifier
    )
}
```

---

## 3. Asynchronous Loading for Local WebP/PNG Drawables

### Problem
`painterResource(R.drawable.my_large_asset)` decodes image resources synchronously on the Main Thread. If the resource is a large WebP/PNG (e.g. background graphics or custom card overlays), it blocks the UI thread for 10ms to 50ms, causing dropped frames.

### Solution
Use Coil's `rememberAsyncImagePainter` with cached and size-restricted constraints.

### Code Sample
```kotlin
// BannerStyle.kt
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.rememberAsyncImagePainter
import coil.decode.BitmapFactoryDecoder
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Precision
import coil.size.Size

@Composable
fun LocalBackgroundBanner(
    drawableResId: Int,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    // Load local drawable asynchronously off-thread
    val backgroundPainter = rememberAsyncImagePainter(
        model = remember(drawableResId) {
            ImageRequest.Builder(context)
                .data(drawableResId)
                .decoderFactory(BitmapFactoryDecoder.Factory())
                .memoryCachePolicy(CachePolicy.ENABLED)
                // Restrict size and precision to save memory overhead
                .precision(Precision.INEXACT)
                .size(Size.ORIGINAL)
                .build()
        }
    )

    Image(
        painter = backgroundPainter,
        contentDescription = "Banner Background",
        contentScale = ContentScale.Crop,
        modifier = modifier
    )
}
```

---

## 4. Virtualized Lazy Grid with Spanned Header Items

### Problem
Nesting a custom staggered grid (that computes columns manually via `Column` & `Row` groups) inside a `LazyColumn` breaks virtualization. The layout measures and constructs all grid cells instantly, causing severe startup layout lag.

### Solution
Flatten the grid cards into a native `LazyVerticalStaggeredGrid` and use `StaggeredGridItemSpan.FullLine` for headers.

### Code Sample
```kotlin
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun VirtualizedGalleryScreen(
    templates: List<VideoTemplate>,
    banners: List<BannerUi>
) {
    val listState = rememberLazyStaggeredGridState()

    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Fixed(2),
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalItemSpacing = 8.dp
    ) {
        // 1. Spanned Search Field
        item(key = "search", span = StaggeredGridItemSpan.FullLine) {
            SearchBarView()
        }

        // 2. Spanned Carousel
        if (banners.isNotEmpty()) {
            item(key = "banners", span = StaggeredGridItemSpan.FullLine) {
                BannerCarousel(banners)
            }
        }

        // 3. Spanned Section Header
        item(key = "header", span = StaggeredGridItemSpan.FullLine) {
            SectionHeader(title = "Featured Templates")
        }

        // 4. Staggered Grid Items (fully lazy & virtualized)
        items(
            items = templates,
            key = { template -> template.id }
        ) { template ->
            TemplateCard(thumbnailPath = template.thumbnailPath)
        }
    }
}
```
