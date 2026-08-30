package com.bapegg.stockpilot.approval;

import com.bapegg.stockpilot.analysis.InventoryAnalysisRules;
import com.bapegg.stockpilot.analysis.InventoryMetricCalculation;
import com.bapegg.stockpilot.analysis.SpAnalysisRun;
import com.bapegg.stockpilot.analysis.SpAnalysisRunRepository;
import com.bapegg.stockpilot.analysis.SpInventoryMetric;
import com.bapegg.stockpilot.analysis.SpInventoryMetricRepository;
import com.bapegg.stockpilot.demand.ManualQuantityViolation;
import com.bapegg.stockpilot.inventory.SpInventorySnapshot;
import com.bapegg.stockpilot.inventory.SpInventorySnapshotRepository;
import com.bapegg.stockpilot.rebalance.DecisionStatus;
import com.bapegg.stockpilot.rebalance.RebalanceCalculation;
import com.bapegg.stockpilot.rebalance.SpApprovalBasisRepository;
import com.bapegg.stockpilot.rebalance.SpRebalanceDecisionRepository;
import com.bapegg.stockpilot.rebalance.SpRebalanceRecommendation;
import com.bapegg.stockpilot.rebalance.SpRebalanceRecommendationRepository;
import com.bapegg.stockpilot.rebalance.SpStoreSkuPolicy;
import com.bapegg.stockpilot.rebalance.SpStoreSkuPolicyRepository;
import com.bapegg.stockpilot.rebalance.SpStoreTransferRoute;
import com.bapegg.stockpilot.rebalance.SpStoreTransferRouteRepository;
import com.bapegg.stockpilot.rebalance.SpTransferDraftRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies {@link ManualQuantityTestExecutor}'s side-effect-free preview behavior against the
 * real Oracle instance, per {@code knowledge/business-rules.md} section 10's `MANUAL`
 * quantity-test contract: a current result that reflects an already-approved sibling
 * recommendation's active draft against the same donor, stale/terminal rejection, a normal
 * (non-exception) infeasible result for an ineligible candidate, and zero rows written under
 * every outcome. The real cross-transaction donor-lock-timeout scenario is covered in
 * {@link ApprovalTransactionConcurrencyIT}, which already holds the infrastructure for a
 * genuinely separate, still-open transaction. Skipped (not failed) when DB_URL is not set.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "DB_URL", matches = ".+")
@Transactional
class ManualQuantityTestExecutorIT {

    private static final LocalDate ANALYSIS_DATE = LocalDate.of(2026, 12, 1);
    private static final String RECEIVER_STORE_ID = "STORE-GANGNAM";
    private static final String RECEIVER_STORE_ID_B = "STORE-SEONGSU";
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
    private SpRebalanceDecisionRepository decisionRepository;

    @Autowired
    private SpApprovalBasisRepository approvalBasisRepository;

    @Autowired
    private SpTransferDraftRepository transferDraftRepository;

    @Autowired
    private ApprovalTransactionFacade facade;

    @Autowired
    private ManualQuantityTestExecutor manualExecutor;

    @Autowired
    private EntityManager entityManager;

    @Test
    void currentResultReflectsAnAlreadyApprovedSiblingDraftAgainstTheSameDonor() {
        SharedDonorFixture fixture = setUpSharedDonorFixture("-ACTDRAFT");

        facade.execute(approvedCommand(fixture.recommendationAId(), fixture, 8), newKey());

        ManualQuantityTestResult result = manualExecutor.test(new ManualQuantityTestCommand(
                fixture.recommendationBId(), fixture.analysisRunId(), fixture.inputSnapshotVersion(),
                fixture.ruleVersion(), 1, 2));

        assertEquals(2, result.donorTransferableQuantity(), "10 on-hand minus the sibling's already-approved 8.");
        assertEquals(2, result.recommendedBaseQuantity());
        assertTrue(result.feasible());
        assertNotNull(result.projection());
        assertTrue(result.approvalRevalidationRequired());
        assertRowCountsUnchanged(fixture.recommendationBId());
    }

    @Test
    void terminalRecommendationRejectsAManualTestAndWritesNoRow() {
        Fixture fixture = setUpFixture("-TERM");
        facade.execute(approvedCommand(fixture.recommendationId(), fixture.analysisRunId(),
                fixture.inputSnapshotVersion(), fixture.ruleVersion(), 8), newKey());
        // The approval above legitimately wrote one decision+basis+draft row; the manual test
        // below must add none on top of that, not leave zero rows.
        int decisionCountBefore = decisionRepository
                .findAllByRecommendation_RecommendationIdOrderByDecisionSequenceAsc(fixture.recommendationId())
                .size();

        ApprovalTransactionException exception = assertThrowsApproval(() -> manualExecutor.test(
                new ManualQuantityTestCommand(fixture.recommendationId(), fixture.analysisRunId(),
                        fixture.inputSnapshotVersion(), fixture.ruleVersion(), 1, 5)));

        assertEquals(ApprovalErrorCode.DECISION_ALREADY_TERMINAL, exception.code());
        assertEquals(decisionCountBefore, decisionRepository
                .findAllByRecommendation_RecommendationIdOrderByDecisionSequenceAsc(fixture.recommendationId())
                .size());
    }

    @Test
    void staleRuleVersionMismatchRejectsAManualTestAndWritesNoRow() {
        Fixture fixture = setUpFixture("-STALE");

        ApprovalTransactionException exception = assertThrowsApproval(() -> manualExecutor.test(
                new ManualQuantityTestCommand(fixture.recommendationId(), fixture.analysisRunId(),
                        fixture.inputSnapshotVersion(), "WRONG-RULE-VERSION", 1, 5)));

        assertEquals(ApprovalErrorCode.STALE_RECOMMENDATION, exception.code());
        assertRowCountsUnchanged(fixture.recommendationId());
    }

    @Test
    void wrongAnalysisRunIdRejectsAManualTestAndWritesNoRow() {
        Fixture fixture = setUpFixture("-WAR");
        InventorySnapshotState receiverBefore = captureInventorySnapshotState(RECEIVER_STORE_ID, fixture.inputSnapshotVersion());
        InventorySnapshotState donorBefore = captureInventorySnapshotState(DONOR_STORE_ID, fixture.inputSnapshotVersion());

        ApprovalTransactionException exception = assertThrowsApproval(() -> manualExecutor.test(
                new ManualQuantityTestCommand(fixture.recommendationId(), fixture.analysisRunId() + 999_999L,
                        fixture.inputSnapshotVersion(), fixture.ruleVersion(), 1, 5)));

        assertEquals(ApprovalErrorCode.STALE_RECOMMENDATION, exception.code());
        assertRowCountsUnchanged(fixture.recommendationId());
        assertInventorySnapshotsUnchanged(fixture.inputSnapshotVersion(), receiverBefore, donorBefore);
    }

    @Test
    void wrongInputSnapshotVersionRejectsAManualTestAndWritesNoRow() {
        Fixture fixture = setUpFixture("-WSV");
        InventorySnapshotState receiverBefore = captureInventorySnapshotState(RECEIVER_STORE_ID, fixture.inputSnapshotVersion());
        InventorySnapshotState donorBefore = captureInventorySnapshotState(DONOR_STORE_ID, fixture.inputSnapshotVersion());

        ApprovalTransactionException exception = assertThrowsApproval(() -> manualExecutor.test(
                new ManualQuantityTestCommand(fixture.recommendationId(), fixture.analysisRunId(),
                        "WRONG-INPUT-SNAPSHOT-VERSION", fixture.ruleVersion(), 1, 5)));

        assertEquals(ApprovalErrorCode.STALE_RECOMMENDATION, exception.code());
        assertRowCountsUnchanged(fixture.recommendationId());
        assertInventorySnapshotsUnchanged(fixture.inputSnapshotVersion(), receiverBefore, donorBefore);
    }

    @Test
    void nullDonorBaseRateDoesNotBlockApprovalOrManualPreviewButNullsOnlyDonorCoverage() {
        // V6 allows a null donor BASE rate, and accepted approval calculation never reads it
        // (only donor HIGH); it exists solely for the manual preview's own donor coverage-days
        // display. The common loader must not turn this schema-legal input into a new stale
        // rejection for either path -- per current-task.md finding 2.
        Fixture fixture = setUpCustomFixture("-NULLBASE", null, null);

        ManualQuantityTestResult preview = manualExecutor.test(new ManualQuantityTestCommand(
                fixture.recommendationId(), fixture.analysisRunId(), fixture.inputSnapshotVersion(),
                fixture.ruleVersion(), 1, 8));

        assertTrue(preview.feasible());
        assertNotNull(preview.projection());
        assertNull(preview.projection().donorBeforeCoverageDays());
        assertNull(preview.projection().donorAfterCoverageDays());
        assertNotNull(preview.projection().receiverBeforeCoverageDays());
        assertNotNull(preview.projection().receiverAfterCoverageDays());

        assertDoesNotThrow(() -> facade.execute(approvedCommand(fixture.recommendationId(), fixture.analysisRunId(),
                fixture.inputSnapshotVersion(), fixture.ruleVersion(), 8), newKey()));
    }

    @Test
    void wrongRouteDonorRejectsAManualTestAndWritesNoRow() {
        Fixture fixture = setUpCustomFixture("-WRD", "STORE-JAMSIL");

        ApprovalTransactionException exception = assertThrowsApproval(() -> manualExecutor.test(
                new ManualQuantityTestCommand(fixture.recommendationId(), fixture.analysisRunId(),
                        fixture.inputSnapshotVersion(), fixture.ruleVersion(), 1, 5)));

        assertEquals(ApprovalErrorCode.STALE_RECOMMENDATION, exception.code());
        assertRowCountsUnchanged(fixture.recommendationId());
    }

    @Test
    void candidateIneligibleCandidateReturnsAnInfeasibleResultRatherThanAnException() {
        Fixture fixture = setUpFixture("-INELIG");
        // Corrupt the donor's own SP_STORE row's owner code away from the receiver's shared
        // default so section 7's owner-mismatch rejection fires, without touching any migration.
        jdbcTemplate.update("UPDATE sp_store SET inventory_owner_code = ? WHERE store_id = ?",
                "OWNER-MANUAL-IT-MISMATCH", DONOR_STORE_ID);

        ManualQuantityTestResult result = manualExecutor.test(new ManualQuantityTestCommand(
                fixture.recommendationId(), fixture.analysisRunId(), fixture.inputSnapshotVersion(),
                fixture.ruleVersion(), 1, 8));

        assertFalse(result.feasible());
        assertEquals(List.of(ManualQuantityViolation.CANDIDATE_INELIGIBLE), result.violations());
        assertEquals(0, result.maximumFeasibleQuantity());
        assertRowCountsUnchanged(fixture.recommendationId());
    }

    private void assertRowCountsUnchanged(Long recommendationId) {
        assertTrue(decisionRepository
                .findAllByRecommendation_RecommendationIdOrderByDecisionSequenceAsc(recommendationId)
                .isEmpty());
        assertTrue(approvalBasisRepository.findAll().stream()
                .noneMatch(b -> recommendationId.equals(b.getDecision().getRecommendation().getRecommendationId())));
        assertTrue(transferDraftRepository.findAll().stream()
                .noneMatch(d -> recommendationId.equals(d.getDecision().getRecommendation().getRecommendationId())));
    }

    /**
     * Reads the fixture's inventory snapshot row via raw JDBC (never the JPA/Hibernate
     * persistence context, whose first-level cache could mask an actual DB write within this
     * same {@code @Transactional} test method) so a before/after comparison proves the real
     * persisted row -- id, quantities and version -- is untouched by a rejected manual test.
     */
    private InventorySnapshotState captureInventorySnapshotState(String storeId, String inputSnapshotVersion) {
        return jdbcTemplate.queryForObject(
                "SELECT inventory_snapshot_id, store_id, sku_id, on_hand_quantity, reserved_quantity, "
                        + "input_snapshot_version FROM sp_inventory_snapshot "
                        + "WHERE store_id = ? AND sku_id = ? AND input_snapshot_version = ?",
                (rs, rowNum) -> new InventorySnapshotState(
                        rs.getLong("inventory_snapshot_id"), rs.getString("store_id"), rs.getString("sku_id"),
                        rs.getInt("on_hand_quantity"), rs.getInt("reserved_quantity"),
                        rs.getString("input_snapshot_version")),
                storeId, SKU_ID, inputSnapshotVersion);
    }

    private void assertInventorySnapshotsUnchanged(
            String inputSnapshotVersion, InventorySnapshotState receiverBefore, InventorySnapshotState donorBefore) {
        assertEquals(receiverBefore, captureInventorySnapshotState(RECEIVER_STORE_ID, inputSnapshotVersion));
        assertEquals(donorBefore, captureInventorySnapshotState(DONOR_STORE_ID, inputSnapshotVersion));
    }

    private static String newKey() {
        return "MQT-IT-KEY-" + UUID.randomUUID();
    }

    private static ApprovalTransactionException assertThrowsApproval(Runnable action) {
        try {
            action.run();
        } catch (ApprovalTransactionException e) {
            return e;
        }
        throw new AssertionError("Expected an ApprovalTransactionException.");
    }

    private ApprovalTransactionCommand approvedCommand(
            Long recommendationId, Long analysisRunId, String inputSnapshotVersion, String ruleVersion, int quantity) {
        return new ApprovalTransactionCommand(
                recommendationId, analysisRunId, inputSnapshotVersion, ruleVersion, 1,
                DecisionStatus.APPROVED, quantity, false,
                "MANUAL_OVERRIDE", "manual quantity test executor IT", "it");
    }

    private ApprovalTransactionCommand approvedCommand(Long recommendationId, SharedDonorFixture fixture, int quantity) {
        return approvedCommand(recommendationId, fixture.analysisRunId(), fixture.inputSnapshotVersion(),
                fixture.ruleVersion(), quantity);
    }

    /**
     * Receiver available=5, donor available=10, both rates=1, route lead time 4/min 1/pkg 1/
     * max 20, receiver policy displayMin=2/targetCoverage=7 -- the same numeric shape already
     * proven in {@code ApprovalTransactionConcurrencyIT}, yielding recommendedBaseQuantity=8.
     */
    private Fixture setUpFixture(String suffix) {
        return setUpCustomFixture(suffix, null);
    }

    private Fixture setUpCustomFixture(String suffix, String routeDonorStoreIdOverride) {
        return setUpCustomFixture(suffix, routeDonorStoreIdOverride, BigDecimal.ONE);
    }

    private Fixture setUpCustomFixture(String suffix, String routeDonorStoreIdOverride, BigDecimal donorBaseRateOverride) {
        String ruleVersion = InventoryAnalysisRules.RULE_VERSION + "-MQTIT" + suffix;
        String inputSnapshotVersion = "MVP-2-MQTIT" + suffix;

        insertSnapshot(RECEIVER_STORE_ID, 6, 1, inputSnapshotVersion);
        insertSnapshot(DONOR_STORE_ID, 10, 0, inputSnapshotVersion);
        SpInventorySnapshot receiverSnapshot = findSnapshot(inputSnapshotVersion, RECEIVER_STORE_ID);
        SpInventorySnapshot donorSnapshot = findSnapshot(inputSnapshotVersion, DONOR_STORE_ID);

        SpAnalysisRun analysisRun =
                analysisRunRepository.save(new SpAnalysisRun(ANALYSIS_DATE, ruleVersion, inputSnapshotVersion));
        analysisRun.markCompleted();
        analysisRun = analysisRunRepository.save(analysisRun);

        SpInventoryMetric receiverMetric = metricRepository.save(new SpInventoryMetric(
                analysisRun, receiverSnapshot, InventoryMetricCalculation.calculate(6, 1, 28)));
        receiverMetric.applyDemandRates(BigDecimal.ONE, BigDecimal.ONE);
        receiverMetric = metricRepository.save(receiverMetric);

        SpInventoryMetric donorMetric = metricRepository.save(new SpInventoryMetric(
                analysisRun, donorSnapshot, InventoryMetricCalculation.calculate(10, 0, 4)));
        donorMetric.applyDemandRates(donorBaseRateOverride, BigDecimal.ONE);
        donorMetric = metricRepository.save(donorMetric);

        SpRebalanceRecommendation recommendation = recommendationRepository.save(new SpRebalanceRecommendation(
                receiverMetric, donorMetric, new RebalanceCalculation(8, 8, 8)));

        String routeDonorStoreId = routeDonorStoreIdOverride != null ? routeDonorStoreIdOverride : DONOR_STORE_ID;
        SpStoreTransferRoute route = routeRepository.save(new SpStoreTransferRoute(
                routeDonorStoreId, RECEIVER_STORE_ID, true, false, 4, 1, 1, 20, inputSnapshotVersion));
        recommendation.assignRoute(route.getRouteId());
        recommendation = recommendationRepository.save(recommendation);

        policyRepository.save(new SpStoreSkuPolicy(RECEIVER_STORE_ID, SKU_ID, 2, 0, 1000, 7, 0, inputSnapshotVersion));
        policyRepository.save(new SpStoreSkuPolicy(DONOR_STORE_ID, SKU_ID, 0, 0, 1000, 0, 0, inputSnapshotVersion));

        entityManager.flush();

        return new Fixture(recommendation.getRecommendationId(), analysisRun.getAnalysisRunId(),
                inputSnapshotVersion, ruleVersion);
    }

    /**
     * Two receivers (available=5 each) sharing one donor metric/snapshot (available=10), so both
     * recommendations lock the same donor row and each independently recalculates to
     * recommendedBaseQuantity=8 before either is touched -- the same shape
     * {@code ApprovalTransactionConcurrencyIT}'s shared-donor fixture already proved.
     */
    private SharedDonorFixture setUpSharedDonorFixture(String suffix) {
        String ruleVersion = InventoryAnalysisRules.RULE_VERSION + "-MQTIT" + suffix;
        String inputSnapshotVersion = "MVP-2-MQTIT" + suffix;

        insertSnapshot(RECEIVER_STORE_ID, 6, 1, inputSnapshotVersion);
        insertSnapshot(RECEIVER_STORE_ID_B, 6, 1, inputSnapshotVersion);
        insertSnapshot(DONOR_STORE_ID, 10, 0, inputSnapshotVersion);
        SpInventorySnapshot receiverASnapshot = findSnapshot(inputSnapshotVersion, RECEIVER_STORE_ID);
        SpInventorySnapshot receiverBSnapshot = findSnapshot(inputSnapshotVersion, RECEIVER_STORE_ID_B);
        SpInventorySnapshot donorSnapshot = findSnapshot(inputSnapshotVersion, DONOR_STORE_ID);

        SpAnalysisRun analysisRun =
                analysisRunRepository.save(new SpAnalysisRun(ANALYSIS_DATE, ruleVersion, inputSnapshotVersion));
        analysisRun.markCompleted();
        analysisRun = analysisRunRepository.save(analysisRun);

        SpInventoryMetric receiverAMetric = metricRepository.save(new SpInventoryMetric(
                analysisRun, receiverASnapshot, InventoryMetricCalculation.calculate(6, 1, 28)));
        receiverAMetric.applyDemandRates(BigDecimal.ONE, BigDecimal.ONE);
        receiverAMetric = metricRepository.save(receiverAMetric);

        SpInventoryMetric receiverBMetric = metricRepository.save(new SpInventoryMetric(
                analysisRun, receiverBSnapshot, InventoryMetricCalculation.calculate(6, 1, 28)));
        receiverBMetric.applyDemandRates(BigDecimal.ONE, BigDecimal.ONE);
        receiverBMetric = metricRepository.save(receiverBMetric);

        SpInventoryMetric donorMetric = metricRepository.save(new SpInventoryMetric(
                analysisRun, donorSnapshot, InventoryMetricCalculation.calculate(10, 0, 4)));
        donorMetric.applyDemandRates(BigDecimal.ONE, BigDecimal.ONE);
        donorMetric = metricRepository.save(donorMetric);

        SpRebalanceRecommendation recommendationA = recommendationRepository.save(new SpRebalanceRecommendation(
                receiverAMetric, donorMetric, new RebalanceCalculation(8, 8, 8)));
        SpRebalanceRecommendation recommendationB = recommendationRepository.save(new SpRebalanceRecommendation(
                receiverBMetric, donorMetric, new RebalanceCalculation(8, 8, 8)));

        SpStoreTransferRoute routeA = routeRepository.save(new SpStoreTransferRoute(
                DONOR_STORE_ID, RECEIVER_STORE_ID, true, false, 4, 1, 1, 20, inputSnapshotVersion));
        SpStoreTransferRoute routeB = routeRepository.save(new SpStoreTransferRoute(
                DONOR_STORE_ID, RECEIVER_STORE_ID_B, true, false, 4, 1, 1, 20, inputSnapshotVersion));
        recommendationA.assignRoute(routeA.getRouteId());
        recommendationB.assignRoute(routeB.getRouteId());
        recommendationA = recommendationRepository.save(recommendationA);
        recommendationB = recommendationRepository.save(recommendationB);

        policyRepository.save(new SpStoreSkuPolicy(RECEIVER_STORE_ID, SKU_ID, 2, 0, 1000, 7, 0, inputSnapshotVersion));
        policyRepository.save(new SpStoreSkuPolicy(RECEIVER_STORE_ID_B, SKU_ID, 2, 0, 1000, 7, 0, inputSnapshotVersion));
        policyRepository.save(new SpStoreSkuPolicy(DONOR_STORE_ID, SKU_ID, 0, 0, 1000, 0, 0, inputSnapshotVersion));

        entityManager.flush();

        return new SharedDonorFixture(recommendationA.getRecommendationId(), recommendationB.getRecommendationId(),
                analysisRun.getAnalysisRunId(), inputSnapshotVersion, ruleVersion);
    }

    private void insertSnapshot(String storeId, int onHand, int reserved, String inputSnapshotVersion) {
        jdbcTemplate.update(
                "INSERT INTO sp_inventory_snapshot (snapshot_date, store_id, sku_id, on_hand_quantity, "
                        + "reserved_quantity, source_type, input_snapshot_version) "
                        + "VALUES (?, ?, ?, ?, ?, 'SYNTHETIC', ?)",
                ANALYSIS_DATE, storeId, SKU_ID, onHand, reserved, inputSnapshotVersion);
    }

    private SpInventorySnapshot findSnapshot(String inputSnapshotVersion, String storeId) {
        return snapshotRepository.findBySnapshotDate(ANALYSIS_DATE).stream()
                .filter(s -> storeId.equals(s.getStoreId()) && inputSnapshotVersion.equals(s.getInputSnapshotVersion()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected a fixture snapshot for " + storeId));
    }

    private record InventorySnapshotState(
            long inventorySnapshotId, String storeId, String skuId,
            int onHandQuantity, int reservedQuantity, String inputSnapshotVersion) {
    }

    private record Fixture(Long recommendationId, Long analysisRunId, String inputSnapshotVersion, String ruleVersion) {
    }

    private record SharedDonorFixture(
            Long recommendationAId, Long recommendationBId, Long analysisRunId,
            String inputSnapshotVersion, String ruleVersion) {
    }
}
