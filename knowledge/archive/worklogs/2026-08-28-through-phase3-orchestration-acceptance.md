# Archived Worklog — through Phase 3 orchestration acceptance

이전 원문은
[`../archive/worklogs/2026-08-28-through-phase3-orchestration-first-review.md`](../archive/worklogs/2026-08-28-through-phase3-orchestration-first-review.md)에 보존했다.

## 2026-08-28 — Phase 3 orchestration first review

- Role: Codex verification/review.
- Completed: Reviewed the new pure orchestrator, result records, effective BASE helper and tests
  against the calculation/data mapping contracts.
- Validation: pure targets 13/13; Oracle Golden 1/1; Oracle full 329/329(skip 0); DB-free 329
  total/259 passed/70 skip; failures/errors 0; `git diff --check` passed.
- Decisions: not accepted; passing Golden coverage does not exercise five open calculation/output
  boundaries owned by [`../state/current-task.md`](../state/current-task.md).
- Open: confidence downgrade, open-transfer-aware receiver gate, arrival projection, stable
  collection iteration and exact aggregate arithmetic.
- Checkpoint: rotated 126-line current task, implemented snapshot and worklog; no milestone was
  created because Phase 3 remains incomplete.
- Next: Claude fixes all five findings with boundary tests, then hands back to Codex.

## 2026-08-28 — Fix Phase 3 orchestration first-review findings

- Role: Claude implementation.
- Finding 1 (confidence not downgraded for orchestrator-only quality flags): `STALE_INVENTORY`
  and `MISSING_INBOUND` are computed in the orchestrator, not inside
  `DemandSignalClassification.classify` (which has no access to current-snapshot timing or
  inbound rows), so they never reached that method's own confidence table. `buildAnchorEvidence`
  now rebuilds `signal` with confidence forced to `LOW` whenever either flag is present and the
  signal isn't already `DATA_INSUFFICIENT`/`NONE` -- this downgraded signal is what everything
  downstream (the auto-quantify gate, `TransferScenarioSet`, and step 3's provisional
  `InventoryExceptionClassification.classify`, which now naturally lands on `REVIEW_REQUIRED`)
  actually reads.
- Finding 2 (receiver shortage gate ignored committed open transfers): `hasMetricLevelBaseShortage`
  now builds a projection that includes `APPROVED`/`IN_TRANSIT` open transfers in both
  directions but still excludes confirmed inbound (so `INBOUND_ALREADY_COVERS` is still
  discovered per-lane, not pre-empted by this coarse gate) -- an already-topped-up receiver is
  now correctly excluded, and one newly drained by an outbound commitment is now correctly
  included. A shortage-check projection that is itself `isInputInvalid()` (e.g. an outbound
  commitment alone exceeding on-hand) now excludes the receiver entirely rather than being
  treated as an extreme shortage signal.
- Finding 3 (`projectedReceiverAtArrival` used the wrong figure): was set to
  `recalculation.receiverProjectedBeforeDemand()` (the pre-arrival snapshot); now set to
  `receiverProjection.receiverAtArrivalWithoutNewTransfer(effectiveReceiverBaseRate,
  route.leadTimeDays())` -- the arrival-time position after subtracting lead-time demand at the
  effective (event-uplifted where applicable) rate.
- Finding 4 (`Map.copyOf`/`Set.copyOf` do not guarantee iteration order): every identified map in
  `Mvp2InputGraph`/`Mvp2CalculationResult` now wraps a `LinkedHashMap` with
  `Collections.unmodifiableMap` instead of `Map.copyOf`. `Mvp2MetricResult.qualityFlags` now
  uses `EnumSet`/`Collections.unmodifiableSet` instead of `Set.copyOf`, so iteration always
  follows `MetricQualityFlag`'s declared order regardless of detection order.
- Finding 5 (`int +=` aggregate overflow): `sumOpenTransferQuantity` and
  `sumConfirmedInboundBeforeCutoff` now accumulate as `long` and range-check the *aggregate*
  (not each addend, which the adapter already validated individually) via a new
  `safeIntSum` helper, throwing `InputContractViolationException` instead of silently wrapping.
- Also tightened `Mvp2BatchGoldenScenarioIT`'s GS-06 assertion from a filtered subset to the
  full, exact, unfiltered reason list (`[OWNER_MISMATCH, LEAD_TIME_TOO_LONG]`), per the review's
  explicit requirement.
- Regression evidence (real, executed this round):
  - `Mvp2CalculationOrchestratorTest` grew from 7 to **16** (9 new: confidence downgrade blocks
    the candidate for both `STALE_INVENTORY` and `MISSING_INBOUND` and sends the metric to
    `REVIEW_REQUIRED`; an approved inbound transfer excludes an already-covered receiver; an
    approved outbound transfer includes a newly-shortened one; an invalid negative
    shortage-check projection excludes the receiver entirely; `projectedReceiverAtArrival`
    matches the hand-computed arrival-time figure, not the pre-arrival one; quality flags iterate
    in enum declaration order and both the flag set and the identified map reject mutation;
    open-transfer and confirmed-inbound aggregates beyond `Integer.MAX_VALUE` each throw). All
    16 passed, including the 7 pre-existing ones unchanged.
  - `EffectiveReceiverBaseRateTest` 6/6 and `Mvp2BatchGoldenScenarioIT` 1/1 (Oracle, now with the
    exact-list GS-06 assertion) both passed unchanged by the fixes -- confirming none of the five
    fixes altered any of the golden values.
  - Oracle-backed full Backend build: **338/338**, skip 0, failures/errors 0.
  - DB-free full Backend build: **338 total / 268 passed / 70 Oracle-conditioned skip**,
    failures/errors 0.
  - `git diff --check`: exit 0 (only pre-existing LF/CRLF warnings on already-tracked files).
- No Migration, REST/React, entity conversion, output persistence/transaction, Job/Step wiring,
  or approval/`MANUAL` parity code touched -- scoped strictly to the five findings.
- Next: Codex re-verifies the five fixes and their boundary tests; once accepted, the next
  bounded unit is entity conversion, atomic output persistence and run transaction/retry
  semantics.
