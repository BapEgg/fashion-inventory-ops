# Current Task

Status: MVP-2 `MANUAL` quantity-test REST specification approved for implementation
Current role: Claude implementation
Last updated: 2026-08-28

## Goal

기존 `POST /api/rebalancing-simulations`에 MVP-2의 side-effect-free `MANUAL` 수량 시험을
연결한다. 사용자가 추천 수량과 다른 이동 수량을 입력했을 때 실제 승인 전에 다음을 확인하는
Backend API다.

- 현재 근거에서도 후보가 실행 가능한지
- 최소 수량, 포장 배수, donor 이동 가능량, 경로 최대량, receiver 수용량 중 무엇을
  위반했는지
- 입력 수량을 자동 변경하지 않은 상태에서 적용 가능한 하향 제안수량이 얼마인지
- 실행 가능할 경우 양쪽 매장의 이동 전후 재고, 커버리지와 위험이 어떻게 바뀌는지

이번 slice는 공개 REST 연결과 그 선행조건인 current-basis parity까지 포함한다. React 화면,
실제 결정 저장 REST, 결정 조회, AI 설명은 범위 밖이다.

## Accepted baseline

- 기존 MVP-1 simulation은 `recommendationId/requestedQuantity` 두 필드와
  `RebalanceSimulationResponse` 성공 shape를 사용한다.
- `ManualQuantityTestCommand` → `ManualQuantityTestExecutor` →
  `ManualQuantityEvaluation`의 순수 계산/application 계약은 accepted다.
- `ManualQuantityTestExecutor`는 recommendation 다음 donor snapshot 순으로
  `PESSIMISTIC_WRITE` lock을 얻지만 decision, approval basis, transfer draft와 inventory를
  쓰지 않는다.
- 승인과 `MANUAL`은 `CurrentApprovalBasisLoader`를 공유한다.
- V10/V11의 approval error catalog와 V14/V15의 catalog-backed RFC 9457
  ProblemDetail 경계가 이미 존재한다.
- 구현 기준선은 [`implemented-state.md`](implemented-state.md)다.

## 1. 선행 수정: 승인·MANUAL current-basis parity

현재 `CurrentApprovalBasisLoader`는 정책 행이 없으면 stale로 거부하고 receiver metric의
baseline BASE를 그대로 쓴다. 이는 `business-rules.md` 10절의 확정 계약과 충돌한다. REST로
노출하기 전에 승인과 `MANUAL` 양쪽에 공유되는 loader에서 다음을 함께 고친다.

### 1.1 정책 fallback

- receiver 또는 donor의 같은 `(storeId, skuId, inputSnapshotVersion)` 정책 행이 있으면
  그 행을 사용한다.
- 행이 없으면 `DemandAnalysisRules`의 아래 승인된 `ASSUMPTION` 기본값을 사용한다.
  누락을 `STALE_RECOMMENDATION`으로 바꾸지 않는다.
  - `displayMinimum=1`
  - `safetyStock=2`
  - `maximumCapacity=100`
  - `targetCoverageDays=7`
  - `retainedDays=14`
- approval package가 Batch package 타입에 의존하지 않게 한다. 작은 immutable effective
  policy 타입을 공용 pure domain에 두거나 loader 내부 값 객체로 두되, 숫자의 유일한 원본은
  `DemandAnalysisRules` 상수여야 한다.
- recommendation의 route 행이 없거나 비활성/identity/version 불일치인 경우는 기존처럼
  fallback하지 않고 `STALE_RECOMMENDATION`이다.

### 1.2 대표 이벤트와 effective receiver BASE

loader는 같은 입력 버전에서 아래 순서로 current receiver BASE를 만든다.

1. receiver의 모든 demand event를
   `SpDemandEventRepository.findByStoreIdAndSkuIdAndInputSnapshotVersion`으로 다시 읽는다.
2. 같은 receiver와 입력 버전의 route들을 읽고 활성 route의 전체 lead time 목록을 만든다.
   recommendation route 하나만으로 plan horizon을 줄이지 않는다.
3. `observationStart=analysisDate-28`, `observationEnd=analysisDate-1`과
   `PlanHorizon.of(analysisDate, effectiveReceiverPolicy.targetCoverageDays,
   activeInboundRouteLeadTimes)`를 사용한다.
4. `RepresentativeEventSelection.selectRelevant`로 두 구간과 겹치는 event를
   `(startDate,eventCode)` 순으로 고르고 첫 행을 representative event로 사용한다.
5. `EffectiveReceiverBaseRate.calculate`에 저장된 receiver metric의
   `primaryDemandSignalType`, representative event, recommendation route와 receiver 목표
   커버리지를 전달한다.

저장 signal이 `KNOWN_EVENT`이고 대표 event가 recommendation route의
`도착일~목표 커버리지 종료일`과 겹치며 uplift 세 값이 완전할 때만 baseline BASE에
`upliftBase`를 적용한다. 계산은 기존 `DemandRateCalculation.applyUplift`의 scale 12
`HALF_UP`을 그대로 사용한다. signal을 재분류하거나 수요율을 다시 산출하지 않는다.

이 effective receiver BASE가 다음 모두에 전달돼야 한다.

- `ApprovalBasisRecalculation`의 현재 추천 BASE 수량
- `ManualQuantityEvaluation`의 추천/실행 가능 수량과 receiver 전후 coverage/risk
- 실제 approval 재검증과 저장되는 approval basis

donor 보호량은 계속 저장된 donor baseline HIGH만 사용한다. event uplift를 donor HIGH에
적용하지 않는다. nullable donor BASE는 기존처럼 승인 가능성을 막지 않고 `MANUAL`
donor coverage만 `null`로 만든다.

event/route/policy 원본이 DB constraint 밖의 모순된 값이라 pure 객체를 만들 수 없으면 raw
예외를 노출하지 않고 `STALE_RECOMMENDATION`으로 정규화한다.

## 2. 요청 계약과 MVP-1 호환 분기

`RebalanceSimulationRequest`는 기존 두 필드를 유지하고 아래 version tuple을 additive로
받는다.

| Field | Legacy | MVP-2 MANUAL |
|---|---|---|
| `recommendationId` | required `Long` | required positive `Long` |
| `requestedQuantity` | required, minimum 1 `Integer` | required, positive integer quantity |
| `analysisRunId` | absent | required positive `Long` |
| `inputSnapshotVersion` | absent | required normalized nonblank, max 64 |
| `ruleVersion` | absent | required normalized nonblank, max 32 |
| `candidateVersion` | absent | required positive `Integer` |

분기는 다음처럼 결정한다.

- version tuple 네 필드가 모두 없으면 기존 MVP-1 service/response 경로다.
- 하나라도 있으면 네 필드가 모두 있어야 하며 MVP-2 `ManualQuantityTestCommand`로 변환한다.
- 일부만 보낸 요청은 `INVALID_REQUEST` 400이다. 누락 값을 recommendation에서 임의로
  채우지 않는다.
- version tuple이 없는 요청이 실제로 `ruleVersion=MVP-1`이 아닌 recommendation을
  가리키면 legacy 계산으로 우회시키지 않고 `INVALID_REQUEST` 400으로 거부한다.
- version tuple을 보낸 요청의 값이 저장 recommendation/run과 다르거나 같은 rule version의
  더 최신 COMPLETED run이 있으면 `STALE_RECOMMENDATION` 409다.
- 문자열은 accepted command 계약과 같이 NFC normalize 후 `strip()`한다. 입력 수량은
  자동 반올림, clamp 또는 추천 수량 치환을 하지 않는다.

기존 MVP-1 성공 요청의 JSON body와 `RebalanceSimulationResponse` 필드, 계산 경로와 HTTP
200을 바꾸지 않는다. MVP-1 Batch/decision 코드는 이번 slice에서 수정하지 않는다.

## 3. MVP-2 성공 응답

새 DTO 이름은 `Mvp2RebalanceSimulationResponse`를 기준으로 하며
`ManualQuantityTestResult`를 일대일로 mapping한다.

```json
{
  "recommendationId": 101,
  "analysisRunId": 20,
  "inputSnapshotVersion": "MVP-2-GS-V1",
  "ruleVersion": "MVP-2",
  "candidateVersion": 1,
  "requestedQuantity": 12,
  "feasible": true,
  "reasonRequired": true,
  "recommendedBaseQuantity": 10,
  "maximumFeasibleQuantity": 30,
  "suggestedQuantity": 12,
  "violations": [],
  "candidateRejectionReasons": [],
  "routeMinimumQuantity": 1,
  "packageMultiple": 1,
  "routeMaximumQuantity": 50,
  "donorTransferableQuantity": 30,
  "receiverCapacityRemaining": 80,
  "projection": {},
  "approvalRevalidationRequired": true,
  "assumption": {
    "type": "ASSUMPTION",
    "notice": "수량 시험 결과는 MVP-2 데모 가정이며 실제 승인 시 최신 근거로 다시 검증합니다."
  }
}
```

`projection`은 실행 가능한 수량일 때만 아래 필드를 가지며 실행 불가능하면 `null`이다.

- receiver: `receiverBeforeAvailable`, `receiverAfterAvailable`,
  `receiverBeforeCoverageDays`, `receiverAfterCoverageDays`, `receiverRiskCode`
- donor: `donorBeforeAvailable`, `donorAfterAvailable`, `donorBeforeCoverageDays`,
  `donorAfterCoverageDays`, `donorRiskCode`
- timing: `leadTimeDays`, `expectedArrivalDate`
- evidence: `receiverInboundArrivingBeforeTransfer`, `receiverOpenTransferInbound`,
  `receiverOpenTransferOutbound`, `donorInboundArrivingBeforeDispatch`,
  `donorOpenTransferOutbound`, `donorAlreadyApprovedDraftQuantity`

추가 계약은 다음과 같다.

- 제약 위반은 정상적인 시험 결과이므로 HTTP 200과 `feasible=false`로 반환한다.
- `violations`는 `ManualQuantityViolation` 선언 순서로 모든 해당 사유를 반환한다.
- `candidateRejectionReasons`도 순수 validator의 확정 순서를 보존한다.
- 배열은 항상 non-null이다.
- `suggestedQuantity`는 입력보다 크지 않은, 제약 안의 package multiple 하향 제안이며 가능한
  값이 없으면 0이다. 서버가 이를 실제 입력/승인 수량으로 적용하지 않는다.
- `reasonRequired`는 current recommended BASE와 입력 수량이 다를 때 true다. 이 API는
  reason을 받거나 저장하지 않는다.
- `approvalRevalidationRequired`는 항상 true다. 결과 token이나 lock을 승인 API로 넘겨
  재검증을 생략하는 기능은 만들지 않는다.
- `BigDecimal`은 JSON number로 반환하며 API layer에서 표시용 반올림을 추가하지 않는다.

## 4. transaction과 persistence 계약

- `ManualQuantityTestExecutor.test`의 기존 `@Transactional` 경계를 유지한다.
- lock 순서는 recommendation → donor inventory snapshot으로 고정한다. 역순 경로를
  추가하지 않는다.
- 최신 decision 상태가 `APPROVED/REJECTED/EXPIRED`면 시험도
  `DECISION_ALREADY_TERMINAL`로 거부한다.
- 이 endpoint는 `Idempotency-Key`, actor, reason, policy exception을 받지 않는다.
- 성공, infeasible 200, stale/terminal/error 모든 경로에서 다음을 새로 쓰거나 변경하지 않는다.
  - `sp_rebalance_decision`
  - `sp_approval_basis`
  - `sp_transfer_draft`
  - inventory snapshot/metric, recommendation, input evidence
- API 응답 후 실제 approval은 recommendation/donor lock부터 모든 근거를 다시 읽고 계산한다.

## 5. 오류와 ProblemDetail 계약

`AnalysisApiExceptionHandler`의 accepted RFC 9457 응답 생성 방식을 재사용한다. 최소 변경은
handler scope에 `RebalanceSimulationController`를 추가하고
`ApprovalTransactionException.code()`를 `ErrorCatalogService`에 전달하는 전용 handler를
추가하는 것이다. 별도 advice를 만들더라도 ProblemDetail 생성 코드를 복제하지 말고 공용
responder로 추출한다.

| 상황 | code | HTTP | retryable |
|---|---|---:|---|
| Bean request shape/JSON 변환 실패 | `VALIDATION_ERROR` | 400 | N |
| 부분 version tuple/command cross-field 오류 | `INVALID_REQUEST` | 400 | N |
| recommendation 없음 | `RECOMMENDATION_NOT_FOUND` | 404 | N |
| version/current-basis 불일치 | `STALE_RECOMMENDATION` | 409 | N |
| 이미 terminal decision | `DECISION_ALREADY_TERMINAL` | 409 | N |
| recommendation/donor lock timeout | `APPROVAL_LOCK_TIMEOUT` | 503 | Y |
| DB 연결 불가 | `PERSISTENCE_UNAVAILABLE` | 503 | Y |
| 분류되지 않은 저장소/내부 오류 | `INTERNAL_SERVER_ERROR` | 500 | N |

- V10/V11에 필요한 catalog code가 이미 있으므로 이번 slice용 Flyway migration을 만들지
  않는다.
- title/detail/status/retryable은 exception message가 아니라 active catalog row 또는 accepted
  Java fallback에서 가져온다.
- `type`, `code`는 catalog lookup 결과의 effective code를 사용한다.
- `instance`, request id header/body, UTC timestamp와 validation-only `fieldErrors` 규칙은 기존
  handler와 동일하다.
- recommendation id, raw SQL/constraint/stack message와 rejected value를 response detail에
  넣지 않는다.
- 기존 legacy simulation service의 `ResponseStatusException`은 이 controller가 새 advice
  scope에 들어갈 때 잘못된 code로 바뀌지 않게 `ApiException`의
  `RECOMMENDATION_NOT_FOUND/INVALID_REQUEST`로 정규화한다. legacy 성공 shape는 그대로다.

## 6. 구현 경계

### 변경 대상

- `CurrentApprovalBasisLoader`와 필요한 route/event repository query
- effective policy/event BASE parity 단위·Oracle 통합 테스트
- `RebalanceSimulationRequest`, `RebalanceSimulationController`와 새 MVP-2 response mapper
- legacy `RebalanceSimulationService`의 non-MVP-1 우회 방지와 안정 error code
- catalog-backed ProblemDetail handler의 simulation scope/approval exception mapping
- API unit/MVC/Oracle integration regression

### 변경하지 않는 대상

- `ManualQuantityEvaluation`의 확정 수량식과 violation 순서
- approval idempotency/append-only 저장 계약과 DB schema
- 저장 automatic scenario(`NO_ACTION/CONSERVATIVE/BASE/AGGRESSIVE`); `MANUAL`을 scenario
  table에 저장하지 않는다.
- React, 승인/결정 REST, AI provider, scheduler, 외부 ERP/WMS/TMS

## 7. 필수 테스트와 완료 조건

### 7.1 Pure/unit/MVC

- tuple 모두 absent → legacy service, tuple 모두 present → manual executor, partial tuple →
  `INVALID_REQUEST`
- legacy MVP-1 성공 JSON은 기존 필드와 값이 동일하며 MVP-2 필드로 감싸지지 않는다.
- non-MVP-1 recommendation을 tuple 없이 호출하면 legacy 계산을 실행하지 않는다.
- feasible/infeasible mapping, non-null arrays, infeasible `projection=null`, assumption notice와
  `approvalRevalidationRequired=true`
- body validation과 각 `ApprovalTransactionException` code가 catalog-backed ProblemDetail로
  mapping되고 effective fallback code/fieldErrors 억제 규칙이 유지된다.
- 기존 `AnalysisApiExceptionHandlerTest` 전체가 그대로 통과한다.

### 7.2 Current-basis parity Oracle IT

- receiver/donor 정책 행이 각각 없을 때 accepted default를 사용하고 stale로 실패하지 않는다.
- 여러 관련 event를 입력 순서와 다르게 저장해도 `(startDate,eventCode)` 첫 행을 대표로
  고른다.
- all-active-route plan horizon 안의 대표 `KNOWN_EVENT`가 recommendation route 적용 구간과
  겹칠 때 uplifted BASE가 manual `recommendedBaseQuantity`/projection과 실제 approval
  재검증의 저장 basis에 동일하게 반영된다.
- 대표 event가 적용 구간과 겹치지 않거나 uplift가 불완전하면 baseline BASE를 사용한다.
- donor HIGH 보호량은 receiver event 추가 전후 동일하고 nullable donor BASE 회귀도
  유지된다.
- 기존 sibling approved draft, stale tuple, terminal decision, ineligible candidate와 donor
  lock-timeout 테스트가 모두 유지된다.

### 7.3 REST Oracle IT

- complete MVP-2 request의 feasible 200과 모든 projection/evidence 값을 검증한다.
- 한 입력이 둘 이상의 hard constraint를 위반할 때 모든 violation, 하향 suggestion,
  `feasible=false`, `projection=null`, HTTP 200을 검증한다.
- unknown recommendation 404, stale tuple 409, terminal 409와 lock timeout 503의 exact
  ProblemDetail code/status/retryable을 검증한다.
- feasible, infeasible와 실패 응답 전후 decision/basis/draft/inventory row와 값이 변하지
  않았음을 실제 DB 조회로 검증한다.
- 기존 `ApiGoldenScenarioIT`의 MVP-1 simulation과 simulate-then-decide 회귀를 유지한다.

### 7.4 최종 검증

- 관련 pure/unit/MVC target tests
- 관련 Oracle target tests
- DB 없는 Backend 전체 test: DB 의존 테스트만 조건부 skip, failure/error 0
- Oracle Backend 전체 test: skip/failure/error 0
- `git diff --check`

실행하지 않은 테스트를 성공으로 기록하지 않는다. 구현 중 Public API 또는 DB schema를 이
명세와 다르게 바꿔야 하면 임의 진행하지 않고 Codex review로 돌려보낸다.

## Next verifiable action

Claude가 1절 current-basis parity를 먼저 구현·검증한 뒤 2~5절 REST slice를 연결하고, 실제
테스트 명령과 결과를 이 문서에 완료 사실로 기록한다. 그 후 Codex가 독립 검증·리뷰한다.

