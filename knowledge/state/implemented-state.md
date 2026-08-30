# Implemented State

Last updated: 2026-08-30 (MVP-2 accepted)

The accepted milestone is [MVP-2.md](../milestones/MVP-2.md). This file is the compact current
repository snapshot; detailed review history is cold evidence under `knowledge/archive`.

## Accepted system

- Oracle/Flyway V1..V15 provide the synthetic Korean demo dataset, audit/error catalogs and MVP-2
  persistence model.
- Deterministic Java code owns analysis, demand metrics, donor eligibility, scenario quantities,
  MANUAL feasibility, approval validation and decision status.
- Spring Batch launches/reuses an analysis run and persists run-bound metrics, candidates, quality
  flags and scenarios.
- Read APIs expose the exception queue/detail, candidates, automatic scenarios, decision history
  and approval evidence for one explicit completed run.
- Approval normalizes the request, locks recommendation then donor state, recalculates the latest
  basis and atomically appends decision/basis/CREATED-draft records. It does not mutate inventory.
- React launches and polls analysis, presents the Korean decision workbench, runs side-effect-free
  MANUAL tests, submits decisions and renders canonical history with safe ProblemDetail handling.
- The explanation endpoint is safely unavailable with `AI_DISABLED`, `AI_UNCONFIGURED` or
  `AI_PROVIDER_NOT_IMPLEMENTED`; no real provider adapter is present.

## Durable boundaries

- AI does not choose quantities or statuses and cannot override Java validation.
- UI state is bound to an explicit run and terminal decisions fail closed.
- Approval is idempotent and append-only; actual ERP/WMS inventory movement is outside this MVP.
- Synthetic data and demo policy thresholds are labeled `ASSUMPTION`, not enterprise facts.
- `scripts/local.ps1 test` never creates, stops or deletes Oracle. It requires a healthy existing
  service, non-empty DB credentials and JUnit XML with tests > 0 and zero skips/failures/errors.

## Latest independent verification

- Credential guard rejected a blank `DB_URL`; the Oracle result rule rejected a copied JUnit
  report after `skipped=3` was injected.
- `scripts/local.ps1 test-db-free`: 520 total / 378 passed / 142 expected conditional skips /
  0 failures / 0 errors.
- `scripts/local.ps1 db-status`: the existing Oracle container was healthy and left untouched.
- `scripts/local.ps1 test`: seed validation passed; Oracle Backend 520/520 with zero
  skips/failures/errors; Frontend 10 files / 97 tests passed; production build passed.
- Root `git diff --check`: exit 0; line-ending conversion warnings only.
- Open production findings: none.

## Not implemented

- Real LLM provider adapter.
- Operational scheduler, stale `RUNNING` recovery and first-JobInstance race normalization.
- Authentication/authorization and external ERP/WMS/TMS integration.
