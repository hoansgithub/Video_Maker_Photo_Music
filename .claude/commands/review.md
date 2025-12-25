# /review - Android Code Review Workflow

Comprehensive code review for Android code.

## Usage

```
/review [scope - file, feature, or PR]
```

## Workflow

### Step 1: Navigation Safety Scan

Use **navigation-guardian** first for critical issues:

```
Use navigation-guardian to scan: $ARGUMENTS
```

Checks:
- State-based navigation anti-patterns
- Channel pattern usage
- LaunchedEffect(Unit) vs LaunchedEffect(state)
- Activity vs Composable decisions

### Step 2: Edge Case Analysis

Use **edgecase-analyzer** for boundary conditions:

```
Use edgecase-analyzer to analyze: $ARGUMENTS
```

Identifies:
- Missing null handling
- Configuration change issues
- Empty data handling
- Back button behavior

### Step 3: Code Quality Review

Use **kotlin-reviewer** for overall quality:

```
Use kotlin-reviewer to review: $ARGUMENTS
```

Evaluates:
- Architecture adherence
- Kotlin conventions
- Best practices
- Code organization

## Review Depth Levels

### Quick Review (Single File)

```
/review FeatureViewModel.kt
```

Checks:
- Channel pattern
- collectAsStateWithLifecycle
- Force unwraps
- Basic structure

### Feature Review (Multiple Files)

```
/review Profile feature
```

Checks:
- All files in feature
- Inter-file dependencies
- Architecture compliance
- Test coverage

### PR Review (All Changes)

```
/review PR 123
```

or

```
/review recent changes
```

Checks:
- All modified files
- Git diff analysis
- Breaking changes
- Migration needs

## Review Categories

### Navigation Safety (Critical)

```
Must Pass:
✓ Channel for navigation events
✓ LaunchedEffect(Unit) for event collection
✓ No navigation data in UI state
✓ No LaunchedEffect(uiState) for navigation
✓ No Activities embedded as composables
```

### Coroutine Safety (Critical)

```
Must Pass:
✓ viewModelScope used
✓ No GlobalScope
✓ Exception handling present
✓ Proper dispatcher usage
```

### Type Safety (High)

```
Must Pass:
✓ No force unwrap (!!)
✓ Null handling with ?. or ?:
✓ Sealed class when exhaustive
```

### Compose Best Practices (Medium)

```
Should Pass:
✓ collectAsStateWithLifecycle() used
✓ Objects in remember {}
✓ State hoisted properly
✓ Preview functions present
```

## Checklist

```
CRITICAL
[ ] No state-based navigation
[ ] Channel pattern used
[ ] LaunchedEffect(Unit) for events
[ ] No force unwraps
[ ] viewModelScope used

HIGH
[ ] collectAsStateWithLifecycle
[ ] Edge cases handled
[ ] Error handling proper

MEDIUM
[ ] Architecture correct
[ ] Code organized
[ ] Tests present
```

## Output Format

```
## Code Review Summary

### 🔴 Critical Issues
[Must fix]

### 🟡 Warnings
[Should fix]

### 🟢 Suggestions
[Nice to fix]

### ✅ Good Practices
[What's done well]

### Final Verdict
[ ] Approved
[ ] Approved with suggestions
[ ] Changes requested
```

## Example

```
User: /review FeatureViewModel.kt

Claude:
1. Uses navigation-guardian
   - Found: LaunchedEffect(uiState) - state-based navigation!
   - Found: Channel pattern missing

2. Uses edgecase-analyzer
   - Found: Empty list case not handled

3. Uses kotlin-reviewer
   - Architecture: Good ✓
   - Style: Consistent ✓
   - Tests: Missing for error case

Result: Changes requested (2 critical issues)
```
