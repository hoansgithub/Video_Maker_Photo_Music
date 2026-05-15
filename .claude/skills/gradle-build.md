---
name: gradle-build
description: Build, test, and run Android apps with Gradle
allowed-tools: Bash(./gradlew:*), Bash(adb:*), Read
hooks:
  pre_tool_use:
    - tool: Bash
      script: |
        # Ensure JAVA_HOME is set
        if [ -z "$JAVA_HOME" ]; then
          export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
        fi
---

# Gradle Build Skill

Build, test, and run Android applications.

## Environment Setup (CRITICAL)

```bash
# ALWAYS set before any Gradle command
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"

# Paths by OS:
# macOS:   /Applications/Android Studio.app/Contents/jbr/Contents/Home
# Windows: C:\Program Files\Android\Android Studio\jbr
# Linux:   /opt/android-studio/jbr
```

## Common Operations

### Build Debug
```bash
./gradlew assembleDebug
```

### Build Release
```bash
./gradlew assembleRelease
```

### Run Unit Tests
```bash
./gradlew test
```

### Run Instrumented Tests
```bash
./gradlew connectedAndroidTest
```

### Clean Build
```bash
./gradlew clean build
```

### Check Dependencies
```bash
./gradlew dependencies
```

### Lint Check
```bash
./gradlew lint
```

## ADB Commands

### List Devices
```bash
adb devices
```

### Install APK
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Launch App
```bash
adb shell am start -n com.example.app/.MainActivity
```

### View Logs
```bash
adb logcat -s "TAG:*"
```

### Take Screenshot
```bash
adb exec-out screencap -p > screenshot.png
```

### Clear App Data
```bash
adb shell pm clear com.example.app
```

## Build Variants

### List All Tasks
```bash
./gradlew tasks
```

### Build Specific Flavor
```bash
./gradlew assembleStagingDebug
./gradlew assembleProductionRelease
```

## Output Format

```markdown
## Build Result

**Variant:** debug/release
**Status:** Success/Failed
**APK:** path/to/app.apk

### Warnings
- [Warning 1]

### Errors (if failed)
- [Error 1]

### Next Steps
- [Recommendation]
```
