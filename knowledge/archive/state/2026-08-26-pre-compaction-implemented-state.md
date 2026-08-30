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

## MVP-2 Phase 2 progress

- New `com.bapegg.stockpilot.demand` package (Backend only, pure Java, no Spring/JPA/Oracle
  dependency), implementing Phase 2 order items 1-2 from `current-task.md`:
  - `DemandAnalysisRules`: the MVP-2 `ASSUMPTION` constants from `business-rules.md`
    sections 1-3 needed so far (28-day window, 14-day minimums, scale-12 internal precision,
    stable-repeat CV threshold, spike/bulk-transaction thresholds).
  - `DailyDemandObservation`: immutable one-day store-SKU record (on-hand, reserved, sold,
    transaction count, max transaction quantity) with the same shape/validation as the
    `sp_daily_sale` `ck_sp_sale_mvp2_detail` Check Constraint; exposes `availableQuantity()`,
    `observable()` and `oosCensored()` per section 2.
  - `DemandObservationWindow`: immutable 28-day window for one store-SKU, validating exactly
    28 consecutive dates ending the day before `analysisDate` and `productLaunchDate <=
    analysisDate`; exposes `launchDaysElapsed()`.
  - `DemandObservationStatistics.calculate(window)`: pure function computing
    `observableDayCount`, the `OOS_CENSORED` quality flag, `activeWeekCount`, `salesDayRatio`
    (scale 6), the four fixed weeks' population coefficient of variation (scale 12, `null`
    when the weekly mean is 0, via `BigDecimal.sqrt(MathContext)`), `maxDailySales`/
    `medianDailySales`/`madDailySales` (median equivalent to section 5's linear-interpolation
    quantile at `p=0.5`; all three `null` together when there are zero observable days),
    `totalWindowSales`, the section-3 spike threshold/candidate/evidence date, and the
    section-3 single-bulk-transaction flag. Demand signal classification itself (the
    `DATA_INSUFFICIENT`/`KNOWN_EVENT`/.../`VARIABLE` decision, which also needs event and
    plan-horizon input), confidence and low/base/high demand rates (section 5) are explicitly
    out of scope for this class and remain for the next increment.
  - Storage rounding to the `sp_inventory_metric` column scales happens only at a future JPA
    entity boundary, matching the existing `InventoryMetricCalculation` pattern; none of
    these classes touch Oracle, an entity, or a migration.
- Validation: `DailyDemandObservationTest` (8), `DemandObservationWindowTest` (5) and
  `DemandObservationStatisticsTest` (5) — all pass. The statistics test reproduces the real
  `data/seed/mvp2` GS-01 (stable, no spike), GS-03 (single-transaction spike + bulk-transaction
  flag, evidence date 2026-09-20) and GS-04 (14 OOS-censored days then 14 observable days,
  exactly at the 14-day minimum boundary) scenarios with exact hand-derived assertions,
  plus an all-OOS edge case (every statistic `null`/zero, not a false zero) and an
  even-observable-day-count median regression case. `.\gradlew.bat test --rerun-tasks`
  (no `DB_URL`): 45 tests total (up from 27), 0 failures/errors; both Oracle IT classes still
  skip cleanly, confirming this increment needs no database. Oracle-backed verification:
  not run, and not applicable — no migration, entity, or persistence code changed.
- Not yet implemented from Phase 2's order: signal classification (order item 3, needs
  `SP_DEMAND_EVENT` overlap and the plan-horizon calculation), low/base/high demand rates
  (order item 3), inventory projection/exceptions (order item 4), candidate/route rules
  (order item 5), scenario quantities (order item 6), and approval-request validation
  (order item 7). GS-02, GS-05 and GS-06 are not yet reproduced in Java.
- Codex review fix (2026-08-26): `DailyDemandObservation` previously decided `observable()`
  from `onHand - reserved >= 1` alone, silently discarding V6's `snapshot_at` and
  `out_of_stock_flag` columns and unable to preserve section 2's "재고 기준시각이 유효하고
  판매 가능 재고가 1개 이상" wording. Both columns are now real fields: `outOfStockFlag`
  (an independent, authoritative input — not re-derived from the quantity math, since real
  data can flag a day OOS, e.g. damaged/held stock, even when the ledger still shows a
  positive quantity) and `snapshotAt`, with a new `snapshotReferenceValid()` check (the
  snapshot's own calendar date must equal the day it claims to represent).
  `observable()` is now `snapshotReferenceValid() && !outOfStockFlag && availableQuantity()
  >= 1`. A new static factory, `DailyDemandObservation.of(date, onHand, reserved, sold,
  transactionCount, maxTransactionQuantity)`, keeps the simple call sites (all prior GS-01/
  GS-03/GS-04 tests) unchanged by deriving `outOfStockFlag` from the quantity and dating
  `snapshotAt` to `date`, exactly like the V6 backfill; the canonical constructor is used
  directly only where a test needs to model an explicit-flag/quantity divergence or a
  stale/mis-dated snapshot. No public API, entity, or migration changed.
  - Validation: `DailyDemandObservationTest` gained 3 tests (explicit OOS flag overriding a
    positive quantity, a mismatched snapshot date, and a rejected `null` `snapshotAt`);
    `DemandObservationStatisticsTest` gained 2 tests confirming both new inputs actually
    remove a day from `observableDayCount`/`oosCensoredDayCount`/CV/spike evidence while
    still counting its raw sales in `totalWindowSales`. `.\gradlew.bat test --rerun-tasks`
    (no `DB_URL`): 44 non-skipped tests pass (up from 39), 0 failures/errors; both Oracle IT
    classes still skip cleanly. Oracle-backed verification: not run, not applicable — no
    migration, entity, or persistence code changed.
- Codex re-review fix (2026-08-26): the first fix above still folded a mismatched-snapshot
  day into `oosCensored()`, so an untrustworthy (but not actually stocked-out) day incorrectly
  set the `OOS_CENSORED` quality flag; separately, a row that was explicitly OOS-flagged but
  carried a positive `soldQuantity` was excluded from demand-evidence distributions yet still
  counted in `totalWindowSales`, letting a data contradiction inflate the spike-share
  denominator. Fixed by separating the two invariants:
  - `DailyDemandObservation` now exposes `stockedOut()` (explicit flag or zero available
    quantity) and `invalidSnapshotReference()` (mismatched `snapshotAt`) as distinct
    predicates. `oosCensored()` is `snapshotReferenceValid() && stockedOut()` — real
    stockouts only; a day can be neither `observable()` nor `oosCensored()` when its
    snapshot reference is untrustworthy.
  - The constructor now rejects `stockedOut() && soldQuantity > 0` outright: a genuinely
    stocked-out day can never carry a recorded sale, so `oosCensored` days always contribute
    zero to any sales total by construction, not by careful call-site bookkeeping.
  - `DemandObservationStatistics` gained an `invalidSnapshotDayCount` field and a three-way
    bucketing loop (observable / real-`oosCensored` / invalid-snapshot); `totalWindowSales`
    now excludes invalid-snapshot days' sales (real-OOS days already always contribute zero
    per the new constructor invariant).
  - Validation: `DailyDemandObservationTest` (13, +2: reject explicit-OOS-with-sales, reject
    zero-available-with-sales) and `DemandObservationStatisticsTest` (7, both prior
    regression tests rewritten to assert `oosCensored=false`/`invalidSnapshotDayCount=1` for
    the mismatched-snapshot case and the corrected `totalWindowSales` value for both cases).
    `.\gradlew.bat build --rerun-tasks` (no `DB_URL`): compile/jar/check all pass; 46
    non-skipped tests pass (up from 44), 0 failures/errors; both Oracle IT classes skip
    cleanly. Oracle-backed verification: not run, not applicable — no DB-touching code
    changed.

## MVP-2 Phase 2: signal classification (2026-08-26)

Added to `com.bapegg.stockpilot.demand`, covering the front half of Phase 2 order item 3
(`business-rules.md` sections 3-4). Still pure Java, no Spring/JPA/Oracle dependency.

- `PlanHorizon`: the section-3 plan horizon — `analysisDate` through the largest
  `leadTimeDays + receiverTargetCoverageDays` across a store-SKU's active inbound routes, or a
  flat `PLAN_HORIZON_NO_ROUTE_FALLBACK_DAYS` (7) `ASSUMPTION` when no route is active. Takes
  `receiverTargetCoverageDays` and the active routes' lead times directly as plain inputs
  (route/policy entities are a later phase's concern); exposes `overlaps(start, end)`.
- `DemandEvent`: one `SP_DEMAND_EVENT` row (event code, store/SKU, date range, low/base/high
  uplift), validating the same shape as V6's `ck_sp_event_dates`/`ck_sp_event_uplift` Check
  Constraints. `hasCompleteUplift()` backs the `INCOMPLETE_EVENT_DATA` quality flag;
  `matchesStoreAndSku(...)` and `overlaps(...)` back section 3's "관련 이벤트" test.
- `DemandSignalType`/`DemandConfidence`: enums mirroring the `sp_inventory_metric`
  `primary_demand_signal_type`/`demand_confidence` Check Constraint value sets.
- `DemandSignalClassification.classify(storeId, skuId, DemandObservationStatistics, PlanHorizon,
  List<DemandEvent>)`: implements section 3's fixed decision order 1-6
  (`DATA_INSUFFICIENT` → `KNOWN_EVENT` → `UNEXPLAINED_SPIKE` → `INTERMITTENT` →
  `STABLE_REPEAT` → `VARIABLE`, first match wins) and section 4's confidence table, including
  the rule that any quality flag downgrades confidence to `LOW` (which subsumes the specific
  "`KNOWN_EVENT` + `INCOMPLETE_EVENT_DATA` → `LOW`" note, since incomplete-uplift is itself
  one of the four listed quality flags). `STALE_INVENTORY` and `MISSING_INBOUND` are not
  evaluated yet (no inbound-schedule or current-snapshot-freshness input exists in this
  slice); a future increment adding them must fold their effect into the same
  any-flag-downgrades-to-LOW rule rather than special-casing it. low/base/high demand rates
  (section 5) remain a separate, not-yet-implemented class.
- Validation: `PlanHorizonTest` (5), `DemandEventTest` (7),
  `DemandSignalClassificationTest` (8) — all pass. The classification test reproduces GS-01
  (`STABLE_REPEAT`/`HIGH`, no event) and GS-02 (`KNOWN_EVENT`/`MEDIUM`, complete uplift) from
  the identical underlying demand pattern in `data/seed/mvp2` — proving the fixed evaluation
  order picks `KNOWN_EVENT` over what would otherwise also qualify as `STABLE_REPEAT` — plus
  hand-derived `DATA_INSUFFICIENT` (recent launch date wins regardless of other inputs),
  `UNEXPLAINED_SPIKE`, `INTERMITTENT` (low sales-day ratio), `VARIABLE` (frequent but
  high-variance weeks), an event scoped to a different SKU being ignored, and an
  incomplete-uplift event downgrading `KNOWN_EVENT` to `LOW` confidence.
  `.\gradlew.bat build --rerun-tasks` (no `DB_URL`): compile/jar/check all pass; 66
  non-skipped tests pass (up from 46), 0 failures/errors; both Oracle IT classes skip
  cleanly. Oracle-backed verification: not run, not applicable — no migration, entity, or
  persistence code changed.
- Codex review fix (2026-08-26): two correctness findings on the signal-classification
  increment above.
  - `DemandSignalClassification.classify` computed the relevant-event lookup and
    `incompleteEventData` only after the `DATA_INSUFFICIENT` check, so its early return
    discarded both (`relevantEvent=null`, `incompleteEventData=false`) even when a genuinely
    relevant event existed. Section 3 stores quality-flag evidence independently of the single
    primary signal, so a `DATA_INSUFFICIENT` row must still carry it. Fixed by moving the
    event lookup/completeness computation above the insufficiency check; the early return now
    passes the real computed `relevantEvent`/`incompleteEventData` through unchanged.
  - `PlanHorizon.of(...)` accepted a negative `receiverTargetCoverageDays` or a negative/`null`
    route lead time silently, producing a wrong (shifted) horizon date instead of rejecting the
    input the way V6's `ck_sp_policy_values` (`target_coverage_days >= 0`) and
    `ck_sp_route_values` (`lead_time_days >= 0`) Check Constraints would. Fixed by validating
    both inputs at the start of `of(...)` and throwing `IllegalArgumentException`.
  - Validation: `DemandSignalClassificationTest` (9, +1: `observableDayCount < 14` still
    preserves `incompleteEventData`; the existing recent-launch-date test now also asserts
    `relevantEvent`/`incompleteEventData` are preserved) and `PlanHorizonTest` (9, +4: negative
    coverage with and without an active route, negative lead time, `null` lead time element).
    `.\gradlew.bat build --rerun-tasks` (no `DB_URL`): compile/jar/check all pass; 71
    non-skipped tests pass (up from 66), 0 failures/errors; both Oracle IT classes skip
    cleanly. Oracle-backed verification: not run, not applicable — no DB-touching code
    changed.
- Phase 2 low/base/high demand rates (2026-08-26), completing order item 3
  (`business-rules.md` section 5): `DemandRateCalculation.calculate(window, stats,
  relevantEvents)` computes each fixed week's eligible sales/day count -- excluding
  non-observable days, the spike-evidence date, and any day overlapping a relevant event --
  and, when at least `MINIMUM_VALID_WEEKLY_RATES` (3) weeks have at least one eligible day,
  the section-5 linear-interpolation percentiles (`p=0.25/0.50/0.75`) over the sorted weekly
  rates, each fixed at scale 12 HALF_UP. Fewer than 3 valid weekly rates sets
  `reviewRequired=true` with all three rates `null`, matching the `REVIEW_REQUIRED` routing.
  - `DemandEvent.upliftFor(scenarioWindowStart, scenarioWindowEnd)` decides only whether an
    event's uplift applies (complete uplift and overlap with the given window), returning an
    `UpliftFactors` record; `DemandRateCalculation.applyUplift(rate, factor)` does the actual
    multiply-then-round-to-scale-12-HALF_UP. Deliberately not wired to a specific scenario
    window yet: that window (a scenario's arrival-through-target-coverage period) is decided
    by candidate/route selection (order item 5), not yet implemented, so calling code cannot
    correctly determine event applicability yet. Passing the signal-classification
    `PlanHorizon` here instead would be a guess, not the documented rule, so this increment
    leaves the two pieces (applicability decision, multiplication) ready but unconnected.
  - Validation: `DemandRateCalculationTest` (7) — passed, including exact reproductions of
    GS-01 (flat 2/day -> low=base=high=2.000000000000), GS-02 (the event's only overlapping
    day, the observation window's last day, is excluded from its week's eligible count even
    though the resulting rate coincidentally stays 2.0), GS-03 (the spike evidence day is
    excluded from its week), GS-04 (only 2 of 4 weeks have any eligible day ->
    `reviewRequired=true`), and a non-uniform weekly-rate case (1/2/3/4) asserting the
    documented interpolation formula exactly (`low=1.75`, `base=2.5`, `high=3.25`).
    `.\gradlew.bat build --rerun-tasks` (no `DB_URL`): compile/jar/check all pass; 78
    non-skipped tests pass (up from 71), 0 failures/errors; both Oracle IT classes skip
    cleanly. Oracle-backed verification: not run, not applicable — no migration, entity, or
    persistence code changed.
- Codex review fix (2026-08-26): `DemandRateCalculation.calculate` unconditionally excluded
  `stats.spikeEvidenceDate()` from the baseline regardless of the store-SKU's actually
  classified signal. Since section 3 checks `KNOWN_EVENT` before `UNEXPLAINED_SPIKE`, a day
  that is merely statistically spike-shaped is not necessarily the classified anomaly when a
  relevant event won instead -- excluding it unconditionally could wrongly discard real
  demand evidence from a `KNOWN_EVENT` metric's baseline. Fixed by adding a
  `DemandSignalType signalType` parameter (the caller's already-computed classification
  result) and excluding the spike-evidence day only when `signalType ==
  UNEXPLAINED_SPIKE`; the relevant-event-period exclusion is unaffected (always applied
  regardless of which signal won). No public API break in practice, since this class is not
  yet wired into any pipeline.
  - Validation: `DemandRateCalculationTest` (8, +1:
    `knownEventSignalDoesNotExcludeAStatisticallySpikeShapedDayFromTheBaseline`, which reuses
    GS-03's exact spike pattern plus an unrelated future event and directly contrasts the
    `KNOWN_EVENT` case, where the spike week's rate is 20/7 = 2.857142857143 scale 12
    HALF_UP, against the `UNEXPLAINED_SPIKE` case, where the same week's rate is 0). The five
    pre-existing tests now pass each GS's actual classified signal type explicitly instead of
    an implicit/unconditional exclusion. `.\gradlew.bat build --rerun-tasks` (no `DB_URL`):
    compile/jar/check all pass; 79 non-skipped tests pass (up from 78), 0 failures/errors;
    both Oracle IT classes skip cleanly. Oracle-backed verification: not run, not applicable
    — no migration, entity, or persistence code changed.
- Phase 2 projected inventory and exception/severity (2026-08-26), completing order item 4
  (`business-rules.md` sections 4, 6, 9):
  - `InventoryProjection`: `currentAvailable`, `projectedReceiverBeforeDemand`,
    `projectedDonorAtDispatch` (section 6's two formulas, all quantities validated
    non-negative with `reserved <= onHand`, but the *projected* values are never clamped);
    `hasNegativeProjection()` flags a negative projection as the domain input-error state
    section 6 describes ("입력 오류이며 자동 보정하지 않는다"), routed to `NON_ACTIONABLE`
    downstream rather than a Java exception. `receiverAtArrivalWithoutNewTransfer(rate,
    leadTimeDays)`, `receiverTargetQuantity(...)` and `donorProtectedQuantity(...)` implement
    the `ceil(rate * days)` arithmetic shared with section 8 (reused now for section 6's
    thresholds; order item 6 will call the same methods for scenario sizing).
  - `InventoryExceptionType`/`InventorySeverity`: enums mirroring the
    `sp_inventory_metric.inventory_exception_type`/`severity` Check Constraint value sets.
  - `InventoryExceptionClassification.classify(...)`: `NON_ACTIONABLE` (negative projection)
    → `REVIEW_REQUIRED`/`REVIEW` (signal type can't auto-quantify: `DATA_INSUFFICIENT`,
    `UNEXPLAINED_SPIKE`, `INTERMITTENT`, incomplete-uplift `KNOWN_EVENT`, or the rate
    calculation itself returned `reviewRequired`) → `STOCKOUT_RISK` (BASE projected stock at
    the earliest arrival is non-positive, `CRITICAL`; or short of target coverage with an
    undetermined severity) → `OVERSTOCK` (protected donor quantity still leaves a
    transferable surplus) → `NORMAL`.
  - Deliberately not computed: section 9's `HIGH` severity, since it requires knowing
    whether an actionable supply candidate exists (order item 5, not yet implemented). A
    `STOCKOUT_RISK` that is not immediately `CRITICAL` gets `severity=null` here rather than
    an unconditional `HIGH` guess; a later increment must fill this in once candidate
    evaluation exists.
  - Validation: `InventoryProjectionTest` (9) and `InventoryExceptionClassificationTest` (11)
    — passed, including an exact GS-05 before/after-inbound contrast: without the confirmed
    inbound, the receiver is `CRITICAL`/`STOCKOUT_RISK` (BASE projected stock at 1-day
    arrival is `2 - ceil(3.0*1) = -1`); with the real 50-unit confirmed inbound reflected,
    the exception clears `STOCKOUT_RISK` entirely. The exact demo policy numbers for this
    SKU (14-day retention at rate 3.0, display 1, safety 2 → protect 45; 52 projected after
    the inbound) leave a small 7-unit transferable surplus, so this specific input lands on
    `OVERSTOCK` rather than `NORMAL` — recorded as computed, not adjusted to look cleaner.
    `.\gradlew.bat build --rerun-tasks` (no `DB_URL`): compile/jar/check all pass; 99
    non-skipped tests pass (up from 79), 0 failures/errors; both Oracle IT classes skip
    cleanly. Oracle-backed verification: not run, not applicable — no migration, entity, or
    persistence code changed.
- Codex review fix (2026-08-26): three correctness findings on the projected-inventory
  increment above.
  - `InventoryProjection.calculate` threw `IllegalArgumentException` for
    `reservedQuantity > onHandQuantity`, contradicting section 2's own listing of that exact
    condition as one of two `NON_ACTIONABLE` input states (must not throw, must not
    auto-correct). Fixed by removing the throw; `currentAvailable` is now allowed to go
    negative like the two projected fields already did. `hasNegativeProjection()` was renamed
    `isInputInvalid()` and now also checks `currentAvailable < 0`.
  - `InventoryExceptionClassification.classify` re-derived a partial "can't auto-quantify"
    signal-type list (`DATA_INSUFFICIENT`/`UNEXPLAINED_SPIKE`/`INTERMITTENT`/incomplete
    `KNOWN_EVENT`) instead of using the already-computed `DemandConfidence`, so a
    quality-flagged metric that was still nominally `STABLE_REPEAT` (e.g. carrying
    `OOS_CENSORED`, which section 4 downgrades to `LOW` confidence) fell through to the
    inventory-threshold checks instead of `REVIEW_REQUIRED`. Fixed by taking
    `DemandConfidence confidence` directly and checking `confidence == NONE || confidence ==
    LOW` — section 4 already folds every quality flag and "no auto quantity" signal type into
    those two confidence values, so this is both simpler and strictly more correct than the
    signal-type list it replaced.
  - `InventoryProjection.calculate`'s three sums used plain `int` arithmetic, so several
    individually in-range `int` inputs could sum past `Integer.MAX_VALUE`/`MIN_VALUE` and
    silently wrap — the same class of defect `RebalanceCalculation` was fixed for earlier in
    this project. Fixed by widening the intermediate sums to `long` and converting back with
    a single `Math.toIntExact`, so an out-of-range result now throws `ArithmeticException`
    instead of wrapping.
  - Validation: `InventoryProjectionTest` (11, +2: reserved > on-hand now flags instead of
    throwing, two summation-overflow rejection cases for the receiver and donor sides) and
    `InventoryExceptionClassificationTest` (10, rewritten around `DemandConfidence` with a new
    test proving a quality-flagged `STABLE_REPEAT` now correctly routes to `REVIEW_REQUIRED`,
    plus a `reserved > on-hand` → `NON_ACTIONABLE` case). `.\gradlew.bat build --rerun-tasks`
    (no `DB_URL`): compile/jar/check all pass; 100 non-skipped tests pass (up from 99), 0
    failures/errors; both Oracle IT classes skip cleanly. Oracle-backed verification: not
    run, not applicable — no migration, entity, or persistence code changed.
- Codex review fix (2026-08-26), a second follow-up on the same increment: two more
  correctness findings.
  - `InventoryProjection`'s `receiverAtArrivalWithoutNewTransfer`, `receiverTargetQuantity`
    and `donorProtectedQuantity` took `leadTimeDays`/`receiverTargetCoverageDays`/
    `receiverDisplayMinimum`/`donorRetainedDays`/`donorDisplayMinimum`/`donorSafetyStock`
    with no non-negativity validation, unlike V6's own Check Constraints on the same policy
    columns (and unlike `PlanHorizon.of`'s already-fixed validation of the same kind of
    input). Fixed by validating each parameter at the top of its method and throwing
    `IllegalArgumentException`.
  - `receiverTargetQuantity`'s `leadTimeDays + receiverTargetCoverageDays` was plain `int`
    addition, so two individually valid values could overflow before ever reaching the
    ceiling calculation. Fixed by widening to `long` first (and widening `ceilDemand`'s
    `days` parameter to `long` to match).
  - Validation: `InventoryProjectionTest` (15, +4: three negative-input rejection tests, one
    per affected method, plus a widened-sum test using `Integer.MAX_VALUE + 10` that asserts
    the correct `2147483657` result rather than a wrapped one). `.\gradlew.bat build
    --rerun-tasks` (no `DB_URL`): compile/jar/check all pass; 104 non-skipped tests pass (up
    from 100), 0 failures/errors; both Oracle IT classes skip cleanly. Oracle-backed
    verification: not run, not applicable — no migration, entity, or persistence code
    changed.
- Codex review fix (2026-08-26), a third follow-up on the same increment: the non-negativity
  validation for `earliestArrivalLeadTimeDays`/`receiverTargetCoverageDays`/`retainedDays`/
  `displayMinimum`/`safetyStock` lived only inside `InventoryProjection`'s own methods, which
  `InventoryExceptionClassification.classify` only calls in its later branches. A call that
  short-circuited to `NON_ACTIONABLE` (`projection.isInputInvalid()`) or `REVIEW_REQUIRED`
  (`confidence` `NONE`/`LOW`, or `rates.reviewRequired()`) therefore never validated those
  five parameters at all, silently accepting negative policy inputs on those two paths while
  rejecting the same inputs on the "healthy" path. Fixed by moving the validation to the very
  top of `classify(...)`, unconditionally, before any early return.
  - Validation: `InventoryExceptionClassificationTest` (12, +2: a negative `safetyStock` now
    throws even when `isInputInvalid()` would otherwise return `NON_ACTIONABLE`; a negative
    `retainedDays` now throws even when `confidence == NONE` would otherwise return
    `REVIEW_REQUIRED`). `.\gradlew.bat build --rerun-tasks` (no `DB_URL`): compile/jar/check
    all pass; 106 non-skipped tests pass (up from 104), 0 failures/errors; both Oracle IT
    classes skip cleanly. Oracle-backed verification: not run, not applicable — no
    migration, entity, or persistence code changed.
- Phase 2 candidate/route rules (2026-08-26), completing order item 5 (`business-rules.md`
  section 7): `TransferCandidateRejectionReason` (enum, declared in the document's fixed
  priority order so `representativeReason()` can pick the first-declared one present without
  discarding the rest); `TransferRoute` (one `SP_STORE_TRANSFER_ROUTE` row, validated the same
  as V6's `ck_sp_route_values`); `TransferCandidateEvaluation.evaluate(...)` — every one of the
  8 conditions is evaluated independently into an `EnumSet`, per section 7's "탈락한 모든
  조건을 저장하거나 응답한다":
  - `OWNER_MISMATCH`: owners differ and the route (if any) doesn't set `ownerOverride`.
  - `ROUTE_NOT_ALLOWED`: no route row at all, or the row exists but `active=false`.
  - `LEAD_TIME_TOO_LONG`/`CAPACITY_EXCEEDED`/`DISPLAY_MINIMUM_VIOLATION`: only evaluated when
    a route row exists (active or not), reusing `InventoryProjection`'s
    `receiverAtArrivalWithoutNewTransfer` and the section-8 protection formulas already built
    for order item 4.
  - `NO_TRANSFERABLE_STOCK`: donor's `projectedDonorAtDispatch() - donorProtectedQuantity(...)
    <= 0`.
  - `INBOUND_ALREADY_COVERS`/`PENDING_TRANSFER_CONFLICT`: passed through as pre-computed
    booleans (the former from order item 4's exception classification; the latter a simple
    "is there already an open transfer on this exact lane" flag), since neither needs new
    calculation logic here.
  - **Interpretation flagged for review**: the business-rules.md excerpt available to this
    implementation names `DISPLAY_MINIMUM_VIOLATION` for section 7 condition 7 ("최소
    이동수량, 포장 배수, 경로 최대수량과 도착 매장 최대 수용량을 만족한다") without further
    disambiguating that code's exact scope. This implementation reads it as: floor the largest
    shipment that donor supply, route maximum, and receiver capacity headroom would jointly
    allow to the route's package multiple, and fail if that floored amount is still below the
    route's minimum quantity. This mapping should be confirmed on review.
  - Validation: `TransferCandidateEvaluationTest` (13) — passed, including an exact GS-06
    reproduction (receiver on_hand 2 at base rate 3.0, donor `STORE-MVP2-DONOR-B` on_hand 90,
    the real active route with no owner override and a 10-day lead time) that shows
    `OWNER_MISMATCH` and `LEAD_TIME_TOO_LONG` both apply simultaneously — a real multi-reason
    case proving no reason is silently dropped for another — plus one test per remaining
    reason code, an owner-override waiver, a fully eligible candidate, and `TransferRoute`'s
    V6-matching validation. `.\gradlew.bat build --rerun-tasks` (no `DB_URL`): compile/jar/
    check all pass; 119 non-skipped tests pass (up from 106), 0 failures/errors; both Oracle
    IT classes skip cleanly. Oracle-backed verification: not run, not applicable — no
    migration, entity, or persistence code changed.
- Not yet implemented from Phase 2's order: scenario quantities (order item 6),
  approval-request validation (order item 7).
  `unexplainedSpikeWithNoEventIsLowConfidence` exercises GS-03's exact daily pattern through
  the classifier (`UNEXPLAINED_SPIKE`/`LOW`), matching the GS-03 row's signal/confidence
  expectation, though it is not wired to a named `GS-03` fixture the way GS-01/GS-02 are.
  GS-05 and GS-06 are not yet reproduced in Java at all.

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

- Codex MVP-2 observation-statistics review (2026-08-26): DB-free full test rerun
  passed 45 tests with 6 Oracle-conditioned skips. One correctness finding remains:
  `DailyDemandObservation.observable()` uses only `onHand - reserved >= 1`, although
  the approved observable-day definition also requires a valid inventory reference
  time and V6 supplies both `snapshot_at` and explicit `out_of_stock_flag`. Those
  inputs cannot be represented, so a stale/invalid or explicitly OOS snapshot with
  positive quantity can be counted in observable-day, ratio, weekly CV and spike
  evidence. Phase 2 items 1-2 are not accepted until the value object/invariants and
  regression tests preserve that input semantics.
- Codex re-review of the observable fix (2026-08-26): DB-free full rerun passed 50
  tests with 6 Oracle-conditioned skips, but the fix is not accepted. It collapses
  every invalid reference timestamp into `oosCensored()`, creating a false
  `OOS_CENSORED` quality flag even though section 4 reserves that flag for stockout.
  It also allows an explicitly OOS day to carry positive sales, excludes that day
  from median/CV/max evidence, but still adds its sales to `totalWindowSales`, which
  remains the spike-share denominator. Invalid snapshot input and genuine zero-sale
  stockout censorship need distinct invariants before signal classification.
- Codex final re-review of the observable invariants (2026-08-26): accepted with no
  additional finding. Invalid snapshot references are neither observable nor OOS and
  are counted separately; genuine OOS is valid-reference-only and construction
  rejects positive sales, while invalid-day sales are excluded consistently from the
  raw total and all derived statistics. DB-free `gradlew build --rerun-tasks` passed:
  52 total tests, 46 passed, 6 Oracle-conditioned skips, zero failures/errors.
- Codex signal-classification review (2026-08-26): DB-free full build independently
  passed 72 total tests (66 passed, 6 Oracle-conditioned skips), but this increment is
  not yet accepted. `DemandSignalClassification.classify(...)` returns immediately for
  `DATA_INSUFFICIENT`, so a relevant incomplete event is not retained and its separate
  `INCOMPLETE_EVENT_DATA` quality flag is falsely reported as absent. `PlanHorizon.of(...)`
  also accepts negative target coverage or active-route lead time when their sum still
  yields a nonnegative interval, contrary to the V6 nonnegative constraints, allowing a
  shortened event-relevance window. Both paths need regression tests and fixes before
  section 5 demand-rate implementation proceeds.
- Codex signal-classification fix re-review (2026-08-26): accepted with no additional
  finding. Relevant-event lookup now precedes the insufficient-data decision, preserving
  `relevantEvent` and `INCOMPLETE_EVENT_DATA` while keeping signal/confidence at
  `DATA_INSUFFICIENT`/`NONE`. `PlanHorizon.of(...)` rejects negative target coverage and
  null/negative route lead-time elements consistently with V6. DB-free full
  `build --rerun-tasks` passed 77 total tests: 71 passed, 6 Oracle-conditioned skips,
  zero failures/errors. Section 5 low/base/high demand rates remain unimplemented.
- Codex demand-rate review (2026-08-26): DB-free full `build --rerun-tasks` passed 84
  total tests (78 passed, 6 Oracle-conditioned skips), but section 5 is not accepted
  yet. `DemandRateCalculation.calculate(...)` excludes `stats.spikeEvidenceDate()`
  unconditionally. When a relevant event overlaps the observation or plan horizon,
  section 3 selects `KNOWN_EVENT` ahead of a spike candidate; that date is then not an
  `UNEXPLAINED_SPIKE` evidence date and must remain in the baseline unless covered by an
  event period itself. The calculator needs the selected signal (or equivalent explicit
  condition) and a combined spike-candidate/future-known-event regression test. Uplift
  helpers exist, but scenario-window wiring remains deferred to the later route/scenario
  stage and must not be presented as end-to-end uplift application yet.
- Codex demand-rate fix re-review (2026-08-26): accepted with no additional finding.
  `DemandRateCalculation.calculate(...)` now receives the already-selected signal and
  excludes the statistical spike evidence date only for `UNEXPLAINED_SPIKE`; related
  event periods remain excluded independently. The regression test contrasts
  `KNOWN_EVENT` and `UNEXPLAINED_SPIKE` using the same spike-shaped statistics. DB-free
  full `build --rerun-tasks` passed 85 total tests: 79 passed, 6 Oracle-conditioned
  skips, zero failures/errors. Scenario-window uplift wiring remains a later-stage
  integration boundary.
- Codex projected-inventory/exception review (2026-08-26): DB-free full
  `build --rerun-tasks` passed 105 total tests (99 passed, 6 Oracle-conditioned skips),
  but the increment is not accepted. Three correctness findings remain: (1)
  `reservedQuantity > onHandQuantity` makes `currentAvailable` negative, which section 6
  defines as a reportable input error, but construction throws before it can become
  `NON_ACTIONABLE`; (2) exception classification does not consume final confidence or a
  complete quality-flag signal, so a `STABLE_REPEAT`/`KNOWN_EVENT` metric downgraded to
  `LOW` by `OOS_CENSORED`, `STALE_INVENTORY`, or `MISSING_INBOUND` can incorrectly receive
  an automatic stockout/overstock/normal classification instead of `REVIEW_REQUIRED`;
  and (3) receiver/donor `int` additions can silently overflow (for example MAX_VALUE +
  one inbound wraps negative), violating deterministic projection and the explicit
  overflow-test requirement. Candidate rules must wait for fixes and regression tests.
- Codex projected-inventory fix re-review (2026-08-26): the three prior findings are
  resolved: negative current available is preserved and routes to `NON_ACTIONABLE`, final
  `NONE`/`LOW` confidence routes to `REVIEW_REQUIRED`, and projection sums no longer wrap
  silently. DB-free full `build --rerun-tasks` passed 106 total tests (100 passed, 6
  Oracle-conditioned skips). The increment is still not accepted because two newly
  identified boundary defects remain: classification/helper methods accept negative
  lead-time, coverage, retained, display, and safety inputs despite V6 nonnegative
  constraints, and `leadTimeDays + receiverTargetCoverageDays` is still performed as an
  `int` before demand multiplication, so two nonnegative values can wrap to a negative
  duration. Both paths can suppress or invert stockout/overstock decisions and need
  validation/exact-add regression tests before candidate rules.
- Codex projected-inventory boundary re-review (2026-08-26): widened lead-time plus
  coverage arithmetic is accepted and helper-level negative-input tests pass. DB-free
  full `build --rerun-tasks` passed 110 total tests (104 passed, 6 Oracle-conditioned
  skips). One P1 remains: `InventoryExceptionClassification.classify(...)` returns
  `REVIEW_REQUIRED` for `NONE`/`LOW` confidence or review-required rates before invoking
  any projection helper, so negative lead/coverage/retained/display/safety policy inputs
  bypass the newly added validation. Since invalid input has precedence over review,
  classification must validate its complete policy input at entry and cover the early-
  return path with regression tests before candidate rules proceed.
- Codex exception-entry validation re-review (2026-08-26): accepted with no additional
  finding. All five timing/policy inputs are validated before `NON_ACTIONABLE` and
  `REVIEW_REQUIRED` early returns, and regression tests cover both bypass paths. DB-free
  full `build --rerun-tasks` passed 112 total tests: 106 passed, 6 Oracle-conditioned
  skips, zero failures/errors. The projected-inventory/exception increment is accepted;
  candidate/route rules and `HIGH` severity completion remain next.
- Codex candidate/route review (2026-08-26): DB-free full `build --rerun-tasks`
  passed 125 total tests (119 passed, 6 Oracle-conditioned skips), but the increment is
  not accepted. Three correctness findings remain: (1) section 7 condition 1 (same SKU,
  different stores) is only a caller comment and cannot be represented or enforced by
  `evaluate(...)`, so an invalid pair can be returned eligible; (2) the record stores and
  exposes a mutable `EnumSet`, allowing callers to clear or add reasons after evaluation
  and change `eligible()`/representative reason; and (3) `atArrival == 0` is classified
  `LEAD_TIME_TOO_LONG`, although the rule allows arrival on the expected stockout date and
  rejects only a later arrival. The `DISPLAY_MINIMUM_VIOLATION` mapping to the maximum
  feasible package-multiple shipment falling below route minimum is accepted as the most
  consistent reading of sections 7-8. Fixes and boundary tests are required before
  scenario quantities.
