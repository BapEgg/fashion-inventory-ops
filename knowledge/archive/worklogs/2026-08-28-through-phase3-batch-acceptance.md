# Active Worklog — 2026-08

이전 원문은
[`../archive/worklogs/2026-08-28-through-phase3-orchestration-acceptance.md`](../archive/worklogs/2026-08-28-through-phase3-orchestration-acceptance.md)에 보존했다.

## 2026-08-28 — Accept Phase 3 in-memory orchestration

- Role: Codex verification/review.
- Completed: Verified all five prior findings in production code and boundary tests; strengthened
  result-map key ordering and nested candidate-list immutability assertions directly.
- Validation: pure 22/22; Oracle Golden 1/1 with SQL 8 and exact GS-06 reasons; Oracle full
  338/338(skip 0); DB-free 338 total/268 passed/70 skip; failures/errors 0; diff check passed.
- Decision: accepted input-to-immutable-calculation orchestration only; no open finding remains in
  this unit.
- Checkpoint: archived the 125-line task, implemented snapshot and active worklog; no milestone was
  created because Phase 3 output persistence and wiring remain incomplete.
- Open: entity conversion, atomic output persistence and run lifecycle/retry are not implemented.
- Next: Codex specifies the bounded persistence transaction before Claude implementation.

## 2026-08-28 — Specify Phase 3 output persistence

- Role: Codex planning/design.
- Completed: Replaced the hot task with an implementation-ready entity conversion, run lifecycle,
  atomic writer and application executor specification.
- Validation: compared the contract with V1/V6 schema, current entities/repositories and accepted
  calculation records; `git diff --check -- knowledge/state/current-task.md` passed.
- Decisions: compute `expectedShortageQuantity` in the pure orchestrator; use separate
  `REQUIRES_NEW` run transactions and one all-or-nothing output transaction; resolve insert races
  only from a fresh transaction; preserve completed-run idempotency without delete/upsert.
- Open: specified production code and persistence tests are not implemented; Job/Step stays deferred.
- Next: Claude implements only `knowledge/state/current-task.md`, then hands off for Codex review.

## 2026-08-28 — Accept Phase 3 output persistence

- Role: Codex verification/review.
- Completed: Verified run lifecycle, expected-shortage ownership, atomic entity writer and executor;
  closed the metric/candidate index-association validation gap and stale comments directly.
- Validation: pure targets 28/28; Oracle lifecycle 7/7 including two-thread claim, atomicity 1/1,
  persisted Golden 1/1, mapping 8/8; Oracle full 359/359; DB-free forced rerun 359 total/280
  passed/79 skip; failures/errors 0; diff check passed.
- Decision: output persistence unit accepted with no open finding; no schema, policy or public API changed.
- Open: Spring Batch Job/Step wiring still does not call the accepted executor.
- Next: Codex specifies the bounded MVP-2 Job/Step wiring before Claude implementation.

## 2026-08-28 — Implement Phase 3 output persistence

- Role: Claude implementation.
- Completed: `expectedShortageQuantity` moved into the pure orchestrator; `SpInventoryMetric`'s
  MVP-2 constructor now stores it instead of recomputing; added run claim/retry
  (`Mvp2RunLifecycleService`/`Transactions`, `SpAnalysisRun.restart`/`markFailed`), the 9-step
  `Mvp2AtomicOutputWriter`, and the Batch-type-free `Mvp2AnalysisExecutor`.
- Validation: Oracle targets all green (orchestrator 20/20, executor unit 6/6, lifecycle IT 6/6,
  atomicity IT 1/1, golden persisted IT 1/1 incl. idempotent re-run, mapping IT 8/8); Oracle full
  356/356 skip 0; DB-free 356 total/278 passed/78 skip; failures/errors 0; diff check passed.
- Decisions: atomicity/lifecycle/golden ITs are NOT `@Transactional` (REQUIRES_NEW commits bypass
  rollback) and self-clean in `finally` instead, mirroring `ApprovalTransactionAtomicityIT`.
- Open: not yet Codex-reviewed; Job/Step wiring still not started.
- Next: Codex reviews this implementation before the next unit is specified.

## 2026-08-28 — Specify MVP-2 Batch Job/Step wiring

- Role: Codex planning/design.
- Completed: Replaced the hot task with the implementation-ready MVP-2 parameter, Tasklet,
  Job/Step, restart/concurrency and Oracle verification contract.
- Validation: compared the design with the existing MVP-1 JDBC JobRepository configuration,
  accepted executor transaction boundaries and Spring Batch 6 APIs; documentation diff check passed.
- Decisions: use exactly three typed identifying parameters; keep domain and Batch idempotency roles
  distinct; use a non-bean resourceless Step transaction manager so executor-owned JPA transactions
  retain their accepted boundaries; preserve the official Golden domain result with its Batch metadata.
- Open: specified Job/Step production code and tests are not implemented; REST launch remains deferred.
- Next: Claude implements only `knowledge/state/current-task.md`, runs target and full builds, then
  hands off for Codex review.

## 2026-08-28 — Implement MVP-2 Batch Job/Step wiring

- Role: Claude implementation.
- Completed: Added `Mvp2AnalysisJobParameters`/`Validator`, `Mvp2AnalysisTasklet`,
  `Mvp2AnalysisJobConfig` (`mvp2AnalysisJob`/`mvp2AnalysisStep`, resourceless Step transaction
  manager); qualified the MVP-1 Job/Step beans so both configs coexist unambiguously; replaced
  `Mvp2AnalysisExecutorGoldenScenarioIT` with `Mvp2AnalysisJobGoldenScenarioIT`, the sole owner of
  the official `(2026-09-30, MVP-2-GS-V1, MVP-2)` triple, launched through the production Job and
  never deleted.
- Validation: Oracle targets all green (parameters 11/11, tasklet 4/4, context/config 4/4, golden
  Job IT 1/1 re-run twice more for idempotency, retry/concurrency IT 2/2 re-run three more times
  for flakiness); Oracle full 380/380 skip 0 (MVP-1 golden scenario included, no regression);
  DB-free 380 total/295 passed/85 skip; failures/errors 0; diff check passed.
- Decisions: the concurrency IT accepts either `JobExecutionAlreadyRunningException` or a raw
  Oracle `ORA-08177` serialization conflict from `JdbcJobInstanceDao` as a valid "lost the race"
  outcome, since both were observed in real concurrent Oracle runs; production code is unchanged
  either way (out of this unit's scope).
- Open: not yet Codex-reviewed; REST/React/scheduler wiring remain deferred.
- Next: Codex reviews this implementation, in particular the concurrency exception-handling note.

## 2026-08-28 — Review MVP-2 Batch Job/Step wiring

- Role: Codex verification/review.
- Completed: Verified parameter/Tasklet/config/retry/Golden production wiring and corrected the
  executor's stale “wiring is later work” Javadoc directly.
- Validation: pure 15/15; Oracle targeted config/Golden/retry-concurrency 7/7; Oracle full
  380/380(skip 0); DB-free 380 total/295 passed/85 conditional skip; failures/errors 0;
  documentation and repository diff check passed.
- Decision: production wiring has no open code finding; simultaneous first JobInstance creation's
  Oracle `ORA-08177` is deferred to the future REST launcher error-normalization contract.
- Open: P2 — concurrency IT does not hold the first execution in RUNNING and accepts every
  `Exception`, so its passing result does not prove exact already-running rejection.
- Next: Claude replaces the broad race test with a latch-controlled executor spy and exact
  `JobExecutionAlreadyRunningException` plus Oracle cardinality assertions, then requests re-review.

## 2026-08-28 — Fix P2 concurrency-test finding

- Role: Claude implementation.
- Completed: Replaced the broad-catch concurrency test with
  `aSecondLauncherAgainstAGenuinelyRunningJobInstanceIsRejectedWithJobExecutionAlreadyRunning`, using
  a `@MockitoSpyBean` on `Mvp2AnalysisExecutor` and two latches so the second launcher only starts
  after the first's `STARTED` JobExecution row is genuinely committed; asserts the exact exception
  type, executor call count, and JobInstance/JobExecution/domain-run cardinality (1 each). No
  production code, retry test, or cleanup logic changed.
- Validation: the concurrency IT forced-reran 5x on Oracle, 2/2 every time, no flakiness; Oracle
  full 380/380 skip 0; DB-free 380 total/295 passed/85 skip; failures/errors 0; diff check passed.
- Decision: kept the ORA-08177 first-insert-race observation as documentation only, per Codex's
  explicit instruction not to weaken the already-running assertion because of it.
- Open: not yet Codex-reviewed.
- Next: Codex re-verifies the corrected concurrency IT and, if resolved, accepts the whole Job/Step
  wiring unit and specifies the next scope.

## 2026-08-28 — Accept Phase 3 Batch execution

- Role: Codex verification/review.
- Completed: Re-reviewed the latch-controlled concurrency test; the prior P2 is closed and the
  full Phase 3 input, calculation, atomic output and Batch Job/Step boundary is accepted.
- Validation: corrected Oracle retry/concurrency IT 2/2 on three forced Codex runs; Oracle full
  380/380(skip 0); DB-free 380 total/295 passed/85 conditional skip; failures/errors 0;
  repository diff check passed.
- Decision: an existing RUNNING JobInstance is rejected exactly with
  `JobExecutionAlreadyRunningException`; simultaneous first-insert `ORA-08177` normalization stays
  deferred to the future REST launcher contract.
- Open: no Phase 3 finding remains; REST/React/LLM and operational recovery remain deferred.
- Next: checkpoint Phase 3 and define the next bounded application-wiring specification.

