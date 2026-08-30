# Current Task — pre-Codex decision REST review snapshot

Status: MVP-2 approval/decision REST — implemented by Claude, Codex 리뷰 대기
Current role: Codex review
Last updated: 2026-08-28

## Goal

[`2026-08-28-approval-decision-rest-spec.md`](../archive/state/2026-08-28-approval-decision-rest-spec.md)에
따라 accepted `ApprovalTransactionFacade`를 public POST에 연결하고 recommendation별 결정
이력·승인 근거·이동지시 초안을 조회하는 GET을 구현했다.

## Implemented scope

- `RebalanceDecisionRequest`에 version tuple(`analysisRunId/inputSnapshotVersion/ruleVersion/
  candidateVersion`), `policyException`, `reasonCode`를 additive로 추가했다. `selectedQuantity`/
  `reason`은 더 이상 Bean Validation에서 무조건 필수가 아니다 — legacy 경로의 필수 여부는
  `RebalanceDecisionService`가, MVP-2 경로의 상태별 필수 여부는 `ApprovalTransactionCommand`의
  기존 canonical constructor가 그대로 검증한다(둘 다 변경하지 않음).
- `RebalanceDecisionController.decide`가 tuple 4개/`policyException`/`reasonCode`/
  `Idempotency-Key` header 중 아무것도 없으면 기존 `RebalanceDecisionService` 경로(성공 JSON/201
  불변, `Location` header 없음)로, 하나라도 있으면 tuple 4개 전부와 정확히 한 개의
  `Idempotency-Key` header(중복 header, comma로 이어붙인 단일 값 모두 거부)를 요구해
  `ApprovalTransactionCommand`를 만들어 `ApprovalTransactionFacade.execute`를 호출한다. 부분
  조합은 `INVALID_DECISION_REQUEST` 400이다. `created=true/false`를 201/200으로 mapping하고
  두 성공 모두 `Location: /api/rebalancing-decisions/{recommendationId}`를 반환한다.
- `RebalanceDecisionService`는 legacy 경로 전용으로 남고, recommendation receiver run의 rule
  version이 정확히 `InventoryAnalysisRules.RULE_VERSION`(`MVP-1`)일 때만 실행하도록
  allowlist guard를 추가했다(MANUAL REST 슬라이스의 동일 패턴). 모든 실패를
  `ResponseStatusException` 대신 catalog-backed `ApiException`/`ApprovalErrorCode`로 던진다.
- `Mvp2RebalanceDecisionResponse`(decisionId/recommendationId/decisionStatus/decisionSequence/
  transferDraftId/created)를 새로 추가했다. 상세 감사 값은 POST body에 없다.
- `GET /api/rebalancing-decisions/{recommendationId}`와 읽기 전용 `Mvp2DecisionHistoryQueryService`를
  새로 추가했다. 결정이 없으면 `PENDING`+빈 배열, 있으면 `decisionSequence` ASC 전체 history를
  반환하며 MVP-2 `APPROVED` 항목에만 nested `approvalBasis`/`transferDraft`를 채운다(그 외 모든
  상태와 기존 MVP-1 decision은 둘 다 `null`). recommendation 존재 확인 1개 + 정렬된 decision
  목록 1개 + (decision이 있을 때만) basis bulk 1개 + draft bulk 1개로 history 길이와 무관하게
  최대 4개 JDBC statement를 유지한다(`SpApprovalBasisRepository`에 `JOIN FETCH analysisRun`
  bulk 조회, `SpTransferDraftRepository`에 bulk 조회 메서드 추가). 물리적 `PENDING` 행,
  MVP-2 `APPROVED`인데 basis/draft가 없는 행, MVP-2 non-approved인데 basis/draft가 있는 행,
  알 수 없는 `decisionContractVersion`은 부분 응답 대신 `INTERNAL_SERVER_ERROR`로 명시적으로
  거부한다. `Idempotency-Key`/fingerprint는 응답 어디에도 없다.
- `AnalysisApiExceptionHandler.assignableTypes`에 `RebalanceDecisionController`를 추가했다(신규
  migration 없음, V10/V11 code 재사용). `ApprovalTransactionFacade`의 stale "future Controller"
  javadoc을 실제 호출자로 갱신했다.

## Compatibility and safety constraints (유지됨)

- legacy 성공 JSON과 계산·HTTP 201은 바뀌지 않았다(Oracle IT로 확인).
- `ApprovalTransactionCommand`, fingerprint, lock order, current-basis 계산과 atomicity는
  controller에서 복제·변경하지 않았다 — 전부 기존 accepted facade/executor를 그대로 호출한다.
- policy exception은 여전히 stale/candidate/numeric 제약을 우회하지 못한다(기존
  `ApprovalRequestValidation` 그대로).
- inventory, external ERP, draft `READY` 상태는 건드리지 않는다.

## Verified behavior (실제 재실행, 2026-08-28)

- 신규 `RebalanceDecisionControllerTest`(DB-free, 12개): 라우팅 all/none/partial, header
  중복·comma·부재, `policyException`/`reasonCode` 단독 존재, replay 200 등 전부 통과.
- 신규 `Mvp2DecisionHistoryQueryServiceTest`(DB-free, 6개): not-found, PENDING+빈 배열, MVP-1
  basis/draft null, 물리적 PENDING/HELD+basis/APPROVED without basis corruption 전부
  `INTERNAL_SERVER_ERROR`로 거부 확인.
- 신규 `RebalanceDecisionRestOracleIT`(Oracle, 19개, **모두 첫 실행에 통과**): HELD→APPROVED
  append-only, exact-BASE 승인은 reason 불필요, 변경된 수량은 reason 없이 `INVALID_DECISION_REQUEST`,
  same-key replay(row 수 불변)/다른 payload는 `IDEMPOTENCY_KEY_REUSED`, stale tuple/terminal/
  unknown recommendation/lock-timeout(503) 각 exact code, legacy exact-MVP-1 성공과 non-MVP-1
  legacy 우회 차단, GET의 PENDING/ordered history exact 값/MVP-1 호환/404/400/corrupt-shape 500/
  JDBC statement ≤4(0·1·2건 history 전부) 검증.
- 기존 `ApiGoldenScenarioIT`의 `decisionWorkflowRejectsNonMvp1DecisionStatuses`가 신규
  exact-MVP-1 guard 때문에 rule-version suffix로는 더 이상 decisionStatus 자체를 검증하지
  못하게 되어(둘 다 400이라 기계적으로는 통과하지만 의도가 달라짐), 전용
  `analysisDate`(2026-11-10)로 격리하도록 고쳤다(MANUAL REST 라운드의 동일한 수정 패턴).
  단독 재확인 `ApiGoldenScenarioIT` **6/6 Oracle passed**.
- **Oracle 전체: 510 total / 510 passed / skip 0 / failures·errors 0.**
- DB-free 전체: 510 total / 373 passed / 137 conditional skip, failures/errors 0.
- `git diff --check`: exit 0(개행 변환 경고만, 실제 whitespace 오류 없음 — spec 원문의
  기존 trailing space 1건도 이 편집으로 제거됨).

## Next verifiable action

Codex가 POST/GET 라우팅, 감사 값 노출 경계, 4-query ceiling과 위 재실행 결과를 독립적으로
검증한다.
