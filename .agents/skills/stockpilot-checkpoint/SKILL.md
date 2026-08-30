---
name: stockpilot-checkpoint
description: Compact StockPilot hot state and rotate worklogs at completed MVP or Phase boundaries, or when knowledge size budgets are exceeded, while preserving raw history in cold archive.
---

# StockPilot Checkpoint

Use this skill when an MVP or Phase is accepted, or when any hot-state budget in
`knowledge/00-start-here.md` is exceeded.

## Preserve before compacting

1. Check `git status --short` and distinguish user/code changes from knowledge changes.
2. Verify the latest accepted state against code, Migration, configuration and actual
   test results. Do not promote an unaccepted review increment into a milestone.
3. Copy the active worklog and any hot-state document being replaced to dated files
   under `knowledge/archive`. Do not delete raw history in the same change.

Archive files are cold evidence. They are not added to the default Resume path.

## Write the checkpoint

For a completed MVP or Phase, create one `knowledge/milestones/<name>.md` containing:

- delivered scope and compatibility boundary
- final observable behavior and durable invariants
- final executed validation evidence
- explicitly deferred or unimplemented work
- links to the archived raw evidence

Do not copy session chronology, superseded findings, chat text or speculative plans.
A knowledge checkpoint does not create a Git tag or release unless the user separately
authorizes that operation.

## Rewrite hot state

- Replace `knowledge/state/implemented-state.md` with the current repository snapshot.
  Keep final behavior, validation status, open findings and unimplemented scope; remove
  resolved review chronology.
- Replace `knowledge/state/current-task.md` with only the current role, goal, required
  context, open blockers, constraints, completion condition and next verifiable action.
- Rotate the active worklog and add one compact entry linking to the checkpoint/archive.

State files are overwrite-style snapshots. Worklogs are append-only history. Never
append a completed session narrative to a state file.

## Verify

- Confirm every hot-state and milestone link resolves.
- Confirm current-task and implemented-state agree on role, open findings and next work.
- Confirm archives exist before removing duplicated hot text.
- Measure the budgets in `knowledge/00-start-here.md` and report any exception.
- Run the skill validator when this workflow changes a skill.

If code, Migration and accepted specification conflict, stop and report the conflict;
compaction must not silently choose a new business rule.
