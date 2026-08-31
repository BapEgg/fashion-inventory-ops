# Implemented State

Last updated: 2026-08-31 (Allocator workbench redesign accepted)

The accepted milestone is [MVP-2.md](../milestones/MVP-2.md). This file is the compact current
repository snapshot; detailed review history is cold evidence under `knowledge/archive`. The
redesign's own implementation contract is
[2026-08-30-allocator-workbench-redesign-spec.md](2026-08-30-allocator-workbench-redesign-spec.md).

## Accepted system

- Oracle/Flyway V1..V16 provide the synthetic Korean demo dataset, audit/error catalogs and MVP-2
  persistence model. V16 (additive demo-volume expansion, own `FASHION-2026-FW-SEP-V2`
  `input_snapshot_version`, isolated from V7's golden scenario) originally referenced six product
  SKUs never seeded and three store ids that never existed (`STORE-SEOUL-GANGNAM-FLAGSHIP` instead
  of `STORE-GANGNAM`, etc.) and was missing the NOT NULL `sp_product.launch_date`/`season_code`/
  `sales_status` columns V6 added -- fixed by adding the six products to the catalog inside V16
  itself and correcting the store ids/columns; verified with a from-scratch migration, the full
  Oracle test suite and a live analysis run (54 review rows across 6 SKUs x 9 stores).
- Deterministic Java code owns analysis, demand metrics, donor eligibility, scenario quantities,
  MANUAL feasibility, approval validation and decision status.
- `AllocatorWorkStatus` (`DECISION_REQUIRED/ON_HOLD/REVIEW_INPUT/NO_TRANSFER_OPTION/COMPLETED`) is
  derived once per metric from its executable (`ELIGIBLE && RECOMMENDED`) candidates' latest
  decisions -- `AllocatorWorkStatusResolver` in Java, `InventoryExceptionQuerySql`'s CASE
  expressions in SQL for filter/sort, kept in lockstep by test rather than a shared code path.
- The run-bound list (`GET /api/inventory-exceptions?analysisRunId=...`) additionally accepts
  repeatable `workStatus` and `sortBy`/`sortDirection` (`WORK_PRIORITY` default), returns each
  item's `workStatus`/`blockingReasons`, and a run-wide, filter-independent `summary`
  (`AllocatorWorkSummary`). List query ceiling is 9 statements (raised from 6 by the summary
  aggregate and work-status/blocking-reason bulk-fetches); detail ceiling is unchanged at 14.
- Spring Batch launches/reuses an analysis run and persists run-bound metrics, candidates, quality
  flags and scenarios.
- `CurrentApprovalBasisLoader`'s pending-transfer-conflict set is `{REQUESTED}` only (`APPROVED`/
  `IN_TRANSIT` are already reflected once in the committed-open-transfer projection and must not
  also block as a conflict) -- matches `Mvp2CalculationOrchestrator`'s Batch-side rule, which was
  already correct.
- `ManualQuantityTestExecutor` and `ApprovalTransactionExecutor` both fail closed on a
  `COMPARISON_ONLY` or already-`REJECTED` recommendation: MANUAL returns `feasible=false` with
  `CANDIDATE_INELIGIBLE`; the decision write path rejects with the existing `STALE_RECOMMENDATION`
  409 before any status-specific branch, writing no decision/basis/draft row.
- Read APIs expose the exception queue/detail, candidates, automatic scenarios, decision history
  and approval evidence for one explicit completed run.
- Approval normalizes the request, locks recommendation then donor state, recalculates the latest
  basis and atomically appends decision/basis/CREATED-draft records. It does not mutate inventory.
- React presents the allocator's own vocabulary (spec section 5) throughout: a run-wide summary
  (`WorkQueueSummary`) and work-status tabs (`WorkStatusTabs`) above a master-detail layout
  (`ExceptionList` worklist + `ExceptionDetail`, split at 1024px, full-screen detail below it);
  detail is four tabs (이동안 검토/판매·재고 근거/입고·매장이동/산출 기준 상세); an actionable
  candidate auto-selects and auto-runs its own MANUAL simulation once; approval requires an
  `ApprovalConfirmDialog` confirm step; HELD/REJECTED use a shared reason-code `<select>` (no more
  free-text codes); comparison-only/rejected candidates never render a decision form.
- The explanation endpoint is safely unavailable with `AI_DISABLED`, `AI_UNCONFIGURED` or
  `AI_PROVIDER_NOT_IMPLEMENTED`; no real provider adapter is present.

## Durable boundaries

- AI does not choose quantities or statuses and cannot override Java validation.
- Frontend actionable gating is `candidateStatus === 'ELIGIBLE' && recommendationMode ===
  'RECOMMENDED' && !terminal` everywhere (`CandidateWorkbench.isCandidateActionable`,
  `DecisionPanel`'s own `structurallyActionable`) -- comparison-only is never treated as
  actionable, matching the Backend fail-closed rule above.
- UI state is bound to an explicit run and terminal decisions fail closed.
- Approval is idempotent and append-only; actual ERP/WMS inventory movement is outside this MVP.
- Synthetic data and demo policy thresholds are labeled `ASSUMPTION`, not enterprise facts.
- `scripts/local.ps1 test` never creates, stops or deletes Oracle. It requires a healthy existing
  service, non-empty DB credentials and JUnit XML with tests > 0 and zero skips/failures/errors.

## Latest independent verification (2026-08-31)

- A stale local Oracle volume (Flyway checksum drift on V7, unrelated to this session's changes)
  was rebuilt from scratch (`docker compose down -v` then `db-up`) before verification.
- `scripts/local.ps1 seed-check`: passed (MVP-2: Products=6, Stores=3, Inventory=348, Sales=336,
  Scenarios=6).
- `scripts/local.ps1 test-db-free`: 527 total / 385 passed / 142 expected conditional skips /
  0 failures / 0 errors.
- `scripts/local.ps1 db-status`: the existing Oracle container was healthy and left untouched.
- `scripts/local.ps1 test`: seed validation passed; Oracle Backend 527/527 with zero
  skips/failures/errors; Frontend 10 files / 106 tests passed; production build passed.
- Root `git diff --check`: exit 0; line-ending conversion warnings only.
- Browser acceptance (real Oracle backend, `http://localhost:5173` equivalent): 재고 현황 갱신 →
  summary tiles/work-status tabs render live counts → default 이동 결정 필요 tab → 검토하기 opens
  master-detail → candidate auto-selected, auto-simulation ran → 이동 승인 opened the confirm
  dialog with correct donor/receiver before→after figures → confirmed → list/summary/tabs
  refreshed live (이동 결정 필요 4→2, 처리 완료 0→2) and the detail panel closed with "처리 결과가
  반영되었습니다". 375px viewport: no page-level horizontal scroll. No console errors from
  application code.
- Open production findings: none.

## Not implemented

- Real LLM provider adapter.
- Operational scheduler, stale `RUNNING` recovery and first-JobInstance race normalization.
- Authentication/authorization and external ERP/WMS/TMS integration.
