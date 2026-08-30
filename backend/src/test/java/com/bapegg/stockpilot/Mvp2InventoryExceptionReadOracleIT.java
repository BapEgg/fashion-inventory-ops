package com.bapegg.stockpilot;

import com.bapegg.stockpilot.analysis.AnalysisRunStatus;
import com.bapegg.stockpilot.analysis.SpAnalysisRun;
import com.bapegg.stockpilot.analysis.SpAnalysisRunRepository;
import com.bapegg.stockpilot.analysis.SpInventoryMetric;
import com.bapegg.stockpilot.analysis.SpInventoryMetricRepository;
import com.bapegg.stockpilot.batch.Mvp2AnalysisJobParameters;
import com.bapegg.stockpilot.demand.DemandAnalysisRules;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mockito.Mockito;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.launch.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import jakarta.persistence.EntityManagerFactory;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the MVP-2 inventory-exception read API (list + detail) against the official
 * {@code (2026-09-30, MVP-2-GS-V1, MVP-2)} Golden Scenario triple, per current-task.md section
 * 7.2. Read-only: this test never writes to {@code sp_inventory_metric}/candidate/scenario rows
 * itself, only (idempotently, like every other Golden Scenario IT) ensures the triple has been
 * completed through the production job before reading it. Skipped (not failed) when DB_URL is
 * not set.
 */
@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "DB_URL", matches = ".+")
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class Mvp2InventoryExceptionReadOracleIT {

    private static final LocalDate ANALYSIS_DATE = LocalDate.of(2026, 9, 30);
    private static final String INPUT_SNAPSHOT_VERSION = "MVP-2-GS-V1";
    private static final String RULE_VERSION = DemandAnalysisRules.RULE_VERSION;

    private static final String RECEIVER = "STORE-MVP2-RECEIVER-A";

    private static final String GS01 = "SKU-MVP2-GS01-STABLE";
    private static final String GS02 = "SKU-MVP2-GS02-EVENT";
    private static final String GS04 = "SKU-MVP2-GS04-OOS";
    private static final String GS05 = "SKU-MVP2-GS05-INBOUND";
    private static final String GS06 = "SKU-MVP2-GS06-ROUTE";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private tools.jackson.databind.ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JobOperator jobOperator;

    @Autowired
    @Qualifier("mvp2AnalysisJob")
    private Job mvp2AnalysisJob;

    @Autowired
    private SpAnalysisRunRepository analysisRunRepository;

    @Autowired
    private SpInventoryMetricRepository metricRepository;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @MockitoSpyBean
    private SpAnalysisRunRepository spiedAnalysisRunRepository;

    /**
     * Per Codex's re-review: the accepted handler unit test covers
     * {@code AnalysisApiExceptionHandler} in isolation, but nothing proved the widened
     * {@code assignableTypes} actually reaches {@link com.bapegg.stockpilot.analysis.InventoryExceptionController}
     * end to end. A {@code DataAccessException} thrown from the run lookup inside
     * {@code Mvp2InventoryExceptionQueryService.listExceptions} must come back as the same
     * catalog-backed {@code PERSISTENCE_UNAVAILABLE} ProblemDetail the analysis endpoints use,
     * not Spring's default error page. Scoped to one sentinel id via {@code doThrow(...).when(spy)}
     * so it never affects the golden-triple test method sharing this Spring context.
     */
    @Test
    void inventoryExceptionControllerDataAccessFailureIsClassifiedThroughTheSharedProblemDetailBoundary() throws Exception {
        long sentinelRunId = 987_654_321L;
        Mockito.doThrow(new DataAccessResourceFailureException("simulated connection loss"))
                .when(spiedAnalysisRunRepository).findById(sentinelRunId);

        Map<String, Object> problem = readMap(mockMvc.perform(get("/api/inventory-exceptions")
                        .param("analysisRunId", String.valueOf(sentinelRunId)))
                .andExpect(status().is(503))
                .andReturn());

        assertEquals("PERSISTENCE_UNAVAILABLE", problem.get("code"));
        assertEquals(Boolean.TRUE, problem.get("retryable"));
        assertEquals("/api/inventory-exceptions", problem.get("instance"));
    }

    @Test
    void listAndDetailExposeTheOfficialGoldenTripleEvidence() throws Exception {
        Long runId = ensureGoldenRunCompleted();

        Map<String, Long> receiverMetricIdBySku = new java.util.HashMap<>();
        for (SpInventoryMetric m : metricRepository.findByAnalysisRun_AnalysisRunId(runId)) {
            if (RECEIVER.equals(m.getInventorySnapshot().getStoreId())) {
                receiverMetricIdBySku.put(m.getInventorySnapshot().getSkuId(), m.getInventoryMetricId());
            }
        }

        assertListShowsGs01WithExecutableCandidate(runId);
        assertListFilterAndPaginationBehavior(runId);
        assertQueryCountStaysWithinCeiling(runId, receiverMetricIdBySku.get(GS01));
        assertDetailGs01(receiverMetricIdBySku.get(GS01));
        assertDetailGs02(receiverMetricIdBySku.get(GS02));
        assertDetailGs04(receiverMetricIdBySku.get(GS04));
        assertDetailGs05IsNotFound(receiverMetricIdBySku.get(GS05));
        assertDetailGs06(receiverMetricIdBySku.get(GS06));
        assertUnknownRunIdIsNotFound();
        assertMvp1RunIdIsRejectedAsValidationError();
        assertNonCompletedRunIsResultsNotReady();
        assertV15CatalogRowsExactMetadata();
    }

    private Long ensureGoldenRunCompleted() throws Exception {
        JobParameters batchParameters =
                new Mvp2AnalysisJobParameters(ANALYSIS_DATE, INPUT_SNAPSHOT_VERSION, RULE_VERSION).toJobParameters();
        try {
            JobExecution execution = jobOperator.start(mvp2AnalysisJob, batchParameters);
            assertEquals(BatchStatus.COMPLETED, execution.getStatus());
        } catch (JobInstanceAlreadyCompleteException alreadyComplete) {
            // Accepted: a previous run already completed this exact official triple.
        }
        SpAnalysisRun run = analysisRunRepository
                .findByAnalysisDateAndInputSnapshotVersionAndRuleVersion(ANALYSIS_DATE, INPUT_SNAPSHOT_VERSION, RULE_VERSION)
                .orElseThrow();
        assertEquals(AnalysisRunStatus.COMPLETED, run.getRunStatus());
        return run.getAnalysisRunId();
    }

    @SuppressWarnings("unchecked")
    private void assertListShowsGs01WithExecutableCandidate(Long runId) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/inventory-exceptions")
                        .param("analysisRunId", String.valueOf(runId))
                        .param("size", "100"))
                .andExpect(status().isOk())
                .andReturn();
        Map<String, Object> page = readMap(result);
        List<Map<String, Object>> items = (List<Map<String, Object>>) page.get("items");
        assertNotNull(items);

        Map<String, Object> gs01 = items.stream()
                .filter(i -> GS01.equals(i.get("skuId")) && RECEIVER.equals(i.get("storeId")))
                .findFirst().orElseThrow();
        assertEquals("STABLE_REPEAT", gs01.get("primaryDemandSignalType"));
        assertEquals("HIGH", gs01.get("demandConfidence"));
        assertEquals(Boolean.TRUE, gs01.get("hasExecutableCandidate"));
        assertTrue(((Number) gs01.get("executableCandidateCount")).intValue() >= 1);
    }

    @SuppressWarnings("unchecked")
    private void assertListFilterAndPaginationBehavior(Long runId) throws Exception {
        MvcResult firstResult = mockMvc.perform(get("/api/inventory-exceptions")
                        .param("analysisRunId", String.valueOf(runId))
                        .param("size", "1")
                        .param("page", "0"))
                .andExpect(status().isOk())
                .andReturn();
        Map<String, Object> firstPage = readMap(firstResult);
        assertEquals(1, ((Number) firstPage.get("size")).intValue());
        assertEquals(0, ((Number) firstPage.get("page")).intValue());
        assertEquals(Boolean.FALSE, firstPage.get("hasPrevious"));
        long total = ((Number) firstPage.get("totalElements")).longValue();
        assertTrue(total >= 2, "The golden run must expose at least GS-01/02/04/06 as size=1 page-stability evidence.");
        assertEquals(Boolean.TRUE, firstPage.get("hasNext"));
        List<Map<String, Object>> firstItems = (List<Map<String, Object>>) firstPage.get("items");
        assertEquals(1, firstItems.size());

        MvcResult secondResult = mockMvc.perform(get("/api/inventory-exceptions")
                        .param("analysisRunId", String.valueOf(runId))
                        .param("size", "1")
                        .param("page", "1"))
                .andExpect(status().isOk())
                .andReturn();
        Map<String, Object> secondPage = readMap(secondResult);
        assertEquals(Boolean.TRUE, secondPage.get("hasPrevious"));
        List<Map<String, Object>> secondItems = (List<Map<String, Object>>) secondPage.get("items");
        assertEquals(1, secondItems.size());
        assertFalse(firstItems.get(0).get("inventoryMetricId").equals(secondItems.get(0).get("inventoryMetricId")),
                "Consecutive size=1 pages of a fixed order must never repeat the same row.");

        // exceptionType filter narrows to exactly the requested type(s).
        Map<String, Object> stockoutOnly = readMap(mockMvc.perform(get("/api/inventory-exceptions")
                        .param("analysisRunId", String.valueOf(runId))
                        .param("exceptionType", "STOCKOUT_RISK")
                        .param("size", "100"))
                .andExpect(status().isOk()).andReturn());
        List<Map<String, Object>> stockoutItems = (List<Map<String, Object>>) stockoutOnly.get("items");
        assertFalse(stockoutItems.isEmpty());
        assertTrue(stockoutItems.stream().allMatch(i -> "STOCKOUT_RISK".equals(i.get("inventoryExceptionType"))));

        // skuId + storeId exact-match filters (AND'd together) narrow to exactly one row --
        // the golden run also carries an OVERSTOCK donor-side metric for the same SKU, so
        // skuId alone is not selective enough on its own.
        Map<String, Object> gs01Only = readMap(mockMvc.perform(get("/api/inventory-exceptions")
                        .param("analysisRunId", String.valueOf(runId))
                        .param("skuId", GS01)
                        .param("storeId", RECEIVER)
                        .param("size", "100"))
                .andExpect(status().isOk()).andReturn());
        List<Map<String, Object>> gs01Items = (List<Map<String, Object>>) gs01Only.get("items");
        assertEquals(1, gs01Items.size());
        assertEquals(GS01, gs01Items.get(0).get("skuId"));
        assertEquals(RECEIVER, gs01Items.get(0).get("storeId"));

        // hasExecutableCandidate=false excludes GS-01 (which has an executable candidate).
        Map<String, Object> noExecutable = readMap(mockMvc.perform(get("/api/inventory-exceptions")
                        .param("analysisRunId", String.valueOf(runId))
                        .param("hasExecutableCandidate", "false")
                        .param("size", "100"))
                .andExpect(status().isOk()).andReturn());
        List<Map<String, Object>> noExecutableItems = (List<Map<String, Object>>) noExecutable.get("items");
        assertTrue(noExecutableItems.stream().noneMatch(i -> GS01.equals(i.get("skuId"))));
        assertTrue(noExecutableItems.stream().allMatch(i -> Boolean.FALSE.equals(i.get("hasExecutableCandidate"))));

        // signal filter: GS-01 is STABLE_REPEAT.
        Map<String, Object> stableOnly = readMap(mockMvc.perform(get("/api/inventory-exceptions")
                        .param("analysisRunId", String.valueOf(runId))
                        .param("signal", "STABLE_REPEAT")
                        .param("size", "100"))
                .andExpect(status().isOk()).andReturn());
        List<Map<String, Object>> stableItems = (List<Map<String, Object>>) stableOnly.get("items");
        assertFalse(stableItems.isEmpty());
        assertTrue(stableItems.stream().allMatch(i -> "STABLE_REPEAT".equals(i.get("primaryDemandSignalType"))));
        assertTrue(stableItems.stream().anyMatch(i -> GS01.equals(i.get("skuId")) && RECEIVER.equals(i.get("storeId"))));

        // qualityFlag filter: GS-04's receiver carries OOS_CENSORED.
        Map<String, Object> oosCensoredOnly = readMap(mockMvc.perform(get("/api/inventory-exceptions")
                        .param("analysisRunId", String.valueOf(runId))
                        .param("qualityFlag", "OOS_CENSORED")
                        .param("size", "100"))
                .andExpect(status().isOk()).andReturn());
        List<Map<String, Object>> oosCensoredItems = (List<Map<String, Object>>) oosCensoredOnly.get("items");
        assertFalse(oosCensoredItems.isEmpty());
        assertTrue(oosCensoredItems.stream()
                .allMatch(i -> ((List<?>) i.get("qualityFlags")).contains("OOS_CENSORED")));
        assertTrue(oosCensoredItems.stream().anyMatch(i -> GS04.equals(i.get("skuId")) && RECEIVER.equals(i.get("storeId"))));

        // Repeatable same-kind filter is OR: severity=CRITICAL,HIGH must return the union, not
        // the (empty) intersection, and must exclude REVIEW/null-severity rows.
        Map<String, Object> criticalOrHigh = readMap(mockMvc.perform(get("/api/inventory-exceptions")
                        .param("analysisRunId", String.valueOf(runId))
                        .param("severity", "CRITICAL")
                        .param("severity", "HIGH")
                        .param("size", "100"))
                .andExpect(status().isOk()).andReturn());
        List<Map<String, Object>> criticalOrHighItems = (List<Map<String, Object>>) criticalOrHigh.get("items");
        assertFalse(criticalOrHighItems.isEmpty());
        assertTrue(criticalOrHighItems.stream()
                .allMatch(i -> "CRITICAL".equals(i.get("severity")) || "HIGH".equals(i.get("severity"))));
        assertTrue(criticalOrHighItems.stream().anyMatch(i -> "CRITICAL".equals(i.get("severity"))),
                "The repeated filter must retain the CRITICAL side of the union.");
        assertTrue(criticalOrHighItems.stream().anyMatch(i -> "HIGH".equals(i.get("severity"))),
                "The repeated filter must retain the HIGH side of the union.");

        // Different-kind filters are AND'd: exceptionType=OVERSTOCK plus the receiver's own
        // STOCKOUT_RISK-only exceptionType filter must exclude that receiver.
        Map<String, Object> overstockOnly = readMap(mockMvc.perform(get("/api/inventory-exceptions")
                        .param("analysisRunId", String.valueOf(runId))
                        .param("exceptionType", "OVERSTOCK")
                        .param("storeId", RECEIVER)
                        .param("size", "100"))
                .andExpect(status().isOk()).andReturn());
        List<Map<String, Object>> overstockReceiverItems = (List<Map<String, Object>>) overstockOnly.get("items");
        assertTrue(overstockReceiverItems.isEmpty(),
                "The receiver store never carries an OVERSTOCK exception in this golden fixture.");

        assertFixedOrderIsStableAcrossPaginationAndRepeatedCalls(runId);
        assertStatementCountDoesNotScaleWithPageSize(runId);
    }

    /**
     * Per Codex's re-review: page 0/1 previously only proved the two ids differed, not that the
     * fixed 6-key order is actually followed or that repeated calls return it identically. Walks
     * every size=1 page and confirms the concatenated id sequence exactly matches one unfiltered
     * size=100 call's order, then repeats that same size=100 call and confirms byte-for-byte
     * identical ordering.
     */
    @SuppressWarnings("unchecked")
    private void assertFixedOrderIsStableAcrossPaginationAndRepeatedCalls(Long runId) throws Exception {
        Map<String, Object> fullPage = readMap(mockMvc.perform(get("/api/inventory-exceptions")
                        .param("analysisRunId", String.valueOf(runId))
                        .param("size", "100"))
                .andExpect(status().isOk()).andReturn());
        List<Map<String, Object>> fullItems = (List<Map<String, Object>>) fullPage.get("items");
        List<Object> fullOrderIds = fullItems.stream().map(i -> i.get("inventoryMetricId")).toList();
        long total = ((Number) fullPage.get("totalElements")).longValue();
        assertEquals(total, fullOrderIds.size());

        Comparator<Map<String, Object>> fixedOrder = Comparator
                .comparingInt((Map<String, Object> i) -> severityRank((String) i.get("severity")))
                .thenComparingInt(i -> Boolean.TRUE.equals(i.get("hasExecutableCandidate")) ? 0 : 1)
                .thenComparingInt(i -> confidenceRank((String) i.get("demandConfidence")))
                .thenComparing(i -> decimalValue(i.get("expectedShortageQuantity")),
                        Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(i -> decimalValue(i.get("estimatedSalesImpact")),
                        Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(i -> (String) i.get("storeId"))
                .thenComparing(i -> (String) i.get("skuId"))
                .thenComparingLong(i -> ((Number) i.get("inventoryMetricId")).longValue());
        List<Object> expectedOrderIds = fullItems.stream().sorted(fixedOrder)
                .map(i -> i.get("inventoryMetricId"))
                .toList();
        assertEquals(expectedOrderIds, fullOrderIds,
                "The unfiltered response must follow all six fixed sort keys, not merely a stable order.");

        List<Object> walkedIds = new java.util.ArrayList<>();
        for (int page = 0; page < total; page++) {
            Map<String, Object> onePage = readMap(mockMvc.perform(get("/api/inventory-exceptions")
                            .param("analysisRunId", String.valueOf(runId))
                            .param("size", "1")
                            .param("page", String.valueOf(page)))
                    .andExpect(status().isOk()).andReturn());
            List<Map<String, Object>> pageItems = (List<Map<String, Object>>) onePage.get("items");
            assertEquals(1, pageItems.size());
            walkedIds.add(pageItems.get(0).get("inventoryMetricId"));
        }
        assertEquals(fullOrderIds, walkedIds, "Walking size=1 pages must reproduce the exact size=100 order.");

        Map<String, Object> repeatedFullPage = readMap(mockMvc.perform(get("/api/inventory-exceptions")
                        .param("analysisRunId", String.valueOf(runId))
                        .param("size", "100"))
                .andExpect(status().isOk()).andReturn());
        List<Map<String, Object>> repeatedItems = (List<Map<String, Object>>) repeatedFullPage.get("items");
        List<Object> repeatedOrderIds = repeatedItems.stream().map(i -> i.get("inventoryMetricId")).toList();
        assertEquals(fullOrderIds, repeatedOrderIds, "The fixed order must be identical across repeated calls.");
    }

    /**
     * Per current-task.md section 5: statement count must not grow with the number of rows
     * returned. Compares the real `prepareStatement` count of a size=1 request against a
     * size=100 request over the same unfiltered run -- both must be identical.
     */
    private void assertStatementCountDoesNotScaleWithPageSize(Long runId) throws Exception {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();

        statistics.clear();
        mockMvc.perform(get("/api/inventory-exceptions")
                        .param("analysisRunId", String.valueOf(runId))
                        .param("size", "1"))
                .andExpect(status().isOk());
        long sizeOneCount = statistics.getPrepareStatementCount();

        statistics.clear();
        mockMvc.perform(get("/api/inventory-exceptions")
                        .param("analysisRunId", String.valueOf(runId))
                        .param("size", "100"))
                .andExpect(status().isOk());
        long sizeHundredCount = statistics.getPrepareStatementCount();

        assertEquals(sizeOneCount, sizeHundredCount,
                "Statement count must be identical regardless of how many rows the page returns.");
    }

    /**
     * Per current-task.md section 5's query ceiling (list 6, detail 14) and the P1 finding that
     * flagged it as unmeasured -- GS-01 is the richest single receiver metric available (an
     * {@code ELIGIBLE} candidate with a route, scenarios and reasons), so its detail call
     * exercises every conditional bulk-fetch branch.
     */
    private void assertQueryCountStaysWithinCeiling(Long runId, Long gs01MetricId) throws Exception {
        assertNotNull(gs01MetricId, "GS-01 receiver metric must exist in the golden run.");
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();

        statistics.clear();
        mockMvc.perform(get("/api/inventory-exceptions")
                        .param("analysisRunId", String.valueOf(runId))
                        .param("size", "100"))
                .andExpect(status().isOk());
        long listStatementCount = statistics.getPrepareStatementCount();

        statistics.clear();
        mockMvc.perform(get("/api/inventory-exceptions/{id}", gs01MetricId))
                .andExpect(status().isOk());
        long detailStatementCount = statistics.getPrepareStatementCount();
        assertAll(
                () -> assertTrue(listStatementCount <= 6,
                        "Statement counts — list: " + listStatementCount + ", detail: " + detailStatementCount
                                + "; list ceiling is 6"),
                () -> assertTrue(detailStatementCount <= 14,
                        "Statement counts — list: " + listStatementCount + ", detail: " + detailStatementCount
                                + "; detail ceiling is 14"));
    }

    private void assertNonCompletedRunIsResultsNotReady() throws Exception {
        SpAnalysisRun runningRun = analysisRunRepository.save(
                new SpAnalysisRun(ANALYSIS_DATE.plusYears(1), RULE_VERSION, "MVP2-READ-NOT-READY-V1"));
        try {
            Map<String, Object> problem = readMap(mockMvc.perform(get("/api/inventory-exceptions")
                            .param("analysisRunId", String.valueOf(runningRun.getAnalysisRunId())))
                    .andExpect(status().isConflict())
                    .andReturn());
            assertEquals("ANALYSIS_RESULTS_NOT_READY", problem.get("code"));
        } finally {
            jdbcTemplate.update("DELETE FROM sp_analysis_run WHERE analysis_run_id = ?", runningRun.getAnalysisRunId());
        }
    }

    @SuppressWarnings("unchecked")
    private void assertDetailGs01(Long metricId) throws Exception {
        assertNotNull(metricId, "GS-01 receiver metric must exist in the golden run.");
        Map<String, Object> detail = readMap(mockMvc.perform(get("/api/inventory-exceptions/{id}", metricId))
                .andExpect(status().isOk()).andReturn());

        Map<String, Object> observationWindow = (Map<String, Object>) detail.get("observationWindow");
        List<Map<String, Object>> days = (List<Map<String, Object>>) observationWindow.get("days");
        assertEquals(28, days.size());

        List<Map<String, Object>> candidates = (List<Map<String, Object>>) detail.get("candidatesAsReceiver");
        Map<String, Object> candidate = candidates.stream()
                .filter(c -> "ELIGIBLE".equals(c.get("candidateStatus"))).findFirst().orElseThrow();
        List<Map<String, Object>> scenarios = (List<Map<String, Object>>) candidate.get("scenarios");
        assertEquals(4, scenarios.size());
        Map<String, Object> base = scenarios.stream()
                .filter(s -> "BASE".equals(s.get("scenarioType"))).findFirst().orElseThrow();
        assertEquals(11, ((Number) base.get("scenarioQuantity")).intValue());
    }

    @SuppressWarnings("unchecked")
    private void assertDetailGs02(Long metricId) throws Exception {
        assertNotNull(metricId, "GS-02 receiver metric must exist in the golden run.");
        Map<String, Object> detail = readMap(mockMvc.perform(get("/api/inventory-exceptions/{id}", metricId))
                .andExpect(status().isOk()).andReturn());

        List<Map<String, Object>> candidates = (List<Map<String, Object>>) detail.get("candidatesAsReceiver");
        Map<String, Object> candidate = candidates.stream()
                .filter(c -> "ELIGIBLE".equals(c.get("candidateStatus"))).findFirst().orElseThrow();
        List<Map<String, Object>> scenarios = (List<Map<String, Object>>) candidate.get("scenarios");
        Map<String, Object> base = scenarios.stream()
                .filter(s -> "BASE".equals(s.get("scenarioType"))).findFirst().orElseThrow();
        assertEquals(0, new BigDecimal("3.000000000000").compareTo(new BigDecimal(base.get("demandRate").toString())));
        assertEquals(20, ((Number) base.get("scenarioQuantity")).intValue());
    }

    @SuppressWarnings("unchecked")
    private void assertDetailGs04(Long metricId) throws Exception {
        assertNotNull(metricId, "GS-04 receiver metric must exist in the golden run.");
        Map<String, Object> detail = readMap(mockMvc.perform(get("/api/inventory-exceptions/{id}", metricId))
                .andExpect(status().isOk()).andReturn());

        Map<String, Object> metric = (Map<String, Object>) detail.get("metric");
        assertEquals("LOW", metric.get("demandConfidence"));
        assertEquals("REVIEW_REQUIRED", metric.get("inventoryExceptionType"));
        List<String> flags = (List<String>) metric.get("qualityFlags");
        assertTrue(flags.contains("OOS_CENSORED"));

        List<Map<String, Object>> days = (List<Map<String, Object>>)
                ((Map<String, Object>) detail.get("observationWindow")).get("days");
        assertTrue(days.stream().anyMatch(d -> Boolean.TRUE.equals(d.get("outOfStock"))));
    }

    /**
     * Per the 2026-08-28 confirmed contract amendment in current-task.md: GS-05's receiver metric
     * is {@code NORMAL} (confirmed inbound already resolves its shortage), and this read-only
     * exception endpoint returns the same {@code INVENTORY_EXCEPTION_NOT_FOUND} 404 for any
     * {@code NORMAL} metric, including this one -- its confirmed-inbound/{@code
     * INBOUND_ALREADY_COVERS} evidence remains owned by the Batch/Oracle calculation regression,
     * not this endpoint.
     */
    private void assertDetailGs05IsNotFound(Long metricId) throws Exception {
        assertNotNull(metricId, "GS-05 receiver metric must exist in the golden run.");
        Map<String, Object> problem = readMap(mockMvc.perform(get("/api/inventory-exceptions/{id}", metricId))
                .andExpect(status().isNotFound())
                .andReturn());
        assertEquals("INVENTORY_EXCEPTION_NOT_FOUND", problem.get("code"));
        assertEquals(Boolean.FALSE, problem.get("retryable"));
    }

    @SuppressWarnings("unchecked")
    private void assertDetailGs06(Long metricId) throws Exception {
        assertNotNull(metricId, "GS-06 receiver metric must exist in the golden run.");
        Map<String, Object> detail = readMap(mockMvc.perform(get("/api/inventory-exceptions/{id}", metricId))
                .andExpect(status().isOk()).andReturn());

        List<Map<String, Object>> candidates = (List<Map<String, Object>>) detail.get("candidatesAsReceiver");
        Map<String, Object> candidate = candidates.stream()
                .filter(c -> "REJECTED".equals(c.get("candidateStatus"))).findFirst().orElseThrow();
        List<Map<String, Object>> reasons = (List<Map<String, Object>>) candidate.get("rejectionReasons");
        assertEquals(2, reasons.size());
        assertEquals("OWNER_MISMATCH", reasons.get(0).get("reasonCode"));
        assertEquals("LEAD_TIME_TOO_LONG", reasons.get(1).get("reasonCode"));
        assertTrue(((Number) reasons.get(0).get("reasonOrder")).intValue() < ((Number) reasons.get(1).get("reasonOrder")).intValue());
    }

    private void assertUnknownRunIdIsNotFound() throws Exception {
        Map<String, Object> problem = readMap(mockMvc.perform(get("/api/inventory-exceptions")
                        .param("analysisRunId", "999999999"))
                .andExpect(status().isNotFound())
                .andReturn());
        assertEquals("ANALYSIS_NOT_FOUND", problem.get("code"));
    }

    private void assertMvp1RunIdIsRejectedAsValidationError() throws Exception {
        SpAnalysisRun mvp1Run = analysisRunRepository.findAll().stream()
                .filter(r -> "MVP-1".equals(r.getRuleVersion()))
                .findFirst().orElse(null);
        if (mvp1Run == null) {
            return;
        }
        Map<String, Object> problem = readMap(mockMvc.perform(get("/api/inventory-exceptions")
                        .param("analysisRunId", String.valueOf(mvp1Run.getAnalysisRunId())))
                .andExpect(status().isBadRequest())
                .andReturn());
        assertEquals("VALIDATION_ERROR", problem.get("code"));
    }

    private void assertV15CatalogRowsExactMetadata() {
        Map<String, Object> notReady = jdbcTemplate.queryForMap(
                "SELECT http_status, retryable_flag, title_ko, default_detail_ko FROM sp_error_catalog WHERE error_code = ?",
                "ANALYSIS_RESULTS_NOT_READY");
        assertEquals(409, ((Number) notReady.get("HTTP_STATUS")).intValue());
        assertEquals("Y", notReady.get("RETRYABLE_FLAG"));
        assertEquals("분석 결과 준비 중", notReady.get("TITLE_KO"));
        assertEquals("요청한 분석 실행이 아직 완료되지 않아 재고 예외 결과를 조회할 수 없습니다.",
                notReady.get("DEFAULT_DETAIL_KO"));

        Map<String, Object> notFound = jdbcTemplate.queryForMap(
                "SELECT http_status, retryable_flag, title_ko, default_detail_ko FROM sp_error_catalog WHERE error_code = ?",
                "INVENTORY_EXCEPTION_NOT_FOUND");
        assertEquals(404, ((Number) notFound.get("HTTP_STATUS")).intValue());
        assertEquals("N", notFound.get("RETRYABLE_FLAG"));
        assertEquals("재고 예외 없음", notFound.get("TITLE_KO"));
        assertEquals("지정한 inventoryMetricId에 해당하는 조회 가능한 재고 예외가 없습니다.",
                notFound.get("DEFAULT_DETAIL_KO"));
    }

    private static int severityRank(String severity) {
        return switch (severity == null ? "" : severity) {
            case "CRITICAL" -> 0;
            case "HIGH" -> 1;
            case "REVIEW" -> 2;
            default -> 3;
        };
    }

    private static int confidenceRank(String confidence) {
        return switch (confidence == null ? "" : confidence) {
            case "HIGH" -> 0;
            case "MEDIUM" -> 1;
            case "LOW" -> 2;
            case "NONE" -> 3;
            default -> 4;
        };
    }

    private static BigDecimal decimalValue(Object value) {
        return value == null ? null : new BigDecimal(value.toString());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readMap(MvcResult result) throws Exception {
        return objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
    }
}
