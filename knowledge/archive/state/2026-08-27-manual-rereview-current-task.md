# Current Task

Status: `MANUAL` 수량 시험 Codex review finding 2건 수정 완료; Codex 재검증 대기
Current role: Codex verification/review
Last updated: 2026-08-27

## Goal

수동 preview와 실제 승인이 같은 current basis에서 같은 eligibility 결론을 내리게 하고,
공통 loader 추출이 accepted 승인 경로에 새 입력 필수조건을 만들지 않도록 보정한다.

## Accepted within this increment

- command 정규화와 `INVALID_REQUEST`, recommendation → donor 잠금 순서, terminal/
  stale/lock error 분류와 side-effect-free transaction 경계
- 6개 violation 고정 순서, 모든 candidate reason, hard ceiling·package 하향 제안
- feasible일 때만 BASE-rate projection을 반환하는 `TransferEffectProjection` 공통 계산
- REST/ProblemDetail/React와 Migration을 변경하지 않은 application-only 범위

## Required fixes

### 1. Preview/approval eligibility parity

현재 `ManualQuantityEvaluation`은 `recalculation.candidateEvaluation().eligible()`을
사용하지만 승인은 `recalculation.eligible()`을 사용한다. 후자는 BASE 수량이 양수인지도
검사한다. receiver의 현재 필요량이 0이면 구조적 후보 사유는 비어 있어도 BASE=0이며,
preview만 요청수량을 feasible로 표시하고 실제 승인은 `STALE_RECOMMENDATION`으로 거부한다.

- 수동 경로도 `ApprovalBasisRecalculation.eligible()`을 단일 eligibility source로 쓴다.
- 이 경우 `CANDIDATE_INELIGIBLE`, `maximumFeasibleQuantity=0`, `suggestedQuantity=0`,
  `projection=null`을 반환한다. candidate rejection reasons는 계산된 값 그대로 보존한다.
- BASE=0·candidate reason 없음·수량 1 fixture에서 preview가 infeasible이고 실제 승인과
  같은 결론인지 순수 회귀 테스트로 고정한다.

### 2. Do not require donor BASE rate for approval

`CurrentApprovalBasisLoader`가 receiver BASE, donor BASE, donor HIGH 중 하나라도 null이면
stale 처리한다. 그러나 V6는 demand-rate 컬럼을 nullable로 허용하며 accepted 승인 계산은
receiver BASE와 donor HIGH만 사용한다. 수동 donor coverage 표시 때문에 추가된 donor BASE가
기존 승인 전체의 새 필수조건이 되면 안 된다.

- 공통 loader는 donor BASE null만으로 stale 처리하지 않는다.
- donor BASE가 null이면 수동 projection의 donor before/after coverage는 기존
  `TransferEffectProjection` 계약대로 null이다. hard constraint·risk 계산은 donor HIGH를 쓴다.
- donor BASE=null, donor HIGH>0인 schema-legal Oracle fixture에서 기존 APPROVED가 성공하고
  manual preview도 feasible이며 donor coverage 두 값만 null인지 각각 검증한다.

## Required regression evidence

- 기존 `ManualQuantityEvaluationTest`와 `ManualQuantityTestExecutorIT`에 위 경계를 추가한다.
- manual command의 wrong `analysisRunId`와 wrong `inputSnapshotVersion`이 각각 stale이며
  decision/basis/draft/inventory를 바꾸지 않는 Oracle assertions도 추가한다.
- 기존 approval 표적, atomicity/concurrency, `TransferScenarioSetTest`를 모두 재실행한다.
- Oracle 전체 Backend build, DB-free 전체 build와 `git diff --check`의 실제
  total/pass/skip/failure를 기록한다.

## Constraints

- 승인 `recalc.eligible()` 규칙, lock 순서, idempotency, 원자성 및 오류 계약을 바꾸지 않는다.
- 수량을 자동 반올림하지 않고 hard constraint를 정책 예외로 우회하지 않는다.
- V1~V13, REST DTO/controller/ProblemDetail, React와 MVP-1 simulation API는 수정하지 않는다.
- 테스트용 production flag나 데이터 보정 분기를 추가하지 않는다.

## Completion

두 회귀 fixture가 수정 전 실패·수정 후 통과하고 제출된 표적 95개와 전체 285개 기준선이
회귀하지 않는다. Claude가 결과를 state/worklog에 기록한 뒤 Codex가 재검증한다.

## 구현 완료 및 증거 (2026-08-27, 이번 라운드)

### Fix 1 — Preview/approval eligibility parity

- `ManualQuantityEvaluation.calculate`가 `recalculation.candidateEvaluation().eligible()`
  대신 `recalculation.eligible()`을 단일 eligibility source로 사용하도록 변경
  ([`ManualQuantityEvaluation.java`](../../backend/src/main/java/com/bapegg/stockpilot/demand/ManualQuantityEvaluation.java)).
  변수명도 `candidateEligible` → `eligible`로 정정.
- 신규 순수 회귀: `ManualQuantityEvaluationTest
  .zeroReceiverNeedWithNoStructuralRejectionReasonIsStillCandidateIneligible` — receiver
  재고가 target을 이미 초과해 `recommendedBaseQuantity=0`이지만 구조적 candidate 사유는
  비어 있는 fixture에서 `CANDIDATE_INELIGIBLE`/infeasible을 확인 (수정 전에는
  feasible=true로 잘못 보고됐을 조건).
- 기존 `belowRouteMinimumViolationForcesTheSuggestionToZero`의 fixture도
  `recommendedBaseQuantity=0`이었음이 재확인되어(플로어링 후 route minimum 미달), 기대
  violation을 `[BELOW_ROUTE_MINIMUM]` → `[CANDIDATE_INELIGIBLE, BELOW_ROUTE_MINIMUM]`로
  수정(테스트 단정 오류 수정, 프로덕션 동작 변경 아님).

### Fix 2 — Donor BASE rate는 승인의 새 필수조건이 아님

- `CurrentApprovalBasisLoader.load`의 stale 검사에서 `donorMetric.getBaseDemandRate()
  == null` 조건을 제거. receiver BASE null 또는 donor HIGH null만 stale
  ([`CurrentApprovalBasisLoader.java`](../../backend/src/main/java/com/bapegg/stockpilot/approval/CurrentApprovalBasisLoader.java)).
  `TransferEffectProjection`은 이미 null rate를 허용(coverage만 null)하므로 다른 코드
  변경 없음.
- 신규 Oracle 회귀: `ManualQuantityTestExecutorIT
  .nullDonorBaseRateDoesNotBlockApprovalOrManualPreviewButNullsOnlyDonorCoverage` — donor
  BASE=null/HIGH=1 schema-legal fixture에서 manual preview가 feasible이고 donor coverage
  두 값만 null이며, 같은 basis의 실제 `facade.execute(APPROVED)`도 성공함을 확인.

### 요구된 추가 stale/no-write assertion

- `ManualQuantityTestExecutorIT.wrongAnalysisRunIdRejectsAManualTestAndWritesNoRow`,
  `.wrongInputSnapshotVersionRejectsAManualTestAndWritesNoRow` (신규) — 각각
  `STALE_RECOMMENDATION`이며 decision/basis/draft 행이 늘지 않음을 확인.

### 실제 실행 증거

- 표적 재실행: `ManualQuantityEvaluationTest` 14/14, `ManualQuantityTestCommandTest` 8/8,
  `ManualQuantityTestExecutorIT` 8/8, `ApprovalTransactionExecutorIT` 15/15,
  `ApprovalTransactionConcurrencyIT` 5/5, `ApprovalTransactionAtomicityIT` 2/2,
  `TransferScenarioSetTest` 21/21 — 전부 통과.
- Oracle 전체 Backend build: **289/289**, skip 0, failures/errors 0.
- DB-free 전체 build: **289 total/238 passed/51 skip**, failures/errors 0.
- `git diff --check`: exit 0 (기존 파일의 LF/CRLF 경고만).
- REST/DTO/ProblemDetail/React/Migration 미변경, V1~V13 미변경.

## Next verifiable action

Codex가 두 finding이 실제로 닫혔는지, 새 회귀 fixture와 assertion이 요구사항과 정확히
일치하는지, 그리고 전체 289개 기준선이 회귀하지 않는지 독립 검증한다. 승인 시
`implemented-state.md`의 `MANUAL` 섹션을 "— accepted"로 재표시하고 다음 작업 단위를
지정한다.
