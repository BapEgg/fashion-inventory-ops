# Current Task

Status: MVP-2 accepted and checkpointed
Current role: User-directed next scope
Last updated: 2026-08-30

## Goal

Preserve the accepted MVP-2 baseline. Start no new implementation until the user selects the next
scope.

## Required context

- Final scope and invariants: [MVP-2 checkpoint](../milestones/MVP-2.md)
- Current observable implementation: [implemented-state.md](implemented-state.md)
- User-facing setup, workflow and evidence: [README.md](../../README.md)

## Open blockers

None. Phase 7 and MVP-2 have no open production finding.

## Constraints

- Java remains the source of truth for quantities, statuses and validation; AI may only explain
  Java-computed facts.
- Synthetic data and demo thresholds remain explicit `ASSUMPTION` values.
- Deferred capabilities are not implied to exist.

## Completion condition

Already met for MVP-2: the guarded DB-free and Oracle/full-stack verification matrix passed and
the acceptance evidence was checkpointed.

## Next verifiable action

Wait for a user-selected scope such as README/portfolio refinement, release/commit work, or a
separately specified post-MVP feature.
