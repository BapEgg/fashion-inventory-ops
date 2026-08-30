package com.bapegg.stockpilot.approval;

import com.bapegg.stockpilot.analysis.InventoryAnalysisRules;
import com.bapegg.stockpilot.analysis.InventoryMetricCalculation;
import com.bapegg.stockpilot.analysis.SpAnalysisRun;
import com.bapegg.stockpilot.analysis.SpAnalysisRunRepository;
import com.bapegg.stockpilot.analysis.SpInventoryMetric;
import com.bapegg.stockpilot.analysis.SpInventoryMetricRepository;
import com.bapegg.stockpilot.inventory.SpInventorySnapshot;
import com.bapegg.stockpilot.inventory.SpInventorySnapshotRepository;
import com.bapegg.stockpilot.rebalance.DecisionStatus;
import com.bapegg.stockpilot.rebalance.DemandEventType;
import com.bapegg.stockpilot.rebalance.RebalanceCalculation;
import com.bapegg.stockpilot.rebalance.SpApprovalBasisRepository;
import com.bapegg.stockpilot.rebalance.SpDemandEvent;
import com.bapegg.stockpilot.rebalance.SpDemandEventRepository;
import com.bapegg.stockpilot.rebalance.SpRebalanceRecommendation;
import com.bapegg.stockpilot.rebalance.SpRebalanceRecommendationRepository;
import com.bapegg.stockpilot.rebalance.SpStoreSkuPolicy;
import com.bapegg.stockpilot.rebalance.SpStoreSkuPolicyRepository;
import com.bapegg.stockpilot.rebalance.SpStoreTransferRoute;
import com.bapegg.stockpilot.rebalance.SpStoreTransferRouteRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the current-basis parity fix in {@link CurrentApprovalBasisLoader}, per
 * current-task.md section 1 and {@code knowledge/business-rules.md} section 10's shared
 * current-basis contract: a missing store-SKU policy row falls back to
 * {@code DemandAnalysisRules} defaults instead of {@code STALE_RECOMMENDATION}, and the
 * representative-event-driven effective receiver BASE rate (not the raw baseline) is what both
 * the {@code MANUAL} preview and a real approval's saved basis actually use. Skipped (not
 * failed) when DB_URL is not set.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "DB_URL", matches = ".+")
@Transactional
class CurrentApprovalBasisLoaderParityIT {

    private static final LocalDate ANALYSIS_DATE = LocalDate.of(2026, 12, 1);
    private static final String RECEIVER_STORE_ID = "STORE-GANGNAM";
    private static final String DONOR_STORE_ID = "STORE-HONGDAE";
    private static final String SKU_ID = "SKU-CAP-BLACK-FREE";

    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private SpAnalysisRunRepository analysisRunRepository;
    @Autowired
    private SpInventorySnapshotRepository snapshotRepository;
    @Autowired
    private SpInventoryMetricRepository metricRepository;
    @Autowired
    private SpRebalanceRecommendationRepository recommendationRepository;
    @Autowired
    private SpStoreTransferRouteRepository routeRepository;
    @Autowired
    private SpStoreSkuPolicyRepository policyRepository;
    @Autowired
    private SpDemandEventRepository demandEventRepository;
    @Autowired
    private SpApprovalBasisRepository approvalBasisRepository;
    @Autowired
    private ApprovalTransactionFacade facade;
    @Autowired
    private ManualQuantityTestExecutor manualExecutor;
    @Autowired
    private EntityManager entityManager;

    @Test
    void missingPolicyRowFallsBackToApprovedDefaultsInsteadOfStale() {
        Fixture fixture = setUpFixture("-NOPOLICY", false);

        ManualQuantityTestResult result = manualExecutor.test(new ManualQuantityTestCommand(
                fixture.recommendationId(), fixture.analysisRunId(), fixture.inputSnapshotVersion(),
                fixture.ruleVersion(), 1, 5));

        // Default target coverage 7 + lead time 4 = 11 days at baseline rate 1, plus default
        // display minimum 1 = 12; projectedReceiverBeforeDemand is 8 (available 9, reserved 1)
        // for this fixture's snapshot -- need = 12 - 8 = 4.
        assertEquals(4, result.recommendedBaseQuantity());
        assertTrue(result.feasible());
    }

    @Test
    void outOfOrderEventsStillPickTheEarliestStartDateThenEventCodeAsRepresentative() {
        Fixture fixture = setUpFixture("-EVTORDER", true);
        // Inserted in reverse (startDate, eventCode) order: the later/bigger-uplift event first.
        saveEvent("Z-LATER-BIGGER", fixture.inputSnapshotVersion(), ANALYSIS_DATE.plusDays(6), ANALYSIS_DATE.plusDays(10),
                new BigDecimal("5.000000"), new BigDecimal("5.000000"), new BigDecimal("5.000000"));
        saveEvent("A-EARLIER-SMALLER", fixture.inputSnapshotVersion(), ANALYSIS_DATE.plusDays(5), ANALYSIS_DATE.plusDays(12),
                new BigDecimal("2.000000"), new BigDecimal("2.000000"), new BigDecimal("2.000000"));
        markReceiverSignalKnownEvent(fixture);

        ManualQuantityTestResult result = manualExecutor.test(new ManualQuantityTestCommand(
                fixture.recommendationId(), fixture.analysisRunId(), fixture.inputSnapshotVersion(),
                fixture.ruleVersion(), 1, 5));

        // The earlier-starting event (uplift factor 2.0, effective rate 2.0) must win over the
        // later one (factor 5.0) even though it was inserted second.
        assertEquals(16, result.recommendedBaseQuantity(),
                "ceil(2.0 * 11) + displayMin 2 - projectedBefore 8 = 22 + 2 - 8 = 16.");
    }

    @Test
    void knownEventOverlappingTheRouteWindowUpliftsBaseForBothManualAndRealApproval() {
        Fixture fixture = setUpFixture("-UPLIFT", true);
        saveEvent("UPLIFT-EVT", fixture.inputSnapshotVersion(), ANALYSIS_DATE.plusDays(5), ANALYSIS_DATE.plusDays(12),
                new BigDecimal("2.000000"), new BigDecimal("2.000000"), new BigDecimal("2.000000"));
        markReceiverSignalKnownEvent(fixture);

        ManualQuantityTestResult preview = manualExecutor.test(new ManualQuantityTestCommand(
                fixture.recommendationId(), fixture.analysisRunId(), fixture.inputSnapshotVersion(),
                fixture.ruleVersion(), 1, 5));
        assertEquals(16, preview.recommendedBaseQuantity());
        assertTrue(preview.feasible());

        facade.execute(new ApprovalTransactionCommand(
                fixture.recommendationId(), fixture.analysisRunId(), fixture.inputSnapshotVersion(),
                fixture.ruleVersion(), 1, DecisionStatus.APPROVED, 16, false,
                "MANUAL_OVERRIDE", "current-basis parity IT", "it"), newKey());

        var savedBasis = approvalBasisRepository.findAll().stream()
                .filter(b -> fixture.recommendationId().equals(b.getDecision().getRecommendation().getRecommendationId()))
                .findFirst().orElseThrow();
        assertEquals(16, savedBasis.getRecommendedBaseQuantity(),
                "The real approval's saved basis must use the same uplifted BASE the preview used.");
    }

    @Test
    void anEventNotOverlappingTheRouteWindowLeavesBaselineBaseUnchanged() {
        Fixture fixture = setUpFixture("-NOOVERLAP", true);
        // Relevant (overlaps the 28-day observation window) but ends well before the route's
        // arrival date (analysisDate + 4 = Dec 5), so it must not uplift anything.
        saveEvent("PAST-EVT", fixture.inputSnapshotVersion(), ANALYSIS_DATE.minusDays(10), ANALYSIS_DATE.minusDays(5),
                new BigDecimal("3.000000"), new BigDecimal("3.000000"), new BigDecimal("3.000000"));
        markReceiverSignalKnownEvent(fixture);

        ManualQuantityTestResult result = manualExecutor.test(new ManualQuantityTestCommand(
                fixture.recommendationId(), fixture.analysisRunId(), fixture.inputSnapshotVersion(),
                fixture.ruleVersion(), 1, 5));

        assertEquals(5, result.recommendedBaseQuantity(), "Same baseline-only value as no event at all.");
    }

    @Test
    void donorProtectionIgnoresReceiverEventUpliftEntirely() {
        Fixture withoutEvent = setUpFixture("-DONORBASE1", true);
        long donorTransferableWithoutEvent = manualExecutor.test(new ManualQuantityTestCommand(
                withoutEvent.recommendationId(), withoutEvent.analysisRunId(), withoutEvent.inputSnapshotVersion(),
                withoutEvent.ruleVersion(), 1, 5)).donorTransferableQuantity();

        Fixture withEvent = setUpFixture("-DONORBASE2", true);
        saveEvent("DONOR-UNAFFECTED-EVT", withEvent.inputSnapshotVersion(), ANALYSIS_DATE.plusDays(5), ANALYSIS_DATE.plusDays(12),
                new BigDecimal("2.000000"), new BigDecimal("2.000000"), new BigDecimal("2.000000"));
        markReceiverSignalKnownEvent(withEvent);
        long donorTransferableWithEvent = manualExecutor.test(new ManualQuantityTestCommand(
                withEvent.recommendationId(), withEvent.analysisRunId(), withEvent.inputSnapshotVersion(),
                withEvent.ruleVersion(), 1, 5)).donorTransferableQuantity();

        assertEquals(donorTransferableWithoutEvent, donorTransferableWithEvent);
    }

    private void saveEvent(String eventCode, String inputSnapshotVersion, LocalDate start, LocalDate end,
            BigDecimal upliftLow, BigDecimal upliftBase, BigDecimal upliftHigh) {
        demandEventRepository.save(new SpDemandEvent(
                eventCode, DemandEventType.PROMOTION, RECEIVER_STORE_ID, SKU_ID, start, end,
                upliftLow, upliftBase, upliftHigh, inputSnapshotVersion));
        entityManager.flush();
    }

    /**
     * The legacy {@link InventoryMetricCalculation}-based constructor this fixture uses has no
     * way to set {@code primaryDemandSignalType}; a real Batch-written MVP-2 row always has one,
     * so setting it via raw JDBC (mirroring the established owner-mismatch-corruption pattern in
     * {@link ManualQuantityTestExecutorIT}) is the realistic per-test shape, not a workaround for
     * a real gap. Clears the persistence context afterward so the loader's later fetch re-reads
     * the real row instead of the stale cached Java object.
     */
    private void markReceiverSignalKnownEvent(Fixture fixture) {
        jdbcTemplate.update(
                "UPDATE sp_inventory_metric SET primary_demand_signal_type = 'KNOWN_EVENT' "
                        + "WHERE analysis_run_id = (SELECT analysis_run_id FROM sp_analysis_run WHERE analysis_run_id = ?) "
                        + "AND inventory_snapshot_id IN ("
                        + "  SELECT inventory_snapshot_id FROM sp_inventory_snapshot "
                        + "  WHERE store_id = ? AND sku_id = ? AND input_snapshot_version = ?)",
                fixture.analysisRunId(), RECEIVER_STORE_ID, SKU_ID, fixture.inputSnapshotVersion());
        entityManager.clear();
    }

    /**
     * Receiver available=9/reserved=1 (projectedBefore=8), donor available=40/reserved=0, both
     * baseline rates=1, route lead time 4/min 1/pkg 1/max 30, receiver policy displayMin=2/
     * targetCoverage=7 (when {@code withPolicy}) -- baseline recommendedBaseQuantity=5
     * (ceil(1*11)+2-8). {@code projectedBefore=8} is deliberately exactly
     * {@code ceil(upliftedRate 2.0 * leadTimeDays 4) = 8}, so the uplifted scenarios (effective
     * rate 2.0, need up to 16) stay candidate-eligible too -- section 7's own lead-time check
     * uses this same effective rate, and a smaller {@code projectedBefore} would make the
     * uplifted candidate itself {@code LEAD_TIME_TOO_LONG} ineligible, not just change its
     * quantity. Donor pool and route max are large enough that neither ever caps these needs.
     */
    private Fixture setUpFixture(String suffix, boolean withPolicy) {
        String ruleVersion = InventoryAnalysisRules.RULE_VERSION + "-CABLPIT" + suffix;
        String inputSnapshotVersion = "MVP-2-CABLPIT" + suffix;

        jdbcTemplate.update(
                "INSERT INTO sp_inventory_snapshot (snapshot_date, store_id, sku_id, on_hand_quantity, "
                        + "reserved_quantity, source_type, input_snapshot_version) VALUES (?, ?, ?, 9, 1, 'SYNTHETIC', ?)",
                ANALYSIS_DATE, RECEIVER_STORE_ID, SKU_ID, inputSnapshotVersion);
        jdbcTemplate.update(
                "INSERT INTO sp_inventory_snapshot (snapshot_date, store_id, sku_id, on_hand_quantity, "
                        + "reserved_quantity, source_type, input_snapshot_version) VALUES (?, ?, ?, 40, 0, 'SYNTHETIC', ?)",
                ANALYSIS_DATE, DONOR_STORE_ID, SKU_ID, inputSnapshotVersion);
        SpInventorySnapshot receiverSnapshot = findSnapshot(inputSnapshotVersion, RECEIVER_STORE_ID);
        SpInventorySnapshot donorSnapshot = findSnapshot(inputSnapshotVersion, DONOR_STORE_ID);

        SpAnalysisRun analysisRun =
                analysisRunRepository.save(new SpAnalysisRun(ANALYSIS_DATE, ruleVersion, inputSnapshotVersion));
        analysisRun.markCompleted();
        analysisRun = analysisRunRepository.save(analysisRun);

        SpInventoryMetric receiverMetric = metricRepository.save(new SpInventoryMetric(
                analysisRun, receiverSnapshot, InventoryMetricCalculation.calculate(9, 1, 28)));
        receiverMetric.applyDemandRates(BigDecimal.ONE, BigDecimal.ONE);
        receiverMetric = metricRepository.save(receiverMetric);

        SpInventoryMetric donorMetric = metricRepository.save(new SpInventoryMetric(
                analysisRun, donorSnapshot, InventoryMetricCalculation.calculate(40, 0, 4)));
        donorMetric.applyDemandRates(BigDecimal.ONE, BigDecimal.ONE);
        donorMetric = metricRepository.save(donorMetric);

        SpRebalanceRecommendation recommendation = recommendationRepository.save(new SpRebalanceRecommendation(
                receiverMetric, donorMetric, new RebalanceCalculation(8, 8, 8)));

        SpStoreTransferRoute route = routeRepository.save(new SpStoreTransferRoute(
                DONOR_STORE_ID, RECEIVER_STORE_ID, true, false, 4, 1, 1, 30, inputSnapshotVersion));
        recommendation.assignRoute(route.getRouteId());
        recommendation = recommendationRepository.save(recommendation);

        if (withPolicy) {
            policyRepository.save(new SpStoreSkuPolicy(RECEIVER_STORE_ID, SKU_ID, 2, 0, 1000, 7, 0, inputSnapshotVersion));
            policyRepository.save(new SpStoreSkuPolicy(DONOR_STORE_ID, SKU_ID, 0, 0, 1000, 0, 0, inputSnapshotVersion));
        }

        entityManager.flush();

        return new Fixture(recommendation.getRecommendationId(), analysisRun.getAnalysisRunId(),
                inputSnapshotVersion, ruleVersion);
    }

    private SpInventorySnapshot findSnapshot(String inputSnapshotVersion, String storeId) {
        return snapshotRepository.findBySnapshotDate(ANALYSIS_DATE).stream()
                .filter(s -> storeId.equals(s.getStoreId()) && inputSnapshotVersion.equals(s.getInputSnapshotVersion()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected a fixture snapshot for " + storeId));
    }

    private static String newKey() {
        return "CABLP-IT-KEY-" + UUID.randomUUID();
    }

    private record Fixture(Long recommendationId, Long analysisRunId, String inputSnapshotVersion, String ruleVersion) {
    }
}
