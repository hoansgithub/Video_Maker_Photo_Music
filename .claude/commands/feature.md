# /feature - Android Feature Workflow

Complete workflow for implementing a new Android feature.

## Usage

```
/feature [description]
```

## Workflow

Execute these steps in order:

### Step 1: Plan with Ultrathink

Use the **ultrathink-planner** agent to design the feature:

```
Use ultrathink-planner to design: $ARGUMENTS
```

This will produce:
- 3 implementation approaches with trade-offs
- Kotlin coroutine considerations
- Navigation safety plan
- File structure

### Step 2: Architecture Review

If the feature touches 3+ files, use **kotlin-architect**:

```
Use kotlin-architect to review the architecture for: $ARGUMENTS
```

This ensures:
- Clean Architecture layers are correct
- Dependencies flow correctly
- Proper Hilt module setup

### Step 3: Implementation

Use **kotlin-developer** to implement each component:

```
Use kotlin-developer to implement step 1 from the plan
```

Follow the established patterns:
- Channel for navigation events
- LaunchedEffect(Unit) for event collection
- collectAsStateWithLifecycle()
- Sealed class UI state

### Step 4: Navigation Check

Use **navigation-guardian** to verify navigation patterns:

```
Use navigation-guardian to check the implementation
```

Verify:
- No state-based navigation
- Channel pattern used correctly
- No Activities embedded as composables
- Proper collectAsStateWithLifecycle usage

### Step 5: Edge Cases

Use **edgecase-analyzer** to identify edge cases:

```
Use edgecase-analyzer to analyze edge cases for: $ARGUMENTS
```

Check:
- Configuration change handling
- Empty data states
- Network failure scenarios
- Back button behavior

### Step 6: Testing

Use **kotlin-tester** to add tests:

```
Use kotlin-tester to add tests for the new feature
```

Cover:
- ViewModel state transitions
- Navigation event emission
- UseCase business logic

### Step 7: Final Review

Use **kotlin-reviewer** for final code review:

```
Use kotlin-reviewer to review the implementation
```

## Checklist Before Done

```
[ ] ultrathink-planner completed design
[ ] kotlin-architect validated structure (if 3+ files)
[ ] kotlin-developer implemented code
[ ] navigation-guardian found no issues
[ ] edgecase-analyzer covered edge cases
[ ] kotlin-tester added tests
[ ] kotlin-reviewer approved
[ ] Channel pattern for navigation
[ ] LaunchedEffect(Unit) for events
[ ] collectAsStateWithLifecycle() used
[ ] No force unwraps (!!)
[ ] finish() after startActivity()
```

## Example

```
User: /feature user profile editing

Claude:
1. Uses ultrathink-planner to design profile editing feature
2. Uses kotlin-architect for architecture review
3. Uses kotlin-developer to implement ProfileEditViewModel
4. Uses kotlin-developer to implement ProfileEditScreen
5. Uses navigation-guardian to check navigation patterns
6. Uses edgecase-analyzer for edge cases
7. Uses kotlin-tester for unit tests
8. Uses kotlin-reviewer for final review
```
