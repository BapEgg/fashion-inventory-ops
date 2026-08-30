---
name: stockpilot-resume
description: Resume StockPilot planning, implementation, or review from the minimum current repository state without loading the full knowledge base.
---

# StockPilot Resume

Use this skill at the beginning of a StockPilot session.

## Read

1. Check `git status --short` and the actual repository tree.
2. Read `AGENTS.md` and `knowledge/state/current-task.md`.
3. Read `knowledge/state/implemented-state.md`.
4. Read only the document required by the current task:
   - scope, API or architecture work: `knowledge/project.md`
   - calculation, Seed or AI-boundary work: `knowledge/business-rules.md`
   - Oracle Schema or data loading work: `knowledge/data-model.md`
5. Read a milestone checkpoint only when the hot state links to that baseline and its
   durable compatibility boundary matters to the task.
6. Read the newest relevant active worklog entry only when the hot state is insufficient.
   Search `knowledge/archive` only for explicit regression investigation or audit work.

Do not read every knowledge document, an entire worklog, or archive history by default.

## Resume summary

Before changing files, identify:

- current role and goal
- confirmed constraints
- repository implementation state
- blocking information
- next verifiable action

Prefer actual code, DB Migration, configuration and executed test results over stale prose. Do not infer authority to change scope or external systems.

If `current-task.md` contains completed session history, or `implemented-state.md`
contains superseded review chronology, treat that as a knowledge-maintenance defect;
do not solve it by loading more history. Use `stockpilot-checkpoint` when the size
budget or a milestone boundary requires compaction.
