package com.bapegg.stockpilot.rebalance;

import com.bapegg.stockpilot.analysis.InventoryClassification;
import com.bapegg.stockpilot.analysis.InventoryPriority;
import com.bapegg.stockpilot.analysis.SpAnalysisRun;
import com.bapegg.stockpilot.analysis.SpAnalysisRunRepository;
import com.bapegg.stockpilot.analysis.SpInventoryMetric;
import com.bapegg.stockpilot.analysis.SpInventoryMetricRepository;
import com.bapegg.stockpilot.analysis.SpMetricQualityFlag;
import com.bapegg.stockpilot.analysis.SpMetricQualityFlagRepository;
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
import com.bapegg.stockpilot.inventory.SpInventorySnapshot;
import com.bapegg.stockpilot.inventory.SpInventorySnapshotRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the JPA persistence mapping for the four MVP-2 Phase 3 entities that had no Java
 * mapping before this round ({@link SpDemandEvent}, {@code SpMetricQualityFlag},
 * {@link SpCandidateReason}, {@link SpRebalanceScenario}) and {@link SpInventoryMetric}'s new
 * MVP-2 constructor, against the real Oracle instance. This test only proves the *mapping* and
 * the legacy-column projection formulas in {@code data-model.md}'s Phase 3 section -- it does not
 * exercise the Batch Job/Step, input adapter, calculation orchestration or atomic writer; those
 * boundaries have their own tests. Skipped (not failed) when DB_URL is not set.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "DB_URL", matches = ".+")
@Transactional
class Mvp2BatchEntityPersistenceMappingIT {

    private static final LocalDate ANALYSIS_DATE = LocalDate.parse("2026-08-25");
    private static final String RULE_VERSION = "MVP-2-EPMIT";
    private static final String INPUT_SNAPSHOT_VERSION = "MVP-2-EPMIT-V1";

    @Autowired
    private SpAnalysisRunRepository analysisRunRepository;

    @Autowired
    private SpInventoryMetricRepository metricRepository;

    @Autowired
    private SpInventorySnapshotRepository snapshotRepository;

    @Autowired
    private SpMetricQualityFlagRepository qualityFlagRepository;

    @Autowired
    private SpRebalanceRecommendationRepository recommendationRepository;

    @Autowired
    private SpCandidateReasonRepository candidateReasonRepository;

    @Autowired
    private SpRebalanceScenarioRepository scenarioRepository;

    @Autowired
    private SpDemandEventRepository demandEventRepository;

    @Autowired
    private SpStoreTransferRouteRepository routeRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void mvp2MetricConstructorMapsEveryV6ColumnAndTheLegacyProjection() {
        SpAnalysisRun run = createTestAnalysisRun();
        SpInventorySnapshot gangnamSnapshot = findSnapshot("STORE-GANGNAM");

        InventoryProjection projection = InventoryProjection.calculate(6, 1, 0, 0, 0, 0, 0);
        DemandObservationStatistics stats = new DemandObservationStatistics(
                20, 0, false, 0, 3,
                new BigDecimal("0.750000"), new BigDecimal("0.100000000000"),
                8, new BigDecimal("3.000000000000"), new BigDecimal("1.000000000000"),
                60L, new BigDecimal("5.000000000000"), false, null,
                5, false, 30L);
        DemandSignalClassification signal = new DemandSignalClassification(
                DemandSignalType.STABLE_REPEAT, DemandConfidence.HIGH, null, false);
        DemandRateCalculation rates = new DemandRateCalculation(
                List.of(new BigDecimal("1.000000000000"), new BigDecimal("2.000000000000"), new BigDecimal("3.000000000000")),
                new BigDecimal("1.000000000000"), new BigDecimal("2.000000000000"), new BigDecimal("3.000000000000"), false);
        InventoryExceptionClassification exception =
                new InventoryExceptionClassification(InventoryExceptionType.STOCKOUT_RISK, InventorySeverity.HIGH);

        SpInventoryMetric metric = metricRepository.save(new SpInventoryMetric(
                run, gangnamSnapshot, stats, signal, rates, projection, exception,
                12L, "MVP-2"));

        entityManager.flush();
        entityManager.clear();

        SpInventoryMetric reloaded = metricRepository.findById(metric.getInventoryMetricId()).orElseThrow();
        // Legacy columns: available_quantity uses currentAvailable (not projected-forward),
        // average_daily_sales is the simple observable-day mean (not the weekly-rate quantile),
        // coverage_days = currentAvailable / baseline BASE, classification/priority mirror the
        // new exception/severity (no REVIEW_REQUIRED mapping exercised by this fixture).
        assertEquals(5, reloaded.getAvailableQuantity());
        assertEquals(0, new BigDecimal("3.0000").compareTo(reloaded.getAverageDailySales()));
        assertEquals(0, new BigDecimal("2.50").compareTo(reloaded.getCoverageDays()));
        assertEquals(InventoryClassification.STOCKOUT_RISK, reloaded.getClassification());
        assertEquals(InventoryPriority.HIGH, reloaded.getPriority());

        // New V6 columns.
        assertEquals(20, reloaded.getObservableDayCount());
        assertEquals(3, reloaded.getActiveWeekCount());
        assertEquals(0, new BigDecimal("0.750000").compareTo(reloaded.getSalesDayRatio()));
        assertEquals(8, reloaded.getMaxDailySales());
        assertEquals(0, new BigDecimal("3.000000000000").compareTo(reloaded.getMedianDailySales()));
        assertEquals(0, new BigDecimal("1.000000000000").compareTo(reloaded.getMadDailySales()));
        assertEquals(5, reloaded.getMaxTransactionQuantity());
        assertEquals(DemandSignalType.STABLE_REPEAT, reloaded.getPrimaryDemandSignalType());
        assertEquals(DemandConfidence.HIGH, reloaded.getDemandConfidence());
        assertEquals(0, new BigDecimal("1.000000000000").compareTo(reloaded.getLowDemandRate()));
        assertEquals(0, new BigDecimal("2.000000000000").compareTo(reloaded.getBaseDemandRate()));
        assertEquals(0, new BigDecimal("3.000000000000").compareTo(reloaded.getHighDemandRate()));
        assertEquals(5, reloaded.getProjectedAvailable());
        assertEquals(12L, reloaded.getExpectedShortageQuantity().longValue());
        assertEquals(InventoryExceptionType.STOCKOUT_RISK, reloaded.getInventoryExceptionType());
        assertEquals(InventorySeverity.HIGH, reloaded.getSeverity());
        assertEquals("MVP-2", reloaded.getCalculationVersion());
    }

    @Test
    void reviewRequiredExceptionProjectsToLegacyNonActionableClassification() {
        SpAnalysisRun run = createTestAnalysisRun();
        SpInventorySnapshot gangnamSnapshot = findSnapshot("STORE-GANGNAM");
        InventoryProjection projection = InventoryProjection.calculate(6, 1, 0, 0, 0, 0, 0);
        DemandObservationStatistics stats = new DemandObservationStatistics(
                10, 0, false, 0, 1,
                new BigDecimal("0.100000"), null,
                null, null, null,
                0L, null, false, null,
                0, false, 20L);
        DemandSignalClassification signal = new DemandSignalClassification(
                DemandSignalType.INTERMITTENT, DemandConfidence.LOW, null, false);
        DemandRateCalculation rates = new DemandRateCalculation(List.of(), null, null, null, true);
        InventoryExceptionClassification exception =
                new InventoryExceptionClassification(InventoryExceptionType.REVIEW_REQUIRED, InventorySeverity.REVIEW);

        SpInventoryMetric metric = metricRepository.save(new SpInventoryMetric(
                run, gangnamSnapshot, stats, signal, rates, projection, exception,
                null, "MVP-2"));
        entityManager.flush();
        entityManager.clear();

        SpInventoryMetric reloaded = metricRepository.findById(metric.getInventoryMetricId()).orElseThrow();
        assertEquals(InventoryClassification.NON_ACTIONABLE, reloaded.getClassification());
        assertNull(reloaded.getPriority());
        assertNull(reloaded.getCoverageDays(), "No BASE rate is available, so legacy coverage_days must be null.");
        assertNull(reloaded.getExpectedShortageQuantity());
        assertEquals(InventoryExceptionType.REVIEW_REQUIRED, reloaded.getInventoryExceptionType());
        assertEquals(InventorySeverity.REVIEW, reloaded.getSeverity());
    }

    @Test
    void expectedShortageQuantityRoundTripsBeyondIntegerRangeWithoutNarrowingOverflow() {
        // A value past Integer.MAX_VALUE (2,147,483,647) but well within NUMBER(12,0)'s ~10^12
        // range -- proves the entity stores exactly what it is given, with no narrowing cast of
        // its own. The narrowing-overflow-safe computation itself is now Mvp2CalculationOrchestrator's
        // responsibility (pure tests), not this mapping constructor's.
        SpAnalysisRun run = createTestAnalysisRun();
        SpInventorySnapshot gangnamSnapshot = findSnapshot("STORE-GANGNAM");
        InventoryProjection projection = InventoryProjection.calculate(5, 0, 0, 0, 0, 0, 0);
        DemandObservationStatistics stats = new DemandObservationStatistics(
                20, 0, false, 0, 3,
                new BigDecimal("0.750000"), null,
                8, new BigDecimal("3.000000000000"), new BigDecimal("1.000000000000"),
                60L, null, false, null, 5, false, 30L);
        DemandSignalClassification signal =
                new DemandSignalClassification(DemandSignalType.STABLE_REPEAT, DemandConfidence.HIGH, null, false);
        DemandRateCalculation rates = new DemandRateCalculation(
                List.of(), new BigDecimal("100000.000000000000"), new BigDecimal("100000.000000000000"),
                new BigDecimal("100000.000000000000"), false);
        InventoryExceptionClassification exception =
                new InventoryExceptionClassification(InventoryExceptionType.STOCKOUT_RISK, InventorySeverity.CRITICAL);

        SpInventoryMetric metric = metricRepository.save(new SpInventoryMetric(
                run, gangnamSnapshot, stats, signal, rates, projection, exception,
                2_999_999_995L, "MVP-2"));
        entityManager.flush();
        entityManager.clear();

        SpInventoryMetric reloaded = metricRepository.findById(metric.getInventoryMetricId()).orElseThrow();
        assertEquals(2_999_999_995L, reloaded.getExpectedShortageQuantity().longValue());
    }

    @Test
    void metricQualityFlagsAllowMultipleRowsPerMetric() {
        SpAnalysisRun run = createTestAnalysisRun();
        SpInventoryMetric metric = saveMinimalMetric(run, "STORE-GANGNAM");

        qualityFlagRepository.save(new SpMetricQualityFlag(metric, MetricQualityFlag.OOS_CENSORED));
        qualityFlagRepository.save(new SpMetricQualityFlag(metric, MetricQualityFlag.STALE_INVENTORY));
        entityManager.flush();
        entityManager.clear();

        List<SpMetricQualityFlag> all = qualityFlagRepository.findAll().stream()
                .filter(f -> metric.getInventoryMetricId().equals(f.getInventoryMetric().getInventoryMetricId()))
                .toList();
        assertEquals(2, all.size());
        assertTrue(all.stream().anyMatch(f -> f.getFlagCode() == MetricQualityFlag.OOS_CENSORED));
        assertTrue(all.stream().anyMatch(f -> f.getFlagCode() == MetricQualityFlag.STALE_INVENTORY));
        assertNotNull(all.get(0).getCreatedAt());
    }

    @Test
    void mvp2CandidateFactorySupportsNullableRejectedQuantitiesAndReasons() {
        SpAnalysisRun run = createTestAnalysisRun();
        SpInventoryMetric receiverMetric = saveMinimalMetric(run, "STORE-GANGNAM");
        SpInventoryMetric donorMetric = saveMinimalMetric(run, "STORE-HONGDAE");

        SpRebalanceRecommendation rejected = recommendationRepository.save(SpRebalanceRecommendation.createMvp2Candidate(
                receiverMetric, donorMetric, null, CandidateStatus.REJECTED, 1, RecommendationMode.NONE,
                null, null, null, null, null, null));
        candidateReasonRepository.save(new SpCandidateReason(rejected, TransferCandidateRejectionReason.OWNER_MISMATCH, 1));
        candidateReasonRepository.save(new SpCandidateReason(rejected, TransferCandidateRejectionReason.ROUTE_NOT_ALLOWED, 2));

        entityManager.flush();
        entityManager.clear();

        SpRebalanceRecommendation reloaded = recommendationRepository.findById(rejected.getRecommendationId()).orElseThrow();
        assertEquals(CandidateStatus.REJECTED, reloaded.getCandidateStatus());
        assertEquals(RecommendationMode.NONE, reloaded.getRecommendationMode());
        assertNull(reloaded.getRecommendedQuantity());
        assertNull(reloaded.getRouteId());

        List<SpCandidateReason> reasons = candidateReasonRepository.findAll().stream()
                .filter(r -> reloaded.getRecommendationId().equals(r.getRecommendation().getRecommendationId()))
                .sorted((a, b) -> Integer.compare(a.getReasonOrder(), b.getReasonOrder()))
                .toList();
        assertEquals(2, reasons.size());
        assertEquals(TransferCandidateRejectionReason.OWNER_MISMATCH, reasons.get(0).getReasonCode());
        assertEquals(TransferCandidateRejectionReason.ROUTE_NOT_ALLOWED, reasons.get(1).getReasonCode());
    }

    @Test
    void mvp2ScenarioMapsEveryColumnAndDerivesTheInboundIncludedFlagFromConfirmedInboundOnly() {
        SpAnalysisRun run = createTestAnalysisRun();
        SpInventoryMetric receiverMetric = saveMinimalMetric(run, "STORE-GANGNAM");
        SpInventoryMetric donorMetric = saveMinimalMetric(run, "STORE-HONGDAE");
        Long routeId = createTestRoute("-SCEN1");
        SpRebalanceRecommendation eligible = recommendationRepository.save(SpRebalanceRecommendation.createMvp2Candidate(
                receiverMetric, donorMetric, routeId, CandidateStatus.ELIGIBLE, 3, RecommendationMode.RECOMMENDED,
                20, 30, 20, 5L, 40L, 95L));

        TransferScenarioResult result = new TransferScenarioResult(
                TransferScenarioType.BASE, new BigDecimal("2.000000000000"), 20L, 20L, true,
                5, 25, new BigDecimal("2.500000"), new BigDecimal("12.500000"), InventoryExceptionType.NORMAL,
                80, 60, new BigDecimal("40.000000"), new BigDecimal("30.000000"), InventoryExceptionType.NORMAL,
                1, LocalDate.of(2026, 8, 26),
                3, 0, 0,
                0, 0, 0,
                null);
        OffsetDateTime expectedExpectedArrivalAt =
                LocalDate.of(2026, 8, 26).atStartOfDay(ZoneOffset.of("+09:00")).toOffsetDateTime();

        SpRebalanceScenario scenario = scenarioRepository.save(new SpRebalanceScenario(eligible, result, 1));
        entityManager.flush();
        entityManager.clear();

        SpRebalanceScenario reloaded = scenarioRepository.findById(scenario.getScenarioId()).orElseThrow();
        assertEquals(TransferScenarioType.BASE, reloaded.getScenarioType());
        assertEquals(0, new BigDecimal("2.000000000000").compareTo(reloaded.getDemandRate()));
        assertEquals(20L, reloaded.getScenarioQuantity());
        assertEquals(1, reloaded.getPackageMultiple());
        assertEquals(5, reloaded.getReceiverBeforeAvailable());
        assertEquals(25, reloaded.getReceiverAfterAvailable());
        assertEquals(InventoryExceptionType.NORMAL, reloaded.getReceiverRiskCode());
        assertEquals(80, reloaded.getDonorBeforeAvailable());
        assertEquals(60, reloaded.getDonorAfterAvailable());
        assertEquals(InventoryExceptionType.NORMAL, reloaded.getDonorRiskCode());
        assertEquals(1, reloaded.getLeadTimeDays());
        // expected_arrival_at is derived from result.expectedArrivalDate() + 00:00 Asia/Seoul,
        // never a caller-supplied argument -- this proves the derivation, not a passed-through value.
        assertEquals(expectedExpectedArrivalAt.toInstant(), reloaded.getExpectedArrivalAt().toInstant());
        assertTrue(reloaded.isInboundIncluded(), "receiverInboundArrivingBeforeTransfer=3 must set the flag.");
        assertNull(reloaded.getWarningSummary());
        // candidate_version is derived from the parent recommendation (3), never a caller-supplied argument.
        assertEquals(eligible.getCandidateVersion(), reloaded.getCandidateVersion());
        assertEquals(3, reloaded.getCandidateVersion());

        List<SpRebalanceScenario> byRecommendation =
                scenarioRepository.findByRecommendation_RecommendationIdOrderByScenarioType(eligible.getRecommendationId());
        assertEquals(1, byRecommendation.size());
    }

    @Test
    void scenarioInboundIncludedFlagStaysNWhenOnlyOpenTransferOrDraftMovedQuantity() {
        SpAnalysisRun run = createTestAnalysisRun();
        SpInventoryMetric receiverMetric = saveMinimalMetric(run, "STORE-GANGNAM");
        SpInventoryMetric donorMetric = saveMinimalMetric(run, "STORE-HONGDAE");
        Long routeId = createTestRoute("-SCEN2");
        SpRebalanceRecommendation eligible = recommendationRepository.save(SpRebalanceRecommendation.createMvp2Candidate(
                receiverMetric, donorMetric, routeId, CandidateStatus.ELIGIBLE, 1, RecommendationMode.RECOMMENDED,
                20, 30, 20, 5L, 40L, 95L));

        TransferScenarioResult result = new TransferScenarioResult(
                TransferScenarioType.BASE, new BigDecimal("2.000000000000"), 20L, 20L, true,
                5, 25, null, null, InventoryExceptionType.NORMAL,
                80, 60, null, null, InventoryExceptionType.NORMAL,
                1, LocalDate.of(2026, 8, 26),
                0, 4, 2,
                0, 3, 6,
                null);

        SpRebalanceScenario scenario = scenarioRepository.save(new SpRebalanceScenario(eligible, result, 1));
        entityManager.flush();
        entityManager.clear();

        SpRebalanceScenario reloaded = scenarioRepository.findById(scenario.getScenarioId()).orElseThrow();
        assertFalse(reloaded.isInboundIncluded(),
                "Open-transfer/draft quantities alone (no confirmed inbound) must not set inbound_included_flag.");
        assertNull(reloaded.getReceiverBeforeCoverage());
        assertNull(reloaded.getDonorAfterCoverage());
    }

    @Test
    void demandEventRoundTripsAndBridgesToThePureDemandEventRecord() {
        SpDemandEvent event = demandEventRepository.save(new SpDemandEvent(
                "EVT-EPMIT-1", DemandEventType.PROMOTION, "STORE-GANGNAM", "SKU-CAP-BLACK-FREE",
                LocalDate.of(2026, 8, 20), LocalDate.of(2026, 8, 30),
                new BigDecimal("1.200000"), new BigDecimal("1.500000"), new BigDecimal("1.800000"),
                INPUT_SNAPSHOT_VERSION));
        entityManager.flush();
        entityManager.clear();

        SpDemandEvent reloaded = demandEventRepository
                .findByStoreIdAndSkuIdAndInputSnapshotVersion("STORE-GANGNAM", "SKU-CAP-BLACK-FREE", INPUT_SNAPSHOT_VERSION)
                .stream().findFirst().orElseThrow();
        assertEquals("EVT-EPMIT-1", reloaded.getEventCode());
        assertEquals(DemandEventType.PROMOTION, reloaded.getEventType());
        assertNotNull(reloaded.getCreatedAt());

        var pure = reloaded.toDemandEvent();
        assertEquals("EVT-EPMIT-1", pure.eventCode());
        assertTrue(pure.hasCompleteUplift());
        assertTrue(pure.matchesStoreAndSku("STORE-GANGNAM", "SKU-CAP-BLACK-FREE"));

        List<SpDemandEvent> byVersion = demandEventRepository.findByInputSnapshotVersion(INPUT_SNAPSHOT_VERSION);
        assertEquals(1, byVersion.size());
    }

    private SpInventoryMetric saveMinimalMetric(SpAnalysisRun run, String storeId) {
        SpInventorySnapshot snapshot = findSnapshot(storeId);
        InventoryProjection projection = InventoryProjection.calculate(6, 1, 0, 0, 0, 0, 0);
        DemandObservationStatistics stats = new DemandObservationStatistics(
                20, 0, false, 0, 3,
                new BigDecimal("0.750000"), null,
                8, new BigDecimal("3.000000000000"), new BigDecimal("1.000000000000"),
                60L, null, false, null, 5, false, 30L);
        DemandSignalClassification signal =
                new DemandSignalClassification(DemandSignalType.STABLE_REPEAT, DemandConfidence.HIGH, null, false);
        DemandRateCalculation rates = new DemandRateCalculation(
                List.of(), new BigDecimal("1.000000000000"), new BigDecimal("2.000000000000"),
                new BigDecimal("3.000000000000"), false);
        InventoryExceptionClassification exception =
                new InventoryExceptionClassification(InventoryExceptionType.NORMAL, null);
        return metricRepository.save(new SpInventoryMetric(
                run, snapshot, stats, signal, rates, projection, exception, 12L, "MVP-2"));
    }

    private Long createTestRoute(String suffix) {
        SpStoreTransferRoute route = routeRepository.save(new SpStoreTransferRoute(
                "STORE-HONGDAE", "STORE-GANGNAM", true, false, 1, 1, 1, 50, INPUT_SNAPSHOT_VERSION + suffix));
        return route.getRouteId();
    }

    private SpAnalysisRun createTestAnalysisRun() {
        SpAnalysisRun run = analysisRunRepository.save(
                new SpAnalysisRun(ANALYSIS_DATE, RULE_VERSION, INPUT_SNAPSHOT_VERSION));
        run.markCompleted();
        return analysisRunRepository.save(run);
    }

    private SpInventorySnapshot findSnapshot(String storeId) {
        return snapshotRepository.findBySnapshotDate(ANALYSIS_DATE).stream()
                .filter(s -> storeId.equals(s.getStoreId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No snapshot found for store " + storeId));
    }
}
