# Archived Current Task — Phase 3 foundation acceptance input

Status: Phase 3 Batch foundation — 3건 결함 수정 완료; Codex 재검증 대기
Current role: Codex verification/review
Last updated: 2026-08-27

## Goal

Phase 3의 입력 adapter나 Job orchestration으로 확장하기 전에, 이번 foundation layer에서
발견된 영속 데이터 무결성 결함 3건을 수정하고 Codex 재리뷰로 인계한다. 새 Migration,
REST/React, 승인·`MANUAL` event-aware wiring은 이번 수정 범위가 아니다.

전체 Phase 3 구현 명세는
[`../archive/state/2026-08-27-phase3-foundation-review-current-task.md`](../archive/state/2026-08-27-phase3-foundation-review-current-task.md),
업무 규칙은 [`../business-rules.md`](../business-rules.md), 물리 mapping은
[`../data-model.md`](../data-model.md)의 “Phase 3 Batch 물리 mapping”을 따른다.

## Required fixes

### 1. 예상 부족수량의 narrowing overflow 차단

`SpInventoryMetric`은 `InventoryProjection.receiverTargetQuantity(...)`의 `long` 결과를
`int`로 직접 cast하지 않는다. `expected_shortage_quantity NUMBER(12,0)` 계약에 맞춰 entity와
getter를 `Long`으로 바꾸고 양수 차이를 narrowing 없이 저장하는 안을 권장한다.

완료 증거:

- `Integer.MAX_VALUE`를 넘지만 `NUMBER(12,0)` 범위인 예상 부족수량이 정확히 Oracle
  round-trip한다.
- null BASE와 invalid projection의 기존 null 동작, 기존 MVP-1 mapping은 유지된다.

### 2. ELIGIBLE 후보의 route 불변조건 보장

`createMvp2Candidate(...)`는 `CandidateStatus.ELIGIBLE`일 때 `routeId`가 반드시 non-null임을
생성 시점에 보장한다. 현재 테스트처럼 `ELIGIBLE/RECOMMENDED + null routeId`를 저장하면
`CurrentApprovalBasisLoader`가 언제나 stale로 거부하므로 유효한 후보 상태가 아니다.
`REJECTED/NONE` 후보의 nullable route는 유지할 수 있다.

완료 증거:

- eligible+null route를 factory 단위 테스트에서 거부한다.
- scenario persistence IT는 실제 `SpStoreTransferRoute`를 만들고 그 route id를 가진 eligible
  recommendation으로 실행한다.

### 3. Scenario audit 값을 계산 결과·후보에서 파생

`SpRebalanceScenario`가 별도 인자로 받은 임의의 `expectedArrivalAt`과 `candidateVersion`을
저장하지 않게 한다. 도착시각은 `TransferScenarioResult.expectedArrivalDate()`에
`00:00 Asia/Seoul`을 결합해 만들고, 후보 버전은
`recommendation.getCandidateVersion()`에서 파생한다. 또는 동일성을 엄격히 검증하되,
중복 인자를 제거하는 쪽을 권장한다.

완료 증거:

- 저장된 도착일/offset이 result 계약과 일치한다.
- 저장된 candidate version이 parent recommendation과 일치하며 caller가 불일치를 주입할
  경로가 없다.

## Constraints

- V1~V13 Migration과 기존 MVP-1 생성자/API 동작을 바꾸지 않는다.
- input adapter, 계산 orchestration, transaction coordinator 등 미구현 Phase 3 범위를 이
  수정과 섞지 않는다.
- Codex가 직접 수정한 `RepresentativeEventSelection` Javadoc의 shared full-plan-horizon
  설명을 유지한다. 이는 동작 변경이 아니다.

## Required validation

1. 새 경계/불변조건 단위·Oracle mapping 테스트
2. `RepresentativeEventSelectionTest`, `DemandSignalClassificationTest`,
   `Mvp2BatchEntityPersistenceMappingIT`
3. 실제 Oracle 전체 Backend build와 DB-free 전체 build
4. `git diff --check`

## Completion condition

세 결함과 회귀 테스트가 모두 닫히고 전체 검증 결과를 실제 수치로 기록하면 Claude는
Codex 검증·재리뷰로 인계한다. Foundation이 승인된 뒤에만 archived Phase 3 명세의 입력
adapter부터 이어간다.

## 이번 라운드 수정 완료

### Fix 1 — narrowing overflow 차단

- [SpInventoryMetric.java](../../backend/src/main/java/com/bapegg/stockpilot/analysis/SpInventoryMetric.java):
  `expectedShortageQuantity` 필드·getter를 `Integer` → `Long`으로 변경. 생성자는
  `Math.max(long, long)`의 `long` 결과를 그대로 대입하며 더 이상 `(int)` cast를 하지 않는다.
- 신규 Oracle 테스트
  `expectedShortageQuantityRoundTripsBeyondIntegerRangeWithoutNarrowingOverflow`
  (`Mvp2BatchEntityPersistenceMappingIT`): `leadTimeDays+coverageDays=30000`,
  `baseRate=100000`으로 목표수량을 3,000,000,000까지 올려(`Integer.MAX_VALUE` 초과,
  `NUMBER(12,0)` 범위 내) 정확한 값이 round-trip함을 확인.
- 기존 두 테스트의 `assertEquals(int, Long)` 단정도 함께 수정했다. 필드 타입 변경 후
  `Integer(12).equals(Long)`이 항상 `false`가 되어 조용히 실패했을 assertion을 `.longValue()`
  비교로 고쳤다(회귀 실행 중 직접 발견).

### Fix 2 — ELIGIBLE 후보 route 불변조건

- [SpRebalanceRecommendation.java](../../backend/src/main/java/com/bapegg/stockpilot/rebalance/SpRebalanceRecommendation.java):
  `createMvp2Candidate(...)`가 `candidateStatus == ELIGIBLE && routeId == null`이면
  `IllegalArgumentException`을 던진다. `REJECTED/NONE`은 계속 nullable route를 허용한다.
- 신규 pure 단위 테스트 [SpRebalanceRecommendationTest.java](../../backend/src/test/java/com/bapegg/stockpilot/rebalance/SpRebalanceRecommendationTest.java)
  (Spring/DB 불필요, 3 tests): 거부 케이스, ELIGIBLE+route 정상 케이스, REJECTED+null route
  정상 케이스.
- `Mvp2BatchEntityPersistenceMappingIT`의 두 scenario 매핑 테스트가 이제 실제
  `SpStoreTransferRoute`를 만들고 그 route id로 ELIGIBLE recommendation을 생성한다.

### Fix 3 — Scenario audit 값 파생

- [SpRebalanceScenario.java](../../backend/src/main/java/com/bapegg/stockpilot/rebalance/SpRebalanceScenario.java):
  생성자에서 `expectedArrivalAt`/`candidateVersion` 인자를 제거했다.
  `expected_arrival_at`은 `result.expectedArrivalDate()`에 `00:00 Asia/Seoul`을 결합해,
  `candidate_version`은 `recommendation.getCandidateVersion()`에서 직접 파생한다. caller가
  불일치를 주입할 경로가 없다.
- 두 scenario 매핑 테스트가 새 3-인자 생성자를 사용하도록 갱신됐고, 저장된 값이 각각
  `result.expectedArrivalDate()`/`eligible.getCandidateVersion()`과 일치하는지 단정한다.

### 실제 실행 증거

- `SpRebalanceRecommendationTest` 3/3(신규), `RepresentativeEventSelectionTest` 5/5,
  `DemandSignalClassificationTest` 9/9(둘 다 무수정 — Codex의 Javadoc 수정만 반영된 상태로
  회귀 없음 확인), `Mvp2BatchEntityPersistenceMappingIT` 8/8(Oracle, 기존 7 + 신규 1).
- Oracle 전체 Backend build: **305/305**, skip 0, failures/errors 0.
- DB-free 전체 build: **305 total/246 passed/59 skip**, failures/errors 0.
- `git diff --check`: exit 0 (기존 파일의 LF/CRLF 경고만).
- Migration, REST/React, 입력 adapter/orchestration/승인 parity는 이번 수정 범위 밖이며
  손대지 않았다.

## Next verifiable action

Codex가 세 결함이 실제로 닫혔는지, 새 테스트가 요구된 완료 증거와 정확히 일치하는지,
전체 305개 기준선이 회귀하지 않는지 독립 검증한다. 승인 시 Phase 3 foundation을
accepted로 전환하고 archived 명세의 입력 adapter부터 다음 작업 단위를 지정한다.
