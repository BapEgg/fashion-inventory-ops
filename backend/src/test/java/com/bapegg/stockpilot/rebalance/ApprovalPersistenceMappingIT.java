package com.bapegg.stockpilot.rebalance;

import com.bapegg.stockpilot.analysis.InventoryAnalysisRules;
import com.bapegg.stockpilot.analysis.InventoryMetricCalculation;
import com.bapegg.stockpilot.analysis.SpAnalysisRun;
import com.bapegg.stockpilot.analysis.SpAnalysisRunRepository;
import com.bapegg.stockpilot.analysis.SpInventoryMetric;
import com.bapegg.stockpilot.analysis.SpInventoryMetricRepository;
import com.bapegg.stockpilot.inventory.SpInventorySnapshot;
import com.bapegg.stockpilot.inventory.SpInventorySnapshotRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the JPA persistence mapping added for {@code V6}/{@code V10}/{@code V11}'s
 * physical columns against the real Oracle instance: an append-only decision history
 * per recommendation, {@link SpApprovalBasis}/{@link SpTransferDraft} each 1:1 with a
 * decision, and that the existing MVP-1 create/read path still round-trips every new
 * column's DB-default value correctly through Hibernate. Skipped (not failed) when
 * DB_URL is not set.
 * <p>
 * This test only proves the *mapping* -- it does not exercise any approval
 * transaction, lock, or recalculation, since none of that exists yet. Each test method
 * runs in one Spring-managed transaction that always rolls back.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "DB_URL", matches = ".+")
@Transactional
class ApprovalPersistenceMappingIT {

    private static final LocalDate ANALYSIS_DATE = LocalDate.parse("2026-08-25");
    private static final String RULE_VERSION = InventoryAnalysisRules.RULE_VERSION + "-PERSISTENCE-MAPPING-IT";

    @Autowired
    private SpAnalysisRunRepository analysisRunRepository;

    @Autowired
    private SpInventoryMetricRepository metricRepository;

    @Autowired
    private SpInventorySnapshotRepository snapshotRepository;

    @Autowired
    private SpRebalanceRecommendationRepository recommendationRepository;

    @Autowired
    private SpRebalanceDecisionRepository decisionRepository;

    @Autowired
    private SpApprovalBasisRepository approvalBasisRepository;

    @Autowired
    private SpTransferDraftRepository transferDraftRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @Test
    void mvp1DecisionRoundTripsEveryNewColumnsDbDefaultThroughHibernate() {
        SpAnalysisRun testRun = createTestAnalysisRun();
        SpRebalanceRecommendation recommendation = createTestRecommendation(testRun);
        SpRebalanceDecision saved = decisionRepository.save(
                new SpRebalanceDecision(recommendation, DecisionStatus.APPROVED, 20, "mapping IT", "mapping-it"));

        // flush + clear forces a real round-trip through Oracle: without it, Hibernate's
        // first-level cache returns the same in-memory instance for the same PK within this
        // one transaction, which would never pick up decided_at/evaluated_at's DB-generated
        // DEFAULT SYSTIMESTAMP values (they are insertable=false/updatable=false).
        entityManager.flush();
        entityManager.clear();

        SpRebalanceDecision reloaded = decisionRepository.findById(saved.getDecisionId()).orElseThrow();
        assertEquals(1, reloaded.getDecisionSequence());
        assertEquals("MVP-1", reloaded.getDecisionContractVersion());
        assertNull(reloaded.getReasonCode());
        assertEquals(1, reloaded.getRecommendationVersion());
        assertTrue(reloaded.getDecisionRequestId().startsWith("MVP1-"));
        assertFalse(reloaded.isPolicyException());
        assertNotNull(reloaded.getDecidedAt());

        SpRebalanceRecommendation reloadedRecommendation = reloaded.getRecommendation();
        assertEquals(CandidateStatus.ELIGIBLE, reloadedRecommendation.getCandidateStatus());
        assertEquals(1, reloadedRecommendation.getCandidateVersion());
        assertEquals(RecommendationMode.RECOMMENDED, reloadedRecommendation.getRecommendationMode());
        assertNull(reloadedRecommendation.getRouteId());
        assertNotNull(reloadedRecommendation.getEvaluatedAt());

        assertEquals("MVP-1-LEGACY", testRun.getInputSnapshotVersion());
    }

    @Test
    void oneRecommendationCanHaveAnAppendOnlyDecisionHistory() {
        SpAnalysisRun testRun = createTestAnalysisRun();
        SpRebalanceRecommendation recommendation = createTestRecommendation(testRun);
        Long recommendationId = recommendation.getRecommendationId();
        SpRebalanceDecision first = decisionRepository.save(
                new SpRebalanceDecision(recommendation, DecisionStatus.APPROVED, 20, "first decision", "mapping-it"));

        // Every subsequent row is inserted directly via raw SQL, since no Java code builds a
        // non-MVP-1-shaped decision yet -- this proves the @ManyToOne mapping and the new
        // DecisionStatus values (HELD/EXPIRED did not exist in this enum before this round) can
        // *read* an append-only history the future approval transaction service will *write*.
        insertRawMvp2Decision(recommendationId, 2, "REJECTED", "SUPERSEDED", "superseded by a later analysis run");
        insertRawMvp2Decision(recommendationId, 3, "HELD", "NEEDS_REVIEW", "awaiting manager sign-off");
        insertRawMvp2Decision(recommendationId, 4, "EXPIRED", "TIMED_OUT", "recommendation expired before a decision");

        List<SpRebalanceDecision> history = decisionRepository
                .findAllByRecommendation_RecommendationIdOrderByDecisionSequenceAsc(recommendationId);
        assertEquals(4, history.size());
        assertEquals(first.getDecisionId(), history.get(0).getDecisionId());
        assertEquals(DecisionStatus.APPROVED, history.get(0).getDecisionStatus());
        assertEquals(DecisionStatus.REJECTED, history.get(1).getDecisionStatus());
        assertEquals(DecisionStatus.HELD, history.get(2).getDecisionStatus());
        assertEquals(DecisionStatus.EXPIRED, history.get(3).getDecisionStatus());
        assertEquals("MVP-2", history.get(3).getDecisionContractVersion());

        // The "current decision" query must resolve to the highest sequence, not the first
        // decision the MVP-1 flow always creates -- this is the query InventoryExceptionService
        // now uses instead of the removed single-result findByRecommendation_RecommendationId.
        SpRebalanceDecision latest = decisionRepository
                .findFirstByRecommendation_RecommendationIdOrderByDecisionSequenceDesc(recommendationId)
                .orElseThrow();
        assertEquals(4, latest.getDecisionSequence());
        assertEquals(DecisionStatus.EXPIRED, latest.getDecisionStatus());

        // existsBy... (the MVP-1 duplicate-decision guard) must stay true regardless of how many
        // rows exist, rather than throwing once more than one is present.
        assertTrue(decisionRepository.existsByRecommendation_RecommendationId(recommendationId));
    }

    private void insertRawMvp2Decision(Long recommendationId, int sequence, String status, String reasonCode, String reason) {
        jdbcTemplate.update(
                "INSERT INTO sp_rebalance_decision (recommendation_id, decision_status, selected_quantity, reason, "
                        + "actor_label, decision_sequence, decision_contract_version, reason_code, "
                        + "recommendation_version, decision_request_id, policy_exception_flag) "
                        + "VALUES (?, ?, NULL, ?, 'mapping-it', ?, 'MVP-2', ?, 1, ?, 'N')",
                recommendationId, status, reason, sequence, reasonCode, "IT-HISTORY-" + UUID.randomUUID());
    }

    @Test
    void approvalBasisAndTransferDraftAreEachOneToOneWithADecision() {
        SpAnalysisRun testRun = createTestAnalysisRun();
        SpRebalanceRecommendation recommendation = createTestRecommendation(testRun);
        SpRebalanceDecision decision = decisionRepository.save(
                new SpRebalanceDecision(recommendation, DecisionStatus.APPROVED, 20, "mapping IT", "mapping-it"));

        SpApprovalBasis basis = approvalBasisRepository.save(new SpApprovalBasis(
                decision, testRun, "MVP-2-GS-V1", RULE_VERSION, 1,
                19L, 61L, 1, 1, 50, 94L, 10L, 40L, 0L));
        SpTransferDraft draft = transferDraftRepository.save(new SpTransferDraft(
                decision, "STORE-HONGDAE", "STORE-GANGNAM", "SKU-CAP-BLACK-FREE", 20, "MVP-2-DRAFT-V1"));

        entityManager.flush();
        entityManager.clear();

        SpApprovalBasis reloadedBasis = approvalBasisRepository.findByDecision_DecisionId(decision.getDecisionId())
                .orElseThrow();
        assertEquals(basis.getApprovalBasisId(), reloadedBasis.getApprovalBasisId());
        assertTrue(reloadedBasis.isCandidateEligible());
        assertEquals("MVP-2", reloadedBasis.getBasisContractVersion());
        assertEquals(testRun.getAnalysisRunId(), reloadedBasis.getAnalysisRun().getAnalysisRunId());
        assertNotNull(reloadedBasis.getCreatedAt());

        SpTransferDraft reloadedDraft = transferDraftRepository.findByDecision_DecisionId(decision.getDecisionId())
                .orElseThrow();
        assertEquals(draft.getTransferDraftId(), reloadedDraft.getTransferDraftId());
        assertEquals(DraftStatus.CREATED, reloadedDraft.getDraftStatus());
        assertEquals("STORE-HONGDAE", reloadedDraft.getDonorStoreId());
        assertNotNull(reloadedDraft.getCreatedAt());
        assertNotNull(reloadedDraft.getUpdatedAt());
    }

    @Test
    void transferDraftMarkReadyTransitionsStatusAndStampsUpdatedAt() {
        SpAnalysisRun testRun = createTestAnalysisRun();
        SpRebalanceRecommendation recommendation = createTestRecommendation(testRun);
        SpRebalanceDecision decision = decisionRepository.save(
                new SpRebalanceDecision(recommendation, DecisionStatus.APPROVED, 20, "mapping IT", "mapping-it"));
        SpTransferDraft draft = transferDraftRepository.save(new SpTransferDraft(
                decision, "STORE-HONGDAE", "STORE-GANGNAM", "SKU-CAP-BLACK-FREE", 20, "MVP-2-DRAFT-V1"));

        entityManager.flush();
        entityManager.clear();

        SpTransferDraft createdDraft = transferDraftRepository.findByDecision_DecisionId(decision.getDecisionId())
                .orElseThrow();
        assertEquals(DraftStatus.CREATED, createdDraft.getDraftStatus());
        OffsetDateTime createdUpdatedAt = createdDraft.getUpdatedAt();
        assertNotNull(createdUpdatedAt);

        createdDraft.markReady();
        transferDraftRepository.save(createdDraft);
        entityManager.flush();
        entityManager.clear();

        SpTransferDraft readyDraft = transferDraftRepository.findByDecision_DecisionId(decision.getDecisionId())
                .orElseThrow();
        assertEquals(DraftStatus.READY, readyDraft.getDraftStatus());
        // V6 has no update trigger, so updated_at only changes because markReady() sets it
        // explicitly -- this is the fix for the finding that nothing kept it current. Compared
        // for inequality rather than ordering, since the JVM and the Oracle container do not
        // share a clock.
        assertFalse(readyDraft.getUpdatedAt().isEqual(createdUpdatedAt),
                "updated_at must change when markReady() transitions the draft, since Oracle has no update trigger for it");

        assertThrows(IllegalStateException.class, readyDraft::markReady,
                "Only a CREATED draft can be marked READY; a second call must not silently no-op.");
    }

    @Test
    void inventorySnapshotExposesTheV6Mvp2ColumnsAsReadOnly() {
        SpInventorySnapshot snapshot = snapshotRepository.findBySnapshotDate(ANALYSIS_DATE).stream()
                .filter(s -> "STORE-GANGNAM".equals(s.getStoreId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected a Gangnam snapshot in the Golden Scenario."));

        assertNotNull(snapshot.getSnapshotAt());
        assertNotNull(snapshot.getInputSnapshotVersion());
        // isOutOfStock() must not throw regardless of the row's actual flag value.
        assertDoesNotThrow(snapshot::isOutOfStock);
    }

    private SpAnalysisRun createTestAnalysisRun() {
        SpAnalysisRun testRun = analysisRunRepository.save(new SpAnalysisRun(ANALYSIS_DATE, RULE_VERSION));
        testRun.markCompleted();
        return analysisRunRepository.save(testRun);
    }

    private SpRebalanceRecommendation createTestRecommendation(SpAnalysisRun testRun) {
        SpInventorySnapshot gangnamSnapshot = findSnapshot("STORE-GANGNAM");
        SpInventorySnapshot hongdaeSnapshot = findSnapshot("STORE-HONGDAE");

        SpInventoryMetric receiverMetric = metricRepository.save(new SpInventoryMetric(
                testRun, gangnamSnapshot, InventoryMetricCalculation.calculate(6, 1, 28)));
        SpInventoryMetric donorMetric = metricRepository.save(new SpInventoryMetric(
                testRun, hongdaeSnapshot, InventoryMetricCalculation.calculate(42, 2, 4)));
        return recommendationRepository.save(new SpRebalanceRecommendation(
                receiverMetric, donorMetric, new RebalanceCalculation(25, 30, 25)));
    }

    private SpInventorySnapshot findSnapshot(String storeId) {
        return snapshotRepository.findBySnapshotDate(ANALYSIS_DATE).stream()
                .filter(s -> storeId.equals(s.getStoreId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No snapshot found for store " + storeId));
    }
}
