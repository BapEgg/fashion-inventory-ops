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
import com.bapegg.stockpilot.rebalance.SpApprovalBasisRepository;
import com.bapegg.stockpilot.rebalance.SpRebalanceDecision;
import com.bapegg.stockpilot.rebalance.SpRebalanceDecisionRepository;
import com.bapegg.stockpilot.rebalance.SpRebalanceRecommendation;
import com.bapegg.stockpilot.rebalance.SpRebalanceRecommendationRepository;
import com.bapegg.stockpilot.rebalance.SpStoreSkuPolicy;
import com.bapegg.stockpilot.rebalance.SpStoreSkuPolicyRepository;
import com.bapegg.stockpilot.rebalance.SpStoreTransferRoute;
import com.bapegg.stockpilot.rebalance.SpStoreTransferRouteRepository;
import com.bapegg.stockpilot.rebalance.SpTransferDraftRepository;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the Codex review finding that a mid-write failure between the decision insert
 * and the basis/draft inserts must roll back everything -- never leaving an orphaned
 * decision row behind -- and that the constraint-name-to-error-code translation for such a
 * failure happens through {@link ApprovalTransactionFacade}'s post-rollback path (never a
 * raw persistence exception), per {@code knowledge/business-rules.md} section 10.
 * <p>
 * Uses {@link MockitoSpyBean} to wrap the real {@link SpApprovalBasisRepository}/
 * {@link SpTransferDraftRepository} beans and inject one failure at the exact write step
 * under test -- a genuine Spring bean substitution, not a production test flag or branch.
 * Each stub is scoped by {@code argThat} to one test's own recommendation id, so it can
 * never affect a different test's fixture even though the spy bean is shared across the
 * whole (separately cached, due to the bean override) Spring context for this class.
 * <p>
 * Deliberately NOT {@code @Transactional} at the class level, for the same reason as
 * {@link ApprovalTransactionConcurrencyIT}: the {@code GenerationType.IDENTITY} decision
 * insert flushes to Oracle immediately, so if the test method itself were the outermost
 * transaction, that row would still be visible to a same-transaction read right after the
 * later basis/draft save fails (the physical ROLLBACK only happens when the OUTERMOST
 * {@code @Transactional} boundary completes). Leaving this class non-transactional makes
 * {@link ApprovalTransactionExecutor#execute}'s own {@code @Transactional} the outermost
 * boundary for the call under test, so its rollback is real and immediate, and the
 * post-call assertions genuinely observe the post-rollback database state. Fixture setup
 * (each repository call auto-committing on its own, per {@code spring.jpa.open-in-view:
 * false}) is cleaned up manually in a {@code finally} block, following
 * {@code ApiGoldenScenarioIT}'s convention. Skipped (not failed) when DB_URL is not set.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "DB_URL", matches = ".+")
class ApprovalTransactionAtomicityIT {

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

    @MockitoSpyBean
    private SpApprovalBasisRepository approvalBasisRepository;

    @MockitoSpyBean
    private SpTransferDraftRepository transferDraftRepository;

    @Autowired
    private ApprovalTransactionFacade facade;

    @Test
    void draftSaveFailureRollsBackTheAlreadySavedDecisionAndBasis() {
        Fixture fixture = setUpFixture("-ATOMIC-DRAFT");
        try {
            Long recommendationId = fixture.recommendationId();
            Mockito.doThrow(new DataIntegrityViolationException("simulated mid-write failure",
                            new RuntimeException("ORA-00001: unique constraint (STOCKPILOT.UQ_SP_DEC_REC_SEQ) violated")))
                    .when(transferDraftRepository)
                    .save(Mockito.argThat(draft -> recommendationId.equals(
                            draft.getDecision().getRecommendation().getRecommendationId())));

            ApprovalTransactionException exception = assertThrowsApproval(
                    () -> facade.execute(approvedCommand(fixture, 5), newKey()));

            // The simulated failure carries a real UQ_SP_DEC_REC_SEQ constraint message, so
            // this also proves that constraint's rollback-then-translate path (Codex review
            // finding 2) produces the stable DECISION_CONFLICT code, never a raw exception.
            assertEquals(ApprovalErrorCode.DECISION_CONFLICT, exception.code());
            assertNoRowsPersisted(fixture);
        } finally {
            cleanupFixture(fixture);
        }
    }

    @Test
    void basisSaveFailureRollsBackTheAlreadySavedDecisionAndWritesNoDraft() {
        Fixture fixture = setUpFixture("-ATOMIC-BASIS");
        try {
            Long analysisRunId = fixture.analysisRunId();
            Mockito.doThrow(new DataIntegrityViolationException("simulated mid-write failure"))
                    .when(approvalBasisRepository)
                    .save(Mockito.argThat(basis -> analysisRunId.equals(basis.getAnalysisRun().getAnalysisRunId())));

            ApprovalTransactionException exception = assertThrowsApproval(
                    () -> facade.execute(approvedCommand(fixture, 5), newKey()));

            // No sp_error_constraint_map row matches this synthetic message, so the
            // translator's documented fallback applies -- still a stable code, never the raw
            // exception.
            assertEquals(ApprovalErrorCode.INTERNAL_SERVER_ERROR, exception.code());
            assertNoRowsPersisted(fixture);
        } finally {
            cleanupFixture(fixture);
        }
    }

    private void assertNoRowsPersisted(Fixture fixture) {
        assertTrue(decisionRepository
                .findAllByRecommendation_RecommendationIdOrderByDecisionSequenceAsc(fixture.recommendationId())
                .isEmpty(), "A mid-write failure must leave no decision row behind.");
        assertTrue(approvalBasisRepository.findAll().stream()
                .noneMatch(b -> b.getAnalysisRun().getAnalysisRunId().equals(fixture.analysisRunId())),
                "A mid-write failure must leave no approval basis row behind.");
        assertTrue(transferDraftRepository.findAll().stream()
                .noneMatch(d -> fixture.recommendationId().equals(
                        d.getDecision().getRecommendation().getRecommendationId())),
                "A mid-write failure must leave no transfer draft row behind.");
    }

    private ApprovalTransactionCommand approvedCommand(Fixture fixture, int quantity) {
        return new ApprovalTransactionCommand(
                fixture.recommendationId(), fixture.analysisRunId(), fixture.inputSnapshotVersion(),
                fixture.ruleVersion(), 1, DecisionStatus.APPROVED, quantity, false,
                "MANUAL_OVERRIDE", "approval transaction atomicity IT", "it");
    }

    private static String newKey() {
        return "ATOMIC-IT-KEY-" + UUID.randomUUID();
    }

    private static ApprovalTransactionException assertThrowsApproval(Runnable action) {
        try {
            action.run();
        } catch (ApprovalTransactionException e) {
            return e;
        }
        throw new AssertionError("Expected an ApprovalTransactionException.");
    }

    private Fixture setUpFixture(String suffix) {
        int receiverOnHand = 6;
        int receiverReserved = 1;
        int donorOnHand = 42;
        int donorReserved = 2;
        // rule_version is VARCHAR2(32 CHAR); keep this prefix short so even the longest suffix fits.
        String ruleVersion = InventoryAnalysisRules.RULE_VERSION + "-ATXIT" + suffix;
        String inputSnapshotVersion = "MVP-2-APPROVAL-TX-IT" + suffix;

        insertSnapshot(RECEIVER_STORE_ID, receiverOnHand, receiverReserved, inputSnapshotVersion);
        insertSnapshot(DONOR_STORE_ID, donorOnHand, donorReserved, inputSnapshotVersion);

        SpInventorySnapshot receiverSnapshot = findSnapshot(inputSnapshotVersion, RECEIVER_STORE_ID);
        SpInventorySnapshot donorSnapshot = findSnapshot(inputSnapshotVersion, DONOR_STORE_ID);

        SpAnalysisRun analysisRun = analysisRunRepository.save(
                new SpAnalysisRun(ANALYSIS_DATE, ruleVersion, inputSnapshotVersion));
        analysisRun.markCompleted();
        analysisRun = analysisRunRepository.save(analysisRun);

        SpInventoryMetric receiverMetric = metricRepository.save(new SpInventoryMetric(
                analysisRun, receiverSnapshot, InventoryMetricCalculation.calculate(receiverOnHand, receiverReserved, 28)));
        receiverMetric.applyDemandRates(BigDecimal.ONE, BigDecimal.ONE);
        receiverMetric = metricRepository.save(receiverMetric);

        SpInventoryMetric donorMetric = metricRepository.save(new SpInventoryMetric(
                analysisRun, donorSnapshot, InventoryMetricCalculation.calculate(donorOnHand, donorReserved, 4)));
        donorMetric.applyDemandRates(BigDecimal.ONE, BigDecimal.ONE);
        donorMetric = metricRepository.save(donorMetric);

        SpRebalanceRecommendation recommendation = recommendationRepository.save(new SpRebalanceRecommendation(
                receiverMetric, donorMetric, new RebalanceCalculation(5, 5, 5)));

        SpStoreTransferRoute route = routeRepository.save(new SpStoreTransferRoute(
                DONOR_STORE_ID, RECEIVER_STORE_ID, true, false, 4, 1, 1, 20, inputSnapshotVersion));
        recommendation.assignRoute(route.getRouteId());
        recommendation = recommendationRepository.save(recommendation);

        SpStoreSkuPolicy receiverPolicy = policyRepository.save(
                new SpStoreSkuPolicy(RECEIVER_STORE_ID, SKU_ID, 2, 0, 1000, 7, 0, inputSnapshotVersion));
        SpStoreSkuPolicy donorPolicy = policyRepository.save(
                new SpStoreSkuPolicy(DONOR_STORE_ID, SKU_ID, 0, 0, 1000, 0, 7, inputSnapshotVersion));

        return new Fixture(recommendation.getRecommendationId(), analysisRun.getAnalysisRunId(),
                inputSnapshotVersion, ruleVersion, route.getRouteId(),
                receiverPolicy.getStoreSkuPolicyId(), donorPolicy.getStoreSkuPolicyId(),
                receiverMetric.getInventoryMetricId(), donorMetric.getInventoryMetricId());
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

    private void cleanupFixture(Fixture fixture) {
        if (fixture == null) {
            return;
        }
        List<SpRebalanceDecision> decisions = decisionRepository
                .findAllByRecommendation_RecommendationIdOrderByDecisionSequenceAsc(fixture.recommendationId());
        for (SpRebalanceDecision decision : decisions) {
            transferDraftRepository.findByDecision_DecisionId(decision.getDecisionId())
                    .ifPresent(transferDraftRepository::delete);
            approvalBasisRepository.findByDecision_DecisionId(decision.getDecisionId())
                    .ifPresent(approvalBasisRepository::delete);
        }
        decisionRepository.deleteAll(decisions);
        recommendationRepository.deleteById(fixture.recommendationId());
        routeRepository.deleteById(fixture.routeId());
        policyRepository.deleteById(fixture.receiverPolicyId());
        policyRepository.deleteById(fixture.donorPolicyId());
        metricRepository.deleteById(fixture.receiverMetricId());
        metricRepository.deleteById(fixture.donorMetricId());
        analysisRunRepository.deleteById(fixture.analysisRunId());
        jdbcTemplate.update("DELETE FROM sp_inventory_snapshot WHERE input_snapshot_version = ?",
                fixture.inputSnapshotVersion());
    }

    private record Fixture(
            Long recommendationId, Long analysisRunId, String inputSnapshotVersion, String ruleVersion,
            Long routeId, Long receiverPolicyId, Long donorPolicyId,
            Long receiverMetricId, Long donorMetricId) {
    }
}
