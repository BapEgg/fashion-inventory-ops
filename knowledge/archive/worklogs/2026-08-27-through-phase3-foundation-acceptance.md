# Archived Worklog — through Phase 3 foundation acceptance

이번 리뷰 이전 원문은
[`../archive/worklogs/2026-08-27-through-phase3-foundation-review.md`](../archive/worklogs/2026-08-27-through-phase3-foundation-review.md)에 보존했다.

## 2026-08-27 — Review Phase 3 Batch foundation

- Role: Codex verification/review.
- Completed: Independently inspected the foundation entities/repositories/factories/shared event
  helper and corrected one misleading event-window Javadoc without changing behavior.
- Validation: Oracle target 21/21; Oracle full 301/301(skip 0); DB-free full 301 total/243
  passed/58 conditional skips; failures/errors 0; `git diff --check` passed.
- Decision: changes requested; foundation is not accepted while three persistence-integrity
  findings remain.
- Open: expected-shortage narrowing overflow, eligible candidate with null route, and scenario
  arrival/version audit values independent of their result/parent.
- Checkpoint: archived the oversized 161-line current task, implemented snapshot and active
  worklog; no milestone was created because this increment is unaccepted.
- Next: Claude applies only the three fixes in
  [`../state/current-task.md`](../state/current-task.md), runs required regression, then hands back
  to Codex.

## 2026-08-27 — Fix Phase 3 foundation review findings

- Role: Claude implementation.
- Finding 1 (narrowing overflow): `SpInventoryMetric.expectedShortageQuantity` changed from
  `Integer` to `Long`; the constructor no longer casts the `long` positive-difference result to
  `int` before assignment. New Oracle test
  `expectedShortageQuantityRoundTripsBeyondIntegerRangeWithoutNarrowingOverflow` forces
  `targetQuantity` to ~3,000,000,000 (past `Integer.MAX_VALUE`, well within `NUMBER(12,0)`) and
  confirms the exact value round-trips. Also caught and fixed two existing test assertions
  (`assertEquals(int, Long)`) that would have silently always-failed at runtime after the field
  type change (`Integer(12).equals(Long)` is `false` even when numerically equal) -- both now
  compare via `.longValue()`.
- Finding 2 (ELIGIBLE candidate route invariant): `SpRebalanceRecommendation.createMvp2Candidate`
  now throws `IllegalArgumentException` when `candidateStatus == ELIGIBLE` and `routeId == null`;
  `REJECTED`/`NONE` candidates may still omit it. New pure test class
  `SpRebalanceRecommendationTest` (3 tests, no Spring/DB) covers the rejection and both allowed
  shapes. The two scenario-mapping Oracle tests now create a real `SpStoreTransferRoute` and use
  its id for their `ELIGIBLE` fixtures instead of `null`.
- Finding 3 (scenario audit derivation): `SpRebalanceScenario`'s constructor dropped the
  `expectedArrivalAt`/`candidateVersion` parameters entirely -- it now derives
  `expected_arrival_at` from `result.expectedArrivalDate()` combined with `00:00 Asia/Seoul`
  itself, and `candidate_version` from `recommendation.getCandidateVersion()`, so no caller can
  inject a mismatched audit value. Both scenario-mapping Oracle tests updated to call the new
  2-arg-fewer constructor and assert the derived values (including
  `eligible.getCandidateVersion()` equality, not just a literal).
- Kept Codex's own `RepresentativeEventSelection` Javadoc clarification (shared full-plan-horizon
  wording) untouched, per the constraint.
- Regression evidence (real, executed this round):
  - `SpRebalanceRecommendationTest` 3/3 (new, pure), `RepresentativeEventSelectionTest` 5/5,
    `DemandSignalClassificationTest` 9/9 (both unmodified).
  - `Mvp2BatchEntityPersistenceMappingIT` 8/8 (Oracle; 7 existing + 1 new overflow test).
  - Oracle-backed full Backend build: **305/305**, skip 0, failures/errors 0.
  - DB-free full Backend build: **305 total / 246 passed / 59 Oracle-conditioned skip**,
    failures/errors 0.
  - `git diff --check`: exit 0 (only pre-existing LF/CRLF warnings on already-tracked files).
- No Migration, REST/React, or input-adapter/orchestration/approval-parity code touched -- this
  round is scoped strictly to the three findings, per `current-task.md`'s constraint.
- Next: Codex re-verifies the three findings are closed and the full suite is still green; once
  the foundation layer is accepted, Claude resumes the archived Phase 3 spec starting with the
  input adapter.
