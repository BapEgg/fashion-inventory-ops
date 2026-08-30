# 2026-08 Active Worklog

2026-08-26 지식 압축 이전의 전체 기록은
[`../archive/worklogs/2026-08-before-knowledge-compaction.md`](../archive/worklogs/2026-08-before-knowledge-compaction.md)에
보존한다. Archive는 기본 Resume 대상이 아니다.

## 2026-08-26 — Hot-state compaction and MVP-1 checkpoint

- Role: Codex knowledge architecture
- Completed: Preserved the raw state/logs in cold archive, froze the MVP-1 knowledge
  checkpoint, rewrote current/implemented state as snapshots, and added checkpoint rules.
- Validation: local Markdown link check and `git diff --check` — passed; all three
  StockPilot skills passed `quick_validate.py`; hot-state budgets passed.
- Decisions: State is overwrite-style, worklog is append-only, archive is read only for
  explicit regression/audit work; a knowledge checkpoint does not create a Git tag.
- Open: Candidate/route correctness findings remain unchanged; no Backend code changed.
- Next: Claude fixes the three findings in [`current-task.md`](../state/current-task.md),
  then Codex re-reviews before scenario quantities.

## 2026-08-26 — Fix reviewed candidate-evaluation findings

- Role: Claude implementation
- Completed: Fixed all three review findings on `TransferCandidateEvaluation`.
  `evaluate(...)` now takes explicit `skuId`/`receiverStoreId`/`donorStoreId`
  parameters (one `skuId` represents "same SKU" structurally; different stores are
  validated directly, rejecting blank or equal values). `reasons()` is now wrapped in
  `Collections.unmodifiableSet(...)` so a caller cannot mutate eligibility or the
  representative reason after construction. `LEAD_TIME_TOO_LONG` now triggers on
  `atArrival < 0` instead of `<= 0`: arriving exactly on the expected stockout day is
  "not later than it" per section 7 condition 6's wording, so it must not be rejected.
- Validation: `.\gradlew.bat build --rerun-tasks` (no `DB_URL`) — compile/jar/check
  pass; 125 non-skipped tests pass (up from 119), 0 failures/errors; both Oracle ITs
  skip cleanly. `TransferCandidateEvaluationTest` (19, +6): same-store rejection,
  blank-identifier rejection, `reasons()` immutability (both eligible and ineligible
  results), and the `atArrival == 0` vs `atArrival == -1` boundary pair. Oracle-backed
  verification: not run, not applicable — no DB-touching code changed.
- Open: Scenario quantities (order item 6, section 8), approval-request validation
  (order item 7), section 9's `HIGH` severity completion.
- Next: Codex re-reviews this fix; only after approval does work proceed to section
  8's scenario quantities, per [`current-task.md`](../state/current-task.md).

## 2026-08-26 — Candidate-evaluation fixes accepted

- Role: Codex verification/review
- Completed: Re-reviewed the three candidate-boundary fixes against business rules
  section 7 and accepted the candidate/route increment with no remaining finding.
- Validation: DB-free `.\backend\gradlew.bat -p backend build --rerun-tasks` —
  `BUILD SUCCESSFUL`; 131 total, 125 passed, 6 Oracle-conditioned skips, 0
  failures/errors.
- Decisions: One shared SKU parameter plus distinct-store validation represents the
  candidate pair boundary; reasons remain immutable; arrival-day stock 0 is on time.
- Open: Scenario quantities, two-store result details, approval-request validation,
  and section 9 `HIGH` severity completion.
- Next: Claude implements section 8 scenario quantities from
  [`current-task.md`](../state/current-task.md).

## 2026-08-26 — Phase 2 scenario results (section 8)

- Role: Claude implementation
- Completed: Added `TransferScenarioType`, `TransferScenarioResult`,
  `TransferScenarioSet` to `com.bapegg.stockpilot.demand`. `calculate(...)` sizes
  `NO_ACTION`/`CONSERVATIVE`/`BASE`/`AGGRESSIVE` (min of receiver need, donor
  transferable, route maximum, receiver capacity, floored to the package multiple;
  below the route minimum forces quantity 0 with a warning), and each result now
  also carries both stores' before/after available quantity, coverage days (scale
  12), a simplified risk code, and the constraint warning -- completing section 8's
  closing sentence, which the prior scenario-quantity-only increment had left out.
  Replaced the earlier `TransferScenarioQuantity` (deleted) with the richer
  `TransferScenarioResult`; `TransferScenarioSet.calculate` now takes the donor's
  full `DemandRateCalculation` (not just its HIGH rate) so donor coverage has a
  real BASE rate to divide by.
- Decisions/flagged for review: `receiverRiskCode`/`donorRiskCode` are a
  deliberately simplified two-value indicator (`STOCKOUT_RISK`/`OVERSTOCK` vs.
  `NORMAL`) computed only from the hypothetical after-transfer quantity against
  the same target/protection formulas already in this package -- not a full
  `DemandSignalClassification`/`InventoryExceptionClassification` re-run, since
  those need 28 days of fresh observation data a hypothetical post-transfer state
  doesn't have. Flagged explicitly in code and `current-task.md` for Codex to
  confirm.
- Validation: `.\gradlew.bat build --rerun-tasks` (no `DB_URL`) — compile/jar/check
  pass; 134 non-skipped tests pass (up from 133 with the prior TransferScenarioQuantity
  version), 0 failures/errors; both Oracle ITs skip cleanly. `TransferScenarioSetTest`
  (9) reproduces GS-01's three automatic scenarios (quantity 11 each, receiver
  6->17 available now meeting target exactly, donor 78->67 remaining a healthy
  67-unit-coverage surplus) plus differing low/base/high quantities, package-multiple
  flooring, a below-minimum infeasible case with its warning text, a donor-supply-
  binding case, `VARIABLE`'s `comparisonOnly` flag, and rejection of a `null` route
  or either side's `reviewRequired` rates. Oracle-backed verification: not run, not
  applicable — no DB-touching code changed.
- Open: Approval-request validation (order item 7, section 10), section 9's `HIGH`
  severity completion (candidate evaluation now exists to support it).
- Next: Codex reviews this increment (especially the simplified risk-code
  interpretation); then implement section 10's stale-version/quantity-range/status-shape
  approval validation per [`current-task.md`](../state/current-task.md).

## 2026-08-26 — Scenario-result Codex review

- Role: Codex verification/review
- Completed: Reviewed automatic scenario sizing, two-store results and risk-code
  interpretation against business rules 5~8 and the scenario data contract.
- Validation: DB-free `.\backend\gradlew.bat -p backend build --rerun-tasks` —
  `BUILD SUCCESSFUL`; 140 total, 134 passed, 6 Oracle-conditioned skips, 0
  failures/errors.
- Decisions: Threshold-based hypothetical risk codes are acceptable without a fresh
  28-day classification; the scenario increment is not accepted due to five
  correctness findings recorded in [`current-task.md`](../state/current-task.md).
- Open: Event-window uplift, no-auto signal gating, candidate/actionability gating,
  inbound/transfer/timing evidence, and the floored-minimum warning value.
- Next: Claude fixes the five findings and adds GS-02 plus boundary regressions before
  Codex re-review; approval validation does not start yet.

## 2026-08-26 — Fix all five scenario-result findings

- Role: Claude implementation
- Completed: `TransferScenarioSet.calculate` now takes `DemandConfidence` and
  `analysisDate`/`relevantEvent`/`inboundIncluded` instead of just `signalType`.
  KNOWN_EVENT uplift is applied per auto scenario only when the event overlaps that
  scenario's own arrival-through-target-coverage window (scale 12 immediately after
  multiplying). `confidence == NONE || LOW` (VARIABLE exempt) now throws
  `IllegalStateException` before any scenario is sized, replacing a signal-type
  allowlist that would have missed a quality-flagged `STABLE_REPEAT`. An inactive
  route or either side's `isInputInvalid()` projection is now rejected outright.
  `TransferScenarioResult` gained `leadTimeDays`/`expectedArrivalDate`/
  `inboundIncludedFlag`. The infeasible-quantity warning now names the actual
  floored quantity instead of the pre-floor raw one.
- Validation: `.\gradlew.bat build --rerun-tasks` (no `DB_URL`) — compile/jar/check
  pass; 151 total, 145 passed, 6 Oracle-conditioned skips, 0 failures/errors.
  `TransferScenarioSetTest` (20, +11): exact GS-02 reproduction (uplift
  1.20/1.50/1.80 → quantities 15/19/24), no-uplift-when-window-doesn't-overlap,
  no-uplift-when-not-KNOWN_EVENT, one rejection test per blocked signal/confidence
  combination (including the quality-flagged `STABLE_REPEAT` case), inactive-route
  and both invalid-projection rejections, VARIABLE proceeding despite LOW
  confidence, and the floored-quantity warning text (raw 17/package 10/min 15 ->
  "10"/"15" present, old "17 is below" phrasing gone). Oracle-backed verification:
  not run, not applicable — no DB-touching code changed.
- Open: Codex re-review of this fix; `HIGH` severity completion (section 9,
  candidate evaluation now available to support it); approval-request validation
  (order item 7, section 10); `MANUAL` scenario (explicitly out of scope here).
- Next: Codex re-reviews; then section 9's `HIGH` severity and section 10's
  approval validation per [`current-task.md`](../state/current-task.md).

## 2026-08-26 — Scenario-result fix re-review

- Role: Codex verification/review
- Completed: Re-reviewed the five fixes against business rules 5~8 and the scenario
  data contract; accepted auto-scenario uplift, confidence/actionability guards and
  the corrected minimum warning, but did not accept the increment.
- Validation: DB-free `.\backend\gradlew.bat -p backend build --rerun-tasks` —
  `BUILD SUCCESSFUL`; 151 total, 145 passed, 6 Oracle-conditioned skips, 0
  failures/errors.
- Decisions: Threshold-based post-scenario risk remains acceptable without a fresh
  28-day classification.
- Open: NO_ACTION demand semantics, positive maximum-capacity validation, and complete
  inbound/open-transfer/arrival evidence remain as three correctness findings.
- Next: Claude fixes the three findings in
  [`current-task.md`](../state/current-task.md), then Codex re-reviews before section 9.

## 2026-08-26 — Fix reviewed scenario-result findings (3)

- Role: Claude implementation
- Completed: `NO_ACTION` now uses the real (event-uplifted, when applicable) BASE
  rate for receiver coverage/risk instead of a literal 0 -- only its own transfer
  quantity stays 0. `TransferScenarioSet.calculate` rejects a non-positive
  `receiverMaximumCapacity` (matching V6's `ck_sp_policy_values`). `InventoryProjection`
  now retains its five confirmed-inbound/open-transfer inputs as fields;
  `TransferScenarioResult` exposes them explicitly instead of one
  `inboundIncludedFlag`, and `expectedArrivalDate`'s javadoc now states plainly that
  it is a date-only value with no time-of-day/timezone, so persisting
  `expected_arrival_at TIMESTAMP WITH TIME ZONE` needs an explicit policy decided
  elsewhere, not a silent default.
- Validation: DB-free `.\backend\gradlew.bat -p backend build --rerun-tasks` --
  `BUILD SUCCESSFUL`; 152 total, 146 passed, 6 Oracle-conditioned skips, 0
  failures/errors. `TransferScenarioSetTest` updated (GS-01's `NO_ACTION` now
  asserts real coverage/risk instead of nulls; GS-02 asserts `NO_ACTION`'s uplifted
  rate too) plus a new non-positive-capacity rejection test. Oracle-backed
  verification: not run, not applicable -- no DB-touching code changed.
- Open: Section 9's `HIGH` severity completion, section 10 approval-request
  validation, `MANUAL` scenario (explicitly out of scope).
- Next: Codex re-reviews this fix; if accepted, implement section 9's `HIGH`
  severity then section 10's approval validation per
  [`current-task.md`](../state/current-task.md).

## 2026-08-26 — Scenario-result second re-review

- Role: Codex verification/review
- Completed: Confirmed the NO_ACTION uplifted-BASE semantics, positive capacity guard,
  and factory-created inbound/open-transfer evidence; the increment remains unaccepted.
- Validation: DB-free `.\backend\gradlew.bat -p backend build --rerun-tasks` —
  `BUILD SUCCESSFUL`; 152 total, 146 passed, 6 Oracle-conditioned skips, 0
  failures/errors.
- Decisions: Date-only arrival remains explicitly separate from a future timezone-aware
  persistence mapping.
- Open: The public `InventoryProjection` canonical constructor can create raw evidence
  inconsistent with its projected totals while `isInputInvalid()` remains false.
- Next: Claude adds canonical-constructor invariants and regression tests from
  [`current-task.md`](../state/current-task.md), then Codex re-reviews before section 9.

## 2026-08-26 — Fix reviewed InventoryProjection provenance finding

- Role: Claude implementation
- Completed: `InventoryProjection`'s canonical (compact) constructor -- not just
  `calculate(...)`, its normal entry point -- now rejects negative evidence fields
  and verifies `projectedReceiverBeforeDemand`/`projectedDonorAtDispatch` exactly
  match section 6's formulas derived from `currentAvailable` and the five
  confirmed-inbound/open-transfer fields, using `long` arithmetic and a checked
  `Math.toIntExact` conversion so an out-of-range true sum cannot wrap into a
  coincidental false match. A direct `new InventoryProjection(...)` call bypassing
  the factory can no longer produce a projection whose totals disagree with its own
  evidence.
- Validation: DB-free `.\backend\gradlew.bat -p backend build --rerun-tasks` —
  `BUILD SUCCESSFUL`; 157 total, 151 passed, 6 Oracle-conditioned skips, 0
  failures/errors. `InventoryProjectionTest` (20, +5): mismatched receiver-side and
  donor-side totals rejected, negative evidence fields rejected directly, matching
  values accepted, and an overflow-safe consistency check. All existing
  factory-created projections across `InventoryExceptionClassificationTest`,
  `TransferCandidateEvaluationTest` and `TransferScenarioSetTest` still pass
  unchanged, confirming `calculate(...)`'s own arithmetic already satisfies the new
  invariant. Oracle-backed verification: not run, not applicable — no DB-touching
  code changed.
- Open: Section 9's `HIGH` severity completion, section 10 approval-request
  validation, `MANUAL` scenario (explicitly out of scope).
- Next: Codex re-reviews this fix; if accepted, implement section 9's `HIGH`
  severity then section 10's approval validation per
  [`current-task.md`](../state/current-task.md).

## 2026-08-26 — Scenario-result increment accepted

- Role: Codex verification/review
- Completed: Re-reviewed the canonical `InventoryProjection` invariants and accepted
  the scenario-result/provenance increment with no remaining finding.
- Validation: focused projection/scenario tests and DB-free
  `.\backend\gradlew.bat -p backend build --rerun-tasks` both succeeded; full suite
  157 total, 151 passed, 6 Oracle-conditioned skips, 0 failures/errors.
- Decisions: Direct and factory construction now share the same raw-evidence formulas;
  negative evidence, inconsistent totals and overflow are rejected while consistent
  negative derived projections remain valid non-actionable inputs.
- Open: Section 9 `HIGH` severity, section 10 approval-request validation, and
  `MANUAL` scenario behavior.
- Next: Claude implements section 9 by consuming candidate `eligible()` results per
  [`current-task.md`](../state/current-task.md), then Codex re-reviews.

## 2026-08-26 — V9 한국어 도메인 Comment

- Role: Codex implementation
- Completed: Added V9 without modifying V8; replaced the same 9 table and 149 column
  Comments with concise Korean nouns, adding value descriptions only for constrained
  code, status and flag columns.
- Validation: Oracle-backed `.\backend\gradlew.bat -p backend build --rerun-tasks` —
  157/157 passed; Flyway V9 `success=1`; representative table, ordinary-column and
  code-column Comments matched Oracle readback. `git diff --check` passed.
- Decisions: V9 contains Comment DDL only; no data row, constraint, index or table
  structure change. V1~V8 remain immutable.
- Open: Section 9 `HIGH` severity and the other existing Phase 2 items.
- Next: Resume the `HIGH` severity implementation in
  [`current-task.md`](../state/current-task.md).

## 2026-08-27 — Phase 2 section 9 `HIGH` severity

- Role: Claude implementation
- Completed: `InventoryExceptionClassification.classify(...)` now takes a
  `hasActionableCandidate` boolean -- the caller's already-computed
  `TransferCandidateEvaluation.eligible()` result, not re-derived here. A
  non-positive BASE projection at the earliest arrival is `CRITICAL` regardless of
  candidate availability (section 9's own precedence); a target-coverage shortfall
  that is not already `CRITICAL` is `HIGH` only when an actionable candidate
  exists, otherwise it keeps the prior undetermined (`null`) severity.
  `NON_ACTIONABLE`/`REVIEW_REQUIRED`/`NORMAL`/`OVERSTOCK` are unchanged.
- Validation: `.\backend\gradlew.bat -p backend build --rerun-tasks` (no `DB_URL`)
  -- compile/jar/check pass; 152 non-skipped tests pass (up from 151), 0
  failures/errors; both Oracle ITs skip cleanly. `InventoryExceptionClassificationTest`
  updated (13, +1): the existing CRITICAL test now passes
  `hasActionableCandidate=true` to prove CRITICAL still wins over HIGH; the old
  single "undetermined severity" shortfall test split into a no-candidate
  (`null`) case and a new has-candidate (`HIGH`) case; `NORMAL`/`OVERSTOCK` tests
  now pass `hasActionableCandidate=true` to prove candidate presence doesn't
  affect them. Oracle-backed verification: not run, not applicable -- no
  DB-touching code changed.
- Open: Section 10 approval-request validation, `MANUAL` scenario (explicitly out
  of scope).
- Next: Codex re-reviews this increment; if accepted, implement section 10's
  stale-version/quantity-range/status-shape approval validation per
  [`current-task.md`](../state/current-task.md).

## 2026-08-27 — Section 9 `HIGH` severity accepted

- Role: Codex verification/review
- Completed: Accepted the `hasActionableCandidate` severity increment with no
  remaining finding; candidate rules stay in `TransferCandidateEvaluation`.
- Validation: DB-free `.\backend\gradlew.bat -p backend build --rerun-tasks` —
  158 total, 152 passed, 6 Oracle-conditioned skips, 0 failures/errors; the focused
  classification suite contains 13 passing tests.
- Decisions: `CRITICAL` precedes candidate-aware `HIGH`; existing non-actionable,
  review, normal and overstock branches remain candidate-independent.
- Open: Section 10 approval validation and `MANUAL` scenario behavior.
- Next: Claude implements the pure-Java section 10 validation boundary from
  [`current-task.md`](../state/current-task.md), then Codex re-reviews.

## 2026-08-27 — Phase 2 section 10 approval-request validation

- Role: Claude implementation
- Completed: Added `DecisionStatus`, `ApprovalRequest`, `RecommendationBasis`,
  `ApprovalOutcome`, `ApprovalRequestValidation` to `com.bapegg.stockpilot.demand`.
  `ApprovalRequest`'s canonical constructor enforces per-status quantity/reason
  shape (`PENDING` null quantity, `APPROVED` positive quantity, `HELD`/
  `REJECTED`/`EXPIRED` null quantity plus a required reason code and reason).
  `ApprovalRequestValidation.validate(request, basis)` returns
  `STALE_RECOMMENDATION` when any of the four version fields mismatch, or when an
  `APPROVED` quantity violates the recalculated donor-supply/route/capacity
  limits regardless of whether a reason was given; it throws
  `IllegalArgumentException` when an `APPROVED` quantity differs from the
  recalculated recommended BASE quantity (a change, or any other policy
  exception) without a reason code and reason. No decision row, draft or
  inventory is written -- that transactional layer remains future work.
- Validation: `.\backend\gradlew.bat -p backend build --rerun-tasks` (no
  `DB_URL`) -- compile/jar/check pass; 176 non-skipped tests pass (up from 152),
  0 failures/errors; both Oracle ITs skip cleanly. New tests: `ApprovalRequestTest`
  (8, status-shape boundaries) and `ApprovalRequestValidationTest` (16, using the
  same GS-02 scenario numbers as `TransferScenarioSetTest` for a realistic basis:
  each version-field mismatch, exact-BASE approval needing no reason, changed
  quantity requiring one, quantity exceeding each of donor/route/capacity/package
  limits staying `STALE_RECOMMENDATION` even with a reason, and `HELD`/
  `REJECTED`/`PENDING` version-match/mismatch pass-through). Oracle-backed
  verification: not run, not applicable -- no DB-touching code changed.
- Open: The transactional layer behind this validation (donor row lock, re-fetch,
  append-only persistence, `SP_TRANSFER_DRAFT` creation), `MANUAL` scenario.
- Next: Codex re-reviews this increment. What comes after (the transactional
  layer, `MANUAL` scenario, or wiring MVP-2 calculations into JPA/Batch/REST/
  React) is an architecture decision beyond Phase 2's pure-Java scope and should
  be confirmed with the user rather than assumed.

## 2026-08-27 — Section 10 approval validation review

- Role: Codex verification/review
- Completed: Reviewed the pure-Java request/basis/verdict boundary; the increment
  remains unaccepted with three correctness findings.
- Validation: DB-free `.\backend\gradlew.bat -p backend build --rerun-tasks` —
  182 total, 176 passed, 6 Oracle-conditioned skips, 0 failures/errors; approval
  request tests 8 and validation tests 16 all pass but omit the found boundaries.
- Decisions: Hard candidate constraints remain non-overridable stale failures; an
  explicit policy-exception fact is still needed solely to enforce its reason shape.
- Open: Recomputed candidate actionability is absent, policy exception is not modeled,
  and direct construction permits a null validation outcome.
- Next: Claude fixes the three findings in
  [`current-task.md`](../state/current-task.md), then Codex re-reviews.

## 2026-08-27 — Fix section 10 approval validation findings

- Role: Claude implementation
- Completed: Fixed all three review findings on the section-10 approval boundary.
  `RecommendationBasis` gained a `candidateEligible` field; `ApprovalRequestValidation
  .validate` now checks it first (before any quantity limit) for `APPROVED` requests
  and returns `STALE_RECOMMENDATION` if the caller's freshly recomputed
  `TransferCandidateEvaluation.eligible()` is false, without re-deriving section 7's
  rejection rules. `ApprovalRequest` gained a `policyException` boolean; `needsReason`
  is now `!approvingExactlyTheRecommendedBaseQuantity || request.policyException()`,
  and a hard limit violation still stays `STALE_RECOMMENDATION` even with
  `policyException=true` and a reason present. `ApprovalRequestValidation` gained a
  compact constructor rejecting a `null` outcome.
- Validation: `.\backend\gradlew.bat -p backend build --rerun-tasks` (no `DB_URL`) --
  compile/jar/check pass; 187 total, 181 non-skipped tests pass (up from 176), 0
  failures/errors; both Oracle ITs skip cleanly. Updated `ApprovalRequestTest` (9,
  +1: policy-exception flag round-trips through the constructor) and
  `ApprovalRequestValidationTest` (20, +4: candidate-ineligible-but-in-limits stays
  stale; policy-exception at the exact BASE quantity needs a reason and is valid with
  one; policy-exception plus a reason still cannot override a donor-limit violation;
  direct `new ApprovalRequestValidation(null)` throws). All prior test bodies were
  updated only for the two new constructor positions, not rewritten. Oracle-backed
  verification: not run, not applicable -- no DB-touching code changed.
- Decisions: `candidateEligible` is read-only evidence the caller supplies (never
  re-derived by the validator), keeping section 7's rejection rules owned by
  `TransferCandidateEvaluation` alone, per the same lesson learned in section 9.
- Open: The transactional layer behind this validation (donor row lock, re-fetch,
  append-only persistence, `SP_TRANSFER_DRAFT` creation), `MANUAL` scenario.
- Next: Codex re-reviews this fix round. Only after approval does the increment move
  to "accepted" in `implemented-state.md` and work proceeds beyond section 10.

## 2026-08-27 — Section 10 approval validation accepted

- Role: Codex verification/review and minor correction
- Completed: Verified all three finding fixes and accepted the pure-Java section 10
  increment; added one request invariant so only `APPROVED` can carry
  `policyException=true`.
- Validation: DB-free `.\backend\gradlew.bat -p backend build --rerun-tasks` —
  188 total, 182 passed, 6 Oracle-conditioned skips, 0 failures/errors; request tests
  10 and validation tests 20 pass. `git diff --check` passed.
- Decisions: Recomputed candidate ineligibility and all hard limits remain stale and
  cannot be overridden by a policy-exception reason.
- Open: Transactional approval persistence, `MANUAL` scenario, and MVP-2 application
  wiring remain separate scopes.
- Next: User selects the next scope from [`current-task.md`](../state/current-task.md).

## 2026-08-27 — Approval transaction and persistence design

- Role: Codex planning/design
- Completed: Specified the next approval Backend increment: V10 persistence support,
  append-only JPA mapping, deterministic lock order, current-basis recomputation,
  idempotent decision writes, atomic decision/basis/draft persistence and REST errors.
- Validation: inspected actual V1/V6 Schema and current Entity/service mappings;
  `git diff --check` passed. No application tests run because only specifications changed.
- Decisions: REST error metadata and Oracle constraint mappings live in DB catalogs;
  Java retains deterministic domain branching and two DB-outage fallback responses.
- Open: V10 and transaction code are not implemented; `MANUAL` and React remain later.
- Next: Claude implements [`current-task.md`](../state/current-task.md), then Codex reviews.

## 2026-08-27 — V10 approval transaction migration

- Role: Claude implementation
- Completed: Added `V10__add_approval_transaction_support.sql` per the Codex design
  in `current-task.md`, as the first small unit of the approval-transaction scope.
  `sp_rebalance_decision` gains `decision_request_id` (unique, backfilled
  `LEGACY-{decision_id}`) and `policy_exception_flag` (backfilled `N`), both
  `NOT NULL`. New `sp_approval_basis` snapshots the approval-time version,
  recomputed candidate eligibility, and recommended/supply/route/capacity limits
  1:1 with a decision. New `sp_error_catalog`/`sp_error_constraint_map` seed 8
  error codes (HTTP status, retryable flag, Korean message) and map two known
  Oracle constraint names to them. Added an index on
  `sp_transfer_draft(donor_store_id, sku_id, draft_status)` for the future active-draft
  aggregation query. Korean comments for the new columns/tables are included in V10
  itself per the design note, not deferred to a later comment-only migration.
  No V1-V9 file was touched.
- Completed (minimal entity fix to keep the build green): the new `NOT NULL` columns
  broke the existing MVP-1-only decision insert path in `SpRebalanceDecision`, since
  its constructor had no idempotency key or policy-exception concept. Added
  `decisionRequestId`/`policyExceptionFlag` fields; the legacy 5-arg constructor
  auto-generates `"MVP1-" + UUID.randomUUID()` and fixes `policyExceptionFlag="N"`.
  Also found `policy_exception_flag` is Oracle `CHAR(1 CHAR)` but Hibernate's default
  inference for a `String` column is `VARCHAR2`, which failed schema validation on
  startup; fixed by adding `columnDefinition = "CHAR(1 CHAR)"`. The real
  `Idempotency-Key`-driven contract, and the `@OneToOne`-to-append-only-`@ManyToOne`
  restructuring, remain the next unit (JPA mapping) -- this fix only unblocks V10.
- Validation: existing Oracle `V9`->`V10` upgrade path (already-running container),
  `.\backend\gradlew.bat -p backend build --rerun-tasks` with `DB_URL`/`DB_USERNAME`/
  `DB_PASSWORD` from the repo's `.env` -- `BUILD SUCCESSFUL`; 188/188 tests pass
  including both Oracle ITs (no skips); Flyway recorded V10 `success=1`. Direct
  `sqlplus` readback confirmed `sp_approval_basis` exists, `sp_error_catalog` has 8
  rows, `sp_error_constraint_map` has 2 rows, and existing decision rows read back
  `decision_request_id` as `LEGACY-{id}` with `policy_exception_flag='N'`. Clean
  `V1`->`V10` was not run (no throwaway Oracle instance available this round).
- Open: JPA mapping (append-only `SpRebalanceDecision`, new `SpTransferDraft`/
  `SpApprovalBasis` entities, MVP-2 field mapping on `SpAnalysisRun`/
  `SpInventorySnapshot`/`SpRebalanceRecommendation`), the `@Transactional` use case,
  REST/error contract, and the real two-transaction concurrency test are all still
  unimplemented.
- Next: Claude implements the JPA mapping unit next, per
  [`current-task.md`](../state/current-task.md)'s "Next verifiable action".

## 2026-08-27 — V10 approval migration review not accepted

- Role: Codex verification/review
- Completed: Reviewed V10 and the minimal legacy decision Entity compatibility change
  against the approved persistence and REST error contracts; no code was fixed.
- Validation: DB-free Backend build — 188 total, 182 passed, 6 Oracle skips;
  current Oracle V10 Backend build — 188/188 passed; `git diff --check` passed.
- Decisions: Existing V10 is immutable after application; corrections belong in V11
  before append-only JPA mapping proceeds.
- Open: approval basis lacks required audit fields/FK, the error catalog cannot produce
  the promised ProblemDetail shape, constraint mapping and policy-exception checks are
  wrong/incomplete, and V10 has no dedicated clean/constraint/Comment tests.
- Next: Claude implements the V11-only correction scope in
  [`current-task.md`](../state/current-task.md), then Codex re-reviews.

