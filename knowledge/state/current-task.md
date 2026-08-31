# Current Task

Status: Allocator workbench redesign implemented and independently verified
Current role: User-directed next scope
Last updated: 2026-08-31

## Goal

Preserve the accepted redesign baseline. Start no new implementation until the user selects the
next scope.

## Required context

- Redesign contract (now implemented): [2026-08-30-allocator-workbench-redesign-spec.md](2026-08-30-allocator-workbench-redesign-spec.md)
- Current observable implementation: [implemented-state.md](implemented-state.md)
- User-facing setup, workflow and evidence: [README.md](../../README.md)

## Open blockers

None. The redesign's Definition of Done items are met and independently re-verified.

## Constraints

- Java remains the source of truth for quantities, statuses and validation; AI may only explain
  Java-computed facts.
- Frontend actionable gating is `ELIGIBLE && RECOMMENDED && !terminal` everywhere; comparison-only
  and rejected candidates never render a decision form.
- Synthetic data and demo thresholds remain explicit `ASSUMPTION` values.
- Deferred capabilities are not implied to exist.

## Completion condition

Already met: `scripts/local.ps1 test` passed (Oracle Backend 527/527, Frontend 106/106, production
build), `test-db-free` passed with the expected conditional-skip count, and the browser acceptance
walkthrough against a real Oracle-backed run confirmed the summary/tabs/master-detail/auto-select/
auto-simulate/approval-confirm/live-refresh flow end to end.

## Next verifiable action

Wait for a user-selected scope. Two known loose ends, neither blocking: (1) `V16` (extra demo seed
volume for screen recording) references product SKUs that were never seeded and is archived
unapplied at `knowledge/archive/unused-migrations/` -- fix or discard on request. (2)
`data/seed/mvp2/*.csv` and `scripts/validate-seed.ps1` were not touched this session and were not
re-verified against the current schema.
