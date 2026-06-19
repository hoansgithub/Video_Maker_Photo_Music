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

---

## 5. Dependency Injection inside Scroll Loops (Caching Resolved Dependencies)

### Problem
Resolving dependencies dynamically (e.g. via `koinInject()` or similar Service Locator lookup) inside the `items` block of a `LazyColumn` or `LazyRow` executes lookup logic repeatedly as new items scroll into view. If resolving a dependency takes 2-5ms, scrolling is severely throttled.

### Solution
Resolve the dependencies once at the parent Screen/Content composable level and pass them down to child rows or item renderers, rather than invoking dependency lookups inside scroll items.

### Code Sample
```kotlin
// IN EFFICIENT WAY (Avoid)
LazyColumn {
    items(songs) { song ->
        val playbackSessionManager: MusicPlaybackSessionManager = koinInject() // RESOLVED FOR EVERY ROW
        SongRow(song, playbackSessionManager)
    }
}

// OPTIMIZED WAY
@Composable
fun SongsContent(songs: List<MusicSong>) {
    // Resolve once at the container level
    val playbackSessionManager: MusicPlaybackSessionManager = koinInject()

    LazyColumn {
        items(
            items = songs,
            key = { it.id }
        ) { song ->
            // Pass the cached instance down
            SongRow(
                song = song,
                sessionManager = playbackSessionManager
            )
        }
    }
}
```

---

## 6. Avoiding Scroll State Recomposition Overhead

### Problem
Reading scroll states like `listState.firstVisibleItemIndex` directly into Compose state variables (e.g., using `mutableStateOf` or read during composition) triggers a full recomposition of the screen on every single pixel scrolled. This creates heavy UI thread load during scroll interactions.

### Solution
Use `snapshotFlow` inside a `LaunchedEffect` to observe scroll updates asynchronously, or use `derivedStateOf` to only trigger recompositions when the computed boolean state actually changes (e.g., threshold reached for pagination).

### Code Sample
```kotlin
// IN EFFICIENT WAY (Avoid)
val firstVisibleIndex = listState.firstVisibleItemIndex // Triggers recomposition on every index change
if (firstVisibleIndex > 2) {
    ShowScrollToTopButton()
}

// OPTIMIZED WAY (observing changes side-effect style via snapshotFlow)
LaunchedEffect(listState) {
    var lastTrackedLocation: String? = null
    snapshotFlow { listState.firstVisibleItemIndex }
        .collect { index ->
            val location = if (index <= 2) "TOP" else "STATION"
            if (lastTrackedLocation != location) {
                trackTabSwipeAnalytics(location) // Only triggers when state changes
                lastTrackedLocation = location
            }
        }
}

// OPTIMIZED WAY (with derivedStateOf)
val shouldLoadMore by remember(listState) {
    derivedStateOf {
        val lastVisibleIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
        lastVisibleIndex >= listState.layoutInfo.totalItemsCount - 6
    }
}
```