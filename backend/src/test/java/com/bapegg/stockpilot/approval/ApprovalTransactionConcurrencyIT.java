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
import com.bapegg.stockpilot.rebalance.SpTransferDraft;
import com.bapegg.stockpilot.rebalance.SpTransferDraftRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies {@link ApprovalTransactionFacade}'s real two-transaction concurrency behavior
 * against the real Oracle instance, per {@code knowledge/business-rules.md} section 10's
 * four required scenarios: (1) same key + same payload from two callers converges on one
 * decision row, (2) same key + a different recommendation lets exactly one caller win and
 * rejects the other as a reused key, (3) two approvals racing for the same donor's supply
 * are serialized by the donor row lock so only one can succeed, and (4) a donor lock held
 * by another transaction longer than the 3-second {@code jakarta.persistence.lock.timeout}
 * causes the waiting caller to fail with {@code APPROVAL_LOCK_TIMEOUT} -- proven for both
 * {@link ApprovalTransactionFacade#execute} and the side-effect-free
 * {@link ManualQuantityTestExecutor#test}, since both share the same donor lock via
 * {@link CurrentApprovalBasisLoader}.
 * <p>
 * Deliberately NOT {@code @Transactional} at the class level, unlike
 * {@link ApprovalTransactionExecutorIT}: these scenarios need fixture data that is
 * genuinely committed and visible across separate real transactions/threads, so each test
 * builds its own fixture via plain repository calls (each auto-committing, per
 * {@code spring.jpa.open-in-view: false}) and cleans it up itself in a {@code finally}
 * block, following {@code ApiGoldenScenarioIT}'s manual-cleanup convention. Skipped (not
 * failed) when DB_URL is not set.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "DB_URL", matches = ".+")
class ApprovalTransactionConcurrencyIT {

    private static final LocalDate ANALYSIS_DATE = LocalDate.of(2026, 11, 1);
    private static final String RECEIVER_STORE_ID = "STORE-JAMSIL";
    private static final String RECEIVER_STORE_ID_B = "STORE-YEOUIDO";
    private static final String DONOR_STORE_ID = "STORE-SEONGSU";
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
    private PlatformTransactionManager transactionManager;

    @Test
    void sameKeyAndSamePayloadConcurrentCallsShareASingleDecision() throws Exception {
        RecommendationFixture fixture =
                buildRecommendationFixture("-SAME-KEY", RECEIVER_STORE_ID, DONOR_STORE_ID, 5, 0, 10, 0);
        try {
            ApprovalTransactionCommand command = approvedCommand(fixture, 8);
            String key = newKey();

            List<Outcome<ApprovalTransactionResult>> outcomes = runConcurrently(List.of(
                    () -> facade.execute(command, key),
                    () -> facade.execute(command, key)));

            assertTrue(outcomes.get(0).succeeded() && outcomes.get(1).succeeded(),
                    "Both concurrent calls with the same key and the same payload must succeed.");
            assertEquals(outcomes.get(0).value().decisionId(), outcomes.get(1).value().decisionId());
            long createdCount = outcomes.stream().filter(o -> o.value().created()).count();
            assertEquals(1, createdCount,
                    "Exactly one caller should have created the decision; the other must replay it.");
            assertEquals(1, decisionCount(fixture.recommendationId()));
            assertEquals(1, basisCount(fixture.recommendationId()));
            assertEquals(1, draftCount(fixture.recommendationId()));
            assertEquals(8, draftQuantity(fixture.recommendationId()),
                    "The shared decision's draft must carry exactly the approved quantity once, not once per caller.");
        } finally {
            cleanupRecommendationFixture(fixture);
        }
    }

    @Test
    void sameKeyWithADifferentRecommendationOneWinsTheOtherIsRejectedAsKeyReused() throws Exception {
        RecommendationFixture fixtureA =
                buildRecommendationFixture("-DUAL-A", RECEIVER_STORE_ID, DONOR_STORE_ID, 5, 0, 10, 0);
        RecommendationFixture fixtureB =
                buildRecommendationFixture("-DUAL-B", RECEIVER_STORE_ID_B, DONOR_STORE_ID, 5, 0, 10, 0);
        try {
            String key = newKey();
            ApprovalTransactionCommand commandA = approvedCommand(fixtureA, 8);
            ApprovalTransactionCommand commandB = approvedCommand(fixtureB, 8);

            List<Outcome<ApprovalTransactionResult>> outcomes = runConcurrently(List.of(
                    () -> facade.execute(commandA, key),
                    () -> facade.execute(commandB, key)));

            long successCount = outcomes.stream().filter(Outcome::succeeded).count();
            long reusedCount = outcomes.stream()
                    .filter(o -> !o.succeeded() && ApprovalErrorCode.IDEMPOTENCY_KEY_REUSED.equals(o.error().code()))
                    .count();
            assertEquals(1, successCount, "Exactly one of the two different-payload callers should win the key.");
            assertEquals(1, reusedCount,
                    "The loser must be rejected as a reused idempotency key, not silently dropped.");

            boolean aWon = outcomes.get(0).succeeded();
            RecommendationFixture winner = aWon ? fixtureA : fixtureB;
            RecommendationFixture loser = aWon ? fixtureB : fixtureA;
            assertEquals(1, decisionCount(winner.recommendationId()));
            assertEquals(1, basisCount(winner.recommendationId()));
            assertEquals(1, draftCount(winner.recommendationId()));
            assertEquals(8, draftQuantity(winner.recommendationId()));
            assertEquals(0, decisionCount(loser.recommendationId()),
                    "The loser's own recommendation must carry no decision at all.");
            assertEquals(0, draftQuantity(loser.recommendationId()));
        } finally {
            cleanupRecommendationFixture(fixtureA);
            cleanupRecommendationFixture(fixtureB);
        }
    }

    @Test
    void donorLockSerializesTwoApprovalsSharingADonorTheSecondIsRejectedAsStale() throws Exception {
        SharedDonorFixture fixture = buildSharedDonorFixture("-SHARED-DONOR");
        try {
            ApprovalTransactionCommand commandA = new ApprovalTransactionCommand(
                    fixture.recommendationAId(), fixture.analysisRunId(), fixture.inputSnapshotVersion(),
                    fixture.ruleVersion(), 1, DecisionStatus.APPROVED, 8, false,
                    "MANUAL_OVERRIDE", "approval transaction concurrency IT", "it");
            ApprovalTransactionCommand commandB = new ApprovalTransactionCommand(
                    fixture.recommendationBId(), fixture.analysisRunId(), fixture.inputSnapshotVersion(),
                    fixture.ruleVersion(), 1, DecisionStatus.APPROVED, 8, false,
                    "MANUAL_OVERRIDE", "approval transaction concurrency IT", "it");

            List<Outcome<ApprovalTransactionResult>> outcomes = runConcurrently(List.of(
                    () -> facade.execute(commandA, newKey()),
                    () -> facade.execute(commandB, newKey())));

            long successCount = outcomes.stream().filter(Outcome::succeeded).count();
            long staleCount = outcomes.stream()
                    .filter(o -> !o.succeeded() && ApprovalErrorCode.STALE_RECOMMENDATION.equals(o.error().code()))
                    .count();
            assertEquals(1, successCount,
                    "The donor row lock must serialize the two approvals so only one wins the shared supply.");
            assertEquals(1, staleCount,
                    "The second approval must recalculate against the first one's committed draft and be rejected as stale.");

            boolean aWon = outcomes.get(0).succeeded();
            Long winnerId = aWon ? fixture.recommendationAId() : fixture.recommendationBId();
            Long loserId = aWon ? fixture.recommendationBId() : fixture.recommendationAId();
            assertEquals(1, decisionCount(winnerId));
            assertEquals(1, basisCount(winnerId));
            assertEquals(1, draftCount(winnerId));
            assertEquals(8, draftQuantity(winnerId));
            assertEquals(0, decisionCount(loserId), "The loser's own recommendation must carry no decision at all.");
            assertEquals(0, draftQuantity(loserId));
            assertEquals(8, draftQuantity(winnerId) + draftQuantity(loserId),
                    "The shared donor's total committed draft quantity must reflect only the single winner, "
                            + "never both approvals' quantities added together.");
        } finally {
            cleanupSharedDonorFixture(fixture);
        }
    }

    @Test
    void donorLockHeldByAnotherTransactionCausesTheWaitingCallerToTimeOut() throws Exception {
        RecommendationFixture fixture =
                buildRecommendationFixture("-LOCK-TIMEOUT", RECEIVER_STORE_ID, DONOR_STORE_ID, 5, 0, 10, 0);
        ExecutorService holderExecutor = Executors.newSingleThreadExecutor();
        CountDownLatch lockAcquired = new CountDownLatch(1);
        CountDownLatch releaseLock = new CountDownLatch(1);
        try {
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

            ApprovalTransactionException exception = assertThrowsApproval(
                    () -> facade.execute(approvedCommand(fixture, 8), newKey()));
            assertEquals(ApprovalErrorCode.APPROVAL_LOCK_TIMEOUT, exception.code());

            releaseLock.countDown();
            holderFuture.get(10, TimeUnit.SECONDS);

            assertEquals(0, decisionCount(fixture.recommendationId()),
                    "A lock-timeout rejection must not leave a decision row behind.");
            assertEquals(0, basisCount(fixture.recommendationId()));
            assertEquals(0, draftCount(fixture.recommendationId()));
            assertEquals(0, draftQuantity(fixture.recommendationId()));
        } finally {
            holderExecutor.shutdownNow();
            cleanupRecommendationFixture(fixture);
        }
    }

    @Test
    void donorLockHeldByAnotherTransactionCausesAWaitingManualQuantityTestToTimeOut() throws Exception {
        RecommendationFixture fixture =
                buildRecommendationFixture("-MANUAL-LOCK-TIMEOUT", RECEIVER_STORE_ID, DONOR_STORE_ID, 5, 0, 10, 0);
        ExecutorService holderExecutor = Executors.newSingleThreadExecutor();
        CountDownLatch lockAcquired = new CountDownLatch(1);
        CountDownLatch releaseLock = new CountDownLatch(1);
        try {
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

            ApprovalTransactionException exception = assertThrowsApproval(() -> manualExecutor.test(
                    new ManualQuantityTestCommand(fixture.recommendationId(), fixture.analysisRunId(),
                            fixture.inputSnapshotVersion(), fixture.ruleVersion(), 1, 8)));
            assertEquals(ApprovalErrorCode.APPROVAL_LOCK_TIMEOUT, exception.code());

            releaseLock.countDown();
            holderFuture.get(10, TimeUnit.SECONDS);

            assertEquals(0, decisionCount(fixture.recommendationId()),
                    "A lock-timeout rejection must not leave a decision row behind.");
        } finally {
            holderExecutor.shutdownNow();
            cleanupRecommendationFixture(fixture);
        }
    }

    private ApprovalTransactionCommand approvedCommand(RecommendationFixture fixture, int quantity) {
        return new ApprovalTransactionCommand(
                fixture.recommendationId(), fixture.analysisRunId(), fixture.inputSnapshotVersion(),
                fixture.ruleVersion(), 1, DecisionStatus.APPROVED, quantity, false,
                "MANUAL_OVERRIDE", "approval transaction concurrency IT", "it");
    }

    private static String newKey() {
        return "CIT-KEY-" + UUID.randomUUID();
    }

    private static ApprovalTransactionException assertThrowsApproval(Callable<?> action) throws Exception {
        try {
            action.call();
        } catch (ApprovalTransactionException e) {
            return e;
        }
        throw new AssertionError("Expected an ApprovalTransactionException.");
    }

    /** Runs every {@code call} on its own thread, synchronized to start together via a barrier. */
    private <T> List<Outcome<T>> runConcurrently(List<Callable<T>> calls) throws InterruptedException {
        int n = calls.size();
        ExecutorService executor = Executors.newFixedThreadPool(n);
        CyclicBarrier barrier = new CyclicBarrier(n);
        try {
            List<Future<Outcome<T>>> futures = new ArrayList<>();
            for (Callable<T> call : calls) {
                futures.add(executor.submit(() -> {
                    barrier.await();
                    return callCapturing(call);
                }));
            }
            List<Outcome<T>> outcomes = new ArrayList<>();
            for (Future<Outcome<T>> future : futures) {
                outcomes.add(future.get(30, TimeUnit.SECONDS));
            }
            return outcomes;
        } catch (ExecutionException | TimeoutException e) {
            throw new RuntimeException(e);
        } finally {
            executor.shutdownNow();
        }
    }

    /** Number of decision rows currently persisted for one recommendation. */
    private long decisionCount(Long recommendationId) {
        return decisionRepository
                .findAllByRecommendation_RecommendationIdOrderByDecisionSequenceAsc(recommendationId)
                .size();
    }

    /** Number of that recommendation's decisions that also have an approval basis row. */
    private long basisCount(Long recommendationId) {
        return decisionRepository
                .findAllByRecommendation_RecommendationIdOrderByDecisionSequenceAsc(recommendationId).stream()
                .filter(d -> approvalBasisRepository.findByDecision_DecisionId(d.getDecisionId()).isPresent())
                .count();
    }

    /** Number of that recommendation's decisions that also have a transfer draft row. */
    private long draftCount(Long recommendationId) {
        return decisionRepository
                .findAllByRecommendation_RecommendationIdOrderByDecisionSequenceAsc(recommendationId).stream()
                .filter(d -> transferDraftRepository.findByDecision_DecisionId(d.getDecisionId()).isPresent())
                .count();
    }

    /** Sum of transfer draft quantities across every decision on one recommendation. */
    private long draftQuantity(Long recommendationId) {
        return decisionRepository
                .findAllByRecommendation_RecommendationIdOrderByDecisionSequenceAsc(recommendationId).stream()
                .flatMap(d -> transferDraftRepository.findByDecision_DecisionId(d.getDecisionId()).stream())
                .mapToLong(SpTransferDraft::getQuantity)
                .sum();
    }

    private static <T> Outcome<T> callCapturing(Callable<T> callable) throws Exception {
        try {
            return new Outcome<>(callable.call(), null);
        } catch (ApprovalTransactionException e) {
            return new Outcome<>(null, e);
        }
    }

    private record Outcome<T>(T value, ApprovalTransactionException error) {
        boolean succeeded() {
            return error == null;
        }
    }

    /**
     * Builds one receiver/donor snapshot+metric+recommendation+route+policy fixture, unique
     * per test via {@code suffix} -- mirrors {@code ApprovalTransactionExecutorIT}'s fixture,
     * but keeps every generated id so cleanup here (not an auto-rollback transaction) can
     * delete precisely what it created.
     */
    private RecommendationFixture buildRecommendationFixture(
            String suffix, String receiverStoreId, String donorStoreId,
            int receiverOnHand, int receiverReserved, int donorOnHand, int donorReserved) {
        String ruleVersion = InventoryAnalysisRules.RULE_VERSION + "-ATXCIT" + suffix;
        String inputSnapshotVersion = "MVP-2-APPROVAL-TX-CIT" + suffix;

        insertSnapshot(receiverStoreId, receiverOnHand, receiverReserved, inputSnapshotVersion);
        insertSnapshot(donorStoreId, donorOnHand, donorReserved, inputSnapshotVersion);
        SpInventorySnapshot receiverSnapshot = findSnapshot(inputSnapshotVersion, receiverStoreId);
        SpInventorySnapshot donorSnapshot = findSnapshot(inputSnapshotVersion, donorStoreId);

        SpAnalysisRun analysisRun =
                analysisRunRepository.save(new SpAnalysisRun(ANALYSIS_DATE, ruleVersion, inputSnapshotVersion));
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
                donorStoreId, receiverStoreId, true, false, 4, 1, 1, 20, inputSnapshotVersion));
        recommendation.assignRoute(route.getRouteId());
        recommendation = recommendationRepository.save(recommendation);

        SpStoreSkuPolicy receiverPolicy = policyRepository.save(
                new SpStoreSkuPolicy(receiverStoreId, SKU_ID, 2, 0, 1000, 7, 0, inputSnapshotVersion));
        SpStoreSkuPolicy donorPolicy = policyRepository.save(
                new SpStoreSkuPolicy(donorStoreId, SKU_ID, 0, 0, 1000, 0, 0, inputSnapshotVersion));

        return new RecommendationFixture(recommendation.getRecommendationId(), analysisRun.getAnalysisRunId(),
                inputSnapshotVersion, ruleVersion, route.getRouteId(),
                receiverPolicy.getStoreSkuPolicyId(), donorPolicy.getStoreSkuPolicyId(),
                receiverMetric.getInventoryMetricId(), donorMetric.getInventoryMetricId(),
                donorSnapshot.getInventorySnapshotId());
    }

    /**
     * Two receivers sharing one donor metric/snapshot (a realistic one-donor-many-receivers
     * analysis run), so the donor row lock the executor takes for {@code recommendationAId}
     * and {@code recommendationBId} is the exact same physical row.
     */
    private SharedDonorFixture buildSharedDonorFixture(String suffix) {
        String ruleVersion = InventoryAnalysisRules.RULE_VERSION + "-ATXCIT" + suffix;
        String inputSnapshotVersion = "MVP-2-APPROVAL-TX-CIT" + suffix;

        insertSnapshot(RECEIVER_STORE_ID, 5, 0, inputSnapshotVersion);
        insertSnapshot(RECEIVER_STORE_ID_B, 5, 0, inputSnapshotVersion);
        insertSnapshot(DONOR_STORE_ID, 10, 0, inputSnapshotVersion);
        SpInventorySnapshot receiverASnapshot = findSnapshot(inputSnapshotVersion, RECEIVER_STORE_ID);
        SpInventorySnapshot receiverBSnapshot = findSnapshot(inputSnapshotVersion, RECEIVER_STORE_ID_B);
        SpInventorySnapshot donorSnapshot = findSnapshot(inputSnapshotVersion, DONOR_STORE_ID);

        SpAnalysisRun analysisRun =
                analysisRunRepository.save(new SpAnalysisRun(ANALYSIS_DATE, ruleVersion, inputSnapshotVersion));
        analysisRun.markCompleted();
        analysisRun = analysisRunRepository.save(analysisRun);

        SpInventoryMetric receiverAMetric = metricRepository.save(new SpInventoryMetric(
                analysisRun, receiverASnapshot, InventoryMetricCalculation.calculate(5, 0, 28)));
        receiverAMetric.applyDemandRates(BigDecimal.ONE, BigDecimal.ONE);
        receiverAMetric = metricRepository.save(receiverAMetric);

        SpInventoryMetric receiverBMetric = metricRepository.save(new SpInventoryMetric(
                analysisRun, receiverBSnapshot, InventoryMetricCalculation.calculate(5, 0, 28)));
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

        SpStoreSkuPolicy receiverAPolicy = policyRepository.save(
                new SpStoreSkuPolicy(RECEIVER_STORE_ID, SKU_ID, 2, 0, 1000, 7, 0, inputSnapshotVersion));
        SpStoreSkuPolicy receiverBPolicy = policyRepository.save(
                new SpStoreSkuPolicy(RECEIVER_STORE_ID_B, SKU_ID, 2, 0, 1000, 7, 0, inputSnapshotVersion));
        SpStoreSkuPolicy donorPolicy = policyRepository.save(
                new SpStoreSkuPolicy(DONOR_STORE_ID, SKU_ID, 0, 0, 1000, 0, 0, inputSnapshotVersion));

        return new SharedDonorFixture(
                recommendationA.getRecommendationId(), recommendationB.getRecommendationId(),
                analysisRun.getAnalysisRunId(), inputSnapshotVersion, ruleVersion,
                routeA.getRouteId(), routeB.getRouteId(),
                receiverAPolicy.getStoreSkuPolicyId(), receiverBPolicy.getStoreSkuPolicyId(),
                donorPolicy.getStoreSkuPolicyId(),
                receiverAMetric.getInventoryMetricId(), receiverBMetric.getInventoryMetricId(),
                donorMetric.getInventoryMetricId(), donorSnapshot.getInventorySnapshotId());
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

    private void cleanupRecommendationFixture(RecommendationFixture fixture) {
        if (fixture == null) {
            return;
        }
        deleteDecisionsAndDependents(fixture.recommendationId());
        recommendationRepository.deleteById(fixture.recommendationId());
        routeRepository.deleteById(fixture.routeId());
        policyRepository.deleteById(fixture.receiverPolicyId());
        policyRepository.deleteById(fixture.donorPolicyId());
        metricRepository.deleteById(fixture.receiverMetricId());
        metricRepository.deleteById(fixture.donorMetricId());
        analysisRunRepository.deleteById(fixture.analysisRunId());
        deleteSnapshotsForVersion(fixture.inputSnapshotVersion());
    }

    private void cleanupSharedDonorFixture(SharedDonorFixture fixture) {
        if (fixture == null) {
            return;
        }
        deleteDecisionsAndDependents(fixture.recommendationAId());
        deleteDecisionsAndDependents(fixture.recommendationBId());
        recommendationRepository.deleteById(fixture.recommendationAId());
        recommendationRepository.deleteById(fixture.recommendationBId());
        routeRepository.deleteById(fixture.routeAId());
        routeRepository.deleteById(fixture.routeBId());
        policyRepository.deleteById(fixture.receiverAPolicyId());
        policyRepository.deleteById(fixture.receiverBPolicyId());
        policyRepository.deleteById(fixture.donorPolicyId());
        metricRepository.deleteById(fixture.receiverAMetricId());
        metricRepository.deleteById(fixture.receiverBMetricId());
        metricRepository.deleteById(fixture.donorMetricId());
        analysisRunRepository.deleteById(fixture.analysisRunId());
        deleteSnapshotsForVersion(fixture.inputSnapshotVersion());
    }

    private void deleteDecisionsAndDependents(Long recommendationId) {
        List<SpRebalanceDecision> decisions = decisionRepository
                .findAllByRecommendation_RecommendationIdOrderByDecisionSequenceAsc(recommendationId);
        for (SpRebalanceDecision decision : decisions) {
            transferDraftRepository.findByDecision_DecisionId(decision.getDecisionId())
                    .ifPresent(transferDraftRepository::delete);
            approvalBasisRepository.findByDecision_DecisionId(decision.getDecisionId())
                    .ifPresent(approvalBasisRepository::delete);
        }
        decisionRepository.deleteAll(decisions);
    }

    private void deleteSnapshotsForVersion(String inputSnapshotVersion) {
        jdbcTemplate.update("DELETE FROM sp_inventory_snapshot WHERE input_snapshot_version = ?", inputSnapshotVersion);
    }

    private record RecommendationFixture(
            Long recommendationId, Long analysisRunId, String inputSnapshotVersion, String ruleVersion,
            Long routeId, Long receiverPolicyId, Long donorPolicyId,
            Long receiverMetricId, Long donorMetricId, Long donorSnapshotId) {
    }

    private record SharedDonorFixture(
            Long recommendationAId, Long recommendationBId, Long analysisRunId,
            String inputSnapshotVersion, String ruleVersion,
            Long routeAId, Long routeBId,
            Long receiverAPolicyId, Long receiverBPolicyId, Long donorPolicyId,
            Long receiverAMetricId, Long receiverBMetricId, Long donorMetricId,
            Long donorSnapshotId) {
    }
}
