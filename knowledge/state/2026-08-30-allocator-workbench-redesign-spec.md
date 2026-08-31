# StockPilot 재고 배분 담당자 워크벤치 전면 개편 명세

Status: IMPLEMENTATION READY
Design owner: Codex
Implementation owner: Claude
Base: accepted MVP-2 repository state on 2026-08-30
Scope type: one-pass UI/workflow rewrite plus the minimum Backend read-model and parity corrections

## 0. Claude 실행 계약

이 문서는 구현 중 선택지를 다시 묻지 않도록 제품 정책, API 계약, 화면 구조, 문구,
상태 전이, 파일 범위와 완료 조건을 고정한 단일 구현 명세다. 이 문서의 `MUST` 항목을
한 번의 변경 세트로 모두 구현한다. 단계별 부분 완료나 후속 TODO를 남기지 않는다.

구현 시작 시 다음 순서만 읽는다.

1. 루트 `AGENTS.md`
2. `knowledge/state/current-task.md`
3. 이 명세 전체
4. `knowledge/business-rules.md`
5. `knowledge/state/implemented-state.md`

구현 규칙은 다음과 같다.

- 현재 dirty worktree의 기존 변경을 사용자 소유 변경으로 간주하고 보존한다.
- 새 UI 프레임워크, 상태관리 라이브러리, 아이콘·차트·툴팁 패키지를 설치하지 않는다.
- React 19, TypeScript, 기존 Fetch API와 CSS 구조 안에서 구현한다.
- DB Migration, 테이블, Entity, 합성 seed, 인증, AI, 외부 ERP 연동을 추가하지 않는다.
- 기존 MVP-1 API와 MVP-2 분석·승인 request body, 멱등성, 잠금, 감사 이력을 깨지 않는다.
- Java가 수량·적격성·상태를 결정한다. Frontend는 응답을 재계산하거나 보정하지 않는다.
- 승인 결과는 ERP 이동요청 **초안** 생성이지 실제 출고·이동 완료가 아니다.
- 구현 종료 시 이 문서의 검증 명령과 브라우저 수용 시나리오를 모두 수행한다.
- 의미 있는 구현 종료 시 `stockpilot-worklog` 지침에 따라 hot state와 worklog를 갱신한다.

코드가 이 문서와 충돌할 때 단순 구현 선택이 아니라 Public API/DB/AI-Java 책임 경계가
달라져야만 해결된다면 임의 확장하지 말고 충돌 근거를 보고한다. 그 외에는 이 문서가
승인된 구현 근거다.

## 1. 목표와 성공 기준

### 1.1 목표 사용자

본사 패션 리테일 재고 배분·보충 담당자다. 이 사용자는 개발자가 아니며 다음 질문에
빠르게 답하려고 화면을 연다.

1. 오늘 내가 먼저 처리할 매장-상품은 무엇인가?
2. 이미 입고 또는 이동 중인 수량을 반영해도 추가 이동이 필요한가?
3. 어느 매장에서 몇 개를 보내는 안이 가능한가?
4. 수량을 바꾸면 입고점과 출고점 재고가 어떻게 달라지는가?
5. 승인·보류·반려가 실제로 저장됐고 다음 상태가 무엇인가?

### 1.2 완료된 사용자 흐름

한 건의 업무는 다음 흐름으로 끝나야 한다.

```text
오늘의 처리 현황 확인
  → 처리 상태·우선도·금액 기준으로 대상 찾기
  → 매장/상품 핵심 재고와 이미 반영 중인 물량 확인
  → 이동 가능한 출고 매장 선택
  → 추가 이동수량과 양쪽 매장 결과 확인
  → 이동 승인·보류·반려
  → 저장 결과와 ERP 이동요청 초안 또는 처리 이력 확인
```

상세 진입 후 원시 28일 표를 먼저 읽지 않아도 이동안과 처리영역에 도달해야 한다.
`REJECTED` 또는 비교 전용 후보는 처리 버튼처럼 보이면 안 된다. 데이터·정책 확인
대상과 이동안이 없는 대상은 긴급도만으로 `이동 결정 필요`보다 앞에 오지 않는다.

### 1.3 정량 수용 기준

- 완료된 run을 연 뒤 첫 화면에서 전체 처리 대상, 긴급, 이동 결정 필요, 원인 확인,
  매출 노출액 합계를 별도 상세 진입 없이 볼 수 있다.
- 목록의 `목표재고 대비 부족`, `재고일수`, `매출 노출액`을 전체 결과 기준으로 정렬한다.
- 상세 기본 탭에서 매장·상품, 판매가능재고, 목표 부족, 확정입고, 진행 중 이동,
  출고 가능 매장, 추가 이동 제안, 처리 버튼을 모두 확인한다.
- 실행 가능한 후보의 저장 추천수량은 자동 검증되고, 같은 최신 근거에서 수량시험이
  `CANDIDATE_INELIGIBLE/PENDING_TRANSFER_CONFLICT`로 뒤집히지 않는다.
- 승인 성공 후 목록과 상세이 최신 처리상태로 갱신되며, 초안 ID가 있으면 명시한다.
- 375px와 데스크톱에서 페이지 자체의 가로 스크롤이 없다. 넓은 표만 내부 스크롤한다.

## 2. 범위

### 2.1 반드시 구현

- 사용자 용어 전면 교체와 기술 용어의 감사영역 격리
- run-wide 업무 요약, 처리상태 탭, 업무용 필터와 서버 정렬
- derived `AllocatorWorkStatus`와 목록 차단 사유
- 데스크톱 master-detail 및 모바일 단일 상세 레이아웃
- 상세정보 탭 재배치: `이동안 검토`를 기본, 근거·정책을 보조 탭으로 이동
- 진행 중 입고·매장이동과 새 **추가** 이동 제안 구분
- 실행 가능 후보 자동 선택 및 저장 추천수량 자동 검증
- 이동 승인·보류·반려 전용 처리 UI와 승인 확인 대화상자
- 처리 성공 후 상세·목록 refresh
- 확정입고 null의 `없음`과 `데이터 확인 필요` 구분
- 매출 노출액 산식과 한계 설명
- 차단 사유별 다음 행동 안내
- 승인 직전 현재근거 계산의 open-transfer status parity 수정
- 비교 전용 후보의 Frontend 처리 차단과 Backend fail-closed 검증
- Backend/Frontend 단위·통합 회귀 테스트와 문서 용어 갱신

### 2.2 명시적으로 제외

- 실제 ERP 전송, 재고 차감, 피킹·출고·운송·입고 처리
- 인증·인가, 담당자 자동 할당, 조직별 접근권한
- 여러 행 일괄 승인·반려
- `REVIEW_REQUIRED/NON_ACTIONABLE` 전용 확인완료 저장 테이블이나 새 write API
- 상품 이미지 URL 또는 이미지 저장 모델
- 실시간 알림, scheduler, WebSocket
- AI 설명 생성 또는 AI 기반 우선순위·수량 결정
- Batch 산식, 수요율, 부족수량, 추천수량의 정책 변경
- 기존 결정 DB schema나 migration 변경

`REVIEW_REQUIRED/NON_ACTIONABLE`은 이번 범위에서 별도 완료상태를 저장하지 않는다. 대신
왜 확인이 필요한지와 어느 원천정보를 확인할지 명확하게 안내하며 재고 현황 갱신으로
재평가하도록 한다. 존재하지 않는 할당·확인완료 기능을 Frontend 임시상태로 흉내 내지
않는다.

## 3. 변경하지 않는 업무 불변조건

- `판매가능재고 = onHandQuantity - reservedQuantity`다.
- `APPROVED`, `IN_TRANSIT` open transfer는 입고점·출고점 projection에 한 번 반영한다.
- 같은 donor-receiver-SKU lane의 `REQUESTED`만 신규 이동의
  `PENDING_TRANSFER_CONFLICT`가 된다.
- `CANCELLED`, `RECEIVED` open transfer는 projection과 conflict에서 제외한다.
- 승인 직전 최신 run/version/candidate와 공급 가능량을 다시 검증한다.
- `APPROVED`는 양의 selected quantity와 동일 tuple의 feasible MANUAL 결과가 필요하다.
- 기준 추천수량 그대로 승인하고 정책 예외가 아니면 사유는 선택이다.
- 수량 변경, 정책 예외, `HELD`, `REJECTED`는 사유 코드와 설명이 필수다.
- `APPROVED`와 `REJECTED`는 terminal, `HELD`는 다시 처리 가능하다.
- 승인은 append-only decision과 approval basis, `SP_TRANSFER_DRAFT`를 만들지만 재고를
  변경하지 않는다.
- `COMPARISON_ONLY`는 비교 자료일 뿐 승인·보류·반려 대상이 아니다.
- 모든 `ASSUMPTION/SYNTHETIC` 값은 특정 기업의 실제 정책·데이터가 아니다.

## 4. Backend 계약

### 4.1 신규 파생 상태

`com.bapegg.stockpilot.analysis.AllocatorWorkStatus` enum을 추가한다.

```java
DECISION_REQUIRED
ON_HOLD
REVIEW_INPUT
NO_TRANSFER_OPTION
COMPLETED
```

한 metric에 연결된 후보 중 `candidateStatus=ELIGIBLE`이고
`recommendationMode=RECOMMENDED`인 후보만 실행 후보로 본다. 후보별 최신 결정은 가장 큰
`decisionSequence` 한 건이다. 파생 우선순위는 반드시 다음 순서를 사용한다.

1. 실행 후보 중 최신 결정이 없거나 `PENDING`인 후보가 하나라도 있으면
   `DECISION_REQUIRED`.
2. 1이 아니고 최신 결정이 `HELD`인 실행 후보가 하나라도 있으면 `ON_HOLD`.
3. 1·2가 아니고 실행 후보가 하나 이상이면 `COMPLETED`. 이때 모든 실행 후보의 최신
   결정은 terminal이어야 한다.
4. 실행 후보가 없고 exception type이 `REVIEW_REQUIRED` 또는 `NON_ACTIONABLE`이면
   `REVIEW_INPUT`.
5. 나머지는 `NO_TRANSFER_OPTION`.

후보가 여러 개인 혼합 상태에서도 위 순서를 한 번만 적용한다. Frontend가 이 상태를
재구성하지 않는다.

### 4.2 목록 request 확장

기존 `GET /api/inventory-exceptions`의 run-bound mode에 optional query를 추가한다.

| Query | 값 | 기본값 | 규칙 |
|---|---|---|---|
| `workStatus` | 위 enum, repeatable | 없음 | OR 다중선택 |
| `sortBy` | `WORK_PRIORITY`, `SALES_EXPOSURE`, `SHORTAGE_QUANTITY`, `COVERAGE_DAYS`, `STORE_PRODUCT` | `WORK_PRIORITY` | 그 외 `VALIDATION_ERROR` |
| `sortDirection` | `ASC`, `DESC` | sort별 기본값 | 그 외 `VALIDATION_ERROR` |

sort별 방향 기본값은 다음과 같다.

- `WORK_PRIORITY`: `ASC`
- `SALES_EXPOSURE`: `DESC`
- `SHORTAGE_QUANTITY`: `DESC`
- `COVERAGE_DAYS`: `ASC`
- `STORE_PRODUCT`: `ASC`

`sortDirection`을 명시하면 선택 sort에 적용한다. 모든 sort는 마지막 tie-breaker로
`storeId ASC, skuId ASC, inventoryMetricId ASC`를 붙여 pagination 순서를 고정한다.

`WORK_PRIORITY ASC`의 주 정렬은 다음 순서다.

1. work status: `DECISION_REQUIRED`, `ON_HOLD`, `REVIEW_INPUT`,
   `NO_TRANSFER_OPTION`, `COMPLETED`
2. severity: `CRITICAL`, `HIGH`, `REVIEW`
3. estimated sales exposure DESC NULLS LAST
4. expected shortage DESC NULLS LAST
5. confidence: `HIGH`, `MEDIUM`, `LOW`, `NONE`
6. stable tie-breaker

다른 sort도 null은 항상 마지막이다. `COVERAGE_DAYS ASC`는 낮은 재고일수부터,
`STORE_PRODUCT ASC`는 `storeName`이 아니라 안정적인 `storeId, skuId` 순으로 정렬한다.

새 query 중 하나라도 있으면 MVP-2 run-bound mode로 분기한다. 기존 MVP-1 bare-array
호출은 그대로 유지한다. 잘못된 query는 기존 RFC 9457 `ProblemDetail` 형식과 field error를
사용한다.

### 4.3 목록 item 확장

`Mvp2InventoryExceptionListItem`에 다음을 추가한다.

```java
AllocatorWorkStatus workStatus,
List<TransferCandidateRejectionReason> blockingReasons
```

`blockingReasons`는 이 metric과 연결된 `REJECTED` 후보의 사유를 중복 제거하고 업무 규칙의
사유 우선순위와 `reasonOrder`에 맞춰 정렬한다. 실행 후보가 있으면 빈 배열이어야 한다.
값이 없을 때도 `null`이 아니라 빈 배열을 반환한다.

### 4.4 run-wide summary

`Mvp2InventoryExceptionPage`에 non-null `summary`를 추가한다.

```java
public record AllocatorWorkSummary(
    long totalReviewTargets,
    long criticalCount,
    long decisionRequiredCount,
    long onHoldCount,
    long reviewInputCount,
    long noTransferOptionCount,
    long completedCount,
    BigDecimal estimatedSalesExposureTotal,
    long estimatedSalesExposureUnknownCount
) {}
```

요약은 현재 page와 사용자 filter의 영향을 받지 않고 해당 completed run의 모든
`inventoryExceptionType <> NORMAL` metric을 집계한다. 모든 page에서 동일해야 한다.

- `totalReviewTargets`는 non-NORMAL metric 수다.
- 다섯 work-status count의 합은 항상 `totalReviewTargets`다.
- `criticalCount`는 severity가 `CRITICAL`인 non-NORMAL metric 수다.
- `estimatedSalesExposureTotal`은 `expectedShortageQuantity × currentSellingPrice`를 계산할
  수 있는 행만 합산하고 scale 2, `HALF_UP`을 유지한다.
- `estimatedSalesExposureUnknownCount`는 shortage가 양수지만 D-1 selling price가 없어
  금액을 계산할 수 없는 행 수다.
- 계산 가능한 행이 하나도 없으면 total은 null이 아니라 `0.00`이고 unknown count를 함께
  표시한다.
- 금액을 알 수 없는 행을 0원이라고 사용자에게 주장하지 않는다.

목록 item의 `estimatedSalesImpact` 기존 계산식과 summary 합계는 같은 helper 또는 동일한
검증된 규칙을 사용해야 한다.

### 4.5 조회 성능과 구현 제한

- page size에 따라 SQL statement 수가 증가하면 안 된다.
- empty/non-empty page 모두 N+1 query를 만들지 않는다.
- summary를 위해 모든 Entity를 메모리에 적재하지 않는다. Oracle conditional aggregate
  또는 bounded bulk query를 사용한다.
- 파생 work status의 Java/SQL 의미가 갈라지지 않도록 resolver 테스트와 repository 통합
  테스트를 함께 둔다.
- non-empty list request의 query ceiling은 9 statements 이하, detail ceiling은 기존
  14 statements 이하로 유지한다.
- size=1과 size=100의 statement count는 동일해야 한다.

### 4.6 open-transfer parity 결함 수정

`CurrentApprovalBasisLoader`의 상태 집합을 다음처럼 고정한다.

```java
COMMITTED_OPEN_TRANSFER_STATUSES = {APPROVED, IN_TRANSIT}
PENDING_CONFLICT_STATUSES = {REQUESTED}
```

현재 `PENDING_CONFLICT_STATUSES`에 들어 있는 `APPROVED`, `IN_TRANSIT`를 제거한다. 이미
projection에 반영한 수량을 동일 lane conflict로 다시 차단하지 않는다. Batch
`Mvp2CalculationOrchestrator`와 수량시험/승인 current-basis가 이 규칙에서 일치해야 한다.

### 4.7 비교 전용 후보 fail-closed

- Frontend actionable 조건은 `ELIGIBLE && RECOMMENDED && !terminal`이다.
- `ELIGIBLE && COMPARISON_ONLY`는 시나리오만 볼 수 있고 수량시험과 결정을 할 수 없다.
- Backend MANUAL과 모든 decision status(`HELD/APPROVED/REJECTED`)도 recommendation mode를
  재검증한다.
- MANUAL은 `CANDIDATE_INELIGIBLE`로 feasible false를 반환한다.
- decision write path는 `candidateStatus != ELIGIBLE` 또는
  `recommendationMode != RECOMMENDED`이면 기존 `STALE_RECOMMENDATION` 409 계약으로
  거부하며 decision·basis·draft를 쓰지 않는다. 이 검증은 recommendation lock과 version
  검증 뒤, status별 write 분기 전에 한 번 수행한다. 새 DB error catalog나 migration은
  만들지 않는다.

### 4.8 Frontend type/API mirror

`frontend/src/types.ts`는 Backend와 정확히 맞춰 다음 타입을 추가한다.

```ts
type AllocatorWorkStatus =
  | 'DECISION_REQUIRED'
  | 'ON_HOLD'
  | 'REVIEW_INPUT'
  | 'NO_TRANSFER_OPTION'
  | 'COMPLETED'

type ExceptionSortKey =
  | 'WORK_PRIORITY'
  | 'SALES_EXPOSURE'
  | 'SHORTAGE_QUANTITY'
  | 'COVERAGE_DAYS'
  | 'STORE_PRODUCT'

type SortDirection = 'ASC' | 'DESC'
```

`ExceptionListFilters`에 `workStatus`, `sortBy`, `sortDirection`을 추가하고 `api.ts`가
repeatable query 및 sort query를 직렬화한다. 첫 조회 default는
`workStatus=['DECISION_REQUIRED']`, 나머지 filter는 비움, `WORK_PRIORITY/ASC`, page 0,
size 20이다. 첫 응답 summary의 decisionRequiredCount가 0이면 workStatus를 비우고
`전체`로 정확히 한 번 다시 조회한다.

Frontend interface는 다음 field를 빠짐없이 mirror한다.

```ts
interface AllocatorWorkSummary {
  totalReviewTargets: number
  criticalCount: number
  decisionRequiredCount: number
  onHoldCount: number
  reviewInputCount: number
  noTransferOptionCount: number
  completedCount: number
  estimatedSalesExposureTotal: number
  estimatedSalesExposureUnknownCount: number
}

// Mvp2InventoryExceptionListItem additions
workStatus: AllocatorWorkStatus
blockingReasons: TransferCandidateRejectionReason[]

// Mvp2InventoryExceptionPage addition
summary: AllocatorWorkSummary
```

## 5. 사용자 용어 사전

enum/code/DTO 필드명은 변경하지 않고 사용자에게 보이는 한국어만 다음 표로 통일한다.
같은 개념을 화면마다 다른 단어로 번역하지 않는다.

### 5.1 화면과 작업

| 기존 표시 | 교체 표시 | 사용 위치 |
|---|---|---|
| StockPilot 재고 배분 워크벤치 | StockPilot 매장간 재고 이동 | 앱 제목 |
| 분석 실행 | 재고 현황 갱신 | 주 동작 |
| 분석 실행/실행 중/완료됨 | 갱신/갱신 중/갱신 완료 | 사용자 상태 |
| 재고 이슈 | 처리 대상 | 목록 전체 |
| 재고 이슈 필터 | 처리 대상 찾기 | filter aria/title |
| 상세 보기 | 검토하기 | 목록 CTA |
| 상세 새로고침 | 최신 내용 불러오기 | stale 복구 |
| 공급 후보 | 출고 가능 매장 | 후보 영역 |
| 선택 | 이동안 검토 | 실행 후보 CTA |
| 자동 시나리오 비교 | 이동수량 비교 | scenario 영역 |
| MANUAL 수량 시험 | 이동수량 변경 | 처리 영역 |
| 수량 시험 | 변경 결과 확인 | button |
| 결정 | 처리 | section |
| 결정 이력 | 처리 이력 | history |
| 이동지시 초안 | ERP 이동요청 초안 | 성공/감사영역 |
| 계산 기준 보기 | 산출 기준 상세 | disclosure |

### 5.2 재고와 우선순위

| 기존 표시 | 교체 표시 |
|---|---|
| 이슈 유형 | 검토 사유 |
| 심각도 | 업무 우선도 |
| 품절 위험 | 품절 위험 |
| 과잉재고 | 과다 재고 |
| 검토 필요 | 원인 확인 필요 |
| 조치 불가 | 데이터·정책 확인 필요 |
| 현재 가용재고 | 현재 판매가능재고 |
| 예상 가용재고 | 입고·이동 반영 예상재고 |
| 예상 부족수량 | 목표재고 대비 부족 |
| 재고 보유일수 | 재고일수 |
| 다음 확정입고 | 확정 입고 예정 |
| 진행 중인 이동 | 진행 중 매장이동 |
| 매출 영향 | 매출 노출액(참고) |
| 우선순위 (참고) | 업무 우선도 |
| 분류 근거 (참고) | 산출 분류 |

`매출 노출액(참고)`에는 항상 다음 설명을 제공한다.

> 목표재고 대비 부족수량 × 기준일 전일 평균판매가입니다. 우선순위 비교용이며 실제
> 손실 매출이나 발생 확률을 뜻하지 않습니다.

### 5.3 판매 흐름과 데이터 확인

| Code | 사용자 표시 |
|---|---|
| `DATA_INSUFFICIENT` | 판매 이력 부족 |
| `KNOWN_EVENT` | 등록 행사 영향 |
| `UNEXPLAINED_SPIKE` | 일시 판매 급증 확인 필요 |
| `INTERMITTENT` | 판매 간격 큼 |
| `STABLE_REPEAT` | 최근 판매 흐름 안정 |
| `VARIABLE` | 판매 변동 큼 |
| Demand signal section | 판매 흐름 |
| Demand confidence | 판단 근거 수준 |
| Quality flags | 데이터 확인 사항 |
| `OOS_CENSORED` | 품절 기간이 포함되어 판매량 해석 주의 |
| `STALE_INVENTORY` | 재고 정보 갱신 시각 확인 필요 |
| `MISSING_INBOUND` | 입고 정보 확인 필요 |
| `INCOMPLETE_EVENT_DATA` | 행사 정보 확인 필요 |

신뢰도는 확률이 아니므로 `%`를 붙이지 않는다. `HIGH/MEDIUM/LOW/NONE`은
`충분/보통/낮음/판단 어려움`으로 표시한다.

### 5.4 후보와 이동

| 기존 표시 | 교체 표시 |
|---|---|
| 이 매장이 받는 후보 | 이 매장으로 보낼 수 있는 출고점 |
| 이 매장이 공급하는 후보 | 이 매장에서 받을 수 있는 입고점 |
| 상대 매장 | 출고 매장 또는 입고 매장 |
| 적격 | 이동 가능 |
| 탈락 | 이동 불가 |
| 추천 방식 | 이동안 유형 |
| 추천수량 | 추가 이동 제안 |
| 이동 가능량 | 출고 가능 수량 |
| 부족수량 | 목표재고 대비 부족 |
| 경로(최소/배수/최대) | 이동 조건(최소/포장단위/최대) |
| 리드타임 | 예상 이동 기간 |
| 평가시각 | 산출 시각 |
| 최신 결정 | 처리 상태 |
| 결정 없음 | 미처리 |
| 비교용 후보 | 비교 전용(처리 불가) |

### 5.5 차단 사유

| Code | 짧은 표시 | 상세 설명 |
|---|---|---|
| `OWNER_MISMATCH` | 재고 소유 정책 제한 | 출고점과 입고점의 재고 소유 정책이 달라 바로 이동할 수 없습니다. |
| `ROUTE_NOT_ALLOWED` | 이동 경로 없음 | 두 매장 사이에 사용 가능한 이동 경로가 등록되어 있지 않습니다. |
| `LEAD_TIME_TOO_LONG` | 도착 예정이 늦음 | 예상 도착일이 품절 위험 대응 시점보다 늦습니다. |
| `INBOUND_ALREADY_COVERS` | 예정 입고로 부족 해소 | 확정 입고를 반영하면 목표재고 부족이 해소됩니다. |
| `NO_TRANSFERABLE_STOCK` | 출고 가능 재고 부족 | 출고점이 유지해야 할 재고를 제외하면 보낼 수량이 없습니다. |
| `DISPLAY_MINIMUM_VIOLATION` | 진열재고 유지 기준 미충족 | 이동하면 출고점의 최소 진열재고를 유지할 수 없습니다. |
| `CAPACITY_EXCEEDED` | 입고점 수용 한도 초과 | 이동하면 입고점의 최대 수용재고를 넘습니다. |
| `PENDING_TRANSFER_CONFLICT` | 같은 경로 요청 진행 중 | 같은 출고점·입고점·상품의 승인 전 이동 요청이 이미 있습니다. |

### 5.6 시나리오, 처리와 상태

| Code/기존 표시 | 사용자 표시 |
|---|---|
| `NO_ACTION` | 이동하지 않음 |
| `CONSERVATIVE` | 낮은 수요 기준 |
| `BASE` | 기준 수요 |
| `AGGRESSIVE` | 높은 수요 기준 |
| `DECISION_REQUIRED` | 이동 결정 필요 |
| `ON_HOLD` | 확인 후 재검토 |
| `REVIEW_INPUT` | 원인·데이터 확인 |
| `NO_TRANSFER_OPTION` | 이동안 없음 |
| `COMPLETED` | 처리 완료 |
| APPROVED action | 이동 승인 |
| HELD action | 보류 |
| REJECTED action | 이동안 반려 |
| APPROVED saved state | 승인됨 |
| HELD saved state | 보류됨 |
| REJECTED saved state | 반려됨 |
| EXPIRED | 만료됨 |

동작형 문구와 완료 상태를 구분한다. 버튼에 `승인됨 제출`, `거절됨 제출` 같은 문구를
사용하지 않는다.

### 5.7 사용자 기본 화면에서 숨길 기술 정보

다음 값은 삭제하지 않고 기본 접힘 `산출 기준 상세` 또는 처리 이력의 `감사정보`에만 둔다.

- run ID, input snapshot version, rule version, calculation version, candidate version
- observable day count, active week count, sales-day ratio
- low/base/high rate raw values와 percentile threshold
- MAD, CV, uplift 원시값
- source type, assumption code의 반복 표기
- decision contract version, basis contract version, payload version
- approval basis ID, 내부 sequence

화면 최상단에 한 번만 다음 배너를 표시한다.

> 데모 데이터와 가정 정책으로 계산한 결과입니다. 실제 기업의 재고·이동 정책이 아닙니다.

각 표 셀마다 `SYNTHETIC/ASSUMPTION`을 반복하지 않는다. 감사영역에는 원본 code를 유지한다.

## 6. 정보 구조와 레이아웃

### 6.1 전체 구조

```text
┌ StockPilot 매장간 재고 이동 ─ 데이터 기준 ─ 재고 현황 갱신 ┐
│ 데모 데이터/가정 정책 안내                                  │
│ [전체 처리 대상] [긴급] [이동 결정 필요] [원인 확인] [노출액] │
│ 처리상태 tabs                                                │
│ 검색/업무필터                         정렬                    │
├────────────────────────┬────────────────────────────────────┤
│ 처리 대상 worklist     │ 선택 대상 상세                     │
│ 상태·우선도·매장상품   │ object header                      │
│ 재고·입고·이동안·금액  │ [이동안 검토][판매·재고 근거]       │
│                        │ [입고·매장이동][산출 기준 상세]      │
│                        │ 진행 중 물량 → 후보 → 수량 → 처리   │
└────────────────────────┴────────────────────────────────────┘
```

- 1280px 이상은 목록 44%, 상세 56% master-detail이다.
- 목록을 선택하지 않았으면 worklist가 전체 폭을 사용하고 오른쪽 빈 panel을 만들지 않는다.
- 1024~1279px은 목록 40%, 상세 60%로 두되 각 panel 내부 표만 가로 스크롤한다.
- 1023px 이하는 상세를 전체 화면으로 전환하고 `← 처리 대상 목록`으로 돌아간다.
- overlay backdrop과 760px drawer는 제거한다.
- 목록/상세 선택 상태는 기존 local React state로 유지하며 router/global store를 추가하지
  않는다.

### 6.2 화면 밀도와 시각 원칙

- 실무용 worklist답게 흰 surface, 옅은 회색 배경·경계, 한 가지 파란 action color를 쓴다.
- gradient, glassmorphism, 큰 hero, 장식용 일러스트, 과도한 카드·그림자·둥근 모서리를 쓰지
  않는다.
- 숫자는 tabular lining과 우측 정렬을 사용한다.
- 상태는 색만으로 구분하지 않고 텍스트를 함께 표시한다.
- 기본 본문 14px, 보조 12px, 표 행 높이 44~52px, control 높이 최소 36px를 권장한다.
- 시스템 Korean font stack을 사용하고 새 webfont를 받지 않는다.
- 현재 repository의 badge CSS를 정리해 재사용하되 의미별 색 대비 4.5:1을 만족한다.

### 6.3 run control

`AnalysisContext`는 큰 개발용 실행 form이 아니라 `재고 현황 기준` utility bar로 보인다.

- 항상 보임: 기준일, `재고 현황 갱신` button, 현재 갱신 상태, 완료 시각.
- `데이터 기준 상세` disclosure 안: input snapshot version, rule version, run ID.
- 초기 input snapshot version 값과 validation은 유지한다.
- `RUNNING`: `재고 현황을 갱신하고 있습니다…`
- timeout: `갱신이 계속 진행 중입니다`와 `상태 확인`.
- reused completed run: `기존 갱신 결과를 불러왔습니다`.
- new completed run: `재고 현황 갱신이 완료되었습니다`.
- 새 run을 시작할 때 이전 목록·상세·결정 상태를 즉시 retire하는 기존 안전장치를 유지한다.

## 7. 메인 작업목록

### 7.1 업무 요약

`page.summary`로 다음 다섯 tile을 표시한다.

1. `전체 처리 대상`: totalReviewTargets
2. `긴급`: criticalCount
3. `이동 결정 필요`: decisionRequiredCount
4. `원인·데이터 확인`: reviewInputCount
5. `매출 노출액(참고)`: estimatedSalesExposureTotal

금액 미산정 수가 양수면 노출액 tile 보조문구로 `금액 미산정 N건`을 표시한다. tile을
클릭하면 `전체`, `이동 결정 필요`, `원인·데이터 확인`은 대응 tab/filter를 적용한다.
`긴급`은 severity CRITICAL filter를 적용한다. 금액 tile은 정렬을
`SALES_EXPOSURE/DESC`로 바꾼다. run-wide 숫자와 클릭 결과를 일치시키기 위해 tile click은
기존 사용자 filter를 모두 초기화한 뒤 해당 tile의 workStatus/severity/sort만 적용하고
page 0으로 조회한다. tile은 button으로 구현하고 keyboard focus가 보여야 한다.

### 7.2 처리상태 tab

다음 순서와 count를 표시한다.

```text
전체 | 이동 결정 필요 | 확인 후 재검토 | 원인·데이터 확인 | 이동안 없음 | 처리 완료
```

- 첫 진입 default는 `이동 결정 필요`다.
- 해당 count가 0이면 자동으로 `전체`를 선택한다. 빈 화면을 기본으로 만들지 않는다.
- tab은 `workStatus` filter 한 종류만 바꾸고 다른 고급 filter는 유지한다.
- 선택된 tab과 filter state/API query가 항상 일치한다.

### 7.3 filter bar

기본 bar에는 다음만 보인다.

- 매장 ID 정확 검색
- 상품 SKU 정확 검색
- `업무 우선도` 다중 선택
- `검토 사유` 다중 선택
- `필터 적용`, `초기화`
- `추가 필터` disclosure

추가 필터 안에는 `판매 흐름`, `판단 근거 수준`, `데이터 확인 사항`, page size를 둔다.
`이동 가능 여부` radio는 제거하고 처리상태 tab으로 대체한다. Backend 호환을 위해
`hasExecutableCandidate` 타입/API 지원은 유지하되 이 화면에서는 전송하지 않는다.

filter label은 5장 용어를 사용한다. 입력할 때마다 요청하지 않고 `필터 적용` 한 번에
page 0으로 조회한다. 적용된 고급 filter는 bar 아래 제거 가능한 text chip으로 표시한다.

### 7.4 sort

목록 상단 select와 sortable column header를 모두 같은 state에 연결한다.

- 업무 우선순위
- 매출 노출액 높은 순
- 목표재고 부족 큰 순
- 재고일수 낮은 순
- 매장·상품 순

`목표재고 대비 부족`, `재고일수`, `매출 노출액` header는 button이고 현재 방향을
`aria-sort`로 표현한다. header click은 Backend query를 다시 요청하며 현재 page를 0으로
돌린다. 현재 page item만 client-side 정렬하지 않는다.

### 7.5 table 열과 셀 규칙

열 순서는 다음과 같다.

1. 처리 상태
2. 업무 우선도
3. 매장
4. 상품
5. 검토 사유·판매 흐름
6. 현재 판매가능재고
7. 목표재고 대비 부족
8. 재고일수
9. 확정 입고 예정
10. 이동안
11. 매출 노출액(참고)
12. 검토

셀 규칙은 다음과 같다.

- 매장은 name 1행, `region · storeId` 2행이다.
- 상품은 name 1행, `color / size · skuId` 2행이다.
- 검토 사유 1행, 판매 흐름과 판단 근거 수준을 2행에 둔다.
- 입고 시각/수량이 있으면 `MM.DD HH:mm · N개`.
- 입고가 null이고 `MISSING_INBOUND`가 있으면 `입고 정보 확인 필요`.
- 입고가 null이고 그 flag가 없으면 `확정 입고 없음`.
- `DECISION_REQUIRED/ON_HOLD`이면 `추가 이동안 N건`.
- 실행 후보가 없으면 `이동안 없음`과 첫 blocking reason의 짧은 표시, 나머지는
  `외 N건`으로 보인다.
- `COMPLETED`이면 후보 count보다 `처리 완료`를 우선 표시한다.
- CTA는 모두 `검토하기`다.
- 행 전체 click handler를 붙이지 않고 한 개의 명시적 button/link로 상세 진입한다.

긴급 `NO_TRANSFER_OPTION` 행은 붉은 우선도 badge를 유지하되 `이동안 없음` 상태가 먼저
보여야 하며 default `이동 결정 필요` tab에는 포함되지 않는다.

### 7.6 목록 refresh

결정 저장 성공 후 현재 run/filter/sort/page를 다시 조회한다. 갱신 결과로 현재 page가
비면 이전 유효 page로 한 번 이동한다. 무한 retry하지 않는다. 선택 metric이 여전히
현재 결과에 있으면 상세을 최신 데이터로 유지하고, tab filter에서 빠지면 상세를 닫고
`처리 결과가 반영되었습니다` status message를 보여준다.

## 8. 상세 화면

### 8.1 object header

상세 header는 scroll 중 상단에 유지하고 다음만 먼저 보여준다.

- `매장명 · 상품명`
- `region · category / color / size · SKU`
- 처리 상태, 업무 우선도, 검토 사유
- 현재 판매가능재고, 입고·이동 반영 예상재고, 목표재고 대비 부족, 재고일수
- `← 처리 대상 목록` 또는 close 동작

run identity, raw 수요율, 관측일 수, source code는 header에 두지 않는다. 매출 노출액은
설명과 함께 보조지표로 표시한다. 상품 이미지 필드가 없으므로 가짜 placeholder나 외부
이미지를 만들지 않는다.

### 8.2 상세 tab

다음 네 tab을 사용한다.

1. `이동안 검토` — default
2. `판매·재고 근거`
3. `입고·매장이동`
4. `산출 기준 상세`

각 tab은 button/tabpanel semantics와 keyboard focus를 제공한다. 상세 metric이 바뀌면
default tab으로 돌아가고 이전 metric의 candidate, simulation, form state를 폐기한다.

### 8.3 이동안 검토 tab의 순서

```text
이미 반영 중인 물량
→ 출고 가능 매장
→ 선택 이동안의 추가 이동수량
→ 이동수량 비교
→ 이동 승인·보류·반려
→ 처리 이력
```

원시 28일 표, 이벤트 raw code, 정책 threshold가 이 순서보다 앞에 오면 안 된다.

### 8.4 이미 반영 중인 물량

detail의 `inboundSchedules`와 `openTransfers`를 요약한 panel을 후보보다 먼저 표시한다.

- 확정 입고: 수량, ETA, 상태
- 입고 방향 `APPROVED/IN_TRANSIT` 매장이동: 출고점, 수량, ETA, 상태
- 출고 방향 `APPROVED/IN_TRANSIT` 매장이동: 입고점, 수량, ETA, 상태
- `REQUESTED`: `승인 전 요청 · 추천수량에는 미반영 · 같은 경로 신규 승인 제한`

`APPROVED/IN_TRANSIT`에는 다음 문구를 한 번 표시한다.

> 아래 추가 이동 제안은 확정 입고와 승인됨·이동 중 수량을 이미 반영했습니다.

아무것도 없고 `MISSING_INBOUND`가 없으면 `현재 확정된 입고·매장이동 없음`, flag가 있으면
`입고 정보 확인 필요`를 표시한다. `—`만 단독으로 보여주지 않는다.

### 8.5 출고 가능 매장과 선택 규칙

`candidatesAsReceiver`를 기본 업무 목록으로 먼저 표시한다. `candidatesAsDonor`는
`이 매장에서 다른 매장으로 보내는 안`이라는 별도 접힘 section으로 표시한다.

후보 표의 열은 다음으로 줄인다.

- 출고/입고 매장
- 이동 가능 여부
- 추가 이동 제안
- 출고 가능 수량
- 예상 이동 기간·도착일
- 처리 상태
- 검토 동작

이동 조건과 부족수량은 선택 상세에 보이고 좁은 후보 표에 반복하지 않는다.

metric detail을 받은 뒤 다음 순서로 한 후보를 **검토 대상으로** 자동 선택한다. 이는
승인이 아니며 처리 버튼은 별도다.

1. `ELIGIBLE + RECOMMENDED + nonterminal` 첫 후보
2. 없으면 `ELIGIBLE + RECOMMENDED + terminal` 첫 후보
3. 없으면 `ELIGIBLE + COMPARISON_ONLY` 첫 후보
4. 없으면 첫 `REJECTED` 후보

Backend가 이미 candidate order를 안정적으로 반환하므로 Frontend가 새 업무 우선순위를
계산하지 않는다.

후보 CTA는 상태에 따라 다음과 같다.

- actionable: `이동안 검토`
- terminal: `처리 이력`
- comparison-only: `비교 보기`
- rejected: `이동 불가 사유`

rejected button이 선택 상태를 바꿔 사유를 보여줄 수는 있지만 수량·처리 form은 절대
렌더링하지 않는다. 선택된 행은 배경과 `선택됨` 보조 텍스트로 표시한다.

### 8.6 이동 불가·원인 확인 안내

실행 후보가 없으면 빈 결정 form 대신 다음 행동 panel을 표시한다.

| 사유 | 다음 행동 안내 |
|---|---|
| 재고 소유 정책 제한 | 재고 소유 정책 담당자에게 두 매장 간 예외 이동 가능 여부를 확인하세요. |
| 이동 경로 없음 | 물류 운영 담당자에게 두 매장 간 이동 경로 등록 여부를 확인하세요. |
| 도착 예정이 늦음 | 점간이동보다 확정 입고, 대체 매장 판매 또는 추가 발주 대응을 검토하세요. |
| 예정 입고로 부족 해소 | 확정 입고 도착 상태를 확인한 뒤 추가 이동 여부를 다시 판단하세요. |
| 출고 가능 재고 부족 | 다른 출고 매장 후보 또는 발주 대응을 검토하세요. |
| 진열재고 유지 기준 미충족 | 출고점 진열 기준을 변경하지 않는 한 다른 출고점을 검토하세요. |
| 입고점 수용 한도 초과 | 입고점 수용 한도와 보관 공간을 확인하세요. |
| 같은 경로 요청 진행 중 | 기존 요청의 승인·취소 결과를 확인한 뒤 재고 현황을 갱신하세요. |

`REVIEW_INPUT` 상세에서는 판매 흐름과 quality flag를 `왜 확인이 필요한가`로 묶고 다음
고정 안내를 제공한다.

- 판매 이력 부족: 판매 이력이 더 쌓인 뒤 다시 분석
- 일시 판매 급증: 행사·단체구매·오입력 여부 확인
- 재고 정보 지연: 최신 재고 수신 시각과 예약재고 확인
- 입고 정보 확인 필요: PO/ASN의 확정 여부와 ETA 확인
- 행사 정보 확인 필요: 행사 기간·대상 SKU·uplift 입력 확인

이 화면에는 존재하지 않는 `검토 완료`, `담당자 할당` 버튼을 만들지 않는다.

### 8.7 선택 이동안 요약

actionable 후보를 선택하면 후보 표 바로 아래에 다음을 한 덩어리로 표시한다.

- `출고 매장 → 입고 매장`
- 추가 이동 제안 수량
- 출고 가능 수량
- 목표재고 대비 부족
- 이동 조건: 최소 / 포장단위 / 최대
- 예상 이동 기간과 도착일
- 입고점 예상재고 전→후
- 출고점 예상재고 전→후

`recommendedQuantity`는 이미 반영 중인 물량 외에 필요한 **추가 이동**이라는 문구를
붙인다. 기존 open transfer와 새 제안을 합산해 새 값을 만들지 않는다.

### 8.8 이동수량 비교

11열 wide scenario table을 다음 7열로 축약한다.

1. 기준
2. 이동수량
3. 입고점 예상재고·재고일수
4. 입고점 상태
5. 출고점 예상재고·재고일수
6. 출고점 상태
7. 예상 도착·주의사항

행 순서는 `이동하지 않음`, `낮은 수요 기준`, `기준 수요`, `높은 수요 기준`이다.
BASE 행을 `기준 제안`으로 시각 강조한다. warningSummary 원문은 노출하지 않고 기존의
정해진 한국어 안내를 사용한다. 모든 숫자는 Backend 값을 그대로 표시한다.

375px에서는 각 scenario를 카드로 재구성할 수 있으나 동일 값과 의미를 보존한다.

### 8.9 판매·재고 근거 tab

- 기존 28일 chart와 정확한 표를 유지한다.
- chart 제목은 `최근 28일 판매량과 판매가능재고`다.
- legend를 추가해 두 선을 색만으로 구분하지 않는다.
- null을 0으로 바꾸지 않고 gap/`정보 없음`으로 표시한다.
- 표 column의 `재고`는 `실재고`, `예약`은 `예약재고`, source는 기본 접힘
  `데이터 출처` 아래로 옮길 수 있다.
- `Math.random()` key를 제거하고 날짜 또는 안정적인 index 결합 key를 사용한다.

### 8.10 입고·매장이동 tab

기존 demand event, inbound, open transfer를 다음 순서로 표시한다.

1. 확정 입고 일정
2. 진행 중 매장이동
3. 등록 행사·가격변경

empty state는 각각 `확정 입고 없음`, `진행 중 매장이동 없음`, `등록된 행사 없음`처럼
의미를 쓴다. `—`만 표시하지 않는다. source type과 assumption type은 각 표의
`데이터 출처 보기` disclosure로 이동한다.

### 8.11 산출 기준 상세 tab

다음 section을 기본 접힘으로 둔다.

- 적용 재고 정책
- 판매 흐름 산출 근거
- run/version identity
- 원본 데이터 출처

이 영역은 감사·문제조사용이며 primary 업무 흐름이 아니다. Backend 값을 그대로 표시하고
임계값을 Frontend에서 해석·재계산하지 않는다.

## 9. 수량 변경과 처리

### 9.1 자동 수량 검증

actionable 후보가 처음 선택되면 `recommendedQuantity`를 input에 채우고 동일 tuple로
MANUAL simulation을 한 번 자동 호출한다.

- 로딩: `추가 이동 제안을 확인하고 있습니다…`
- 성공 feasible: `이 수량으로 이동 가능`
- 실패: 위반 사유와 최대 가능수량·하향 제안을 표시
- 추천수량이 null/0이면 자동 호출하지 않고 처리 불가 안내
- candidate id/version/recommended quantity 조합당 한 번만 자동 호출해 effect loop를 막는다.
- 사용자가 input을 바꾸면 이전 simulation을 즉시 무효화하되 요청은 자동 전송하지 않는다.
- `변경 결과 확인`을 눌러야 새 simulation을 보낸다.
- input은 양의 정수만 허용하고 client validation text를 제공한다.

`APPROVED/IN_TRANSIT`가 존재하는 정상 후보에서 저장 추천수량과 같은 수량이
`PENDING_TRANSFER_CONFLICT`로 뒤집히는 것은 결함이다. 4.6 수정과 통합테스트로 막는다.

### 9.2 simulation 결과

기본 결과에는 다음만 보인다.

- 실행 가능 여부
- 추천 기준수량 / 입력 수량 / 최대 가능수량
- 입고점 판매가능재고 전→후와 재고일수
- 출고점 판매가능재고 전→후와 재고일수
- 예상 도착일

확정입고, open transfer inbound/outbound, 기승인 초안 수량의 raw breakdown은
`반영 내역 보기` disclosure에 둔다. `승인 시 최신 근거로 다시 확인합니다`를 평문으로
표시한다.

### 9.3 처리 action

radio + `제출` 조합을 제거하고 세 개의 명확한 button을 사용한다.

```text
[이동 승인] [보류] [이동안 반려]
```

- `이동 승인`은 matching feasible simulation, 담당자명, 필요한 사유가 있을 때만 enabled.
- `보류`, `이동안 반려`는 담당자명·사유 선택·설명이 있을 때 enabled.
- disable 상태 바로 아래에 정확한 이유를 한 문장으로 보여준다.
- terminal candidate는 buttons 대신 `이미 처리 완료된 이동안입니다`와 history만 표시한다.
- comparison-only/rejected 후보에는 이 section 자체를 렌더링하지 않는다.

### 9.4 담당자와 사유

`담당자 표시명 (데모 label...)`은 다음으로 교체한다.

```text
담당자명 (인증 연결 전 데모 입력값)
```

sessionStorage 동작과 100자 제한은 유지한다.

raw 사유 코드를 자유 입력하게 하지 않고 action별 한국어 select를 사용한다. 선택한 value를
기존 `reasonCode`로 전송한다.

승인 수량 변경·정책 예외용:

| Code | 표시 |
|---|---|
| `QTY_ADJUSTED` | 매장 상황에 맞춰 수량 조정 |
| `STORE_REQUEST` | 매장 요청 수량 반영 |
| `POLICY_EXCEPTION` | 정책 예외 검토 반영 |
| `OTHER` | 기타 |

보류용:

| Code | 표시 |
|---|---|
| `STORE_CONFIRMATION` | 매장 확인 대기 |
| `INBOUND_CONFIRMATION` | 입고 일정 확인 대기 |
| `MANAGER_REVIEW` | 관리자 검토 대기 |
| `DATA_CHECK` | 데이터 확인 필요 |
| `OTHER` | 기타 |

반려용:

| Code | 표시 |
|---|---|
| `TRANSFER_NOT_NEEDED` | 이동 불필요 |
| `STORE_CONSTRAINT` | 매장 운영 제약 |
| `PRODUCT_POLICY` | 상품 운영 정책 |
| `DATA_UNRELIABLE` | 데이터 신뢰 어려움 |
| `OTHER` | 기타 |

설명 label은 `처리 메모`다. 필수일 때 `무엇을 확인했거나 왜 수량을 바꿨는지
입력하세요`를 helper로 표시한다. 기준수량 그대로 일반 승인하면 사유영역은 기본 접힘
`메모 추가`로 둔다. policy exception checkbox는 `정책 예외로 승인`이고 다음 안내를
유지한다.

> 정책 예외는 사유 기록 방식이며 재고·경로·최신성 제약을 우회하지 않습니다.

### 9.5 승인 확인 대화상자

`이동 승인` click은 즉시 POST하지 않고 accessible modal을 연다.

```text
이 이동안을 승인할까요?
출고 매장 → 입고 매장
상품명 / SKU / 색상 / 사이즈
이동수량 N개
예상 도착일
입고점 예상재고 전 → 후
출고점 예상재고 전 → 후
승인하면 ERP 이동요청 초안이 생성됩니다. 실제 출고 완료는 아닙니다.
[취소] [N개 이동 승인]
```

focus trap, Escape/취소, 최초 focus, 닫힌 뒤 원래 button focus 복귀를 구현한다. 새 dialog
library는 사용하지 않는다. 보류·반려는 이미 사유를 입력하므로 별도 modal 없이 저장할
수 있다.

### 9.6 성공·실패·stale

- 승인 created: `이동 승인 완료 · ERP 이동요청 초안 #ID가 생성되었습니다.`
- 승인 replay: `이미 처리된 동일 요청 결과를 불러왔습니다.`
- 보류: `보류로 저장했습니다. 확인 후 다시 처리할 수 있습니다.`
- 반려: `이동안을 반려했습니다.`
- 모든 성공 message는 `role=status`, `aria-live=polite`, action별 결과를 정확히 사용한다.
- terminal 성공 즉시 form을 retire하는 기존 fail-closed 동작을 유지한다.
- `STALE_RECOMMENDATION`: simulation/form을 폐기하고 `재고 상황이 바뀌었습니다. 최신
  내용을 불러온 뒤 다시 검토하세요.`와 `최신 내용 불러오기`.
- `DECISION_ALREADY_TERMINAL`: form을 즉시 retire하고 canonical history를 다시 조회한다.
- retryable 오류만 같은 idempotency key/body로 재시도한다. field가 바뀌면 새 key를 만든다.
- 성공 뒤 detail과 list를 재조회하되 성공 message가 refresh 과정에서 사라지지 않게 한다.

### 9.7 처리 이력

기본 이력 행은 다음만 보인다.

```text
처리상태 · 수량 · 담당자 · 처리시각 · 사유
```

sequence, recommendation version, contract version, approval basis, draft payload는
`감사정보 보기`에 둔다. `이동지시 초안`은 모두 `ERP 이동요청 초안`으로 표시하고 상태가
`CREATED/READY`여도 실제 ERP 접수·출고 완료가 아님을 한 번 안내한다.

## 10. 접근성, 반응형과 오류 상태

- 모든 icon-only control에는 보이는 텍스트 또는 `aria-label`이 있다. 가능하면 텍스트를
  우선한다.
- tab, modal, disclosure, sortable header, table caption의 semantic을 지킨다.
- focus outline을 제거하지 않는다.
- loading skeleton에는 screen-reader status text가 있다.
- 목록·상세·simulation·history 오류는 서로의 정상영역을 지우지 않는다.
- API가 알 수 없는 future enum을 보내면 raw code fallback을 유지하되 화면은 crash하지
  않는다.
- null/unknown을 0, 정상, 입고 없음으로 임의 변환하지 않는다.
- 375px에서는 KPI를 2열, filter를 세로, 목록을 행별 compact block 또는 내부 scroll,
  상세을 단일 column으로 표시한다.
- 200% zoom에서도 주요 처리 button과 입력이 겹치거나 잘리지 않는다.
- `prefers-reduced-motion`에서 불필요한 transition을 제거한다.

## 11. 파일 변경 경계

다음은 허용 범위다. 필요한 새 class/component/test는 같은 package/folder에 만든다.

### 11.1 Frontend

- `frontend/src/App.tsx`
- `frontend/src/types.ts`
- `frontend/src/api.ts`
- `frontend/src/labels.ts`
- `frontend/src/formatters.ts`
- `frontend/src/styles.css`
- `frontend/src/components/AnalysisContext.tsx`
- `frontend/src/components/ExceptionFilters.tsx`
- `frontend/src/components/ExceptionList.tsx`
- `frontend/src/components/ExceptionDetail.tsx`
- `frontend/src/components/CandidateWorkbench.tsx`
- `frontend/src/components/ScenarioComparison.tsx`
- `frontend/src/components/DecisionPanel.tsx`
- `frontend/src/components/ObservationEvidence.tsx`
- 신규 `WorkQueueSummary`, `WorkStatusTabs`, `ImpactHelp`, `ApprovalConfirmDialog` 같은 소형
  component
- 위 파일들의 기존/신규 `*.test.ts(x)`

component 이름은 예시이며 기능을 불필요하게 한 파일에 몰지 않는 범위에서 조정할 수
있다. Redux/router/CSS framework를 추가하지 않는다.

### 11.2 Backend

- `analysis/InventoryExceptionController.java`
- `analysis/Mvp2InventoryExceptionQueryService.java`
- `analysis/Mvp2InventoryExceptionPage.java`
- `analysis/Mvp2InventoryExceptionListItem.java`
- `analysis/SpInventoryMetricRepository.java`
- 신규 `analysis/AllocatorWorkStatus.java`, summary record/projection/resolver
- `approval/CurrentApprovalBasisLoader.java`
- MANUAL/approval mode fail-closed에 직접 필요한 기존 service/command class
- 관련 repository method와 Backend test

### 11.3 문서

- `knowledge/business-rules.md`: work status, sort, open-transfer parity, comparison-only 처리
  불가 규칙 반영
- `knowledge/project.md`: 사용자 workflow와 용어 반영
- `README.md`: 사용자 workflow, 구현 기능, 화면 용어 반영
- `knowledge/state/current-task.md`, `knowledge/state/implemented-state.md`, active worklog

### 11.4 수정 금지

- `backend/src/main/resources/db/migration/**`
- `data/seed/**`
- JPA Entity column/schema
- AI/explanation package
- Docker/환경변수/비밀번호
- 실제 ERP/WMS/TMS integration
- unrelated archive 문서의 일괄 용어 치환
- 현재 dirty worktree의 관계없는 사용자 변경 되돌리기

README screenshot은 구현 후 실제 화면을 캡처할 수 있는 기존 수단이 있을 때만 새 화면으로
교체한다. 캡처를 위해 새 browser dependency를 설치하지 않는다. 교체하지 못하면 README
문구는 갱신하되 테스트 통과를 screenshot에 의존하지 않는다.

## 12. 테스트 명세

### 12.1 Backend unit test

다음을 각각 독립 테스트로 고정한다.

- work status: undecided executable → `DECISION_REQUIRED`
- work status: HELD executable만 존재 → `ON_HOLD`
- work status: terminal executable만 존재 → `COMPLETED`
- work status: REVIEW_REQUIRED/NON_ACTIONABLE + no executable → `REVIEW_INPUT`
- work status: stockout/overstock + no executable → `NO_TRANSFER_OPTION`
- 혼합 후보 precedence: undecided > HELD > terminal
- comparison-only는 work status 실행 후보가 아님
- blocking reason 중복 제거와 priority order
- sort enum/query validation과 기본 방향
- summary status 합 invariant와 unknown exposure count
- impact list/summary가 같은 산식을 사용

### 12.2 Backend Oracle integration test

`Mvp2InventoryExceptionReadOracleIT` 또는 동등한 IT에 다음을 추가한다.

- page JSON에 `summary`, item `workStatus`, `blockingReasons`가 non-null로 존재
- summary는 page/filter를 바꿔도 같은 run-wide 값
- 다섯 status count 합이 totalReviewTargets
- workStatus repeat filter 정확성
- 각 sort의 null-last, direction, stable pagination
- size=1 page walk가 size=100과 동일 순서
- query ceiling list ≤9/detail ≤14, size에 무관
- 잘못된 workStatus/sortBy/sortDirection의 RFC 9457 field error
- MVP-1 bare-array 응답 무변경

open-transfer parity fixture에서 다음을 모두 검증한다.

- same lane `REQUESTED` → `PENDING_TRANSFER_CONFLICT`, MANUAL infeasible
- same lane `APPROVED` → projection 수량에 한 번 포함, conflict 없음
- same lane `IN_TRANSIT` → projection 수량에 한 번 포함, conflict 없음
- `CANCELLED/RECEIVED` → projection과 conflict 모두 제외
- clean completed run의 모든 `ELIGIBLE + RECOMMENDED + nonterminal` 후보에서 저장
  `recommendedQuantity`를 동일 tuple로 MANUAL test하면 feasible=true
- comparison-only MANUAL 및 `HELD/APPROVED/REJECTED` 모두 fail closed하고
  decision/basis/draft row가 생기지 않음

### 12.3 Frontend test

기존 test를 새 용어와 구조로 갱신하고 다음을 추가한다.

- summary 5개 표시, tile action이 정확한 filter/sort request를 보냄
- default status tab과 0건 fallback
- filter 적용/reset/page reset, workStatus repeat query
- sortable header가 `aria-sort`와 Backend query를 변경하고 client sort를 하지 않음
- inbound null + MISSING_INBOUND → `입고 정보 확인 필요`
- inbound null + no flag → `확정 입고 없음`
- list no-transfer row의 blocking reason 표시
- detail open 시 first actionable candidate 자동 선택
- rejected/comparison-only/terminal 후보에 처리 form이 없음
- actionable selection의 recommended quantity 자동 simulation 1회
- input 변경 시 simulation invalidation, button click 후 새 test
- existing approved/in-transit 물량과 `추가 이동 제안` 문구 동시 표시
- matching feasible simulation 전 approval disabled와 구체 사유
- approval modal summary, cancel/focus, confirm POST
- action별 reason-code select와 required validation
- idempotency retry/stale/terminal 기존 안전 계약 유지
- decision success가 detail/list refresh callback을 호출하고 success text를 보존
- 처리 이력 기본/감사정보 disclosure
- 알 수 없는 enum fallback과 null 표시
- 기존 용어 금지 assertion: `MANUAL`, `선택`, `탈락`, `거절됨 제출`, `조치 불가`,
  `매출 영향`이 primary 업무 화면에 나타나지 않음

### 12.4 build와 전체 검증

Oracle이 필요한 full verification 전에 사용자의 실행 중 환경을 임의 삭제·reset하지 않는다.
다음 순서로 실행한다.

```powershell
.\scripts\local.ps1 seed-check
.\scripts\local.ps1 test-db-free
.\scripts\local.ps1 db-status
.\scripts\local.ps1 test
git diff --check
```

- `test-db-free`의 Oracle 조건부 skip은 기존 계약대로 허용하고 실제 합계를 기록한다.
- full `test`는 Oracle 0 skip, Frontend test, production build까지 실제 실행된 결과만 기록한다.
- 환경 때문에 full test를 실행하지 못하면 성공으로 쓰지 말고 DB-free/Frontend 등 실제로
  실행한 범위와 blocker를 정확히 보고한다.

## 13. 브라우저 수용 시나리오

`http://localhost:5173/`에서 실제 Backend와 연결해 다음을 확인한다. 기존 사용자의 synthetic
결정을 임의로 덮어쓰지 않는다. write가 필요한 성공 흐름은 통합테스트 fixture로 검증하고,
브라우저에서는 안전하게 남아 있는 nonterminal synthetic 후보가 있을 때만 저장한다.

### A. 아침 업무 시작

1. 재고 현황을 갱신하거나 완료된 run을 재사용한다.
2. summary에서 전체·긴급·이동 결정 필요·원인 확인·노출액을 본다.
3. default `이동 결정 필요` tab에는 실제 처리 가능한 대상만 나온다.
4. 노출액 tile을 눌러 전체 결과가 금액 내림차순으로 다시 조회된다.

### B. 이미 이동 중인 물량이 있는 상품

1. `시그니처 베이직 크루넥 티셔츠` 같은 open transfer 보유 항목을 연다.
2. 기존 승인/이동 중 2개가 `이미 반영 중인 물량`에 표시된다.
3. 후보 수량은 `추가 이동 제안`으로 표시된다.
4. 저장 추천수량 자동 test가 feasible이며 pending conflict로 0이 되지 않는다.
5. 양쪽 매장 전→후 재고와 예상 도착을 action 전에 확인한다.

### C. 긴급하지만 이동안이 없는 상품

1. 재고 소유 정책·이동기간 때문에 reject된 항목을 연다.
2. `이동안 없음`이 업무 우선도와 함께 명확히 보인다.
3. rejected 후보의 button은 `이동 불가 사유`이고 승인 form이 없다.
4. 풀어 쓴 사유와 다음 행동 안내가 보인다.
5. 이 항목은 default `이동 결정 필요` tab의 최상단을 차지하지 않는다.

### D. 원인 확인 대상

1. shortage 0/impact 0의 일시 급증·데이터 품질 항목을 연다.
2. `왜 확인이 필요한가`와 다음 확인 항목이 보인다.
3. 존재하지 않는 이동 승인 또는 가짜 `검토 완료` button이 없다.

### E. 처리

1. actionable 후보의 추가 이동수량을 바꾸고 `변경 결과 확인`을 누른다.
2. infeasible이면 구체 사유, 최대 가능수량, 하향 제안이 보인다.
3. feasible이면 담당자와 필요한 사유를 입력한다.
4. `이동 승인` modal에서 매장·상품·수량·도착·전후 재고·초안 경계를 확인한다.
5. 성공 후 초안 ID, 처리 이력, 목록 상태 변경을 확인한다.
6. stale/terminal 오류에서는 old form이 계속 활성화되지 않는다.

### F. 반응형·접근성

1. 1280px 이상 master-detail과 375px 단일 상세에서 page-level 가로 스크롤이 없다.
2. keyboard만으로 tab, sort, filter, 후보, modal, action에 접근한다.
3. modal focus가 빠져나가지 않고 닫으면 호출 button으로 돌아간다.
4. console error/warning과 unhandled rejection이 없다.

## 14. Definition of Done

다음이 모두 참일 때만 완료다.

- 2.1의 모든 항목 구현, 2.2의 범위는 추가하지 않음
- Backend list/summary/sort/work status 계약과 API tests 통과
- open-transfer parity 및 comparison-only fail-closed 회귀 테스트 통과
- Frontend primary 화면 용어가 5장 사전과 일치
- summary→filter→detail→candidate→simulation→decision→history 흐름 완결
- rejected/no-option/review-input/terminal/null/error/loading/mobile 상태 처리
- 실제 전체 test/build/diff 결과 기록
- README/business-rules/project와 hot state가 구현 사실에 맞게 갱신
- `stockpilot-worklog` entry가 20줄 이내로 변경·검증·남은 blocker를 기록
- 후속 TODO, 임시 mock, 임시 Frontend 완료상태, 미사용 dependency가 없음

구현 보고는 다음 형식으로 끝낸다.

```text
완료 범위:
핵심 업무 변화:
API/규칙 변화:
실행한 검증과 실제 결과:
브라우저 수용 결과:
미해결 blocker: 없음 또는 구체 근거
```

## 15. 실서비스 UI 참고 출처와 적용 범위

이 설계는 특정 기업 내부 화면을 복제하지 않는다. 공개된 공식 문서에서 검토·승인형
enterprise workflow 패턴만 가져온다.

- [Oracle Retail Review and Approve](https://docs.oracle.com/en/industries/retail/retail-inventory-planning-optimization-cloud/24.2.301.0/ipodl/ch-Review-Approve.htm)
  - 적용: 승인 대기 count, To-Do List, alert 기반 filter, 상품·매장 단위 검토.
- [Oracle Retail Inventory Optimization](https://docs.oracle.com/en/industries/retail/retail-inventory-optimization-cloud-service/22.2.302.0/inoug/inventory-optimization.htm)
  - 적용: rebalancing transfer summary, recommendation 검토·override·submit/approve 흐름.
- [SAP Allocation Management — Fiori Apps](https://help.sap.com/docs/CARAB/410a12785a4945dca77e6afba0970c93/e6e1c1174e4e4e6dafcd612e1a2fe8c4.html)
  - 적용: workload, allocation plan, result를 분리한 업무 구조.
- [SAP Allocation Plan V2](https://help.sap.com/docs/CARAB/410a12785a4945dca77e6afba0970c93/af9bfef59a19497c823735d1b84876b0.html)
  - 적용: plan header, 상품·색상, 점포, allocation quantity와 exception 중심 검토.
- [SAP Fiori Work List](https://experience.sap.com/fiori-design-web/work-list/)
  - 적용: count가 있는 worklist, filter/search, 반복 처리 가능한 밀도.
- [SAP Fiori Object Page](https://experience.sap.com/fiori-design-web/object-page/)
  - 적용: 선택 object header, 중요 section 우선, 상세 근거의 계층화.
- [SAP Fiori Action Placement](https://experience.sap.com/fiori-design-web/action-placement/)
  - 적용: primary action의 일관된 위치와 destructive/secondary action 구분.

StockPilot에 없는 상품 이미지, mass edit, 조직 권한, 실제 ERP status는 참고 화면에
존재하더라도 이번 구현에 가져오지 않는다.
