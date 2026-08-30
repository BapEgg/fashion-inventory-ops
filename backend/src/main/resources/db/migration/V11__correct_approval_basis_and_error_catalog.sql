-- StockPilot V11: corrects Codex review findings on the already-applied, immutable
-- V10 approval-transaction foundation. V10 itself is not modified.
--
-- sp_approval_basis is only ever written by the future approval transaction, which
-- is not implemented yet, so this migration assumes (and verifies) it is empty:
-- the new evidence columns (receiver/donor projection, already-approved draft
-- quantity) have no other captured column they could be losslessly backfilled
-- from, and analysis_run_id is changing from a free-text VARCHAR2 to a NUMBER(19,0)
-- FK. A real historical row could not be safely reinterpreted, so the migration
-- fails loudly instead of guessing if one is ever found.
DECLARE
    existing_row_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO existing_row_count FROM sp_approval_basis;
    IF existing_row_count > 0 THEN
        RAISE_APPLICATION_ERROR(-20001,
            'sp_approval_basis has ' || existing_row_count || ' existing row(s). V11 cannot ' ||
            'losslessly backfill receiver_projected_before_demand/donor_projected_at_dispatch/' ||
            'already_approved_draft_quantity (no prior column captures them), nor safely convert ' ||
            'analysis_run_id from VARCHAR2 to a validated NUMBER(19,0) FK. Resolve the existing ' ||
            'row(s) with a manual data decision before applying V11.');
    END IF;
END;
/

-- ck_sp_basis_ids references analysis_run_id directly in its CHECK expression, so
-- it must be dropped before the column itself can be dropped.
ALTER TABLE sp_approval_basis DROP CONSTRAINT ck_sp_basis_ids;

ALTER TABLE sp_approval_basis ADD (
    analysis_run_id_numeric NUMBER(19, 0),
    basis_contract_version VARCHAR2(32 CHAR) DEFAULT 'MVP-2' NOT NULL,
    receiver_projected_before_demand NUMBER(12, 0),
    donor_projected_at_dispatch NUMBER(12, 0),
    already_approved_draft_quantity NUMBER(12, 0)
);

ALTER TABLE sp_approval_basis DROP COLUMN analysis_run_id;
ALTER TABLE sp_approval_basis RENAME COLUMN analysis_run_id_numeric TO analysis_run_id;

ALTER TABLE sp_approval_basis MODIFY (
    analysis_run_id NOT NULL,
    receiver_projected_before_demand NOT NULL,
    donor_projected_at_dispatch NOT NULL,
    already_approved_draft_quantity NOT NULL
);

ALTER TABLE sp_approval_basis ADD CONSTRAINT fk_sp_basis_analysis_run
    FOREIGN KEY (analysis_run_id) REFERENCES sp_analysis_run (analysis_run_id);

ALTER TABLE sp_approval_basis ADD CONSTRAINT ck_sp_basis_ids CHECK (
    LENGTH(TRIM(input_snapshot_version)) > 0
    AND LENGTH(TRIM(rule_version)) > 0
    AND LENGTH(TRIM(basis_contract_version)) > 0
    AND candidate_version > 0
);

ALTER TABLE sp_approval_basis DROP CONSTRAINT ck_sp_basis_eligible;
ALTER TABLE sp_approval_basis ADD CONSTRAINT ck_sp_basis_eligible
    CHECK (candidate_eligible_flag = 'Y');

ALTER TABLE sp_approval_basis DROP CONSTRAINT ck_sp_basis_values;
ALTER TABLE sp_approval_basis ADD CONSTRAINT ck_sp_basis_values CHECK (
    recommended_base_quantity >= 0
    AND donor_transferable_quantity >= 0
    AND receiver_capacity_remaining >= 0
    AND route_minimum_quantity > 0
    AND package_multiple > 0
    AND route_maximum_quantity >= route_minimum_quantity
    AND receiver_projected_before_demand >= 0
    AND donor_projected_at_dispatch >= 0
    AND already_approved_draft_quantity >= 0
);

ALTER TABLE sp_error_catalog ADD (
    title_ko VARCHAR2(200 CHAR),
    default_detail_ko VARCHAR2(500 CHAR),
    active_flag CHAR(1 CHAR),
    updated_at TIMESTAMP(6) WITH TIME ZONE
);

UPDATE sp_error_catalog SET title_ko = '멱등성 키 재사용', default_detail_ko = message_ko
    WHERE error_code = 'IDEMPOTENCY_KEY_REUSED';
UPDATE sp_error_catalog SET title_ko = '추천 근거 최신 아님', default_detail_ko = message_ko
    WHERE error_code = 'STALE_RECOMMENDATION';
UPDATE sp_error_catalog SET title_ko = '승인 잠금 시간 초과', default_detail_ko = message_ko
    WHERE error_code = 'APPROVAL_LOCK_TIMEOUT';
UPDATE sp_error_catalog SET title_ko = '요청 값 검증 실패', default_detail_ko = message_ko
    WHERE error_code = 'VALIDATION_ERROR';
UPDATE sp_error_catalog SET title_ko = '자원 없음', default_detail_ko = message_ko
    WHERE error_code = 'NOT_FOUND';
UPDATE sp_error_catalog SET title_ko = '이미 종료된 결정', default_detail_ko = message_ko
    WHERE error_code = 'DECISION_ALREADY_TERMINAL';
UPDATE sp_error_catalog SET title_ko = '저장소 일시 불가', default_detail_ko = message_ko
    WHERE error_code = 'PERSISTENCE_UNAVAILABLE';
UPDATE sp_error_catalog SET title_ko = '내부 오류', default_detail_ko = message_ko
    WHERE error_code = 'INTERNAL_SERVER_ERROR';
UPDATE sp_error_catalog SET active_flag = 'Y', updated_at = SYSTIMESTAMP;

ALTER TABLE sp_error_catalog MODIFY (
    title_ko NOT NULL,
    default_detail_ko NOT NULL,
    active_flag DEFAULT 'Y' NOT NULL,
    updated_at DEFAULT SYSTIMESTAMP NOT NULL
);

ALTER TABLE sp_error_catalog ADD CONSTRAINT ck_sp_error_title
    CHECK (LENGTH(TRIM(title_ko)) > 0);
ALTER TABLE sp_error_catalog ADD CONSTRAINT ck_sp_error_detail
    CHECK (LENGTH(TRIM(default_detail_ko)) > 0);
ALTER TABLE sp_error_catalog ADD CONSTRAINT ck_sp_error_active
    CHECK (active_flag IN ('Y', 'N'));

-- New approval-transaction-specific codes. VALIDATION_ERROR/NOT_FOUND (V10) stay as
-- generic fallbacks for request shapes outside this contract; the codes below are
-- specific to the section-10 approval use case and take precedence over the generic
-- pair whenever a handler can tell the difference.
INSERT INTO sp_error_catalog (error_code, http_status, retryable_flag, message_ko, title_ko, default_detail_ko) VALUES
    ('INVALID_REQUEST', 400, 'N', '요청 값이 승인 요청 계약과 맞지 않습니다.',
     '요청 값 오류', '요청 필드 값이 승인 요청 계약과 일치하지 않습니다.');
INSERT INTO sp_error_catalog (error_code, http_status, retryable_flag, message_ko, title_ko, default_detail_ko) VALUES
    ('INVALID_DECISION_REQUEST', 400, 'N', '결정 요청의 상태별 필수 항목이 누락되었거나 잘못되었습니다.',
     '결정 요청 형식 오류', '상태별로 필요한 수량, 사유 코드 또는 사유가 누락되었거나 잘못되었습니다.');
INSERT INTO sp_error_catalog (error_code, http_status, retryable_flag, message_ko, title_ko, default_detail_ko) VALUES
    ('RECOMMENDATION_NOT_FOUND', 404, 'N', '요청한 추천을 찾을 수 없습니다.',
     '추천 없음', '지정한 recommendationId에 해당하는 추천이 없습니다.');
INSERT INTO sp_error_catalog (error_code, http_status, retryable_flag, message_ko, title_ko, default_detail_ko) VALUES
    ('INVALID_DECISION_TRANSITION', 409, 'N', '현재 결정 상태에서 허용되지 않는 전이입니다.',
     '허용되지 않는 결정 전이', '결정 상태 기계에서 허용하지 않는 상태 전이를 요청했습니다.');
INSERT INTO sp_error_catalog (error_code, http_status, retryable_flag, message_ko, title_ko, default_detail_ko) VALUES
    ('DECISION_CONFLICT', 409, 'N', '동시 요청으로 결정 저장에 충돌이 발생했습니다.',
     '결정 저장 충돌', '동시에 처리된 다른 요청과 충돌해 결정을 저장하지 못했습니다. 최신 상태로 다시 시도하세요.');

-- UQ_SP_DEC_REC_SEQ alone does not prove the recommendation reached a terminal
-- state (it could also be a genuine race between two in-flight requests), so it
-- maps to the generic DECISION_CONFLICT rather than DECISION_ALREADY_TERMINAL.
-- DECISION_ALREADY_TERMINAL remains in the catalog for the Java-side business-rule
-- check that reads the latest decision status directly (business-rules.md section
-- 10 step 4), which is the only place that can actually prove terminality.
UPDATE sp_error_constraint_map SET error_code = 'DECISION_CONFLICT'
    WHERE constraint_name = 'UQ_SP_DEC_REC_SEQ';

ALTER TABLE sp_rebalance_decision ADD CONSTRAINT ck_sp_dec_policy_exception_scope CHECK (
    policy_exception_flag = 'N'
    OR (decision_contract_version = 'MVP-2' AND decision_status = 'APPROVED')
);

COMMENT ON COLUMN sp_approval_basis.approval_basis_id IS '승인 근거 ID';
COMMENT ON COLUMN sp_approval_basis.decision_id IS '결정 ID (FK, 결정과 1:1)';
COMMENT ON COLUMN sp_approval_basis.analysis_run_id IS '승인 시점 근거로 사용한 분석 실행 ID (FK)';
COMMENT ON COLUMN sp_approval_basis.input_snapshot_version IS '승인 시점 근거로 사용한 입력 스냅샷 버전';
COMMENT ON COLUMN sp_approval_basis.rule_version IS '승인 시점 근거로 사용한 규칙 버전';
COMMENT ON COLUMN sp_approval_basis.candidate_version IS '승인 시점 근거로 사용한 후보 버전';
COMMENT ON COLUMN sp_approval_basis.candidate_eligible_flag IS
    '승인 시점 재계산된 후보 실행 가능 여부 (Y만 허용: 실행 불가 후보는 승인 자체가 거부되어 근거를 남기지 않는다)';
COMMENT ON COLUMN sp_approval_basis.recommended_base_quantity IS '승인 시점 재계산된 추천 BASE 수량';
COMMENT ON COLUMN sp_approval_basis.donor_transferable_quantity IS '승인 시점 재계산된 donor 이동 가능 수량';
COMMENT ON COLUMN sp_approval_basis.route_minimum_quantity IS '승인 시점 경로 최소 수량';
COMMENT ON COLUMN sp_approval_basis.package_multiple IS '승인 시점 경로 포장 배수';
COMMENT ON COLUMN sp_approval_basis.route_maximum_quantity IS '승인 시점 경로 최대 수량';
COMMENT ON COLUMN sp_approval_basis.receiver_capacity_remaining IS '승인 시점 receiver 잔여 수용량';
COMMENT ON COLUMN sp_approval_basis.basis_contract_version IS '승인 근거 계약 버전';
COMMENT ON COLUMN sp_approval_basis.receiver_projected_before_demand IS
    '승인 시점 receiver의 수요 반영 전 예상재고 (InventoryProjection.projectedReceiverBeforeDemand)';
COMMENT ON COLUMN sp_approval_basis.donor_projected_at_dispatch IS
    '승인 시점 donor의 출고 시점 예상재고 (InventoryProjection.projectedDonorAtDispatch)';
COMMENT ON COLUMN sp_approval_basis.already_approved_draft_quantity IS
    '승인 시점 donor의 이미 승인된 활성 Draft 합계 수량';
COMMENT ON COLUMN sp_approval_basis.created_at IS '생성 시각';

COMMENT ON COLUMN sp_error_catalog.error_code IS '오류 코드';
COMMENT ON COLUMN sp_error_catalog.http_status IS 'HTTP 상태 코드';
COMMENT ON COLUMN sp_error_catalog.message_ko IS '한국어 오류 메시지 (레거시, title_ko/default_detail_ko로 대체 예정)';
COMMENT ON COLUMN sp_error_catalog.title_ko IS 'ProblemDetail 제목(한국어)';
COMMENT ON COLUMN sp_error_catalog.default_detail_ko IS 'ProblemDetail 기본 상세 설명(한국어)';
COMMENT ON COLUMN sp_error_catalog.active_flag IS '사용 여부 (Y: 사용, N: 비활성)';
COMMENT ON COLUMN sp_error_catalog.created_at IS '생성 시각';
COMMENT ON COLUMN sp_error_catalog.updated_at IS '수정 시각';

COMMENT ON COLUMN sp_error_constraint_map.constraint_name IS 'Oracle 제약명';
COMMENT ON COLUMN sp_error_constraint_map.error_code IS '매핑된 오류 코드 (FK)';
COMMENT ON COLUMN sp_error_constraint_map.created_at IS '생성 시각';
