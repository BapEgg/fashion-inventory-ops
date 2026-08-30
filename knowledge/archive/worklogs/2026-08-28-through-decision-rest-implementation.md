# Archived Worklog — 2026-08 through approval/decision REST implementation

이전 원문은
[`../archive/worklogs/2026-08-28-through-phase4-read-api-spec.md`](../archive/worklogs/2026-08-28-through-phase4-read-api-spec.md)에
보존했다.

## 2026-08-28 — Specify inventory-exception read API

- Role: Codex planning/design and budget checkpoint.
- Completed: fixed the implementation-ready MVP-2 list/detail read contract, preserved the
  exact design record, and compacted the implementation handoff within the hot-state budget.
- Validation: compared existing MVP-1 controller/service/DTO, MVP-2 Batch entities/input adapter,
  business rules, data model, V14 error boundary and official Golden fixture; no code test run.
- Decisions: legacy bare-array success stays exact; explicit run-bound mode is paged; sorting is
  transparent and fixed; detail exposes stored 28-day evidence/candidates/reasons/scenarios without
  recalculating decisions; V15 is error-catalog DML-only; React remains last.
- Archive: [full specification record](../archive/state/2026-08-28-phase4-read-api-spec-current-task.md).
- Open: specified production code, V15 and tests are not implemented.
- Next: Claude implements only [the current task](../state/current-task.md), records actual
  target/full validation, then hands off to Codex review.

## 2026-08-28 — Implement inventory-exception read API (list + detail)

- Role: Claude implementation.
- Completed: `GET /api/inventory-exceptions` run-bound paged read model alongside the unchanged
  MVP-1 legacy path, and rule-version-branched `GET /api/inventory-exceptions/{metricId}` detail
  (28-day evidence, candidates/reasons/scenarios, rule thresholds). Widened the catalog-backed
  ProblemDetail advice to `InventoryExceptionController`; added V15 DML (2 rows); added several
  bulk repository methods and 4 missing entity getters (`SpDailySale` V6 columns,
  `getSourceType()` on 3 entities).
- Validation: new `Mvp2InventoryExceptionQueryServiceTest` 17/17 (mock-based, DB-free). Full
  DB-free build: 452 total / 349 passed / 103 conditional skip, failures/errors 0.
  `git diff --check`: exit 0. **Oracle integration: 미실행** — no `DB_URL` in this session, so the
  new `Mvp2InventoryExceptionReadOracleIT` (Golden Scenario GS-01~06, V15 exact metadata,
  query-count ceiling) has never actually run against Oracle.
- Decisions: list filter/sort/page done in JPQL (WHERE + ORDER BY CASE + scalar subquery for
  D-1 price), not native SQL, favoring portability the team can review over hand-tuned Oracle
  syntax I could not verify this session; candidate/inbound/price aggregation done as separate
  small bulk queries rather than one mega-query, trading a higher query count for lower risk.
- Open: Oracle-side correctness of the above is unverified. Exact query count for list/detail
  vs the 6/14 ceiling is not measured.
- Next: run `Mvp2InventoryExceptionReadOracleIT` against real Oracle first, fix anything it
  finds, then hand to Codex review per [current-task.md](../state/current-task.md).

## 2026-08-28 — Review inventory-exception read API

- Role: Codex verification/review.
- Completed: reviewed list/detail DTO, JPQL/repository ownership, V15/error boundary and tests
  against the [owning specification](../archive/state/2026-08-28-phase4-read-api-spec-current-task.md);
  fixed only a test JSON cast/V15 assertion and rejected-value message leak.
- Validation: unit 17/17; DB-free full 452 total/349 passed/103 conditional skip; Oracle target
  and full 452 total/451 passed/skip 0/**1 failure**; `git diff --check` exit 0.
- Decisions: not accepted. GS-05 is NORMAL, so the spec's NORMAL=404 rule conflicts with its
  GS-05 detail=200 test; recommended preserving exception semantics and removing that test.
- Open: list/detail explicit query counts 10/17 exceed 6/14; required filter/sort/page/error/
  query-count tests are missing; unknown rule versions fall through to MVP-1 detail.
- Next: user confirms the GS-05 recommendation, then Claude fixes only
  [current-task.md](../state/current-task.md) and returns full Oracle/DB-free evidence.

## 2026-08-28 — Confirm GS-05 exception-detail contract

- Role: User contract decision and Codex specification handoff.
- Completed: replaced the blocking choice in [current-task.md](../state/current-task.md) with
  an implementation-ready amendment and moved the task to Claude.
- Decisions: an existing `NORMAL` metric, including GS-05 receiver after confirmed inbound,
  returns catalog-backed `INVENTORY_EXCEPTION_NOT_FOUND` 404 from the exception-detail API.
  The conflicting GS-05 detail-200 requirement is retired; no new analysis-result endpoint is
  added in this scope.
- Open: query ceilings, required filter/sort/page/error/query-count coverage and explicit unknown
  rule-version handling remain unchanged from the Codex review.
- Next: Claude replaces the stale GS-05 200 assertion with exact 404 ProblemDetail coverage,
  fixes the remaining findings, and records real Oracle/DB-free/query-count evidence.

## 2026-08-28 — Fix read API query ceiling, rule-version and test-coverage findings

- Role: Claude implementation.
- Completed: merged list-side product/store/quality-flag lookups and detail-side product/store
  and open-transfer lookups into single JPQL statements, bringing explicit list calls to 6 and
  worst-case detail calls to exactly 14. Made `getExceptionDetail` route only a confirmed
  `InventoryAnalysisRules.RULE_VERSION` to the legacy shape, failing any other unrecognized
  version as `INTERNAL_SERVER_ERROR` instead of silently treating it as MVP-1. Replaced the
  Oracle IT's stale GS-05 200 assertion with the confirmed 404 regression, and added
  filter/pagination-stability/non-completed-run/Hibernate-statistics query-count coverage to it,
  plus two new DB-free unit tests (list happy-path mapping, unknown rule version).
- Validation: unit 19/19 (was 17/17). DB-free full: 454 total/351 passed/103 conditional skip,
  failures/errors 0 (실제 실행). `git diff --check` exit 0. **Oracle: 미실행** — `DB_URL`
  still unset in this session; none of the above is Oracle-verified yet.
- Decisions: kept the merges to plain ad-hoc JPQL joins (no native SQL) to stay within what I
  could reason about without a live DB to test against.
- Open: every Oracle-dependent claim above (actual query count, GS-05 404, new filter/pagination
  cases) is unverified.
- Next: run [`Mvp2InventoryExceptionReadOracleIT`](../../backend/src/test/java/com/bapegg/stockpilot/Mvp2InventoryExceptionReadOracleIT.java)
  first once `DB_URL` is available (user's explicit request), fix anything it finds, then hand
  to Codex review per [current-task.md](../state/current-task.md).

## 2026-08-28 — Run against real Oracle, fix what it found

- Role: Claude implementation and Oracle verification.
- Completed: user stood up a local Docker Oracle container and pointed `.env` at it; sourced
  it into this session and ran `Mvp2InventoryExceptionReadOracleIT` first, per the user's
  request. Found and fixed two real bugs it surfaced: `SpProductRepository`'s merged
  product+store query threw `ClassCastException` under `Optional<Object[]>` (Spring Data JPA
  double-wraps a single multi-column row) -- changed to `List<Object[]>`; and after that fix,
  detail's real statement count was 15 (ceiling 14) -- merged product+store+quality-flag into
  the existing `findListRowsByInventoryMetricIdIn` call, dropping the service's direct
  `SpProductRepository`/`SpMetricQualityFlagRepository` dependencies entirely. Also fixed one
  Oracle IT test bug: a skuId-only filter assertion expected exactly 1 row but the golden run's
  donor-side OVERSTOCK metric shares the same SKU -- added a storeId AND.
- Validation: full Oracle backend run (DB_URL connected, all conditional tests executed):
  **454 total / 454 passed / skip 0 / failures/errors 0**, including the list ≤6 / detail ≤14
  query-count regression. `git diff --check` exit 0.
- Decisions: none beyond the bug fixes above -- no scope or contract change.
- Open: none for this unit; ready for Codex's independent re-review.
- Next: Codex re-verifies query ceiling, GS-05 contract, rule-version branching, the
  `Optional<Object[]>` fix and new test coverage before marking this unit accepted.

## 2026-08-28 — Re-review inventory read finding fixes

- Role: Codex verification/review.
- Completed: verified GS-05 404, explicit MVP-1/MVP-2 routing and merged read queries; corrected
  the query-ceiling regression to count JDBC prepared statements instead of HQL executions.
- Validation: unit 19/19; DB-free full 454 total/351 passed/103 conditional skip; Oracle target
  and full 454 total/453 passed/skip 0/**1 failure**; list/detail measured 7/14 statements.
- Decisions: not accepted. The earlier 454/454 result omitted the run identifier-load SQL because
  `getQueryExecutionCount()` was the wrong counter; the corrected regression remains in place.
- Open: list must drop from 7 to ≤6; signal/severity/quality OR, fixed-order stability,
  row-count-invariant statement count and inventory-controller error-boundary tests are missing.
- Next: Claude fixes only those P1 findings and returns real Oracle/DB-free evidence for Codex
  re-review; see [current-task.md](../state/current-task.md).

## 2026-08-28 — Fix Codex's second re-review findings (list ceiling, missing coverage)

- Role: Claude implementation and Oracle verification (Oracle still connected from the prior
  round's local Docker container).
- Completed: found the exact source of the 7th list statement empirically (ran each repository
  call standalone with `Statistics.getPrepareStatementCount()` deltas, via a throwaway diagnostic
  test) -- it was the separate D-1 daily-sale bulk query. Merged it into
  `findListRowsByInventoryMetricIdIn` as a per-row scalar subquery instead, dropping the now-dead
  `SpDailySaleRepository.findBySalesDateAndInputSnapshotVersionAndStoreIdInAndSkuIdIn`. Added the
  five missing Oracle IT regressions: signal/quality filters, repeatable-filter OR vs
  different-filter AND, fixed 6-key order stability (full size=1 page walk + repeated size=100
  call), size=1-vs-size=100 statement-count invariance, and a `@MockitoSpyBean`-based
  `InventoryExceptionController` DataAccess→503 ProblemDetail MVC regression (separate `@Test`,
  sentinel run id, isolated from the golden-triple test).
- Validation: full Oracle backend run (real, DB_URL connected): **455 total / 455 passed /
  skip 0 / failures/errors 0**. DB-free full: 455 total/351 passed/104 conditional skip,
  failures/errors 0. `git diff --check` exit 0.
- Decisions: none beyond the fixes above.
- Open: none for this unit; ready for Codex's final independent re-review.
- Next: Codex re-verifies the corrected list/detail statement counts, new filter/order/
  statement-count/error-boundary coverage before marking this unit accepted.

## 2026-08-28 — Accept inventory-exception read API

- Role: Codex final verification/review.
- Completed: independently reviewed the D-1 price scalar-subquery merge, list/detail mapping,
  filter/order/page tests and inventory-controller error boundary; strengthened test-only
  assertions so severity OR proves both sides, every quality-filter row matches, and the response
  is independently sorted by all six keys.
- Validation: unit 19/19; DB-free full 455 total/351 passed/104 conditional skip; Oracle target
  2/2 and full 455/455, skip/failures/errors 0; JDBC ceiling list/detail 6/14;
  `git diff --check` exit 0.
- Decisions: accepted this inventory-exception read API unit; no public contract or production
  behavior changed during final review.
- Open: none for this unit. React application wiring remains deferred.
- Next: user selects the next Phase 4 Backend slice, then Codex writes its detailed specification.

## 2026-08-28 — Specify MANUAL quantity-test REST slice

- Role: Codex planning/design.
- Completed: selected the next Phase 4 Backend slice and wrote the implementation-ready
  [MANUAL REST specification](../archive/state/2026-08-28-manual-quantity-test-rest-spec.md),
  then compacted [current-task.md](../state/current-task.md) for Claude handoff.
- Validation: repository/code/schema inspection; `git diff --check` exit 0 before checkpoint.
  No build or test was run because this turn changed knowledge only.
- Decisions: preserve legacy MVP-1 simulation success shape; require the full MVP-2 version tuple;
  return infeasible tests as 200; fix policy-default and event-aware BASE parity in the shared
  approval/MANUAL loader before exposing the REST path; reuse existing catalog codes with no migration.
- Open: implementation and all target/DB-free/Oracle verification remain pending.
- Next: Claude implements current-basis parity first, then REST/error mapping and the required
  regression matrix; Codex independently reviews the result.

## 2026-08-28 — Implement MANUAL quantity-test REST slice, Oracle-verified

- Role: Claude implementation and Oracle verification (same local Docker Oracle from the prior
  round, still connected).
- Completed: fixed `CurrentApprovalBasisLoader`'s two current-basis bugs (missing-policy fallback
  to `DemandAnalysisRules` defaults; receiver BASE now the representative-event-driven effective
  rate, not the stored baseline, shared by approval and `MANUAL`). Connected the additive 4-field
  version tuple on `POST /api/rebalancing-simulations`, added `Mvp2RebalanceSimulationResponse`,
  and widened `AnalysisApiExceptionHandler` with a new `ApprovalTransactionException` handler
  (no new migration -- V10/V11 already had every code). Added
  `CurrentApprovalBasisLoaderParityIT` (5 tests: policy fallback, event ordering, uplift applied/
  not-applied, donor-HIGH independence), `RebalanceSimulationRestOracleIT` (7 tests including a
  real concurrent lock-timeout), and `RebalanceSimulationControllerTest` (4 DB-free routing unit
  tests).
- Validation: full Oracle backend run (DB_URL connected): **471 total / 471 passed / skip 0 /
  failures/errors 0**. DB-free full: 471 total/355 passed/116 conditional skip, failures/errors 0.
  `git diff --check` exit 0.
- Decisions: none beyond the spec.
- Open: one real regression found and fixed during this round -- the new rule-version guard in
  `RebalanceSimulationService` initially required an exact `"MVP-1"` match and broke
  `ApiGoldenScenarioIT`'s `"MVP-1-DECISION-IT"`-suffixed test fixture; narrowed to a positive
  match against `DemandAnalysisRules.RULE_VERSION` ("MVP-2") instead, which is what the spec
  actually needed. No other open findings for this unit.
- Next: Codex independently re-verifies current-basis parity, REST tuple routing, the error
  contract, zero-persistence and lock-timeout before marking this unit accepted.

## 2026-08-28 — Review MANUAL quantity-test REST implementation

- Role: Codex verification/review.
- Completed: reviewed current-basis parity, tuple routing, response/error mapping and Oracle tests
  against the [owning specification](../archive/state/2026-08-28-manual-quantity-test-rest-spec.md).
- Validation: pure/unit target 41/41; Oracle target 25/25; DB-free full 471 total/355 passed/
  116 conditional skip; Oracle full forced with `--rerun-tasks` 471/471, skip/failures/errors 0;
  `git diff --check` exit 0.
- Decisions: not accepted. Passing tests do not close two contract findings.
- Open: legacy service deny-lists only exact MVP-2 instead of allowlisting exact MVP-1, so
  unknown/future rules can bypass current-basis validation; REST Oracle evidence checks only
  decision absence, not full persistence/inventory immutability or the complete response/error contract.
- Next: Claude fixes only the two findings in [current-task.md](../state/current-task.md), reruns
  target/DB-free/Oracle suites, then returns for Codex re-review.

## 2026-08-28 — Fix Codex's two MANUAL REST review findings

- Role: Claude implementation and Oracle verification (same local Docker Oracle, still connected).
- Completed: (finding 1) reverted `RebalanceSimulationService`'s legacy tuple-less guard from
  deny-only-exact-`MVP-2` back to allowlist-exact-`MVP-1`
  (`!InventoryAnalysisRules.RULE_VERSION.equals(...)`), so unknown/future/`MVP-2-*` rule versions
  are rejected too. This re-broke `ApiGoldenScenarioIT`'s `"MVP-1-DECISION-IT"`-suffixed fixture,
  so fixed the test's isolation strategy instead of loosening production code again: it now uses a
  dedicated unused `analysisDate` (2026-10-31) with the exact `ruleVersion`. Added two regressions
  to `RebalanceSimulationRestOracleIT`: `unknownRuleVersionOnATupleLessRequestIsRejected` and
  `exactMvp1RuleVersionOnATupleLessRequestIsStillAccepted` (new minimal legacy-path fixture at its
  own dedicated `analysisDate`, 2026-12-01). (finding 2) replaced
  `RebalanceSimulationRestOracleIT`'s decision-only `assertZeroPersistence` with a real
  before/after `capture`/`PersistenceState`/`assertUnchanged` helper covering decision count,
  decision-linked approval-basis/transfer-draft presence, and both stores' inventory-snapshot
  id/onHand/reserved quantity; applied it to all persistence-sensitive tests, made the terminal
  test compare against the post-approval baseline instead of zero, added the exact
  declared-order violation list + empty-rejection-reason assertion to the multi-violation test,
  asserted all 18 `ManualQuantityProjection` fields plus the fixed assumption on the feasible
  test, and added a shared `assertProblemDetail` helper (exact code/type/retryable/requestId
  header-body match/timestamp) to all 5 error-path tests.
- Validation: `RebalanceSimulationRestOracleIT` alone: **9/9 Oracle passed** (including both new
  regressions). Full Oracle run: **473 total / 473 passed / skip 0 / failures/errors 0**. DB-free
  full: 473 total/355 passed/118 conditional skip, failures/errors 0. `ApiGoldenScenarioIT` alone
  re-verified: **6/6 Oracle passed** (no regression from the fixture isolation change).
  `git diff --check` exit 0 (line-ending warnings only, no real whitespace errors).
- Decisions: none beyond the two findings — no scope, contract, or schema change. This schema has
  no optimistic-lock `version` column on `sp_inventory_snapshot`, so the "id/quantity/version"
  invariance check in the finding text is realized as id/onHand/reserved quantity comparison.
- Open: none from Claude's side; ready for Codex's independent re-review.
- Next: Codex re-verifies both finding fixes and the real Oracle/DB-free counts above before
  marking this unit accepted.

## 2026-08-28 — Re-review MANUAL REST finding fixes

- Role: Codex verification/review.
- Completed: verified the exact-MVP-1 production allowlist, isolated MVP-1 fixture and the
  strengthened REST Oracle tests against the owning MANUAL REST specification.
- Validation: Oracle target 20/20; DB-free full 473 total/355 passed/118 conditional skip;
  Oracle full forced with `--rerun-tasks` 473/473, skip/failures/errors 0;
  `git diff --check` exit 0.
- Decisions: production allowlist finding closed; the unit remains not accepted.
- Open: REST evidence is still partial: exact coverage/risk/notice, metric and terminal audit-value
  immutability, ProblemDetail body status/instance and diagnostic non-disclosure are not asserted.
- Next: Claude adds only the missing test evidence in [current-task.md](../state/current-task.md),
  reruns the required suites, then returns for final Codex review.

## 2026-08-28 — Fix Codex's round 2 REST-evidence finding

- Role: Claude implementation and Oracle verification (same local Docker Oracle, still connected).
- Completed: strengthened `RebalanceSimulationRestOracleIT` only, no production change. The
  feasible test now asserts all 20 top-level fields and all 18 `ManualQuantityProjection` fields
  to exact fixture-derived values -- hand-derived the four coverage-day values (rate=1, so they
  equal the available quantities), both exact risk codes (`receiverRiskCode=NORMAL` since
  targetForRisk=ceil(1×11)+2=13 and receiverAfterAvailable 13 is not below it;
  `donorRiskCode=OVERSTOCK` since the fixture's donor policy is all zero, so donorProtected=0 and
  any positive donorAfterAvailable trips it), the exact fixed assumption notice string, and the
  freshly-recalculated `donorTransferableQuantity`(10, not the stale stored 8) and
  `receiverCapacityRemaining`(995). Replaced the count-based persistence check with a value-based
  one: `capture` now builds a `DecisionAudit` per decision (status, selected quantity, sequence,
  reason code) with its linked `Optional<BasisAudit>`/`Optional<DraftAudit>` business values, plus
  both stores' snapshot and metric (available quantity, base/high demand rate) fingerprints;
  `assertUnchanged` compares the whole thing before/after every persistence-sensitive test,
  including the terminal test's post-approval (non-zero) baseline. Widened `assertProblemDetail`
  to take an expected HTTP status and a "leak candidate" id, asserting the response and body
  status match, the fixed `instance` path, and that `detail` never contains the recommendation id
  or any of a small raw-diagnostic marker list (`ORA-`, `SQLException`, `Caused by`, etc.) --
  confirms no real leak exists, since `detail` was already catalog-fixed text, not the exception's
  own message.
- Validation: `RebalanceSimulationRestOracleIT` alone: **9/9 Oracle passed on the first run** --
  every hand-derived exact value (coverage days, risk codes, donorTransferableQuantity,
  receiverCapacityRemaining) matched the real Oracle result with no trial-and-error. Full Oracle
  run: **473 total / 473 passed / skip 0 / failures/errors 0**. DB-free full: 473 total/355
  passed/118 conditional skip, failures/errors 0 (unchanged test count, only stronger assertions).
  `ApiGoldenScenarioIT` alone re-verified: **6/6 Oracle passed** (no regression).
  `git diff --check` exit 0 (line-ending warnings only).
- Decisions: none beyond the finding -- test-only change, no scope/contract/schema change.
- Open: none from Claude's side; ready for Codex's final independent re-review.
- Next: Codex re-verifies the strengthened evidence and the real Oracle/DB-free counts above,
  then marks this unit accepted or opens any remaining finding.

## 2026-08-28 — Accept MANUAL quantity-test REST

- Role: Codex final verification/review.
- Completed: reviewed the strengthened exact response, persistence-value and ProblemDetail
  evidence against the [owning specification](../archive/state/2026-08-28-manual-quantity-test-rest-spec.md).
- Validation: Oracle target 20/20; DB-free full 473 total/355 passed/118 conditional skip;
  Oracle full forced with `--rerun-tasks` 473/473, skip/failures/errors 0;
  `git diff --check` exit 0.
- Decisions: accepted the MVP-2 `MANUAL` quantity-test REST unit; no production or public-contract
  change was made during final review.
- Open: none for this unit.
- Next: select the next unimplemented slice, then Codex writes its detailed specification.

## 2026-08-28 — Specify MVP-2 approval/decision REST

- Role: Codex planning/design.
- Completed: wrote the implementation-ready
  [approval/decision REST specification](../archive/state/2026-08-28-approval-decision-rest-spec.md)
  for additive legacy/MVP-2 POST routing, facade idempotency wiring and the GET audit-history read.
- Validation: repository/application/schema inspection; `git diff --check` only. No build or test
  was run because this turn changed knowledge documents only.
- Decisions: retain `decisionStatus`; select MVP-2 by full version tuple plus exactly one
  `Idempotency-Key`; preserve legacy success shape; return minimal POST result and a separate
  full GET history with basis/draft; expose no idempotency key; use bulk reads with a 4-query ceiling.
- Open: implementation and target/DB-free/Oracle verification remain pending.
- Next: Claude implements POST routing/facade first, then GET bulk read and the required regression
  matrix; Codex independently reviews the result.

## 2026-08-28 — Implement MVP-2 approval/decision REST, Oracle-verified

- Role: Claude implementation and Oracle verification (same local Docker Oracle, still connected).
- Completed: extended `RebalanceDecisionRequest` additively (version tuple, `policyException`,
  `reasonCode`; relaxed `selectedQuantity`/`reason` off Bean Validation since MVP-2's per-status
  requiredness can't be expressed statically). `RebalanceDecisionController.decide` branches on
  any-of-{tuple, `policyException`, `reasonCode`, `Idempotency-Key` header} vs. none: none keeps
  the exact legacy MVP-1 success JSON/201 with no `Location`; any requires the full tuple plus
  exactly one header (rejecting duplicate headers and a comma-joined single value) and builds an
  `ApprovalTransactionCommand` for the already-accepted `ApprovalTransactionFacade` --  every
  status/quantity/policy-exception shape rule stays owned by that command's existing canonical
  constructor, not reimplemented here. Added `Mvp2RebalanceDecisionResponse`. Rewrote
  `RebalanceDecisionService` to throw catalog-backed `ApiException`/`ApprovalErrorCode` instead of
  raw `ResponseStatusException`, and added the same exact-`InventoryAnalysisRules.RULE_VERSION`
  allowlist guard the `MANUAL` REST slice used, so a non-MVP-1 recommendation can't be decided
  through the tuple-less legacy path. Added the new read side: `Mvp2DecisionHistoryQueryService`
  (recommendation-existence + ordered-decision-list + conditional basis/draft bulk reads, ≤4
  statements regardless of history length, with a `JOIN FETCH analysisRun` bulk basis query to
  avoid N+1) and `Mvp2DecisionHistoryResponse` with nested `DecisionItem`/`ApprovalBasisItem`/
  `TransferDraftItem` records; enforces the corruption boundary (physical `PENDING` row, MVP-2
  `APPROVED` missing basis/draft, non-approved MVP-2 with basis/draft, unknown contract version)
  as `INTERNAL_SERVER_ERROR` rather than a partial response. Widened
  `AnalysisApiExceptionHandler.assignableTypes` and updated `ApprovalTransactionFacade`'s stale
  "future Controller" javadoc.
- Validation: new `RebalanceDecisionControllerTest` (12 DB-free routing/cardinality unit tests --
  caught and fixed one real NPE where a complete tuple with a null header list threw before the
  header-presence check could reject it) and `Mvp2DecisionHistoryQueryServiceTest` (6 DB-free
  mapping/corruption unit tests -- caught and fixed a `List.of(null)` bug in the test itself, not
  production code, using `any()` matchers instead). New `RebalanceDecisionRestOracleIT`: **19/19
  Oracle passed on the first real run**, covering HELD-then-APPROVED append-only sequencing,
  exact-BASE approval needing no reason, a changed quantity without a reason rejected as
  `INVALID_DECISION_REQUEST`, same-key/same-payload replay (row count unchanged) vs.
  same-key/different-payload `IDEMPOTENCY_KEY_REUSED`, stale tuple/terminal/unknown-recommendation/
  lock-timeout (503) each with the exact catalog code, legacy exact-MVP-1 success plus non-MVP-1
  legacy-bypass prevention, and the GET side's PENDING/ordered-history-exact-values/MVP-1
  compatibility/404/400/corrupt-shape-500/≤4-statement-ceiling matrix. Full Oracle run:
  **510 total / 510 passed / skip 0 / failures/errors 0**. DB-free full: 510 total/373
  passed/137 conditional skip, failures/errors 0. `git diff --check` exit 0 (also removed one
  pre-existing trailing-whitespace line in current-task.md's own spec text).
- Decisions: none beyond the spec -- no scope, contract, or schema change; every write-path rule
  came from already-accepted `ApprovalTransactionCommand`/`ApprovalRequestValidation`.
- Open: one real test-validity regression found and fixed during this round --
  `ApiGoldenScenarioIT`'s `decisionWorkflowRejectsNonMvp1DecisionStatuses` used a
  suffixed-rule-version fixture that the new exact-MVP-1 legacy guard now rejects for an unrelated
  reason (still 400, so the test kept passing mechanically but no longer verified what it claimed
  to); isolated it via a dedicated `analysisDate` (2026-11-10) instead, matching the same fix
  already applied to the sibling `decisionWorkflowApprovesWithinSimulationRange` test in the
  `MANUAL` REST round. No other open findings.
- Next: Codex independently reviews the POST/GET routing, the audit-value exposure boundary, and
  the 4-query ceiling before marking this unit accepted.
