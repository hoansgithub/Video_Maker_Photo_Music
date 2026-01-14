# Android Development Rules

## Tech Stack

- **Language**: Kotlin 2.0+
- **UI**: Jetpack Compose (December 2025 BOM)
- **Min SDK**: 28 (Android 9.0)
- **Target SDK**: 36
- **Architecture**: Clean Architecture (Data/Domain/Presentation)
- **Navigation**: Navigation 3 (1.0.0-alpha03) - developer-owned back stack
- **Concurrency**: Coroutines + Flow
- **DI**: ACCDI (AlcheClub Custom DI) - see di/ module
- **Build**: KSP2 (not KAPT)

### Future Considerations

| Technology | Current | Future Option | Notes |
|------------|---------|---------------|-------|
| Navigation | Nav3 1.0.0-alpha03 | Latest stable | Update when stable |
| Compose BOM | 2025.10.01 | Latest stable | Update quarterly |
| Media3 | 1.6.1 | Latest stable | Update for new features/fixes |

**Navigation 3 Architecture:**
- You own the back stack (`SnapshotStateList<AppRoute>`)
- Navigation = list manipulation (add/remove items)
- Routes implement `NavKey` interface
- `NavDisplay` replaces `NavHost`
- `entry<T>` replaces `composable<T>`
- Reference: https://developer.android.com/guide/navigation/navigation-3

---

## Project: Video Maker Photo Music

### MVP Scope

**Images Only** - The MVP supports only image/photo input. Video clip input will be added in a future version.

- Input: Photos from gallery (via Photo Picker)
- Output: MP4 slideshow video with transitions and music

### Core Libraries

| Component | Library | Version |
|-----------|---------|---------|
| Video Editing | Media3 Transformer | 1.9.0 |
| Preview | Media3 ExoPlayer + CompositionPlayer | 1.9.0 |
| Effects | Media3 Effect API | 1.9.0 |
| Media Picker | Android Photo Picker | System API |
| Database | Room + KSP2 | 2.8.4 |
| Background Work | WorkManager | 2.11.0 |

### Key Documentation

See `docs/RESEARCH.md` for full technical research including:
- Media3 Transformer pipeline architecture
- Audio composition and looping strategies
- **Transition Sets** - Curated collections of 20+ transitions (2D & 3D)
- Storage model with Room/KSP2
- Preview vs Export architecture

### Settings Model

Users can individually select each setting - mix and match as they like.

| Setting | Options | Description |
|---------|---------|-------------|
| **Transition Set** | Classic, Geometric, Cinematic, Creative, Minimal, Dynamic | Collection of 20+ transition animations |
| **Transition Duration** | 2, 3, 4, 5, 6, 8, 10, 12 seconds | How long each image is shown |
| **Overlay Frame** | None, frame1, frame2, ... | Decorative frame overlay |
| **Background Music** | None, track1, track2, or custom | Audio track |
| **Audio Volume** | 0-100% | Music volume level |
| **Aspect Ratio** | 16:9, 9:16, 1:1, 4:3 | Output video dimensions |

### Bundled Assets

```
app/src/main/assets/audio/
├── track1.mp3    # Sample background music
└── track2.mp3    # Sample background music

app/src/main/res/drawable/
├── frame1.webp   # Overlay frame 1
└── frame2.webp   # Overlay frame 2
```

### Image Scaling Strategy

Images are rendered with a **blurred background fill** to avoid black bars:

```
┌─────────────────────────────────────┐
│▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓│  ← Blurred scale-aspect-fill
│▓▓▓┌─────────────────────────┐▓▓▓▓▓│
│▓▓▓│                         │▓▓▓▓▓│
│▓▓▓│   Original Image        │▓▓▓▓▓│  ← Sharp scale-aspect-fit
│▓▓▓│   (aspect-fit)          │▓▓▓▓▓│
│▓▓▓│                         │▓▓▓▓▓│
│▓▓▓└─────────────────────────┘▓▓▓▓▓│
│▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓│
└─────────────────────────────────────┘

Layers (bottom to top):
1. Background: Same image, scale-aspect-fill + Gaussian blur
2. Foreground: Original image, scale-aspect-fit (sharp)
3. Overlay: Decorative frame (optional)
```

---

## Agent Workflow

```
NEW FEATURE
─────────────
1. ultrathink-planner  → Design approach
2. kotlin-architect    → Architecture decisions
3. kotlin-developer    → Implementation
4. navigation-guardian → Event-based navigation check
5. kotlin-tester       → Unit tests

BUG FIX
───────
1. kotlin-debugger     → Root cause
2. kotlin-developer    → Fix
3. navigation-guardian → Verify navigation patterns

CODE REVIEW
───────────
1. navigation-guardian → Navigation anti-pattern scan
2. kotlin-reviewer     → Quality check
```

---

## Critical Rules

### 1. Navigation Pattern - StateFlow-Based (GOLD STANDARD)

```kotlin
// ❌ FORBIDDEN - State-based navigation (CAUSES BUGS!)
LaunchedEffect(uiState) {
    if (uiState is Success) navigate()  // Re-triggers on back/rotation!
}

// ❌ FORBIDDEN - Navigation flags in state
data class Success(val shouldNavigate: Boolean)

// ❌ FORBIDDEN - hasNavigated flags
var hasNavigated = false
if (!hasNavigated) { navigate(); hasNavigated = true }

// ✅ REQUIRED - StateFlow for navigation events (Google recommended)
private val _navigationEvent = MutableStateFlow<NavigationEvent?>(null)
val navigationEvent: StateFlow<NavigationEvent?> = _navigationEvent.asStateFlow()

// ✅ REQUIRED - Navigation method with direct assignment
fun navigateToNext() {
    _navigationEvent.value = NavigationEvent.GoToNext
}

// ✅ REQUIRED - Callback to clear navigation event after handling
fun onNavigationHandled() {
    _navigationEvent.value = null
}

// ✅ REQUIRED - LaunchedEffect(navigationEvent) for observation
val navigationEvent by viewModel.navigationEvent.collectAsStateWithLifecycle()
LaunchedEffect(navigationEvent) {
    navigationEvent?.let { event ->
        when (event) {
            is NavigationEvent.GoToNext -> navigateNext()
        }
        viewModel.onNavigationHandled()
    }
}
```

**Why StateFlow-Based Navigation is Preferred:**
- Google's officially recommended pattern for navigation events
- Events become state with explicit consumed callback - clearer lifecycle
- `collectAsStateWithLifecycle` automatically handles lifecycle transitions
- `LaunchedEffect(navigationEvent)` only triggers when event changes
- No risk of missing events during config changes
- Consistent with Google's architecture samples (2024+)

### 1b. Navigation 3 Architecture

```kotlin
// ============================================
// ROUTES - Must implement NavKey
// ============================================
sealed class AppRoute : NavKey, Parcelable {
    @Parcelize @Serializable
    data object Home : AppRoute()

    @Parcelize @Serializable
    data class Editor(val projectId: String) : AppRoute()
}

// ============================================
// NAVIGATION STATE - You own the back stack
// ============================================
@Stable
class NavigationState(
    val backStack: SnapshotStateList<AppRoute>  // You own this!
) {
    val currentRoute: AppRoute? get() = backStack.lastOrNull()
    val canGoBack: Boolean get() = backStack.size > 1
}

@Composable
fun rememberNavigationState(startRoute: AppRoute): NavigationState {
    val backStack = rememberSaveable(saver = backStackSaver()) {
        mutableStateListOf(startRoute)
    }
    return NavigationState(backStack)
}

// ✅ REQUIRED - Safe cast in saver to prevent crashes
private fun backStackSaver(): Saver<SnapshotStateList<AppRoute>, Any> {
    return listSaver(
        save = { it.toList() },
        restore = { saved ->
            // Safe cast - filters to only valid AppRoute instances
            val list = (saved as? List<*>)?.filterIsInstance<AppRoute>().orEmpty()
            mutableStateListOf<AppRoute>().apply { addAll(list) }
        }
    )
}

// ============================================
// NAVIGATOR - Navigation actions
// LIFECYCLE WARNING: Only instantiate in @Composable with remember()
// ============================================
@Stable
class Navigator(private val state: NavigationState) {
    fun navigate(route: AppRoute) {
        state.backStack.add(route)
    }

    fun goBack(): Boolean {
        return if (state.canGoBack) {
            state.backStack.removeLastOrNull() != null  // Check result!
        } else false
    }

    fun clearAndNavigate(route: AppRoute) {
        state.backStack.apply {  // Atomic operation
            clear()
            add(route)
        }
    }
}

// ============================================
// APP NAVIGATION - Using NavDisplay
// ============================================
@Composable
fun AppNavigation(startWithOnboarding: Boolean) {
    val startRoute = if (startWithOnboarding) AppRoute.Onboarding else AppRoute.Home
    val navigationState = rememberNavigationState(startRoute)
    val navigator = remember(navigationState) { Navigator(navigationState) }

    NavDisplay(
        backStack = navigationState.backStack,
        onBack = { navigator.goBack() },
        entryProvider = { route ->
            NavEntry(route) {
                RouteContent(route, navigator)
            }
        }
    )
}

// ============================================
// VIEWMODEL WITH PARAMETERIZED ROUTES
// ============================================
@Composable
fun RouteContent(route: AppRoute, navigator: Navigator) {
    when (route) {
        is AppRoute.Editor -> {
            // ✅ REQUIRED - Key factory AND viewModel to route parameter
            val factory = remember(route.projectId) {
                ACCDI.get<EditorViewModelFactory>()
            }
            val viewModel: EditorViewModel = viewModel(
                key = "editor_${route.projectId}",  // Prevents wrong VM reuse
                factory = createSafeViewModelFactory { factory.create(route.projectId) }
            )
            EditorScreen(viewModel = viewModel, ...)
        }
        // ...
    }
}

// ✅ REQUIRED - Type-safe ViewModel factory (no unchecked casts!)
private inline fun <reified VM : ViewModel> createSafeViewModelFactory(
    crossinline creator: () -> VM
): ViewModelProvider.Factory {
    return object : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val viewModel = creator()
            if (modelClass.isAssignableFrom(viewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return viewModel as T
            } else {
                throw IllegalArgumentException(
                    "Unknown ViewModel: ${modelClass.name}, expected: ${viewModel::class.java.name}"
                )
            }
        }
    }
}
```

**Key Navigation 3 Differences from Nav2:**

| Aspect | Navigation 2.x | Navigation 3 |
|--------|---------------|--------------|
| Back stack | Library-managed | Developer-owned `SnapshotStateList` |
| Navigate | `navController.navigate()` | `navigator.navigate()` (list.add) |
| Go back | `navController.popBackStack()` | `navigator.goBack()` (list.remove) |
| Route access | `backStackEntry.toRoute<T>()` | Direct `key` parameter in entry |
| Host | `NavHost` | `NavDisplay` |
| Destinations | `composable<T>` | `entry<T>` in entryProvider |
| State survival | Automatic | `rememberSaveable` with custom saver |

**Navigation 3 Anti-Patterns to Avoid:**

```kotlin
// ❌ FORBIDDEN - Unsafe cast in ViewModel factory
@Suppress("UNCHECKED_CAST")
return factory.create(projectId) as T  // Can crash!

// ❌ FORBIDDEN - No remember key for parameterized routes
val factory = remember { ACCDI.get<EditorViewModelFactory>() }  // Wrong VM on route change!

// ❌ FORBIDDEN - Navigator stored outside composition
class MyViewModel(val navigator: Navigator)  // Memory leak!

// ❌ FORBIDDEN - Unsafe saver cast
val list = saved as List<AppRoute>  // Crashes on corrupted state!

// ✅ REQUIRED - Safe patterns shown above
```

### 2. Never Embed Activities as Composables

```kotlin
// ❌ FORBIDDEN - Bypasses Activity lifecycle!
composable<AppDestination.Feature> {
    FeatureScreen()  // Activity's onBackPressed, ads, etc. NEVER called!
}

// ✅ REQUIRED - Launch Activity directly
private fun navigateToFeature() {
    startActivity(Intent(this, FeatureActivity::class.java))
}
```

### 3. collectAsStateWithLifecycle (Not collectAsState)

```kotlin
// ❌ FORBIDDEN
val uiState by viewModel.uiState.collectAsState()

// ✅ REQUIRED - Lifecycle-aware
val uiState by viewModel.uiState.collectAsStateWithLifecycle()
```

### 4. viewModelScope for Coroutines

```kotlin
// ❌ FORBIDDEN - Manual scope management
private val scope = CoroutineScope(Dispatchers.Main)

// ❌ FORBIDDEN - GlobalScope
GlobalScope.launch { }

// ✅ REQUIRED - Auto-cancelled with ViewModel
viewModelScope.launch {
    // Automatically cancelled when ViewModel cleared
}
```

### 5. Sealed Class State Machines

```kotlin
// ❌ FORBIDDEN - Multiple boolean flags
var isLoading = false
var hasError = false
var data: Data? = null

// ✅ REQUIRED - Single state source
sealed class FeatureUiState {
    data object Loading : FeatureUiState()
    data class Success(val data: Data) : FeatureUiState()
    data class Error(val message: String) : FeatureUiState()
}
```

### 6. NO CRASHES - No Force Unwrap

```kotlin
// ❌ FORBIDDEN
val value = nullable!!

// ✅ REQUIRED - Safe handling
val value = nullable ?: run {
    Logger.e("Value was null")
    return
}

// ✅ OR with default
val value = nullable ?: defaultValue
```

### 7. Activity Navigation - Always finish()

```kotlin
// ❌ FORBIDDEN - Creates activity stack issues
startActivity(intent)

// ✅ REQUIRED - For forward navigation
startActivity(intent)
finish()
```

---

## Architecture Patterns

### ViewModel Pattern (GOLD STANDARD)

```kotlin
// ============================================
// UI STATE
// ============================================
sealed class FeatureUiState {
    data object Loading : FeatureUiState()
    data class Success(val data: FeatureData) : FeatureUiState()
    data class Error(val message: String) : FeatureUiState()
}

// ============================================
// NAVIGATION EVENTS
// ============================================
sealed class FeatureNavigationEvent {
    data object NavigateBack : FeatureNavigationEvent()
    data class NavigateToDetail(val id: String) : FeatureNavigationEvent()
}

// ============================================
// VIEW MODEL
// ============================================
@HiltViewModel
class FeatureViewModel @Inject constructor(
    private val fetchDataUseCase: FetchDataUseCase
) : ViewModel() {

    // UI State - for displaying data
    private val _uiState = MutableStateFlow<FeatureUiState>(FeatureUiState.Loading)
    val uiState: StateFlow<FeatureUiState> = _uiState.asStateFlow()

    // Navigation Events - StateFlow-based (Google recommended)
    private val _navigationEvent = MutableStateFlow<FeatureNavigationEvent?>(null)
    val navigationEvent: StateFlow<FeatureNavigationEvent?> = _navigationEvent.asStateFlow()

    init {
        loadData()
    }

    fun onItemClick(id: String) {
        _navigationEvent.value = FeatureNavigationEvent.NavigateToDetail(id)
    }

    fun navigateBack() {
        _navigationEvent.value = FeatureNavigationEvent.NavigateBack
    }

    /** Called by UI after navigation is handled - clears the event */
    fun onNavigationHandled() {
        _navigationEvent.value = null
    }

    private fun loadData() {
        viewModelScope.launch {
            _uiState.value = FeatureUiState.Loading

            when (val result = fetchDataUseCase()) {
                is Result.Success -> _uiState.value = FeatureUiState.Success(result.data)
                is Result.Error -> _uiState.value = FeatureUiState.Error(result.message)
            }
        }
    }
}
```

### Composable Screen Pattern

```kotlin
@Composable
fun FeatureScreen(
    viewModel: FeatureViewModel = hiltViewModel(),
    onNavigateToDetail: (String) -> Unit,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val navigationEvent by viewModel.navigationEvent.collectAsStateWithLifecycle()

    // Handle navigation events - StateFlow-based (Google recommended pattern)
    // Observe navigationEvent StateFlow and call onNavigationHandled() after navigating
    LaunchedEffect(navigationEvent) {
        navigationEvent?.let { event ->
            when (event) {
                is FeatureNavigationEvent.NavigateToDetail -> onNavigateToDetail(event.id)
                is FeatureNavigationEvent.NavigateBack -> onNavigateBack()
            }
            viewModel.onNavigationHandled()
        }
    }

    // UI based on state
    when (val state = uiState) {
        is FeatureUiState.Loading -> LoadingContent()
        is FeatureUiState.Success -> SuccessContent(data = state.data)
        is FeatureUiState.Error -> ErrorContent(message = state.message)
    }
}
```

### Repository Pattern

```kotlin
// Interface in Domain layer
interface FeatureRepository {
    suspend fun getData(): Result<FeatureData>
    suspend fun saveData(data: FeatureData): Result<Unit>
}

// Implementation in Data layer
class FeatureRepositoryImpl @Inject constructor(
    private val remoteDataSource: FeatureRemoteDataSource,
    private val localDataSource: FeatureLocalDataSource
) : FeatureRepository {

    override suspend fun getData(): Result<FeatureData> {
        return try {
            val data = remoteDataSource.fetchData()
            localDataSource.cache(data)
            Result.Success(data)
        } catch (e: Exception) {
            Logger.e("FeatureRepository", "Failed to fetch: ${e.message}")
            Result.Error(e.message ?: "Unknown error")
        }
    }
}
```

### Use Case Pattern

```kotlin
class FetchFeatureUseCase @Inject constructor(
    private val repository: FeatureRepository
) {
    suspend operator fun invoke(): Result<FeatureData> {
        return repository.getData()
    }
}
```

---

## Naming Conventions

| Type | Convention | Example |
|------|------------|---------|
| Activity | `*Activity` | `ProfileActivity` |
| ViewModel | `*ViewModel` | `ProfileViewModel` |
| UI State | `*UiState` | `ProfileUiState` |
| Navigation Event | `*NavigationEvent` | `ProfileNavigationEvent` |
| Repository Interface | No suffix | `UserRepository` |
| Repository Impl | `*Impl` | `UserRepositoryImpl` |
| Use Case | `*UseCase` | `FetchUserUseCase` |
| Composable | `*Screen`, `*Content` | `ProfileScreen`, `ProfileContent` |

---

## File Organization

```kotlin
// ============================================
// UI STATE
// ============================================
sealed class FeatureUiState { ... }

// ============================================
// NAVIGATION EVENTS
// ============================================
sealed class FeatureNavigationEvent { ... }

// ============================================
// VIEW MODEL
// ============================================
@HiltViewModel
class FeatureViewModel @Inject constructor(...) : ViewModel() { ... }

// ============================================
// ACTIVITY
// ============================================
class FeatureActivity : ComponentActivity() { ... }

// ============================================
// COMPOSABLE SCREEN
// ============================================
@Composable
fun FeatureScreen(...) { ... }

// ============================================
// PREVIEW
// ============================================
@Preview
@Composable
private fun FeatureScreenPreview() { ... }
```

---

## Emergency Checklist

Before EVERY code change:

**Navigation 3:**
- [ ] Routes implement `NavKey` interface
- [ ] `SnapshotStateList` for back stack (NOT NavBackStack)
- [ ] `rememberSaveable` with safe saver for back stack persistence
- [ ] `remember(route.param)` key for parameterized route factories
- [ ] `viewModel(key = "...")` for parameterized ViewModels
- [ ] `createSafeViewModelFactory` (NOT unchecked cast)
- [ ] Navigator only in `@Composable` with `remember()` (NOT in ViewModel)
- [ ] Safe cast with `filterIsInstance` in saver restore

**ViewModel Navigation Events:**
- [ ] `StateFlow<Event?>` for navigation events (NOT state-based)
- [ ] `LaunchedEffect(navigationEvent)` for event observation
- [ ] `onNavigationHandled()` callback to clear event after navigating

**General:**
- [ ] `collectAsStateWithLifecycle()` for StateFlow
- [ ] `viewModelScope` for coroutines
- [ ] `finish()` after `startActivity()` for forward nav
- [ ] Sealed class for UI state
- [ ] No `!!` force unwrap
- [ ] No Activities embedded as composables
- [ ] Ads destroyed only on `ON_DESTROY`
- [ ] Protocol dependencies (not concrete)

---

## Common Gotchas

### Navigation Breaks on Back Button

**Cause**: Using `LaunchedEffect(uiState)` for navigation
**Fix**: Use `Channel<NavigationEvent>` + `LaunchedEffect(Unit)`

### State Not Updating

**Cause**: Using `collectAsState()` instead of `collectAsStateWithLifecycle()`
**Fix**: Always use `collectAsStateWithLifecycle()`

### Activity's onBackPressed Not Called

**Cause**: Screen embedded as composable instead of Activity
**Fix**: Use `startActivity()` for screens with dedicated Activities

### Memory Leaks

**Cause**: Using `GlobalScope` or manual `CoroutineScope`
**Fix**: Always use `viewModelScope`

### Ad Caching Not Working

**Cause**: Destroying ads in `onDispose`
**Fix**: Only destroy in `Lifecycle.Event.ON_DESTROY`

---

## Video Maker Specific Patterns

### 1. Media3 Transformer Usage

```kotlin
// ❌ FORBIDDEN - Using raw MediaCodec for composition
MediaCodec.createEncoderByType("video/avc")

// ✅ REQUIRED - Use Media3 Transformer
val transformer = Transformer.Builder(context)
    .addListener(transformerListener)
    .build()

transformer.start(composition, outputPath)
```

### 2. Composition Creation Pattern

```kotlin
// ✅ REQUIRED - Single composition factory for preview + export
class CompositionFactory @Inject constructor(
    private val context: Context
) {
    fun createComposition(project: ProjectWithAssets): Composition {
        val videoSequence = EditedMediaItemSequence(
            project.assets.map { createEditedMediaItem(it, project.settings) }
        )

        return Composition.Builder(listOf(videoSequence))
            .setVideoCompositorSettings(aspectRatioSettings)
            .build()
    }
}

// ❌ FORBIDDEN - Separate composition logic for preview vs export
// Both must use the same CompositionFactory
```

### 3. Photo Picker (No Permissions Required)

```kotlin
// ❌ FORBIDDEN - Requesting media permissions for picking
Manifest.permission.READ_MEDIA_IMAGES
Manifest.permission.READ_MEDIA_VIDEO

// ✅ REQUIRED - Use Photo Picker API
val pickMedia = rememberLauncherForActivityResult(
    ActivityResultContracts.PickMultipleVisualMedia()
) { uris -> /* handle selection */ }

// MVP: Images only
pickMedia.launch(
    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
)

// Future: When video input is supported
// pickMedia.launch(
//     PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
// )
```

### 4. Room with KSP2

```kotlin
// ❌ FORBIDDEN - Using KAPT for Room
kapt("androidx.room:room-compiler:2.8.4")

// ✅ REQUIRED - Use KSP only
ksp("androidx.room:room-compiler:2.8.4")

// gradle.properties
ksp.useKSP2=true
```

### 5. Background Export with WorkManager

```kotlin
// ❌ FORBIDDEN - Export in ViewModel coroutine
viewModelScope.launch {
    transformer.start(composition, outputPath)  // Won't survive process death!
}

// ✅ REQUIRED - Use WorkManager + CoroutineWorker
@HiltWorker
class VideoExportWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // Export logic here - survives process death
    }
}
```

### 6. Preview Player Lifecycle

```kotlin
// ❌ FORBIDDEN - Not releasing player resources
val player = CompositionPlayer.Builder(context).build()
// No cleanup!

// ✅ REQUIRED - Proper lifecycle management
DisposableEffect(composition) {
    val player = CompositionPlayer.Builder(context)
        .build()
        .apply { setComposition(composition) }

    onDispose {
        player.release()  // Always release!
    }
}
```

### 7. Asset URI Handling

```kotlin
// ❌ FORBIDDEN - Assuming URIs persist across sessions
val uri = savedUri  // May be invalid after app restart!

// ✅ REQUIRED - Validate URIs on project load
suspend fun validateAssets(project: Project): List<AssetValidation> {
    return project.assets.map { asset ->
        try {
            context.contentResolver.openInputStream(asset.uri)?.close()
            AssetValidation.Valid(asset)
        } catch (e: Exception) {
            AssetValidation.Invalid(asset, "Asset no longer accessible")
        }
    }
}
```

### 8. Effects Pipeline

```kotlin
// ✅ REQUIRED - Use Media3 Effect API for transformations
val effects = Effects(
    /* audioProcessors */ listOf(volumeProcessor),
    /* videoEffects */ listOf(
        Presentation.createForAspectRatio(
            aspectRatio,
            Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP
        ),
        overlayEffect
    )
)

val editedItem = EditedMediaItem.Builder(mediaItem)
    .setEffects(effects)
    .build()
```

### 9. CRITICAL: Transition Texture Color Consistency

**Problem Discovered (3-day investigation):**
When using Media3 Transformer with custom `GlEffect`/`GlShaderProgram` for transitions between images, there was a visible color difference:
- During transition: TO image appeared brighter
- After transition ended: Same image appeared darker (visible "jump")

**Root Cause:**
Media3's internal image-to-texture pipeline uses different color space handling than `BitmapFactory.decodeFile` + `GLUtils.texImage2D`. Even loading the same PNG file, the resulting GPU textures had different RGB values.

**The Fix - Single Source of Truth Architecture:**

```kotlin
// ❌ FORBIDDEN - Mixing Media3's texture with our own loaded texture
// This causes color mismatch!
class TransitionShaderProgram : BaseGlShaderProgram {
    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        // inputTexId = FROM image (loaded by Media3)
        // toTextureId = TO image (loaded by us via GLUtils.texImage2D)
        // PROBLEM: These have DIFFERENT colors for the same image!
        program.setSamplerTexIdUniform("uFromSampler", inputTexId, 0)  // Media3's texture
        program.setSamplerTexIdUniform("uToSampler", toTextureId, 1)   // Our texture
    }
}

// ✅ REQUIRED - Load BOTH textures ourselves, IGNORE Media3's inputTexId
class TransitionShaderProgram : BaseGlShaderProgram {
    private var fromTextureId: Int = -1  // WE load this
    private var toTextureId: Int = -1    // WE load this

    override fun configure(inputWidth: Int, inputHeight: Int): Size {
        // Load BOTH from same source (BitmapFactory + GLUtils.texImage2D)
        fromTextureId = createTextureFromBitmap(fromImageBitmap)
        toTextureId = createTextureFromBitmap(toImageBitmap)
        return Size(inputWidth, inputHeight)
    }

    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        // IGNORE inputTexId - use OUR textures for BOTH
        val fromTexId = if (fromTextureId != -1) fromTextureId else inputTexId
        val toTexId = if (toTextureId != -1) toTextureId else inputTexId

        program.setSamplerTexIdUniform("uFromSampler", fromTexId, 0)  // Our texture
        program.setSamplerTexIdUniform("uToSampler", toTexId, 1)      // Our texture
        // Now BOTH use identical loading path → identical colors!
    }
}
```

**Key Takeaway:**
When blending two images in a custom GlShaderProgram, NEVER mix Media3's internally-loaded texture with your own BitmapFactory-loaded texture. Either:
1. Use Media3 for both (if possible)
2. Load BOTH yourself using identical methods (recommended for transitions)

This ensures the color handling is consistent across both textures.

---

## Module Structure

```
app/src/main/java/co/alcheclub/video/maker/photo/music/
├── di/                    # Hilt modules
├── presentation/          # UI (Compose screens + ViewModels)
│   ├── home/
│   ├── picker/
│   ├── editor/
│   ├── preview/
│   ├── export/
│   └── projects/
├── domain/                # Business logic
│   ├── model/
│   ├── repository/
│   └── usecase/
├── data/                  # Data layer
│   ├── local/database/
│   ├── repository/
│   └── media/
├── media/                 # Media processing
│   ├── composition/
│   ├── effects/
│   ├── audio/
│   └── export/
└── MainActivity.kt
```

---

## Emergency Checklist for Video Maker

Before EVERY code change:

**Navigation 3 (this project uses Nav3):**
- [ ] `createSafeViewModelFactory` for all ViewModel creation
- [ ] `remember(route.projectId)` for Editor/Export/AssetPicker factories
- [ ] `viewModel(key = "screen_${projectId}")` for parameterized screens
- [ ] Safe cast in `backStackSaver()` restore

**Media:**
- [ ] Media3 Transformer for video export (NOT raw MediaCodec)
- [ ] CompositionPlayer for preview (NOT custom player)
- [ ] Photo Picker for media selection (NOT MediaStore with permissions)
- [ ] **MVP: ImageOnly** for picker (NOT ImageAndVideo)
- [ ] WorkManager for export (NOT viewModelScope)
- [ ] KSP2 for Room (NOT KAPT)
- [ ] Same Composition for preview and export
- [ ] Player resources released in DisposableEffect
- [ ] Asset URIs validated on project load
- [ ] Progress tracking via WorkManager setProgress()

---

## Library Decision: Custom vs Media3

### Evaluation Summary

| Approach | Pros | Cons | Effort |
|----------|------|------|--------|
| **Custom MediaCodec** | Full control, no dependencies | Very complex, device fragmentation, months of work | 6+ months |
| **Media3 Transformer** | Google-maintained, tested, optimized | Limited transition support (coming soon) | 2-4 weeks |
| **Hybrid: Media3 + Custom Effects** | Best of both worlds | Moderate complexity | 4-6 weeks |

### Decision: Hybrid Approach (Recommended)

Use **Media3 Transformer** as the core engine with custom thin wrappers:

1. **Media3 Transformer** → Core video composition & export
2. **Custom TransitionEffect** → Thin wrapper for GL Transitions shaders
3. **Custom AudioLooper** → Simple PCM looping logic (optional, Media3 handles basic cases)

### Why NOT Full Custom?

Building from scratch with raw MediaCodec requires:
- ~3000+ lines of OpenGL/EGL boilerplate
- ~2000+ lines of MediaCodec encoder/decoder management
- Handling 100+ device-specific codec quirks
- Memory management for frame buffers
- Audio-video sync logic
- Container format (MP4) muxing

Media3 Transformer handles all of this and is maintained by Google.

### What We CAN Customize

```kotlin
// ============================================
// CUSTOM TRANSITION EFFECT (Thin wrapper)
// ============================================
class GlTransitionEffect(
    private val shaderSource: String,
    private val durationMs: Long
) : GlEffect {
    override fun toGlShaderProgram(
        context: Context,
        useHdr: Boolean
    ): GlShaderProgram {
        return GlTransitionShaderProgram(context, shaderSource, durationMs, useHdr)
    }
}

// ============================================
// CUSTOM OVERLAY EFFECT (Static frame)
// ============================================
class FrameOverlayEffect(
    private val overlayUri: Uri,
    private val opacity: Float
) : GlEffect {
    // Renders PNG overlay on top of video frames
}

// ============================================
// AUDIO PROCESSOR (Volume control)
// ============================================
class VolumeAudioProcessor(
    private val volume: Float
) : BaseAudioProcessor() {
    // Adjusts PCM sample values
}
```

### Files to Create in `media/` Package

```
media/
├── composition/
│   ├── CompositionFactory.kt       # Builds Media3 Composition
│   └── CompositionPreviewManager.kt # Manages preview player
├── effects/
│   ├── GlTransitionEffect.kt       # Custom transition wrapper
│   ├── FrameOverlayEffect.kt       # Overlay frame effect
│   └── shaders/
│       └── transitions.kt          # GLSL shader sources
├── audio/
│   ├── VolumeAudioProcessor.kt     # Volume control
│   └── AudioLoopHelper.kt          # Looping logic
└── export/
    └── VideoExportWorker.kt        # WorkManager worker
```

---

## ACCDI Dependency Injection Pattern

### Module Structure (Following ac-remote-android)

```kotlin
// ============================================
// DATA MODULE
// ============================================
val dataModule = module {
    // Database
    single { ProjectDatabase.getInstance(androidContext()) }
    single { get<ProjectDatabase>().projectDao() }
    single { get<ProjectDatabase>().assetDao() }

    // Preferences
    single { PreferencesManager(androidContext()) }

    // Repositories
    single<ProjectRepository> { ProjectRepositoryImpl(get(), get()) }
    single<AssetRepository> { AssetRepositoryImpl(get()) }
}

// ============================================
// DOMAIN MODULE
// ============================================
val domainModule = module {
    // Use Cases (Factory scope - stateless)
    factory { CreateProjectUseCase(get()) }
    factory { GetProjectsUseCase(get()) }
    factory { AddAssetsUseCase(get()) }
    factory { UpdateProjectSettingsUseCase(get()) }
    factory { ExportVideoUseCase(get(), get()) }
    factory { DeleteProjectUseCase(get()) }
}

// ============================================
// MEDIA MODULE
// ============================================
val mediaModule = module {
    // Composition (Singleton - expensive to create)
    single { CompositionFactory(androidContext()) }
    single { TransitionLibrary() }
}

// ============================================
// PRESENTATION MODULE
// ============================================
val presentationModule = module {
    // ViewModels
    viewModel { HomeViewModel(get()) }
    viewModel { params -> AssetPickerViewModel(get(), params.get()) }
    viewModel { params -> EditorViewModel(params.get(), get(), get()) }
    viewModel { params -> PreviewViewModel(params.get(), get()) }
    viewModel { params -> ExportViewModel(params.get(), get()) }
}
```

### Application Setup

```kotlin
class VideoMakerApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // Initialize ACCDI
        ACCDI.init(this) {
            modules(
                dataModule,
                domainModule,
                mediaModule,
                presentationModule
            )
        }
    }
}
```

### ViewModel Injection in Compose

```kotlin
@Composable
fun EditorScreen(
    projectId: String,
    viewModel: EditorViewModel = ACCDI.getViewModel { parametersOf(projectId) }
) {
    // Screen content
}
```

---

## Version Information

| Component | Version | Notes |
|-----------|---------|-------|
| Kotlin | 2.1.20 | Latest stable |
| Compose BOM | 2025.10.01 | December 2025 release |
| Navigation 3 | 1.0.0-alpha03 | Developer-owned back stack |
| Media3 | 1.6.1 | Stable with Transformer |
| Room | 2.8.4 | KSP2 + Kotlin 2.0 support |
| WorkManager | 2.11.0 | Coroutines support |
| KSP | 2.1.20-2.0.1 | Matches Kotlin version |
| AGP | 8.13.2 | Latest stable |
| Target SDK | 36 | Android 15 |
| Min SDK | 28 | Android 9 (2018+) |
