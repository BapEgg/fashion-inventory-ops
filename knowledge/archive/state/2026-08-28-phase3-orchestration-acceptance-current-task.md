# Archived Current Task — Phase 3 orchestration acceptance

Status: Phase 3 Batch in-memory orchestration — 5건 수정 완료; Codex 재검증 대기
Current role: Codex verification/review
Last updated: 2026-08-28

## Goal

현재 `Mvp2CalculationOrchestrator`의 계산 경계 결함 다섯 건을 수정하고, 요구된
stale/missing confidence·projection·ordering·overflow 회귀를 추가한다. 범위는 여전히
DB-write-free in-memory calculation이며 entity 저장, run transaction과 Job/Step wiring은
구현하지 않는다.

계산 규칙은 [`../business-rules.md`](../business-rules.md), Candidate 물리 의미는
[`../data-model.md`](../data-model.md)의 “Phase 3 Batch 물리 mapping”을 따른다. 첫 구현과
검증 원문은
[`../archive/state/2026-08-28-phase3-orchestration-first-review-current-task.md`](../archive/state/2026-08-28-phase3-orchestration-first-review-current-task.md)에 보존했다.

## Open findings

1. `STALE_INVENTORY`와 `MISSING_INBOUND`를 quality flag에만 추가하고 `signal.confidence`에
   반영하지 않는다. `DATA_INSUFFICIENT/NONE` 우선순위는 유지하되, 그 외 하나 이상의
   quality flag는 `LOW`여야 하며 stable/event 자동 후보가 차단되고 metric은
   `REVIEW_REQUIRED`가 되어야 한다.
2. receiver shortage gate가 `currentAvailable`만 비교해 `APPROVED/IN_TRANSIT` open
   transfer를 무시한다. “confirmed inbound만 제외한 BASE 부족” projection으로 gate하고
   음수 projection은 Candidate 계산에서 제외한다. 승인 이동 inbound로 이미 해소된
   receiver와 승인 이동 outbound 때문에 새로 부족해진 receiver를 각각 회귀로 고정한다.
3. `Mvp2CandidateResult.projectedReceiverAtArrival`에
   `receiverProjectedBeforeDemand`를 넣고 있다. effective receiver BASE와 route lead 동안의
   수요를 차감한 `receiverAtArrivalWithoutNewTransfer`를 제공해야 한다.
4. `Mvp2CalculationResult`의 식별 map과 metric quality flag가 `Map.copyOf`/`Set.copyOf`로
   명시적 insertion/enum iteration order를 잃는다. immutable이면서 store/SKU 및 enum
   순서를 보존해야 한다.
5. confirmed inbound와 open transfer 합계를 `int +=`로 계산해 여러 정상 범위 행의 합이
   32-bit를 넘으면 wrap된다. exact 합산으로 overflow를 안정적으로 거부해야 한다.

## Constraints

- 기존 pure Java 계산을 복제하거나 SQL/JPA에 수량 결정을 넣지 않는다.
- adapter 8 statements, orchestrator SQL 0회와 output DB write 0회를 유지한다.
- V1~V13, MVP-1, 승인 transaction과 `MANUAL` 동작을 변경하지 않는다.
- rejected reason은 전체 enum 순서로 보존하고 GS-06은 필터 없이 정확한 전체 목록을
  검증한다.
- 사용자 변경이 섞인 dirty worktree를 보존한다.

## Required validation

- Pure: stale/missing flag가 confidence/exception/candidate gate까지 반영됨; approved open
  inbound/outbound receiver gate; invalid projection 차단; 도착 시점 예상재고; immutable
  map/flag iteration order; inbound/open-transfer aggregate overflow.
- Existing pure targets: `EffectiveReceiverBaseRateTest`, `Mvp2CalculationOrchestratorTest`.
- Oracle `Mvp2BatchGoldenScenarioIT`: metric 12, candidate 4, scenario 8과 GS-01~06 핵심값,
  SQL 8회 유지. GS-06 reason은 정확히
  `[OWNER_MISMATCH, LEAD_TIME_TOO_LONG]`이어야 한다.
- Oracle 전체 Backend build, DB-free 전체 build, `git diff --check`.

## Completion condition

다섯 finding의 재현 테스트와 수정이 모두 존재하고 Golden/전체 회귀가 통과하면 Codex
재검증으로 인계한다. 그 전에는 orchestration을 accepted로 기록하지 않는다.

## 이번 라운드 수정 완료

### Fix 1 — confidence downgrade

[Mvp2CalculationOrchestrator.java](../../backend/src/main/java/com/bapegg/stockpilot/batch/Mvp2CalculationOrchestrator.java)의
`buildAnchorEvidence`가 `STALE_INVENTORY`/`MISSING_INBOUND` quality flag 계산 직후, signal이
`DATA_INSUFFICIENT`(confidence=NONE)가 아니면 `signal`을 confidence=`LOW`로 재구성한다. 이
값이 gate·`TransferScenarioSet`·provisional exception 계산에 그대로 전파된다.

### Fix 2 — open-transfer-aware receiver gate

`hasMetricLevelBaseShortage`가 이제 `APPROVED/IN_TRANSIT` open transfer 양방향을 포함하고
confirmed inbound는 계속 제외하는 projection으로 gate한다(INBOUND_ALREADY_COVERS는 여전히
lane 단계에서 발견). 이 projection이 `isInputInvalid()`면 receiver를 완전히 제외한다.

### Fix 3 — 도착 시점 projection

`evaluateCandidate`가 `Mvp2CandidateResult.projectedReceiverAtArrival`에
`recalculation.receiverProjectedBeforeDemand()` 대신
`receiverProjection.receiverAtArrivalWithoutNewTransfer(effectiveReceiverBaseRate,
route.leadTimeDays())`를 넣는다.

### Fix 4 — immutable iteration order

[Mvp2InputGraph.java](../../backend/src/main/java/com/bapegg/stockpilot/batch/Mvp2InputGraph.java),
[Mvp2CalculationResult.java](../../backend/src/main/java/com/bapegg/stockpilot/batch/Mvp2CalculationResult.java)의
모든 식별 map이 `Map.copyOf` 대신 `Collections.unmodifiableMap(new LinkedHashMap<>(...))`을
쓴다. [Mvp2MetricResult.java](../../backend/src/main/java/com/bapegg/stockpilot/batch/Mvp2MetricResult.java)의
`qualityFlags`는 `Set.copyOf` 대신 `EnumSet`/`Collections.unmodifiableSet`을 써서 enum
선언 순서를 보존한다.

### Fix 5 — exact aggregate arithmetic

`sumOpenTransferQuantity`/`sumConfirmedInboundBeforeCutoff`가 `long`으로 누적한 뒤 새
`safeIntSum` helper로 **합계** 자체의 32bit 범위를 검사한다(개별 행은 adapter가 이미
검사했으므로 여기서는 합계만).

### 그 외

`Mvp2BatchGoldenScenarioIT`의 GS-06 단정을 filter된 부분집합에서 필터 없는 정확한 전체
목록(`[OWNER_MISMATCH, LEAD_TIME_TOO_LONG]`) 비교로 강화했다.

### 실제 실행 증거

- [Mvp2CalculationOrchestratorTest.java](../../backend/src/test/java/com/bapegg/stockpilot/batch/Mvp2CalculationOrchestratorTest.java)
  **16/16**(9개 신규): confidence downgrade가 candidate를 막고 metric을 REVIEW_REQUIRED로
  보냄(STALE_INVENTORY·MISSING_INBOUND 각각), approved inbound가 이미 해소된 receiver를
  배제, approved outbound가 새로 부족해진 receiver를 포함, invalid projection 배제,
  도착 시점 projection 값 검증, quality flag enum 순서·map/set 불변성, open-transfer·
  confirmed-inbound aggregate overflow 각각.
- `EffectiveReceiverBaseRateTest` 6/6, `Mvp2BatchGoldenScenarioIT` 1/1(GS-06 강화된 단정
  포함) — 다섯 수정 후에도 변경 없이 통과, golden 값에 영향 없음을 확인했다.
- Oracle 전체 Backend build: **338/338**, skip 0, failures/errors 0.
- DB-free 전체 build: **338 total/268 passed/70 skip**, failures/errors 0.
- `git diff --check`: exit 0 (기존 파일의 LF/CRLF 경고만).
- Migration, REST/React, entity 변환/원자적 저장/Job wiring, 승인·MANUAL parity는 이번
  범위 밖이며 손대지 않았다.

## Next verifiable action

Codex가 다섯 finding이 실제로 닫혔는지, golden 값과 전체 338개 기준선이 회귀하지 않는지
독립 검증한다. 승인 시 entity 변환·원자적 저장·run transaction/retry(§5)를 다음 단위로
지정한다.
