# MVP-2 Phase 5 — React Application Wiring

Status: Accepted
Checkpoint date: 2026-08-29

## Delivered scope

- The React client launches and polls analysis runs, then binds the queue and selected exception to
  one explicit completed run without falling back to stale data.
- The workbench presents exception evidence, donor candidates, deterministic scenario comparisons,
  MANUAL quantity tests, approval/hold/reject actions and canonical decision history.
- API failures use safe ProblemDetail rendering with explicit retry/reset recovery paths. Korean
  labels and formatters cover statuses, risk codes, sources and audit evidence.
- Narrow layouts keep evidence tables in local horizontal-scroll regions; the accepted review also
  verified the initial desktop and 375px render.
- No Backend Java, public API, Flyway migration, DB schema or deterministic business rule was changed
  by this frontend slice.

## Preserved invariants

- The frontend displays Java-owned quantities, statuses, risk levels and validation results; it does
  not independently calculate or choose them.
- Analysis context requires a complete run tuple. Starting a new analysis retires prior work context,
  aborts obsolete requests and does not auto-select a candidate.
- Approval submits the exact feasible simulation contract. UUID idempotency keys are reused only for
  retries of the same normalized request.
- `APPROVED`, `REJECTED` and `EXPIRED` are terminal. Terminal state fails closed from canonical
  history, a terminal POST response or `DECISION_ALREADY_TERMINAL`; `HELD` remains actionable.
- Decision/basis/draft audit identifiers and versions are displayed without exposing idempotency
  internals. Synthetic data and demo `ASSUMPTION` boundaries remain explicit.

## Acceptance evidence

- Codex independently reviewed the final two fixes and found no open production finding.
- `pnpm --dir frontend test`: 10 files / 97 tests passed.
- `pnpm --dir frontend build`: `tsc -b && vite build` passed.
- `git diff --check -- frontend`: exit 0; line-ending conversion warnings only.
- Component/API tests mock the client boundary. No Oracle or live Backend run was required for this
  frontend-only acceptance round.

## Deferred scope

- Optional Phase 6 real LLM provider adapter and any provider-specific operating contract.
- Operational scheduling, stale `RUNNING` recovery and first-JobInstance race normalization.
- Authentication/authorization and external ERP/WMS/TMS integration.
- Phase 7 full-stack verification and README command replay.

## Provenance

- [Owning React wiring specification](../archive/state/2026-08-29-mvp2-react-application-wiring-spec.md)
- [Acceptance current-task snapshot](../archive/state/2026-08-29-phase5-react-acceptance-current-task.md)
- [Acceptance implemented-state snapshot](../archive/state/2026-08-29-phase5-react-acceptance-implemented-state.md)
- [Raw worklog through acceptance](../archive/worklogs/2026-08-29-through-phase5-react-acceptance.md)
