package com.bapegg.stockpilot.rebalance;

import com.bapegg.stockpilot.analysis.InventoryAnalysisRules;
import com.bapegg.stockpilot.analysis.InventoryMetricCalculation;
import com.bapegg.stockpilot.analysis.SpAnalysisRun;
import com.bapegg.stockpilot.analysis.SpAnalysisRunRepository;
import com.bapegg.stockpilot.analysis.SpInventoryMetric;
import com.bapegg.stockpilot.analysis.SpInventoryMetricRepository;
import com.bapegg.stockpilot.api.error.RequestIdFilter;
import com.bapegg.stockpilot.inventory.SpInventorySnapshot;
import com.bapegg.stockpilot.inventory.SpInventorySnapshotRepository;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies {@code POST}/{@code GET /api/rebalancing-decisions}' MVP-2 approval/decision REST
 * slice against the real Oracle instance, per current-task.md sections 1-5. Does not re-verify
 * {@link com.bapegg.stockpilot.approval.ApprovalTransactionExecutor}'s own accepted business
 * logic (lock order, current-basis recalculation, atomicity) -- only that this REST layer routes
 * to it correctly and presents its result/errors per the documented contract. Not
 * {@code @Transactional} -- the lock-timeout scenario needs a genuinely separate, still-open
 * transaction, so every fixture is built via committed repository calls and torn down in a
 * {@code finally} block. Skipped (not failed) when DB_URL is not set.
 */
@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "DB_URL", matches = ".+")
class RebalanceDecisionRestOracleIT {

    private static final LocalDate ANALYSIS_DATE = LocalDate.of(2026, 11, 15);
    private static final LocalDate LEGACY_ANALYSIS_DATE = LocalDate.of(2026, 11, 16);
    private static final String RECEIVER_STORE_ID = "STORE-GANGNAM";
    private static final String DONOR_STORE_ID = "STORE-HONGDAE";
    private static final String SKU_ID = "SKU-CAP-BLACK-FREE";
    private static final String IDEMPOTENCY_HEADER = "Idempotency-Key";
    /** Hand-derived from the fixture shape documented on {@link #buildFixture}. */
    private static final int RECOMMENDED_BASE_QUANTITY = 8;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private tools.jackson.databind.ObjectMapper objectMapper;
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
    private PlatformTransactionManager transactionManager;
    @Autowired
    private EntityManagerFactory entityManagerFactory;

    // ------------------------------------------------------------------
    // POST
    // ------------------------------------------------------------------

    private static final List<String> MVP2_RESPONSE_KEYS =
            List.of("decisionId", "recommendationId", "decisionStatus", "decisionSequence", "transferDraftId", "created");

    @Test
    void heldThenApprovedIsAppendOnlyWithBasisAndDraftOnlyOnApproval() throws Exception {
        Fixture fixture = buildFixture("-HELDTHENAPPROVED", 20);
        try {
            PersistenceState before = capture(fixture);

            MvcResult heldResult = mockMvc.perform(post("/api/rebalancing-decisions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header(IDEMPOTENCY_HEADER, newKey())
                            .content(mvp2Json(fixture, "HELD", null, null, "NEEDS_REVIEW", "awaiting sign-off")))
                    .andExpect(status().isCreated())
                    .andReturn();
            Map<String, Object> held = readMap(heldResult);
            // Exact six-field MVP-2 response contract -- no more, no less.
            assertEquals(new java.util.HashSet<>(MVP2_RESPONSE_KEYS), held.keySet());
            assertNotNull(held.get("decisionId"));
            assertEquals(fixture.recommendationId().intValue(), ((Number) held.get("recommendationId")).intValue());
            assertEquals("HELD", held.get("decisionStatus"));
            assertEquals(1, ((Number) held.get("decisionSequence")).intValue());
            assertNull(held.get("transferDraftId"));
            assertEquals(Boolean.TRUE, held.get("created"));
            assertEquals(POST_PATH + "/" + fixture.recommendationId(), heldResult.getResponse().getHeader("Location"));
            Long heldDecisionId = ((Number) held.get("decisionId")).longValue();
            assertTrue(approvalBasisRepository.findByDecision_DecisionId(heldDecisionId).isEmpty());
            assertTrue(transferDraftRepository.findByDecision_DecisionId(heldDecisionId).isEmpty());
            assertUnchanged(before, capture(fixture));

            MvcResult approvedResult = mockMvc.perform(post("/api/rebalancing-decisions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header(IDEMPOTENCY_HEADER, newKey())
                            .content(mvp2Json(fixture, "APPROVED", RECOMMENDED_BASE_QUANTITY, false, null, null)))
                    .andExpect(status().isCreated())
                    .andReturn();
            Map<String, Object> approved = readMap(approvedResult);
            assertEquals(new java.util.HashSet<>(MVP2_RESPONSE_KEYS), approved.keySet());
            assertNotNull(approved.get("decisionId"));
            assertNotEquals(held.get("decisionId"), approved.get("decisionId"),
                    "The second decision must be a new row, not the HELD one.");
            assertEquals(fixture.recommendationId().intValue(), ((Number) approved.get("recommendationId")).intValue());
            assertEquals("APPROVED", approved.get("decisionStatus"));
            assertEquals(2, ((Number) approved.get("decisionSequence")).intValue());
            assertNotNull(approved.get("transferDraftId"));
            assertEquals(Boolean.TRUE, approved.get("created"));
            assertEquals(POST_PATH + "/" + fixture.recommendationId(), approvedResult.getResponse().getHeader("Location"));
            assertUnchanged(before, capture(fixture));

            // Atomic row state: decision + basis + draft for the APPROVED row, referencing each other.
            Long approvedDecisionId = ((Number) approved.get("decisionId")).longValue();
            Long approvedDraftId = ((Number) approved.get("transferDraftId")).longValue();
            SpApprovalBasis basis = approvalBasisRepository.findByDecision_DecisionId(approvedDecisionId).orElseThrow();
            assertEquals(fixture.analysisRunId(), basis.getAnalysisRun().getAnalysisRunId());
            assertEquals(fixture.inputSnapshotVersion(), basis.getInputSnapshotVersion());
            assertEquals(fixture.ruleVersion(), basis.getRuleVersion());
            assertEquals(RECOMMENDED_BASE_QUANTITY, basis.getRecommendedBaseQuantity());
            SpTransferDraft draft = transferDraftRepository.findByDecision_DecisionId(approvedDecisionId).orElseThrow();
            assertEquals(approvedDraftId, draft.getTransferDraftId());
            assertEquals(DONOR_STORE_ID, draft.getDonorStoreId());
            assertEquals(RECEIVER_STORE_ID, draft.getReceiverStoreId());
            assertEquals(RECOMMENDED_BASE_QUANTITY, draft.getQuantity());
            assertEquals(DraftStatus.CREATED, draft.getDraftStatus());

            assertEquals(2, decisionRepository
                    .findAllByRecommendation_RecommendationIdOrderByDecisionSequenceAsc(fixture.recommendationId())
                    .size());
        } finally {
            cleanupFixture(fixture);
        }
    }

    @Test
    void approvingExactlyTheRecommendedBaseQuantityNeedsNoReason() throws Exception {
        Fixture fixture = buildFixture("-EXACTBASE", 20);
        try {
            mockMvc.perform(post("/api/rebalancing-decisions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header(IDEMPOTENCY_HEADER, newKey())
                            .content(mvp2Json(fixture, "APPROVED", RECOMMENDED_BASE_QUANTITY, false, null, null)))
                    .andExpect(status().isCreated());
        } finally {
            cleanupFixture(fixture);
        }
    }

    @Test
    void aChangedQuantityWithoutAReasonIsRejectedAsInvalid() throws Exception {
        Fixture fixture = buildFixture("-CHANGEDNOREASON", 20);
        try {
            PersistenceState before = capture(fixture);
            MvcResult result = mockMvc.perform(post("/api/rebalancing-decisions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header(IDEMPOTENCY_HEADER, newKey())
                            .content(mvp2Json(fixture, "APPROVED", RECOMMENDED_BASE_QUANTITY - 1, false, null, null)))
                    .andExpect(status().isBadRequest())
                    .andReturn();
            assertPostProblemDetail(result, readMap(result), 400, "INVALID_DECISION_REQUEST", false,
                    fixture.recommendationId());
            assertFalse(decisionRepository.existsByRecommendation_RecommendationId(fixture.recommendationId()));
            assertUnchanged(before, capture(fixture));
        } finally {
            cleanupFixture(fixture);
        }
    }

    @Test
    void sameKeyAndSamePayloadReplaysWithoutANewRow() throws Exception {
        Fixture fixture = buildFixture("-REPLAY", 20);
        try {
            String key = newKey();
            String body = mvp2Json(fixture, "HELD", null, null, "NEEDS_REVIEW", "awaiting sign-off");

            Map<String, Object> first = readMap(mockMvc.perform(post("/api/rebalancing-decisions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header(IDEMPOTENCY_HEADER, key)
                            .content(body))
                    .andExpect(status().isCreated())
                    .andReturn());
            assertEquals(new java.util.HashSet<>(MVP2_RESPONSE_KEYS), first.keySet());
            assertNotNull(first.get("decisionId"));
            assertEquals(fixture.recommendationId().intValue(), ((Number) first.get("recommendationId")).intValue());
            assertEquals("HELD", first.get("decisionStatus"));
            assertEquals(1, ((Number) first.get("decisionSequence")).intValue());
            assertNull(first.get("transferDraftId"));
            assertEquals(Boolean.TRUE, first.get("created"));
            PersistenceState afterFirst = capture(fixture);

            MvcResult replayResult = mockMvc.perform(post("/api/rebalancing-decisions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header(IDEMPOTENCY_HEADER, key)
                            .content(body))
                    .andExpect(status().isOk())
                    .andReturn();
            Map<String, Object> replay = readMap(replayResult);

            // Replay is still a full, exact six-field response with the same success Location rule
            // -- every field, not just the ones that differ from a fresh create.
            assertEquals(new java.util.HashSet<>(MVP2_RESPONSE_KEYS), replay.keySet());
            assertEquals(first.get("decisionId"), replay.get("decisionId"));
            assertEquals(fixture.recommendationId().intValue(), ((Number) replay.get("recommendationId")).intValue());
            assertEquals("HELD", replay.get("decisionStatus"));
            assertEquals(first.get("decisionSequence"), replay.get("decisionSequence"));
            assertEquals(first.get("transferDraftId"), replay.get("transferDraftId"));
            assertEquals(Boolean.FALSE, replay.get("created"));
            assertEquals(POST_PATH + "/" + fixture.recommendationId(), replayResult.getResponse().getHeader("Location"));
            assertEquals(1, decisionRepository
                    .findAllByRecommendation_RecommendationIdOrderByDecisionSequenceAsc(fixture.recommendationId())
                    .size());
            assertUnchanged(afterFirst, capture(fixture));
        } finally {
            cleanupFixture(fixture);
        }
    }

    @Test
    void sameKeyWithADifferentPayloadIsRejectedAsReused() throws Exception {
        Fixture fixture = buildFixture("-KEYREUSE", 20);
        try {
            String key = newKey();
            mockMvc.perform(post("/api/rebalancing-decisions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header(IDEMPOTENCY_HEADER, key)
                            .content(mvp2Json(fixture, "HELD", null, null, "NEEDS_REVIEW", "awaiting sign-off")))
                    .andExpect(status().isCreated());
            PersistenceState afterFirst = capture(fixture);

            MvcResult result = mockMvc.perform(post("/api/rebalancing-decisions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header(IDEMPOTENCY_HEADER, key)
                            .content(mvp2Json(fixture, "REJECTED", null, null, "NOT_NEEDED", "no longer needed")))
                    .andExpect(status().isConflict())
                    .andReturn();
            assertPostProblemDetail(result, readMap(result), 409, "IDEMPOTENCY_KEY_REUSED", false, key);
            assertEquals(1, decisionRepository
                    .findAllByRecommendation_RecommendationIdOrderByDecisionSequenceAsc(fixture.recommendationId())
                    .size(), "The rejected different-payload replay must not add a second row.");
            assertUnchanged(afterFirst, capture(fixture));
        } finally {
            cleanupFixture(fixture);
        }
    }

    @Test
    void staleVersionTupleIsRejected() throws Exception {
        Fixture fixture = buildFixture("-STALETUPLE", 20);
        try {
            PersistenceState before = capture(fixture);
            String body = "{\"recommendationId\":" + fixture.recommendationId()
                    + ",\"decisionStatus\":\"HELD\",\"actorLabel\":\"it\""
                    + ",\"reasonCode\":\"NEEDS_REVIEW\",\"reason\":\"awaiting sign-off\""
                    + ",\"analysisRunId\":" + fixture.analysisRunId()
                    + ",\"inputSnapshotVersion\":\"" + fixture.inputSnapshotVersion() + "\""
                    + ",\"ruleVersion\":\"WRONG-RULE-VERSION\",\"candidateVersion\":1}";
            MvcResult result = mockMvc.perform(post("/api/rebalancing-decisions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header(IDEMPOTENCY_HEADER, newKey())
                            .content(body))
                    .andExpect(status().isConflict())
                    .andReturn();
            assertPostProblemDetail(result, readMap(result), 409, "STALE_RECOMMENDATION", false, fixture.recommendationId());
            assertFalse(decisionRepository.existsByRecommendation_RecommendationId(fixture.recommendationId()));
            assertUnchanged(before, capture(fixture));
        } finally {
            cleanupFixture(fixture);
        }
    }

    @Test
    void terminalDecisionIsRejected() throws Exception {
        Fixture fixture = buildFixture("-TERMINAL", 20);
        try {
            mockMvc.perform(post("/api/rebalancing-decisions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header(IDEMPOTENCY_HEADER, newKey())
                            .content(mvp2Json(fixture, "APPROVED", RECOMMENDED_BASE_QUANTITY, false, null, null)))
                    .andExpect(status().isCreated());
            PersistenceState afterApproved = capture(fixture);

            MvcResult result = mockMvc.perform(post("/api/rebalancing-decisions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header(IDEMPOTENCY_HEADER, newKey())
                            .content(mvp2Json(fixture, "REJECTED", null, null, "NOT_NEEDED", "no longer needed")))
                    .andExpect(status().isConflict())
                    .andReturn();
            assertPostProblemDetail(result, readMap(result), 409, "DECISION_ALREADY_TERMINAL", false,
                    fixture.recommendationId());
            assertEquals(1, decisionRepository
                    .findAllByRecommendation_RecommendationIdOrderByDecisionSequenceAsc(fixture.recommendationId())
                    .size());
            assertUnchanged(afterApproved, capture(fixture));
        } finally {
            cleanupFixture(fixture);
        }
    }

    @Test
    void unknownRecommendationIsNotFound() throws Exception {
        String body = "{\"recommendationId\":999999999,\"decisionStatus\":\"HELD\",\"actorLabel\":\"it\""
                + ",\"reasonCode\":\"NEEDS_REVIEW\",\"reason\":\"awaiting sign-off\""
                + ",\"analysisRunId\":1,\"inputSnapshotVersion\":\"X\",\"ruleVersion\":\"MVP-2\","
                + "\"candidateVersion\":1}";
        MvcResult result = mockMvc.perform(post("/api/rebalancing-decisions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(IDEMPOTENCY_HEADER, newKey())
                        .content(body))
                .andExpect(status().isNotFound())
                .andReturn();
        assertPostProblemDetail(result, readMap(result), 404, "RECOMMENDATION_NOT_FOUND", false, 999999999L);
    }

    @Test
    void partialVersionTupleIsRejectedAsInvalidDecisionRequest() throws Exception {
        Fixture fixture = buildFixture("-PARTIAL", 20);
        try {
            PersistenceState before = capture(fixture);
            String body = "{\"recommendationId\":" + fixture.recommendationId()
                    + ",\"decisionStatus\":\"HELD\",\"actorLabel\":\"it\""
                    + ",\"reasonCode\":\"NEEDS_REVIEW\",\"reason\":\"awaiting sign-off\""
                    + ",\"analysisRunId\":" + fixture.analysisRunId() + "}";
            MvcResult result = mockMvc.perform(post("/api/rebalancing-decisions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header(IDEMPOTENCY_HEADER, newKey())
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andReturn();
            assertPostProblemDetail(result, readMap(result), 400, "INVALID_DECISION_REQUEST", false,
                    fixture.recommendationId());
            assertFalse(decisionRepository.existsByRecommendation_RecommendationId(fixture.recommendationId()));
            assertUnchanged(before, capture(fixture));
        } finally {
            cleanupFixture(fixture);
        }
    }

    @Test
    void donorLockHeldByAnotherTransactionCausesA503LockTimeout() throws Exception {
        Fixture fixture = buildFixture("-LOCKTIMEOUT", 20);
        ExecutorService holderExecutor = Executors.newSingleThreadExecutor();
        CountDownLatch lockAcquired = new CountDownLatch(1);
        CountDownLatch releaseLock = new CountDownLatch(1);
        try {
            PersistenceState before = capture(fixture);
            TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
            Future<?> holderFuture = holderExecutor.submit(() -> transactionTemplate.executeWithoutResult(status -> {
                snapshotRepository.lockById(fixture.donorSnapshotId()).orElseThrow();
                lockAcquired.countDown();
                try {
                    releaseLock.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }));
            assertTrue(lockAcquired.await(5, TimeUnit.SECONDS), "Holder transaction failed to acquire the donor lock.");

            MvcResult result = mockMvc.perform(post("/api/rebalancing-decisions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header(IDEMPOTENCY_HEADER, newKey())
                            .content(mvp2Json(fixture, "APPROVED", RECOMMENDED_BASE_QUANTITY, false, null, null)))
                    .andExpect(status().is(503))
                    .andReturn();
            assertPostProblemDetail(result, readMap(result), 503, "APPROVAL_LOCK_TIMEOUT", true, fixture.recommendationId());

            releaseLock.countDown();
            holderFuture.get(10, TimeUnit.SECONDS);
            assertFalse(decisionRepository.existsByRecommendation_RecommendationId(fixture.recommendationId()));
            assertUnchanged(before, capture(fixture));
        } finally {
            holderExecutor.shutdownNow();
            cleanupFixture(fixture);
        }
    }

    private static final List<String> LEGACY_RESPONSE_KEYS =
            List.of("decisionId", "recommendationId", "decisionStatus", "selectedQuantity", "reason", "actorLabel",
                    "decidedAt");

    @Test
    void legacyBodyStillGetsTheExactMvp1SuccessContractAnd201() throws Exception {
        LegacyFixture fixture = buildLegacyFixture();
        try {
            String body = "{\"recommendationId\":" + fixture.recommendationId()
                    + ",\"decisionStatus\":\"APPROVED\",\"selectedQuantity\":5"
                    + ",\"reason\":\"legacy approval\",\"actorLabel\":\"it\"}";
            MvcResult result = mockMvc.perform(post("/api/rebalancing-decisions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isCreated())
                    .andReturn();
            assertNull(result.getResponse().getHeader("Location"),
                    "Legacy success must not gain a Location header.");
            Map<String, Object> response = readMap(result);
            // Exact legacy key set -- byte-compatible with the pre-existing MVP-1 contract, no
            // version-tuple fields ever echoed back.
            assertEquals(new java.util.HashSet<>(LEGACY_RESPONSE_KEYS), response.keySet());
            assertNotNull(response.get("decisionId"));
            assertEquals(fixture.recommendationId().intValue(), ((Number) response.get("recommendationId")).intValue());
            assertEquals("APPROVED", response.get("decisionStatus"));
            assertEquals(5, ((Number) response.get("selectedQuantity")).intValue());
            assertEquals("legacy approval", response.get("reason"));
            assertEquals("it", response.get("actorLabel"));
            // decidedAt is DB-DEFAULT-stamped (insertable=false) and this response is built from
            // the same in-memory entity save() returned, without a post-insert refresh -- it is
            // null here by design (pre-existing behavior); GET re-reads the row fresh and sees it.
            assertNull(response.get("decidedAt"));
        } finally {
            cleanupLegacyFixture(fixture);
        }
    }

    @Test
    void aNonMvp1RecommendationCannotBeDecidedThroughTheLegacyTupleLessPath() throws Exception {
        Fixture fixture = buildFixture("-NONMVP1LEGACY", 20);
        try {
            PersistenceState before = capture(fixture);
            String body = "{\"recommendationId\":" + fixture.recommendationId()
                    + ",\"decisionStatus\":\"APPROVED\",\"selectedQuantity\":" + RECOMMENDED_BASE_QUANTITY
                    + ",\"reason\":\"should be rejected\",\"actorLabel\":\"it\"}";
            MvcResult result = mockMvc.perform(post("/api/rebalancing-decisions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andReturn();
            assertPostProblemDetail(result, readMap(result), 400, "INVALID_DECISION_REQUEST", false,
                    fixture.recommendationId());
            assertFalse(decisionRepository.existsByRecommendation_RecommendationId(fixture.recommendationId()));
            assertUnchanged(before, capture(fixture));
        } finally {
            cleanupFixture(fixture);
        }
    }

    /**
     * Regression for Codex Finding P2 ("common/legacy input validation occurs in the wrong layer
     * and order"): a non-positive {@code recommendationId} must be a common-field
     * {@code VALIDATION_ERROR} before any routing/DB access, for both legacy and MVP-2 shaped
     * bodies -- not {@code RECOMMENDATION_NOT_FOUND} (legacy previously reached the repository)
     * or {@code INVALID_DECISION_REQUEST} (MVP-2 previously reached the command constructor).
     */
    @Test
    void aNonPositiveRecommendationIdOnALegacyBodyIsCommonValidationError() throws Exception {
        String body = "{\"recommendationId\":0,\"decisionStatus\":\"APPROVED\",\"selectedQuantity\":5"
                + ",\"reason\":\"n/a\",\"actorLabel\":\"it\"}";
        MvcResult result = mockMvc.perform(post("/api/rebalancing-decisions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andReturn();
        assertPostValidationProblemDetail(result, readMap(result), "recommendationId", "FORMAT");
    }

    @Test
    void aNonPositiveRecommendationIdOnAnMvp2BodyIsCommonValidationError() throws Exception {
        String body = "{\"recommendationId\":-1,\"decisionStatus\":\"HELD\",\"actorLabel\":\"it\""
                + ",\"reasonCode\":\"NEEDS_REVIEW\",\"reason\":\"n/a\""
                + ",\"analysisRunId\":1,\"inputSnapshotVersion\":\"X\",\"ruleVersion\":\"MVP-2\","
                + "\"candidateVersion\":1}";
        MvcResult result = mockMvc.perform(post("/api/rebalancing-decisions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(IDEMPOTENCY_HEADER, newKey())
                        .content(body))
                .andExpect(status().isBadRequest())
                .andReturn();
        assertPostValidationProblemDetail(result, readMap(result), "recommendationId", "FORMAT");
    }

    /**
     * Per section 1.1: an explicit {@code policyException=true} always needs a reason code and
     * reason, even at the exact recommended BASE quantity -- and succeeds once one is supplied.
     */
    @Test
    void policyExceptionTrueWithoutAReasonIsRejectedAsInvalid() throws Exception {
        Fixture fixture = buildFixture("-POLICYEXCNOREASON", 20);
        try {
            PersistenceState before = capture(fixture);
            MvcResult result = mockMvc.perform(post("/api/rebalancing-decisions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header(IDEMPOTENCY_HEADER, newKey())
                            .content(mvp2Json(fixture, "APPROVED", RECOMMENDED_BASE_QUANTITY, true, null, null)))
                    .andExpect(status().isBadRequest())
                    .andReturn();
            assertPostProblemDetail(result, readMap(result), 400, "INVALID_DECISION_REQUEST", false,
                    fixture.recommendationId());
            assertFalse(decisionRepository.existsByRecommendation_RecommendationId(fixture.recommendationId()));
            assertUnchanged(before, capture(fixture));
        } finally {
            cleanupFixture(fixture);
        }
    }

    @Test
    void policyExceptionTrueWithAReasonSucceeds() throws Exception {
        Fixture fixture = buildFixture("-POLICYEXCWITHREASON", 20);
        try {
            Map<String, Object> body = readMap(mockMvc.perform(post("/api/rebalancing-decisions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header(IDEMPOTENCY_HEADER, newKey())
                            .content(mvp2Json(fixture, "APPROVED", RECOMMENDED_BASE_QUANTITY, true,
                                    "MANUAL_OVERRIDE", "explicit policy exception")))
                    .andExpect(status().isCreated())
                    .andReturn());
            assertEquals("APPROVED", body.get("decisionStatus"));
        } finally {
            cleanupFixture(fixture);
        }
    }

    /**
     * Regression for Codex Finding P2 ("a known legacy decision-sequence race is exposed as
     * INTERNAL_SERVER_ERROR"): the legacy writer performs its exists-check and insert with no
     * lock, so two concurrent requests for the same never-decided recommendation can both pass
     * the check and race to insert {@code decision_sequence=1}. The loser's
     * {@code UQ_SP_DEC_REC_SEQ} violation must resolve to the catalog's {@code DECISION_CONFLICT}
     * 409, not an unclassified 500. Mirrors {@code ApprovalTransactionConcurrencyIT}'s
     * {@code CyclicBarrier} technique for starting both callers together.
     */
    @Test
    void concurrentLegacyRequestsRaceAndTheLoserIsRejectedAsDecisionConflict() throws Exception {
        // Must be an exact-MVP-1 fixture: RebalanceDecisionService's legacy path only runs at all
        // for a recommendation whose run's rule version is exactly "MVP-1" -- buildFixture's
        // "MVP-2-RDROIT..." rule version would be rejected as INVALID_DECISION_REQUEST before the
        // race ever had a chance to happen.
        LegacyFixture fixture = buildLegacyFixture();
        try {
            String bodyA = "{\"recommendationId\":" + fixture.recommendationId()
                    + ",\"decisionStatus\":\"APPROVED\",\"selectedQuantity\":8"
                    + ",\"reason\":\"race A\",\"actorLabel\":\"it-a\"}";
            String bodyB = "{\"recommendationId\":" + fixture.recommendationId()
                    + ",\"decisionStatus\":\"REJECTED\",\"selectedQuantity\":8"
                    + ",\"reason\":\"race B\",\"actorLabel\":\"it-b\"}";

            int n = 2;
            ExecutorService executor = Executors.newFixedThreadPool(n);
            java.util.concurrent.CyclicBarrier barrier = new java.util.concurrent.CyclicBarrier(n);
            List<Future<MvcResult>> futures = new java.util.ArrayList<>();
            try {
                for (String requestBody : List.of(bodyA, bodyB)) {
                    futures.add(executor.submit(() -> {
                        barrier.await();
                        return mockMvc.perform(post("/api/rebalancing-decisions")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(requestBody))
                                .andReturn();
                    }));
                }
                List<MvcResult> results = new java.util.ArrayList<>();
                for (Future<MvcResult> future : futures) {
                    results.add(future.get(30, TimeUnit.SECONDS));
                }

                long successCount = results.stream().filter(r -> r.getResponse().getStatus() == 201).count();
                long conflictCount = results.stream().filter(r -> r.getResponse().getStatus() == 409).count();
                assertEquals(1, successCount, "Exactly one of the two unlocked concurrent legacy requests should win.");
                assertEquals(1, conflictCount, "The loser must be rejected, not silently dropped or 500.");

                MvcResult conflictResult = results.stream().filter(r -> r.getResponse().getStatus() == 409).findFirst()
                        .orElseThrow();
                assertPostProblemDetail(conflictResult, readMap(conflictResult), 409, "DECISION_CONFLICT", false,
                        fixture.recommendationId());
                assertEquals(1, decisionRepository
                        .findAllByRecommendation_RecommendationIdOrderByDecisionSequenceAsc(fixture.recommendationId())
                        .size());
            } finally {
                executor.shutdownNow();
            }
        } finally {
            cleanupLegacyFixture(fixture);
        }
    }

    // ------------------------------------------------------------------
    // GET
    // ------------------------------------------------------------------

    private static final List<String> GET_RESPONSE_KEYS = List.of("recommendationId", "currentStatus", "decisions");
    private static final List<String> DECISION_ITEM_KEYS = List.of(
            "decisionId", "decisionSequence", "decisionStatus", "selectedQuantity", "policyException", "reasonCode",
            "reason", "actorLabel", "recommendationVersion", "decisionContractVersion", "decidedAt", "approvalBasis",
            "transferDraft");
    private static final List<String> APPROVAL_BASIS_ITEM_KEYS = List.of(
            "approvalBasisId", "analysisRunId", "inputSnapshotVersion", "ruleVersion", "candidateVersion",
            "candidateEligible", "recommendedBaseQuantity", "donorTransferableQuantity", "routeMinimumQuantity",
            "packageMultiple", "routeMaximumQuantity", "receiverCapacityRemaining", "receiverProjectedBeforeDemand",
            "donorProjectedAtDispatch", "alreadyApprovedDraftQuantity", "basisContractVersion", "createdAt");
    private static final List<String> TRANSFER_DRAFT_ITEM_KEYS = List.of(
            "transferDraftId", "donorStoreId", "receiverStoreId", "skuId", "quantity", "draftStatus",
            "externalReference", "payloadVersion", "createdAt", "updatedAt");

    @Test
    void noDecisionYetReturnsPendingWithAnEmptyArray() throws Exception {
        Fixture fixture = buildFixture("-GETPENDING", 20);
        try {
            Map<String, Object> body = readMap(mockMvc.perform(get("/api/rebalancing-decisions/{id}",
                            fixture.recommendationId()))
                    .andExpect(status().isOk())
                    .andReturn());
            assertEquals(new java.util.HashSet<>(GET_RESPONSE_KEYS), body.keySet());
            assertEquals(fixture.recommendationId().intValue(), ((Number) body.get("recommendationId")).intValue());
            assertEquals("PENDING", body.get("currentStatus"));
            assertEquals(List.of(), body.get("decisions"));
        } finally {
            cleanupFixture(fixture);
        }
    }

    @Test
    void heldThenApprovedHistoryIsOrderedWithExactBasisAndDraftValues() throws Exception {
        Fixture fixture = buildFixture("-GETHISTORY", 20);
        try {
            mockMvc.perform(post("/api/rebalancing-decisions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header(IDEMPOTENCY_HEADER, newKey())
                            .content(mvp2Json(fixture, "HELD", null, null, "NEEDS_REVIEW", "awaiting sign-off")))
                    .andExpect(status().isCreated());
            mockMvc.perform(post("/api/rebalancing-decisions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header(IDEMPOTENCY_HEADER, newKey())
                            .content(mvp2Json(fixture, "APPROVED", RECOMMENDED_BASE_QUANTITY, false, null, null)))
                    .andExpect(status().isCreated());

            Map<String, Object> body = readMap(mockMvc.perform(get("/api/rebalancing-decisions/{id}",
                            fixture.recommendationId()))
                    .andExpect(status().isOk())
                    .andReturn());

            assertEquals(new java.util.HashSet<>(GET_RESPONSE_KEYS), body.keySet());
            assertEquals(fixture.recommendationId().intValue(), ((Number) body.get("recommendationId")).intValue());
            assertEquals("APPROVED", body.get("currentStatus"));
            List<Map<String, Object>> decisions = (List<Map<String, Object>>) body.get("decisions");
            assertEquals(2, decisions.size());

            Map<String, Object> held = decisions.get(0);
            assertEquals(new java.util.HashSet<>(DECISION_ITEM_KEYS), held.keySet());
            assertNotNull(held.get("decisionId"));
            assertEquals(1, ((Number) held.get("decisionSequence")).intValue());
            assertEquals("HELD", held.get("decisionStatus"));
            assertNull(held.get("selectedQuantity"));
            assertEquals(Boolean.FALSE, held.get("policyException"));
            assertEquals("NEEDS_REVIEW", held.get("reasonCode"));
            assertEquals("awaiting sign-off", held.get("reason"));
            assertEquals("it", held.get("actorLabel"));
            assertEquals(1, ((Number) held.get("recommendationVersion")).intValue());
            assertEquals("MVP-2", held.get("decisionContractVersion"));
            assertNull(held.get("approvalBasis"));
            assertNull(held.get("transferDraft"));
            assertNotNull(held.get("decidedAt"));

            Map<String, Object> approved = decisions.get(1);
            assertEquals(new java.util.HashSet<>(DECISION_ITEM_KEYS), approved.keySet());
            assertNotNull(approved.get("decisionId"));
            assertEquals(2, ((Number) approved.get("decisionSequence")).intValue());
            assertEquals("APPROVED", approved.get("decisionStatus"));
            assertEquals(RECOMMENDED_BASE_QUANTITY, ((Number) approved.get("selectedQuantity")).intValue());
            assertEquals(Boolean.FALSE, approved.get("policyException"));
            assertNull(approved.get("reasonCode"));
            assertNull(approved.get("reason"));
            assertEquals("it", approved.get("actorLabel"));
            assertEquals(1, ((Number) approved.get("recommendationVersion")).intValue());
            assertEquals("MVP-2", approved.get("decisionContractVersion"));
            assertNotNull(approved.get("decidedAt"));

            // Every stored approval-basis field, at the exact fixture-derived value (donor
            // available=40, donorProtected=ceil(1*7)=7 -> donorTransferableQuantity=33; receiver
            // available=5, maxCapacity=1000 -> receiverCapacityRemaining=995; route min=1/pkg=1/
            // max=20; no prior draft -> alreadyApprovedDraftQuantity=0), not a selected subset.
            Map<String, Object> basis = (Map<String, Object>) approved.get("approvalBasis");
            assertNotNull(basis);
            assertEquals(new java.util.HashSet<>(APPROVAL_BASIS_ITEM_KEYS), basis.keySet());
            assertNotNull(basis.get("approvalBasisId"));
            assertEquals(fixture.analysisRunId().longValue(), ((Number) basis.get("analysisRunId")).longValue());
            assertEquals(fixture.inputSnapshotVersion(), basis.get("inputSnapshotVersion"));
            assertEquals(fixture.ruleVersion(), basis.get("ruleVersion"));
            assertEquals(1, ((Number) basis.get("candidateVersion")).intValue());
            assertEquals(Boolean.TRUE, basis.get("candidateEligible"));
            assertEquals(RECOMMENDED_BASE_QUANTITY, ((Number) basis.get("recommendedBaseQuantity")).intValue());
            assertEquals(33, ((Number) basis.get("donorTransferableQuantity")).intValue());
            assertEquals(1, ((Number) basis.get("routeMinimumQuantity")).intValue());
            assertEquals(1, ((Number) basis.get("packageMultiple")).intValue());
            assertEquals(20, ((Number) basis.get("routeMaximumQuantity")).intValue());
            assertEquals(995, ((Number) basis.get("receiverCapacityRemaining")).intValue());
            assertEquals(5, ((Number) basis.get("receiverProjectedBeforeDemand")).intValue());
            assertEquals(40, ((Number) basis.get("donorProjectedAtDispatch")).intValue());
            assertEquals(0, ((Number) basis.get("alreadyApprovedDraftQuantity")).intValue());
            assertEquals("MVP-2", basis.get("basisContractVersion"));
            assertNotNull(basis.get("createdAt"));

            Map<String, Object> draft = (Map<String, Object>) approved.get("transferDraft");
            assertNotNull(draft);
            assertEquals(new java.util.HashSet<>(TRANSFER_DRAFT_ITEM_KEYS), draft.keySet());
            assertNotNull(draft.get("transferDraftId"));
            assertEquals(DONOR_STORE_ID, draft.get("donorStoreId"));
            assertEquals(RECEIVER_STORE_ID, draft.get("receiverStoreId"));
            assertEquals(SKU_ID, draft.get("skuId"));
            assertEquals(RECOMMENDED_BASE_QUANTITY, ((Number) draft.get("quantity")).intValue());
            assertEquals("CREATED", draft.get("draftStatus"));
            assertNull(draft.get("externalReference"));
            assertEquals("MVP-2-DRAFT-V1", draft.get("payloadVersion"));
            assertNotNull(draft.get("createdAt"));
            // updated_at has an insert-time DB DEFAULT too (like created_at); only a later UPDATE
            // needs markReady() to keep it current, per SpTransferDraft's own javadoc -- at
            // creation time it is simply non-null, same as created_at.
            assertNotNull(draft.get("updatedAt"));

            // Neither the Idempotency-Key nor its fingerprint is ever exposed.
            String rawJson = objectMapper.writeValueAsString(body).toLowerCase();
            assertFalse(rawJson.contains("idempotency"));
            assertFalse(rawJson.contains("fingerprint"));
            assertFalse(rawJson.contains("decisionrequestid"));
        } finally {
            cleanupFixture(fixture);
        }
    }

    @Test
    void anExistingMvp1DecisionReadsWithNullBasisAndDraft() throws Exception {
        LegacyFixture fixture = buildLegacyFixture();
        try {
            String body = "{\"recommendationId\":" + fixture.recommendationId()
                    + ",\"decisionStatus\":\"APPROVED\",\"selectedQuantity\":5"
                    + ",\"reason\":\"legacy approval\",\"actorLabel\":\"it\"}";
            mockMvc.perform(post("/api/rebalancing-decisions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isCreated());

            Map<String, Object> history = readMap(mockMvc.perform(get("/api/rebalancing-decisions/{id}",
                            fixture.recommendationId()))
                    .andExpect(status().isOk())
                    .andReturn());
            assertEquals(new java.util.HashSet<>(GET_RESPONSE_KEYS), history.keySet());
            assertEquals(fixture.recommendationId().intValue(), ((Number) history.get("recommendationId")).intValue());
            assertEquals("APPROVED", history.get("currentStatus"));
            List<Map<String, Object>> decisions = (List<Map<String, Object>>) history.get("decisions");
            assertEquals(1, decisions.size());
            Map<String, Object> legacyItem = decisions.get(0);
            assertEquals(new java.util.HashSet<>(DECISION_ITEM_KEYS), legacyItem.keySet());
            assertEquals("MVP-1", legacyItem.get("decisionContractVersion"));
            assertNull(legacyItem.get("approvalBasis"));
            assertNull(legacyItem.get("transferDraft"));
        } finally {
            cleanupLegacyFixture(fixture);
        }
    }

    @Test
    void unknownRecommendationGetIsNotFound() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/rebalancing-decisions/{id}", 999999999L))
                .andExpect(status().isNotFound())
                .andReturn();
        assertProblemDetail(result, readMap(result), 404, "RECOMMENDATION_NOT_FOUND", false,
                "/api/rebalancing-decisions/999999999", 999999999L);
    }

    @Test
    void aNonPositivePathVariableIsValidationError() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/rebalancing-decisions/{id}", 0))
                .andExpect(status().isBadRequest())
                .andReturn();
        assertValidationProblemDetail(result, readMap(result), "/api/rebalancing-decisions/0",
                "recommendationId", "FORMAT");
    }

    @Test
    void aPhysicallyCorruptMvp2ShapeIsInternalServerError() throws Exception {
        Fixture fixture = buildFixture("-CORRUPT", 20);
        try {
            mockMvc.perform(post("/api/rebalancing-decisions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header(IDEMPOTENCY_HEADER, newKey())
                            .content(mvp2Json(fixture, "HELD", null, null, "NEEDS_REVIEW", "awaiting sign-off")))
                    .andExpect(status().isCreated());
            // Corrupt the physical row directly: an MVP-2 HELD decision must never carry an
            // approval basis. This bypasses every Java-layer guarantee on purpose, to prove the
            // read side's own defensive check (not just the write side) catches the violation.
            Long decisionId = decisionRepository
                    .findAllByRecommendation_RecommendationIdOrderByDecisionSequenceAsc(fixture.recommendationId())
                    .get(0).getDecisionId();
            SpAnalysisRun analysisRun = analysisRunRepository.findById(fixture.analysisRunId()).orElseThrow();
            jdbcTemplate.update(
                    "INSERT INTO sp_approval_basis (decision_id, analysis_run_id, input_snapshot_version, "
                            + "rule_version, candidate_version, candidate_eligible_flag, recommended_base_quantity, "
                            + "donor_transferable_quantity, route_minimum_quantity, package_multiple, "
                            + "route_maximum_quantity, receiver_capacity_remaining, basis_contract_version, "
                            + "receiver_projected_before_demand, donor_projected_at_dispatch, "
                            + "already_approved_draft_quantity) "
                            + "VALUES (?, ?, ?, ?, 1, 'Y', 8, 33, 1, 1, 20, 995, 'MVP-2', 5, 40, 0)",
                    decisionId, fixture.analysisRunId(), fixture.inputSnapshotVersion(), fixture.ruleVersion());

            MvcResult result = mockMvc.perform(get("/api/rebalancing-decisions/{id}", fixture.recommendationId()))
                    .andExpect(status().isInternalServerError())
                    .andReturn();
            assertProblemDetail(result, readMap(result), 500, "INTERNAL_SERVER_ERROR", false,
                    "/api/rebalancing-decisions/" + fixture.recommendationId(), fixture.recommendationId());
        } finally {
            jdbcTemplate.update("DELETE FROM sp_approval_basis WHERE decision_id IN "
                    + "(SELECT decision_id FROM sp_rebalance_decision WHERE recommendation_id = ?)",
                    fixture.recommendationId());
            cleanupFixture(fixture);
        }
    }

    @Test
    void statementCountStaysAtMostFourRegardlessOfHistoryLength() throws Exception {
        Fixture fixture = buildFixture("-STMTCOUNT", 20);
        try {
            Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();

            statistics.clear();
            mockMvc.perform(get("/api/rebalancing-decisions/{id}", fixture.recommendationId()))
                    .andExpect(status().isOk());
            long zeroDecisionCount = statistics.getPrepareStatementCount();
            assertTrue(zeroDecisionCount <= 4, "Zero-decision statement count was " + zeroDecisionCount);

            mockMvc.perform(post("/api/rebalancing-decisions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header(IDEMPOTENCY_HEADER, newKey())
                            .content(mvp2Json(fixture, "HELD", null, null, "NEEDS_REVIEW", "awaiting sign-off")))
                    .andExpect(status().isCreated());
            statistics.clear();
            mockMvc.perform(get("/api/rebalancing-decisions/{id}", fixture.recommendationId()))
                    .andExpect(status().isOk());
            long oneDecisionCount = statistics.getPrepareStatementCount();
            assertTrue(oneDecisionCount <= 4, "One-decision statement count was " + oneDecisionCount);

            mockMvc.perform(post("/api/rebalancing-decisions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header(IDEMPOTENCY_HEADER, newKey())
                            .content(mvp2Json(fixture, "APPROVED", RECOMMENDED_BASE_QUANTITY, false, null, null)))
                    .andExpect(status().isCreated());
            statistics.clear();
            mockMvc.perform(get("/api/rebalancing-decisions/{id}", fixture.recommendationId()))
                    .andExpect(status().isOk());
            long twoDecisionCount = statistics.getPrepareStatementCount();
            assertTrue(twoDecisionCount <= 4, "Two-decision statement count was " + twoDecisionCount);
        } finally {
            cleanupFixture(fixture);
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static final String POST_PATH = "/api/rebalancing-decisions";
    private static final List<String> RAW_DIAGNOSTIC_MARKERS =
            List.of("ORA-", "SQLException", "Caused by", "at com.bapegg", "java.sql", "UQ_SP_", "CK_SP_");

    /**
     * The status/type/instance/retryable/title/detail/requestId/timestamp core every ProblemDetail
     * response shares (per current-task.md section 5), regardless of whether it carries
     * {@code fieldErrors}. Exact HTTP status on both the response and the body's own
     * {@code status}, the expected {@code instance} path, non-null {@code title}/{@code detail},
     * a {@code requestId} matching the {@link RequestIdFilter#REQUEST_ID_HEADER} response header,
     * a non-null {@code timestamp}, and {@code detail} never leaking a raw recommendation id,
     * idempotency key, or SQL/constraint/stack diagnostic text.
     */
    private void assertProblemDetailCore(
            MvcResult result, Map<String, Object> problem, int expectedStatus, String expectedCode,
            boolean expectedRetryable, String expectedInstance, Object leakCandidate) {
        assertEquals(expectedStatus, result.getResponse().getStatus());
        assertEquals(expectedStatus, ((Number) problem.get("status")).intValue());
        assertEquals(expectedCode, problem.get("code"));
        assertEquals("urn:stockpilot:error:" + expectedCode, problem.get("type"));
        assertEquals(expectedInstance, problem.get("instance"));
        assertEquals(expectedRetryable, problem.get("retryable"));
        assertNotNull(problem.get("title"));
        assertNotNull(problem.get("detail"));
        String headerRequestId = result.getResponse().getHeader(RequestIdFilter.REQUEST_ID_HEADER);
        assertNotNull(headerRequestId);
        assertEquals(headerRequestId, problem.get("requestId"));
        assertNotNull(problem.get("timestamp"));

        String detail = String.valueOf(problem.get("detail"));
        if (leakCandidate != null) {
            assertFalse(detail.contains(String.valueOf(leakCandidate)),
                    "ProblemDetail.detail must not leak the raw recommendation id/idempotency key.");
        }
        for (String marker : RAW_DIAGNOSTIC_MARKERS) {
            assertFalse(detail.contains(marker), "ProblemDetail.detail must not leak a raw diagnostic marker: " + marker);
        }
    }

    /**
     * For every non-validation code (everything this class raises except {@code VALIDATION_ERROR}):
     * the core contract, plus confirming {@code fieldErrors} is never attached -- per
     * {@code AnalysisApiExceptionHandler.respond}, that property is only ever set when the
     * effective code is {@code VALIDATION_ERROR}.
     */
    private void assertProblemDetail(
            MvcResult result, Map<String, Object> problem, int expectedStatus, String expectedCode,
            boolean expectedRetryable, String expectedInstance, Object leakCandidate) {
        assertProblemDetailCore(result, problem, expectedStatus, expectedCode, expectedRetryable, expectedInstance,
                leakCandidate);
        assertNull(problem.get("fieldErrors"), "A non-validation ProblemDetail must never carry fieldErrors.");
    }

    /** Overload for the POST endpoint, whose {@code instance} is always the fixed collection path. */
    private void assertPostProblemDetail(
            MvcResult result, Map<String, Object> problem, int expectedStatus, String expectedCode,
            boolean expectedRetryable, Object leakCandidate) {
        assertProblemDetail(result, problem, expectedStatus, expectedCode, expectedRetryable, POST_PATH, leakCandidate);
    }

    /**
     * For {@code VALIDATION_ERROR} only: the core contract plus an exact, single-entry
     * {@code fieldErrors} list (not just "contains a matching entry") -- proves no other field was
     * also rejected and nothing beyond the one expected violation leaked into the response.
     */
    @SuppressWarnings("unchecked")
    private void assertValidationProblemDetail(
            MvcResult result, Map<String, Object> problem, String expectedInstance, String expectedField,
            String expectedFieldErrorCode) {
        assertProblemDetailCore(result, problem, 400, "VALIDATION_ERROR", false, expectedInstance, null);
        List<Map<String, Object>> fieldErrors = (List<Map<String, Object>>) problem.get("fieldErrors");
        assertNotNull(fieldErrors);
        assertEquals(1, fieldErrors.size(), "Expected exactly one fieldError but got " + fieldErrors);
        assertEquals(expectedField, fieldErrors.get(0).get("field"));
        assertEquals(expectedFieldErrorCode, fieldErrors.get(0).get("code"));
    }

    private void assertPostValidationProblemDetail(
            MvcResult result, Map<String, Object> problem, String expectedField, String expectedFieldErrorCode) {
        assertValidationProblemDetail(result, problem, POST_PATH, expectedField, expectedFieldErrorCode);
    }

    /** Both stores' inventory snapshot and metric business values, to prove a call left them untouched. */
    private PersistenceState capture(Fixture fixture) {
        SpInventorySnapshot receiver = findSnapshot(ANALYSIS_DATE, fixture.inputSnapshotVersion(), RECEIVER_STORE_ID);
        SpInventorySnapshot donor = findSnapshot(ANALYSIS_DATE, fixture.inputSnapshotVersion(), DONOR_STORE_ID);
        SpInventoryMetric receiverMetric = metricRepository.findById(fixture.receiverMetricId()).orElseThrow();
        SpInventoryMetric donorMetric = metricRepository.findById(fixture.donorMetricId()).orElseThrow();
        return new PersistenceState(
                snapshotFingerprint(receiver), snapshotFingerprint(donor),
                metricFingerprint(receiverMetric), metricFingerprint(donorMetric));
    }

    private static String snapshotFingerprint(SpInventorySnapshot snapshot) {
        return snapshot.getInventorySnapshotId() + ":" + snapshot.getOnHandQuantity() + ":"
                + snapshot.getReservedQuantity();
    }

    private static String metricFingerprint(SpInventoryMetric metric) {
        return metric.getInventoryMetricId() + ":" + metric.getAvailableQuantity() + ":"
                + metric.getBaseDemandRate() + ":" + metric.getHighDemandRate();
    }

    private void assertUnchanged(PersistenceState before, PersistenceState after) {
        assertEquals(before, after, "This call must not mutate inventory snapshot or metric values.");
    }

    private record PersistenceState(
            String receiverSnapshotFingerprint, String donorSnapshotFingerprint,
            String receiverMetricFingerprint, String donorMetricFingerprint) {
    }

    private static String mvp2Json(
            Fixture fixture, String decisionStatus, Integer selectedQuantity, Boolean policyException,
            String reasonCode, String reason) {
        StringBuilder json = new StringBuilder("{\"recommendationId\":").append(fixture.recommendationId())
                .append(",\"decisionStatus\":\"").append(decisionStatus).append('"')
                .append(",\"actorLabel\":\"it\"")
                .append(",\"analysisRunId\":").append(fixture.analysisRunId())
                .append(",\"inputSnapshotVersion\":\"").append(fixture.inputSnapshotVersion()).append('"')
                .append(",\"ruleVersion\":\"").append(fixture.ruleVersion()).append('"')
                .append(",\"candidateVersion\":1");
        if (selectedQuantity != null) {
            json.append(",\"selectedQuantity\":").append(selectedQuantity);
        }
        if (policyException != null) {
            json.append(",\"policyException\":").append(policyException);
        }
        if (reasonCode != null) {
            json.append(",\"reasonCode\":\"").append(reasonCode).append('"');
        }
        if (reason != null) {
            json.append(",\"reason\":\"").append(reason).append('"');
        }
        return json.append('}').toString();
    }

    private static String newKey() {
        return "RDROIT-KEY-" + UUID.randomUUID();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readMap(MvcResult result) throws Exception {
        return objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
    }

    /**
     * Receiver available=5/reserved=1 (on-hand 6), donor available=40/reserved=2 (on-hand 42),
     * both baseline+high rates=1, route lead time 4/min 1/pkg 1/max {@code routeMaximum},
     * receiver policy displayMin 2/maxCapacity 1000/targetCoverage 7, donor policy retainedDays
     * 7/displayMin 0/safety 0 -- the same numeric shape {@link
     * com.bapegg.stockpilot.approval.ApprovalTransactionExecutorIT} already proved, giving
     * {@code recommendedBaseQuantity} = ceil(1*(4+7)) + 2 - 5 = 8 (floored to pkg 1, within
     * donorTransferable 40-ceil(1*7)=33 and routeMax).
     */
    private Fixture buildFixture(String suffix, int routeMaximum) {
        String ruleVersion = "MVP-2-RDROIT" + suffix;
        String inputSnapshotVersion = "MVP-2-RDROIT" + suffix;

        insertSnapshot(ANALYSIS_DATE, RECEIVER_STORE_ID, 6, 1, inputSnapshotVersion);
        insertSnapshot(ANALYSIS_DATE, DONOR_STORE_ID, 42, 2, inputSnapshotVersion);
        SpInventorySnapshot receiverSnapshot = findSnapshot(ANALYSIS_DATE, inputSnapshotVersion, RECEIVER_STORE_ID);
        SpInventorySnapshot donorSnapshot = findSnapshot(ANALYSIS_DATE, inputSnapshotVersion, DONOR_STORE_ID);

        SpAnalysisRun analysisRun =
                analysisRunRepository.save(new SpAnalysisRun(ANALYSIS_DATE, ruleVersion, inputSnapshotVersion));
        analysisRun.markCompleted();
        analysisRun = analysisRunRepository.save(analysisRun);

        SpInventoryMetric receiverMetric = metricRepository.save(new SpInventoryMetric(
                analysisRun, receiverSnapshot, InventoryMetricCalculation.calculate(6, 1, 28)));
        receiverMetric.applyDemandRates(BigDecimal.ONE, BigDecimal.ONE);
        receiverMetric = metricRepository.save(receiverMetric);

        SpInventoryMetric donorMetric = metricRepository.save(new SpInventoryMetric(
                analysisRun, donorSnapshot, InventoryMetricCalculation.calculate(42, 2, 4)));
        donorMetric.applyDemandRates(BigDecimal.ONE, BigDecimal.ONE);
        donorMetric = metricRepository.save(donorMetric);

        SpRebalanceRecommendation recommendation = recommendationRepository.save(new SpRebalanceRecommendation(
                receiverMetric, donorMetric, new RebalanceCalculation(8, 8, 8)));

        SpStoreTransferRoute route = routeRepository.save(new SpStoreTransferRoute(
                DONOR_STORE_ID, RECEIVER_STORE_ID, true, false, 4, 1, 1, routeMaximum, inputSnapshotVersion));
        recommendation.assignRoute(route.getRouteId());
        recommendation = recommendationRepository.save(recommendation);

        SpStoreSkuPolicy receiverPolicy = policyRepository.save(
                new SpStoreSkuPolicy(RECEIVER_STORE_ID, SKU_ID, 2, 0, 1000, 7, 0, inputSnapshotVersion));
        SpStoreSkuPolicy donorPolicy = policyRepository.save(
                new SpStoreSkuPolicy(DONOR_STORE_ID, SKU_ID, 0, 0, 1000, 0, 7, inputSnapshotVersion));

        return new Fixture(recommendation.getRecommendationId(), analysisRun.getAnalysisRunId(),
                inputSnapshotVersion, ruleVersion, route.getRouteId(),
                receiverPolicy.getStoreSkuPolicyId(), donorPolicy.getStoreSkuPolicyId(),
                receiverMetric.getInventoryMetricId(), donorMetric.getInventoryMetricId(),
                donorSnapshot.getInventorySnapshotId());
    }

    private void insertSnapshot(LocalDate date, String storeId, int onHand, int reserved, String inputSnapshotVersion) {
        jdbcTemplate.update(
                "INSERT INTO sp_inventory_snapshot (snapshot_date, store_id, sku_id, on_hand_quantity, "
                        + "reserved_quantity, source_type, input_snapshot_version) VALUES (?, ?, ?, ?, ?, 'SYNTHETIC', ?)",
                date, storeId, SKU_ID, onHand, reserved, inputSnapshotVersion);
    }

    private SpInventorySnapshot findSnapshot(LocalDate date, String inputSnapshotVersion, String storeId) {
        return snapshotRepository.findBySnapshotDate(date).stream()
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
            Long receiverMetricId, Long donorMetricId, Long donorSnapshotId) {
    }

    /** A minimal exact-MVP-1 fixture for the legacy tuple-less path -- no route or policy rows. */
    private LegacyFixture buildLegacyFixture() {
        String inputSnapshotVersion = "MVP-1-RDROIT-LEGACY";
        purgeLegacyFixtureRemnants(inputSnapshotVersion);

        insertSnapshot(LEGACY_ANALYSIS_DATE, RECEIVER_STORE_ID, 6, 1, inputSnapshotVersion);
        insertSnapshot(LEGACY_ANALYSIS_DATE, DONOR_STORE_ID, 42, 2, inputSnapshotVersion);
        SpInventorySnapshot receiverSnapshot = findSnapshot(LEGACY_ANALYSIS_DATE, inputSnapshotVersion, RECEIVER_STORE_ID);
        SpInventorySnapshot donorSnapshot = findSnapshot(LEGACY_ANALYSIS_DATE, inputSnapshotVersion, DONOR_STORE_ID);

        SpAnalysisRun analysisRun = analysisRunRepository.save(
                new SpAnalysisRun(LEGACY_ANALYSIS_DATE, InventoryAnalysisRules.RULE_VERSION, inputSnapshotVersion));
        analysisRun.markCompleted();
        analysisRun = analysisRunRepository.save(analysisRun);

        SpInventoryMetric receiverMetric = metricRepository.save(new SpInventoryMetric(
                analysisRun, receiverSnapshot, InventoryMetricCalculation.calculate(6, 1, 28)));
        receiverMetric.applyDemandRates(BigDecimal.ONE, BigDecimal.ONE);
        receiverMetric = metricRepository.save(receiverMetric);

        SpInventoryMetric donorMetric = metricRepository.save(new SpInventoryMetric(
                analysisRun, donorSnapshot, InventoryMetricCalculation.calculate(42, 2, 4)));
        donorMetric.applyDemandRates(BigDecimal.ONE, BigDecimal.ONE);
        donorMetric = metricRepository.save(donorMetric);

        SpRebalanceRecommendation recommendation = recommendationRepository.save(
                new SpRebalanceRecommendation(receiverMetric, donorMetric, new RebalanceCalculation(8, 8, 8)));

        return new LegacyFixture(recommendation.getRecommendationId(), analysisRun.getAnalysisRunId(),
                receiverMetric.getInventoryMetricId(), donorMetric.getInventoryMetricId(), inputSnapshotVersion);
    }

    private void cleanupLegacyFixture(LegacyFixture fixture) {
        if (fixture == null) {
            return;
        }
        purgeLegacyFixtureRemnants(fixture.inputSnapshotVersion());
    }

    private void purgeLegacyFixtureRemnants(String inputSnapshotVersion) {
        List<Long> runIds = jdbcTemplate.queryForList(
                "SELECT analysis_run_id FROM sp_analysis_run WHERE input_snapshot_version = ?",
                Long.class, inputSnapshotVersion);
        for (Long runId : runIds) {
            jdbcTemplate.update(
                    "DELETE FROM sp_transfer_draft WHERE decision_id IN (SELECT decision_id FROM "
                            + "sp_rebalance_decision WHERE recommendation_id IN (SELECT recommendation_id FROM "
                            + "sp_rebalance_recommendation WHERE receiver_metric_id IN "
                            + "(SELECT inventory_metric_id FROM sp_inventory_metric WHERE analysis_run_id = ?) "
                            + "OR donor_metric_id IN "
                            + "(SELECT inventory_metric_id FROM sp_inventory_metric WHERE analysis_run_id = ?)))",
                    runId, runId);
            jdbcTemplate.update(
                    "DELETE FROM sp_approval_basis WHERE decision_id IN (SELECT decision_id FROM "
                            + "sp_rebalance_decision WHERE recommendation_id IN (SELECT recommendation_id FROM "
                            + "sp_rebalance_recommendation WHERE receiver_metric_id IN "
                            + "(SELECT inventory_metric_id FROM sp_inventory_metric WHERE analysis_run_id = ?) "
                            + "OR donor_metric_id IN "
                            + "(SELECT inventory_metric_id FROM sp_inventory_metric WHERE analysis_run_id = ?)))",
                    runId, runId);
            jdbcTemplate.update(
                    "DELETE FROM sp_rebalance_decision WHERE recommendation_id IN (SELECT recommendation_id FROM "
                            + "sp_rebalance_recommendation WHERE receiver_metric_id IN "
                            + "(SELECT inventory_metric_id FROM sp_inventory_metric WHERE analysis_run_id = ?) "
                            + "OR donor_metric_id IN "
                            + "(SELECT inventory_metric_id FROM sp_inventory_metric WHERE analysis_run_id = ?))",
                    runId, runId);
            jdbcTemplate.update(
                    "DELETE FROM sp_rebalance_recommendation WHERE receiver_metric_id IN "
                            + "(SELECT inventory_metric_id FROM sp_inventory_metric WHERE analysis_run_id = ?) "
                            + "OR donor_metric_id IN "
                            + "(SELECT inventory_metric_id FROM sp_inventory_metric WHERE analysis_run_id = ?)",
                    runId, runId);
            jdbcTemplate.update("DELETE FROM sp_inventory_metric WHERE analysis_run_id = ?", runId);
            jdbcTemplate.update("DELETE FROM sp_analysis_run WHERE analysis_run_id = ?", runId);
        }
        jdbcTemplate.update("DELETE FROM sp_inventory_snapshot WHERE input_snapshot_version = ?", inputSnapshotVersion);
    }

    private record LegacyFixture(
            Long recommendationId, Long analysisRunId, Long receiverMetricId, Long donorMetricId,
            String inputSnapshotVersion) {
    }
}
