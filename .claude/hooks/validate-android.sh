#!/bin/bash
# Android Code Validation Hook
# Runs PreToolUse to catch common anti-patterns before code changes

# Read tool input from stdin
INPUT=$(cat)
FILE_PATH=$(echo "$INPUT" | jq -r '.tool_input.file_path // empty')

# Only validate Kotlin files
if [[ ! "$FILE_PATH" =~ \.kt$ ]]; then
    exit 0
fi

# Check if file exists
if [[ ! -f "$FILE_PATH" ]]; then
    exit 0
fi

ISSUES=""

# Check for force unwraps (!!)
FORCE_UNWRAPS=$(grep -n '!!' "$FILE_PATH" 2>/dev/null | head -5)
if [[ -n "$FORCE_UNWRAPS" ]]; then
    ISSUES="${ISSUES}Force unwraps detected (use ?:, ?.let, or safe calls):\n$FORCE_UNWRAPS\n\n"
fi

# Check for GlobalScope (should use viewModelScope)
GLOBAL_SCOPE=$(grep -n 'GlobalScope' "$FILE_PATH" 2>/dev/null | head -3)
if [[ -n "$GLOBAL_SCOPE" ]]; then
    ISSUES="${ISSUES}GlobalScope detected (use viewModelScope instead):\n$GLOBAL_SCOPE\n\n"
fi

# Check for state-based navigation (common bug)
STATE_NAV=$(grep -n 'LaunchedEffect.*uiState' "$FILE_PATH" 2>/dev/null | head -3)
if [[ -n "$STATE_NAV" ]]; then
    ISSUES="${ISSUES}State-based navigation detected (use Channel + LaunchedEffect(Unit)):\n$STATE_NAV\n\n"
fi

# Check for collectAsState without lifecycle awareness
COLLECT=$(grep -n 'collectAsState()' "$FILE_PATH" 2>/dev/null | grep -v 'collectAsStateWithLifecycle' | head -3)
if [[ -n "$COLLECT" ]]; then
    ISSUES="${ISSUES}collectAsState() detected (use collectAsStateWithLifecycle()):\n$COLLECT\n\n"
fi

# Check for NavController in composables (Navigation 3 violation)
NAV_CONTROLLER=$(grep -n 'navController' "$FILE_PATH" 2>/dev/null | head -3)
if [[ -n "$NAV_CONTROLLER" ]]; then
    ISSUES="${ISSUES}NavController in composable (use callbacks with Navigation 3):\n$NAV_CONTROLLER\n\n"
fi

# Check for WeakReference with action callbacks (causes app freeze)
WEAK_ACTION=$(grep -n 'WeakReference.*action\|WeakReference.*callback\|weakAction\|weakCallback' "$FILE_PATH" 2>/dev/null | head -3)
if [[ -n "$WEAK_ACTION" ]]; then
    ISSUES="${ISSUES}🔴 CRITICAL: WeakReference for action callback detected (causes app freeze!):\n$WEAK_ACTION\nAction callbacks MUST be strong references.\n\n"
fi

# If issues found, print warning but allow operation
if [[ -n "$ISSUES" ]]; then
    echo "⚠️ Android Code Quality Warnings:" >&2
    echo -e "$ISSUES" >&2
    echo "Consider fixing these issues for better code quality." >&2
fi

# Always allow the operation (exit 0)
# Use exit 2 to block if you want strict enforcement
exit 0
