# Implemented State

Last verified: 2026-08-25

## Present in the repository

- Portfolio-oriented README with product, user, workflow, architecture, ERD and local execution
- Java 21, Spring Boot 4.1.0 and Gradle Wrapper 9.5.1 Backend scaffold
  - Web MVC, JPA, Validation, Batch, Spring Boot Flyway Starter, Flyway Oracle and Oracle JDBC
- React 19, TypeScript 5.9 and Vite 8 Frontend scaffold
- Oracle Database Free 23ai Docker Compose service
  - Pinned image: `gvenzl/oracle-free:23.26.2-slim-faststart`
  - Local-only port binding, health check and persistent Docker Volume
- One ignored root `.env` for Oracle and optional LLM settings, with random-password setup command
- Approved domain ERD and versioned Oracle migrations
  - `V1`: domain schema, constraints and indexes
  - `V2`: synthetic Golden Scenario Seed
  - `V3`: Spring Batch 6.0.4 Oracle metadata schema
  - `V4`: concise Korean comments for all domain tables and columns
- README-rendered SVG architecture and ERD with editable draw.io sources
- CSV Seed validation for headers, keys, references, quantities and Golden Scenario expectations
- Approved MVP specification, business assumptions, data pipeline and short agent handoff skills

- JPA entities and repositories for the Batch-analysis slice of the schema:
  `SpInventorySnapshot`, `SpDailySale` (`backend/.../inventory`); `SpAnalysisRun`,
  `SpInventoryMetric` (`backend/.../analysis`); `SpRebalanceRecommendation`
  (`backend/.../rebalance`). `SpProduct`, `SpStore` and `SpRebalanceDecision` are
  intentionally not yet mapped; nothing in the implemented slice queries them.
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
- Idempotent Spring Batch analysis (`InventoryAnalysisJobConfig`,
  `InventoryAnalysisTasklet`): a single-tasklet `inventoryAnalysisJob` keyed by
  `(analysisDate, ruleVersion)` JobParameters. Idempotency is layered: Spring Batch's
  own JobRepository (BATCH_* tables from V3) refuses to relaunch already-completed
  JobParameters, and the tasklet also checks `SpAnalysisRun` and runs the whole
  compute-and-persist sequence in one transaction so a failure leaves no partial run.

## Not implemented

- `SpProduct`, `SpStore`, `SpRebalanceDecision` JPA mapping (add when an API needs them)
- REST APIs (`AnalysisRunService`/controller layer to launch the Job and expose results)
- Inventory exception and simulation screens
- Decision persistence behavior
- LLM provider integration

## Environment observations

- Java: Temurin 21.0.11
- Gradle: committed Wrapper 9.5.1
- Docker Engine: 29.6.2; Docker Compose: 5.3.1
- Oracle: 23.26.2 in `stockpilot-oracle-1`, healthy on `127.0.0.1:1521/FREEPDB1`
- Node.js/npm: unavailable on the user PATH; the Frontend was verified with the Codex bundled runtime
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

The schema and Seed, and the Batch analysis slice (entities, pure calculation, idempotent
Job) are working and Oracle-verified, including the reviewed transfer-calculation fix.
REST APIs, UI screens, decision persistence and LLM integration must not be presented as
implemented.
