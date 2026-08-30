# MVP-2 Approval/Decision REST Specification

Status: approved for implementation
Current role: Claude implementation
Last updated: 2026-08-28

## Goal

이미 accepted된 `ApprovalTransactionFacade`를 공개 REST에 연결하고, recommendation별
append-only 결정 이력과 승인 근거·이동지시 초안을 조회하는 API를 완성한다.

- `POST /api/rebalancing-decisions`: `HELD`/`APPROVED`/`REJECTED` 저장, idempotent replay,
  최신 근거 재검증과 원자적 승인 저장
- `GET /api/rebalancing-decisions/{recommendationId}`: 논리적 현재 상태, 전체 결정 이력,
  승인 근거와 이동지시 초안 조회

이번 slice는 Backend REST와 Oracle 검증까지다. React 화면, 인증, 실제 ERP 전송,
draft `READY` 전이와 AI 설명은 범위 밖이다.

`actorLabel`은 인증된 사용자 ID가 아니라 데모용 caller-supplied 감사 label이다.
reason code도 이번 범위에서는 허용값 catalog가 없는 정규화 문자열이며 실제 기업 정책을
안다고 가정하지 않는다.

## Accepted baseline

- `ApprovalTransactionCommand`/`Facade`/`Executor`/`Reader`와 current-basis loader는 accepted다.
- write transaction은 recommendation → donor snapshot 순으로 잠그고, `APPROVED` decision,
  approval basis와 transfer draft를 한 원자적 transaction으로 저장한다.
- 같은 idempotency key·같은 normalized payload는 기존 결과를 replay하고, 다른 payload는
  `IDEMPOTENCY_KEY_REUSED`다.
- `HELD`에서는 새 `HELD`/`APPROVED`/`REJECTED`가 가능하고
  `APPROVED`/`REJECTED`/`EXPIRED`는 terminal이다.
- `MANUAL` 수량 시험과 실제 승인은 같은 current-basis 재계산을 사용하지만 시험 결과나
  token을 승인 요청에 전달해 재검증을 생략하지 않는다.
- V10/V11 error catalog와 `AnalysisApiExceptionHandler`의 RFC 9457 responder를 재사용한다.
- 기존 MVP-1 `POST /api/rebalancing-decisions` 성공 계약은 보존한다.

## 1. POST 요청과 MVP-1 호환 분기

기존 `RebalanceDecisionRequest`를 additive superset으로 확장한다. 상태 필드 이름은 새
`status`를 만들지 않고 기존 JSON의 `decisionStatus`를 그대로 사용한다.

| Field | Legacy MVP-1 | MVP-2 |
|---|---|---|
| `recommendationId` | required positive | required positive |
| `decisionStatus` | `APPROVED`/`REJECTED` | `HELD`/`APPROVED`/`REJECTED` |
| `selectedQuantity` | required positive | `APPROVED`: positive, 그 외: `null` |
| `reason` | required nonblank | 상태·수량에 따라 conditional |
| `actorLabel` | required nonblank | normalized nonblank, max 100 |
| `analysisRunId` | absent | required positive |
| `inputSnapshotVersion` | absent | normalized nonblank, max 64 |
| `ruleVersion` | absent | normalized nonblank, max 32 |
| `candidateVersion` | absent | required positive |
| `policyException` | absent | optional, omitted=`false` |
| `reasonCode` | absent | conditional, normalized max 40 |

MVP-2 요청은 HTTP header `Idempotency-Key`를 정확히 한 개 포함해야 한다. 값은
`IdempotencyFingerprint.normalize`과 같은 NFC + `strip()` 후 1..100자다. 중복 header나
comma-separated 복수 값은 하나로 합쳐 저장하지 않고 `INVALID_DECISION_REQUEST`로 거부한다.

분기와 검증 순서는 다음과 같다.

1. JSON/enum/type과 공통 단일-field shape를 검증한다.
2. version tuple 네 필드가 모두 없고 `policyException`/`reasonCode`/`Idempotency-Key`도
   없으면 legacy 경로다.
3. tuple 중 하나, MVP-2 전용 body field 또는 header가 하나라도 있으면 tuple 네 필드와
   header 정확히 한 개가 모두 있어야 한다. 누락을 recommendation에서 채우지 않는다.
4. MVP-2 body를 `ApprovalTransactionCommand`로 만들어 accepted normalization, 길이와
   cross-field 규칙을 그대로 적용한 뒤 `ApprovalTransactionFacade.execute`를 호출한다.

JSON의 explicit `null`은 absent와 같다. `policyException: false`도 MVP-2 전용 field가
명시된 것이므로 tuple/header 없는 legacy body에 섞으면 거부한다.

### 1.1 MVP-2 상태별 shape

- `APPROVED`: `selectedQuantity > 0`; `policyException`은 true/false 가능
- `HELD`/`REJECTED`: `selectedQuantity=null`, `policyException=false`, `reasonCode`와
  `reason` 필수
- `PENDING`/`EXPIRED`: public command에서 항상 `INVALID_DECISION_REQUEST`
- recomputed recommended BASE와 다른 승인 수량 또는 `policyException=true` 승인은
  `reasonCode`와 `reason`이 모두 필수
- exact recommended BASE, `policyException=false` 승인은 reason fields가 없어도 된다.
- policy exception은 numeric limit, candidate eligibility나 stale 결과를 우회하지 않는다.
- 입력 수량을 반올림·clamp·추천 수량으로 치환하지 않는다.

단일-field Bean/body 변환 오류는 `VALIDATION_ERROR`; tuple/header/status 조합과 command
cross-field 오류는 `INVALID_DECISION_REQUEST`다. 후자에는 `fieldErrors`를 붙이지 않는다.

### 1.2 Legacy 경로

legacy body는 기존 필드와 조건, `RebalanceDecisionResponse` JSON, HTTP 201을 그대로
사용한다. 새 response로 감싸거나 `Location` header를 추가하지 않는다.

안전 경계로 `RebalanceDecisionService`는 recommendation receiver run의 rule version이
정확히 `InventoryAnalysisRules.RULE_VERSION`(`MVP-1`)일 때만 실행한다. non-MVP-1,
unknown/future/suffix recommendation은 version tuple 없는 legacy writer로 우회할 수 없고
`INVALID_DECISION_REQUEST` 400이다.

legacy service의 `ResponseStatusException`은 controller가 공용 advice scope에 들어가도
analysis code로 오분류되지 않도록 안정적인 `ApiException`/approval code로 정규화한다.
성공 body만 기존 계약과 byte-for-byte compatible해야 하며 오류는 공용 ProblemDetail을 쓴다.

## 2. MVP-2 POST 성공 응답

새 DTO `Mvp2RebalanceDecisionResponse`는 application result에 recommendation identity만
더한 최소 계약이다.

```json
{
  "decisionId": 301,
  "recommendationId": 101,
  "decisionStatus": "APPROVED",
  "decisionSequence": 2,
  "transferDraftId": 401,
  "created": true
}
```

- 신규 commit: `201 Created`, `created=true`
- 같은 key·같은 normalized payload replay: `200 OK`, 기존 decision/draft ID와 sequence,
  `created=false`
- `HELD`/`REJECTED`: `transferDraftId=null`
- `APPROVED`: `transferDraftId` 필수
- 두 성공 모두 `Location: /api/rebalancing-decisions/{recommendationId}`
- idempotency key, fingerprint와 내부 exception message는 응답하지 않는다.

상세 감사 값은 POST body에 복제하지 않는다. 저장 후 canonical 상세는 GET으로 조회한다.

## 3. POST application/persistence 계약

- controller는 계산·상태전이를 재구현하지 않고 command 생성과 facade 호출만 담당한다.
- `created`에 따라 status만 201/200으로 바꾸고 facade result의 ID/sequence를 그대로 mapping한다.
- same-key replay는 terminal/stale 재검증보다 먼저 기존 normalized request를 식별하는 accepted
  semantics를 유지한다. 따라서 이미 commit된 같은 요청은 현재 terminal이어도 200 replay다.
- `APPROVED`는 recommendation과 donor snapshot lock 아래 최신 basis를 다시 계산한다.
- 성공 시 실제 inventory를 바꾸지 않고 decision + basis + draft만 저장한다.
- `HELD`/`REJECTED`는 decision만 append하고 basis/draft를 만들지 않는다.
- 어느 insert/flush라도 실패하면 세 row 전체가 rollback된다.
- legacy writer는 MVP-2 append-only writer, idempotency나 lock 정책을 대체하지 않는다.

## 4. GET 결정 이력 계약

`GET /api/rebalancing-decisions/{recommendationId}`는 양의 ID를 받고 recommendation이
존재하면 결정 row가 없어도 200을 반환한다.

### 4.1 Top-level response

```json
{
  "recommendationId": 101,
  "currentStatus": "PENDING",
  "decisions": []
}
```

- decision row 없음: `currentStatus=PENDING`, `decisions=[]`
- row 있음: 가장 큰 `decisionSequence`의 status가 `currentStatus`
- `decisions`는 `decisionSequence ASC`의 non-null 배열
- paging은 이번 범위에 넣지 않는다. 한 recommendation의 audit history만 읽는다.

### 4.2 Decision item

```json
{
  "decisionId": 301,
  "decisionSequence": 2,
  "decisionStatus": "APPROVED",
  "selectedQuantity": 12,
  "policyException": false,
  "reasonCode": "MANUAL_OVERRIDE",
  "reason": "수량 조정 근거",
  "actorLabel": "본사 배분 담당자",
  "recommendationVersion": 1,
  "decisionContractVersion": "MVP-2",
  "decidedAt": "2026-08-28T12:00:00+09:00",
  "approvalBasis": {},
  "transferDraft": {}
}
```

`decisionRequestId`/`Idempotency-Key`와 fingerprint는 GET에서도 노출하지 않는다.

`approvalBasis`와 `transferDraft`는 MVP-2 `APPROVED` item에만 non-null이다. `HELD`,
`REJECTED`, `EXPIRED`와 기존 MVP-1 decision은 둘 다 null이다.

### 4.3 Approval basis item

아래 저장 field를 일대일로 반환한다.

- `approvalBasisId`, `analysisRunId`, `inputSnapshotVersion`, `ruleVersion`,
  `candidateVersion`, `candidateEligible`
- `recommendedBaseQuantity`, `donorTransferableQuantity`
- `routeMinimumQuantity`, `packageMultiple`, `routeMaximumQuantity`
- `receiverCapacityRemaining`, `receiverProjectedBeforeDemand`, `donorProjectedAtDispatch`,
  `alreadyApprovedDraftQuantity`
- `basisContractVersion`, `createdAt`

### 4.4 Transfer draft item

- `transferDraftId`, `donorStoreId`, `receiverStoreId`, `skuId`, `quantity`
- `draftStatus`, `externalReference`, `payloadVersion`, `createdAt`, `updatedAt`

draft 조회는 현재 상태를 보여줄 뿐 `READY` 전이나 ERP 전송을 수행하지 않는다.

### 4.5 Read consistency and corruption boundary

- 전용 query service는 `@Transactional(readOnly=true)`다. lock을 잡지 않는다.
- recommendation existence, ordered decisions, basis bulk read, draft bulk read로 구성하고
  history row 수와 무관하게 JDBC select 최대 4개를 유지한다. per-decision N+1은 금지한다.
- basis bulk query는 `analysisRunId` 접근이 N+1이 되지 않도록 projection 또는 join fetch를
  사용한다.
- `MVP-2` `APPROVED` decision에 basis나 draft가 없거나, non-approved MVP-2 row에 둘 중
  하나가 있거나, 물리적 `PENDING` row가 있으면 부분 응답 대신 `INTERNAL_SERVER_ERROR`다.
- 기존 `MVP-1` decision은 basis/draft가 없어도 정상이다.
- unknown decision contract version은 임의 shape로 해석하지 않고 `INTERNAL_SERVER_ERROR`다.

## 5. 오류와 ProblemDetail

`AnalysisApiExceptionHandler.assignableTypes`에 `RebalanceDecisionController`를 추가하고
기존 responder/catalog lookup을 그대로 사용한다. 새 advice나 응답 생성 복제는 만들지 않는다.

| Situation | code | HTTP | retryable |
|---|---|---:|---|
| malformed JSON/enum/common field shape/path type | `VALIDATION_ERROR` | 400 | N |
| partial tuple, missing/multiple key, illegal status fields | `INVALID_DECISION_REQUEST` | 400 | N |
| recommendation 없음 | `RECOMMENDATION_NOT_FOUND` | 404 | N |
| version/current-basis/quantity constraint 불일치 | `STALE_RECOMMENDATION` | 409 | N |
| 같은 key를 다른 normalized payload에 재사용 | `IDEMPOTENCY_KEY_REUSED` | 409 | N |
| latest decision terminal | `DECISION_ALREADY_TERMINAL` | 409 | N |
| append sequence/기타 알려진 결정 저장 경합 | `DECISION_CONFLICT` | 409 | N |
| recommendation/donor lock timeout | `APPROVAL_LOCK_TIMEOUT` | 503 | Y |
| DB 접근 불가 | `PERSISTENCE_UNAVAILABLE` | 503 | Y |
| 불완전한 저장 이력/분류되지 않은 내부 오류 | `INTERNAL_SERVER_ERROR` | 500 | N |

V10/V11에 모든 code와 constraint mapping이 이미 있으므로 새 Flyway migration을 만들지
않는다. HTTP/title/detail/retryable은 active catalog 또는 accepted fallback이 소유한다.

모든 오류는 effective `type`, `code`, body/HTTP `status`, `title`, `detail`, `retryable`,
request header/body `requestId`, UTC `timestamp`, `instance`를 가진다. validation-only 상황에만
정렬된 `fieldErrors`가 있다. recommendation id, idempotency key, raw SQL/constraint/stack와
rejected value를 detail에 넣지 않는다.

## 6. 구현 대상과 비대상

### 변경 대상

- `RebalanceDecisionRequest` additive fields와 controller all/none routing
- exact-MVP-1 legacy guard와 legacy error normalization
- `Mvp2RebalanceDecisionResponse`
- facade REST 연결과 `RebalanceDecisionController` advice scope
- decision-history query service, nested read DTO와 필요한 bulk repository query
- stale Javadoc 중 “future REST/service” 표현의 현재 사실 반영
- unit/MVC/Oracle contract와 query-count regression

### 변경하지 않는 대상

- `ApprovalTransactionCommand`, fingerprint, lock order와 current-basis 계산식
- append-only transition/sequence와 transaction atomicity
- V1~V15 migration과 catalog 정책
- inventory 차감, draft READY 전이, ERP/WMS/TMS 호출
- React, AI provider, 인증/인가

Public response나 DB schema가 이 명세와 달라져야 한다면 임의 구현하지 않고 Codex review로
돌려보낸다.

## 7. 필수 테스트와 완료 조건

### 7.1 Pure/unit/MVC

- legacy body + no header → legacy service와 기존 성공 JSON/201 exact
- full tuple + exactly one key → facade; `created=true` 201, replay `created=false` 200,
  Location/body exact
- partial tuple, MVP-2-only field/header without tuple, missing/blank/multiple/oversized key →
  `INVALID_DECISION_REQUEST`
- status별 quantity/reason/policyException shape와 normalization 경계
- tuple-less non-MVP-1 recommendation은 legacy writer 미호출
- every approval error code와 DataAccess/fallback이 effective catalog ProblemDetail로 mapping
- GET PENDING/ordered history/nested basis/draft mapping과 corruption normalization unit tests

### 7.2 Oracle POST IT

- HELD 201(seq1, basis/draft 없음) → 새 key APPROVED 201(seq2, basis+draft 있음)
- exact BASE approval without reason; changed quantity/policy exception reason-required 경계
- same key + same normalized payload replay 200, row count/ID/sequence 불변
- same key + different payload 409, stale tuple 409, terminal 409, unknown 404
- lock-timeout 503 exact ProblemDetail; 기존 same-donor concurrency/atomic rollback IT 유지
- 성공/실패 전후 inventory snapshot/metric 값 불변과 승인 세 row 원자성
- legacy exact MVP-1 성공 및 non-MVP-1 우회 방지

### 7.3 Oracle GET IT

- recommendation은 있으나 결정 없음 → PENDING + empty array
- HELD→APPROVED history ascending, currentStatus APPROVED, 모든 decision/basis/draft 값 exact
- 기존 MVP-1 decision은 정상 조회되고 basis/draft null
- unknown recommendation 404, invalid path 400, corrupt MVP-2 shape 500
- history 0/1/2행에서 JDBC statement count가 최대 4이며 row 수에 따라 증가하지 않음
- idempotency key/fingerprint가 JSON 어디에도 없음

### 7.4 Final verification

- 관련 pure/unit/MVC target tests
- 관련 Oracle target tests
- DB-free Backend 전체: DB-dependent test만 conditional skip, failures/errors 0
- Oracle Backend 전체: skip/failures/errors 0
- `git diff --check`

실행하지 않은 테스트를 성공으로 기록하지 않는다.

## Next verifiable action

Claude가 먼저 POST additive routing과 facade wiring을 구현해 legacy/MVP-2 MVC 계약을
고정하고, 다음으로 GET bulk query/DTO를 구현한다. 마지막에 Oracle POST/GET matrix와
전체 회귀를 실행해 실제 결과를 기록한 뒤 Codex review로 인계한다.
