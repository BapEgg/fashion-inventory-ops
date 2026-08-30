# Archived Current Task — Phase 3 orchestration first review

Status: Phase 3 Batch in-memory calculation orchestration implemented; Codex 재검증 대기
Current role: Codex verification/review
Last updated: 2026-08-27

## Goal

승인된 `Mvp2InputGraph`를 기존 순수 Java 규칙에 연결해 한 run의 metric, quality flag,
candidate, reason, scenario 결과를 immutable in-memory aggregate로 만든다. 이번 단위는 DB
output 저장, run transaction, Job/Step wiring과 승인·`MANUAL` parity를 구현하지 않는다.

전체 Phase 3 계약은
[`../archive/state/2026-08-27-phase3-foundation-review-current-task.md`](../archive/state/2026-08-27-phase3-foundation-review-current-task.md),
계산 규칙은 [`../business-rules.md`](../business-rules.md), 물리 mapping은
[`../data-model.md`](../data-model.md)의 “Phase 3 Batch 물리 mapping”을 따른다.

## Calculation order

1. 각 anchor의 28일 window에서 statistics, quality evidence와 plan horizon을 계산한다.
   plan horizon은 분석일부터 모든 활성 inbound route의
   `leadTimeDays + targetCoverageDays` 최댓값까지며 route가 없으면 기본 7일이다.
2. 요청 버전 event 전체에서 `RepresentativeEventSelection`으로 관련 event와 대표 event를
   고르고 signal/confidence, baseline LOW/BASE/HIGH를 계산한다. baseline 제외에는 관련
   event 전체를 사용한다.
3. 가장 빠른 활성 route 또는 완전한 CONFIRMED inbound ETA를 metric 기준 lead로 사용하고,
   둘 다 없으면 7일을 사용한다. 해당 cutoff의 confirmed inbound, APPROVED/IN_TRANSIT open
   transfer와 active APPROVED draft 합계를 반영해 canonical projection과 provisional
   exception(`hasActionableCandidate=false`)을 만든다.
4. 자동 계산 가능한 BASE 부족 receiver에 대해 같은 SKU의 다른 anchor store를 donor로
   평가한다. lane별 route/current evidence와 대표 event의 route-window uplift BASE를 사용해
   `ApprovalBasisRecalculation`을 실행한다.
5. `eligible()`인 안정/이벤트 후보는 `ELIGIBLE/RECOMMENDED`, VARIABLE은
   `ELIGIBLE/COMPARISON_ONLY`, 나머지는 `REJECTED/NONE`이며 모든 탈락 사유를 enum 순서로
   보존한다.
6. 실제 eligible lane 유무로 metric exception/severity를 한 번 재분류한다. Eligible만
   `NO_ACTION/CONSERVATIVE/BASE/AGGRESSIVE` 네 scenario를 만들고 BASE 수량을 recommendation
   대표수량으로 사용한다.

## Output boundary

- 한 immutable aggregate가 분석일/version, anchor별 계산 결과, 최종 metric 결과와
  quality flags, candidate/reason/scenario를 식별 가능한 key로 제공한다.
- entity 생성과 저장 순서는 다음 persistence 단위가 담당한다. 계산 aggregate는 JPA entity,
  repository, `JdbcTemplate`, Spring Batch에 의존하지 않는다.
- 모든 iteration과 tie-break는 `storeId`, `skuId`, donor/receiver/route id 등 명시적인 안정
  순서로 고정한다.

## Constraints

- adapter의 8-statement/no-loop 계약을 유지하고 orchestration에서는 SQL을 0회 실행한다.
- 기존 순수 계산을 복제하거나 SQL/JPA에 수량 결정을 넣지 않는다.
- V1~V13 Migration, MVP-1 Job/API, 승인 transaction과 `MANUAL` 동작을 바꾸지 않는다.
- `ASSUMPTION` 상수는 `DemandAnalysisRules` 한 곳에서 사용한다.

## Required validation

- pure target: plan horizon, stale/missing-inbound confidence, effective event BASE/no-event
  parity, REQUESTED-only conflict, deterministic ordering과 rejected reason 전체 보존.
- `MVP-2-GS-V1` Oracle composition test는 adapter+orchestrator를 실행하되 output을 저장하지
  않고 metric 12, recommendation 4, scenario 8을 확인한다.
- Golden 핵심값: GS-01 eligible BASE 11, GS-02 effective BASE rate 3.000000000000/BASE 수량
  20, GS-05 `INBOUND_ALREADY_COVERS`, GS-06 `OWNER_MISMATCH`+`LEAD_TIME_TOO_LONG`, GS-03/04
  자동 recommendation 없음, GS-04 `OOS_CENSORED`.
- composition 전체 SQL count는 adapter의 8회뿐임을 계측한다.
- 실제 Oracle 전체 Backend build, DB-free 전체 build와 `git diff --check`를 실행한다.

## Completion condition

동일 graph가 항상 동일한 in-memory 결과와 Golden 값을 만들고 새 SQL·DB write가 없음을
증명하면 Codex 검증·리뷰로 인계한다. 승인 후 다음 단위에서 entity 변환, 원자적 저장과
run transaction/retry를 구현한다.

## 구현 완료 (이번 라운드)

### 새 pure helper와 orchestrator

- [EffectiveReceiverBaseRate.java](../../backend/src/main/java/com/bapegg/stockpilot/demand/EffectiveReceiverBaseRate.java):
  대표 KNOWN_EVENT가 해당 route의 도착~목표 커버리지 구간과 겹칠 때만 baseline BASE에
  uplift BASE를 적용한다(§10). `TransferScenarioSet`은 시나리오별로 이미 자체 적용하므로
  이 helper를 쓰지 않고, `ApprovalBasisRecalculation` 호출 전 receiver BASE를 미리
  계산하는 데만 쓴다.
- [Mvp2CalculationOrchestrator.java](../../backend/src/main/java/com/bapegg/stockpilot/batch/Mvp2CalculationOrchestrator.java):
  6단계 계산 순서를 그대로 구현했다. `ApprovalBasisRecalculation`/
  `TransferCandidateEvaluation`/`TransferScenarioSet`/`RepresentativeEventSelection`은
  호출만 하고 재구현하지 않았다.
- 새 quality flag 도출(이전에 없던 로직): `STALE_INVENTORY`(현재 snapshot >24h 오래됨 또는
  관측일 현지 날짜 불일치), `MISSING_INBOUND`(수량/ETA 누락 입고 행).
- 새 결과 타입 `Mvp2MetricResult`/`Mvp2CandidateResult`/`Mvp2CalculationResult`
  (`batch` package) — JPA/JdbcTemplate/Spring Batch 의존성 없음, identifiable key map
  (`metricsByStoreSku`/`candidatesByReceiver`)과 결정론적 정렬 flat list를 함께 제공한다.

### Golden test로 발견·수정한 실제 버그 2건

1. receiver gate가 "자동 계산 가능"만 검사해 재고가 충분한 donor(자기 자신도
   auto-quantifiable 신호)까지 receiver로 잘못 평가되어 후보가 4개 대신 9개 생성됐다.
   `currentAvailable < target` 얕은 pre-check를 추가해 고쳤다.
2. 그 pre-check가 처음엔 inbound 반영된 projection을 써서, GS-05가 이미 그 단계에서
   커버되어 candidate 자체가 안 만들어졌다(INBOUND_ALREADY_COVERS를 검증할 방법이
   없어짐). pre-check를 `currentAvailable`만 보도록 좁혀 GS-05가 여전히 후보로 평가되고
   `ApprovalBasisRecalculation`이 route-specific inbound로 정식 판정하도록 고쳤다.

### 실제 실행 증거

- [EffectiveReceiverBaseRateTest.java](../../backend/src/test/java/com/bapegg/stockpilot/demand/EffectiveReceiverBaseRateTest.java)
  6/6(pure).
- [Mvp2CalculationOrchestratorTest.java](../../backend/src/test/java/com/bapegg/stockpilot/batch/Mvp2CalculationOrchestratorTest.java)
  7/7(pure, hand-built graph): REQUESTED만 PENDING_TRANSFER_CONFLICT, APPROVED는 아님;
  MISSING_INBOUND 양쪽; STALE_INVENTORY 양쪽; deterministic ordering.
- [Mvp2BatchGoldenScenarioIT.java](../../backend/src/test/java/com/bapegg/stockpilot/batch/Mvp2BatchGoldenScenarioIT.java)
  1/1(Oracle, 실제 MVP-2-GS-V1 seed, output 저장 없음): metric 12/candidate 4/scenario 8,
  GS-01 BASE=11, GS-02 effective rate=3.000000000000/BASE=20, GS-03/04 candidate 없음,
  GS-04 OOS_CENSORED, GS-05 INBOUND_ALREADY_COVERS, GS-06 OWNER_MISMATCH+
  LEAD_TIME_TOO_LONG. adapter+orchestrator 전체 SQL 실행 횟수가 여전히 8임을 같은
  connection-proxy 계측으로 재확인했다.
- Oracle 전체 Backend build: **329/329**, skip 0, failures/errors 0.
- DB-free 전체 build: **329 total/259 passed/70 skip**, failures/errors 0.
- `git diff --check`: exit 0 (기존 파일의 LF/CRLF 경고만).
- Migration, REST/React, entity 변환/원자적 저장/Job wiring, 승인·MANUAL parity는 이번
  범위 밖이며 손대지 않았다.

## Next verifiable action

Codex가 6단계 계산 순서가 정확히 구현됐는지, 두 버그 수정이 올바른지, golden 값과
전체 329개 기준선이 재현되는지 독립 검증한다. 승인 시 entity 변환·원자적 저장·run
transaction/retry(§5)를 다음 단위로 지정한다.
