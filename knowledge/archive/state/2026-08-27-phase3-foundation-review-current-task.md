# Archived Current Task — Phase 3 foundation review input

Status: Phase 3 Batch 기반 계층(entity/repository/공유 helper) 구현·검증 완료; 나머지 구현 진행 중
Current role: Claude implementation
Last updated: 2026-08-27

## Goal

MVP-2의 28일 입력을 bulk로 읽어 승인된 순수 Java 규칙을 실행하고 V6 결과 구조에
원자적으로 저장한다. 기존 MVP-1 Job/API를 보존하며 REST와 React는 연결하지 않는다.

공식 계산·상태 계약은 [`../business-rules.md`](../business-rules.md), 물리 mapping과
transaction 계약은 [`../data-model.md`](../data-model.md)의 “Phase 3 Batch 물리 mapping”을
따른다.

## 1. 호환 Job과 실행 키

- 기존 `inventoryAnalysisJob`, `InventoryAnalysisTasklet`, 공개 `AnalysisRunService` 동작은
  바꾸지 않는다. Job bean이 둘이 되므로 기존 주입부에 명시적 qualifier만 추가한다.
- 별도 `mvp2InventoryAnalysisJob`/step/tasklet과 내부 application service를 만든다.
  Controller, DTO, ProblemDetail은 만들지 않는다.
- identifying parameter 세 개는 `analysisDate`, 정규화한 `inputSnapshotVersion`, 고정
  `DemandAnalysisRules.RULE_VERSION`(`MVP-2`)이다. version은 nonblank, 최대 64자다.
- repository에는 triple 조회를 추가한다. 같은 COMPLETED triple은 no-op, 다른 input version은
  새 run이다. MVP-1 두 인자 조회/constructor는 유지한다.

## 2. 입력 adapter와 선검증

- 전용 JDBC adapter가 일곱 bulk query 그룹만 실행한다: anchor+catalog+optional policy,
  28일 inventory/sales, event, inbound, open transfer, route, 활성 APPROVED draft 합계.
  store–SKU 또는 lane loop 안에서 repository/SQL을 호출하지 않는다.
- anchor는 요청 version과 `snapshot_date=analysisDate`인 모든 store–SKU다. 하나도 없으면
  실패한다. 각 anchor는 `[analysisDate-28, analysisDate-1]`의 inventory 28행과 sales
  28행이 날짜별로 정확히 있어야 한다. 누락·version 혼합·future snapshot·overflow는
  input-contract failure이며 output을 쓰지 않는다.
- `analysisReferenceAt = analysisDate+1일 00:00 Asia/Seoul`. anchor가 24시간보다 오래됐거나
  관측 snapshot 현지 날짜가 행 날짜와 다르면 `STALE_INVENTORY`; 관측 행은 기존 pure
  rule대로 통계에서 제외한다.
- 정책 행이 없으면 `display=1`, `safety=2`, `capacity=100`, `target=7`, `retained=14`를 쓴다.
  이 상수는 `DemandAnalysisRules` 한 곳에 둔다. route 부재는 fallback이 아니라 탈락이다.
- inbound 참조의 quantity/ETA가 하나라도 불완전하면 `MISSING_INBOUND`; 완전한
  `CONFIRMED`만 cutoff 이내 수량에 포함한다. open transfer 수량은
  `APPROVED/IN_TRANSIT`만 projection에 포함하고 `REQUESTED`만 exact-lane conflict다.

## 3. 공통 pure-rule 보정

- 대표 event 선택을 pure helper로 분리한다. 요청 version의 관련 event를
  `(startDate,eventCode)` 순으로 정렬해 첫 행을 대표로 삼고, Batch·승인·`MANUAL`이
  같은 helper를 사용한다. demand baseline 제외에는 관련 event 전체를 사용한다.
- `DemandSignalClassification`에 stale/missing-inbound 입력을 받는 overload를 추가하고
  기존 signature는 두 값을 false로 위임해 호환한다. 네 quality flag 중 하나라도 있으면
  기존 규칙대로 confidence를 LOW로 내린다.
- route별 effective receiver BASE는 대표 `KNOWN_EVENT`가
  `[analysisDate+lead, arrival+targetCoverage]`와 겹칠 때만 baseline BASE × uplift BASE,
  scale 12 `HALF_UP`이다. donor 보호량은 baseline HIGH를 유지한다.

## 4. 계산 순서

1. raw graph 전체를 검증한 뒤 각 anchor의 28일 window → statistics → plan horizon → signal/
   confidence → baseline low/base/high와 quality flags를 계산한다.
2. 가장 빠른 active route 또는 완전한 confirmed inbound ETA의 lead를 metric 기준 lead로
   사용하고, 둘 다 없으면 7일이다. 그 cutoff의 canonical projection과 provisional
   exception(`hasActionableCandidate=false`)을 만든다.
3. 자동 계산 가능한 store–SKU 중 confirmed inbound를 제외하면 canonical BASE 부족인
   receiver만 같은 SKU의 다른 anchor store와 lane 후보를 만든다. donor는 valid projection,
   HIGH rate와 정책이 있어야 한다.
4. 각 lane은 route별 inbound/open-transfer/draft projection과 effective BASE로
   `ApprovalBasisRecalculation`을 실행한다. `eligible()`이 최종 후보 eligibility다.
   안정/이벤트는 RECOMMENDED, VARIABLE은 COMPARISON_ONLY, 나머지는 REJECTED/NONE이다.
5. 실제 eligible lane 유무로 metric exception/severity를 한 번 재분류한다. Eligible만
   네 scenario를 계산하고, BASE scenario 수량을 recommendation 대표수량으로 쓴다.

## 5. 영속성과 transaction

- `SpInventoryMetric`의 V6 전 컬럼과 `SpMetricQualityFlag`, `SpCandidateReason`,
  `SpRebalanceScenario` mapping/factory/repository를 구현한다. `SpRebalanceRecommendation`에는
  MVP-2 factory를 추가하되 MVP-1 constructor를 유지한다. 새 Migration은 만들지 않는다.
- 저장 순서는 run → metrics → quality flags → recommendations → reasons → scenarios →
  run COMPLETED다. IDENTITY parent id가 필요한 bounded 합성 규모이므로 JPA insert를 허용하되
  `saveAll/flush`를 loop마다 호출하지 않고 단계별 한 번만 수행한다.
- run prepare/restart와 failure 표시는 `REQUIRES_NEW` coordinator가 담당한다. metric부터
  COMPLETED까지는 step의 단일 output transaction이다. 예외 시 output은 전부 rollback한 뒤
  listener가 run을 FAILED로 남긴다. FAILED 재시도는 같은 run을 RUNNING으로 되돌린다.
- completed domain run 뒤 Batch metadata 기록 전 crash window에서는 재실행 tasklet이 no-op해
  metadata만 완료한다. 강제 JVM 종료로 남은 Batch STARTED/RUNNING 자동 복구는 이번 범위 밖이다.

## 6. 승인·MANUAL parity 수정

- current-basis loader가 대표 event와 모든 active inbound-route lead를 요청 version으로
  다시 읽고 effective receiver BASE를 `LoadedApprovalBasis`에 넣는다.
- policy row 부재 시 Batch와 같은 기본값을 쓰며, pending conflict 집합은 REQUESTED만 둔다.
  APPROVED/IN_TRANSIT 수량과 활성 draft 합산, recommendation→donor lock 순서는 유지한다.
- event가 적용된 persisted BASE 수량을 사유 없이 승인하면 성공하고, 같은 수량의 MANUAL은
  `reasonRequired=false`여야 한다. 다른 수량·stale·동시 승인 계약은 그대로다.

## 7. 필수 검증

- Pure target: event 선택 tie-break, stale/missing confidence, REQUESTED-only conflict,
  event effective BASE와 no-event parity, policy fallback.
- Oracle input/persistence target: 일곱 read 그룹 1회씩, loop query 0, child mapping/scale/timezone,
  중간 writer 실패의 전체 rollback과 FAILED→동일 run 재시작, COMPLETED no-op, 새 input 새 run.
- `MVP-2-GS-V1`/2026-09-30: metric 12, recommendation 4, scenario 8. GS-01은 open transfer를
  반영한 eligible BASE 11; GS-02는 rate 3.000000000000/BASE 20; GS-05는
  `INBOUND_ALREADY_COVERS`; GS-06은 `OWNER_MISMATCH`와 `LEAD_TIME_TOO_LONG`; GS-03/04는
  자동 recommendation 없음, GS-04 quality는 `OOS_CENSORED`다.
- Oracle event-aware 승인·MANUAL과 GS-01 APPROVED-open-transfer 회귀를 추가한다.
- 전체 Oracle Backend build, DB-free build, 기존 MVP-1 Golden IT와 `git diff --check`를 실행한다.
  합성 run의 bulk query 수·입력/출력 행 수·wall-clock을 기록하되 휴대환경 SLA는 주장하지 않는다.

## Out of scope and completion

REST/React, 새 오류 계약, V14+, staging/chunk, 실제 ERP/재고 차감, LLM은 제외한다.
위 표적·전체 검증이 실제로 통과하고 기존 289-test 기준선에 회귀가 없을 때 Claude는
구현 결과와 실행 수치를 기록한 뒤 Codex 검증·리뷰로 인계한다.

## 이번 라운드 구현 완료 (기반 계층)

- 새 entity·repository 4종(`SpDemandEvent`, `SpMetricQualityFlag`, `SpCandidateReason`,
  `SpRebalanceScenario`)과 새 enum(`demand.MetricQualityFlag`, `rebalance.DemandEventType`).
  V6 기존 컬럼/테이블에만 매핑하며 새 Migration은 없다.
- `SpInventoryMetric`의 V6 17개 컬럼을 채우는 MVP-2 생성자(legacy 컬럼 투영 포함,
  MVP-1 생성자는 불변)와 `SpRebalanceRecommendation.createMvp2Candidate(...)` static
  factory(nullable 수량 지원, MVP-1 생성자는 불변).
- `SpAnalysisRunRepository`의 triple-key(`analysisDate, inputSnapshotVersion, ruleVersion`)
  조회 추가(§1의 no-op/재시작/신규-run 판정에 필요).
- 공유 순수 helper `demand.RepresentativeEventSelection`(§3의 대표 event 선택 계약)으로
  `DemandSignalClassification`을 리팩터링(동작 불변, 기존 9개 테스트 무수정 통과로 확인).
  `eventCode` tie-break를 이번에 처음 추가했다.
- 실행 증거: `RepresentativeEventSelectionTest` 5/5(신규),
  `DemandSignalClassificationTest` 9/9(무수정), `Mvp2BatchEntityPersistenceMappingIT`
  7/7(신규, Oracle). Oracle 전체 301/301(skip 0). DB-free 전체 301 total/243 passed/58
  skip. `git diff --check` 통과.

## 남은 구현 범위 (§1-2, §4-7 대부분)

아래는 아직 구현되지 않았다. 위 기반 계층 위에서 다음 순서로 진행한다.

1. **입력 adapter**(§2): 전용 JDBC adapter로 일곱 bulk read 그룹(anchor+catalog+policy,
   28일 inventory/sales, event, inbound, open transfer, route, 활성 APPROVED draft 합계)을
   각 한 번씩 실행하고 store–SKU/lane key로 메모리 grouping한다. anchor 완전성·input-contract
   실패 판정(§2)도 여기서 구현한다.
2. **계산 오케스트레이션**(§4): raw graph 검증 → 28일 window/통계/신호/confidence/baseline
   → canonical projection/provisional exception → candidate 생성(같은 SKU 다른 anchor
   store lane) → `ApprovalBasisRecalculation` 실행 → candidate/exception 최종 재분류 →
   eligible candidate만 네 scenario 계산, 순서 그대로 구현한다.
3. **영속화·transaction**(§5): 저장 순서(run → metrics → quality flags → recommendations →
   reasons → scenarios → run COMPLETED), `REQUIRES_NEW` run 상태 coordinator, 단일 output
   transaction, FAILED 재시도/COMPLETED no-op 시맨틱을 구현한다. 새 MVP-2 Job/Step/Service는
   기존 `inventoryAnalysisJob`을 건드리지 않고 별도 bean으로 추가한다(§1).
4. **승인·MANUAL parity**(§6): `CurrentApprovalBasisLoader`가 `RepresentativeEventSelection`과
   새 `SpDemandEventRepository`를 사용해 대표 event와 route별 effective receiver BASE를
   계산해 `LoadedApprovalBasis`에 넣는다. policy 기본값·`REQUESTED`-only conflict는 이미
   구현된 계약과 일치하는지 확인만 하면 된다(현재 loader 동작 재확인 필요).
5. **필수 검증**(§7): pure target(event tie-break은 완료, stale/missing confidence, event
   effective BASE parity 등 나머지), Oracle input/persistence target(bulk read 1회씩,
   loop query 0, rollback/재시작/no-op/새-run), `MVP-2-GS-V1` golden test(GS-01~06 기대값),
   Oracle event-aware 승인·MANUAL 회귀, GS-01 APPROVED-open-transfer 회귀.

## Next verifiable action

Claude가 입력 adapter(§2)부터 이어서 구현하고 실제 Oracle 표적 테스트로 검증한다.
