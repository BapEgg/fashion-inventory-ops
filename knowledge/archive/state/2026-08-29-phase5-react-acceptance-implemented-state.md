# Implemented State

Last updated: 2026-08-29 (React application wiring's two follow-up findings fixed, pending Codex re-review)

This hot snapshot records accepted observable behavior and remaining product scope. Completed
baselines live in [`MVP-1.md`](../milestones/MVP-1.md) and
[`MVP-2-Phase-3.md`](../milestones/MVP-2-Phase-3.md).

## Accepted foundations

- Oracle/Flyway V1..V15, deterministic Java analysis/rebalancing rules, MVP-2 Batch orchestration,
  approval transaction persistence, inventory-exception reads, MANUAL quantity-test REST and
  approval/decision REST are accepted.
- Approval owns fingerprint normalization, recommendation-then-donor locking, latest-basis
  recalculation and atomic append-only decision/basis/CREATED-draft storage. It never mutates
  inventory quantities. AI remains explanation-only and demo thresholds are `ASSUMPTION`.

## Accepted approval/decision REST

- `POST /api/rebalancing-decisions` preserves the exact-MVP-1 tuple-less path and routes a complete
  MVP-2 version tuple plus exactly one `Idempotency-Key` to `ApprovalTransactionFacade`.
- New/replayed MVP-2 results return 201/200 plus `Location` and the exact six-field response;
  legacy remains its original seven-field 201 response without `Location`.
- Common non-positive recommendation IDs and invalid legacy quantity/reason shapes are rejected as
  `VALIDATION_ERROR` before repository/state lookup.
- Concurrent legacy sequence-one conflicts are translated after rollback through the existing
  constraint catalog to `DECISION_CONFLICT` 409 rather than 500.
- `GET /api/rebalancing-decisions/{recommendationId}` returns logical PENDING or full ascending
  history. Only MVP-2 APPROVED exposes its stored approval basis and current transfer draft;
  idempotency data is never exposed. Corrupt shapes fail closed and reads use at most four JDBC
  statements regardless of history length.
- Oracle regressions pin exact POST/GET key sets and values, success/failure snapshot and metric
  immutability, atomic decision/basis/draft state, validation-only exact `fieldErrors`, non-validation
  absence of `fieldErrors`, request-id correlation and diagnostic non-disclosure.
- No Migration, accepted business rule, response shape, fingerprint, lock order or inventory
  mutation changed during review fixes.

## Latest independent verification

- Codex DB-free target: controller 17/17 and history query 6/6.
- Codex Oracle target: decision REST 24/24 and API golden 6/6.
- Codex DB-free full: 520 total / 378 passed / 142 conditional skip; no failures/errors.
- Codex Oracle full forced with `--rerun-tasks`: 520/520; skip/failures/errors 0.
- `git diff --check`: exit 0; only line-ending conversion warnings.
- Open findings for the approval/decision REST unit: none.

## React application wiring — third round's two findings fixed, pending Codex re-review

- The MVP-2 type/API/component/test files from the
  [approved specification](../archive/state/2026-08-29-mvp2-react-application-wiring-spec.md) exist,
  including analysis polling, run-bound queue, evidence/candidates/scenarios, MANUAL simulation,
  decision/history, ProblemDetail types, Korean labels and display formatters. The MVP-1-only
  `RecommendationPanel.tsx` is removed and no Backend/Migration change belongs to this slice.
- All findings from the prior 2026-08-29 review (first round) were fixed and Codex independently
  confirmed them: new-analysis work-context retirement, the full `styles.css` rewrite, MVP-2
  exception-type/severity as primary detail fields, canonical-history-derived terminal gating,
  central network-failure normalization, retry/reset recovery actions, gap-not-zero observation
  chart, translated related-evidence identity, expanded audit fields, and Korean risk-code labels.
- Codex's second review then reopened 5 further findings (2 P1, 3 P2), all independently confirmed
  fixed: the terminal
  gate now fails closed the instant `DECISION_ALREADY_TERMINAL` arrives instead of depending on a
  follow-up history GET that could fail or race; the three `RelatedEvidence` tables are each in
  their own local `__scroll` container instead of causing page-level overflow at narrow widths;
  `ObservationEvidence` now shows both `inventorySourceType` and `salesSourceType` in separate
  columns instead of dropping one; `AnalysisContext`'s run status now renders through a new
  exhaustive `analysisRunStatusLabel` instead of the raw enum code; and the canonical audit view now
  also renders `approvalBasisId`/`recommendationVersion`/`decisionContractVersion`.
- Codex's third review confirmed the five-finding round above but reopened 2 further findings (1 P1,
  1 P2), both now fixed: `DecisionPanel.submitBody`'s success path sets the same `forcedTerminal`
  flag whenever the `decide()` response's own `decisionStatus` is itself terminal
  (APPROVED/REJECTED/EXPIRED), instead of only reacting to `DECISION_ALREADY_TERMINAL` -- HELD stays
  explicitly actionable; the success message (`decisionResult`) was moved outside the
  actionable/terminal branch so it stays visible once the form retires; and the two
  `DECISION_ALREADY_TERMINAL` fail-closed regressions were corrected to queue the initial mount
  history fetch as a success before the intended post-error failure/non-terminal response, since the
  prior mock ordering let the mount fetch consume the one-shot rejection instead of the intended
  follow-up call.
- Claude-side validation this round: `pnpm --dir frontend test` -- 10 files / **97 tests, all
  passed** (was 94; +3 for successful-APPROVED/REJECTED/HELD fail-closed coverage). `pnpm --dir
  frontend build` -- `tsc -b && vite build` succeeded. `git diff --check -- frontend`: exit 0 (CRLF
  warnings only). Diff confirms no Backend file changed in this slice.
- Oracle/live-Backend integration was not executed for this Frontend-only slice; component/API tests
  mock the client boundary.
- Not yet independently reproduced by Codex -- the counts above are Claude's own real run, recorded
  as such.

## Not implemented

- Real LLM provider adapter.
- Operational scheduler/stale RUNNING recovery and first-JobInstance race service normalization.
- Authentication/authorization and external ERP/WMS/TMS integration.

