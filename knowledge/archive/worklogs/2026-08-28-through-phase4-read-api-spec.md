# Archived Worklog — through Phase 4 inventory-exception read API specification

Phase 3 Batch acceptance까지의 원문은
[`../archive/worklogs/2026-08-28-through-phase3-batch-acceptance.md`](../archive/worklogs/2026-08-28-through-phase3-batch-acceptance.md)에 보존했다.

## 2026-08-28 — Checkpoint accepted Phase 3 Batch

- Role: Codex verification/review and checkpoint.
- Completed: Re-verified the deterministic RUNNING-instance rejection, closed the last P2 and
  checkpointed the accepted input→calculation→atomic output→Job/Step boundary.
- Validation: corrected Oracle retry/concurrency IT 2/2 on three forced runs; Oracle full
  380/380(skip 0); DB-free 380 total/295 passed/85 conditional skip; failures/errors 0;
  diff and checkpoint-link validation passed.
- Decision: preserve exact `JobExecutionAlreadyRunningException` for an existing execution;
  normalize simultaneous first-insert `ORA-08177` in the future REST launcher boundary.
- Checkpoint: [`../milestones/MVP-2-Phase-3.md`](../milestones/MVP-2-Phase-3.md).
- Open: MVP-2 REST/React/LLM application wiring and operational recovery remain deferred.
- Next: Codex specifies the bounded REST analysis launch/status contract before implementation.

## 2026-08-28 — Specify REST analysis launch and status

- Role: Codex planning/design.
- Completed: Replaced the hot task with the implementation-ready dual MVP-1/MVP-2 request,
  synchronous launch, domain status query and DB-backed ProblemDetail specification.
- Validation: compared the contract with the Phase 3 checkpoint, existing MVP-1 controller/service,
  accepted Job behavior and V10/V11 error catalog; documentation diff check passed.
- Decisions: absent/null input version preserves MVP-1 while non-null selects MVP-2; the server owns
  rule version; new MVP-2 runs return 201 and retry/replay 200; Batch metadata stays internal;
  V14 adds only six analysis error rows and Oracle 8177 is classified by SQLException error code.
- Open: specified REST/error production code, V14 and tests are not implemented; UI/LLM stay deferred.
- Next: Claude implements only `knowledge/state/current-task.md` and hands off actual target/full
  validation for Codex review.

## 2026-08-28 — Implement MVP-2 REST analysis launch and status

- Role: Claude implementation.
- Completed: Added `V14` (6 `sp_error_catalog` rows), the `api.error` package (catalog entity/
  repository/service, `ApiException`, `AnalysisApiExceptionHandler`, `RequestIdFilter`),
  `Mvp2AnalysisApplicationService`, `AnalysisLaunchFailureClassifier`, `AnalysisRunQueryService`,
  and extended `AnalysisController`/`AnalysisRunRequest`/`AnalysisRunResponse` with the new GET
  endpoint and additive fields.
- Validation: Oracle targets all green (classifier 11/11, application service 9/9, catalog
  fallback 4/4, V14 IT 1/1, REST IT 9/9 incl. a latch-controlled concurrent-409 case re-run 3x);
  Oracle full 414/414 skip 0; DB-free 414 total/319 passed/95 skip; failures/errors 0; diff check
  passed.
- Decisions: this Spring version throws `ConstraintViolationException` (not
  `HandlerMethodValidationException`) for `@Validated` path-variable violations -- both are
  handled; updated the pre-existing `ApprovalTransactionSchemaIT` row-count assertion to count
  only approval-owned codes, since `sp_error_catalog` is a shared table V14 legitimately extended.
- Open: not yet Codex-reviewed; React/LLM wiring remain deferred.
- Next: Codex reviews this implementation, in particular the two documented deviations from spec.

## 2026-08-28 — Review MVP-2 REST analysis launch and status

- Role: Codex verification/review.
- Completed: reviewed the dual-mode controller, synchronous launcher, classifier, catalog-backed
  ProblemDetail boundary, V14 and tests against the accepted REST specification.
- Validation: pure targets 24/24; Oracle V14+REST 10/10; Oracle full 414/414 skip 0; DB-free
  414 total/319 passed/95 conditional skip; failures/errors 0; `git diff --check` exit 0.
- Decisions: accepted both Spring validation exception handlers and the shared-catalog test scope
  adjustment; did not accept the REST unit because green tests omit four contract/race paths.
- Open: fallback effective-code mismatch, replay 201 race, fieldErrors schema/coverage, classifier
  cause-chain gaps, and V14 exact-metadata assertion gap.
- Next: Claude fixes only [`../state/current-task.md`](../state/current-task.md), adds regressions and
  returns actual Oracle/full validation for Codex re-review.

## 2026-08-28 — Fix MVP-2 REST analysis launch/status findings

- Role: Claude implementation.
- Completed: `ErrorPresentation` now carries the effective fallback code and the handler uses it
  for `type`/`code`; `Mvp2AnalysisApplicationService.launch` derives `created` from
  `!existedBefore && !alreadyCompleted` so a replay race can never report 201; renamed field-error
  `category`→`code` (public `ApiFieldError`, sorted by `(field, code)`), gave `ApiException` an
  optional field-error list, and made `inputSnapshotVersion`/`analysisDate` service-layer
  violations and non-numeric path ids (`MethodArgumentTypeMismatchException`) all produce proper
  field errors; `AnalysisLaunchFailureClassifier` now finds `InputContractViolationException` and
  SQLState-`08` connection failures at any cause-chain depth, in both the thrown and
  failed-execution paths; `V14ErrorCatalogOracleIT` pins the exact title/detail/message text.
- Validation: Oracle targets all green (classifier 14/14, application service 15/15, catalog
  fallback 6/6, new handler unit tests 2/2, V14 IT 1/1, REST IT 15/15); concurrency cases re-run
  3x with no flake; Oracle full 428/428 skip 0; DB-free 428 total/328 passed/100 skip;
  failures/errors 0; diff check passed.
- Decisions: none beyond the five findings themselves -- no new scope, no schema change.
- Open: not yet Codex-reviewed.
- Next: Codex re-verifies all five findings and, if resolved, accepts the REST unit and specifies
  the next scope.

## 2026-08-28 — Re-review REST analysis finding fixes

- Role: Codex verification/review.
- Completed: confirmed all five prior corrections in production code and their regressions, then
  reviewed the effective fallback boundary beyond the newly added happy-path tests.
- Validation: pure targets 33/33; Oracle V14+REST 15/15; Oracle full 428/428 skip 0; DB-free
  428 total/328 passed/100 conditional skip; failures/errors 0; `git diff --check` exit 0.
- Decisions: accepted effective code, replay race, fieldErrors schema, cause-chain and exact V14
  fixes; corrected the recorded suite counts from actual XML.
- Open: direct controller JPA failures bypass analysis ProblemDetail, and validation fieldErrors
  remain attached when catalog resolution changes the effective response to persistence/internal.
- Next: Claude closes only [`../state/current-task.md`](../state/current-task.md) and returns the
  new regression plus full Oracle/DB-free evidence for another Codex review.

## 2026-08-28 — Fix REST analysis P1/P2 boundary findings

- Role: Claude implementation.
- Completed: added `AnalysisLaunchFailureClassifier.classifyDataAccess` and a matching
  `@ExceptionHandler(DataAccessException.class)` so GET/POST domain-repository failures resolve
  through the same ProblemDetail boundary as Job-launch failures (P1); `respond()` now includes
  `fieldErrors` only when the effective resolved code is `VALIDATION_ERROR`, so a fallback to
  persistence/internal never carries a stale validation field list (P2); corrected the P3
  documentation-count gap by reading exact per-class totals straight from the Oracle run's XML.
- Validation: exact XML counts -- classifier 14, application service 13, catalog service 4,
  handler 4 (+2 new), V14 Oracle 1, REST Oracle 16 (+2 new) = pure 35/35, Oracle 17/17; concurrency
  cases re-run 3x with no flake; Oracle full 432/432 skip 0; DB-free 432 total/330 passed/102 skip;
  failures/errors 0; diff check passed.
- Decisions: none beyond the two findings themselves -- no new scope, no schema change.
- Open: not yet Codex-reviewed.
- Next: Codex re-verifies P1/P2 and the corrected counts and, if resolved, accepts the REST unit
  and specifies the next scope.

## 2026-08-28 — Accept REST analysis launch/status

- Role: Codex verification/review.
- Completed: verified direct controller persistence failures use the common ProblemDetail boundary
  and fallback responses suppress validation-only field errors; no production finding remained.
- Validation: pure targets 35/35 and Oracle V14+REST 17/17 before review correction; Oracle full
  432/432 skip 0; DB-free 432 total/330 passed/102 skip; failures/errors 0.
- Correction: added two test-only classifier cases for connection→persistence and generic
  DataAccess→internal; final pure 37/37, Oracle full 434/434 skip 0, DB-free 434 total/332
  passed/102 skip, failures/errors 0; `git diff --check` exit 0.
- Decisions: accepted the bounded REST launch/status unit; preserved all API/schema/Batch behavior.
- Open: remaining Phase 4 read/write APIs, React wiring, LLM provider and operations work.
- Next: specify the MVP-2 inventory-exception list/detail read contract before implementation.
