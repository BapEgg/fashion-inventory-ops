-- New sp_error_catalog rows for the MVP-2 inventory-exception read API. No schema change --
-- V11 already added title_ko/default_detail_ko/active_flag/updated_at with the DEFAULTs
-- this INSERT relies on.

INSERT INTO sp_error_catalog (error_code, http_status, retryable_flag, message_ko, title_ko, default_detail_ko) VALUES
    ('ANALYSIS_RESULTS_NOT_READY', 409, 'Y', '요청한 분석 실행이 아직 완료되지 않아 재고 예외 결과를 조회할 수 없습니다.',
     '분석 결과 준비 중', '요청한 분석 실행이 아직 완료되지 않아 재고 예외 결과를 조회할 수 없습니다.');
INSERT INTO sp_error_catalog (error_code, http_status, retryable_flag, message_ko, title_ko, default_detail_ko) VALUES
    ('INVENTORY_EXCEPTION_NOT_FOUND', 404, 'N', '지정한 inventoryMetricId에 해당하는 조회 가능한 재고 예외가 없습니다.',
     '재고 예외 없음', '지정한 inventoryMetricId에 해당하는 조회 가능한 재고 예외가 없습니다.');
