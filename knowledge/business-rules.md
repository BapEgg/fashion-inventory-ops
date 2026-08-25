# Inventory Analysis Business Rules

Status: `APPROVED MVP-2 DEMO ASSUMPTIONS` — Phase 1 Schema 완료, Java 규칙 미구현

Approved specification rule version: `MVP-2`

Active implemented Java rule version: `MVP-1`
Approved: 2026-08-25
Last updated: 2026-08-25

> 이 문서의 모든 기간, 임계값, 기본 정책과 수량식은 데모를 위한
> `ASSUMPTION`이다. 실제 F&F 정책이나 검증된 산업 표준값이 아니다. Java가
> 동일 입력에 동일 결과를 내도록 하는 유일한 규칙 원본이며, AI와 Frontend는
> 이 값을 재계산하거나 변경하지 않는다. 값이 바뀌면 새 rule version을 사용한다.

## 1. 버전 관리 ASSUMPTION 기준선

다음 값은 2026-08-25 `MVP-2` 데모 기준으로 승인됐다. 정책 입력이 제공된 행은
해당 매장–SKU 또는 경로 값을 사용하며 아래 데모 기본값은 합성 Seed의
fallback이다. F&F 내부 정책 또는 산업 표준의 근거로 재사용하지 않는다.

| 이름 | 승인된 ASSUMPTION 값 | 적용 |
|---|---:|---|
| 관측 기간 | 이전 `28일` | 분석일 제외 |
| 최소 관측 가능 일수 | `14일` | 재고가 있어 판매를 볼 수 있었던 날 |
| 최소 출시 경과일 | `14일` | 미만은 `DATA_INSUFFICIENT` |
| 재고 최신성 허용 | 분석 기준시각 이전 `24시간` | 초과 시 `STALE_INVENTORY` |
| 간헐 수요 활성 주 | `2개 이하` | 네 고정 주간 중 판매량 > 0 |
| 간헐 수요 판매일 비율 | `25% 미만` | 판매일 / 관측 가능일 |
| 안정 수요 최소 활성 주 | `3개 이상` | 네 고정 주간 기준 |
| 안정 수요 주간 CV | `0.35 이하` | 모집단 표준편차 / 평균 |
| 급증 절대 하한 | `5개` | 일판매 최대값 조건 |
| 급증 MAD 배수 | `3` | 중앙값 + 3 × max(MAD, 1) |
| 급증 기간 점유율 | `35% 이상` | 최대 일판매 / 28일 판매 |
| 단일 대량거래 수량 | `5개 이상` | 별도 플래그 |
| 단일 거래의 일판매 점유율 | `70% 이상` | 별도 플래그 |
| 내부 수요율 scale | 소수 `12자리` | `HALF_UP` |
| 화면 표시 수요율·커버리지 | 소수 `2자리` | `HALF_UP`, 계산에 재사용 금지 |
| 수요 매장 목표 커버리지 | `7일` | 매장–SKU 입력 없을 때 |
| 공급 매장 보존 커버리지 | `14일` | 매장–SKU 입력 없을 때 |
| 최소 진열량 | `1개` | 매장–SKU 입력 없을 때 |
| 안전재고 | `2개` | 매장–SKU 입력 없을 때 |
| 최대 수용량 | `100개` | 매장–SKU 입력 없을 때 |
| 최소 이동수량 | `1개` | 경로 입력 없으면 경로 자체를 허용하지 않음 |
| 포장 배수 | `1개` | 경로 입력 없으면 경로 자체를 허용하지 않음 |
| 경로 최대 이동수량 | `100개` | 경로 입력 없으면 경로 자체를 허용하지 않음 |

매장 간 경로는 명시적 입력이 없으면 `ROUTE_NOT_ALLOWED`다. 따라서 경로 관련
세 기본값은 **활성 경로 행 안의 누락값을 허용할 경우에만** 사용하는 제안값이며,
무등록 경로를 자동 허용하지 않는다.

## 2. 용어와 관측 가능성

- `analysisDate`: 관측에 포함하지 않는 분석 기준일
- `inputSnapshotVersion`: 한 분석이 읽은 입력 묶음의 불변 버전
- `currentAvailable`: 장부 재고에서 예약재고를 뺀 현재 가용재고
- `observableDay`: 그날의 재고 기준시각이 유효하고 판매 가능 재고가 1개 이상인 날
- `oosCensoredDay`: 품절 상태라 판매 0을 무수요 근거로 쓸 수 없는 날
- `activeWeek`: 해당 고정 7일 구간의 판매 합계가 1개 이상인 주
- `receiver`: 재고 부족을 검토하는 도착 매장
- `donor`: 같은 SKU를 공급할 수 있는 출발 매장

관측 구간은 `[analysisDate - 28일, analysisDate - 1일]`이다. 이를 오래된
순서대로 7일씩 나눈 네 개의 고정 주간으로 사용한다. 판매 0이라도 재고가
있으면 관측 가능일과 수요 분포에 포함한다. 품절일의 판매 0은 수요 0으로
사용하지 않고 `OOS_CENSORED` 플래그만 남긴다.

다음 중 하나면 자동 수량 판단의 입력이 불충분하다.

- 출시 후 경과일이 14일 미만
- 관측 가능일이 14일 미만
- 필수 수량이 음수이거나 예약재고가 장부재고보다 큼
- 분석 실행이 요구한 `inputSnapshotVersion`과 입력 행 버전이 맞지 않음

앞의 두 경우 주 신호는 `DATA_INSUFFICIENT`, 신뢰도는 `NONE`이다. 입력 자체가
모순인 뒤의 두 경우 재고 예외는 `NON_ACTIONABLE`이며 음수를 0으로 자동 보정하지
않는다.

## 3. 수요 신호 분류

`primaryDemandSignalType`은 하나만 저장하고 품질 플래그는 별도로 여러 개
저장한다. 아래 순서를 바꾸지 않는다.

1. 출시 경과일 또는 관측 가능일이 14일 미만이면 `DATA_INSUFFICIENT`.
2. 관련 이벤트가 관측 구간 또는 계획 구간과 겹치면 `KNOWN_EVENT`.
3. 알려진 이벤트가 없고 급증 조건을 만족하면 `UNEXPLAINED_SPIKE`.
4. 활성 주가 2개 이하 **또는** 판매 발생일 비율이 25% 미만이면 `INTERMITTENT`.
5. 활성 주가 3개 이상이고 주간 판매 CV가 0.35 이하이며 급증이 없으면
   `STABLE_REPEAT`.
6. 나머지는 `VARIABLE`.

계획 구간은 분석일부터 활성 경로별
`leadTimeDays + receiverTargetCoverageDays`의 최댓값까지다. 활성 경로가 하나도
없으면 분석일부터 데모 기본 목표 커버리지 7일까지다. 이벤트의 매장·SKU 범위가
현재 지표와 일치할 때만 관련 이벤트다.

### 급증과 단일 대량거래

관측 가능일의 일판매 배열에 판매 0을 포함해 중앙값과 MAD를 계산한다.

```text
medianDailySales = median(observable daily sales)
MAD = median(abs(dailySales - medianDailySales))

spikeThreshold = max(5, medianDailySales + 3 * max(MAD, 1))
spikeCandidate = maxDailySales >= spikeThreshold
              AND totalWindowSales > 0
              AND maxDailySales / totalWindowSales >= 0.35
```

짝수 개 중앙값은 가운데 두 값의 산술평균이다. `maxDailySales`가 같은 날이
여러 개면 가장 오래된 날을 근거일로 저장한다.

```text
singleBulkTransaction = maxTransactionQuantity >= 5
                     AND maxDailySales > 0
                     AND maxTransactionQuantity / maxDailySales >= 0.70
```

단일 대량거래는 급증의 근거 플래그이지 그 원인을 확정하는 라벨이 아니다.

### 활성 주, 판매일 비율과 CV

- `activeWeekCount`: 네 주 중 관측 가능일 판매 합계가 1 이상인 주 수
- `salesDayRatio`: 판매량이 1 이상인 관측 가능일 수 / 관측 가능일 수
- 주간 판매 CV: 네 고정 주간의 관측 가능일 판매 합계에 대한 모집단 표준편차 /
  산술평균. 평균이 0이면 CV는 정의하지 않으며 `STABLE_REPEAT`가 될 수 없다.

## 4. 품질 플래그와 신뢰도

필수 품질 플래그는 다음과 같다.

| Flag | 조건 |
|---|---|
| `OOS_CENSORED` | 관측 구간에 품절로 잘린 날이 하나 이상 |
| `STALE_INVENTORY` | 기준 재고시각이 분석 기준시각보다 24시간 넘게 오래됨 |
| `MISSING_INBOUND` | 입고 참조는 있으나 ETA·수량·상태 중 필수값이 불완전 |
| `INCOMPLETE_EVENT_DATA` | 이벤트는 있으나 대상 범위 또는 low/base/high uplift가 불완전 |

신뢰도는 예측 확률이 아니며 UI에서 `%`로 표시하지 않는다.

| Confidence | 조건 |
|---|---|
| `HIGH` | `STABLE_REPEAT`, 충분한 관측, 품질 플래그 없음 |
| `MEDIUM` | 정량 uplift가 완전한 `KNOWN_EVENT`, 또는 품질 플래그 없는 `VARIABLE` |
| `LOW` | `UNEXPLAINED_SPIKE`, `INTERMITTENT`, 또는 하나 이상의 품질 플래그 |
| `NONE` | `DATA_INSUFFICIENT` 또는 계산 불가능한 입력 |

주 신호가 `KNOWN_EVENT`여도 `INCOMPLETE_EVENT_DATA`가 있으면 `LOW`로 내린다.

## 5. low/base/high 수요율

일반 기준수요에서 `OOS_CENSORED`일, `UNEXPLAINED_SPIKE` 근거일과 관련 이벤트
기간의 판매일을 제외한다. 각 고정 주간에 남은 관측 가능일이 하나 이상이면:

```text
weeklyDemandRate = eligibleSalesQuantity / eligibleObservationDays
```

분모가 0인 주는 유효 주간 수요율에서 제외한다. 유효 주간 수요율이 3개 미만이면
low/base/high 자동 시나리오를 만들지 않고 `REVIEW_REQUIRED`로 보낸다.

각 주간 수요율은 계산 직후 scale 12, `HALF_UP`으로 고정한다. 정렬한 유효
주간 수요율을 `x[0..n-1]`이라 할 때 백분위는 선형 보간법으로 계산한다.

```text
h = (n - 1) * p
j = floor(h)
g = h - j
quantile(p) = x[j] * (1 - g) + x[min(j + 1, n - 1)] * g

lowDemandRate  = quantile(0.25)
baseDemandRate = quantile(0.50)
highDemandRate = quantile(0.75)
```

보간 결과도 scale 12, `HALF_UP`으로 고정한다. `KNOWN_EVENT`는 세 uplift factor가
모두 있고 `0 < upliftLow <= upliftBase <= upliftHigh`이며 **그 이벤트가 해당
시나리오의 도착·목표 커버리지 구간과 겹칠 때만** 각각의 수요율에 곱한다. 관측
구간에만 있었고 종료된 이벤트의 uplift를 미래 수요에 다시 곱하지 않는다. 곱한
직후 scale 12, `HALF_UP`으로 고정하며 화면 표시용 2자리 반올림값을 수량 계산에
재사용하지 않는다.

## 6. 재고 예상값과 예외

```text
currentAvailable = onHandQuantity - reservedQuantity

projectedReceiverBeforeDemand =
    currentAvailable
    + inboundArrivingBeforeTransfer
    + openTransferInbound
    - openTransferOutbound

projectedDonorAtDispatch =
    currentAvailable
    + inboundArrivingBeforeDispatch
    - openTransferOutbound
    - alreadyApprovedDraftQuantity
```

수요를 차감하기 전 위 값이 음수면 입력 오류이며 자동 보정하지 않는다. 도착 전
품절 위험은 별도로 다음처럼 계산하며 이 예측값은 음수가 될 수 있다.

```text
receiverAtArrivalWithoutNewTransfer(rate) =
    projectedReceiverBeforeDemand - ceil(rate * leadTimeDays)
```

재고 예외는 하나를 저장한다.

| Exception | 조건 |
|---|---|
| `STOCKOUT_RISK` | 가장 빠른 활성 경로 또는 확정 입고 도착 전 BASE 예상재고가 0 이하이거나 목표 커버리지 수량보다 부족 |
| `OVERSTOCK` | 최소 진열·안전재고와 high 수요 보존량을 유지하고도 이동 가능 재고가 존재 |
| `REVIEW_REQUIRED` | 낮은 신뢰도, 불완전 품질, 또는 자동 수량을 만들 수 없는 유형 |
| `NORMAL` | 즉시 검토할 조치 없음 |
| `NON_ACTIONABLE` | 입력 오류 또는 필수 제약 데이터 누락으로 계산 불가 |

`DATA_INSUFFICIENT`, `UNEXPLAINED_SPIKE`, `INTERMITTENT`, 정량 uplift가 없는
`KNOWN_EVENT`에는 단일 추천량을 만들지 않는다. `VARIABLE`은 보수적·기준·
공격적 시나리오 결과를 비교할 수 있지만 기본 추천 수량이나 자동 선택을
제공하지 않는다. 화면과 API는 이를 `comparisonOnly=true`와 `ASSUMPTION` 고지로
명확히 구분한다.

## 7. 이동 후보와 탈락 사유

후보는 아래 조건을 모두 통과해야 한다.

1. 동일 SKU, 서로 다른 매장이다.
2. 동일 재고 소유 주체이거나 경로가 소유권 예외를 명시적으로 허용한다.
3. 출발→도착 경로가 활성 상태다.
4. 예약, 진행 중 출고, 이미 승인된 초안, 보호재고를 제외한 공급량이 양수다.
5. 확정 입고가 도착 매장의 부족을 이미 해소하지 않는다.
6. 이동 도착일이 BASE 수요 기준 예상 품절일보다 늦지 않다.
7. 최소 이동수량, 포장 배수, 경로 최대수량과 도착 매장 최대 수용량을 만족한다.

탈락한 모든 조건을 저장하거나 응답한다. 정렬용 대표 사유가 필요하면 아래
목록에서 먼저 나온 코드를 사용하되 나머지 사유를 버리지 않는다.

1. `OWNER_MISMATCH`
2. `ROUTE_NOT_ALLOWED`
3. `LEAD_TIME_TOO_LONG`
4. `INBOUND_ALREADY_COVERS`
5. `NO_TRANSFERABLE_STOCK`
6. `DISPLAY_MINIMUM_VIOLATION`
7. `CAPACITY_EXCEEDED`
8. `PENDING_TRANSFER_CONFLICT`

## 8. 공급 가능량과 시나리오 수량

공급 매장은 high 수요율로 보호한다.

```text
donorProtectedQuantity =
    ceil(donorHighDemandRate * donorRetainedDays)
    + donorDisplayMinimum
    + donorSafetyStock

donorTransferableQuantity =
    max(projectedDonorAtDispatch - donorProtectedQuantity, 0)
```

도착 매장 필요량은 이동 중 소비를 목표 기간에 포함한다.

```text
receiverTargetQuantity(rate) =
    ceil(rate * (leadTimeDays + receiverTargetCoverageDays))
    + receiverDisplayMinimum

receiverNeed(rate) =
    max(receiverTargetQuantity(rate) - projectedReceiverBeforeDemand, 0)

receiverCapacityRemaining =
    max(receiverMaximumCapacity - projectedReceiverBeforeDemand, 0)
```

시나리오 수량의 적용 순서는 고정한다.

```text
rawQuantity = min(
    receiverNeed(rate),
    donorTransferableQuantity,
    routeMaximumQuantity,
    receiverCapacityRemaining
)

scenarioQuantity = floor(rawQuantity / packageMultiple) * packageMultiple
```

`scenarioQuantity < routeMinimumQuantity`이면 해당 시나리오는 수량 0과 제약
경고를 반환하며 실행 가능 시나리오로 보지 않는다. `ceil`을 적용하기 전 수요율을
2자리로 반올림하지 않는다.

| Scenario | rate / quantity |
|---|---|
| `NO_ACTION` | `0` |
| `CONSERVATIVE` | `lowDemandRate` |
| `BASE` | `baseDemandRate` |
| `AGGRESSIVE` | `highDemandRate` |
| `MANUAL` | 사용자 입력 후 위 공급·경로·수용량 제약 전부 재검증 |

각 결과는 양쪽 매장의 이동 전후 가용재고, 커버리지, 새 품절 위험, 반영한 입고,
진행 중 이동과 경고를 함께 반환한다.

## 9. 검토 우선순위

불투명한 단일 점수를 만들지 않고 다음 정렬 키를 순서대로 사용한다.

1. 심각도: `CRITICAL` → `HIGH` → `REVIEW`
2. 조치 가능성: 실행 가능한 공급 후보 있음 → 없음
3. 신뢰도: `HIGH` → `MEDIUM` → `LOW` → `NONE`
4. BASE 예상 부족수량 내림차순
5. 예상 매출 영향(`base shortage × current selling price`) 내림차순
6. 안정적 tie-breaker: `storeId`, `skuId` 오름차순

- `CRITICAL`: 가장 빠른 확정 입고 또는 활성 이동 경로의 도착 전에 BASE 예상
  재고가 0 이하
- `HIGH`: 목표 커버리지보다 부족하고 실행 가능한 조치가 있음
- `REVIEW`: 품질 경고나 낮은 신뢰도로 사람의 원인 확인이 우선

매출 영향은 정렬 보조값일 뿐 이익, 손실 확률 또는 실제 재무 예측으로 표시하지
않는다.

## 10. 결정, stale 검증과 감사

상태는 `PENDING`, `HELD`, `APPROVED`, `REJECTED`, `EXPIRED`다.

- 분석 결과 생성 시 논리 상태는 `PENDING`이다.
- `APPROVED`일 때만 `selectedQuantity > 0`이어야 한다.
- `HELD`, `REJECTED`, `EXPIRED`의 선택수량은 `NULL`이다.
- 추천된 BASE 수량을 그대로 승인하면 시스템 계산 근거를 자동 저장하고 자유
  설명은 선택이다.
- 수량 변경, `HELD`, `REJECTED`, 정책 예외 승인에는 reason code와 설명이
  필수다.
- 결정은 append-only 순번 이력으로 저장한다. 기존 이력을 덮어쓰지 않는다.

승인 요청은 `analysisRunId`, `inputSnapshotVersion`, `ruleVersion`, 후보 버전과
선택수량을 전달한다. 한 트랜잭션에서 다음을 수행한다.

1. 공유 donor의 재고 스냅샷 행을 잠근다.
2. 최신 재고, 예약, 입고, 진행 중 이동과 이미 승인된 draft 수량을 다시 읽는다.
3. 동일 후보·입력 버전인지, 모든 제약과 수량 범위를 다시 계산한다.
4. 실패하면 `STALE_RECOMMENDATION`으로 아무 것도 저장하지 않는다.
5. 성공하면 `APPROVED` 결정과 `TRANSFER_DRAFT`를 함께 저장한다.

실제 재고는 차감하지 않는다. 같은 공급 재고를 두 수요 매장이 동시에 승인해도
행 잠금과 승인 draft 재합산 때문에 공급 가능량을 초과할 수 없어야 한다.

## 11. AI와 RAG 경계

예외 탐지, 신호 분류, 신뢰도, 우선순위, 수량, 이동 가능 여부와 상태 전이는
Java·SQL 규칙이 담당한다. AI는 구조화된 계산 사실을 설명하거나 허용된 필터
API로 조회를 보조할 수 있을 뿐이다.

- AI 비활성·미설정·장애는 정상적인 unavailable 응답이며 핵심 흐름을 막지 않는다.
- AI는 새로운 수량, reason code, 품질 플래그 또는 상태를 생성하지 않는다.
- `UNEXPLAINED_SPIKE`의 원인을 단정하지 않고 확인 체크리스트만 제시한다.
- 실제 비정형 SOP가 제공될 때만 RAG를 검토하고 문서 버전과 조항을 인용한다.
- 정책 실행 가능 여부는 RAG가 아니라 Java 규칙이 최종 결정한다.
- 합성 정책 문서는 화면과 응답에서 `ASSUMPTION`으로 표시한다.

## 12. 합성 검증 시나리오 기대값

| ID | 필수 검증 |
|---|---|
| `GS-01` | `STABLE_REPEAT`, `HIGH`, 3개 자동 시나리오, 양쪽 매장 보호 |
| `GS-02` | `KNOWN_EVENT`, 완전한 uplift 반영, 3개 자동 시나리오 |
| `GS-03` | 급증식과 단일 대량거래 플래그, `LOW`, 단일 추천 없음 |
| `GS-04` | `OOS_CENSORED`, 판매 0을 무수요로 사용하지 않음 |
| `GS-05` | 확정 입고 반영 후 `INBOUND_ALREADY_COVERS` |
| `GS-06` | 소유권·경로·리드타임 중 설정된 위반의 정확한 reason code |

추가 회귀 테스트는 백분위 보간, scale 12 경계, `ceil`, 포장 배수, 최소수량,
최대 수용량, 음수 입력 거부, 동시 승인, 신상품과 AI-disabled 흐름을 포함한다.

## 13. MVP-1 회귀 기준

아래 규칙은 현재 구현된 `MVP-1`을 설명하는 Legacy 기준이며 MVP-2 식과 섞지
않는다.

- 관측 기간: 분석일 이전 7일
- 품절 위험: coverage `<= 3일`
- 과잉재고: coverage `>= 21일`
- 목표 커버리지 `7일`, donor 보존 `14일`, 안전재고 `2개`
- 현재 Golden Scenario: 강남 가용 5/7일 판매 28, 홍대 가용 40/판매 4,
  홍대→강남 25개
- 상태: `PENDING`(결정 부재), terminal `APPROVED`/`REJECTED`

기존 순수 Java 및 Oracle 통합 테스트는 MVP-2 구현 중에도 이 회귀값을 보존한다.

## 14. 승인 기록과 변경 관리

2026-08-25 다음 `MVP-2` 데모 규칙이 승인됐다.

1. 1절의 28일 관측, 최소 14일, CV 0.35, 급증·24시간 최신성,
   7일/14일 커버리지와 모든 fallback 정책은 versioned `ASSUMPTION`이다.
2. 동일 소유권 또는 명시적으로 허용된 국내 매장 간 이동만 계산한다.
3. 실제 배송 과정 없이 경로별 리드타임만 계산한다.
4. 이벤트 uplift는 예측하지 않고 low/base/high 입력값으로 받는다.
5. `VARIABLE`은 시나리오 비교만 제공하고 기본 추천 수량은 제공하지 않는다.
6. 승인은 실제 재고를 변경하지 않고 `SP_TRANSFER_DRAFT`만 생성한다.
7. 기존 `SP_REBALANCE_RECOMMENDATION`을 유지하고 복수 결과는
   `SP_REBALANCE_SCENARIO` 자식으로 저장한다.

위 규칙을 바꾸면 `MVP-2` 결과를 덮어쓰지 않고 새 rule version과 분석 실행을
만든다. 문서, UI, API 설명과 AI 출력은 이를 실제 F&F 정책이나 검증된 산업
표준으로 표현하지 않는다.
