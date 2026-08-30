# Archived Worklog — through Phase 3 foundation review

이번 재리뷰 이전 원문은
[`../archive/worklogs/2026-08-27-through-manual-rereview.md`](../archive/worklogs/2026-08-27-through-manual-rereview.md)에 보존했다.

## 2026-08-27 — Re-review MANUAL finding fixes

- Role: Codex verification/review.
- Completed: Confirmed the eligibility-parity and nullable donor-BASE production fixes and
  their focused regression fixtures against the approved MANUAL contract.
- Validation: expanded Oracle target 99/99; Oracle full 289/289(skip 0); DB-free full
  289 total/238 passed/51 conditional skips; failures/errors 0; `git diff --check` passed.
- Decision: no new production behavior finding; acceptance remains withheld for one explicit
  evidence gap in the stale/no-write Oracle tests.
- Open: `assertRowCountsUnchanged` proves decision/basis/draft only, not the required inventory
  snapshot before/after invariance. Exact test-only fix is in
  [`../state/current-task.md`](../state/current-task.md).
- Checkpoint: archived the oversized current task and active worklog, then removed completed
  review chronology from hot state without promoting the unaccepted increment to a milestone.
- Next: Claude adds only the missing inventory assertion; Codex reruns target and full builds.

## 2026-08-27 — Add missing inventory no-write evidence

- Role: Claude implementation.
- Test-only change (no production Java touched): added
  `ManualQuantityTestExecutorIT.InventorySnapshotState` (a plain record of
  `inventory_snapshot_id`/`store_id`/`sku_id`/`on_hand_quantity`/`reserved_quantity`/
  `input_snapshot_version`) and `captureInventorySnapshotState(storeId, inputSnapshotVersion)`,
  which reads that row via raw `jdbcTemplate` SQL rather than a JPA repository, so the
  before/after comparison cannot be satisfied by Hibernate's first-level cache returning the
  same unmodified managed entity inside the test's own `@Transactional` method — it proves the
  actual persisted row is untouched.
- Applied a before/after capture of both the receiver's and donor's snapshot row (via the new
  helper) around `wrongAnalysisRunIdRejectsAManualTestAndWritesNoRow` and
  `wrongInputSnapshotVersionRejectsAManualTestAndWritesNoRow`'s existing `STALE_RECOMMENDATION`
  assertions, asserting full row equality (id, store, SKU, on-hand, reserved, version) after the
  rejected call, alongside the pre-existing decision/basis/draft row-count assertions.
- No other test or production code changed; no new production-behavior finding surfaced while
  making this change.
- Regression evidence (real, executed this round):
  - `ManualQuantityTestExecutorIT` 8/8; approval package (all `*IT` classes) 30/30 — Oracle.
  - Oracle-backed full Backend build: **289/289**, skip 0, failures/errors 0.
  - DB-free full Backend build: **289 total / 238 passed / 51 Oracle-conditioned skip**,
    failures/errors 0 (unchanged totals — no new test methods, only new assertions inside
    existing Oracle-only test methods).
  - `git diff --check`: exit 0 (only pre-existing LF/CRLF warnings on already-tracked files).
- Next: Codex re-verifies the inventory-invariance evidence is sufficient and, if so, accepts
  the `MANUAL` application API (marks it "— accepted" in `implemented-state.md`) and specifies
  the next unit of work.

## 2026-08-27 — Accept MANUAL application API

- Role: Codex verification/review.
- Completed: Verified the raw-JDBC receiver/donor inventory before/after assertions close the
  final stale/no-write evidence finding; no open MANUAL finding remains.
- Validation: expanded Oracle target 99/99; Oracle full 289/289(skip 0); DB-free full
  289 total/238 passed/51 conditional skips; failures/errors 0; `git diff --check` passed.
- Decision: accepted the side-effect-free `MANUAL` application API. REST/React remain deferred.
- Open: MVP-2 Batch orchestration and result persistence are not implemented.
- Next: Codex specifies Phase 3 Batch before Phase 4 REST and Phase 5 React; see
  [`../state/current-task.md`](../state/current-task.md).

## 2026-08-27 — Start Phase 3 Batch design

- Role: Codex planning/design.
- Completed: Traced the MVP-1 Batch boundary, V6+ input/output Schema, pure calculations and
  approval/MANUAL consumers; fixed the safe job, bulk-read and transaction direction.
- Validation: Static code/Schema/test trace only; no test command was run because production
  code was not changed.
- Decision: Preserve MVP-1 with a separate triple-key MVP-2 job; use fixed-count bulk reads and
  one atomic output transaction until staging-based chunking is separately designed.
- Open: `KNOWN_EVENT` scenario BASE is event-uplifted (GS-02 example 19), while current
  approval/MANUAL basis recalculates the non-uplifted BASE (11), so an unchanged recommendation
  can incorrectly require a change reason.
- Next: User chooses the recommended event-aware shared-basis fix or an explicit rule/schema
  alternative; then Codex completes the implementable Batch specification.

## 2026-08-27 — Approve event basis and find transfer double-count

- Role: Codex planning/design.
- Completed: Recorded user approval for an event-aware shared approval/MANUAL basis, then
  traced all six Golden Scenario inputs through the candidate boundary.
- Validation: Static code/Schema/Seed/test trace only; no test command or production change.
- Decision: `KNOWN_EVENT` Batch, MANUAL and approval must select and apply the same effective
  route-specific BASE uplift without a new Migration.
- Open: GS-01's `APPROVED` open transfer is already added/subtracted in projection, but the
  current loader also marks it as `PENDING_TRANSFER_CONFLICT`, contradicting GS-01's required
  eligible automatic scenarios.
- Next: User decides whether only `REQUESTED` blocks as a pending conflict (recommended), or
  whether the GS-01 expectation/input must change; then Codex completes the Batch spec.

## 2026-08-27 — Finalize Phase 3 Batch specification

- Role: Codex planning/design.
- Completed: Recorded user approval that only `REQUESTED` is a pending lane conflict and
  completed the implementable Phase 3 contract across rules, architecture, physical mapping
  and the Claude handoff.
- Validation: Static code/Schema/Seed/test trace; no production code or Migration changed and
  no test suite was run. `git diff --check` is the documentation validation gate.
- Decisions: Separate triple-key MVP-2 Job; seven bulk read groups; Java calculations; one
  atomic output transaction; REQUIRES_NEW run state; event-aware approval/MANUAL parity;
  APPROVED/IN_TRANSIT projection-only and REQUESTED conflict-only.
- Open: Implementation and actual Oracle/DB-free validation only; no policy blocker remains.
- Next: Claude implements [`../state/current-task.md`](../state/current-task.md), records actual
  row/query/time and test results, then hands the increment to Codex review.

## 2026-08-27 — Phase 3 Batch: foundation layer (entities, shared event helper)

- Role: Claude implementation.
- Scope note: the confirmed Phase 3 spec in `current-task.md` is large (job/identity, input
  adapter, calculation order, persistence/transaction, approval/`MANUAL` parity, golden-scenario
  tests). This round implements and fully tests only the foundation layer everything else depends
  on; the input adapter, orchestration tasklet, job wiring, approval/`MANUAL` event-aware parity
  fix and the `MVP-2-GS-V1` golden test are **not yet implemented** -- see the itemized status in
  `current-task.md`.
- New JPA entities + repositories for the four V6 tables that had no Java mapping before this
  round: `SpDemandEvent`/`SpDemandEventRepository` (rebalance), `SpMetricQualityFlag`/
  `SpMetricQualityFlagRepository` (analysis, child of `SpInventoryMetric`), `SpCandidateReason`/
  `SpCandidateReasonRepository` (rebalance, child of `SpRebalanceRecommendation`),
  `SpRebalanceScenario`/`SpRebalanceScenarioRepository` (rebalance). New enums
  `com.bapegg.stockpilot.demand.MetricQualityFlag` and `com.bapegg.stockpilot.rebalance.DemandEventType`.
- `SpInventoryMetric` gained a full MVP-2 constructor populating all 17 V6 columns from
  `DemandObservationStatistics`/`DemandSignalClassification`/`DemandRateCalculation`/
  `InventoryProjection`/`InventoryExceptionClassification`, plus the `data-model.md` Phase 3
  legacy-column projection (`available_quantity`=currentAvailable, `average_daily_sales`=simple
  observable-day mean, `coverage_days`=currentAvailable/baseline BASE,
  `classification`/`priority` mirror the new exception/severity with `REVIEW_REQUIRED`→
  `NON_ACTIONABLE`). The pre-existing MVP-1 constructor is unchanged.
- `SpRebalanceRecommendation.createMvp2Candidate(...)` -- a new static factory (mirroring
  `SpRebalanceDecision.createMvp2Decision`) supporting nullable quantities for
  `REJECTED`/`NONE`/`COMPARISON_ONLY` candidates; the MVP-1 constructor is unchanged.
- `SpAnalysisRunRepository.findByAnalysisDateAndInputSnapshotVersionAndRuleVersion(...)` added
  for the Job's triple-key no-op/restart/new-run check; no other repository changed.
- New shared pure helper `com.bapegg.stockpilot.demand.RepresentativeEventSelection` extracted
  from `DemandSignalClassification`'s previously-inline event filtering, per
  `business-rules.md` section 10's shared current-basis contract: filters by store/SKU and
  overlap with either of two caller-supplied windows, sorts ascending by
  `(startDate, eventCode)` (new: the `eventCode` tie-break did not exist before -- ties were
  previously resolved by whatever order `events.stream().min(...)` happened to return).
  `DemandSignalClassification.classify` now delegates to it; behavior is otherwise unchanged
  (confirmed by its full existing test suite passing unmodified).
- New tests (all real, executed this round):
  - `RepresentativeEventSelectionTest` (5, pure): earliest-start-date pick, `eventCode` tie-break,
    store/SKU exclusion, window-exclusion, single-window-match inclusion.
  - `DemandSignalClassificationTest` (9, pure, pre-existing, unmodified): confirms the
    representative-event refactor is behavior-preserving.
  - `Mvp2BatchEntityPersistenceMappingIT` (7, Oracle, new): `SpInventoryMetric`'s MVP-2
    constructor round-trips every V6 column and the legacy projection correctly, including the
    `REVIEW_REQUIRED`→`NON_ACTIONABLE`/null-BASE-rate branch; `SpMetricQualityFlag` allows
    multiple rows per metric; `SpRebalanceRecommendation.createMvp2Candidate` supports nullable
    rejected-candidate quantities with `SpCandidateReason` children; `SpRebalanceScenario` maps
    every column and derives `inbound_included_flag` from confirmed inbound only (open-transfer/
    draft quantities alone do not set it); `SpDemandEvent` round-trips and bridges to the pure
    `DemandEvent` record.
- Full regression evidence (real, executed this round):
  - Oracle-backed full Backend build: **301/301**, skip 0, failures/errors 0.
  - DB-free full Backend build: **301 total / 243 passed / 58 Oracle-conditioned skip**,
    failures/errors 0.
  - `git diff --check`: exit 0 (only pre-existing LF/CRLF warnings on already-tracked files).
- No REST/DTO/ProblemDetail/React files touched; no Migration added or modified (V1-V13 untouched,
  all four new entities map onto already-existing V6 columns/tables).
- Next: Claude continues with the input adapter (seven bulk read groups), the MVP-2 job/tasklet
  orchestration (calculation order, persistence, transaction/retry semantics), the approval/
  `MANUAL` event-aware parity fix, and the `MVP-2-GS-V1` golden scenario test -- see
  `current-task.md`'s itemized remaining scope.
