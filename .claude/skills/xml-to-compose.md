---
name: xml-to-compose
description: Migrate an Android XML View layout to Jetpack Compose using a structured 10-step methodology. Use when converting legacy XML layouts to modern Compose UI.
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

# XML to Jetpack Compose Migration Skill

Systematically convert a legacy XML layout into Jetpack Compose UI while maintaining visual parity. This skill migrates UI only (XML → Compose).

## 10-Step Migration Process

### Step 1: Identify XML Candidate

If the user specified a target layout, skip to Step 2. Otherwise, select the best candidate:
- **Prefer leaf screens** (no child fragments) — simplest to migrate
- **Prefer screens with few custom views** — reduces complexity
- **Avoid screens with deep fragment nesting** — migrate fragments last

### Step 2: Analyze Layout Structure

Audit the target XML layout:
1. Read the XML file and note the view hierarchy
2. Identify all custom views, data binding expressions, and view references
3. List all resources used (strings, dimensions, colors, drawables)
4. Map Fragment/Activity connections to the layout

### Step 3: Capture Baseline Screenshot

If possible, obtain a visual reference of the current XML layout for comparison:
- Ask the user for a screenshot, or
- Use existing screenshot tests, or
- Proceed without (validate visually later)

### Step 4: Setup Compose Dependencies

Check `build.gradle.kts` or `libs.versions.toml` for Compose setup. Add if missing:

```kotlin
android {
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.activity:activity-compose:1.9.0")
}
```

Run sync to verify dependencies resolve.

### Step 5: Minimal Theming Setup

If Compose theming already exists, skip to Step 6. Otherwise, set up minimum theming:
- For Material-based projects, create a minimal `AppTheme` composable wrapping `MaterialTheme`
- Match existing XML theme colors, typography, and shapes
- Do NOT migrate the entire theme — only what's needed for the target screen

### Step 6: Convert Layout (XML → Compose Mapping)

| XML View | Compose Equivalent |
|----------|-------------------|
| `LinearLayout (vertical)` | `Column` |
| `LinearLayout (horizontal)` | `Row` |
| `FrameLayout` | `Box` |
| `ConstraintLayout` | `ConstraintLayout` (Compose) or `Column`/`Row`/`Box` |
| `ScrollView` | `Column` + `Modifier.verticalScroll()` |
| `RecyclerView` | `LazyColumn` / `LazyRow` |
| `TextView` | `Text` |
| `EditText` | `TextField` / `OutlinedTextField` |
| `ImageView` | `Image` / `AsyncImage` (Coil) |
| `Button` | `Button` |
| `CheckBox` | `Checkbox` |
| `Switch` | `Switch` |
| `ProgressBar` | `CircularProgressIndicator` / `LinearProgressIndicator` |
| `CardView` | `Card` |
| `Toolbar` | `TopAppBar` |
| `ViewPager2` | `HorizontalPager` |
| `TabLayout` | `TabRow` |
| `Spacer` / `View (spacer)` | `Spacer` |
| `include` | Extract as `@Composable` function |

Key attribute mappings:

| XML Attribute | Compose Modifier |
|--------------|-----------------|
| `layout_width/height` | `Modifier.fillMaxWidth()` / `.height(Xdp)` |
| `padding` | `Modifier.padding(Xdp)` |
| `margin` | Use `Modifier.padding()` on parent or `Spacer` |
| `background` | `Modifier.background(color)` |
| `visibility="gone"` | Conditional composition (`if (visible) { ... }`) |
| `visibility="invisible"` | `Modifier.alpha(0f)` |
| `gravity` | `Modifier.align()` / `horizontalAlignment` / `verticalArrangement` |
| `onClick` | `Modifier.clickable { }` or `onClick` parameter |

Include a `@Preview` for the new composable:

```kotlin
@Preview(showBackground = true)
@Composable
fun FeatureScreenPreview() {
    AppTheme { FeatureScreen() }
}
```

### Step 7: Validate Visual Parity

Compare the Compose Preview with the baseline screenshot from Step 3:
- Focus on layout structure and styling (ignore string content)
- Iterate on Compose code until visual parity is achieved
- Write a Compose UI test for the new composable

### Step 8: Replace Usages (Incremental Adoption)

For incremental migration, use `ComposeView` in existing Fragments/Activities:

```kotlin
// In Fragment's onCreateView or Activity's onCreate
ComposeView(requireContext()).apply {
    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
    setContent {
        AppTheme { FeatureScreen() }
    }
}
```

To embed Views in Compose (for components not yet migrated):

```kotlin
AndroidView(
    factory = { context -> CustomView(context) },
    update = { view -> view.setData(data) }
)
```

### Step 9: Cleanup XML Files

Delete the migrated XML layout file and its associated resources:
- Remove the XML layout file
- Remove unused string/dimen/color resources (only if not referenced elsewhere)
- Remove the old Fragment/Activity inflate code
- Delete associated legacy tests

**Caution:** Only remove code and resources not referenced by other parts of the project.

### Step 10: Final Build & Test

```bash
./gradlew assembleDebug
./gradlew test
```

## Verification Checklist

- [ ] Compose dependencies resolve without errors
- [ ] New composable has `@Preview` function
- [ ] Visual parity with original XML layout
- [ ] `ComposeView` uses `DisposeOnViewTreeLifecycleDestroyed` strategy
- [ ] Unused XML resources removed (only orphaned ones)
- [ ] Build succeeds
- [ ] Tests pass
