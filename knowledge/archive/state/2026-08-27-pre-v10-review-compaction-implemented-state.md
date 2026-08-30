# Implemented State

Last updated: 2026-08-27

이 문서는 현재 저장소에서 관찰되는 사실만 유지하는 hot-state 스냅샷이다.
수정·리뷰 순서는 worklog에만 남기고 여기에는 최종 동작, 검증 상태와 열린
결함만 기록한다.

## Baselines

- MVP-1의 동결된 지식 기준선은
  [`../milestones/MVP-1.md`](../milestones/MVP-1.md)다.
- MVP-1 코드 동작과 `V1`~`V5` Migration은 MVP-2의 호환 기준선이다.
- 현재 적용 대상 Migration은 `V1`~`V10`이며 기존 `V1`~`V9` 파일은 변경하지 않았다.
- 승인된 MVP-2 제품·업무 범위는 [`../project.md`](../project.md), 계산과 AI 경계는
  [`../business-rules.md`](../business-rules.md), Schema는
  [`../data-model.md`](../data-model.md)가 소유한다.

## Present in the repository

### Platform and MVP-1 compatibility

- Java 21, Spring Boot 4.1.0, Gradle Wrapper 9.5.1 Backend
- React 19, TypeScript 5.9, Vite 8 Frontend
- Oracle Database Free 23ai와 Flyway Schema
- 7일 기반 결정론적 `InventoryMetricCalculation`과 `RebalanceCalculation`
- JDBC-backed Spring Batch `inventoryAnalysisJob`; 실행 키는 MVP-1에서
  `(analysisDate, ruleVersion)`이며 domain-run guard도 유지한다.
- 분석 실행, 예외 목록·상세, 시뮬레이션, 결정 REST API와 React 목록·상세 화면
- AI-disabled 설명 API 경계. 실제 LLM provider adapter와 외부 호출은 없다.
- hot state, milestone checkpoint, active worklog와 cold archive를 분리한 지식
  운영 구조와 `stockpilot-resume`/`stockpilot-worklog`/`stockpilot-checkpoint` 스킬

MVP-1의 상세 불변조건과 최종 검증은 milestone checkpoint를 따른다. 현재 코드나
테스트와 checkpoint가 충돌하면 코드·Migration·실행 결과를 우선하고 checkpoint를
수정한다.

### MVP-2 Phase 1 — accepted

- `V6__evolve_stockpilot_mvp2_schema.sql`: legacy backfill, 버전 입력·결과·결정 이력과
  `SP_TRANSFER_DRAFT`, 호환성 Check Constraint
- `V7__load_mvp2_synthetic_scenarios.sql`: `MVP-2-GS-V1` GS-01~GS-06 입력
- `V8__add_mvp2_domain_comments.sql`: 허용값과 `SYNTHETIC`/`ASSUMPTION` 경계
- `V9__localize_and_simplify_domain_comments.sql`: V8의 9개 테이블·149개 컬럼
  Comment를 한국어로 교체. 일반 컬럼은 간결한 명사형, 코드·상태·플래그 컬럼은
  허용값별 한국어 의미를 사용하며 데이터와 Schema 구조는 변경하지 않는다.
- `data/seed/mvp2`: 6 products, 3 stores, 348 inventory rows, 336 sales rows,
  1 event, 1 inbound, 1 open transfer, 2 routes, 12 store-SKU policies
- `scripts/validate-seed.ps1`은 MVP-1 검증을 유지한 채 MVP-2 계약을 검증한다.
- 깨끗한 Oracle `V1`→`V8`과 기존 `V5`→`V8` 업그레이드가 모두 27개 Backend
  테스트를 통과했다. 제약·Comment·legacy backfill과 GS-01~GS-06 입력을 readback했다.
- 기존 Oracle `V8`→`V9` 업그레이드와 Backend 전체 build에서 157/157 테스트가
  통과했다. Flyway V9 성공 이력과 대표 일반/코드 컬럼의 한국어 Comment를
  Oracle에서 readback했다.

### MVP-2 Phase 2 — accepted increments

`com.bapegg.stockpilot.demand`의 다음 순수 Java 범위는 Spring/JPA/Oracle과 독립되어
있고 Codex 리뷰에서 승인됐다.

- 28일 관측 값과 통계: `DailyDemandObservation`, `DemandObservationWindow`,
  `DemandObservationStatistics`
  - invalid snapshot과 실제 OOS를 분리한다.
  - OOS일의 양수 판매를 거부하고 invalid snapshot 판매는 통계에서 제외한다.
- 신호와 confidence: `PlanHorizon`, `DemandEvent`, `DemandSignalClassification`
  - `DATA_INSUFFICIENT`에서도 관련 이벤트와 `INCOMPLETE_EVENT_DATA`를 보존한다.
  - 기간·경로 입력의 비음수 제약을 Java 경계에서도 검증한다.
- low/base/high 수요율: `DemandRateCalculation`
  - `UNEXPLAINED_SPIKE`일 때만 spike evidence day를 baseline에서 제외한다.
  - 이벤트 기간 제외와 uplift helper를 분리했으며 scenario-window 적용은 아래
    시나리오 결과 증분에서 사용한다.
- 재고 예상값과 예외: `InventoryProjection`, `InventoryExceptionClassification`
  - 음수 current/projected 입력은 `NON_ACTIONABLE`, `NONE`/`LOW` confidence는
    `REVIEW_REQUIRED`로 간다.
  - 합산·기간 계산은 checked/widened arithmetic을 사용하며, canonical constructor가
    다섯 근거값과 파생 수량의 일관성을 직접 검증해 `calculate(...)`를 우회해도
    provenance가 깨지지 않는다.
  - GS-05는 confirmed inbound 전후 분기로 재현된다.
  - 가장 빠른 도착 시점의 BASE 예상재고가 0 이하이면 후보 여부와 무관하게
    `CRITICAL`이다. 비-CRITICAL 목표 커버리지 부족은 호출자가 전달한 실행 가능
    후보 존재 여부에 따라 `HIGH` 또는 severity `null`로 결정된다. 분류기는
    후보 탈락 규칙을 재구현하지 않는다.
- 후보·경로: `TransferCandidateEvaluation`, `TransferCandidateRejectionReason`,
  `TransferRoute`
  - 하나의 SKU와 서로 다른 두 매장을 입력 경계에서 표현·검증한다.
  - 모든 탈락 사유를 불변 집합으로 보존하고 선언된 우선순위로 대표 사유를 고른다.
  - 도착일 BASE 예상재고가 정확히 0이면 허용하고 음수일 때만
    `LEAD_TIME_TOO_LONG`으로 거부한다.
  - GS-06의 `OWNER_MISMATCH`와 `LEAD_TIME_TOO_LONG` 동시 발생을 재현한다.
  - 최소수량·포장 배수·경로 최대·수용량 feasibility를
    `DISPLAY_MINIMUM_VIOLATION`으로 매핑한 해석은 승인됐다.

### MVP-2 Phase 2 — accepted scenario increment

- 시나리오 결과: `TransferScenarioType`, `TransferScenarioResult`, `TransferScenarioSet`
  - `NO_ACTION/CONSERVATIVE/BASE/AGGRESSIVE` 수량을 공급 가능량·도착 매장
    필요량·경로 최대·수용량의 최솟값에서 포장 배수로 내림해 산정하고, 경로
    최소수량 미만이면 수량 0과 실제 내림 수량을 명시한 경고 문자열을 반환한다.
  - `NO_ACTION`은 이동수량만 0이다. receiver 전후 커버리지·위험은 실제
    (해당되면 uplift 반영) BASE 수요율을 그대로 사용하며, 수요 자체가 0인
    것으로 취급하지 않는다.
  - 각 시나리오는 양쪽 매장의 이동 전후 가용재고·커버리지(내부 rate 기준
    scale 12)·위험 코드·`leadTimeDays`·`expectedArrivalDate`와, 확정 입고·
    진행 중 이동의 방향·수량을 하나의 flag 대신 개별 필드
    (`receiverInboundArrivingBeforeTransfer`/`receiverOpenTransferInbound`/
    `receiverOpenTransferOutbound`/`donorInboundArrivingBeforeDispatch`/
    `donorOpenTransferOutbound`/`donorAlreadyApprovedDraftQuantity`)로 명시적으로
    보존한다. `expectedArrivalDate`는 순수 Java 계층에 시각·timezone 정보가
    없다는 책임 경계를 문서화한 날짜 전용 값이며, `data-model.md`의
    `expected_arrival_at TIMESTAMP WITH TIME ZONE` 계약은 향후 영속화 계층이
    별도 정책으로 채워야 한다.
  - `receiverMaximumCapacity`는 V6 `ck_sp_policy_values`(`maximum_capacity > 0`)와
    동일하게 양수만 허용하며, 0·음수는 `IllegalArgumentException`으로 거부되어
    수량 0인 정상 시나리오로 숨지 않는다.
  - `KNOWN_EVENT`는 각 자동 시나리오 고유의 도착~목표 커버리지 구간과 관련
    이벤트가 겹치고 uplift가 완전할 때만 해당 low/base/high 수요율에 곱한다
    (곱한 직후 scale 12 HALF_UP). `signalType != KNOWN_EVENT`이거나 구간이
    겹치지 않으면 uplift를 적용하지 않는다.
  - `calculate(...)`가 이미 계산된 `DemandConfidence`를 직접 받아
    `confidence==NONE || confidence==LOW`(단, `VARIABLE`은 제외)이면
    `IllegalStateException`으로 자동 시나리오 계산 자체를 차단한다. 신호 유형
    목록을 다시 나열하지 않고 confidence 하나로 판정해 품질 플래그가 있는
    `STABLE_REPEAT` 같은 경우도 놓치지 않는다(`InventoryExceptionClassification`
    에서 배운 것과 동일한 교훈).
  - `route.active()==false` 또는 `receiverProjection`/`donorProjection`의
    `isInputInvalid()==true`이면 계산 자체를 거부한다(각각
    `IllegalArgumentException`/`IllegalStateException`).
  - `receiverRiskCode`/`donorRiskCode`는 시나리오 이후 수량을 목표·보호 수량과
    비교하는 간략 지표이며 28일 관측을 다시 분류하지 않는다. 이 단순화 자체는
    시나리오 가정 계산의 범위로 허용된다.
  - `VARIABLE` 신호는 `comparisonOnly=true`로 표시되고, confidence가
    `LOW`여도(비교 전용이 목적이므로) 네 시나리오 모두 계산되지만 대표
    추천으로 표시되지 않는다.
  - GS-01(양쪽 매장 보호를 포함한 세 자동 시나리오)과 GS-02(실제 uplift
    1.20/1.50/1.80 반영, CONSERVATIVE/BASE/AGGRESSIVE 수량 15/19/24, `NO_ACTION`도
    uplift된 BASE 수요율 3.0을 사용)를 실제 Seed 수치로 재현했다.
  - `InventoryProjection`도 확정 입고·진행 중 이동 5개 raw 입력을 파생값과 함께
    필드로 보존하도록 넓혔다(`TransferScenarioResult`가 그대로 복사해 온다).
    canonical constructor 자체가(`calculate(...)`를 우회해도) `currentAvailable`과
    다섯 근거값에서 6절 식으로 유도한 값과 `projectedReceiverBeforeDemand`/
    `projectedDonorAtDispatch`가 정확히 일치하는지 `long`/checked 변환으로
    검증하므로, provenance가 서로 다른 객체를 만들 수 없다.
  - 검증: DB 없는 `Backend build --rerun-tasks` — 151 non-skipped, 0
    failures/errors, Oracle-conditioned 6개 정상 skip. Oracle 통합 검증은
    미실행/해당 없음(DB 코드 변경 없음).

### MVP-2 Phase 2 — accepted approval-validation increment

- 승인 요청 검증(10절): `DecisionStatus`, `ApprovalRequest`, `RecommendationBasis`,
  `ApprovalOutcome`, `ApprovalRequestValidation`
  - `ApprovalRequest`의 canonical constructor가 상태별 shape을 검증한다:
    `PENDING`은 선택수량 `null`, `APPROVED`는 선택수량 양수, `HELD`/`REJECTED`/
    `EXPIRED`는 선택수량 `null`이고 reason code·설명이 필수.
  - `ApprovalRequest`에 `policyException`(boolean) 필드를 추가했다. 상태별
    shape 검증에는 관여하지 않고 `ApprovalRequestValidation.validate`가
    사유 필수 여부를 판단할 때만 사용한다.
  - `RecommendationBasis`에 `candidateEligible`(boolean) 필드를 추가했다. 호출자가
    이 basis를 만들 때 반드시 `TransferCandidateEvaluation.eligible()`을
    같은 donor-receiver-route-SKU 조합으로 다시 계산해 채워야 하며, 이 값 자체는
    `RecommendationBasis`나 `ApprovalRequestValidation`이 재구현하지 않는다.
  - `ApprovalRequestValidation.validate(request, basis)`는 `analysisRunId`/
    `inputSnapshotVersion`/`ruleVersion`/후보 버전 중 하나라도 최신 근거와
    다르면 `STALE_RECOMMENDATION`이다.
  - `APPROVED`는 수량 한도 검사보다 먼저, 그리고 독립적으로
    `basis.candidateEligible()`을 확인한다. `false`이면 수량이 모든 수량 한도를
    만족해도 `STALE_RECOMMENDATION`이다(소유권 불일치, 비활성 경로, 긴
    lead time, 입고로 부족 해소, 진행 중 이동 충돌 같은 section 7의 비수량
    탈락 사유를 basis 하나로 반영).
  - `APPROVED`는 재계산된 공급(`donorTransferableQuantity`)·경로
    (최소/배수/최대)·수용량 한도를 모두 만족해야 하며, 위반하면(정책 예외
    표시와 사유 유무 모두 무관하게) `STALE_RECOMMENDATION`이다.
  - `APPROVED` 선택수량이 재계산된 BASE 추천 수량과 정확히 같고
    `policyException==false`이면 사유 없이 허용된다. 수량이 다르거나
    `policyException==true`이면 reason code와 설명이 없을 때
    `IllegalArgumentException`을 던진다 — 이는 staleness 결과가 아니라 요청
    자체의 shape 결함으로 취급한다.
  - `ApprovalRequestValidation`의 canonical constructor가 `outcome != null`을
    강제해 직접 생성해도 유효하지 않은(`stale()==false`인 null outcome) 값을
    만들 수 없다.
  - `policyException`은 `APPROVED` 상태에서만 `true`일 수 있어 비승인 상태가
    정책 예외 승인 의미를 잘못 보존하지 않는다.
  - 저장·잠금·재고 변경은 하지 않으며 검증 결과만 반환한다.
  - 검증: DB 없는 `Backend build --rerun-tasks` — 182 non-skipped, 0
    failures/errors, Oracle-conditioned 6개 정상 skip. Oracle 통합 검증은
    미실행/해당 없음(DB 코드 변경 없음).

### MVP-2 승인 트랜잭션 — V10 기반 증분 존재, Codex 리뷰 미승인

V10 Migration과 기존 MVP-1 결정 Entity의 최소 호환 수정만 존재한다. JPA mapping,
`@Transactional` use case, REST/오류 계약, 동시성 테스트는 아직 없다.

- `V10__add_approval_transaction_support.sql`: 기존 `V1`~`V9`는 수정하지 않았다.
  - `sp_rebalance_decision`에 `decision_request_id`(unique, 기존 행은
    `LEGACY-{decision_id}`로 backfill)와 `policy_exception_flag`(기존 행은
    `N`)를 추가하고 둘 다 `NOT NULL`로 전환했다.
  - 신규 `sp_approval_basis`: 결정과 1:1(`decision_id` unique FK)이나 승인된
    설계의 basis contract version, 양쪽 projection과 기존 활성 draft 합계가 없고,
    `analysis_run_id`가 숫자 FK가 아닌 `VARCHAR2`다.
  - 신규 `sp_error_catalog`/`sp_error_constraint_map`: REST 오류 코드별
    HTTP 상태·재시도 가능 여부·한국어 문구와, 알려진 Oracle 제약명
    (`UQ_SP_DEC_REQUEST_ID`→`IDEMPOTENCY_KEY_REUSED`,
    `UQ_SP_DEC_REC_SEQ`→`DECISION_ALREADY_TERMINAL`)의 오류 코드 매핑을
    시드했다.
  - `sp_transfer_draft(donor_store_id, sku_id, draft_status)` 인덱스를
    추가해 향후 활성 draft 합계 조회를 지원한다.
  - 일부 한국어 Comment를 포함했지만 신규 컬럼 전체를 다루지는 않는다.
- `SpRebalanceDecision`(기존 MVP-1 전용 Entity)에 `decisionRequestId`/
  `policyExceptionFlag`를 추가해 새 `NOT NULL` 컬럼과 매핑을 맞췄다. 기존
  5-arg 생성자는 호출자가 idempotency key를 주지 못하므로 `"MVP1-" + UUID`를
  자동 생성하고 `policyExceptionFlag="N"`을 고정한다. `policy_exception_flag`는
  Oracle에서 `CHAR(1 CHAR)`이므로 Hibernate 기본 VARCHAR2 추론과 어긋나
  `columnDefinition = "CHAR(1 CHAR)"`를 명시했다(Schema validate 실패로 발견).
  `@OneToOne(unique=true)`를 append-only `@ManyToOne`으로 바꾸는 작업과
  `SpTransferDraft`/`SpApprovalBasis` Entity 추가는 다음 단위(JPA mapping)다.
- 열린 리뷰 결함: 오류 카탈로그는 목표 `title_ko`, `default_detail_ko`,
  `active_flag`, `updated_at`과 승인용 세부 코드를 제공하지 못하고, 순번 unique 위반을 terminal
  결정 오류로 잘못 매핑한다. `policy_exception_flag='Y'`도 승인 상태로 제한되지
  않는다. V10 자체를 수정하지 않고 V11에서 보완해야 한다.

## Not implemented

- `MANUAL` 시나리오의 부작용 없는 수량·제약 재검증
- 승인 트랜잭션의 JPA mapping(append-only `@ManyToOne`, `SpTransferDraft`/
  `SpApprovalBasis` Entity), `@Transactional` use case, REST/오류 계약,
  donor 동시 잠금 실제 동시성 테스트
- V10 리뷰 결함을 보완할 V11 Migration과 V10/V11 전용 Oracle 제약·Comment 테스트
- MVP-2 계산(수요·예외·후보·시나리오)의 JPA persistence, Batch, REST API,
  React 화면
- 실제 LLM provider adapter

Schema나 Seed가 존재한다는 이유로 위 기능을 구현 완료로 추론하지 않는다.

## Latest verification evidence

- 승인 트랜잭션 V10 Codex 리뷰: DB 없는 `Backend build --rerun-tasks`는 188 total,
  182 passed, 6 Oracle-conditioned skips; 현재 Oracle V10에서는 188/188 통과.
  기존 회귀는 보존됐지만 V10 전용 자동 테스트와 clean `V1`→`V10` 검증은 없으며,
  위 Schema/오류 계약 결함 때문에 증분은 미승인이다.
- 10절 순수 Java 승인 요청 검증 Codex 재리뷰 승인. DB 없는
  `Backend build --rerun-tasks`: 188 total, 182 passed, 6 Oracle-conditioned
  skips, 0 failures/errors; compile, jar, check 통과. 승인 요청 10개와 validator
  20개 테스트가 상태·stale·후보·수량·정책 예외 경계를 검증한다.
- 9절 `HIGH` severity를 `TransferCandidateEvaluation.eligible()` 결과와 연결한
  증분 Codex 재리뷰 승인. DB 없는 `Backend build --rerun-tasks`: 158 total, 152
  passed, 6 Oracle-conditioned skips, 0 failures/errors; compile, jar, check 통과.
- V9 한국어 Comment: Oracle-backed `Backend build --rerun-tasks` 157/157 통과,
  Flyway V9 `success=1`, 대표 테이블·컬럼 Comment readback 일치. V8과 대상 비교
  결과 9개 테이블·149개 컬럼이 일치하며 V9에는 Comment 문장만 존재한다.
- 시나리오 결과와 `InventoryProjection` provenance 증분 Codex 재리뷰 승인.
  DB 없는 `Backend build --rerun-tasks`: 157 total, 151 passed, 6
  Oracle-conditioned skips, 0 failures/errors; compile, jar, check 통과.
- MVP-2 Phase 1: clean `V1`→`V8` 및 existing `V5`→`V8` Oracle 경로에서
  27/27 Backend tests 통과; Seed validation과 Frontend production build 통과.
- MVP-1 최종 검증 증거는 [`../milestones/MVP-1.md`](../milestones/MVP-1.md)에
  고정했다.
- 지식베이스 변경: 모든 local Markdown link와 `git diff --check` 통과;
  세 StockPilot skill 모두 `quick_validate.py` 통과. Backend 동작은 변경하지 않아
  애플리케이션 build는 재실행하지 않았다.

## Historical evidence

- 압축 전 current task:
  [`../archive/state/2026-08-26-pre-compaction-current-task.md`](../archive/state/2026-08-26-pre-compaction-current-task.md)
- 압축 전 implemented state:
  [`../archive/state/2026-08-26-pre-compaction-implemented-state.md`](../archive/state/2026-08-26-pre-compaction-implemented-state.md)
- 압축 전 raw worklog:
  [`../archive/worklogs/2026-08-before-knowledge-compaction.md`](../archive/worklogs/2026-08-before-knowledge-compaction.md)

이 archive는 회귀 원인 조사나 감사가 필요할 때만 검색하며 Resume의 기본 읽기
대상이 아니다.

