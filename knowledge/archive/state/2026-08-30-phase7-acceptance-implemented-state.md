# Implemented State

Last updated: 2026-08-30 (MVP-2 Phase 7 findings fixed and re-verified; pending Codex re-review)

This hot snapshot records current accepted behavior. Detailed evidence is preserved in
[`MVP-1.md`](../milestones/MVP-1.md),
[`MVP-2-Phase-3.md`](../milestones/MVP-2-Phase-3.md) and
[`MVP-2-Phase-5.md`](../milestones/MVP-2-Phase-5.md).

## Accepted system

- Oracle/Flyway V1..V15, deterministic Java analysis/rebalancing rules, MVP-2 Batch orchestration,
  approval persistence, inventory-exception reads, MANUAL simulation REST and decision/history REST
  are accepted.
- Approval owns normalization, recommendation-then-donor locking, latest-basis recalculation and
  atomic append-only decision/basis/CREATED-draft storage. It does not mutate inventory quantities.
- The React application launches and polls analysis, binds reads to an explicit run, presents
  evidence/candidates/scenarios, submits MANUAL tests and decisions, and renders canonical history.
- Terminal UI behavior fails closed for `APPROVED`, `REJECTED` and `EXPIRED` while `HELD` remains
  actionable. Korean presentation, safe ProblemDetail errors and responsive table containment are
  covered by regression tests.
- AI remains explanation-only. Demo thresholds and synthetic data are identified as `ASSUMPTION`,
  not actual enterprise policy or data.

## Latest independent verification

- Backend accepted baseline: DB-free 520 total / 378 passed / 142 conditional skip; Oracle forced
  full run 520/520 with zero skips, failures or errors.
- Frontend Phase 5 acceptance: 10 test files / 97 tests passed; production build passed.
- `git diff --check -- frontend`: exit 0 with line-ending conversion warnings only.
- Open Phase 5 production findings: none.

## Phase 7 — final verification (Codex's 3 findings fixed 2026-08-30, pending re-review)

- `scripts/local.ps1` gained a `test-db-free` command (imports `.env`, then strips
  `DB_URL` so Oracle-only integration tests skip deterministically) and a rewritten `test` command
  that is now the full local verifier: requires/imports `.env`, runs seed validation, fails early
  unless Docker is reachable and the compose Oracle service reports `healthy`, runs the Backend
  Oracle suite (`clean test --rerun-tasks`), then requires Node/pnpm and runs
  `pnpm install --frozen-lockfile`, `pnpm test`, `pnpm build` in `frontend/`. It never starts, stops,
  recreates or deletes the Oracle container/volume; `db-up`/`db-down` remain the only explicit
  lifecycle actions.
- Real matrix execution and results, in order:
  - `seed-check`: passed (MVP-1 1/3/3/21, MVP-2 6/3/348/336, 6 scenarios).
  - `test-db-free`: 520 total / 378 passed / 142 conditional skip (missing `DB_URL`) / 0
    failures / 0 errors.
  - `db-up` / `db-status`: Oracle container already running and healthy; confirmed idempotent.
  - `test` (full verifier, real Oracle): seed validation passed; Oracle health check passed;
    Backend Oracle suite **520/520 passed, 0 skip, 0 failures, 0 errors**; Frontend
    `pnpm install --frozen-lockfile` + `pnpm test` (10 files / 97 tests passed) +
    `pnpm build` (`tsc -b && vite build`) all passed.
  - `git diff --check` (repository root, not just `frontend/`): exit 0, line-ending conversion
    warnings only.
  - No credential value was printed by any verifier stage; `.env` remains `.gitignore`d and
    untracked (confirmed via `git check-ignore`/`git ls-files`).
- Batch Golden Scenario regression (`Mvp2BatchGoldenScenarioIT`, included in the 520/520 Oracle
  run) independently confirms the orchestrator issues exactly 8 bulk JDBC statements and no
  additional SQL, and produces 12 metrics (6 SKUs × receiver/donor anchors), 4 candidates
  (2 eligible and 2 rejected), 1 quality flag (`OOS_CENSORED`), 3 distinct rejection reasons
  (`INBOUND_ALREADY_COVERS`, `OWNER_MISMATCH`, `LEAD_TIME_TOO_LONG`) and 8 automatic scenarios
  (GS-01/GS-02 ELIGIBLE, 4 scenarios each).
- Real browser smoke pass: ran Backend (a temporary local instance on an alternate port, since the
  default `8080` was occupied by an unrelated project's server on this machine — not a StockPilot
  defect) and Frontend together against the live Oracle data. Reused the official
  `analysisDate=2026-09-30`/`inputSnapshotVersion=MVP-2-GS-V1` run (`alreadyCompleted: true`),
  filtered/opened the queue, inspected 28-day evidence/candidates/automatic scenarios, ran a
  side-effect-free MANUAL quantity test (correctly returned `feasible=false` with
  `CANDIDATE_INELIGIBLE`/`PENDING_TRANSFER_CONFLICT` violations against real conflicting open-transfer
  data), and confirmed decision history stayed `PENDING` (no decision was submitted, per the smoke
  scope). No page-level horizontal overflow at desktop or 375px, no unexpected 5xx, no live console
  errors (one stale `ERR_EMPTY_RESPONSE` entry traced to the temporary-port setup step, not a
  runtime defect). Saved a real desktop screenshot to `docs/images/stockpilot-workbench.png` for
  the README (captured via an in-page `html2canvas` render since the automation surface used has no
  direct disk-save action; not a generated mockup).
- `README.md` was rewritten to the verified-fact structure specified for Phase 7 (problem/user/value,
  real screenshot, five-step workflow, implemented capabilities, architecture, engineering decisions,
  copy-paste commands, dated verification results, honest limitations). Obsolete phase-status tables,
  future-tense claims and internal review chronology were removed.
- `knowledge/data-model.md` now documents `V14__add_analysis_api_error_catalog.sql` and
  `V15__add_inventory_exception_read_error_catalog.sql` (both `SP_ERROR_CATALOG` INSERT-only, no
  schema change) alongside V1-V13.
- Codex independently reproduced `seed-check`, the exact `test-db-free` and `test` commands,
  Backend 520/378/142 and Oracle 520/520/0, Frontend 97 tests/build, root diff check, README links,
  external reference support and repository secret non-disclosure, then opened 3 findings (1 P1,
  2 P2): the full verifier didn't enforce its required zero-skip Oracle result from real JUnit XML
  (only trusted Gradle's exit code), and README overstated the candidate count and implied an
  activatable AI feature that doesn't exist.
- All 3 findings are fixed: `scripts/local.ps1` gained `Assert-StockPilotOracleCredentialsPresent`
  (throws unless `DB_URL`/`DB_USERNAME`/`DB_PASSWORD` are non-empty, values never printed) and
  `Get-StockPilotJUnitSummary` (aggregates real `TEST-*.xml` results); `test` now requires
  `tests > 0` and zero skipped/failures/errors from the real XML, not just Gradle's exit code;
  `test-db-free` now prints its own real total/pass/skip/failure/error summary. The guard was
  demonstrated against a synthetic, non-destructive copy of the real Oracle result XML with one
  file's `skipped` set to 3 -- correctly rejected by the exact `test`-command rule. README's
  candidate count now reads `4개 후보(2개 적격/2개 탈락)`; its AI bullet now accurately states the
  explanation endpoint always returns `AI_DISABLED`/`AI_UNCONFIGURED`/`AI_PROVIDER_NOT_IMPLEMENTED`
  in the current configuration, verified against `ExplanationService`'s actual code paths.
- Real re-verification after the fixes (2026-08-30, not copied from a prior round): `test-db-free`
  -- `tests=520 passed=378 skipped=142 failures=0 errors=0`; `db-status` -- Oracle already healthy
  (untouched); `test` (full verifier) -- Oracle credential presence check passed, `Oracle Backend
  results: tests=520 passed=520 skipped=0 failures=0 errors=0`, Frontend 10 files/97 tests + build
  all passed, ending in `Full local verification passed.`; root `git diff --check` exit 0. No
  credential value appears in either background-task output log for this round.

## Not implemented

- Optional real LLM provider adapter; deliberately skipped for this MVP completion path.
- Operational scheduler/stale `RUNNING` recovery and first-JobInstance race normalization.
- Authentication/authorization and external ERP/WMS/TMS integration.
