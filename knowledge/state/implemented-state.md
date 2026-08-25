# Implemented State

Last verified: 2026-08-25

## MVP-2 transition boundary

- `MVP-1` is the implemented baseline described in the rest of this file. Its Oracle
  schema, deterministic 7-day calculations, Batch, REST API, React workflow and
  AI-disabled explanation boundary remain real, tested code.
- `MVP-2` Phase 1 input and Oracle Schema are implemented. The 28-day deterministic
  Java classification, candidate/scenario calculation, append-only decision service,
  draft creation API and MVP-2 screens remain unimplemented and must not be inferred
  from the presence of Schema columns or Seed rows.
- `knowledge/project.md`, `knowledge/business-rules.md` and
  `knowledge/data-model.md` carry the approved `MVP-2` demo design dated 2026-08-25.
  The active Java rule version remains `MVP-1`; Phase 1 does not add or activate
  MVP-2 calculations.
- The applied migration sequence is now `V1` through `V8`. `V6` safely backfills and
  extends the MVP-1 tables, adds normalized input/result/draft tables and conditional
  compatibility constraints; `V7` loads GS-01~GS-06 under `MVP-2-GS-V1`; `V8` records
  allowed values and the `SYNTHETIC`/`ASSUMPTION` boundary in Oracle Comments.
- Existing `V1`~`V5` files were not changed. Pre/post-upgrade Flyway checksums stayed
  `-522347871`, `-62292254`, `-2094568784`, `-2079476858`, `-429638937`.
- `data/seed/mvp2` contains the nine fixed CSV contracts: 6 products, 3 stores,
  348 inventory rows, 336 sales rows, 1 event, 1 inbound, 1 open transfer, 2 routes
  and 12 store-SKU policies. The validator preserves and checks MVP-1 first.
- The approval-record pass changed only the existing Frontend header disclosure to
  state explicitly: `SYNTHETIC` data, `ASSUMPTION` demo policy, and not actual F&F
  policy or a validated industry standard. It did not add MVP-2 behavior or change
  any calculation, API or persistence path.
- Approval-record validation (2026-08-25): approval terminology scan returned no
  stale Draft/pending markers; all four SVG/draw.io assets parsed as XML; Frontend
  TypeScript and Vite production build passed after the disclosure change.
- Rebaseline validation (2026-08-25): `scripts/local.ps1 seed-check` passed;
  TypeScript plus Vite production build passed using the bundled Node runtime;
  Oracle-backed `gradlew test --rerun-tasks` passed all 27 tests across 6 suites
  with zero skips, failures or errors. These were the pre-Phase 1 baseline results.
- Phase 1 validation (2026-08-25): a disposable clean Oracle user applied `V1`~`V8`
  and passed all 27 backend tests; the existing V5 Oracle Schema then upgraded to
  `V8` and passed the same 27 tests with `--rerun-tasks`. Readback returned the exact
  V7 counts above, zero disabled/unvalidated constraints, required table Comments,
  and complete legacy product/store backfills. Oracle reproduced GS-01~GS-06 input
  facts and rejected three rolled-back invalid writes: negative route lead time,
  a `COMPARISON_ONLY` candidate with a default quantity, and a zero-quantity draft.
  Seed validation and the bundled-Node Frontend production build also passed.

## Present in the repository

- Portfolio-oriented README with product, user, workflow, architecture, ERD and local execution
- Java 21, Spring Boot 4.1.0 and Gradle Wrapper 9.5.1 Backend scaffold
  - Web MVC, JPA, Validation, Batch, Spring Boot Flyway Starter, Flyway Oracle and Oracle JDBC
- React 19, TypeScript 5.9 and Vite 8 Frontend, now with the order 6 screens (see below)
- Oracle Database Free 23ai Docker Compose service
  - Pinned image: `gvenzl/oracle-free:23.26.2-slim-faststart`
  - Local-only port binding, health check and persistent Docker Volume
- One ignored root `.env` for Oracle and optional LLM settings, with random-password setup command
- Implemented MVP-1 domain baseline and MVP-2 Phase 1 Oracle migrations
  - `V1`: domain schema, constraints and indexes
  - `V2`: synthetic Golden Scenario Seed
  - `V3`: Spring Batch 6.0.4 Oracle metadata schema
  - `V4`: concise Korean comments for all domain tables and columns
  - `V5`: Korean user-facing catalog/store labels and expanded `2026-08-26`
    SYNTHETIC operating data (8 products, 8 stores, 64 snapshots, 448 sales rows)
  - `V6`: MVP-2-compatible input, result, decision-history and transfer-draft Schema
  - `V7`: `MVP-2-GS-V1` SYNTHETIC inputs for GS-01~GS-06
  - `V8`: MVP-2 domain Comments and explicit demo boundary
- README-rendered SVG architecture and ERD with editable draw.io sources
- CSV Seed validation for both MVP-1 and MVP-2 headers, keys, references, periods,
  quantities, uplift ordering, routes and Golden Scenario expectations
- MVP-1 implementation record, approved MVP-2 specification, data pipeline and short agent handoff skills

- JPA entities and repositories for the full domain schema: `SpInventorySnapshot`,
  `SpDailySale` (`backend/.../inventory`); `SpAnalysisRun`, `SpInventoryMetric`
  (`backend/.../analysis`); `SpProduct`, `SpStore` (`backend/.../catalog`);
  `SpRebalanceRecommendation`, `SpRebalanceDecision` (`backend/.../rebalance`).
- Pure Java deterministic calculation, independent of Spring/JPA:
  `InventoryMetricCalculation` (availability, sales rate, coverage, classification,
  priority) and `RebalanceCalculation` (receiver/donor transfer quantity), both under
  `com.bapegg.stockpilot.analysis` / `.rebalance`, following `business-rules.md`
  sections 2-4. Storage rounding to the column scale happens only at the JPA entity
  boundary (`SpInventoryMetric`).
  - Fixed a review-found defect: `RebalanceCalculation` previously took a 10-decimal
    `averageDailySales` and reused it across the `ceil` transfer boundary instead of
    the unrounded sales rate required by section 2 (e.g. one sale in seven days became
    `0.1428571429 * 7 = 1.0000000003`, inflating the receiver target by one unit).
    `RebalanceCalculation.calculate` now takes the raw integer 7-day sold quantity
    directly and computes `receiverTargetQuantity` / `donorRetainedQuantity` with an
    exact integer ceiling division (`ceilDiv`), never going through BigDecimal at that
    boundary. `InventoryMetricCalculation.averageDailySales` (still 10-decimal
    BigDecimal) remains used only for coverage-days classification and the persisted
    `average_daily_sales` display column, which the review did not flag.
  - Fixed a follow-up review defect: `soldQuantityInWindow * coverageDays` was
    evaluated as `int` before `ceilDiv`, so a large but valid input (e.g.
    `200000000 * 14 = 2800000000`) silently wrapped to a negative number and could
    produce an incorrect retained quantity or suppress a recommendation. The
    multiplication is now widened to `long` at both call sites, and `ceilDiv` uses
    `Math.toIntExact` so an out-of-`int`-range result throws rather than truncates.
  - Fixed a second follow-up review defect: `ceilDiv` previously converted to `int`
    before the caller added `SAFETY_STOCK_UNITS`, so that addition was still plain
    `int` arithmetic and could itself overflow (e.g. receiver sales
    `Integer.MAX_VALUE`: the exact target `2147483649` wrapped to `-2147483647`,
    silently suppressing the shortage). `ceilDiv` now returns `long`, and the caller
    adds `SAFETY_STOCK_UNITS` in `long` too, converting to `int` exactly once via a
    single `Math.toIntExact` after that addition — so an out-of-range target/retained
    quantity throws `ArithmeticException` instead of wrapping.
- Idempotent Spring Batch analysis (`InventoryAnalysisJobConfig`,
  `InventoryAnalysisTasklet`): a single-tasklet `inventoryAnalysisJob` keyed by
  `(analysisDate, ruleVersion)` JobParameters. Idempotency is layered: Spring Batch's
  own JobRepository (BATCH_* tables from V3) refuses to relaunch already-completed
  JobParameters, and the tasklet also checks `SpAnalysisRun` and runs the whole
  compute-and-persist sequence in one transaction so a failure leaves no partial run.
  - Fixed a review-found defect that made the first layer above a no-op: Spring Boot
    4.1's `BatchAutoConfiguration` defaults to `ResourcelessJobRepository`, an
    in-memory, single-slot implementation that never writes to the `BATCH_*` tables
    and only "remembers" a JobInstance within the same JVM/ApplicationContext —
    confirmed directly via a temporary diagnostic test (`JobRepository impl:
    ...ResourcelessJobRepository`, `BATCH_JOB_INSTANCE` count `0` even after a
    completed run). Every earlier "verified idempotent across separate JVM runs"
    validation note in this project's history was, in retrospect, an artifact of how
    those specific test runs happened to be ordered, not a real guarantee. Per Spring
    Batch 6, a JDBC-backed repository requires **both** `@EnableBatchProcessing` and
    `@EnableJdbcJobRepository` together on a `@Configuration` class (confirmed by
    testing — `@EnableJdbcJobRepository` alone has no effect); both are now on
    `InventoryAnalysisJobConfig`, using the default `tablePrefix = "BATCH_"` that
    already matches V3's schema. Re-verified with the same diagnostic: `BATCH_JOB_INSTANCE`
    count went `0` → `1` after a real launch.
  - `InventoryAnalysisGoldenScenarioIT` now also asserts directly (via `JdbcTemplate`)
    that a `COMPLETED` row exists in `BATCH_JOB_INSTANCE`/`BATCH_JOB_EXECUTION` for
    `inventoryAnalysisJob`, so a future regression back to `ResourcelessJobRepository`
    fails this test loudly instead of silently. Re-run in a genuinely fresh JVM
    process: no "Job ... launched" log line appeared before the test passed, meaning
    `jobOperator.start()` threw `JobInstanceAlreadyCompleteException` immediately —
    real, persistent, cross-process idempotency this time, not the JVM-local illusion
    from before.
  - Fixed a follow-up review defect in the above assertion: it originally counted any
    historical `COMPLETED` `BATCH_JOB_INSTANCE` row for the job name, which stays green
    forever once one genuine row exists — a future regression to
    `ResourcelessJobRepository` would still report *some* JobExecution and satisfy a
    `>= 1` count without proving anything about the current run. The test now captures
    the actual `JobExecution` from whichever branch ran (the object `jobOperator.start()`
    returns on success, or `jobRepository.getLastJobExecution(jobName, parameters)` on
    the already-complete path) and asserts that exact `JOB_EXECUTION_ID` resolves to a
    `COMPLETED` row in Oracle — an in-memory fallback's id would not resolve there at
    all, so this now fails correctly on regression. Also fixed
    `ApiGoldenScenarioIT.deleteBatchJobInstance` (used by the rerun-reporting test's
    cleanup) to join `BATCH_JOB_INSTANCE` and filter `JOB_NAME = 'inventoryAnalysisJob'`,
    since matching on `analysisDate`/`ruleVersion` parameters alone could otherwise
    delete a different job's metadata that happened to share those values.
    - Validation (2026-08-25, test-only change, no production code touched): full
      Oracle-backed `gradlew build` — 22/22 tests pass, 0 failures/errors across all 5
      suites (`StockPilotApplicationTests`, `RebalanceCalculationTest`,
      `InventoryMetricCalculationTest`, `ApiGoldenScenarioIT` 4/4,
      `InventoryAnalysisGoldenScenarioIT` 1/1). `gradlew test --rerun` without `DB_URL`:
      both Oracle ITs skip cleanly (4/4 and 1/1 skipped, 0 failures/errors), unit tests
      still run and pass — confirms AI-disabled/no-Oracle usability is preserved.
      Oracle verified directly via `sqlplus` (through `docker exec` into
      `stockpilot-oracle-1`, since the host shell has no `sqlplus` on PATH): real Golden
      Scenario data untouched (`sp_analysis_run`=1, `sp_inventory_metric`=3,
      `sp_rebalance_recommendation`=1, `sp_rebalance_decision`=1, its
      `BATCH_JOB_INSTANCE` row present with `STATUS=COMPLETED`); zero residue for the
      `2026-09-15` test-owned fixture date in both the domain tables and
      `BATCH_JOB_INSTANCE`; zero residue for `MVP-1-DECISION-IT%` rule versions.
  - Fixed a second Codex re-review defect in the same assertion: the exact-execution-id
    lookup above is not actually sufficient, because Spring Batch 6.0.4's
    `ResourcelessJobRepository` hard-codes JobExecution id `1` for every JobInstance it
    creates, and the real Golden Scenario JobInstance (`analysisDate=2026-08-25`)
    genuinely got id `1` the first time it was ever persisted to Oracle — so a future
    regression back to `ResourcelessJobRepository` would report id `1` too, coincidentally
    matching that real historical row and passing the id-based check for the wrong
    reason. Replaced the primary regression guard with a direct, proxy-safe assertion of
    the wired `JobRepository`'s implementation class:
    `AopProxyUtils.ultimateTargetClass(jobRepository)` must equal
    `SimpleJobRepository.class` (`@EnableJdbcJobRepository` wraps the genuine JDBC
    implementation in a transactional AOP proxy via `ProxyFactory`, confirmed by
    inspecting `AbstractJobRepositoryFactoryBean`'s bytecode; a fallback
    `ResourcelessJobRepository` bean is not proxied at all, so `ultimateTargetClass`
    resolves to it directly). The exact-execution-id-in-Oracle check is kept as a
    secondary confirmation that this run's own execution really persisted, not as the
    regression guard.
    - Validation (2026-08-25, test-only change): proved the new assertion actually
      catches the exact regression Codex described — temporarily removed
      `@EnableJdbcJobRepository` from `InventoryAnalysisJobConfig`, reran the test
      against the real Golden Scenario date (where id `1` already existed in Oracle
      from a prior genuine run, precisely the coincidence Codex flagged): the class
      assertion failed immediately with `expected: <SimpleJobRepository> but was:
      <ResourcelessJobRepository>`, confirming the old id-based check would have
      false-passed here but the new class check does not. Reverted the temporary
      removal (net production-code change for this round: none). Full Oracle-backed
      `gradlew build --rerun`: 22/22 tests pass, 0 failures/errors across all 5 suites.
      `gradlew test --rerun` without `DB_URL`: both Oracle ITs skip cleanly (4/4 and
      1/1 skipped). `sqlplus` (via `docker exec` into `stockpilot-oracle-1`) confirmed
      real Golden Scenario data unchanged (`sp_analysis_run`=1, `sp_inventory_metric`=3,
      `sp_rebalance_recommendation`=1, `sp_rebalance_decision`=1, its
      `BATCH_JOB_INSTANCE` row COMPLETED) and zero residue for the `2026-09-15` fixture
      date or `MVP-1-DECISION-IT%` rule versions.
- `SpProduct`, `SpStore` (`backend/.../catalog`) and `SpRebalanceDecision`
  (`backend/.../rebalance`) JPA entities/repositories, added for the order 5 API layer.
- Implementation order 5: the minimum analysis, list, detail, simulation and decision
  REST APIs, per `project.md` section 6 and `business-rules.md` sections 4 and 6.
  - `POST /api/analyses` (`AnalysisController` + `AnalysisRunService`): launches
    `inventoryAnalysisJob` via `JobOperator`; treats `JobInstanceAlreadyCompleteException`
    as a successful no-op so the endpoint is safe to call more than once per date.
    - Fixed a review-found reporting defect: `alreadyCompleted` was derived only from
      that exception, so if the domain `SpAnalysisRun` row already existed while Spring
      Batch's own JobInstance metadata did not (they are separate sources of truth —
      e.g. after a crash between the tasklet's commit and Spring Batch recording its
      own completion), the Job still ran to completion as a tasklet no-op but the
      response incorrectly said `alreadyCompleted: false`. `runAnalysis` now also
      checks the domain table for an existing completed run *before* launching the
      Job and treats that as `alreadyCompleted` too.
  - `GET /api/inventory-exceptions` / `GET /api/inventory-exceptions/{id}`
    (`InventoryExceptionController` + `InventoryExceptionService`): lists actionable
    (`STOCKOUT_RISK`/`OVERSTOCK`) metrics for the latest completed run or an explicit
    `analysisDate`, ordered by classification/priority/coverage; detail includes
    recommendations on both the receiver and donor side with any existing decision, and
    (fixed after review) rejects a `NORMAL`/`NON_ACTIONABLE` metric id with 404 the same
    way as an unknown id, since the list already excludes them from this resource space.
  - `POST /api/rebalancing-simulations` (`RebalanceSimulationController` +
    `RebalanceSimulationService`): validates `1 <= requestedQuantity <=
    donorTransferableQuantity` and returns both stores' before/after available
    quantity and coverage days without persisting anything.
  - `POST /api/rebalancing-decisions` (`RebalanceDecisionController` +
    `RebalanceDecisionService`): rejects a second decision for the same
    recommendation with 409 (terminal, per business-rules.md section 6) and enforces
    the same quantity range as simulation before persisting.
  - Request DTOs use Bean Validation (`@NotNull`/`@NotBlank`/`@Min`); domain/not-found/
    conflict errors use `ResponseStatusException` (400/404/409) rather than a custom
    exception hierarchy, to keep the web layer minimal.
- Implementation order 6: the exception list and detail/simulation screens
  (`project.md` section 3), calling the order 5 APIs with no client-side
  business-rule calculation (`project.md` section 5).
  - `frontend/src/types.ts` mirrors the Backend response/request records exactly;
    `frontend/src/api.ts` is a thin `fetch` wrapper (no HTTP library added) that throws
    an `ApiError` carrying the `detail` field from Spring's default `ResponseStatusException`
    error body, so callers can show the Backend's own message.
  - `frontend/src/components/ExceptionList.tsx`: table of actionable exceptions
    (classification/priority badges, store/product, available quantity, coverage
    days, recommended quantity) with a 상세보기 link per row.
  - `frontend/src/components/ExceptionDetail.tsx` + `RecommendationPanel.tsx`:
    evidence fields, then one panel per recommendation (receiver and donor side) with
    a quantity input, a 시뮬레이션 button showing both stores' before/after available
    quantity and coverage, and — appearing only once a simulation has run — a
    사유/담당자 form that approves or rejects at the exact quantity just simulated
    (business-rules.md section 6). An already-decided recommendation shows its
    terminal status instead of the form; the Backend's 404/409 responses are surfaced
    as-is rather than re-validated on the client.
  - `App.tsx` adds a 분석 실행 control (calls `POST /api/analyses` for a chosen date)
    above the list/detail views; no router library — view switching is a single
    `selectedId` state variable, since there are only two screens.
  - `vite.config.ts` proxies `/api` to `http://localhost:8080` in dev, so the
    Frontend never needs CORS configuration or Backend changes; `fetch('/api/...')`
    is same-origin from the browser's perspective.
  - Fixed a review-found state-coherence defect: changing `analysisDate` while a
    detail screen was open refreshed only the background list, leaving
    `selectedId`/`detail` showing the previous date's exception under the new date
    (a pending recommendation could be decided from stale date context). The
    `analysisDate`-change effect in `App.tsx` now resets `selectedId`/`detail` to
    `null` synchronously and aborts any in-flight detail request; `loadExceptions`
    and `loadDetail` each own an `AbortController` (via `useRef`) and abort the
    previous request before starting a new one, so a stale response can never
    overwrite newer state. `api.ts` gained an optional `signal` parameter on
    `listExceptions`/`getExceptionDetail` and an `isAbortError` helper so an
    intentional cancellation is never shown to the user as an error.
  - Fixed a second review-found race: `handleRunAnalysis` captured the analysis date
    via a plain closure, so if the user changed the date input before the (still
    editable) in-flight `POST /api/analyses` resolved, its `loadExceptions` call
    could fire after — and overwrite — the correct list already loaded for the newly
    selected date. Fixed two ways: the date `<input>` is now `disabled={analysisRunning}`
    (a real user cannot change it mid-request), and `handleRunAnalysis` additionally
    compares the requested date against a live `analysisDateRef` after the `await`,
    only reloading the list if the user is still on that date, as defense in depth.
  - UI smell cleanup (`.agents/skills/ui-smell-review/SKILL.md` review, user-requested
    fix): the top-of-page hero (`.hero`/`.eyebrow`/`.summary`/`.status` in
    `styles.css`, a generic SaaS-landing-page pattern — eyebrow label, up-to-6rem
    `<h1>`, subhead, status pill) pushed the actual controls (분석 실행, 예외 목록)
    down on every visit despite this being a tool inventory managers open repeatedly.
    Replaced with a slim `<header className="app-header">` (title + one subtitle line,
    the synthetic-data note now plain text via `.data-note` instead of a colored pill).
    Reduced the identical oversized shadow/radius (`border-radius: 20px`,
    `box-shadow: 0 18px 50px`) shared by `.hero`/`.panel` to a plain bordered `.panel`
    with no shadow. Removed unused `.panel ul`/`.panel li` CSS (a leftover generic
    3-column feature-card grid never rendered by any component — confirmed via grep
    that no `<ul>`/`<li>` exists in any `.tsx` file). Kept the classification/priority/
    decision badges and the table-based list/simulation layouts as-is: they aid the
    actual comparison task rather than being decorative. Verified live in the browser
    (list and detail screens both render correctly with real Golden Scenario data,
    no console errors); `pnpm run build` (tsc + vite build) passed.
- Implementation order 7: the AI-disabled explanation boundary (business-rules.md
  section 7, project.md section 6's `POST /api/inventory-exceptions/{id}/explanation`).
  New `com.bapegg.stockpilot.explanation` package (Backend only; no Frontend change,
  since order 7's scope per `current-task.md` is the boundary itself):
  - `AiProperties`: a `@ConfigurationProperties(prefix = "stockpilot.ai")` record
    binding the existing `stockpilot.ai.*` keys in `application.yml`
    (`enabled`/`provider`/`base-url`/`api-key`/`model`, sourced from `.env`'s
    `AI_ENABLED`/`AI_PROVIDER`/`AI_BASE_URL`/`AI_API_KEY`/`AI_MODEL`, all already
    present but empty/false). `StockPilotApplication` gained `@ConfigurationPropertiesScan`
    to register it.
  - `ExplanationService.explain(id)`: first calls the existing
    `InventoryExceptionService.getExceptionDetail(id)` (discarding the result) so an
    unknown or non-actionable id 404s exactly like the detail endpoint does, before
    reporting on AI availability. Returns an explicit unavailable
    `ExplanationResponse(available=false, reason, explanation=null)` — `"AI_DISABLED"`
    when `stockpilot.ai.enabled=false` (the real, current `.env` state); `"AI_UNCONFIGURED"`
    when enabled but any of provider/base-url/api-key/model is blank; `"AI_PROVIDER_NOT_IMPLEMENTED"`
    when enabled and fully configured, since no provider adapter exists yet — per
    AGENTS.md's AI boundary, one is added only once real settings are supplied and an
    adapter is built and verified against them. No HTTP call to any LLM provider exists
    in this codebase yet, by design.
  - `ExplanationController`: `POST /api/inventory-exceptions/{id}/explanation`,
    delegating directly to the service; no request body.
  - Validation: `ExplanationServiceTest` (pure JUnit 5 + Mockito, no Spring context, no
    Oracle, no LLM key needed) covers all three reason branches and 404 propagation —
    passed, including with `DB_URL` unset, confirming the boundary is testable and
    usable without an LLM API key or Oracle (project.md section 8, AGENTS.md).
    `ApiGoldenScenarioIT.explanationEndpointReportsAiDisabledForTheGoldenScenario`
    (read-only against the real Golden Scenario Gangnam exception, like
    `goldenScenarioWorksThroughTheApi`) verified the real end-to-end wiring — actual
    `.env` config binding through the real controller — returns `available=false`,
    `reason="AI_DISABLED"`. Full Oracle-backed `gradlew build --rerun`: 27/27 tests
    pass, 0 failures/errors, across 6 suites (added `ExplanationServiceTest` 4 and one
    more `ApiGoldenScenarioIT` method, now 5). `gradlew test --rerun` without `DB_URL`:
    `ExplanationServiceTest` still runs and passes (4/4, no Oracle needed); both Oracle
    IT classes skip cleanly. `sqlplus` readback confirmed the real Golden Scenario
    domain data is unchanged (`sp_analysis_run`=1, `sp_inventory_metric`=3,
    `sp_rebalance_recommendation`=1, `sp_rebalance_decision`=1) — the new endpoint is
    read-only and never writes.
- Implementation order 8: final recorded Backend/Frontend/Oracle verification pass
  against `project.md` section 8's acceptance criteria, all satisfied:
  - Clean, version-controlled Oracle schema: `flyway_schema_history` shows all 4
    migrations (`V1`-`V4`) with `success=1`.
  - Golden Scenario correctness and Batch idempotency: verified live against the
    running app (`gradlew bootRun`, real Oracle), not just tests — `POST
    /api/analyses` called twice returned the identical `analysisRunId=1`,
    `status=COMPLETED`, `alreadyCompleted=true` both times; `GET
    /api/inventory-exceptions` returned Gangnam `STOCKOUT_RISK`/`HIGH` and Hongdae
    `OVERSTOCK` with the 25-unit recommendation, matching business-rules.md section 8.
  - Recommendation/simulation results come only from the approved Java rules: `POST
    /api/rebalancing-simulations` for the Gangnam/Hongdae recommendation returned the
    exact before/after quantities `RebalanceCalculation`/`InventoryMetricCalculation`
    compute, with no AI or manual-override code path in that flow.
  - Simulation never mutates source inventory or an existing decision: `sqlplus`
    readback after the live simulation call confirmed `sp_inventory_snapshot`'s
    available quantity for both stores unchanged (Gangnam 5, Hongdae 40) and domain
    row counts unchanged (`sp_analysis_run`=1, `sp_inventory_metric`=3,
    `sp_rebalance_recommendation`=1, `sp_rebalance_decision`=1).
  - Approval/rejection require a non-blank reason and persist transactionally:
    `RebalanceDecisionRequest.reason` is `@NotBlank`; `RebalanceDecisionService`'s
    write method is `@Transactional` (confirmed in source).
  - App starts and core APIs work with AI disabled and without an API Key: the entire
    live smoke test above ran against the real, unmodified `.env` (`AI_ENABLED=false`,
    all other `AI_*` keys empty) with no error; `POST
    /api/inventory-exceptions/1/explanation` returned
    `{"available":false,"reason":"AI_DISABLED","explanation":null}` and `POST
    /api/inventory-exceptions/9999/explanation` (unknown id) returned `404`, both live.
  - AI output cannot change quantities or decision status: the explanation boundary is
    entirely read-only and has no code path that writes to `sp_inventory_metric`,
    `sp_rebalance_recommendation` or `sp_rebalance_decision` (see the `explanation`
    package above); no AI output exists yet to change anything regardless.
  - Backend tests/build and Frontend TypeScript build pass: `gradlew clean build --rerun`
    with `.env` Oracle credentials — 27/27 tests pass, 0 failures/errors, across 6
    suites. `gradlew test --rerun` with `DB_URL` and all `AI_*` variables unset — the
    16 pure-calculation tests (`InventoryMetricCalculationTest` 8,
    `RebalanceCalculationTest` 8), `StockPilotApplicationTests` (1) and
    `ExplanationServiceTest` (4) all still ran and passed; both Oracle IT classes
    skipped cleanly (no failures). `pnpm --dir frontend run build` (`tsc -b && vite
    build`) — passed.
  - Frontend verified live in a real browser against the running Backend/Oracle: list
    screen (Gangnam/Hongdae rows) and detail screen (Gangnam evidence, approved
    Hongdae recommendation) both render correctly; no new console errors.
  - Remaining work described as a concrete next task: the real LLM provider adapter
    (see "Not implemented" below) — optional, does not block the MVP.

## Not implemented

- The real LLM provider adapter behind the AI explanation boundary (only the
  disabled/unconfigured reporting exists; add an adapter once real
  `AI_PROVIDER`/`AI_BASE_URL`/`AI_API_KEY`/`AI_MODEL` settings are supplied)

## Latest Codex review

- Codex Order 7 review (2026-08-25): accepted with no actionable finding. The new
  boundary validates an existing actionable exception, returns explicit unavailable
  states, and contains no quantity/status calculation, persistence, provider HTTP
  call, or tracked secret. Oracle-backed `gradlew build --rerun-tasks` passed all 27
  tests (0 failures/errors); `ExplanationServiceTest` also passed 4/4 in a separate
  run with DB credentials removed. Order 8 remains.
- Codex Order 8 review (2026-08-25): Backend Oracle clean build passed 27/27 tests,
  Frontend production build passed, Seed validation passed (1/3/3/21), Compose config
  validated, and Oracle was healthy. Order 8 remains unaccepted because the public
  README implementation-status table still marks the completed Batch/API, UI, and
  decision/AI areas as unimplemented. The table must represent the completed
  deterministic MVP while keeping the real LLM provider adapter explicitly
  unimplemented.

## Expanded Korean demo data

- Added `V5__expand_korean_demo_seed.sql` without modifying applied migrations.
  Existing technical IDs and the `2026-08-25` Golden Scenario remain intact.
- User-facing reference data is Korean (`강남 플래그십`, `잠실 롯데월드몰점`,
  `베이직 볼캡`, `오버핏 후드 집업`, etc.). The Frontend default analysis date is
  now `2026-08-26`, the expanded dataset date.
- Oracle readback after a real API analysis: 8 products, 8 stores, 64 snapshots,
  448 seven-day sales facts; 16 stockout-risk, 24 normal and 24 overstock metrics;
  48 deterministic recommendations. Flyway V5 is recorded successful.
- Validation: Oracle-backed Backend clean build passed 27/27 tests; Frontend
  production build and the original Golden CSV Seed validation passed. The API
  returned Korean store/product labels for the expanded analysis.

## Environment observations

- Java: Temurin 21.0.11
- Gradle: committed Wrapper 9.5.1
- Docker Engine: 29.6.2; Docker Compose: 5.3.1
- Oracle: 23.26.2 in `stockpilot-oracle-1`, healthy on `127.0.0.1:1521/FREEPDB1`
- Node.js/npm/pnpm: available on the user PATH as of the order 6 session (`node v24.16.0`,
  `pnpm 11.19.0` via `C:\nvm4w\nodejs`); earlier sessions used the Codex bundled runtime instead
- LLM provider settings: intentionally empty and not required while AI is disabled

## Validation record

- Seed: `.\\scripts\\local.ps1 seed-check` — passed; products 1, stores 3, inventory 3, sales 21
- Compose: `docker compose --env-file .env.example config --quiet` — passed
- Oracle: `.\\scripts\\local.ps1 db-up` and `db-status` — container healthy
- Flyway and Backend: Backend started against Oracle; four migrations applied; schema at version 4
- Oracle readback: products 1, stores 3, inventory 3, sales 21, successful migrations 4
- Oracle comments: eight domain table comments and 54 domain column comments
- Diagram sources: both SVG and draw.io XML files parsed successfully; SVG layouts rendered for visual inspection
- Backend test: Gradle `test` — passed
- Frontend: TypeScript compile and Vite production build — passed
- Pure calculation unit tests: `InventoryMetricCalculationTest` (8), `RebalanceCalculationTest` (3) —
  passed; values match the Golden Scenario table in `business-rules.md` section 8
- Codex review rerun: `.\gradlew.bat test --rerun-tasks` — passed 12 tests and skipped
  the Oracle IT because that shell lacked `DB_URL`; the Oracle IT was then run alone
  with the ignored `.env` exported and passed. Review also reproduced the uncovered
  one-sale rounding/`ceil` defect described above.
- Fix verification: `RebalanceCalculationTest` (5, up from 3) — passed, including two
  new regression cases (`oneSaleInWindowDoesNotInflateReceiverTargetByOneUnit`,
  `oneSaleInWindowDoesNotInflateDonorRetainedByOneUnit`) that reproduce the review's
  exact scenario and assert the correct exact-integer target/retained quantities.
- Full Gradle `build` (compile, jar, all 15 tests including the Oracle IT, check), run
  with `.env` credentials exported — passed.
- Oracle integration: `InventoryAnalysisGoldenScenarioIT` (`@EnabledIfEnvironmentVariable(DB_URL)`,
  run with `.env` credentials exported) — passed after the fix, both as part of the full
  build above and standalone. Verified against the real Oracle instance with a direct
  `sqlplus` readback after the fix:
  - Gangnam: STOCKOUT_RISK / HIGH; Hongdae: OVERSTOCK; Seongsu: NORMAL
  - One recommendation: Hongdae → Gangnam, recommended 25, shortage 25, transferable 30
    (unchanged from before the fix, because 28 and 4 sold-quantities do not trigger the
    boundary defect; the fix changes behavior only for inputs like the regression cases)
  - Row counts stayed at 1 `sp_analysis_run` / 3 `sp_inventory_metric` / 1
    `sp_rebalance_recommendation` after rerunning the Job — idempotency still holds
- Confirmed `gradlew test` still skips (not fails) `InventoryAnalysisGoldenScenarioIT`
  when `DB_URL` is unset, after the fix.
- Codex follow-up review: `.\gradlew.bat build --rerun-tasks` with ignored `.env`
  exported — all 15 tests passed, including Oracle IT with zero skips. Static boundary
  verification reproduced the uncovered `int` multiplication overflow described above.
- Overflow fix verification: `RebalanceCalculationTest` (6, up from 5) — passed,
  including `largeSoldQuantityDoesNotOverflowDuringCeilingCalculation`, which
  reproduces Codex's exact `200_000_000 * 14` example and asserts the correct
  `donorTransferableQuantity` (`99_999_998`), not a wrapped/negative value.
- Full Gradle `build` (compile, jar, all 16 tests including the Oracle IT, check), run
  with `.env` credentials exported — passed.
- Oracle integration after the overflow fix: `InventoryAnalysisGoldenScenarioIT` — passed
  again, both in the full build above and via a direct `sqlplus` readback: same 25-unit
  Hongdae → Gangnam recommendation, same row counts (1 `sp_analysis_run` /
  3 `sp_inventory_metric` / 1 `sp_rebalance_recommendation`) — the overflow fix does not
  change Golden Scenario behavior since 28/4/200 sold-quantities are all far below the
  overflow threshold.
- Confirmed `gradlew test` still skips (not fails) `InventoryAnalysisGoldenScenarioIT`
  when `DB_URL` is unset, after the overflow fix.
- Codex second follow-up review: `.\gradlew.bat build --rerun-tasks` with ignored
  `.env` exported — all 16 tests passed, including Oracle IT with zero skips. Boundary
  verification reproduced the remaining safety-stock addition overflow described above.
- Safety-stock overflow fix verification: `RebalanceCalculationTest` (8, up from 6) —
  passed, including `receiverTargetOverflowAfterAddingSafetyStockIsRejectedNotWrapped`
  and `donorRetainedOverflowAfterAddingSafetyStockIsRejectedNotWrapped`, which reproduce
  Codex's exact `Integer.MAX_VALUE` scenario for both the receiver and donor boundary
  and assert `ArithmeticException` (rejected), not a wrapped negative quantity.
- Full Gradle `build` (compile, jar, all 18 tests including the Oracle IT, check), run
  with `.env` credentials exported — passed.
- Oracle integration after the safety-stock fix: `InventoryAnalysisGoldenScenarioIT` —
  passed again, both in the full build above and via a direct `sqlplus` readback: same
  25-unit Hongdae → Gangnam recommendation, same row counts (1 `sp_analysis_run` /
  3 `sp_inventory_metric` / 1 `sp_rebalance_recommendation`) — this fix does not change
  Golden Scenario behavior since its sold-quantities are nowhere near `Integer.MAX_VALUE`.
- Confirmed `gradlew test` still skips (not fails) `InventoryAnalysisGoldenScenarioIT`
  when `DB_URL` is unset, after the safety-stock overflow fix.
- Codex final review: `.\gradlew.bat build --rerun-tasks` with ignored `.env`
  exported — 18 tests passed, zero skipped and zero failed, including Oracle IT. The
  widened multiplication, ceiling division, safety-stock addition and final checked
  conversion were reviewed with no additional finding.
- Implementation order 5 (APIs): `ApiGoldenScenarioIT` (new, `@EnabledIfEnvironmentVariable(DB_URL)`)
  drives the whole API surface with `MockMvc` against the real Oracle instance in one
  flow — `POST /api/analyses`, `GET /api/inventory-exceptions` (list), `GET
  /api/inventory-exceptions/{id}` (detail), `POST /api/rebalancing-simulations`, `POST
  /api/rebalancing-decisions` and a second decision attempt — and passed, run with
  `.env` credentials exported. Verified through the API (not just the repository layer):
  - List returns exactly 2 actionable exceptions (Gangnam `STOCKOUT_RISK`/`HIGH`,
    Hongdae `OVERSTOCK`; Seongsu `NORMAL` correctly excluded), Gangnam's
    `recommendedQuantity` is 25.
  - Detail for Gangnam shows one receiver-side recommendation to `STORE-HONGDAE` with
    `recommendedQuantity` 25.
  - Simulating `requestedQuantity=20` returns receiver 5→25 available and donor 40→20
    available, matching `available ± requestedQuantity`.
  - The decision is idempotent-safe to rerun against the persistent Oracle instance
    (creates the first decision, or verifies a prior one, depending on prior test
    runs); a second decision attempt for the same recommendation always returns 409,
    and a direct `sqlplus` readback confirmed exactly one `sp_rebalance_decision` row
    (`APPROVED`, quantity 25) after the run.
- Full Gradle `build` (compile, jar, all 19 tests including both Oracle ITs, check),
  run with `.env` credentials exported — passed.
- Confirmed `gradlew test` still skips (not fails) both `InventoryAnalysisGoldenScenarioIT`
  and the new `ApiGoldenScenarioIT` when `DB_URL` is unset.
- Codex order 5 review: `.\gradlew.bat build --rerun-tasks` with ignored `.env`
  exported — 19 tests passed, zero skipped and zero failed, including both Oracle ITs.
  Review found two remaining gaps:
  - `GET /api/inventory-exceptions/{id}` does not enforce the same actionable
    classification boundary as the list and returns `NORMAL`/`NON_ACTIONABLE` metrics
    as successful exception details when their IDs are supplied directly.
  - `ApiGoldenScenarioIT` skips the decision-create request whenever a decision from a
    prior persistent-Oracle run exists, so a green rerun does not exercise or prove the
    `POST /api/rebalancing-decisions` 201 creation path.
- Fixed both order 5 review gaps:
  - `InventoryExceptionService.getExceptionDetail` now filters on the same `ACTIONABLE`
    (`STOCKOUT_RISK`/`OVERSTOCK`) set the list uses before returning a metric, via
    `Optional.filter`, so a `NORMAL`/`NON_ACTIONABLE` id falls through to the same 404
    as an unknown id.
  - `ApiGoldenScenarioIT` now deletes any decision it previously left on the Golden
    Scenario recommendation (via `SpRebalanceDecisionRepository`) before asserting
    creation, so the `POST /api/rebalancing-decisions` 201 path and the subsequent 409
    repeat-rejection are both unconditionally exercised on every run, not skipped.
  - Added a second test, `getExceptionDetailRejectsNormalClassification`, which looks
    up the Golden Scenario's Seongsu (`NORMAL`) metric directly via the repository and
    asserts `GET /api/inventory-exceptions/{id}` returns 404 for it.
- Fix verification: `gradlew build` (compile, jar, 20 tests including both Oracle ITs,
  check), run with `.env` credentials exported — passed (`ApiGoldenScenarioIT` now has
  2 test methods). Rerunning `ApiGoldenScenarioIT` alone a second time against the same
  Oracle instance still passed, confirming the decision-cleanup makes it deterministic;
  a direct `sqlplus` readback showed exactly one `sp_rebalance_decision` row (`APPROVED`,
  quantity 25) afterward, not an accumulating history.
- Confirmed `gradlew test` still skips (not fails) both Oracle ITs when `DB_URL` is unset.
- Codex follow-up review confirmed the actionable detail filter and Seongsu 404 test,
  but found the decision-test isolation unsafe: `ApiGoldenScenarioIT` deletes whatever
  persisted decision currently belongs to the Golden Scenario recommendation, then
  replaces it with an integration-test approval. This can destroy an existing terminal
  audit record (status, reason, actor and timestamp) merely by running tests.
- The same flow simulates quantity 20 but approves quantity 25, so it still does not
  demonstrate the business-rule workflow that the selected approval quantity has the
  valid simulation immediately preceding it.
- Safe verification without `DB_URL`: `.\gradlew.bat build --rerun-tasks` — build
  passed; 17 tests executed successfully and all 3 Oracle-conditioned test methods
  skipped. Oracle-enabled `ApiGoldenScenarioIT` was intentionally not rerun because of
  the destructive cleanup above.
- Fixed by redesign, not just a patch: `decisionWorkflowApprovesWithinSimulationRange`
  (new, third `ApiGoldenScenarioIT` method) never touches the real Golden Scenario
  recommendation. It builds its own `SpAnalysisRun` (rule version
  `MVP-1-DECISION-IT`, never colliding with the real `MVP-1` run), `SpInventoryMetric`
  rows and `SpRebalanceRecommendation` directly via repositories — reusing the real,
  immutable Gangnam/Hongdae `SpInventorySnapshot` rows as evidence, since a snapshot
  can back metrics from more than one analysis run — then exercises
  `POST /api/rebalancing-simulations` and `POST /api/rebalancing-decisions` on that
  test-owned recommendation, and deletes all of it (metrics, recommendation, decision,
  run) in a `finally` block both before and after. `goldenScenarioWorksThroughTheApi`
  is now read-only against the real data (analyze/list/detail/simulate only; decision
  assertions moved out entirely). Both the simulated and approved quantity are now the
  same value (20), demonstrating the simulate-then-decide-at-that-quantity workflow
  from business-rules.md section 6.
- Fix verification: `gradlew build` (compile, jar, 21 tests including both Oracle ITs,
  check), run with `.env` credentials exported — passed (`ApiGoldenScenarioIT` now 3
  test methods). Reran `ApiGoldenScenarioIT` alone a second time against the same
  Oracle instance — passed again. A direct `sqlplus` readback after both runs showed
  the real Golden Scenario decision (`recommendation_id=1`) completely unchanged
  (`reason='Golden Scenario API verification'`, unchanged since the prior session) —
  proof the isolated test never touches it — plus exactly one real `sp_analysis_run`
  row and zero leftover `MVP-1-DECISION-IT` rows (metrics, recommendation or decision),
  confirming the test's own cleanup leaves no trace either way.
- Confirmed `gradlew test` still skips (not fails) all Oracle-conditioned test methods
  when `DB_URL` is unset.
- Codex final Order 5 follow-up review: Oracle-enabled `.\gradlew.bat build
  --rerun-tasks` passed all 21 tests, followed by a second standalone execution of all
  three `ApiGoldenScenarioIT` methods, which also passed. Static review confirmed the
  real Golden Scenario decision is read-only, cleanup is scoped to the distinct
  `MVP-1-DECISION-IT` run, FK deletion order is correct, and simulation/approval both
  use quantity 20. No additional finding.

Order 5 is complete: the actionable detail boundary and the decision-test isolation and
matching-quantity gaps are both fixed and Oracle-verified, without ever mutating real
decision state.
- Implementation order 6 (Frontend): `pnpm run build` (`tsc -b && vite build`) —
  passed, with Node.js/pnpm available on PATH this session (`node v24.16.0`, unlike
  the "unavailable" note in Environment observations from an earlier session).
- Live browser verification (not just the build) with the Backend running against
  Oracle and the Vite dev server proxying `/api`:
  - List screen for the real Golden Scenario (2026-08-25) showed exactly the 2
    actionable rows (Gangnam `STOCKOUT_RISK`/`HIGH`/25 recommended; Hongdae
    `OVERSTOCK`), matching the API contract; Seongsu (`NORMAL`) correctly absent.
  - Clicking into Gangnam's already-decided real recommendation correctly rendered
    the terminal "승인됨 (수량 25)" state with no simulate/decide form, proving the
    already-decided branch renders correctly against real data.
  - To visually verify the simulate → decide golden path itself (the real
    recommendation already had a decision from earlier sessions), a temporary,
    clearly test-owned analysis run/metrics/recommendation was inserted directly in
    Oracle for a distinct date (2026-08-26, same `MVP-1` rule version, reusing the
    real read-only Gangnam/Hongdae snapshots) — the same isolation pattern
    `decisionWorkflowApprovesWithinSimulationRange` uses. Through the browser: typed
    quantity 20, clicked 시뮬레이션 — table showed Gangnam 5→25 available /
    1.25일→6.25일 and Hongdae 40→20 available / 70일→35일 (network: `POST
    /api/rebalancing-simulations` → 200); filled 사유/담당자, clicked 승인 — panel
    updated to "승인됨 (수량 20)" with the form hidden (network: `POST
    /api/rebalancing-decisions` → 201); no browser console errors throughout.
  - The temporary 2026-08-26 fixture was fully deleted afterward via `sqlplus`; a
    readback confirmed zero rows remain for that date and the real Golden Scenario
    decision (`recommendation_id=1`) is still exactly as it was, untouched by any of
    this manual verification.
- Full Oracle-enabled `gradlew build` rerun after the Frontend/browser work (Backend
  code itself did not change) — all 21 tests still passed, confirming the manual
  Oracle fixture insert/cleanup left the database in the expected state (1
  `sp_analysis_run` / 3 `sp_inventory_metric` / 1 `sp_rebalance_recommendation` / 1
  `sp_rebalance_decision`, all real Golden Scenario rows).

Order 1-6 are complete and Oracle-verified end to end, including the Frontend screens
exercised live in a browser. Only the optional AI explanation boundary (order 7) and the
final recorded verification pass (order 8) remain; LLM integration must not be presented
as implemented.

- Codex Order 6 follow-up review (2026-08-25): Frontend production build passed; the
  Oracle-enabled Backend `build --rerun-tasks` passed all tasks. Live browser verification
  reconfirmed the 2026-08-25 list and terminal detail branch, but found one open state
  defect: changing the analysis date from an open detail leaves the old `selectedId` and
  `detail` rendered under the new date. Order 6 needs this date-context reset fixed and
  reverified before final acceptance.
- Fix verification: `pnpm run build` (`tsc -b && vite build`) — passed after the
  `App.tsx`/`api.ts` changes described above.
- Live browser re-verification of the exact defect scenario: with the Backend running
  against Oracle, opened the real, already-decided Gangnam detail (2026-08-25, "승인됨
  (수량 25)"), then changed 분석 기준일 to an isolated test-owned date (2026-08-26, same
  `MVP-1` rule version, reusing the real read-only snapshots — inserted and fully
  deleted via `sqlplus`, never touching the real decision). The screen immediately fell
  back to "재고 예외 목록" for the new date instead of continuing to show the stale
  Gangnam detail. `read_network_requests` showed the superseded
  `GET /api/inventory-exceptions?analysisDate=2026-08-25` request as `net::ERR_ABORTED`
  followed by the `analysisDate=2026-08-26` request completing normally — direct
  evidence the `AbortController` guard cancels in-flight requests, not just their
  results. No browser console errors. Afterward, `sqlplus` confirmed zero rows remain
  for 2026-08-26 and the real decision (`recommendation_id=1`,
  `reason='Golden Scenario API verification'`) is unchanged.
- Full Oracle-enabled `gradlew build` rerun after the browser verification (Backend
  code unchanged this round) — all 21 tests still passed, confirming the database is
  back to exactly 1/3/1/1 real Golden Scenario rows.

Order 1-6 are complete and Oracle-verified end to end, including the Frontend screens
exercised live in a browser and the reviewed date/detail state-coherence defect fixed
and re-verified. Only the optional AI explanation boundary (order 7) and the final
recorded verification pass (order 8) remain; LLM integration must not be presented as
implemented.

- Codex Order 6 fix re-review (2026-08-25): the original stale-detail path is correctly
  addressed in code by clearing detail state on date changes and aborting superseded
  list/detail requests. Fresh Frontend production build and Oracle-enabled Backend
  `build --rerun-tasks` both passed. One related race remains: an analysis POST for date
  A can resolve after selecting date B, then call `loadExceptions(A)` and abort/replace
  B's list request because the date input remains enabled while analysis runs.
- Fix verification: `pnpm run build` (`tsc -b && vite build`) — passed after adding
  `disabled={analysisRunning}` to the date input and the `analysisDateRef` comparison
  guard in `handleRunAnalysis`.
- Live browser verification of the exact race window (not just the build): with the
  Backend running against Oracle, patched `window.fetch` in the page to delay only
  `POST /api/analyses` by 15 seconds (a realistic network-throttle technique — the
  real app code path, not a mock), then clicked 분석 실행 for the real, already-analyzed
  2026-08-25 date. For the full 15s delay, `document.querySelector('input[type="date"]').disabled`
  was `true` and the button read "실행 중…", confirming a real user cannot change the
  date while the request is in flight — the race is structurally unreachable through
  the UI, not merely guarded against. After the delayed request resolved, the input
  and button returned to normal and the "이미 완료되어 있습니다" message showed
  correctly; no console errors. Since the delayed request targeted the real,
  already-COMPLETED analysis date, the server-side no-op meant no Oracle state changed
  by this test — confirmed via `sqlplus` (`sp_analysis_run` count 1, `sp_rebalance_decision`
  count 1, unchanged).
- Full Oracle-enabled `gradlew build` rerun after the browser verification (Backend
  code unchanged this round) — all 21 tests still passed.

Order 1-6 are complete and Oracle-verified end to end, including the Frontend screens
exercised live in a browser and both reviewed Frontend races (stale detail on date
change; stale analysis-completion reload racing a date change) fixed and re-verified.
Only the optional AI explanation boundary (order 7) and the final recorded verification
pass (order 8) remain; LLM integration must not be presented as implemented.

- Codex final Order 6 follow-up (2026-08-25): accepted the disabled date input and
  live-date ref guard. Fresh Frontend build passed; Oracle-enabled Backend build ran 21
  tests with zero failures/errors/skips; live Oracle list and analysis rerun remained
  functional. A separate API-reporting gap was observed: when the domain
  `sp_analysis_run` already exists but Spring Batch metadata has been rebuilt, the first
  POST starts a no-op Job execution and returns `alreadyCompleted=false`; the tasklet
  finds the existing domain run and performs no analysis. The UI consequently reports
  "analysis completed" rather than "already completed". The following POST returns
  true. Existing API tests assert only HTTP 200 and do not cover this response field.
- Fixed: `AnalysisRunService.runAnalysis` now checks the domain `SpAnalysisRun` table
  for an existing completed run *before* launching the Job, treating that (in addition
  to Spring Batch's own `JobInstanceAlreadyCompleteException`) as `alreadyCompleted`.
- Fix verification: new test `runAnalysisReportsAlreadyCompletedWhenDomainRunPredatesBatchMetadata`
  in `ApiGoldenScenarioIT` reproduces the exact defect state directly: inserts a
  domain-only, already-`COMPLETED` `SpAnalysisRun` for a never-before-used date
  (2026-09-15) under the real `MVP-1` rule version (the API always uses that fixed
  version, so a distinct rule version can't be used the way `DECISION-IT` did), so
  Spring Batch's own JobInstance metadata for those exact parameters does not yet
  exist. Calls `POST /api/analyses` for that date and asserts the response reports
  `alreadyCompleted: true` and `status: COMPLETED`. Cleans up its own row before and
  after in a `finally` block; never touches the real 2026-08-25 Golden Scenario run.
- `gradlew build` (compile, jar, 22 tests including all Oracle ITs, check), run with
  `.env` credentials exported — passed (`ApiGoldenScenarioIT` now 4 test methods).
- `sqlplus` readback after the run: only the real Golden Scenario rows remain — 1
  `sp_analysis_run` (2026-08-25/MVP-1), 3 `sp_inventory_metric`, 1
  `sp_rebalance_recommendation`, 1 `sp_rebalance_decision` (unchanged reason text) —
  confirming the new test's `2026-09-15` fixture left no trace.
- Confirmed `gradlew test` still skips (not fails) all four Oracle-conditioned test
  methods when `DB_URL` is unset. No Frontend changes this round, so no Frontend
  rebuild was needed.

Order 1-6 are complete and Oracle-verified end to end: the Backend/API layer (including
the analysis-rerun reporting fix), all reviewed Frontend races, and the Frontend screens
themselves have each been fixed and re-verified live. Only the optional AI explanation
boundary (order 7) and the final recorded verification pass (order 8) remain; LLM
integration must not be presented as implemented.

- Codex analysis-rerun fix review (2026-08-25): accepted the domain pre-check and
  non-destructive API assertion; Oracle-enabled `build --rerun-tasks` passed 22 tests
  with zero failures/errors/skips. A separate persistence gap remains: immediately
  after those Job executions, direct Oracle readback returned zero rows in
  `BATCH_JOB_INSTANCE`, `BATCH_JOB_EXECUTION`, and `BATCH_JOB_EXECUTION_PARAMS`.
  Therefore the documented JDBC JobRepository layer is not observably persisting to
  the Flyway-owned `BATCH_*` tables, and restart-level Batch idempotency has not been
  demonstrated; current correctness is supplied by the domain-run guard.
- Root cause confirmed via a temporary diagnostic test (deleted after use): the
  `JobRepository` bean was `org.springframework.batch.core.repository.support.
  ResourcelessJobRepository` — Spring Boot 4.1's default when neither
  `@EnableBatchProcessing` nor `@EnableJdbcJobRepository` is present. It holds exactly
  one `JobInstance` in a private field and never touches the `DataSource`, which is
  why `BATCH_JOB_INSTANCE` stayed empty despite the Job appearing idempotent — that
  appearance came only from Java-object state alive within one JVM/ApplicationContext,
  never a real cross-process guarantee.
- Fixed: added both `@EnableBatchProcessing` and `@EnableJdbcJobRepository` to
  `InventoryAnalysisJobConfig` (Spring Batch 6 requires both together on a
  `@Configuration` class for JDBC persistence — confirmed via the official docs and by
  testing that `@EnableJdbcJobRepository` alone changed nothing). Default
  `tablePrefix = "BATCH_"` already matches V3's schema exactly, so no migration
  change was needed. Re-ran the diagnostic: `BATCH_JOB_INSTANCE` count went from `0`
  to `1` immediately after a real Job launch, confirming genuine JDBC persistence.
- Added the Oracle assertion Codex asked for directly in
  `InventoryAnalysisGoldenScenarioIT`: a `JdbcTemplate` query asserting a `COMPLETED`
  row exists in `BATCH_JOB_INSTANCE`/`BATCH_JOB_EXECUTION` for `inventoryAnalysisJob`
  after the idempotent-rerun assertions, so a future regression back to
  `ResourcelessJobRepository` is caught here instead of only being visible via an
  empty Oracle table.
- Verification, not just the build: reran `InventoryAnalysisGoldenScenarioIT` alone in
  a genuinely fresh JVM (`gradlew test --tests ... --rerun-tasks`, a separate process
  from the one that first completed it). No "Job ... launched" log line appeared at
  all before the test passed — meaning `jobOperator.start()` threw
  `JobInstanceAlreadyCompleteException` immediately from Oracle-persisted state,
  proving real cross-process idempotency this time (the new `BATCH_JOB_INSTANCE`
  assertion also passed).
- `ApiGoldenScenarioIT`'s `runAnalysisReportsAlreadyCompletedWhenDomainRunPredatesBatchMetadata`
  is the only test here that actually launches the Job (as a tasklet no-op), so it now
  also cleans up the `BATCH_JOB_INSTANCE`/`BATCH_JOB_EXECUTION`/`_PARAMS` rows it
  creates (identified by JobParameters, not the hashed `JOB_KEY`, via a new
  `deleteBatchJobInstance` helper) in addition to the domain `SpAnalysisRun` row —
  this was discovered because the first post-fix full-suite run left a stray
  `2026-09-15` JobInstance behind, caught by inspecting `BATCH_JOB_INSTANCE` directly
  rather than assuming the existing domain-table cleanup was sufficient.
- `gradlew build` (compile, jar, 22 tests including all Oracle ITs, check), run with
  `.env` credentials — passed, after both the JDBC-repository fix and the added
  cleanup. `sqlplus` readback confirmed exactly one `BATCH_JOB_INSTANCE` row remains
  (`inventoryAnalysisJob`, `analysisDate=2026-08-25`, `ruleVersion=MVP-1`,
  `COMPLETED`), alongside the unchanged real domain rows (1/3/1/1) — no test-owned
  residue in either the domain tables or Spring Batch's own metadata tables.
- Confirmed `gradlew test` still skips (not fails) all Oracle-conditioned tests when
  `DB_URL` is unset.

Order 1-6 are complete and Oracle-verified end to end, and Spring Batch's own
JobRepository is now genuinely JDBC-backed and persistent — the idempotency this
project has relied on and documented throughout is a real, cross-process guarantee,
not an in-memory illusion. Only the optional AI explanation boundary (order 7) and the
final recorded verification pass (order 8) remain; LLM integration must not be
presented as implemented.

- Codex JDBC JobRepository follow-up (2026-08-25): official Spring Batch 6 docs
  confirm the two-annotation configuration. Oracle full build passed 22 tests; direct
  readback showed one completed Golden JobInstance and zero `2026-09-15` parameters;
  a separate no-daemon JVM Golden Scenario IT passed. Production persistence is
  accepted. Test hardening remains: the persistence assertion accepts any historical
  completed row, and cleanup matches parameters without restricting `JOB_NAME`.

- Codex test-guard re-review (2026-08-25): cleanup is now correctly restricted to
  `inventoryAnalysisJob` and is accepted. The exact-execution-id assertion still
  false-passes: bytecode inspection of Spring Batch 6.0.4 showed
  `ResourcelessJobRepository.createJobExecution` always assigns id `1`, while direct
  Oracle readback showed the historical Golden execution also has id `1`. Oracle full
  build passed 22 tests, but this assertion does not detect the intended regression.

- Codex final JDBC regression-guard review (2026-08-25): accepted the proxy-safe
  `AopProxyUtils.ultimateTargetClass(jobRepository) == SimpleJobRepository.class`
  assertion; it directly distinguishes the JDBC repository from the resourceless
  fallback independently of historical Oracle IDs. Oracle `build --rerun-tasks`
  passed all 22 tests. Post-build readback remained domain `1/3/1/1`, one real Batch
  JobInstance, and zero `2026-09-15` parameters. No additional finding.
