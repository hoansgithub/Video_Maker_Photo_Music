# AdMob Native Ad Policy Audit Guide

A reusable checklist for scanning Android/iOS apps against Google AdMob native ad policies.

---

## 1. Policy Requirements

Source: [AdMob Native Ads Playbook](https://admob.google.com/home/resources/native-ads-playbook/) | [Ads Disguised as Content](https://pubscale.com/policy/ads-disguised-as-content) | [Native Ads Policy](https://support.google.com/admob/answer/6329638)

### Mandatory

| # | Requirement | Detail |
|---|-------------|--------|
| 1 | **Ad Attribution** | Must include label "Ad" or "Advertisement", or a **minimum 15px badge** that says "Ad" |
| 2 | **AdChoices Icon** | Must display AdChoices overlay (provided by SDK via `ad_choices_container`) |
| 3 | **Visual Distinction** | Users must tell where app content ends and ad begins. Use **at least one**: different background color, border on all 4 sides, or elevation/shadow |
| 4 | **Non-clickable Background** | Ad background/white-space must NOT be clickable. Only designated elements (headline, CTA, icon, media) should be clickable |
| 5 | **Clear CTA** | Include obvious call-to-action ("Install", "Shop Now", "Learn More", etc.) |

### Recommended

| # | Recommendation | Detail |
|---|----------------|--------|
| 6 | Match app style | Use similar fonts, colors, styles — but maintain clear separation |
| 7 | Don't stretch/crop assets | Preserve advertiser asset aspect ratios |
| 8 | Adequate size | Consider larger layouts to make the ad noticeable |

---

## 2. Audit Steps

### Step 1: Inventory All Native Ad Layouts

Find all native ad XML layouts in the project:

```bash
# Android
find . -path "*/res/layout/native*.xml" -o -path "*/res/layout/*ad*.xml" | sort

# iOS (XIB/Storyboard)
find . -name "*Ad*.xib" -o -name "*Native*.xib" | sort

# Compose/SwiftUI (inline layouts)
grep -rn "NativeAdView\|GADNativeAdView\|NativeAd(" --include="*.kt" --include="*.swift" | grep -v "import"
```

### Step 2: Map Layouts to Placements

Find which layout is used for each ad placement:

```bash
# Android - find layout provider/inflater
grep -rn "R.layout.native\|NativeAdView\|setNativeAd\|populateNativeAdView" --include="*.kt" --include="*.java"

# iOS
grep -rn "GADNativeAdView\|nativeAdView\|loadNibNamed.*Ad" --include="*.swift" --include="*.m"
```

### Step 3: Check Each Layout Against Policy

For each layout file, verify:

```bash
# Check for Ad badge/label
grep -l "ad_badge\|ad_label\|ad_attribution\|\"Ad\"\|\"Advertisement\"" <layout_file>

# Check for AdChoices container
grep -l "ad_choices_container\|adChoices" <layout_file>

# Check for CTA button
grep -l "ad_call_to_action\|callToAction\|cta" <layout_file>

# Check background color
grep "android:background\|backgroundColor" <layout_file>
```

### Step 4: Compare Background Colors

Extract and compare:

```bash
# Get all background colors from ad layouts
grep -rn "android:background=" */res/layout/native*.xml

# Get app container colors
grep -rn "background_dark\|BackgroundDark\|colorScheme.background\|surface" */res/values/colors.xml */theme/*.kt
```

**Rule**: Ad background hex value must be visually distinguishable from its container. A difference of < `#101010` between ad and container is too subtle.

### Step 5: Map Placements to Screens

Identify where each ad appears:

```bash
# Find all ad placement constants
grep -rn "NATIVE_\|NativePlacement\|AdPlacement" --include="*.kt" --include="*.swift" | grep -i "native"

# Find where placements are used in UI
grep -rn "NATIVE_\|loadNativeAd\|showNativeAd\|NativeAdView" --include="*.kt" --include="*.swift" | grep -v "test\|Test"
```

---

## 3. Audit Table Template

Copy and fill in for each layout:

```markdown
| Layout File | Background | Ad Badge | AdChoices | Border/Shadow | CTA | Compliant? |
|-------------|-----------|----------|-----------|---------------|-----|------------|
| native_xxx  | #XXXXXX   | Yes/No   | Yes/No    | Yes/No        | Yes/No | Yes/No |
```

### Per-Placement Table

```markdown
| Placement Name | Layout Used | Screen | Container BG | Ad BG | BG Match? | Ad Badge? | Verdict |
|----------------|-------------|--------|-------------|-------|-----------|-----------|---------|
| NATIVE_XXX     | native_xxx  | HomeScreen | #09090B | #1E1E1E | Distinct | Yes | PASS |
```

---

## 4. Common Violations

### 4.1 Missing Ad Badge (HIGH RISK)

**Violation**: No "Ad" label visible to the user.

**Fix** (Android XML):
```xml
<TextView
    android:id="@+id/ad_badge"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:text="Ad"
    android:textSize="10sp"
    android:textColor="#FFFFFF"
    android:background="@drawable/bg_ad_badge"
    android:paddingHorizontal="6dp"
    android:paddingVertical="2dp"
    android:layout_gravity="top|start"
    android:layout_margin="8dp" />
```

**Fix** (iOS):
```swift
let adBadge = UILabel()
adBadge.text = "Ad"
adBadge.font = .systemFont(ofSize: 10, weight: .semibold)
adBadge.textColor = .white
adBadge.backgroundColor = UIColor(white: 1.0, alpha: 0.25)
adBadge.layer.cornerRadius = 4
adBadge.clipsToBounds = true
```

**Badge drawable** (`bg_ad_badge.xml`):
```xml
<shape xmlns:android="http://schemas.android.com/apk/res/android">
    <solid android:color="#40FFFFFF" />
    <corners android:radius="4dp" />
    <padding android:left="6dp" android:right="6dp" android:top="2dp" android:bottom="2dp" />
</shape>
```

### 4.2 Background Too Similar to Container (HIGH RISK)

**Violation**: Ad background is visually indistinguishable from app content background.

**Detection**:
```
App background:  #09090B (nearly black)
Ad background:   #030303 (nearly black)
Difference:      ~#060608 → TOO CLOSE
```

**Fix**: Change ad background to a visibly distinct shade. Recommended minimum contrast:
- Dark theme: ad bg should be `#1A1A1A`+ when container is `#09090B`
- Light theme: ad bg should be `#E8E8E8`- when container is `#FFFFFF`

### 4.3 Infeed Ads Without Visual Boundary (HIGH RISK)

**Violation**: Ad items in a scrollable list (LazyColumn, RecyclerView, UITableView) look identical to content items.

**Fix options** (use at least one):
1. **Different background color** on the ad item
2. **Border** around the ad:
   ```xml
   <shape>
       <stroke android:width="1dp" android:color="#33FFFFFF" />
       <corners android:radius="8dp" />
   </shape>
   ```
3. **Elevation/shadow**:
   ```xml
   android:elevation="2dp"
   ```

### 4.4 Fullscreen Ads Without Attribution (MEDIUM RISK)

**Violation**: Fullscreen native ads that cover the entire screen without an "Ad" label. Lower risk because fullscreen context implies it's an ad, but still recommended to include a badge.

### 4.5 Clickable Background (MEDIUM RISK)

**Violation**: Entire ad container is clickable, not just designated elements.

**Detection**:
```bash
grep -rn "setOnClickListener\|android:clickable=\"true\"" <layout_file>
```

**Fix**: Only register click targets via `NativeAdView` asset setters (`setHeadlineView`, `setCallToActionView`, etc.). Never set `onClickListener` on the root view.

---

## 5. Risk Severity Matrix

| Violation | Risk Level | Consequence |
|-----------|-----------|-------------|
| Missing Ad badge on infeed ads | **Critical** | Account suspension, ad serving limited |
| Background identical to container | **Critical** | "Ads Disguised as Content" violation |
| Missing AdChoices container | **High** | Policy violation, ads may not serve |
| Missing CTA button | **Medium** | Reduced engagement, possible policy flag |
| Clickable background | **Medium** | Invalid click activity, revenue clawback |
| Missing Ad badge on fullscreen | **Low** | Context makes it obvious, but still recommended |

---

## 6. Validation Tools

### AdMob Native Ad Validator (Built-in)

Test ads automatically show policy violations as an overlay:

```kotlin
// Android - enable in debug builds
// Uses test ad unit IDs → validator appears automatically
val adRequest = AdRequest.Builder().build()
adLoader.loadAd(adRequest) // with test ad unit ID
```

Docs: [Native Validator (Android)](https://developers.google.com/admob/android/beta/native/validator)

### Manual Checklist Per Layout

- [ ] "Ad" badge visible (min 15px / 10sp)
- [ ] AdChoices container present at top-right
- [ ] Background color differs from container by at least `#101010`
- [ ] CTA button present with action text
- [ ] Background is NOT clickable
- [ ] Ad assets (icon, media) not stretched or cropped
- [ ] Text colors readable against ad background

---

## 7. Quick Scan Commands

Run these from the project root to find potential issues:

```bash
# Find layouts WITHOUT ad badge
echo "=== Layouts missing Ad badge ==="
for f in $(find . -path "*/res/layout/native*.xml"); do
    if ! grep -q "ad_badge\|ad_label\|ad_attribution" "$f"; then
        echo "  MISSING: $f"
    fi
done

# Find layouts WITHOUT AdChoices
echo "=== Layouts missing AdChoices ==="
for f in $(find . -path "*/res/layout/native*.xml"); do
    if ! grep -q "ad_choices_container" "$f"; then
        echo "  MISSING: $f"
    fi
done

# Find layouts WITHOUT CTA
echo "=== Layouts missing CTA ==="
for f in $(find . -path "*/res/layout/native*.xml"); do
    if ! grep -q "ad_call_to_action" "$f"; then
        echo "  MISSING: $f"
    fi
done

# List all ad background colors
echo "=== Ad background colors ==="
grep -rn "android:background=" */res/layout/native*.xml | grep -v "ad_icon\|ad_media\|ad_call\|cta\|badge"

# Compare with app backgrounds
echo "=== App background colors ==="
grep -rn "background.*#\|Background.*Color\|background_dark\|surface_dark" */res/values/colors.xml
```

---

## 8. References

- [AdMob Native Ads Playbook](https://admob.google.com/home/resources/native-ads-playbook/)
- [Native Ads Policy](https://support.google.com/admob/answer/6329638)
- [Overview of Native Ads](https://support.google.com/admob/answer/6239795)
- [Ads Disguised as Content](https://pubscale.com/policy/ads-disguised-as-content)
- [Native Ad Validator (Android)](https://developers.google.com/admob/android/beta/native/validator)
- [Native Ad Validator (iOS)](https://developers.google.com/admob/ios/native/validator)
- [AdMob Community: Background Handling](https://support.google.com/admob/thread/219238137)
