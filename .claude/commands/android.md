---
description: Quick Android development task using android-developer agent
allowed-tools: Read, Edit, Write, Bash(./gradlew:*)
---

Use the **android-developer** agent to: $ARGUMENTS

Remember to follow all Android rules:
- Navigation 3 with NavDisplay + rememberNavBackStack
- NavKey routes with @Serializable
- backStack.add() / backStack.pop() for navigation
- Channel for one-time events (Activity launches, toasts)
- collectAsStateWithLifecycle()
- viewModelScope for coroutines
- No force unwrap (!!)
- Sealed class for UI state
