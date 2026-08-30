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
import com.bapegg.stockpilot.rebalance.RebalanceCalculation;
import com.bapegg.stockpilot.rebalance.SpApprovalBasis;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies {@link ApprovalTransactionFacade}'s sequential (non-concurrent) behavior
 * against the real Oracle instance, per {@code knowledge/business-rules.md} section 10:
 * single HELD/REJECTED/APPROVED decisions, an append-only HELD-then-APPROVED history,
 * stale/terminal/invalid-quantity rejection with no row written, idempotency-key replay,
 * and a reused key with a different payload. Real two-transaction concurrency is
 * covered separately in {@link ApprovalTransactionConcurrencyIT}, since it needs
 * committed (not rolled-back) fixture data. Skipped (not failed) when DB_URL is not set.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "DB_URL", matches = ".+")
@Transactional
class ApprovalTransactionExecutorIT {

    private static final LocalDate ANALYSIS_DATE = LocalDate.of(2026, 10, 1);
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
    private SpRebalanceDecisionRepository decisionRepository;

    @Autowired
    private SpApprovalBasisRepository approvalBasisRepository;

    @Autowired
    private SpTransferDraftRepository transferDraftRepository;

    @Autowired
    private ApprovalTransactionFacade facade;

    @Autowired
    private EntityManager entityManager;

    @Test
    void heldDecisionIsSavedAloneAtSequenceOne() {
        Fixture fixture = setUpFixture("-HELD", 6, 1, 42, 2);

        ApprovalTransactionResult result = facade.execute(heldCommand(fixture), newKey());

        assertTrue(result.created());
        assertEquals(DecisionStatus.HELD, result.status());
        assertEquals(1, result.decisionSequence());
        assertNull(result.transferDraftId());
        assertTrue(approvalBasisRepository.findByDecision_DecisionId(result.decisionId()).isEmpty());
        assertTrue(transferDraftRepository.findByDecision_DecisionId(result.decisionId()).isEmpty());
    }

    @Test
    void rejectedDecisionIsSavedAloneAtSequenceOne() {
        Fixture fixture = setUpFixture("-REJECTED", 6, 1, 42, 2);

        ApprovalTransactionResult result = facade.execute(rejectedCommand(fixture), newKey());

        assertTrue(result.created());
        assertEquals(DecisionStatus.REJECTED, result.status());
        assertEquals(1, result.decisionSequence());
        assertNull(result.transferDraftId());
    }

    @Test
    void approvedDecisionAtomicallySavesDecisionBasisAndDraft() {
        Fixture fixture = setUpFixture("-APPROVED", 6, 1, 42, 2);

        ApprovalTransactionResult result = facade.execute(approvedCommand(fixture, 5), newKey());

        assertTrue(result.created());
        assertEquals(DecisionStatus.APPROVED, result.status());
        assertEquals(1, result.decisionSequence());
        assertNotNull(result.transferDraftId());

        SpApprovalBasis basis = approvalBasisRepository.findByDecision_DecisionId(result.decisionId()).orElseThrow();
        assertEquals(fixture.analysisRunId(), basis.getAnalysisRun().getAnalysisRunId());
        assertTrue(basis.isCandidateEligible());
        assertTrue(transferDraftRepository.findByDecision_DecisionId(result.decisionId()).isPresent());
    }

    @Test
    void heldThenApprovedProducesAnAppendOnlySequenceTwoWithBasisAndDraft() {
        Fixture fixture = setUpFixture("-HELD-THEN-APPROVED", 6, 1, 42, 2);

        ApprovalTransactionResult held = facade.execute(heldCommand(fixture), newKey());
        assertEquals(1, held.decisionSequence());

        ApprovalTransactionResult approved = facade.execute(approvedCommand(fixture, 5), newKey());
        assertEquals(2, approved.decisionSequence());
        assertEquals(DecisionStatus.APPROVED, approved.status());
        assertNotNull(approved.transferDraftId());

        assertEquals(2, decisionRepository
                .findAllByRecommendation_RecommendationIdOrderByDecisionSequenceAsc(fixture.recommendationId())
                .size());
    }

    @Test
    void terminalRecommendationRejectsAFurtherDecisionAndWritesNoRow() {
        Fixture fixture = setUpFixture("-TERMINAL", 6, 1, 42, 2);
        facade.execute(approvedCommand(fixture, 5), newKey());

        ApprovalTransactionException exception = assertThrowsApproval(
                () -> facade.execute(rejectedCommand(fixture), newKey()));
        assertEquals(ApprovalErrorCode.DECISION_ALREADY_TERMINAL, exception.code());
        assertEquals(1, decisionRepository
                .findAllByRecommendation_RecommendationIdOrderByDecisionSequenceAsc(fixture.recommendationId())
                .size());
    }

    @Test
    void staleVersionMismatchRejectsAndWritesNoRow() {
        Fixture fixture = setUpFixture("-STALE-VERSION", 6, 1, 42, 2);
        ApprovalTransactionCommand staleCommand = new ApprovalTransactionCommand(
                fixture.recommendationId(), fixture.analysisRunId(), fixture.inputSnapshotVersion(),
                "WRONG-RULE-VERSION", 1, DecisionStatus.APPROVED, 5, false, null, null, "it");

        ApprovalTransactionException exception = assertThrowsApproval(() -> facade.execute(staleCommand, newKey()));
        assertEquals(ApprovalErrorCode.STALE_RECOMMENDATION, exception.code());
        assertTrue(decisionRepository
                .findAllByRecommendation_RecommendationIdOrderByDecisionSequenceAsc(fixture.recommendationId())
                .isEmpty());
    }

    @Test
    void quantityExceedingDonorTransferableIsRejectedAsStaleAndWritesNoRow() {
        // Fixture route/policy give a small donor-transferable ceiling; request far above it.
        Fixture fixture = setUpFixture("-OVER-DONOR", 6, 1, 42, 2);

        ApprovalTransactionException exception = assertThrowsApproval(
                () -> facade.execute(approvedCommand(fixture, 999), newKey()));
        assertEquals(ApprovalErrorCode.STALE_RECOMMENDATION, exception.code());
        assertTrue(decisionRepository
                .findAllByRecommendation_RecommendationIdOrderByDecisionSequenceAsc(fixture.recommendationId())
                .isEmpty());
        assertTrue(approvalBasisRepository.findAll().stream()
                .noneMatch(b -> b.getAnalysisRun().getAnalysisRunId().equals(fixture.analysisRunId())));
    }

    @Test
    void sameKeyAndSamePayloadReplaysTheOriginalResultWithoutANewRow() {
        Fixture fixture = setUpFixture("-REPLAY", 6, 1, 42, 2);
        String key = newKey();
        ApprovalTransactionCommand command = heldCommand(fixture);

        ApprovalTransactionResult first = facade.execute(command, key);
        ApprovalTransactionResult replay = facade.execute(command, key);

        assertEquals(first.decisionId(), replay.decisionId());
        assertFalse(replay.created());
        assertEquals(1, decisionRepository
                .findAllByRecommendation_RecommendationIdOrderByDecisionSequenceAsc(fixture.recommendationId())
                .size());
    }

    @Test
    void wrongRouteDonorIsRejectedAsStaleAndWritesNoRow() {
        Fixture fixture = setUpCustomFixture("-WRONG-ROUTE-DONOR", 6, 1, 42, 2,
                "STORE-SEONGSU", null, null, false, null, null);

        assertStaleWithNoRows(fixture);
    }

    @Test
    void wrongRouteReceiverIsRejectedAsStaleAndWritesNoRow() {
        Fixture fixture = setUpCustomFixture("-WRONG-ROUTE-RECEIVER", 6, 1, 42, 2,
                null, "STORE-JAMSIL", null, false, null, null);

        assertStaleWithNoRows(fixture);
    }

    @Test
    void wrongRouteInputVersionIsRejectedAsStaleAndWritesNoRow() {
        Fixture fixture = setUpCustomFixture("-WRONG-ROUTE-VERSION", 6, 1, 42, 2,
                null, null, "MVP-2-WRONG-ROUTE-VERSION", false, null, null);

        assertStaleWithNoRows(fixture);
    }

    @Test
    void donorMetricOnADifferentAnalysisRunIsRejectedAsStaleAndWritesNoRow() {
        Fixture fixture = setUpCustomFixture("-WRONG-DONOR-RUN", 6, 1, 42, 2,
                null, null, null, true, null, null);

        assertStaleWithNoRows(fixture);
    }

    @Test
    void receiverSnapshotWithAMismatchedInputVersionIsRejectedAsStaleAndWritesNoRow() {
        Fixture fixture = setUpCustomFixture("-WRONG-RCV-SNAP-VER", 6, 1, 42, 2,
                null, null, null, false, "MVP-2-WRONG-RECEIVER-SNAPSHOT-VERSION", null);

        assertStaleWithNoRows(fixture);
    }

    @Test
    void donorSnapshotWithAMismatchedInputVersionIsRejectedAsStaleAndWritesNoRow() {
        Fixture fixture = setUpCustomFixture("-WRONG-DONOR-SNAP-VER", 6, 1, 42, 2,
                null, null, null, false, null, "MVP-2-WRONG-DONOR-SNAPSHOT-VERSION");

        assertStaleWithNoRows(fixture);
    }

    private void assertStaleWithNoRows(Fixture fixture) {
        ApprovalTransactionException exception = assertThrowsApproval(
                () -> facade.execute(approvedCommand(fixture, 5), newKey()));
        assertEquals(ApprovalErrorCode.STALE_RECOMMENDATION, exception.code());
        assertTrue(decisionRepository
                .findAllByRecommendation_RecommendationIdOrderByDecisionSequenceAsc(fixture.recommendationId())
                .isEmpty());
        assertTrue(approvalBasisRepository.findAll().stream()
                .noneMatch(b -> b.getAnalysisRun().getAnalysisRunId().equals(fixture.analysisRunId())));
    }

    @Test
    void sameKeyWithADifferentPayloadIsRejectedAsReused() {
        Fixture fixture = setUpFixture("-KEY-REUSE", 6, 1, 42, 2);
        String key = newKey();
        facade.execute(heldCommand(fixture), key);

        ApprovalTransactionException exception = assertThrowsApproval(() -> facade.execute(rejectedCommand(fixture), key));
        assertEquals(ApprovalErrorCode.IDEMPOTENCY_KEY_REUSED, exception.code());
        assertEquals(1, decisionRepository
                .findAllByRecommendation_RecommendationIdOrderByDecisionSequenceAsc(fixture.recommendationId())
                .size());
    }

    private ApprovalTransactionCommand heldCommand(Fixture fixture) {
        return new ApprovalTransactionCommand(
                fixture.recommendationId(), fixture.analysisRunId(), fixture.inputSnapshotVersion(),
                fixture.ruleVersion(), 1, DecisionStatus.HELD, null, false, "NEEDS_REVIEW", "awaiting sign-off", "it");
    }

    private ApprovalTransactionCommand rejectedCommand(Fixture fixture) {
        return new ApprovalTransactionCommand(
                fixture.recommendationId(), fixture.analysisRunId(), fixture.inputSnapshotVersion(),
                fixture.ruleVersion(), 1, DecisionStatus.REJECTED, null, false, "NOT_NEEDED", "no longer needed", "it");
    }

    /**
     * Always carries a reason: the fixture's exact recalculated BASE quantity is not
     * hand-derived here (that formula belongs to {@code InventoryProjection}'s own tests),
     * so a quantity that happens to differ from it must not trip
     * {@code ApprovalRequestValidation}'s "changed quantity needs a reason" rule.
     */
    private ApprovalTransactionCommand approvedCommand(Fixture fixture, int quantity) {
        return new ApprovalTransactionCommand(
                fixture.recommendationId(), fixture.analysisRunId(), fixture.inputSnapshotVersion(),
                fixture.ruleVersion(), 1, DecisionStatus.APPROVED, quantity, false,
                "MANUAL_OVERRIDE", "approval transaction executor IT", "it");
    }

    private static String newKey() {
        return "IT-KEY-" + UUID.randomUUID();
    }

    private static ApprovalTransactionException assertThrowsApproval(Runnable action) {
        try {
            action.run();
        } catch (ApprovalTransactionException e) {
            return e;
        }
        throw new AssertionError("Expected an ApprovalTransactionException.");
    }

    /**
     * Builds one receiver/donor snapshot+metric+recommendation+route+policy fixture, unique
     * per test via {@code suffix} on the rule/input-snapshot versions -- avoids any risk of
     * collision if this method is ever called more than once within the same rolled-back
     * transaction.
     */
    private Fixture setUpFixture(String suffix, int receiverOnHand, int receiverReserved, int donorOnHand, int donorReserved) {
        return setUpCustomFixture(suffix, receiverOnHand, receiverReserved, donorOnHand, donorReserved,
                null, null, null, false, null, null);
    }

    /**
     * The general-purpose fixture builder: every corruption parameter defaults to the
     * valid, self-consistent shape when {@code null}/{@code false}, so passing exactly one
     * non-default value builds a fixture that violates exactly one identity fact the
     * Codex review required {@link ApprovalTransactionExecutor#recalculate} to cross-check --
     * a wrong route donor/receiver, a route on a different {@code inputSnapshotVersion}, a
     * donor metric on a different {@code SpAnalysisRun}, or a receiver/donor snapshot whose
     * own {@code input_snapshot_version} column disagrees with the command's.
     */
    private Fixture setUpCustomFixture(
            String suffix, int receiverOnHand, int receiverReserved, int donorOnHand, int donorReserved,
            String routeDonorStoreIdOverride, String routeReceiverStoreIdOverride, String routeInputSnapshotVersionOverride,
            boolean donorMetricOnSeparateAnalysisRun,
            String receiverSnapshotInputVersionOverride, String donorSnapshotInputVersionOverride) {
        // rule_version is VARCHAR2(32 CHAR); keep this prefix short so even the longest suffix fits.
        String ruleVersion = InventoryAnalysisRules.RULE_VERSION + "-ATXIT" + suffix;
        String inputSnapshotVersion = "MVP-2-APPROVAL-TX-IT" + suffix;
        String receiverSnapshotVersion =
                receiverSnapshotInputVersionOverride != null ? receiverSnapshotInputVersionOverride : inputSnapshotVersion;
        String donorSnapshotVersion =
                donorSnapshotInputVersionOverride != null ? donorSnapshotInputVersionOverride : inputSnapshotVersion;

        insertSnapshot(RECEIVER_STORE_ID, receiverOnHand, receiverReserved, receiverSnapshotVersion);
        insertSnapshot(DONOR_STORE_ID, donorOnHand, donorReserved, donorSnapshotVersion);

        SpInventorySnapshot receiverSnapshot = findSnapshot(receiverSnapshotVersion, RECEIVER_STORE_ID);
        SpInventorySnapshot donorSnapshot = findSnapshot(donorSnapshotVersion, DONOR_STORE_ID);

        SpAnalysisRun analysisRun = analysisRunRepository.save(
                new SpAnalysisRun(ANALYSIS_DATE, ruleVersion, inputSnapshotVersion));
        analysisRun.markCompleted();
        analysisRun = analysisRunRepository.save(analysisRun);

        SpAnalysisRun donorAnalysisRun = analysisRun;
        if (donorMetricOnSeparateAnalysisRun) {
            SpAnalysisRun otherRun = analysisRunRepository.save(
                    new SpAnalysisRun(ANALYSIS_DATE, ruleVersion + "-DNR", inputSnapshotVersion));
            otherRun.markCompleted();
            donorAnalysisRun = analysisRunRepository.save(otherRun);
        }

        SpInventoryMetric receiverMetric = metricRepository.save(new SpInventoryMetric(
                analysisRun, receiverSnapshot, InventoryMetricCalculation.calculate(receiverOnHand, receiverReserved, 28)));
        receiverMetric.applyDemandRates(BigDecimal.ONE, BigDecimal.ONE);
        receiverMetric = metricRepository.save(receiverMetric);

        SpInventoryMetric donorMetric = metricRepository.save(new SpInventoryMetric(
                donorAnalysisRun, donorSnapshot, InventoryMetricCalculation.calculate(donorOnHand, donorReserved, 4)));
        donorMetric.applyDemandRates(BigDecimal.ONE, BigDecimal.ONE);
        donorMetric = metricRepository.save(donorMetric);

        SpRebalanceRecommendation recommendation = recommendationRepository.save(new SpRebalanceRecommendation(
                receiverMetric, donorMetric, new RebalanceCalculation(5, 5, 5)));

        String routeDonorStoreId = routeDonorStoreIdOverride != null ? routeDonorStoreIdOverride : DONOR_STORE_ID;
        String routeReceiverStoreId = routeReceiverStoreIdOverride != null ? routeReceiverStoreIdOverride : RECEIVER_STORE_ID;
        String routeInputSnapshotVersion =
                routeInputSnapshotVersionOverride != null ? routeInputSnapshotVersionOverride : inputSnapshotVersion;
        SpStoreTransferRoute route = routeRepository.save(new SpStoreTransferRoute(
                routeDonorStoreId, routeReceiverStoreId, true, false, 4, 1, 1, 20, routeInputSnapshotVersion));
        recommendation.assignRoute(route.getRouteId());
        recommendation = recommendationRepository.save(recommendation);

        policyRepository.save(new SpStoreSkuPolicy(RECEIVER_STORE_ID, SKU_ID, 2, 0, 1000, 7, 0, inputSnapshotVersion));
        policyRepository.save(new SpStoreSkuPolicy(DONOR_STORE_ID, SKU_ID, 0, 0, 1000, 0, 7, inputSnapshotVersion));

        entityManager.flush();

        return new Fixture(recommendation.getRecommendationId(), analysisRun.getAnalysisRunId(),
                inputSnapshotVersion, ruleVersion);
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

    private record Fixture(Long recommendationId, Long analysisRunId, String inputSnapshotVersion, String ruleVersion) {
    }
}
