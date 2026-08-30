# Current Task

Status: `MANUAL` 수량 시험 구현 완료; Codex 재검증 대기
Current role: Codex verification/review
Last updated: 2026-08-27

## Goal

사용자가 바꿔 입력한 추천 수량의 실행 가능성과 양쪽 매장 예상 결과를 반환하는
application API를 구현하고, 화면·REST보다 먼저 순수 계산과 Oracle 경계를 검증한다.

## Fixed policy (`MVP-2` demo `ASSUMPTION`)

- 입력은 수요율이 아니라 양의 정수 **이동수량**이다.
- 시스템은 입력수량을 자동 반올림하거나 승인수량으로 바꾸지 않는다.
- BASE와 달라도 hard constraint 안이면 `feasible=true`이며 실제 승인에는 기존
  규칙대로 reason code와 설명이 필요하다.
- 정책 예외도 최소·배수·공급·경로 최대·receiver 수용량을 우회하지 못한다.
- 수동 결과의 커버리지와 위험은 receiver/donor의 현재 BASE 수요율을 사용한다.
- 시험은 정책 예외 권한이나 예약이 아닌 preview이며 실제 승인은 다시 잠그고 재계산한다.

## Application contract

`ManualQuantityTestCommand`:

- `recommendationId`, `analysisRunId`: null이 아닌 양수 `Long`; `candidateVersion`,
  `requestedQuantity`: 양수 `int`
- `inputSnapshotVersion`: NFC+strip 후 1~64자; `ruleVersion`: 같은 방식의 1~32자
- actor, reason, status, policyException, Idempotency-Key는 받지 않는다.

`ManualQuantityTestResult`는 다음을 모두 반환한다.

- 요청 identity/version, `requestedQuantity`, `feasible`, `reasonRequired`,
  `recommendedBaseQuantity`, `maximumFeasibleQuantity`, `suggestedQuantity`
- 고정 순서의 `violations`, 모든 `candidateRejectionReasons`, route minimum/package/
  maximum, donor transferable, receiver capacity remaining
- feasible일 때만 non-null인 `projection`: 양쪽 before/after available·coverage·risk,
  lead/arrival와 반영한 inbound/open transfer/active draft 수량
- `approvalRevalidationRequired=true`; 어떤 decision/draft ID도 반환하지 않는다.

요청 shape는 `INVALID_REQUEST`, 추천 없음은 `RECOMMENDATION_NOT_FOUND`, identity/
latest-run/route/snapshot 불일치는 `STALE_RECOMMENDATION`, terminal은
`DECISION_ALREADY_TERMINAL`, 잠금은 `APPROVAL_LOCK_TIMEOUT`, DB 장애는 기존 fallback을
쓴다. 새 오류 row/Migration은 없고 수량 제약 위반은 정상 결과다.

## Deterministic calculation

```text
hardCeiling = min(donorTransferable, routeMaximum, receiverCapacityRemaining)
maximumFeasible = floor(hardCeiling / packageMultiple) * packageMultiple
if !candidateEligible or maximumFeasible < routeMinimum: maximumFeasible = 0

suggested = floor(min(requestedQuantity, maximumFeasible) / packageMultiple)
            * packageMultiple
if suggested < routeMinimum: suggested = 0
```

`feasible`은 candidate가 실행 가능하고 아래 위반이 없을 때만 true다. enum 순서는 고정한다.

1. `CANDIDATE_INELIGIBLE`
2. `BELOW_ROUTE_MINIMUM`
3. `NOT_PACKAGE_MULTIPLE`
4. `EXCEEDS_DONOR_TRANSFERABLE`
5. `EXCEEDS_ROUTE_MAXIMUM`
6. `EXCEEDS_RECEIVER_CAPACITY`

여러 위반을 동시에 반환한다. `reasonRequired`는 입력수량이 재계산 BASE와 다르면 true다.
invalid 결과는 `projection=null`이며 자동 제안값만 제공한다. valid 결과의
after 수량은 입력수량 그대로 계산하고 coverage는 scale 12 `HALF_UP`, 수요율이 0
이하면 null이다. risk는 기존 BASE target/HIGH donor 보호재고 규칙을 그대로 쓴다.

## Implementation boundary

- `demand`: 순수 `ManualQuantityViolation`, `ManualQuantityEvaluation`,
  `ManualQuantityProjection`을 추가한다.
- 자동 시나리오와 수동 시험이 before/after·coverage·risk 공식을 복제하지 않도록
  `TransferScenarioSet`의 공통 부분을 순수 `TransferEffectProjection`으로 추출한다.
- `TransferScenarioType`은 Oracle에 저장되는 네 유형만 유지한다. 현재 “MANUAL은
  user-supplied rate”라는 Javadoc은 수량 입력 계약으로 바로잡는다.
- `approval`: command/result와 `ManualQuantityTestExecutor`를 추가한다. 기존
  `ApprovalTransactionExecutor.recalculate`의 조회·교차검증은
  `CurrentApprovalBasisLoader`로 추출해 두 use case가 동일한 근거를 사용하게 한다.
  repository query와 계산식을 수동 경로에 복사하지 않는다.
- loader가 반환하는 context는 transaction 내부에서만 사용하며 JPA lazy entity를
  application result로 노출하지 않는다.

## Transaction and lock order

`ManualQuantityTestExecutor.test`는 `@Transactional`이며 저장·flush하지 않는다.
1. transaction 밖 command 생성 시 shape를 검증한다.
2. recommendation을 기존 3초 `PESSIMISTIC_WRITE` query로 잠근다.
3. 최신 decision이 없거나 `HELD`인지 확인한다. `APPROVED/REJECTED/EXPIRED`는 종료다.
4. analysis/input/rule/candidate version과 동일 rule의 최신 COMPLETED run을 검증한다.
5. approval과 같은 순서로 donor snapshot을 잠근다.
6. route 방향·버전·active, 양쪽 snapshot/run/SKU, policy, inbound, open transfer와
   commit된 active draft 합계를 같은 loader에서 다시 읽어 basis를 만든다.
7. 순수 평가 결과를 반환하고 decision/basis/draft/inventory를 한 행도 변경하지 않는다.

Idempotency-Key는 없고 잠금은 예약이 아닌 일관된 preview만 만든다; 모든 승인 경로도 recommendation → donor 순서를 유지한다.

## Tests and completion

- `ManualQuantityTestCommandTest`: null/0/음수 ID·version·quantity, Unicode/공백 정규화,
  길이 경계를 검증한다.
- `ManualQuantityEvaluationTest`: BASE 일치, BASE보다 작은/큰 valid 수량, 여섯 위반의
  단독·복합 결과, package 하향 제안, maximum이 minimum 미만인 0 결과, 모든 candidate
  reason 보존, projection/coverage/risk와 checked arithmetic을 exact assertion한다.
- Oracle IT: current result와 active draft 반영, wrong run/route/snapshot stale, terminal,
  candidate-ineligible 결과, 실제 donor lock timeout, 모든 관련 테이블 row count 불변을
  검증한다.
- 기존 approval 표적 30개와 `TransferScenarioSet` 전체를 회귀 실행해 공통 추출이
  승인 수량·원자성·동시성 및 네 자동 시나리오 결과를 바꾸지 않았음을 확인한다.
- Oracle 전체 Backend build, DB-free 전체 build, `git diff --check`의 실제
  total/pass/skip을 기록한다.
- REST DTO/controller/ProblemDetail와 React는 수정하지 않는다. 기존 MVP-1
  `/api/rebalancing-simulations` 계약도 이 단위에서 변경하지 않는다.

## 구현 완료 및 증거

- `demand`: `TransferEffectProjection`(전후 가용재고·커버리지·위험 공통 계산, `TransferScenarioSet`이
  재사용하도록 추출), `ManualQuantityViolation`(6개 고정 순서), `ManualQuantityProjection`,
  `ManualQuantityEvaluation`(`ApprovalBasisRecalculation.calculate`를 그대로 호출해 위반·
  maximumFeasible·suggested·projection을 계산). `TransferScenarioType`의 stale MANUAL Javadoc을
  수량 계약으로 정정했다.
- `approval`: `CurrentApprovalBasisLoader`(기존 `ApprovalTransactionExecutor.recalculate`의
  version 검증·donor 잠금·조회·교차검증을 그대로 추출, `LoadedApprovalBasis` pure record 반환),
  `ManualQuantityTestCommand`(`INVALID_REQUEST`), `ManualQuantityTestResult`,
  `ManualQuantityTestExecutor`(`@Transactional`, 저장 없음). `ApprovalTransactionExecutor`는
  loader를 쓰도록 리팩터링했고 동작은 바꾸지 않았다. `DecisionStatus.isTerminalForFurtherDecision()`을
  추가해 두 executor가 terminal 판정을 공유한다. `ApprovalErrorCode.INVALID_REQUEST` 추가.
- 단위 테스트: `ManualQuantityEvaluationTest`(13, BASE 일치/증감, 6개 위반 단독·복합, package
  하향 제안, max<min→0, candidate reason 보존, projection exact arithmetic, 음수 거부),
  `ManualQuantityTestCommandTest`(8) 모두 통과.
- Oracle IT: `ManualQuantityTestExecutorIT`(5 — 활성 draft 반영, terminal, stale rule version,
  wrong route donor, candidate-ineligible 정상 결과)와 `ApprovalTransactionConcurrencyIT`에 추가한
  manual-test lock-timeout 시나리오(실제 `ORA-00054` 확인) 모두 통과.
- 리팩터링 회귀: 기존 approval 표적(`ApprovalTransactionExecutorIT` 15, `ApprovalTransactionCommandTest`
  11, `IdempotencyFingerprintTest` 6, `PersistenceErrorTranslatorTest` 9, `ApprovalTransactionAtomicityIT`
  2, `ApprovalTransactionConcurrencyIT` 5 = 48)와 `TransferScenarioSetTest`(21) 전부 그대로 통과해
  `CurrentApprovalBasisLoader`/`TransferEffectProjection` 추출이 기존 승인 수량·원자성·동시성·자동
  시나리오 결과를 바꾸지 않았음을 확인했다.
- 기존 Oracle 전체 Backend build 285/285(skip 0), DB 없는 전체 build 285 total/237 passed/48
  Oracle-conditioned skip, 0 failures/errors. `git diff --check` 통과.

## Next verifiable action

Codex가 `CurrentApprovalBasisLoader` 추출이 기존 승인 경로를 바꾸지 않았는지, `ManualQuantityEvaluation`의
계산식·violation 순서·projection이 계약과 일치하는지, Oracle IT의 stale/terminal/ineligible/lock-timeout
근거를 독립 검증한다. 승인되면 `implemented-state.md`에 accepted로 옮긴다.
