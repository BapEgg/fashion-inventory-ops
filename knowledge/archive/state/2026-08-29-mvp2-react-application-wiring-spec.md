# MVP-2 React Application Wiring Specification

Status: approved for implementation
Current role: Claude implementation
Last updated: 2026-08-29

## Goal

이미 accepted된 MVP-2 Backend API를 본사 재고 배분·보충 담당자가 한 흐름에서 사용할 수
있는 React 업무 화면으로 연결한다.

1. 합성 입력 버전으로 분석을 시작하고 완료 상태를 확인한다.
2. 완료된 run의 재고 예외 queue를 필터·페이지 단위로 검토한다.
3. store-SKU의 28일 근거, 데이터 품질, 입고·이동, 후보와 자동 시나리오를 확인한다.
4. 선택 수량을 부작용 없이 `MANUAL` 시험한다.
5. `HELD`/`APPROVED`/`REJECTED`를 저장하고 append-only 이력과 이동지시 초안을 확인한다.

이번 slice는 React application wiring과 그 검증만 포함한다. Backend API/DB schema/업무
계산식은 변경하지 않는다. 화면은 Java 결과를 표시하고 요청을 조립할 뿐 수량, 후보 적격성,
위험 또는 결정 상태를 자체 계산하지 않는다.

## Accepted baseline and current gap

- Backend에는 다음 MVP-2 계약이 accepted되어 있다.
  - `POST /api/analyses`, `GET /api/analyses/{analysisRunId}`
  - run-bound `GET /api/inventory-exceptions`와 MVP-2 상세
  - version tuple을 받는 `POST /api/rebalancing-simulations`
  - idempotent `POST /api/rebalancing-decisions`와 결정 이력 GET
- 현재 Frontend는 MVP-1 bare-array 목록과 legacy simulation/decision shape만 사용한다.
  MVP-2 page envelope, 28일 근거, 후보별 scenario, `HELD`, version tuple, idempotency,
  decision history와 ProblemDetail을 알지 못한다.
- 현재 기준 `pnpm --dir frontend build`는 `frontend/vite.config.ts`의 `process.env.PORT`에
  필요한 top-level `@types/node`가 없어 `TS2580`으로 실패한다. 구현 첫 단계에서
  `@types/node`를 dev dependency로 명시하고 lockfile을 갱신한다.
- 공식 합성 시나리오 preset은 `analysisDate=2026-09-30`,
  `inputSnapshotVersion=MVP-2-GS-V1`이다. 이는 실제 운영 기준일이나 실제 기업 데이터가
  아니라 화면 연결 검증용 `SYNTHETIC` 입력이다.

## 1. 화면과 상태 흐름

별도 router/state library를 추가하지 않고 한 React application 안에서 다음 상태 흐름을
명시적으로 관리한다.

```text
분석 컨텍스트 입력
  -> 분석 실행/상태 확인
  -> COMPLETED run-bound 예외 queue
  -> store-SKU 상세
  -> 후보 선택
  -> 자동 scenario 비교 + MANUAL 수량 시험
  -> 보류/승인/거절
  -> canonical 결정 이력 조회
```

- `analysisRunId`가 없는 동안에는 run-bound 목록 API를 호출하지 않는다.
- 사용자가 분석일이나 입력 버전을 수정해도 자동으로 legacy 목록을 호출하지 않는다.
  명시적인 `분석 실행` 후 Backend가 돌려준 run identity를 사용한다.
- 새 run을 선택하거나 실행하면 이전 목록, page, 선택 metric, 선택 candidate, simulation,
  decision form, pending retry를 모두 초기화한다.
- 목록 filter/page 변경 시 상세를 닫고 이전 목록·상세 요청을 abort한다.
- 상세를 닫아 목록으로 돌아갈 때는 현재 run/filter/page를 보존한다.
- 화면 새로고침 후 run을 자동 실행하거나 decision을 복원하지 않는다. 이번 범위에서는
  서버 상태를 바꾸는 자동 요청보다 명시적 사용자 동작을 우선한다.

## 2. API/type boundary

### 2.1 ProblemDetail

`api.ts`는 실패 응답을 문자열 하나로 축약하지 않는다. 아래 응답을 안전하게 판별해
`ApiError`에 보존한다.

```ts
interface ApiFieldError {
  field: string
  code: 'REQUIRED' | 'SIZE' | 'FORMAT' | 'FORBIDDEN' | string
  message: string
}

interface ApiProblem {
  type: string
  title: string
  status: number
  detail: string
  instance: string
  code: string
  retryable: boolean
  requestId: string
  timestamp: string
  fieldErrors?: ApiFieldError[]
}
```

- JSON이 위 shape가 아니거나 응답 body를 읽을 수 없으면 status 기반의 안전한 한국어
  fallback을 사용한다. HTML, raw body 또는 stack text를 화면에 그대로 출력하지 않는다.
- `ApiError`는 최소 `status`, `code`, `title`, `detail`, `retryable`, `requestId`,
  `fieldErrors`를 가진다.
- 정상 response DTO는 Backend record를 그대로 mirror한다. nullable scalar와 non-null array를
  임의로 바꾸지 않는다.

### 2.2 Analysis API

```http
POST /api/analyses
Content-Type: application/json

{
  "analysisDate": "2026-09-30",
  "inputSnapshotVersion": "MVP-2-GS-V1"
}
```

`ruleVersion`은 보내지 않는다. 응답의 모든 필드(`analysisRunId`, `analysisDate`,
`inputSnapshotVersion`, `ruleVersion`, `status`, `alreadyCompleted`, `startedAt`,
`completedAt`)와 status GET shape를 type에 반영한다.

### 2.3 Run-bound list API

목록은 반드시 `analysisRunId`를 보내고 다음 parameter를 지원한다.

- repeatable: `exceptionType`, `severity`, `signal`, `confidence`, `qualityFlag`
- scalar: `storeId`, `skuId`, `hasExecutableCandidate`, `page`, `size`

배열은 comma-separated 값이 아니라 같은 query key를 `URLSearchParams.append`로 반복한다.
응답은 `Mvp2InventoryExceptionPage` envelope와 그 `items`를 그대로 사용한다. 목록에서 후보
하나를 임의로 대표 추천으로 선택하지 않는다.

### 2.4 Detail, simulation and decision API

- 상세: `GET /api/inventory-exceptions/{inventoryMetricId}`
- 수량 시험 body:

```json
{
  "recommendationId": 101,
  "requestedQuantity": 12,
  "analysisRunId": 20,
  "inputSnapshotVersion": "MVP-2-GS-V1",
  "ruleVersion": "MVP-2",
  "candidateVersion": 1
}
```

- 결정 body는 같은 version tuple과 `decisionStatus`, status별 `selectedQuantity`,
  `policyException`, `reasonCode`, `reason`, `actorLabel`을 보낸다.
- MVP-2 decision POST는 `Idempotency-Key` header를 정확히 하나 보낸다.
- canonical 이력은 성공 직후와 상세 후보 선택 시
  `GET /api/rebalancing-decisions/{recommendationId}`로 조회한다.
- Frontend에서 legacy body로 fallback하거나 누락 tuple을 추천 응답에서 추측해 채우는 경로를
  만들지 않는다.

## 3. 분석 실행 영역

분석 영역은 큰 hero/card가 아니라 현재 데이터 컨텍스트를 바꾸는 compact toolbar다.

- 입력: `분석 기준일`, `입력 스냅샷 버전`, `분석 실행`.
- 최초 값은 공식 합성 preset을 사용하며 옆에 `데모 preset`이라고 명시한다.
- 입력 버전은 required, 최대 64자다. 공백만 있거나 앞뒤 공백을 포함하면 submit 전에
  일반적인 입력 오류를 보여줄 수 있지만 Backend normalization 규칙을 재구현하지 않는다.
- POST 결과가 `COMPLETED`면 즉시 그 `analysisRunId`로 첫 page를 조회한다.
- 결과가 `RUNNING`이면 같은 run id의 GET을 1.5초 간격으로 최대 40회 확인한다.
  - `COMPLETED`: polling 종료 후 목록 조회
  - `FAILED`: polling 종료 후 실패 상태 표시
  - 40회 후에도 `RUNNING`: 실패로 바꾸지 않고 `계속 실행 중`과 수동 `상태 새로고침` 제공
- unmount, 새 분석 실행 또는 입력 컨텍스트 변경 시 기존 polling/request를 abort한다.
- `alreadyCompleted=true`는 `기존 완료 결과를 불러왔습니다`, 신규 완료는
  `분석이 완료되었습니다`로 구분한다.
- run context에는 run id, 기준일, 입력 버전, 규칙 버전, 완료시각과 상태를 표시한다.

## 4. 재고 예외 queue

### 4.1 Filter

다음 Backend filter를 노출한다.

- 예외 유형: 품절 위험, 과잉 재고, 검토 필요, 조치 불가
- 심각도: 긴급, 높음, 검토
- 수요 신호
- 신뢰도
- 품질 경고
- 실행 가능한 후보: 전체/있음/없음
- 정확 일치 `매장 ID`, `상품 SKU`
- page size: 20/50/100

enum group은 다중 선택을 지원하고 API에는 repeatable query로 보낸다. `필터 적용` 시 page를
0으로 되돌리고, `초기화`는 모든 filter와 page size를 기본값으로 복원한다. 입력할 때마다
요청하지 않고 적용 버튼으로 한 번에 조회한다.

### 4.2 Table

업무 우선순위 판단에 필요한 열만 첫 화면에 둔다.

- 심각도/예외 유형
- 매장명과 region
- 상품명, color/size와 SKU
- 현재 가용재고
- 재고 보유일수
- 예상 부족수량
- 가장 빠른 확정 입고와 수량
- 수요 신호/신뢰도/품질 경고
- 실행 가능·비교용·탈락 후보 수
- 예상 매출 영향(정렬 참고값)

Backend 고정 정렬을 그대로 표시하고 Frontend client-side 재정렬을 하지 않는다.
`hasExecutableCandidate=false`인 행도 숨기지 않는다. 빈 page는 `조건에 맞는 재고 예외가
없습니다`와 filter 초기화 동작을 제공한다. pagination은 이전/다음과 현재 page/전체 page,
전체 건수를 표시하며 `hasPrevious`/`hasNext`를 신뢰한다.

한 행은 button/link 한 개로 상세 진입이 가능해야 하고 keyboard focus가 보여야 한다. table
행 전체에 중첩 click handler를 붙여 button 의미를 잃게 만들지 않는다.

## 5. 재고 예외 상세

상세는 다음 순서의 업무 문서형 화면으로 구성한다.

1. store/product identity와 주요 결과
2. 28일 판매·재고 근거
3. 이벤트·입고·진행 중 이동
4. 적용 정책과 분류 근거
5. 공급 후보와 scenario/결정

### 5.1 Summary

- 매장명, region, 상품명, category/color/size, SKU
- 예외 유형, 심각도, 수요 신호, 신뢰도, quality flags
- 현재/예상 가용재고, 예상 부족수량, 재고 보유일수
- low/base/high 수요율과 관측일·활성주·판매일 비율
- run identity와 calculation version

신뢰도는 확률이 아니므로 `%`로 렌더링하지 않는다. `NONE`은 `산정 불가`로 표시한다.

### 5.2 Observation evidence

- 28개 날짜를 오래된 순으로 사용한다.
- 판매수량과 가용 재고(`onHandQuantity - reservedQuantity`)의 시각적 추이는 작은 SVG chart로
  표시해도 되지만, 좌표 scaling은 표현 전용이며 원본값을 변경하지 않는다.
- exact 값은 접근 가능한 표로 함께 제공한다: 날짜, 재고, 예약, 품절, 판매수량, 거래건수,
  최대 거래수량, 평균 판매가, source.
- 누락은 `0`으로 바꾸지 않고 `—`, `outOfStock=true`는 명시적인 `품절 관측`으로 표시한다.

### 5.3 Related evidence and assumptions

- demand events, inbound schedules, open transfers는 서로 다른 표로 표시한다.
- event uplift가 null이면 `—`이며 임의 uplift를 계산하지 않는다.
- inbound/transfer의 status와 방향은 한국어 label로 표시하되 원본 code를 tooltip 또는 보조
  텍스트로 확인할 수 있게 한다.
- policy의 `source=DEFAULT_ASSUMPTION`은 `기본 데모 가정 적용` 경고를 별도로 표시한다.
- rule threshold는 기본 접힘 `계산 기준 보기` 영역에 Backend 값 그대로 표시한다.

## 6. 후보와 scenario 비교

`candidatesAsReceiver`와 `candidatesAsDonor`를 분리하고 각 목록에서 사용자가 하나의
candidate를 선택한다. 자동으로 첫 후보를 승인 대상으로 선택하지 않는다.

- 후보 행: 상대 매장, 방향, candidate status, recommendation mode, 추천수량, 부족수량,
  donor 이동 가능량, route 최소/배수/최대, lead time, 평가시각, 최신 결정 상태.
- `REJECTED` 후보는 모든 rejection reason을 priority 순으로 표시하고 시험/결정 버튼을
  disable한다.
- `COMPARISON_ONLY`는 `비교용 후보`라고 명시하고 실행 가능한 기본 추천처럼 꾸미지 않는다.
- terminal 최신 상태(`APPROVED`, `REJECTED`, `EXPIRED`)에서는 새 시험·결정 입력을 disable하고
  이력만 조회한다. `PENDING`, `HELD`는 계속 결정 가능하다.

선택한 후보의 저장 scenario는 `NO_ACTION`, `CONSERVATIVE`, `BASE`, `AGGRESSIVE` 순의 비교
열로 표시한다.

- 이동수량과 수요율
- receiver/donor 이동 전후 가용재고와 재고 보유일수
- receiver/donor 위험 code
- lead time, 예상 도착, 입고 반영 여부
- Backend warning 존재 여부

`warningSummary`는 현재 Backend 내부 설명이 영어 진단 문자열이므로 그대로 사용자에게
노출하거나 parsing하지 않는다. non-null이면 `최소 이동수량·포장단위 조건으로 실행할 수
없는 시나리오입니다`라는 한국어 안내와 route 수치를 표시한다.

수요 신뢰도는 metric 단일 값이다. 후보·scenario마다 서로 다른 confidence가 있는 것처럼
복제하지 않고 비교 영역 header에 한 번 표시한다.

## 7. MANUAL 수량 시험

- 추천수량 또는 선택한 자동 scenario 수량을 `시험 수량` 입력의 시작값으로 가져올 수 있다.
  가져온 뒤에도 사용자가 직접 수정할 수 있다.
- 입력은 양의 정수만 허용하며 Frontend가 package multiple에 맞춰 반올림하거나 최대값으로
  clamp하지 않는다.
- candidate identity와 detail의 run tuple을 모두 보내 simulation API를 호출한다.
- 입력 수량, candidate 또는 run context가 바뀌면 이전 simulation 결과를 즉시 무효화한다.
- `feasible=false`는 API 오류가 아니라 정상 결과다. 모든 `violations`, candidate rejection
  reasons, 최대 가능수량, 하향 제안수량과 route/donor/capacity limit를 표시한다.
- `suggestedQuantity`는 버튼으로 입력란에 복사할 수 있지만 자동 재시험·자동 승인을 하지
  않는다.
- `feasible=true`일 때만 projection을 표시한다. 양쪽 재고·보유일수·위험, 도착일과 inbound/
  open-transfer/approved-draft 근거를 모두 보여준다.
- `approvalRevalidationRequired=true`와 `assumption.notice`를 decision 영역 바로 위에 표시한다.
  simulation 결과를 approval token처럼 보내거나 재검증 생략 근거로 사용하지 않는다.

## 8. 결정과 이력

### 8.1 Action shape

- `HELD`: 선택수량은 보내지 않고 사유 코드·설명 필수
- `REJECTED`: 선택수량은 보내지 않고 사유 코드·설명 필수
- `APPROVED`: 현재 candidate/version/수량과 정확히 일치하는 최신 simulation이
  `feasible=true`일 때만 화면 버튼 활성화
- `APPROVED`의 `selectedQuantity`는 입력란을 다시 읽지 않고 그 simulation response의
  `requestedQuantity`를 사용한다.
- `simulation.reasonRequired=true` 또는 `policyException=true`이면 승인에도 사유 코드·설명을
  필수로 받는다. exact BASE이고 policy exception이 아니면 두 필드는 선택이다.
- `policyException`은 승인에서만 노출한다. 이는 numeric/candidate/stale 제약을 우회한다는
  표현을 하지 않는다.

reason code의 허용값 catalog는 현재 Backend에 없다. Frontend가 실제 기업 정책처럼 보이는
고정 code 목록을 발명하지 않고 최대 40자의 자유 입력을 받으며 `데모 감사 코드`라고
표시한다. `actorLabel`도 인증 사용자 ID가 아니라 최대 100자의 caller-supplied 데모 label임을
명시한다. 성공 후 actor label만 browser session 동안 편의상 유지할 수 있지만 사용자 계정으로
표현하지 않는다.

### 8.2 Idempotency

- 제출 직전에 `crypto.randomUUID()`로 key를 한 번 생성한다.
- payload와 key를 immutable pending request로 함께 보존한다.
- network failure 또는 `retryable=true` ProblemDetail 후 `같은 요청 다시 시도`는 보존한
  payload와 같은 key를 재전송한다.
- decision field, candidate, simulation 또는 run이 변경되면 pending request를 폐기한다.
  새 사용자 의도는 새 key를 사용한다.
- double click은 disabled 상태로 막는다. 자동 retry는 하지 않는다.
- Frontend는 Backend fingerprint 알고리즘을 복제하지 않는다.

### 8.3 Success and canonical history

- 신규 `created=true`는 저장 완료와 sequence를 표시한다.
- replay `created=false`는 `이미 처리된 동일 요청 결과를 불러왔습니다`라고 표시한다.
- APPROVED는 `transferDraftId`, HELD/REJECTED는 상태와 sequence를 표시한다.
- 성공 직후 POST body만으로 상세을 조립하지 않고 decision-history GET을 다시 읽는다.
- history는 sequence 오름차순 timeline/table로 렌더링한다. 상태, 선택수량, 정책 예외, 사유,
  actor label, decidedAt을 표시한다.
- APPROVED item의 approval basis와 transfer draft를 접을 수 있는 감사 상세로 표시한다.
  draft는 `CREATED/READY/...` 현재 상태일 뿐 실제 ERP 전송 완료처럼 표현하지 않는다.

## 9. Error recovery and asynchronous safety

목록, 상세, simulation, decision, history 오류 상태를 하나의 전역 문자열로 공유하지 않는다.
각 작업 위치 가까이에 `ProblemAlert`를 표시한다.

- `VALIDATION_ERROR`: `fieldErrors`를 해당 form 요약과 필드 근처에 표시
- `ANALYSIS_RESULTS_NOT_READY`: run 상태 확인 동작 제공
- `STALE_RECOMMENDATION`: simulation/pending decision 폐기, 상세 새로고침 동작 제공
- `DECISION_ALREADY_TERMINAL`: decision form 폐기, 이력·상세 새로고침
- `APPROVAL_LOCK_TIMEOUT`, `PERSISTENCE_UNAVAILABLE` 또는 `retryable=true`: 동일 pending
  decision 재시도 또는 해당 GET 재조회 동작 제공
- `IDEMPOTENCY_KEY_REUSED`: 동일 key 자동 재사용을 중단하고 사용자가 form을 확인한 뒤 새
  요청을 만들도록 안내
- 그 밖의 오류: title/detail과 request id를 표시하고 raw payload는 표시하지 않는다.

abort된 요청은 오류로 렌더링하지 않는다. 모든 비동기 state update는 해당 request가 여전히
최신 run/filter/metric/candidate인지 확인한다. 오래된 detail/simulation/history response가
현재 화면을 덮어쓰지 않아야 한다.

## 10. Korean presentation and visual rules

이 화면은 portfolio landing page가 아니라 운영 담당자용 workbench처럼 보여야 한다.

- 넓은 desktop table와 명확한 정보 계층을 사용하고, 모든 영역을 동일한 둥근 card로 쪼개거나
  거대한 제목·gradient·과도한 pill badge를 사용하지 않는다.
- 강조색은 primary action에만, 빨강/주황은 위험·오류, 초록은 실행 가능·완료 상태에만 쓴다.
- 매장명·상품명은 Backend의 한국어 catalog 값을 우선 표시하고 ID는 보조 정보로 둔다.
- enum은 `labels.ts`의 exhaustive 한국어 map으로 변환한다. unknown runtime code는 숨기거나
  crash하지 않고 원본 code를 fallback으로 표시한다.
- 수량은 `Intl.NumberFormat('ko-KR')` 정수 형식이다.
- 재고 보유일수는 비율 값이므로 업무 판단용 정밀도를 남겨 최대 소수 1자리로 표시한다.
  예: `1.25` -> `1.3일`, `70.00` -> `70일`. 이는 표시 반올림일 뿐 request나 비교에
  재사용하지 않는다. null은 `산정 불가`다.
- 수요율은 최대 소수 2자리, 금액은 KRW 정수 통화, 날짜/시각은 `ko-KR`로 표시한다.
- null은 `0`이 아니라 `—` 또는 의미가 확정된 `산정 불가`로 표시한다.
- 모바일에서는 table을 임의 카드 목록으로 재구성하지 않고 가로 scroll과 핵심 요약을
  제공한다. action form은 한 열로 쌓는다.

모든 화면 상단에 다음 문구가 지속적으로 보여야 한다.

```text
SYNTHETIC 데이터 · ASSUMPTION 데모 정책 · 실제 F&F 정책 또는 검증된 산업 표준 아님
```

상세의 정책·rule·simulation에도 해당 assumption notice를 가까이 표시한다.

## 11. Implementation structure

기존 파일을 MVP-2 contract로 교체하되 미래용 빈 폴더는 만들지 않는다. 아래 구조는 책임
경계이며 필요하면 이름을 소폭 조정할 수 있다.

- `src/types.ts`: Backend DTO, request, ProblemDetail union/type
- `src/api.ts`: typed request, repeated query parameter, ProblemDetail parsing, all MVP-2 calls
- `src/labels.ts`: enum별 한국어 label과 unknown fallback
- `src/formatters.ts`: quantity/coverage/rate/money/date nullable formatter
- `src/App.tsx`: run context와 list/detail top-level state only
- `src/components/AnalysisContext.tsx`
- `src/components/ExceptionFilters.tsx`
- `src/components/ExceptionList.tsx`
- `src/components/ExceptionDetail.tsx`
- `src/components/ObservationEvidence.tsx`
- `src/components/CandidateWorkbench.tsx`
- `src/components/ScenarioComparison.tsx`
- `src/components/DecisionPanel.tsx`
- `src/components/ProblemAlert.tsx`

거대한 한 component에 모든 DTO를 render하지 않고, 반대로 label 한 줄마다 component를
만들지도 않는다. React context/global store/router/chart library는 이번 범위에 추가하지 않는다.

## 12. Tests and completion conditions

### 12.1 Test setup

`vitest`, `jsdom`, `@testing-library/react`, `@testing-library/jest-dom`,
`@testing-library/user-event`, `@types/node`를 dev dependency로 추가한다. package script는
최소 `test`(`vitest run`)를 제공하고 `pnpm-lock.yaml`을 함께 갱신한다.

### 12.2 API/formatter tests

- analysis POST는 `ruleVersion` 없이 exact MVP-2 body를 보내고 status GET을 mapping한다.
- list arrays는 repeated query key이고 scalar/page는 exact하다.
- simulation/decision은 complete tuple을 보내며 decision header는 정확히 한 key다.
- decision retry는 같은 immutable body/key, form 변경 후 새 intent는 새 key다.
- RFC 9457 body와 malformed/non-JSON error fallback, abort 판별을 검증한다.
- coverage `1.25 -> 1.3일`, `70.00 -> 70일`, null -> `산정 불가`; quantity/money/null
  formatting을 검증한다.

### 12.3 Component/application tests

- 분석 COMPLETED/replay와 RUNNING polling/FAILED/timeout UI
- run 완료 전 list 미호출, 완료 후 `analysisRunId` 기반 page 조회
- filter repeated params, page 전환, empty/error/retry와 stale response 억제
- 상세의 28일 evidence, null/OOS/quality/assumption, 후보·탈락 사유·scenario 순서
- candidate를 사용자가 선택하기 전 decision target이 생기지 않음
- infeasible simulation은 모든 violation/suggestion을 보이고 approve disabled
- feasible simulation과 같은 수량에서 approve enabled, 수량/candidate/run 변경 시 invalidation
- HELD/REJECTED/APPROVED body shape와 reason-required/policy-exception UI
- retryable decision은 same key/body, success 후 canonical history refresh
- terminal 상태 form disabled, approval basis/draft audit 표시
- keyboard label/name, focusable action과 `aria-live` loading/success/error 상태

### 12.4 Final verification

- `pnpm --dir frontend test`
- `pnpm --dir frontend build`
- 관련 Backend public contract target은 변경하지 않았음을 diff로 확인한다. Backend 파일이
  변경됐다면 범위 위반으로 Codex review에 돌려보낸다.
- `git diff --check`

실행하지 않은 테스트를 성공으로 기록하지 않는다. Frontend 구현 중 Backend response/API 또는
DB schema 변경이 필요해 보이면 임의 수정하지 않고 Codex planning으로 돌려보낸다.

## 13. Out of scope

- Backend API, Java 계산·상태전이·오류 catalog와 Flyway 변경
- AI explanation endpoint/provider와 AI가 결정하는 수량·상태
- 인증/인가, 담당자별 지점 배정, actorLabel의 사용자 계정화
- scheduler/stale RUNNING recovery
- transfer draft READY 전이와 ERP/WMS/TMS 전송
- 실시간 push/websocket, URL deep link, client-side cache/global state library
- portfolio README 작성과 배포 설정 변경

## Next verifiable action

Claude가 먼저 type/API/ProblemDetail과 test setup을 고정한 뒤 분석→목록→상세→후보/scenario→
MANUAL→결정/이력 순으로 연결한다. 실제 frontend test/build 결과와 변경 파일을 기록한 후
Codex가 Backend 계약 재구현 여부, stale/idempotency 안전성, 화면 완료 조건을 독립 검증한다.
