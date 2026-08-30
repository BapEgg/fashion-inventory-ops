# Archived Implemented State — Phase 3 foundation review input

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

## `MANUAL` quantity testing — accepted

Migration·REST·React 변경 없이 side-effect-free application preview가 구현돼 있다.

- `ManualQuantityTestCommand/Result/Executor`는 정규화된 추천·run·input/rule/candidate
  version과 양의 정수 수량을 받아 recommendation → donor 순서로 잠그고 결과만 반환한다.
- `CurrentApprovalBasisLoader`는 승인과 수동 시험이 version, route, snapshot, policy,
  inbound/open transfer/active draft 조회와 교차검증을 공유하게 한다.
- `ManualQuantityEvaluation`은 고정 순서의 6개 위반, 모든 candidate reason, 최대
  가능수량·하향 제안수량과 feasible projection을 순수 계산한다.
- 수동 eligibility는 실제 승인과 동일한 `ApprovalBasisRecalculation.eligible()`을 쓴다.
  BASE=0이면 구조적 사유가 없어도 infeasible이며 최대/제안수량 0, projection null이다.
- donor BASE는 nullable이며 승인 필수조건이 아니다. null이면 donor coverage만 null이고
  hard constraint와 risk는 donor HIGH 기반 보호수량을 사용한다.
- candidate-ineligible은 예외가 아닌 `feasible=false`; terminal/stale/lock/DB 장애는
  기존 안정 error code를 사용한다. Idempotency-Key나 저장·flush는 없다.

### Accepted invariants

- wrong run/input/rule/route는 stale로 실패하며 decision/basis/draft/inventory를 바꾸지
  않는다. inventory 불변성은 receiver·donor 행의 id·store·SKU·on-hand·reserved·version을
  raw JDBC로 호출 전후 비교한다.
- terminal은 안정 오류로 거부되고, 후보 부적격은 예외 대신 모든 사유와
  `feasible=false`를 반환한다. 모든 결과는 실제 승인 시 재검증이 필요하다.

## Phase 3 Batch — foundation layer implemented, orchestration not started

Phase 3 상세 명세(`current-task.md`)는 크므로 이번 라운드는 이후 모든 작업이 의존하는
기반 계층만 구현·검증했다. Migration은 추가하지 않았다(V6 기존 컬럼/테이블에만 매핑).

- 새 JPA entity·repository 4종: `SpDemandEvent`(rebalance), `SpMetricQualityFlag`
  (analysis, `SpInventoryMetric` 자식), `SpCandidateReason`(rebalance,
  `SpRebalanceRecommendation` 자식), `SpRebalanceScenario`(rebalance). 새 enum
  `demand.MetricQualityFlag`, `rebalance.DemandEventType`.
- `SpInventoryMetric`에 V6 17개 컬럼을 모두 채우는 MVP-2 생성자를 추가했다. legacy
  컬럼은 `data-model.md` Phase 3 mapping대로 투영한다(`available_quantity`=
  currentAvailable, `average_daily_sales`=단순 관측일 평균, `coverage_days`=
  currentAvailable/baseline BASE, `classification`/`priority`는 새 exception/severity를
  반영하되 `REVIEW_REQUIRED`→`NON_ACTIONABLE`). 기존 MVP-1 생성자는 그대로다.
- `SpRebalanceRecommendation.createMvp2Candidate(...)`: `REJECTED/NONE/COMPARISON_ONLY`의
  nullable 수량을 지원하는 새 static factory. `SpAnalysisRunRepository`에 triple-key
  no-op/재시작/신규-run 조회를 추가했다.
- 새 공유 순수 helper `demand.RepresentativeEventSelection`: `DemandSignalClassification`의
  기존 inline 이벤트 필터링을 추출하고 `(startDate, eventCode)` 오름차순 tie-break를
  추가했다(`eventCode` tie-break는 이전에 없었음 — 동일 시작일 이벤트의 이전 순서는
  비결정적이었다). `classify()`는 이 helper에 위임하며 동작은 그대로다(기존 9개 테스트
  무수정 통과로 확인).

### Open items (다음 라운드)

Batch 입력 adapter(7개 bulk read group), job/tasklet 오케스트레이션(계산 순서·영속화·
transaction/재시도), 승인·`MANUAL`의 event-aware effective BASE parity 보정(섹션 6),
`MVP-2-GS-V1` golden scenario 테스트는 아직 구현되지 않았다. 상세는
[`current-task.md`](current-task.md)가 소유한다.

## Current verification evidence

- Codex 확장 표적 Oracle(MANUAL 라운드): approval package 전체 + manual evaluation +
  scenarios 99/99.
- Claude Phase 3 foundation 표적(이번 라운드, 실제 실행): `RepresentativeEventSelectionTest`
  5/5, `DemandSignalClassificationTest` 9/9(무수정 통과로 회귀 없음 확인),
  `Mvp2BatchEntityPersistenceMappingIT` 7/7.
- Oracle 전체 Backend build: **301/301**, skip 0, failures/errors 0.
- DB-free 전체 build: **301 total/243 passed/58 Oracle-conditioned skip**,
  failures/errors 0. `git diff --check` 통과.
- eligibility parity, donor BASE nullable, wrong run/input inventory 불변성 등 이전
  `MANUAL` 회귀 fixture도 이번 전체 재실행에서 함께 통과했다.

## Not implemented

- Phase 3 Batch의 입력 adapter, job/tasklet 오케스트레이션, 승인·`MANUAL` event-aware
  parity 보정, `MVP-2-GS-V1` golden scenario 테스트 (위 "Open items" 참고)
- 승인 transaction과 `MANUAL` application API의 REST DTO/ProblemDetail/React wiring
- MVP-2 계산의 REST/React application wiring과 실제 snapshot write path
- 실제 LLM provider adapter

## Cold evidence

- 이번 재리뷰 직전 state:
  [`../archive/state/2026-08-27-manual-rereview-current-task.md`](../archive/state/2026-08-27-manual-rereview-current-task.md),
  [`../archive/state/2026-08-27-manual-rereview-implemented-state.md`](../archive/state/2026-08-27-manual-rereview-implemented-state.md)
- 이번 재리뷰까지의 active worklog:
  [`../archive/worklogs/2026-08-27-through-manual-rereview.md`](../archive/worklogs/2026-08-27-through-manual-rereview.md)
- 그 이전 MANUAL 리뷰 archive는 위 문서의 historical links에서 이어진다.
