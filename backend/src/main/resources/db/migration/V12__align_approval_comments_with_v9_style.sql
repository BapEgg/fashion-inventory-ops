-- StockPilot V12: replaces V10/V11 Comment on new/changed columns only, so they
-- follow the same convention V9 already established for the rest of the schema --
-- plain columns get a concise Korean noun phrase (no English identifiers, no Java
-- symbols, no explanatory rationale), and code/status/flag columns get
-- "값: 한국어 의미" for every currently allowed value. No data, constraint, index,
-- or Java-visible behavior changes; V10 and V11 are not modified.
--
-- "donor"/"receiver" become the same "출고 매장"/"입고 매장" terms V9 already uses
-- on sp_store_transfer_route; "생성 시각"/"수정 시각" become "생성일시"/"수정일시"
-- to match every other audit-timestamp column in the schema.

COMMENT ON COLUMN sp_rebalance_decision.decision_request_id IS '승인 요청 멱등성 키';

COMMENT ON TABLE sp_approval_basis IS '승인 결정 근거 스냅샷';
COMMENT ON COLUMN sp_approval_basis.approval_basis_id IS '승인 근거 ID';
COMMENT ON COLUMN sp_approval_basis.decision_id IS '결정 ID';
COMMENT ON COLUMN sp_approval_basis.analysis_run_id IS '분석 실행 ID';
COMMENT ON COLUMN sp_approval_basis.input_snapshot_version IS '입력 스냅샷 버전';
COMMENT ON COLUMN sp_approval_basis.rule_version IS '규칙 버전';
COMMENT ON COLUMN sp_approval_basis.candidate_version IS '후보 버전';
COMMENT ON COLUMN sp_approval_basis.candidate_eligible_flag IS '후보 실행 가능 여부 (Y: 가능)';
COMMENT ON COLUMN sp_approval_basis.recommended_base_quantity IS '추천 BASE 수량';
COMMENT ON COLUMN sp_approval_basis.donor_transferable_quantity IS '출고 매장 이동 가능 수량';
COMMENT ON COLUMN sp_approval_basis.route_minimum_quantity IS '경로 최소 수량';
COMMENT ON COLUMN sp_approval_basis.package_multiple IS '경로 포장 배수';
COMMENT ON COLUMN sp_approval_basis.route_maximum_quantity IS '경로 최대 수량';
COMMENT ON COLUMN sp_approval_basis.receiver_capacity_remaining IS '입고 매장 잔여 수용량';
COMMENT ON COLUMN sp_approval_basis.basis_contract_version IS '승인 근거 계약 버전';
COMMENT ON COLUMN sp_approval_basis.receiver_projected_before_demand IS '입고 매장 수요 반영 전 예상재고';
COMMENT ON COLUMN sp_approval_basis.donor_projected_at_dispatch IS '출고 매장 출고 시점 예상재고';
COMMENT ON COLUMN sp_approval_basis.already_approved_draft_quantity IS '출고 매장 기승인 활성 Draft 합계 수량';
COMMENT ON COLUMN sp_approval_basis.created_at IS '생성일시';

COMMENT ON TABLE sp_error_catalog IS 'REST 오류 코드 카탈로그';
COMMENT ON COLUMN sp_error_catalog.error_code IS
    '오류 코드 (INVALID_REQUEST: 요청 값 오류, INVALID_DECISION_REQUEST: 결정 요청 형식 오류, RECOMMENDATION_NOT_FOUND: 추천 없음, STALE_RECOMMENDATION: 추천 근거 최신 아님, INVALID_DECISION_TRANSITION: 허용되지 않는 결정 전이, IDEMPOTENCY_KEY_REUSED: 멱등성 키 재사용, DECISION_CONFLICT: 결정 저장 충돌, APPROVAL_LOCK_TIMEOUT: 승인 잠금 시간 초과, PERSISTENCE_UNAVAILABLE: 저장소 일시 불가, INTERNAL_SERVER_ERROR: 내부 오류, VALIDATION_ERROR: 요청 값 검증 실패, NOT_FOUND: 자원 없음, DECISION_ALREADY_TERMINAL: 이미 종료된 결정)';
COMMENT ON COLUMN sp_error_catalog.http_status IS 'HTTP 상태 코드';
COMMENT ON COLUMN sp_error_catalog.message_ko IS '한국어 오류 메시지';
COMMENT ON COLUMN sp_error_catalog.title_ko IS '오류 제목(한국어)';
COMMENT ON COLUMN sp_error_catalog.default_detail_ko IS '오류 기본 상세 설명(한국어)';
COMMENT ON COLUMN sp_error_catalog.active_flag IS '사용 여부 (Y: 사용, N: 비활성)';
COMMENT ON COLUMN sp_error_catalog.created_at IS '생성일시';
COMMENT ON COLUMN sp_error_catalog.updated_at IS '수정일시';

COMMENT ON TABLE sp_error_constraint_map IS '제약명-오류 코드 매핑';
COMMENT ON COLUMN sp_error_constraint_map.error_code IS '매핑된 오류 코드';
COMMENT ON COLUMN sp_error_constraint_map.created_at IS '생성일시';
