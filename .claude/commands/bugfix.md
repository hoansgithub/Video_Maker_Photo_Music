# /bugfix - Android Bug Fix Workflow

Complete workflow for diagnosing and fixing Android bugs.

## Usage

```
/bugfix [description of the bug]
```

## Workflow

### Step 1: Diagnosis

Use **kotlin-debugger** to find root cause:

```
Use kotlin-debugger to investigate: $ARGUMENTS
```

This will:
- Analyze symptoms
- Search for related code
- Check recent changes
- Identify root cause

### Step 2: Fix Implementation

Use **kotlin-developer** to implement the fix:

```
Use kotlin-developer to fix the bug found by debugger
```

Follow the fix patterns:
- No force unwraps
- Proper error handling
- Channel for navigation
- viewModelScope for coroutines

### Step 3: Navigation Verification

Use **navigation-guardian** to ensure no navigation anti-patterns:

```
Use navigation-guardian to verify the fix doesn't introduce issues
```

Check:
- No new state-based navigation
- Channel pattern maintained
- No Activities embedded as composables

### Step 4: Regression Test

Use **kotlin-tester** to add regression test:

```
Use kotlin-tester to add a regression test for this bug
```

Test should:
- Reproduce the original bug scenario
- Verify the fix works
- Prevent future regressions

## Bug Categories

### Navigation Bugs (MOST COMMON)

```
Symptoms: Double navigation, wrong screen on back, re-navigation on rotation
Common Causes:
- LaunchedEffect(uiState) for navigation
- Navigation data in UI state
- Activity embedded as composable
- Missing Channel pattern
```

### Crash Bugs

```
Symptoms: App crashes, NullPointerException
Common Causes:
- Force unwrap (!!)
- Unhandled null
- Missing null checks
- Uncaught exceptions
```

### UI Bugs

```
Symptoms: UI not updating, stale data
Common Causes:
- collectAsState vs collectAsStateWithLifecycle
- Objects created in composition
- State not in ViewModel
- Missing remember {}
```

### Coroutine Bugs

```
Symptoms: Operations continue after navigation, memory leaks
Common Causes:
- GlobalScope usage
- Wrong coroutine scope
- Missing exception handling
- Uncancelled jobs
```

## Checklist Before Done

```
[ ] Root cause identified
[ ] Fix implemented correctly
[ ] No new navigation anti-patterns
[ ] No new force unwraps
[ ] Regression test added
[ ] Related code reviewed
[ ] Original bug no longer reproduces
```

## Example

```
User: /bugfix navigation happens twice when clicking save button

Claude:
1. Uses kotlin-debugger to investigate
   - Finds LaunchedEffect(uiState) causing double navigation
2. Uses kotlin-developer to fix with Channel pattern
3. Uses navigation-guardian to verify no anti-patterns
4. Uses kotlin-tester to add regression test
5. Confirms fix prevents double navigation
```
