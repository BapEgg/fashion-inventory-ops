# Active Worklog — 2026-08

이전 18개 entry 원문은
[`../archive/worklogs/2026-08-27-through-manual-review.md`](../archive/worklogs/2026-08-27-through-manual-review.md)에 보존했다.

## 2026-08-27 — Review MANUAL quantity testing

- Role: Codex verification/review.
- Completed: Reviewed shared basis/projection extraction, pure manual evaluation,
  transaction/lock/error boundaries and Oracle evidence against the detailed contract.
- Validation: targeted Oracle 95/95; Oracle full 285/285(skip 0); DB-free full
  285 total/237 passed/48 conditional skips; `git diff --check` passed.
- Diagnostic: a temporary parity regression test reproduced BASE=0 with
  `approval.eligible=false` but `manual.feasible=true` (1/1 failed as expected); source removed.
- Decisions: approval withheld for two findings—eligibility parity and donor BASE rate becoming
  a new precondition on the accepted approval path.
- Open: Exact fixes and missing stale/no-write assertions are owned by
  [`../state/current-task.md`](../state/current-task.md).
- Checkpoint: preserved the oversized pre-review state/worklog under `knowledge/archive` and
  compacted hot state without promoting this unaccepted increment to a milestone.
- Next: Claude fixes only the two findings; Codex reruns the targeted and full suites.

## 2026-08-27 — Fix MANUAL quantity-test review findings

- Role: Claude implementation.
- Finding 1 (eligibility parity): `ManualQuantityEvaluation.calculate` now sources
  eligibility from `ApprovalBasisRecalculation.eligible()` (candidate structural reasons
  AND `recommendedBaseQuantity > 0`) instead of `candidateEvaluation().eligible()` alone,
  so a BASE=0 basis with no structural rejection reason is `CANDIDATE_INELIGIBLE`/infeasible
  in preview, matching the real approval's `STALE_RECOMMENDATION` outcome at the same basis.
- Finding 2 (donor BASE not a new approval precondition): `CurrentApprovalBasisLoader.load`
  no longer treats `donorMetric.getBaseDemandRate() == null` as stale; only receiver BASE and
  donor HIGH being null still trigger `STALE_RECOMMENDATION`. `TransferEffectProjection`
  already tolerated a null rate (returns a null coverage figure), so no other production code
  changed.
- New regression evidence:
  - `ManualQuantityEvaluationTest.zeroReceiverNeedWithNoStructuralRejectionReasonIsStillCandidateIneligible`
    (new): receiver already exceeds target so `recommendedBaseQuantity=0` with empty structural
    candidate reasons — confirms `CANDIDATE_INELIGIBLE`/infeasible instead of the pre-fix
    false-positive `feasible=true`.
  - `ManualQuantityEvaluationTest.belowRouteMinimumViolationForcesTheSuggestionToZero` (existing):
    this fixture's `recommendedBaseQuantity` was already 0 under the correct formula; updated its
    expected violations from `[BELOW_ROUTE_MINIMUM]` to `[CANDIDATE_INELIGIBLE, BELOW_ROUTE_MINIMUM]`
    to match the now-correct eligibility source (this is a test-assertion fix, not new production
    behavior — the fixture always had BASE=0, the old assertion simply never checked eligibility).
  - `ManualQuantityTestExecutorIT.wrongAnalysisRunIdRejectsAManualTestAndWritesNoRow` (new),
    `.wrongInputSnapshotVersionRejectsAManualTestAndWritesNoRow` (new): both `STALE_RECOMMENDATION`
    with zero decision/basis/draft rows written, per the required regression evidence list.
  - `ManualQuantityTestExecutorIT.nullDonorBaseRateDoesNotBlockApprovalOrManualPreviewButNullsOnlyDonorCoverage`
    (new, Oracle): donor BASE=null/HIGH=1 schema-legal fixture — manual preview is feasible with
    only `projection().donorBeforeCoverageDays()`/`donorAfterCoverageDays()` null (receiver
    coverage stays non-null), and the real `facade.execute(APPROVED)` on the same basis succeeds
    without throwing.
- Full regression (all targeted classes rerun): `ManualQuantityEvaluationTest` 14/14,
  `ManualQuantityTestCommandTest` 8/8, `ManualQuantityTestExecutorIT` 8/8,
  `ApprovalTransactionExecutorIT` 15/15, `ApprovalTransactionConcurrencyIT` 5/5,
  `ApprovalTransactionAtomicityIT` 2/2, `TransferScenarioSetTest` 21/21 — all pass.
- Full build evidence (real, executed this round):
  - Oracle-backed full Backend build: **289/289**, skip 0, failures/errors 0.
  - DB-free full Backend build: **289 total / 238 passed / 51 Oracle-conditioned skip**,
    failures/errors 0.
  - `git diff --check`: exit 0 (only pre-existing LF/CRLF warnings on already-tracked files).
- No REST/DTO/ProblemDetail/React/Migration files touched; V1–V13 untouched.
- Next: Codex re-verifies both findings are closed and the full suite is still green, then
  either accepts (relabel the `MANUAL` section "— accepted" in `implemented-state.md`) or
  raises further findings.
