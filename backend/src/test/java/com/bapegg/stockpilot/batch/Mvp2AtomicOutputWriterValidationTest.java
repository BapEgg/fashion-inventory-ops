package com.bapegg.stockpilot.batch;

import com.bapegg.stockpilot.analysis.SpAnalysisRun;
import com.bapegg.stockpilot.analysis.SpAnalysisRunRepository;
import com.bapegg.stockpilot.analysis.SpInventoryMetricRepository;
import com.bapegg.stockpilot.analysis.SpMetricQualityFlagRepository;
import com.bapegg.stockpilot.inventory.SpInventorySnapshotRepository;
import com.bapegg.stockpilot.rebalance.CandidateStatus;
import com.bapegg.stockpilot.rebalance.RecommendationMode;
import com.bapegg.stockpilot.rebalance.SpCandidateReasonRepository;
import com.bapegg.stockpilot.rebalance.SpRebalanceRecommendationRepository;
import com.bapegg.stockpilot.rebalance.SpRebalanceScenarioRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class Mvp2AtomicOutputWriterValidationTest {

    private static final Long RUN_ID = 7L;
    private static final LocalDate ANALYSIS_DATE = LocalDate.of(2026, 8, 28);
    private static final String INPUT_VERSION = "MVP2-WRITER-VALIDATION-V1";
    private static final String RULE_VERSION = "MVP2-WRITER-VALIDATION";
    private static final Mvp2StoreSkuKey RECEIVER_KEY = new Mvp2StoreSkuKey("STORE-A", "SKU-A");
    private static final Mvp2StoreSkuKey DONOR_KEY = new Mvp2StoreSkuKey("STORE-B", "SKU-A");

    private final SpAnalysisRunRepository runRepository = mock(SpAnalysisRunRepository.class);
    private final SpInventoryMetricRepository metricRepository = mock(SpInventoryMetricRepository.class);
    private final SpInventorySnapshotRepository snapshotRepository = mock(SpInventorySnapshotRepository.class);
    private final SpMetricQualityFlagRepository qualityFlagRepository = mock(SpMetricQualityFlagRepository.class);
    private final SpRebalanceRecommendationRepository recommendationRepository =
            mock(SpRebalanceRecommendationRepository.class);
    private final SpCandidateReasonRepository reasonRepository = mock(SpCandidateReasonRepository.class);
    private final SpRebalanceScenarioRepository scenarioRepository = mock(SpRebalanceScenarioRepository.class);
    private final Mvp2AtomicOutputWriter writer = new Mvp2AtomicOutputWriter(
            runRepository, metricRepository, snapshotRepository, qualityFlagRepository,
            recommendationRepository, reasonRepository, scenarioRepository);

    @Test
    void rejectsMetricIndexWhoseKeysExistButPointToTheWrongMetrics() {
        Mvp2MetricResult receiver = metric(RECEIVER_KEY);
        Mvp2MetricResult donor = metric(DONOR_KEY);
        Map<Mvp2StoreSkuKey, Mvp2MetricResult> swappedIndex = new LinkedHashMap<>();
        swappedIndex.put(RECEIVER_KEY, donor);
        swappedIndex.put(DONOR_KEY, receiver);
        Mvp2CalculationResult result = new Mvp2CalculationResult(
                ANALYSIS_DATE, INPUT_VERSION, List.of(receiver, donor), List.of(), swappedIndex, Map.of());
        givenRunningRun();

        assertThrows(Mvp2OutputContractViolationException.class, () -> writer.writeAndComplete(RUN_ID, result));

        verifyNoOutputRepositoryInteraction();
    }

    @Test
    void rejectsCandidateIndexedUnderADifferentReceiverKey() {
        Mvp2MetricResult receiver = metric(RECEIVER_KEY);
        Mvp2MetricResult donor = metric(DONOR_KEY);
        Mvp2CandidateResult candidate = new Mvp2CandidateResult(
                RECEIVER_KEY.storeId(), DONOR_KEY.storeId(), RECEIVER_KEY.skuId(), null,
                CandidateStatus.REJECTED, 1, RecommendationMode.NONE,
                null, null, null, null, null, null, List.of(), 1, List.of());
        Mvp2CalculationResult result = new Mvp2CalculationResult(
                ANALYSIS_DATE, INPUT_VERSION, List.of(receiver, donor), List.of(candidate),
                Map.of(RECEIVER_KEY, receiver, DONOR_KEY, donor), Map.of(DONOR_KEY, List.of(candidate)));
        givenRunningRun();

        assertThrows(Mvp2OutputContractViolationException.class, () -> writer.writeAndComplete(RUN_ID, result));

        verifyNoOutputRepositoryInteraction();
    }

    private Mvp2MetricResult metric(Mvp2StoreSkuKey key) {
        Mvp2MetricResult metric = mock(Mvp2MetricResult.class);
        when(metric.storeId()).thenReturn(key.storeId());
        when(metric.skuId()).thenReturn(key.skuId());
        when(metric.calculationVersion()).thenReturn(RULE_VERSION);
        return metric;
    }

    private void givenRunningRun() {
        when(runRepository.lockById(RUN_ID))
                .thenReturn(Optional.of(new SpAnalysisRun(ANALYSIS_DATE, RULE_VERSION, INPUT_VERSION)));
    }

    private void verifyNoOutputRepositoryInteraction() {
        verifyNoInteractions(
                metricRepository, snapshotRepository, qualityFlagRepository,
                recommendationRepository, reasonRepository, scenarioRepository);
    }
}
