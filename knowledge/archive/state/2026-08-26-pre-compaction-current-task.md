  # Current Task

Status: MVP-2 Phase 2 후보·경로 규칙 Codex 리뷰 finding 3건 수정 대기; 시나리오 수량 보류

Current role: Claude implementation (Codex review findings remediation)
Last updated: 2026-08-26

## Goal

승인된 MVP-2 규칙을 Spring/JPA와 독립된 결정론적 Java 값 객체와 계산으로
구현하고 GS-01~GS-06 단위 테스트로 고정한다. Phase 1 Schema와 Seed를 다시
설계하거나 MVP-1 동작을 변경하지 않는다.

## Implemented prerequisite

- `V6__evolve_stockpilot_mvp2_schema.sql`: legacy backfill, 입력·결과·결정·Draft
  Schema와 호환성 제약
- `V7__load_mvp2_synthetic_scenarios.sql`: `MVP-2-GS-V1` GS-01~GS-06 입력
- `V8__add_mvp2_domain_comments.sql`: 허용값과 데모 경계
- `data/seed/mvp2`와 확장된 `scripts/validate-seed.ps1`
- 깨끗한 Oracle 및 기존 V5→V8 업그레이드, 백엔드 27개 테스트, Seed 검증,
  Frontend build 통과

자세한 사실과 checksum은 `knowledge/state/implemented-state.md`의
MVP-2 transition boundary를 따른다.

## Required specification

Phase 2에 필요한 부분만 읽는다.

1. `knowledge/business-rules.md` 1~12절
2. `knowledge/data-model.md` 5~7절과 V6 실제 Schema
3. `knowledge/project.md` Golden Scenario와 구현 순서
4. 기존 순수 Java `InventoryMetricCalculation`, `RebalanceCalculation`과 테스트
5. `data/seed/mvp2/README.md`

문서와 Schema가 다르면 실제 V6~V8과 Oracle 적용 결과를 우선하고 차이를 보고한다.

## Confirmed MVP-2 demo boundary

1. 28일, 최소 14일, CV 0.35, 급증, 24시간, 7일/14일과 fallback은
   rule version `MVP-2`의 `ASSUMPTION`이다.
2. 같은 소유권 또는 명시적으로 허용된 국내 매장 사이만 이동 가능하다.
3. 실제 배송 과정 없이 방향성 경로의 lead time만 계산한다.
4. 이벤트 uplift는 low/base/high 입력이며 Java나 AI가 예측하지 않는다.
5. `VARIABLE`은 비교 시나리오만 만들고 기본 추천 수량은 `NULL`이다.
6. 승인 검증은 수량·버전·상태를 결정하지만 재고를 변경하지 않는다.
7. AI는 Java 결과를 설명할 뿐 신호·수량·후보·상태를 결정하지 않는다.

## Phase 2 implementation order

1. 28일 관측 입력과 OOS 검열, 최신성, 이벤트·거래 특성을 표현하는 immutable
   Java input/value object를 만든다.
2. `effectiveDemandDays`, 품질 플래그와 관측 통계를 scale/rounding 규칙까지
   순수 함수로 구현한다.
3. 수요 신호 우선순위, confidence, low/base/high 수요율을 구현한다.
4. 입고·진행 중 이동을 반영한 projected available과 exception/severity를 구현한다.
5. 소유권·국내 경로·lead time·최소/배수/최대·용량·donor 보존 규칙으로 후보와
   모든 탈락 사유를 결정한다.
6. `NO_ACTION/CONSERVATIVE/BASE/AGGRESSIVE` 양쪽 매장 결과를 계산한다.
   `VARIABLE`에는 대표 추천 수량을 만들지 않는다.
7. 승인 요청의 stale version, 허용 수량, 상태 shape를 순수 Java로 검증하되
   Draft 저장·DB 잠금·REST 처리는 Phase 4로 남긴다.
8. GS-01~GS-06과 경계값·overflow·rounding 단위 테스트를 추가하고 MVP-1 전체
   회귀 테스트를 실행한다.

## Constraints

- V1~V8 Migration과 Seed를 수정하지 않는다. 필요한 Schema 결함을 발견하면
  새 Migration 근거 없이는 진행하지 않고 보고한다.
- Phase 2에서는 JPA entity, Batch, REST, React, AI provider를 구현하지 않는다.
- 임계값과 상태 결정은 Java 코드 한 곳에서 관리하고 AI prompt에 복제하지 않는다.
- `SP_REBALANCE_RECOMMENDATION` 호환 의미와 Scenario 자식 구조를 유지한다.
- 실제 기업 정책, 운영 데이터, 산업 표준이라고 표현하지 않는다.

## Definition of done

- GS-01~GS-06이 승인된 신호·품질·후보 분기로 결정론적으로 재현된다.
- low/base/high, projected inventory, 후보 사유와 네 시나리오가 문서 식과
  scale/rounding에 맞는 exact assertion으로 고정된다.
- `VARIABLE` 대표 추천량 부재, owner mismatch, long lead time, confirmed inbound
  분기가 명시적으로 테스트된다.
- 모든 신규 테스트와 기존 MVP-1 테스트의 실제 실행 결과만 상태 문서에 기록한다.

## Blockers

후보·경로 증분에 correctness finding 3건이 남아 있다. 동일 SKU·서로 다른 매장
조건을 평가 경계가 직접 검증해야 하고, 반환 reasons 집합을 외부에서 변경할 수
없어야 하며, 도착 시 예상재고가 정확히 0인 동률은 "예상 품절일보다 늦지 않다"는
규칙에 맞게 `LEAD_TIME_TOO_LONG`으로 거부하지 않아야 한다. 이전 finding들은
모두 승인됐다. `DISPLAY_MINIMUM_VIOLATION`의 현재 최소/배수 feasibility 매핑은
Section 7·8 문맥상 수용한다.

## 재고 예상값 입력 검증 우회 finding 수정 (2026-08-26)

`InventoryExceptionClassification.classify`의 비음수 정책 검증이
`InventoryProjection`의 helper 메서드(`receiverAtArrivalWithoutNewTransfer` 등)
안에만 있어서, `projection.isInputInvalid()`(→ `NON_ACTIONABLE`)나
`NONE`/`LOW` confidence·`rates.reviewRequired()`(→ `REVIEW_REQUIRED`) 조기
반환 경로에서는 그 helper들을 호출하지 않으므로 음수
`earliestArrivalLeadTimeDays`/`receiverTargetCoverageDays`/`retainedDays`/
`displayMinimum`/`safetyStock`이 전혀 검증되지 않은 채 결과가 나가는 결함을
수정했다.

- `classify(...)` 진입 시 다섯 파라미터를 어떤 분기로 가든 항상 먼저 검증하도록
  최상단으로 옮겼다. `NON_ACTIONABLE`/`REVIEW_REQUIRED` 조기 반환도 이제 이
  검증을 통과해야만 도달한다.
- Public API 변경 없음(기존 시그니처 그대로, 검증 위치만 이동).
- 회귀 테스트 추가: `InventoryExceptionClassificationTest`에 `isInputInvalid()`가
  참인 상태에서 음수 `safetyStock`을 주면 예외가 발생함(이전에는
  `NON_ACTIONABLE`로 조용히 통과), `confidence=NONE` 상태에서 음수
  `retainedDays`를 주면 예외가 발생함(이전에는 `REVIEW_REQUIRED`로 조용히
  통과)을 확인하는 테스트 2개.
- 검증: `.\gradlew.bat build --rerun-tasks`(DB_URL 없음) — compile/jar/check 전부
  통과, 비-skip 테스트 106개 전부 통과(기존 104 + 신규 2), 0 failures/errors,
  Oracle IT 2개는 기존과 동일하게 정상 skip. Entity/Migration/Oracle 코드를
  건드리지 않아 Oracle 통합 검증은 미실행/해당 없음이다.

## 재고 예상값 추가 correctness finding 2건 수정 (2026-08-26)

- `InventoryProjection`의 `receiverAtArrivalWithoutNewTransfer`,
  `receiverTargetQuantity`, `donorProtectedQuantity`에 V6과 동일한 비음수 검증을
  추가했다(`leadTimeDays`, `receiverTargetCoverageDays`, `receiverDisplayMinimum`,
  `donorRetainedDays`, `donorDisplayMinimum`, `donorSafetyStock` 각각).
- `receiverTargetQuantity` 내부의 `leadTimeDays + receiverTargetCoverageDays`
  합산을 `long`으로 넓혀서 계산한 뒤 `ceilDemand`에 전달한다(`ceilDemand`도
  `long days`를 받도록 변경). 두 값 모두 개별적으로는 유효해도 plain `int` 합산은
  overflow할 수 있었다.
- Public API 변경 없음(기존 메서드에 검증 추가, 반환 타입은 그대로 `long`).
- 회귀 테스트 추가: `InventoryProjectionTest`에 세 메서드 각각의 음수 입력 거부
  테스트, `leadTimeDays=Integer.MAX_VALUE + receiverTargetCoverageDays=10`이
  wrap되지 않고 정확한 합(2147483657)으로 처리됨을 확인하는 테스트.
- 검증: `.\gradlew.bat build --rerun-tasks`(DB_URL 없음) — compile/jar/check 전부
  통과, 비-skip 테스트 104개 전부 통과(기존 100 + 신규 4), 0 failures/errors,
  Oracle IT 2개는 기존과 동일하게 정상 skip. Entity/Migration/Oracle 코드를
  건드리지 않아 Oracle 통합 검증은 미실행/해당 없음이다.

## 재고 예상값·예외 correctness finding 3건 수정 (2026-08-26)

- `InventoryProjection.calculate`가 `reservedQuantity > onHandQuantity`일 때
  `IllegalArgumentException`을 던지던 것을 제거했다. 2절은 이를 두 가지
  `NON_ACTIONABLE` 입력 조건 중 하나로 명시하므로(예외로 중단하지 않고 결과를
  내야 함), 이제 `currentAvailable`이 음수로 그대로 계산되고 자동 보정되지
  않는다. `hasNegativeProjection()`을 `isInputInvalid()`로 이름을 바꿔
  `currentAvailable<0`도 함께 확인한다.
- `InventoryExceptionClassification.classify`가 `DemandSignalType`/
  `incompleteEventData` 조합 목록 대신 이미 계산된 `DemandConfidence`를 직접
  받도록 바꿨다. 4절의 confidence는 이미 모든 품질 플래그와 "자동 수량 불가"
  신호 유형을 `NONE`/`LOW`로 접어두므로, `confidence==NONE || confidence==LOW`
  하나로 검사하면 신호 유형 목록을 일일이 나열하는 것보다 간단하면서도 더
  정확하다(예: `OOS_CENSORED`가 붙은 `STABLE_REPEAT`도 이제 올바르게
  `REVIEW_REQUIRED`로 간다 — 이전 코드는 이 경우를 놓쳤다).
- `InventoryProjection.calculate`의 세 합산식을 `long`으로 넓혀 계산한 뒤
  `Math.toIntExact`로 한 번만 변환한다. 개별 입력은 각각 `int` 범위 안이어도
  합산 결과가 `int` 범위를 넘으면(`RebalanceCalculation`에서 이미 고쳤던 것과
  동일한 종류의 결함) 조용히 wrap되지 않고 `ArithmeticException`을 던진다.
- Public API 변경: `classify`의 시그니처가 바뀌었으나 이 클래스를 아직 실제
  파이프라인에 연결하지 않았으므로 하위 호환 우려는 없다.
- 회귀 테스트 추가/갱신: `InventoryProjectionTest`에 reserved>onHand가 이제
  예외 대신 음수 `currentAvailable`+`isInputInvalid()`를 만듦을 확인하는 테스트,
  합산 overflow 2건(receiver측/donor측) 거부 테스트. `InventoryExceptionClassificationTest`
  를 confidence 기반으로 재작성하고, OOS 등 품질 플래그로 `LOW`가 된
  `STABLE_REPEAT`이 `REVIEW_REQUIRED`가 됨을 확인하는 테스트와 reserved>onHand가
  `NON_ACTIONABLE`이 됨을 확인하는 테스트를 추가했다.
- 검증: `.\gradlew.bat build --rerun-tasks`(DB_URL 없음) — compile/jar/check 전부
  통과, 비-skip 테스트 100개 전부 통과(기존 99 + 신규 1), 0 failures/errors,
  Oracle IT 2개는 기존과 동일하게 정상 skip. Entity/Migration/Oracle 코드를
  건드리지 않아 Oracle 통합 검증은 미실행/해당 없음이다.

## 수요율 계산 correctness finding 수정 (2026-08-26)

`DemandRateCalculation.calculate`가 `stats.spikeEvidenceDate()`를 신호 유형과
무관하게 항상 제외하던 결함을 수정했다. 3절은 `KNOWN_EVENT`를 `UNEXPLAINED_SPIKE`
보다 먼저 판정하므로, 관련 이벤트가 우선해 최종 신호가 `KNOWN_EVENT`가 되면
그 날짜는 실제로 `UNEXPLAINED_SPIKE` 근거일이 아니며 제외 대상이 아니다.

- `calculate(...)`에 이미 결정된 `DemandSignalType signalType` 파라미터를 추가했다.
  `excludeSpikeEvidenceDay = (signalType == UNEXPLAINED_SPIKE)`일 때만
  `stats.spikeEvidenceDate()`를 제외한다. 관련 이벤트 기간 제외는 신호 유형과
  무관하게 그대로 유지한다(어느 신호가 이겼든 이벤트 기간은 항상 제외).
- Public API 변경: `calculate`의 시그니처가 바뀌었으나 이 클래스를 아직 실제
  파이프라인(Batch/API)에 연결하지 않았으므로 하위 호환 우려는 없다.
- 회귀 테스트 추가: `DemandRateCalculationTest`에
  `knownEventSignalDoesNotExcludeAStatisticallySpikeShapedDayFromTheBaseline` —
  GS-03과 동일한 급증 패턴(2026-09-20에 20개)에 무관한 미래 이벤트를 추가해
  최종 신호를 `KNOWN_EVENT`로 가정했을 때는 그 날짜(주간 수요율 20/7=2.857142857143)가
  포함되고, `UNEXPLAINED_SPIKE`로 가정했을 때는 제외됨(0)을 같은 통계 객체로
  직접 대조 검증했다. 기존 5개 테스트도 실제 해당 GS의 신호 유형
  (`STABLE_REPEAT`/`KNOWN_EVENT`/`UNEXPLAINED_SPIKE`/`INTERMITTENT`/`VARIABLE`)을
  명시적으로 전달하도록 갱신했다.
- 검증: `.\gradlew.bat build --rerun-tasks`(DB_URL 없음) — compile/jar/check 전부
  통과, 비-skip 테스트 79개 전부 통과(기존 78 + 신규 1), 0 failures/errors,
  Oracle IT 2개는 기존과 동일하게 정상 skip. Entity/Migration/Oracle 코드를
  건드리지 않아 Oracle 통합 검증은 미실행/해당 없음이다.

## Codex correctness finding 2건 수정 (2026-08-26)

- `DemandSignalClassification.classify`: 관련 이벤트 조회와 `incompleteEventData`
  계산을 `DATA_INSUFFICIENT` 조기 반환보다 먼저 수행하도록 재배치했다. 품질
  플래그는 주 신호와 별도로 저장되므로(3절), `DATA_INSUFFICIENT` 행도 실제로
  존재하는 관련 이벤트·완전성 정보를 그대로 보존한다(이전에는 조기 반환 시
  `null`/`false`로 버려졌음).
- `PlanHorizon.of(...)`: V6의 `ck_sp_policy_values`(target_coverage_days >= 0)와
  `ck_sp_route_values`(lead_time_days >= 0)와 동일하게 음수 `receiverTargetCoverageDays`와
  `activeRouteLeadTimeDays`의 음수/`null` 원소를 거부하도록 검증을 추가했다.
- Public API/DB 변경 없음.
- 회귀 테스트 추가: `DemandSignalClassificationTest`에 관측 가능일 부족
  (`observableDayCount<14`) 상황에서도 `incompleteEventData`가 보존되는지 확인하는
  테스트 1개, 기존 `insufficientLaunchDaysIsDataInsufficientRegardlessOfOtherInputs`
  테스트에 `relevantEvent`/`incompleteEventData` 보존 검증 추가. `PlanHorizonTest`에
  음수 target coverage(경로 있음/없음 둘 다), 음수 lead time, `null` lead time
  원소를 거부하는 테스트 4개 추가.
- 검증: `.\gradlew.bat build --rerun-tasks`(DB_URL 없음) — compile/jar/check 전부
  통과, 비-skip 테스트 71개 전부 통과(기존 66 + 신규 5), 0 failures/errors,
  Oracle IT 2개는 기존과 동일하게 정상 skip. Entity/Migration/Oracle 코드를
  건드리지 않아 Oracle 통합 검증은 미실행/해당 없음이다.

## Codex 재리뷰 결함 수정 (2026-08-26)

Codex 재리뷰 finding 2건을 수정했다: (1) 날짜가 맞지 않는 snapshot이
`oosCensored()`로 합쳐져 실제 품절이 아닌데 `OOS_CENSORED` 플래그가 생기는 문제,
(2) explicit OOS이면서 양수 판매인 행이 분포에서는 제외되지만 `totalWindowSales`에는
포함되어 spike 점유율을 왜곡하는 반쪽 검열 문제.

- `DailyDemandObservation`에 두 상태를 명확히 분리했다: `stockedOut()`(명시적
  플래그 또는 가용재고 0), `invalidSnapshotReference()`(스냅샷 기준일 불일치).
  `oosCensored()` = `snapshotReferenceValid() && stockedOut()`(진짜 품절만),
  `observable()` = `snapshotReferenceValid() && !stockedOut()`.
  기준시각이 안 맞는 날은 `oosCensored()`도 `observable()`도 아닌 별도 상태다.
- 새 invariant를 생성자에서 강제한다: `stockedOut()`이면 `soldQuantity`는 반드시
  0이어야 한다(생성 시점에 거부). 이 덕분에 진짜 OOS-censored 일자는 항상 판매
  0이라 `totalWindowSales`를 절대 왜곡할 수 없다.
- `DemandObservationStatistics`에 `invalidSnapshotDayCount` 필드를 추가하고,
  집계 루프를 observable/실제 oosCensored/invalidSnapshot 세 갈래로 나눴다.
  `totalWindowSales`는 이제 invalidSnapshot 일자의 판매량을 제외한다(진짜
  oosCensored 일자는 생성자 invariant 덕분에 항상 0이라 포함해도 무방).
- Public API/DB 변경 없음.
- 회귀 테스트 갱신: `DailyDemandObservationTest`에 explicit-OOS+양수판매 거부,
  가용재고0+양수판매 거부, 기준시각 불일치가 `invalidSnapshotReference()`이지
  `oosCensored()`가 아님을 확인하는 테스트 추가(총 13개). `DemandObservationStatisticsTest`
  의 두 통계 테스트를 새 invariant/분리에 맞게 재작성해 `oosCensored` 플래그가
  기준시각 불일치로는 켜지지 않고, `totalWindowSales`가 무효 일자의 판매량을
  포함하지 않음을 정확한 수치로 검증(총 7개).
- 검증: `.\gradlew.bat build --rerun-tasks`(DB_URL 없음) — compile/jar/check 전부
  통과, 비-skip 테스트 46개 전부 통과(기존 44 + 신규 2), 0 failures/errors,
  Oracle IT 2개는 기존과 동일하게 정상 skip. Entity/Migration/Oracle 코드를
  건드리지 않아 Oracle 통합 검증은 미실행/해당 없음이다.

## Codex 리뷰 결함 수정 (2026-08-26)

Codex 리뷰 finding(`DailyDemandObservation`이 V6의 `snapshot_at`과
`out_of_stock_flag`를 표현하지 않고 `onHand - reserved >= 1`만으로 observable을
결정)을 수정했다.

- `DailyDemandObservation`에 `outOfStockFlag`(명시적 입력)와 `snapshotAt`
  필드를 추가했다. `observable()`은 이제
  `snapshotReferenceValid() && !outOfStockFlag && availableQuantity() >= 1`로
  판정한다. `snapshotReferenceValid()`는 `snapshotAt`의 날짜가 그날의 관측일과
  같은지 확인한다.
- 기존 단순 호출부(수량만으로 충분한 테스트)를 위해 정적 팩토리
  `DailyDemandObservation.of(date, onHand, reserved, sold, txnCount, maxTxnQty)`를
  추가했다. 이 팩토리는 V6 backfill 방식대로 `outOfStockFlag`를 수량에서
  유도하고 `snapshotAt`을 해당 날짜로 맞춘다. 명시적 OOS 플래그나 기준시각
  불일치를 표현하려면 canonical 생성자를 직접 호출한다.
- Public API/DB 변경 없음 — 순수 값 객체와 테스트만 수정했다.
- 회귀 테스트 추가: `DailyDemandObservationTest`에 명시적 OOS 플래그가 양수
  재고를 무시시키는 경우, snapshot 날짜 불일치, null snapshotAt 거부 테스트 3개;
  `DemandObservationStatisticsTest`에 동일한 두 입력이 observable-day 통계
  (`observableDayCount`, `oosCensoredDayCount`, `totalWindowSales`)에서 실제로
  제외되는지 확인하는 테스트 2개.
- 검증: `.\gradlew.bat test --rerun-tasks`(DB_URL 없음) — 44개 비-skip 테스트
  전부 통과(기존 39 + 신규 5), 0 failures/errors, Oracle IT 2개는 기존과 동일하게
  정상 skip. Entity/Migration/Oracle 코드를 건드리지 않아 Oracle 통합 검증은
  미실행/해당 없음이다.

## Phase 2 progress (2026-08-25)

`com.bapegg.stockpilot.demand` 패키지에 순서 1~2를 구현했다: `DailyDemandObservation`
(하루 원자료), `DemandObservationWindow`(28일 고정 창), `DemandObservationStatistics`
(observableDayCount, `OOS_CENSORED`, activeWeekCount, salesDayRatio, 주간 CV,
median/MAD, 급증·단일 대량거래 플래그). GS-01/03/04의 관측 통계 절반을 실제 Seed
숫자로 정확히 재현하는 단위 테스트 18개가 통과했다. 자세한 내용은
`knowledge/state/implemented-state.md`의 "MVP-2 Phase 2 progress"를 따른다.

아직 구현하지 않음: 신호 분류 최종 판정(`DATA_INSUFFICIENT`~`VARIABLE`, 이벤트·계획
구간 필요), confidence, low/base/high 수요율, 재고 예상값과 예외, 후보·경로 규칙,
시나리오 수량, 승인 검증. GS-02·GS-05·GS-06은 아직 Java로 재현되지 않았다.

## Phase 2 progress (2026-08-26, 신호 분류)

`com.bapegg.stockpilot.demand`에 3~4절 앞부분을 구현했다: `PlanHorizon`(계획 구간),
`DemandEvent`(SP_DEMAND_EVENT 입력, `hasCompleteUplift()`), `DemandSignalType`/
`DemandConfidence`(enum), `DemandSignalClassification.classify(...)`(3절 순서
1~6과 4절 confidence 표를 그대로 구현하는 순수 함수). GS-01(`STABLE_REPEAT`/`HIGH`)과
GS-02(`KNOWN_EVENT`/`MEDIUM`, 완전한 uplift)를 실제 Seed 수치로 재현했고,
`DATA_INSUFFICIENT`/`UNEXPLAINED_SPIKE`/`INTERMITTENT`/`VARIABLE` 분기와 event
store/sku 불일치, `INCOMPLETE_EVENT_DATA`로 인한 confidence 강등을 손으로 계산한
합성 입력으로 회귀 테스트했다. 자세한 내용은 `implemented-state.md`를 따른다.

이번 증분의 범위 결정: confidence 계산에서 `STALE_INVENTORY`/`MISSING_INBOUND`
품질 플래그는 아직 입력이 없어 반영하지 않았다(이 두 플래그를 나중에 추가할 때는
기존 "품질 플래그 하나라도 있으면 confidence LOW" 규칙에 합류시켜야 한다).
low/base/high 수요율(5절)은 별도 클래스로 남겨뒀다.

## Phase 2 progress (2026-08-26, low/base/high 수요율)

`com.bapegg.stockpilot.demand`에 `DemandRateCalculation`을 추가했다: 요일별
관측 가능일 중 `OOS_CENSORED`(비관측), `UNEXPLAINED_SPIKE` 근거일, 관련 이벤트
기간의 판매일을 제외한 주간 수요율을 계산하고, 유효 주간 수요율이 3개 미만이면
`reviewRequired=true`(low/base/high 없음)를 반환한다. 유효 주간 수요율이 3개
이상이면 문서의 선형보간 백분위 공식으로 low(0.25)/base(0.50)/high(0.75)를
scale 12 HALF_UP으로 고정한다.

`DemandEvent.upliftFor(scenarioWindowStart, scenarioWindowEnd)`를 별도로 추가해
uplift 곱셈의 적용 여부만 결정하고(완전한 uplift + 해당 구간과 겹침), 실제
곱셈·반올림은 `DemandRateCalculation.applyUplift(rate, factor)`가 담당한다.
**범위 결정**: uplift는 시나리오별 도착·목표 커버리지 구간이 있어야 적용 여부를
알 수 있는데, 그 구간은 아직 미구현인 5단계(후보·경로)가 골라야 하는 값이라
이번 증분에서는 계획 구간(PlanHorizon)으로 대신 곱하지 않았다. `applyUplift`/
`upliftFor`는 5단계가 실제 시나리오 구간을 계산해 호출할 준비만 마쳐뒀다.

GS-01(주 2개 고정 → low=base=high=2.0), GS-02(이벤트 겹침일이 주간에서 제외되지만
결과 수요율은 우연히 동일), GS-03(급증 근거일 제외), GS-04(유효 주간 2개뿐 →
`REVIEW_REQUIRED`)를 실제 Seed 수치로 재현했고, 비균일 주간 수요율(1/2/3/4)로
문서의 보간 공식(low=1.75, base=2.5, high=3.25)을 정확히 검증했다. 자세한 내용은
`implemented-state.md`를 따른다.

## Phase 2 progress (2026-08-26, 재고 예상값과 예외)

`com.bapegg.stockpilot.demand`에 4/6/9절을 구현했다: `InventoryProjection`
(currentAvailable, projectedReceiverBeforeDemand, projectedDonorAtDispatch,
receiverAtArrivalWithoutNewTransfer, receiverTargetQuantity, donorProtectedQuantity —
모두 ceil 적용), `InventoryExceptionType`/`InventorySeverity`(enum),
`InventoryExceptionClassification.classify(...)`(NON_ACTIONABLE → 자동 수량 불가
신호 REVIEW_REQUIRED → STOCKOUT_RISK/CRITICAL → STOCKOUT_RISK(심각도 미정) →
OVERSTOCK → NORMAL 순서로 판정).

GS-05를 입고 반영 전/후로 대조하는 테스트로 재현했다: 입고 없이는 CRITICAL/
STOCKOUT_RISK(도착 전 BASE 예상재고 음수)였다가, 실제 50개 확정 입고를 반영하면
STOCKOUT_RISK에서 완전히 벗어난다. 다만 이 SKU의 정확한 데모 정책 수치(보존
14일 × 3.0 + 진열1 + 안전재고2 = 45 보호량, 입고 반영 후 52 가용)로는 7개의
이동 가능 잉여가 남아 결과가 NORMAL이 아니라 OVERSTOCK으로 계산된다 — 수치를
인위적으로 조정하지 않고 그대로 테스트·기록했다. `INBOUND_ALREADY_COVERS`
reason code 자체는 5단계(후보 규칙)의 책임이라 이번 범위에 포함하지 않았다.

**범위 결정**: 9절의 `HIGH` 심각도("목표 커버리지보다 부족하고 실행 가능한 조치가
있음")는 공급 후보 존재 여부를 알아야 하는데 그건 아직 미구현인 5단계의 일이다.
CRITICAL이 아닌 STOCKOUT_RISK는 severity를 `null`로 두고 5단계가 채우도록
남겨뒀다(임의로 HIGH를 단정하지 않음). 자세한 내용은 `implemented-state.md`를
따른다.

## Phase 2 progress (2026-08-26, 이동 후보와 탈락 사유)

`com.bapegg.stockpilot.demand`에 7절을 구현했다: `TransferCandidateRejectionReason`
(우선순위 선언 순서의 enum), `TransferRoute`(V6 `ck_sp_route_values`와 동일한 검증),
`TransferCandidateEvaluation.evaluate(...)`(8개 사유를 모두 독립적으로 판정해
`EnumSet`에 저장 — 대표 사유만 고르고 나머지를 버리지 않음).

GS-06(수요 매장 on_hand 2·기준수요율 3.0, 공급 매장 DONOR-B on_hand 90, 경로
active·override 없음·리드타임 10일)을 실제 Seed 수치로 재현해 `OWNER_MISMATCH`와
`LEAD_TIME_TOO_LONG`이 **동시에** 걸림을 확인했다(대표 사유는 우선순위상
`OWNER_MISMATCH`). 이는 "탈락한 모든 조건을 저장한다"는 7절 규칙을 실제 다중
사유 사례로 검증한다.

**범위 결정/해석**: `DISPLAY_MINIMUM_VIOLATION`은 7절 조건 7("최소 이동수량,
포장 배수, 경로 최대수량과 도착 매장 최대 수용량을 만족한다")을 "공급·경로
최대·수용량 여유로 만들 수 있는 최대 배수 단위 출하량이 경로 최소수량에
못 미치는가"로 해석해 구현했다. 문서에 이 reason code의 정확한 범위를 더
구체화하는 설명이 없어 이 매핑은 리뷰 확인이 필요한 해석 결정임을 명시한다.
`PENDING_TRANSFER_CONFLICT`는 동일 donor-receiver-SKU 경로에 이미 진행 중인
이동이 있는지를 나타내는 단순 boolean 입력으로 받았다(수량 로직과 독립).
자세한 내용은 `implemented-state.md`를 따른다.

## Next verifiable action

먼저 후보·경로 리뷰 finding 3건을 수정하고 Codex 재리뷰를 받는다. 그 다음
`business-rules.md` 8절의 시나리오 수량(`NO_ACTION/CONSERVATIVE/BASE/AGGRESSIVE`)을
순수 함수로 구현한다. `donorProtectedQuantity`(이미 구현됨), `receiverTargetQuantity`
(이미 구현됨)를 재사용해 `rawQuantity = min(receiverNeed, donorTransferableQuantity,
routeMaximumQuantity, receiverCapacityRemaining)`과 포장 배수 내림 처리,
`scenarioQuantity < routeMinimumQuantity`이면 수량 0 + 제약 경고 처리를 구현한다.
`VARIABLE`에는 대표 추천 수량을 만들지 않는 분기를 명시적으로 검증한다. GS-01
(3개 자동 시나리오, 양쪽 매장 보호)을 실제 Seed 수치로 재현하는 테스트부터
추가한다.
