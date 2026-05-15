---
name: nav3-recipes
description: Navigation 3 code recipes for Jetpack Compose — deep links, scenes (dialog, bottom sheet, list-detail), multi-backstack, animations, conditional navigation, modularized nav (Hilt/Koin), ViewModel arguments, returning results.
allowed-tools: Read, Grep, Glob, Edit, Write, Bash(./gradlew:*)
hooks:
  pre_tool_use:
    - tool: Bash
      script: |
        # Ensure JAVA_HOME is set
        if [ -z "$JAVA_HOME" ]; then
          export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
        fi
---

<!-- Adapted from github.com/android/skills (2026-05) -->

# Navigation 3 Recipes

15+ code recipes for common Navigation 3 patterns. Each recipe includes when to use, code example, and notes.

## Dependencies

```kotlin
implementation("androidx.navigation3:navigation3-runtime:1.0.0")
implementation("androidx.navigation3:navigation3-ui:1.0.0")
```

---

## Recipe 1: Basic Setup

**When:** Starting a new Nav3 project from scratch.

```kotlin
@Serializable object HomeRoute : NavKey
@Serializable data class DetailRoute(val id: String) : NavKey

@Composable
fun App() {
    val backStack = rememberNavBackStack(HomeRoute)
    NavDisplay(
        backStack = backStack,
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator()
        ),
        entryProvider = entryProvider {
            entry<HomeRoute> {
                HomeScreen(onNavigate = { id -> backStack.add(DetailRoute(id)) })
            }
            entry<DetailRoute> { key ->
                DetailScreen(id = key.id, onBack = { backStack.pop() })
            }
        },
        onBack = { backStack.pop() }
    )
}
```

---

## Recipe 2: Saveable Back Stack

**When:** Persisting navigation state across configuration changes and process death.

```kotlin
val backStack = rememberSaveable(
    saver = NavBackStack.Saver(HomeRoute)
) { NavBackStack(HomeRoute) }
```

**Note:** `rememberNavBackStack` already handles this. Use `rememberSaveable` with `NavBackStack.Saver` for custom save logic.

---

## Recipe 3: Entry Provider DSL

**When:** Organizing entry registrations with the DSL builder.

```kotlin
entryProvider = entryProvider {
    entry<HomeRoute> { HomeScreen() }
    entry<DetailRoute> { key -> DetailScreen(key.id) }
    entry<SettingsRoute> { SettingsScreen() }
}
```

**Note:** Each `entry<Route>` block receives the route key as its lambda parameter.

---

## Recipe 4: Bottom Nav + Multi-Backstack

**When:** Tab-based UI where each tab has its own navigation history.

```kotlin
@Serializable sealed class Tab : NavKey {
    @Serializable object Home : Tab()
    @Serializable object Search : Tab()
    @Serializable object Profile : Tab()
}

@Composable
fun MainScreen() {
    var selectedTab by rememberSaveable { mutableStateOf<Tab>(Tab.Home) }
    val homeBackStack = rememberNavBackStack(Tab.Home)
    val searchBackStack = rememberNavBackStack(Tab.Search)
    val profileBackStack = rememberNavBackStack(Tab.Profile)

    val currentBackStack = when (selectedTab) {
        is Tab.Home -> homeBackStack
        is Tab.Search -> searchBackStack
        is Tab.Profile -> profileBackStack
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab is Tab.Home,
                    onClick = { selectedTab = Tab.Home },
                    icon = { Icon(Icons.Default.Home, null) },
                    label = { Text("Home") }
                )
                // ... other tabs
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            NavDisplay(
                backStack = currentBackStack,
                entryDecorators = listOf(
                    rememberSaveableStateHolderNavEntryDecorator(),
                    rememberViewModelStoreNavEntryDecorator()
                ),
                entryProvider = entryProvider {
                    // Register entries for all tabs
                },
                onBack = { currentBackStack.pop() }
            )
        }
    }
}
```

---

## Recipe 5: Deep Links (Basic)

**When:** Parsing a deep link URL from an Android Intent.

```kotlin
// In Activity
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val deepLinkUri = intent?.data

    setContent {
        val backStack = rememberNavBackStack(HomeRoute)

        LaunchedEffect(deepLinkUri) {
            deepLinkUri?.let { uri ->
                val route = parseDeepLink(uri) // Your URL → NavKey mapping
                route?.let { backStack.add(it) }
            }
        }

        NavDisplay(backStack = backStack, /* ... */)
    }
}
```

---

## Recipe 6: Deep Links (Advanced — Synthetic Back Stack)

**When:** Deep link should show proper "Up" navigation (e.g., Detail → Home on back press).

```kotlin
LaunchedEffect(deepLinkUri) {
    deepLinkUri?.let { uri ->
        when {
            uri.pathSegments.firstOrNull() == "detail" -> {
                val id = uri.pathSegments.getOrNull(1) ?: return@let
                // Build synthetic back stack: Home → Detail
                backStack.add(HomeRoute)
                backStack.add(DetailRoute(id))
            }
        }
    }
}
```

---

## Recipe 7: Dialog Scene

**When:** Showing a destination as a dialog overlay instead of a full-screen replacement.

```kotlin
@Serializable data class ConfirmRoute(val message: String) : NavKey

entry<ConfirmRoute> { key ->
    NavDisplay.Scene(isDialog = true) {
        AlertDialog(
            onDismissRequest = { backStack.pop() },
            title = { Text("Confirm") },
            text = { Text(key.message) },
            confirmButton = {
                TextButton(onClick = { backStack.pop() }) { Text("OK") }
            }
        )
    }
}
```

**Note:** `NavDisplay.Scene(isDialog = true)` renders the entry as an overlay, keeping the previous entry visible behind it.

---

## Recipe 8: Bottom Sheet Scene

**When:** Showing a destination as a modal bottom sheet.

```kotlin
@Serializable data class OptionsRoute(val itemId: String) : NavKey

entry<OptionsRoute> { key ->
    NavDisplay.Scene(isDialog = true) {
        ModalBottomSheet(onDismissRequest = { backStack.pop() }) {
            OptionsContent(
                itemId = key.itemId,
                onDismiss = { backStack.pop() }
            )
        }
    }
}
```

---

## Recipe 9: List-Detail Scene

**When:** Adaptive layout that shows list and detail side-by-side on large screens, stacked on small screens.

```kotlin
NavDisplay(
    backStack = backStack,
    entryProvider = entryProvider {
        entry<ListRoute> {
            NavDisplay.Scene(sceneStrategy = ListDetailSceneStrategy) {
                ListScreen(onItemClick = { id -> backStack.add(DetailRoute(id)) })
            }
        }
        entry<DetailRoute> { key ->
            NavDisplay.Scene(sceneStrategy = ListDetailSceneStrategy) {
                DetailScreen(id = key.id)
            }
        }
    }
)
```

---

## Recipe 10: Two-Pane Scene

**When:** Side-by-side layout for tablets/foldables (e.g., email list + preview).

```kotlin
NavDisplay.Scene(sceneStrategy = TwoPaneSceneStrategy) {
    // Content adapts to available width
}
```

---

## Recipe 11: Animations

**When:** Custom enter/exit transitions between destinations.

```kotlin
// Override default animations for all destinations
NavDisplay(
    transitionSpec = { fadeIn() togetherWith fadeOut() },
    popTransitionSpec = { fadeIn() togetherWith fadeOut() },
    /* ... */
)

// Override for a single destination
entry<DetailRoute> { key ->
    NavDisplay.Transition(
        enter = slideInHorizontally { it },
        exit = slideOutHorizontally { -it }
    ) {
        DetailScreen(key.id)
    }
}
```

---

## Recipe 12: Multiple Backstacks

**When:** Top-level routes with independent navigation histories (tabs, rail).

```kotlin
val backstacks = remember {
    mapOf(
        "home" to NavBackStack(HomeRoute),
        "search" to NavBackStack(SearchRoute),
        "profile" to NavBackStack(ProfileRoute)
    )
}
var activeTab by rememberSaveable { mutableStateOf("home") }

NavDisplay(
    backStack = backstacks[activeTab]!!,
    /* ... */
)
```

**Note:** Each backstack retains its state independently. Switching tabs resumes where the user left off.

---

## Recipe 13: Conditional Navigation

**When:** Switching navigation flows based on auth state, onboarding, etc.

```kotlin
@Composable
fun AppNavigation(isLoggedIn: Boolean) {
    val startRoute: NavKey = if (isLoggedIn) HomeRoute else LoginRoute
    val backStack = rememberNavBackStack(startRoute)

    NavDisplay(
        backStack = backStack,
        entryProvider = entryProvider {
            entry<LoginRoute> {
                LoginScreen(onLoginSuccess = {
                    backStack.clear()
                    backStack.add(HomeRoute)
                })
            }
            entry<HomeRoute> { HomeScreen() }
        },
        onBack = { backStack.pop() }
    )
}
```

---

## Recipe 14: Modularized Navigation (Hilt)

**When:** Decoupling navigation code across feature modules with Hilt DI.

```kotlin
// In :feature-home module
@Module
@InstallIn(SingletonComponent::class)
object HomeNavModule {
    @Provides
    @IntoSet
    fun provideHomeEntries(): NavEntryProvider = NavEntryProvider {
        entry<HomeRoute> {
            val viewModel: HomeViewModel = hiltViewModel()
            HomeScreen(viewModel)
        }
    }
}

// In :app module — collect all providers
@Composable
fun AppNavigation(entryProviders: Set<NavEntryProvider>) {
    val backStack = rememberNavBackStack(HomeRoute)
    NavDisplay(
        backStack = backStack,
        entryProvider = entryProvider {
            entryProviders.forEach { provider -> provider.register(this) }
        },
        onBack = { backStack.pop() }
    )
}
```

---

## Recipe 15: Modularized Navigation (Koin)

**When:** Same as Recipe 14 but with Koin DI.

```kotlin
// In :feature-home module
val homeNavModule = module {
    factory<NavEntryProvider> {
        NavEntryProvider {
            entry<HomeRoute> {
                val viewModel: HomeViewModel = koinViewModel()
                HomeScreen(viewModel)
            }
        }
    }
}
```

---

## Recipe 16: ViewModel Argument Passing

**When:** Passing navigation arguments to a ViewModel.

```kotlin
@Serializable data class ProfileRoute(val userId: String) : NavKey

entry<ProfileRoute> { key ->
    val viewModel: ProfileViewModel = viewModel(
        factory = ProfileViewModel.Factory(key.userId)
    )
    ProfileScreen(viewModel)
}

// With Hilt (preferred)
@HiltViewModel
class ProfileViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    val userId = savedStateHandle.toRoute<ProfileRoute>().userId
}
```

---

## Recipe 17: Returning Results (Event-Based)

**When:** A destination needs to return a result to its caller (e.g., picker → parent).

```kotlin
// Parent screen
val resultChannel = remember { Channel<PickerResult>(Channel.BUFFERED) }

LaunchedEffect(Unit) {
    resultChannel.receiveAsFlow().collect { result ->
        // Handle result
    }
}

entry<PickerRoute> { key ->
    PickerScreen(onResult = { result ->
        resultChannel.trySend(result)
        backStack.pop()
    })
}
```

---

## Recipe 18: Returning Results (State-Based)

**When:** Result needs to survive configuration changes via `CompositionLocal`.

```kotlin
val LocalPickerResult = compositionLocalOf<PickerResult?> { null }

var pickerResult by rememberSaveable { mutableStateOf<PickerResult?>(null) }

CompositionLocalProvider(LocalPickerResult provides pickerResult) {
    NavDisplay(
        backStack = backStack,
        entryProvider = entryProvider {
            entry<PickerRoute> {
                PickerScreen(onResult = { result ->
                    pickerResult = result
                    backStack.pop()
                })
            }
        }
    )
}
```

---

## Quick Reference

| Pattern | Recipe |
|---------|--------|
| New Nav3 project | Recipe 1 |
| Persist nav state | Recipe 2 |
| Tabs with separate history | Recipe 4, 12 |
| Handle deep links | Recipe 5, 6 |
| Dialog / Bottom Sheet | Recipe 7, 8 |
| Adaptive layout | Recipe 9, 10 |
| Custom transitions | Recipe 11 |
| Auth-gated navigation | Recipe 13 |
| Multi-module navigation | Recipe 14, 15 |
| Pass args to ViewModel | Recipe 16 |
| Return results | Recipe 17, 18 |
