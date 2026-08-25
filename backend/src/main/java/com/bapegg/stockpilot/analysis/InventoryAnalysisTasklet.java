package com.bapegg.stockpilot.analysis;

import com.bapegg.stockpilot.inventory.SpDailySale;
import com.bapegg.stockpilot.inventory.SpDailySaleRepository;
import com.bapegg.stockpilot.inventory.SpInventorySnapshot;
import com.bapegg.stockpilot.inventory.SpInventorySnapshotRepository;
import com.bapegg.stockpilot.rebalance.RebalanceCalculation;
import com.bapegg.stockpilot.rebalance.SpRebalanceRecommendation;
import com.bapegg.stockpilot.rebalance.SpRebalanceRecommendationRepository;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Computes {@link SpInventoryMetric} and {@link SpRebalanceRecommendation} rows for one
 * analysis date in a single transaction. Idempotency has two layers:
 * <ul>
 *     <li>Spring Batch's own JobRepository (BATCH_JOB_INSTANCE) refuses to relaunch a
 *     Job with JobParameters that already completed.</li>
 *     <li>This tasklet also checks {@link SpAnalysisRun} directly and no-ops if a run
 *     for the same analysis date and rule version already exists, and runs entirely in
 *     one transaction so a failure never leaves a partial run behind.</li>
 * </ul>
 */
@Component
public class InventoryAnalysisTasklet implements Tasklet {

    private final SpAnalysisRunRepository analysisRunRepository;
    private final SpInventorySnapshotRepository snapshotRepository;
    private final SpDailySaleRepository dailySaleRepository;
    private final SpInventoryMetricRepository metricRepository;
    private final SpRebalanceRecommendationRepository recommendationRepository;

    public InventoryAnalysisTasklet(
            SpAnalysisRunRepository analysisRunRepository,
            SpInventorySnapshotRepository snapshotRepository,
            SpDailySaleRepository dailySaleRepository,
            SpInventoryMetricRepository metricRepository,
            SpRebalanceRecommendationRepository recommendationRepository) {
        this.analysisRunRepository = analysisRunRepository;
        this.snapshotRepository = snapshotRepository;
        this.dailySaleRepository = dailySaleRepository;
        this.metricRepository = metricRepository;
        this.recommendationRepository = recommendationRepository;
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        JobParameters parameters = chunkContext.getStepContext().getStepExecution().getJobParameters();
        LocalDate analysisDate = parameters.getLocalDate("analysisDate");
        String ruleVersion = parameters.getString("ruleVersion");

        if (analysisRunRepository.findByAnalysisDateAndRuleVersion(analysisDate, ruleVersion).isPresent()) {
            return RepeatStatus.FINISHED;
        }

        SpAnalysisRun analysisRun = analysisRunRepository.save(new SpAnalysisRun(analysisDate, ruleVersion));

        List<MetricEntry> entries = new ArrayList<>();
        for (SpInventorySnapshot snapshot : snapshotRepository.findBySnapshotDate(analysisDate)) {
            int soldInWindow = sumSoldInWindow(snapshot, analysisDate);
            InventoryMetricCalculation calculation = InventoryMetricCalculation.calculate(
                    snapshot.getOnHandQuantity(), snapshot.getReservedQuantity(), soldInWindow);
            SpInventoryMetric metric = metricRepository.save(
                    new SpInventoryMetric(analysisRun, snapshot, calculation));
            entries.add(new MetricEntry(metric, calculation, soldInWindow, snapshot.getSkuId(), snapshot.getStoreId()));
        }

        createRecommendations(entries);

        analysisRun.markCompleted();
        analysisRunRepository.save(analysisRun);

        return RepeatStatus.FINISHED;
    }

    private int sumSoldInWindow(SpInventorySnapshot snapshot, LocalDate analysisDate) {
        LocalDate windowStart = analysisDate.minusDays(InventoryAnalysisRules.OBSERVATION_WINDOW_DAYS);
        LocalDate windowEnd = analysisDate.minusDays(1);
        return dailySaleRepository
                .findByStoreIdAndSkuIdAndSalesDateBetween(snapshot.getStoreId(), snapshot.getSkuId(), windowStart, windowEnd)
                .stream()
                .mapToInt(SpDailySale::getSoldQuantity)
                .sum();
    }

    private void createRecommendations(List<MetricEntry> entries) {
        Map<String, List<MetricEntry>> bySku = entries.stream().collect(Collectors.groupingBy(MetricEntry::skuId));

        for (List<MetricEntry> skuEntries : bySku.values()) {
            List<MetricEntry> receivers = skuEntries.stream()
                    .filter(e -> e.calculation().classification() == InventoryClassification.STOCKOUT_RISK)
                    .toList();
            List<MetricEntry> donors = skuEntries.stream()
                    .filter(e -> e.calculation().classification() == InventoryClassification.OVERSTOCK)
                    .toList();

            for (MetricEntry receiver : receivers) {
                for (MetricEntry donor : donors) {
                    if (receiver.storeId().equals(donor.storeId())) {
                        continue;
                    }
                    RebalanceCalculation.calculate(
                            receiver.soldQuantityInWindow(), receiver.calculation().availableQuantity(),
                            donor.soldQuantityInWindow(), donor.calculation().availableQuantity()
                    ).ifPresent(calc -> recommendationRepository.save(
                            new SpRebalanceRecommendation(receiver.metric(), donor.metric(), calc)));
                }
            }
        }
    }

    private record MetricEntry(
            SpInventoryMetric metric,
            InventoryMetricCalculation calculation,
            int soldQuantityInWindow,
            String skuId,
            String storeId) {
    }
}
