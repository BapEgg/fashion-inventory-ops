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
5. Read the newest relevant section in `knowledge/worklogs` only when the current state is insufficient.

Do not read every knowledge document by default.

## Resume summary

Before changing files, identify:

- current role and goal
- confirmed constraints
- repository implementation state
- blocking information
- next verifiable action

Prefer actual code, DB Migration, configuration and executed test results over stale prose. Do not infer authority to change scope or external systems.

