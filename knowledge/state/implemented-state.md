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
  sections 2-4 with BigDecimal HALF_UP at 10-decimal internal precision; storage
  rounding to the column scale happens only at the JPA entity boundary.
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
- Oracle integration: `InventoryAnalysisGoldenScenarioIT` (`@EnabledIfEnvironmentVariable(DB_URL)`,
  run with `.env` credentials exported) — passed. Verified against the real Oracle instance,
  including a direct `sqlplus` readback:
  - Gangnam: STOCKOUT_RISK / HIGH; Hongdae: OVERSTOCK; Seongsu: NORMAL
  - One recommendation: Hongdae → Gangnam, recommended 25, shortage 25, transferable 30
  - Job launched 3 times across separate JVM runs against the same Oracle data; only
    run 1 persisted rows (1 `sp_analysis_run`, 3 `sp_inventory_metric`, 1
    `sp_rebalance_recommendation`); runs 2-3 threw `JobInstanceAlreadyCompleteException`
    with no additional rows — idempotency confirmed, not just asserted
- Full Gradle `build` (compile, jar, all 13 tests including the Oracle IT, check) — passed

The schema and Seed, and the Batch analysis slice (entities, pure calculation, idempotent Job)
described above are working and Oracle-verified. REST APIs, UI screens, decision persistence
and LLM integration must not be presented as implemented.
