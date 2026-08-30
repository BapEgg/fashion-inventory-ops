# Archived Worklog — through Phase 3 orchestration first review

이번 승인 이전 원문은
[`../archive/worklogs/2026-08-27-through-phase3-input-adapter-acceptance.md`](../archive/worklogs/2026-08-27-through-phase3-input-adapter-acceptance.md)에 보존했다.

## 2026-08-27 — Accept Phase 3 Batch input adapter

- Role: Codex verification/review.
- Completed: Verified all four adapter fixes, their boundary ITs, immutable key indexes and the
  unchanged fixed-query contract; no open adapter finding remains.
- Validation: Oracle target 10/10; Oracle full 315/315(skip 0); DB-free full 315 total/246
  passed/69 conditional skips; failures/errors 0; `git diff --check` passed.
- Decision: accepted the input adapter only; calculation orchestration and persistence remain
  unimplemented and unaccepted.
- Checkpoint: archived the 128-line current task, implemented snapshot and active worklog; no
  milestone was created because Phase 3 as a whole is incomplete.
- Open: in-memory deterministic calculation orchestration is the next bounded unit.
- Next: Claude implements [`../state/current-task.md`](../state/current-task.md), proves the
  `MVP-2-GS-V1` values with adapter-only SQL and no output writes, then hands back to Codex.

## 2026-08-27 — Implement Phase 3 in-memory calculation orchestration

- Role: Claude implementation.
- New pure helper `demand.EffectiveReceiverBaseRate`: the route-specific effective receiver BASE
  rate (baseline x uplift BASE when the representative `KNOWN_EVENT` overlaps that route's own
  arrival-through-target-coverage window), per business-rules.md section 10's shared
  current-basis contract. `TransferScenarioSet` does not use it -- it already applies uplift
  per-scenario internally; this exists for narrower callers (here, `ApprovalBasisRecalculation`)
  that need only the single uplifted BASE rate as a plain input.
- New `Mvp2CalculationOrchestrator` (`batch` package, pure, zero SQL): connects an
  `Mvp2InputGraph` to the existing pure `demand` rules following current-task.md's six-step
  calculation order --
  1. per-anchor 28-day statistics + plan horizon (active routes reaching that receiver +
     its own policy coverage, 7-day fallback);
  2. representative event selection (`RepresentativeEventSelection`, reused unchanged) + signal/
     confidence/baseline LOW-BASE-HIGH;
  3. earliest-arrival lead time (min of active route lead times and complete `CONFIRMED` inbound
     lead days, 7-day fallback) + a canonical projection combining both receiver-shaped and
     donor-shaped evidence for the same store + a provisional exception
     (`hasActionableCandidate=false`);
  4. candidate evaluation for auto-quantifiable, actually-short receivers against every other
     same-SKU anchor as donor, via the *existing* `ApprovalBasisRecalculation.calculate` (not
     re-implemented) fed the route-specific effective BASE rate and a lane-specific
     confirmed-inbound cutoff (open-transfer in/out and drafts are route-independent, computed
     once per anchor);
  5. `ELIGIBLE`/`RECOMMENDED` (stable/event), `ELIGIBLE`/`COMPARISON_ONLY` (`VARIABLE`, scenarios
     but no single recommended quantity), or `REJECTED`/`NONE`, every rejection reason preserved
     in `TransferCandidateRejectionReason`'s declared order (via `ApprovalBasisRecalculation`'s
     own `candidateEvaluation()`, also not re-implemented);
  6. metric exception/severity re-classified once against real eligible-candidate presence;
     eligible candidates get all four `TransferScenarioSet` scenarios, with the `BASE` scenario's
     own quantity (not `ApprovalBasisRecalculation`'s, though they agree) as the recommended
     quantity.
  New quality-flag derivation (not previously written anywhere): `STALE_INVENTORY` (current
  snapshot >24h before `analysisReferenceAt`, or any observation day with a mismatched local
  date) and `MISSING_INBOUND` (any inbound row missing quantity/ETA) -- `OOS_CENSORED` and
  `INCOMPLETE_EVENT_DATA` already existed on `DemandObservationStatistics`/
  `DemandSignalClassification`.
- New result types `Mvp2MetricResult`, `Mvp2CandidateResult`, `Mvp2CalculationResult` (`batch`
  package) -- immutable, no JPA/JdbcTemplate/Spring Batch dependency anywhere, with the required
  identifiable-key maps (`metricsByStoreSku`, `candidatesByReceiver`) alongside deterministically
  (storeId/skuId-then-donor) ordered flat lists.
- Two real bugs found and fixed via the Oracle golden test (both caught empirically, not by
  inspection): (1) the initial receiver gate only checked "auto-quantifiable", so a well-stocked
  donor whose own signal also happened to be auto-quantifiable was incorrectly evaluated as a
  receiver against every other same-SKU anchor too, producing 9 candidates instead of 4; fixed by
  adding a `currentAvailable < target` shortage pre-check. (2) that shortage pre-check initially
  used the full inbound-adjusted projection, which meant GS-05's own confirmed inbound closed the
  gap *before* candidate evaluation ever ran, silently dropping the candidate instead of
  rejecting it with `INBOUND_ALREADY_COVERS` as required -- fixed by checking only
  `currentAvailable` at this pre-filter stage (the adjusted figure is still what
  `ApprovalBasisRecalculation` uses for the real, lane-specific decision).
- New tests (all real, executed this round):
  - `EffectiveReceiverBaseRateTest` (6, pure): uplift applies when the event overlaps that
    route's own window; falls back to baseline for non-`KNOWN_EVENT` signal, no event, no
    overlap, incomplete uplift; two routes with different lead times can get different effective
    rates for the same event.
  - `Mvp2CalculationOrchestratorTest` (7, pure, hand-built `Mvp2InputGraph` fixtures):
    `REQUESTED` blocks as `PENDING_TRANSFER_CONFLICT`, `APPROVED` does not; an incomplete inbound
    row sets `MISSING_INBOUND`, a complete one does not; a current snapshot >24h before
    `analysisReferenceAt` sets `STALE_INVENTORY`, a fresh one does not; metrics/candidates come
    out in deterministic `(storeId, skuId)`/donor order regardless of input order.
  - `Mvp2BatchGoldenScenarioIT` (1, Oracle, new): runs the real adapter + orchestrator against
    the real `MVP-2-GS-V1` seed (V7 migration) with **zero output writes** and confirms every
    golden value from current-task.md exactly: 12 metrics, 4 candidates, 8 scenarios total;
    GS-01 eligible with `recommendedQuantity=11`, both stores' after-transfer position sane;
    GS-02 `KNOWN_EVENT` with effective BASE rate exactly `3.000000000000` and BASE quantity 20;
    GS-03/GS-04 produce no candidate at all (gated out); GS-04 carries `OOS_CENSORED`; GS-05
    `REJECTED` with `INBOUND_ALREADY_COVERS`, no scenarios, null recommended quantity; GS-06
    `REJECTED` against donor-B with both `OWNER_MISMATCH` and `LEAD_TIME_TOO_LONG` present. Also
    reconfirms the orchestrator itself runs zero SQL: the same `Connection.prepareStatement`/
    `createStatement`-counting proxy from the adapter ITs stayed at exactly 8 across the whole
    adapter+orchestrator call.
- Full regression evidence (real, executed this round):
  - Oracle-backed full Backend build: **329/329**, skip 0, failures/errors 0.
  - DB-free full Backend build: **329 total / 259 passed / 70 Oracle-conditioned skip**,
    failures/errors 0.
  - `git diff --check`: exit 0 (only pre-existing LF/CRLF warnings on already-tracked files).
- No Migration, REST/React, entity conversion, output persistence/transaction, Job/Step wiring,
  or approval/`MANUAL` parity code touched -- scoped strictly to in-memory calculation, per
  `current-task.md`'s constraint. `ApprovalBasisRecalculation`/`TransferCandidateEvaluation`/
  `TransferScenarioSet`/`RepresentativeEventSelection` were called, never modified or duplicated.
- Next: Codex reviews the orchestrator, the two bugs' fixes, and the golden evidence; once
  accepted, the next bounded unit is entity conversion, atomic output persistence and run
  transaction/retry semantics.
