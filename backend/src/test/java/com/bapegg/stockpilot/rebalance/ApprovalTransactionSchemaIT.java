package com.bapegg.stockpilot.rebalance;

import com.bapegg.stockpilot.analysis.InventoryAnalysisRules;
import com.bapegg.stockpilot.analysis.InventoryMetricCalculation;
import com.bapegg.stockpilot.analysis.SpAnalysisRun;
import com.bapegg.stockpilot.analysis.SpAnalysisRunRepository;
import com.bapegg.stockpilot.analysis.SpInventoryMetric;
import com.bapegg.stockpilot.analysis.SpInventoryMetricRepository;
import com.bapegg.stockpilot.inventory.SpInventorySnapshot;
import com.bapegg.stockpilot.inventory.SpInventorySnapshotRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the V10/V11/V12/V13 approval-transaction schema (sp_approval_basis,
 * sp_error_catalog, sp_error_constraint_map, and the new sp_rebalance_decision
 * columns) directly against Oracle with raw SQL, since no JPA entity exists yet for
 * the new tables -- that is the next unit (JPA mapping). Skipped (not failed) when
 * DB_URL is not set.
 * <p>
 * The whole class runs each test in one Spring-managed transaction that always rolls
 * back, so every row inserted or mutated here -- including the ones used to prove a
 * constraint rejects an invalid value -- never actually lands in the shared Oracle
 * instance and never collides between test methods.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "DB_URL", matches = ".+")
@Transactional
class ApprovalTransactionSchemaIT {

    private static final LocalDate ANALYSIS_DATE = LocalDate.parse("2026-08-25");
    private static final String RULE_VERSION = InventoryAnalysisRules.RULE_VERSION + "-V11-SCHEMA-IT";

    /**
     * Every {@code sp_error_catalog} row's exact expected metadata, pinning
     * {@code default_detail_ko} to the same text as {@code message_ko} for the eight
     * codes V10 first seeded (V11 backfilled {@code default_detail_ko} from
     * {@code message_ko} for those) and to its own distinct V11-inserted text for the
     * five approval-specific codes.
     */
    private record ExpectedErrorCode(
            String code, int httpStatus, String retryableFlag, String titleKo, String defaultDetailKo) {
    }

    private static final List<ExpectedErrorCode> EXPECTED_ERROR_CODES = List.of(
            new ExpectedErrorCode("IDEMPOTENCY_KEY_REUSED", 409, "N", "멱등성 키 재사용",
                    "동일한 Idempotency-Key가 다른 요청 내용으로 이미 사용됐습니다."),
            new ExpectedErrorCode("STALE_RECOMMENDATION", 409, "N", "추천 근거 최신 아님",
                    "추천 근거가 최신 상태와 달라 승인할 수 없습니다. 최신 근거로 다시 확인하세요."),
            new ExpectedErrorCode("APPROVAL_LOCK_TIMEOUT", 503, "Y", "승인 잠금 시간 초과",
                    "동시 처리 중인 다른 요청으로 잠금을 얻지 못했습니다. 잠시 후 다시 시도하세요."),
            new ExpectedErrorCode("VALIDATION_ERROR", 400, "N", "요청 값 검증 실패",
                    "요청 형식 또는 값이 올바르지 않습니다."),
            new ExpectedErrorCode("NOT_FOUND", 404, "N", "자원 없음",
                    "요청한 자원을 찾을 수 없습니다."),
            new ExpectedErrorCode("DECISION_ALREADY_TERMINAL", 409, "N", "이미 종료된 결정",
                    "이미 종료 상태(APPROVED/REJECTED/EXPIRED)인 결정에는 새 결정을 추가할 수 없습니다."),
            new ExpectedErrorCode("PERSISTENCE_UNAVAILABLE", 503, "Y", "저장소 일시 불가",
                    "저장소에 일시적으로 접근할 수 없습니다. 잠시 후 다시 시도하세요."),
            new ExpectedErrorCode("INTERNAL_SERVER_ERROR", 500, "N", "내부 오류",
                    "예기치 않은 오류가 발생했습니다."),
            new ExpectedErrorCode("INVALID_REQUEST", 400, "N", "요청 값 오류",
                    "요청 필드 값이 승인 요청 계약과 일치하지 않습니다."),
            new ExpectedErrorCode("INVALID_DECISION_REQUEST", 400, "N", "결정 요청 형식 오류",
                    "상태별로 필요한 수량, 사유 코드 또는 사유가 누락되었거나 잘못되었습니다."),
            new ExpectedErrorCode("RECOMMENDATION_NOT_FOUND", 404, "N", "추천 없음",
                    "지정한 recommendationId에 해당하는 추천이 없습니다."),
            new ExpectedErrorCode("INVALID_DECISION_TRANSITION", 409, "N", "허용되지 않는 결정 전이",
                    "결정 상태 기계에서 허용하지 않는 상태 전이를 요청했습니다."),
            new ExpectedErrorCode("DECISION_CONFLICT", 409, "N", "결정 저장 충돌",
                    "동시에 처리된 다른 요청과 충돌해 결정을 저장하지 못했습니다. 최신 상태로 다시 시도하세요."));

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private SpAnalysisRunRepository analysisRunRepository;

    @Autowired
    private SpInventoryMetricRepository metricRepository;

    @Autowired
    private SpInventorySnapshotRepository snapshotRepository;

    @Autowired
    private SpRebalanceRecommendationRepository recommendationRepository;

    @Test
    void newColumnsHaveTheCorrectTypeNullabilityAndForeignKeyTarget() {
        Map<String, Object> analysisRunIdColumn = jdbcTemplate.queryForMap(
                "SELECT data_type, nullable FROM user_tab_columns "
                        + "WHERE table_name = 'SP_APPROVAL_BASIS' AND column_name = 'ANALYSIS_RUN_ID'");
        assertEquals("NUMBER", analysisRunIdColumn.get("DATA_TYPE"));
        assertEquals("N", analysisRunIdColumn.get("NULLABLE"));

        String fkLocalColumn = jdbcTemplate.queryForObject(
                "SELECT column_name FROM user_cons_columns WHERE constraint_name = 'FK_SP_BASIS_ANALYSIS_RUN'",
                String.class);
        assertEquals("ANALYSIS_RUN_ID", fkLocalColumn);

        Map<String, Object> fkTarget = jdbcTemplate.queryForMap(
                "SELECT rc.table_name AS ref_table, rcc.column_name AS ref_column "
                        + "FROM user_constraints c "
                        + "JOIN user_constraints rc ON rc.constraint_name = c.r_constraint_name "
                        + "JOIN user_cons_columns rcc ON rcc.constraint_name = rc.constraint_name "
                        + "WHERE c.constraint_name = 'FK_SP_BASIS_ANALYSIS_RUN'");
        assertEquals("SP_ANALYSIS_RUN", fkTarget.get("REF_TABLE"));
        assertEquals("ANALYSIS_RUN_ID", fkTarget.get("REF_COLUMN"));

        for (String column : List.of("BASIS_CONTRACT_VERSION", "RECEIVER_PROJECTED_BEFORE_DEMAND",
                "DONOR_PROJECTED_AT_DISPATCH", "ALREADY_APPROVED_DRAFT_QUANTITY")) {
            String nullable = jdbcTemplate.queryForObject(
                    "SELECT nullable FROM user_tab_columns WHERE table_name = 'SP_APPROVAL_BASIS' AND column_name = ?",
                    String.class, column);
            assertEquals("N", nullable, column + " must be NOT NULL");
        }

        for (String column : List.of("TITLE_KO", "DEFAULT_DETAIL_KO", "ACTIVE_FLAG", "UPDATED_AT")) {
            String nullable = jdbcTemplate.queryForObject(
                    "SELECT nullable FROM user_tab_columns WHERE table_name = 'SP_ERROR_CATALOG' AND column_name = ?",
                    String.class, column);
            assertEquals("N", nullable, column + " must be NOT NULL");
        }

        assertUniqueConstraint("UQ_SP_DEC_REQUEST_ID", "SP_REBALANCE_DECISION", "DECISION_REQUEST_ID");
        assertUniqueConstraint("UQ_SP_BASIS_DECISION", "SP_APPROVAL_BASIS", "DECISION_ID");
    }

    /**
     * A prior version of this test only checked {@code user_cons_columns} for the
     * column name, which would still pass if {@code UQ_SP_DEC_REQUEST_ID} were ever
     * replaced by a same-named non-unique index or a constraint on the wrong table.
     * This confirms the actual duplicate-insert behavior the unique constraint exists
     * to guarantee, on a live Oracle connection.
     */
    @Test
    void decisionRequestIdRejectsAReusedIdempotencyKeyOnADifferentDecision() {
        long recommendationId = createTestRecommendation();
        String reusedRequestId = "IT-DUPLICATE-" + UUID.randomUUID();

        jdbcTemplate.update(
                "INSERT INTO sp_rebalance_decision (recommendation_id, decision_status, selected_quantity, reason, "
                        + "actor_label, decision_sequence, decision_contract_version, recommendation_version, "
                        + "decision_request_id, policy_exception_flag) "
                        + "VALUES (?, 'APPROVED', 5, NULL, 'schema-it', 1, 'MVP-2', 1, ?, 'N')",
                recommendationId, reusedRequestId);

        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update(
                "INSERT INTO sp_rebalance_decision (recommendation_id, decision_status, selected_quantity, reason, "
                        + "actor_label, decision_sequence, decision_contract_version, recommendation_version, "
                        + "decision_request_id, policy_exception_flag) "
                        + "VALUES (?, 'APPROVED', 7, NULL, 'schema-it', 2, 'MVP-2', 1, ?, 'N')",
                recommendationId, reusedRequestId));
    }

    /** Same rationale as above, for {@code UQ_SP_BASIS_DECISION}. */
    @Test
    void approvalBasisRejectsASecondRowForTheSameDecision() {
        long recommendationId = createTestRecommendation();
        long decisionId = insertMvp2ApprovedDecision(recommendationId, 1);

        insertBasis(decisionId, "Y");

        assertThrows(DataIntegrityViolationException.class, () -> insertBasis(decisionId, "Y"));
    }

    @Test
    void errorCatalogHasExactlyTheApprovedRowsWithExactMetadata() {
        // sp_error_catalog is a shared table -- V14 later added its own MVP-2 analysis-API rows to
        // it, so this counts only the approval use case's own 13 codes (proving none of them was
        // accidentally removed or duplicated), not the table's total row count.
        List<String> approvalCodes = EXPECTED_ERROR_CODES.stream().map(ExpectedErrorCode::code).toList();
        Integer approvalRowCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sp_error_catalog WHERE error_code IN ("
                        + String.join(",", java.util.Collections.nCopies(approvalCodes.size(), "?")) + ")",
                Integer.class, approvalCodes.toArray());
        assertEquals(EXPECTED_ERROR_CODES.size(), approvalRowCount,
                "sp_error_catalog must contain exactly the 13 approved codes, no more, no fewer");

        for (ExpectedErrorCode expected : EXPECTED_ERROR_CODES) {
            Map<String, Object> row = jdbcTemplate.queryForMap(
                    "SELECT http_status, retryable_flag, active_flag, title_ko, default_detail_ko "
                            + "FROM sp_error_catalog WHERE error_code = ?",
                    expected.code());
            assertEquals(expected.httpStatus(), ((Number) row.get("HTTP_STATUS")).intValue(),
                    expected.code() + ".http_status");
            assertEquals(expected.retryableFlag(), row.get("RETRYABLE_FLAG"), expected.code() + ".retryable_flag");
            assertEquals("Y", row.get("ACTIVE_FLAG"), expected.code() + ".active_flag");
            assertEquals(expected.titleKo(), row.get("TITLE_KO"), expected.code() + ".title_ko");
            assertEquals(expected.defaultDetailKo(), row.get("DEFAULT_DETAIL_KO"), expected.code() + ".default_detail_ko");
        }

        String recSeqMappedCode = jdbcTemplate.queryForObject(
                "SELECT error_code FROM sp_error_constraint_map WHERE constraint_name = 'UQ_SP_DEC_REC_SEQ'",
                String.class);
        assertEquals("DECISION_CONFLICT", recSeqMappedCode,
                "A bare unique-sequence violation cannot itself prove terminal state, per the V11 finding");

        String idempotencyMappedCode = jdbcTemplate.queryForObject(
                "SELECT error_code FROM sp_error_constraint_map WHERE constraint_name = 'UQ_SP_DEC_REQUEST_ID'",
                String.class);
        assertEquals("IDEMPOTENCY_KEY_REUSED", idempotencyMappedCode);

        Integer constraintMapRowCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sp_error_constraint_map", Integer.class);
        assertEquals(2, constraintMapRowCount);
    }

    /**
     * Pins the exact V12/V13 comment text -- not just "some comment exists" -- for every
     * V10/V11 column V12 corrected to follow V9's convention: plain columns get a
     * concise Korean noun phrase (no English identifiers, no Java symbols, no
     * explanatory rationale), and enum-like code/status/flag columns get
     * "값: 한국어 의미" for every currently allowed value, matching the exact style
     * already established on sp_store_transfer_route/sp_metric_quality_flag in V9.
     */
    @Test
    void newAndChangedColumnsMatchTheExactV9StyleComment() {
        assertColumnComment("SP_REBALANCE_DECISION", "DECISION_REQUEST_ID", "승인 요청 멱등성 키");
        assertColumnComment("SP_REBALANCE_DECISION", "POLICY_EXCEPTION_FLAG",
                "정책 예외 승인 여부 (Y: 정책 예외, N: 일반)");

        assertTableComment("SP_APPROVAL_BASIS", "승인 결정 근거 스냅샷");
        assertColumnComment("SP_APPROVAL_BASIS", "APPROVAL_BASIS_ID", "승인 근거 ID");
        assertColumnComment("SP_APPROVAL_BASIS", "DECISION_ID", "결정 ID");
        assertColumnComment("SP_APPROVAL_BASIS", "ANALYSIS_RUN_ID", "분석 실행 ID");
        assertColumnComment("SP_APPROVAL_BASIS", "INPUT_SNAPSHOT_VERSION", "입력 스냅샷 버전");
        assertColumnComment("SP_APPROVAL_BASIS", "RULE_VERSION", "규칙 버전");
        assertColumnComment("SP_APPROVAL_BASIS", "CANDIDATE_VERSION", "후보 버전");
        assertColumnComment("SP_APPROVAL_BASIS", "CANDIDATE_ELIGIBLE_FLAG", "후보 실행 가능 여부 (Y: 가능)");
        assertColumnComment("SP_APPROVAL_BASIS", "RECOMMENDED_BASE_QUANTITY", "추천 기준수량");
        assertColumnComment("SP_APPROVAL_BASIS", "DONOR_TRANSFERABLE_QUANTITY", "출고 매장 이동 가능 수량");
        assertColumnComment("SP_APPROVAL_BASIS", "ROUTE_MINIMUM_QUANTITY", "경로 최소 수량");
        assertColumnComment("SP_APPROVAL_BASIS", "PACKAGE_MULTIPLE", "경로 포장 배수");
        assertColumnComment("SP_APPROVAL_BASIS", "ROUTE_MAXIMUM_QUANTITY", "경로 최대 수량");
        assertColumnComment("SP_APPROVAL_BASIS", "RECEIVER_CAPACITY_REMAINING", "입고 매장 잔여 수용량");
        assertColumnComment("SP_APPROVAL_BASIS", "BASIS_CONTRACT_VERSION", "승인 근거 계약 버전");
        assertColumnComment("SP_APPROVAL_BASIS", "RECEIVER_PROJECTED_BEFORE_DEMAND", "입고 매장 수요 반영 전 예상재고");
        assertColumnComment("SP_APPROVAL_BASIS", "DONOR_PROJECTED_AT_DISPATCH", "출고 매장 출고 시점 예상재고");
        assertColumnComment("SP_APPROVAL_BASIS", "ALREADY_APPROVED_DRAFT_QUANTITY", "출고 매장 기승인 활성 이동 초안 합계수량");
        assertColumnComment("SP_APPROVAL_BASIS", "CREATED_AT", "생성일시");

        assertTableComment("SP_ERROR_CATALOG", "REST 오류 코드 카탈로그");
        assertColumnComment("SP_ERROR_CATALOG", "ERROR_CODE",
                "오류 코드 (INVALID_REQUEST: 요청 값 오류, INVALID_DECISION_REQUEST: 결정 요청 형식 오류, "
                        + "RECOMMENDATION_NOT_FOUND: 추천 없음, STALE_RECOMMENDATION: 추천 근거 최신 아님, "
                        + "INVALID_DECISION_TRANSITION: 허용되지 않는 결정 전이, IDEMPOTENCY_KEY_REUSED: 멱등성 키 재사용, "
                        + "DECISION_CONFLICT: 결정 저장 충돌, APPROVAL_LOCK_TIMEOUT: 승인 잠금 시간 초과, "
                        + "PERSISTENCE_UNAVAILABLE: 저장소 일시 불가, INTERNAL_SERVER_ERROR: 내부 오류, "
                        + "VALIDATION_ERROR: 요청 값 검증 실패, NOT_FOUND: 자원 없음, "
                        + "DECISION_ALREADY_TERMINAL: 이미 종료된 결정)");
        assertColumnComment("SP_ERROR_CATALOG", "HTTP_STATUS", "HTTP 상태 코드");
        assertColumnComment("SP_ERROR_CATALOG", "MESSAGE_KO", "한국어 오류 메시지");
        assertColumnComment("SP_ERROR_CATALOG", "TITLE_KO", "오류 제목(한국어)");
        assertColumnComment("SP_ERROR_CATALOG", "DEFAULT_DETAIL_KO", "오류 기본 상세 설명(한국어)");
        assertColumnComment("SP_ERROR_CATALOG", "ACTIVE_FLAG", "사용 여부 (Y: 사용, N: 비활성)");
        assertColumnComment("SP_ERROR_CATALOG", "CREATED_AT", "생성일시");
        assertColumnComment("SP_ERROR_CATALOG", "UPDATED_AT", "수정일시");

        assertTableComment("SP_ERROR_CONSTRAINT_MAP", "제약명-오류 코드 매핑");
        assertColumnComment("SP_ERROR_CONSTRAINT_MAP", "ERROR_CODE", "매핑된 오류 코드");
        assertColumnComment("SP_ERROR_CONSTRAINT_MAP", "CREATED_AT", "생성일시");
    }

    @Test
    void everyNewV10ThroughV12ColumnHasAKoreanComment() {
        List<String> uncommented = jdbcTemplate.query(
                "SELECT c.table_name AS t, c.column_name AS c "
                        + "FROM user_tab_columns c "
                        + "LEFT JOIN user_col_comments cc "
                        + "  ON cc.table_name = c.table_name AND cc.column_name = c.column_name "
                        + "WHERE c.table_name IN ('SP_APPROVAL_BASIS', 'SP_ERROR_CATALOG', 'SP_ERROR_CONSTRAINT_MAP') "
                        + "AND (cc.comments IS NULL OR TRIM(cc.comments) IS NULL)",
                (rs, rowNum) -> rs.getString("t") + "." + rs.getString("c"));
        assertTrue(uncommented.isEmpty(), "Missing Korean comments on: " + uncommented);

        List<String> decisionColumnsUncommented = jdbcTemplate.query(
                "SELECT c.column_name AS c "
                        + "FROM user_tab_columns c "
                        + "LEFT JOIN user_col_comments cc "
                        + "  ON cc.table_name = c.table_name AND cc.column_name = c.column_name "
                        + "WHERE c.table_name = 'SP_REBALANCE_DECISION' "
                        + "AND c.column_name IN ('DECISION_REQUEST_ID', 'POLICY_EXCEPTION_FLAG') "
                        + "AND (cc.comments IS NULL OR TRIM(cc.comments) IS NULL)",
                (rs, rowNum) -> rs.getString("c"));
        assertTrue(decisionColumnsUncommented.isEmpty(), "Missing Korean comments on: " + decisionColumnsUncommented);
    }

    @Test
    void candidateEligibleFlagOnlyAllowsY() {
        long recommendationId = createTestRecommendation();
        long eligibleDecisionId = insertMvp2ApprovedDecision(recommendationId, 1);
        long ineligibleDecisionId = insertMvp2ApprovedDecision(recommendationId, 2);

        insertBasis(eligibleDecisionId, "Y");

        assertThrows(DataIntegrityViolationException.class, () -> insertBasis(ineligibleDecisionId, "N"),
                "candidate_eligible_flag='N' must be rejected: an ineligible candidate cannot be approved, "
                        + "so it never has an approval basis to record");
    }

    @Test
    void analysisRunIdMustReferenceARealAnalysisRun() {
        long recommendationId = createTestRecommendation();
        long decisionId = insertMvp2ApprovedDecision(recommendationId, 1);

        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update(
                "INSERT INTO sp_approval_basis ("
                        + "decision_id, analysis_run_id, input_snapshot_version, rule_version, candidate_version, "
                        + "candidate_eligible_flag, recommended_base_quantity, donor_transferable_quantity, "
                        + "route_minimum_quantity, package_multiple, route_maximum_quantity, "
                        + "receiver_capacity_remaining, basis_contract_version, receiver_projected_before_demand, "
                        + "donor_projected_at_dispatch, already_approved_draft_quantity"
                        + ") VALUES (?, -999999, 'MVP-2-GS-V1', ?, 1, 'Y', 19, 61, 1, 1, 50, 94, 'MVP-2', 10, 40, 0)",
                decisionId, RULE_VERSION));
    }

    @Test
    void policyExceptionFlagYIsOnlyAllowedForAnMvp2ApprovedDecision() {
        long recommendationId = createTestRecommendation();

        // Valid: MVP-2 contract, APPROVED status, explicit policy exception.
        insertDecision(recommendationId, 1, "APPROVED", "MVP-2", 5, null, "policy exception approval", "Y");

        // Invalid: MVP-2 contract but HELD, not APPROVED.
        assertThrows(DataIntegrityViolationException.class, () -> insertDecision(
                recommendationId, 2, "HELD", "MVP-2", null, "NEEDS_REVIEW", "should be rejected", "Y"));

        // Invalid: an MVP-1-contract decision cannot carry a policy exception at all.
        long mvp1DecisionId = insertDecision(recommendationId, 3, "APPROVED", "MVP-1", 5, null, "mvp1 shape", "N");
        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update(
                "UPDATE sp_rebalance_decision SET policy_exception_flag = 'Y' WHERE decision_id = ?", mvp1DecisionId));
    }

    private void assertColumnComment(String table, String column, String expectedComment) {
        String comment = jdbcTemplate.queryForObject(
                "SELECT comments FROM user_col_comments WHERE table_name = ? AND column_name = ?",
                String.class, table, column);
        assertEquals(expectedComment, comment, table + "." + column + " comment");
    }

    private void assertTableComment(String table, String expectedComment) {
        String comment = jdbcTemplate.queryForObject(
                "SELECT comments FROM user_tab_comments WHERE table_name = ?", String.class, table);
        assertEquals(expectedComment, comment, table + " table comment");
    }

    /**
     * Confirms {@code constraintName} is actually a {@code UNIQUE} constraint on
     * {@code table}'s {@code column} -- not merely that some constraint with that name
     * exists and happens to touch that column, which a same-named check or a unique
     * constraint on the wrong table would also satisfy.
     */
    private void assertUniqueConstraint(String constraintName, String table, String column) {
        Integer matchCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_constraints c "
                        + "JOIN user_cons_columns cc ON cc.constraint_name = c.constraint_name "
                        + "WHERE c.constraint_name = ? AND c.constraint_type = 'U' "
                        + "AND c.table_name = ? AND cc.column_name = ?",
                Integer.class, constraintName, table, column);
        assertEquals(1, matchCount,
                constraintName + " must be a UNIQUE constraint on " + table + "." + column);
    }

    private long createTestRecommendation() {
        SpInventorySnapshot gangnamSnapshot = findSnapshot("STORE-GANGNAM");
        SpInventorySnapshot hongdaeSnapshot = findSnapshot("STORE-HONGDAE");

        SpAnalysisRun testRun = analysisRunRepository.save(new SpAnalysisRun(ANALYSIS_DATE, RULE_VERSION));
        testRun.markCompleted();
        testRun = analysisRunRepository.save(testRun);

        SpInventoryMetric receiverMetric = metricRepository.save(new SpInventoryMetric(
                testRun, gangnamSnapshot, InventoryMetricCalculation.calculate(6, 1, 28)));
        SpInventoryMetric donorMetric = metricRepository.save(new SpInventoryMetric(
                testRun, hongdaeSnapshot, InventoryMetricCalculation.calculate(42, 2, 4)));
        SpRebalanceRecommendation recommendation = recommendationRepository.save(new SpRebalanceRecommendation(
                receiverMetric, donorMetric, new RebalanceCalculation(25, 30, 25)));

        return recommendation.getRecommendationId();
    }

    private long insertMvp2ApprovedDecision(long recommendationId, int sequence) {
        return insertDecision(recommendationId, sequence, "APPROVED", "MVP-2", 5, null, null, "N");
    }

    private long insertDecision(long recommendationId, int sequence, String status, String contractVersion,
            Integer selectedQuantity, String reasonCode, String reason, String policyExceptionFlag) {
        String requestId = "IT-" + UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO sp_rebalance_decision (recommendation_id, decision_status, selected_quantity, reason, "
                        + "actor_label, decision_sequence, decision_contract_version, reason_code, "
                        + "recommendation_version, decision_request_id, policy_exception_flag) "
                        + "VALUES (?, ?, ?, ?, 'schema-it', ?, ?, ?, 1, ?, ?)",
                recommendationId, status, selectedQuantity, reason, sequence, contractVersion, reasonCode,
                requestId, policyExceptionFlag);
        return jdbcTemplate.queryForObject(
                "SELECT decision_id FROM sp_rebalance_decision WHERE decision_request_id = ?", Long.class, requestId);
    }

    private void insertBasis(long decisionId, String eligibleFlag) {
        jdbcTemplate.update(
                "INSERT INTO sp_approval_basis ("
                        + "decision_id, analysis_run_id, input_snapshot_version, rule_version, candidate_version, "
                        + "candidate_eligible_flag, recommended_base_quantity, donor_transferable_quantity, "
                        + "route_minimum_quantity, package_multiple, route_maximum_quantity, "
                        + "receiver_capacity_remaining, basis_contract_version, receiver_projected_before_demand, "
                        + "donor_projected_at_dispatch, already_approved_draft_quantity"
                        + ") VALUES (?, (SELECT analysis_run_id FROM sp_analysis_run WHERE rule_version = ?), "
                        + "'MVP-2-GS-V1', ?, 1, ?, 19, 61, 1, 1, 50, 94, 'MVP-2', 10, 40, 0)",
                decisionId, RULE_VERSION, RULE_VERSION, eligibleFlag);
    }

    private SpInventorySnapshot findSnapshot(String storeId) {
        return snapshotRepository.findBySnapshotDate(ANALYSIS_DATE).stream()
                .filter(s -> storeId.equals(s.getStoreId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No snapshot found for store " + storeId));
    }
}
