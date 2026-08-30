-- StockPilot approval transaction support (business-rules.md section 10).
-- Adds idempotency and policy-exception tracking to sp_rebalance_decision, a
-- 1:1 snapshot of the approval-time basis a decision was validated against,
-- and a small DB-managed catalog so the REST error contract does not hard-code
-- HTTP status/retryable/message strings in Java. Existing V1-V9 rows and audit
-- history remain; no V1-V9 file is modified.

ALTER TABLE sp_rebalance_decision ADD (
    decision_request_id VARCHAR2(100 CHAR),
    policy_exception_flag CHAR(1 CHAR)
);

UPDATE sp_rebalance_decision
SET decision_request_id = 'LEGACY-' || TO_CHAR(decision_id),
    policy_exception_flag = 'N';

ALTER TABLE sp_rebalance_decision MODIFY (
    decision_request_id NOT NULL,
    policy_exception_flag DEFAULT 'N' NOT NULL
);

ALTER TABLE sp_rebalance_decision ADD CONSTRAINT uq_sp_dec_request_id
    UNIQUE (decision_request_id);
ALTER TABLE sp_rebalance_decision ADD CONSTRAINT ck_sp_dec_request_id
    CHECK (LENGTH(TRIM(decision_request_id)) > 0);
ALTER TABLE sp_rebalance_decision ADD CONSTRAINT ck_sp_dec_policy_exception
    CHECK (policy_exception_flag IN ('Y', 'N'));

CREATE TABLE sp_approval_basis (
    approval_basis_id NUMBER(19, 0) GENERATED ALWAYS AS IDENTITY,
    decision_id NUMBER(19, 0) NOT NULL,
    analysis_run_id VARCHAR2(64 CHAR) NOT NULL,
    input_snapshot_version VARCHAR2(64 CHAR) NOT NULL,
    rule_version VARCHAR2(32 CHAR) NOT NULL,
    candidate_version NUMBER(10, 0) NOT NULL,
    candidate_eligible_flag CHAR(1 CHAR) NOT NULL,
    recommended_base_quantity NUMBER(12, 0) NOT NULL,
    donor_transferable_quantity NUMBER(12, 0) NOT NULL,
    route_minimum_quantity NUMBER(10, 0) NOT NULL,
    package_multiple NUMBER(10, 0) NOT NULL,
    route_maximum_quantity NUMBER(10, 0) NOT NULL,
    receiver_capacity_remaining NUMBER(12, 0) NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT pk_sp_approval_basis PRIMARY KEY (approval_basis_id),
    CONSTRAINT fk_sp_basis_decision FOREIGN KEY (decision_id)
        REFERENCES sp_rebalance_decision (decision_id),
    CONSTRAINT uq_sp_basis_decision UNIQUE (decision_id),
    CONSTRAINT ck_sp_basis_ids CHECK (
        LENGTH(TRIM(analysis_run_id)) > 0
        AND LENGTH(TRIM(input_snapshot_version)) > 0
        AND LENGTH(TRIM(rule_version)) > 0
        AND candidate_version > 0
    ),
    CONSTRAINT ck_sp_basis_eligible CHECK (candidate_eligible_flag IN ('Y', 'N')),
    CONSTRAINT ck_sp_basis_values CHECK (
        recommended_base_quantity >= 0
        AND donor_transferable_quantity >= 0
        AND receiver_capacity_remaining >= 0
        AND route_minimum_quantity > 0
        AND package_multiple > 0
        AND route_maximum_quantity >= route_minimum_quantity
    )
);

CREATE TABLE sp_error_catalog (
    error_code VARCHAR2(64 CHAR) NOT NULL,
    http_status NUMBER(3, 0) NOT NULL,
    retryable_flag CHAR(1 CHAR) NOT NULL,
    message_ko VARCHAR2(500 CHAR) NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT pk_sp_error_catalog PRIMARY KEY (error_code),
    CONSTRAINT ck_sp_error_code CHECK (LENGTH(TRIM(error_code)) > 0),
    CONSTRAINT ck_sp_error_status CHECK (http_status BETWEEN 400 AND 599),
    CONSTRAINT ck_sp_error_retryable CHECK (retryable_flag IN ('Y', 'N')),
    CONSTRAINT ck_sp_error_message CHECK (LENGTH(TRIM(message_ko)) > 0)
);

CREATE TABLE sp_error_constraint_map (
    constraint_name VARCHAR2(128 CHAR) NOT NULL,
    error_code VARCHAR2(64 CHAR) NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT pk_sp_error_constraint_map PRIMARY KEY (constraint_name),
    CONSTRAINT fk_sp_error_map_code FOREIGN KEY (error_code)
        REFERENCES sp_error_catalog (error_code)
);

INSERT INTO sp_error_catalog (error_code, http_status, retryable_flag, message_ko) VALUES
    ('IDEMPOTENCY_KEY_REUSED', 409, 'N', '동일한 Idempotency-Key가 다른 요청 내용으로 이미 사용됐습니다.');
INSERT INTO sp_error_catalog (error_code, http_status, retryable_flag, message_ko) VALUES
    ('STALE_RECOMMENDATION', 409, 'N', '추천 근거가 최신 상태와 달라 승인할 수 없습니다. 최신 근거로 다시 확인하세요.');
INSERT INTO sp_error_catalog (error_code, http_status, retryable_flag, message_ko) VALUES
    ('APPROVAL_LOCK_TIMEOUT', 503, 'Y', '동시 처리 중인 다른 요청으로 잠금을 얻지 못했습니다. 잠시 후 다시 시도하세요.');
INSERT INTO sp_error_catalog (error_code, http_status, retryable_flag, message_ko) VALUES
    ('VALIDATION_ERROR', 400, 'N', '요청 형식 또는 값이 올바르지 않습니다.');
INSERT INTO sp_error_catalog (error_code, http_status, retryable_flag, message_ko) VALUES
    ('NOT_FOUND', 404, 'N', '요청한 자원을 찾을 수 없습니다.');
INSERT INTO sp_error_catalog (error_code, http_status, retryable_flag, message_ko) VALUES
    ('DECISION_ALREADY_TERMINAL', 409, 'N', '이미 종료 상태(APPROVED/REJECTED/EXPIRED)인 결정에는 새 결정을 추가할 수 없습니다.');
INSERT INTO sp_error_catalog (error_code, http_status, retryable_flag, message_ko) VALUES
    ('PERSISTENCE_UNAVAILABLE', 503, 'Y', '저장소에 일시적으로 접근할 수 없습니다. 잠시 후 다시 시도하세요.');
INSERT INTO sp_error_catalog (error_code, http_status, retryable_flag, message_ko) VALUES
    ('INTERNAL_SERVER_ERROR', 500, 'N', '예기치 않은 오류가 발생했습니다.');

INSERT INTO sp_error_constraint_map (constraint_name, error_code) VALUES
    ('UQ_SP_DEC_REQUEST_ID', 'IDEMPOTENCY_KEY_REUSED');
INSERT INTO sp_error_constraint_map (constraint_name, error_code) VALUES
    ('UQ_SP_DEC_REC_SEQ', 'DECISION_ALREADY_TERMINAL');

CREATE INDEX ix_sp_draft_donor_active ON sp_transfer_draft (
    donor_store_id, sku_id, draft_status
);

COMMENT ON COLUMN sp_rebalance_decision.decision_request_id IS
    '승인 요청 멱등성 키 (클라이언트 Idempotency-Key)';
COMMENT ON COLUMN sp_rebalance_decision.policy_exception_flag IS
    '정책 예외 승인 여부 (Y: 정책 예외, N: 일반)';

COMMENT ON TABLE sp_approval_basis IS
    '승인 시점에 재계산되어 검증에 사용된 근거 스냅샷 (결정과 1:1)';
COMMENT ON COLUMN sp_approval_basis.candidate_eligible_flag IS
    '승인 시점 재계산된 후보 실행 가능 여부 (Y: 가능, N: 불가능)';

COMMENT ON TABLE sp_error_catalog IS 'REST 오류 코드별 HTTP 상태, 재시도 가능 여부와 한국어 문구';
COMMENT ON COLUMN sp_error_catalog.retryable_flag IS '클라이언트 재시도 권장 여부 (Y: 권장, N: 비권장)';

COMMENT ON TABLE sp_error_constraint_map IS 'Oracle 제약 위반을 오류 코드로 매핑하는 조회 테이블';
