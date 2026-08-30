# Implemented State

Last updated: 2026-08-27

현재 저장소에서 관찰되는 동작과 열린 결함만 유지하는 hot-state snapshot이다.
수정·검증 과정의 원문은 아래 archive에 있다.

## Baseline and platform

- MVP-1 동결 기준선: [`../milestones/MVP-1.md`](../milestones/MVP-1.md)
- Java 21, Spring Boot 4.1.0, Gradle 9.5.1, Oracle Free 23ai/Flyway/JPA
- React 19, TypeScript 5.9, Vite 8
- MVP-1의 7일 Batch, 목록·상세·simulation·결정 REST/React와 AI-disabled 설명
  경계는 호환 기준선으로 유지된다. 실제 LLM provider 호출은 없다.

## MVP-2 data and deterministic rules — accepted

- immutable `V1`~`V13` Migration이 존재한다. V6~V9는 버전 입력·결과·결정 이력,
  GS-01~GS-06 SYNTHETIC Seed와 간결한 한국어 Comment를 제공한다.
- V10~V13은 idempotency key, policy-exception flag, `SP_APPROVAL_BASIS`, 오류 catalog/
  constraint map, 활성 draft 조회 인덱스와 최종 한국어 Comment를 제공한다.
- `data/seed/mvp2`와 validator는 MVP-1을 보존하며 6 products, 3 stores, 348 inventory,
  336 sales, event/inbound/open-transfer/routes/policies 입력 계약을 검증한다.
- `com.bapegg.stockpilot.demand`의 28일 관측·신호·confidence·low/base/high 수요율,
  projection·exception·candidate·네 자동 scenario와 승인 request 검증은 순수 Java다.
- 수량·상태는 Java가 결정하고 AI는 설명만 담당한다. 모든 MVP-2 정책은 실제 기업
  정책이 아닌 `ASSUMPTION`이다.

## Approval persistence and transaction — accepted

- Decision은 append-only 1:N이며 `PENDING/HELD/APPROVED/REJECTED/EXPIRED`를 매핑한다.
  공개 MVP-1 결정 API는 계속 APPROVED/REJECTED만 허용한다.
- `SpApprovalBasis`와 `SpTransferDraft`가 결정과 각각 1:1이다. Draft의 현재 전이는
  `CREATED → READY`이며 실제 재고나 ERP를 변경하지 않는다.
- application API는 command 정규화·SHA-256 fingerprint, 동일-key replay/conflict,
  recommendation → donor snapshot 3초 pessimistic lock과 최신 근거 교차검증을 수행한다.
- HELD/REJECTED는 append-only decision, APPROVED는 decision+basis+CREATED draft를
  한 transaction으로 저장한다. 중간 실패는 모두 rollback한다.
- DB constraint 번역은 실패 transaction rollback 후 별도 read transaction에서 하며
  원시 SQL·제약명·stack message를 노출하지 않는다.
- Codex accepted 증거: 표적 Oracle 30/30, Oracle 전체 258/258(skip 0), DB 없는 전체
  258 total/216 passed/42 Oracle-conditioned skip, `git diff --check` 통과.

## `MANUAL` quantity testing — findings fixed, Codex 재검증 대기

Migration·REST·React 변경 없이 side-effect-free application preview를 추가했다.

- `ManualQuantityTestCommand/Result/Executor`: 정규화된 추천·run·input/rule/candidate
  version과 양의 정수 수량을 받아 recommendation → donor 순서로 잠그고 결과만 반환한다.
- `CurrentApprovalBasisLoader`: 승인과 수동 시험이 version, route, snapshot, policy,
  inbound/open transfer/active draft 조회와 교차검증을 공유한다.
- `ManualQuantityEvaluation`: 고정 순서의 6개 hard-constraint 위반, 모든 candidate
  reason, 최대 가능수량·하향 제안수량과 feasible projection을 순수 계산한다.
- `TransferEffectProjection`: 자동 scenario와 수동 시험의 양쪽 before/after available,
  coverage, risk 공식을 공유한다.
- candidate-ineligible은 예외가 아닌 `feasible=false`; terminal/stale/lock/DB 장애는
  기존 안정 error code를 사용한다. Idempotency-Key나 저장·flush는 없다.

### Findings closed this round

1. **Eligibility parity**: `ManualQuantityEvaluation`이 이제 `candidateEvaluation()
   .eligible()` 대신 `ApprovalBasisRecalculation.eligible()`(구조적 사유 AND
   `recommendedBaseQuantity > 0`)을 단일 eligibility source로 쓴다. BASE=0·구조적 후보
   사유 없음 fixture가 `CANDIDATE_INELIGIBLE`/infeasible로 바뀌었고, 기존
   `belowRouteMinimumViolationForcesTheSuggestionToZero`(BASE=0였음)도 같은 이유로
   기대 violation을 갱신했다.
2. **Donor BASE는 승인의 새 필수조건이 아님**: `CurrentApprovalBasisLoader.load`가
   더 이상 `donorMetric.getBaseDemandRate() == null`만으로 stale 처리하지 않는다.
   receiver BASE null 또는 donor HIGH null만 stale이다. `TransferEffectProjection`은
   이미 null rate를 허용해(coverage만 null) 다른 프로덕션 코드 변경은 없었다.

### Closing regression evidence

- `ManualQuantityEvaluationTest.zeroReceiverNeedWithNoStructuralRejectionReasonIsStillCandidateIneligible`
  (신규, 순수): BASE=0·구조적 사유 없음 fixture가 이제 infeasible임을 고정.
- `ManualQuantityTestExecutorIT.wrongAnalysisRunIdRejectsAManualTestAndWritesNoRow`,
  `.wrongInputSnapshotVersionRejectsAManualTestAndWritesNoRow` (신규, Oracle): 각각 stale이며
  decision/basis/draft 행을 남기지 않음을 확인.
- `ManualQuantityTestExecutorIT.nullDonorBaseRateDoesNotBlockApprovalOrManualPreviewButNullsOnlyDonorCoverage`
  (신규, Oracle): donor BASE=null/HIGH=1 schema-legal fixture에서 manual preview가
  feasible이고 donor coverage 두 값만 null이며, 같은 basis의 실제 APPROVED도 성공함을 확인.

## Current verification evidence

- 표적 재실행(실제 실행, 이번 라운드): `ManualQuantityEvaluationTest` 14/14,
  `ManualQuantityTestCommandTest` 8/8, `ManualQuantityTestExecutorIT` 8/8,
  `ApprovalTransactionExecutorIT` 15/15, `ApprovalTransactionConcurrencyIT` 5/5,
  `ApprovalTransactionAtomicityIT` 2/2, `TransferScenarioSetTest` 21/21 — 전부 통과.
- Oracle 전체 Backend build: **289/289**, skip 0, failures/errors 0.
- DB-free 전체 build: **289 total/238 passed/51 Oracle-conditioned skip**,
  failures/errors 0. `git diff --check` 통과(기존 파일의 LF/CRLF 경고만).
- 아직 Codex 재검증 전이므로 `MANUAL` 섹션은 accepted로 표시하지 않는다.

## Not implemented

- 승인 transaction과 `MANUAL` application API의 REST DTO/ProblemDetail/React wiring
- MVP-2 계산의 Batch/REST/React application wiring과 실제 snapshot write path
- 실제 LLM provider adapter

## Cold evidence

- MANUAL 리뷰 직전 state:
  [`../archive/state/2026-08-27-manual-review-current-task.md`](../archive/state/2026-08-27-manual-review-current-task.md),
  [`../archive/state/2026-08-27-manual-review-implemented-state.md`](../archive/state/2026-08-27-manual-review-implemented-state.md)
- MANUAL 리뷰까지의 raw worklog:
  [`../archive/worklogs/2026-08-27-through-manual-review.md`](../archive/worklogs/2026-08-27-through-manual-review.md)
- 이전 V10 review compaction은 해당 archive 문서의 historical links에서 이어진다.
