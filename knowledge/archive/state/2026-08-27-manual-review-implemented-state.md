# Implemented State

Last updated: 2026-08-27

이 문서는 현재 저장소에서 관찰되는 사실만 유지하는 hot-state 스냅샷이다. 완료된
수정·리뷰 과정은 worklog/archive에 두고 실제 동작, 검증과 열린 결함만 기록한다.

## Baselines

- MVP-1의 동결 기준선은 [`../milestones/MVP-1.md`](../milestones/MVP-1.md)다.
- MVP-1 코드 동작과 `V1`~`V5`는 MVP-2 호환 기준선이다.
- 저장소에는 `V1`~`V13` Migration이 있으며 기존 `V1`~`V12`는 수정하지 않았다.
- 제품 범위는 [`../project.md`](../project.md), 계산·AI 경계는
  [`../business-rules.md`](../business-rules.md), 물리 계약은
  [`../data-model.md`](../data-model.md)가 소유한다.

## Present in the repository

### Platform and MVP-1 compatibility

- Java 21, Spring Boot 4.1.0, Gradle Wrapper 9.5.1 Backend
- React 19, TypeScript 5.9, Vite 8 Frontend
- Oracle Database Free 23ai, Flyway, Hibernate `ddl-auto=validate`
- 7일 MVP-1 Batch 분석과 목록·상세·시뮬레이션·결정 REST/React Vertical Slice
- AI-disabled 설명 API 경계; 실제 LLM provider 호출은 없음

### MVP-2 Phase 1 — accepted

- `V6`: 버전 입력·결과·결정 이력, 신규 원본/Scenario/Draft Schema와 legacy backfill
- `V7`: `MVP-2-GS-V1` GS-01~GS-06 SYNTHETIC 입력
- `V8`: 최초 MVP-2 도메인 Comment
- `V9`: V8과 같은 9개 테이블·149개 컬럼 Comment를 간결한 한국어로 교체
- `data/seed/mvp2`: 6 products, 3 stores, 348 inventory rows, 336 sales rows,
  1 event, 1 inbound, 1 open transfer, 2 routes, 12 store-SKU policies
- `scripts/validate-seed.ps1`은 MVP-1을 보존하며 MVP-2 Seed 계약을 검증한다.

Oracle Phase 1 최종 증거는 clean `V1`→`V8`, existing `V5`→`V8` 27/27와 기존
`V8`→`V9` Backend 157/157, Flyway 성공 및 Comment readback이다.

### MVP-2 Phase 2 pure Java — accepted

`com.bapegg.stockpilot.demand`는 Spring/JPA/Oracle과 독립된 결정론적 계층이다.

- 28일 관측과 통계: invalid snapshot과 OOS 검열을 분리하고 양수 OOS 판매를 거부
  - 신호·confidence: DATA_INSUFFICIENT/KNOWN_EVENT/spike/intermittent/stable/variable,
    품질 flag와 LOW/NONE 경계
- low/base/high 수요율: 유효 주간 백분위, scale 12 `HALF_UP`, 조건부 event uplift
- 재고 projection·예외: 입고·진행 중 이동·승인 draft 근거를 보존하고 checked
  arithmetic과 provenance 불변조건 적용
- 후보·경로: 모든 탈락 사유 보존, 소유권·경로·lead time·수량·용량 제약 평가
- Scenario: `NO_ACTION/CONSERVATIVE/BASE/AGGRESSIVE`, 양쪽 매장 전후 재고·커버리지·
  위험·도착일·근거 반환; VARIABLE은 비교 전용
- 승인 검증: 상태별 request shape, 네 버전 일치, 최신 후보 실행 가능성, donor/경로/
  용량 hard limit, 수량 변경·정책 예외 사유와 non-null outcome 검증

순수 승인 검증 최종 DB-free 증거는 188 total, 182 passed, Oracle-conditioned 6 skip,
0 failures/errors다.

### V10/V11/V12/V13 approval foundation — accepted

- `sp_rebalance_decision`에 unique `decision_request_id`와 `policy_exception_flag` 추가;
  기존 결정은 `LEGACY-{decision_id}`/`N`으로 backfill (V10)
- `sp_approval_basis`, `sp_error_catalog`, `sp_error_constraint_map` 생성 (V10)
- `sp_transfer_draft(donor_store_id, sku_id, draft_status)` 인덱스 추가 (V10)
- 기존 MVP-1 `SpRebalanceDecision` 생성 경로는 `MVP1-{UUID}`와 `N`을 채워 V10
  NOT NULL 컬럼과 호환 (V10)
- V11이 V10의 다섯 finding을 모두 수정했다: `sp_approval_basis`에
  `basis_contract_version`/`receiver_projected_before_demand`/
  `donor_projected_at_dispatch`/`already_approved_draft_quantity` 추가,
  `analysis_run_id`를 `sp_analysis_run` FK `NUMBER(19,0)`로 전환,
  `candidate_eligible_flag`를 `'Y'`만 허용하도록 좁힘; `sp_error_catalog`에
  `title_ko`/`default_detail_ko`/`active_flag`/`updated_at`과 승인용 세부 코드
  5개(`INVALID_REQUEST`/`INVALID_DECISION_REQUEST`/`RECOMMENDATION_NOT_FOUND`/
  `INVALID_DECISION_TRANSITION`/`DECISION_CONFLICT`) 추가;
  `UQ_SP_DEC_REC_SEQ`를 `DECISION_ALREADY_TERMINAL`이 아니라 `DECISION_CONFLICT`로
  재매핑; `policy_exception_flag='Y'`를 `MVP-2 APPROVED`로만 제한하는
  `ck_sp_dec_policy_exception_scope` 추가; V10/V11 신규 컬럼 전체에 한국어 Comment
  완성.
- V12가 이전 P2 finding 대부분을 보정했다: `V10`/`V11`의 신규·변경 컬럼 Comment를
  V9 양식(일반 컬럼은 간결한 명사형, `donor`/`receiver`→`출고 매장`/`입고 매장`,
  Java 심볼·사유 설명 제거, `생성 시각`/`수정 시각`→`생성일시`/`수정일시`)으로
  교체했고, `sp_error_catalog.error_code`에 13개 코드 전부의 한국어 의미를
  `flag_code` 선례와 동일한 `(코드: 의미, ...)` 형식으로 나열했다. 데이터·제약·
  인덱스는 바꾸지 않았다.
- V13이 V12에 남은 번역 가능한 영어 Comment 용어 2건을 교체했다:
  `추천 BASE 수량`→`추천 기준수량`(`sp_rebalance_scenario.scenario_type`의
  기존 `BASE: 기준` 선례와 통일), `기승인 활성 Draft 합계 수량`→`기승인 활성
  이동 초안 합계수량`(`sp_transfer_draft` 테이블 Comment `재고 이동 초안`
  선례와 통일). 데이터·제약·인덱스는 바꾸지 않았다.
- 강화된 `ApprovalTransactionSchemaIT`(9 tests, JPA Entity 없이 raw SQL, 각
  테스트 `@Transactional` rollback)가 오류 카탈로그 13개 행 전체의 정확한
  메타데이터, FK/unique 제약의 실제 `constraint_type`·대상 테이블·컬럼, V12/V13
  Comment의 정확한 문자열을 실제 Oracle에서 검증한다. 실제 중복 insert가
  `UQ_SP_DEC_REQUEST_ID`/`UQ_SP_BASIS_DECISION`에서 거부되는지도 별도
  테스트로 고정한다.
- `data-model.md`의 물리 모델을 실제 Schema와 맞췄다: `SP_APPROVAL_BASIS`
  Identity column group을 `approval_basis_id`(identity PK) +
  `decision_id`(unique FK)로 정정, Recommendation→Decision ERD를
  `||--o|`(V1)에서 `||--o{`(V6 이후 append-only 1:N)로 수정, 존재하지 않던
  `SP_REBALANCE_DECISION.approval_basis_id` 표기를
  `SP_APPROVAL_BASIS.approval_basis_id`로 정정, 오류 코드 13개 전부를 승인
  전용 10개/일반 fallback 3개로 구분해 나열, Migration 표에 `V13` 추가.

Codex 최종 독립 재검증에서 기존 Oracle의 13개 Migration validate와 Backend
197/197, 임시 빈 Oracle의 clean `V1`→`V13` 및 197/197가 통과했다. 임시
컨테이너는 검증 후 제거했다.

## Review status

V10~V13 승인 트랜잭션 기반, JPA persistence mapping, 승인 트랜잭션 `@Transactional` use case까지
Codex 독립 재검증을 통과해 accepted다. 아래 "`MANUAL` 수량 시험"은 Claude 구현이 끝났고 Codex
재검증을 기다린다.

### 승인 트랜잭션 JPA persistence mapping — accepted

Migration은 변경하지 않고 Java Entity/Repository/Service만 보정했다.

- `SpRebalanceDecision`: `@OneToOne` → `@ManyToOne`(V6가 append-only
  `uq_sp_dec_rec_seq(recommendation_id, decision_sequence)`로 바꿨다).
  `selectedQuantity`/`reason`의 `nullable` 오류를 정정. `decisionSequence`/
  `decisionContractVersion`/`reasonCode`/`recommendationVersion`(V6) 추가.
  기존 MVP-1 생성자는 이 필드들을 기존 DB `DEFAULT`와 동일한 값으로 채워
  기존 생성·조회 경로를 그대로 보존한다.
- `rebalance.DecisionStatus`에 `PENDING`/`HELD`/`EXPIRED`를
  추가해 V6의 5개 물리 상태를 모두 표현한다. `RebalanceDecisionService
  .decide`가 `decisionStatus`를 `APPROVED`/`REJECTED`로 명시적으로 제한해
  MVP-1 REST 경계는 넓어지지 않는다.
- 단건 `Optional`을 반환하던 `findByRecommendation
  _RecommendationId`를 제거하고 `existsByRecommendation_RecommendationId`
  (MVP-1 중복 결정 방지)와 `findFirstByRecommendation
  _RecommendationIdOrderByDecisionSequenceDesc`(최신 결정 = 최대 순번)로
  분리했다. `InventoryExceptionService.toRecommendationView`는 후자를
  쓰도록 고쳤고, `RebalanceDecisionService.decide`는 전자를 쓴다. 둘 다
  결정이 여러 건이어도 예외를 던지지 않는다.
  `findAllByRecommendation_RecommendationIdOrderByDecisionSequenceAsc`
  (append-only 이력 전체 조회)도 유지한다.
- 신규 `SpTransferDraft`/`SpApprovalBasis` Entity+Repository —
  `V6`/`V10`/`V11` 컬럼을 매핑했다. 둘 다 결정과 1:1(unique FK).
  `SpApprovalBasis.candidateEligibleFlag`는 생성자에서 항상 `"Y"`(V11의
  `ck_sp_basis_eligible` 제약과 일치).
- `SpTransferDraft`에 `markReady()`가 있다. `CREATED`에서만
  호출 가능(그 외 상태면 `IllegalStateException`)하고 상태를 `READY`로
  바꾸며 `updatedAt`을 직접 채운다. V6에 update trigger가 없어
  `updated_at` 갱신 책임을 이 메서드가 명시적으로 진다. 이번 MVP-2 범위의
  전이는 `CREATED`→`READY` 하나뿐이다.
- `SpAnalysisRun.inputSnapshotVersion`(V6), `SpInventorySnapshot`의
  `snapshotAt`/`outOfStockFlag`/`inputSnapshotVersion`(V6, 읽기 전용),
  `SpRebalanceRecommendation`의 `routeId`/`candidateStatus`/`candidateVersion`/
  `recommendationMode`/`projectedReceiverAtArrival`/`projectedDonorAtDispatch`/
  `receiverCapacityRemaining`/`evaluatedAt`(V6) 추가. `routeId`는
  `sp_store_transfer_route` Entity 없이 순수 FK 값으로만 매핑했다(아직 이
  관계를 join하는 Java 코드가 없음).
- 신규 `CandidateStatus`/`RecommendationMode`/`DraftStatus` enum.
- 강화된 `ApprovalPersistenceMappingIT`(5 tests)와 신규
  `ApiGoldenScenarioIT.decisionWorkflowRejectsNonMvp1DecisionStatuses`가
  세 보정을 실제 Oracle에서 검증한다: raw SQL로 넣은
  `HELD`/`EXPIRED`/`REJECTED` 행의 정상 역직렬화, `findFirstBy...Desc`가
  최신 결정을 반환, `existsBy...`가 다건에서도 예외 없이 동작, 실제 REST
  엔드포인트가 `PENDING`/`HELD`/`EXPIRED`를 400으로 거부, `markReady()`의
  상태 전이와 `updated_at` 변경, 두 번째 호출의 `IllegalStateException`.

Codex 독립 검증: 표적 Oracle IT 11/11, 기존 Oracle V13 전체 Backend build
203/203(skip 0), DB 없는 build 203 total/182 passed/21 Oracle-conditioned skips가
모두 실패·오류 없이 통과했다. Migration 변경이 없어 clean `V1`→`V13` 재실행은
생략했고 `git diff --check`도 통과했다. 열린 persistence mapping finding은 없다.

### 승인 트랜잭션 `@Transactional` use case — accepted

Migration 추가 없이(V13 유지) approval application API와 필요한 JPA read/lock
query를 구현했다. REST/ProblemDetail/React 연결은 이 범위에 포함하지 않았다.

- `ApprovalTransactionFacade`가 정규화된 command와 SHA-256 fingerprint로 동일 key
  replay를 처리하고, `ApprovalTransactionExecutor`가 recommendation → donor snapshot
  순서의 3초 pessimistic lock 안에서 최신 근거를 재조회한다.
- APPROVED는 donor metric run, 양쪽 snapshot version/SKU, route donor/receiver/version/
  active를 현재 추천과 교차 검증한다. 불일치는 `STALE_RECOMMENDATION`이며 decision,
  basis, draft를 쓰지 않는다.
- HELD/REJECTED는 append-only decision을 저장한다. APPROVED는 순수 Java 재계산 뒤
  decision + basis + CREATED transfer draft를 한 transaction으로 저장한다.
- persistence constraint는 실패 transaction rollback이 끝난 뒤 facade의 별도 read
  transaction에서 `sp_error_constraint_map`으로 번역한다. lock/연결/미매핑 오류도
  안정된 application error code로 분류하며 DB 원문은 노출하지 않는다.
- basis 또는 draft 저장 실패 시 세 aggregate가 모두 rollback된다. 같은 key 경합,
  같은 recommendation 경합, shared donor 경합과 lock timeout에서 승자/패자의
  decision·basis·draft 개수 및 draft 수량까지 검증한다.

Codex 독립 재검증: 표적 Oracle IT 30/30(`ApprovalTransactionExecutorIT` 15,
`PersistenceErrorTranslatorTest` 9, `ApprovalTransactionAtomicityIT` 2,
`ApprovalTransactionConcurrencyIT` 4), 기존 Oracle 전체 Backend build
258/258(skip 0), DB 없는 전체 258 total/216 passed/42 Oracle-conditioned skip가
실패·오류 없이 통과했다. `git diff --check`도 통과했고 열린 finding은 없다.

### `MANUAL` 수량 시험 — Claude 구현 완료, Codex 재검증 대기

Migration 추가 없이, 승인 트랜잭션과 동일한 근거·잠금 순서를 공유하는 side-effect-free 미리보기
API를 구현했다. 상세 계약은 [`current-task.md`](current-task.md)가 소유한다.

- 신규 순수 `demand.TransferEffectProjection`: 전후 가용재고·커버리지·위험을 계산하는 공통 공식을
  `TransferScenarioSet`에서 추출했다 — 자동 시나리오와 수동 시험이 이 공식을 복제하지 않는다.
  `TransferScenarioSet`의 결과값은 리팩터링 전후 동일함을 `TransferScenarioSetTest`(21)로 확인했다.
- 신규 순수 `demand.ManualQuantityEvaluation`/`ManualQuantityProjection`/`ManualQuantityViolation`:
  기존 `ApprovalBasisRecalculation.calculate`를 그대로 호출해 eligibility·한도를 구하고, 6개 고정
  순서 위반, `maximumFeasibleQuantity`/`suggestedQuantity` 자동 제안, feasible일 때만 non-null인
  projection을 계산한다.
- 신규 `approval.CurrentApprovalBasisLoader`: 기존 `ApprovalTransactionExecutor.recalculate`의
  version 검증·donor 잠금·route/policy/inbound/open-transfer/draft 조회·교차검증을 그대로 추출해
  `LoadedApprovalBasis`(pure record, JPA entity 미노출)로 반환한다. `ApprovalTransactionExecutor`는
  이 loader를 쓰도록 리팩터링했고 APPROVED 경로의 동작은 바뀌지 않았다.
- 신규 `approval.ManualQuantityTestCommand`(`INVALID_REQUEST`)/`ManualQuantityTestResult`/
  `ManualQuantityTestExecutor`: recommendation 잠금 → terminal 확인 → loader의 version 검증·donor
  잠금·basis 로드 → `ManualQuantityEvaluation` 순서로 실행하며 아무 것도 저장·flush하지 않는다.
  candidate-ineligible은 예외가 아니라 `feasible=false`인 정상 결과다.
- `DecisionStatus.isTerminalForFurtherDecision()`을 추가해 두 executor가 terminal 판정을 공유한다.

Codex 독립 재검증 전 실제 증거: 단위 테스트(`ManualQuantityEvaluationTest` 13,
`ManualQuantityTestCommandTest` 8) 전부 통과. Oracle IT `ManualQuantityTestExecutorIT`(5 — 활성
draft 반영, terminal, stale rule version, wrong route donor, candidate-ineligible 정상 결과)와
`ApprovalTransactionConcurrencyIT`에 추가한 manual-test lock-timeout 시나리오(실제 `ORA-00054`
확인) 모두 통과. 리팩터링 회귀로 기존 approval 표적 48개(`ApprovalTransactionExecutorIT` 15,
`ApprovalTransactionCommandTest` 11, `IdempotencyFingerprintTest` 6,
`PersistenceErrorTranslatorTest` 9, `ApprovalTransactionAtomicityIT` 2,
`ApprovalTransactionConcurrencyIT` 5)와 `TransferScenarioSetTest`(21)가 그대로 통과해 추출이 기존
승인 수량·원자성·동시성·자동 시나리오 결과를 바꾸지 않았음을 확인했다. 기존 Oracle 전체 Backend
build 285/285(skip 0), DB 없는 전체 build 285 total/237 passed/48 Oracle-conditioned skip, 0
failures/errors. `git diff --check` 통과.

다음 구현 범위와 완료 조건은 [`current-task.md`](current-task.md)가 소유한다.

## Not implemented

- REST controller/DTO/ProblemDetail wiring for 승인 트랜잭션·`MANUAL` 수량 시험 application API
  (application API 자체는 구현 완료 — 위 섹션 참고)
- MVP-2 계산의 Batch/REST/React application wiring
- 실제 MVP-2 재고 스냅샷 적재(쓰기) 경로
- 실제 LLM provider adapter

## Current verification evidence

- `MANUAL` 수량 시험(Codex 재검증 대기): 기존 Oracle Backend build 285/285(skip 0); DB 없는 build
  285 total/237 passed/48 Oracle-conditioned skip, 0 failures/errors. `git diff --check` 성공.
- 승인 트랜잭션 `@Transactional` use case(accepted): 표적 Oracle IT 30/30,
  기존 Oracle Backend 258/258(skip 0),
  DB 없는 build 258 total/216 passed/42 Oracle-conditioned skip. 실패·오류 0,
  `git diff --check` 성공.
- JPA persistence mapping 보정(accepted): 기존 Oracle(V13) `Backend build
  --rerun-tasks` 203/203, skip 0, compile/jar/check 성공. DB 없는 build
  203 total, 182 passed, Oracle-conditioned 21 skips, 0 failures/errors.
  Migration 변경 없어 clean 재실행 생략(사유는 위 참고).
- V10~V13 각 라운드의 상세 이전 증거는 worklog의 해당 날짜 항목에 있다.

## Historical evidence

- 이번 압축 전 상태:
  [`../archive/state/2026-08-27-pre-v10-review-compaction-implemented-state.md`](../archive/state/2026-08-27-pre-v10-review-compaction-implemented-state.md)
- 이번 압축 전 active worklog:
  [`../archive/worklogs/2026-08-27-through-v10-review.md`](../archive/worklogs/2026-08-27-through-v10-review.md)
- 이전 compaction archive와 MVP-1 raw evidence는 위 archive 및 MVP-1 milestone에서
  연결한다.
