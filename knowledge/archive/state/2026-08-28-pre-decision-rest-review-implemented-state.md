# Implemented State — pre-Codex decision REST review snapshot

Last updated: 2026-08-28 (MVP-2 approval/decision REST implemented, Codex 리뷰 대기)

현재 저장소에서 관찰되는 accepted 동작과 미구현 범위만 유지하는 hot-state snapshot이다.

## Accepted baselines

- MVP-1: [`../milestones/MVP-1.md`](../milestones/MVP-1.md)
- MVP-2 Phase 3 Batch: [`../milestones/MVP-2-Phase-3.md`](../milestones/MVP-2-Phase-3.md)
- Java 21, Spring Boot 4.1.0, Gradle 9.5.1, Oracle Free 23ai/Flyway/JPA
- MVP-2 `V1`~`V13`, 합성 Seed/validator, pure Java 계산 규칙, 승인 transaction과
  side-effect-free `MANUAL` 수량 시험은 accepted다.

## MVP-2 Phase 3 Batch — accepted

- `Mvp2InputAdapter`가 7개 bulk group/8 statements로 versioned 28일 입력, 정책, event,
  inbound, open transfer, route와 active approved draft 합계를 immutable graph로 읽는다.
- `Mvp2CalculationOrchestrator`가 수요 신호·confidence·quality, projected inventory,
  exception, 모든 공급 후보·탈락 사유와 네 scenario를 결정론적 Java로 계산한다.
- `Mvp2RunLifecycleService`가 natural key
  `(analysisDate, inputSnapshotVersion, ruleVersion)`를 별도 transaction으로 claim/fail/retry한다.
- `Mvp2AtomicOutputWriter`가 metric/flag/candidate/reason/scenario와 run COMPLETED 전이를
  하나의 transaction으로 저장한다. 실패 시 부분 결과를 남기지 않는다.
- `Mvp2AnalysisExecutor`가 claim → load → calculate → write의 유일한 application entry
  point이며 Batch 타입에 의존하지 않는다.
- `mvp2AnalysisJob`/`mvp2AnalysisStep`이 정확히 세 typed identifying parameter를 사용한다.
  Step은 `ResourcelessTransactionManager`로 executor 내부 JPA transaction 경계를 보존한다.
- 실패한 JobInstance는 같은 인스턴스의 새 JobExecution으로 재시작한다. 완료·실행 중
  인스턴스는 Spring Batch가 각각 `JobInstanceAlreadyCompleteException`과
  `JobExecutionAlreadyRunningException`으로 tasklet 전에 거부한다.
- `inventoryAnalysisJob`과 기존 MVP-1 API는 qualifier로 분리되어 그대로 동작한다.
- 공식 Golden triple `(2026-09-30, MVP-2-GS-V1, MVP-2)`의 Batch metadata와 domain 결과는
  보존되며 다른 테스트가 삭제하지 않는다.
- 현재 Phase 3 관련 열린 finding은 없다.

## MVP-2 REST analysis launch/status — accepted

- `POST /api/analyses`: absent/null `inputSnapshotVersion` keeps the existing MVP-1
  `AnalysisRunService` path and 200. A non-null value selects `Mvp2AnalysisApplicationService`,
  which always calls `JobOperator.start` (never short-circuits on a pre-read check) and re-reads
  the exact domain row afterward as the single source of truth: new run → 201, restarted
  (previously `FAILED`) run → 200/`alreadyCompleted=false`, replay (Batch or domain already
  complete, *including* a replay that races an empty pre-read) → 200/`alreadyCompleted=true` --
  `created` is derived as `!existedBefore && !alreadyCompleted`, so a race never reports 201 for a
  replay. `ruleVersion` in the request body is `@Null` and rejected; the server always supplies it.
  Every success response carries a `Location` header and the additive
  `inputSnapshotVersion`/`startedAt`/`completedAt` fields alongside the original five.
- `GET /api/analyses/{analysisRunId}` (`@Positive`) returns any MVP-1 or MVP-2 domain run's
  id/date/input/rule/status/startedAt/completedAt; an unknown id is `ANALYSIS_NOT_FOUND` (404); a
  non-numeric id is `VALIDATION_ERROR` (400) via a dedicated `MethodArgumentTypeMismatchException` handler.
- `AnalysisLaunchFailureClassifier` maps a Job launch failure to one stable code in priority order:
  already-running → launch-conflict (Oracle `ORA-8177`, detected via `SQLException.errorCode` at
  any cause-chain depth) → input-invalid (`InputContractViolationException`, also at any depth in
  the directly-thrown path now, not just the failed-execution path) → restart-unavailable
  (`JobRestartException`/`STOPPED`/`ABANDONED`) → persistence-unavailable (connection failure or
  any-depth `SQLException` with SQLState class `08`) → execution-failed (unclassified). Reads both
  a directly-thrown exception and a normally-returned but non-`COMPLETED` `JobExecution`'s
  `getAllFailureExceptions()`.
- `V14` added six new `sp_error_catalog` rows (no schema change); existing approval-owned rows are
  untouched. `ErrorCatalogService` falls back to a fixed internal-error presentation for a
  missing/inactive row and a fixed persistence-unavailable presentation if the lookup itself
  fails -- `ErrorPresentation` now carries the *effective* code for each case
  (`INTERNAL_SERVER_ERROR`/`PERSISTENCE_UNAVAILABLE`), and the handler always uses that effective
  code for the response `type`/`code`, never the originally-requested one.
- `AnalysisApiExceptionHandler` is the only ProblemDetail (RFC 9457) boundary, scoped to
  `AnalysisController`, `InventoryExceptionController` and `RebalanceSimulationController`.
  Validation responses add a `fieldErrors` list of
  `{field, code, message}` (not `category`) sorted by `(field, code)`, covering
  `MethodArgumentNotValidException` (body), `ConstraintViolationException`/
  `HandlerMethodValidationException` (path variable, both handled), `MethodArgumentTypeMismatchException`
  (non-numeric path variable), and now also `Mvp2AnalysisApplicationService`'s own
  `inputSnapshotVersion` blank(`REQUIRED`)/outer-whitespace(`FORMAT`)/over-64-char(`SIZE`)
  service-layer checks via `ApiException`'s optional `List<ApiFieldError>`. The existing MVP-1
  `ResponseStatusException` throws are normalized the same way (400→validation, 409→running,
  else→internal).
- `AnalysisLaunchFailureClassifier.classifyDataAccess(DataAccessException)` and a matching
  `@ExceptionHandler(DataAccessException.class)` on `AnalysisApiExceptionHandler` normalize any
  `DataAccessException` raised outside a Job launch (e.g. `AnalysisRunQueryService.findById` on
  GET, or `Mvp2AnalysisApplicationService`'s pre-/post-launch domain-run reads on POST) through
  this same single boundary -- connection-shaped failures become `PERSISTENCE_UNAVAILABLE`,
  everything else `INTERNAL_SERVER_ERROR`. Nothing escapes to Spring's own default error response.
- `fieldErrors` is included only when the *effective* resolved code equals `VALIDATION_ERROR`: if
  catalog resolution changes the response to a persistence/internal fallback, any field errors an
  `ApiException` carried are dropped, never exposed on a non-validation response.
- No rejected value or raw SQL/constraint/stack message ever appears in a response.

catalog effective code, replay 201 경합, fieldErrors schema/coverage, classifier cause-chain,
V14 exact metadata, controller 직접 `DataAccessException` 정규화와 fallback fieldErrors 억제까지
Codex 독립 재검증을 통과했다. 이 REST launch/status 단위에 열린 finding은 없다.

## MVP-2 inventory-exception read API — accepted

- `GET /api/inventory-exceptions`: `analysisRunId` 부재 시 기존 MVP-1 bare-array 계약을 그대로
  유지한다. `analysisRunId` 또는 신규 filter/page parameter가 있으면 새 run-bound paged read
  model(`Mvp2InventoryExceptionPage`/`Mvp2InventoryExceptionListItem`)로 분기한다. 고정 6-key
  정렬(severity, 실행 가능 후보, confidence, shortage DESC, 화면 보조 estimatedSalesImpact
  DESC, store/sku/metric id)과 filter는 JPQL WHERE/ORDER BY에서 수행하고, 페이지 id 집합만
  얻은 뒤 quality flag/candidate count/D-1 price/confirmed inbound 요약/product·store명을
  별도 bulk 조회로 채운다.
- `GET /api/inventory-exceptions/{metricId}`: metric이 속한 run의 `ruleVersion`으로 분기한다.
  MVP-1은 기존 `InventoryExceptionService.getExceptionDetail`에 그대로 위임하고(성공 shape
  불변), MVP-2는 새 `Mvp2InventoryExceptionDetail`(28일 evidence, policy, demand event/
  inbound/open transfer, receiver·donor candidate와 rejection reason·scenario·최신 decision,
  적용 rule threshold)을 반환한다. `NORMAL`과 존재하지 않는 id는 동일한
  `INVENTORY_EXCEPTION_NOT_FOUND`다.
- `AnalysisApiExceptionHandler`의 `assignableTypes`를 `InventoryExceptionController`까지
  넓혔다. 기존 `InventoryExceptionService`의 두 `ResponseStatusException(404)`도
  `ApiException(ANALYSIS_NOT_FOUND / INVENTORY_EXCEPTION_NOT_FOUND)`로 교체해 같은
  catalog-backed ProblemDetail 경계를 공유한다.
- `V15__add_inventory_exception_read_error_catalog.sql`이 스키마 변경 없이
  `ANALYSIS_RESULTS_NOT_READY`(409/Y)와 `INVENTORY_EXCEPTION_NOT_FOUND`(404/N) 두 행만
  INSERT한다.
- `SpDailySale`에 V6 `transaction_count`/`max_transaction_quantity`/`average_selling_price`/
  `input_snapshot_version` read mapping을 추가했다(V6에 이미 있던 컬럼, 기존 write 경로
  없음). `SpDemandEvent`/`SpInboundSchedule`/`SpOpenTransfer`에 누락됐던 `getSourceType()`을
  추가했다.
- official GS-05 receiver는 confirmed inbound로 이미 `NORMAL`이며, 사용자가 2026-08-28
  확정한 계약대로 exception detail은 `NORMAL`을 `INVENTORY_EXCEPTION_NOT_FOUND` 404로
  반환한다(변경 없음). Oracle IT의 GS-05 assertion을 이 404 ProblemDetail 회귀로 교체했다.
- `SpInventoryMetricRepository.findListRowsByInventoryMetricIdIn`이 metric/snapshot/product/store/
  quality flag에 더해 D-1 `averageSellingPrice`까지(row별 정확한 scalar subquery로) 한 번에
  읽어, 목록의 별도 D-1 price bulk 조회를 제거했다. `SpOpenTransferRepository.findOpenForStore`는
  receiver/donor 양방향 open transfer를 병합 조회한다. **실제 Oracle `getPrepareStatementCount()`
  기준 목록 6, 상세(candidate+route 포함 worst case) 14 statements**로 두 ceiling을 모두
  만족한다(list 7→6 수정 확인).
- detail 분기는 `InventoryAnalysisRules.RULE_VERSION`("MVP-1")만 legacy로 보내고,
  `DemandAnalysisRules.RULE_VERSION`("MVP-2")도 아닌 값은 `INTERNAL_SERVER_ERROR`로 명시적으로
  실패시키며 run/metric id를 로그에 남긴다.
- DB-free에 candidate count/quality flag/estimatedSalesImpact 매핑과 unknown-rule-version
  단위 테스트가 있다(19/19). Oracle IT는 signal/quality filter, repeatable severity OR의 양쪽
  결과와 다른 filter 간 AND, 여섯 정렬 key의 독립 comparator, size=1 전체 page walk와 반복
  호출 안정성, size=1/100의 row-수-불변 statement 수를 검증한다. 또한
  `InventoryExceptionController`에서 발생한 `DataAccessException`이 widened advice를 거쳐
  `PERSISTENCE_UNAVAILABLE` ProblemDetail이 되는 MVC 경계를 검증한다.
- query-count 회귀는 실제 JDBC statement를 세는 `getPrepareStatementCount()`를 사용한다
  (Codex가 `getQueryExecutionCount()`의 identifier-load SQL 누락을 교정).

## MVP-2 `MANUAL` quantity-test REST — accepted

- **Current-basis parity 버그 2건 수정** (`CurrentApprovalBasisLoader`, 승인·`MANUAL` 공유):
  (1) 매장-SKU 정책 행이 없으면 이전에는 `STALE_RECOMMENDATION`으로 거부했지만, 이제
  `DemandAnalysisRules`의 승인된 기본값(`displayMinimum=1/safetyStock=2/maximumCapacity=100/
  targetCoverageDays=7/retainedDays=14`)으로 fallback한다(route 행 부재는 여전히 fallback하지
  않고 `STALE_RECOMMENDATION`). (2) receiver의 저장 baseline BASE를 그대로 쓰던 것을,
  대표 이벤트(같은 입력 버전에서 관측/계획 구간과 겹치는 이벤트를 `(startDate,eventCode)`
  오름차순 정렬한 첫 행) 기반 effective BASE로 교체했다 -- 신호가 `KNOWN_EVENT`이고 대표
  이벤트가 이 후보의 route 도착일~목표 커버리지 종료일과 겹치며 uplift 3값이 모두 있을 때만
  `DemandRateCalculation.applyUplift`(scale 12 HALF_UP)로 baseline에 곱한다. donor 보호량은
  계속 donor baseline HIGH만 사용하고 event uplift를 적용하지 않는다. 이 effective BASE는
  추천/`MANUAL` 비교/실제 승인 재검증 모두에 동일하게 반영된다(Oracle IT로 preview와
  실제 저장 basis 양쪽에서 확인).
- `POST /api/rebalancing-simulations`에 `analysisRunId/inputSnapshotVersion/ruleVersion/
  candidateVersion` 4-field version tuple을 additive로 연결했다. 네 필드 모두 없으면 기존
  MVP-1 `RebalanceSimulationService` 경로(JSON/계산 불변)이고, 모두 있으면 side-effect-free
  `ManualQuantityTestExecutor`로 간다. 일부만 있으면 `INVALID_REQUEST` 400이다. legacy
  경로는 저장 run의 rule version이 **정확히** `InventoryAnalysisRules.RULE_VERSION`
  (`MVP-1`)일 때만 계산하는 allowlist다 — exact `MVP-2`, unknown, future, 어떤 suffix 값도
  `INVALID_REQUEST` 400으로 거부한다. `ApiGoldenScenarioIT`는 production allowlist를
  완화하지 않고 전용 `analysisDate`로 exact-MVP-1 fixture를 격리한다.
- `Mvp2RebalanceSimulationResponse`가 `ManualQuantityTestResult`를 1:1 매핑한다.
  `feasible=false`도 정상 200이며 `violations`/`candidateRejectionReasons`는 항상 non-null
  배열, `projection`은 feasible일 때만 채워진다. `approvalRevalidationRequired`는 항상
  `true`, 고정 `ASSUMPTION` notice를 포함한다. 결정/basis/draft/inventory를 어떤 경로에서도
  쓰지 않는다(Oracle IT로 zero-persistence 확인).
- `AnalysisApiExceptionHandler`의 `assignableTypes`에 `RebalanceSimulationController`를
  추가하고 신규 `@ExceptionHandler(ApprovalTransactionException.class)`를 붙였다 --
  `ApprovalErrorCode`의 코드(`INVALID_REQUEST/RECOMMENDATION_NOT_FOUND/
  STALE_RECOMMENDATION/DECISION_ALREADY_TERMINAL/APPROVAL_LOCK_TIMEOUT/
  PERSISTENCE_UNAVAILABLE/INTERNAL_SERVER_ERROR`)는 이미 V10/V11에 모두 있어 신규
  migration이 없다. 레거시 `RebalanceSimulationService`의 `ResponseStatusException`도
  `ApiException(RECOMMENDATION_NOT_FOUND/INVALID_REQUEST)`로 정규화했다.
- Oracle IT로 lock-timeout(503, 다른 트랜잭션이 donor snapshot을 잡고 있을 때)까지 REST
  경로로 실제 재현·확인했다.
- `RebalanceSimulationRestOracleIT`의 persistence 불변 검증은 decision count가 아니라
  decision/basis/draft의 선택한 감사 값(상태, 선택 수량, sequence, reasonCode, basis의
  recommended/donorTransferable/receiverCapacity, draft의 quantity/status)과 양쪽 재고
  snapshot·metric(가용수량, base/high demand rate)의 business 값을 before/after로 비교한다.
  terminal은 승인 직후(0이 아닌) 그 감사 값 baseline과 비교한다.
  feasible response는 top-level 20개 필드와 projection 18개 필드 전부를 fixture에서 손으로
  유도한 exact 값(coverage 4개, risk code 2개, 고정 assumption notice 문자열 포함)으로
  검증한다. multi-violation은 선언 순서 exact list를 검증한다. 5개 오류 경로 모두
  `assertProblemDetail`이 응답/본문 양쪽의 exact HTTP status, `type`/`code`/`retryable`,
  이 endpoint 고정 `instance` 경로, header와 일치하는 `requestId`, non-null `timestamp`를
  검증하고 `detail`에 recommendation id나 raw SQL/스택 진단 문자열이 없음을 확인한다. 이
  단위에 열린 test finding은 없다.

## MVP-2 approval/decision REST — implemented; Codex review pending

- `POST /api/rebalancing-decisions`에 tuple 4개(`analysisRunId/inputSnapshotVersion/ruleVersion/
  candidateVersion`)/`policyException`/`reasonCode`/`Idempotency-Key` header 중 아무것도 없으면
  기존 MVP-1 `RebalanceDecisionService` 경로(성공 JSON/201 불변, `Location` 없음)이고, 하나라도
  있으면 tuple 4개 전부와 정확히 한 개의 header를 요구해 accepted `ApprovalTransactionCommand`/
  `ApprovalTransactionFacade`를 그대로 호출한다. `created=true/false`를 201/200으로 mapping하고
  두 성공 모두 `Location: /api/rebalancing-decisions/{recommendationId}`를 반환한다. 부분 조합,
  중복/comma로 이어붙인 header는 `INVALID_DECISION_REQUEST` 400이다.
- legacy `RebalanceDecisionService`는 recommendation receiver run의 rule version이 정확히
  `InventoryAnalysisRules.RULE_VERSION`(`MVP-1`)일 때만 실행하는 allowlist를 갖는다(`MANUAL`
  REST 슬라이스와 같은 패턴). 모든 실패는 catalog-backed `ApiException`/`ApprovalErrorCode`다
  (더 이상 raw `ResponseStatusException`을 쓰지 않는다).
- `GET /api/rebalancing-decisions/{recommendationId}`가 새 읽기 전용
  `Mvp2DecisionHistoryQueryService`로 recommendation당 결정 이력 전체와 nested approval basis/
  transfer draft를 반환한다. 결정이 없으면 `PENDING`+빈 배열이다. MVP-2 `APPROVED` 항목에만
  basis/draft가 채워지고 그 외(HELD/REJECTED/EXPIRED, 기존 MVP-1 decision)는 둘 다 `null`이다.
  history 길이와 무관하게 최대 4개 JDBC statement(recommendation 존재 1 + 정렬된 decision 목록
  1 + basis bulk 1 + draft bulk 1, 결정이 없으면 뒤 둘은 생략)를 유지하며, basis bulk 조회는
  `JOIN FETCH analysisRun`으로 id 접근 N+1을 막는다. 물리적 `PENDING` 행, MVP-2 `APPROVED`인데
  basis/draft가 없는 행, non-approved MVP-2인데 basis/draft가 있는 행, 알 수 없는
  `decisionContractVersion`은 부분 응답 대신 `INTERNAL_SERVER_ERROR`다. `Idempotency-Key`나
  fingerprint는 응답 어디에도 없다.
- `Mvp2RebalanceDecisionResponse`가 facade 결과에 recommendation identity만 더한 최소 성공
  계약이다(decisionId/recommendationId/decisionStatus/decisionSequence/transferDraftId/created).
  상세 감사 값은 GET으로만 조회한다.
- `AnalysisApiExceptionHandler.assignableTypes`에 `RebalanceDecisionController`를 추가했다
  (V10/V11 code 재사용, 신규 migration 없음).
- `ApprovalTransactionCommand`/`ApprovalTransactionFacade`/`ApprovalTransactionExecutor`(lock
  order, current-basis 재계산, 원자적 저장)와 `ApprovalRequestValidation`(변경된 수량·정책
  예외에 reason 필수)은 그대로 재사용했고 이 라운드에서 변경하지 않았다.
- 신규 `RebalanceDecisionControllerTest`(DB-free, 12개)와
  `Mvp2DecisionHistoryQueryServiceTest`(DB-free, 6개)가 라우팅/오류 카디널리티와 GET의 PENDING/
  MVP-1 호환/corruption 경계를 검증한다. `RebalanceDecisionRestOracleIT`(Oracle, 19개, 첫 실행
  통과)가 HELD→APPROVED append-only, exact-BASE 무reason 승인, 변경 수량 reason 필수, replay/
  key 재사용/stale/terminal/lock-timeout(503) 각 exact code, legacy exact-MVP-1 성공과 비MVP-1
  우회 차단, GET의 PENDING/ordered history exact 값/MVP-1 호환/404/400/corrupt-shape 500/JDBC
  statement ≤4를 Oracle에서 검증한다.
- Codex 독립 리뷰가 아직 진행되지 않아 이 단위는 아직 accepted가 아니다.

## Other accepted MVP-2 behavior

- 승인 transaction은 idempotency fingerprint, recommendation/donor lock, 최신 근거 재검증,
  append-only decision과 transfer draft 저장을 하나의 원자적 경계로 처리한다.
- `MANUAL` 수량 시험은 persistence 부작용 없이 승인과 같은 순수 validator/계산 경계를 쓴다.
- 수량·상태는 Java가 결정하고 AI는 설명만 담당한다. 모든 임계값은 데모 `ASSUMPTION`이다.

## Latest independent verification

- Accepted REST launch/status baseline: pure 37/37, Oracle target 17/17, Oracle full 434/434
  skip 0, DB-free 434 total/332 passed/102 conditional skip, failures/errors 0.
- Inventory read final Codex verification: unit target **19/19**; DB-free full **455 total /
  351 passed / 104 conditional skip**, failures/errors 0; Oracle target **2/2**, Oracle full
  **455/455**, skip/failures/errors 0. 실제 JDBC ceiling 회귀는 list 6/detail 14를 통과하며
  `git diff --check`도 통과했다. 이 read API 단위에 열린 finding은 없다.
- `MANUAL` REST final Codex verification: Oracle target **20/20**; DB-free full **473 total /
  355 passed / 118 conditional skip**; Oracle full을 `--rerun-tasks`로 실제 재실행해
  **473/473 passed / skip 0**, failures/errors 0; `git diff --check` exit 0. exact-MVP-1
  compatibility, full response projection, terminal/value immutability and error contract를
  재검증했으며 이 단위에 열린 finding은 없다.
- Claude (approval/decision REST 구현, 실제 Oracle 연결 상태에서 실행): 신규
  `RebalanceDecisionControllerTest` 12/12, `Mvp2DecisionHistoryQueryServiceTest` 6/6(둘 다
  DB-free), `RebalanceDecisionRestOracleIT` **19/19 Oracle passed**(첫 실행에 전부 통과).
  **Oracle 전체: 510 total / 510 passed / skip 0 / failures·errors 0.** DB-free 전체: 510
  total / 373 passed / 137 conditional skip, failures/errors 0. `git diff --check`: exit 0.
  기존 `ApiGoldenScenarioIT`의 `decisionWorkflowRejectsNonMvp1DecisionStatuses`가 신규
  exact-MVP-1 guard로 인해 rule-version suffix 격리로는 더 이상 의도한 것(decisionStatus 거부)을
  검증하지 못하게 된 것을 발견해, 전용 `analysisDate`(2026-11-10) 격리로 수정·재확인(단독
  6/6 Oracle passed) — `MANUAL` REST 라운드와 동일한 수정 패턴.
- Codex independent review: 미실행 — 다음 단계에서 진행한다.

## Not implemented

- MVP-2 React application wiring과 실제 LLM provider adapter
- 운영 scheduler, stale RUNNING 자동 회수/timeout과 신규 JobInstance 첫-insert 경쟁 오류의
  REST 서비스 오류 코드 정규화(현재는 `ANALYSIS_LAUNCH_CONFLICT`로 분류만 한다)
- 인증과 외부 ERP/WMS/TMS 연동

## Cold evidence

Phase 3의 세션별 review/fix 과정은 checkpoint가 연결한 `knowledge/archive`에만 보존한다.
