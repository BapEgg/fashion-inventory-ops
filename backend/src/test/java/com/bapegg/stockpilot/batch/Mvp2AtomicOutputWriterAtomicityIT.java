package com.bapegg.stockpilot.batch;

import com.bapegg.stockpilot.analysis.AnalysisRunStatus;
import com.bapegg.stockpilot.analysis.SpAnalysisRun;
import com.bapegg.stockpilot.analysis.SpAnalysisRunRepository;
import com.bapegg.stockpilot.analysis.SpInventoryMetricRepository;
import com.bapegg.stockpilot.demand.DemandConfidence;
import com.bapegg.stockpilot.demand.DemandObservationStatistics;
import com.bapegg.stockpilot.demand.DemandRateCalculation;
import com.bapegg.stockpilot.demand.DemandSignalClassification;
import com.bapegg.stockpilot.demand.DemandSignalType;
import com.bapegg.stockpilot.demand.InventoryExceptionClassification;
import com.bapegg.stockpilot.demand.InventoryExceptionType;
import com.bapegg.stockpilot.demand.InventoryProjection;
import com.bapegg.stockpilot.demand.InventorySeverity;
import com.bapegg.stockpilot.demand.MetricQualityFlag;
import com.bapegg.stockpilot.demand.TransferCandidateRejectionReason;
import com.bapegg.stockpilot.demand.TransferScenarioResult;
import com.bapegg.stockpilot.demand.TransferScenarioType;
import com.bapegg.stockpilot.rebalance.CandidateStatus;
import com.bapegg.stockpilot.rebalance.RecommendationMode;
import com.bapegg.stockpilot.rebalance.SpRebalanceRecommendationRepository;
import com.bapegg.stockpilot.rebalance.SpStoreTransferRoute;
import com.bapegg.stockpilot.rebalance.SpStoreTransferRouteRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifies {@link Mvp2AtomicOutputWriter#writeAndComplete}'s all-or-nothing guarantee, per
 * current-task.md section 4's closing sentence: a failure anywhere rolls back metric through the
 * completion transition together. Forces the failure at the recommendation
 * {@code saveAllAndFlush} call -- i.e. strictly after the metric/quality-flag flush already
 * succeeded -- via a {@link MockitoSpyBean} on the real {@link SpRebalanceRecommendationRepository}
 * bean, then verifies every one of the five written entity types is back to zero for this run and
 * only the run itself ends up {@code FAILED} (replicating what {@link Mvp2AnalysisExecutor} does
 * on a caught {@code RuntimeException}, without needing a full input-adapter fixture).
 * <p>
 * Deliberately NOT {@code @Transactional} at the class level, for the same reason as
 * {@code ApprovalTransactionAtomicityIT}: {@code writeAndComplete}'s own {@code @Transactional}
 * must be the outermost boundary for the call under test so its rollback is real and immediate,
 * and the {@code REQUIRES_NEW} claim/markFailed transactions must commit independently regardless.
 * Skipped (not failed) when DB_URL is not set.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "DB_URL", matches = ".+")
class Mvp2AtomicOutputWriterAtomicityIT {

    private static final LocalDate ANALYSIS_DATE = LocalDate.of(2026, 8, 28);
    private static final String INPUT_SNAPSHOT_VERSION = "MVP2-ATOMIC-IT-V1";
    private static final String RULE_VERSION = "MVP2-ATOMIC-IT";
    // Real seeded catalog store/SKU combination (sp_inventory_snapshot's FK_SP_INV_PRODUCT
    // requires a genuine sp_store/sp_sku/sp_store_sku row, so a synthetic id here is rejected).
    private static final String RECEIVER = "STORE-GANGNAM";
    private static final String DONOR = "STORE-HONGDAE";
    private static final String SKU = "SKU-CAP-BLACK-FREE";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Mvp2RunLifecycleService lifecycleService;

    @Autowired
    private Mvp2AtomicOutputWriter outputWriter;

    @Autowired
    private SpAnalysisRunRepository analysisRunRepository;

    @Autowired
    private SpInventoryMetricRepository metricRepository;

    @Autowired
    private SpStoreTransferRouteRepository routeRepository;

    @MockitoSpyBean
    private SpRebalanceRecommendationRepository recommendationRepository;

    @Test
    void aRecommendationSaveFailureAfterTheMetricAndFlagFlushRollsBackEverythingAndOnlyFailsTheRun() {
        Long routeId = null;
        Long runId = null;
        try {
            insertSnapshot(RECEIVER);
            insertSnapshot(DONOR);
            SpStoreTransferRoute route = routeRepository.save(
                    new SpStoreTransferRoute(DONOR, RECEIVER, true, false, 1, 1, 1, 50, INPUT_SNAPSHOT_VERSION));
            routeId = route.getRouteId();

            Mvp2RunClaim claim = lifecycleService.claim(ANALYSIS_DATE, INPUT_SNAPSHOT_VERSION, RULE_VERSION);
            runId = claim.runId();

            Mockito.doThrow(new DataIntegrityViolationException("simulated mid-write failure"))
                    .when(recommendationRepository)
                    .saveAllAndFlush(Mockito.any());

            Mvp2CalculationResult result = buildResult(routeId);
            Long finalRunId = runId;
            RuntimeException writeFailure = assertThrows(RuntimeException.class,
                    () -> outputWriter.writeAndComplete(finalRunId, result));

            lifecycleService.markFailed(runId);

            assertEquals(0, metricRepository.countByAnalysisRun_AnalysisRunId(runId));
            assertEquals(0L, countForRun(
                    "SELECT COUNT(*) FROM sp_metric_quality_flag f "
                            + "JOIN sp_inventory_metric m ON f.inventory_metric_id = m.inventory_metric_id "
                            + "WHERE m.analysis_run_id = ?", runId));
            assertEquals(0L, countForRun(
                    "SELECT COUNT(*) FROM sp_rebalance_recommendation r "
                            + "JOIN sp_inventory_metric m ON r.receiver_metric_id = m.inventory_metric_id "
                            + "WHERE m.analysis_run_id = ?", runId));
            assertEquals(0L, countForRun(
                    "SELECT COUNT(*) FROM sp_candidate_reason cr "
                            + "JOIN sp_rebalance_recommendation r ON cr.recommendation_id = r.recommendation_id "
                            + "JOIN sp_inventory_metric m ON r.receiver_metric_id = m.inventory_metric_id "
                            + "WHERE m.analysis_run_id = ?", runId));
            assertEquals(0L, countForRun(
                    "SELECT COUNT(*) FROM sp_rebalance_scenario s "
                            + "JOIN sp_rebalance_recommendation r ON s.recommendation_id = r.recommendation_id "
                            + "JOIN sp_inventory_metric m ON r.receiver_metric_id = m.inventory_metric_id "
                            + "WHERE m.analysis_run_id = ?", runId));

            SpAnalysisRun run = analysisRunRepository.findById(runId).orElseThrow();
            assertEquals(AnalysisRunStatus.FAILED, run.getRunStatus());
            assertEquals(0, writeFailure.getSuppressed().length);
        } finally {
            cleanup(runId, routeId);
        }
    }

    private Mvp2CalculationResult buildResult(Long routeId) {
        InventoryProjection receiverProjection = InventoryProjection.calculate(6, 1, 0, 0, 0, 0, 0);
        DemandObservationStatistics receiverStats = new DemandObservationStatistics(
                20, 0, false, 0, 3,
                new BigDecimal("0.750000"), new BigDecimal("0.100000000000"),
                8, new BigDecimal("3.000000000000"), new BigDecimal("1.000000000000"),
                60L, new BigDecimal("5.000000000000"), false, null,
                5, false, 30L);
        DemandSignalClassification receiverSignal =
                new DemandSignalClassification(DemandSignalType.STABLE_REPEAT, DemandConfidence.HIGH, null, false);
        DemandRateCalculation receiverRates = new DemandRateCalculation(
                List.of(new BigDecimal("1.000000000000"), new BigDecimal("2.000000000000"), new BigDecimal("3.000000000000")),
                new BigDecimal("1.000000000000"), new BigDecimal("2.000000000000"), new BigDecimal("3.000000000000"), false);
        InventoryExceptionClassification receiverException =
                new InventoryExceptionClassification(InventoryExceptionType.STOCKOUT_RISK, InventorySeverity.HIGH);
        Mvp2MetricResult receiverMetric = new Mvp2MetricResult(
                RECEIVER, SKU, receiverStats, receiverSignal, receiverRates, receiverProjection, 1,
                receiverException, 12L, Set.of(MetricQualityFlag.OOS_CENSORED), RULE_VERSION);

        InventoryProjection donorProjection = InventoryProjection.calculate(42, 2, 0, 0, 0, 0, 0);
        DemandObservationStatistics donorStats = new DemandObservationStatistics(
                20, 0, false, 0, 3,
                new BigDecimal("0.750000"), null,
                8, new BigDecimal("3.000000000000"), new BigDecimal("1.000000000000"),
                60L, null, false, null, 5, false, 30L);
        DemandSignalClassification donorSignal =
                new DemandSignalClassification(DemandSignalType.STABLE_REPEAT, DemandConfidence.HIGH, null, false);
        DemandRateCalculation donorRates = new DemandRateCalculation(
                List.of(), new BigDecimal("1.000000000000"), new BigDecimal("2.000000000000"),
                new BigDecimal("3.000000000000"), false);
        InventoryExceptionClassification donorException =
                new InventoryExceptionClassification(InventoryExceptionType.NORMAL, null);
        Mvp2MetricResult donorMetric = new Mvp2MetricResult(
                DONOR, SKU, donorStats, donorSignal, donorRates, donorProjection, 7,
                donorException, 0L, Set.of(), RULE_VERSION);

        TransferScenarioResult scenario = new TransferScenarioResult(
                TransferScenarioType.BASE, new BigDecimal("2.000000000000"), 20L, 20L, true,
                5, 25, new BigDecimal("2.500000"), new BigDecimal("12.500000"), InventoryExceptionType.NORMAL,
                80, 60, new BigDecimal("40.000000"), new BigDecimal("30.000000"), InventoryExceptionType.NORMAL,
                1, LocalDate.of(2026, 8, 30),
                3, 0, 0,
                0, 0, 0,
                null);
        Mvp2CandidateResult eligibleCandidate = new Mvp2CandidateResult(
                RECEIVER, DONOR, SKU, routeId, CandidateStatus.ELIGIBLE, 1, RecommendationMode.RECOMMENDED,
                20, 30, 20, 5L, 40L, 95L, List.of(), 1, List.of(scenario));
        Mvp2CandidateResult rejectedCandidate = new Mvp2CandidateResult(
                RECEIVER, DONOR, SKU, null, CandidateStatus.REJECTED, 1, RecommendationMode.NONE,
                null, null, null, null, null, null,
                List.of(TransferCandidateRejectionReason.OWNER_MISMATCH), 1, List.of());

        return new Mvp2CalculationResult(
                ANALYSIS_DATE, INPUT_SNAPSHOT_VERSION,
                List.of(receiverMetric, donorMetric),
                List.of(eligibleCandidate, rejectedCandidate),
                Map.of(
                        new Mvp2StoreSkuKey(RECEIVER, SKU), receiverMetric,
                        new Mvp2StoreSkuKey(DONOR, SKU), donorMetric),
                Map.of(new Mvp2StoreSkuKey(RECEIVER, SKU), List.of(eligibleCandidate, rejectedCandidate)));
    }

    private void insertSnapshot(String storeId) {
        jdbcTemplate.update(
                "INSERT INTO sp_inventory_snapshot (snapshot_date, store_id, sku_id, on_hand_quantity, "
                        + "reserved_quantity, source_type, input_snapshot_version) "
                        + "VALUES (?, ?, ?, ?, ?, 'SYNTHETIC', ?)",
                ANALYSIS_DATE, storeId, SKU, 10, 0, INPUT_SNAPSHOT_VERSION);
    }

    private long countForRun(String sql, Long runId) {
        Long count = jdbcTemplate.queryForObject(sql, Long.class, runId);
        return count == null ? -1 : count;
    }

    private void cleanup(Long runId, Long routeId) {
        if (runId != null) {
            analysisRunRepository.deleteById(runId);
        }
        if (routeId != null) {
            routeRepository.deleteById(routeId);
        }
        jdbcTemplate.update("DELETE FROM sp_inventory_snapshot WHERE input_snapshot_version = ?", INPUT_SNAPSHOT_VERSION);
    }
}
