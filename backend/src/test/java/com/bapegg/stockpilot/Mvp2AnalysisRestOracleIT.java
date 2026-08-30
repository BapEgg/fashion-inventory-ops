package com.bapegg.stockpilot;

import com.bapegg.stockpilot.analysis.AnalysisRunStatus;
import com.bapegg.stockpilot.analysis.SpAnalysisRun;
import com.bapegg.stockpilot.analysis.SpAnalysisRunRepository;
import com.bapegg.stockpilot.api.error.ApiErrorCode;
import com.bapegg.stockpilot.batch.Mvp2AnalysisExecutor;
import com.bapegg.stockpilot.demand.DemandAnalysisRules;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end {@code POST}/{@code GET /api/analyses}} coverage for the MVP-2 launch/status REST
 * contract, per current-task.md's Required tests items 1 and 5: created/restart/replay status
 * codes, the additive response fields, forbidden/invalid request rejection, GET success/not-found/
 * invalid-id, every ProblemDetail's shared shape, and a latch-controlled concurrent POST proving
 * exactly one {@code 409 ANALYSIS_ALREADY_RUNNING}. Each test owns its own unique
 * {@code inputSnapshotVersion} (real seed rows built directly, mirroring
 * {@code Mvp2AnalysisJobRetryAndConcurrencyOracleIT}) and cleans up in a {@code finally} block.
 * Skipped (not failed) when DB_URL is not set.
 */
@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "DB_URL", matches = ".+")
class Mvp2AnalysisRestOracleIT {

    private static final LocalDate ANALYSIS_DATE = LocalDate.of(2026, 11, 2);
    private static final LocalDate MVP1_ANALYSIS_DATE = LocalDate.of(2026, 11, 3);
    private static final String STORE_ID = "STORE-GANGNAM";
    private static final String SKU_ID = "SKU-CAP-BLACK-FREE";
    private static final String RULE_VERSION = DemandAnalysisRules.RULE_VERSION;
    private static final ZoneOffset SEOUL_OFFSET = ZoneOffset.of("+09:00");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private tools.jackson.databind.ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoSpyBean
    private SpAnalysisRunRepository analysisRunRepository;

    @MockitoSpyBean
    private Mvp2AnalysisExecutor executor;

    @Test
    void aNewMvp2RequestIsCreatedAndReachableThroughGet() throws Exception {
        String inputVersion = "MVP2-REST-NEW-V1";
        try {
            insertFullAnchor(inputVersion, 5, 0);

            MvcResult created = mockMvc.perform(post("/api/analyses")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson(ANALYSIS_DATE, inputVersion)))
                    .andExpect(status().isCreated())
                    .andReturn();
            Map<String, Object> body = readMap(created);
            assertEquals(RULE_VERSION, body.get("ruleVersion"));
            assertEquals("COMPLETED", body.get("status"));
            assertEquals(Boolean.FALSE, body.get("alreadyCompleted"));
            assertEquals(inputVersion, body.get("inputSnapshotVersion"));
            assertNotNull(body.get("startedAt"));
            assertNotNull(body.get("completedAt"));
            Number analysisRunId = (Number) body.get("analysisRunId");
            assertNotNull(analysisRunId);
            assertEquals("/api/analyses/" + analysisRunId.longValue(),
                    created.getResponse().getHeader("Location"));

            MvcResult fetched = mockMvc.perform(get("/api/analyses/{id}", analysisRunId.longValue()))
                    .andExpect(status().isOk())
                    .andReturn();
            Map<String, Object> statusBody = readMap(fetched);
            assertEquals(analysisRunId.longValue(), ((Number) statusBody.get("analysisRunId")).longValue());
            assertEquals(inputVersion, statusBody.get("inputSnapshotVersion"));
            assertEquals("COMPLETED", statusBody.get("status"));
        } finally {
            cleanup(inputVersion);
        }
    }

    @Test
    void replayingAnAlreadyCompletedMvp2TripleReturnsOkWithAlreadyCompletedTrue() throws Exception {
        String inputVersion = "MVP2-REST-REPLAY-V1";
        try {
            insertFullAnchor(inputVersion, 5, 0);
            String body = requestJson(ANALYSIS_DATE, inputVersion);
            mockMvc.perform(post("/api/analyses").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isCreated());

            MvcResult replay = mockMvc.perform(post("/api/analyses").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isOk())
                    .andReturn();
            Map<String, Object> replayBody = readMap(replay);
            assertEquals(Boolean.TRUE, replayBody.get("alreadyCompleted"));
        } finally {
            cleanup(inputVersion);
        }
    }

    @Test
    void aFailedInputContractIsReportedAsFourTwentyTwoAndThenRestartsToOkAfterSeedDataArrives() throws Exception {
        String inputVersion = "MVP2-REST-RESTART-V1";
        try {
            String body = requestJson(ANALYSIS_DATE, inputVersion);

            // No seed data at all yet: the adapter finds no anchors.
            MvcResult failed = mockMvc.perform(post("/api/analyses").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isUnprocessableEntity())
                    .andReturn();
            Map<String, Object> failedProblem = readMap(failed);
            assertEquals(ApiErrorCode.ANALYSIS_INPUT_INVALID, failedProblem.get("code"));
            assertEquals(Boolean.TRUE, failedProblem.get("retryable"));

            SpAnalysisRun failedRun = analysisRunRepository
                    .findByAnalysisDateAndInputSnapshotVersionAndRuleVersion(ANALYSIS_DATE, inputVersion, RULE_VERSION)
                    .orElseThrow();
            assertEquals(AnalysisRunStatus.FAILED, failedRun.getRunStatus());

            insertFullAnchor(inputVersion, 5, 0);

            MvcResult restarted = mockMvc.perform(post("/api/analyses").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isOk())
                    .andReturn();
            Map<String, Object> restartedBody = readMap(restarted);
            assertEquals(Boolean.FALSE, restartedBody.get("alreadyCompleted"));
            assertEquals(failedRun.getAnalysisRunId().longValue(), ((Number) restartedBody.get("analysisRunId")).longValue());
        } finally {
            cleanup(inputVersion);
        }
    }

    @Test
    void aForbiddenRuleVersionFieldIsRejectedAsAValidationProblem() throws Exception {
        String requestBody = "{\"analysisDate\":\"" + ANALYSIS_DATE + "\",\"inputSnapshotVersion\":\"MVP2-REST-FORBIDDEN-V1\","
                + "\"ruleVersion\":\"" + RULE_VERSION + "\"}";

        MvcResult result = mockMvc.perform(post("/api/analyses").contentType(MediaType.APPLICATION_JSON).content(requestBody))
                .andExpect(status().isBadRequest())
                .andReturn();
        Map<String, Object> problem = readMap(result);
        assertEquals(ApiErrorCode.VALIDATION_ERROR, problem.get("code"));
        assertTrue(fieldErrorsOf(problem).stream()
                .anyMatch(fe -> "ruleVersion".equals(fe.get("field")) && "FORBIDDEN".equals(fe.get("code"))));
    }

    @Test
    void aBlankInputSnapshotVersionIsRejectedAsAValidationProblemWithAFieldError() throws Exception {
        String requestBody = "{\"analysisDate\":\"" + ANALYSIS_DATE + "\",\"inputSnapshotVersion\":\"   \"}";

        MvcResult result = mockMvc.perform(post("/api/analyses").contentType(MediaType.APPLICATION_JSON).content(requestBody))
                .andExpect(status().isBadRequest())
                .andReturn();
        Map<String, Object> problem = readMap(result);
        assertEquals(ApiErrorCode.VALIDATION_ERROR, problem.get("code"));
        assertTrue(fieldErrorsOf(problem).stream()
                .anyMatch(fe -> "inputSnapshotVersion".equals(fe.get("field")) && "REQUIRED".equals(fe.get("code"))),
                "A blank inputSnapshotVersion (service-layer check, not Bean Validation) must still produce a field error.");
    }

    @Test
    void anInputSnapshotVersionWithOuterWhitespaceIsRejectedAsAFormatFieldError() throws Exception {
        String requestBody = "{\"analysisDate\":\"" + ANALYSIS_DATE + "\",\"inputSnapshotVersion\":\" MVP2-REST-WS-V1\"}";

        MvcResult result = mockMvc.perform(post("/api/analyses").contentType(MediaType.APPLICATION_JSON).content(requestBody))
                .andExpect(status().isBadRequest())
                .andReturn();
        Map<String, Object> problem = readMap(result);
        assertTrue(fieldErrorsOf(problem).stream()
                .anyMatch(fe -> "inputSnapshotVersion".equals(fe.get("field")) && "FORMAT".equals(fe.get("code"))));
    }

    @Test
    void anInputSnapshotVersionLongerThanSixtyFourCharactersIsRejectedAsASizeFieldError() throws Exception {
        String requestBody = "{\"analysisDate\":\"" + ANALYSIS_DATE + "\",\"inputSnapshotVersion\":\"" + "V".repeat(65) + "\"}";

        MvcResult result = mockMvc.perform(post("/api/analyses").contentType(MediaType.APPLICATION_JSON).content(requestBody))
                .andExpect(status().isBadRequest())
                .andReturn();
        Map<String, Object> problem = readMap(result);
        assertTrue(fieldErrorsOf(problem).stream()
                .anyMatch(fe -> "inputSnapshotVersion".equals(fe.get("field")) && "SIZE".equals(fe.get("code"))));
    }

    @Test
    void aMalformedAnalysisDateIsRejectedAsAValidationProblem() throws Exception {
        String requestBody = "{\"analysisDate\":\"not-a-date\"}";

        MvcResult result = mockMvc.perform(post("/api/analyses").contentType(MediaType.APPLICATION_JSON).content(requestBody))
                .andExpect(status().isBadRequest())
                .andReturn();
        Map<String, Object> problem = readMap(result);
        assertEquals(ApiErrorCode.VALIDATION_ERROR, problem.get("code"));
    }

    @Test
    void aNullAnalysisDateIsRejectedAsAValidationProblemWithARequiredFieldError() throws Exception {
        String requestBody = "{\"analysisDate\":null}";

        MvcResult result = mockMvc.perform(post("/api/analyses").contentType(MediaType.APPLICATION_JSON).content(requestBody))
                .andExpect(status().isBadRequest())
                .andReturn();
        Map<String, Object> problem = readMap(result);
        assertEquals(ApiErrorCode.VALIDATION_ERROR, problem.get("code"));
        assertTrue(fieldErrorsOf(problem).stream()
                .anyMatch(fe -> "analysisDate".equals(fe.get("field")) && "REQUIRED".equals(fe.get("code"))));
    }

    @Test
    void getOnAnUnknownAnalysisRunIdIsNotFound() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/analyses/{id}", 999_999_999L))
                .andExpect(status().isNotFound())
                .andReturn();
        Map<String, Object> problem = readMap(result);
        assertEquals(ApiErrorCode.ANALYSIS_NOT_FOUND, problem.get("code"));
        assertProblemDetailShape(result, problem, ApiErrorCode.ANALYSIS_NOT_FOUND, "/api/analyses/999999999");
    }

    @Test
    void getWithANonPositiveIdIsRejectedAsAValidationProblem() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/analyses/{id}", 0L))
                .andExpect(status().isBadRequest())
                .andReturn();
        Map<String, Object> problem = readMap(result);
        assertEquals(ApiErrorCode.VALIDATION_ERROR, problem.get("code"));
    }

    @Test
    void getWithANonNumericIdIsRejectedAsAValidationProblem() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/analyses/{id}", "not-a-number"))
                .andExpect(status().isBadRequest())
                .andReturn();
        Map<String, Object> problem = readMap(result);
        assertEquals(ApiErrorCode.VALIDATION_ERROR, problem.get("code"));
    }

    /** P1: a GET repository failure must resolve through the same catalog-backed ProblemDetail boundary, not escape it. */
    @Test
    void getRepositoryFailureIsClassifiedThroughTheSameProblemDetailBoundary() throws Exception {
        Long simulatedId = 555_555_555L;
        Mockito.doThrow(new DataAccessResourceFailureException("simulated connection loss"))
                .when(analysisRunRepository).findById(simulatedId);

        MvcResult result = mockMvc.perform(get("/api/analyses/{id}", simulatedId))
                .andExpect(status().is(503))
                .andReturn();
        Map<String, Object> problem = readMap(result);
        assertProblemDetailShape(result, problem, ApiErrorCode.PERSISTENCE_UNAVAILABLE, "/api/analyses/" + simulatedId);
        assertEquals(Boolean.TRUE, problem.get("retryable"));
    }

    /** P1: a POST pre-launch domain-run read failure must also resolve through the same boundary. */
    @Test
    void postPreReadRepositoryFailureIsClassifiedThroughTheSameProblemDetailBoundary() throws Exception {
        String inputVersion = "MVP2-REST-PREREAD-FAIL-V1";
        Mockito.doThrow(new DataAccessResourceFailureException("simulated connection loss"))
                .when(analysisRunRepository)
                .findByAnalysisDateAndInputSnapshotVersionAndRuleVersion(ANALYSIS_DATE, inputVersion, RULE_VERSION);

        MvcResult result = mockMvc.perform(post("/api/analyses")
                        .contentType(MediaType.APPLICATION_JSON).content(requestJson(ANALYSIS_DATE, inputVersion)))
                .andExpect(status().is(503))
                .andReturn();
        Map<String, Object> problem = readMap(result);
        assertEquals(ApiErrorCode.PERSISTENCE_UNAVAILABLE, problem.get("code"));
        assertEquals(Boolean.TRUE, problem.get("retryable"));
    }

    @Test
    void mvp1RequestsWithNoInputSnapshotVersionStillReturnOkAndCarryTheLocationHeader() throws Exception {
        deleteMvp1Run(MVP1_ANALYSIS_DATE);
        try {
            MvcResult result = mockMvc.perform(post("/api/analyses")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"analysisDate\":\"" + MVP1_ANALYSIS_DATE + "\"}"))
                    .andExpect(status().isOk())
                    .andReturn();
            Map<String, Object> body = readMap(result);
            assertEquals(mvp1RuleVersion(), body.get("ruleVersion"));
            assertNotNull(result.getResponse().getHeader("Location"));
        } finally {
            deleteMvp1Run(MVP1_ANALYSIS_DATE);
        }
    }

    @Test
    void concurrentPostOfTheSameFreshTripleYieldsOneCreatedAndOneAlreadyRunning() throws Exception {
        String inputVersion = "MVP2-REST-CONCURRENCY-V1";
        try {
            insertFullAnchor(inputVersion, 5, 0);
            String requestBody = requestJson(ANALYSIS_DATE, inputVersion);

            CountDownLatch entered = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            Mockito.doAnswer(invocation -> {
                        entered.countDown();
                        assertTrue(release.await(10, TimeUnit.SECONDS),
                                "release must be signaled by the test before this stub's own wait times out.");
                        return invocation.callRealMethod();
                    })
                    .when(executor).execute(ANALYSIS_DATE, inputVersion, RULE_VERSION);

            ExecutorService pool = Executors.newSingleThreadExecutor();
            Future<MvcResult> firstFuture;
            try {
                firstFuture = pool.submit(() -> mockMvc.perform(post("/api/analyses")
                        .contentType(MediaType.APPLICATION_JSON).content(requestBody)).andReturn());
                assertTrue(entered.await(10, TimeUnit.SECONDS),
                        "The first request must reach the executor before the second is sent.");

                MvcResult second = mockMvc.perform(post("/api/analyses")
                                .contentType(MediaType.APPLICATION_JSON).content(requestBody))
                        .andExpect(status().isConflict())
                        .andReturn();
                Map<String, Object> problem = readMap(second);
                assertEquals(ApiErrorCode.ANALYSIS_ALREADY_RUNNING, problem.get("code"));
            } finally {
                release.countDown();
                pool.shutdown();
            }

            MvcResult first = firstFuture.get(15, TimeUnit.SECONDS);
            assertEquals(201, first.getResponse().getStatus());
        } finally {
            cleanup(inputVersion);
        }
    }

    private void assertProblemDetailShape(MvcResult result, Map<String, Object> problem, String code, String expectedInstance) {
        assertEquals("urn:stockpilot:error:" + code, problem.get("type"));
        assertEquals(expectedInstance, problem.get("instance"));
        assertEquals(code, problem.get("code"));
        String requestId = (String) problem.get("requestId");
        assertNotNull(requestId);
        assertEquals(requestId, result.getResponse().getHeader("X-Request-Id"));
        assertNotNull(problem.get("timestamp"));
    }

    private static String mvp1RuleVersion() {
        return com.bapegg.stockpilot.analysis.InventoryAnalysisRules.RULE_VERSION;
    }

    private String requestJson(LocalDate analysisDate, String inputSnapshotVersion) {
        return "{\"analysisDate\":\"" + analysisDate + "\",\"inputSnapshotVersion\":\"" + inputSnapshotVersion + "\"}";
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readMap(MvcResult result) throws Exception {
        return objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> fieldErrorsOf(Map<String, Object> problem) {
        Object fieldErrors = problem.get("fieldErrors");
        return fieldErrors == null ? List.of() : (List<Map<String, Object>>) fieldErrors;
    }

    private void insertFullAnchor(String version, long onHand, long reserved) {
        LocalDate historyStart = ANALYSIS_DATE.minusDays(28);
        for (int offset = 0; offset < 28; offset++) {
            LocalDate date = historyStart.plusDays(offset);
            insertInventoryDay(date, version, 20, 0);
            insertSalesDay(date, version, 2, 2, 1);
        }
        insertInventoryDay(ANALYSIS_DATE, version, onHand, reserved);
    }

    private void insertInventoryDay(LocalDate date, String version, long onHand, long reserved) {
        OffsetDateTime snapshotAt = date.atStartOfDay(SEOUL_OFFSET).toOffsetDateTime();
        String outOfStockFlag = (onHand - reserved) <= 0 ? "Y" : "N";
        jdbcTemplate.update(
                "INSERT INTO sp_inventory_snapshot (snapshot_date, store_id, sku_id, on_hand_quantity, "
                        + "reserved_quantity, source_type, input_snapshot_version, snapshot_at, out_of_stock_flag) "
                        + "VALUES (?, ?, ?, ?, ?, 'SYNTHETIC', ?, ?, ?)",
                date, STORE_ID, SKU_ID, onHand, reserved, version, snapshotAt, outOfStockFlag);
    }

    private void insertSalesDay(LocalDate date, String version, int soldQuantity, int transactionCount, int maxTransactionQuantity) {
        jdbcTemplate.update(
                "INSERT INTO sp_daily_sale (sales_date, store_id, sku_id, sold_quantity, source_type, "
                        + "input_snapshot_version, transaction_count, max_transaction_quantity, average_selling_price) "
                        + "VALUES (?, ?, ?, ?, 'SYNTHETIC', ?, ?, ?, 10000.00)",
                date, STORE_ID, SKU_ID, soldQuantity, version, transactionCount, maxTransactionQuantity);
    }

    private void deleteMvp1Run(LocalDate analysisDate) {
        analysisRunRepository.findByAnalysisDateAndRuleVersion(analysisDate, mvp1RuleVersion())
                .ifPresent(run -> {
                    jdbcTemplate.update("DELETE FROM sp_rebalance_recommendation WHERE receiver_metric_id IN "
                            + "(SELECT inventory_metric_id FROM sp_inventory_metric WHERE analysis_run_id = ?)", run.getAnalysisRunId());
                    jdbcTemplate.update("DELETE FROM sp_inventory_metric WHERE analysis_run_id = ?", run.getAnalysisRunId());
                    jdbcTemplate.update("DELETE FROM sp_analysis_run WHERE analysis_run_id = ?", run.getAnalysisRunId());
                });
        deleteBatchMetadataByRuleVersion(analysisDate, mvp1RuleVersion(), "inventoryAnalysisJob");
    }

    private void cleanup(String inputVersion) {
        analysisRunRepository.findByAnalysisDateAndInputSnapshotVersionAndRuleVersion(ANALYSIS_DATE, inputVersion, RULE_VERSION)
                .ifPresent(run -> deleteDomainOutput(run.getAnalysisRunId()));
        deleteBatchMetadataByInputVersion(inputVersion);
        jdbcTemplate.update("DELETE FROM sp_inventory_snapshot WHERE input_snapshot_version = ?", inputVersion);
        jdbcTemplate.update("DELETE FROM sp_daily_sale WHERE input_snapshot_version = ?", inputVersion);
    }

    private void deleteDomainOutput(Long runId) {
        jdbcTemplate.update(
                "DELETE FROM sp_rebalance_scenario WHERE recommendation_id IN "
                        + "(SELECT recommendation_id FROM sp_rebalance_recommendation WHERE receiver_metric_id IN "
                        + "(SELECT inventory_metric_id FROM sp_inventory_metric WHERE analysis_run_id = ?))", runId);
        jdbcTemplate.update(
                "DELETE FROM sp_candidate_reason WHERE recommendation_id IN "
                        + "(SELECT recommendation_id FROM sp_rebalance_recommendation WHERE receiver_metric_id IN "
                        + "(SELECT inventory_metric_id FROM sp_inventory_metric WHERE analysis_run_id = ?))", runId);
        jdbcTemplate.update(
                "DELETE FROM sp_rebalance_recommendation WHERE receiver_metric_id IN "
                        + "(SELECT inventory_metric_id FROM sp_inventory_metric WHERE analysis_run_id = ?)", runId);
        jdbcTemplate.update(
                "DELETE FROM sp_metric_quality_flag WHERE inventory_metric_id IN "
                        + "(SELECT inventory_metric_id FROM sp_inventory_metric WHERE analysis_run_id = ?)", runId);
        jdbcTemplate.update("DELETE FROM sp_inventory_metric WHERE analysis_run_id = ?", runId);
        jdbcTemplate.update("DELETE FROM sp_analysis_run WHERE analysis_run_id = ?", runId);
    }

    private void deleteBatchMetadataByInputVersion(String inputVersion) {
        List<Long> instanceIds = jdbcTemplate.queryForList(
                "SELECT DISTINCT je.JOB_INSTANCE_ID FROM BATCH_JOB_EXECUTION je "
                        + "JOIN BATCH_JOB_EXECUTION_PARAMS p ON p.JOB_EXECUTION_ID = je.JOB_EXECUTION_ID "
                        + "WHERE p.PARAMETER_NAME = 'inputSnapshotVersion' AND p.PARAMETER_VALUE = ?",
                Long.class, inputVersion);
        deleteBatchInstances(instanceIds);
    }

    private void deleteBatchMetadataByRuleVersion(LocalDate analysisDate, String ruleVersion, String jobName) {
        List<Long> instanceIds = jdbcTemplate.queryForList("""
                SELECT DISTINCT je.JOB_INSTANCE_ID
                FROM BATCH_JOB_EXECUTION je
                JOIN BATCH_JOB_INSTANCE ji ON ji.JOB_INSTANCE_ID = je.JOB_INSTANCE_ID AND ji.JOB_NAME = ?
                JOIN BATCH_JOB_EXECUTION_PARAMS pDate
                    ON pDate.JOB_EXECUTION_ID = je.JOB_EXECUTION_ID
                    AND pDate.PARAMETER_NAME = 'analysisDate' AND pDate.PARAMETER_VALUE = ?
                JOIN BATCH_JOB_EXECUTION_PARAMS pRule
                    ON pRule.JOB_EXECUTION_ID = je.JOB_EXECUTION_ID
                    AND pRule.PARAMETER_NAME = 'ruleVersion' AND pRule.PARAMETER_VALUE = ?
                """, Long.class, jobName, analysisDate.toString(), ruleVersion);
        deleteBatchInstances(instanceIds);
    }

    private void deleteBatchInstances(List<Long> instanceIds) {
        for (Long instanceId : instanceIds) {
            List<Long> executionIds = jdbcTemplate.queryForList(
                    "SELECT JOB_EXECUTION_ID FROM BATCH_JOB_EXECUTION WHERE JOB_INSTANCE_ID = ?", Long.class, instanceId);
            for (Long executionId : executionIds) {
                jdbcTemplate.update("DELETE FROM BATCH_STEP_EXECUTION_CONTEXT WHERE STEP_EXECUTION_ID IN "
                        + "(SELECT STEP_EXECUTION_ID FROM BATCH_STEP_EXECUTION WHERE JOB_EXECUTION_ID = ?)", executionId);
                jdbcTemplate.update("DELETE FROM BATCH_STEP_EXECUTION WHERE JOB_EXECUTION_ID = ?", executionId);
                jdbcTemplate.update("DELETE FROM BATCH_JOB_EXECUTION_CONTEXT WHERE JOB_EXECUTION_ID = ?", executionId);
                jdbcTemplate.update("DELETE FROM BATCH_JOB_EXECUTION_PARAMS WHERE JOB_EXECUTION_ID = ?", executionId);
                jdbcTemplate.update("DELETE FROM BATCH_JOB_EXECUTION WHERE JOB_EXECUTION_ID = ?", executionId);
            }
            jdbcTemplate.update("DELETE FROM BATCH_JOB_INSTANCE WHERE JOB_INSTANCE_ID = ?", instanceId);
        }
    }
}
