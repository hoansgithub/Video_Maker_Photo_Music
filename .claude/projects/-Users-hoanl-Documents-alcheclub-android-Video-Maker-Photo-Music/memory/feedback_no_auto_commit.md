---
name: no_auto_commit
description: Never commit automatically — always wait for explicit user instruction
type: feedback
---

Never run `git commit` automatically after completing a task or code quality check.

**Why:** User explicitly said "never commit until I tell you to."

**How to apply:** Always wait for the user to say "commit" or "commit all" before running any git commit command. Finishing a feature, passing a build check, or completing a code review does NOT imply permission to commit.