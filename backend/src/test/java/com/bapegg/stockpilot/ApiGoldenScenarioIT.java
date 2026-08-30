package com.bapegg.stockpilot;

import com.bapegg.stockpilot.analysis.AnalysisRunResponse;
import com.bapegg.stockpilot.analysis.InventoryAnalysisRules;
import com.bapegg.stockpilot.analysis.InventoryExceptionDetail;
import com.bapegg.stockpilot.analysis.InventoryExceptionSummary;
import com.bapegg.stockpilot.analysis.InventoryMetricCalculation;
import com.bapegg.stockpilot.analysis.SpAnalysisRun;
import com.bapegg.stockpilot.analysis.SpAnalysisRunRepository;
import com.bapegg.stockpilot.analysis.SpInventoryMetric;
import com.bapegg.stockpilot.analysis.SpInventoryMetricRepository;
import com.bapegg.stockpilot.explanation.ExplanationResponse;
import com.bapegg.stockpilot.inventory.SpInventorySnapshot;
import com.bapegg.stockpilot.inventory.SpInventorySnapshotRepository;
import com.bapegg.stockpilot.rebalance.DecisionStatus;
import com.bapegg.stockpilot.rebalance.RebalanceCalculation;
import com.bapegg.stockpilot.rebalance.RebalanceDecisionResponse;
import com.bapegg.stockpilot.rebalance.RebalanceSimulationResponse;
import com.bapegg.stockpilot.rebalance.SpRebalanceDecisionRepository;
import com.bapegg.stockpilot.rebalance.SpRebalanceRecommendation;
import com.bapegg.stockpilot.rebalance.SpRebalanceRecommendationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises the implementation-order-5 API surface against the real Oracle instance.
 * Skipped (not failed) when DB_URL is not set.
 * <p>
 * {@link #goldenScenarioWorksThroughTheApi()} is read-only against the real Golden
 * Scenario data (project.md section 4 / business-rules.md section 8: analyze, list,
 * detail, simulate) and safe to rerun forever, since the analysis run/metrics/
 * recommendation are naturally idempotent (established in
 * {@code InventoryAnalysisGoldenScenarioIT}).
 * <p>
 * {@link #decisionWorkflowApprovesWithinSimulationRange()} exercises
 * {@code POST /api/rebalancing-decisions} on its own test-owned analysis run,
 * metrics and recommendation (built directly via repositories, reusing the real,
 * immutable Golden Scenario inventory snapshots as evidence) instead of the shared
 * Golden Scenario recommendation. Decisions are terminal and not idempotent, so
 * reusing the real recommendation would either be skipped on a rerun or, worse,
 * require deleting a decision that could be a genuine prior audit record — deleting
 * or replacing real decision state is not acceptable test isolation. Using test-owned
 * data instead means the create/201 and repeat/409 assertions run unconditionally on
 * every execution without ever touching real state, and the test cleans its own data
 * up in a {@code finally} block so reruns never collide.
 * <p>
 * {@link #runAnalysisReportsAlreadyCompletedWhenDomainRunPredatesBatchMetadata()}
 * covers a narrower {@code POST /api/analyses} response-reporting regression on its
 * own never-before-used date, again cleaned up in a {@code finally} block.
 */
@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "DB_URL", matches = ".+")
class ApiGoldenScenarioIT {

    private static final String ANALYSIS_DATE = "2026-08-25";
    // Per the 2026-08-28 Codex finding: RebalanceSimulationService's tuple-less legacy path now
    // allowlists an *exact* InventoryAnalysisRules.RULE_VERSION match (not "anything not MVP-2"),
    // so this test can no longer use a "MVP-1-DECISION-IT" suffix to isolate its own run from the
    // real Golden Scenario one -- it isolates via a dedicated, never-otherwise-used analysisDate
    // (with its own freshly inserted snapshot rows) instead, keeping the rule version exactly
    // "MVP-1" like a real recommendation would have.
    private static final LocalDate DECISION_TEST_ANALYSIS_DATE = LocalDate.of(2026, 10, 31);
    // Same reasoning as DECISION_TEST_ANALYSIS_DATE above: RebalanceDecisionService now allowlists
    // an *exact* InventoryAnalysisRules.RULE_VERSION match too (current-task.md's 2026-08-28
    // approval/decision REST spec, section 1.2), so this test also needs its own dedicated date
    // and snapshot rows instead of a suffixed rule version, to keep testing decisionStatus
    // rejection rather than incidentally testing the rule-version guard instead.
    private static final LocalDate DECISION_STATUS_TEST_ANALYSIS_DATE = LocalDate.of(2026, 11, 10);
    private static final LocalDate RERUN_REPORTING_TEST_DATE = LocalDate.of(2026, 9, 15);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SpRebalanceDecisionRepository decisionRepository;

    @Autowired
    private SpAnalysisRunRepository analysisRunRepository;

    @Autowired
    private SpInventoryMetricRepository metricRepository;

    @Autowired
    private SpInventorySnapshotRepository snapshotRepository;

    @Autowired
    private SpRebalanceRecommendationRepository recommendationRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void goldenScenarioWorksThroughTheApi() throws Exception {
        mockMvc.perform(post("/api/analyses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"analysisDate\":\"" + ANALYSIS_DATE + "\"}"))
                .andExpect(status().isOk());

        List<InventoryExceptionSummary> exceptions = readList(
                mockMvc.perform(get("/api/inventory-exceptions").param("analysisDate", ANALYSIS_DATE))
                        .andExpect(status().isOk())
                        .andReturn(),
                new TypeReference<List<InventoryExceptionSummary>>() {
                });

        assertEquals(2, exceptions.size());
        InventoryExceptionSummary gangnam = findByStore(exceptions, "STORE-GANGNAM");
        InventoryExceptionSummary hongdae = findByStore(exceptions, "STORE-HONGDAE");
        assertEquals("STOCKOUT_RISK", gangnam.classification().name());
        assertEquals("HIGH", gangnam.priority().name());
        assertEquals(25, gangnam.recommendedQuantity());
        assertEquals("OVERSTOCK", hongdae.classification().name());

        Long recommendationId = gangnam.recommendationId();
        assertNotNull(recommendationId);

        InventoryExceptionDetail detail = readOne(
                mockMvc.perform(get("/api/inventory-exceptions/{id}", gangnam.inventoryMetricId()))
                        .andExpect(status().isOk())
                        .andReturn(),
                InventoryExceptionDetail.class);
        assertEquals(1, detail.recommendationsAsReceiver().size());
        assertEquals("STORE-HONGDAE", detail.recommendationsAsReceiver().get(0).counterpartStoreId());
        assertEquals(25, detail.recommendationsAsReceiver().get(0).recommendedQuantity());

        RebalanceSimulationResponse simulation = readOne(
                mockMvc.perform(post("/api/rebalancing-simulations")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"recommendationId\":" + recommendationId + ",\"requestedQuantity\":20}"))
                        .andExpect(status().isOk())
                        .andReturn(),
                RebalanceSimulationResponse.class);
        // Gangnam (receiver) available=5, Hongdae (donor) available=40; requestedQuantity=20.
        assertEquals(5, simulation.receiverBefore().availableQuantity());
        assertEquals(25, simulation.receiverAfter().availableQuantity());
        assertEquals(40, simulation.donorBefore().availableQuantity());
        assertEquals(20, simulation.donorAfter().availableQuantity());
    }

    /**
     * The AI boundary (business-rules.md section 7): with AI disabled (the real
     * {@code .env}'s {@code AI_ENABLED=false}, matching project.md's "core APIs work
     * with AI disabled" acceptance criterion), the endpoint reports an explicit
     * unavailable state rather than an error. Read-only against the real Golden
     * Scenario, like {@link #goldenScenarioWorksThroughTheApi()}.
     */
    @Test
    void explanationEndpointReportsAiDisabledForTheGoldenScenario() throws Exception {
        mockMvc.perform(post("/api/analyses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"analysisDate\":\"" + ANALYSIS_DATE + "\"}"))
                .andExpect(status().isOk());

        List<InventoryExceptionSummary> exceptions = readList(
                mockMvc.perform(get("/api/inventory-exceptions").param("analysisDate", ANALYSIS_DATE))
                        .andExpect(status().isOk())
                        .andReturn(),
                new TypeReference<List<InventoryExceptionSummary>>() {
                });
        InventoryExceptionSummary gangnam = findByStore(exceptions, "STORE-GANGNAM");

        ExplanationResponse response = readOne(
                mockMvc.perform(post("/api/inventory-exceptions/{id}/explanation", gangnam.inventoryMetricId()))
                        .andExpect(status().isOk())
                        .andReturn(),
                ExplanationResponse.class);
        assertFalse(response.available());
        assertEquals("AI_DISABLED", response.reason());
        assertNull(response.explanation());
    }

    @Test
    void getExceptionDetailRejectsNormalClassification() throws Exception {
        mockMvc.perform(post("/api/analyses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"analysisDate\":\"" + ANALYSIS_DATE + "\"}"))
                .andExpect(status().isOk());

        SpAnalysisRun run = analysisRunRepository
                .findByAnalysisDateAndRuleVersion(LocalDate.parse(ANALYSIS_DATE), InventoryAnalysisRules.RULE_VERSION)
                .orElseThrow(() -> new AssertionError("Expected analysis run to be persisted."));
        SpInventoryMetric seongsu = metricRepository.findByAnalysisRun_AnalysisRunId(run.getAnalysisRunId())
                .stream()
                .filter(m -> "STORE-SEONGSU".equals(m.getInventorySnapshot().getStoreId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected a Seongsu metric in the Golden Scenario."));
        assertEquals("NORMAL", seongsu.getClassification().name());

        // GET /api/inventory-exceptions/{id} must reject a NORMAL metric the same way
        // as an unknown id: it is not part of the exception list's resource space.
        mockMvc.perform(get("/api/inventory-exceptions/{id}", seongsu.getInventoryMetricId()))
                .andExpect(status().isNotFound());
    }

    @Test
    void decisionWorkflowApprovesWithinSimulationRange() throws Exception {
        LocalDate analysisDate = DECISION_TEST_ANALYSIS_DATE;
        int quantity = 20;

        deleteAnalysisRun(analysisDate, InventoryAnalysisRules.RULE_VERSION);
        insertSnapshot(analysisDate, "STORE-GANGNAM", "SKU-CAP-BLACK-FREE", 6, 1);
        insertSnapshot(analysisDate, "STORE-HONGDAE", "SKU-CAP-BLACK-FREE", 42, 2);
        try {
            SpInventorySnapshot gangnamSnapshot = findSnapshot(analysisDate, "STORE-GANGNAM");
            SpInventorySnapshot hongdaeSnapshot = findSnapshot(analysisDate, "STORE-HONGDAE");

            SpAnalysisRun testRun =
                    analysisRunRepository.save(new SpAnalysisRun(analysisDate, InventoryAnalysisRules.RULE_VERSION));
            testRun.markCompleted();
            testRun = analysisRunRepository.save(testRun);

            SpInventoryMetric receiverMetric = metricRepository.save(new SpInventoryMetric(
                    testRun, gangnamSnapshot, InventoryMetricCalculation.calculate(6, 1, 28)));
            SpInventoryMetric donorMetric = metricRepository.save(new SpInventoryMetric(
                    testRun, hongdaeSnapshot, InventoryMetricCalculation.calculate(42, 2, 4)));
            SpRebalanceRecommendation recommendation = recommendationRepository.save(new SpRebalanceRecommendation(
                    receiverMetric, donorMetric, new RebalanceCalculation(25, 30, 25)));
            Long recommendationId = recommendation.getRecommendationId();

            RebalanceSimulationResponse simulation = readOne(
                    mockMvc.perform(post("/api/rebalancing-simulations")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("{\"recommendationId\":" + recommendationId
                                            + ",\"requestedQuantity\":" + quantity + "}"))
                            .andExpect(status().isOk())
                            .andReturn(),
                    RebalanceSimulationResponse.class);
            assertEquals(5, simulation.receiverBefore().availableQuantity());
            assertEquals(5 + quantity, simulation.receiverAfter().availableQuantity());
            assertEquals(40, simulation.donorBefore().availableQuantity());
            assertEquals(40 - quantity, simulation.donorAfter().availableQuantity());

            // Approve the exact quantity just simulated, demonstrating the
            // simulate-then-decide workflow from business-rules.md section 6.
            RebalanceDecisionResponse decision = readOne(
                    mockMvc.perform(post("/api/rebalancing-decisions")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("{\"recommendationId\":" + recommendationId
                                            + ",\"decisionStatus\":\"APPROVED\""
                                            + ",\"selectedQuantity\":" + quantity
                                            + ",\"reason\":\"Approved at the simulated quantity\""
                                            + ",\"actorLabel\":\"integration-test\"}"))
                            .andExpect(status().isCreated())
                            .andReturn(),
                    RebalanceDecisionResponse.class);
            assertEquals("APPROVED", decision.decisionStatus().name());
            assertEquals(quantity, decision.selectedQuantity());

            // Terminal decision cannot be changed: a second decision attempt is rejected.
            mockMvc.perform(post("/api/rebalancing-decisions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"recommendationId\":" + recommendationId
                                    + ",\"decisionStatus\":\"REJECTED\""
                                    + ",\"selectedQuantity\":" + quantity
                                    + ",\"reason\":\"should not be accepted\""
                                    + ",\"actorLabel\":\"integration-test\"}"))
                    .andExpect(status().isConflict());
        } finally {
            deleteAnalysisRun(analysisDate, InventoryAnalysisRules.RULE_VERSION);
            jdbcTemplate.update("DELETE FROM sp_inventory_snapshot WHERE snapshot_date = ? AND store_id IN (?, ?)",
                    analysisDate, "STORE-GANGNAM", "STORE-HONGDAE");
        }
    }

    /**
     * Regression for a Codex review finding: widening {@link DecisionStatus} to all
     * five MVP-2 persistence states (so the JPA mapping can read {@code HELD}/
     * {@code EXPIRED}/{@code PENDING} rows a future approval service writes) must not
     * silently widen what this MVP-1-only REST contract accepts. Also confirms no
     * decision row is persisted for a rejected request.
     */
    @Test
    void decisionWorkflowRejectsNonMvp1DecisionStatuses() throws Exception {
        LocalDate analysisDate = DECISION_STATUS_TEST_ANALYSIS_DATE;

        deleteAnalysisRun(analysisDate, InventoryAnalysisRules.RULE_VERSION);
        insertSnapshot(analysisDate, "STORE-GANGNAM", "SKU-CAP-BLACK-FREE", 6, 1);
        insertSnapshot(analysisDate, "STORE-HONGDAE", "SKU-CAP-BLACK-FREE", 42, 2);
        try {
            SpInventorySnapshot gangnamSnapshot = findSnapshot(analysisDate, "STORE-GANGNAM");
            SpInventorySnapshot hongdaeSnapshot = findSnapshot(analysisDate, "STORE-HONGDAE");

            SpAnalysisRun testRun =
                    analysisRunRepository.save(new SpAnalysisRun(analysisDate, InventoryAnalysisRules.RULE_VERSION));
            testRun.markCompleted();
            testRun = analysisRunRepository.save(testRun);

            SpInventoryMetric receiverMetric = metricRepository.save(new SpInventoryMetric(
                    testRun, gangnamSnapshot, InventoryMetricCalculation.calculate(6, 1, 28)));
            SpInventoryMetric donorMetric = metricRepository.save(new SpInventoryMetric(
                    testRun, hongdaeSnapshot, InventoryMetricCalculation.calculate(42, 2, 4)));
            SpRebalanceRecommendation recommendation = recommendationRepository.save(new SpRebalanceRecommendation(
                    receiverMetric, donorMetric, new RebalanceCalculation(25, 30, 25)));
            Long recommendationId = recommendation.getRecommendationId();

            for (String rejectedStatus : new String[] {"PENDING", "HELD", "EXPIRED"}) {
                mockMvc.perform(post("/api/rebalancing-decisions")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"recommendationId\":" + recommendationId
                                        + ",\"decisionStatus\":\"" + rejectedStatus + "\""
                                        + ",\"selectedQuantity\":20"
                                        + ",\"reason\":\"should not be accepted\""
                                        + ",\"actorLabel\":\"integration-test\"}"))
                        .andExpect(status().isBadRequest());
            }

            assertFalse(decisionRepository.existsByRecommendation_RecommendationId(recommendationId),
                    "No decision should be persisted for a rejected decisionStatus.");
        } finally {
            deleteAnalysisRun(analysisDate, InventoryAnalysisRules.RULE_VERSION);
            jdbcTemplate.update("DELETE FROM sp_inventory_snapshot WHERE snapshot_date = ? AND store_id IN (?, ?)",
                    analysisDate, "STORE-GANGNAM", "STORE-HONGDAE");
        }
    }

    /**
     * Regression for a Codex review finding: {@code AnalysisRunService} used to derive
     * {@code alreadyCompleted} only from Spring Batch's own
     * {@code JobInstanceAlreadyCompleteException}. If the domain {@link SpAnalysisRun}
     * row already exists while Spring Batch's JobInstance metadata does not (e.g. after
     * a crash between the tasklet's commit and Spring Batch recording its own
     * completion), the Job still launches, completes as a tasklet no-op, and the old
     * code reported {@code alreadyCompleted: false} even though nothing new was
     * computed. Reproduces that exact state directly (not via the Job) on a
     * never-before-used date under the real rule version, since the API always uses
     * that fixed rule version — the real Golden Scenario date/decision is never touched.
     * <p>
     * This is also the only test here that actually launches
     * {@code inventoryAnalysisJob} (as a tasklet no-op, since the domain run already
     * exists), so unlike {@link #decisionWorkflowApprovesWithinSimulationRange()} it
     * must also clean up the {@code BATCH_JOB_INSTANCE}/{@code BATCH_JOB_EXECUTION}
     * row Spring Batch's own (now JDBC-backed, see {@code InventoryAnalysisJobConfig})
     * JobRepository persists for it, or a rerun would find that JobInstance already
     * completed via a *different* JobParameters key than the domain-run check expects.
     */
    @Test
    void runAnalysisReportsAlreadyCompletedWhenDomainRunPredatesBatchMetadata() throws Exception {
        deleteAnalysisRun(RERUN_REPORTING_TEST_DATE, InventoryAnalysisRules.RULE_VERSION);
        deleteBatchJobInstance(RERUN_REPORTING_TEST_DATE, InventoryAnalysisRules.RULE_VERSION);
        try {
            SpAnalysisRun preExisting = new SpAnalysisRun(RERUN_REPORTING_TEST_DATE, InventoryAnalysisRules.RULE_VERSION);
            preExisting.markCompleted();
            analysisRunRepository.save(preExisting);

            AnalysisRunResponse response = readOne(
                    mockMvc.perform(post("/api/analyses")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("{\"analysisDate\":\"" + RERUN_REPORTING_TEST_DATE + "\"}"))
                            .andExpect(status().isOk())
                            .andReturn(),
                    AnalysisRunResponse.class);

            assertEquals(RERUN_REPORTING_TEST_DATE, response.analysisDate());
            assertEquals("COMPLETED", response.status().name());
            assertTrue(response.alreadyCompleted(),
                    "Expected alreadyCompleted=true when the domain run predates Spring Batch's own metadata");
        } finally {
            deleteAnalysisRun(RERUN_REPORTING_TEST_DATE, InventoryAnalysisRules.RULE_VERSION);
            deleteBatchJobInstance(RERUN_REPORTING_TEST_DATE, InventoryAnalysisRules.RULE_VERSION);
        }
    }

    private void insertSnapshot(LocalDate snapshotDate, String storeId, String skuId, int onHand, int reserved) {
        jdbcTemplate.update(
                "INSERT INTO sp_inventory_snapshot (snapshot_date, store_id, sku_id, on_hand_quantity, "
                        + "reserved_quantity, source_type, input_snapshot_version) "
                        + "VALUES (?, ?, ?, ?, ?, 'SYNTHETIC', 'MVP-1-LEGACY')",
                snapshotDate, storeId, skuId, onHand, reserved);
    }

    private SpInventorySnapshot findSnapshot(LocalDate analysisDate, String storeId) {
        return snapshotRepository.findBySnapshotDate(analysisDate).stream()
                .filter(s -> storeId.equals(s.getStoreId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No snapshot found for store " + storeId));
    }

    /** Test-owned data only: callers pass a (date, ruleVersion) pair that never matches a real run. */
    private void deleteAnalysisRun(LocalDate analysisDate, String ruleVersion) {
        analysisRunRepository.findByAnalysisDateAndRuleVersion(analysisDate, ruleVersion)
                .ifPresent(run -> {
                    List<SpInventoryMetric> metrics = metricRepository.findByAnalysisRun_AnalysisRunId(run.getAnalysisRunId());
                    for (SpInventoryMetric metric : metrics) {
                        recommendationRepository.findByReceiverMetricIdOrDonorMetricId(metric.getInventoryMetricId())
                                .forEach(recommendation -> {
                                    decisionRepository.deleteAll(decisionRepository
                                            .findAllByRecommendation_RecommendationIdOrderByDecisionSequenceAsc(
                                                    recommendation.getRecommendationId()));
                                    recommendationRepository.delete(recommendation);
                                });
                    }
                    metricRepository.deleteAll(metrics);
                    analysisRunRepository.delete(run);
                });
    }

    /**
     * Test-owned Spring Batch metadata cleanup: deletes the {@code BATCH_JOB_INSTANCE}
     * (and dependent {@code BATCH_JOB_EXECUTION}/{@code _PARAMS}/{@code _CONTEXT} and
     * {@code BATCH_STEP_EXECUTION}/{@code _CONTEXT} rows) for {@code inventoryAnalysisJob}
     * launched with the given (date, ruleVersion), identified via its JobParameters
     * rather than the hashed JOB_KEY, and restricted to {@code JOB_NAME = 'inventoryAnalysisJob'}
     * so a different job that happened to share the same parameter values is never touched.
     * Never touches the real Golden Scenario JobInstance since it always uses a
     * different, never-before-used {@code analysisDate}.
     */
    private void deleteBatchJobInstance(LocalDate analysisDate, String ruleVersion) {
        List<Long> jobInstanceIds = jdbcTemplate.queryForList("""
                SELECT DISTINCT je.JOB_INSTANCE_ID
                FROM BATCH_JOB_EXECUTION je
                JOIN BATCH_JOB_INSTANCE ji
                    ON ji.JOB_INSTANCE_ID = je.JOB_INSTANCE_ID
                    AND ji.JOB_NAME = 'inventoryAnalysisJob'
                JOIN BATCH_JOB_EXECUTION_PARAMS pDate
                    ON pDate.JOB_EXECUTION_ID = je.JOB_EXECUTION_ID
                    AND pDate.PARAMETER_NAME = 'analysisDate' AND pDate.PARAMETER_VALUE = ?
                JOIN BATCH_JOB_EXECUTION_PARAMS pRule
                    ON pRule.JOB_EXECUTION_ID = je.JOB_EXECUTION_ID
                    AND pRule.PARAMETER_NAME = 'ruleVersion' AND pRule.PARAMETER_VALUE = ?
                """, Long.class, analysisDate.toString(), ruleVersion);

        for (Long jobInstanceId : jobInstanceIds) {
            jdbcTemplate.update("""
                    DELETE FROM BATCH_STEP_EXECUTION_CONTEXT WHERE STEP_EXECUTION_ID IN (
                        SELECT se.STEP_EXECUTION_ID FROM BATCH_STEP_EXECUTION se
                        JOIN BATCH_JOB_EXECUTION je ON je.JOB_EXECUTION_ID = se.JOB_EXECUTION_ID
                        WHERE je.JOB_INSTANCE_ID = ?)
                    """, jobInstanceId);
            jdbcTemplate.update("""
                    DELETE FROM BATCH_JOB_EXECUTION_CONTEXT WHERE JOB_EXECUTION_ID IN (
                        SELECT JOB_EXECUTION_ID FROM BATCH_JOB_EXECUTION WHERE JOB_INSTANCE_ID = ?)
                    """, jobInstanceId);
            jdbcTemplate.update("""
                    DELETE FROM BATCH_STEP_EXECUTION WHERE JOB_EXECUTION_ID IN (
                        SELECT JOB_EXECUTION_ID FROM BATCH_JOB_EXECUTION WHERE JOB_INSTANCE_ID = ?)
                    """, jobInstanceId);
            jdbcTemplate.update("""
                    DELETE FROM BATCH_JOB_EXECUTION_PARAMS WHERE JOB_EXECUTION_ID IN (
                        SELECT JOB_EXECUTION_ID FROM BATCH_JOB_EXECUTION WHERE JOB_INSTANCE_ID = ?)
                    """, jobInstanceId);
            jdbcTemplate.update("DELETE FROM BATCH_JOB_EXECUTION WHERE JOB_INSTANCE_ID = ?", jobInstanceId);
            jdbcTemplate.update("DELETE FROM BATCH_JOB_INSTANCE WHERE JOB_INSTANCE_ID = ?", jobInstanceId);
        }
    }

    private static InventoryExceptionSummary findByStore(List<InventoryExceptionSummary> exceptions, String storeId) {
        return exceptions.stream()
                .filter(e -> storeId.equals(e.storeId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No exception found for store " + storeId));
    }

    private <T> T readOne(MvcResult result, Class<T> type) throws Exception {
        return objectMapper.readValue(result.getResponse().getContentAsString(), type);
    }

    private <T> List<T> readList(MvcResult result, TypeReference<List<T>> type) throws Exception {
        return objectMapper.readValue(result.getResponse().getContentAsString(), type);
    }
}
