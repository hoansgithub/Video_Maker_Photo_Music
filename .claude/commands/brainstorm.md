# /brainstorm - Android Creative Solutions

Generate multiple creative approaches for Android challenges.

## Usage

```
/brainstorm [topic or challenge]
```

## Workflow

### Step 1: Brainstorm Alternatives

Use **brainstormer** to generate options:

```
Use brainstormer to think differently about: $ARGUMENTS
```

This will:
- Challenge assumptions
- Generate 3+ approaches
- Consider unconventional solutions
- Evaluate trade-offs

### Step 2: Edge Case Consideration (Optional)

If any approach is selected, use **edgecase-analyzer**:

```
Use edgecase-analyzer for the selected approach
```

## Brainstorming Triggers

Use this command when:

- **"How should I..."** - Architecture decisions
- **"What's the best way to..."** - Implementation choices
- **"I'm stuck on..."** - Problem solving
- **"There must be a better way..."** - Optimization
- **"What are my options for..."** - Feature design

## Example Topics

### Architecture Decisions

```
/brainstorm handling offline mode
/brainstorm caching strategy for API responses
/brainstorm navigation architecture
/brainstorm state management approach
```

### Implementation Challenges

```
/brainstorm handling complex form validation
/brainstorm real-time updates from server
/brainstorm optimizing list performance
/brainstorm managing multiple concurrent requests
```

### Feature Design

```
/brainstorm user onboarding flow
/brainstorm search with filters
/brainstorm infinite scroll vs pagination
/brainstorm image upload with progress
```

## Output Structure

```
## Brainstorm: [Topic]

### Approach 1: [Name] (Conventional)
- How it works
- Pros/Cons
- Best for

### Approach 2: [Name] (Alternative)
- How it works
- Pros/Cons
- Best for

### Approach 3: [Name] (Unconventional)
- How it works
- Pros/Cons
- Best for

### Comparison Matrix
[Table comparing all approaches]

### Recommendation
[Which to choose and why]
```

## Example

```
User: /brainstorm state management approach

Claude uses brainstormer:

Approach 1: StateFlow + Sealed Class
- Standard pattern, type-safe
- Single state updates
- Best for: Most features

Approach 2: Multi-Flow State
- Granular updates
- More boilerplate
- Best for: Complex UIs

Approach 3: Redux-style Reducer
- Predictable, testable
- Learning curve
- Best for: Large apps

Recommendation: StateFlow + Sealed Class for simplicity.
Redux-style for complex state requirements.
```
