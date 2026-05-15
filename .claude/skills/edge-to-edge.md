---
name: edge-to-edge
description: Migrate a Jetpack Compose app to edge-to-edge display (SDK 35+). Use when UI is obscured by system bars, navigation bar, status bar, or IME keyboard.
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

# Edge-to-Edge Migration Skill

Migrate a Jetpack Compose app to edge-to-edge display with proper inset handling.

## Prerequisites

- Project **MUST** use Jetpack Compose.
- Project **MUST** target SDK 35 or later. If lower, increase `targetSdk` to 35.

## Step 1: Audit Activities

1. Locate all Activity classes and detect which have existing edge-to-edge support.
2. For each Activity, scan for lists, FABs, `TextField`, `OutlinedTextField`, or `BasicTextField`.
3. If text fields are found, verify IME handling (Step 4).

## Step 2: Enable Edge-to-Edge

1. Add `enableEdgeToEdge()` before `setContent` in `onCreate` for each Activity:

```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    enableEdgeToEdge()
    super.onCreate(savedInstanceState)
    setContent { /* ... */ }
}
```

2. Add `android:windowSoftInputMode="adjustResize"` in `AndroidManifest.xml` for Activities using a soft keyboard. Do NOT use `SOFT_INPUT_ADJUST_RESIZE` (deprecated).

## Step 3: Apply Insets

Choose **only one method** per component to avoid double padding:

### Approach 1 (PREFERRED): Scaffold PaddingValues

```kotlin
Scaffold { innerPadding ->
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .consumeWindowInsets(innerPadding),
        contentPadding = innerPadding
    ) { /* Content */ }
}
```

### Approach 2 (PREFERRED): Material 3 Auto-Insets

These Material 3 components manage insets automatically:
- `TopAppBar`, `SmallTopAppBar`, `CenterAlignedTopAppBar`, `MediumTopAppBar`, `LargeTopAppBar`
- `BottomAppBar`, `NavigationBar`, `NavigationRail`
- `ModalBottomSheet`, `ModalDrawerSheet`, `DismissibleDrawerSheet`, `PermanentDrawerSheet`

For Material 2, pass insets directly to the component:
```kotlin
TopAppBar(windowInsets = AppBarDefaults.topAppBarWindowInsets)
```
Do NOT apply padding to the parent container — it prevents the App Bar background from drawing into the system bar area.

### Approach 3: safeDrawingPadding

For components outside a Scaffold:

```kotlin
Box(
    modifier = Modifier
        .fillMaxSize()
        .safeDrawingPadding()
) {
    Button(
        onClick = {},
        modifier = Modifier.align(Alignment.BottomCenter)
    ) { Text("Login") }
}
```

### Approach 4: Manual WindowInsets

For deeply nested components, use `WindowInsetsRulers`:

```kotlin
Modifier.fitInside(WindowInsetsRulers.SafeDrawing.current)
```

Or inset size modifiers for elements matching system bar dimensions:

```kotlin
Modifier.windowInsetsTopHeight(WindowInsets.systemBars)
```

### Adaptive Scaffolds

`NavigationSuiteScaffold` and `ListDetailPaneScaffold` do NOT propagate `PaddingValues` to inner content. Apply insets to **individual** screens. Do NOT apply `safeDrawingPadding` to the parent scaffold.

## Step 4: IME Handling

### With Scaffold

```kotlin
// RIGHT: contentWindowInsets contains IME insets
Scaffold(contentWindowInsets = WindowInsets.safeDrawing) { innerPadding ->
    Column(
        modifier = Modifier
            .padding(innerPadding)
            .consumeWindowInsets(innerPadding)
            .verticalScroll(rememberScrollState())
    ) { /* Content */ }
}

// RIGHT: fitInside works regardless of contentWindowInsets
Scaffold { innerPadding ->
    Column(
        modifier = Modifier
            .padding(innerPadding)
            .consumeWindowInsets(innerPadding)
            .fitInside(WindowInsetsRulers.Ime.current)
            .verticalScroll(rememberScrollState())
    ) { /* Content */ }
}

// RIGHT: Default contentWindowInsets + imePadding
Scaffold { innerPadding ->
    Column(
        modifier = Modifier
            .padding(innerPadding)
            .consumeWindowInsets(innerPadding)
            .imePadding()
            .verticalScroll(rememberScrollState())
    ) { /* Content */ }
}

// WRONG: Double IME padding (contentWindowInsets has IME + imePadding)
Scaffold(contentWindowInsets = WindowInsets.safeDrawing) { innerPadding ->
    Column(
        modifier = Modifier
            .padding(innerPadding)
            .imePadding() // ← Double padding!
    ) { /* Content */ }
}
```

### Without Scaffold

```kotlin
// RIGHT: safeDrawingPadding consumes insets
Box(modifier = Modifier.safeDrawingPadding()) {
    Column(modifier = Modifier.imePadding()) { /* Content */ }
}

// WRONG: asPaddingValues does NOT consume — double padding
Box(modifier = Modifier.padding(WindowInsets.safeDrawing.asPaddingValues())) {
    Column(modifier = Modifier.imePadding()) { /* Content */ }
}
```

## Step 5: Navigation Bar Contrast

When using `enableEdgeToEdge`, set system bar icon colors to the inverse of the device theme:

```kotlin
val darkTheme = isSystemInDarkTheme()
val view = LocalView.current
if (!view.isInEditMode) {
    SideEffect {
        val window = (view.context as Activity).window
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !darkTheme
            isAppearanceLightNavigationBars = !darkTheme
        }
    }
}
```

## Step 6: Lists

Apply `contentPadding` to the list itself, NOT padding on the parent:

```kotlin
// RIGHT
LazyColumn(
    contentPadding = innerPadding,
    modifier = Modifier.consumeWindowInsets(innerPadding)
) { /* items */ }

// WRONG — clips item content at the edges
LazyColumn(modifier = Modifier.padding(innerPadding)) { /* items */ }
```

## Step 7: Dialogs

For dialogs, set `decorFitsSystemWindows = false` on the dialog window:

```kotlin
Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(decorFitsSystemWindows = false)
) {
    // Dialog content
}
```

## Verification Checklist

- [ ] `enableEdgeToEdge()` called before `setContent` in all Activities
- [ ] `android:windowSoftInputMode="adjustResize"` for keyboard Activities
- [ ] Only ONE inset method per component (no double padding)
- [ ] Text fields not hidden by IME keyboard
- [ ] FABs and bottom buttons not obscured by navigation bar
- [ ] Lists use `contentPadding`, not parent `Modifier.padding`
- [ ] System bar icons legible in both light and dark themes
- [ ] Build succeeds: `./gradlew assembleDebug`
