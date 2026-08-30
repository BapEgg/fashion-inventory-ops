package com.bapegg.stockpilot.rebalance;

import com.bapegg.stockpilot.analysis.InventoryAnalysisRules;
import com.bapegg.stockpilot.analysis.InventoryMetricCalculation;
import com.bapegg.stockpilot.analysis.SpAnalysisRun;
import com.bapegg.stockpilot.analysis.SpAnalysisRunRepository;
import com.bapegg.stockpilot.analysis.SpInventoryMetric;
import com.bapegg.stockpilot.analysis.SpInventoryMetricRepository;
import com.bapegg.stockpilot.api.error.RequestIdFilter;
import com.bapegg.stockpilot.approval.ApprovalTransactionCommand;
import com.bapegg.stockpilot.approval.ApprovalTransactionFacade;
import com.bapegg.stockpilot.approval.ManualQuantityTestExecutor;
import com.bapegg.stockpilot.inventory.SpInventorySnapshot;
import com.bapegg.stockpilot.inventory.SpInventorySnapshotRepository;
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
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies {@code POST /api/rebalancing-simulations}' MVP-2 {@code MANUAL} quantity-test REST
 * slice against the real Oracle instance, per current-task.md sections 2-5. Not
 * {@code @Transactional} -- the lock-timeout scenario needs a genuinely separate, still-open
 * transaction, so every fixture is built via committed repository calls (mirroring {@code
 * ApprovalTransactionConcurrencyIT}'s convention) and torn down in a {@code finally} block.
 * Skipped (not failed) when DB_URL is not set.
 */
@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "DB_URL", matches = ".+")
class RebalanceSimulationRestOracleIT {

    private static final LocalDate ANALYSIS_DATE = LocalDate.of(2026, 11, 20);
    private static final String RECEIVER_STORE_ID = "STORE-GANGNAM";
    private static final String DONOR_STORE_ID = "STORE-HONGDAE";
    private static final String SKU_ID = "SKU-CAP-BLACK-FREE";

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
    private ApprovalTransactionFacade facade;
    @Autowired
    private ManualQuantityTestExecutor manualExecutor;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void feasibleRequestReturns200WithFullProjectionAndLeavesNoTrace() throws Exception {
        Fixture fixture = buildFixture("-FEASIBLE", 2, 10);
        try {
            PersistenceState before = capture(fixture);

            Map<String, Object> body = readMap(mockMvc.perform(post("/api/rebalancing-simulations")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson(fixture, 8)))
                    .andExpect(status().isOk())
                    .andReturn());

            // Full fixed fixture shape (receiver available=5/reserved=0, donor available=10/
            // reserved=0, both baseline+high rate=1, route lead 4/min 1/pkg 2/max 10, receiver
            // policy displayMin=2/maxCapacity=1000/targetCoverage=7, donor policy all-zero so
            // donorProtected=0 and any positive donorAfter is OVERSTOCK) drives every top-level
            // and projection field below to one exact, hand-derived value -- not just notNull.
            assertEquals(fixture.recommendationId().longValue(), ((Number) body.get("recommendationId")).longValue());
            assertEquals(fixture.analysisRunId().longValue(), ((Number) body.get("analysisRunId")).longValue());
            assertEquals(fixture.inputSnapshotVersion(), body.get("inputSnapshotVersion"));
            assertEquals(fixture.ruleVersion(), body.get("ruleVersion"));
            assertEquals(1, ((Number) body.get("candidateVersion")).intValue());
            assertEquals(8, ((Number) body.get("requestedQuantity")).intValue());
            assertEquals(Boolean.TRUE, body.get("feasible"));
            assertEquals(Boolean.FALSE, body.get("reasonRequired"), "8 is exactly the recommended BASE quantity.");
            assertEquals(8, ((Number) body.get("recommendedBaseQuantity")).intValue());
            // hardCeiling = min(donorTransferable 10, routeMax 10, receiverCapacity 995) = 10;
            // floored to pkg 2 stays 10; suggested = floor(min(requested 8, 10), 2) = 8.
            assertEquals(10, ((Number) body.get("maximumFeasibleQuantity")).intValue());
            assertEquals(8, ((Number) body.get("suggestedQuantity")).intValue());
            assertEquals(List.of(), body.get("violations"));
            assertEquals(List.of(), body.get("candidateRejectionReasons"));
            assertEquals(1, ((Number) body.get("routeMinimumQuantity")).intValue());
            assertEquals(2, ((Number) body.get("packageMultiple")).intValue());
            assertEquals(10, ((Number) body.get("routeMaximumQuantity")).intValue());
            // Freshly recalculated, not the stale stored RebalanceCalculation.donorTransferableQuantity
            // (8) -- donorProtected is 0 because the donor policy's retainedDays/displayMinimum/
            // safetyStock are all 0, so the full donor-at-dispatch quantity (10) is transferable.
            assertEquals(10, ((Number) body.get("donorTransferableQuantity")).longValue());
            // max(receiverMaximumCapacity 1000 - receiverBeforeAvailable 5, 0) = 995.
            assertEquals(995, ((Number) body.get("receiverCapacityRemaining")).longValue());
            assertEquals(Boolean.TRUE, body.get("approvalRevalidationRequired"));
            Map<String, Object> assumption = (Map<String, Object>) body.get("assumption");
            assertEquals("ASSUMPTION", assumption.get("type"));
            assertEquals("수량 시험 결과는 MVP-2 데모 가정이며 실제 승인 시 최신 근거로 다시 검증합니다.", assumption.get("notice"));

            Map<String, Object> projection = (Map<String, Object>) body.get("projection");
            assertNotNull(projection);
            assertEquals(5, ((Number) projection.get("receiverBeforeAvailable")).intValue());
            assertEquals(13, ((Number) projection.get("receiverAfterAvailable")).intValue());
            // coverageDays = availableQuantity / rate(1), scale 12 HALF_UP -- both baseline rates
            // are exactly 1 here, so this is just the available quantity as a decimal.
            assertEquals(5.0, ((Number) projection.get("receiverBeforeCoverageDays")).doubleValue());
            assertEquals(13.0, ((Number) projection.get("receiverAfterCoverageDays")).doubleValue());
            // targetForRisk = ceil(rate 1 * (leadTime 4 + targetCoverage 7)) + displayMin 2 = 13;
            // receiverAfterAvailable 13 is not < 13, so NORMAL (no new stockout risk).
            assertEquals("NORMAL", projection.get("receiverRiskCode"));
            assertEquals(10, ((Number) projection.get("donorBeforeAvailable")).intValue());
            assertEquals(2, ((Number) projection.get("donorAfterAvailable")).intValue());
            assertEquals(10.0, ((Number) projection.get("donorBeforeCoverageDays")).doubleValue());
            assertEquals(2.0, ((Number) projection.get("donorAfterCoverageDays")).doubleValue());
            // donorProtected = 0 (all-zero donor policy), donorAfterAvailable 2 - 0 > 0 -> OVERSTOCK.
            assertEquals("OVERSTOCK", projection.get("donorRiskCode"));
            assertEquals(4, ((Number) projection.get("leadTimeDays")).intValue());
            assertEquals(ANALYSIS_DATE.plusDays(4).toString(), projection.get("expectedArrivalDate"));
            // No confirmed inbound or open transfers exist in this fixture at all.
            assertEquals(0, ((Number) projection.get("receiverInboundArrivingBeforeTransfer")).intValue());
            assertEquals(0, ((Number) projection.get("receiverOpenTransferInbound")).intValue());
            assertEquals(0, ((Number) projection.get("receiverOpenTransferOutbound")).intValue());
            assertEquals(0, ((Number) projection.get("donorInboundArrivingBeforeDispatch")).intValue());
            assertEquals(0, ((Number) projection.get("donorOpenTransferOutbound")).intValue());
            assertEquals(0, ((Number) projection.get("donorAlreadyApprovedDraftQuantity")).intValue());

            assertUnchanged(before, capture(fixture));
        } finally {
            cleanupFixture(fixture);
        }
    }

    @Test
    void multiViolationInfeasibleRequestStillReturns200WithNullProjection() throws Exception {
        // packageMultiple=2, routeMaximum=10, donorTransferable=10 -- a request of 15 violates
        // NOT_PACKAGE_MULTIPLE, EXCEEDS_DONOR_TRANSFERABLE and EXCEEDS_ROUTE_MAXIMUM at once.
        Fixture fixture = buildFixture("-MULTIVIOL", 2, 10);
        try {
            PersistenceState before = capture(fixture);

            Map<String, Object> body = readMap(mockMvc.perform(post("/api/rebalancing-simulations")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson(fixture, 15)))
                    .andExpect(status().isOk())
                    .andReturn());

            assertEquals(Boolean.FALSE, body.get("feasible"));
            assertNull(body.get("projection"));
            // Declared ManualQuantityViolation order is CANDIDATE_INELIGIBLE, BELOW_ROUTE_MINIMUM,
            // NOT_PACKAGE_MULTIPLE, EXCEEDS_DONOR_TRANSFERABLE, EXCEEDS_ROUTE_MAXIMUM,
            // EXCEEDS_RECEIVER_CAPACITY -- assert the exact list, not just a subset, so an
            // unexpected extra or missing violation fails the test.
            assertEquals(List.of("NOT_PACKAGE_MULTIPLE", "EXCEEDS_DONOR_TRANSFERABLE", "EXCEEDS_ROUTE_MAXIMUM"),
                    body.get("violations"));
            assertEquals(List.of(), body.get("candidateRejectionReasons"));
            // maximumFeasibleQuantity = floor(min(donorTransferable 10, routeMax 10, receiverCapacity 995), pkg 2) = 10;
            // suggestedQuantity = floor(min(requested 15, maximumFeasible 10), pkg 2) = 10.
            assertEquals(10, ((Number) body.get("maximumFeasibleQuantity")).intValue());
            assertEquals(10, ((Number) body.get("suggestedQuantity")).intValue());

            assertUnchanged(before, capture(fixture));
        } finally {
            cleanupFixture(fixture);
        }
    }

    @Test
    void partialVersionTupleIsRejectedAsInvalidRequest() throws Exception {
        Fixture fixture = buildFixture("-PARTIAL", 1, 20);
        try {
            PersistenceState before = capture(fixture);

            MvcResult result = mockMvc.perform(post("/api/rebalancing-simulations")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"recommendationId\":" + fixture.recommendationId()
                                    + ",\"requestedQuantity\":8,\"analysisRunId\":" + fixture.analysisRunId() + "}"))
                    .andExpect(status().isBadRequest())
                    .andReturn();
            assertProblemDetail(result, readMap(result), 400, "INVALID_REQUEST", false, fixture.recommendationId());
            assertUnchanged(before, capture(fixture));
        } finally {
            cleanupFixture(fixture);
        }
    }

    @Test
    void unknownRecommendationIsNotFound() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/rebalancing-simulations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recommendationId\":999999999,\"requestedQuantity\":8,"
                                + "\"analysisRunId\":1,\"inputSnapshotVersion\":\"X\",\"ruleVersion\":\"MVP-2\","
                                + "\"candidateVersion\":1}"))
                .andExpect(status().isNotFound())
                .andReturn();
        assertProblemDetail(result, readMap(result), 404, "RECOMMENDATION_NOT_FOUND", false, 999999999L);
    }

    @Test
    void staleVersionTupleIsRejected() throws Exception {
        Fixture fixture = buildFixture("-STALETUPLE", 1, 20);
        try {
            PersistenceState before = capture(fixture);

            MvcResult result = mockMvc.perform(post("/api/rebalancing-simulations")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"recommendationId\":" + fixture.recommendationId() + ",\"requestedQuantity\":8,"
                                    + "\"analysisRunId\":" + fixture.analysisRunId()
                                    + ",\"inputSnapshotVersion\":\"" + fixture.inputSnapshotVersion() + "\","
                                    + "\"ruleVersion\":\"WRONG-RULE-VERSION\",\"candidateVersion\":1}"))
                    .andExpect(status().isConflict())
                    .andReturn();
            assertProblemDetail(result, readMap(result), 409, "STALE_RECOMMENDATION", false, fixture.recommendationId());
            assertUnchanged(before, capture(fixture));
        } finally {
            cleanupFixture(fixture);
        }
    }

    @Test
    void terminalDecisionIsRejected() throws Exception {
        Fixture fixture = buildFixture("-TERMINAL", 1, 20);
        try {
            facade.execute(new ApprovalTransactionCommand(
                    fixture.recommendationId(), fixture.analysisRunId(), fixture.inputSnapshotVersion(),
                    fixture.ruleVersion(), 1, DecisionStatus.APPROVED, 8, false,
                    "MANUAL_OVERRIDE", "rest oracle IT setup", "it"), newKey());

            // Baseline is captured AFTER the legitimate approval above, not zero -- the rejected
            // simulate call below must add nothing on top of that already-approved state.
            PersistenceState before = capture(fixture);

            MvcResult result = mockMvc.perform(post("/api/rebalancing-simulations")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson(fixture, 5)))
                    .andExpect(status().isConflict())
                    .andReturn();
            assertProblemDetail(result, readMap(result), 409, "DECISION_ALREADY_TERMINAL", false, fixture.recommendationId());
            assertUnchanged(before, capture(fixture));
        } finally {
            cleanupFixture(fixture);
        }
    }

    @Test
    void donorLockHeldByAnotherTransactionCausesA503LockTimeout() throws Exception {
        Fixture fixture = buildFixture("-LOCKTIMEOUT", 1, 20);
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

            MvcResult result = mockMvc.perform(post("/api/rebalancing-simulations")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson(fixture, 8)))
                    .andExpect(status().is(503))
                    .andReturn();
            assertProblemDetail(result, readMap(result), 503, "APPROVAL_LOCK_TIMEOUT", true, fixture.recommendationId());

            releaseLock.countDown();
            holderFuture.get(10, TimeUnit.SECONDS);
            assertUnchanged(before, capture(fixture));
        } finally {
            holderExecutor.shutdownNow();
            cleanupFixture(fixture);
        }
    }

    /**
     * Regression for Codex Finding 1: an unknown/future rule version on a tuple-less request is
     * neither the MVP-1 allowlisted value nor MVP-2, so it must be rejected too -- not silently
     * fall through to the unlocked legacy calculation.
     */
    @Test
    void unknownRuleVersionOnATupleLessRequestIsRejected() throws Exception {
        Fixture fixture = buildFixture("-UNKNOWNRULE", 1, 20);
        try {
            PersistenceState before = capture(fixture);

            MvcResult result = mockMvc.perform(post("/api/rebalancing-simulations")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"recommendationId\":" + fixture.recommendationId()
                                    + ",\"requestedQuantity\":5}"))
                    .andExpect(status().isBadRequest())
                    .andReturn();
            assertProblemDetail(result, readMap(result), 400, "INVALID_REQUEST", false, fixture.recommendationId());
            assertUnchanged(before, capture(fixture));
        } finally {
            cleanupFixture(fixture);
        }
    }

    /**
     * Positive regression for Codex Finding 1: a tuple-less request against a recommendation whose
     * run's rule version is exactly {@link InventoryAnalysisRules#RULE_VERSION} must still be
     * accepted by the legacy (unlocked) calculation.
     */
    @Test
    void exactMvp1RuleVersionOnATupleLessRequestIsStillAccepted() throws Exception {
        LegacyFixture fixture = buildLegacyFixture();
        try {
            Map<String, Object> body = readMap(mockMvc.perform(post("/api/rebalancing-simulations")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"recommendationId\":" + fixture.recommendationId()
                                    + ",\"requestedQuantity\":3}"))
                    .andExpect(status().isOk())
                    .andReturn());

            assertEquals(fixture.recommendationId().intValue(), ((Number) body.get("recommendationId")).intValue());
            assertEquals(3, ((Number) body.get("requestedQuantity")).intValue());
            assertNotNull(body.get("receiverBefore"));
            assertNotNull(body.get("receiverAfter"));
            assertNotNull(body.get("donorBefore"));
            assertNotNull(body.get("donorAfter"));
        } finally {
            cleanupLegacyFixture(fixture);
        }
    }

    /**
     * Captures every row a {@code MANUAL} test must never mutate: the full decision list with
     * its audit values (not just a count -- a same-count-but-changed-status mutation must fail
     * this too) and each decision's basis/draft business values, plus both stores' inventory
     * snapshot and metric business values. {@link #assertUnchanged} then compares two of these
     * value-for-value.
     */
    private PersistenceState capture(Fixture fixture) {
        List<SpRebalanceDecision> decisions = decisionRepository
                .findAllByRecommendation_RecommendationIdOrderByDecisionSequenceAsc(fixture.recommendationId());
        List<DecisionAudit> decisionAudits = decisions.stream().map(this::auditDecision).toList();
        SpInventorySnapshot receiver = findSnapshot(fixture.inputSnapshotVersion(), RECEIVER_STORE_ID);
        SpInventorySnapshot donor = findSnapshot(fixture.inputSnapshotVersion(), DONOR_STORE_ID);
        SpInventoryMetric receiverMetric = metricRepository.findById(fixture.receiverMetricId()).orElseThrow();
        SpInventoryMetric donorMetric = metricRepository.findById(fixture.donorMetricId()).orElseThrow();
        return new PersistenceState(
                decisionAudits, snapshotFingerprint(receiver), snapshotFingerprint(donor),
                metricFingerprint(receiverMetric), metricFingerprint(donorMetric));
    }

    private DecisionAudit auditDecision(SpRebalanceDecision decision) {
        Optional<BasisAudit> basis = approvalBasisRepository.findByDecision_DecisionId(decision.getDecisionId())
                .map(b -> new BasisAudit(
                        b.getRecommendedBaseQuantity(), b.getDonorTransferableQuantity(), b.getReceiverCapacityRemaining()));
        Optional<DraftAudit> draft = transferDraftRepository.findByDecision_DecisionId(decision.getDecisionId())
                .map(d -> new DraftAudit(d.getQuantity(), d.getDraftStatus()));
        return new DecisionAudit(decision.getDecisionId(), decision.getDecisionStatus(),
                decision.getSelectedQuantity(), decision.getDecisionSequence(), decision.getReasonCode(), basis, draft);
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
        assertEquals(before, after, "Simulation must not persist or mutate any row.");
    }

    private record BasisAudit(long recommendedBaseQuantity, long donorTransferableQuantity, long receiverCapacityRemaining) {
    }

    private record DraftAudit(Integer quantity, DraftStatus draftStatus) {
    }

    private record DecisionAudit(
            Long decisionId, DecisionStatus decisionStatus, Integer selectedQuantity, int decisionSequence,
            String reasonCode, Optional<BasisAudit> basis, Optional<DraftAudit> draft) {
    }

    private record PersistenceState(
            List<DecisionAudit> decisions, String receiverSnapshotFingerprint, String donorSnapshotFingerprint,
            String receiverMetricFingerprint, String donorMetricFingerprint) {
    }

    private static final List<String> RAW_DIAGNOSTIC_MARKERS =
            List.of("ORA-", "SQLException", "Caused by", "at com.bapegg", "java.sql");

    private static final String SIMULATIONS_PATH = "/api/rebalancing-simulations";

    /**
     * Asserts the full ProblemDetail contract (per current-task.md section 5): exact HTTP status
     * on both the response and the body's own {@code status}, {@code code}/{@code type}, {@code
     * instance} (always this endpoint's path), {@code retryable}, a {@code requestId} that matches
     * the {@link RequestIdFilter#REQUEST_ID_HEADER} response header, a non-null {@code timestamp},
     * and that {@code detail} never leaks the raw recommendation id a caller sent or any raw
     * SQL/stack diagnostic text.
     */
    private void assertProblemDetail(
            MvcResult result, Map<String, Object> problem, int expectedStatus, String expectedCode,
            boolean expectedRetryable, Object leakCandidateId) {
        assertEquals(expectedStatus, result.getResponse().getStatus());
        assertEquals(expectedStatus, ((Number) problem.get("status")).intValue());
        assertEquals(expectedCode, problem.get("code"));
        assertEquals("urn:stockpilot:error:" + expectedCode, problem.get("type"));
        assertEquals(SIMULATIONS_PATH, problem.get("instance"));
        assertEquals(expectedRetryable, problem.get("retryable"));
        String headerRequestId = result.getResponse().getHeader(RequestIdFilter.REQUEST_ID_HEADER);
        assertNotNull(headerRequestId);
        assertEquals(headerRequestId, problem.get("requestId"));
        assertNotNull(problem.get("timestamp"));

        String detail = String.valueOf(problem.get("detail"));
        if (leakCandidateId != null) {
            assertFalse(detail.contains(String.valueOf(leakCandidateId)),
                    "ProblemDetail.detail must not leak the raw recommendation id.");
        }
        for (String marker : RAW_DIAGNOSTIC_MARKERS) {
            assertFalse(detail.contains(marker), "ProblemDetail.detail must not leak a raw diagnostic marker: " + marker);
        }
    }

    private static String requestJson(Fixture fixture, int requestedQuantity) {
        return "{\"recommendationId\":" + fixture.recommendationId() + ",\"requestedQuantity\":" + requestedQuantity
                + ",\"analysisRunId\":" + fixture.analysisRunId()
                + ",\"inputSnapshotVersion\":\"" + fixture.inputSnapshotVersion() + "\""
                + ",\"ruleVersion\":\"" + fixture.ruleVersion() + "\",\"candidateVersion\":1}";
    }

    private static String newKey() {
        return "RSROIT-KEY-" + UUID.randomUUID();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readMap(MvcResult result) throws Exception {
        return objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
    }

    /**
     * Receiver available=5/reserved=0 (projectedBefore=5), donor available=10/reserved=0, both
     * baseline rates=1, route lead time 4/min 1/target coverage 7/displayMin 2 --
     * recommendedBaseQuantity=8 (ceil(1*11)+2-5), the same numeric shape
     * {@code ApprovalTransactionConcurrencyIT} already proved.
     */
    private Fixture buildFixture(String suffix, int packageMultiple, int routeMaximum) {
        String ruleVersion = InventoryAnalysisRules.RULE_VERSION + "-RSROIT" + suffix;
        String inputSnapshotVersion = "MVP-2-RSROIT" + suffix;

        jdbcTemplate.update(
                "INSERT INTO sp_inventory_snapshot (snapshot_date, store_id, sku_id, on_hand_quantity, "
                        + "reserved_quantity, source_type, input_snapshot_version) VALUES (?, ?, ?, 5, 0, 'SYNTHETIC', ?)",
                ANALYSIS_DATE, RECEIVER_STORE_ID, SKU_ID, inputSnapshotVersion);
        jdbcTemplate.update(
                "INSERT INTO sp_inventory_snapshot (snapshot_date, store_id, sku_id, on_hand_quantity, "
                        + "reserved_quantity, source_type, input_snapshot_version) VALUES (?, ?, ?, 10, 0, 'SYNTHETIC', ?)",
                ANALYSIS_DATE, DONOR_STORE_ID, SKU_ID, inputSnapshotVersion);
        SpInventorySnapshot receiverSnapshot = findSnapshot(inputSnapshotVersion, RECEIVER_STORE_ID);
        SpInventorySnapshot donorSnapshot = findSnapshot(inputSnapshotVersion, DONOR_STORE_ID);

        SpAnalysisRun analysisRun =
                analysisRunRepository.save(new SpAnalysisRun(ANALYSIS_DATE, ruleVersion, inputSnapshotVersion));
        analysisRun.markCompleted();
        analysisRun = analysisRunRepository.save(analysisRun);

        SpInventoryMetric receiverMetric = metricRepository.save(new SpInventoryMetric(
                analysisRun, receiverSnapshot, InventoryMetricCalculation.calculate(5, 0, 28)));
        receiverMetric.applyDemandRates(BigDecimal.ONE, BigDecimal.ONE);
        receiverMetric = metricRepository.save(receiverMetric);

        SpInventoryMetric donorMetric = metricRepository.save(new SpInventoryMetric(
                analysisRun, donorSnapshot, InventoryMetricCalculation.calculate(10, 0, 4)));
        donorMetric.applyDemandRates(BigDecimal.ONE, BigDecimal.ONE);
        donorMetric = metricRepository.save(donorMetric);

        SpRebalanceRecommendation recommendation = recommendationRepository.save(new SpRebalanceRecommendation(
                receiverMetric, donorMetric, new RebalanceCalculation(8, 8, 8)));

        SpStoreTransferRoute route = routeRepository.save(new SpStoreTransferRoute(
                DONOR_STORE_ID, RECEIVER_STORE_ID, true, false, 4, 1, packageMultiple, routeMaximum, inputSnapshotVersion));
        recommendation.assignRoute(route.getRouteId());
        recommendation = recommendationRepository.save(recommendation);

        SpStoreSkuPolicy receiverPolicy = policyRepository.save(
                new SpStoreSkuPolicy(RECEIVER_STORE_ID, SKU_ID, 2, 0, 1000, 7, 0, inputSnapshotVersion));
        SpStoreSkuPolicy donorPolicy = policyRepository.save(
                new SpStoreSkuPolicy(DONOR_STORE_ID, SKU_ID, 0, 0, 1000, 0, 0, inputSnapshotVersion));

        return new Fixture(recommendation.getRecommendationId(), analysisRun.getAnalysisRunId(),
                inputSnapshotVersion, ruleVersion, route.getRouteId(),
                receiverPolicy.getStoreSkuPolicyId(), donorPolicy.getStoreSkuPolicyId(),
                receiverMetric.getInventoryMetricId(), donorMetric.getInventoryMetricId(),
                donorSnapshot.getInventorySnapshotId());
    }

    private SpInventorySnapshot findSnapshot(String inputSnapshotVersion, String storeId) {
        return findSnapshot(ANALYSIS_DATE, inputSnapshotVersion, storeId);
    }

    private SpInventorySnapshot findSnapshot(LocalDate snapshotDate, String inputSnapshotVersion, String storeId) {
        return snapshotRepository.findBySnapshotDate(snapshotDate).stream()
                .filter(s -> storeId.equals(s.getStoreId()) && inputSnapshotVersion.equals(s.getInputSnapshotVersion()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected a fixture snapshot for " + storeId));
    }

    /**
     * A dedicated, otherwise-unused {@code analysisDate} rather than a suffixed ruleVersion, so
     * the run's ruleVersion can be exactly {@link InventoryAnalysisRules#RULE_VERSION} -- the same
     * isolation pattern {@code ApiGoldenScenarioIT} uses for the same reason (Codex Finding 1).
     */
    private static final LocalDate LEGACY_ANALYSIS_DATE = LocalDate.of(2026, 12, 1);

    /**
     * A minimal MVP-1-only fixture for the tuple-less legacy path -- no route or policy rows,
     * since {@link RebalanceSimulationService#simulate} never reads them.
     */
    private LegacyFixture buildLegacyFixture() {
        String inputSnapshotVersion = "MVP-1-LEGACYFIXTURE";
        // Self-healing: a prior run that failed between inserting rows and returning the
        // fixture (so its finally-block cleanup never ran) would otherwise collide with this
        // fixed, non-random version string on retry.
        purgeLegacyFixtureRemnants(inputSnapshotVersion);

        jdbcTemplate.update(
                "INSERT INTO sp_inventory_snapshot (snapshot_date, store_id, sku_id, on_hand_quantity, "
                        + "reserved_quantity, source_type, input_snapshot_version) VALUES (?, ?, ?, 5, 0, 'SYNTHETIC', ?)",
                LEGACY_ANALYSIS_DATE, RECEIVER_STORE_ID, SKU_ID, inputSnapshotVersion);
        jdbcTemplate.update(
                "INSERT INTO sp_inventory_snapshot (snapshot_date, store_id, sku_id, on_hand_quantity, "
                        + "reserved_quantity, source_type, input_snapshot_version) VALUES (?, ?, ?, 10, 0, 'SYNTHETIC', ?)",
                LEGACY_ANALYSIS_DATE, DONOR_STORE_ID, SKU_ID, inputSnapshotVersion);
        SpInventorySnapshot receiverSnapshot = findSnapshot(LEGACY_ANALYSIS_DATE, inputSnapshotVersion, RECEIVER_STORE_ID);
        SpInventorySnapshot donorSnapshot = findSnapshot(LEGACY_ANALYSIS_DATE, inputSnapshotVersion, DONOR_STORE_ID);

        SpAnalysisRun analysisRun = analysisRunRepository.save(
                new SpAnalysisRun(LEGACY_ANALYSIS_DATE, InventoryAnalysisRules.RULE_VERSION, inputSnapshotVersion));
        analysisRun.markCompleted();
        analysisRun = analysisRunRepository.save(analysisRun);

        SpInventoryMetric receiverMetric = metricRepository.save(new SpInventoryMetric(
                analysisRun, receiverSnapshot, InventoryMetricCalculation.calculate(5, 0, 28)));
        receiverMetric.applyDemandRates(BigDecimal.ONE, BigDecimal.ONE);
        receiverMetric = metricRepository.save(receiverMetric);

        SpInventoryMetric donorMetric = metricRepository.save(new SpInventoryMetric(
                analysisRun, donorSnapshot, InventoryMetricCalculation.calculate(10, 0, 4)));
        donorMetric.applyDemandRates(BigDecimal.ONE, BigDecimal.ONE);
        donorMetric = metricRepository.save(donorMetric);

        SpRebalanceRecommendation recommendation = recommendationRepository.save(
                new SpRebalanceRecommendation(receiverMetric, donorMetric, new RebalanceCalculation(8, 8, 8)));

        return new LegacyFixture(recommendation.getRecommendationId(), analysisRun.getAnalysisRunId(),
                inputSnapshotVersion, receiverMetric.getInventoryMetricId(), donorMetric.getInventoryMetricId());
    }

    private void cleanupLegacyFixture(LegacyFixture fixture) {
        if (fixture == null) {
            return;
        }
        purgeLegacyFixtureRemnants(fixture.inputSnapshotVersion());
    }

    /** Deletes any row (recommendation/metric/run/snapshot) left over for this version, in FK order. */
    private void purgeLegacyFixtureRemnants(String inputSnapshotVersion) {
        List<Long> runIds = jdbcTemplate.queryForList(
                "SELECT analysis_run_id FROM sp_analysis_run WHERE input_snapshot_version = ?",
                Long.class, inputSnapshotVersion);
        for (Long runId : runIds) {
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
            Long recommendationId, Long analysisRunId, String inputSnapshotVersion,
            Long receiverMetricId, Long donorMetricId) {
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
}
