# 2026-08 Worklog

## 2026-08-25 — MVP structure and onboarding baseline

- Role: Codex planning and design
- Completed: Created the minimal Backend, Frontend, synthetic Seed, knowledge base and agent handoff structure defined in [`project.md`](../project.md).
- Validation: `backend\\gradlew.bat --gradle-user-home .gradle-user-home test` — passed.
- Validation: TypeScript compile and Vite production build with Codex bundled Node 24.19.0 — passed.
- Decisions: Oracle is the only primary database; secrets use one ignored local Backend config; AI is optional explanation only.
- Open: Existing Oracle edition/service/credentials, user Node installation and LLM provider settings are unavailable.
- Next: Claude implements the Oracle-backed Vertical Slice in [`current-task.md`](../state/current-task.md).

## 2026-08-25 — Oracle data foundation and portfolio documentation

- Role: Codex planning, data design and environment verification
- Completed: Added the approved ERD, synthetic data pipeline, Oracle Compose service, Flyway schema/Seed/Batch migrations and portfolio README.
- Validation: `.\\scripts\\local.ps1 seed-check` — passed with 1 product, 3 stores, 3 inventory rows and 21 sales rows.
- Validation: Oracle container — healthy; Backend started; Flyway applied 3 migrations; Oracle readback matched all expected counts.
- Validation: Gradle `test`, TypeScript compile and Vite production build — passed.
- Decisions: Host-run Backend/Frontend plus one pinned Oracle container; one ignored root `.env`; Flyway is the only schema owner.
- Open: Batch, APIs, UI and decision behavior remain; LLM provider settings are intentionally deferred.
- Next: Claude follows [`current-task.md`](../state/current-task.md) to implement deterministic calculations and the Oracle-backed Vertical Slice.

## 2026-08-25 — Schema comments and editable portfolio diagrams

- Role: Codex data documentation and architecture design
- Completed: Added concise Korean comments through Flyway V4 and replaced README Mermaid blocks with SVG architecture and ERD assets plus editable draw.io sources.
- Validation: Backend startup — Flyway advanced Oracle from v3 to v4; eight table comments and 54 column comments confirmed.
- Validation: Gradle `test` — passed; all SVG and draw.io files parsed as valid XML and were visually rendered for inspection.
- Decisions: Ordinary comments are short noun phrases; code comments enumerate allowed values; framework Batch tables stay outside the domain ERD.
- Open: Batch, APIs, UI and decision behavior remain unimplemented; the external LLM provider remains optional.
- Next: Claude implements the Oracle-backed Vertical Slice in [`current-task.md`](../state/current-task.md) without changing the approved diagrams unless code reality changes.

## 2026-08-25 — Batch analysis slice (implementation order 1-4)

- Role: Claude implementation
- Completed: JPA entities/repositories for the Batch-analysis tables (inventory, analysis,
  rebalance packages); pure Java `InventoryMetricCalculation` and `RebalanceCalculation`
  per `business-rules.md`; idempotent single-tasklet `inventoryAnalysisJob`
  (`JobParameters`-based + in-transaction `SpAnalysisRun` guard).
- Validation: `gradlew test` (13 tests: 8 calc + 3 rebalance + app-context + Oracle IT) — passed.
- Validation: `InventoryAnalysisGoldenScenarioIT` against real Oracle (`.env` credentials) —
  passed; `sqlplus` readback confirmed Gangnam STOCKOUT_RISK/HIGH, Hongdae OVERSTOCK, Seongsu
  NORMAL, and one Hongdae→Gangnam recommendation of 25 units. Job launched 3 times across
  separate JVM runs; only the first persisted rows, proving idempotency, not just asserting it.
- Validation: `gradlew build` (compile, jar, tests, check) with Oracle env — passed.
- Decisions: `SpProduct`, `SpStore`, `SpRebalanceDecision` deliberately not mapped yet — no
  order 1-4 code queries them. Idempotency is layered on Spring Batch's own JobRepository
  plus a domain-level `SpAnalysisRun` check, both inside one transaction per run.
- Open: No API/service layer yet to launch the Job; UI, decision persistence and LLM remain.
- Next: Codex reviews this slice, then order 5 (APIs) per [`current-task.md`](../state/current-task.md).

## 2026-08-25 — Batch analysis slice review

- Role: Codex verification and review
- Completed: Fast-forwarded local `main` to `origin/main` at `11985bf` and reviewed Claude commit `15cf26d` against the approved rules, schema and tests.
- Validation: `.\gradlew.bat test --rerun-tasks` — 12 passed; Oracle IT conditionally skipped without `DB_URL`.
- Validation: Oracle `InventoryAnalysisGoldenScenarioIT` with ignored `.env` exported — passed.
- Decisions: No code fix applied because the review finding conflicts with a confirmed business rule and triggers the agent guide stop condition.
- Open: `sales / 7` is rounded to scale 10 before transfer `ceil`; one sale reproduces a one-unit receiver-target error.
- Next: Claude preserves an exact sales-rate input for transfer calculations, adds the boundary regression test, reruns unit and Oracle verification, then proceeds to order 5.

## 2026-08-25 — Fix reviewed transfer-boundary rounding defect

- Role: Claude implementation
- Completed: `RebalanceCalculation.calculate` now takes the raw integer 7-day sold
  quantity instead of a rounded `averageDailySales`, computing
  `receiverTargetQuantity`/`donorRetainedQuantity` with an exact integer ceiling
  division (`ceilDiv`) so the value business-rules.md section 2 calls "unrounded" is
  truly exact, not just higher-precision. Updated `InventoryAnalysisTasklet` to carry
  `soldQuantityInWindow` through to the new call site.
- Validation: `RebalanceCalculationTest` (5, +2 regression cases reproducing the
  review's exact one-sale-in-seven-days scenario for both receiver and donor) — passed.
- Validation: `gradlew build` (compile, jar, 15 tests incl. Oracle IT, check) with
  `.env` credentials — passed. Oracle `sqlplus` readback confirms the Golden Scenario
  recommendation is unchanged (25 units, Hongdae → Gangnam) and row counts stayed at
  1/3/1 after rerunning the idempotent Job — no duplicates introduced by the fix.
- Validation: `gradlew test` without `DB_URL` — Oracle IT still skips, not fails.
- Decisions: Scoped the fix strictly to the flagged transfer boundary; left
  `InventoryMetricCalculation`'s 10-decimal `averageDailySales` (coverage-days
  classification and display column) unchanged since the review did not flag it.
- Open: Same as before — no API/service layer, UI, decision persistence or LLM.
- Next: Proceed to order 5 (APIs) per [`current-task.md`](../state/current-task.md).

## 2026-08-25 — Transfer fix follow-up review

- Role: Codex verification and review
- Completed: Reviewed `e8a0f64`; confirmed raw seven-day sales reach the exact ceiling formula and the original one-sale rounding defect is covered.
- Validation: `.\gradlew.bat build --rerun-tasks` with ignored `.env` exported — 15 passed, zero skipped, including Oracle IT.
- Validation: Integer boundary check — `200000000 * 14` wraps from `2800000000` to `-1494967296` before `ceilDiv`.
- Decisions: No code fix applied; the new overflow can silently change deterministic quantities and requires implementation plus regression coverage.
- Open: Make the numerator overflow-safe while preserving the approved exact formula.
- Next: Claude adds widened or checked arithmetic and a boundary test, reruns the Oracle-enabled build, then hands back for Codex review before order 5.

## 2026-08-25 — Fix reviewed integer-overflow defect

- Role: Claude implementation
- Completed: `RebalanceCalculation.ceilDiv` now takes `long` operands, and both call
  sites widen `soldQuantityInWindow * coverageDays` to `long` before calling it, so the
  multiplication can no longer wrap a 32-bit `int`. `ceilDiv` uses `Math.toIntExact`
  so a quotient that still cannot fit in an `int` throws instead of truncating.
- Validation: `RebalanceCalculationTest` (6, +1) — passed, including
  `largeSoldQuantityDoesNotOverflowDuringCeilingCalculation`, which reproduces Codex's
  exact `200_000_000 * 14` example and asserts the correct `donorTransferableQuantity`
  (`99_999_998`) rather than a wrapped/negative value.
- Validation: `gradlew build` (compile, jar, 16 tests incl. Oracle IT, check) with
  `.env` credentials — passed. Oracle `sqlplus` readback: Golden Scenario recommendation
  unchanged (25 units, Hongdae → Gangnam), row counts still 1/3/1 — no duplicates.
- Validation: `gradlew test` without `DB_URL` — Oracle IT still skips, not fails.
- Decisions: Scoped strictly to the flagged overflow; kept the exact-ceiling-division
  approach from the previous fix, only widened its arithmetic.
- Open: No API/service layer, UI, decision persistence or LLM yet.
- Next: Proceed to order 5 (APIs) per [`current-task.md`](../state/current-task.md), unless another review round is requested first.

## 2026-08-25 — Overflow fix second follow-up review

- Role: Codex verification and review
- Completed: Confirmed the multiplication is widened and the `200_000_000 * 14` regression now passes.
- Validation: `.\gradlew.bat build --rerun-tasks` with ignored `.env` exported — 16 passed, zero skipped, including Oracle IT.
- Validation: Safety-stock boundary — receiver sales `2147483647` yields exact target `2147483649` but the current `int + 2` wraps to `-2147483647`.
- Decisions: No code fix applied because the remaining overflow silently changes a deterministic recommendation.
- Open: Keep target/retained arithmetic widened through safety-stock addition and perform one final checked conversion.
- Next: Claude adds the exact max-int boundary test, completes the checked arithmetic, reruns the Oracle-enabled build, then returns for review before order 5.

## 2026-08-25 — Fix reviewed safety-stock addition overflow

- Role: Claude implementation
- Completed: `RebalanceCalculation.ceilDiv` now returns `long` (no longer converts to
  `int` internally); the caller adds `SAFETY_STOCK_UNITS` in `long` and converts to
  `int` exactly once via a single `Math.toIntExact` after that addition, so the target/
  retained expression stays widened through its final step and an out-of-range result
  throws `ArithmeticException` instead of wrapping.
- Validation: `RebalanceCalculationTest` (8, +2) — passed, including
  `receiverTargetOverflowAfterAddingSafetyStockIsRejectedNotWrapped` and
  `donorRetainedOverflowAfterAddingSafetyStockIsRejectedNotWrapped`, reproducing
  Codex's exact `Integer.MAX_VALUE` example for both boundaries.
- Validation: `gradlew build` (compile, jar, 18 tests incl. Oracle IT, check) with
  `.env` credentials — passed. Oracle `sqlplus` readback: Golden Scenario recommendation
  and row counts (1/3/1) unchanged — this fix does not affect sold-quantities anywhere
  near `Integer.MAX_VALUE`.
- Validation: `gradlew test` without `DB_URL` — Oracle IT still skips, not fails.
- Decisions: All three review rounds on this class (rounding, multiplication overflow,
  addition overflow) are now resolved; no further known defects in `RebalanceCalculation`.
- Open: No API/service layer, UI, decision persistence or LLM yet.
- Next: Proceed to order 5 (APIs) per [`current-task.md`](../state/current-task.md), unless another review round is requested first.

## 2026-08-25 — Rebalance arithmetic final review

- Role: Codex verification and review
- Completed: Reviewed the final widened arithmetic and receiver/donor safety-stock boundary tests; no additional finding.
- Validation: `.\gradlew.bat build --rerun-tasks` with ignored `.env` exported — 18 passed, zero skipped, zero failed, including Oracle IT.
- Decisions: All three review-found calculation defects are resolved; the Golden Scenario and idempotency path remain unchanged.
- Open: API/service layer, UI, decision persistence and LLM boundary remain outside this reviewed slice.
- Next: Claude may proceed to order 5 in [`current-task.md`](../state/current-task.md).

## 2026-08-25 — Order 5 API review

- Role: Codex verification and review
- Completed: Reviewed the analysis, exception, simulation and decision APIs plus the Oracle API Golden Scenario test.
- Validation: `.\gradlew.bat build --rerun-tasks` with ignored `.env` exported — 19 passed, zero skipped, zero failed.
- Decisions: The primary Golden Scenario passes, but order 5 is not ready for UI consumption until the detail boundary and repeatable decision-create test are corrected.
- Open: Detail currently returns normal/non-actionable metrics; persistent-DB reruns can skip the decision POST/201 path entirely.
- Next: Claude aligns detail with the actionable list and makes decision creation deterministic in `ApiGoldenScenarioIT`, then returns for Codex review before order 6.

## 2026-08-25 — Implementation order 5: analysis, list, detail, simulation and decision APIs

- Role: Claude implementation
- Completed: Added `SpProduct`/`SpStore` (`catalog` package) and `SpRebalanceDecision`
  (`rebalance` package) entities/repositories. Added `AnalysisRunService` (launches
  `inventoryAnalysisJob` via `JobOperator`, treats `JobInstanceAlreadyCompleteException`
  as a no-op), `InventoryExceptionService` (list/detail over actionable exceptions with
  product/store names and recommendation/decision evidence), `RebalanceSimulationService`
  (before/after coverage for a proposed quantity, no persistence) and
  `RebalanceDecisionService` (one terminal decision per recommendation, 409 on repeat).
  Exposed as `POST /api/analyses`, `GET /api/inventory-exceptions[/{id}]`, `POST
  /api/rebalancing-simulations`, `POST /api/rebalancing-decisions`. Request DTOs use
  Bean Validation; errors use `ResponseStatusException` (400/404/409) — no custom
  exception hierarchy, per the "minimum" API scope.
- Validation: New `ApiGoldenScenarioIT` drives the whole flow with `MockMvc` against
  real Oracle (`.env` credentials) in one test — analyze, list (2 actionable, Seongsu
  correctly excluded), detail, simulate (5→25 receiver / 40→20 donor available),
  decide (201), repeat decision (409) — passed. Direct `sqlplus` readback: exactly one
  `sp_rebalance_decision` row (APPROVED, 25). Designed to tolerate a decision already
  existing from an earlier run, since decisions are intentionally not idempotent.
- Validation: `gradlew build` (compile, jar, 19 tests incl. both Oracle ITs, check)
  with `.env` credentials — passed. `gradlew test` without `DB_URL` — both Oracle ITs
  skip, not fail.
- Decisions: Used Spring's built-in `ResponseStatusException` instead of a custom
  exception/`@RestControllerAdvice` package, since bean-validation failures already get
  Spring's default 400 handling and the remaining cases (404/409/500) are few and
  scoped to single call sites — judged simpler than adding a new `web` package for this.
  Kept `InventoryMetricCalculation`'s coverage formula un-refactored and duplicated a
  small coverage helper in `RebalanceSimulationService` instead, to avoid touching an
  already-reviewed calculation file for a cosmetic DRY improvement.
- Open: Frontend screens (order 6), AI explanation boundary (order 7), and the final
  recorded Backend/Frontend/Oracle verification pass (order 8) remain.
- Next: Order 6 (exception list and detail/simulation screens) per
  [`current-task.md`](../state/current-task.md).

## 2026-08-25 — Fix order 5 review gaps (detail boundary, non-deterministic test)

- Role: Claude implementation
- Completed: `InventoryExceptionService.getExceptionDetail` now applies `Optional.filter`
  against the same `ACTIONABLE` (`STOCKOUT_RISK`/`OVERSTOCK`) set the list uses, so a
  `NORMAL`/`NON_ACTIONABLE` metric id 404s like an unknown id instead of returning
  successfully. Added `getExceptionDetailRejectsNormalClassification`, which looks up
  the Golden Scenario's Seongsu (`NORMAL`) metric directly via the repository and
  asserts the 404. Reworked `ApiGoldenScenarioIT` to delete any decision it previously
  left on the target recommendation (via `SpRebalanceDecisionRepository`) before
  asserting creation, removing the `if (!decisionAlreadyExisted)` branch so the
  201-create and 409-repeat assertions always run instead of being silently skipped on
  a reused Oracle database.
- Validation: `gradlew build` (compile, jar, 20 tests incl. both Oracle ITs, check)
  with `.env` credentials — passed (`ApiGoldenScenarioIT` now 2 test methods).
- Validation: Reran `ApiGoldenScenarioIT` alone a second time against the same Oracle
  instance — passed again, confirming the decision-cleanup makes the create/reject
  path deterministic on repeat runs, not just on a fresh database. `sqlplus` readback:
  exactly one `sp_rebalance_decision` row (APPROVED, 25) afterward, not an accumulating
  history.
- Validation: `gradlew test` without `DB_URL` — both Oracle ITs still skip, not fail.
- Decisions: Chose explicit repository-based cleanup over wrapping the test in
  `@Transactional` rollback, since the Spring Batch Job's own step-scoped transaction
  management makes mixing it with an ambient test transaction harder to reason about;
  explicit delete-then-create is simpler and equally deterministic.
- Open: Frontend screens (order 6), AI explanation boundary (order 7), and the final
  recorded verification pass (order 8) remain.
- Next: Order 6 (exception list and detail/simulation screens) per
  [`current-task.md`](../state/current-task.md), unless another review round is requested first.

## 2026-08-25 — Order 5 isolation final review

- Role: Codex verification and review
- Completed: Reviewed test-owned fixture construction, FK cleanup order, real-decision isolation and matching simulation/approval quantity; no finding.
- Validation: Oracle-enabled `.\gradlew.bat build --rerun-tasks` — all 21 tests passed.
- Validation: Standalone `ApiGoldenScenarioIT` rerun immediately afterward — all 3 methods passed again.
- Decisions: Order 5 review blockers are resolved without mutating the real Golden Scenario decision.
- Open: Frontend screens, AI explanation boundary and final delivery verification remain.
- Next: Claude may proceed to order 6 in [`current-task.md`](../state/current-task.md).

## 2026-08-25 — Order 5 fix follow-up review

- Role: Codex verification and review
- Completed: Accepted the actionable detail filter and its Seongsu NORMAL → 404 test.
- Validation: `.\gradlew.bat build --rerun-tasks` without `DB_URL` — passed; 17 executed, 3 Oracle-conditioned methods skipped.
- Decisions: Did not run the Oracle API IT because its cleanup deletes a persistent terminal audit decision before recreating it.
- Open: Isolate decision test data without altering prior domain state; simulate the same quantity that is approved (currently 20 vs 25).
- Next: Claude makes the API IT non-destructive and workflow-consistent, runs it twice plus the full Oracle-enabled build, then returns for Codex review before order 6.

## 2026-08-25 — Fix order 5 decision-test isolation and quantity mismatch

- Role: Claude implementation
- Completed: Replaced the destructive delete-then-recreate approach with real
  isolation. New third test method `decisionWorkflowApprovesWithinSimulationRange`
  builds its own `SpAnalysisRun` (rule version `MVP-1-DECISION-IT`, distinct from the
  real `MVP-1`), `SpInventoryMetric` rows and `SpRebalanceRecommendation` directly via
  repositories — reusing the real, immutable Gangnam/Hongdae `SpInventorySnapshot`
  rows as evidence, since one snapshot can back metrics from more than one analysis
  run — then exercises simulate/decide/repeat-decide on that recommendation and
  deletes all of it in a `finally` block (and before, for safety) so it never reads,
  deletes or replaces the real Golden Scenario recommendation's decision.
  `goldenScenarioWorksThroughTheApi` is now read-only (analyze/list/detail/simulate
  only); the isolated test also simulates and approves the same quantity (20),
  demonstrating the simulate-then-decide workflow from business-rules.md section 6.
- Validation: `gradlew build` (compile, jar, 21 tests incl. both Oracle ITs, check)
  with `.env` credentials — passed (`ApiGoldenScenarioIT` now 3 test methods).
- Validation: Reran `ApiGoldenScenarioIT` alone a second time against the same Oracle
  instance — passed again. `sqlplus` readback after both runs: the real decision
  (`recommendation_id=1`, `reason='Golden Scenario API verification'`) is unchanged
  from the prior session, and zero `MVP-1-DECISION-IT` rows remain (metrics,
  recommendation, decision or run) — the isolation and cleanup both hold up.
- Validation: `gradlew test` without `DB_URL` — all Oracle-conditioned methods skip.
- Decisions: Chose "build test-owned fixtures via repositories, reusing real read-only
  snapshot evidence" over transactional rollback, since Spring Batch's own step-scoped
  transaction management made mixing the Job launch with an ambient rolled-back test
  transaction harder to reason about; direct fixture construction is simpler to audit
  and provably never touches the real recommendation.
- Open: Frontend screens (order 6), AI explanation boundary (order 7), and the final
  recorded verification pass (order 8) remain.
- Next: Order 6 (exception list and detail/simulation screens) per
  [`current-task.md`](../state/current-task.md), unless another review round is requested first.

## 2026-08-25 — Implementation order 6: exception list and detail/simulation screens

- Role: Claude implementation
- Completed: `frontend/src/types.ts` mirrors the Backend DTOs; `api.ts` is a thin
  `fetch` wrapper (no new HTTP dependency) that surfaces the Backend's own error
  `detail` message. `ExceptionList.tsx` renders the actionable-exception table;
  `ExceptionDetail.tsx` + `RecommendationPanel.tsx` render evidence and one
  simulate-then-decide panel per recommendation (receiver and donor side), showing the
  terminal state instead of the form once a decision exists. `App.tsx` adds a 분석 실행
  control and switches between the two screens with a single `selectedId` state
  variable — no router library added. `vite.config.ts` proxies `/api` to
  `http://localhost:8080` in dev so no CORS/Backend change was needed. No
  client-side business-rule calculation anywhere (project.md section 5) — every
  quantity, coverage value and validation comes from the API responses as-is.
- Validation: `pnpm run build` (`tsc -b && vite build`) — passed. Node.js/pnpm were
  available on PATH this session (`node v24.16.0`), unlike earlier sessions.
- Validation: Live browser verification with the Backend running against Oracle and
  the Vite dev server proxying `/api` (not just the build): the real Golden Scenario
  list showed the correct 2 actionable rows and excluded Seongsu; the real Gangnam
  recommendation (already decided in an earlier session) correctly rendered its
  terminal "승인됨 (수량 25)" state with no form. To see the simulate → decide path
  itself without touching that real decision, a temporary, clearly test-owned
  analysis run/metrics/recommendation was inserted directly in Oracle for a distinct
  date (2026-08-26, real `MVP-1` rule version, reusing the real read-only snapshots) —
  same isolation idea as `decisionWorkflowApprovesWithinSimulationRange`. Through the
  browser: simulated quantity 20 (`POST /api/rebalancing-simulations` → 200, table
  showed 5→25/1.25일→6.25일 and 40→20/70일→35일 exactly as expected), then approved it
  (`POST /api/rebalancing-decisions` → 201, panel updated to "승인됨 (수량 20)"); no
  console errors. Deleted the temporary fixture afterward; `sqlplus` confirmed zero
  rows remain for 2026-08-26 and the real decision is byte-for-byte unchanged.
- Validation: Oracle-enabled `gradlew build` rerun after the browser work (Backend
  code unchanged) — all 21 tests still passed, confirming the DB is back to exactly
  1/3/1/1 real Golden Scenario rows.
- Decisions: No router library (only two screens; a `selectedId` state variable is
  simpler). No CORS/Backend change — Vite's dev proxy handles it, consistent with
  "no additional server" scope. Chose to verify the simulate/decide UI against an
  isolated temporary fixture (inserted and cleaned up via `sqlplus`) rather than
  against the real, already-decided Golden Scenario recommendation, for the same
  reason order 5's review insisted on: never mutate real decision state to run a test
  (here, a manual one).
- Open: AI explanation boundary (order 7) and the final recorded verification pass
  (order 8) remain.
- Next: Order 7, or Codex review of the Frontend slice, per
  [`current-task.md`](../state/current-task.md).
## 2026-08-25 — Order 6 follow-up review

- Role: Codex verification/review
- Completed: reviewed the Frontend/API boundary and reproduced the live list/detail flow against Oracle.
- Validation: `pnpm build` — passed; `gradlew build --rerun-tasks` with ignored Oracle env — passed.
- Validation: browser date change `2026-08-25` → `2026-08-24` while detail open — stale prior-date detail reproduced.
- Decisions: Order 6 remains open until date selection and detail state stay coherent.
- Open: reset detail on date change and prevent stale async list responses from winning.
- Next: Claude fixes the Frontend state transition; Codex reruns build and browser regression.

## 2026-08-25 — Fix order 6 date/detail state-coherence defect

- Role: Claude implementation
- Completed: `App.tsx`'s `analysisDate`-change effect now resets `selectedId`/`detail`
  to `null` synchronously and aborts any in-flight detail request, so a stale detail
  screen can never survive a date change. `loadExceptions` and `loadDetail` each own
  an `AbortController` (`useRef`) and abort the previous request before starting a new
  one, ignoring the resulting `AbortError` rather than treating it as a real failure.
  `api.ts` gained an optional `signal` param on `listExceptions`/`getExceptionDetail`
  and an `isAbortError` helper.
- Validation: `pnpm run build` (`tsc -b && vite build`) — passed.
- Validation: Live browser re-verification of the exact reported scenario — Backend
  running against Oracle, opened the real, already-decided Gangnam detail
  (2026-08-25), changed 분석 기준일 to an isolated test-owned date (2026-08-26, same
  `MVP-1` rule version, real read-only snapshots, inserted/deleted via `sqlplus`
  without touching the real decision) — the screen immediately fell back to the list
  for the new date. `read_network_requests` showed the superseded
  `analysisDate=2026-08-25` list request as `net::ERR_ABORTED`, direct evidence the
  guard cancels in-flight requests. No console errors. `sqlplus` afterward: zero
  2026-08-26 rows remain, real decision unchanged.
- Validation: Oracle-enabled `gradlew build` rerun (Backend unchanged this round) —
  all 21 tests still passed; DB back to exactly 1/3/1/1 real Golden Scenario rows.
- Decisions: Used `AbortController` + a `useRef`-tracked "current request" pattern for
  both the list and detail loaders, rather than a simpler request-sequence counter,
  since it also cancels the actual in-flight network request (visible as
  `net::ERR_ABORTED`), not just the state update — cheaper on the Backend/network too.
- Open: AI explanation boundary (order 7) and the final recorded verification pass
  (order 8) remain.
- Next: Order 7, or another Codex review of the Frontend slice, per
  [`current-task.md`](../state/current-task.md).

## 2026-08-25 — Analysis rerun reporting fix review

- Role: Codex verification/review
- Completed: accepted the domain pre-check and isolated `alreadyCompleted` API regression.
- Validation: Oracle `build --rerun-tasks` — 22 tests, zero failures/errors/skips.
- Validation: post-build Oracle readback — Batch instances/executions/params all zero.
- Decisions: response P2 is fixed; documented JDBC JobRepository persistence is not proven.
- Open: determine why Flyway-owned `BATCH_*` tables remain unused and verify restart persistence.
- Next: Claude wires/verifies the JDBC repository and adds an Oracle metadata assertion.

## 2026-08-25 — Order 6 race fix final review

- Role: Codex verification/review
- Completed: accepted the date lock and live-date guard; no remaining Order 6 finding.
- Validation: `pnpm build` — passed; Oracle build — 21 tests, zero failures/errors/skips.
- Validation: live Oracle list and analysis rerun worked; first rerun response exposed a reporting gap.
- Decisions: UI race is closed; `alreadyCompleted` must also reflect a pre-existing domain run.
- Open: a no-op Job can return false when Batch metadata is absent but `sp_analysis_run` exists.
- Next: Claude fixes the outcome derivation and adds a non-destructive API response assertion.

## 2026-08-25 — Order 6 date-state fix re-review

- Role: Codex verification/review
- Completed: accepted the stale-detail reset and reviewed abort-controller behavior.
- Validation: `pnpm build` — passed; Oracle-enabled `gradlew build --rerun-tasks` — passed.
- Decisions: detail/list cancellation is sound, but analysis completion needs the same date guard.
- Open: analysis for A can finish after selecting B and replace B's list with A's results.
- Next: Claude locks the date during analysis or ignores stale completion, then Codex reruns the ordering regression.

## 2026-08-25 — Fix order 6 analysis/date race

- Role: Claude implementation
- Completed: Added `disabled={analysisRunning}` to the date `<input>` in `App.tsx`, so
  a real user structurally cannot change the analysis date while `POST /api/analyses`
  is in flight. As defense in depth, `handleRunAnalysis` now also captures the
  requested date, and after the `await` compares it against a live `analysisDateRef`
  (kept in sync via a small effect) before calling `loadExceptions` — if the user is
  no longer on that date, the stale reload is skipped instead of overwriting the
  correct list the date-change effect already loaded.
- Validation: `pnpm run build` (`tsc -b && vite build`) — passed.
- Validation: Live browser reproduction of the exact race window — patched
  `window.fetch` in the page (a network-throttle technique exercising the real app
  code, not a mock) to delay only `POST /api/analyses` by 15s, clicked 분석 실행 for
  the real 2026-08-25 date, and confirmed for the full delay that
  `input[type="date"].disabled === true` and the button read "실행 중…" — the race is
  now unreachable through real UI interaction, not merely guarded in code. After the
  delayed request resolved, state returned to normal with no console errors. The
  request targeted the already-completed real date (a no-op), so `sqlplus` confirmed
  Oracle state was unchanged by the test (`sp_analysis_run` 1, `sp_rebalance_decision` 1).
- Validation: Oracle-enabled `gradlew build` rerun (Backend unchanged this round) —
  all 21 tests still passed.
- Decisions: Implemented both options Codex offered (disable the input; compare dates
  after the await) rather than picking one, since together they give both a
  structural UI-level prevention and a defensive code-level guard at low extra cost.
- Open: AI explanation boundary (order 7) and the final recorded verification pass
  (order 8) remain.
- Next: Order 7, or another Codex review of the Frontend slice, per
  [`current-task.md`](../state/current-task.md).

## 2026-08-25 — Fix analysis-rerun `alreadyCompleted` reporting gap

- Role: Claude implementation
- Completed: `AnalysisRunService.runAnalysis` previously derived `alreadyCompleted`
  only from Spring Batch's `JobInstanceAlreadyCompleteException`. Since the domain
  `SpAnalysisRun` row and Spring Batch's own JobInstance metadata are separate sources
  of truth, a domain run that already existed while Batch metadata did not (e.g. after
  a crash between the tasklet's commit and Batch recording its own completion) let the
  Job launch fresh, complete as a tasklet no-op, and the response incorrectly say
  `alreadyCompleted: false`. Fixed by checking the domain table for an existing
  completed run *before* launching the Job and treating that as `alreadyCompleted`
  too, in addition to the existing exception-based check.
- Added `runAnalysisReportsAlreadyCompletedWhenDomainRunPredatesBatchMetadata` to
  `ApiGoldenScenarioIT`: reproduces the exact defect state directly (inserts a
  domain-only `COMPLETED` `SpAnalysisRun` for a never-before-used date, 2026-09-15,
  under the real `MVP-1` rule version, since the public API always uses that fixed
  version), calls `POST /api/analyses`, and asserts `alreadyCompleted: true`. Cleans up
  its own row before and after so it can never collide with a rerun or leave residue.
- Validation: `gradlew build` (compile, jar, 22 tests incl. all Oracle ITs, check)
  with `.env` credentials — passed (`ApiGoldenScenarioIT` now 4 test methods).
- Validation: `sqlplus` readback confirmed only the real Golden Scenario rows remain
  (1 `sp_analysis_run` / 3 `sp_inventory_metric` / 1 `sp_rebalance_recommendation` / 1
  `sp_rebalance_decision`, decision reason text unchanged) — the new test's
  2026-09-15 fixture left no trace.
- Validation: `gradlew test` without `DB_URL` — all four Oracle-conditioned test
  methods skip, not fail. No Frontend changes this round, so no Frontend rebuild.
- Decisions: Reused the real `MVP-1` rule version with a distinct, never-used *date*
  for this regression (rather than a distinct rule version, as
  `decisionWorkflowApprovesWithinSimulationRange` does), since `AnalysisRunService`
  always uses the fixed rule version internally — a fake rule version would never be
  reachable through the actual `POST /api/analyses` code path being tested.
- Open: AI explanation boundary (order 7) and the final recorded verification pass
  (order 8) remain.
- Next: Order 7, or another Codex review, per
  [`current-task.md`](../state/current-task.md).

## 2026-08-25 — JDBC regression guard final review

- Role: Codex verification/review
- Completed: accepted proxy-safe JDBC repository type assertion; no finding.
- Validation: Oracle `build --rerun-tasks` — 22 tests passed, zero failures/errors/skips.
- Validation: readback — domain 1/3/1/1, one Batch JobInstance, zero test-date params.
- Decisions: JDBC persistence, restart idempotency, cleanup scope and regression guard are complete.
- Open: optional AI explanation boundary and final delivery verification only.
- Next: proceed to Order 7 or Order 8 per [`current-task.md`](../state/current-task.md).

## 2026-08-25 — JDBC test guards re-review

- Role: Codex verification/review
- Completed: accepted the `inventoryAnalysisJob` restriction in metadata cleanup.
- Validation: Oracle full build — 22 tests passed.
- Validation: Batch 6.0.4 bytecode and Oracle readback — both relevant execution ids are 1.
- Decisions: exact id does not distinguish the current repository from historical Oracle data.
- Open: assert JDBC wiring directly or use a uniquely test-owned JobInstance.
- Next: Claude replaces the false-positive-prone assertion, then Codex performs final verification.

## 2026-08-25 — JDBC JobRepository follow-up review

- Role: Codex verification/review
- Completed: accepted Spring Batch 6 JDBC configuration and restart persistence.
- Validation: Oracle build — 22 tests; one completed Golden JobInstance, zero test-date params.
- Validation: separate `--no-daemon` Golden Scenario IT JVM — passed.
- Decisions: production fix is sound; current regression assertion can false-pass on historical data.
- Open: prove current JDBC wiring/execution and scope cleanup to `inventoryAnalysisJob`.
- Next: Claude tightens both Oracle test guards, then Codex performs the final rerun.

## 2026-08-25 — Fix Spring Batch JobRepository silently falling back to in-memory

- Role: Claude implementation
- Completed: Diagnosed a Codex-flagged finding (Oracle-enabled build left
  `BATCH_JOB_INSTANCE`/`BATCH_JOB_EXECUTION`/`_PARAMS` empty) with a temporary
  diagnostic test: the `JobRepository` bean was
  `org.springframework.batch.core.repository.support.ResourcelessJobRepository` —
  Spring Boot 4.1's default when neither `@EnableBatchProcessing` nor
  `@EnableJdbcJobRepository` is present. It holds one `JobInstance` in a private field
  and never touches the `DataSource`. Every earlier worklog note claiming "idempotent
  across separate JVM runs" was, in hindsight, coincidental to how those specific test
  runs were ordered within a JVM/ApplicationContext, not a real guarantee. Per Spring
  Batch 6 docs, JDBC persistence requires **both** `@EnableBatchProcessing` and
  `@EnableJdbcJobRepository` together on a `@Configuration` class — confirmed by
  testing that `@EnableJdbcJobRepository` alone (tried first) had no effect. Added
  both to `InventoryAnalysisJobConfig`; default `tablePrefix="BATCH_"` already matches
  V3's schema. Added a direct `JdbcTemplate` assertion to
  `InventoryAnalysisGoldenScenarioIT` that a `COMPLETED` row exists in
  `BATCH_JOB_INSTANCE`/`BATCH_JOB_EXECUTION`, so a future regression fails loudly.
  Also gave `ApiGoldenScenarioIT`'s rerun-reporting test (the only one that actually
  launches the Job) a `deleteBatchJobInstance` cleanup identified by JobParameters,
  after the first post-fix run left a stray `BATCH_JOB_INSTANCE` row for its
  2026-09-15 test date.
- Validation: `gradlew build` (compile, jar, 22 tests incl. all Oracle ITs, check)
  with `.env` credentials — passed.
- Validation: Reran `InventoryAnalysisGoldenScenarioIT` alone in a genuinely fresh JVM
  process (separate `gradlew test --rerun-tasks` invocation). No "Job ... launched"
  log line appeared before the test passed — `jobOperator.start()` threw
  `JobInstanceAlreadyCompleteException` immediately from Oracle-persisted state, real
  cross-process idempotency this time; the new `BATCH_JOB_INSTANCE` assertion passed.
- Validation: `sqlplus` readback confirmed exactly one `BATCH_JOB_INSTANCE` row
  remains (the real `analysisDate=2026-08-25`/`MVP-1` run, `COMPLETED`) alongside the
  unchanged domain rows (1 `sp_analysis_run` / 3 `sp_inventory_metric` / 1
  `sp_rebalance_recommendation` / 1 `sp_rebalance_decision`) — no residue anywhere.
- Validation: `gradlew test` without `DB_URL` — all Oracle-conditioned tests skip.
- Decisions: Kept the domain-run guard (`SpAnalysisRun` check in the tasklet and in
  `AnalysisRunService`) as a second idempotency layer even though the first layer now
  genuinely works, since it's cheap, already tested, and protects the rare case where
  the two stores fall out of sync (e.g. mid-transaction crash).
- Open: AI explanation boundary (order 7) and the final recorded verification pass
  (order 8) remain.
- Next: Order 7, or another Codex review, per
  [`current-task.md`](../state/current-task.md).

## 2026-08-25 — Tighten JDBC JobRepository regression assertion and Batch-cleanup job-name filter

- Role: Claude implementation (review fix)
- Trigger: Codex follow-up on the prior JDBC `JobRepository` fix, two findings:
  1. `InventoryAnalysisGoldenScenarioIT`'s regression assertion counted any
     historically `COMPLETED` `BATCH_JOB_INSTANCE` row for `inventoryAnalysisJob`,
     so it stays green forever once one genuinely JDBC-persisted row exists — it
     could not detect a future regression back to `ResourcelessJobRepository`,
     since that in-memory bean would still report *some* `JobExecution` and satisfy
     a `>= 1` count without proving anything about the current run.
  2. `ApiGoldenScenarioIT.deleteBatchJobInstance` matched purely on
     `analysisDate`/`ruleVersion` `JobParameters`, without also filtering
     `JOB_NAME = 'inventoryAnalysisJob'` — in theory it could delete another job's
     Batch metadata that happened to share the same parameter values.
- Completed:
  - `InventoryAnalysisGoldenScenarioIT` now captures the real `JobExecution` from
    whichever branch runs — the object `jobOperator.start()` returns on success, or
    `jobRepository.getLastJobExecution(inventoryAnalysisJob.getName(), parameters)`
    (with an `assertNotNull` guard) on the already-complete path — and asserts that
    exact `JOB_EXECUTION_ID` resolves to a `COMPLETED` row in Oracle's
    `BATCH_JOB_EXECUTION`/`BATCH_JOB_INSTANCE`, replacing the old count-only check.
    A fallback to `ResourcelessJobRepository` would report an id that does not
    resolve in Oracle, so this now fails correctly on that regression.
  - `ApiGoldenScenarioIT.deleteBatchJobInstance` now joins to `BATCH_JOB_INSTANCE`
    and adds `ji.JOB_NAME = 'inventoryAnalysisJob'` to the `WHERE` clause.
  - No production code changed this round — test-only fix.
- Validation: `gradlew build` with `.env` Oracle credentials — 22/22 tests pass,
  0 failures/errors across all 5 suites (`StockPilotApplicationTests`,
  `RebalanceCalculationTest`, `InventoryMetricCalculationTest`,
  `ApiGoldenScenarioIT` 4/4, `InventoryAnalysisGoldenScenarioIT` 1/1).
- Validation: `gradlew test --rerun` without `DB_URL` — both Oracle ITs skip
  cleanly (4/4 and 1/1 skipped, 0 failures/errors), unit tests still run and pass.
- Validation: `sqlplus` readback (via `docker exec` into `stockpilot-oracle-1`,
  since the host shell has no `sqlplus` on `PATH`) confirmed: real Golden Scenario
  data untouched (`sp_analysis_run`=1, `sp_inventory_metric`=3,
  `sp_rebalance_recommendation`=1, `sp_rebalance_decision`=1, its
  `BATCH_JOB_INSTANCE` row present with `STATUS=COMPLETED`); zero residue for the
  `2026-09-15` test-owned fixture date in both domain tables and
  `BATCH_JOB_INSTANCE`; zero residue for `MVP-1-DECISION-IT%` rule versions.
- Decisions: Chose an exact-execution-id lookup over a tighter count query (e.g.
  "count of rows created after test start") because it directly ties the assertion
  to the specific `JobExecution` object the currently wired bean just handed back,
  which is the actual thing under test — no reliance on timing or row ordering.
- Open: AI explanation boundary (order 7) and the final recorded verification pass
  (order 8) remain.
- Next: Order 7, or another Codex review, per
  [`current-task.md`](../state/current-task.md).

## 2026-08-25 — Replace id-based JDBC JobRepository regression check with an implementation-class assertion

- Role: Claude implementation (review fix)
- Trigger: Codex re-review of the prior fix's exact-execution-id assertion:
  > "The JOB_NAME cleanup restriction is resolved. The execution-id assertion is not:
  > Spring Batch 6.0.4 ResourcelessJobRepository hard-codes JobExecution id 1, and the
  > existing Oracle Golden execution also has id 1, so a fallback repository can still
  > match the historical row. Assert the wired repository implementation (proxy-safe)
  > or use a uniquely test-owned JobInstance whose row cannot pre-exist."
- Completed:
  - Confirmed via `javap` on the actual `spring-batch-core-6.0.4.jar` classes that
    `ResourcelessJobRepository` is a plain concrete class (not proxied), and that
    `AbstractJobRepositoryFactoryBean` (used by `@EnableJdbcJobRepository`) wraps the
    real `SimpleJobRepository` in a transactional `org.springframework.aop.framework.ProxyFactory`
    proxy — so a plain `instanceof` check on the injected `JobRepository` bean would not
    work reliably; the check must be proxy-safe.
  - `InventoryAnalysisGoldenScenarioIT` now asserts
    `AopProxyUtils.ultimateTargetClass(jobRepository) == SimpleJobRepository.class` as
    the primary regression guard, replacing the exact-execution-id-in-Oracle check as
    the thing that actually detects a fallback. The id-in-Oracle check is kept as a
    secondary confirmation that this run's own execution really persisted.
  - No production code changed permanently this round — test-only fix. (A temporary,
    reverted-before-commit removal of `@EnableJdbcJobRepository` was used only to prove
    the new assertion works — see Validation below.)
- Validation: proved the new assertion actually catches the regression Codex
  described, not just that it happens to pass under correct config — temporarily
  removed `@EnableJdbcJobRepository` from `InventoryAnalysisJobConfig`, reran the test
  against the real Golden Scenario date (`analysisDate=2026-08-25`, where
  `BATCH_JOB_EXECUTION` id `1` already existed in Oracle from a prior genuine run —
  exactly the coincidence Codex flagged): the class assertion failed immediately with
  `expected: <SimpleJobRepository> but was: <ResourcelessJobRepository>`, confirming
  the old id-based check would have false-passed in this exact scenario but the new
  class check does not. Reverted the temporary removal.
- Validation: `gradlew build --rerun` with `.env` Oracle credentials — 22/22 tests
  pass, 0 failures/errors across all 5 suites.
- Validation: `gradlew test --rerun` without `DB_URL` — both Oracle ITs skip cleanly
  (4/4 and 1/1 skipped), unit tests still run and pass.
- Validation: `sqlplus` readback (via `docker exec` into `stockpilot-oracle-1`)
  confirmed real Golden Scenario data unchanged (`sp_analysis_run`=1,
  `sp_inventory_metric`=3, `sp_rebalance_recommendation`=1, `sp_rebalance_decision`=1,
  its `BATCH_JOB_INSTANCE` row `STATUS=COMPLETED`) and zero residue for the
  `2026-09-15` test-owned fixture date or `MVP-1-DECISION-IT%` rule versions.
- Decisions: Kept the id-in-Oracle check alongside the new class check rather than
  removing it — it still adds value as a positive confirmation that this specific run's
  data landed in Oracle, it's just not sufficient alone as the regression guard.
- Open: AI explanation boundary (order 7) and the final recorded verification pass
  (order 8) remain.
- Next: Order 7, or another Codex review, per
  [`current-task.md`](../state/current-task.md).

## 2026-08-25 — UI smell cleanup (hero section, shadows, dead CSS)

- Role: Claude implementation (user-requested UI fix)
- Completed: Reviewed the Frontend using `.agents/skills/ui-smell-review/SKILL.md`
  criteria, then fixed what it found (the skill itself is diagnose-only; fixes were
  applied as separate implementation work per the user's request). Replaced the
  generic SaaS-landing-page hero (`.hero`/`.eyebrow`/`.summary`/`.status` — eyebrow
  label, up-to-6rem `<h1>`, subhead, status pill) with a slim `<header className="app-header">`
  in `App.tsx`; reduced `.hero`/`.panel`'s identical oversized shadow/20px radius to a
  plain bordered `.panel`; turned the synthetic-data note into plain text instead of a
  marketing-style pill; removed unused `.panel ul`/`.panel li` dead CSS (a leftover
  feature-card grid no component renders). Kept the classification/priority/decision
  badges and table-based list/simulation layouts, since they support the actual
  comparison task rather than being decorative.
- Validation: Live in a real browser against the running Backend/Oracle (`gradlew
  bootRun` + `pnpm --dir frontend run dev`) — list screen (2 Golden Scenario rows) and
  detail screen (Gangnam/Core Ball Cap, approved decision) both render correctly after
  the change; no new console errors.
- Validation: `pnpm --dir frontend run build` (tsc -b && vite build) — passed.
- Decisions: Scoped this to Frontend-only, matching the skill's own scope (no Backend
  or business-rule changes).
- Open: AI explanation boundary (order 7) and the final recorded verification pass
  (order 8) remain.
- Next: Order 7, or another review cycle, per
  [`current-task.md`](../state/current-task.md).

## 2026-08-25 — Order 7: AI-disabled explanation boundary

- Role: Claude implementation
- Completed: New `com.bapegg.stockpilot.explanation` package. `AiProperties`
  (`@ConfigurationProperties(prefix = "stockpilot.ai")`, registered via
  `@ConfigurationPropertiesScan` on `StockPilotApplication`) binds the existing
  `stockpilot.ai.*`/`AI_*` keys. `ExplanationService.explain(id)` first validates the
  id via the existing `InventoryExceptionService.getExceptionDetail` (same 404 rule as
  the detail endpoint), then reports an explicit unavailable `ExplanationResponse`
  instead of an error: `AI_DISABLED` (today's real `.env` state), `AI_UNCONFIGURED`
  (enabled but incomplete settings), or `AI_PROVIDER_NOT_IMPLEMENTED` (enabled and
  fully configured, but no adapter exists yet — one is added only once real provider
  settings are supplied, per AGENTS.md's AI boundary). `ExplanationController` exposes
  `POST /api/inventory-exceptions/{id}/explanation` (project.md section 6). No LLM
  HTTP call exists in the codebase. Backend-only; no Frontend change.
- Validation: `ExplanationServiceTest` (pure JUnit 5 + Mockito, no Spring context) —
  4/4 passed, including with `DB_URL` unset (no Oracle or LLM key needed).
- Validation: `ApiGoldenScenarioIT.explanationEndpointReportsAiDisabledForTheGoldenScenario`
  (read-only, real Golden Scenario Gangnam exception) — passed, confirming real `.env`
  config binding and controller wiring return `AI_DISABLED` end-to-end.
- Validation: `gradlew build --rerun` with `.env` Oracle credentials — 27/27 tests
  pass, 0 failures/errors across 6 suites.
- Validation: `gradlew test --rerun` without `DB_URL` — the new unit test still runs
  and passes; both Oracle IT classes skip cleanly.
- Validation: `sqlplus` readback confirmed real Golden Scenario domain data unchanged
  (`sp_analysis_run`=1, `sp_inventory_metric`=3, `sp_rebalance_recommendation`=1,
  `sp_rebalance_decision`=1) — the new endpoint is read-only.
- Decisions: Scoped order 7 to the Backend boundary only, matching its exact wording
  in `current-task.md` ("add a provider adapter only after provider settings are
  supplied") — no Frontend trigger UI added since that wasn't asked for, and no LLM
  client code added since `.env`'s AI settings remain empty.
- Open: Order 8 (final recorded Backend/Frontend/Oracle verification pass) remains.
- Next: Order 8, or a Codex review of this order, per
  [`current-task.md`](../state/current-task.md).

## 2026-08-25 — Codex review of Order 7

- Role: Codex verification/review
- Completed: Reviewed the AI-disabled explanation boundary against
  [`business-rules.md`](../business-rules.md) section 7 and the acceptance criteria.
- Validation: Oracle-backed `backend/gradlew.bat -p backend build --rerun-tasks` —
  passed all 27 tests with 0 failures/errors.
- Validation: DB credentials removed, focused `ExplanationServiceTest` rerun — 4/4
  passed without Oracle or an LLM key.
- Decisions: Accepted; no quantity/status decision, provider call, persistence, or
  tracked secret was introduced. No code correction was needed.
- Open: Order 8 final recorded Backend/Frontend/Oracle verification remains.
- Next: Execute Order 8 per [`current-task.md`](../state/current-task.md).

## 2026-08-25 — Order 8: final Backend/Frontend/Oracle verification pass

- Role: Claude implementation
- Completed: Re-verified every `project.md` section 8 acceptance criterion against a
  live, freshly-started application (not just historical test runs). Started
  `gradlew bootRun` against real Oracle and called the actual endpoints via `curl`:
  `POST /api/analyses` twice (idempotent — same `analysisRunId=1`, `alreadyCompleted:
  true` both times), `GET /api/inventory-exceptions` and `/{id}` (correct
  classifications/priority/recommendation), `POST /api/rebalancing-simulations`
  (correct before/after quantities), `POST /api/inventory-exceptions/1/explanation`
  (`AI_DISABLED`, matching the real unmodified `.env`) and the same for an unknown id
  (`404`). Confirmed via `sqlplus` that the simulation call mutated nothing
  (`sp_inventory_snapshot` and all domain row counts unchanged) and that
  `flyway_schema_history` shows all 4 migrations with `success=1`. Confirmed
  `@NotBlank reason` and `@Transactional` on the decision write path in source.
  Verified the Frontend live in a browser against the running Backend/Oracle (list
  and detail screens both correct, no console errors).
- Validation: `gradlew clean build --rerun` with `.env` Oracle credentials — 27/27
  tests pass, 0 failures/errors, 6 suites.
- Validation: `gradlew test --rerun` with `DB_URL` and every `AI_*` variable unset —
  16 pure-calculation tests, `StockPilotApplicationTests`, and `ExplanationServiceTest`
  (4) all ran and passed with no Oracle or LLM key; both Oracle IT classes skipped
  cleanly (no failures).
- Validation: `pnpm --dir frontend run build` (`tsc -b && vite build`) — passed.
- Decisions: Treated order 8 as requiring a genuinely live smoke test through a
  running process (real HTTP, real Oracle, real `.env`), not only JUnit/MockMvc runs,
  since "Run and record ... Oracle integration results" reads as broader than
  automated tests alone.
- Open: None blocking. The real LLM provider adapter remains a described, optional
  next task (see `implemented-state.md`'s "Not implemented").
- Next: Codex review of the complete implementation (orders 1-8), or the optional LLM
  provider adapter if the user supplies real settings, per
  [`current-task.md`](../state/current-task.md).

## 2026-08-25 — Codex review of Order 8

- Role: Codex verification/review
- Completed: Rechecked the final verification record against
  [`project.md`](../project.md) section 8 and the public README.
- Validation: Oracle-backed Backend `clean build --rerun-tasks` — 27/27 passed;
  Frontend production build, Seed validation, Compose config and Oracle health passed.
- Decisions: Order 8 not accepted; README lines 246-248 still call completed MVP
  capabilities unimplemented, contradicting the repository and completion claim.
- Open: Correct the README status table while keeping the real LLM adapter marked
  optional/unimplemented.
- Next: Claude documentation fix, then Codex re-review.

## 2026-08-25 — Expanded Korean demo data

- Role: Codex implementation
- Completed: Added V5 with Korean catalog/store labels and a deterministic
  `2026-08-26` SYNTHETIC dataset; moved the UI default analysis date to that dataset.
- Validation: Oracle readback — 8 products, 8 stores, 64 snapshots, 448 sales facts;
  analysis produced 16 stockout-risk, 24 normal, 24 overstock and 48 recommendations.
- Validation: Oracle Backend clean build — 27/27 passed; Frontend production build
  and original Golden CSV Seed validation passed; Flyway V5 recorded `success=1`.
- Decisions: Preserved technical IDs and the `2026-08-25` Golden Scenario; no applied
  migration, calculation threshold, API contract or schema was changed.
- Open: Coverage-days display policy and README rewrite remain separate follow-ups.
- Next: Confirm and implement the coverage display, then update README.

## 2026-08-25 — MVP-2 planning rebaseline

- Role: Codex planning and design
- Completed: Reframed StockPilot as a demand-signal and inventory-exception review
  workbench; aligned the MVP-2 draft specification, deterministic assumptions, target
  data model, README, editable diagrams and state documents.
- Validation: `scripts/local.ps1 seed-check` — passed; bundled-Node TypeScript/Vite
  production build — passed; Oracle-backed `gradlew test --rerun-tasks` — 27/27
  passed with zero skips, failures or errors; all SVG/draw.io files parse as XML.
- Decisions: Preserved implemented MVP-1 and immutable V1–V5; planned MVP-2 migrations
  start at V6; marked every MVP-2 capability and threshold as approval-pending.
- Open: User approval of the ASSUMPTION table and six scope/data-model choices in
  [`project.md`](../project.md) section 12.
- Next: Stop before code; after approval, implement V6 schema, V7 six-scenario
  SYNTHETIC Seed and V8 comments per [`current-task.md`](../state/current-task.md).

## 2026-08-25 — MVP-2 demo design approved

- Role: Codex planning and design
- Completed: Recorded user approval of the versioned MVP-2 ASSUMPTION baseline and
  six scope/data-model choices; promoted project, rules and logical model from Draft
  to approved-but-unimplemented; strengthened the existing UI disclosure.
- Validation: Approval terminology scan and all SVG/draw.io XML parses passed;
  Frontend TypeScript/Vite production build passed; no V6+ migration or MVP-2
  calculation code was added.
- Decisions: Domestic same-owner/explicit routes only; lead-time-only logistics;
  input low/base/high uplift; VARIABLE comparison-only; transfer draft without stock
  mutation; preserve Recommendation with Scenario children.
- Open: None at the policy-choice gate. Implementation conflicts still trigger the
  stop conditions in `AGENTS.md`.
- Next: Claude implements Phase 1 V6 Schema, V7 SYNTHETIC scenarios and V8 comments
  per [`current-task.md`](../state/current-task.md).

## 2026-08-25 — MVP-2 Phase 1 Oracle inputs

- Role: Codex implementation
- Completed: Added immutable V6~V8 for the compatible MVP-2 Schema, GS-01~GS-06
  SYNTHETIC inputs and domain Comments; added nine `data/seed/mvp2` contracts and
  extended the validator while preserving MVP-1.
- Validation: clean Oracle V1→V8 and existing V5→V8 `gradlew test --rerun-tasks` —
  27/27 passed in both paths; V1~V5 checksums unchanged.
- Validation: Oracle readback — products 6, stores 3, inventory 348, sales 336,
  event/inbound/open-transfer 1 each, routes 2, policies 12; zero invalid constraints;
  GS-01~GS-06 facts matched and three invalid writes were rejected then rolled back.
- Validation: `scripts/validate-seed.ps1`, bundled-Node `pnpm run build`, and
  `git diff --check` — passed.
- Decisions: Kept Java/API behavior at MVP-1; Schema enforces VARIABLE no-default,
  transfer-draft-only persistence boundary and versioned ASSUMPTION/SYNTHETIC labels.
- Open: MVP-2 deterministic Java signals, candidates, scenarios and approval
  validation remain unimplemented.
- Next: Implement Phase 2 pure Java rules and GS-01~GS-06 tests per
  [`current-task.md`](../state/current-task.md).

## 2026-08-25 — MVP-2 Phase 2 observation statistics

- Role: Claude implementation
- Completed: Added `com.bapegg.stockpilot.demand` (`DailyDemandObservation`,
  `DemandObservationWindow`, `DemandObservationStatistics`) covering Phase 2 order
  items 1-2: 28-day window input, `OOS_CENSORED` flag, active-week/sales-day-ratio/CV,
  median/MAD and section-3 spike/single-bulk-transaction detection. No entity,
  migration or Oracle code touched.
- Validation: `.\gradlew.bat test --rerun-tasks` (no `DB_URL`) — 45/45 pass (up from
  27), 0 failures/errors, both Oracle ITs skip cleanly. New tests reproduce real
  `data/seed/mvp2` GS-01/GS-03/GS-04 numbers exactly, plus an all-OOS edge case and an
  even-count median case. Oracle-backed verification: not run/not applicable (no DB
  change).
- Decisions: Signal classification, confidence and low/base/high rates (section 3/5,
  need event and plan-horizon input) deliberately excluded from this class to keep it
  a single, fully pure-from-window statistic.
- Open: Signal classification, inventory projection/exceptions, candidate/route
  rules, scenario quantities and approval validation remain. GS-02/GS-05/GS-06 not
  yet in Java.
- Next: Implement section 3's signal classification (needs the plan-horizon value
  object and `SP_DEMAND_EVENT` overlap) per
  [`current-task.md`](../state/current-task.md).

## 2026-08-26 — Codex review of MVP-2 observation statistics

- Role: Codex verification/review
- Completed: Compared Phase 2 items 1-2 with rules sections 1-3 and V6 input shape.
- Validation: DB-free Backend `test --rerun-tasks` — 45 passed, 6 Oracle IT skips,
  zero failures/errors.
- Decisions: Not accepted; observable-day calculation drops V6 `snapshot_at` and
  `out_of_stock_flag`, so it cannot enforce the approved valid-reference/OOS meaning.
- Open: Preserve those inputs in the pure value-object boundary and add stale/OOS
  exclusion regression tests before implementing signal classification.
- Next: Claude fixes the observation input semantics, then requests Codex re-review.

## 2026-08-26 — Fix reviewed observable-day input gap

- Role: Claude implementation
- Completed: `DailyDemandObservation` now carries the explicit `out_of_stock_flag`
  (authoritative, not re-derived from quantity) and `snapshot_at` (via a new
  `snapshotReferenceValid()` date-match check). `observable()` is
  `snapshotReferenceValid() && !outOfStockFlag && availableQuantity() >= 1`. Added
  static factory `DailyDemandObservation.of(...)` so existing simple call sites (all
  prior GS-01/03/04 tests) keep the V6-backfill-equivalent behavior unchanged; the
  canonical constructor models an explicit-flag/quantity divergence or a stale
  snapshot directly. No public API/entity/migration changed.
- Validation: `.\gradlew.bat test --rerun-tasks` (no `DB_URL`) — 44 non-skipped tests
  pass (up from 39), 0 failures/errors; both Oracle IT classes skip cleanly. New
  regression tests: `DailyDemandObservationTest` (+3: explicit-flag override,
  mismatched snapshot date, null `snapshotAt` rejected), `DemandObservationStatisticsTest`
  (+2: both inputs actually remove a day from `observableDayCount`/
  `oosCensoredDayCount`/CV/spike evidence). Oracle-backed verification: not run, not
  applicable — no DB-touching code changed.
- Decisions: Kept the observable/oosCensored binary partition rather than adding a
  third "invalid snapshot" quality-flag category, since business-rules.md section 4
  defines no such flag; an invalid-reference-time day is folded into `oosCensored`
  purely as "not usable as demand evidence," documented inline as a deliberate scope
  decision.
- Open: Signal classification (order item 3), low/base/high rates, inventory
  projection/exceptions, candidate/route rules, scenario quantities, approval
  validation. GS-02/GS-05/GS-06 not yet in Java.
- Next: Codex re-reviews this fix; then implement section 3's signal classification
  per [`current-task.md`](../state/current-task.md).

## 2026-08-26 — Codex re-review of observable fix

- Role: Codex verification/review
- Completed: Rechecked the added snapshot/OOS inputs and regression tests against
  rules sections 2-4.
- Validation: DB-free Backend `test --rerun-tasks` — 50 total, 44 passed, 6 Oracle
  skips, zero failures/errors.
- Decisions: Not accepted; invalid timestamps are incorrectly labeled OOS, and an
  OOS row with positive sales is excluded from distributions but retained in the
  spike denominator.
- Open: Separate invalid snapshot handling from genuine zero-sale OOS censorship and
  make sales inclusion consistent.
- Next: Claude corrects the invariants and requests another Codex re-review before
  signal classification.

## 2026-08-26 — Fix reviewed OOS/invalid-snapshot invariant mixing

- Role: Claude implementation
- Completed: Split the two invariants Codex's re-review flagged. `DailyDemandObservation`
  now has `stockedOut()` (explicit flag or zero available quantity) and
  `invalidSnapshotReference()` (mismatched `snapshotAt`) as distinct predicates;
  `oosCensored()` = `snapshotReferenceValid() && stockedOut()` (real stockouts only — a
  mismatched-snapshot day is neither `observable()` nor `oosCensored()`). The constructor
  now rejects `stockedOut() && soldQuantity > 0` outright, so an OOS-censored day can never
  carry a recorded sale by construction. `DemandObservationStatistics` gained
  `invalidSnapshotDayCount` and a three-way bucketing loop; `totalWindowSales` now excludes
  invalid-snapshot days' sales. No public API/entity/migration changed.
- Validation: `.\gradlew.bat build --rerun-tasks` (no `DB_URL`) — compile/jar/check pass;
  46 non-skipped tests pass (up from 44), 0 failures/errors; both Oracle IT classes skip
  cleanly. `DailyDemandObservationTest` (13, +2: reject explicit-OOS-with-sales, reject
  zero-available-with-sales) and `DemandObservationStatisticsTest` (7, both prior
  regression tests rewritten to assert `oosCensored=false` + `invalidSnapshotDayCount=1`
  for the mismatched-snapshot case, and the corrected `totalWindowSales` for both cases).
  Oracle-backed verification: not run, not applicable — no DB-touching code changed.
- Decisions: Enforced the "real OOS censorship is always zero-sale" rule as a hard
  constructor invariant rather than a downstream aggregation carve-out, so
  `totalWindowSales` cannot be distorted by a contradictory stocked-out-with-sales row
  regardless of how future code sums it.
- Open: Signal classification (order item 3), low/base/high rates, inventory
  projection/exceptions, candidate/route rules, scenario quantities, approval
  validation. GS-02/GS-05/GS-06 not yet in Java.
- Next: Codex re-reviews this fix; then implement section 3's signal classification
  per [`current-task.md`](../state/current-task.md).

## 2026-08-26 — Codex accepts observable invariant fix

- Role: Codex verification/review
- Completed: Rechecked invalid-snapshot/OOS separation, constructor invariants and
  consistent sales exclusion against rules sections 2-4.
- Validation: DB-free Backend `build --rerun-tasks` — 52 total, 46 passed, 6 Oracle
  skips, zero failures/errors; compile, jar and check passed.
- Decisions: Accepted with no additional finding; both prior P1 issues are resolved.
- Open: Phase 2 signal classification and later deterministic rule stages remain.
- Next: Implement section 3 signal classification per
  [`current-task.md`](../state/current-task.md).

## 2026-08-26 — Phase 2 signal classification (sections 3-4)

- Role: Claude implementation
- Completed: Added `PlanHorizon`, `DemandEvent`, `DemandSignalType`, `DemandConfidence`
  and `DemandSignalClassification.classify(...)` to `com.bapegg.stockpilot.demand`,
  implementing section 3's fixed six-step signal decision and section 4's confidence
  table (any quality flag downgrades to `LOW`, subsuming the `KNOWN_EVENT` +
  `INCOMPLETE_EVENT_DATA` note). `STALE_INVENTORY`/`MISSING_INBOUND` not evaluated yet
  (no input for them in this slice). low/base/high rates (section 5) still separate.
- Validation: `.\gradlew.bat build --rerun-tasks` (no `DB_URL`) — compile/jar/check
  pass; 66 non-skipped tests pass (up from 46), 0 failures/errors; both Oracle ITs skip
  cleanly. New tests: `PlanHorizonTest` (5), `DemandEventTest` (7),
  `DemandSignalClassificationTest` (8) — reproduces GS-01 (`STABLE_REPEAT`/`HIGH`) and
  GS-02 (`KNOWN_EVENT`/`MEDIUM`) from the identical underlying demand pattern (proving
  `KNOWN_EVENT` correctly wins the fixed evaluation order over what would otherwise also
  qualify as `STABLE_REPEAT`), plus hand-derived `DATA_INSUFFICIENT`,
  `UNEXPLAINED_SPIKE` (GS-03's exact pattern), `INTERMITTENT`, `VARIABLE`, an
  event-scoped-to-a-different-SKU-is-ignored case, and incomplete-uplift confidence
  downgrade. Oracle-backed verification: not run, not applicable — no DB-touching code
  changed.
- Open: low/base/high demand rates (rest of order item 3), inventory
  projection/exceptions, candidate/route rules, scenario quantities, approval
  validation. GS-05/GS-06 not yet in Java.
- Next: Implement section 5's low/base/high demand rates (percentile interpolation,
  event uplift multiplication window, `REVIEW_REQUIRED` fallback) per
  [`current-task.md`](../state/current-task.md).

## 2026-08-26 — Codex reviews signal classification

- Role: Codex verification/review
- Completed: Compared the new signal/confidence boundary with rules sections 3-4 and
  V6 constraints; reviewed its 20 new unit tests and the full backend regression.
- Validation: DB-free Backend `build --rerun-tasks` — 72 total, 66 passed, 6 Oracle
  skips, zero failures/errors; compile, jar and check passed.
- Decisions: Not accepted yet; primary-signal precedence is correct, but two
  correctness findings remain.
- Open: Preserve relevant incomplete-event metadata even for `DATA_INSUFFICIENT`; reject
  negative target coverage and route lead times in `PlanHorizon.of(...)`.
- Next: Claude fixes both paths with boundary tests, then Codex re-reviews before the
  section 5 low/base/high demand-rate increment.

## 2026-08-26 — Fix reviewed signal-classification findings

- Role: Claude implementation
- Completed: Moved `DemandSignalClassification.classify`'s relevant-event lookup and
  `incompleteEventData` computation above the `DATA_INSUFFICIENT` early return, so that
  return now preserves the real computed values instead of hard-coding `null`/`false`.
  Added negative/`null` input validation to `PlanHorizon.of(...)` for
  `receiverTargetCoverageDays` and each route lead time, matching V6's
  `ck_sp_policy_values`/`ck_sp_route_values` Check Constraints. No public API/entity/
  migration changed.
- Validation: `.\gradlew.bat build --rerun-tasks` (no `DB_URL`) — compile/jar/check
  pass; 71 non-skipped tests pass (up from 66), 0 failures/errors; both Oracle ITs skip
  cleanly. New/updated tests: `DemandSignalClassificationTest` (9, +1: insufficient
  observable days still preserves `incompleteEventData`; existing recent-launch test now
  also asserts the event is preserved) and `PlanHorizonTest` (9, +4: negative coverage
  with/without an active route, negative lead time, `null` lead time element). Oracle-
  backed verification: not run, not applicable — no DB-touching code changed.
- Open: low/base/high demand rates (rest of order item 3), inventory
  projection/exceptions, candidate/route rules, scenario quantities, approval
  validation. GS-05/GS-06 not yet in Java.
- Next: Codex re-reviews this fix; then implement section 5's low/base/high demand
  rates per [`current-task.md`](../state/current-task.md).

## 2026-08-26 — Codex accepts signal-classification fixes

- Role: Codex verification/review
- Completed: Rechecked both prior P1 fixes and their boundary tests against rules
  sections 3-4 and V6 nonnegative constraints.
- Validation: DB-free Backend `build --rerun-tasks` — 77 total, 71 passed, 6 Oracle
  skips, zero failures/errors; compile, jar and check passed.
- Decisions: Accepted with no additional finding; relevant incomplete-event evidence
  survives `DATA_INSUFFICIENT`, and invalid horizon inputs are rejected.
- Open: Section 5 low/base/high rates and subsequent Phase 2 stages remain.
- Next: Claude implements section 5 per [`current-task.md`](../state/current-task.md).

## 2026-08-26 — Phase 2 low/base/high demand rates (section 5)

- Role: Claude implementation
- Completed: Added `DemandRateCalculation` to `com.bapegg.stockpilot.demand`: per-week
  eligible sales/day counts (excluding non-observable days, the spike-evidence date, and
  relevant-event days), the `REVIEW_REQUIRED`-triggering `< 3` valid weekly rates check,
  and the documented linear-interpolation percentile formula for low/base/high, scale 12
  HALF_UP throughout. Added `DemandEvent.upliftFor(...)` (applicability only) and
  `DemandRateCalculation.applyUplift(...)` (multiply + round), left deliberately
  unconnected to a specific scenario window since that window is decided by order item
  5 (candidate/route rules), not yet implemented -- documented as a scope decision
  rather than guessed at.
- Validation: `.\gradlew.bat build --rerun-tasks` (no `DB_URL`) — compile/jar/check
  pass; 78 non-skipped tests pass (up from 71), 0 failures/errors; both Oracle ITs skip
  cleanly. New tests: `DemandRateCalculationTest` (7) — exact GS-01
  (low=base=high=2.0), GS-02 (event day excluded from its week's eligible count),
  GS-03 (spike day excluded), GS-04 (only 2 valid weeks -> `reviewRequired=true`), and
  a non-uniform weekly-rate case (1/2/3/4) asserting the interpolation formula exactly
  (1.75/2.5/3.25). Oracle-backed verification: not run, not applicable — no DB-touching
  code changed.
- Open: Inventory projection/exceptions (order item 4), candidate/route rules (order
  item 5), scenario quantities (order item 6), approval validation (order item 7).
  GS-05/GS-06 not yet in Java.
- Next: Implement section 4/6's projected inventory and exception/severity
  determination (`STOCKOUT_RISK`/`OVERSTOCK`/`REVIEW_REQUIRED`/`NORMAL`/
  `NON_ACTIONABLE`, `CRITICAL`/`HIGH`/`REVIEW`), reproducing GS-05's confirmed-inbound
  scenario, per [`current-task.md`](../state/current-task.md).

## 2026-08-26 — Codex reviews demand-rate calculation

- Role: Codex verification/review
- Completed: Compared weekly eligibility, percentile interpolation, scale/rounding and
  uplift helper boundaries with business rules section 5.
- Validation: DB-free Backend `build --rerun-tasks` — 84 total, 78 passed, 6 Oracle
  skips, zero failures/errors; compile, jar and check passed.
- Decisions: Not accepted; spike evidence is excluded even when `KNOWN_EVENT` wins the
  signal priority and the spike is therefore not `UNEXPLAINED_SPIKE` evidence.
- Open: Make spike-day exclusion conditional on the selected signal and add the
  combined spike-candidate/future-event regression test. Scenario-window uplift wiring
  remains a later-stage integration boundary.
- Next: Claude fixes the finding, then Codex re-reviews before inventory projection.

## 2026-08-26 — Fix reviewed spike-exclusion finding in demand rates

- Role: Claude implementation
- Completed: `DemandRateCalculation.calculate` now takes the store-SKU's already-decided
  `DemandSignalType` and excludes `stats.spikeEvidenceDate()` from the baseline only when
  that signal is `UNEXPLAINED_SPIKE` -- not unconditionally -- since section 3 checks
  `KNOWN_EVENT` first, so a statistically spike-shaped day can lose to a relevant event
  and not actually be the classified anomaly. The relevant-event-period exclusion is
  unchanged (always applied). No production pipeline depends on the old signature yet.
- Validation: `.\gradlew.bat build --rerun-tasks` (no `DB_URL`) — compile/jar/check
  pass; 79 non-skipped tests pass (up from 78), 0 failures/errors; both Oracle ITs skip
  cleanly. New test: `DemandRateCalculationTest`
  (`knownEventSignalDoesNotExcludeAStatisticallySpikeShapedDayFromTheBaseline`) reuses
  GS-03's exact spike pattern plus an unrelated future event and directly contrasts the
  `KNOWN_EVENT` case (spike week's rate = 20/7 = 2.857142857143) against the
  `UNEXPLAINED_SPIKE` case (same week's rate = 0) from the same stats object. The five
  pre-existing tests now pass each GS's actual classified signal type explicitly.
  Oracle-backed verification: not run, not applicable — no DB-touching code changed.
- Open: Inventory projection/exceptions (order item 4), candidate/route rules (order
  item 5), scenario quantities (order item 6), approval validation (order item 7).
  GS-05/GS-06 not yet in Java.
- Next: Codex re-reviews this fix; then implement section 4/6's projected inventory and
  exception/severity determination, reproducing GS-05, per
  [`current-task.md`](../state/current-task.md).

## 2026-08-26 — Codex accepts demand-rate fix

- Role: Codex verification/review
- Completed: Rechecked conditional spike-day exclusion and its same-statistics
  `KNOWN_EVENT` versus `UNEXPLAINED_SPIKE` regression test against sections 3 and 5.
- Validation: DB-free Backend `build --rerun-tasks` — 85 total, 79 passed, 6 Oracle
  skips, zero failures/errors; compile, jar and check passed.
- Decisions: Accepted with no additional finding; the previous P1 is resolved.
- Open: Scenario-window uplift wiring remains deferred to its route/scenario stage;
  projected inventory and later Phase 2 stages remain.
- Next: Claude implements projected inventory and exception/severity per
  [`current-task.md`](../state/current-task.md).

## 2026-08-26 — Phase 2 projected inventory and exception/severity (sections 4, 6, 9)

- Role: Claude implementation
- Completed: Added `InventoryProjection` (`currentAvailable`,
  `projectedReceiverBeforeDemand`, `projectedDonorAtDispatch`,
  `receiverAtArrivalWithoutNewTransfer`, `receiverTargetQuantity`,
  `donorProtectedQuantity`), `InventoryExceptionType`/`InventorySeverity`, and
  `InventoryExceptionClassification.classify(...)` (`NON_ACTIONABLE` →
  `REVIEW_REQUIRED` for signal types that can't auto-quantify → `STOCKOUT_RISK`
  (`CRITICAL` or undetermined severity) → `OVERSTOCK` → `NORMAL`) to
  `com.bapegg.stockpilot.demand`. Section 9's `HIGH` severity deliberately left
  `null` for a non-critical `STOCKOUT_RISK`, since it needs candidate-availability
  evaluation (order item 5, not yet implemented).
- Validation: `.\gradlew.bat build --rerun-tasks` (no `DB_URL`) — compile/jar/check
  pass; 99 non-skipped tests pass (up from 79), 0 failures/errors; both Oracle ITs
  skip cleanly. New tests: `InventoryProjectionTest` (9),
  `InventoryExceptionClassificationTest` (11) — an exact GS-05 before/after-inbound
  contrast (without the confirmed inbound: `CRITICAL`/`STOCKOUT_RISK`; with the real
  50-unit inbound: clears `STOCKOUT_RISK`, lands on `OVERSTOCK` given this SKU's exact
  demo policy numbers -- a 7-unit surplus recorded as computed, not adjusted toward
  `NORMAL`), plus `NON_ACTIONABLE`, each REVIEW_REQUIRED-triggering signal type,
  clear `OVERSTOCK`/`NORMAL` cases, and the undetermined-severity `STOCKOUT_RISK`
  case. Oracle-backed verification: not run, not applicable — no DB-touching code
  changed.
- Open: Candidate/route rules (order item 5), scenario quantities (order item 6),
  approval validation (order item 7). GS-06 not yet in Java;
  `INBOUND_ALREADY_COVERS` and the other section-7 rejection reasons are order item
  5's responsibility.
- Next: Implement section 7's candidate/route rejection reasons (owner mismatch,
  route, lead time, confirmed inbound, transferable stock, display minimum/package/
  capacity, pending transfer conflict), reproducing GS-06, per
  [`current-task.md`](../state/current-task.md).

## 2026-08-26 — Codex reviews projected inventory and exceptions

- Role: Codex verification/review
- Completed: Checked section 6 projection formulas, exception precedence, partial
  section 9 severity and GS-05 against rules, V6 types and boundary behavior.
- Validation: DB-free Backend `build --rerun-tasks` — 105 total, 99 passed, 6 Oracle
  skips, zero failures/errors; compile, jar and check passed.
- Decisions: Not accepted; three correctness findings remain.
- Open: Preserve negative current available as `NON_ACTIONABLE`; use final confidence/
  all quality flags for `REVIEW_REQUIRED`; prevent projection arithmetic overflow and
  add regression tests for each path.
- Next: Claude fixes the findings, then Codex re-reviews before candidate rules.

## 2026-08-26 — Fix reviewed projected-inventory findings

- Role: Claude implementation
- Completed: Fixed all three findings. `InventoryProjection.calculate` no longer throws
  for `reservedQuantity > onHandQuantity` (section 2's other `NON_ACTIONABLE` input
  condition) -- `currentAvailable` can now go negative like the two projections
  already could, and `hasNegativeProjection()` (renamed `isInputInvalid()`) checks it
  too. `InventoryExceptionClassification.classify` now takes the already-computed
  `DemandConfidence` directly instead of re-deriving a partial signal-type list, so
  `confidence == NONE || confidence == LOW` alone (already folding in every quality
  flag per section 4) decides `REVIEW_REQUIRED` -- catching cases like an
  `OOS_CENSORED`-flagged `STABLE_REPEAT` that the old list missed. The three
  projection sums are now computed in `long` with a single `Math.toIntExact`
  conversion back, so summing in-range `int` inputs that overflow now throws
  `ArithmeticException` instead of wrapping (the same defect class fixed earlier in
  `RebalanceCalculation`).
- Validation: `.\gradlew.bat build --rerun-tasks` (no `DB_URL`) — compile/jar/check
  pass; 100 non-skipped tests pass (up from 99), 0 failures/errors; both Oracle ITs
  skip cleanly. `InventoryProjectionTest` (11, +2: reserved>on-hand flags instead of
  throwing, two overflow-rejection cases) and `InventoryExceptionClassificationTest`
  (10, rewritten around confidence, +1 net: a quality-flagged `STABLE_REPEAT` now
  correctly routes to `REVIEW_REQUIRED`, plus a reserved>on-hand →
  `NON_ACTIONABLE` case). Oracle-backed verification: not run, not applicable — no
  DB-touching code changed.
- Open: Candidate/route rules (order item 5), scenario quantities (order item 6),
  approval validation (order item 7). GS-06 not yet in Java.
- Next: Codex re-reviews this fix; then implement section 7's candidate/route
  rejection reasons, reproducing GS-06, per
  [`current-task.md`](../state/current-task.md).

## 2026-08-26 — Codex re-reviews projected-inventory fixes

- Role: Codex verification/review
- Completed: Confirmed all three previous P1 fixes and boundary tests against sections
  2, 4 and 6; continued review of the same calculation boundary.
- Validation: DB-free Backend `build --rerun-tasks` — 106 total, 100 passed, 6 Oracle
  skips, zero failures/errors; compile, jar and check passed.
- Decisions: Prior findings resolved, but increment not accepted due two additional
  correctness findings.
- Open: Reject negative lead/coverage/retained/display/safety inputs consistently with
  V6, and prevent overflow in `leadTimeDays + receiverTargetCoverageDays`.
- Next: Claude fixes both boundaries with regression tests, then Codex re-reviews before
  candidate rules.

## 2026-08-26 — Fix reviewed projected-inventory follow-up findings

- Role: Claude implementation
- Completed: Added V6-matching non-negativity validation to
  `InventoryProjection.receiverAtArrivalWithoutNewTransfer`, `.receiverTargetQuantity`
  and `.donorProtectedQuantity` (lead time, target coverage, retained days, display
  minimum, safety stock). Widened `receiverTargetQuantity`'s
  `leadTimeDays + receiverTargetCoverageDays` addition (and `ceilDemand`'s `days`
  parameter) to `long` so two individually valid int inputs can no longer overflow
  before reaching the ceiling calculation. No public API break.
- Validation: `.\gradlew.bat build --rerun-tasks` (no `DB_URL`) — compile/jar/check
  pass; 104 non-skipped tests pass (up from 100), 0 failures/errors; both Oracle ITs
  skip cleanly. `InventoryProjectionTest` (15, +4: one negative-input rejection test
  per affected method, plus a widened-sum test proving
  `Integer.MAX_VALUE + 10` resolves to the correct `2147483657` rather than a wrapped
  value). Oracle-backed verification: not run, not applicable — no DB-touching code
  changed.
- Open: Candidate/route rules (order item 5), scenario quantities (order item 6),
  approval validation (order item 7). GS-06 not yet in Java.
- Next: Codex re-reviews this fix; then implement section 7's candidate/route
  rejection reasons, reproducing GS-06, per
  [`current-task.md`](../state/current-task.md).

## 2026-08-26 — Codex re-reviews projection boundary follow-up

- Role: Codex verification/review
- Completed: Verified helper nonnegative checks, widened duration arithmetic and four
  new boundary tests; reviewed exception-entry precedence.
- Validation: DB-free Backend `build --rerun-tasks` — 110 total, 104 passed, 6 Oracle
  skips, zero failures/errors; compile, jar and check passed.
- Decisions: Overflow finding accepted; increment still not accepted due one P1.
- Open: Validate all policy/timing inputs before the confidence/rate early return so
  invalid values cannot be mislabeled `REVIEW_REQUIRED`.
- Next: Claude fixes the entry-point validation and adds early-return regression tests,
  then Codex re-reviews before candidate rules.

## 2026-08-26 — Fix reviewed validation-bypass finding in exception classification

- Role: Claude implementation
- Completed: Moved `InventoryExceptionClassification.classify`'s non-negativity
  validation for `earliestArrivalLeadTimeDays`/`receiverTargetCoverageDays`/
  `retainedDays`/`displayMinimum`/`safetyStock` to the very top of the method,
  unconditionally. Previously that validation lived only inside
  `InventoryProjection`'s own methods, which the `NON_ACTIONABLE` and
  `REVIEW_REQUIRED` early-return branches never call -- so a negative policy input
  paired with an invalid projection or low/none confidence silently passed through
  uncaught, while the same input on the "healthy" path correctly threw. No public
  API break (signature unchanged, only where the check runs).
- Validation: `.\gradlew.bat build --rerun-tasks` (no `DB_URL`) — compile/jar/check
  pass; 106 non-skipped tests pass (up from 104), 0 failures/errors; both Oracle ITs
  skip cleanly. `InventoryExceptionClassificationTest` (12, +2): a negative
  `safetyStock` now throws even when `isInputInvalid()` would otherwise return
  `NON_ACTIONABLE`; a negative `retainedDays` now throws even when
  `confidence == NONE` would otherwise return `REVIEW_REQUIRED`. Oracle-backed
  verification: not run, not applicable — no DB-touching code changed.
- Open: Candidate/route rules (order item 5), scenario quantities (order item 6),
  approval validation (order item 7). GS-06 not yet in Java.
- Next: Codex re-reviews this fix; then implement section 7's candidate/route
  rejection reasons, reproducing GS-06, per
  [`current-task.md`](../state/current-task.md).

## 2026-08-26 — Codex accepts exception-entry validation fix

- Role: Codex verification/review
- Completed: Rechecked unconditional policy/timing validation and both early-return
  regression paths against sections 2, 6 and V6 constraints.
- Validation: DB-free Backend `build --rerun-tasks` — 112 total, 106 passed, 6 Oracle
  skips, zero failures/errors; compile, jar and check passed.
- Decisions: Accepted with no additional finding; projected inventory and partial
  exception/severity scope is approved.
- Open: Candidate/route rules, scenario quantities, `HIGH` severity completion and
  approval validation remain.
- Next: Claude implements section 7 candidate/route rules and GS-06 per
  [`current-task.md`](../state/current-task.md).

## 2026-08-26 — Phase 2 candidate/route rules (section 7)

- Role: Claude implementation
- Completed: Added `TransferCandidateRejectionReason` (enum in the document's fixed
  priority order), `TransferRoute` (V6-matching validation), and
  `TransferCandidateEvaluation.evaluate(...)` to `com.bapegg.stockpilot.demand`. All
  8 section-7 conditions are evaluated independently into an `EnumSet` (owner match,
  route active, lead time vs. BASE stockout timing, confirmed-inbound/pending-transfer
  passthroughs, donor transferable stock, and a combined min/package/capacity
  feasibility check), so no failing reason is dropped in favor of another.
- Decisions/flagged for review: `DISPLAY_MINIMUM_VIOLATION`'s exact scope isn't
  further disambiguated in the business-rules.md excerpt available; implemented as
  "the largest shipment donor supply/route max/receiver capacity jointly allow,
  floored to the package multiple, still clears the route's minimum quantity" —
  flagged explicitly in code and state docs for Codex to confirm.
- Validation: `.\gradlew.bat build --rerun-tasks` (no `DB_URL`) — compile/jar/check
  pass; 119 non-skipped tests pass (up from 106), 0 failures/errors; both Oracle ITs
  skip cleanly. New tests: `TransferCandidateEvaluationTest` (13) — an exact GS-06
  reproduction proving `OWNER_MISMATCH` and `LEAD_TIME_TOO_LONG` apply
  simultaneously (real multi-reason case), plus one test per remaining reason code,
  an owner-override waiver, a fully eligible candidate, and route validation.
  Oracle-backed verification: not run, not applicable — no DB-touching code changed.
- Open: Scenario quantities (order item 6, section 8), approval-request validation
  (order item 7), section 9's `HIGH` severity completion (needs this candidate
  evaluation, now available). GS-05/GS-06 fully covered at their respective layers;
  full end-to-end scenario/decision flow for all six GS still pending.
- Next: Codex reviews this increment (especially the `DISPLAY_MINIMUM_VIOLATION`
  interpretation); then implement section 8's scenario quantities
  (`NO_ACTION`/`CONSERVATIVE`/`BASE`/`AGGRESSIVE`), reproducing GS-01's three
  automatic scenarios, per [`current-task.md`](../state/current-task.md).

## 2026-08-26 — Codex reviews candidate and route rules

- Role: Codex verification/review
- Completed: Checked all-reason preservation, fixed representative priority, route
  constraints, GS-06 and the minimum/package/capacity interpretation against sections 7-8.
- Validation: DB-free Backend `build --rerun-tasks` — 125 total, 119 passed, 6 Oracle
  skips, zero failures/errors; compile, jar and check passed.
- Decisions: `DISPLAY_MINIMUM_VIOLATION` interpretation accepted; increment not accepted
  due three correctness findings.
- Open: Enforce same-SKU/different-store condition, make reasons immutable, and allow
  arrival exactly on the expected stockout date.
- Next: Claude fixes the findings with boundary tests, then Codex re-reviews before
  scenario quantities.
