# Android Development Rules

## Tech Stack

- **Language**: Kotlin 2.0+ | **UI**: Jetpack Compose (BOM 2025.10.01)
- **Min SDK**: 28 | **Target SDK**: 36 | **Architecture**: Clean Architecture
- **Navigation**: Navigation 3 (1.0.0-alpha03) - developer-owned back stack
- **Concurrency**: Coroutines + Flow | **DI**: ACCDI | **Build**: KSP2 (not KAPT)

**Navigation 3 Key Concepts:**
- You own the back stack (`SnapshotStateList<AppRoute>`)
- Routes implement `NavKey` | `NavDisplay` replaces `NavHost`
- Reference: https://developer.android.com/guide/navigation/navigation-3

---

## Project: Video Maker Photo Music

**MVP Scope**: Images Only (photos → MP4 slideshow with transitions and music)

### Core Libraries

| Component | Library | Version |
|-----------|---------|---------|
| Video Editing | Media3 Transformer | 1.9.0 |
| Preview | Media3 CompositionPlayer | 1.9.0 |
| Media Picker | Android Photo Picker | System API |
| Database | Room + KSP2 | 2.8.4 |
| Background Work | WorkManager | 2.11.0 |

### Settings Model

| Setting | Options | Description |
|---------|---------|-------------|
| **Transition Set** | Classic, Geometric, Cinematic, Creative, Minimal, Dynamic | 20+ transition animations |
| **Transition Duration** | 2-12 seconds | How long each image is shown |
| **Overlay Frame** | None, frame1, frame2, ... | Decorative frame overlay |
| **Background Music** | None, track1, track2, or custom | Audio track |
| **Audio Volume** | 0-100% | Music volume level |
| **Aspect Ratio** | 16:9, 9:16, 1:1, 4:3 | Output video dimensions |

**Image Rendering**: Blurred background fill (scale-aspect-fill + blur) + sharp foreground (scale-aspect-fit) + optional overlay frame

---

## Critical Rules

### 1. Navigation Events - Channel Pattern (Google Official)

**Source**: [Now in Android](https://github.com/android/nowinandroid), [Architecture Samples](https://github.com/android/architecture-samples)

```kotlin
// ❌ FORBIDDEN - State-based navigation
LaunchedEffect(uiState) { if (uiState is Success) navigate() }

// ❌ FORBIDDEN - StateFlow for events (causes replay on config change)
private val _navigationEvent = MutableStateFlow<Event?>(null)

// ✅ REQUIRED - Channel for one-time events (Google pattern)
private val _navigationEvent = Channel<NavigationEvent>()
val navigationEvent = _navigationEvent.receiveAsFlow()

fun navigateToNext() {
    viewModelScope.launch {
        _navigationEvent.send(NavigationEvent.GoToNext)
    }
}
// No onNavigationHandled() needed - Channel auto-consumes!

// In Composable
LaunchedEffect(Unit) {  // Key = Unit (not event!)
    viewModel.navigationEvent.collect { event ->
        when (event) {
            is NavigationEvent.GoToNext -> navigateNext()
        }
        // Auto-consumed - no manual cleanup
    }
}
```

**Why Channel over StateFlow?**
- StateFlow = state (replays last value) → ❌ causes duplicate navigation
- Channel = events (consumed once) → ✅ correct for one-time actions

### 2. Navigation 3 Architecture

| Aspect | Navigation 2.x | Navigation 3 |
|--------|---------------|--------------|
| Back stack | Library-managed | Developer-owned `SnapshotStateList` |
| Navigate | `navController.navigate()` | `navigator.navigate()` (list.add) |
| Go back | `navController.popBackStack()` | `navigator.goBack()` (list.remove) |
| Host | `NavHost` | `NavDisplay` |
| Destinations | `composable<T>` | `entry<T>` in entryProvider |

**Key Patterns:**
```kotlin
// Safe saver - prevents crashes on corrupted state
private fun backStackSaver(): Saver<SnapshotStateList<AppRoute>, Any> {
    return listSaver(
        save = { it.toList() },
        restore = { saved ->
            val list = (saved as? List<*>)?.filterIsInstance<AppRoute>().orEmpty()
            mutableStateListOf<AppRoute>().apply { addAll(list) }
        }
    )
}

// Parameterized routes - prevent wrong VM reuse
val factory = remember(route.projectId) { ACCDI.get<EditorViewModelFactory>() }
val viewModel: EditorViewModel = viewModel(
    key = "editor_${route.projectId}",
    factory = createSafeViewModelFactory { factory.create(route.projectId) }
)

// Type-safe factory (no unchecked casts)
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
                throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
            }
        }
    }
}
```

### 3. Essential Android Rules

```kotlin
// ❌ FORBIDDEN
val uiState by viewModel.uiState.collectAsState()
GlobalScope.launch { }
val value = nullable!!
startActivity(intent)  // Without finish()
composable<AppDestination> { FeatureScreen() }  // Activity as composable

// ✅ REQUIRED
val uiState by viewModel.uiState.collectAsStateWithLifecycle()
viewModelScope.launch { }
val value = nullable ?: defaultValue
startActivity(intent); finish()  // Always finish() for forward nav
// Launch Activity directly for screens with dedicated Activities
```

### 4. Sealed Class State Machines

```kotlin
// ❌ FORBIDDEN - Multiple boolean flags
var isLoading = false; var hasError = false

// ✅ REQUIRED - Single state source
sealed class FeatureUiState {
    data object Loading : FeatureUiState()
    data class Success(val data: Data) : FeatureUiState()
    data class Error(val message: String) : FeatureUiState()
}
```

### 5. Database Query Safety (CRITICAL)

- **NEVER** fetch all records (assume billions of rows)
- **ALL** queries MUST have LIMIT / pagination
- **ALL** filtering in query (WHERE), NOT client-side
- **ALL** sorting in query (ORDER BY), NOT client-side
- Applies to: Room, Supabase, Firebase, SQL, MongoDB, etc.

```kotlin
// ❌ FORBIDDEN
@Query("SELECT * FROM users")
suspend fun getAllUsers(): List<User>

// ✅ REQUIRED
@Query("SELECT * FROM users WHERE is_active = 1 ORDER BY name LIMIT :limit OFFSET :offset")
suspend fun getActiveUsers(limit: Int, offset: Int): List<User>
```

---

## Video Maker Specific Rules

### Media3 Patterns

```kotlin
// ✅ Media3 Transformer for export
val transformer = Transformer.Builder(context)
    .addListener(transformerListener)
    .build()
transformer.start(composition, outputPath)

// ✅ Photo Picker (no permissions)
val pickMedia = rememberLauncherForActivityResult(
    ActivityResultContracts.PickMultipleVisualMedia()
) { uris -> /* handle */ }
pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))

// ✅ WorkManager for background export
@HiltWorker
class VideoExportWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result { /* survives process death */ }
}

// ✅ Player lifecycle
DisposableEffect(composition) {
    val player = CompositionPlayer.Builder(context)
        .build()
        .apply { setComposition(composition) }
    onDispose { player.release() }
}
```

### Critical Media Issues

**1. Transition Texture Color Consistency**
- **Problem**: Mixing Media3's texture with BitmapFactory texture causes color mismatch
- **Solution**: Load BOTH textures using same method (BitmapFactory + GLUtils.texImage2D)
- See docs/transition-texture-color-fix.md for details

**2. MediaCodec Resource Exhaustion (Error 4006)**
- **Problem**: Async release in scrolling lists → resource buildup
- **Solution**:
  - Sync release: `player.release()` (NOT `releaseAsync()`)
  - Debounce: `delay(150)` before `prepare()`
  - Error 4006: Longer retry delays + `System.gc()`
- **When**: LazyColumn/LazyRow with video players
- See docs/mediacodec-exhaustion-fix.md for details

---

## Emergency Checklist

**Navigation 3:**
- [ ] Routes implement `NavKey`
- [ ] `SnapshotStateList` for back stack
- [ ] `rememberSaveable` with safe saver (filterIsInstance)
- [ ] `remember(route.param)` for parameterized factories
- [ ] `viewModel(key = "...")` for parameterized ViewModels
- [ ] `createSafeViewModelFactory` (no unchecked cast)
- [ ] Navigator only in @Composable (NOT in ViewModel)

**ViewModel Navigation:**
- [ ] `StateFlow<Event?>` (NOT state-based)
- [ ] `LaunchedEffect(navigationEvent)` observation
- [ ] `onNavigationHandled()` callback

**General:**
- [ ] `collectAsStateWithLifecycle()`
- [ ] `viewModelScope` for coroutines
- [ ] `finish()` after `startActivity()` for forward nav
- [ ] Sealed class for UI state
- [ ] No `!!` force unwrap
- [ ] No Activities as composables

**Media:**
- [ ] Media3 Transformer (NOT raw MediaCodec)
- [ ] Photo Picker (NOT MediaStore permissions)
- [ ] WorkManager for export (NOT viewModelScope)
- [ ] Player released in DisposableEffect
- [ ] Scrolling lists: Sync release + debounce

**Database:**
- [ ] Every query has LIMIT
- [ ] Filtering in query (WHERE)
- [ ] Sorting in query (ORDER BY)

---

## Common Gotchas

| Issue | Cause | Fix |
|-------|-------|-----|
| Navigation breaks on back | `LaunchedEffect(uiState)` | Use StateFlow events |
| State not updating | `collectAsState()` | Use `collectAsStateWithLifecycle()` |
| onBackPressed not called | Activity as composable | Launch Activity directly |
| Memory leaks | GlobalScope | Use `viewModelScope` |
| Error 4006 (scrolling) | Async player release | Sync release + debounce |

---

## Naming Conventions

| Type | Convention | Example |
|------|------------|---------|
| Activity | `*Activity` | `ProfileActivity` |
| ViewModel | `*ViewModel` | `ProfileViewModel` |
| UI State | `*UiState` | `ProfileUiState` |
| Navigation Event | `*NavigationEvent` | `ProfileNavigationEvent` |
| Repository | No suffix / `*Impl` | `UserRepository` / `UserRepositoryImpl` |
| Use Case | `*UseCase` | `FetchUserUseCase` |

---

## Version Information

| Component | Version | Notes |
|-----------|---------|-------|
| Kotlin | 2.1.0 | Stable |
| Compose BOM | 2025.10.01 | December 2025 |
| Navigation 3 | 1.0.0-alpha03 | Developer-owned back stack |
| Media3 | 1.6.1 | Transformer stable |
| Room | 2.8.4 | KSP2 support |
| AGP | 8.13.2 | Pangle SDK compatible |
| Target SDK | 36 | Android 15 |
| Min SDK | 28 | Android 9 (2018+) |

---

## Git Workflow

⚠️ **NEVER commit automatically. Always wait for explicit instruction.**

Commit format: `<type>: <description>`
- `feat:` new feature | `fix:` bug fix | `refactor:` code restructure | `ui:` UI/UX improvements

---

## Documentation Workflow

⚠️ **NEVER write summary files, reports, or documentation automatically.**

- **FORBIDDEN**: Creating summary.md, refactoring_summary.md, report files, or any documentation without explicit user request
- **REQUIRED**: Only create documentation when user explicitly asks "write a summary" or "create documentation"
- **EXCEPTION**: Code comments and inline documentation are always allowed
