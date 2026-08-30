-- New sp_error_catalog rows for the MVP-2 analysis launch/status REST contract. No schema
-- change: V11 already added title_ko/default_detail_ko/active_flag/updated_at with the
-- DEFAULTs this INSERT relies on.

INSERT INTO sp_error_catalog (error_code, http_status, retryable_flag, message_ko, title_ko, default_detail_ko) VALUES
    ('ANALYSIS_ALREADY_RUNNING', 409, 'Y', '동일한 분석 요청이 이미 실행 중입니다. 완료된 뒤 다시 시도하세요.',
     '분석 실행 중', '같은 analysisDate/inputSnapshotVersion 조합의 분석이 이미 실행 중입니다. 완료된 뒤 다시 요청하세요.');
INSERT INTO sp_error_catalog (error_code, http_status, retryable_flag, message_ko, title_ko, default_detail_ko) VALUES
    ('ANALYSIS_LAUNCH_CONFLICT', 409, 'Y', '동시 요청으로 분석 시작이 충돌했습니다. 잠시 후 다시 시도하세요.',
     '분석 시작 충돌', '동일한 분석을 동시에 시작하려는 다른 요청과 충돌했습니다. 잠시 후 다시 시도하세요.');
INSERT INTO sp_error_catalog (error_code, http_status, retryable_flag, message_ko, title_ko, default_detail_ko) VALUES
    ('ANALYSIS_INPUT_INVALID', 422, 'Y', '분석에 필요한 입력 데이터가 아직 갖춰지지 않았습니다. 입력 적재 후 다시 시도하세요.',
     '분석 입력 데이터 부족', '요청한 analysisDate/inputSnapshotVersion에 대한 입력 데이터가 계약을 충족하지 못했습니다. 입력을 확인한 뒤 다시 시도하세요.');
INSERT INTO sp_error_catalog (error_code, http_status, retryable_flag, message_ko, title_ko, default_detail_ko) VALUES
    ('ANALYSIS_RESTART_UNAVAILABLE', 409, 'N', '이 분석은 현재 상태에서 자동으로 재시작할 수 없습니다. 운영자에게 문의하세요.',
     '분석 재시작 불가', '분석 Job이 중지(STOPPED)되었거나 중단(ABANDONED)되어 자동 재시작 대상이 아닙니다. 운영자 확인이 필요합니다.');
INSERT INTO sp_error_catalog (error_code, http_status, retryable_flag, message_ko, title_ko, default_detail_ko) VALUES
    ('ANALYSIS_NOT_FOUND', 404, 'N', '요청한 분석 실행을 찾을 수 없습니다. analysisRunId를 확인하세요.',
     '분석 실행 없음', '지정한 analysisRunId에 해당하는 분석 실행이 없습니다. id를 다시 확인하세요.');
INSERT INTO sp_error_catalog (error_code, http_status, retryable_flag, message_ko, title_ko, default_detail_ko) VALUES
    ('ANALYSIS_EXECUTION_FAILED', 500, 'N', '분석 실행 중 예기치 않은 오류가 발생했습니다. 문제가 계속되면 운영자에게 문의하세요.',
     '분석 실행 실패', '분석 Job이 분류되지 않은 원인으로 실패했습니다. 로그 확인 후 운영자에게 문의하세요.');
