---
name: stockpilot-worklog
description: Record a concise StockPilot role handoff after meaningful work without duplicating specifications or raw conversation history.
---

# StockPilot Worklog

Use this skill after meaningful work or before transferring the task to another role.

## Update order

1. Update `knowledge/state/implemented-state.md` with observable repository and validation facts.
2. Update `knowledge/state/current-task.md` with the next role, goal, blockers and completion condition.
3. Append one compact entry to `knowledge/worklogs/YYYY-MM.md`.

## Entry format

```markdown
## YYYY-MM-DD — short session title

- Role: ...
- Completed: ...
- Validation: command — result
- Decisions: ...
- Open: ...
- Next: ...
```

Keep the entry under 20 lines. Link to the owning specification instead of copying it. Do not store raw prompts, chat transcripts, secrets, speculative plans or file-by-file narration.

If no meaningful state changed, do not create a Worklog entry.

