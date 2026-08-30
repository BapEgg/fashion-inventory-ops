---
name: stockpilot-worklog
description: Record a concise StockPilot role handoff after meaningful work without duplicating specifications or raw conversation history.
---

# StockPilot Worklog

Use this skill after meaningful work or before transferring the task to another role.

## Update order

1. Replace obsolete statements in `knowledge/state/implemented-state.md` with current
   observable repository and validation facts.
2. Rewrite `knowledge/state/current-task.md` around the next role, goal, blockers and
   completion condition, removing completed work from the hot path.
3. Append one compact entry to `knowledge/worklogs/YYYY-MM.md`.

State documents are snapshots, not append-only logs. Never paste the session entry,
resolved finding chronology or file-by-file narration into either state document.

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

## Compaction trigger

Before finishing, check the hot-state budgets in `knowledge/00-start-here.md`. If an
MVP/Phase was accepted or a budget is exceeded, use `stockpilot-checkpoint` to preserve
the raw files and rewrite the hot state. Do not truncate or delete history in place.
