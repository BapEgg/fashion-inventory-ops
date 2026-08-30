# Archived Current Task — Phase 3 input adapter acceptance input

Status: Phase 3 Batch input adapter 4건 수정 완료; Codex 재검증 대기
Current role: Codex verification/review
Last updated: 2026-08-27

## Goal

계산·영속화 orchestration으로 확장하기 전에 입력 adapter의 남은 계약 결함 4건을 닫고
Codex 재리뷰로 인계한다. 새 Migration, Job/Step, output 저장, 승인·`MANUAL` parity,
REST/React는 이번 수정 범위가 아니다.

전체 Phase 3 계약은
[`../archive/state/2026-08-27-phase3-foundation-review-current-task.md`](../archive/state/2026-08-27-phase3-foundation-review-current-task.md),
입력 규칙은 [`../business-rules.md`](../business-rules.md), 물리 read 계약은
[`../data-model.md`](../data-model.md)의 “Phase 3 Batch 물리 mapping”을 따른다.

## Required fixes

### 1. 미래 inventory snapshot을 독립적으로 거부

현재 history query는 `snapshot_date <= analysisDate-1`만 읽어 같은 입력 버전에 미래 행이
있어도 완전한 28일 graph를 그대로 반환한다. 기존 8 statement 한도를 유지하면서 같은
버전의 `snapshot_date > analysisDate` 또는 현재 anchor의 `snapshotAt > analysisReferenceAt`
을 `InputContractViolationException`으로 거부한다. 오래된 현재 snapshot과 관측일의 현지
날짜 불일치는 input failure가 아니라 후속 `STALE_INVENTORY` 판정용으로 계속 보존한다.

완료 증거: 누락 없는 정상 graph에 같은 버전의 미래 inventory 행만 추가한 Oracle IT가
반드시 input-contract failure를 확인한다.

### 2. Route NUMBER 값을 checked conversion으로 읽기

`minimum_quantity`, `package_multiple`, `maximum_quantity`는 Oracle `NUMBER(10,0)`이므로
`Integer.MAX_VALUE`를 넘을 수 있다. `ResultSet.getInt`에 맡기지 말고 다른 수량과 동일하게
`getLong` → `safeInt`로 변환해 안정적인 `InputContractViolationException`을 반환한다.
`lead_time_days NUMBER(5,0)`도 같은 mapping helper를 사용해 일관성을 유지할 수 있다.

완료 증거: DB 제약상 유효하지만 32bit 범위를 넘는 route 수량의 Oracle IT가 driver 예외가
아닌 input-contract failure를 확인한다.

### 3. 활성 승인 draft 합계 overflow를 graph 반환 전에 차단

개별 draft 수량이 int 범위여도 `SUM(quantity)`는 이를 넘을 수 있다. 현재 pure
`InventoryProjection.calculate(...)`의 `alreadyApprovedDraftQuantity`가 `int`이므로 adapter가
합계를 그대로 `Long` graph에 넣지 말고 domain 범위를 검사한다. `Map` 값을 `Integer`로
고정하거나 `Long`을 유지하더라도 범위 초과 시 반드시 input-contract failure를 발생시킨다.

완료 증거: 개별 행은 유효하지만 합계만 `Integer.MAX_VALUE`를 넘는 복수 active APPROVED
draft Oracle IT가 실패를 확인한다.

### 4. input graph를 store–SKU/lane key로 실제 grouping

현재 inventory/sales와 draft만 grouping되고 event, inbound, open transfer, route는 flat
`List`다. adapter 경계에서 immutable index를 완성해 orchestration이 전체 list를 반복
scan하지 않게 한다. store–SKU용 공개 key와 donor–receiver lane/store-pair key를 두거나,
동일 의미의 immutable lookup API를 제공한다. 원본 list를 호환용으로 유지해도 되지만
계산 loop가 key lookup만으로 필요한 evidence를 얻을 수 있어야 한다.

완료 증거: 두 개 이상의 store/SKU/lane fixture에서 각 key가 자기 event/inbound/transfer/
route만 반환하며 결과 map과 내부 list가 수정 불가능함을 테스트한다.

## Constraints and validation

- 일곱 bulk group/8개 물리 statement와 loop query 0 계약을 유지한다.
- SQL에서 통계·신호·수요율·eligibility·수량 규칙을 계산하지 않는다.
- `Mvp2InputAdapterIT`에 위 세 failure 경계와 key lookup을 추가한다.
- 실제 Oracle 표적, 전체 Oracle Backend build, DB-free 전체 build, `git diff --check`를
  실행하고 실제 수치를 기록한다.

## Completion condition

네 finding이 닫히고 adapter가 완전한 immutable indexed graph 또는 안정적인
`InputContractViolationException`만 반환하면 Codex 재검증으로 인계한다. 승인 전에는 계산
orchestration 구현을 시작하지 않는다.

## 이번 라운드 수정 완료

### Fix 1 — future snapshot 독립 탐지

- [Mvp2InputAdapter.java](../../backend/src/main/java/com/bapegg/stockpilot/batch/Mvp2InputAdapter.java)의
  inventory history query에서 상한(`snapshot_date <= historyEnd`)을 제거해(하한만 유지) 여전히
  하나의 statement로 같은 버전의 미래 행도 함께 읽는다. `buildAnchor`가 missing-day 검사
  전에 `inventoryByDate`에 `analysisDate`보다 뒤인 날짜가 있으면 즉시 거부한다.
- anchor 자신의 `snapshotAt`이 `analysisReferenceAt`(`analysisDate+1일 00:00 Asia/Seoul`)보다
  뒤면 거부한다. 오래된(stale) 현재 snapshot이나 현지 날짜 불일치는 여기서 거부하지 않고
  후속 `STALE_INVENTORY` 판정용으로 보존한다.

### Fix 2 — route NUMBER checked conversion

- `lead_time_days`, `minimum_quantity`, `package_multiple`, `maximum_quantity` 모두
  `rs.getInt(...)` 대신 다른 수량과 동일한 `getLong` → `safeInt` 경로로 읽는다.
- `safeInt`를 `(storeId, skuId)` 전용 시그니처에서 단일 `context` 문자열로 일반화해 route
  (donor/receiver, SKU 없음)와 draft 합계에도 그대로 재사용한다.

### Fix 3 — draft 합계 overflow 차단

- `loadActiveApprovedDraftQuantity`가 `SUM(quantity)`를 `Long`으로 읽은 뒤 graph에 넣기
  전에 `safeInt`로 검증한다(map 값 타입은 `Long`을 유지하되, 범위를 벗어나면 반드시
  `InputContractViolationException`).

### Fix 4 — key-indexed graph

- 새 public key record `Mvp2StoreSkuKey`, `Mvp2LaneKey`(donor–receiver–sku),
  `Mvp2StorePairKey`(donor–receiver, route는 SKU-scoped 아님)를 추가했다.
- `Mvp2InputGraph`에 `eventsByStoreSku`, `inboundByStoreSku`, `openTransfersByLane`,
  `routesByStorePair` 네 immutable map을 추가했다. adapter의 in-memory `groupBy` helper로
  구성하며(새 query 없음) 기존 flat list는 호환용으로 유지한다. compact constructor가
  map과 내부 list를 모두 깊은 불변으로 만든다.

### 실제 실행 증거

- [Mvp2InputAdapterIT.java](../../backend/src/test/java/com/bapegg/stockpilot/batch/Mvp2InputAdapterIT.java)
  **10/10**(Oracle): 기존 6개(버전 혼합 테스트는 future-date decoy가 이제 fix 1로 먼저
  걸리므로 이름·범위를 좁혀 버전 혼합만 검증) + 신규 4개(future snapshot 독립 탐지,
  `snapshotAt` 미래, route overflow, draft 합계 overflow). happy path fixture를 2-lane으로
  확장해 각 key가 자기 evidence만 반환하고 부재 key는 `null`이며 map·list 모두 수정
  불가능함을 확인했고, 이 확장된 fixture에서도 8-statement/no-loop 계측은 그대로 8이었다.
- Oracle 전체 Backend build: **315/315**, skip 0, failures/errors 0.
- DB-free 전체 build: **315 total/246 passed/69 skip**, failures/errors 0.
- `git diff --check`: exit 0 (기존 파일의 LF/CRLF 경고만).
- Migration, REST/React, 계산 orchestration, 출력 영속화, 승인·MANUAL parity는 이번 범위
  밖이며 손대지 않았다.

## Next verifiable action

Codex가 네 finding이 실제로 닫혔는지, 확장된 fixture에서도 8-statement/no-loop 계약이
유지되는지, 전체 315개 기준선이 회귀하지 않는지 독립 검증한다. 승인 시 계산
orchestration(§4-5) 구현을 시작한다.
