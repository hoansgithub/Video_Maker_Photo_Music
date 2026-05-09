# Android Development Rules

## Tech Stack
- **Kotlin 1.9+** with Jetpack Compose
- **Min SDK**: 24 (Android 7.0)
- **Navigation**: Navigation 3 with NavDisplay (NOT NavHost)
- **State**: StateFlow (NOT LiveData)
- **Concurrency**: Coroutines + Flow (NOT RxJava)
- **DI**: Hilt

## Android CLI (Agent-Optimized Tooling)

```bash
android init                     # Initialize CLI
android update                   # Keep CLI up to date
android sdk install              # Lean SDK management
android create                   # New project with templates
android emulator                 # Create/manage virtual devices
android run                      # Build & deploy to device/emulator
android skills list --long       # List all available skills
android skills add --all         # Add all skills at once
android docs search 'query'      # Search latest docs
android docs fetch kb://android/topic/navigation/overview
```

| Task | Use |
|------|-----|
| SDK setup, project creation, device management | `android` CLI |
| Build, test, lint, custom tasks | `./gradlew` |
| Latest API docs lookup | `android docs` |
| Migration guidance (Nav3, Compose, AGP9) | `android skills` |

## Migration & Analysis Skills

Skills in `.claude/skills/` auto-trigger based on task context.

| Skill | When to Use |
|-------|-------------|
| `gradle-build` | Build, test, run Android apps with Gradle and ADB |
| `edge-to-edge` | Migrate to edge-to-edge display (SDK 35+), fix system bar/IME overlap |
| `agp9-upgrade` | Upgrade to AGP 9 (built-in Kotlin, new DSL, kapt→KSP) |
| `camera1-to-camerax` | Migrate Camera1/Camera2 to CameraX |
| `xml-to-compose` | Convert XML layouts to Jetpack Compose (10-step methodology) |
| `r8-analyzer` | Analyze R8/ProGuard keep rules for redundancies (read-only) |
| `play-billing-upgrade` | Upgrade Play Billing Library to latest version |
| `nav3-recipes` | Navigation 3 code recipes (deep links, scenes, multi-backstack, etc.) |

## Build Environment (CRITICAL)

```bash
# ALWAYS set before building
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew build
```

| OS | Android Studio JDK Path |
|----|------------------------|
| macOS | `/Applications/Android Studio.app/Contents/jbr/Contents/Home` |
| Windows | `C:\Program Files\Android\Android Studio\jbr` |
| Linux | `/opt/android-studio/jbr` |

## Agent Workflow

```
NEW FEATURE
  1. ultrathink-planner    → Design approach
  2. kotlin-architect      → Architecture decisions
  3. kotlin-developer      → Implementation
  4. navigation-guardian   → Event-based navigation check
  5. kotlin-tester         → Unit tests

BUG FIX
  1. kotlin-debugger       → Root cause
  2. kotlin-developer      → Fix
  3. navigation-guardian   → Verify navigation patterns

CODE REVIEW
  1. navigation-guardian   → Navigation anti-pattern scan
  2. kotlin-reviewer       → Quality check
```

## Critical Rules

### 1. Navigation 3 (REQUIRED)

Routes: `@Serializable object HomeRoute : NavKey` / `@Serializable data class ProfileRoute(val userId: String) : NavKey`

Setup: `val backStack = rememberNavBackStack(HomeRoute)` → `NavDisplay(backStack, entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator(), rememberViewModelStoreNavEntryDecorator()), entryProvider = entryProvider { entry<Route> { key -> ... } }, onBack = { backStack.pop() })`

- ❌ `NavHost(navController, ...) { }` / `navController.navigate(...)` → ✅ `NavDisplay` + `backStack.add(route)`

**ViewModel Scoping**: VMs cached per NavEntry key via `rememberViewModelStoreNavEntryDecorator()`.
- Created when key added to back stack, retained while on stack, cleared on pop
- Do NOT scope screen ViewModels to Activity — use NavEntry scoping

### 2. Channel for One-Time Events (GOLD STANDARD)

- ❌ `LaunchedEffect(uiState) { if (uiState is Success) navigate() }` — breaks on back/rotation!
- ✅ `Channel<Event>(Channel.BUFFERED)` + `.receiveAsFlow()` in ViewModel, `LaunchedEffect(Unit) { viewModel.event.collect { } }` in Composable

### 3. collectAsStateWithLifecycle

- ❌ `viewModel.uiState.collectAsState()` → ✅ `viewModel.uiState.collectAsStateWithLifecycle()`

### 4. viewModelScope for Coroutines

- ❌ `GlobalScope.launch { }` / custom `CoroutineScope` → ✅ `viewModelScope.launch { }`

### 5. Sealed Class State Machines

- ❌ `var isLoading = false; var hasError = false` → ✅ `sealed class UiState { Loading, Success(data), Error(msg) }`

### 6. NO CRASHES

- ❌ `nullable!!` → ✅ `nullable ?: return`

### 7. Activity Navigation

- ❌ Don't embed Activities as composables
- ✅ Via Channel event: `startActivity(intent)` + `finish()` for forward nav

### 8. Recomposition Optimization

- ✅ `@Immutable` + `ImmutableList` for stable data classes
- ✅ `viewModel::onItemClick` (method reference) for stable lambdas
- ✅ `items(users, key = { it.id })` for LazyColumn keys

### 9. 3rd-Party SDK Manifest Safety (CRITICAL)

- ❌ `<activity android:name="com.thirdparty.SomeActivity" />` (may not exist in APK)
- ✅ Only declare what you use; analytics-only SDKs: metadata only, no activity declarations
- Every `<activity>` in AndroidManifest MUST have its class in dependencies

### 10. Compose Side-Effect Safety (CRITICAL)

- ❌ `LaunchedEffect(trigger) { focusRequester.requestFocus() }` — can crash if view changes
- ✅ `try { delay(100); focusRequester.requestFocus() } catch (e: Exception) { }` — delay + try-catch for view interactions

### 11. Repository Query Safety (CRITICAL)

- ❌ `repository.getById(id)` without status filter — may return soft-deleted/draft records
- ✅ ALL queries MUST include status/visibility filters; direct ID lookups and related entity queries too

### 12. WeakReference MUST NOT Break Critical Flow (CRITICAL)

- ❌ `WeakReference(action).get()?.invoke()` — may be null, APP FREEZES
- ✅ `action()` — action callbacks MUST be strong references
- WeakReference OK only for optional callbacks (onShown), UI elements

### 13. Database Query Safety (CRITICAL)
- NEVER fetch all records — assume billions of rows
- ALL queries MUST have LIMIT / pagination
- ALL filtering in the query (WHERE clauses), NOT client-side
- ALL sorting in the query (ORDER BY), NOT client-side
- Applies to: Room, Supabase, Firebase, SQL, MongoDB, etc.

### 14. ANR Prevention (CRITICAL)

ANR triggers when main thread blocked >5s. Google Play penalizes ≥0.47% ANR rate.

- ❌ I/O without dispatcher → ✅ `withContext(Dispatchers.IO) { }`, CPU work on `Dispatchers.Default`
- ❌ `runBlocking { }` / `Thread.sleep()` on main → ✅ coroutines + `delay()`
- ❌ `prefs.edit().commit()` → ✅ `.apply()`
- ❌ Heavy `onReceive()` → ✅ `goAsync()` + IO coroutine + `pendingResult.finish()`
- ❌ Slow `startForeground()` → ✅ Call within 5s, heavy work in IO coroutine
- ❌ `synchronized` blocking main → ✅ `Mutex`
- Repository methods: `suspend` + `withContext(Dispatchers.IO)` internally
- Application.onCreate(): defer heavy init to background coroutine

## Context Optimization
- **Exploration**: haiku | **Implementation**: sonnet | **Architecture**: opus
- Use subagents to isolate verbose output (Logcat, debugging)

## Git Workflow

```
⚠️ NEVER commit automatically. Always wait for the user's explicit instruction before running git commit.
```

## Verification Loop

```
1. Build: ./gradlew build (or android run)
2. Test: ./gradlew test
3. Navigation: Run navigation-guardian agent
4. Docs: android docs search '<relevant API>'
```
