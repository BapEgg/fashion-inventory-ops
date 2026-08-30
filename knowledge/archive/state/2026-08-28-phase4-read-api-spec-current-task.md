# Archived Current Task — Phase 4 inventory-exception read API specification

Status: MVP-2 inventory-exception list/detail REST specification approved for implementation
Current role: Claude implementation
Last updated: 2026-08-28

## Goal

이미 저장된 MVP-2 분석 결과와 같은 입력 버전의 근거를 조회하는 다음 두 API를 구현한다.

- `GET /api/inventory-exceptions`
- `GET /api/inventory-exceptions/{metricId}`

목록은 본사 재고 배분 담당자의 검토 queue이고, 상세는 한 store-SKU 예외의 28일 근거와
donor 후보·탈락 사유·자동 시나리오를 설명한다. 이 slice는 read-only다. 승인, `MANUAL`
수량 시험, React 연결은 다음 순서로 미룬다.

## Accepted baseline

- MVP-2 Batch는 metric, quality flag, candidate, candidate reason, scenario를 한 output
  transaction에서 저장하고 run을 `COMPLETED`로 전환한다.
- `POST /api/analyses`와 `GET /api/analyses/{analysisRunId}` 계약, V14 catalog-backed
  ProblemDetail과 persistence 분류 경계는 accepted 상태다.
- 현재 MVP-1 목록은 optional `analysisDate`를 받아 bare JSON array를 반환하고, 상세는
  `STOCKOUT_RISK/OVERSTOCK` metric만 반환한다.
- 구현 기준선은 [`implemented-state.md`](implemented-state.md)를 따른다.

## 1. Scope와 호환 분기

### 1.1 목록 Legacy 분기

`analysisRunId`와 2절의 신규 parameter를 모두 생략한 요청은 기존 MVP-1 경로다.

```http
GET /api/inventory-exceptions
GET /api/inventory-exceptions?analysisDate=2026-08-28
```

- 성공 body는 현재 `List<InventoryExceptionSummary>` bare array와 기존 필드명을 그대로
  유지한다. pagination envelope로 감싸지 않는다.
- run 선택, actionable 분류, 정렬과 추천 표시도 기존 MVP-1 의미를 바꾸지 않는다.
- 이 slice 때문에 MVP-1 Batch 계산식이나 DTO 필드를 수정하지 않는다.

### 1.2 Run-bound 분기

`analysisRunId`가 있으면 새 run-bound read model을 사용한다.

```http
GET /api/inventory-exceptions?analysisRunId=123&page=0&size=20
```

- `analysisRunId`는 양의 정수이며, 존재하는 `COMPLETED` run이어야 한다.
- 이번 구현 대상은 `ruleVersion=MVP-2`다. 다른 rule version의 run id는
  `VALIDATION_ERROR`와 `analysisRunId/FORMAT` field error로 거부한다.
- `analysisDate`와 `analysisRunId`는 같이 보낼 수 없다. 같이 오면
  `analysisDate/FORBIDDEN` validation error다.
- 신규 filter/page parameter가 하나라도 있는데 `analysisRunId`가 없으면
  `analysisRunId/REQUIRED` validation error다.
- 완료된 run 결과는 append/overwrite하지 않으므로 한 run 안의 pagination은 안정적이다.

### 1.3 상세 호환

- path identity는 기존과 같이 `inventoryMetricId`를 뜻하며 parameter 이름을
  `metricId`로 통일한다.
- MVP-1 metric이면 현재 `InventoryExceptionDetail` 성공 shape를 그대로 반환한다.
- MVP-2 metric이면 4절의 `Mvp2InventoryExceptionDetail`을 반환한다.
- MVP-1은 기존 두 actionable classification만, MVP-2는
  `STOCKOUT_RISK/OVERSTOCK/REVIEW_REQUIRED/NON_ACTIONABLE`을 예외로 본다.
  `NORMAL` 또는 존재하지 않는 id는 같은 `INVENTORY_EXCEPTION_NOT_FOUND`로 응답한다.
- rule version별 response shape 분기는 metric이 속한 run으로 결정한다. query parameter로
  rule version을 다시 받지 않는다.

## 2. Run-bound 목록 요청 계약

| Parameter | Contract |
|---|---|
| `analysisRunId` | required, positive `Long` |
| `exceptionType` | repeatable enum; `STOCKOUT_RISK`, `OVERSTOCK`, `REVIEW_REQUIRED`, `NON_ACTIONABLE`만 허용 |
| `severity` | repeatable enum; `CRITICAL`, `HIGH`, `REVIEW` |
| `signal` | repeatable `DemandSignalType` |
| `confidence` | repeatable `DemandConfidence` |
| `qualityFlag` | repeatable `MetricQualityFlag`; 하나라도 일치하면 포함 |
| `storeId` | exact trimmed match, 1~64 chars |
| `skuId` | exact trimmed match, 1~64 chars |
| `hasExecutableCandidate` | optional boolean |
| `page` | zero-based, default `0`, minimum `0` |
| `size` | default `20`, minimum `1`, maximum `100` |

- 같은 parameter를 반복하면 OR, 서로 다른 filter 종류 사이는 AND다.
- comma-separated enum 묶음은 지원하지 않는다. 반복 query parameter만 사용한다.
- enum은 대문자 code의 exact match다. 한국어 표시는 나중 React가 code를 번역한다.
- `hasExecutableCandidate=true`는 해당 metric이 receiver 또는 donor인 후보 중
  `candidateStatus=ELIGIBLE AND recommendationMode=RECOMMENDED`가 하나 이상이라는 뜻이다.
  `COMPARISON_ONLY`는 실행 가능한 기본 추천으로 세지 않는다.
- 빈 문자열, 길이 초과, enum/boolean/number 변환 실패는 `VALIDATION_ERROR`다.

### 2.1 고정 정렬

사용자 지정 sort는 이번 범위에 추가하지 않는다. 모든 page는 아래 고정 key를 사용한다.

1. severity: `CRITICAL`, `HIGH`, `REVIEW`, `null`
2. 실행 가능한 후보 있음, 없음
3. confidence: `HIGH`, `MEDIUM`, `LOW`, `NONE`, `null`
4. `expectedShortageQuantity` 내림차순, null last
5. `estimatedSalesImpact` 내림차순, null last
6. `storeId`, `skuId`, `inventoryMetricId` 오름차순

`currentSellingPrice`는 같은 input version의 관측 마지막 날(`analysisDate - 1`)에 저장된
`average_selling_price`다. `estimatedSalesImpact`는 저장된 `expectedShortageQuantity`와 이
가격의 곱이며 scale 2 `HALF_UP`인 **화면 정렬 보조값**이다. 이 값은 이익·손실 확률이나
실제 재무 예측이 아니고 수량·신호·상태 계산에 재사용하지 않는다.

## 3. Run-bound 목록 응답

새 응답 record 이름은 `Mvp2InventoryExceptionPage`와
`Mvp2InventoryExceptionListItem`을 기준으로 한다.

```json
{
  "analysisRunId": 123,
  "analysisDate": "2026-09-30",
  "inputSnapshotVersion": "MVP-2-GS-V1",
  "ruleVersion": "MVP-2",
  "completedAt": "2026-08-28T12:34:56+09:00",
  "assumptionType": "ASSUMPTION",
  "assumptionNotice": "MVP-2 데모 규칙이며 실제 기업 정책이 아닙니다.",
  "page": 0,
  "size": 20,
  "totalElements": 7,
  "totalPages": 1,
  "hasPrevious": false,
  "hasNext": false,
  "items": []
}
```

각 `items[]`는 다음 필드를 가진다.

| Group | Fields |
|---|---|
| identity | `inventoryMetricId`, `storeId`, `storeName`, `region`, `skuId`, `productName`, `category`, `color`, `sizeName` |
| legacy compatibility | `classification`, `priority`, `availableQuantity`, `averageDailySales`, `coverageDays` |
| MVP-2 result | `inventoryExceptionType`, `severity`, `primaryDemandSignalType`, `demandConfidence`, `baseDemandRate`, `projectedAvailable`, `expectedShortageQuantity`, `calculationVersion` |
| quality | non-null `qualityFlags[]`, enum declaration order |
| incoming evidence summary | `upcomingConfirmedInboundQuantity`, `nextConfirmedInboundAt` |
| ordering aid | `currentSellingPrice`, `estimatedSalesImpact` |
| candidate summary | `executableCandidateCount`, `comparisonOnlyCandidateCount`, `rejectedCandidateCount`, `hasExecutableCandidate` |

- 목록에서 임의의 후보 하나를 `recommendationId/recommendedQuantity`로 대표시키지 않는다.
  동일 metric에 후보가 여러 개일 수 있으므로 후보 선택과 수량은 상세에서 확인한다.
- `upcomingConfirmedInboundQuantity`는 같은 store-SKU/input version에서
  `CONFIRMED`, 수량·ETA가 모두 있고, ETA가 `analysisDate 00:00 Asia/Seoul` 이후인 원본 행의
  합이다. `nextConfirmedInboundAt`은 그중 가장 빠른 ETA다. 이는 예정 근거 요약이며 특정
  scenario에 실제 반영됐다는 뜻이 아니다. 실제 반영 여부는 scenario의
  `inboundIncluded`만 사용한다.
- scalar 근거가 없으면 `null`, count와 배열은 각각 `0`과 `[]`다.
- `BigDecimal`은 JSON number로 반환하고 API에서 표시용 재반올림하지 않는다.

## 4. MVP-2 상세 응답

`Mvp2InventoryExceptionDetail`은 아래 top-level group을 가진다. 배열은 항상 non-null이다.

### 4.1 run과 identity

- `run`: `analysisRunId`, `analysisDate`, `inputSnapshotVersion`, `ruleVersion`, `completedAt`
- `store`: `storeId`, `storeName`, `region`
- `product`: `skuId`, `productName`, `category`, `color`, `sizeName`
- `assumption`: `type=ASSUMPTION`, 위 notice 문자열

catalog store/product가 MVP-2 결과에서 누락되면 null로 숨기지 말고 내부 데이터 무결성
오류로 처리한다.

### 4.2 metric result

`metric`은 목록의 legacy/MVP-2 result field에 다음 저장 통계를 더한다.

- `observableDayCount`, `activeWeekCount`, `salesDayRatio`
- `maxDailySales`, `medianDailySales`, `madDailySales`, `maxTransactionQuantity`
- `lowDemandRate`, `baseDemandRate`, `highDemandRate`
- `qualityFlags[]`

값은 `SP_INVENTORY_METRIC`과 `SP_METRIC_QUALITY_FLAG`를 그대로 읽는다. 상세 조회 중
신호, confidence, 수요율, projection, shortage, severity를 다시 계산하지 않는다.

### 4.3 current snapshot과 적용 policy

- `currentSnapshot`: `snapshotDate`, `snapshotAt`, `onHandQuantity`, `reservedQuantity`,
  저장 metric의 `availableQuantity`, `outOfStock`, `sourceType`
- `policy`: `source`(`VERSIONED_INPUT` 또는 `DEFAULT_ASSUMPTION`), `displayMinimum`,
  `safetyStock`, `maximumCapacity`, `targetCoverageDays`, `retainedDays`, `assumptionType`

같은 store-SKU/input version의 policy 행이 없을 때만 `DemandAnalysisRules`의 approved
default를 반환하고 `source=DEFAULT_ASSUMPTION`으로 명시한다. 이 조회가 새로운 fallback을
만들어서는 안 된다.

### 4.4 28일 observation evidence

`observationWindow`는 `startDate=analysisDate-28`, `endDate=analysisDate-1`,
`dayCount=28`, 오래된 날짜부터 정렬한 `days[]`를 가진다. 각 day는 다음 원본값이다.

- `date`
- inventory: `onHandQuantity`, `reservedQuantity`, `outOfStock`, `snapshotAt`
- sales: `soldQuantity`, `transactionCount`, `maxTransactionQuantity`,
  `averageSellingPrice`
- `inventorySourceType`, `salesSourceType`

완료된 MVP-2 run은 Batch input contract상 두 행이 매일 정확히 하나씩 있어야 한다.
28개가 아니거나 버전이 다르면 부분 응답을 만들지 않고 `INTERNAL_SERVER_ERROR`로
실패시키며 run/metric id를 server log에 남긴다.

`SpDailySale`에는 V6 물리 컬럼인 `transactionCount`, `maxTransactionQuantity`,
`averageSellingPrice`, `inputSnapshotVersion` read mapping을 추가한다. 기존 write 경로는 없다.

### 4.5 관련 입력 근거

- `demandEvents[]`: 같은 store-SKU/input version의 `eventCode`, `eventType`, `startDate`,
  `endDate`, `upliftLow/Base/High`, `sourceType`, `assumptionType`; `(startDate,eventCode)` 순
- `inboundSchedules[]`: `inboundReference`, `quantity`, `etaAt`, `inboundStatus`,
  `sourceType`; `(etaAt null last,inboundReference)` 순
- `openTransfers[]`: 현재 store가 donor 또는 receiver인 같은 SKU/input version의
  `transferReference`, 상대 방향을 나타내는 `direction`, donor/receiver id, `quantity`,
  `etaAt`, `transferStatus`, `sourceType`; `(etaAt null last,transferReference)` 순

행별 `selectedEvent`, `includedInProjection`을 새로 추론하지 않는다. 저장 scenario의
`inboundIncluded`와 저장 candidate projection이 계산 결과의 유일한 근거다.

### 4.6 candidate, reason, scenario와 최신 결정

`candidatesAsReceiver[]`와 `candidatesAsDonor[]`를 분리한다. 각 candidate는 다음을 가진다.

- `recommendationId`, `direction`, counterpart `storeId/storeName`
- `route`: `routeId`, `active`, `ownerOverride`, `leadTimeDays`, `minimumQuantity`,
  `packageMultiple`, `maximumQuantity`, `assumptionType`
- `candidateStatus`, `candidateVersion`, `recommendationMode`
- `receiverShortageQuantity`, `donorTransferableQuantity`, `recommendedQuantity`
- `projectedReceiverAtArrival`, `projectedDonorAtDispatch`, `receiverCapacityRemaining`,
  `evaluatedAt`
- `rejectionReasons[]`: `reasonCode`, `reasonOrder`; `reasonOrder` 오름차순
- `scenarios[]`: `NO_ACTION`, `CONSERVATIVE`, `BASE`, `AGGRESSIVE` 순
- nullable `latestDecision`: `decisionSequence`, `decisionStatus`, `selectedQuantity`,
  `reasonCode`, `reason`, `actorLabel`, `decidedAt`

scenario는 `scenarioId`, `scenarioType`, `demandRate`, `scenarioQuantity`,
`packageMultiple`, 양쪽 `before/afterAvailable`, `before/afterCoverage`, `riskCode`,
`leadTimeDays`, `expectedArrivalAt`, `inboundIncluded`, `warningSummary`,
`candidateVersion`, `createdAt`을 반환한다. rejected 후보의 scenario는 `[]`다.
`MANUAL`은 저장 scenario가 아니므로 이 배열에 절대 섞지 않는다.

후보 정렬은 `ELIGIBLE` 우선, 그 안에서 `RECOMMENDED`, `COMPARISON_ONLY`, `NONE`, 이후
counterpart `storeId`, `recommendationId` 순이다. 후보·reason·scenario·latest decision은
bulk query로 읽고 candidate loop 안에서 repository를 호출하지 않는다.

### 4.7 적용 rule threshold

`ruleAssumptions`에는 아래 `DemandAnalysisRules` 상수의 실제 값을 반환한다.

- 관측/충분성: `observationWindowDays`, `minimumObservableDays`, `minimumLaunchDays`
- stable/intermittent: `stableRepeatMaxWeeklyCv`, `stableRepeatMinimumActiveWeeks`,
  `intermittentMaximumActiveWeeks`, `intermittentMaximumSalesDayRatio`
- spike/bulk: `spikeAbsoluteMinimum`, `spikeMadMultiplier`, `spikeWindowShareMinimum`,
  `bulkTransactionMinimumQuantity`, `bulkTransactionShareMinimum`
- rate: `minimumValidWeeklyRates`, `low/base/highDemandRatePercentile`

이 group에도 `assumptionType=ASSUMPTION`을 넣는다. 설명용 상수 노출일 뿐 응답을 만들며
classification을 다시 실행하지 않는다.

## 5. Persistence와 query 경계

- 새 service는 `@Transactional(readOnly = true)`다. lock과 write/flush를 수행하지 않는다.
- 목록은 전체 run을 메모리에 읽은 뒤 자르지 않는다. DB에서 filter, 고정 order,
  count와 page window를 적용한다.
- 목록 page의 quality/candidate count 등 child는 page metric id 집합으로 bulk 조회한다.
- 상세도 section별 bulk query를 사용하고 lazy relation을 transaction 밖에서 접근하지 않는다.
- 권장 query ceiling은 정상 목록 6 statements 이하, 정상 상세 14 statements 이하다.
  item/candidate 개수가 늘어도 statement 수가 늘지 않는 회귀 테스트를 둔다.
- raw SQL, constraint 이름, Batch metadata, stack message는 response에 노출하지 않는다.
- 이번 slice는 기존 table/index/constraint를 변경하지 않는다. 필요한 DB 변경은 6절 V15
  error catalog DML뿐이다.

## 6. 오류 계약과 V15 DML

기존 ProblemDetail shape, request id, effective catalog fallback, validation-only
`fieldErrors`를 그대로 사용한다. 현재 advice 범위를 `AnalysisController`와
`InventoryExceptionController` 두 controller로 넓히되 다른 controller에는 적용하지 않는다.

| Situation | Code | HTTP | Retryable |
|---|---|---:|---|
| run id 없음 | `ANALYSIS_NOT_FOUND` | 404 | N |
| run은 있으나 `COMPLETED` 아님 | `ANALYSIS_RESULTS_NOT_READY` | 409 | Y |
| metric 없음, NORMAL, 또는 해당 version의 예외 아님 | `INVENTORY_EXCEPTION_NOT_FOUND` | 404 | N |
| parameter/조합 오류 | `VALIDATION_ERROR` | 400 | N |
| DB connection/persistence failure | 기존 classifier의 `PERSISTENCE_UNAVAILABLE` 또는 `INTERNAL_SERVER_ERROR` | catalog 기준 | catalog 기준 |
| 완료 결과의 28일/catalog/FK 불변식 파손 | `INTERNAL_SERVER_ERROR` | 500 | N |

`V15__add_inventory_exception_read_error_catalog.sql`은 schema DDL 없이 다음 두 active row만
INSERT한다.

- `ANALYSIS_RESULTS_NOT_READY`: title `분석 결과 준비 중`, detail
  `요청한 분석 실행이 아직 완료되지 않아 재고 예외 결과를 조회할 수 없습니다.`
- `INVENTORY_EXCEPTION_NOT_FOUND`: title `재고 예외 없음`, detail
  `지정한 inventoryMetricId에 해당하는 조회 가능한 재고 예외가 없습니다.`

Java는 stable code만 선택하고 title/detail/message는 DB catalog가 소유한다. 새 catalog
lookup 실패도 기존 effective fallback 규칙을 따른다.

## 7. Required tests

### 7.1 DB-free

1. Legacy 목록 두 요청의 HTTP 200 body가 기존 bare array/필드/정렬을 유지한다.
2. run-bound request가 envelope/page default를 적용하고 모든 filter의 OR/AND 의미를 지킨다.
3. 고정 정렬의 여섯 key와 page 경계가 deterministic하다.
4. arbitrary first 후보가 사라지고 executable/comparison/rejected count가 정확하다.
5. MVP-1 상세는 기존 shape, MVP-2 상세는 새 shape로 분기한다.
6. validation 조합, unknown/non-completed run, normal/unknown metric의 code/status/fieldErrors를
   검증한다.
7. inventory controller의 DataAccess failure와 catalog fallback도 accepted ProblemDetail
   shape로 정규화한다.
8. empty child는 `[]`, nullable scalar는 null이며 raw exception text가 유출되지 않는다.

### 7.2 Oracle official Golden triple

기존 owner test를 훼손하거나 공식 triple 결과를 삭제하지 않고
`(2026-09-30, MVP-2-GS-V1, MVP-2)`의 completed run을 재사용한다.

1. 목록에서 GS-01 receiver가 `STABLE_REPEAT/HIGH`, executable 후보 있음으로 보인다.
2. GS-02 receiver가 `KNOWN_EVENT`, 상세 BASE rate `3.000000000000`, BASE quantity `20`을
   그대로 반환한다.
3. GS-04 receiver는 `OOS_CENSORED`, `LOW`, `REVIEW_REQUIRED`, 28일 중 실제 OOS day를
   반환한다.
4. GS-05 receiver 상세는 confirmed inbound와 `INBOUND_ALREADY_COVERS`, rejected 후보,
   빈 scenario를 반환한다.
5. GS-06 receiver 상세의 reason 순서는 `OWNER_MISMATCH`, `LEAD_TIME_TOO_LONG`이다.
6. GS-01 상세는 정확히 28일 연속 evidence와 4개 scenario, BASE quantity `11`을 반환한다.
7. signal/severity/quality/candidate filter와 `size=1` 다중 page가 SQL 정렬과 total을
   보존한다.
8. V15 두 row의 exact code/status/retryable/title/detail와 inventory HTTP error 응답을
   검증한다.
9. query-count 검증은 목록/상세가 5절 ceiling 안이고 row 수에 따라 증가하지 않음을 보인다.

### 7.3 Full regression

- Oracle 환경 full Backend test: skip 0, failures/errors 0
- DB 없는 full Backend test: Oracle conditional test만 skip, failures/errors 0
- 기존 MVP-1 Golden, MVP-2 Batch, approval, `MANUAL`, analysis REST test를 삭제·완화하지 않는다.

## 8. Out of scope

- 승인/보류/거절 write API와 idempotency/lock 로직 변경
- `MANUAL` 수량 시험 endpoint
- React 화면 연결, enum 한국어 label rendering과 coverage 표시 반올림
- AI explanation, scheduler, 인증/담당자 배정, 외부 ERP/WMS/TMS
- 실시간 입력 publication, 신규 가격/매출 예측 정책, schema/index 변경

## 9. Completion condition

구현은 다음이 모두 충족될 때 Codex 검증으로 넘긴다.

- 1~6절 public/query/error 계약을 만족한다.
- DTO와 repository/service에 loop query나 조회 중 business 결과 재계산이 없다.
- V15는 error catalog DML-only다.
- 7절 target과 두 full regression의 실제 test count/skip/failure를 기록한다.
- `implemented-state.md`에는 검증된 현재 사실만 반영하고 상세 구현 과정은 worklog에만 남긴다.

## Next verifiable action

Claude가 위 bounded read API만 구현하고 DB-free target부터 통과시킨 뒤 Oracle Golden/V15
target과 두 full regression 결과를 기록한다. 이후 Codex가 계약·성능·호환성을 독립 재검증한다.
