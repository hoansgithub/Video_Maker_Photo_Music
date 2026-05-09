---
name: agp9-upgrade
description: Migrate an Android project to Android Gradle Plugin (AGP) 9. Covers built-in Kotlin, new DSL, kapt-to-KSP migration, BuildConfig changes. Not for KMP projects.
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

# AGP 9 Migration Skill

Upgrade an Android project to Android Gradle Plugin 9 with built-in Kotlin, new DSL, and KSP migration.

## Prerequisites

1. Check current AGP version. If lower than 9, recommend running AGP Upgrade Assistant in Android Studio first.
2. Do NOT use this skill for Kotlin Multiplatform (KMP) projects.
3. Verify compatibility: AGP 9 requires Kotlin 2.0+, Gradle 8.11.1+, JDK 17+.

## Step 1: Update Dependencies

Update version references in `build.gradle.kts`, `build.gradle`, or `libs.versions.toml`:

| Dependency | Minimum Version |
|-----------|----------------|
| AGP | 9.0.0+ |
| KSP (`com.google.devtools.ksp`) | 2.3.6+ |
| Hilt (`com.google.dagger:hilt-android`) | 2.59.2+ |
| Gradle Wrapper | 8.11.1+ |

Update the Gradle wrapper:

```bash
./gradlew wrapper --gradle-version=8.11.1
```

## Step 2: Migrate to Built-in Kotlin

AGP 9 has built-in Kotlin support — `kotlin-android` plugin is no longer needed.

### 2a. Remove `kotlin-android` plugin

```kotlin
// Module-level build.gradle.kts — REMOVE this line:
plugins {
    alias(libs.plugins.kotlin.android)  // ← DELETE
}

// Top-level build.gradle.kts — REMOVE this line:
plugins {
    alias(libs.plugins.kotlin.android) apply false  // ← DELETE
}
```

Also remove from `libs.versions.toml`:

```toml
# DELETE this line:
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
```

### 2b. Migrate `kotlinOptions` DSL

```kotlin
// OLD (remove):
android {
    kotlinOptions {
        jvmTarget = "17"
    }
}

// NEW:
android {
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        jvmTarget = "17"
    }
}
```

### 2c. Migrate `kotlin.sourceSets` if used

Replace `kotlin.sourceSets` with `android.sourceSets` or standard Kotlin source directories.

## Step 3: Migrate to New AGP DSL

Key property changes in AGP 9:

| Old DSL | New DSL |
|---------|---------|
| `android.namespace` | `android.namespace` (unchanged) |
| `buildTypes { release { minifyEnabled = true } }` | `buildTypes { release { isMinifyEnabled = true } }` |
| `buildTypes { release { shrinkResources = true } }` | `buildTypes { release { isShrinkResources = true } }` |
| `buildTypes { release { debuggable = false } }` | `buildTypes { release { isDebuggable = false } }` |
| `testInstrumentationRunner` | `testInstrumentationRunner` (unchanged) |
| `flavorDimensions "tier"` | `flavorDimensions += "tier"` |
| `versionCode 1` | `versionCode = 1` |
| `versionName "1.0"` | `versionName = "1.0"` |

## Step 4: Migrate kapt to KSP

### 4a. Check KSP compatibility

Inspect each `kapt` dependency — if its JAR contains `services/com.google.devtools.ksp.processing.SymbolProcessorProvider`, it supports KSP.

### 4b. Replace kapt with KSP

```kotlin
// OLD:
plugins {
    id("org.jetbrains.kotlin.kapt")  // ← REMOVE
}
dependencies {
    kapt("com.google.dagger:hilt-compiler:2.59.2")
    kapt("androidx.room:room-compiler:2.7.0")
}

// NEW:
plugins {
    id("com.google.devtools.ksp")  // ← ADD
}
dependencies {
    ksp("com.google.dagger:hilt-compiler:2.59.2")
    ksp("androidx.room:room-compiler:2.7.0")
}
```

### 4c. Incompatible kapt dependencies

If a dependency does NOT support KSP, keep it with `com.android.legacy-kapt`:

```kotlin
plugins {
    id("com.android.legacy-kapt")  // Replaces kotlin-kapt for built-in Kotlin
}
dependencies {
    kapt("some.library:processor:1.0")  // Incompatible with KSP
}
```

### 4d. Hilt-specific steps

Hilt 2.59.2+ supports KSP. After switching, ensure `@HiltAndroidApp`, `@AndroidEntryPoint`, and `@HiltViewModel` annotations still compile.

## Step 5: BuildConfig Changes

If any module uses custom `BuildConfig` fields, enable the feature explicitly:

```kotlin
android {
    buildFeatures {
        buildConfig = true
    }
}
```

For custom fields, use the new API. **Important:** String values must include escaped quotes:

```kotlin
BuildConfigField(
    type = "String",
    value = "\"Some value\"",  // Quotes are part of the value
    comment = "Optional comment",
)
```

## Step 6: Clean Up gradle.properties

Remove these flags after migration (they are now defaults):

```properties
# DELETE these lines:
android.builtInKotlin=true
android.newDsl=true
android.uniquePackageNames=true
android.enableAppCompileTimeRClass=true
```

Also remove `android.builtInKotlin=false` if previously set to opt out.

## Verification

```bash
# 1. Sync check
./gradlew help

# 2. Dry run build
./gradlew build --dry-run

# 3. Full build
./gradlew assembleDebug

# 4. Run tests
./gradlew test
```

## Troubleshooting

| Issue | Fix |
|-------|-----|
| `Cannot add extension with name 'kotlin'` | Remove `kotlin-android` plugin (Step 2a) |
| `kotlin-kapt is incompatible` | Switch to KSP or `com.android.legacy-kapt` (Step 4) |
| Paparazzi v2.0.0-alpha04 failures | Upgrade Paparazzi to latest version compatible with AGP 9 |
| KSP version mismatch | Ensure KSP version ≥ 2.3.6 and matches your Kotlin version |

## Guidelines

- Never write or run Python scripts.
- Only search Gradle dependency cache as a last resort.
- Never add `android.disallowKotlinSourceSets=false` to `gradle.properties`.
- When verifying, do not run `clean` task (wastes time).
