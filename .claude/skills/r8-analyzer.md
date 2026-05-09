---
name: r8-analyzer
description: Analyze Android R8/ProGuard keep rules for redundancies, overly broad rules, and optimization opportunities. Read-only analysis — does NOT modify keep rules.
allowed-tools: Read, Grep, Glob, Bash(./gradlew:*)
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

# R8/ProGuard Keep Rules Analyzer

Analyze keep rules to identify redundancies, broad rules, and optimization opportunities. **Read-only** — does NOT modify keep rule files.

## Step 1: Locate Configuration Files

Find all ProGuard/R8 config files:

```bash
# Find ProGuard rule files
find . -name "proguard-rules.pro" -o -name "proguard-*.pro" -o -name "consumer-rules.pro"
```

## Step 2: Check AGP Version and R8 Mode

Verify R8 is properly configured in `build.gradle.kts`:

```kotlin
// App module — REQUIRED for R8
buildTypes {
    release {
        isMinifyEnabled = true
        isShrinkResources = true
        proguardFiles(
            getDefaultProguardFile("proguard-android-optimize.txt"),
            "proguard-rules.pro"
        )
    }
}
```

Check `gradle.properties` for R8 flags:
- **AGP 8.0+**: Full mode enabled by default. Ensure `android.enableR8.fullMode=false` is NOT present.
- **AGP 8.6–8.x**: Explicitly enable `android.r8.optimizedResourceShrinking=true`.
- **AGP 9.0+**: All optimizations included by default. Suggest upgrading if on older AGP.

## Step 3: R8 Configuration Reference

Verify the app uses `proguard-android-optimize.txt` (not `proguard-android.txt`). The optimized variant enables additional R8 optimizations.

## Step 4: Evaluate Against Known Library Rules

Modern libraries bundle their own consumer keep rules. These manual rules are **redundant**:

### Global Rules (REMOVE)

```proguard
# These disable ALL R8 optimization — always remove
-dontshrink
-dontobfuscate
-dontoptimize
```

### Android Components (REMOVE)

AAPT2 and R8 automatically keep components in `AndroidManifest.xml`:

```proguard
# Redundant — handled by AAPT2
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Service
-keep public class * extends android.view.View
```

### AndroidX / Kotlin / Kotlinx (REMOVE)

Libraries bundle their own rules:

```proguard
# Redundant — bundled in library AARs
-keep class androidx.** { *; }
-keep class kotlinx.** { *; }
-keep class kotlin.** { *; }
```

### Gson (REMOVE if v2.11.0+)

Gson 2.11.0+ bundles its own rules. Use `@SerializedName` on fields instead:

```proguard
# Redundant with Gson 2.11.0+
-keep class com.example.app.models.** { *; }
-keep class * extends com.google.gson.TypeAdapter
-keep class com.google.gson.reflect.TypeToken { *; }
```

### Retrofit (REMOVE if v2.9.0+)

Retrofit 2.9.0+ bundles rules that auto-detect `@GET`, `@POST`, etc.:

```proguard
# Redundant with Retrofit 2.9.0+
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * { @retrofit2.http.* <methods>; }
```

### OkHttp / Room / Other AndroidX

All modern AndroidX libraries include consumer rules. Remove manual rules targeting these packages.

## Step 5: Impact Hierarchy Analysis

Assess remaining rules from broadest to narrowest impact (9 tiers):

| Tier | Type | Example | Impact |
|------|------|---------|--------|
| 1 | Package-wide wildcards | `-keep class com.example.**{*;}` | Disables ALL optimization in package |
| 2 | Inversion operator | `-keep class !com.example.MyClass{*;}` | Keeps everything EXCEPT named class |
| 3 | Class + all members | `-keep class com.example.MyClass{*;}` | No optimization for entire class |
| 4 | keepclassmembers | `-keepclassmembers class X{*;}` | Retains all members |
| 5 | Keep + modifiers | `-keep,allowobfuscation class X{*;}` | Partial optimization |
| 6 | Specific method, no modifier | `-keep class X{void myMethod();}` | Targeted but no optimization |
| 7 | Class-name only | `-keep class com.example.MyClass` | Name only, members can be removed |
| 8 | Modifiers without members | `-keep,allowshrinking class X` | Minimal impact |
| 9 | Conditional keep rules | `-keepclasseswithmembers class *{native <methods>;}` | Most optimization-friendly |

**Higher tiers = more harmful to app size.** Flag Tier 1–3 rules for immediate review.

## Step 6: Subsumption Check

Identify keep rules that are subsumed by broader rules:
- If `-keep class com.example.**{*;}` exists, any rule targeting `com.example.sub.MyClass` is redundant
- Suggest removing the broader rule and keeping only specific rules

## Step 7: Reflection Analysis

Search for reflection usage that may justify keep rules:

```bash
grep -rn "Class.forName\|getDeclaredField\|getDeclaredMethod\|getMethod\|getField" --include="*.kt" --include="*.java"
grep -rn "::class.java\|javaClass\|KClass" --include="*.kt"
grep -rn "@SerializedName\|@Json\|@JsonProperty" --include="*.kt" --include="*.java"
```

For each reflection usage:
- If reflection targets a specific class, suggest a narrow keep rule for just that class
- If using `@SerializedName` or `@Json`, the library's bundled rules should suffice

## Step 8: Generate Ordered Recommendations

For every keep rule, provide one of:
- **REMOVE** — rule is redundant (library bundles its own rules)
- **REFINE** — rule is too broad, suggest narrower alternative
- **KEEP** — rule is necessary and correctly scoped

Order recommendations by impact (Tier 1 first → Tier 9 last).

## Step 9: Testing Guidance

After implementing recommendations, advise running:
1. Full release build: `./gradlew assembleRelease`
2. UI Automator tests focusing on packages where keep rules were modified
3. Manual testing of features that use reflection, serialization, or JNI

## Constraints

- **Read-only analysis** — do NOT modify keep rule files
- Do not mention impact tier numbers to the user
- Do not generate report sections if no rules to report
- Do not mention the skill's reference files
- Do not mention R8 benefits (focus on actionable analysis)
