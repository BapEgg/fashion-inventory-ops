package com.bapegg.stockpilot.api.error;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the six {@code V14} rows in the real Oracle {@code sp_error_catalog}, per
 * current-task.md's Required tests item 4: the *exact* status/retryable/title/detail/message text
 * the DML inserted, not merely that some non-blank text exists -- pinning the literal Korean
 * strings so a future edit to the migration is caught here. Skipped (not failed) when DB_URL is
 * not set.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "DB_URL", matches = ".+")
class V14ErrorCatalogOracleIT {

    private static final Map<String, ExpectedRow> EXPECTED = Map.of(
            "ANALYSIS_ALREADY_RUNNING", new ExpectedRow(409, true,
                    "분석 실행 중",
                    "같은 analysisDate/inputSnapshotVersion 조합의 분석이 이미 실행 중입니다. 완료된 뒤 다시 요청하세요.",
                    "동일한 분석 요청이 이미 실행 중입니다. 완료된 뒤 다시 시도하세요."),
            "ANALYSIS_LAUNCH_CONFLICT", new ExpectedRow(409, true,
                    "분석 시작 충돌",
                    "동일한 분석을 동시에 시작하려는 다른 요청과 충돌했습니다. 잠시 후 다시 시도하세요.",
                    "동시 요청으로 분석 시작이 충돌했습니다. 잠시 후 다시 시도하세요."),
            "ANALYSIS_INPUT_INVALID", new ExpectedRow(422, true,
                    "분석 입력 데이터 부족",
                    "요청한 analysisDate/inputSnapshotVersion에 대한 입력 데이터가 계약을 충족하지 못했습니다. 입력을 확인한 뒤 다시 시도하세요.",
                    "분석에 필요한 입력 데이터가 아직 갖춰지지 않았습니다. 입력 적재 후 다시 시도하세요."),
            "ANALYSIS_RESTART_UNAVAILABLE", new ExpectedRow(409, false,
                    "분석 재시작 불가",
                    "분석 Job이 중지(STOPPED)되었거나 중단(ABANDONED)되어 자동 재시작 대상이 아닙니다. 운영자 확인이 필요합니다.",
                    "이 분석은 현재 상태에서 자동으로 재시작할 수 없습니다. 운영자에게 문의하세요."),
            "ANALYSIS_NOT_FOUND", new ExpectedRow(404, false,
                    "분석 실행 없음",
                    "지정한 analysisRunId에 해당하는 분석 실행이 없습니다. id를 다시 확인하세요.",
                    "요청한 분석 실행을 찾을 수 없습니다. analysisRunId를 확인하세요."),
            "ANALYSIS_EXECUTION_FAILED", new ExpectedRow(500, false,
                    "분석 실행 실패",
                    "분석 Job이 분류되지 않은 원인으로 실패했습니다. 로그 확인 후 운영자에게 문의하세요.",
                    "분석 실행 중 예기치 않은 오류가 발생했습니다. 문제가 계속되면 운영자에게 문의하세요."));

    @Autowired
    private SpErrorCatalogRepository repository;

    @Test
    void everyV14RowHasTheExactStatusRetryableTitleDetailMessageAndIsActive() {
        for (Map.Entry<String, ExpectedRow> entry : EXPECTED.entrySet()) {
            String code = entry.getKey();
            ExpectedRow expected = entry.getValue();
            SpErrorCatalog row = repository.findById(code)
                    .orElseThrow(() -> new AssertionError("Expected a V14 sp_error_catalog row for " + code));

            assertEquals(expected.httpStatus(), row.getHttpStatus(), code + ": http_status");
            assertEquals(expected.retryable(), row.isRetryable(), code + ": retryable_flag");
            assertTrue(row.isActive(), code + ": active_flag must be Y");
            assertEquals(expected.titleKo(), row.getTitleKo(), code + ": title_ko");
            assertEquals(expected.defaultDetailKo(), row.getDefaultDetailKo(), code + ": default_detail_ko");
            assertEquals(expected.messageKo(), row.getMessageKo(), code + ": message_ko");
        }
    }

    private record ExpectedRow(int httpStatus, boolean retryable, String titleKo, String defaultDetailKo, String messageKo) {
    }
}
