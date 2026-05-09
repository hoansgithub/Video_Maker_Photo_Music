---
name: play-billing-upgrade
description: Upgrade or migrate an Android project from any legacy Google Play Billing Library (PBL) version to the latest stable version. Use when upgrading billing, fixing deprecated billing APIs, or migrating SkuDetails to ProductDetails.
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

# Play Billing Library Version Upgrade Skill

Upgrade Google Play Billing Library from any legacy version to the latest stable version.

## Phase 1: Discovery & Situational Awareness

### 1a. Locate Dependency

Find the billing dependency in `build.gradle`, `build.gradle.kts`, or `libs.versions.toml`:

```bash
grep -rn "billingclient\|play.billing\|billing-ktx" --include="*.gradle*" --include="*.toml"
```

### 1b. Initial Build Test

Attempt sync and build. If it fails immediately, scan for deprecated APIs to determine the **Effective Version**.

### 1c. Detect Effective Version

The presence of deprecated APIs indicates the baseline version:

| Deprecated API | Effective Version |
|---------------|-------------------|
| `SkuDetails`, `BillingFlowParams.Builder.setSkuDetails()` | PBL ≤ 5 |
| `querySkuDetailsAsync()` | PBL ≤ 5 |
| `launchPriceChangeConfirmationFlow()` | PBL ≤ 5 |
| `queryPurchases()` (sync) | PBL ≤ 4 |
| `enablePendingPurchases()` without params | PBL ≤ 6 |
| `setIsOfferPersonalized()` | PBL 5–6 |

### 1d. Calculate Migration Path

- **Within 2 major versions** of target → **Direct Migration**
- **More than 2 major versions** behind → **Stepped Migration** (upgrade 2 major versions at a time)

## Phase 2: Document Mapping & Planning

For each major version jump, identify:
- API removals and replacements
- Class renames (e.g., `SkuDetails` → `ProductDetails`)
- New required parameters
- SDK requirement changes

### Key API Changes Across Versions

| PBL 4→5 | Change |
|---------|--------|
| `SkuDetails` | → `ProductDetails` |
| `querySkuDetailsAsync()` | → `queryProductDetailsAsync()` |
| `BillingFlowParams.setSkuDetails()` | → `.setProductDetailsParamsList()` |
| `queryPurchases()` (sync) | → `queryPurchasesAsync()` |

| PBL 5→6 | Change |
|---------|--------|
| `enablePendingPurchases()` | Now requires `PendingPurchasesParams` |
| `launchPriceChangeConfirmationFlow()` | Removed — use Play Console |
| `setIsOfferPersonalized()` | Moved to `ProductDetailsParams` |

| PBL 6→7 | Change |
|---------|--------|
| `BillingClient.Builder` | New `enableAutoServiceReconnection()` option |
| Custom retry logic | Can be replaced with auto-reconnection |
| `acknowledgePurchase()` flow | Updated acknowledgment pattern |

| PBL 7→8 | Change |
|---------|--------|
| `compileSdk` | Requires 35+ |
| Kotlin coroutines extensions | Updated `billing-ktx` APIs |

## Phase 3: Execution

### Step 1: SDK & Environment Alignment

Update `build.gradle.kts` to meet SDK requirements:

```kotlin
android {
    compileSdk = 35  // PBL 8 requires 35+
}

dependencies {
    implementation("com.android.billingclient:billing:X.Y.Z")
    implementation("com.android.billingclient:billing-ktx:X.Y.Z")  // If using Kotlin
}
```

Verify AGP and Kotlin version compatibility.

### Step 2: Intent-Based Refactoring

Analyze the **intent** of existing code rather than doing textual replacement:

- Replace deprecated classes with their modern equivalents
- Update method signatures and parameter types
- Remove custom retry/reconnection logic if `enableAutoServiceReconnection()` is available
- Update purchase verification and acknowledgment flows

### Step 3: Stepped Verification (Stepped Migrations Only)

For each intermediate version:
1. Upgrade to the intermediate version
2. Run `./gradlew assembleDebug` to verify
3. Repeat until reaching the target version

### Step 4: Final Validation

```bash
# Run tests
./gradlew test

# Clean build
./gradlew clean assembleDebug

# Sync
./gradlew build
```

## Validation Checklist

- [ ] No deprecated billing APIs remain (SkuDetails, querySkuDetailsAsync, etc.)
- [ ] `enablePendingPurchases()` uses `PendingPurchasesParams` (PBL 6+)
- [ ] `compileSdk` meets minimum requirement for target PBL version
- [ ] All purchase flows use `ProductDetails` (not `SkuDetails`)
- [ ] Acknowledgment flow updated for target version
- [ ] Custom retry logic removed if auto-reconnection is available
- [ ] Unit tests pass
- [ ] Clean build succeeds

## Final Report Format

After migration, explain each change to the developer:
- "Updated SDK to [Version] because PBL [Version] requires it."
- "Replaced `SkuDetails` with `ProductDetails` — the new API for product information."
- "Removed custom `retryConnection()` logic — now handled by `enableAutoServiceReconnection()`."
- "Suggest exploring [New Feature] from the latest release (e.g., Prepaid Plans, Installments)."
