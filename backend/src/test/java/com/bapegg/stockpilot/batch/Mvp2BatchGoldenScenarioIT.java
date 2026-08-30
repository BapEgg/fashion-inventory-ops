package com.bapegg.stockpilot.batch;

import com.bapegg.stockpilot.demand.DemandConfidence;
import com.bapegg.stockpilot.demand.DemandSignalType;
import com.bapegg.stockpilot.demand.InventoryExceptionType;
import com.bapegg.stockpilot.demand.MetricQualityFlag;
import com.bapegg.stockpilot.demand.TransferCandidateRejectionReason;
import com.bapegg.stockpilot.demand.TransferScenarioResult;
import com.bapegg.stockpilot.rebalance.CandidateStatus;
import com.bapegg.stockpilot.rebalance.RecommendationMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.sql.Connection;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies {@link Mvp2InputAdapter} + {@link Mvp2CalculationOrchestrator} together against the
 * real {@code MVP-2-GS-V1} Golden Scenario seed data (V7 migration), per {@code current-task.md}:
 * exactly 12 metrics, 4 candidates and 8 scenarios, with the specific per-scenario golden values.
 * No output is written -- this proves only the in-memory composition. The orchestrator itself
 * executes zero additional SQL beyond the adapter's own 8 statements. Skipped (not failed) when
 * DB_URL is not set.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "DB_URL", matches = ".+")
@Transactional
class Mvp2BatchGoldenScenarioIT {

    private static final LocalDate ANALYSIS_DATE = LocalDate.of(2026, 9, 30);
    private static final String INPUT_SNAPSHOT_VERSION = "MVP-2-GS-V1";

    private static final String RECEIVER = "STORE-MVP2-RECEIVER-A";
    private static final String DONOR_A = "STORE-MVP2-DONOR-A";
    private static final String DONOR_B = "STORE-MVP2-DONOR-B";

    private static final String GS01 = "SKU-MVP2-GS01-STABLE";
    private static final String GS02 = "SKU-MVP2-GS02-EVENT";
    private static final String GS03 = "SKU-MVP2-GS03-SPIKE";
    private static final String GS04 = "SKU-MVP2-GS04-OOS";
    private static final String GS05 = "SKU-MVP2-GS05-INBOUND";
    private static final String GS06 = "SKU-MVP2-GS06-ROUTE";

    @Autowired
    private DataSource dataSource;

    @Autowired
    private Mvp2InputAdapter adapter;

    @Test
    void mvp2GsV1ProducesExactlyTwelveMetricsFourCandidatesAndEightScenariosWithTheGoldenValues() {
        AtomicInteger statementCount = new AtomicInteger();
        Mvp2InputAdapter countingAdapter = new Mvp2InputAdapter(countingJdbcTemplate(statementCount));

        Mvp2InputGraph graph = countingAdapter.load(ANALYSIS_DATE, INPUT_SNAPSHOT_VERSION);
        Mvp2CalculationResult result = Mvp2CalculationOrchestrator.calculate(graph);

        assertEquals(8, statementCount.get(), "The orchestrator itself must execute zero SQL.");

        assertEquals(12, result.metrics().size(), "6 SKUs x 2 anchors (receiver + donor) each.");
        assertEquals(4, result.candidates().size(), "GS-01, GS-02, GS-05, GS-06 only -- GS-03/04 are gated out.");
        long totalScenarios = result.candidates().stream().mapToLong(c -> c.scenarios().size()).sum();
        assertEquals(8, totalScenarios, "Only GS-01 and GS-02 are ELIGIBLE, 4 scenarios each.");

        assertGs01EligibleBaseElevenWithBothStoresProtected(result);
        assertGs02EffectiveBaseRateThreeAndBaseQuantityTwenty(result);
        assertGs03And04HaveNoCandidateAtAll(result);
        assertGs04IsOosCensored(result);
        assertGs05InboundAlreadyCovers(result);
        assertGs06OwnerMismatchAndLeadTimeTooLong(result);
    }

    private void assertGs01EligibleBaseElevenWithBothStoresProtected(Mvp2CalculationResult result) {
        Mvp2CandidateResult candidate = oneCandidate(result, RECEIVER, GS01);
        assertEquals(CandidateStatus.ELIGIBLE, candidate.candidateStatus());
        assertEquals(RecommendationMode.RECOMMENDED, candidate.recommendationMode());
        assertEquals(DONOR_A, candidate.donorStoreId());
        assertTrue(candidate.rejectionReasons().isEmpty());
        assertEquals(11, candidate.recommendedQuantity());
        assertEquals(4, candidate.scenarios().size());
        TransferScenarioResult base = scenarioOfType(candidate, com.bapegg.stockpilot.demand.TransferScenarioType.BASE);
        assertEquals(11L, base.scenarioQuantity());
        // Both stores' after-transfer position stays sane (protected): receiver gains, donor
        // still has stock left over rather than going negative.
        assertTrue(base.receiverAfterAvailable() > base.receiverBeforeAvailable());
        assertTrue(base.donorAfterAvailable() >= 0);
    }

    private void assertGs02EffectiveBaseRateThreeAndBaseQuantityTwenty(Mvp2CalculationResult result) {
        Mvp2MetricResult receiverMetric = result.metricsByStoreSku().get(new Mvp2StoreSkuKey(RECEIVER, GS02));
        assertEquals(DemandSignalType.KNOWN_EVENT, receiverMetric.signal().signalType());

        Mvp2CandidateResult candidate = oneCandidate(result, RECEIVER, GS02);
        assertEquals(CandidateStatus.ELIGIBLE, candidate.candidateStatus());
        assertEquals(RecommendationMode.RECOMMENDED, candidate.recommendationMode());
        assertEquals(20, candidate.recommendedQuantity());
        TransferScenarioResult base = scenarioOfType(candidate, com.bapegg.stockpilot.demand.TransferScenarioType.BASE);
        assertEquals(0, new BigDecimal("3.000000000000").compareTo(base.demandRate()));
        assertEquals(20L, base.scenarioQuantity());
    }

    private void assertGs03And04HaveNoCandidateAtAll(Mvp2CalculationResult result) {
        assertTrue(candidatesFor(result, RECEIVER, GS03).isEmpty(), "GS-03's spike/LOW confidence must not auto-quantify.");
        assertTrue(candidatesFor(result, RECEIVER, GS04).isEmpty(), "GS-04's intermittent/LOW confidence must not auto-quantify.");
    }

    private void assertGs04IsOosCensored(Mvp2CalculationResult result) {
        Mvp2MetricResult metric = result.metricsByStoreSku().get(new Mvp2StoreSkuKey(RECEIVER, GS04));
        assertTrue(metric.qualityFlags().contains(MetricQualityFlag.OOS_CENSORED));
        assertTrue(metric.stats().oosCensored());
        assertEquals(DemandConfidence.LOW, metric.signal().confidence());
    }

    private void assertGs05InboundAlreadyCovers(Mvp2CalculationResult result) {
        Mvp2CandidateResult candidate = oneCandidate(result, RECEIVER, GS05);
        assertEquals(CandidateStatus.REJECTED, candidate.candidateStatus());
        assertEquals(RecommendationMode.NONE, candidate.recommendationMode());
        assertTrue(candidate.rejectionReasons().contains(TransferCandidateRejectionReason.INBOUND_ALREADY_COVERS));
        assertTrue(candidate.scenarios().isEmpty());
        assertNull(candidate.recommendedQuantity());
    }

    private void assertGs06OwnerMismatchAndLeadTimeTooLong(Mvp2CalculationResult result) {
        Mvp2CandidateResult candidate = oneCandidate(result, RECEIVER, GS06);
        assertEquals(CandidateStatus.REJECTED, candidate.candidateStatus());
        assertEquals(DONOR_B, candidate.donorStoreId());
        // The full, unfiltered reason list must be exactly these two -- no additional reason
        // (e.g. DISPLAY_MINIMUM_VIOLATION, CAPACITY_EXCEEDED) may be present either.
        assertEquals(
                List.of(TransferCandidateRejectionReason.OWNER_MISMATCH, TransferCandidateRejectionReason.LEAD_TIME_TOO_LONG),
                candidate.rejectionReasons());
        assertTrue(candidate.scenarios().isEmpty());
    }

    private static Mvp2CandidateResult oneCandidate(Mvp2CalculationResult result, String receiverStoreId, String skuId) {
        List<Mvp2CandidateResult> candidates = candidatesFor(result, receiverStoreId, skuId);
        assertEquals(1, candidates.size(), "Expected exactly one candidate for " + receiverStoreId + "/" + skuId);
        return candidates.get(0);
    }

    private static List<Mvp2CandidateResult> candidatesFor(Mvp2CalculationResult result, String receiverStoreId, String skuId) {
        return result.candidatesByReceiver().getOrDefault(new Mvp2StoreSkuKey(receiverStoreId, skuId), List.of());
    }

    private static TransferScenarioResult scenarioOfType(
            Mvp2CandidateResult candidate, com.bapegg.stockpilot.demand.TransferScenarioType type) {
        return candidate.scenarios().stream().filter(s -> s.scenarioType() == type).findFirst().orElseThrow();
    }

    /** Same counting-connection technique as {@code Mvp2InputAdapterIT}: still runs inside the
     * test's own rolled-back transaction while counting every prepareStatement/createStatement. */
    private JdbcTemplate countingJdbcTemplate(AtomicInteger counter) {
        Connection transactional = DataSourceUtils.getConnection(dataSource);
        Connection counting = (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(), new Class<?>[] {Connection.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("prepareStatement") || method.getName().equals("createStatement")) {
                        counter.incrementAndGet();
                    }
                    try {
                        return method.invoke(transactional, args);
                    } catch (InvocationTargetException e) {
                        throw e.getCause();
                    }
                });
        SingleConnectionDataSource countingDataSource = new SingleConnectionDataSource(counting, true);
        return new JdbcTemplate(countingDataSource);
    }
}
